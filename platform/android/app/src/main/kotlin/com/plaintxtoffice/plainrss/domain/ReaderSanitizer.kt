package com.plaintxtoffice.plainrss.domain

import java.net.URI
import java.util.Locale
import java.util.UUID

/**
 * Validates that only safe HTTP/HTTPS URLs without credentials or control characters leave the reader.
 */
object ReaderUrl {
  fun allowed(value: String, baseUrl: String? = null): String? {
    if (value.any { it.code < 32 || it.code == 127 }) return null
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    return try {
      val resolved =
        if (baseUrl != null) {
          URI(baseUrl.trim()).resolve(trimmed)
        } else {
          URI(trimmed)
        }
      val scheme = resolved.scheme?.lowercase(Locale.ROOT) ?: return null
      if (scheme != "http" && scheme != "https") return null
      val host = resolved.host
      if (host.isNullOrEmpty()) return null
      if (resolved.userInfo != null) return null
      resolved.toASCIIString()
    } catch (_: Exception) {
      null
    }
  }
}

/**
 * Input to the private article HTML sanitizer.
 */
data class ReaderInput(
  val title: String,
  val metadata: String,
  val body: String,
  val baseUrl: String? = null,
  val allowsImages: Boolean = false,
)

/**
 * Sanitized HTML document and local-to-remote URL mappings for images and links.
 */
data class ReaderDocument(
  val html: String,
  val images: Map<String, String>,
  val links: Map<String, String>,
)

/**
 * Reconstructs a small, safe HTML vocabulary for the private article reader.
 * No publisher CSS, attributes, executable markup, namespaces, raw text elements,
 * or unmapped external URLs enter the generated document.
 */
object ReaderSanitizer {
  val formattingTags: Set<String> = setOf(
    "p", "div", "span", "br", "hr", "strong", "b", "em", "i", "u", "s",
    "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote",
    "pre", "code", "table", "thead", "tbody", "tfoot", "tr", "td", "th",
    "figure", "figcaption", "a",
  )

  val discardedTags: Set<String> = setOf(
    "script", "style", "iframe", "object", "embed", "svg", "math", "form",
    "video", "audio", "template", "noscript", "textarea", "title", "xmp", "plaintext",
  )

  fun escaped(text: String): String = HtmlText.escapeHtml(text)

  fun decoded(text: String): String = HtmlText.decodeEntities(text)

