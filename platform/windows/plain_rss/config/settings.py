"""Persistent user preferences for the RSS reader."""

from __future__ import annotations

import json
import logging
import os
from dataclasses import asdict, dataclass, field
from pathlib import Path
from tempfile import NamedTemporaryFile
from typing import Final, Literal

LOGGER = logging.getLogger(__name__)
SETTINGS_SCHEMA_VERSION: Final = 1
ARTICLE_COLUMNS: Final = ("article", "published")
DATE_DISPLAY_FORMATS: Final = (
    "month_day_12_hour",
    "month_day_24_hour",
    "day_month_24_hour",
    "iso_24_hour",
)
type SortOrder = Literal["ascending", "descending"]
type DateDisplayFormat = Literal[
    "month_day_12_hour",
    "month_day_24_hour",
    "day_month_24_hour",
    "iso_24_hour",
]


@dataclass(frozen=True, slots=True)
class ArticleListSettings:
    """Store the Article list pane's layout preferences."""

    column_order: tuple[str, ...] = ARTICLE_COLUMNS
    sort_column: str = "published"
    sort_order: SortOrder = "descending"


@dataclass(frozen=True, slots=True)
class ReaderSettings:
    """Store versioned user preferences for the RSS reader."""

    schema_version: int = SETTINGS_SCHEMA_VERSION
    article_list: ArticleListSettings = field(
        default_factory=ArticleListSettings,
    )
    date_display_format: DateDisplayFormat = "month_day_12_hour"


class SettingsRepository:
    """Load and save reader preferences in a local JSON file."""

    def __init__(self, path: Path) -> None:
        """Initialize the repository at ``path``."""
        self._path = path

    def load(self) -> ReaderSettings:
        """Load valid preferences, falling back to defaults when needed."""
        if not self._path.exists():
            return ReaderSettings()

        try:
            raw_settings = json.loads(self._path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            LOGGER.warning("Could not load settings: %s", error)
            return ReaderSettings()
        return _parse_settings(raw_settings)

    def save(self, settings: ReaderSettings) -> None:
        """Write preferences atomically to disk."""
        self._path.parent.mkdir(parents=True, exist_ok=True)
        contents = json.dumps(asdict(settings), indent=2, sort_keys=True)
        temporary_path = _write_temporary_contents(self._path, contents)
        try:
            temporary_path.replace(self._path)
        except OSError:
            temporary_path.unlink(missing_ok=True)
            raise


def _parse_settings(raw_settings: object) -> ReaderSettings:
    """Return validated settings from decoded JSON data."""
    if not isinstance(raw_settings, dict):
        LOGGER.warning("Settings file did not contain an object.")
        return ReaderSettings()

    schema_version = raw_settings.get("schema_version")
    if schema_version != SETTINGS_SCHEMA_VERSION:
        LOGGER.warning("Settings file uses an unsupported schema version.")
        return ReaderSettings()

    raw_article_list = raw_settings.get("article_list")
    if not isinstance(raw_article_list, dict):
        raw_article_list = {}

    return ReaderSettings(
        article_list=ArticleListSettings(
            column_order=_normalize_column_order(
                raw_article_list.get("column_order"),
            ),
            sort_column=_normalize_sort_column(
                raw_article_list.get("sort_column"),
            ),
            sort_order=_normalize_sort_order(
                raw_article_list.get("sort_order"),
            ),
        ),
        date_display_format=normalize_date_display_format(
            raw_settings.get("date_display_format"),
        ),
    )


def _normalize_column_order(raw_order: object) -> tuple[str, ...]:
    """Return the known article columns in a complete visual order."""
    if not isinstance(raw_order, list):
        return ARTICLE_COLUMNS

    column_order = tuple(
        column
        for column in raw_order
        if isinstance(column, str) and column in ARTICLE_COLUMNS
    )
    if len(column_order) != len(set(column_order)):
        return ARTICLE_COLUMNS

    return column_order + tuple(
        column for column in ARTICLE_COLUMNS if column not in column_order
    )


def _normalize_sort_column(raw_column: object) -> str:
    """Return a known article-list column for sorting."""
    if isinstance(raw_column, str) and raw_column in ARTICLE_COLUMNS:
        return raw_column
    return ArticleListSettings().sort_column


def _normalize_sort_order(raw_order: object) -> SortOrder:
    """Return a supported article-list sort direction."""
    if raw_order == "ascending":
        return "ascending"
    if raw_order == "descending":
        return "descending"
    return ArticleListSettings().sort_order


def normalize_date_display_format(raw_format: object) -> DateDisplayFormat:
    """Return a supported user-facing publication date format."""
    if raw_format == "month_day_12_hour":
        return "month_day_12_hour"
    if raw_format == "month_day_24_hour":
        return "month_day_24_hour"
    if raw_format == "day_month_24_hour":
        return "day_month_24_hour"
    if raw_format == "iso_24_hour":
        return "iso_24_hour"
    return ReaderSettings().date_display_format


def _write_temporary_contents(path: Path, contents: str) -> Path:
    """Write and flush JSON beside its final destination."""
    with NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        dir=path.parent,
        prefix=f".{path.name}.",
        suffix=".tmp",
        delete=False,
    ) as temporary_file:
        temporary_path = Path(temporary_file.name)
        temporary_file.write(f"{contents}\n")
        temporary_file.flush()
        os.fsync(temporary_file.fileno())
    return temporary_path
