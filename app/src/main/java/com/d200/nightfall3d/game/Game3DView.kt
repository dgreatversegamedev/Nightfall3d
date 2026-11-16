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
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import kotlin.math.PI
import kotlin.math.abs
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
// Optional (gun in hand): mp5_gun.glb, ak47_gun.glb, ar_gun.glb, rifle.glb, carbine.glb  (the game still works without them)
//
// CHANGES IN THIS VERSION
//  1) FLAGS look military now: dark steel pole with a finial, a banner made of 4 waving cloth pieces with a dark
//     stripe pattern, a dark pedestal with a glowing frame around it (the capture zone) and a thin light beam.
//     (FLAG_NODES_PER_TEAM = 12 nodes for each team.)
//  2) SHIELD is a glass honeycomb now: very thin pale bars (the 90 edges of the hexagon / pentagon ball). When a
//     bullet hits it, a ring of light spreads out from the hit spot over the shield, the bars near the ring glow
//     thicker, and a bright spot flashes where it was hit.
//  3) BLOOD: many small dark red drops (64) that fly out, fall, and become flat blood spots on the floor for a
//     moment. Sparks stay orange. A bullet that hits your shield makes sparks, not blood.
//  4) RIFLE and CARBINE are the right size now (scale 0.5 and 0.42) and use the grip / turn numbers fitted
//     in gun-size-fitter.html.
//
// EARLIER
//  - Capture the Flag drawing from MatchLogic.kt (MatchState), long glowing tracers, muzzle flash, scope (GunDef.hasScope).
//  - GUN FIT MODE: rig.fitOn (used by a fit panel in the HUD, if you add one).
// =====================================================================

// ---- Tuning numbers: change these if something looks wrong ----
private const val CHAR_HEIGHT = 1.8f      // character size in meters
private const val FACING_OFFSET = 0f      // if he walks sideways/backwards, try 90f, 180f or -90f
private const val WALK_SPEED = 2.2f
private const val TURN_SPEED = 540f       // degrees per second he turns toward where he walks
private const val AIM_TURN_SPEED = 1080f  // degrees per second he turns toward where he aims
private const val ACCEL = 9f              // how fast he speeds up / slows down
private const val BLEND_TIME = 0.25f      // seconds to blend between animations
private const val BLEND_FLIP = false      // if the blend looks backwards (pops at the end), change to true
private const val RUN_SPEED = 4.6f
private const val CAM_DIST = 3.2f         // how far behind him (pinch with two fingers to change)
private const val CAM_HEIGHT = 1.5f       // camera height (chest level, always)
private const val CAM_LOOK_AHEAD = 6f     // looks this far in front of him
private const val CAM_LOOK_Y = 1.0f       // height of the point the camera looks at
private const val ZOOM_MIN = 0.4f         // closest (pinch out)
private const val ZOOM_MAX = 3f           // farthest (pinch in)
private const val CAM_DRAG_SPEED = 0.25f  // degrees of camera turn per pixel dragged
private const val CAM_PITCH_SPEED = 0.2f  // degrees of camera tilt per pixel dragged up/down
private const val CAM_PITCH_MAX = 55f     // highest the camera can go (degrees above chest level)
private const val AIM_DEADZONE = 0.15f    // aim stick: pushed less than this = no aim yet
private const val BACKPEDAL_DOT = -0.2f   // moving against his facing by this much = play the walk backward
private const val RAD_TO_DEG = 180f / PI.toFloat()
private const val DEG_TO_RAD = PI.toFloat() / 180f

// ---- Scope ----
private const val SCOPE_DIST = 0.35f      // camera distance behind him while scoped (small = over the shoulder)
private const val SCOPE_DRAG = 0.4f       // camera turns slower while scoped (steady aim)

// ---- Bullets ----
private const val BULLET_COUNT = 24
private const val BULLET_SPEED = 40f          // meters per second
private const val BULLET_RANGE = 50f          // bullet disappears after this distance
private const val FIRE_INTERVAL = 0.12f       // seconds between shots while the fire stick is held
private const val MUZZLE_FORWARD = 0.5f       // bullet starts this far in front of the HAND
private const val AIM_POINT_DIST = 12f        // the bullet aims at the point this far from him along the ring direction
private const val BULLET_THICK = 0.03f        // tracer thickness (meters)
private const val BULLET_LEN = 0.9f           // tracer length (meters). Longer = more like a streak
private const val FLASH_TIME = 0.06f          // how long the muzzle flash stays (seconds)
// Fallback only (used if the hand position cannot be read)
private const val BULLET_HEIGHT = 1.15f
private const val BULLET_START_FORWARD = 0.9f
private const val BULLET_START_SIDE = 0.25f

