package androidx.media3.decoder.ffmpeg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.media3.common.MimeTypes;
import org.junit.Test;

/** Each format goes to the FFmpeg decoder that build.sh compiles in (D49). */
public final class FfmpegLibraryTest {

  @Test
  public void oldVideoFormatsMapToTheirDecoders() {
    assertEquals("mpeg4", FfmpegLibrary.getCodecName(MimeTypes.VIDEO_MP4V));
    assertEquals("mpeg4", FfmpegLibrary.getCodecName(MimeTypes.VIDEO_DIVX));
    assertEquals("msmpeg4v2", FfmpegLibrary.getCodecName(MimeTypes.VIDEO_MP42));
    assertEquals("msmpeg4v3", FfmpegLibrary.getCodecName(MimeTypes.VIDEO_MP43));
    assertEquals("mpeg1video", FfmpegLibrary.getCodecName(MimeTypes.VIDEO_MPEG));
    assertEquals("mpeg2video", FfmpegLibrary.getCodecName(MimeTypes.VIDEO_MPEG2));
    assertEquals("h263", FfmpegLibrary.getCodecName(MimeTypes.VIDEO_H263));
    assertEquals("mjpeg", FfmpegLibrary.getCodecName(MimeTypes.VIDEO_MJPEG));
    assertEquals("h264", FfmpegLibrary.getCodecName(MimeTypes.VIDEO_H264));
  }

  @Test
  public void audioStillMaps() {
    assertEquals("ac3", FfmpegLibrary.getCodecName(MimeTypes.AUDIO_AC3));
    assertEquals("dca", FfmpegLibrary.getCodecName(MimeTypes.AUDIO_DTS));
    assertEquals("truehd", FfmpegLibrary.getCodecName(MimeTypes.AUDIO_TRUEHD));
  }

  @Test
  public void formatsNotBuiltInAreNotClaimed() {
    assertNull(FfmpegLibrary.getCodecName(MimeTypes.VIDEO_AV1));
    assertNull(FfmpegLibrary.getCodecName(MimeTypes.VIDEO_VP9));
  }
}
