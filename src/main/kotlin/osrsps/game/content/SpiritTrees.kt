package osrsps.game.content

import osrsps.game.FrameSlot
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/** The spirit trees' words. */
object SpiritTreeLines : Lines("spirit_tree", "Spirit_tree") {
    val MENU_TITLE = line("menu_title", "the travel menu's title; words not on the Wiki (its picture shows them)")
    val TREE_GNOME_VILLAGE = line("tree_gnome_village", "a travel menu entry, as the cache's Last-destination option names it")
    val GNOME_STRONGHOLD = line("gnome_stronghold", "a travel menu entry, as the cache's Last-destination option names it")
    val BATTLEFIELD_OF_KHAZARD = line("battlefield_of_khazard", "a travel menu entry, as the cache's Last-destination option names it")
    val GRAND_EXCHANGE = line("grand_exchange", "a travel menu entry, as the cache's Last-destination option names it")
    val FELDIP_HILLS = line("feldip_hills", "a travel menu entry, as the cache's Last-destination option names it")
    val PRIFDDINAS = line("prifddinas", "a travel menu entry, as the cache's Last-destination option names it")
    val PORT_SARIM = line("port_sarim", "a travel menu entry, as the cache's Last-destination option names it")
    val ETCETERIA = line("etceteria", "a travel menu entry, as the cache's Last-destination option names it")
    val BRIMHAVEN = line("brimhaven", "a travel menu entry, as the cache's Last-destination option names it")
    val HOSIDIUS = line("hosidius", "a travel menu entry, as the cache's Last-destination option names it")
    val FARMING_GUILD = line("farming_guild", "a travel menu entry, as the cache's Last-destination option names it")
    val YOUR_HOUSE = line("your_house", "a travel menu entry, as the cache's Last-destination option names it")
    val POISON_WASTE = line("poison_waste", "a travel menu entry, as the cache's Last-destination option names it")
    val LAGUNA_AURORAE = line("laguna_aurorae", "a travel menu entry, as the cache's Last-destination option names it")
    val ALREADY_HERE = line("already_here", "the tree, when the player picks the tree they're at (the Wiki's Spirit tree transcript)")
    val BARK = line("bark", "the box before the trip (the Wiki's Spirit tree transcript)")
    val NO_BRETHREN = line("no_brethren", "the tree, for a place with no tree to go to (the Wiki's Spirit tree transcript)")
    val NOT_IN_HOUSE = line("not_in_house", "the tree, for Your house with no tree there (the Wiki's Spirit tree transcript)")
    val LAGUNA_RESTRICTED = line("laguna_restricted", "the tree, for Laguna Aurorae not yet visited (the Wiki's Spirit tree transcript)")
    val LAGUNA_VISIT = line("laguna_visit", "the box after it (the Wiki's Spirit tree transcript)")
    val GREETING = line("greeting", "Talk-to (the Wiki's Spirit tree transcript, Gnome Stronghold and Tree Gnome Village)")
    val WHERE = line("where", "the player's option and line (the transcript)")
    val NO_THANKS = line("no_thanks", "the player's option and line (the transcript)")
    val RELATED = line("related", "the tree's answer to where, before the travel menu (the transcript)")
}

/**
 * One place on the spirit tree menu: its number in the menu and in varbit `spirit_tree_previous` (the cache's
 * Last-destination labels, 1 to 14, in that order), its label, and for the trees the 242 map places, where the tree
 * stands and where a trip lands.
 */
enum class SpiritTreeSpot(val label: Line, val tree: Destination? = null, val landing: Destination? = null) {
    TREE_GNOME_VILLAGE(SpiritTreeLines.TREE_GNOME_VILLAGE, Destination(2543, 3168, 0), Destination(2543, 3167, 0)),
    GNOME_STRONGHOLD(SpiritTreeLines.GNOME_STRONGHOLD, Destination(2460, 3445, 0), Destination(2460, 3444, 0)),
    BATTLEFIELD_OF_KHAZARD(SpiritTreeLines.BATTLEFIELD_OF_KHAZARD, Destination(2555, 3260, 0), Destination(2555, 3259, 0)),
    GRAND_EXCHANGE(SpiritTreeLines.GRAND_EXCHANGE, Destination(3184, 3509, 0), Destination(3184, 3508, 0)),
    FELDIP_HILLS(SpiritTreeLines.FELDIP_HILLS, Destination(2486, 2850, 0), Destination(2486, 2849, 0)),
    PRIFDDINAS(SpiritTreeLines.PRIFDDINAS, Destination(3274, 6124, 0), Destination(3274, 6123, 0)),
    PORT_SARIM(SpiritTreeLines.PORT_SARIM),
    ETCETERIA(SpiritTreeLines.ETCETERIA),
    BRIMHAVEN(SpiritTreeLines.BRIMHAVEN),
    HOSIDIUS(SpiritTreeLines.HOSIDIUS),
    FARMING_GUILD(SpiritTreeLines.FARMING_GUILD),
    YOUR_HOUSE(SpiritTreeLines.YOUR_HOUSE),
    POISON_WASTE(SpiritTreeLines.POISON_WASTE),
    LAGUNA_AURORAE(SpiritTreeLines.LAGUNA_AURORAE, Destination(1202, 2786, 0), Destination(1202, 2785, 0)),
    ;