// ---- Hit effects (sparks on walls / shield, blood on fighters) ----
private const val FX_HALF = 24                // first 24 = sparks, the rest = blood
private const val FX_COUNT = 88               // 24 sparks + 64 blood drops
private const val BLOOD_PIECES = 14           // drops for one bullet hit
private const val SPARK_PIECES = 5

// ---- Gun in the right hand ----
private const val RIGHT_HAND_JOINT = 14          // position of the right hand in the skeleton list

// ---- Punch and jump ----
private const val PUNCH_LEN = 0.5f
private const val PUNCH_CHAIN_AT = 0.3f
private const val JUMP_CLIP = "jump_inplace"
private const val CROUCH_POSE_T = 0.40f
private const val CROUCH_SPEED = 1.2f

// ---- Shield: glass honeycomb (12 pentagons + 20 hexagons = 90 edges) ----
private const val SHIELD_R = 1.1f             // bubble radius (meters)
private const val SHIELD_CY = 0.9f            // bubble centre height
private const val SHIELD_EDGE_THICK = 0.02f   // thickness of each glass line (thin = glass, thick = box)
private const val SHIELD_HIT_TIME = 0.7f      // how long the hit ripple lasts (same number in MatchLogic.kt)
private const val RIPPLE_RING = 16            // light spots in the ripple ring
private const val RIPPLE_COUNT = RIPPLE_RING + 1   // + 1 bright spot where the bullet hit
private const val JUMP_LEN = 0.5f
private const val JUMP_TAKEOFF_T = 0.1406f
private const val JUMP_LAND_T = 0.3594f
private const val JUMP_SPEED = 8f
private const val GRAVITY = 24f

// ---- Capture the Flag drawing ----
// 12 nodes per team: 0 pole | 1 finial | 2..5 banner pieces | 6 pedestal | 7..10 base frame bars | 11 light beam
private const val FLAG_NODES_PER_TEAM = 12

// Gun turn (degrees, X/Y/Z)
internal class GunTurn(val x: Float, val y: Float, val z: Float)

// One gun. pos/scale/grip/carry/aim are the fit numbers for THAT gun (from the gun fit mode in the HUD).
// hasScope = true only for guns that really have a scope (sniper). The HUD scope button only works for those.
internal class GunDef(
    val name: String,
    val file: String,
    val pos: Position,
    val scale: Float,
    val grip: Position,        // moves the gun so the grip sits in the hand
    val carry: GunTurn,        // idle / walk / run
    val aim: Map<String, GunTurn>,   // muzzle straight forward while shooting
    val hasScope: Boolean = false
)

// ADD NEW GUNS HERE (one entry each). For a sniper add  hasScope = true  as the last line.
// To fit a gun: play, tap FIT in the HUD, adjust, tap COPY, then paste the copied block here in place of that gun.
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
    // rifle.glb (sniper with scope). Size and grip fitted in gun-size-fitter.html (about 0.9 m long on the 1.8 m character).
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
    // carbine.glb. Size and grip fitted in gun-size-fitter.html (about 0.76 m long on the 1.8 m character).
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

