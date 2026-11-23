import Foundation
import Testing
@testable import Open_RSS

/// Serves canned responses in several chunks, with no Content-Length, so the size limit
/// has to be enforced while the body arrives.
private final class ChunkedStubProtocol: URLProtocol, @unchecked Sendable {
    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "stub.test" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func stopLoading() {}
    override func startLoading() {
        let url = request.url!
        let status = url.path == "/missing" ? 404 : 200
        let response = HTTPURLResponse(url: url, statusCode: status, httpVersion: "HTTP/1.1", headerFields: ["Etag": "\"v1\""])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        let chunks = url.path == "/big" ? 3 : 1
        for _ in 0..<chunks {
            client?.urlProtocol(self, didLoad: Data(repeating: 0x61, count: 400))
        }
        client?.urlProtocolDidFinishLoading(self)
    }
}

struct MediaFeedsTests {
    private func parse(_ xml: String) throws -> ParsedFeed {
        try RSSFeedParser.parse(data: Data(xml.utf8), sourceURL: URL(string: "https://example.com/feed")!)
    }

    private var stubbed: URLSessionConfiguration {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [ChunkedStubProtocol.self]
        return configuration
    }

    @Test func downloadCollectsEveryChunk() async throws {
        let request = URLRequest(url: URL(string: "https://stub.test/big")!)
        let (data, response) = try await FeedFetcher.download(request, configuration: stubbed)
        #expect(data.count == 1_200)
        #expect(response.statusCode == 200)
        #expect(response.value(forHTTPHeaderField: "Etag") == "\"v1\"")
    }

    @Test func downloadStopsPastTheLimit() async {
        let request = URLRequest(url: URL(string: "https://stub.test/big")!)
        do {
            _ = try await FeedFetcher.download(request, configuration: stubbed, limit: 1_000)
            Issue.record("Expected the download to be rejected as too large.")
        } catch FeedError.tooLarge {
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test func downloadReportsStatusWithoutThrowing() async throws {
        let request = URLRequest(url: URL(string: "https://stub.test/missing")!)
        let (_, response) = try await FeedFetcher.download(request, configuration: stubbed)
        #expect(response.statusCode == 404)
    }

    @Test func podcastEnclosureBecomesAPlayLink() throws {
        let feed = try parse("""
        <rss version="2.0"><channel><title>Show</title>
        <item><title>Episode 1</title><link>https://example.com/ep1</link>
        <description>&lt;p&gt;Show notes&lt;/p&gt;</description>
        <enclosure url="https://cdn.example.com/ep1.mp3?a=1&amp;b=2" length="123" type="audio/mpeg"/></item>
        </channel></rss>
        """)
        let article = try #require(feed.articles.first)
        #expect(article.link == "https://example.com/ep1")
        #expect(article.summary == "Show notes")
        #expect(article.contentHTML
            == "<p>Show notes</p><p><a href=\"https://cdn.example.com/ep1.mp3?a=1&amp;b=2\">Play audio</a></p>")

        let document = ReaderSanitizer.document(ReaderInput(title: "Episode 1", metadata: "", body: article.contentHTML ?? "", baseURL: nil, allowsImages: false))
        #expect(document.links.values.map(\.absoluteString) == ["https://cdn.example.com/ep1.mp3?a=1&b=2"])
    }

    @Test func enclosureAloneStillGivesAnEntryContent() throws {
        let feed = try parse("""
        <rss version="2.0"><channel><title>Show</title>
        <item><title>Episode 2</title><enclosure url="https://cdn.example.com/ep2.mp4" type="video/mp4"/></item>
        </channel></rss>
        """)
        let article = try #require(feed.articles.first)
        #expect(article.contentHTML == "<p><a href=\"https://cdn.example.com/ep2.mp4\">Play video</a></p>")
        #expect(article.link == nil)
    }

    @Test func atomEnclosureLinkIsNotTheArticleLink() throws {
        let feed = try parse("""
        <feed xmlns="http://www.w3.org/2005/Atom"><title>Show</title>
        <entry><title>Episode 3</title><id>e3</id>
        <link rel="enclosure" type="audio/mpeg" href="https://cdn.example.com/ep3.mp3"/>
        <link rel="alternate" href="https://example.com/ep3"/>
        <summary>Notes</summary></entry>
        </feed>
        """)
        let article = try #require(feed.articles.first)
        #expect(article.link == "https://example.com/ep3")
        #expect(article.contentHTML == "Notes<p><a href=\"https://cdn.example.com/ep3.mp3\">Play audio</a></p>")
    }
}
