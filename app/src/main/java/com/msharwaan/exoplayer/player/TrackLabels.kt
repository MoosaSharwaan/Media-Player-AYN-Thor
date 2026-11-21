package com.msharwaan.exoplayer.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes

/**
 * What the Audio and Subtitles buttons call a track inside the file: language, channels,
 * format and the track's own title, so a film's main track and its commentary can be told
 * apart ("EN · 5.1 · AC3 · Commentary").
 */
internal object TrackLabels {

    fun audio(f: Format): String {
        val ch = when (f.channelCount) {
            1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"
            else -> if (f.channelCount > 0) "${f.channelCount} ch" else null
        }
        return listOfNotNull(language(f), ch, audioFormat(f.sampleMimeType), title(f)).joinToString(" · ").ifEmpty { "Audio" }
    }

    fun subtitle(f: Format): String {
        // Subtitles parsed while the file is read keep their original type in codecs.
        val mime = if (f.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES) f.codecs else f.sampleMimeType
        return listOfNotNull(language(f), subtitleFormat(mime), title(f)).joinToString(" · ").ifEmpty { "Embedded" }
    }

    /** "und" (undetermined) and missing languages are left out rather than shown as UND. */
    private fun language(f: Format): String? = f.language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }?.uppercase()

    private fun title(f: Format): String? = f.label?.takeIf { it.isNotBlank() }

    private fun audioFormat(mime: String?): String? = when (mime) {
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_AC3 -> "AC3"
        MimeTypes.AUDIO_E_AC3 -> "E-AC3"
        MimeTypes.AUDIO_E_AC3_JOC -> "E-AC3 Atmos"
        MimeTypes.AUDIO_AC4 -> "AC4"
        MimeTypes.AUDIO_DTS -> "DTS"
        MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
        MimeTypes.AUDIO_DTS_EXPRESS -> "DTS Express"
        MimeTypes.AUDIO_DTS_X -> "DTS:X"
        MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_ALAC -> "ALAC"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_RAW -> "PCM"
        else -> null
    }

    private fun subtitleFormat(mime: String?): String? = when (mime) {
        MimeTypes.APPLICATION_SUBRIP -> "SRT"
        MimeTypes.TEXT_SSA -> "ASS"
        MimeTypes.TEXT_VTT, MimeTypes.APPLICATION_MP4VTT -> "VTT"
        MimeTypes.APPLICATION_TX3G -> "Text"
        MimeTypes.APPLICATION_TTML -> "TTML"
        MimeTypes.APPLICATION_PGS -> "PGS"
        MimeTypes.APPLICATION_VOBSUB -> "VobSub"
        MimeTypes.APPLICATION_DVBSUBS -> "DVB"
        else -> null
    }
}
