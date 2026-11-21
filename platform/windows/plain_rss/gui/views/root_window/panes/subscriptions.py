"""Subscription pane for the RSS reader root window."""

from __future__ import annotations

from typing import TYPE_CHECKING

from PySide6.QtCore import QSignalBlocker, Qt
from PySide6.QtWidgets import QListWidget, QListWidgetItem

if TYPE_CHECKING:
    from plain_rss.domains.feeds.rss import Subscription


class SubscriptionListPane(QListWidget):
    """Display and select saved feed subscriptions."""

    def __init__(self) -> None:
        """Create the subscription list pane."""
        super().__init__()
        self.setAccessibleName("Subscriptions")

    def render_subscriptions(
        self,
        subscriptions: list[Subscription],
        selected_url: str | None,
    ) -> QListWidgetItem | None:
        """Render subscriptions and return the retained selection item."""
        signal_blocker = QSignalBlocker(self)
        self.clear()
        selected_item: QListWidgetItem | None = None
        for subscription in subscriptions:
            item = QListWidgetItem(subscription.title)
            item.setData(Qt.ItemDataRole.UserRole, subscription.url)
            self.addItem(item)
            if subscription.url == selected_url:
                selected_item = item
        if selected_item is not None:
            self.setCurrentItem(selected_item)
        signal_blocker.unblock()
        return selected_item

    def selected_url(self) -> str | None:
        """Return the URL of the currently selected subscription."""
        current = self.currentItem()
        if current is None:
            return None
        url = current.data(Qt.ItemDataRole.UserRole)
        return url if isinstance(url, str) else None
