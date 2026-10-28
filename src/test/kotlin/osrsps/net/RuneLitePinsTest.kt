package osrsps.net

import osrsps.Revision
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class RuneLitePinsTest {
    @Test
    fun `the server's revision has a RuneLite build`() {
        // A revision bump has to pin the RuneLite build that plays it, or RSProx friends follow live and get turned away.
        assertNotNull(RuneLitePins.load()[Revision.MAJOR], "pin a RuneLite build for revision ${Revision.MAJOR} in ${RuneLitePins.RESOURCE}")
    }

    @Test
    fun `revision 241 is RuneLite 1_13_4`() {
        val pin = RuneLitePins.load()[241]!!
        assertEquals("1.13.4", pin.runelite)
        assertEquals("1e634e64717418713898b06061a189ec1cdf948d", pin.commit)
        assertEquals("25f22ff874ddf8c4016c8d03150c24920cdaa536ae7ad44aed36173c826c0b2a", pin.bootstrapSha256)
    }

    @Test
    fun `a commit or hash that isn't all lowercase hex fails the file`() {
        val short = assertFailsWith<IllegalArgumentException> { RuneLitePins.parse(file(commit = "1e634e6")) }
        assertContains(short.message!!, "isn't 40 lowercase hex digits")
        assertFailsWith<IllegalArgumentException> { RuneLitePins.parse(file(commit = COMMIT.uppercase())) }
        assertFailsWith<IllegalArgumentException> { RuneLitePins.parse(file(commit = "../$COMMIT".take(40))) }
        val hash = assertFailsWith<IllegalArgumentException> { RuneLitePins.parse(file(sha = SHA256.dropLast(1))) }
        assertContains(hash.message!!, "isn't 64 lowercase hex digits")
    }

    @Test
    fun `a revision pinned twice fails the file`() {
        val entry = """{ "revision": 241, "runelite": "1.13.4", "commit": "$COMMIT", "bootstrapSha256": "$SHA256", "source": "test" }"""
        val failure = assertFailsWith<IllegalArgumentException> { RuneLitePins.parse("""{ "pins": [ $entry, $entry ] }""") }
        assertContains(failure.message!!, "pinned twice")
    }

    private fun file(commit: String = COMMIT, sha: String = SHA256) =
        """{ "pins": [ { "revision": 241, "runelite": "1.13.4", "commit": "$commit", "bootstrapSha256": "$sha", "source": "test" } ] }"""

    private companion object {
        const val COMMIT = "1e634e64717418713898b06061a189ec1cdf948d"
        const val SHA256 = "25f22ff874ddf8c4016c8d03150c24920cdaa536ae7ad44aed36173c826c0b2a"
    }
}
