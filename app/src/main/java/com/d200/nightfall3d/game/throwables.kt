package com.d200.nightfall3d.game

import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CubeNode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// =====================================================================
// Throwables.kt  -  GRENADE and GAS (new file)
//
//   Weapons screen > GRENADES tab: take / remove each one (ThrowLoadout below).
//   Match screen: the GRENADE button (left) throws a grenade, the GAS button throws a gas can.
//   You throw it the way you aim (aim ring held) or the way you face. It flies in an arc, bounces off the
//   floor and walls, and goes off after its fuse.
//
//   GRENADE: explodes after GRENADE_FUSE seconds. Damage drops with distance. Walls block it (MatchState.blast).
//   GAS    : pops after GAS_FUSE seconds and leaves a green cloud for GAS_SEC seconds that hurts every enemy
//            standing inside it (MatchState.gasHurt).
//   Bots are hurt. Your team and you are not (no self damage for now).
//   All the numbers are the constants right below. Change them here.
// =====================================================================

object ThrowLoadout {
    var grenadeOn = true             // set on the Weapons screen
    var gasOn = true
    const val GRENADE_COUNT = 3      // how many you start the match with
    const val GAS_COUNT = 2
}

internal const val THROW_MAX = 4           // flying at the same time (per kind)
internal const val THROW_FLASH_MAX = 4     // explosion flashes at the same time
internal const val GAS_MAX = 2             // gas clouds at the same time
internal const val GAS_PUFFS = 14          // cubes that make one cloud

private const val THROW_SPEED = 11f        // forward speed (m/s)
private const val THROW_UP = 5.5f          // upward speed
private const val THROW_GRAV = 16f
private const val THROW_COOLDOWN = 0.6f    // seconds between two throws
private const val THROW_HAND_Y = 1.4f      // height he lets go of it

private const val GRENADE_FUSE = 2.0f
private const val GRENADE_RADIUS = 4.5f
private const val GRENADE_DMG = 130        // at the centre (bots have 100 HP). Falls to 25% at the edge.
private const val FLASH_SEC = 0.28f
private const val FLASH_MAX_SIZE = 3.2f

private const val GAS_FUSE = 1.1f
private const val GAS_RADIUS = 4.0f
private const val GAS_SEC = 8f
private const val GAS_DPS = 14f            // HP per second to every enemy inside

internal class ThrowState {
    val alive = BooleanArray(THROW_MAX)
    val kind = IntArray(THROW_MAX)          // 0 grenade, 1 gas
    val x = FloatArray(THROW_MAX)
    val y = FloatArray(THROW_MAX)
    val z = FloatArray(THROW_MAX)
    val vx = FloatArray(THROW_MAX)
    val vy = FloatArray(THROW_MAX)
    val vz = FloatArray(THROW_MAX)
    val fuse = FloatArray(THROW_MAX)
    val flashT = FloatArray(THROW_FLASH_MAX)
    val flashX = FloatArray(THROW_FLASH_MAX)
    val flashY = FloatArray(THROW_FLASH_MAX)
    val flashZ = FloatArray(THROW_FLASH_MAX)
    val gasT = FloatArray(GAS_MAX)
    val gasX = FloatArray(GAS_MAX)
    val gasY = FloatArray(GAS_MAX)
    val gasZ = FloatArray(GAS_MAX)
    var cd = 0f
}

/** bombs: 0..THROW_MAX-1 = grenade models, THROW_MAX.. = gas can models. */
internal class ThrowNodes(val bombs: List<CubeNode>, val flash: List<CubeNode>, val puffs: List<CubeNode>) {
    fun all(): List<CubeNode> = bombs + flash + puffs
}

