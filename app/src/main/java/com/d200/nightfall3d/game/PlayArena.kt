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
    }

    // Ramp platform: flat top in the middle, a slope of length "run" at both ends (along X)
    private class Ramp(val x: Float, val z: Float, val w: Float, val d: Float, val ph: Float, val run: Float)

    var outX = 0f
    var outZ = 0f

    private fun blocked(px: Float, pz: Float): Boolean {
        if (abs(px) > halfX - RADIUS || abs(pz) > halfZ - RADIUS) return true
        for (b in boxes) {
            val lx = b.lx(px, pz)
            val lz = b.lz(px, pz)
            val cx = max(-b.hw, min(lx, b.hw))
            val cz = max(-b.hd, min(lz, b.hd))
            val dx = lx - cx
            val dz = lz - cz
            if (dx * dx + dz * dz < RADIUS * RADIUS) return true
        }
        return false
    }

    /** Try to walk from (x,z) to (nx,nz). Slides along walls. Result is in outX / outZ. */
    fun tryMove(x: Float, z: Float, nx: Float, nz: Float) {
        when {
            !blocked(nx, nz) -> { outX = nx; outZ = nz }
            !blocked(nx, z) -> { outX = nx; outZ = z }
            !blocked(x, nz) -> { outX = x; outZ = nz }
            else -> { outX = x; outZ = z }
        }
    }

    /** Floor height under (x,z): 0 on the ground, up to ph on the ramp platform. */
    fun groundY(x: Float, z: Float): Float {
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
