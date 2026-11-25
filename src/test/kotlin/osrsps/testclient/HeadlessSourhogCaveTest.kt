package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.Inventory
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.content.SourhogCave
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Post-quest content step 8 through the headless client: the strange hole climbed down into the Sourhog Cave and its
 * exit rope climbed back up. The rl-client script test-runner/examples/sourhog-cave.json does the same in the real
 * client, and crosses the blockage. A sourhog with the real combat file spits: it blinds a player without reinforced
 * goggles (Attack and Defence drained 90%), and not one wearing them.
 */
class HeadlessSourhogCaveTest {
    private val hole = ZoneLoc(SourhogCave.HOLE, LocShape.CENTREPIECE_STRAIGHT, 0, 3150, 3347, 0)
    private val rope = ZoneLoc(SourhogCave.ROPE, LocShape.CENTREPIECE_STRAIGHT, 0, 3157, 9714, 0)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3150 shr 6, 3347 shr 6); markLoaded(3157 shr 6, 9714 shr 6) },
            varTypes = VarTypes(6_000, mapOf(HOLE_VARBIT to VarbitType(HOLE_VARP, 0, 5)), 21_000),
            locTypes = LocTypes(
                listOf(
                    LocType(
                        SourhogCave.HOLE, "null", List(5) { null }, LocCollisionType(width = 2, length = 2), multiloc = true,
                        multiVarbit = HOLE_VARBIT, multiLocs = listOf(HOLE_ROPED, HOLE_ROPED),
                    ),
                    LocType(HOLE_ROPED, "Strange hole", listOf("Climb-down", null, null, null, null), LocCollisionType(width = 2, length = 2)),
                    LocType(SourhogCave.ROPE, "Rope", listOf("Climb-up", null, null, null, null), LocCollisionType(width = 1, length = 1)),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { add(hole); add(rope) },
            random = GameRandom(3),
            cycleMillis = millis,
        )
    }

    private val sourhog = NpcType(
        SourhogCave.SOURHOG, "Sourhog", ops = listOf(null, "Attack", null, null, null), combatLevel = 37,
        internalName = "sourhog", stats = NpcStats(hitpoints = 45, attack = 30, strength = 30, defence = 30, ranged = 35),
    )

    private fun fight(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(sourhog)
        World(
            crcs, CollisionMap().apply { markLoaded(3160 shr 6, 9700 shr 6) },
            objTypes = ObjTypes.of(ObjType(GOGGLES, "Reinforced goggles", iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = WearPos.HEAD)),
            npcTypes = types, npcSpawns = listOf(NpcSpawn(sourhog, 3160, 9700, wander = 0, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), random = GameRandom(7), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.ready() {
        command("setlevel attack 99")
        command("setlevel hitpoints 99")
        await("Attack and Hitpoints 99") { if (stat(ATTACK)?.level == 99 && stat(HITPOINTS)?.level == 99) Unit else null }
    }

    @Test
    fun `a sourhog's spit blinds a player without reinforced goggles`(@TempDir dir: Path) {
        fight(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.ready()
                client.command("tele 3161 9700 0")
                // Its spit drains 90% of Attack's base level: 99 down to 10.
                val attack = client.await("the blinding", timeoutMillis = 90_000) { client.stat(ATTACK)?.takeIf { it.level < 99 } }
                assertEquals(10, attack.level)
            }
        }
    }

    @Test
    fun `reinforced goggles keep a sourhog's spit from blinding`(@TempDir dir: Path) {
        fight(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.ready()
                client.command("item $GOGGLES 1")
                val slot = client.await("the goggles held") { client.inventory(Inventory.INV).indexOfFirst { it?.id == GOGGLES }.takeIf { it >= 0 } }
                client.ifButton(INVENTORY, INVENTORY_ITEMS, WEAR_BUTTON, slot, GOGGLES)
                client.await("the goggles worn") { client.inventory(WORN).firstOrNull { it?.id == GOGGLES } }
                client.command("tele 3161 9700 0")
                client.await("its hits", timeoutMillis = 90_000) { client.stat(HITPOINTS)?.takeIf { it.level <= 80 } }
                assertEquals(99, client.stat(ATTACK)!!.level)
            }
        }
    }

    @Test
    fun `the hole goes down below the rope, and the rope comes back up beside the hole`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 3150 3345 0")
                client.await("the teleport") { client.position?.takeIf { it == Coord(3150, 3345, 0) } }
                client.locOp(hole.id, hole.x, hole.z, SourhogCave.CLIMB)
                client.await("the cave") { client.position?.takeIf { it == Coord(SourhogCave.CAVE.x, SourhogCave.CAVE.z, 0) } }
                client.locOp(rope.id, rope.x, rope.z, SourhogCave.CLIMB)
                client.await("the surface") { client.position?.takeIf { it == Coord(SourhogCave.SURFACE.x, SourhogCave.SURFACE.z, 0) } }
            }
        }
    }

    private companion object {
        const val HOLE_VARBIT = 10582
        const val HOLE_VARP = 2748
        const val HOLE_ROPED = 40309
        const val GOGGLES = 24942
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val WEAR_BUTTON = 3
        const val WORN = 94
        const val ATTACK = 0
        const val HITPOINTS = 3
    }
}
