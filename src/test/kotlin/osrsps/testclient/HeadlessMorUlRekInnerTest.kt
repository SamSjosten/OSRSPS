package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.MorUlRekInner
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Karamja step K2.3 through the headless client: a hot vent door holding back a player who hasn't shown a fire cape,
 * a barrier guard's talk taking the fire cape as proof and the player then Xo, the door letting them through both ways,
 * and an elder's talk. test-runner/examples/mor-ul-rek-inner.json does the same on the real map.
 */
class HeadlessMorUlRekInnerTest {
    private val guard = NpcType(MorUlRekInner.GUARD_BORDER, "TzHaar-Ket", ops = listOf("Talk-to", null, null, null, null), internalName = "tzhaar_guard_border")
    private val elder = NpcType(MorUlRekInner.ELDER_BAL, "TzHaar-Mej-Bal", ops = listOf("Talk-to", null, null, null, null), internalName = "tzhaar_elder2")
    private val objs = arrayOfNulls<ObjType>(FIRE_CAPE + 1).apply { this[FIRE_CAPE] = ObjType(FIRE_CAPE, "Fire cape") }
    private val door = LocType(MorUlRekInner.DOOR, "Hot vent door", ops = listOf("Pass", null, null, null, null), collision = LocCollisionType(width = 1, length = 1))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(2436 shr 6, 5120 shr 6)
                markLoaded(2436 shr 6, 5119 shr 6)
            },
            objTypes = ObjTypes(objs), npcTypes = NpcTypes.of(guard, elder),
            npcSpawns = listOf(NpcSpawn(guard, 2436, 5122, wander = 0, source = "test"), NpcSpawn(elder, 2438, 5117, wander = 0, source = "test")),
            locTypes = LocTypes(mapOf(MorUlRekInner.DOOR to door)),
            staticLocs = StaticLocs().apply { add(ZoneLoc(MorUlRekInner.DOOR, LocShape.CENTREPIECE_STRAIGHT, 0, 2436, 5120, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.arrive(x: Int, z: Int) {
        await("the player at $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.chat(key: String) =
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }.also { continueDialogue() }

    private fun HeadlessClient.menu(option: Int) {
        awaitDialogue("a menu") { it is DialogueView.Menu }
        chooseOption(option)
    }

    @Test
    fun `a fire cape shown to the guard opens the hot vent doors and makes the player Xo`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.command("tele 2436 5121 0")
                client.arrive(2436, 5121)
                client.locOp(MorUlRekInner.DOOR, 2436, 5120, MorUlRekInner.PASS)
                client.await("the refusal") { client.messages().firstOrNull { it.text == "[tzhaar_guard.no_pass]" } }

                client.command("item $FIRE_CAPE")
                client.await("the cape") { if (client.inventory(BACKPACK).any { it?.id == FIRE_CAPE }) Unit else null }
                val npc = client.await("the guard in view") { client.npcs().firstOrNull { it.id == MorUlRekInner.GUARD_BORDER } }
                client.npcOp(npc.index, MorUlRekInner.TALK_TO)
                client.awaitDialogue("tzhaar_guard.greeting") { it is DialogueView.Chat && it.text == "[tzhaar_guard.greeting name=JalYt-Mej-ada]" }
                client.continueDialogue()
                client.menu(1)
                client.chat("tzhaar_guard.why_cant")
                client.chat("tzhaar_guard.closed")
                client.menu(1)
                client.chat("tzhaar_guard.whys_that")
                client.chat("tzhaar_guard.not_trusted")
                client.menu(2)
                client.chat("tzhaar_guard.prove_said")
                client.awaitDialogue("tzhaar_guard.acceptable") { it is DialogueView.Chat && it.text == "[tzhaar_guard.acceptable name=JalYt-Mej-ada]" }
                client.continueDialogue()
                // With the cape: gold, the cape, sorry.
                val menu = client.awaitDialogue("the proof menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[tzhaar_guard.gold]", "[tzhaar_guard.fire_cape]", "[tzhaar_guard.sorry]"), menu.options)
                client.chooseOption(2)
                client.chat("tzhaar_guard.fire_cape")
                client.awaitDialogue("the cape box") { it is DialogueView.Other && it.interfaceId == Chatbox.OBJECTBOX }
                client.resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
                client.awaitDialogue("tzhaar_guard.impressive") { it is DialogueView.Chat && it.text == "[tzhaar_guard.impressive name=JalYt-Mej-ada]" }
                client.continueDialogue()
                client.chat("tzhaar_guard.surely")
                client.chat("tzhaar_guard.granted")
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }

                // Now Xo, and through the door and back.
                client.npcOp(npc.index, MorUlRekInner.TALK_TO)
                client.awaitDialogue("the Xo greeting") { it is DialogueView.Chat && it.text == "[tzhaar_guard.greeting name=JalYt-Mej-Xo-ada]" }
                client.continueDialogue()
                client.menu(4)
                client.chat("tzhaar_guard.fine")
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
                client.command("tele 2436 5121 0")
                client.locOp(MorUlRekInner.DOOR, 2436, 5120, MorUlRekInner.PASS)
                client.arrive(2436, 5119)
                client.locOp(MorUlRekInner.DOOR, 2436, 5120, MorUlRekInner.PASS)
                client.arrive(2436, 5121)
            }
        }
    }

    @Test
    fun `TzHaar-Mej-Bal tells of the terminal chambers`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ben").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.command("tele 2438 5115 0")
                val npc = client.await("the elder in view") { client.npcs().firstOrNull { it.id == MorUlRekInner.ELDER_BAL } }
                client.npcOp(npc.index, MorUlRekInner.TALK_TO)
                client.awaitDialogue("tzhaar_elder.hello") { it is DialogueView.Chat && it.text == "[tzhaar_elder.hello name=JalYt-Mej-ben]" }
                client.continueDialogue()
                for (key in listOf("whats_happening", "chamber", "why", "lose_heat", "solidify", "haar_tok", "dead", "conscious", "intriguing")) {
                    client.chat("tzhaar_elder.$key")
                }
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
            }
        }
    }

    private companion object {
        const val FIRE_CAPE = 6570
        const val BACKPACK = 93
    }
}
