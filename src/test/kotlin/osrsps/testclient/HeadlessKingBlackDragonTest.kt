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
import osrsps.game.combat.Prayers
import osrsps.game.content.KingBlackDragon
import osrsps.game.content.KingBlackDragon.Breath
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Wilderness step W4.6 through the headless client: the Lava Maze Dungeon's lever into the King Black Dragon's lair,
 * Commune counting the players inside, and the lair's lever back; his breaths capped by an anti-dragon shield and
 * Protect from Magic, a special breath's effect taking hold; and a kill counting in his kill count with his bones and
 * hides on the floor. The rl-client script test-runner/examples/king-black-dragon.json does the levers and the fight on
 * the real map.
 */
class HeadlessKingBlackDragonTest {
    private val boss = NpcType(
        KingBlackDragon.BOSS, "King Black Dragon", size = 5, ops = listOf(null, "Attack", null, null, null), combatLevel = 276,
        internalName = "king_dragon", stats = NpcStats(attack = 240, strength = 240, defence = 240, hitpoints = 240, magic = 240),
    )
    private val types = NpcTypes.of(boss)

    // Shaped like the cache's prayer obj for Protect from Magic, as HeadlessPrayerTest has it.
    private val protectFromMagic = ObjType(
        20815, "Null", internalName = "placeholder_xmas16_marionette_unpainted",
        params = mapOf(630 to 12, 1751 to (Prayers.PRAYERBOOK shl 16 or PROTECT_FROM_MAGIC), 1752 to "Protect from Magic", 1753 to 37, 1755 to 2675),
    )

