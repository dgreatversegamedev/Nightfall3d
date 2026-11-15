package com.d200.nightfall3d.game

/**
 * One PUNCH button -> cycles punch_1 (right) -> punch_2 (left) -> punch_3 (low) -> punch_1 ...
 * resetAfterMs: if he stops tapping for this long, the next tap starts again at punch_1.
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
