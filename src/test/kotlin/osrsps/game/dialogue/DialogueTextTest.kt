package osrsps.game.dialogue

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class DialogueTextTest {
    private object Greeter : Lines("greeter", "Transcript:Greeter") {
        val HELLO = line("hello", "Talk-to: first line")
        val SCORE = line("score", "Talk-to: the score", "points", "rank")
    }

    @Test
    fun `a line shows its words, with placeholders filled`() {
        val text = DialogueText(mapOf("greeter.hello" to "Hi.", "greeter.score" to "{points} points, rank {rank}."))
        assertEquals("Hi.", text[Greeter.HELLO])
        assertEquals("12 points, rank 3.", text.get(Greeter.SCORE, "points" to 12, "rank" to 3))
    }

    @Test
    fun `a line with no words shows its key and values in brackets`() {
        assertEquals("[greeter.hello]", DialogueText.NONE[Greeter.HELLO])
        assertEquals("[greeter.score points=12 rank=3]", DialogueText.NONE.get(Greeter.SCORE, "points" to 12, "rank" to 3))
    }

    @Test
    fun `reads key = words, skipping notes and blank lines`() {
        val read = DialogueFile.parse("﻿# a note\n\ngreeter.hello = Hi = hello.  \n  greeter.score =\r\n")
        assertEquals(mapOf("greeter.hello" to "Hi = hello.", "greeter.score" to ""), read.words)
        assertEquals(emptyList(), read.problems)
    }

    @Test
    fun `says which lines it can't use`() {
        val read = DialogueFile.parse("greeter.hello = Hi.\nHello there\nGreeter.hello = Hi.\nhello = Hi.\ngreeter.hello = Again.\ngreeter.hello =\n")
        assertEquals(mapOf("greeter.hello" to "Hi."), read.words)
        assertEquals(
            listOf(
                "line 2: not 'key = words': Hello there",
                "line 3: not 'key = words': Greeter.hello = Hi.",
                "line 4: not 'key = words': hello = Hi.",
                "line 5: greeter.hello has words further up too; the first ones count",
            ),
            read.problems,
        )
    }

    @Test
    fun `words further down fill a key left empty`() {
        val read = DialogueFile.parse("greeter.hello =\ngreeter.score = 1\ngreeter.hello = Hi.\n")
        assertEquals(mapOf("greeter.hello" to "Hi.", "greeter.score" to "1"), read.words)
        assertEquals(emptyList(), read.problems)
    }

    @Test
    fun `checks keys and placeholders against the lines`() {
        val problems = DialogueFile.check(
            mapOf("greeter.hello" to "Hi {name}.", "greeter.score" to "{points} {rank}", "greeter.bye" to "Bye."),
            listOf(Greeter),
        )
        assertEquals(
            listOf("greeter.hello: {name} isn't filled in; it can use no placeholders", "greeter.bye isn't a line the server has"),
            problems,
        )
    }

    @Test
    fun `loads a file and counts the lines with words`(@TempDir dir: Path) {
        val file = dir.resolve(DialogueFile.NAME)
        Files.writeString(file, "greeter.hello = Hi.\ngreeter.score =\n")
        val loaded = DialogueFile.load(file, listOf(Greeter))
        assertEquals("1 of 2 dialogue lines have words in dialogue.txt; 1 show their keys (greeter.score)", loaded.summary)
        assertEquals("Hi.", loaded.text[Greeter.HELLO])
        assertFalse(loaded.text.has(Greeter.SCORE))
        assertEquals(emptyList(), loaded.problems)
    }

    @Test
    fun `a missing file leaves every line showing its key`(@TempDir dir: Path) {
        val loaded = DialogueFile.load(dir.resolve(DialogueFile.NAME), listOf(Greeter))
        assertEquals("No dialogue.txt: all 2 dialogue lines show their keys", loaded.summary)
        assertEquals("[greeter.hello]", loaded.text[Greeter.HELLO])
    }

    @Test
    fun `writes every key under its source, keeping words, and keeps words for keys it no longer has`() {
        val file = DialogueFile.render(listOf(Greeter), mapOf("greeter.score" to "{points}!", "greeter.old" to "Gone."))
        val body = file.lines().dropWhile { it.startsWith("#") }.joinToString("\n").trim()
        assertEquals(
            """
            # ===== greeter: https://oldschool.runescape.wiki/w/Transcript:Greeter

            # Talk-to: first line
            greeter.hello =

            # Talk-to: the score (placeholders: {points} {rank})
            greeter.score = {points}!

            # ===== Not used by the server any more (renamed or removed); kept so nothing is lost
            greeter.old = Gone.
            """.trimIndent(),
            body,
        )
        // Read back, it is the same words.
        assertEquals(mapOf("greeter.hello" to "", "greeter.score" to "{points}!", "greeter.old" to "Gone."), DialogueFile.parse(file).words)
    }

    @Test
    fun `line names must be keys`() {
        assertFailsWith<IllegalArgumentException> { object : Lines("Bad Prefix", "X") {} }
        val e = assertFailsWith<IllegalArgumentException> {
            object : Lines("twice", "X") {
                val A = line("a", "here")
                val B = line("a", "there")
            }
        }
        assertTrue(e.message!!.contains("twice.a is declared twice"), e.message)
    }
}
