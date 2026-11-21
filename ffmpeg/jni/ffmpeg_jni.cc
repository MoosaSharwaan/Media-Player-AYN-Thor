/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <string.h>
#include <jni.h>
#include <stdlib.h>

extern "C" {
#ifdef __cplusplus
#define __STDC_CONSTANT_MACROS
#ifdef _STDINT_H
#undef _STDINT_H
#endif
#include <stdint.h>
#endif
#include <libavcodec/avcodec.h>
#include <libavutil/channel_layout.h>
#include <libavutil/error.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>
}

#define LOG_TAG "ffmpeg_jni"
#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define LOGD(...) \
  ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))

#define LIBRARY_FUNC(RETURN_TYPE, NAME, ...)                               \
  extern "C" {                                                             \
  JNIEXPORT RETURN_TYPE                                                    \
  Java_androidx_media3_decoder_ffmpeg_FfmpegLibrary_##NAME(JNIEnv *env,    \
                                                           jobject thiz,   \
                                                           ##__VA_ARGS__); \
  }                                                                        \
  JNIEXPORT RETURN_TYPE                                                    \
  Java_androidx_media3_decoder_ffmpeg_FfmpegLibrary_##NAME(                \
      JNIEnv *env, jobject thiz, ##__VA_ARGS__)

#define AUDIO_DECODER_FUNC(RETURN_TYPE, NAME, ...)               \
  extern "C" {                                                   \
  JNIEXPORT RETURN_TYPE                                          \
  Java_androidx_media3_decoder_ffmpeg_FfmpegAudioDecoder_##NAME( \
      JNIEnv *env, jobject thiz, ##__VA_ARGS__);                 \
  }                                                              \
  JNIEXPORT RETURN_TYPE                                          \
  Java_androidx_media3_decoder_ffmpeg_FfmpegAudioDecoder_##NAME( \
      JNIEnv *env, jobject thiz, ##__VA_ARGS__)

// Exo Player: video decoding, for FfmpegVideoDecoder.
#define VIDEO_DECODER_FUNC(RETURN_TYPE, NAME, ...)               \
  extern "C" {                                                   \
  JNIEXPORT RETURN_TYPE                                          \
  Java_androidx_media3_decoder_ffmpeg_FfmpegVideoDecoder_##NAME( \
      JNIEnv *env, jobject thiz, ##__VA_ARGS__);                 \
  }                                                              \
  JNIEXPORT RETURN_TYPE                                          \
  Java_androidx_media3_decoder_ffmpeg_FfmpegVideoDecoder_##NAME( \
      JNIEnv *env, jobject thiz, ##__VA_ARGS__)

#define ERROR_STRING_BUFFER_LENGTH 256

// Output format corresponding to AudioFormat.ENCODING_PCM_16BIT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_16BIT = AV_SAMPLE_FMT_S16;
// Output format corresponding to AudioFormat.ENCODING_PCM_FLOAT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_FLOAT = AV_SAMPLE_FMT_FLT;

// LINT.IfChange
static const int AUDIO_DECODER_ERROR_INVALID_DATA = -1;
static const int AUDIO_DECODER_ERROR_OTHER = -2;
// LINT.ThenChange(../java/androidx/media3/decoder/ffmpeg/FfmpegAudioDecoder.java)

static jmethodID growOutputBufferMethod;

/**
 * Returns the AVCodec with the specified name, or NULL if it is not available.
 */
const AVCodec *getCodecByName(JNIEnv *env, jstring codecName);

/**
 * Allocates and opens a new AVCodecContext for the specified codec, passing the
 * provided extraData as initialization data for the decoder if it is non-NULL.
 * Returns the created context.
 */
AVCodecContext *createContext(JNIEnv *env, const AVCodec *codec,
                              jbyteArray extraData, jboolean outputFloat,
                              jint rawSampleRate, jint rawChannelCount);

struct GrowOutputBufferCallback {
  uint8_t *operator()(int requiredSize) const;

