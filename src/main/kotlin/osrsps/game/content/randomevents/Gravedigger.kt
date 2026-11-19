package osrsps.game.content.randomevents

import net.rsprot.protocol.game.outgoing.interfaces.IfSetObject
import osrsps.game.FrameSlot
import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.content.XpLamp
import osrsps.game.content.message
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/** Leo's lines; the words are in the dialogue file, from his transcript (read 2026-11-19). */
object GravediggerLines : Lines("gravedigger", "Transcript:Leo") {
    val HELLO = line("hello", "Overhead dialogue: dialogue 1", "name")
    val PLEASE_HELP = line("please_help", "Overhead dialogue: dialogue 2", "name")
    val NEED_HELP = line("need_help", "Overhead dialogue: dialogue 3", "name")
    val BORROW = line("borrow", "Overhead dialogue: dialogue 4", "name")
    val MAYBE_LATER = line("maybe_later", "Overhead dialogue: if the player does not respond in time")
    val HELP = line("help", "Standard dialogue, Gielinor: his question", "name")
    val OKAY = line("okay", "Standard dialogue, Gielinor: the first option, and the player's words")
    val BUSY = line("busy", "Standard dialogue, Gielinor: the second option, and the player's words")
    val WRONG_COFFINS = line("wrong_coffins", "Standard dialogue, Graveyard: first line", "name")
    val PICK_UP = line("pick_up", "Standard dialogue, Graveyard: second line (also after Remind me)")
    val FINISHED = line("finished", "When talked to again: the first option, and the player's words")
    val GOOD_ATTEMPT = line("good_attempt", "I think I've finished: not every coffin right")
    val ANOTHER_SHOT = line("another_shot", "I think I've finished: not every coffin right, the player's answer")
    val WONDERFUL = line("wonderful", "I think I've finished: every coffin right")
    val REMIND = line("remind", "When talked to again: the second option, and the player's words")
    val INSTRUCTIONS = line("instructions", "Remind me: the box after his words, its six lines as one text")
    val LEAVE = line("leave", "When talked to again: the third option, and the player's words")
    val TAKE_YOU_BACK = line("take_you_back", "I want to leave now: his answer")
    val SOMEONE_ELSE = line("someone_else", "Other player's Leo", "name")
}

/**
 * The Gravedigger (OSRS Wiki, Gravedigger, Leo, Coffin, Grave and Gravestone pages and Leo's transcript, read
 * 2026-11-19). Leo comes as NPC 6746 (macro_gravedigger_invitation; Talk-to, Dismiss) and asks for help with some
 * graves; yes takes the player to his graveyard (map square 30,78, [EventAreas]), where he (6745) has put five coffins
 * in the wrong graves. Take-Coffin on a grave (macro_digger_open_grave_coffin_1 to _5) takes its coffin
 * (macro_digger_coffin_object_1 to _5) and leaves it empty (macro_digger_open_grave_empty_1 to _5); Check on a coffin
 * shows what's inside (interface gravedigger_macro2); Read on a gravestone shows whose grave it is (interface
 * gravedigger_gravestone); a coffin used on an empty grave goes in. With all five right, saying so (FINISHED) sends
 * the player back with two zombie outfit pieces not yet had, or with the whole outfit a zombie emote not yet had
 * (varbits emote_zombie_dance, emote_zombie_walk), or with both emotes too a lamp. Leaving (LEAVE) leaves with
 * nothing. The mausoleum is a bank deposit box.
 *
 * The five professions and their gravestone pictures are the cache's macro_digger_stone* objs (axe, cook, mine,
 * spade, vase); the coffins' contents are its macro_digger_* item models. Guessed: which coffin obj holds which body
 * (in the stone objs' order: lumberjack, cook, miner, farmer, potter), which items lie in whose coffin and in which
 * slots; the gravestones shuffled at random and the coffins put in so that none starts right; where the player lands
 * and Leo stands; which zombie pieces come (two of the missing at random) and which emote; that a coffin left in the
 * backpack goes as the player leaves. Left out: the death in the graveyard, the silly-burial message and axe-on-tree
 * messages, and taking coffins out of a logged-out player's backpack.
 */
