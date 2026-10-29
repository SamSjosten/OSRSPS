package osrsps.game.content

import osrsps.game.combat.FoodLines
import osrsps.game.dialogue.Lines
import osrsps.game.skill.BirdNestLines
import osrsps.game.skill.LevelUpLines
import osrsps.game.skill.WoodcuttingLines

/** Every conversation's lines, which the dialogue file is read and written against. Each new [Lines] object goes here. */
object DialogueLines {
    val ALL: List<Lines> = listOf(
        HansLines, CookLines, DukeLines, ManLines, WomanLines, PerduLines, CraftingTutorLines, BankerLines, BankerTutorLines,
        DoomsayerLines, HatiusLines, LumbridgeGuideLines, HarlanLines, Examines, LevelUpLines, WoodcuttingLines, FoodLines,
        BirdNestLines,
        // Rest of Lumbridge.
        ShopKeeperLines, ShopAssistantLines, BobLines, RamBartenderLines, ArthurLines, DonieLines, GeeLines, CookingTutorLines,
        SmithingApprenticeLines, WoodsmanTutorLines, AbigailaLines, IronmanTutorLines, FredLines, GillieLines, MillieLines,
        HopperLines, DuckLines,
    )
}
