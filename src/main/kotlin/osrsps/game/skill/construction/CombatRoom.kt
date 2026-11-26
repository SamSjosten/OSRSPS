package osrsps.game.skill.construction

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.PlayerEffects
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.SureHit
import osrsps.game.dialogue.Line
import osrsps.game.instance.Instance
import osrsps.game.npc.Direction
import osrsps.game.npc.MoveRestrict
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.WearPos
import osrsps.game.content.arriveDelay
import osrsps.game.content.message
import osrsps.game.script.ScriptContext
import osrsps.game.skill.Agility
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile
import kotlin.math.abs
import kotlin.math.max

/**
 * Construction step CON6a (the combat room): what its built furniture does before anyone fights, from the Wiki's Combat
 * room, Glove rack, Weapons rack, Extra weapons rack and Boxing gloves pages (read 2026-11-26). Building it is
 * [Furniture]'s.
 *
 * - Racks: Search gives the rack's practice kit, free and without end, as the kitchen's larders do ([takeSupplies]).
 *   The glove rack has the red and the blue boxing gloves; the weapons rack adds the wooden shortsword and wooden
 *   shield; the extra weapons rack adds the pugel. Each stays in the house ([HOUSE_ONLY]): the Boxing gloves page says
 *   they "are destroyed if a player tries to leave a house with them"; that the rest are the same is INFERRED from
 *   their house-only names (poh_wooden_sword, poh_wooden_shield, poh_pugel).
 * - The rings' ropes (Climb-over) and the ranging pedestals' magic barriers (Walk-through) take the player across, from
 *   whichever side they are on. Climbing plays `human_get_over_combatring_nogloves`, or the `_redgloves` or `_bluegloves`
 *   one with those gloves worn (the cache's names). GUESSED: the crossing takes [CROSS_TICKS]; the barrier has no
 *   animation.
 *
 * Step CON6b, the combat dummies (the Combat dummy, Undead combat dummy and Ornate undead combat dummy pages, read
 * 2026-11-26): Attach puts the sandbag on, and the attached dummy ([DUMMIES]) is attacked like any monster, but "The
 * player will always hit their max hit when attacking the dummy, but no experience will be granted for hitting it. Any
 * ammunition fired at it will always be lost" ([osrsps.game.combat.Combat.dummy]). It never fights back or goes down
 * (INFERRED: it is whole again after each hit). Detach takes it off; Upgrade and Remove wait for that. The undead ones
 * count as any Slayer task for a black mask or Slayer helmet ([ANY_TASK]). Anyone in the house can attach, attack and
 * detach one (INFERRED). Left out: what being undead or demonic does (no salve amulet or demonbane in the game yet).
 *
 * Step CON6c, the fights (the Combat room, Boxing ring, Fencing ring, Combat ring and Ranging pedestals pages, read
 * 2026-11-26): players inside the same ring's ropes, or on the ranging pedestals' spots, may fight each other "without
 * losing their items" ([arena], [arenaRefusal]; Attack shows there as in the Wilderness). The boxing ring takes boxing
 * gloves or bare fists, with no armour, jewellery or combat spells; the fencing ring any weapon, but no armour,
 * jewellery or combat spells; the combat ring "anything goes"; on the pedestals "the player must use Ranged or
 * Magic". A death in a ring stays in the room ([arenaDeathTile]). GUESSED: no skull; XP as for any fight; a shield is
 * allowed in the fencing ring (the weapons rack gives one); the refusals' words. Left out: the combat ring's ban on
 * teleport spells.
 *
 * Next (CON6d): the balance beam. Left out: the ornate dummy's Swap (its other kinds).
 */
class CombatRoom(private val world: World, private val houses: Houses) {
    fun register() {
        for ((loc, kit) in RACKS) {
            world.handlers.onLoc(loc, Kitchen.SEARCH) { op ->
                arriveDelay()
                takeSupplies(world, houses, this, op.loc ?: return@onLoc, kit)
            }
        }
        for ((loc, npc) in DUMMIES) {
            world.handlers.onLoc(loc, ATTACH) { op ->
                arriveDelay()
                attach(this, op.loc ?: return@onLoc, npc)
            }
            world.handlers.onNpc(npc, DETACH) { op -> detach(op.npc ?: return@onNpc) }
            // The sandbag comes off before the dummy is upgraded or removed (the Undead combat dummy page: "the sandbag
            // must be detached before upgrading"; INFERRED for Remove).
            for (option in listOf(NPC_UPGRADE, NPC_REMOVE)) world.handlers.onNpc(npc, option) { world.message(player, ConstructionLines.DUMMY_DETACH_FIRST) }
        }
        world.pvpCombat.inArena = { player -> arena(player) != null }
        world.pvpCombat.arenaRefusal = ::arenaRefusal
        val dummyNpcs = DUMMIES.values.toSet()
        world.combat.dummy = { npc -> npc.type.id in dummyNpcs }
        val sureHit = world.combat.sureHit
        world.combat.sureHit = { player, npc, type -> if (npc.type.id in dummyNpcs) SureHit.MAX else sureHit(player, npc, type) }
        for (loc in ROPES + BARRIERS) {
            world.handlers.onLoc(loc, CROSS) { op ->
                arriveDelay()
                cross(this, op.loc ?: return@onLoc)
            }
            // Used from either side: a corner post's reach is only its own tile, so its diagonal side needs naming
            // (Codex review on #991).
            world.useLocFrom(loc) { _, at -> sides(at)?.toList()?.map { (x, z) -> Tile(x, z) }.orEmpty() }
        }
    }

