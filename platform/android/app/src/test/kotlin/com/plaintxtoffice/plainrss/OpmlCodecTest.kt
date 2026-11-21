package com.plaintxtoffice.plainrss

import com.plaintxtoffice.plainrss.domain.OpmlParser
import com.plaintxtoffice.plainrss.domain.OpmlWriter
import com.plaintxtoffice.plainrss.model.OpmlException
import com.plaintxtoffice.plainrss.model.OpmlFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpmlCodecTest {
  @Test
  fun writeAndParse_roundTripsUnfiledAndFolderFeeds() {
    val feeds = listOf(
      OpmlFeed(title = "Root Feed", url = "https://example.com/rss.xml", folder = null),
      OpmlFeed(title = "Kotlin Blog", url = "https://kotlinlang.org/feed.xml", folder = "Dev"),
      OpmlFeed(title = "Android Dev", url = "https://developer.android.com/feed.xml", folder = "Dev"),
    )

    val xml = OpmlWriter.write(feeds, dateCreatedMillis = 1_760_000_000_000L)
    val parsed = OpmlParser.parse(xml.toByteArray(Charsets.UTF_8))

    assertEquals(feeds, parsed)
  }

  @Test
  fun parseNestedFolders_recordsInnermostEnclosingFolder() {
    val opml = """
      <?xml version="1.0" encoding="UTF-8"?>
      <opml version="2.0">
        <body>
          <outline text="Outer">
            <outline text="Inner">
              <outline text="Nested Feed" xmlUrl="https://example.com/nested.xml"/>
            </outline>
          </outline>
        </body>
      </opml>
    """.trimIndent()

    val parsed = OpmlParser.parse(opml.toByteArray(Charsets.UTF_8))
    assertEquals(
      listOf(OpmlFeed(title = "Nested Feed", url = "https://example.com/nested.xml", folder = "Inner")),
      parsed,
    )
  }

  @Test
  fun parseInvalidXml_throwsOpmlException() {
    assertThrows(OpmlException::class.java) {
      OpmlParser.parse("<rss><channel></channel></rss>".toByteArray(Charsets.UTF_8))
    }
  }
}