  JNIEnv *env;
  jobject thiz;
  jobject decoderOutputBuffer;
};

/**
 * Decodes the packet into the output buffer, returning the number of bytes
 * written, or a negative AUDIO_DECODER_ERROR constant value in the case of an
 * error.
 */
int decodePacket(AVCodecContext *context, AVPacket *packet,
                 uint8_t *outputBuffer, int outputSize,
                 GrowOutputBufferCallback growBuffer);

/**
 * Transforms ffmpeg AVERROR into a negative AUDIO_DECODER_ERROR constant value.
 */
int transformError(int errorNumber);

/**
 * Outputs a log message describing the avcodec error number.
 */
void logError(const char *functionName, int errorNumber);

/**
 * Releases the specified context.
 */
void releaseContext(AVCodecContext *context);

// ---- video (Exo Player) ----

// LINT.IfChange
static const int VIDEO_DECODER_SUCCESS = 0;
static const int VIDEO_DECODER_NO_FRAME = 1;
static const int VIDEO_DECODER_ERROR_INVALID_DATA = -1;
static const int VIDEO_DECODER_ERROR_OTHER = -2;
// LINT.ThenChange(../src/main/java/androidx/media3/decoder/ffmpeg/FfmpegVideoDecoder.java)

// HAL_PIXEL_FORMAT_YV12: Y, then V, then U; chroma rows aligned to 16 bytes.
static const int kImageFormatYV12 = 0x32315659;

// VideoDecoderOutputBuffer's colorspace constants.
static const int COLORSPACE_UNKNOWN = 0;
static const int COLORSPACE_BT601 = 1;
static const int COLORSPACE_BT709 = 2;
static const int COLORSPACE_BT2020 = 3;

static bool videoAvailable = false;
static jmethodID initForYuvFrameMethod;
static jfieldID dataField;
static jfieldID timeUsField;

struct VideoContext {
  AVCodecContext *codec;
  AVPacket *packet;
  AVFrame *frame;
  // A frame taken out early, to make room when FFmpeg wouldn't accept more input.
  bool hasPendingFrame;
  ANativeWindow *window;
  jobject surface;
  int windowWidth;
  int windowHeight;
};

static bool initVideoIds(JNIEnv *env) {
  jclass clazz = env->FindClass("androidx/media3/decoder/VideoDecoderOutputBuffer");
  if (!clazz) {
    LOGE("JNI_OnLoad: FindClass VideoDecoderOutputBuffer failed");
    return false;
  }
  initForYuvFrameMethod = env->GetMethodID(clazz, "initForYuvFrame", "(IIIII)Z");
  dataField = env->GetFieldID(clazz, "data", "Ljava/nio/ByteBuffer;");
  timeUsField = env->GetFieldID(clazz, "timeUs", "J");
  if (!initForYuvFrameMethod || !dataField || !timeUsField) {
    LOGE("JNI_OnLoad: video GetMethodID/GetFieldID failed");
    return false;
  }
  return true;
}

static int align16(int value) { return (value + 15) & ~15; }

static int colorspaceOf(const AVFrame *frame) {
  switch (frame->colorspace) {
    case AVCOL_SPC_BT709:
      return COLORSPACE_BT709;
    case AVCOL_SPC_BT2020_NCL:
    case AVCOL_SPC_BT2020_CL:
      return COLORSPACE_BT2020;
    case AVCOL_SPC_BT470BG:
    case AVCOL_SPC_SMPTE170M:
      return COLORSPACE_BT601;
    default:
      return COLORSPACE_UNKNOWN;
  }
}

/** One row of 10-bit samples (stored in 16 bits) as 8-bit, keeping every [step]th sample. */
static void copyRow10(uint8_t *out, const uint8_t *row, int count, int step) {
  const uint16_t *in = (const uint16_t *)row;
  for (int x = 0; x < count; x++) out[x] = (uint8_t)(in[x * step] >> 2);
}

