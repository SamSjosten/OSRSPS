package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos

/** The museum's varbits as the 242 cache defines them (osrs-dumps 2026-11-11-rev242, config/dump.varbit). */
val MUSEUM_VARBITS: Map<Int, VarbitType> = mapOf(
    Kudos.VARBIT_KUDOS to VarbitType(1010, 0, 10),
    Kudos.VARBIT_NECKLACE to VarbitType(1010, 12, 12),
    Kudos.VARBIT_ANCIENT_SYMBOL to VarbitType(1011, 2, 2),
    Kudos.VARBIT_OLD_SYMBOL to VarbitType(1011, 3, 3),
    Kudos.VARBIT_ANCIENT_COIN to VarbitType(1011, 4, 4),
    Kudos.VARBIT_OLD_COIN to VarbitType(1011, 5, 5),
    Kudos.VARBIT_POTTERY to VarbitType(1011, 6, 6),
)

/** Kudos counted from the museum's varbits, and cleaning never turning up a find twice (R29B-2). */
class MuseumKudosTest {
    private fun player() = Player(1, "alice", 3260, 3445, 0, VarTypes(6_000, MUSEUM_VARBITS))

    /** How many of [rolls] cleans turn up [obj], from a fixed seed. */
    private fun found(player: Player, obj: Int, rolls: Int = 5_000): Int {
        val random = GameRandom(42)
        return (1..rolls).count { MuseumCleaning.clean(random, player)?.first == obj }
    }

    @Test
    fun `each display filled is 10 Kudos, written to the client's count`() {
        val alice = player()
        assertEquals(0, Kudos.total(alice))
        alice.vars.setVarbit(Kudos.VARBIT_OLD_SYMBOL, 1)
        alice.vars.setVarbit(Kudos.VARBIT_POTTERY, 1)
        Kudos.refresh(alice)
        assertEquals(20, alice.vars.getVarbit(Kudos.VARBIT_KUDOS))
        assertEquals(2, Kudos.done(alice, Kudos.DIG_SITE))
        // The necklace is no Kudos.
        alice.vars.setVarbit(Kudos.VARBIT_NECKLACE, 1)
        assertEquals(20, Kudos.total(alice))
    }

    @Test
    fun `a unique turns up until it's held anywhere or on display, and the necklace until its secret is learnt`() {
        val alice = player()
        val symbol = ObjType(MuseumCleaning.OLD_SYMBOL, "Old symbol")
        assertTrue(found(alice, MuseumCleaning.OLD_SYMBOL) > 0)
        assertTrue(found(alice, MuseumCleaning.CLEAN_NECKLACE) > 0)

        alice.bank.add(symbol, 1)
        assertEquals(0, found(alice, MuseumCleaning.OLD_SYMBOL))
        alice.bank.clear()
        alice.inventory.add(symbol, 1)
        assertEquals(0, found(alice, MuseumCleaning.OLD_SYMBOL))
        alice.inventory.clear()
        alice.equipment[WearPos.NECK] = InvObj(MuseumCleaning.CLEAN_NECKLACE, 1)
        assertEquals(0, found(alice, MuseumCleaning.CLEAN_NECKLACE))
        alice.equipment.clear()

        // Dropped before it was handed in, it turns up again (guessed); on display, never.
        assertTrue(found(alice, MuseumCleaning.OLD_SYMBOL) > 0)
        alice.vars.setVarbit(Kudos.VARBIT_OLD_SYMBOL, 1)
        assertEquals(0, found(alice, MuseumCleaning.OLD_SYMBOL))
        assertTrue(found(alice, MuseumCleaning.ANCIENT_SYMBOL) > 0)
        alice.vars.setVarbit(Kudos.VARBIT_NECKLACE, 1)
        assertEquals(0, found(alice, MuseumCleaning.CLEAN_NECKLACE))
        // Pottery's case doesn't stop pottery.
        alice.vars.setVarbit(Kudos.VARBIT_POTTERY, 1)
        assertTrue(found(alice, MuseumCleaning.POTTERY) > 0)
    }
}