object Gravedigger : RandomEvent("gravedigger", 6746) {
    const val GRAVEYARD_LEO = 6745
    const val TAKE_COFFIN = 1
    const val CHECK = 1
    const val READ = 1
    const val DEPOSIT = 1
    const val MAUSOLEUM = 10055

    /** One body: its gravestone picture and what lies in its coffin with the bones. */
    enum class Profession(val stone: Int, val items: List<Int>) {
        LUMBERJACK(7614, listOf(7603, 7612)),
        COOK(7615, listOf(7601, 7597, 7600, 7604, 7605)),
        MINER(7616, listOf(7607, 7606)),
        FARMER(7617, listOf(7602, 7610, 7609)),
        POTTER(7618, listOf(7613, 7599, 7608)),
        ;

        /** Its coffin, macro_digger_coffin_object_1 to _5. */
        val coffin: Int get() = FIRST_COFFIN + ordinal
    }

    const val FIRST_COFFIN = 7587
    val COFFINS = Profession.entries.map { it.coffin }
    const val BONES = 7598
    const val SKULL = 7611

    /** Each grave's locs, grave 1 first: with a coffin, and empty. */
    val FULL_GRAVES = listOf(9364, 9365, 9366, 9367, 10049)
    val EMPTY_GRAVES = listOf(10050, 10051, 10052, 10053, 10054)
    val GRAVESTONES = listOf(9359, 9360, 9361, 9362, 9363)

    /** Each grave's tile (its south-west corner) and its gravestone's, grave 1 first. */
    val GRAVE_TILES = listOf(Tile(1924, 4996), Tile(1926, 4999), Tile(1928, 4996), Tile(1930, 4999), Tile(1932, 4996))
    val GRAVESTONE_TILES = GRAVE_TILES.map { Tile(it.x, it.z + 2) }

    const val COFFIN_INTERFACE = 141
    const val COFFIN_FIRST_SLOT = 3
    const val COFFIN_SLOTS = 9
    const val GRAVESTONE_INTERFACE = 175
    const val GRAVESTONE_PICTURE = 1
    const val COFFIN_ZOOM = 400
    const val GRAVESTONE_ZOOM = 300

    const val SHIRT = 7592
    const val TROUSERS = 7593
    const val MASK = 7594
    const val GLOVES = 7595
    const val BOOTS = 7596
    val OUTFIT = listOf(SHIRT, TROUSERS, MASK, GLOVES, BOOTS)
    const val PIECES_PER_REWARD = 2

    /** emote_zombie_dance and emote_zombie_walk. */
    val EMOTE_VARBITS = listOf(1920, 1921)

    /** The zones copied: the graveyard of map square 30,78. */
    const val FROM_X = 1920
    const val FROM_Z = 4992
    const val WIDTH_ZONES = 2
    const val HEIGHT_ZONES = 2

    val ARRIVE = Tile(1928, 4994)
    val LEO_AT = Tile(1926, 4994)

    /** A stay in the graveyard: whose each grave is, and whose coffin is in it (null while taken). */
    class Graveyard(val leo: Npc?, val owners: List<Profession>, val coffins: Array<Profession?>) {
        fun solved() = owners.indices.all { coffins[it] == owners[it] }
    }

    override val teleports = true
    override val nags = with(GravediggerLines) { listOf(HELLO, PLEASE_HELP, NEED_HELP, BORROW) }
    override val leaving = GravediggerLines.MAYBE_LATER
    override val notYours = GravediggerLines.SOMEONE_ELSE

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean = with(GravediggerLines) {
        chatNpc(ChatAnim.SAD, HELP, "name" to player.username)
        if (choice(OKAY, BUSY) != 1) {
            chatPlayer(ChatAnim.NEUTRAL, BUSY)
            return true
        }
        chatPlayer(ChatAnim.HAPPY, OKAY)
        val away = events.areas.takeAway(player, this@Gravedigger, FROM_X, FROM_Z, WIDTH_ZONES, HEIGHT_ZONES, ARRIVE) ?: return true
        val owners = shuffled(world, Profession.entries)
        var coffins = shuffled(world, owners)
        // Every coffin starts in a wrong grave.
        while (coffins.indices.any { coffins[it] == owners[it] }) coffins = shuffled(world, owners)
        val yard = Graveyard(spawnLeo(world, away), owners, coffins.toTypedArray<Profession?>())
        away.state = yard
        delay(2)
        val leo = yard.leo ?: return true
        chatNpc(leo, ChatAnim.SAD, text(WRONG_COFFINS, "name" to player.username))
        chatNpc(leo, ChatAnim.NEUTRAL, text(PICK_UP))
        true
    }

