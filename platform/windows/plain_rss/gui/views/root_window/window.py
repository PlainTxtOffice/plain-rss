"""Main window shell for the RSS reader."""

from __future__ import annotations

from dataclasses import replace
from pathlib import Path
from typing import TYPE_CHECKING, override

from PySide6.QtCore import Qt, QTimer, QUrl
from PySide6.QtGui import QAction, QCloseEvent, QDesktopServices, QKeySequence
from PySide6.QtWidgets import (
    QDialog,
    QFileDialog,
    QHBoxLayout,
    QInputDialog,
    QMainWindow,
    QMessageBox,
    QSplitter,
    QStatusBar,
    QStyle,
    QToolBar,
    QWidget,
)

from plain_rss.domains.feeds.rss import (
    FeedFetcher,
    ParsedFeed,
    Subscription,
    SubscriptionRepository,
    is_web_url,
)
from plain_rss.gui.events.feed_loader import FeedRefresh
from plain_rss.gui.feedback import add_feedback_action
from plain_rss.gui.views.root_window.panes.articles import ArticleListPane
from plain_rss.gui.views.root_window.panes.preview import ArticlePreviewPane
from plain_rss.gui.views.root_window.panes.subscriptions import (
    SubscriptionListPane,
)
from plain_rss.gui.views.settings.settings_dialog import SettingsDialog

if TYPE_CHECKING:
    from plain_rss.config.settings import ReaderSettings, SettingsRepository

SETTINGS_WRITE_DELAY_MILLISECONDS = 250
APP_NAME = "Plain RSS"
DISTRIBUTION = "plain-rss"


