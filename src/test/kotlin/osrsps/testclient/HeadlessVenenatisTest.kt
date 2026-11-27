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
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.EscapeCaves
import osrsps.game.content.Spindel
import osrsps.game.content.SpiderQueen
import osrsps.game.content.Venenatis
import osrsps.game.content.WildernessLairFee
import osrsps.game.content.WildernessLairFeeLines
import osrsps.game.content.WildernessLairLines
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Wilderness step W4.14 through the headless client: the Silk Chasm's way in taking the lairs' fee and putting the player
 * on level 2, and its way out (Spindel's loc) leading into the Escape Caves; Venenatis's fight, [SpiderQueen]'s at her
 * numbers, calling two spiderlings that raise her max hits to 39 and 34, moving and webbing; and a kill counting in her
 * kill count, leaving her big bones, and taking 10,000 off the fee. The rl-client script
 * test-runner/examples/venenatis.json does the way in and the fight on the real map.
 */
class HeadlessVenenatisTest {
    private val boss = NpcType(
        Venenatis.BOSS, "Venenatis", size = 4, ops = listOf(null, "Attack", null, null, null), combatLevel = 464, internalName = "venenatis",
        stats = NpcStats(attack = 300, strength = 200, defence = 321, hitpoints = 850, magic = 300, ranged = 350),
    )
    private val spiderling = NpcType(
        Venenatis.SPIDERLING, "Venenatis' Spiderling", size = 1, ops = listOf(null, "Attack", null, null, null), combatLevel = 12,
        internalName = "venenatis_spiderling", stats = NpcStats(attack = 15, strength = 20, defence = 1, hitpoints = 5, magic = 10, ranged = 20),
    )
    private val types = NpcTypes.of(boss, spiderling)

    private val varTypes = VarTypes(
        6_000,
        mapOf(WildernessLairFee.VARBIT_PAID to VarbitType(WBR_PERM_INT_1, 0, 0), WildernessLairFee.VARBIT_DISCOUNT to VarbitType(WBR_PERM_INT_1, 1, 3)),
        21_000,
    )

    private fun server(dir: Path, seed: Long, spawn: Boolean = true) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val text = NpcDrops::class.java.getResource(NpcDrops.RESOURCE)!!.readText()
        val names = Regex("\"item\": (\\d+), \"name\": \"([^\"]+)\"").findAll(text).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val objTypes = ObjTypes.of(*names.map { (id, name) -> ObjType(id, name, stackable = true) }.toTypedArray())
        val entrance = LocType(Venenatis.ENTRANCE, "Cave Entrance", listOf("Enter", "Peek", "Check-Fee", null, null), LocCollisionType(width = 4, length = 2))
        val exit = LocType(Spindel.EXIT, "Cave", listOf("Exit", null, null, null, null), LocCollisionType(length = 3))
        val webs = listOf(SpiderQueen.WEB_CENTRE, SpiderQueen.WEB_EDGE, SpiderQueen.WEB_CORNER).associateWith { LocType(it, "", listOf(null, null, null, null, null), LocCollisionType(interactType = 0)) }
        World(
            crcs, CollisionMap().apply { markLoaded(53, 159); markLoaded(52, 160); markLoaded(51, 59) }, objTypes = objTypes, npcTypes = types,
            random = GameRandom(seed), varTypes = varTypes,
            npcSpawns = if (spawn) listOf(NpcSpawn(boss, Venenatis.SPAWN.x, Venenatis.SPAWN.z, Venenatis.CHASM_LEVEL, wander = 0, source = "test")) else emptyList(),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes),
            locTypes = LocTypes(mapOf(Venenatis.ENTRANCE to entrance, Spindel.EXIT to exit) + webs),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Venenatis.ENTRANCE, 10, 0, 3321, 3794, 0))
                add(ZoneLoc(Spindel.EXIT, 10, 1, 3422, 10183, Venenatis.CHASM_LEVEL))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `Check-Fee says paid when her discounts leave nothing to pay`(@TempDir dir: Path) {
        server(dir, seed = 1, spawn = false).use { server ->
            server.login("eve").use { client ->
                client.await("the login rebuild") { client.position }
                val eve = server.world.playerNamed("eve")!!
                client.command("varbit ${WildernessLairFee.VARBIT_DISCOUNT} 5")
                client.await("five discounts") { Unit.takeIf { WildernessLairFee.fee(eve) == 0 } }
                client.tele(3322, 3793, 0)
                client.locOp(Venenatis.ENTRANCE, 3321, 3794, Venenatis.CHECK_FEE)
                client.seen("[${WildernessLairLines.FEE_PAID.key}]")
            }
        }
    }

    @Test
    fun `the way in takes the fee and puts the player in the chasm, and the way out leads into the Escape Caves`(@TempDir dir: Path) {
        server(dir, seed = 1, spawn = false).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                val abe = server.world.playerNamed("abe")!!
                client.tele(3322, 3793, 0)
                client.locOp(Venenatis.ENTRANCE, 3321, 3794, Venenatis.CHECK_FEE)
                client.seen("[${WildernessLairLines.FEE_DUE.key} fee=50000]")
                client.locOp(Venenatis.ENTRANCE, 3321, 3794, Venenatis.PEEK)
                client.seen("[${WildernessLairLines.PEEK_KILLS.key}]")

                client.command("item ${WildernessLairFee.COINS} 50000")
                client.await("the coins") { Unit.takeIf { abe.inventory.count(WildernessLairFee.COINS) == 50_000L } }
                client.locOp(Venenatis.ENTRANCE, 3321, 3794, Venenatis.ENTER)
                client.awaitDialogue("the fee's menu") { it is DialogueView.Menu && WildernessLairFeeLines.TITLE.key in it.title }
                client.chooseOption(1)
                val arrival = Coord(Venenatis.ARRIVAL.x, Venenatis.ARRIVAL.z, Venenatis.CHASM_LEVEL)
                client.await("in the chasm", timeoutMillis = 15_000) { client.position?.takeIf { it == arrival } }
                assertEquals(0L, abe.inventory.count(WildernessLairFee.COINS), "the fee taken")

                client.tele(3423, 10185, Venenatis.CHASM_LEVEL)
                client.locOp(Spindel.EXIT, 3422, 10183, 1)
                val mouths = EscapeCaves.LAIR_MOUTHS.map { Coord(it.x, it.z, 0) }
                client.await("in the Escape Caves", timeoutMillis = 15_000) { client.position?.takeIf { it in mouths } }
                assertTrue(!server.world.escapeCaves.debuffed(abe), "no debuff coming from a lair")
            }
        }
    }

    @Test
    fun `her spiderlings strengthen her, she moves every 4 attacks, and her web catches the player`(@TempDir dir: Path) {
        server(dir, seed = 4).use { server ->
            server.login("bo").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.command("varp 172 1")
                client.tele(Venenatis.SPAWN.x + 1, Venenatis.SPAWN.z + 8, Venenatis.CHASM_LEVEL)
                val npc = server.world.npcs.all.first { it.type.id == Venenatis.BOSS }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Venenatis") }
                val fight = client.await("her fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                client.await("her spiderlings") { fight.lines.firstOrNull { it.endsWith("calls 2 spiderlings") } }
                assertEquals(2, Venenatis.FIGHT.spiderlings(npc).size)
                client.await("a stronger shot", timeoutMillis = 30_000) { fight.lines.firstOrNull { "(max 39, 40% through prayer)" in it || "(max 34, 40% through prayer)" in it } }
                client.await("her move", timeoutMillis = 30_000) { fight.lines.firstOrNull { " moves to " in it } }
                for (s in Venenatis.FIGHT.spiderlings(npc).toList()) client.commandReply("hit 5 ${s.index}") { it.startsWith("Hit ") }
                client.await("her web", timeoutMillis = 60_000) { fight.lines.firstOrNull { " web over " in it } }
                client.await("caught in it", timeoutMillis = 30_000) { fight.lines.firstOrNull { it.endsWith("bo caught in the web") } }
                assertTrue(Venenatis.FIGHT.web(npc).isNotEmpty())
            }
        }
    }

    @Test
    fun `a kill counts in her kill count, leaves her big bones, and takes 10,000 off the fee`(@TempDir dir: Path) {
        server(dir, seed = 3).use { server ->
            server.login("cy").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(Venenatis.SPAWN.x + 1, Venenatis.SPAWN.z + 8, Venenatis.CHASM_LEVEL)
                val npc = server.world.npcs.all.first { it.type.id == Venenatis.BOSS }
                client.commandReply("hit 850 ${npc.index}") { it.startsWith("Hit Venenatis") }
                val cy = server.world.playerNamed("cy")!!
                client.await("the kill count") { Unit.takeIf { cy.vars[Venenatis.KILL_COUNT_VARP] == 1 } }
                client.await("her bones") { server.world.groundObjs.visibleAt(cy, npc.x, npc.z, Venenatis.CHASM_LEVEL).firstOrNull { it.id == BIG_BONES } }
                assertEquals(40_000, WildernessLairFee.fee(cy), "10,000 off for the most damage")
            }
        }
    }

    private companion object {
        const val WBR_PERM_INT_1 = 3755
        const val BIG_BONES = 532
    }
}
