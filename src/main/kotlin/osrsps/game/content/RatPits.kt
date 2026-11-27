package osrsps.game.content

import net.rsprot.protocol.game.outgoing.misc.player.SetPlayerOp
import osrsps.game.FrameSlot
import osrsps.game.Interfaces
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.HitsplatType
import osrsps.game.combat.MeleeCombat
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.game.shop.ShopScreen

/** The Varrock rat pits' words: the Wiki's Rat Pits and the cats' transcripts' Rat Pits sections, read 2026-11-19. */
object RatPitsLines : Lines("rat_pits", "Rat Pits, Transcript:Kitten (Rat Pits), read 2026-11-19") {
    val CHALLENGE = line("challenge", "the player op in the pits; guessed")
    val CHALLENGED = line("challenged", "the message to a player challenged; our own line", "name")
    val NEED_CAT = line("need_cat", "challenging with no cat following; our own line")
    val CATS_ONLY = line("cats_only", "challenging with a kitten or overgrown cat: these pits take grown cats (Rat Pits); our own line")
    val THEY_NEED_CAT = line("they_need_cat", "challenging a player with no grown cat following; our own line")
    val BUSY = line("busy", "challenging a player already in a fight; our own line")
    val SAME_BET = line("same_bet", "accepting before both bets match; our own line")
    val NO_COINS = line("no_coins", "a bet the player can't pay; our own line")
    val DECLINED = line("declined", "the other player declined or left; our own line")
    val UP_FOR_IT = line("up_for_it", "the player to their cat before the fight")
    val ADVICE = line("advice", "the cat's answer")
    val STRATEGY: List<Line> = listOf(
        line("careful", "strategy option 1, which the player then says"),
        line("stay_in", "strategy option 2, which the player then says"),
        line("no_guts", "strategy option 3, which the player then says"),
        line("berserk", "strategy option 4, which the player then says"),
    )
    val COME_HERE = line("come_here", "overhead, the player calling the winning cat")
    val WON = line("won", "the player, their cat won", "coins")
    val LOST = line("lost", "the player, their cat lost", "coins")
    val MEOW = line("meow", "the cat's answer after the fight")
    val DRAW = line("draw", "both cats out at the same kills: the bets back; our own line")
    val STAKE_BACK = line("stake_back", "logging in after the server stopped mid-match: the bet back; our own line", "coins")
}

/**
 * The Varrock rat pits under the manhole east of the city (post-quest step 14; every account has finished Ratcatchers,
 * which opens the pits). From the Wiki's Rat Pits, Rat (Rat Pits), Hooknosed Jack and the cats' transcripts, read
 * 2026-11-19, and the 242 cache's map, NPCs and interfaces.
 *
 * - **In and out**: the manhole (10321) climbs down to the pits' ladder (10309), which climbs back up beside it.
 * - **A match**: two players in the pits with grown cats (not kittens or overgrown cats: these are the cats' pits)
 *   challenge each other ([CHALLENGE_OP]); the betting screen (`ratcatcher_cat_betting`, 279) opens for both, each
 *   setting a bet of 100 to 20,000 coins; once both bets match and both accept, the confirm screen (280) asks again.
 *   The coins are taken, each player tells their cat a strategy, and the cats go into the pit to fight the pits' rats
 *   (4610 to 4612), the scores (`ratcatcher_cat_scores`, 281) showing. The first to [KILLS_TO_WIN] rats wins both bets;
 *   a cat that flees or dies loses, and one that dies is gone for good. A win gives the cat about 15 minutes' growth
 *   and a loss half that (the Wiki's Rewards).
 * - **Strategies**: the Wiki's four, each trading Attack and Strength against Defence and Hitpoints; the two careful
 *   ones flee at low health, the other two fight to the death.
 *
 * Guessed: that a match starts with a player op called Challenge, its slot, and the messages around it; the bet
 * buttons' steps (100, or 1,000 on op 2); [KILLS_TO_WIN]; how much each strategy changes the stats and when the careful
 * ones flee; the fight's rolls (the usual accuracy formula, an attack every [ATTACK_TICKS], max hit 1, 2 berserk); the
 * rats' count and where they and the cats stand; that leaving or logging out forfeits; the landing tiles.
 * Left out: spectating from the rat walls, the Ardougne, Keldagrim and Port Sarim pits (outside Misthalin or other
 * cats), the jingles, ironman refusals, and reading the manual.
 */
