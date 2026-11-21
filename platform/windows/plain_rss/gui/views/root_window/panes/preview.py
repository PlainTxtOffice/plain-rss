"""Article preview pane for the RSS reader root window."""

from __future__ import annotations

from html import escape
from typing import TYPE_CHECKING

from PySide6.QtWidgets import QTextBrowser

from plain_rss.gui.views.root_window.dates import format_published

if TYPE_CHECKING:
    from plain_rss.config.settings import DateDisplayFormat
    from plain_rss.domains.feeds.rss import FeedEntry


class ArticlePreviewPane(QTextBrowser):
    """Display the selected article's lightweight feed preview."""

    def __init__(self, date_display_format: DateDisplayFormat) -> None:
        """Create the article preview pane."""
        super().__init__()
        self._date_display_format: DateDisplayFormat = date_display_format
        self._entry: FeedEntry | None = None
        self.setAccessibleName("Article preview")
        self.setOpenExternalLinks(True)
        self.setPlaceholderText("Select an article to preview it.")

    def display_entry(self, entry: FeedEntry) -> None:
        """Render an article using the current date-display preference."""
        self._entry = entry
        self.setHtml(_article_html(entry, self._date_display_format))

    def set_date_display_format(
        self,
        date_display_format: DateDisplayFormat,
    ) -> None:
        """Re-render the selected entry using a new date format."""
        if self._date_display_format == date_display_format:
            return
        self._date_display_format = date_display_format
        if self._entry is not None:
            self.display_entry(self._entry)

    def clear_preview(self) -> None:
        """Clear the preview when no article remains selected."""
        self._entry = None
        self.clear()


def _article_html(
    entry: FeedEntry,
    date_display_format: DateDisplayFormat,
) -> str:
    """Build readable article HTML without embedded feed images."""
    link = escape(entry.link, quote=True)
    published = format_published(entry.published, date_display_format)
    return (
        f"<h2>{escape(entry.title)}</h2>"
        f"<p><b>{escape(entry.feed_title)}</b><br>{escape(published)}</p>"
        f'<p><a href="{link}">Open original article</a></p>'
        f"<hr>{entry.summary}"
    )
