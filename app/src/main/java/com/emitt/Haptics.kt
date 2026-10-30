package com.emitt

import androidx.annotation.StringRes
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.emitt.data.Settings

/**
 * Vibration patterns. [timings] alternate off/on in ms (starting with a pause), [amps] is the
 * strength for each step, 0..255.
 */
enum class HapticPattern(@StringRes val label: Int, val timings: LongArray, val amps: IntArray) {
    OFF(R.string.pat_off, longArrayOf(), intArrayOf()),
    TICK(R.string.pat_tick, longArrayOf(0, 25), intArrayOf(0, 220)),
    DOUBLE(R.string.pat_double, longArrayOf(0, 40, 90, 40), intArrayOf(0, 230, 0, 230)),
    TRIPLE(R.string.pat_triple, longArrayOf(0, 35, 70, 35, 70, 35), intArrayOf(0, 220, 0, 220, 0, 220)),
    HEARTBEAT(R.string.pat_heartbeat, longArrayOf(0, 70, 110, 110, 420, 70, 110, 110), intArrayOf(0, 255, 0, 160, 0, 255, 0, 160)),
    BUZZ(R.string.pat_buzz, longArrayOf(0, 380), intArrayOf(0, 200)),
    RISING(R.string.pat_rising, longArrayOf(0, 60, 40, 60, 40, 60, 40, 130), intArrayOf(0, 60, 0, 120, 0, 190, 0, 255)),
    FALLING(R.string.pat_falling, longArrayOf(0, 130, 40, 60, 40, 60, 40, 60), intArrayOf(0, 255, 0, 190, 0, 120, 0, 60)),
    PURR(R.string.pat_purr, LongArray(20) { if (it % 2 == 0) 18 else 22 }, IntArray(20) { if (it % 2 == 0) 0 else 110 }),
    SOS(
        R.string.pat_sos,
        longArrayOf(0, 60, 60, 60, 60, 60, 180, 180, 60, 180, 60, 180, 180, 60, 60, 60, 60, 60),
        intArrayOf(0, 230, 0, 230, 0, 230, 0, 230, 0, 230, 0, 230, 0, 230, 0, 230, 0, 230),
    ),
    ;

    companion object {
        fun of(name: String?, fallback: HapticPattern) = entries.firstOrNull { it.name == name } ?: fallback
    }
}

/** The moments EMITT can vibrate for, each with its own pattern in Settings. */
enum class HapticEvent(@StringRes val label: Int, @StringRes val hint: Int, val default: HapticPattern) {
    WRITE(R.string.hev_write, R.string.hev_write_hint, HapticPattern.DOUBLE),
    EMULATED(R.string.hev_emulated, R.string.hev_emulated_hint, HapticPattern.HEARTBEAT),
    FAIL(R.string.hev_fail, R.string.hev_fail_hint, HapticPattern.BUZZ),
}

object Haptics {
    fun play(context: Context, event: HapticEvent) {
        val s = Settings(context)
        if (!s.haptics) return
        play(context, s.hapticPattern(event), s.hapticStrength)
    }

    /** Plays [pattern] at [strength] (0..1), e.g. for the Test buttons in Settings. */
    fun play(context: Context, pattern: HapticPattern, strength: Float) {
        if (pattern == HapticPattern.OFF) return
        val v = vibrator(context) ?: return
        runCatching {
            val effect = if (v.hasAmplitudeControl()) {
                val amps = IntArray(pattern.amps.size) { i -> if (pattern.amps[i] == 0) 0 else (pattern.amps[i] * strength).toInt().coerceIn(1, 255) }
                VibrationEffect.createWaveform(pattern.timings, amps, -1)
            } else {
                VibrationEffect.createWaveform(pattern.timings, -1)
            }
            v.cancel()
            v.vibrate(effect)
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
}
