package com.d200.nightfall3d.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.d200.nightfall3d.game.GameItem
import com.d200.nightfall3d.ui.theme.*

private fun itemColor(category: String): Color = when (category) {
    "Grenades" -> Color(0xFF2E8B57)
    "Items" -> Color(0xFFC0392B)
    "Melee" -> Color(0xFF2E6BD6)
    else -> Color(0xFFD0702E)
}

@Composable
fun ItemsScreen(api: GameApi, onBack: () -> Unit) {
    val p = api.profile()
    val tabs = listOf("ITEMS", "GRENADES", "MELEE", "SPECIAL")
    var tab by remember { mutableStateOf(0) }
    var selId by remember { mutableStateOf("") }
    val all = api.items()
    val list = if (tab == 0) all else all.filter { it.category.equals(tabs[tab], ignoreCase = true) }
    val sel = all.firstOrNull { it.id == selId }

    NfScreen("OTHER ITEMS", onBack, p.coins, p.diamonds) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NfTabs(tabs, tab, { i -> tab = i })
            Row(
                Modifier.fillMaxWidth().weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                list.forEach { item ->
                    ItemCard(item, item.id == selId, Modifier.width(140.dp).fillMaxHeight()) {
                        selId = item.id
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NfText(
                    if (sel == null) "Tap an item to select it" else sel.name,
                    modifier = Modifier.weight(1f),
                    color = NfColors.TextDim
                )
                NfButton("BUY", style = BtnStyle.BLUE) { }
            }
        }
    }
}

@Composable
private fun ItemCard(item: GameItem, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    NfPanel(modifier, selected = selected, onClick = onClick) {
        ArtBlock(Modifier.fillMaxSize(), itemColor(item.category), Color(0xFF14183A), item.name.take(1))
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            NfText(item.name, size = 14, bold = true)
            NfPrice(item.priceCoins, NfColors.Gold, false)
        }
    }
}
