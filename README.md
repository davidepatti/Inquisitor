# Inquisitor

## macOS launcher

Build a self-contained `.app` (bundled runtime):

```bash
./scripts/build_macos_app.sh
```

This creates:

- `dist/Inquisitor.app`

Run from shell:

```bash
./scripts/launch_inquisitor.sh
```

Or auto-build + run:

```bash
./scripts/inquisitor
```

Open as a clickable app icon from Finder:

```bash
open dist/Inquisitor.app
```

Optional env vars:

- `APP_NAME` (default: `Inquisitor`)
- `DIST_DIR` (default: `./dist`)
- `ICON_PATH` (default: `./assets/Inquisitor.icns` if present)