class RatPits(private val world: World, private val collision: osrsps.map.CollisionMap) {
    /** Guessed numbers on the Wiki's trade-offs: Attack, the cat's max hit, Defence and Hitpoints, and whether it flees. */
    enum class Strategy(val attack: Int, val maxHit: Int, val defence: Int, val hitpoints: Int, val flees: Boolean) {
        CAREFUL(-2, 1, 4, 4, true),
        STAY_IN(-1, 1, 2, 2, true),
        NO_GUTS(2, 1, 0, 0, false),
        BERSERK(4, 2, -2, -2, false),
    }

    /** One side of a match: the player, their bet, whether they have accepted, and their cat's fight once it starts. */
    private class Side(val player: Player) {
        var bet = MIN_BET
        var accepted = false
        var confirmed = false
        var strategy: Strategy? = null
        var cat: Npc? = null
        var hitpoints = 0
        var kills = 0
        var fled = false
        var dead = false
    }

    private class Match(val a: Side, val b: Side) {
        var stage = Stage.BETTING
        val rats = ArrayList<Npc>()
        val ratHp = HashMap<Npc, Int>()
        fun sides() = listOf(a, b)
        fun of(player: Player) = if (a.player === player) a else b
        fun other(player: Player) = if (a.player === player) b else a
    }

    private enum class Stage { BETTING, CONFIRM, STRATEGY, FIGHT, OVER }

    private val matches = HashMap<String, Match>()

    /** Who each player last challenged, and when. */
    private val challenges = HashMap<String, Pair<String, Long>>()

    fun inArea(x: Int, z: Int, level: Int) = level == 0 && x in AREA_X && z in AREA_Z

    fun inMatch(player: Player): Boolean = matches.containsKey(player.saveKey)

    // ---- In and out -----------------------------------------------------------------------------------------------

    suspend fun ScriptContext.climbDown() {
        arriveDelay()
        player.anim(Climbing.ANIM_REACH_FOR_LADDER)
        delay(1)
        world.teleport(player, LANDING.x, LANDING.z, 0)
        arrived(player)
    }

    suspend fun ScriptContext.climbUp() {
        arriveDelay()
        player.anim(Climbing.ANIM_REACH_FOR_LADDER)
        delay(1)
        forfeit(player)
        world.teleport(player, SURFACE.x, SURFACE.z, 0)
    }

    /** In the pits: the Challenge op shows until they leave. */
    private fun arrived(player: Player) {
        // Kept on the player so a settings toggle that sends every option again ([osrsps.game.PlayerOps.send]) keeps it.
        player.slot5Op = world.textFor(player)[RatPitsLines.CHALLENGE]
        player.session.queue(SetPlayerOp(CHALLENGE_OP, false, player.slot5Op))
        watchArea(player)
    }

    private fun watchArea(player: Player) {
        world.npcScripts.later(1) {
            // A player dropped after a lost connection leaves the world without loggedOut set: they're gone all the same.
            if (player.loggedOut || world.onlinePlayers.none { it === player }) {
                player.slot5Op = null
                return@later
            }
            if (inArea(player.x, player.z, player.level)) return@later watchArea(player)
            forfeit(player)
            player.slot5Op = null
            player.session.queue(SetPlayerOp(CHALLENGE_OP, false, null))
        }
    }

    fun login(player: Player) {
        // A stake still saved is from a match the server stopped (or crashed) in the middle of: it goes back.
        val stake = player.charges[STAKE]
        if (stake > 0 && !inMatch(player)) {
            player.charges[STAKE] = 0
            world.objTypes[ShopScreen.COINS]?.let { world.groundObjs.give(player, it, stake) }
            player.message(world.textFor(player)[RatPitsLines.STAKE_BACK, "coins" to "%,d".format(stake)])
        }
        if (inArea(player.x, player.z, player.level)) arrived(player)
    }

    fun logout(player: Player) = forfeit(player)

