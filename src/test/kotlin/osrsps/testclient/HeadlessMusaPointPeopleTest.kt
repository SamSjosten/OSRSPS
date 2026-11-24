package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
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
import osrsps.game.content.Townsfolk
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Pickpocketing
import osrsps.map.CollisionMap

/**
 * Karamja step K1.3 through the headless client, with the real NPC combat and drop files: Musa Point's two men talk as
 * the other men do and can be pickpocketed, and the snake goes for a player beside it, drops bones and comes back.
 * test-runner/examples/musa-point-people.json does the same on the real map.
 */
class HeadlessMusaPointPeopleTest {
    private val manOps = listOf("Talk-to", "Attack", "Pickpocket", null, null)
    private val musaMan = NpcType(MUSA_MAN, "Man", ops = manOps, combatLevel = 2, internalName = "man4_for_musa_point", stats = NpcStats(hitpoints = 7))
    private val karamjaMan = NpcType(KARAMJA_MAN, "Man", ops = manOps, combatLevel = 2, internalName = "karamja_man", stats = NpcStats(hitpoints = 7))
    // The combat and drop files name "man", which would fall back to the first NPC called Man without this one.
    private val man = NpcType(3106, "Man", ops = manOps, combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))
    private val snake = NpcType(SNAKE, "Snake", ops = listOf(null, "Attack", null, null, null), combatLevel = 5, internalName = "snake", stats = NpcStats(hitpoints = 6))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val types = NpcTypes.of(man, musaMan, karamjaMan, snake)
        val objTypes = DropTestItems.objTypes(listOf(ObjType(Pickpocketing.CITIZEN_POUCH, "Coin pouch", stackable = true)))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(3), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(
                NpcSpawn(musaMan, 3223, 3218, wander = 0, source = "test"),
                NpcSpawn(karamjaMan, 3221, 3218, wander = 0, source = "test"),
                // Far enough from the login tile that it leaves the player alone until they go to it.
                NpcSpawn(snake, 3245, 3218, wander = 0, source = "test"),
            ),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.npc(id: Int) = await("$id in view") { npcs().firstOrNull { it.id == id } }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    @Test
    fun `Musa Point's two men talk as the other men do and can be pickpocketed`(@TempDir dir: Path) {
        assertTrue(MUSA_MAN in Townsfolk.MEN && KARAMJA_MAN in Townsfolk.MEN)
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.await("the login rebuild") { client.position }
                for (id in listOf(MUSA_MAN, KARAMJA_MAN)) {
                    client.npcOp(client.npc(id).index, Townsfolk.TALK_TO)
                    client.awaitDialogue("the man's talk") { it is DialogueView.Chat && it.text == "[man.hello]" }
                    client.closeModal()
                    client.await("the talk closed") { if (client.dialogueView == null) Unit else null }

                    val tries = client.seen("[pickpocket.attempt who=man]")
                    client.npcOp(client.npc(id).index, Pickpocketing.PICKPOCKET)
                    client.await("a pickpocket at $id") { if (client.seen("[pickpocket.attempt who=man]") > tries) Unit else null }
                    val ada = server.world.playerNamed("ada")!!
                    client.await("the next pickpocket allowed") { if (server.world.tick >= ada.nextPickpocketTick) Unit else null }
                    client.command("heal")
                }
            }
        }
    }

    @Test
    fun `the snake goes for a player beside it, drops bones and comes back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bea").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("tele 3246 3218") { it.startsWith("Teleported") }
                val snakeNpc = server.world.npcs.all.single { it.type.id == SNAKE }
                client.await("the snake attacking") { snakeNpc.target?.takeIf { it == server.world.playerNamed("bea") } }

                client.commandReply("kill ${snakeNpc.index}") { it.startsWith("Killed Snake") }
                client.await("bones under the snake") { client.groundObjs().firstOrNull { it.id == BONES } }
                val died = snakeNpc.diedTick
                client.await("a new snake") { server.world.npcs.all.firstOrNull { it.type.id == SNAKE && it !== snakeNpc && !it.dead } }
                assertTrue(server.world.tick >= died + RESPAWN_TICKS, "back no sooner than 30 ticks after it died")
            }
        }
    }

    private companion object {
        const val MUSA_MAN = 3110
        const val KARAMJA_MAN = 3652
        const val SNAKE = 2845
        const val BONES = 526
        const val RESPAWN_TICKS = 30
    }
}
