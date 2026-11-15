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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.ui.theme.*

private val Dark = Color(0xE60A0E22)
private val Edge = Color(0x22FFFFFF)

private class MenuEntry(val label: String, val route: String, val icon: NfIconKind, val color: Color)

private val entries = listOf(
    MenuEntry("Store", "store", NfIconKind.STORE, NfColors.Gold),
    MenuEntry("Luck Royale", "luck", NfIconKind.GIFT, NfColors.Gold),
    MenuEntry("Character", "character", NfIconKind.USER, Color.White),
    MenuEntry("Weapons", "weapons", NfIconKind.WEAPONS, Color.White),
    MenuEntry("Vault", "vault", NfIconKind.VAULT, Color.White),
    MenuEntry("Pet", "pet", NfIconKind.PET, Color.White),
    MenuEntry("Missions", "missions", NfIconKind.MISSIONS, Color.White),
    MenuEntry("Events", "events", NfIconKind.EVENTS, Color.White)
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
            }
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(12.dp)
    ) {
        // Centre: the 3D character will be drawn here later
        CharacterSilhouette(
            Modifier.align(Alignment.Center).fillMaxHeight(0.92f).width(300.dp)
        )

        // Left: profile + menu buttons (each fits its text, like the reference)
        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .padding(bottom = 44.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProfileCard(p.name, p.level)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                entries.forEach { e ->
                    NfMenuRow(
                        e.label, e.icon, e.color,
                        highlight = e.route == "store",
                        badge = e.route == "luck"
                    ) { onOpen(e.route) }
                }
            }
        }

        // Top right: currency row, battle pass, free gifts
        Column(
            Modifier.align(Alignment.TopEnd),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CurrencyChip(p.coins, NfColors.Gold)
                CurrencyChip(p.diamonds, NfColors.Cyan, true)
                NfIconButton(NfIconKind.MAIL, size = 34.dp) { onOpen("mail") }
                NfIconButton(NfIconKind.SETTINGS, size = 34.dp) { onOpen("settings") }
            }
            BattlePassCard { onOpen("battlepass") }
            GiftCard { onOpen("gifts") }
        }

        // Bottom right: ranked card + PLAY
        Column(
            Modifier.align(Alignment.BottomEnd).width(300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Dark)
                        .border(1.dp, Edge, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        NfText("BR-RANKED", color = NfColors.Cyan, bold = true)
                        NfText("Random Map", size = 11, color = NfColors.TextDim)
                    }
                }
                NfIconButton(NfIconKind.GROUP, size = 46.dp) { onOpen("friends") }
            }
            NfButton(
                "PLAY  \u00BB",
                Modifier.fillMaxWidth().height(58.dp),
                primary = true,
                size = 28
            ) { onOpen("play") }
        }

        // Bottom left: chat bar
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .clip(RoundedCornerShape(8.dp))
                .background(Dark)
                .border(1.dp, Edge, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NfText("[World]", size = 12, color = NfColors.Cyan, bold = true)
            NfText("Kingz: Let's squad up!", size = 12)
        }
    }
}

@Composable
private fun ProfileCard(name: String, level: Int) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Dark)
            .border(1.dp, NfColors.Gold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFFF7A8A), NfColors.Red)))
                .border(1.dp, NfColors.Gold.copy(alpha = 0.8f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            NfIcon(NfIconKind.USER, Modifier.size(20.dp), Color(0xCCFFFFFF))
        }
        Column(Modifier.padding(end = 14.dp)) {
            NfText(name, bold = true)
            NfText("Lv.$level", size = 11, color = NfColors.Gold, bold = true)
        }
    }
}

@Composable
private fun BattlePassCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .width(250.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF4A1FA8), Color(0xFF8B3FE0))))
            .border(1.dp, Color(0x55C9A8FF), shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NfIcon(NfIconKind.SHIELD, Modifier.size(40.dp), Color(0xFFE8DBFF))
        Column {
            NfText("BATTLE PASS", size = 16, bold = true)
            NfText("SEASON 1", size = 14, bold = true)
        }
    }
}

@Composable
private fun GiftCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier
                .width(250.dp)
                .clip(shape)
                .background(Dark)
                .border(1.dp, Edge, shape)
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NfIcon(NfIconKind.GIFT, Modifier.size(34.dp), NfColors.Gold)
            NfText("FREE GIFTS", size = 16, bold = true)
        }
        Box(
            Modifier
                .clip(shape)
                .background(Dark)
                .border(1.dp, Edge, shape)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            NfText("23:45:12", size = 12, color = NfColors.Text)
        }
    }
}
