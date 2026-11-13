---
last_updated: 2026-11-08
version: 5.0.1
product_versions:
  windows: "0.1.0"
  ios: "1.0"
  android: "1.1.0"
generated_from: "dev-docs/specs/USER_MANUAL_SPEC.instructions.md"
---

# User Manual

## Overview

Plain RSS is an RSS and Atom reader for people who want to follow websites
from one place. Windows uses a desktop window with subscriptions, articles,
and previews. iOS uses a feed list, article list, and article reader.

This manual describes the current repository implementation. Windows metadata
reports version **0.1.0**; iOS application metadata reports **1.0**, build
**1**. Public release
availability is unknown; these version numbers do not establish a published
release. Features differ between platforms.

<!-- Verified from: platform/windows/pyproject.toml; platform/ios/plain RSS.xcodeproj/project.pbxproj; platform/windows/plain_rss/gui/views/root_window/window.py; platform/ios/plain RSS/ContentView.swift -->

## Installation and Setup

### Check installation availability

| Platform | Requirements and availability |
| --- | --- |
| Windows | The source package declares Python 3.12 or later, PySide6, and defusedxml. A supported minimum Windows version and public installer URL are unknown. |
| iOS | The Xcode project declares an iOS 26.4 deployment target. An App Store or TestFlight installation link is unknown. |

No verified public download route is supplied by this repository. The iOS
deployment target is a project setting, not evidence of testing on every
compatible device. Once an installed copy is available, follow the tasks below.

<!-- Verified from: platform/windows/pyproject.toml; platform/ios/plain RSS.xcodeproj/project.pbxproj -->

### Set up the Windows source edition

If you have the repository and Python installed, open PowerShell in
`platform/windows/` and run:

```powershell
python -m venv .venv
.venv/Scripts/python.exe -m pip install -e .
```

This installs the Windows application and its declared dependencies into that
folder's environment. Internet access may be needed to obtain dependencies.
You do not need to activate the environment when using the full commands below.

<!-- Verified from: platform/windows/README.md; platform/windows/pyproject.toml; platform/windows/AGENTS.md -->

### Add your first subscription

Have an RSS or Atom feed address ready. Prefer an HTTPS feed URL. Windows
requires a complete `http://` or `https://` address. iOS adds `https://` when
you omit the scheme, but insecure HTTP connections may be blocked. No account
sign-in is part of the implemented feed-adding workflow.

<!-- Verified from: platform/windows/plain_rss/gui/views/root_window/window.py [_add_feed]; platform/ios/plain RSS/ContentView.swift [addFeed, friendlyMessage] -->

## Starting the Program

### Start on Windows

From `platform/windows/`, run:

```powershell
.venv/Scripts/python.exe -m plain_rss.main
```

After installation into that environment, the equivalent command is:

```powershell
.venv/Scripts/plain-rss.exe
```

The window opens and refreshes saved feeds. Choose **File > Quit** or close the
window to exit. If a refresh is active, the app finishes stopping it before
closing. A distributed executable, if provided, is named **Plain RSS.exe**.

<!-- Verified from: platform/windows/pyproject.toml; platform/windows/plain_rss/main.py; platform/windows/plain_rss/gui/app.py; platform/windows/plain_rss/gui/views/root_window/window.py [closeEvent]; platform/windows/scripts/nuitka/onefile.ps1 -->

### Start on iOS

Open the installed **plain RSS** app. On a compact display, start from the feed
list; on a wider display, the app can show multiple columns. Automatic refresh
is enabled by default and checks feeds on opening or returning to the app,
with at least 15 minutes between automatic attempts. Return to the Home Screen
when finished; there is no in-app Quit command.

<!-- Verified from: platform/ios/plain RSS/PlainRSSApp.swift; platform/ios/plain RSS/ContentView.swift [refreshIfStale]; platform/ios/plain RSS/ReaderSettings.swift -->

## Getting Around

| Platform | Main areas and controls |
| --- | --- |
| Windows | Subscriptions on the left, **Article** and **Published** columns in the middle, and the selected article's preview on the right. Menus: **File**, **Feeds**, **Article**, and **Help**. |
| iOS | **All Articles**, **Unread**, **Starred**, **Unfiled**, folders, and individual feeds. Use **Add Feed**, **Refresh**, **Folders**, and **More** from the feed list. **More** contains settings, privacy, and OPML actions. |

On Windows, drag the pane dividers to adjust the reading space. On iOS, select
a feed or filter, then an article; use the navigation controls to return.

