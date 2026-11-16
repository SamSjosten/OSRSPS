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
import osrsps.game.combat.MeleeCombat
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.combat.Poison
import osrsps.game.combat.Prayers
import osrsps.game.content.Scorpia
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Wilderness step W4.4 through the headless client: the Scorpion Pit's cavern down to Scorpia's cave and its crevice
 * back up; Scorpia stabbing a player and draining 2 prayer through Protect from Melee; her two guardians coming on the
 * next attack once she is on 99 hitpoints, healing her, and not coming back once killed; and a kill counting in her kill
 * count. The rl-client script test-runner/examples/scorpia.json does the cave and the fight on the real map.
 */
class HeadlessScorpiaTest {
    private val boss = NpcType(
        Scorpia.BOSS, "Scorpia", size = 5, ops = listOf(null, "Attack", null, null, null), combatLevel = 225,
        internalName = "scorpia", stats = NpcStats(attack = 250, strength = 150, defence = 180, hitpoints = 200),
    )
    private val guardian = NpcType(
        Scorpia.GUARDIAN, "Scorpia's guardian", ops = listOf(null, "Attack", null, null, null), combatLevel = 47,
        internalName = "scorpia_guardian", stats = NpcStats(defence = 60, hitpoints = 70, magic = 30, ranged = 30),
    )
    private val offspring = NpcType(
        Scorpia.OFFSPRING, "Scorpia's offspring", ops = listOf(null, "Attack", null, null, null), combatLevel = 15,
        internalName = "scorpia_minion", stats = NpcStats(hitpoints = 5, ranged = 60),
    )
    private val types = NpcTypes.of(boss, guardian, offspring)

    // Shaped like the cache's prayer obj for Protect from Melee, as HeadlessPrayerTest has it.
    private val protectFromMelee = ObjType(
        20817, "Null", internalName = "placeholder_xmas16_marionette_blue",
        params = mapOf(630 to 14, 1751 to (Prayers.PRAYERBOOK shl 16 or PROTECT_FROM_MELEE), 1752 to "Protect from Melee", 1753 to 43, 1755 to 2676),
    )

    private val way = Scorpia.WAYS[1]

