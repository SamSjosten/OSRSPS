package osrsps.game.skill.hunter

import kotlin.math.abs
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.message
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.game.skill.SuccessRoll
import osrsps.game.zone.Projectile
import osrsps.map.CollisionMap
import osrsps.map.LineOfSight

/** Matthias's lines, from the Wiki's Transcript:Matthias (read 2026-11-15). */
object MatthiasLines : Lines("matthias", "Transcript:Matthias") {
    val HELLO = line("hello", "standard: the player's greeting")
    val GREETINGS = line("greetings", "standard: his answer")
    val QUESTS = line("quests", "first menu, option 1, which the player then says")
    val STRANGE_NOTION = line("strange_notion", "quests: his first answer")
    val ER_YES = line("er_yes", "quests: the player's reply")
    val NO_QUESTS = line("no_quests", "quests: his last answer")
    val WHAT_PLACE = line("what_place", "first menu, option 2, which the player then says")
    val GOOD_QUESTION = line("good_question", "what this place is: his first answer")
    val FALCONER = line("falconer", "what this place is: his second answer")
    val HAVE_A_GO = line("have_a_go", "second menu, option 2: could the player have a go, which the player then says")
    val TOO_LOW = line("too_low", "having a go below 43 Hunter: his first answer")
    val PROFESSIONALS = line("professionals", "having a go below 43 Hunter: his second answer")
    val TRY_ONE = line("try_one", "having a go: his first answer")
    val NOBLEMEN = line("noblemen", "having a go: his second answer")
    val FEE = line("fee", "having a go: the 500 coin fee")
    val NOT_ENOUGH = line("not_enough", "having a go with under 500 coins: the player's reply")
    val REASONABLE = line("reasonable", "fee menu, option 1, which the player then says")
    val NOT_INTERESTED = line("not_interested", "fee menu, option 2, which the player then says")
    val COME_BACK = line("come_back", "not interested: his answer")
    val HANDS_FREE = line("hands_free", "paying with a weapon or shield worn: his answer")
    val GIVES_GLOVE = line("gives_glove", "the message box on getting the glove and falcon")
    val KEEP_AN_EYE = line("keep_an_eye", "after the glove: his line")
    val NOT_MY_SORT = line("not_my_sort", "second menu, option 3, which the player then says")
    val FAIR_ENOUGH = line("fair_enough", "not my sort of thing: his answer")
    val WHAT_FALCONRY = line("what_falconry", "second menu, option 4, which the player then says")
    val SPORT = line("sport", "what falconry is: his first answer")
    val PUTTING_IT_SIMPLY = line("putting_it_simply", "what falconry is: his second answer")
    val LIKE_A_PET = line("like_a_pet", "what falconry is: the player's question")
    val NOT_EXACTLY = line("not_exactly", "what falconry is: his third answer")
    val ASSOCIATE = line("associate", "what falconry is: his last answer")
    val YOUR_BIRD = line("your_bird", "first menu, option 3: could the player have a go with his bird, which the player then says")
    val HELLO_AGAIN = line("hello_again", "glove worn: the player's greeting")
    val GETTING_ALONG = line("getting_along", "glove worn: his question (the Wiki gives him, her or them; the words follow the male player)")
    val HARDER = line("harder", "glove worn: the player's answer")
    val TALKING_TO_FALCON = line("talking_to_falcon", "glove worn: his answer")
    val KEEP_TRYING = line("keep_trying", "glove menu, option 1, which the player then says")
    val COME_TALK = line("come_talk", "keep trying: the player's next line (the Wiki gives it to the player)")
    val LEAVE_IT = line("leave_it", "glove menu, option 2, which the player then says")
    val GIVE_BACK = line("give_back", "the message box on giving the falcon and glove back")
    val RAN_AWAY = line("ran_away", "falcon lost: the player's greeting")
    val NOT_DOMESTICATED = line("not_domesticated", "falcon lost: his first answer")
    val FETCH = line("fetch", "falcon lost: his question")
    val YES_PLEASE = line("yes_please", "lost menu, option 1, which the player then says")
    val BRINGS_BIRD = line("brings_bird", "the message box on the falcon brought back")
    val MORE_CAREFUL = line("more_careful", "after the falcon is brought back: his line")
    val LEAVE_IT_LOST = line("leave_it_lost", "lost menu, option 2, which the player then says")
    val GIVE_GLOVE_BACK = line("give_glove_back", "the message box on giving the glove back without a falcon")
    val GOT_THE_HANG = line("got_the_hang", "falcon holding a kebbit: his answer")
}

