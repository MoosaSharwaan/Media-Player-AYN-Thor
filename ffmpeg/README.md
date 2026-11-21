# FFmpeg module

Media3's `decoder_ffmpeg` extension, taken from [androidx/media](https://github.com/androidx/media) at tag `1.8.0` (`libraries/decoder_ffmpeg`), with video decoding added for Exo Player (decision D49 in [docs/PLAN.md](../docs/PLAN.md)).

## What changed from Media3

- **`ExperimentalFfmpegVideoRenderer`** is implemented: upstream it is a stub that decodes nothing.
- **`FfmpegVideoDecoder`** (new): sends each packet to FFmpeg, takes at most one frame back, copies it as 8-bit YUV 4:2:0 and draws it onto the video surface. Modelled on Media3's `VpxDecoder`.
- **`FfmpegLibrary.getCodecName`** maps the old video formats: MPEG-4 Part 2 (Xvid, DivX), MS-MPEG-4 v2/v3 (DivX 3), MPEG-1/2, H.263, Motion JPEG.
- **`jni/ffmpeg_jni.cc`** gains the video functions: decode, convert (4:2:2, 4:4:4 and 10-bit down to 8-bit 4:2:0) and draw on an `ANativeWindow`.

The app sends Xvid and DivX to FFmpeg even though Android claims them (`PlaybackHolder.codecSelector`); the other formats reach FFmpeg when Android has no decoder that can play them.

## The native library

`src/main/jniLibs/arm64-v8a/libffmpegJNI.so` is committed, so normal builds need neither the NDK nor FFmpeg's source. It is built for arm64 only (the Thor); on other devices FFmpeg is simply unavailable and Android's decoders are used.

Rebuild it after changing `jni/`:

```
git clone --depth 1 --branch release/6.0 https://github.com/FFmpeg/FFmpeg   # built from 573f2b8
sdkmanager "ndk;27.0.12077973"
ffmpeg/build.sh <NDK path> <FFmpeg path>
```

Decoders compiled in: audio `aac mp3 ac3 eac3 truehd mlp dca vorbis opus amrnb amrwb flac alac pcm_mulaw pcm_alaw`; video `mpeg4 msmpeg4v2 msmpeg4v3 mpeg1video mpeg2video h263 mjpeg h264`.

## Licences

- Media3 code: Apache License 2.0.
- FFmpeg: LGPL 2.1 or later, statically linked. Source: FFmpeg `release/6.0` at commit `573f2b81ebd97da81def2ba762e81b0328547004`, configured by `build.sh` (no GPL or non-free parts enabled).
