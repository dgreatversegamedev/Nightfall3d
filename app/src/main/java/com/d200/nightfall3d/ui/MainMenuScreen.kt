package com.d200.nightfall3d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.ui.theme.*

// Main menu built from the reference image (1672 x 941).
// Sizes and positions are in reference pixels, scaled by k.
//
// One grid, so nothing looks random:
//   left menu rows   250 x 56, pitch 64, x = 14
//   top icons        54 wide, pitch 88, right aligned
//   right tiles      140 x 170, pitch 150
//   bottom edge      915 for the bottom bar, the box and START
//   margins 14, gaps 12
// Icons are drawn in code (HudIcons.kt). Art images are only used for the background,
// banner, battle royale picture, booyah pass logo and the avatar.
// One colour system: navy glass, steel/white lines, Nightfall orange for highlights.

private val Navy = Color(0xEB0A1230)
private val Orange = Color(0xFFFF8A00)
private val OrangeDark = Color(0xFFC96A00)
private val RankBlue = Color(0xFF35B6FF)
private val Line = Color(0x33FFFFFF)
private val OrangeBrush = Brush.verticalGradient(listOf(Color(0xFFFFB347), Orange, Color(0xFFD96A00)))

/** Only routes that already exist in the app are opened. New buttons are UI only for now. */
private val LIVE_ROUTES = setOf(
    "store", "luck", "character", "weapons", "vault", "pet", "missions", "events",
    "mail", "settings", "battlepass", "gifts", "friends", "play"
)

private class MenuEntry(
    val label: String,
    val route: String,
    val glyph: Glyph,
    val hot: Boolean,
    val badge: Boolean
)

private val entries = listOf(
    MenuEntry("Store", "store", Glyph.STORE, true, true),
    MenuEntry("Luck Royale", "luck", Glyph.LUCK, true, true),
    MenuEntry("Character", "character", Glyph.CHARACTER, false, false),
    MenuEntry("Vault", "vault", Glyph.VAULT, false, false),
    MenuEntry("Weapons", "weapons", Glyph.PISTOL, false, false),
    MenuEntry("Pet", "pet", Glyph.WOLF, false, false),
    MenuEntry("Missions", "missions", Glyph.MISSIONS, false, false),
    MenuEntry("Events", "events", Glyph.EVENTS, false, true)
)

private const val ROW_X = 14f
private const val ROW_Y = 272f
private const val ROW_W = 250f
private const val ROW_H = 56f
private const val ROW_PITCH = 64f

private class BarItem(val glyph: Glyph, val label: String, val route: String)

private val barItems = listOf(
    BarItem(Glyph.CROWN, "RANK", "rank"),
    BarItem(Glyph.GEAR, "SETTINGS", "settings"),
    BarItem(Glyph.INVENTORY, "INVENTORY", "inventory"),
    BarItem(Glyph.PRESET, "PRESET", "preset"),
    BarItem(Glyph.LAB, "LAB", "lab")
)

// ------------------------------------------------------------------ entry point

@Composable
fun MainMenuScreen(api: GameApi, onOpen: (String) -> Unit) {
    val p = api.profile()
    val go: (String) -> Unit = { r -> if (r in LIVE_ROUTES) onOpen(r) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(NfBrush.Screen)
            .windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        if (maxWidth >= maxHeight) {
            val k = minOf(maxWidth.value / REF_W, maxHeight.value / REF_H)
            Box(Modifier.align(Alignment.Center)) {
                MenuFrame(k, p.name, p.level, p.coins, p.diamonds, go)
            }
        } else {
            PortraitMenu(maxWidth.value / 800f, p.name, p.level, p.coins, p.diamonds, go)
        }
    }
}

// ------------------------------------------------------------------ landscape

