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
import com.d200.nightfall3d.game.MatchConfig
import com.d200.nightfall3d.game.MatchSettings
import com.d200.nightfall3d.ui.theme.*

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
    // number of bots in the match (Deathmatch only)
    var bots by remember { mutableStateOf(MatchSettings.botCount.coerceIn(1, MatchSettings.MAX_BOTS)) }
    // team sizes (Team Deathmatch, Capture the Flag, Zombies). myTeam includes you.
    var myTeam by remember { mutableStateOf(api.match.myTeam.coerceIn(1, MatchConfig.MAX_TEAM)) }
    var otherTeam by remember { mutableStateOf(api.match.otherTeam.coerceIn(1, MatchConfig.MAX_TEAM)) }

    val mode = modes[modeIdx]
    val isDeathmatch = mode == GameMode.DEATHMATCH
    val isTeamMode = mode == GameMode.TEAM_DEATHMATCH || mode == GameMode.CAPTURE_FLAG
    val isZombies = mode == GameMode.ZOMBIES

    // Keeps the old bot count in step with the chosen mode and team sizes,
    // so the match that starts always has the right number of bots.
    fun applyBots() {
        api.match.myTeam = myTeam
        api.match.otherTeam = otherTeam
        when (modes[modeIdx]) {
            GameMode.DEATHMATCH ->
                MatchSettings.botCount = bots.coerceIn(1, MatchSettings.MAX_BOTS)
            GameMode.TEAM_DEATHMATCH, GameMode.CAPTURE_FLAG ->
                // your teammates (everyone on your team except you) + the other team
                MatchSettings.botCount = ((myTeam - 1) + otherTeam).coerceIn(1, MatchSettings.MAX_BOTS)
            GameMode.ZOMBIES ->
                // your teammates only, the zombies come later
                MatchSettings.botCount = (myTeam - 1).coerceIn(0, MatchSettings.MAX_BOTS)
            else -> {}
        }
    }

    val counterText = when {
        isDeathmatch -> "${bots + 1}/${MatchSettings.MAX_BOTS + 1}"
        isTeamMode -> "${myTeam + otherTeam}/${MatchConfig.MAX_TEAM * 2}"
        isZombies -> "$myTeam/${MatchConfig.MAX_TEAM}"
        else -> "4/16"
    }

    NfScreen(
        "MATCH SETUP",
        onBack,
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NfIcon(NfIconKind.GROUP, Modifier.size(18.dp), NfColors.TextDim)
                NfText(counterText, size = 13, color = NfColors.TextDim)
            }
        }
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

            // Left: mode, map, bots / teams, preview, invite
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Selector(
                    NfIconKind.TARGET,
                    "MODE",
                    mode.label,
                    mode.tagline,
                    onPrev = {
                        modeIdx = (modeIdx + modes.size - 1) % modes.size
                        api.match.mode = modes[modeIdx]
                        applyBots()
                    },
                    onNext = {
                        modeIdx = (modeIdx + 1) % modes.size
                        api.match.mode = modes[modeIdx]
                        applyBots()
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

                // Deathmatch: just a bot count
                if (isDeathmatch) {
                    NfRow("Bots (you + $bots)") {
                        Stepper(
                            "$bots",
                            onMinus = {
                                if (bots > 1) {
                                    bots -= 1
                                    applyBots()
                                }
                            },
                            onPlus = {
                                if (bots < MatchSettings.MAX_BOTS) {
                                    bots += 1
                                    applyBots()
                                }
                            }
                        )
                    }
                }

                // Team Deathmatch, Capture the Flag and Zombies: how many players on your team
                if (isTeamMode || isZombies) {
                    NfRow("Your team (you + ${myTeam - 1})") {
                        Stepper(
                            "$myTeam",
                            onMinus = {
                                if (myTeam > 1) {
                                    myTeam -= 1
                                    applyBots()
                                }
                            },
                            onPlus = {
                                if (myTeam < MatchConfig.MAX_TEAM) {
                                    myTeam += 1
                                    applyBots()
                                }
                            }
                        )
                    }
                }

                // Team Deathmatch and Capture the Flag: how many players on the other team
                if (isTeamMode) {
                    NfRow("Other team") {
                        Stepper(
                            "$otherTeam",
                            onMinus = {
                                if (otherTeam > 1) {
                                    otherTeam -= 1
                                    applyBots()
                                }
                            },
                            onPlus = {
                                if (otherTeam < MatchConfig.MAX_TEAM) {
                                    otherTeam += 1
                                    applyBots()
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
                NfSectionTitle("PLAYERS")
                NfChoice(listOf("Solo", "Duo", "Squad"), team, { v ->
                    team = v
                    api.match.teamSize = v
                })
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
                Spacer(Modifier.weight(1f))
                NfButton(
                    "START MATCH",
                    Modifier.fillMaxWidth().height(54.dp),
                    primary = true,
                    size = 22
                ) {
                    applyBots()
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
