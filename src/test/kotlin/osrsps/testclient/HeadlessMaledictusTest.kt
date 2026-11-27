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
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.content.Maledictus
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Wilderness step W5.4 through the headless client: the Revenant maledictus in the hellhounds' ruins fights two players
 * at once, its ice spell freezes whoever stands still, and when they kill it the top damage dealer's ancient totem or
 * emblem lands on its tile and the other's blighted super restore and two blighted manta rays under them.
 */
class HeadlessMaledictusTest {
    private val boss = NpcType(
        Maledictus.BOSS, "Revenant maledictus", ops = listOf(null, "Attack", null, null, null), size = 5, combatLevel = 397,
        internalName = "wild_cave_superior", stats = NpcStats(attack = 106, strength = 126, defence = 90, hitpoints = 1250, ranged = 130, magic = 130),
    )
    private val types = NpcTypes.of(boss)
    private val objs = ObjTypes.of(
        ObjType(21810, "Ancient totem"), ObjType(21807, "Ancient emblem"), ObjType(24598, "Blighted super restore(4)"), ObjType(24589, "Blighted manta ray"),
    )

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3241 shr 6, 10165 shr 6) }, objTypes = objs, npcTypes = types, random = GameRandom(5),
            npcSpawns = listOf(NpcSpawn(boss, 3241, 10165, source = "test", respawns = false)), npcCombat = NpcCombat.load(types, SeqNames.NONE),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    @Test
    fun `it fights two players at once, freezes with ice, and splits its loot`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { a ->
                server.login("bea").use { b ->
                    a.await("the login rebuild") { a.position }
                    b.await("the login rebuild") { b.position }
                    for (client in listOf(a, b)) client.commandReply("setlevel hitpoints 99") { true }
                    a.tele(3238, 10165)
                    b.tele(3238, 10168)
                    val world = server.world
                    val ada = world.playerNamed("ada")!!
                    val bea = world.playerNamed("bea")!!
                    val npc = world.npcs.all.first { it.type.id == Maledictus.BOSS }
                    fun hurt(player: Player) = player.hitpoints < 99
                    a.keepAlive()
                    b.keepAlive()
                    a.await("both hit", timeoutMillis = 40_000) { Unit.takeIf { hurt(ada) && hurt(bea) } }
                    a.keepAlive()
                    b.keepAlive()
                    a.await("the ice spell", timeoutMillis = 40_000) {
                        Unit.takeIf { Maledictus.nextSpecial(npc) != Maledictus.Special.ICE && (ada.freezeTicks > 0 || bea.freezeTicks > 0) }
                    }
                    assertTrue(npc.multiTarget, "multi-combat in the singles-plus caves")

                    a.commandReply("god") { it.startsWith("You can") }
                    b.commandReply("god") { it.startsWith("You can") }
                    a.commandReply("hit 100 ${npc.index}") { it.startsWith("Hit Revenant maledictus") }
                    b.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                    val x = npc.x
                    val z = npc.z
                    fun at(player: Player, x: Int, z: Int) = world.groundObjs.visibleAt(player, x, z, 0).map { it.id }.sorted()
                    a.await("the loot", timeoutMillis = 20_000) { Unit.takeIf { at(bea, x, z).isNotEmpty() && at(ada, ada.x, ada.z).isNotEmpty() } }
                    assertTrue(at(bea, x, z).count { it == 21810 || it == 21807 } == 1, "bea's totem or emblem: ${at(bea, x, z)}")
                    assertEquals(listOf(24589, 24589, 24598), at(ada, ada.x, ada.z), "ada's restore and two mantas under her")
                    assertTrue(at(ada, x, z).isEmpty(), "bea's pile is hers alone")
                }
            }
        }
    }
}
