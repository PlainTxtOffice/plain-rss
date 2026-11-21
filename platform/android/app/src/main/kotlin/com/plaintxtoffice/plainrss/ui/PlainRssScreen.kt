package com.plaintxtoffice.plainrss.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.plaintxtoffice.plainrss.R
import com.plaintxtoffice.plainrss.domain.ReaderInput
import com.plaintxtoffice.plainrss.domain.ReaderLibrary
import com.plaintxtoffice.plainrss.domain.ReaderSanitizer
import com.plaintxtoffice.plainrss.domain.ReaderUrl
import com.plaintxtoffice.plainrss.model.Article
import com.plaintxtoffice.plainrss.model.ArticleOrder
import com.plaintxtoffice.plainrss.model.DateDisplay
import com.plaintxtoffice.plainrss.model.Feed
import com.plaintxtoffice.plainrss.model.FeedFilter
import com.plaintxtoffice.plainrss.model.FeedFolder
import com.plaintxtoffice.plainrss.model.FeedOrder
import com.plaintxtoffice.plainrss.network.ReaderImageFetcher
import com.plaintxtoffice.plainrss.ui.theme.PlainRssTheme
import com.plaintxtoffice.plainrss.ui.theme.RssOrangeLight
import com.plaintxtoffice.plainrss.viewmodel.ReaderUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Top-level adaptive screen for Plain RSS.
 */
