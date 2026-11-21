import XCTest

/// Walks through the typical user flow at a pace a viewer can follow, for the
/// screen recording App Review asks new apps for.
///
/// Skipped unless the environment sets `APP_REVIEW_WALKTHROUGH=1`, because it
/// downloads a live feed. With `xcodebuild`, pass
/// `TEST_RUNNER_APP_REVIEW_WALKTHROUGH=1`.
///
/// Rules: add only data this test can recognise as its own, put back any
/// setting it changes, never delete or clear the user's data, and stop with a
/// failed assertion before editing anything if the app is not in the state
/// the test expects. The test adds the NASA news feed when it is missing,
/// opens one of its articles, which marks it read, and stars then unstars it.
@MainActor
final class AppReviewWalkthroughUITests: XCTestCase {
    private let app = XCUIApplication()
    private let feedAddress = "www.nasa.gov/news-release/feed/"
    private let feedTitle = "NASA"

    override func setUp() async throws {
        continueAfterFailure = false
    }

    func testTypicalUserFlow() throws {
        try XCTSkipUnless(
            ProcessInfo.processInfo.environment["APP_REVIEW_WALKTHROUGH"] == "1",
            "Set APP_REVIEW_WALKTHROUGH=1 to record the App Review walkthrough."
        )
        app.launch()
        XCTAssertTrue(app.buttons["More"].waitForExistence(timeout: 15))
        pause(3)

        addFeedIfMissing()
        readAnArticle()
        showFolders()
        showMoreScreens()
        pause(3)
    }

    private func addFeedIfMissing() {
        let feed = app.staticTexts[feedTitle].firstMatch
        if feed.exists { return }
        app.buttons["Add Feed"].firstMatch.tap()
        let field = app.textFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        pause()
        field.typeText(feedAddress)
        pause(2)
        app.buttons["Add"].firstMatch.tap()
        XCTAssertTrue(feed.waitForExistence(timeout: 40), "The sample feed did not load")
        pause(3)
    }

    private func readAnArticle() {
        app.staticTexts[feedTitle].firstMatch.tap()
        let article = app.cells.firstMatch
        XCTAssertTrue(article.waitForExistence(timeout: 10))
        pause(3)
        article.tap()
        pause(4)

        let load = app.buttons["Load images"].firstMatch
        if load.waitForExistence(timeout: 5) {
            load.tap()
            pause(3)
            let confirm = app.alerts.buttons["Load images"].firstMatch
            if confirm.waitForExistence(timeout: 5) { confirm.tap() }
            pause(6)
        }

        // Star, then unstar, so the article is left as it was found.
        for _ in 0..<2 {
            let star = app.buttons.matching(NSPredicate(format: "label IN {'Star', 'Unstar'}")).firstMatch
            if star.waitForExistence(timeout: 5) { star.tap() }
            pause(2)
        }
        app.swipeUp()
        pause(3)

        goBack()
        goBack()
    }

    private func showFolders() {
        app.buttons["Folders"].firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Folders"].waitForExistence(timeout: 5))
        pause(4)
        app.buttons["Done"].firstMatch.tap()
        pause(2)
    }

    private func showMoreScreens() {
        openFromMore("readerSettingsButton")
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
        pause(4)
        app.swipeUp()
        pause(3)
        app.buttons["Done"].firstMatch.tap()
        pause(2)

        openFromMore("privacyPolicyButton")
        XCTAssertTrue(app.buttons["privacyPolicyDone"].waitForExistence(timeout: 5))
        pause(4)
        app.swipeUp()
        pause(3)
        app.swipeUp()
        pause(3)
        app.buttons["privacyPolicyDone"].tap()
        pause(2)

        openFromMore("sendFeedbackButton")
        XCTAssertTrue(app.buttons["feedbackDone"].waitForExistence(timeout: 5))
        pause(5)
        app.buttons["feedbackDone"].tap()
        pause(2)
    }

    private func openFromMore(_ identifier: String) {
        app.buttons["More"].firstMatch.tap()
        let item = app.buttons[identifier].firstMatch
        XCTAssertTrue(item.waitForExistence(timeout: 5))
        pause(2)
        item.tap()
    }

    private func goBack() {
        app.navigationBars.buttons.element(boundBy: 0).tap()
        pause(2)
    }

    /// Pauses so each step stays on screen long enough to watch.
    private func pause(_ seconds: TimeInterval = 1.5) {
        Thread.sleep(forTimeInterval: seconds)
    }
}
