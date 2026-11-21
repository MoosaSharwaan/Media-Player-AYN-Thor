#!/bin/bash
# Builds libffmpegJNI.so for the Thor (arm64-v8a) into src/main/jniLibs, where the app picks it
# up. The result is committed, so normal builds need neither the NDK nor FFmpeg's source.
#
#   ffmpeg/build.sh <Android NDK path> <FFmpeg 6.0 source path>
#
# FFmpeg: git clone --depth 1 --branch release/6.0 https://github.com/FFmpeg/FFmpeg
# NDK:    sdkmanager "ndk;27.0.12077973"
set -eu

MODULE="$(cd "$(dirname "$0")" && pwd)"
NDK="$1"
FFMPEG="$2"
API=29
ABI=arm64-v8a
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
OUT="$MODULE/build-native/$ABI"

# Audio for what Android lacks (AC3, E-AC3, DTS, TrueHD and the rest Media3 maps), video for the
# older formats Android decodes badly or not at all (D49), and H.264 for 10-bit (Hi10P) anime.
DECODERS=(
  aac mp3 ac3 eac3 truehd mlp dca vorbis opus amrnb amrwb flac alac pcm_mulaw pcm_alaw
  mpeg4 msmpeg4v2 msmpeg4v3 mpeg1video mpeg2video h263 mjpeg h264
)

OPTIONS=(
  --target-os=android --arch=aarch64 --cpu=armv8-a
  --cross-prefix="$TOOLCHAIN/aarch64-linux-android$API-"
  --nm="$TOOLCHAIN/llvm-nm" --ar="$TOOLCHAIN/llvm-ar" --ranlib="$TOOLCHAIN/llvm-ranlib" --strip="$TOOLCHAIN/llvm-strip"
  --prefix="$OUT"
  --enable-static --disable-shared --disable-doc --disable-programs --disable-everything
  --disable-avdevice --disable-avformat --disable-swscale --disable-postproc --disable-avfilter
  --disable-symver --enable-swresample --disable-v4l2-m2m --disable-vulkan --disable-debug
  --extra-ldexeflags=-pie
)
for decoder in "${DECODERS[@]}"; do OPTIONS+=(--enable-decoder="$decoder"); done

rm -rf "$OUT"
cd "$FFMPEG"
./configure "${OPTIONS[@]}"
make -j"$(nproc)"
make install-libs install-headers
make distclean

cmake -S "$MODULE/jni" -B "$OUT/cmake" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$ABI" -DANDROID_PLATFORM="android-$API" -DCMAKE_BUILD_TYPE=Release \
  -DFFMPEG_PREFIX="$OUT"
cmake --build "$OUT/cmake"
mkdir -p "$MODULE/src/main/jniLibs/$ABI"
"$TOOLCHAIN/llvm-strip" --strip-unneeded -o "$MODULE/src/main/jniLibs/$ABI/libffmpegJNI.so" "$OUT/cmake/libffmpegJNI.so"
ls -l "$MODULE/src/main/jniLibs/$ABI/libffmpegJNI.so"
