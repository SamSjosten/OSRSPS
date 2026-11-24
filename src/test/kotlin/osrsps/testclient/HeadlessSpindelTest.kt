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
import osrsps.game.combat.AttackType
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.combat.SureHit
import osrsps.game.content.Spindel
import osrsps.game.content.SpiderQueen
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
 * Wilderness step W4.10 through the headless client: the Web Chasm's way in taking the lairs' fee and putting the player
 * on level 2, and its way out; Spindel's first ranged attack calling two spiderlings, which raise her max hit and let
 * her through a prayer, her moving every 4 attacks, and her web on the cycle's third magic attack catching whoever
 * stands in it; and a kill counting in her kill count, leaving her big bones, and taking 10,000 off the fee. The
 * rl-client script test-runner/examples/spindel.json does the way in and the fight on the real map.
 */
class HeadlessSpindelTest {
    private val boss = NpcType(
        Spindel.BOSS, "Spindel", size = 4, ops = listOf(null, "Attack", null, null, null), combatLevel = 302, internalName = "venenatis_singles",
        stats = NpcStats(attack = 200, strength = 130, defence = 225, hitpoints = 515, magic = 235, ranged = 286),
    )
    private val spiderling = NpcType(
        Spindel.SPIDERLING, "Spindel's Spiderling", size = 1, ops = listOf(null, "Attack", null, null, null), combatLevel = 12,
        internalName = "spindel_spiderling", stats = NpcStats(attack = 15, strength = 20, defence = 1, hitpoints = 5, magic = 10, ranged = 20),
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
        val entrance = LocType(Spindel.ENTRANCE, "Cave Entrance", listOf("Enter", "Peek", "Check-Fee", null, null), LocCollisionType(width = 3, length = 3))
        val exit = LocType(Spindel.EXIT, "Cave", listOf("Exit", null, null, null, null), LocCollisionType(length = 3))
        val webs = listOf(SpiderQueen.WEB_CENTRE, SpiderQueen.WEB_EDGE, SpiderQueen.WEB_CORNER).associateWith { LocType(it, "", listOf(null, null, null, null, null), LocCollisionType(interactType = 0)) }
        World(
            crcs, CollisionMap().apply { markLoaded(25, 180); markLoaded(49, 58) }, objTypes = objTypes, npcTypes = types,
            random = GameRandom(seed), varTypes = varTypes,
            npcSpawns = if (spawn) listOf(NpcSpawn(boss, Spindel.SPAWN.x, Spindel.SPAWN.z, Spindel.CHASM_LEVEL, wander = 0, source = "test")) else emptyList(),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes),
            locTypes = LocTypes(mapOf(Spindel.ENTRANCE to entrance, Spindel.EXIT to exit) + webs),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Spindel.ENTRANCE, 10, 0, 3183, 3744, 0))
                add(ZoneLoc(Spindel.EXIT, 10, 1, 1630, 11527, Spindel.CHASM_LEVEL))
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
    fun `the way in takes the fee and puts the player in the chasm, and the way out leads back`(@TempDir dir: Path) {
        server(dir, seed = 1, spawn = false).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                val abe = server.world.playerNamed("abe")!!
                client.tele(Spindel.OUTSIDE.x, Spindel.OUTSIDE.z, 0)
                client.locOp(Spindel.ENTRANCE, 3183, 3744, Spindel.CHECK_FEE)
                client.seen("[${WildernessLairLines.FEE_DUE.key} fee=50000]")
                client.locOp(Spindel.ENTRANCE, 3183, 3744, Spindel.PEEK)
                client.seen("[${WildernessLairLines.PEEK_KILLS.key}]")

                client.command("item ${WildernessLairFee.COINS} 50000")
                client.await("the coins") { Unit.takeIf { abe.inventory.count(WildernessLairFee.COINS) == 50_000L } }
                client.locOp(Spindel.ENTRANCE, 3183, 3744, Spindel.ENTER)
                client.awaitDialogue("the fee's menu") { it is DialogueView.Menu && WildernessLairFeeLines.TITLE.key in it.title }
                client.chooseOption(1)
                val arrival = Coord(Spindel.ARRIVAL.x, Spindel.ARRIVAL.z, Spindel.CHASM_LEVEL)
                client.await("in the chasm", timeoutMillis = 15_000) { client.position?.takeIf { it == arrival } }
                assertEquals(0L, abe.inventory.count(WildernessLairFee.COINS), "the fee taken")

                client.tele(1630, 11530, Spindel.CHASM_LEVEL)
                client.locOp(Spindel.EXIT, 1630, 11527, 1)
                client.await("outside", timeoutMillis = 15_000) { client.position?.takeIf { it == Coord(Spindel.OUTSIDE.x, Spindel.OUTSIDE.z, 0) } }
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
                client.tele(Spindel.SPAWN.x + 1, Spindel.SPAWN.z + 8, Spindel.CHASM_LEVEL)
                val npc = server.world.npcs.all.first { it.type.id == Spindel.BOSS }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Spindel") }
                val fight = client.await("her fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                assertTrue(npc.unleashed, "unleashed, so no leash or far-off player holds her (R46B-1, R46B-2)")
                client.await("her spiderlings") { fight.lines.firstOrNull { it.endsWith("calls 2 spiderlings") } }
                assertEquals(2, Spindel.FIGHT.spiderlings(npc).size)
                val bo = server.world.playerNamed("bo")!!
                assertTrue(Spindel.FIGHT.spiderlings(npc).all { it.spawn.x < npc.x }, "never east of her")
                assertEquals(SureHit.MAX, server.world.combat.sureHit(bo, Spindel.FIGHT.spiderlings(npc).first(), AttackType.SLASH), "max hits on spiderlings")
                assertEquals(SureHit.ROLLED, server.world.combat.sureHit(bo, npc, AttackType.SLASH))
                client.await("a stronger shot", timeoutMillis = 30_000) { fight.lines.firstOrNull { "(max 35, 40% through prayer)" in it || "(max 28, 40% through prayer)" in it } }
                client.await("her move", timeoutMillis = 30_000) { fight.lines.firstOrNull { " moves to " in it } }
                for (s in Spindel.FIGHT.spiderlings(npc).toList()) client.commandReply("hit 5 ${s.index}") { it.startsWith("Hit ") }
                client.await("her web", timeoutMillis = 60_000) { fight.lines.firstOrNull { " web over " in it } }
                client.await("caught in it", timeoutMillis = 30_000) { fight.lines.firstOrNull { it.endsWith("bo caught in the web") } }
                assertTrue(Spindel.FIGHT.web(npc).isNotEmpty())
            }
        }
    }

    @Test
    fun `stood under, she steps out and keeps fighting`(@TempDir dir: Path) {
        server(dir, seed = 5).use { server ->
            server.login("di").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.command("varp 172 1") // auto retaliate off: a spiderling's swing mustn't walk them off the tile
                client.tele(Spindel.SPAWN.x + 1, Spindel.SPAWN.z + 8, Spindel.CHASM_LEVEL)
                val npc = server.world.npcs.all.first { it.type.id == Spindel.BOSS }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Spindel") }
                val fight = client.await("her fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                client.tele(npc.x + 1, npc.z + 1, Spindel.CHASM_LEVEL)
                val di = server.world.playerNamed("di")!!
                client.await("out from under her") { Unit.takeIf { !npc.covers(di.x, di.z) } }
                val attacks = fight.lines.size
                client.await("her next attack", timeoutMillis = 30_000) { Unit.takeIf { fight.lines.drop(attacks).any { " at di " in it || "bites di" in it } } }
            }
        }
    }

    @Test
    fun `a kill counts in her kill count, leaves her big bones, and takes 10,000 off the fee`(@TempDir dir: Path) {
        server(dir, seed = 3).use { server ->
            server.login("cy").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(Spindel.SPAWN.x + 1, Spindel.SPAWN.z + 8, Spindel.CHASM_LEVEL)
                val npc = server.world.npcs.all.first { it.type.id == Spindel.BOSS }
                client.commandReply("hit 515 ${npc.index}") { it.startsWith("Hit Spindel") }
                val cy = server.world.playerNamed("cy")!!
                client.await("the kill count") { Unit.takeIf { cy.vars[Spindel.KILL_COUNT_VARP] == 1 } }
                client.await("her bones") { server.world.groundObjs.visibleAt(cy, npc.x, npc.z, Spindel.CHASM_LEVEL).firstOrNull { it.id == BIG_BONES } }
                assertEquals(40_000, WildernessLairFee.fee(cy), "10,000 off for the most damage")
            }
        }
    }

    private companion object {
        const val WBR_PERM_INT_1 = 3755
        const val BIG_BONES = 532
    }
}
