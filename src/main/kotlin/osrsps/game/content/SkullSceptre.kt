package osrsps.game.content

import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.WearPos
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.ScriptContext

/** The skull sceptre's messages (keys only; the words are in data/dialogue.txt). */
object SkullSceptreLines : Lines("skull_sceptre", "Skull sceptre") {
    val JOIN_SKULL = line("join_skull", "The left and right skull halves used together: the strange skull made")
    val JOIN_SCEPTRE = line("join_sceptre", "The top and bottom of the sceptre used together: the runed sceptre made")
    val JOIN_WHOLE = line("join_whole", "The strange skull and the runed sceptre used together: the skull sceptre made")
    val DIVINE = line("divine", "Divine on the skull sceptre: the charges left", "charges")
    val NO_CHARGES = line("no_charges", "Invoke on an imbued skull sceptre with no charges left")
    val CRUMBLES = line("crumbles", "Invoke used the last charge of a skull sceptre that isn't imbued: it crumbles")
    val CHARGED = line("charged", "Bone fragments or a sceptre piece used on the imbued skull sceptre: the charges it holds now", "charges")
    val FULL = line("full", "Bone fragments or a sceptre piece used on the imbued skull sceptre while it holds all it can")
    val NOT_IMBUED = line("not_imbued", "Bone fragments or a sceptre piece used on a skull sceptre that isn't imbued")
    val CHISELLED = line("chiselled", "A chisel used on a sceptre piece: the bone fragments it breaks into", "fragments")
}

/**
 * Solztun's lines, as the OSRS Wiki's transcript has them (keys only). Every account counts as having two-factor
 * authentication (the plan's decision), so the transcript's branches for an account without it are left out.
 */
object SolztunLines : Lines("solztun", "Transcript:Solztun") {
    val CAN_YOU_HEAR = line("can_you_hear", "Solztun's first line: can the player hear him")
    val HEAR_GHOSTSPEAK = line("hear_ghostspeak", "The player's answer, wearing an amulet of ghostspeak")
    val HEAR_NO_AMULET = line("hear_no_amulet", "The player's answer, with no amulet: are they dead")
    val SPIRIT = line("spirit", "Solztun: he is a spirit, putting his thoughts in their head")
    val STRANGE = line("strange", "The player: it is strange")
    val DONT_DWELL = line("dont_dwell", "Solztun: don't dwell on it")
    val IMBUE = line("imbue", "The option and the player's line: could he imbue their sceptre")
    val WHO = line("who", "The option and the player's line: who is he")
    val WHY = line("why", "The option and the player's line: why is he here")
    val BYE = line("bye", "The option and the player's line: bye")
    val FAREWELL = line("farewell", "Solztun's goodbye, after Bye and after the imbue")
    val IMBUE_NEED = line("imbue_need", "Solztun: he needs to see the treasure and know their account is safe")
    val SAFE = line("safe", "The option and the player's line: is their account safe")
    val SAFE_YES = line("safe_yes", "Solztun: the authenticator is set up; show him the treasure")
    val TREASURE = line("treasure", "The option and the player's line: is this the treasure")
    val SHOW_BOOTS = line("show_boots", "The box as the player shows him the boots", "boots")
    val DELIGHTED = line("delighted", "Solztun, delighted with the boots, with no body to wear them")
    val IMBUE_AGAIN = line("imbue_again", "The player asks again for the imbue")
    val IMBUE_DONE = line("imbue_done", "Solztun agrees and imbues every skull sceptre they carry")
    val WONT_BREAK = line("wont_break", "Solztun: the sceptres won't break now, and take any piece")
    val HOW_MANY = line("how_many", "The player: how many charges each piece gives")
    val CHARGES = line("charges", "Solztun: the charges each piece gives, and the diary tiers")
    val CHISEL = line("chisel", "Solztun: a chisel breaks pieces into fragments, which charge it too")
    val WARN = line("warn", "Solztun: turning off the authenticator takes the imbue away")
    val KEEP_SAFE = line("keep_safe", "The player: they'll keep it on, goodbye")
    val WHO_INTRO = line("who_intro", "Who are you, the first time: Solztun, a barbarian explorer")
    val WHO_NAME = line("who_name", "The player gives their name", "name")
    val WHO_KNOWS = line("who_knows", "Solztun: he knows their name already")
    val WHO_EXPLORING = line("who_exploring", "The player: was he exploring this place")
    val WHO_SISTER = line("who_sister", "Solztun: his sister warned him, and was right")
    val WHO_LITARA = line("who_litara", "The player: is his sister Litara, and how was she right")
    val WHO_DIED = line("who_died", "Solztun: arrows near the exit")
    val WHO_AGAIN = line("who_again", "Who are you, after the first time: who he is, and that he died before the treasure")
    val WHO_HELP = line("who_help", "The player: did nobody come to help")
    val WHO_ASSUMED = line("who_assumed", "Solztun: they took it he'd found the treasure")
    val WHO_LIFESTYLE = line("who_lifestyle", "The player: did he not like the barbarian life")
    val WHO_EXPLORE = line("who_explore", "Solztun: exploring over fighting")
    val WHO_AGREE = line("who_agree", "The player: they like exploring too")
    val WHY_TREASURE = line("why_treasure", "Solztun: he came for the treasure")
    val WHY_CRADLE = line("why_cradle", "The player: is it in the cradle")
    val WHY_DEAD = line("why_dead", "Solztun: yes, but he is dead")
    val WHY_PRICE = line("why_price", "The player offers to show it for a price")
    val WHY_OFFER = line("why_offer", "Solztun: he has little, but can make a skull sceptre stronger")
    val WHY_HOW = line("why_how", "The player: how")
    val WHY_BREAKS = line("why_breaks", "Solztun: a sceptre breaks when empty; imbued it takes any piece")
    val WHY_DEAL = line("why_deal", "The player: a deal")
    val NOT_TREASURE = line("not_treasure", "A skull sceptre used on him with no boots in the pack or bank: that isn't the treasure")
    val NOTHING_SHOWN = line("nothing_shown", "The player: they didn't show him anything")
    val SENSES = line("senses", "Solztun: he senses they don't have it")
}

