package com.d200.nightfall3d.game

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin

// =====================================================================
// ShieldView.kt  -  draws the shield from a GLB file (replaces the old Kotlin-drawn version).
//
// The shield you equip in Weapons > SHIELDS (ShieldLoadout.selected) has a file name in Shields.kt (glb = ...).
// That file is loaded from app/src/main/assets/ and placed around the player.
// If the GLB has animations inside, they play by themselves.
//
// Still done here (same as before): hit pulse, 25-hit style break, grow-back timer, appear effect,
// blinking when the shield is weak. The numbers come from ShieldDef (Shields.kt).
//
// If a GLB is missing or broken, a plain blue ball in the shield's colour is shown instead (no crash).
//
// CHANGES IN THIS VERSION (SHIELD FIT)
//  Two ways to place a shield, chosen per shield in Shields.kt (floorAnchored):
//   floorAnchored = true  : the GLB keeps its own size and its floor (origin) sits on the ground under the player.
//                           ShieldDef.modelScale makes it bigger or smaller. Used by Classic Hex (measured from its file).
//   floorAnchored = false : the old way (TUNING below). Shields not measured yet still use it.
//  The shield now also grows and shrinks from the floor, not from his chest.
//
// TUNING (old way only): SHIELD_FIT makes every old-way shield bigger or smaller. The GLB is scaled so its biggest
// side is SHIELD_R * 2 * SHIELD_FIT meters wide, and it is centered on the player's chest.
// =====================================================================

internal const val SHIELD_R = 1.1f
internal const val SHIELD_CY = 0.9f
internal const val SHIELD_MAX_HITS = 25f

private const val SHIELD_FIT = 1.0f
private const val SHIELD_BREAK_TIME = 0.55f
private const val SHIELD_APPEAR_TIME = 0.45f
private const val SHIELD_HIT_TIME = 0.7f

/** The shield nodes. Game3DView builds it once and adds all() to the scene. */
internal class ShieldRig(
    val root: Node,
    val fallback: SphereNode
) {
    /** Which shield the arena should have loaded (index in SHIELDS). Game3DView reloads the GLB when this changes. */
    var wantIdx by mutableIntStateOf(ShieldLoadout.selected)

    var model: ModelNode? = null
    var loadedIdx = -1
    var failed = false

    fun all(): List<Node> = listOf(root, fallback)
}

internal fun createShieldRig(engine: Engine, ml: MaterialLoader): ShieldRig {
    val root = Node(engine).apply { isVisible = false }
    val mat = ml.createColorInstance(Color(ShieldLoadout.current().accent).copy(alpha = 0.30f))
    val fb = SphereNode(engine, radius = 1f, materialInstance = mat).apply { isVisible = false }
    return ShieldRig(root, fb)
}

/** Loads the GLB of shield number `index` and puts it inside the shield root node. Safe to call again. */
internal fun loadShieldModel(sh: ShieldRig, loader: ModelLoader, index: Int) {
    val i = index.coerceIn(0, SHIELDS.lastIndex)
    if (sh.loadedIdx == i && sh.model != null) return
    val def = SHIELDS[i]
    try {
        sh.model?.let {
            sh.root.removeChildNode(it)
            it.destroy()
        }
        sh.model = null
        sh.loadedIdx = -1
        val m = if (def.floorAnchored) {
            // own size, own origin (the floor): no scaling and no centering here
            ModelNode(
                modelInstance = loader.createModelInstance(def.glb),
                autoAnimate = true
            )
        } else {
            ModelNode(
                modelInstance = loader.createModelInstance(def.glb),
                autoAnimate = true,
                scaleToUnits = SHIELD_R * 2f * SHIELD_FIT,
                centerOrigin = Position(0f, 0f, 0f)
            )
        }
        sh.root.addChildNode(m)
        sh.model = m
        sh.loadedIdx = i
        sh.failed = false
    } catch (e: Throwable) {
        Log.e("Game3D", "Could not load shield file " + def.glb, e)
        sh.model = null
        sh.loadedIdx = -1
        sh.failed = true
    }
}

