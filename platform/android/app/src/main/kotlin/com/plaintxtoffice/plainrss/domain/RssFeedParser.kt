package com.plaintxtoffice.plainrss.domain

import com.plaintxtoffice.plainrss.model.ParsedArticle
import com.plaintxtoffice.plainrss.model.ParsedFeed
import com.plaintxtoffice.plainrss.model.RssParserException
import java.io.ByteArrayInputStream
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.TimeZone
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/**
 * Parses RSS 2.0, Atom 1.0, and RDF/RSS 1.0 XML documents into [ParsedFeed].
 */
object RssFeedParser {
  fun parse(data: ByteArray): ParsedFeed {
    val handler = FeedSaxHandler()
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
      throw RssParserException(cause = e)
    }

    if (!handler.sawFeedRoot) {
      throw RssParserException()
    }
    return ParsedFeed(
      title = handler.feedTitle,
      articles = handler.articles,
    )
  }

  private class FeedSaxHandler : DefaultHandler() {
    var feedTitle: String = "Untitled Feed"
    val articles: MutableList<ParsedArticle> = mutableListOf()
    var sawFeedRoot: Boolean = false

    private var currentElement: String = ""
    private val currentText = StringBuilder()
    private var currentItem: MutableParsedArticle? = null
    private var isInsideItem = false
    private var isInsideChannel = false
    private var nonItemDepth = 0
    private var hasFeedTitle = false
    private var currentLinkIsAlternate = false
    private var hasPrimaryDate = false

    override fun startElement(
      uri: String?,
      localName: String?,
      qName: String?,
      attributes: Attributes,
    ) {
      val name = (qName?.takeIf { it.isNotEmpty() } ?: localName ?: "").lowercase(Locale.ROOT)
      currentElement = name
      currentText.setLength(0)

      when (name) {
        "rss", "feed", "rdf:rdf", "rdf" -> {
          sawFeedRoot = true
          if (name == "feed") isInsideChannel = true
        }
        "channel" -> {
          isInsideChannel = true
        }
        "item", "entry" -> {
          isInsideItem = true
          currentItem = MutableParsedArticle()
          currentLinkIsAlternate = false
          hasPrimaryDate = false
        }
        "image", "textinput", "author", "source", "contributor" -> {
          if (!isInsideItem) nonItemDepth += 1
        }
        "link" -> {
          if (isInsideItem) {
            val href = attributes.valueIgnoreCase("href")?.trim()
            if (!href.isNullOrEmpty()) {
              val rel = (attributes.valueIgnoreCase("rel") ?: "alternate").lowercase(Locale.ROOT)
              if (rel == "alternate" && !currentLinkIsAlternate) {
                currentItem?.link = href
                currentLinkIsAlternate = true
              } else if (currentItem?.link == null && rel != "self" && rel != "enclosure") {
                currentItem?.link = href
              }
            }
          }
        }
      }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
      currentText.appendRange(ch, start, start + length)
    }

    @Throws(SAXException::class)
    override fun endElement(uri: String?, localName: String?, qName: String?) {
      val element = (qName?.takeIf { it.isNotEmpty() } ?: localName ?: "").lowercase(Locale.ROOT)
      val text = currentText.toString().trim()

      if (isInsideItem) {
        applyArticleText(text, element)
      } else if (
        isInsideChannel &&
          element == "title" &&
          text.isNotEmpty() &&
          nonItemDepth == 0 &&
          !hasFeedTitle
      ) {
        feedTitle = HtmlText.decodeEntities(text)
        hasFeedTitle = true
      }

      when (element) {
        "image", "textinput", "author", "source", "contributor" -> {
          if (!isInsideItem && nonItemDepth > 0) nonItemDepth -= 1
        }
        "item", "entry" -> {
          currentItem?.let { articles.add(it.toParsedArticle()) }
          currentItem = null
          isInsideItem = false
        }
        "channel", "feed" -> {
          isInsideChannel = false
        }
      }
      currentText.setLength(0)
    }

    private fun applyArticleText(text: String, element: String) {
      if (text.isEmpty()) return
      val item = currentItem ?: return
      when (element) {
        "title" -> item.title = HtmlText.decodeEntities(text)
        "link" -> if (item.link == null) item.link = text
        "guid", "id" -> item.guid = text
        "description", "summary", "content", "content:encoded", "encoded" -> {
          if (item.summary.isEmpty()) {
            item.summary =
              HtmlText.collapseWhitespace(HtmlText.decodeEntities(HtmlText.stripHtml(text)))
          }
          if (element == "content" || element == "content:encoded" || element == "encoded" || item.contentHtml == null) {
            item.contentHtml = text
          }
        }
        "pubdate", "published" -> {
          RssDateParser.parse(text)?.let {
            item.publishedAt = it
            hasPrimaryDate = true
          }
        }
        "updated", "dc:date", "date" -> {
          if (!hasPrimaryDate) {
            RssDateParser.parse(text)?.let { item.publishedAt = it }
          }
        }
      }
    }
  }

  private class MutableParsedArticle {
    var title: String = "Untitled Article"
    var link: String? = null
    var summary: String = ""
    var contentHtml: String? = null
    var publishedAt: Long? = null
    var guid: String? = null

    fun toParsedArticle(): ParsedArticle =
      ParsedArticle(
        title = title,
        link = link,
        summary = summary,
        contentHtml = contentHtml,
        publishedAt = publishedAt,
        guid = guid,
      )
  }
}

