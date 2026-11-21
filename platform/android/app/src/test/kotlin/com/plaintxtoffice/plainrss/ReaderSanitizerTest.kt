package com.plaintxtoffice.plainrss

import com.plaintxtoffice.plainrss.domain.ReaderInput
import com.plaintxtoffice.plainrss.domain.ReaderSanitizer
import com.plaintxtoffice.plainrss.domain.ReaderUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSanitizerTest {
  @Test
  fun sanitizeDocument_removesScriptsStylesAndReplacesBlockedImagesWithAltText() {
    val input = ReaderInput(
      title = "Test Article",
      metadata = "Example Feed · Just now",
      body = """
        <script>alert('xss')</script>
        <style>body { display: none; }</style>
        <p onclick="steal()">Hello <strong>world</strong>!</p>
        <img src="https://example.com/photo.jpg" alt="Sunset photo">
        <a href="https://example.com/story">Read more</a>
        <a href="javascript:alert(1)">Bad link</a>
      """.trimIndent(),
      baseUrl = "https://example.com",
      allowsImages = false,
    )

    val doc = ReaderSanitizer.document(input, generation = "gen1")
    assertFalse(doc.html.contains("alert"))
    assertFalse(doc.html.contains("onclick"))
    assertTrue(doc.html.contains("img-src 'none'"))
    assertTrue(doc.html.contains("<span> [Sunset photo] </span>"))
    assertTrue(doc.images.isEmpty())
    assertEquals(mapOf("rss-link://gen1/0" to "https://example.com/story"), doc.links)
    assertTrue(doc.html.contains("<a href=\"rss-link://gen1/0\">Read more</a>"))
    assertTrue(doc.html.contains("<a>Bad link</a>"))
  }

  @Test
  fun sanitizeDocument_mapsAllowedImagesToCustomScheme() {
    val input = ReaderInput(
      title = "Article With Image",
      metadata = "Feed",
      body = "<p><img src=\"/images/pic.png\" alt=\"Diagram\"></p>",
      baseUrl = "https://example.com/posts/1",
      allowsImages = true,
    )

    val doc = ReaderSanitizer.document(input, generation = "gen2")
    assertTrue(doc.html.contains("img-src rss-image:"))
    assertTrue(doc.html.contains("<img src=\"rss-image://gen2/0\" alt=\"Diagram\">"))
    assertEquals(
      mapOf("rss-image://gen2/0" to "https://example.com/images/pic.png"),
      doc.images,
    )
  }

  @Test
  fun sanitizeDocument_keepsTextAroundALoneLessThanSign() {
    val input = ReaderInput(
      title = "Maths",
      metadata = "Feed",
      body = "<p>if a < b then c > d</p><p>I <3 feeds</p><p>tail a < b",
    )

    val doc = ReaderSanitizer.document(input, generation = "gen3")
    assertTrue(doc.html.contains("<p>if a &lt; b then c &gt; d</p>"))
    assertTrue(doc.html.contains("<p>I &lt;3 feeds</p>"))
    assertTrue(doc.html.contains("<p>tail a &lt; b"))
  }

  @Test
  fun readerUrl_rejectsUnsafeUrlsAndCredentials() {
    assertEquals("https://example.com/feed.xml", ReaderUrl.allowed("https://example.com/feed.xml"))
    assertEquals(
      "https://example.com/sub/page.html",
      ReaderUrl.allowed("page.html", "https://example.com/sub/index.html"),
    )
    assertNull(ReaderUrl.allowed("javascript:alert(1)"))
    assertNull(ReaderUrl.allowed("file:///etc/passwd"))
    assertNull(ReaderUrl.allowed("https://user:pass@example.com/secret"))
    assertNull(ReaderUrl.allowed("https://example.com/bad\u0007path"))
  }
}