/** A falconry kebbit: its NPC type, level, XP in tenths, catch pair, the falcon on it, and what it gives. */
class FalconKebbit(val npc: Int, val level: Int, val xpTenths: Int, val pair: Pair<Int, Int>, val falcon: Int, val drops: List<Int>)

/**
 * Falconry (Hunter step 7) in the Piscatoris falconry area. Matthias (Talk-to, or Quick-falcon) rents a gyr falcon on the
 * falconer's glove for 500 coins at 43 Hunter with the weapon and shield slots empty; the glove stays on until he takes
 * it back. Catch on a kebbit sends the falcon: it flies, and on reaching the kebbit the catch is rolled at the player's
 * Hunter level. Caught, the kebbit goes and the falcon waits on its tile with Retrieve, which gives the XP, bones and
 * fur (raw meat too for a dashing kebbit) and the falcon back on the glove; missed, the falcon comes back. A falcon left
 * on its prey too long flies off to its perch, and Matthias fetches it. Leaving the area (a teleport) or losing the glove
 * any other way sends the falcon back to him.
 *
 * Sources: the OSRS Wiki's Falconry page (read 2026-11-15) for the levels, XP, rent, the glove's rules and the 19 August
 * 2026 changes (moving while the falcon chases, a full backpack discarding the excess); Falconer's glove (the glove's
 * messages); each kebbit's page for its catch pair (Mod Ash), drops and spawns; the Hunter training page for a kebbit back four
 * seconds after its prey is retrieved (7 ticks, rounded up); Transcript:Matthias for his talk. Ids
 * and ops from the rev 242 cache: huntingbeast_speedy/silent/speedy2 (5531-5533, op1 Catch), hunting_npc_falconer 1340
 * (op1 Talk-to, op3 Quick-falcon), hunting_falcon_onspeedy/onspeedy2/onsilent (1342-1344, op1 Retrieve), falcon_gloves
 * 10023 and falcon_on_gloves 10024 (worn as a weapon over the shield), human_falconer_attack, falcon_travel_anim.
 *
 * Guessed: how long the falcon flies ([FLIGHT_TICKS_PER] tiles a tick, at least one) and comes back, how long it waits on
 * its prey ([WAIT_TICKS]), the kebbits' wander, how far off one can be sent at ([LAUNCH_RANGE], in sight), the area as the fence's bounding box
 * ([inArea]), every message key and chathead, Quick-falcon renting at
 * once (and handing the glove back when it is worn). Left out: investing 500,000 coins, the stile, the hand slot (the
 * cache's glove covers only weapon and shield), bonecrushers and pouches, Hunters' Rumours' tufts.
 */
class Falconry(private val world: World, private val collision: CollisionMap) {
    /** A player's falcon while it is off the glove: flying to [target], waiting on its prey as [onPrey], or coming back. */
    private class Flight(val player: Player, var kebbit: FalconKebbit?, var target: Npc?, var arrives: Long) {
        var onPrey: Npc? = null
        var leaves: Long = 0

        /** The kebbit the falcon caught, hidden until its prey is retrieved or left. */
        var caught: Npc? = null
    }

    private val flights = HashMap<String, Flight>()
    private val respawns = ArrayList<Pair<Npc, Long>>()

