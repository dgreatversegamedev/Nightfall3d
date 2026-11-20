package com.d200.nightfall3d.game

import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// =====================================================================
// ShieldView.kt  -  draws the shield (NEW FILE, put it in the game folder).
// Moved here from Game3DView.kt and extended with 5 looks (see Shields.kt):
//   CLASSIC  blue hexagon bubble
//   VORTEX   purple smoke with spinning streaks (you can see your character inside)
//   TRIANGLE blue triangle plates
//   TECH     cyan hexagon dome + spinning circuit rings on the floor
//   SPARK    lavender smoke with flickering lightning
// Strength, grow-back time and the damage aura come from ShieldDef (Shields.kt).
// =====================================================================

internal const val SHIELD_R = 1.1f
internal const val SHIELD_CY = 0.9f
internal const val SHIELD_MAX_HITS = 25f

private const val SHIELD_EDGE_THICK = 0.034f
private const val SHIELD_FREQ = 3
private const val SHIELD_BREAK_TIME = 0.55f
private const val SHIELD_APPEAR_TIME = 0.45f
private const val SHIELD_HIT_TIME = 0.7f
private const val M_SHINE = 5
private const val M_HOT = 6
private const val M_AMBER = 7
private const val SHIELD_PUFFS = 12
private const val RING_SEGS = 24
private const val VORTEX_STRANDS = 6
private const val VORTEX_SEGS = 40
private const val SPARK_ARCS = 14
private const val SPARK_SEG = 14
private const val SV_RAD = 180f / PI.toFloat()

// ---------------- Shape: lines on a sphere ----------------
private class ShieldGeo(
    val count: Int,
    val mid: FloatArray,
    val dir: FloatArray,
    val quat: Array<Quaternion>,
    val lens: FloatArray,
    val band: IntArray
)

private val HEX_GEO: ShieldGeo by lazy { buildShieldGeo(false) }
private val TRI_GEO: ShieldGeo by lazy { buildShieldGeo(true) }

private fun quatFromZ(dx: Float, dy: Float, dz: Float): Quaternion {
    if (dz > 0.9999f) return Quaternion(0f, 0f, 0f, 1f)
    if (dz < -0.9999f) return Quaternion(1f, 0f, 0f, 0f)
    val w = 1f + dz
    val x = -dy
    val y = dx
    val n = sqrt(x * x + y * y + w * w)
    return Quaternion(x / n, y / n, 0f, w / n)
}

// tri = false: hexagon cells (+12 pentagons).  tri = true: the triangle edges of the sphere.
private fun buildShieldGeo(tri: Boolean): ShieldGeo {
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

    val mids = ArrayList<Float>(); val dirs = ArrayList<Float>(); val lens = ArrayList<Float>()
    val quats = ArrayList<Quaternion>(); val bands = ArrayList<Int>()
    fun addEdge(dx: Float, dy: Float, dz: Float, mx: Float, my: Float, mz: Float) {
        val d = sqrt(dx * dx + dy * dy + dz * dz)
        val ml = sqrt(mx * mx + my * my + mz * mz)
        val s = if (tri) 1f / ml else 1f
        mids.add(mx * s * SHIELD_R); mids.add(my * s * SHIELD_R); mids.add(mz * s * SHIELD_R)
        dirs.add(mx / ml); dirs.add(my / ml); dirs.add(mz / ml)
        lens.add(d * SHIELD_R * 1.1f)
        quats.add(quatFromZ(dx / d, dy / d, dz / d))
        bands.add((((my / ml) + 1f) / 2f * 5f).toInt().coerceIn(0, 4))
    }

    if (tri) {
        val seen = HashSet<Long>()
        for (t in tris) for (e in 0..2) {
            val a = t[e]; val b = t[(e + 1) % 3]
            val key = min(a, b).toLong() * 100000L + max(a, b).toLong()
            if (!seen.add(key)) continue
            val va = verts[a]; val vb = verts[b]
            addEdge(
                vb[0] - va[0], vb[1] - va[1], vb[2] - va[2],
                (va[0] + vb[0]) / 2f, (va[1] + vb[1]) / 2f, (va[2] + vb[2]) / 2f
            )
        }
    } else {
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
        for (pair in edgeFaces.values) {
            if (pair[1] < 0) continue
            val a = cen[pair[0]]; val b = cen[pair[1]]
            addEdge(
                b[0] - a[0], b[1] - a[1], b[2] - a[2],
                (a[0] + b[0]) / 2f, (a[1] + b[1]) / 2f, (a[2] + b[2]) / 2f
            )
        }
    }
    return ShieldGeo(quats.size, mids.toFloatArray(), dirs.toFloatArray(), quats.toTypedArray(), lens.toFloatArray(), bands.toIntArray())
}

