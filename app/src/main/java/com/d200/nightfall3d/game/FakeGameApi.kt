package com.d200.nightfall3d.game

class FakeGameApi : GameApi {
    override var equippedWeaponId: String = "ak47"
    override var selectedCharacterId: String = "blue"
    override val match = MatchConfig()
    override val settings = GameSettings()

    override fun profile() = PlayerProfile("DGreat", 32, 125450, 840)

    override fun weapons() = listOf(
        Weapon("ak47", "AK47", "Rifle", owned = true, description = "Classic and reliable.",
            damage = 0.70f, range = 0.65f, accuracy = 0.55f, magazine = 0.50f),
        Weapon("maa1", "MAA1", "SMG", owned = false, priceCoins = 800, description = "Fast firing close range spray.",
            damage = 0.45f, range = 0.40f, accuracy = 0.60f, magazine = 0.70f),
        Weapon("scar", "SCAR", "Rifle", owned = false, priceCoins = 1200, description = "Balanced and accurate.",
            damage = 0.65f, range = 0.70f, accuracy = 0.75f, magazine = 0.55f),
        Weapon("aug", "AUG", "Rifle", owned = false, priceDiamonds = 499, description = "Steady aim at mid range.",
            damage = 0.60f, range = 0.70f, accuracy = 0.85f, magazine = 0.60f),
        Weapon("mp40", "MP40", "SMG", owned = false, priceDiamonds = 999, description = "Heavy hitting SMG.",
            damage = 0.50f, range = 0.45f, accuracy = 0.60f, magazine = 0.65f),
        Weapon("m1887", "M1887", "Shotgun", owned = false, locked = true, description = "Devastating up close. Unlocks at level 40.",
            damage = 0.95f, range = 0.25f, accuracy = 0.30f, magazine = 0.20f),
        Weapon("awm", "AWM", "Sniper", owned = false, priceCoins = 1800, description = "One shot, long range.",
            damage = 1.00f, range = 0.95f, accuracy = 0.90f, magazine = 0.10f),
        Weapon("katana", "Katana", "Melee", owned = false, priceCoins = 800, description = "Silent and deadly up close.",
            damage = 0.80f, range = 0.10f, accuracy = 1.00f, magazine = 1.00f)
    )

    // Real characters only. The glbPath is a file in app/src/main/assets/.
    // To add the next one: put its .glb in assets, add it to CHAR_FILES in Game3DView.kt, and add a line here.
    // Orange uses orange_amor.glb (the helmet file orange_helmet.glb is for the character screen later, not here).
    // The stats, skill name and skill text of Orange are placeholders: change them any time.
    override fun characters() = listOf(
        Character("blue", "Blue", "Fast and balanced. The first fighter of the squad.",
            "Shadow Dash", "Dash forward and become invisible for a short time.",
            0.60f, 0.85f, 0.70f, 0xFF3B82F6, "char_blue.glb", level = 12, rank = "Silver"),
        Character("orange", "Orange", "Heavy armour. Built to take the hits.",
            "Iron Skin", "Take less damage for a short time.",
            0.85f, 0.55f, 0.70f, 0xFFFF8A00, "orange_amor.glb", level = 1, rank = "Rookie")
    )

    override fun items() = listOf(
        GameItem("grenade", "Grenade", "Grenades", 300),
        GameItem("medkit", "Med Kit", "Items", 200),
        GameItem("smoke", "Smoke", "Grenades", 150),
        GameItem("mine", "Landmine", "Special", 250),
        GameItem("katana", "Katana", "Melee", 800)
    )

    override fun storeItems() = listOf(
        StoreItem("plasma", "Plasma SMG", "Weapons", "Promo", "-70%", "149", "diamonds", true),
        StoreItem("launcher", "Golden Launcher", "Weapons", "Gift", "-50%", "299", "diamonds", true),
        StoreItem("blade", "Neon Katana", "Weapons", "New", "", "799", "diamonds", true),
        StoreItem("coins1000", "1000 Coins", "Coins", "", "", "$0.99", "usd", true),
        StoreItem("dia500", "500 Diamonds", "Diamonds", "", "", "$4.99", "usd", true),
        StoreItem("elite", "Elite Bundle", "Bundles", "", "", "$9.99", "usd", true),
        StoreItem("redfury", "Red Fury Skin", "Characters", "New", "", "1200", "coins", false),
        StoreItem("starter", "Starter Pack", "Gift Packs", "Gift", "", "$1.99", "usd", false),
        StoreItem("coins5000", "5000 Coins", "Coins", "", "", "$3.99", "usd", false),
        StoreItem("dia1200", "1200 Diamonds", "Diamonds", "Promo", "", "$9.99", "usd", false)
    )

    override fun friends() = listOf(
        Friend("ShadowKing", "Online"),
        Friend("BellaGirl", "Online"),
        Friend("RazorX", "In Lobby")
    )

    override fun arenas() = listOf("Zero Arena", "Ironvault", "Dockyard")

    override fun startMatch() { /* the 3D game hooks in here later */ }
}
