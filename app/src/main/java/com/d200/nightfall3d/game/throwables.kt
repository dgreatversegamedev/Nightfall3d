package com.d200.nightfall3d.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// =====================================================================
// Throwables.kt  -  GRENADE and GAS CAN  (replaces the old file)
//
// Same design as the HTML Throwables Lab:
//  * 3D grenade (ridged olive body, neck, fuse head, lever, pin ring) and 3D gas can (hazard band, valve, red wheel).
//  * GRENADE blast: flash, fireball, smoke column, dust ring, sparks, metal shards, scorch mark.
//  * GAS CAN: leaks while it flies, pops (flash, white vapour jet, lid and valve wheel fly off),
//    then a green cloud of soft round puffs that swirl, spread low along the ground and fade out.
//
// HOW YOU THROW (HudScreen.kt):
//  Press and hold the GRENADE or GAS button, drag away from it to aim. The drag direction is the throw direction
//  (up = forward, relative to the camera) and the drag length is how far it goes (THROW_MIN_DIST .. THROW_MAX_DIST).
//  While you hold, a dotted line shows the flight path, and a ring on the ground shows where it lands and how big
//  the blast (orange) or gas (green) is. Let go to throw. A quick tap throws straight ahead THROW_DEFAULT_DIST metres.
//
// RELOAD: you start with ThrowLoadout.GRENADE_COUNT grenades and GAS_COUNT gas cans. Every used one comes back one at
//  a time: THROW_RELOAD_SEC after the first one was used you get one back, then the timer runs again for the next.
//
// Damage numbers: grenade = MatchState.blast, gas = MatchState.gasHurt (unchanged).
// All numbers are the constants below.
// =====================================================================

object ThrowLoadout {
    var grenadeOn = true             // set on the Weapons screen
    var gasOn = true
    const val GRENADE_COUNT = 3      // how many you start the match with (and the most you can hold)
    const val GAS_COUNT = 2
}

internal const val THROW_MAX = 4           // flying at the same time (per kind)
internal const val THROW_FLASH_MAX = 4     // explosion flashes at the same time
internal const val GAS_MAX = 2             // gas clouds at the same time
internal const val GAS_PUFFS = 48          // round puffs that make one cloud

// ---- aim and throw ----
private const val THROW_MIN_DIST = 3f      // shortest throw (m)
private const val THROW_MAX_DIST = 18f     // longest throw (m)
private const val THROW_DEFAULT_DIST = 8f  // a quick tap
private const val THROW_GRAV = 16f
private const val THROW_COOLDOWN = 0.6f    // seconds between two throws
private const val THROW_HAND_Y = 1.4f      // height he lets go of it
private const val THROW_RELOAD_SEC = 30f   // seconds for ONE used grenade / gas can to come back

private const val GRENADE_FUSE = 2.6f
private const val GRENADE_RADIUS = 4.5f
private const val GRENADE_DMG = 130        // at the centre (bots have 100 HP). Falls to 25% at the edge.

private const val GAS_FUSE = 2.2f
private const val GAS_RADIUS = 4.0f
private const val GAS_SEC = 8f
private const val GAS_DPS = 14f            // HP per second to every enemy inside
private const val GAS_EMIT_SEC = 1.9f      // the can keeps spraying this long after it pops

private const val GUIDE_N = 18             // dots on the aim line
private const val RING_N = 28              // blocks in the landing ring
private const val DEBRIS_N = 14
private const val SCORCH_N = 4
private const val SCORCH_SEC = 22f

// ---- particle pool: one range of nodes for each kind ----
private const val P_F0 = 0;   private const val P_F1 = 36      // fireball
private const val P_S0 = 36;  private const val P_S1 = 66      // smoke
private const val P_D0 = 66;  private const val P_D1 = 90      // dust ring
private const val P_K0 = 90;  private const val P_K1 = 150     // sparks
private const val P_V0 = 150; private const val P_V1 = 174     // white vapour
private const val P_G0 = 174                                   // gas puffs (GAS_MAX * GAS_PUFFS)
private const val PN = P_G0 + GAS_MAX * GAS_PUFFS

internal class ThrowState {
    // flying items
    val alive = BooleanArray(THROW_MAX)
    val kind = IntArray(THROW_MAX)          // 0 grenade, 1 gas
    val x = FloatArray(THROW_MAX)
    val y = FloatArray(THROW_MAX)
    val z = FloatArray(THROW_MAX)
    val vx = FloatArray(THROW_MAX)
    val vy = FloatArray(THROW_MAX)
    val vz = FloatArray(THROW_MAX)
    val fuse = FloatArray(THROW_MAX)
    val leakT = FloatArray(THROW_MAX)

    // flashes
    val flashT = FloatArray(THROW_FLASH_MAX)
    val flashBig = FloatArray(THROW_FLASH_MAX)
    val flashX = FloatArray(THROW_FLASH_MAX)
    val flashY = FloatArray(THROW_FLASH_MAX)
    val flashZ = FloatArray(THROW_FLASH_MAX)