<!-- Verified from: platform/windows/plain_rss/gui/views/root_window/window.py [_create_widgets, _create_menu_bar]; platform/ios/plain RSS/ContentView.swift -->

## Everyday Tasks

### Subscribe to a feed

#### Add a Windows subscription

1. Choose **Feeds > Add feed** or **Add feed** on the toolbar.
1. Enter the complete feed address in **Feed URL:** and accept the dialog.
1. Select the new subscription to see its downloaded articles.

An exact duplicate URL is rejected. The subscription is saved before its
initial download, so a failed download can be retried with **Refresh**.

<!-- Verified from: platform/windows/plain_rss/gui/views/root_window/window.py [_add_feed] -->

#### Add an iOS subscription

1. Tap **Add Feed**, or **Add Your First Feed** in an empty library.
1. Enter the feed address under **Feed URL**.
1. Tap **Add** and wait for the download, or **Cancel** to dismiss the sheet.

The feed is saved after a successful fetch. Duplicate feeds are rejected.
Wait for an existing refresh to finish before adding another feed.

<!-- Verified from: platform/ios/plain RSS/ContentView.swift [AddFeedSheet, addFeed] -->

### Refresh subscriptions

On Windows, choose **Feeds > Refresh** or the toolbar refresh button. On iOS,
tap **Refresh** in the feed list or pull down on the article list. New and
updated entries appear after downloads complete. Network access is required.
A publisher may provide only a limited set of recent articles.

<!-- Verified from: platform/windows/plain_rss/gui/views/root_window/window.py [refresh_feeds]; platform/ios/plain RSS/ContentView.swift [refreshAllFeeds, articleList]; platform/ios/plain RSS/ReaderSettings.swift [FeedArticleMerger] -->

### Read and open articles

1. Select a feed or, on iOS, an article filter.
1. Select an article to read its preview or saved content.
1. On Windows, choose **Article > Open article** or **Open original article**
   in the preview. On iOS, use **Open full article** when it is available.

Original links open outside the reader. The publisher controls the full
article, availability, and any access requirements. On iOS, opening an article
marks it read; saved article text remains available offline.

<!-- Verified from: platform/windows/plain_rss/gui/views/root_window/window.py [_open_selected_article]; platform/windows/plain_rss/gui/views/root_window/panes/preview.py; platform/ios/plain RSS/ContentView.swift [ArticleDetailView]; platform/ios/plain RSS/ReaderSettings.swift -->

### Search, mark, and star on iOS

- Use **Search articles** to match titles and summaries within the selected
  feed, folder, or filter.
- Swipe an article to use its read/unread or star/unstar action. The reader's
  **Star** button also toggles whether an article is starred.
- Choose **Mark All as Read**, then confirm **Mark as Read**, to mark the
  articles currently shown, including the active search filter.
- Choose **Unread** or **Starred** to focus the article list.

Stars protect articles from retention cleanup, but not from removal of their
entire feed. These controls are not implemented in the Windows reader.

<!-- Verified from: platform/ios/plain RSS/ContentView.swift [visibleArticles, articleList, markVisibleAsRead, ArticleDetailView]; platform/ios/plain RSS/ReaderSettings.swift [excess]; platform/windows/plain_rss/gui/views/root_window/window.py -->

### Organize iOS feeds

1. Tap **Folders** from the feed list.
1. Enter a folder name and use the add/save control. Names must be unique
   ignoring case, contain 1–80 characters after trimming, and not be
   **Unfiled**.
1. Open a feed's context menu and choose **Move to folder**, then the folder
   or **Unfiled**.

The folder manager also provides rename and removal controls. Removing a folder
keeps its feeds and articles, moving the feeds to **Unfiled**. A feed's context
menu has **Rename…**; enter a nonblank **Feed name** and choose **Save**.
A subsequent refresh can replace that name with the publisher's feed title.

<!-- Verified from: platform/ios/plain RSS/ReaderSettings.swift [FolderManagerView, folderName, remove]; platform/ios/plain RSS/ContentView.swift [feedRow, renameFeed, apply] -->

### Remove a subscription

On Windows, select a feed and choose **Feeds > Remove feed**. Removal happens
immediately without a confirmation dialog and clears its loaded articles.

On iOS, open the feed's context menu, choose **Remove feed…**, and confirm
**Remove feed**. This deletes all saved articles in that feed, including stars.
Adding the feed again cannot guarantee recovery of older articles.

