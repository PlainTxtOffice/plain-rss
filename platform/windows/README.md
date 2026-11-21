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

See the [repository README](../../README.md) for what Plain RSS does and the
[user manual](../../docs/USER_MANUAL.md) for using it.
