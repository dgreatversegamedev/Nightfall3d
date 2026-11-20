package com.d200.nightfall3d.game

enum class PlayType(val label: String, val tagline: String) {
    ONLINE("Online", "Play with players around the world"),
    OFFLINE("Offline", "Play without internet"),
    FRIENDS("With Friends", "Play with your friends"),
    TRAINING("Training", "Practice and learn")
}

enum class GameMode(val label: String, val tagline: String) {
    DEATHMATCH("Deathmatch", "Fast paced battle"),
    TEAM_DEATHMATCH("Team Deathmatch", "Team up and fight"),
    CAPTURE_FLAG("Capture the Flag", "Capture. Hold. Win."),
    ZOMBIES("Zombies", "Survive the horde"),
    KING_OF_HILL("King of the Hill", "Hold the zone to score")
}

data class PlayerProfile(val name: String, val level: Int, val coins: Int, val diamonds: Int)

data class Weapon(
    val id: String,
    val name: String,
    val category: String,
    val owned: Boolean,
    val locked: Boolean = false,
    val priceCoins: Int = 0,
    val priceDiamonds: Int = 0,
    val description: String = "",
    val damage: Float = 0.5f,
    val range: Float = 0.5f,
    val accuracy: Float = 0.5f,
    val magazine: Float = 0.5f
)

data class Character(
    val id: String,
    val name: String,
    val tagline: String,
    val skillName: String,
    val skillText: String,
    val health: Float,
    val speed: Float,
    val damage: Float,
    val color: Long,
    val glbPath: String,          // file in app/src/main/assets/, for example "char_blue.glb"
    val level: Int = 1,           // this character's level
    val rank: String = "Rookie"   // this character's rank
)

data class GameItem(val id: String, val name: String, val category: String, val priceCoins: Int)

data class StoreItem(
    val id: String,
    val name: String,
    val category: String,
    val tag: String,
    val badge: String,
    val price: String,
    val currency: String,
    val featured: Boolean
)

data class Friend(val name: String, val status: String)

class MatchConfig {
    var playType: PlayType = PlayType.ONLINE
    var mode: GameMode = GameMode.DEATHMATCH
    var arena: String = "Zero Arena"
    var teamSize: String = "Solo"
    var autoPickup: Boolean = true
    var weaponLimit: Boolean = false
    var timeLimit: Int = 10

    // Capture the Flag: how many captures win the match. 0 = no limit (keep capturing until the time is up).
    var captureLimit: Int = 0

    // Team sizes for Team Deathmatch, Capture the Flag and Zombies.
    // myTeam = how many players are on YOUR team, and that number includes you (1 to MAX_TEAM).
    // otherTeam = how many players are on the other team (1 to MAX_TEAM). Not used in Zombies.
    var myTeam: Int = 3
    var otherTeam: Int = 3

    companion object {
        const val MAX_TEAM = 5
    }
}

class GameSettings {
    val values = mutableMapOf<String, String>()
}

interface GameApi {
    fun profile(): PlayerProfile
    fun weapons(): List<Weapon>
    var equippedWeaponId: String
    fun characters(): List<Character>
    var selectedCharacterId: String
    fun items(): List<GameItem>
    fun storeItems(): List<StoreItem>
    fun friends(): List<Friend>
    fun arenas(): List<String>
    val match: MatchConfig
    val settings: GameSettings
    fun startMatch()
}
