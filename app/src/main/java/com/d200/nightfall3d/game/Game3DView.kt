package com.d200.nightfall3d.game

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// =====================================================================
// Game3DView.kt  -  the 3D ENGINE (no buttons in here).
// It draws the arena + character + gun, moves him, plays the animations, shoots bullets and turns the camera.
// The HUD (HudScreen.kt) does not touch this file. It only writes into one GameInput object:
//   joyX / joyY      move stick (-1..1)
//   aimHeld/shooting/aimX/aimY   fire stick
//   punchTap = true  one punch (cycles punch_1 -> punch_2 -> punch_3)
//   jumpTap  = true  one jump
//   handMode = true  fists: gun hidden, no shooting
//   gunWanted = n    which gun is in the hand (index in GUNS)
// Drag on the open screen = turn camera. Two fingers = pinch zoom / twist.
//
// Needs in app/src/main/assets/ : arena_play.glb, arena_play.json, char_blue.glb
// Optional (gun in hand): mp5_gun.glb, ak47_gun.glb, ar_gun.glb  (the game still works without them)
// =====================================================================

// ---- Tuning numbers: change these if something looks wrong ----
private const val CHAR_HEIGHT = 1.8f      // character size in meters
private const val FACING_OFFSET = 0f      // if he walks sideways/backwards, try 90f, 180f or -90f
private const val WALK_SPEED = 2.2f
private const val TURN_SPEED = 540f       // degrees per second he turns toward where he walks
private const val AIM_TURN_SPEED = 1080f  // degrees per second he turns toward where he aims (faster, so the gun lines up quickly)
private const val ACCEL = 9f              // how fast he speeds up / slows down (meters per second, per second)
private const val BLEND_TIME = 0.25f      // seconds to blend between animations (idle/walk/run/shoot)
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
private const val AIM_DEADZONE = 0.15f    // aim stick: pushed less than this = no aim yet (he keeps his direction)
private const val FIRE_ZONE = 0.7f        // aim stick: pushed past this fraction of the radius = he shoots
private const val BACKPEDAL_DOT = -0.2f   // moving against his facing by this much = play the walk backward
private const val RAD_TO_DEG = 180f / PI.toFloat()
private const val DEG_TO_RAD = PI.toFloat() / 180f

// ---- Bullets ----
private const val BULLET_COUNT = 24
private const val BULLET_SPEED = 40f          // meters per second
private const val BULLET_RANGE = 50f          // bullet disappears after this distance
private const val FIRE_INTERVAL = 0.12f       // seconds between shots while the fire stick is held
private const val MUZZLE_FORWARD = 0.5f       // bullet starts this far in front of the HAND (about the gun length)
// Fallback only (used if the hand position cannot be read): start point measured from his body
private const val BULLET_HEIGHT = 1.15f
private const val BULLET_START_FORWARD = 0.9f
private const val BULLET_START_SIDE = 0.25f   // toward his right hand

// ---- Gun in the right hand ----
private const val RIGHT_HAND_JOINT = 14          // position of the right hand in the skeleton list

// ---- Punch and jump (clips live in the character GLB, each 0.5 s) ----
private const val PUNCH_LEN = 0.5f            // length of punch_1 / punch_2 / punch_3
private const val PUNCH_CHAIN_AT = 0.3f       // a new tap after this many seconds starts the next punch
private const val JUMP_CLIP = "jump_inplace"
private const val JUMP_LEN = 0.5f
private const val JUMP_TAKEOFF_T = 0.1406f    // clip time when the feet leave the ground
private const val JUMP_LAND_T = 0.3594f       // clip time when the feet touch down again
private const val JUMP_SPEED = 8f             // up speed at takeoff (m/s). Higher = higher jump
private const val GRAVITY = 24f               // pull down (m/s per second). Higher = faster, snappier jump

// Gun turn (degrees, X/Y/Z)
internal class GunTurn(val x: Float, val y: Float, val z: Float)

// One gun. pos/scale/grip/carry/aim are the fit numbers for THAT gun (from the gun fit viewer).
internal class GunDef(
    val name: String,
    val file: String,
    val pos: Position,
    val scale: Float,
    val grip: Position,        // moves the gun so the grip sits in the hand
    val carry: GunTurn,        // idle / walk / run
    val aim: Map<String, GunTurn>   // muzzle straight forward while shooting
)

