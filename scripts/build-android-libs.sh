#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$PROJECT_ROOT/android"
JNI_LIBS_DIR="$ANDROID_DIR/app/src/main/jniLibs"
CARGO_TOML="$PROJECT_ROOT/torvox-gui-android/Cargo.toml"
TARGET_DIR="$PROJECT_ROOT/target"

: "${ANDROID_NDK_ROOT:?ANDROID_NDK_ROOT must be set}"

if ! command -v cargo-ndk &>/dev/null; then
	echo "Installing cargo-ndk..."
	cargo install cargo-ndk
fi

echo "=== Cross-compiling torvox-gui-android for Android ==="

TARGETS=("aarch64-linux-android" "x86_64-linux-android")
ABI_MAP=("arm64-v8a" "x86_64")

for i in "${!TARGETS[@]}"; do
	TARGET="${TARGETS[$i]}"
	ABI="${ABI_MAP[$i]}"
	echo "--- Building for $TARGET ($ABI) ---"

	CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android33-clang" \
		CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android33-clang" \
		cargo ndk -t "$TARGET" -o "$TARGET_DIR" build --manifest-path "$CARGO_TOML" --profile dev

	mkdir -p "$JNI_LIBS_DIR/$ABI"
	cp "$TARGET_DIR/$TARGET/debug/libtorvox_core.so" "$JNI_LIBS_DIR/$ABI/"
	echo "Copied to $JNI_LIBS_DIR/$ABI/libtorvox_core.so"
done

echo "=== Done ==="
