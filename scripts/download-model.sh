#!/usr/bin/env bash
# Downloads the prompt-injection classifier used by the detector's classifier layer.
#
# Model: Horizon-Labs/prompt-injection-guard-small (Apache-2.0)
# Chosen because it is trained on realistic documents with planted injections across
# 45 document types -- which is our threat shape -- rather than on user-typed prompts.
# See specs/001-listing-injection-defense/research.md section 1.1.
#
# Idempotent: skips files already present with a plausible size.
set -euo pipefail

REPO="Horizon-Labs/prompt-injection-guard-small"
DEST="$(cd "$(dirname "$0")/.." && pwd)/models/prompt-injection-guard-small"
BASE="https://huggingface.co/${REPO}/resolve/main"

mkdir -p "$DEST"

fetch() {
  local remote="$1" local_name="$2" min_bytes="$3"
  local target="$DEST/$local_name"
  if [ -f "$target" ]; then
    local size; size=$(wc -c < "$target" | tr -d ' ')
    if [ "$size" -ge "$min_bytes" ]; then
      echo "  ok      $local_name (${size} bytes, already present)"
      return 0
    fi
    echo "  stale   $local_name (${size} bytes < ${min_bytes}), re-downloading"
  fi
  echo "  fetch   $local_name"
  curl -fSL --retry 3 -o "$target" "${BASE}/${remote}"
  local size; size=$(wc -c < "$target" | tr -d ' ')
  if [ "$size" -lt "$min_bytes" ]; then
    echo "ERROR: $local_name is only ${size} bytes (expected >= ${min_bytes})." >&2
    echo "       The download likely returned an error page rather than the file." >&2
    rm -f "$target"; exit 1
  fi
  echo "  done    $local_name (${size} bytes)"
}

echo "Downloading ${REPO} -> ${DEST}"
fetch "onnx/model_quantized.onnx" "model_quantized.onnx" 10000000
fetch "tokenizer.json"            "tokenizer.json"          100000

echo
echo "Model ready. The backend loads it once at startup."
echo "If this fails, the service still starts and screens in degraded mode (FR-030)."
echo
echo "NOTE: on a restricted corporate network the huggingface.co CDN blob endpoint may"
echo "      return HTTP 403 even though the metadata API responds. If that happens,"
echo "      fetch the two files from an unrestricted machine and drop them into:"
echo "        models/prompt-injection-guard-small/{model_quantized.onnx,tokenizer.json}"
echo "      Screening works without them -- structural and pattern layers alone caught"
echo "      100% of text fixtures in the evaluation set."
