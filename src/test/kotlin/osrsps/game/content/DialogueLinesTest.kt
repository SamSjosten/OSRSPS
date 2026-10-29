package osrsps.game.content

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class DialogueLinesTest {
    @Test
    fun `every Lines object in the content is listed, so dialogue-keys writes its keys`() {
        val declared = CONTENT_DIRS.flatMap { dir ->
            Files.list(Path.of(dir)).use { files ->
                files.toList().filter(Files::isRegularFile).flatMap { file -> DECLARATION.findAll(file.readText()).map { it.groupValues[1] }.toList() }
            }
        }
        assertEquals(declared.sorted(), DialogueLines.ALL.map { it::class.simpleName!! }.sorted())
    }

    @Test
    fun `no two conversations share a key prefix`() {
        val prefixes = DialogueLines.ALL.map { it.prefix }
        assertEquals(prefixes.distinct(), prefixes)
    }

    private companion object {
        /** Where lines are declared: the engine's game messages, NPCs and places, the skills, combat, the bank and shops. */
        val CONTENT_DIRS = listOf(
            "src/main/kotlin/osrsps/game",
            "src/main/kotlin/osrsps/game/content",
            "src/main/kotlin/osrsps/game/skill",
            "src/main/kotlin/osrsps/game/combat",
            "src/main/kotlin/osrsps/game/bank",
            "src/main/kotlin/osrsps/game/shop",
        )
        val DECLARATION = Regex("""object (\w+) : \w*Lines\(""")
    }
}
