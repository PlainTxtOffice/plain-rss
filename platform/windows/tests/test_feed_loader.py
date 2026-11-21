"""Tests for background feed refresh behavior."""

from plain_rss.domains.feeds.rss import FeedFetcher, ParsedFeed, Subscription
from plain_rss.gui.events.feed_loader import FeedRefresh


class _InterruptedFeedRefresh(FeedRefresh):
    """Simulate a refresh asked to stop before its next fetch."""

    def isInterruptionRequested(self) -> bool:
        """Report an already-requested interruption for the test."""
        return True


class _CountingFetcher(FeedFetcher):
    """Record fetch attempts made by a refresh thread."""

    fetch_count = 0

    @staticmethod
    def fetch(url: str) -> ParsedFeed:
        """Record the call without making a network request."""
        _CountingFetcher.fetch_count += 1
        msg = f"Unexpected fetch for {url}"
        raise AssertionError(msg)


def test_feed_refresh_stops_before_next_subscription() -> None:
    """An interrupted refresh does not begin another feed download."""
    _CountingFetcher.fetch_count = 0
    feed_refresh = _InterruptedFeedRefresh(
        (Subscription(title="Example", url="https://example.com/feed"),),
        _CountingFetcher(),
    )

    feed_refresh.run()

    assert _CountingFetcher.fetch_count == 0