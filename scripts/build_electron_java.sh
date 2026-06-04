#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="${ROOT_DIR}/build/electron-java"
CLASS_DIR="${BUILD_DIR}/classes"

mkdir -p "${CLASS_DIR}"

javac -encoding UTF-8 -d "${CLASS_DIR}" \
  "${ROOT_DIR}/Inquisitor.java"

echo "Built Java CLI classes in ${CLASS_DIR}"
