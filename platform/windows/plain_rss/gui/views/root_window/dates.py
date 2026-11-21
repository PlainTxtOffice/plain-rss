"""Date formatting shared by root-window panes."""

from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from plain_rss.config.settings import DateDisplayFormat

UNKNOWN_PUBLICATION = "Unknown date"
ISO_DATE_LENGTH = 10


def format_published(
    published: str | None,
    date_display_format: DateDisplayFormat,
) -> str:
    """Format an ISO publication value for a user-facing pane."""
    if published is None:
        return UNKNOWN_PUBLICATION
    if len(published) == ISO_DATE_LENGTH:
        return _format_date_only(published, date_display_format)

    try:
        timestamp = datetime.fromisoformat(published)
    except ValueError:
        return UNKNOWN_PUBLICATION
    return _format_timestamp(timestamp, date_display_format)


def publication_sort_key(published: str | None) -> tuple[int, str]:
    """Return a chronological sort key for normalized publication values."""
    if published is None:
        return (0, "")
    return (1, published)


def _format_date_only(
    published: str,
    date_display_format: DateDisplayFormat,
) -> str:
    """Format an ISO date that does not include a time."""
    if date_display_format == "iso_24_hour":
        return published

    timestamp = datetime.fromisoformat(published)
    if date_display_format == "day_month_24_hour":
        return f"{timestamp.day} {timestamp:%b} {timestamp:%Y}"
    return f"{timestamp:%b} {timestamp.day}, {timestamp:%Y}"


def _format_timestamp(
    timestamp: datetime,
    date_display_format: DateDisplayFormat,
) -> str:
    """Format an ISO timestamp using a supported display preference."""
    if date_display_format == "month_day_24_hour":
        return (
            f"{timestamp:%b} {timestamp.day}, {timestamp:%Y} {timestamp:%H:%M}"
        )
    if date_display_format == "day_month_24_hour":
        return (
            f"{timestamp.day} {timestamp:%b} {timestamp:%Y} {timestamp:%H:%M}"
        )
    if date_display_format == "iso_24_hour":
        return f"{timestamp:%Y-%m-%d %H:%M}"

    hours_per_half_day = 12
    hour = timestamp.hour % hours_per_half_day or hours_per_half_day
    period = "AM" if timestamp.hour < hours_per_half_day else "PM"
    return (
        f"{timestamp:%b} {timestamp.day}, {timestamp:%Y} "
        f"{hour}:{timestamp:%M} {period}"
    )
