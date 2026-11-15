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
    PlayType.ONLINE -> Pair(Color(0xFF2E6BD6), Color(0xFF0F1D4A))
    PlayType.OFFLINE -> Pair(Color(0xFF4A5470), Color(0xFF141A2E))
    PlayType.FRIENDS -> Pair(Color(0xFF7A3FD0), Color(0xFF1B1048))
    PlayType.TRAINING -> Pair(Color(0xFFD0702E), Color(0xFF3A1A0F))
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                types.forEach { t ->
                    PlayCard(t, t == selected, Modifier.weight(1f).fillMaxHeight()) {
                        selected = t
                        api.match.playType = t
                        onOpen(if (t == PlayType.FRIENDS) "friends" else "matchsetup")
                    }
                }
            }
            NfText("Go online for the best experience!", size = 12, color = NfColors.Cyan)
        }
    }
}

@Composable
private fun PlayCard(t: PlayType, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = cardColors(t)
    NfPanel(modifier, selected = selected, onClick = onClick) {
        ArtBlock(Modifier.fillMaxSize(), colors.first, colors.second, t.label.take(1))
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(90.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE0B0F24))))
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            NfText(
                t.label.uppercase(),
                size = 20,
                bold = true,
                color = if (selected) NfColors.Gold else NfColors.Text
            )
            NfText(t.tagline, size = 11, color = NfColors.TextDim)
        }
    }
}
