import Foundation

// MARK: - Feed Parser

nonisolated struct ParsedFeed {
    var title: String
    var articles: [ParsedArticle]
}

nonisolated struct ParsedArticle {
    var title: String = "Untitled Article"
    var link: String?
    var summary: String = ""
    var contentHTML: String?
    var publishedAt: Date?
    var guid: String?
}

nonisolated final class RSSFeedParser: NSObject, XMLParserDelegate {
    private var feedTitle = "Untitled Feed"
    private var articles: [ParsedArticle] = []
    private var currentElement = ""
    private var currentText = ""
    private var currentItem: ParsedArticle?
    private var isInsideItem = false
    private var isInsideChannel = false
    private var nonItemDepth = 0
    private var hasFeedTitle = false
    private var sawFeedRoot = false
    private var currentLinkIsAlternate = false
    private var hasPrimaryDate = false

    static func parse(data: Data, sourceURL: URL) throws -> ParsedFeed {
        let delegate = RSSFeedParser()

        let parser = XMLParser(data: data)
        parser.delegate = delegate

        guard parser.parse() else {
            throw parser.parserError ?? RSSParserError.invalidFeed
        }
        guard delegate.sawFeedRoot else {
            throw RSSParserError.invalidFeed
        }

        return ParsedFeed(
            title: delegate.feedTitle,
            articles: delegate.articles
        )
    }

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        currentElement = elementName.lowercased()
        currentText = ""

        switch currentElement {
        case "rss", "feed", "rdf:rdf":
            sawFeedRoot = true
            if currentElement == "feed" { isInsideChannel = true }
        case "channel":
            isInsideChannel = true
        case "item", "entry":
            isInsideItem = true
            currentItem = ParsedArticle()
            currentLinkIsAlternate = false
            hasPrimaryDate = false
        case "image", "textinput", "author", "source", "contributor":
            if !isInsideItem { nonItemDepth += 1 }
        case "link":
            // Atom entries can carry several links; only the alternate one is the article page.
            if isInsideItem, let href = attributeDict["href"] {
                let rel = (attributeDict["rel"] ?? "alternate").lowercased()
                if rel == "alternate", !currentLinkIsAlternate {
                    currentItem?.link = href
                    currentLinkIsAlternate = true
                } else if currentItem?.link == nil, rel != "self", rel != "enclosure" {
                    currentItem?.link = href
                }
            }
        default:
            break
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        currentText += string
    }

    func parser(_ parser: XMLParser, foundCDATA CDATABlock: Data) {
        if let string = String(data: CDATABlock, encoding: .utf8) {
            currentText += string
        }
    }

    func parser(
        _ parser: XMLParser,
        didEndElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?
    ) {
        let element = elementName.lowercased()
        let text = currentText.trimmingCharacters(in: .whitespacesAndNewlines)

        if isInsideItem {
            applyArticleText(text, for: element)
        } else if isInsideChannel, element == "title", !text.isEmpty, nonItemDepth == 0, !hasFeedTitle {
            feedTitle = text.htmlDecoded
            hasFeedTitle = true
        }

        switch element {
        case "image", "textinput", "author", "source", "contributor":
            if !isInsideItem, nonItemDepth > 0 { nonItemDepth -= 1 }
        case "item", "entry":
            if let item = currentItem {
                articles.append(item)
            }
            currentItem = nil
            isInsideItem = false
        case "channel", "feed":
            isInsideChannel = false
        default:
            break
        }

        currentText = ""
    }

    private func applyArticleText(_ text: String, for element: String) {
        guard !text.isEmpty else { return }

        switch element {
        case "title":
            currentItem?.title = text.htmlDecoded
        case "link":
            if currentItem?.link == nil {
                currentItem?.link = text
            }
        case "guid", "id":
            currentItem?.guid = text
        case "description", "summary", "content", "content:encoded":
            if currentItem?.summary.isEmpty == true {
                currentItem?.summary = text.strippingHTML.htmlDecoded.collapsingWhitespace
            }
            // Full content beats a teaser, whichever order the feed lists them in.
            if element == "content" || element == "content:encoded" || currentItem?.contentHTML == nil {
                currentItem?.contentHTML = text
            }
        case "pubdate", "published":
            if let date = RSSDateParser.parse(text) {
                currentItem?.publishedAt = date
                hasPrimaryDate = true
            }
        case "updated", "dc:date":
            // Only a fallback: an edit date must not replace the original publication date.
            if !hasPrimaryDate, let date = RSSDateParser.parse(text) {
                currentItem?.publishedAt = date
            }
        default:
            break
        }
    }
}