// ---------------- Colours for each look (order = ShieldStyle) ----------------
private class StyleCols(
    val lines: LongArray,      // 0..4 gradient bottom to top, 5 shine, 6 hit glow, 7 amber (shield is weak)
    val body: Long, val bodyA: Float,
    val flash: Long,
    val glow: Long, val glowA: Float,
    val disc: Long, val discA: Float,
    val puff: Long, val puffA: Float
)

private val STYLE_COLS = listOf(
    // CLASSIC
    StyleCols(
        longArrayOf(0xFF2A7BFFL, 0xFF2FB0FFL, 0xFF3FD2FFL, 0xFF7FE8FFL, 0xFFB8F6FFL, 0xFFE8FDFFL, 0xFFFFFFFFL, 0xFFFFB347L),
        0xFF1E78FFL, 0.30f, 0xFF9FF2FFL, 0xFF4FD8FFL, 0.14f, 0xFF35C8FFL, 0.40f, 0xFF4FD8FFL, 0.10f
    ),
    // VORTEX
    StyleCols(
        longArrayOf(0xFF5B18C8L, 0xFF7A26E6L, 0xFF9B3DFFL, 0xFFC06BFFL, 0xFFE0A4FFL, 0xFFF7DEFFL, 0xFFFFFFFFL, 0xFFFFB347L),
        0xFF6A1FE0L, 0.20f, 0xFFE5A0FFL, 0xFFB050FFL, 0.14f, 0xFF9030FFL, 0.34f, 0xFF8A2FF0L, 0.12f
    ),
    // TRIANGLE
    StyleCols(
        longArrayOf(0xFF1B4FE8L, 0xFF2477FFL, 0xFF32A4FFL, 0xFF5CC8FFL, 0xFF9AE4FFL, 0xFFDDF6FFL, 0xFFFFFFFFL, 0xFFFFB347L),
        0xFF1A66FFL, 0.24f, 0xFF8FE6FFL, 0xFF3A9DFFL, 0.12f, 0xFF2A8CFFL, 0.38f, 0xFF3A9DFFL, 0.10f
    ),
    // TECH
    StyleCols(
        longArrayOf(0xFF0E93A6L, 0xFF14B4C8L, 0xFF2FD3E4L, 0xFF6BEAF4L, 0xFFAEF6FAL, 0xFFE3FDFEL, 0xFFFFFFFFL, 0xFFFFB347L),
        0xFF10AEC2L, 0.16f, 0xFFA6F8FFL, 0xFF3FE0F0L, 0.10f, 0xFF19D2E4L, 0.30f, 0xFF3FE0F0L, 0.10f
    ),
    // SPARK
    StyleCols(
        longArrayOf(0xFF7C74F0L, 0xFF9690FFL, 0xFFB4AFFFL, 0xFFD2CFFFL, 0xFFEAE8FFL, 0xFFF6F5FFL, 0xFFFFFFFFL, 0xFFFFB347L),
        0xFF8E86F2L, 0.13f, 0xFFE2DFFFL, 0xFFC8C3FFL, 0.10f, 0xFF9F97FFL, 0.28f, 0xFF6E6A9AL, 0.20f
    )
)

internal class StyleMats(
    val lines: List<MaterialInstance>,
    val fill: List<MaterialInstance>,      // 0 = body, 1 = hit flash, 2 = amber (weak)
    val glow: MaterialInstance,
    val disc: MaterialInstance,
    val puff: MaterialInstance
)

