package osrsps.game.skill

import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfSetHide
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import osrsps.game.Experience
import osrsps.game.Interfaces
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.dialogue.Chatbox
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Lines
import osrsps.game.script.QueueType
import osrsps.game.script.ScriptContext
import osrsps.game.script.ScriptRunner

/**
 * The level-up's words: a game message and the two lines of the level-up box. Their words are in the dialogue file, like
 * every other line of Jagex text (Sam's rule, 2026-10-29). `{article}` is "a" or "an", picked in code to suit the skill.
 */
object LevelUpLines : Lines("levelup", "Skills") {
    val MESSAGE = line("message", "the chat message on reaching a level", "skill", "level")
    val TITLE = line("title", "the level-up box's first line", "article", "skill")
    val BODY = line("body", "the level-up box's second line", "skill", "level")
    val COMBAT_MESSAGE = line("combat_message", "the chat message on reaching a combat level", "level")
    val COMBAT_TITLE = line("combat_title", "the combat level-up box's first line")
    val COMBAT_BODY = line("combat_body", "the combat level-up box's second line", "level")
}

/**
 * Levels a skill's level-up unlocks something at, from the cache's skill_features table (213), whose skill column holds
 * (stat, level, ...) rows; the client's levelup_chatbox_transmit script reads the same rows. An empty set for a skill
 * means no unlock jingle plays for it.
 */
class SkillUnlocks(private val levels: Map<Skill, Set<Int>>) {
    fun unlocks(skill: Skill, level: Int): Boolean = level in levels[skill].orEmpty()

    companion object {
        val NONE = SkillUnlocks(emptyMap())
    }
}

/**
 * What a level-up shows and plays: the chat message, the fireworks, the skill's jingle and the levelup_display box in the
 * chatbox with the skill's icon, then a message, jingle and box for the combat level when that went up too.
 *
 * A skill's XP going past a level marks it ([osrsps.game.PlayerSkills.addTenths]); each tick's script phase queues one
 * level-up per marked skill, which shows the level reached. The 241 interface, its components and the spotanim names
 * are read from the cache's gamevals and osrs-dumps; the jingle ids from the OSRS Wiki's jingle pages. The order of
 * message, fireworks, jingle and box is the 2004Scape project's levelup script, the queue type and the fireworks'
 * height are guessed, and the "Level N Skill: unlock" chat lines live shows after some level-ups are not sent yet.
 */
