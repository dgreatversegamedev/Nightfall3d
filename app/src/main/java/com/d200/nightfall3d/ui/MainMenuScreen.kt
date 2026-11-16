package com.d200.nightfall3d.ui

import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.ui.theme.*

// Main menu built from the reference image (1672 x 941).
// Sizes and positions are in reference pixels, scaled by k.
//
// One grid, so nothing looks random:
//   left menu rows   250 x 56, pitch 64, x = 14  (slanted like START, text only, no icons)
//   top icons        54 wide, pitch 88, right aligned
//   right tiles      140 x 170, pitch 150
//   bottom edge      915 for the bottom bar and START
//   margins 14, gaps 12
// Icons are drawn in code (HudIcons.kt). Art images are only used for the background,
// banner, battle royale picture, booyah pass logo and the avatar.
// One colour system: navy glass, steel/white lines, Nightfall orange for highlights.
//
// CHANGES IN THIS VERSION
//  1) Left menu rows (Store ... Events) are slanted like the START button, same size, and have no icons.
//  2) The chat strip ("[World] Kingz: Let's squad up!") is removed.
//  3) The box button next to START is removed.
//  4) START has no curved glow behind it any more, only the slanted shape.

// ------------------------------------------------------------------ layout helpers
// If any of these already exist elsewhere in the package, delete the copy here.

const val REF_W = 1672f
const val REF_H = 941f

/** Size in reference pixels, scaled by k. */
fun Modifier.sized(k: Float, w: Float, h: Float): Modifier =
    this.size((w * k).dp, (h * k).dp)

/** Position from the top-left corner in reference pixels, scaled by k. */
fun atTL(k: Float, x: Float, y: Float): Modifier =
    Modifier.offset((x * k).dp, (y * k).dp)

/*
 * Corner anchors used by HudScreen. Values are reference pixels (x, y = top-left of the item
 * in the 1672 x 941 reference image, w / h = its size). They work inside a Box.
 */

/** Top right, by distance from the right edge (x) and from the top (y). */
fun BoxScope.edgeTR(k: Float, x: Float, y: Float): Modifier =
    Modifier.align(Alignment.TopEnd).offset((-x * k).dp, (y * k).dp)

fun BoxScope.atTR(k: Float, x: Float, y: Float, w: Float): Modifier =
    Modifier.align(Alignment.TopEnd).offset((-(REF_W - x - w) * k).dp, (y * k).dp)

fun BoxScope.atBL(k: Float, x: Float, y: Float, h: Float): Modifier =
    Modifier.align(Alignment.BottomStart).offset((x * k).dp, (-(REF_H - y - h) * k).dp)

fun BoxScope.atBR(k: Float, x: Float, y: Float, w: Float, h: Float): Modifier =
    Modifier.align(Alignment.BottomEnd).offset((-(REF_W - x - w) * k).dp, (-(REF_H - y - h) * k).dp)

fun BoxScope.atBC(k: Float, x: Float, y: Float, w: Float, h: Float): Modifier =
    Modifier.align(Alignment.BottomCenter).offset(((x + w / 2f - REF_W / 2f) * k).dp, (-(REF_H - y - h) * k).dp)

/** Font size in reference pixels, scaled by k. */
fun fsz(k: Float, px: Float): Int = (px * k).toInt()

/** Drawable resource id by name, 0 if the image is not in res/drawable. */
@Composable
fun drawableId(name: String): Int {
    val ctx = LocalContext.current
    return ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
}

/** Draws a drawable by name. Draws nothing if the image does not exist. */
@Composable
fun ArtImage(name: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val id = drawableId(name)
    if (id != 0) {
        Image(painterResource(id), null, modifier, contentScale = contentScale)
    }
}

private val Navy = Color(0xEB0A1230)
private val Orange = Color(0xFFFF8A00)
private val OrangeDark = Color(0xFFC96A00)
private val RankBlue = Color(0xFF35B6FF)
private val Line = Color(0x4DFFFFFF)
/** Glass morphism: frosted white tint over the scene, thin light border. */
private val GlassBrush = Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x14FFFFFF)))
private val OrangeBrush = Brush.verticalGradient(listOf(Color(0xFFFFB347), Orange, Color(0xFFD96A00)))

/** Only routes that already exist in the app are opened. New buttons are UI only for now. */
private val LIVE_ROUTES = setOf(
    "store", "luck", "character", "weapons", "vault", "pet", "missions", "events",
    "mail", "settings", "battlepass", "gifts", "friends", "play"
)

private class MenuEntry(
    val label: String,
    val route: String,
    val hot: Boolean,
    val badge: Boolean
)

