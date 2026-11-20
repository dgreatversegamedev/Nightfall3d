package com.d200.nightfall3d.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// =====================================================================
// MatchLogic.kt  -  Deathmatch + Team Deathmatch + Capture the Flag (offline test, bots).
// No drawing in here. Game3DView.kt draws the bots (copies of the blue GLB) and the flags from the numbers in this file.
//
//   fighter id 0   = you
//   fighter id 1.. = bots
//   Deathmatch      : everybody is an enemy of everybody (each bot has its own team number)
//   Team Deathmatch : you + the BLUE bots = team 0 (allies), the RED bots = team 1 (enemies)
//                     bullets pass through allies (no friendly fire)
//   Capture the Flag: same two teams as Team Deathmatch. Team 0 base = the blue pad, team 1 base = the red pad.
//
// CAPTURE THE FLAG RULES
//   - Each team has one flag at its base. Touch the ENEMY flag to pick it up. Carry it to YOUR base to score.
//   - You can only score while YOUR flag is at your base (not taken, not lying on the ground).
//   - If the carrier is eliminated the flag drops there. Touch your own dropped flag to send it home.
//     A dropped flag also goes home by itself after FLAG_RETURN_SEC seconds. Touch an enemy's dropped flag to take it.
//   - Captures are counted per team (teamA = your team, teamB = enemy team).
//   - captureLimit 0 = no limit: keep capturing until the time is up. 3 / 5 / 7 = first team to that many captures wins.
//   - When you are eliminated you come back at your own base.
//   - Bots: half of each team attack (go for the flag), the other half defend (guard the base). They use a grid
//     path finder (NavGrid, at the bottom of this file) so they can walk around the walls.
//
// CHANGES IN THIS VERSION
//  1) SHIELD NOW REALLY PROTECTS YOUR HP: while the shield is on, bullets do no damage to your HP at all
//     (SHIELD_KEEP = 0). Before, you still took 30% of every bullet, and at least 1 HP per bullet, so the shield
//     felt like it did nothing. The shield itself is used up by the hits (see SHIELD_MAX_HITS in Game3DView.kt),
//     and when it breaks your HP is exposed again until it grows back.
//     If you want a leak, set SHIELD_KEEP to 0.1 (you take 10% of the damage).
//  2) MatchSettings.blueBots / redBots: the Match Setup screen chooses how many bots are on each team
//     (Team Deathmatch and Capture the Flag). MAX_BOTS is now 8 in total.
//  3) When a bullet hits you while your shield is on, the hit is sent to the shield effect (rig.shieldHitT + direction)
//     and makes sparks instead of blood.
// =====================================================================

object MatchSettings {
    var botCount = 5               // Deathmatch: set by the Match Setup screen
    var blueBots = 2               // Team Deathmatch / Capture the Flag: bots on YOUR team (you not counted)
    var redBots = 3                // Team Deathmatch / Capture the Flag: bots on the enemy team
    const val MAX_BOTS = 8         // more bots = more RAM (each bot is one more copy of the blue GLB + a gun)
}

internal const val PLAYER_MAX_HP = 200
internal const val BOT_MAX_HP = 100
internal const val BOT_BULLET_COUNT = 48

// flag states
internal const val F_HOME = 0          // flag is at its base
internal const val F_CARRIED = 1       // somebody is carrying it
internal const val F_DROPPED = 2       // lying on the ground where the carrier was eliminated

private const val PLAYER_BULLET_DMG = 20      // your bullet damage
private const val BOT_BULLET_DMG = 9          // bot bullet damage (low, so the test is fair)
private const val PUNCH_DMG = 40
private const val PUNCH_RANGE = 1.4f
private const val SHIELD_KEEP = 0f            // with the shield on you take this part of the damage (0 = none, 0.3 = 30%)
private const val SHIELD_CENTER_Y = 0.9f      // same as SHIELD_CY in Game3DView.kt
private const val SHIELD_HIT_SEC = 0.7f       // same as SHIELD_HIT_TIME in Game3DView.kt
private const val RESPAWN_SECONDS = 3f
private const val KILL_LIMIT_DM = 10          // first to this many kills wins (Deathmatch)
private const val KILL_LIMIT_TDM = 25         // first team to this many kills wins (Team Deathmatch)
private const val HIT_RADIUS = 0.45f
private const val HIT_HEIGHT = 1.8f

private const val FLAG_PICK_R = 1.3f          // how close you must be to pick up / return a flag (meters)
private const val FLAG_CAP_R = 1.8f           // how close to your own base the carrier must get to score
private const val FLAG_RETURN_SEC = 15f       // a dropped flag goes home by itself after this long
private const val BASE_SPAWN_R = 5f           // you / bots come back inside this distance from their own flag

private const val BOT_BULLET_SPEED = 30f
private const val BOT_BULLET_RANGE = 40f
private const val BOT_FIRE_INTERVAL = 0.4f    // seconds between bot shots
private const val BOT_SPREAD_DEG = 5f         // bigger = bots miss more
private const val BOT_SIGHT = 28f             // bots see enemies this far (meters)
private const val BOT_SHOOT_RANGE = 24f
private const val BOT_WALK = 2.2f
private const val BOT_RUN = 3.9f
private const val BOT_TURN = 420f             // degrees per second
private const val SPAWN_SCALE = 0.95f         // spawn area = this fraction of the arena size (lower it if bots spawn outside)
private const val SPAWN_CLEAR = 0.65f         // a spawn spot needs this much free space all around the body (meters)

// Path finder grid: covers x = -NAV_HALF_X..NAV_HALF_X and z = -NAV_HALF_Z..NAV_HALF_Z. Zero Arena is 90 x 56.
// If you make a bigger arena, raise these two numbers.
private const val NAV_CELL = 1f
private const val NAV_HALF_X = 60f
private const val NAV_HALF_Z = 40f

private const val M_FACING_OFFSET = 0f        // same as FACING_OFFSET in Game3DView.kt
private const val M_RAD = 180f / PI.toFloat()
private const val M_DEG = PI.toFloat() / 180f

