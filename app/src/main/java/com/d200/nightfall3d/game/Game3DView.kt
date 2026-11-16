package com.d200.nightfall3d.game

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.gltfio.Animator
import com.google.android.filament.IndirectLight
import com.google.android.filament.Skybox
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.Scene
import io.github.sceneview.environment.Environment
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// =====================================================================
// Game3DView.kt  -  the 3D ENGINE (no buttons in here).
// It draws the arena + character + gun, moves him, plays the animations, shoots bullets and turns the camera.
// The HUD (HudScreen.kt) only writes into one GameInput object (see the class below).
//
// Needs in app/src/main/assets/ : arena_play.glb, arena_play.json, char_blue.glb
// Optional (gun in hand): mp5_gun.glb, ak47_gun.glb, ar_gun.glb, rifle.glb, carbine.glb
//
// CHANGES IN THIS VERSION
//  1) SHIELD is now a SOLID energy bubble: a blue glowing translucent ball (so you really look "inside" a shield and
//     everything behind it is tinted blue), a soft outer glow layer, thick glowing hex lines on the surface, and a
//     glowing disc on the floor under your feet. A hit flashes the whole ball bright and rolls a ripple over it.
//  2) FLAGS now have a real cloth banner: 24 thin overlapping strips, each one leaning along the wave, so it reads as
//     one smooth waving cloth (not 10 separate rectangles).
// =====================================================================

// ---- Tuning numbers: change these if something looks wrong ----
private const val CHAR_HEIGHT = 1.8f
private const val FACING_OFFSET = 0f
private const val WALK_SPEED = 2.2f
private const val TURN_SPEED = 540f
private const val AIM_TURN_SPEED = 1080f
private const val ACCEL = 9f
private const val BLEND_TIME = 0.25f
private const val BLEND_FLIP = false
private const val RUN_SPEED = 4.6f
private const val CAM_DIST = 3.2f
private const val CAM_HEIGHT = 1.5f
private const val CAM_LOOK_AHEAD = 6f
private const val CAM_LOOK_Y = 1.0f
private const val ZOOM_MIN = 0.4f
private const val ZOOM_MAX = 3f
private const val CAM_DRAG_SPEED = 0.25f
private const val CAM_PITCH_SPEED = 0.2f
private const val CAM_PITCH_MAX = 55f
private const val AIM_DEADZONE = 0.15f
private const val BACKPEDAL_DOT = -0.2f
private const val RAD_TO_DEG = 180f / PI.toFloat()
private const val DEG_TO_RAD = PI.toFloat() / 180f

// ---- Scope ----
private const val SCOPE_DIST = 0.35f
private const val SCOPE_DRAG = 0.4f

// ---- Bullets ----
private const val BULLET_COUNT = 24
private const val BULLET_SPEED = 40f
private const val BULLET_RANGE = 50f
private const val FIRE_INTERVAL = 0.12f
private const val MUZZLE_FORWARD = 0.5f
private const val AIM_POINT_DIST = 12f
private const val BULLET_THICK = 0.03f
private const val BULLET_LEN = 0.9f
private const val FLASH_TIME = 0.06f
private const val BULLET_HEIGHT = 1.15f
private const val BULLET_START_FORWARD = 0.9f
private const val BULLET_START_SIDE = 0.25f

// ---- Hit effects ----
private const val FX_HALF = 24
private const val FX_COUNT = 88
private const val BLOOD_PIECES = 14
private const val SPARK_PIECES = 5

// ---- Gun in the right hand ----
private const val RIGHT_HAND_JOINT = 14

// ---- Punch and jump ----
private const val PUNCH_LEN = 0.5f
private const val PUNCH_CHAIN_AT = 0.3f
private const val JUMP_CLIP = "jump_inplace"
private const val CROUCH_POSE_T = 0.40f
private const val CROUCH_SPEED = 1.2f

// ---- Shield: solid energy bubble + glowing hex lines ----
private const val SHIELD_R = 1.1f
private const val SHIELD_CY = 0.9f
private const val SHIELD_EDGE_THICK = 0.034f  // thick glowing hex lines (was 0.016)
private const val SHIELD_FREQ = 3
private const val SHIELD_MAX_HITS = 25f
private const val SHIELD_REVIVE_SEC = 20f
private const val SHIELD_BREAK_TIME = 0.55f
private const val SHIELD_APPEAR_TIME = 0.45f
private const val M_SHINE = 5
private const val M_HOT = 6
private const val M_AMBER = 7
private const val SHIELD_HIT_TIME = 0.7f
private const val RIPPLE_RING = 16
private const val RIPPLE_COUNT = RIPPLE_RING + 1
private const val JUMP_LEN = 0.5f
private const val JUMP_TAKEOFF_T = 0.1406f
private const val JUMP_LAND_T = 0.3594f
private const val JUMP_SPEED = 8f
private const val GRAVITY = 24f

// ---- Capture the Flag drawing ----
// nodes per team: 0 pole | 1 finial | strips | pedestal | 4 frame bars | light beam
private const val FLAG_STRIPS = 24            // the banner is 24 thin strips = smooth waving cloth
private const val FLAG_STRIP_W = 0.03f
private const val FLAG_H = 0.55f              // cloth height
private const val FL_STRIP = 2
private const val FL_PED = FL_STRIP + FLAG_STRIPS
private const val FL_FRAME = FL_PED + 1
private const val FL_BEAM = FL_FRAME + 4
private const val FLAG_NODES_PER_TEAM = FL_BEAM + 1

// Gun turn (degrees, X/Y/Z)
internal class GunTurn(val x: Float, val y: Float, val z: Float)

internal class GunDef(
    val name: String,
    val file: String,
    val pos: Position,
    val scale: Float,
    val grip: Position,
    val carry: GunTurn,
    val aim: Map<String, GunTurn>,
    val hasScope: Boolean = false
)

internal val GUNS = listOf(
    GunDef(
        name = "MP5",
        file = "mp5_gun.glb",
        pos = Position(0.005f, 0.06f, 0.025f),
        scale = 0.28f,
        grip = Position(0f, 0.11f, 0.18f),
        carry = GunTurn(-99f, 16f, -45f),
        aim = mapOf(
            "shoot" to GunTurn(-148f, 16f, -45f),
            "walk_shoot" to GunTurn(-142f, 11f, -48f),
            "run_shoot" to GunTurn(-167f, 31f, -37f)
        )
    ),
    GunDef(
        name = "AK-47",
        file = "ak47_gun.glb",
        pos = Position(0.005f, 0.06f, 0.025f),
        scale = 0.2f,
        grip = Position(-0.003f, 0.132f, 0.574f),
        carry = GunTurn(-120f, 53f, -44.5f),
        aim = mapOf(
            "shoot" to GunTurn(-148f, 16f, -45f),
            "walk_shoot" to GunTurn(-142f, 11f, -48f),
            "run_shoot" to GunTurn(-167f, 31f, -37f)
        )
    ),
    GunDef(
        name = "AR",
        file = "ar_gun.glb",
        pos = Position(0.005f, 0.06f, 0.025f),
        scale = 0.24f,
        grip = Position(0.049f, 0.191f, 0.394f),
        carry = GunTurn(-130f, 51f, -37f),
        aim = mapOf(
            "shoot" to GunTurn(-148f, 16f, -45f),
            "walk_shoot" to GunTurn(-142f, 11f, -48f),
            "run_shoot" to GunTurn(-167f, 31f, -37f)
        )
    ),
    GunDef(
        name = "Rifle",
        file = "rifle.glb",
        pos = Position(0.005f, 0.06f, 0.025f),
        scale = 0.5f,
        grip = Position(0.3f, 0.015f, 0.394f),
        carry = GunTurn(-130f, 51f, -37f),
        aim = mapOf(
            "shoot" to GunTurn(-148f, 16f, -45f),
            "walk_shoot" to GunTurn(-142f, 11f, -48f),
            "run_shoot" to GunTurn(-167f, 31f, -37f)
        ),
        hasScope = true
    ),
    GunDef(
        name = "Carbine",
        file = "carbine.glb",
        pos = Position(0.005f, 0.06f, 0.025f),
        scale = 0.42f,
        grip = Position(-0.055f, 0.085f, 0.28f),
        carry = GunTurn(-130f, 51f, -37f),
        aim = mapOf(
            "shoot" to GunTurn(-148f, 16f, -45f),
            "walk_shoot" to GunTurn(-124f, 11f, -48f),
            "run_shoot" to GunTurn(-167f, 31f, -37f)
        )
    )
)

