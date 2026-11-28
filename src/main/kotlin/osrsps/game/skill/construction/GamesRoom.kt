package osrsps.game.skill.construction

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.NpcType
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.AmmoKind
import osrsps.game.content.arriveDelay
import osrsps.game.content.message
import osrsps.game.npc.MoveRestrict
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/**
 * Construction step CON6e (the games room): its furniture builds ([FurnitureBuilds]), and the hoop and stick is played,
 * from the Wiki's Games room and Hoop and stick pages (read 2026-11-27).
 *
 * Hoop and stick: "the player will make 10 attempts at landing the hoop on the stick", each landing by the player's
 * Ranged level and worth 2.5 Ranged XP ([hoopThrow]). The page's Jagex chart gives about 29.7% at level 1 and 94.1% at 99:
 * 76 and 241 in 256, the usual skilling roll's ends ([landChance]). GUESSED: a throw every [THROW_TICKS]; the
 * human_throw_hoop1 animation for each throw; the score told at the end (the page's scoreboard is left out); the
 * words. Anyone in the house can play.
 *
 * Step CON6f, the attack stones (the Clay, Limestone and Marble attack stone pages, read 2026-11-27): Set-up puts up
 * the combat stone (the cache's poh_tbt_* NPCs, whose op is Hit, with the cache's hitpoints: 40 for the clay one) in the stone's place ([setUp]). "The
 * player who deals the most damage wins the game" once it breaks ([broken]); it never fights back ([osrsps.game.combat.Combat.passive]);
 * hitting it gives XP for "2.5% of the damage dealt" in the attack style's skill and no Hitpoints XP
 * ([osrsps.game.combat.Combat.damageXpPermille]). INFERRED: the cache's 64 looks of each stone (poh_tbt_clay_000 to _333)
 * are its chips; GUESSED: they come off one at a time as it loses hitpoints ([CHIPS]); a tie goes to whoever got there
 * first; the stone is back on its place 3 ticks after it breaks; the words. Left out: the chips that "may chip off and
 * deal damage to the player" (how often and how much is not on the pages), and the refusal of stat-lowering spells.
 *
 * Step CON6g, the dartboard and archery target (their Wiki pages, read 2026-11-27): ten throws or shots each
 * ([rangingShot]), "Whoever has the highest score after ten throws is the winner". The dartboard takes darts, throwing
 * knives and thrownaxes from the weapon slot, used up, as "Ava's devices will not return them", and refuses the
 * blowpipe ("You can't use that weapon here"); a hit on it is 7.5 Ranged XP. The archery target takes a bow or
 * crossbow with the player's own arrows or bolts, a shot used up each time, for 10 Ranged XP a hit. GUESSED: a hit is
 * rolled as the hoop's landing is ([landChance]) and scores one point (the boards' rings are left out); a throw or
 * shot at the weapon's attack speed; the weapon's animation and launch graphic, but no projectile; the score told at
 * the end; the words. A game stops when the player walks off or does something else (R68B-2; GUESSED as live).
 *
 * Next: the prize chest, and the game space's games and the elemental balances.
 */
class GamesRoom(private val world: World, private val houses: Houses) {
    /** Each stone's looks by chips, poh_tbt_<kind>_000 to _333, by [Stone]. */
    private val looks = HashMap<Stone, List<NpcType>>()

    /** The stones up now, and what each player dealt to each, in the order they first hit it. */
    private val games = HashMap<Npc, LinkedHashMap<String, Int>>()

