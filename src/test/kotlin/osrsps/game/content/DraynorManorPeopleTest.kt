package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals

/** Draynor step 12b: the manor's people's talks, with no dialogue file, so every line shows its key. */
class DraynorManorPeopleTest {
    private val h = DialogueHarness()

    @Test
    fun `Oddenstein greets the player, then talks about his portal machine or his house`() {
        h.talk { with(DraynorManorPeople) { oddenstein() } }
        h.expect("oddenstein.careful")
        h.menu("oddenstein.portal", "oddenstein.house")
        h.pick(1)
        h.expect("oddenstein.portal", "oddenstein.rift", "oddenstein.huh", "oddenstein.teleport")
        h.ends()

        h.talk { with(DraynorManorPeople) { oddenstein() } }
        h.expect("oddenstein.careful")
        h.menu("oddenstein.portal", "oddenstein.house")
        h.pick(2)
        h.expect("oddenstein.house", "oddenstein.tenants")
        h.ends()
    }

    @Test
    fun `the witch has nothing interesting to say`() {
        h.talk { with(DraynorManorPeople) { witch() } }
        h.expect("manor_witch.ickle", "manor_witch.wondered", "manor_witch.clearly_not")
        h.ends()
    }

    private val avaMenu = arrayOf("ava.devices", "ava.information", "ava.orbs", "ava.more")
    private val moreMenu = arrayOf("ava.stuff", "ava.head_off", "ava.previous")

    @Test
    fun `Ava's devices, information and orbs options end the talk`() {
        val shops = mutableListOf<Boolean>()
        h.talk { shops += with(DraynorManorPeople) { ava() } }
        h.expect("ava.hello")
        h.menu(*avaMenu)
        h.pick(1)
        h.expect("ava.devices")
        h.ends()

        h.talk { shops += with(DraynorManorPeople) { ava() } }
        h.expect("ava.hello")
        h.menu(*avaMenu)
        h.pick(2)
        h.expect("ava.information", *(1..9).map { "ava.info_$it" }.toTypedArray())
        h.ends()

        h.talk { shops += with(DraynorManorPeople) { ava() } }
        h.expect("ava.hello")
        h.menu(*avaMenu)
        h.pick(3)
        h.expect("ava.orbs")
        h.ends()
        assertEquals(listOf(false, false, false), shops)
    }

    @Test
    fun `Ava's more options go back, end in her shop, or see the player off`() {
        val shops = mutableListOf<Boolean>()
        h.talk { shops += with(DraynorManorPeople) { ava() } }
        h.expect("ava.hello")
        h.menu(*avaMenu)
        h.pick(4)
        h.menu(*moreMenu)
        h.pick(3)
        h.menu(*avaMenu)
        h.pick(4)
        h.menu(*moreMenu)
        h.pick(1)
        h.expect("ava.stuff", "ava.shop")
        h.ends()

        h.talk { shops += with(DraynorManorPeople) { ava() } }
        h.expect("ava.hello")
        h.menu(*avaMenu)
        h.pick(4)
        h.menu(*moreMenu)
        h.pick(2)
        h.expect("ava.head_off", "ava.bed")
        h.ends()
        assertEquals(listOf(true, false), shops)
    }
}