    fun register() {
        world.handlers.onNpc(MATTHIAS, TALK_TO) { op -> world.dialogues.start(this, op.npc) { talk() } }
        world.handlers.onNpc(MATTHIAS, QUICK_FALCON) { op -> world.dialogues.start(this, op.npc) { quick() } }
        for (kebbit in KEBBITS) {
            world.handlers.onNpc(kebbit.npc, CATCH) { op -> launch(player, kebbit, op.npc ?: return@onNpc) }
            // The falcon goes from where the player stands, when the kebbit is in sight and in range.
            world.npcInteractions.fromAfar(kebbit.npc, CATCH) { player, npc -> inRange(player, npc).also { if (it) launch(player, kebbit, npc) } }
            world.handlers.onNpc(kebbit.falcon, RETRIEVE) { op -> retrieve(kebbit, op.npc ?: return@onNpc) }
        }
        world.equipment.keptOn = keptOn@{ player, obj ->
            if (obj != GLOVE_FALCON && obj != GLOVE_EMPTY) return@keptOn false
            world.message(player, HunterLines.GLOVE_STAYS)
            true
        }
    }

    /** Call once a tick. */
    fun process() {
        val tick = world.tick
        respawns.removeAll { (npc, at) ->
            if (at > tick) return@removeAll false
            if (npc.hidden) {
                world.npcs.teleport(npc, npc.spawn.x, npc.spawn.z)
                world.show(npc)
            }
            true
        }
        for (player in world.onlinePlayers) {
            if (player.loggedOut) continue
            val key = player.saveKey
            val worn = player.equipment[WearPos.WEAPON]?.id
            val held = player.inventory.contents().any { it.second.id == GLOVE_FALCON || it.second.id == GLOVE_EMPTY }
            val wearing = worn == GLOVE_FALCON || worn == GLOVE_EMPTY
            if (held || (wearing && !inArea(player.x, player.z, player.level))) {
                takeGlove(player)
                world.message(player, HunterLines.FALCON_FLIES_BACK)
            } else if (!wearing) {
                end(key)
            }
        }
        val iterator = flights.values.iterator()
        while (iterator.hasNext()) {
            val flight = iterator.next()
            val player = flight.player
            if (player.loggedOut) {
                // The glove stays on with no falcon: Matthias fetches it when they are back.
                flight.onPrey?.let(world::removeNpc)
                respawn(flight, tick)
                iterator.remove()
                continue
            }
            val onPrey = flight.onPrey
            when {
                onPrey != null && tick >= flight.leaves -> {
                    world.removeNpc(onPrey)
                    respawn(flight, tick)
                    world.message(player, HunterLines.FALCON_LEFT_PREY)
                    iterator.remove()
                }
                onPrey == null && tick >= flight.arrives -> {
                    val target = flight.target
                    val kebbit = flight.kebbit
                    if (target == null || kebbit == null) {
                        // Back on the glove after a miss.
                        if (player.equipment[WearPos.WEAPON]?.id == GLOVE_EMPTY) player.equipment[WearPos.WEAPON] = InvObj(GLOVE_FALCON, 1)
                        iterator.remove()
                    } else if (!target.hidden && world.npcs[target.index] === target &&
                        SuccessRoll.roll(world.random, kebbit.pair.first, kebbit.pair.second, player.skills.currentLevel(Skill.HUNTER))
                    ) {
                        val falcon = world.npcTypes[kebbit.falcon]?.let { type ->
                            world.spawnNpc(NpcSpawn(type, target.x, target.z, target.level, source = "falconry ${player.saveKey}", respawns = false))
                        }
                        world.hide(target)
                        flight.caught = target
                        if (falcon == null) {
                            respawn(flight, tick)
                            iterator.remove()
                            continue
                        }
                        flight.onPrey = falcon
                        flight.leaves = tick + WAIT_TICKS
                        world.message(player, HunterLines.FALCON_CAUGHT)
                    } else {
                        world.message(player, HunterLines.FALCON_MISSED)
                        flight.kebbit = null
                        flight.target = null
                        flight.arrives = tick + flightTicks(player.x, player.z, target.x, target.z)
                    }
                }
            }
        }
    }

