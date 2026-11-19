package osrsps.game.collection

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.Player
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.dialogue.DialogueText

class CollectionLogTest {
    private val essence = 22372
    private val visage = 11286
    private val pickaxe = 11920
    private val club = 20756

    // Bosses: Bryophyta (essence) and King Black Dragon (pickaxe, visage); Other: Slayer (visage).
    private val bryophyta = CollectionLogCatalog.Page(478, 0, 0, listOf(essence), completedVarbit = 11965)
    private val kbd = CollectionLogCatalog.Page(491, 0, 1, listOf(pickaxe, visage), completedVarbit = 11978)
    private val slayer = CollectionLogCatalog.Page(527, 4, 0, listOf(visage, club), completedVarbit = null)
    private val catalog = CollectionLogCatalog(
        listOf(CollectionLogCatalog.Tab(0, 471, listOf(bryophyta, kbd)), CollectionLogCatalog.Tab(4, 475, listOf(slayer))),
    )

    private val said = mutableListOf<String>()
    private val popups = mutableListOf<Pair<String, String>>()

    // 2026-11-19 is rune day 9031 (2002-02-27 is day 0).
    private val log = CollectionLog(
        catalog, { "Item $it" }, { DialogueText.NONE },
        popup = { _, title, text -> popups += title to text },
        now = { Instant.parse("2026-11-19T04:00:00Z") },
        message = { _, text -> said += text },
    )

    private fun player(setting: Int = 0): Player {
        val varbits = mapOf(
            CollectionLog.VARBIT_NEW_ITEM_OPTION to VarbitType(100, 0, 1),
            11965 to VarbitType(101, 0, 0),
            11978 to VarbitType(101, 1, 1),
            CollectionLog.VARBIT_CURRENT_RUNEDAY to VarbitType(102, 0, 15),
        )
        return Player(1, "test", 3222, 3218, 0, VarTypes(6_000, varbits)).apply { vars.setVarbit(CollectionLog.VARBIT_NEW_ITEM_OPTION, setting) }
    }

    private fun latest(player: Player, slot: Int) = player.vars[CollectionLog.VARP_LATEST + slot * 2] to player.vars[CollectionLog.VARP_LATEST + slot * 2 + 1]

    @Test
    fun `an item is logged once, counted on after, and an item the log doesn't track is ignored`() {
        val player = player()
        log.add(player, visage, 1)
        log.add(player, visage, 2)
        log.add(player, 995, 1_000)
        assertEquals(3, log.count(player, visage))
        assertEquals(mapOf(visage to 3), player.collectionLog)
        assertEquals(1, log.obtained(player))
    }

    @Test
    fun `an item dropped for a player who logged out is logged, and announced, at their next login`() {
        val player = player(setting = CollectionLog.NOTIFY_CHAT)
        log.addLater(player.saveKey, pickaxe, 1)
        log.addLater(player.saveKey, 995, 1)
        assertEquals(0, log.count(player, pickaxe))
        log.login(player)
        assertEquals(mapOf(pickaxe to 1), player.collectionLog)
        assertEquals(1, said.size)
        log.login(player)
        assertEquals(1, log.count(player, pickaxe), "once only")
    }

    @Test
    fun `bracelets of ethereum stop at 250`() {
        val bracelet = CollectionLog.BRACELET_OF_ETHEREUM
        val revenants = CollectionLogCatalog.Page(525, 4, 0, listOf(bracelet), completedVarbit = null)
        val withBracelet = CollectionLog(
            CollectionLogCatalog(listOf(CollectionLogCatalog.Tab(4, 475, listOf(revenants)))), { "Item $it" }, { DialogueText.NONE },
            now = { Instant.parse("2026-11-19T04:00:00Z") }, message = { _, _ -> },
        )
        val player = player()
        withBracelet.add(player, bracelet, 249)
        assertEquals(249, withBracelet.count(player, bracelet))
        withBracelet.add(player, bracelet, 1)
        assertEquals(250, withBracelet.count(player, bracelet))
        withBracelet.add(player, bracelet, 5)
        assertEquals(250, withBracelet.count(player, bracelet))
    }

