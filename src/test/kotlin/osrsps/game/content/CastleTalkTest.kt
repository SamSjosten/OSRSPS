package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.ObjType
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.Bankers.banker
import osrsps.game.content.Cadmus.talk
import osrsps.game.content.Doomsayer.talk
import osrsps.game.content.Hatius.talk
import osrsps.game.content.Perdu.talk
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.map.CollisionMap

/** Perdu, the Crafting tutor and the bankers, with no dialogue file: every line shows its key. */
class CastleTalkTest {
    private val h = DialogueHarness()

    @Test
    fun `Perdu sends a player with no parchment to Ferox Enclave`() {
        h.talk { with(Perdu) { talk() } }
        h.expect("perdu.greeting")
        h.menu("perdu.why_pay", "perdu.show_items", "perdu.breaking", "perdu.no_thanks")
        h.pick(3)
        h.expect("perdu.breaking", "perdu.rivals")
        h.menu("perdu.fix", "perdu.lock")
        h.pick(2)
        h.expect("perdu.lock", "perdu.lock_explained", "perdu.parchment_merchant", "perdu.go_to_ferox")
        h.ends()
    }

    @Test
    fun `Perdu asks for the item from a player with a parchment`() {
        h.player.inventory.add(ObjType(Perdu.TROUVER_PARCHMENT, "Trouver parchment"), 1)
        h.talk { with(Perdu) { talk() } }
        h.expect("perdu.greeting")
        h.menu("perdu.why_pay", "perdu.show_items", "perdu.breaking", "perdu.no_thanks")
        h.pick(3)
        h.expect("perdu.breaking", "perdu.rivals")
        h.menu("perdu.fix", "perdu.lock")
        h.pick(2)
        h.expect("perdu.lock", "perdu.lock_explained", "perdu.have_parchment", "perdu.show_me")
        h.ends()
    }

    @Test
    fun `Cadmus says what a steward does, and the crafting tips lead into his advice and end the talk`() {
        h.talk { with(Cadmus) { talk() } }
        h.expect("cadmus.hello", "cadmus.greeting")
        h.menu("cadmus.what_do_you_do", "cadmus.advice", "cadmus.good")
        h.pick(1)
        h.expect(
            "cadmus.what_do_you_do", "cadmus.steward", "cadmus.entail", "cadmus.repairs", "cadmus.handyman", "cadmus.learned",
        )
        h.menu("cadmus.tips", "cadmus.best_be_off")
        h.pick(1)
        h.expect(
            "cadmus.tips", "cadmus.of_course", "cadmus.advice_1", "cadmus.advice_2", "cadmus.advice_3", "cadmus.materials",
            "cadmus.advice_4", "cadmus.wheel_hint", "cadmus.advice_5", "cadmus.guide_hint",
        )
        assertEquals(listOf(Cadmus.SPINNING_WHEEL_ICON), h.boxObjs)
        h.ends()
    }

    @Test
    fun `Cadmus's advice straight from the first menu, and good thanks ends it`() {
        h.talk { with(Cadmus) { talk() } }
        h.expect("cadmus.hello", "cadmus.greeting")
        h.menu("cadmus.what_do_you_do", "cadmus.advice", "cadmus.good")
        h.pick(2)
        h.expect(
            "cadmus.advice", "cadmus.advice_1", "cadmus.advice_2", "cadmus.advice_3", "cadmus.materials", "cadmus.advice_4",
            "cadmus.wheel_hint", "cadmus.advice_5", "cadmus.guide_hint",
        )
        h.ends()

        h.talk { with(Cadmus) { talk() } }
        h.expect("cadmus.hello", "cadmus.greeting")
        h.menu("cadmus.what_do_you_do", "cadmus.advice", "cadmus.good")
        h.pick(3)
        h.expect("cadmus.good", "cadmus.very_well")
        h.ends()
    }

