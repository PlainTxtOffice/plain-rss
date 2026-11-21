import Foundation
import SwiftData
import Testing
@testable import Open_RSS

@MainActor
struct LibrarySettingsTests {
    private func container() throws -> ModelContainer {
        try ModelContainer(for: Feed.self, Article.self, FeedFolder.self,
                           configurations: ModelConfiguration(isStoredInMemoryOnly: true))
    }
    @Test func retentionAndRepeatedMerge() throws {
        let storage = try container(), context = storage.mainContext
        let feed = Feed(url: "https://fixture.invalid/feed", title: "Fixture")
        context.insert(feed)
        let url = URL(string: feed.url)!
        let items = (0..<10).map { index in
            ParsedArticle(title: "Article \(index)", publishedAt: Date(timeIntervalSince1970: Double(index)), guid: "\(index)")
        }
        FeedArticleMerger.merge(items + items, into: feed, sourceURL: url, context: context)
        try context.save()
        #expect(feed.articles.count == 10)
        feed.articles.first { $0.guid == "0" }!.isStarred = true
        ReaderLibrary.trim(feed, limit: 3, context: context)
        try context.save()
        #expect(Set(feed.articles.map(\.guid)) == ["0", "7", "8", "9"])
        for _ in 0..<3 {
            FeedArticleMerger.merge(items, into: feed, sourceURL: url, context: context)
            ReaderLibrary.trim(feed, limit: 3, context: context)
            try context.save()
            #expect(Set(feed.articles.map(\.guid)) == ["0", "7", "8", "9"])
        }
        #expect(ReaderLibrary.excess(in: feed, limit: 3).isEmpty)
        #expect(ReaderLibrary.excess(in: feed, limit: 1).count == 2)
        #expect(feed.articles.first { $0.guid == "0" }!.isStarred)
        let undated = Feed(url: "https://fixture.invalid/undated", title: "Undated")
        context.insert(undated)
        let missingDates = (0..<10).map { ParsedArticle(title: "Undated", guid: "\($0)") }
        for _ in 0..<3 {
            FeedArticleMerger.merge(missingDates, into: undated, sourceURL: url, context: context)
            ReaderLibrary.trim(undated, limit: 3, context: context)
            try context.save()
            #expect(Set(undated.articles.map(\.guid)) == ["0", "1", "2"])
        }
    }
    @Test func foldersNeverDeleteFeedsOrArticles() throws {
        let storage = try container(), context = storage.mainContext
        let feed = Feed(url: "https://fixture.invalid", title: "Feed")
        let article = Article(guid: "a", title: "Article")
        article.feed = feed
        let first = FeedFolder(name: "First"), second = FeedFolder(name: "Second")
        context.insert(feed); context.insert(article); context.insert(first); context.insert(second)
        feed.folderID = first.id
        try context.save()
        first.name = "Renamed"
        #expect(feed.folderID == first.id)
        feed.folderID = second.id
        ReaderLibrary.remove(first, feeds: [feed], context: context)
        try context.save()
        #expect(feed.folderID == second.id)
        ReaderLibrary.remove(second, feeds: [feed], context: context)
        try context.save()
        #expect(feed.folderID == nil)
        #expect(try context.fetchCount(FetchDescriptor<Feed>()) == 1)
        #expect(try context.fetchCount(FetchDescriptor<Article>()) == 1)
        #expect(ReaderLibrary.folderName("  Work  ", folders: []) == "Work")
        #expect(ReaderLibrary.folderName("unFILEd", folders: []) == nil)
        #expect(ReaderLibrary.folderName("", folders: []) == nil)
        #expect(ReaderLibrary.folderName("FIRST", folders: [FeedFolder(name: "First")]) == nil)
    }
    @Test func stableOrderingAndMissingDates() {
        let a = Article(guid: "a", title: "Same", publishedAt: Date(timeIntervalSince1970: 10))
        let b = Article(guid: "b", title: "Same", publishedAt: a.publishedAt)
        let missing = Article(guid: "c", title: "Same")
        a.fetchedAt = .distantPast; b.fetchedAt = a.fetchedAt
        #expect(ReaderLibrary.articles([b, missing, a]).map(\.guid) == ["a", "b", "c"])
        #expect(ReaderLibrary.articles([missing, b, a], order: .oldest).map(\.guid) == ["a", "b", "c"])
        a.isRead = true
        #expect(ReaderLibrary.articles([a, b], order: .unread).first === b)
        let f1 = Feed(url: "https://b.invalid", title: "Same")
        let f2 = Feed(url: "https://a.invalid", title: "Same")
        #expect(ReaderLibrary.feeds([f1, f2], order: .title).first === f2)
    }
    @Test func datesRespectTimezoneAndFuture() {
        let locale = Locale(identifier: "en_US"), utc = TimeZone(secondsFromGMT: 0)!
        let date = Date(timeIntervalSince1970: 0)
        #expect(DateDisplay.date.text(nil) == "Date unavailable")
        #expect(DateDisplay.date.text(date, locale: locale, timeZone: utc).contains("Jan 1, 1970"))
        #expect(DateDisplay.date.text(date, locale: locale, timeZone: TimeZone(secondsFromGMT: -3600)!).contains("Dec 31, 1969"))
        #expect(DateDisplay.automatic.text(date, now: date.addingTimeInterval(30)) == "Just now")
        #expect(DateDisplay.relative.text(date.addingTimeInterval(3600), now: date, locale: locale).contains("in"))
        #expect(DateDisplay.automatic.text(date.addingTimeInterval(3600), now: date, locale: locale, timeZone: utc) != "Just now")
    }
    @Test func feedCacheIsEphemeral() {
        let config = FeedTransport.configuration()
        #expect(config.urlCache == nil)
        #expect(config.httpCookieStorage == nil)
        #expect(config.urlCredentialStorage == nil)
        #expect(!config.httpShouldSetCookies)
        #expect(config.requestCachePolicy == .reloadIgnoringLocalCacheData)
    }
    @Test func settingsPersistInIsolatedDefaults() {
        let suite = "plainRSS-tests-\(UUID().uuidString)"
        let first = UserDefaults(suiteName: suite)!
        defer { first.removePersistentDomain(forName: suite) }
        first.set(500, forKey: "articleRetention")
        first.set(DateDisplay.dateTime.rawValue, forKey: "dateDisplay")
        first.set(ArticleOrder.oldest.rawValue, forKey: "articleOrder")
        first.set(FeedOrder.unread.rawValue, forKey: "feedOrder")
        let second = UserDefaults(suiteName: suite)!
        #expect(second.integer(forKey: "articleRetention") == 500)
        #expect(second.string(forKey: "dateDisplay") == DateDisplay.dateTime.rawValue)
        #expect(second.string(forKey: "articleOrder") == ArticleOrder.oldest.rawValue)
        #expect(second.string(forKey: "feedOrder") == FeedOrder.unread.rawValue)
    }
    @Test func diskReopenPreservesLibrary() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let configuration = ModelConfiguration(url: directory.appendingPathComponent("fixture.store"))
        do {
            let storage = try ModelContainer(for: Feed.self, Article.self, FeedFolder.self, configurations: configuration)
            let feed = Feed(url: "https://fixture.invalid", title: "Legacy")
            feed.retentionEnabled = false
            let article = Article(guid: "keep", title: "Saved"); article.isStarred = true; article.feed = feed
            storage.mainContext.insert(feed); storage.mainContext.insert(article)
            try storage.mainContext.save()
        }
        let reopened = try ModelContainer(for: Feed.self, Article.self, FeedFolder.self, configurations: configuration)
        let feed = try reopened.mainContext.fetch(FetchDescriptor<Feed>()).first!
        #expect(!feed.retentionEnabled)
        #expect(feed.folderID == nil)
        #expect(feed.articles.first?.isStarred == true)
    }
}

