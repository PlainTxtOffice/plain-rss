"""Tests for persistent reader preferences."""

from pathlib import Path

from plain_rss.config.settings import (
    ArticleListSettings,
    ReaderSettings,
    SettingsRepository,
)


def test_settings_repository_round_trips(tmp_path: Path) -> None:
    """Saved article-list preferences are available at the next launch."""
    settings_path = tmp_path / "settings.json"
    repository = SettingsRepository(settings_path)
    settings = ReaderSettings(
        article_list=ArticleListSettings(
            column_order=("published", "article"),
            sort_column="article",
            sort_order="ascending",
        ),
        date_display_format="iso_24_hour",
    )

    repository.save(settings)

    assert repository.load() == settings
    assert not list(tmp_path.glob(".settings.json.*.tmp"))


def test_settings_repository_uses_defaults_for_invalid_values(
    tmp_path: Path,
) -> None:
    """Invalid persisted preferences cannot prevent the reader from opening."""
    settings_path = tmp_path / "settings.json"
    settings_path.write_text(
        """{
        "schema_version": 1,
        "article_list": {
            "column_order": ["unknown", "article"],
            "sort_column": "unknown",
            "sort_order": "sideways"
        },
        "date_display_format": "sideways"
        }""",
        encoding="utf-8",
    )

    settings = SettingsRepository(settings_path).load()

    assert settings.article_list == ArticleListSettings()


def test_settings_repository_retains_valid_date_format_without_article_list(
    tmp_path: Path,
) -> None:
    """A malformed layout section cannot discard a valid date preference."""
    settings_path = tmp_path / "settings.json"
    settings_path.write_text(
        """{
        "schema_version": 1,
        "article_list": "invalid",
        "date_display_format": "iso_24_hour"
        }""",
        encoding="utf-8",
    )

    settings = SettingsRepository(settings_path).load()

    assert settings.article_list == ArticleListSettings()
    assert settings.date_display_format == "iso_24_hour"
