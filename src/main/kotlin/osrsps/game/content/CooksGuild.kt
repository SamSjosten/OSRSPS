package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

private val logger = KotlinLogging.logger {}

/** The Head chef's lines, from the Wiki's Transcript:Head chef. */
object HeadChefLines : Lines("head_chef", "Transcript:Head chef") {
    val WELCOME = line("welcome", "his greeting below 99 Cooking: only accomplished chefs may enter; use the facilities")
    val NICE_CAPE = line("nice_cape", "option 1: the player admires his cape (the menu's text)")
    val NICE_CAPE_SAID = line("nice_cape_said", "option 1: the player's box, worded a little differently from the option on live")
    val CAPE_INFO = line("cape_info", "option 1: his answer, the Skillcape of Cooking at 99, and no burnt food")
    val CAPE_INFO_2 = line("cape_info_2", "option 1: his second box, the player could wear one too at 99")
    val THANKS_BYE = line("thanks_bye", "option 2: thanks, bye")
    val MASTER = line("master", "his greeting at 99 Cooking: he offers the Skillcape of Cooking")
    val NO_THANKS = line("no_thanks", "99 menu, option 1: no thanks")
    val CHANGE_MIND = line("change_mind", "99 menu, option 1: his answer, come back if the player changes their mind")
    val YES_PLEASE = line("yes_please", "99 menu, option 2: yes please")
    val BUY = line("buy", "the player asks to buy a cape")
    val PRICE = line("price", "his price: 99,000 coins")
    val TOO_EXPENSIVE = line("too_expensive", "price menu, option 1: much too expensive")
    val SHAME = line("shame", "price menu, option 1: his regret")
    val SURE = line("sure", "price menu, option 2: sure")
    val NO_MONEY = line("no_money", "sure, under 99,000 coins: the player hasn't the money")
    val COME_BACK = line("come_back", "sure, under 99,000 coins: come back with it")
    val NO_SPACE = line("no_space", "sure, with the coins but no room: each cape comes with a free hood")
    val SOLD = line("sold", "sure, with the coins and room: the player can now be called Master Chef")
    val SKILLCAPE = line("skillcape", "the menu of a player who owns a cape, option 1: Skillcape")
    val HOOD = line("hood", "that menu, option 2: Hood")
    val HOOD_ASK = line("hood_ask", "hood: the player asks for another hood")
    val HOOD_GIVEN = line("hood_given", "hood: his answer, as the hood is handed over free")
    val HOOD_HANDED = line("hood_handed", "hood: the message box after his answer, the hood handed over")
    val DOOR_HAT = line("door_hat", "the door, 32 Cooking or more without a chef's hat or the like")
    val DOOR_LEVEL = line("door_level", "the door, below 32 Cooking without a chef's hat: get 32 and come back in a hat")
    val DOOR_LEVEL_HAT = line("door_level_hat", "the door, below 32 Cooking in a chef's hat: get 32 Cooking")
}

/** The guild's north door's message: guessed to be a game message, its words not yet found on the Wiki. */
object CooksGuildLines : Lines("cooks_guild", "Cooks' Guild") {
    val BANK_DOOR = line("bank_door", "the north door to the bank and range, for those with 99 Cooking (or the hard Varrock Diary, not built)")
}

/**
 * The Cooks' Guild (Edgeville piece step 9), north-west of Varrock's west gate: the front door (`chefdoor` 24958), open
 * to those with 32 Cooking wearing a chef's hat or golden chef's hat, a Cooking cape or hood, a max cape or hood, or
 * Varrock armour 3 or 4 (OSRS Wiki, Cooks' Guild), with the Head chef (2658) refusing the rest; his talk and his
 * Skillcape of Cooking for 99,000 coins at 99 (trimmed when another skill is 99, as every skillcape seller here); the
 * spiral stairs over three floors; the ranges (`fai_varrock_range`, Cooking's [osrsps.game.skill.Cooking.RANGES]); the
 * top floor's hopper and controls and the ground floor's flour bin, as Mill Lane Mill's (the flour is shared); the
 * north room's door to the bank and range, for 99 Cooking (the Wiki's other way in, the hard Varrock Diary, isn't
 * built); and the tables' item spawns (2004Scape's map m49_53). Ids and tiles are the 241 cache's.
 *
 * Inferred: the north door's message (a key until the Words thread finds it), the flour bin's Empty being op 1 of its
 * multiloc (`fai_varrock_windmill_base`) as of Mill Lane Mill's, and the max capes that count (every `skillcape_max`
 * cape and hood the cache has). Left out: the dairy churn (members' churning isn't built), Romily Weaklax's pie tasks
 * (his Talk-to isn't built; his Trade opens the Pie Shop) and the Head chef's treasure trail talk.
 */