    // ---- Challenging and betting --------------------------------------------------------------------------------

    private fun grownCat(player: Player): Boolean = world.cats.followerKind(player)?.stage == Cats.Stage.CAT

    /** [player] challenged [target]: a challenge back starts the betting. */
    fun challenge(player: Player, target: Player) {
        val words = world.textFor(player)
        if (!inArea(player.x, player.z, player.level) || !inArea(target.x, target.z, target.level)) return
        if (inMatch(player)) return
        val kind = world.cats.followerKind(player)
        if (kind == null) return player.message(words[RatPitsLines.NEED_CAT])
        if (kind.stage != Cats.Stage.CAT) return player.message(words[RatPitsLines.CATS_ONLY])
        if (!grownCat(target)) return player.message(words[RatPitsLines.THEY_NEED_CAT])
        if (inMatch(target)) return player.message(words[RatPitsLines.BUSY])
        val theirs = challenges[target.saveKey]
        if (theirs != null && theirs.first == player.saveKey && world.tick - theirs.second <= CHALLENGE_TICKS) {
            challenges.remove(target.saveKey)
            challenges.remove(player.saveKey)
            startBetting(target, player)
            return
        }
        challenges.values.removeIf { world.tick - it.second > CHALLENGE_TICKS }
        challenges[player.saveKey] = target.saveKey to world.tick
        target.message(world.textFor(target)[RatPitsLines.CHALLENGED, "name" to player.username])
    }

    private fun startBetting(a: Player, b: Player) {
        val match = Match(Side(a), Side(b))
        matches[a.saveKey] = match
        matches[b.saveKey] = match
        for (side in match.sides()) {
            world.interfaces.openModal(side.player, FrameSlot.MAINMODAL, BETTING)
            for (component in BETTING_BUTTONS) world.interfaces.setEvents(side.player, BETTING, component, -1, -1, Interfaces.IF_EVENT_OP1 or Interfaces.IF_EVENT_OP2)
        }
        showBets(match)
    }

    private fun showBets(match: Match) {
        for (side in match.sides()) {
            val other = match.other(side.player)
            val p = side.player
            world.interfaces.setText(p, BETTING, P1_NAME, p.username)
            world.interfaces.setText(p, BETTING, P2_NAME, other.player.username)
            world.interfaces.setText(p, BETTING, P1_BET, "%,d".format(side.bet))
            world.interfaces.setText(p, BETTING, P2_BET, "%,d".format(other.bet))
        }
    }

    /** A bet button: [up] or down by 100 (op 1) or 1,000 (op 2). */
    fun changeBet(player: Player, up: Boolean, option: Int) {
        val match = matches[player.saveKey]?.takeIf { it.stage == Stage.BETTING } ?: return
        val side = match.of(player)
        val step = if (option == 1) 100 else 1_000
        side.bet = (side.bet + if (up) step else -step).coerceIn(MIN_BET, MAX_BET)
        match.a.accepted = false
        match.b.accepted = false
        showBets(match)
    }

    fun accept(player: Player) {
        val match = matches[player.saveKey]?.takeIf { it.stage == Stage.BETTING } ?: return
        if (match.a.bet != match.b.bet) return player.message(world.textFor(player)[RatPitsLines.SAME_BET])
        if (player.inventory.count(ShopScreen.COINS) < match.of(player).bet) return player.message(world.textFor(player)[RatPitsLines.NO_COINS])
        match.of(player).accepted = true
        if (!match.a.accepted || !match.b.accepted) return
        match.stage = Stage.CONFIRM
        for (side in match.sides()) {
            world.interfaces.openModal(side.player, FrameSlot.MAINMODAL, CONFIRM)
            world.interfaces.setText(side.player, CONFIRM, CONFIRM_AMOUNT, "%,d".format(side.bet))
            for (component in CONFIRM_BUTTONS) world.interfaces.setEvents(side.player, CONFIRM, component, -1, -1, Interfaces.IF_EVENT_OP1)
        }
    }