    // gas clouds
    val gasT = FloatArray(GAS_MAX)
    val gasAcc = FloatArray(GAS_MAX)
    val gasX = FloatArray(GAS_MAX)
    val gasY = FloatArray(GAS_MAX)
    val gasZ = FloatArray(GAS_MAX)

    // particles
    val pLife = FloatArray(PN)
    val pMax = FloatArray(PN)
    val pX = FloatArray(PN)
    val pY = FloatArray(PN)
    val pZ = FloatArray(PN)
    val pVX = FloatArray(PN)
    val pVY = FloatArray(PN)
    val pVZ = FloatArray(PN)
    val pS0 = FloatArray(PN)
    val pS1 = FloatArray(PN)
    val pDrag = FloatArray(PN)
    val pGrav = FloatArray(PN)
    val pFloor = FloatArray(PN)
    val pHold = FloatArray(PN)
    val pTurb = FloatArray(PN)
    val pPh = FloatArray(PN)

    // metal pieces
    val dLife = FloatArray(DEBRIS_N)
    val dFloor = FloatArray(DEBRIS_N)
    val dX = FloatArray(DEBRIS_N)
    val dY = FloatArray(DEBRIS_N)
    val dZ = FloatArray(DEBRIS_N)
    val dVX = FloatArray(DEBRIS_N)
    val dVY = FloatArray(DEBRIS_N)
    val dVZ = FloatArray(DEBRIS_N)
    val dRX = FloatArray(DEBRIS_N)
    val dRY = FloatArray(DEBRIS_N)
    val dRZ = FloatArray(DEBRIS_N)
    val dWX = FloatArray(DEBRIS_N)
    val dWY = FloatArray(DEBRIS_N)
    val dWZ = FloatArray(DEBRIS_N)

    // scorch marks
    val sLife = FloatArray(SCORCH_N)
    val sX = FloatArray(SCORCH_N)
    val sY = FloatArray(SCORCH_N)
    val sZ = FloatArray(SCORCH_N)
    var sNext = 0

    var cd = 0f
    var clock = 0f

    // ---- aim: written by the HUD button, read here ----
    var aimOn by mutableStateOf(false)      // finger is holding the button
    var aimKind = 0                          // 0 grenade, 1 gas
    var aimX = 0f                            // unit direction of the drag on the screen (x right, y down)
    var aimY = 0f
    var power = -1f                          // 0..1 drag length. Below 0 = no drag (quick tap)
    var aimDistInt by mutableIntStateOf(0)   // metres, for the HUD text

    // ---- reload ----
    var gTimer = 0f
    var cTimer = 0f
    var gSec by mutableIntStateOf(0)         // seconds until the next grenade is back (0 = full)
    var cSec by mutableIntStateOf(0)

    // work values for the throw
    var wdx = 0f
    var wdz = 1f
    var wdist = THROW_DEFAULT_DIST
    var sx = 0f
    var sy = 0f
    var sz = 0f
    var svx = 0f
    var svy = 0f
    var svz = 0f
    var sT = 1f
    var guideShown = false
}

/**
 * grenades / cans: parent nodes with the model parts inside. fx: round particles (see the P_ ranges).
 * dots + ringG + ringC: the aim guide.
 */
internal class ThrowNodes(
    val grenades: List<Node>,
    val cans: List<Node>,
    val flash: List<SphereNode>,
    val fx: List<SphereNode>,
    val debris: List<CubeNode>,
    val scorch: List<SphereNode>,
    val dots: List<SphereNode>,
    val ringG: List<CubeNode>,
    val ringC: List<CubeNode>
) {
    fun all(): List<Node> = grenades + cans + flash + fx + debris + scorch + dots + ringG + ringC
}