// Mirrors the pre-settings schema; the store is entirely synthetic and temporary.
private enum LegacyLibrary {
    @Model final class Feed {
        var url: String
        var title: String
        var lastFetchedAt: Date?
        var etag: String?
        var lastModified: String?
        @Relationship(deleteRule: .cascade, inverse: \Article.feed)
        var articles: [Article] = []
        init(url: String, title: String) { self.url = url; self.title = title }
    }
    @Model final class Article {
        var guid: String
        var title: String
        var link: String?
        var summary: String
        var contentHTML: String?
        var publishedAt: Date?
        var isRead: Bool = false
        var isStarred: Bool = false
        var fetchedAt: Date
        var feed: Feed?
        init(guid: String, title: String) {
            self.guid = guid; self.title = title; self.summary = ""; self.fetchedAt = Date()
        }
    }
}

extension LibrarySettingsTests {
    @Test func legacySchemaMigrationPreservesArticlesAndExemption() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let configuration = ModelConfiguration(url: directory.appendingPathComponent("legacy.store"))
        do {
            let old = try ModelContainer(for: LegacyLibrary.Feed.self, LegacyLibrary.Article.self, configurations: configuration)
            let feed = LegacyLibrary.Feed(url: "https://fixture.invalid/legacy", title: "Legacy Feed")
            let article = LegacyLibrary.Article(guid: "legacy", title: "Legacy Article")
            article.isStarred = true; article.isRead = true; article.feed = feed
            old.mainContext.insert(feed); old.mainContext.insert(article)
            try old.mainContext.save()
        }
        let migrated = try ModelContainer(for: Feed.self, Article.self, FeedFolder.self, configurations: configuration)
        let feed = try migrated.mainContext.fetch(FetchDescriptor<Feed>()).first!
        #expect(feed.title == "Legacy Feed")
        #expect(feed.folderID == nil)
        #expect(!feed.retentionEnabled)
        #expect(feed.articles.count == 1)
        #expect(feed.articles.first?.isStarred == true)
        #expect(feed.articles.first?.isRead == true)
    }
}
