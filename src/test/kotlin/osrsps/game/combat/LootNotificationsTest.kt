package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.AllSettings
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.VarTypes
import osrsps.game.dialogue.DialogueText

class LootNotificationsTest {
    private val said = mutableListOf<String>()
    private val prices = mapOf(1 to 5_000L)
    private val notes = LootNotifications({ prices[it.id] }, { DialogueText.NONE }) { _, text -> said += text }

    private val rune = ObjType(1, "Rune thing", cost = 100, tradeable = true)
    private val iron = ObjType(2, "Iron thing", cost = 1_000, tradeable = true)
    private val bound = ObjType(3, "Bound thing", cost = 10)

    private fun player(on: Int = 1, value: Int = 10_000, untradeable: Int = 0) =
        Player(1, "test", 3222, 3218, 0, VarTypes(6_000, AllSettings.VARBITS)).apply {
            vars.setVarbit(LootNotifications.VARBIT_ON, on)
            vars.setVarbit(LootNotifications.VARBIT_VALUE, value)
            vars.setVarbit(LootNotifications.VARBIT_UNTRADEABLE, untradeable)
        }

    private fun valuable(item: String, value: String) = DialogueText.NONE[LootLines.VALUABLE, "item" to item, "value" to value]

    @Test
    fun `a stack worth the threshold or more is announced, at its Grand Exchange price, else high alchemy`() {
        val player = player()
        notes.dropped(player, rune, 1)
        notes.dropped(player, rune, 2)
        // No price: 600 each at high alchemy.
        notes.dropped(player, iron, 16)
        notes.dropped(player, iron, 17)
        assertEquals(listOf(valuable("2 x Rune thing", "10,000"), valuable("17 x Iron thing", "10,200")), said)
    }

    @Test
    fun `off, nothing is said, and untradeable drops need their own setting`() {
        notes.dropped(player(on = 0), rune, 5)
        notes.dropped(player(), bound, 1)
        assertEquals(emptyList(), said)
        notes.dropped(player(untradeable = 1), bound, 1)
        notes.dropped(player(on = 0, untradeable = 1), bound, 1)
        assertEquals(listOf(DialogueText.NONE[LootLines.UNTRADEABLE, "item" to "Bound thing"]), said)
    }
}
