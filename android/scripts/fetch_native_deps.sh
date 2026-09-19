#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/third_party"

LLAMA_COMMIT="5b59b83f4e2101ea173d4f853a0522d9971f48c6"
WHISPER_COMMIT="5670d5c0bbcb148feabef84400a07cfca9aa3b30"

if [ ! -d "$ROOT/third_party/llama.cpp/.git" ]; then
  git clone https://github.com/ggml-org/llama.cpp.git "$ROOT/third_party/llama.cpp"
fi
git -C "$ROOT/third_party/llama.cpp" fetch --depth 1 origin "$LLAMA_COMMIT"
git -C "$ROOT/third_party/llama.cpp" checkout --detach "$LLAMA_COMMIT"

# Fold6 target: do not waste CI/device binaries on x86_64 emulator ABI.
sed -i 's/listOf("arm64-v8a", "x86_64")/listOf("arm64-v8a")/' "$ROOT/third_party/llama.cpp/examples/llama.android/lib/build.gradle.kts"

# Fold6 compatibility mode: one portable ARM64 CPU backend instead of runtime variant plugins.
sed -i 's/-DGGML_BACKEND_DL=ON/-DGGML_BACKEND_DL=OFF/' "$ROOT/third_party/llama.cpp/examples/llama.android/lib/build.gradle.kts"
sed -i 's/-DGGML_CPU_ALL_VARIANTS=ON/-DGGML_CPU_ALL_VARIANTS=OFF/' "$ROOT/third_party/llama.cpp/examples/llama.android/lib/build.gradle.kts"
sed -i 's/set(GGML_CPU_KLEIDIAI ON)/set(GGML_CPU_KLEIDIAI OFF)/' "$ROOT/third_party/llama.cpp/examples/llama.android/lib/src/main/cpp/CMakeLists.txt"
sed -i 's/set(GGML_OPENMP ON)/set(GGML_OPENMP OFF)/' "$ROOT/third_party/llama.cpp/examples/llama.android/lib/src/main/cpp/CMakeLists.txt"

python3 "$ROOT/scripts/patch_llama_android.py"

# Mobile-safe defaults for the Fold6: lower context and batch to reduce peak RAM.
sed -i 's/DEFAULT_CONTEXT_SIZE    = 8192/DEFAULT_CONTEXT_SIZE    = 4096/' "$ROOT/third_party/llama.cpp/examples/llama.android/lib/src/main/cpp/ai_chat.cpp"
sed -i 's/BATCH_SIZE              = 512/BATCH_SIZE              = 256/' "$ROOT/third_party/llama.cpp/examples/llama.android/lib/src/main/cpp/ai_chat.cpp"

if [ ! -d "$ROOT/third_party/whisper.cpp/.git" ]; then
  git clone https://github.com/ggml-org/whisper.cpp.git "$ROOT/third_party/whisper.cpp"
fi
git -C "$ROOT/third_party/whisper.cpp" fetch --depth 1 origin "$WHISPER_COMMIT"
git -C "$ROOT/third_party/whisper.cpp" checkout --detach "$WHISPER_COMMIT"

rm -f "$ROOT/third_party/whisper.cpp/examples/whisper.android/lib/build.gradle"
cp "$ROOT/scripts/whisper-lib.build.gradle.kts" "$ROOT/third_party/whisper.cpp/examples/whisper.android/lib/build.gradle.kts"

echo "Native dependencies ready."
