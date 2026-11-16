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
import osrsps.game.content.ChaosFanatic
import osrsps.game.content.ChaosFanaticLines
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Wilderness step W4.3 through the headless client: the Chaos Fanatic, seeded, casting his bolts with a shout each time,
 * then his three exploding bolts (one on the player's tile) followed by twelve plain bolts; his disarm, which a full
 * backpack stops and which otherwise puts the player's weapon in the backpack; and a kill counting in his kill count
 * with his bones on the floor. The rl-client script test-runner/examples/chaos-fanatic.json fights him on the real map.
 */
class HeadlessChaosFanaticTest {
    private val boss = NpcType(
        ChaosFanatic.BOSS, "Chaos Fanatic", ops = listOf(null, "Attack", null, null, null), combatLevel = 202,
        internalName = "chaos_fanatic", stats = NpcStats(attack = 1, strength = 1, defence = 220, hitpoints = 225, magic = 200),
    )
    private val types = NpcTypes.of(boss)

    private fun server(dir: Path, seed: Long) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val text = NpcDrops::class.java.getResource(NpcDrops.RESOURCE)!!.readText()
        val names = Regex("\"item\": (\\d+), \"name\": \"([^\"]+)\"").findAll(text).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val objs = names.map { (id, name) -> ObjType(id, name, stackable = true) }.associateBy { it.id }.toMutableMap()
        objs[SCIMITAR] = ObjType(SCIMITAR, "Rune scimitar", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON)
        objs[POT] = ObjType(POT, "Pot")
        objs[DART] = ObjType(DART, "Bronze dart", stackable = true, iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON)
        World(
            crcs, CollisionMap().apply { markLoaded(X shr 6, Z shr 6) }, objTypes = ObjTypes.of(*objs.values.toTypedArray()),
            npcTypes = types, random = GameRandom(seed), npcSpawns = listOf(NpcSpawn(boss, X, Z, wander = 0, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, ObjTypes.of(*objs.values.toTypedArray())),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.wield(id: Int) {
        command("item $id 1")
        val slot = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }
        ifButton(INVENTORY, INVENTORY_ITEMS, IOP_WIELD, slot, id)
        await("item $id worn") { inventory(WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == id } }
    }

    private fun HeadlessClient.fight(server: TestServer) = server.world.npcs.all.first { it.type.id == ChaosFanatic.BOSS }.let { npc ->
        // Hit once so he takes the player on, aggressive or not.
        commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Chaos Fanatic") }
        npc to await("his fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
    }

    @Test
    fun `he casts with a shout, and his three exploding bolts come before twelve plain ones`(@TempDir dir: Path) {
        server(dir, seed = 11).use { server ->
            server.login("ike").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(X + 4, Z)
                val (npc, fight) = client.fight(server)
                client.await("a bolt", timeoutMillis = 30_000) { fight.lines.firstOrNull { "casts at ike" in it } }
                val shouts = ChaosFanaticLines.SHOUTS.map { "[${it.key}]" }.toSet()
                client.await("a shout") { client.npcSaid().firstOrNull { it.index == npc.index && it.text in shouts } }
                val explosions = client.await("the explosions", timeoutMillis = 60_000) { fight.lines.firstOrNull { "explosions on" in it } }
                val landings = explosions.substringAfter("explosions on ").trim().split(" ")
                assertEquals(3, landings.size, explosions)
                client.await("three explosions' hazards") { Unit.takeIf { fight.lines.dropWhile { it != explosions }.count { "hazard on" in it } >= 3 } }
                val hazards = fight.lines.dropWhile { it != explosions }.filter { "hazard on" in it }.take(3)
                assertTrue(hazards.all { it.endsWith("from tick ${ChaosFanatic.EXPLOSION_TICKS} for 1") }, hazards.joinToString("\n"))
                assertEquals(landings, hazards.map { it.substringAfter("hazard on ").substringBefore(" from") }, "one tile each")
                fun attacks() = fight.lines.dropWhile { it != explosions }.drop(1)
                    .filter { "casts at" in it || "explosions on" in it || "disarms" in it }
                client.await("twelve attacks after", timeoutMillis = 30_000) { Unit.takeIf { attacks().size >= ChaosFanatic.COOLDOWN } }
                assertTrue(attacks().take(ChaosFanatic.COOLDOWN).all { "casts at" in it }, attacks().joinToString("\n"))
            }
        }
    }

    @Test
    fun `his disarm is stopped by a full backpack and otherwise takes the weapon off`(@TempDir dir: Path) {
        server(dir, seed = 5).use { server ->
            server.login("kit").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.wield(SCIMITAR)
                client.command("item $POT 28")
                client.await("a full backpack") { Unit.takeIf { client.inventory(BACKPACK).count { it?.id == POT } == 28 } }
                client.tele(X + 3, Z)
                val (_, fight) = client.fight(server)
                val first = client.await("a disarm", timeoutMillis = 90_000) { fight.lines.firstOrNull { "disarms kit" in it } }
                client.awaitTicks(5)
                assertTrue(fight.lines.none { "took kit's weapon" in it }, "not with a full backpack")
                assertEquals(SCIMITAR, client.inventory(WORN).getOrNull(WearPos.WEAPON)?.id, "still wielded")

                client.command("empty")
                client.await("an empty backpack") { Unit.takeIf { client.inventory(BACKPACK).none { it?.id == POT } } }
                client.await("the weapon taken", timeoutMillis = 90_000) { fight.lines.firstOrNull { "took kit's weapon $SCIMITAR" in it && it != first } }
                client.await("the scimitar in the backpack") { Unit.takeIf { client.inventory(BACKPACK).any { it?.id == SCIMITAR } } }
                assertEquals(null, client.inventory(WORN).getOrNull(WearPos.WEAPON), "nothing wielded")
            }
        }
    }

    @Test
    fun `his disarm puts thrown weapons onto the stack already in the backpack`(@TempDir dir: Path) {
        server(dir, seed = 5).use { server ->
            server.login("kit").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.command("item $DART 10")
                val slot = client.await("the darts in the backpack") { client.inventory(BACKPACK).indexOfFirst { it?.id == DART }.takeIf { it >= 0 } }
                client.ifButton(INVENTORY, INVENTORY_ITEMS, IOP_WIELD, slot, DART)
                client.await("the darts worn") { client.inventory(WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == DART && it.count == 10 } }
                client.command("item $DART 5")
                client.await("five more darts") { Unit.takeIf { client.inventory(BACKPACK).any { it?.id == DART && it.count == 5 } } }
                client.tele(X + 3, Z)
                val (_, fight) = client.fight(server)
                client.await("the darts taken", timeoutMillis = 90_000) { fight.lines.firstOrNull { "took kit's weapon $DART" in it } }
                client.awaitTicks(2)
                val darts = client.inventory(BACKPACK).filterNotNull().filter { it.id == DART }
                assertEquals(listOf(15), darts.map { it.count }, "one stack of all fifteen")
            }
        }
    }

    @Test
    fun `his explosions hurt only the player he aims at, not one standing beside them`(@TempDir dir: Path) {
        server(dir, seed = 11).use { server ->
            server.login("ike").use { client ->
                server.login("amy").use { other ->
                    client.await("the login rebuild") { client.position }
                    other.await("the login rebuild") { other.position }
                    client.commandReply("god") { it.startsWith("You can") }
                    other.commandReply("god") { it.startsWith("You can") }
                    // Auto retaliate off, so neither walks up to him and both stay on the one tile.
                    client.command("varp 172 1")
                    other.command("varp 172 1")
                    client.tele(X + 4, Z)
                    val (_, fight) = client.fight(server)
                    // On ike's tile once he has taken ike on.
                    other.tele(X + 4, Z)
                    val explosions = client.await("the explosions", timeoutMillis = 60_000) { fight.lines.firstOrNull { "explosions on" in it } }
                    assertTrue(explosions.substringAfter("explosions on ").startsWith("${X + 4},$Z"), explosions)
                    client.await("the explosions landing on ike") { fight.lines.firstOrNull { "hazard hit ike" in it } }
                    client.awaitTicks(2)
                    assertTrue(fight.lines.none { "hazard hit amy" in it }, fight.lines.joinToString("\n"))
                }
            }
        }
    }

    @Test
    fun `a kill counts in his kill count and leaves his bones`(@TempDir dir: Path) {
        server(dir, seed = 3).use { server ->
            server.login("lou").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(X + 1, Z)
                val npc = server.world.npcs.all.first { it.type.id == ChaosFanatic.BOSS }
                client.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                client.await("the kill count") {
                    client.messages().firstOrNull { it.text == "[wilderness_boss.kill_count boss=Chaos Fanatic count=1]" }
                }
                val lou = server.world.playerNamed("lou")!!
                assertEquals(1, lou.vars[ChaosFanatic.KILL_COUNT_VARP])
                client.await("his bones") { server.world.groundObjs.visibleAt(lou, npc.x, npc.z, 0).firstOrNull { it.id == BONES } }
            }
        }
    }

    private companion object {
        const val X = 2979
        const val Z = 3846
        const val BONES = 526
        const val SCIMITAR = 1333
        const val POT = 1931
        const val DART = 806
        const val BACKPACK = 93
        const val WORN = 94
        const val IOP_WIELD = 3
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
    }
}
