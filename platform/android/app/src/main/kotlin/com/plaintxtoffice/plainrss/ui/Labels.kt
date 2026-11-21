package com.plaintxtoffice.plainrss.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.plaintxtoffice.plainrss.R
import com.plaintxtoffice.plainrss.model.ArticleOrder
import com.plaintxtoffice.plainrss.model.DateDisplay
import com.plaintxtoffice.plainrss.model.Feed
import com.plaintxtoffice.plainrss.model.FeedFilter
import com.plaintxtoffice.plainrss.model.FeedFolder
import com.plaintxtoffice.plainrss.model.FeedOrder

/** Localized Settings label for an article sort order. */
@Composable
fun ArticleOrder.displayLabel(): String =
  stringResource(
    when (this) {
      ArticleOrder.NEWEST -> R.string.article_order_newest
      ArticleOrder.OLDEST -> R.string.article_order_oldest
      ArticleOrder.TITLE -> R.string.article_order_title
      ArticleOrder.UNREAD -> R.string.article_order_unread
    }
  )

/** Localized Settings label for a feed sort order. */
@Composable
fun FeedOrder.displayLabel(): String =
  stringResource(
    when (this) {
      FeedOrder.TITLE -> R.string.feed_order_title
      FeedOrder.UNREAD -> R.string.feed_order_unread
      FeedOrder.UPDATED -> R.string.feed_order_updated
    }
  )

/** Localized Settings label for an article date format. */
@Composable
fun DateDisplay.displayLabel(): String =
  stringResource(
    when (this) {
      DateDisplay.AUTOMATIC -> R.string.date_display_automatic
      DateDisplay.RELATIVE -> R.string.date_display_relative
      DateDisplay.DATE -> R.string.date_display_date
      DateDisplay.DATE_TIME -> R.string.date_display_date_time
    }
  )

/**
 * Localized title of the articles pane for [selectedFilter]: the filter's name, the selected
 * folder's name, or the selected feed's title.
 */
@Composable
internal fun articlesPaneTitle(
  selectedFilter: FeedFilter?,
  folders: List<FeedFolder>,
  feeds: List<Feed>,
): String =
  when (selectedFilter) {
    null, FeedFilter.All -> stringResource(R.string.filter_all_articles)
    FeedFilter.Unread -> stringResource(R.string.filter_unread)
    FeedFilter.Starred -> stringResource(R.string.filter_starred)
    FeedFilter.Unfiled -> stringResource(R.string.filter_unfiled)
    is FeedFilter.Folder ->
      folders.firstOrNull { it.id == selectedFilter.id }?.name
        ?: stringResource(R.string.nav_title_folder_fallback)
    is FeedFilter.FeedItem ->
      feeds.firstOrNull { it.url == selectedFilter.url }?.title
        ?: stringResource(R.string.nav_title_articles_fallback)
  }
