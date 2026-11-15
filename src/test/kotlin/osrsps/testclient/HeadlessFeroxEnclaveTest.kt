package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameFrame
import osrsps.game.GameRandom
import osrsps.game.Movement
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.PlayerOps
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.content.FeroxEnclave
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Wilderness step W2.3 through the headless client: no attacks inside Ferox Enclave and one allowed just outside, the
 * overlay's "Level: --" varbit inside, the south barrier both ways and closed to a Tele Blocked player, a Pool of
 * Refreshment, and the bank chest and banker. The locs are placed as the 242 map has them, with their cache ids.
 */
class HeadlessFeroxEnclaveTest {
    private val vars = VarTypes(
        6_000,
        mapOf(
            // inside_wilderness on wilderness_statistics and wildy_hub_buffer on wildwars_temptransmit2 (242 cache).
            World.VARBIT_INSIDE_WILDERNESS to VarbitType(WILDERNESS_STATISTICS, 22, 22),
            World.VARBIT_WILDY_HUB_BUFFER to VarbitType(TEMPTRANSMIT2, 21, 21),
        ),
    )

    private val banker = NpcType(
        FeroxEnclave.BANKER, "Banker", ops = listOf("Talk-to", null, "Bank", "Collect", null), internalName = "wildy_hub_banker",
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val wall = LocCollisionType(width = 1, length = 1)
        val barrier = LocType(FeroxEnclave.BARRIER, "Barrier", listOf("Pass-Through", null, null, null, null), wall)
        val mirror = LocType(FeroxEnclave.BARRIER_MIRROR, "Barrier", listOf("Pass-Through", null, null, null, null), wall)
        val pool = LocType(FeroxEnclave.POOL, "Pool of Refreshment", listOf("Drink", null, null, null, null), LocCollisionType(width = 2, length = 2))
        val chest = LocType(BANK_CHEST, "Bank chest", listOf("Use", "Collect", null, null, null), wall)
        World(
            crcs, CollisionMap().apply { markLoaded(3123 shr 6, 3620 shr 6); markLoaded(3154 shr 6, 3620 shr 6) },
            random = GameRandom(5), varTypes = vars,
            locTypes = LocTypes(listOf(barrier, mirror, pool, chest).associateBy { it.id }),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(FeroxEnclave.BARRIER, LocShape.WALL_STRAIGHT, 3, 3134, 3617, 0))
                add(ZoneLoc(FeroxEnclave.BARRIER_MIRROR, LocShape.WALL_STRAIGHT, 3, 3135, 3617, 0))
                add(ZoneLoc(FeroxEnclave.POOL, LocShape.CENTREPIECE_STRAIGHT, 1, 3128, 3633, 0))
                add(ZoneLoc(BANK_CHEST, LocShape.CENTREPIECE_STRAIGHT, 0, 3130, 3632, 0))
            },
            npcTypes = NpcTypes.of(banker), npcSpawns = listOf(NpcSpawn(banker, 3134, 3629, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int) = await("$x, $z") { position?.takeIf { it == Coord(x, z, 0) } }

    @Test
    fun `no attack inside the Enclave, one just outside it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("kira").use { a ->
                server.login("liam").use { b ->
                    for (client in listOf(a, b)) {
                        client.await("the login rebuild") { client.position }
                        client.commandReply("keys on") { it == "Dialogue shows keys" }
                    }
                    val kira = server.world.playerNamed("kira")!!
                    a.tele(3140, 3630)
                    b.tele(3141, 3630)
                    a.await("Attack given") { Unit.takeIf { a.playerOps()[PlayerOps.ATTACK] == PlayerOps.ATTACK_TEXT } }
                    a.await("the overlay reads Level: --") { a.varp(TEMPTRANSMIT2).takeIf { it == 1 shl 21 } }
                    a.await("b in view") { a.otherPlayers().takeIf { it.isNotEmpty() } }
                    a.playerOp(b.index, PlayerOps.ATTACK)
                    a.awaitMessage("the refusal") { it == "[pvp.not_in_wilderness]" }
                    assertEquals(0L, kira.pjUntil)

                    // Just past the south barrier, level 13: a normal fight.
                    a.tele(3134, 3616)
                    b.tele(3136, 3616)
                    assertEquals(13, Wilderness.pvpLevel(3134, 3616))
                    a.await("the buffer varbit off outside") { a.varp(TEMPTRANSMIT2).takeIf { it == 0 } }
                    assertTrue(GameFrame.PVP_ICONS in a.openInterfaces())
                    a.playerOp(b.index, PlayerOps.ATTACK)
                    a.await("the fight's PJ timer") { Unit.takeIf { kira.pjUntil > server.world.tick } }
                }
            }
        }
    }

    @Test
    fun `the barrier passes both ways, but not a Tele Blocked player inward`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("mona").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                val mona = server.world.playerNamed("mona")!!
                client.tele(3134, 3616)
                client.locOp(FeroxEnclave.BARRIER, 3134, 3617, FeroxEnclave.PASS_THROUGH)
                client.awaitAt(3134, 3617)
                client.tele(3135, 3617)
                client.locOp(FeroxEnclave.BARRIER_MIRROR, 3135, 3617, FeroxEnclave.PASS_THROUGH)
                client.awaitAt(3135, 3616)

                mona.teleblockTicks = 400
                client.locOp(FeroxEnclave.BARRIER_MIRROR, 3135, 3617, FeroxEnclave.PASS_THROUGH)
                client.awaitMessage("the Tele Block refusal") { it == "[ferox.barrier_teleblocked]" }
                assertEquals(Coord(3135, 3616, 0), client.position)
            }
        }
    }

    @Test
    fun `the pool restores, and the bank chest and banker open the bank`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("nils").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                val nils = server.world.playerNamed("nils")!!
                client.commandReply("setlevel hitpoints 20") { true }
                client.tele(3127, 3633)
                client.commandReply("hit 7 me") { it.startsWith("Hit yourself") }
                client.commandReply("boost attack 9") { true }
                nils.runEnergy = 0
                client.await("the boost") { client.stat(ATTACK)?.takeIf { it.level == 9 } }
                client.await("the hit") { client.stat(HITPOINTS)?.takeIf { it.level == 13 } }
                client.locOp(FeroxEnclave.POOL, 3128, 3633, FeroxEnclave.DRINK)
                client.awaitMessage("the pool's message") { it == "[ferox.pool]" }
                client.await("Hitpoints back") { client.stat(HITPOINTS)?.takeIf { it.level == 20 } }
                client.await("Attack back to its level") { client.stat(ATTACK)?.takeIf { it.level == 1 } }
                assertEquals(Movement.MAX_ENERGY, nils.runEnergy)

                client.locOp(BANK_CHEST, 3130, 3632, 1)
                client.await("the bank from the chest") { client.openInterfaces().takeIf { BankScreen.MAIN in it } }
                client.tele(3134, 3628)
                val npc = client.await("the banker in view") { client.npcs().firstOrNull { it.id == FeroxEnclave.BANKER } }
                client.closeModal()
                client.npcOp(npc.index, 3)
                client.await("the bank from the banker") { client.openInterfaces().takeIf { BankScreen.MAIN in it } }
            }
        }
    }

    private companion object {
        const val WILDERNESS_STATISTICS = 1105
        const val TEMPTRANSMIT2 = 1602

        /** fairy_chest_bank, Ferox Enclave's bank chest in the 242 map. */
        const val BANK_CHEST = 26711
        const val HITPOINTS = 3
        const val ATTACK = 0
    }
}