/**
 * The skull sceptre (Edgeville piece step 14b), from the OSRS Wiki's Skull sceptre, Solztun and Transcript:Solztun pages
 * (read 2026-11-03) and the 241 cache.
 *
 * - The pieces join: the left skull half (9008, the ankou's) on the right (9007, the minotaurs') makes the strange
 *   skull (9009); the top of the sceptre (9010, the catablepons') on the bottom (9011, the flesh crawlers') the runed
 *   sceptre (9012); those two together the skull sceptre (9013), with [MAX_CHARGES] charges. Each join takes a tick
 *   and nothing else (the Wiki); once Solztun has imbued one, the whole sceptre is made imbued.
 * - Invoke takes the player to the Stronghold's entrance in Barbarian Village ([Church.STRONGHOLD]) for a charge, up
 *   to level 20 Wilderness. A sceptre that isn't imbued crumbles with its last charge (the Wiki: it "will
 *   self-destruct"); an imbued one stays, empty. Divine says the charges left. Guessed: the standard spellbook's
 *   teleport animation and graphic, and its 3 ticks.
 * - The charges belong to the player ([osrsps.game.Charges]), as the blowpipe's do: two sceptres share them, and when
 *   one that isn't imbued crumbles the next starts with [MAX_CHARGES] (the Wiki).
 * - Solztun (7673), in the Sepulchre of Death's reward room, imbues every skull sceptre the player carries into the
 *   skull sceptre (i) (21276) once he has seen a pair of the Cradle of Life's boots. The imbued sceptre is charged with
 *   bone fragments (25139, one charge each) or a sceptre piece (3 charges, the left skull half 5); a chisel breaks a
 *   piece into that many fragments, a strange skull into 8, a runed sceptre 6 and a whole sceptre that isn't imbued
 *   14 (the Wiki's Bone fragments). The Varrock Diary's extra charges wait for the diary.
 * - Solztun's "Who are you?" tells his story the first time and a shorter one after (varbit `sos_brother_found`,
 *   which Litara will read); `sos_sceptre_imbued` is set by his imbue.
 */
object SkullSceptre {
    const val RIGHT_SKULL_HALF = 9007
    const val LEFT_SKULL_HALF = 9008
    const val STRANGE_SKULL = 9009
    const val TOP_OF_SCEPTRE = 9010
    const val BOTTOM_OF_SCEPTRE = 9011
    const val RUNED_SCEPTRE = 9012
    const val SCEPTRE = 9013
    const val IMBUED = 21276
    const val BONE_FRAGMENTS = 25139
    const val CHISEL = 1755

