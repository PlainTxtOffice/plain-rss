package com.plaintxtoffice.plainrss.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.plaintxtoffice.plainrss.data.PreferencesRepository
import com.plaintxtoffice.plainrss.domain.OpmlParser
import com.plaintxtoffice.plainrss.domain.OpmlWriter
import com.plaintxtoffice.plainrss.domain.ReaderLibrary
import com.plaintxtoffice.plainrss.domain.ReaderUrl
import com.plaintxtoffice.plainrss.model.Article
import com.plaintxtoffice.plainrss.model.ArticleOrder
import com.plaintxtoffice.plainrss.model.DateDisplay
import com.plaintxtoffice.plainrss.model.Feed
import com.plaintxtoffice.plainrss.model.FeedException
import com.plaintxtoffice.plainrss.model.FeedFetchResult
import com.plaintxtoffice.plainrss.model.FeedFilter
import com.plaintxtoffice.plainrss.model.FeedFolder
import com.plaintxtoffice.plainrss.model.FeedOrder
import com.plaintxtoffice.plainrss.model.FetchedFeed
import com.plaintxtoffice.plainrss.model.OpmlFeed
import com.plaintxtoffice.plainrss.model.ReaderPreferences
import com.plaintxtoffice.plainrss.network.FeedFetcher
import com.plaintxtoffice.plainrss.storage.InMemoryLibraryStore
import com.plaintxtoffice.plainrss.storage.LibrarySnapshot
import com.plaintxtoffice.plainrss.storage.LibraryStore
import java.net.URI
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.milliseconds

/**
 * Immutable UI state for the Plain RSS reader screens.
 */
data class ReaderUiState(
  val isLoaded: Boolean = false,
  val storageError: String? = null,
  val usingTemporaryStorage: Boolean = false,
  val feeds: List<Feed> = emptyList(),
  val articles: List<Article> = emptyList(),
  val folders: List<FeedFolder> = emptyList(),
  val preferences: ReaderPreferences = ReaderPreferences(),
  val selectedFilter: FeedFilter? = null,
  val selectedArticleId: String? = null,
  val searchQuery: String = "",
  val isRefreshing: Boolean = false,
  val errorMessage: String? = null,
  val importSummary: String? = null,
  val imagePermissionArticleId: String? = null,
) {
  val unreadCountsByFeed: Map<String, Int>
    get() {
      val counts = mutableMapOf<String, Int>()
      for ((_, feedUrl, _, _, _, _, _, isRead) in articles) {
        if (!isRead) {
          counts[feedUrl] = (counts[feedUrl] ?: 0) + 1
        }
      }
      return counts
    }

  val totalUnreadCount: Int
    get() = articles.count { !it.isRead }

  val sortedFeeds: List<Feed>
    get() {
      val counts = unreadCountsByFeed
      return ReaderLibrary.sortFeeds(feeds, preferences.feedOrder) { counts[it.url] ?: 0 }
    }

  val sortedFolders: List<FeedFolder>
    get() = folders.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

  fun isUnfiled(feed: Feed): Boolean = folders.none { it.id == feed.folderId }

  val unfiledFeeds: List<Feed>
    get() = sortedFeeds.filter { isUnfiled(it) }

  fun feedsInFolder(folderId: String): List<Feed> =
    sortedFeeds.filter { it.folderId == folderId }

  val visibleArticles: List<Article>
    get() {
      val folderIds = folders.map { it.id }.toSet()
      val unfiledFeedUrls =
        feeds.filter { it.folderId == null || it.folderId !in folderIds }.map { it.url }.toSet()
      val filtered =
        when (val filter = selectedFilter) {
          null, FeedFilter.All -> articles
          FeedFilter.Unread -> articles.filter { !it.isRead }
          FeedFilter.Starred -> articles.filter { it.isStarred }
          FeedFilter.Unfiled -> articles.filter { it.feedUrl in unfiledFeedUrls }
          is FeedFilter.Folder -> {
            val urlsInFolder = feeds.filter { it.folderId == filter.id }.map { it.url }.toSet()
            articles.filter { it.feedUrl in urlsInFolder }
          }
          is FeedFilter.FeedItem -> articles.filter { it.feedUrl == filter.url }
        }
      val query = searchQuery.trim()
      val matching =
        if (query.isEmpty()) {
          filtered
        } else {
          filtered.filter {
            it.title.contains(query, ignoreCase = true) ||
              it.summary.contains(query, ignoreCase = true)
          }
        }
      return ReaderLibrary.sortArticles(matching, preferences.articleOrder)
    }

  val selectedArticle: Article?
    get() = selectedArticleId?.let { id -> articles.firstOrNull { it.id == id } }

  val selectedArticleFeedTitle: String?
    get() = selectedArticle?.let { article -> feeds.firstOrNull { it.url == article.feedUrl }?.title }

  val showsFeedTitleInRows: Boolean
    get() = selectedFilter !is FeedFilter.FeedItem

  val imagesAllowedForSelectedArticle: Boolean
    get() = selectedArticleId != null && imagePermissionArticleId == selectedArticleId
}

