package com.plaintxtoffice.plainrss

import com.plaintxtoffice.plainrss.domain.OpmlParser
import com.plaintxtoffice.plainrss.domain.RssFeedParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Runs the XML parsers against Android's own SAX implementation, which accepts
 * fewer parser features than the desktop JVM used by local unit tests.
 */
class XmlParsingOnDeviceTest {
  @Test
  fun rssFeed_parsesWithPlatformSaxParser() {
    val xml =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <rss version="2.0"><channel><title>Device Feed</title>
      <item><title>First</title><link>https://example.com/1</link><guid>1</guid></item>
      </channel></rss>
      """.trimIndent()

    val feed = RssFeedParser.parse(xml.toByteArray(Charsets.UTF_8))

    assertEquals("Device Feed", feed.title)
    assertEquals(listOf("First"), feed.articles.map { it.title })
  }

  @Test
  fun opml_parsesWithPlatformSaxParser() {
    val xml =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <opml version="2.0"><body>
      <outline text="Tech"><outline text="Example" xmlUrl="https://example.com/feed.xml"/></outline>
      </body></opml>
      """.trimIndent()

    val feeds = OpmlParser.parse(xml.toByteArray(Charsets.UTF_8))

    assertEquals(listOf("https://example.com/feed.xml"), feeds.map { it.url })
    assertEquals("Tech", feeds.single().folder)
  }
}
