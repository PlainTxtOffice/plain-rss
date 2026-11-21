package com.plaintxtoffice.plainrss.model

/**
 * Represents a subscribed RSS or Atom feed stored in the local library.
 */
data class Feed(
  val url: String,
  val title: String,
  val lastFetchedAt: Long? = null,
  val etag: String? = null,
  val lastModified: String? = null,
  val folderId: String? = null,
  val retentionEnabled: Boolean = true,
  /** True once the user renames the feed, so a refresh keeps their title. */
  val hasCustomTitle: Boolean = false,
)

/**
 * Represents a downloaded article belonging to a [Feed].
 */
data class Article(
  val guid: String,
  val feedUrl: String,
  val title: String,
  val link: String? = null,
  val summary: String = "",
  val contentHtml: String? = null,
  val publishedAt: Long? = null,
  val isRead: Boolean = false,
  val isStarred: Boolean = false,
  val fetchedAt: Long = System.currentTimeMillis(),
) {
  /** Composite key uniquely identifying an article across all feeds. */
  val id: String
    get() = "$feedUrl\u0000$guid"
}

/**
 * Represents a user-created folder for organizing feeds.
 */
data class FeedFolder(
  val id: String,
  val name: String,
)

/**
 * Filter applied to the article list.
 */
sealed interface FeedFilter {
  data object All : FeedFilter
  data object Unread : FeedFilter
  data object Starred : FeedFilter
  data object Unfiled : FeedFilter
  data class Folder(val id: String) : FeedFilter
  data class FeedItem(val url: String) : FeedFilter
}

/**
 * Sort order for the article list.
 *
 * Preferences persist the enum [name]. [legacyLabel] is the English label that older versions
 * persisted instead; it is kept only so those stored values still load. Localized display labels
 * live in the UI layer.
 */
enum class ArticleOrder(private val legacyLabel: String) {
  NEWEST("Newest first"),
  OLDEST("Oldest first"),
  TITLE("Title"),
  UNREAD("Unread first");

  companion object {
    /** Returns the entry for a persisted [value] (enum name or legacy label), or the default. */
    fun fromStoredValue(value: String?): ArticleOrder =
      entries.firstOrNull { it.name == value || it.legacyLabel == value } ?: NEWEST
  }
}

/**
 * Sort order for the subscription feed list.
 *
 * Preferences persist the enum [name]. [legacyLabel] is the English label that older versions
 * persisted instead; it is kept only so those stored values still load. Localized display labels
 * live in the UI layer.
 */
enum class FeedOrder(private val legacyLabel: String) {
  TITLE("Title"),
  UNREAD("Unread count"),
  UPDATED("Recently refreshed");

  companion object {
    /** Returns the entry for a persisted [value] (enum name or legacy label), or the default. */
    fun fromStoredValue(value: String?): FeedOrder =
      entries.firstOrNull { it.name == value || it.legacyLabel == value } ?: TITLE
  }
}

/**
 * Display format for article publication dates.
 *
 * Preferences persist the enum [name]. [legacyLabel] is the English label that older versions
 * persisted instead; it is kept only so those stored values still load. Localized display labels
 * live in the UI layer.
 */
enum class DateDisplay(private val legacyLabel: String) {
  AUTOMATIC("Relative, then date"),
  RELATIVE("Relative"),
  DATE("Date"),
  DATE_TIME("Date and time");

  companion object {
    /** Returns the entry for a persisted [value] (enum name or legacy label), or the default. */
    fun fromStoredValue(value: String?): DateDisplay =
      entries.firstOrNull { it.name == value || it.legacyLabel == value } ?: AUTOMATIC
  }
}

/**
 * User-configurable reader preferences.
 */
data class ReaderPreferences(
  val articleRetention: Int = DEFAULT_RETENTION,
  val refreshOnOpen: Boolean = true,
  val articleOrder: ArticleOrder = ArticleOrder.NEWEST,
  val feedOrder: FeedOrder = FeedOrder.TITLE,
  val dateDisplay: DateDisplay = DateDisplay.AUTOMATIC,
  val lastAutoRefreshMillis: Long = 0L,
) {
  companion object {
    const val DEFAULT_RETENTION = 200
    val RETENTION_OPTIONS = listOf(50, 100, 200, 500, 1000)
  }
}

/**
 * Parsed feed metadata and articles produced by [com.plaintxtoffice.plainrss.domain.RssFeedParser].
 */
data class ParsedFeed(
  val title: String,
  val articles: List<ParsedArticle>,
)

/**
 * Single parsed article entry from an RSS or Atom document.
 */
data class ParsedArticle(
  val title: String = "Untitled Article",
  val link: String? = null,
  val summary: String = "",
  val contentHtml: String? = null,
  val publishedAt: Long? = null,
  val guid: String? = null,
)

/**
 * Downloaded and parsed feed along with HTTP conditional-request validators.
 */
data class FetchedFeed(
  val parsed: ParsedFeed,
  val etag: String? = null,
  val lastModified: String? = null,
)

/**
 * Result of fetching a known feed URL.
 */
sealed interface FeedFetchResult {
  data object NotModified : FeedFetchResult
  data class Fetched(val feed: FetchedFeed) : FeedFetchResult
}

/**
 * Subscription entry read from or written to an OPML document.
 */
data class OpmlFeed(
  val title: String,
  val url: String,
  val folder: String? = null,
)

/**
 * Domain error thrown when a feed document cannot be parsed.
 */
class RssParserException(
  override val message: String = "The feed could not be parsed.",
  cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Domain error thrown when downloading or discovering a feed fails.
 */
sealed class FeedException(override val message: String) : Exception(message) {
  class InvalidResponse : FeedException("Invalid response from server.")
  class ServerError(statusCode: Int) :
    FeedException("The feed server returned an error (HTTP $statusCode).")
  class TooLarge : FeedException("That feed is too large to download.")
  class NoFeedFound : FeedException("No RSS or Atom feed was found at that address.")
  class InsecureConnection :
    FeedException("Android blocks insecure http:// connections. Use the https:// version of the feed URL.")
}

/**
 * Domain error thrown when an OPML file is invalid.
 */
class OpmlException(
  override val message: String = "That file isn't a valid OPML subscription list.",
  cause: Throwable? = null,
) : Exception(message, cause)