/** Currency pill: drawn icon, value, orange plus. */
@Composable
private fun CurrencyPill(k: Float, modifier: Modifier, glyph: Glyph, value: String) {
    val shape = RoundedCornerShape((6 * k).dp)
    Row(
        modifier
            .sized(k, 260f, 52f)
            .clip(shape)
            .background(Navy)
            .border((1.5f * k).dp, Line, shape)
            .padding(horizontal = (12 * k).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(glyph, Modifier.sized(k, 38f, 38f))
        Spacer(Modifier.width((10 * k).dp))
        NfText(value, Modifier.weight(1f), size = fsz(k, 30f), bold = true)
        NfText("+", size = fsz(k, 32f), bold = true, color = Orange)
    }
}

/** 54 x 54 top bar button with a drawn icon and an optional orange dot. */
@Composable
private fun TopIcon(k: Float, modifier: Modifier, glyph: Glyph, dot: Boolean = false, onClick: () -> Unit = {}) {
    Box(modifier.sized(k, 54f, 54f).clickable { onClick() }, contentAlignment = Alignment.Center) {
        GlyphIcon(glyph, Modifier.sized(k, 38f, 38f))
        if (dot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = (4 * k).dp, end = (4 * k).dp)
                    .size((12 * k).dp)
                    .clip(CircleShape)
                    .background(Orange)
            )
        }
    }
}

@Composable
private fun MenuFrame(k: Float, name: String, level: Int, coins: Int, diamonds: Int, go: (String) -> Unit) {
    Box(Modifier.sized(k, REF_W, REF_H)) {
        if (drawableId("menu_bg") != 0) {
            ArtImage("menu_bg", Modifier.fillMaxSize(), ContentScale.FillBounds)
        } else {
            Box(Modifier.fillMaxSize().background(NfBrush.Screen))
            CharacterSilhouette(Modifier.align(Alignment.Center).fillMaxHeight(0.8f).width((300 * k).dp))
        }

        // ---------- top bar (full width, 76 high)
        Box(
            atTL(k, 0f, 0f)
                .sized(k, REF_W, 76f)
                .clip(CutCornerShape(bottomEnd = (46 * k).dp))
                .background(Brush.horizontalGradient(listOf(Color(0xF2060A1E), Color(0xE60F1738))))
        )
        Box(
            atTL(k, 0f, 0f)
                .sized(k, 378f, 76f)
                .clip(CutCornerShape(bottomEnd = (22 * k).dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF0E1A44), Color(0xFF1B2C74))))
        )
        Box(atTL(k, 0f, 74f).sized(k, 378f, 2f).background(Orange))
        Box(
            atTL(k, 14f, 5f)
                .sized(k, 66f, 66f)
                .clip(RoundedCornerShape((4 * k).dp))
                .border((2 * k).dp, Color.White, RoundedCornerShape((4 * k).dp))
        ) { ArtImage("avatar", Modifier.fillMaxSize(), ContentScale.Crop) }
        Row(atTL(k, 96f, 10f), verticalAlignment = Alignment.CenterVertically) {
            NfText(name, size = fsz(k, 28f), bold = true)
            Spacer(Modifier.width((6 * k).dp))
            NfText("\u2605", size = fsz(k, 24f), color = NfColors.Gold)
        }
        NfText("Lv. $level", atTL(k, 96f, 44f), size = fsz(k, 22f), color = NfColors.TextDim)
        GlyphIcon(Glyph.RANKBADGE, atTL(k, 290f, 8f).sized(k, 60f, 60f))

        CurrencyPill(k, atTL(k, 398f, 12f), Glyph.COIN, formatNum(coins))
        CurrencyPill(k, atTL(k, 676f, 12f), Glyph.GEM, formatNum(diamonds))

        // six icons on one 88 px pitch, right aligned
        GlyphIcon(Glyph.MEMBER, atTL(k, 1060f, 6f).sized(k, 70f, 70f))
        TopIcon(k, atTL(k, 1156f, 11f), Glyph.GIFT, true) { go("gifts") }
        TopIcon(k, atTL(k, 1244f, 11f), Glyph.PLAYERS) { go("friends") }
        TopIcon(k, atTL(k, 1332f, 11f), Glyph.MAIL, true) { go("mail") }
        TopIcon(k, atTL(k, 1420f, 11f), Glyph.GEAR) { go("settings") }
        TopIcon(k, atTL(k, 1508f, 11f), Glyph.WIFI)
        TopIcon(k, atTL(k, 1596f, 11f), Glyph.BATTERY)

        // ---------- left: banner, equal menu rows, chat strip
        MenuBanner(k, atTL(k, ROW_X, 100f)) { go("battlepass") }
        entries.forEachIndexed { i, e ->
            MenuRow(k, e, atTL(k, ROW_X, ROW_Y + i * ROW_PITCH)) { go(e.route) }
        }
        ChatStrip(k, atTL(k, ROW_X, 788f))

        // ---------- right: three equal tiles
        GiftTile(k, atTL(k, 1218f, 90f)) { go("gifts") }
        TopUpTile(k, atTL(k, 1368f, 90f))
        Box(atTL(k, 1518f, 90f).sized(k, 140f, 170f)) {
            ArtImage("art_booyah", Modifier.fillMaxSize())
        }

        // ---------- right: mode card, ranked, box + START
        BrCard(k, atTL(k, 1313f, 300f)) { go("play") }
        RankedCard(k, atTL(k, 1237f, 706f), { go("play") }, { go("friends") })
        MenuBox(k, atTL(k, 1214f, 809f))
        StartButton(k, atTL(k, 1335f, 809f)) { go("play") }

        // ---------- bottom bar
        BottomBar(k, atTL(k, 14f, 857f).sized(k, 1074f, 58f), go)
    }
}

