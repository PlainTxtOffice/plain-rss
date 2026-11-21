import SwiftUI
import SwiftData

nonisolated enum ArticleOrder: String, CaseIterable {
    case newest = "Newest first", oldest = "Oldest first", title = "Title", unread = "Unread first"
}
nonisolated enum FeedOrder: String, CaseIterable {
    case title = "Title", unread = "Unread count", updated = "Recently refreshed"
}
nonisolated enum DateDisplay: String, CaseIterable {
    case automatic = "Relative, then date", relative = "Relative", date = "Date", dateTime = "Date and time"

    func text(_ date: Date?, now: Date = Date(), locale: Locale = .current, timeZone: TimeZone = .current) -> String {
        guard let date else { return "Date unavailable" }
        let age = now.timeIntervalSince(date)
        if self == .relative || (self == .automatic && age >= 0 && age < 604800) {
            if age >= 0 && age < 60 { return "Just now" }
            let formatter = RelativeDateTimeFormatter()
            formatter.locale = locale
            formatter.unitsStyle = .abbreviated
            return formatter.localizedString(for: date, relativeTo: now)
        }
        let formatter = DateFormatter()
        formatter.locale = locale; formatter.timeZone = timeZone
        formatter.dateStyle = .medium
        formatter.timeStyle = self == .dateTime ? .short : .none
        return formatter.string(from: date)
    }
}

/// Feed responses have validators stored in SwiftData, but no reusable HTTP cache.
nonisolated enum FeedTransport {
    static func configuration() -> URLSessionConfiguration {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        configuration.httpCookieStorage = nil
        configuration.urlCredentialStorage = nil
        configuration.httpShouldSetCookies = false
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        return configuration
    }
}

enum ReaderLibrary {
    static func articles(_ articles: [Article], order: ArticleOrder = .newest) -> [Article] {
        articles.sorted { a, b in
            switch order {
            case .title:
                let comparison = a.title.localizedStandardCompare(b.title)
                if comparison != .orderedSame { return comparison == .orderedAscending }
            case .unread:
                if a.isRead != b.isRead { return !a.isRead }
            default: break
            }
            // Missing publication dates always follow dated articles, in either direction.
            if a.publishedAt != b.publishedAt {
                guard let left = a.publishedAt else { return false }
                guard let right = b.publishedAt else { return true }
                return order == .oldest ? left < right : left > right
            }
            let left = (a.feed?.url ?? "") + "\u{0}" + a.guid
            let right = (b.feed?.url ?? "") + "\u{0}" + b.guid
            if left != right { return left < right }
            if a.fetchedAt != b.fetchedAt { return a.fetchedAt > b.fetchedAt }
            return String(describing: a.persistentModelID) < String(describing: b.persistentModelID)
        }
    }
    static func feeds(_ feeds: [Feed], order: FeedOrder, unreadCount: (Feed) -> Int = { $0.unreadCount }) -> [Feed] {
        let counts = order == .unread ? Dictionary(feeds.map { ($0.persistentModelID, unreadCount($0)) }, uniquingKeysWith: { first, _ in first }) : [:]
        return feeds.sorted { a, b in
            if order == .unread, let left = counts[a.persistentModelID], let right = counts[b.persistentModelID], left != right { return left > right }
            if order == .updated, a.lastFetchedAt != b.lastFetchedAt {
                return (a.lastFetchedAt ?? .distantPast) > (b.lastFetchedAt ?? .distantPast)
            }
            let comparison = a.title.localizedStandardCompare(b.title)
            return comparison == .orderedSame ? a.url < b.url : comparison == .orderedAscending
        }
    }
    static func excess(in feed: Feed, limit: Int) -> [Article] {
        Array(articles(feed.articles.filter { !$0.isStarred }).dropFirst(max(1, limit)))
    }
    static func trim(_ feed: Feed, limit: Int, context: ModelContext) {
        for article in excess(in: feed, limit: limit) { context.delete(article) }
    }
    static func remove(_ folder: FeedFolder, feeds: [Feed], context: ModelContext) {
        for feed in feeds where feed.folderID == folder.id { feed.folderID = nil }
        context.delete(folder)
    }
    static func folderName(_ input: String, excluding id: String? = nil, folders: [FeedFolder]) -> String? {
        let name = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, name.count <= 80,
              name.caseInsensitiveCompare("Unfiled") != .orderedSame,
              !folders.contains(where: { $0.id != id && $0.name.caseInsensitiveCompare(name) == .orderedSame }) else { return nil }
        return name
    }
}

