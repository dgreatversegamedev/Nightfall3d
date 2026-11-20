package com.d200.nightfall3d.game

import kotlin.math.ceil

/**
 * AUTO = fast fire while the aim joystick is held.
 * SINGLE = slow, one bullet per shot: while the joystick is held it shoots, pauses (the cooldown), shoots again.
 * Both are driven by shotsPerSecond, SINGLE just has a long pause (sniper 1.25 s, pistol 0.4 s).
 */
enum class FireMode { AUTO, SINGLE }

/**
 * One gun = one WeaponSpec. Every gun in the game (and any gun added later,
 * like the pistol) uses this same format.
 *
 * Distances are in meters (1 unit = 1 m, same as the FX lab). Damage is HP taken
 * per bullet, out of a 100 HP player.
 */
data class WeaponSpec(
    val id: String,
    val name: String,
    val bulletGlb: String,          // bullet model file
    val flashGlb: String,           // muzzle fire model file
    val fireMode: FireMode,
    val shotsPerSecond: Float,      // fire rate (sniper is below 1)
    val bulletSpeed: Float,         // meters per second
    val range: Float,               // bullet disappears after this many meters
    val damage: Float,              // HP per bullet (per pellet for the shotgun)
    val falloffStartPct: Float,     // full damage until this fraction of the range
    val minDamagePct: Float,        // fraction of damage left at max range (1 = no falloff)
    val headshotMultiplier: Float,
    val pellets: Int,               // bullets per shot (1 for everything except shotgun)
    val spreadDeg: Float,           // random cone angle in degrees (0 = perfectly straight)
    val magazineSize: Int,
    val reloadSeconds: Float,
    val bulletScale: Float,         // size of the bullet model
    val flashScale: Float           // size of the muzzle fire model
) {
    /** Seconds to wait between shots. */
    val shotIntervalSeconds: Float get() = 1f / shotsPerSecond

    val isSingleShot: Boolean get() = fireMode == FireMode.SINGLE

    /** How many bullet models of this gun can be flying at once (the game makes this many copies of the bullet GLB). */
    val bulletSlots: Int get() = (ceil(range / bulletSpeed * shotsPerSecond).toInt() * pellets + 2).coerceIn(3, 24)

    /** Damage dealt by one bullet that hits something [distance] meters from the muzzle. */
    fun damageAt(distance: Float, headshot: Boolean = false): Float {
        if (distance > range) return 0f
        val fullUntil = range * falloffStartPct
        val scale = if (distance <= fullUntil) {
            1f
        } else {
            val t = (distance - fullUntil) / (range - fullUntil)
            1f - t * (1f - minDamagePct)
        }
        val head = if (headshot) headshotMultiplier else 1f
        return damage * scale * head
    }

    /** How many full-damage hits it takes to kill a player (all pellets count for the shotgun). */
    fun hitsToKill(maxHp: Float = 100f): Int = ceil(maxHp / (damage * pellets)).toInt()
}

object WeaponSpecs {

    val AK47 = WeaponSpec(
        id = "ak47", name = "AK-47",
        bulletGlb = "bullet_rifle.glb", flashGlb = "flash_rifle.glb",
        fireMode = FireMode.AUTO, shotsPerSecond = 10f,
        bulletSpeed = 75f, range = 80f,
        damage = 25f, falloffStartPct = 0.5f, minDamagePct = 0.6f, headshotMultiplier = 1.5f,
        pellets = 1, spreadDeg = 1.6f,
        magazineSize = 30, reloadSeconds = 2.4f,
        bulletScale = 1f, flashScale = 1f
    )

    val AR = WeaponSpec(
        id = "ar", name = "AR",
        bulletGlb = "bullet_rifle.glb", flashGlb = "flash_rifle.glb",
        fireMode = FireMode.AUTO, shotsPerSecond = 11f,
        bulletSpeed = 85f, range = 100f,
        damage = 20f, falloffStartPct = 0.6f, minDamagePct = 0.6f, headshotMultiplier = 1.5f,
        pellets = 1, spreadDeg = 0.7f,
        magazineSize = 30, reloadSeconds = 2.2f,
        bulletScale = 1f, flashScale = 1f
    )

