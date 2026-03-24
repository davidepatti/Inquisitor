#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_NAME="${APP_NAME:-Inquisitor}"
APP_PATH="${APP_PATH:-${ROOT_DIR}/dist/${APP_NAME}.app}"
APP_BIN="${APP_PATH}/Contents/MacOS/${APP_NAME}"

if [[ ! -x "${APP_BIN}" ]]; then
  echo "App binary not found: ${APP_BIN}" >&2
  echo "Build it first with: ${ROOT_DIR}/scripts/build_macos_app.sh" >&2
  exit 1
fi

if [[ "${1:-}" == "--open" ]]; then
  open "${APP_PATH}"
  exit 0
fi

exec "${APP_BIN}" "$@"
