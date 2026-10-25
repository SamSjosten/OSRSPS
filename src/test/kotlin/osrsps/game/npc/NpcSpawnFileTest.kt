package osrsps.game.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import osrsps.game.NpcType
import osrsps.game.NpcTypes

class NpcSpawnFileTest {
    private val types = NpcTypes.of(
        NpcType(3105, "Hans", internalName = "hans"),
        NpcType(2790, "Cow", size = 2, internalName = "cow"),
        NpcType(2791, "Cow", size = 2, internalName = "cow2"),
        NpcType(2793, "Cow", size = 2, internalName = "cow3"),
        NpcType(2792, "Cow calf", size = 2, internalName = "cow2_calf"),
        NpcType(2794, "Cow calf", size = 2, internalName = "cow3_calf"),
    )

    private fun file(name: String) = NpcSpawnFile::class.java.getResource("/npc-spawns/$name")!!.readText()

    @Test
    fun `the Lumbridge file has the cows east of the river, each with a source`() {
        val spawns = NpcSpawnFile.parse(file("lumbridge.json"), types).spawns
        val cows = spawns.filter { it.type.name == "Cow" }
        assertEquals(21, cows.size)
        assertEquals(8, spawns.count { it.type.name == "Cow calf" })
        // The River Lum runs north from Lumbridge at about x 3240; the field is east of it.
        assertTrue(cows.all { it.x > 3240 && it.z in 3250..3300 })
        assertTrue(spawns.all { it.source.isNotBlank() && it.wander > 0 })
    }

    @Test
    fun `the Lumbridge Castle file reads, with the 241 cache's names`() {
        // Ids and gameval names as the 241 cache has them (checked 2026-10-25).
        val castle = NpcTypes.of(
            NpcType(3105, "Hans", internalName = "hans"),
            NpcType(4626, "Cook", internalName = "cook"),
            NpcType(7457, "null", internalName = "lost_property_merchant_standard"),
            NpcType(3106, "Man", internalName = "man"),
            NpcType(3108, "Man", internalName = "man3"),
            NpcType(3111, "Woman", internalName = "woman"),
            NpcType(5007, "Imp", internalName = "imp"),
            NpcType(2854, "Rat", internalName = "rat"),
            NpcType(815, "Duke Horacio", internalName = "duke_of_lumbridge"),
            NpcType(3220, "Crafting tutor", internalName = "aide_tutor_crafting"),
            NpcType(6520, "null", internalName = "deadman_banker_blue_south"),
            NpcType(3227, "Banker tutor", internalName = "aide_tutor_banker"),
            NpcType(306, "Lumbridge Guide", internalName = "lumbridge_guide"),
            NpcType(119, "null", internalName = "cws_doomsayer"),
            NpcType(5523, "Hatius Cosaintus", internalName = "hatius_lumbridge_diary"),
        )
        val result = NpcSpawnFile.parse(file("lumbridge-castle.json"), castle, "lumbridge-castle.json")
        assertEquals(emptyList(), result.unknown)
        val spawns = result.spawns
        assertEquals(28, spawns.size)
        assertEquals(10, spawns.count { it.type.id == 2854 }, "rats")
        assertEquals(5, spawns.count { it.level == 1 })
        val hans = spawns.single { it.type.id == 3105 }
        assertEquals(10, hans.patrol.size)
        assertEquals(10, hans.patrol.sumOf { it.wait })
        assertEquals(MoveRestrict.OUTDOORS, hans.restrict)
        assertEquals(MoveRestrict.INDOORS, spawns.single { it.type.id == 4626 }.restrict)
        // Everyone but Hans wanders or stands still on purpose.
        assertTrue(spawns.filter { it.patrol.isEmpty() }.all { it.wander == 0 || it.wander == 5 })
    }

    @Test
    fun `every file names only NPCs it can find in a full cache`() {
        // With no types at all every spawn is skipped by name, not dropped silently.
        for (file in NpcSpawnFile.FILES) {
            val text = NpcSpawnFile::class.java.getResource("/npc-spawns/$file")!!.readText()
            val result = NpcSpawnFile.parse(text, NpcTypes.NONE, file)
            assertTrue(result.spawns.isEmpty())
            assertTrue(result.unknown.isNotEmpty())
        }
    }

    @Test
    fun `optional fields, patrols and ids`() {
        val text = """
            { "about": "test", "spawns": [
              { "npc": 3105, "x": 1, "z": 2, "level": 1, "direction": "nw", "patrol": [[1, 2], [3, 4, 10]], "source": "a" },
              { "npc": "Cow calf", "x": 5, "z": 6, "wander": 2, "source": "b" },
              { "npc": "goblin", "x": 5, "z": 6, "source": "c" }
            ] }
        """
        val result = NpcSpawnFile.parse(text, types)
        val (hans, calf) = result.spawns
        assertEquals(Direction.NORTH_WEST, hans.direction)
        assertEquals(1, hans.level)
        assertEquals(listOf(PatrolStop(1, 2), PatrolStop(3, 4, 10)), hans.patrol)
        assertEquals(0, hans.wander)
        assertEquals(2792, calf.type.id)
        assertEquals(Direction.SOUTH, calf.direction)
        assertEquals(listOf("goblin"), result.unknown)
    }

    @Test
    fun `malformed entries name the file and entry`() {
        fun fails(entry: String): String = assertFailsWith<IllegalArgumentException> {
            NpcSpawnFile.parse("""{ "spawns": [ $entry ] }""", types, "x.json")
        }.message!!
        assertTrue(fails("""{ "npc": "hans", "x": 1, "z": 2 }""").startsWith("x.json: spawn 1: no \"source\""))
        assertTrue(fails("""{ "npc": "hans", "x": 1, "z": 2, "source": " " }""").contains("blank source"))
        assertTrue(fails("""{ "npc": "hans", "x": 1, "z": 2, "direction": "up", "source": "a" }""").contains("direction 'up'"))
        assertTrue(fails("""{ "npc": "hans", "x": 1, "z": 2, "patrol": [[1]], "source": "a" }""").contains("[x, z]"))
        assertTrue(fails("""{ "npc": "hans", "x": 1, "z": 2, "patrol": [[1, 2, 3, 4]], "source": "a" }""").contains("[x, z, wait]"))
        assertTrue(fails("""{ "npc": "hans", "x": 1, "z": 2, "patrol": [[1, 2, -1]], "source": "a" }""").contains("wait -1"))
        assertTrue(fails("""{ "npc": "hans", "x": 1, "z": 2, "level": 4, "source": "a" }""").contains("level 4"))
        assertTrue(fails("""{ "npc": "hans", "x": 1, "z": 2, "restrict": "underground", "source": "a" }""").contains("restrict 'underground'"))
        assertTrue(fails("""{ "npc": "hans", "z": 2, "source": "a" }""").contains("no \"x\""))
    }
}