    fun confirm(player: Player) {
        val match = matches[player.saveKey]?.takeIf { it.stage == Stage.CONFIRM } ?: return
        match.of(player).confirmed = true
        if (!match.a.confirmed || !match.b.confirmed) return
        // The coins go in now; a player who no longer has them ends it for both.
        if (match.sides().any { it.player.inventory.count(ShopScreen.COINS) < it.bet }) {
            for (side in match.sides()) side.player.message(world.textFor(side.player)[RatPitsLines.NO_COINS])
            return end(match, null, refund = false)
        }
        for (side in match.sides()) {
            AlKharidTraders.pay(side.player, side.bet)
            // Kept with the save until the match settles, so a stop or crash mid-match hands it back at login.
            side.player.charges[STAKE] = side.bet
        }
        match.stage = Stage.STRATEGY
        for (side in match.sides()) {
            world.interfaces.closeModals(side.player)
            askStrategy(match, side)
        }
        // Guessed: a player who walks off their dialogue (but stays in the pits) doesn't hold the match up for ever.
        world.npcScripts.later(STRATEGY_TICKS) {
            if (match.stage != Stage.STRATEGY) return@later
            for (side in match.sides()) {
                if (side.strategy != null) continue
                side.strategy = Strategy.STAY_IN
                world.scripts.interrupt(side.player)
            }
            startFight(match)
        }
    }

    /**
     * [screen] (the betting or confirm screen) was declined or closed: no match. A screen closing because the next one
     * opened over it, or because the match moved on, does nothing.
     */
    fun decline(player: Player, screen: Int) {
        val match = matches[player.saveKey] ?: return
        if (match.stage != (if (screen == BETTING) Stage.BETTING else Stage.CONFIRM)) return
        match.other(player).player.message(world.textFor(match.other(player).player)[RatPitsLines.DECLINED])
        end(match, null, refund = false)
    }

    private fun askStrategy(match: Match, side: Side) {
        val cat = world.cats.follower(side.player) ?: return end(match, null, refund = true)
        world.scripts.start(side.player) {
            world.dialogues.start(this, cat) {
                chatPlayer(ChatAnim.QUIZ, RatPitsLines.UP_FOR_IT)
                chatNpc(ChatAnim.HAPPY, RatPitsLines.ADVICE)
                val picked = choice(*RatPitsLines.STRATEGY.toTypedArray())
                // Once the timeout has picked for them (or the match is over) a late answer changes nothing.
                if (match.stage != Stage.STRATEGY) return@start
                chatPlayer(ChatAnim.NEUTRAL, RatPitsLines.STRATEGY[picked - 1])
                if (match.stage == Stage.STRATEGY) side.strategy = Strategy.entries[picked - 1]
            }
            if (match.stage != Stage.STRATEGY) return@start
            if (side.strategy == null) side.strategy = Strategy.STAY_IN
            if (match.sides().all { it.strategy != null }) startFight(match)
        }
    }

    // ---- The fight ------------------------------------------------------------------------------------------------

    private fun startFight(match: Match) {
        // The cat may have been picked up and another dropped since the challenge: only grown cats fight.
        if (match.sides().any { !grownCat(it.player) }) {
            for (side in match.sides()) side.player.message(world.textFor(side.player)[RatPitsLines.THEY_NEED_CAT])
            return end(match, null, refund = true)
        }
        match.stage = Stage.FIGHT
        for ((i, side) in match.sides().withIndex()) {
            val cat = world.cats.hold(side.player) ?: return end(match, null, refund = true)
            side.cat = cat
            val (x, z) = CAT_STARTS[i]
            world.npcs.teleport(cat, x, z)
            side.hitpoints = (CAT_HITPOINTS + side.strategy!!.hitpoints).coerceAtLeast(1)
            world.interfaces.openInside(side.player, side.player.frame.interfaceId, side.player.frame.component(FrameSlot.OVERLAY_HUD), SCORES)
        }
        for (i in 0 until RATS) spawnRat(match, i)
        showScores(match)
        world.npcScripts.later(ATTACK_TICKS) { round(match) }
    }

    private fun spawnRat(match: Match, i: Int) {
        val type = world.npcTypes[RAT_TYPES[i % RAT_TYPES.size]] ?: return
        val (x, z) = RAT_TILES[i % RAT_TILES.size]
        val rat = world.spawnNpc(NpcSpawn(type, x, z, 0, wander = 3, source = "rat pits", respawns = false)) ?: return
        match.rats += rat
        match.ratHp[rat] = type.stats.hitpoints
    }

