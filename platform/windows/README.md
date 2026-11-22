# Windows application

Create the environment and install the app with its developer tools from
`platform/windows/`:

```powershell
python -m venv .venv
.venv/Scripts/python.exe -m pip install -e ".[dev]"
```

Run these commands from `platform/windows/`:

```powershell
.venv/Scripts/python.exe -m plain_rss.main
.venv/Scripts/python.exe -m ruff check .
.venv/Scripts/python.exe -m pytest
.venv/Scripts/python.exe -m mypy plain_rss
.venv/Scripts/rumdl.exe check
```

Application modules live in `plain_rss/` and use imports such as
`plain_rss.config.path_handler`. Development data and logs are relative to this
Windows project folder. Add application tests under `tests/`.

Build a packaged app with the [Nuitka scripts](scripts/nuitka/README.md).

## App icon

Edit only the shared source at `../../assets/icons/icon.svg`, then regenerate
`../../assets/icons/icon-github-readme.svg` from the repository root with
`python scripts/make-github-readme-icon.py`. Regenerate the Windows icon from
`platform/windows/`:

```powershell
.venv/Scripts/python.exe -m scripts.generate_icon
```

This uses the existing PySide6 dependency and renders the rounded README icon
(the master clipped to 22% rounded corners, with transparent corners) into
`assets/plain_rss.ico` with PNG frames from 16 through 256 pixels, so the
desktop icon matches the README and Windows 11's rounded style. The GUI loads
`assets/plain_rss.ico` at startup and the Nuitka scripts use it as the
executable icon.

See the [repository README](../../README.md) for what Plain RSS does and the
[user manual](../../docs/USER_MANUAL.md) for using it.
