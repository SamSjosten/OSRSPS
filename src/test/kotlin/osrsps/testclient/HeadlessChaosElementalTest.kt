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
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.ChaosElemental
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Wilderness step W4.5 through the headless client: the Chaos Elemental, seeded, firing Discord in its styles,
 * throwing the player away from it with Confusion, and taking off the weapon and then other worn items with Madness;
 * never Madness at a player with a full backpack; and a kill counting in its kill count with one of its minor drops on
 * the floor. The rl-client script test-runner/examples/chaos-elemental.json fights it on the real map.
 */
class HeadlessChaosElementalTest {
    private val boss = NpcType(
        ChaosElemental.BOSS, "Chaos Elemental", size = 3, ops = listOf(null, "Attack", null, null, null), combatLevel = 305,
        internalName = "chaoselemental", stats = NpcStats(attack = 270, strength = 270, defence = 270, hitpoints = 250, magic = 270, ranged = 270),
    )
    private val types = NpcTypes.of(boss)

    private val worn = listOf(SCIMITAR to WearPos.WEAPON, HELM to WearPos.HEAD, BODY to WearPos.BODY, LEGS to WearPos.LEGS, SHIELD to WearPos.SHIELD)

    private fun server(dir: Path, seed: Long) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val text = NpcDrops::class.java.getResource(NpcDrops.RESOURCE)!!.readText()
        val names = Regex("\"item\": (\\d+), \"name\": \"([^\"]+)\"").findAll(text).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val objs = names.map { (id, name) -> ObjType(id, name, stackable = true) }.associateBy { it.id }.toMutableMap()
        for ((id, pos) in worn) objs[id] = ObjType(id, "Worn $id", iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = pos)
        objs[POT] = ObjType(POT, "Pot")
        val objTypes = ObjTypes.of(*objs.values.toTypedArray())
        World(
            crcs, CollisionMap().apply { markLoaded(X shr 6, Z shr 6); markLoaded((X shr 6) + 1, Z shr 6) }, objTypes = objTypes, npcTypes = types,
            random = GameRandom(seed), npcSpawns = listOf(NpcSpawn(boss, X, Z, wander = 0, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.wear(id: Int, pos: Int) {
        command("item $id 1")
        val slot = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }
        ifButton(INVENTORY, INVENTORY_ITEMS, IOP_WEAR, slot, id)
        await("item $id worn") { inventory(WORN).getOrNull(pos)?.takeIf { it.id == id } }
    }

    private fun HeadlessClient.fight(server: TestServer) = server.world.npcs.all.first { it.type.id == ChaosElemental.BOSS }.let { npc ->
        // Hit once so it takes the player on, aggressive or not.
        commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Chaos Elemental") }
        await("its fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
    }

    @Test
    fun `it fires discord, throws the player with confusion, and takes the weapon off first with madness`(@TempDir dir: Path) {
        server(dir, seed = 7).use { server ->
            server.login("pip").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                for ((id, pos) in worn) client.wear(id, pos)
                client.tele(X + 6, Z + 1)
                val fight = client.fight(server)
                client.await("a discord", timeoutMillis = 30_000) { fight.lines.firstOrNull { "discord (" in it } }
                val threw = client.await("a throw", timeoutMillis = 60_000) { fight.lines.firstOrNull { "threw pip to" in it } }
                val (tx, tz) = threw.substringAfter("threw pip to ").split(",").map { it.trim().toInt() }
                assertTrue(tx > X + 2, "thrown east, away from it: $threw")
                val off = client.await("madness", timeoutMillis = 60_000) { fight.lines.firstOrNull { "took off pip's" in it } }
                val slots = off.substringAfter("took off pip's ").trim().split(" ").map { it.toInt() }
                assertEquals(WearPos.WEAPON, slots.first(), off)
                assertTrue(slots.size in 1..ChaosElemental.MADNESS_ITEMS, off)
                client.await("the scimitar in the backpack") { Unit.takeIf { client.inventory(BACKPACK).any { it?.id == SCIMITAR } } }
                for (pos in slots) assertEquals(null, client.inventory(WORN).getOrNull(pos), "slot $pos taken off")
                assertTrue(tz in (Z - 10)..(Z + 10))
            }
        }
    }

    @Test
    fun `it never uses madness on a player with a full backpack`(@TempDir dir: Path) {
        server(dir, seed = 7).use { server ->
            server.login("rob").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.wear(SCIMITAR, WearPos.WEAPON)
                client.command("item $POT 28")
                client.await("a full backpack") { Unit.takeIf { client.inventory(BACKPACK).count { it?.id == POT } == 28 } }
                client.tele(X + 6, Z + 1)
                val fight = client.fight(server)
                fun attacks() = fight.lines.count { "discord (" in it || "confusion at" in it || "madness at" in it }
                client.await("fifteen attacks", timeoutMillis = 90_000) { Unit.takeIf { attacks() >= 15 } }
                assertTrue(fight.lines.none { "madness at" in it }, fight.lines.joinToString("\n"))
                assertEquals(SCIMITAR, client.inventory(WORN).getOrNull(WearPos.WEAPON)?.id)
            }
        }
    }

    @Test
    fun `a kill counts in its kill count and leaves a minor drop`(@TempDir dir: Path) {
        server(dir, seed = 3).use { server ->
            server.login("sal").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(X + 4, Z)
                val npc = server.world.npcs.all.first { it.type.id == ChaosElemental.BOSS }
                client.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                client.await("the kill count") {
                    client.messages().firstOrNull { it.text == "[wilderness_boss.kill_count boss=Chaos Elemental count=1]" }
                }
                val sal = server.world.playerNamed("sal")!!
                assertEquals(1, sal.vars[ChaosElemental.KILL_COUNT_VARP])
                val minor = setOf(24592, 24595, 24598, 536, 12701)
                client.await("a minor drop") {
                    (npc.x..npc.x + 2).flatMap { x -> (npc.z..npc.z + 2).flatMap { z -> server.world.groundObjs.visibleAt(sal, x, z, 0) } }
                        .firstOrNull { it.id in minor }
                }
            }
        }
    }

    @Test
    fun `a special's projectile arrives as the special lands, near or far`(@TempDir dir: Path) {
        server(dir, seed = 7).use { server ->
            server.login("tam").use { client ->
                client.await("the login rebuild") { client.position }
                val npc = server.world.npcs.all.first { it.type.id == ChaosElemental.BOSS }
                val tam = server.world.playerNamed("tam")!!
                for (dx in listOf(3, 5, 8, 12)) {
                    server.world.teleport(tam, X + dx, Z + 1, 0)
                    val ticks = ChaosElemental.specialTicks(npc, tam)
                    val p = ChaosElemental.specialProjectile(server.world, npc, tam, ChaosElemental.SPOT_MADNESS, ticks)
                    assertEquals(ticks * 30, p.endTime, "lands on its tick at $dx tiles")
                    assertTrue(p.startTime < p.endTime, "a flight at $dx tiles: $p")
                }
            }
        }
    }

    private companion object {
        const val X = 3261
        const val Z = 3927
        const val SCIMITAR = 1333
        const val HELM = 1163
        const val BODY = 1127
        const val LEGS = 1079
        const val SHIELD = 1201
        const val POT = 1931
        const val BACKPACK = 93
        const val WORN = 94
        const val IOP_WEAR = 3
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
    }
}
