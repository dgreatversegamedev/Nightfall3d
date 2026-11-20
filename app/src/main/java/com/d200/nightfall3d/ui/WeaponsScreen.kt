package com.d200.nightfall3d.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.d200.nightfall3d.game.GUNS
import com.d200.nightfall3d.game.GameApi
import com.d200.nightfall3d.game.SHIELDS
import com.d200.nightfall3d.game.ShieldDef
import com.d200.nightfall3d.game.ShieldLoadout
import com.d200.nightfall3d.game.ThrowLoadout
import com.d200.nightfall3d.game.gunNames
import com.d200.nightfall3d.ui.theme.*
import com.google.android.filament.Engine
import com.google.android.filament.IndirectLight
import com.google.android.filament.Skybox
import io.github.sceneview.Scene
import io.github.sceneview.environment.Environment
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes

// =====================================================================
// WeaponsScreen.kt
// Lists every gun that is uploaded into the game (the GUNS list in Game3DView.kt).
// Add a gun there and it shows up here by itself. TEST MODE: no buying.
//
// CHANGES IN THIS VERSION
//  0) NEW TAB "GRENADES": Grenade and Gas. TAKE / REMOVE each one. The ones you take get a button on the match screen
//     (ThrowLoadout in Throwables.kt). SHIELDS is now tab 7 and OTHERS tab 8.
//  1) NEW TAB "SHIELDS": banners for every shield (the SHIELDS list in Shields.kt). Each banner shows the name,
//     HARD / AD tag, how to unlock it and EQUIPPED. Tap a banner for the details, then EQUIP.
//     TEST MODE (ShieldLoadout.TEST_MODE in Shields.kt): you can equip locked shields too.
//  2) The tab row scrolls sideways, because there are more tabs now.
//  3) OTHERS moved to the last tab (still opens the items screen).
//
// LOADOUT: you can select only 5 weapons for now (GunLoadout.MAX). The first one selected is the starting gun.
// TO GIVE A GUN ITS REAL INFO, edit GUN_INFO below.
// =====================================================================

/** The guns the player selected (max 5). The match screen reads this. */
object GunLoadout {
    const val MAX = 5
    var picked: List<String> = emptyList()

    /** The selected guns that really exist. If nothing was selected yet, the first 5 uploaded guns. */
    fun names(all: List<String>): List<String> {
        val ok = picked.filter { it in all }.distinct().take(MAX)
        return if (ok.isEmpty()) all.take(MAX) else ok
    }
}

private class GunInfo(
    val category: String,
    val description: String,
    val damage: Float,
    val range: Float,
    val accuracy: Float,
    val magazine: Float
)

// EDIT HERE LATER: stats are middle numbers for now
private val GUN_INFO = mapOf(
    "MP5" to GunInfo("SMG", "Sub machine gun.", 0.5f, 0.5f, 0.5f, 0.5f),
    "AK-47" to GunInfo("Rifle", "Classic and reliable.", 0.5f, 0.5f, 0.5f, 0.5f),
    "AR" to GunInfo("Rifle", "Assault rifle.", 0.5f, 0.5f, 0.5f, 0.5f),
    "Rifle" to GunInfo("Sniper", "Long range rifle with scope.", 0.8f, 0.9f, 0.8f, 0.3f),
    "Carbine" to GunInfo("Rifle", "Light and fast carbine.", 0.45f, 0.45f, 0.55f, 0.6f)
)

private class GunRow(val name: String, val file: String, val info: GunInfo)

private fun gunRow(name: String): GunRow {
    val file = GUNS.firstOrNull { it.name == name }?.file ?: ""
    return GunRow(name, file, GUN_INFO[name] ?: GunInfo("Rifle", "", 0.5f, 0.5f, 0.5f, 0.5f))
}

private val Accent = Color(0xFFFF8A00)          // Nightfall orange
private val OnAccent = Color(0xFF1A0E00)        // dark text on an orange button
private val ScreenBg = Color(0xFF080B0A)
private val PanelBg = Color(0xFF0E1311)
private val CardBg = Color(0xFF111614)
private val LineColor = Color(0xFF232B28)
private val DimText = Color(0xFF8A938F)
private val TrackColor = Color(0xFF1C2421)

