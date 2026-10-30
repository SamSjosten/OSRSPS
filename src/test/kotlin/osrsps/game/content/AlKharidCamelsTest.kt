package osrsps.game.content

import kotlin.test.Test
import osrsps.game.content.AlKharidCamels.camel

/** Al Kharid step 6: a camel's talk without a camulet. */
class AlKharidCamelsTest {
    private val h = DialogueHarness()
    private val menu = arrayOf("camel.dung", "camel.unpleasant", "camel.neither")

    @Test
    fun `a camel doesn't understand a request for dung`() {
        h.talk { camel { 0 } }
        h.menu(*menu)
        h.pick(1)
        h.expect("camel.dung", "camel.dung_no")
        h.ends()
    }

    @Test
    fun `something unpleasant gets one of three lines and one of three reactions`() {
        val rolls = ArrayDeque(listOf(2, 1))
        h.talk { camel { rolls.removeFirst() } }
        h.menu(*menu)
        h.pick(2)
        h.expect("camel.rude_3", "camel.reacts_2")
        h.ends()

        h.talk { camel { 0 } }
        h.menu(*menu)
        h.pick(2)
        h.expect("camel.rude_1", "camel.reacts_1")
        h.ends()
    }

    @Test
    fun `neither ends the talk`() {
        h.talk { camel { 0 } }
        h.menu(*menu)
        h.pick(3)
        h.expect("camel.neither")
        h.ends()
    }
}