    val CARBINE = WeaponSpec(
        id = "carbine", name = "Carbine",
        bulletGlb = "bullet_rifle.glb", flashGlb = "flash_rifle.glb",
        fireMode = FireMode.AUTO, shotsPerSecond = 12.5f,
        bulletSpeed = 80f, range = 75f,
        damage = 16f, falloffStartPct = 0.5f, minDamagePct = 0.5f, headshotMultiplier = 1.5f,
        pellets = 1, spreadDeg = 1.0f,
        magazineSize = 30, reloadSeconds = 2.0f,
        bulletScale = 1f, flashScale = 1f
    )

    val SMG = WeaponSpec(
        id = "smg", name = "SMG",
        bulletGlb = "bullet_smg.glb", flashGlb = "flash_rifle.glb",
        fireMode = FireMode.AUTO, shotsPerSecond = 14f,
        bulletSpeed = 65f, range = 45f,
        damage = 10f, falloffStartPct = 0.4f, minDamagePct = 0.4f, headshotMultiplier = 1.5f,
        pellets = 1, spreadDeg = 2.2f,
        magazineSize = 32, reloadSeconds = 1.8f,
        bulletScale = 1f, flashScale = 1f
    )

    /** Slow single shots (shoot, pause, shoot while the joystick is held), very long range, no damage falloff, two body hits kill, headshot kills. */
    val SNIPER = WeaponSpec(
        id = "sniper", name = "Sniper",
        bulletGlb = "bullet_sniper.glb", flashGlb = "flash_sniper.glb",
        fireMode = FireMode.SINGLE, shotsPerSecond = 0.8f,
        bulletSpeed = 300f, range = 250f,
        damage = 90f, falloffStartPct = 1f, minDamagePct = 1f, headshotMultiplier = 1.5f,
        pellets = 1, spreadDeg = 0f,
        magazineSize = 5, reloadSeconds = 3.2f,
        bulletScale = 1f, flashScale = 1.3f
    )

    // ---- not in the game yet, ready for when you add them ----

    val PISTOL = WeaponSpec(
        id = "pistol", name = "Pistol",
        bulletGlb = "bullet_pistol.glb", flashGlb = "flash_pistol.glb",
        fireMode = FireMode.SINGLE, shotsPerSecond = 2.5f,
        bulletSpeed = 60f, range = 35f,
        damage = 14f, falloffStartPct = 0.5f, minDamagePct = 0.6f, headshotMultiplier = 1.5f,
        pellets = 1, spreadDeg = 0.6f,
        magazineSize = 12, reloadSeconds = 1.3f,
        bulletScale = 1f, flashScale = 1f
    )

    val SHOTGUN = WeaponSpec(
        id = "shotgun", name = "Shotgun",
        bulletGlb = "bullet_shotgun_pellet.glb", flashGlb = "flash_shotgun.glb",
        fireMode = FireMode.SINGLE, shotsPerSecond = 1.2f,
        bulletSpeed = 55f, range = 25f,
        damage = 9f, falloffStartPct = 0.3f, minDamagePct = 0.3f, headshotMultiplier = 1.0f,
        pellets = 8, spreadDeg = 4f,
        magazineSize = 6, reloadSeconds = 2.6f,
        bulletScale = 1f, flashScale = 1f
    )

    /**
     * Which spec a gun in the GUNS list (Game3DView.kt) uses.
     * MP5 = SMG, Rifle = the scoped sniper. Add new guns here.
     */
    fun forGun(gunName: String): WeaponSpec = when (gunName) {
        "MP5" -> SMG
        "AK-47" -> AK47
        "AR" -> AR
        "Rifle" -> SNIPER
        "Carbine" -> CARBINE
        else -> AR
    }

    /** Bots shoot red bullets (this file goes in app/src/main/assets/). */
    const val BOT_BULLET_GLB = "bullet_bot.glb"

    val ALL: List<WeaponSpec> = listOf(AK47, AR, CARBINE, SMG, SNIPER, PISTOL, SHOTGUN)

    fun byId(id: String): WeaponSpec? = ALL.firstOrNull { it.id == id }
}
