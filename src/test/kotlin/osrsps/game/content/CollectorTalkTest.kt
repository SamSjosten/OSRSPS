package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.WearPos
import osrsps.game.collection.CollectionLog
import osrsps.game.collection.CollectionLogCatalog
import osrsps.game.collection.TheCollector
import osrsps.game.dialogue.Chatbox
import osrsps.game.dialogue.DialogueText

/** Collection log step 3: The Collector's talks, with no dialogue file, so every line shows its key. */
class CollectorTalkTest {
    private val h = DialogueHarness("The Collector")

    private val given = mutableListOf<Int>()
    private var room = true
    private val give: (Player, Int) -> Boolean = { player, obj ->
        if (room) {
            given += obj
            player.inventory[player.inventory.contents().size] = InvObj(obj, 1)
        }
        room
    }

    // A 2,000-item log: gilded at 1,800 (90%, a multiple of 25).
    private val catalog = CollectionLogCatalog(listOf(CollectionLogCatalog.Tab(0, 471, listOf(CollectionLogCatalog.Page(478, 0, 0, (1..2_000).toList(), null)))))
    private val log = CollectionLog(catalog, { "Item $it" }, { DialogueText.NONE }, message = { _, _ -> })

    private fun logged(count: Int) {
        h.player.collectionLog.clear()
        for (item in 1..count) h.player.collectionLog[item] = 1
    }

    @Test
    fun `the ranks are the cache's, gilded at 90 percent rounded down to 25`() {
        assertEquals(1_800, TheCollector.gilded(2_000))
        assertEquals(1_725, TheCollector.gilded(1_944))
        assertEquals(emptyList(), TheCollector.ranks(99, 1_944))
        assertEquals(listOf(0), TheCollector.ranks(100, 1_944))
        assertEquals((0..7).toList(), TheCollector.ranks(1_724, 1_944))
        assertEquals((0..8).toList(), TheCollector.ranks(1_725, 1_944))
        assertEquals(30613, TheCollector.STAVES.last())
        assertEquals(30595, TheCollector.BOOKS.last())
    }

    @Test
    fun `Talk-to offers a log only to a player without one, carried, worn or banked`() {
        val intro = arrayOf("collector.beautiful", "collector.what_is", "collector.everything", "collector.guess_so")
        h.talk { with(TheCollector) { talk(give) } }
        h.expect(*intro, "collector.collector", "collector.would_you")
        h.menu("collector.yes", "collector.no")
        h.pick(1)
        h.expect("collector.yes_said", "collector.gives_log", "collector.there", "collector.thanks")
        h.ends()
        assertEquals(listOf(TheCollector.LOG), given)

        h.talk { with(TheCollector) { talk(give) } }
        h.expect(*intro)
        h.ends()

        h.player.inventory.clear()
        h.player.equipment[WearPos.SHIELD] = InvObj(TheCollector.BOOKS[3], 1)
        h.talk { with(TheCollector) { talk(give) } }
        h.expect(*intro)
        h.ends()
        h.player.equipment.clear()

        h.talk { with(TheCollector) { talk(give) } }
        h.expect(*intro, "collector.collector", "collector.would_you")
        h.menu("collector.yes", "collector.no")
        h.pick(2)
        h.expect("collector.no_said", "collector.no_taste")
        h.ends()
    }

    @Test
    fun `Get-log hands one over, not into a full inventory, and not to a player with one banked`() {
        room = false
        h.talk { with(TheCollector) { getLog(give) } }
        h.expect("collector.cant_carry", "collector.come_back")
        h.ends()
        room = true
        h.talk { with(TheCollector) { getLog(give) } }
        h.expect("collector.gives_log", "collector.there", "collector.thanks")
        h.ends()
        assertEquals(listOf(TheCollector.LOG), h.boxObjs)
        h.player.inventory.clear()
        h.player.bank[0] = InvObj(TheCollector.LOG, 1)
        h.talk { with(TheCollector) { getLog(give) } }
        h.expect("collector.have_one", "collector.youre_right")
        h.ends()
    }

    private fun counting(count: Int) = arrayOf("collector.count_please", "collector.love_to", "collector.take_a_seat", "collector.counting", "collector.collected count=$count")

    private fun staff(id: Int) = "collector.hands_staff staff=Item $id"

    @Test
    fun `the count's comment is the highest rank reached, and each staff unlocked and not had is handed over, highest first`() {
        logged(50)
        h.talk { with(TheCollector) { claim(log, give) { "Item $it" } } }
        h.expect(*counting(50), "collector.rank_none", "collector.reward", "collector.all_rewards")
        h.ends()

        logged(320)
        h.player.bank[0] = InvObj(TheCollector.STAVES[0], 1)
        h.talk { with(TheCollector) { claim(log, give) { "Item $it" } } }
        h.expect(*counting(320), "collector.rank_iron", "collector.reward", staff(TheCollector.STAVES[1]))
        h.ends()
        assertEquals(listOf(TheCollector.STAVES[1]), given)

        h.player.bank.clear()
        h.player.inventory.clear()
        given.clear()
        logged(2_000)
        h.talk { with(TheCollector) { claim(log, give) { "Item $it" } } }
        h.expect(*counting(2000), "collector.rank_dragon", "collector.rank_gilded", "collector.complete", "collector.reward", *TheCollector.STAVES.reversed().map(::staff).toTypedArray())
        h.ends()
        assertEquals(TheCollector.STAVES.reversed(), given)

        // All of them had: nothing more.
        h.talk { with(TheCollector) { claim(log, give) { "Item $it" } } }
        h.expect(*counting(2000), "collector.rank_dragon", "collector.rank_gilded", "collector.complete", "collector.reward", "collector.all_rewards")
        h.ends()
    }

    @Test
    fun `a full inventory stops the staves`() {
        logged(500)
        room = false
        h.talk { with(TheCollector) { claim(log, give) { "Item $it" } } }
        h.expect(*counting(500), "collector.rank_steel", "collector.reward", "collector.cant_carry", "collector.come_back")
        h.ends()
    }

    @Test
    fun `the destroy check answers Yes or No`() {
        val answers = mutableListOf<Boolean>()
        h.talk { answers += confirmDestroy(TheCollector.LOG, 1, "title", "text") }
        assertEquals(Chatbox.CONFIRM_DESTROY, h.player.chatModal)
        assertTrue(h.dialogues.resume(h.player, Chatbox.CONFIRM_DESTROY, Chatbox.CONFIRM_DESTROY_UNIVERSE, 1))
        h.talk { answers += confirmDestroy(TheCollector.LOG, 1, "title", "text") }
        assertTrue(h.dialogues.resume(h.player, Chatbox.CONFIRM_DESTROY, Chatbox.CONFIRM_DESTROY_UNIVERSE, 0))
        assertEquals(listOf(true, false), answers)
    }
}
