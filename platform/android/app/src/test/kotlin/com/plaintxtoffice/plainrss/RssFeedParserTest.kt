package com.plaintxtoffice.plainrss

import com.plaintxtoffice.plainrss.domain.FeedDiscovery
import com.plaintxtoffice.plainrss.domain.RssDateParser
import com.plaintxtoffice.plainrss.domain.RssFeedParser
import com.plaintxtoffice.plainrss.model.RssParserException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RssFeedParserTest {
  @Test
  fun parseRss20Feed_decodesEntitiesAndPrefersContentEncoded() {
    val xml = """
      <?xml version="1.0" encoding="UTF-8"?>
      <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
        <channel>
          <title>Plain &amp; Simple News</title>
          <image><title>Ignored Image Title</title></image>
          <item>
            <title>First &amp;mdash; Post &#x263A;</title>
            <link>https://example.com/posts/1</link>
            <guid>guid-1</guid>
            <pubDate>Mon, 26 Oct 2026 14:30:00 GMT</pubDate>
            <description><![CDATA[<p>Short teaser &amp; summary.</p>]]></description>
            <content:encoded><![CDATA[<p>Full article body.</p>]]></content:encoded>
          </item>
        </channel>
      </rss>
    """.trimIndent()

    val parsed = RssFeedParser.parse(xml.toByteArray(Charsets.UTF_8))
    assertEquals("Plain & Simple News", parsed.title)
    assertEquals(1, parsed.articles.size)
    val article = parsed.articles.first()
    assertEquals("First \u2014 Post \u263A", article.title)
    assertEquals("https://example.com/posts/1", article.link)
    assertEquals("guid-1", article.guid)
    assertEquals("Short teaser & summary.", article.summary)
    assertEquals("<p>Full article body.</p>", article.contentHtml)
    assertNotNull(article.publishedAt)
  }

  @Test
  fun parseAtomFeed_selectsAlternateLinkAndKeepsPublishedOverUpdated() {
    val xml = """
      <?xml version="1.0" encoding="utf-8"?>
      <feed xmlns="http://www.w3.org/2005/Atom">
        <title>Atom Journal</title>
        <author><name>Editor</name></author>
        <entry>
          <title>Atom Entry</title>
          <link rel="self" href="https://example.com/atom/entry1.xml"/>
          <link rel="alternate" href="https://example.com/posts/atom-1"/>
          <id>tag:example.com,2026:atom-1</id>
          <published>2026-10-20T10:00:00Z</published>
          <updated>2026-10-25T12:00:00Z</updated>
          <summary type="html">&lt;p&gt;Atom summary text&lt;/p&gt;</summary>
        </entry>
      </feed>
    """.trimIndent()

    val parsed = RssFeedParser.parse(xml.toByteArray(Charsets.UTF_8))
    assertEquals("Atom Journal", parsed.title)
    assertEquals(1, parsed.articles.size)
    val article = parsed.articles.first()
    assertEquals("Atom Entry", article.title)
    assertEquals("https://example.com/posts/atom-1", article.link)
    assertEquals("tag:example.com,2026:atom-1", article.guid)
    assertEquals("Atom summary text", article.summary)
    assertEquals(RssDateParser.parse("2026-10-20T10:00:00Z"), article.publishedAt)
  }

  @Test
  fun parseNonFeedXml_throwsRssParserException() {
    val html = "<html><body><h1>Not a feed</h1></body></html>"
    assertThrows(RssParserException::class.java) {
      RssFeedParser.parse(html.toByteArray(Charsets.UTF_8))
    }
  }

  @Test
  fun feedDiscovery_findsAlternateFeedLinkAndResolvesRelativeUrl() {
    val html = """
      <!doctype html>
      <html>
        <head>
          <link rel="stylesheet" href="/style.css">
          <link rel="alternate" type="application/rss+xml" title="RSS" href="/blog/feed.xml">
        </head>
        <body>Hello</body>
      </html>
    """.trimIndent()

    val discovered = FeedDiscovery.feedUrl(
      html.toByteArray(Charsets.UTF_8),
      "https://example.com/blog/index.html",
    )
    assertEquals("https://example.com/blog/feed.xml", discovered)
  }

  @Test
  fun rssDateParser_parsesNamedTimeZonesAndIso8601() {
    assertNotNull(RssDateParser.parse("Tue, 27 Oct 2026 09:15:00 EDT"))
    assertNotNull(RssDateParser.parse("2026-10-27T13:15:00.123Z"))
    assertNull(RssDateParser.parse("not-a-date"))
  }
}
