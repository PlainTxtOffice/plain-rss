package com.plaintxtoffice.plainrss

import com.plaintxtoffice.plainrss.domain.ReaderLibrary
import com.plaintxtoffice.plainrss.model.Article
import com.plaintxtoffice.plainrss.model.ArticleOrder
import com.plaintxtoffice.plainrss.model.DateDisplay
import com.plaintxtoffice.plainrss.model.FeedFolder
import com.plaintxtoffice.plainrss.model.ParsedArticle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderLibraryTest {
  @Test
  fun sortArticles_placesMissingDatesAfterDatedArticlesInBothChronologicalOrders() {
    val older = Article(guid = "1", feedUrl = "f", title = "Older", publishedAt = 1_000L)
    val newer = Article(guid = "2", feedUrl = "f", title = "Newer", publishedAt = 2_000L)
    val undated = Article(guid = "3", feedUrl = "f", title = "Undated", publishedAt = null)

    val newestFirst = ReaderLibrary.sortArticles(
      listOf(undated, older, newer),
      ArticleOrder.NEWEST,
      Locale.US,
    )
    assertEquals(listOf("2", "1", "3"), newestFirst.map { it.guid })

    val oldestFirst = ReaderLibrary.sortArticles(
      listOf(undated, newer, older),
      ArticleOrder.OLDEST,
      Locale.US,
    )
    assertEquals(listOf("1", "2", "3"), oldestFirst.map { it.guid })
  }

  @Test
  fun trimFeedArticles_keepsNewestUnstarredAndAllStarredArticles() {
    val articles = listOf(
      Article(guid = "1", feedUrl = "f", title = "Oldest Starred", publishedAt = 1_000L, isStarred = true),
      Article(guid = "2", feedUrl = "f", title = "Old Unstarred", publishedAt = 2_000L, isStarred = false),
      Article(guid = "3", feedUrl = "f", title = "Mid Unstarred", publishedAt = 3_000L, isStarred = false),
      Article(guid = "4", feedUrl = "f", title = "New Unstarred", publishedAt = 4_000L, isStarred = false),
    )

    val trimmed = ReaderLibrary.trimFeedArticles(articles, feedUrl = "f", limit = 2)
    assertEquals(setOf("1", "3", "4"), trimmed.map { it.guid }.toSet())
  }

  @Test
  fun mergeArticles_updatesExistingAndSkipsDuplicateGuidsInSameBatch() {
    val existing = listOf(
      Article(
        guid = "g1",
        feedUrl = "https://example.com/rss",
        title = "Initial Title",
        summary = "Initial summary",
        isRead = true,
        isStarred = true,
      )
    )
    val incoming = listOf(
      ParsedArticle(title = "Updated Title", summary = "Updated summary", guid = "g1"),
      ParsedArticle(title = "Duplicate In Feed", summary = "Ignored", guid = "g1"),
      ParsedArticle(title = "Brand New", summary = "Fresh", guid = "g2"),
    )

    val merged = ReaderLibrary.mergeArticles(
      parsed = incoming,
      existingFeedArticles = existing,
      feedUrl = "https://example.com/rss",
      nowMillis = 5_000L,
    )
    assertEquals(2, merged.size)
    val first = merged.first { it.guid == "g1" }
    assertEquals("Updated Title", first.title)
    assertEquals("Updated summary", first.summary)
    assertEquals(true, first.isRead)
    assertEquals(true, first.isStarred)
  }

  @Test
  fun validateFolderName_enforcesUniquenessLengthAndReservedUnfiled() {
    val existing = listOf(FeedFolder(id = "1", name = "News"))
    assertEquals("Tech", ReaderLibrary.validateFolderName("  Tech  ", existing))
    assertNull(ReaderLibrary.validateFolderName("", existing))
    assertNull(ReaderLibrary.validateFolderName("unfiled", existing))
    assertNull(ReaderLibrary.validateFolderName("NEWS", existing))
    assertEquals("News", ReaderLibrary.validateFolderName("News", existing, excludingId = "1"))
  }

  @Test
  fun formatDate_handlesMissingAndJustNow() {
    assertEquals(
      "Date unavailable",
      ReaderLibrary.formatDate(DateDisplay.AUTOMATIC, null, nowMillis = 100_000L),
    )
    assertEquals(
      "Just now",
      ReaderLibrary.formatDate(DateDisplay.AUTOMATIC, 90_000L, nowMillis = 100_000L),
    )
  }
}
