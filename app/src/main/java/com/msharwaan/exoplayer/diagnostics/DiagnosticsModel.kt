package com.msharwaan.exoplayer.diagnostics

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import android.view.KeyEvent
import com.msharwaan.exoplayer.input.PadAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The v0 measurements, as a hidden page inside the app (Select + Start opens it).
 *
 * Each line answers one of the plan's "questions only the real Thor can answer":
 * display modes, per-screen dimming, which codecs are hardware decoded, firmware build,
 * and which window receives which key.
 */
class DiagnosticsModel(private val context: Context) {

    data class DisplayInfo(
        val id: Int,
        val name: String,
        val flags: String,
        val state: String,
        val current: String,
        val modes: List<Mode>,
    )

    data class Mode(val id: Int, val label: String)

    data class Codec(val mime: String, val name: String, val hardware: Boolean)

    /** Buttons on the bottom screen, in D-pad order. */
    enum class Test(val label: String) {
        DIM_BOTTOM("Dim bottom window to 1%"),
        UNDIM_BOTTOM("Undo bottom dim"),
        NEXT_MODE("Top screen: next display mode"),
        RESET_MODE("Top screen: default mode"),
        CLEAR_LOG("Clear key log"),
    }

    private val _displays = MutableStateFlow(readDisplays())
    val displays: StateFlow<List<DisplayInfo>> = _displays.asStateFlow()

    val codecs: List<Codec> by lazy { readCodecs() }

    val firmware: String = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · ${Build.DISPLAY}"

    private val _keyLog = MutableStateFlow<List<String>>(emptyList())
    val keyLog: StateFlow<List<String>> = _keyLog.asStateFlow()

    private val _focus = MutableStateFlow(0)
    val focus: StateFlow<Int> = _focus.asStateFlow()

    /** Brightness requested for the bottom window: -1 means "system default". */
    private val _bottomBrightness = MutableStateFlow(-1f)
    val bottomBrightness: StateFlow<Float> = _bottomBrightness.asStateFlow()

    /** Display mode requested for the top window: 0 means "let the system choose". */
    private val _topModeId = MutableStateFlow(0)
    val topModeId: StateFlow<Int> = _topModeId.asStateFlow()

    fun refreshDisplays() { _displays.value = readDisplays() }

    fun logKey(event: KeyEvent, window: String) {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return
        val line = "${KeyEvent.keyCodeToString(event.keyCode)} → $window (device: ${event.device?.name ?: "?"})"
        _keyLog.value = (listOf(line) + _keyLog.value).take(8)
    }

    fun onAction(action: PadAction): Boolean {
        val tests = Test.entries
        when (action) {
            PadAction.UP -> _focus.value = (_focus.value - 1).coerceAtLeast(0)
            PadAction.DOWN -> _focus.value = (_focus.value + 1).coerceAtMost(tests.size - 1)
            PadAction.A -> run(tests[_focus.value])
            else -> return false
        }
        return true
    }

    fun run(test: Test) {
        when (test) {
            Test.DIM_BOTTOM -> _bottomBrightness.value = 0.01f
            Test.UNDIM_BOTTOM -> _bottomBrightness.value = -1f
            Test.NEXT_MODE -> {
                val modes = _displays.value.firstOrNull { it.id == Display.DEFAULT_DISPLAY }?.modes.orEmpty()
                if (modes.isNotEmpty()) {
                    val index = modes.indexOfFirst { it.id == _topModeId.value }
                    _topModeId.value = modes[(index + 1) % modes.size].id
                }
            }
            Test.RESET_MODE -> _topModeId.value = 0
            Test.CLEAR_LOG -> _keyLog.value = emptyList()
        }
    }

    private fun readDisplays(): List<DisplayInfo> {
        val dm = context.getSystemService(DisplayManager::class.java)
        return dm.displays.map { d ->
            val flags = buildList {
                if (d.flags and Display.FLAG_PRESENTATION != 0) add("presentation")
                if (d.flags and Display.FLAG_PRIVATE != 0) add("private")
                if (d.flags and Display.FLAG_SECURE != 0) add("secure")
            }.joinToString(", ").ifEmpty { "none" }
            val mode = d.mode
            DisplayInfo(
                id = d.displayId,
                name = d.name ?: "?",
                flags = flags,
                state = when (d.state) {
                    Display.STATE_ON -> "on"
                    Display.STATE_OFF -> "off"
                    Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND -> "doze"
                    else -> "state ${d.state}"
                },
                current = "${mode.physicalWidth}×${mode.physicalHeight} @ ${"%.2f".format(mode.refreshRate)} Hz",
                modes = d.supportedModes.map {
                    Mode(it.modeId, "#${it.modeId} ${it.physicalWidth}×${it.physicalHeight} @ ${"%.2f".format(it.refreshRate)} Hz")
                },
            )
        }
    }

    private fun readCodecs(): List<Codec> {
        val wanted = listOf(
            "video/avc", "video/hevc", "video/x-vnd.on2.vp9", "video/av01",
            "audio/ac3", "audio/eac3", "audio/vnd.dts", "audio/true-hd",
        )
        val all = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder }
        return wanted.map { mime ->
            val match = all.filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
            val best = match.firstOrNull { it.isHardwareAccelerated } ?: match.firstOrNull()
            Codec(mime, best?.name ?: "none", best?.isHardwareAccelerated == true)
        }
    }
}
