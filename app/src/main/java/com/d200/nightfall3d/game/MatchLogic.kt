package com.d200.nightfall3d.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
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
// MatchLogic.kt  -  Deathmatch + Team Deathmatch (offline test, bots).
// No drawing in here. Game3DView.kt draws the bots (copies of the blue GLB) from the numbers in this file.
//
//   fighter id 0   = you
//   fighter id 1.. = bots
//   Deathmatch      : everybody is an enemy of everybody (each bot has its own team number)
//   Team Deathmatch : you + the first half of the bots = team 0 (allies), the rest = team 1 (enemies)
//                     bullets pass through allies (no friendly fire)
//
// CHANGES IN THIS VERSION (Build 1 fixes)
//  1) friendsAlive / enemiesAlive: live counts for the HUD icons at the top right.
//  2) Spawn / revive can no longer put you inside a wall (clearance check all around the body + safe fallback).
//  3) hitFx: a list of hit spots (x, y, z, kind) that Game3DView turns into sparks (wall) and blood (fighter).
// =====================================================================

object MatchSettings {
    var botCount = 5               // set by the Match Setup screen
    const val MAX_BOTS = 7         // more bots = more RAM (each bot is one more copy of the blue GLB + a gun)
}

internal const val PLAYER_MAX_HP = 200
internal const val BOT_MAX_HP = 100
internal const val BOT_BULLET_COUNT = 48

private const val PLAYER_BULLET_DMG = 20      // your bullet damage
private const val BOT_BULLET_DMG = 9          // bot bullet damage (low, so the test is fair)
private const val PUNCH_DMG = 40
private const val PUNCH_RANGE = 1.4f
private const val SHIELD_KEEP = 0.3f          // with the shield on you take only this part of the damage
private const val RESPAWN_SECONDS = 3f
private const val KILL_LIMIT_DM = 10          // first to this many kills wins (Deathmatch)
private const val KILL_LIMIT_TDM = 25         // first team to this many kills wins (Team Deathmatch)
private const val HIT_RADIUS = 0.45f
private const val HIT_HEIGHT = 1.8f

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
}

internal class ScoreRow(val name: String, val kills: Int, val deaths: Int, val team: Int, val isYou: Boolean)

internal class MatchState(val mode: GameMode, requestedBots: Int, timeLimitMin: Int) {
    val teamMode = mode == GameMode.TEAM_DEATHMATCH
    val bots: List<Bot>

    init {
        val n = requestedBots.coerceIn(1, MatchSettings.MAX_BOTS)
        bots = List(n) { i -> Bot(i + 1, if (teamMode) (if (i < n / 2) 0 else 1) else i + 1) }
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
    var teamA by mutableIntStateOf(0)          // your team
    var teamB by mutableIntStateOf(0)          // enemy team

    // LIVE counts for the HUD icons (top right): friends still alive (you included) and enemies still alive
    var friendsAlive by mutableIntStateOf(if (teamMode) 1 + bots.count { it.team == 0 } else 1)
    var enemiesAlive by mutableIntStateOf(if (teamMode) bots.count { it.team != 0 } else bots.size)

    val kills = IntArray(bots.size + 1)
    val deaths = IntArray(bots.size + 1)

    // ---- hit spots for the sparks / blood (filled here, emptied by Game3DView every frame) ----
    // each entry = [x, y, z, kind]   kind 0 = bullet hit a wall (sparks), kind 1 = bullet hit a fighter (blood)
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
            addHit(x, y, z, 1)                       // blood
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
    // A spot is OK only if the whole body fits: the centre AND a ring of 8 points around it
    // (SPAWN_CLEAR meters out) are free at knee, chest and head height. So he can never appear half inside a wall.
    private fun spawnOk(play: PlayArena, x: Float, z: Float): Boolean {
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
        // must be inside the arena: a clear line from the blue spawn or from the middle
        return los(play, play.blueX, 1.2f, play.blueZ, x, 1.2f, z) || los(play, 0f, 1.2f, 0f, x, 1.2f, z)
    }

    // Picks a random free spot that is far from the other living fighters.
    // If no good spot is found, falls back to the blue spawn point (the one you start the match on, always free).
    private fun pickSpawn(rig: GameInput, play: PlayArena, selfId: Int): FloatArray {
        val r = max(max(kotlin.math.abs(play.blueX), kotlin.math.abs(play.blueZ)), 8f) * SPAWN_SCALE
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

    private fun spawnBot(b: Bot, rig: GameInput, play: PlayArena) {
        val p = pickSpawn(rig, play, b.id)
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
        b.faceDeg = atan2(b.z, -b.x) * M_RAD + M_FACING_OFFSET   // look toward the middle
        b.anim = "idle"
    }

    private fun respawnPlayer(rig: GameInput, play: PlayArena) {
        val p = pickSpawn(rig, play, 0)
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
        var a = amount.toFloat()
        if (rig.shieldOn) a *= SHIELD_KEEP
        playerHp = (playerHp - a.roundToInt().coerceAtLeast(1)).coerceAtLeast(0)
        if (playerHp <= 0) {
            playerDead = true
            playerRespawnT = RESPAWN_SECONDS
            respawnSec = ceil(RESPAWN_SECONDS).toInt()
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
            registerKill(b.id, attacker)
        }
    }

    private fun registerKill(victim: Int, attacker: Int) {
        deaths[victim]++
        if (attacker >= 0 && attacker != victim) {
            kills[attacker]++
            if (teamMode) {
                if (teamOf(attacker) == 0) teamA++ else teamB++
            }
        }
        feed = when {
            attacker == 0 -> "You eliminated ${nameOf(victim)}"
            victim == 0 -> "${nameOf(attacker)} eliminated you"
            else -> "${nameOf(attacker)} eliminated ${nameOf(victim)}"
        }
        feedT = 3f
        board++
        refreshCounts()

        if (teamMode) {
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
        val aimAtTarget = haveTarget && b.hasLos && dist > 0.01f
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
