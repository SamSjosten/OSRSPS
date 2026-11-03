package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.EssenceMine
import osrsps.game.content.SedridorLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Mining
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Draynor piece step 15a through the headless client and the registered handlers: Archmage Sedridor's talk and his
 * Teleport sending the player to the rune essence mine, a portal back beside him, and the Rune Essence rock giving rune
 * essence below Mining 30 and pure essence from 30 until the backpack is full. Locs and Sedridor are placed as the 241
 * cache and mejrs's NPC list have them, on open ground. The rl-client script test-runner/examples/essence-mine.json does
 * the same on the real map.
 */
class HeadlessEssenceMineTest {
    private val sedridor = NpcType(EssenceMine.SEDRIDOR, "Archmage Sedridor", ops = listOf("Talk-to", null, "Teleport", null, null))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Mining.PURE_ESSENCE + 1)
        objs[Mining.RUNE_ESSENCE] = ObjType(Mining.RUNE_ESSENCE, "Rune essence")
        objs[Mining.PURE_ESSENCE] = ObjType(Mining.PURE_ESSENCE, "Pure essence")
        objs[BRONZE_PICKAXE] = ObjType(BRONZE_PICKAXE, "Bronze pickaxe")
        objs[POT] = ObjType(POT, "Pot")
        val map = CollisionMap().apply {
            markLoaded(3103 shr 6, 9571 shr 6)
            markLoaded(2880 shr 6, 4800 shr 6)
        }
        World(
            crcs, map, objTypes = ObjTypes(objs), random = GameRandom(5),
            locTypes = LocTypes(
                listOf(
                    LocType(Mining.ESSENCE_ROCK, "Rune Essence", listOf("Mine", null, null, null, null), LocCollisionType(width = 5, length = 5)),
                    LocType(EssenceMine.PORTAL, "Portal", listOf("Use", null, null, null, null), LocCollisionType()),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { add(ZoneLoc(Mining.ESSENCE_ROCK, LocShape.CENTREPIECE_STRAIGHT, 3, 2891, 4847, 0)) },
            npcTypes = NpcTypes.of(sedridor), npcSpawns = listOf(NpcSpawn(sedridor, 3103, 9571, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.inMine() = await("the mine") { position?.takeIf { it.x in 2880..2943 && it.z in 4800..4863 && it.level == 0 } }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).count { it?.id == id }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `Sedridor's first option sends the player to the mine, and a portal brings them back beside him`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3104, 9571, 0)
                client.npcOp(server.world.npcs.all.first { it.type.id == EssenceMine.SEDRIDOR }.index, EssenceMine.TALK_TO)
                client.awaitDialogue("hello") { it is DialogueView.Chat && it.text == DialogueText.NONE[SedridorLines.HELLO] }
                client.continueDialogue()
                client.awaitDialogue("his greeting") { it is DialogueView.Chat && it.text == DialogueText.NONE.get(SedridorLines.GREETING, "name" to "alice") }
                client.continueDialogue()
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.awaitDialogue("the ask") { it is DialogueView.Chat && it.text == DialogueText.NONE[SedridorLines.TELEPORT] }
                client.continueDialogue()
                val landed = client.inMine()
                assertTrue(EssenceMine.LANDINGS.any { maxOf(kotlin.math.abs(it.x - landed.x), kotlin.math.abs(it.z - landed.z)) <= 1 }, "landed at $landed")

                client.tele(2886, 4850, 0)
                client.locOp(EssenceMine.PORTAL, 2885, 4850, 1)
                val back = client.await("back beside Sedridor") { client.position?.takeIf { it.z in 9560..9580 } }
                assertTrue(maxOf(kotlin.math.abs(back.x - EssenceMine.TO_SEDRIDOR.x), kotlin.math.abs(back.z - EssenceMine.TO_SEDRIDOR.z)) <= 2, "back at $back")
            }
        }
    }

    @Test
    fun `his Teleport option sends the player at once, and the old tower's story goes back to his options, and Nothing ends the talk where they stand`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3104, 9571, 0)
                val index = server.world.npcs.all.first { it.type.id == EssenceMine.SEDRIDOR }.index
                client.npcOp(index, EssenceMine.TALK_TO)
                client.awaitDialogue("hello") { it is DialogueView.Chat }
                client.continueDialogue()
                client.awaitDialogue("his greeting") { it is DialogueView.Chat && it.text.startsWith("[sedridor.greeting") }
                client.continueDialogue()
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                // The old tower's story: eleven boxes, then his options again.
                client.chooseOption(3)
                client.awaitDialogue("the ask") { it is DialogueView.Chat && it.text == DialogueText.NONE[SedridorLines.OLD_TOWER] }
                client.continueDialogue()
                for (line in SedridorLines.OLD_TOWER_ANSWER) {
                    client.awaitDialogue(line.key) { it is DialogueView.Chat && it.text == DialogueText.NONE[line] }
                    client.continueDialogue()
                }
                client.awaitDialogue("the menu again") { it is DialogueView.Menu }
                client.chooseOption(4)
                client.awaitDialogue("nothing") { it is DialogueView.Chat && it.text == DialogueText.NONE[SedridorLines.NOTHING] }
                client.continueDialogue()
                client.awaitDialogue("farewell") { it is DialogueView.Chat && it.text == DialogueText.NONE[SedridorLines.FAREWELL] }
                client.continueDialogue()
                assertEquals(Coord(3104, 9571, 0), client.position)

                client.npcOp(index, EssenceMine.TELEPORT_OP)
                client.inMine()
            }
        }
    }

    @Test
    fun `the essence rock gives rune essence below Mining 30 and pure essence from 30, until the backpack is full`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.fillUp(pots = 24)
                // It goes on by itself, with no second click, until the backpack is full.
                client.seen("[mining.essence_full]")
                assertEquals(3, client.count(Mining.RUNE_ESSENCE))
                assertEquals(0, client.count(Mining.PURE_ESSENCE))
                assertEquals(28, client.inventory(BACKPACK).count { it != null })
            }
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel mining 30")
                client.await("level 30") { client.stat(MINING)?.takeIf { it.level == 30 } }
                client.fillUp(pots = 25)
                client.seen("[mining.essence_full]")
                assertEquals(2, client.count(Mining.PURE_ESSENCE))
                assertEquals(0, client.count(Mining.RUNE_ESSENCE))
            }
        }
    }

    /** A pickaxe and [pots] pots, then Mine on the rock until the backpack is full. */
    private fun HeadlessClient.fillUp(pots: Int) {
        await("the login rebuild") { position }
        command("item $BRONZE_PICKAXE")
        command("item $POT $pots")
        await("the pickaxe and pots") { if (count(POT) == pots && count(BRONZE_PICKAXE) == 1) Unit else null }
        tele(2890, 4849, 0)
        locOp(Mining.ESSENCE_ROCK, 2891, 4847, 1)
        seen("[mining.swing]")
        await("an essence", timeoutMillis = 30_000) { messages().firstOrNull { it.text == "[mining.essence]" } }
        await("a full backpack", timeoutMillis = 60_000) { if (inventory(BACKPACK).count { it != null } == 28) Unit else null }
    }

    private companion object {
        const val BRONZE_PICKAXE = 1265
        const val POT = 1931
        const val BACKPACK = 93
        const val MINING = 14
    }
}