    private fun <T> shuffled(world: World, from: List<T>): List<T> {
        val list = from.toMutableList()
        for (i in list.size - 1 downTo 1) {
            val j = world.random.nextInt(i + 1)
            list[i] = list[j].also { list[j] = list[i] }
        }
        return list
    }

    private fun spawnLeo(world: World, away: EventAreas.Away): Npc? {
        val type = world.npcTypes[GRAVEYARD_LEO] ?: return null
        val tile = away.inside(LEO_AT.x, LEO_AT.z)
        return world.spawnNpc(NpcSpawn(type, tile.x, tile.z, 0, source = "graveyard for ${away.owner}", respawns = false))
    }

    /** The stay [player] is in and its graveyard, if they are in one. */
    fun yardOf(world: World, player: Player): Pair<EventAreas.Away, Graveyard>? {
        val away = world.randomEvents.areas.awayOf(player) ?: return null
        val yard = away.state as? Graveyard ?: return null
        return away to yard
    }

    /** Which grave (0 to 4) [loc] is, from its tile on the real map. */
    private fun graveOf(away: EventAreas.Away, loc: ZoneLoc, tiles: List<Tile>): Int = tiles.indexOf(away.outside(loc.x, loc.z))

    /** Take-Coffin: the coffin into the backpack, the grave left empty. */
    fun takeCoffin(world: World, player: Player, loc: ZoneLoc) {
        val (away, yard) = yardOf(world, player) ?: return
        val grave = graveOf(away, loc, GRAVE_TILES).takeIf { it >= 0 } ?: return
        val body = yard.coffins[grave] ?: return
        val type = world.objTypes[body.coffin] ?: return
        if (player.inventory.add(type, 1) == 0) return world.message(player, GameLines.NO_SPACE)
        yard.coffins[grave] = null
        world.locs.replace(loc, EMPTY_GRAVES[grave])
    }

    /** A coffin used on an empty grave: in it goes. */
    fun bury(world: World, player: Player, loc: ZoneLoc, slot: Int, obj: Int) {
        val (away, yard) = yardOf(world, player) ?: return
        val grave = graveOf(away, loc, GRAVE_TILES).takeIf { it >= 0 } ?: return
        val body = Profession.entries.firstOrNull { it.coffin == obj } ?: return
        if (yard.coffins[grave] != null || player.inventory[slot]?.id != obj) return
        player.inventory.remove(slot, 1)
        yard.coffins[grave] = body
        world.locs.replace(loc, FULL_GRAVES[grave])
    }