private const val GRENADE_TAB = 6               // index of "GRENADES" in the tab list
private const val SHIELD_TAB = 7                // index of "SHIELDS" in the tab list
private const val OTHERS_TAB = 8                // index of "OTHERS" (opens the items screen)

@Composable
fun WeaponsScreen(api: GameApi, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val names = remember { gunNames() }
    val rows = remember { names.map { gunRow(it) } }
    val tabs = listOf("ALL", "RIFLE", "SMG", "SHOTGUN", "SNIPER", "MELEE", "GRENADES", "SHIELDS", "OTHERS")
    var tab by remember { mutableStateOf(0) }
    val picked = remember { mutableStateListOf<String>().apply { addAll(GunLoadout.names(names)) } }
    var selName by remember { mutableStateOf(picked.firstOrNull() ?: (names.firstOrNull() ?: "")) }
    val list = if (tab == 0) rows else rows.filter { it.info.category.equals(tabs[tab], ignoreCase = true) }
    val sel: GunRow? = rows.firstOrNull { it.name == selName } ?: rows.firstOrNull()

    // shields
    val level = remember { api.profile().level }
    var shieldView by remember { mutableStateOf(ShieldLoadout.selected.coerceIn(0, SHIELDS.lastIndex)) }
    var shieldEquipped by remember { mutableStateOf(ShieldLoadout.selected.coerceIn(0, SHIELDS.lastIndex)) }

    // ONE 3D engine shared by every gun picture on this screen (light on memory)
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environment = remember(engine) {
        Environment(
            indirectLight = IndirectLight.Builder()
                .irradiance(1, floatArrayOf(1f, 1f, 1f))
                .intensity(30_000f)
                .build(engine),
            skybox = Skybox.Builder().color(0.067f, 0.086f, 0.078f, 1f).build(engine)   // same dark as the cards
        )
    }

    // select / unselect a gun, keep the loadout for the match
    fun toggle(gunName: String) {
        if (gunName in picked) {
            if (picked.size > 1) picked.remove(gunName)
        } else if (picked.size < GunLoadout.MAX) {
            picked.add(gunName)
        }
        GunLoadout.picked = picked.toList()
        if (picked.isNotEmpty()) api.equippedWeaponId = picked.first()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NfIconButton(NfIconKind.BACK, Modifier, size = 44.dp) { onBack() }
                NfText("WEAPONS", size = 22, bold = true)
            }

            // tabs
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                tabs.forEachIndexed { i, t ->
                    val on = i == tab
                    Column(
                        Modifier.clickable {
                            if (i == OTHERS_TAB) onOpen("items") else tab = i
                        },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        NfText(t, size = 13, bold = true, color = if (on) Accent else DimText)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .width(34.dp)
                                .height(2.dp)
                                .background(if (on) Accent else Color.Transparent)
                        )
                    }
                }
            }

            if (tab == SHIELD_TAB) {
                ShieldsPane(
                    Modifier.fillMaxWidth().weight(1f),
                    level,
                    shieldView,
                    shieldEquipped,
                    { shieldView = it },
                    {
                        ShieldLoadout.selected = it
                        shieldEquipped = it
                    }
                )
            } else if (tab == GRENADE_TAB) {
                ThrowablesPane(Modifier.fillMaxWidth().weight(1f))
            } else {
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // left: the uploaded guns (real 3D pictures)
                    Column(
                        Modifier.weight(1.5f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NfText("UPLOADED GUNS  ${list.size}", size = 11, bold = true, color = DimText)
                        if (list.isEmpty()) {
                            NfText("No guns here yet", size = 13, color = DimText)
                        }
                        list.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { g ->
                                    GunCard(
                                        g,
                                        g.name == selName,
                                        g.name in picked,
                                        engine, modelLoader, environment,
                                        Modifier.weight(1f).height(110.dp)
                                    ) { selName = g.name }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }

                    // right: details. The info scrolls, the button stays big at the bottom.
                    if (sel != null) {
                        val panelShape = RoundedCornerShape(8.dp)
                        val selectedName: String = sel.name      // plain copy, used by the button click below
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(panelShape)
                                .background(PanelBg)
                                .border(1.dp, LineColor, panelShape)
                                .padding(14.dp)
                        ) {
                            Column(
                                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                NfText(selectedName, size = 24, bold = true)
                                if (sel.info.description.isNotEmpty()) {
                                    NfText(sel.info.description, size = 12, color = DimText)
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CardBg)
                                ) {
                                    key(selectedName) {
                                        GunPicture(engine, modelLoader, environment, sel.file, Modifier.fillMaxSize(), 0f)
                                    }
                                }
                                AccentBar("Damage", sel.info.damage)
                                AccentBar("Range", sel.info.range)
                                AccentBar("Accuracy", sel.info.accuracy)
                                AccentBar("Magazine", sel.info.magazine)
                            }

                            Spacer(Modifier.height(10.dp))

                            val isPicked = selectedName in picked
                            val full = picked.size >= GunLoadout.MAX
                            // SELECT is orange. REMOVE, and SELECT when the 5 are full, are dark.
                            val canClick = if (isPicked) picked.size > 1 else !full
                            val filled = !isPicked && !full
                            val label = if (isPicked) "REMOVE" else if (full) "5 / 5 FULL" else "SELECT"
                            val btnShape = RoundedCornerShape(8.dp)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)                       // big enough to tap
                                    .clip(btnShape)
                                    .background(if (filled) Accent else CardBg)
                                    .border(1.5.dp, if (canClick) Accent else LineColor, btnShape)
                                    .clickable(enabled = canClick) { toggle(selectedName) },
                                contentAlignment = Alignment.Center
                            ) {
                                NfText(
                                    label,
                                    size = 15,
                                    bold = true,
                                    color = if (filled) OnAccent else if (canClick) Accent else DimText
                                )
                            }
                        }
                    }
                }
            }

            // bottom line
            if (tab == SHIELD_TAB) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NfText(
                        "Equipped: ${SHIELDS[shieldEquipped.coerceIn(0, SHIELDS.lastIndex)].name}",
                        size = 14,
                        bold = true,
                        color = Accent
                    )
                    if (ShieldLoadout.TEST_MODE) {
                        NfText("Test mode: every shield can be equipped.", size = 12, color = DimText)
                    }
                }
            } else if (tab == GRENADE_TAB) {
                NfText("What you take here gets a button on the left of the match screen.", size = 12, color = DimText)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NfText(
                        "Selected ${picked.size} / ${GunLoadout.MAX} weapons",
                        size = 14,
                        bold = true,
                        color = Accent
                    )
                    NfText("You can only select ${GunLoadout.MAX} weapons for now.", size = 12, color = DimText)
                }
            }
        }
    }
}

