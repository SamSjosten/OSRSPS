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
    private val moth = NpcType(Butterflies.SUNLIGHT_MOTH.npcs.first(), "Sunlight moth", ops = listOf("Catch", null, null, null, null), internalName = "butterfly_sunlight_moth")

    private fun server(dir: Path, kind: NpcType = warlock) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(30_000)
        objs[Butterflies.BUTTERFLY_JAR] = ObjType(Butterflies.BUTTERFLY_JAR, "Butterfly jar", stackable = false, tradeable = true)
        objs[JARRED] = ObjType(JARRED, "Black warlock", stackable = false, tradeable = true, iops = listOf(null, null, null, "Release", "Drop"))
        objs[Butterflies.MAGIC_INSECT_NET] = ObjType(Butterflies.MAGIC_INSECT_NET, "Magic insect net", stackable = false, tradeable = false)
        objs[Butterflies.INSECT_NET] = ObjType(Butterflies.INSECT_NET, "Insect net", stackable = false, tradeable = true)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            random = GameRandom(7),
            npcTypes = NpcTypes.of(warlock, moth),
            npcSpawns = listOf(NpcSpawn(kind, 3224, 3218, wander = 0, source = "test")),
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
                // The catch holds the player for its whole 3 ticks.
                client.awaitTicks(Butterflies.CATCH_TICKS)
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

    @Test
    fun `a caught sunlight moth gives 5 percent run energy, jarred or not`(@TempDir dir: Path) {
        server(dir, moth).use { server ->
            server.login("su").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hunter 99")
                client.await("level 99") { client.stat(HUNTER)?.takeIf { it.level == 99 } }
                val player = server.world.playerNamed("su")!!
                player.equipment[WearPos.WEAPON] = InvObj(Butterflies.MAGIC_INSECT_NET, 1)
                val target = client.await("the moth in view", 15_000) { client.npcs().firstOrNull { it.id == moth.id } }
                // Energy comes back 15 a tick standing at Agility 1; the catch must add 500 more than that.
                player.runEnergy = 0
                val start = server.world.tick
                client.npcOp(target.index, Butterflies.CATCH)
                client.seen("[hunter.caught_released creature=sunlight moth]")
                val regen = 15 * (server.world.tick - start + 1)
                assertTrue(player.runEnergy - regen >= 500 - 15, "energy ${player.runEnergy} after ${regen / 15} ticks")
            }
        }
    }

    @Test
    fun `a missed butterfly flies straight away from the player`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dy").use { client ->
                client.await("the login rebuild") { client.position }
                // At 45 with a plain net a black warlock is caught 20 times in 256: try until one is missed.
                client.command("setlevel hunter 45")
                client.await("level 45") { client.stat(HUNTER)?.takeIf { it.level == 45 } }
                server.world.playerNamed("dy")!!.equipment[WearPos.WEAPON] = InvObj(Butterflies.INSECT_NET, 1)
                var missed = false
                repeat(5) {
                    if (missed) return@repeat
                    val mark = client.messages().size
                    client.npcOp(client.warlock().index, Butterflies.CATCH)
                    val result = client.await("the catch's result", 15_000) {
                        client.messages().drop(mark).firstOrNull { it.text == "[hunter.catch_miss]" || it.text.startsWith("[hunter.caught") }
                    }
                    missed = result.text == "[hunter.catch_miss]"
                    client.awaitTicks(Butterflies.CATCH_TICKS)
                }
                assertTrue(missed)
                // The player is west of it at 3222 or 3223, so it flies east.
                client.await("the warlock gone east") { client.npcs().firstOrNull { it.id == warlock.id }?.takeIf { it.coord.x > 3224 } }
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
