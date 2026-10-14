package com.tonewerk.eq.audio

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Holds all audio effects. Because service + UI share one process,
 * UI reads/writes these states directly and the engine applies them.
 * 10-band EQ via DynamicsProcessing (API 28+), legacy Equalizer fallback.
 */
object FxEngine {
    const val MIN_DB = -12f
    const val MAX_DB = 12f
    const val NO_SESSION = -1

    var bandFreqs: FloatArray =
        floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        private set

    val bandGains = mutableStateListOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    var preampDb by mutableStateOf(0f)
    var bass by mutableStateOf(0f)          // 0..1000
    var spatial by mutableStateOf(0f)       // 0..1000
    var loudnessDb by mutableStateOf(0f)    // -15..15
    var enabled by mutableStateOf(true)
    var globalMode by mutableStateOf(false)
    var engineReady by mutableStateOf(false)
    var usesDynamics by mutableStateOf(true)
    var playerSessionId by mutableStateOf(NO_SESSION)
    var fftData by mutableStateOf(ByteArray(0))
    var lastError by mutableStateOf<String?>(null)

    private var dp: DynamicsProcessing? = null
    private var eq: Equalizer? = null
    private var bassFx: BassBoost? = null
    private var virtFx: Virtualizer? = null
    private var loudFx: LoudnessEnhancer? = null
    private var vis: Visualizer? = null
    private var sessionId = NO_SESSION
    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var applyScheduled = false

    fun attach(session: Int, isGlobal: Boolean) {
        release()
        sessionId = session
        globalMode = isGlobal
        lastError = null
        if (tryDynamics(session)) usesDynamics = true
        else if (tryLegacy(session)) usesDynamics = false
        else {
            lastError = if (isGlobal)
                "This device blocked global-session effects. Play something once, then retry."
            else "Audio effects unavailable on this device."
            return
        }
        try { bassFx = BassBoost(0, session) } catch (_: Throwable) {}
        try { virtFx = Virtualizer(0, session) } catch (_: Throwable) {}
        try { loudFx = LoudnessEnhancer(session) } catch (_: Throwable) {}
        engineReady = true
        applyAll()
        startVisualizer()
    }