// ---------------- Grenade + Gas ----------------

@Composable
private fun ThrowablesPane(modifier: Modifier) {
    var grenadeOn by remember { mutableStateOf(ThrowLoadout.grenadeOn) }
    var gasOn by remember { mutableStateOf(ThrowLoadout.gasOn) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ThrowCard(
            Modifier.weight(1f).fillMaxHeight(),
            "GRENADE",
            "Goes off 2 seconds after you throw it. Big damage close to the blast, less far away. Walls block it. ${ThrowLoadout.GRENADE_COUNT} per match.",
            listOf("Damage" to 0.9f, "Radius" to 0.55f, "Fuse" to 0.4f),
            grenadeOn
        ) {
            grenadeOn = !grenadeOn
            ThrowLoadout.grenadeOn = grenadeOn
        }
        ThrowCard(
            Modifier.weight(1f).fillMaxHeight(),
            "GAS",
            "Leaves a green cloud for 8 seconds. Everyone standing in it loses HP every second. ${ThrowLoadout.GAS_COUNT} per match.",
            listOf("Damage" to 0.5f, "Radius" to 0.5f, "Duration" to 0.8f),
            gasOn
        ) {
            gasOn = !gasOn
            ThrowLoadout.gasOn = gasOn
        }
    }
}

@Composable
private fun ThrowCard(
    modifier: Modifier,
    name: String,
    desc: String,
    bars: List<Pair<String, Float>>,
    taken: Boolean,
    onToggle: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier
            .clip(shape)
            .background(PanelBg)
            .border(1.dp, if (taken) Accent else LineColor, shape)
            .padding(14.dp)
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NfText(name, size = 24, bold = true)
            NfText(desc, size = 12, color = DimText)
            bars.forEach { AccentBar(it.first, it.second) }
        }
        Spacer(Modifier.height(10.dp))
        val btnShape = RoundedCornerShape(8.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(btnShape)
                .background(if (taken) CardBg else Accent)
                .border(1.5.dp, Accent, btnShape)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            NfText(if (taken) "REMOVE" else "TAKE", size = 15, bold = true, color = if (taken) Accent else OnAccent)
        }
    }
}

