package com.msharwaan.exoplayer.player

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * The volume bar: Android's media volume up to 100 %, then a boost to 200 % (FEATURES:
 * volume boost) through a LoudnessEnhancer on the player's audio. Changes are made without
 * Android's volume pop-up, so nothing is drawn over the film (rule R1).
 */
class VolumeControl(context: Context, private val audioSessionId: () -> Int) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    private var boostStep = 0
    private var enhancer: LoudnessEnhancer? = null

    /** 0–100: media volume; 125–200: boosted. */
    private val _percent = MutableStateFlow(read())
    val percent: StateFlow<Int> = _percent.asStateFlow()

    // The Thor's volume buttons outside playback, or another app, can change the volume too.
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    init {
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
    }

    private val current get() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

    fun up() {
        when {
            current < max -> audio.setStreamVolume(AudioManager.STREAM_MUSIC, current + 1, 0)
            boostStep < BOOST_STEPS -> { boostStep++; applyBoost() }
        }
        refresh()
    }

    fun down() {
        when {
            boostStep > 0 -> { boostStep--; applyBoost() }
            current > 0 -> audio.setStreamVolume(AudioManager.STREAM_MUSIC, current - 1, 0)
        }
        refresh()
    }

    fun refresh() {
        // Volume turned down elsewhere while boosted: the boost goes too.
        if (boostStep > 0 && current < max) { boostStep = 0; applyBoost() }
        _percent.value = read()
    }

    /** A new file can bring a new audio session; the boost moves to it. */
    fun onAudioSessionChanged() {
        enhancer?.release()
        enhancer = null
        applyBoost()
    }

    private fun read(): Int = if (boostStep > 0) 100 + boostStep * 100 / BOOST_STEPS else (current * 100f / max).roundToInt()

    private fun applyBoost() {
        if (boostStep == 0) { enhancer?.enabled = false; return }
        val session = audioSessionId().takeIf { it > 0 } ?: return
        val e = enhancer ?: runCatching { LoudnessEnhancer(session) }.getOrNull()?.also { enhancer = it } ?: return
        // 125 % → +1.9 dB … 200 % → +6 dB, in millibels.
        val factor = 1f + boostStep.toFloat() / BOOST_STEPS
        e.setTargetGain((2000 * log10(factor)).roundToInt())
        e.enabled = true
    }

    fun release(context: Context) {
        context.contentResolver.unregisterContentObserver(observer)
        enhancer?.release()
        enhancer = null
    }

    companion object {
        /** 125, 150, 175, 200 %. */
        const val BOOST_STEPS = 4
    }
}
