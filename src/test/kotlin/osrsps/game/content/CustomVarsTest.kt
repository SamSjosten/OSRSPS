package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import osrsps.cache.VarTypeEncoder
import osrsps.cache.VarTypeLoader
import osrsps.game.VarTypes
import osrsps.game.VarbitType

class CustomVarsTest {
    @Test
    fun `the overlay carries a type for our varp and each varbit, past the cache's ids, that the loader reads back`() {
        val files = CustomVars.overlayFiles
        val varps = files.filter { it.group == CustomVars.VARP_GROUP }
        val varbits = files.filter { it.group == CustomVars.VARBIT_GROUP }
        assertEquals(files.size, varps.size + varbits.size)
        assertTrue(files.all { it.index == 2 })
        assertEquals(listOf(CustomVars.LODESTONES_VARP), varps.map { it.file })
        assertContentEquals(byteArrayOf(0), varps.single().data)
        assertEquals(Lodestones.SITES.map { it.varbit }, varbits.map { it.file })
        for (file in varbits) {
            assertEquals(CustomVars.varbits[file.file], VarTypeLoader.decodeVarbit(file.data))
            assertTrue(file.file >= CustomVars.FIRST_VARBIT)
        }
        assertEquals(VarbitType(5891, 2, 2), CustomVars.varbits[20413], "Varrock's: bit 2 of the varp")
        assertTrue(CustomVars.LODESTONES_VARP >= CustomVars.FIRST_VARP)
    }

    @Test
    fun `the varbit encoder writes opcode 1 as the loader reads it`() {
        assertContentEquals(byteArrayOf(1, 0x17, 0x03, 5, 7, 0), VarTypeEncoder.encodeVarbit(VarbitType(5891, 5, 7)))
        assertEquals(VarbitType(300, 0, 31), VarTypeLoader.decodeVarbit(VarTypeEncoder.encodeVarbit(VarbitType(300, 0, 31))))
    }

    @Test
    fun `a world's var types take ours on top of the cache's, and grow to hold our varp`() {
        val cache = VarTypes(5891, mapOf(1 to VarbitType(0, 0, 0)))
        val merged = cache.with(CustomVars.varbits)
        assertEquals(5892, merged.varpCount)
        assertEquals(1 + CustomVars.varbits.size, merged.varbitCount)
        assertEquals(VarbitType(0, 0, 0), merged.varbit(1))
        assertEquals(CustomVars.varbits[Lodestones.EDGEVILLE.varbit], merged.varbit(Lodestones.EDGEVILLE.varbit))
        assertEquals(6_000, VarTypes.NONE.with(CustomVars.varbits).varpCount, "already big enough")
        assertEquals(cache, cache.with(emptyMap()))
        assertFailsWith<IllegalArgumentException>("a cache that has taken our id") {
            VarTypes(6_000, mapOf(CustomVars.FIRST_VARBIT to VarbitType(5, 0, 0))).with(CustomVars.varbits)
        }
    }
}
