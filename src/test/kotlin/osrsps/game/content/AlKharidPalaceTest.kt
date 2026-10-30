package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.NpcType
import osrsps.game.ObjType
import osrsps.game.Skill
import osrsps.game.content.AlKharidPalace.bigWindow
import osrsps.game.content.AlKharidPalace.hassan
import osrsps.game.content.AlKharidPalace.osman
import osrsps.game.content.AlKharidPalace.princeAli
import osrsps.game.shop.ShopScreen

/** Al Kharid step 5: the palace's people as after Prince Ali Rescue and Contact!, and the big window. */
class AlKharidPalaceTest {
    private val h = DialogueHarness()
    private val given = mutableListOf<Int>()
    private val coins = ObjType(ShopScreen.COINS, "Coins", stackable = true)
    private val firstMenu = arrayOf("osman.sqirks", "osman.sophanem", "osman.need_to_go")
    private val sophanemMenu = arrayOf("osman.daggers", "osman.menaphos", "osman.something_else", "osman.should_go")
    private val sqirksMenu = arrayOf("osman.where", "osman.why", "osman.how", "osman.reward", "osman.nectar")

    private fun coinsHeld() = h.player.inventory.count(ShopScreen.COINS).toInt()

    @Test
    fun `Hassan and Prince Ali have their after-quest lines`() {
        h.talk { hassan() }
        h.expect("hassan.thank_you")
        h.ends()

        h.talk { princeAli() }
        h.expect("prince_ali.good_to_see", "prince_ali.resting", "prince_ali.best")
        h.ends()
    }

    @Test
    fun `the big window gets Hassan's refusal and what the shortcut needs`() {
        h.talk { bigWindow(NpcType(AlKharidPalace.HASSAN, "Chancellor Hassan")) }
        h.expect("hassan.not_a_thief", "hassan.window_needs")
        h.ends()
    }

    @Test
    fun `Osman sells a Keris for 20,000 coins, and not to a player short of them`() {
        h.player.inventory.add(coins, 19_999)
        h.talk { osman { given += it } }
        h.expect("osman.hello_again")
        h.menu(*firstMenu)
        h.pick(2)
        h.expect("osman.sophanem", "osman.sophanem_reply")
        h.menu(*sophanemMenu)
        h.pick(1)
        h.expect("osman.daggers", "osman.dagger_offer")
        h.menu("osman.take_one", "osman.too_much")
        h.pick(1)
        h.expect("osman.take_one", "osman.no_coins")
        h.menu(*sophanemMenu)
        h.pick(4)
        h.expect("osman.should_go", "osman.until_next_time")
        h.ends()
        assertEquals(emptyList(), given)

        h.player.inventory.add(coins, 1)
        h.talk { osman { given += it } }
        h.expect("osman.hello_again")
        h.menu(*firstMenu)
        h.pick(2)
        h.expect("osman.sophanem", "osman.sophanem_reply")
        h.menu(*sophanemMenu)
        h.pick(1)
        h.expect("osman.daggers", "osman.dagger_offer")
        h.menu("osman.take_one", "osman.too_much")
        h.pick(1)
        h.expect("osman.take_one", "osman.hands_dagger")
        h.menu(*sophanemMenu)
        h.pick(2)
        h.expect("osman.menaphos", "osman.menaphos_reply", "osman.not_involved", "osman.no")
        h.menu(*sophanemMenu)
        h.pick(3)
        h.expect("osman.something_else", "osman.as_you_wish")
        h.menu(*firstMenu)
        h.pick(3)
        h.expect("osman.need_to_go", "osman.until_next_time")
        h.ends()
        assertEquals(listOf(AlKharidPalace.KERIS), given)
        assertEquals(0, coinsHeld())
    }

    @Test
    fun `Osman takes every sq'irk juice for Thieving experience`() {
        h.player.inventory.add(ObjType(10851, "Winter sq'irkjuice"), 1)
        h.player.inventory.add(ObjType(10849, "Summer sq'irkjuice"), 1)
        h.player.inventory.add(ObjType(10849, "Summer sq'irkjuice"), 1)
        val before = h.player.skills.tenths(Skill.THIEVING)
        h.talk { osman { given += it } }
        h.expect("osman.hello_again")
        h.menu(*firstMenu)
        h.pick(1)
        h.expect("osman.have_juice", "osman.thieving_tips xp=6350", "osman.thank_you", "osman.will_do")
        h.ends()
        assertEquals(63_500, h.player.skills.tenths(Skill.THIEVING) - before)
        assertEquals(0, h.player.inventory.contents().size)
    }

    @Test
    fun `with no juice Osman talks about the Sorceress's Garden`() {
        h.talk { osman { given += it } }
        h.expect("osman.hello_again")
        h.menu(*firstMenu)
        h.pick(1)
        h.expect("osman.sqirks_menu")
        h.menu(*sqirksMenu)
        h.pick(2)
        h.expect("osman.why", "osman.why_reply")
        h.menu(*sqirksMenu)
        h.pick(5)
        h.expect("osman.nectar", "osman.nectar_1", "osman.salivating", "osman.wouldnt", "osman.nectar_2", "osman.i_see")
        h.menu(*sqirksMenu)
        h.pick(1)
        h.expect(
            "osman.where", "osman.where_1", "osman.what_happened", "osman.where_2", "osman.so_all", "osman.where_3",
            "osman.oh_come_on", "osman.where_4", "osman.this_should", "osman.where_5",
        )
        h.menu("osman.another", "osman.thanks_info")
        h.pick(2)
        h.expect("osman.thanks_info")
        h.ends()
    }
}
