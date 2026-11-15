package com.d200.nightfall3d.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.ui.MainMenuScreen
import com.d200.nightfall3d.ui.theme.NfButton
import com.d200.nightfall3d.ui.theme.NfColors

@Composable
fun NavGraph(api: GameApi) {
    var route by remember { mutableStateOf("main") }

    BackHandler(enabled = route != "main") { route = "main" }

    when (route) {
        "main" -> MainMenuScreen(api) { route = it }
        // Each new screen gets its own line here as we build it
        else -> ComingSoon(route) { route = "main" }
    }
}

@Composable
private fun ComingSoon(name: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(NfColors.Bg),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            "$name - coming soon",
            style = TextStyle(color = NfColors.TextDim, fontSize = 18.sp)
        )
        NfButton("BACK", primary = true) { onBack() }
    }
}
