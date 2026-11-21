"""Tests for the per-user data folder name and its legacy migration."""

import tomllib
from pathlib import Path

from plain_rss.config import path_handler

PROJECT = Path(__file__).resolve().parents[1]


def test_app_data_dir_matches_pyproject() -> None:
    metadata = tomllib.loads(
        (PROJECT / "pyproject.toml").read_text(encoding="utf-8"),
    )

    assert (
        metadata["tool"]["plain-rss"]["app-data-dir"]
        == path_handler.DEFAULT_APP_DATA_DIR_NAME
    )


def test_app_data_dir_has_no_spaces() -> None:
    assert " " not in path_handler.DEFAULT_APP_DATA_DIR_NAME


def test_legacy_folder_is_renamed_when_only_it_exists(tmp_path: Path) -> None:
    legacy = tmp_path / "Plain RSS"
    (legacy / "data").mkdir(parents=True)
    (legacy / "data" / "subscriptions.json").write_text("[]", encoding="utf-8")
    current = tmp_path / "plain-rss"

    assert path_handler.migrate_legacy_folder(legacy, current) is True
    assert not legacy.exists()
    assert (current / "data" / "subscriptions.json").read_text(
        encoding="utf-8",
    ) == "[]"


def test_legacy_folder_never_overwrites_current_data(tmp_path: Path) -> None:
    legacy = tmp_path / "Plain RSS"
    legacy.mkdir()
    current = tmp_path / "plain-rss"
    current.mkdir()
    (current / "keep.txt").write_text("current", encoding="utf-8")

    assert path_handler.migrate_legacy_folder(legacy, current) is False
    assert legacy.is_dir()
    assert (current / "keep.txt").read_text(encoding="utf-8") == "current"


def test_missing_legacy_folder_is_ignored(tmp_path: Path) -> None:
    current = tmp_path / "plain-rss"

    assert (
        path_handler.migrate_legacy_folder(tmp_path / "Plain RSS", current)
        is False
    )
    assert not current.exists()


def test_failed_rename_keeps_the_legacy_folder(
    tmp_path: Path,
    monkeypatch,
) -> None:
    legacy = tmp_path / "Plain RSS"
    legacy.mkdir()

    def locked(self: Path, target: Path) -> Path:
        raise PermissionError("in use")

    monkeypatch.setattr(Path, "rename", locked)

    assert (
        path_handler.migrate_legacy_folder(legacy, tmp_path / "plain-rss")
        is False
    )
    assert legacy.is_dir()
