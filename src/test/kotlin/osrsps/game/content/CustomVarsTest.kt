package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.cache.VarTypeEncoder
import osrsps.cache.VarTypeLoader
import osrsps.game.VarTypes
import osrsps.game.VarbitType

class CustomVarsTest {
    /** OpenRS2's build 2746 of revision 241 (VM 214's cache): varps to 5,995 and varbits to 20,410. */
    private val build2746 = VarTypes(5996, mapOf(20410 to VarbitType(5995, 0, 3)), 20411)

    /** Another 241 build, whose varps end at 5,890: the ids the code first had, which build 2746 refused. */
    private val olderBuild = VarTypes(5891, mapOf(20410 to VarbitType(5890, 0, 3)), 20411)

    @Test
    fun `the ids follow the loaded cache's, whichever build it is`() {
        val vars = CustomVars.after(build2746)
        assertEquals(5996, vars.firstVarp)
        assertEquals(5996, vars.lodestonesVarp)
        assertEquals(20411, vars.firstVarbit)
        assertEquals(20411, vars.lodestoneVarbit(Lodestones.LUMBRIDGE))
        assertEquals(20413, vars.lodestoneVarbit(Lodestones.VARROCK))
        assertEquals(20416, vars.lodestonePreviousVarbit, "after the sites'")
        assertEquals("varp 5996, varbits 20411 to 20416", vars.toString())

        val older = CustomVars.after(olderBuild)
        assertEquals(5891, older.lodestonesVarp)
        assertEquals(20411, older.lodestoneVarbit(Lodestones.LUMBRIDGE))

        val none = CustomVars.after(VarTypes.NONE)
        assertEquals(6_000, none.lodestonesVarp, "past every real 241 id, for tests")
        assertEquals(21_000, none.lodestoneVarbit(Lodestones.LUMBRIDGE))
    }

    @Test
    fun `the overlay carries a type for our varp and each varbit, past the cache's ids, that the loader reads back`() {
        for (cache in listOf(build2746, olderBuild)) {
            val vars = CustomVars.after(cache)
            val files = vars.overlayFiles
            val varps = files.filter { it.group == CustomVars.VARP_GROUP }
            val varbits = files.filter { it.group == CustomVars.VARBIT_GROUP }
            assertEquals(files.size, varps.size + varbits.size)
            assertTrue(files.all { it.index == 2 })
            assertEquals(listOf(vars.lodestonesVarp, vars.harlanClaimVarp), varps.map { it.file })
            assertTrue(varps.all { it.file >= cache.varpCount }, "a varp id the cache has would be refused at startup")
            assertTrue(varps.all { it.data.contentEquals(byteArrayOf(0)) })
            assertEquals(Lodestones.SITES.map { vars.lodestoneVarbit(it) } + vars.lodestonePreviousVarbit, varbits.map { it.file })
            for (file in varbits) {
                assertEquals(vars.varbits[file.file], VarTypeLoader.decodeVarbit(file.data))
                assertTrue(file.file >= cache.varbitCount, "a varbit id the cache has would be refused at startup")
            }
        }
        val vars = CustomVars.after(build2746)
        assertEquals(VarbitType(5996, 2, 2), vars.varbits[20413], "Varrock's: bit 2 of the varp")
        assertEquals(VarbitType(5996, 8, 11), vars.varbits[20416], "the previous destination's: bits 8 to 11, above the sites'")
    }

    @Test
    fun `the vars go by name, for the chatbox and the runner scripts, which can't know a cache's ids`() {
        val vars = CustomVars.after(build2746)
        assertEquals(mapOf("lodestones" to 5996, "harlan.claimed" to 5997), vars.varpNames)
        assertEquals(5996, vars.varp("Lodestones"))
        assertNull(vars.varp("5996"))
        assertEquals(
            mapOf(
                "lodestone.lumbridge" to 20411, "lodestone.draynor" to 20412, "lodestone.varrock" to 20413, "lodestone.al_kharid" to 20414,
                "lodestone.edgeville" to 20415, "lodestone.previous" to 20416,
            ),
            vars.varbitNames,
        )
        assertEquals(20414, vars.varbit("lodestone.AL_KHARID"))
        assertNull(vars.varbit("al_kharid"))
    }

    @Test
    fun `the varbit encoder writes opcode 1 as the loader reads it`() {
        assertContentEquals(byteArrayOf(1, 0x17, 0x03, 5, 7, 0), VarTypeEncoder.encodeVarbit(VarbitType(5891, 5, 7)))
        assertEquals(VarbitType(300, 0, 31), VarTypeLoader.decodeVarbit(VarTypeEncoder.encodeVarbit(VarbitType(300, 0, 31))))
    }

    @Test
    fun `a world's var types take ours on top of the cache's, and grow to hold our varp and varbits`() {
        val vars = CustomVars.after(build2746)
        val merged = build2746.with(vars.varbits)
        assertEquals(5997, merged.varpCount)
        assertEquals(20417, merged.varbitCount)
        assertEquals(VarbitType(5995, 0, 3), merged.varbit(20410))
        assertEquals(vars.varbits[vars.lodestoneVarbit(Lodestones.EDGEVILLE)], merged.varbit(20415))
        assertEquals(6_001, VarTypes.NONE.with(CustomVars.after(VarTypes.NONE).varbits).varpCount)
        assertEquals(build2746, build2746.with(emptyMap()))
        assertFailsWith<IllegalArgumentException>("a cache that has taken our id") {
            VarTypes(6_000, mapOf(20411 to VarbitType(5, 0, 0)), 20412).with(vars.varbits)
        }
        assertFailsWith<IllegalArgumentException>("a varbit past the count") { VarTypes(6_000, mapOf(5 to VarbitType(5, 0, 0)), 5) }

        // Our varps by name too, for the saves, with the cache's own count kept for telling an old numeric id of ours apart.
        val named = build2746.with(vars.varbits, vars.varpNames)
        assertEquals(mapOf("lodestones" to 5996, "harlan.claimed" to 5997), named.namedVarps)
        assertEquals("lodestones", named.varpName(5996))
        assertNull(named.varpName(5995))
        assertEquals(5996, named.cacheVarpCount)
        assertEquals(5998, named.varpCount)
        assertEquals(5996, build2746.cacheVarpCount, "a cache's own types: every varp is its")
        assertFailsWith<IllegalArgumentException>("a name used twice") { named.with(emptyMap(), mapOf("lodestones" to 5998)) }
        assertFailsWith<IllegalArgumentException>("a named varp among the cache's") { VarTypes(6_000, emptyMap(), 0, mapOf("x" to 5)) }
        assertFailsWith<IllegalArgumentException>("two names, one varp") { VarTypes(6_002, emptyMap(), 0, mapOf("x" to 6_001, "y" to 6_001), 6_000) }
        assertEquals(6, VarTypes(6_000, mapOf(5 to VarbitType(5, 0, 0))).varbitCount, "the count defaults to the highest id plus one")
    }
}
