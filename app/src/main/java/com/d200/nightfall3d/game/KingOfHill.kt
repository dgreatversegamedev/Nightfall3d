package com.d200.nightfall3d.game

import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CubeNode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// =====================================================================
// KingOfHill.kt  -  King of the Hill (everybody against everybody, points).
//
// RULES (change the numbers below)
//   - One orange circle (the zone) is on the map. Stand inside it ALONE to fill the capture timer.
//   - HILL_CAPTURE_SEC seconds inside = +1 point for you, and the zone moves to a new place.
//   - Two or more fighters inside = CONTESTED: the timer freezes until only one is left.
//   - A different fighter standing alone in it makes the old timer go down (twice as fast) until it is his.
//   - If the one who is capturing gets eliminated AFTER more than half of the time (7.5 s), the zone
//     disappears and moves to a new place. Eliminated before half: the timer just resets, same place.
//   - First to HILL_POINT_LIMIT points wins. When the clock ends, the most points wins.
//
// This file has: Hill (the rules), createHillNodes + updateHill (the drawing: orange ring, gold fill ring, beam).
// The small edits in MatchLogic.kt, HudScreen.kt and Game3DView.kt are in KingOfHill_edits.txt.
// =====================================================================

internal const val HILL_RADIUS = 4f              // size of the circle (meters)
internal const val HILL_CAPTURE_SEC = 15f        // seconds to stand in it for 1 point
internal const val HILL_POINT_LIMIT = 5          // first to this many points wins
private const val HILL_MIN_MOVE = 14f            // the new zone is at least this far from the old one
private const val HILL_RING_SEGS = 48
private const val HILL_FILL_SEGS = 36
private const val HILL_K_RAD = 180f / PI.toFloat()

internal class Hill {
    var x = 0f
    var y = 0f
    var z = 0f
    var holder = -1              // fighter id who is capturing (0 = you), -1 = nobody
    var progress = 0f            // seconds he has captured so far
    var contested = false
    var ready = false
    var points = IntArray(0)     // points of each fighter (index = fighter id)
    var drawnX = Float.NaN       // used by updateHill so the ring is placed only when the zone moves
    var drawnZ = Float.NaN

    fun init(play: PlayArena, mt: MatchState) {
        points = IntArray(mt.bots.size + 1)
        place(play, true)
        ready = true
    }

    private fun inside(px: Float, pz: Float) = hypot(px - x, pz - z) <= HILL_RADIUS
    private fun alive(id: Int, mt: MatchState) = if (id == 0) !mt.playerDead else mt.bots[id - 1].alive
    private fun name(id: Int) = if (id == 0) "YOU" else "BOT $id"

    // ---------------- choosing a place ----------------
    // The whole circle is on the normal floor with no wall inside it
    private fun open(play: PlayArena, px: Float, pz: Float): Boolean {
        val floor = play.groundY(play.blueX, play.blueZ, 0.3f)
        val g = play.groundY(px, pz, 0.3f)
        if (g > floor + 0.5f) return false
        for (h in floatArrayOf(g + 0.5f, g + 1.3f)) {
            if (play.bulletHits(px, h, pz)) return false
            for (k in 0 until 8) {
                val a = k * (PI.toFloat() / 4f)
                if (play.bulletHits(px + cos(a) * HILL_RADIUS * 0.8f, h, pz + sin(a) * HILL_RADIUS * 0.8f)) return false
            }
        }
        return true
    }

    private fun line(play: PlayArena, x0: Float, z0: Float, x1: Float, z1: Float): Boolean {
        val d = hypot(x1 - x0, z1 - z0)
        val n = (d / 0.4f).toInt().coerceAtLeast(1)
        for (i in 1 until n) {
            val t = i.toFloat() / n
            if (play.bulletHits(x0 + (x1 - x0) * t, 1.2f, z0 + (z1 - z0) * t)) return false
        }
        return true
    }

    private fun put(play: PlayArena, px: Float, pz: Float) {
        x = px
        z = pz
        y = play.groundY(px, pz, 0.3f)
        holder = -1
        progress = 0f
        contested = false
    }

    private fun place(play: PlayArena, first: Boolean) {
        val r = max(max(abs(play.blueX), abs(play.blueZ)), 8f) * 0.9f
        for (t in 0 until 200) {
            val px = if (first && t == 0) 0f else (Random.nextFloat() * 2f - 1f) * r
            val pz = if (first && t == 0) 0f else (Random.nextFloat() * 2f - 1f) * r
            if (!first && hypot(px - x, pz - z) < HILL_MIN_MOVE) continue
            if (!open(play, px, pz)) continue
            // reachable: a clear line from the blue spawn or from the middle of the map
            if (!line(play, play.blueX, play.blueZ, px, pz) && !line(play, 0f, 0f, px, pz)) continue
            put(play, px, pz)
            return
        }
        put(play, (play.blueX + play.redX) / 2f, (play.blueZ + play.redZ) / 2f)   // fallback: between the two bases
    }