/** Called once from Game3DView. Builds every model and hides it. */
internal fun createThrowNodes(engine: Engine, ml: MaterialLoader): ThrowNodes {
    fun c(r: Float, g: Float, b: Float, a: Float = 1f) = ml.createColorInstance(Color(r, g, b, a))

    val olive = c(0.34f, 0.39f, 0.17f)
    val oliveDark = c(0.20f, 0.24f, 0.10f)
    val steel = c(0.66f, 0.68f, 0.71f)
    val darkMetal = c(0.17f, 0.18f, 0.17f)
    val canBody = c(0.24f, 0.29f, 0.23f)
    val yellow = c(0.91f, 0.78f, 0.10f)
    val gold = c(0.69f, 0.54f, 0.16f)
    val red = c(0.79f, 0.20f, 0.16f)
    val lime = c(0.30f, 1f, 0.42f)

    // ---------- the grenade ----------
    fun grenadeModel(): Node {
        val root = Node(engine)
        val body = SphereNode(engine, radius = 0.07f, materialInstance = olive)
        body.scale = Float3(1f, 1.15f, 1f)
        root.addChildNode(body)
        for (yy in listOf(-0.045f, 0f, 0.045f)) {                       // the ridges
            val band = CylinderNode(engine, radius = 0.071f - abs(yy) * 0.25f, height = 0.012f, materialInstance = oliveDark)
            band.position = Position(0f, yy, 0f)
            root.addChildNode(band)
        }
        val neck = CylinderNode(engine, radius = 0.024f, height = 0.035f, materialInstance = darkMetal)
        neck.position = Position(0f, 0.092f, 0f)
        root.addChildNode(neck)
        val head = CylinderNode(engine, radius = 0.02f, height = 0.032f, materialInstance = steel)
        head.position = Position(0f, 0.124f, 0f)
        root.addChildNode(head)
        val lever = CubeNode(engine, size = Float3(0.014f, 0.15f, 0.006f), materialInstance = steel)
        lever.position = Position(0.058f, 0.035f, 0f)
        lever.rotation = Rotation(z = 14f)
        root.addChildNode(lever)
        val pin = CubeNode(engine, size = Float3(0.032f, 0.032f, 0.005f), materialInstance = steel)
        pin.position = Position(0.04f, 0.124f, 0f)
        root.addChildNode(pin)
        root.isVisible = false
        return root
    }

    // ---------- the gas can ----------
    fun canModel(): Node {
        val root = Node(engine)
        val body = CylinderNode(engine, radius = 0.07f, height = 0.2f, materialInstance = canBody)
        root.addChildNode(body)
        val band = CylinderNode(engine, radius = 0.0715f, height = 0.05f, materialInstance = yellow)
        band.position = Position(0f, 0.03f, 0f)
        root.addChildNode(band)
        val band2 = CylinderNode(engine, radius = 0.0715f, height = 0.02f, materialInstance = darkMetal)
        band2.position = Position(0f, -0.07f, 0f)
        root.addChildNode(band2)
        val shoulder = CylinderNode(engine, radius = 0.05f, height = 0.03f, materialInstance = darkMetal)
        shoulder.position = Position(0f, 0.115f, 0f)
        root.addChildNode(shoulder)
        val valve = CylinderNode(engine, radius = 0.016f, height = 0.04f, materialInstance = gold)
        valve.position = Position(0f, 0.15f, 0f)
        root.addChildNode(valve)
        val w1 = CubeNode(engine, size = Float3(0.07f, 0.01f, 0.012f), materialInstance = red)
        w1.position = Position(0f, 0.175f, 0f)
        root.addChildNode(w1)
        val w2 = CubeNode(engine, size = Float3(0.012f, 0.01f, 0.07f), materialInstance = red)
        w2.position = Position(0f, 0.175f, 0f)
        root.addChildNode(w2)
        val light = SphereNode(engine, radius = 0.014f, materialInstance = lime)
        light.position = Position(0f, 0.045f, 0.072f)
        root.addChildNode(light)
        root.isVisible = false
        return root
    }

    val grenades = List(THROW_MAX) { grenadeModel() }
    val cans = List(THROW_MAX) { canModel() }

    val flashMat = c(1f, 0.93f, 0.7f, 0.9f)
    val flash = List(THROW_FLASH_MAX) { SphereNode(engine, radius = 0.5f, materialInstance = flashMat).apply { isVisible = false } }

    // particles, one material set for each kind
    val fireMats = listOf(c(1f, 0.9f, 0.55f, 0.95f), c(1f, 0.55f, 0.10f, 0.9f), c(0.9f, 0.25f, 0.05f, 0.85f))
    val smokeMats = listOf(c(0.16f, 0.15f, 0.14f, 0.6f), c(0.30f, 0.29f, 0.28f, 0.55f))
    val dustMat = c(0.6f, 0.53f, 0.43f, 0.5f)
    val sparkMat = c(1f, 0.8f, 0.3f)
    val vaporMat = c(0.9f, 1f, 0.85f, 0.5f)
    val gasMats = listOf(c(0.62f, 0.98f, 0.28f, 0.24f), c(0.40f, 0.80f, 0.16f, 0.24f), c(0.52f, 0.9f, 0.22f, 0.24f))
    val fx = List(PN) { i ->
        val m = when {
            i < P_F1 -> fireMats[i % 3]
            i < P_S1 -> smokeMats[i % 2]
            i < P_D1 -> dustMat
            i < P_K1 -> sparkMat
            i < P_G0 -> vaporMat
            else -> gasMats[i % 3]
        }
        SphereNode(engine, radius = 0.5f, materialInstance = m).apply { isVisible = false }
    }

    val shardMat = c(0.17f, 0.18f, 0.16f)
    val debris = List(DEBRIS_N) { CubeNode(engine, size = Float3(0.06f, 0.025f, 0.04f), materialInstance = shardMat).apply { isVisible = false } }

    val scorchMat = c(0.03f, 0.03f, 0.03f, 0.65f)
    val scorch = List(SCORCH_N) { SphereNode(engine, radius = 1f, materialInstance = scorchMat).apply { isVisible = false } }

    val dotMat = c(1f, 1f, 1f, 0.9f)
    val dots = List(GUIDE_N) { SphereNode(engine, radius = 0.5f, materialInstance = dotMat).apply { isVisible = false } }

    val ringGMat = c(1f, 0.45f, 0.1f, 0.85f)
    val ringCMat = c(0.5f, 1f, 0.25f, 0.85f)
    val ringG = List(RING_N) { CubeNode(engine, size = Float3(1.0f, 0.04f, 0.1f), materialInstance = ringGMat).apply { isVisible = false } }
    val ringC = List(RING_N) { CubeNode(engine, size = Float3(0.9f, 0.04f, 0.1f), materialInstance = ringCMat).apply { isVisible = false } }

    return ThrowNodes(grenades, cans, flash, fx, debris, scorch, dots, ringG, ringC)
}

