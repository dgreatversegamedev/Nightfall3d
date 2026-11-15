package com.d200.nightfall3d.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.ui.theme.*

private val GlassDark = Color(0xAA0B0F24)

@Composable
private fun RoundAction(kind: NfIconKind, size: Dp, modifier: Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0x66243070), Color(0x66101840))))
            .border(1.dp, Color(0x55FFFFFF), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        NfIcon(kind, Modifier.size(size * 0.5f), Color.White)
    }
}

@Composable
fun HudScreen(api: GameApi, onExit: () -> Unit) {
    val guns = api.weapons().filter { it.owned && !it.locked }.take(4)
    // slot 0 = bare hands (punch), slots 1.. = guns
    var slot by remember { mutableStateOf(1) }
    val ammoText = if (slot == 0) "FISTS" else "30/120"
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

        // Top left: tactical minimap and exit
        Canvas(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .size(84.dp)
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
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 104.dp, top = 12.dp)
                .size(34.dp)
                .clip(slotShape)
                .background(GlassDark)
                .border(1.dp, Color(0x33FFFFFF), slotShape)
                .clickable { onExit() },
            contentAlignment = Alignment.Center
        ) {
            NfIcon(NfIconKind.CLOSE, Modifier.size(18.dp), Color.White)
        }

        // Top centre: score and timer
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier
                        .clip(CutCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                        .background(NfBrush.Blue)
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) { NfText("12", bold = true) }
                Box(
                    Modifier
                        .clip(slotShape)
                        .background(GlassDark)
                        .border(1.dp, Color(0x44FFFFFF), slotShape)
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) { NfText("06:20", bold = true) }
                Box(
                    Modifier
                        .clip(CutCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFFF6B7A), NfColors.Red)))
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) { NfText("8", bold = true) }
            }
            NfText("Objective: 40", size = 11, color = NfColors.TextDim)
        }

        // Top right: ammo and weapon bar (slot 0 is the hands / punch slot)
        Column(
            Modifier.align(Alignment.TopEnd).padding(10.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NfText(ammoText, size = 22, bold = true)
                GunSilhouette(Modifier.width(70.dp).height(30.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NfPanel(Modifier.size(46.dp), selected = slot == 0, onClick = { slot = 0 }) {
                    NfText(
                        "HANDS",
                        modifier = Modifier.align(Alignment.Center),
                        size = 9,
                        bold = true,
                        align = TextAlign.Center
                    )
                }
                guns.forEachIndexed { i, _ ->
                    NfPanel(Modifier.size(46.dp), selected = slot == i + 1, onClick = { slot = i + 1 }) {
                        GunSilhouette(Modifier.align(Alignment.Center).fillMaxSize(0.8f))
                    }
                }
            }
        }

        // Bottom left: movement joystick
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 30.dp, bottom = 24.dp)
                .size(112.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0x22FFFFFF), Color(0x44101840))))
                .border(1.dp, Color(0x44FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xCCFFFFFF), Color(0x77AAB4E0))))
                    .border(1.dp, Color(0x99FFFFFF), CircleShape)
            )
        }

        // Bottom right: action buttons
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 30.dp, bottom = 28.dp)
                .size(88.dp)
                .clip(CircleShape)
                .background(NfBrush.Gold)
                .border(1.dp, Color(0x66FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NfIcon(NfIconKind.BULLET, Modifier.size(28.dp), Color(0xFF1A1200))
                NfText(
                    if (slot == 0) "PUNCH" else "FIRE",
                    size = 12,
                    bold = true,
                    color = Color(0xFF1A1200)
                )
            }
        }
        RoundAction(NfIconKind.JUMP, 54.dp, Modifier.align(Alignment.BottomEnd).padding(end = 128.dp, bottom = 66.dp))
        RoundAction(NfIconKind.DUCK, 54.dp, Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 128.dp))

        // Bottom centre: health bar
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NfText("HP 200/200", size = 11, bold = true)
            Box(
                Modifier
                    .width(260.dp)
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
    }
}
