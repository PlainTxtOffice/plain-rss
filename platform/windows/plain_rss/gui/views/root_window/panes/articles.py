"""Article list pane for the RSS reader root window."""

from __future__ import annotations

from typing import override

from PySide6.QtCore import (
    QModelIndex,
    QPersistentModelIndex,
    QSignalBlocker,
    Qt,
    Signal,
)
from PySide6.QtWidgets import QAbstractItemView, QTreeWidget, QTreeWidgetItem

from plain_rss.config.settings import (
    ARTICLE_COLUMNS,
    ArticleListSettings,
    DateDisplayFormat,
    SortOrder,
)
from plain_rss.domains.feeds.rss import FeedEntry
from plain_rss.gui.views.root_window.dates import (
    format_published,
    publication_sort_key,
)

_PUBLISHED_COLUMN = ARTICLE_COLUMNS.index("published")


class _ArticleItem(QTreeWidgetItem):
    """Sort published entries by their normalized ISO date."""

    def __lt__(self, other: QTreeWidgetItem) -> bool:
        """Compare ISO publication values when sorting the Published column."""
        article_list = self.treeWidget()
        if (
            article_list is not None
            and article_list.sortColumn() == _PUBLISHED_COLUMN
        ):
            published = self.data(
                _PUBLISHED_COLUMN,
                Qt.ItemDataRole.UserRole,
            )
            other_published = other.data(
                _PUBLISHED_COLUMN,
                Qt.ItemDataRole.UserRole,
            )
            if (published is None or isinstance(published, str)) and (
                other_published is None or isinstance(other_published, str)
            ):
                return publication_sort_key(published) < publication_sort_key(
                    other_published,
                )
        return super().__lt__(other)


class ArticleListPane(QTreeWidget):
    """Display articles with persistent layout and sorting preferences."""

    preferences_changed = Signal()

    def __init__(
        self,
        article_list_settings: ArticleListSettings,
        date_display_format: DateDisplayFormat,
    ) -> None:
        """Create the article list pane with saved display preferences."""
        super().__init__()
        self._date_display_format: DateDisplayFormat = date_display_format
        self._entries: tuple[FeedEntry, ...] = ()

        self.setAccessibleName("Articles")
        self.setHeaderLabels(["Article", "Published"])
        self.setRootIsDecorated(False)
        self.setSelectionBehavior(
            QAbstractItemView.SelectionBehavior.SelectRows,
        )
        self.setColumnWidth(0, 420)
        self.setSortingEnabled(True)

        article_header = self.header()
        article_header.setSectionsMovable(True)
        article_header.setFirstSectionMovable(True)
        article_header.setSectionsClickable(True)
        article_header.setSortIndicatorShown(True)
        self._apply_settings(article_list_settings)
        article_header.sectionMoved.connect(self._columns_moved)
        article_header.sortIndicatorChanged.connect(self._sort_changed)

    def set_entries(self, entries: tuple[FeedEntry, ...]) -> None:
        """Render the feed entries while retaining the selected article."""
        selected_entry = self.current_entry()
        self._entries = entries
        sorting_enabled = self.isSortingEnabled()
        self.setSortingEnabled(False)
        self.clear()
        selected_item: QTreeWidgetItem | None = None
        for entry in entries:
            item = _ArticleItem(
                [
                    entry.title,
                    format_published(
                        entry.published,
                        self._date_display_format,
                    ),
                ],
            )
            item.setData(0, Qt.ItemDataRole.UserRole, entry)
            item.setData(1, Qt.ItemDataRole.UserRole, entry.published)
            self.addTopLevelItem(item)
            if entry == selected_entry:
                selected_item = item
        self.setSortingEnabled(sorting_enabled)
        if sorting_enabled:
            self.sortItems(
                self.sortColumn(),
                self.header().sortIndicatorOrder(),
            )
        if selected_item is not None:
            self.setCurrentItem(selected_item)

    def set_date_display_format(
        self,
        date_display_format: DateDisplayFormat,
    ) -> None:
        """Update the displayed dates without changing entry data."""
        if self._date_display_format == date_display_format:
            return
        self._date_display_format = date_display_format
        self.set_entries(self._entries)

    @override
    def scrollTo(
        self,
        index: QModelIndex | QPersistentModelIndex,
        hint: QAbstractItemView.ScrollHint = (
            QAbstractItemView.ScrollHint.EnsureVisible
        ),
    ) -> None:
        """Keep the horizontal viewport stable when Qt selects an article."""
        horizontal_position = self.horizontalScrollBar().value()
        super().scrollTo(index, hint)
        self.horizontalScrollBar().setValue(horizontal_position)

    def current_entry(self) -> FeedEntry | None:
        """Return the currently selected article entry."""
        current = self.currentItem()
        if current is None:
            return None
        entry = current.data(0, Qt.ItemDataRole.UserRole)
        return entry if isinstance(entry, FeedEntry) else None

    def article_list_settings(self) -> ArticleListSettings:
        """Return the current Article list layout and sorting preferences."""
        article_header = self.header()
        column_order = tuple(
            ARTICLE_COLUMNS[article_header.logicalIndex(visual_index)]
            for visual_index in range(article_header.count())
        )
        sort_column = ARTICLE_COLUMNS[article_header.sortIndicatorSection()]
        sort_order = _to_sort_order(article_header.sortIndicatorOrder())
        return ArticleListSettings(
            column_order=column_order,
            sort_column=sort_column,
            sort_order=sort_order,
        )

    def _apply_settings(self, settings: ArticleListSettings) -> None:
        """Apply saved layout and sort settings without emitting changes."""
        article_header = self.header()
        signal_blocker = QSignalBlocker(article_header)
        for visual_index, column_name in enumerate(settings.column_order):
            logical_index = ARTICLE_COLUMNS.index(column_name)
            article_header.moveSection(
                article_header.visualIndex(logical_index),
                visual_index,
            )
        sort_column = ARTICLE_COLUMNS.index(settings.sort_column)
        sort_order = _to_qt_sort_order(settings.sort_order)
        article_header.setSortIndicator(sort_column, sort_order)
        signal_blocker.unblock()
        self.sortItems(sort_column, sort_order)

    def _columns_moved(
        self,
        _logical_index: int,
        _old_visual_index: int,
        _new_visual_index: int,
    ) -> None:
        """Notify the root window when the user reorders columns."""
        self.preferences_changed.emit()

    def _sort_changed(
        self,
        _logical_index: int,
        _sort_order: Qt.SortOrder,
    ) -> None:
        """Notify the root window when the user changes sorting."""
        self.preferences_changed.emit()


def _to_qt_sort_order(sort_order: SortOrder) -> Qt.SortOrder:
    """Map a persisted sort direction to Qt's corresponding enum."""
    if sort_order == "ascending":
        return Qt.SortOrder.AscendingOrder
    return Qt.SortOrder.DescendingOrder


def _to_sort_order(sort_order: Qt.SortOrder) -> SortOrder:
    """Map Qt's sort enum to a persisted sort direction."""
    if sort_order == Qt.SortOrder.AscendingOrder:
        return "ascending"
    return "descending"