    /** A new sceptre's charges, and the most an imbued one holds: 10 without the Varrock Diary (the Wiki). */
    const val MAX_CHARGES = 10

    /** The player's sceptre charges, in [osrsps.game.Charges]. */
    const val CHARGES = "skull_sceptre"

    /** Each piece's charges and bone fragments (the Wiki, without the diary). */
    val PIECE_CHARGES = mapOf(LEFT_SKULL_HALF to 5, RIGHT_SKULL_HALF to 3, TOP_OF_SCEPTRE to 3, BOTTOM_OF_SCEPTRE to 3)

    /** What a chisel makes of each: the pieces, the joined halves and a whole sceptre that isn't imbued (the Wiki's Bone fragments page, without the diary). */
    val CHISEL_FRAGMENTS = PIECE_CHARGES + mapOf(STRANGE_SKULL to 8, RUNED_SCEPTRE to 6, SCEPTRE to 14)

    /** Inventory ops 3 and 4 (the 241 cache's iops: Wield, Invoke, Divine). */
    const val INVOKE = 3
    const val DIVINE = 4

    /** human_castteleport and teleport_casting, the standard spellbook's (guessed for the sceptre). */
    const val TELEPORT_ANIM = 714
    const val TELEPORT_SPOTANIM = 111
    const val TELEPORT_SPOTANIM_HEIGHT = 92
    const val TELEPORT_TICKS = 3

    const val SOLZTUN = 7673
    const val TALK_TO = 1

    /** `sos_brother_found`: the player has heard Solztun's story. */
    const val VARBIT_BROTHER_FOUND = 5639

    /** `sos_sceptre_imbued`: Solztun has imbued a sceptre for the player. */
    const val VARBIT_IMBUED = 5638

    /** The joins: two items used together, and what they make. */
    val JOINS = listOf(
        Triple(LEFT_SKULL_HALF, RIGHT_SKULL_HALF, STRANGE_SKULL),
        Triple(TOP_OF_SCEPTRE, BOTTOM_OF_SCEPTRE, RUNED_SCEPTRE),
        Triple(STRANGE_SKULL, RUNED_SCEPTRE, SCEPTRE),
    )

    val BOOTS = listOf(StrongholdOfSecurity.FANCY_BOOTS, StrongholdOfSecurity.FIGHTING_BOOTS)

    fun hasBoots(player: Player) = BOOTS.any { player.inventory.count(it) > 0 || player.equipment.count(it) > 0 }

    fun hasBootsAnywhere(player: Player) = hasBoots(player) || BOOTS.any { player.bank.count(it) > 0 }

    fun carriesSceptre(player: Player) = player.inventory.count(SCEPTRE) > 0

    /** Solztun has imbued a sceptre for [player]: every one they make after starts imbued (the Wiki's Skull sceptre (i)). */
    fun imbueUnlocked(player: Player) = player.vars.types.varbit(VARBIT_IMBUED) != null && player.vars.getVarbit(VARBIT_IMBUED) == 1

    /**
     * Turns every skull sceptre in [player]'s pack into the imbued one; returns how many.
     */
    fun imbue(player: Player): Int {
        var imbued = 0
        for ((slot, obj) in player.inventory.contents()) {
            if (obj.id != SCEPTRE) continue
            player.inventory[slot] = InvObj(IMBUED, 1)
            imbued++
        }
        return imbued
    }

    /** What a charge of [type] adds to an imbued sceptre holding [have]: the room left, at most [per] each of [count]. */
    fun chargesAdded(have: Int, per: Int, count: Int): Int = minOf(count.toLong() * per, (MAX_CHARGES - have).toLong()).toInt().coerceAtLeast(0)
}

