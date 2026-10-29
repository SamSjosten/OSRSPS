package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.content.DonieAndGee.talk
import osrsps.game.content.LumbridgeShopkeepers.bob
import osrsps.game.content.LumbridgeShopkeepers.keeper
import osrsps.game.content.LumbridgeTownsfolk.abigaila
import osrsps.game.content.LumbridgeTownsfolk.ironmanTutor
import osrsps.game.content.LumbridgeTutors.cookingTutor
import osrsps.game.content.LumbridgeTutors.smithingApprentice
import osrsps.game.content.LumbridgeTutors.woodsmanTutor
import osrsps.game.content.ShearedRam.arthur
import osrsps.game.content.ShearedRam.bartender

/** Rest of Lumbridge step 5: the town centre's conversations, with no dialogue file, so every line shows its key. */
class TownCentreTalkTest {
    private val h = DialogueHarness()

    /** What the talk handed over, in order. */
    private val given = mutableListOf<Int>()
    private val give: (Player, Int) -> Unit = { _, obj -> given += obj }

    private val coins = ObjType(ShearedRam.COINS, "Coins", stackable = true)

    @Test
    fun `the shop keeper opens the shop or is told no thanks`() {
        var trade = true
        h.talk { trade = with(LumbridgeShopkeepers) { keeper(ShopKeeperLines) } }
        h.expect("lumbridge_shop_keeper.help")
        h.menu("lumbridge_shop_keeper.selling", "lumbridge_shop_keeper.no_thanks")
        h.pick(2)
        h.expect("lumbridge_shop_keeper.no_thanks")
        h.ends()
        assertFalse(trade)

        h.talk { trade = with(LumbridgeShopkeepers) { keeper(ShopAssistantLines) } }
        h.expect("lumbridge_shop_assistant.help")
        h.menu("lumbridge_shop_assistant.selling", "lumbridge_shop_assistant.no_thanks")
        h.pick(1)
        assertTrue(trade)
    }

    @Test
    fun `Bob opens with his menu, and asking what he sells opens the shop`() {
        var trade = false
        h.talk { trade = with(LumbridgeShopkeepers) { bob() } }
        h.menu("bob.quest", "bob.sell", "bob.repair")
        h.pick(2)
        h.expect("bob.sell", "bob.axes")
        h.next()
        assertTrue(trade)

        h.talk { trade = with(LumbridgeShopkeepers) { bob() } }
        h.menu("bob.quest", "bob.sell", "bob.repair")
        h.pick(3)
        h.expect("bob.repair", "bob.repair_reply")
        h.ends()
        assertFalse(trade)
    }

    @Test
    fun `the Bartender sells a beer for two coins, and turns away a player without them`() {
        h.player.inventory.add(coins, 3)
        h.talk { with(ShearedRam) { bartender(give) } }
        h.expect("ram_bartender.welcome")
        h.menu("ram_bartender.beer", "ram_bartender.rumours", "ram_bartender.fine")
        h.pick(1)
        h.expect("ram_bartender.beer", "ram_bartender.price")
        h.next()
        assertEquals(listOf(ShearedRam.BEER_OBJ), given)
        assertEquals(1, h.player.inventory.count(ShearedRam.COINS))

        h.talk { with(ShearedRam) { bartender(give) } }
        h.expect("ram_bartender.welcome")
        h.menu("ram_bartender.beer", "ram_bartender.rumours", "ram_bartender.fine")
        h.pick(1)
        h.expect("ram_bartender.beer", "ram_bartender.price", "ram_bartender.cant_afford")
        h.ends()
        assertEquals(1, given.size)
    }

