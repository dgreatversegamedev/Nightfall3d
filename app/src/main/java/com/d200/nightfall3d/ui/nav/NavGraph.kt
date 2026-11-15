package com.d200.nightfall3d.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.ui.CharacterScreen
import com.d200.nightfall3d.ui.FriendsScreen
import com.d200.nightfall3d.ui.HudScreen
import com.d200.nightfall3d.ui.ItemsScreen
import com.d200.nightfall3d.ui.MainMenuScreen
import com.d200.nightfall3d.ui.MatchSetupScreen
import com.d200.nightfall3d.ui.PlayModeScreen
import com.d200.nightfall3d.ui.SettingsScreen
import com.d200.nightfall3d.ui.StoreScreen
import com.d200.nightfall3d.ui.WeaponsScreen
import com.d200.nightfall3d.ui.theme.NfBrush
import com.d200.nightfall3d.ui.theme.NfButton
import com.d200.nightfall3d.ui.theme.NfColors
import com.d200.nightfall3d.ui.theme.NfText

@Composable
fun NavGraph(api: GameApi) {
    val stack = remember { mutableStateListOf("main") }
    val route = stack.last()

    val open: (String) -> Unit = { r -> stack.add(r) }
    val back: () -> Unit = {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        }
    }
    val home: () -> Unit = {
        stack.clear()
        stack.add("main")
    }

    BackHandler(enabled = stack.size > 1) { back() }

    when (route) {
        "main" -> MainMenuScreen(api, open)
        "play" -> PlayModeScreen(api, back, open)
        "friends" -> FriendsScreen(api, back, open)
        "matchsetup" -> MatchSetupScreen(api, back, open)
        "character" -> CharacterScreen(api, back)
        "weapons" -> WeaponsScreen(api, back, open)
        "items" -> ItemsScreen(api, back)
        "store" -> StoreScreen(api, back)
        "settings" -> SettingsScreen(api, back)
        "hud" -> HudScreen(api, home)
        else -> ComingSoon(route, back)
    }
}

@Composable
private fun ComingSoon(name: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(NfBrush.Screen),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NfText(name.uppercase() + " - coming soon", size = 18, color = NfColors.TextDim)
        NfButton("BACK", primary = true) { onBack() }
    }
}