/**
 * Copies [frame] into the output buffer as 8-bit YUV 4:2:0. 4:2:2 and 4:4:4 (Motion JPEG) are
 * thinned to 4:2:0 by taking every other chroma row (and column); 10-bit (Hi10P H.264) is
 * shifted down to 8 bits.
 */
static int copyFrame(JNIEnv *env, const AVFrame *frame, jobject outputBuffer) {
  bool full420 = frame->format == AV_PIX_FMT_YUV420P || frame->format == AV_PIX_FMT_YUVJ420P;
  bool rows422 = frame->format == AV_PIX_FMT_YUV422P || frame->format == AV_PIX_FMT_YUVJ422P;
  bool full444 = frame->format == AV_PIX_FMT_YUV444P || frame->format == AV_PIX_FMT_YUVJ444P;
  bool tenBit = frame->format == AV_PIX_FMT_YUV420P10LE;
  full420 = full420 || tenBit;
  if (!full420 && !rows422 && !full444) {
    LOGE("Unsupported pixel format %d.", frame->format);
    return VIDEO_DECODER_ERROR_INVALID_DATA;
  }
  int width = frame->width;
  int height = frame->height;
  int uvWidth = (width + 1) / 2;
  int uvHeight = (height + 1) / 2;
  int yStride = align16(width);
  int uvStride = align16(uvWidth);
  jboolean ok = env->CallBooleanMethod(outputBuffer, initForYuvFrameMethod, width, height,
                                       yStride, uvStride, colorspaceOf(frame));
  if (env->ExceptionCheck() || !ok) {
    LOGE("Failed to allocate the output buffer.");
    return VIDEO_DECODER_ERROR_OTHER;
  }
  jobject data = env->GetObjectField(outputBuffer, dataField);
  uint8_t *dst = data ? (uint8_t *)env->GetDirectBufferAddress(data) : NULL;
  if (!dst) {
    LOGE("Output buffer has no direct data.");
    return VIDEO_DECODER_ERROR_OTHER;
  }
  for (int y = 0; y < height; y++) {
    const uint8_t *row = frame->data[0] + y * frame->linesize[0];
    if (tenBit) {
      copyRow10(dst + y * yStride, row, width, 1);
    } else {
      memcpy(dst + y * yStride, row, width);
    }
  }
  uint8_t *dstPlanes[2] = {dst + yStride * height, dst + yStride * height + uvStride * uvHeight};
  for (int plane = 0; plane < 2; plane++) {
    const uint8_t *src = frame->data[plane + 1];
    int srcStride = frame->linesize[plane + 1];
    for (int y = 0; y < uvHeight; y++) {
      int srcRow = full420 ? y : (y * 2 < height ? y * 2 : height - 1);
      const uint8_t *row = src + srcRow * srcStride;
      uint8_t *out = dstPlanes[plane] + y * uvStride;
      if (tenBit) {
        copyRow10(out, row, uvWidth, 1);
      } else if (full444) {
        for (int x = 0; x < uvWidth; x++) out[x] = row[x * 2];
      } else {
        memcpy(out, row, uvWidth);
      }
    }
  }
  if (frame->best_effort_timestamp != AV_NOPTS_VALUE) {
    env->SetLongField(outputBuffer, timeUsField, frame->best_effort_timestamp);
  }
  return VIDEO_DECODER_SUCCESS;
}

static void releaseVideoContext(JNIEnv *env, VideoContext *video) {
  if (!video) return;
  if (video->codec) avcodec_free_context(&video->codec);
  if (video->packet) av_packet_free(&video->packet);
  if (video->frame) av_frame_free(&video->frame);
  if (video->window) ANativeWindow_release(video->window);
  if (video->surface) env->DeleteGlobalRef(video->surface);
  delete video;
}

