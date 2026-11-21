import Foundation
import SwiftData
import Testing
@testable import Open_RSS

/// Behavior the iOS app shares with the Android app: feedback drafts, kept feed
/// renames, and reader text after a '<' that does not start a tag.
struct AndroidParityTests {
    @Test func deviceSummaryHasThreeLines() {
        let summary = Feedback.deviceSummary(
            appName: "Plain RSS", version: "1.0", build: "1",
            systemName: "iOS", systemVersion: "26.4", model: "iPhone17,1"
        )
        #expect(summary == "Plain RSS 1.0 (1)\niOS 26.4\niPhone17,1")
    }

    @Test func emailBodyLeavesRoomAboveTheSummary() {
        #expect(Feedback.emailBody(deviceSummary: "S") == "\n\nS")
    }

    @Test func emailDraftCarriesOnlySubjectAndSummary() throws {
        let url = try #require(Feedback.emailURL(kind: .bugReport, deviceSummary: "Plain RSS 1.0 (1)\niOS 26.4\niPhone17,1"))
        let components = try #require(URLComponents(url: url, resolvingAgainstBaseURL: false))
        #expect(components.scheme == "mailto")
        #expect(components.path == "support@plaintxtoffice.com")
        let items = Dictionary(uniqueKeysWithValues: (components.queryItems ?? []).map { ($0.name, $0.value ?? "") })
        #expect(items.count == 2)
        #expect(items["subject"] == "Plain RSS bug report (iOS)")
        #expect(items["body"] == "\n\nPlain RSS 1.0 (1)\niOS 26.4\niPhone17,1")
        #expect(Feedback.subject(for: .featureRequest) == "Plain RSS feature request (iOS)")
    }

    @MainActor @Test func renamedFeedKeepsItsTitleOnRefresh() throws {
        let container = try ModelContainer(
            for: Feed.self, Article.self, FeedFolder.self,
            configurations: ModelConfiguration(isStoredInMemoryOnly: true)
        )
        let feed = Feed(url: "https://example.com/feed", title: "Publisher")
        container.mainContext.insert(feed)

        feed.adoptPublisherTitle("Publisher Renamed Itself")
        #expect(feed.title == "Publisher Renamed Itself")
        #expect(!feed.hasCustomTitle)

        #expect(!feed.rename(to: "   "))
        #expect(!feed.hasCustomTitle)

        #expect(feed.rename(to: "  My Name "))
        #expect(feed.title == "My Name")
        feed.adoptPublisherTitle("Publisher Again")
        #expect(feed.title == "My Name")
    }

    @Test func readerKeepsTextAfterALessThanThatIsNotATag() {
        func html(_ body: String) -> String {
            ReaderSanitizer.document(ReaderInput(title: "T", metadata: "", body: body, baseURL: nil, allowsImages: false)).html
        }
        #expect(html("<p>a < b and c > d</p>").contains("a &lt; b and c &gt; d"))
        #expect(html("<p>I <3 feeds</p>").contains("I &lt;3 feeds"))
        #expect(html("<p>end <</p>").contains("end &lt;"))
        // Real markup is still parsed, and blocked elements stay blocked.
        #expect(html("<p>one</p><script>alert(1 < 2)</script><p>two</p>").contains("alert") == false)
        #expect(html("<p>x</p><!-- hidden -->").contains("hidden") == false)
    }
}
