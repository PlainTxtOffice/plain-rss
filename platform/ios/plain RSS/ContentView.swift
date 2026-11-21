import SwiftUI
import SwiftData
import UniformTypeIdentifiers

enum FeedFilter: Hashable {
    case all
    case unread
    case starred
    case feed(String)
    case folder(String)
    case unfiled
}

struct ContentView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Query(sort: \Feed.title) private var feeds: [Feed]
    @Query(sort: \FeedFolder.name) private var folders: [FeedFolder]
    @Query(filter: #Predicate<Article> { $0.isRead == false }) private var unreadArticles: [Article]
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("refreshOnOpen") private var refreshOnOpen = true
    @AppStorage("lastAutoRefresh") private var lastAutoRefresh = 0.0
    @AppStorage("articleRetention") private var retention = 200
    @AppStorage("articleOrder") private var articleOrder = ArticleOrder.newest.rawValue
    @AppStorage("feedOrder") private var feedOrder = FeedOrder.title.rawValue
    @State private var showingSettings = false
    @State private var showingFolders = false
    @State private var feedToDelete: Feed?
    @State private var feedToRename: Feed?
    @State private var renameText = ""
    @State private var searchText = ""
    @State private var confirmMarkAllRead = false

    private var unreadCounts: [PersistentIdentifier: Int] {
        unreadArticles.reduce(into: [:]) { counts, article in
            if let id = article.feed?.persistentModelID { counts[id, default: 0] += 1 }
        }
    }
    private var sortedFeeds: [Feed] {
        let counts = unreadCounts
        return ReaderLibrary.feeds(feeds, order: FeedOrder(rawValue: feedOrder) ?? .title) { counts[$0.persistentModelID] ?? 0 }
    }
    private func isUnfiled(_ feed: Feed) -> Bool { !folders.contains { $0.id == feed.folderID } }
    // Nil on iPhone so launch shows the feed list; a selection would push straight to articles.
    @State private var feedFilter: FeedFilter?
    @State private var selectedArticle: Article?
    @State private var preferredColumn: NavigationSplitViewColumn = .sidebar
    @State private var showingPrivacy = false
    @State private var showingFeedback = false
    @State private var isAddingFeed = false
    @State private var isImportingOPML = false
    @State private var exportDocument: OPMLDocument?
    @State private var importSummary: String?
    @State private var isRefreshing = false
    @State private var errorMessage: String?

    private var visibleArticles: [Article] {
        let articles: [Article]
        switch feedFilter {
        case .all, .none:
            articles = feeds.flatMap(\.articles)
        case .unread:
            articles = unreadArticles
        case .starred:
            articles = feeds.flatMap(\.articles).filter(\.isStarred)
        case .folder(let id):
            articles = feeds.filter { $0.folderID == id }.flatMap(\.articles)
        case .unfiled:
            articles = feeds.filter(isUnfiled).flatMap(\.articles)
        case .feed(let url):
            articles = feeds.first { $0.url == url }?.articles ?? []
        }
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        let matching = query.isEmpty ? articles : articles.filter {
            $0.title.localizedStandardContains(query) || $0.summary.localizedStandardContains(query)
        }
        return ReaderLibrary.articles(matching, order: ArticleOrder(rawValue: articleOrder) ?? .newest)
    }

    private var navigationTitle: String {
        switch feedFilter {
        case .all, .none: return "All Articles"
        case .unread: return "Unread"
        case .starred: return "Starred"
        case .folder(let id): return folders.first { $0.id == id }?.name ?? "Folder"
        case .unfiled: return "Unfiled"
        case .feed(let url): return feeds.first { $0.url == url }?.title ?? "Articles"
        }
    }

    private var showsFeedTitleInRows: Bool {
        if case .feed = feedFilter { return false }
        return true
    }

    var body: some View {
        NavigationSplitView(preferredCompactColumn: $preferredColumn) {
            List(selection: $feedFilter) {
                Section("Feeds") {
                    Label("All Articles", systemImage: "tray.full")
                        .tag(FeedFilter.all)

                    HStack {
                        Label("Unread", systemImage: "circle.inset.filled")
                        Spacer()
                        if !unreadArticles.isEmpty {
                            Text("\(unreadArticles.count)").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .tag(FeedFilter.unread)

                    Label("Starred", systemImage: "star.fill")
                        .tag(FeedFilter.starred)

                    Label("Unfiled", systemImage: "tray").tag(FeedFilter.unfiled)
                    ForEach(sortedFeeds.filter(isUnfiled)) { feed in feedRow(feed) }
                    ForEach(folders) { folder in
                        Label(folder.name, systemImage: "folder").tag(FeedFilter.folder(folder.id))
                        ForEach(sortedFeeds.filter { $0.folderID == folder.id }) { feed in
                            feedRow(feed).padding(.leading, 16)
                        }
                    }

                    if feeds.isEmpty {
                        Button {
                            isAddingFeed = true
                        } label: {
                            Label("Add Your First Feed", systemImage: "plus.circle.fill")
                        }

                        Button {
                            isImportingOPML = true
                        } label: {
                            Label("Import from Another App", systemImage: "square.and.arrow.down")
                        }
                    }
                }
            }
            .navigationTitle("Plain RSS")
            .alert("Import Complete", isPresented: Binding(
                get: { importSummary != nil },
                set: { if !$0 { importSummary = nil } }
            )) {
                Button("OK", role: .cancel) { importSummary = nil }
            } message: {
                Text(importSummary ?? "")
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Folders", systemImage: "folder.badge.gearshape") { showingFolders = true }
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button {
                        Task { await refreshAllFeeds() }
                    } label: {
                        if isRefreshing {
                            ProgressView()
                        } else {
                            Label("Refresh", systemImage: "arrow.clockwise")
                        }
                    }
                    .disabled(feeds.isEmpty || isRefreshing)

                    Button {
                        isAddingFeed = true
                    } label: {
                        Label("Add Feed", systemImage: "plus")
                    }

                    Menu {
                        Button {
                            isImportingOPML = true
                        } label: {
                            Label("Import OPML…", systemImage: "square.and.arrow.down")
                        }
                        .disabled(isRefreshing)

                        Button("Settings", systemImage: "gearshape") { showingSettings = true }
                            .accessibilityIdentifier("readerSettingsButton")

                        Button("Privacy", systemImage: "hand.raised") { showingPrivacy = true }
                            .accessibilityIdentifier("privacyPolicyButton")

                        Button("Send Feedback", systemImage: "envelope") { showingFeedback = true }
                            .accessibilityIdentifier("sendFeedbackButton")

                        Button(action: exportFeeds) {
                            Label("Export OPML…", systemImage: "square.and.arrow.up")
                        }
                        .disabled(feeds.isEmpty)
                    } label: {
                        Label("More", systemImage: "ellipsis")
                    }
                }
            }
        } content: {
            articleList
                } detail: {
            if let selectedArticle {
                ArticleDetailView(article: selectedArticle)
            } else {
                ContentUnavailableView(
                    "Select an Article",
                    systemImage: "doc.text.magnifyingglass"
                )
            }
        }
        .alert("RSS Error", isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) { errorMessage = nil }
        } message: {
            Text(errorMessage ?? "Unknown error")
        }
        .onAppear {
            if horizontalSizeClass == .regular, feedFilter == nil {
                feedFilter = .all
            }
        }
        .task { await refreshIfStale() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await refreshIfStale() } }
        }
        .alert("Rename Feed", isPresented: Binding(get: { feedToRename != nil }, set: { if !$0 { feedToRename = nil } })) {
            TextField("Feed name", text: $renameText)
            Button("Save") { renameFeed() }
            Button("Cancel", role: .cancel) { feedToRename = nil }
        }
        .confirmationDialog("Mark all shown articles as read?", isPresented: $confirmMarkAllRead, titleVisibility: .visible) {
            Button("Mark as Read") { markVisibleAsRead() }
        }
        .sheet(isPresented: $showingSettings, onDismiss: { selectedArticle = nil }) { ReaderSettingsView() }
        .sheet(isPresented: $showingFolders, onDismiss: { feedFilter = horizontalSizeClass == .regular ? .all : nil }) { FolderManagerView() }
        .confirmationDialog("Remove feed and all its articles?", isPresented: Binding(get: { feedToDelete != nil }, set: { if !$0 { feedToDelete = nil } }), titleVisibility: .visible) {
            Button("Remove feed", role: .destructive) {
                if let feed = feedToDelete {
                    selectedArticle = nil
                    feedFilter = horizontalSizeClass == .regular ? .all : nil
                    modelContext.delete(feed)
                    do { try modelContext.save() } catch { modelContext.rollback(); errorMessage = error.localizedDescription }
                }
                feedToDelete = nil
            }
        } message: { Text("This also deletes starred articles in this feed. Removing a folder keeps its feeds and articles.") }
        .sheet(isPresented: $showingPrivacy) { PrivacyPolicyView() }
        .sheet(isPresented: $showingFeedback) { FeedbackView() }
        .sheet(isPresented: $isAddingFeed) {
            AddFeedSheet { input in
                await addFeed(from: input)
            }
        }
        // .data as well: other apps' OPML type identifiers vary, and the parser rejects non-OPML files.
        .fileImporter(isPresented: $isImportingOPML, allowedContentTypes: [.opml, .xml, .data]) { result in
            Task { await importOPML(from: result) }
        }
        .fileExporter(
            isPresented: Binding(
                get: { exportDocument != nil },
                set: { if !$0 { exportDocument = nil } }
            ),
            document: exportDocument,
            contentType: .opml,
            defaultFilename: "Plain RSS Subscriptions"
        ) { result in
            if case .failure(let error) = result {
                errorMessage = error.localizedDescription
            }
        }
    }

    @ViewBuilder
    private var emptyArticlesView: some View {
        if feeds.isEmpty {
            ContentUnavailableView {
                Label("No Feeds Yet", systemImage: "dot.radiowaves.up.forward")
            } description: {
                Text("Add an RSS or Atom feed to start reading.")
            } actions: {
                Button("Add Feed") {
                    isAddingFeed = true
                }
                .buttonStyle(.borderedProminent)
            }
        } else if !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            ContentUnavailableView.search(text: searchText)
        } else if feedFilter == .unread {
            ContentUnavailableView(
                "All Caught Up",
                systemImage: "checkmark.circle",
                description: Text("You have no unread articles.")
            )
        } else if feedFilter == .starred {
            ContentUnavailableView(
                "No Starred Articles",
                systemImage: "star",
                description: Text("Tap the star on an article to save it here.")
            )
        } else {
            ContentUnavailableView(
                "No Articles",
                systemImage: "newspaper",
                description: Text("Pull down to refresh.")
            )
        }
    }

    @ViewBuilder
    private var articleList: some View {
        let articles = visibleArticles
        List(selection: $selectedArticle) {
            ForEach(articles) { article in
                ArticleRow(article: article, showsFeedTitle: showsFeedTitleInRows)
                    .tag(article)
                    .swipeActions(edge: .leading) {
                        Button {
                            article.isRead.toggle()
                        } label: {
                            Label(article.isRead ? "Unread" : "Read", systemImage: article.isRead ? "envelope.badge" : "envelope.open")
                        }
                        .tint(.blue)
                    }
                    .swipeActions(edge: .trailing) {
                        Button {
                            article.isStarred.toggle()
                        } label: {
                            Label(article.isStarred ? "Unstar" : "Star", systemImage: article.isStarred ? "star.slash" : "star")
                        }
                        .tint(.orange)
                    }
            }
        }
        .navigationTitle(navigationTitle)
        .searchable(text: $searchText, prompt: "Search articles")
        .refreshable {
            await refreshAllFeeds()
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Mark All as Read", systemImage: "checkmark.circle") { confirmMarkAllRead = true }
                    .disabled(!articles.contains { !$0.isRead })
            }
        }
        .overlay {
            if articles.isEmpty {
                emptyArticlesView
            }
        }
    }

    /// Returns an error message to show, or nil on success.
    private func addFeed(from input: String) async -> String? {
        var trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return "Enter a valid feed URL."
        }

        if !trimmed.contains("://") {
            trimmed = "https://" + trimmed
        }

        guard let url = URL(string: trimmed), url.host() != nil else {
            return "Enter a valid feed URL."
        }

        if feeds.contains(where: { $0.url == url.absoluteString }) {
            return "That feed is already added."
        }

        guard !isRefreshing else {
            return "Wait for the current refresh to finish, then try again."
        }
        isRefreshing = true
        defer { isRefreshing = false }

        do {
            let (resolved, fetched) = try await FeedFetcher.fetchNew(url: url)
            if feeds.contains(where: { $0.url == resolved.absoluteString }) {
                return "That feed is already added."
            }
            try apply(fetched, url: resolved, to: nil)
            try modelContext.save()
            return nil
        } catch {
            return friendlyMessage(for: error)
        }
    }

    private func importOPML(from result: Result<URL, Error>) async {
        let fileURL: URL
        switch result {
        case .success(let url):
            fileURL = url
        case .failure(let error):
            errorMessage = error.localizedDescription
            return
        }

        guard !isRefreshing else {
            errorMessage = "Wait for the current refresh to finish, then try again."
            return
        }

        let importedFeeds: [OPMLFeed]
        do {
            importedFeeds = try await Task.detached(priority: .userInitiated) {
                let isAccessing = fileURL.startAccessingSecurityScopedResource()
                defer {
                    if isAccessing { fileURL.stopAccessingSecurityScopedResource() }
                }
                return try OPMLParser.parse(data: Data(contentsOf: fileURL))
            }.value
        } catch {
            errorMessage = error.localizedDescription
            return
        }

        guard !importedFeeds.isEmpty else {
            importSummary = "No feeds were found in that file."
            return
        }

        var knownURLs = Set(feeds.map(\.url))
        var createdFolders: [FeedFolder] = []
        var added: [Feed] = []
        var invalidCount = 0

        for imported in importedFeeds {
            guard let url = URL(string: imported.url),
                  let scheme = url.scheme?.lowercased(),
                  scheme == "http" || scheme == "https",
                  url.host() != nil else {
                invalidCount += 1
                continue
            }
            guard knownURLs.insert(url.absoluteString).inserted else { continue }

            let feed = Feed(url: url.absoluteString, title: imported.title)
            feed.folderID = folderID(forImported: imported.folder, created: &createdFolders)
            modelContext.insert(feed)
            added.append(feed)
        }
        let duplicateCount = importedFeeds.count - added.count - invalidCount

        do {
            try modelContext.save()
        } catch {
            errorMessage = error.localizedDescription
            return
        }

        var failures: [String] = []
        if !added.isEmpty {
            isRefreshing = true
            failures = await refresh(added)
            isRefreshing = false
        }

        importSummary = Self.importSummaryText(
            addedCount: added.count,
            duplicateCount: duplicateCount,
            invalidCount: invalidCount,
            failures: failures
        )
    }

    /// Finds or creates the local folder for an OPML folder name. Names the app can't use import as unfiled.
    private func folderID(forImported name: String?, created: inout [FeedFolder]) -> String? {
        guard let name = name?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty else { return nil }
        let known = folders + created
        if let match = known.first(where: { $0.name.caseInsensitiveCompare(name) == .orderedSame }) { return match.id }
        guard let valid = ReaderLibrary.folderName(name, folders: known) else { return nil }
        let folder = FeedFolder(name: valid)
        modelContext.insert(folder)
        created.append(folder)
        return folder.id
    }

    static func importSummaryText(
        addedCount: Int,
        duplicateCount: Int,
        invalidCount: Int,
        failures: [String]
    ) -> String {
        var lines = [addedCount == 1 ? "Added 1 feed." : "Added \(addedCount) feeds."]

        if duplicateCount > 0 {
            lines.append(duplicateCount == 1
                ? "1 feed was already in your list."
                : "\(duplicateCount) feeds were already in your list.")
        }
        if invalidCount > 0 {
            lines.append(invalidCount == 1
                ? "1 entry had an invalid URL and was skipped."
                : "\(invalidCount) entries had invalid URLs and were skipped.")
        }
        if !failures.isEmpty {
            let shown = failures.prefix(5).joined(separator: "\n")
            let more = failures.count > 5 ? "\n…and \(failures.count - 5) more" : ""
            lines.append("\nThese feeds were added but couldn't load right now:\n\(shown)\(more)")
        }
        return lines.joined(separator: " ")
    }

    private func exportFeeds() {
        exportDocument = OPMLDocument(feeds: sortedFeeds.map { feed in
            OPMLFeed(title: feed.title, url: feed.url, folder: folders.first { $0.id == feed.folderID }?.name)
        })
    }

    private func refreshAllFeeds() async {
        guard !isRefreshing else { return }
        isRefreshing = true
        errorMessage = nil
        defer { isRefreshing = false }

        let failures = await refresh(feeds)
        if !failures.isEmpty {
            errorMessage = failures.joined(separator: "\n")
        }
    }

    /// Refreshes the given feeds concurrently. Returns one "Title: reason" line per failure.
    private func refresh(_ feedsToRefresh: [Feed]) async -> [String] {
        let targets = feedsToRefresh.compactMap { feed in
            URL(string: feed.url).map { (id: feed.persistentModelID, title: feed.title, url: $0) }
        }

        var failures: [String] = []
        func record(_ result: (String, Error?)) {
            if let error = result.1 { failures.append("\(result.0): \(friendlyMessage(for: error))") }
        }
        // Bounded so a large OPML import doesn't open hundreds of connections at once.
        let maxConcurrent = 6
        await withTaskGroup(of: (String, Error?).self) { group in
            var inFlight = 0
            for target in targets {
                if inFlight >= maxConcurrent, let result = await group.next() {
                    record(result)
                    inFlight -= 1
                }
                group.addTask { @MainActor in
                    do {
                        // A feed removed while it waited its turn is skipped.
                        guard let feed = liveFeed(target.id) else { return (target.title, nil) }
                        try await refreshFeed(feed, at: target.url)
                        return (target.title, nil)
                    } catch {
                        return (target.title, error)
                    }
                }
                inFlight += 1
            }
            for await result in group { record(result) }
        }
        return failures
    }

    private func refreshFeed(_ feed: Feed, at url: URL) async throws {
        let id = feed.persistentModelID
        let result = try await FeedFetcher.fetch(url: url, etag: feed.etag, lastModified: feed.lastModified)
        // A feed removed while its download was in flight is not added back.
        guard let feed = liveFeed(id) else { return }
        switch result {
        case .notModified:
            feed.lastFetchedAt = Date()
            applyRetention(to: feed)
        case .fetched(let fetched):
            try apply(fetched, url: url, to: feed)
        }
        try modelContext.save()
    }

    /// The feed with this identifier, or nil once the user has removed it.
    private func liveFeed(_ id: PersistentIdentifier) -> Feed? {
        guard let feed: Feed = modelContext.registeredModel(for: id),
              !feed.isDeleted, feed.modelContext != nil else { return nil }
        return feed
    }

    /// Stores a downloaded feed, creating the `Feed` when `existingFeed` is nil. Does not save.
    private func apply(_ fetched: FetchedFeed, url: URL, to existingFeed: Feed?) throws {
        let feed: Feed
        if let existing = existingFeed {
            existing.adoptPublisherTitle(fetched.parsed.title)
            feed = existing
        } else {
            feed = Feed(url: url.absoluteString, title: fetched.parsed.title)
            modelContext.insert(feed)
        }
        feed.lastFetchedAt = Date()
        feed.etag = fetched.etag
        feed.lastModified = fetched.lastModified

        FeedArticleMerger.merge(fetched.parsed.articles, into: feed, sourceURL: url, context: modelContext)
        applyRetention(to: feed)
    }

    private func applyRetention(to feed: Feed) {
        guard feed.retentionEnabled else { return }
        let excess = ReaderLibrary.excess(in: feed, limit: retention)
        if let selectedArticle, excess.contains(where: { $0 === selectedArticle }) { self.selectedArticle = nil }
        ReaderLibrary.trim(feed, limit: retention, context: modelContext)
    }

    private func refreshIfStale() async {
        guard refreshOnOpen, !feeds.isEmpty, !isRefreshing else { return }
        let now = Date().timeIntervalSince1970
        guard now - lastAutoRefresh > 15 * 60 else { return }
        lastAutoRefresh = now
        await refreshAllFeeds()
    }

    private func renameFeed() {
        if let feed = feedToRename, feed.rename(to: renameText) {
            do { try modelContext.save() } catch { modelContext.rollback(); errorMessage = error.localizedDescription }
        }
        feedToRename = nil
    }

    private func markVisibleAsRead() {
        for article in visibleArticles where !article.isRead { article.isRead = true }
        do { try modelContext.save() } catch { modelContext.rollback(); errorMessage = error.localizedDescription }
    }

    private func friendlyMessage(for error: Error) -> String {
        if let urlError = error as? URLError,
           urlError.code == .appTransportSecurityRequiresSecureConnection {
            return "iOS blocks insecure http:// connections. Use the https:// version of the feed URL."
        }
        return error.localizedDescription
    }

    private func feedRow(_ feed: Feed) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(feed.title).font(.headline)
                Text(feed.url).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
            if let unread = unreadCounts[feed.persistentModelID], unread > 0 {
                Text("\(unread)").font(.caption).foregroundStyle(.secondary)
            }
        }
        .tag(FeedFilter.feed(feed.url))
        .contextMenu {
            Menu("Move to folder") {
                Button("Unfiled") { move(feed, to: nil) }
                ForEach(folders) { folder in Button(folder.name) { move(feed, to: folder.id) } }
            }
            Button("Rename…", systemImage: "pencil") { renameText = feed.title; feedToRename = feed }
            Button("Remove feed…", role: .destructive) { feedToDelete = feed }
        }
    }
    private func move(_ feed: Feed, to id: String?) {
        feed.folderID = id
        do { try modelContext.save() } catch { modelContext.rollback(); errorMessage = error.localizedDescription }
    }

}