internal fun Attributes.valueIgnoreCase(target: String): String? {
  for (i in 0 until length) {
    val name = getQName(i).takeIf { it.isNotEmpty() } ?: getLocalName(i)
    if (name.equals(target, ignoreCase = true)) {
      return getValue(i)
    }
  }
  return null
}

/**
 * Parses ISO 8601 and RFC 822 publication timestamps into epoch milliseconds.
 */
object RssDateParser {
  private val rfc822Patterns = listOf(
    "EEE, d MMM yyyy HH:mm:ss Z",
    "d MMM yyyy HH:mm:ss Z",
    "EEE, d MMM yyyy HH:mm Z",
    "d MMM yyyy HH:mm Z",
    "EEE, d MMM yy HH:mm:ss Z",
  )

  private val zoneOffsets = mapOf(
    "UT" to "+0000",
    "UTC" to "+0000",
    "GMT" to "+0000",
    "Z" to "+0000",
    "EST" to "-0500",
    "EDT" to "-0400",
    "CST" to "-0600",
    "CDT" to "-0500",
    "MST" to "-0700",
    "MDT" to "-0600",
    "PST" to "-0800",
    "PDT" to "-0700",
  )

  fun parse(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    try {
      return Instant.parse(trimmed).toEpochMilli()
    } catch (_: DateTimeParseException) {
    }

    try {
      return OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
    }

    val normalized = normalizeZone(trimmed)
    for (pattern in rfc822Patterns) {
      val formatter = SimpleDateFormat(pattern, Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
      }
      val pos = ParsePosition(0)
      val parsed = formatter.parse(normalized, pos)
      if (parsed != null && pos.index == normalized.length) {
        return parsed.time
      }
    }
    return null
  }

  private fun normalizeZone(value: String): String {
    val spaceIndex = value.lastIndexOf(' ')
    if (spaceIndex <= 0) return value
    val zone = value.substring(spaceIndex + 1).uppercase(Locale.ROOT)
    val offset = zoneOffsets[zone] ?: return value
    return value.substring(0, spaceIndex) + " " + offset
  }
}

/**
 * HTML entity decoding, escaping, and tag stripping helpers.
 */
object HtmlText {
  private val blockClosingRegex =
    Regex("<(br|/p|/div|/li|/h[1-6]|/blockquote|/tr)[^>]*>", RegexOption.IGNORE_CASE)
  private val anyTagRegex = Regex("<[^>]+>")
  private val whitespaceRegex = Regex("\\s+")

