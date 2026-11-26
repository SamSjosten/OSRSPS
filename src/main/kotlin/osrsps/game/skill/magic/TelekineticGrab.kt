package osrsps.game.skill.magic

import osrsps.game.FountainOfRune
import osrsps.game.GameLines
import osrsps.game.GroundObjs
import osrsps.game.Interaction
import osrsps.game.Movement
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.Spellbook
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.QueueType
import osrsps.game.zone.Projectile
import osrsps.game.zone.ZoneObj
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.LineOfSight
import osrsps.map.RouteTarget

/** What Telekinetic Grab says; its words are on the server (data/dialogue.txt), not in git. */
object TelekineticGrabLines : Lines("telekinetic_grab", "Telekinetic Grab") {
    val FIZZLES = line("fizzles", "the caster more than 10 tiles from the item when it arrives (the Wiki's Telekinetic Grab)")
}

/**
 * Telekinetic Grab (Magic utility step U7b): the spell picked on the magic tab and clicked on an item on the ground
 * (OPOBJT). The player walks until the item is within [RANGE] tiles and in sight, then casts: the runes and XP go, the
 * graphic flies to the item, and [ARRIVAL] ticks later the item (a whole stack) is taken into the backpack as Take would
 * ([osrsps.game.GroundObjs.take]), with its "too late" message if it has gone meanwhile.
 *
 * Verified on the OSRS Wiki (Telekinetic Grab, read 2026-11-19): level 33, 43 XP, an air and a law rune (the cache's;
 * a staff of air stands in), 10 tiles over fences and rivers, a cast every 3 ticks, a whole stack, the travel time the
 * same at any distance, runes spent but nothing got when the caster has gone too far away (its message, quoted), and
 * "Too late - it's gone!" (Take's own key) when someone else took it first. The animation and graphics are the cache's
 * `human_casttelegrab` (723), `telegrab_casting` (142), `telegrab_travel` (143) and `telegrab_impact` (144).
 *
 * Guessed: the item arriving [ARRIVAL] ticks after the cast; "too far away" meaning more than [RANGE] tiles when it
 * arrives; a full backpack refusing the cast with Take's message and nothing spent; waiting out the 3 ticks rather than
 * dropping the click; the projectile's heights and timing (combat's); the impact graphic on the item's tile at
 * height 0; the cast going on when the player walks off after it (only distance stops it); no XP at the Fountain of
 * Rune. Not built: the Tower of Life, Ahab's beer and the other items the Wiki lists as ungrabbable, and members'
 * objects on free worlds (this world is a members' world).
 */
