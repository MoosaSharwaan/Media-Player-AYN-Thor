/*
 * Copyright (C) 2020 The Android Open Source Project
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
package androidx.media3.decoder.ffmpeg;

import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_MIME_TYPE_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_NO;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_YES_WITHOUT_RECONFIGURATION;

import android.os.Handler;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.decoder.VideoDecoderOutputBuffer;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.video.DecoderVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import java.util.Objects;

/**
 * Decodes and renders video using FFmpeg.
 *
 * <p>Exo Player: implemented here (upstream Media3 1.8 ships this class as a stub). Used for the
 * older formats Android's decoders handle badly or not at all; see {@link FfmpegLibrary}.
 * Frames are decoded into YUV buffers and copied onto the output surface.
 */
@UnstableApi
public final class ExperimentalFfmpegVideoRenderer extends DecoderVideoRenderer {

  private static final String TAG = "ExperimentalFfmpegVideoRenderer";

  /** Enough to cover FFmpeg's frame threading delay plus the renderer's own queue. */
  private static final int NUM_INPUT_BUFFERS = 8;

  private static final int NUM_OUTPUT_BUFFERS = 8;

  /** Large enough for most compressed frames of the formats this renderer decodes. */
  private static final int DEFAULT_INPUT_BUFFER_SIZE = 960 * 1024;

  private final int threads;

  @Nullable private FfmpegVideoDecoder decoder;

  /**
   * Creates a new instance.
   *
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   */
  public ExperimentalFfmpegVideoRenderer(
      long allowedJoiningTimeMs,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify) {
    super(allowedJoiningTimeMs, eventHandler, eventListener, maxDroppedFramesToNotify);
    threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  public final @RendererCapabilities.Capabilities int supportsFormat(Format format) {
    @Nullable String mimeType = format.sampleMimeType;
    if (mimeType == null || !FfmpegLibrary.isAvailable() || !MimeTypes.isVideo(mimeType)) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
    } else if (!FfmpegLibrary.supportsFormat(mimeType)) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE);
    } else if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_DRM);
    } else {
      return RendererCapabilities.create(
          C.FORMAT_HANDLED, ADAPTIVE_SEAMLESS, TUNNELING_NOT_SUPPORTED);
    }
  }

  @Override
  protected FfmpegVideoDecoder createDecoder(Format format, @Nullable CryptoConfig cryptoConfig)
      throws FfmpegDecoderException {
    TraceUtil.beginSection("createFfmpegVideoDecoder");
    int initialInputBufferSize =
        format.maxInputSize != Format.NO_VALUE ? format.maxInputSize : DEFAULT_INPUT_BUFFER_SIZE;
    FfmpegVideoDecoder decoder =
        new FfmpegVideoDecoder(
            NUM_INPUT_BUFFERS, NUM_OUTPUT_BUFFERS, initialInputBufferSize, threads, format);
    this.decoder = decoder;
    TraceUtil.endSection();
    return decoder;
  }

  @Override
  protected void renderOutputBufferToSurface(VideoDecoderOutputBuffer outputBuffer, Surface surface)
      throws FfmpegDecoderException {
    if (decoder == null) {
      throw new FfmpegDecoderException(
          "Failed to render output buffer to surface: decoder is not initialized.");
    }
    decoder.renderToSurface(outputBuffer, surface);
    outputBuffer.release();
  }

  @Override
  protected void setDecoderOutputMode(@C.VideoOutputMode int outputMode) {
    if (decoder != null) {
      decoder.setOutputMode(outputMode);
    }
  }

  @Override
  protected DecoderReuseEvaluation canReuseDecoder(
      String decoderName, Format oldFormat, Format newFormat) {
    boolean sameMimeType = Objects.equals(oldFormat.sampleMimeType, newFormat.sampleMimeType);
    return new DecoderReuseEvaluation(
        decoderName,
        oldFormat,
        newFormat,
        sameMimeType ? REUSE_RESULT_YES_WITHOUT_RECONFIGURATION : REUSE_RESULT_NO,
        sameMimeType ? 0 : DISCARD_REASON_MIME_TYPE_CHANGED);
  }
}
