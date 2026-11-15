package com.d200.nightfall3d.game

class FakeGameApi : GameApi {
    private var equipped = "ak47"
    private var character = "ilona"

    override var selectedMode: GameMode = GameMode.DEATHMATCH
    override var selectedArena: String = "Zero Arena"

    override fun profile() = PlayerProfile("DGreat", 32, 125450, 840)

    override fun weapons() = listOf(
        Weapon("ak47", "AK47", "Rifle", owned = true),
        Weapon("maa1", "MAA1", "SMG", owned = false, priceCoins = 800),
        Weapon("scar", "SCAR", "Rifle", owned = false, priceCoins = 1200),
        Weapon("aug", "AUG", "Rifle", owned = false, priceDiamonds = 499),
        Weapon("mp40", "MP40", "SMG", owned = false, priceDiamonds = 999)
    )

    override fun equippedWeaponId() = equipped
    override fun equipWeapon(id: String) { equipped = id }

    override fun characters() = listOf(
        Character("ilona", "Ilona", "characters/ilona.glb")
    )

    override fun selectedCharacterId() = character
    override fun selectCharacter(id: String) { character = id }

    override fun arenas() = listOf("Zero Arena")

    override fun startMatch() { /* 3D game hooks in here later */ }
}
