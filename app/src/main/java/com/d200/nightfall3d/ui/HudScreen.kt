package com.d200.nightfall3d.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.ui.theme.*
import kotlin.math.roundToInt

// In-game HUD built from the reference image (1672 x 941).
// All icons are drawn in code (HudIcons.kt). No emojis, no stick figures, no image files for buttons.
//
// One size system, so nothing looks random:
//   BIG   112  shield, jump, crouch, prone, sprint
//   MID    88  backpack, medkit, grenade
//   SMALL  80  symmetry, scope, sentry turret
//   TOP    58  mic, speaker, emoji, chat
//   sticks 220 (walk) and 200 (fire ring)
// One colour system: dark navy glass, steel ring when idle, Nightfall orange when active.
//
// The layout fills the whole screen: left items hug the left edge, right items hug the right edge.

private val Glass = Color(0xE00A1230)
private val GlassSoft = Color(0xB30A1230)
private val Orange = Color(0xFFFF8A00)
private val OrangeDark = Color(0xFFC96A00)
private val Steel = Color(0xFF2D8CFF)
private val RingIdle = Color(0x669AB4E8)
private val CutColor = Color(0xFF0A1230)

private const val BIG = 112f
private const val MID = 88f
private const val SMALL = 80f
private const val TOP = 58f

private fun isMelee(cat: String) = cat.equals("melee", ignoreCase = true)

/** Round button with a drawn icon. */
@Composable
private fun GlyphButton(
    k: Float,
    modifier: Modifier,
    d: Float,
    glyph: Glyph,
    iconFrac: Float = 0.56f,
    ring: Color = RingIdle,
    ringW: Float = 2f,
    fill: Color = GlassSoft,
    tint: Color = Color.White,
    onClick: () -> Unit = {}
) {
    Box(
        modifier
            .sized(k, d, d)
            .clip(CircleShape)
            .background(fill)
            .border((ringW * k).dp, ring, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(glyph, Modifier.size((d * iconFrac * k).dp), main = tint, cut = CutColor)
    }
}

/** Round button for the drawn scope / turret icons from Theme.kt. */
@Composable
private fun CodeButton(
    k: Float,
    modifier: Modifier,
    d: Float,
    kind: NfIconKind,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier
            .sized(k, d, d)
            .clip(CircleShape)
            .background(if (active) Orange else GlassSoft)
            .border((2 * k).dp, if (active) OrangeDark else RingIdle, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        NfIcon(
            kind,
            Modifier.size((d * 0.54f * k).dp),
            if (active) Color(0xFF1A0E00) else Color.White
        )
    }
}

/**
 * Draggable stick. Reports x/y in -1..1 through [onChange].
 * Left stick walks. Right stick (the bullet ring) aims and shoots.
 */
@Composable
private fun Stick(
    k: Float,
    sizePx: Float,
    fire: Boolean,
    modifier: Modifier,
    onChange: (Float, Float) -> Unit = { _, _ -> }
) {
    var off by remember { mutableStateOf(Offset.Zero) }
    val maxR = with(LocalDensity.current) { (sizePx * k).dp.toPx() * 0.30f }
    Box(
        modifier
            .sized(k, sizePx, sizePx)
            .clip(CircleShape)
            .background(Color(0x33FFFFFF))
            .border((if (fire) 4f else 2f).times(k).dp, if (fire) OrangeDark else Color(0x55FFFFFF), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { off = Offset.Zero },
                    onDragEnd = { off = Offset.Zero; onChange(0f, 0f) },
                    onDragCancel = { off = Offset.Zero; onChange(0f, 0f) }
                ) { change, drag ->
                    change.consume()
                    var n = off + drag
                    val d = n.getDistance()
                    if (d > maxR) n = n * (maxR / d)
                    off = n
                    onChange(n.x / maxR, n.y / maxR)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (fire) {
            Box(
                Modifier
                    .fillMaxSize(0.86f)
                    .clip(CircleShape)
                    .background(Color(0xEB0A1020))
                    .border((3 * k).dp, Color(0x55FFFFFF), CircleShape)
            )
            GlyphIcon(
                Glyph.BULLET,
                Modifier
                    .offset { IntOffset(off.x.roundToInt(), off.y.roundToInt()) }
                    .sized(k, 104f, 104f),
                cut = Color(0xFF0A1020)
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension / 2f
                val w = Color(0x99FFFFFF)
                drawCircle(Color(0x33FFFFFF), r * 0.62f, c, style = Stroke(2f))
                val a = r * 0.80f
                val s = r * 0.08f
                fun chevron(p0: Offset, p1: Offset, p2: Offset) {
                    val path = Path().apply {
                        moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y)
                    }
                    drawPath(path, w, style = Stroke(3f))
                }
                chevron(Offset(c.x - s, c.y - a + s), Offset(c.x, c.y - a), Offset(c.x + s, c.y - a + s))
                chevron(Offset(c.x - s, c.y + a - s), Offset(c.x, c.y + a), Offset(c.x + s, c.y + a - s))
                chevron(Offset(c.x - a + s, c.y - s), Offset(c.x - a, c.y), Offset(c.x - a + s, c.y + s))
                chevron(Offset(c.x + a - s, c.y - s), Offset(c.x + a, c.y), Offset(c.x + a - s, c.y + s))
            }
            Box(
                Modifier
                    .offset { IntOffset(off.x.roundToInt(), off.y.roundToInt()) }
                    .sized(k, 72f, 72f)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFF4F6FA), Color(0xFFB4BAC8))))
                    .border((4 * k).dp, Color(0x66202838), CircleShape)
            )
        }
    }
}