    /** Whether [player] can send the falcon at [npc] from where they stand: in sight, within [LAUNCH_RANGE] tiles. Guessed. */
    private fun inRange(player: Player, npc: Npc): Boolean =
        npc.level == player.level && maxOf(abs(npc.x - player.x), abs(npc.z - player.z)) <= LAUNCH_RANGE &&
            LineOfSight.clear(collision, player.level, player.x, player.z, 1, npc.x, npc.z, npc.type.size, npc.type.size)

    /** Catch on [npc]: the falcon flies from [player] to it. */
    private fun launch(player: Player, kebbit: FalconKebbit, npc: Npc) {
        if (npc.hidden) return
        player.waypoints.clear()
        val key = player.saveKey
        val worn = player.equipment[WearPos.WEAPON]?.id
        if (worn != GLOVE_FALCON) {
            return world.message(player, if (worn == GLOVE_EMPTY) HunterLines.FALCON_AWAY else HunterLines.KEBBIT_ESCAPES)
        }
        val hunter = player.skills.currentLevel(Skill.HUNTER)
        if (hunter < kebbit.level) return world.message(player, HunterLines.FALCON_LEVEL, "level" to kebbit.level)
        player.anim(LAUNCH_ANIM)
        player.equipment[WearPos.WEAPON] = InvObj(GLOVE_EMPTY, 1)
        val ticks = flightTicks(player.x, player.z, npc.x, npc.z)
        world.zones.projectile(
            Projectile(
                id = FALCON_TRAVEL, fromX = player.x, fromZ = player.z, level = player.level, toX = npc.x, toZ = npc.z,
                startHeight = 100, endHeight = 0, startTime = 0, endTime = ticks * CYCLES_PER_TICK, angle = 16, progress = 64,
                sourceIndex = -(player.index + 1), targetIndex = npc.index + 1,
            ),
        )
        flights[key] = Flight(player, kebbit, npc, world.tick + ticks)
    }

    private fun ScriptContext.retrieve(kebbit: FalconKebbit, npc: Npc) {
        val flight = flights[player.saveKey]
        // The empty glove too: the falcon goes back onto it (exploit review round 36, C-3).
        if (flight == null || flight.onPrey !== npc || player.equipment[WearPos.WEAPON]?.id != GLOVE_EMPTY) {
            return world.message(player, HunterLines.NOT_YOUR_FALCON)
        }
        flights.remove(player.saveKey)
        world.removeNpc(npc)
        respawn(flight, world.tick)
        player.equipment[WearPos.WEAPON] = InvObj(GLOVE_FALCON, 1)
        player.skills.addTenths(Skill.HUNTER, kebbit.xpTenths)
        // Since 19 August 2026 what doesn't fit is discarded.
        for (id in kebbit.drops) world.objTypes[id]?.let { player.inventory.add(it, 1) }
        world.message(player, HunterLines.FALCON_RETRIEVED)
    }

    private suspend fun Dialogue.talk() = with(MatthiasLines) {
        val key = player.saveKey
        val worn = player.equipment[WearPos.WEAPON]?.id
        when {
            flights[key]?.onPrey != null -> {
                chatPlayer(ChatAnim.HAPPY, HELLO_AGAIN)
                chatNpc(ChatAnim.NEUTRAL, GOT_THE_HANG)
            }
            worn == GLOVE_EMPTY && flights[key] == null -> lostFalcon()
            worn == GLOVE_FALCON || worn == GLOVE_EMPTY -> {
                chatPlayer(ChatAnim.HAPPY, HELLO_AGAIN)
                chatNpc(ChatAnim.NEUTRAL, GETTING_ALONG)
                chatPlayer(ChatAnim.NEUTRAL, HARDER)
                chatNpc(ChatAnim.NEUTRAL, TALKING_TO_FALCON)
                when (choice(KEEP_TRYING, LEAVE_IT)) {
                    1 -> {
                        chatPlayer(ChatAnim.NEUTRAL, KEEP_TRYING)
                        chatPlayer(ChatAnim.NEUTRAL, COME_TALK)
                    }
                    else -> {
                        chatPlayer(ChatAnim.NEUTRAL, LEAVE_IT)
                        takeGlove(player)
                        mesbox(GIVE_BACK)
                    }
                }
            }
            else -> {
                chatPlayer(ChatAnim.HAPPY, HELLO)
                chatNpc(ChatAnim.NEUTRAL, GREETINGS)
                when (choice(QUESTS, WHAT_PLACE, YOUR_BIRD)) {
                    1 -> quests()
                    2 -> {
                        chatPlayer(ChatAnim.QUIZ, WHAT_PLACE)
                        chatNpc(ChatAnim.NEUTRAL, GOOD_QUESTION)
                        chatNpc(ChatAnim.NEUTRAL, FALCONER)
                        placeMenu()
                    }
                    else -> {
                        chatPlayer(ChatAnim.QUIZ, YOUR_BIRD)
                        haveAGo()
                    }
                }
            }
        }
    }