    fun register() {
        world.handlers.onLoc(HOOP_AND_STICK, HOOP) { op ->
            // Beside it already, this runs in the movement phase (as the lectern's Create-last decides).
            val fromMovement = player.lastMoveTick != tick
            arriveDelay()
            start(this, op.loc ?: return@onLoc, fromMovement) { at, movement -> hoopThrow(at, 1, 0, movement) }
        }
        for ((loc, game) in listOf(DARTBOARD to RangingGame.DARTS, ARCHERY_TARGET to RangingGame.ARCHERY)) {
            world.handlers.onLoc(loc, RANGING_OP) { op ->
                val fromMovement = player.lastMoveTick != tick
                arriveDelay()
                start(this, op.loc ?: return@onLoc, fromMovement) { at, movement -> rangingShot(at, game, 1, 0, movement) }
            }
        }
        for (stone in Stone.entries) {
            val all = (0..63).mapNotNull { world.npcTypes.find("poh_tbt_${stone.key}_${it / 16}${it / 4 % 4}${it % 4}") }
            if (all.size == 64) looks[stone] = all
            world.handlers.onLoc(stone.loc, SET_UP) { op ->
                arriveDelay()
                setUp(this, op.loc ?: return@onLoc, stone)
            }
        }
        val stoneTypes = looks.values.flatten().map { it.id }.toSet()
        val baseTypes = looks.values.map { it.first().id }.toSet()
        val attacksBy = world.npcInteractions.attacksBy
        world.npcInteractions.attacksBy = { type, op -> (op == HIT && type.id in stoneTypes) || attacksBy(type, op) }
        val damageXpPermille = world.combat.damageXpPermille
        world.combat.damageXpPermille = { npc -> if (npc.spawn.type.id in baseTypes) STONE_XP_PERMILLE else damageXpPermille(npc) }
        val passive = world.combat.passive
        world.combat.passive = { npc -> npc.spawn.type.id in baseTypes || passive(npc) }
        world.npcScripts.onDeath(baseTypes) { broken(it) }
    }

    /** The tick each player may throw or shoot next in the games room, kept across games started again. */
    private val nextThrow = java.util.WeakHashMap<Player, Long>()

    /**
     * Starts a game at [loc]: each throw is a weak queue entry ([QUEUE]), so walking off or doing something else stops
     * the game (R68B-2), and a game started again waits out the last throw's ticks ([nextThrow]). [fromMovement] when it
     * starts in the movement phase, which comes after the script phase where a queue's delay first counts down.
     */
    private fun start(context: ScriptContext, loc: ZoneLoc, fromMovement: Boolean, first: ScriptContext.(ZoneLoc, Boolean) -> Unit) {
        val player = context.player
        if (houses.houseAt(loc.x, loc.z) == null) return
        world.scripts.clearQueue(player, QUEUE)
        val wait = ((nextThrow[player] ?: 0L) - world.tick).toInt()
        if (wait <= 0) return context.first(loc, fromMovement)
        context.queue(QUEUE, osrsps.game.script.QueueType.WEAK, if (fromMovement) wait - 1 else wait) { first(loc, false) }
    }

    /** Whether the game at [loc] goes on: it is still there, in the house the player is in. */
    private fun ScriptContext.stillPlaying(loc: ZoneLoc): Boolean {
        val house = houses.houseAt(loc.x, loc.z) ?: return false
        return world.locs.exists(loc) && houses.houseAt(player.x, player.z) === house
    }

    /** Throw-at on the dartboard or Shoot-at on the archery target [loc]: throw or shot [shot] of ten, then the score. */
    private fun ScriptContext.rangingShot(loc: ZoneLoc, game: RangingGame, shot: Int, scored: Int, fromMovement: Boolean) {
        if (!stillPlaying(loc)) return
        var score = scored
        val weapon = world.rangedWeapons.weapon(world.objTypes[player.equipment[WearPos.WEAPON]?.id ?: -1])
        if (game == RangingGame.DARTS && weapon?.loaded == true) return world.message(player, ConstructionLines.DARTBOARD_BLOWPIPE)
        val fits = when (game) {
            RangingGame.DARTS -> weapon != null && weapon.thrown && weapon.kind == AmmoKind.DARTS && !weapon.splash
            RangingGame.ARCHERY -> weapon != null && !weapon.loaded && (weapon.kind == AmmoKind.ARROWS || weapon.kind == AmmoKind.BOLTS)
        }
        if (!fits || weapon == null) return if (shot == 1) world.message(player, game.weaponLine) else world.message(player, ConstructionLines.RANGING_SCORE, "score" to score)
        val ammo = if (game == RangingGame.ARCHERY) world.rangedWeapons.ammo(world.objTypes[player.equipment[WearPos.AMMO]?.id ?: -1]) else null
        if (game == RangingGame.ARCHERY && (ammo == null || !weapon.fires(ammo))) {
            return if (shot == 1) world.message(player, ConstructionLines.ARCHERY_AMMO) else world.message(player, ConstructionLines.RANGING_SCORE, "score" to score)
        }
        val speed = world.combat.attackSpeed(player)
        player.equipment.remove(if (game == RangingGame.DARTS) WearPos.WEAPON else WearPos.AMMO, 1)
        if (weapon.anim >= 0) player.anim(weapon.anim)
        val launch = if (weapon.launch >= 0) weapon.launch else ammo?.launch ?: -1
        if (launch >= 0) player.effects.spotanim(0, launch, LAUNCH_HEIGHT, 0)
        if (world.random.nextInt(256) < landChance(player.skills.currentLevel(Skill.RANGED))) {
            score++
            player.skills.addTenths(Skill.RANGED, game.xpTenths)
        }
        nextThrow[player] = world.tick + speed
        // The score comes after the last shot's interval too, as every shot's wait did before (Codex review on #1066).
        queue(QUEUE, osrsps.game.script.QueueType.WEAK, if (fromMovement) speed - 1 else speed) {
            if (shot < THROWS) return@queue rangingShot(loc, game, shot + 1, score, false)
            world.message(player, ConstructionLines.RANGING_SCORE, "score" to score)
            logger.info { "${player.username} scored $score of $THROWS at ${game.name.lowercase()}" }
        }
    }

