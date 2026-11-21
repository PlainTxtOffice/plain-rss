"""Main program entry point."""

from __future__ import annotations

from plain_rss.config.logging.log_config import configure_logging
from plain_rss.config.path_handler import DATA_DIR
from plain_rss.config.settings import SettingsRepository
from plain_rss.domains.feeds.rss import FeedFetcher, SubscriptionRepository
from plain_rss.gui.app import run_app


def main() -> int:
    """Configure application services and start the desktop reader."""
    configure_logging(project_name="rss_reader")
    subscription_repository = SubscriptionRepository(
        DATA_DIR / "subscriptions.json",
    )
    settings_repository = SettingsRepository(DATA_DIR / "settings.json")
    feed_service = FeedFetcher()
    return run_app(
        subscription_repository,
        settings_repository,
        feed_service,
    )


if __name__ == "__main__":
    raise SystemExit(main())
