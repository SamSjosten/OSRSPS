package osrsps.game.content

import osrsps.game.DropLines
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
        AeonisigLines,
        AereckLines,
        AlKharidShopAssistantLines,
        AlKharidShopKeeperLines,
        AliMorrisaneLines,
        AltarLines,
        ApothecaryLines,
        ApothecaryPotionsLines,
        ArenaStubLines,
        ArthurLines,
        AsyffLines,
        AuburyLines,
        AxeLogsLines,
        BankLines,
        BankerLines,
        BankerTutorLines,
        BaraekLines,
        BarfyBillLines,
        BennyLines,
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
        DalburLines,
        DiangoLines,
        DommikLines,
        DonieLines,
        DoomsayerLines,
        DraynorSearchLines,
        DropLines,
        DuckLines,
        DuelGuideLines,
        DukeLines,
        EllamariaLines,
        EllisLines,
        EquipmentLines,
        Examines,
        FerrnookLines,
        FiremakingLines,
        FishingLines,
        FishingTutorLines,
        FletchingLines,
        FoodLines,
        FortunatoLines,
        FredLines,
        FriendsChatLines,
        FronkLines,
        GameLines,
        GeClerkLines,
        GeeLines,
        GemTraderLines,
        GillieLines,
        GrandExchangeLines,
        GuardCaptainLines,
        HansLines,
        HarlanLines,
        HassanLines,
        HatiusLines,
        HerbloreLines,
        HopperLines,
        HorvikLines,
        IrenaLines,
        IronmanTutorLines,
        KarimLines,
        KebabLines,
        KingRoaldLines,
        LevelUpLines,
        LouieLegsLines,
        LoweLines,
        LumbridgeGuideLines,
        MakeLines,
        ManLines,
        MillLines,
        MillieLines,
        MiningLines,
        MiningTutorLines,
        NigelLines,
        OliviaLines,
        OsmanLines,
        PalaceLibraryLines,
        PerduLines,
        PoisonLines,
        PortSarimJailLines,
        PotionLines,
        PrayerLines,
        PrayerTutorLines,
        PriceCheckerLines,
        PrinceAliLines,
        PrysinLines,
        RamBartenderLines,
        RanaelLines,
        ReldoLines,
        RottenPotatoLines,
        RovinLines,
        SearchLines,
        SettingsLines,
        ShantayGateLines,
        ShantayGuardLines,
        ShantayLines,
        SheepLines,
        ShopAssistantLines,
        ShopKeeperLines,
        ShopLines,
        SilkTraderLines,
        SmithingApprenticeLines,
        SmithingLines,
        SocialLines,
        StrayDogLines,
        TanningLines,
        TeaSellerLines,
        ThessaliaLines,
        TournamentGuideLines,
        TradeLines,
        UrhneyLines,
        VarrockSearchLines,
        VarrockShopKeeperLines,
        VarrockSwordshopLines,
        WestBankDoorLines,
        WomanLines,
        WoodcuttingLines,
        WoodsmanTutorLines,
        ZaffLines,
        ZekeLines,
    )
}

/** Sends [player] the game message [line] in the dialogue file's words, or its key. */
fun World.message(player: Player, line: Line, vararg values: Pair<String, Any>) = player.message(textFor(player).get(line, *values))
