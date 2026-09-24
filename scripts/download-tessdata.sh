#!/usr/bin/env bash
# Downloads Tesseract English language data for the OCR layer, which screens
# hostile instructions rendered into listing photos (FR-035).
#
# No credentials required -- this is a public GitHub raw URL.
#
# Idempotent. If this is missing the backend still starts and still screens text;
# images are reported as NOT_SCREENED rather than assumed clean (FR-037).
set -euo pipefail

DEST="$(cd "$(dirname "$0")/.." && pwd)/models/tessdata"
URL="https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata"
TARGET="$DEST/eng.traineddata"
MIN_BYTES=1000000
MAX_ATTEMPTS=5

mkdir -p "$DEST"

# GitHub redirects to a CDN that reports the real size up front. Use it as the
# resume target when available; fall back to the MIN_BYTES floor when it isn't.
# `|| true` matters: under `set -o pipefail` a timed-out preflight would otherwise
# abort the whole script, even though the fallback below handles it fine.
expected_size=$( { curl -sIL --max-time 30 "$URL" 2>/dev/null || true; } \
  | awk 'tolower($1)=="content-length:"{print $2}' | tr -d '\r' | tail -1)
want_size="${expected_size:-$MIN_BYTES}"

if [ -f "$TARGET" ]; then
  size=$(wc -c < "$TARGET" | tr -d ' ')
  if [ "$size" -ge "$want_size" ]; then
    echo "  ok      eng.traineddata (${size} bytes, already present)"
    exit 0
  fi
  echo "  partial eng.traineddata (${size}/${want_size} bytes), resuming"
fi

echo "  fetch   eng.traineddata"
attempt=1
while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
  have=0
  [ -f "$TARGET" ] && have=$(wc -c < "$TARGET" | tr -d ' ')
  [ "$have" -ge "$want_size" ] && break

  curl -fSL -C - --retry 5 --retry-all-errors --retry-delay 3 \
       --speed-limit 5000 --speed-time 60 \
       -o "$TARGET" "$URL" && break

  echo "  retry   eng.traineddata (attempt ${attempt}/${MAX_ATTEMPTS} interrupted)"
  attempt=$((attempt + 1))
done

if [ ! -f "$TARGET" ]; then
  echo "ERROR: eng.traineddata could not be downloaded." >&2
  exit 1
fi

size=$(wc -c < "$TARGET" | tr -d ' ')
if [ "$size" -lt "$want_size" ]; then
  echo "ERROR: eng.traineddata is ${size} bytes, expected ${want_size}." >&2
  exit 1
fi
echo "  done    eng.traineddata (${size} bytes)"
