package com.d200.nightfall3d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.ui.theme.*

private class MenuEntry(val label: String, val route: String, val color: Color)

private val entries = listOf(
    MenuEntry("Store", "store", NfColors.Gold),
    MenuEntry("Luck Royale", "luck", NfColors.Accent),
    MenuEntry("Character", "character", NfColors.Cyan),
    MenuEntry("Weapons", "weapons", NfColors.Purple),
    MenuEntry("Vault", "vault", NfColors.Cyan),
    MenuEntry("Pet", "pet", NfColors.Purple),
    MenuEntry("Missions", "missions", NfColors.Green),
    MenuEntry("Events", "events", NfColors.Accent)
)

@Composable
fun MainMenuScreen(api: GameApi, onOpen: (String) -> Unit) {
    val p = api.profile()
    Box(
        Modifier
            .fillMaxSize()
            .background(NfBrush.Screen)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(12.dp)
    ) {
        // Centre: the 3D character will be drawn here later
        CharacterSilhouette(
            Modifier.align(Alignment.Center).fillMaxHeight(0.92f).width(300.dp)
        )
        NfText(
            "3D MODEL HERE",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            size = 11,
            color = NfColors.TextDim
        )

        // Left: profile + menu list
        Column(
            Modifier
                .align(Alignment.TopStart)
                .width(190.dp)
                .fillMaxHeight()
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProfileCard(p.name, p.level)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                entries.forEach { e -> MenuRow(e) { onOpen(e.route) } }
            }
        }

        // Top right: currency, settings, battle pass, free gifts
        Column(
            Modifier.align(Alignment.TopEnd).width(300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CurrencyChip(p.coins, NfColors.Gold)
                CurrencyChip(p.diamonds, NfColors.Cyan, true)
                NfButton("Settings", size = 12) { onOpen("settings") }
            }
            BattlePassCard()
            GiftCard()
        }

        // Bottom right: ranked info + PLAY
        Column(
            Modifier.align(Alignment.BottomEnd).width(280.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NfPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    NfText("BR-RANKED", color = NfColors.Cyan, bold = true)
                    NfText("Random Map", size = 11, color = NfColors.TextDim)
                }
            }
            NfButton(
                "PLAY  >>",
                Modifier.fillMaxWidth().height(58.dp),
                primary = true,
                size = 26
            ) { onOpen("play") }
        }

        // Bottom left: chat bar
        NfPanel(Modifier.align(Alignment.BottomStart).width(300.dp)) {
            NfText(
                "[World] Kingz: Let's squad up!",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                size = 12
            )
        }
    }
}

@Composable
private fun ProfileCard(name: String, level: Int) {
    NfPanel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(NfColors.Red)
                    .border(2.dp, NfColors.Gold, CircleShape)
            )
            Column {
                NfText(name, bold = true)
                NfText("Lv.$level", size = 11, color = NfColors.TextDim)
            }
        }
    }
}

@Composable
private fun MenuRow(e: MenuEntry, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x990B0F24))
            .border(1.dp, Color(0x33FFFFFF), shape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(e.color))
        NfText(e.label.uppercase(), size = 13, bold = true)
    }
}

@Composable
private fun BattlePassCard() {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .width(210.dp)
            .clip(shape)
            .background(NfBrush.Violet)
            .border(1.dp, Color(0x66FFFFFF), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0x55FFFFFF)))
        Column {
            NfText("BATTLE PASS", bold = true)
            NfText("SEASON 1", size = 12, color = NfColors.Gold, bold = true)
        }
    }
}

@Composable
private fun GiftCard() {
    NfPanel(Modifier.width(180.dp)) {
        Column(Modifier.padding(10.dp)) {
            NfText("FREE GIFTS", color = NfColors.Gold, bold = true)
            NfText("23:45:12", size = 12, color = NfColors.TextDim)
        }
    }
}
