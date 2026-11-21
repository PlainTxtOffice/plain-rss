package com.plaintxtoffice.plainrss

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.plaintxtoffice.plainrss.model.Article
import com.plaintxtoffice.plainrss.model.Feed
import com.plaintxtoffice.plainrss.network.ReaderImageFetcher
import com.plaintxtoffice.plainrss.ui.FeedbackDialog
import com.plaintxtoffice.plainrss.ui.FeedbackKind
import com.plaintxtoffice.plainrss.ui.PlainRssScreen
import com.plaintxtoffice.plainrss.ui.theme.PlainRssTheme
import com.plaintxtoffice.plainrss.viewmodel.ReaderUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlainRssScreenTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  private val fakeImageFetcher = object : ReaderImageFetcher {
    override suspend fun fetchPng(url: String): ByteArray = ByteArray(0)
  }

  @Test
  fun emptyLibrary_showsAddFirstFeedAndImportActions() {
    composeTestRule.setContent {
      PlainRssTheme {
        PlainRssScreen(
          uiState = ReaderUiState(isLoaded = true),
          imageFetcher = fakeImageFetcher,
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

    composeTestRule.onNodeWithText("All Articles").assertIsDisplayed()
    composeTestRule.onNodeWithTag("addFirstFeedButton").assertIsDisplayed()
    composeTestRule.onNodeWithTag("importFromAnotherAppButton").assertIsDisplayed()
  }

  @Test
  fun selectingFeed_displaysArticlesAndOpensDetail() {
    val sampleFeed = Feed(
      url = "https://example.com/feed.xml",
      title = "Example Feed",
    )
    val sampleArticle = Article(
      guid = "item-1",
      feedUrl = sampleFeed.url,
      title = "Sample Article Headline",
      summary = "Short summary of the article.",
      publishedAt = 1_760_000_000_000L,
    )
    var currentState by mutableStateOf(
      ReaderUiState(
        isLoaded = true,
        feeds = listOf(sampleFeed),
        articles = listOf(sampleArticle),
      )
    )

    composeTestRule.setContent {
      PlainRssTheme {
        PlainRssScreen(
          uiState = currentState,
          imageFetcher = fakeImageFetcher,
          onSelectFilter = { filter ->
            currentState = currentState.copy(selectedFilter = filter, selectedArticleId = null)
          },
          onSelectArticle = { articleId ->
            currentState = currentState.copy(selectedArticleId = articleId)
          },
          onSearchQueryChange = { query ->
            currentState = currentState.copy(searchQuery = query)
          },
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

    composeTestRule.onNodeWithText("Example Feed").performClick()
    composeTestRule.onNodeWithText("Sample Article Headline").assertIsDisplayed()
    composeTestRule.onNodeWithText("Sample Article Headline").performClick()
    composeTestRule.onNodeWithText("Remote images are off").assertIsDisplayed()
  }

  @Test
  fun feedbackDialog_draftsEmailByKindAndReportsMissingApps() {
    val emailKinds = mutableListOf<FeedbackKind>()
    var emailAvailable = true
    composeTestRule.setContent {
      PlainRssTheme {
        FeedbackDialog(
          onSendEmail = { kind ->
            emailKinds += kind
            emailAvailable
          },
          onOpenContactForm = { false },
          onDismiss = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("feedbackReportBugButton").performClick()
    composeTestRule.onNodeWithTag("feedbackRequestFeatureButton").performClick()
    assertEquals(listOf(FeedbackKind.BUG_REPORT, FeedbackKind.FEATURE_REQUEST), emailKinds)
    composeTestRule.onNodeWithTag("feedbackError").assertDoesNotExist()

    emailAvailable = false
    composeTestRule.onNodeWithTag("feedbackReportBugButton").performClick()
    composeTestRule
      .onNodeWithText("No email app is available on this device. Use Open contact form instead.")
      .assertIsDisplayed()

    composeTestRule.onNodeWithTag("feedbackContactFormButton").performClick()
    composeTestRule.onNodeWithTag("feedbackError").assertIsDisplayed()
    composeTestRule.onNodeWithTag("feedbackDone").assertIsDisplayed()
  }
}
