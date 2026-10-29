package osrsps.game.skill

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos

/** A tool a gathering skill uses: an axe, a pickaxe. [anim] is the player's animation while using it. */
data class SkillTool(val obj: Int, val level: Int, val anim: Int)

/** What [SkillTools.best] found. */
sealed interface ToolCheck {
    data class Found(val tool: SkillTool) : ToolCheck

    /** The player has tools of this kind, but none their level can use; [level] is the lowest any of them needs. */
    data class TooLow(val level: Int) : ToolCheck

    data object None : ToolCheck
}

/** A skill's tools, best first: the bronze to crystal axes, say. */
class SkillTools(private val skill: Skill, tools: List<SkillTool>) {
    private val tools = tools.toList()
    private val ids = this.tools.map { it.obj }.toSet()

    init {
        require(ids.size == this.tools.size) { "A tool is listed twice for ${skill.displayName}" }
    }

    /** Whether [obj] is one of these tools. */
    operator fun contains(obj: Int): Boolean = obj in ids

    /**
     * The best of these tools [player] holds, in the backpack or the weapon slot, that their current [skill] level can
     * use. Live picks the best usable tool the same way; whether it counts boosted levels is inferred (boosts let you
     * use a tool early on live, the Wiki's tool pages say).
     */
    fun best(player: Player): ToolCheck {
        val level = player.skills.currentLevel(skill)
        val weapon = player.equipment[WearPos.WEAPON]?.id
        var lowest = Int.MAX_VALUE
        for (tool in tools) {
            if (weapon != tool.obj && player.inventory.count(tool.obj) == 0L) continue
            if (tool.level <= level) return ToolCheck.Found(tool)
            lowest = minOf(lowest, tool.level)
        }
        return if (lowest == Int.MAX_VALUE) ToolCheck.None else ToolCheck.TooLow(lowest)
    }
}