    /** The spot's number in the menu and in `spirit_tree_previous`, from 1. */
    val number: Int get() = ordinal + 1

    companion object {
        fun byNumber(number: Int): SpiritTreeSpot? = entries.getOrNull(number - 1)

        /** The spot whose tree has its south-west tile at [x], [z], [level]. */
        fun at(x: Int, z: Int, level: Int): SpiritTreeSpot? = entries.firstOrNull { it.tree == Destination(x, z, level) }
    }
}

/**
 * The spirit trees (travel networks step T4). Each tree the 242 map places is a multiloc on its quest's var: the small
 * trees (`spirittree_small` 1295: the Grand Exchange's, Battlefield of Khazard's, Feldip Hills' and Laguna Aurorae's)
 * and Prifddinas's (`spirittree_prif` 37329) on Tree Gnome Village's progress, the Gnome Stronghold's (`stronghold_ent`
 * 1294) on The Grand Tree's, and Tree Gnome Village's own (`ent` 1293) on varbit `bolren_got_orbs`. Every account has
 * finished the quests, so each shows Travel, Talk-to and Last-destination.
 *
 * - Travel opens the travel menu (interface `menu`, 187, laid out by client script `menu`), listing the fourteen places
 *   as the cache's Last-destination labels number them; picking one goes there, by way of [SpiritTreeLines.BARK].
 * - Talk-to: the tree's greeting and two options; "Where can I go?" opens the same menu.
 * - Last-destination goes straight to the place last travelled to (varbit `spirit_tree_previous`, which names it in
 *   the option).
 * - The tree the player is at answers [SpiritTreeLines.ALREADY_HERE]; a place with no tree to go to,
 *   [SpiritTreeLines.NO_BRETHREN] (Your house: [SpiritTreeLines.NOT_IN_HOUSE]); Laguna Aurorae, which needs a Sailing
 *   visit, [SpiritTreeLines.LAGUNA_RESTRICTED] and [SpiritTreeLines.LAGUNA_VISIT].
 *
 * Sources: the 242 cache (the loc types, their places on the map, the varbit, interface 187 and client script 217);
 * the Wiki's Spirit tree page and its transcript (read 19 Nov 2026) for the places, their requirements and every line.
 *
 * Inferred: the menu lists the places in the order the cache's Last-destination labels number them (the Wiki shows
 * the menu only as a picture); Tree Gnome Village's tree shows its options after the quest at `bolren_got_orbs` 2,
 * the lowest value whose form has them ([osrsps.game.Quests.AFTER_QUESTS]); the Poison Waste's tree isn't on the 242
 * map, so the Poison Waste has no tree to go to.
 * Guessed: where each trip lands (the tile south of the tree's south-west corner, each walkable in the 242 map); the
 * trip itself (the box, then the move once it is continued, with no animation and a tick's pause); the menu's entries
 * all shown alike, the unavailable ones too; the tree's chathead (`treevillage_small_spirittree` for the small trees,
 * `treevillage_spirittree` for the big ones); Laguna Aurorae refusing every player, as Sailing isn't built.
 * Not built yet: trees grown in farming patches (Port Sarim, Etceteria, Brimhaven, Hosidius, the Farming Guild) and in
 * a house, which answer [SpiritTreeLines.NO_BRETHREN] and [SpiritTreeLines.NOT_IN_HOUSE] until they are.
 */
class SpiritTrees(private val world: World) {
    fun register() {
        for (tree in TREES) {
            world.handlers.onLoc(tree, TRAVEL) { op -> travelMenu(op.loc ?: return@onLoc) }
            world.handlers.onLoc(tree, TALK) { op -> talk(op.loc ?: return@onLoc) }
            world.handlers.onLoc(tree, LAST_DESTINATION) { op -> lastDestination(op.loc ?: return@onLoc) }
        }
    }

    private fun here(loc: ZoneLoc): SpiritTreeSpot? = SpiritTreeSpot.at(loc.x, loc.z, loc.level)

    private suspend fun ScriptContext.travelMenu(loc: ZoneLoc) {
        arriveDelay()
        val from = here(loc) ?: return
        val to = menu() ?: return
        go(loc, from, to)
    }

    private suspend fun ScriptContext.talk(loc: ZoneLoc) {
        arriveDelay()
        val from = here(loc) ?: return
        val head = world.npcTypes[if (loc.id == BIG_TREE || loc.id == STRONGHOLD_TREE) BIG_HEAD else SMALL_HEAD] ?: return
        var where = false
        world.dialogues.start(this) {
            val name = head.name
            chatNpc(head, name, ChatAnim.NEUTRAL, SpiritTreeLines.GREETING)
            if (choice(SpiritTreeLines.WHERE, SpiritTreeLines.NO_THANKS) == 1) {
                chatPlayer(ChatAnim.NEUTRAL, SpiritTreeLines.WHERE)
                chatNpc(head, name, ChatAnim.NEUTRAL, SpiritTreeLines.RELATED)
                where = true
            } else {
                chatPlayer(ChatAnim.NEUTRAL, SpiritTreeLines.NO_THANKS)
            }
        }
        if (!where) return
        val to = menu() ?: return
        go(loc, from, to)
    }

