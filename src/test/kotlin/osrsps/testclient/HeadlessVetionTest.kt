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
import osrsps.game.content.Calvarion
import osrsps.game.content.EscapeCaves
import osrsps.game.content.Vetion
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
 * Wilderness step W4.13 through the headless client: Vet'ion's Rest's way in taking the lairs' fee and dropping the player
 * on level 1, and its way out into the Escape Caves; his lightning, his hellhounds at half his hitpoints keeping him
 * from harm while they live, and his enraged form at 0 with full hitpoints; and his enraged form's death counting in his
 * kill count, leaving his big bones, and taking 10,000 off the fee. The rl-client script test-runner/examples/vetion.json
 * does the way in and the fight on the real map.
 */
class HeadlessVetionTest {
    private val stats = NpcStats(attack = 430, strength = 430, defence = 395, hitpoints = 255, magic = 300)
    private val normal = NpcType(Vetion.BOSS, "Vet'ion", size = 3, ops = listOf(null, "Attack", null, null, null), combatLevel = 454, internalName = "vetion", stats = stats)
    private val enraged = NpcType(Vetion.ENRAGED, "Vet'ion", size = 3, ops = listOf(null, "Attack", null, null, null), combatLevel = 454, internalName = "vetion_2", stats = stats)
    private val hound = NpcType(
        Vetion.HOUND, "Skeleton Hellhound", size = 2, ops = listOf(null, "Attack", null, null, null), combatLevel = 194,
        internalName = "vetion_hellhound_jnr", stats = NpcStats(attack = 210, strength = 250, defence = 150, hitpoints = 30),
    )
    private val greater = NpcType(
        Vetion.GREATER_HOUND, "Greater Skeleton Hellhound", size = 2, ops = listOf(null, "Attack", null, null, null), combatLevel = 231,
        internalName = "vetion_hellhound_snr", stats = NpcStats(attack = 228, strength = 295, defence = 180, hitpoints = 30),
    )
    private val types = NpcTypes.of(normal, enraged, hound, greater)

    private val varTypes = VarTypes(
        6_000,
        mapOf(WildernessLairFee.VARBIT_PAID to VarbitType(WBR_PERM_INT_1, 0, 0), WildernessLairFee.VARBIT_DISCOUNT to VarbitType(WBR_PERM_INT_1, 1, 3)),
        21_000,
    )