VIDEO_DECODER_FUNC(jlong, ffmpegVideoInitialize, jstring codecName, jbyteArray extraData,
                   jint threads, jint width, jint height) {
  if (!videoAvailable) {
    LOGE("Video decoding wasn't set up when the library loaded.");
    return 0L;
  }
  const AVCodec *codec = getCodecByName(env, codecName);
  if (!codec) {
    LOGE("Codec not found.");
    return 0L;
  }
  AVCodecContext *context = avcodec_alloc_context3(codec);
  if (!context) {
    LOGE("Failed to allocate context.");
    return 0L;
  }
  if (extraData) {
    jsize size = env->GetArrayLength(extraData);
    context->extradata_size = size;
    context->extradata = (uint8_t *)av_mallocz(size + AV_INPUT_BUFFER_PADDING_SIZE);
    if (!context->extradata) {
      LOGE("Failed to allocate extradata.");
      avcodec_free_context(&context);
      return 0L;
    }
    env->GetByteArrayRegion(extraData, 0, size, (jbyte *)context->extradata);
  }
  // The container's picture size: DivX 3 has none in its own data.
  context->width = width;
  context->height = height;
  context->coded_width = width;
  context->coded_height = height;
  context->thread_count = threads;
  context->thread_type = FF_THREAD_FRAME | FF_THREAD_SLICE;
  // Timestamps arrive in microseconds.
  context->pkt_timebase = (AVRational){1, 1000000};
  int result = avcodec_open2(context, codec, NULL);
  if (result < 0) {
    logError("avcodec_open2", result);
    avcodec_free_context(&context);
    return 0L;
  }
  VideoContext *video = new VideoContext();
  video->codec = context;
  video->packet = av_packet_alloc();
  video->frame = av_frame_alloc();
  if (!video->packet || !video->frame) {
    LOGE("Failed to allocate packet or frame.");
    releaseVideoContext(env, video);
    return 0L;
  }
  return (jlong)video;
}

VIDEO_DECODER_FUNC(jlong, ffmpegVideoReset, jlong jContext) {
  VideoContext *video = (VideoContext *)jContext;
  if (!video) {
    LOGE("Tried to reset without a context.");
    return 0L;
  }
  avcodec_flush_buffers(video->codec);
  av_frame_unref(video->frame);
  video->hasPendingFrame = false;
  return jContext;
}

VIDEO_DECODER_FUNC(jint, ffmpegVideoSendPacket, jlong jContext, jobject data, jint size,
                   jlong timeUs) {
  VideoContext *video = (VideoContext *)jContext;
  if (!video) return VIDEO_DECODER_ERROR_OTHER;
  AVPacket *packet = video->packet;
  packet->data = (uint8_t *)env->GetDirectBufferAddress(data);
  packet->size = size;
  // Both stamps: AVI's come in decoding order, MP4 and Matroska's are display times. FFmpeg's
  // timestamp correction keeps whichever stays in order once frames are reordered.
  packet->pts = timeUs;
  packet->dts = timeUs;
  int result = avcodec_send_packet(video->codec, packet);
  for (int attempt = 0; result == AVERROR(EAGAIN) && attempt < 2; attempt++) {
    // FFmpeg has frames waiting: take one out (it is shown next), or drop it if one already is.
    if (video->hasPendingFrame) {
      av_frame_unref(video->frame);
      video->hasPendingFrame = false;
    }
    if (avcodec_receive_frame(video->codec, video->frame) == 0) {
      video->hasPendingFrame = true;
    }
    result = avcodec_send_packet(video->codec, packet);
  }
  av_packet_unref(packet);
  if (result == 0) return VIDEO_DECODER_SUCCESS;
  logError("avcodec_send_packet", result);
  return result == AVERROR_INVALIDDATA || result == AVERROR(EAGAIN) ? VIDEO_DECODER_ERROR_INVALID_DATA
                                                                    : VIDEO_DECODER_ERROR_OTHER;
}