/**
 * Owns state, persistence, and feed synchronization for the Plain RSS Android application.
 */
class ReaderViewModel(
  private val primaryStore: LibraryStore,
  private val preferencesRepository: PreferencesRepository,
  private val feedFetcher: FeedFetcher,
  private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

  private var activeStore: LibraryStore = primaryStore

  /** Serializes saves so the snapshot written last is never older than one written before it. */
  private val saveMutex = Mutex()
  private var pendingSave: Job? = null

  private val _uiState = MutableStateFlow(
    ReaderUiState(preferences = preferencesRepository.load())
  )
  val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

  init {
    loadLibrary()
  }

  fun loadLibrary() {
    viewModelScope.launch {
      try {
        val snapshot = activeStore.load()
        _uiState.update { state ->
          state.copy(
            isLoaded = true,
            storageError = null,
            feeds = snapshot.feeds,
            articles = snapshot.articles,
            folders = snapshot.folders,
          )
        }
        refreshIfStale()
      } catch (e: Exception) {
        _uiState.update { state ->
          state.copy(
            isLoaded = false,
            storageError = e.message ?: "The persistent library could not be opened.",
          )
        }
      }
    }
  }

  fun retryStorage() {
    activeStore = primaryStore
    _uiState.update { it.copy(usingTemporaryStorage = false, storageError = null) }
    loadLibrary()
  }

  fun continueWithoutSaving() {
    activeStore = InMemoryLibraryStore()
    _uiState.update {
      it.copy(
        isLoaded = true,
        storageError = null,
        usingTemporaryStorage = true,
        feeds = emptyList(),
        articles = emptyList(),
        folders = emptyList(),
      )
    }
  }

  fun selectFilter(filter: FeedFilter?) {
    _uiState.update {
      it.copy(
        selectedFilter = filter,
        selectedArticleId = null,
        imagePermissionArticleId = null,
      )
    }
  }

  fun selectArticle(articleId: String?) {
    if (articleId == null) {
      _uiState.update {
        it.copy(
          selectedArticleId = null,
          imagePermissionArticleId = null,
        )
      }
      return
    }
    _uiState.update { state ->
      val updatedArticles = state.articles.map { article ->
        if (article.id == articleId && !article.isRead) {
          article.copy(isRead = true)
        } else {
          article
        }
      }
      state.copy(
        articles = updatedArticles,
        selectedArticleId = articleId,
        imagePermissionArticleId = null,
      )
    }
    persistCurrentLibrary()
  }

  fun updateSearchQuery(query: String) {
    _uiState.update { it.copy(searchQuery = query) }
  }

  fun toggleArticleRead(articleId: String) {
    _uiState.update { state ->
      val updated = state.articles.map { article ->
        if (article.id == articleId) article.copy(isRead = !article.isRead) else article
      }
      state.copy(articles = updated)
    }
    persistCurrentLibrary()
  }

  fun toggleArticleStarred(articleId: String) {
    _uiState.update { state ->
      val updated = state.articles.map { article ->
        if (article.id == articleId) article.copy(isStarred = !article.isStarred) else article
      }
      state.copy(articles = updated)
    }
    persistCurrentLibrary()
  }

  fun markVisibleAsRead() {
    _uiState.update { state ->
      val visibleIds = state.visibleArticles.map { it.id }.toSet()
      val updated = state.articles.map { article ->
        if (article.id in visibleIds && !article.isRead) {
          article.copy(isRead = true)
        } else {
          article
        }
      }
      state.copy(articles = updated)
    }
    persistCurrentLibrary()
  }

  fun allowImagesForCurrentArticle() {
    _uiState.update { state ->
      val currentId = state.selectedArticleId ?: return@update state
      state.copy(imagePermissionArticleId = currentId)
    }
  }

  fun hideImagesForCurrentArticle() {
    _uiState.update { it.copy(imagePermissionArticleId = null) }
  }

  fun onAppBackgrounded() {
    _uiState.update { it.copy(imagePermissionArticleId = null) }
    // The process may be killed once backgrounded, so write any debounced change now.
    if (pendingSave?.isActive == true) {
      persistCurrentLibrary(debounce = false)
    }
  }

  fun onAppForegrounded() {
    if (_uiState.value.isLoaded) {
      viewModelScope.launch { refreshIfStale() }
    }
  }

  suspend fun addFeed(rawInput: String): String? {
    var trimmed = rawInput.trim()
    if (trimmed.isEmpty()) {
      return "Enter a valid feed URL."
    }
    if (!trimmed.contains("://")) {
      trimmed = "https://$trimmed"
    }
    val parsedUri =
      try {
        URI(trimmed)
      } catch (_: Exception) {
        return "Enter a valid feed URL."
      }
    val scheme = parsedUri.scheme?.lowercase(Locale.ROOT)
    if ((scheme != "http" && scheme != "https") || parsedUri.host.isNullOrEmpty()) {
      return "Enter a valid feed URL."
    }
    val normalizedUrl = parsedUri.toASCIIString()
    val currentState = _uiState.value
    if (currentState.feeds.any { it.url == normalizedUrl }) {
      return "That feed is already added."
    }
    if (currentState.isRefreshing) {
      return "Wait for the current refresh to finish, then try again."
    }

    _uiState.update { it.copy(isRefreshing = true) }
    // Runs in viewModelScope so a rotation that cancels the calling dialog does not
    // abandon the download; the feed is still added and saved.
    return viewModelScope.async {
      try {
        val (resolvedUrl, fetched) = feedFetcher.fetchNew(normalizedUrl)
        if (_uiState.value.feeds.any { it.url == resolvedUrl }) {
          "That feed is already added."
        } else {
          applyFetchedFeed(fetched, resolvedUrl, createIfMissing = true)
          saveLibraryOrThrow()
          null
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        friendlyMessage(e)
      } finally {
        _uiState.update { it.copy(isRefreshing = false) }
      }
    }.await()
  }

  fun refreshAllFeeds() {
    if (_uiState.value.isRefreshing || _uiState.value.feeds.isEmpty()) return
    viewModelScope.launch {
      performRefreshAll()
    }
  }

  suspend fun refreshIfStale() {
    val state = _uiState.value
    if (!state.preferences.refreshOnOpen || state.feeds.isEmpty() || state.isRefreshing) return
    val now = clock()
    if (now - state.preferences.lastAutoRefreshMillis <= AUTO_REFRESH_INTERVAL_MS) return
    updatePreferences { it.copy(lastAutoRefreshMillis = now) }
    performRefreshAll()
  }

  private suspend fun performRefreshAll() {
    val state = _uiState.value
    if (state.isRefreshing || state.feeds.isEmpty()) return
    _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
    try {
      val failures = refreshFeedsList(_uiState.value.feeds)
      if (failures.isNotEmpty()) {
        _uiState.update { it.copy(errorMessage = failures.joinToString("\n")) }
      }
    } finally {
      _uiState.update { it.copy(isRefreshing = false) }
    }
  }

  private suspend fun refreshFeedsList(feedsToRefresh: List<Feed>): List<String> = coroutineScope {
    val semaphore = Semaphore(MAX_CONCURRENT_REFRESHES)
    val results = feedsToRefresh.map { feed ->
      async {
        semaphore.withPermit {
          try {
            val currentFeed = _uiState.value.feeds.firstOrNull { it.url == feed.url } ?: feed
            val result = feedFetcher.fetch(
              url = currentFeed.url,
              etag = currentFeed.etag,
              lastModified = currentFeed.lastModified,
            )
            when (result) {
              FeedFetchResult.NotModified -> {
                val now = clock()
                _uiState.update { s ->
                  val updatedFeeds = s.feeds.map {
                    if (it.url == currentFeed.url) it.copy(lastFetchedAt = now) else it
                  }
                  s.copy(feeds = updatedFeeds)
                }
                applyRetentionToFeed(currentFeed.url)
              }
              is FeedFetchResult.Fetched -> {
                applyFetchedFeed(result.feed, currentFeed.url, createIfMissing = false)
              }
            }
            null
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            "${feed.title}: ${friendlyMessage(e)}"
          }
        }
      }
    }.awaitAll()
    // One write for the whole batch instead of one full-library write per feed.
    val saveFailure =
      try {
        saveLibraryOrThrow()
        null
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        e.message ?: "Couldn't save changes."
      }
    results.filterNotNull() + listOfNotNull(saveFailure)
  }

  /**
   * Merges a download into the library. A refresh passes [createIfMissing] = false so a
   * feed the user removed while its download was in flight is not added back.
   */
  private fun applyFetchedFeed(fetched: FetchedFeed, feedUrl: String, createIfMissing: Boolean) {
    val now = clock()
    _uiState.update { state ->
      val existingFeed = state.feeds.firstOrNull { it.url == feedUrl }
      if (existingFeed == null && !createIfMissing) return@update state
      val updatedFeed =
          existingFeed?.copy(
              // A name the user chose wins over the publisher's title.
              title = if (existingFeed.hasCustomTitle) existingFeed.title else fetched.parsed.title,
              lastFetchedAt = now,
              etag = fetched.etag,
              lastModified = fetched.lastModified,
          )
              ?: Feed(
                  url = feedUrl,
                  title = fetched.parsed.title,
                  lastFetchedAt = now,
                  etag = fetched.etag,
                  lastModified = fetched.lastModified,
                  retentionEnabled = true,
              )

      val updatedFeeds =
        if (existingFeed != null) {
          state.feeds.map { if (it.url == feedUrl) updatedFeed else it }
        } else {
          state.feeds + updatedFeed
        }

      val otherArticles = state.articles.filterNot { it.feedUrl == feedUrl }
      val existingFeedArticles = state.articles.filter { it.feedUrl == feedUrl }
      val mergedFeedArticles = ReaderLibrary.mergeArticles(
        parsed = fetched.parsed.articles,
        existingFeedArticles = existingFeedArticles,
        feedUrl = feedUrl,
        nowMillis = now,
      )
      val combinedArticles = otherArticles + mergedFeedArticles
      state.copy(
        feeds = updatedFeeds,
        articles = combinedArticles,
      )
    }
    applyRetentionToFeed(feedUrl)
  }

  private fun applyRetentionToFeed(feedUrl: String) {
    _uiState.update { state ->
      val feed = state.feeds.firstOrNull { it.url == feedUrl } ?: return@update state
      if (!feed.retentionEnabled) return@update state
      val trimmedArticles = ReaderLibrary.trimFeedArticles(
        allArticles = state.articles,
        feedUrl = feedUrl,
        limit = state.preferences.articleRetention,
      )
      val selectedStillExists =
        state.selectedArticleId == null || trimmedArticles.any { it.id == state.selectedArticleId }
      state.copy(
        articles = trimmedArticles,
        selectedArticleId = if (selectedStillExists) state.selectedArticleId else null,
        imagePermissionArticleId = if (selectedStillExists) state.imagePermissionArticleId else null,
      )
    }
  }

  fun renameFeed(feedUrl: String, newName: String) {
    val trimmed = newName.trim()
    if (trimmed.isEmpty()) return
    _uiState.update { state ->
      val updated = state.feeds.map { feed ->
        if (feed.url == feedUrl) feed.copy(title = trimmed, hasCustomTitle = true) else feed
      }
      state.copy(feeds = updated)
    }
    persistCurrentLibrary()
  }

  fun moveFeedToFolder(feedUrl: String, folderId: String?) {
    _uiState.update { state ->
      val updated = state.feeds.map { feed ->
        if (feed.url == feedUrl) feed.copy(folderId = folderId) else feed
      }
      state.copy(feeds = updated)
    }
    persistCurrentLibrary()
  }

  fun removeFeed(feedUrl: String) {
    _uiState.update { state ->
      val remainingFeeds = state.feeds.filterNot { it.url == feedUrl }
      val remainingArticles = state.articles.filterNot { it.feedUrl == feedUrl }
      val resetFilter =
        if (state.selectedFilter == FeedFilter.FeedItem(feedUrl)) null else state.selectedFilter
      val selectedStillExists =
        state.selectedArticleId != null && remainingArticles.any { it.id == state.selectedArticleId }
      state.copy(
        feeds = remainingFeeds,
        articles = remainingArticles,
        selectedFilter = resetFilter,
        selectedArticleId = if (selectedStillExists) state.selectedArticleId else null,
        imagePermissionArticleId = if (selectedStillExists) state.imagePermissionArticleId else null,
      )
    }
    persistCurrentLibrary()
  }

  fun saveFolder(name: String, editingFolderId: String? = null): String? {
    val validName = ReaderLibrary.validateFolderName(
      input = name,
      folders = _uiState.value.folders,
      excludingId = editingFolderId,
    ) ?: return "Use a unique name of 1–80 characters, other than Unfiled."

    _uiState.update { state ->
      val updatedFolders =
        if (editingFolderId != null) {
          state.folders.map { folder ->
            if (folder.id == editingFolderId) folder.copy(name = validName) else folder
          }
        } else {
          state.folders + FeedFolder(id = UUID.randomUUID().toString(), name = validName)
        }
      state.copy(folders = updatedFolders)
    }
    persistCurrentLibrary()
    return null
  }

  fun removeFolder(folderId: String) {
    _uiState.update { state ->
      val (updatedFolders, updatedFeeds) = ReaderLibrary.removeFolder(
        folderId = folderId,
        folders = state.folders,
        feeds = state.feeds,
      )
      val resetFilter =
        if (state.selectedFilter == FeedFilter.Folder(folderId)) null else state.selectedFilter
      state.copy(
        folders = updatedFolders,
        feeds = updatedFeeds,
        selectedFilter = resetFilter,
      )
    }
    persistCurrentLibrary()
  }

  fun setArticleRetention(limit: Int) {
    if (limit !in ReaderPreferences.RETENTION_OPTIONS) return
    updatePreferences { it.copy(articleRetention = limit) }
  }

  fun setRefreshOnOpen(enabled: Boolean) {
    updatePreferences { it.copy(refreshOnOpen = enabled) }
  }

  fun setArticleOrder(order: ArticleOrder) {
    updatePreferences { it.copy(articleOrder = order) }
  }

  fun setFeedOrder(order: FeedOrder) {
    updatePreferences { it.copy(feedOrder = order) }
  }

  fun setDateDisplay(display: DateDisplay) {
    updatePreferences { it.copy(dateDisplay = display) }
  }

  fun applyRetentionToAllFeeds() {
    _uiState.update { state ->
      val limit = state.preferences.articleRetention
      val updatedFeeds = state.feeds.map { it.copy(retentionEnabled = true) }
      var trimmed = state.articles
      for ((url) in updatedFeeds) {
        trimmed = ReaderLibrary.trimFeedArticles(trimmed, url, limit)
      }
      val selectedStillExists =
        state.selectedArticleId != null && trimmed.any { it.id == state.selectedArticleId }
      state.copy(
        feeds = updatedFeeds,
        articles = trimmed,
        selectedArticleId = if (selectedStillExists) state.selectedArticleId else null,
        imagePermissionArticleId = if (selectedStillExists) state.imagePermissionArticleId else null,
      )
    }
    persistCurrentLibrary()
  }

  fun importOpml(data: ByteArray) {
    if (_uiState.value.isRefreshing) {
      _uiState.update {
        it.copy(errorMessage = "Wait for the current refresh to finish, then try again.")
      }
      return
    }
    viewModelScope.launch {
      val importedFeeds =
        try {
          OpmlParser.parse(data)
        } catch (e: Exception) {
          _uiState.update { it.copy(errorMessage = e.message ?: "Invalid OPML file.") }
          return@launch
        }

      if (importedFeeds.isEmpty()) {
        _uiState.update { it.copy(importSummary = "No feeds were found in that file.") }
        return@launch
      }

      val currentState = _uiState.value
      val knownUrls = currentState.feeds.map { it.url }.toMutableSet()
      val updatedFolders = currentState.folders.toMutableList()
      val addedFeeds = mutableListOf<Feed>()
      var invalidCount = 0

      for ((title, url, folder) in importedFeeds) {
        val allowedUrl = ReaderUrl.allowed(url)
        if (allowedUrl == null) {
          invalidCount++
          continue
        }
        if (!knownUrls.add(allowedUrl)) {
          continue
        }
        val folderId = resolveOrCreateFolderId(folder, updatedFolders)
        addedFeeds.add(
          Feed(
            url = allowedUrl,
            title = title,
            folderId = folderId,
            retentionEnabled = true,
          )
        )
      }

      val duplicateCount = importedFeeds.size - addedFeeds.size - invalidCount
      _uiState.update { state ->
        state.copy(
          folders = updatedFolders,
          feeds = state.feeds + addedFeeds,
        )
      }

      try {
        saveLibraryOrThrow()
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = e.message ?: "Couldn't save imported feeds.") }
        return@launch
      }

      val failures =
        if (addedFeeds.isNotEmpty()) {
          _uiState.update { it.copy(isRefreshing = true) }
          try {
            refreshFeedsList(addedFeeds)
          } finally {
            _uiState.update { it.copy(isRefreshing = false) }
          }
        } else {
          emptyList()
        }

      _uiState.update {
        it.copy(
          importSummary = ReaderLibrary.importSummaryText(
            addedCount = addedFeeds.size,
            duplicateCount = duplicateCount,
            invalidCount = invalidCount,
            failures = failures,
          )
        )
      }
    }
  }

  private fun resolveOrCreateFolderId(
    rawName: String?,
    folders: MutableList<FeedFolder>,
  ): String? {
    val trimmed = rawName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val existing = folders.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
    if (existing != null) return existing.id
    val validName = ReaderLibrary.validateFolderName(trimmed, folders) ?: return null
    val created = FeedFolder(id = UUID.randomUUID().toString(), name = validName)
    folders.add(created)
    return created.id
  }

  fun exportOpml(nowMillis: Long = clock()): String {
    val state = _uiState.value
    val opmlFeeds = state.sortedFeeds.map { feed ->
      val folderName = state.folders.firstOrNull { it.id == feed.folderId }?.name
      OpmlFeed(
        title = feed.title,
        url = feed.url,
        folder = folderName,
      )
    }
    return OpmlWriter.write(opmlFeeds, nowMillis)
  }

  fun clearErrorMessage() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  fun clearImportSummary() {
    _uiState.update { it.copy(importSummary = null) }
  }

  fun showErrorMessage(message: String) {
    _uiState.update { it.copy(errorMessage = message) }
  }

  private fun updatePreferences(transform: (ReaderPreferences) -> ReaderPreferences) {
    _uiState.update { state ->
      val updated = transform(state.preferences)
      preferencesRepository.save(updated)
      state.copy(preferences = updated)
    }
  }

  /**
   * Saves the library after a short pause so a burst of taps produces one write.
   * A newer request replaces a pending one; the write always uses the latest state.
   */
  private fun persistCurrentLibrary(debounce: Boolean = true) {
    pendingSave?.cancel()
    pendingSave = viewModelScope.launch {
      try {
        if (debounce) delay(SAVE_DEBOUNCE_MS.milliseconds)
        saveLibraryOrThrow()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = e.message ?: "Couldn't save changes.") }
      }
    }
  }

  private suspend fun saveLibraryOrThrow() {
    saveMutex.withLock {
      // Read the state inside the lock: a save that starts later always writes
      // state at least as new as the save before it.
      val state = _uiState.value
      activeStore.save(
        LibrarySnapshot(
          feeds = state.feeds,
          articles = state.articles,
          folders = state.folders,
        )
      )
    }
  }

  private fun friendlyMessage(error: Throwable): String =
    when (error) {
      is FeedException -> error.message
      else -> error.message ?: "An unexpected error occurred."
    }

  companion object {
    const val AUTO_REFRESH_INTERVAL_MS = 15L * 60L * 1000L
    private const val MAX_CONCURRENT_REFRESHES = 6
    const val SAVE_DEBOUNCE_MS = 400L
  }
}

/**
 * Factory for creating [ReaderViewModel] with explicit dependencies.
 */
class ReaderViewModelFactory(
  private val libraryStore: LibraryStore,
  private val preferencesRepository: PreferencesRepository,
  private val feedFetcher: FeedFetcher,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
      "Unknown ViewModel class: ${modelClass.name}"
    }
    return ReaderViewModel(
      primaryStore = libraryStore,
      preferencesRepository = preferencesRepository,
      feedFetcher = feedFetcher,
    ) as T
  }
}