    /** Across [loc], a rope or a barrier, to the tile on its other side: from the side [context]'s player is nearer. */
    private suspend fun cross(context: ScriptContext, loc: ZoneLoc) {
        val player = context.player
        if (houses.houseAt(loc.x, loc.z) == null) return
        val (a, b) = sides(loc) ?: return
        val from = if (distance(player, a) <= distance(player, b)) a else b
        val to = if (from == a) b else a
        if (player.x != from.first || player.z != from.second) {
            if (distance(player, from) > 1) return
            world.teleport(player, from.first, from.second, loc.level)
            context.delay(1)
        }
        if (!world.locs.exists(loc) || houses.houseAt(loc.x, loc.z) == null) return
        player.waypoints.clear()
        if (loc.id in ROPES) player.anim(climbAnim(player))
        val angle = Agility.angle(to.first - from.first, to.second - from.second)
        player.effects.exactMove(PlayerEffects.ExactMove(from.first, from.second, to.first, to.second, 0, CROSS_TICKS * 30, angle))
        context.delay(CROSS_TICKS)
        world.teleport(player, to.first, to.second, loc.level)
    }

    /**
     * Attach on a combat dummy [loc]: the sandbag goes on, the dummy [npc] standing in its place to be attacked until
     * someone detaches it or the room or house goes. GUESSED: the loc gives way to the NPC (the NPC's model has the
     * dummy's frame and, for the undead ones, the same mask as the loc), and it faces the way the loc does.
     */
    private fun attach(context: ScriptContext, loc: ZoneLoc, npc: Int) {
        val house = houses.houseAt(loc.x, loc.z) ?: return
        val slot = Houses.slot(loc.x, loc.z, loc.level, loc.layer)
        if (slot in house.dummies || world.locs[loc.x, loc.z, loc.level, loc.layer] != loc) return
        val type = world.npcTypes[npc] ?: return
        val spawned = world.spawnNpc(NpcSpawn(type, loc.x, loc.z, loc.level, facing(loc.angle), restrict = MoveRestrict.NOMOVE, source = "house combat dummy", respawns = false)) ?: return
        world.locs.setPermanent(loc.x, loc.z, loc.level, loc.layer, null)
        house.dummies[slot] = Attached(spawned, loc)
        logger.info { "${context.player.username} attached a combat dummy (${type.name}) in ${house.owner}'s house" }
    }

    /** Detach on an attached combat dummy [npc]: it goes, and the dummy's loc is back. */
    private fun detach(npc: Npc) {
        val house = houses.houseAt(npc.x, npc.z) ?: return
        val (slot, attached) = house.dummies.entries.firstOrNull { it.value.npc === npc }?.toPair() ?: return
        house.dummies.remove(slot)
        world.removeNpc(npc)
        val loc = attached.loc
        world.locs.setPermanent(loc.x, loc.z, loc.level, loc.layer, loc)
    }

    /** A house's fighting ring a player stands in: its [kind], in the room at [cell] of the house in [instance]. */
    data class Arena(val kind: ArenaKind, val instance: Instance, val cell: Houses.Cell)

