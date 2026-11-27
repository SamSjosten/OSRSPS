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
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.content.RevenantDrops
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Wilderness step W5.3 through the headless client: a revenant's death drops from the revenants' table for its killer,
 * ether every kill that they can take, and a looting bag now and then, but none while they own one in the backpack, in
 * the bank or dropped for them and still lying there (Codex review on #1014).
 */
class HeadlessRevenantDropsTest {
    private val imp = NpcType(
        7881, "Revenant imp", ops = listOf(null, "Attack", null, null, null), combatLevel = 7, internalName = "wild_cave_imp",
        stats = NpcStats(attack = 5, strength = 5, defence = 4, hitpoints = 10, ranged = 5, magic = 9),
    )
    private val types = NpcTypes.of(imp, NpcType(osrsps.game.combat.Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null)))
    private val objs = ObjTypes.of(
        *(listOf(RevenantDrops.ETHER, RevenantDrops.COINS, RevenantDrops.LOOTING_BAG, RevenantDrops.LOOTING_BAG_OPEN) +
            RevenantDrops.MAIN.map { it.item } + RevenantDrops.SECONDARY.map { it.item })
            .distinct().map { ObjType(it, "item $it", stackable = it != RevenantDrops.LOOTING_BAG && it != RevenantDrops.LOOTING_BAG_OPEN) }
            .toTypedArray(),
    )

    // Four batches of imps, one row each, so each batch's drops lie apart.
    private val rows = (0 until 5).map { 10060 + it * 2 }
    private val columns = 3186..3215

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { for (x in columns step 32) markLoaded(x shr 6, 10060 shr 6); markLoaded(3215 shr 6, 10060 shr 6) },
            npcTypes = types, objTypes = objs, random = GameRandom(11),
            npcSpawns = rows.flatMap { z -> columns.map { x -> NpcSpawn(imp, x, z, wander = 0, source = "test") } },
            npcCombat = NpcCombat.load(types, SeqNames.NONE), cycleMillis = millis,
        )
    }

    @Test
    fun `ether every kill, a bag now and then, and none for a killer who owns one`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel hitpoints 99") { true }
                client.commandReply("god on") { true }
                // Well away from the imps, so none comes after abe and each dies where it spawned.
                client.command("tele 3200 10100 0")
                client.await("there") { client.position?.takeIf { it == Coord(3200, 10100, 0) } }
                val world = server.world
                val abe = world.playerNamed("abe")!!
                val bag = objs[RevenantDrops.LOOTING_BAG]!!
                val batches = world.npcs.all.filter { it.type.id == imp.id }.toList().chunked(columns.count()).withIndex().associate { (i, it) -> rows[i] to it }

                fun killRow(z: Int) {
                    for (npc in batches.getValue(z)) {
                        client.keepAlive()
                        client.commandReply("kill ${npc.index}") { it.startsWith("Killed") || it.contains("already dead") }
                    }
                    client.awaitTicks(3)
                }
                fun lying(z: Int, id: Int): Int =
                    (columns.first - 3..columns.last + 3).sumOf { x -> (z - 1..z + 1).sumOf { zz -> world.groundObjs.visibleAt(abe, x, zz, 0).count { it.id == id } } }

                // Nothing owned: some bags among 30 imps (1 in 15 each), and ether.
                killRow(rows[0])
                assertTrue(lying(rows[0], RevenantDrops.LOOTING_BAG) > 0, "a bag among 30 kills")
                assertTrue(lying(rows[0], RevenantDrops.ETHER) > 0, "ether")
                // The bags lie there for abe: owned by the next kills' reckoning, so no more. Then cleared away.
                killRow(rows[1])
                assertEquals(0, lying(rows[1], RevenantDrops.LOOTING_BAG), "a bag lying there for abe")
                for (z in rows.take(2)) for (x in columns.first - 3..columns.last + 3) for (zz in z - 1..z + 1) world.groundObjs.clear(x, zz, 0)

                // One in the backpack, then one in the bank.
                abe.inventory.add(bag, 1)
                killRow(rows[2])
                assertEquals(0, lying(rows[2], RevenantDrops.LOOTING_BAG), "a bag in the backpack")
                abe.inventory.clear()
                abe.bank.add(bag, 1)
                killRow(rows[3])
                assertEquals(0, lying(rows[3], RevenantDrops.LOOTING_BAG), "a bag in the bank")

                // The ether can be taken.
                val ether = (columns).firstNotNullOf { x -> world.groundObjs.visibleAt(abe, x, rows[3], 0).firstOrNull { it.id == RevenantDrops.ETHER }?.let { x to it } }
                client.command("tele ${ether.first} ${rows[3]} 0")
                client.await("by the ether") { client.position?.takeIf { it == Coord(ether.first, rows[3], 0) } }
                client.objOp(RevenantDrops.ETHER, ether.first, rows[3], TAKE)
                client.await("the ether taken") { Unit.takeIf { abe.inventory.count(RevenantDrops.ETHER) > 0 } }

                // Last, one only in abe's grave: skulled, so the bag is lost with the rest (exploit review R65B-7).
                abe.bank.clear()
                abe.inventory.add(bag, 1)
                client.commandReply("god off") { it.startsWith("You can") }
                client.commandReply("skull on") { it.startsWith("You're skulled") }
                client.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                client.keepAlive()
                client.await("the bag in the grave", timeoutMillis = 15_000) {
                    Unit.takeIf { !abe.dead && (world.deaths.graveOf(abe)?.items?.count(RevenantDrops.LOOTING_BAG) ?: 0L) > 0L }
                }
                assertEquals(0L, abe.inventory.count(RevenantDrops.LOOTING_BAG))
                assertTrue(RevenantDrops.ownsBag(world, abe), "owned through the grave alone")
                client.commandReply("god on") { it.startsWith("You can") }
                client.command("tele 3200 10100 0")
                client.await("back") { client.position?.takeIf { it == Coord(3200, 10100, 0) } }
                killRow(rows[4])
                assertEquals(0, lying(rows[4], RevenantDrops.LOOTING_BAG), "a bag in the grave")
            }
        }
    }

    private companion object {
        const val TAKE = 3
    }
}
