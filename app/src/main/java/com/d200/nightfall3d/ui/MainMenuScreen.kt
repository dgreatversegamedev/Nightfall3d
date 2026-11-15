package com.d200.nightfall3d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.ui.theme.*

private class MenuEntry(val label: String, val route: String, val icon: NfIconKind, val color: Color)

private val entries = listOf(
    MenuEntry("Store", "store", NfIconKind.STORE, NfColors.Gold),
    MenuEntry("Luck Royale", "luck", NfIconKind.GIFT, NfColors.Accent),
    MenuEntry("Character", "character", NfIconKind.USER, NfColors.Cyan),
    MenuEntry("Weapons", "weapons", NfIconKind.WEAPONS, NfColors.Purple),
    MenuEntry("Vault", "vault", NfIconKind.VAULT, NfColors.Cyan),
    MenuEntry("Pet", "pet", NfIconKind.PET, NfColors.Purple),
    MenuEntry("Missions", "missions", NfIconKind.MISSIONS, NfColors.Green),
    MenuEntry("Events", "events", NfIconKind.EVENTS, NfColors.Accent)
)

@Composable
fun MainMenuScreen(api: GameApi, onOpen: (String) -> Unit) {
    val p = api.profile()
    Box(
        Modifier
            .fillMaxSize()
            .background(NfBrush.Screen)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x558B5CF6), Color.Transparent),
                        Offset(size.width * 0.5f, size.height * 0.55f),
                        size.height * 0.9f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x2235D3FF), Color.Transparent),
                        Offset(size.width * 0.9f, size.height * 0.1f),
                        size.height * 0.8f
                    )
                )
            }
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
                entries.forEach { e -> NfMenuRow(e.label, e.icon, e.color) { onOpen(e.route) } }
            }
        }

        // Top right: currency, mail, settings, battle pass, free gifts
        Column(
            Modifier.align(Alignment.TopEnd).width(320.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CurrencyChip(p.coins, NfColors.Gold)
                CurrencyChip(p.diamonds, NfColors.Cyan, true)
                NfIconButton(NfIconKind.MAIL, size = 32.dp) { onOpen("mail") }
                NfIconButton(NfIconKind.SETTINGS, size = 32.dp) { onOpen("settings") }
            }
            BattlePassCard { onOpen("battlepass") }
            GiftCard { onOpen("gifts") }
        }

        // Bottom right: ranked info + PLAY
        Column(
            Modifier.align(Alignment.BottomEnd).width(290.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NfPanel(Modifier.fillMaxWidth(), onClick = { onOpen("friends") }) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        NfText("BR-RANKED", color = NfColors.Cyan, bold = true)
                        NfText("Random Map", size = 11, color = NfColors.TextDim)
                    }
                    NfIcon(NfIconKind.GROUP, Modifier.size(24.dp), NfColors.Text)
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
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NfText("[World]", size = 12, color = NfColors.Cyan, bold = true)
                NfText("Kingz: Let's squad up!", size = 12)
            }
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
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFFF7A8A), NfColors.Red)))
                    .border(1.dp, NfColors.Gold.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                NfIcon(NfIconKind.USER, Modifier.size(20.dp), Color(0xCCFFFFFF))
            }
            Column {
                NfText(name, bold = true)
                NfText("Lv.$level", size = 11, color = NfColors.TextDim)
            }
        }
    }
}

@Composable
private fun BattlePassCard(onClick: () -> Unit) {
    val shape = CutCornerShape(topEnd = 14.dp, bottomStart = 14.dp)
    Row(
        Modifier
            .width(220.dp)
            .clip(shape)
            .background(NfBrush.Violet)
            .border(1.dp, Color(0x44FFFFFF), shape)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color(0x44FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            NfIcon(NfIconKind.SHIELD, Modifier.size(22.dp), Color.White)
        }
        Column {
            NfText("BATTLE PASS", bold = true)
            NfText("SEASON 1", size = 12, color = NfColors.Gold, bold = true)
        }
    }
}

@Composable
private fun GiftCard(onClick: () -> Unit) {
    NfPanel(Modifier.width(190.dp), onClick = onClick) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NfIcon(NfIconKind.GIFT, Modifier.size(26.dp), NfColors.Gold)
            Column {
                NfText("FREE GIFTS", color = NfColors.Gold, bold = true)
                NfText("23:45:12", size = 12, color = NfColors.TextDim)
            }
        }
    }
}