    @Test
    fun `Arthur's clue types come on three pages, and his menu comes back after each topic`() {
        h.talk { with(ShearedRam) { arthur() } }
        h.expect("arthur.greeting")
        val first = arrayOf("arthur.beginner", "arthur.advanced", "arthur.types", "arthur.toggle", "arthur.goodbye")
        h.menu(*first)
        h.pick(3)
        h.menu("arthur.anagrams", "arthur.challenge", "arthur.cipher", "arthur.coordinate", "arthur.more")
        h.pick(5)
        h.menu("arthur.cryptic", "arthur.emote", "arthur.hot_cold", "arthur.puzzle", "arthur.more")
        h.pick(2)
        h.expect("arthur.emote_1", "arthur.emote_2", "arthur.emote_3", "arthur.emote_4", "arthur.emote_5")
        h.menu("arthur.cryptic", "arthur.emote", "arthur.hot_cold", "arthur.puzzle", "arthur.more")
        h.pick(5)
        h.menu("arthur.map", "arthur.fairy_ring", "arthur.more", "arthur.something_else")
        h.pick(4)
        h.menu(*first)
        h.pick(4)
        // Vesseled clues start on: Arthur offers to stop them.
        h.expect("arthur.stop_ask", "arthur.vessels_stop")
        h.menu("arthur.stop", "arthur.nevermind")
        h.pick(1)
        h.expect("arthur.stop", "arthur.stopped", "arthur.anything_else")
        h.menu(*first)
        h.pick(5)
        h.expect("arthur.goodbye", "arthur.so_long")
        h.ends()
    }

    @Test
    fun `Gee has one more line than Donie about small monsters, and every quest is done`() {
        for ((lines, extra) in listOf(DonieLines to emptyList(), GeeLines to listOf("gee.small_monsters"))) {
            val p = lines.prefix
            h.talk { with(DonieAndGee) { talk(lines, 2) } }
            h.expect("$p.hello")
            h.menu("$p.whats_up", "$p.quests", "$p.stick")
            h.pick(2)
            h.expect("$p.know_quests", "$p.what_kind")
            h.menu("$p.fight", "$p.easy", "$p.thinker", "$p.all_kinds", "$p.another_time")
            h.pick(1)
            h.expect("$p.dangerous")
            h.menu("$p.demons", "$p.vampyres", "$p.small", "$p.another_time")
            h.pick(3)
            h.expect(*(listOf("$p.small", "$p.small_isnt", "$p.yes_it_can", "$p.point_taken") + extra + listOf("$p.imps", "$p.imps_done", "$p.imps_will_be")).toTypedArray())
            h.ends()
        }
    }

    @Test
    fun `Where am I leads to a second menu`() {
        h.talk { with(DonieAndGee) { talk(DonieLines, 4) } }
        h.expect("donie.hello")
        h.menu("donie.where_am_i", "donie.how_are_you", "donie.quests", "donie.shoe_lace")
        h.pick(1)
        h.expect("donie.where_am_i", "donie.town")
        h.menu("donie.how_are_you", "donie.know_quests", "donie.shoe_lace")
        h.pick(3)
        h.expect("donie.shoe_lace", "donie.not_untied", "donie.youre_right", "donie.fool")
        h.ends()
    }

    @Test
    fun `the Cooking tutor's training talk goes by Cooking level`() {
        h.talk { with(LumbridgeTutors) { cookingTutor() } }
        h.expect("cooking_tutor.hello", "cooking_tutor.greeting")
        h.menu("cooking_tutor.train", "cooking_tutor.kinds", "cooking_tutor.no_thanks")
        h.pick(1)
        h.expect(
            "cooking_tutor.train", "cooking_tutor.low_1", "cooking_tutor.low_2", "cooking_tutor.range_hint", "cooking_tutor.low_3",
            "cooking_tutor.cooks_range", "cooking_tutor.low_4", "cooking_tutor.low_5", "cooking_tutor.low_6", "cooking_tutor.bank_hint",
            "cooking_tutor.anything_else",
        )
        assertEquals(listOf(LumbridgeTutors.RANGE_ICON, LumbridgeTutors.BANK_ICON), h.boxObjs)
        h.menu("cooking_tutor.train", "cooking_tutor.kinds", "cooking_tutor.no_thanks")
        h.pick(2)
        h.expect("cooking_tutor.kinds", "cooking_tutor.many", "cooking_tutor.which")
        h.menu("cooking_tutor.fish", "cooking_tutor.brewing", "cooking_tutor.vegetables", "cooking_tutor.pies")
        h.pick(2)
        h.expect("cooking_tutor.brewing", "cooking_tutor.brewing_hint", "cooking_tutor.brewing_1", "cooking_tutor.brewing_2", "cooking_tutor.brewing_more")
        h.menu("cooking_tutor.train", "cooking_tutor.kinds", "cooking_tutor.no_thanks")
        h.pick(3)
        h.expect("cooking_tutor.no_thanks", "cooking_tutor.come_back")
        h.ends()

        h.player.skills.setLevel(Skill.COOKING, 99)
        h.talk { with(LumbridgeTutors) { cookingTutor() } }
        h.expect("cooking_tutor.hello", "cooking_tutor.greeting")
        h.menu("cooking_tutor.train", "cooking_tutor.kinds", "cooking_tutor.no_thanks")
        h.pick(1)
        h.expect("cooking_tutor.train", "cooking_tutor.master", "cooking_tutor.pizza", "cooking_tutor.potatoes", "cooking_tutor.curry", "cooking_tutor.anything_else")
    }