internal class Bot(val id: Int, val team: Int) {
    var x = 0f
    var y = 0f
    var z = 0f
    var faceDeg = 0f
    var hp = BOT_MAX_HP
    var alive = false
    var respawnT = 0f
    var speed = 0f
    var dirX = 1f
    var dirZ = 0f
    var target = -1
    var hasLos = false
    var thinkT = 0f
    var fireT = 0f
    var flashT = 0f
    var strafe = 1f
    var strafeT = 0f
    var stuckT = 0f
    var escapeT = 0f
    var escX = 0f
    var escZ = 0f
    var wanderT = 0f
    var wanderX = 0f
    var wanderZ = 0f
    var anim = "idle"

    // Capture the Flag
    var defender = false              // true = guards the base, false = goes for the flag
    var path: FloatArray? = null      // waypoints x, z, x, z ...
    var pathI = 0
    var pathT = 0f
    var pathGX = 0f
    var pathGZ = 0f
}

internal class ScoreRow(val name: String, val kills: Int, val deaths: Int, val team: Int, val isYou: Boolean)

// blueBots / redBots: how many bots on each team. Used only in Team Deathmatch and Capture the Flag
// (-1 = not given: the bots are split in half).
internal class MatchState(
    val mode: GameMode,
    requestedBots: Int,
    timeLimitMin: Int,
    val captureLimit: Int = 0,
    blueBots: Int = -1,
    redBots: Int = -1
) {
    val ctf = mode == GameMode.CAPTURE_FLAG
    val teamMode = mode == GameMode.TEAM_DEATHMATCH || ctf
    val bots: List<Bot>

    init {
        val n: Int
        val nBlue: Int
        if (teamMode && blueBots >= 0 && redBots >= 0) {
            n = (blueBots + redBots).coerceIn(1, MatchSettings.MAX_BOTS)
            nBlue = blueBots.coerceAtMost(n - 1)          // always at least one enemy bot
        } else {
            n = requestedBots.coerceIn(1, MatchSettings.MAX_BOTS)
            nBlue = n / 2
        }
        bots = List(n) { i -> Bot(i + 1, if (teamMode) (if (i < nBlue) 0 else 1) else i + 1) }
        // Capture the Flag roles: in each team the 1st bot attacks, the 2nd defends, the 3rd attacks ...
        val seen = IntArray(2)
        if (teamMode) {
            for (b in bots) {
                b.defender = seen[b.team] % 2 == 1
                seen[b.team]++
            }
        }
    }

    // ---- numbers the HUD shows (Compose state) ----
    var playerHp by mutableIntStateOf(PLAYER_MAX_HP)
    var playerDead by mutableStateOf(false)
    var respawnSec by mutableIntStateOf(0)
    var timeSec by mutableIntStateOf(timeLimitMin * 60)
    var over by mutableStateOf(false)
    var resultText by mutableStateOf("")
    var feed by mutableStateOf("")
    var board by mutableIntStateOf(0)          // goes up every time a score changes
    var teamA by mutableIntStateOf(0)          // your team (kills in Team Deathmatch, captures in Capture the Flag)
    var teamB by mutableIntStateOf(0)          // enemy team

    // LIVE counts for the HUD icons (top right): friends still alive (you included) and enemies still alive
    var friendsAlive by mutableIntStateOf(if (teamMode) 1 + bots.count { it.team == 0 } else 1)
    var enemiesAlive by mutableIntStateOf(if (teamMode) bots.count { it.team != 0 } else bots.size)

    // Capture the Flag numbers for the HUD
    var ownFlag by mutableIntStateOf(F_HOME)       // state of YOUR flag (F_HOME / F_CARRIED / F_DROPPED)
    var enemyFlag by mutableIntStateOf(F_HOME)     // state of the ENEMY flag
    var youCarry by mutableStateOf(false)          // you are carrying the enemy flag
    var objText by mutableStateOf("")              // "GET THE ENEMY FLAG  34 m"

    val kills = IntArray(bots.size + 1)
    val deaths = IntArray(bots.size + 1)

    // ---- the two flags (index 0 = your team's flag, index 1 = the enemy team's flag). Game3DView draws them. ----
    val flagState = IntArray(2)
    val flagCarrier = IntArray(2) { -1 }
    val flagX = FloatArray(2)       // where the flag is drawn (follows the carrier on his back)
    val flagY = FloatArray(2)
    val flagZ = FloatArray(2)
    val homeX = FloatArray(2)       // the bases
    val homeY = FloatArray(2)
    val homeZ = FloatArray(2)
    private val flagDropT = FloatArray(2)
    private var nav: NavGrid? = null

    // ---- hit spots for the sparks / blood (filled here, emptied by Game3DView every frame) ----
    // each entry = [x, y, z, kind]   kind 0 = bullet hit a wall or your shield (sparks), kind 1 = bullet hit a fighter (blood)
    val hitFx = ArrayList<FloatArray>()

    // ---- bot bullets (drawn by Game3DView) ----
    val bAlive = BooleanArray(BOT_BULLET_COUNT)
    val bx = FloatArray(BOT_BULLET_COUNT)
    val by = FloatArray(BOT_BULLET_COUNT)
    val bz = FloatArray(BOT_BULLET_COUNT)
    val bdx = FloatArray(BOT_BULLET_COUNT)
    val bdz = FloatArray(BOT_BULLET_COUNT)
    val bDist = FloatArray(BOT_BULLET_COUNT)
    private val bOwner = IntArray(BOT_BULLET_COUNT)

    private var timeLeft = timeLimitMin * 60f
    private var feedT = 0f
    private var playerRespawnT = 0f
    private var spawned = false
    private var punchDone = false

    fun myKills(): Int = if (board >= 0) kills[0] else 0

    fun rows(): List<ScoreRow> {
        if (board < 0) return emptyList()
        val list = ArrayList<ScoreRow>()
        for (id in 0..bots.size) {
            list.add(ScoreRow(if (id == 0) "YOU" else "BOT $id", kills[id], deaths[id], teamOf(id), id == 0))
        }
        return list.sortedByDescending { it.kills }
    }

    private fun addHit(x: Float, y: Float, z: Float, kind: Int) {
        if (hitFx.size < 40) hitFx.add(floatArrayOf(x, y, z, kind.toFloat()))
    }

    private fun say(text: String) {
        feed = text
        feedT = 3.5f
    }

    // Counts who is really alive right now (called every frame)
    private fun refreshCounts() {
        var f = if (playerDead) 0 else 1
        var e = 0
        for (b in bots) {
            if (!b.alive) continue
            if (teamMode && b.team == 0) f++ else e++
        }
        if (f != friendsAlive) friendsAlive = f
        if (e != enemiesAlive) enemiesAlive = e
    }

    // ------------------------------------------------------------------
    // Called once per frame from Game3DView.stepGame
    // ------------------------------------------------------------------
    fun update(dt: Float, rig: GameInput) {
        if (over) return
        val play = rig.play ?: return
        if (!spawned) {
            spawned = true
            if (ctf) initCtf(play)
            for (b in bots) spawnBot(b, rig, play)
        }

        timeLeft -= dt
        val ts = ceil(timeLeft).toInt().coerceAtLeast(0)
        if (ts != timeSec) timeSec = ts
        if (feedT > 0f) {
            feedT -= dt
            if (feedT <= 0f) feed = ""
        }

        if (playerDead) {
            playerRespawnT -= dt
            val s = ceil(playerRespawnT).toInt().coerceAtLeast(0)
            if (s != respawnSec) respawnSec = s
            if (playerRespawnT <= 0f) respawnPlayer(rig, play)
        } else {
            doPunch(rig)
        }

        for (b in bots) updateBot(b, dt, rig, play)
        updateBotBullets(dt, rig, play)
        if (ctf) updateCtf(dt, rig)
        refreshCounts()

        if (timeLeft <= 0f) endByTime()
    }

    // One bullet step. Returns true if the bullet hit a fighter (the bullet is then used up).
    // owner = fighter id of whoever shot it (0 = you).
    fun bulletStep(x: Float, y: Float, z: Float, owner: Int, rig: GameInput): Boolean {
        if (over) return false
        for (id in 0..bots.size) {
            if (id == owner || !isAlive(id) || teamOf(id) == teamOf(owner)) continue
            val dx = x - posX(id, rig)
            val dz = z - posZ(id, rig)
            if (dx * dx + dz * dz > HIT_RADIUS * HIT_RADIUS) continue
            val fy = feetY(id, rig)
            if (y < fy || y > fy + HIT_HEIGHT) continue
            val dmg = if (owner == 0) PLAYER_BULLET_DMG else BOT_BULLET_DMG
            if (id == 0 && rig.shieldOn) {
                // the shield took the hit: sparks (no blood) + tell the shield effect where it was hit
                addHit(x, y, z, 0)
                val sx = x - rig.x
                val sy = y - (rig.y + rig.jumpH + SHIELD_CENTER_Y)
                val sz = z - rig.z
                val l = hypot(hypot(sx, sy), sz)
                if (l > 0.001f) {
                    rig.shieldDx = sx / l
                    rig.shieldDy = sy / l
                    rig.shieldDz = sz / l
                } else {
                    rig.shieldDx = 0f
                    rig.shieldDy = 0f
                    rig.shieldDz = 1f
                }
                rig.shieldHitT = SHIELD_HIT_SEC
            } else {
                addHit(x, y, z, 1)                   // blood
            }
            if (id == 0) damagePlayer(dmg, owner, rig) else damageBot(bots[id - 1], dmg, owner)
            return true
        }
        return false
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private fun teamOf(id: Int) = if (id == 0) 0 else bots[id - 1].team
    private fun isAlive(id: Int) = if (id == 0) !playerDead else bots[id - 1].alive
    private fun posX(id: Int, rig: GameInput) = if (id == 0) rig.x else bots[id - 1].x
    private fun posZ(id: Int, rig: GameInput) = if (id == 0) rig.z else bots[id - 1].z
    private fun feetY(id: Int, rig: GameInput) = if (id == 0) rig.y + rig.jumpH else bots[id - 1].y
    private fun nameOf(id: Int) = if (id == 0) "YOU" else "BOT $id"

    private fun los(play: PlayArena, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float): Boolean {
        val dx = x1 - x0
        val dz = z1 - z0
        val d = hypot(dx, dz)
        val n = (d / 0.3f).toInt().coerceAtLeast(1)
        for (i in 1 until n) {
            val t = i.toFloat() / n
            if (play.bulletHits(x0 + dx * t, y0 + (y1 - y0) * t, z0 + dz * t)) return false
        }
        return true
    }

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------
    // The whole body fits: the centre AND a ring of 8 points around it (SPAWN_CLEAR meters out) are free
    // at knee, chest and head height. So he can never appear half inside a wall.
    private fun clearOk(play: PlayArena, x: Float, z: Float): Boolean {
        val floor = play.groundY(play.blueX, play.blueZ, 0.3f)     // the normal floor height of this arena
        val g = play.groundY(x, z, 0.3f)
        if (g > floor + 0.5f) return false                          // not on top of crates or ramps
        val heights = floatArrayOf(g + 0.4f, g + 1.0f, g + 1.6f)
        for (h in heights) {
            if (play.bulletHits(x, h, z)) return false
            for (k in 0 until 8) {
                val a = k * (PI.toFloat() / 4f)
                if (play.bulletHits(x + cos(a) * SPAWN_CLEAR, h, z + sin(a) * SPAWN_CLEAR)) return false
            }
        }
        return true
    }

    // Deathmatch spawn: clear body room AND inside the arena (a clear line from the blue spawn or from the middle)
    private fun spawnOk(play: PlayArena, x: Float, z: Float): Boolean {
        if (!clearOk(play, x, z)) return false
        return los(play, play.blueX, 1.2f, play.blueZ, x, 1.2f, z) || los(play, 0f, 1.2f, 0f, x, 1.2f, z)
    }

    // Picks a random free spot that is far from the other living fighters.
    // If no good spot is found, falls back to the blue spawn point (the one you start the match on, always free).
    private fun pickSpawn(rig: GameInput, play: PlayArena, selfId: Int): FloatArray {
        val r = max(max(abs(play.blueX), abs(play.blueZ)), 8f) * SPAWN_SCALE
        var bestX = play.blueX
        var bestZ = play.blueZ
        var bestScore = -1f
        var tries = 0
        var found = 0
        while (tries < 150 && found < 10) {
            tries++
            val x = (Random.nextFloat() * 2f - 1f) * r
            val z = (Random.nextFloat() * 2f - 1f) * r
            if (!spawnOk(play, x, z)) continue
            found++
            var minD = 999f
            for (id in 0..bots.size) {
                if (id == selfId || !isAlive(id)) continue
                val d = hypot(posX(id, rig) - x, posZ(id, rig) - z)
                if (d < minD) minD = d
            }
            if (minD > bestScore) {
                bestScore = minD
                bestX = x
                bestZ = z
            }
        }
        return floatArrayOf(bestX, bestZ)
    }

    // Capture the Flag spawn: a free spot near your own flag, not right on top of it
    private fun pickBaseSpawn(play: PlayArena, team: Int): FloatArray {
        val hx = homeX[team]
        val hz = homeZ[team]
        var tries = 0
        while (tries < 60) {
            tries++
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val r = 1.8f + Random.nextFloat() * (BASE_SPAWN_R - 1.8f)
            val x = hx + cos(a) * r
            val z = hz + sin(a) * r
            if (clearOk(play, x, z)) return floatArrayOf(x, z)
        }
        return floatArrayOf(hx + (if (hx > 0f) -2f else 2f), hz)   // fallback: just in front of the flag
    }

    private fun spawnBot(b: Bot, rig: GameInput, play: PlayArena) {
        val p = if (ctf) pickBaseSpawn(play, b.team) else pickSpawn(rig, play, b.id)
        b.x = p[0]
        b.z = p[1]
        b.y = play.groundY(b.x, b.z, 0.3f)
        b.hp = BOT_MAX_HP
        b.alive = true
        b.speed = 0f
        b.target = -1
        b.hasLos = false
        b.thinkT = 0f
        b.fireT = 0.8f + Random.nextFloat()
        b.flashT = 0f
        b.escapeT = 0f
        b.stuckT = 0f
        b.path = null
        b.pathT = 0f
        b.faceDeg = atan2(b.z, -b.x) * M_RAD + M_FACING_OFFSET   // look toward the middle
        b.anim = "idle"
    }

    private fun respawnPlayer(rig: GameInput, play: PlayArena) {
        val p = if (ctf) pickBaseSpawn(play, 0) else pickSpawn(rig, play, 0)
        rig.x = p[0]
        rig.z = p[1]
        rig.y = play.groundY(p[0], p[1], 0.3f)
        rig.jumpH = 0f
        rig.jumpV = 0f
        rig.jumpState = 0
        rig.punchClip = ""
        val dx = -p[0]
        val dz = -p[1]
        rig.faceDeg = atan2(-dz, dx) * M_RAD + M_FACING_OFFSET
        rig.camYaw = atan2(dx, -dz) * M_RAD         // camera looks toward the middle
        rig.camPitch = 0f
        playerHp = PLAYER_MAX_HP
        playerDead = false
        respawnSec = 0
    }

    // ------------------------------------------------------------------
    // Damage, kills, score
    // ------------------------------------------------------------------
    private fun damagePlayer(amount: Int, attacker: Int, rig: GameInput) {
        if (playerDead || over) return
        // Shield on: you take SHIELD_KEEP of the damage (0 = the bullet does nothing to your HP).
        // Shield off: you take the full damage (at least 1).
        val taken = if (rig.shieldOn) (amount * SHIELD_KEEP).roundToInt() else amount.coerceAtLeast(1)
        if (taken <= 0) return
        playerHp = (playerHp - taken).coerceAtLeast(0)
        if (playerHp <= 0) {
            playerDead = true
            playerRespawnT = RESPAWN_SECONDS
            respawnSec = ceil(RESPAWN_SECONDS).toInt()
            if (ctf) dropFlagAt(0, rig.x, rig.y, rig.z)
            registerKill(0, attacker)
        }
    }

    private fun damageBot(b: Bot, amount: Int, attacker: Int) {
        if (!b.alive || over) return
        b.hp -= amount
        if (b.hp <= 0) {
            b.hp = 0
            b.alive = false
            b.respawnT = RESPAWN_SECONDS
            b.speed = 0f
            if (ctf) dropFlagAt(b.id, b.x, b.y, b.z)
            registerKill(b.id, attacker)
        }
    }

    private fun registerKill(victim: Int, attacker: Int) {
        deaths[victim]++
        if (attacker >= 0 && attacker != victim) {
            kills[attacker]++
            // Team Deathmatch counts kills for the team. Capture the Flag counts captures instead.
            if (teamMode && !ctf) {
                if (teamOf(attacker) == 0) teamA++ else teamB++
            }
        }
        // a flag message (taken / dropped) shown a moment ago stays on screen; the kill message replaces it
        feed = when {
            attacker == 0 -> "You eliminated ${nameOf(victim)}"
            victim == 0 -> "${nameOf(attacker)} eliminated you"
            else -> "${nameOf(attacker)} eliminated ${nameOf(victim)}"
        }
        feedT = 3f
        board++
        refreshCounts()

        if (ctf) {
            // no kill limit in Capture the Flag: only captures (or the clock) decide the match
        } else if (teamMode) {
            if (teamA >= KILL_LIMIT_TDM) finish("YOUR TEAM WINS")
            else if (teamB >= KILL_LIMIT_TDM) finish("ENEMY TEAM WINS")
        } else if (attacker >= 0 && kills[attacker] >= KILL_LIMIT_DM) {
            finish(if (attacker == 0) "YOU WIN" else "${nameOf(attacker)} WINS")
        }
    }

    private fun finish(text: String) {
        if (!over) {
            over = true
            resultText = text
        }
    }

    private fun endByTime() {
        if (teamMode) {
            finish(if (teamA > teamB) "YOUR TEAM WINS" else if (teamB > teamA) "ENEMY TEAM WINS" else "DRAW")
        } else {
            var best = 0
            var tie = false
            for (id in 1..bots.size) {
                if (kills[id] > kills[best]) { best = id; tie = false }
                else if (kills[id] == kills[best]) tie = true
            }
            finish(if (tie) "DRAW" else if (best == 0) "YOU WIN" else "${nameOf(best)} WINS")
        }
    }

    // Fists: one hit per punch, on the enemies in front of you
    private fun doPunch(rig: GameInput) {
        if (rig.punchClip.isEmpty()) { punchDone = false; return }
        if (punchDone || rig.punchT < 0.15f) return
        punchDone = true
        val fr = (rig.faceDeg - M_FACING_OFFSET) * M_DEG
        val sx = cos(fr)
        val sz = -sin(fr)
        for (b in bots) {
            if (!b.alive || b.team == 0 && teamMode) continue
            val dx = b.x - rig.x
            val dz = b.z - rig.z
            val d = hypot(dx, dz)
            if (d > PUNCH_RANGE || d < 0.01f) continue
            if ((dx * sx + dz * sz) / d < 0.3f) continue
            addHit(b.x, b.y + 1.3f, b.z, 1)
            damageBot(b, PUNCH_DMG, 0)
        }
    }

    // ------------------------------------------------------------------
    // Capture the Flag
    // ------------------------------------------------------------------
    private fun initCtf(play: PlayArena) {
        homeX[0] = play.blueX
        homeZ[0] = play.blueZ
        homeX[1] = play.redX
        homeZ[1] = play.redZ
        for (t in 0..1) {
            homeY[t] = play.groundY(homeX[t], homeZ[t], 0.3f)
            flagState[t] = F_HOME
            flagCarrier[t] = -1
            flagX[t] = homeX[t]
            flagY[t] = homeY[t]
            flagZ[t] = homeZ[t]
        }
        nav = NavGrid(play)
    }

    // Called every frame in Capture the Flag: pickups, returns, captures, flag positions, HUD text
    private fun updateCtf(dt: Float, rig: GameInput) {
        // a flag lying on the ground goes home by itself after a while
        for (t in 0..1) {
            if (flagState[t] == F_DROPPED) {
                flagDropT[t] -= dt
                if (flagDropT[t] <= 0f) returnFlag(t)
            }
        }

        for (id in 0..bots.size) {
            if (over) break
            if (!isAlive(id)) continue
            val team = teamOf(id)
            val enemy = 1 - team
            val px = posX(id, rig)
            val pz = posZ(id, rig)

            // take the enemy flag (from its base or from the ground)
            if (flagState[enemy] != F_CARRIED && hypot(px - flagX[enemy], pz - flagZ[enemy]) < FLAG_PICK_R) {
                flagState[enemy] = F_CARRIED
                flagCarrier[enemy] = id
                say(
                    when {
                        id == 0 -> "You took the enemy flag!"
                        team == 0 -> "${nameOf(id)} took the enemy flag"
                        else -> "${nameOf(id)} took YOUR flag!"
                    }
                )
            }

            // your own flag lying on the ground: touch it to send it home
            if (flagState[team] == F_DROPPED && hypot(px - flagX[team], pz - flagZ[team]) < FLAG_PICK_R) {
                returnFlag(team)
            }

            // score: carrying the enemy flag into your base while your own flag is home
            if (flagState[enemy] == F_CARRIED && flagCarrier[enemy] == id &&
                flagState[team] == F_HOME &&
                hypot(px - homeX[team], pz - homeZ[team]) < FLAG_CAP_R
            ) {
                capture(team, id)
            }
        }

        updateFlagPositions(rig)
        updateObjective(rig)
    }

    private fun capture(team: Int, id: Int) {
        val enemy = 1 - team
        flagState[enemy] = F_HOME
        flagCarrier[enemy] = -1
        if (team == 0) teamA++ else teamB++
        board++
        say(
            when {
                id == 0 -> "You captured the flag!"
                team == 0 -> "${nameOf(id)} captured the flag!"
                else -> "The enemy team captured your flag!"
            }
        )
        if (captureLimit > 0) {
            if (teamA >= captureLimit) finish("YOUR TEAM WINS")
            else if (teamB >= captureLimit) finish("ENEMY TEAM WINS")
        }
    }

    private fun returnFlag(t: Int) {
        flagState[t] = F_HOME
        flagCarrier[t] = -1
        say(if (t == 0) "Your flag was returned" else "The enemy flag was returned")
    }

    // The carrier was eliminated: the flag stays where he fell
    private fun dropFlagAt(id: Int, x: Float, y: Float, z: Float) {
        for (t in 0..1) {
            if (flagState[t] == F_CARRIED && flagCarrier[t] == id) {
                flagState[t] = F_DROPPED
                flagCarrier[t] = -1
                flagX[t] = x
                flagY[t] = y
                flagZ[t] = z
                flagDropT[t] = FLAG_RETURN_SEC
                say(if (t == 0) "Your flag was dropped" else "The enemy flag was dropped")
            }
        }
    }

    // Where each flag is drawn: at its base, on the ground, or on the back of its carrier
    private fun updateFlagPositions(rig: GameInput) {
        for (t in 0..1) {
            if (flagState[t] == F_HOME) {
                flagX[t] = homeX[t]
                flagY[t] = homeY[t]
                flagZ[t] = homeZ[t]
            } else if (flagState[t] == F_CARRIED) {
                val c = flagCarrier[t]
                if (c < 0) continue
                val face = if (c == 0) rig.faceDeg else bots[c - 1].faceDeg
                val fr = (face - M_FACING_OFFSET) * M_DEG
                flagX[t] = posX(c, rig) - cos(fr) * 0.35f
                flagZ[t] = posZ(c, rig) + sin(fr) * 0.35f
                flagY[t] = feetY(c, rig) + 0.7f
            }
        }
    }

    // The text under the scoreboard: what you should do now and how far it is
    private fun updateObjective(rig: GameInput) {
        if (flagState[0] != ownFlag) ownFlag = flagState[0]
        if (flagState[1] != enemyFlag) enemyFlag = flagState[1]
        val carry = !playerDead && flagState[1] == F_CARRIED && flagCarrier[1] == 0
        if (carry != youCarry) youCarry = carry

        var txt = ""
        if (!playerDead) {
            val tx: Float
            val tz: Float
            val label: String
            if (carry && flagState[0] == F_HOME) {
                tx = homeX[0]; tz = homeZ[0]; label = "TAKE IT TO YOUR BASE"
            } else if (carry) {
                tx = flagX[0]; tz = flagZ[0]; label = "GET YOUR FLAG BACK TO SCORE"
            } else if (flagState[0] == F_CARRIED) {
                tx = flagX[0]; tz = flagZ[0]; label = "CATCH THE FLAG CARRIER"
            } else if (flagState[0] == F_DROPPED) {
                tx = flagX[0]; tz = flagZ[0]; label = "RETURN YOUR FLAG"
            } else if (flagState[1] == F_CARRIED) {
                tx = flagX[1]; tz = flagZ[1]; label = "PROTECT YOUR FLAG CARRIER"
            } else {
                tx = flagX[1]; tz = flagZ[1]; label = "GET THE ENEMY FLAG"
            }
            val d = (hypot(tx - rig.x, tz - rig.z) / 2f).toInt() * 2
            txt = "$label  $d m"
        }
        if (txt != objText) objText = txt
    }

    // Where a bot wants to go in Capture the Flag (null = nothing special, just fight / wander)
    private fun ctfGoal(b: Bot): FloatArray? {
        val t = b.team
        val e = 1 - t
        // carrying the enemy flag: go home. If our own flag is not at home we cannot score,
        // so go and get our own flag back first (chase the carrier or pick it up from the ground)
        if (flagState[e] == F_CARRIED && flagCarrier[e] == b.id) {
            return if (flagState[t] == F_HOME) floatArrayOf(homeX[t], homeZ[t]) else floatArrayOf(flagX[t], flagZ[t])
        }
        // our flag is lying on the ground: defenders (and anyone close) send it home
        if (flagState[t] == F_DROPPED) {
            val d = hypot(flagX[t] - b.x, flagZ[t] - b.z)
            if (b.defender || d < 22f) return floatArrayOf(flagX[t], flagZ[t])
        }
        // an enemy has our flag: everybody chases him
        if (flagState[t] == F_CARRIED) return floatArrayOf(flagX[t], flagZ[t])
        if (!b.defender) {
            // attackers: go for the enemy flag, or stay with the teammate who carries it
            if (flagState[e] != F_CARRIED) return floatArrayOf(flagX[e], flagZ[e])
            val d = hypot(flagX[e] - b.x, flagZ[e] - b.z)
            return if (d < 4f) null else floatArrayOf(flagX[e], flagZ[e])
        }
        // defenders: stay near the base
        val d = hypot(homeX[t] - b.x, homeZ[t] - b.z)
        return if (d > 8f) floatArrayOf(homeX[t], homeZ[t]) else null
    }

    private fun dirTo(dx: Float, dz: Float): FloatArray {
        val l = hypot(dx, dz)
        return if (l > 0.001f) floatArrayOf(dx / l, dz / l) else floatArrayOf(0f, 0f)
    }

    // Direction for a bot that walks to (gx, gz): along a path around the walls, cutting corners when it can
    private fun navDir(b: Bot, gx: Float, gz: Float, dt: Float): FloatArray {
        val n = nav
        val d = hypot(gx - b.x, gz - b.z)
        if (n == null || d < 3f) return dirTo(gx - b.x, gz - b.z)
        b.pathT -= dt
        if (b.path == null || b.pathT <= 0f || hypot(gx - b.pathGX, gz - b.pathGZ) > 3f) {
            b.path = n.path(b.x, b.z, gx, gz)
            b.pathI = 0
            b.pathGX = gx
            b.pathGZ = gz
            b.pathT = 0.8f + Random.nextFloat() * 0.4f
        }
        val p = b.path ?: return dirTo(gx - b.x, gz - b.z)
        val count = p.size / 2
        if (count == 0) return dirTo(gx - b.x, gz - b.z)
        // skip the waypoints he already reached
        while (b.pathI < count - 1 && hypot(p[b.pathI * 2] - b.x, p[b.pathI * 2 + 1] - b.z) < 0.9f) b.pathI++
        // aim at the farthest of the next few waypoints that can be walked to in a straight line
        var best = b.pathI
        var k = b.pathI + 1
        while (k < count && k <= b.pathI + 5) {
            if (n.lineFree(b.x, b.z, p[k * 2], p[k * 2 + 1])) best = k
            k++
        }
        return dirTo(p[best * 2] - b.x, p[best * 2 + 1] - b.z)
    }

    // ------------------------------------------------------------------
    // Bot brain
    // ------------------------------------------------------------------
    private fun think(b: Bot, rig: GameInput, play: PlayArena) {
        var bestVis = -1
        var bestVisD = Float.MAX_VALUE
        var bestAny = -1
        var bestAnyD = Float.MAX_VALUE
        for (id in 0..bots.size) {
            if (id == b.id || !isAlive(id) || teamOf(id) == b.team) continue
            val tx = posX(id, rig)
            val tz = posZ(id, rig)
            val d = hypot(tx - b.x, tz - b.z)
            if (d < bestAnyD) { bestAnyD = d; bestAny = id }
            if (d <= BOT_SIGHT && d < bestVisD &&
                los(play, b.x, b.y + 1.2f, b.z, tx, feetY(id, rig) + 1.2f, tz)
            ) {
                bestVisD = d
                bestVis = id
            }
        }
        if (bestVis >= 0) { b.target = bestVis; b.hasLos = true }
        else { b.target = bestAny; b.hasLos = false }
    }

    private fun updateBot(b: Bot, dt: Float, rig: GameInput, play: PlayArena) {
        if (!b.alive) {
            b.respawnT -= dt
            if (b.respawnT <= 0f) spawnBot(b, rig, play)
            return
        }

        b.thinkT -= dt
        if (b.thinkT <= 0f || (b.target >= 0 && !isAlive(b.target))) {
            b.thinkT = 0.25f + Random.nextFloat() * 0.2f
            think(b, rig, play)
        }
        b.fireT -= dt
        b.flashT -= dt
        b.strafeT -= dt
        if (b.strafeT <= 0f) {
            b.strafeT = 1f + Random.nextFloat() * 1.5f
            b.strafe = if (Random.nextBoolean()) 1f else -1f
        }

        // ---- where does he want to go ----
        var wantX = 0f
        var wantZ = 0f
        var wantSpeed = 0f
        var dist = 0f
        var toX = 0f
        var toZ = 0f
        val t = b.target
        val haveTarget = t >= 0 && isAlive(t)
        if (haveTarget) {
            val dx = posX(t, rig) - b.x
            val dz = posZ(t, rig) - b.z
            dist = hypot(dx, dz)
            if (dist > 0.01f) { toX = dx / dist; toZ = dz / dist }
            if (!b.hasLos || dist > 14f) {
                wantX = toX; wantZ = toZ; wantSpeed = BOT_RUN          // go and find him
            } else if (dist < 5f) {
                wantX = -toX * 0.6f - toZ * b.strafe                   // too close: back off and strafe
                wantZ = -toZ * 0.6f + toX * b.strafe
                wantSpeed = BOT_WALK
            } else {
                wantX = -toZ * b.strafe                                // good range: strafe
                wantZ = toX * b.strafe
                wantSpeed = BOT_WALK
            }
        } else {
            b.wanderT -= dt
            if (b.wanderT <= 0f) {
                b.wanderT = 2f + Random.nextFloat() * 2f
                val a = Random.nextFloat() * 2f * PI.toFloat()
                b.wanderX = cos(a)
                b.wanderZ = sin(a)
            }
            wantX = b.wanderX; wantZ = b.wanderZ; wantSpeed = BOT_WALK
        }

        // ---- Capture the Flag: go for the flag / bring it home / guard the base ----
        // He keeps fighting only when an enemy is close and in sight (and he is not carrying the flag).
        var objective = false
        if (ctf) {
            val goal = ctfGoal(b)
            if (goal != null) {
                val carrying = flagState[1 - b.team] == F_CARRIED && flagCarrier[1 - b.team] == b.id
                val fighting = !carrying && haveTarget && b.hasLos && dist < 10f
                if (!fighting) {
                    val dir = navDir(b, goal[0], goal[1], dt)
                    wantX = dir[0]
                    wantZ = dir[1]
                    wantSpeed = BOT_RUN
                    objective = true
                }
            }
        }

        if (b.escapeT > 0f) {                                           // stuck on a wall: go another way
            b.escapeT -= dt
            wantX = b.escX; wantZ = b.escZ; wantSpeed = BOT_RUN * 0.8f
        }
        val wl = hypot(wantX, wantZ)
        if (wl > 0.001f) {
            wantX /= wl; wantZ /= wl
            b.dirX = wantX; b.dirZ = wantZ
        } else {
            wantSpeed = 0f
        }

        // ---- move (same wall rules as the player) ----
        val maxChange = 9f * dt
        b.speed += (wantSpeed - b.speed).coerceIn(-maxChange, maxChange)
        val oldX = b.x
        val oldZ = b.z
        if (b.speed > 0.05f) {
            val nx = b.x + b.dirX * b.speed * dt
            val nz = b.z + b.dirZ * b.speed * dt
            play.tryMove(b.x, b.z, nx, nz, b.y)
            b.x = play.outX
            b.z = play.outZ
        }
        // do not stand inside other fighters
        var px = 0f
        var pz = 0f
        for (id in 0..bots.size) {
            if (id == b.id || !isAlive(id)) continue
            val ox = b.x - posX(id, rig)
            val oz = b.z - posZ(id, rig)
            val od = hypot(ox, oz)
            if (od < 0.8f && od > 0.001f) {
                val push = (0.8f - od) * 0.5f
                px += ox / od * push
                pz += oz / od * push
            }
        }
        if (px != 0f || pz != 0f) {
            play.tryMove(b.x, b.z, b.x + px, b.z + pz, b.y)
            b.x = play.outX
            b.z = play.outZ
        }
        val moved = hypot(b.x - oldX, b.z - oldZ)
        if (b.speed > 1f && moved < b.speed * dt * 0.25f) b.stuckT += dt else b.stuckT = max(0f, b.stuckT - dt)
        if (b.stuckT > 0.5f) {
            b.stuckT = 0f
            b.escapeT = 1f
            val a = Random.nextFloat() * 2f * PI.toFloat()
            b.escX = cos(a)
            b.escZ = sin(a)
        }
        val g = play.groundY(b.x, b.z, b.y)
        b.y += (g - b.y) * min(1f, dt * 20f)

        // ---- turn ----
        // on the way to a flag he faces where he runs, unless an enemy is very close
        val aimAtTarget = haveTarget && b.hasLos && dist > 0.01f && (!objective || dist < 9f)
        var fdx = b.dirX
        var fdz = b.dirZ
        var turn = false
        if (aimAtTarget) { fdx = toX; fdz = toZ; turn = true }
        else if (b.speed > 0.15f) turn = true
        if (turn) {
            val target = atan2(-fdz, fdx) * M_RAD + M_FACING_OFFSET
            var diff = target - b.faceDeg
            while (diff > 180f) diff -= 360f
            while (diff < -180f) diff += 360f
            val maxTurn = BOT_TURN * dt
            b.faceDeg += diff.coerceIn(-maxTurn, maxTurn)
        }

        // ---- shoot ----
        if (aimAtTarget && dist <= BOT_SHOOT_RANGE && b.fireT <= 0f) {
            val fr = (b.faceDeg - M_FACING_OFFSET) * M_DEG
            val sx = cos(fr)
            val sz = -sin(fr)
            if (sx * toX + sz * toZ > 0.97f) {
                val mx = b.x + sx * 0.9f - sz * 0.25f
                val mz = b.z + sz * 0.9f + sx * 0.25f
                val my = b.y + 1.15f
                var ddx = posX(t, rig) - mx
                var ddz = posZ(t, rig) - mz
                val dl = hypot(ddx, ddz)
                if (dl > 0.01f) { ddx /= dl; ddz /= dl } else { ddx = sx; ddz = sz }
                val ang = (Random.nextFloat() * 2f - 1f) * BOT_SPREAD_DEG * M_DEG
                val rx = ddx * cos(ang) - ddz * sin(ang)
                val rz = ddx * sin(ang) + ddz * cos(ang)
                spawnBullet(b.id, mx, my, mz, rx, rz)
                b.fireT = BOT_FIRE_INTERVAL + Random.nextFloat() * 0.2f
                b.flashT = 0.3f
            }
        }

        // ---- which animation to play ----
        val base = if (b.speed < 0.15f) "idle" else if (b.speed > (BOT_WALK + BOT_RUN) / 2f) "run" else "walk"
        b.anim = if (b.flashT > 0f) (if (base == "idle") "shoot" else base + "_shoot") else base
    }

    // ------------------------------------------------------------------
    // Bot bullets
    // ------------------------------------------------------------------
    private fun spawnBullet(owner: Int, x: Float, y: Float, z: Float, dx: Float, dz: Float) {
        val i = bAlive.indexOfFirst { !it }
        if (i < 0) return
        bAlive[i] = true
        bOwner[i] = owner
        bx[i] = x
        by[i] = y
        bz[i] = z
        bdx[i] = dx
        bdz[i] = dz
        bDist[i] = 0f
    }

    private fun updateBotBullets(dt: Float, rig: GameInput, play: PlayArena) {
        for (i in 0 until BOT_BULLET_COUNT) {
            if (!bAlive[i]) continue
            var left = BOT_BULLET_SPEED * dt
            while (left > 0f && bAlive[i]) {
                val step = min(0.25f, left)
                val nx = bx[i] + bdx[i] * step
                val nz = bz[i] + bdz[i] * step
                val wall = play.bulletHits(nx, by[i], nz)
                if (wall || bDist[i] > BOT_BULLET_RANGE) {
                    if (wall) addHit(bx[i], by[i], bz[i], 0)      // sparks on the wall
                    bAlive[i] = false
                } else if (bulletStep(nx, by[i], nz, bOwner[i], rig)) {
                    bAlive[i] = false
                } else {
                    bx[i] = nx
                    bz[i] = nz
                    bDist[i] += step
                }
                left -= step
            }
        }
    }
}

// =====================================================================
// NavGrid: a simple path finder for the bots (used in Capture the Flag).
// The arena is cut into 1 m squares. A square is "free" when a person can stand in the middle of it
// (the same wall rules as tryMove). path() finds the shortest way between two spots with A*.
// Built once when the match starts.
// =====================================================================
internal class NavGrid(play: PlayArena) {
    private val w = (NAV_HALF_X * 2f / NAV_CELL).toInt()
    private val h = (NAV_HALF_Z * 2f / NAV_CELL).toInt()
    private val free = BooleanArray(w * h)
    private val cost = FloatArray(w * h)
    private val from = IntArray(w * h)
    private val closed = BooleanArray(w * h)

    init {
        for (j in 0 until h) {
            for (i in 0 until w) {
                val x = -NAV_HALF_X + (i + 0.5f) * NAV_CELL
                val z = -NAV_HALF_Z + (j + 0.5f) * NAV_CELL
                // tryMove only lets him arrive at the target spot when that spot is free
                play.tryMove(x - 0.02f, z, x, z, 0f)
                free[j * w + i] = abs(play.outX - x) < 0.001f && abs(play.outZ - z) < 0.001f
            }
        }
    }

    private fun cx(c: Int) = -NAV_HALF_X + (c % w + 0.5f) * NAV_CELL
    private fun cz(c: Int) = -NAV_HALF_Z + (c / w + 0.5f) * NAV_CELL

    private fun cellOf(x: Float, z: Float): Int {
        val i = ((x + NAV_HALF_X) / NAV_CELL).toInt().coerceIn(0, w - 1)
        val j = ((z + NAV_HALF_Z) / NAV_CELL).toInt().coerceIn(0, h - 1)
        return j * w + i
    }

    // the free square closest to (x, z), or -1
    private fun nearestFree(x: Float, z: Float): Int {
        val c = cellOf(x, z)
        if (free[c]) return c
        val ci = c % w
        val cj = c / w
        var best = -1
        var bestD = Int.MAX_VALUE
        for (r in 1..4) {
            for (dj in -r..r) {
                for (di in -r..r) {
                    val i = ci + di
                    val j = cj + dj
                    if (i < 0 || j < 0 || i >= w || j >= h) continue
                    val k = j * w + i
                    if (!free[k]) continue
                    val d = di * di + dj * dj
                    if (d < bestD) { bestD = d; best = k }
                }
            }
            if (best >= 0) return best
        }
        return -1
    }

    private fun heur(a: Int, b: Int): Float {
        val dx = abs(a % w - b % w)
        val dz = abs(a / w - b / w)
        return max(dx, dz) + 0.414f * min(dx, dz)
    }

    private fun key(f: Float, idx: Int): Long = ((f * 100f).toLong() shl 20) or idx.toLong()

    /** Walkable straight line (checked every 0.4 m against the free squares). */
    fun lineFree(x0: Float, z0: Float, x1: Float, z1: Float): Boolean {
        val d = hypot(x1 - x0, z1 - z0)
        val n = (d / 0.4f).toInt()
        for (i in 1..n) {
            val t = i * 0.4f / d
            if (!free[cellOf(x0 + (x1 - x0) * t, z0 + (z1 - z0) * t)]) return false
        }
        return true
    }

    /** Waypoints x, z, x, z ... from near (x0, z0) to near (x1, z1). null = no way found. */
    fun path(x0: Float, z0: Float, x1: Float, z1: Float): FloatArray? {
        val s = nearestFree(x0, z0)
        val e = nearestFree(x1, z1)
        if (s < 0 || e < 0) return null
        if (s == e) return floatArrayOf(cx(e), cz(e))
        java.util.Arrays.fill(cost, Float.MAX_VALUE)
        java.util.Arrays.fill(closed, false)
        val open = java.util.PriorityQueue<Long>()
        cost[s] = 0f
        from[s] = -1
        open.add(key(heur(s, e), s))
        var found = false
        var steps = 0
        while (open.isNotEmpty() && steps < 40000) {
            steps++
            val cur = (open.poll() and 0xFFFFFL).toInt()
            if (closed[cur]) continue
            closed[cur] = true
            if (cur == e) { found = true; break }
            val ci = cur % w
            val cj = cur / w
            for (dj in -1..1) {
                for (di in -1..1) {
                    if (di == 0 && dj == 0) continue
                    val i = ci + di
                    val j = cj + dj
                    if (i < 0 || j < 0 || i >= w || j >= h) continue
                    val nk = j * w + i
                    if (!free[nk] || closed[nk]) continue
                    // no cutting across the corner of a wall
                    if (di != 0 && dj != 0 && (!free[cj * w + i] || !free[j * w + ci])) continue
                    val c = cost[cur] + (if (di != 0 && dj != 0) 1.414f else 1f)
                    if (c < cost[nk]) {
                        cost[nk] = c
                        from[nk] = cur
                        open.add(key(c + heur(nk, e), nk))
                    }
                }
            }
        }
        if (!found) return null
        val list = ArrayList<Int>()
        var c = e
        while (c != -1 && c != s) {
            list.add(c)
            c = from[c]
        }
        list.reverse()
        val out = FloatArray(list.size * 2)
        for (n in list.indices) {
            out[n * 2] = cx(list[n])
            out[n * 2 + 1] = cz(list[n])
        }
        return out
    }
}