  fun document(
    input: ReaderInput,
    generation: String = UUID.randomUUID().toString().lowercase(Locale.ROOT),
  ): ReaderDocument {
    val images = linkedMapOf<String, String>()
    val links = linkedMapOf<String, String>()
    val output = StringBuilder()
    val bodyText = if (input.body.length > 500_000) input.body.substring(0, 500_000) else input.body
    var i = 0
    val n = bodyText.length
    val suppressed = mutableListOf<String>()

    while (i < n) {
      if (bodyText[i] != '<') {
        val start = i
        while (i < n && bodyText[i] != '<') i++
        if (suppressed.isEmpty()) {
          output.append(escaped(decoded(bodyText.substring(start, i))))
        }
        continue
      }

      if (bodyText.startsWith("<!--", i)) {
        val endComment = bodyText.indexOf("-->", i + 4)
        i = if (endComment >= 0) minOf(n, endComment + 3) else n
        continue
      }

      // A '<' that does not start a tag, comment, or declaration ("a < b", "<3") is text.
      val afterSlash = if (bodyText.getOrNull(i + 1) == '/') i + 2 else i + 1
      val tagLead = bodyText.getOrNull(afterSlash)
      val startsMarkup =
        tagLead != null &&
          (tagLead in 'a'..'z' || tagLead in 'A'..'Z' || tagLead == '!' || tagLead == '?')
      if (!startsMarkup) {
        if (suppressed.isEmpty()) output.append("&lt;")
        i++
        continue
      }

      i++
      var closing = false
      if (i < n && bodyText[i] == '/') {
        closing = true
        i++
      }

      val nameStart = i
      while (i < n && bodyText[i].code < 128 && bodyText[i].isLetterOrDigit()) {
        i++
      }
      val name = bodyText.substring(nameStart, i).lowercase(Locale.ROOT)
      val attributes = mutableMapOf<String, String>()
      var complete = false

      while (i < n) {
        while (i < n && bodyText[i].isWhitespace()) i++
        if (i >= n) break
        if (bodyText[i] == '>') {
          i++
          complete = true
          break
        }
        if (bodyText[i] == '/') {
          i++
          continue
        }
        val start = i
        while (
          i < n &&
            !bodyText[i].isWhitespace() &&
            bodyText[i] != '=' &&
            bodyText[i] != '>' &&
            bodyText[i] != '/'
        ) {
          i++
        }
        if (i == start) {
          i++
          continue
        }
        val key = bodyText.substring(start, i).lowercase(Locale.ROOT)
        while (i < n && bodyText[i].isWhitespace()) i++
        var value = ""
        if (i < n && bodyText[i] == '=') {
          i++
          while (i < n && bodyText[i].isWhitespace()) i++
          if (i < n && (bodyText[i] == '\'' || bodyText[i] == '"')) {
            val quote = bodyText[i]
            i++
            val valStart = i
            while (i < n && bodyText[i] != quote) i++
            value = bodyText.substring(valStart, i)
            if (i < n) i++
          } else {
            val valStart = i
            while (i < n && !bodyText[i].isWhitespace() && bodyText[i] != '>') i++
            value = bodyText.substring(valStart, i)
          }
        }
        if (!attributes.containsKey(key)) {
          attributes[key] = decoded(value)
        }
      }

      if (!complete) break

      if (suppressed.isNotEmpty()) {
        if (closing && name == suppressed.last()) {
          suppressed.removeAt(suppressed.lastIndex)
        } else if (!closing && name == suppressed.last()) {
          suppressed.add(name)
        }
        continue
      }

      if (name in discardedTags) {
        if (!closing && name != "embed") {
          suppressed.add(name)
        }
        continue
      }

      if (name == "img" && !closing) {
        val alt = escaped(attributes["alt"] ?: "Image")
        val src = attributes["src"]
        val remote = if (src != null) ReaderUrl.allowed(src, input.baseUrl) else null
        if (input.allowsImages && images.size < 40 && remote != null) {
          val local = "rss-image://$generation/${images.size}"
          images[local] = remote
          output.append("<img src=\"$local\" alt=\"$alt\">")
        } else {
          output.append("<span> [$alt] </span>")
        }
        continue
      }

      if (name !in formattingTags) continue
      if (closing) {
        output.append("</$name>")
        continue
      }
      if (name == "a") {
        val href = attributes["href"]
        val remote = if (href != null) ReaderUrl.allowed(href, input.baseUrl) else null
        if (remote != null) {
          val local = "rss-link://$generation/${links.size}"
          links[local] = remote
          output.append("<a href=\"$local\">")
        } else {
          output.append("<a>")
        }
      } else {
        output.append("<$name>")
      }
    }

    val nonce = UUID.randomUUID().toString()
    val imagePolicy = if (input.allowsImages) "rss-image:" else "'none'"
    val html = buildString {
      append("<!doctype html><html><head><meta charset=\"utf-8\">")
      append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
      append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'none'; style-src 'nonce-$nonce'; img-src $imagePolicy; connect-src 'none'; frame-src 'none'; object-src 'none'; media-src 'none'; font-src 'none'; base-uri 'none'; form-action 'none';\">")
      append("<meta http-equiv=\"x-dns-prefetch-control\" content=\"off\">")
      append("<style nonce=\"$nonce\">\n")
      append(":root{color-scheme:light dark}body{font-family:sans-serif;font-size:16px;line-height:1.55;margin:8px 20px 48px;overflow-wrap:break-word}h1{font-size:24px;font-weight:bold;line-height:1.25}.meta{color:#8e8e93;margin-bottom:24px}img{max-width:100%;height:auto}a{color:#e0521a}blockquote{border-left:3px solid #888;padding-left:14px}pre,table{overflow-x:auto}code{font-family:monospace}\n")
      append("</style></head><body>")
      append("<h1>${escaped(input.title)}</h1>")
      append("<p class=\"meta\">${escaped(input.metadata)}</p>")
      append(output)
      append("</body></html>")
    }

    return ReaderDocument(
      html = html,
      images = images,
      links = links,
    )
  }
}
