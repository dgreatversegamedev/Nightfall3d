package com.d200.nightfall3d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.ui.theme.*

@Composable
fun CharacterScreen(api: GameApi, onBack: () -> Unit) {
    val p = api.profile()
    val chars = api.characters()
    var idx by remember {
        mutableIntStateOf(chars.indexOfFirst { it.id == api.selectedCharacterId }.coerceAtLeast(0))
    }
    var chosenId by remember { mutableStateOf(api.selectedCharacterId) }
    val sel = chars[idx.coerceIn(0, chars.lastIndex)]
    val many = chars.size > 1

    fun step(d: Int) {
        if (chars.isEmpty()) return
        idx = ((idx + d) % chars.size + chars.size) % chars.size
    }

    NfScreen("CHARACTER SELECT", onBack, p.coins, p.diamonds) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

            // Centre: the real 3D character, with an arrow on each side to go to the next / previous one
            Box(Modifier.weight(1f).fillMaxHeight()) {
                key(sel.id) {
                    CharacterPreview3D(sel.glbPath, Modifier.fillMaxSize())
                }
                NfIconButton(
                    NfIconKind.BACK,
                    Modifier.align(Alignment.CenterStart).padding(start = 8.dp).alpha(if (many) 1f else 0.35f),
                    size = 52.dp
                ) { step(-1) }
                NfIconButton(
                    NfIconKind.BACK,
                    Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).rotate(180f).alpha(if (many) 1f else 0.35f),
                    size = 52.dp
                ) { step(1) }
                NfText(
                    "${idx.coerceIn(0, chars.lastIndex) + 1} / ${chars.size}",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                    size = 12,
                    color = NfColors.TextDim
                )
            }

            // Right: info panel
            NfPanel(Modifier.width(270.dp).fillMaxHeight()) {
                Column(
                    Modifier.fillMaxSize().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NfText(sel.name, size = 26, bold = true, modifier = Modifier.weight(1f))
                        NfIcon(NfIconKind.SHIELD, Modifier.size(22.dp), NfColors.Purple)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        NfText("Lv. ${sel.level}", size = 14, bold = true, color = NfColors.Gold)
                        NfText("Rank: ${sel.rank}", size = 14, bold = true, color = NfColors.Cyan)
                    }
                    NfText(sel.tagline, size = 12, color = NfColors.TextDim)
                    StatBar("Health", sel.health, NfColors.Green)
                    StatBar("Speed", sel.speed, NfColors.Gold)
                    StatBar("Damage", sel.damage, NfColors.Red)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x33000000)).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NfSectionTitle("SKILL")
                        NfText(sel.skillName, size = 15, bold = true, color = NfColors.Gold)
                        NfText(sel.skillText, size = 12, color = NfColors.TextDim)
                    }
                    Spacer(Modifier.weight(1f))
                    NfButton(
                        if (chosenId == sel.id) "SELECTED" else "SELECT",
                        Modifier.fillMaxWidth(),
                        primary = true
                    ) {
                        chosenId = sel.id
                        api.selectedCharacterId = sel.id
                        SelectedCharacter.glb = sel.glbPath     // the main menu now shows this one
                    }
                }
            }
        }
    }
}
