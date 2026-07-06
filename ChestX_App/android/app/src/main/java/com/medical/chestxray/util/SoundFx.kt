package com.medical.chestxray.util

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Tiny, tasteful UI sound effects synthesized with the system [ToneGenerator] — no
 * audio assets required, and it rides the device's system-sound volume so it stays
 * subtle. Each interaction gets a distinct, short blip.
 *
 * Toggle globally via [enabled] (wired to a Settings switch).
 */
object SoundFx {

    /** Master switch. When false, all playback is a no-op. */
    @Volatile
    var enabled: Boolean = true

    // Kept low (0–100) so the effects are a gentle accent, not a distraction.
    private const val VOLUME = 55

    private var generator: ToneGenerator? = null

    private fun tone(): ToneGenerator? {
        if (generator == null) {
            generator = try {
                ToneGenerator(AudioManager.STREAM_SYSTEM, VOLUME)
            } catch (e: RuntimeException) {
                null // Audio system busy / unavailable — fail silently.
            }
        }
        return generator
    }

    private fun play(toneType: Int, durationMs: Int) {
        if (!enabled) return
        try {
            tone()?.startTone(toneType, durationMs)
        } catch (e: Exception) {
            // Never let a cosmetic sound crash the UI.
        }
    }

    /** Light tap for button presses. */
    fun click() = play(ToneGenerator.TONE_CDMA_PIP, 40)

    /** Soft blip when switching bottom-nav tabs. */
    fun tab() = play(ToneGenerator.TONE_PROP_BEEP, 60)

    /** Gentle cue when a long operation begins (e.g. analysis). */
    fun loading() = play(ToneGenerator.TONE_PROP_BEEP2, 90)

    /** Pleasant double-beep on a completed / successful action. */
    fun success() = play(ToneGenerator.TONE_PROP_ACK, 150)

    /** Distinct error cue. */
    fun error() = play(ToneGenerator.TONE_SUP_ERROR, 200)

    /** Release the underlying generator (optional; safe to skip for a singleton). */
    fun release() {
        generator?.release()
        generator = null
    }
}