// ADD NEW ARENAS HERE (index 0 = "Zero Arena" = the default).
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
    var y = 0f               // floor height under him
    var z = 0f
    var play: PlayArena? = null   // walls + ramp, filled when arena_play.json is read
    var match: MatchState? = null // Deathmatch / Team Deathmatch / Capture the Flag (null = no match, only you)
    var camPitch = 0f
    var camYaw = 0f          // degrees, 0 = looking toward -Z
    var faceDeg = 0f
    var joyX = 0f            // move stick
    var joyY = 0f
    var shooting = false
    var aimHeld = false
    var aimX = 0f
    var aimY = 0f
    var paused = false
    var crouch = false
    var shieldOn = false      // shield bubble is up (HUD shield button)
    var scopeOn = false       // scoped in (HUD scope button, only for guns with hasScope)
    var handMode = false
    var gunWanted by mutableIntStateOf(0)   // which gun the HUD wants in the hand (index in GUNS)
    var zoom = 1f
    var active = 0
    var lastNanos = 0L
    var gunOwner = -1
    var gunLoaded = false
    var gunDef: GunDef = GUNS[0]
    var gunChild: Node? = null    // the gun model inside the gun holder (its position = the grip numbers)
    val attachFails = IntArray(3)
    var fireTimer = 0f
    var mx = 0f
    var my = 0f
    var mz = 0f
    var flashT = 0f           // muzzle flash timer
    val hand = FloatArray(3)
    val bAlive = BooleanArray(BULLET_COUNT)
    val bx = FloatArray(BULLET_COUNT)
    val bz = FloatArray(BULLET_COUNT)
    val by = FloatArray(BULLET_COUNT)
    val bdx = FloatArray(BULLET_COUNT)
    val bdz = FloatArray(BULLET_COUNT)
    val bDist = FloatArray(BULLET_COUNT)
    // hit effect particles (sparks 0..23, blood 24..87)
    val fxLife = FloatArray(FX_COUNT)
    val fxX = FloatArray(FX_COUNT)
    val fxY = FloatArray(FX_COUNT)
    val fxZ = FloatArray(FX_COUNT)
    val fxVX = FloatArray(FX_COUNT)
    val fxVY = FloatArray(FX_COUNT)
    val fxVZ = FloatArray(FX_COUNT)
    val fxSplat = BooleanArray(FX_COUNT)    // blood drop that landed and is now a flat spot on the floor
    // shield hit (set by MatchLogic.kt when a bullet hits your shield)
    var shieldHitT = 0f        // seconds left of the ripple (0 = no ripple)
    var shieldDx = 0f          // direction from the shield centre to the hit spot (length 1)
    var shieldDy = 0f
    var shieldDz = 1f
    // punch combo + jump
    val combo = PunchCombo()
    var punchClip = ""
    var punchT = 0f
    var punchTap = false
    var jumpTap = false
    var jumpState = 0         // 0 on the ground, 1 crouching before takeoff, 2 in the air, 3 landing
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
    var fitOn = false                 // true = the gun uses the fit numbers below
    var fitSel = 0                    // which pose is shown/edited: 0 carry, 1 shoot, 2 walk_shoot, 3 run_shoot
    var fitGunName = ""               // the gun the fit numbers belong to (they reload when the gun changes)
    var fitGx = 0f                    // grip x / y / z
    var fitGy = 0f
    var fitGz = 0f
    var fitScale = 0.24f
    val fitRot = HashMap<String, FloatArray>()   // pose name -> turn x, y, z (degrees)
    var fitTick by mutableIntStateOf(0)          // goes up whenever a fit number changes (so the HUD redraws)
    var fitPose by mutableStateOf("carry")       // the pose being edited right now (shown in the HUD)

    fun fitKey(): String = when (fitSel) {
        1 -> "shoot"
        2 -> "walk_shoot"
        3 -> "run_shoot"
        else -> "carry"
    }
}

// Where one character is in its animations (used for the blend)
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

// Everything that is drawn for ONE bot: the character copy, its gun, the marker over the head and the health bar
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

// ---------------- Shield shape: truncated icosahedron (soccer ball) ----------------
// 60 corners, 90 edges. Every edge is one thin glass line, so the bubble looks like a hexagon honeycomb.
private class ShieldGeo(val count: Int, val mid: FloatArray, val quat: Array<Quaternion>, val len: Float)

private val SHIELD_GEO: ShieldGeo by lazy { buildShieldGeo() }

