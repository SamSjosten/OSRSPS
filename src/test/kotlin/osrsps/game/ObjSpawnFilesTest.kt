package osrsps.game

import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.net.URLClassLoader
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class ObjSpawnFilesTest {
    private val types = ObjTypes.of(
        ObjType(1351, "Bronze axe", internalName = "bronze_axe"),
        ObjType(995, "Coins", stackable = true, internalName = "coins"),
    )

    @Test
    fun `reads spawns by gameval name or id, skipping the header, blank lines and comments`() {
        val text = """
            obj	count	x	z	level	respawn	# source
            bronze_axe	1	3225	3218	0	100	# wiki, respawn guessed

            # a whole-line comment
            995	25	3226	3219	1	40
        """.trimIndent()
        assertEquals(
            listOf(ObjSpawn(1351, 1, 3225, 3218, 0, 100), ObjSpawn(995, 25, 3226, 3219, 1, 40)),
            ObjSpawnFiles.parse(text, types, "test.tsv"),
        )
    }

    @Test
    fun `a bad line fails with its file and line`() {
        fun fails(line: String) = assertFailsWith<IllegalArgumentException> { ObjSpawnFiles.parse("\n$line", types, "castle.tsv") }.message!!
        assertTrue(fails("bronze_axe\t1\t3225\t3218\t0").startsWith("castle.tsv line 2: expected 6"))
        assertTrue(fails("iron_axe\t1\t3225\t3218\t0\t100").contains("no obj 'iron_axe'"))
        assertTrue(fails("bronze_axe\t2\t3225\t3218\t0\t100").contains("doesn't stack"))
        assertTrue(fails("coins\t5\t3225\t3218\t4\t100").contains("level 4"))
        assertTrue(fails("coins\t5\t3225\t3218\t0\t0").contains("respawn time"))
        assertTrue(fails("coins\tfive\t3225\t3218\t0\t100").contains("'five' is not a number"))
    }

    @Test
    fun `Lumbridge Castle's spawn file reads, with the 241 cache's ids for its names`() {
        // Names and ids as the 241 cache's gameval index has them (checked 2026-10-25).
        val castle = ObjTypes.of(
            ObjType(558, "Mind rune", stackable = true, internalName = "mindrune"),
            ObjType(882, "Bronze arrow", stackable = true, internalName = "bronze_arrow"),
            ObjType(946, "Knife", internalName = "knife"),
            ObjType(1061, "Leather boots", internalName = "leather_boots"),
            ObjType(1205, "Bronze dagger", internalName = "bronze_dagger"),
            ObjType(1265, "Bronze pickaxe", internalName = "bronze_pickaxe"),
            ObjType(1511, "Logs", internalName = "logs"),
            ObjType(1923, "Bowl", internalName = "bowl_empty"),
            ObjType(1925, "Bucket", internalName = "bucket_empty"),
            ObjType(1931, "Pot", internalName = "pot_empty"),
            ObjType(1935, "Jug", internalName = "jug_empty"),
            ObjType(1965, "Cabbage", internalName = "cabbage"),
        )
        val text = ObjSpawnFiles.resources().toMap().getValue("lumbridge-castle.tsv")
        val spawns = ObjSpawnFiles.parse(text, castle, "lumbridge-castle.tsv")
        assertEquals(20, spawns.size)
        assertEquals(4, spawns.count { it.id == 1511 && it.level == 2 }, "the top floor's four logs")
        assertEquals(6, spawns.count { it.z > 6400 }, "the cellar's")
    }

    @Test
    fun `Lumbridge's spawn file outside the castle reads, with the 241 cache's ids for its names`() {
        // Names and ids as the rev 241 gameval symbols have them (Joshua-F/osrs-dumps, checked 2026-10-29).
        val town = ObjTypes.of(
            ObjType(556, "Air rune", stackable = true, internalName = "airrune"),
            ObjType(1059, "Leather gloves", internalName = "leather_gloves"),
            ObjType(1203, "Iron dagger", internalName = "iron_dagger"),
            ObjType(1735, "Shears", internalName = "shears"),
            ObjType(1925, "Bucket", internalName = "bucket_empty"),
            ObjType(1931, "Pot", internalName = "pot_empty"),
            ObjType(1939, "Swamp tar", stackable = true, internalName = "swamp_tar"),
            ObjType(1944, "Egg", internalName = "egg"),
            ObjType(1957, "Onion", internalName = "onion"),
        )
        val text = ObjSpawnFiles.resources().toMap().getValue("lumbridge.tsv")
        val spawns = ObjSpawnFiles.parse(text, town, "lumbridge.tsv")
        assertEquals(6, spawns.count { it.id == 1944 }, "the Wiki's 4 eggs at Fred's farm and 2 at the Groats' farm house")
        assertEquals(17, spawns.count { it.id == 1939 }, "the Wiki's 17 swamp tar")
        assertEquals(2, spawns.count { it.id == 1735 })
        assertEquals(listOf(1), spawns.filter { it.id == 1957 }.map { it.level }, "the onion upstairs in the general store")
    }

    @Test
    fun `Edgeville's spawns are the Wiki's leather gloves south of the bank and iron mace in the jail`() {
        val town = ObjTypes.of(ObjType(1059, "Leather gloves", internalName = "leather_gloves"), ObjType(1420, "Iron mace", internalName = "iron_mace"))
        val text = ObjSpawnFiles.resources().toMap().getValue("edgeville.tsv")
        val spawns = ObjSpawnFiles.parse(text, town, "edgeville.tsv")
        assertEquals(listOf(1059, 1420), spawns.map { it.id })
        assertEquals(listOf(3097 to 3486, 3111 to 3517), spawns.map { it.x to it.z })
    }

    @Test
    fun `Draynor's spawns are the Wiki's cheese and tomato in Aggie's house and two logs in Spria's`() {
        val village = ObjTypes.of(
            ObjType(1511, "Logs", internalName = "logs"),
            ObjType(1982, "Tomato", internalName = "tomato"),
            ObjType(1985, "Cheese", internalName = "cheese"),
        )
        val text = ObjSpawnFiles.resources().toMap().getValue("draynor.tsv")
        val spawns = ObjSpawnFiles.parse(text, village, "draynor.tsv")
        assertEquals(listOf(1985, 1982, 1511, 1511), spawns.map { it.id })
        // Aggie's house is west of x 3089, Spria's the room west of Morgan's.
        assertTrue(spawns.take(2).all { it.x in 3083..3088 && it.z in 3256..3263 })
        assertTrue(spawns.drop(2).all { it.x in 3089..3095 && it.z in 3264..3268 })
    }

    @Test
    fun `finds the spawn files in a folder and in a jar`() {
        val dir = createTempDirectory("obj-spawns")
        val spawns = dir.resolve(ObjSpawnFiles.RESOURCE_DIR).createDirectories()
        spawns.resolve("b.tsv").writeText("coins\t1\t1\t1\t0\t5")
        spawns.resolve("a.tsv").writeText("bronze_axe\t1\t1\t1\t0\t5")
        spawns.resolve("README.md").writeText("not a spawn file")
        URLClassLoader(arrayOf(dir.toUri().toURL()), null).use { loader ->
            assertEquals(listOf("a.tsv", "b.tsv"), ObjSpawnFiles.resources(loader).map { it.first })
        }

        val jar = dir.resolve("content.jar")
        JarOutputStream(jar.outputStream()).use { out ->
            out.putNextEntry(JarEntry("${ObjSpawnFiles.RESOURCE_DIR}/"))
            out.putNextEntry(JarEntry("${ObjSpawnFiles.RESOURCE_DIR}/castle.tsv"))
            out.write("coins\t1\t1\t1\t0\t5".toByteArray())
        }
        URLClassLoader(arrayOf(jar.toUri().toURL()), null).use { loader ->
            assertEquals(listOf("castle.tsv" to "coins\t1\t1\t1\t0\t5"), ObjSpawnFiles.resources(loader))
        }
    }

    @Test
    fun `no spawn folder means no spawns`() {
        URLClassLoader(arrayOf(createTempDirectory("empty").toUri().toURL()), null).use { loader ->
            assertEquals(emptyList(), ObjSpawnFiles.resources(loader))
        }
    }
}