// ------------------------------------------------------------------ portrait

@Composable
private fun PortraitMenu(k: Float, name: String, level: Int, coins: Int, diamonds: Int, go: (String) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (drawableId("menu_bg") != 0) {
            ArtImage("menu_bg", Modifier.fillMaxSize(), ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color(0x40000000)))
        } else {
            Box(Modifier.fillMaxSize().background(NfBrush.Screen))
            CharacterSilhouette(Modifier.align(Alignment.Center).fillMaxHeight(0.6f).width((300 * k).dp))
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = (12 * k).dp, vertical = (8 * k).dp)
        ) {
            // top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .height((64 * k).dp)
                    .clip(RoundedCornerShape((6 * k).dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF0E1A44), Color(0xE60F1738))))
                    .padding(horizontal = (8 * k).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .sized(k, 52f, 52f)
                        .clip(RoundedCornerShape((4 * k).dp))
                        .border((2 * k).dp, Color.White, RoundedCornerShape((4 * k).dp))
                ) { ArtImage("avatar", Modifier.fillMaxSize(), ContentScale.Crop) }
                Spacer(Modifier.width((8 * k).dp))
                Column {
                    NfText(name, size = fsz(k, 26f), bold = true)
                    NfText("Lv. $level", size = fsz(k, 20f), color = NfColors.TextDim)
                }
                Spacer(Modifier.weight(1f))
                GlyphIcon(Glyph.COIN, Modifier.sized(k, 32f, 32f))
                Spacer(Modifier.width((4 * k).dp))
                NfText(formatNum(coins) + " +", size = fsz(k, 22f), bold = true)
                Spacer(Modifier.width((10 * k).dp))
                GlyphIcon(Glyph.GEM, Modifier.sized(k, 32f, 32f))
                Spacer(Modifier.width((4 * k).dp))
                NfText(formatNum(diamonds) + " +", size = fsz(k, 22f), bold = true)
                Spacer(Modifier.width((10 * k).dp))
                GlyphIcon(Glyph.MAIL, Modifier.sized(k, 40f, 40f).clickable { go("mail") })
                Spacer(Modifier.width((10 * k).dp))
                GlyphIcon(Glyph.GEAR, Modifier.sized(k, 40f, 40f).clickable { go("settings") })
            }
            Spacer(Modifier.height((12 * k).dp))

            // banner + gifts + top-up
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                MenuBanner(k, Modifier) { go("battlepass") }
                Spacer(Modifier.weight(1f))
                GiftTile(k, Modifier) { go("gifts") }
                Spacer(Modifier.width((10 * k).dp))
                TopUpTile(k, Modifier)
            }
            Spacer(Modifier.height((12 * k).dp))

            // menu rows (left) + booyah pass and mode card (right)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp * k)) {
                    entries.forEach { e -> MenuRow(k, e, Modifier) { go(e.route) } }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy((12 * k).dp)) {
                    Box(Modifier.sized(k, 140f, 170f)) { ArtImage("art_booyah", Modifier.fillMaxSize()) }
                    BrCard(k, Modifier) { go("play") }
                }
            }

            Spacer(Modifier.weight(1f))

            // bottom: ranked, box + START, bar
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                RankedCard(k, Modifier, { go("play") }, { go("friends") })
                Spacer(Modifier.height((12 * k).dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MenuBox(k, Modifier)
                    Spacer(Modifier.width((14 * k).dp))
                    StartButton(k, Modifier) { go("play") }
                }
                Spacer(Modifier.height((12 * k).dp))
                BottomBar(k, Modifier.fillMaxWidth().height((58 * k).dp), go)
            }
        }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun MenuBanner(k: Float, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape((6 * k).dp)
    Box(
        modifier
            .sized(k, 440f, 160f)
            .clip(shape)
            .border((1.5f * k).dp, Line, shape)
            .clickable { onClick() }
    ) { ArtImage("menu_banner", Modifier.fillMaxSize(), ContentScale.FillBounds) }
}