// ---------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------

private fun rr(a: Float, b: Float): Float = a + Random.nextFloat() * (b - a)

private fun spawnP(
    st: ThrowState, from: Int, to: Int,
    x: Float, y: Float, z: Float, vx: Float, vy: Float, vz: Float,
    life: Float, s0: Float, s1: Float, drag: Float, grav: Float, floor: Float, hold: Float, turb: Float = 0f
) {
    var i = from
    while (i < to && st.pLife[i] > 0f) i++
    if (i >= to) return
    st.pLife[i] = life
    st.pMax[i] = life
    st.pX[i] = x; st.pY[i] = y; st.pZ[i] = z
    st.pVX[i] = vx; st.pVY[i] = vy; st.pVZ[i] = vz
    st.pS0[i] = s0; st.pS1[i] = s1
    st.pDrag[i] = drag; st.pGrav[i] = grav
    st.pFloor[i] = floor; st.pHold[i] = hold; st.pTurb[i] = turb
    st.pPh[i] = Random.nextFloat() * 20f
}

private fun spawnDebris(st: ThrowState, x: Float, y: Float, z: Float, vx: Float, vy: Float, vz: Float, life: Float, floor: Float) {
    val i = st.dLife.indexOfFirst { it <= 0f }
    if (i < 0) return
    st.dLife[i] = life
    st.dFloor[i] = floor
    st.dX[i] = x; st.dY[i] = y; st.dZ[i] = z
    st.dVX[i] = vx; st.dVY[i] = vy; st.dVZ[i] = vz
    st.dRX[i] = 0f; st.dRY[i] = 0f; st.dRZ[i] = 0f
    st.dWX[i] = rr(-600f, 600f); st.dWY[i] = rr(-600f, 600f); st.dWZ[i] = rr(-600f, 600f)
}

private fun spawnFlash(st: ThrowState, x: Float, y: Float, z: Float, big: Float) {
    val f = st.flashT.indexOfFirst { it <= 0f }
    if (f < 0) return
    st.flashT[f] = 0.28f
    st.flashBig[f] = big
    st.flashX[f] = x; st.flashY[f] = y; st.flashZ[f] = z
}

private fun spawnScorch(st: ThrowState, x: Float, y: Float, z: Float) {
    val i = st.sNext
    st.sNext = (st.sNext + 1) % SCORCH_N
    st.sLife[i] = SCORCH_SEC
    st.sX[i] = x; st.sY[i] = y + 0.02f; st.sZ[i] = z
}

// ---------------------------------------------------------------------
// explosions
// ---------------------------------------------------------------------

private fun boomGrenade(st: ThrowState, x: Float, y0: Float, z: Float, gy: Float) {
    val y = max(y0, gy + 0.25f)
    val twoPi = 2f * PI.toFloat()
    spawnFlash(st, x, y, z, 5f)
    // fireball
    for (n in 0 until 24) {
        val u = rr(-1f, 1f); val a = rr(0f, twoPi); val s = sqrt(1f - u * u); val sp = rr(2f, 7f)
        spawnP(st, P_F0, P_F1, x + cos(a) * s * 0.2f, y + u * 0.2f, z + sin(a) * s * 0.2f,
            cos(a) * s * sp, abs(u) * sp * 0.7f + 1f, sin(a) * s * sp,
            rr(0.45f, 0.9f), 0.8f, rr(2.6f, 4.4f), 3.4f, -2f, gy + 0.1f, 0.15f)
    }
    // smoke column
    for (n in 0 until 28) {
        val a = rr(0f, twoPi); val sp = rr(0.6f, 3f)
        spawnP(st, P_S0, P_S1, x + cos(a) * 0.3f, y * 0.6f, z + sin(a) * 0.3f,
            cos(a) * sp, rr(1.4f, 3.8f), sin(a) * sp,
            rr(1.8f, 3.6f), 1f, rr(4f, 6.4f), 1.3f, -0.2f, gy + 0.3f, 0.3f, 0.8f)
    }
    // dust ring along the ground
    for (n in 0 until 22) {
        val a = rr(0f, twoPi); val sp = rr(6f, 10f)
        spawnP(st, P_D0, P_D1, x, gy + 0.2f, z, cos(a) * sp, rr(0.2f, 1f), sin(a) * sp,
            rr(0.9f, 1.5f), 0.8f, rr(2.4f, 3.6f), 3.2f, 0f, gy + 0.15f, 0.1f)
    }
    // sparks
    for (n in 0 until 50) {
        val u = rr(-1f, 1f); val a = rr(0f, twoPi); val s = sqrt(1f - u * u); val sp = rr(6f, 17f)
        spawnP(st, P_K0, P_K1, x, y, z, cos(a) * s * sp, abs(u) * sp + 2f, sin(a) * s * sp,
            rr(0.5f, 1.4f), 0.16f, 0.05f, 0.4f, 16f, gy + 0.03f, 0.5f)
    }
    // metal shards
    for (n in 0 until 8) {
        val u = rr(-1f, 1f); val a = rr(0f, twoPi); val s = sqrt(1f - u * u); val sp = rr(6f, 13f)
        spawnDebris(st, x, y, z, cos(a) * s * sp, abs(u) * sp + 3f, sin(a) * s * sp, rr(2.2f, 3.2f), gy)
    }
    spawnScorch(st, x, gy, z)
}