    /** Check on a coffin: the bones and belongings inside. */
    fun check(world: World, player: Player, obj: Int) {
        val body = Profession.entries.firstOrNull { it.coffin == obj } ?: return
        val inside = listOf(SKULL) + body.items + BONES
        for (i in 0 until COFFIN_SLOTS) {
            player.session.queue(IfSetObject(COFFIN_INTERFACE, COFFIN_FIRST_SLOT + i, inside.getOrElse(i) { -1 }, COFFIN_ZOOM))
        }
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, COFFIN_INTERFACE)
    }

    /** Read on a gravestone: whose grave it is. */
    fun read(world: World, player: Player, loc: ZoneLoc) {
        val (away, yard) = yardOf(world, player) ?: return
        val grave = graveOf(away, loc, GRAVESTONE_TILES).takeIf { it >= 0 } ?: return
        player.session.queue(IfSetObject(GRAVESTONE_INTERFACE, GRAVESTONE_PICTURE, yard.owners[grave].stone, GRAVESTONE_ZOOM))
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, GRAVESTONE_INTERFACE)
    }

    /** Talk-to on Leo in the graveyard. */
    suspend fun ScriptContext.talkInYard(world: World, npc: Npc) {
        val (away, yard) = yardOf(world, player) ?: return
        world.dialogues.start(this, npc) {
            with(GravediggerLines) {
                when (choice(FINISHED, REMIND, LEAVE)) {
                    1 -> {
                        chatPlayer(ChatAnim.NEUTRAL, FINISHED)
                        if (!yard.solved()) {
                            chatNpc(ChatAnim.NEUTRAL, GOOD_ATTEMPT)
                            chatPlayer(ChatAnim.NEUTRAL, ANOTHER_SHOT)
                            return@start
                        }
                        // Back first, then the reward (what doesn't fit goes on the ground there, not in the yard
                        // that is about to end), then his line, so shutting it neither loses nor repeats the reward.
                        // Guessed: his line comes after the teleport.
                        leave(world, player, away)
                        reward(world, player)
                        chatNpc(ChatAnim.HAPPY, WONDERFUL)
                    }
                    2 -> {
                        chatPlayer(ChatAnim.NEUTRAL, REMIND)
                        chatNpc(ChatAnim.NEUTRAL, PICK_UP)
                        mesbox(INSTRUCTIONS)
                    }
                    else -> {
                        chatPlayer(ChatAnim.NEUTRAL, LEAVE)
                        chatNpc(ChatAnim.NEUTRAL, TAKE_YOU_BACK)
                        leave(world, player, away)
                    }
                }
            }
        }
    }

    private fun reward(world: World, player: Player) {
        val missing = shuffled(world, OUTFIT.filter { !owns(player, it) })
        if (missing.isNotEmpty()) {
            for (piece in missing.take(PIECES_PER_REWARD)) world.randomEvents.reward(player, piece)
            return
        }
        val emotes = EMOTE_VARBITS.filter { player.vars.types.varbit(it) != null && player.vars.getVarbit(it) == 0 }
        if (emotes.isNotEmpty()) {
            player.vars.setVarbit(emotes[world.random.nextInt(emotes.size)], 1)
            return
        }
        world.randomEvents.reward(player, XpLamp.LAMP)
    }

    private fun leave(world: World, player: Player, away: EventAreas.Away) {
        for (slot in 0 until player.inventory.capacity) if (player.inventory[slot]?.id in COFFINS) player.inventory.remove(slot)
        world.randomEvents.areas.bringBack(player, away)
    }

    private fun owns(player: Player, id: Int) = player.inventory.count(id) > 0 || player.equipment.count(id) > 0 || player.bank.count(id) > 0

    /** The graveyard's locs whose handlers work in its copies. */
    val INSTANCE_LOCS: Set<Int> = (FULL_GRAVES + EMPTY_GRAVES + GRAVESTONES + MAUSOLEUM).toSet()

    fun register(world: World) {
        world.handlers.onNpc(GRAVEYARD_LEO, RandomEvents.TALK_TO) { op -> talkInYard(world, op.npc ?: return@onNpc) }
        for (id in FULL_GRAVES) world.handlers.onLoc(id, TAKE_COFFIN) { op -> takeCoffin(world, player, op.loc ?: return@onLoc) }
        for (id in EMPTY_GRAVES) for (coffin in COFFINS) {
            world.handlers.onObjOnLoc(id, coffin) { op -> bury(world, player, op.loc ?: return@onObjOnLoc, op.sub, op.obj) }
        }
        for (id in GRAVESTONES) world.handlers.onLoc(id, READ) { op -> read(world, player, op.loc ?: return@onLoc) }
        for (coffin in COFFINS) world.handlers.onObj(coffin, CHECK) { op -> check(world, player, op.id) }
        world.handlers.onLoc(MAUSOLEUM, DEPOSIT) { world.depositBox.open(player) }
        world.handlers.onObjOnLoc(MAUSOLEUM) { op -> world.depositBox.useItem(player, op.sub, op.obj) }
    }
}