@Composable
private fun SelRow(
    k: Float,
    label: String,
    glyph: Glyph,
    selected: Boolean,
    chevron: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape((4 * k).dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height((60 * k).dp)
            .background(if (selected) Color(0x33FF8A00) else Color.Transparent, shape)
            .then(if (selected) Modifier.border((2 * k).dp, Orange, shape) else Modifier)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.padding(start = (14 * k).dp).sized(k, 46f, 36f),
            contentAlignment = Alignment.Center
        ) {
            val wide = glyph == Glyph.RIFLE
            GlyphIcon(
                glyph,
                Modifier.sized(k, if (wide) 46f else 34f, if (wide) 18f else 34f),
                main = if (selected) Orange else Color.White,
                accent = if (selected) Color.White else Orange,
                cut = if (selected) Color(0xFF2A1A0C) else CutColor
            )
        }
        Spacer(Modifier.width((12 * k).dp))
        NfText(
            label,
            Modifier.weight(1f),
            size = fsz(k, 27f),
            bold = true,
            color = if (selected) Orange else Color.White
        )
        if (chevron) {
            GlyphIcon(
                Glyph.CHEVRON,
                Modifier.padding(end = (12 * k).dp).size((22 * k).dp),
                main = if (selected) Orange else Color(0x88FFFFFF)
            )
        }
    }
}

/** Small dark pill with a count, sits on the corner of a round button. */
@Composable
private fun CountBadge(k: Float, modifier: Modifier, text: String) {
    Box(
        modifier
            .sized(k, 30f, 28f)
            .clip(RoundedCornerShape((6 * k).dp))
            .background(Color(0xF00A1230))
            .border((1.5f * k).dp, RingIdle, RoundedCornerShape((6 * k).dp)),
        contentAlignment = Alignment.Center
    ) { NfText(text, size = fsz(k, 22f), bold = true, align = TextAlign.Center) }
}

