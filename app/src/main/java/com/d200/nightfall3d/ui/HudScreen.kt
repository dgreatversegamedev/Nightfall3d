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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.ui.theme.*
import kotlin.math.roundToInt

private val GlassDark = Color(0xAA0B0F24)

private fun isMelee(cat: String) = cat.equals("melee", ignoreCase = true)

/** Round icon button. [active] lights it gold (turret on, shield on, hand mode). */
@Composable
private fun RoundAction(
    kind: NfIconKind,
    size: Dp,
    modifier: Modifier,
    active: Boolean = false,
    onClick: () -> Unit = {}
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (active) Brush.verticalGradient(listOf(Color(0xCCFFD84A), Color(0xCCFF9A00)))
                else Brush.verticalGradient(listOf(Color(0x66243070), Color(0x66101840)))
            )
            .border(1.dp, if (active) Color(0xAAFFFFFF) else Color(0x55FFFFFF), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        NfIcon(kind, Modifier.size(size * 0.5f), if (active) Color(0xFF1A1200) else Color.White)
    }
}

/**
 * Draggable virtual stick. Reports x/y in -1..1 through [onChange].
 * Left stick = walk. Right stick = aim and shoot (replaces the fire button).
 */
@Composable
private fun Stick(
    size: Dp,
    accent: Color,
    modifier: Modifier,
    knobIcon: NfIconKind? = null,
    onChange: (Float, Float) -> Unit = { _, _ -> }
) {
    var off by remember { mutableStateOf(Offset.Zero) }
    val maxR = with(LocalDensity.current) { size.toPx() * 0.30f }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0x22FFFFFF), Color(0x55101840))))
            .border(1.dp, accent.copy(alpha = 0.55f), CircleShape)
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
        // inner guide ring
        Box(
            Modifier
                .size(size * 0.62f)
                .border(1.dp, accent.copy(alpha = 0.25f), CircleShape)
        )
        Box(
            Modifier
                .offset { IntOffset(off.x.roundToInt(), off.y.roundToInt()) }
                .size(size * 0.40f)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.55f))
                    )
                )
                .border(1.dp, Color(0x66FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (knobIcon != null) {
                NfIcon(knobIcon, Modifier.size(size * 0.20f), Color(0xFF1A1200))
            }
        }
    }
}

