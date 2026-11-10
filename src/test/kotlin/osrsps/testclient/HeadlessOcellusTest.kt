package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.EquipmentTab
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.Deaths
import osrsps.game.combat.NpcCombat
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.raid.ArchiveRaids
import osrsps.game.raid.ArchiveRoom
import osrsps.game.raid.ArchiveRun
import osrsps.game.raid.Ocellus
import osrsps.game.raid.PartyScaling
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Ocellus's first phase (raid step 24a) through the headless client: his arena on the vault's upper floor, the hooded
 * figure readying the party up and his fight beginning once everyone standing is in and ready, or 2 minutes after the
 * first ready; his walk to a corner with Protect from Melee up and a shot every five ticks; the seven cracks at a
 * corner, uncaught ones charging him and a caught one healing the player's chip damage; his line and explosion; his walk
 * on to the next corner anticlockwise; his move to the middle at two thirds and a quarter, its four sides of cracks, his
 * line and his charge expended (raid step 24b); the hold at the end of his blue bar, his line and his transformation;
 * and his second phase (raid step 25a): the portals on the arms of the cross, the Blade passing its damage to his bar,
 * the quadrants' turns and their volleys, and, until step 26, his death as his third phase would begin, doing the room.
 * The rl-client script
 * test-runner/examples/archive-ocellus.json fights him in the real client.
 */
