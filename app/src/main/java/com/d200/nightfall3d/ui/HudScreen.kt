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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.d200.nightfall3d.game.ThrowLoadout
import com.d200.nightfall3d.game.CHAR_FILES
import com.d200.nightfall3d.game.GUNS
import com.d200.nightfall3d.game.Game3DView
import com.d200.nightfall3d.game.gunNames
import com.d200.nightfall3d.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.roundToInt

// In-game HUD built from the reference image (1672 x 941).
// All icons are drawn in code (HudIcons.kt). No emojis, no stick figures, no image files for buttons.
//
// The buttons write into one GameInput (read by Game3DView.kt):
//   walk stick -> move | fire ring -> aim + shoot (gun mode) / big fist button -> punch (hand mode)
//   jump | Switch (gun mode <-> hand mode) | weapon list -> which gun is in the hand
//
// CHANGES IN THIS VERSION
//  1) CAPTURE THE FLAG now starts a real match (before, only Deathmatch / Team Deathmatch did, so no flag showed).
//     The match gets the capture limit and the Blue / Red bot numbers from the Match Setup screen.
//  2) Scoreboard in Capture the Flag: captures for each team, "FIRST TO n" (or "NO LIMIT"), and a line that tells
//     you what to do now and how far it is ("GET THE ENEMY FLAG  34 m").
//  3) A banner "YOU HAVE THE FLAG" while you carry the enemy flag.
//
// EARLIER (Build 1)
//  1) Top right icons are LIVE: blue icon = friends alive (you included), red skull = enemies alive.
//  2) Scope button only works on a gun that has a scope (GunDef.hasScope).
//  3) Orange accent on the scoreboard title.
//
// One size system: BIG 112 | MID 88 | SMALL 80 | TOP 58 | sticks 220 (walk) and 200 (fire ring)
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
 * Right stick (the bullet ring) aims and shoots. It measures your finger from the CENTRE of the ring.
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