private val entries = listOf(
    MenuEntry("Store", "store", true, true),
    MenuEntry("Luck Royale", "luck", true, true),
    MenuEntry("Character", "character", false, false),
    MenuEntry("Vault", "vault", false, false),
    MenuEntry("Weapons", "weapons", false, false),
    MenuEntry("Pet", "pet", false, false),
    MenuEntry("Missions", "missions", false, false),
    MenuEntry("Events", "events", false, true)
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
            MenuFrame(k, p.name, p.level, p.coins, p.diamonds, go)
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
            .background(GlassBrush)
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
    Box(Modifier.fillMaxSize()) {
        if (drawableId("menu_bg") != 0) {
            ArtImage("menu_bg", Modifier.fillMaxSize(), ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(NfBrush.Screen))
            CharacterSilhouette(Modifier.align(Alignment.Center).fillMaxHeight(0.8f).width((300 * k).dp))
        }

        // ---------- top bar: simple glass strip across the whole width
        Box(
            Modifier
                .fillMaxWidth()
                .height((76 * k).dp)
                .background(Brush.horizontalGradient(listOf(Color(0xCC060A1E), Color(0x99101838))))
        )
        Box(atTL(k, 0f, 75f).fillMaxWidth().height((1 * k).dp).background(Line))
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

        CurrencyPill(k, atTL(k, 398f, 12f), Glyph.COIN, formatNum(coins))
        CurrencyPill(k, atTL(k, 676f, 12f), Glyph.GEM, formatNum(diamonds))

        // four icons, pinned to the right edge
        Row(
            edgeTR(k, 14f, 11f),
            horizontalArrangement = Arrangement.spacedBy((34 * k).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopIcon(k, Modifier, Glyph.GIFT, true) { go("gifts") }
            TopIcon(k, Modifier, Glyph.PLAYERS) { go("friends") }
            TopIcon(k, Modifier, Glyph.MAIL, true) { go("mail") }
            TopIcon(k, Modifier, Glyph.GEAR) { go("settings") }
        }

        // ---------- left: banner and the slanted menu rows
        MenuBanner(k, atTL(k, ROW_X, 100f)) { go("battlepass") }
        entries.forEachIndexed { i, e ->
            MenuRow(k, e, atTL(k, ROW_X, ROW_Y + i * ROW_PITCH)) { go(e.route) }
        }

        // ---------- right: two small tiles, pinned to the right edge
        GiftTile(k, atTR(k, 1448f, 90f, 104f)) { go("gifts") }
        TopUpTile(k, atTR(k, 1558f, 90f, 104f))

        // ---------- right: mode card, ranked, START
        BrCard(k, atTR(k, 1313f, 300f, 345f)) { go("play") }
        RankedCard(k, atTR(k, 1237f, 706f, 420f), { go("play") }, { go("friends") })
        StartButton(k, atTR(k, 1335f, 809f, 322f)) { go("play") }

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
                Column(verticalArrangement = Arrangement.spacedBy((8 * k).dp)) {
                    entries.forEach { e -> MenuRow(k, e, Modifier) { go(e.route) } }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy((12 * k).dp)) {
                    Box(Modifier.sized(k, 140f, 170f)) { ArtImage("art_booyah", Modifier.fillMaxSize()) }
                    BrCard(k, Modifier) { go("play") }
                }
            }

            Spacer(Modifier.weight(1f))

            // bottom: ranked, START, bar
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                RankedCard(k, Modifier, { go("play") }, { go("friends") })
                Spacer(Modifier.height((12 * k).dp))
                StartButton(k, Modifier) { go("play") }
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

/**
 * Every menu row has the same size and the same slant as START. Text only, no icon.
 * Only Store and Luck Royale are highlighted. Change the slant with the number inside SlantShape(...).
 */
@Composable
private fun MenuRow(k: Float, e: MenuEntry, modifier: Modifier, onClick: () -> Unit) {
    val shape = SlantShape((14 * k).dp)
    Box(
        modifier
            .sized(k, ROW_W, ROW_H)
            .clip(shape)
            .background(GlassBrush)
            .border((1.5f * k).dp, if (e.hot) Orange else Line, shape)
            .clickable { onClick() }
    ) {
        NfText(
            e.label.uppercase(),
            Modifier.align(Alignment.CenterStart).padding(start = (34 * k).dp),
            size = fsz(k, 26f),
            bold = true,
            color = if (e.hot) Orange else Color.White
        )
        if (e.badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = (6 * k).dp, end = (22 * k).dp)
                    .size((11 * k).dp)
                    .clip(CircleShape)
                    .background(Orange)
            )
        }
    }
}

@Composable
private fun LabelChip(k: Float, modifier: Modifier, text: String, color: Color) {
    val shape = RoundedCornerShape((4 * k).dp)
    Box(
        modifier.clip(shape).background(GlassBrush).border((1.dp), Line, shape),
        contentAlignment = Alignment.Center
    ) { NfText(text, size = fsz(k, 15f), bold = true, color = color) }
}

/** Right side tiles share one small size: 104 x 128. */
@Composable
private fun GiftTile(k: Float, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.sized(k, 104f, 128f).clickable { onClick() }) {
        GlyphIcon(Glyph.GIFT, Modifier.align(Alignment.TopCenter).sized(k, 68f, 68f))
        LabelChip(
            k,
            Modifier.align(Alignment.TopCenter).offset(0.dp, (76 * k).dp).sized(k, 104f, 24f),
            "FREE GIFTS",
            Orange
        )
    }
}

@Composable
private fun TopUpTile(k: Float, modifier: Modifier) {
    val shape = RoundedCornerShape((4 * k).dp)
    Box(modifier.sized(k, 104f, 128f)) {
        GlyphIcon(Glyph.CROWN, Modifier.align(Alignment.TopCenter).sized(k, 68f, 68f))
        LabelChip(
            k,
            Modifier.align(Alignment.TopCenter).offset(0.dp, (76 * k).dp).sized(k, 104f, 24f),
            "TOP-UP",
            Color.White
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(0.dp, (104 * k).dp)
                .sized(k, 104f, 24f)
                .clip(shape)
                .background(GlassBrush)
                .border((1.dp), Line, shape)
        ) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.CLOCK, Modifier.size((14 * k).dp))
                Spacer(Modifier.width((5 * k).dp))
                NfText("23:45:12", size = fsz(k, 15f), bold = true)
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
            .background(GlassBrush)
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
                .background(Color(0xB30B0F24))
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
            .background(GlassBrush)
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

/** START: slanted shape only. The rounded glow that used to sit behind it is gone. */
@Composable
private fun StartButton(k: Float, modifier: Modifier, onClick: () -> Unit) {
    val slant = SlantShape((26 * k).dp)
    Box(
        modifier
            .sized(k, 322f, 106f)
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
            .background(GlassBrush)
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