/** Every menu row has the same size. Only Store and Luck Royale are highlighted. */
@Composable
private fun MenuRow(k: Float, e: MenuEntry, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape((5 * k).dp)
    Box(
        modifier
            .sized(k, ROW_W, ROW_H)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xF20A0E22), Color(0xD00A0E22))))
            .border((1.5f * k).dp, if (e.hot) Orange else Line, shape)
            .clickable { onClick() }
    ) {
        Box(
            Modifier.align(Alignment.CenterStart).padding(start = (14 * k).dp).sized(k, 40f, 40f),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(e.glyph, Modifier.sized(k, 38f, 38f), main = if (e.hot) Orange else Color.White, accent = if (e.hot) Color.White else Orange)
        }
        NfText(
            e.label.uppercase(),
            Modifier.align(Alignment.CenterStart).padding(start = (68 * k).dp),
            size = fsz(k, 26f),
            bold = true,
            color = if (e.hot) Orange else Color.White
        )
        if (e.badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = (6 * k).dp, end = (8 * k).dp)
                    .size((11 * k).dp)
                    .clip(CircleShape)
                    .background(Orange)
            )
        }
    }
}

@Composable
private fun ChatStrip(k: Float, modifier: Modifier) {
    val shape = RoundedCornerShape((5 * k).dp)
    Row(
        modifier
            .sized(k, 520f, 54f)
            .clip(shape)
            .background(Navy)
            .border((1.5f * k).dp, Line, shape)
            .padding(horizontal = (14 * k).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(Glyph.CHAT, Modifier.sized(k, 34f, 34f))
        Spacer(Modifier.width((14 * k).dp))
        NfText("[World] Kingz: Let's squad up!", size = fsz(k, 22f), maxLines = 1)
    }
}

@Composable
private fun LabelChip(k: Float, modifier: Modifier, text: String, color: Color) {
    val shape = RoundedCornerShape((4 * k).dp)
    Box(
        modifier.clip(shape).background(Navy).border((1.dp), Line, shape),
        contentAlignment = Alignment.Center
    ) { NfText(text, size = fsz(k, 19f), bold = true, color = color) }
}

/** Right side tiles share one size: 140 x 170. */
@Composable
private fun GiftTile(k: Float, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.sized(k, 140f, 170f).clickable { onClick() }) {
        GlyphIcon(Glyph.GIFT, Modifier.align(Alignment.TopCenter).sized(k, 96f, 96f))
        LabelChip(
            k,
            Modifier.align(Alignment.TopCenter).offset(0.dp, (104 * k).dp).sized(k, 128f, 30f),
            "FREE GIFTS",
            Orange
        )
    }
}

@Composable
private fun TopUpTile(k: Float, modifier: Modifier) {
    val shape = RoundedCornerShape((4 * k).dp)
    Box(modifier.sized(k, 140f, 170f)) {
        GlyphIcon(Glyph.CROWN, Modifier.align(Alignment.TopCenter).sized(k, 96f, 96f))
        LabelChip(
            k,
            Modifier.align(Alignment.TopCenter).offset(0.dp, (104 * k).dp).sized(k, 128f, 30f),
            "TOP-UP",
            Color.White
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(0.dp, (140 * k).dp)
                .sized(k, 128f, 30f)
                .clip(shape)
                .background(Navy)
                .border((1.dp), Line, shape)
        ) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.CLOCK, Modifier.size((18 * k).dp))
                Spacer(Modifier.width((6 * k).dp))
                NfText("23:45:12", size = fsz(k, 19f), bold = true)
            }
        }
    }
}

