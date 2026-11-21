"""Static/base path constants for development and packaged builds."""

from __future__ import annotations

import os
import sys
import tomllib
from pathlib import Path

IS_COMPILED = bool(
    globals().get("__compiled__", False) or getattr(sys, "frozen", False),
)
# The repository root in development. In compiled builds this is the bundle
# root, where Nuitka places included data files such as assets/.
DEV_ROOT = Path(__file__).resolve().parents[2]
PYPROJECT_PATH = DEV_ROOT / "pyproject.toml"
DEFAULT_APP_DATA_DIR_NAME = "plain-rss"
# Builds before 2026-10-28 used this folder name; compiled builds rename it.
LEGACY_APP_DATA_DIR_NAME = "Plain RSS"


def _app_data_dir_name() -> str:
    """Return the per-user data folder name from pyproject.toml if present."""
    try:
        with PYPROJECT_PATH.open("rb") as pyproject_file:
            pyproject = tomllib.load(pyproject_file)
    except (OSError, tomllib.TOMLDecodeError):
        return DEFAULT_APP_DATA_DIR_NAME
    tool_settings = pyproject.get("tool", {}).get("plain-rss", {})
    return str(tool_settings.get("app-data-dir", DEFAULT_APP_DATA_DIR_NAME))


def migrate_legacy_folder(legacy: Path, current: Path) -> bool:
    """Rename a data folder left under an earlier name to the current name.

    - Moves only when the legacy folder exists and the current one does not,
      so it never merges or overwrites data.
    - Leaves the legacy folder in place when the rename fails, and the app
      then starts with a new folder.
    """
    if current.exists() or not legacy.is_dir():
        return False
    try:
        legacy.rename(current)
    except OSError:
        return False
    return True


# Compiled builds keep writable data per user so the executable can live
# anywhere; source runs keep using the repository's data/ and logs/ folders.
USER_DATA_DIR = (
    Path(os.environ.get("LOCALAPPDATA", Path.home() / "AppData" / "Local"))
    / _app_data_dir_name()
)
if IS_COMPILED:
    migrate_legacy_folder(
        USER_DATA_DIR.with_name(LEGACY_APP_DATA_DIR_NAME),
        USER_DATA_DIR,
    )

ROOT_DIR = USER_DATA_DIR if IS_COMPILED else DEV_ROOT
ASSETS_DIR = DEV_ROOT / "assets"
LOGS_DIR = ROOT_DIR / "logs"
EXPORTS_DIR = ROOT_DIR / "exports"
BACKUPS_DIR = EXPORTS_DIR / "backups"
DATA_DIR = ROOT_DIR / "data"
CACHE_DIR = DATA_DIR / "cache"
