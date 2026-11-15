package com.d200.nightfall3d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.game.StoreItem
import com.d200.nightfall3d.ui.theme.*

private fun tagColor(tag: String): Color = when (tag) {
    "Promo" -> NfColors.Red
    "Gift" -> Color(0xFFE0A100)
    "New" -> Color(0xFF2E9E5B)
    else -> NfColors.Purple
}

@Composable
fun StoreScreen(api: GameApi, onBack: () -> Unit) {
    val p = api.profile()
    val tabs = listOf("FEATURED", "WEAPONS", "CHARACTERS", "BUNDLES", "GIFT PACKS", "COINS", "DIAMONDS")
    var tab by remember { mutableStateOf(0) }
    val all = api.storeItems()
    val list = if (tab == 0) {
        all.filter { it.featured }
    } else {
        all.filter { it.category.equals(tabs[tab], ignoreCase = true) }
    }

    NfScreen(
        "STORE",
        onBack,
        p.coins,
        p.diamonds,
        actions = {
            NfButton("TOP UP", style = BtnStyle.VIOLET, size = 12) { tab = 6 }
        }
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NfSideTabs(tabs, tab, { i -> tab = i }, Modifier.width(130.dp).fillMaxHeight())
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (list.isEmpty()) {
                    NfText("Nothing here yet", color = NfColors.TextDim)
                }
                list.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { item -> StoreCard(item, Modifier.weight(1f).height(150.dp)) }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreCard(item: StoreItem, modifier: Modifier) {
    NfPanel(modifier) {
        ArtBlock(Modifier.fillMaxSize(), Color(0xFF3A2A6B), Color(0xFF14183A))
        GunSilhouette(
            Modifier.align(Alignment.Center).fillMaxWidth(0.7f).fillMaxHeight(0.4f)
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(60.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE0B0F24))))
        )
        if (item.tag.isNotEmpty()) {
            NfTag(item.tag, tagColor(item.tag), Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        if (item.badge.isNotEmpty()) {
            NfText(
                item.badge,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                size = 13,
                color = NfColors.Gold,
                bold = true
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            NfText(item.name, size = 13, bold = true)
            when (item.currency) {
                "diamonds" -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CoinDot(NfColors.Cyan, true)
                    NfText(item.price, size = 12, bold = true)
                }
                "coins" -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CoinDot(NfColors.Gold)
                    NfText(item.price, size = 12, bold = true)
                }
                else -> NfText(item.price, size = 12, bold = true, color = NfColors.Gold)
            }
        }
    }
}
