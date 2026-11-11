package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.EquipmentTab
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.CombatSpell
import osrsps.game.combat.Deaths
import osrsps.game.combat.Element
import osrsps.game.combat.NpcCombat
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.Npc
import osrsps.game.raid.ArchiveRaids
import osrsps.game.raid.ArchiveRoom
import osrsps.game.raid.ArchiveRun
import osrsps.game.raid.Dagonhai
import osrsps.game.raid.PartyScaling
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * The Dagon'hai Elder and his Monks (raid step 23) through the headless client: their fight beginning with the first
 * of the party onto the room's floor, round the cauldron; the shared pool, a stunned one taking 15% more; the Elder's
 * shots; a channel stopped by a spell of its element and one run into the cauldron's charge; a tome blocked on the gust
 * and one reaching the cauldron; each shield break with its rifts and imps, a rift drained by a player beside it; and
 * after the fifth the cauldron's burst, a Monk dead, the two left on 7 hitpoints, and the room done once both die. The
 * rl-client script test-runner/examples/archive-dagonhai.json fights them in the real client.
 */
class HeadlessDagonhaiTest {
    private fun caster(id: Int, name: String, hitpoints: Int, internal: String) = NpcType(
        id, name, size = 2, ops = listOf(null, "Attack", null, null, null), internalName = internal,
        stats = NpcStats(attack = 1, defence = 100, strength = 1, hitpoints = hitpoints, ranged = 260, magic = 290),
    )