VIDEO_DECODER_FUNC(jint, ffmpegVideoReceiveFrame, jlong jContext, jobject outputBuffer) {
  VideoContext *video = (VideoContext *)jContext;
  if (!video) return VIDEO_DECODER_ERROR_OTHER;
  if (!video->hasPendingFrame) {
    int result = avcodec_receive_frame(video->codec, video->frame);
    if (result == AVERROR(EAGAIN) || result == AVERROR_EOF) return VIDEO_DECODER_NO_FRAME;
    if (result < 0) {
      logError("avcodec_receive_frame", result);
      return VIDEO_DECODER_ERROR_INVALID_DATA;
    }
  }
  video->hasPendingFrame = false;
  int result = copyFrame(env, video->frame, outputBuffer);
  av_frame_unref(video->frame);
  return result;
}

VIDEO_DECODER_FUNC(jint, ffmpegVideoRenderFrame, jlong jContext, jobject surface, jobject data,
                   jint width, jint height, jint yStride, jint uvStride) {
  VideoContext *video = (VideoContext *)jContext;
  if (!video) return VIDEO_DECODER_ERROR_OTHER;
  if (!video->surface || !env->IsSameObject(video->surface, surface)) {
    if (video->window) ANativeWindow_release(video->window);
    if (video->surface) env->DeleteGlobalRef(video->surface);
    video->window = ANativeWindow_fromSurface(env, surface);
    video->surface = env->NewGlobalRef(surface);
    video->windowWidth = 0;
    video->windowHeight = 0;
    if (!video->window) {
      LOGE("ANativeWindow_fromSurface failed.");
      return VIDEO_DECODER_ERROR_OTHER;
    }
  }
  if (width != video->windowWidth || height != video->windowHeight) {
    if (ANativeWindow_setBuffersGeometry(video->window, width, height, kImageFormatYV12)) {
      LOGE("ANativeWindow_setBuffersGeometry failed.");
      return VIDEO_DECODER_ERROR_OTHER;
    }
    video->windowWidth = width;
    video->windowHeight = height;
  }
  ANativeWindow_Buffer buffer;
  if (ANativeWindow_lock(video->window, &buffer, NULL) || !buffer.bits) {
    LOGE("ANativeWindow_lock failed.");
    return VIDEO_DECODER_ERROR_OTHER;
  }
  const uint8_t *src = (const uint8_t *)env->GetDirectBufferAddress(data);
  if (!src) {
    ANativeWindow_unlockAndPost(video->window);
    return VIDEO_DECODER_ERROR_OTHER;
  }
  int rows = height < buffer.height ? height : buffer.height;
  int cols = width < buffer.stride ? width : buffer.stride;
  uint8_t *dstY = (uint8_t *)buffer.bits;
  for (int y = 0; y < rows; y++) {
    memcpy(dstY + y * buffer.stride, src + y * yStride, cols);
  }
  int uvHeight = (height + 1) / 2;
  int uvCols = (cols + 1) / 2;
  int dstUvStride = align16(buffer.stride / 2);
  int dstUvHeight = (buffer.height + 1) / 2;
  int uvRows = uvHeight < dstUvHeight ? uvHeight : dstUvHeight;
  const uint8_t *srcU = src + yStride * height;
  const uint8_t *srcV = srcU + uvStride * uvHeight;
  uint8_t *dstV = dstY + buffer.stride * buffer.height;
  uint8_t *dstU = dstV + dstUvStride * dstUvHeight;
  for (int y = 0; y < uvRows; y++) {
    memcpy(dstU + y * dstUvStride, srcU + y * uvStride, uvCols);
    memcpy(dstV + y * dstUvStride, srcV + y * uvStride, uvCols);
  }
  ANativeWindow_unlockAndPost(video->window);
  return VIDEO_DECODER_SUCCESS;
}