  fun escapeHtml(text: String): String =
    text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")

  fun stripHtml(text: String): String =
    text
      .replace(blockClosingRegex, " ")
      .replace(anyTagRegex, "")

  fun collapseWhitespace(text: String): String =
    text.trim().replace(whitespaceRegex, " ")

  fun decodeEntities(text: String): String {
    if (!text.contains('&')) return text
    val result = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
      val ampersand = text.indexOf('&', index)
      if (ampersand < 0) {
        result.append(text, index, text.length)
        break
      }
      result.append(text, index, ampersand)
      val semicolon = text.indexOf(';', ampersand + 1)
      if (semicolon > ampersand && semicolon - ampersand <= 12) {
        val entity = text.substring(ampersand + 1, semicolon)
        val decoded = decodeEntity(entity)
        if (decoded != null) {
          result.append(decoded)
          index = semicolon + 1
          continue
        }
      }
      result.append('&')
      index = ampersand + 1
    }
    return result.toString()
  }

  fun decodeEntity(entity: String): String? =
    when (entity) {
      "amp" -> "&"
      "lt" -> "<"
      "gt" -> ">"
      "quot" -> "\""
      "apos" -> "'"
      "nbsp" -> "\u00A0"
      "ndash" -> "\u2013"
      "mdash" -> "\u2014"
      "lsquo" -> "\u2018"
      "rsquo" -> "\u2019"
      "ldquo" -> "\u201C"
      "rdquo" -> "\u201D"
      "hellip" -> "\u2026"
      "copy" -> "\u00A9"
      "reg" -> "\u00AE"
      "trade" -> "\u2122"
      "colon" -> ":"
      "Tab" -> "\t"
      "NewLine" -> "\n"
      else -> {
        if (!entity.startsWith("#")) {
          null
        } else {
          val body = entity.substring(1)
          val codePoint =
            if (body.startsWith("x", ignoreCase = true)) {
              body.substring(1).toIntOrNull(16)
            } else {
              body.toIntOrNull(10)
            }
          if (codePoint != null && Character.isValidCodePoint(codePoint)) {
            String(Character.toChars(codePoint))
          } else {
            null
          }
        }
      }
    }
}

/**
 * Discovers an RSS or Atom `<link rel="alternate">` feed URL advertised inside an HTML document.
 */
object FeedDiscovery {
  private val feedTypes = setOf(
    "application/rss+xml",
    "application/atom+xml",
    "application/rdf+xml",
  )
  private val linkTagRegex = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
  private val attributeRegex =
    Regex("([a-zA-Z-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")

  fun feedUrl(data: ByteArray, pageUrl: String): String? {
    val maxLen = minOf(data.size, 500_000)
    val html = String(data, 0, maxLen, Charsets.UTF_8)
    for (match in linkTagRegex.findAll(html)) {
      val attrs = parseAttributes(match.value)
      val rel = attrs["rel"] ?: continue
      val relTokens = rel.lowercase(Locale.ROOT).split(Regex("\\s+"))
      if ("alternate" !in relTokens) continue
      val type = attrs["type"]?.lowercase(Locale.ROOT)?.substringBefore(';')?.trim() ?: continue
      if (type !in feedTypes) continue
      val href = attrs["href"] ?: continue
      val resolved = ReaderUrl.allowed(href, pageUrl) ?: continue
      return resolved
    }
    return null
  }

  private fun parseAttributes(tag: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    for (match in attributeRegex.findAll(tag)) {
      val key = match.groupValues[1].lowercase(Locale.ROOT)
      val rawValue = match.groups[2]?.value ?: match.groups[3]?.value ?: ""
      if (!result.containsKey(key)) {
        result[key] = HtmlText.decodeEntities(rawValue)
      }
    }
    return result
  }
}