<!-- Verified from: platform/windows/plain_rss/gui/views/root_window/window.py [_remove_selected_feed]; platform/ios/plain RSS/ContentView.swift -->

### Control iOS reader images

Remote reader images are off by default. Tap **Load images**, read the notice,
and confirm **Load images** to allow them for that article. Tap **Hide images**
to revoke permission. Leaving the article or backgrounding the app also resets
permission. Scripts and other remote content remain blocked.

Image requests contact external servers, which may receive your IP address and
image URLs. Feed refreshes also contact publishers. View **More > Privacy**
for the app's offline privacy information.

<!-- Verified from: platform/ios/plain RSS/ContentView.swift [ArticleDetailView]; platform/ios/plain RSS/PrivateArticleReader.swift; platform/ios/plain RSS/PrivacyPolicyView.swift -->

### Send feedback on Android

In the Android app, choose **More > Send Feedback**.

- **Report a bug** and **Request a feature** open a draft in your email app,
  addressed to `support@plaintxtoffice.com`. The draft already contains the app
  version, Android version, and device model. Your feeds and articles are not
  added; include a feed address yourself if the problem involves one.
- **Open contact form** opens <https://plaintxtoffice.com/contact/> in your
  browser.

Nothing is sent unless you send it. If the device has no email app or no
browser, the dialog shows a message naming the other option.

<!-- Verified from: platform/android/app/src/main/kotlin/com/plaintxtoffice/plainrss/ui/Feedback.kt; platform/android/app/src/main/kotlin/com/plaintxtoffice/plainrss/ui/Dialogs.kt [FeedbackDialog]; platform/android/app/src/main/res/values/strings.xml -->

## Settings and Preferences

### Change Windows display preferences

Choose **File > Settings...**, select a **Date display:** example, then choose
**OK**. **Cancel** keeps the current setting. The default is month-first with a
12-hour clock; alternatives are month-first with a 24-hour clock, day-first
with a 24-hour clock, and year-month-day with a 24-hour clock.

Click **Article** or **Published** to sort; drag column headers to reorder them.
The default is publication date descending. Date format, column order, sort
column, and direction persist between runs.

<!-- Verified from: platform/windows/plain_rss/config/settings.py; platform/windows/plain_rss/gui/views/settings/settings_dialog.py; platform/windows/plain_rss/gui/views/root_window/panes/articles.py -->

### Change iOS preferences

Open **More > Settings**. Changes persist automatically; tap **Done** to close.

| Setting | Default | Choices or behavior |
| --- | --- | --- |
| **Articles per feed** | 200 | 50, 100, 200, 500, or 1,000 unstarred articles, plus all stars. |
| **Refresh when app opens** | On | Automatically checks on opening or returning, at most once per 15 minutes. Turn off for manual refresh only. |
| **Article sorting** | Newest first | Newest first, Oldest first, Title, Unread first. |
| **Feed sorting** | Title | Title, Unread count, Recently refreshed. |
| **Article dates** | Relative, then date | Relative, then date; Relative; Date; Date and time. |

Dates use the device locale and time zone. Missing dates show **Date
unavailable** and follow dated articles in chronological sorting.

<!-- Verified from: platform/ios/plain RSS/ReaderSettings.swift [ReaderSettingsView, DateDisplay, ReaderLibrary]; platform/ios/plain RSS/ContentView.swift [refreshIfStale] -->

### Apply iOS retention limits

Changing **Articles per feed** alone deletes nothing. New feeds use retention;
older exempt feeds remain exempt. Feeds already using retention adopt the
selected limit on their next successful refresh.

To clean up existing feeds immediately, choose **Apply limit to existing
articles…**, then confirm **Delete excess unstarred articles**. This enables
retention for every existing feed. Stars are kept in addition to the limit.
Cleanup cannot be undone, and older articles may no longer be on the server.

<!-- Verified from: platform/ios/plain RSS/ReaderSettings.swift [ReaderSettingsView]; platform/ios/plain RSS/ContentView.swift [applyRetention]; platform/ios/plain RSS/Item.swift -->

## Importing and Exporting

### Export Windows subscriptions

1. Choose **File > Export JSON...**.
1. Select the destination filename and save.
1. Check the status bar for the export destination or an error dialog.

The output uses a `.json` extension and contains subscription titles and URLs,
not articles or display settings. Example:

```json
[
  {
    "title": "Example feed",
    "url": "https://example.com/feed.xml"
  }
]
```

Writing to an existing destination replaces its contents after the save dialog
is accepted. No subscription-import command is implemented in the Windows UI.