fun registerSkullSceptre(world: World) {
    with(SkullSceptre) {
        for ((a, b, made) in JOINS) {
            world.handlers.onObjOnObj(a, b) { op -> join(world, op, a, b, made) }
        }
        for (sceptre in listOf(SCEPTRE, IMBUED)) {
            world.handlers.onObj(sceptre, INVOKE) { op -> invoke(world, sceptre, worn = false) }
            world.handlers.onObj(sceptre, DIVINE) { world.message(player, SkullSceptreLines.DIVINE, "charges" to player.charges[CHARGES]) }
            val type = world.objTypes[sceptre] ?: continue
            wornOption(type, "Invoke")?.let { world.handlers.onWorn(sceptre, it) { invoke(world, sceptre, worn = true) } }
            wornOption(type, "Divine")?.let {
                world.handlers.onWorn(sceptre, it) { world.message(player, SkullSceptreLines.DIVINE, "charges" to player.charges[CHARGES]) }
            }
        }
        for ((piece, per) in PIECE_CHARGES + (BONE_FRAGMENTS to 1)) {
            world.handlers.onObjOnObj(piece, IMBUED) { op -> charge(world, op, piece, per) }
            world.handlers.onObjOnObj(piece, SCEPTRE) { world.message(player, SkullSceptreLines.NOT_IMBUED) }
        }
        for ((piece, fragments) in CHISEL_FRAGMENTS) {
            world.handlers.onObjOnObj(CHISEL, piece) { op ->
                val slot = if (op.obj == piece) op.objSlot else op.sub
                if (player.inventory[slot]?.id != piece) return@onObjOnObj
                val type = world.objTypes[BONE_FRAGMENTS] ?: return@onObjOnObj
                player.inventory.remove(slot, 1)
                world.groundObjs.give(player, type, fragments)
                world.message(player, SkullSceptreLines.CHISELLED, "fragments" to fragments)
            }
        }
        world.handlers.onNpc(SOLZTUN, TALK_TO) { op -> world.dialogues.start(this, op.npc) { solztun(world, sceptreUsed = false) } }
        world.handlers.onObjOnNpc(SOLZTUN, SCEPTRE) { op -> world.dialogues.start(this, op.npc) { solztun(world, sceptreUsed = true) } }
    }
}

private fun wornOption(type: osrsps.game.ObjType, name: String): Int? =
    (1..osrsps.game.EquipmentTab.WORN_OPS).firstOrNull { type.stringParam(osrsps.game.EquipmentTab.PARAM_WORN_OP1 + it - 1).equals(name, ignoreCase = true) }

/**
 * Two pieces used together: both go, the piece they make takes the slot of the one used. The join takes a tick (the
 * Wiki), a p_delay, so a second join sent in the same tick is dropped; the pieces are checked again after it. A whole
 * sceptre starts imbued once Solztun has imbued one.
 */
private suspend fun ScriptContext.join(world: World, op: Op, a: Int, b: Int, made: Int) {
    val slots = listOf(op.sub, op.objSlot)
    if (slots.any { it < 0 }) return
    fun holds() = slots.map { player.inventory[it]?.id }.toSet() == setOf(a, b)
    if (!holds()) return
    delay(1)
    if (!holds()) return
    val result = if (made == SkullSceptre.SCEPTRE && SkullSceptre.imbueUnlocked(player)) SkullSceptre.IMBUED else made
    player.inventory.remove(op.objSlot, 1)
    player.inventory[op.sub] = InvObj(result, 1)
    when (made) {
        SkullSceptre.STRANGE_SKULL -> world.message(player, SkullSceptreLines.JOIN_SKULL)
        SkullSceptre.RUNED_SCEPTRE -> world.message(player, SkullSceptreLines.JOIN_SCEPTRE)
        else -> {
            player.charges[SkullSceptre.CHARGES] = SkullSceptre.MAX_CHARGES
            world.message(player, SkullSceptreLines.JOIN_WHOLE)
        }
    }
}

/** Invoke: the teleport for a charge, the sceptre gone with the last one unless it is imbued. */
private suspend fun ScriptContext.invoke(world: World, sceptre: Int, worn: Boolean) {
    val charges = player.charges[SkullSceptre.CHARGES]
    if (charges <= 0) {
        // Only an imbued sceptre can be empty; one that isn't would have crumbled.
        return world.message(player, SkullSceptreLines.NO_CHARGES)
    }
    if (Wilderness.level(player.x, player.z) > Wilderness.TELEPORT_LIMIT) {
        world.message(player, HomeTeleportLines.WILDERNESS)
        world.message(player, HomeTeleportLines.WILDERNESS_2)
        return
    }
    player.charges[SkullSceptre.CHARGES] = charges - 1
    if (charges == 1 && sceptre == SkullSceptre.SCEPTRE) {
        if (worn) player.equipment.remove(WearPos.WEAPON, 1)
        else player.inventory.contents().firstOrNull { it.second.id == sceptre }?.let { player.inventory.remove(it.first, 1) }
        world.message(player, SkullSceptreLines.CRUMBLES)
        // The next sceptre that isn't imbued starts full when one crumbles (the Wiki); an imbued one keeps its 0.
        val another = player.inventory.count(SkullSceptre.SCEPTRE) > 0 || player.equipment.count(SkullSceptre.SCEPTRE) > 0
        if (another) player.charges[SkullSceptre.CHARGES] = SkullSceptre.MAX_CHARGES
    }
    // The worn Invoke comes without the inventory's closing of modals: close them, or a bank open here would stay
    // usable at the Stronghold (Codex on #476).
    world.interfaces.closeModals(player)
    player.scripts.weakQueue.clear()
    player.waypoints.clear()
    player.anim(SkullSceptre.TELEPORT_ANIM)
    player.spotanim(SkullSceptre.TELEPORT_SPOTANIM, SkullSceptre.TELEPORT_SPOTANIM_HEIGHT)
    delay(SkullSceptre.TELEPORT_TICKS)
    world.teleport(player, Church.STRONGHOLD.x, Church.STRONGHOLD.z, 0)
    player.anim(-1)
}

