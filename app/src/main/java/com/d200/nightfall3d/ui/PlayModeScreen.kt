package com.d200.nightfall3d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.game.PlayType
import com.d200.nightfall3d.ui.theme.*

private fun cardColors(t: PlayType): Pair<Color, Color> = when (t) {
    PlayType.ONLINE -> Pair(Color(0xFF3A86F0), Color(0xFF0A1440))
    PlayType.OFFLINE -> Pair(Color(0xFF5A6684), Color(0xFF10162A))
    PlayType.FRIENDS -> Pair(Color(0xFF9A52F0), Color(0xFF150C3E))
    PlayType.TRAINING -> Pair(Color(0xFFE8803A), Color(0xFF35160B))
}

private fun cardIcon(t: PlayType): NfIconKind = when (t) {
    PlayType.ONLINE -> NfIconKind.GLOBE
    PlayType.OFFLINE -> NfIconKind.OFFLINE
    PlayType.FRIENDS -> NfIconKind.GROUP
    PlayType.TRAINING -> NfIconKind.TARGET
}

@Composable
fun PlayModeScreen(api: GameApi, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val types = PlayType.values()
    var selected by remember { mutableStateOf(api.match.playType) }

    NfScreen("SELECT PLAY MODE", onBack) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NfText("Choose how you want to play", color = NfColors.TextDim)
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                types.forEach { t ->
                    PlayCard(t, t == selected, Modifier.weight(1f).fillMaxHeight()) {
                        selected = t
                        api.match.playType = t
                        onOpen(if (t == PlayType.FRIENDS) "friends" else "matchsetup")
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NfIcon(NfIconKind.GLOBE, Modifier.size(14.dp), NfColors.Cyan)
                NfText("Go online for the best experience!", size = 12, color = NfColors.Cyan)
            }
        }
    }
}

@Composable
private fun PlayCard(t: PlayType, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = cardColors(t)
    NfPanel(modifier, selected = selected, onClick = onClick) {
        ArtBlock(Modifier.fillMaxSize(), colors.first, colors.second)
        // big faint icon as artwork placeholder
        NfIcon(
            cardIcon(t),
            Modifier.align(Alignment.Center).offset(y = (-24).dp).size(86.dp),
            Color(0x33FFFFFF)
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(110.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF00A0E24))))
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NfIcon(cardIcon(t), Modifier.size(22.dp), if (selected) NfColors.Gold else NfColors.Text)
                NfText(
                    t.label.uppercase(),
                    size = 20,
                    bold = true,
                    color = if (selected) NfColors.Gold else NfColors.Text
                )
            }
            NfText(t.tagline, size = 11, color = NfColors.TextDim)
        }
    }
}
