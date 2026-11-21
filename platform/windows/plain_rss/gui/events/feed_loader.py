"""Background feed-refresh thread for the desktop reader."""

from __future__ import annotations

from PySide6.QtCore import QThread, Signal

from plain_rss.domains.feeds.rss import (
    FeedFetcher,
    FeedFetchError,
    Subscription,
)


class FeedRefresh(QThread):
    """Fetch subscriptions sequentially without blocking the Qt event loop."""

    feed_loaded = Signal(str, object)
    feed_failed = Signal(str, str)

    def __init__(
        self,
        subscriptions: tuple[Subscription, ...],
        feed_service: FeedFetcher,
    ) -> None:
        """Initialize the refresh thread with subscriptions to fetch."""
        super().__init__()
        self._subscriptions = subscriptions
        self._feed_service = feed_service

    def run(self) -> None:
        """Fetch each subscription and report each result to the UI thread."""
        for subscription in self._subscriptions:
            if self.isInterruptionRequested():
                return
            try:
                parsed_feed = self._feed_service.fetch(subscription.url)
            except FeedFetchError as error:
                self.feed_failed.emit(subscription.url, str(error))
                continue
            self.feed_loaded.emit(subscription.url, parsed_feed)
