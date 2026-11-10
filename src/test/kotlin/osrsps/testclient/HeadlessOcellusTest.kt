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
import osrsps.game.Skill
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.Deaths
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.SureHit
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
 * the quadrants' turns and their volleys, the rest of a quadrant's turn (step 25b); and his third phase (step 26a): the
 * cross falling to stepping stones, his quadrant and tank, a jump over an arm, and the quadrants falling, until step 27
 * his death as his final stand would begin, doing the room.
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
    /** His third phase, with Attack, as the 242 cache has it. */
    private val third = NpcType(
        Ocellus.P3, "Ocellus", size = 3, ops = listOf(null, "Attack", null, null, null), internalName = "tfa_ocellus_p3",
        stats = NpcStats(attack = 325, defence = 250, strength = 250, hitpoints = 5250, ranged = 300, magic = 300),
    )
    /** The Void Vine, as the 242 cache has it: 44 hitpoints and Attack. */
    private val vine = NpcType(
        Ocellus.VINE, "<col=00ffff>Void Vine</col>", ops = listOf(null, "Attack", null, null, null), internalName = "tfa_ocellus_p3_void_vine",
        stats = NpcStats(attack = 1, defence = 1, strength = 1, hitpoints = 44, ranged = 1, magic = 1),
    )
    /** His void flares, as the 242 cache has them: 44 hitpoints, Defence 175, Magic 300 and Attack. */
    private val flares = Ocellus.Flare.entries.map { kind ->
        NpcType(
            kind.id, "Void ${kind.name.lowercase().replaceFirstChar { it.uppercase() }}", ops = listOf(null, "Attack", null, null, null),
            internalName = listOf("tfa_ocellus_p3_bombportal_basic", "tfa_ocellus_p3_bombportal_heal", "tfa_ocellus_p3_bombportal_buff")[kind.ordinal],
            stats = NpcStats(attack = 1, defence = 175, strength = 1, hitpoints = 44, ranged = 1, magic = 300),
        )
    }
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))
    private val types = NpcTypes.of(figure, notMet, met, first, second, blade, third, vine, grave, *flares.toTypedArray())

    private val portal = LocCollisionType(width = 2, length = 1, interactType = 1, blocksProjectile = false)
    private val locTypes = LocTypes(
        mapOf(
            ArchiveRaids.ARCHIVE_DOOR to LocType(ArchiveRaids.ARCHIVE_DOOR, "Archive Door", ops = listOf("Enter", null, null, null, null), collision = LocCollisionType(width = 9, length = 1)),
            Ocellus.LOC_PORTAL to LocType(Ocellus.LOC_PORTAL, "Portal", collision = portal),
            Ocellus.LOC_PORTAL_ACTIVE to LocType(Ocellus.LOC_PORTAL_ACTIVE, "Portal", collision = portal),
            Ocellus.LOC_VOID to LocType(Ocellus.LOC_VOID, collision = LocCollisionType(interactType = 1, blocksProjectile = false)),
        ) + Ocellus.LOC_ARMS.mapIndexed { i, id ->
            // The 242 cache's: the north and south arms 3 by 11, the east and west 11 by 3; the falling ones block shots too.
            id to LocType(id, collision = if (i % 2 == 0) LocCollisionType(3, 11, 1, true) else LocCollisionType(11, 3, 1, true))
        } + Ocellus.LOC_ARMS_EMPTY.mapIndexed { i, id ->
            id to LocType(id, collision = if (i % 2 == 0) LocCollisionType(3, 11, 1, false) else LocCollisionType(11, 3, 1, false))
        } + (listOf(Ocellus.LOC_BOMB, Ocellus.LOC_DEBRIS) + Ocellus.LOC_INSOLENCE).map { id ->
            // The 242 cache's void, debris and cracks: walkable.
            id to LocType(id, "Void", collision = LocCollisionType(interactType = 0, blocksProjectile = false))
        } + Ocellus.GAPS.map { id ->
            id to LocType(id, "Gap", ops = listOf("Jump", null, null, null, null), collision = LocCollisionType(interactType = 1, blocksProjectile = false))
        } + Ocellus.Censer.entries.flatMap { censer ->
            // The 242 cache's: 1 by 1, blocking shots, Interact while lit; the inactive form without it.
            listOf(
                censer.id to LocType(censer.id, "Censer", ops = listOf("Interact", null, null, null, null), collision = LocCollisionType(interactType = 1, blocksProjectile = true)),
                censer.inactive to LocType(censer.inactive, "Censer", collision = LocCollisionType(interactType = 1, blocksProjectile = true)),
            )
        },
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
        // The floor portal's way on: the same spot in the next quadrant clockwise, the south-west back round to the north-west.
        assertEquals(Tile(4133, 5679), Ocellus.throughFloorPortal(centre, Ocellus.Quadrant.NW, 4121, 5679))
        assertEquals(Tile(4133, 5667), Ocellus.throughFloorPortal(centre, Ocellus.Quadrant.NE, 4133, 5679))
        assertEquals(Tile(4121, 5667), Ocellus.throughFloorPortal(centre, Ocellus.Quadrant.SE, 4133, 5667))
        assertEquals(Tile(4121, 5679), Ocellus.throughFloorPortal(centre, Ocellus.Quadrant.SW, 4121, 5667))
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
                // It never regains hitpoints by itself, so no point of damage is lost to a regeneration tick.
                assertTrue(sword.hitpointsRegenTicks > 1_000_000)
                // Each quadrant 42 ticks, clockwise; 123 ticks in, in the third, his line, and his third phase a tick on.
                for (next in listOf("SW", "NW")) {
                    alice.keepAlive()
                    alice.awaitLine(run, "the $next quadrant's turn", 30_000) { it.endsWith("second: quadrant $next") }
                }
                alice.keepAlive()
                val third = tickOf(alice.awaitLine(run, "his third phase", 30_000) { it.endsWith("third: phase starts") })
                val turns = fight.lines.filter { it.contains("second: quadrant ") }
                assertEquals(listOf("SE", "SW", "NW"), turns.map { it.substringAfter("quadrant ") })
                assertEquals((0..2).map { second + Ocellus.BLADE_AT + Ocellus.QUADRANT_TICKS * it }, turns.map(::tickOf))
                assertEquals(second + Ocellus.SECOND_PHASE_TICKS, tickOf(fight.lines.single { it.endsWith("second: this is enough") }))
                assertEquals(second + Ocellus.SECOND_PHASE_TICKS + Ocellus.THIRD_PHASE_AFTER, third)
                assertEquals(18, fight.lines.count { it.contains("second: volley ") && it.contains(" fires from ") }, "six a quadrant, three quadrants")
                // The Blade drops a tick in and the portals go a tick later.
                alice.awaitLine(run, "the portals going") { it.endsWith("third: portals go") }
                assertTrue(world.locs[east.x, east.z, boss.level, 2]?.id != Ocellus.LOC_PORTAL)
                assertTrue(sword.dead || world.npcs[sword.index] !== sword)
                assertNull(Ocellus.blade(boss))
            }
        }
    }

    @Test
    fun `nobody shelters from his second phase on the cross, and no splash reaches him past the Blade`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.throughTheRest(run)
                alice.toOcellus(server, run)
                alice.npcOp(assertNotNull(run.ocellusFigure).index, 1)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ocellus #") } }
                val boss = boss(run)
                val fight = assertNotNull(run.fights[ArchiveRoom.OCELLUS])
                val world = server.world
                val centre = Ocellus.centre(boss.spawn.x, boss.spawn.z)
                // On the north arm as the portals come: moved a tile off it, into the north-west (nobody's quadrant, so
                // the first clockwise from there), not left under a portal (exploit review round 22, R22B-1).
                alice.commandReply("hit 2500 ${boss.index}") { it.startsWith("Hit ") }
                alice.tele(centre.x, centre.z + 4, ArchiveRoom.OCELLUS.level)
                val second = tickOf(alice.awaitLine(run, "his second phase") { it.endsWith("second: phase starts") })
                val moved = alice.awaitLine(run, "her moved off the arm") { it.contains("second: alice moved off the cross to ") }
                assertEquals(second + Ocellus.PORTALS_AT, tickOf(moved))
                assertTrue(moved.endsWith("${centre.x - 1},${centre.z + 4}"), moved)
                alice.await("in the north-west") { alice.position?.takeIf { it == Coord(centre.x - 1, centre.z + 4, ArchiveRoom.OCELLUS.level) } }
                // His second phase is barred to her: an area spell or a chinchompa's splash finds only the Blade.
                val player = world.playerNamed("alice")!!
                assertTrue(world.combat.barred(player, boss) != null, "his second phase can't be hit")
                alice.awaitLine(run, "the Blade") { it.contains("second: your time; the Blade; first quadrant NW") }
                assertNull(world.combat.barred(player, assertNotNull(Ocellus.blade(boss))), "the Blade can be")
                // Then on the middle of the cross, which no portal covers: the north-west's last volley takes her there,
                // outside the north-east, where its floor portal leads.
                alice.tele(centre.x, centre.z, ArchiveRoom.OCELLUS.level)
                alice.keepAlive()
                val taken = alice.awaitLine(run, "the last volley taking her", 30_000) { it.contains("second: last volley takes alice outside NE for ") }
                val last = fight.lines.single { it.contains("second: last volley fires from ") }
                assertTrue(tickOf(taken) in tickOf(last) + 1..tickOf(last) + 3, taken)
            }
        }
    }

    @Test
    fun `later in a quadrant's turn the pools burst, every portal turns purple, the floor portal takes her on, and the last volley kills`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.throughTheRest(run)
                alice.toOcellus(server, run)
                alice.npcOp(assertNotNull(run.ocellusFigure).index, 1)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ocellus #") } }
                val boss = boss(run)
                val fight = assertNotNull(run.fights[ArchiveRoom.OCELLUS])
                val centre = Ocellus.centre(boss.spawn.x, boss.spawn.z)
                alice.commandReply("hit 2500 ${boss.index}") { it.startsWith("Hit ") }
                // In the south-east quadrant, under ::god, so every hit is logged and none hurts.
                val stand = Tile(centre.x + 3, centre.z - 3)
                alice.tele(stand.x, stand.z, ArchiveRoom.OCELLUS.level)
                val second = tickOf(alice.awaitLine(run, "his second phase") { it.endsWith("second: phase starts") })
                val turn = second + Ocellus.BLADE_AT
                alice.keepAlive()
                // 28 ticks into the turn the pools, on her tile among them, bursting 4 ticks later.
                val pools = alice.awaitLine(run, "the pools", 30_000) { it.contains("second: pools on ") }
                assertEquals(turn + Ocellus.POOLS_AT, tickOf(pools))
                assertTrue(pools.contains("${stand.x},${stand.z}"), pools)
                val count = pools.substringAfter("pools on ").substringBefore(' ').toInt()
                assertTrue(count in Ocellus.POOL_TILES.first..Ocellus.POOL_TILES.last + 1, pools)
                val burst = alice.awaitLine(run, "the pool bursting") { it.contains("second: pool bursts on alice") }
                assertEquals(tickOf(pools) + Ocellus.POOL_LANDS, tickOf(burst))
                // 31 in, every portal on the south and east arms purple, the others orange.
                val purple = alice.awaitLine(run, "every portal purple") { it.endsWith("second: every portal purple") }
                assertEquals(turn + Ocellus.ALL_PURPLE_AT, tickOf(purple))
                val portals = Ocellus.portals(boss)
                assertTrue(portals.filterKeys { it.first == Ocellus.Arm.S || it.first == Ocellus.Arm.E }.values.all { it })
                assertTrue(portals.filterKeys { it.first == Ocellus.Arm.N || it.first == Ocellus.Arm.W }.values.none { it })
                // 33 in, the floor portal at the quadrant's middle; on it, she goes to the same spot in the south-west.
                val opened = alice.awaitLine(run, "the floor portal") { it.contains("second: floor portal at ") }
                assertEquals(turn + Ocellus.FLOOR_PORTAL_AT, tickOf(opened))
                val portal = Ocellus.quadrantTile(centre, Ocellus.Quadrant.SE, Ocellus.FLOOR_PORTAL_OUT, Ocellus.FLOOR_PORTAL_OUT)
                assertTrue(opened.endsWith("${portal.x},${portal.z}"), opened)
                // Not awaited there: the portal can take her on before the client sees her on it.
                alice.command("tele ${portal.x} ${portal.z} ${ArchiveRoom.OCELLUS.level}")
                val through = alice.awaitLine(run, "her going through") { it.contains("second: alice through the floor portal to ") }
                val landed = Ocellus.throughFloorPortal(centre, Ocellus.Quadrant.SE, portal.x, portal.z)
                assertTrue(through.endsWith("${landed.x},${landed.z}"), through)
                alice.await("in the south-west quadrant") { alice.position?.takeIf { it == Coord(landed.x, landed.z, ArchiveRoom.OCELLUS.level) } }
                // 40 in, every purple portal fires: she is out of it.
                val last = alice.awaitLine(run, "the last volley") { it.contains("second: last volley fires from 12 portals") }
                assertEquals(turn + Ocellus.LAST_VOLLEY_AT, tickOf(last))
                alice.awaitLine(run, "the floor portal gone") { it.endsWith("second: floor portal gone") }
                alice.awaitTicks(4)
                assertTrue(fight.lines.none { it.contains("last volley from") }, "nobody left in the south-east")
                // Staying in the south-west through its turn, a tile off the spot she landed on (where its own floor portal
                // will open), its last volley reaches her.
                alice.tele(landed.x + 1, landed.z, ArchiveRoom.OCELLUS.level)
                alice.keepAlive()
                val hit = alice.awaitLine(run, "the next last volley", 30_000) { it.contains("second: last volley from ") }
                assertTrue(hit.contains(" hits alice for "), hit)
                assertTrue(tickOf(hit) in turn + Ocellus.QUADRANT_TICKS + Ocellus.LAST_VOLLEY_AT + 1..turn + Ocellus.QUADRANT_TICKS + Ocellus.LAST_VOLLEY_AT + 3, hit)
                // His line as the phase ends, in the third quadrant's turn.
                alice.keepAlive()
                alice.awaitLine(run, "his third phase", 30_000) { it.endsWith("third: phase starts") }
                val enough = fight.lines.single { it.endsWith("second: this is enough") }
                assertEquals(second + Ocellus.SECOND_PHASE_TICKS, tickOf(enough))
            }
        }
    }

    @Test
    fun `once the cross falls each arm has three stones over it, from a quadrant to the next clockwise`() {
        val centre = Ocellus.centre(Ocellus.FIGURE_X, Ocellus.FIGURE_Z)
        // The capture notes' crossings: SE 4129,5663 to SW 4125,5669; SW 4117,5671 to NW 4123,5675; NW 4125,5683 to NE
        // 4129,5677; NE 4137,5675 to SE 4131,5671.
        val ways = Ocellus.Arm.entries.associateWith { Ocellus.crossing(centre, it) }
        assertEquals(listOf(Tile(4129, 5663), Tile(4125, 5669)), ways.getValue(Ocellus.Arm.S).let { listOf(it.start, it.land) })
        assertEquals(listOf(Tile(4117, 5671), Tile(4123, 5675)), ways.getValue(Ocellus.Arm.W).let { listOf(it.start, it.land) })
        assertEquals(listOf(Tile(4125, 5683), Tile(4129, 5677)), ways.getValue(Ocellus.Arm.N).let { listOf(it.start, it.land) })
        assertEquals(listOf(Tile(4137, 5675), Tile(4131, 5671)), ways.getValue(Ocellus.Arm.E).let { listOf(it.start, it.land) })
        for ((arm, way) in ways) {
            assertEquals(arm, Ocellus.armOut(way.from))
            assertEquals(Ocellus.Quadrant.entries[(way.from.ordinal + 1) % 4], way.to, "clockwise")
            assertEquals(way.from, Ocellus.quadrant(centre, way.start.x, way.start.z))
            assertEquals(way.to, Ocellus.quadrant(centre, way.land.x, way.land.z))
            // Its stones on its arm, each a hop (at most two tiles) from the last.
            assertTrue(way.stones.all { Ocellus.onCross(centre, it.x, it.z) }, arm.name)
            assertTrue(!Ocellus.onCross(centre, way.start.x, way.start.z) && !Ocellus.onCross(centre, way.land.x, way.land.z), arm.name)
            (listOf(way.start) + way.stones + way.land).zipWithNext { a, b -> assertTrue(maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z)) <= 2, "$arm $a $b") }
            // On the arm's fallen loc, 3 wide and 11 long.
            val sw = Ocellus.armTile(centre, arm)
            val (width, length) = if (arm == Ocellus.Arm.N || arm == Ocellus.Arm.S) 3 to 11 else 11 to 3
            assertTrue(way.stones.all { it.x in sw.x until sw.x + width && it.z in sw.z until sw.z + length }, arm.name)
        }
        // His room's corridor south of the arena is no part of it: nobody there is moved off the cross or lost.
        assertTrue(Ocellus.inArena(centre, 4127, 5661))
        assertFalse(Ocellus.inArena(centre, 4127, 5629))
        // The arms: the north 5675-5685 and the south 5661-5671 at x 4126-4128, the east 4129-4139 and west 4115-4125.
        assertEquals(Tile(4126, 5675), Ocellus.armTile(centre, Ocellus.Arm.N))
        assertEquals(Tile(4126, 5661), Ocellus.armTile(centre, Ocellus.Arm.S))
        assertEquals(Tile(4129, 5672), Ocellus.armTile(centre, Ocellus.Arm.E))
        assertEquals(Tile(4115, 5672), Ocellus.armTile(centre, Ocellus.Arm.W))
        // His corner in each quadrant is his first phase's there, inside it.
        val middle = Ocellus.middle(Ocellus.FIGURE_X, Ocellus.FIGURE_Z)
        for (quadrant in Ocellus.Quadrant.entries) {
            val corner = Ocellus.quadrantCorner(middle, quadrant)
            assertTrue((0..2).all { dx -> (0..2).all { dz -> Ocellus.quadrant(centre, corner.x + dx, corner.z + dz) == quadrant } }, quadrant.name)
        }
    }

    @Test
    fun `in his third phase the cross falls, he holds a quadrant and fights his tank, and each quadrant falls in turn`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.throughTheRest(run)
                alice.toOcellus(server, run)
                alice.npcOp(assertNotNull(run.ocellusFigure).index, 1)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ocellus #") } }
                val boss = boss(run)
                val fight = assertNotNull(run.fights[ArchiveRoom.OCELLUS])
                val world = server.world
                val level = ArchiveRoom.OCELLUS.level
                val centre = Ocellus.centre(boss.spawn.x, boss.spawn.z)
                alice.commandReply("hit 2500 ${boss.index}") { it.startsWith("Hit ") }
                // The south-east first, so the second phase ends in the north-west's turn, which he holds.
                alice.tele(centre.x + 3, centre.z - 3, level)
                alice.awaitLine(run, "his second phase") { it.endsWith("second: phase starts") }
                alice.keepAlive()
                alice.awaitLine(run, "the south-west's turn", 30_000) { it.endsWith("second: quadrant SW") }
                alice.keepAlive()
                val started = alice.awaitLine(run, "his third phase", 30_000) { it.contains("third: third phase: ") }
                val third = tickOf(started)
                assertTrue(started.endsWith("5250 hitpoints; his quadrant NW"), started)
                assertEquals(Ocellus.P3, boss.type.id)
                assertEquals(5250, boss.maxHitpoints, "the same bar")
                // On the south arm as it cracks: moved off it, west, the side of his quadrant.
                alice.tele(centre.x, centre.z - 5, level)
                val moved = alice.awaitLine(run, "her moved off the cross") { it.contains("third: alice moved off the cross to ") }
                assertEquals(third + Ocellus.ARMS_CRACK_AT, tickOf(moved))
                assertTrue(moved.endsWith("${centre.x - 2},${centre.z - 5}"), moved)
                alice.await("off the arm") { alice.position?.takeIf { it == Coord(centre.x - 2, centre.z - 5, level) } }
                // Twelve in, the arms' empty forms and their stones, and he takes the north-west from its corner.
                val took = alice.awaitLine(run, "his quadrant") { it.contains("third: takes quadrant NW") }
                assertEquals(third + Ocellus.ARMS_EMPTY_AT, tickOf(took))
                val corner = Ocellus.quadrantCorner(Ocellus.middle(boss.spawn.x, boss.spawn.z), Ocellus.Quadrant.NW)
                assertEquals(Tile(corner.x, corner.z), Tile(boss.x, boss.z))
                assertEquals(Ocellus.Quadrant.NW, Ocellus.held(boss))
                for (arm in Ocellus.Arm.entries) {
                    val sw = Ocellus.armTile(centre, arm)
                    assertEquals(Ocellus.LOC_ARMS_EMPTY[arm.ordinal], world.locs[sw.x, sw.z, level, 2]?.id, arm.name)
                    val stones = Ocellus.crossing(centre, arm).stones
                    assertEquals(listOf(Ocellus.LOC_STONES_OUTER[arm.ordinal], Ocellus.LOC_STONE_MIDDLE, Ocellus.LOC_STONES_OUTER[arm.ordinal]), stones.map { world.locs[it.x, it.z, level, 2]?.id })
                }
                assertTrue(alice.fight().contains("third phase, quadrant NW"), alice.fight())
                // From the south-west, the south arm's stone beside her is the way back: closed.
                val back = Ocellus.crossing(centre, Ocellus.Arm.S).stones.last()
                alice.locOp(Ocellus.LOC_STONES_OUTER[Ocellus.Arm.S.ordinal], back.x, back.z, 1)
                alice.awaitLine(run, "the way back closed") { it.endsWith("third: alice can't jump the S arm back") }
                // Beside him, east, she hits him and is his tank: his melee, then his slash, towards her.
                val beside = Tile(corner.x + 3, corner.z + 1)
                alice.tele(beside.x, beside.z, level)
                alice.commandReply("hit 10 ${boss.index}") { it.startsWith("Hit ") }
                alice.awaitLine(run, "his tank") { it.endsWith("third: turns to alice") }
                assertEquals("alice", Ocellus.tank(boss)?.username)
                alice.awaitLine(run, "his melee") { it.endsWith("third: melee at alice") }
                alice.awaitLine(run, "his slash") { it.endsWith("third: slash towards alice") }
                alice.awaitLine(run, "his slash landing") { it.contains("third: slash hits alice for ") }
                // Out of his reach, still in his quadrant: a void shot at everyone.
                alice.tele(beside.x + 2, beside.z, level)
                alice.awaitLine(run, "his void shot") { it.endsWith("third: void shot at alice") }
                // The north arm's barriers stand: no jumping yet.
                val north = Ocellus.crossing(centre, Ocellus.Arm.N)
                alice.locOp(Ocellus.LOC_STONES_OUTER[Ocellus.Arm.N.ordinal], north.stones.first().x, north.stones.first().z, 1)
                alice.awaitLine(run, "the barriers") { it.endsWith("third: alice can't jump the N arm: barriers up") }
                // At 75% the north-west is to fall and its barriers drop: over the north arm to the north-east.
                alice.commandReply("hit ${boss.hitpoints - 5250 * 75 / 100} ${boss.index}") { it.startsWith("Hit ") }
                val fall = tickOf(alice.awaitLine(run, "the north-west to fall") { it.contains("third: NW to fall at ") })
                alice.locOp(Ocellus.LOC_STONES_OUTER[Ocellus.Arm.N.ordinal], north.stones.first().x, north.stones.first().z, 1)
                alice.awaitLine(run, "her jump") { it.endsWith("third: alice jumps the N arm") }
                alice.awaitLine(run, "her over") { it.endsWith("third: alice over to NE at ${north.land.x},${north.land.z}") }
                alice.await("in the north-east") { alice.position?.takeIf { it == Coord(north.land.x, north.land.z, level) } }
                // It falls, with its arms' stones; she isn't lost with it; he takes the north-east.
                val fell = alice.awaitLine(run, "the north-west falling") { it.endsWith("third: quadrant NW falls") }
                assertEquals(fall + Ocellus.FALL_FALLS, tickOf(fell))
                assertTrue(fight.lines.none { it.contains("lost with it") })
                for (arm in listOf(Ocellus.Arm.N, Ocellus.Arm.W)) {
                    val stone = Ocellus.crossing(centre, arm).stones.first()
                    assertTrue(world.locs[stone.x, stone.z, level, 2]?.id !in Ocellus.GAPS, arm.name)
                }
                val next = alice.awaitLine(run, "the north-east") { it.contains("third: takes quadrant NE") }
                assertEquals(fall + Ocellus.FALL_NEXT, tickOf(next))
                assertEquals(listOf(Ocellus.Quadrant.NW), Ocellus.fallen(boss))
                // At 50% she stays: lost with the north-east. At 25% the south-east falls, and until step 27 he dies as his
                // final stand would begin.
                alice.commandReply("hit ${boss.hitpoints - 5250 / 2} ${boss.index}") { it.startsWith("Hit ") }
                alice.awaitLine(run, "her lost") { it.contains("third: alice lost with it for ") }
                alice.awaitLine(run, "the south-east") { it.contains("third: takes quadrant SE") }
                alice.commandReply("hit ${boss.hitpoints - 5250 / 4} ${boss.index}") { it.startsWith("Hit ") }
                alice.keepAlive()
                alice.await("his room done", 30_000) { ArchiveRoom.OCELLUS.takeIf { it in run.cleared } }
                assertEquals(listOf(Ocellus.Quadrant.NW, Ocellus.Quadrant.NE, Ocellus.Quadrant.SE), Ocellus.fallen(boss))
                assertTrue(fight.lines.any { it.endsWith("third: final stand: stand-in, he dies") })
                assertTrue(boss.dead)
            }
        }
    }

    /** Through his first two phases to his third, the second ending in the north-west's turn; the tick it began. */
    private fun HeadlessClient.toHisThird(run: ArchiveRun, vararg others: HeadlessClient): Long {
        val boss = boss(run)
        val centre = Ocellus.centre(boss.spawn.x, boss.spawn.z)
        commandReply("hit 2500 ${boss.index}") { it.startsWith("Hit ") }
        tele(centre.x + 3, centre.z - 3, ArchiveRoom.OCELLUS.level)
        awaitLine(run, "his second phase") { it.endsWith("second: phase starts") }
        keepAlive()
        for (other in others) other.keepAlive()
        awaitLine(run, "the south-west's turn", 30_000) { it.endsWith("second: quadrant SW") }
        keepAlive()
        for (other in others) other.keepAlive()
        return tickOf(awaitLine(run, "his third phase", 30_000) { it.contains("third: third phase: ") })
    }

    @Test
    fun `in his third phase void bombs fall, and after each six attacks a special, the cog, the void and Untether, and Insolence first in the next quadrant`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.throughTheRest(run)
                alice.toOcellus(server, run)
                alice.npcOp(assertNotNull(run.ocellusFigure).index, 1)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ocellus #") } }
                val boss = boss(run)
                val fight = assertNotNull(run.fights[ArchiveRoom.OCELLUS])
                val world = server.world
                val level = ArchiveRoom.OCELLUS.level
                val centre = Ocellus.centre(boss.spawn.x, boss.spawn.z)
                // She stays where she is put: his hits don't send her at him.
                alice.command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
                alice.toHisThird(run)
                val took = tickOf(alice.awaitLine(run, "his quadrant") { it.contains("third: takes quadrant NW") })
                // Beside him, east, she is his tank.
                val corner = Ocellus.quadrantCorner(Ocellus.middle(boss.spawn.x, boss.spawn.z), Ocellus.Quadrant.NW)
                val beside = Tile(corner.x + 3, corner.z + 1)
                alice.tele(beside.x, beside.z, level)
                alice.commandReply("hit 10 ${boss.index}") { it.startsWith("Hit ") }
                // Three ticks in, two void bombs in each quadrant, leaving void round them: their locs on their tiles.
                val bombs = alice.awaitLine(run, "the void bombs") { it.contains("third: void bombs at ") }
                assertEquals(took + Ocellus.BOMBS_AT, tickOf(bombs))
                val dropped = bombs.substringAfter("void bombs at ").split(' ').map { it.split(',').let { (x, z) -> Tile(x.toInt(), z.toInt()) } }
                assertEquals(Ocellus.BOMBS * 4, dropped.size)
                assertEquals(Ocellus.Quadrant.entries.flatMap { q -> List(Ocellus.BOMBS) { q } }, dropped.map { Ocellus.quadrant(centre, it.x, it.z) })
                val voids = Ocellus.voids(boss)
                for (bomb in dropped) {
                    assertTrue((-1..1).all { dx -> (-1..1).all { dz -> Tile(bomb.x + dx, bomb.z + dz) in voids } }, "void round $bomb")
                }
                assertEquals(Ocellus.LOC_BOMB, world.locs[dropped.last().x, dropped.last().z, level, 2]?.id)
                assertTrue(voids.none { Ocellus.onCross(centre, it.x, it.z) }, "none on the cross, its arms and stones")
                // Where she stands for the cog: beside him, on a tile no bomb's void has taken, whose void would
                // outlast the debris's (Codex, PR 581).
                val size = boss.type.size
                val stand = if (beside !in voids) beside else ((boss.x - 1)..(boss.x + size)).flatMap { x -> ((boss.z - 1)..(boss.z + size)).map { z -> Tile(x, z) } }
                    .first { (it.x !in boss.x until boss.x + size || it.z !in boss.z until boss.z + size) && it !in voids &&
                        Ocellus.quadrant(centre, it.x, it.z) == Ocellus.Quadrant.NW && !Ocellus.onCross(centre, it.x, it.z) }
                // On one, the void hurts her each tick.
                // One clear of his footprint, so she can stand on it.
                val onBomb = dropped.first { it.x !in boss.x - 1..boss.x + 3 || it.z !in boss.z - 1..boss.z + 3 }
                alice.tele(onBomb.x, onBomb.z, level)
                alice.awaitLine(run, "the void on her") { it.contains("third: void on alice for ") }
                alice.tele(stand.x, stand.z, level)
                // Six attacks on, the cog: two strikes in the first quadrant, three ticks apart, and debris where she stood
                // on the first of them, void five ticks later.
                val cog = alice.awaitLine(run, "the cog", 30_000) { it.endsWith("third: cog: 2 strikes") }
                assertEquals(6, fight.lines.count { it.contains("third: melee at ") || it.contains("third: slash towards ") || it.contains("third: void shot at ") })
                val first = alice.awaitLine(run, "the cog's first strike") { it.contains("third: cog strike 1 hits alice for ") }
                assertEquals(tickOf(cog) + Ocellus.COG_GAP, tickOf(first))
                val debris = alice.awaitLine(run, "the debris") { it.contains("third: debris falls on ") }
                assertTrue(debris.endsWith("${stand.x},${stand.z}"), debris)
                alice.awaitLine(run, "the cog's second strike") { it.contains("third: cog strike 2 hits alice for ") }
                val void = alice.awaitLine(run, "the debris's void") { it.endsWith("third: debris void") }
                assertEquals(tickOf(debris) + Ocellus.DEBRIS_AFTER, tickOf(void))
                assertTrue(stand in Ocellus.voids(boss))
                assertEquals(Ocellus.LOC_DEBRIS, world.locs[stand.x, stand.z, level, 2]?.id)
                // Six more, the void: his line, then three ticks later his void shot; alone, she is his tank, so no vine.
                alice.keepAlive()
                val voidLine = alice.awaitLine(run, "the void", 30_000) { it.endsWith("third: the void") }
                val shot = alice.awaitLine(run, "the void's shot") { it.endsWith("third: void shot at alice") && tickOf(it) > tickOf(voidLine) }
                assertEquals(tickOf(voidLine) + Ocellus.VOID_AFTER, tickOf(shot))
                assertNull(Ocellus.rooted(boss))
                // Six more, Untether: four eruptions, on her tile among them; the square; and his coming down, which spares
                // her beside him.
                alice.keepAlive()
                val untether = tickOf(alice.awaitLine(run, "untether", 30_000) { it.endsWith("third: untether") })
                alice.awaitLine(run, "the first eruption") { it.contains("third: eruption 1 on ") }
                alice.awaitLine(run, "an eruption on her") { it.contains("third: eruption hits alice for ") }
                alice.keepAlive()
                val square = alice.awaitLine(run, "the square", 30_000) { it.endsWith("third: the square shows") }
                assertEquals(untether + Ocellus.SQUARE_AT, tickOf(square))
                val down = alice.awaitLine(run, "his coming down") { it.endsWith("third: he comes down") }
                assertEquals(untether + Ocellus.SLAM_AT, tickOf(down))
                assertEquals(4, fight.lines.count { it.contains("third: eruption ") && it.contains(" on ") })
                assertTrue(fight.lines.none { it.contains("he comes down on alice") }, "safe in the square")
                // As Untether ends, in his first quadrant, two Siphons: each heals him every four ticks.
                val siphons = alice.awaitLine(run, "the siphons") { it.contains("third: siphon flares at ") }
                assertEquals(2, Ocellus.flares(boss).size)
                assertTrue(Ocellus.flares(boss).all { it.type.id == Ocellus.Flare.SIPHON.id && Ocellus.quadrant(centre, it.x, it.z) == Ocellus.Quadrant.NW })
                val heal = alice.awaitLine(run, "a siphon's heal") { it.contains("third: siphon heals him ") }
                assertEquals(tickOf(siphons) + Ocellus.SIPHON_HEAL_EVERY, tickOf(heal))
                // Standing between one and him, she blocks its next heal; destroying it, she is empowered.
                val siphon = Ocellus.flares(boss).first()
                val (midX, midZ) = boss.x + 1 to boss.z + 1
                val steps = maxOf(kotlin.math.abs(midX - siphon.x), kotlin.math.abs(midZ - siphon.z))
                val block = Tile(siphon.x + Math.round((midX - siphon.x) / steps.toDouble()).toInt(), siphon.z + Math.round((midZ - siphon.z) / steps.toDouble()).toInt())
                alice.tele(block.x, block.z, level)
                alice.awaitLine(run, "her block") { it.contains("third: alice blocks a siphon's heal, healed ") }
                alice.commandReply("hit 44 ${siphon.index}") { it.startsWith("Hit ") }
                alice.awaitLine(run, "her empowerment") { it.endsWith("third: siphon destroyed: alice empowered") }
                alice.tele(stand.x, stand.z, level)
                // At 75% the north-west falls with its void; she goes on to the north-east, and there Insolence is first:
                // three superior slashes towards her, five ticks apart, leaving void.
                alice.commandReply("hit ${boss.hitpoints - 5250 * 75 / 100} ${boss.index}") { it.startsWith("Hit ") }
                alice.awaitLine(run, "the north-west to fall") { it.contains("third: NW to fall at ") }
                val ne = Ocellus.quadrantTile(centre, Ocellus.Quadrant.NE, 3, 3)
                alice.tele(ne.x, ne.z, level)
                alice.awaitLine(run, "the north-east") { it.contains("third: takes quadrant NE") }
                assertTrue(Ocellus.voids(boss).none { Ocellus.quadrant(centre, it.x, it.z) == Ocellus.Quadrant.NW }, "gone with it")
                alice.keepAlive()
                val insolence = tickOf(alice.awaitLine(run, "insolence", 30_000) { it.endsWith("third: insolence") })
                val slashes = (1..3).map { n -> tickOf(alice.awaitLine(run, "superior slash $n") { it.endsWith("third: superior slash towards alice") && fight.lines.count { l -> l.endsWith("superior slash towards alice") } >= n }) }
                assertEquals((0..2).map { insolence + Ocellus.INSOLENCE_GAP * it }, slashes)
                alice.awaitTicks(2)
                assertTrue(Ocellus.voids(boss).any { world.locs[it.x, it.z, level, 2]?.id in Ocellus.LOC_INSOLENCE }, "its bands")
            }
        }
    }

    @Test
    fun `from his second quadrant void flares come with his first attack, Augmenters then Rifts, empowering whoever destroys one, and one left detonates`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.throughTheRest(run)
                alice.toOcellus(server, run)
                alice.npcOp(assertNotNull(run.ocellusFigure).index, 1)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ocellus #") } }
                val boss = boss(run)
                val world = server.world
                val level = ArchiveRoom.OCELLUS.level
                val centre = Ocellus.centre(boss.spawn.x, boss.spawn.z)
                alice.command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
                alice.toHisThird(run)
                alice.awaitLine(run, "his quadrant") { it.contains("third: takes quadrant NW") }
                val player = world.playerNamed("alice")!!
                // Straight to 75%: no Untether in the north-west, so no Siphons.
                alice.commandReply("hit ${boss.hitpoints - 5250 * 75 / 100} ${boss.index}") { it.startsWith("Hit ") }
                alice.awaitLine(run, "the north-west to fall") { it.contains("third: NW to fall at ") }
                val ne = Ocellus.quadrantTile(centre, Ocellus.Quadrant.NE, 3, 3)
                alice.tele(ne.x, ne.z, level)
                val took = tickOf(alice.awaitLine(run, "the north-east") { it.contains("third: takes quadrant NE") })
                // With his first attack there, two Augmenters in his quadrant, off the void and the cross.
                val augmenters = alice.awaitLine(run, "the augmenters") { it.contains("third: augmenter flares at ") }
                assertEquals(took + Ocellus.FIRST_ATTACK_AFTER, tickOf(augmenters))
                assertTrue(lines(run).none { it.contains("siphon flares") })
                val two = Ocellus.flares(boss)
                assertEquals(2, two.size)
                assertTrue(two.all { it.type.id == Ocellus.Flare.AUGMENTER.id && Ocellus.quadrant(centre, it.x, it.z) == Ocellus.Quadrant.NE }, "$two")
                assertTrue(two.none { Tile(it.x, it.z) in Ocellus.voids(boss) || Ocellus.onCross(centre, it.x, it.z) })
                // Destroying one boosts her Attack and Strength as a super combat potion, Magic and Ranged as a ranging potion.
                alice.commandReply("hit 44 ${two[0].index}") { it.startsWith("Hit ") }
                alice.awaitLine(run, "her empowerment") { it.endsWith("third: augmenter destroyed: alice empowered") }
                for ((skill, percent, constant) in listOf(Triple(Skill.ATTACK, 15, 5), Triple(Skill.STRENGTH, 15, 5), Triple(Skill.MAGIC, 10, 4), Triple(Skill.RANGED, 10, 4))) {
                    val base = player.skills.baseLevel(skill)
                    assertEquals(base + base * percent / 100 + constant, player.skills.currentLevel(skill), skill.name)
                }
                // The other, left, detonates as its bar fills, on everyone.
                alice.keepAlive()
                val detonates = alice.awaitLine(run, "the detonation", 30_000) { it.endsWith("third: augmenter flare detonates") }
                assertEquals(tickOf(augmenters) + Ocellus.FLARE_CHARGE, tickOf(detonates))
                alice.awaitLine(run, "its hit on her") { it.contains("third: flare hits alice for ") }
                assertTrue(Ocellus.flares(boss).isEmpty())
                // At 50% on to the south-east, where they are Rifts, maxed by melee.
                alice.commandReply("hit ${boss.hitpoints - 5250 * 50 / 100} ${boss.index}") { it.startsWith("Hit ") }
                alice.awaitLine(run, "the north-east to fall") { it.contains("third: NE to fall at ") }
                val se = Ocellus.quadrantTile(centre, Ocellus.Quadrant.SE, 3, 3)
                alice.tele(se.x, se.z, level)
                alice.keepAlive()
                alice.awaitLine(run, "the south-east", 30_000) { it.contains("third: takes quadrant SE") }
                alice.awaitLine(run, "the rifts") { it.contains("third: rift flares at ") }
                val rifts = Ocellus.flares(boss)
                assertTrue(rifts.size == 2 && rifts.all { it.type.id == Ocellus.Flare.RIFT.id }, "$rifts")
                assertEquals(SureHit.MAX, world.combat.sureHit(player, rifts[0], AttackType.SLASH))
                assertEquals(SureHit.ROLLED, world.combat.sureHit(player, boss, AttackType.SLASH))
                alice.commandReply("hit 44 ${rifts[0].index}") { it.startsWith("Hit ") }
                alice.awaitLine(run, "her empowerment by the rift") { it.endsWith("third: rift destroyed: alice empowered") }
            }
        }
    }

    @Test
    fun `his void roots someone other than his tank in a vine, which only another can break`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    val run = twoIn(server, alice, bob)
                    bob.toOcellus(server, run)
                    val hooded = assertNotNull(run.ocellusFigure)
                    alice.npcOp(hooded.index, 1)
                    bob.npcOp(hooded.index, 1)
                    alice.await("his fight") { alice.fight().takeIf { it.startsWith("ocellus #") } }
                    val boss = boss(run)
                    val world = server.world
                    val level = ArchiveRoom.OCELLUS.level
                    val centre = Ocellus.centre(boss.spawn.x, boss.spawn.z)
                    bob.tele(centre.x + 4, centre.z - 4, level)
                    // They stay where they are put: his hits don't send them at him.
                    for (client in listOf(alice, bob)) client.command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
                    alice.toHisThird(run, bob)
                    alice.awaitLine(run, "his quadrant") { it.contains("third: takes quadrant NW") }
                    val corner = Ocellus.quadrantCorner(Ocellus.middle(boss.spawn.x, boss.spawn.z), Ocellus.Quadrant.NW)
                    alice.tele(corner.x + 3, corner.z + 1, level)
                    val spot = Ocellus.quadrantTile(centre, Ocellus.Quadrant.NW, 2, 2)
                    bob.tele(spot.x, spot.z, level)
                    alice.commandReply("hit 10 ${boss.index}") { it.startsWith("Hit ") }
                    alice.awaitLine(run, "her his tank") { it.endsWith("third: turns to alice") }
                    // The void after the cog: bob, not his tank, rooted where he stands.
                    alice.keepAlive()
                    bob.keepAlive()
                    alice.awaitLine(run, "the cog", 30_000) { it.contains("third: cog: ") }
                    alice.keepAlive()
                    bob.keepAlive()
                    alice.awaitLine(run, "the vine", 30_000) { it.endsWith("third: void vine roots bob") }
                    val bobPlayer = world.playerNamed("bob")!!
                    assertEquals(bobPlayer, Ocellus.rooted(boss))
                    val vineNpc = assertNotNull(world.npcs.inPlay.firstOrNull { it.type.id == Ocellus.VINE })
                    assertEquals(Tile(spot.x, spot.z), Tile(vineNpc.x, vineNpc.z))
                    // He can't go, nor break it himself.
                    bob.command("tele ${spot.x + 3} ${spot.z} $level")
                    bob.await("back where it holds him") { bob.position?.takeIf { it == Coord(spot.x, spot.z, level) } }
                    assertTrue(bobPlayer.rootedTicks > 0, "held in the movement phase too")
                    assertTrue(world.combat.barred(bobPlayer, vineNpc) != null)
                    val alicePlayer = world.playerNamed("alice")!!
                    assertNull(world.combat.barred(alicePlayer, vineNpc))
                    // Alice breaks it: he is free.
                    alice.commandReply("hit 44 ${vineNpc.index}") { it.startsWith("Hit ") }
                    alice.awaitLine(run, "his freeing") { it.endsWith("third: bob breaks free of the void vine") }
                    assertNull(Ocellus.rooted(boss))
                    assertEquals(0, bobPlayer.rootedTicks)
                    assertTrue(run.fights[ArchiveRoom.OCELLUS]!!.lines.none { it.contains("void vine crushes") })
                }
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
    fun `from his third phase a censer stands in each quadrant, and each is lit once for the party`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.commandReply("setlevel hitpoints 99") { it.contains("set to level 99") }
                alice.commandReply("setlevel prayer 99") { it.contains("set to level 99") }
                alice.commandReply("heal") { it.startsWith("Healed") }
                alice.throughTheRest(run)
                alice.toOcellus(server, run)
                alice.npcOp(assertNotNull(run.ocellusFigure).index, 1)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ocellus #") } }
                val boss = boss(run)
                val world = server.world
                val level = ArchiveRoom.OCELLUS.level
                val centre = Ocellus.centre(boss.spawn.x, boss.spawn.z)
                alice.command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
                // None before his third phase.
                for (censer in Ocellus.Censer.entries) {
                    val tile = Ocellus.censerTile(centre, censer)
                    assertNull(world.locs[tile.x, tile.z, level, 2], censer.name)
                }
                alice.toHisThird(run)
                alice.awaitLine(run, "the censers") { it.endsWith("third: censers stand") }
                for (censer in Ocellus.Censer.entries) {
                    val tile = Ocellus.censerTile(centre, censer)
                    assertEquals(censer.id, world.locs[tile.x, tile.z, level, 2]?.id, censer.name)
                }
                val player = world.playerNamed("alice")!!

                /** Beside [censer], on the middle's side, and its Interact. */
                fun light(censer: Ocellus.Censer) {
                    val tile = Ocellus.censerTile(centre, censer)
                    val beside = Tile(tile.x + if (tile.x > centre.x) -1 else 1, tile.z)
                    alice.tele(beside.x, beside.z, level)
                    alice.locOp(censer.id, tile.x, tile.z, 1)
                    alice.awaitLine(run, "the ${censer.name.lowercase()} censer lit") { it.endsWith("alice lights the ${censer.name.lowercase()} censer") }
                    assertEquals(censer.inactive, world.locs[tile.x, tile.z, level, 2]?.id, censer.name)
                    assertTrue(censer in Ocellus.lit(boss))
                }

                // Saradomin's: 40 hitpoints and 40 prayer points back.
                alice.commandReply("god off") { it.startsWith("You can be hurt") }
                alice.commandReply("hit 50 me") { it.startsWith("Hit ") }
                alice.commandReply("god on") { it.startsWith("You can't be hurt") }
                alice.commandReply("boost prayer 10") { it.startsWith("Prayer is now 10") }
                val hurt = player.hitpoints
                light(Ocellus.Censer.SARADOMIN)
                assertTrue(player.hitpoints >= minOf(99, hurt + Ocellus.SARADOMIN_RESTORE - 1), "${player.hitpoints} from $hurt")
                assertEquals(10 + Ocellus.SARADOMIN_RESTORE, player.skills.currentLevel(Skill.PRAYER))
                // The Zarosian: half a bar of special attack energy.
                alice.commandReply("spec 10") { it.isNotEmpty() }
                light(Ocellus.Censer.ZAROSIAN)
                assertTrue(world.specialAttacks.energy(player) in 600..700, "${world.specialAttacks.energy(player)}")
                // The Armadylean and Bandosian: her next ranged and next melee attack on him a sure max, once each.
                light(Ocellus.Censer.ARMADYLEAN)
                light(Ocellus.Censer.BANDOSIAN)
                assertEquals(SureHit.MAX, Ocellus.censerHit(player, boss, AttackType.RANGED))
                assertNull(Ocellus.censerHit(player, boss, AttackType.RANGED), "once")
                assertNull(Ocellus.censerHit(player, boss, AttackType.MAGIC), "not magic")
                assertEquals(SureHit.MAX, Ocellus.censerHit(player, boss, AttackType.SLASH))
                assertNull(Ocellus.censerHit(player, boss, AttackType.CRUSH), "once")
                // Each once: the inactive form has no Interact, and nothing more is logged.
                val tile = Ocellus.censerTile(centre, Ocellus.Censer.SARADOMIN)
                alice.tele(tile.x - 1, tile.z, level)
                alice.locOp(Ocellus.Censer.SARADOMIN.inactive, tile.x, tile.z, 1)
                alice.awaitTicks(3)
                assertEquals(1, lines(run).count { it.endsWith("lights the saradomin censer") })
                assertEquals(Ocellus.Censer.entries.toSet(), Ocellus.lit(boss))
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