private fun buildShieldGeo(): ShieldGeo {
    val phi = (1f + sqrt(5f)) / 2f
    val bases = listOf(
        floatArrayOf(0f, 1f, 3f * phi),
        floatArrayOf(1f, 2f + phi, 2f * phi),
        floatArrayOf(phi, 2f, 2f * phi + 1f)
    )
    val pts = ArrayList<FloatArray>()
    for (b in bases) {
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) {
            if (b[0] == 0f && sx == -1) continue           // 0 has no sign: do not make the same corner twice
            val x = b[0] * sx
            val y = b[1] * sy
            val z = b[2] * sz
            pts.add(floatArrayOf(x, y, z))                 // even (cyclic) permutations only
            pts.add(floatArrayOf(y, z, x))
            pts.add(floatArrayOf(z, x, y))
        }
    }
    val p0 = pts[0]
    val radius = sqrt(p0[0] * p0[0] + p0[1] * p0[1] + p0[2] * p0[2])
    val s = SHIELD_R / radius
    val mids = ArrayList<Float>()
    val quats = ArrayList<Quaternion>()
    for (i in pts.indices) for (j in i + 1 until pts.size) {
        val a = pts[i]
        val b = pts[j]
        val dx = b[0] - a[0]
        val dy = b[1] - a[1]
        val dz = b[2] - a[2]
        val d = sqrt(dx * dx + dy * dy + dz * dz)
        if (d < 1.9f || d > 2.1f) continue                 // neighbours are exactly 2 apart
        mids.add((a[0] + b[0]) / 2f * s)
        mids.add((a[1] + b[1]) / 2f * s)
        mids.add((a[2] + b[2]) / 2f * s)
        quats.add(quatFromZ(dx / d, dy / d, dz / d))
    }
    return ShieldGeo(quats.size, mids.toFloatArray(), quats.toTypedArray(), 2f * s * 1.04f)
}

// Turns the Z axis (0,0,1) toward the unit direction (dx, dy, dz)
private fun quatFromZ(dx: Float, dy: Float, dz: Float): Quaternion {
    if (dz > 0.9999f) return Quaternion(0f, 0f, 0f, 1f)
    if (dz < -0.9999f) return Quaternion(1f, 0f, 0f, 0f)
    val w = 1f + dz
    val x = -dy
    val y = dx
    val n = sqrt(x * x + y * y + w * w)
    return Quaternion(x / n, y / n, 0f, w / n)
}

