package com.d200.nightfall3d.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.ui.theme.*

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

        // Top left: minimap and exit
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xAA0B0F24))
                .border(2.dp, NfColors.Cyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(NfColors.Accent))
        }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 100.dp, top = 12.dp)
                .size(34.dp)
                .clip(slotShape)
                .background(Color(0xAA0B0F24))
                .clickable { onExit() },
            contentAlignment = Alignment.Center
        ) {
            NfText("X", bold = true)
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
                    Modifier.clip(slotShape).background(Color(0xFF1666D6)).padding(horizontal = 12.dp, vertical = 4.dp)
                ) { NfText("12", bold = true) }
                Box(
                    Modifier.clip(slotShape).background(Color(0xAA0B0F24)).padding(horizontal = 12.dp, vertical = 4.dp)
                ) { NfText("06:20", bold = true) }
                Box(
                    Modifier.clip(slotShape).background(NfColors.Red).padding(horizontal = 12.dp, vertical = 4.dp)
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
                NfText(ammoText, size = 20, bold = true)
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
                guns.forEachIndexed { i, g ->
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
                .size(110.dp)
                .clip(CircleShape)
                .background(Color(0x33FFFFFF))
                .border(2.dp, Color(0x66FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0x66FFFFFF)))
        }

        // Bottom right: action buttons
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 30.dp, bottom = 30.dp)
                .size(84.dp)
                .clip(CircleShape)
                .background(NfBrush.Gold),
            contentAlignment = Alignment.Center
        ) {
            NfText(if (slot == 0) "PUNCH" else "FIRE", size = 14, bold = true, color = Color(0xFF1A1200))
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 128.dp, bottom = 70.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0x66FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            NfText("JUMP", size = 10, bold = true)
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 40.dp, bottom = 128.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0x66FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            NfText("DUCK", size = 10, bold = true)
        }

        // Bottom centre: health bar
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NfText("HP 200/200", size = 11, bold = true)
            Box(
                Modifier
                    .width(240.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0x55000000))
            ) {
                Box(Modifier.fillMaxSize().background(NfColors.Green))
            }
        }
    }
}