    @Test
    fun `the Smithing apprentice sends a player with no ore to the mining tutor`() {
        h.talk { with(LumbridgeTutors) { smithingApprentice() } }
        h.expect("smithing_apprentice.teach", "smithing_apprentice.furnace_hint", "smithing_apprentice.need_ore", "smithing_apprentice.mining_hint")
        h.ends()
        assertEquals(listOf(LumbridgeTutors.FURNACE_ICON, LumbridgeTutors.MINING_TUTOR_ICON), h.boxObjs)

        h.player.inventory.add(ObjType(LumbridgeTutors.ORES.first(), "Copper ore"), 1)
        h.talk { with(LumbridgeTutors) { smithingApprentice() } }
        h.expect(
            "smithing_apprentice.teach", "smithing_apprentice.furnace_hint", "smithing_apprentice.have_ore", "smithing_apprentice.click_furnace",
            "smithing_apprentice.full_inventory", "smithing_apprentice.bank_hint", "smithing_apprentice.hammer", "smithing_apprentice.rusted",
            "smithing_apprentice.varrock",
        )
        h.ends()

        h.player.skills.setLevel(Skill.SMITHING, 40)
        h.talk { with(LumbridgeTutors) { smithingApprentice() } }
        h.expect("smithing_apprentice.what_next", "smithing_apprentice.smithing_tutor", "smithing_apprentice.smithing_tutor_hint", "smithing_apprentice.smelt")
        h.ends()
    }

    @Test
    fun `the Woodsman tutor hands a beginner an axe and a tinderbox`() {
        h.talk { with(LumbridgeTutors) { woodsmanTutor(give) } }
        h.menu("woodsman_tutor.basics", "woodsman_tutor.cape", "woodsman_tutor.goodbye")
        h.pick(1)
        h.expect(
            "woodsman_tutor.basics", "woodsman_tutor.tree_hint", "woodsman_tutor.have_an_axe", "woodsman_tutor.likely_tree",
            "woodsman_tutor.full_inventory", "woodsman_tutor.bank_hint", "woodsman_tutor.lost_tinderbox", "woodsman_tutor.click_tinderbox",
        )
        h.ends()
        assertEquals(listOf(LumbridgeTutors.BRONZE_AXE, LumbridgeTutors.TINDERBOX), given)
    }

    @Test
    fun `the Woodsman tutor hands nothing to a beginner with an axe and a tinderbox in the bank`() {
        // The free axe sold to Bob for coins from nothing while only the bag and worn items counted (second exploit
        // review, 29 Oct 2026).
        h.player.bank.add(ObjType(LumbridgeTutors.BRONZE_AXE, "Bronze axe"), 1)
        h.player.bank.add(ObjType(LumbridgeTutors.TINDERBOX, "Tinderbox"), 1)
        h.talk { with(LumbridgeTutors) { woodsmanTutor(give) } }
        h.menu("woodsman_tutor.basics", "woodsman_tutor.cape", "woodsman_tutor.goodbye")
        h.pick(1)
        h.expect(
            "woodsman_tutor.basics", "woodsman_tutor.tree_hint", "woodsman_tutor.likely_tree",
            "woodsman_tutor.full_inventory", "woodsman_tutor.bank_hint", "woodsman_tutor.click_tinderbox",
        )
        h.ends()
        assertEquals(emptyList(), given)
    }