private fun boomGas(st: ThrowState, cloud: Int, x: Float, y0: Float, z: Float, gy: Float) {
    val y = max(y0, gy + 0.2f)
    val twoPi = 2f * PI.toFloat()
    spawnFlash(st, x, y + 0.1f, z, 1.8f)
    // fast white vapour jet
    for (n in 0 until 24) {
        val u = rr(-1f, 1f); val a = rr(0f, twoPi); val s = sqrt(1f - u * u); val sp = rr(4f, 9f)
        spawnP(st, P_V0, P_V1, x, y, z, cos(a) * s * sp, abs(u) * sp * 0.6f + 0.5f, sin(a) * s * sp,
            rr(0.5f, 0.9f), 0.3f, rr(1.2f, 2f), 3f, 0f, gy + 0.05f, 0.2f)
    }
    // lid, valve wheel and a few bits fly off
    for (n in 0 until 6) {
        spawnDebris(st, x, y + 0.1f, z, rr(-4f, 4f), rr(5f, 9f), rr(-4f, 4f), rr(2.2f, 3.2f), gy)
    }
    st.gasT[cloud] = GAS_SEC
    st.gasAcc[cloud] = 0f
    st.gasX[cloud] = x
    st.gasY[cloud] = gy
    st.gasZ[cloud] = z
}

private fun spawnGasPuff(st: ThrowState, cloud: Int, early: Boolean) {
    val a = rr(0f, 2f * PI.toFloat())
    val hs = rr(0.6f, if (early) 6f else 3.2f)
    val from = P_G0 + cloud * GAS_PUFFS
    val gy = st.gasY[cloud]
    spawnP(st, from, from + GAS_PUFFS,
        st.gasX[cloud] + rr(-0.1f, 0.1f), gy + 0.25f + rr(0f, 0.15f), st.gasZ[cloud] + rr(-0.1f, 0.1f),
        cos(a) * hs, rr(-0.2f, 2.4f), sin(a) * hs,
        rr(6.5f, 8f), rr(0.5f, 0.9f), rr(2.6f, 4.4f), 1.5f, -0.02f, gy + 0.25f, 0.55f, 1.4f)
}

// ---------------------------------------------------------------------
// aim: direction, distance, flight path
// ---------------------------------------------------------------------

private fun resolveAim(rig: GameInput, st: ThrowState, faceX: Float, faceZ: Float) {
    var dx: Float
    var dz: Float
    if (st.power >= 0f) {
        val yawR = rig.camYaw * (PI.toFloat() / 180f)
        val fx = sin(yawR)
        val fz = -cos(yawR)
        val rx = cos(yawR)
        val rz = sin(yawR)
        dx = fx * (-st.aimY) + rx * st.aimX
        dz = fz * (-st.aimY) + rz * st.aimX
        st.wdist = THROW_MIN_DIST + st.power * (THROW_MAX_DIST - THROW_MIN_DIST)
    } else {
        dx = faceX
        dz = faceZ
        st.wdist = THROW_DEFAULT_DIST
    }
    val l = hypot(dx, dz).coerceAtLeast(0.001f)
    st.wdx = dx / l
    st.wdz = dz / l
}

