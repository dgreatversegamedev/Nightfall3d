package com.d200.nightfall3d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.game.GameMode
import com.d200.nightfall3d.game.MatchSettings
import com.d200.nightfall3d.ui.theme.*

// =====================================================================
// MatchSetupScreen.kt
// CHANGES IN THIS VERSION
//  1) Capture the Flag is now a bot mode (it was missing, so the flag never showed).
//  2) Team Deathmatch and Capture the Flag: choose how many bots are on BLUE (your team, you not counted)
//     and how many on RED. Deathmatch keeps the single "Bots" number.
//  3) Capture the Flag: "Captures to win" = No limit / 3 / 5 / 7 (saved in api.match.captureLimit).
// =====================================================================

// 0 = no limit (keep capturing until the time is up)
private val CAPTURE_OPTIONS = listOf(0, 3, 5, 7)

@Composable
fun MatchSetupScreen(api: GameApi, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val modes = GameMode.values()
    val arenas = api.arenas()
    var modeIdx by remember { mutableStateOf(modes.indexOf(api.match.mode).coerceAtLeast(0)) }
    var arenaIdx by remember { mutableStateOf(arenas.indexOf(api.match.arena).coerceAtLeast(0)) }
    var team by remember { mutableStateOf(api.match.teamSize) }
    var autoPickup by remember { mutableStateOf(api.match.autoPickup) }
    var weaponLimit by remember { mutableStateOf(api.match.weaponLimit) }
    var time by remember { mutableStateOf(api.match.timeLimit) }
    // Deathmatch: number of bots
    var bots by remember { mutableStateOf(MatchSettings.botCount.coerceIn(1, MatchSettings.MAX_BOTS)) }
    // Team Deathmatch / Capture the Flag: bots on each team
    var blue by remember { mutableStateOf(MatchSettings.blueBots.coerceIn(0, MatchSettings.MAX_BOTS - 1)) }
    var red by remember { mutableStateOf(MatchSettings.redBots.coerceIn(1, MatchSettings.MAX_BOTS - blue)) }
    var capIdx by remember { mutableStateOf(CAPTURE_OPTIONS.indexOf(api.match.captureLimit).coerceAtLeast(0)) }

    val mode = modes[modeIdx]
    val isCtf = mode == GameMode.CAPTURE_FLAG
    val teamBots = mode == GameMode.TEAM_DEATHMATCH || isCtf
    val botMode = mode == GameMode.DEATHMATCH || teamBots

    NfScreen(
        "MATCH SETUP",
        onBack,
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NfIcon(NfIconKind.GROUP, Modifier.size(18.dp), NfColors.TextDim)
                NfText(
                    if (teamBots) "${blue + red + 1}/${MatchSettings.MAX_BOTS + 1}"
                    else if (botMode) "${bots + 1}/${MatchSettings.MAX_BOTS + 1}"
                    else "4/16",
                    size = 13,
                    color = NfColors.TextDim
                )
            }
        }
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

            // Left: mode, map, bots, preview, invite
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Selector(
                    NfIconKind.TARGET,
                    "MODE",
                    modes[modeIdx].label,
                    modes[modeIdx].tagline,
                    onPrev = {
                        modeIdx = (modeIdx + modes.size - 1) % modes.size
                        api.match.mode = modes[modeIdx]
                    },
                    onNext = {
                        modeIdx = (modeIdx + 1) % modes.size
                        api.match.mode = modes[modeIdx]
                    }
                )
                Selector(
                    NfIconKind.GLOBE,
                    "MAP",
                    arenas[arenaIdx],
                    "Tap the arrows to change",
                    onPrev = {
                        arenaIdx = (arenaIdx + arenas.size - 1) % arenas.size
                        api.match.arena = arenas[arenaIdx]
                    },
                    onNext = {
                        arenaIdx = (arenaIdx + 1) % arenas.size
                        api.match.arena = arenas[arenaIdx]
                    }
                )
                if (teamBots) {
                    // Blue = your team (you + these bots). Red = the enemy team.
                    NfRow("Blue bots (you + $blue)") {
                        Stepper(
                            "$blue",
                            onMinus = {
                                if (blue > 0) {
                                    blue -= 1
                                    MatchSettings.blueBots = blue
                                }
                            },
                            onPlus = {
                                if (blue + red < MatchSettings.MAX_BOTS) {
                                    blue += 1
                                    MatchSettings.blueBots = blue
                                }
                            }
                        )
                    }
                    NfRow("Red bots (enemy)") {
                        Stepper(
                            "$red",
                            onMinus = {
                                if (red > 1) {
                                    red -= 1
                                    MatchSettings.redBots = red
                                }
                            },
                            onPlus = {
                                if (blue + red < MatchSettings.MAX_BOTS) {
                                    red += 1
                                    MatchSettings.redBots = red
                                }
                            }
                        )
                    }
                } else if (botMode) {
                    NfRow("Bots (you + $bots)") {
                        Stepper(
                            "$bots",
                            onMinus = {
                                if (bots > 1) {
                                    bots -= 1
                                    MatchSettings.botCount = bots
                                }
                            },
                            onPlus = {
                                if (bots < MatchSettings.MAX_BOTS) {
                                    bots += 1
                                    MatchSettings.botCount = bots
                                }
                            }
                        )
                    }
                }
                NfPanel(Modifier.fillMaxWidth().weight(1f)) {
                    ArtBlock(
                        Modifier.fillMaxSize(),
                        Color(0xFF3A2A6B),
                        Color(0xFF14183A),
                        arenas[arenaIdx].uppercase()
                    )
                }
                NfButton(
                    "INVITE FRIENDS",
                    Modifier.fillMaxWidth(),
                    style = BtnStyle.BLUE,
                    size = 13
                ) { onOpen("friends") }
            }

            // Right: players, game settings, start
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Solo / Duo / Squad is hidden in the team modes (the Blue / Red bot numbers decide the teams)
                if (!teamBots) {
                    NfSectionTitle("PLAYERS")
                    NfChoice(listOf("Solo", "Duo", "Squad"), team, { v ->
                        team = v
                        api.match.teamSize = v
                    })
                }
                NfSectionTitle("GAME SETTINGS")
                NfRow("Auto Pickup") {
                    NfToggle(autoPickup) { v ->
                        autoPickup = v
                        api.match.autoPickup = v
                    }
                }
                NfRow("Weapon Limit") {
                    NfToggle(weaponLimit) { v ->
                        weaponLimit = v
                        api.match.weaponLimit = v
                    }
                }
                NfRow("Time Limit") {
                    Stepper(
                        "$time min",
                        onMinus = {
                            if (time > 5) {
                                time -= 5
                                api.match.timeLimit = time
                            }
                        },
                        onPlus = {
                            if (time < 30) {
                                time += 5
                                api.match.timeLimit = time
                            }
                        }
                    )
                }
                if (isCtf) {
                    NfRow("Captures to win") {
                        Stepper(
                            if (CAPTURE_OPTIONS[capIdx] == 0) "No limit" else "${CAPTURE_OPTIONS[capIdx]}",
                            onMinus = {
                                if (capIdx > 0) {
                                    capIdx -= 1
                                    api.match.captureLimit = CAPTURE_OPTIONS[capIdx]
                                }
                            },
                            onPlus = {
                                if (capIdx < CAPTURE_OPTIONS.lastIndex) {
                                    capIdx += 1
                                    api.match.captureLimit = CAPTURE_OPTIONS[capIdx]
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                NfButton(
                    "START MATCH",
                    Modifier.fillMaxWidth().height(54.dp),
                    primary = true,
                    size = 22
                ) {
                    MatchSettings.botCount = bots
                    MatchSettings.blueBots = blue
                    MatchSettings.redBots = red
                    api.match.captureLimit = if (isCtf) CAPTURE_OPTIONS[capIdx] else 0
                    api.startMatch()
                    onOpen("hud")
                }
            }
        }
    }
}

@Composable
private fun ArrowBox(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(34.dp)
            .clip(shape)
            .background(Color(0x33FFFFFF))
            .border(1.dp, NfBrush.Glow, shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        NfText(text, size = 16, bold = true)
    }
}

@Composable
private fun Selector(
    icon: NfIconKind,
    title: String,
    value: String,
    hint: String,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    NfPanel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ArrowBox("<", onPrev)
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center
            ) { NfIcon(icon, Modifier.size(22.dp), NfColors.Text) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                NfText(title, size = 11, color = NfColors.Cyan, bold = true)
                NfText(value, size = 16, bold = true, align = TextAlign.Center)
                NfText(hint, size = 10, color = NfColors.TextDim, align = TextAlign.Center)
            }
            ArrowBox(">", onNext)
        }
    }
}

@Composable
private fun Stepper(text: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ArrowBox("-", onMinus)
        NfText(text, size = 13, bold = true)
        ArrowBox("+", onPlus)
    }
}
