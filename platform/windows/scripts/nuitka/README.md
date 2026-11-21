# Nuitka Builds

Two PowerShell scripts build Plain RSS with Nuitka. Both run from any folder,
use the repository's `.venv` interpreter by default, and embed
the Windows project's `assets/plain_rss.ico` as the program icon.

| Script | Output |
| --- | --- |
| `onefile.ps1` | `build/onefile/Plain RSS.exe`, a single executable |
| `standalone.ps1` | `build/standalone/main.dist/Plain RSS.exe` plus its supporting files |

```powershell
.\scripts\nuitka\onefile.ps1
.\scripts\nuitka\standalone.ps1 -ResetRuntimeDefaults
.\scripts\nuitka\onefile.ps1 -PythonExecutable C:\Python313\python.exe
```

The onefile script needs `zstandard`; install it with
`python -m pip install "nuitka[onefile]"` in the build interpreter.

## Where compiled builds keep data

Source runs keep using the repository's `data/` and `logs/` folders. Compiled
builds store them under `%LOCALAPPDATA%\plain-rss` instead, so the executable
can be copied anywhere. The folder name comes from `[tool.plain-rss]` in
`pyproject.toml`.

`-ResetRuntimeDefaults` renames that folder to a timestamped backup before
building, so the next smoke test starts with no saved subscriptions or
settings. Nothing is deleted.
