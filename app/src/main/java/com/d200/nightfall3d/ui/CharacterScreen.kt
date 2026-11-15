package com.d200.nightfall3d.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.ui.theme.*

@Composable
fun CharacterScreen(api: GameApi, onBack: () -> Unit) {
    val p = api.profile()
    val chars = api.characters()
    var viewId by remember { mutableStateOf(api.selectedCharacterId) }
    var chosenId by remember { mutableStateOf(api.selectedCharacterId) }
    val sel = chars.firstOrNull { it.id == viewId } ?: chars.first()
    val accent = Color(sel.color)

    NfScreen("CHARACTER SELECT", onBack, p.coins, p.diamonds) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

            // Left: character thumbnails
            Column(
                Modifier.width(78.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chars.forEach { c ->
                    NfPanel(Modifier.size(70.dp), selected = c.id == viewId, onClick = { viewId = c.id }) {
                        ArtBlock(Modifier.fillMaxSize(), Color(c.color), Color(0xFF14183A), c.name.take(1))
                    }
                }
            }

            // Centre: 3D model area
            Box(Modifier.weight(1f).fillMaxHeight()) {
                CharacterSilhouette(
                    Modifier.align(Alignment.Center).fillMaxHeight(0.95f).width(280.dp),
                    accent
                )
                NfText(
                    "3D MODEL HERE",
                    modifier = Modifier.align(Alignment.BottomCenter),
                    size = 11,
                    color = NfColors.TextDim
                )
            }

            // Right: info panel
            NfPanel(Modifier.width(270.dp).fillMaxHeight()) {
                Column(
                    Modifier.fillMaxSize().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NfText(sel.name, size = 26, bold = true)
                    NfText(sel.tagline, size = 12, color = NfColors.TextDim)
                    StatBar("Health", sel.health, NfColors.Green)
                    StatBar("Speed", sel.speed, NfColors.Gold)
                    StatBar("Damage", sel.damage, NfColors.Red)
                    NfSectionTitle("SKILL")
                    NfText(sel.skillName, size = 15, bold = true)
                    NfText(sel.skillText, size = 12, color = NfColors.TextDim)
                    Spacer(Modifier.weight(1f))
                    NfButton(
                        if (chosenId == sel.id) "SELECTED" else "SELECT",
                        Modifier.fillMaxWidth(),
                        primary = true
                    ) {
                        chosenId = sel.id
                        api.selectedCharacterId = sel.id
                    }
                }
            }
        }
    }
}
