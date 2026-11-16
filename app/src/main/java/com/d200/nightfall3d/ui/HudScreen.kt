package com.d200.nightfall3d.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import com.d200.nightfall3d.game.GameInput
import com.d200.nightfall3d.game.GameMode
import com.d200.nightfall3d.game.MatchSettings
import com.d200.nightfall3d.game.MatchState
import com.d200.nightfall3d.game.PLAYER_MAX_HP
import com.d200.nightfall3d.game.CHAR_FILES
import com.d200.nightfall3d.game.Game3DView
import com.d200.nightfall3d.game.gunNames
import com.d200.nightfall3d.ui.theme.*
import kotlin.math.hypot
import kotlin.math.roundToInt

// In-game HUD built from the reference image (1672 x 941).
// All icons are drawn in code (HudIcons.kt). No emojis, no stick figures, no image files for buttons.
//
// This screen now has the real 3D game behind it (Game3DView.kt). The buttons write into one GameInput:
//   walk stick  -> move
//   fire ring   -> aim + shoot (gun mode)   |   big fist button -> punch (hand mode)
//   jump        -> jump
//   Switch (round button beside the fire ring) -> gun mode or hand mode (fists: gun hidden, only punch / jump / crouch)
//   weapon list -> which gun is in the hand
//
// DEATHMATCH / TEAM DEATHMATCH (new)
//   When the mode picked on Match Setup is Deathmatch or Team Deathmatch, a MatchState (MatchLogic.kt) is made here
//   and handed to the 3D game through input.match. The HUD then shows:
//     top centre   scoreboard (top 3 in Deathmatch, team scores in Team Deathmatch) + clock + kill feed
//     top right    players in the match, your kills
//     bottom       your real health
//     centre       ELIMINATED + respawn countdown, and the MATCH OVER screen
//
// One size system, so nothing looks random:
//   BIG   112  shield, jump, crouch, prone, sprint
//   MID    88  backpack, medkit, grenade
//   SMALL  80  symmetry, scope, sentry turret
//   TOP    58  mic, speaker, emoji, chat
//   sticks 220 (walk) and 200 (fire ring)
// One colour system: dark navy glass, steel ring when idle, Nightfall orange when active.

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

private const val FIRE_ON = 0.70f   // fire ring pushed past this = shoots
private const val FIRE_OFF = 0.62f  // ...and stops again below this (small gap so it does not flicker)

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
 * Left stick walks (moves from where you first touch it).
 * Right stick (the bullet ring) aims and shoots. It measures your finger from the CENTRE of the ring,
 * so the direction you see is the direction you aim, in every direction (left, middle, right, back).
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
                if (fire) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val c = Offset(size.width / 2f, size.height / 2f)
                        fun push(p: Offset) {
                            var v = p - c
                            val d = v.getDistance()
                            if (d > maxR) v = v * (maxR / d)
                            off = v
                            onChange(v.x / maxR, v.y / maxR)
                        }
                        push(down.position)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            push(ch.position)
                            ch.consume()
                        }
                        off = Offset.Zero
                        onChange(0f, 0f)
                    }
                } else {
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

/** Deathmatch: top 3 players. Team Deathmatch: your team vs the enemy team. Plus the match clock. */
@Composable
private fun ScoreBoard(k: Float, modifier: Modifier, mt: MatchState) {
    val shape = RoundedCornerShape((6 * k).dp)
    val t = mt.timeSec
    val clock = "${t / 60}:${(t % 60).toString().padStart(2, '0')}"
    Column(
        modifier
            .clip(shape)
            .background(Glass)
            .border((1.5f * k).dp, Color(0x44FFFFFF), shape)
            .padding(horizontal = (12 * k).dp, vertical = (6 * k).dp),
        verticalArrangement = Arrangement.spacedBy((2 * k).dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            NfText(mt.mode.label.uppercase(), size = fsz(k, 20f), color = NfColors.Cyan, bold = true)
            NfText(clock, size = fsz(k, 22f), bold = true)
        }
        if (mt.teamMode) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                NfText("YOUR TEAM  ${mt.teamA}", size = fsz(k, 26f), bold = true, color = Color(0xFF35E07A))
                NfText("${mt.teamB}  ENEMY TEAM", size = fsz(k, 26f), bold = true, color = Color(0xFFFF5A5A))
            }
        } else {
            mt.rows().take(3).forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NfText(
                        "${i + 1}. ${r.name}",
                        size = fsz(k, 22f),
                        bold = r.isYou,
                        color = if (r.isYou) NfColors.Gold else NfColors.Text
                    )
                    NfText("${r.kills}", size = fsz(k, 22f), bold = true, color = if (r.isYou) NfColors.Gold else NfColors.Text)
                }
            }
        }
    }
}