@Composable
private fun BrCard(k: Float, modifier: Modifier, onClick: () -> Unit) {
    val shape = CutCornerShape(topStart = (34 * k).dp)
    Box(
        modifier
            .sized(k, 345f, 255f)
            .clip(shape)
            .background(Navy)
            .border((1.5f * k).dp, Orange, shape)
            .clickable { onClick() }
    ) {
        ArtImage(
            "menu_br_art",
            Modifier.align(Alignment.TopStart).fillMaxWidth().height((186 * k).dp),
            ContentScale.Crop
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height((69 * k).dp)
                .background(Color(0xF20B0F24))
        ) {
            Column(Modifier.align(Alignment.CenterStart).padding(start = (16 * k).dp)) {
                NfText("BATTLE ROYALE", size = fsz(k, 27f), bold = true, color = Orange)
                NfText("Random Map", size = fsz(k, 21f), color = Color.White)
            }
            GlyphIcon(
                Glyph.CHEVRON,
                Modifier.align(Alignment.CenterEnd).padding(end = (16 * k).dp).size((26 * k).dp),
                main = Orange
            )
        }
    }
}

@Composable
private fun RankedCard(k: Float, modifier: Modifier, onPlay: () -> Unit, onFriends: () -> Unit) {
    val shape = RoundedCornerShape((6 * k).dp)
    Box(
        modifier
            .sized(k, 420f, 91f)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xF20A0E22), Color(0xE6101838))))
            .border((1.5f * k).dp, Color(0x6635B6FF), shape)
            .clickable { onPlay() }
    ) {
        GlyphIcon(
            Glyph.PLAYERS,
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = (24 * k).dp)
                .sized(k, 70f, 70f)
                .clickable { onFriends() },
            main = Color.White
        )
        Column(Modifier.align(Alignment.CenterStart).padding(start = (118 * k).dp)) {
            NfText("BR-RANKED", size = fsz(k, 29f), bold = true, color = RankBlue)
            NfText("Random Map", size = fsz(k, 22f), color = Color.White)
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = (112 * k).dp)
                .width(1.dp)
                .height((60 * k).dp)
                .background(Line)
        )
        GlyphIcon(
            Glyph.RANKBADGE,
            Modifier.align(Alignment.CenterEnd).padding(end = (22 * k).dp).sized(k, 70f, 70f)
        )
    }
}

@Composable
private fun MenuBox(k: Float, modifier: Modifier) {
    val shape = RoundedCornerShape((8 * k).dp)
    Box(
        modifier
            .sized(k, 106f, 106f)
            .clip(shape)
            .background(Navy)
            .border((2 * k).dp, Orange, shape),
        contentAlignment = Alignment.Center
    ) { GlyphIcon(Glyph.CUBE, Modifier.sized(k, 72f, 72f)) }
}

@Composable
private fun StartButton(k: Float, modifier: Modifier, onClick: () -> Unit) {
    val slant = SlantShape((26 * k).dp)
    Box(
        modifier
            .sized(k, 322f, 106f)
            .drawBehind {
                drawRoundRect(
                    Orange.copy(alpha = 0.22f),
                    Offset(-8f, -8f),
                    Size(size.width + 16f, size.height + 16f),
                    CornerRadius(30f)
                )
            }
            .clip(slant)
            .background(OrangeBrush)
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0x44FFFFFF), Color.Transparent),
                        endY = size.height * 0.5f
                    ),
                    size = Size(size.width, size.height * 0.5f)
                )
            }
            .border(
                (2 * k).dp,
                Brush.verticalGradient(listOf(Color(0xAAFFFFFF), Color(0x44FF8A00))),
                slant
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        NfText("START", size = fsz(k, 62f), bold = true, italic = true, color = Color(0xFF140A00))
    }
}

@Composable
private fun BottomBar(k: Float, modifier: Modifier, go: (String) -> Unit) {
    val shape = RoundedCornerShape((6 * k).dp)
    Row(
        modifier
            .clip(shape)
            .background(Navy)
            .border(1.dp, Line, shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        barItems.forEachIndexed { i, item ->
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { go(item.route) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlyphIcon(item.glyph, Modifier.size((36 * k).dp))
                Spacer(Modifier.width((10 * k).dp))
                NfText(item.label, size = fsz(k, 24f), bold = true)
            }
            if (i < barItems.size - 1) {
                Box(Modifier.width(1.dp).height((30 * k).dp).background(Line))
            }
        }
    }
}