/** Every node of the shield. Game3DView builds it once and adds all() to the scene. */
internal class ShieldRig(
    val lines: List<CubeNode>,
    val body: SphereNode,
    val glow: SphereNode,
    val disc: CylinderNode,
    val puffs: List<SphereNode>,
    val ring: List<CubeNode>,
    val sets: List<StyleMats>
) {
    val cur = IntArray(lines.size) { -1 }
    var fillCur = -1
    var style = -1
    val arc = FloatArray(SPARK_ARCS * (SPARK_SEG + 1) * 3)
    var sparkT = 0f

    fun all(): List<Node> {
        val l = ArrayList<Node>()
        l.add(body)
        l.add(glow)
        l.add(disc)
        l.addAll(puffs)
        l.addAll(lines)
        l.addAll(ring)
        return l
    }
}

internal fun createShieldRig(engine: Engine, ml: MaterialLoader): ShieldRig {
    fun mk(c: Long, a: Float): MaterialInstance = ml.createColorInstance(Color(c).copy(alpha = a))
    val sets = STYLE_COLS.map { s ->
        StyleMats(
            lines = s.lines.map { mk(it, 1f) },
            fill = listOf(mk(s.body, s.bodyA), mk(s.flash, 0.55f), mk(0xFFFFA030L, 0.32f)),
            glow = mk(s.glow, s.glowA),
            disc = mk(s.disc, s.discA),
            puff = mk(s.puff, s.puffA)
        )
    }
    val first = sets[0]
    val geo = HEX_GEO
    val total = max(HEX_GEO.count, TRI_GEO.count)
    val lines = List(total) { i ->
        CubeNode(engine, size = Float3(SHIELD_EDGE_THICK, SHIELD_EDGE_THICK, 1f), materialInstance = first.lines[0])
            .apply {
                if (i < geo.count) quaternion = geo.quat[i]
                isVisible = false
            }
    }
    val body = SphereNode(engine, radius = 1f, materialInstance = first.fill[0]).apply { isVisible = false }
    val glow = SphereNode(engine, radius = 1f, materialInstance = first.glow).apply { isVisible = false }
    val disc = CylinderNode(engine, radius = SHIELD_R, height = 0.02f, materialInstance = first.disc).apply { isVisible = false }
    val puffs = List(SHIELD_PUFFS) {
        SphereNode(engine, radius = 1f, materialInstance = first.puff).apply { isVisible = false }
    }
    val ringMat = mk(0xFF35E8F5L, 1f)
    val ring = List(RING_SEGS * 2) {
        CubeNode(engine, size = Float3(0.34f, 0.03f, 0.07f), materialInstance = ringMat).apply { isVisible = false }
    }
    return ShieldRig(lines, body, glow, disc, puffs, ring, sets)
}

private fun hideShield(sh: ShieldRig) {
    for (n in sh.lines) if (n.isVisible) n.isVisible = false
    for (n in sh.puffs) if (n.isVisible) n.isVisible = false
    for (n in sh.ring) if (n.isVisible) n.isVisible = false
    if (sh.body.isVisible) sh.body.isVisible = false
    if (sh.glow.isVisible) sh.glow.isVisible = false
    if (sh.disc.isVisible) sh.disc.isVisible = false
}