    private fun showScores(match: Match) {
        for (side in match.sides()) {
            val other = match.other(side.player)
            world.interfaces.setText(side.player, SCORES, NAME_P1, side.player.username)
            world.interfaces.setText(side.player, SCORES, NAME_P2, other.player.username)
            world.interfaces.setText(side.player, SCORES, SCORE_P1, side.kills.toString())
            world.interfaces.setText(side.player, SCORES, SCORE_P2, other.kills.toString())
        }
    }

    /** Every [ATTACK_TICKS]: each cat swings at its nearest rat, and the rats beside a cat swing back. */
    private fun round(match: Match) {
        if (match.stage != Stage.FIGHT) return
        for (side in match.sides()) {
            if (side.fled || side.dead) continue
            val cat = side.cat ?: continue
            val strategy = side.strategy!!
            val rat = match.rats.filter { world.npcs[it.index] === it }.minByOrNull { maxOf(kotlin.math.abs(it.x - cat.x), kotlin.math.abs(it.z - cat.z)) } ?: continue
            cat.destination = osrsps.map.Tile(rat.x, rat.z)
            // No swings until the cat has got beside its rat, past the pit's walls.
            if (cat.level != rat.level || !osrsps.map.Reach.reachedEntity(collision, cat.level, cat.x, cat.z, cat.type.size, rat.x, rat.z, rat.type.size, rat.type.size)) continue
            val catType = cat.type.stats
            val ratMax = rat.type.stats.hitpoints
            var ratKilled = false
            if (hits(catType.attack + strategy.attack, rat.type.stats.defence)) {
                val damage = world.random.between(1, strategy.maxHit)
                val left = (match.ratHp[rat] ?: 1) - damage
                rat.effects.hit(-1, HitsplatType.DAMAGE_OTHER, HitsplatType.DAMAGE_OTHER, damage)
                rat.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(left, ratMax))
                if (left <= 0) {
                    ratKilled = true
                    side.kills++
                    world.removeNpc(rat)
                    match.rats.remove(rat)
                    match.ratHp.remove(rat)
                    spawnRat(match, side.kills + match.other(side.player).kills)
                } else {
                    match.ratHp[rat] = left
                }
            } else {
                rat.effects.hit(-1, HitsplatType.BLOCK_OTHER, HitsplatType.BLOCK_OTHER, 0)
            }
            // The rat bites back, if it lived.
            if (ratKilled) continue
            val catMax = (CAT_HITPOINTS + strategy.hitpoints).coerceAtLeast(1)
            if (hits(rat.type.stats.attack, catType.defence + strategy.defence)) {
                side.hitpoints -= 1
                cat.effects.hit(-1, HitsplatType.DAMAGE_OTHER, HitsplatType.DAMAGE_OTHER, 1)
                if (side.hitpoints <= 0) side.dead = true
                else if (strategy.flees && side.hitpoints <= FLEE_AT) side.fled = true
            } else {
                cat.effects.hit(-1, HitsplatType.BLOCK_OTHER, HitsplatType.BLOCK_OTHER, 0)
            }
            cat.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(side.hitpoints, catMax))
        }
        showScores(match)
        val (a, b) = match.a to match.b
        val aWon = a.kills >= KILLS_TO_WIN
        val bWon = b.kills >= KILLS_TO_WIN
        val aOut = a.fled || a.dead
        val bOut = b.fled || b.dead
        when {
            aWon && bWon || aOut && bOut -> end(match, null, refund = true)
            aWon || bOut -> end(match, a, refund = false)
            bWon || aOut -> end(match, b, refund = false)
            else -> world.npcScripts.later(ATTACK_TICKS) { round(match) }
        }
    }

    /** The usual accuracy roll on (level + 8) each side. */
    private fun hits(attack: Int, defence: Int): Boolean {
        val a = (attack.coerceAtLeast(0) + 8) * 64
        val d = (defence.coerceAtLeast(0) + 8) * 64
        val chance = if (a > d) 1.0 - (d + 2.0) / (2.0 * (a + 1)) else a / (2.0 * (d + 1))
        return world.random.nextInt(10_000) < (chance * 10_000).toInt()
    }

    /** [player] left or logged out: their match, if any, goes to the other side (or ends, before the coins went in). */
    private fun forfeit(player: Player) {
        challenges.remove(player.saveKey)
        // Challenges nobody answered in time go too, so the map holds no more than live ones.
        challenges.values.removeIf { world.tick - it.second > CHALLENGE_TICKS }
        val match = matches[player.saveKey] ?: return
        when (match.stage) {
            Stage.BETTING -> decline(player, BETTING)
            Stage.CONFIRM -> decline(player, CONFIRM)
            Stage.STRATEGY, Stage.FIGHT -> end(match, match.other(player), refund = false)
            Stage.OVER -> Unit
        }
    }

    /** The match is over: [winner] takes both bets, or with none and [refund] each gets their own back. */
    private fun end(match: Match, winner: Side?, refund: Boolean) {
        if (match.stage == Stage.OVER) return
        val paid = match.stage == Stage.STRATEGY || match.stage == Stage.FIGHT
        match.stage = Stage.OVER
        for (rat in match.rats) if (world.npcs[rat.index] === rat) world.removeNpc(rat)
        match.rats.clear()
        for (side in match.sides()) {
            matches.remove(side.player.saveKey)
            val p = side.player
            world.interfaces.closeModals(p)
            world.interfaces.closeInside(p, p.frame.interfaceId, p.frame.component(FrameSlot.OVERLAY_HUD))
        }
        if (!paid) return
        var settled = false
        val settleAll = {
            if (!settled) {
                settled = true
                for (side in match.sides()) settle(side, winner, refund)
            }
        }
        // Both saves are written together, as a trade's are (World.commitTrade): a crash can't keep one player's
        // winnings and the other's stake, which would hand the stake back at login. If the saves can't be journalled
        // the match still settles in memory, for the next saves to record.
        if (!world.commitTrade(match.a.player, match.b.player, settleAll)) settleAll()
        for (side in match.sides()) {
            val p = side.player
            if (winner == null || p.loggedOut) continue
            val won = winner === side
            val cat = side.cat
            world.scripts.start(p) {
                if (won) p.effects.say(world.textFor(p)[RatPitsLines.COME_HERE])
                world.dialogues.start(this, cat?.takeIf { !side.dead }) {
                    chatPlayer(if (won) ChatAnim.HAPPY else ChatAnim.ANGRY, if (won) RatPitsLines.WON else RatPitsLines.LOST, "coins" to "%,d".format(side.bet))
                    if (!side.dead && cat != null) chatNpc(ChatAnim.HAPPY, RatPitsLines.MEOW)
                }
            }
        }
    }

    /** [side]'s share of the end: the stake is settled, the coins paid, and the cat released, grown or gone. */
    private fun settle(side: Side, winner: Side?, refund: Boolean) {
        val p = side.player
        p.charges[STAKE] = 0
        // What doesn't fit goes on the floor under them, as other content's rewards do (GroundObjs.give).
        val coins = world.objTypes[ShopScreen.COINS]
        when {
            winner == null && refund -> {
                coins?.let { world.groundObjs.give(p, it, side.bet) }
                p.message(world.textFor(p)[RatPitsLines.DRAW])
            }
            winner === side -> coins?.let { world.groundObjs.give(p, it, side.bet * 2) }
        }
        if (side.dead) {
            world.cats.passedAway(p)
        } else if (side.cat != null) {
            world.cats.release(p)
            world.cats.addGrowth(p, if (winner === side) WIN_GROWTH else LOSS_GROWTH)
        }
    }

    companion object {
        const val MANHOLE = 10321
        const val LADDER = 10309
        const val CLIMB_DOWN = 1
        const val CLIMB_UP = 1
        const val MANUAL = 6767

        /** The bet held while a match runs, by name in the player's saved [osrsps.game.Charges]. */
        const val STAKE = "rat_pits_stake"

        /** Guessed: the player op slot for Challenge (Attack's is 2; 5 is free). */
        const val CHALLENGE_OP = osrsps.game.PlayerOps.SLOT_5

        const val BETTING = 279
        const val P1_NAME = 7
        const val P2_NAME = 8
        const val ACCEPT = 5
        const val REJECT = 6
        const val ACCEPT_TEXT = 10
        const val DECLINE_TEXT = 11
        const val P1_BET = 14
        const val P2_BET = 15
        const val BET_DOWN = 18
        const val BET_UP = 19
        val BETTING_BUTTONS = listOf(ACCEPT, REJECT, ACCEPT_TEXT, DECLINE_TEXT, BET_DOWN, BET_UP)

        const val CONFIRM = 280
        const val CONFIRM_YES = 1
        const val CONFIRM_NO = 2
        const val CONFIRM_YES_TEXT = 3
        const val CONFIRM_NO_TEXT = 4
        const val CONFIRM_AMOUNT = 5
        val CONFIRM_BUTTONS = listOf(CONFIRM_YES, CONFIRM_NO, CONFIRM_YES_TEXT, CONFIRM_NO_TEXT)

        const val SCORES = 281
        const val NAME_P1 = 3
        const val NAME_P2 = 4
        const val SCORE_P1 = 7
        const val SCORE_P2 = 8

        /** The Wiki's bet limits. */
        const val MIN_BET = 100
        const val MAX_BET = 20_000

        /** The Wiki's Rewards: about 15 minutes' growth (10 cycles) for a win, half for a loss. */
        const val WIN_GROWTH = 10
        const val LOSS_GROWTH = 5

        /** Guessed: the fight's numbers. */
        const val KILLS_TO_WIN = 5
        const val ATTACK_TICKS = 4
        const val RATS = 3
        const val FLEE_AT = 2
        const val CHALLENGE_TICKS = 100
        const val STRATEGY_TICKS = 50
        /** A grown cat's Hitpoints (the Wiki's Cat, from Mod Ash). */
        const val CAT_HITPOINTS = 6

        val RAT_TYPES = listOf(4610, 4611, 4612)

        val AREA_X = 2890..2930
        val AREA_Z = 5078..5108

        /** Guessed tiles: beside the ladder, beside the manhole, the cats' corners and the rats' spots in the pit. */
        val LANDING = Destination(2896, 5097, 0)
        val SURFACE = Destination(3267, 3401, 0)
        val CAT_STARTS = listOf(2905 to 5084, 2911 to 5088)
        val RAT_TILES = listOf(2908 to 5086, 2906 to 5089, 2910 to 5083)
    }
}

