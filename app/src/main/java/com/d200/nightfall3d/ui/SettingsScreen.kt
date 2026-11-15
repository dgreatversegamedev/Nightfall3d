package com.d200.nightfall3d.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.ui.theme.*

private class Opt(
    val key: String,
    val label: String,
    val def: String,
    val options: List<String>? = null
)

private val low3 = listOf("Low", "Medium", "High")

private val tabs = listOf(
    "BASIC" to listOf(
        Opt("lang", "Language", "English", listOf("English", "French", "Spanish")),
        Opt("gfx", "Graphics", "Ultra", listOf("Smooth", "Standard", "Ultra")),
        Opt("fps", "Frame Rate", "Ultra", listOf("Standard", "High", "Ultra")),
        Opt("pickup", "Auto Pickup", "on"),
        Opt("cross", "Crosshair", "on"),
        Opt("sfx", "Sound Effects", "on")
    ),
    "CONTROLS" to listOf(
        Opt("assist", "Aim Assist", "on"),
        Opt("vibe", "Vibration", "on"),
        Opt("fire", "Fire Button", "Right", listOf("Left", "Right", "Both"))
    ),
    "SENSITIVITY" to listOf(
        Opt("look", "Look", "Medium", low3),
        Opt("aim", "Aim", "Medium", low3),
        Opt("scope", "Scope", "Medium", low3)
    ),
    "GRAPHICS" to listOf(
        Opt("shadows", "Shadows", "on"),
        Opt("aa", "Anti-aliasing", "on"),
        Opt("tex", "Texture Quality", "High", low3)
    ),
    "SOUND" to listOf(
        Opt("music", "Music", "on"),
        Opt("sfx", "Sound Effects", "on"),
        Opt("vol", "Volume", "High", low3)
    ),
    "NOTIFICATION" to listOf(
        Opt("n_match", "Match Alerts", "on"),
        Opt("n_friend", "Friend Invites", "on"),
        Opt("n_daily", "Daily Rewards", "on")
    )
)

@Composable
fun SettingsScreen(api: GameApi, onBack: () -> Unit) {
    val values = remember {
        mutableStateMapOf<String, String>().apply { putAll(api.settings.values) }
    }
    var tab by remember { mutableStateOf(0) }

    NfScreen("SETTINGS", onBack) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NfSideTabs(
                    tabs.map { it.first },
                    tab,
                    { i -> tab = i },
                    Modifier.width(150.dp).fillMaxHeight()
                )
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tabs[tab].second.forEach { o ->
                        val cur = values[o.key] ?: o.def
                        NfRow(o.label) {
                            val opts = o.options
                            if (opts == null) {
                                NfToggle(cur == "on") { v -> values[o.key] = if (v) "on" else "off" }
                            } else {
                                NfChoice(opts, cur, { v -> values[o.key] = v })
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                NfButton("DEFAULT") { values.clear() }
                NfButton("SAVE", primary = true) {
                    api.settings.values.clear()
                    api.settings.values.putAll(values)
                    onBack()
                }
            }
        }
    }
}