// ---------------- Called every frame from Game3DView.stepGame ----------------
// Hit: the whole body flashes. After def.maxHits hits the shield shatters, then grows back after def.reviveSec.
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

    // new look chosen: swap the colours and the line shape
    val st = def.style.ordinal
    if (st >= sh.sets.size) return
    if (sh.style != st) {
        sh.style = st
        val s = sh.sets[st]
        java.util.Arrays.fill(sh.cur, -1)
        sh.fillCur = -1
        sh.glow.materialInstance = s.glow
        sh.disc.materialInstance = s.disc
        for (p in sh.puffs) p.materialInstance = s.puff
        val gg = if (def.style == ShieldStyle.TRIANGLE) TRI_GEO else HEX_GEO
        for (i in sh.lines.indices) if (i < gg.count) sh.lines[i].quaternion = gg.quat[i]
    }
    val set = sh.sets[st]

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
    val low = rig.shieldHp <= def.maxHits * 0.3f
    val scanY = ((t * (if (low) 0.8f else 0.45f)) % 2.6f) - 1.3f
    val breathe = 1f + 0.015f * sin(t * 2.2f)

    // --- body: translucent ball, soft outer glow, floor disc ---
    val grow = if (ease < 1f) 0.5f + 0.5f * ease else 1f
    val bodyR = SHIELD_R * 0.97f * breathe * grow
    if (breaking) {
        sh.body.isVisible = false
        sh.glow.isVisible = false
        sh.disc.isVisible = false
    } else {
        val want = if (hit && fade > 0.55f) 1 else if (low) 2 else 0
        if (sh.fillCur != want) {
            sh.fillCur = want
            sh.body.materialInstance = set.fill[want]
        }
        sh.body.position = Position(cx, cy, cz)
        sh.body.scale = Float3(bodyR, bodyR, bodyR)
        val glowR = bodyR * (1.06f + 0.015f * sin(t * 3f))
        sh.glow.position = Position(cx, cy, cz)
        sh.glow.scale = Float3(glowR, glowR, glowR)
        val dr = grow * (1f + 0.03f * sin(t * 2.6f))
        sh.disc.position = Position(cx, rig.y + 0.03f, cz)
        sh.disc.scale = Float3(dr, 1f, dr)
        sh.body.isVisible = true
        sh.glow.isVisible = true
        sh.disc.isVisible = rig.jumpH < 0.3f
    }

    // --- lines ---
    val procedural = def.style == ShieldStyle.VORTEX || def.style == ShieldStyle.SPARK
    if (procedural) {
        val kk = breathe * grow * (if (breaking) 1f + 1.8f * bp else 1f)
        val tm = max(0.001f, (if (ease < 1f) ease else 1f) * (1f - bp))
        val hot = breaking || (hit && fade > 0.35f)
        if (def.style == ShieldStyle.VORTEX) vortexLines(sh, set, cx, cy, cz, t, kk, tm, hot, low)
        else sparkLines(sh, set, cx, cy, cz, t, dt, kk, tm, hot, low)
    } else {
        val g = if (def.style == ShieldStyle.TRIANGLE) TRI_GEO else HEX_GEO
        for (i in sh.lines.indices) {
            val n = sh.lines[i]
            if (i >= g.count) {
                if (n.isVisible) n.isVisible = false
                continue
            }
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
            if (sh.cur[i] != m) {
                sh.cur[i] = m
                n.materialInstance = set.lines[m]
            }
            if (!n.isVisible) n.isVisible = true
        }
    }

    // --- extras: smoke puffs (Vortex, Spark) and floor rings (Tech) ---
    updatePuffs(sh.puffs, procedural && !breaking, def.style == ShieldStyle.VORTEX, cx, cy, cz, bodyR, t)
    updateTechRing(sh.ring, def.style == ShieldStyle.TECH && !breaking && rig.jumpH < 0.3f, cx, rig.y + 0.045f, cz, t, grow)
}

