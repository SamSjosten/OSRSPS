package osrsps.game.content

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class DialogueLinesTest {
    @Test
    fun `every Lines object in the content is listed, so dialogue-keys writes its keys`() {
        val declared = Files.list(Path.of("src/main/kotlin/osrsps/game/content")).use { files ->
            files.toList().flatMap { file -> DECLARATION.findAll(file.readText()).map { it.groupValues[1] }.toList() }
        }
        assertEquals(declared.sorted(), DialogueLines.ALL.map { it::class.simpleName!! }.sorted())
    }

    @Test
    fun `no two conversations share a key prefix`() {
        val prefixes = DialogueLines.ALL.map { it.prefix }
        assertEquals(prefixes.distinct(), prefixes)
    }

    private companion object {
        val DECLARATION = Regex("""object (\w+) : Lines\(""")
    }
}