    @Test
    fun `the banker says what the castle's bank is`() {
        h.talk { with(Bankers) { banker() } }
        h.expect("banker.greeting")
        h.menu("banker.open_bank", "banker.pin", "banker.collect", "banker.buy_slots", "banker.what_place")
        h.pick(5)
        h.expect("banker.what_place", "banker.this_branch", "banker.your_job", "banker.safekeeping")
        h.ends()
    }

    @Test
    fun `asking the banker for the bank ends the talk with the bank to open`() {
        var ask = Bankers.Ask.NOTHING
        h.talk { ask = with(Bankers) { banker() } }
        h.expect("banker.greeting")
        h.menu("banker.open_bank", "banker.pin", "banker.collect", "banker.buy_slots", "banker.what_place")
        h.pick(1)
        kotlin.test.assertEquals(-1, h.player.chatModal)
        kotlin.test.assertEquals(Bankers.Ask.BANK, ask)
    }

    @Test
    fun `asking the banker for PIN settings ends the talk with the settings to open`() {
        var ask = Bankers.Ask.NOTHING
        h.talk { ask = with(Bankers) { banker() } }
        h.expect("banker.greeting")
        h.menu("banker.open_bank", "banker.pin", "banker.collect", "banker.buy_slots", "banker.what_place")
        h.pick(2)
        kotlin.test.assertEquals(-1, h.player.chatModal)
        kotlin.test.assertEquals(Bankers.Ask.PIN, ask)
    }

    @Test
    fun `the Doomsayer explains himself to a player with every warning on`() {
        h.talk { with(Doomsayer) { talk() } }
        h.expect(
            "doomsayer.doom", "doomsayer.where", "doomsayer.everywhere", "doomsayer.stop_it", "doomsayer.my_role", "doomsayer.title",
            "doomsayer.patrol", "doomsayer.disable", "doomsayer.danger_areas", "doomsayer.see_again", "doomsayer.waiting",
            "doomsayer.re_enable", "doomsayer.remember", "doomsayer.welcome",
        )
        h.ends()
    }

    @Test
    fun `Hatius opens with a menu and comes back to it after saying who he is`() {
        h.talk { with(Hatius) { talk() } }
        h.menu("hatius.who_are_you", "hatius.my_diary", "hatius.farewell")
        h.pick(1)
        h.expect("hatius.who_are_you", "hatius.taskmaster", "hatius.what_diary", "hatius.explain", "hatius.explore", "hatius.side_panel")
        h.menu("hatius.who_are_you", "hatius.my_diary", "hatius.farewell")
        h.pick(3)
        h.expect("hatius.farewell", "hatius.goodbye")
        h.ends()
    }

    @Test
    fun `Hatius tells a tier's rewards and the talk ends`() {
        h.talk { with(Hatius) { talk() } }
        h.menu("hatius.who_are_you", "hatius.my_diary", "hatius.farewell")
        h.pick(2)
        h.expect("hatius.my_diary")
        h.menu("hatius.what_diary", "hatius.rewards", "hatius.claim", "hatius.farewell")
        h.pick(2)
        h.expect("hatius.rewards", "hatius.four_tiers", "hatius.which_tier")
        h.menu("hatius.easy", "hatius.medium", "hatius.hard", "hatius.elite")
        h.pick(3)
        h.expect("hatius.hard", "hatius.hard_rewards", "hatius.thanks")
        h.ends()
    }

    @Test
    fun `every castle NPC with lines has Talk-to`() {
        val world = World(IntArray(0), CollisionMap())
        for (id in listOf(Cook.ID, DukeHoracio.ID, Perdu.ID, Cadmus.ID, Bankers.BANKER, Doomsayer.ID, Hatius.ID, LumbridgeGuide.ID) + Townsfolk.MEN + Townsfolk.WOMEN) {
            assertTrue(world.handlers.find(Op(OpTarget.NPC, id, 1)) != null, "NPC $id")
        }
    }
}