// Purple streaks that spin around the ball and flow from the floor to the top
private fun vortexLines(
    sh: ShieldRig, set: StyleMats, cx: Float, cy: Float, cz: Float,
    t: Float, k: Float, thickMul: Float, hot: Boolean, low: Boolean
) {
    val twoPi = 2f * PI.toFloat()
    val spin = t * 2.2f
    val total = VORTEX_STRANDS * VORTEX_SEGS
    val rr = SHIELD_R * k
    for (i in sh.lines.indices) {
        val n = sh.lines[i]
        if (i >= total) {
            if (n.isVisible) n.isVisible = false
            continue
        }
        val s = i / VORTEX_SEGS
        val j = i % VORTEX_SEGS
        val u0 = j.toFloat() / VORTEX_SEGS
        val u1 = (j + 1f) / VORTEX_SEGS
        val ph = (((u0 - t * 0.35f + s * 0.17f) % 1f) + 1f) % 1f
        if (ph > 0.7f) {
            if (n.isVisible) n.isVisible = false
            continue
        }
        val prof = sin(ph / 0.7f * PI.toFloat())
        val a0 = s * twoPi / VORTEX_STRANDS + u0 * 6.9f + spin
        val a1 = s * twoPi / VORTEX_STRANDS + u1 * 6.9f + spin
        val l0 = -1.25f + 2.5f * u0
        val l1 = -1.25f + 2.5f * u1
        val c0 = cos(l0)
        val c1 = cos(l1)
        val x0 = c0 * cos(a0); val y0 = sin(l0); val z0 = c0 * sin(a0)
        val x1 = c1 * cos(a1); val y1 = sin(l1); val z1 = c1 * sin(a1)
        val dx = x1 - x0
        val dy = y1 - y0
        val dz = z1 - z0
        val d = max(sqrt(dx * dx + dy * dy + dz * dz), 0.0001f)
        val mx = (x0 + x1) / 2f
        val my = (y0 + y1) / 2f
        val mz = (z0 + z1) / 2f
        val ml = max(sqrt(mx * mx + my * my + mz * mz), 0.0001f)
        n.position = Position(cx + mx / ml * rr, cy + my / ml * rr, cz + mz / ml * rr)
        n.quaternion = quatFromZ(dx / d, dy / d, dz / d)
        val th = (0.5f + 1.7f * prof) * thickMul
        n.scale = Float3(th, th, d * rr * 1.2f)
        val band = (u0 * 5f).toInt().coerceIn(0, 4)
        val m = if (hot) M_HOT else if (low) M_AMBER else if (prof > 0.8f) M_SHINE else band
        if (sh.cur[i] != m) {
            sh.cur[i] = m
            n.materialInstance = set.lines[m]
        }
        if (!n.isVisible) n.isVisible = true
    }
}

// New random lightning paths along the ball surface
private fun genSparkArcs(sh: ShieldRig) {
    val a = sh.arc
    for (k in 0 until SPARK_ARCS) {
        var px = Random.nextFloat() * 2f - 1f
        var py = Random.nextFloat() * 2f - 1f
        var pz = Random.nextFloat() * 2f - 1f
        var l = sqrt(px * px + py * py + pz * pz)
        if (l < 0.01f) { px = 0f; py = 1f; pz = 0f; l = 1f }
        px /= l; py /= l; pz /= l
        var tx = Random.nextFloat() * 2f - 1f
        var ty = Random.nextFloat() * 2f - 1f
        var tz = Random.nextFloat() * 2f - 1f
        for (j in 0..SPARK_SEG) {
            val o = (k * (SPARK_SEG + 1) + j) * 3
            a[o] = px
            a[o + 1] = py
            a[o + 2] = pz
            tx = tx * 0.6f + (Random.nextFloat() * 2f - 1f) * 0.9f
            ty = ty * 0.6f + (Random.nextFloat() * 2f - 1f) * 0.9f
            tz = tz * 0.6f + (Random.nextFloat() * 2f - 1f) * 0.9f
            val dp = tx * px + ty * py + tz * pz
            tx -= px * dp; ty -= py * dp; tz -= pz * dp
            val tl = max(sqrt(tx * tx + ty * ty + tz * tz), 0.001f)
            tx /= tl; ty /= tl; tz /= tl
            val c = cos(0.17f)
            val s = sin(0.17f)
            px = px * c + tx * s
            py = py * c + ty * s
            pz = pz * c + tz * s
        }
    }
}

