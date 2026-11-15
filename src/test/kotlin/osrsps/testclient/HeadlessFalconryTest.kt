package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.hunter.Falconry
import osrsps.map.CollisionMap

/**
 * Hunter step 7 through the headless client, in the Piscatoris falconry area: Quick-falcon on Matthias rents the falcon
 * for 500 coins (the sword worn goes to the backpack), Catch sends it at a spotted kebbit (sure at 99), Retrieve gives
 * the XP, bones and fur and the falcon back, and the glove won't come off; then Talk-to's "leave it for now" hands it
 * back, and a teleport out with the glove sends the falcon home. The kebbit stands still four tiles east of Matthias.
 */
class HeadlessFalconryTest {
    private val matthias = NpcType(Falconry.MATTHIAS, "Matthias", ops = listOf("Talk-to", null, "Quick-falcon", null, null), internalName = "hunting_npc_falconer")
    private val spotted = NpcType(Falconry.SPOTTED.npc, "Spotted kebbit", ops = listOf("Catch", null, null, null, null), internalName = "huntingbeast_speedy")
    private val falcon = NpcType(Falconry.SPOTTED.falcon, "Gyr Falcon", ops = listOf("Retrieve", null, null, null, null), internalName = "hunting_falcon_onspeedy")

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(30_000)
        objs[Falconry.COINS] = ObjType(Falconry.COINS, "Coins", stackable = true, tradeable = true)
        for (id in listOf(Falconry.GLOVE_EMPTY, Falconry.GLOVE_FALCON)) objs[id] = ObjType(id, "Falconer's glove", stackable = false, tradeable = false, wearpos1 = WearPos.WEAPON)
        for (id in Falconry.SPOTTED.drops) objs[id] = ObjType(id, "Drop $id", stackable = false, tradeable = true)
        objs[SWORD] = ObjType(SWORD, "Bronze sword", stackable = false, tradeable = true, wearpos1 = WearPos.WEAPON)
        objs[DARTS] = ObjType(DARTS, "Bronze dart", stackable = true, tradeable = true, wearpos1 = WearPos.WEAPON)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(2376 shr 6, 3590 shr 6) }, objTypes = ObjTypes(objs),
            random = GameRandom(7),
            npcTypes = NpcTypes.of(matthias, spotted, falcon),
            npcSpawns = listOf(
                NpcSpawn(matthias, 2376, 3592, source = "test"),
                NpcSpawn(spotted, 2380, 3590, source = "test"),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.seen(text: String, timeoutMillis: Long = 10_000) =
        await(text, timeoutMillis) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.npc(id: Int) = await("NPC $id in view", 15_000) { npcs().firstOrNull { it.id == id } }

    private fun TestServer.weapon(name: String) = world.playerNamed(name)!!.equipment[WearPos.WEAPON]?.id

    private fun TestServer.awaitWeapon(client: HeadlessClient, name: String, id: Int?) =
        client.await("weapon $id") { if (weapon(name) == id) Unit else null }

    @Test
    fun `Quick-falcon rents the falcon, it catches a spotted kebbit, and Retrieve gives the XP, bones and fur`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fa").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(2377, 3590)
                client.command("setlevel hunter 99")
                client.command("item ${Falconry.COINS} 600")
                client.await("the coins") { if (client.count(Falconry.COINS) == 600) Unit else null }
                server.world.playerNamed("fa")!!.equipment[WearPos.WEAPON] = InvObj(SWORD, 1)
                val before = client.await("Hunter XP") { client.stat(HUNTER)?.takeIf { it.level == 99 } }.xp
                client.npcOp(client.npc(Falconry.MATTHIAS).index, Falconry.QUICK_FALCON)
                client.awaitDialogue("the glove") { it is DialogueView.Message && it.text == "[matthias.gives_glove]" }
                server.awaitWeapon(client, "fa", Falconry.GLOVE_FALCON)
                assertEquals(100, client.count(Falconry.COINS))
                assertEquals(1, client.count(SWORD))
                // The glove stays on.
                server.world.equipment.remove(server.world.playerNamed("fa")!!, WearPos.WEAPON)
                client.seen("[hunter.glove_stays]")
                assertEquals(Falconry.GLOVE_FALCON, server.weapon("fa"))
                // Sent from where the player stands (by Matthias, four tiles off): they don't walk over.
                val from = client.position
                client.npcOp(client.npc(Falconry.SPOTTED.npc).index, Falconry.CATCH)
                client.seen("[hunter.falcon_caught]")
                assertEquals(from, client.position)
                // The kebbit stays gone while the falcon holds it, however long that is.
                client.awaitTicks(Falconry.RESPAWN_TICKS + 3)
                assertTrue(client.npcs().none { it.id == Falconry.SPOTTED.npc })
                client.npcOp(client.npc(Falconry.SPOTTED.falcon).index, Falconry.RETRIEVE)
                client.seen("[hunter.falcon_retrieved]")
                // Back four seconds after the retrieve.
                client.npc(Falconry.SPOTTED.npc)
                client.await("104 XP") { if (client.stat(HUNTER)!!.xp - before == 104) Unit else null }
                for (id in Falconry.SPOTTED.drops) assertEquals(1, client.count(id), "item $id")
                assertEquals(Falconry.GLOVE_FALCON, server.weapon("fa"))
            }
        }
    }

    @Test
    fun `Talk-to takes the glove back, and a teleport out with it sends the falcon home`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fb").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(2377, 3590)
                client.command("setlevel hunter 50")
                client.command("item ${Falconry.COINS} 1000")
                client.await("the coins") { if (client.count(Falconry.COINS) == 1000) Unit else null }
                val matthiasIndex = client.npc(Falconry.MATTHIAS).index
                client.npcOp(matthiasIndex, Falconry.QUICK_FALCON)
                server.awaitWeapon(client, "fb", Falconry.GLOVE_FALCON)
                client.npcOp(matthiasIndex, Falconry.TALK_TO)
                for (key in listOf("hello_again", "getting_along", "harder", "talking_to_falcon")) {
                    client.awaitDialogue(key) { it is DialogueView.Chat && it.text.startsWith("[matthias.$key") }
                    client.continueDialogue()
                }
                client.awaitDialogue("the glove menu") { it is DialogueView.Menu && it.options.size == 2 }
                client.chooseOption(2)
                client.awaitDialogue("leave it") { it is DialogueView.Chat && it.text == "[matthias.leave_it]" }
                client.continueDialogue()
                client.awaitDialogue("the glove back") { it is DialogueView.Message && it.text == "[matthias.give_back]" }
                assertNull(server.weapon("fb"))
                // Again, then out of the area with it.
                client.npcOp(matthiasIndex, Falconry.QUICK_FALCON)
                server.awaitWeapon(client, "fb", Falconry.GLOVE_FALCON)
                assertEquals(0, client.count(Falconry.COINS))
                client.tele(3222, 3218)
                client.seen("[hunter.falcon_flies_back]")
                server.awaitWeapon(client, "fb", null)
            }
        }
    }

    @Test
    fun `wielded darts that wouldn't all fit on the backpack's stack keep the falcon from being rented`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fc").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(2377, 3590)
                client.command("setlevel hunter 50")
                client.command("item ${Falconry.COINS} 600")
                client.await("the coins") { if (client.count(Falconry.COINS) == 600) Unit else null }
                val player = server.world.playerNamed("fc")!!
                client.command("item $DARTS ${Int.MAX_VALUE - 1}")
                client.await("the darts") { if (client.count(DARTS) == Int.MAX_VALUE - 1) Unit else null }
                player.equipment[WearPos.WEAPON] = InvObj(DARTS, 10)
                client.npcOp(client.npc(Falconry.MATTHIAS).index, Falconry.QUICK_FALCON)
                client.awaitDialogue("hands free") { it is DialogueView.Chat && it.text == "[matthias.hands_free]" }
                assertEquals(InvObj(DARTS, 10), player.equipment[WearPos.WEAPON])
                assertEquals(Int.MAX_VALUE - 1, client.count(DARTS))
                assertEquals(600, client.count(Falconry.COINS))
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val HUNTER = 21
        const val SWORD = 1277
        const val DARTS = 806
    }
}