    private fun server(dir: Path, seed: Long) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val text = NpcDrops::class.java.getResource(NpcDrops.RESOURCE)!!.readText()
        val names = Regex("\"item\": (\\d+), \"name\": \"([^\"]+)\"").findAll(text).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val objs = names.map { (id, name) -> ObjType(id, name, stackable = true) }.associateBy { it.id }.toMutableMap()
        objs[protectFromMelee.id] = protectFromMelee
        val objTypes = ObjTypes.of(*objs.values.toTypedArray())
        World(
            crcs, CollisionMap().apply { markLoaded(50, 161); markLoaded(50, 61) }, objTypes = objTypes, npcTypes = types,
            random = GameRandom(seed), prayerList = Prayers.load(objTypes),
            npcSpawns = listOf(NpcSpawn(boss, X, Z, wander = 0, source = "test"), NpcSpawn(offspring, OX, OZ, wander = 0, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes),
            locTypes = LocTypes(
                listOf(
                    LocType(Scorpia.ENTRANCE, "Cavern", listOf("Enter", null, null, null, null), LocCollisionType(width = 4, length = 2)),
                    LocType(Scorpia.EXIT, "Crevice", listOf("Use", null, null, null, null), LocCollisionType(width = 1, length = 1)),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Scorpia.ENTRANCE, LocShape.CENTREPIECE_STRAIGHT, 0, way.cavern.x, way.cavern.z, 0))
                add(ZoneLoc(Scorpia.EXIT, LocShape.WALL_STRAIGHT, 3, way.crevice.x, way.crevice.z, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    @Test
    fun `the Scorpion Pit's cavern leads down to her cave and its crevice back up`(@TempDir dir: Path) {
        server(dir, seed = 1).use { server ->
            server.login("ash").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(way.above.x, way.above.z)
                client.locOp(Scorpia.ENTRANCE, way.cavern.x, way.cavern.z, Scorpia.ENTER)
                client.await("down in the cave") { client.position?.takeIf { it == Coord(way.below.x, way.below.z, 0) } }
                assertTrue(server.world.multiCombat.at(0, way.below.x, way.below.z), "the cave is multi-way")
                client.locOp(Scorpia.EXIT, way.crevice.x, way.crevice.z, Scorpia.USE)
                client.await("back up") { client.position?.takeIf { it == Coord(way.above.x, way.above.z, 0) } }
            }
        }
    }

    @Test
    fun `she drains prayer through Protect from Melee, and her guardians come once at 99 and heal her`(@TempDir dir: Path) {
        server(dir, seed = 4).use { server ->
            server.login("bea").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("setlevel prayer 99") { true }
                // Auto retaliate off, so only ::hit attacks her.
                client.command("varp 172 1")
                client.await("auto retaliate off") { Unit.takeIf { client.varp(172) == 1 } }
                client.tele(X - 1, Z + 2)
                client.ifButton(Prayers.PRAYERBOOK, PROTECT_FROM_MELEE, 1)
                client.await("Protect from Melee on") { Unit.takeIf { client.varp(Prayers.VARP_PRAYERS) == 1 shl 14 } }
                val npc = server.world.npcs.all.first { it.type.id == Scorpia.BOSS }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Scorpia") }
                val fight = client.await("her fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                client.await("a stab draining prayer", timeoutMillis = 30_000) { fight.lines.firstOrNull { "stabs bea, draining 2 prayer" in it } }
                assertTrue(fight.lines.none { "guardians come" in it }, "no guardians at 199")

                client.commandReply("hit 100 ${npc.index}") { it.startsWith("Hit Scorpia") }
                client.await("99 hitpoints") { Unit.takeIf { npc.hitpoints <= Scorpia.GUARDIANS_AT } }
                client.awaitTicks(3)
                assertTrue(fight.lines.none { "guardians come" in it }, "not before the next attack on her")
                client.commandReply("hit 0 ${npc.index}") { it.startsWith("Hit Scorpia") }
                client.await("her guardians") { fight.lines.firstOrNull { "guardians come" in it } }
                val guardians = server.world.npcs.all.filter { it.type.id == Scorpia.GUARDIAN }.toList()
                assertEquals(2, guardians.size)
                val low = npc.hitpoints
                client.await("a heal", timeoutMillis = 20_000) { fight.lines.firstOrNull { "a guardian heals her" in it } }
                assertTrue(npc.hitpoints > low, "healed from $low to ${npc.hitpoints}")
                // The bar shows the heal, with no hit on her since.
                client.await("her bar at the healed hitpoints") {
                    client.npcHealth(npc.index)?.takeIf { it.fill == MeleeCombat.healthBarFill(npc.hitpoints, npc.maxHitpoints) && npc.hitpoints > low }
                }

                for (g in guardians) client.commandReply("kill ${g.index}") { it.startsWith("Killed") }
                client.awaitTicks(10)
                assertEquals(1, fight.lines.count { "guardians come" in it }, "once a fight")
                assertTrue(server.world.npcs.all.none { it.type.id == Scorpia.GUARDIAN && !it.dead }, "killed, they don't come back")
            }
        }
    }

    @Test
    fun `a guardian with nothing left to heal leaves the world as any NPC does`(@TempDir dir: Path) {
        server(dir, seed = 4).use { server ->
            server.login("cal").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.command("varp 172 1")
                client.await("auto retaliate off") { Unit.takeIf { client.varp(172) == 1 } }
                client.tele(X - 1, Z + 2)
                val npc = server.world.npcs.all.first { it.type.id == Scorpia.BOSS }
                client.commandReply("hit 101 ${npc.index}") { it.startsWith("Hit Scorpia") }
                val fight = client.await("her fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                client.commandReply("hit 0 ${npc.index}") { it.startsWith("Hit Scorpia") }
                client.await("her guardians") { fight.lines.firstOrNull { "guardians come" in it } }
                val guardians = server.world.npcs.all.filter { it.type.id == Scorpia.GUARDIAN }.toList()
                client.await("the guardians in view") { Unit.takeIf { guardians.all { g -> client.npcs().any { it.index == g.index } } } }
                // Healed back to full, they have nothing to heal and leave LEAVE_TICKS after their last heal.
                client.await("a guardian leaving", timeoutMillis = 30_000) { fight.lines.firstOrNull { "a guardian leaves" in it } }
                client.await("both gone", timeoutMillis = 10_000) { Unit.takeIf { fight.lines.count { "a guardian leaves" in it } == 2 } }
                client.await("the guardians gone from view") { Unit.takeIf { client.npcs().none { v -> guardians.any { it.index == v.index } } } }
                assertTrue(guardians.none { g -> server.world.npcs.all.any { it === g } }, "out of the world")
            }
        }
    }

    @Test
    fun `her offspring's shots poison`(@TempDir dir: Path) {
        server(dir, seed = 2).use { server ->
            server.login("dee").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.command("varp 172 1")
                client.tele(OX + 3, OZ)
                val npc = server.world.npcs.all.first { it.type.id == Scorpia.OFFSPRING }
                client.commandReply("hit 0 ${npc.index}") { it.startsWith("Hit Scorpia's offspring") }
                client.await("poisoned", timeoutMillis = 60_000) { Unit.takeIf { client.varp(Poison.VARP) > 0 } }
            }
        }
    }

    @Test
    fun `a kill counts in her kill count`(@TempDir dir: Path) {
        server(dir, seed = 3).use { server ->
            server.login("cyd").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(X - 1, Z + 2)
                val npc = server.world.npcs.all.first { it.type.id == Scorpia.BOSS }
                client.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                client.await("the kill count") {
                    client.messages().firstOrNull { it.text == "[wilderness_boss.kill_count boss=Scorpia count=1]" }
                }
                assertEquals(1, server.world.playerNamed("cyd")!!.vars[Scorpia.KILL_COUNT_VARP])
            }
        }
    }

    @Test
    fun `she comes for a player at the south crevice's foot, past her spawn leash`(@TempDir dir: Path) {
        server(dir, seed = 4).use { server ->
            server.login("dot").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                val below = Scorpia.WAYS.first().below
                client.tele(below.x, below.z)
                val npc = server.world.npcs.all.first { it.type.id == Scorpia.BOSS }
                // From here, 9 tiles south of her spawn, a leashed Scorpia never reached them (exploit review round 39).
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Scorpia") }
                client.await("her past the leash", timeoutMillis = 15_000) { Unit.takeIf { Z - npc.z > 7 } }
                assertTrue(npc.unleashed)
            }
        }
    }

    private companion object {
        /** An offspring away from her, so her fight doesn't start. */
        const val OX = 3220
        const val OZ = 10340
        const val X = 3233
        const val Z = 10341
        const val PROTECT_FROM_MELEE = 23
    }
}
