import Foundation
import SwiftUI
import UniformTypeIdentifiers

nonisolated extension UTType {
    static let opml = UTType(filenameExtension: "opml", conformingTo: .xml) ?? .xml
}

nonisolated struct OPMLFeed: Equatable {
    var title: String
    var url: String
    var folder: String? = nil
}

// MARK: - Writing

nonisolated enum OPMLWriter {
    static func write(_ feeds: [OPMLFeed], dateCreated: Date = Date()) -> String {
        func outline(_ feed: OPMLFeed, indent: String) -> String {
            let title = feed.title.htmlEscaped
            return "\(indent)<outline type=\"rss\" text=\"\(title)\" title=\"\(title)\" xmlUrl=\"\(feed.url.htmlEscaped)\"/>"
        }
        var outlines = feeds.filter { $0.folder == nil }.map { outline($0, indent: "    ") }
        var folderNames: [String] = []
        for name in feeds.compactMap(\.folder) where !folderNames.contains(name) { folderNames.append(name) }
        for name in folderNames {
            let escaped = name.htmlEscaped
            outlines.append("    <outline text=\"\(escaped)\" title=\"\(escaped)\">")
            outlines += feeds.filter { $0.folder == name }.map { outline($0, indent: "      ") }
            outlines.append("    </outline>")
        }

        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <opml version="2.0">
          <head>
            <title>Plain RSS Subscriptions</title>
            <dateCreated>\(rfc822Formatter.string(from: dateCreated))</dateCreated>
          </head>
          <body>
        \(outlines.joined(separator: "\n"))
          </body>
        </opml>

        """
    }

    private static let rfc822Formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "EEE, d MMM yyyy HH:mm:ss Z"
        return formatter
    }()
}

// MARK: - Reading

enum OPMLError: LocalizedError {
    case invalidFile

    var errorDescription: String? {
        "That file isn't a valid OPML subscription list."
    }
}

/// Collects every outline with an `xmlUrl`. Each feed records its innermost enclosing folder outline.
nonisolated final class OPMLParser: NSObject, XMLParserDelegate {
    private var feeds: [OPMLFeed] = []
    private var sawOPMLRoot = false
    private var folderStack: [String?] = []

    static func parse(data: Data) throws -> [OPMLFeed] {
        let delegate = OPMLParser()
        let parser = XMLParser(data: data)
        parser.delegate = delegate

        guard parser.parse(), delegate.sawOPMLRoot else {
            throw OPMLError.invalidFile
        }
        return delegate.feeds
    }

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        switch elementName.lowercased() {
        case "opml":
            sawOPMLRoot = true
        case "outline":
            // Attribute names vary in case between apps ("xmlUrl" vs "xmlurl").
            let attributes = Dictionary(
                attributeDict.map { ($0.key.lowercased(), $0.value) },
                uniquingKeysWith: { first, _ in first }
            )

            let title = [attributes["title"], attributes["text"]]
                .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
                .first { !$0.isEmpty }
            let enclosing = folderStack.last ?? nil

            guard let url = attributes["xmlurl"]?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !url.isEmpty else {
                folderStack.append(title ?? enclosing)
                return
            }

            feeds.append(OPMLFeed(title: title ?? url, url: url, folder: enclosing))
            folderStack.append(enclosing)
        default:
            break
        }
    }

    func parser(
        _ parser: XMLParser,
        didEndElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?
    ) {
        if elementName.lowercased() == "outline" { _ = folderStack.popLast() }
    }
}

// MARK: - Export Document

struct OPMLDocument: FileDocument {
    static let readableContentTypes: [UTType] = [.opml, .xml]

    var text: String

    init(feeds: [OPMLFeed]) {
        text = OPMLWriter.write(feeds)
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents,
              let text = String(data: data, encoding: .utf8) else {
            throw OPMLError.invalidFile
        }
        self.text = text
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}