    @Test
    fun `counts stop at 65,535`() {
        val player = player()
        log.add(player, essence, 65_000)
        log.add(player, essence, Int.MAX_VALUE)
        assertEquals(CollectionLog.MAX_COUNT, log.count(player, essence))
    }

    @Test
    fun `the counts and each page's completed varbit follow what is logged, an item counted once however many pages`() {
        val player = player()
        log.login(player)
        assertEquals(0, player.vars[CollectionLog.VARP_COUNT])
        assertEquals(4, player.vars[CollectionLog.VARP_COUNT_MAX])
        assertEquals(3, player.vars[4614]) // collection_count_bosses_max
        assertEquals(2, player.vars[4622]) // collection_count_other_max
        log.add(player, visage, 1)
        assertEquals(1, player.vars[CollectionLog.VARP_COUNT])
        assertEquals(1, player.vars[4613])
        assertEquals(1, player.vars[4621])
        assertEquals(0, player.vars.getVarbit(11978))
        log.add(player, pickaxe, 1)
        assertEquals(1, player.vars.getVarbit(11978), "King Black Dragon's page is done")
        assertEquals(0, player.vars.getVarbit(11965))
        log.add(player, essence, 1)
        assertEquals(1, player.vars.getVarbit(11965))
        assertEquals(3, player.vars[CollectionLog.VARP_COUNT])
        assertEquals(3, player.vars[4613])
    }

    @Test
    fun `new items go to the front of the latest twelve, with the rune day, and empty slots null`() {
        val player = player()
        log.login(player)
        assertEquals(-1 to 0, latest(player, 0))
        assertEquals(9031, player.vars.getVarbit(CollectionLog.VARBIT_CURRENT_RUNEDAY))
        log.add(player, visage, 1)
        log.add(player, essence, 1)
        log.add(player, visage, 1)
        assertEquals(essence to 9031, latest(player, 0))
        assertEquals(visage to 9031, latest(player, 1))
        assertEquals(-1 to 0, latest(player, 2))
    }

    @Test
    fun `a new item is announced in chat, by pop-up, both or neither, as the setting's bits ask`() {
        log.add(player(setting = 0), visage, 1)
        assertEquals(emptyList(), said + popups.map { it.second })
        log.add(player(setting = 1), visage, 1)
        val message = DialogueText.NONE[CollectionLogLines.NEW_ITEM, "item" to "Item $visage"]
        assertEquals(listOf(message), said)
        assertEquals(emptyList(), popups)
        log.add(player(setting = 2), visage, 1)
        val popup = DialogueText.NONE[CollectionLogLines.POPUP_TITLE] to DialogueText.NONE[CollectionLogLines.POPUP_TEXT, "item" to "Item $visage"]
        assertEquals(listOf(popup), popups)
        val both = player(setting = 3)
        log.add(both, visage, 1)
        assertEquals(listOf(message, message), said)
        assertEquals(listOf(popup, popup), popups)
        // Only once: another of it is counted, not announced.
        log.add(both, visage, 1)
        assertEquals(2, said.size)
    }

    @Test
    fun `removing an item takes it off its pages and out of the latest`() {
        val player = player()
        log.add(player, pickaxe, 1)
        log.add(player, visage, 1)
        log.remove(player, pickaxe)
        assertEquals(0, player.vars.getVarbit(11978))
        assertEquals(1, player.vars[CollectionLog.VARP_COUNT])
        assertEquals(visage to 9031, latest(player, 0))
        assertEquals(-1 to 0, latest(player, 1))
        log.remove(player, null)
        assertEquals(emptyMap(), player.collectionLog)
        assertEquals(-1 to 0, latest(player, 0))
    }
}