/** Works out the start point and the speeds so the item lands wdist metres away. */
private fun solveThrow(rig: GameInput, st: ThrowState, play: PlayArena?) {
    val y0 = rig.y + rig.jumpH + THROW_HAND_Y
    var sx = rig.x + st.wdx * 0.5f
    var sz = rig.z + st.wdz * 0.5f
    if (play != null && play.bulletHits(sx, y0, sz)) { sx = rig.x; sz = rig.z }   // wall right in front: drop it at your feet
    st.sx = sx; st.sy = y0; st.sz = sz
    val tx = sx + st.wdx * st.wdist
    val tz = sz + st.wdz * st.wdist
    val gy = (play?.groundY(tx, tz, y0) ?: rig.y) + 0.1f
    val t = (0.55f + st.wdist * 0.055f).coerceIn(0.7f, 1.7f)
    st.sT = t
    st.svx = st.wdx * st.wdist / t
    st.svz = st.wdz * st.wdist / t
    st.svy = ((gy - y0) + 0.5f * THROW_GRAV * t * t) / t
}

private fun hideGuide(st: ThrowState, nodes: ThrowNodes) {
    if (!st.guideShown) return
    st.guideShown = false
    for (d in nodes.dots) d.isVisible = false
    for (r in nodes.ringG) r.isVisible = false
    for (r in nodes.ringC) r.isVisible = false
}

private fun updateGuide(rig: GameInput, st: ThrowState, nodes: ThrowNodes, play: PlayArena?, faceX: Float, faceZ: Float) {
    resolveAim(rig, st, faceX, faceZ)
    solveThrow(rig, st, play)
    st.aimDistInt = st.wdist.roundToInt()
    st.guideShown = true

    var lx = st.sx
    var ly = st.sy
    var lz = st.sz
    var hitAt = GUIDE_N
    for (i in 0 until GUIDE_N) {
        val t = st.sT * (i + 1) / GUIDE_N
        val px = st.sx + st.svx * t
        val pz = st.sz + st.svz * t
        val py = st.sy + st.svy * t - 0.5f * THROW_GRAV * t * t
        if (play != null && play.bulletHits(px, py, pz)) { hitAt = i; break }
        lx = px; ly = py; lz = pz
        val d = nodes.dots[i]
        d.position = Position(px, py, pz)
        val s = if (i == GUIDE_N - 1) 0.22f else 0.12f
        d.scale = Float3(s, s, s)
        d.isVisible = true
    }
    for (i in hitAt until GUIDE_N) nodes.dots[i].isVisible = false

    // landing ring on the ground: the size of the blast (orange) or the gas (green)
    val gy = (play?.groundY(lx, lz, ly) ?: rig.y) + 0.06f
    val grenade = st.aimKind == 0
    val radius = if (grenade) GRENADE_RADIUS else GAS_RADIUS
    val ring = if (grenade) nodes.ringG else nodes.ringC
    val other = if (grenade) nodes.ringC else nodes.ringG
    for (r in other) if (r.isVisible) r.isVisible = false
    val twoPi = 2f * PI.toFloat()
    for (j in 0 until RING_N) {
        val a = j * twoPi / RING_N
        val n = ring[j]
        n.position = Position(lx + cos(a) * radius, gy, lz + sin(a) * radius)
        n.rotation = Rotation(y = -(a * 180f / PI.toFloat()) - 90f)
        n.isVisible = true
    }
}

// ---------------------------------------------------------------------
// every frame (called from Game3DView.stepGame)
// ---------------------------------------------------------------------