object CooksGuild {
    const val HEAD_CHEF = 2658
    const val TALK_TO = 1

    /** merchant_pies: Romily Weaklax, whose Trade opens the Pie Shop (registered with every shop, [registerShops]). */
    const val ROMILY = 6533
    const val TRADE = 3

    /** chefdoor and chefdoor_open: the front door, 3143, 3443, opening north into the guild. */
    const val FRONT_DOOR = 24958
    const val FRONT_DOOR_OPEN = 24959
    val FRONT_DOOR_TILE = 3143 to 3443
    const val OPEN = 1

    /** fai_varrock_cook_guild_door: the door into the north-east room with the bank, 3144, 3452, on its tile's west edge. */
    const val NORTH_DOOR = 10045
    val NORTH_DOOR_TILE = 3144 to 3452

    /** fai_varrock_mill_spiralstairs, _middle and _top, at 3144, 3447. */
    const val STAIRS_BOTTOM = 2608
    const val STAIRS_MIDDLE = 2609
    const val STAIRS_TOP = 2610
    const val CLIMB = 1
    const val CLIMB_UP = 2
    const val CLIMB_DOWN = 3
    val STAIRS_TILE = 3144 to 3447

    /** fai_varrock_windmill_hopper and _levers on the top floor, and the flour bin's multiloc on the ground floor. */
    const val HOPPER = 2586
    const val CONTROLS = 2607
    const val FLOUR_BIN = 14960

    const val ENTRY_LEVEL = 32
    const val NORTH_LEVEL = 99
    const val MAX_LEVEL = 99
    const val COINS = 995
    const val CAPE_PRICE = 99_000

    /** skillcape_cooking, its trimmed one and skillcape_cooking_hood. */
    const val COOKING_CAPE = 9801
    const val COOKING_CAPE_TRIMMED = 9802
    const val COOKING_HOOD = 9803

    /** chefs_hat and chefs_hat_gold. */
    val CHEFS_HATS = setOf(1949, 20205)

    /**
     * What lets a player with 32 Cooking in: the chef's hats, the Cooking cape and hood, Varrock armour 3 and 4
     * (varrock_armour_hard and _elite), and every max cape and hood (skillcape_max and its kinds, broken ones aside).
     */
    val ENTRY_WORN: Set<Int> = CHEFS_HATS + setOf(COOKING_CAPE, COOKING_CAPE_TRIMMED, COOKING_HOOD, 13106, 13107) + setOf(
        13280, 13281, 13329, 13330, 13331, 13332, 13333, 13334, 13335, 13336, 13337, 13338, 13342, 20760, 20764, 21282, 21285,
        21776, 21778, 21780, 21782, 21784, 21786, 21898, 21900, 24133, 24134, 24135, 24232, 24233, 24234, 24855, 24857, 27363,
        27365, 27366, 28902, 28904, 28906, 34625, 34627,
    )

    fun wearsEntry(player: Player): Boolean = ENTRY_WORN.any { player.equipment.count(it) > 0 }

    fun wearsChefsHat(player: Player): Boolean = CHEFS_HATS.any { player.equipment.count(it) > 0 }

    /** Whether [player] may go in by the front door. */
    fun mayEnter(player: Player): Boolean = player.skills.baseLevel(Skill.COOKING) >= ENTRY_LEVEL && wearsEntry(player)

    /** Whether [player] stands inside the guild, north of the front door's tile. */
    fun inside(player: Player, door: ZoneLoc): Boolean = player.z > door.z

