package com.d200.nightfall3d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.ui.theme.NfButton
import com.d200.nightfall3d.ui.theme.NfColors

private val menuItems = listOf(
    "Store" to "store",
    "Luck Royale" to "luck",
    "Character" to "character",
    "Weapons" to "weapons",
    "Vault" to "vault",
    "Pet" to "pet",
    "Missions" to "missions",
    "Events" to "events"
)

@Composable
fun MainMenuScreen(api: GameApi, onOpen: (String) -> Unit) {
    val p = api.profile()
    Box(Modifier.fillMaxSize().background(NfColors.Bg)) {

        // Centre: the 3D character will be drawn here later
        Box(Modifier.align(Alignment.Center)) {
            BasicText(
                "3D character here",
                style = TextStyle(color = NfColors.TextDim, fontSize = 16.sp)
            )
        }

        // Top left: player name and level
        BasicText(
            "${p.name}  Lv.${p.level}",
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            style = TextStyle(
                color = NfColors.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )

        // Left: menu list (scrolls on small screens)
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp, top = 56.dp, bottom = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            menuItems.forEach { (label, route) ->
                NfButton(label, Modifier.width(150.dp)) { onOpen(route) }
            }
        }

        // Top right: coins, diamonds, settings
        Row(
            Modifier.align(Alignment.TopEnd).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Chip("${p.coins}", NfColors.Gold)
            Chip("${p.diamonds}", NfColors.Diamond)
            NfButton("Settings") { onOpen("settings") }
        }

        // Bottom right: PLAY
        NfButton(
            "PLAY",
            Modifier.align(Alignment.BottomEnd).padding(16.dp).width(200.dp),
            primary = true
        ) { onOpen("play") }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(NfColors.Panel)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        BasicText(
            text,
            style = TextStyle(color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        )
    }
}
