package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.DraynorJail
import osrsps.game.content.DraynorVillage
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Draynor piece step 5's talks through the headless client and the registered handlers: Leela, and Morgan, the
 * map's multi-NPC that shows his first talk until it sets varbit 15611 and his later one after. The rl-client script
 * test-runner/examples/draynor-jail.json does the same in Draynor.
 */
class HeadlessDraynorTalkTest {
    private val leela = NpcType(DraynorJail.LEELA, "Leela", ops = listOf("Talk-to", null, null, null, null))
    private val morganFirst = NpcType(DraynorVillage.MORGAN_FIRST, "Morgan", ops = listOf("Talk-to", null, null, null, null))
    private val morganLater = NpcType(DraynorVillage.MORGAN_LATER, "Morgan", ops = listOf("Talk-to", null, null, null, null))

    /** The map's Morgan: [morganFirst] while varbit 15611 is 0, [morganLater] at 1. */
    private val morgan = NpcType(
        MORGAN, "Morgan", multiVarbit = DraynorVillage.MORGAN_VARBIT,
        multiNpcs = intArrayOf(DraynorVillage.MORGAN_FIRST, DraynorVillage.MORGAN_LATER, -1),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            npcTypes = NpcTypes.of(leela, morganFirst, morganLater, morgan),
            npcSpawns = listOf(NpcSpawn(leela, 3221, 3220, source = "test"), NpcSpawn(morgan, 3223, 3220, source = "test")),
            varTypes = VarTypes(6_000, mapOf(DraynorVillage.MORGAN_VARBIT to VarbitType(MORGAN_VARP, 0, 0))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.opNpc(id: Int, op: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, op)
    }

    /** Continues each page that follows, checking it shows key [ns].key, then waits for the talk to end. */
    private fun HeadlessClient.talk(ns: String, vararg keys: String) {
        for (key in keys) {
            awaitDialogue("$ns.$key") { it is DialogueView.Chat && it.text == "[$ns.$key]" }
            continueDialogue()
        }
        await("the talk to end") { if (dialogueView == null) Unit else null }
    }

    @Test
    fun `Talk-to on Leela runs her talk`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.opNpc(DraynorJail.LEELA, DraynorJail.TALK_TO)
                client.talk("leela", "friend", "here", "point", "luck")
            }
        }
    }

    @Test
    fun `Morgan gives his first talk once, then his later one`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.opNpc(MORGAN, DraynorVillage.TALK_TO)
                client.talk("morgan", "no_more", "hero", "helped")
                client.await("varbit 15611 set") { if (client.varp(MORGAN_VARP) == 1) Unit else null }
                client.opNpc(MORGAN, DraynorVillage.TALK_TO)
                client.talk("morgan", "once", "dont")
            }
        }
    }

    private companion object {
        const val MORGAN = 3479
        /** A free varp for the test's varbit 15611; the cache's base var isn't needed here. */
        const val MORGAN_VARP = 4000
    }
}
