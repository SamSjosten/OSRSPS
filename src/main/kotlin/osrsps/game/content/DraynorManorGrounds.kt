package osrsps.game.content

import kotlin.math.abs
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.HitsplatType
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.script.ScriptContext
import osrsps.game.skill.ToolCheck
import osrsps.game.zone.ZoneLoc

/** Veronica's talk once Ernest the Chicken is done (the Wiki's Transcript:Veronica, read 2026-11-01: Post-quest). */
object VeronicaLines : Lines("veronica", "Transcript:Veronica") {
    val THANKS = line("thanks", "after Ernest the Chicken: Veronica's first line")
    val WHERE = line("where", "the player's question")
    val WARTY = line("warty", "Veronica's answer")
}

/** What the manor and its grounds say; the last six are the inside's ([DraynorManor], step 12a). */
object ManorGroundsLines : Lines("draynor_manor", "Draynor Manor") {
    val DOORS_WONT_OPEN = line("doors_wont_open", "the front doors' Open from inside (2004Scape's quest_haunted script)")
    val DOORS_SLAM = line("doors_slam", "two ticks after walking in through the front doors (2004Scape's quest_haunted script)")
    val COMPOST_HANDS = line("compost_hands", "Search on the compost heap, in a message box (2004Scape's quest_haunted script)")
    val COMPOST_DIG = line("compost_dig", "a spade used on the compost heap (2004Scape's quest_haunted script)")
    val COMPOST_NOTHING = line("compost_nothing", "two ticks later, after Ernest the Chicken (2004Scape's quest_haunted script)")
    val FOUNTAIN_FISH = line("fountain_fish", "Search on the fountain after Ernest the Chicken, said by the player (2004Scape's quest_haunted script)")
    val VINE = line("vine", "a vine in the south-east of the grounds whips the player (2004Scape's manor_vines script)")
    val TREE_NO_AXE = line("tree_no_axe", "Chop on an undead tree with no axe (the Wiki's Undead tree page)")
    val TREE_BOUNCE = line("tree_bounce", "Chop on an undead tree with an axe (the Wiki's Undead tree page)")
    val SECRET_DOOR = line("secret_door", "Search on the bookcases from the east, as they open (2004Scape's quest_haunted script)")
    val LEVER_OPENS = line("lever_opens", "Pull on the lever in the room behind the bookcases (2004Scape's quest_haunted script)")
    val CLOSET_LOCKED = line("closet_locked", "Open on the closet door behind the stairs (2004Scape's quest_haunted script)")
    val CLOSET_UNLOCK = line("closet_unlock", "the closet key used on that door (2004Scape's quest_haunted script)")
    val CLOSET_THROUGH = line("closet_through", "straight after, as the player walks through (2004Scape's quest_haunted script)")
    val BACK_DOOR_LOCKED = line("back_door_locked", "Open on the back door from outside (guessed: the Wiki only says it is the way out)")
}

/**
 * Draynor Manor's grounds (Draynor piece step 11), after Ernest the Chicken and Animal Magnetism, which every account
 * counts as done.
 *
 * - The fence: the 241 map has no gate in it, only the gap at 3109 to 3110, 3330 where Veronica stands, so there is
 *   nothing to open. The dead trees chop as any dead tree ([osrsps.game.skill.Woodcutting]).
 * - The front doors (`haunteddoorl` 134 and `haunteddoorr` 135 at 3108 and 3109, 3353) let the player in from the
 *   south and say they won't open from inside; two ticks after walking in, the player is told they slam shut. As
 *   2004Scape's quest_haunted script has it, the player steps onto the doorway and then the tile north of it; the doors
 *   aren't shown opening (guessed: the swing, and that the step is instant).
 * - The compost heap (152) won't be searched by hand and gives nothing to a spade, and the fountain (153) is full of
 *   dead fish: both as 2004Scape's script has them after the quest, the dig and reach animations its `human_dig` and
 *   `human_pickpocket`.
 * - The 14 undead trees (`nasty_tree` 4418; the Wiki's map) show as `nasty_tree_choppable` (Undead tree, Chop) once
 *   Animal Magnetism is done (varbit 3185, `anma_main`). They can't be attacked and never move; one swipes a player on
 *   a tile next to it, whatever their combat level but not once they are tolerated ([osrsps.game.combat.Tolerance]), for 0 to 3 (the Wiki) every [TREE_SPEED] ticks with `deadtree_attack`. Chop with no axe or any
 *   axe gives the Wiki's messages, the axe's swing with it. Guessed: how often and how far they swipe, the even damage,
 *   and that no accuracy roll is made. The blessed axe's undead twigs are left out: it is an Animal Magnetism item.
 * - The vines (2004Scape's manor_vines script): walking into the 8 by 8 tiles from 3112, 3336 starts a five-tick
 *   timer that, while the player stays within 10 tiles of that corner each way, whips them for 1 with the
 *   `whippingplant_attack` spotanim on their tile.
 * - Veronica (3561) by the gap gives her post-quest talk; every chathead expression is guessed.
 * - The Belladonna patch stays as the map has it: Farming isn't built.
 */
