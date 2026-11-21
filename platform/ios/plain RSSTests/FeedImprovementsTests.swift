import Foundation
import Testing
@testable import Open_RSS

struct FeedImprovementsTests {
    private func parse(_ xml: String) throws -> ParsedFeed {
        try RSSFeedParser.parse(data: Data(xml.utf8), sourceURL: URL(string: "https://example.com/feed")!)
    }

    @Test func readerDecodesCommonNamedEntities() {
        let input = ReaderInput(title: "T", metadata: "", body: "<p>Wait&hellip; it&rsquo;s &mdash; fine &copy;</p>", baseURL: nil, allowsImages: false)
        let html = ReaderSanitizer.document(input).html
        #expect(html.contains("Wait\u{2026} it\u{2019}s \u{2014} fine \u{00A9}"))
        #expect(!html.contains("&amp;hellip;"))
    }

    @Test func atomPublishedDateBeatsUpdated() throws {
        let feed = try parse("""
        <feed xmlns="http://www.w3.org/2005/Atom"><title>F</title>
          <entry><title>A</title><id>1</id>
            <published>2024-01-01T00:00:00Z</published>
            <updated>2024-06-01T00:00:00Z</updated></entry>
          <entry><title>B</title><id>2</id>
            <updated>2024-06-01T00:00:00Z</updated>
            <published>2024-01-01T00:00:00Z</published></entry>
          <entry><title>C</title><id>3</id><updated>2024-06-01T00:00:00Z</updated></entry>
        </feed>
        """)
        let jan1 = ISO8601DateFormatter().date(from: "2024-01-01T00:00:00Z")
        let jun1 = ISO8601DateFormatter().date(from: "2024-06-01T00:00:00Z")
        #expect(feed.articles[0].publishedAt == jan1)
        #expect(feed.articles[1].publishedAt == jan1)
        #expect(feed.articles[2].publishedAt == jun1)
    }

    @Test func atomLinkPrefersAlternate() throws {
        let feed = try parse("""
        <feed xmlns="http://www.w3.org/2005/Atom"><title>F</title>
          <entry><title>A</title><id>1</id>
            <link rel="enclosure" href="https://example.com/file.mp3"/>
            <link rel="replies" href="https://example.com/comments"/>
            <link rel="alternate" href="https://example.com/post"/>
          </entry>
          <entry><title>B</title><id>2</id><link href="https://example.com/b"/></entry>
        </feed>
        """)
        #expect(feed.articles[0].link == "https://example.com/post")
        #expect(feed.articles[1].link == "https://example.com/b")
    }

    @Test func rssImageTitleDoesNotReplaceFeedTitle() throws {
        let feed = try parse("""
        <rss version="2.0"><channel><title>Real Title</title>
          <image><title>Logo Title</title><url>https://example.com/l.png</url></image>
          <item><title>Post</title></item>
        </channel></rss>
        """)
        #expect(feed.title == "Real Title")
        #expect(feed.articles.count == 1)
    }

    @Test func parsesTimezoneNamesAndMissingSeconds() {
        let est = RSSDateParser.parse("Tue, 10 Jun 2025 08:00:00 EST")
        let utc = RSSDateParser.parse("Tue, 10 Jun 2025 13:00:00 +0000")
        #expect(est != nil && est == utc)
        #expect(RSSDateParser.parse("Tue, 10 Jun 2025 08:00 GMT") != nil)
        #expect(RSSDateParser.parse("10 Jun 2025 08:00:00 PDT") != nil)
    }

    @Test func htmlPageIsNotAFeed() {
        #expect(throws: (any Error).self) {
            try parse("<html><head><title>Site</title></head><body></body></html>")
        }
    }

    @Test func discoversFeedLinkInHTML() throws {
        let html = """
        <html><head>
        <link rel="stylesheet" href="/s.css">
        <link rel='alternate' type='application/atom+xml' title="Feed" href="/blog/atom.xml">
        </head></html>
        """
        let found = FeedDiscovery.feedURL(in: Data(html.utf8), pageURL: URL(string: "https://example.com/blog/")!)
        #expect(found?.absoluteString == "https://example.com/blog/atom.xml")
        #expect(FeedDiscovery.feedURL(in: Data("<html></html>".utf8), pageURL: URL(string: "https://example.com")!) == nil)
    }

    @Test func opmlRoundTripsFolders() throws {
        let feeds = [
            OPMLFeed(title: "Loose", url: "https://a.example/feed"),
            OPMLFeed(title: "One", url: "https://b.example/feed", folder: "News & Views"),
            OPMLFeed(title: "Two", url: "https://c.example/feed", folder: "News & Views"),
            OPMLFeed(title: "Three", url: "https://d.example/feed", folder: "Tech"),
        ]
        let parsed = try OPMLParser.parse(data: Data(OPMLWriter.write(feeds).utf8))
        #expect(Set(parsed.map(\.url)) == Set(feeds.map(\.url)))
        #expect(parsed.first { $0.title == "Loose" }?.folder == nil)
        #expect(parsed.first { $0.title == "Two" }?.folder == "News & Views")
        #expect(parsed.first { $0.title == "Three" }?.folder == "Tech")
    }

    @Test func feedSortByUnreadUsesSuppliedCounts() {
        let a = Feed(url: "https://a.example", title: "A")
        let b = Feed(url: "https://b.example", title: "B")
        let counts = [a.url: 1, b.url: 5]
        let sorted = ReaderLibrary.feeds([a, b], order: .unread) { counts[$0.url] ?? 0 }
        #expect(sorted.map(\.title) == ["B", "A"])
    }
}
