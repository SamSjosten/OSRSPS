package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.instance.Instance
import osrsps.game.script.ScriptContext
import osrsps.game.social.ClanStore

/** The clan hall portal's lines (guessed: the Wiki gives none). */
object ClanHallLines : Lines("clan_hall", "Clan hall portal") {
    val NO_CLAN = line("no_clan", "Enter or Enter-member in no clan (guessed)")
    val NOT_ALLOWED = line("not_allowed", "a rank below the clan's Enter clan hall permission (guessed)")
    val GUEST_PROMPT = line("guest_prompt", "Enter-guest: the prompt for the clan's name (guessed)")
    val NO_SUCH_CLAN = line("no_such_clan", "Enter-guest: no clan has that name (guessed)", "name")
    val NO_GUESTS = line("no_guests", "Enter-guest: the clan doesn't let guests in, or bans the player (guessed)", "name")
    val NO_ROOM = line("no_room", "no room for another hall instance (guessed)")
}

/**
 * Misthalin's game-wide systems: the clan hall portal in the Clan Hub and each clan's hall. Sources: the OSRS Wiki's Clan
 * hall portal and Clan hall pages and the update post "A Guide to the Clans System", read 2026-11-19, and the 242 cache:
 * the portal (clan_hub_portal 41724 at 3186, 3471; Enter, Enter-member, Enter-guest) and the hall's map, map square
 * 27,85 (x 1728 to 1791, z 5440 to 5503), with its exit portal (clan_hall_exit_medieval 41617, Leave) at 1759, 5488.
 *
 * - Each clan's hall is an instance of that map square ([osrsps.game.instance.Instances]), made when the first player
 *   goes in and ended by the world once nobody is in it (the Wiki: the hall is instanced, and what is left in it goes
 *   when its members leave). Everyone going into one clan's hall while it stands goes into the same instance.
 * - Enter takes a clan member into their own clan's hall; someone in no clan with a guest clan loaded (the Clans tab's
 *   Another Clan page) into that clan's, else it asks for a clan's name as Enter-guest does (inferred). Enter-member
 *   takes a member into their own clan's hall (guessed: the Wiki doesn't say how it differs from Enter). Enter-guest
 *   asks for a clan's name and goes into that clan's hall as a guest (the Wiki's portal page: "you can visit another
 *   clan's hall by entering that clan's name ... if that clan has enabled guest entry").
 * - Who may enter follows the clan's Enter clan hall permission ([osrsps.game.social.Clans.mayEnterHall]); the cache
 *   gives it Guest as its default.
 * - The exit portal's Leave, and logging out inside, put the player outside the hub portal.
 * - The hall's altar's Pray-at recharges prayer as the churches' do. Its bank booths, deposit box and oven work as
 *   every other's (Banking, Cooking).
 *
 * Guessed: every line, the tiles each way (beside each portal), and that the portals act at once. Not built yet: the
 * hall settings (home world, lock, most players), the coffer, party chest, piano, combat ring, dummy and the hall's
 * people.
 */
class ClanHall(private val world: World) {
    /** Each clan's hall standing now, by clan id. */
    private val halls = HashMap<Long, Instance>()

    /** The clan whose hall holds tile [x], [z], or null. */
    fun clanAt(x: Int, z: Int): Long? {
        val instance = world.instances.at(x, z) ?: return null
        return halls.entries.firstOrNull { it.value === instance }?.key
    }

    fun register() {
        world.handlers.onLoc(PORTAL, ENTER) {
            arriveDelay()
            enter(this)
        }
        world.handlers.onLoc(PORTAL, ENTER_MEMBER) {
            arriveDelay()
            enterOwn(this)
        }
        world.handlers.onLoc(PORTAL, ENTER_GUEST) {
            arriveDelay()
            enterAsGuest(this)
        }
        world.handlers.onLoc(EXIT, LEAVE) {
            arriveDelay()
            leave(player)
        }
        world.handlers.onLoc(ALTAR, PRAY_AT) { prayAt(world) }
    }

