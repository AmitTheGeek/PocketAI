#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LLAMA_DIR="$ROOT_DIR/work/llama.cpp"
LLAMA_COMMIT="1537a0a8b2f8711d840878b0a0677ab2213c882c"
LLAMA_URL="https://github.com/ggml-org/llama.cpp.git"
PATCH_FILE="$ROOT_DIR/patches/llama-cpp-pocketai.patch"

mkdir -p "$ROOT_DIR/work"

if [ ! -d "$LLAMA_DIR/.git" ]; then
  git clone "$LLAMA_URL" "$LLAMA_DIR"
fi

git -C "$LLAMA_DIR" fetch --tags origin "$LLAMA_COMMIT"
git -C "$LLAMA_DIR" checkout "$LLAMA_COMMIT"

if ! git -C "$LLAMA_DIR" apply --check "$PATCH_FILE"; then
  if git -C "$LLAMA_DIR" diff --quiet; then
    echo "Patch does not apply cleanly and no local diff is present." >&2
    exit 1
  fi
  echo "Patch already appears to be applied; leaving existing llama.cpp checkout in place."
  exit 0
fi

git -C "$LLAMA_DIR" apply "$PATCH_FILE"
echo "llama.cpp is pinned and patched at $LLAMA_COMMIT."