VIDEO_DECODER_FUNC(void, ffmpegVideoRelease, jlong jContext) {
  releaseVideoContext(env, (VideoContext *)jContext);
}

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
  JNIEnv *env;
  if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
    LOGE("JNI_OnLoad: GetEnv failed");
    return -1;
  }
  jclass clazz =
      env->FindClass("androidx/media3/decoder/ffmpeg/FfmpegAudioDecoder");
  if (!clazz) {
    LOGE("JNI_OnLoad: FindClass failed");
    return -1;
  }
  growOutputBufferMethod =
      env->GetMethodID(clazz, "growOutputBuffer",
                       "(Landroidx/media3/decoder/"
                       "SimpleDecoderOutputBuffer;I)Ljava/nio/ByteBuffer;");
  if (!growOutputBufferMethod) {
    LOGE("JNI_OnLoad: GetMethodID failed");
    return -1;
  }
  // Video is optional: if it can't be set up, audio decoding still loads.
  videoAvailable = initVideoIds(env);
  if (env->ExceptionCheck()) env->ExceptionClear();
  return JNI_VERSION_1_6;
}

LIBRARY_FUNC(jstring, ffmpegGetVersion) {
  return env->NewStringUTF(LIBAVCODEC_IDENT);
}

LIBRARY_FUNC(jint, ffmpegGetInputBufferPaddingSize) {
  return (jint)AV_INPUT_BUFFER_PADDING_SIZE;
}

LIBRARY_FUNC(jboolean, ffmpegHasDecoder, jstring codecName) {
  return getCodecByName(env, codecName) != NULL;
}

AUDIO_DECODER_FUNC(jlong, ffmpegInitialize, jstring codecName,
                   jbyteArray extraData, jboolean outputFloat,
                   jint rawSampleRate, jint rawChannelCount) {
  const AVCodec *codec = getCodecByName(env, codecName);
  if (!codec) {
    LOGE("Codec not found.");
    return 0L;
  }
  return (jlong)createContext(env, codec, extraData, outputFloat, rawSampleRate,
                              rawChannelCount);
}

AUDIO_DECODER_FUNC(jint, ffmpegDecode, jlong context, jobject inputData,
                   jint inputSize, jobject decoderOutputBuffer,
                   jobject outputData, jint outputSize) {
  if (!context) {
    LOGE("Context must be non-NULL.");
    return -1;
  }
  if (!inputData || !decoderOutputBuffer || !outputData) {
    LOGE("Input and output buffers must be non-NULL.");
    return -1;
  }
  if (inputSize < 0) {
    LOGE("Invalid input buffer size: %d.", inputSize);
    return -1;
  }
  if (outputSize < 0) {
    LOGE("Invalid output buffer length: %d", outputSize);
    return -1;
  }
  uint8_t *inputBuffer = (uint8_t *)env->GetDirectBufferAddress(inputData);
  uint8_t *outputBuffer = (uint8_t *)env->GetDirectBufferAddress(outputData);
  AVPacket *packet = av_packet_alloc();
  if (!packet) {
    LOGE("Failed to allocate packet.");
    return -1;
  }
  packet->data = inputBuffer;
  packet->size = inputSize;
  const int ret =
      decodePacket((AVCodecContext *)context, packet, outputBuffer, outputSize,
                   GrowOutputBufferCallback{env, thiz, decoderOutputBuffer});
  av_packet_free(&packet);
  return ret;
}

uint8_t *GrowOutputBufferCallback::operator()(int requiredSize) const {
  jobject newOutputData = env->CallObjectMethod(
      thiz, growOutputBufferMethod, decoderOutputBuffer, requiredSize);
  if (env->ExceptionCheck()) {
    LOGE("growOutputBuffer() failed");
    env->ExceptionDescribe();
    return nullptr;
  }
  return static_cast<uint8_t *>(env->GetDirectBufferAddress(newOutputData));
}

AUDIO_DECODER_FUNC(jint, ffmpegGetChannelCount, jlong context) {
  if (!context) {
    LOGE("Context must be non-NULL.");
    return -1;
  }
  return ((AVCodecContext *)context)->ch_layout.nb_channels;
}