    /** The ranging games with weapons: what each refuses with when the weapon is wrong, and the XP of a hit. */
    enum class RangingGame(val weaponLine: osrsps.game.dialogue.Line, val xpTenths: Int) {
        DARTS(ConstructionLines.DARTBOARD_WEAPON, 75),
        ARCHERY(ConstructionLines.ARCHERY_WEAPON, 100),
    }

    /** Set-up on attack stone [loc]: the combat stone stands in its place until it breaks or the room goes. */
    private fun setUp(context: ScriptContext, loc: ZoneLoc, stone: Stone) {
        val house = houses.houseAt(loc.x, loc.z) ?: return
        val slot = Houses.slot(loc.x, loc.z, loc.level, loc.layer)
        if (slot in house.dummies || world.locs[loc.x, loc.z, loc.level, loc.layer] != loc) return
        val type = looks[stone]?.first() ?: return
        val npc = world.spawnNpc(NpcSpawn(type, loc.x, loc.z, loc.level, restrict = MoveRestrict.NOMOVE, source = "house attack stone", respawns = false)) ?: return
        // Everyone in the room may hit it at once, for the most-damage win (Codex review on #1051; a house isn't multi-way).
        npc.multiTarget = true
        world.locs.setPermanent(loc.x, loc.z, loc.level, loc.layer, null)
        house.dummies[slot] = CombatRoom.Attached(npc, loc)
        games.keys.removeAll { world.npcs[it.index] !== it }
        games[npc] = LinkedHashMap()
        logger.info { "${context.player.username} set up an attack stone (${stone.key}) in ${house.owner}'s house" }
    }

    /** [player]'s hit took [dealt] off [npc]: kept toward the win, and a stone chips as it goes down. */
    fun damaged(npc: Npc, player: Player, dealt: Int) {
        val game = games[npc] ?: return
        if (dealt <= 0) return
        game[player.saveKey] = (game[player.saveKey] ?: 0) + dealt
        val all = Stone.entries.firstNotNullOfOrNull { stone -> looks[stone]?.takeIf { it.first().id == npc.spawn.type.id } } ?: return
        val max = npc.maxHitpoints.coerceAtLeast(1)
        val chips = ((max - npc.hitpoints.coerceAtLeast(0)) * (CHIPS.size - 1) / max).coerceIn(0, CHIPS.size - 1)
        val look = all[CHIPS[chips]]
        if (look.id != (npc.transformedTo ?: npc.spawn.type).id) world.npcs.transform(npc, look, Int.MAX_VALUE)
    }

    /** A combat stone broke: whoever dealt it the most wins, everyone who hit it is told, and the stone is back. */
    private fun broken(npc: Npc) {
        val game = games.remove(npc) ?: return
        // The stone's slot is freed and its return set first, whoever won: one that died with no damage kept (::kill)
        // still comes back (R68B-1).
        val house = houses.houseAt(npc.x, npc.z)
        val entry = house?.dummies?.entries?.firstOrNull { it.value.npc === npc }?.toPair()
        if (house != null && entry != null) {
            val (slot, attached) = entry
            house.dummies.remove(slot)
            world.npcScripts.later(BACK_TICKS) {
                val loc = attached.loc
                if (houses.houseAt(loc.x, loc.z) === house && slot !in house.dummies && world.locs[loc.x, loc.z, loc.level, loc.layer] == null && stillBuilt(house, loc)) {
                    world.locs.setPermanent(loc.x, loc.z, loc.level, loc.layer, loc)
                }
            }
        }
        val winner = game.entries.maxByOrNull { it.value }?.key ?: return
        val name = world.onlinePlayers.firstOrNull { it.saveKey == winner }?.username ?: winner
        for (player in world.onlinePlayers) if (player.saveKey in game) world.message(player, ConstructionLines.STONE_WINNER, "name" to name)
        logger.info { "$name won an attack stone game: $game" }
    }