private fun hideShield(sh: ShieldRig) {
    if (sh.root.isVisible) sh.root.isVisible = false
    if (sh.fallback.isVisible) sh.fallback.isVisible = false
}

// ---------------- Called every frame from Game3DView.stepGame ----------------
// Hit: the shield pulses. After def.maxHits hits it shatters, then grows back after def.reviveSec.
internal fun updateShield(rig: GameInput, sh: ShieldRig, nanos: Long, dt: Float) {
    val dead = rig.match?.playerDead == true
    val sel = ShieldLoadout.selected.coerceIn(0, SHIELDS.lastIndex)
    val def = SHIELDS[sel]
    if (rig.shieldIdx != sel) {              // a different shield was chosen: start fresh
        rig.shieldIdx = sel
        rig.shieldHp = def.maxHits
        rig.shieldCd = 0f
        rig.shieldCdSec = 0
        rig.shieldBreakT = 0f
        sh.wantIdx = sel                     // Game3DView loads the new GLB
    }

    if (rig.shieldCd > 0f) {
        rig.shieldCd = max(0f, rig.shieldCd - dt)
        if (rig.shieldCd <= 0f) {
            rig.shieldHp = def.maxHits
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
            rig.shieldCd = def.reviveSec
            rig.shieldCdSec = def.reviveSec.toInt()
            rig.shieldBreakT = SHIELD_BREAK_TIME
        }
    }

    val breaking = rig.shieldBreakT > 0f
    if (dead || (!rig.shieldOn && !breaking)) {
        hideShield(sh)
        rig.shieldHitT = 0f
        rig.shieldWasOn = false
        return
    }
    if (rig.shieldOn && !rig.shieldWasOn && rig.shieldAppearT <= 0f) rig.shieldAppearT = SHIELD_APPEAR_TIME
    rig.shieldWasOn = rig.shieldOn

    val t = nanos / 1_000_000_000f
    if (rig.shieldHitT > 0f) rig.shieldHitT = max(0f, rig.shieldHitT - dt)
    if (rig.shieldAppearT > 0f) rig.shieldAppearT = max(0f, rig.shieldAppearT - dt)

    val fade = rig.shieldHitT / SHIELD_HIT_TIME
    val hit = fade > 0f && !breaking
    val ap = 1f - rig.shieldAppearT / SHIELD_APPEAR_TIME
    val ease = ap * ap * (3f - 2f * ap)
    val bp = if (breaking) 1f - rig.shieldBreakT / SHIELD_BREAK_TIME else 0f
    val low = rig.shieldHp <= def.maxHits * 0.3f

    // size: slow breathing, grows in when it appears, pulses when hit, swells and vanishes when it breaks
    var k = 1f + 0.015f * sin(t * 2.2f)
    if (ease < 1f) k *= 0.5f + 0.5f * ease
    if (hit) k += 0.07f * fade * sin((1f - fade) * 20f)
    if (breaking) k *= (1f + 0.9f * bp) * (1f - bp)
    k = max(k, 0.001f)

    // weak shield blinks
    val blinkHide = low && !breaking && ((t * 6f).toInt() % 5 == 0)

    val cx = rig.x
    val feetY = rig.y + rig.jumpH
    val cy = feetY + SHIELD_CY
    val cz = rig.z

    val modelReady = sh.model != null && sh.loadedIdx == sel
    val showModel = modelReady && !blinkHide
    val showFallback = sh.failed && !modelReady && !blinkHide

    if (showModel) {
        if (def.floorAnchored) {
            // the file's floor is on the ground under him, and it grows from the floor
            val s = k * def.modelScale
            sh.root.position = Position(cx, feetY, cz)
            sh.root.scale = Float3(s, s, s)
        } else {
            sh.root.position = Position(cx, cy, cz)
            sh.root.scale = Float3(k, k, k)
        }
    }
    if (showFallback) {
        val r = SHIELD_R * 0.97f * k
        sh.fallback.position = Position(cx, cy, cz)
        sh.fallback.scale = Float3(r, r, r)
    }
    if (sh.root.isVisible != showModel) sh.root.isVisible = showModel
    if (sh.fallback.isVisible != showFallback) sh.fallback.isVisible = showFallback
}
