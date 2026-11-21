package com.plaintxtoffice.plainrss

import com.plaintxtoffice.plainrss.model.Article
import com.plaintxtoffice.plainrss.model.Feed
import com.plaintxtoffice.plainrss.model.FeedFolder
import com.plaintxtoffice.plainrss.storage.FileLibraryStore
import com.plaintxtoffice.plainrss.storage.LibrarySnapshot
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FileLibraryStoreTest {
  @Test
  fun save_replacesTheExistingLibraryAndLeavesNoTempFile() = runTest {
    val dir = Files.createTempDirectory("plainrss-store").toFile()
    try {
      val file = File(dir, "library.json")
      val store = FileLibraryStore(file)
      val feed = Feed(url = "https://example.com/feed.xml", title = "Example")
      val first = LibrarySnapshot(feeds = listOf(feed))
      val second = LibrarySnapshot(
        feeds = listOf(feed.copy(folderId = "f1", hasCustomTitle = true)),
        articles = listOf(
          Article(guid = "g1", feedUrl = feed.url, title = "Post", isStarred = true, fetchedAt = 5L)
        ),
        folders = listOf(FeedFolder(id = "f1", name = "Tech")),
      )

      store.save(first)
      assertEquals(first, store.load())
      store.save(second)

      assertEquals(second, FileLibraryStore(file).load())
      assertFalse(File(dir, "library.json.tmp").exists())
    } finally {
      dir.deleteRecursively()
    }
  }
}
