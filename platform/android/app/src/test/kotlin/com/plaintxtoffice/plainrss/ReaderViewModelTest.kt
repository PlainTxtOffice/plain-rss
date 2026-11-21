package com.plaintxtoffice.plainrss

import com.plaintxtoffice.plainrss.data.InMemoryPreferencesRepository
import com.plaintxtoffice.plainrss.model.FeedFetchResult
import com.plaintxtoffice.plainrss.model.FeedFilter
import com.plaintxtoffice.plainrss.model.FetchedFeed
import com.plaintxtoffice.plainrss.model.ParsedArticle
import com.plaintxtoffice.plainrss.model.ParsedFeed
import com.plaintxtoffice.plainrss.network.FeedFetcher
import com.plaintxtoffice.plainrss.storage.InMemoryLibraryStore
import com.plaintxtoffice.plainrss.storage.LibrarySnapshot
import com.plaintxtoffice.plainrss.storage.LibraryStore
import com.plaintxtoffice.plainrss.viewmodel.ReaderViewModel
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun addFeed_prependsHttpsAndStoresFetchedArticles() = runTest {
    val fakeFetcher = FakeFeedFetcher().apply {
      feeds["https://example.com/feed.xml"] = FetchedFeed(
        parsed = ParsedFeed(
          title = "Example Feed",
          articles = listOf(
            ParsedArticle(
              title = "Hello World",
              link = "https://example.com/1",
              summary = "Summary 1",
              guid = "g1",
              publishedAt = 1_000L,
            )
          ),
        ),
        etag = "\"v1\"",
      )
    }
    val viewModel = ReaderViewModel(
      primaryStore = InMemoryLibraryStore(),
      preferencesRepository = InMemoryPreferencesRepository(),
      feedFetcher = fakeFetcher,
    )

    val error = viewModel.addFeed("example.com/feed.xml")
    assertNull(error)

    val state = viewModel.uiState.value
    assertEquals(1, state.feeds.size)
    assertEquals("Example Feed", state.feeds.first().title)
    assertEquals(1, state.articles.size)
    assertEquals("Hello World", state.articles.first().title)

    // Duplicate check
    val duplicateError = viewModel.addFeed("https://example.com/feed.xml")
    assertEquals("That feed is already added.", duplicateError)
  }

  @Test
  fun selectArticleAndImagePermissions_marksReadAndResetsPermissionOnSwitchOrBackground() = runTest {
    val fakeFetcher = FakeFeedFetcher().apply {
      feeds["https://example.com/feed.xml"] = FetchedFeed(
        parsed = ParsedFeed(
          title = "Example Feed",
          articles = listOf(
            ParsedArticle(title = "Post 1", guid = "g1", publishedAt = 2_000L),
            ParsedArticle(title = "Post 2", guid = "g2", publishedAt = 1_000L),
          ),
        )
      )
    }
    val viewModel = ReaderViewModel(
      primaryStore = InMemoryLibraryStore(),
      preferencesRepository = InMemoryPreferencesRepository(),
      feedFetcher = fakeFetcher,
    )
    viewModel.addFeed("https://example.com/feed.xml")

    val articles = viewModel.uiState.value.visibleArticles
    val firstId = articles[0].id
    val secondId = articles[1].id

    viewModel.selectArticle(firstId)
    assertTrue(viewModel.uiState.value.articles.first { it.id == firstId }.isRead)
    assertFalse(viewModel.uiState.value.imagesAllowedForSelectedArticle)

    viewModel.allowImagesForCurrentArticle()
    assertTrue(viewModel.uiState.value.imagesAllowedForSelectedArticle)

    // Switching articles revokes temporary image permission
    viewModel.selectArticle(secondId)
    assertFalse(viewModel.uiState.value.imagesAllowedForSelectedArticle)

    viewModel.allowImagesForCurrentArticle()
    assertTrue(viewModel.uiState.value.imagesAllowedForSelectedArticle)

    // Backgrounding revokes temporary image permission
    viewModel.onAppBackgrounded()
    assertFalse(viewModel.uiState.value.imagesAllowedForSelectedArticle)
  }

  @Test
  fun foldersAndFiltering_organizesFeedsAndMovesToUnfiledOnFolderRemoval() = runTest {
    val fakeFetcher = FakeFeedFetcher().apply {
      feeds["https://example.com/feed.xml"] = FetchedFeed(
        parsed = ParsedFeed(
          title = "Tech Feed",
          articles = listOf(ParsedArticle(title = "Kotlin News", guid = "k1")),
        )
      )
    }
    val viewModel = ReaderViewModel(
      primaryStore = InMemoryLibraryStore(),
      preferencesRepository = InMemoryPreferencesRepository(),
      feedFetcher = fakeFetcher,
    )
    viewModel.addFeed("https://example.com/feed.xml")

    assertNull(viewModel.saveFolder("Technology"))
    val folder = viewModel.uiState.value.folders.single()
    viewModel.moveFeedToFolder("https://example.com/feed.xml", folder.id)

    viewModel.selectFilter(FeedFilter.Folder(folder.id))
    assertEquals(1, viewModel.uiState.value.visibleArticles.size)

    viewModel.selectFilter(FeedFilter.Unfiled)
    assertEquals(0, viewModel.uiState.value.visibleArticles.size)

    viewModel.removeFolder(folder.id)
    assertTrue(viewModel.uiState.value.folders.isEmpty())
    assertNull(viewModel.uiState.value.feeds.single().folderId)
  }

  @Test
  fun storageRecovery_offersRetryAndTemporaryModeWithoutOverwritingFailedStore() = runTest {
    var shouldFail = true
    val flakyStore = object : LibraryStore {
      override suspend fun load(): LibrarySnapshot {
        if (shouldFail) throw IOException("Corrupt library")
        return LibrarySnapshot()
      }

      override suspend fun save(snapshot: LibrarySnapshot) {
        if (shouldFail) throw IOException("Cannot write")
      }
    }

    val viewModel = ReaderViewModel(
      primaryStore = flakyStore,
      preferencesRepository = InMemoryPreferencesRepository(),
      feedFetcher = FakeFeedFetcher(),
    )

    assertNotNull(viewModel.uiState.value.storageError)
    assertFalse(viewModel.uiState.value.isLoaded)

    viewModel.continueWithoutSaving()
    assertNull(viewModel.uiState.value.storageError)
    assertTrue(viewModel.uiState.value.usingTemporaryStorage)

    shouldFail = false
    viewModel.retryStorage()
    assertNull(viewModel.uiState.value.storageError)
    assertFalse(viewModel.uiState.value.usingTemporaryStorage)
    assertTrue(viewModel.uiState.value.isLoaded)
  }

  @Test
  fun removingFeedDuringRefresh_doesNotBringItBack() = runTest {
    val url = "https://example.com/feed.xml"
    val feed = FetchedFeed(
      parsed = ParsedFeed(title = "Example Feed", articles = listOf(ParsedArticle(title = "Post", guid = "g1")))
    )
    val gate = CompletableDeferred<Unit>()
    val fetcher = object : FeedFetcher {
      override suspend fun fetch(url: String, etag: String?, lastModified: String?): FeedFetchResult {
        gate.await()
        return FeedFetchResult.Fetched(feed)
      }

      override suspend fun fetchNew(url: String): Pair<String, FetchedFeed> = url to feed
    }
    val viewModel = ReaderViewModel(
      primaryStore = InMemoryLibraryStore(),
      preferencesRepository = InMemoryPreferencesRepository(),
      feedFetcher = fetcher,
    )
    viewModel.addFeed(url)

    viewModel.refreshAllFeeds()
    assertTrue(viewModel.uiState.value.isRefreshing)
    viewModel.removeFeed(url)
    gate.complete(Unit)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isRefreshing)
    assertTrue(viewModel.uiState.value.feeds.isEmpty())
    assertTrue(viewModel.uiState.value.articles.isEmpty())
  }

  @Test
  fun renamedFeed_keepsItsNameAfterRefresh() = runTest {
    val url = "https://example.com/feed.xml"
    val otherUrl = "https://example.org/feed.xml"
    val fakeFetcher = FakeFeedFetcher().apply {
      feeds[url] = FetchedFeed(parsed = ParsedFeed(title = "Publisher Title", articles = emptyList()))
      feeds[otherUrl] = FetchedFeed(parsed = ParsedFeed(title = "Other Title", articles = emptyList()))
    }
    val store = InMemoryLibraryStore()
    val viewModel = ReaderViewModel(
      primaryStore = store,
      preferencesRepository = InMemoryPreferencesRepository(),
      feedFetcher = fakeFetcher,
    )
    viewModel.addFeed(url)
    viewModel.addFeed(otherUrl)
    viewModel.renameFeed(url, "  My Name  ")
    fakeFetcher.feeds[url] =
      FetchedFeed(parsed = ParsedFeed(title = "New Publisher Title", articles = emptyList()))
    fakeFetcher.feeds[otherUrl] =
      FetchedFeed(parsed = ParsedFeed(title = "New Other Title", articles = emptyList()))

    viewModel.refreshAllFeeds()
    advanceUntilIdle()

    val titles = store.load().feeds.associate { it.url to it.title }
    assertEquals("My Name", titles[url])
    // A feed the user never renamed still follows the publisher.
    assertEquals("New Other Title", titles[otherUrl])
  }

  @Test
  fun rapidChanges_areSavedOnceWithTheLatestState() = runTest {
    val saved = mutableListOf<LibrarySnapshot>()
    val countingStore = object : LibraryStore {
      override suspend fun load(): LibrarySnapshot = LibrarySnapshot()

      override suspend fun save(snapshot: LibrarySnapshot) {
        saved.add(snapshot)
      }
    }
    val fakeFetcher = FakeFeedFetcher().apply {
      feeds["https://example.com/feed.xml"] = FetchedFeed(
        parsed = ParsedFeed(title = "Example Feed", articles = listOf(ParsedArticle(title = "Post", guid = "g1")))
      )
    }
    val viewModel = ReaderViewModel(
      primaryStore = countingStore,
      preferencesRepository = InMemoryPreferencesRepository(),
      feedFetcher = fakeFetcher,
    )
    viewModel.addFeed("https://example.com/feed.xml")
    val savesAfterAdd = saved.size
    val articleId = viewModel.uiState.value.articles.single().id

    viewModel.toggleArticleStarred(articleId)
    viewModel.toggleArticleRead(articleId)
    viewModel.toggleArticleStarred(articleId)
    viewModel.toggleArticleStarred(articleId)
    assertEquals(savesAfterAdd, saved.size)
    advanceUntilIdle()

    assertEquals(savesAfterAdd + 1, saved.size)
    val article = saved.last().articles.single()
    assertTrue(article.isStarred)
    assertTrue(article.isRead)
  }

  @Test
  fun backgrounding_flushesAPendingSaveImmediately() = runTest {
    var saves = 0
    val countingStore = object : LibraryStore {
      override suspend fun load(): LibrarySnapshot = LibrarySnapshot()

      override suspend fun save(snapshot: LibrarySnapshot) {
        saves++
      }
    }
    val viewModel = ReaderViewModel(
      primaryStore = countingStore,
      preferencesRepository = InMemoryPreferencesRepository(),
      feedFetcher = FakeFeedFetcher(),
    )
    assertNull(viewModel.saveFolder("Technology"))
    assertEquals(0, saves)

    viewModel.onAppBackgrounded()

    assertEquals(1, saves)
  }

  private class FakeFeedFetcher : FeedFetcher {
    val feeds = mutableMapOf<String, FetchedFeed>()

    override suspend fun fetch(
      url: String,
      etag: String?,
      lastModified: String?,
    ): FeedFetchResult {
      val feed = feeds[url] ?: throw IOException("Not found: $url")
      return FeedFetchResult.Fetched(feed)
    }

    override suspend fun fetchNew(url: String): Pair<String, FetchedFeed> {
      val feed = feeds[url] ?: throw IOException("Not found: $url")
      return url to feed
    }
  }
}