@Composable
fun PlainRssScreen(
  uiState: ReaderUiState,
  imageFetcher: ReaderImageFetcher,
  onSelectFilter: (FeedFilter?) -> Unit,
  onSelectArticle: (String?) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onToggleArticleRead: (String) -> Unit,
  onToggleArticleStarred: (String) -> Unit,
  onMarkVisibleAsRead: () -> Unit,
  onAllowImages: () -> Unit,
  onHideImages: () -> Unit,
  onAddFeed: suspend (String) -> String?,
  onRefreshAll: () -> Unit,
  onRenameFeed: (url: String, newName: String) -> Unit,
  onMoveFeedToFolder: (url: String, folderId: String?) -> Unit,
  onRemoveFeed: (url: String) -> Unit,
  onSaveFolder: (name: String, editingId: String?) -> String?,
  onRemoveFolder: (folderId: String) -> Unit,
  onRetentionChange: (Int) -> Unit,
  onApplyRetentionToAll: () -> Unit,
  onRefreshOnOpenChange: (Boolean) -> Unit,
  onArticleOrderChange: (ArticleOrder) -> Unit,
  onFeedOrderChange: (FeedOrder) -> Unit,
  onDateDisplayChange: (DateDisplay) -> Unit,
  onRequestImportOpml: () -> Unit,
  onRequestExportOpml: () -> Unit,
  onOpenExternalUrl: (String) -> Unit,
  onClearErrorMessage: () -> Unit,
  onClearImportSummary: () -> Unit,
  onRetryStorage: () -> Unit,
  onContinueWithoutSaving: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showingAddFeed by rememberSaveable { mutableStateOf(false) }
  var showingFolders by rememberSaveable { mutableStateOf(false) }
  var showingSettings by rememberSaveable { mutableStateOf(false) }
  var showingPrivacy by rememberSaveable { mutableStateOf(false) }
  var showingFeedback by rememberSaveable { mutableStateOf(false) }
  var confirmMarkAllRead by rememberSaveable { mutableStateOf(false) }
  var feedToDelete by remember { mutableStateOf<Feed?>(null) }
  var feedToRename by remember { mutableStateOf<Feed?>(null) }
  var renameText by rememberSaveable { mutableStateOf("") }

  var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(60_000L.milliseconds)
      nowMillis = System.currentTimeMillis()
    }
  }

  if (uiState.storageError != null) {
    StorageRecoveryDialog(
      errorDetail = uiState.storageError,
      onRetry = onRetryStorage,
      onContinueWithoutSaving = onContinueWithoutSaving,
    )
  }

  if (showingAddFeed) {
    AddFeedDialog(
      onDismiss = { showingAddFeed = false },
      onAddFeed = onAddFeed,
    )
  }

  if (showingFolders) {
    FolderManagerDialog(
      folders = uiState.sortedFolders,
      onSaveFolder = onSaveFolder,
      onRemoveFolder = onRemoveFolder,
      onDismiss = { showingFolders = false },
    )
  }

  if (showingSettings) {
    SettingsDialog(
      preferences = uiState.preferences,
      hasFeeds = uiState.feeds.isNotEmpty(),
      onRetentionChange = onRetentionChange,
      onApplyRetentionToAll = onApplyRetentionToAll,
      onRefreshOnOpenChange = onRefreshOnOpenChange,
      onArticleOrderChange = onArticleOrderChange,
      onFeedOrderChange = onFeedOrderChange,
      onDateDisplayChange = onDateDisplayChange,
      onDismiss = { showingSettings = false },
    )
  }

  if (showingPrivacy) {
    PrivacyPolicyDialog(
      onOpenContactUrl = onOpenExternalUrl,
      onDismiss = { showingPrivacy = false },
    )
  }

  if (showingFeedback) {
    val context = LocalContext.current
    FeedbackDialog(
      onSendEmail = { kind -> openFeedbackEmail(context, kind) },
      onOpenContactForm = { openContactForm(context) },
      onDismiss = { showingFeedback = false },
    )
  }

  if (confirmMarkAllRead) {
    AlertDialog(
      onDismissRequest = { confirmMarkAllRead = false },
      title = { Text(stringResource(R.string.mark_all_read_dialog_title)) },
      confirmButton = {
        Button(
          onClick = {
            confirmMarkAllRead = false
            onMarkVisibleAsRead()
          }
        ) {
          Text(stringResource(R.string.mark_all_read_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmMarkAllRead = false }) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    )
  }

  feedToRename?.let { feed ->
    AlertDialog(
      onDismissRequest = { feedToRename = null },
      title = { Text(stringResource(R.string.rename_feed_dialog_title)) },
      text = {
        OutlinedTextField(
          value = renameText,
          onValueChange = { renameText = it },
          label = { Text(stringResource(R.string.rename_feed_label)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onRenameFeed(feed.url, renameText)
            feedToRename = null
          },
          enabled = renameText.trim().isNotEmpty(),
        ) {
          Text(stringResource(R.string.action_save))
        }
      },
      dismissButton = {
        TextButton(onClick = { feedToRename = null }) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    )
  }

  feedToDelete?.let { feed ->
    AlertDialog(
      onDismissRequest = { feedToDelete = null },
      title = { Text(stringResource(R.string.remove_feed_dialog_title)) },
      text = {
        Text(stringResource(R.string.remove_feed_dialog_message))
      },
      confirmButton = {
        Button(
          onClick = {
            onRemoveFeed(feed.url)
            feedToDelete = null
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
        ) {
          Text(stringResource(R.string.remove_feed_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { feedToDelete = null }) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    )
  }

  if (uiState.errorMessage != null) {
    AlertDialog(
      onDismissRequest = onClearErrorMessage,
      title = { Text(stringResource(R.string.error_dialog_title)) },
      text = { Text(uiState.errorMessage) },
      confirmButton = {
        TextButton(onClick = onClearErrorMessage) {
          Text(stringResource(R.string.action_ok))
        }
      },
    )
  }

  if (uiState.importSummary != null) {
    AlertDialog(
      onDismissRequest = onClearImportSummary,
      title = { Text(stringResource(R.string.import_complete_title)) },
      text = { Text(uiState.importSummary) },
      confirmButton = {
        TextButton(onClick = onClearImportSummary) {
          Text(stringResource(R.string.action_ok))
        }
      },
    )
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isExpanded = maxWidth >= 720.dp

    LaunchedEffect(isExpanded) {
      if (isExpanded && uiState.selectedFilter == null) {
        onSelectFilter(FeedFilter.All)
      }
    }

    if (isExpanded) {
      Row(modifier = Modifier.fillMaxSize()) {
        FeedsPane(
          uiState = uiState,
          onSelectFilter = onSelectFilter,
          onOpenFolders = { showingFolders = true },
          onRefreshAll = onRefreshAll,
          onOpenAddFeed = { showingAddFeed = true },
          onOpenSettings = { showingSettings = true },
          onOpenPrivacy = { showingPrivacy = true },
          onOpenFeedback = { showingFeedback = true },
          onRequestImportOpml = onRequestImportOpml,
          onRequestExportOpml = onRequestExportOpml,
          onRequestRenameFeed = { feed ->
            renameText = feed.title
            feedToRename = feed
          },
          onMoveFeedToFolder = onMoveFeedToFolder,
          onRequestRemoveFeed = { feed -> feedToDelete = feed },
          modifier = Modifier
            .width(260.dp)
            .fillMaxHeight(),
        )
        VerticalDivider()
        ArticlesPane(
          uiState = uiState,
          nowMillis = nowMillis,
          showBackButton = false,
          onBack = {},
          onSearchQueryChange = onSearchQueryChange,
          onSelectArticle = onSelectArticle,
          onToggleArticleRead = onToggleArticleRead,
          onToggleArticleStarred = onToggleArticleStarred,
          onRequestMarkAllRead = { confirmMarkAllRead = true },
          onOpenAddFeed = { showingAddFeed = true },
          onRefreshAll = onRefreshAll,
          modifier = Modifier
            .width(340.dp)
            .fillMaxHeight(),
        )
        VerticalDivider()
        val selectedArticle = uiState.selectedArticle
        if (selectedArticle != null) {
          ArticleDetailPane(
            article = selectedArticle,
            feedTitle = uiState.selectedArticleFeedTitle,
            dateDisplay = uiState.preferences.dateDisplay,
            nowMillis = nowMillis,
            imagesAllowed = uiState.imagesAllowedForSelectedArticle,
            imageFetcher = imageFetcher,
            showBackButton = false,
            onBack = {},
            onToggleStarred = { onToggleArticleStarred(selectedArticle.id) },
            onAllowImages = onAllowImages,
            onHideImages = onHideImages,
            onOpenExternalUrl = onOpenExternalUrl,
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight(),
          )
        } else {
          EmptySelectionPane(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight(),
          )
        }
      }
    } else {
      val selectedArticle = uiState.selectedArticle
      when {
        selectedArticle != null -> {
          BackHandler { onSelectArticle(null) }
          ArticleDetailPane(
            article = selectedArticle,
            feedTitle = uiState.selectedArticleFeedTitle,
            dateDisplay = uiState.preferences.dateDisplay,
            nowMillis = nowMillis,
            imagesAllowed = uiState.imagesAllowedForSelectedArticle,
            imageFetcher = imageFetcher,
            showBackButton = true,
            onBack = { onSelectArticle(null) },
            onToggleStarred = { onToggleArticleStarred(selectedArticle.id) },
            onAllowImages = onAllowImages,
            onHideImages = onHideImages,
            onOpenExternalUrl = onOpenExternalUrl,
            modifier = Modifier.fillMaxSize(),
          )
        }
        uiState.selectedFilter != null -> {
          BackHandler { onSelectFilter(null) }
          ArticlesPane(
            uiState = uiState,
            nowMillis = nowMillis,
            showBackButton = true,
            onBack = { onSelectFilter(null) },
            onSearchQueryChange = onSearchQueryChange,
            onSelectArticle = onSelectArticle,
            onToggleArticleRead = onToggleArticleRead,
            onToggleArticleStarred = onToggleArticleStarred,
            onRequestMarkAllRead = { confirmMarkAllRead = true },
            onOpenAddFeed = { showingAddFeed = true },
            onRefreshAll = onRefreshAll,
            modifier = Modifier.fillMaxSize(),
          )
        }
        else -> {
          FeedsPane(
            uiState = uiState,
            onSelectFilter = onSelectFilter,
            onOpenFolders = { showingFolders = true },
            onRefreshAll = onRefreshAll,
            onOpenAddFeed = { showingAddFeed = true },
            onOpenSettings = { showingSettings = true },
            onOpenPrivacy = { showingPrivacy = true },
          onOpenFeedback = { showingFeedback = true },
            onRequestImportOpml = onRequestImportOpml,
            onRequestExportOpml = onRequestExportOpml,
            onRequestRenameFeed = { feed ->
              renameText = feed.title
              feedToRename = feed
            },
            onMoveFeedToFolder = onMoveFeedToFolder,
            onRequestRemoveFeed = { feed -> feedToDelete = feed },
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedsPane(
  uiState: ReaderUiState,
  onSelectFilter: (FeedFilter?) -> Unit,
  onOpenFolders: () -> Unit,
  onRefreshAll: () -> Unit,
  onOpenAddFeed: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenPrivacy: () -> Unit,
  onOpenFeedback: () -> Unit,
  onRequestImportOpml: () -> Unit,
  onRequestExportOpml: () -> Unit,
  onRequestRenameFeed: (Feed) -> Unit,
  onMoveFeedToFolder: (url: String, folderId: String?) -> Unit,
  onRequestRemoveFeed: (Feed) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showingMoreMenu by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier,
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
          TextButton(
            onClick = onOpenFolders,
            modifier = Modifier.testTag("foldersButton"),
          ) {
            Text(stringResource(R.string.action_folders))
          }
          IconButton(
            onClick = onRefreshAll,
            enabled = uiState.feeds.isNotEmpty() && !uiState.isRefreshing,
            modifier = Modifier.testTag("refreshFeedsButton"),
          ) {
            if (uiState.isRefreshing) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
              )
            } else {
              Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
          }
          IconButton(
            onClick = onOpenAddFeed,
            modifier = Modifier.testTag("addFeedButton"),
          ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_feed))
          }
          Box {
            IconButton(
              onClick = { showingMoreMenu = true },
              modifier = Modifier.testTag("moreMenuButton"),
            ) {
              Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
            }
            DropdownMenu(
              expanded = showingMoreMenu,
              onDismissRequest = { showingMoreMenu = false },
            ) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.action_import_opml)) },
                enabled = !uiState.isRefreshing,
                onClick = {
                  showingMoreMenu = false
                  onRequestImportOpml()
                },
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.action_settings)) },
                onClick = {
                  showingMoreMenu = false
                  onOpenSettings()
                },
                modifier = Modifier.testTag("readerSettingsButton"),
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.action_privacy)) },
                onClick = {
                  showingMoreMenu = false
                  onOpenPrivacy()
                },
                modifier = Modifier.testTag("privacyPolicyButton"),
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.action_send_feedback)) },
                onClick = {
                  showingMoreMenu = false
                  onOpenFeedback()
                },
                modifier = Modifier.testTag("sendFeedbackButton"),
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.action_export_opml)) },
                enabled = uiState.feeds.isNotEmpty(),
                onClick = {
                  showingMoreMenu = false
                  onRequestExportOpml()
                },
              )
            }
          }
        },
      )
    },
  ) { innerPadding ->
    val unreadCounts = uiState.unreadCountsByFeed
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .consumeWindowInsets(innerPadding)
        .testTag("feedsList"),
      contentPadding = innerPadding,
    ) {
      if (uiState.usingTemporaryStorage) {
        item(key = "temporary_storage_banner") {
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.small,
          ) {
            Text(
              text = stringResource(R.string.temporary_storage_banner),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(12.dp),
            )
          }
        }
      }

      item(key = "filter_all") {
        FilterRowItem(
          title = stringResource(R.string.filter_all_articles),
          badgeCount = null,
          selected = uiState.selectedFilter == FeedFilter.All,
          onClick = { onSelectFilter(FeedFilter.All) },
          testTag = "filterAllArticles",
        )
      }
      item(key = "filter_unread") {
        FilterRowItem(
          title = stringResource(R.string.filter_unread),
          badgeCount = uiState.totalUnreadCount.takeIf { it > 0 },
          selected = uiState.selectedFilter == FeedFilter.Unread,
          onClick = { onSelectFilter(FeedFilter.Unread) },
          testTag = "filterUnread",
        )
      }
      item(key = "filter_starred") {
        FilterRowItem(
          title = stringResource(R.string.filter_starred),
          badgeCount = null,
          selected = uiState.selectedFilter == FeedFilter.Starred,
          onClick = { onSelectFilter(FeedFilter.Starred) },
          testTag = "filterStarred",
        )
      }
      item(key = "filter_unfiled") {
        FilterRowItem(
          title = stringResource(R.string.filter_unfiled),
          badgeCount = null,
          selected = uiState.selectedFilter == FeedFilter.Unfiled,
          onClick = { onSelectFilter(FeedFilter.Unfiled) },
          testTag = "filterUnfiled",
        )
      }

      items(uiState.unfiledFeeds, key = { "unfiled_${it.url}" }) { feed ->
        FeedRowItem(
          feed = feed,
          unreadCount = unreadCounts[feed.url] ?: 0,
          folders = uiState.sortedFolders,
          selected = uiState.selectedFilter == FeedFilter.FeedItem(feed.url),
          indented = false,
          onClick = { onSelectFilter(FeedFilter.FeedItem(feed.url)) },
          onMoveToFolder = { folderId -> onMoveFeedToFolder(feed.url, folderId) },
          onRename = { onRequestRenameFeed(feed) },
          onRemove = { onRequestRemoveFeed(feed) },
        )
      }

      for ((id, name) in uiState.sortedFolders) {
        item(key = "folder_$id") {
          FilterRowItem(
            title = name,
            badgeCount = null,
            selected = uiState.selectedFilter == FeedFilter.Folder(id),
            onClick = { onSelectFilter(FeedFilter.Folder(id)) },
            testTag = "folder_$name",
          )
        }
        val folderFeeds = uiState.feedsInFolder(id)
        items(folderFeeds, key = { "folder_${id}_${it.url}" }) { feed ->
          FeedRowItem(
            feed = feed,
            unreadCount = unreadCounts[feed.url] ?: 0,
            folders = uiState.sortedFolders,
            selected = uiState.selectedFilter == FeedFilter.FeedItem(feed.url),
            indented = true,
            onClick = { onSelectFilter(FeedFilter.FeedItem(feed.url)) },
            onMoveToFolder = { targetId -> onMoveFeedToFolder(feed.url, targetId) },
            onRename = { onRequestRenameFeed(feed) },
            onRemove = { onRequestRemoveFeed(feed) },
          )
        }
      }

      if (uiState.feeds.isEmpty()) {
        item(key = "empty_feeds_actions") {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Button(
              onClick = onOpenAddFeed,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("addFirstFeedButton"),
            ) {
              Text(stringResource(R.string.action_add_first_feed))
            }
            OutlinedButton(
              onClick = onRequestImportOpml,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("importFromAnotherAppButton"),
            ) {
              Text(stringResource(R.string.action_import_another_app))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FilterRowItem(
  title: String,
  badgeCount: Int?,
  selected: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  val containerColor =
    if (selected) {
      MaterialTheme.colorScheme.secondaryContainer
    } else {
      Color.Transparent
    }
  ListItem(
    headlineContent = {
      Text(
        text = title,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      )
    },
    trailingContent = {
      if (badgeCount != null && badgeCount > 0) {
        Text(
          text = badgeCount.toString(),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    colors = ListItemDefaults.colors(containerColor = containerColor),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag(testTag),
  )
}

@Composable
private fun FeedRowItem(
  feed: Feed,
  unreadCount: Int,
  folders: List<FeedFolder>,
  selected: Boolean,
  indented: Boolean,
  onClick: () -> Unit,
  onMoveToFolder: (String?) -> Unit,
  onRename: () -> Unit,
  onRemove: () -> Unit,
) {
  var showMenu by remember { mutableStateOf(false) }
  var showMoveSubmenu by remember { mutableStateOf(false) }
  val containerColor =
    if (selected) {
      MaterialTheme.colorScheme.secondaryContainer
    } else {
      Color.Transparent
    }

  ListItem(
    headlineContent = {
      Text(
        text = feed.title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    supportingContent = {
      Text(
        text = feed.url,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    trailingContent = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (unreadCount > 0) {
          Text(
            text = unreadCount.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Box {
          IconButton(
            onClick = {
              showMoveSubmenu = false
              showMenu = true
            },
            modifier = Modifier.testTag("feedMenu_${feed.url}"),
          ) {
            Icon(
              Icons.Default.MoreVert,
              contentDescription = stringResource(R.string.feed_options_description, feed.title),
            )
          }
          DropdownMenu(
            expanded = showMenu,
            onDismissRequest = {
              showMenu = false
              showMoveSubmenu = false
            },
          ) {
            if (!showMoveSubmenu) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.move_to_folder)) },
                onClick = { showMoveSubmenu = true },
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.rename_ellipsis)) },
                onClick = {
                  showMenu = false
                  onRename()
                },
              )
              DropdownMenuItem(
                text = {
                  Text(
                    text = stringResource(R.string.remove_feed_ellipsis),
                    color = MaterialTheme.colorScheme.error,
                  )
                },
                onClick = {
                  showMenu = false
                  onRemove()
                },
              )
            } else {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_unfiled)) },
                onClick = {
                  showMenu = false
                  showMoveSubmenu = false
                  onMoveToFolder(null)
                },
              )
              for ((id, name) in folders) {
                DropdownMenuItem(
                  text = { Text(name) },
                  onClick = {
                    showMenu = false
                    showMoveSubmenu = false
                    onMoveToFolder(id)
                  },
                )
              }
            }
          }
        }
      }
    },
    colors = ListItemDefaults.colors(containerColor = containerColor),
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = if (indented) 16.dp else 0.dp)
      .clickable(onClick = onClick)
      .testTag("feedRow_${feed.url}"),
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticlesPane(
  uiState: ReaderUiState,
  nowMillis: Long,
  showBackButton: Boolean,
  onBack: () -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onSelectArticle: (String?) -> Unit,
  onToggleArticleRead: (String) -> Unit,
  onToggleArticleStarred: (String) -> Unit,
  onRequestMarkAllRead: () -> Unit,
  onOpenAddFeed: () -> Unit,
  onRefreshAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val articles = uiState.visibleArticles
  val feedsByUrl = remember(uiState.feeds) { uiState.feeds.associateBy { it.url } }

  Scaffold(
    modifier = modifier,
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = articlesPaneTitle(
              selectedFilter = uiState.selectedFilter,
              folders = uiState.folders,
              feeds = uiState.feeds,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          if (showBackButton) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.testTag("articlesBackButton"),
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
          }
        },
        actions = {
          IconButton(
            onClick = onRefreshAll,
            enabled = uiState.feeds.isNotEmpty() && !uiState.isRefreshing,
            modifier = Modifier.testTag("articlesRefreshButton"),
          ) {
            if (uiState.isRefreshing) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
              )
            } else {
              Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
          }
          IconButton(
            onClick = onRequestMarkAllRead,
            enabled = articles.any { !it.isRead },
            modifier = Modifier.testTag("markAllReadButton"),
          ) {
            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_mark_all_read))
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .consumeWindowInsets(innerPadding)
    ) {
      OutlinedTextField(
        value = uiState.searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text(stringResource(R.string.search_articles_placeholder)) },
        singleLine = true,
        leadingIcon = {
          Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
          if (uiState.searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchQueryChange("") }) {
              Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear_search))
            }
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp)
          .testTag("searchArticlesField"),
      )

      if (articles.isEmpty()) {
        EmptyArticlesState(
          uiState = uiState,
          onOpenAddFeed = onOpenAddFeed,
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        )
      } else {
        PullToRefreshBox(
          isRefreshing = uiState.isRefreshing,
          onRefresh = onRefreshAll,
          modifier = Modifier
            .fillMaxSize()
            .testTag("articlesPullToRefresh"),
        ) {
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .testTag("articlesList"),
            contentPadding = PaddingValues(bottom = 16.dp),
          ) {
            items(articles, key = { it.id }) { article ->
              val feedTitle =
                if (uiState.showsFeedTitleInRows) {
                  feedsByUrl[article.feedUrl]?.title
                } else {
                  null
                }
              SwipeableArticleRow(
                article = article,
                onToggleRead = { onToggleArticleRead(article.id) },
                onToggleStarred = { onToggleArticleStarred(article.id) },
              ) {
                ArticleRowItem(
                  article = article,
                  feedTitle = feedTitle,
                  dateText = ReaderLibrary.formatDate(
                    display = uiState.preferences.dateDisplay,
                    publishedAtMillis = article.publishedAt,
                    nowMillis = nowMillis,
                  ),
                  selected = uiState.selectedArticleId == article.id,
                  onClick = { onSelectArticle(article.id) },
                  onToggleRead = { onToggleArticleRead(article.id) },
                  onToggleStarred = { onToggleArticleStarred(article.id) },
                )
              }
              HorizontalDivider()
            }
          }
        }
      }
    }
  }
}