    /** The Head chef's refusal at the front door, or null for a player he lets in. */
    fun refusal(player: Player) = when {
        player.skills.baseLevel(Skill.COOKING) < ENTRY_LEVEL ->
            if (wearsChefsHat(player)) HeadChefLines.DOOR_LEVEL_HAT else HeadChefLines.DOOR_LEVEL
        !wearsEntry(player) -> HeadChefLines.DOOR_HAT
        else -> null
    }

    fun ownsCape(player: Player): Boolean = listOf(COOKING_CAPE, COOKING_CAPE_TRIMMED).any {
        player.inventory.count(it) > 0 || player.equipment.count(it) > 0 || player.bank.count(it) > 0
    }

    /** The Head chef's Talk-to. Expressions are guessed. */
    internal suspend fun Dialogue.headChef(give: (Player, Int) -> Unit, takeCoins: (Player, Int) -> Unit) = with(HeadChefLines) {
        if (player.skills.baseLevel(Skill.COOKING) < MAX_LEVEL) {
            chatNpc(ChatAnim.HAPPY, WELCOME)
            if (choice(NICE_CAPE, THANKS_BYE) == 1) {
                chatPlayer(ChatAnim.HAPPY, NICE_CAPE_SAID)
                chatNpc(ChatAnim.HAPPY, CAPE_INFO)
                chatNpc(ChatAnim.HAPPY, CAPE_INFO_2)
            } else {
                chatPlayer(ChatAnim.NEUTRAL, THANKS_BYE)
            }
            return@with
        }
        chatNpc(ChatAnim.HAPPY, MASTER)
        if (choice(NO_THANKS, YES_PLEASE) == 1) {
            chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
            chatNpc(ChatAnim.NEUTRAL, CHANGE_MIND)
            return@with
        }
        if (ownsCape(player) && choice(SKILLCAPE, HOOD) == 2) {
            chatPlayer(ChatAnim.QUIZ, HOOD_ASK)
            give(player, COOKING_HOOD)
            chatNpc(ChatAnim.HAPPY, HOOD_GIVEN)
            mesbox(text(HOOD_HANDED))
            return@with
        }
        buyCape(give, takeCoins)
    }

    private suspend fun Dialogue.buyCape(give: (Player, Int) -> Unit, takeCoins: (Player, Int) -> Unit) = with(HeadChefLines) {
        chatPlayer(ChatAnim.QUIZ, BUY)
        chatNpc(ChatAnim.NEUTRAL, PRICE)
        if (choice(TOO_EXPENSIVE, SURE) == 1) {
            chatPlayer(ChatAnim.SAD, TOO_EXPENSIVE)
            chatNpc(ChatAnim.SAD, SHAME)
            return@with
        }
        chatPlayer(ChatAnim.HAPPY, SURE)
        val coins = player.inventory.count(COINS)
        when {
            coins < CAPE_PRICE -> {
                chatPlayer(ChatAnim.SAD, NO_MONEY)
                chatNpc(ChatAnim.NEUTRAL, COME_BACK)
            }
            // The coins' slot frees up when they are all handed over.
            player.inventory.freeSlots + (if (coins == CAPE_PRICE.toLong()) 1 else 0) < 2 -> chatNpc(ChatAnim.NEUTRAL, NO_SPACE)
            else -> {
                takeCoins(player, CAPE_PRICE)
                val trimmed = Skill.entries.any { it != Skill.COOKING && player.skills.baseLevel(it) >= MAX_LEVEL }
                give(player, if (trimmed) COOKING_CAPE_TRIMMED else COOKING_CAPE)
                give(player, COOKING_HOOD)
                chatNpc(ChatAnim.HAPPY, SOLD)
            }
        }
    }
}

/**
 * The front door: the player is let through, in (when the Head chef allows it) or out, onto the tile on the other side.
 * Guessed: the door isn't swung open, so no one else can slip through behind them while it stands open, as the guild's
 * check would then be skipped (the same walk-through as the north-east room's door).
 */