    /**
     * The fighting ring [player] stands in, or null: inside the ropes of a boxing, fencing or combat ring (the 4 by 4
     * middle of the room, which turning the room leaves where it is), or on a ranging pedestal's spot. Known by the mat
     * built in the ring's middle, or the ranging spot underfoot.
     */
    fun arena(player: Player): Arena? {
        val house = houses.houseAt(player.x, player.z) ?: return null
        val instance = house.instance
        val dx = player.x - instance.baseX
        val dz = player.z - instance.baseZ
        if ((dx and 7) !in RING_INSIDE || (dz and 7) !in RING_INSIDE) return null
        val cell = Houses.Cell(dx shr 3, dz shr 3, player.level)
        val middleX = instance.baseX + (cell.x shl 3) + 3
        val middleZ = instance.baseZ + (cell.z shl 3) + 3
        val kind = when {
            world.locs.at(player.x, player.z, player.level).any { it.id == RANGING_SPOT } -> ArenaKind.PEDESTALS
            else -> world.locs.at(middleX, middleZ, player.level).firstNotNullOfOrNull { MATS[it.id] } ?: return null
        }
        return Arena(kind, instance, cell)
    }

    /**
     * Why [attacker] can't attack [target] from the ring they stand in ([arena]), empty when its rules allow it: both in
     * the same ring; in the boxing ring, boxing gloves or bare fists and nothing else worn; in the fencing ring, a
     * weapon (and shield) and nothing else; in the combat ring, anything; on the ranging pedestals, from one spot to the
     * other with Ranged or Magic. Combat spells only in the combat ring and on the pedestals. [spell] is whether the
     * attack is a cast from the spellbook.
     */
    private fun arenaRefusal(attacker: Player, target: Player, spell: Boolean): List<Line> {
        val own = arena(attacker) ?: return listOf(ConstructionLines.ARENA_NOT_SAME)
        val theirs = arena(target)
        if (theirs == null || theirs.instance !== own.instance || theirs.cell != own.cell || theirs.kind != own.kind) return listOf(ConstructionLines.ARENA_NOT_SAME)
        val type = world.combat.style(attacker).type
        val magic = spell || type == AttackType.MAGIC
        val worn = attacker.equipment.contents().map { it.first }
        return when (own.kind) {
            ArenaKind.BOXING -> when {
                worn.any { it != WearPos.WEAPON } || attacker.equipment[WearPos.WEAPON]?.id.let { it != null && it != RED_GLOVES && it != BLUE_GLOVES } -> listOf(ConstructionLines.ARENA_KIT)
                magic || type == AttackType.RANGED -> listOf(ConstructionLines.ARENA_STYLE)
                else -> emptyList()
            }
            ArenaKind.FENCING -> when {
                worn.any { it != WearPos.WEAPON && it != WearPos.SHIELD } -> listOf(ConstructionLines.ARENA_KIT)
                magic || type == AttackType.RANGED -> listOf(ConstructionLines.ARENA_STYLE)
                else -> emptyList()
            }
            ArenaKind.COMBAT -> emptyList()
            ArenaKind.PEDESTALS -> if (magic || type == AttackType.RANGED) emptyList() else listOf(ConstructionLines.ARENA_STYLE)
        }
    }

    /**
     * Where a player who dies in a fighting ring comes back: "they are simply teleported out of it to another place in
     * the room" (the Combat room page). GUESSED: the tile at 1, 3 of the room, outside the ropes whichever way it is turned.
     */
    fun arenaDeathTile(player: Player): Tile? {
        val arena = arena(player) ?: return null
        return Tile(arena.instance.baseX + (arena.cell.x shl 3) + 1, arena.instance.baseZ + (arena.cell.z shl 3) + 3)
    }

    private fun distance(player: Player, tile: Pair<Int, Int>) = max(abs(player.x - tile.first), abs(player.z - tile.second))

    /** The house's fighting rings, each with its own rules ([arenaRefusal]). */
    enum class ArenaKind { BOXING, FENCING, COMBAT, PEDESTALS }

    /** A combat dummy attached: the [npc] standing in for its [loc] ([Houses.Open.dummies]). */
    class Attached(val npc: Npc, val loc: ZoneLoc)