    private val cauldron = NpcType(
        Dagonhai.CAULDRON, "Cauldron", size = 3, ops = listOf(null, null, null, null, null), internalName = "tfa_dagonhai_focus",
        stats = NpcStats(attack = 1, defence = 1, strength = 1, hitpoints = 1000, ranged = 1, magic = 1),
    )
    private val imp = NpcType(
        Dagonhai.IMP, "Imp", ops = listOf(null, "Attack", null, null, null), internalName = "tfa_dagonhai_demon",
        stats = NpcStats(attack = 250, defence = 50, strength = 195, hitpoints = 44, ranged = 66, magic = 125),
    )
    private val rift = NpcType(
        Dagonhai.RIFT, "Demonic rift", ops = listOf(null, null, null, null, null), internalName = "tfa_dagonhai_rift",
        stats = NpcStats(attack = 1, defence = 1, strength = 1, hitpoints = 20, ranged = 1, magic = 1),
    )
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))

    /** The common imp, which the combat file names `imp`: without it there the file's entry would take ours by its name. */
    private val commonImp = NpcType(5007, "Imp", ops = listOf(null, "Attack", null, null, null), internalName = "imp")
    private val types = NpcTypes.of(
        cauldron, imp, rift, grave, commonImp,
        caster(Dagonhai.ELDER, "Dagon'hai Elder", 640, "tfa_dagonhai_leader"),
        caster(Dagonhai.ELDER_WEAKENED, "Dagon'hai Elder", 7, "tfa_dagonhai_leader_unshielded"),
        caster(Dagonhai.MONK_1, "Dagon'hai Monk", 640, "tfa_dagonhai_minion_1"),
        caster(Dagonhai.MONK_1_WEAKENED, "Dagon'hai Monk", 7, "tfa_dagonhai_minion_1_unshielded"),
        caster(Dagonhai.MONK_2, "Dagon'hai Monk", 640, "tfa_dagonhai_minion_2"),
        caster(Dagonhai.MONK_2_WEAKENED, "Dagon'hai Monk", 7, "tfa_dagonhai_minion_2_unshielded"),
    )

    private val locTypes = LocTypes(
        mapOf(ArchiveRaids.ARCHIVE_DOOR to LocType(ArchiveRaids.ARCHIVE_DOOR, "Archive Door", ops = listOf("Enter", null, null, null, null), collision = LocCollisionType(width = 9, length = 1))),
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
            encounterDefs = listOfNotNull(Dagonhai.def(types)), cycleMillis = millis,
        )
    }

    /** A Fire Bolt as the spellbook has it, for its element. */
    private val fireBolt = CombatSpell(0, "Fire Bolt", 0, 35, 12, emptyList(), 0, -1, -1, -1, -1, -1, element = Element.FIRE)

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.fight(): String = commandReply("raid") { it.startsWith("dagonhai #") || it.startsWith("You're not") }

    /** Into the run, under `::god`, from the lobby's Archive Door, with auto retaliate off so they stay where they're put. */
    private fun HeadlessClient.intoRun(server: TestServer, username: String): ArchiveRun {
        await("the login rebuild") { position }
        commandReply("god on") { it.startsWith("You can't be hurt") }
        commandReply("setlevel prayer 99") { it.contains("set to level 99") }
        command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
        await("auto retaliate off") { if (varp(EquipmentTab.VARP_AUTO_RETALIATE_OFF) == 1) Unit else null }
        tele(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z)
        locOp(ArchiveRaids.ARCHIVE_DOOR, 4123, 5562, ArchiveRaids.ENTER)
        await("into the instance") { position?.takeIf { server.world.instances.inDynamicArea(it.x, it.z) } }
        return assertNotNull(server.world.parties.of(server.world.playerNamed(username)!!)?.let(server.world.archiveRaids::of))
    }

    /** Through the Guardian's hall, Ezekiel's room and Moia's (each done as it's walked into, none built in this world). */
    private fun HeadlessClient.toFourthRow(run: ArchiveRun) {
        for (room in listOf(ArchiveRoom.GUARDIAN, ArchiveRoom.EZEKIEL, ArchiveRoom.MOIA)) {
            commandReply("raid tp ${room.name.lowercase()}") { it.startsWith("To ") }
            await("${room.boss}'s room done") { room.takeIf { it in run.cleared } }
        }
    }

    /** Onto the floor where the room's transporter puts a player. */
    private fun HeadlessClient.ontoFloor(server: TestServer, run: ArchiveRun) {
        val at = server.world.archiveRaids.tile(run, Dagonhai.ARENA_ENTRY_X, Dagonhai.ARENA_ENTRY_Z)
        tele(at.x, at.z)
    }

    private fun lines(run: ArchiveRun): List<String> = run.fights[ArchiveRoom.DAGONHAI]?.lines?.toList().orEmpty()

    private fun HeadlessClient.awaitLine(run: ArchiveRun, what: String, timeoutMillis: Long = 20_000, predicate: (String) -> Boolean): String =
        try {
            await(what, timeoutMillis) { lines(run).lastOrNull(predicate) }
        } catch (e: AssertionError) {
            throw AssertionError("${e.message}; the fight's last lines: ${lines(run).takeLast(16)}", e)
        }

    private fun tickOf(line: String): Long = line.substringBefore(' ').toLong()

    private fun members(run: ArchiveRun): List<Npc> =
        assertNotNull(run.fights[ArchiveRoom.DAGONHAI]).adds.filter { it.spawn.type.id in Dagonhai.MEMBERS.map { m -> m.type } }

    private fun HeadlessClient.begin(server: TestServer, run: ArchiveRun) {
        toFourthRow(run)
        commandReply("raid tp dagonhai") { it.startsWith("To ") }
        awaitTicks(3)
        assertFalse(ArchiveRoom.DAGONHAI in run.started, "their fight begun at the way in")
        ontoFloor(server, run)
        await("their fight") { fight().takeIf { it.startsWith("dagonhai #") } }
    }

    @Test
    fun `the floor, the gust tile, a stunned one's share, and every place on the floor`() {
        assertTrue(Dagonhai.inArena(Dagonhai.X, Dagonhai.Z, Dagonhai.ARENA_ENTRY_X, Dagonhai.ARENA_ENTRY_Z))
        assertFalse(Dagonhai.inArena(Dagonhai.X, Dagonhai.Z, ArchiveRoom.DAGONHAI.entryX, ArchiveRoom.DAGONHAI.entryZ), "the way in")
        assertFalse(Dagonhai.inArena(Dagonhai.X, Dagonhai.Z, 4080, 5599), "the way in's corridor")
        assertTrue(Dagonhai.inArena(Dagonhai.X + 640, Dagonhai.Z + 64, 4054 + 640, 5589 + 64), "shifted with the cauldron")
        for (member in Dagonhai.MEMBERS) for (dx in 0 until Dagonhai.SIZE) for (dz in 0 until Dagonhai.SIZE) {
            assertTrue(Dagonhai.inArena(Dagonhai.X, Dagonhai.Z, Dagonhai.X + member.dx + dx, Dagonhai.Z + member.dz + dz))
        }
        for (spot in Dagonhai.RIFT_SPOTS) assertTrue(Dagonhai.inArena(Dagonhai.X, Dagonhai.Z, Dagonhai.X + spot.x, Dagonhai.Z + spot.z), "$spot")
        // The north Monk's gust: two steps south of his south edge, towards the cauldron's middle; direction south (4).
        val north = Dagonhai.MEMBERS[1]
        assertEquals(Tile(Dagonhai.X + north.dx, Dagonhai.Z + north.dz - 2) to 4, Dagonhai.interceptTile(Dagonhai.X + north.dx, Dagonhai.Z + north.dz, Dagonhai.X, Dagonhai.Z))
        // The south Monk's: two steps north of his north edge, north (0).
        val south = Dagonhai.MEMBERS[2]
        assertEquals(Tile(Dagonhai.X + south.dx, Dagonhai.Z + south.dz + 1 + 2) to 0, Dagonhai.interceptTile(Dagonhai.X + south.dx, Dagonhai.Z + south.dz, Dagonhai.X, Dagonhai.Z))
        assertEquals(100, Dagonhai.poolLoss(100, stunned = false))
        assertEquals(115, Dagonhai.poolLoss(100, stunned = true))
        assertEquals(4079, Dagonhai.spotChannel(1, Element.FIRE))
        assertEquals(4101, Dagonhai.spotChannel(3, Element.EARTH))
    }

    @Test
    fun `their fight begins on the floor, the pool is shared, the Elder fires, a channel is stopped by its element and another fills the charge`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.begin(server, run)
                val fight = assertNotNull(run.fights[ArchiveRoom.DAGONHAI])
                val cauldronNpc = fight.boss
                assertEquals(server.world.archiveRaids.tile(run, Dagonhai.X, Dagonhai.Z), Tile(cauldronNpc.spawn.x, cauldronNpc.spawn.z))
                val trio = members(run)
                assertEquals(3, trio.size)
                assertTrue(alice.fight().contains("pool 640/640, Elder 640 hp, Monk 640 hp, Monk 640 hp; stability 1000/1000, charge 0%"), alice.fight())
                // A hit on one comes off all three.
                alice.commandReply("hit 100 ${trio[1].index}") { it.startsWith("Hit ") }
                alice.await("the pool at 540") { if (trio.all { it.hitpoints == 540 }) Unit else null }
                // The Elder fires every six ticks at her.
                alice.awaitLine(run, "his attack") { it.contains("ritual: a shot at alice") || it.contains("ritual: a surge at alice") }

                // The first channel; a spell of its element landing stops it and stuns the channeller.
                val began = alice.awaitLine(run, "a channel", 30_000) { it.contains(" channels ") }
                val number = began.substringAfter("member ").substringBefore(' ').toInt()
                val element = Element.valueOf(began.substringAfter("channels ").uppercase())
                val channeller = trio.first { it.spawn.type.id == Dagonhai.MEMBERS[number - 1].type }
                alice.commandReply("pause") { it.contains("aused after tick") }
                val player = server.world.playerNamed("alice")!!
                // A splash lets it through; the wrong element too.
                for (listener in server.world.magicCombat.landings) listener(player, channeller, fireBolt.copy(element = element), false)
                for (listener in server.world.magicCombat.landings) listener(player, channeller, fireBolt.copy(element = Element.entries.first { it != element }), true)
                alice.stepTicks(1)
                assertTrue(lines(run).none { it.contains("stops member") }, lines(run).takeLast(6).toString())
                for (listener in server.world.magicCombat.landings) listener(player, channeller, fireBolt.copy(element = element), true)
                alice.stepTicks(1)
                assertTrue(lines(run).any { it.contains("alice stops member $number's ${element.name.lowercase()} channel; stunned") }, lines(run).takeLast(6).toString())
                // Stunned, it takes 15% more: 100 off it is 115 off the pool.
                val before = trio[0].hitpoints
                alice.command("hit 100 ${channeller.index}")
                alice.stepTicks(2)
                assertEquals(before - 115, trio[0].hitpoints, lines(run).takeLast(6).toString())
                alice.commandReply("resume") { it.startsWith("Resumed") }
                // The next channel, left alone, goes into the cauldron: 25% charge.
                alice.awaitLine(run, "a channel into the cauldron", 40_000) { it.contains("channel goes into the cauldron") }
                alice.awaitLine(run, "the charge") { it.endsWith("ritual: charge 25%") }
            }
        }
    }

    @Test
    fun `a tome blocked on the gust hits the blocker, one left alone reaches the cauldron`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.begin(server, run)
                // The first search: stand on the gust it shows.
                val search = alice.awaitLine(run, "a search", 40_000) { it.contains(" searches for a ") }
                val gust = search.substringAfter("the gust at ").split(',').map(String::toInt)
                alice.tele(gust[0], gust[1])
                val god = search.substringAfter("searches for a ").substringBefore(" tome")
                alice.awaitLine(run, "the block") { it.endsWith("ritual: alice blocks the $god tome") }
                assertTrue(lines(run).none { it.contains("reaches the cauldron") })
                // The next, left alone (back at the floor's east edge), reaches the cauldron and adds to its charge.
                alice.ontoFloor(server, run)
                alice.awaitLine(run, "a tome reaching the cauldron", 60_000) { it.contains(" tome reaches the cauldron") }
                alice.awaitLine(run, "its charge") { it.contains("ritual: charge ") }
            }
        }
    }

    @Test
    fun `each break opens rifts, one drained by a player beside it, and after the fifth a Monk dies and the two left on 7 do the room`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.begin(server, run)
                val fight = assertNotNull(run.fights[ArchiveRoom.DAGONHAI])
                val trio = members(run)
                // The first break: stability down 200, two rifts, the pool full again.
                alice.commandReply("hit 640 ${trio[0].index}") { it.startsWith("Hit ") }
                alice.awaitLine(run, "the first break") { it.endsWith("ritual: shield break 1; stability 800") }
                alice.await("two rifts") { if (fight.adds.count { it.type.id == Dagonhai.RIFT } == 2) Unit else null }
                alice.await("the pool full again") { if (trio.all { it.hitpoints == Dagonhai.POOL }) Unit else null }
                assertEquals(800, fight.boss.hitpoints)
                // An imp out of a rift 5 ticks on.
                alice.awaitLine(run, "an imp") { it.contains("an imp out of the rift") }
                // Beside one, its imps stay in and it drains, and so does her prayer.
                val riftNpc = fight.adds.first { it.type.id == Dagonhai.RIFT }
                val prayer = server.world.playerNamed("alice")!!.skills.currentLevel(Skill.PRAYER)
                alice.tele(riftNpc.x + 1, riftNpc.z)
                alice.awaitLine(run, "the rift drained") { it.endsWith("ritual: a rift closes, drained") }
                assertTrue(server.world.playerNamed("alice")!!.skills.currentLevel(Skill.PRAYER) < prayer)
                // Four more breaks: after the fifth the cauldron bursts.
                for (n in 2..Dagonhai.BREAKS) {
                    alice.await("the pool full for break $n") { if (trio.first { !it.dead }.hitpoints == Dagonhai.POOL) Unit else null }
                    alice.commandReply("hit 640 ${trio[0].index}") { it.startsWith("Hit ") }
                    alice.awaitLine(run, "break $n") { it.contains("ritual: shield break $n;") }
                }
                alice.awaitLine(run, "the burst") { it.endsWith("ritual: the cauldron bursts: a Monk dies") }
                alice.awaitLine(run, "the two left") { it.endsWith("ritual: 2 left on 7, stunned") }
                val left = trio.filter { !it.dead }
                assertEquals(2, left.size)
                assertTrue(left.any { it.spawn.type.id == Dagonhai.ELDER }, "the Elder left")
                assertTrue(left.all { it.hitpoints == Dagonhai.WEAKENED_HITPOINTS && it.maxHitpoints == Dagonhai.WEAKENED_HITPOINTS })
                assertTrue(left.all { it.type.id in listOf(Dagonhai.ELDER_WEAKENED, Dagonhai.MONK_1_WEAKENED, Dagonhai.MONK_2_WEAKENED) }, "their weakened forms")
                // The two killed: the room is done.
                for (npc in left) alice.commandReply("hit 7 ${npc.index}") { it.startsWith("Hit ") }
                alice.await("their room done") { ArchiveRoom.DAGONHAI.takeIf { it in run.cleared } }
            }
        }
    }
}