class HeadlessOcellusTest {
    /**
     * The figure as the 242 cache has it: a nameless multi-NPC showing the "Hooded Figure" (Talk-to) to a player who
     * hasn't met him and "Ocellus" (Talk-to, Proceed) to one who has, by varbit [MET_VARBIT]; an op names the shown type.
     */
    private val figure = NpcType(
        Ocellus.INTRO, size = 3, ops = listOf("Talk-to", null, "Proceed", null, null), internalName = "tfa_ocellus_intro",
        multiVarbit = MET_VARBIT, multiNpcs = intArrayOf(Ocellus.INTRO_NOT_MET, Ocellus.INTRO_MET, Ocellus.INTRO_NOT_MET),
    )
    private val notMet = NpcType(Ocellus.INTRO_NOT_MET, "Hooded Figure", size = 3, ops = listOf("Talk-to", null, null, null, null), internalName = "tfa_ocellus_intro_not_met")
    private val met = NpcType(Ocellus.INTRO_MET, "Ocellus", size = 3, ops = listOf("Talk-to", null, "Proceed", null, null), internalName = "tfa_ocellus_intro_met")
    private val first = NpcType(
        Ocellus.P1, "Ocellus", size = 3, ops = listOf(null, "Attack", null, null, null), internalName = "tfa_ocellus_p1",
        stats = NpcStats(attack = 1, defence = 215, strength = 1, hitpoints = 2500, ranged = 250, magic = 350),
    )
    /** His second phase, nameless and with no options, and the Blade beside him, as the 242 cache has them. */
    private val second = NpcType(
        Ocellus.P2, size = 3, internalName = "tfa_ocellus_p2",
        stats = NpcStats(attack = 325, defence = 250, strength = 250, hitpoints = 5250, ranged = 300, magic = 300),
    )
    private val blade = NpcType(
        Ocellus.BLADE, "<col=00ffff>The Blade</col>", size = 3, ops = listOf(null, "Attack", null, null, null), internalName = "tfa_ocellus_p2_sword_hitbox",
        stats = NpcStats(attack = 1, defence = 250, strength = 1, hitpoints = 1000, ranged = 1, magic = 220),
    )
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))
    private val types = NpcTypes.of(figure, notMet, met, first, second, blade, grave)

    private val portal = LocCollisionType(width = 2, length = 1, interactType = 1, blocksProjectile = false)
    private val locTypes = LocTypes(
        mapOf(
            ArchiveRaids.ARCHIVE_DOOR to LocType(ArchiveRaids.ARCHIVE_DOOR, "Archive Door", ops = listOf("Enter", null, null, null, null), collision = LocCollisionType(width = 9, length = 1)),
            Ocellus.LOC_PORTAL to LocType(Ocellus.LOC_PORTAL, "Portal", collision = portal),
            Ocellus.LOC_PORTAL_ACTIVE to LocType(Ocellus.LOC_PORTAL_ACTIVE, "Portal", collision = portal),
        ),
    )
    private val staticLocs = StaticLocs().apply { add(ZoneLoc(ArchiveRaids.ARCHIVE_DOOR, LocShape.CENTREPIECE_STRAIGHT, 0, 4123, 5562, 0)) }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                for (x in 63..65) for (z in 86..88) markLoaded(x, z)
                markLoaded(3222 shr 6, 3218 shr 6)
            },
            locTypes = locTypes, staticLocs = staticLocs,
            npcTypes = types, npcCombat = NpcCombat.load(types, SeqNames.NONE), partyScaling = PartyScaling.load(types),
            varTypes = VarTypes(6_000, mapOf(MET_VARBIT to VarbitType(MET_VARP, 0, 0))),
            encounterDefs = listOfNotNull(Ocellus.def(types)), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.fight(): String = commandReply("raid") { it.startsWith("ocellus #") || it.startsWith("You're not") }

    private fun party(clients: List<Pair<HeadlessClient, String>>) {
        val (leader, leaderName) = clients.first()
        for ((client, name) in clients.drop(1)) {
            leader.commandReply("raidparty invite $name") { it.startsWith("Invited") }
            client.commandReply("raidparty accept $leaderName") { it.startsWith("You joined") }
        }
    }

    private fun HeadlessClient.intoRun(server: TestServer, username: String): ArchiveRun {
        await("the login rebuild") { position }
        commandReply("god on") { it.startsWith("You can't be hurt") }
        tele(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z)
        locOp(ArchiveRaids.ARCHIVE_DOOR, 4123, 5562, ArchiveRaids.ENTER)
        await("into the instance") { position?.takeIf { server.world.instances.inDynamicArea(it.x, it.z) } }
        return assertNotNull(server.world.parties.of(server.world.playerNamed(username)!!)?.let(server.world.archiveRaids::of))
    }

    /** Through the four rooms before his, each done as it begins (none of their bosses is in this world). */
    private fun HeadlessClient.throughTheRest(run: ArchiveRun) {
        for (room in listOf(ArchiveRoom.GUARDIAN, ArchiveRoom.BORK, ArchiveRoom.DREADNOTH, ArchiveRoom.XARTH)) {
            commandReply("raid tp ${room.name.lowercase()}") { it.startsWith("To ") }
            await("${room.boss}'s room done") { room.takeIf { it in run.cleared } }
        }
    }

    /** Into his room by `::raid tp`: on his arena's level, where a player comes in. */
    private fun HeadlessClient.toOcellus(server: TestServer, run: ArchiveRun) {
        commandReply("raid tp ocellus") { it.startsWith("To ") }
        val at = server.world.archiveRaids.tile(run, ArchiveRoom.OCELLUS.entryX, ArchiveRoom.OCELLUS.entryZ)
        await("in his room, upstairs") { position?.takeIf { it == Coord(at.x, at.z, ArchiveRoom.OCELLUS.level) } }
        // The figure stands ~32 tiles north of the landing, out of the client's view: walk up to him, as a player does.
        val hooded = run.ocellusFigure ?: return
        tele(hooded.x, hooded.z - 2, ArchiveRoom.OCELLUS.level)
        await("by the hooded figure") { position?.takeIf { it == Coord(hooded.x, hooded.z - 2, ArchiveRoom.OCELLUS.level) } }
    }

    private fun lines(run: ArchiveRun): List<String> = run.fights[ArchiveRoom.OCELLUS]?.lines?.toList().orEmpty()

    private fun HeadlessClient.awaitLine(run: ArchiveRun, what: String, timeoutMillis: Long = 10_000, predicate: (String) -> Boolean): String =
        try {
            await(what, timeoutMillis) { lines(run).lastOrNull(predicate) }
        } catch (e: AssertionError) {
            throw AssertionError("${e.message}; the fight's last lines: ${lines(run).takeLast(12)}", e)
        }

    private fun tickOf(line: String): Long = line.substringBefore(' ').toLong()

    private fun boss(run: ArchiveRun): Npc = assertNotNull(run.fights[ArchiveRoom.OCELLUS]).boss

    @Test
    fun `his corners go anticlockwise round the middle, and a corner's seven cracks run out from his side nearer it`() {
        val middle = Ocellus.middle(Ocellus.FIGURE_X, Ocellus.FIGURE_Z)
        // The capture notes' tiles.
        assertEquals(Tile(4126, 5672), middle)
        assertEquals(listOf(Tile(4132, 5666), Tile(4132, 5678), Tile(4120, 5678), Tile(4120, 5666)), (0..3).map { Ocellus.corner(middle, it) })
        // At the south-east corner, on 4132..4134 x 5666..5668: the middle is north-west of him.
        val spots = Ocellus.spots(4132, 5666, middle.x, middle.z)
        assertEquals(7, spots.size)
        assertTrue(spots.all { it.size == Ocellus.TENDRIL_LENGTH })
        // West along his two northern rows, from the tile beside him.
        assertEquals((0 until 8).map { Tile(4131 - it, 5667) }, spots[0])
        assertEquals((0 until 8).map { Tile(4131 - it, 5668) }, spots[1])
        // North along his two western columns.
        assertEquals((0 until 8).map { Tile(4132, 5669 + it) }, spots[2])
        assertEquals((0 until 8).map { Tile(4133, 5669 + it) }, spots[3])
        // North-west from his inner corner (4132,5668): off it, and from either tile beside that.
        assertEquals((0 until 8).map { Tile(4131 - it, 5669 + it) }, spots[4])
        assertEquals((0 until 8).map { Tile(4131 - it, 5668 + it) }, spots[5])
        assertEquals((0 until 8).map { Tile(4132 - it, 5669 + it) }, spots[6])
        // None of them on him.
        assertTrue(spots.flatten().none { it.x in 4132..4134 && it.z in 5666..5668 })
        // At the north-west corner (4120..4122 x 5678..5680) they run south and east.
        val nw = Ocellus.spots(4120, 5678, middle.x, middle.z)
        assertEquals(Tile(4123, 5678), nw[0].first())
        assertEquals(Tile(4121, 5677), nw[2].first())
        assertEquals(Tile(4123, 5677), nw[4].first())
    }

    @Test
    fun `at the middle each side's two cracks run diagonally out from the ends of that side, clockwise first`() {
        val middle = Ocellus.middle(Ocellus.FIGURE_X, Ocellus.FIGURE_Z)
        // On 4126..4128 x 5672..5674. North: from his north-west corner, then his north-east.
        val north = Ocellus.sideTendrils(middle.x, middle.z, 0)
        assertEquals((1..6).map { Tile(4126 - it, 5674 + it) }, north[0])
        assertEquals((1..6).map { Tile(4128 + it, 5674 + it) }, north[1])
        // East: north-east, then south-east; south: south-east, then south-west; west: south-west, then north-west.
        val east = Ocellus.sideTendrils(middle.x, middle.z, 1)
        assertEquals(north[1], east[0])
        assertEquals((1..6).map { Tile(4128 + it, 5672 - it) }, east[1])
        val south = Ocellus.sideTendrils(middle.x, middle.z, 2)
        assertEquals(east[1], south[0])
        assertEquals((1..6).map { Tile(4126 - it, 5672 - it) }, south[1])
        val west = Ocellus.sideTendrils(middle.x, middle.z, 3)
        assertEquals(listOf(south[1], north[0]), west)
        assertTrue((0..3).flatMap { Ocellus.sideTendrils(middle.x, middle.z, it) }.all { it.size == Ocellus.SIDE_LENGTH })
    }

    @Test
    fun `at two thirds he goes to the middle, lays four sides of cracks, expends his charge on her and walks on, and again at a quarter`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.throughTheRest(run)
                alice.toOcellus(server, run)
                alice.npcOp(assertNotNull(run.ocellusFigure).index, 1)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ocellus #") } }
                val boss = boss(run)
                // Back to the landing, off every crack, so each charges him.
                val landing = server.world.archiveRaids.tile(run, ArchiveRoom.OCELLUS.entryX, ArchiveRoom.OCELLUS.entryZ)
                alice.tele(landing.x, landing.z, ArchiveRoom.OCELLUS.level)
                alice.keepAlive()
                // To two thirds of 2,500 as he walks: whatever he is doing, he goes.
                alice.commandReply("hit 834 ${boss.index}") { it.startsWith("Hit ") }
                val went = alice.awaitLine(run, "to the middle") { it.contains("shield: to the middle (1)") }
                val start = tickOf(went)
                val firstSide = Ocellus.middles(boss).second
                assertFalse(boss.protectsFromMelee)
                val inAt = alice.awaitLine(run, "in at the middle") { it.endsWith("shield: in at the middle") }
                assertEquals(start + Ocellus.MIDDLE_IN, tickOf(inAt))
                assertEquals(Ocellus.middle(boss.spawn.x, boss.spawn.z), Tile(boss.x, boss.z))
                assertEquals(Ocellus.Doing.MIDDLE, Ocellus.doing(boss)!!.first)
                alice.keepAlive()
                val onward = alice.awaitLine(run, "on from the middle") { it.contains("shield: on from the middle") }
                val sides = lines(run).filter { it.contains("shield: side ") }
                val first = start + Ocellus.MIDDLE_IN + Ocellus.FIRST_SIDE
                // Four sides clockwise from the first, eight ticks apart, each's second crack two ticks after its first.
                assertEquals((0 until 4).flatMap { listOf(first + 8 * it, first + 8 * it + 2) }, sides.map(::tickOf))
                val names = listOf("north", "east", "south", "west")
                assertEquals((0 until 4).flatMap { i -> listOf(1, 2).map { "${names[(firstSide + i) % 4]} crack $it" } }, sides.map { it.substringAfter("side ").substringBefore(" on ") })
                // An explosion six ticks after each side begins.
                val explosions = lines(run).filter { it.endsWith("shield: explodes") && tickOf(it) > start }
                assertEquals((0 until 4).map { first + 8 * it + Ocellus.SIDE_EXPLODES }, explosions.map(::tickOf))
                // Each crack flowed back four ticks after it was laid, uncaught: 16 charge on the 0 he had, so his line is
                // the hard one, eight ticks after the last side, and it lands on her two ticks later as the charge empties.
                val charged = lines(run).filter { it.contains("shield: uncaught: charge ") && tickOf(it) > start }
                assertEquals(sides.map { tickOf(it) + Ocellus.SIDE_FLOWS }, charged.map(::tickOf))
                val lastSide = first + 8 * 3
                val line = lines(run).single { it.contains("shield: annihilation at charge ") || it.contains("shield: death awaits at charge ") }
                assertEquals(lastSide + Ocellus.MIDDLE_LINE, tickOf(line))
                val charge = line.substringAfter("at charge ").toInt()
                assertTrue(charge >= 16, line)
                assertTrue(line.contains("annihilation"), line)
                val expended = lines(run).single { it.contains("shield: expends charge ") }
                assertEquals(tickOf(line) + Ocellus.EXPEND_AFTER_LINE, tickOf(expended))
                assertTrue(lines(run).any { it.contains("shield: charge hits alice for ") && tickOf(it) == tickOf(expended) })
                assertEquals(tickOf(expended) + Ocellus.WALK_AFTER_EXPEND, tickOf(onward))
                assertEquals(Ocellus.Doing.WALKING, Ocellus.doing(boss)!!.first)
                assertTrue(boss.protectsFromMelee)
                assertTrue(Ocellus.charge(boss) <= Ocellus.CHARGE_PER, "emptied, a crack or so since")
                // No shots while he was there.
                assertTrue(lines(run).none { it.contains("ball at") && tickOf(it) in start..tickOf(onward) })
                // To a quarter: the second time.
                alice.commandReply("hit 1041 ${boss.index}") { it.startsWith("Hit ") }
                alice.awaitLine(run, "to the middle again") { it.contains("shield: to the middle (2)") }
                assertEquals(2, Ocellus.middles(boss).first)
            }
        }
    }

    @Test
    fun `his arena is upstairs, he waits as the hooded figure until she readies, then walks his corners with Protect from Melee up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                val world = server.world
                val hooded = assertNotNull(run.ocellusFigure, "the hooded figure, there from the run's start")
                assertEquals(world.archiveRaids.tile(run, Ocellus.FIGURE_X, Ocellus.FIGURE_Z), Tile(hooded.x, hooded.z))
                assertEquals(ArchiveRoom.OCELLUS.level, hooded.level)
                // Before the fourth room is done, readying does nothing.
                alice.tele(hooded.x, hooded.z - 2, ArchiveRoom.OCELLUS.level)
                alice.npcOp(hooded.index, 1)
                alice.awaitTicks(3)
                assertTrue(run.ready.isEmpty())
                alice.throughTheRest(run)
                alice.toOcellus(server, run)
                // Walking in starts nothing: he waits for the party to ready up.
                alice.awaitTicks(3)
                assertFalse(ArchiveRoom.OCELLUS in run.started)
                val from = alice.messages().size
                alice.npcOp(hooded.index, 1)
                // Both in the same tick: the count, then the rule.
                val said = alice.await("the ready count and rule") {
                    alice.messages().drop(from).map { it.text }.filter { it.startsWith("[tfa_ocellus.ready") }.takeIf { it.size >= 2 }
                }
                assertEquals(listOf("[tfa_ocellus.ready ready=1 total=1]", "[tfa_ocellus.ready_wait]"), said)
                // Alone, she's everyone: it begins at once, the figure gone and his first phase where it stood.
                val begun = alice.await("his fight") { alice.fight().takeIf { it.startsWith("ocellus #") } }
                assertTrue(begun.contains("Ocellus 2500/2500 hp, charge 0/96"), begun)
                assertNull(run.ocellusFigure)
                assertTrue(world.npcs.all.none { it.type.id == Ocellus.INTRO })
                val boss = boss(run)
                assertEquals(Tile(hooded.x, hooded.z), Tile(boss.spawn.x, boss.spawn.z))
                assertEquals(ArchiveRoom.OCELLUS.level, boss.level)
                assertTrue(lines(run).any { it.endsWith("shield: Protect from Melee up") }, lines(run).toString())
                assertTrue(boss.protectsFromMelee)
                // Walking: a shot at her five ticks in, and none sooner.
                alice.awaitLine(run, "his first shot") { it.contains("ball at alice") }
                assertEquals(Ocellus.SHOT_EVERY.toLong(), tickOf(lines(run).first { it.contains("ball at alice") }))
                // Then back to the landing, off his cracks, so none is caught.
                val landing = world.archiveRaids.tile(run, ArchiveRoom.OCELLUS.entryX, ArchiveRoom.OCELLUS.entryZ)
                alice.tele(landing.x, landing.z, ArchiveRoom.OCELLUS.level)
                // At his corner: the prayer down, seven cracks two ticks apart from the tick after, his line three after
                // the last, the explosion three after that, and on to the next corner anticlockwise three after it.
                val arrived = alice.awaitLine(run, "at his corner") { it.contains("shield: at the ") }
                val corner = Ocellus.doing(boss)!!.second
                assertEquals(Ocellus.corner(Ocellus.middle(boss.spawn.x, boss.spawn.z), corner), Tile(boss.x, boss.z))
                assertFalse(boss.protectsFromMelee)
                val onward = alice.awaitLine(run, "on to the next corner") { it.contains("shield: on to the ") }
                val at = tickOf(arrived)
                val cracks = lines(run).filter { it.contains("shield: crack ") }
                assertEquals((0 until Ocellus.TENDRILS).map { at + 1 + Ocellus.TENDRIL_GAP * it }, cracks.map(::tickOf))
                assertEquals((1..Ocellus.TENDRILS).toSet(), cracks.map { it.substringAfter("crack ").substringBefore(' ').toInt() }.toSet(), "each spot once")
                val lastCrack = at + 1 + Ocellus.TENDRIL_GAP * (Ocellus.TENDRILS - 1)
                assertEquals(lastCrack + Ocellus.CORNER_LINE, tickOf(lines(run).first { it.contains("shield: corner line ") && tickOf(it) > at }))
                val explodes = lines(run).first { it.endsWith("shield: explodes") && tickOf(it) > at }
                assertEquals(lastCrack + Ocellus.CORNER_LINE + Ocellus.EXPLODE_AFTER_LINE, tickOf(explodes))
                assertEquals(tickOf(explodes) + Ocellus.WALK_AFTER_EXPLODE, tickOf(onward))
                assertEquals((corner + 1) % 4, Ocellus.doing(boss)!!.second)
                assertTrue(boss.protectsFromMelee)
                // Nobody on any of them: each charged him 2.
                val charged = lines(run).filter { it.contains("shield: uncaught: charge ") }
                assertEquals((1..Ocellus.TENDRILS).map { "${it * Ocellus.CHARGE_PER}" }, charged.map { it.substringAfter("charge ") })
                assertEquals(Ocellus.TENDRILS * Ocellus.CHARGE_PER, Ocellus.charge(boss))
                assertEquals((0 until Ocellus.TENDRILS).map { at + 1 + Ocellus.TENDRIL_GAP * it + Ocellus.TENDRIL_FLOWS }, charged.map(::tickOf))
                // Melee does nothing through the prayer: her swing rolls 0 with sure max hits.
                alice.commandReply("setlevel strength 99") { it.contains("set to level 99") }
                alice.commandReply("setlevel attack 99") { it.contains("set to level 99") }
                alice.commandReply("sure max") { it.startsWith("Your attacks can't miss and") }
                alice.tele(boss.x - 1, boss.z, ArchiveRoom.OCELLUS.level)
                alice.npcOp(boss.index, 2)
                alice.awaitTicks(4)
                if (boss.protectsFromMelee) assertEquals(2500, boss.hitpoints, "nothing through Protect from Melee")
                // His blue bar run out, he holds at it: nothing more hurts him, nor does he go to the middle for the two
                // thresholds he passed on the way.
                alice.commandReply("hit 2500 ${boss.index}") { it.startsWith("Hit ") }
                val held = alice.awaitLine(run, "held") { it.endsWith("held: phase starts") }
                assertFalse(boss.dead)
                assertEquals(0, boss.hitpoints)
                assertTrue(alice.fight().contains("held"))
                assertTrue(lines(run).none { it.contains("to the middle (") })
                // His line three ticks in, there teleporting to the middle; his transformation three after it.
                val disappear = alice.awaitLine(run, "his line") { it.endsWith("held: will disappear") }
                assertEquals(tickOf(held) + Ocellus.HOLD_LINE, tickOf(disappear))
                assertEquals(Ocellus.middle(boss.spawn.x, boss.spawn.z), Tile(boss.x, boss.z))
                val transformation = alice.awaitLine(run, "his transformation") { it.endsWith("held: transformation") }
                assertEquals(tickOf(disappear) + Ocellus.TRANSFORM_ANIM_AFTER, tickOf(transformation))
                assertFalse(boss.dead)
                // His second phase ten ticks after his line, on the remainder of his hitpoints.
                val second = alice.awaitLine(run, "his second phase") { it.endsWith("second: phase starts") }
                assertEquals(tickOf(disappear) + Ocellus.SECOND_PHASE_AFTER, tickOf(second))
                assertEquals(Ocellus.P2, boss.type.id)
                assertEquals(5250, boss.hitpoints)
                assertFalse(boss.dead)
            }
        }
    }

    @Test
    fun `his portals stand on the arms of the cross, every two tiles`() {
        val centre = Ocellus.centre(Ocellus.FIGURE_X, Ocellus.FIGURE_Z)
        assertEquals(Tile(4127, 5673), centre)
        // The capture notes' tiles: E 4128-4138 and W 4115-4125 at y 5673, N 5674-5684 and S 5661-5671 at x 4127.
        assertEquals((0..5).map { Tile(4128 + 2 * it, 5673) }, (0..5).map { Ocellus.portalTiles(centre, Ocellus.Arm.E, it).first() })
        assertEquals((0..5).map { Tile(4125 - 2 * it, 5673) }, (0..5).map { Ocellus.portalTiles(centre, Ocellus.Arm.W, it).first() })
        assertEquals((0..5).map { Tile(4127, 5674 + 2 * it) }, (0..5).map { Ocellus.portalTiles(centre, Ocellus.Arm.N, it).first() })
        assertEquals((0..5).map { Tile(4127, 5671 - 2 * it) }, (0..5).map { Ocellus.portalTiles(centre, Ocellus.Arm.S, it).first() })
        // Every tile of each arm but the middle's, once.
        val all = Ocellus.Arm.entries.flatMap { arm -> (0..5).flatMap { Ocellus.portalTiles(centre, arm, it) } }
        assertEquals(48, all.toSet().size)
        assertTrue(all.none { it == centre })
        assertTrue(all.all { Ocellus.quadrant(centre, it.x, it.z) == null })
        // The north arm's second portal (5676-5677) shoots west across the north-west quadrant, its strip 12 tiles long.
        assertEquals(listOf(Tile(4126, 5676), Tile(4126, 5677)), Ocellus.strip(centre, Ocellus.Arm.N, 1, Ocellus.Quadrant.NW, 1))
        assertEquals(listOf(Tile(4115, 5676), Tile(4115, 5677)), Ocellus.strip(centre, Ocellus.Arm.N, 1, Ocellus.Quadrant.NW, 12))
        // The east arm's first (4128-4129) south across the south-east one.
        assertEquals(listOf(Tile(4128, 5672), Tile(4129, 5672)), Ocellus.strip(centre, Ocellus.Arm.E, 0, Ocellus.Quadrant.SE, 1))
        // Each quadrant's strips stay in it.
        for (quadrant in Ocellus.Quadrant.entries) {
            val (ns, ew) = Ocellus.arms(quadrant)
            val tiles = listOf(ns, ew).flatMap { arm -> (0..5).flatMap { i -> (1..Ocellus.STRIP_LENGTH).flatMap { Ocellus.strip(centre, arm, i, quadrant, it) } } }
            assertTrue(tiles.all { Ocellus.quadrant(centre, it.x, it.z) == quadrant }, quadrant.name)
            assertEquals(144, tiles.toSet().size, "${quadrant.name}: 12 by 12, every tile covered by each arm's strips")
        }
    }

    @Test
    fun `his second phase puts up the portals and the Blade, whose damage reaches his bar, and the quadrants' volleys fire`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
                alice.commandReply("setlevel hitpoints 99") { it.contains("set to level 99") }
                alice.commandReply("heal") { it.startsWith("Healed") }
                alice.throughTheRest(run)
                alice.toOcellus(server, run)
                alice.npcOp(assertNotNull(run.ocellusFigure).index, 1)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ocellus #") } }
                val boss = boss(run)
                val fight = assertNotNull(run.fights[ArchiveRoom.OCELLUS])
                val world = server.world
                val centre = Ocellus.centre(boss.spawn.x, boss.spawn.z)
                // His blue bar out at once; then into the south-east quadrant, on the south arm's second strip (two tiles
                // south of the middle row, three east of the cross): the first volley's safe crossing there.
                alice.commandReply("hit 2500 ${boss.index}") { it.startsWith("Hit ") }
                alice.tele(centre.x + 3, centre.z - 3, ArchiveRoom.OCELLUS.level)
                alice.commandReply("god off") { it.startsWith("You can be hurt") }
                val second = tickOf(alice.awaitLine(run, "his second phase") { it.endsWith("second: phase starts") })
                val portals = alice.awaitLine(run, "the portals") { it.endsWith("second: portals stand") }
                assertEquals(second + Ocellus.PORTALS_AT, tickOf(portals))
                assertEquals(24, Ocellus.portals(boss).size)
                assertTrue(Ocellus.portals(boss).values.none { it }, "all orange")
                val east = Ocellus.portalTiles(centre, Ocellus.Arm.E, 0).first()
                val placedEast = assertNotNull(world.locs[east.x, east.z, boss.level, 2])
                assertEquals(Ocellus.LOC_PORTAL to 0, placedEast.id to placedEast.angle)
                val north = Ocellus.portalTiles(centre, Ocellus.Arm.N, 0).first()
                assertEquals(1, assertNotNull(world.locs[north.x, north.z, boss.level, 2]).angle, "turned along the north arm")
                // Seven ticks in his line, the Blade on his tiles, and the quadrant she stands in first.
                val blade = alice.awaitLine(run, "the Blade") { it.contains("second: your time; the Blade; first quadrant ") }
                assertEquals(second + Ocellus.BLADE_AT, tickOf(blade))
                assertTrue(blade.endsWith("SE"), blade)
                val sword = assertNotNull(Ocellus.blade(boss))
                assertEquals(Tile(boss.x, boss.z), Tile(sword.x, sword.z))
                assertTrue(alice.fight().contains("second phase, quadrant SE"), alice.fight())
                // The first volley: all but the south arm's and the east arm's second portals purple, firing four ticks on;
                // she stands where the two orange strips cross, and nothing hits her.
                val volley = alice.awaitLine(run, "the first volley") { it.endsWith("second: volley 1: orange S 1, E 1") }
                assertEquals(tickOf(blade), tickOf(volley))
                val purple = Ocellus.portals(boss).filterValues { it }.keys
                assertEquals((0..5).filter { it != 1 }.flatMap { listOf(Ocellus.Arm.S to it, Ocellus.Arm.E to it) }.toSet(), purple)
                val fired = alice.awaitLine(run, "the first volley firing") { it.endsWith("second: volley 1 fires from 10 portals") }
                assertEquals(tickOf(volley) + Ocellus.VOLLEY_FIRES, tickOf(fired))
                // The second keeps the south arm's fourth orange, so the second's strip, hers, fires: its shot reaches her,
                // three tiles out, the tick after it fires (18 client cycles).
                alice.awaitLine(run, "the second volley") { it.endsWith("second: volley 2: orange S 3, E 1") }
                val hit = alice.awaitLine(run, "the shot hitting her") { it.contains("second: portal shot from ") }
                assertTrue(hit.contains("portal shot from S 1 hits alice for "), hit)
                assertEquals(tickOf(volley) + Ocellus.VOLLEY_GAP + Ocellus.VOLLEY_FIRES + 1, tickOf(hit))
                assertTrue(hit.substringAfter(" for ").toInt() in Ocellus.PORTAL_MIN..Ocellus.PORTAL_MAX, hit)
                assertEquals(1, fight.lines.count { it.contains("portal shot") }, "once, and not in the first volley")
                alice.commandReply("god on") { it.startsWith("You can't be hurt") }
                // 1,000 on the Blade: 430 off his bar, and the Blade whole again.
                alice.commandReply("hit 1000 ${sword.index}") { it.startsWith("Hit ") }
                alice.awaitLine(run, "the Blade's damage") { it.endsWith("second: the Blade takes 1000: 430 to his bar") }
                assertEquals(5250 - 430, boss.hitpoints)
                assertEquals(1000, sword.hitpoints)
                assertFalse(sword.dead)
                // Each quadrant 42 ticks, clockwise; until step 26, he dies 123 ticks in, in the third, and the room is done.
                for (next in listOf("SW", "NW")) {
                    alice.keepAlive()
                    alice.awaitLine(run, "the $next quadrant's turn", 30_000) { it.endsWith("second: quadrant $next") }
                }
                alice.keepAlive()
                alice.await("his room done", 30_000) { ArchiveRoom.OCELLUS.takeIf { it in run.cleared } }
                assertTrue(run.completed)
                assertTrue(boss.dead)
                val turns = fight.lines.filter { it.contains("second: quadrant ") }
                assertEquals(listOf("SE", "SW", "NW"), turns.map { it.substringAfter("quadrant ") })
                assertEquals((0..2).map { second + Ocellus.BLADE_AT + Ocellus.QUADRANT_TICKS * it }, turns.map(::tickOf))
                assertEquals(second + Ocellus.SECOND_PHASE_TICKS, tickOf(fight.lines.single { it.endsWith("second: end of the second phase: stand-in, he dies") }))
                assertEquals(18, fight.lines.count { it.contains("second: volley ") && it.contains(" fires from ") }, "six a quadrant, three quadrants")
                // The portals and the Blade go with the fight.
                assertTrue(world.locs[east.x, east.z, boss.level, 2]?.id != Ocellus.LOC_PORTAL)
                assertTrue(sword.dead || world.npcs[sword.index] !== sword)
            }
        }
    }

    @Test
    fun `standing on a crack as its energy flows heals what she lost since, then nicks her`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
                alice.commandReply("setlevel hitpoints 99") { it.contains("set to level 99") }
                alice.commandReply("heal") { it.startsWith("Healed") }
                alice.throughTheRest(run)
                alice.toOcellus(server, run)
                val hooded = assertNotNull(run.ocellusFigure)
                alice.npcOp(hooded.index, 1)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ocellus #") } }
                // Back to the landing, off his cracks and out of his shots' reach.
                val landing = server.world.archiveRaids.tile(run, ArchiveRoom.OCELLUS.entryX, ArchiveRoom.OCELLUS.entryZ)
                alice.tele(landing.x, landing.z, ArchiveRoom.OCELLUS.level)
                alice.awaitLine(run, "at his corner") { it.contains("shield: at the ") }
                // At the corner he shoots no more, but a shot from his walk can still be in flight from across the arena
                // (up to 12 ticks): it lands under ::god first.
                alice.await("his shots landed") { Unit.takeIf { server.world.meleeCombat.shotsInFlight == 0 } }
                // 30 off her, then onto the far end of the next crack, out of the
                // explosion's reach.
                alice.commandReply("god off") { it.startsWith("You can be hurt") }
                alice.commandReply("hit 30 me") { it.startsWith("Hit ") }
                val player = server.world.playerNamed("alice")!!
                alice.await("her 69") { player.hitpoints.takeIf { it == 69 } }
                val laid = lines(run).size
                val crack = alice.awaitLine(run, "a crack laid") { it.contains("shield: crack ") && lines(run).indexOf(it) >= laid }
                val far = crack.substringAfter(" to ").split(',').map(String::toInt)
                alice.tele(far[0], far[1], ArchiveRoom.OCELLUS.level)
                val caught = alice.awaitLine(run, "her catching it") { it.contains("shield: alice catches a crack") }
                assertEquals(tickOf(crack) + Ocellus.TENDRIL_FLOWS, tickOf(caught))
                assertTrue(caught.contains("healed 30, hit for "), "$caught; the fight: ${lines(run).takeLast(25)}")
                val nick = caught.substringAfter("hit for ").toInt()
                assertTrue(nick in Ocellus.CRACK_MIN..Ocellus.CRACK_MAX, caught)
                alice.commandReply("god on") { it.startsWith("You can't be hurt") }
                assertEquals(99 - nick, player.hitpoints)
                // Caught, it charged him nothing.
                assertFalse(lines(run).any { it.contains("uncaught") && tickOf(it) == tickOf(caught) })
            }
        }
    }

    @Test
    fun `a run opened with no NPC slot free gets the hooded figure once one is`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                // Every free slot taken, by NPCs standing still on one tile far off, before the run opens.
                val npcs = server.world.npcs
                val filler = NpcType(1, "Filler")
                val fillers = generateSequence { npcs.add(NpcSpawn(filler, 1000, 1000, source = "test")) }.toList()
                assertTrue(fillers.size > 1000, "${fillers.size} fillers")
                val run = alice.intoRun(server, "alice")
                alice.awaitTicks(3)
                assertNull(run.ocellusFigure, "no slot for the figure")
                // A slot frees: the figure is put in where it stands, and his room can begin as usual.
                for (npc in fillers) npcs.remove(npc)
                val hooded = alice.await("the figure, once a slot is free") { run.ocellusFigure }
                assertEquals(server.world.archiveRaids.tile(run, Ocellus.FIGURE_X, Ocellus.FIGURE_Z), Tile(hooded.x, hooded.z))
                assertEquals(ArchiveRoom.OCELLUS.level, hooded.level)
                alice.throughTheRest(run)
                alice.toOcellus(server, run)
                alice.npcOp(hooded.index, 1)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ocellus #") } }
            }
        }
    }

    /** Alice and bob in one party and one run, alice through the four rooms before his and into his room. */
    private fun twoIn(server: TestServer, alice: HeadlessClient, bob: HeadlessClient): ArchiveRun {
        val clients = listOf(alice to "alice", bob to "bob")
        for ((client, _) in clients) client.await("the login rebuild") { client.position }
        party(clients)
        val run = alice.intoRun(server, "alice")
        assertEquals(run, bob.intoRun(server, "bob"))
        alice.throughTheRest(run)
        alice.toOcellus(server, run)
        return run
    }

    @Test
    fun `with two in the party the fight waits for both to be in his room and ready`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    val run = twoIn(server, alice, bob)
                    val hooded = assertNotNull(run.ocellusFigure)
                    alice.npcOp(hooded.index, 1)
                    alice.await("her ready") { run.ready.takeIf { it.isNotEmpty() } }
                    // Bob is still in the nexus: nothing begins.
                    alice.awaitTicks(5)
                    assertFalse(ArchiveRoom.OCELLUS in run.started)
                    // In his room but not ready: still nothing.
                    bob.toOcellus(server, run)
                    bob.awaitTicks(3)
                    assertFalse(ArchiveRoom.OCELLUS in run.started)
                    // Bob has met him before: he sees Ocellus, and Proceed readies him.
                    bob.command("varbit $MET_VARBIT 1")
                    bob.awaitTicks(2)
                    bob.npcOp(hooded.index, 3)
                    alice.await("his fight") { alice.fight().takeIf { it.startsWith("ocellus #") } }
                    assertTrue(server.world.tick - run.firstReadyTick < Ocellus.READY_TIMEOUT)
                    // A party of two counts as the Wiki's 2,500, and both are in the fight.
                    assertEquals(2500, boss(run).maxHitpoints)
                    assertEquals(2, assertNotNull(run.fights[ArchiveRoom.OCELLUS]).players.size)
                }
            }
        }
    }

    @Test
    fun `2 minutes after the first ready it begins with whoever is in his room`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    val run = twoIn(server, alice, bob)
                    alice.npcOp(assertNotNull(run.ocellusFigure).index, 1)
                    alice.await("her ready") { run.ready.takeIf { it.isNotEmpty() } }
                    val first = run.firstReadyTick
                    // Long waits need the real client's keep-alives, or the idle timeout ends the connections.
                    repeat((Ocellus.READY_TIMEOUT - 20) / 20) {
                        alice.keepAlive()
                        bob.keepAlive()
                        alice.awaitTicks(20)
                    }
                    assertFalse(ArchiveRoom.OCELLUS in run.started, "not before 2 minutes")
                    alice.await("his fight, 2 minutes on", timeoutMillis = 120_000) { alice.fight().takeIf { it.startsWith("ocellus #") } }
                    assertEquals(first + Ocellus.READY_TIMEOUT, server.world.tick - assertNotNull(run.fights[ArchiveRoom.OCELLUS]).ticks)
                    // Bob, in the nexus, isn't in the fight.
                    assertEquals(1, assertNotNull(run.fights[ArchiveRoom.OCELLUS]).players.size)
                }
            }
        }
    }

    private companion object {
        /** Stand-ins for the cache's met varbit and its varp: only the switch between the two shown types matters here. */
        const val MET_VARBIT = 5_900
        const val MET_VARP = 5_900
    }
}
