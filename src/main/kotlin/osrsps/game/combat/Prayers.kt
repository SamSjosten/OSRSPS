package osrsps.game.combat

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.Equipment
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.PlayerVars
import osrsps.game.Skill

private val logger = KotlinLogging.logger {}

/**
 * A prayer of the standard book. What the cache's prayer obj has: its [name], the Prayer [level] it needs, its [bit] in
 * varp 83 (prayer0, whose bits 0 to 28 are varbit prayer_allactive) and its [component] of the prayer book, and the
 * [sound] it makes when turned on. What the data file adds: its [drain] effect per tick, the [groups] of prayers it
 * can't be on with (each prayer turned on turns off every other prayer that shares a group with it), its [overhead]
 * icon (-1 for none), the attack type it [protects] against (null for none), the Defence level it needs ([defence], 1
 * for none), and what it adds to the combat formulas ([boosts]).
 */
data class Prayer(
    val obj: Int,
    val name: String,
    val bit: Int,
    val component: Int,
    val level: Int,
    val sound: Int,
    val drain: Int,
    val groups: Set<String>,
    val overhead: Int = -1,
    val protects: AttackType? = null,
    val defence: Int = 1,
    val boosts: PrayerBoosts = PrayerBoosts.NONE,
) {
    val mask: Int get() = 1 shl bit
}

/**
 * What prayers add to the combat formulas, in percent of the level they multiply (100 for none) and, for
 * [magicDamage], tenths of a percent of magic damage bonus. Each style has its own: Piety's strength is melee
 * [strength] and Rigour's is [rangedStrength]. [defence] counts against every style and [magicDefence] against magic
 * attacks alone (see [Combat]). [minRangedStrengthGain] is Sharp Eye's: its ranged strength boost is at least a level
 * (the OSRS Wiki's DPS calculator, weirdgloop's osrs-dps-calc, PlayerVsNPCCalc, "force 1 level gain").
 */
data class PrayerBoosts(
    val attack: Int = 100,
    val strength: Int = 100,
    val defence: Int = 100,
    val ranged: Int = 100,
    val rangedStrength: Int = 100,
    val magic: Int = 100,
    val magicDefence: Int = 100,
    val magicDamage: Int = 0,
    val minRangedStrengthGain: Int = 0,
) {
    /** Both together: the percentages add (osrs-dps-calc's prayers add, for a pair that boosts one level). */
    operator fun plus(other: PrayerBoosts) = PrayerBoosts(
        attack = attack + other.attack - 100,
        strength = strength + other.strength - 100,
        defence = defence + other.defence - 100,
        ranged = ranged + other.ranged - 100,
        rangedStrength = rangedStrength + other.rangedStrength - 100,
        magic = magic + other.magic - 100,
        magicDefence = magicDefence + other.magicDefence - 100,
        magicDamage = magicDamage + other.magicDamage,
        minRangedStrengthGain = maxOf(minRangedStrengthGain, other.minRangedStrengthGain),
    )

    companion object {
        val NONE = PrayerBoosts()

        /** [level] times [percent] / 100, rounded down: a prayer's boost to a level (OSRS Wiki, Damage per second). */
        fun boost(level: Int, percent: Int): Int = level * percent / 100
    }
}

/**
 * The standard prayer book (OSRS Wiki, Prayer, read 2026-10-29): prayers turned on and off on the prayer book (interface
 * 541), the points they drain, their overhead icons and the protection prayers. The book's components transmit op 1
 * themselves (prayerbook.if3 has transmitop), so a click needs no IF_SETEVENTS; the client also turns the prayer on in
 * its own copy of varp 83 (client script prayer_op), and the server's varp then settles it.
 *
 * Drain: every tick each prayer on adds its drain effect to the player's drain counter, and a point goes each time the
 * counter passes the drain resistance, 60 plus twice the worn prayer bonus, the counter keeping the rest. A prayer
 * turned on this tick neither drains this tick nor applies to anything this tick: protection and the other effects go
 * by the prayers on at the start of the tick, [inEffect]. At no points every prayer goes off. Protection prayers block
 * all of an NPC's damage of their type (40% of a player's, which isn't in).
 *
 * Guessed and not checked on live: the messages' wording; that a prayer can be turned on during a delay (an agility
 * obstacle) as it can be in combat; that dying and logging out turn every prayer off; that a protection prayer counts
 * when the NPC's attack is rolled (as our melee NPC hits land on the tick they are rolled, that is also when they land);
 * that the drain happens after the NPCs' attacks in the tick; and that the drain counter starts again from 0 whenever
 * every prayer is off, so a prayer turned off and on again in one tick (flicking) costs nothing, as the Wiki says.
 *
 * Every account has Chivalry and Piety (Knight Waves, after King's Ransom) and Rigour, Augury and Preserve (the
 * Chambers of Xeric scrolls) unlocked, as every account has every quest done ([osrsps.game.Quests]); [unlockAll] sets
 * the varbits the client checks before it lets them be clicked.
 */
