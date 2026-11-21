"""Qt application composition root."""

from __future__ import annotations

import ctypes
import sys
from typing import TYPE_CHECKING

from PySide6.QtCore import QTimer
from PySide6.QtGui import QIcon
from PySide6.QtWidgets import QApplication

from plain_rss.config.path_handler import ASSETS_DIR
from plain_rss.gui.views.root_window.window import RootWindow

if TYPE_CHECKING:
    from plain_rss.config.settings import SettingsRepository
    from plain_rss.domains.feeds.rss import FeedFetcher, SubscriptionRepository

APP_ICON_PATH = ASSETS_DIR / "plain_rss.ico"
# Windows groups taskbar buttons by this ID; without it, a source run shows
# the Python interpreter's icon instead of the app icon.
APP_USER_MODEL_ID = "PlainRSS.PlainRSS"


def run_app(
    subscription_repository: SubscriptionRepository,
    settings_repository: SettingsRepository,
    feed_service: FeedFetcher,
) -> int:
    """Create and run the desktop RSS reader."""
    if sys.platform == "win32":
        ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID(
            APP_USER_MODEL_ID,
        )
    application = QApplication([])
    application.setApplicationName("Plain RSS")
    application.setWindowIcon(QIcon(str(APP_ICON_PATH)))
    window = RootWindow(
        subscription_repository,
        settings_repository,
        feed_service,
    )
    window.show()
    QTimer.singleShot(0, window.refresh_feeds)
    return application.exec()
