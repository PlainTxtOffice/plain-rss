import SwiftUI
import SwiftData

@main
struct PlainRSSApp: App {
    @State private var recovery: StorageRecovery = {
        let schema = Schema([
            Feed.self,
            Article.self,
            FeedFolder.self,
        ])
        #if DEBUG
        let fixture = ProcessInfo.processInfo.arguments.contains("-plainRSSFixture")
        // Invented sample feeds for the App Store screenshots, in memory only.
        let storeSample = ProcessInfo.processInfo.arguments.contains("-plainRSSStoreSample")
        #else
        let fixture = false
        let storeSample = false
        #endif
        return StorageRecovery(schema: schema) {
            let modelConfiguration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: fixture || storeSample)
            let container = try ModelContainer(for: schema, configurations: [modelConfiguration])
            #if DEBUG
            if fixture {
                let feed = Feed(url: "https://fixture.invalid/feed", title: "Fixture Feed")
                let article = Article(guid: "fixture", title: "Synthetic Article", summary: "Offline fixture", publishedAt: Date(timeIntervalSince1970: 0))
                article.feed = feed
                container.mainContext.insert(feed); container.mainContext.insert(article)
                try container.mainContext.save()
            }
            if storeSample {
                try StoreScreenshotSample.insert(into: container.mainContext)
            }
            #endif
            return container
        }
    }()

    var body: some Scene {
        WindowGroup {
            StorageRecoveryGate(recovery: recovery) {
                ContentView()
            }
        }
    }
}