/** dirX/dirZ = the way you aim (or face). Used when the button is only tapped. */
internal fun updateThrowables(dt: Float, rig: GameInput, nodes: ThrowNodes, dirX: Float, dirZ: Float, dead: Boolean) {
    val st = rig.thr
    val play = rig.play
    val mt = rig.match
    st.cd = max(0f, st.cd - dt)
    st.clock += dt

    // ---- reload: used ones come back one at a time ----
    val maxG = if (ThrowLoadout.grenadeOn) ThrowLoadout.GRENADE_COUNT else 0
    if (rig.grenades < maxG) {
        st.gTimer += dt
        if (st.gTimer >= THROW_RELOAD_SEC) { st.gTimer = 0f; rig.grenades = rig.grenades + 1 }
        val sec = ceil(THROW_RELOAD_SEC - st.gTimer).toInt()
        if (rig.grenades < maxG && sec != st.gSec) st.gSec = sec
        if (rig.grenades >= maxG) st.gSec = 0
    } else { st.gTimer = 0f; if (st.gSec != 0) st.gSec = 0 }
    val maxC = if (ThrowLoadout.gasOn) ThrowLoadout.GAS_COUNT else 0
    if (rig.gasCans < maxC) {
        st.cTimer += dt
        if (st.cTimer >= THROW_RELOAD_SEC) { st.cTimer = 0f; rig.gasCans = rig.gasCans + 1 }
        val sec = ceil(THROW_RELOAD_SEC - st.cTimer).toInt()
        if (rig.gasCans < maxC && sec != st.cSec) st.cSec = sec
        if (rig.gasCans >= maxC) st.cSec = 0
    } else { st.cTimer = 0f; if (st.cSec != 0) st.cSec = 0 }

    // ---- aim guide while the finger holds the button ----
    val haveAim = if (st.aimKind == 0) rig.grenades else rig.gasCans
    if (st.aimOn && !dead && haveAim > 0) updateGuide(rig, st, nodes, play, dirX, dirZ) else hideGuide(st, nodes)

    // ---- a throw asked by the HUD button (released) ----
    val req = rig.throwReq
    rig.throwReq = 0
    if (req != 0 && !dead && st.cd <= 0f && play != null) {
        val kind = req - 1
        val have = if (kind == 0) rig.grenades else rig.gasCans
        val slot = st.alive.indexOfFirst { !it }
        if (have > 0 && slot >= 0) {
            if (kind == 0) rig.grenades = rig.grenades - 1 else rig.gasCans = rig.gasCans - 1
            resolveAim(rig, st, dirX, dirZ)
            solveThrow(rig, st, play)
            st.power = -1f
            st.alive[slot] = true
            st.kind[slot] = kind
            st.x[slot] = st.sx
            st.y[slot] = st.sy
            st.z[slot] = st.sz
            st.vx[slot] = st.svx
            st.vy[slot] = st.svy
            st.vz[slot] = st.svz
            st.fuse[slot] = if (kind == 0) GRENADE_FUSE else GAS_FUSE
            st.leakT[slot] = 0f
            st.cd = THROW_COOLDOWN
            // the lever / pin come off when it leaves the hand
            spawnDebris(st, st.sx, st.sy, st.sz, rr(-1f, 1f) + st.svx * 0.3f, rr(2f, 4f), rr(-1f, 1f) + st.svz * 0.3f, 2.5f, rig.y)
        }
    }

    // ---- flying grenades / gas cans ----
    for (i in 0 until THROW_MAX) {
        val nodeA = nodes.grenades[i]
        val nodeB = nodes.cans[i]
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
        var gyHere = rig.y
        if (play != null) {
            // walls: bounce back a little
            if (play.bulletHits(nx, st.y[i], st.z[i])) { st.vx[i] *= -0.4f; nx = st.x[i] }
            if (play.bulletHits(nx, st.y[i], nz)) { st.vz[i] *= -0.4f; nz = st.z[i] }
            // floor: bounce, then roll
            gyHere = play.groundY(nx, nz, st.y[i])
            val g = gyHere + 0.1f
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
                boomGrenade(st, nx, ny, nz, gyHere)
            } else {
                val g = st.gasT.indexOfFirst { it <= 0f }
                if (g >= 0) boomGas(st, g, nx, ny, nz, gyHere)
            }
            continue
        }

        // gas can leaks while it flies
        if (st.kind[i] == 1) {
            st.leakT[i] -= dt
            if (st.leakT[i] <= 0f) {
                st.leakT[i] = 0.05f
                spawnP(st, P_V0, P_V1, nx, ny + 0.1f, nz, rr(-0.6f, 0.6f), rr(0.2f, 0.9f), rr(-0.6f, 0.6f),
                    rr(0.7f, 1.1f), 0.12f, rr(0.6f, 1f), 2f, 0f, gyHere + 0.03f, 0.3f)
            }
        }

        val node = if (st.kind[i] == 0) nodeA else nodeB
        val other = if (st.kind[i] == 0) nodeB else nodeA
        if (other.isVisible) other.isVisible = false
        node.position = Position(nx, ny, nz)
        node.rotation = Rotation(x = st.fuse[i] * 300f, y = st.fuse[i] * 200f)
        node.isVisible = true
    }

    // ---- gas clouds: keep spraying for a moment, then hurt everyone inside ----
    for (g in 0 until GAS_MAX) {
        if (st.gasT[g] <= 0f) continue
        st.gasT[g] -= dt
        val t = GAS_SEC - max(0f, st.gasT[g])
        if (t < GAS_EMIT_SEC) {
            st.gasAcc[g] += (if (t < 0.3f) 60f else 20f) * dt
            while (st.gasAcc[g] >= 1f) {
                st.gasAcc[g] -= 1f
                spawnGasPuff(st, g, t < 0.4f)
            }
        }
        mt?.gasHurt(st.gasX[g], st.gasZ[g], GAS_RADIUS, GAS_DPS, dt)
    }

    updateFlashes(st, nodes, dt)
    updateParticles(st, nodes, dt)
    updateDebris(st, nodes, dt)
    updateScorch(st, nodes, dt)
}

// ---------------------------------------------------------------------
// drawing the effects
// ---------------------------------------------------------------------

private fun updateFlashes(st: ThrowState, nodes: ThrowNodes, dt: Float) {
    for (f in 0 until THROW_FLASH_MAX) {
        val n = nodes.flash[f]
        if (st.flashT[f] <= 0f) { if (n.isVisible) n.isVisible = false; continue }
        st.flashT[f] -= dt
        if (st.flashT[f] <= 0f) { n.isVisible = false; continue }
        val u = 1f - st.flashT[f] / 0.28f
        val s = max(0.01f, (0.6f + st.flashBig[f] * u) * (1f - 0.9f * u * u))
        n.position = Position(st.flashX[f], st.flashY[f], st.flashZ[f])
        n.scale = Float3(s, s, s)
        n.isVisible = true
    }
}

