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
 * Ranged level and worth 2.5 Ranged XP ([hoop]). The page's Jagex chart gives about 29.7% at level 1 and 94.1% at 99:
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
 * ([rangingGame]), "Whoever has the highest score after ten throws is the winner". The dartboard takes darts, throwing
 * knives and thrownaxes from the weapon slot, used up, as "Ava's devices will not return them", and refuses the
 * blowpipe ("You can't use that weapon here"); a hit on it is 7.5 Ranged XP. The archery target takes a bow or
 * crossbow with the player's own arrows or bolts, a shot used up each time, for 10 Ranged XP a hit. GUESSED: a hit is
 * rolled as the hoop's landing is ([landChance]) and scores one point (the boards' rings are left out); a throw or
 * shot at the weapon's attack speed; the weapon's animation and launch graphic, but no projectile; the score told at
 * the end; the words.
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
            arriveDelay()
            hoop(this, op.loc ?: return@onLoc)
        }
        for ((loc, game) in listOf(DARTBOARD to RangingGame.DARTS, ARCHERY_TARGET to RangingGame.ARCHERY)) {
            world.handlers.onLoc(loc, RANGING_OP) { op ->
                arriveDelay()
                rangingGame(this, op.loc ?: return@onLoc, game)
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

    /** Throw-at on the dartboard or Shoot-at on the archery target [loc]: ten throws or shots, then the score. */
    private suspend fun rangingGame(context: ScriptContext, loc: ZoneLoc, game: RangingGame) {
        val player = context.player
        if (houses.houseAt(loc.x, loc.z) == null) return
        var score = 0
        for (shot in 1..THROWS) {
            if (!world.locs.exists(loc) || houses.houseAt(loc.x, loc.z) == null) return
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
            context.delay(speed)
        }
        world.message(player, ConstructionLines.RANGING_SCORE, "score" to score)
        logger.info { "${player.username} scored $score of $THROWS at ${game.name.lowercase()}" }
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
        val winner = game.entries.maxByOrNull { it.value }?.key
        val name = world.onlinePlayers.firstOrNull { it.saveKey == winner }?.username ?: winner ?: return
        for (player in world.onlinePlayers) if (player.saveKey in game) world.message(player, ConstructionLines.STONE_WINNER, "name" to name)
        logger.info { "$name won an attack stone game: $game" }
        val house = houses.houseAt(npc.x, npc.z) ?: return
        val (slot, attached) = house.dummies.entries.firstOrNull { it.value.npc === npc }?.toPair() ?: return
        house.dummies.remove(slot)
        world.npcScripts.later(BACK_TICKS) {
            val loc = attached.loc
            if (houses.houseAt(loc.x, loc.z) === house && slot !in house.dummies && world.locs[loc.x, loc.z, loc.level, loc.layer] == null && stillBuilt(house, loc)) {
                world.locs.setPermanent(loc.x, loc.z, loc.level, loc.layer, loc)
            }
        }
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

    /** Hoop on the hoop and stick [loc]: ten throws, then the score. */
    private suspend fun hoop(context: ScriptContext, loc: ZoneLoc) {
        val player = context.player
        if (houses.houseAt(loc.x, loc.z) == null) return
        var landed = 0
        repeat(THROWS) {
            if (!world.locs.exists(loc) || houses.houseAt(loc.x, loc.z) == null) return
            player.anim(THROW_ANIM)
            context.delay(THROW_TICKS)
            if (world.random.nextInt(256) < landChance(player.skills.currentLevel(Skill.RANGED))) {
                landed++
                player.skills.addTenths(Skill.RANGED, LAND_XP_TENTHS)
            }
        }
        world.message(player, ConstructionLines.HOOP_SCORE, "score" to landed)
        logger.info { "${player.username} landed $landed of $THROWS hoops" }
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
