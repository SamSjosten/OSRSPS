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
