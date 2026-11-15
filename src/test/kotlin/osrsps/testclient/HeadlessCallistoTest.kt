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
import osrsps.game.content.Artio
import osrsps.game.content.Callisto
import osrsps.game.content.EscapeCaves
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
 * Wilderness step W4.12 through the headless client: Callisto's Den's way in taking the lairs' fee, and its way out
 * leading into the Escape Caves; his swipe splashing onto a second player beside his target; and a kill counting in his
 * kill count, leaving his big bones, and taking 10,000 off the fee for the top damage dealer. The rl-client script
 * test-runner/examples/callisto.json does the way in and the fight on the real map.
 */
class HeadlessCallistoTest {
    private val boss = NpcType(
        Callisto.BOSS, "Callisto", size = 5, ops = listOf(null, "Attack", null, null, null), combatLevel = 470,
        internalName = "callisto", stats = NpcStats(attack = 350, strength = 300, defence = 225, hitpoints = 1000, ranged = 200, magic = 140),
    )
    private val types = NpcTypes.of(boss)

    /** The cache's `wbr_entrance_fee_paid` and `wbr_entrance_discount`, bits of `wbr_perm_int_1`. */
    private val varTypes = VarTypes(
        6_000,
        mapOf(WildernessLairFee.VARBIT_PAID to VarbitType(WBR_PERM_INT_1, 0, 0), WildernessLairFee.VARBIT_DISCOUNT to VarbitType(WBR_PERM_INT_1, 1, 3)),
        21_000,
    )

    private fun server(dir: Path, seed: Long, spawn: Boolean = true) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val text = NpcDrops::class.java.getResource(NpcDrops.RESOURCE)!!.readText()
        val names = Regex("\"item\": (\\d+), \"name\": \"([^\"]+)\"").findAll(text).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val objTypes = ObjTypes.of(*names.map { (id, name) -> ObjType(id, name, stackable = true) }.toTypedArray())
        val entrance = LocType(Callisto.ENTRANCE, "Cave Entrance", listOf("Enter", "Peek", "Check-Fee", null, null), LocCollisionType(width = 4, length = 5))
        val exit = LocType(Artio.EXIT, "Cave", listOf("Exit", null, null, null, null), LocCollisionType(width = 2, length = 2))
        World(
            crcs, CollisionMap().apply { markLoaded(52, 161); markLoaded(52, 160); markLoaded(51, 60) }, objTypes = objTypes, npcTypes = types,
            random = GameRandom(seed), varTypes = varTypes,
            npcSpawns = if (spawn) listOf(NpcSpawn(boss, Callisto.SPAWN.x, Callisto.SPAWN.z, wander = 0, source = "test")) else emptyList(),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes),
            locTypes = LocTypes(mapOf(Callisto.ENTRANCE to entrance, Artio.EXIT to exit)),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Callisto.ENTRANCE, 10, 3, 3291, 3849, 0))
                add(ZoneLoc(Artio.EXIT, 10, 1, 3358, 10315, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        // The reply, not the position: an aggressive Callisto can knock the player back before the client is seen there.
        commandReply("tele $x $z 0") { it == "Teleported to $x, $z, 0" }
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `the way in takes the fee, and the way out leads into the Escape Caves`(@TempDir dir: Path) {
        server(dir, seed = 1, spawn = false).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                val abe = server.world.playerNamed("abe")!!
                client.tele(3293, 3848)
                client.locOp(Callisto.ENTRANCE, 3291, 3849, Callisto.CHECK_FEE)
                client.seen("[${WildernessLairLines.FEE_DUE.key} fee=50000]")
                client.command("item ${WildernessLairFee.COINS} 50000")
                client.await("the coins") { Unit.takeIf { abe.inventory.count(WildernessLairFee.COINS) == 50_000L } }
                client.locOp(Callisto.ENTRANCE, 3291, 3849, Callisto.ENTER)
                client.awaitDialogue("the fee's menu") { it is DialogueView.Menu && WildernessLairFeeLines.TITLE.key in it.title }
                client.chooseOption(1)
                client.await("in the den", timeoutMillis = 15_000) { client.position?.takeIf { it == Coord(Callisto.ARRIVAL.x, Callisto.ARRIVAL.z, 0) } }
                assertEquals(0L, abe.inventory.count(WildernessLairFee.COINS), "the fee taken")

                client.tele(3359, 10317)
                client.locOp(Artio.EXIT, 3358, 10315, 1)
                val mouths = EscapeCaves.LAIR_MOUTHS.map { Coord(it.x, it.z, 0) }
                client.await("in the Escape Caves", timeoutMillis = 15_000) { client.position?.takeIf { it in mouths } }
                assertTrue(!server.world.escapeCaves.debuffed(abe), "no debuff coming from a lair")
            }
        }
    }

    @Test
    fun `his swipe splashes onto a player beside his target`(@TempDir dir: Path) {
        server(dir, seed = 4).use { server ->
            server.login("bo").use { bo ->
                server.login("di").use { di ->
                    bo.await("the login rebuild") { bo.position }
                    di.await("the login rebuild") { di.position }
                    bo.commandReply("god") { it.startsWith("You can") }
                    di.commandReply("god") { it.startsWith("You can") }
                    bo.command("varp 172 1")
                    di.command("varp 172 1")
                    // Beside his west side, one above the other.
                    bo.tele(Callisto.SPAWN.x - 1, Callisto.SPAWN.z + 2)
                    di.tele(Callisto.SPAWN.x - 1, Callisto.SPAWN.z + 3)
                    val npc = server.world.npcs.all.first { it.type.id == Callisto.BOSS }
                    bo.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Callisto") }
                    val fight = bo.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                    // His knockback throws bo off; di keeps stepping back beside bo till a swipe catches both.
                    fun both() = fight.lines.windowed(2).firstOrNull { (a, b) ->
                        "swipes at" in a && "swipes at" in b && a.substringBefore(" fight") == b.substringBefore(" fight")
                    }
                    var caught: List<String>? = null
                    repeat(60) {
                        if (caught != null) return@repeat
                        val at = server.world.playerNamed("bo")!!
                        di.command("tele ${at.x} ${at.z + 1} 0")
                        bo.awaitTicks(6)
                        caught = both()
                    }
                    assertTrue(caught != null, fight.lines.joinToString("\n"))
                }
            }
        }
    }

    @Test
    fun `a kill counts in his kill count, leaves his big bones, and takes 10,000 off the fee for the top damage`(@TempDir dir: Path) {
        server(dir, seed = 3).use { server ->
            server.login("cy").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(Callisto.SPAWN.x + 2, Callisto.SPAWN.z - 2)
                val npc = server.world.npcs.all.first { it.type.id == Callisto.BOSS }
                client.commandReply("hit 1000 ${npc.index}") { it.startsWith("Hit Callisto") }
                val cy = server.world.playerNamed("cy")!!
                client.await("the kill count") { Unit.takeIf { cy.vars[Callisto.KILL_COUNT_VARP] == 1 } }
                client.await("his bones") { server.world.groundObjs.visibleAt(cy, npc.x, npc.z, 0).firstOrNull { it.id == BIG_BONES } }
                assertEquals(40_000, WildernessLairFee.fee(cy), "10,000 off for the most damage")
            }
        }
    }

    private companion object {
        const val WBR_PERM_INT_1 = 3755
        const val BIG_BONES = 532
    }
}
