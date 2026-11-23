#if DEBUG
import Foundation
import SwiftData

/// Invented feeds and articles for the App Store screenshots.
///
/// Debug builds insert them into an in-memory store when launched with
/// `-plainRSSStoreSample`, so the screenshots need no network and never touch
/// the library on disk. The publishers use reserved `.example` addresses.
enum StoreScreenshotSample {
    private struct SampleArticle {
        var title: String
        var summary: String
        var minutesAgo: Double
        var isRead = false
        var isStarred = false
        var contentHTML: String?
    }

    private struct SampleFeed {
        var title: String
        var url: String
        var folder: String?
        var articles: [SampleArticle]
    }

    static func insert(into context: ModelContext, now: Date = Date()) throws {
        var folderIDs: [String: String] = [:]
        for name in ["Outdoors", "Technology"] {
            let folder = FeedFolder(name: name)
            context.insert(folder)
            folderIDs[name] = folder.id
        }
        for sample in feeds {
            let feed = Feed(url: sample.url, title: sample.title)
            feed.folderID = sample.folder.flatMap { folderIDs[$0] }
            feed.lastFetchedAt = now
            context.insert(feed)
            for (index, item) in sample.articles.enumerated() {
                let slug = item.title.lowercased()
                    .components(separatedBy: CharacterSet.alphanumerics.inverted)
                    .filter { !$0.isEmpty }
                    .joined(separator: "-")
                let article = Article(
                    guid: "\(sample.url)#\(index)",
                    title: item.title,
                    link: sample.url.replacingOccurrences(of: #"/[^/]*$"#, with: "/\(slug)", options: .regularExpression),
                    summary: item.summary,
                    contentHTML: item.contentHTML,
                    publishedAt: now.addingTimeInterval(-item.minutesAgo * 60)
                )
                article.isRead = item.isRead
                article.isStarred = item.isStarred
                article.feed = feed
                context.insert(article)
            }
        }
        try context.save()
    }

    // Each article is short enough to fit on one iPhone screen, so the
    // reader screenshots end cleanly instead of cutting a line.
    private static let hexagonHTML = """
        <p>Six bright stars form a giant loop across the winter sky, and you can trace it with nothing more \
        than your eyes and a clear, dark evening.</p>
        <h2>Start with Orion</h2>
        <p>Find the three stars of Orion’s belt, then look down and to the right for Rigel, the first \
        corner of the hexagon.</p>
        <h2>Walk the loop</h2>
        <ol>
        <li>From Rigel, move up and to the right to Aldebaran, the orange eye of the bull.</li>
        <li>Continue high overhead to Capella, a pale yellow star that is easy to spot.</li>
        <li>Arc down to Pollux, the brighter twin.</li>
        <li>Drop to Procyon, then finish at Sirius, the brightest star of the night.</li>
        </ol>
        <blockquote>Give your eyes twenty minutes away from bright screens.</blockquote>
        """

    private static let sourdoughHTML = """
        <p>A starter is just flour, water and patience. Most problems come down to temperature and timing, \
        not to anything you did wrong.</p>
        <h2>It barely rises</h2>
        <p>Move it somewhere warmer and feed it twice a day for a few days. A sluggish starter usually \
        wakes up within a week.</p>
        <h2>It smells sharp</h2>
        <p>A tangy, vinegar smell means it is hungry. Feed it, and the smell softens within a day.</p>
        <h2>How often to feed it</h2>
        <ul>
        <li>On the counter: once or twice a day.</li>
        <li>In the fridge: once a week.</li>
        <li>Before baking: feed it the night before.</li>
        </ul>
        <blockquote>A healthy starter doubles in four to eight hours.</blockquote>
        """

    private static let orzoHTML = """
        <p>Toasted orzo, a squeeze of lemon and whatever greens are in the fridge. One pan, twenty minutes \
        and very little washing up.</p>
        <h2>You will need</h2>
        <ul>
        <li>1 cup orzo</li>
        <li>2 cups vegetable stock</li>
        <li>1 lemon, zest and juice</li>
        <li>2 handfuls of spinach or kale</li>
        <li>Olive oil, salt and pepper</li>
        </ul>
        <h2>Method</h2>
        <ol>
        <li>Toast the orzo in a little oil until golden.</li>
        <li>Add the stock and simmer, stirring often, for ten minutes.</li>
        <li>Stir in the greens, lemon zest and juice, then season to taste.</li>
        </ol>
        <blockquote>Finish with a spoonful of yogurt or a little grated cheese.</blockquote>
        """

    private static let feeds: [SampleFeed] = [
        SampleFeed(title: "Harbor Dev Notes", url: "https://harbordev.example/rss", folder: "Technology", articles: [
            SampleArticle(
                title: "Shipping Smaller Updates, More Often",
                summary: "Lessons from a year of weekly releases: shorter changelogs, calmer reviews and fewer late-night fixes.",
                minutesAgo: 70
            ),
            SampleArticle(
                title: "A Practical Guide to Offline-First Apps",
                summary: "Store first, sync later. How to design an app that works on a plane and still feels instant.",
                minutesAgo: 430, isStarred: true
            ),
            SampleArticle(
                title: "Keyboard Shortcuts Worth Learning",
                summary: "Ten shortcuts that save a surprising amount of time once they become habit.",
                minutesAgo: 2_900, isRead: true
            ),
        ]),
        SampleFeed(title: "Night Sky Almanac", url: "https://nightsky.example/rss", folder: "Outdoors", articles: [
            SampleArticle(
                title: "Finding the Winter Hexagon",
                summary: "Six bright stars form a giant loop across the winter sky. Here is how to trace it in a few minutes.",
                minutesAgo: 25, contentHTML: hexagonHTML
            ),
            SampleArticle(
                title: "Meteor Watching from the City",
                summary: "You do not need a dark-sky park. A rooftop, a blanket and patience go a long way.",
                minutesAgo: 380
            ),
            SampleArticle(
                title: "Choosing Your First Binoculars",
                summary: "What the numbers on the barrel mean, and why a modest pair beats a cheap telescope.",
                minutesAgo: 3_100, isRead: true, isStarred: true
            ),
        ]),
        SampleFeed(title: "Quiet Kitchen", url: "https://quietkitchen.example/rss", folder: nil, articles: [
            SampleArticle(
                title: "One-Pan Lemon Orzo",
                summary: "Toasted orzo, a squeeze of lemon and whatever greens are in the fridge. Dinner in twenty minutes.",
                minutesAgo: 45, contentHTML: orzoHTML
            ),
            SampleArticle(
                title: "Why We Grind Our Own Spices",
                summary: "A small grinder and whole seeds changed our cooking more than any new pan.",
                minutesAgo: 320, isRead: true
            ),
            SampleArticle(
                title: "Sourdough Starter Questions, Answered",
                summary: "Sluggish rise, strange smells and how often to feed it. The short answers to the questions we get most.",
                minutesAgo: 1_600, isRead: true, isStarred: true, contentHTML: sourdoughHTML
            ),
        ]),
        SampleFeed(title: "Small Screen Weekly", url: "https://smallscreen.example/rss", folder: "Technology", articles: [
            SampleArticle(
                title: "Reading Long Articles on a Phone",
                summary: "Bigger text, fewer distractions and no autoplaying video. Our favorite habits for reading on the go.",
                minutesAgo: 130
            ),
            SampleArticle(
                title: "The Case for Fewer Notifications",
                summary: "We turned off every alert for a month. Here is what we turned back on, and why.",
                minutesAgo: 560
            ),
            SampleArticle(
                title: "Five Widgets That Earn Their Space",
                summary: "Glanceable, quiet and actually useful: the home screen widgets we kept.",
                minutesAgo: 4_300, isRead: true, isStarred: true
            ),
        ]),
        SampleFeed(title: "Trailhead Journal", url: "https://trailhead.example/feed", folder: "Outdoors", articles: [
            SampleArticle(
                title: "Autumn Loops Under Ten Miles",
                summary: "Six short hikes with big views, quiet trails and a good spot for lunch at the halfway point.",
                minutesAgo: 190, isStarred: true
            ),
            SampleArticle(
                title: "Packing for a First Overnight Trip",
                summary: "A short, tested list for one night outside, and the things you can safely leave at home.",
                minutesAgo: 1_500
            ),
        ]),
    ]
}
#endif