<!-- Verified from: platform/windows/plain_rss/gui/views/root_window/window.py [_export_subscriptions]; platform/windows/plain_rss/domains/feeds/rss.py [SubscriptionRepository] -->

### Import iOS subscriptions

1. Choose **More > Import OPML…**, or **Import from Another App** in an empty
   library.
1. Select an OPML file using the file picker.
1. Review **Import Complete** for added, duplicate, invalid, or failed feeds.

The importer reads feed outlines with `xmlUrl`, accepts HTTP/HTTPS feed URLs,
and skips known URLs and duplicates in the file. Valid new subscriptions are
saved and then refreshed. A failed refresh does not mean the subscription was
not imported. Folder outlines can recreate folder assignments; nested input
uses the innermost enclosing folder rather than a nested folder hierarchy.

<!-- Verified from: platform/ios/plain RSS/OPML.swift [OPMLParser]; platform/ios/plain RSS/ContentView.swift [importOPML, folderID] -->

### Export iOS subscriptions

Choose **More > Export OPML…**, then save the file at the destination offered
by iOS. The default filename is **plain RSS Subscriptions**. Export includes
feed titles, URLs, and folder grouping for feeds; it does not export article
text, read status, or stars. Empty folders have no feed records to export.
The system file picker handles destination and replacement choices.

Windows JSON and iOS OPML are different formats and cannot be exchanged using
the implemented import/export controls without conversion.

<!-- Verified from: platform/ios/plain RSS/ContentView.swift [exportFeeds]; platform/ios/plain RSS/OPML.swift [OPMLWriter]; platform/windows/plain_rss/domains/feeds/rss.py [SubscriptionRepository] -->

## Commands and Shortcuts

| Platform | Command or control | Effect |
| --- | --- | --- |
| Windows | **Feeds > Add feed** | Subscribe to a URL. |
| Windows | **Feeds > Refresh** | Refresh all subscriptions. |
| Windows | **Feeds > Remove feed** | Immediately remove the selected subscription. |
| Windows | **Article > Open article** | Open the selected article in the browser. |
| Windows | **File > Settings...** | Change date display. |
| Windows | **File > Export JSON...** | Save the subscription list. |
| Windows | **Help > About Plain RSS** | Show application information. |
| Windows | **File > Quit** | Close the application. |
| iOS | **More**, swipe actions, and feed context menus | Access import/export, preferences, privacy, read/star controls, and feed organization. |

Windows uses system-standard keyboard bindings for adding, refreshing,
removing, opening, and quitting. Use the shortcuts displayed beside those
commands in your installed app; a fixed key list is not verified here.
The Windows launch commands in Starting the Program open the graphical app;
no separate task-oriented command-line options are implemented. Dedicated iOS
keyboard shortcuts are not declared in the inspected interface.

<!-- Verified from: platform/windows/plain_rss/gui/views/root_window/window.py [_create_actions]; platform/windows/plain_rss/main.py; platform/ios/plain RSS/ContentView.swift -->

## Your Data Files

### Locate Windows files

| File | Source edition location | Packaged edition location |
| --- | --- | --- |
| Subscriptions | `platform/windows/data/subscriptions.json` | `%LOCALAPPDATA%/plain-rss/data/subscriptions.json` |
| Display preferences | `platform/windows/data/settings.json` | `%LOCALAPPDATA%/plain-rss/data/settings.json` |
| Application log | `platform/windows/logs/rss_reader.log` | `%LOCALAPPDATA%/plain-rss/logs/rss_reader.log` |

Builds before 2026-10-28 used `%LOCALAPPDATA%/Plain RSS/`. The packaged
edition renames that folder to `plain-rss` on first launch when the new folder
does not exist yet.

Use the application controls to change subscriptions and preferences. Manual
editing is not required. The subscription file uses the JSON list shown above.
Display preferences contain `schema_version: 1`, an `article_list` object, and
`date_display_format`. Invalid or unsupported settings fall back to defaults.
Windows keeps downloaded article entries in memory, so refresh is needed after
restarting; exporting subscriptions does not save article contents.

<!-- Verified from: platform/windows/plain_rss/config/path_handler.py; platform/windows/plain_rss/main.py; platform/windows/plain_rss/config/settings.py; platform/windows/plain_rss/config/logging/log_config.py; platform/windows/plain_rss/gui/views/root_window/window.py -->

### Understand iOS storage

