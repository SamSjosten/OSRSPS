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
import osrsps.game.ge.GrandExchangeLines
import osrsps.game.ge.PriceCheckerLines
import osrsps.game.shop.ShopLines
import osrsps.game.skill.BirdNestLines
import osrsps.game.skill.CookingLines
import osrsps.game.skill.CraftingLines
import osrsps.game.skill.FiremakingLines
import osrsps.game.skill.FishingLines
import osrsps.game.skill.FletchingLines
import osrsps.game.skill.HerbloreLines
import osrsps.game.skill.LevelUpLines
import osrsps.game.skill.MakeLines
import osrsps.game.skill.MiningLines
import osrsps.game.skill.SmithingLines
import osrsps.game.skill.WoodcuttingLines
import osrsps.game.social.FriendsChatLines
import osrsps.game.social.SocialLines
import osrsps.game.trade.TradeLines

/**
 * Every conversation's lines, which the dialogue file is read and written against. Each new [Lines] object goes here,
 * one a line in alphabetical order, so threads adding objects at once don't touch the same line (CONTRIBUTING.md).
 */
object DialogueLines {
    val ALL: List<Lines> = listOf(
        AbigailaLines,
        AereckLines,
        AggieLines,
        AlKharidShopAssistantLines,
        AlKharidShopKeeperLines,
        AliMorrisaneLines,
        AltarLines,
        ArthurLines,
        AxeLogsLines,
        BankLines,
        BankerLines,
        BankerTutorLines,
        BarfyBillLines,
        BirdNestLines,
        BobLines,
        BonesLines,
        BorderGuardLines,
        CamelLines,
        CampArcherLines,
        CampMonkLines,
        CampWarriorLines,
        CampWizardLines,
        CandleSellerLines,
        ClimbLines,
        CombatLines,
        CookLines,
        CookingLines,
        CookingTutorLines,
        CountCheckLines,
        CraftingLines,
        CraftingTutorLines,
        CropLines,
        DairyCowLines,
        DiangoLines,
        DommikLines,
        DonieLines,
        DoomsayerLines,
        DraynorSearchLines,
        DuckLines,
        DukeLines,
        EllisLines,
        EquipmentLines,
        Examines,
        FiremakingLines,
        FishingLines,
        FishingTutorLines,
        FletchingLines,
        FoodLines,
        FortunatoLines,
        FredLines,
        FriendsChatLines,
        GameLines,
        GeeLines,
        GemTraderLines,
        GillieLines,
        GrandExchangeLines,
        HansLines,
        HarlanLines,
        HassanLines,
        HatiusLines,
        HerbloreLines,
        HopperLines,
        IronmanTutorLines,
        KarimLines,
        KebabLines,
        LevelUpLines,
        LouieLegsLines,
        LumbridgeGuideLines,
        MakeLines,
        ManLines,
        MillLines,
        MillieLines,
        MiningLines,
        MiningTutorLines,
        NedLines,
        NigelLines,
        OliviaLines,
        OsmanLines,
        PerduLines,
        PoisonLines,
        PotionLines,
        PrayerLines,
        PrayerTutorLines,
        PriceCheckerLines,
        PrinceAliLines,
        RamBartenderLines,
        RanaelLines,
        RottenPotatoLines,
        SearchLines,
        SettingsLines,
        SheepLines,
        ShopAssistantLines,
        ShopKeeperLines,
        ShopLines,
        SilkTraderLines,
        SmithingApprenticeLines,
        SmithingLines,
        SocialLines,
        TanningLines,
        TradeLines,
        UrhneyLines,
        WomanLines,
        WoodcuttingLines,
        WoodsmanTutorLines,
        ZekeLines,
    )
}

/** Sends [player] the game message [line] in the dialogue file's words, or its key. */
fun World.message(player: Player, line: Line, vararg values: Pair<String, Any>) = player.message(textFor(player).get(line, *values))
