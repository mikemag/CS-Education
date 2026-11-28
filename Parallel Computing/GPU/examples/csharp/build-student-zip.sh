#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
archive=build/ILGPU_Verify.zip
mkdir -p build
temp_zip=$(mktemp build/ILGPU_Verify.zip.XXXXXX)
trap 'rm -f "$temp_zip"' EXIT

git ls-files --cached --others --exclude-standard -- ILGPU_Verify |
  zip -q -X -MM - -@ > "$temp_zip"
unzip -tq "$temp_zip"
mv -f "$temp_zip" "$archive"
echo "Built $archive"
