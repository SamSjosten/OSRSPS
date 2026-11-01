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
import osrsps.game.combat.LootLines
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
import osrsps.game.skill.PickpocketLines
import osrsps.game.skill.SmithingLines
import osrsps.game.skill.ThievingLines
import osrsps.game.skill.WoodcuttingLines
import osrsps.game.social.ClanLines
import osrsps.game.social.FriendsChatLines
import osrsps.game.social.ReportLines
import osrsps.game.social.SocialLines
import osrsps.game.trade.TradeLines

/**
 * Every conversation's lines, which the dialogue file is read and written against. Each new [Lines] object goes here,
 * one a line in alphabetical order, so threads adding objects at once don't touch the same line (CONTRIBUTING.md).
 */
object DialogueLines {
    val ALL: List<Lines> = listOf(
        AbbotLangleyLines,
        AbigailaLines,
        AeonisigLines,
        AereckLines,
        AggieLines,
        AlKharidShopAssistantLines,
        AlKharidShopKeeperLines,
        AliMorrisaneLines,
        AltarLines,
        ApothecaryLines,
        ApothecaryPotionsLines,
        ArenaStubLines,
        ArisLines,
        ArthurLines,
        AsyffLines,
        AuburyLines,
        AxeLogsLines,
        BankGuardLines,
        BankLines,
        BankerLines,
        BankerTutorLines,
        BaraekLines,
        BarfyBillLines,
        BennyLines,
        BirdNestLines,
        BlueMoonBartenderLines,
        BlueMoonCookLines,
        BobLines,
        BonesLines,
        BorderGuardLines,
        BrotherAlthricLines,
        BrotherJeredLines,
        CamelLines,
        CampArcherLines,
        CampMonkLines,
        CampWarriorLines,
        CampWizardLines,
        CandleSellerLines,
        CharlieLines,
        ClanLines,
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
        DancingDonkeyBartenderLines,
        DiangoLines,
        DimintheisLines,
        DommikLines,
        DonieLines,
        DoomsayerLines,
        DorisLines,
        DrHarlowLines,
        DraulLeptocLines,
        DraynorSearchLines,
        DropLines,
        DuckLines,
        DuelGuideLines,
        DukeLines,
        EdgevilleClosedLines,
        EdgevilleLines,
        EdgevilleMonkLines,
        EdgevilleShopAssistantLines,
        EdgevilleShopKeeperLines,
        EllamariaLines,
        EllisLines,
        ElsieLines,
        EquipmentLines,
        EvilDaveLines,
        Examines,
        FatherLawrenceLines,
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
        GertrudeLines,
        GillieLines,
        GrandExchangeLines,
        GuardCaptainLines,
        HansLines,
        HariLines,
        HarlanLines,
        HassanLines,
        HatiusLines,
        HaystackLines,
        HerbloreLines,
        HomeTeleportLines,
        HopperLines,
        HorvikLines,
        IffieLines,
        IrenaLines,
        IronmanTutorLines,
        JailLines,
        JohnathonLines,
        JollyBoarBartenderLines,
        JollyBoarCookLines,
        JonnyTheBeardLines,
        JulietLines,
        KarimLines,
        KebabLines,
        KingRoaldLines,
        LeelaLines,
        LevelUpLines,
        LootLines,
        LouieLegsLines,
        LoweLines,
        LucienLines,
        LumbridgeGuideLines,
        MageOfZamorakLines,
        MakeLines,
        ManLines,
        MillLines,
        MillieLines,
        MiningLines,
        MiningTutorLines,
        MissSchismLines,
        MorganLines,
        NedLines,
        NigelLines,
        OliviaLines,
        OsmanLines,
        OziachLines,
        PalaceLibraryLines,
        PerduLines,
        PhillipaLines,
        PickpocketLines,
        PlayingChildLines,
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
        ReportLines,
        RichardLines,
        RomeoLines,
        RottenPotatoLines,
        RovinLines,
        SearchLines,
        SettingsLines,
        ShantayGateLines,
        ShantayGuardLines,
        ShantayLines,
        SheepLines,
        ShilopLines,
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
        ThievingLines,
        TournamentGuideLines,
        TownCrierLines,
        TradeLines,
        TrampLines,
        UrhneyLines,
        VarrockSearchLines,
        VarrockShopKeeperLines,
        VarrockSwordshopLines,
        WestBankDoorLines,
        WiloughLines,
        WomLines,
        WomanLines,
        WoodcuttingLines,
        WoodsmanTutorLines,
        YarloLines,
        ZaffLines,
        ZekeLines,
    )
}

/** Sends [player] the game message [line] in the dialogue file's words, or its key. */
fun World.message(player: Player, line: Line, vararg values: Pair<String, Any>) = player.message(textFor(player).get(line, *values))
