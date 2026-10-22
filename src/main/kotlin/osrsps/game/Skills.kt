package osrsps.game

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

/**
 * The skills, in the client's stat id order ([ordinal] is the id the client and RuneLite's Skill enum use).
 * Sailing (id 23) came out on 19 November 2025, so live has 24 skills, not the 23 older lists give.
 */
enum class Skill(val displayName: String, vararg val aliases: String) {
    ATTACK("Attack", "att", "atk"),
    DEFENCE("Defence", "def", "defense"),
    STRENGTH("Strength", "str"),
    HITPOINTS("Hitpoints", "hp", "hits"),
    RANGED("Ranged", "range", "ranging"),
    PRAYER("Prayer", "pray"),
    MAGIC("Magic", "mage"),
    COOKING("Cooking", "cook"),
    WOODCUTTING("Woodcutting", "wc", "wcing"),
    FLETCHING("Fletching", "fletch"),
    FISHING("Fishing", "fish"),
    FIREMAKING("Firemaking", "fm", "fming"),
    CRAFTING("Crafting", "craft"),
    SMITHING("Smithing", "smith"),
    MINING("Mining", "mine"),
    HERBLORE("Herblore", "herb"),
    AGILITY("Agility", "agil", "agi"),
    THIEVING("Thieving", "thieve", "thiev"),
    SLAYER("Slayer", "slay"),
    FARMING("Farming", "farm"),
    RUNECRAFT("Runecraft", "rc", "runecrafting"),
    HUNTER("Hunter", "hunt"),
    CONSTRUCTION("Construction", "con", "cons"),
    SAILING("Sailing", "sail"),
    ;

    val id: Int get() = ordinal

    companion object {
        val COUNT = entries.size

        private val byName: Map<String, Skill> = buildMap {
            for (skill in Skill.entries) {
                put(skill.name.lowercase(), skill)
                for (alias in skill.aliases) put(alias, skill)
            }
        }

        /** A skill by name, alias or id, ignoring case: "attack", "att" and "0" are all Attack. */
        fun find(text: String): Skill? {
            val key = text.lowercase()
            return byName[key] ?: key.toIntOrNull()?.let { entries.getOrNull(it) }
        }
    }
}

/** The XP table: XP is kept in tenths, as live keeps it to one decimal place (wiki, Experience). */
object Experience {
    const val MAX_LEVEL = 99

    /** 200,000,000 XP, the cap in every skill. */
    const val MAX_TENTHS = 2_000_000_000

    /**
     * Whole XP needed for each level, index = level (index 0 unused). The wiki's formula:
     * `floor(1/4 * sum for l in 1 until level of floor(l + 300 * 2^(l / 7)))`, giving 83 for level 2 and
     * 13,034,431 for 99.
     */
    private val table: IntArray = IntArray(MAX_LEVEL + 1).also { table ->
        var points = 0.0
        for (level in 2..MAX_LEVEL) {
            val l = level - 1
            points += floor(l + 300.0 * 2.0.pow(l / 7.0))
            table[level] = (points / 4).toInt()
        }
    }

    /** The whole XP that reaches [level], 1 to 99. */
    fun xpForLevel(level: Int): Int {
        require(level in 1..MAX_LEVEL) { "Levels are 1 to $MAX_LEVEL, not $level" }
        return table[level]
    }

    /** The level [tenths] of XP gives, 1 to 99. */
    fun levelForTenths(tenths: Int): Int {
        val xp = tenths / 10
        var level = 1
        while (level < MAX_LEVEL && xp >= table[level + 1]) level++
        return level
    }
}

/**
 * One player's skills: XP and the current level of each, where the current level is the boosted or drained one
 * (current hitpoints are the Hitpoints skill's current level). Changed skills are sent once a tick by [flush].
 */
class PlayerSkills {
    private val tenths = IntArray(Skill.COUNT)
    private val current = IntArray(Skill.COUNT) { 1 }

    /** Starts all true: every skill is sent on the first flush. */
    private val changed = BooleanArray(Skill.COUNT) { true }