class Prayers(
    val all: List<Prayer>,
    private val equipment: Equipment? = null,
    private val message: (Player, String) -> Unit = Player::message,
    private val sound: (Player, Int) -> Unit = { player, id -> player.sound(id) },
    private val setOverhead: (Player, Int) -> Unit = { _, _ -> },
) {
    private val byComponent: Map<Int, Prayer> = all.associateBy { it.component }

    /** Every other prayer each prayer turns off when turned on, as a mask of their bits. */
    private val conflicts: Map<Prayer, Int> = all.associateWith { prayer ->
        all.filter { it !== prayer && it.groups.any { group -> group in prayer.groups } }.fold(0) { mask, it -> mask or it.mask }
    }

    fun find(name: String): Prayer? = all.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** The prayers [player] has on, as varp 83's bits. */
    fun active(player: Player): Int = player.vars[VARP_PRAYERS] and ALL_BITS

    fun isActive(player: Player, prayer: Prayer): Boolean = active(player) and prayer.mask != 0

    /** Whether [prayer] counts this tick: it was on at the start of the tick. */
    fun inEffect(player: Player, prayer: Prayer): Boolean = player.prayersInEffect and prayer.mask != 0

    /** Whether a protection prayer that counts this tick blocks an NPC's attack of [type] (melee is [AttackType.CRUSH] here). */
    fun protects(player: Player, type: AttackType): Boolean {
        val against = if (type.melee) AttackType.CRUSH else type
        return all.any { it.protects == against && inEffect(player, it) }
    }

    /** What the prayers that count this tick ([inEffect]) add to [player]'s combat formulas. */
    fun boosts(player: Player): PrayerBoosts {
        if (player.prayersInEffect == 0) return PrayerBoosts.NONE
        return all.filter { inEffect(player, it) }.fold(PrayerBoosts.NONE) { sum, it -> sum + it.boosts }
    }

    /** A click on the prayer book. Returns false when it isn't a prayer's component. */
    fun click(player: Player, component: Int, op: Int): Boolean {
        val prayer = byComponent[component] ?: return false
        if (op != 1) return true
        if (isActive(player, prayer)) deactivate(player, prayer) else activate(player, prayer)
        return true
    }

    /** Turns [prayer] on, the prayers it conflicts with off; false, having told the player why, when they can't. */
    fun activate(player: Player, prayer: Prayer): Boolean {
        val reason = refusal(player, prayer)
        if (reason != null) {
            // The client may already show it on; the varp sent back (unchanged) settles that.
            player.vars.resend(VARP_PRAYERS)
            if (reason.isNotEmpty()) message(player, reason)
            return false
        }
        val before = active(player)
        val after = (before and conflicts.getValue(prayer).inv()) or prayer.mask
        player.prayersActivated = player.prayersActivated or prayer.mask
        set(player, after)
        sound(player, prayer.sound)
        return true
    }

    fun deactivate(player: Player, prayer: Prayer) {
        if (!isActive(player, prayer)) return
        set(player, active(player) and prayer.mask.inv())
        sound(player, DEACTIVATE_SOUND)
    }

    /** Every prayer off, with no sound: dying, logging in. */
    fun deactivateAll(player: Player) {
        set(player, 0)
        player.prayersInEffect = 0
        player.prayersActivated = 0
    }

    /** Why [player] can't turn [prayer] on, null when they can; empty when there are no points (the client says so). */
    private fun refusal(player: Player, prayer: Prayer): String? = when {
        player.dead -> ""
        player.skills.currentLevel(Skill.PRAYER) <= 0 -> ""
        player.skills.baseLevel(Skill.PRAYER) < prayer.level -> "You need a Prayer level of ${prayer.level} to use ${prayer.name}."
        player.skills.baseLevel(Skill.DEFENCE) < prayer.defence -> "You need a Prayer level of ${prayer.level} and a Defence level of ${prayer.defence} to use ${prayer.name}."
        else -> null
    }

    private fun set(player: Player, bits: Int) {
        player.vars[VARP_PRAYERS] = (player.vars[VARP_PRAYERS] and ALL_BITS.inv()) or bits
        if (bits == 0) player.prayerDrainCounter = 0
        val overhead = all.firstOrNull { it.overhead >= 0 && bits and it.mask != 0 }?.overhead ?: -1
        if (overhead != player.overheadIcon) {
            player.overheadIcon = overhead
            setOverhead(player, overhead)
        }
    }

    /** The start of a tick, before any input: the prayers on now are the ones that count this tick. */
    fun startTick(player: Player) {
        player.prayersInEffect = active(player)
        player.prayersActivated = 0
    }

    /** This tick's drain, after the NPCs have attacked; at no points every prayer goes off. */
    fun drain(player: Player) {
        val draining = active(player) and player.prayersActivated.inv()
        if (draining == 0) return
        val effect = all.sumOf { if (draining and it.mask != 0) it.drain else 0 }
        val resistance = drainResistance(player)
        player.prayerDrainCounter += effect
        var points = player.skills.currentLevel(Skill.PRAYER)
        while (player.prayerDrainCounter > resistance && points > 0) {
            player.prayerDrainCounter -= resistance
            points--
        }
        player.skills.setCurrentLevel(Skill.PRAYER, points)
        if (points == 0) {
            set(player, 0)
            sound(player, DEPLETE_SOUND)
            message(player, OUT_OF_POINTS)
        }
    }

    /** 60 plus twice the worn prayer bonus (OSRS Wiki, Prayer). */
    fun drainResistance(player: Player): Int = 60 + 2 * (equipment?.bonuses(player)?.prayer ?: 0)

    /** At login: every prayer off, and the unlocks set ([Prayers] class note). */
    fun login(player: Player) {
        deactivateAll(player)
        unlockAll(player.vars)
    }

    /** Sets the unlock varbits; one the cache doesn't have (tests' caches) is left out. */
    fun unlockAll(vars: PlayerVars) {
        if (vars.types.varbit(VARBIT_KNIGHT_WAVES) != null && vars.getVarbit(VARBIT_KNIGHT_WAVES) < KNIGHT_WAVES_DONE) {
            vars.setVarbit(VARBIT_KNIGHT_WAVES, KNIGHT_WAVES_DONE)
        }
        for (varbit in SCROLL_UNLOCKS) if (vars.types.varbit(varbit) != null) vars.setVarbit(varbit, 1)
    }

    companion object {
        const val RESOURCE = "/prayers.json"

        const val PRAYERBOOK = 541

        /** prayer0: bits 0 to 28 are varbit prayer_allactive (4101), the standard book's prayers. */
        const val VARP_PRAYERS = 83
        const val ALL_BITS = (1 shl 29) - 1

        /** RuneLite's SoundEffectID: PRAYER_DEACTIVE_VWOOP and PRAYER_DEPLETE_TWINKLE. */
        const val DEACTIVATE_SOUND = 2663
        const val DEPLETE_SOUND = 2672

        /** kr_knightwaves_state (client script 10172 lets Chivalry and Piety be used past 7). */
        const val VARBIT_KNIGHT_WAVES = 3909
        const val KNIGHT_WAVES_DONE = 8

        /** prayer_rigour_unlocked, prayer_augury_unlocked, prayer_preserve_unlocked. */
        val SCROLL_UNLOCKS = listOf(5451, 5452, 5453)

        const val OUT_OF_POINTS = "You have run out of prayer points, you can recharge at an altar."

        /** Cache params of the prayer objs (client scripts prayer_redraw and prayer_updatebutton). */
        const val PARAM_BIT = 630
        const val PARAM_COMPONENT = 1751
        const val PARAM_NAME = 1752
        const val PARAM_LEVEL = 1753
        const val PARAM_SOUND = 1755

        /** Reads [RESOURCE]; see [parse]. */
        fun load(objTypes: ObjTypes): List<Prayer> {
            val text = Prayers::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text, objTypes)
        }

        /**
         * Parses a prayers file:
         *
         * ```
         * { "prayers": [ { "prayer": "placeholder_xmas16_marionette_blue", "name": "Protect from Melee", "drain": 12,
         *                  "groups": ["overhead"], "overhead": 0, "protects": "melee" } ] }
         * ```
         *
         * "prayer" is the prayer obj's gameval name and "name" what its param 1752 must say, so a cache where the obj
         * has become something else fails the file. "groups", "overhead", "protects" ("melee", "ranged" or "magic"),
         * "defence" and "boosts" ([PrayerBoosts]' fields, e.g. `{"attack": 120, "strength": 123, "defence": 125}`) may be
         * left out. A prayer the cache doesn't have is skipped with a warning.
         */
        fun parse(text: String, objTypes: ObjTypes, fileName: String = RESOURCE): List<Prayer> {
            val root = Json.parseToJsonElement(text).jsonObject
            val unknown = ArrayList<String>()
            val prayers = ArrayList<Prayer>()
            root.required("prayers").jsonArray.forEachIndexed { i, element ->
                try {
                    val entry = element.jsonObject
                    val objName = entry.required("prayer").jsonPrimitive.content
                    val type = objTypes.find(objName)
                    if (type == null) {
                        unknown += objName
                        return@forEachIndexed
                    }
                    val name = entry.required("name").jsonPrimitive.content
                    require(type.stringParam(PARAM_NAME) == name) { "$objName is ${type.stringParam(PARAM_NAME)} in the cache (param $PARAM_NAME), not $name" }
                    val packed = type.intParam(PARAM_COMPONENT, -1)
                    require(packed ushr 16 == PRAYERBOOK) { "$name isn't on the prayer book (param $PARAM_COMPONENT is $packed)" }
                    val prayer = Prayer(
                        obj = type.id,
                        name = name,
                        bit = type.intParam(PARAM_BIT, -1).also { require(it in 0..28) { "$name has bit $it (param $PARAM_BIT)" } },
                        component = packed and 0xFFFF,
                        level = type.intParam(PARAM_LEVEL, -1).also { require(it in 1..99) { "$name needs Prayer $it (param $PARAM_LEVEL)" } },
                        sound = type.intParam(PARAM_SOUND, -1),
                        drain = entry.required("drain").jsonPrimitive.int.also { require(it >= 1) { "a drain of $it" } },
                        groups = entry["groups"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
                        overhead = entry["overhead"]?.jsonPrimitive?.int ?: -1,
                        protects = entry["protects"]?.jsonPrimitive?.content?.let { protects ->
                            when (protects) {
                                "melee" -> AttackType.CRUSH
                                "ranged" -> AttackType.RANGED
                                "magic" -> AttackType.MAGIC
                                else -> throw IllegalArgumentException("\"protects\": \"$protects\"")
                            }
                        },
                        defence = entry["defence"]?.jsonPrimitive?.int ?: 1,
                        boosts = entry["boosts"]?.jsonObject?.let(::parseBoosts) ?: PrayerBoosts.NONE,
                    )
                    require(prayers.none { it.bit == prayer.bit }) { "bit ${prayer.bit} is listed twice" }
                    require(prayers.none { it.component == prayer.component }) { "component ${prayer.component} is listed twice" }
                    prayers += prayer
                } catch (e: Exception) {
                    throw IllegalArgumentException("$fileName: prayers entry ${i + 1}: ${e.message}", e)
                }
            }
            if (unknown.isNotEmpty()) logger.warn { "$fileName: skipped prayers the cache doesn't have: ${unknown.joinToString()}" }
            return prayers
        }

        private val BOOST_KEYS = setOf(
            "attack", "strength", "defence", "ranged", "rangedStrength", "magic", "magicDefence", "magicDamage", "minRangedStrengthGain",
        )

        private fun parseBoosts(boosts: JsonObject): PrayerBoosts {
            val unknown = boosts.keys - BOOST_KEYS
            require(unknown.isEmpty()) { "unknown boosts ${unknown.joinToString()}" }
            fun percent(key: String) = boosts[key]?.jsonPrimitive?.int?.also { require(it in 100..200) { "\"$key\": $it" } } ?: 100
            return PrayerBoosts(
                attack = percent("attack"),
                strength = percent("strength"),
                defence = percent("defence"),
                ranged = percent("ranged"),
                rangedStrength = percent("rangedStrength"),
                magic = percent("magic"),
                magicDefence = percent("magicDefence"),
                magicDamage = boosts["magicDamage"]?.jsonPrimitive?.int ?: 0,
                minRangedStrengthGain = boosts["minRangedStrengthGain"]?.jsonPrimitive?.int ?: 0,
            )
        }

        private fun JsonObject.required(key: String): JsonElement = get(key) ?: throw IllegalArgumentException("no \"$key\"")
    }
}