/** Fragments or a piece used on the imbued sceptre: charges up to [SkullSceptre.MAX_CHARGES], the rest kept. */
private fun ScriptContext.charge(world: World, op: Op, piece: Int, per: Int) {
    val slot = if (op.obj == piece) op.objSlot else op.sub
    val held = player.inventory[slot]?.takeIf { it.id == piece } ?: return
    val have = player.charges[SkullSceptre.CHARGES]
    val added = SkullSceptre.chargesAdded(have, per, held.count)
    // A piece gives all its charges or none: it isn't split. Fragments go one charge each.
    val used = if (per == 1) added else if (added >= per) 1 else 0
    if (used <= 0) return world.message(player, SkullSceptreLines.FULL)
    player.inventory.remove(slot, used)
    player.charges[SkullSceptre.CHARGES] = have + used * per
    world.message(player, SkullSceptreLines.CHARGED, "charges" to player.charges[SkullSceptre.CHARGES])
}

/** Solztun, as the transcript has him; [sceptreUsed] when a skull sceptre was used on him. */
private suspend fun Dialogue.solztun(world: World, sceptreUsed: Boolean) = with(SolztunLines) {
    if (sceptreUsed) {
        chatPlayer(ChatAnim.QUIZ, IMBUE)
        chatNpc(ChatAnim.NEUTRAL, IMBUE_NEED)
        if (!imbueMenu(world, anywhere = true)) return@with
    } else {
        chatNpc(ChatAnim.NEUTRAL, CAN_YOU_HEAR)
        val ghostspeak = player.equipment.count(FatherUrhney.GHOSTSPEAK_AMULET) > 0
        chatPlayer(ChatAnim.QUIZ, if (ghostspeak) HEAR_GHOSTSPEAK else HEAR_NO_AMULET)
        chatNpc(ChatAnim.NEUTRAL, SPIRIT)
        chatPlayer(ChatAnim.NEUTRAL, STRANGE)
        chatNpc(ChatAnim.NEUTRAL, DONT_DWELL)
    }
    mainMenu(world)
}

/** The main menu, back after each answer until Bye or the imbue ends it. */
private suspend fun Dialogue.mainMenu(world: World) = with(SolztunLines) {
    while (true) {
        val canImbue = SkullSceptre.carriesSceptre(player) && SkullSceptre.hasBoots(player)
        val options = listOfNotNull(IMBUE.takeIf { canImbue }, WHO, WHY, BYE)
        when (options[choice(*options.toTypedArray()) - 1]) {
            IMBUE -> {
                chatPlayer(ChatAnim.QUIZ, IMBUE)
                chatNpc(ChatAnim.NEUTRAL, IMBUE_NEED)
                if (!imbueMenu(world, anywhere = false)) return@with
            }
            WHO -> who()
            WHY -> why()
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, BYE)
                chatNpc(ChatAnim.NEUTRAL, FAREWELL)
                return@with
            }
        }
    }
}

/**
 * Is my account safe / Is this the treasure, until the imbue is done (false: the dialogue ends) or the treasure isn't
 * there (true: on to the main menu). [anywhere]: a sceptre used on him finds boots in the bank too.
 */
