package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.SwampCaveGoblins
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Fishing
import osrsps.game.skill.LightSources
import osrsps.map.CollisionMap

/**
 * Misthalin piece 7 step 3a through the headless client, with the real combat, drop and fishing data: a cave slime
 * refuses a player below Slayer 17 and dies to one at 17, leaving its swamp tar; a cave goblin begs not to be hurt,
 * then hands a player with no light a lit torch; the caves' spot nets frog spawn at 33 Fishing. The rl-client script
 * test-runner/examples/swamp-caves-monsters.json does the same in the real caves.
 */
class HeadlessSwampCavesMonstersTest {
    private val slime = NpcType(
        SLIME, "Cave slime", ops = listOf(null, "Attack", null, null, null), combatLevel = 23, internalName = "swamp_cave_slime",
        stats = NpcStats(hitpoints = 25, attack = 13, strength = 13, defence = 35),
    )
    private val goblin = NpcType(
        SwampCaveGoblins.GOBLINS[0], "Cave goblin", ops = listOf("Talk-to", "Attack", null, null, null), combatLevel = 3, internalName = "cave_goblin",
        stats = NpcStats(hitpoints = 10),
    )
    private val spot = NpcType(Fishing.SWAMP_CAVES_WEST_SPOT, "Fishing spot", ops = listOf("Small Net", null, "Bait", null, null), internalName = "0_49_149_cavefish")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(slime, goblin, spot)
        val objTypes = DropTestItems.objTypes(
            listOf(
                ObjType(LightSources.LIT_TORCH, "Lit torch"), ObjType(LightSources.TINDERBOX, "Tinderbox", tradeable = true),
                ObjType(Fishing.NET, "Small fishing net", tradeable = true), ObjType(FROG_SPAWN, "Frog spawn"),
                ObjType(SWAMP_WEED, "Swamp weed", tradeable = true),
            ),
        )
        World(
            crcs, CollisionMap().apply { markLoaded(3160 shr 6, 9560 shr 6) }, random = GameRandom(11), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(
                NpcSpawn(slime, 3162, 9560, source = "test"), NpcSpawn(goblin, 3158, 9563, source = "test"), NpcSpawn(goblin, 3156, 9563, source = "test"),
                NpcSpawn(spot, 3154, 9544, source = "test"),
            ),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.seen2(text: String) = await("$text twice") { messages().filter { it.text == text }.takeIf { it.size >= 2 } }

    private fun HeadlessClient.held(id: Int) = inventory(BACKPACK).count { it?.id == id }

    @Test
    fun `a cave slime refuses a player below Slayer 17, even mid-fight, and one at 17 kills it for its swamp tar`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3160, 9560)
                val npc = client.await("the slime in view") { client.npcs().firstOrNull { it.id == SLIME } }
                client.commandReply("god") { it.startsWith("You can") }
                client.npcOp(npc.index, 2)
                client.seen("[combat.slayer_level]")
                client.awaitTicks(4)
                assertTrue(client.npcHits().isEmpty(), "${client.npcHits()}")

                client.command("setlevel slayer 17")
                client.await("Slayer 17") { client.stat(SLAYER)?.takeIf { it.level == 17 } }
                // Mid-fight, dropping below 17 stops the fight at the next swing.
                client.npcOp(npc.index, 2)
                client.await("a first swing") { client.npcHits().firstOrNull { it.index == npc.index } }
                client.command("setlevel slayer 16")
                client.seen2("[combat.slayer_level]")
                val hits = client.npcHits().count { it.index == npc.index }
                client.awaitTicks(8)
                assertEquals(hits, client.npcHits().count { it.index == npc.index }, "no swings after the refusal")

                client.command("setlevel slayer 17")
                client.await("Slayer 17 again") { client.stat(SLAYER)?.takeIf { it.level == 17 } }
                client.command("setlevel attack 99")
                client.command("setlevel strength 99")
                client.await("Strength 99") { client.stat(STRENGTH)?.takeIf { it.level == 99 } }
                client.npcOp(npc.index, 2)
                client.await("the slime's death", timeoutMillis = 90_000) { if (client.npcs().none { it.id == SLIME }) Unit else null }
                client.await("its swamp tar") { client.groundObjs().map { it.id }.takeIf { SWAMP_TAR in it } }
                assertEquals(2, client.messages().count { it.text == "[combat.slayer_level]" })
            }
        }
    }

    @Test
    fun `a cave goblin begs not to be hurt, gives a player with no light a lit torch, and begs again once one is killed`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3158, 9561)
                val npc = client.await("the goblin in view") { client.npcs().firstOrNull { it.id == goblin.id } }
                client.npcOp(npc.index, SwampCaveGoblins.TALK_TO)
                client.awaitDialogue("the plea") { it is DialogueView.Chat && it.text == "[cave_goblin.dont_hurt]" }
                client.continueDialogue()
                val menu = client.awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[cave_goblin.not_going_to]", "[cave_goblin.slay_you_all]"), menu.options)
                client.chooseOption(1)
                client.awaitDialogue("the promise") { it is DialogueView.Chat && it.text == "[cave_goblin.not_going_to]" }
                client.continueDialogue()
                client.awaitTicks(2)

                // With no light and no tinderbox, the torch or the tinderbox are among its talks: talk till one comes.
                var talks = 0
                while (client.held(LightSources.LIT_TORCH) == 0 && talks < 40) {
                    client.npcOp(npc.index, SwampCaveGoblins.TALK_TO)
                    val first = client.awaitDialogue("a talk") { it is DialogueView.Chat } as DialogueView.Chat
                    assertTrue(first.text != "[cave_goblin.dont_hurt]", "it remembers the promise")
                    // Through to the end of the talk.
                    while (client.dialogueView is DialogueView.Chat) {
                        client.continueDialogue()
                        client.awaitTicks(2)
                    }
                    talks++
                }
                assertEquals(1, client.held(LightSources.LIT_TORCH), "after $talks talks")

                // Killing one breaks the promise: the other begs again.
                client.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                val other = client.await("the other goblin") { client.npcs().firstOrNull { it.id == goblin.id && it.index != npc.index } }
                client.npcOp(other.index, SwampCaveGoblins.TALK_TO)
                client.awaitDialogue("the plea again") { it is DialogueView.Chat && it.text == "[cave_goblin.dont_hurt]" }
            }
        }
    }

    @Test
    fun `the caves' spot nets frog spawn or swamp weed at 33 Fishing, and not below`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3155, 9545)
                val npc = client.await("the spot in view") { client.npcs().firstOrNull { it.id == spot.id } }
                client.command("item ${Fishing.NET}")
                client.await("the net") { if (client.held(Fishing.NET) == 1) Unit else null }
                client.npcOp(npc.index, 1)
                client.seen("[fishing.level_net level=33]")

                client.command("setlevel fishing 99")
                client.await("Fishing 99") { client.stat(FISHING)?.takeIf { it.level == 99 } }
                client.npcOp(npc.index, 1)
                client.await("a catch", timeoutMillis = 60_000) { if (client.held(FROG_SPAWN) + client.held(SWAMP_WEED) > 0) Unit else null }
            }
        }
    }

    private companion object {
        const val SLIME = 480
        const val SWAMP_TAR = 1939
        const val FROG_SPAWN = 5004
        const val SWAMP_WEED = 10978
        const val BACKPACK = 93
        const val SLAYER = 18
        const val STRENGTH = 2
        const val FISHING = 10
    }
}