enum RSSParserError: LocalizedError {
    case invalidFeed

    var errorDescription: String? {
        "The feed could not be parsed."
    }
}

enum FeedError: LocalizedError {
    case invalidResponse
    case serverError(Int)
    case tooLarge
    case noFeedFound

    var errorDescription: String? {
        switch self {
        case .invalidResponse:
            return "Invalid response from server."
        case .serverError(let statusCode):
            return "The feed server returned an error (HTTP \(statusCode))."
        case .tooLarge:
            return "That feed is too large to download."
        case .noFeedFound:
            return "No RSS or Atom feed was found at that address."
        }
    }
}

nonisolated enum RSSDateParser {
    private static func formatter(_ format: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = format
        return formatter
    }

    private static let rfc822Formatters = [
        "EEE, d MMM yyyy HH:mm:ss Z",
        "d MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm Z",
        "d MMM yyyy HH:mm Z",
        "EEE, d MMM yy HH:mm:ss Z",
    ].map(formatter)

    private static let iso8601Formatter = ISO8601DateFormatter()

    private static let iso8601FractionalFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    /// RFC 822 zone names that `DateFormatter`'s numeric `Z` pattern does not understand.
    private static let zoneOffsets: [String: String] = [
        "UT": "+0000", "UTC": "+0000", "GMT": "+0000", "Z": "+0000",
        "EST": "-0500", "EDT": "-0400", "CST": "-0600", "CDT": "-0500",
        "MST": "-0700", "MDT": "-0600", "PST": "-0800", "PDT": "-0700",
    ]

    static func parse(_ string: String) -> Date? {
        let trimmed = string.trimmingCharacters(in: .whitespacesAndNewlines)
        if let date = iso8601Formatter.date(from: trimmed) ?? iso8601FractionalFormatter.date(from: trimmed) {
            return date
        }
        let normalized = normalizingZone(trimmed)
        for formatter in rfc822Formatters {
            if let date = formatter.date(from: normalized) { return date }
        }
        return nil
    }

    private static func normalizingZone(_ string: String) -> String {
        guard let space = string.lastIndex(of: " ") else { return string }
        let zone = string[string.index(after: space)...].uppercased()
        guard let offset = zoneOffsets[zone] else { return string }
        return string[..<space] + " " + offset
    }
}

nonisolated extension String {
    var htmlEscaped: String {
        replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
    }
}

nonisolated extension String {
    var strippingHTML: String {
        replacingOccurrences(
            of: "<(br|/p|/div|/li|/h[1-6]|/blockquote|/tr)[^>]*>",
            with: " ",
            options: [.regularExpression, .caseInsensitive]
        )
        .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
    }

    var collapsingWhitespace: String {
        split(whereSeparator: \.isWhitespace).joined(separator: " ")
    }

    var htmlDecoded: String {
        guard contains("&") else { return self }

        var result = ""
        result.reserveCapacity(count)
        var index = startIndex

        while let ampersand = self[index...].firstIndex(of: "&") {
            result += self[index..<ampersand]

            guard let semicolon = self[ampersand...].firstIndex(of: ";"),
                  distance(from: ampersand, to: semicolon) <= 10,
                  let decoded = Self.decodeEntity(String(self[self.index(after: ampersand)..<semicolon])) else {
                result.append("&")
                index = self.index(after: ampersand)
                continue
            }

            result.append(decoded)
            index = self.index(after: semicolon)
        }

        result += self[index...]
        return result
    }

    static func decodeEntity(_ entity: String) -> Character? {
        switch entity {
        case "amp": return "&"
        case "lt": return "<"
        case "gt": return ">"
        case "quot": return "\""
        case "apos": return "'"
        case "nbsp": return "\u{00A0}"
        case "ndash": return "\u{2013}"
        case "mdash": return "\u{2014}"
        case "lsquo": return "\u{2018}"
        case "rsquo": return "\u{2019}"
        case "ldquo": return "\u{201C}"
        case "rdquo": return "\u{201D}"
        case "hellip": return "\u{2026}"
        case "copy": return "\u{00A9}"
        case "reg": return "\u{00AE}"
        case "trade": return "\u{2122}"
        default:
            guard entity.hasPrefix("#") else { return nil }
            let number = entity.dropFirst()
            let value = number.hasPrefix("x") || number.hasPrefix("X")
                ? UInt32(number.dropFirst(), radix: 16)
                : UInt32(number)
            guard let value, let scalar = Unicode.Scalar(value) else { return nil }
            return Character(scalar)
        }
    }
}
