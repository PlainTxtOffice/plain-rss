package com.plaintxtoffice.plainrss.domain

import com.plaintxtoffice.plainrss.model.OpmlException
import com.plaintxtoffice.plainrss.model.OpmlFeed
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/**
 * Formats subscription lists as OPML 2.0 XML documents.
 */
object OpmlWriter {
  fun write(feeds: List<OpmlFeed>, dateCreatedMillis: Long = System.currentTimeMillis()): String {
    fun outline(feed: OpmlFeed, indent: String): String {
      val title = HtmlText.escapeHtml(feed.title)
      val xmlUrl = HtmlText.escapeHtml(feed.url)
      return "$indent<outline type=\"rss\" text=\"$title\" title=\"$title\" xmlUrl=\"$xmlUrl\"/>"
    }

    val outlines = mutableListOf<String>()
    feeds.filter { it.folder == null }.forEach { outlines.add(outline(it, "    ")) }

    val folderNames = mutableListOf<String>()
    for (folder in feeds.mapNotNull { it.folder }) {
      if (folder !in folderNames) {
        folderNames.add(folder)
      }
    }
    for (name in folderNames) {
      val escaped = HtmlText.escapeHtml(name)
      outlines.add("    <outline text=\"$escaped\" title=\"$escaped\">")
      feeds.filter { it.folder == name }.forEach { outlines.add(outline(it, "      ")) }
      outlines.add("    </outline>")
    }

    val rfc822Formatter = SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("GMT")
    }
    val dateText = rfc822Formatter.format(Date(dateCreatedMillis))

    return buildString {
      append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
      append("<opml version=\"2.0\">\n")
      append("  <head>\n")
      append("    <title>plain RSS Subscriptions</title>\n")
      append("    <dateCreated>$dateText</dateCreated>\n")
      append("  </head>\n")
      append("  <body>\n")
      if (outlines.isNotEmpty()) {
        append(outlines.joinToString("\n"))
        append("\n")
      }
      append("  </body>\n")
      append("</opml>\n")
    }
  }
}

/**
 * Parses OPML subscription files, recording each feed's innermost enclosing folder outline.
 */
object OpmlParser {
  fun parse(data: ByteArray): List<OpmlFeed> {
    val handler = OpmlSaxHandler()
    try {
      val factory = SAXParserFactory.newInstance().apply {
        isNamespaceAware = false
        // Android's Expat-based factory rejects every feature outside
        // http://xml.org/sax/features/, so each hardening flag is best-effort.
        runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
      }
      val parser = factory.newSAXParser()
      parser.parse(InputSource(ByteArrayInputStream(data)), handler)
    } catch (e: Exception) {
      throw OpmlException(cause = e)
    }
    if (!handler.sawOpmlRoot) {
      throw OpmlException()
    }
    return handler.feeds
  }

  private class OpmlSaxHandler : DefaultHandler() {
    val feeds = mutableListOf<OpmlFeed>()
    var sawOpmlRoot = false
    private val folderStack = ArrayDeque<String?>()

    override fun startElement(
      uri: String?,
      localName: String?,
      qName: String?,
      attributes: Attributes,
    ) {
      val name = (qName?.takeIf { it.isNotEmpty() } ?: localName ?: "").lowercase(Locale.ROOT)
      when (name) {
        "opml" -> sawOpmlRoot = true
        "outline" -> {
          val titleAttr = attributes.valueIgnoreCase("title")?.trim()?.takeIf { it.isNotEmpty() }
          val textAttr = attributes.valueIgnoreCase("text")?.trim()?.takeIf { it.isNotEmpty() }
          val title = titleAttr ?: textAttr
          val enclosing = if (folderStack.isEmpty()) null else folderStack.last()
          val xmlUrl = attributes.valueIgnoreCase("xmlurl")?.trim()?.takeIf { it.isNotEmpty() }

          if (xmlUrl == null) {
            folderStack.addLast(title ?: enclosing)
          } else {
            feeds.add(
              OpmlFeed(
                title = title ?: xmlUrl,
                url = xmlUrl,
                folder = enclosing,
              )
            )
            folderStack.addLast(enclosing)
          }
        }
      }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
      val name = (qName?.takeIf { it.isNotEmpty() } ?: localName ?: "").lowercase(Locale.ROOT)
      if (name == "outline" && folderStack.isNotEmpty()) {
        folderStack.removeLast()
      }
    }
  }
}
