import Foundation
import SwiftData

@Model
final class Feed {
    var url: String
    var title: String
    var lastFetchedAt: Date?
    var etag: String?
    var lastModified: String?
    var folderID: String?
    // Legacy feeds remain exempt until retention is explicitly applied.
    var retentionEnabled: Bool = false
    // True once the user renames the feed, so a refresh keeps their title.
    var hasCustomTitle: Bool = false

    @Relationship(deleteRule: .cascade, inverse: \Article.feed)
    var articles: [Article] = []

    init(url: String, title: String) {
        self.url = url
        self.title = title
        self.retentionEnabled = true
    }

    /// Applies a name the user chose. Blank names are ignored. Returns whether the feed changed.
    @discardableResult
    func rename(to name: String) -> Bool {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        title = trimmed
        hasCustomTitle = true
        return true
    }

    /// Takes the publisher's title from a refresh. A name the user chose wins over it.
    func adoptPublisherTitle(_ publisherTitle: String) {
        if !hasCustomTitle { title = publisherTitle }
    }

    var unreadCount: Int {
        articles.filter { !$0.isRead }.count
    }
}

@Model
final class Article {
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

    init(
        guid: String,
        title: String,
        link: String? = nil,
        summary: String = "",
        contentHTML: String? = nil,
        publishedAt: Date? = nil
    ) {
        self.guid = guid
        self.title = title
        self.link = link
        self.summary = summary
        self.contentHTML = contentHTML
        self.publishedAt = publishedAt
        self.fetchedAt = Date()
    }
}

@Model
final class FeedFolder {
    var id: String = UUID().uuidString
    var name: String = ""
    init(name: String) { self.name = name }
}
