package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.content.ArchiveLobby.apprentice
import osrsps.game.content.ArchiveLobby.banker
import osrsps.game.content.ArchiveLobby.montoya
import osrsps.game.content.ArchiveLobby.recordKeeper

/** Raid step 14: the lobby's talkers, with no dialogue file, so every line shows its key. */
class ArchiveLobbyTest {
    private val h = DialogueHarness()

    private fun run(prefix: String, topic: String, speakers: Int) = (1..speakers).map { "$prefix.${topic}_$it" }.toTypedArray()

    @Test
    fun `General Montoya answers each topic, back to her menu each time, until thanks`() {
        val menu = arrayOf("general_montoya.archive", "general_montoya.enemies", "general_montoya.everybody", "general_montoya.thanks")
        h.talk { montoya() }
        h.expect(*run("general_montoya", "greeting", 4))
        h.menu(*menu)
        for ((option, topic, lines) in listOf(Triple(1, "archive", 13), Triple(2, "enemies", 18), Triple(3, "everybody", 3))) {
            h.pick(option)
            h.expect("general_montoya.$topic", *run("general_montoya", topic, lines))
            h.menu(*menu)
        }
        h.pick(4)
        h.expect("general_montoya.thanks", "general_montoya.thanks_1")
        h.ends()
    }

    @Test
    fun `the Record Keeper's introduction, then his topics, each followed by the statistics prompt`() {
        val menu = arrayOf("record_keeper.doing", "record_keeper.things", "record_keeper.goodbye")
        h.talk { recordKeeper() }
        h.expect(*run("record_keeper", "intro", 23))
        h.menu("record_keeper.yes", "record_keeper.no")
        h.pick(2)
        h.menu(*menu)
        h.pick(1)
        h.expect("record_keeper.doing", *run("record_keeper", "doing", 10))
        h.menu("record_keeper.yes", "record_keeper.no")
        h.pick(2)
        h.menu(*menu)
        h.pick(2)
        h.expect("record_keeper.things", *run("record_keeper", "things", 2))
        h.menu("record_keeper.yes", "record_keeper.no")
        h.pick(2)
        h.menu(*menu)
        h.pick(3)
        h.expect("record_keeper.goodbye", "record_keeper.goodbye_1")
        h.ends()
    }

    @Test
    fun `Yes to the Record Keeper's statistics ends the talk, as the screen isn't built`() {
        h.talk { recordKeeper() }
        h.expect(*run("record_keeper", "intro", 23))
        h.menu("record_keeper.yes", "record_keeper.no")
        h.pick(1)
        assertEquals(-1, h.player.chatModal)
    }

    @Test
    fun `the Wizard apprentice's first talk, then his later one ending as the first does`() {
        h.talk { apprentice(met = false) }
        h.expect(*run("tfa_wizard_apprentice", "first", 26))
        h.ends()
        h.talk { apprentice(met = true) }
        h.expect(*run("tfa_wizard_apprentice", "later", 3), *(16..26).map { "tfa_wizard_apprentice.first_$it" }.toTypedArray())
        h.ends()
    }

    @Test
    fun `the Archive's banker offers the standard banker's four options and her own fifth`() {
        val menu = arrayOf("banker.open_bank", "banker.pin", "banker.collect", "banker.buy_slots", "tfa_banker.function")
        val asks = listOf(Bankers.Ask.BANK, Bankers.Ask.PIN, Bankers.Ask.COLLECT)
        for ((i, expected) in asks.withIndex()) {
            var ask = Bankers.Ask.NOTHING
            h.talk { ask = banker() }
            h.expect("tfa_banker.greeting")
            h.menu(*menu)
            h.pick(i + 1)
            assertEquals(expected, ask)
            assertEquals(-1, h.player.chatModal)
        }
        var ask = Bankers.Ask.BANK
        h.talk { ask = banker() }
        h.expect("tfa_banker.greeting")
        h.menu(*menu)
        h.pick(4)
        h.expect("banker.buy_slots")
        h.ends()
        assertEquals(Bankers.Ask.NOTHING, ask)
        h.talk { banker() }
        h.expect("tfa_banker.greeting")
        h.menu(*menu)
        h.pick(5)
        h.expect("tfa_banker.function", *run("tfa_banker", "function", 20))
        h.ends()
    }
}
