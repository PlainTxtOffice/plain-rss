import Foundation
import Testing
@testable import Open_RSS

struct YouTubeTests {
    private func feedURL(_ address: String) -> String? {
        YouTubeAddress.feedURL(for: URL(string: address)!)?.absoluteString
    }

    @Test func channelAddressMapsToItsFeed() {
        #expect(feedURL("https://www.youtube.com/channel/UCE_M8A5yxnLfW0KghEeajjw")
            == "https://www.youtube.com/feeds/videos.xml?channel_id=UCE_M8A5yxnLfW0KghEeajjw")
        #expect(feedURL("https://m.youtube.com/channel/UCE_M8A5yxnLfW0KghEeajjw/videos")
            == "https://www.youtube.com/feeds/videos.xml?channel_id=UCE_M8A5yxnLfW0KghEeajjw")
    }

    @Test func playlistAddressMapsToItsFeed() {
        #expect(feedURL("https://www.youtube.com/playlist?list=PL1234_abc-XYZ")
            == "https://www.youtube.com/feeds/videos.xml?playlist_id=PL1234_abc-XYZ")
    }

    @Test func otherAddressesAreLeftForDiscovery() {
        #expect(feedURL("https://www.youtube.com/@Apple") == nil)
        #expect(feedURL("https://www.youtube.com/feeds/videos.xml?channel_id=UCE_M8A5yxnLfW0KghEeajjw") == nil)
        #expect(feedURL("https://www.youtube.com/channel/not-a-channel-id") == nil)
        #expect(feedURL("https://www.youtube.com/playlist?list=bad%20id") == nil)
        #expect(feedURL("https://example.com/channel/UCE_M8A5yxnLfW0KghEeajjw") == nil)
    }

    @Test func recognisesVideoLinks() {
        #expect(YouTubeAddress.isVideo(URL(string: "https://www.youtube.com/watch?v=Sp54mxnWjUM")!))
        #expect(YouTubeAddress.isVideo(URL(string: "https://www.youtube.com/shorts/Hz64WOtsT4Y")!))
        #expect(YouTubeAddress.isVideo(URL(string: "https://youtu.be/Sp54mxnWjUM")!))
        #expect(!YouTubeAddress.isVideo(URL(string: "https://www.youtube.com/@Apple")!))
        #expect(!YouTubeAddress.isVideo(URL(string: "https://example.com/watch?v=1")!))
    }

    @Test func discoversFeedLinkLateInALargePage() {
        let padding = String(repeating: "<script>var x = 1;</script>\n", count: 35_000)
        let html = "<html><head>" + padding
            + "<link rel=\"alternate\" type=\"application/rss+xml\" title=\"RSS\" "
            + "href=\"https://www.youtube.com/feeds/videos.xml?channel_id=UCE_M8A5yxnLfW0KghEeajjw\">"
            + "</head></html>"
        #expect(html.utf8.count > 900_000)
        let found = FeedDiscovery.feedURL(in: Data(html.utf8), pageURL: URL(string: "https://www.youtube.com/@Apple")!)
        #expect(found?.absoluteString == "https://www.youtube.com/feeds/videos.xml?channel_id=UCE_M8A5yxnLfW0KghEeajjw")
    }

    @Test func parsesVideoEntryFromMediaGroup() throws {
        let xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom">
         <title>Channel</title>
         <author><name>Channel</name></author>
         <entry>
          <id>yt:video:abc123</id>
          <title>A video</title>
          <link rel="alternate" href="https://www.youtube.com/watch?v=abc123"/>
          <author><name>Channel</name></author>
          <published>2026-11-12T08:00:35+00:00</published>
          <updated>2026-11-15T23:14:50+00:00</updated>
          <media:group>
           <media:title>Media title must not replace the title</media:title>
           <media:thumbnail url="https://i1.ytimg.com/vi/abc123/hqdefault.jpg" width="480" height="360"/>
           <media:description>First line &lt;b&gt; &amp; more
        See https://example.com/a?b=1&amp;c=2.

        Second paragraph</media:description>
          </media:group>
         </entry>
        </feed>
        """
        let feed = try RSSFeedParser.parse(data: Data(xml.utf8), sourceURL: URL(string: "https://www.youtube.com/feeds/videos.xml")!)
        #expect(feed.title == "Channel")
        let article = try #require(feed.articles.first)
        #expect(article.title == "A video")
        #expect(article.link == "https://www.youtube.com/watch?v=abc123")
        #expect(article.summary == "First line <b> & more See https://example.com/a?b=1&c=2. Second paragraph")
        let html = try #require(article.contentHTML)
        #expect(html.hasPrefix("<p><img src=\"https://i1.ytimg.com/vi/abc123/hqdefault.jpg\" alt=\"Video thumbnail\"></p>"))
        #expect(html.contains("<p>First line &lt;b&gt; &amp; more<br>See "))
        #expect(html.contains("<a href=\"https://example.com/a?b=1&amp;c=2\">https://example.com/a?b=1&amp;c=2</a>.</p>"))
        #expect(html.hasSuffix("<p>Second paragraph</p>"))

        // The reader keeps the link and withholds the thumbnail until images are allowed.
        let document = ReaderSanitizer.document(ReaderInput(title: "A video", metadata: "", body: html, baseURL: nil, allowsImages: false))
        #expect(document.images.isEmpty)
        #expect(document.links.values.map(\.absoluteString) == ["https://example.com/a?b=1&c=2"])
    }

    @Test func mediaThumbnailDoesNotReplaceAnEntrysOwnContent() throws {
        let xml = """
        <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel><title>Blog</title>
        <item><title>Post</title><description>&lt;p&gt;Own text&lt;/p&gt;</description>
        <media:thumbnail url="https://example.com/t.jpg"/><media:description>Other text</media:description></item>
        </channel></rss>
        """
        let feed = try RSSFeedParser.parse(data: Data(xml.utf8), sourceURL: URL(string: "https://example.com/feed")!)
        let article = try #require(feed.articles.first)
        #expect(article.contentHTML == "<p>Own text</p>")
        #expect(article.summary == "Own text")
    }
}
