package com.d200.nightfall3d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.d200.nightfall3d.game.Friend
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.game.PlayType
import com.d200.nightfall3d.ui.theme.*

@Composable
fun FriendsScreen(api: GameApi, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val tabs = listOf("ONLINE", "OFFLINE", "INVITE")
    var tab by remember { mutableStateOf(0) }

    val startRoom: () -> Unit = {
        api.match.playType = PlayType.FRIENDS
        onOpen("matchsetup")
    }

    NfScreen("PLAY WITH FRIENDS", onBack) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NfSideTabs(tabs, tab, { i -> tab = i }, Modifier.width(120.dp).fillMaxHeight())

            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NfPanel(Modifier.fillMaxWidth().weight(1f)) {
                    Column(
                        Modifier.fillMaxSize().padding(10.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (tab == 0) {
                            api.friends().forEach { f -> FriendRow(f, startRoom) }
                        } else if (tab == 1) {
                            NfText("OFFLINE MULTIPLAYER", color = NfColors.Cyan, bold = true)
                            NfText(
                                "Play with friends on the same Wi-Fi or hotspot. One phone creates the room and the others join it. No internet needed.",
                                size = 13,
                                color = NfColors.TextDim
                            )
                        } else {
                            NfText("INVITE CODE", color = NfColors.Cyan, bold = true)
                            NfText("NF3D-4821", size = 28, bold = true, color = NfColors.Gold)
                            NfText(
                                "Share this code with your friends so they can join your room.",
                                size = 13,
                                color = NfColors.TextDim
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NfButton("CREATE ROOM", Modifier.weight(1f), style = BtnStyle.VIOLET) { startRoom() }
                    NfButton("QUICK JOIN", Modifier.weight(1f), primary = true) { startRoom() }
                }
            }

            // Right: decoration
            Box(Modifier.width(230.dp).fillMaxHeight()) {
                CharacterSilhouette(
                    Modifier
                        .align(Alignment.Center)
                        .offset(x = (-34).dp)
                        .fillMaxHeight(0.8f)
                        .width(130.dp),
                    NfColors.Purple
                )
                CharacterSilhouette(
                    Modifier
                        .align(Alignment.Center)
                        .offset(x = 34.dp)
                        .fillMaxHeight(0.8f)
                        .width(130.dp),
                    NfColors.Accent
                )
                NfText(
                    "PLAY TOGETHER!",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                    size = 20,
                    bold = true,
                    color = NfColors.Gold
                )
            }
        }
    }
}

@Composable
private fun FriendRow(f: Friend, onInvite: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NfBrush.Row)
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(NfBrush.Violet),
            contentAlignment = Alignment.Center
        ) {
            NfText(f.name.take(1), bold = true)
        }
        Column(Modifier.weight(1f)) {
            NfText(f.name, bold = true)
            NfText(
                "● " + f.status,
                size = 11,
                color = if (f.status == "Online") NfColors.Green else NfColors.Cyan
            )
        }
        NfButton("INVITE", primary = true, size = 12) { onInvite() }
    }
}