class TelekineticGrab(
    private val world: World,
    private val spell: ItemSpell?,
    private val collision: CollisionMap,
    private val movement: Movement,
) {
    /** The tick before which each player can't cast it again. */
    private val nextCast = java.util.WeakHashMap<Player, Long>()

    /** Whether component [component] of the magic tab is this spell. */
    fun casts(component: Int): Boolean = spell != null && spell.component == component

    /** The spell clicked on obj [id] at [x], [z]: the walk into range starts, or nothing if the player can't see it. */
    fun click(player: Player, id: Int, x: Int, z: Int, invertRun: Boolean) {
        val spell = spell ?: return
        if (world.groundObjs.find(player, id, x, z, player.level) == null) return
        val interaction = GrabInteraction(spell, id, x, z, player.level)
        player.interaction = interaction
        if (!interaction.inRange(player)) movement.request(player, interaction.target, invertRun)
    }

    private inner class GrabInteraction(val spell: ItemSpell, val id: Int, val x: Int, val z: Int, val level: Int) : Interaction {
        val target: RouteTarget =
            if (collision[x, z, level] and CollisionFlag.BLOCK_WALK == 0) RouteTarget.tile(x, z)
            else RouteTarget.loc(x, z, osrsps.map.LocShape.GROUND_DECOR, 0, 1, 1, 0)

        // An item under the player is in range too; combat's check refuses a target on the same tile (Codex review on #982).
        fun inRange(player: Player): Boolean =
            player.level == level && ((player.x == x && player.z == z) || LineOfSight.inRange(collision, level, player.x, player.z, 1, x, z, 1, 1, RANGE))

        /** Waiting out the 3 ticks in range keeps the click (Codex review on #982), rather than "I can't reach that!". */
        override fun holding(player: Player): Boolean = inRange(player) && (nextCast[player] ?: 0L) > world.tick

        override fun tryInteract(player: Player): Boolean {
            if (player.level != level) return true
            val obj = world.groundObjs.find(player, id, x, z, level) ?: return true
            if (!inRange(player)) return false
            player.waypoints.clear()
            if ((nextCast[player] ?: 0L) > world.tick) return false
            cast(player, spell, obj)
            return true
        }
    }

    private fun cast(player: Player, spell: ItemSpell, obj: ZoneObj) {
        if (Spellbook.of(player) != Spellbook.STANDARD) return
        val casting = world.itemSpells
        if (!casting.hasLevel(player, spell)) return
        val type = world.objTypes[obj.id] ?: return
        val text = world.textFor(player)
        // An item whose Take is content's own (a quest piece, giant bones that don't fit) isn't grabbed: nothing
        // interesting happens, nothing spent (guessed, as the Wiki's other ungrabbable items). Clues and scroll boxes go
        // through the trails' own checks on arrival.
        if (!isTrailPickup(obj.id) && world.handlers.find(Op(OpTarget.GROUND_OBJ, obj.id, GroundObjs.TAKE_OPTION)) != null) {
            player.message(text[GameLines.NOTHING_INTERESTING])
            return
        }
        if (!world.groundObjs.fits(player.inventory, type, obj.count)) {
            player.message(text[GameLines.NO_SPACE])
            return
        }
        val costs = casting.runesToPay(player, spell) ?: return
        if (!casting.pay(player, costs)) return
        nextCast[player] = world.tick + spell.ticks
        if (!FountainOfRune.covers(player)) player.skills.addTenths(Skill.MAGIC, spell.xpTenths)
        player.infos.playerInfo.avatar.extendedInfo.setFaceLoc(obj.x, obj.z, 1, 1, instant = false, walkMode = 0)
        player.anim(spell.anim)
        player.spotanim(spell.spotanim, ItemSpells.SPOTANIM_HEIGHT)
        world.zones.projectile(
            Projectile(
                id = TRAVEL, fromX = player.x, fromZ = player.z, level = player.level, toX = obj.x, toZ = obj.z,
                startHeight = 172, endHeight = 0, startTime = 51, endTime = ARRIVAL * CYCLES_PER_TICK, angle = 16, progress = 64,
                sourceIndex = -(player.index + 1),
            ),
        )
        // A queue first counts down in the next tick's script phase, before this tick's movement phase that casts, so
        // ARRIVAL - 1 lands it ARRIVAL ticks on (Codex review on #982).
        world.scripts.queue(player, QUEUE, QueueType.SOFT, ARRIVAL - 1) { land(player, obj) }
    }

    /** The graphic reaches [obj]: it comes to the player if it is still there and they are near enough. */
    private fun land(player: Player, obj: ZoneObj) {
        world.zones.mapAnim(obj.x, obj.z, obj.level, IMPACT)
        if (player.level != obj.level || LineOfSight.distance(player.x, player.z, 1, obj.x, obj.z, 1, 1) > RANGE) {
            player.message(world.textFor(player)[TelekineticGrabLines.FIZZLES])
            return
        }
        if (isTrailPickup(obj.id)) world.trails.takeChecked(player, obj) else world.groundObjs.take(player, obj)
    }

    /** A clue scroll or scroll box, whose pickup has the trails' own rules. */
    private fun isTrailPickup(id: Int): Boolean =
        world.trails.tierOf(id) != null || osrsps.game.trail.TrailTier.entries.any { it.scrollBox == id || it.sharedClue == id } ||
            id in world.trails.data.byItem

    companion object {
        /** Tiles the spell reaches (Wiki). */
        const val RANGE = 10

        /** Ticks from the cast to the item arriving; guessed. */
        const val ARRIVAL = 2

        /** `telegrab_travel` and `telegrab_impact`. */
        const val TRAVEL = 143
        const val IMPACT = 144

        private const val CYCLES_PER_TICK = 30
        private const val QUEUE = "telekinetic_grab"

        fun load(objTypes: ObjTypes): ItemSpell? = ItemSpell.load(objTypes, "33_tele_grab", 430, 3, anim = 723, spotanim = 142)
    }
}