AUDIO_DECODER_FUNC(jint, ffmpegGetSampleRate, jlong context) {
  if (!context) {
    LOGE("Context must be non-NULL.");
    return -1;
  }
  return ((AVCodecContext *)context)->sample_rate;
}

AUDIO_DECODER_FUNC(jlong, ffmpegReset, jlong jContext, jbyteArray extraData) {
  AVCodecContext *context = (AVCodecContext *)jContext;
  if (!context) {
    LOGE("Tried to reset without a context.");
    return 0L;
  }

  AVCodecID codecId = context->codec_id;
  if (codecId == AV_CODEC_ID_TRUEHD) {
    jboolean outputFloat =
        (jboolean)(context->request_sample_fmt == OUTPUT_FORMAT_PCM_FLOAT);
    // Release and recreate the context if the codec is TrueHD.
    // TODO: Figure out why flushing doesn't work for this codec.
    releaseContext(context);
    const AVCodec *codec = avcodec_find_decoder(codecId);
    if (!codec) {
      LOGE("Unexpected error finding codec %d.", codecId);
      return 0L;
    }
    return (jlong)createContext(env, codec, extraData, outputFloat,
                                /* rawSampleRate= */ -1,
                                /* rawChannelCount= */ -1);
  }

  avcodec_flush_buffers(context);
  return (jlong)context;
}

AUDIO_DECODER_FUNC(void, ffmpegRelease, jlong context) {
  if (context) {
    releaseContext((AVCodecContext *)context);
  }
}

const AVCodec *getCodecByName(JNIEnv *env, jstring codecName) {
  if (!codecName) {
    return NULL;
  }
  const char *codecNameChars = env->GetStringUTFChars(codecName, NULL);
  const AVCodec *codec = avcodec_find_decoder_by_name(codecNameChars);
  env->ReleaseStringUTFChars(codecName, codecNameChars);
  return codec;
}

AVCodecContext *createContext(JNIEnv *env, const AVCodec *codec,
                              jbyteArray extraData, jboolean outputFloat,
                              jint rawSampleRate, jint rawChannelCount) {
  AVCodecContext *context = avcodec_alloc_context3(codec);
  if (!context) {
    LOGE("Failed to allocate context.");
    return NULL;
  }
  context->request_sample_fmt =
      outputFloat ? OUTPUT_FORMAT_PCM_FLOAT : OUTPUT_FORMAT_PCM_16BIT;
  if (extraData) {
    jsize size = env->GetArrayLength(extraData);
    context->extradata_size = size;
    context->extradata =
        (uint8_t *)av_malloc(size + AV_INPUT_BUFFER_PADDING_SIZE);
    if (!context->extradata) {
      LOGE("Failed to allocate extradata.");
      releaseContext(context);
      return NULL;
    }
    env->GetByteArrayRegion(extraData, 0, size, (jbyte *)context->extradata);
  }
  if (context->codec_id == AV_CODEC_ID_PCM_MULAW ||
      context->codec_id == AV_CODEC_ID_PCM_ALAW) {
    context->sample_rate = rawSampleRate;
    av_channel_layout_default(&context->ch_layout, rawChannelCount);
  }
  context->err_recognition = AV_EF_IGNORE_ERR;
  int result = avcodec_open2(context, codec, NULL);
  if (result < 0) {
    logError("avcodec_open2", result);
    releaseContext(context);
    return NULL;
  }
  return context;
}

