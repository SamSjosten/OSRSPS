package osrsps.game

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import osrsps.game.combat.SpecialAttacks
import osrsps.game.combat.StatRestore
import osrsps.game.social.FriendLists
import osrsps.game.ge.BuyLimitUse
import osrsps.game.ge.GeAccount
import osrsps.game.ge.GeOffer
import osrsps.game.ge.OfferType
import osrsps.game.social.IgnoreLists

private val logger = KotlinLogging.logger {}

/**
 * What a player keeps between sessions. Scripts, open interfaces and the route being walked are not kept: they are
 * gone after a logout on live too, apart from some timers (poison, farming) that come with the content that has them.
 */
class PlayerSave(
    val x: Int,
    val z: Int,
    val level: Int,
    val runEnergy: Int,
    /** Varps that aren't 0, by id, and the special attack energy ([SpecialAttacks.VARP_ENERGY]) whatever it is. */
    val varps: Map<Int, Int>,
    val skills: Map<Skill, SkillSave>,
    /** The inventory's filled slots, by slot. */
    val inventory: Map<Int, InvObj> = emptyMap(),
    /** What is worn, by [WearPos]. */
    val equipment: Map<Int, InvObj> = emptyMap(),
    /** The `::scenario` the player last started, null when they are not in one. */
    val scenario: String? = null,
    /** The player as they were before their first `::scenario`, which `::reset` puts back; kept over a logout. */
    val beforeScenario: PlayerSave? = null,
    /** When the account was made, in ms since 1970; null in saves written before it was kept. */
    val createdAt: Long? = null,
    /** Game ticks spent logged in. */
    val playedTicks: Long = 0,
    /** What charged items hold, by name; see [Charges]. */
    val charges: Map<String, Int> = emptyMap(),
    /** The bank's filled slots, by slot. */
    val bank: Map<Int, InvObj> = emptyMap(),
    /** The combat and other skills' boost cycles' segments; see [osrsps.game.combat.StatRestore.segment]. */
    val boostSegments: BoostSegments = BoostSegments(0, 0),
    /** The chatbox's filters. Like the boost segments, part of the header rather than the state ::reset puts back. */
    val chatFilters: ChatFilters = ChatFilters.DEFAULT,
    /** The friends list, oldest first. Part of the header too: ::reset doesn't touch it. */
    val friends: List<String> = emptyList(),
    /** Grand Exchange offers, by slot. */
    val geOffers: Map<Int, GeOffer> = emptyMap(),
    /** What the Grand Exchange buy limits have counted, by item. */
    val geLimits: List<BuyLimitUse> = emptyList(),
    /** The ignore list, oldest first, in the header like the friends list. */
    val ignores: List<String> = emptyList(),
) {
    class BoostSegments(val combat: Int, val other: Int)

    class SkillSave(val tenths: Int, val currentLevel: Int)

    /**
     * Gives [player] this save's state. The player must be new: varps the save doesn't name are left as they are.
     * [inTick]: whether the login is in a tick's input rather than on a paused cycle, for the stat cycles (see
     * [osrsps.game.combat.BoostCycle.restart]).
     */
    fun applyTo(player: Player, inTick: Boolean = true) {
        player.x = x
        player.z = z
        player.level = level
        player.buildAreaZoneX = x shr 3
        player.buildAreaZoneZ = z shr 3
        player.scenario = scenario
        player.beforeScenario = beforeScenario
        // A save from before account ages were kept counts the account as made now.
        if (createdAt != null) player.createdAt = createdAt
        player.playedTicks = playedTicks
        StatRestore.resume(player.statTimers.combatBoost, boostSegments.combat, inTick)
        StatRestore.resume(player.statTimers.skillBoost, boostSegments.other, inTick)
        player.statTimers.hitpoints.restart(inTick)
        player.chatFilters = chatFilters
        player.friends.addAll(friends)
        player.ignores.addAll(ignores)
        applyState(player)
    }

    /**
     * Puts a player who is already in the world back to this save's state, apart from where they stand: the caller
     * teleports them, so the client is told. Varps the save doesn't name go to 0 and every slot is emptied first, so
     * nothing from before survives. The scenario fields are left to the caller too.
     */
    fun restoreTo(player: Player) {
        player.vars.forEachNonZero { varp, _ -> if (varp !in varps) player.vars[varp] = 0 }
        for (skill in Skill.entries) {
            if (skill !in skills) {
                // A save names every skill; one written by a server with fewer skills leaves the new ones as new.
                player.skills.setLevel(skill, PlayerSkills.newLevel(skill))
            }
        }
        player.inventory.clear()
        player.equipment.clear()
        player.bank.clear()
        player.charges.clear()
        player.ge.clear()
        applyState(player)
    }

    private fun applyState(player: Player) {
        player.runEnergy = runEnergy
        for ((varp, value) in varps) {
            if (varp >= player.vars.types.varpCount) {
                // A revision can drop varps; losing one beats refusing the login.
                logger.warn { "Dropping saved varp $varp of ${player.username}: the cache has ${player.vars.types.varpCount}" }
                continue
            }
            player.vars[varp] = value
        }
        if (SpecialAttacks.VARP_ENERGY !in varps) player.vars[SpecialAttacks.VARP_ENERGY] = SpecialAttacks.FULL
        for ((skill, save) in skills) {
            player.skills.setTenths(skill, save.tenths)
            player.skills.setCurrentLevel(skill, save.currentLevel)
        }
        // Item ids aren't checked against the cache: live doesn't remove items between revisions.
        for ((slot, obj) in inventory) player.inventory[slot] = obj
        // Not checked against levels either: a save is what the player had, even if something has changed since.
        for ((slot, obj) in equipment) player.equipment[slot] = obj
        for ((slot, obj) in bank) player.bank[slot] = obj
        for ((name, count) in charges) player.charges[name] = count
        for ((slot, offer) in geOffers) player.ge[slot] = offer
        for (use in geLimits) player.ge.limits[use.item] = use
    }

    fun toJson(username: String): String {
        val json = buildJsonObject {
            put("version", VERSION)
            // For whoever opens the file; the file name is what finds it.
            put("username", username)
            // Not part of the state ::reset puts back: time played and account age go on through scenarios.
            if (createdAt != null) put("createdAt", createdAt)
            put("playedTicks", playedTicks)
            // Not part of ::reset's state either: the cycles run on through scenarios as they do through logouts.
            putJsonObject("boostSegments") {
                put("combat", boostSegments.combat)
                put("other", boostSegments.other)
            }
            // And the chat filters, which are the client's settings rather than the account's state.
            if (chatFilters != ChatFilters.DEFAULT) {
                putJsonObject("chatFilters") {
                    put("public", chatFilters.public)
                    put("private", chatFilters.private)
                    put("trade", chatFilters.trade)
                }
            }
            if (friends.isNotEmpty()) putJsonArray("friends") { for (name in friends) add(name) }
            if (ignores.isNotEmpty()) putJsonArray("ignores") { for (name in ignores) add(name) }
            putState()
            if (scenario != null) put("scenario", scenario)
            if (beforeScenario != null) putJsonObject("beforeScenario") { with(beforeScenario) { putState() } }
        }
        return format.encodeToString(JsonObject.serializer(), json) + "\n"
    }

    /** Everything but the header and the scenario fields, which a [beforeScenario] doesn't have. */
    private fun JsonObjectBuilder.putState() {
        putJsonObject("position") {
            put("x", x)
            put("z", z)
            put("level", level)
        }
        put("runEnergy", runEnergy)
        putJsonObject("skills") {
            for ((skill, save) in skills) {
                putJsonObject(skill.name.lowercase()) {
                    put("xpTenths", save.tenths)
                    put("currentLevel", save.currentLevel)
                }
            }
        }
        putJsonObject("varps") {
            for ((varp, value) in varps.toSortedMap()) put(varp.toString(), value)
        }
        putJsonArray("inventory") { putObjs(inventory) }
        putJsonArray("equipment") { putObjs(equipment) }
        if (bank.isNotEmpty()) putJsonArray("bank") { putObjs(bank) }
        if (charges.isNotEmpty()) putJsonObject("charges") { for ((name, count) in charges) put(name, count) }
        if (geOffers.isNotEmpty()) {
            putJsonArray("geOffers") {
                for ((slot, offer) in geOffers.toSortedMap()) {
                    addJsonObject {
                        put("slot", slot)
                        put("type", offer.type.name.lowercase())
                        put("item", offer.item)
                        put("quantity", offer.quantity)
                        put("price", offer.price)
                        put("completed", offer.completed)
                        put("exchanged", offer.exchanged)
                        if (offer.aborted) put("aborted", true)
                        put("collectItems", offer.collectItems)
                        put("collectCoins", offer.collectCoins)
                    }
                }
            }
        }
        if (geLimits.isNotEmpty()) {
            putJsonArray("geLimits") {
                for (use in geLimits) {
                    addJsonObject {
                        put("item", use.item)
                        put("windowStart", use.windowStart)
                        put("bought", use.bought)
                    }
                }
            }
        }
    }

    companion object {
        /** Raised when the format changes in a way older saves can't be read as. */
        const val VERSION = 1

        /** The packets carry each filter in a byte. */
        const val MAX_CHAT_FILTER = 255

        private val format = Json { prettyPrint = true }

        fun of(player: Player): PlayerSave {
            val varps = LinkedHashMap<Int, Int>()
            player.vars.forEachNonZero { varp, value -> varps[varp] = value }
            // Kept at 0 too, so a save without it is one from before special attacks, whose energy starts full.
            varps[SpecialAttacks.VARP_ENERGY] = player.vars[SpecialAttacks.VARP_ENERGY]
            val skills = Skill.entries.associateWith { SkillSave(player.skills.tenths(it), player.skills.currentLevel(it)) }
            val inventory = player.inventory.contents().toMap()
            val equipment = player.equipment.contents().toMap()
            return PlayerSave(
                player.x, player.z, player.level, player.runEnergy, varps, skills, inventory, equipment,
                player.scenario, player.beforeScenario, player.createdAt, player.playedTicks, player.charges.contents(),
                player.bank.contents().toMap(),
                BoostSegments(StatRestore.segment(player.statTimers.combatBoost), StatRestore.segment(player.statTimers.skillBoost)),
                player.chatFilters,
                player.friends.toList(),
                player.ge.contents().toMap(), player.ge.limits.values.toList(),
                player.ignores.toList(),
            )
        }

        /** Reads a save written by [toJson]. Throws [IllegalArgumentException] if it can't be used as it is. */
        fun fromJson(text: String): PlayerSave {
            val root = try {
                Json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw IllegalArgumentException("Not a JSON object: ${e.message}", e)
            }
            try {
                val version = root.int("version")
                require(version in 1..VERSION) { "Save version $version is newer than this server's $VERSION" }
                // Optional: saves written before scenarios existed have neither.
                val scenario = root["scenario"]?.jsonPrimitive?.contentOrNull
                val beforeScenario = root["beforeScenario"]?.jsonObject?.let { readState(it, null, null) }
                // Optional too: saves written before Hans could tell your age have neither.
                val createdAt = root["createdAt"]?.jsonPrimitive?.long
                val playedTicks = root["playedTicks"]?.jsonPrimitive?.long ?: 0
                require(playedTicks >= 0) { "Played ticks $playedTicks is negative" }
                // Optional too: saves from before boosts wore off start both cycles at their first segment.
                val segments = root["boostSegments"]?.jsonObject
                val boostSegments = BoostSegments(segments?.int("combat") ?: 0, segments?.int("other") ?: 0)
                for (segment in listOf(boostSegments.combat, boostSegments.other)) {
                    require(segment in 0 until StatRestore.CYCLE_TICKS / StatRestore.SEGMENT_TICKS) { "Boost segment $segment is out of range" }
                }
                // Optional: saves from before the filters were kept, and saves with every filter on, have none.
                val chatFilters = root["chatFilters"]?.jsonObject?.let { filters ->
                    val (public, private, trade) = listOf("public", "private", "trade").map { name ->
                        filters.int(name).also { require(it in 0..MAX_CHAT_FILTER) { "Chat filter $name $it is out of range" } }
                    }
                    ChatFilters(public, private, trade)
                } ?: ChatFilters.DEFAULT
                // Optional: saves from before the friends list, and saves with an empty one, have none.
                val friends = root["friends"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
                require(friends.size <= FriendLists.MAX_FRIENDS) { "${friends.size} friends is more than ${FriendLists.MAX_FRIENDS}" }
                val ignores = root["ignores"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
                require(ignores.size <= IgnoreLists.MAX_IGNORES) { "${ignores.size} ignored names is more than ${IgnoreLists.MAX_IGNORES}" }
                val state = readState(root, scenario, beforeScenario)
                return PlayerSave(
                    state.x, state.z, state.level, state.runEnergy, state.varps, state.skills, state.inventory, state.equipment,
                    scenario, beforeScenario, createdAt, playedTicks, state.charges, state.bank, boostSegments, chatFilters,
                    friends,
                    state.geOffers, state.geLimits,
                    ignores,
                )
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: Exception) {
                // A missing key or a value of the wrong type.
                throw IllegalArgumentException(e.message, e)
            }
        }

        private fun readState(root: JsonObject, scenario: String?, beforeScenario: PlayerSave?): PlayerSave {
            val position = root.getValue("position").jsonObject
            val x = position.int("x")
            val z = position.int("z")
            val level = position.int("level")
            require(x in 0..MAX_COORD && z in 0..MAX_COORD && level in 0..3) { "No such tile $x, $z, $level" }
            val runEnergy = root.int("runEnergy")
            require(runEnergy in 0..Movement.MAX_ENERGY) { "Run energy $runEnergy is out of range" }
            val skills = LinkedHashMap<Skill, SkillSave>()
            for ((name, value) in root.getValue("skills").jsonObject) {
                val skill = Skill.entries.firstOrNull { it.name.lowercase() == name } ?: throw IllegalArgumentException("No skill '$name'")
                val entry = value.jsonObject
                val tenths = entry.int("xpTenths")
                val currentLevel = entry.int("currentLevel")
                require(tenths in 0..Experience.MAX_TENTHS) { "$name XP $tenths is out of range" }
                require(currentLevel in 0..PlayerSkills.MAX_CURRENT_LEVEL) { "$name level $currentLevel is out of range" }
                skills[skill] = SkillSave(tenths, currentLevel)
            }
            val varps = LinkedHashMap<Int, Int>()
            for ((id, value) in root.getValue("varps").jsonObject) {
                val varp = id.toIntOrNull() ?: throw IllegalArgumentException("Varp id '$id' is not a number")
                require(varp >= 0) { "Varp id $varp is negative" }
                varps[varp] = value.jsonPrimitive.int
            }
            // Optional: saves written before the inventory or equipment existed have none.
            val inventory = root.objs("inventory", "Inventory", Inventory.INV_CAPACITY)
            val equipment = root.objs("equipment", "Equipment", WearPos.COUNT)
            // Optional: saves from before the bank, and saves with an empty one, have none.
            val bank = root.objs("bank", "Bank", Inventory.BANK_CAPACITY)
            // Optional: saves from before charged items have none.
            val charges = LinkedHashMap<String, Int>()
            for ((name, value) in root["charges"]?.jsonObject.orEmpty()) {
                val count = value.jsonPrimitive.int
                require(count >= 0) { "Charge $name is negative" }
                charges[name] = count
            }
            // Optional: saves from before the Grand Exchange, and saves with no offers, have none.
            val geOffers = LinkedHashMap<Int, GeOffer>()
            for (element in root["geOffers"]?.jsonArray.orEmpty()) {
                val entry = element.jsonObject
                val slot = entry.int("slot")
                require(slot in 0 until GeAccount.SLOTS) { "Grand Exchange slot $slot is out of range" }
                require(slot !in geOffers) { "Grand Exchange slot $slot is saved twice" }
                val typeName = entry.getValue("type").jsonPrimitive.content
                val type = OfferType.entries.firstOrNull { it.name.lowercase() == typeName } ?: throw IllegalArgumentException("No offer type '$typeName'")
                // GeOffer's own checks throw IllegalArgumentException for values out of range.
                geOffers[slot] = GeOffer(
                    type, entry.int("item"), entry.int("quantity"), entry.long("price"), entry.int("completed"), entry.long("exchanged"),
                    entry["aborted"]?.jsonPrimitive?.boolean ?: false, entry.int("collectItems"), entry.long("collectCoins"),
                )
            }
            val geLimits = root["geLimits"]?.jsonArray.orEmpty().map {
                val entry = it.jsonObject
                BuyLimitUse(entry.int("item"), entry.long("windowStart"), entry.int("bought"))
            }
            return PlayerSave(
                x, z, level, runEnergy, varps, skills, inventory, equipment, scenario, beforeScenario, charges = charges, bank = bank,
                geOffers = geOffers, geLimits = geLimits,
            )
        }

        /** The username a save was written for, or null if [text] isn't a save that names one. */
        fun usernameIn(text: String): String? = try {
            Json.parseToJsonElement(text).jsonObject["username"]?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) {
            null
        }

        private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

        private fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.long

        private fun JsonArrayBuilder.putObjs(objs: Map<Int, InvObj>) {
            for ((slot, obj) in objs.toSortedMap()) {
                addJsonObject {
                    put("slot", slot)
                    put("id", obj.id)
                    put("count", obj.count)
                }
            }
        }

        /** The {slot, id, count} entries under [key], none if it is missing. */
        private fun JsonObject.objs(key: String, what: String, capacity: Int): Map<Int, InvObj> {
            val objs = LinkedHashMap<Int, InvObj>()
            for (element in this[key]?.jsonArray.orEmpty()) {
                val entry = element.jsonObject
                val slot = entry.int("slot")
                require(slot in 0 until capacity) { "$what slot $slot is out of range" }
                require(slot !in objs) { "$what slot $slot is saved twice" }
                objs[slot] = InvObj(entry.int("id"), entry.int("count"))
            }
            return objs
        }

        private const val MAX_COORD = 16383
    }
}