Subscriptions, folders, saved article text, read status, and stars live in the
app's on-device library. Preferences persist on the device. The library is not
a temporary cache; it remains available offline. Use app controls to change it,
and OPML to transfer subscriptions. The exact sandbox file location is
**Unknown.** and is not a user-editable path.

Reader images use temporary memory and nonpersistent website storage; there is
no persistent image-cache clearing control. External browsers and exported
files have their own storage outside the reader.

<!-- Verified from: platform/ios/plain RSS/Item.swift; platform/ios/plain RSS/PlainRSSApp.swift; platform/ios/plain RSS/ReaderSettings.swift; platform/ios/plain RSS/PrivateArticleReader.swift -->

## Troubleshooting

| Symptom | Likely Cause | Resolution |
| --- | --- | --- |
| Windows rejects a feed URL | Missing or unsupported scheme | Enter a complete HTTP or HTTPS feed address. |
| A feed already exists | The URL is already subscribed | Select the existing feed instead of adding it again. |
| iOS reports an insecure connection | The server uses blocked HTTP | Try the publisher's HTTPS feed URL. |
| Adding or importing on iOS asks you to wait | A refresh is in progress | Wait for it to finish, then retry. |
| Feed refresh fails | Network, server, or feed parsing error | Check the displayed error and feed address, then retry Refresh. |
| Windows has no articles after restarting | Downloaded articles are not stored between runs | Allow startup refresh to complete or choose Refresh. |
| Windows cannot save display settings | The settings file cannot be written | Check access to the data folder and retry. |
| iOS shows **Your data couldn't be opened** | The persistent library could not be opened | Choose **Retry**. **Continue Without Saving** uses temporary storage; changes made in that mode will not persist. Existing saved data is left untouched. |
| iOS images are absent | Remote images are disabled or a resource is rejected | Use Load images for the current article. Unsupported, oversized, or failed images may remain absent; open the original article if needed. |
| OPML import rejects a file | It is not a valid OPML document | Export OPML from the source reader; renaming another format does not convert it. |

<!-- Verified from: platform/windows/plain_rss/gui/views/root_window/window.py; platform/windows/plain_rss/domains/feeds/rss.py; platform/ios/plain RSS/ContentView.swift; platform/ios/plain RSS/StorageRecovery.swift; platform/ios/plain RSS/PrivateArticleReader.swift; platform/ios/plain RSS/OPML.swift -->

## FAQ

### Can I read offline?

On iOS, previously saved article text remains available offline. Remote images,
new refreshes, and external article links need network access. Windows keeps
articles only for the current session and reloads them after restarting.

<!-- Verified from: platform/ios/plain RSS/ReaderSettings.swift; platform/windows/plain_rss/gui/views/root_window/window.py -->

### Does export preserve my whole library?

No. Windows JSON exports subscriptions. iOS OPML exports subscriptions and their
folder grouping, not article contents, read state, or stars.

<!-- Verified from: platform/windows/plain_rss/domains/feeds/rss.py [SubscriptionRepository]; platform/ios/plain RSS/OPML.swift [OPMLWriter] -->

### Do Windows and iOS synchronize automatically?

No cross-device synchronization or shared account workflow is implemented in
the inspected applications. Each platform stores its own subscriptions and
preferences locally.

<!-- Verified from: platform/windows/plain_rss/main.py; platform/windows/plain_rss/config/path_handler.py; platform/ios/plain RSS/PlainRSSApp.swift; platform/ios/plain RSS/Item.swift -->

### Will starring an article keep it forever?

On iOS, stars are exempt from retention cleanup. Removing the feed still deletes
its starred articles. OPML export is not an article backup.

<!-- Verified from: platform/ios/plain RSS/ReaderSettings.swift; platform/ios/plain RSS/ContentView.swift; platform/ios/plain RSS/OPML.swift -->

## Glossary

- **Feed:** an RSS or Atom stream published by a website.
- **Subscription:** a feed address saved in the reader.
- **OPML:** an XML subscription-list format used by the iOS import/export tools.
- **Star:** an iOS marker for an article you want to keep accessible and exempt
  from normal retention cleanup.
- **Retention:** the iOS limit on unstarred articles kept per enabled feed.
- **Unfiled:** iOS feeds that are not assigned to an existing folder.

<!-- Verified from: platform/windows/plain_rss/domains/feeds/rss.py; platform/ios/plain RSS/OPML.swift; platform/ios/plain RSS/ReaderSettings.swift; platform/ios/plain RSS/ContentView.swift -->