int decodePacket(AVCodecContext *context, AVPacket *packet,
                 uint8_t *outputBuffer, int outputSize,
                 GrowOutputBufferCallback growBuffer) {
  int result = 0;
  // Queue input data.
  result = avcodec_send_packet(context, packet);
  if (result) {
    logError("avcodec_send_packet", result);
    return transformError(result);
  }

  // Dequeue output data until it runs out.
  int outSize = 0;
  while (true) {
    AVFrame *frame = av_frame_alloc();
    if (!frame) {
      LOGE("Failed to allocate output frame.");
      return AUDIO_DECODER_ERROR_INVALID_DATA;
    }
    result = avcodec_receive_frame(context, frame);
    if (result) {
      av_frame_free(&frame);
      if (result == AVERROR(EAGAIN)) {
        break;
      }
      logError("avcodec_receive_frame", result);
      return transformError(result);
    }

    // Resample output.
    AVSampleFormat sampleFormat = context->sample_fmt;
    int channelCount = context->ch_layout.nb_channels;
    int sampleRate = context->sample_rate;
    int sampleCount = frame->nb_samples;
    int dataSize = av_samples_get_buffer_size(NULL, channelCount, sampleCount,
                                              sampleFormat, 1);
    SwrContext *resampleContext = static_cast<SwrContext *>(context->opaque);
    if (!resampleContext) {
      result =
          swr_alloc_set_opts2(&resampleContext,             // ps
                              &context->ch_layout,          // out_ch_layout
                              context->request_sample_fmt,  // out_sample_fmt
                              sampleRate,                   // out_sample_rate
                              &context->ch_layout,          // in_ch_layout
                              sampleFormat,                 // in_sample_fmt
                              sampleRate,                   // in_sample_rate
                              0,                            // log_offset
                              NULL                          // log_ctx
          );
      if (result < 0) {
        logError("swr_alloc_set_opts2", result);
        av_frame_free(&frame);
        return transformError(result);
      }
      result = swr_init(resampleContext);
      if (result < 0) {
        logError("swr_init", result);
        av_frame_free(&frame);
        return transformError(result);
      }
      context->opaque = resampleContext;
    }

    int outSampleSize = av_get_bytes_per_sample(context->request_sample_fmt);
    int outSamples = swr_get_out_samples(resampleContext, sampleCount);
    int bufferOutSize = outSampleSize * channelCount * outSamples;
    if (outSize + bufferOutSize > outputSize) {
      LOGD(
          "Output buffer size (%d) too small for output data (%d), "
          "reallocating buffer.",
          outputSize, outSize + bufferOutSize);
      outputSize = outSize + bufferOutSize;
      outputBuffer = growBuffer(outputSize);
      if (!outputBuffer) {
        LOGE("Failed to reallocate output buffer.");
        av_frame_free(&frame);
        return AUDIO_DECODER_ERROR_OTHER;
      }
    }
    result = swr_convert(resampleContext, &outputBuffer, bufferOutSize,
                         (const uint8_t **)frame->data, frame->nb_samples);
    av_frame_free(&frame);
    if (result < 0) {
      logError("swr_convert", result);
      return AUDIO_DECODER_ERROR_INVALID_DATA;
    }
    int available = swr_get_out_samples(resampleContext, 0);
    if (available != 0) {
      LOGE("Expected no samples remaining after resampling, but found %d.",
           available);
      return AUDIO_DECODER_ERROR_INVALID_DATA;
    }
    outputBuffer += bufferOutSize;
    outSize += bufferOutSize;
  }
  return outSize;
}

int transformError(int errorNumber) {
  return errorNumber == AVERROR_INVALIDDATA ? AUDIO_DECODER_ERROR_INVALID_DATA
                                            : AUDIO_DECODER_ERROR_OTHER;
}

void logError(const char *functionName, int errorNumber) {
  char *buffer = (char *)malloc(ERROR_STRING_BUFFER_LENGTH * sizeof(char));
  av_strerror(errorNumber, buffer, ERROR_STRING_BUFFER_LENGTH);
  LOGE("Error in %s: %s", functionName, buffer);
  free(buffer);
}

void releaseContext(AVCodecContext *context) {
  if (!context) {
    return;
  }
  SwrContext *swrContext;
  if ((swrContext = (SwrContext *)context->opaque)) {
    swr_free(&swrContext);
    context->opaque = NULL;
  }
  avcodec_free_context(&context);
}
