#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="${ROOT_DIR}/build/macos"
CLASS_DIR="${BUILD_DIR}/classes"
INPUT_DIR="${BUILD_DIR}/input"
DIST_DIR="${DIST_DIR:-${ROOT_DIR}/dist}"

APP_NAME="${APP_NAME:-Inquisitor}"
MAIN_CLASS="${MAIN_CLASS:-InquisitorSwingUI}"
JAR_NAME="${JAR_NAME:-Inquisitor.jar}"
ICON_PATH="${ICON_PATH:-${ROOT_DIR}/assets/Inquisitor.icns}"
APP_PATH="${DIST_DIR}/${APP_NAME}.app"

rm -rf "${BUILD_DIR}"
mkdir -p "${CLASS_DIR}" "${INPUT_DIR}" "${DIST_DIR}"
rm -rf "${APP_PATH}"

javac -d "${CLASS_DIR}" \
  "${ROOT_DIR}/Inquisitor.java" \
  "${ROOT_DIR}/InquisitorSwingUI.java"

printf 'Main-Class: %s\n' "${MAIN_CLASS}" > "${BUILD_DIR}/manifest.mf"
jar --create \
  --file "${INPUT_DIR}/${JAR_NAME}" \
  --manifest "${BUILD_DIR}/manifest.mf" \
  -C "${CLASS_DIR}" .

JPACKAGE_ARGS=(
  --type app-image
  --dest "${DIST_DIR}"
  --name "${APP_NAME}"
  --input "${INPUT_DIR}"
  --main-jar "${JAR_NAME}"
  --main-class "${MAIN_CLASS}"
)

if [[ -f "${ICON_PATH}" ]]; then
  JPACKAGE_ARGS+=(--icon "${ICON_PATH}")
fi

jpackage "${JPACKAGE_ARGS[@]}"
APP_DATA_DIR="${APP_PATH}/Contents/app"

# Bundle default course profiles and sample QA folders for Finder launches.
if [[ -f "${ROOT_DIR}/courses.properties" ]]; then
  cp "${ROOT_DIR}/courses.properties" "${APP_DATA_DIR}/courses.properties"
fi

for sample_dir in Wallace_questions Lynch_questions; do
  if [[ -d "${ROOT_DIR}/${sample_dir}" ]]; then
    rm -rf "${APP_DATA_DIR:?}/${sample_dir}"
    cp -R "${ROOT_DIR}/${sample_dir}" "${APP_DATA_DIR}/${sample_dir}"
  fi
done

echo "Built app: ${APP_PATH}"
echo "Double-click in Finder, or run: ${APP_PATH}/Contents/MacOS/${APP_NAME}"