    private suspend fun ScriptContext.lastDestination(loc: ZoneLoc) {
        arriveDelay()
        val from = here(loc) ?: return
        // The client shows the option only once a trip has been made; a click without one is forged.
        if (player.vars.types.varbit(VARBIT_PREVIOUS) == null) return
        val to = SpiritTreeSpot.byNumber(player.vars.getVarbit(VARBIT_PREVIOUS)) ?: return
        go(loc, from, to)
    }

    /** The travel menu: the place picked, or null if the player closed it. */
    private suspend fun ScriptContext.menu(): SpiritTreeSpot? {
        val words = world.textFor(player)
        val options = SpiritTreeSpot.entries.joinToString("|") { words[it.label] }
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, MENU)
        world.interfaces.runClientScript(player, MENU_SCRIPT, listOf(words[SpiritTreeLines.MENU_TITLE], options, 1))
        world.interfaces.setEvents(player, MENU, MENU_ENTRIES, 0, SpiritTreeSpot.entries.size - 1, 0, PAUSE_BUTTON)
        val picked = pauseButton(MENU, MENU_ENTRIES, 0 until SpiritTreeSpot.entries.size)
        world.interfaces.closeModals(player)
        return SpiritTreeSpot.entries.getOrNull(picked)
    }

    /** The trip from the tree at [loc] ([from]) to [to], or the tree's answer for why not. */
    private suspend fun ScriptContext.go(loc: ZoneLoc, from: SpiritTreeSpot, to: SpiritTreeSpot) {
        val head = world.npcTypes[if (loc.id == BIG_TREE || loc.id == STRONGHOLD_TREE) BIG_HEAD else SMALL_HEAD] ?: return
        val refusal = when {
            to == from -> SpiritTreeLines.ALREADY_HERE
            to == SpiritTreeSpot.YOUR_HOUSE -> SpiritTreeLines.NOT_IN_HOUSE
            to == SpiritTreeSpot.LAGUNA_AURORAE -> SpiritTreeLines.LAGUNA_RESTRICTED
            to.landing == null -> SpiritTreeLines.NO_BRETHREN
            else -> null
        }
        if (refusal != null) {
            world.dialogues.start(this) {
                chatNpc(head, head.name, ChatAnim.NEUTRAL, refusal)
                if (refusal == SpiritTreeLines.LAGUNA_RESTRICTED) mesbox(SpiritTreeLines.LAGUNA_VISIT)
            }
            return
        }
        val landing = to.landing ?: return
        world.dialogues.start(this) { mesbox(SpiritTreeLines.BARK) }
        // The box waited: the player must still be at the tree, and the tree there.
        if (!world.locs.exists(loc) || maxOf(kotlin.math.abs(player.x - loc.x), kotlin.math.abs(player.z - loc.z)) > TREE_REACH) return
        if (player.vars.types.varbit(VARBIT_PREVIOUS) != null) player.vars.setVarbit(VARBIT_PREVIOUS, to.number)
        delay(TRIP_TICKS)
        world.teleport(player, landing.x, landing.z, landing.level)
    }

    companion object {
        /** `ent` (Tree Gnome Village), `stronghold_ent`, `spirittree_small` and `spirittree_prif`. */
        const val BIG_TREE = 1293
        const val STRONGHOLD_TREE = 1294
        const val SMALL_TREE = 1295
        const val PRIF_TREE = 37329
        val TREES = listOf(BIG_TREE, STRONGHOLD_TREE, SMALL_TREE, PRIF_TREE)

        const val TRAVEL = 1
        const val TALK = 2
        const val LAST_DESTINATION = 3

        /** Varbit `spirit_tree_previous`: the last place travelled to, by its number, 0 for none. */
        const val VARBIT_PREVIOUS = 20252

        /** Interface `menu`, its entries' layer (`lj_layer1`, whose children the script makes), and client script `menu`. */
        const val MENU = 187
        const val MENU_ENTRIES = 3
        const val MENU_SCRIPT = 217

        /** IF_SETEVENTS events1 bit 0: the entries send a resume click. */
        const val PAUSE_BUTTON = 1

        /** NPCs `treevillage_small_spirittree` and `treevillage_spirittree`: the trees' chatheads. */
        const val SMALL_HEAD = 4981
        const val BIG_HEAD = 4982

        /** Guessed: a tick between the box and the move. */
        const val TRIP_TICKS = 1

        /** A tree is used from beside it; the big trees are 4 by 4, so a player on their far side is 4 tiles from the corner. */
        const val TREE_REACH = 4
    }
}
