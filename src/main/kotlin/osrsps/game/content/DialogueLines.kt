package osrsps.game.content

import osrsps.game.EquipmentLines
import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.SettingsLines
import osrsps.game.World
import osrsps.game.bank.BankLines
import osrsps.game.combat.CombatLines
import osrsps.game.combat.FoodLines
import osrsps.game.combat.PoisonLines
import osrsps.game.combat.PotionLines
import osrsps.game.combat.PrayerLines
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.shop.ShopLines
import osrsps.game.skill.BirdNestLines
import osrsps.game.skill.CookingLines
import osrsps.game.skill.CraftingLines
import osrsps.game.skill.FiremakingLines
import osrsps.game.skill.FishingLines
import osrsps.game.skill.FletchingLines
import osrsps.game.skill.LevelUpLines
import osrsps.game.skill.MakeLines
import osrsps.game.skill.MiningLines
import osrsps.game.skill.SmithingLines
import osrsps.game.skill.WoodcuttingLines
import osrsps.game.social.SocialLines

/** Every conversation's lines, which the dialogue file is read and written against. Each new [Lines] object goes here. */
object DialogueLines {
    val ALL: List<Lines> = listOf(
        HansLines, CookLines, DukeLines, ManLines, WomanLines, PerduLines, CraftingTutorLines, BankerLines, BankerTutorLines,
        // Gathering skills after woodcutting and firemaking, one a line.
        MiningLines,
        FishingLines,
        CookingLines,
        SmithingLines,
        // Crafting, fletching and herblore.
        MakeLines,
        FletchingLines,
        CraftingLines,
        DoomsayerLines, HatiusLines, LumbridgeGuideLines, HarlanLines, Examines, LevelUpLines, WoodcuttingLines, FoodLines,
        BirdNestLines, FiremakingLines, CombatLines, PrayerLines, GameLines, EquipmentLines, PotionLines, SettingsLines,
        // The raid's combat foundations, one a line.
        PoisonLines,
        // Rest of Lumbridge.
        ShopKeeperLines, ShopAssistantLines, BobLines, RamBartenderLines, ArthurLines, DonieLines, GeeLines, CookingTutorLines,
        SmithingApprenticeLines, WoodsmanTutorLines, AbigailaLines, IronmanTutorLines, FredLines, GillieLines, MillieLines,
        HopperLines, DuckLines, SheepLines, CropLines, DairyCowLines, MillLines, BonesLines, SearchLines, ClimbLines,
        BankLines, ShopLines, BarfyBillLines, UrhneyLines, CampWarriorLines, CampArcherLines, CampMonkLines, CampWizardLines,
        CandleSellerLines, MiningTutorLines, FishingTutorLines, AereckLines, PrayerTutorLines, NigelLines, CountCheckLines,
        AltarLines,
        // Social systems.
        SocialLines,
        // Al Kharid.
        BorderGuardLines,
        // Draynor Village.
        DraynorSearchLines, AxeLogsLines,
    )
}

/** Sends [player] the game message [line] in the dialogue file's words, or its key. */
fun World.message(player: Player, line: Line, vararg values: Pair<String, Any>) = player.message(textFor(player).get(line, *values))