object DraynorManorGrounds {
    const val FRONT_DOOR_L = 134
    const val FRONT_DOOR_R = 135
    const val COMPOST_HEAP = 152
    const val FOUNTAIN = 153
    const val OPEN = 1
    const val SEARCH = 1

    const val SPADE = 952
    const val ANIM_DIG = 830
    const val ANIM_REACH = 881

    const val VERONICA = 3561
    const val TALK_TO = 1

    /** `nasty_tree`, the map's undead trees, and the type they show after Animal Magnetism. */
    const val UNDEAD_TREE = 4418
    const val UNDEAD_TREE_CHOPPABLE = 4417
    const val CHOP = 1

    /** deadtree_attack. */
    const val ANIM_TREE_ATTACK = 73

    /** How often an undead tree can swipe. Guessed. */
    const val TREE_SPEED = 4

    /** The most an undead tree hits for (the Wiki: 0 to 3). */
    const val TREE_MAX_HIT = 3

    /** whippingplant_attack. */
    const val VINE_SPOTANIM = 8

    /** The vines' zone (2004Scape's 0_48_52_40_8): its south-west corner and size. */
    const val VINES_X = 3112
    const val VINES_Z = 3336
    const val VINES_SIZE = 8

    /** How far from that corner the timer keeps running (2004Scape: movecoord 9, 0, 9). */
    const val VINES_REACH = 9

    /** Ticks between whips (2004Scape: settimer 5). */
    const val VINE_TICKS = 5

    /** Whether the player at [x], [z], [level] is in the vines' zone, which starts the timer. */
    fun inVinesZone(x: Int, z: Int, level: Int): Boolean =
        level == 0 && x in VINES_X until VINES_X + VINES_SIZE && z in VINES_Z until VINES_Z + VINES_SIZE

    /** Whether the timer keeps running for a player at [x], [z], [level]. */
    fun inVinesReach(x: Int, z: Int, level: Int): Boolean =
        level == 0 && x in VINES_X..VINES_X + VINES_REACH && z in VINES_Z..VINES_Z + VINES_REACH

    /** Whether a player at [x], [z], [level] is on a tile next to [tree] (diagonals too; guessed). */
    fun besideTree(tree: Npc, x: Int, z: Int, level: Int): Boolean =
        level == tree.level && maxOf(abs(x - tree.x), abs(z - tree.z)) == 1

    internal suspend fun Dialogue.veronica(): Unit = with(VeronicaLines) {
        chatNpc(ChatAnim.HAPPY, THANKS)
        chatPlayer(ChatAnim.QUIZ, WHERE)
        chatNpc(ChatAnim.NEUTRAL, WARTY)
    }
}

/**
 * The grounds' two hazards: the vines' timer for each player, run once a tick from [World] for every player (started on
 * the tick they enter the zone, cleared once they leave its reach), and the undead trees' swipes.
 */
class ManorHazards(private val world: World) {
    /** A vine's damage to a player, as a hit from no one; tests swap these for recorders. Called on the game thread. */
    @Volatile internal var whip: (Player, Int) -> Unit = { player, damage ->
        world.meleeCombat.statusHit(player, damage, HitsplatType.DAMAGE_ME, HitsplatType.DAMAGE_OTHER)
    }

