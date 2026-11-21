package com.plaintxtoffice.plainrss.storage

import com.plaintxtoffice.plainrss.model.Article
import com.plaintxtoffice.plainrss.model.Feed
import com.plaintxtoffice.plainrss.model.FeedFolder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Snapshot of the user's persistent feed library.
 */
data class LibrarySnapshot(
  val feeds: List<Feed> = emptyList(),
  val articles: List<Article> = emptyList(),
  val folders: List<FeedFolder> = emptyList(),
)

/**
 * Persistence boundary for reading and writing the on-device feed library.
 */
interface LibraryStore {
  suspend fun load(): LibrarySnapshot
  suspend fun save(snapshot: LibrarySnapshot)
}

/**
 * Stores the feed library as UTF-8 JSON inside the app's private storage directory.
 */
class FileLibraryStore(private val file: File) : LibraryStore {
  private val mutex = Mutex()

  override suspend fun load(): LibrarySnapshot =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        if (!file.exists()) {
          return@withLock LibrarySnapshot()
        }
        val text = file.readText(Charsets.UTF_8)
        if (text.isBlank()) {
          return@withLock LibrarySnapshot()
        }
        try {
          LibraryJsonCodec.decode(text)
        } catch (e: Exception) {
          throw IOException("The stored library file could not be read.", e)
        }
      }
    }

  override suspend fun save(snapshot: LibrarySnapshot) {
    withContext(Dispatchers.IO) {
      mutex.withLock {
        val parent = file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
          throw IOException("Unable to create storage directory.")
        }
        val encoded = LibraryJsonCodec.encode(snapshot)
        val tempFile = File(parent, "${file.name}.tmp")
        FileOutputStream(tempFile).use { out ->
          out.write(encoded.toByteArray(Charsets.UTF_8))
          out.fd.sync()
        }
        // Replace the library in one step so a crash leaves either the old or
        // the new file, never a missing one.
        try {
          Files.move(
            tempFile.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
          )
        } catch (_: AtomicMoveNotSupportedException) {
          Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
      }
    }
  }
}

/**
 * Non-persistent in-memory [LibraryStore] used for unit tests and temporary storage recovery mode.
 */
class InMemoryLibraryStore(
  initial: LibrarySnapshot = LibrarySnapshot(),
) : LibraryStore {
  private var current: LibrarySnapshot = initial

  override suspend fun load(): LibrarySnapshot = current

  override suspend fun save(snapshot: LibrarySnapshot) {
    current = snapshot
  }
}

/**
 * JSON serializer for [LibrarySnapshot].
 */
object LibraryJsonCodec {
  private const val SCHEMA_VERSION = 1

  fun encode(snapshot: LibrarySnapshot): String {
    val root = JSONObject()
    root.put("schema_version", SCHEMA_VERSION)

    val foldersArray = JSONArray()
    for ((id, name) in snapshot.folders) {
      foldersArray.put(
        JSONObject().apply {
          put("id", id)
          put("name", name)
        }
      )
    }
    root.put("folders", foldersArray)

    val feedsArray = JSONArray()
    for ((url, title, lastFetchedAt, etag, lastModified, folderId, retentionEnabled, hasCustomTitle) in snapshot.feeds) {
      feedsArray.put(
        JSONObject().apply {
          put("url", url)
          put("title", title)
          if (lastFetchedAt != null) put("lastFetchedAt", lastFetchedAt)
          if (etag != null) put("etag", etag)
          if (lastModified != null) put("lastModified", lastModified)
          if (folderId != null) put("folderId", folderId)
          put("retentionEnabled", retentionEnabled)
          if (hasCustomTitle) put("customTitle", true)
        }
      )
    }
    root.put("feeds", feedsArray)

    val articlesArray = JSONArray()
    for ((guid, feedUrl, title, link, summary, contentHtml, publishedAt, isRead, isStarred, fetchedAt) in snapshot.articles) {
      articlesArray.put(
        JSONObject().apply {
          put("guid", guid)
          put("feedUrl", feedUrl)
          put("title", title)
          if (link != null) put("link", link)
          put("summary", summary)
          if (contentHtml != null) put("contentHtml", contentHtml)
          if (publishedAt != null) put("publishedAt", publishedAt)
          put("isRead", isRead)
          put("isStarred", isStarred)
          put("fetchedAt", fetchedAt)
        }
      )
    }
    root.put("articles", articlesArray)

    // Compact output: the file is rewritten often and is not meant for hand editing.
    return root.toString()
  }

  fun decode(jsonText: String): LibrarySnapshot {
    val root = JSONObject(jsonText)

    val folders = mutableListOf<FeedFolder>()
    val foldersArray = root.optJSONArray("folders") ?: JSONArray()
    for (i in 0 until foldersArray.length()) {
      val item = foldersArray.getJSONObject(i)
      folders.add(
        FeedFolder(
          id = item.getString("id"),
          name = item.getString("name"),
        )
      )
    }

    val feeds = mutableListOf<Feed>()
    val feedsArray = root.optJSONArray("feeds") ?: JSONArray()
    for (i in 0 until feedsArray.length()) {
      val item = feedsArray.getJSONObject(i)
      feeds.add(
        Feed(
          url = item.getString("url"),
          title = item.getString("title"),
          lastFetchedAt = if (item.has("lastFetchedAt") && !item.isNull("lastFetchedAt")) item.getLong("lastFetchedAt") else null,
          etag = item.optStringOrNull("etag"),
          lastModified = item.optStringOrNull("lastModified"),
          folderId = item.optStringOrNull("folderId"),
          retentionEnabled = item.optBoolean("retentionEnabled", true),
          hasCustomTitle = item.optBoolean("customTitle", false),
        )
      )
    }

    val articles = mutableListOf<Article>()
    val articlesArray = root.optJSONArray("articles") ?: JSONArray()
    for (i in 0 until articlesArray.length()) {
      val item = articlesArray.getJSONObject(i)
      articles.add(
        Article(
          guid = item.getString("guid"),
          feedUrl = item.getString("feedUrl"),
          title = item.getString("title"),
          link = item.optStringOrNull("link"),
          summary = item.optString("summary", ""),
          contentHtml = item.optStringOrNull("contentHtml"),
          publishedAt = if (item.has("publishedAt") && !item.isNull("publishedAt")) item.getLong("publishedAt") else null,
          isRead = item.optBoolean("isRead", false),
          isStarred = item.optBoolean("isStarred", false),
          fetchedAt = item.optLong("fetchedAt", 0L),
        )
      )
    }

    return LibrarySnapshot(
      feeds = feeds,
      articles = articles,
      folders = folders,
    )
  }

  private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key).takeIf { it.isNotEmpty() } else null
}