// ADD NEW GUNS HERE (one entry each)
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
    // AK-47: fitted to the character's right hand and checked in all 6 animations (idle, walk, run, shoot, walk_shoot, run_shoot)
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
    // AR (M4 style): same method as the AK
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
    )
)

// ADD NEW ARENAS HERE (index 0 = "Zero Arena" = the default). The index comes from the Arena step of the 3D MODE screens.
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
    var y = 0f               // floor height under him (platform / ramp)
    var z = 0f
    var play: PlayArena? = null   // walls + ramp, filled when arena_play.json is read
    var camPitch = 0f        // degrees the camera is raised (drag up/down)
    var camYaw = 0f          // degrees, 0 = looking toward -Z
    var faceDeg = 0f
    var joyX = 0f            // move stick
    var joyY = 0f
    var shooting = false     // aim stick is pushed into the fire zone
    var aimHeld = false      // a finger is on the aim stick (aiming, maybe not shooting)
    var aimX = 0f            // fire stick direction (only used while shooting)
    var aimY = 0f
    var paused = false
    var handMode = false      // fists mode: gun hidden, no shooting, punch button active
    var gunWanted by mutableIntStateOf(0)   // which gun the HUD wants in the hand (index in GUNS)
    var zoom = 1f
    var active = 0
    var lastNanos = 0L
    var gunOwner = -1        // which character's hand the gun is attached to
    var gunLoaded = false    // a gun model is loaded and sits in the holder
    var gunDef: GunDef = GUNS[0]
    val attachFails = IntArray(3)   // gives up after a few tries (no endless retry / log spam)
    var fireTimer = 0f
    // where the next bullet leaves from (filled every frame while shooting)
    var mx = 0f
    var my = 0f
    var mz = 0f
    val hand = FloatArray(3)
    val bAlive = BooleanArray(BULLET_COUNT)
    val bx = FloatArray(BULLET_COUNT)
    val bz = FloatArray(BULLET_COUNT)
    val by = FloatArray(BULLET_COUNT)
    val bdx = FloatArray(BULLET_COUNT)
    val bdz = FloatArray(BULLET_COUNT)
    val bDist = FloatArray(BULLET_COUNT)
    // punch combo + jump
    val combo = PunchCombo()
    var punchClip = ""        // "" = not punching, otherwise punch_1 / punch_2 / punch_3
    var punchT = 0f           // seconds since this punch started
    var punchTap = false      // set by the PUNCH button, read once per frame
    var jumpTap = false       // set by the JUMP button, read once per frame
    var jumpState = 0         // 0 on the ground, 1 crouching before takeoff, 2 in the air, 3 landing
    var jumpClock = 0f
    var airClock = 0f
    var jumpClipT = 0f        // which moment of the jump clip to show
    var jumpH = 0f            // height above the floor
    var jumpV = 0f            // up speed
    val anim = Array(3) { AnimState() }
    var speed = 0f           // current speed (eases toward the target)
    var moveX = 0f           // last move direction (kept while slowing down)
    var moveZ = 0f
    var runMode = false      // run/walk switch with a small gap, so it does not flicker
}

// Where one character is in its animations (used for the blend)
internal class AnimState {
    var cur = -1
    var prev = -1
    var curTime = 0f
    var prevTime = 0f
    var fade = 1f
    var wantName = ""
    var baseTime = 0f         // time of the idle/walk/run layer under a punch
    var prevClamp = false     // the fading-out clip is a one-shot (punch / jump): hold its last pose, do not loop it
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

    val bullets = remember {
        val bulletMat = materialLoader.createColorInstance(Color(0xFFFFE066))
        List(BULLET_COUNT) {
            CubeNode(engine, size = Float3(0.06f, 0.06f, 0.4f), materialInstance = bulletMat)
                .apply { isVisible = false }
        }
    }

