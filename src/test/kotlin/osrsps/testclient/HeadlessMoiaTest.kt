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
import osrsps.game.World
import osrsps.game.combat.Deaths
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.Poison
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.Npc
import osrsps.game.raid.ArchiveRaids
import osrsps.game.raid.ArchiveRoom
import osrsps.game.raid.ArchiveRun
import osrsps.game.raid.Moia
import osrsps.game.raid.PartyScaling
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Moia (raid step 19) through the headless client: her fight beginning with the first of the party onto her room's
 * round floor, scaled to the party; her binders round the ring but for one slot, hitting a player within two tiles and
 * rising again after they die; her daggers every five ticks, her cough infecting a player, the infection hurting and
 * draining more each time, spreading to whoever is next to them and halved for one immune to poison; her teleport and
 * the magic and ranged daggers after it; her enrage at a third, with its diamond of binders whose charge is broken by
 * killing them and bursts on everyone if they live; and her death doing the room. The rl-client script
 * test-runner/examples/archive-moia.json fights her in the real client.
 */
class HeadlessMoiaTest {
    private val moia = NpcType(
        Moia.BOSS, "Moia", size = 3, ops = listOf(null, "Attack", null, null, null), internalName = "tfa_moia",
        stats = NpcStats(attack = 1, defence = 195, strength = 1, hitpoints = 1900, ranged = 200, magic = 350),
    )
    private val binder = NpcType(
        Moia.BINDER, "Undead binder", ops = listOf(null, "Attack", null, null, null), internalName = "tfa_moia_minion",
        stats = NpcStats(attack = 120, defence = 225, strength = 120, hitpoints = 95, ranged = 1, magic = 50),
    )
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))
    private val types = NpcTypes.of(moia, binder, grave)

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
            encounterDefs = listOfNotNull(Moia.def(types)), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    /** `::raid`'s line on the fight the player is in. */
    private fun HeadlessClient.fight(): String = commandReply("raid") { it.startsWith("moia #") || it.startsWith("You're not") }

    /** [clients] into one party, the first leading. */
    private fun party(clients: List<Pair<HeadlessClient, String>>) {
        val (leader, leaderName) = clients.first()
        for ((client, name) in clients.drop(1)) {
            leader.commandReply("raidparty invite $name") { it.startsWith("Invited") }
            client.commandReply("raidparty accept $leaderName") { it.startsWith("You joined") }
        }
    }

    /** Into the run, under `::god`, from the lobby's Archive Door, with auto retaliate off so they stay where they're put. */
    private fun HeadlessClient.intoRun(server: TestServer, username: String): ArchiveRun {
        await("the login rebuild") { position }
        commandReply("god on") { it.startsWith("You can't be hurt") }
        // Enough Prayer for the infection's drains to show whole.
        commandReply("setlevel prayer 99") { it.contains("set to level 99") }
        command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
        await("auto retaliate off") { if (varp(EquipmentTab.VARP_AUTO_RETALIATE_OFF) == 1) Unit else null }
        tele(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z)
        locOp(ArchiveRaids.ARCHIVE_DOOR, 4123, 5562, ArchiveRaids.ENTER)
        await("into the instance") { position?.takeIf { server.world.instances.inDynamicArea(it.x, it.z) } }
        return assertNotNull(server.world.parties.of(server.world.playerNamed(username)!!)?.let(server.world.archiveRaids::of))
    }

    /** Through the Guardian's hall and Ezekiel's room (both done as they're walked into, neither built in this world). */
    private fun HeadlessClient.toThirdRow(run: ArchiveRun) {
        commandReply("raid tp guardian") { it.startsWith("To ") }
        await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
        commandReply("raid tp ezekiel") { it.startsWith("To ") }
        await("Ezekiel's room done") { ArchiveRoom.EZEKIEL.takeIf { it in run.cleared } }
    }

    /** Onto her arena's east edge, a few tiles in from the way in, where her binders don't reach. */
    private fun HeadlessClient.ontoArena(server: TestServer, run: ArchiveRun, dz: Int = 0) {
        val at = server.world.archiveRaids.tile(run, 4080, 5663 + dz)
        tele(at.x, at.z)
    }

    private fun lines(run: ArchiveRun): List<String> = run.fights[ArchiveRoom.MOIA]?.lines?.toList().orEmpty()

    private fun HeadlessClient.awaitLine(run: ArchiveRun, what: String, timeoutMillis: Long = 20_000, predicate: (String) -> Boolean): String =
        try {
            await(what, timeoutMillis) { lines(run).lastOrNull(predicate) }
        } catch (e: AssertionError) {
            throw AssertionError("${e.message}; the fight's last lines: ${lines(run).takeLast(16)}", e)
        }

    private fun tickOf(line: String): Long = line.substringBefore(' ').toLong()

    private fun boss(run: ArchiveRun): Npc = assertNotNull(run.fights[ArchiveRoom.MOIA]).boss

    private fun binders(run: ArchiveRun): List<Npc> = assertNotNull(run.fights[ArchiveRoom.MOIA]).adds.filter { it.type.id == Moia.BINDER }

    @Test
    fun `her arena is her room's round floor, a binder reaches two tiles, and an infection ramps up`() {
        assertTrue(Moia.inArena(Moia.X, Moia.Z, 4046, 5646))
        assertTrue(Moia.inArena(Moia.X, Moia.Z, 4081, 5680))
        assertTrue(Moia.inArena(Moia.X, Moia.Z, Moia.NAVIGATOR_X, Moia.NAVIGATOR_Z))
        assertFalse(Moia.inArena(Moia.X, Moia.Z, 4082, 5663), "the way in")
        assertFalse(Moia.inArena(Moia.X, Moia.Z, ArchiveRoom.MOIA.entryX, ArchiveRoom.MOIA.entryZ), "where a player comes in")
        assertFalse(Moia.inArena(Moia.X, Moia.Z, 4060, 5681))
        // Shifted with her, as in a run's instance.
        assertTrue(Moia.inArena(Moia.X + 1000, Moia.Z + 64, 4046 + 1000, 5646 + 64))
        assertTrue(Moia.inBinderReach(10, 10, 12, 8))
        assertTrue(Moia.inBinderReach(10, 10, 11, 10))
        assertFalse(Moia.inBinderReach(10, 10, 10, 10), "under it")
        assertFalse(Moia.inBinderReach(10, 10, 13, 10))
        assertEquals(listOf(2, 4, 6, 8, 10, 12), (0 until Moia.INFECTION_HITS).map(Moia::infectionDamage))
        // Each place she teleports to is on her arena whole.
        for (spot in Moia.SPOTS) for (dx in 0 until Moia.SIZE) for (dz in 0 until Moia.SIZE) {
            assertTrue(Moia.inArena(Moia.X, Moia.Z, Moia.X + spot.x + dx, Moia.Z + spot.z + dz), "$spot")
        }
    }

    @Test
    fun `her fight begins with the first onto her floor, with her binders round the ring, her daggers, cough and teleport, and her death does the room`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.toThirdRow(run)
                // At the way into her room nothing begins.
                alice.commandReply("raid tp moia") { it.startsWith("To ") }
                alice.awaitTicks(3)
                assertFalse(ArchiveRoom.MOIA in run.started, "her fight begun at the way in")
                alice.ontoArena(server, run)
                val begun = alice.await("her fight") { alice.fight().takeIf { it.startsWith("moia #") } }
                // A party of one counts as two: the Wiki's 1,900, and every slot of the ring but one taken.
                assertTrue(begun.contains("Moia 1900/1900 hp, ${Moia.RING.size - 1} binders, 0 infected; 1 players"), begun)
                val her = boss(run)
                assertEquals(server.world.archiveRaids.tile(run, Moia.X, Moia.Z), Tile(her.spawn.x, her.spawn.z))
                val middle = Tile(her.spawn.x + 1, her.spawn.z + 1)
                for (b in binders(run)) assertTrue(Moia.RING.any { b.x == middle.x + it.x && b.z == middle.z + it.z }, "a binder off the ring at ${b.x},${b.z}")

                // Her actions every five ticks: a dagger, a dagger, her cough, a dagger, then her teleport.
                val teleported = alice.awaitLine(run, "her teleport", 30_000) { it.endsWith("fight: teleports") }
                val actions = lines(run).filter { it.contains("fight: a dagger at alice") || it.endsWith("fight: coughs at alice") || it.endsWith("fight: teleports") }
                assertEquals(listOf("a dagger", "a dagger", "coughs", "a dagger", "teleports"), actions.map { it.substringAfter("fight: ").substringBefore(" at").removeSuffix(" alice") }, actions.toString())
                val ticks = actions.map(::tickOf)
                assertEquals(ticks.indices.map { ticks.first() + Moia.ATTACK_SPEED * it }, ticks)
                // The cough infected her; the infection hurt 2, then 4, draining prayer as much.
                alice.awaitLine(run, "the infection's second hit") { it.contains("fight: the infection hurts alice for 4") }
                assertTrue(lines(run).any { it.endsWith("fight: alice is infected") })
                assertTrue(lines(run).any { it.contains("fight: the infection hurts alice for 2, 0 dealt, draining 2 prayer") }, lines(run).toString())
                // After the teleport, she comes into sight elsewhere; then a magic dagger and a ranged one.
                val came = alice.awaitLine(run, "her coming back") { it.contains("fight: comes to ") }
                assertEquals(tickOf(teleported) + Moia.TELEPORT_LANDS, tickOf(came))
                assertFalse(her.x == her.spawn.x && her.z == her.spawn.z, "she teleported to where she stood")
                val magic = alice.awaitLine(run, "the magic dagger") { it.endsWith("fight: a magic dagger at alice") }
                val ranged = alice.awaitLine(run, "the ranged dagger after it") { it.endsWith("fight: a dagger at alice") && tickOf(it) > tickOf(magic) }
                assertEquals(tickOf(came) + 1, tickOf(magic))
                assertEquals(tickOf(magic) + Moia.PAIR_GAP, tickOf(ranged))

                // At 0 she dies, and her room is done.
                alice.commandReply("hit 1900 ${her.index}") { it.startsWith("Hit ") }
                alice.await("her room done") { ArchiveRoom.MOIA.takeIf { it in run.cleared } }
            }
        }
    }

    @Test
    fun `a binder hits whoever is within two tiles, and one that dies rises again in its slot`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.toThirdRow(run)
                alice.commandReply("raid tp moia") { it.startsWith("To ") }
                alice.ontoArena(server, run)
                alice.await("her fight") { alice.fight().takeIf { it.startsWith("moia #") } }
                val first = binders(run).minBy { it.index }
                // Two tiles east of it, before its first hit is due and the ring turns.
                alice.tele(first.x + 2, first.z)
                alice.awaitLine(run, "a binder's hit") { it.endsWith("fight: a binder hits at alice") }
                // One killed rises again in its slot 30 ticks on.
                val before = binders(run).size
                alice.commandReply("hit 95 ${first.index}") { it.startsWith("Hit ") }
                val fell = alice.awaitLine(run, "its fall") { it.contains("fight: a binder falls at ") }
                assertEquals(before - 1, binders(run).size)
                val rose = alice.awaitLine(run, "its rising again", 40_000) { it.endsWith("fight: a binder rises again") }
                assertEquals(tickOf(fell) + Moia.BINDER_RETURN, tickOf(rose))
                assertEquals(before, binders(run).size)
            }
        }
    }

    @Test
    fun `her cough spreads to whoever is next to the infected, and one immune to poison takes half`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    party(listOf(alice to "alice", bob to "bob"))
                    val run = alice.intoRun(server, "alice")
                    bob.intoRun(server, "bob")
                    alice.toThirdRow(run)
                    // Bob immune to poison, as an antipoison or a sanfew serum leaves him.
                    bob.commandReply("varp ${Poison.VARP} -20") { it.startsWith("Varp ") }
                    bob.ontoArena(server, run, dz = 1)
                    alice.ontoArena(server, run)
                    alice.await("her fight") { alice.fight().takeIf { it.startsWith("moia #") } }
                    // Whoever her cough lands on, it spreads to the other, next to them, as it first hurts.
                    try {
                        alice.await("both infected", 30_000) { if (listOf("alice", "bob").all { n -> lines(run).any { it.endsWith("fight: $n is infected") } }) Unit else null }
                    } catch (e: AssertionError) {
                        throw AssertionError("${e.message}; the fight's last lines: ${lines(run).takeLast(16)}", e)
                    }
                    alice.awaitLine(run, "the infection on bob") { it.contains("fight: the infection hurts bob for ") }
                    alice.awaitLine(run, "the infection on alice") { it.contains("fight: the infection hurts alice for ") }
                    // His first hit is half of the infection's 2; hers the whole.
                    assertTrue(lines(run).first { it.contains("fight: the infection hurts bob for ") }.contains("hurts bob for 1,"), lines(run).toString())
                    assertTrue(lines(run).first { it.contains("fight: the infection hurts alice for ") }.contains("hurts alice for 2,"), lines(run).toString())
                }
            }
        }
    }

    @Test
    fun `an enrage on the tick after a teleport began leaves her in sight and back in the middle`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.toThirdRow(run)
                alice.commandReply("raid tp moia") { it.startsWith("To ") }
                alice.ontoArena(server, run)
                alice.await("her fight") { alice.fight().takeIf { it.startsWith("moia #") } }
                val her = boss(run)
                // Stepped a tick at a time to the tick her teleport begins.
                alice.commandReply("pause") { it.contains("aused after tick") }
                var steps = 0
                while (lines(run).none { it.endsWith("fight: teleports") }) {
                    assertTrue(++steps < 60, "no teleport in 60 ticks: ${lines(run).takeLast(8)}")
                    alice.stepTicks(1)
                }
                // Down to a third on the next tick, the one she would go from sight on (Codex on #625).
                alice.command("hit ${her.hitpoints - her.maxHitpoints * Moia.ENRAGE_PERCENT / 100} ${her.index}")
                alice.stepTicks(1)
                alice.stepTicks(Moia.TELEPORT_LANDS + 2)
                assertTrue(lines(run).any { it.contains("enrage: enrage at ") }, lines(run).takeLast(8).toString())
                assertFalse(her.hidden, "hidden for good in her enrage")
                assertEquals(her.spawn.x to her.spawn.z, her.x to her.z)
                alice.commandReply("resume") { it.startsWith("Resumed") }
            }
        }
    }

    @Test
    fun `at a third she enrages, with a diamond of binders whose charge breaks when they die and bursts on everyone when they live`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.toThirdRow(run)
                alice.commandReply("raid tp moia") { it.startsWith("To ") }
                alice.ontoArena(server, run)
                alice.await("her fight") { alice.fight().takeIf { it.startsWith("moia #") } }
                val her = boss(run)
                alice.commandReply("hit ${her.hitpoints - her.maxHitpoints * Moia.ENRAGE_PERCENT / 100} ${her.index}") { it.startsWith("Hit ") }
                alice.awaitLine(run, "her enrage") { it.contains("enrage: enrage at ") }
                val risen = alice.awaitLine(run, "the diamond") { it.contains("enrage: 4 binders rise in a diamond") }
                // The ring's binders went; the diamond's four stand round her, back in the middle.
                assertEquals(her.spawn.x to her.spawn.z, her.x to her.z)
                val diamond = binders(run)
                assertEquals(4, diamond.size)
                val middle = Tile(her.x + 1, her.z + 1)
                assertEquals(
                    setOf(Tile(middle.x, middle.z + Moia.DIAMOND), Tile(middle.x + Moia.DIAMOND, middle.z), Tile(middle.x, middle.z - Moia.DIAMOND), Tile(middle.x - Moia.DIAMOND, middle.z)),
                    diamond.map { Tile(it.x, it.z) }.toSet(),
                )
                // All four killed: the charge is broken, and a fresh diamond rises 20 ticks on.
                for (b in diamond) alice.commandReply("hit 95 ${b.index}") { it.startsWith("Hit ") }
                val broken = alice.awaitLine(run, "the charge broken") { it.endsWith("enrage: the charge is broken") }
                assertTrue(tickOf(broken) < tickOf(risen) + Moia.CHARGE_TICKS)
                val fresh = alice.awaitLine(run, "a fresh diamond", 30_000) { it.contains("enrage: 4 binders rise in a diamond") && tickOf(it) > tickOf(broken) }
                assertEquals(tickOf(broken) + Moia.CHARGE_GAP, tickOf(fresh))
                // Left alone, it bursts on her as it fills, and the diamond goes.
                val burst = alice.awaitLine(run, "the burst", 40_000) { it.contains("enrage: the charge bursts on alice for ") }
                assertEquals(tickOf(fresh) + Moia.CHARGE_TICKS, tickOf(burst))
                alice.await("the diamond gone") { if (binders(run).isEmpty()) Unit else null }
                assertTrue(lines(run).none { it.contains("enrage: teleports") }, "a teleport in her enrage")
            }
        }
    }
    @Test
    fun `her room's way in is in her fight, so a step off her floor neither cures the infection nor dodges the charge's burst`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.toThirdRow(run)
                alice.commandReply("raid tp moia") { it.startsWith("To ") }
                alice.ontoArena(server, run)
                alice.await("her fight") { alice.fight().takeIf { it.startsWith("moia #") } }
                alice.awaitLine(run, "infected", 30_000) { it.endsWith("fight: alice is infected") }
                // The way in, east of her floor, with no wall between (exploit review round 26, R26A-2).
                val wayIn = server.world.archiveRaids.tile(run, 4082, 5663)
                assertFalse(Moia.inArena(Moia.X, Moia.Z, 4082, 5663))
                alice.tele(wayIn.x, wayIn.z)
                val hurts = lines(run).count { it.contains("the infection hurts alice") }
                alice.await("the infection hurting on in the way in", 20_000) { if (lines(run).count { it.contains("the infection hurts alice") } > hurts) Unit else null }
                val fight = assertNotNull(run.fights[ArchiveRoom.MOIA])
                assertTrue(fight.def.status!!.invoke(fight).contains("1 infected"), fight.def.status!!.invoke(fight))
                // Her enrage: the charge bursts on her even there.
                val her = boss(run)
                alice.commandReply("hit ${her.hitpoints - her.maxHitpoints * Moia.ENRAGE_PERCENT / 100} ${her.index}") { it.startsWith("Hit ") }
                alice.awaitLine(run, "the diamond") { it.contains("enrage: 4 binders rise in a diamond") }
                alice.awaitLine(run, "the burst", 30_000) { it.contains("the charge bursts on alice") }
            }
        }
    }

    @Test
    fun `damage to her binders counts for her room's points only up to a quarter of everyone's damage to her`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    party(listOf(alice to "alice", bob to "bob"))
                    val run = alice.intoRun(server, "alice")
                    bob.intoRun(server, "bob")
                    alice.toThirdRow(run)
                    bob.ontoArena(server, run, dz = 1)
                    alice.ontoArena(server, run)
                    alice.await("her fight") { alice.fight().takeIf { it.startsWith("moia #") } }
                    // Alice only ever hits binders: three rounds of the ring, waiting for each to rise again (R26A-3).
                    repeat(3) {
                        alice.await("binders up", 40_000) { binders(run).takeIf { it.size == Moia.RING.size - 1 } }
                        for (b in binders(run)) alice.commandReply("hit 95 ${b.index}") { it.startsWith("Hit ") }
                    }
                    // Bob does all of Moia.
                    val her = boss(run)
                    while (!her.dead) {
                        bob.commandReply("hit 500 ${her.index}") { it.startsWith("Hit ") || it.contains("already dead") || it.contains("hidden") }
                        bob.awaitTicks(1)
                    }
                    alice.await("her room done", 20_000) { ArchiveRoom.MOIA.takeIf { it in run.cleared } }
                    val a = server.world.playerNamed("alice")!!.saveKey
                    val b = server.world.playerNamed("bob")!!.saveKey
                    val onBinders = run.minionDamage[ArchiveRoom.MOIA]?.get(a) ?: 0
                    val onHer = run.damage[ArchiveRoom.MOIA]?.get(b) ?: 0
                    val cap = onHer * ArchiveRaids.MINION_CAP_PERCENT / 100
                    assertTrue(onBinders > cap, "alice did $onBinders to binders, bob $onHer to her")
                    assertEquals(null, run.damage[ArchiveRoom.MOIA]?.get(a), "none of alice's damage was to her")
                    // Alice's counts only up to a quarter of Bob's: about 20 of the room's 100 points to his 80.
                    assertEquals(mapOf(b to onHer, a to cap), server.world.archiveRaids.credited(run, ArchiveRoom.MOIA))
                }
            }
        }
    }

    @Test
    fun `the cap on damage to her binders is the room's, shared by all who hit them, not a quarter each`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    server.login("carol").use { carol ->
                        party(listOf(alice to "alice", bob to "bob", carol to "carol"))
                        val run = alice.intoRun(server, "alice")
                        bob.intoRun(server, "bob")
                        carol.intoRun(server, "carol")
                        alice.toThirdRow(run)
                        bob.ontoArena(server, run, dz = 1)
                        carol.ontoArena(server, run, dz = -1)
                        alice.ontoArena(server, run)
                        alice.await("her fight") { alice.fight().takeIf { it.startsWith("moia #") } }
                        // Alice and Carol only ever hit binders, half each, three rounds of the ring (R27A-2: a cap each
                        // let every farmer take a quarter).
                        repeat(3) {
                            alice.await("binders up", 40_000) { binders(run).takeIf { it.size == Moia.RING.size - 1 } }
                            for ((i, b) in binders(run).withIndex()) {
                                (if (i % 2 == 0) alice else carol).commandReply("hit 95 ${b.index}") { it.startsWith("Hit ") }
                            }
                        }
                        val her = boss(run)
                        while (!her.dead) {
                            bob.commandReply("hit 500 ${her.index}") { it.startsWith("Hit ") || it.contains("already dead") || it.contains("hidden") }
                            bob.awaitTicks(1)
                        }
                        alice.await("her room done", 20_000) { ArchiveRoom.MOIA.takeIf { it in run.cleared } }
                        val (a, b, c) = listOf("alice", "bob", "carol").map { server.world.playerNamed(it)!!.saveKey }
                        val onBinders = run.minionDamage[ArchiveRoom.MOIA].orEmpty()
                        val onHer = assertNotNull(run.damage[ArchiveRoom.MOIA]?.get(b))
                        val cap = onHer * ArchiveRaids.MINION_CAP_PERCENT / 100
                        val farmed = onBinders.getValue(a) + onBinders.getValue(c)
                        assertTrue(farmed > cap && onBinders.getValue(a) > 0 && onBinders.getValue(c) > 0, "binders $onBinders, bob $onHer to her")
                        // Between them, no more than the one cap, split as they did the binders.
                        val credited = server.world.archiveRaids.credited(run, ArchiveRoom.MOIA)
                        assertEquals(onHer, credited[b])
                        val shared = credited.getValue(a) + credited.getValue(c)
                        assertTrue(shared in cap - 1..cap, "alice and carol credited $credited, the cap $cap")
                        assertEquals(onBinders.getValue(a).toLong() * cap / farmed, credited.getValue(a).toLong())
                    }
                }
            }
        }
    }
}