// ---------------- Shields ----------------

/** Left: one banner per shield. Right: details of the tapped shield and the EQUIP button. */
@Composable
private fun ShieldsPane(
    modifier: Modifier,
    level: Int,
    viewIdx: Int,
    equippedIdx: Int,
    onView: (Int) -> Unit,
    onEquip: (Int) -> Unit
) {
    val vi = viewIdx.coerceIn(0, SHIELDS.lastIndex)
    val d = SHIELDS[vi]
    val accent = Color(d.accent)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier.weight(1.5f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NfText("SHIELDS  ${SHIELDS.size}", size = 11, bold = true, color = DimText)
            SHIELDS.forEachIndexed { i, s ->
                ShieldBanner(s, i == equippedIdx, i == vi, level, Modifier.fillMaxWidth().height(76.dp)) { onView(i) }
            }
        }

        val panelShape = RoundedCornerShape(8.dp)
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(panelShape)
                .background(PanelBg)
                .border(1.dp, LineColor, panelShape)
                .padding(14.dp)
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NfText(d.name, size = 24, bold = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (d.hard) ShieldTag("HARD", accent)
                    if (d.ad) ShieldTag("AD", accent)
                    if (d.auraDps > 0f) ShieldTag("DAMAGES NEARBY", accent)
                }
                NfText(d.description, size = 12, color = DimText)
                Box(
                    Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(6.dp)).background(CardBg),
                    contentAlignment = Alignment.Center
                ) {
                    ShieldOrb(accent, Modifier.size(92.dp))
                }
                AccentBar("Strength", (d.maxHits / 60f).coerceIn(0.05f, 1f))
                AccentBar("Recharge", (1f - (d.reviveSec - 15f) / 20f).coerceIn(0.05f, 1f))
                AccentBar("Damage", (d.auraDps / 15f).coerceIn(0f, 1f))
                NfText("Unlock: ${d.unlockText()}", size = 12, bold = true, color = DimText)
            }

            Spacer(Modifier.height(10.dp))

            val equipped = vi == equippedIdx
            val ok = d.availableAt(level)
            val canEquip = !equipped && (ok || ShieldLoadout.TEST_MODE)
            val label = if (equipped) "EQUIPPED" else if (ok) "EQUIP" else if (ShieldLoadout.TEST_MODE) "EQUIP (TEST)" else "LOCKED"
            val filled = canEquip
            val btnShape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(btnShape)
                    .background(if (filled) Accent else CardBg)
                    .border(1.5.dp, if (canEquip || equipped) Accent else LineColor, btnShape)
                    .clickable(enabled = canEquip) { onEquip(vi) },
                contentAlignment = Alignment.Center
            ) {
                NfText(
                    label,
                    size = 15,
                    bold = true,
                    color = if (filled) OnAccent else if (equipped) Accent else DimText
                )
            }
        }
    }
}