    private fun server(dir: Path, seed: Long, spawn: Boolean = true) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val text = NpcDrops::class.java.getResource(NpcDrops.RESOURCE)!!.readText()
        val names = Regex("\"item\": (\\d+), \"name\": \"([^\"]+)\"").findAll(text).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val objTypes = ObjTypes.of(*names.map { (id, name) -> ObjType(id, name, stackable = true) }.toTypedArray())
        val entrance = LocType(Vetion.ENTRANCE, "Crevice", listOf("Jump-Down", "Peek", "Check-Fee", null, null), LocCollisionType(width = 4, length = 2))
        val exit = LocType(Calvarion.EXIT, "Cave", listOf("Exit", null, null, null, null), LocCollisionType(width = 2, length = 3))
        World(
            crcs, CollisionMap().apply { markLoaded(51, 159); markLoaded(52, 160); markLoaded(50, 59) }, objTypes = objTypes, npcTypes = types,
            random = GameRandom(seed), varTypes = varTypes,
            npcSpawns = if (spawn) listOf(NpcSpawn(normal, Vetion.SPAWN.x, Vetion.SPAWN.z, Vetion.REST_LEVEL, wander = 0, source = "test")) else emptyList(),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes),
            locTypes = LocTypes(mapOf(Vetion.ENTRANCE to entrance, Calvarion.EXIT to exit)),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Vetion.ENTRANCE, 10, 1, 3221, 3787, 0))
                add(ZoneLoc(Calvarion.EXIT, 10, 1, 3294, 10190, Vetion.REST_LEVEL))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    /** Awaits [npc]'s hounds, kills them, and awaits [line] after the [skip] earlier ones. */
    private fun HeadlessClient.killHounds(fight: osrsps.game.encounter.Encounter, npc: osrsps.game.npc.Npc, line: String, skip: Int = 0) {
        await("his hounds") { fight.lines.filter { it.endsWith("calls 2 hounds") }.drop(skip).firstOrNull() }
        for (h in Vetion.FIGHT.hounds(npc).toList()) commandReply("hit 30 ${h.index}") { it.startsWith("Hit ") }
        await("his hounds dead") { fight.lines.filter { it.endsWith(line) }.drop(skip).firstOrNull() }
    }

    @Test
    fun `the way in takes the fee and drops the player into the rest, and the way out leads into the Escape Caves`(@TempDir dir: Path) {
        server(dir, seed = 1, spawn = false).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                val abe = server.world.playerNamed("abe")!!
                client.tele(3220, 3788, 0)
                client.locOp(Vetion.ENTRANCE, 3221, 3787, Vetion.CHECK_FEE)
                client.seen("[${WildernessLairLines.FEE_DUE.key} fee=50000]")
                client.locOp(Vetion.ENTRANCE, 3221, 3787, Vetion.PEEK)
                client.seen("[${WildernessLairLines.PEEK_KILLS.key}]")

                client.command("item ${WildernessLairFee.COINS} 50000")
                client.await("the coins") { Unit.takeIf { abe.inventory.count(WildernessLairFee.COINS) == 50_000L } }
                client.locOp(Vetion.ENTRANCE, 3221, 3787, Vetion.JUMP_DOWN)
                client.awaitDialogue("the fee's menu") { it is DialogueView.Menu && WildernessLairFeeLines.TITLE.key in it.title }
                client.chooseOption(1)
                val arrival = Coord(Vetion.ARRIVAL.x, Vetion.ARRIVAL.z, Vetion.REST_LEVEL)
                client.await("in the rest", timeoutMillis = 15_000) { client.position?.takeIf { it == arrival } }
                assertEquals(0L, abe.inventory.count(WildernessLairFee.COINS), "the fee taken")

                client.locOp(Calvarion.EXIT, 3294, 10190, 1)
                val mouths = EscapeCaves.LAIR_MOUTHS.map { Coord(it.x, it.z, 0) }
                client.await("in the Escape Caves", timeoutMillis = 15_000) { client.position?.takeIf { it in mouths } }
            }
        }
    }

    @Test
    fun `Check-Fee says paid when his discounts leave nothing to pay`(@TempDir dir: Path) {
        server(dir, seed = 1, spawn = false).use { server ->
            server.login("eve").use { client ->
                client.await("the login rebuild") { client.position }
                val eve = server.world.playerNamed("eve")!!
                client.command("varbit ${WildernessLairFee.VARBIT_DISCOUNT} 5")
                client.await("five discounts") { Unit.takeIf { WildernessLairFee.fee(eve) == 0 } }
                client.tele(3220, 3788, 0)
                client.locOp(Vetion.ENTRANCE, 3221, 3787, Vetion.CHECK_FEE)
                client.seen("[${WildernessLairLines.FEE_PAID.key}]")
            }
        }
    }

    @Test
    fun `his lightning hurts, his hounds keep him from harm, and at 0 he comes back enraged`(@TempDir dir: Path) {
        server(dir, seed = 4).use { server ->
            server.login("bo").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(Vetion.SPAWN.x + 1, Vetion.SPAWN.z - 4, Vetion.REST_LEVEL)
                val npc = server.world.npcs.all.first { it.type.id == Vetion.BOSS }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Vet'ion") }
                val fight = client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                client.await("his lightning", timeoutMillis = 30_000) { fight.lines.firstOrNull { "lightning on" in it } }

                // Half his hitpoints: two hounds, and nothing hurts him while one lives.
                client.commandReply("hit 127 ${npc.index}") { it.startsWith("Hit Vet'ion") }
                client.await("his hounds") { fight.lines.firstOrNull { it.endsWith("calls 2 hounds") } }
                assertEquals(2, Vetion.FIGHT.hounds(npc).size)
                client.commandReply("hit 50 ${npc.index}") { it.startsWith("Hit Vet'ion #${npc.index} for 0") }
                for (h in Vetion.FIGHT.hounds(npc).toList()) client.commandReply("hit 30 ${h.index}") { it.startsWith("Hit ") }
                client.await("his hounds dead") { fight.lines.firstOrNull { it.endsWith("his hounds are dead") } }

                // At 0 in his normal form: enraged, at full hitpoints.
                client.commandReply("hit 300 ${npc.index}") { it.startsWith("Hit Vet'ion") }
                client.await("his enraged form") { fight.lines.firstOrNull { it.endsWith("enrages") } }
                assertTrue(!npc.dead, "not dead at 0 in his normal form")
                assertEquals(Vetion.ENRAGED, npc.type.id)
                assertEquals(npc.maxHitpoints, npc.hitpoints)
            }
        }
    }

    @Test
    fun `his enraged form's death counts, leaves his big bones, and takes 10,000 off the fee`(@TempDir dir: Path) {
        server(dir, seed = 3).use { server ->
            server.login("cy").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(Vetion.SPAWN.x + 1, Vetion.SPAWN.z - 4, Vetion.REST_LEVEL)
                val npc = server.world.npcs.all.first { it.type.id == Vetion.BOSS }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Vet'ion") }
                val fight = client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                // A hit past half stops at half, for his hounds: each form's hounds die before it goes below half.
                client.commandReply("hit 254 ${npc.index}") { it.startsWith("Hit Vet'ion") }
                client.killHounds(fight, npc, "his hounds are dead")
                client.commandReply("hit 127 ${npc.index}") { it.startsWith("Hit Vet'ion") }
                client.await("his enraged form") { fight.lines.firstOrNull { it.endsWith("enrages") } }
                client.commandReply("hit 255 ${npc.index}") { it.startsWith("Hit Vet'ion #${npc.index} for 128") }
                client.killHounds(fight, npc, "his hounds are dead", skip = 1)
                client.commandReply("hit 127 ${npc.index}") { it.startsWith("Hit Vet'ion") }
                val cy = server.world.playerNamed("cy")!!
                client.await("the kill count") { Unit.takeIf { cy.vars[Vetion.KILL_COUNT_VARP] == 1 } }
                client.await("his bones") { server.world.groundObjs.visibleAt(cy, npc.x, npc.z, Vetion.REST_LEVEL).firstOrNull { it.id == BIG_BONES } }
                assertEquals(40_000, WildernessLairFee.fee(cy), "10,000 off for the most damage")
            }
        }
    }

    private companion object {
        const val WBR_PERM_INT_1 = 3755
        const val BIG_BONES = 532
    }
}
