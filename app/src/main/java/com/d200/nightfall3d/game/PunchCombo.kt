package com.dgreat.nightfallnative   // change to your package

/**
 * One PUNCH button -> cycles punch_1 (right) -> punch_2 (left) -> punch_3 (low) -> punch_1 ...
 * Call nextClip() every time the player taps punch and play the clip name it returns.
 * resetAfterMs: if the player stops tapping for this long, the next tap starts again at punch_1.
 */
class PunchCombo(
    private val clips: List<String> = listOf("punch_1", "punch_2", "punch_3"),
    private val resetAfterMs: Long = 900L
) {
    private var index = 0
    private var lastTapMs = 0L

    fun nextClip(nowMs: Long = System.currentTimeMillis()): String {
        if (nowMs - lastTapMs > resetAfterMs) index = 0
        lastTapMs = nowMs
        val clip = clips[index]
        index = (index + 1) % clips.size
        return clip
    }
}