@Composable
fun HudScreen(api: GameApi, onExit: () -> Unit) {
    val p = api.profile()
    val owned = api.weapons().filter { it.owned && !it.locked }
    val guns = owned.filter { !isMelee(it.category) }.take(6)
    val melees = owned.filter { isMelee(it.category) }.take(6)

    // Only the guns picked on the Weapons screen (max 5) are in his list. The first one is his starting gun.
    val allGuns = remember { gunNames() }
    val gunList = remember { GunLoadout.names(allGuns) }
    val startGun = remember { gunList.indexOf(api.equippedWeaponId).let { if (it < 0) 0 else it } }

    // Deathmatch / Team Deathmatch: the match with the bots. null for every other mode (just you, like before).
    val matchOn = api.match.mode == GameMode.DEATHMATCH || api.match.mode == GameMode.TEAM_DEATHMATCH
    val match = remember {
        if (matchOn) MatchState(api.match.mode, MatchSettings.botCount, api.match.timeLimit) else null
    }

    // The one object the 3D game reads. The buttons below write into it.
    val input = remember {
        GameInput().also {
            it.gunWanted = allGuns.indexOf(gunList[startGun]).coerceAtLeast(0)
            it.match = match
        }
    }
    // the character picked on the Character screen (blue is the only real one for now)
    val pickedGlb = api.characters().firstOrNull { it.id == api.selectedCharacterId }?.glbPath ?: "char_blue.glb"
    val charIndex = CHAR_FILES.indexOf(pickedGlb).let { if (it < 0) 0 else it }

    var handMode by remember { mutableStateOf(false) }
    var gunIdx by remember { mutableStateOf(startGun) }
    var meleeIdx by remember { mutableStateOf(-1) } // -1 = bare fists
    var menuOpen by remember { mutableStateOf(false) }
    var turretOn by remember { mutableStateOf(false) }
    var shieldOn by remember { mutableStateOf(false) }
    var scopeOn by remember { mutableStateOf(false) }
    var ducking by remember { mutableStateOf(false) }
    var symmetryOn by remember { mutableStateOf(false) }

    /** Gun mode <-> hand mode. Hand mode hides the gun, stops shooting and turns the fire ring into a punch button. */
    fun setHand(v: Boolean) {
        handMode = v
        menuOpen = false
        scopeOn = false
        input.handMode = v
        input.shooting = false
        input.aimHeld = false
        input.aimX = 0f
        input.aimY = 0f
        shieldOn = false
        input.shieldOn = false
    }

    val options: List<String> =
        if (handMode) listOf("Fists") + melees.map { it.name } else gunList
    val selected = if (handMode) meleeIdx + 1 else gunIdx
    val currentName = options.getOrNull(selected) ?: "No weapon"

    // Players in this match: you + the bots. Without a match it is only you.
    val playersJoined = if (match != null) match.bots.size + 1 else 1
    val hp = match?.playerHp ?: PLAYER_MAX_HP

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
            // ---------------- the real 3D game (arena, character, camera drag / pinch)
            Game3DView(input = input, modifier = Modifier.fillMaxSize(), charIndex = charIndex)

            // crosshair (gun mode only)
            if (!handMode) {
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

            // ---------------- top centre: scoreboard + kill feed (Deathmatch / Team Deathmatch only)
            if (match != null) {
                ScoreBoard(k, atTL(k, 620f, 10f).width((430 * k).dp), match)
                val feedText = match.feed
                if (feedText.isNotEmpty()) {
                    NfText(
                        feedText,
                        size = fsz(k, 24f),
                        bold = true,
                        color = Color(0xFFFFE066),
                        modifier = atTL(k, 620f, 160f).width((430 * k).dp),
                        align = TextAlign.Center
                    )
                }
            }

            // ---------------- top right: players joined, kills, settings
            // All boxes use the same dark glass. Only the small icon carries colour.
            val infoShape = RoundedCornerShape((5 * k).dp)
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
                NfText("$playersJoined", size = fsz(k, 27f), bold = true)
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
                NfText(if (match != null) "${match.myKills()}" else "4", size = fsz(k, 27f), bold = true)
            }
            // settings gear: leaves the match
            GlyphIcon(
                Glyph.GEAR,
                edgeTR(k, 6f, 8f).sized(k, 46f, 46f).clickable { onExit() }
            )

            // ---------------- weapon panel (tap it to pick a weapon)
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

            // ---------------- left: backpack, medkit, grenade, walk stick
            GlyphButton(k, atBL(k, 26f, 418f, MID), MID, Glyph.BACKPACK, 0.58f, Orange, 3f)
            GlyphButton(k, atBL(k, 26f, 530f, MID), MID, Glyph.MEDKIT, 0.58f)
            CountBadge(k, atBL(k, 84f, 576f, 28f), "5")
            GlyphButton(k, atBL(k, 26f, 642f, MID), MID, Glyph.GRENADE, 0.58f)
            CountBadge(k, atBL(k, 84f, 688f, 28f), "3")

            // walk stick -> the character moves
            Stick(k, 220f, false, atBL(k, 175f, 555f, 220f)) { x, y ->
                input.joyX = x
                input.joyY = y
            }

            // ---------------- right side
            // shield: gun mode only
            if (!handMode) {
                GlyphButton(
                    k, atBR(k, 1257f, 485f, BIG, BIG), BIG, Glyph.SHIELD, 0.56f,
                    Steel, 3f, if (shieldOn) Color(0xE01B4DA0) else GlassSoft
                ) {
                    shieldOn = !shieldOn
                    input.shieldOn = shieldOn      // shield bubble around the character
                }
            }

            if (!handMode) {
                // fire ring -> aim, and shoot when pushed to the outer edge
                Stick(k, 200f, true, atBR(k, 1270f, 615f, 200f, 200f)) { x, y ->
                    val m = hypot(x, y)
                    input.aimHeld = m > 0.01f
                    input.shooting = if (input.shooting) m >= FIRE_OFF else m >= FIRE_ON
                    input.aimX = x
                    input.aimY = y
                }
            } else {
                // hand mode: the fire ring becomes one big PUNCH button (right, left, low, right ...)
                GlyphButton(
                    k, atBR(k, 1270f, 615f, 200f, 200f), 200f, Glyph.FIST, 0.52f,
                    Orange, 4f, Glass, Orange
                ) { input.punchTap = true }
            }

            // Switch: gun <-> hand fight (round button above the fire ring)
            GlyphButton(
                k, atBR(k, 1400f, 510f, MID, MID), MID, Glyph.SWITCH, 0.58f,
                Orange, 3f, tint = Orange
            ) { setHand(!handMode) }

            // jump
            GlyphButton(k, atBR(k, 1534f, 632f, BIG, BIG), BIG, Glyph.JUMP, 0.58f) { input.jumpTap = true }
            GlyphButton(
                k, atBR(k, 1534f, 780f, BIG, BIG), BIG, Glyph.CROUCH, 0.58f,
                if (ducking) Orange else RingIdle, if (ducking) 3f else 2f
            ) {
                ducking = !ducking
                input.crouch = ducking             // character bends down
            }
            GlyphButton(k, atBR(k, 1324f, 822f, BIG, BIG), BIG, Glyph.PRONE, 0.58f)

            // one aligned column of small buttons (gun mode only)
            if (!handMode) {
                GlyphButton(
                    k, atBR(k, 1130f, 600f, SMALL, SMALL), SMALL, Glyph.SYMMETRY, 0.60f,
                    if (symmetryOn) Orange else RingIdle, if (symmetryOn) 3f else 2f
                ) { symmetryOn = !symmetryOn }
                CodeButton(k, atBR(k, 1130f, 695f, SMALL, SMALL), SMALL, NfIconKind.SCOPE, scopeOn) {
                    scopeOn = !scopeOn
                }
                CodeButton(k, atBR(k, 1130f, 790f, SMALL, SMALL), SMALL, NfIconKind.TURRET, turretOn) {
                    turretOn = !turretOn
                }
            }

            // ---------------- bottom centre: health bar (real health in a match)
            Row(
                atBC(k, 415f, 868f, 645f, 46f).sized(k, 645f, 46f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NfText("HP $hp/$PLAYER_MAX_HP", size = fsz(k, 24f), bold = true)
                Spacer(Modifier.width((16 * k).dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height((14 * k).dp)
                        .clip(RoundedCornerShape((7 * k).dp))
                        .background(Color(0x66000000))
                        .border((1.dp), Color(0x55FFFFFF), RoundedCornerShape((7 * k).dp))
                ) {
                    val frac = (hp.toFloat() / PLAYER_MAX_HP).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(frac)
                            .background(if (frac < 0.3f) Color(0xFFFF5A5A) else Color(0xFFF4F6FA))
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
                                if (handMode) {
                                    meleeIdx = i - 1
                                } else {
                                    gunIdx = i
                                    input.gunWanted = allGuns.indexOf(name).coerceAtLeast(0)      // puts that gun in his hand
                                    api.equippedWeaponId = name
                                }
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

            // ---------------- eliminated: respawn countdown
            if (match != null && match.playerDead && !match.over) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy((6 * k).dp)
                ) {
                    NfText("ELIMINATED", size = fsz(k, 60f), bold = true, color = Color(0xFFFF5A5A))
                    NfText("Respawning in ${match.respawnSec}", size = fsz(k, 30f), bold = true)
                }
            }

            // ---------------- match over (on top of everything)
            if (match != null && match.over) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xCC000000))
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NfText("MATCH OVER", size = fsz(k, 34f), color = NfColors.TextDim, bold = true)
                        NfText(match.resultText, size = fsz(k, 64f), bold = true, color = NfColors.Gold)
                        NfText(
                            "Your kills ${match.myKills()}    Deaths ${match.deaths[0]}",
                            size = fsz(k, 28f),
                            bold = true
                        )
                        NfButton("BACK TO MENU", primary = true) { onExit() }
                    }
                }
            }
        }
    }
}