internal class ArenaDef(val name: String, val glb: String)
internal val ARENA_DEFS = listOf(
    ArenaDef("Zero Arena", "arena_play.glb")
)
internal fun arenaNames(): List<String> = ARENA_DEFS.map { it.name }

internal val CHAR_FILES = listOf("char_blue.glb", "char_red.glb", "char_aqua.glb")
internal val CHAR_NAMES = listOf("Blue", "Red", "Aqua")

// Plain values the game loop changes every frame (no Compose redraw needed)
internal class GameInput {
    var x = 0f
    var y = 0f
    var z = 0f
    var play: PlayArena? = null
    var match: MatchState? = null
    var camPitch = 0f
    var camYaw = 0f
    var faceDeg = 0f
    var joyX = 0f
    var joyY = 0f
    var shooting = false
    var aimHeld = false
    var aimX = 0f
    var aimY = 0f
    var paused = false
    var crouch = false
    var shieldOn by mutableStateOf(false)
    var shieldHp = SHIELD_MAX_HITS
    var shieldCd = 0f
    var shieldCdSec by mutableIntStateOf(0)
    var shieldBreakT = 0f
    var shieldAppearT = 0f
    var shieldWasOn = false
    var scopeOn = false
    var handMode = false
    var gunWanted by mutableIntStateOf(0)
    var zoom = 1f
    var active = 0
    var lastNanos = 0L
    var gunOwner = -1
    var gunLoaded = false
    var gunDef: GunDef = GUNS[0]
    var gunChild: Node? = null
    val attachFails = IntArray(3)
    var fireTimer = 0f
    var mx = 0f
    var my = 0f
    var mz = 0f
    var flashT = 0f
    val hand = FloatArray(3)
    val bAlive = BooleanArray(BULLET_COUNT)
    val bx = FloatArray(BULLET_COUNT)
    val bz = FloatArray(BULLET_COUNT)
    val by = FloatArray(BULLET_COUNT)
    val bdx = FloatArray(BULLET_COUNT)
    val bdz = FloatArray(BULLET_COUNT)
    val bDist = FloatArray(BULLET_COUNT)
    val fxLife = FloatArray(FX_COUNT)
    val fxX = FloatArray(FX_COUNT)
    val fxY = FloatArray(FX_COUNT)
    val fxZ = FloatArray(FX_COUNT)
    val fxVX = FloatArray(FX_COUNT)
    val fxVY = FloatArray(FX_COUNT)
    val fxVZ = FloatArray(FX_COUNT)
    val fxSplat = BooleanArray(FX_COUNT)
    var shieldHitT = 0f
    var shieldDx = 0f
    var shieldDy = 0f
    var shieldDz = 1f
    val combo = PunchCombo()
    var punchClip = ""
    var punchT = 0f
    var punchTap = false
    var jumpTap = false
    var jumpState = 0
    var jumpClock = 0f
    var airClock = 0f
    var jumpClipT = 0f
    var jumpH = 0f
    var jumpV = 0f
    val anim = Array(3) { AnimState() }
    var speed = 0f
    var moveX = 0f
    var moveZ = 0f
    var runMode = false

    // ---- Gun fit mode (HUD "FIT" panel) ----
    var fitOn = false
    var fitSel = 0
    var fitGunName = ""
    var fitGx = 0f
    var fitGy = 0f
    var fitGz = 0f
    var fitScale = 0.24f
    val fitRot = HashMap<String, FloatArray>()
    var fitTick by mutableIntStateOf(0)
    var fitPose by mutableStateOf("carry")

    fun fitKey(): String = when (fitSel) {
        1 -> "shoot"
        2 -> "walk_shoot"
        3 -> "run_shoot"
        else -> "carry"
    }
}

internal class AnimState {
    var cur = -1
    var prev = -1
    var curTime = 0f
    var prevTime = 0f
    var fade = 1f
    var wantName = ""
    var baseTime = 0f
    var prevClamp = false
}

internal class BotView(
    val model: ModelNode,
    val gun: Node,
    val marker: CubeNode,
    val bar: CubeNode,
    val gunIdx: Int
) {
    val anim = AnimState()
    var gunLoaded = false
    var gunAttached = false
    var fails = 0
}

// ---------------- Shield shape: Goldberg sphere (hexagon cells + 12 pentagons) ----------------
private class ShieldGeo(
    val count: Int,
    val mid: FloatArray,
    val dir: FloatArray,
    val quat: Array<Quaternion>,
    val lens: FloatArray,
    val band: IntArray
)

private val SHIELD_GEO: ShieldGeo by lazy { buildShieldGeo() }

private fun buildShieldGeo(): ShieldGeo {
    val phi = (1f + sqrt(5f)) / 2f
    val bv = arrayOf(
        floatArrayOf(-1f, phi, 0f), floatArrayOf(1f, phi, 0f), floatArrayOf(-1f, -phi, 0f), floatArrayOf(1f, -phi, 0f),
        floatArrayOf(0f, -1f, phi), floatArrayOf(0f, 1f, phi), floatArrayOf(0f, -1f, -phi), floatArrayOf(0f, 1f, -phi),
        floatArrayOf(phi, 0f, -1f), floatArrayOf(phi, 0f, 1f), floatArrayOf(-phi, 0f, -1f), floatArrayOf(-phi, 0f, 1f)
    )
    val bf = arrayOf(
        intArrayOf(0, 11, 5), intArrayOf(0, 5, 1), intArrayOf(0, 1, 7), intArrayOf(0, 7, 10), intArrayOf(0, 10, 11),
        intArrayOf(1, 5, 9), intArrayOf(5, 11, 4), intArrayOf(11, 10, 2), intArrayOf(10, 7, 6), intArrayOf(7, 1, 8),
        intArrayOf(3, 9, 4), intArrayOf(3, 4, 2), intArrayOf(3, 2, 6), intArrayOf(3, 6, 8), intArrayOf(3, 8, 9),
        intArrayOf(4, 9, 5), intArrayOf(2, 4, 11), intArrayOf(6, 2, 10), intArrayOf(8, 6, 7), intArrayOf(9, 8, 1)
    )
    val n = SHIELD_FREQ
    val verts = ArrayList<FloatArray>()
    val index = HashMap<String, Int>()
    fun vert(x: Float, y: Float, z: Float): Int {
        val l = sqrt(x * x + y * y + z * z)
        val nx = x / l; val ny = y / l; val nz = z / l
        val key = "${(nx * 10000).toInt()},${(ny * 10000).toInt()},${(nz * 10000).toInt()}"
        return index.getOrPut(key) { verts.add(floatArrayOf(nx, ny, nz)); verts.size - 1 }
    }
    val tris = ArrayList<IntArray>()
    for (f in bf) {
        val a = bv[f[0]]; val b = bv[f[1]]; val c = bv[f[2]]
        fun p(i: Int, j: Int): Int {
            val wa = (n - i - j).toFloat(); val wb = i.toFloat(); val wc = j.toFloat()
            return vert(
                (a[0] * wa + b[0] * wb + c[0] * wc) / n,
                (a[1] * wa + b[1] * wb + c[1] * wc) / n,
                (a[2] * wa + b[2] * wb + c[2] * wc) / n
            )
        }
        for (i in 0 until n) for (j in 0 until n - i) {
            tris.add(intArrayOf(p(i, j), p(i + 1, j), p(i, j + 1)))
            if (j < n - i - 1) tris.add(intArrayOf(p(i + 1, j), p(i + 1, j + 1), p(i, j + 1)))
        }
    }
    val cen = tris.map { t ->
        val a = verts[t[0]]; val b = verts[t[1]]; val c = verts[t[2]]
        val x = a[0] + b[0] + c[0]; val y = a[1] + b[1] + c[1]; val z = a[2] + b[2] + c[2]
        val l = sqrt(x * x + y * y + z * z)
        floatArrayOf(x / l, y / l, z / l)
    }
    val edgeFaces = HashMap<Long, IntArray>()
    for ((fi, t) in tris.withIndex()) for (e in 0..2) {
        val a = t[e]; val b = t[(e + 1) % 3]
        val key = min(a, b).toLong() * 100000L + max(a, b).toLong()
        val old = edgeFaces[key]
        if (old == null) edgeFaces[key] = intArrayOf(fi, -1) else old[1] = fi
    }
    val mids = ArrayList<Float>(); val dirs = ArrayList<Float>(); val lens = ArrayList<Float>()
    val quats = ArrayList<Quaternion>(); val bands = ArrayList<Int>()
    for (pair in edgeFaces.values) {
        if (pair[1] < 0) continue
        val a = cen[pair[0]]; val b = cen[pair[1]]
        val dx = b[0] - a[0]; val dy = b[1] - a[1]; val dz = b[2] - a[2]
        val d = sqrt(dx * dx + dy * dy + dz * dz)
        val mx = (a[0] + b[0]) / 2f; val my = (a[1] + b[1]) / 2f; val mz = (a[2] + b[2]) / 2f
        val ml = sqrt(mx * mx + my * my + mz * mz)
        mids.add(mx * SHIELD_R); mids.add(my * SHIELD_R); mids.add(mz * SHIELD_R)
        dirs.add(mx / ml); dirs.add(my / ml); dirs.add(mz / ml)
        lens.add(d * SHIELD_R * 1.1f)
        quats.add(quatFromZ(dx / d, dy / d, dz / d))
        bands.add((((my / ml) + 1f) / 2f * 5f).toInt().coerceIn(0, 4))
    }
    return ShieldGeo(quats.size, mids.toFloatArray(), dirs.toFloatArray(), quats.toTypedArray(), lens.toFloatArray(), bands.toIntArray())
}

