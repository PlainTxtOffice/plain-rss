import Foundation

nonisolated struct FetchedFeed {
    var parsed: ParsedFeed
    var etag: String?
    var lastModified: String?
}

nonisolated enum FeedFetchResult {
    case notModified
    case fetched(FetchedFeed)
}

/// Downloads and parses feeds. Holds no UI or persistence state.
nonisolated enum FeedFetcher {
    static let maxBytes = 10_000_000

    /// Fetches a known feed, sending stored validators so unchanged feeds return `.notModified`.
    static func fetch(url: URL, etag: String?, lastModified: String?) async throws -> FeedFetchResult {
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.cachePolicy = .reloadIgnoringLocalCacheData
        if let etag { request.setValue(etag, forHTTPHeaderField: "If-None-Match") }
        if let lastModified { request.setValue(lastModified, forHTTPHeaderField: "If-Modified-Since") }

        let (data, response) = try await download(request)
        if response.statusCode == 304 { return .notModified }
        guard (200...299).contains(response.statusCode) else {
            throw FeedError.serverError(response.statusCode)
        }
        let parsed = try RSSFeedParser.parse(data: data, sourceURL: url)
        return .fetched(FetchedFeed(
            parsed: parsed,
            etag: response.value(forHTTPHeaderField: "Etag"),
            lastModified: response.value(forHTTPHeaderField: "Last-Modified")
        ))
    }

    /// Fetches a feed the user just typed in. If the address is a web page rather than a feed,
    /// follows its advertised `<link rel="alternate">` feed. Returns the URL the feed was found at.
    static func fetchNew(url: URL) async throws -> (url: URL, feed: FetchedFeed) {
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.cachePolicy = .reloadIgnoringLocalCacheData

        let (data, response) = try await download(request)
        guard (200...299).contains(response.statusCode) else {
            throw FeedError.serverError(response.statusCode)
        }
        func fetched(_ data: Data, _ response: HTTPURLResponse, _ source: URL) throws -> (URL, FetchedFeed) {
            let parsed = try RSSFeedParser.parse(data: data, sourceURL: source)
            return (source, FetchedFeed(
                parsed: parsed,
                etag: response.value(forHTTPHeaderField: "Etag"),
                lastModified: response.value(forHTTPHeaderField: "Last-Modified")
            ))
        }

        do {
            return try fetched(data, response, url)
        } catch {
            guard let discovered = FeedDiscovery.feedURL(in: data, pageURL: response.url ?? url),
                  discovered.absoluteString != url.absoluteString else {
                throw FeedError.noFeedFound
            }
            var second = URLRequest(url: discovered)
            second.timeoutInterval = 20
            second.cachePolicy = .reloadIgnoringLocalCacheData
            let (feedData, feedResponse) = try await download(second)
            guard (200...299).contains(feedResponse.statusCode) else {
                throw FeedError.serverError(feedResponse.statusCode)
            }
            return try fetched(feedData, feedResponse, discovered)
        }
    }

    private static func download(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let session = URLSession(configuration: FeedTransport.configuration())
        defer { session.invalidateAndCancel() }
        let (bytes, response) = try await session.bytes(for: request)
        guard let http = response as? HTTPURLResponse else { throw FeedError.invalidResponse }
        if http.expectedContentLength > Int64(maxBytes) { throw FeedError.tooLarge }
        var data = Data()
        if http.expectedContentLength > 0 { data.reserveCapacity(Int(http.expectedContentLength)) }
        for try await byte in bytes {
            if data.count >= maxBytes { throw FeedError.tooLarge }
            data.append(byte)
        }
        return (data, http)
    }
}

/// Finds a feed address advertised by an HTML page.
nonisolated enum FeedDiscovery {
    private static let feedTypes: Set<String> = ["application/rss+xml", "application/atom+xml", "application/rdf+xml"]

    static func feedURL(in data: Data, pageURL: URL) -> URL? {
        let html = String(decoding: data.prefix(500_000), as: UTF8.self)
        guard let tags = try? NSRegularExpression(pattern: "<link\\b[^>]*>", options: .caseInsensitive) else { return nil }
        let range = NSRange(html.startIndex..., in: html)
        for match in tags.matches(in: html, range: range) {
            guard let tagRange = Range(match.range, in: html) else { continue }
            let attributes = attributes(of: String(html[tagRange]))
            guard let rel = attributes["rel"], rel.lowercased().split(separator: " ").contains("alternate"),
                  let type = attributes["type"], feedTypes.contains(type.lowercased()),
                  let href = attributes["href"],
                  let url = ReaderURL.allowed(href, relativeTo: pageURL) else { continue }
            return url
        }
        return nil
    }

    private static func attributes(of tag: String) -> [String: String] {
        guard let pairs = try? NSRegularExpression(pattern: "([a-zA-Z-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')") else { return [:] }
        var result: [String: String] = [:]
        for match in pairs.matches(in: tag, range: NSRange(tag.startIndex..., in: tag)) {
            guard let name = Range(match.range(at: 1), in: tag) else { continue }
            let value = [2, 3].compactMap { Range(match.range(at: $0), in: tag) }.first.map { String(tag[$0]) } ?? ""
            let key = tag[name].lowercased()
            if result[key] == nil { result[key] = value.htmlDecoded }
        }
        return result
    }
}
