package com.d200.nightfall3d.game

// =====================================================================
// Shields.kt  -  the list of shields (NEW FILE, put it in the game folder).
// Add or change a shield here: it shows up in Weapons > SHIELDS by itself.
//
// TEST MODE: ShieldLoadout.TEST_MODE = true lets you equip any shield, even a locked one.
// Set it to false when buying and ads are really connected.
//
// maxHits   = how many hits the shield takes before it breaks
// reviveSec = seconds until it grows back
// auraDps   = HP per second that enemies lose while they stand inside auraR (meters). 0 = no aura.
// =====================================================================

// Keep this order: it is the same order as STYLE_COLS in ShieldView.kt
internal enum class ShieldStyle { CLASSIC, VORTEX, TRIANGLE, TECH, SPARK }

internal class ShieldDef(
    val id: String,
    val name: String,
    val style: ShieldStyle,
    val description: String,
    val hard: Boolean,
    val unlockLevel: Int,
    val priceCoins: Int,
    val ad: Boolean,
    val maxHits: Float,
    val reviveSec: Float,
    val auraDps: Float,
    val auraR: Float,
    val accent: Long
) {
    fun availableAt(level: Int): Boolean = !ad && priceCoins == 0 && level >= unlockLevel

    fun unlockText(): String = when {
        ad -> "WATCH AN AD TO UNLOCK"
        priceCoins > 0 -> "%,d COINS".format(priceCoins)
        unlockLevel > 0 -> "UNLOCK AT LEVEL $unlockLevel"
        else -> "FREE"
    }
}

internal val SHIELDS = listOf(
    ShieldDef(
        id = "classic", name = "Classic Hex", style = ShieldStyle.CLASSIC,
        description = "Standard energy shield. Blocks all damage until it breaks.",
        hard = false, unlockLevel = 0, priceCoins = 0, ad = false,
        maxHits = 25f, reviveSec = 20f, auraDps = 0f, auraR = 0f,
        accent = 0xFF35C8FFL
    ),
    ShieldDef(
        id = "vortex", name = "Vortex", style = ShieldStyle.VORTEX,
        description = "Hard shield. Magical spinning smoke. You can see and shoot through it. Enemies that come close lose HP. Slower to grow back.",
        hard = true, unlockLevel = 20, priceCoins = 0, ad = false,
        maxHits = 35f, reviveSec = 30f, auraDps = 12f, auraR = 2.4f,
        accent = 0xFFB04CFFL
    ),
    ShieldDef(
        id = "prism", name = "Prism Barrier", style = ShieldStyle.TRIANGLE,
        description = "Hard shield. Blue triangle plates, the strongest shield. A shock field hurts enemies that come close.",
        hard = true, unlockLevel = 0, priceCoins = 50000, ad = false,
        maxHits = 60f, reviveSec = 25f, auraDps = 6f, auraR = 2.0f,
        accent = 0xFF2A8CFFL
    ),
    ShieldDef(
        id = "tech", name = "Tech Dome", style = ShieldStyle.TECH,
        description = "Tech dome with spinning circuit rings on the floor.",
        hard = false, unlockLevel = 50, priceCoins = 0, ad = false,
        maxHits = 30f, reviveSec = 18f, auraDps = 0f, auraR = 0f,
        accent = 0xFF19D2E4L
    ),
    ShieldDef(
        id = "spark", name = "Storm Spark", style = ShieldStyle.SPARK,
        description = "Ad shield. Lightning inside a cloud of smoke. Enemies that come close get shocked and lose HP.",
        hard = false, unlockLevel = 0, priceCoins = 0, ad = true,
        maxHits = 25f, reviveSec = 20f, auraDps = 10f, auraR = 2.8f,
        accent = 0xFFB4AFFFL
    )
)

object ShieldLoadout {
    const val TEST_MODE = true

    /** Index in SHIELDS of the shield you use in the match. */
    var selected: Int = 0

    internal fun current(): ShieldDef = SHIELDS[selected.coerceIn(0, SHIELDS.lastIndex)]
}
