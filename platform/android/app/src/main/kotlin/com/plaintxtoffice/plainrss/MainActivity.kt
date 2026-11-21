package com.plaintxtoffice.plainrss

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.plaintxtoffice.plainrss.data.SharedPreferencesRepository
import com.plaintxtoffice.plainrss.domain.ReaderUrl
import com.plaintxtoffice.plainrss.network.HttpFeedFetcher
import com.plaintxtoffice.plainrss.network.HttpReaderImageFetcher
import com.plaintxtoffice.plainrss.storage.FileLibraryStore
import com.plaintxtoffice.plainrss.ui.PlainRssScreen
import com.plaintxtoffice.plainrss.ui.theme.PlainRssTheme
import com.plaintxtoffice.plainrss.viewmodel.ReaderViewModel
import com.plaintxtoffice.plainrss.viewmodel.ReaderViewModelFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

/**
 * Main entrypoint activity for the Plain RSS Android application.
 */
class MainActivity : ComponentActivity() {
  private val viewModel: ReaderViewModel by viewModels {
    ReaderViewModelFactory(
      libraryStore = FileLibraryStore(File(filesDir, "library.json")),
      preferencesRepository = SharedPreferencesRepository(applicationContext),
      feedFetcher = HttpFeedFetcher(),
    )
  }

  private val imageFetcher = HttpReaderImageFetcher()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      val lifecycleOwner = LocalLifecycleOwner.current

      DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
          when (event) {
            Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
            Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
            else -> Unit
          }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
      }

      val importOpmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
      ) { uri: Uri? ->
        if (uri != null) {
          readOpmlFromUri(uri)
        }
      }

      val exportOpmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-opml")
      ) { uri: Uri? ->
        if (uri != null) {
          writeOpmlToUri(uri)
        }
      }

      PlainRssTheme {
        PlainRssScreen(
          uiState = uiState,
          imageFetcher = imageFetcher,
          onSelectFilter = viewModel::selectFilter,
          onSelectArticle = viewModel::selectArticle,
          onSearchQueryChange = viewModel::updateSearchQuery,
          onToggleArticleRead = viewModel::toggleArticleRead,
          onToggleArticleStarred = viewModel::toggleArticleStarred,
          onMarkVisibleAsRead = viewModel::markVisibleAsRead,
          onAllowImages = viewModel::allowImagesForCurrentArticle,
          onHideImages = viewModel::hideImagesForCurrentArticle,
          onAddFeed = viewModel::addFeed,
          onRefreshAll = viewModel::refreshAllFeeds,
          onRenameFeed = viewModel::renameFeed,
          onMoveFeedToFolder = viewModel::moveFeedToFolder,
          onRemoveFeed = viewModel::removeFeed,
          onSaveFolder = viewModel::saveFolder,
          onRemoveFolder = viewModel::removeFolder,
          onRetentionChange = viewModel::setArticleRetention,
          onApplyRetentionToAll = viewModel::applyRetentionToAllFeeds,
          onRefreshOnOpenChange = viewModel::setRefreshOnOpen,
          onArticleOrderChange = viewModel::setArticleOrder,
          onFeedOrderChange = viewModel::setFeedOrder,
          onDateDisplayChange = viewModel::setDateDisplay,
          onRequestImportOpml = {
            importOpmlLauncher.launch(arrayOf("*/*"))
          },
          onRequestExportOpml = {
            exportOpmlLauncher.launch("plain RSS Subscriptions.opml")
          },
          onOpenExternalUrl = ::openExternalUrl,
          onClearErrorMessage = viewModel::clearErrorMessage,
          onClearImportSummary = viewModel::clearImportSummary,
          onRetryStorage = viewModel::retryStorage,
          onContinueWithoutSaving = viewModel::continueWithoutSaving,
        )
      }
    }
  }

  private fun readOpmlFromUri(uri: Uri) {
    lifecycleScope.launch {
      try {
        val bytes = withContext(Dispatchers.IO) {
          contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Unable to read selected file.")
        }
        viewModel.importOpml(bytes)
      } catch (e: Exception) {
        viewModel.showErrorMessage(e.message ?: "Unable to read OPML file.")
      }
    }
  }

  private fun writeOpmlToUri(uri: Uri) {
    val opmlText = viewModel.exportOpml()
    lifecycleScope.launch {
      try {
        withContext(Dispatchers.IO) {
          contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(opmlText.toByteArray(Charsets.UTF_8))
          } ?: throw IllegalStateException("Unable to write to selected file.")
        }
      } catch (e: Exception) {
        viewModel.showErrorMessage(e.message ?: "Unable to export OPML file.")
      }
    }
  }

  private fun openExternalUrl(rawUrl: String) {
    val allowed = ReaderUrl.allowed(rawUrl) ?: return
    runCatching {
      val intent = Intent(Intent.ACTION_VIEW, allowed.toUri()).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
      }
      startActivity(intent)
    }
  }
}
