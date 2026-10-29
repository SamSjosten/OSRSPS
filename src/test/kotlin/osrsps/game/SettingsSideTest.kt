package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsSideTest {
    @Test
    fun `the 241 varbits report nothing`() {
        assertEquals(emptyList(), SettingsSide.checkVarbits(VarTypes(6_000, SettingsSide.VARBITS)))
    }

    @Test
    fun `a varbit a revision moved or dropped is reported`() {
        val moved = SettingsSide.VARBITS + (SettingsSide.VARBIT_SKULL_PREVENTION to VarbitType(3075, 17, 17)) - SettingsSide.VARBIT_IRONMAN
        assertEquals(
            listOf(
                "varbit 13131 should be VarbitType(baseVar=3075, startBit=16, endBit=16) but is VarbitType(baseVar=3075, startBit=17, endBit=17)",
                "varbit 1777 should be VarbitType(baseVar=499, startBit=3, endBit=5) but is null",
            ),
            SettingsSide.checkVarbits(VarTypes(6_000, moved)).sorted(),
        )
    }
}