// MARK: - Article Row

struct ArticleRow: View {
    let article: Article
    var showsFeedTitle = false

    private var preview: String {
        article.summary
            .prefix(500)
            .split(whereSeparator: \.isWhitespace)
            .joined(separator: " ")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline) {
                Text(article.title)
                    .font(.headline)
                    .fontWeight(article.isRead ? .regular : .semibold)
                if article.isStarred {
                    Image(systemName: "star.fill")
                        .font(.caption)
                        .foregroundStyle(.orange)
                }
            }

            HStack(spacing: 4) {
                if showsFeedTitle, let feedTitle = article.feed?.title {
                    Text(feedTitle)
                        .lineLimit(1)
                    if article.publishedAt != nil {
                        Text("·")
                    }
                }
                ArticleDateText(date: article.publishedAt)
            }
            .font(.caption)
            .foregroundStyle(.secondary)

            if !preview.isEmpty {
                Text(preview)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(3)
                    .padding(.top, 2)
            }
        }
        .padding(.vertical, 4)
    }
}

struct ArticleDateText: View {
    let date: Date?
    @AppStorage("dateDisplay") private var display = DateDisplay.automatic.rawValue
    var body: some View {
        TimelineView(.periodic(from: .now, by: 60)) { timeline in
            Text((DateDisplay(rawValue: display) ?? .automatic).text(date, now: timeline.date))
        }
    }
}

