package com.d200.nightfall3d.game

import android.content.Context
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The arena rules: walls, crates, barrels... (things he cannot walk through), the ramp platform
 * (things he can walk up) and where bullets stop. Read from app/src/main/assets/arena_play.json.
 *
 * Game3DView uses:  load(context), blueX, blueZ, tryMove(), outX, outZ, groundY(), bulletHits()
 *
 * JUMPING OVER THINGS:
 * tryMove() and groundY() now take an optional feetY = how high his feet are above the ground
 * (0 when standing, higher while jumping). If his feet are above the top of a box, the box no
 * longer blocks him and he lands on top of it. If you do not pass feetY, it is 0 and everything
 * behaves as before, so nothing else breaks.
 */
internal class PlayArena private constructor(
    private val boxes: List<Box>,
    private val ramps: List<Ramp>,
    val blueX: Float,
    val blueZ: Float,
    val redX: Float,
    val redZ: Float,
    private val halfX: Float,
    private val halfZ: Float
) {
    // A box can turn: w is along its own X, d along its own Z, h is the height.
    private class Box(val x: Float, val z: Float, rot: Float, val hw: Float, val hd: Float, val h: Float, val y: Float) {
        val c = cos(rot)
        val s = sin(rot)
        // world -> box coordinates
        fun lx(px: Float, pz: Float) = (px - x) * c - (pz - z) * s
        fun lz(px: Float, pz: Float) = (px - x) * s + (pz - z) * c
        val top: Float get() = y + h
    }

    // Ramp platform: flat top in the middle, a slope of length "run" at both ends (along X)
    private class Ramp(val x: Float, val z: Float, val w: Float, val d: Float, val ph: Float, val run: Float)

    var outX = 0f
    var outZ = 0f

    /** Is the circle around (px,pz) touching this box from above/side? (ignores height) */
    private fun touches(b: Box, px: Float, pz: Float): Boolean {
        val lx = b.lx(px, pz)
        val lz = b.lz(px, pz)
        val cx = max(-b.hw, min(lx, b.hw))
        val cz = max(-b.hd, min(lz, b.hd))
        val dx = lx - cx
        val dz = lz - cz
        return dx * dx + dz * dz < RADIUS * RADIUS
    }

    private fun blocked(px: Float, pz: Float, feetY: Float): Boolean {
        if (abs(px) > halfX - RADIUS || abs(pz) > halfZ - RADIUS) return true
        for (b in boxes) {
            // feet above the top of the box (with a small step) -> he is over it, it does not block
            if (feetY >= b.top - STEP) continue
            // the box starts above his head -> he walks under it, it does not block
            if (b.y >= feetY + BODY_H) continue
            if (touches(b, px, pz)) return true
        }
        return false
    }

    /** Try to walk from (x,z) to (nx,nz). Slides along walls. Result is in outX / outZ. */
    fun tryMove(x: Float, z: Float, nx: Float, nz: Float, feetY: Float = 0f) {
        when {
            !blocked(nx, nz, feetY) -> { outX = nx; outZ = nz }
            !blocked(nx, z, feetY) -> { outX = nx; outZ = z }
            !blocked(x, nz, feetY) -> { outX = x; outZ = nz }
            else -> { outX = x; outZ = z }
        }
    }

    /**
     * Floor height under (x,z): 0 on the ground, up to ph on the ramp platform,
     * and the top of a box if his feet are at (or above) the top of that box.
     */
    fun groundY(x: Float, z: Float, feetY: Float = 0f): Float {
        var best = 0f
        for (r in ramps) {
            if (abs(z - r.z) > r.d / 2f) continue
            val dx = abs(x - r.x)
            val top = r.w / 2f
            val h = when {
                dx <= top -> r.ph
                dx <= top + r.run -> r.ph * (1f - (dx - top) / r.run)
                else -> 0f
            }
            if (h > best) best = h
        }
        for (b in boxes) {
            val t = b.top
            if (t > feetY + STEP) continue      // too high to stand on from here
            if (t <= best) continue
            if (touches(b, x, z)) best = t
        }
        return best
    }

    /** True if a bullet at (x, y, z) hit a wall / crate / barrel. */
    fun bulletHits(x: Float, y: Float, z: Float): Boolean {
        if (abs(x) > halfX || abs(z) > halfZ) return true
        for (b in boxes) {
            if (y < b.y || y > b.y + b.h) continue
            if (abs(b.lx(x, z)) <= b.hw && abs(b.lz(x, z)) <= b.hd) return true
        }
        return false
    }

    companion object {
        private const val RADIUS = 0.35f   // how wide he is
        private const val STEP = 0.30f     // small steps he walks up without jumping, and landing tolerance
        private const val BODY_H = 1.8f    // how tall he is (to walk under things)

        fun load(context: Context, file: String = "arena_play.json"): PlayArena {
            val text = context.assets.open(file).bufferedReader().use { it.readText() }
            val j = JSONObject(text)

            val boxes = ArrayList<Box>()
            val cols = j.getJSONArray("colliders")
            for (i in 0 until cols.length()) {
                val c = cols.getJSONObject(i)
                if (c.getString("type") == "platform") continue   // the ramp platform is walkable
                boxes.add(
                    Box(
                        c.getDouble("x").toFloat(), c.getDouble("z").toFloat(),
                        c.optDouble("rot", 0.0).toFloat(),
                        c.getDouble("w").toFloat() / 2f, c.getDouble("d").toFloat() / 2f,
                        c.getDouble("h").toFloat(), c.optDouble("y", 0.0).toFloat()
                    )
                )
            }

            val ramps = ArrayList<Ramp>()
            val plats = j.optJSONArray("platforms")
            if (plats != null) {
                for (i in 0 until plats.length()) {
                    val p = plats.getJSONObject(i)
                    ramps.add(
                        Ramp(
                            p.getDouble("x").toFloat(), p.getDouble("z").toFloat(),
                            p.getDouble("w").toFloat(), p.getDouble("d").toFloat(),
                            p.getDouble("ph").toFloat(), p.getDouble("run").toFloat()
                        )
                    )
                }
            }

            val pads = j.getJSONObject("pads")
            val blue = pads.getJSONArray("blue")
            val red = pads.getJSONArray("red")
            val half = j.getJSONArray("half")
            return PlayArena(
                boxes, ramps,
                blue.getDouble(0).toFloat(), blue.getDouble(1).toFloat(),
                red.getDouble(0).toFloat(), red.getDouble(1).toFloat(),
                half.getDouble(0).toFloat(), half.getDouble(1).toFloat()
            )
        }
    }
}
