package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.hunter.Butterflies
import osrsps.map.CollisionMap

/**
 * Hunter step 6a through the headless client: a black warlock (sure at 99 with the magic insect net) caught into a
 * butterfly jar, then released for its Strength boost and the jar back; caught again with no jar, let go at once with
 * the boost; then the checks: below the level with a net, and no net below the barehanded level. The player logs in at
 * 3222, 3218; the warlock stands still two tiles east.
 */
class HeadlessButterflyTest {
    private val warlock = NpcType(Butterflies.BLACK_WARLOCK.npcs.first(), "Black warlock", ops = listOf("Catch", null, null, null, null), internalName = "butterfly_warlock")

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(30_000)
        objs[Butterflies.BUTTERFLY_JAR] = ObjType(Butterflies.BUTTERFLY_JAR, "Butterfly jar", stackable = false, tradeable = true)
        objs[JARRED] = ObjType(JARRED, "Black warlock", stackable = false, tradeable = true, iops = listOf(null, null, null, "Release", "Drop"))
        objs[Butterflies.MAGIC_INSECT_NET] = ObjType(Butterflies.MAGIC_INSECT_NET, "Magic insect net", stackable = false, tradeable = false)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            random = GameRandom(7),
            npcTypes = NpcTypes.of(warlock),
            npcSpawns = listOf(NpcSpawn(warlock, 3224, 3218, wander = 0, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String, timeoutMillis: Long = 10_000) =
        await(text, timeoutMillis) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.warlock() = await("the warlock in view", 15_000) { npcs().firstOrNull { it.id == warlock.id } }

    @Test
    fun `a black warlock caught into a jar is released for its boost, and caught with no jar it goes at once`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bo").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hunter 99")
                client.command("setlevel strength 60")
                client.command("item ${Butterflies.BUTTERFLY_JAR} 1")
                client.slotOf(Butterflies.BUTTERFLY_JAR)
                val before = client.await("Hunter XP") { client.stat(HUNTER)?.takeIf { it.level == 99 } }.xp
                server.world.playerNamed("bo")!!.equipment[WearPos.WEAPON] = InvObj(Butterflies.MAGIC_INSECT_NET, 1)
                client.npcOp(client.warlock().index, Butterflies.CATCH)
                client.seen("[hunter.caught creature=black warlock]")
                client.await("the jarred warlock") { if (client.count(JARRED) == 1) Unit else null }
                assertEquals(0, client.count(Butterflies.BUTTERFLY_JAR))
                client.await("54 XP") { if (client.stat(HUNTER)!!.xp - before == 54) Unit else null }
                // Release: 60 * 15% + 4 = 13 over 60, and the jar back.
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, RELEASE_BUTTON_OP, client.slotOf(JARRED), JARRED)
                client.seen("[hunter.released]")
                client.await("Strength 73") { client.stat(STRENGTH)?.takeIf { it.level == 73 } }
                client.await("the jar back") { if (client.count(Butterflies.BUTTERFLY_JAR) == 1) Unit else null }
                // Back after its respawn; with the jar dropped, the next catch lets it go at once (the boost is already at its top).
                client.command("empty")
                client.await("an empty backpack") { if (client.count(Butterflies.BUTTERFLY_JAR) == 0) Unit else null }
                client.npcOp(client.warlock().index, Butterflies.CATCH)
                client.seen("[hunter.caught_released creature=black warlock]", timeoutMillis = 15_000)
                client.await("108 XP") { if (client.stat(HUNTER)!!.xp - before == 108) Unit else null }
                assertEquals(0, client.count(JARRED))
                assertEquals(73, server.world.playerNamed("bo")!!.skills.currentLevel(Skill.STRENGTH))
            }
        }
    }

    @Test
    fun `a net is needed below the barehanded level, and the level with one`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cy").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hunter 50")
                client.await("level 50") { client.stat(HUNTER)?.takeIf { it.level == 50 } }
                client.npcOp(client.warlock().index, Butterflies.CATCH)
                client.seen("[hunter.need_net]")
                client.command("setlevel hunter 40")
                client.await("level 40") { client.stat(HUNTER)?.takeIf { it.level == 40 } }
                server.world.playerNamed("cy")!!.equipment[WearPos.WEAPON] = InvObj(Butterflies.MAGIC_INSECT_NET, 1)
                client.npcOp(client.warlock().index, Butterflies.CATCH)
                client.seen("[hunter.catch_level level=45]")
                assertTrue(server.world.npcs.all.none { it.hidden })
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val HUNTER = 21
        const val STRENGTH = 2
        const val JARRED = 10014

        /** iop4 (Release) is button op 6 (see InventoryTab.click). */
        const val RELEASE_BUTTON_OP = 6
    }
}
