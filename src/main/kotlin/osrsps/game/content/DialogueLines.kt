package osrsps.game.content

import osrsps.game.EquipmentLines
import osrsps.game.GameLines
import osrsps.game.SettingsLines
import osrsps.game.combat.CombatLines
import osrsps.game.combat.FoodLines
import osrsps.game.combat.PrayerLines
import osrsps.game.dialogue.Lines
import osrsps.game.skill.BirdNestLines
import osrsps.game.skill.FiremakingLines
import osrsps.game.skill.FishingLines
import osrsps.game.skill.LevelUpLines
import osrsps.game.skill.MiningLines
import osrsps.game.skill.WoodcuttingLines

/** Every conversation's lines, which the dialogue file is read and written against. Each new [Lines] object goes here. */
object DialogueLines {
    val ALL: List<Lines> = listOf(
        HansLines, CookLines, DukeLines, ManLines, WomanLines, PerduLines, CraftingTutorLines, BankerLines, BankerTutorLines,
        // Gathering skills after woodcutting and firemaking, one a line.
        MiningLines,
        FishingLines,
        DoomsayerLines, HatiusLines, LumbridgeGuideLines, HarlanLines, Examines, LevelUpLines, WoodcuttingLines, FoodLines,
        BirdNestLines, FiremakingLines, CombatLines, PrayerLines, GameLines, EquipmentLines, SettingsLines,
        // Rest of Lumbridge.
        ShopKeeperLines, ShopAssistantLines, BobLines, RamBartenderLines, ArthurLines, DonieLines, GeeLines, CookingTutorLines,
        SmithingApprenticeLines, WoodsmanTutorLines, AbigailaLines, IronmanTutorLines, FredLines, GillieLines, MillieLines,
        HopperLines, DuckLines,
    )
}
