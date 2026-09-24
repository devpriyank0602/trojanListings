#!/usr/bin/env bash
# Downloads the prompt-injection classifier used by the detector's classifier layer.
#
# Model: Horizon-Labs/prompt-injection-guard-small (Apache-2.0)
# Chosen because it is trained on realistic documents with planted injections across
# 45 document types -- which is our threat shape -- rather than on user-typed prompts.
# See specs/001-listing-injection-defense/research.md section 1.1.
#
# NO CREDENTIALS ARE REQUIRED. The repo is public and ungated (gated:false,
# private:false) and this script sends no Authorization header. Nothing in the
# backend reads a HuggingFace token either -- ModelLoader opens two local files.
# A failure here is therefore always a network or transfer problem, never an
# authentication one. Do not add token handling to "fix" a download failure.
#
# Idempotent: skips files already present and intact.
set -euo pipefail

REPO="Horizon-Labs/prompt-injection-guard-small"
DEST="$(cd "$(dirname "$0")/.." && pwd)/models/prompt-injection-guard-small"
BASE="https://huggingface.co/${REPO}/resolve/main"

MAX_ATTEMPTS=8

mkdir -p "$DEST"

# HuggingFace reports the authoritative size and SHA-256 of the real blob in the
# headers of the resolve endpoint, before any body is transferred:
#   x-linked-size: 268409234
#   x-linked-etag: "541471ab...dae02"
# Reading them here means the resume loop has a real target and the integrity
# check is a genuine hash rather than a size guess -- and neither goes stale when
# upstream republishes the model.
preflight() {
  local remote="$1"
  expected_size=""
  expected_hash=""
  local headers
  headers=$(curl -sIL --max-time 60 "${BASE}/${remote}" 2>/dev/null) || return 0
  expected_size=$(printf '%s' "$headers" | awk 'tolower($1)=="x-linked-size:"{print $2}' | tr -d '\r' | tail -1)
  expected_hash=$(printf '%s' "$headers" | awk 'tolower($1)=="x-linked-etag:"{print $2}' | tr -d '\r"' | tail -1)
}

sha256_of() {
  shasum -a 256 "$1" | cut -d' ' -f1
}

fetch() {
  local remote="$1" local_name="$2" min_bytes="$3"
  local target="$DEST/$local_name"

  preflight "$remote"
  local want_size="${expected_size:-$min_bytes}"
  local want_hash="$expected_hash"

  if [ -f "$target" ]; then
    local size; size=$(wc -c < "$target" | tr -d ' ')
    if [ "$size" -ge "$want_size" ]; then
      if [ -z "$want_hash" ] || [ "$(sha256_of "$target")" = "$want_hash" ]; then
        echo "  ok      $local_name (${size} bytes, already present)"
        return 0
      fi
      echo "  bad     $local_name checksum mismatch, refetching from scratch"
      rm -f "$target"
    else
      echo "  partial $local_name (${size}/${want_size} bytes), resuming"
    fi
  fi

  echo "  fetch   $local_name (${want_size} bytes expected)"
  local attempt=1
  while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
    local have=0
    [ -f "$target" ] && have=$(wc -c < "$target" | tr -d ' ')
    [ "$have" -ge "$want_size" ] && break

    # -C -             resume from wherever the last attempt died
    # --retry-all-errors  retry on transient transport errors, not just HTTP ones
    # --speed-limit/time  abort a socket that stalls below 10 KB/s for 60s, so the
    #                     loop can resume it rather than the whole attempt dying
    #                     slowly (observed: a transfer decaying to ~3 KB/s and
    #                     failing at 222 MB of 256 MB)
    curl -fSL -C - --retry 5 --retry-all-errors --retry-delay 3 \
         --speed-limit 10000 --speed-time 60 \
         -o "$target" "${BASE}/${remote}" && break

    echo "  retry   $local_name (attempt ${attempt}/${MAX_ATTEMPTS} interrupted)"
    attempt=$((attempt + 1))
  done

  if [ ! -f "$target" ]; then
    echo "ERROR: $local_name could not be downloaded at all." >&2
    exit 1
  fi

  local size; size=$(wc -c < "$target" | tr -d ' ')
  if [ "$size" -lt "$want_size" ]; then
    echo "ERROR: $local_name is ${size} bytes, expected ${want_size} after ${MAX_ATTEMPTS} attempts." >&2
    echo "       The partial file is left in place so a later run can resume it." >&2
    exit 1
  fi

  if [ -n "$want_hash" ]; then
    local got; got=$(sha256_of "$target")
    if [ "$got" != "$want_hash" ]; then
      echo "ERROR: $local_name failed checksum verification." >&2
      echo "       expected $want_hash" >&2
      echo "       actual   $got" >&2
      rm -f "$target"
      exit 1
    fi
    echo "  done    $local_name (${size} bytes, sha256 verified)"
  else
    echo "  done    $local_name (${size} bytes, no upstream hash to verify against)"
  fi
}

echo "Downloading ${REPO} -> ${DEST}"
fetch "onnx/model_quantized.onnx" "model_quantized.onnx" 10000000
fetch "tokenizer.json"            "tokenizer.json"          100000

echo
echo "Model ready. The backend loads it once at startup."
echo "If this fails, the service still starts and screens in degraded mode (FR-030)."
echo
echo "NOTE: on a restricted corporate network the huggingface.co CDN blob endpoint may"
echo "      return HTTP 403 even though the metadata API responds. That is egress"
echo "      filtering, not a missing credential -- this model needs none. If it happens,"
echo "      fetch the two files from an unrestricted machine and drop them into:"
echo "        models/prompt-injection-guard-small/{model_quantized.onnx,tokenizer.json}"
echo "      Screening works without them -- structural and pattern layers alone caught"
echo "      100% of text fixtures in the evaluation set."
