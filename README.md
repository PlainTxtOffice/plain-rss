<!-- last_updated: 2026-11-22 -->
<!-- Authoring guidance: dev-docs/specs/README_SPEC.instructions.md -->
<!-- rumdl-disable MD033 MD041 -->
<p align="center">
  <img src="assets/icons/icon-github-readme.svg" alt="Plain RSS icon" width="96">
</p>

<h1 align="center">Plain RSS</h1>

<p align="center">
  A private, quiet reader for the websites you choose. Follow their RSS and
  Atom feeds and read new articles in one place, with no account.
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/PlainTxtOffice/plain-rss" alt="MIT license"></a>
  <a href="https://play.google.com/store/apps/details?id=com.plaintxtoffice.plainrss"><img src="https://img.shields.io/badge/platform-Android-3DDC84" alt="Platform: Android, on Google Play"></a>
  <a href="https://apps.apple.com/app/id6837553488"><img src="https://img.shields.io/badge/platform-iOS-555555" alt="Platform: iOS, on the App Store"></a>
  <a href="https://github.com/PlainTxtOffice/plain-rss/actions/workflows/android-build.yml"><img src="https://github.com/PlainTxtOffice/plain-rss/actions/workflows/android-build.yml/badge.svg?branch=main" alt="Android build status"></a>
  <a href="https://github.com/PlainTxtOffice/plain-rss/actions/workflows/ios-build.yml"><img src="https://github.com/PlainTxtOffice/plain-rss/actions/workflows/ios-build.yml/badge.svg?branch=main" alt="iOS Simulator build status"></a>
</p>

<p align="center">
  <a href="#download">Download</a> ·
  <a href="docs/USER_MANUAL.md">User manual</a> ·
  <a href="docs/PRIVACY.md">Privacy</a>
</p>
<!-- rumdl-enable MD033 MD041 -->

---

## About

Plain RSS brings updates from the websites you choose into one quiet reader,
with no account, no ads and no feed service in between. Subscribe to a site's
feed, refresh when you want to see what is new, and read the articles in a
clean reader. Your subscriptions and downloaded articles are stored on your own
device. Plain RSS is available for Android and for iOS on iPhone and iPad.

<!-- Verified from: platform/ios/plain RSS/ContentView.swift; platform/ios/plain RSS/Item.swift; platform/android/app/src/main/kotlin/com/plaintxtoffice/plainrss/viewmodel/ReaderViewModel.kt; platform/android/app/src/main/kotlin/com/plaintxtoffice/plainrss/storage/LibraryStore.kt; docs/PRIVACY.md -->

## Why RSS

Many websites publish an RSS or Atom feed: a simple list of their newest
articles. A feed reader checks those lists for you, which changes how you
follow the news.

- **Fewer distractions.** There is no algorithmic feed, no ads and no
  engagement tricks. You see what the sites you subscribed to published, in
  the order you choose, and nothing else.
- **Your sources in one place.** Instead of visiting each site to see what is
  new, refresh once and every subscription is brought up to date together.
- **Company news as it is published.** Many investor relations sites publish
  their press releases, filings and event announcements as RSS feeds.
  Subscribe to the companies you follow, and a new release shows up the next
  time you refresh after the company posts it.

By default, Plain RSS refreshes when you open or return to the app, at most
once every 15 minutes; you can turn that off in Settings and refresh only when
you choose. It does not check feeds in the background, so new articles arrive
when you open it.

<!-- Verified from: platform/ios/plain RSS/ContentView.swift [refreshIfStale]; platform/ios/plain RSS/ReaderSettings.swift; platform/android/app/src/main/kotlin/com/plaintxtoffice/plainrss/viewmodel/ReaderViewModel.kt [refreshIfStale, onAppForegrounded] -->

## Features

- Subscribe with a feed address, or with a website's address when you do not
  know the feed; the app finds the feed the page advertises.
- Read downloaded articles offline in a clean reader, and open the original
  story in your browser when you want more.
- Browse all, unread or starred articles, and search article titles and
  summaries.
- Organize feeds into folders and give them your own names.
- Pull down to refresh, swipe an article to mark it read or star it, or mark
  everything shown as read at once.
- Choose how articles and feeds are sorted and how dates appear.
- Limit how many articles each feed keeps; starred articles are always kept.
- Keep remote images in articles off until you choose to load them for that
  article; scripts in articles are always blocked.
- Import subscriptions from another reader, or export them, as OPML.
- Send a bug report or feature request from inside the app.

<!-- Verified from: platform/ios/plain RSS/ContentView.swift; platform/ios/plain RSS/ReaderSettings.swift; platform/ios/plain RSS/OPML.swift; platform/ios/plain RSS/FeedFetcher.swift; platform/ios/plain RSS/PrivateArticleReader.swift; platform/ios/plain RSS/Feedback.swift; platform/android/app/src/main/kotlin/com/plaintxtoffice/plainrss/network/FeedFetcher.kt; platform/android/app/src/main/kotlin/com/plaintxtoffice/plainrss/ui/PlainRssScreen.kt; platform/android/app/src/main/kotlin/com/plaintxtoffice/plainrss/ui/Dialogs.kt; platform/android/app/src/main/kotlin/com/plaintxtoffice/plainrss/ui/PrivateArticleReaderView.kt; platform/android/app/src/main/kotlin/com/plaintxtoffice/plainrss/ui/Feedback.kt; platform/android/app/src/main/kotlin/com/plaintxtoffice/plainrss/domain/OpmlCodec.kt -->

## App Screenshot

### Android

<!-- rumdl-disable MD033 -->
<p align="center">
  <a href="platform/android/assets/screenshots/article_list.png">
    <img src="platform/android/assets/screenshots/article_list.png" alt="Plain RSS on Android showing the All Articles list with search, a starred article, and read and star buttons" width="280">
  </a>
</p>
<!-- rumdl-enable MD033 -->

The iOS app uses its own navigation and controls; its screenshot will be added
when a capture is available.

### Windows (not released)

<!-- rumdl-disable MD033 -->
<p align="center">
  <a href="platform/windows/assets/screenshots/main_window.png">
    <img src="platform/windows/assets/screenshots/main_window.png" alt="Plain RSS on Windows showing subscriptions, an article list, and an article preview" width="640">
  </a>
</p>
<!-- rumdl-enable MD033 -->

## Download

| Platform | Download | Notes |
| --- | --- | --- |
| Android | [Google Play](https://play.google.com/store/apps/details?id=com.plaintxtoffice.plainrss) | Requires Android 8.0 or later. |
| iOS | [App Store](https://apps.apple.com/app/id6837553488) | Version 1.0. Requires iOS 26.4 or later on iPhone or iPad. |

The Windows app has not been released yet.

<!-- Verified from: platform/android/app/build.gradle.kts [applicationId, minSdk]; platform/ios/plain RSS.xcodeproj/project.pbxproj [MARKETING_VERSION, IPHONEOS_DEPLOYMENT_TARGET] -->

## User Manual

See the [User Manual](docs/USER_MANUAL.md) for installation availability,
setup, everyday tasks, settings, and troubleshooting.

## Feedback

Send bug reports and feature requests to
[support@plaintxtoffice.com](mailto:support@plaintxtoffice.com), through the
[contact page](https://plaintxtoffice.com/contact/), or in
[GitHub Issues](https://github.com/PlainTxtOffice/plain-rss/issues). In the
Android and iOS apps, choose **More > Send Feedback** to prepare an email with
the app and device details filled in, or to open the contact page.

## License

Plain RSS is licensed under the [MIT License](LICENSE).
Copyright © 2026 Plain Text Office LLC.