/** Called once per frame from Game3DView.stepGame. dirX/dirZ = the way you aim (or face). */
internal fun updateThrowables(dt: Float, rig: GameInput, nodes: ThrowNodes, dirX: Float, dirZ: Float, dead: Boolean) {
    val st = rig.thr
    val play = rig.play
    val mt = rig.match
    st.cd = max(0f, st.cd - dt)

    // ---- a throw asked by the HUD button ----
    val req = rig.throwReq
    rig.throwReq = 0
    if (req != 0 && !dead && st.cd <= 0f && play != null) {
        val kind = req - 1
        val have = if (kind == 0) rig.grenades else rig.gasCans
        val slot = st.alive.indexOfFirst { !it }
        if (have > 0 && slot >= 0) {
            if (kind == 0) rig.grenades = rig.grenades - 1 else rig.gasCans = rig.gasCans - 1
            val len = hypot(dirX, dirZ).coerceAtLeast(0.001f)
            val dx = dirX / len
            val dz = dirZ / len
            val y0 = rig.y + rig.jumpH + THROW_HAND_Y
            var sx = rig.x + dx * 0.5f
            var sz = rig.z + dz * 0.5f
            if (play.bulletHits(sx, y0, sz)) { sx = rig.x; sz = rig.z }      // wall right in front: drop it at your feet
            st.alive[slot] = true
            st.kind[slot] = kind
            st.x[slot] = sx
            st.y[slot] = y0
            st.z[slot] = sz
            st.vx[slot] = dx * THROW_SPEED
            st.vz[slot] = dz * THROW_SPEED
            st.vy[slot] = THROW_UP
            st.fuse[slot] = if (kind == 0) GRENADE_FUSE else GAS_FUSE
            st.cd = THROW_COOLDOWN
        }
    }

    // ---- flying grenades / gas cans ----
    for (i in 0 until THROW_MAX) {
        val nodeA = nodes.bombs[i]
        val nodeB = nodes.bombs[THROW_MAX + i]
        if (!st.alive[i]) {
            if (nodeA.isVisible) nodeA.isVisible = false
            if (nodeB.isVisible) nodeB.isVisible = false
            continue
        }
        st.fuse[i] -= dt
        st.vy[i] -= THROW_GRAV * dt
        var nx = st.x[i] + st.vx[i] * dt
        var nz = st.z[i] + st.vz[i] * dt
        var ny = st.y[i] + st.vy[i] * dt
        if (play != null) {
            // walls: bounce back a little
            if (play.bulletHits(nx, st.y[i], st.z[i])) { st.vx[i] *= -0.4f; nx = st.x[i] }
            if (play.bulletHits(nx, st.y[i], nz)) { st.vz[i] *= -0.4f; nz = st.z[i] }
            // floor: bounce, then roll
            val g = play.groundY(nx, nz, st.y[i]) + 0.1f
            if (ny <= g) {
                ny = g
                st.vy[i] = if (abs(st.vy[i]) > 1.5f) -st.vy[i] * 0.35f else 0f
                st.vx[i] *= 0.7f
                st.vz[i] *= 0.7f
            }
        }
        st.x[i] = nx
        st.y[i] = ny
        st.z[i] = nz

        if (st.fuse[i] <= 0f) {
            st.alive[i] = false
            nodeA.isVisible = false
            nodeB.isVisible = false
            if (st.kind[i] == 0) {
                mt?.blast(nx, ny, nz, GRENADE_RADIUS, GRENADE_DMG, rig)
                val f = st.flashT.indexOfFirst { it <= 0f }
                if (f >= 0) {
                    st.flashT[f] = FLASH_SEC
                    st.flashX[f] = nx
                    st.flashY[f] = ny + 0.3f
                    st.flashZ[f] = nz
                }
            } else {
                val g = st.gasT.indexOfFirst { it <= 0f }
                if (g >= 0) {
                    st.gasT[g] = GAS_SEC
                    st.gasX[g] = nx
                    st.gasY[g] = ny
                    st.gasZ[g] = nz
                }
            }
            continue
        }
        val node = if (st.kind[i] == 0) nodeA else nodeB
        val other = if (st.kind[i] == 0) nodeB else nodeA
        if (other.isVisible) other.isVisible = false
        node.position = Position(nx, ny, nz)
        node.rotation = Rotation(x = st.fuse[i] * 300f, y = st.fuse[i] * 200f)
        node.isVisible = true
    }

    // ---- explosion flashes ----
    for (f in 0 until THROW_FLASH_MAX) {
        val n = nodes.flash[f]
        if (st.flashT[f] <= 0f) { if (n.isVisible) n.isVisible = false; continue }
        st.flashT[f] -= dt
        if (st.flashT[f] <= 0f) { n.isVisible = false; continue }
        val u = 1f - st.flashT[f] / FLASH_SEC
        val s = 0.6f + FLASH_MAX_SIZE * u
        n.position = Position(st.flashX[f], st.flashY[f], st.flashZ[f])
        n.scale = Float3(s, s, s)
        n.isVisible = true
    }

    // ---- gas clouds ----
    for (g in 0 until GAS_MAX) {
        val first = g * GAS_PUFFS
        if (st.gasT[g] <= 0f) {
            if (nodes.puffs[first].isVisible) for (k in 0 until GAS_PUFFS) nodes.puffs[first + k].isVisible = false
            continue
        }
        st.gasT[g] -= dt
        val left = max(0f, st.gasT[g])
        val t = GAS_SEC - left
        mt?.gasHurt(st.gasX[g], st.gasZ[g], GAS_RADIUS, GAS_DPS, dt)
        val grow = min(1f, min(t / 0.8f, left / 1.5f))        // grows in, thins out at the end
        for (k in 0 until GAS_PUFFS) {
            val n = nodes.puffs[first + k]
            if (grow <= 0.02f) { n.isVisible = false; continue }
            val a = k * (2f * PI.toFloat() / GAS_PUFFS) + t * 0.6f
            val r = GAS_RADIUS * (0.2f + 0.7f * (((k * 37) % 10) / 10f))
            val h = 0.2f + 1.1f * (((k * 53) % 10) / 10f) + 0.15f * sin(t * 2f + k)
            val s = grow * (0.9f + 0.35f * sin(t * 1.5f + k * 1.7f))
            n.position = Position(st.gasX[g] + cos(a) * r, st.gasY[g] + h, st.gasZ[g] + sin(a) * r)
            n.rotation = Rotation(y = t * 40f + k * 30f)
            n.scale = Float3(s, s, s)
            n.isVisible = true
        }
    }
}
