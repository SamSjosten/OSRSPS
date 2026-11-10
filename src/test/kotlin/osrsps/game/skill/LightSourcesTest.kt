package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.WearPos

class LightSourcesTest {
    private fun player() = Player(1, "test", 3222, 3218, 0)

    @Test
    fun `the Wiki's table, level and brightness and which are naked flames`() {
        val table = LightSources.ALL.associate { it.lit to Triple(it.level, it.brightness, it.nakedFlame) }
        assertEquals(Triple(1, 1, true), table[LightSources.LIT_CANDLE])
        assertEquals(Triple(1, 1, true), table[LightSources.LIT_TORCH])
        assertEquals(Triple(4, 1, false), table[LightSources.CANDLE_LANTERN_LIT])
        assertEquals(Triple(12, 2, true), table[LightSources.OIL_LAMP_LIT])
        assertEquals(Triple(26, 2, false), table[LightSources.OIL_LANTERN_LIT])
        assertEquals(Triple(49, 3, false), table[LightSources.BULLSEYE_LANTERN_LIT])
        assertEquals(Triple(65, 2, false), table[LightSources.MINING_HELMET_LIT])
        // Only the bullseye lantern is out of reach of every effect.
        assertEquals(listOf(LightSources.BULLSEYE_LANTERN_LIT), LightSources.ALL.filter { !it.extinguishable }.map { it.lit })
    }

    @Test
    fun `brightness adds up to 3 from carried and worn lights, and unlit ones give none`() {
        val player = player()
        assertFalse(LightSources.hasLight(player))
        player.inventory[0] = InvObj(LightSources.CANDLE, 1)
        assertEquals(0, LightSources.brightness(player))
        player.inventory[1] = InvObj(LightSources.LIT_CANDLE, 1)
        assertEquals(1, LightSources.brightness(player))
        player.equipment[WearPos.HEAD] = InvObj(LightSources.MINING_HELMET_LIT, 1)
        assertEquals(3, LightSources.brightness(player))
        player.inventory[2] = InvObj(LightSources.BULLSEYE_LANTERN_LIT, 1)
        assertEquals(3, LightSources.brightness(player))
        // The Firemaking cape is always lit.
        val caped = player().apply { equipment[WearPos.CAPE] = InvObj(9804, 1) }
        assertTrue(LightSources.hasLight(caped))
    }

    @Test
    fun `swamp gas finds the naked flames, and putting out spares the bullseye lantern`() {
        val player = player()
        player.inventory[0] = InvObj(LightSources.LIT_CANDLE, 1)
        player.inventory[1] = InvObj(LightSources.OIL_LANTERN_LIT, 1)
        player.inventory[2] = InvObj(LightSources.BULLSEYE_LANTERN_LIT, 1)
        player.equipment[WearPos.HEAD] = InvObj(LightSources.MINING_HELMET_LIT, 1)
        assertEquals(listOf(LightSources.LIT_CANDLE), LightSources.nakedFlames(player).map { it.lit })

        assertEquals(1, LightSources.extinguish(player) { it.nakedFlame })
        assertEquals(LightSources.CANDLE, player.inventory[0]?.id)
        assertEquals(LightSources.OIL_LANTERN_LIT, player.inventory[1]?.id)

        assertEquals(2, LightSources.extinguish(player))
        assertEquals(LightSources.OIL_LANTERN, player.inventory[1]?.id)
        assertEquals(LightSources.BULLSEYE_LANTERN_LIT, player.inventory[2]?.id)
        assertEquals(LightSources.MINING_HELMET, player.equipment[WearPos.HEAD]?.id)
        assertTrue(LightSources.hasLight(player))
    }
}