/**
 * Lets the user swipe an article row right to toggle read and left to toggle the star.
 * The row springs back afterward; the buttons inside the row do the same without a gesture.
 */
@Composable
private fun SwipeableArticleRow(
  article: Article,
  onToggleRead: () -> Unit,
  onToggleStarred: () -> Unit,
  content: @Composable () -> Unit,
) {
  val currentToggleRead by rememberUpdatedState(onToggleRead)
  val currentToggleStarred by rememberUpdatedState(onToggleStarred)
  val swipeState = rememberSwipeToDismissBoxState()
  val scope = rememberCoroutineScope()

  SwipeToDismissBox(
    state = swipeState,
    onDismiss = { value ->
      when (value) {
        SwipeToDismissBoxValue.StartToEnd -> currentToggleRead()
        SwipeToDismissBoxValue.EndToStart -> currentToggleStarred()
        SwipeToDismissBoxValue.Settled -> Unit
      }
      // Spring back: the article remains in the list.
      scope.launch { swipeState.reset() }
    },
    backgroundContent = {
      val direction = swipeState.dismissDirection
      if (direction != SwipeToDismissBoxValue.Settled) {
        val towardEnd = direction == SwipeToDismissBoxValue.StartToEnd
        val label =
          if (towardEnd) {
            if (article.isRead) R.string.action_mark_unread else R.string.action_mark_read
          } else {
            if (article.isStarred) R.string.action_unstar else R.string.action_star
          }
        Row(
          modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 20.dp),
          horizontalArrangement = if (towardEnd) Arrangement.Start else Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }
    },
  ) {
    content()
  }
}