@Composable
fun HudScreen(api: GameApi, onExit: () -> Unit) {
    val p = api.profile()
    val owned = api.weapons().filter { it.owned && !it.locked }
    val guns = owned.filter { !isMelee(it.category) }.take(6)
    val melees = owned.filter { isMelee(it.category) }.take(6)

    var handMode by remember { mutableStateOf(false) }
    var gunIdx by remember { mutableStateOf(0) }
    var meleeIdx by remember { mutableStateOf(-1) } // -1 = bare fists
    var menuOpen by remember { mutableStateOf(false) }
    var turretOn by remember { mutableStateOf(false) }
    var shieldOn by remember { mutableStateOf(false) }
    var scopeOn by remember { mutableStateOf(false) }
    var ducking by remember { mutableStateOf(false) }
    var symmetryOn by remember { mutableStateOf(false) }

    val options: List<String> =
        if (handMode) listOf("Fists") + melees.map { it.name } else guns.map { it.name }
    val selected = if (handMode) meleeIdx + 1 else gunIdx
    val currentName = options.getOrNull(selected) ?: "No weapon"

    // The squad list is not shown here. It will appear when real players are in the match.

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05070F))
            .windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        val landscape = maxWidth >= maxHeight
        val k = if (landscape) {
            minOf(maxWidth.value / REF_W, maxHeight.value / REF_H)
        } else {
            minOf(maxWidth.value, maxHeight.value) / 1100f
        }

        // The frame fills the whole screen, so nothing is cut off and nothing floats in the middle.
        Box(Modifier.fillMaxSize()) {
            // ---------------- scene (placeholder arena until the 3D view is wired in)
            if (drawableId("hud_bg") != 0) {
                ArtImage("hud_bg", Modifier.fillMaxSize(), ContentScale.Crop)
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFF3B4C73), Color(0xFF1C2238))))
                )
                CharacterSilhouette(Modifier.align(Alignment.Center).fillMaxHeight(0.62f).width(160.dp))
            }

            // crosshair
            Canvas(Modifier.align(Alignment.Center).sized(k, 70f, 70f)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension * 0.30f
                val w = Color(0xCCFFFFFF)
                drawCircle(w, r, c, style = Stroke(2.5f))
                drawLine(w, Offset(c.x, c.y - r - 8f), Offset(c.x, c.y - r + 6f), 2.5f)
                drawLine(w, Offset(c.x, c.y + r - 6f), Offset(c.x, c.y + r + 8f), 2.5f)
                drawLine(w, Offset(c.x - r - 8f, c.y), Offset(c.x - r + 6f, c.y), 2.5f)
                drawLine(w, Offset(c.x + r - 6f, c.y), Offset(c.x + r + 8f, c.y), 2.5f)
                drawCircle(Color(0xFFFF4D5E), 3f, c)
            }

            // scope overlay (guns only)
            if (scopeOn && !handMode) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.height * 0.32f
                    drawCircle(Color(0x66FFFFFF), r, c, style = Stroke(2f))
                    drawLine(Color(0x88FFFFFF), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.5f)
                    drawLine(Color(0x88FFFFFF), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.5f)
                }
            }

            // ---------------- top left: minimap, voice / emoji / chat
            val mapShape = RoundedCornerShape((8 * k).dp)
            Box(
                atTL(k, 12f, 12f)
                    .sized(k, 232f, 210f)
                    .clip(mapShape)
                    .border((2 * k).dp, Orange, mapShape)
            ) { ArtImage("hud_minimap", Modifier.fillMaxSize(), ContentScale.Crop) }

            GlyphButton(k, atTL(k, 260f, 9f), TOP, Glyph.MIC_OFF, 0.50f)
            GlyphButton(k, atTL(k, 260f, 84f), TOP, Glyph.SPK_OFF, 0.50f)
            GlyphButton(k, atTL(k, 343f, 84f), TOP, Glyph.EMOJI, 0.52f)
            GlyphButton(k, atTL(k, 260f, 161f), TOP, Glyph.CHAT, 0.52f)

            // ---------------- top right: currency (landscape), players, kills, settings
            // All four boxes use the same dark glass. Only the small icon carries colour.
            val infoShape = RoundedCornerShape((5 * k).dp)
            if (landscape) {
                Row(
                    edgeTR(k, 444f, 10f)
                        .height((46 * k).dp)
                        .clip(infoShape)
                        .background(Glass)
                        .border((1.5f * k).dp, Color(0x44FFFFFF), infoShape)
                        .padding(horizontal = (10 * k).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphIcon(Glyph.COIN, Modifier.sized(k, 34f, 34f))
                    Spacer(Modifier.width((8 * k).dp))
                    NfText(formatNum(p.coins) + " +", size = fsz(k, 26f), bold = true)
                }
                Row(
                    edgeTR(k, 297f, 10f)
                        .height((46 * k).dp)
                        .clip(infoShape)
                        .background(Glass)
                        .border((1.5f * k).dp, Color(0x44FFFFFF), infoShape)
                        .padding(horizontal = (10 * k).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphIcon(Glyph.GEM, Modifier.sized(k, 34f, 34f))
                    Spacer(Modifier.width((8 * k).dp))
                    NfText(formatNum(p.diamonds) + " +", size = fsz(k, 26f), bold = true)
                }
            }
            Row(
                edgeTR(k, 172f, 10f)
                    .height((46 * k).dp)
                    .clip(infoShape)
                    .background(Glass)
                    .border((1.5f * k).dp, Color(0x44FFFFFF), infoShape)
                    .padding(horizontal = (12 * k).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlyphIcon(Glyph.PLAYERS, Modifier.sized(k, 32f, 32f), main = Color(0xFF35D3FF))
                Spacer(Modifier.width((8 * k).dp))
                NfText("32", size = fsz(k, 27f), bold = true)
            }
            Row(
                edgeTR(k, 60f, 10f)
                    .height((46 * k).dp)
                    .clip(infoShape)
                    .background(Glass)
                    .border((1.5f * k).dp, Color(0x44FFFFFF), infoShape)
                    .padding(horizontal = (14 * k).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlyphIcon(Glyph.SKULL, Modifier.sized(k, 32f, 32f), main = Color(0xFFE5483C))
                Spacer(Modifier.width((10 * k).dp))
                NfText("4", size = fsz(k, 27f), bold = true)
            }
            // settings gear: leaves the match
            GlyphIcon(
                Glyph.GEAR,
                edgeTR(k, 6f, 8f).sized(k, 46f, 46f).clickable { onExit() }
            )

            // ---------------- weapon panel + slots + Switch + Gun / Hand Fight / Combat
            val panelShape = CutCornerShape(topEnd = (14 * k).dp)
            Box(
                atTR(k, 1383f, 65f, 275f)
                    .sized(k, 275f, 103f)
                    .clip(panelShape)
                    .background(Glass)
                    .border((2 * k).dp, Orange, panelShape)
                    .clickable { menuOpen = !menuOpen }
            ) {
                Column(Modifier.align(Alignment.TopStart).padding(start = (14 * k).dp, top = (6 * k).dp)) {
                    if (handMode) {
                        NfText("MELEE", size = fsz(k, 36f), bold = true)
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            NfText("30", size = fsz(k, 44f), bold = true)
                            NfText("/120", size = fsz(k, 22f), color = NfColors.TextDim)
                        }
                    }
                    NfText(currentName, size = fsz(k, 21f), maxLines = 1)
                }
                GlyphIcon(
                    if (handMode) Glyph.FIST else Glyph.RIFLE,
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = (14 * k).dp)
                        .let { if (handMode) it.sized(k, 56f, 56f) else it.sized(k, 120f, 48f) },
                    cut = Color(0xFF0A1230)
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = (14 * k).dp, bottom = (8 * k).dp)
                        .width((230 * k).dp)
                        .height((3 * k).dp)
                        .background(Color(0x99FFFFFF))
                )
            }

            val slotShape = RoundedCornerShape((4 * k).dp)
            Box(
                atTR(k, 1383f, 170f, 92f)
                    .sized(k, 92f, 98f)
                    .clip(slotShape)
                    .background(Glass)
                    .border((1.5f * k).dp, if (!handMode) Orange else Color(0x44FFFFFF), slotShape)
                    .clickable { handMode = false; menuOpen = false }
            ) {
                GlyphIcon(
                    Glyph.PISTOL,
                    Modifier.align(Alignment.TopCenter).padding(top = (12 * k).dp).sized(k, 56f, 40f)
                )
                NfText(
                    "15/60",
                    Modifier.align(Alignment.BottomStart).padding(start = (10 * k).dp, bottom = (14 * k).dp),
                    size = fsz(k, 20f),
                    bold = true
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = (10 * k).dp, bottom = (8 * k).dp)
                        .width((70 * k).dp)
                        .height((3 * k).dp)
                        .background(Color(0x99FFFFFF))
                )
            }
            Box(
                atTR(k, 1477f, 170f, 88f)
                    .sized(k, 88f, 98f)
                    .clip(slotShape)
                    .background(Glass)
                    .border((1.5f * k).dp, Color(0x44FFFFFF), slotShape)
            )
            Box(
                atTR(k, 1568f, 170f, 90f)
                    .sized(k, 90f, 98f)
                    .clip(slotShape)
                    .background(Glass)
                    .border((1.5f * k).dp, if (handMode) Orange else Color(0x44FFFFFF), slotShape)
                    .clickable { handMode = true; menuOpen = false }
            ) {
                GlyphIcon(
                    Glyph.FIST,
                    Modifier.align(Alignment.TopCenter).padding(top = (10 * k).dp).sized(k, 52f, 52f)
                )
                NfText(
                    "\u221E",
                    Modifier.align(Alignment.BottomCenter).padding(bottom = (8 * k).dp),
                    size = fsz(k, 28f),
                    bold = true
                )
            }

            val switchShape = RoundedCornerShape((4 * k).dp)
            Box(
                atTR(k, 1502f, 278f, 156f)
                    .sized(k, 156f, 54f)
                    .clip(switchShape)
                    .background(Glass)
                    .border((2 * k).dp, Orange, switchShape)
                    .clickable {
                        handMode = !handMode
                        menuOpen = false
                        scopeOn = false
                    }
            ) {
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.SWITCH, Modifier.sized(k, 34f, 34f), main = Orange)
                    Spacer(Modifier.width((8 * k).dp))
                    NfText("Switch", size = fsz(k, 28f), bold = true, color = Orange)
                }
            }

            val selShape = RoundedCornerShape((6 * k).dp)
            Box(
                atTR(k, 1427f, 380f, 221f)
                    .sized(k, 221f, 180f)
                    .clip(selShape)
                    .background(Glass)
                    .border((2 * k).dp, Steel, selShape)
            ) {
                Column(Modifier.fillMaxSize()) {
                    SelRow(k, "Gun", Glyph.RIFLE, !handMode, true) {
                        handMode = false
                        menuOpen = false
                    }
                    SelRow(k, "Hand Fight", Glyph.FIST, handMode, false) {
                        handMode = true
                        menuOpen = false
                        scopeOn = false
                    }
                    SelRow(k, "Combat", Glyph.SWORDS, false, false) { }
                }
            }

            // ---------------- left: backpack, medkit, grenade, sprint, walk stick
            GlyphButton(k, atBL(k, 26f, 418f, MID), MID, Glyph.BACKPACK, 0.58f, Orange, 3f)
            GlyphButton(k, atBL(k, 26f, 530f, MID), MID, Glyph.MEDKIT, 0.58f)
            CountBadge(k, atBL(k, 84f, 576f, 28f), "5")
            GlyphButton(k, atBL(k, 26f, 642f, MID), MID, Glyph.GRENADE, 0.58f)
            CountBadge(k, atBL(k, 84f, 688f, 28f), "3")
            GlyphButton(k, atBL(k, 251f, 364f, BIG), BIG, Glyph.SPRINT, 0.58f, Orange, 3f, tint = Orange)

            Stick(k, 220f, false, atBL(k, 175f, 555f, 220f)) { x, y ->
                // x, y in -1..1: wire to character movement here
            }

            // ---------------- right: shield, fire ring, jump, crouch, prone, symmetry, scope, turret
            GlyphButton(
                k, atBR(k, 1257f, 485f, BIG, BIG), BIG, Glyph.SHIELD, 0.56f,
                Steel, 3f, if (shieldOn) Color(0xE01B4DA0) else GlassSoft
            ) { shieldOn = !shieldOn }

            Stick(k, 200f, true, atBR(k, 1270f, 615f, 200f, 200f)) { x, y ->
                // aim direction; shooting while the stick is held away from centre
            }

            GlyphButton(k, atBR(k, 1534f, 632f, BIG, BIG), BIG, Glyph.JUMP, 0.58f)
            GlyphButton(
                k, atBR(k, 1534f, 780f, BIG, BIG), BIG, Glyph.CROUCH, 0.58f,
                if (ducking) Orange else RingIdle, if (ducking) 3f else 2f
            ) { ducking = !ducking }
            GlyphButton(k, atBR(k, 1324f, 822f, BIG, BIG), BIG, Glyph.PRONE, 0.58f)

            // one aligned column of small buttons
            GlyphButton(
                k, atBR(k, 1130f, 600f, SMALL, SMALL), SMALL, Glyph.SYMMETRY, 0.60f,
                if (symmetryOn) Orange else RingIdle, if (symmetryOn) 3f else 2f
            ) { symmetryOn = !symmetryOn }
            if (!handMode) {
                CodeButton(k, atBR(k, 1130f, 695f, SMALL, SMALL), SMALL, NfIconKind.SCOPE, scopeOn) {
                    scopeOn = !scopeOn
                }
            }
            CodeButton(k, atBR(k, 1130f, 790f, SMALL, SMALL), SMALL, NfIconKind.TURRET, turretOn) {
                turretOn = !turretOn
            }

            // ---------------- bottom centre: armor + health bar
            Row(
                atBC(k, 415f, 868f, 645f, 46f).sized(k, 645f, 46f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlyphIcon(Glyph.HELMET, Modifier.sized(k, 40f, 40f))
                Spacer(Modifier.width((6 * k).dp))
                GlyphIcon(Glyph.ARMOR, Modifier.sized(k, 40f, 40f))
                Spacer(Modifier.width((16 * k).dp))
                NfText("HP 200/200", size = fsz(k, 24f), bold = true)
                Spacer(Modifier.width((16 * k).dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height((14 * k).dp)
                        .clip(RoundedCornerShape((7 * k).dp))
                        .background(Color(0x66000000))
                        .border((1.dp), Color(0x55FFFFFF), RoundedCornerShape((7 * k).dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF4F6FA))
                    )
                }
            }

            // ---------------- weapon drop down (drawn last so it sits on top)
            if (menuOpen) {
                Column(
                    atTR(k, 1383f, 172f, 275f)
                        .width((275 * k).dp)
                        .heightIn(max = (200 * k).dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy((4 * k).dp)
                ) {
                    options.forEachIndexed { i, name ->
                        NfPanel(
                            Modifier.fillMaxWidth().height((46 * k).dp),
                            selected = i == selected,
                            onClick = {
                                if (handMode) meleeIdx = i - 1 else gunIdx = i
                                menuOpen = false
                            }
                        ) {
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = (10 * k).dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy((10 * k).dp)
                            ) {
                                if (handMode) {
                                    GlyphIcon(Glyph.FIST, Modifier.sized(k, 28f, 28f), main = NfColors.TextDim)
                                } else {
                                    GlyphIcon(Glyph.RIFLE, Modifier.sized(k, 44f, 18f), main = NfColors.TextDim)
                                }
                                NfText(name, size = fsz(k, 22f), bold = true, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
