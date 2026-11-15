package com.d200.nightfall3d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.game.Weapon
import com.d200.nightfall3d.ui.theme.*

@Composable
fun WeaponsScreen(api: GameApi, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val p = api.profile()
    val all = api.weapons()
    val tabs = listOf("ALL", "RIFLE", "SMG", "SHOTGUN", "SNIPER", "MELEE", "OTHERS")
    var tab by remember { mutableStateOf(0) }
    var selId by remember { mutableStateOf(api.equippedWeaponId) }
    var equipped by remember { mutableStateOf(api.equippedWeaponId) }
    val list = if (tab == 0) all else all.filter { it.category.equals(tabs[tab], ignoreCase = true) }
    val sel = all.firstOrNull { it.id == selId } ?: all.first()

    NfScreen("WEAPONS", onBack, p.coins, p.diamonds) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NfTabs(tabs, tab, { i ->
                if (i == 6) {
                    onOpen("items")
                } else {
                    tab = i
                }
            })
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left: weapon cards
                Column(
                    Modifier.weight(1.5f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (list.isEmpty()) {
                        NfText("No weapons here yet", color = NfColors.TextDim)
                    }
                    list.chunked(3).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { w ->
                                WeaponCard(
                                    w,
                                    w.id == selId,
                                    w.id == equipped,
                                    Modifier.weight(1f).height(104.dp)
                                ) { selId = w.id }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                // Right: details
                NfPanel(Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        Modifier.fillMaxSize().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NfText(sel.name, size = 24, bold = true)
                        NfText(sel.description, size = 12, color = NfColors.TextDim)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33000000))
                                .padding(6.dp)
                        ) {
                            GunSilhouette(Modifier.fillMaxWidth().height(80.dp))
                        }
                        StatBar("Damage", sel.damage, NfColors.Green)
                        StatBar("Range", sel.range, NfColors.Gold)
                        StatBar("Accuracy", sel.accuracy, NfColors.Cyan)
                        StatBar("Magazine", sel.magazine, NfColors.Accent)
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NfButton("GIFT", Modifier.weight(1f), style = BtnStyle.VIOLET, size = 13) { }
                            NfButton("BUY", Modifier.weight(1f), style = BtnStyle.BLUE, size = 13) { }
                            NfButton("EQUIP", Modifier.weight(1f), primary = true, size = 13) {
                                if (sel.owned && !sel.locked) {
                                    equipped = sel.id
                                    api.equippedWeaponId = sel.id
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeaponCard(
    w: Weapon,
    selected: Boolean,
    equipped: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    NfPanel(modifier, selected = selected, onClick = onClick) {
        GunSilhouette(
            Modifier.align(Alignment.Center).fillMaxWidth(0.8f).fillMaxHeight(0.5f),
            if (w.locked) Color(0xFF626A94) else Color(0xFFC4CCF0)
        )
        NfText(
            w.category.uppercase(),
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            size = 9,
            color = NfColors.TextDim,
            bold = true
        )
        when {
            w.locked -> NfIcon(
                NfIconKind.LOCK,
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(14.dp),
                NfColors.Red
            )
            w.owned -> NfIcon(
                NfIconKind.CHECK,
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(14.dp),
                NfColors.Green
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            NfText(w.name, size = 12, bold = true)
            when {
                w.locked -> NfText("Locked", size = 11, color = NfColors.Red, bold = true)
                w.owned -> NfText(
                    if (equipped) "Equipped" else "Owned",
                    size = 11,
                    color = NfColors.Green,
                    bold = true
                )
                w.priceCoins > 0 -> NfPrice(w.priceCoins, NfColors.Gold, false)
                else -> NfPrice(w.priceDiamonds, NfColors.Cyan, true)
            }
        }
    }
}