    private suspend fun Dialogue.quests() = with(MatthiasLines) {
        chatPlayer(ChatAnim.QUIZ, QUESTS)
        chatNpc(ChatAnim.CONFUSED, STRANGE_NOTION)
        chatPlayer(ChatAnim.NEUTRAL, ER_YES)
        chatNpc(ChatAnim.NEUTRAL, NO_QUESTS)
    }

    private suspend fun Dialogue.placeMenu() = with(MatthiasLines) {
        while (true) {
            when (choice(QUESTS, HAVE_A_GO, NOT_MY_SORT, WHAT_FALCONRY)) {
                1 -> return@with quests()
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, HAVE_A_GO)
                    return@with haveAGo()
                }
                3 -> {
                    chatPlayer(ChatAnim.NEUTRAL, NOT_MY_SORT)
                    return@with chatNpc(ChatAnim.NEUTRAL, FAIR_ENOUGH)
                }
                else -> {
                    chatPlayer(ChatAnim.QUIZ, WHAT_FALCONRY)
                    chatNpc(ChatAnim.NEUTRAL, SPORT)
                    chatNpc(ChatAnim.NEUTRAL, PUTTING_IT_SIMPLY)
                    chatPlayer(ChatAnim.QUIZ, LIKE_A_PET)
                    chatNpc(ChatAnim.NEUTRAL, NOT_EXACTLY)
                    chatNpc(ChatAnim.NEUTRAL, ASSOCIATE)
                }
            }
        }
    }

    private suspend fun Dialogue.haveAGo() = with(MatthiasLines) {
        if (player.skills.baseLevel(Skill.HUNTER) < LEVEL) {
            chatNpc(ChatAnim.NEUTRAL, TOO_LOW)
            return@with chatNpc(ChatAnim.NEUTRAL, PROFESSIONALS)
        }
        chatNpc(ChatAnim.NEUTRAL, TRY_ONE)
        chatNpc(ChatAnim.NEUTRAL, NOBLEMEN)
        chatNpc(ChatAnim.NEUTRAL, FEE)
        if (player.inventory.count(COINS) < FEE_COINS) return@with chatPlayer(ChatAnim.SAD, NOT_ENOUGH)
        when (choice(REASONABLE, NOT_INTERESTED)) {
            1 -> {
                chatPlayer(ChatAnim.NEUTRAL, REASONABLE)
                // The coins may have gone while the lines played.
                if (player.inventory.count(COINS) < FEE_COINS) return@with chatPlayer(ChatAnim.SAD, NOT_ENOUGH)
                if (!rent(player)) return@with chatNpc(ChatAnim.NEUTRAL, HANDS_FREE)
                mesbox(GIVES_GLOVE)
                chatNpc(ChatAnim.NEUTRAL, KEEP_AN_EYE)
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, NOT_INTERESTED)
                chatNpc(ChatAnim.NEUTRAL, COME_BACK)
            }
        }
    }

    private suspend fun Dialogue.lostFalcon() = with(MatthiasLines) {
        chatPlayer(ChatAnim.SAD, RAN_AWAY)
        chatNpc(ChatAnim.NEUTRAL, NOT_DOMESTICATED)
        chatNpc(ChatAnim.QUIZ, FETCH)
        when (choice(YES_PLEASE, LEAVE_IT_LOST)) {
            1 -> {
                chatPlayer(ChatAnim.HAPPY, YES_PLEASE)
                player.equipment[WearPos.WEAPON] = InvObj(GLOVE_FALCON, 1)
                mesbox(BRINGS_BIRD)
                chatNpc(ChatAnim.NEUTRAL, MORE_CAREFUL)
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, LEAVE_IT_LOST)
                takeGlove(player)
                mesbox(GIVE_GLOVE_BACK)
            }
        }
    }

    /** Quick-falcon: rents at once with the level, the coins and free hands; hands the glove back when it is worn. */
    private suspend fun Dialogue.quick() = with(MatthiasLines) {
        val worn = player.equipment[WearPos.WEAPON]?.id
        when {
            flights[player.saveKey]?.onPrey != null -> chatNpc(ChatAnim.NEUTRAL, GOT_THE_HANG)
            worn == GLOVE_EMPTY && flights[player.saveKey] == null -> {
                player.equipment[WearPos.WEAPON] = InvObj(GLOVE_FALCON, 1)
                mesbox(BRINGS_BIRD)
            }
            worn == GLOVE_FALCON || worn == GLOVE_EMPTY -> {
                takeGlove(player)
                mesbox(GIVE_BACK)
            }
            player.skills.baseLevel(Skill.HUNTER) < LEVEL -> chatNpc(ChatAnim.NEUTRAL, TOO_LOW)
            player.inventory.count(COINS) < FEE_COINS -> chatPlayer(ChatAnim.SAD, NOT_ENOUGH)
            !rent(player) -> chatNpc(ChatAnim.NEUTRAL, HANDS_FREE)
            else -> mesbox(GIVES_GLOVE)
        }
    }

    /**
     * Whether the weapon and shield slots can be emptied: since 19 August 2026 what is worn there goes to the backpack if
     * there is room for it.
     */
    private fun handsFree(player: Player): Boolean {
        var slots = 0
        for (pos in listOf(WearPos.WEAPON, WearPos.SHIELD)) {
            val obj = player.equipment[pos] ?: continue
            val type = world.objTypes[obj.id] ?: return false
            val stack = if (type.stackable) player.inventory.contents().firstOrNull { it.second.id == obj.id }?.second else null
            when {
                // Onto the stack already in the backpack, all of it or not at all.
                stack != null -> if (stack.count.toLong() + obj.count > Inventory.MAX_STACK) return false
                type.stackable -> slots++
                else -> slots += obj.count
            }
        }
        // The fee comes off first, so a coin stack it empties is room too.
        var fee = FEE_COINS
        var freed = 0
        for ((_, held) in player.inventory.contents()) {
            if (fee == 0) break
            if (held.id != COINS) continue
            val paid = minOf(fee, held.count)
            fee -= paid
            if (paid == held.count) freed++
        }
        return slots <= player.inventory.freeSlots + freed
    }

    /**
     * Takes the fee, moves the weapon and shield to the backpack and puts the glove on, all or nothing: the coins and the
     * room are checked again here, with nothing between that and the change (exploit review round 36, C-2).
     */
    private fun rent(player: Player): Boolean {
        if (player.inventory.count(COINS) < FEE_COINS || !handsFree(player)) return false
        // The hands first, the fee last (exploit review round 37, B-3); a coin stack the fee empties is room handsFree
        // counted, so what didn't fit before the fee is moved after it.
        val pending = moveHands(player)
        var left = FEE_COINS
        for ((slot, held) in player.inventory.contents()) {
            if (left == 0) break
            if (held.id == COINS) left -= player.inventory.remove(slot, left)
        }
        if (pending) moveHands(player)
        // handsFree said both would fit; never write the glove over a weapon still worn, and never keep the fee for it.
        if (player.equipment[WearPos.WEAPON] != null || player.equipment[WearPos.SHIELD] != null) {
            world.objTypes[COINS]?.let { world.groundObjs.give(player, it, FEE_COINS) }
            return false
        }
        player.equipment[WearPos.WEAPON] = InvObj(GLOVE_FALCON, 1)
        return true
    }

    /** Moves what is worn in the weapon and shield slots to the backpack where it fits; true if any is still worn. */
    private fun moveHands(player: Player): Boolean {
        for (pos in listOf(WearPos.WEAPON, WearPos.SHIELD)) {
            val obj = player.equipment[pos] ?: continue
            val type = world.objTypes[obj.id] ?: continue
            if (!type.stackable && player.inventory.freeSlots == 0) continue
            if (type.stackable && player.inventory.count(obj.id) == 0L && player.inventory.freeSlots == 0) continue
            if (type.stackable && player.inventory.count(obj.id) + obj.count > Inventory.MAX_STACK) continue
            if (player.inventory.add(type, obj.count) == obj.count) player.equipment[pos] = null
        }
        return player.equipment[WearPos.WEAPON] != null || player.equipment[WearPos.SHIELD] != null
    }


    /** Takes the glove off [player] wherever it is, and ends their falcon's flight. */
    private fun takeGlove(player: Player) {
        val worn = player.equipment[WearPos.WEAPON]?.id
        if (worn == GLOVE_FALCON || worn == GLOVE_EMPTY) player.equipment[WearPos.WEAPON] = null
        for ((slot, held) in player.inventory.contents()) {
            if (held.id == GLOVE_FALCON || held.id == GLOVE_EMPTY) player.inventory[slot] = null
        }
        end(player.saveKey)
    }

    private fun end(key: String) {
        val flight = flights.remove(key) ?: return
        flight.onPrey?.let(world::removeNpc)
        respawn(flight, world.tick)
    }

    /** The kebbit [flight]'s falcon caught comes back [RESPAWN_TICKS] after its prey is retrieved (or left). */
    private fun respawn(flight: Flight, tick: Long) {
        val kebbit = flight.caught ?: return
        flight.caught = null
        respawns += kebbit to tick + RESPAWN_TICKS
    }

    companion object {
        const val MATTHIAS = 1340
        const val TALK_TO = 1
        const val QUICK_FALCON = 3
        const val CATCH = 1
        const val RETRIEVE = 1

        const val GLOVE_EMPTY = 10023
        const val GLOVE_FALCON = 10024
        const val COINS = 995
        const val FEE_COINS = 500
        const val LEVEL = 43

        const val LAUNCH_ANIM = 5162 // human_falconer_attack
        const val FALCON_TRAVEL = 922 // falcon_travel_anim

        /** Guessed. */
        const val FLIGHT_TICKS_PER = 3
        const val WAIT_TICKS = 50
        /** The Wiki's Hunter training: a kebbit is back four seconds after its prey is retrieved. */
        const val RESPAWN_TICKS = 7

        /** Guessed: how far off a kebbit in sight can be sent at. */
        const val LAUNCH_RANGE = 15

        private const val CYCLES_PER_TICK = 30

        fun flightTicks(fromX: Int, fromZ: Int, toX: Int, toZ: Int): Int = 1 + maxOf(abs(fromX - toX), abs(fromZ - toZ)) / FLIGHT_TICKS_PER

        /** The falconry area: the bounding box of its fence (the rev 242 map's rails). Guessed to be the area. */
        fun inArea(x: Int, z: Int, level: Int) = level == 0 && x in 2364..2394 && z in 3573..3619

        const val BONES = 526

        val SPOTTED = FalconKebbit(5531, 43, 1040, 26 to 310, 1342, listOf(BONES, 10125))
        val DARK = FalconKebbit(5532, 57, 1320, 0 to 253, 1344, listOf(BONES, 10115))
        val DASHING = FalconKebbit(5533, 69, 1560, 0 to 205, 1343, listOf(BONES, 10127, 29107))

        val KEBBITS = listOf(SPOTTED, DARK, DASHING)
    }
}