@Composable
private fun ShieldBanner(
    def: ShieldDef,
    equipped: Boolean,
    viewing: Boolean,
    level: Int,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    val accent = Color(def.accent)
    val ok = def.availableAt(level)
    Box(
        modifier
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.30f), CardBg, CardBg)))
            .border(if (viewing) 1.5.dp else 1.dp, if (viewing) Accent else LineColor, shape)
            .clickable { onClick() }
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShieldOrb(accent, Modifier.size(52.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NfText(def.name.uppercase(), size = 15, bold = true)
                    if (def.hard) ShieldTag("HARD", accent)
                    if (def.ad) ShieldTag("AD", accent)
                }
                NfText(def.unlockText(), size = 11, color = DimText)
            }
            NfText(
                if (equipped) "EQUIPPED" else if (ok) "UNLOCKED" else "LOCKED",
                size = 11,
                bold = true,
                color = if (equipped) Accent else DimText
            )
        }
    }
}

/** A glowing ball in the shield colour (a light picture of the shield). */
@Composable
private fun ShieldOrb(color: Color, modifier: Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(color.copy(alpha = 0.10f), color.copy(alpha = 0.35f), color.copy(alpha = 0.75f))
                )
            )
            .border(1.5.dp, color, CircleShape)
    )
}

@Composable
private fun ShieldTag(text: String, color: Color) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.8f), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        NfText(text, size = 9, bold = true, color = color)
    }
}

/**
 * The real gun, drawn from its own .glb (the file already in the game), turning slowly.
 * If the file is missing it shows the old grey silhouette instead.
 */
@Composable
private fun GunPicture(
    engine: Engine,
    modelLoader: ModelLoader,
    environment: Environment,
    file: String,
    modifier: Modifier,
    startYaw: Float
) {
    val node = remember(file) {
        try {
            ModelNode(
                modelInstance = modelLoader.createModelInstance(file),
                autoAnimate = false,
                scaleToUnits = 1f,                        // every gun fits the same size
                centerOrigin = Position(0f, 0f, 0f)       // turn around its middle
            )
        } catch (e: Throwable) {
            Log.e("WeaponsScreen", "Could not load $file", e)
            null
        }
    }
    if (node == null) {
        GunSilhouette(modifier, Color(0xFF8E9894))
        return
    }
    val cameraNode = rememberCameraNode(engine) { position = Position(0f, 0f, 1.7f) }
    val nodes = rememberNodes { add(node) }
    Scene(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        cameraNode = cameraNode,
        childNodes = nodes,
        environment = environment,
        onFrame = { nanos ->
            node.rotation = Rotation(y = (startYaw + nanos / 1_000_000_000f * 35f) % 360f)
        }
    )
}

@Composable
private fun GunCard(
    g: GunRow,
    selected: Boolean,
    inLoadout: Boolean,
    engine: Engine,
    modelLoader: ModelLoader,
    environment: Environment,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .clip(shape)
            .background(CardBg)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Accent else LineColor, shape)
    ) {
        GunPicture(
            engine, modelLoader, environment, g.file,
            Modifier.align(Alignment.Center).fillMaxWidth(0.85f).fillMaxHeight(0.62f),
            0f
        )
        // transparent layer on top, so a tap on the picture also selects the card
        Box(Modifier.matchParentSize().clickable { onClick() })
        NfText(
            g.info.category.uppercase(),
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            size = 9,
            color = DimText,
            bold = true
        )
        if (inLoadout) {
            NfIcon(
                NfIconKind.CHECK,
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(14.dp),
                Accent
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            NfText(g.name, size = 12, bold = true)
            if (inLoadout) {
                NfText("Selected", size = 11, color = Accent, bold = true)
            }
        }
    }
}

/** One stat: name on the left, thin bar on the right. Always the same orange. */
@Composable
private fun AccentBar(label: String, value: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NfText(label, Modifier.width(78.dp), size = 12, color = DimText)
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(TrackColor)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Accent)
            )
        }
    }
}