struct ReaderSettingsView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Query private var feeds: [Feed]
    @AppStorage("articleRetention") private var retention = 200
    @AppStorage("articleOrder") private var articleOrder = ArticleOrder.newest.rawValue
    @AppStorage("feedOrder") private var feedOrder = FeedOrder.title.rawValue
    @AppStorage("dateDisplay") private var dateDisplay = DateDisplay.automatic.rawValue
    @AppStorage("refreshOnOpen") private var refreshOnOpen = true
    @State private var confirmRetention = false
    @State private var error: String?
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Articles per feed", selection: $retention) {
                        ForEach([50, 100, 200, 500, 1000], id: \.self) { Text("\($0)").tag($0) }
                    }
                    Button("Apply limit to existing articles…", role: .destructive) { confirmRetention = true }
                        .disabled(feeds.isEmpty)
                } header: { Text("Stored articles") } footer: {
                    Text("New feeds keep the newest \(retention) unstarred articles on refresh. Starred articles are always kept in addition to this limit. Existing feeds stay exempt until you apply the limit. Changing this setting alone deletes nothing; feeds already using retention adopt it on their next refresh. Removing a feed deletes all of its articles, including stars.")
                }
                Section {
                    Toggle("Refresh when app opens", isOn: $refreshOnOpen)
                } header: { Text("Refreshing") } footer: {
                    Text("Checks your feeds when you open the app, at most every 15 minutes. Turn off to refresh only when you ask.")
                }
                Section("Display") {
                    Picker("Article sorting", selection: $articleOrder) {
                        ForEach(ArticleOrder.allCases, id: \.rawValue) { Text($0.rawValue).tag($0.rawValue) }
                    }
                    Picker("Feed sorting", selection: $feedOrder) {
                        ForEach(FeedOrder.allCases, id: \.rawValue) { Text($0.rawValue).tag($0.rawValue) }
                    }
                    Picker("Article dates", selection: $dateDisplay) {
                        ForEach(DateDisplay.allCases, id: \.rawValue) { Text($0.rawValue).tag($0.rawValue) }
                    }
                    Text("Dates use your device’s locale and time zone. Articles without dates show “Date unavailable” and follow dated articles when sorting by date.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("Storage and cache") {
                    Text("Feed text, read status, stars, and subscriptions are stored on this device and remain available offline. This is your library, not a temporary cache.")
                    Text("Feed downloads bypass the HTTP cache and use server validators to avoid unchanged downloads. Reader images are off by default. Images you allow use temporary memory only; leaving an article or backgrounding the app revokes permission. WebKit uses a nonpersistent data store. There is no persistent image cache to clear.")
                }
            }
            .navigationTitle("Settings")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .confirmationDialog("Apply the \(retention)-article limit to every feed?", isPresented: $confirmRetention, titleVisibility: .visible) {
                Button("Delete excess unstarred articles", role: .destructive) {
                    do {
                        for feed in feeds { feed.retentionEnabled = true; ReaderLibrary.trim(feed, limit: retention, context: context) }
                        try context.save()
                    } catch { context.rollback(); self.error = error.localizedDescription }
                }
            } message: { Text("Keeps the newest articles and every star. Enables retention on future refreshes for existing feeds. This cannot be undone; older articles may no longer be available from the feed server.") }
            .alert("Couldn’t save", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
                Button("OK") { error = nil }
            } message: { Text(error ?? "") }
        }
    }
}

struct FolderManagerView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Query(sort: \FeedFolder.name) private var folders: [FeedFolder]
    @Query private var feeds: [Feed]
    @State private var name = ""
    @State private var editing: FeedFolder?
    @State private var error: String?
    var body: some View {
        NavigationStack {
            Form {
                Section(editing == nil ? "New folder" : "Rename folder") {
                    TextField("Folder name", text: $name)
                    Button(editing == nil ? "Create folder" : "Save name") { save() }
                    if editing != nil { Button("Cancel rename") { editing = nil; name = "" } }
                    if let error { Text(error).foregroundStyle(.red) }
                }
                Section {
                    ForEach(folders) { folder in
                        HStack {
                            Text(folder.name)
                            Spacer()
                            Button("Rename") { editing = folder; name = folder.name }
                            Button("Remove", role: .destructive) {
                                ReaderLibrary.remove(folder, feeds: feeds, context: context)
                                if editing?.id == folder.id { editing = nil; name = "" }
                                persist()
                            }
                        }.buttonStyle(.borderless)
                    }
                } header: { Text("Folders") } footer: {
                    Text("Removing a folder moves its feeds to Unfiled. Feeds and articles are kept. Move a feed using its context menu in the feed list. Folders are included in OPML export and recreated on import.")
                }
            }
            .navigationTitle("Folders")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }
    private func save() {
        guard let valid = ReaderLibrary.folderName(name, excluding: editing?.id, folders: folders) else {
            error = "Use a unique name of 1–80 characters, other than Unfiled."; return
        }
        if let editing { editing.name = valid } else { context.insert(FeedFolder(name: valid)) }
        if persist() { editing = nil; name = ""; error = nil }
    }
    @discardableResult private func persist() -> Bool {
        do { try context.save(); return true }
        catch { context.rollback(); self.error = error.localizedDescription; return false }
    }
}

// Merge before retention so repeated refreshes cannot duplicate GUIDs.
enum FeedArticleMerger {
    static func merge(_ parsed: [ParsedArticle], into feed: Feed, sourceURL: URL, context: ModelContext) {
        let existing = Dictionary(feed.articles.map { ($0.guid, $0) }, uniquingKeysWith: { first, _ in first })
        var seen = Set(existing.keys)
        for item in parsed {
            let guid = item.guid ?? item.link ?? "\(sourceURL.absoluteString)-\(item.title)-\(item.publishedAt?.timeIntervalSince1970 ?? 0)"
            if let article = existing[guid] {
                // Publishers correct posts after the fact; keep the stored copy current.
                if article.title != item.title { article.title = item.title }
                if article.summary != item.summary { article.summary = item.summary }
                if let html = item.contentHTML, article.contentHTML != html { article.contentHTML = html }
                if let link = item.link, article.link != link { article.link = link }
            }
            guard seen.insert(guid).inserted else { continue }
            let article = Article(guid: guid, title: item.title, link: item.link, summary: item.summary,
                                  contentHTML: item.contentHTML, publishedAt: item.publishedAt)
            article.feed = feed
            context.insert(article)
        }
    }
}