    @Test
    fun `the Woodsman tutor's trees menu has magic trees from level 29`() {
        h.player.skills.setLevel(Skill.WOODCUTTING, 40)
        h.player.inventory.add(ObjType(LumbridgeTutors.BRONZE_AXE, "Bronze axe"), 1)
        h.talk { with(LumbridgeTutors) { woodsmanTutor(give) } }
        val top = arrayOf("woodsman_tutor.advice", "woodsman_tutor.trees", "woodsman_tutor.cape", "woodsman_tutor.goodbye")
        h.menu(*top)
        h.pick(2)
        h.expect("woodsman_tutor.trees")
        h.menu("woodsman_tutor.oak_willow", "woodsman_tutor.maple_yew", "woodsman_tutor.magic", "woodsman_tutor.axes", "woodsman_tutor.go_back")
        h.pick(3)
        h.expect("woodsman_tutor.magic_hint", "woodsman_tutor.hollow")
        h.menu("woodsman_tutor.oak_willow", "woodsman_tutor.maple_yew", "woodsman_tutor.magic", "woodsman_tutor.axes", "woodsman_tutor.go_back")
        h.pick(5)
        h.menu(*top)
        h.pick(1)
        // Carrying an axe, so none is handed over.
        h.expect("woodsman_tutor.advice", "woodsman_tutor.better", "woodsman_tutor.also", "woodsman_tutor.look_out")
        h.menu(*top)
        h.pick(4)
        h.expect("woodsman_tutor.goodbye")
        h.ends()
        assertEquals(emptyList(), given)
    }

    @Test
    fun `at 99 Woodcutting the tutor sells the cape and hood for 99,000 coins`() {
        h.player.skills.setLevel(Skill.WOODCUTTING, 99)
        h.player.inventory.add(coins, 100_000)
        h.talk { with(LumbridgeTutors) { woodsmanTutor(give) } }
        h.expect("woodsman_tutor.master_1", "woodsman_tutor.master_2")
        h.menu("woodsman_tutor.no_thanks", "woodsman_tutor.yes_please")
        h.pick(2)
        h.expect("woodsman_tutor.yes_please", "woodsman_tutor.price")
        h.menu("woodsman_tutor.too_rich", "woodsman_tutor.no_problem")
        h.pick(2)
        h.expect("woodsman_tutor.no_problem", "woodsman_tutor.sold")
        h.ends()
        assertEquals(listOf(LumbridgeTutors.WOODCUTTING_CAPE, LumbridgeTutors.WOODCUTTING_HOOD), given)
        assertEquals(1_000, h.player.inventory.count(LumbridgeTutors.COINS))
    }

    @Test
    fun `Abigaila tells her story, and won't let a player carrying anything scry`() {
        h.player.inventory.add(coins, 1)
        h.talk { with(LumbridgeTownsfolk) { abigaila() } }
        h.expect("abigaila.help")
        h.menu("abigaila.who", "abigaila.scry", "abigaila.leave")
        h.pick(1)
        h.expect(
            "abigaila.who", "abigaila.story_1", "abigaila.story_2", "abigaila.story_3", "abigaila.story_4", "abigaila.story_5",
            "abigaila.story_6", "abigaila.sob", "abigaila.story_7", "abigaila.story_8", "abigaila.free", "abigaila.story_9",
            "abigaila.husband", "abigaila.pass_time",
        )
        h.menu("abigaila.who", "abigaila.scry", "abigaila.leave")
        h.pick(2)
        h.expect("abigaila.scry", "abigaila.empty_first")
        h.ends()
    }

    @Test
    fun `the Ironman tutor greets the player by name and has no armour for them`() {
        h.talk { with(LumbridgeTownsfolk) { ironmanTutor() } }
        h.expect("ironman_tutor.greeting name=Zezima")
        h.menu("ironman_tutor.tell_me", "ironman_tutor.review", "ironman_tutor.armour", "ironman_tutor.fine")
        h.pick(3)
        h.expect("ironman_tutor.armour", "ironman_tutor.not_ironman")
        h.ends()
    }

    @Test
    fun `every town centre NPC that talks has an examine`() {
        val ids = listOf(
            LumbridgeShopkeepers.SHOP_KEEPER, LumbridgeShopkeepers.SHOP_ASSISTANT, LumbridgeShopkeepers.BOB, ShearedRam.BARTENDER,
            ShearedRam.ARTHUR, DonieAndGee.DONIE, DonieAndGee.GEE, LumbridgeTutors.COOKING_TUTOR, LumbridgeTutors.SMITHING_APPRENTICE,
            LumbridgeTutors.WOODSMAN_TUTOR, LumbridgeTownsfolk.ABIGAILA, LumbridgeTownsfolk.IRONMAN_TUTOR,
        )
        for (id in ids) assertEquals("examine.npc_$id", Examines.npc(id)?.key, "NPC $id")
    }
}
