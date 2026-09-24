#!/usr/bin/env bash
# Downloads Tesseract English language data for the OCR layer, which screens
# hostile instructions rendered into listing photos (FR-035).
#
# Idempotent. If this is missing the backend still starts and still screens text;
# images are reported as NOT_SCREENED rather than assumed clean (FR-037).
set -euo pipefail

DEST="$(cd "$(dirname "$0")/.." && pwd)/models/tessdata"
URL="https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata"
TARGET="$DEST/eng.traineddata"
MIN_BYTES=1000000

mkdir -p "$DEST"

if [ -f "$TARGET" ]; then
  size=$(wc -c < "$TARGET" | tr -d ' ')
  if [ "$size" -ge "$MIN_BYTES" ]; then
    echo "  ok      eng.traineddata (${size} bytes, already present)"
    exit 0
  fi
fi

echo "  fetch   eng.traineddata"
curl -fSL --retry 3 -o "$TARGET" "$URL"
size=$(wc -c < "$TARGET" | tr -d ' ')
if [ "$size" -lt "$MIN_BYTES" ]; then
  echo "ERROR: eng.traineddata is only ${size} bytes." >&2
  rm -f "$TARGET"; exit 1
fi
echo "  done    eng.traineddata (${size} bytes)"