    // ---------------- every frame ----------------
    fun update(dt: Float, mt: MatchState, rig: GameInput, play: PlayArena) {
        if (!ready || mt.over) return

        // the one who was capturing got eliminated
        if (holder >= 0 && !alive(holder, mt)) {
            if (progress >= HILL_CAPTURE_SEC / 2f) {
                mt.say("${name(holder)} was eliminated - the zone moved")
                place(play, false)
            } else {
                holder = -1
                progress = 0f
            }
        }

        var count = 0
        var only = -1
        if (!mt.playerDead && inside(rig.x, rig.z)) { count++; only = 0 }
        for (b in mt.bots) if (b.alive && inside(b.x, b.z)) { count++; only = b.id }

        if (count == 0) {
            contested = false
            progress = max(0f, progress - dt * 1.5f)
            if (progress <= 0f) holder = -1
        } else if (count >= 2) {
            contested = true                      // timer frozen
        } else {
            contested = false
            if (holder == only || holder < 0) {
                holder = only
                progress += dt
            } else {
                progress -= dt * 2f               // somebody else is taking it over
                if (progress <= 0f) { holder = only; progress = 0f }
            }
        }

        if (holder >= 0 && progress >= HILL_CAPTURE_SEC) {
            val id = holder
            points[id]++
            mt.board++
            mt.say("${name(id)} scored a point")
            if (points[id] >= HILL_POINT_LIMIT) {
                mt.finish(if (id == 0) "YOU WIN" else "${name(id)} WINS")
                return
            }
            place(play, false)
        }

        val d = hypot(rig.x - x, rig.z - z).toInt()
        val pct = (progress / HILL_CAPTURE_SEC * 100f).toInt()
        val txt = when {
            mt.playerDead -> ""
            contested -> "CONTESTED - FIGHT FOR IT"
            holder == 0 -> "HOLD THE ZONE  ${ceil(HILL_CAPTURE_SEC - progress).toInt()} s"
            !mt.playerDead && inside(rig.x, rig.z) -> "TAKING THE ZONE..."
            holder > 0 -> "${name(holder)} IS CAPTURING  $pct%"
            else -> "GO TO THE ZONE  $d m"
        }
        if (txt != mt.objText) mt.objText = txt
    }

    // The clock ended: most points wins
    fun endByTime(mt: MatchState) {
        var best = 0
        var tie = false
        for (id in 1 until points.size) {
            if (points[id] > points[best]) { best = id; tie = false }
            else if (points[id] == points[best]) tie = true
        }
        mt.finish(if (tie) "DRAW" else if (best == 0) "YOU WIN" else "BOT $best WINS")
    }
}

// ---------------- drawing ----------------
// nodes: 0..47 orange ring | 48..83 gold fill ring (shows the capture progress) | last = light beam

internal fun createHillNodes(engine: Engine, materialLoader: MaterialLoader): List<CubeNode> {
    val orange = materialLoader.createColorInstance(Color(0xFFFF8A00))
    val gold = materialLoader.createColorInstance(Color(0xFFFFE066))
    val list = ArrayList<CubeNode>()
    val outerLen = (2f * PI.toFloat() * HILL_RADIUS / HILL_RING_SEGS) * 1.08f
    repeat(HILL_RING_SEGS) {
        list.add(CubeNode(engine, size = Float3(outerLen, 0.06f, 0.22f), materialInstance = orange))
    }
    val innerLen = (2f * PI.toFloat() * HILL_RADIUS * 0.82f / HILL_FILL_SEGS) * 1.08f
    repeat(HILL_FILL_SEGS) {
        list.add(CubeNode(engine, size = Float3(innerLen, 0.08f, 0.3f), materialInstance = gold))
    }
    list.add(CubeNode(engine, size = Float3(0.05f, 12f, 0.05f), materialInstance = orange))
    list.forEach { it.isVisible = false }
    return list
}

internal fun updateHill(mt: MatchState?, nodes: List<CubeNode>, nanos: Long) {
    val h = mt?.hill
    if (mt == null || h == null || !h.ready) {
        for (n in nodes) if (n.isVisible) n.isVisible = false
        return
    }
    val full = 2f * PI.toFloat()
    if (h.drawnX != h.x || h.drawnZ != h.z) {         // the zone moved: put the rings in the new place
        h.drawnX = h.x
        h.drawnZ = h.z
        for (i in 0 until HILL_RING_SEGS) {
            val a = full * i / HILL_RING_SEGS
            val n = nodes[i]
            n.position = Position(h.x + cos(a) * HILL_RADIUS, h.y + 0.06f, h.z + sin(a) * HILL_RADIUS)
            n.rotation = Rotation(y = atan2(-cos(a), -sin(a)) * HILL_K_RAD)
            n.isVisible = true
        }
        for (i in 0 until HILL_FILL_SEGS) {
            val a = -PI.toFloat() / 2f + full * i / HILL_FILL_SEGS
            val n = nodes[HILL_RING_SEGS + i]
            val r = HILL_RADIUS * 0.82f
            n.position = Position(h.x + cos(a) * r, h.y + 0.08f, h.z + sin(a) * r)
            n.rotation = Rotation(y = atan2(-cos(a), -sin(a)) * HILL_K_RAD)
        }
        val beam = nodes[nodes.size - 1]
        beam.position = Position(h.x, h.y + 6f, h.z)
        beam.isVisible = true
    }
    val lit = (h.progress / HILL_CAPTURE_SEC * HILL_FILL_SEGS).toInt()
    for (i in 0 until HILL_FILL_SEGS) {
        val n = nodes[HILL_RING_SEGS + i]
        val on = i < lit
        if (n.isVisible != on) n.isVisible = on
    }
}
