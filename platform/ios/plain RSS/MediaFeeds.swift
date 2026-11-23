import Foundation

/// Recognises YouTube addresses so a channel or playlist can be subscribed to like any feed.
/// YouTube publishes an Atom feed for every channel and playlist; no account or API key is involved.
nonisolated enum YouTubeAddress {
    private static let hosts: Set<String> = ["youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com"]

    static func isYouTube(_ url: URL) -> Bool {
        guard let host = url.host()?.lowercased() else { return false }
        return hosts.contains(host) || host == "youtu.be"
    }

    /// The feed for a channel or playlist address that names its ID, found without a network request.
    /// Returns nil for anything else, including `@handle` addresses, whose feed the channel page advertises.
    static func feedURL(for url: URL) -> URL? {
        guard let host = url.host()?.lowercased(), hosts.contains(host),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: true) else { return nil }
        let path = components.path.split(separator: "/").map(String.init)

        if path.count >= 2, path[0] == "channel", isChannelID(path[1]) {
            return feed(named: "channel_id", value: path[1])
        }
        if path.first == "playlist",
           let list = components.queryItems?.first(where: { $0.name == "list" })?.value, isID(list) {
            return feed(named: "playlist_id", value: list)
        }
        return nil
    }

    /// Whether an article link plays a video, so the reader can label its button accordingly.
    static func isVideo(_ url: URL) -> Bool {
        guard isYouTube(url) else { return false }
        if url.host()?.lowercased() == "youtu.be" { return url.path.count > 1 }
        let path = url.path
        return path == "/watch" || path.hasPrefix("/shorts/") || path.hasPrefix("/live/")
    }

    private static func feed(named name: String, value: String) -> URL? {
        var components = URLComponents()
        components.scheme = "https"
        components.host = "www.youtube.com"
        components.path = "/feeds/videos.xml"
        components.queryItems = [URLQueryItem(name: name, value: value)]
        return components.url
    }

    private static func isChannelID(_ value: String) -> Bool {
        value.count == 24 && value.hasPrefix("UC") && isID(value)
    }

    private static func isID(_ value: String) -> Bool {
        !value.isEmpty && value.count <= 64 && value.unicodeScalars.allSatisfy {
            $0.isASCII && (CharacterSet.alphanumerics.contains($0) || $0 == "_" || $0 == "-")
        }
    }
}

/// Builds reader content for entries that describe themselves only through Media RSS,
/// as YouTube's feeds do: a thumbnail and a plain-text description.
nonisolated enum MediaEntryContent {
    static func html(description: String?, thumbnail: String?) -> String? {
        var parts: [String] = []
        if let thumbnail, !thumbnail.isEmpty {
            parts.append("<p><img src=\"\(thumbnail.htmlEscaped)\" alt=\"Video thumbnail\"></p>")
        }
        if let description {
            let paragraphs = description
                .replacingOccurrences(of: "\r\n", with: "\n")
                .components(separatedBy: "\n\n")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
            for paragraph in paragraphs {
                let lines = paragraph.split(separator: "\n").map { linked(String($0).htmlEscaped) }
                parts.append("<p>\(lines.joined(separator: "<br>"))</p>")
            }
        }
        return parts.isEmpty ? nil : parts.joined()
    }

    /// A link that plays or opens an entry's attachment, such as a podcast episode.
    static func enclosureHTML(url: String, type: String?) -> String? {
        let address = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !address.isEmpty else { return nil }
        let kind = type?.lowercased() ?? ""
        let label = kind.hasPrefix("audio/") ? "Play audio"
            : kind.hasPrefix("video/") ? "Play video"
            : kind.hasPrefix("image/") ? "Open image"
            : "Open attachment"
        return "<p><a href=\"\(address.htmlEscaped)\">\(label)</a></p>"
    }

    /// Turns plain web addresses in already-escaped text into links.
    private static func linked(_ escaped: String) -> String {
        guard escaped.contains("http"),
              let pattern = try? NSRegularExpression(pattern: "https?://(?:(?!&(?:lt|gt|quot);)[^\\s<>\"])+", options: .caseInsensitive) else {
            return escaped
        }
        var result = escaped
        let matches = pattern.matches(in: escaped, range: NSRange(escaped.startIndex..., in: escaped))
        for match in matches.reversed() {
            guard let range = Range(match.range, in: result) else { continue }
            var address = String(result[range])
            var trailing = ""
            while let last = address.last, ".,;:!?)".contains(last) {
                trailing.insert(address.removeLast(), at: trailing.startIndex)
            }
            result.replaceSubrange(range, with: "<a href=\"\(address)\">\(address)</a>\(trailing)")
        }
        return result
    }
}