// Names for the pick screens in Game3DHub (so the lists live in one place)
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

    // Your bullets: long glowing tracers (hot orange-yellow)
    val bullets = remember {
        val bulletMat = materialLoader.createColorInstance(Color(0xFFFFB02E))
        List(BULLET_COUNT) {
            CubeNode(engine, size = Float3(BULLET_THICK, BULLET_THICK, BULLET_LEN), materialInstance = bulletMat)
                .apply { isVisible = false }
        }
    }

    // Bot bullets (red-orange, so you can tell them from yours)
    val botBullets = remember {
        val mat = materialLoader.createColorInstance(Color(0xFFFF4A2C))
        List(BOT_BULLET_COUNT) {
            CubeNode(engine, size = Float3(BULLET_THICK, BULLET_THICK, BULLET_LEN), materialInstance = mat)
                .apply { isVisible = false }
        }
    }

    // Muzzle flash at the gun
    val muzzleFlash = remember {
        val mat = materialLoader.createColorInstance(Color(0xFFFFD35A))
        CubeNode(engine, size = Float3(0.16f, 0.16f, 0.16f), materialInstance = mat).apply { isVisible = false }
    }

    // Hit effects: 24 sparks (wall / shield hits) + 64 blood drops (fighter hits). Blood = small, dark red, two shades.
    val fxNodes = remember {
        val spark = materialLoader.createColorInstance(Color(0xFFFFC040))
        val bloodDark = materialLoader.createColorInstance(Color(0xFF7A0C12))
        val bloodBright = materialLoader.createColorInstance(Color(0xFFB8161E))
        List(FX_COUNT) { i ->
            if (i < FX_HALF) CubeNode(engine, size = Float3(0.045f, 0.045f, 0.045f), materialInstance = spark)
            else CubeNode(engine, size = Float3(0.035f, 0.035f, 0.035f), materialInstance = if (i % 3 == 0) bloodBright else bloodDark)
        }.also { l -> l.forEach { it.isVisible = false } }
    }

    // Capture the Flag. 12 nodes for each team (see FLAG_NODES_PER_TEAM). Team 0 (your flag) is orange, team 1 (enemy) is cyan.
    // Hidden unless the mode is Capture the Flag.
    val flagNodes = remember {
        val steel = materialLoader.createColorInstance(Color(0xFF3A3F45))       // pole
        val gunmetal = materialLoader.createColorInstance(Color(0xFF23272B))    // pedestal
        val orange = materialLoader.createColorInstance(Color(0xFFFF8A00))
        val orangeDark = materialLoader.createColorInstance(Color(0xFF8A4300))
        val cyan = materialLoader.createColorInstance(Color(0xFF35D3FF))
        val cyanDark = materialLoader.createColorInstance(Color(0xFF14677F))
        val list = ArrayList<CubeNode>()
        for (team in 0..1) {
            val main = if (team == 0) orange else cyan
            val dark = if (team == 0) orangeDark else cyanDark
            list.add(CubeNode(engine, size = Float3(0.06f, 2.3f, 0.06f), materialInstance = steel))          // 0 pole
            list.add(CubeNode(engine, size = Float3(0.12f, 0.12f, 0.12f), materialInstance = main))          // 1 finial
            for (s in 0 until 4) {                                                                           // 2..5 banner pieces
                list.add(CubeNode(engine, size = Float3(0.2f, 0.46f, 0.025f), materialInstance = if (s % 2 == 0) main else dark))
            }
            list.add(CubeNode(engine, size = Float3(2.1f, 0.12f, 2.1f), materialInstance = gunmetal))        // 6 pedestal
            list.add(CubeNode(engine, size = Float3(3.0f, 0.05f, 0.07f), materialInstance = main))           // 7 frame (+z)
            list.add(CubeNode(engine, size = Float3(3.0f, 0.05f, 0.07f), materialInstance = main))           // 8 frame (-z)
            list.add(CubeNode(engine, size = Float3(0.07f, 0.05f, 3.0f), materialInstance = main))           // 9 frame (+x)
            list.add(CubeNode(engine, size = Float3(0.07f, 0.05f, 3.0f), materialInstance = main))           // 10 frame (-x)
            list.add(CubeNode(engine, size = Float3(0.05f, 12f, 0.05f), materialInstance = main))            // 11 beam (seen from far)
        }
        list.forEach { it.isVisible = false }
        list.toList()
    }

    // The bots that are drawn (filled after the arena and your character are loaded)
    val botViews = remember { mutableListOf<BotView>() }

    // Shield: glass honeycomb bubble (hidden until the shield button is on). Each bar is turned once, here.
    val shieldNodes = remember {
        val geo = SHIELD_GEO
        val mat = materialLoader.createColorInstance(Color(0xFF9FE6FF))      // pale glass blue
        List(geo.count) { i ->
            CubeNode(engine, size = Float3(SHIELD_EDGE_THICK, SHIELD_EDGE_THICK, geo.len), materialInstance = mat)
                .apply {
                    quaternion = geo.quat[i]
                    isVisible = false
                }
        }
    }

    // Shield hit ripple: a ring of bright spots that spreads out from the hit spot + one flash at the hit spot
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
        addAll(shieldNodes)
        addAll(rippleNodes)
        add(muzzleFlash)
        addAll(fxNodes)
        addAll(flagNodes)
    }

    // 1) arena, 2) character. One step at a time so nothing freezes.
    LaunchedEffect(Unit) {
        try {
            withFrameNanos { }
            val p = PlayArena.load(context)
            rig.play = p
            rig.x = p.blueX
            rig.z = p.blueZ
            rig.camYaw = if (p.blueX < 0f) 90f else -90f   // face the middle of the arena
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
                centerOrigin = Position(0f, -1f, 0f)   // feet on the ground
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

    // 3) the gun in the hand. Runs again whenever the HUD changes gunWanted.
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

    // 4) the bots (Deathmatch / Team Deathmatch / Capture the Flag). Each bot = one more copy of char_blue.glb + a gun.
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        val mt = rig.match ?: return@LaunchedEffect
        if (botViews.isNotEmpty()) return@LaunchedEffect
        try {
            val enemyMat = materialLoader.createColorInstance(Color(0xFFFF3B3B))
            val allyMat = materialLoader.createColorInstance(Color(0xFF35E07A))
            val hpMat = materialLoader.createColorInstance(Color(0xFFF4F6FA))
            for (b in mt.bots) {
                val gi = (0..GUNS.lastIndex).random()      // any of the guns
                val gdef = GUNS[gi]
                withFrameNanos { }
                val m = ModelNode(
                    modelInstance = modelLoader.createModelInstance(CHAR_FILES[0]),   // always the blue one
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
                stepGame(nanos, rig, models, cameraNode, engine, gun, bullets, shieldNodes, rippleNodes, botViews, botBullets, muzzleFlash, fxNodes, flagNodes)
            }
        )

        // Whole-screen touch layer UNDER the HUD buttons: one finger drag = turn camera, two fingers = zoom/twist
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
                                val sens = if (rig.scopeOn) SCOPE_DRAG else 1f     // steadier while scoped
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

// The animation name the gun fit mode forces for each pose tab
private fun fitWantOf(sel: Int): String = when (sel) {
    1 -> "shoot"
    2 -> "walk_shoot"
    3 -> "run_shoot"
    else -> "idle"
}

// Copies the numbers of the gun in the hand into the fit values, once per gun (so the panel starts from the real numbers)
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
    rippleNodes: List<CubeNode>,
    botViews: List<BotView>,
    botBullets: List<CubeNode>,
    muzzleFlash: CubeNode,
    fxNodes: List<CubeNode>,
    flagNodes: List<CubeNode>
) {
    // Paused: freeze everything (the camera stays where it is)
    if (rig.paused) { rig.lastNanos = nanos; return }
    // fists mode: no aiming, no shooting
    if (rig.handMode) { rig.shooting = false; rig.aimHeld = false; rig.scopeOn = false }

    val dt = if (rig.lastNanos == 0L) 0f else ((nanos - rig.lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
    rig.lastNanos = nanos

    // Eliminated: the controls do nothing until he respawns
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

    // ---- Jump: crouch -> up and down with real height -> landing ----
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
    val fx = sin(yawR)          // camera forward
    val fz = -cos(yawR)
    val rx = cos(yawR)          // camera right
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
        rig.fitOn -> fitWantOf(rig.fitSel)           // gun fit mode: the pose picked in the panel
        rig.jumpState != 0 -> JUMP_CLIP
        rig.punchClip.isNotEmpty() -> rig.punchClip
        rig.crouch -> JUMP_CLIP
        else -> shootWant
    }
    val forceTime = if (rig.fitOn) -1f else if (rig.jumpState != 0) rig.jumpClipT else if (rig.punchClip.isNotEmpty()) rig.punchT else if (rig.crouch) CROUCH_POSE_T else -1f
    val layerBase = if (want == "punch_1" || want == "punch_2") base else ""

    val moveDot = sx * rig.moveX + sz * rig.moveZ
    val timeScale = if (rig.speed > 0.15f && moveDot < BACKPEDAL_DOT) -1f else 1f

    // When scoped the camera sits at his shoulder, so his body is hidden (you look through the scope)
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
        // gun fit mode: numbers from the HUD panel (grip, scale, and the turn of the pose being edited)
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

    // Muzzle flash: a small bright burst at the gun for a few frames after each shot
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

    // Deathmatch / Team Deathmatch / Capture the Flag: bots think, shoot, die, respawn. Then they are drawn.
    if (mt != null) {
        mt.update(dt, rig)
        stepBots(dt, rig, mt, botViews, botBullets, engine)
        // hit spots collected by the match logic -> sparks (wall / shield) or blood (fighter)
        for (h in mt.hitFx) spawnBurst(rig, h[0], h[1], h[2], h[3].toInt(), if (h[3] > 0.5f) BLOOD_PIECES else SPARK_PIECES)
        mt.hitFx.clear()
        if (mt.over) rig.paused = true      // match finished: freeze the game, the HUD shows the result
    }
    // the shield is drawn after the match update, so a hit from this frame shows at once
    updateShield(rig, shieldNodes, rippleNodes, nanos, dt)
    updateFx(dt, rig, fxNodes)
    updateFlags(mt, flagNodes, nanos)

    // Follow camera. Normal: behind him (distance = pinch zoom). Scoped: at his shoulder.
    val dist = if (rig.scopeOn) SCOPE_DIST else CAM_DIST * rig.zoom
    val pr = rig.camPitch * DEG_TO_RAD
    val back = dist * cos(pr)
    val camY = rig.y + rig.jumpH * 0.4f
    cameraNode.position = Position(rig.x - fx * back, camY + CAM_HEIGHT + dist * sin(pr), rig.z - fz * back)
    cameraNode.lookAt(Position(rig.x + fx * CAM_LOOK_AHEAD, camY + CAM_LOOK_Y, rig.z + fz * CAM_LOOK_AHEAD))
}

// ---------------- Capture the Flag: draw the two flags ----------------

// nodes = 12 per team (team 0 first): 0 pole | 1 finial | 2..5 banner pieces | 6 pedestal | 7..10 base frame | 11 beam.
// Positions come from MatchState. The pedestal and the frame always stay at the base. The rest follows the flag.
private fun updateFlags(mt: MatchState?, nodes: List<CubeNode>, nanos: Long) {
    if (mt == null || !mt.ctf) {
        for (n in nodes) if (n.isVisible) n.isVisible = false
        return
    }
    val t = nanos / 1_000_000_000f
    for (team in 0..1) {
        val o = team * FLAG_NODES_PER_TEAM
        val x = mt.flagX[team]
        val y = mt.flagY[team]
        val z = mt.flagZ[team]
        val hx = mt.homeX[team]
        val hy = mt.homeY[team]
        val hz = mt.homeZ[team]
        val s = if (mt.flagState[team] == F_CARRIED) 0.65f else 1f      // smaller on somebody's back

        // pole + finial
        nodes[o].position = Position(x, y + 1.15f * s, z)
        nodes[o].scale = Float3(s, s, s)
        nodes[o + 1].position = Position(x, y + 2.36f * s, z)
        nodes[o + 1].scale = Float3(s, s, s)

        // banner: 4 cloth pieces next to each other, each one waves a little more than the one before
        for (i in 0 until 4) {
            val node = nodes[o + 2 + i]
            val phase = t * 4f - i * 0.9f + team * 1.7f
            val along = (0.12f + i * 0.2f) * s
            node.position = Position(
                x + along,
                y + (2.0f - i * 0.025f) * s,
                z + sin(phase) * 0.045f * (i + 1) * s
            )
            node.rotation = Rotation(y = sin(phase) * 12f)
            node.scale = Float3(s, s, s)
        }

        // base: dark pedestal + glowing frame (this square is the capture zone)
        nodes[o + 6].position = Position(hx, hy + 0.06f, hz)
        nodes[o + 7].position = Position(hx, hy + 0.14f, hz + 1.5f)
        nodes[o + 8].position = Position(hx, hy + 0.14f, hz - 1.5f)
        nodes[o + 9].position = Position(hx + 1.5f, hy + 0.14f, hz)
        nodes[o + 10].position = Position(hx - 1.5f, hy + 0.14f, hz)

        // thin light beam on the flag, so you can find it from far
        nodes[o + 11].position = Position(x, y + 6f, z)

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
    val barYaw = -rig.camYaw                     // health bar turned to face the camera
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

// Moves the 90 glass lines of the honeycomb around him (they were already turned when they were made).
// When a bullet hits (rig.shieldHitT > 0): a ring of light spreads over the shield from the hit spot,
// the lines near the ring get thicker (they glow), and a bright flash shows at the hit spot.
private fun updateShield(rig: GameInput, nodes: List<CubeNode>, ripple: List<CubeNode>, nanos: Long, dt: Float) {
    if (!rig.shieldOn || rig.match?.playerDead == true) {
        for (n in nodes) if (n.isVisible) n.isVisible = false
        for (n in ripple) if (n.isVisible) n.isVisible = false
        rig.shieldHitT = 0f
        return
    }
    val g = SHIELD_GEO
    val t = nanos / 1_000_000_000f
    val pulse = 1f + 0.02f * sin(t * 5f)                  // the bubble breathes a little
    val cx = rig.x
    val cy = rig.y + rig.jumpH + SHIELD_CY
    val cz = rig.z

    // ripple state
    if (rig.shieldHitT > 0f) rig.shieldHitT = max(0f, rig.shieldHitT - dt)
    val fade = rig.shieldHitT / SHIELD_HIT_TIME            // 1 = just hit, 0 = over
    val hit = fade > 0f
    val hx = rig.shieldDx
    val hy = rig.shieldDy
    val hz = rig.shieldDz
    val wave = (1f - fade) * 2.4f                          // how far the ring has spread (meters over the shield)
    val hpx = hx * SHIELD_R
    val hpy = hy * SHIELD_R
    val hpz = hz * SHIELD_R

    for (i in nodes.indices) {
        val n = nodes[i]
        val mx = g.mid[i * 3] * pulse
        val my = g.mid[i * 3 + 1] * pulse
        val mz = g.mid[i * 3 + 2] * pulse
        n.position = Position(cx + mx, cy + my, cz + mz)
        var k = 1f
        if (hit) {
            val d = sqrt((mx - hpx) * (mx - hpx) + (my - hpy) * (my - hpy) + (mz - hpz) * (mz - hpz))
            val near = max(0f, 1f - abs(d - wave) / 0.55f)   // 1 right on the ring, 0 away from it
            k = 1f + 4f * near * fade
        }
        n.scale = if (k > 1.01f) Float3(k, k, 1f) else Float3(1f, 1f, 1f)
        if (!n.isVisible) n.isVisible = true
    }

    if (!hit) {
        for (n in ripple) if (n.isVisible) n.isVisible = false
        return
    }
    // two directions that are at right angles to the hit direction (to lay the ring around it)
    val ax = if (abs(hy) < 0.9f) 0f else 1f
    val ay = if (abs(hy) < 0.9f) 1f else 0f
    var ux = ay * hz - 0f * hy
    var uy = 0f * hx - ax * hz
    var uz = ax * hy - ay * hx
    val ul = sqrt(ux * ux + uy * uy + uz * uz).coerceAtLeast(0.0001f)
    ux /= ul; uy /= ul; uz /= ul
    val vx = hy * uz - hz * uy
    val vy = hz * ux - hx * uz
    val vz = hx * uy - hy * ux

    val theta = (1f - fade) * 1.3f                         // ring size as an angle around the hit spot
    val ct = cos(theta)
    val st = sin(theta)
    val sc = 0.5f + fade                                   // light spots get smaller as it fades
    for (i in 0 until RIPPLE_RING) {
        val phi = i.toFloat() / RIPPLE_RING * 2f * PI.toFloat()
        val ox = ux * cos(phi) + vx * sin(phi)
        val oy = uy * cos(phi) + vy * sin(phi)
        val oz = uz * cos(phi) + vz * sin(phi)
        val px = (hx * ct + ox * st) * SHIELD_R * 1.02f
        val py = (hy * ct + oy * st) * SHIELD_R * 1.02f
        val pz = (hz * ct + oz * st) * SHIELD_R * 1.02f
        val node = ripple[i]
        node.position = Position(cx + px, cy + py, cz + pz)
        node.scale = Float3(sc, sc, sc)
        if (!node.isVisible) node.isVisible = true
    }
    // the bright flash at the hit spot
    val core = ripple[RIPPLE_RING]
    core.position = Position(cx + hpx * 1.02f, cy + hpy * 1.02f, cz + hpz * 1.02f)
    val cs = 1f + fade * 2.2f
    core.scale = Float3(cs, cs, cs)
    if (!core.isVisible) core.isVisible = true
}

// ---------------- Bullets ----------------

// fx, fz = the direction every new bullet flies in (horizontal, length 1)
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
            rig.flashT = FLASH_TIME                     // muzzle flash for this shot
        }
    }
    val mt = rig.match
    for (i in 0 until BULLET_COUNT) {
        if (!rig.bAlive[i]) { bullets[i].isVisible = false; continue }
        var left = BULLET_SPEED * dt
        while (left > 0f && rig.bAlive[i]) {
            val step = min(0.25f, left)          // small steps so thin walls can't be skipped
            val nx = rig.bx[i] + rig.bdx[i] * step
            val nz = rig.bz[i] + rig.bdz[i] * step
            val wall = rig.play?.bulletHits(nx, rig.by[i], nz) ?: false
            if (wall || rig.bDist[i] > BULLET_RANGE) {
                if (wall) spawnBurst(rig, rig.bx[i], rig.by[i], rig.bz[i], 0, SPARK_PIECES)   // sparks on the wall
                rig.bAlive[i] = false
            } else if (mt != null && mt.bulletStep(nx, rig.by[i], nz, 0, rig)) {
                rig.bAlive[i] = false            // hit a bot (blood is added by the match logic)
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

// kind 0 = orange sparks (wall / shield), kind 1 = blood (fighter). n = how many pieces fly out.
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

// Blood drops fall and, when they touch the floor, stay there as a flat dark spot for about 2 seconds
// (then they shrink away). Sparks just fly and fade.
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

        rig.fxVY[i] -= 9f * dt                      // pieces fall
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