@Composable
private fun ArticleRowItem(
  article: Article,
  feedTitle: String?,
  dateText: String,
  selected: Boolean,
  onClick: () -> Unit,
  onToggleRead: () -> Unit,
  onToggleStarred: () -> Unit,
) {
  val preview = remember(article.summary) {
    article.summary
      .take(500)
      .trim()
      .replace(Regex("\\s+"), " ")
  }
  val containerColor =
    if (selected) {
      MaterialTheme.colorScheme.secondaryContainer
    } else {
      // Opaque, so a swipe label behind the row never shows through its text.
      MaterialTheme.colorScheme.background
    }

  Surface(
    color = containerColor,
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("articleRow_${article.guid}"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = article.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = if (article.isRead) FontWeight.Normal else FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
        )
        if (article.isStarred) {
          Spacer(modifier = Modifier.width(6.dp))
          Icon(
            Icons.Default.Star,
            contentDescription = stringResource(R.string.article_starred_description),
            tint = RssOrangeLight,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      val metaText =
        if (!feedTitle.isNullOrEmpty()) {
          stringResource(R.string.article_meta_format, feedTitle, dateText)
        } else {
          dateText
        }
      Text(
        text = metaText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      if (preview.isNotEmpty()) {
        Text(
          text = preview,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 2.dp),
        )
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp),
      ) {
        TextButton(
          onClick = onToggleRead,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
          modifier = Modifier.testTag("toggleRead_${article.guid}"),
        ) {
          Text(
            text = if (article.isRead) stringResource(R.string.action_mark_unread) else stringResource(R.string.action_mark_read),
            style = MaterialTheme.typography.labelMedium,
          )
        }
        TextButton(
          onClick = onToggleStarred,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
          modifier = Modifier.testTag("toggleStar_${article.guid}"),
        ) {
          Text(
            text = if (article.isStarred) stringResource(R.string.action_unstar) else stringResource(R.string.action_star),
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyArticlesState(
  uiState: ReaderUiState,
  onOpenAddFeed: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      when {
        uiState.feeds.isEmpty() -> {
          Text(
            text = stringResource(R.string.empty_no_feeds_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = stringResource(R.string.empty_no_feeds_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Button(onClick = onOpenAddFeed) {
            Text(stringResource(R.string.action_add_feed))
          }
        }
        uiState.searchQuery.trim().isNotEmpty() -> {
          Text(
            text = stringResource(R.string.empty_no_results_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = stringResource(R.string.empty_no_search_results, uiState.searchQuery.trim()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        uiState.selectedFilter == FeedFilter.Unread -> {
          Text(
            text = stringResource(R.string.empty_all_caught_up_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = stringResource(R.string.empty_all_caught_up_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        uiState.selectedFilter == FeedFilter.Starred -> {
          Text(
            text = stringResource(R.string.empty_no_starred_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = stringResource(R.string.empty_no_starred_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        else -> {
          Text(
            text = stringResource(R.string.empty_no_articles_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = stringResource(R.string.empty_no_articles_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleDetailPane(
  article: Article,
  feedTitle: String?,
  dateDisplay: DateDisplay,
  nowMillis: Long,
  imagesAllowed: Boolean,
  imageFetcher: ReaderImageFetcher,
  showBackButton: Boolean,
  onBack: () -> Unit,
  onToggleStarred: () -> Unit,
  onAllowImages: () -> Unit,
  onHideImages: () -> Unit,
  onOpenExternalUrl: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showingImageNotice by rememberSaveable(article.id) { mutableStateOf(false) }
  val originalUrl = remember(article.link) { article.link?.let { ReaderUrl.allowed(it) } }
  val dateText = remember(dateDisplay, article.publishedAt, nowMillis) {
    ReaderLibrary.formatDate(dateDisplay, article.publishedAt, nowMillis)
  }
  val metadata =
    if (!feedTitle.isNullOrEmpty()) {
      stringResource(R.string.article_meta_format, feedTitle, dateText)
    } else {
      dateText
    }
  val readerInput = remember(article, metadata, originalUrl, imagesAllowed) {
    ReaderInput(
      title = article.title,
      metadata = metadata,
      body = article.contentHtml ?: "<p>${ReaderSanitizer.escaped(article.summary)}</p>",
      baseUrl = originalUrl ?: ReaderUrl.allowed(article.feedUrl),
      allowsImages = imagesAllowed,
    )
  }

  if (showingImageNotice) {
    AlertDialog(
      onDismissRequest = { showingImageNotice = false },
      title = { Text(stringResource(R.string.load_images_dialog_title)) },
      text = {
        Text(
          stringResource(R.string.load_images_dialog_message)
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showingImageNotice = false
            onAllowImages()
          },
          modifier = Modifier.testTag("confirmLoadImagesButton"),
        ) {
          Text(stringResource(R.string.action_load_images))
        }
      },
      dismissButton = {
        TextButton(onClick = { showingImageNotice = false }) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    )
  }

  Scaffold(
    modifier = modifier,
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = feedTitle ?: "",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          if (showBackButton) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.testTag("detailBackButton"),
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
          }
        },
        actions = {
          TextButton(
            onClick = onToggleStarred,
            modifier = Modifier.testTag("detailStarButton"),
          ) {
            Text(if (article.isStarred) stringResource(R.string.action_unstar) else stringResource(R.string.action_star))
          }
          if (originalUrl != null) {
            TextButton(
              onClick = { onOpenExternalUrl(originalUrl) },
              modifier = Modifier.testTag("openFullArticleButton"),
            ) {
              Text(stringResource(R.string.action_open_full_article))
            }
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .consumeWindowInsets(innerPadding)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (imagesAllowed) stringResource(R.string.images_enabled_status) else stringResource(R.string.images_disabled_status),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        TextButton(
          onClick = {
            if (imagesAllowed) {
              onHideImages()
            } else {
              showingImageNotice = true
            }
          },
          modifier = Modifier.testTag("toggleImagesButton"),
        ) {
          Text(if (imagesAllowed) stringResource(R.string.action_hide_images) else stringResource(R.string.action_load_images))
        }
      }
      HorizontalDivider()
      PrivateArticleReaderView(
        input = readerInput,
        imageFetcher = imageFetcher,
        onOpenExternalUrl = onOpenExternalUrl,
        modifier = Modifier
          .fillMaxSize()
          .testTag("privateArticleWebView"),
      )
    }
  }
}

@Composable
private fun EmptySelectionPane(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        Icons.AutoMirrored.Filled.List,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(40.dp),
      )
      Text(
        text = stringResource(R.string.select_an_article),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun PlainRssScreenCompactPreview() {
  val sampleFeed = Feed(
    url = "https://example.com/feed.xml",
    title = "Example Tech News",
    lastFetchedAt = 1_760_000_000_000L,
  )
  val sampleArticle = Article(
    guid = "post-1",
    feedUrl = sampleFeed.url,
    title = "Release 1.0 of Plain RSS for Android",
    link = "https://example.com/post-1",
    summary = "Follow RSS and Atom feeds and read articles in a focused interface.",
    publishedAt = 1_760_000_000_000L,
    isRead = false,
    isStarred = true,
  )
  PlainRssTheme {
    PlainRssScreen(
      uiState = ReaderUiState(
        isLoaded = true,
        feeds = listOf(sampleFeed),
        articles = listOf(sampleArticle),
        folders = listOf(FeedFolder(id = "f1", name = "Technology")),
      ),
      imageFetcher = object : ReaderImageFetcher {
        override suspend fun fetchPng(url: String): ByteArray = ByteArray(0)
      },
      onSelectFilter = {},
      onSelectArticle = {},
      onSearchQueryChange = {},
      onToggleArticleRead = {},
      onToggleArticleStarred = {},
      onMarkVisibleAsRead = {},
      onAllowImages = {},
      onHideImages = {},
      onAddFeed = { null },
      onRefreshAll = {},
      onRenameFeed = { _, _ -> },
      onMoveFeedToFolder = { _, _ -> },
      onRemoveFeed = {},
      onSaveFolder = { _, _ -> null },
      onRemoveFolder = {},
      onRetentionChange = {},
      onApplyRetentionToAll = {},
      onRefreshOnOpenChange = {},
      onArticleOrderChange = {},
      onFeedOrderChange = {},
      onDateDisplayChange = {},
      onRequestImportOpml = {},
      onRequestExportOpml = {},
      onOpenExternalUrl = {},
      onClearErrorMessage = {},
      onClearImportSummary = {},
      onRetryStorage = {},
      onContinueWithoutSaving = {},
    )
  }
}
