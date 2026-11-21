/*
 * Exo Player: FFmpeg video decoding for ExperimentalFfmpegVideoRenderer, modelled on Media3's
 * VpxDecoder. Licensed under the Apache License, Version 2.0, like the rest of this module.
 */
package androidx.media3.decoder.ffmpeg;

import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoder;
import androidx.media3.decoder.VideoDecoderOutputBuffer;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * FFmpeg video decoder. Each input buffer is sent to FFmpeg and at most one decoded frame is taken
 * back; frames FFmpeg holds for reordering (B-frames) come out with later inputs. Frames are
 * copied into the output buffer as YUV 4:2:0, then onto the surface when rendered.
 */
@UnstableApi
/* package */ final class FfmpegVideoDecoder
    extends SimpleDecoder<DecoderInputBuffer, VideoDecoderOutputBuffer, FfmpegDecoderException> {

  // LINT.IfChange
  private static final int VIDEO_DECODER_SUCCESS = 0;
  private static final int VIDEO_DECODER_NO_FRAME = 1;
  private static final int VIDEO_DECODER_ERROR_INVALID_DATA = -1;
  private static final int VIDEO_DECODER_ERROR_OTHER = -2;
  // LINT.ThenChange(../../../../../../../jni/ffmpeg_jni.cc)

  private final String codecName;
  private long nativeContext;
  private volatile @C.VideoOutputMode int outputMode;

  public FfmpegVideoDecoder(
      int numInputBuffers,
      int numOutputBuffers,
      int initialInputBufferSize,
      int threads,
      Format format)
      throws FfmpegDecoderException {
    super(
        new DecoderInputBuffer[numInputBuffers], new VideoDecoderOutputBuffer[numOutputBuffers]);
    if (!FfmpegLibrary.isAvailable()) {
      throw new FfmpegDecoderException("Failed to load decoder native library.");
    }
    codecName = Assertions.checkNotNull(FfmpegLibrary.getCodecName(format.sampleMimeType));
    // DivX 3 and Motion JPEG carry no picture size of their own: it comes from the container.
    nativeContext =
        ffmpegVideoInitialize(
            codecName,
            extraData(format.initializationData),
            threads,
            Math.max(format.width, 0),
            Math.max(format.height, 0));
    if (nativeContext == 0) {
      throw new FfmpegDecoderException("Failed to initialize decoder: " + codecName);
    }
    setInitialInputBufferSize(initialInputBufferSize);
  }

  @Override
  public String getName() {
    return "ffmpeg" + FfmpegLibrary.getVersion() + "-" + codecName;
  }

  /** Sets the output mode for frames rendered by the decoder. */
  public void setOutputMode(@C.VideoOutputMode int outputMode) {
    this.outputMode = outputMode;
  }

  @Override
  protected DecoderInputBuffer createInputBuffer() {
    // FFmpeg reads a little past the end of each packet.
    return new DecoderInputBuffer(
        DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT,
        FfmpegLibrary.getInputBufferPaddingSize());
  }

  @Override
  protected VideoDecoderOutputBuffer createOutputBuffer() {
    return new VideoDecoderOutputBuffer(this::releaseOutputBuffer);
  }

  @Override
  protected FfmpegDecoderException createUnexpectedDecodeException(Throwable error) {
    return new FfmpegDecoderException("Unexpected decode error", error);
  }

  @Override
  @Nullable
  protected FfmpegDecoderException decode(
      DecoderInputBuffer inputBuffer, VideoDecoderOutputBuffer outputBuffer, boolean reset) {
    if (reset) {
      nativeContext = ffmpegVideoReset(nativeContext);
      if (nativeContext == 0) {
        return new FfmpegDecoderException("Error resetting (see logcat).");
      }
    }
    ByteBuffer inputData = Util.castNonNull(inputBuffer.data);
    int sendResult =
        ffmpegVideoSendPacket(nativeContext, inputData, inputData.limit(), inputBuffer.timeUs);
    if (sendResult == VIDEO_DECODER_ERROR_OTHER) {
      return new FfmpegDecoderException("Error sending a packet (see logcat).");
    }
    // A damaged packet (VIDEO_DECODER_ERROR_INVALID_DATA) is skipped: the next one may decode.
    outputBuffer.init(inputBuffer.timeUs, outputMode, /* supplementalData= */ null);
    int frameResult = ffmpegVideoReceiveFrame(nativeContext, outputBuffer);
    if (frameResult == VIDEO_DECODER_ERROR_OTHER) {
      return new FfmpegDecoderException("Error decoding a frame (see logcat).");
    }
    if (frameResult != VIDEO_DECODER_SUCCESS) {
      // No frame yet (reordering), or a frame that couldn't be read: nothing to show.
      outputBuffer.shouldBeSkipped = true;
    }
    return null;
  }

  @Override
  public void release() {
    super.release();
    ffmpegVideoRelease(nativeContext);
    nativeContext = 0;
  }

  /** Copies a decoded frame onto {@code surface}. */
  public void renderToSurface(VideoDecoderOutputBuffer outputBuffer, Surface surface)
      throws FfmpegDecoderException {
    if (outputBuffer.mode != C.VIDEO_OUTPUT_MODE_SURFACE_YUV || outputBuffer.data == null) {
      throw new FfmpegDecoderException("Invalid output mode.");
    }
    int[] strides = Util.castNonNull(outputBuffer.yuvStrides);
    if (ffmpegVideoRenderFrame(
            nativeContext,
            surface,
            outputBuffer.data,
            outputBuffer.width,
            outputBuffer.height,
            strides[0],
            strides[1])
        != VIDEO_DECODER_SUCCESS) {
      throw new FfmpegDecoderException("Buffer render error (see logcat).");
    }
  }

  /** The codec's setup data (an MPEG-4 VOL header, for one), or null when the format has none. */
  @Nullable
  private static byte[] extraData(List<byte[]> initializationData) {
    if (initializationData.isEmpty()) {
      return null;
    }
    int size = 0;
    for (byte[] part : initializationData) {
      size += part.length;
    }
    byte[] data = new byte[size];
    int offset = 0;
    for (byte[] part : initializationData) {
      System.arraycopy(part, 0, data, offset, part.length);
      offset += part.length;
    }
    return data;
  }

  private native long ffmpegVideoInitialize(
      String codecName, @Nullable byte[] extraData, int threads, int width, int height);

  private native long ffmpegVideoReset(long context);

  private native int ffmpegVideoSendPacket(long context, ByteBuffer data, int size, long timeUs);

  private native int ffmpegVideoReceiveFrame(long context, VideoDecoderOutputBuffer outputBuffer);

  private native int ffmpegVideoRenderFrame(
      long context,
      Surface surface,
      ByteBuffer data,
      int width,
      int height,
      int yStride,
      int uvStride);

  private native void ffmpegVideoRelease(long context);
}