    private fun server(dir: Path, seed: Long, spawn: Boolean = true) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val text = NpcDrops::class.java.getResource(NpcDrops.RESOURCE)!!.readText()
        val names = Regex("\"item\": (\\d+), \"name\": \"([^\"]+)\"").findAll(text).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val objs = names.map { (id, name) -> ObjType(id, name, stackable = true) }.associateBy { it.id }.toMutableMap()
        objs[protectFromMagic.id] = protectFromMagic
        objs[SHIELD] = ObjType(SHIELD, "Anti-dragon shield", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.SHIELD)
        val objTypes = ObjTypes.of(*objs.values.toTypedArray())
        val lever = { id: Int -> LocType(id, "Lever", listOf("Pull", "Commune", null, null, "Private"), LocCollisionType(width = 1, length = 1)) }
        World(
            crcs, CollisionMap().apply { markLoaded(35, 73); markLoaded(47, 160) }, objTypes = objTypes, npcTypes = types,
            random = GameRandom(seed), prayerList = Prayers.load(objTypes),
            npcSpawns = if (spawn) listOf(NpcSpawn(boss, X, Z, wander = 0, source = "test")) else emptyList(),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes),
            locTypes = LocTypes(listOf(lever(KingBlackDragon.LEVER_IN), lever(KingBlackDragon.LEVER_OUT)).associateBy { it.id }),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(KingBlackDragon.LEVER_IN, WALLDECOR_NOOFFSET, 3, 3067, 10253, 0))
                add(ZoneLoc(KingBlackDragon.LEVER_OUT, WALLDECOR_NOOFFSET, 3, 2271, 4680, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    @Test
    fun `his breaths' caps follow the Wiki's table`() {
        assertEquals(65, KingBlackDragon.cap(Breath.FIRE, shield = false, prayer = false))
        assertEquals(15, KingBlackDragon.cap(Breath.FIRE, shield = true, prayer = false))
        assertEquals(20, KingBlackDragon.cap(Breath.FIRE, shield = false, prayer = true))
        assertEquals(15, KingBlackDragon.cap(Breath.FIRE, shield = true, prayer = true))
        for (special in listOf(Breath.TOXIC, Breath.SHOCK, Breath.ICE)) {
            assertEquals(50, KingBlackDragon.cap(special, shield = false, prayer = false))
            assertEquals(10, KingBlackDragon.cap(special, shield = true, prayer = false))
            assertEquals(15, KingBlackDragon.cap(special, shield = false, prayer = true))
            assertEquals(10, KingBlackDragon.cap(special, shield = true, prayer = true))
        }
    }

    @Test
    fun `a breath's projectile arrives as the breath lands, near or far`(@TempDir dir: Path) {
        server(dir, seed = 1).use { server ->
            server.login("vic").use { client ->
                client.await("the login rebuild") { client.position }
                val npc = server.world.npcs.all.first { it.type.id == KingBlackDragon.BOSS }
                val vic = server.world.playerNamed("vic")!!
                for (dx in listOf(5, 7, 10, 14)) {
                    server.world.teleport(vic, X + dx, Z + 2, 0)
                    val ticks = KingBlackDragon.breathTicks(npc, vic)
                    val p = KingBlackDragon.breathProjectile(server.world, npc, vic, Breath.FIRE, ticks)
                    assertEquals(ticks * 30, p.endTime, "lands on its tick at $dx tiles")
                    assertTrue(p.startTime < p.endTime, "a flight at $dx tiles: $p")
                }
            }
        }
    }

    @Test
    fun `protection cuts a special breath's effect to one in eight, and he ignores tolerance`() {
        assertEquals(2, KingBlackDragon.effectChance(shield = false, prayer = false))
        assertEquals(8, KingBlackDragon.effectChance(shield = true, prayer = false))
        assertEquals(8, KingBlackDragon.effectChance(shield = false, prayer = true))
        assertEquals(8, KingBlackDragon.effectChance(shield = true, prayer = true))
        assertEquals(false, NpcCombat.load(types, SeqNames.NONE)[boss]?.aggression?.tolerant)
    }

    @Test
    fun `he takes on a player who comes through the lever, far from his spawn`(@TempDir dir: Path) {
        server(dir, seed = 1).use { server ->
            server.login("wes").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.command("varp 172 1")
                client.tele(2271, 4680)
                val npc = server.world.npcs.all.first { it.type.id == KingBlackDragon.BOSS }
                val wes = server.world.playerNamed("wes")!!
                client.await("him taking wes on", timeoutMillis = 20_000) { npc.target?.takeIf { it === wes } }
                val fight = client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                val first = client.await("a breath or a swing", timeoutMillis = 30_000) { fight.lines.firstOrNull { "breathes" in it || "swings" in it } }
                // As any NPC taking a target on, his first attack waits for the next tick.
                val took = fight.lines.first { "takes on wes" in it }
                assertTrue(first.substringBefore(" ").toLong() > took.substringBefore(" ").toLong(), "$took then $first")
            }
        }
    }

    @Test
    fun `a strike made before his fight began holds his next attack`(@TempDir dir: Path) {
        server(dir, seed = 1).use { server ->
            server.login("xan").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.command("varp 172 1")
                val npc = server.world.npcs.all.first { it.type.id == KingBlackDragon.BOSS }
                val fight = client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                // As a generic strike just before the fight would leave it: held 12 ticks from the fight's own count.
                val heldUntil = fight.ticks + 12
                npc.nextAttackTick = server.world.tick + 12
                client.tele(X + 2, Z - 1)
                val first = client.await("an attack", timeoutMillis = 30_000) { fight.lines.firstOrNull { "breathes" in it || "swings" in it } }
                assertTrue(first.substringBefore(" ").toLong() >= heldUntil, "attacked before $heldUntil: $first")
            }
        }
    }

    @Test
    fun `an unprotected player is told which of his breaths hit them`(@TempDir dir: Path) {
        server(dir, seed = 1).use { server ->
            server.login("dee").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.command("varp 172 1")
                client.tele(X + 2, Z - 6)
                val keys = setOf("[kbd.icy]", "[kbd.toxic]", "[kbd.shocking]", "[kbd.fiery]")
                client.await("a breath's own line", timeoutMillis = 40_000) { client.messages().firstOrNull { it.text in keys } }
                assertTrue(client.messages().none { it.text == "[dragonfire.burnt]" })
            }
        }
    }

    @Test
    fun `the dungeon's lever takes the player into the lair, Commune counts who is there, and its own lever back`(@TempDir dir: Path) {
        server(dir, seed = 1, spawn = false).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(3067, 10254)
                client.locOp(KingBlackDragon.LEVER_IN, 3067, 10253, KingBlackDragon.COMMUNE)
                client.awaitMessage("nobody inside") { it == "[kbd.commune count=0]" }
                client.locOp(KingBlackDragon.LEVER_IN, 3067, 10253, 1)
                client.await("in the lair", timeoutMillis = 15_000) { client.position?.takeIf { it == Coord(2271, 4680, 0) } }
                assertTrue(server.world.multiCombat.at(0, 2271, 4690), "the lair is multi-way")
                client.locOp(KingBlackDragon.LEVER_OUT, 2271, 4680, 1)
                client.await("back in the dungeon", timeoutMillis = 15_000) { client.position?.takeIf { it == Coord(3067, 10253, 0) } }
            }
        }
    }

    @Test
    fun `a shield and Protect from Magic cap his breaths, and a special breath's effect takes hold`(@TempDir dir: Path) {
        server(dir, seed = 9).use { server ->
            server.login("bo").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel prayer 99") { true }
                client.commandReply("setlevel hitpoints 99") { true }
                client.command("item $SHIELD 1")
                val slot = client.await("the shield") { client.inventory(BACKPACK).indexOfFirst { it?.id == SHIELD }.takeIf { it >= 0 } }
                client.ifButton(INVENTORY, INVENTORY_ITEMS, IOP_WIELD, slot, SHIELD)
                client.await("the shield on") { client.inventory(WORN).getOrNull(WearPos.SHIELD)?.takeIf { it.id == SHIELD } }
                client.ifButton(Prayers.PRAYERBOOK, PROTECT_FROM_MAGIC, 1)
                client.await("Protect from Magic on") { Unit.takeIf { client.varp(Prayers.VARP_PRAYERS) == 1 shl 12 } }
                client.command("varp 172 1")
                client.tele(X + 2, Z - 4)
                val npc = server.world.npcs.all.first { it.type.id == KingBlackDragon.BOSS }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit King Black Dragon") }
                val fight = client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                var rounds = 0
                while (fight.lines.none { "takes hold of bo" in it }) {
                    assertTrue(++rounds <= 40, fight.lines.joinToString("\n"))
                    client.commandReply("setlevel hitpoints 99") { true }
                    client.awaitTicks(8)
                }
                val breaths = fight.lines.filter { "breathes" in it }
                assertTrue(breaths.isNotEmpty())
                for (line in breaths) {
                    val damage = line.substringAfterLast("for ").trim().toInt()
                    val cap = if ("breathes fire" in line) 15 else 10
                    assertTrue(damage <= cap, line)
                }
            }
        }
    }

    @Test
    fun `a kill counts in his kill count and leaves his bones and hides`(@TempDir dir: Path) {
        server(dir, seed = 3).use { server ->
            server.login("cy").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(X + 2, Z - 1)
                val npc = server.world.npcs.all.first { it.type.id == KingBlackDragon.BOSS }
                client.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                client.await("the kill count") {
                    client.messages().firstOrNull { it.text == "[wilderness_boss.kill_count boss=King Black Dragon count=1]" }
                }
                val cy = server.world.playerNamed("cy")!!
                assertEquals(1, cy.vars[KingBlackDragon.KILL_COUNT_VARP])
                client.await("his bones") { server.world.groundObjs.visibleAt(cy, npc.x, npc.z, 0).firstOrNull { it.id == 536 } }
                client.await("his hides") { server.world.groundObjs.visibleAt(cy, npc.x, npc.z, 0).firstOrNull { it.id == 1747 && it.count == 2 } }
            }
        }
    }

    private companion object {
        const val X = 2269
        const val Z = 4697
        const val SHIELD = 1540
        const val PROTECT_FROM_MAGIC = 21
        const val WALLDECOR_NOOFFSET = 4
        const val BACKPACK = 93
        const val WORN = 94
        const val IOP_WIELD = 3
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
    }
}