@Composable
fun HudScreen(api: GameApi, onExit: () -> Unit) {
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

    val options: List<String> =
        if (handMode) listOf("Fists") + melees.map { it.name } else guns.map { it.name }
    val selected = if (handMode) meleeIdx + 1 else gunIdx
    val currentName = options.getOrNull(selected) ?: "No weapon"
    val ammoText = if (handMode) "MELEE" else "30/120"
    val slotShape = RoundedCornerShape(8.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3B4C73), Color(0xFF1C2238))))
            .windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        // Placeholder arena: ground and crates
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Color(0xFF2A2F4A), Offset(0f, h * 0.78f), Size(w, h * 0.22f))
            drawRect(Color(0xFF8A5A2B), Offset(w * 0.12f, h * 0.55f), Size(w * 0.10f, h * 0.23f))
            drawRect(Color(0xFF2F6FA8), Offset(w * 0.72f, h * 0.50f), Size(w * 0.14f, h * 0.28f))
            drawRect(Color(0xFF8A5A2B), Offset(w * 0.30f, h * 0.63f), Size(w * 0.07f, h * 0.15f))
            drawRect(Color(0xFF2F6FA8), Offset(w * 0.55f, h * 0.61f), Size(w * 0.08f, h * 0.17f))
        }

        // Player
        CharacterSilhouette(
            Modifier.align(Alignment.Center).fillMaxHeight(0.62f).width(160.dp)
        )

        // ---------------- Top left: minimap, exit, weapon switcher ----------------
        Canvas(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .size(88.dp)
                .clip(CircleShape)
                .background(Color(0xCC0B1230))
                .border(1.dp, NfColors.Cyan.copy(alpha = 0.6f), CircleShape)
        ) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.width / 2f
            val line = Color(0x4435D3FF)
            drawCircle(line, r * 0.66f, c, style = Stroke(1.5f))
            drawCircle(line, r * 0.33f, c, style = Stroke(1.5f))
            drawLine(line, Offset(c.x, 0f), Offset(c.x, size.height), 1.5f)
            drawLine(line, Offset(0f, c.y), Offset(size.width, c.y), 1.5f)
            drawCircle(Color(0x6635D3FF), r * 0.12f, c)
            drawCircle(NfColors.Accent, r * 0.09f, c)
        }
        Column(
            Modifier.align(Alignment.TopStart).padding(start = 106.dp, top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NfIconButton(NfIconKind.CLOSE, size = 32.dp) { onExit() }
            }
            // Weapon switcher: tap to open the drop down
            NfPanel(
                Modifier.width(158.dp).height(44.dp),
                selected = menuOpen,
                onClick = { menuOpen = !menuOpen }
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (handMode) {
                        NfIcon(NfIconKind.FIST, Modifier.size(24.dp), NfColors.Gold)
                    } else {
                        GunSilhouette(Modifier.width(34.dp).height(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        NfText(currentName, size = 12, bold = true, maxLines = 1)
                        NfText(
                            if (handMode) "HAND COMBAT" else "WEAPON",
                            size = 9,
                            color = NfColors.TextDim,
                            bold = true
                        )
                    }
                    NfIcon(
                        NfIconKind.BACK,
                        Modifier.size(14.dp).rotate(if (menuOpen) 90f else -90f),
                        NfColors.TextDim
                    )
                }
            }
        }

        // ---------------- Top centre: score and timer ----------------
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    Modifier
                        .clip(CutCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                        .background(NfBrush.Blue)
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                ) { NfText("12", size = 13, bold = true) }
                Box(
                    Modifier
                        .clip(slotShape)
                        .background(GlassDark)
                        .border(1.dp, Color(0x33FFFFFF), slotShape)
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                ) { NfText("06:20", size = 13, bold = true) }
                Box(
                    Modifier
                        .clip(CutCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFFF6B7A), NfColors.Red)))
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                ) { NfText("8", size = 13, bold = true) }
            }
            NfText("Objective: 40", size = 10, color = NfColors.TextDim)
        }

        // ---------------- Top right: ammo ----------------
        Row(
            Modifier.align(Alignment.TopEnd).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NfText(ammoText, size = 22, bold = true)
            if (handMode) {
                NfIcon(NfIconKind.FIST, Modifier.size(30.dp), NfColors.TextDim)
            } else {
                GunSilhouette(Modifier.width(70.dp).height(30.dp))
            }
        }

        // Scope / sight mode overlay (guns only)
        if (scopeOn && !handMode) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.height * 0.32f
                drawCircle(Color(0x66FFFFFF), r, c, style = Stroke(2f))
                drawLine(Color(0x88FFFFFF), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.5f)
                drawLine(Color(0x88FFFFFF), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.5f)
                drawCircle(Color(0xFFFF4D5E), 4f, c)
            }
        }

        // ---------------- Bottom left: walk stick ----------------
        Stick(
            118.dp,
            NfColors.Cyan,
            Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 22.dp)
        ) { x, y ->
            // x, y in -1..1: wire to character movement here
        }

        // ---------------- Bottom right: aim and shoot stick + action buttons ----------------
        Stick(
            126.dp,
            NfColors.Gold,
            Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 18.dp),
            knobIcon = if (handMode) NfIconKind.FIST else NfIconKind.BULLET
        ) { x, y ->
            // aim direction; shooting while the stick is held away from centre
        }
        RoundAction(
            NfIconKind.JUMP, 54.dp,
            Modifier.align(Alignment.BottomEnd).padding(end = 164.dp, bottom = 22.dp)
        )
        RoundAction(
            NfIconKind.DUCK, 48.dp,
            Modifier.align(Alignment.BottomEnd).padding(end = 150.dp, bottom = 84.dp),
            active = ducking
        ) { ducking = !ducking }
        RoundAction(
            NfIconKind.SWAP, 48.dp,
            Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 154.dp),
            active = handMode
        ) {
            handMode = !handMode
            menuOpen = false
            scopeOn = false
        }
        RoundAction(
            NfIconKind.SHIELD_LINE, 46.dp,
            Modifier.align(Alignment.BottomEnd).padding(end = 84.dp, bottom = 166.dp),
            active = shieldOn
        ) { shieldOn = !shieldOn }
        if (!handMode) {
            RoundAction(
                NfIconKind.SCOPE, 46.dp,
                Modifier.align(Alignment.BottomEnd).padding(end = 204.dp, bottom = 140.dp),
                active = scopeOn
            ) { scopeOn = !scopeOn }
        }
        RoundAction(
            NfIconKind.TURRET, 46.dp,
            Modifier.align(Alignment.BottomEnd).padding(end = 138.dp, bottom = 150.dp),
            active = turretOn
        ) { turretOn = !turretOn }

        // ---------------- Bottom centre: health bar ----------------
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NfText("HP 200/200", size = 11, bold = true)
            Box(
                Modifier
                    .width(220.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x66000000))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(Color(0xFF2BBF6E), NfColors.Green)))
                )
            }
        }

        // ---------------- Weapon drop down (drawn last so it sits on top) ----------------
        if (menuOpen) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 106.dp, top = 102.dp)
                    .width(158.dp)
                    .heightIn(max = 132.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEachIndexed { i, name ->
                    NfPanel(
                        Modifier.fillMaxWidth().height(40.dp),
                        selected = i == selected,
                        onClick = {
                            if (handMode) meleeIdx = i - 1 else gunIdx = i
                            menuOpen = false
                        }
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (handMode) {
                                NfIcon(NfIconKind.FIST, Modifier.size(20.dp), NfColors.TextDim)
                            } else {
                                GunSilhouette(Modifier.width(30.dp).height(18.dp))
                            }
                            NfText(name, size = 12, bold = true, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