    companion object {
        private val logger = KotlinLogging.logger {}

        /** The dummies' Attach (op 1), and the attached dummy's Detach, Upgrade and Remove (ops 3 to 5). */
        const val ATTACH = 1
        const val DETACH = 3
        const val NPC_UPGRADE = 4
        const val NPC_REMOVE = 5

        /**
         * Each combat dummy loc that can be built, with the NPC it is attached (the cache's poh_combat_dummy,
         * _undeadslayer and _upgraded_1_undead, and their _npc).
         */
        val DUMMIES: Map<Int, Int> = mapOf(29336 to 2668, 29337 to 7413, 9355 to 10507)

        /**
         * The attached dummies that count as any Slayer task: the undead and ornate undead ones (the Undead combat
         * dummy page: "acts as an undead creature, a demonic creature, and a Slayer task", and its NPC examine says the
         * same). INFERRED: whatever task the player has, or none.
         */
        val ANY_TASK: Set<Int> = setOf(7413, 10507)

        /** The tiles inside a ring's ropes along each side of the room (the cache's ropes stand between 1 and 2, and 5 and 6). */
        val RING_INSIDE = 2..5

        /** The ranging pedestals' spot (poh_magic_circle_mat). */
        const val RANGING_SPOT = 13147

        /** The rings' middle mats (poh_boxing_mat_middle, poh_fencing_mat_middle, poh_combat_mat_middle) and the ring each is. */
        val MATS: Map<Int, ArenaKind> = mapOf(13127 to ArenaKind.BOXING, 13136 to ArenaKind.FENCING, 13140 to ArenaKind.COMBAT)

        /** Which way an NPC standing in for a loc turned [angle] faces: 0 west, 1 north, 2 east, 3 south (GUESSED). */
        private fun facing(angle: Int): Direction = when (angle and 3) {
            0 -> Direction.WEST
            1 -> Direction.NORTH
            2 -> Direction.EAST
            else -> Direction.SOUTH
        }

        /** The racks' Search and the ropes' Climb-over and the barriers' Walk-through: op 1. */
        const val CROSS = 1

        /** GUESSED: how long climbing over a rope or walking through a barrier takes. */
        const val CROSS_TICKS = 2

        const val RED_GLOVES = 7671
        const val BLUE_GLOVES = 7673
        const val WOODEN_SWORD = 7675
        const val WOODEN_SHIELD = 7676
        const val PUGEL = 7679

        /** `human_get_over_combatring_nogloves`, `_redgloves`, `_bluegloves`. */
        const val CLIMB_ANIM = 3688
        const val CLIMB_RED_ANIM = 3689
        const val CLIMB_BLUE_ANIM = 3690

        /** The glove rack, weapons rack and extra weapons rack, and what each gives (the racks' Wiki pages). */
        val RACKS: Map<Int, List<Int>> = mapOf(
            13381 to listOf(RED_GLOVES, BLUE_GLOVES),
            13382 to listOf(RED_GLOVES, BLUE_GLOVES, WOODEN_SWORD, WOODEN_SHIELD),
            13383 to listOf(RED_GLOVES, BLUE_GLOVES, WOODEN_SWORD, WOODEN_SHIELD, PUGEL),
        )

        /** The boxing, fencing and combat rings' ropes, each with Climb-over (poh_boxing_ringwall_*, poh_fencing_ringwall, poh_combat_ringwall). */
        val ROPES: Set<Int> = setOf(13129, 13130, 13131, 13132, 13133, 13137)

        /** The ranging pedestals' magic barriers, with Walk-through (poh_magic_circle_wall and _off_wall). */
        val BARRIERS: Set<Int> = setOf(13145, 13146)

        /** What the combat room's racks give that can't leave the house. */
        val HOUSE_ONLY: Set<Int> = setOf(RED_GLOVES, BLUE_GLOVES, WOODEN_SWORD, WOODEN_SHIELD, PUGEL)

        /** The climb's animation for what [player] wears on their hands: the red or the blue boxing gloves, or neither. */
        fun climbAnim(player: Player): Int = when (player.equipment[WearPos.WEAPON]?.id) {
            RED_GLOVES -> CLIMB_RED_ANIM
            BLUE_GLOVES -> CLIMB_BLUE_ANIM
            else -> CLIMB_ANIM
        }

        /**
         * The two tiles [loc] stands between: a straight wall (shape 0) between its tile and the next one on its side
         * (angle 0 west, 1 north, 2 east, 3 south); a corner post (shape 3) between its tile and the diagonal one at its
         * corner (0 north-west, 1 north-east, 2 south-east, 3 south-west). Null for any other shape.
         */
        fun sides(loc: ZoneLoc): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
            val (dx, dz) = when (loc.shape) {
                WALL_STRAIGHT -> when (loc.angle) { 0 -> -1 to 0; 1 -> 0 to 1; 2 -> 1 to 0; else -> 0 to -1 }
                WALL_CORNER -> when (loc.angle) { 0 -> -1 to 1; 1 -> 1 to 1; 2 -> 1 to -1; else -> -1 to -1 }
                else -> return null
            }
            return (loc.x to loc.z) to (loc.x + dx to loc.z + dz)
        }

        private const val WALL_STRAIGHT = 0
        private const val WALL_CORNER = 3
    }
}

/** Every obj a house gives that stays in it: taken from whoever leaves with it, carried or worn ([Houses]). */
object HouseOnly {
    val OBJS: Set<Int> = Kitchen.HOUSE_ONLY + CombatRoom.HOUSE_ONLY
}