    val nodes = rememberNodes {
        add(gun)
        addAll(bullets)
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
            val gm = ModelNode(
                modelInstance = modelLoader.createModelInstance(def.file),
                autoAnimate = false
            )
            gm.position = def.grip
            gun.addChildNode(gm)
            gunModel = gm
            rig.gunDef = def
            rig.gunLoaded = true
            note = ""
        } catch (e: Throwable) {
            Log.e("Game3D", "Could not load gun", e)
            note = "Gun file missing (" + GUNS[i].file + ")"
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
            onFrame = { nanos -> stepGame(nanos, rig, models, cameraNode, engine, gun, bullets) }
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
                                rig.camYaw += (c.position.x - c.previousPosition.x) * CAM_DRAG_SPEED
                                rig.camPitch = (rig.camPitch + (c.position.y - c.previousPosition.y) * CAM_PITCH_SPEED)
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

private fun stepGame(
    nanos: Long,
    rig: GameInput,
    models: Array<ModelNode?>,
    cameraNode: io.github.sceneview.node.CameraNode,
    engine: Engine,
    gun: Node,
    bullets: List<CubeNode>
) {
    // Paused: freeze everything (the camera stays where it is)
    if (rig.paused) { rig.lastNanos = nanos; return }
    // fists mode: no aiming, no shooting
    if (rig.handMode) { rig.shooting = false; rig.aimHeld = false }

    val dt = if (rig.lastNanos == 0L) 0f else ((nanos - rig.lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
    rig.lastNanos = nanos

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
            // the clip's "in the air" part is stretched to match how long this jump really lasts
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

    // ---- Punch combo: each tap starts the next punch (right, left, low, right ...) ----
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
    // walk -> run switch has a gap, so it does not flicker at the edge
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

    // Speed eases up and down (no instant jump between stand / walk / run)
    // (the low punch roots him to the spot)
    val targetSpeed = if (!moving || rig.punchClip == "punch_3") 0f else if (rig.runMode) RUN_SPEED else WALK_SPEED
    val maxChange = ACCEL * dt
    rig.speed += (targetSpeed - rig.speed).coerceIn(-maxChange, maxChange)

    // Move, with sliding along walls (rules copied from the HTML)
    val play = rig.play
    if (play != null) {
        val nx = rig.x + rig.moveX * rig.speed * dt
        val nz = rig.z + rig.moveZ * rig.speed * dt
        play.tryMove(rig.x, rig.z, nx, nz)
        rig.x = play.outX
        rig.z = play.outZ
        // go up / down the ramp smoothly
        rig.y += (play.groundY(rig.x, rig.z) - rig.y) * min(1f, dt * 20f)
    }

    // Where the aim stick points (relative to the camera). He turns to it whenever a finger is on the stick
    // and it is pushed past the dead zone (shooting or not).
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

    // Turn smoothly: toward the aim while shooting, otherwise toward where he walks.
    // (standing still and not aiming: keeps his last direction)
    if (wantTurn) {
        // the model faces +X, so turn +X toward (tx, tz)
        val target = atan2(-tz, tx) * RAD_TO_DEG + FACING_OFFSET
        var diff = target - rig.faceDeg
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        val maxTurn = (if (aiming) AIM_TURN_SPEED else TURN_SPEED) * dt
        rig.faceDeg += diff.coerceIn(-maxTurn, maxTurn)
    }

    // The way he is facing right now (= the way the gun points). Bullets fly this way.
    val faceR = (rig.faceDeg - FACING_OFFSET) * DEG_TO_RAD
    val sx = cos(faceR)
    val sz = -sin(faceR)

    // Pick the animation from the real speed
    val base = if (rig.speed < 0.15f) "idle" else if (rig.speed > (WALK_SPEED + RUN_SPEED) / 2f) "run" else "walk"
    val shootWant = if (rig.shooting) (if (base == "idle") "shoot" else base + "_shoot") else base
    val want = when {
        rig.jumpState != 0 -> JUMP_CLIP
        rig.punchClip.isNotEmpty() -> rig.punchClip
        else -> shootWant
    }
    // jump and punch are played at an exact moment of the clip (not by the clock)
    val forceTime = if (rig.jumpState != 0) rig.jumpClipT else if (rig.punchClip.isNotEmpty()) rig.punchT else -1f
    // punch_1 / punch_2 only move the upper body, so the legs keep walking/running underneath
    val layerBase = if (want == "punch_1" || want == "punch_2") base else ""

    // Moving against the way he faces (walking backward while shooting forward): play the walk in reverse
    val moveDot = sx * rig.moveX + sz * rig.moveZ
    val timeScale = if (rig.speed > 0.15f && moveDot < BACKPEDAL_DOT) -1f else 1f

    // Only characters that are already loaded are touched
    for (i in models.indices) {
        val m = models[i] ?: continue
        val on = i == rig.active
        m.isVisible = on
        if (on) {
            m.position = Position(rig.x, rig.y + rig.jumpH, rig.z)
            m.rotation = Rotation(y = rig.faceDeg)
            driveAnimation(m, rig.anim[i], want, dt, timeScale, forceTime, layerBase)
        }
    }

    // Gun: attach to the active character's right hand (only after both are loaded),
    // turn it for the current animation
    val activeModel = models[rig.active]
    if (rig.gunLoaded && activeModel != null &&
        rig.gunOwner != rig.active && rig.attachFails[rig.active] < 10
    ) {
        if (attachGun(engine, gun, activeModel)) rig.gunOwner = rig.active
        else rig.attachFails[rig.active]++
    }
    // the gun is put away while he punches
    gun.isVisible = rig.gunLoaded && activeModel != null && rig.gunOwner == rig.active && rig.punchClip.isEmpty() && !rig.handMode
    val def = rig.gunDef
    val turn = def.aim[want] ?: def.carry
    gun.position = def.pos
    gun.quaternion = quatXYZ(turn.x, turn.y, turn.z)
    gun.scale = Float3(def.scale, def.scale, def.scale)

    // Where bullets leave: the real right hand position (after the animation was applied), pushed forward
    // along the gun direction. If the hand cannot be read, falls back to a point measured from his body.
    if (rig.shooting) {
        if (activeModel != null && rig.gunLoaded && handWorld(engine, activeModel, rig.hand)) {
            rig.mx = rig.hand[0] + sx * MUZZLE_FORWARD
            rig.my = rig.hand[1]
            rig.mz = rig.hand[2] + sz * MUZZLE_FORWARD
        } else {
            rig.mx = rig.x + sx * BULLET_START_FORWARD - sz * BULLET_START_SIDE
            rig.my = rig.y + BULLET_HEIGHT
            rig.mz = rig.z + sz * BULLET_START_FORWARD + sx * BULLET_START_SIDE
        }
    }
    updateBullets(dt, rig, bullets, sx, sz)

    // Chest-level follow camera (distance = pinch zoom, direction = drag)
    val dist = CAM_DIST * rig.zoom
    val pr = rig.camPitch * DEG_TO_RAD
    val back = dist * cos(pr)
    val camY = rig.y + rig.jumpH * 0.4f      // the camera follows a jump only a little (less shake)
    cameraNode.position = Position(rig.x - fx * back, camY + CAM_HEIGHT + dist * sin(pr), rig.z - fz * back)
    cameraNode.lookAt(Position(rig.x + fx * CAM_LOOK_AHEAD, camY + CAM_LOOK_Y, rig.z + fz * CAM_LOOK_AHEAD))
}

// ---------------- Animation with blending ----------------

private fun animIndex(animator: Animator, name: String): Int {
    for (i in 0 until animator.animationCount) {
        if (animator.getAnimationName(i) == name) return i
    }
    return -1
}

// Plays the wanted animation and blends from the old one (BLEND_TIME seconds).
// timeScale = -1 plays the cycle backward (used for walking backward while shooting forward).
// forceTime >= 0 shows that exact moment of the clip (punch / jump). layerBase = name of a clip (idle/walk/run) that is
// applied first, so a upper-body-only punch keeps the legs moving.
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
                // keep the same point in the step cycle (walk <-> run looks smooth)
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
        }
    }
    for (i in 0 until BULLET_COUNT) {
        if (!rig.bAlive[i]) { bullets[i].isVisible = false; continue }
        var left = BULLET_SPEED * dt
        while (left > 0f && rig.bAlive[i]) {
            val step = min(0.25f, left)          // small steps so thin walls can't be skipped
            val nx = rig.bx[i] + rig.bdx[i] * step
            val nz = rig.bz[i] + rig.bdz[i] * step
            if ((rig.play?.bulletHits(nx, rig.by[i], nz) ?: false) || rig.bDist[i] > BULLET_RANGE) {
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

// ---------------- Gun helpers ----------------

// Puts the gun holder under the right hand bone of this character.
// (If the build fails or the gun does not follow the hand, this is the function to check.)
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

// Reads where the right hand is in the world right now (x, y, z into out[0..2]).
// Filament stores the matrix column by column, so the position is at 12, 13, 14.
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

// Same turn order as the viewer (X, then Y, then Z), so the numbers match what you saw
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
