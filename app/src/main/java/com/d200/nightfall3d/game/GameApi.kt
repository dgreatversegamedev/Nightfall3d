package com.d200.nightfall3d.game

enum class GameMode(val label: String) {
    DEATHMATCH("Deathmatch"),
    CAPTURE_FLAG("Capture the Flag"),
    ZOMBIES("Zombies"),
    KING_OF_HILL("King of the Hill")
}

data class PlayerProfile(val name: String, val level: Int, val coins: Int, val diamonds: Int)

data class Weapon(
    val id: String,
    val name: String,
    val category: String,
    val owned: Boolean,
    val priceCoins: Int = 0,
    val priceDiamonds: Int = 0
)

data class Character(val id: String, val name: String, val glbPath: String)

interface GameApi {
    fun profile(): PlayerProfile
    fun weapons(): List<Weapon>
    fun equippedWeaponId(): String
    fun equipWeapon(id: String)
    fun characters(): List<Character>
    fun selectedCharacterId(): String
    fun selectCharacter(id: String)
    fun arenas(): List<String>
    var selectedMode: GameMode
    var selectedArena: String
    fun startMatch()
}