/**
 * Deathmatch: top 3 players. Team Deathmatch: your team vs the enemy team. Plus the match clock.
 * Capture the Flag: captures for each team, the capture limit, and what you should do now.
 */
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
            NfText(mt.mode.label.uppercase(), size = fsz(k, 20f), color = Orange, bold = true)
            NfText(clock, size = fsz(k, 22f), bold = true)
        }
        if (mt.teamMode) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                NfText("YOUR TEAM  ${mt.teamA}", size = fsz(k, 26f), bold = true, color = Color(0xFF35E07A))
                NfText("${mt.teamB}  ENEMY TEAM", size = fsz(k, 26f), bold = true, color = Color(0xFFFF5A5A))
            }
            if (mt.ctf) {
                NfText(
                    if (mt.captureLimit > 0) "FIRST TO ${mt.captureLimit} CAPTURES" else "NO LIMIT - TIME DECIDES",
                    size = fsz(k, 16f),
                    color = NfColors.TextDim,
                    bold = true,
                    modifier = Modifier.fillMaxWidth(),
                    align = TextAlign.Center
                )
                if (mt.objText.isNotEmpty()) {
                    NfText(
                        mt.objText,
                        size = fsz(k, 19f),
                        color = Orange,
                        bold = true,
                        modifier = Modifier.fillMaxWidth(),
                        align = TextAlign.Center
                    )
                }
            }
        } else {
            if (mt.koth && mt.objText.isNotEmpty()) {
                NfText(
                    mt.objText,
                    size = fsz(k, 19f),
                    color = Orange,
                    bold = true,
                    modifier = Modifier.fillMaxWidth(),
                    align = TextAlign.Center
                )
            }
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

    // Deathmatch / Team Deathmatch / Capture the Flag: the match with the bots. null for every other mode (just you).
    val matchOn = api.match.mode == GameMode.DEATHMATCH ||
        api.match.mode == GameMode.TEAM_DEATHMATCH ||
        api.match.mode == GameMode.CAPTURE_FLAG ||
        api.match.mode == GameMode.KING_OF_HILL
    val match = remember {
        if (matchOn) {
            MatchState(
                api.match.mode,
                MatchSettings.botCount,
                api.match.timeLimit,
                api.match.captureLimit,
                MatchSettings.blueBots,
                MatchSettings.redBots
            )
        } else null
    }

    // The one object the 3D game reads. The buttons below write into it.
    val input = remember {
        GameInput().also {
            it.gunWanted = allGuns.indexOf(gunList[startGun]).coerceAtLeast(0)
            it.grenades = if (ThrowLoadout.grenadeOn) ThrowLoadout.GRENADE_COUNT else 0
            it.gasCans = if (ThrowLoadout.gasOn) ThrowLoadout.GAS_COUNT else 0
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
    val shieldOn = input.shieldOn            // the engine owns it now (it can break and come back)
    var scopeOn by remember { mutableStateOf(false) }
    var noScopeMsg by remember { mutableStateOf(false) }
    var ducking by remember { mutableStateOf(false) }
    var symmetryOn by remember { mutableStateOf(false) }

    // "This gun has no scope" message goes away by itself
    LaunchedEffect(noScopeMsg) {
        if (noScopeMsg) {
            delay(1500)
            noScopeMsg = false
        }
    }

    /** Gun mode <-> hand mode. Hand mode hides the gun, stops shooting and turns the fire ring into a punch button. */
    fun setHand(v: Boolean) {
        handMode = v
        menuOpen = false
        scopeOn = false
        input.scopeOn = false
        input.handMode = v
        input.shooting = false
        input.aimHeld = false
        input.aimX = 0f
        input.aimY = 0f
        input.shieldOn = false
        input.shieldCd = 0f
        input.shieldCdSec = 0
    }

    val options: List<String> =
        if (handMode) listOf("Fists") + melees.map { it.name } else gunList
    val selected = if (handMode) meleeIdx + 1 else gunIdx
    val currentName = options.getOrNull(selected) ?: "No weapon"

    // Does the gun in his hand have a scope? (set hasScope = true on the gun in GUNS, Game3DView.kt)
    val gunHasScope = !handMode && GUNS.getOrNull(allGuns.indexOf(currentName))?.hasScope == true

    // LIVE counts: friends alive (you included) and enemies alive
    val friends = match?.friendsAlive ?: 1
    val enemies = match?.enemiesAlive ?: 0
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

            // scope overlay (only a gun that has a scope)
            if (scopeOn && gunHasScope) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.height * 0.40f
                    // dark edges around the round lens
                    drawRect(Color(0xCC000000), Offset.Zero, androidx.compose.ui.geometry.Size(c.x - r, size.height))
                    drawRect(Color(0xCC000000), Offset(c.x + r, 0f), androidx.compose.ui.geometry.Size(size.width - c.x - r, size.height))
                    drawCircle(Color(0x99FFFFFF), r, c, style = Stroke(3f))
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

            // ---------------- top centre: scoreboard + kill feed (Deathmatch / Team Deathmatch / Capture the Flag)
            if (match != null) {
                ScoreBoard(k, atTL(k, 620f, 10f).width((430 * k).dp), match)
                val feedText = match.feed
                if (feedText.isNotEmpty()) {
                    NfText(
                        feedText,
                        size = fsz(k, 24f),
                        bold = true,
                        color = Color(0xFFFFE066),
                        modifier = atTL(k, 620f, if (match.ctf || match.koth) 215f else 160f).width((430 * k).dp),
                        align = TextAlign.Center
                    )
                }
                // you carry the enemy flag
                if (match.ctf && match.youCarry && !match.playerDead) {
                    NfText(
                        "YOU HAVE THE FLAG",
                        size = fsz(k, 30f),
                        bold = true,
                        color = Orange,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = (255 * k).dp)
                    )
                }
            }

            // ---------------- top right: friends alive, enemies alive, settings
            // blue icon = friends still alive (you included)    red skull = enemies still alive
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
                NfText("$friends", size = fsz(k, 27f), bold = true)
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
                NfText("$enemies", size = fsz(k, 27f), bold = true)
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
            // grenade: taken on the Weapons screen > GRENADES
            if (ThrowLoadout.grenadeOn) {
                GlyphButton(k, atBL(k, 26f, 642f, MID), MID, Glyph.GRENADE, 0.58f) {
                    if (input.grenades > 0) input.throwReq = 1
                }
                CountBadge(k, atBL(k, 84f, 688f, 28f), "${input.grenades}")
            }
            // gas can (above the backpack; change 306f / 352f to move it)
            if (ThrowLoadout.gasOn) {
                Box(
                    atBL(k, 26f, 306f, MID)
                        .sized(k, MID, MID)
                        .clip(CircleShape)
                        .background(GlassSoft)
                        .border((2 * k).dp, Color(0xFF8BE03A), CircleShape)
                        .clickable { if (input.gasCans > 0) input.throwReq = 2 },
                    contentAlignment = Alignment.Center
                ) {
                    NfText("GAS", size = fsz(k, 26f), bold = true, color = Color(0xFF8BE03A))
                }
                CountBadge(k, atBL(k, 84f, 352f, 28f), "${input.gasCans}")
            }

            // walk stick -> the character moves
            Stick(k, 220f, false, atBL(k, 175f, 555f, 220f)) { x, y ->
                input.joyX = x
                input.joyY = y
            }

            // ---------------- right side
            // shield: gun mode only
            if (!handMode) {
                val shieldCd = input.shieldCdSec
                GlyphButton(
                    k, atBR(k, 1257f, 485f, BIG, BIG), BIG, Glyph.SHIELD, 0.56f,
                    if (shieldCd > 0) Orange else Steel, 3f,
                    if (shieldCd > 0) Color(0xE0401800) else if (shieldOn) Color(0xE01B4DA0) else GlassSoft
                ) {
                    if (shieldCd == 0) input.shieldOn = !input.shieldOn   // while it is broken it comes back by itself
                }
                if (shieldCd > 0) {   // countdown over the broken shield button
                    Box(atBR(k, 1257f, 485f, BIG, BIG).sized(k, BIG, BIG), contentAlignment = Alignment.Center) {
                        NfText("$shieldCd", size = fsz(k, 40f), bold = true, color = Orange)
                    }
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
                // scope: works only on a gun with a scope. Other guns: dimmed + a short message.
                CodeButton(
                    k,
                    atBR(k, 1130f, 695f, SMALL, SMALL).alpha(if (gunHasScope) 1f else 0.4f),
                    SMALL, NfIconKind.SCOPE, scopeOn && gunHasScope
                ) {
                    if (gunHasScope) {
                        scopeOn = !scopeOn
                        input.scopeOn = scopeOn      // camera moves to his shoulder
                    } else {
                        noScopeMsg = true
                    }
                }
                CodeButton(k, atBR(k, 1130f, 790f, SMALL, SMALL), SMALL, NfIconKind.TURRET, turretOn) {
                    turretOn = !turretOn
                }
            }

            if (noScopeMsg) {
                NfText(
                    "This gun has no scope",
                    size = fsz(k, 26f),
                    bold = true,
                    color = Color(0xFFFFB070),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (130 * k).dp)
                )
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
                                scopeOn = false             // a new weapon starts un-scoped
                                input.scopeOn = false
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
                        if (match.ctf) {
                            NfText(
                                "Captures  ${match.teamA} - ${match.teamB}",
                                size = fsz(k, 32f),
                                bold = true,
                                color = Orange
                            )
                        }
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