private suspend fun ScriptContext.frontDoor(world: World, door: ZoneLoc) = with(CooksGuild) {
    arriveDelay()
    if (!inside(player, door)) {
        val refusal = refusal(player)
        if (refusal != null) {
            // The Head chef says it; with him gone from the guild, the line shows as a message.
            val chef = world.npcs.inPlay.firstOrNull { it.spawn.type.id == HEAD_CHEF && !it.outOfPlay }
            if (chef != null) world.dialogues.start(this@frontDoor, chef) { chatNpc(ChatAnim.NEUTRAL, refusal) } else world.message(player, refusal)
            return@with
        }
    }
    val to = DraynorManor.otherSide(door, player.x, player.z)
    world.teleport(player, to.x, to.z, door.level)
}

/**
 * The north room's door, on the west edge of its tile: through for 99 Cooking, either way; a message for the rest. The
 * room behind it, to the east, holds the bank and a range.
 */
private suspend fun ScriptContext.northDoor(world: World, door: ZoneLoc) {
    arriveDelay()
    val inside = player.x >= door.x
    if (!inside && player.skills.baseLevel(Skill.COOKING) < CooksGuild.NORTH_LEVEL) {
        world.message(player, CooksGuildLines.BANK_DOOR)
        return
    }
    // Guessed: walked through with no open type, as the cache's other type for it (_inactive) isn't an open door.
    val to = DraynorManor.otherSide(door, player.x, player.z)
    world.teleport(player, to.x, to.z, door.level)
}

fun registerCooksGuild(world: World) {
    with(CooksGuild) {
        val give: (Player, Int) -> Unit = { player, obj ->
            world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
        }
        val takeCoins: (Player, Int) -> Unit = { player, count ->
            var left = count
            for ((slot, held) in player.inventory.contents()) {
                if (left == 0) break
                if (held.id == COINS) left -= player.inventory.remove(slot, left)
            }
        }
        world.handlers.onNpc(HEAD_CHEF, TALK_TO) { op -> world.dialogues.start(this, op.npc) { headChef(give, takeCoins) } }
        // The doors by tile too: the same types anywhere else are plain doors.
        world.handlers.onLoc(FRONT_DOOR, OPEN) { op ->
            val door = op.loc!!
            if (door.x to door.z == FRONT_DOOR_TILE) frontDoor(world, door) else world.doors.toggle(door, OPEN, player.saveKey)
        }
        world.handlers.onLoc(NORTH_DOOR, OPEN) { op ->
            val door = op.loc!!
            if (door.x to door.z == NORTH_DOOR_TILE) northDoor(world, door) else world.doors.toggle(door, OPEN, player.saveKey)
        }

        // The stairs by tile, as the exploit review asks of types that may stand elsewhere.
        fun ScriptContext.ours(loc: ZoneLoc): Boolean {
            if (loc.x to loc.z == STAIRS_TILE) return true
            world.message(player, GameLines.NOTHING_INTERESTING)
            return false
        }
        world.handlers.onLoc(STAIRS_BOTTOM, CLIMB) { op -> if (ours(op.loc!!)) climbStairs(world, op.loc!!, 1) }
        world.handlers.onLoc(STAIRS_MIDDLE, CLIMB) { op -> if (ours(op.loc!!)) climbStairsMenu(world, op.loc!!) }
        world.handlers.onLoc(STAIRS_MIDDLE, CLIMB_UP) { op -> if (ours(op.loc!!)) climbStairs(world, op.loc!!, 1) }
        world.handlers.onLoc(STAIRS_MIDDLE, CLIMB_DOWN) { op -> if (ours(op.loc!!)) climbStairs(world, op.loc!!, -1) }
        world.handlers.onLoc(STAIRS_TOP, CLIMB) { op -> if (ours(op.loc!!)) climbStairs(world, op.loc!!, -1) }

        // As Mill Lane Mill's: the flour is the player's own, shared between windmills.
        world.handlers.onLoc(HOPPER, MillLaneMill.FILL) { fillHopper(world) }
        world.handlers.onObjOnLoc(HOPPER, MillLaneMill.GRAIN) { fillHopper(world) }
        world.handlers.onLoc(CONTROLS, MillLaneMill.OPERATE) { operate(world) }
        world.handlers.onLoc(FLOUR_BIN, MillLaneMill.EMPTY) { emptyBin(world) }
        world.handlers.onObjOnLoc(FLOUR_BIN, MillLaneMill.POT) { emptyBin(world) }
    }
}
