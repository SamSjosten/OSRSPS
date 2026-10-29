package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertTrue
import osrsps.game.ObjType
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.Bankers.banker
import osrsps.game.content.Bankers.tutor
import osrsps.game.content.CraftingTutor.talk
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
    fun `the Crafting tutor's topics drop off the menu as they are heard`() {
        h.talk { with(CraftingTutor) { talk() } }
        h.expect("crafting_tutor.hello", "crafting_tutor.offer")
        h.menu("crafting_tutor.training", "crafting_tutor.items", "crafting_tutor.no_thanks")
        h.pick(2)
        h.expect("crafting_tutor.items", "crafting_tutor.list", "crafting_tutor.which_topic")
        h.menu("crafting_tutor.armour", "crafting_tutor.pottery", "crafting_tutor.glass", "crafting_tutor.jewellery", "crafting_tutor.weapons")
        h.pick(3)
        h.expect(
            "crafting_tutor.glass", "crafting_tutor.glass_1", "crafting_tutor.glass_2", "crafting_tutor.glass_3",
            "crafting_tutor.glass_reply", "crafting_tutor.more_from_list",
        )
        h.menu("crafting_tutor.armour", "crafting_tutor.pottery", "crafting_tutor.jewellery", "crafting_tutor.weapons", "crafting_tutor.no_more")
        h.pick(5)
        h.expect("crafting_tutor.no_more", "crafting_tutor.anything_else")
        h.menu("crafting_tutor.training", "crafting_tutor.no_thanks")
        h.pick(2)
        h.expect("crafting_tutor.no_thanks", "crafting_tutor.come_back")
        h.ends()
    }

    @Test
    fun `the Crafting tutor's training talk goes by Crafting level`() {
        h.player.skills.setLevel(Skill.CRAFTING, 15)
        h.talk { with(CraftingTutor) { talk() } }
        h.expect("crafting_tutor.hello", "crafting_tutor.offer")
        h.menu("crafting_tutor.training", "crafting_tutor.items", "crafting_tutor.no_thanks")
        h.pick(1)
        h.expect(
            "crafting_tutor.training", "crafting_tutor.mid_flax", "crafting_tutor.mid_hide_armour", "crafting_tutor.thanks",
            "crafting_tutor.anything_else",
        )
        h.menu("crafting_tutor.training", "crafting_tutor.items", "crafting_tutor.no_thanks")
    }

    @Test
    fun `a Crafting tutor at 99 jokes before the high-level advice`() {
        h.player.skills.setLevel(Skill.CRAFTING, 99)
        h.talk { with(CraftingTutor) { talk() } }
        h.expect("crafting_tutor.hello", "crafting_tutor.offer")
        h.menu("crafting_tutor.training", "crafting_tutor.items", "crafting_tutor.no_thanks")
        h.pick(1)
        h.expect(
            "crafting_tutor.training", "crafting_tutor.high_maxed", "crafting_tutor.high_jewellery", "crafting_tutor.high_armour",
            "crafting_tutor.high_hides", "crafting_tutor.high_glass", "crafting_tutor.thanks",
        )
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
    fun `asking the Banker tutor for the account ends the talk with the bank to open`() {
        var ask = Bankers.Ask.NOTHING
        h.talk { ask = with(Bankers) { tutor() } }
        h.expect("banker_tutor.greeting")
        h.menu("banker_tutor.how_it_works", "banker_tutor.access", "banker_tutor.pin", "banker_tutor.collect", "banker_tutor.buy_slots")
        h.pick(2)
        kotlin.test.assertEquals(-1, h.player.chatModal)
        kotlin.test.assertEquals(Bankers.Ask.BANK, ask)
    }

    @Test
    fun `the Banker tutor goes from the topics to the PIN menu and back`() {
        h.talk { with(Bankers) { tutor() } }
        h.expect("banker_tutor.greeting")
        h.menu("banker_tutor.how_it_works", "banker_tutor.access", "banker_tutor.pin", "banker_tutor.collect", "banker_tutor.buy_slots")
        h.pick(1)
        h.menu("banker_tutor.using", "banker_tutor.deposit_boxes", "banker_tutor.what_pin")
        h.pick(2)
        h.expect("banker_tutor.deposit_boxes_said", "banker_tutor.deposit_1", "banker_tutor.deposit_2")
        h.menu("banker_tutor.using", "banker_tutor.deposit_boxes", "banker_tutor.what_pin")
        h.pick(3)
        h.expect("banker_tutor.what_pin_said", "banker_tutor.pin_1", "banker_tutor.pin_2", "banker_tutor.pin_3")
        h.menu("banker_tutor.set_pin", "banker_tutor.remove_pin", "banker_tutor.forgot_pin", "banker_tutor.other_topics", "banker_tutor.goodbye")
        h.pick(2)
        h.expect("banker_tutor.remove_pin", "banker_tutor.remove_1", "banker_tutor.remove_2")
        h.menu("banker_tutor.set_pin", "banker_tutor.remove_pin", "banker_tutor.forgot_pin", "banker_tutor.other_topics", "banker_tutor.goodbye")
        h.pick(5)
        h.expect("banker_tutor.goodbye")
        h.ends()
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
        for (id in listOf(Cook.ID, DukeHoracio.ID, Perdu.ID, CraftingTutor.ID, Bankers.BANKER, Bankers.BANKER_TUTOR, Doomsayer.ID, Hatius.ID, LumbridgeGuide.ID) + Townsfolk.MEN + Townsfolk.WOMEN) {
            assertTrue(world.handlers.find(Op(OpTarget.NPC, id, 1)) != null, "NPC $id")
        }
    }
}