    /** 10 fixed peaking bands — the pro approach (same as Wavelet). */
    private fun tryDynamics(session: Int): Boolean = try {
        val d = DynamicsProcessing(0, session)
        d.configure(
            DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2, bandFreqs.size, 0, 0,     // channels, preEq / mbc / postEq bands
                true, false, false, true     // stages inited + limiter
            ).build()
        )
        d.enabled = true
        dp = d
        true
    } catch (t: Throwable) {
        Log.w("FxEngine", "DynamicsProcessing failed: ${t.message}")
        dp = null; false
    }

    /** Fallback: device Equalizer (band count/frequencies vary per device). */
    private fun tryLegacy(session: Int): Boolean = try {
        val e = Equalizer(0, session)
        val n = e.numberOfBands.toInt()
        eq = e
        bandFreqs = FloatArray(n) { i -> e.getCenterFreq(i.toShort()) / 1000f }
        while (bandGains.size > n) bandGains.removeAt(bandGains.size - 1)
        while (bandGains.size < n) bandGains.add(0f)
        true
    } catch (t: Throwable) {
        Log.w("FxEngine", "Equalizer failed: ${t.message}")
        eq = null; false
    }

    fun applyAll() {
        val on = enabled
        dp?.let { d ->
            try {
                d.enabled = on
                if (on) {
                    for (i in bandFreqs.indices)
                        d.setPreEqBandAllChannelsTo(
                            i, DynamicsProcessing.EqBand(true, bandFreqs[i].toInt(), bandGains[i])
                        )
                    d.setInputGainAllChannelsTo(preampDb)   // preamp = input gain
                }
            } catch (t: Throwable) { Log.w("FxEngine", "dp apply: ${t.message}") }
        }
        eq?.let { e ->
            try {
                e.enabled = on
                if (on) {
                    val r = e.bandLevelRange
                    for (i in 0 until e.numberOfBands.toInt()) {
                        val cl = bandGains[i].coerceIn(r[0] / 100f, r[1] / 100f)
                        e.setBandLevel(i.toShort(), (cl * 100).toInt().toShort())
                    }
                }
            } catch (t: Throwable) { Log.w("FxEngine", "eq apply: ${t.message}") }
        }
        try { bassFx?.enabled = on; if (on) bassFx?.setStrength(bass.toInt().coerceIn(0, 1000).toShort()) } catch (_: Throwable) {}
        try { virtFx?.enabled = on; if (on) virtFx?.setStrength(spatial.toInt().coerceIn(0, 1000).toShort()) } catch (_: Throwable) {}
        try { loudFx?.enabled = on; if (on) loudFx?.setGain(loudnessDb * 100f) } catch (_: Throwable) {}
    }

    /** Coalesce rapid slider changes to one apply per frame. */
    fun applySoon() {
        if (applyScheduled) return
        applyScheduled = true
        scope.launch { applyScheduled = false; applyAll(); persist() }
    }

    fun setBand(i: Int, db: Float) { if (i in bandGains.indices) { bandGains[i] = db; applySoon() } }
    fun setPreamp(db: Float) { preampDb = db; applySoon() }
    fun setBass(v: Float) { bass = v; applySoon() }
    fun setSpatial(v: Float) { spatial = v; applySoon() }
    fun setLoudness(db: Float) { loudnessDb = db; applySoon() }
    fun setEnabled(on: Boolean) { enabled = on; applySoon() }

    fun startVisualizer() {
        stopVisualizer()
        try {
            val v = Visualizer(if (sessionId == NO_SESSION) 0 else sessionId)
            v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vz: Visualizer?, w: ByteArray?, r: Int) = Unit
                override fun onFftDataCapture(vz: Visualizer?, fft: ByteArray?, r: Int) {
                    if (fft != null) fftData = fft
                }
            }, Visualizer.getMaxCaptureRate(), true, true)
            v.enabled = true
            vis = v
        } catch (t: Throwable) { Log.w("FxEngine", "Visualizer: ${t.message}") }
    }

    fun stopVisualizer() { try { vis?.enabled = false; vis?.release() } catch (_: Throwable) {}; vis = null }

    fun release() {
        stopVisualizer()
        try { dp?.enabled = false; dp?.release() } catch (_: Throwable) {}
        try { eq?.enabled = false; eq?.release() } catch (_: Throwable) {}
        try { bassFx?.release() } catch (_: Throwable) {}
        try { virtFx?.release() } catch (_: Throwable) {}
        try { loudFx?.release() } catch (_: Throwable) {}
        dp = null; eq = null; bassFx = null; virtFx = null; loudFx = null
        engineReady = false; sessionId = NO_SESSION
    }

    fun restore(ctx: Context) {
        appContext = ctx.applicationContext
        val p = ctx.getSharedPreferences("fx", Context.MODE_PRIVATE)
        p.getString("gains", null)?.split(",")?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == bandGains.size }
            ?.forEachIndexed { i, g -> bandGains[i] = g }
        preampDb = p.getFloat("pre", 0f)
        bass = p.getFloat("bass", 0f)
        spatial = p.getFloat("spatial", 0f)
        loudnessDb = p.getFloat("loud", 0f)
        enabled = p.getBoolean("on", true)
    }

    private fun persist() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences("fx", Context.MODE_PRIVATE).edit()
            .putString("gains", bandGains.joinToString(",") { it.toString() })
            .putFloat("pre", preampDb).putFloat("bass", bass)
            .putFloat("spatial", spatial).putFloat("loud", loudnessDb)
            .putBoolean("on", enabled)
            .apply()
    }
}