    init {
        // A new account has 10 Hitpoints (1,154 XP) and level 1 everywhere else.
        setLevel(Skill.HITPOINTS, NEW_HITPOINTS)
    }

    /** XP in tenths: 175 is 17.5 XP. */
    fun tenths(skill: Skill): Int = tenths[skill.id]

    /** Whole XP, as the client shows it. */
    fun xp(skill: Skill): Int = tenths[skill.id] / 10

    /** The level the XP gives. */
    fun baseLevel(skill: Skill): Int = Experience.levelForTenths(tenths[skill.id])

    /** The level after boosts and drains. */
    fun currentLevel(skill: Skill): Int = current[skill.id]

    /** Sets the XP to what [level] needs and the current level to match, as `::setlevel` does. */
    fun setLevel(skill: Skill, level: Int) {
        setTenths(skill, Experience.xpForLevel(level) * 10)
        setCurrentLevel(skill, level)
    }

    /** Sets the XP, leaving the current level as it is. */
    fun setTenths(skill: Skill, value: Int) {
        require(value in 0..Experience.MAX_TENTHS) { "XP must be 0 to 200,000,000" }
        if (tenths[skill.id] == value) return
        tenths[skill.id] = value
        changed[skill.id] = true
    }

    /** The client keeps a level in one unsigned byte. */
    fun setCurrentLevel(skill: Skill, level: Int) {
        require(level in 0..MAX_CURRENT_LEVEL) { "Current levels are 0 to $MAX_CURRENT_LEVEL, not $level" }
        if (current[skill.id] == level) return
        current[skill.id] = level
        changed[skill.id] = true
    }

    /**
     * Adds XP, capped at 200M, and returns the levels gained. Each level gained also raises the current level by one,
     * so a boost or drain carries over. Inferred from how live behaves when levelling while boosted; not measured.
     */
    fun addTenths(skill: Skill, amount: Int): Int {
        require(amount >= 0) { "XP can only be added, not $amount" }
        val before = baseLevel(skill)
        setTenths(skill, (tenths[skill.id].toLong() + amount).coerceAtMost(Experience.MAX_TENTHS.toLong()).toInt())
        val gained = baseLevel(skill) - before
        if (gained > 0) setCurrentLevel(skill, (current[skill.id] + gained).coerceAtMost(MAX_CURRENT_LEVEL))
        return gained
    }

    /** The wiki's combat level formula, from base levels. 3 for a new account, 126 with every combat skill at 99. */
    val combatLevel: Int
        get() {
            // Everything is multiplied by 40 so the 1/4 and 13/40 factors stay in whole numbers.
            val base = 10 * (baseLevel(Skill.DEFENCE) + baseLevel(Skill.HITPOINTS) + baseLevel(Skill.PRAYER) / 2)
            val melee = baseLevel(Skill.ATTACK) + baseLevel(Skill.STRENGTH)
            val ranged = baseLevel(Skill.RANGED) * 3 / 2
            val magic = baseLevel(Skill.MAGIC) * 3 / 2
            return (base + 13 * max(melee, max(ranged, magic))) / 40
        }

    /** Hands each skill changed since the last flush to [send], in id order, with its current level and whole XP. */
    fun flush(send: (skill: Skill, currentLevel: Int, xp: Int) -> Unit) {
        for (skill in Skill.entries) {
            if (!changed[skill.id]) continue
            changed[skill.id] = false
            send(skill, current[skill.id], xp(skill))
        }
    }

    /** Makes the next [flush] send every skill again. */
    fun resendAll() {
        changed.fill(true)
    }

    companion object {
        const val MAX_CURRENT_LEVEL = 255

        /** A new account's Hitpoints level; every other skill starts at 1. */
        const val NEW_HITPOINTS = 10

        /** The level [skill] starts at on a new account. */
        fun newLevel(skill: Skill): Int = if (skill == Skill.HITPOINTS) NEW_HITPOINTS else 1
    }
}
