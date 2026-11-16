package com.d200.nightfall3d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.d200.nightfall3d.game.gunNames
import com.d200.nightfall3d.ui.theme.*

// =====================================================================
// WeaponsScreen.kt
// Lists every gun that is uploaded into the game (the GUNS list in Game3DView.kt).
// Add a gun there and it shows up here by itself. TEST MODE: no buying, every gun can be equipped.
// One colour only: the damage green. Everything else is dark.
//
// TO GIVE A GUN ITS REAL INFO, edit GUN_INFO below (category, text, and 4 numbers from 0 to 1).
// A gun that is not in GUN_INFO still shows up (as a RIFLE with middle numbers).
// The gun you equip here is the gun you start the match with.
// =====================================================================

private class GunInfo(
    val category: String,
    val description: String,
    val damage: Float,
    val range: Float,
    val accuracy: Float,
    val magazine: Float
)

// EDIT HERE LATER: stats are middle numbers for now
private val GUN_INFO = mapOf(
    "MP5" to GunInfo("SMG", "Sub machine gun.", 0.5f, 0.5f, 0.5f, 0.5f),
    "AK-47" to GunInfo("Rifle", "Classic and reliable.", 0.5f, 0.5f, 0.5f, 0.5f),
    "AR" to GunInfo("Rifle", "Assault rifle.", 0.5f, 0.5f, 0.5f, 0.5f)
)

private class GunRow(val name: String, val info: GunInfo)

private fun gunRow(name: String) = GunRow(name, GUN_INFO[name] ?: GunInfo("Rifle", "", 0.5f, 0.5f, 0.5f, 0.5f))

private val ScreenBg = Color(0xFF080B0A)
private val PanelBg = Color(0xFF0E1311)
private val CardBg = Color(0xFF111614)
private val LineColor = Color(0xFF232B28)
private val DimText = Color(0xFF8A938F)
private val TrackColor = Color(0xFF1C2421)

@Composable
fun WeaponsScreen(api: GameApi, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val names = remember { gunNames() }
    val rows = remember { names.map { gunRow(it) } }
    val tabs = listOf("ALL", "RIFLE", "SMG", "SHOTGUN", "SNIPER", "MELEE", "OTHERS")
    var tab by remember { mutableStateOf(0) }
    var equipped by remember {
        mutableStateOf(if (api.equippedWeaponId in names) api.equippedWeaponId else (names.firstOrNull() ?: ""))
    }
    var selName by remember { mutableStateOf(equipped) }
    val list = if (tab == 0) rows else rows.filter { it.info.category.equals(tabs[tab], ignoreCase = true) }
    val sel = rows.firstOrNull { it.name == selName } ?: rows.firstOrNull()

    Box(
        Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NfIconButton(NfIconKind.BACK, Modifier, size = 44.dp) { onBack() }
                NfText("WEAPONS", size = 22, bold = true)
            }

            // tabs
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                tabs.forEachIndexed { i, t ->
                    val on = i == tab
                    Column(
                        Modifier.clickable {
                            if (i == 6) onOpen("items") else tab = i
                        },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        NfText(t, size = 13, bold = true, color = if (on) NfColors.Green else DimText)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .width(34.dp)
                                .height(2.dp)
                                .background(if (on) NfColors.Green else Color.Transparent)
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // left: the uploaded guns
                Column(
                    Modifier.weight(1.5f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NfText("UPLOADED GUNS  ${list.size}", size = 11, bold = true, color = DimText)
                    if (list.isEmpty()) {
                        NfText("No guns here yet", size = 13, color = DimText)
                    }
                    list.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { g ->
                                GunCard(
                                    g,
                                    g.name == selName,
                                    g.name == equipped,
                                    Modifier.weight(1f).height(104.dp)
                                ) { selName = g.name }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                // right: details
                if (sel != null) {
                    val panelShape = RoundedCornerShape(8.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(panelShape)
                            .background(PanelBg)
                            .border(1.dp, LineColor, panelShape)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NfText(sel.name, size = 24, bold = true)
                        if (sel.info.description.isNotEmpty()) {
                            NfText(sel.info.description, size = 12, color = DimText)
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(CardBg)
                                .padding(6.dp)
                        ) {
                            GunSilhouette(Modifier.fillMaxWidth().height(80.dp), Color(0xFF8E9894))
                        }
                        GreenBar("Damage", sel.info.damage)
                        GreenBar("Range", sel.info.range)
                        GreenBar("Accuracy", sel.info.accuracy)
                        GreenBar("Magazine", sel.info.magazine)
                        Spacer(Modifier.weight(1f))
                        val isEq = sel.name == equipped
                        val btnShape = RoundedCornerShape(6.dp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(btnShape)
                                .background(if (isEq) CardBg else NfColors.Green)
                                .border(1.dp, NfColors.Green, btnShape)
                                .clickable(enabled = !isEq) {
                                    equipped = sel.name
                                    api.equippedWeaponId = sel.name
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            NfText(
                                if (isEq) "EQUIPPED" else "EQUIP",
                                size = 14,
                                bold = true,
                                color = if (isEq) NfColors.Green else Color(0xFF04100A)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GunCard(
    g: GunRow,
    selected: Boolean,
    equipped: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .clip(shape)
            .background(CardBg)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) NfColors.Green else LineColor, shape)
            .clickable { onClick() }
    ) {
        GunSilhouette(
            Modifier.align(Alignment.Center).fillMaxWidth(0.8f).fillMaxHeight(0.5f),
            Color(0xFF8E9894)
        )
        NfText(
            g.info.category.uppercase(),
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            size = 9,
            color = DimText,
            bold = true
        )
        if (equipped) {
            NfIcon(
                NfIconKind.CHECK,
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(14.dp),
                NfColors.Green
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            NfText(g.name, size = 12, bold = true)
            if (equipped) {
                NfText("Equipped", size = 11, color = NfColors.Green, bold = true)
            }
        }
    }
}

/** One stat: name on the left, thin bar on the right. Always the same green. */
@Composable
private fun GreenBar(label: String, value: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NfText(label, Modifier.width(78.dp), size = 12, color = DimText)
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(TrackColor)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(NfColors.Green)
            )
        }
    }
}
