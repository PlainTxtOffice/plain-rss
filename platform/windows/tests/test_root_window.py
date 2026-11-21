"""Tests for main-window presentation helpers."""

from types import SimpleNamespace
from unittest.mock import patch

import pytest

from plain_rss.config.settings import DateDisplayFormat
from plain_rss.domains.feeds.rss import FeedEntry
from plain_rss.gui.views.root_window.dates import (
    format_published,
    publication_sort_key,
)
from plain_rss.gui.views.root_window.window import RootWindow


@pytest.mark.parametrize(
    ("published", "date_display_format", "expected"),
    [
        (
            "2026-07-15T14:23:00+00:00",
            "month_day_12_hour",
            "Jul 15, 2026 2:23 PM",
        ),
        (
            "2026-07-15T14:23:00+00:00",
            "month_day_24_hour",
            "Jul 15, 2026 14:23",
        ),
        (
            "2026-07-15T14:23:00+00:00",
            "day_month_24_hour",
            "15 Jul 2026 14:23",
        ),
        ("2026-07-15T14:23:00+00:00", "iso_24_hour", "2026-07-15 14:23"),
        ("2026-07-15", "month_day_12_hour", "Jul 15, 2026"),
        (None, "month_day_12_hour", "Unknown date"),
    ],
)
def test_format_published(
    published: str | None,
    date_display_format: DateDisplayFormat,
    expected: str,
) -> None:
    """ISO values render consistently in every supported display format."""
    assert format_published(published, date_display_format) == expected


def test_publication_sort_key_orders_known_timestamps() -> None:
    """Published timestamps sort chronologically rather than by display text."""
    earlier = publication_sort_key("2026-06-01T09:00:00+00:00")
    later = publication_sort_key("2026-07-15T14:23:00+00:00")

    assert earlier < later


def test_open_selected_article_uses_desktop_url_service() -> None:
    """The Article command opens its source URL outside the preview pane."""
    entry = FeedEntry(
        feed_title="Example Feed",
        title="Example article",
        link="https://example.com/article",
        summary="",
        published=None,
    )
    article_pane = SimpleNamespace(current_entry=lambda: entry)
    window = SimpleNamespace(_articles_pane=article_pane)

    with patch(
        "plain_rss.gui.views.root_window.window.QDesktopServices.openUrl",
        return_value=True,
    ) as open_url:
        RootWindow._open_selected_article(window)  # type: ignore[arg-type]

    assert open_url.call_args.args[0].toString() == entry.link