    private suspend fun enter(context: ScriptContext) {
        val player = context.player
        if (world.clans.clanOf(player) != null) return enterOwn(context)
        val guest = world.clans.guestClanOf(player)
        if (guest != null) return go(player, guest)
        enterAsGuest(context)
    }

    private fun enterOwn(context: ScriptContext) {
        val player = context.player
        val clan = world.clans.clanOf(player) ?: return world.message(player, ClanHallLines.NO_CLAN)
        go(player, clan)
    }

    private suspend fun enterAsGuest(context: ScriptContext) {
        val player = context.player
        val name = context.stringDialog(world.textFor(player)[ClanHallLines.GUEST_PROMPT]).trim()
        if (name.isEmpty()) return
        val clan = world.clans.clanNamed(name) ?: return world.message(player, ClanHallLines.NO_SUCH_CLAN, "name" to name)
        // A member naming their own clan goes in as a member.
        if (clan.member(player.saveKey) == null && !world.clans.mayEnterHall(player, clan)) {
            return world.message(player, ClanHallLines.NO_GUESTS, "name" to clan.name)
        }
        go(player, clan)
    }

    /** [player] into [clan]'s hall, if its permission lets them; the hall is made if it isn't standing. */
    private fun go(player: Player, clan: ClanStore.Clan) {
        if (!world.clans.mayEnterHall(player, clan)) return world.message(player, ClanHallLines.NOT_ALLOWED)
        val instance = hallOf(clan) ?: return world.message(player, ClanHallLines.NO_ROOM)
        world.teleport(player, instance.baseX + (ARRIVE_X - TEMPLATE_X), instance.baseZ + (ARRIVE_Z - TEMPLATE_Z), 0)
    }

    private fun hallOf(clan: ClanStore.Clan): Instance? {
        forgetEnded()
        halls[clan.id]?.let { return it }
        val instance = world.instances.create(TEMPLATE_X, TEMPLATE_Z, ZONES, ZONES, OUTSIDE_X, OUTSIDE_Z, 0) ?: return null
        halls[clan.id] = instance
        return instance
    }

    private fun leave(player: Player) {
        if (clanAt(player.x, player.z) == null) return world.message(player, GameLines.NOTHING_INTERESTING)
        world.teleport(player, OUTSIDE_X, OUTSIDE_Z, 0)
    }

    /** Halls whose instance the world has ended, as nothing tells this when one does. */
    private fun forgetEnded() {
        if (halls.isEmpty()) return
        val live = world.instances.all.toHashSet()
        halls.values.retainAll(live)
    }

    companion object {
        /** clan_hub_portal, in the Clan Hub, and its options. */
        const val PORTAL = 41724
        const val ENTER = 1
        const val ENTER_MEMBER = 2
        const val ENTER_GUEST = 3

        /** clan_hall_exit_medieval, the hall's way out. */
        const val EXIT = 41617
        const val LEAVE = 1

        /** clan_medieval_altar, upstairs in the hall. */
        const val ALTAR = 41650
        const val PRAY_AT = 1

        /**
         * The hall's locs whose options work inside its instances (locs elsewhere in instances do nothing unless listed):
         * the exit, the altar, the bank booths (fai_varrock_bankbooth), the deposit box and the oven (fai_varrock_posh_stove).
         */
        val INSTANCE_LOCS = setOf(EXIT, ALTAR, 10583, 41723, 7421)

        /** The hall's map: the south-west tile of map square 27,85, copied whole (8 by 8 zones, every level). */
        const val TEMPLATE_X = 1728
        const val TEMPLATE_Z = 5440
        const val ZONES = 8

        /** Where a player arrives in the hall: south of its exit portal (guessed). */
        const val ARRIVE_X = 1759
        const val ARRIVE_Z = 5486

        /** Where a player leaving the hall stands: west of the hub portal (guessed). */
        const val OUTSIDE_X = 3185
        const val OUTSIDE_Z = 3472
    }
}