// Colours + which colour each line has right now
private object ShieldFx {
    var mats: List<MaterialInstance> = emptyList()
    var cur = IntArray(0)
    var fillMats: List<MaterialInstance> = emptyList()   // 0 = blue body, 1 = hit flash, 2 = amber (weak)
    var fillCur = -1
}

private fun quatFromZ(dx: Float, dy: Float, dz: Float): Quaternion {
    if (dz > 0.9999f) return Quaternion(0f, 0f, 0f, 1f)
    if (dz < -0.9999f) return Quaternion(1f, 0f, 0f, 0f)
    val w = 1f + dz
    val x = -dy
    val y = dx
    val n = sqrt(x * x + y * y + w * w)
    return Quaternion(x / n, y / n, 0f, w / n)
}

internal fun characterNames(): List<String> = CHAR_NAMES
internal fun gunNames(): List<String> = GUNS.map { it.name }

@Composable
internal fun Game3DView(
    input: GameInput,
    modifier: Modifier = Modifier,
    charIndex: Int = 0,
    arenaIndex: Int = 0
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val cameraNode = rememberCameraNode(engine)
    val context = LocalContext.current
    val arenaDef = ARENA_DEFS[arenaIndex.coerceIn(0, ARENA_DEFS.lastIndex)]
    val ci = charIndex.coerceIn(0, CHAR_FILES.lastIndex)
    val rig = input

    var ready by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Loading arena...") }
    var note by remember { mutableStateOf("") }

    val environment = remember(engine) {
        Environment(
            indirectLight = IndirectLight.Builder()
                .irradiance(1, floatArrayOf(1f, 1f, 1f))
                .intensity(30_000f)
                .build(engine),
            skybox = Skybox.Builder().color(0.61f, 0.74f, 0.79f, 1f).build(engine)
        )
    }

    val models = remember { arrayOfNulls<ModelNode>(CHAR_FILES.size) }
    val gun = remember { Node(engine).apply { isVisible = false } }
    var gunModel by remember { mutableStateOf<ModelNode?>(null) }

    val bullets = remember {
        val bulletMat = materialLoader.createColorInstance(Color(0xFFFFB02E))
        List(BULLET_COUNT) {
            CubeNode(engine, size = Float3(BULLET_THICK, BULLET_THICK, BULLET_LEN), materialInstance = bulletMat)
                .apply { isVisible = false }
        }
    }

    val botBullets = remember {
        val mat = materialLoader.createColorInstance(Color(0xFFFF4A2C))
        List(BOT_BULLET_COUNT) {
            CubeNode(engine, size = Float3(BULLET_THICK, BULLET_THICK, BULLET_LEN), materialInstance = mat)
                .apply { isVisible = false }
        }
    }

    val muzzleFlash = remember {
        val mat = materialLoader.createColorInstance(Color(0xFFFFD35A))
        CubeNode(engine, size = Float3(0.16f, 0.16f, 0.16f), materialInstance = mat).apply { isVisible = false }
    }

    val fxNodes = remember {
        val spark = materialLoader.createColorInstance(Color(0xFFFFC040))
        val bloodDark = materialLoader.createColorInstance(Color(0xFF7A0C12))
        val bloodBright = materialLoader.createColorInstance(Color(0xFFB8161E))
        List(FX_COUNT) { i ->
            if (i < FX_HALF) CubeNode(engine, size = Float3(0.045f, 0.045f, 0.045f), materialInstance = spark)
            else CubeNode(engine, size = Float3(0.035f, 0.035f, 0.035f), materialInstance = if (i % 3 == 0) bloodBright else bloodDark)
        }.also { l -> l.forEach { it.isVisible = false } }
    }

    // Capture the Flag. Team 0 (your flag) is orange, team 1 (enemy) is cyan. Hidden unless the mode is Capture the Flag.
    val flagNodes = remember {
        val steel = materialLoader.createColorInstance(Color(0xFF3A3F45))
        val gunmetal = materialLoader.createColorInstance(Color(0xFF23272B))
        val orange = materialLoader.createColorInstance(Color(0xFFFF8A00))
        val orangeShade = materialLoader.createColorInstance(Color(0xFFD96F00))
        val cyan = materialLoader.createColorInstance(Color(0xFF35D3FF))
        val cyanShade = materialLoader.createColorInstance(Color(0xFF1FA6D1))
        val list = ArrayList<CubeNode>()
        for (team in 0..1) {
            val main = if (team == 0) orange else cyan
            val shade = if (team == 0) orangeShade else cyanShade
            list.add(CubeNode(engine, size = Float3(0.05f, 2.3f, 0.05f), materialInstance = steel))          // pole
            list.add(CubeNode(engine, size = Float3(0.1f, 0.1f, 0.1f), materialInstance = main))            // finial
            for (s in 0 until FLAG_STRIPS) {                                                                 // cloth: thin strips, soft fold shading
                val m = if ((s / 4) % 2 == 0) main else shade
                list.add(CubeNode(engine, size = Float3(FLAG_STRIP_W * 1.7f, FLAG_H, 0.012f), materialInstance = m))
            }
            list.add(CubeNode(engine, size = Float3(2.1f, 0.12f, 2.1f), materialInstance = gunmetal))        // pedestal
            list.add(CubeNode(engine, size = Float3(3.0f, 0.04f, 0.06f), materialInstance = main))
            list.add(CubeNode(engine, size = Float3(3.0f, 0.04f, 0.06f), materialInstance = main))
            list.add(CubeNode(engine, size = Float3(0.06f, 0.04f, 3.0f), materialInstance = main))
            list.add(CubeNode(engine, size = Float3(0.06f, 0.04f, 3.0f), materialInstance = main))
            list.add(CubeNode(engine, size = Float3(0.03f, 12f, 0.03f), materialInstance = main))            // beam
        }
        list.forEach { it.isVisible = false }
        list.toList()
    }

    val botViews = remember { mutableListOf<BotView>() }

    // Shield hex lines (thick, glowing)
    val shieldNodes = remember {
        val geo = SHIELD_GEO
        // 0..4 = blue -> aqua gradient (bottom to top), 5 = shine, 6 = hit glow, 7 = amber (shield is weak)
        val colours = listOf(0xFF2A7BFFL, 0xFF2FB0FFL, 0xFF3FD2FFL, 0xFF7FE8FFL, 0xFFB8F6FFL, 0xFFE8FDFFL, 0xFFFFFFFFL, 0xFFFFB347L)
        val mats = colours.map { materialLoader.createColorInstance(Color(it)) }
        ShieldFx.mats = mats
        ShieldFx.cur = IntArray(geo.count) { geo.band[it] }
        List(geo.count) { i ->
            CubeNode(engine, size = Float3(SHIELD_EDGE_THICK, SHIELD_EDGE_THICK, 1f), materialInstance = mats[geo.band[i]])
                .apply {
                    quaternion = geo.quat[i]
                    isVisible = false
                }
        }
    }

    // Shield body: 0 = translucent blue ball, 1 = soft outer glow ball, 2 = glowing floor disc
    // If the ball looks solid (not see-through) in your SceneView version, lower the alpha values below.
    val shieldFill = remember {
        val body = materialLoader.createColorInstance(Color(0xFF1E78FF).copy(alpha = 0.30f))
        val flash = materialLoader.createColorInstance(Color(0xFF9FF2FF).copy(alpha = 0.55f))
        val amber = materialLoader.createColorInstance(Color(0xFFFFA030).copy(alpha = 0.32f))
        val glow = materialLoader.createColorInstance(Color(0xFF4FD8FF).copy(alpha = 0.14f))
        val disc = materialLoader.createColorInstance(Color(0xFF35C8FF).copy(alpha = 0.40f))
        ShieldFx.fillMats = listOf(body, flash, amber)
        ShieldFx.fillCur = 0
        listOf<Node>(
            SphereNode(engine, radius = 1f, materialInstance = body).apply { isVisible = false },
            SphereNode(engine, radius = 1f, materialInstance = glow).apply { isVisible = false },
            CylinderNode(engine, radius = SHIELD_R, height = 0.02f, materialInstance = disc).apply { isVisible = false }
        )
    }

    val rippleNodes = remember {
        val mat = materialLoader.createColorInstance(Color(0xFFEFFFFF))
        List(RIPPLE_COUNT) {
            CubeNode(engine, size = Float3(0.07f, 0.07f, 0.07f), materialInstance = mat).apply { isVisible = false }
        }
    }

    val nodes = rememberNodes {
        add(gun)
        addAll(bullets)
        addAll(botBullets)
        addAll(shieldFill)
        addAll(shieldNodes)
        addAll(rippleNodes)
        add(muzzleFlash)
        addAll(fxNodes)
        addAll(flagNodes)
    }

    // 1) arena, 2) character
    LaunchedEffect(Unit) {
        try {
            withFrameNanos { }
            val p = PlayArena.load(context)
            rig.play = p
            rig.x = p.blueX
            rig.z = p.blueZ
            rig.camYaw = if (p.blueX < 0f) 90f else -90f
            status = "Loading arena model..."
            withFrameNanos { }
            nodes.add(
                ModelNode(
                    modelInstance = modelLoader.createModelInstance(arenaDef.glb),
                    autoAnimate = false
                )
            )
            status = "Loading character..."
            withFrameNanos { }
            val m = ModelNode(
                modelInstance = modelLoader.createModelInstance(CHAR_FILES[ci]),
                autoAnimate = false,
                scaleToUnits = CHAR_HEIGHT,
                centerOrigin = Position(0f, -1f, 0f)
            )
            m.isVisible = false
            models[ci] = m
            rig.active = ci
            nodes.add(m)
            ready = true
        } catch (e: Throwable) {
            Log.e("Game3D", "Could not build the scene", e)
            status = "Could not load: ${e.message}"
        }
    }

    // 3) the gun in the hand
    LaunchedEffect(ready, rig.gunWanted) {
        if (!ready) return@LaunchedEffect
        val i = rig.gunWanted.coerceIn(0, GUNS.lastIndex)
        try {
            val def = GUNS[i]
            withFrameNanos { }
            rig.gunLoaded = false
            gunModel?.let { gun.removeChildNode(it); it.destroy() }
            gunModel = null
            rig.gunChild = null
            val gm = ModelNode(
                modelInstance = modelLoader.createModelInstance(def.file),
                autoAnimate = false
            )
            gm.position = def.grip
            gun.addChildNode(gm)
            gunModel = gm
            rig.gunChild = gm
            rig.gunDef = def
            rig.gunLoaded = true
            note = ""
        } catch (e: Throwable) {
            Log.e("Game3D", "Could not load gun", e)
            note = "Gun file missing (" + GUNS[i].file + ")"
        }
    }

    // 4) the bots
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        val mt = rig.match ?: return@LaunchedEffect
        if (botViews.isNotEmpty()) return@LaunchedEffect
        try {
            val enemyMat = materialLoader.createColorInstance(Color(0xFFFF3B3B))
            val allyMat = materialLoader.createColorInstance(Color(0xFF35E07A))
            val hpMat = materialLoader.createColorInstance(Color(0xFFF4F6FA))
            for (b in mt.bots) {
                val gi = (0..GUNS.lastIndex).random()
                val gdef = GUNS[gi]
                withFrameNanos { }
                val m = ModelNode(
                    modelInstance = modelLoader.createModelInstance(CHAR_FILES[0]),
                    autoAnimate = false,
                    scaleToUnits = CHAR_HEIGHT,
                    centerOrigin = Position(0f, -1f, 0f)
                )
                m.isVisible = false
                nodes.add(m)

                val g = Node(engine).apply { isVisible = false }
                nodes.add(g)
                var gunOk = false
                try {
                    withFrameNanos { }
                    val gm = ModelNode(
                        modelInstance = modelLoader.createModelInstance(gdef.file),
                        autoAnimate = false
                    )
                    gm.position = gdef.grip
                    g.addChildNode(gm)
                    gunOk = true
                } catch (e: Throwable) {
                    Log.e("Game3D", "Could not load bot gun", e)
                }

                val friendly = mt.teamMode && b.team == 0
                val marker = CubeNode(
                    engine, size = Float3(0.22f, 0.22f, 0.22f),
                    materialInstance = if (friendly) allyMat else enemyMat
                ).apply { isVisible = false }
                val bar = CubeNode(engine, size = Float3(0.7f, 0.07f, 0.07f), materialInstance = hpMat)
                    .apply { isVisible = false }
                nodes.add(marker)
                nodes.add(bar)

                botViews.add(BotView(m, g, marker, bar, gi).also { it.gunLoaded = gunOk })
            }
        } catch (e: Throwable) {
            Log.e("Game3D", "Could not build the bots", e)
            note = "Could not load bots: ${e.message}"
        }
    }

    Box(modifier.fillMaxSize()) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            cameraNode = cameraNode,
            childNodes = nodes,
            environment = environment,
            onFrame = { nanos ->
                stepGame(nanos, rig, models, cameraNode, engine, gun, bullets, shieldNodes, shieldFill, rippleNodes, botViews, botBullets, muzzleFlash, fxNodes, flagNodes)
            }
        )

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var lastSpread = 0f
                        var lastAngle = Float.NaN
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                val spread = (pressed[0].position - pressed[1].position).getDistance()
                                if (lastSpread > 0f && spread > 0f) {
                                    rig.zoom = (rig.zoom * lastSpread / spread).coerceIn(ZOOM_MIN, ZOOM_MAX)
                                }
                                lastSpread = spread
                                val v = pressed[1].position - pressed[0].position
                                val ang = atan2(v.y, v.x)
                                if (!lastAngle.isNaN()) {
                                    var da = ang - lastAngle
                                    while (da > PI.toFloat()) da -= 2f * PI.toFloat()
                                    while (da < -PI.toFloat()) da += 2f * PI.toFloat()
                                    rig.camYaw += da * RAD_TO_DEG
                                }
                                lastAngle = ang
                            } else {
                                lastSpread = 0f
                                lastAngle = Float.NaN
                                val c = pressed[0]
                                val sens = if (rig.scopeOn) SCOPE_DRAG else 1f
                                rig.camYaw += (c.position.x - c.previousPosition.x) * CAM_DRAG_SPEED * sens
                                rig.camPitch = (rig.camPitch + (c.position.y - c.previousPosition.y) * CAM_PITCH_SPEED * sens)
                                    .coerceIn(0f, CAM_PITCH_MAX)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        )

        if (!ready) {
            Text(status, color = Color.White, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center))
        } else if (note.isNotEmpty()) {
            Text(note, color = Color(0xFFFFB070), fontSize = 12.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
        }
    }
}