private suspend fun Dialogue.imbueMenu(world: World, anywhere: Boolean): Boolean {
    while (true) with(SolztunLines) {
        if (choice(SAFE, TREASURE) == 1) {
            chatPlayer(ChatAnim.QUIZ, SAFE)
            chatNpc(ChatAnim.HAPPY, SAFE_YES)
            return@with
        }
        chatPlayer(ChatAnim.QUIZ, TREASURE)
        val boots = SkullSceptre.BOOTS.firstOrNull { player.inventory.count(it) > 0 || player.equipment.count(it) > 0 }
            ?: SkullSceptre.BOOTS.firstOrNull { anywhere && player.bank.count(it) > 0 }
        if (boots == null) {
            chatNpc(ChatAnim.NEUTRAL, NOT_TREASURE)
            chatPlayer(ChatAnim.NEUTRAL, NOTHING_SHOWN)
            chatNpc(ChatAnim.NEUTRAL, SENSES)
            return true
        }
        objbox(boots, text(SHOW_BOOTS, "boots" to (world.objTypes[boots]?.name ?: "")))
        chatNpc(ChatAnim.HAPPY, DELIGHTED)
        chatPlayer(ChatAnim.QUIZ, IMBUE_AGAIN)
        chatNpc(ChatAnim.HAPPY, IMBUE_DONE)
        if (SkullSceptre.imbue(player) > 0 && player.vars.types.varbit(SkullSceptre.VARBIT_IMBUED) != null) {
            player.vars.setVarbit(SkullSceptre.VARBIT_IMBUED, 1)
        }
        chatNpc(ChatAnim.NEUTRAL, WONT_BREAK)
        chatPlayer(ChatAnim.QUIZ, HOW_MANY)
        chatNpc(ChatAnim.NEUTRAL, CHARGES)
        chatNpc(ChatAnim.NEUTRAL, CHISEL)
        chatNpc(ChatAnim.NEUTRAL, WARN)
        chatPlayer(ChatAnim.HAPPY, KEEP_SAFE)
        chatNpc(ChatAnim.NEUTRAL, FAREWELL)
        return false
    }
}

private suspend fun Dialogue.who() = with(SolztunLines) {
    chatPlayer(ChatAnim.QUIZ, WHO)
    val first = player.vars.types.varbit(SkullSceptre.VARBIT_BROTHER_FOUND) == null || player.vars.getVarbit(SkullSceptre.VARBIT_BROTHER_FOUND) == 0
    if (first) {
        chatNpc(ChatAnim.NEUTRAL, WHO_INTRO)
        chatPlayer(ChatAnim.HAPPY, WHO_NAME, "name" to player.username)
        chatNpc(ChatAnim.NEUTRAL, WHO_KNOWS)
        chatPlayer(ChatAnim.QUIZ, WHO_EXPLORING)
        chatNpc(ChatAnim.SAD, WHO_SISTER)
        chatPlayer(ChatAnim.QUIZ, WHO_LITARA)
        chatNpc(ChatAnim.SAD, WHO_DIED)
        if (player.vars.types.varbit(SkullSceptre.VARBIT_BROTHER_FOUND) != null) player.vars.setVarbit(SkullSceptre.VARBIT_BROTHER_FOUND, 1)
    } else {
        chatNpc(ChatAnim.NEUTRAL, WHO_AGAIN)
    }
    chatPlayer(ChatAnim.QUIZ, WHO_HELP)
    chatNpc(ChatAnim.SAD, WHO_ASSUMED)
    chatPlayer(ChatAnim.QUIZ, WHO_LIFESTYLE)
    chatNpc(ChatAnim.NEUTRAL, WHO_EXPLORE)
    chatPlayer(ChatAnim.HAPPY, WHO_AGREE)
}

private suspend fun Dialogue.why() = with(SolztunLines) {
    chatPlayer(ChatAnim.QUIZ, WHY)
    chatNpc(ChatAnim.NEUTRAL, WHY_TREASURE)
    chatPlayer(ChatAnim.QUIZ, WHY_CRADLE)
    chatNpc(ChatAnim.SAD, WHY_DEAD)
    chatPlayer(ChatAnim.NEUTRAL, WHY_PRICE)
    chatNpc(ChatAnim.NEUTRAL, WHY_OFFER)
    chatPlayer(ChatAnim.QUIZ, WHY_HOW)
    chatNpc(ChatAnim.NEUTRAL, WHY_BREAKS)
    chatPlayer(ChatAnim.HAPPY, WHY_DEAL)
}