fun registerRatPits(world: World, pits: RatPits) = with(pits) {
    world.handlers.onLoc(RatPits.MANHOLE, RatPits.CLIMB_DOWN) { climbDown() }
    world.handlers.onLoc(RatPits.LADDER, RatPits.CLIMB_UP) { climbUp() }
    world.handlers.onButton(RatPits.BETTING, RatPits.BET_DOWN) { op -> changeBet(player, up = false, op.option) }
    world.handlers.onButton(RatPits.BETTING, RatPits.BET_UP) { op -> changeBet(player, up = true, op.option) }
    for (component in listOf(RatPits.ACCEPT, RatPits.ACCEPT_TEXT)) world.handlers.onButton(RatPits.BETTING, component) { accept(player) }
    for (component in listOf(RatPits.REJECT, RatPits.DECLINE_TEXT)) world.handlers.onButton(RatPits.BETTING, component) { decline(player, RatPits.BETTING) }
    for (component in listOf(RatPits.CONFIRM_YES, RatPits.CONFIRM_YES_TEXT)) world.handlers.onButton(RatPits.CONFIRM, component) { confirm(player) }
    for (component in listOf(RatPits.CONFIRM_NO, RatPits.CONFIRM_NO_TEXT)) world.handlers.onButton(RatPits.CONFIRM, component) { decline(player, RatPits.CONFIRM) }
    world.interfaces.onClose(RatPits.BETTING) { decline(it, RatPits.BETTING) }
    world.interfaces.onClose(RatPits.CONFIRM) { decline(it, RatPits.CONFIRM) }
}
