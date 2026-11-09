package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.content.DonieAndGee.talk
import osrsps.game.content.LumbridgeShopkeepers.bob
import osrsps.game.content.LumbridgeShopkeepers.keeper
import osrsps.game.content.LumbridgeTownsfolk.abigaila
import osrsps.game.content.LumbridgeTownsfolk.ironmanTutor
import osrsps.game.content.ShearedRam.arthur
import osrsps.game.content.ShearedRam.bartender
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.map.CollisionMap

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
    fun `Doric's thanks has the player's name`() {
        h.talk { with(DonieAndGee) { talk(GeeLines, 2) } }
        h.expect("gee.hello")
        h.menu("gee.whats_up", "gee.quests", "gee.stick")
        h.pick(2)
        h.expect("gee.know_quests", "gee.what_kind")
        h.menu("gee.fight", "gee.easy", "gee.thinker", "gee.all_kinds", "gee.another_time")
        h.pick(3)
        h.expect("gee.thinker", "gee.skills_1", "gee.skills_2", "gee.skills_3")
        h.menu("gee.fred", "gee.doric", "gee.another_time")
        h.pick(2)
        h.expect("gee.doric_said", "gee.doric_1", "gee.doric_done", "gee.doric_thanks name=Zezima")
        h.ends()
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
    fun `the Ironman tutor's Talk-to and Armour answer on the type Ironmen see since 242 too, and Arthur's on his Grand Exchange id`() {
        val world = World(IntArray(0), CollisionMap())
        for (id in listOf(LumbridgeTownsfolk.IRONMAN_TUTOR, LumbridgeTownsfolk.IRONMAN_TUTOR_VISIBLE)) {
            for (op in listOf(LumbridgeTownsfolk.TALK_TO, LumbridgeTownsfolk.ARMOUR)) {
                assertTrue(world.handlers.find(Op(OpTarget.NPC, id, op)) != null, "NPC $id op $op")
            }
        }
        assertEquals(16811, ShearedRam.ARTHUR)
        assertTrue(world.handlers.find(Op(OpTarget.NPC, ShearedRam.ARTHUR, ShearedRam.TALK_TO)) != null)
    }

    @Test
    fun `every town centre NPC that talks has an examine`() {
        val ids = listOf(
            LumbridgeShopkeepers.SHOP_KEEPER, LumbridgeShopkeepers.SHOP_ASSISTANT, LumbridgeShopkeepers.BOB, ShearedRam.BARTENDER,
            ShearedRam.ARTHUR, DonieAndGee.DONIE, DonieAndGee.GEE, Feoras.ID,
            Wilfred.ID, LumbridgeTownsfolk.ABIGAILA, LumbridgeTownsfolk.IRONMAN_TUTOR,
        )
        for (id in ids) assertEquals("examine.npc_$id", Examines.npc(id)?.key, "NPC $id")
    }
}