    /**
     * Whether the room [loc] stood in is still laid with that attack stone built in it: one removed in the 3 ticks before
     * the stone comes back leaves no stone to put back (Codex review on #1051).
     */
    private fun stillBuilt(house: Houses.Open, loc: ZoneLoc): Boolean {
        val state = world.playerBySaveKey(house.owner)?.house ?: return false
        val instance = house.instance
        val room = state.room((loc.x - instance.baseX) shr 3, (loc.z - instance.baseZ) shr 3, loc.level) ?: return false
        return loc.id in room.built.values
    }

    /** The attack stones: their gameval kind and the loc each is built as. */
    enum class Stone(val key: String, val loc: Int) { CLAY("clay", 13393), LIMESTONE("limestone", 13392), MARBLE("marble", 13394) }

    /** Hoop on the hoop and stick [loc]: throw [throwNo] of ten, landing or not [THROW_TICKS] later, then the score. */
    private fun ScriptContext.hoopThrow(loc: ZoneLoc, throwNo: Int, landed: Int, fromMovement: Boolean) {
        if (!stillPlaying(loc)) return
        player.anim(THROW_ANIM)
        nextThrow[player] = world.tick + THROW_TICKS
        queue(QUEUE, osrsps.game.script.QueueType.WEAK, if (fromMovement) THROW_TICKS - 1 else THROW_TICKS) {
            var now = landed
            if (world.random.nextInt(256) < landChance(player.skills.currentLevel(Skill.RANGED))) {
                now++
                player.skills.addTenths(Skill.RANGED, LAND_XP_TENTHS)
            }
            if (throwNo < THROWS) return@queue hoopThrow(loc, throwNo + 1, now, false)
            world.message(player, ConstructionLines.HOOP_SCORE, "score" to now)
            logger.info { "${player.username} landed $now of $THROWS hoops" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        /** The dartboard and archery target as built (poh_dartboard1, poh_archery_target1), and their Throw-at and Shoot-at (op 1). */
        const val DARTBOARD = 13400
        const val ARCHERY_TARGET = 13402
        const val RANGING_OP = 1

        /** The launch graphic's height, as a ranged attack's. */
        private const val LAUNCH_HEIGHT = 96

        /** The attack stones' Set-up (op 1), and the combat stone's Hit, which attacks it. */
        const val SET_UP = 1
        const val HIT = "Hit"

        /** XP for a point of damage on an attack stone, in thousandths: 2.5% of the damage dealt (the attack stone pages). */
        const val STONE_XP_PERMILLE = 25

        /** GUESSED: how long after breaking a stone is back on its place. */
        const val BACK_TICKS = 3

        /** GUESSED: the looks a stone goes through as it loses hitpoints, as indexes of _000 to _333 (one chip at a time). */
        val CHIPS: List<Int> = listOf("000", "100", "110", "111", "211", "221", "222", "322", "332", "333").map { it.toInt(4) }

        /** Poh_stick+hoop1, as built, and its Hoop (op 1). */
        const val HOOP_AND_STICK = 13398
        const val HOOP = 1

        const val THROWS = 10

        /** The games' own queue key, apart from Making's. */
        const val QUEUE = "poh_games"

        /** 2.5 Ranged XP a landing (the Hoop and stick page). */
        const val LAND_XP_TENTHS = 25

        /** GUESSED: how long a throw takes. */
        const val THROW_TICKS = 3

        /** human_throw_hoop1 (GUESSED that it is the throw; there is a _hoop2). */
        const val THROW_ANIM = 3602

        /**
         * In 256, the chance a hoop lands at Ranged [level]: 76 at 1 and 241 at 99, in a straight line between (the Hoop
         * and stick page's chart: 29.7% and 94.1%).
         */
        fun landChance(level: Int): Int {
            val at = level.coerceIn(1, 99) - 1
            return 1 + (75 * (98 - at) + 240 * at) / 98
        }
    }
}
