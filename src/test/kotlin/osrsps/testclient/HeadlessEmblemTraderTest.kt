package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.World
import osrsps.game.content.EmblemTrader
import osrsps.game.content.EmblemTraderLines
import osrsps.game.dialogue.Chatbox
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Wilderness step W5.3 through the headless client: the Emblem Trader in the Revenant Caves skulls a player who asks
 * through Talk-to, for 20 minutes, makes a skull that has run down whole again, and on Rewards has no rewards out here.
 */
class HeadlessEmblemTraderTest {
    private val trader = NpcType(EmblemTrader.ID, "Emblem Trader", ops = listOf("Talk-to", null, "Rewards", null, "Skull"), internalName = "wild_cave_trader")

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3196 shr 6, 10224 shr 6) }, npcTypes = NpcTypes.of(trader), random = GameRandom(3),
            npcSpawns = listOf(NpcSpawn(trader, 3196, 10224, wander = 0, source = "test")), cycleMillis = millis,
        )
    }

    @Test
    fun `Talk-to skulls a player who asks, Skull makes the skull whole again, and Rewards has none`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 3196 10222 0")
                client.await("there") { client.position?.takeIf { it == Coord(3196, 10222, 0) } }
                val abe = server.world.playerNamed("abe")!!
                val npc = server.world.npcs.all.first { it.type.id == EmblemTrader.ID }
                client.npcOp(npc.index, EmblemTrader.TALK_TO)
                repeat(3) {
                    client.awaitDialogue("his talk") { it is DialogueView.Chat }
                    client.continueDialogue()
                }
                client.awaitDialogue("his menu") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.awaitDialogue("the player's ask") { it is DialogueView.Chat && EmblemTraderLines.SKULL_ASK.key in it.text }
                client.continueDialogue()
                client.awaitDialogue("the warning") { it is DialogueView.Menu && EmblemTraderLines.SKULL_WARNING.key in it.title }
                client.chooseOption(1)
                client.await("skulled") { Unit.takeIf { abe.skullTicks > 0 } }
                assertEquals(true, abe.skullTicks in EmblemTrader.SKULL_TICKS - 5..EmblemTrader.SKULL_TICKS)
                val skulled = "[${EmblemTraderLines.SKULLED.key}]"
                client.await("the skull's box") { Unit.takeIf { client.dialogueView is DialogueView.Other && client.text(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_TEXT) == skulled } }
                client.resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
                client.await("the talk over") { Unit.takeIf { client.dialogueView == null } }

                // A few ticks on, the skull has less than its 20 minutes: Skull offers to make it whole again.
                client.awaitTicks(3)
                client.npcOp(npc.index, EmblemTrader.SKULL)
                client.awaitDialogue("the extension") { it is DialogueView.Menu && EmblemTraderLines.EXTEND_TITLE.key in it.title }
                client.chooseOption(1)
                client.await("the full 20 minutes") { Unit.takeIf { abe.skullTicks >= EmblemTrader.SKULL_TICKS - 1 } }
                // A longer skull, as a PvP attack's 30 minutes, can't be made longer.
                abe.skullTicks = osrsps.game.combat.Skulls.TICKS
                assertEquals(false, EmblemTrader.canExtend(abe))

                client.npcOp(npc.index, EmblemTrader.REWARDS)
                client.awaitDialogue("no rewards") { it is DialogueView.Chat && EmblemTraderLines.NO_REWARDS.key in it.text }
            }
        }
    }
}
