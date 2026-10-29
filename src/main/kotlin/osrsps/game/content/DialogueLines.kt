package osrsps.game.content

import osrsps.game.dialogue.Lines
import osrsps.game.skill.LevelUpLines

/** Every conversation's lines, which the dialogue file is read and written against. Each new [Lines] object goes here. */
object DialogueLines {
    val ALL: List<Lines> = listOf(
        HansLines, CookLines, DukeLines, ManLines, WomanLines, PerduLines, CraftingTutorLines, BankerLines, BankerTutorLines,
        DoomsayerLines, HatiusLines, LumbridgeGuideLines, HarlanLines, Examines, LevelUpLines,
    )
}
