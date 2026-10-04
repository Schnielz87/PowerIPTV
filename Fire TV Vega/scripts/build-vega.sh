#!/usr/bin/env bash
# Baut das Fire-TV-Paket (.vpkg, armv7) – im CI mit installiertem Vega SDK (siehe .github/workflows).
set -euo pipefail
build_number="${BUILD_NUMBER:-1}"
exec npx react-native build-vega \
  --build-type Release \
  --target armv7 \
  --build-version "1.1.${build_number}" \
  --build-number "${build_number}"