// Flickering white lightning that jumps along the surface
private fun sparkLines(
    sh: ShieldRig, set: StyleMats, cx: Float, cy: Float, cz: Float,
    t: Float, dt: Float, k: Float, thickMul: Float, hot: Boolean, low: Boolean
) {
    sh.sparkT -= dt
    if (sh.sparkT <= 0f) {
        genSparkArcs(sh)
        sh.sparkT = 0.06f + Random.nextFloat() * 0.07f
    }
    val total = SPARK_ARCS * SPARK_SEG
    val rr = SHIELD_R * k
    val arr = sh.arc
    for (i in sh.lines.indices) {
        val n = sh.lines[i]
        if (i >= total) {
            if (n.isVisible) n.isVisible = false
            continue
        }
        val a = i / SPARK_SEG
        val j = i % SPARK_SEG
        val on = ((t * 14f).toInt() + a * 5) % 4 != 0
        if (!on) {
            if (n.isVisible) n.isVisible = false
            continue
        }
        val o0 = (a * (SPARK_SEG + 1) + j) * 3
        val o1 = o0 + 3
        val dx = arr[o1] - arr[o0]
        val dy = arr[o1 + 1] - arr[o0 + 1]
        val dz = arr[o1 + 2] - arr[o0 + 2]
        val d = max(sqrt(dx * dx + dy * dy + dz * dz), 0.0001f)
        val mx = (arr[o0] + arr[o1]) / 2f
        val my = (arr[o0 + 1] + arr[o1 + 1]) / 2f
        val mz = (arr[o0 + 2] + arr[o1 + 2]) / 2f
        val ml = max(sqrt(mx * mx + my * my + mz * mz), 0.0001f)
        n.position = Position(cx + mx / ml * rr, cy + my / ml * rr, cz + mz / ml * rr)
        n.quaternion = quatFromZ(dx / d, dy / d, dz / d)
        val th = (0.9f + 0.8f * (1f - j.toFloat() / SPARK_SEG * 0.6f)) * thickMul
        n.scale = Float3(th, th, d * rr * 1.15f)
        val m = if (hot) M_HOT else if (low) M_AMBER else if (j % 2 == 0) M_HOT else 4
        if (sh.cur[i] != m) {
            sh.cur[i] = m
            n.materialInstance = set.lines[m]
        }
        if (!n.isVisible) n.isVisible = true
    }
}

// Soft smoke balls that drift around the shield
private fun updatePuffs(
    puffs: List<SphereNode>, show: Boolean, vortex: Boolean,
    cx: Float, cy: Float, cz: Float, r: Float, t: Float
) {
    for (p in puffs.indices) {
        val n = puffs[p]
        if (!show) {
            if (n.isVisible) n.isVisible = false
            continue
        }
        val f = p.toFloat()
        val spin = if (vortex) 2.6f else 0.7f
        val ang = t * spin * (if (p % 2 == 0) 1f else 0.7f) + f * 2.4f
        val elev = sin(t * 0.8f + f * 1.7f) * (if (vortex) 0.9f else 1.1f)
        val rr = r * (0.82f + 0.12f * sin(t * 1.3f + f))
        val ce = cos(elev)
        n.position = Position(cx + cos(ang) * ce * rr, cy + sin(elev) * rr * 0.9f, cz + sin(ang) * ce * rr)
        val s = (if (vortex) 0.30f else 0.42f) * (0.8f + 0.3f * sin(t * 1.9f + f * 2.1f))
        n.scale = Float3(s, s, s)
        if (!n.isVisible) n.isVisible = true
    }
}

// Two rings of dashes on the floor, spinning in opposite directions
private fun updateTechRing(
    ring: List<CubeNode>, show: Boolean, cx: Float, fy: Float, cz: Float, t: Float, grow: Float
) {
    val step = 2f * PI.toFloat() / RING_SEGS
    for (i in ring.indices) {
        val n = ring[i]
        if (!show) {
            if (n.isVisible) n.isVisible = false
            continue
        }
        val outer = i >= RING_SEGS
        val j = if (outer) i - RING_SEGS else i
        val on = if (outer) (j % 3 != 0) else (j % 4 != 3)
        if (!on) {
            if (n.isVisible) n.isVisible = false
            continue
        }
        val a = j * step + (if (outer) -t * 0.9f else t * 1.4f)
        val rad = (if (outer) SHIELD_R * 1.55f else SHIELD_R * 1.25f) * grow
        n.position = Position(cx + cos(a) * rad, fy, cz + sin(a) * rad)
        n.rotation = Rotation(y = -(a * SV_RAD + 90f))
        n.scale = Float3(if (outer) 1.1f else 1f, 1f, 1f)
        if (!n.isVisible) n.isVisible = true
    }
}