// MARK: - Add Feed

struct AddFeedSheet: View {
    /// Returns an error message to show, or nil on success.
    let onAdd: (String) async -> String?

    @Environment(\.dismiss) private var dismiss
    @State private var urlText = ""
    @State private var isAdding = false
    @State private var errorMessage: String?
    @FocusState private var isFieldFocused: Bool

    private var trimmedURL: String {
        urlText.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("example.com/feed.xml", text: $urlText)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        .textContentType(.URL)
                        .autocorrectionDisabled()
                        .submitLabel(.done)
                        .focused($isFieldFocused)
                        .disabled(isAdding)
                        .onSubmit(add)
                } header: {
                    Text("Feed URL")
                } footer: {
                    if let errorMessage {
                        Text(errorMessage)
                            .foregroundStyle(.red)
                    } else {
                        Text("Enter the address of an RSS or Atom feed.")
                    }
                }
            }
            .navigationTitle("Add Feed")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isAdding {
                        ProgressView()
                    } else {
                        Button("Add", action: add)
                            .disabled(trimmedURL.isEmpty)
                    }
                }
            }
        }
        .presentationDetents([.medium])
        .interactiveDismissDisabled(isAdding)
        .onAppear { isFieldFocused = true }
    }

    private func add() {
        guard !trimmedURL.isEmpty, !isAdding else { return }
        isAdding = true
        errorMessage = nil

        Task {
            if let error = await onAdd(trimmedURL) {
                errorMessage = error
                isAdding = false
            } else {
                dismiss()
            }
        }
    }
}