class RootWindow(QMainWindow):
    """Coordinate subscriptions, articles, and previews in one window."""

    def __init__(
        self,
        subscription_repository: SubscriptionRepository,
        settings_repository: SettingsRepository,
        feed_service: FeedFetcher,
    ) -> None:
        """Create the main reader window."""
        super().__init__()
        self._subscription_repository = subscription_repository
        self._settings_repository = settings_repository
        self._feed_service = feed_service
        self._subscriptions = self._subscription_repository.load()
        self._settings = self._settings_repository.load()
        self._feeds_by_url: dict[str, ParsedFeed] = {}
        self._feed_refresh: FeedRefresh | None = None
        self._pending_subscriptions: list[Subscription] = []
        self._close_requested = False
        self._settings_save_timer = QTimer(self)
        self._settings_save_timer.setSingleShot(True)
        self._settings_save_timer.timeout.connect(
            self._save_article_list_settings,
        )

        self.setWindowTitle("Plain RSS")
        self.resize(1200, 720)
        self._create_actions()
        self._create_widgets()
        self._create_menu_bar()
        self._create_tool_bar()
        self.setStatusBar(QStatusBar(self))
        self.statusBar().showMessage("Add a feed URL to begin.")
        self._render_subscriptions()

    def _create_actions(self) -> None:
        """Create reusable application actions."""
        self._add_action = QAction("Add feed", self)
        self._add_action.setShortcut(QKeySequence.StandardKey.New)
        self._add_action.setToolTip("Add a feed URL")
        self._add_action.triggered.connect(self._add_feed)

        reload_icon = self.style().standardIcon(
            QStyle.StandardPixmap.SP_BrowserReload,
        )
        self._refresh_action = QAction(reload_icon, "Refresh", self)
        self._refresh_action.setShortcut(QKeySequence.StandardKey.Refresh)
        self._refresh_action.setToolTip("Refresh all feeds")
        self._refresh_action.triggered.connect(self.refresh_feeds)

        self._remove_action = QAction("Remove feed", self)
        self._remove_action.setShortcut(QKeySequence.StandardKey.Delete)
        self._remove_action.setToolTip("Remove the selected feed")
        self._remove_action.triggered.connect(self._remove_selected_feed)

        open_icon = self.style().standardIcon(
            QStyle.StandardPixmap.SP_DialogOpenButton,
        )
        self._open_action = QAction(open_icon, "Open article", self)
        self._open_action.setShortcut(QKeySequence.StandardKey.Open)
        self._open_action.setToolTip(
            "Open the selected article in your browser",
        )
        self._open_action.triggered.connect(self._open_selected_article)

        self._settings_action = QAction("Settings...", self)
        self._settings_action.setToolTip("Choose reader display preferences")
        self._settings_action.triggered.connect(self._show_settings)

        self._quit_action = QAction("Quit", self)
        self._quit_action.setShortcut(QKeySequence.StandardKey.Quit)
        self._quit_action.setToolTip("Close Plain RSS")
        self._quit_action.triggered.connect(self.close)

        self._export_action = QAction("Export JSON...", self)
        self._export_action.setToolTip("Export subscriptions as a JSON file")
        self._export_action.triggered.connect(self._export_subscriptions)

        self._about_action = QAction("About Plain RSS", self)
        self._about_action.triggered.connect(self._show_about)

    def _create_widgets(self) -> None:
        """Create and connect the reader's three main panes."""
        self._subscriptions_pane = SubscriptionListPane()
        self._subscriptions_pane.currentItemChanged.connect(
            self._subscription_selected,
        )

        self._articles_pane = ArticleListPane(
            self._settings.article_list,
            self._settings.date_display_format,
        )
        self._articles_pane.currentItemChanged.connect(self._article_selected)
        self._articles_pane.preferences_changed.connect(
            self._schedule_article_list_settings_save,
        )

        self._preview_pane = ArticlePreviewPane(
            self._settings.date_display_format,
        )

        splitter = QSplitter(Qt.Orientation.Horizontal)
        splitter.addWidget(self._subscriptions_pane)
        splitter.addWidget(self._articles_pane)
        splitter.addWidget(self._preview_pane)
        splitter.setSizes([220, 500, 480])

        central_widget = QWidget()
        layout = QHBoxLayout(central_widget)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.addWidget(splitter)
        self.setCentralWidget(central_widget)

    def _create_menu_bar(self) -> None:
        """Create the application's compact menu bar."""
        file_menu = self.menuBar().addMenu("File")
        file_menu.addAction(self._settings_action)
        file_menu.addSeparator()
        file_menu.addAction(self._export_action)
        file_menu.addSeparator()
        file_menu.addAction(self._quit_action)

        feeds_menu = self.menuBar().addMenu("Feeds")
        feeds_menu.addAction(self._add_action)
        feeds_menu.addAction(self._refresh_action)
        feeds_menu.addSeparator()
        feeds_menu.addAction(self._remove_action)

        article_menu = self.menuBar().addMenu("Article")
        article_menu.addAction(self._open_action)

        help_menu = self.menuBar().addMenu("Help")
        add_feedback_action(help_menu, self, APP_NAME, DISTRIBUTION)
        help_menu.addSeparator()
        help_menu.addAction(self._about_action)

    def _create_tool_bar(self) -> None:
        """Create quick-access controls for common reader actions."""
        tool_bar = QToolBar("Reader tools", self)
        tool_bar.setMovable(False)
        tool_bar.addAction(self._add_action)
        tool_bar.addAction(self._refresh_action)
        tool_bar.addAction(self._remove_action)
        tool_bar.addAction(self._open_action)
        self.addToolBar(tool_bar)

    def _add_feed(self) -> None:
        """Validate, save, and start loading a new subscription."""
        url, accepted = QInputDialog.getText(
            self,
            "Add feed",
            "Feed URL:",
        )
        url = url.strip()
        if not accepted:
            return
        if not is_web_url(url):
            QMessageBox.warning(
                self,
                "Add feed",
                "Enter a complete http:// or https:// feed URL.",
            )
            return
        if any(subscription.url == url for subscription in self._subscriptions):
            QMessageBox.information(
                self,
                "Add feed",
                "That feed is already subscribed.",
            )
            return

        self._subscriptions.append(Subscription(title=url, url=url))
        self._save_subscriptions()
        self._render_subscriptions(selected_url=url)
        self._start_refresh((self._subscriptions[-1],))

    def _show_settings(self) -> None:
        """Let the reader choose a persistent date-display preference."""
        dialog = SettingsDialog(self._settings.date_display_format)
        if dialog.exec() != QDialog.DialogCode.Accepted:
            return

        updated_settings = replace(
            self._settings,
            date_display_format=dialog.date_display_format(),
        )
        if updated_settings == self._settings:
            return
        if not self._save_settings(updated_settings):
            return

        self._articles_pane.set_date_display_format(
            updated_settings.date_display_format,
        )
        self._preview_pane.set_date_display_format(
            updated_settings.date_display_format,
        )

    def _show_about(self) -> None:
        """Show basic application information."""
        QMessageBox.about(
            self,
            "About Plain RSS",
            "Plain RSS\nA lightweight Windows RSS and Atom reader.",
        )

    def _export_subscriptions(self) -> None:
        """Write subscriptions to a user-selected JSON export file."""
        selected_path, _selected_filter = QFileDialog.getSaveFileName(
            self,
            "Export subscriptions",
            "subscriptions.json",
            "JSON files (*.json)",
        )
        if not selected_path:
            return

        export_path = Path(selected_path).with_suffix(".json")
        try:
            self._subscription_repository.write_export(
                export_path,
                self._subscriptions,
            )
        except OSError as error:
            QMessageBox.critical(
                self,
                "Export subscriptions",
                f"Could not write {export_path}: {error}",
            )
            return
        self.statusBar().showMessage(
            f"Exported subscriptions to {export_path}.",
        )

    def _remove_selected_feed(self) -> None:
        """Remove the selected subscription and its loaded articles."""
        subscription = self._selected_subscription()
        if subscription is None:
            return

        self._subscriptions.remove(subscription)
        self._feeds_by_url.pop(subscription.url, None)
        self._save_subscriptions()
        self._render_subscriptions()
        self._articles_pane.set_entries(())
        self._preview_pane.clear_preview()
        self.statusBar().showMessage(f"Removed {subscription.title}.")

    def refresh_feeds(self) -> None:
        """Refresh every subscribed feed."""
        self._start_refresh(tuple(self._subscriptions))

    def _start_refresh(self, subscriptions: tuple[Subscription, ...]) -> None:
        """Start one background refresh if no refresh is already active."""
        if self._feed_refresh is not None:
            self._queue_refresh(subscriptions)
            self.statusBar().showMessage("Refresh queued.")
            return
        if not subscriptions:
            self.statusBar().showMessage("Add a feed URL before refreshing.")
            return

        self._refresh_action.setEnabled(False)
        self._feed_refresh = FeedRefresh(
            subscriptions,
            self._feed_service,
        )
        self._feed_refresh.feed_loaded.connect(self._feed_loaded)
        self._feed_refresh.feed_failed.connect(self._feed_failed)
        self._feed_refresh.finished.connect(self._refresh_finished)
        self._feed_refresh.start()
        self.statusBar().showMessage("Refreshing feeds...")

    def _queue_refresh(self, subscriptions: tuple[Subscription, ...]) -> None:
        """Remember feed requests that arrive during an active refresh."""
        pending_urls = {
            subscription.url for subscription in self._pending_subscriptions
        }
        for subscription in subscriptions:
            if subscription.url not in pending_urls:
                self._pending_subscriptions.append(subscription)
                pending_urls.add(subscription.url)

    def _feed_loaded(self, url: str, parsed_feed: ParsedFeed) -> None:
        """Store a refreshed feed and update its visible title."""
        self._feeds_by_url[url] = parsed_feed
        subscription = next(
            (item for item in self._subscriptions if item.url == url),
            None,
        )
        if subscription is None:
            return
        if subscription.title == url and parsed_feed.title:
            position = self._subscriptions.index(subscription)
            self._subscriptions[position] = Subscription(
                title=parsed_feed.title,
                url=url,
            )
            self._save_subscriptions()
            self._render_subscriptions(selected_url=url)
        selected_subscription = self._selected_subscription()
        if (
            selected_subscription is not None
            and selected_subscription.url == url
        ):
            self._render_articles(url)

    def _feed_failed(self, _url: str, error_message: str) -> None:
        """Display a concise feed failure without interrupting other feeds."""
        self.statusBar().showMessage(error_message, 10_000)

    def _refresh_finished(self) -> None:
        """Restore the interface after the refresh thread completes."""
        feed_refresh = self._feed_refresh
        self._feed_refresh = None
        self._refresh_action.setEnabled(True)
        if feed_refresh is not None:
            feed_refresh.deleteLater()
        if self._close_requested:
            self.close()
            return

        pending_subscriptions = tuple(self._pending_subscriptions)
        self._pending_subscriptions.clear()
        if pending_subscriptions:
            self._start_refresh(pending_subscriptions)
            return
        self.statusBar().showMessage("Feed refresh complete.")

    def _render_subscriptions(self, selected_url: str | None = None) -> None:
        """Render saved subscriptions and retain selected feed when possible."""
        selected_url = selected_url or self._subscriptions_pane.selected_url()
        selected_item = self._subscriptions_pane.render_subscriptions(
            self._subscriptions,
            selected_url,
        )
        if selected_item is not None:
            self._subscription_selected(selected_item, None)

    def _subscription_selected(
        self,
        current: object,
        _previous: object,
    ) -> None:
        """Show articles for the newly selected subscription."""
        url = self._subscriptions_pane.selected_url()
        if current is not None and url is not None:
            self._render_articles(url)

    def _render_articles(self, url: str) -> None:
        """Display entries currently loaded for a subscription."""
        parsed_feed = self._feeds_by_url.get(url)
        if parsed_feed is None:
            self._articles_pane.set_entries(())
            return
        self._articles_pane.set_entries(parsed_feed.entries)

    def _article_selected(
        self,
        current: object,
        _previous: object,
    ) -> None:
        """Render the chosen article in the preview pane."""
        if current is None:
            self._preview_pane.clear_preview()
            return
        entry = self._articles_pane.current_entry()
        if entry is not None:
            self._preview_pane.display_entry(entry)

    def _open_selected_article(self) -> None:
        """Open the chosen article's source URL with the system browser."""
        entry = self._articles_pane.current_entry()
        if entry is not None and entry.link:
            QDesktopServices.openUrl(QUrl(entry.link))

    def _selected_subscription(self) -> Subscription | None:
        """Return the subscription currently selected in the left pane."""
        url = self._subscriptions_pane.selected_url()
        return next(
            (item for item in self._subscriptions if item.url == url),
            None,
        )

    def _save_subscriptions(self) -> None:
        """Persist the current subscriptions."""
        self._subscription_repository.save(self._subscriptions)

    def _save_article_list_settings(self) -> None:
        """Persist the Article list pane's layout preferences."""
        self._save_settings(
            replace(
                self._settings,
                article_list=self._articles_pane.article_list_settings(),
            ),
        )

    def _schedule_article_list_settings_save(self) -> None:
        """Delay layout persistence until a header drag has settled."""
        self._settings_save_timer.start(SETTINGS_WRITE_DELAY_MILLISECONDS)

    def _save_settings(self, settings: ReaderSettings) -> bool:
        """Persist reader preferences and report a recoverable failure."""
        try:
            self._settings_repository.save(settings)
        except OSError:
            self.statusBar().showMessage(
                "Could not save display settings.",
                10_000,
            )
            return False
        self._settings = settings
        return True

    @override
    def closeEvent(self, event: QCloseEvent) -> None:
        """Finish the active refresh before Qt destroys its thread object."""
        if self._settings_save_timer.isActive():
            self._settings_save_timer.stop()
            self._save_article_list_settings()
        if self._feed_refresh is None:
            event.accept()
            return

        self._close_requested = True
        self._feed_refresh.requestInterruption()
        self.setEnabled(False)
        self.statusBar().showMessage("Finishing refresh before closing...")
        event.ignore()