private fun fitWantOf(sel: Int): String = when (sel) {
    1 -> "shoot"
    2 -> "walk_shoot"
    3 -> "run_shoot"
    else -> "idle"
}

private fun syncFit(rig: GameInput, def: GunDef) {
    if (rig.fitGunName == def.name) return
    rig.fitGunName = def.name
    rig.fitGx = def.grip.x
    rig.fitGy = def.grip.y
    rig.fitGz = def.grip.z
    rig.fitScale = def.scale
    rig.fitRot["carry"] = floatArrayOf(def.carry.x, def.carry.y, def.carry.z)
    for (key in listOf("shoot", "walk_shoot", "run_shoot")) {
        val t = def.aim[key] ?: def.carry
        rig.fitRot[key] = floatArrayOf(t.x, t.y, t.z)
    }
    rig.fitTick++
}

private fun stepGame(
    nanos: Long,
    rig: GameInput,
    models: Array<ModelNode?>,
    cameraNode: io.github.sceneview.node.CameraNode,
    engine: Engine,
    gun: Node,
    bullets: List<CubeNode>,
    shieldNodes: List<CubeNode>,
    shieldFill: List<Node>,
    rippleNodes: List<CubeNode>,
    botViews: List<BotView>,
    botBullets: List<CubeNode>,
    muzzleFlash: CubeNode,
    fxNodes: List<CubeNode>,
    flagNodes: List<CubeNode>
) {
    if (rig.paused) { rig.lastNanos = nanos; return }
    if (rig.handMode) { rig.shooting = false; rig.aimHeld = false; rig.scopeOn = false }

    val dt = if (rig.lastNanos == 0L) 0f else ((nanos - rig.lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
    rig.lastNanos = nanos

    val mt = rig.match
    val dead = mt?.playerDead == true
    if (dead) {
        rig.shooting = false
        rig.aimHeld = false
        rig.aimX = 0f
        rig.aimY = 0f
        rig.joyX = 0f
        rig.joyY = 0f
        rig.punchTap = false
        rig.jumpTap = false
        rig.punchClip = ""
        rig.scopeOn = false
    }

    // ---- Jump ----
    if (rig.jumpTap) {
        rig.jumpTap = false
        if (rig.jumpState == 0 && rig.punchClip != "punch_3") {
            rig.jumpState = 1
            rig.jumpClock = 0f
            rig.punchClip = ""
        }
    }
    when (rig.jumpState) {
        1 -> {
            rig.jumpClock += dt
            rig.jumpClipT = rig.jumpClock
            if (rig.jumpClock >= JUMP_TAKEOFF_T) {
                rig.jumpState = 2
                rig.jumpV = JUMP_SPEED
                rig.airClock = 0f
            }
        }
        2 -> {
            rig.jumpV -= GRAVITY * dt
            rig.jumpH += rig.jumpV * dt
            rig.airClock += dt
            val u = min(1f, rig.airClock / (2f * JUMP_SPEED / GRAVITY))
            rig.jumpClipT = JUMP_TAKEOFF_T + (JUMP_LAND_T - JUMP_TAKEOFF_T) * u
            if (rig.jumpH <= 0f && rig.jumpV < 0f) {
                rig.jumpH = 0f
                rig.jumpState = 3
                rig.jumpClipT = maxOf(rig.jumpClipT, JUMP_LAND_T - 0.03f)
            }
        }
        3 -> {
            rig.jumpClipT += dt
            if (rig.jumpClipT >= JUMP_LEN) rig.jumpState = 0
        }
    }

    // ---- Punch combo ----
    if (rig.punchTap) {
        rig.punchTap = false
        if (rig.jumpState == 0 && (rig.punchClip.isEmpty() || rig.punchT >= PUNCH_CHAIN_AT)) {
            rig.punchClip = rig.combo.nextClip()
            rig.punchT = 0f
        }
    }
    if (rig.punchClip.isNotEmpty()) {
        rig.punchT += dt
        if (rig.punchT >= PUNCH_LEN) rig.punchClip = ""
    }

    val yawR = rig.camYaw * DEG_TO_RAD
    val fx = sin(yawR)
    val fz = -cos(yawR)
    val rx = cos(yawR)
    val rz = sin(yawR)

    val mag = min(1f, hypot(rig.joyX, rig.joyY))
    val moving = mag > 0.1f
    if (rig.runMode) { if (mag < 0.6f) rig.runMode = false } else { if (mag > 0.75f) rig.runMode = true }
    var dx = 0f
    var dz = 0f
    if (moving) {
        dx = fx * (-rig.joyY) + rx * rig.joyX
        dz = fz * (-rig.joyY) + rz * rig.joyX
        val l = hypot(dx, dz)
        if (l > 0f) { dx /= l; dz /= l }
        rig.moveX = dx
        rig.moveZ = dz
    }

    val targetSpeed = if (!moving || rig.punchClip == "punch_3") 0f else if (rig.crouch || rig.scopeOn) CROUCH_SPEED else if (rig.runMode) RUN_SPEED else WALK_SPEED
    val maxChange = ACCEL * dt
    rig.speed += (targetSpeed - rig.speed).coerceIn(-maxChange, maxChange)

    val play = rig.play
    if (play != null) {
        val feet = rig.y + rig.jumpH
        val nx = rig.x + rig.moveX * rig.speed * dt
        val nz = rig.z + rig.moveZ * rig.speed * dt
        play.tryMove(rig.x, rig.z, nx, nz, feet)
        rig.x = play.outX
        rig.z = play.outZ
        val oldY = rig.y
        rig.y += (play.groundY(rig.x, rig.z, feet) - rig.y) * min(1f, dt * 20f)
        if (rig.jumpState == 2) rig.jumpH -= (rig.y - oldY)
    }

    val aimMag = min(1f, hypot(rig.aimX, rig.aimY))
    val aiming = rig.aimHeld && aimMag > AIM_DEADZONE
    var tx = dx
    var tz = dz
    var wantTurn = moving
    if (aiming) {
        tx = fx * (-rig.aimY) + rx * rig.aimX
        tz = fz * (-rig.aimY) + rz * rig.aimX
        val l = hypot(tx, tz)
        if (l > 0f) { tx /= l; tz /= l }
        wantTurn = true
    }

    if (wantTurn) {
        val target = atan2(-tz, tx) * RAD_TO_DEG + FACING_OFFSET
        var diff = target - rig.faceDeg
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        val maxTurn = (if (aiming) AIM_TURN_SPEED else TURN_SPEED) * dt
        rig.faceDeg += diff.coerceIn(-maxTurn, maxTurn)
    }

    val faceR = (rig.faceDeg - FACING_OFFSET) * DEG_TO_RAD
    val sx = cos(faceR)
    val sz = -sin(faceR)

    val aimDirX = if (aiming) tx else sx
    val aimDirZ = if (aiming) tz else sz

    val base = if (rig.speed < 0.15f) "idle" else if (rig.speed > (WALK_SPEED + RUN_SPEED) / 2f) "run" else "walk"
    val shootWant = if (rig.shooting) (if (base == "idle") "shoot" else base + "_shoot") else base
    val want = when {
        rig.fitOn -> fitWantOf(rig.fitSel)
        rig.jumpState != 0 -> JUMP_CLIP
        rig.punchClip.isNotEmpty() -> rig.punchClip
        rig.crouch -> JUMP_CLIP
        else -> shootWant
    }
    val forceTime = if (rig.fitOn) -1f else if (rig.jumpState != 0) rig.jumpClipT else if (rig.punchClip.isNotEmpty()) rig.punchT else if (rig.crouch) CROUCH_POSE_T else -1f
    val layerBase = if (want == "punch_1" || want == "punch_2") base else ""

    val moveDot = sx * rig.moveX + sz * rig.moveZ
    val timeScale = if (rig.speed > 0.15f && moveDot < BACKPEDAL_DOT) -1f else 1f

    for (i in models.indices) {
        val m = models[i] ?: continue
        val on = i == rig.active
        m.isVisible = on && !dead && !rig.scopeOn
        if (on) {
            m.position = Position(rig.x, rig.y + rig.jumpH, rig.z)
            m.rotation = Rotation(y = rig.faceDeg)
            driveAnimation(m, rig.anim[i], want, dt, timeScale, forceTime, layerBase)
        }
    }

    val activeModel = models[rig.active]
    if (rig.gunLoaded && activeModel != null &&
        rig.gunOwner != rig.active && rig.attachFails[rig.active] < 10
    ) {
        if (attachGun(engine, gun, activeModel)) rig.gunOwner = rig.active
        else rig.attachFails[rig.active]++
    }
    gun.isVisible = rig.gunLoaded && activeModel != null && rig.gunOwner == rig.active && (rig.punchClip.isEmpty() || rig.fitOn) && !rig.handMode && !dead && !rig.scopeOn
    val def = rig.gunDef
    syncFit(rig, def)
    if (rig.fitOn) {
        val key = rig.fitKey()
        if (rig.fitPose != key) rig.fitPose = key
        val r = rig.fitRot[key] ?: floatArrayOf(0f, 0f, 0f)
        gun.position = def.pos
        gun.quaternion = quatXYZ(r[0], r[1], r[2])
        gun.scale = Float3(rig.fitScale, rig.fitScale, rig.fitScale)
        rig.gunChild?.position = Position(rig.fitGx, rig.fitGy, rig.fitGz)
    } else {
        val turn = def.aim[want] ?: def.carry
        gun.position = def.pos
        gun.quaternion = quatXYZ(turn.x, turn.y, turn.z)
        gun.scale = Float3(def.scale, def.scale, def.scale)
        rig.gunChild?.position = def.grip
    }

    if (rig.shooting) {
        if (activeModel != null && rig.gunLoaded && handWorld(engine, activeModel, rig.hand)) {
            rig.mx = rig.hand[0] + sx * MUZZLE_FORWARD
            rig.my = rig.hand[1]
            rig.mz = rig.hand[2] + sz * MUZZLE_FORWARD
        } else {
            rig.mx = rig.x + sx * BULLET_START_FORWARD - sz * BULLET_START_SIDE
            rig.my = rig.y + rig.jumpH + BULLET_HEIGHT
            rig.mz = rig.z + sz * BULLET_START_FORWARD + sx * BULLET_START_SIDE
        }
    }

    var bdirX = aimDirX
    var bdirZ = aimDirZ
    if (rig.shooting) {
        val toX = rig.x + aimDirX * AIM_POINT_DIST - rig.mx
        val toZ = rig.z + aimDirZ * AIM_POINT_DIST - rig.mz
        val len = hypot(toX, toZ)
        if (len > 0.5f) { bdirX = toX / len; bdirZ = toZ / len }
    }
    updateBullets(dt, rig, bullets, bdirX, bdirZ)

    if (rig.flashT > 0f && !dead) {
        rig.flashT -= dt
        val s = 0.8f + Random.nextFloat() * 0.8f
        muzzleFlash.position = Position(rig.mx + bdirX * 0.15f, rig.my, rig.mz + bdirZ * 0.15f)
        muzzleFlash.rotation = Rotation(Random.nextFloat() * 90f, Random.nextFloat() * 90f, Random.nextFloat() * 90f)
        muzzleFlash.scale = Float3(s, s, s)
        muzzleFlash.isVisible = true
    } else if (muzzleFlash.isVisible) {
        muzzleFlash.isVisible = false
    }

    if (mt != null) {
        mt.update(dt, rig)
        stepBots(dt, rig, mt, botViews, botBullets, engine)
        for (h in mt.hitFx) {
            if (h[3] <= 0.5f && rig.shieldOn) {
                val ddx = h[0] - rig.x; val ddy = h[1] - (rig.y + rig.jumpH + SHIELD_CY); val ddz = h[2] - rig.z
                if (ddx * ddx + ddy * ddy + ddz * ddz < (SHIELD_R + 0.4f) * (SHIELD_R + 0.4f)) continue
            }
            spawnBurst(rig, h[0], h[1], h[2], h[3].toInt(), if (h[3] > 0.5f) BLOOD_PIECES else SPARK_PIECES)
        }
        mt.hitFx.clear()
        if (mt.over) rig.paused = true
    }
    updateShield(rig, shieldNodes, shieldFill, rippleNodes, nanos, dt)
    updateFx(dt, rig, fxNodes)
    updateFlags(mt, flagNodes, nanos)

    val dist = if (rig.scopeOn) SCOPE_DIST else CAM_DIST * rig.zoom
    val pr = rig.camPitch * DEG_TO_RAD
    val back = dist * cos(pr)
    val camY = rig.y + rig.jumpH * 0.4f
    cameraNode.position = Position(rig.x - fx * back, camY + CAM_HEIGHT + dist * sin(pr), rig.z - fz * back)
    cameraNode.lookAt(Position(rig.x + fx * CAM_LOOK_AHEAD, camY + CAM_LOOK_Y, rig.z + fz * CAM_LOOK_AHEAD))
}

// ---------------- Capture the Flag: draw the two flags ----------------

private fun updateFlags(mt: MatchState?, nodes: List<CubeNode>, nanos: Long) {
    if (mt == null || !mt.ctf) {
        for (n in nodes) if (n.isVisible) n.isVisible = false
        return
    }
    val t = nanos / 1_000_000_000f
    val clothW = FLAG_STRIPS * FLAG_STRIP_W
    for (team in 0..1) {
        val o = team * FLAG_NODES_PER_TEAM
        val x = mt.flagX[team]
        val y = mt.flagY[team]
        val z = mt.flagZ[team]
        val hx = mt.homeX[team]
        val hy = mt.homeY[team]
        val hz = mt.homeZ[team]
        val s = if (mt.flagState[team] == F_CARRIED) 0.65f else 1f

        nodes[o].position = Position(x, y + 1.15f * s, z)
        nodes[o].scale = Float3(s, s, s)
        nodes[o + 1].position = Position(x, y + 2.35f * s, z)
        nodes[o + 1].scale = Float3(s, s, s)

        // cloth: a travelling wave from the pole to the free edge. Each strip leans along the slope of the wave,
        // and the strips overlap, so it looks like one continuous piece of cloth.
        val waveLen = 4.2f
        for (i in 0 until FLAG_STRIPS) {
            val node = nodes[o + FL_STRIP + i]
            val u = i / (FLAG_STRIPS - 1f)
            val phase = t * 3.6f - u * waveLen + team * 1.7f
            val amp = (0.012f + 0.14f * u) * s
            val slope = amp * cos(phase) * (-waveLen) / (clothW * s)       // dz / dx of the wave
            val yaw = -atan(slope) * RAD_TO_DEG
            node.position = Position(
                x + (0.04f + i * FLAG_STRIP_W + FLAG_STRIP_W / 2f) * s,
                y + (2.2f - FLAG_H / 2f + sin(phase * 1.2f) * 0.015f * u) * s,
                z + sin(phase) * amp
            )
            node.rotation = Rotation(y = yaw)
            node.scale = Float3(s, s, s)
        }

        nodes[o + FL_PED].position = Position(hx, hy + 0.06f, hz)
        nodes[o + FL_FRAME].position = Position(hx, hy + 0.14f, hz + 1.5f)
        nodes[o + FL_FRAME + 1].position = Position(hx, hy + 0.14f, hz - 1.5f)
        nodes[o + FL_FRAME + 2].position = Position(hx + 1.5f, hy + 0.14f, hz)
        nodes[o + FL_FRAME + 3].position = Position(hx - 1.5f, hy + 0.14f, hz)

        nodes[o + FL_BEAM].position = Position(x, y + 6f, z)

        if (!nodes[o].isVisible) {
            for (k in 0 until FLAG_NODES_PER_TEAM) nodes[o + k].isVisible = true
        }
    }
}

// ---------------- Bots ----------------

private fun stepBots(
    dt: Float,
    rig: GameInput,
    mt: MatchState,
    views: List<BotView>,
    botBullets: List<CubeNode>,
    engine: Engine
) {
    val barYaw = -rig.camYaw
    for (i in views.indices) {
        if (i >= mt.bots.size) break
        val b = mt.bots[i]
        val v = views[i]
        val gdef = GUNS[v.gunIdx.coerceIn(0, GUNS.lastIndex)]
        val vis = b.alive
        v.model.isVisible = vis
        v.marker.isVisible = vis
        v.bar.isVisible = vis
        if (!vis) { v.gun.isVisible = false; continue }

        v.model.position = Position(b.x, b.y, b.z)
        v.model.rotation = Rotation(y = b.faceDeg)
        driveAnimation(v.model, v.anim, b.anim, dt, 1f, -1f, "")

        if (v.gunLoaded && !v.gunAttached && v.fails < 10) {
            if (attachGun(engine, v.gun, v.model)) v.gunAttached = true else v.fails++
        }
        v.gun.isVisible = v.gunLoaded && v.gunAttached
        val turn = gdef.aim[b.anim] ?: gdef.carry
        v.gun.position = gdef.pos
        v.gun.quaternion = quatXYZ(turn.x, turn.y, turn.z)
        v.gun.scale = Float3(gdef.scale, gdef.scale, gdef.scale)

        v.marker.position = Position(b.x, b.y + 2.35f, b.z)
        v.bar.position = Position(b.x, b.y + 2.1f, b.z)
        v.bar.rotation = Rotation(y = barYaw)
        v.bar.scale = Float3(max(0.02f, b.hp.toFloat() / BOT_MAX_HP), 1f, 1f)
    }

    for (i in 0 until BOT_BULLET_COUNT) {
        val n = botBullets[i]
        if (!mt.bAlive[i]) { n.isVisible = false; continue }
        n.position = Position(mt.bx[i], mt.by[i], mt.bz[i])
        n.rotation = Rotation(y = atan2(mt.bdx[i], mt.bdz[i]) * RAD_TO_DEG)
        n.isVisible = true
    }
}

// ---------------- Animation with blending ----------------

private fun animIndex(animator: Animator, name: String): Int {
    for (i in 0 until animator.animationCount) {
        if (animator.getAnimationName(i) == name) return i
    }
    return -1
}

private fun isOneShot(name: String) = name.startsWith("punch") || name.startsWith("jump")

private fun driveAnimation(
    m: ModelNode, st: AnimState, want: String, dt: Float, timeScale: Float,
    forceTime: Float = -1f, layerBase: String = ""
) {
    val animator = m.modelInstance.animator
    if (animator.animationCount == 0) return

    if (st.wantName != want) {
        val idx = animIndex(animator, want)
        if (idx >= 0) {
            if (st.cur >= 0 && st.cur != idx) {
                st.prev = st.cur
                st.prevClamp = isOneShot(st.wantName)
                st.prevTime = st.curTime
                val pd = animator.getAnimationDuration(st.prev)
                val nd = animator.getAnimationDuration(idx)
                st.curTime = if (pd > 0f) st.prevTime / pd * nd else 0f
                st.fade = 0f
            }
            st.cur = idx
        } else {
            Log.e("Game3D", "No animation called '$want' in this model")
        }
        st.wantName = want
    }
    if (st.cur < 0) return

    val step = dt * timeScale
    val d = animator.getAnimationDuration(st.cur)
    if (forceTime >= 0f) st.curTime = min(forceTime, d)
    else if (d > 0f) st.curTime = ((st.curTime + step) % d + d) % d

    var baseIdx = -1
    if (layerBase.isNotEmpty()) {
        baseIdx = animIndex(animator, layerBase)
        val bd = if (baseIdx >= 0) animator.getAnimationDuration(baseIdx) else 0f
        if (bd > 0f) st.baseTime = ((st.baseTime + step) % bd + bd) % bd
    }

    val blending = st.fade < 1f && st.prev >= 0
    if (blending) {
        val pd = animator.getAnimationDuration(st.prev)
        if (pd > 0f) {
            st.prevTime = if (st.prevClamp) min(pd, st.prevTime + step) else ((st.prevTime + step) % pd + pd) % pd
        }
        st.fade = min(1f, st.fade + dt / BLEND_TIME)
    }

    if (baseIdx >= 0) animator.applyAnimation(baseIdx, st.baseTime)
    animator.applyAnimation(st.cur, st.curTime)
    if (blending) {
        animator.applyCrossFade(st.prev, st.prevTime, if (BLEND_FLIP) 1f - st.fade else st.fade)
    }
    animator.updateBoneMatrices()
}

// ---------------- Shield ----------------

// Solid blue energy bubble (translucent body + outer glow + floor disc) with thick glowing hex lines on top.
// Hit: whole body flashes, ring ripples over the lines. After SHIELD_MAX_HITS it shatters, then grows back.
private fun updateShield(rig: GameInput, nodes: List<CubeNode>, fill: List<Node>, ripple: List<CubeNode>, nanos: Long, dt: Float) {
    for (n in ripple) if (n.isVisible) n.isVisible = false
    val dead = rig.match?.playerDead == true

    if (rig.shieldCd > 0f) {
        rig.shieldCd = max(0f, rig.shieldCd - dt)
        if (rig.shieldCd <= 0f) {
            rig.shieldHp = SHIELD_MAX_HITS
            rig.shieldCdSec = 0
            rig.shieldOn = true
            rig.shieldAppearT = SHIELD_APPEAR_TIME
        } else {
            rig.shieldOn = false
            val sec = ceil(rig.shieldCd).toInt()
            if (sec != rig.shieldCdSec) rig.shieldCdSec = sec
        }
    }
    if (rig.shieldBreakT > 0f) rig.shieldBreakT = max(0f, rig.shieldBreakT - dt)

    if (rig.shieldOn && rig.shieldHitT >= SHIELD_HIT_TIME - 0.001f) {
        rig.shieldHp -= 1f
        if (rig.shieldHp <= 0f) {
            rig.shieldOn = false
            rig.shieldCd = SHIELD_REVIVE_SEC
            rig.shieldCdSec = SHIELD_REVIVE_SEC.toInt()
            rig.shieldBreakT = SHIELD_BREAK_TIME
        }
    }

    val breaking = rig.shieldBreakT > 0f
    if (dead || (!rig.shieldOn && !breaking)) {
        for (n in nodes) if (n.isVisible) n.isVisible = false
        for (n in fill) if (n.isVisible) n.isVisible = false
        rig.shieldHitT = 0f
        rig.shieldWasOn = false
        return
    }
    if (rig.shieldOn && !rig.shieldWasOn && rig.shieldAppearT <= 0f) rig.shieldAppearT = SHIELD_APPEAR_TIME
    rig.shieldWasOn = rig.shieldOn

    val g = SHIELD_GEO
    val fx = ShieldFx
    val t = nanos / 1_000_000_000f
    val cx = rig.x
    val cy = rig.y + rig.jumpH + SHIELD_CY
    val cz = rig.z

    if (rig.shieldHitT > 0f) rig.shieldHitT = max(0f, rig.shieldHitT - dt)
    if (rig.shieldAppearT > 0f) rig.shieldAppearT = max(0f, rig.shieldAppearT - dt)
    val fade = rig.shieldHitT / SHIELD_HIT_TIME
    val hit = fade > 0f && !breaking
    val hx = rig.shieldDx
    val hy = rig.shieldDy
    val hz = rig.shieldDz
    val front = (1f - fade) * 2.6f
    val ap = 1f - rig.shieldAppearT / SHIELD_APPEAR_TIME
    val ease = ap * ap * (3f - 2f * ap)
    val bp = if (breaking) 1f - rig.shieldBreakT / SHIELD_BREAK_TIME else 0f
    val low = rig.shieldHp <= SHIELD_MAX_HITS * 0.3f
    val scanY = ((t * (if (low) 0.8f else 0.45f)) % 2.6f) - 1.3f
    val breathe = 1f + 0.015f * sin(t * 2.2f)

    // --- solid body: translucent ball, soft outer glow, floor disc ---
    val grow = if (ease < 1f) 0.5f + 0.5f * ease else 1f
    val bodyR = SHIELD_R * 0.97f * breathe * grow
    val body = fill[0]
    val glow = fill[1]
    val disc = fill[2]
    if (breaking) {
        body.isVisible = false
        glow.isVisible = false
        disc.isVisible = false
    } else {
        val want = if (hit && fade > 0.55f) 1 else if (low) 2 else 0
        if (fx.fillCur != want && fx.fillMats.size == 3) {
            fx.fillCur = want
            (body as? SphereNode)?.materialInstance = fx.fillMats[want]
        }
        body.position = Position(cx, cy, cz)
        body.scale = Float3(bodyR, bodyR, bodyR)
        val glowR = bodyR * (1.06f + 0.015f * sin(t * 3f))
        glow.position = Position(cx, cy, cz)
        glow.scale = Float3(glowR, glowR, glowR)
        val dr = grow * (1f + 0.03f * sin(t * 2.6f))
        disc.position = Position(cx, rig.y + 0.03f, cz)
        disc.scale = Float3(dr, 1f, dr)
        body.isVisible = true
        glow.isVisible = true
        disc.isVisible = rig.jumpH < 0.3f
    }

    // --- hex lines ---
    for (i in nodes.indices) {
        val n = nodes[i]
        val ux = g.dir[i * 3]
        val uy = g.dir[i * 3 + 1]
        val uz = g.dir[i * 3 + 2]
        var k = breathe + 0.012f * sin(t * 1.7f + (ux * 3.1f + uy * 4.3f + uz * 2.3f) * 3f)
        var thick = 1f
        var m = g.band[i]
        if (abs(uy - scanY) < 0.15f) m = if (low) M_AMBER else M_SHINE

        if (hit) {
            val ang = acos((ux * hx + uy * hy + uz * hz).coerceIn(-1f, 1f))
            val rel = front - ang
            if (rel > 0f) k += 0.10f * fade * exp(-rel * 2.2f) * sin(rel * 14f)
            if (ang < 0.45f) k -= 0.05f * fade * (1f - ang / 0.45f)
            val q = rel / 0.22f
            val ring = exp(-q * q)
            if (ring > 0.35f) { m = M_HOT; thick = 1f + 1.6f * ring * fade }
            else if (ring > 0.12f) { m = M_SHINE; thick = 1f + 0.6f * ring * fade }
        }
        if (ease < 1f) {
            k *= 0.5f + 0.5f * ease
            thick *= ease
            if (ease < 0.8f) m = M_SHINE
        }
        if (breaking) {
            k *= 1f + 1.8f * bp
            thick *= (1f - bp)
            m = M_HOT
        }
        thick = max(thick, 0.001f)
        n.position = Position(cx + g.mid[i * 3] * k, cy + g.mid[i * 3 + 1] * k, cz + g.mid[i * 3 + 2] * k)
        n.scale = Float3(thick, thick, g.lens[i] * k)
        if (fx.cur[i] != m) {
            fx.cur[i] = m
            n.materialInstance = fx.mats[m]
        }
        if (!n.isVisible) n.isVisible = true
    }
}

// ---------------- Bullets ----------------

private fun updateBullets(dt: Float, rig: GameInput, bullets: List<CubeNode>, fx: Float, fz: Float) {
    if (!rig.shooting) rig.fireTimer = 0f
    rig.fireTimer -= dt
    if (rig.shooting && rig.fireTimer <= 0f) {
        val i = rig.bAlive.indexOfFirst { !it }
        if (i >= 0) {
            rig.bAlive[i] = true
            rig.bx[i] = rig.mx
            rig.bz[i] = rig.mz
            rig.by[i] = rig.my
            rig.bdx[i] = fx
            rig.bdz[i] = fz
            rig.bDist[i] = 0f
            bullets[i].rotation = Rotation(y = atan2(fx, fz) * RAD_TO_DEG)
            rig.fireTimer = FIRE_INTERVAL
            rig.flashT = FLASH_TIME
        }
    }
    val mt = rig.match
    for (i in 0 until BULLET_COUNT) {
        if (!rig.bAlive[i]) { bullets[i].isVisible = false; continue }
        var left = BULLET_SPEED * dt
        while (left > 0f && rig.bAlive[i]) {
            val step = min(0.25f, left)
            val nx = rig.bx[i] + rig.bdx[i] * step
            val nz = rig.bz[i] + rig.bdz[i] * step
            val wall = rig.play?.bulletHits(nx, rig.by[i], nz) ?: false
            if (wall || rig.bDist[i] > BULLET_RANGE) {
                if (wall) spawnBurst(rig, rig.bx[i], rig.by[i], rig.bz[i], 0, SPARK_PIECES)
                rig.bAlive[i] = false
            } else if (mt != null && mt.bulletStep(nx, rig.by[i], nz, 0, rig)) {
                rig.bAlive[i] = false
            } else {
                rig.bx[i] = nx
                rig.bz[i] = nz
                rig.bDist[i] += step
            }
            left -= step
        }
        bullets[i].isVisible = rig.bAlive[i]
        if (rig.bAlive[i]) bullets[i].position = Position(rig.bx[i], rig.by[i], rig.bz[i])
    }
}

// ---------------- Hit effects ----------------

private fun spawnBurst(rig: GameInput, x: Float, y: Float, z: Float, kind: Int, n: Int) {
    val from = if (kind == 1) FX_HALF else 0
    val to = if (kind == 1) FX_COUNT else FX_HALF
    var made = 0
    for (i in from until to) {
        if (made >= n) break
        if (rig.fxLife[i] > 0f) continue
        made++
        val a = Random.nextFloat() * 2f * PI.toFloat()
        val sp = if (kind == 1) 0.8f + Random.nextFloat() * 3.2f else 2f + Random.nextFloat() * 3.5f
        rig.fxX[i] = x + (Random.nextFloat() - 0.5f) * 0.1f
        rig.fxY[i] = y + (Random.nextFloat() - 0.5f) * 0.1f
        rig.fxZ[i] = z + (Random.nextFloat() - 0.5f) * 0.1f
        rig.fxVX[i] = cos(a) * sp
        rig.fxVZ[i] = sin(a) * sp
        rig.fxVY[i] = if (kind == 1) 0.4f + Random.nextFloat() * 2.6f else 1f + Random.nextFloat() * 3f
        rig.fxLife[i] = (if (kind == 1) 1.0f else 0.25f) + Random.nextFloat() * 0.2f
        rig.fxSplat[i] = false
    }
}

private fun updateFx(dt: Float, rig: GameInput, nodes: List<CubeNode>) {
    for (i in 0 until FX_COUNT) {
        val n = nodes[i]
        if (rig.fxLife[i] <= 0f) { if (n.isVisible) n.isVisible = false; continue }
        rig.fxLife[i] -= dt
        if (rig.fxLife[i] <= 0f) { n.isVisible = false; rig.fxSplat[i] = false; continue }

        if (rig.fxSplat[i]) {
            val s = min(1f, rig.fxLife[i] / 0.6f)
            val w = (2.5f + (i % 3)) * s
            n.scale = Float3(w, 0.3f, w)
            continue
        }

        rig.fxVY[i] -= 9f * dt
        rig.fxX[i] += rig.fxVX[i] * dt
        rig.fxY[i] += rig.fxVY[i] * dt
        rig.fxZ[i] += rig.fxVZ[i] * dt

        if (i >= FX_HALF) {
            val floor = (rig.play?.groundY(rig.fxX[i], rig.fxZ[i], 1f) ?: 0f) + 0.02f
            if (rig.fxY[i] <= floor) {
                rig.fxY[i] = floor
                rig.fxSplat[i] = true
                rig.fxLife[i] = 1.6f + Random.nextFloat() * 0.8f
                n.position = Position(rig.fxX[i], rig.fxY[i], rig.fxZ[i])
                val w = 2.5f + (i % 3)
                n.scale = Float3(w, 0.3f, w)
                n.isVisible = true
                continue
            }
            n.scale = Float3(1f, 1f, 1f)
        }
        n.position = Position(rig.fxX[i], rig.fxY[i], rig.fxZ[i])
        n.isVisible = true
    }
}

// ---------------- Gun helpers ----------------

private fun attachGun(engine: Engine, gun: Node, model: ModelNode): Boolean {
    return try {
        val joints = model.modelInstance.getJointsAt(0)
        val bone = joints[RIGHT_HAND_JOINT]
        val tm = engine.transformManager
        tm.setParent(tm.getInstance(gun.entity), tm.getInstance(bone))
        true
    } catch (e: Throwable) {
        Log.e("Game3D", "Could not attach gun to hand", e)
        false
    }
}

private val handMatrix = FloatArray(16)
private fun handWorld(engine: Engine, model: ModelNode, out: FloatArray): Boolean {
    return try {
        val joints = model.modelInstance.getJointsAt(0)
        val bone = joints[RIGHT_HAND_JOINT]
        val tm = engine.transformManager
        tm.getWorldTransform(tm.getInstance(bone), handMatrix)
        out[0] = handMatrix[12]
        out[1] = handMatrix[13]
        out[2] = handMatrix[14]
        true
    } catch (e: Throwable) {
        false
    }
}

private fun quatXYZ(xDeg: Float, yDeg: Float, zDeg: Float): Quaternion {
    fun axis(deg: Float, ax: Float, ay: Float, az: Float): Quaternion {
        val h = deg * DEG_TO_RAD / 2f
        val s = sin(h)
        return Quaternion(ax * s, ay * s, az * s, cos(h))
    }
    fun mul(a: Quaternion, b: Quaternion) = Quaternion(
        a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y,
        a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
        a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w,
        a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z
    )
    return mul(mul(axis(xDeg, 1f, 0f, 0f), axis(yDeg, 0f, 1f, 0f)), axis(zDeg, 0f, 0f, 1f))
}