private fun updateParticles(st: ThrowState, nodes: ThrowNodes, dt: Float) {
    val tt = st.clock
    for (i in 0 until PN) {
        val n = nodes.fx[i]
        if (st.pLife[i] <= 0f) { if (n.isVisible) n.isVisible = false; continue }
        st.pLife[i] -= dt
        if (st.pLife[i] <= 0f) { n.isVisible = false; continue }
        val age = st.pMax[i] - st.pLife[i]
        val u = age / st.pMax[i]

        val k = exp(-st.pDrag[i] * dt)
        st.pVX[i] *= k; st.pVY[i] *= k; st.pVZ[i] *= k
        st.pVY[i] -= st.pGrav[i] * dt
        val turb = st.pTurb[i]
        if (turb > 0f) {
            val ph = st.pPh[i]
            st.pVX[i] += sin(tt * 1.7f + ph + st.pZ[i] * 0.8f) * turb * dt
            st.pVZ[i] += cos(tt * 1.3f + ph * 1.3f + st.pX[i] * 0.8f) * turb * dt
            st.pVY[i] += sin(tt * 1.1f + ph * 0.7f) * turb * 0.3f * dt
        }
        st.pX[i] += st.pVX[i] * dt
        st.pY[i] += st.pVY[i] * dt
        st.pZ[i] += st.pVZ[i] * dt
        if (st.pY[i] < st.pFloor[i]) {
            st.pY[i] = st.pFloor[i]
            st.pVY[i] = if (i in P_K0 until P_K1) abs(st.pVY[i]) * 0.45f else max(0f, st.pVY[i])
            st.pVX[i] *= 0.96f
            st.pVZ[i] *= 0.96f
        }
        if (i >= P_G0 && st.pY[i] > st.pFloor[i] + 2.2f) {       // the gas stays low
            st.pY[i] = st.pFloor[i] + 2.2f
            if (st.pVY[i] > 0f) st.pVY[i] = 0f
        }

        val e = 1f - (1f - u) * (1f - u)
        val size = st.pS0[i] + (st.pS1[i] - st.pS0[i]) * e
        val hold = st.pHold[i]
        var fade = if (u < hold) 1f else 1f - (u - hold) / (1f - hold)
        fade *= min(1f, age / 0.25f)                      // grows in, thins out at the end
        val s = max(0.001f, size * fade)
        n.position = Position(st.pX[i], st.pY[i], st.pZ[i])
        n.scale = Float3(s, s, s)
        n.isVisible = true
    }
}

private fun updateDebris(st: ThrowState, nodes: ThrowNodes, dt: Float) {
    for (i in 0 until DEBRIS_N) {
        val n = nodes.debris[i]
        if (st.dLife[i] <= 0f) { if (n.isVisible) n.isVisible = false; continue }
        st.dLife[i] -= dt
        if (st.dLife[i] <= 0f) { n.isVisible = false; continue }
        st.dVY[i] -= THROW_GRAV * dt
        st.dX[i] += st.dVX[i] * dt
        st.dY[i] += st.dVY[i] * dt
        st.dZ[i] += st.dVZ[i] * dt
        val floor = st.dFloor[i] + 0.03f
        if (st.dY[i] < floor) {
            st.dY[i] = floor
            st.dVY[i] = if (abs(st.dVY[i]) > 0.8f) -st.dVY[i] * 0.35f else 0f
            st.dVX[i] *= 0.7f
            st.dVZ[i] *= 0.7f
            st.dWX[i] *= 0.6f; st.dWY[i] *= 0.6f; st.dWZ[i] *= 0.6f
        }
        st.dRX[i] += st.dWX[i] * dt
        st.dRY[i] += st.dWY[i] * dt
        st.dRZ[i] += st.dWZ[i] * dt
        val s = min(1f, st.dLife[i] / 0.5f)
        n.position = Position(st.dX[i], st.dY[i], st.dZ[i])
        n.rotation = Rotation(x = st.dRX[i], y = st.dRY[i], z = st.dRZ[i])
        n.scale = Float3(s, s, s)
        n.isVisible = true
    }
}

private fun updateScorch(st: ThrowState, nodes: ThrowNodes, dt: Float) {
    for (i in 0 until SCORCH_N) {
        val n = nodes.scorch[i]
        if (st.sLife[i] <= 0f) { if (n.isVisible) n.isVisible = false; continue }
        st.sLife[i] -= dt
        if (st.sLife[i] <= 0f) { n.isVisible = false; continue }
        val grow = min(1f, (SCORCH_SEC - st.sLife[i]) / 0.15f)
        val fade = min(1f, st.sLife[i] / 3f)
        val r = 1.9f * grow * fade
        n.position = Position(st.sX[i], st.sY[i], st.sZ[i])
        n.scale = Float3(max(0.01f, r), 0.01f, max(0.01f, r))
        n.isVisible = true
    }
}