    /** An undead tree's swipe at a player, for the damage rolled. */
    @Volatile internal var swipe: (Player, Int) -> Unit = { player, damage -> world.meleeCombat.hitPlayer(player, null, damage, world.tick) }

    /** An undead tree swipes the first player beside it, when its next swipe is due. */
    fun swipe(tree: Npc) = with(DraynorManorGrounds) {
        val tick = world.tick
        if (tick < tree.nextAttackTick) return
        val player = world.onlinePlayers.firstOrNull {
            !it.loggedOut && !it.dead && it.hitpoints > 0 && !it.tolerance.tolerant && besideTree(tree, it.x, it.z, it.level)
        } ?: return
        tree.nextAttackTick = tick + TREE_SPEED
        tree.face(player)
        tree.effects.anim(ANIM_TREE_ATTACK)
        swipe(player, world.random.between(0, TREE_MAX_HIT))
    }

    fun process(player: Player) = with(DraynorManorGrounds) {
        val tick = world.tick
        if (player.vinesDue < 0) {
            if (inVinesZone(player.x, player.z, player.level)) player.vinesDue = tick + VINE_TICKS
            return
        }
        if (!inVinesReach(player.x, player.z, player.level)) {
            player.vinesDue = -1
            return
        }
        if (tick < player.vinesDue) return
        player.vinesDue = tick + VINE_TICKS
        world.message(player, ManorGroundsLines.VINE)
        world.zones.mapAnim(player.x, player.z, player.level, VINE_SPOTANIM)
        whip(player, 1)
    }
}

fun registerDraynorManorGrounds(world: World) {
    with(DraynorManorGrounds) {
        for (door in listOf(FRONT_DOOR_L, FRONT_DOOR_R)) world.handlers.onLoc(door, OPEN) { op -> frontDoor(world, op.loc!!) }
        world.handlers.onLoc(COMPOST_HEAP, SEARCH) {
            world.dialogues.start(this) { mesbox(ManorGroundsLines.COMPOST_HANDS) }
        }
        world.handlers.onObjOnLoc(COMPOST_HEAP, SPADE) {
            arriveDelay()
            player.anim(ANIM_DIG)
            world.message(player, ManorGroundsLines.COMPOST_DIG)
            delay(2)
            world.message(player, ManorGroundsLines.COMPOST_NOTHING)
        }
        world.handlers.onLoc(FOUNTAIN, SEARCH) {
            arriveDelay()
            player.anim(ANIM_REACH)
            world.dialogues.start(this) { chatPlayer(ChatAnim.NEUTRAL, ManorGroundsLines.FOUNTAIN_FISH) }
        }
        world.handlers.onNpc(VERONICA, TALK_TO) { op -> world.dialogues.start(this, op.npc) { veronica() } }
        world.handlers.onNpc(UNDEAD_TREE_CHOPPABLE, CHOP) {
            when (val axe = world.woodcutting.data.tools.best(player)) {
                is ToolCheck.Found -> {
                    player.anim(axe.tool.anim)
                    world.message(player, ManorGroundsLines.TREE_BOUNCE)
                }
                is ToolCheck.TooLow -> world.message(player, ManorGroundsLines.TREE_BOUNCE)
                ToolCheck.None -> world.message(player, ManorGroundsLines.TREE_NO_AXE)
            }
        }
        world.npcScripts.onTick(listOf(UNDEAD_TREE)) { tree -> world.manorHazards.swipe(tree) }
    }
}

/** The front doors: in from the south, onto the doorway and the tile north of it; shut to anyone inside. */
private suspend fun ScriptContext.frontDoor(world: World, door: ZoneLoc) {
    if (player.z > door.z) {
        world.message(player, ManorGroundsLines.DOORS_WONT_OPEN)
        return
    }
    world.teleport(player, door.x, door.z + 1, door.level)
    delay(2)
    world.message(player, ManorGroundsLines.DOORS_SLAM)
}