// MARK: - Article Detail

struct ArticleDetailView: View {
    @Bindable var article: Article
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("dateDisplay") private var dateDisplay = DateDisplay.automatic.rawValue
    @State private var imagePermissionFor: PersistentIdentifier?
    @State private var consentRequestedFor: PersistentIdentifier?
    @State private var showingImageNotice = false

    private var originalURL: URL? { article.link.flatMap { ReaderURL.allowed($0) } }
    private var imagesAllowed: Bool { imagePermissionFor == article.persistentModelID }
    private var input: ReaderInput {
        ReaderInput(title: article.title,
                    metadata: [article.feed?.title, (DateDisplay(rawValue: dateDisplay) ?? .automatic).text(article.publishedAt)].compactMap { $0 }.joined(separator: " · "),
                    body: article.contentHTML ?? "<p>\(ReaderSanitizer.escaped(article.summary))</p>",
                    baseURL: originalURL ?? article.feed.flatMap { ReaderURL.allowed($0.url) },
                    allowsImages: imagesAllowed)
    }
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text(imagesAllowed ? "Images enabled for this article" : "Remote images are off")
                    .font(.footnote).foregroundStyle(.secondary)
                Spacer()
                Button(imagesAllowed ? "Hide images" : "Load images") {
                    if imagesAllowed { imagePermissionFor = nil }
                    else { consentRequestedFor = article.persistentModelID; showingImageNotice = true }
                }.font(.footnote)
            }.padding()
            PrivateArticleWebView(input: input).id(article.persistentModelID)
        }
        .navigationTitle(article.feed?.title ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { article.isStarred.toggle() } label: {
                    Label(article.isStarred ? "Unstar" : "Star", systemImage: article.isStarred ? "star.fill" : "star")
                }.tint(.orange)
                if let originalURL {
                    Link(destination: originalURL) { Label("Open full article", systemImage: "safari") }
                }
            }
        }
        .alert("Load this article’s images?", isPresented: $showingImageNotice) {
            Button("Load images") {
                if consentRequestedFor == article.persistentModelID { imagePermissionFor = article.persistentModelID }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Images contact external servers, which may receive your IP address and image URLs. Permission lasts only while viewing this article. Scripts and other remote content remain blocked.")
        }
        .onAppear {
            article.isRead = true; imagePermissionFor = nil; consentRequestedFor = nil; showingImageNotice = false
        }
        .onChange(of: article.persistentModelID) { _, _ in
            imagePermissionFor = nil; consentRequestedFor = nil; showingImageNotice = false
        }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { imagePermissionFor = nil; showingImageNotice = false }
        }
        .onDisappear { imagePermissionFor = nil; showingImageNotice = false }
    }
}

#Preview {
    ContentView()
        .modelContainer(for: [Feed.self, Article.self, FeedFolder.self], inMemory: true)
}
