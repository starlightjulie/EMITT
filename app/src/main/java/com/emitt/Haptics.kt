package com.emitt

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
enum class HapticPattern(val label: String, val timings: LongArray, val amps: IntArray) {
    OFF("Off", longArrayOf(), intArrayOf()),
    TICK("Tick", longArrayOf(0, 25), intArrayOf(0, 220)),
    DOUBLE("Double tap", longArrayOf(0, 40, 90, 40), intArrayOf(0, 230, 0, 230)),
    TRIPLE("Triple", longArrayOf(0, 35, 70, 35, 70, 35), intArrayOf(0, 220, 0, 220, 0, 220)),
    HEARTBEAT("Heartbeat", longArrayOf(0, 70, 110, 110, 420, 70, 110, 110), intArrayOf(0, 255, 0, 160, 0, 255, 0, 160)),
    BUZZ("Long buzz", longArrayOf(0, 380), intArrayOf(0, 200)),
    RISING("Rising", longArrayOf(0, 60, 40, 60, 40, 60, 40, 130), intArrayOf(0, 60, 0, 120, 0, 190, 0, 255)),
    FALLING("Falling", longArrayOf(0, 130, 40, 60, 40, 60, 40, 60), intArrayOf(0, 255, 0, 190, 0, 120, 0, 60)),
    PURR("Purr", LongArray(20) { if (it % 2 == 0) 18 else 22 }, IntArray(20) { if (it % 2 == 0) 0 else 110 }),
    SOS(
        "SOS",
        longArrayOf(0, 60, 60, 60, 60, 60, 180, 180, 60, 180, 60, 180, 180, 60, 60, 60, 60, 60),
        intArrayOf(0, 230, 0, 230, 0, 230, 0, 230, 0, 230, 0, 230, 0, 230, 0, 230, 0, 230),
    ),
    ;

    companion object {
        fun of(name: String?, fallback: HapticPattern) = entries.firstOrNull { it.name == name } ?: fallback
    }
}

/** The moments EMITT can vibrate for, each with its own pattern in Settings. */
enum class HapticEvent(val label: String, val hint: String, val default: HapticPattern) {
    WRITE("Tag written or read", "When EMITT writes, reads, erases or locks a tag", HapticPattern.DOUBLE),
    EMULATED("Phone read as a tag", "When another device reads this phone in tag mode", HapticPattern.HEARTBEAT),
    FAIL("Something failed", "A write or read that didn't work", HapticPattern.BUZZ),
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