class LevelUps(
    private val interfaces: Interfaces,
    private val scripts: ScriptRunner,
    private val unlocks: SkillUnlocks,
    private val words: (Player) -> DialogueText,
) {
    /**
     * Queues a level-up for every skill [player] has levelled since the last call, then one for the combat level if
     * that went up with them.
     */
    fun process(player: Player) {
        for (skill in player.skills.takeAdvanced()) {
            queueOnce(player, "levelup_${skill.name.lowercase()}") { announce(skill) }
        }
        val combatFrom = player.skills.takeCombatFrom() ?: return
        if (player.skills.combatLevel > combatFrom) {
            // Guessed: the combat level's box follows the skill's, as it does on live.
            queueOnce(player, "levelup_combat") { announceCombat() }
        }
    }

    /** A level-up still waiting (behind an open bank, say) shows the level reached when it runs, so one is enough. */
    private fun queueOnce(player: Player, name: String, script: suspend ScriptContext.() -> Unit) {
        if (player.scripts.queued.none { it.name == name }) scripts.queue(player, name, QueueType.NORMAL, 0, script)
    }

    private suspend fun ScriptContext.announce(skill: Skill) {
        val level = player.skills.baseLevel(skill)
        val text = words(player)
        val name = skill.displayName
        player.message(text[LevelUpLines.MESSAGE, "skill" to name, "level" to level])
        fireworks(level)
        jingle(skill, level)?.let(player::jingle)
        show(text[LevelUpLines.TITLE, "article" to article(skill), "skill" to name], text[LevelUpLines.BODY, "skill" to name, "level" to level], layer(skill))
    }

    private suspend fun ScriptContext.announceCombat() {
        val level = player.skills.combatLevel
        val text = words(player)
        player.message(text[LevelUpLines.COMBAT_MESSAGE, "level" to level])
        player.jingle(JINGLE_COMBAT)
        show(text[LevelUpLines.COMBAT_TITLE], text[LevelUpLines.COMBAT_BODY, "level" to level], LAYER_COMBAT)
    }

    private fun ScriptContext.fireworks(level: Int) {
        player.spotanim(if (level == Experience.MAX_LEVEL) SPOTANIM_99 else SPOTANIM, FIREWORKS_HEIGHT)
    }

    private suspend fun ScriptContext.show(title: String, body: String, layer: Int) {
        interfaces.openChatModal(player, INTERFACE, Chatbox.UNCLAMP_FIXED)
        val session = player.session
        session.queue(IfSetText(INTERFACE, TEXT1, title))
        session.queue(IfSetText(INTERFACE, TEXT2, body))
        session.queue(IfSetText(INTERFACE, CONTINUE, Chatbox.CONTINUE_TEXT))
        session.queue(IfSetEventsV2(INTERFACE, CONTINUE, -1, -1, Chatbox.EVENT_PAUSE_BUTTON, 0))
        session.queue(IfSetHide(INTERFACE, layer, false))
        pauseButton(INTERFACE, CONTINUE)
        interfaces.closeChatModal(player)
    }

    /**
     * The skill's jingle: the unlock version when the level unlocks something, or the skill's own variants. Null for
     * Sailing, whose jingle id isn't known yet.
     */
    fun jingle(skill: Skill, level: Int): Int? = when (skill) {
        Skill.AGILITY -> 28
        Skill.CONSTRUCTION -> if (level % 10 == 0) 32 else 31
        Skill.HITPOINTS -> if (level >= 50) 48 else 47
        Skill.HUNTER -> if (level % 2 == 0) 49 else 50
        Skill.STRENGTH -> if (level >= 50) 66 else 65
        Skill.SAILING -> null
        else -> {
            val base = JINGLES.getValue(skill)
            if (unlocks.unlocks(skill, level)) base + 1 else base
        }
    }

    companion object {
        /** levelup_display. */
        const val INTERFACE = 233
        const val TEXT1 = 1
        const val TEXT2 = 2
        const val CONTINUE = 3

        /** levelup_display:combat. */
        const val LAYER_COMBAT = 55

        /** levelup_anim and levelup_99_anim. */
        const val SPOTANIM = 199
        const val SPOTANIM_99 = 1388

        /** Guessed from the 2004Scape script's spotanim_pl height. */
        const val FIREWORKS_HEIGHT = 124

        /** Combat Level Up! (cache id 299 on the Wiki's jingle list). */
        const val JINGLE_COMBAT = 299

        /** The skill table's database row used by skill_features (table 213). */
        const val SKILL_FEATURES_TABLE = 213

        private val COMBAT_SKILLS = setOf(
            Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS, Skill.RANGED, Skill.PRAYER, Skill.MAGIC,
        )

        /**
         * Each skill's "Level Up!" jingle; its "(Unlocks)" version is the next id. Read from the Wiki's jingle pages
         * (2026-10-29): Agility 28 to Hunter 50 from User:Fixer/jingleland, Farming 10, and Magic 51, Mining 53, Smithing
         * 63, Woodcutting 69 from their own pages. The ids run in alphabetical order, so Prayer, Ranged, Runecraft,
         * Slayer and Thieving are inferred from where they fall between those.
         */
        private val JINGLES: Map<Skill, Int> = mapOf(
            Skill.ATTACK to 29, Skill.COOKING to 33, Skill.CRAFTING to 35, Skill.DEFENCE to 37, Skill.FARMING to 10,
            Skill.FIREMAKING to 39, Skill.FISHING to 41, Skill.FLETCHING to 43, Skill.HERBLORE to 45, Skill.MAGIC to 51,
            Skill.MINING to 53, Skill.PRAYER to 55, Skill.RANGED to 57, Skill.RUNECRAFT to 59, Skill.SLAYER to 61,
            Skill.SMITHING to 63, Skill.THIEVING to 67, Skill.WOODCUTTING to 69,
        )

        /** Each skill's layer in levelup_display, which holds its icon; all start hidden. */
        fun layer(skill: Skill): Int = when (skill) {
            Skill.AGILITY -> 4
            Skill.ATTACK -> 6
            Skill.CONSTRUCTION -> 9
            Skill.COOKING -> 12
            Skill.CRAFTING -> 14
            Skill.DEFENCE -> 17
            Skill.FARMING -> 19
            Skill.FIREMAKING -> 21
            Skill.FISHING -> 23
            Skill.FLETCHING -> 25
            Skill.HERBLORE -> 28
            Skill.HITPOINTS -> 30
            Skill.HUNTER -> 32
            Skill.MAGIC -> 34
            Skill.MINING -> 36
            Skill.PRAYER -> 38
            Skill.RANGED -> 40
            Skill.RUNECRAFT -> 43
            Skill.SLAYER -> 45
            Skill.SMITHING -> 47
            Skill.STRENGTH -> 49
            Skill.THIEVING -> 51
            Skill.WOODCUTTING -> 53
            Skill.SAILING -> 57
        }

        fun article(skill: Skill): String = if (skill.displayName.first().lowercaseChar() in "aeiou") "an" else "a"
    }
}
