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

if ! git -C "$LLAMA_DIR" cat-file -e "$LLAMA_COMMIT^{commit}"; then
  git -C "$LLAMA_DIR" fetch --tags origin "$LLAMA_COMMIT"
fi

git -C "$LLAMA_DIR" checkout "$LLAMA_COMMIT"

if ! git -C "$LLAMA_DIR" apply --unidiff-zero --check "$PATCH_FILE" 2>/dev/null; then
  current_patch="$(mktemp)"
  trap 'rm -f "$current_patch"' EXIT
  git -C "$LLAMA_DIR" diff --unified=0 > "$current_patch"

  if git -C "$LLAMA_DIR" apply --unidiff-zero --reverse --check "$PATCH_FILE" 2>/dev/null &&
    cmp -s "$current_patch" "$PATCH_FILE"; then
    echo "Patch is already applied exactly; leaving existing llama.cpp checkout in place."
    exit 0
  fi

  echo "Patch does not apply cleanly, or local llama.cpp changes differ from $PATCH_FILE." >&2
  exit 1
fi

git -C "$LLAMA_DIR" apply --unidiff-zero "$PATCH_FILE"
echo "llama.cpp is pinned and patched at $LLAMA_COMMIT."
