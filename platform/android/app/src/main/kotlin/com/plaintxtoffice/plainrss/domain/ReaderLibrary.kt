package com.plaintxtoffice.plainrss.domain

import com.plaintxtoffice.plainrss.model.Article
import com.plaintxtoffice.plainrss.model.ArticleOrder
import com.plaintxtoffice.plainrss.model.DateDisplay
import com.plaintxtoffice.plainrss.model.Feed
import com.plaintxtoffice.plainrss.model.FeedFolder
import com.plaintxtoffice.plainrss.model.FeedOrder
import com.plaintxtoffice.plainrss.model.ParsedArticle
import java.text.Collator
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure domain logic for sorting feeds and articles, merging downloaded entries,
 * enforcing retention limits, managing folders, and formatting publication dates.
 */
object ReaderLibrary {
  fun sortArticles(
    articles: List<Article>,
    order: ArticleOrder = ArticleOrder.NEWEST,
    locale: Locale = Locale.getDefault(),
  ): List<Article> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
    return articles.sortedWith { a, b ->
      when (order) {
        ArticleOrder.TITLE -> {
          val cmp = collator.compare(a.title, b.title)
          if (cmp != 0) return@sortedWith cmp
        }
        ArticleOrder.UNREAD -> {
          if (a.isRead != b.isRead) {
            return@sortedWith if (!a.isRead) -1 else 1
          }
        }
        ArticleOrder.NEWEST, ArticleOrder.OLDEST -> Unit
      }

      if (a.publishedAt != b.publishedAt) {
        val left = a.publishedAt ?: return@sortedWith 1
        val right = b.publishedAt ?: return@sortedWith -1
        return@sortedWith if (order == ArticleOrder.OLDEST) {
          left.compareTo(right)
        } else {
          right.compareTo(left)
        }
      }

      val leftKey = a.id
      val rightKey = b.id
      if (leftKey != rightKey) {
        return@sortedWith leftKey.compareTo(rightKey)
      }
      b.fetchedAt.compareTo(a.fetchedAt)
    }
  }

  fun sortFeeds(
    feeds: List<Feed>,
    order: FeedOrder,
    locale: Locale = Locale.getDefault(),
    unreadCount: (Feed) -> Int,
  ): List<Feed> {
    val counts =
      if (order == FeedOrder.UNREAD) {
        feeds.associate { it.url to unreadCount(it) }
      } else {
        emptyMap()
      }
    val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
    return feeds.sortedWith { a, b ->
      if (order == FeedOrder.UNREAD) {
        val left = counts[a.url] ?: 0
        val right = counts[b.url] ?: 0
        if (left != right) return@sortedWith right.compareTo(left)
      }
      if (order == FeedOrder.UPDATED && a.lastFetchedAt != b.lastFetchedAt) {
        val left = a.lastFetchedAt ?: Long.MIN_VALUE
        val right = b.lastFetchedAt ?: Long.MIN_VALUE
        return@sortedWith right.compareTo(left)
      }
      val cmp = collator.compare(a.title, b.title)
      if (cmp != 0) cmp else a.url.compareTo(b.url)
    }
  }

  fun excessArticles(feedArticles: List<Article>, limit: Int): Set<String> {
    val unstarred = feedArticles.filter { !it.isStarred }
    val sorted = sortArticles(unstarred, ArticleOrder.NEWEST)
    return sorted.drop(maxOf(1, limit)).map { it.id }.toSet()
  }

  fun trimFeedArticles(
    allArticles: List<Article>,
    feedUrl: String,
    limit: Int,
  ): List<Article> {
    val feedArticles = allArticles.filter { it.feedUrl == feedUrl }
    val excessIds = excessArticles(feedArticles, limit)
    if (excessIds.isEmpty()) return allArticles
    return allArticles.filterNot { it.id in excessIds }
  }

  fun mergeArticles(
    parsed: List<ParsedArticle>,
    existingFeedArticles: List<Article>,
    feedUrl: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): List<Article> {
    val byGuid = existingFeedArticles.associateBy { it.guid }.toMutableMap()
    val orderedGuids = existingFeedArticles.map { it.guid }.toMutableList()
    val seenInBatch = mutableSetOf<String>()

    for ((title, link, summary, contentHtml, publishedAt, guid1) in parsed) {
      val guid =
        guid1?.takeIf { it.isNotEmpty() }
          ?: link?.takeIf { it.isNotEmpty() }
          ?: "$feedUrl-$title-${(publishedAt ?: 0L) / 1000L}"
      if (!seenInBatch.add(guid)) continue
      val existing = byGuid[guid]
      if (existing != null) {
        val updated = existing.copy(
          title = title,
          summary = summary,
          contentHtml = contentHtml ?: existing.contentHtml,
          link = link ?: existing.link,
        )
        byGuid[guid] = updated
      } else {
        orderedGuids.add(guid)
        byGuid[guid] = Article(
          guid = guid,
          feedUrl = feedUrl,
          title = title,
          link = link,
          summary = summary,
          contentHtml = contentHtml,
          publishedAt = publishedAt,
          fetchedAt = nowMillis,
        )
      }
    }
    return orderedGuids.mapNotNull { byGuid[it] }
  }

  fun validateFolderName(
    input: String,
    folders: List<FeedFolder>,
    excludingId: String? = null,
  ): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.length > 80) return null
    if (trimmed.equals("Unfiled", ignoreCase = true)) return null
    val duplicate = folders.any { folder ->
      folder.id != excludingId && folder.name.equals(trimmed, ignoreCase = true)
    }
    return if (duplicate) null else trimmed
  }

  fun removeFolder(
    folderId: String,
    folders: List<FeedFolder>,
    feeds: List<Feed>,
  ): Pair<List<FeedFolder>, List<Feed>> {
    val updatedFolders = folders.filterNot { it.id == folderId }
    val updatedFeeds = feeds.map { feed ->
      if (feed.folderId == folderId) feed.copy(folderId = null) else feed
    }
    return updatedFolders to updatedFeeds
  }

  fun formatDate(
    display: DateDisplay,
    publishedAtMillis: Long?,
    nowMillis: Long = System.currentTimeMillis(),
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault(),
  ): String {
    if (publishedAtMillis == null) return "Date unavailable"
    val ageMillis = nowMillis - publishedAtMillis
    val sevenDaysMillis = 7L * 24L * 60L * 60L * 1000L
    if (
      display == DateDisplay.RELATIVE ||
        (display == DateDisplay.AUTOMATIC && ageMillis in 0 until sevenDaysMillis)
    ) {
      if (ageMillis in 0 until 60_000L) return "Just now"
      return formatRelative(ageMillis)
    }
    val formatter =
      if (display == DateDisplay.DATE_TIME) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
      } else {
        DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
      }
    formatter.timeZone = timeZone
    return formatter.format(Date(publishedAtMillis))
  }

  private fun formatRelative(ageMillis: Long): String {
    val future = ageMillis < 0
    val seconds = kotlin.math.abs(ageMillis) / 1000L
    val minutes = seconds / 60L
    val hours = minutes / 60L
    val days = hours / 24L
    val weeks = days / 7L
    val unitText =
      when {
        seconds < 60L -> "${seconds}s"
        minutes < 60L -> "${minutes}m"
        hours < 24L -> "${hours}h"
        days < 7L -> "${days}d"
        else -> "${weeks}w"
      }
    return if (future) "in $unitText" else "$unitText ago"
  }

  fun importSummaryText(
    addedCount: Int,
    duplicateCount: Int,
    invalidCount: Int,
    failures: List<String>,
  ): String {
    val lines = mutableListOf(
      if (addedCount == 1) "Added 1 feed." else "Added $addedCount feeds."
    )
    if (duplicateCount > 0) {
      lines.add(
        if (duplicateCount == 1) {
          "1 feed was already in your list."
        } else {
          "$duplicateCount feeds were already in your list."
        }
      )
    }
    if (invalidCount > 0) {
      lines.add(
        if (invalidCount == 1) {
          "1 entry had an invalid URL and was skipped."
        } else {
          "$invalidCount entries had invalid URLs and were skipped."
        }
      )
    }
    if (failures.isNotEmpty()) {
      val shown = failures.take(5).joinToString("\n")
      val more = if (failures.size > 5) "\n…and ${failures.size - 5} more" else ""
      lines.add("\nThese feeds were added but couldn't load right now:\n$shown$more")
    }
    return lines.joinToString(" ")
  }
}
