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
import osrsps.game.PlayerOps
import osrsps.game.SeqNames
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.PvpInteraction
import osrsps.game.combat.PvpLines
import osrsps.game.content.CustomVars
import osrsps.game.content.RevenantCaves
import osrsps.game.content.RevenantCavesLines
import osrsps.game.content.SinglesPlus
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Wilderness step W5.1 through the headless client: the Revenant Caves' ways in taking the 100,000-coin fee once, the
 * stairs leading out, a death in the caves losing the fee where one outside doesn't, and singles-plus combat letting a
 * player attack someone a monster is fighting there, which single-way combat outside refuses. The rl-client script
 * test-runner/examples/revenant-caves.json does the way in and out on the real map.
 */
class HeadlessRevenantCavesTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", null, null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 99))
    private val types = NpcTypes.of(man)
    private val varTypes = VarTypes(6_000, emptyMap())
    private val vars = CustomVars.after(varTypes)

    private fun server(dir: Path, spawns: List<NpcSpawn> = emptyList()) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val objTypes = ObjTypes.of(ObjType(RevenantCaves.COINS, "Coins", stackable = true))
        val loc = { id: Int, name: String, op: String, w: Int, l: Int -> LocType(id, name, listOf(op, null, null, null, null), LocCollisionType(width = w, length = l)) }
        World(
            crcs,
            CollisionMap().apply { for ((sx, sz) in listOf(48 to 57, 47 to 58, 48 to 59, 49 to 157, 50 to 157, 49 to 158, 50 to 159, 49 to 159)) markLoaded(sx, sz) },
            objTypes = objTypes, npcTypes = types, random = GameRandom(5), varTypes = varTypes, customVars = vars,
            npcSpawns = spawns, npcCombat = NpcCombat.load(types, SeqNames.NONE),
            locTypes = LocTypes(
                mapOf(
                    RevenantCaves.CAVERN_LOW to loc(RevenantCaves.CAVERN_LOW, "Cavern", "Enter", 4, 2),
                    RevenantCaves.CREVICE to loc(RevenantCaves.CREVICE, "Crevice", "Jump-Down", 4, 2),
                    RevenantCaves.STAIRS_LOW to loc(RevenantCaves.STAIRS_LOW, "Stairs", "Climb-up", 1, 2),
                ),
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(RevenantCaves.CAVERN_LOW, 10, 0, 3073, 3654, 0))
                add(ZoneLoc(RevenantCaves.CREVICE, 10, 3, 3067, 3740, 0))
                add(ZoneLoc(RevenantCaves.STAIRS_LOW, 10, 1, 3218, 10058, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.arrive(x: Int, z: Int, what: String) =
        await(what, timeoutMillis = 15_000) { position?.takeIf { it == Coord(x, z, 0) } }

    /** Pays at the level 17 cavern with coins in the backpack and goes in. */
    private fun HeadlessClient.payAndEnter(coins: () -> Long) {
        tele(3074, 3653)
        command("item ${RevenantCaves.COINS} ${RevenantCaves.FEE}")
        await("the coins") { Unit.takeIf { coins() >= RevenantCaves.FEE } }
        locOp(RevenantCaves.CAVERN_LOW, 3073, 3654, RevenantCaves.ENTER)
        awaitDialogue("the fee's menu") { it is DialogueView.Menu && RevenantCavesLines.TITLE.key in it.title }
        chooseOption(1)
        arrive(RevenantCaves.CAVERN_LOW_ARRIVAL.x, RevenantCaves.CAVERN_LOW_ARRIVAL.z, "in the caves")
    }

    @Test
    fun `a way in takes the fee once, the stairs lead out, and the crevice lets a payer back in`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                val abe = server.world.playerNamed("abe")!!
                client.tele(3074, 3653)
                client.locOp(RevenantCaves.CAVERN_LOW, 3073, 3654, RevenantCaves.ENTER)
                client.seen("[${RevenantCavesLines.NO_COINS.key} fee=100000]")
                assertTrue(!RevenantCaves.paid(abe), "nothing paid with no coins")

                client.payAndEnter { abe.inventory.count(RevenantCaves.COINS) }
                assertEquals(0L, abe.inventory.count(RevenantCaves.COINS), "the fee taken")
                assertTrue(RevenantCaves.paid(abe))
                assertTrue(SinglesPlus.at(0, abe.x, abe.z), "the caves are singles-plus")

                client.tele(3217, 10058)
                client.locOp(RevenantCaves.STAIRS_LOW, 3218, 10058, RevenantCaves.CLIMB_UP)
                client.arrive(RevenantCaves.STAIRS_LOW_EXIT.x, RevenantCaves.STAIRS_LOW_EXIT.z, "by the level 17 trapdoor")

                client.tele(3066, 3741)
                client.locOp(RevenantCaves.CREVICE, 3067, 3740, RevenantCaves.ENTER)
                client.arrive(RevenantCaves.CREVICE_ARRIVAL.x, RevenantCaves.CREVICE_ARRIVAL.z, "in the caves, no menu")
                assertTrue(RevenantCaves.paid(abe), "still paid")
            }
        }
    }

    @Test
    fun `a death in the caves loses the fee, and one outside them doesn't`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bo").use { client ->
                client.await("the login rebuild") { client.position }
                val bo = server.world.playerNamed("bo")!!
                client.payAndEnter { bo.inventory.count(RevenantCaves.COINS) }
                client.commandReply("hit 99 me") { true }
                client.await("dead and back", timeoutMillis = 20_000) { Unit.takeIf { !bo.dead && !RevenantCaves.inCaves(bo.x, bo.z) } }
                assertTrue(!RevenantCaves.paid(bo), "the fee lost")

                client.payAndEnter { bo.inventory.count(RevenantCaves.COINS) }
                client.tele(3217, 10058)
                client.locOp(RevenantCaves.STAIRS_LOW, 3218, 10058, RevenantCaves.CLIMB_UP)
                client.arrive(RevenantCaves.STAIRS_LOW_EXIT.x, RevenantCaves.STAIRS_LOW_EXIT.z, "out")
                client.commandReply("hit 99 me") { true }
                client.await("dead and back", timeoutMillis = 20_000) { Unit.takeIf { !bo.dead && bo.x != RevenantCaves.STAIRS_LOW_EXIT.x } }
                assertTrue(RevenantCaves.paid(bo), "a death outside the caves to no player keeps it")
                assertEquals(RevenantCaves.FEE, RevenantCaves.forfeit(bo), "a PvP death gives the killer the fee")
                assertTrue(!RevenantCaves.paid(bo), "and loses it")
            }
        }
    }

    @Test
    fun `singles-plus lets a player attack someone a monster is fighting, where single-way refuses`(@TempDir dir: Path) {
        val spawns = listOf(NpcSpawn(man, 3200, 10060, wander = 0, source = "test"), NpcSpawn(man, 3080, 3700, wander = 0, source = "test"))
        server(dir, spawns).use { server ->
            server.login("cy").use { a ->
                server.login("di").use { b ->
                    for (c in listOf(a, b)) {
                        c.await("the login rebuild") { c.position }
                        c.commandReply("setlevel hitpoints 99") { true }
                        c.command("varp 172 1")
                    }
                    val world = server.world
                    val cy = world.playerNamed("cy")!!
                    val di = world.playerNamed("di")!!
                    fun fight(x: Int, z: Int) {
                        a.tele(x, z)
                        b.tele(x + 2, z)
                        b.await("cy in view") { b.otherPlayers().takeIf { it.isNotEmpty() } }
                        val npc = world.npcs.all.first { kotlin.math.abs(it.x - x) < 5 && kotlin.math.abs(it.z - z) < 5 }
                        a.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Man") }
                        a.await("the man on cy") { Unit.takeIf { cy.attackedBy === npc && npc.target === cy } }
                        // Cy fights it back, a real attack, not just the command's hit.
                        a.npcOp(npc.index, 2)
                        a.await("cy on the man") { Unit.takeIf { cy.attackedNpc === npc } }
                        b.playerOp(a.index, PlayerOps.ATTACK)
                    }

                    // Outside the caves, single-way: refused.
                    fight(3081, 3702)
                    val text = DialogueText.NONE[PvpLines.TARGET_IN_COMBAT]
                    b.await("the refusal") { b.messages().firstOrNull { it.text == text } }

                    // In the caves, singles-plus: di gets at cy, and cy's fight with the man ends, theirs on it and
                    // its on them (Codex review on #1002).
                    fight(3201, 10062)
                    b.await("di on cy") { (di.interaction as? PvpInteraction)?.target?.takeIf { it === cy } }
                    val man = world.npcs.all.first { kotlin.math.abs(it.x - 3200) < 5 && kotlin.math.abs(it.z - 10060) < 5 }
                    b.await("di's first attack") { Unit.takeIf { cy.pjOpponent === di } }
                    a.await("the man gives up") { Unit.takeIf { man.target == null } }
                    assertTrue(cy.interaction !is osrsps.game.combat.MeleeInteraction, "cy no longer on the man")
                    assertEquals(null, cy.attackedNpc)

                    // The other way round: di, fought by the man, attacks cy; the man gives up on di, and di keeps after
                    // cy. First the fight over and both PJ timers run out.
                    di.interaction = null
                    b.await("both PJ timers out", timeoutMillis = 20_000) { Unit.takeIf { world.tick > di.pjUntil && world.tick > cy.pjUntil } }
                    val other = world.npcs.all.first { kotlin.math.abs(it.x - 3200) < 5 && kotlin.math.abs(it.z - 10060) < 5 }
                    b.commandReply("hit 1 ${other.index}") { it.startsWith("Hit Man") }
                    b.await("the man on di") { Unit.takeIf { di.attackedBy === other && other.target === di } }
                    b.npcOp(other.index, 2)
                    b.await("di on the man") { Unit.takeIf { di.attackedNpc === other } }
                    b.playerOp(a.index, PlayerOps.ATTACK)
                    b.await("di's attack on cy") { Unit.takeIf { (di.interaction as? PvpInteraction)?.target === cy && di.pjUntil > world.tick && di.pjOpponent === cy } }
                    b.await("the man gives up on di") { Unit.takeIf { other.target == null } }
                    assertTrue((di.interaction as? PvpInteraction)?.target === cy, "di still on cy")
                }
            }
        }
    }
}
