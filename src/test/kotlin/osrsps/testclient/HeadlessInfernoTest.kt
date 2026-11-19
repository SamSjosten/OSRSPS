package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Scenario
import osrsps.game.Scenarios
import osrsps.game.SeqNames
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.tzhaar.FightCaves
import osrsps.game.tzhaar.Inferno
import osrsps.game.tzhaar.InfernoMonster
import osrsps.game.tzhaar.InfernoPillars
import osrsps.game.tzhaar.InfernoWaves
import osrsps.game.tzhaar.KetKeh
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.Tile

/**
 * Inferno step IN1 through the headless client: TzHaar-Ket-Keh taking a fire cape, which opens the entrance; Jump-in
 * into an Inferno of one's own with its three rocky supports; the Nibblers going for the supports, a support's collapse
 * hurting whoever stands beside it, and the Nibblers turning on the player once none stand; the supports falling as
 * wave 66 ends; a death and the exit paying TokKul; a logout in a wave pausing the run; TzKal-Zuk's death handing over
 * the infernal cape. The rl-client script test-runner/examples/inferno.json goes in on the real map.
 */
class HeadlessInfernoTest {
    private fun monster(id: Int, name: String, size: Int, internal: String, hitpoints: Int, ops: List<String?> = listOf(null, "Attack", null, null, null)) = NpcType(
        id, name, size = size, ops = ops, combatLevel = 100, internalName = internal,
        stats = NpcStats(attack = 20, strength = 30, defence = 15, hitpoints = hitpoints),
    )

    private val types = NpcTypes.of(
        monster(7691, "Jal-Nib", 1, "inferno_nibbler", 10),
        monster(7692, "Jal-MejRah", 2, "inferno_creature_harpie", 25),
        monster(7693, "Jal-Ak", 3, "inferno_creature_splitter", 40),
        monster(7697, "Jal-ImKot", 4, "inferno_creature_melee", 75),
        monster(7698, "Jal-Xil", 3, "inferno_creature_ranger", 125),
        monster(7699, "Jal-Zek", 4, "inferno_creature_mager", 220),
        monster(7700, "JalTok-Jad", 5, "inferno_jad", 350),
        monster(7706, "TzKal-Zuk", 7, "inferno_tzkalzuk_placeholder", 1200),
        monster(InfernoPillars.SUPPORT, "Rocky support", 3, "inferno_invisible_3x3", 1, ops = listOf(null, null, null, null, null)),
        monster(InfernoPillars.COLLAPSING, "Rocky support", 3, "inferno_safespot_dying", 1, ops = listOf(null, null, null, null, null)),
        NpcType(KetKeh.TZHAAR_KET_KEH, "TzHaar-Ket-Keh", size = 2, ops = listOf("Talk-to", null, null, null, null), internalName = "inferno_master"),
    )

    /** The cache's Inferno varbits: the fire cape given, and each support's damage. */
    private val varTypes = VarTypes(
        6_000,
        mapOf(
            Inferno.VARBIT_SACRIFICED to VarbitType(1570, 0, 1),
            InfernoPillars.VARBITS[0] to VarbitType(1573, 0, 7),
            InfernoPillars.VARBITS[1] to VarbitType(1573, 8, 15),
            InfernoPillars.VARBITS[2] to VarbitType(1573, 16, 23),
        ),
        20_000,
    )

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val objTypes = ObjTypes.of(
            ObjType(FightCaves.TOKKUL, "Tokkul", stackable = true),
            ObjType(FightCaves.FIRE_CAPE, "Fire cape"),
            ObjType(Inferno.INFERNAL_CAPE, "Infernal cape"),
            ObjType(995, "Coins", stackable = true),
        )
        val loc = { id: Int, name: String, ops: List<String?>, w: Int, l: Int -> LocType(id, name, ops, LocCollisionType(width = w, length = l)) }
        World(
            crcs, CollisionMap().apply { markLoaded(35, 83); markLoaded(38, 80); markLoaded(39, 79); markLoaded(39, 80) }, varTypes,
            objTypes = objTypes, npcTypes = types,
            random = GameRandom(1), npcCombat = NpcCombat.load(types, SeqNames.NONE),
            npcSpawns = listOf(NpcSpawn(types[KetKeh.TZHAAR_KET_KEH]!!, 2496, 5115, source = "test")),
            locTypes = LocTypes(
                (
                    listOf(
                        loc(Inferno.ENTRANCE, "null", listOf(null, null, null, null, null), 6, 6).copy(multiloc = true),
                        loc(Inferno.EXIT, "Cave exit", listOf("Exit", "Quick-exit", null, null, null), 5, 4),
                    ) + InfernoPillars.LOCS.map { loc(it, "null", listOf(null, null, null, null, null), 3, 3) }
                    ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Inferno.ENTRANCE, CENTREPIECE, 0, 2493, 5124, 0))
                add(ZoneLoc(Inferno.EXIT, CENTREPIECE, 0, 2269, 5325, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun TestServer.run(name: String): Inferno.Run? = world.playerNamed(name)?.let { world.inferno.runOf(it) }

    /** The run's monsters once its wave [wave] has come in. */
    private fun HeadlessClient.waveIn(server: TestServer, name: String, wave: Int) = await("wave $wave", timeoutMillis = 20_000) {
        server.run(name)?.takeIf { it.wave == wave && it.nextWaveTick < 0 && it.monsters.isNotEmpty() }?.monsters?.toList()
    }

    private fun HeadlessClient.tokkul(): Int = inventory(INV).filterNotNull().filter { it.id == FightCaves.TOKKUL }.sumOf { it.count }

    private fun HeadlessClient.next(description: String, predicate: (DialogueView) -> Boolean): DialogueView {
        continueDialogue()
        return awaitDialogue(description, predicate = predicate)
    }

    @Test
    fun `TzHaar-Ket-Keh takes a fire cape, and the entrance then jumps into an Inferno of one's own with its three supports`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ash").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(2496, 5122)
                val player = server.world.playerNamed("ash")!!
                // Not given yet: Jump-in does nothing.
                client.locOp(Inferno.ENTRANCE, 2493, 5124, Inferno.JUMP_IN)
                client.await("the click handled") { client.position }
                assertNull(server.run("ash"))

                client.commandReply("item ${FightCaves.FIRE_CAPE} 1") { true }
                val keh = client.await("TzHaar-Ket-Keh in view") { client.npcs().firstOrNull { it.id == KetKeh.TZHAAR_KET_KEH } }
                client.npcOp(keh.index, KetKeh.TALK_TO)
                client.awaitDialogue("his greeting") { it is DialogueView.Chat && "ket_keh.greeting" in it.text && "JalYt-" in it.text }
                val menu = client.next("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(3, menu.options.size)
                client.chooseOption(2)
                client.awaitDialogue("can I help") { it is DialogueView.Chat && "ket_keh.help" in it.text }
                client.next("prove it") { it is DialogueView.Chat && "ket_keh.prove" in it.text }
                client.next("his word") { it is DialogueView.Chat && "ket_keh.take_word" in it.text }
                client.next("sacrifice it?") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.awaitDialogue("the player's offer") { it is DialogueView.Chat && "ket_keh.sacrifice" in it.text }
                client.next("hand it over") { it is DialogueView.Chat && "ket_keh.hand_over" in it.text }
                client.next("really?") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.awaitDialogue("handed over") { it is DialogueView.Message && "ket_keh.handed" in it.text }
                assertTrue(client.inventory(INV).none { it?.id == FightCaves.FIRE_CAPE }, "the cape is gone")
                assertTrue(server.world.inferno.sacrificed(player))
                client.next("you can enter") { it is DialogueView.Chat && "ket_keh.enter_now" in it.text }

                client.locOp(Inferno.ENTRANCE, 2493, 5124, Inferno.JUMP_IN)
                val run = client.await("the run") { server.run("ash") }
                val arrival = run.at(Inferno.ARRIVAL)
                client.await("in the Inferno") { client.position?.takeIf { it == Coord(arrival.x, arrival.z, 0) } }
                assertTrue(server.world.instances.at(arrival.x, arrival.z) === run.instance, "an Inferno of their own")
                assertTrue(server.world.multiCombat.at(0, arrival.x, arrival.z), "multi-way")
                assertEquals(3, run.pillars.size)
                for (pillar in run.pillars) {
                    val at = run.at(InfernoPillars.TILES[pillar.index])
                    assertEquals(at.x to at.z, pillar.npc.x to pillar.npc.z)
                    assertEquals(InfernoPillars.HITPOINTS, pillar.npc.hitpoints)
                    assertNotNull(server.world.locs.find(InfernoPillars.LOCS[pillar.index], at.x, at.z, 0), "the support's loc")
                }

                val wave = client.waveIn(server, "ash", 1)
                assertEquals(InfernoWaves.monsters(1).map { it.type }.sorted(), wave.map { it.type.id }.sorted())
                client.await("wave 1's line") { client.messages().firstOrNull { "inferno.wave" in it.text } }
                val bat = wave.single { it.type.id == InfernoMonster.BAT.type }
                assertTrue(bat.target === player, "the bat goes for the player")
                assertTrue(wave.filter { it.type.id == InfernoMonster.NIBBLER.type }.all { it.target == null && it.scriptedAttacks }, "the Nibblers don't")
                val middle = run.at(InfernoWaves.NIBBLER_TILE)
                assertTrue(wave.filter { it.type.id == InfernoMonster.NIBBLER.type }.all { maxOf(Math.abs(it.spawn.x - middle.x), Math.abs(it.spawn.z - middle.z)) <= 1 })
            }
        }
    }

    @Test
    fun `a player's hit doesn't turn a Nibbler from the supports, which no player's attack reaches, never heal, and come in when there's room`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cat").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("inferno 3") { it.startsWith("Inferno") }
                val nibblers = client.waveIn(server, "cat", 3)
                val run = server.run("cat")!!
                val player = server.world.playerNamed("cat")!!
                for (pillar in run.pillars) {
                    assertNotNull(server.world.combat.barred(player, pillar.npc), "no player's attack reaches a support")
                    assertTrue(pillar.npc.hitpointsRegenTicks > 1_000_000, "a support's regeneration never comes round")
                }

                val nibbler = nibblers.first { it.type.id == InfernoMonster.NIBBLER.type }
                val hitAt = nibbler.x to nibbler.z
                server.world.meleeCombat.hit(nibbler, player, 0, server.world.tick)
                client.await("back after the supports") { nibbler.takeIf { it.target == null && it.leader != null } }
                assertTrue(run.pillars.any { it.npc === nibbler.leader }, "following a support")
                client.await("on its way, not held where it was hit", timeoutMillis = 3_000) { nibbler.takeIf { (it.x to it.z) != hitAt } }

                // A support that couldn't come in for want of room comes in once there is, with the damage it had.
                val south = run.pillars.single { it.index == 2 }
                server.world.removeNpc(south.npc)
                server.world.locs.remove(south.loc)
                run.pillars.remove(south)
                run.pillarsOwed += 2
                val back = client.await("the owed support in") { run.pillars.firstOrNull { it.index == 2 } }
                assertTrue(run.pillarsOwed.isEmpty())
                val at = run.at(InfernoPillars.TILES[2])
                assertEquals(at.x to at.z, back.npc.x to back.npc.z)
                assertNotNull(server.world.locs.find(InfernoPillars.LOCS[2], at.x, at.z, 0), "its loc")
            }
        }
    }

    @Test
    fun `the Nibblers wear the supports down, a collapse halves whoever stands beside it, and with none left they turn on the player`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bea").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("inferno 3") { it.startsWith("Inferno") }
                val nibblers = client.waveIn(server, "bea", 3)
                assertEquals(6, nibblers.size)
                val run = server.run("bea")!!
                val player = server.world.playerNamed("bea")!!
                client.await("a support hit", timeoutMillis = 30_000) { run.pillars.firstOrNull { it.damage > 0 } }
                val hurt = run.pillars.first { it.damage > 0 }
                assertEquals(hurt.damage, server.world.inferno.pillars.damage(player, hurt.index), "the varbit keeps its damage")

                // Beside the west support as it falls: half of 10 hitpoints.
                val west = run.pillars.single { it.index == 0 }
                client.tele(west.npc.x + InfernoPillars.SIZE, west.npc.z)
                client.commandReply("kill ${west.npc.index}") { it.startsWith("Killed") }
                client.await("the support down") { run.takeIf { r -> r.pillars.none { it.index == 0 } } }
                assertNull(server.world.locs.find(InfernoPillars.LOCS[0], west.npc.x, west.npc.z, 0), "its loc gone")
                assertEquals(5, client.await("the collapse's damage") { player.hitpoints.takeIf { it < 10 } })
                assertEquals(InfernoPillars.HITPOINTS, server.world.inferno.pillars.damage(player, 0))

                client.commandReply("setlevel hitpoints 99") { true }
                client.commandReply("god") { it.startsWith("You can") }
                for (pillar in run.pillars.toList()) client.commandReply("kill ${pillar.npc.index}") { it.startsWith("Killed") }
                client.await("no supports") { run.pillars.isEmpty().takeIf { it } }
                client.await("the Nibblers on the player", timeoutMillis = 20_000) {
                    run.monsters.filter { !it.dead }.takeIf { m -> m.isNotEmpty() && m.all { it.target === player } }
                }
            }
        }
    }

    @Test
    fun `the supports still standing fall as wave 66 ends, and none come back for wave 67`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cal").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("inferno 66") { it.startsWith("Inferno") }
                val wave = client.waveIn(server, "cal", 66)
                assertEquals(listOf(InfernoMonster.MAGE.type, InfernoMonster.MAGE.type), wave.map { it.type.id }.filter { it != InfernoMonster.NIBBLER.type })
                val run = server.run("cal")!!
                assertEquals(3, run.pillars.size)
                client.commandReply("inferno next") { it.startsWith("Took") }
                client.await("wave 67 coming") { run.takeIf { it.wave == 67 } }
                assertTrue(run.pillars.isEmpty(), "every support down")
                val jad = client.waveIn(server, "cal", 67).single()
                assertEquals(InfernoMonster.JAD.type, jad.type.id)
                assertTrue(run.pillars.isEmpty())
            }
        }
    }

    @Test
    fun `a death in the Inferno is safe, pays for the waves done, and the next run starts from wave 1`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dee").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("item 995 7") { true }
                client.commandReply("inferno 4") { it.startsWith("Inferno") }
                client.waveIn(server, "dee", 4)
                client.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                client.await("outside after the death", timeoutMillis = 20_000) {
                    client.position?.takeIf { it == Coord(Inferno.OUTSIDE.x, Inferno.OUTSIDE.z, 0) }
                }
                assertEquals(InfernoWaves.tokkul(3), client.await("the TokKul") { client.tokkul().takeIf { it > 0 } })
                assertEquals(7, client.inventory(INV).filterNotNull().single { it.id == 995 }.count, "nothing lost")
                assertTrue(server.world.deaths.all.isEmpty(), "no grave")
                assertNull(server.run("dee"))
                assertEquals(0, server.world.playerNamed("dee")!!.vars[server.world.customVars.infernoVarp])
            }
        }
    }

    @Test
    fun `the exit pays for the waves done, and a logout in a wave pauses the run for the next login`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("eve").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("inferno 3") { it.startsWith("Inferno") }
                val wave = client.waveIn(server, "eve", 3)
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the pause and its warning") {
                    client.messages().map { it.text }.takeIf { m -> m.any { "inferno.logout_pause" in it } && m.any { "inferno.logout_repeat" in it } }
                }
                for (npc in wave) client.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                client.await("the logout at the wave's end") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.login("eve").use { client ->
                client.await("the login rebuild") { client.position }
                val run = client.await("the run back") { server.run("eve") }
                assertEquals(4, run.wave)
                val middle = run.at(Inferno.MIDDLE)
                client.await("in the middle") { client.position?.takeIf { it == Coord(middle.x, middle.z, 0) } }
                assertEquals(3, run.pillars.size, "the supports as they were")
                client.waveIn(server, "eve", 4)
                val exit = run.at(Tile(2269, 5325))
                client.locOp(Inferno.EXIT, exit.x, exit.z, Inferno.QUICK_EXIT)
                client.await("outside") { client.position?.takeIf { it == Coord(Inferno.OUTSIDE.x, Inferno.OUTSIDE.z, 0) } }
                assertEquals(InfernoWaves.tokkul(3), client.await("the TokKul") { client.tokkul().takeIf { it > 0 } })
                client.await("the Inferno gone") { server.world.instances.all.isEmpty().takeIf { it } }
            }
        }
    }

    @Test
    fun `TzKal-Zuk's death ends the run with the infernal cape and 16,440 TokKul`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fay").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("inferno 69") { it.startsWith("Inferno") }
                val zuk = client.waveIn(server, "fay", 69).single()
                assertEquals(InfernoMonster.ZUK.type, zuk.type.id)
                assertTrue(server.run("fay")!!.pillars.isEmpty())
                client.commandReply("kill ${zuk.index}") { it.startsWith("Killed") }
                client.await("outside", timeoutMillis = 20_000) { client.position?.takeIf { it == Coord(Inferno.OUTSIDE.x, Inferno.OUTSIDE.z, 0) } }
                assertEquals(Inferno.WIN_TOKKUL, client.await("the TokKul") { client.tokkul().takeIf { it > 0 } })
                assertNotNull(client.inventory(INV).firstOrNull { it?.id == Inferno.INFERNAL_CAPE })
                client.awaitDialogue("TzHaar-Ket-Keh") { it is DialogueView.Chat && "ket_keh.impressive" in it.text }
            }
        }
    }

    @Test
    fun `a hit landing on TzKal-Zuk after the player died is no win, and the death stays safe`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gus").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("inferno 69") { it.startsWith("Inferno") }
                val zuk = client.waveIn(server, "gus", 69).single()
                val player = server.world.playerNamed("gus")!!
                val tick = server.world.tick
                server.world.meleeCombat.hitPlayer(player, null, player.hitpoints, tick)
                server.world.meleeCombat.hit(zuk, null, zuk.hitpoints, tick, finishing = true)
                client.await("outside", timeoutMillis = 20_000) { client.position?.takeIf { it == Coord(Inferno.OUTSIDE.x, Inferno.OUTSIDE.z, 0) } }
                client.await("the run over") { (server.run("gus") == null).takeIf { it } }
                assertTrue(client.inventory(INV).none { it?.id == Inferno.INFERNAL_CAPE }, "no cape")
                if (player.vars.types.varpCount > Inferno.VARP_ZUK_KILLS) assertEquals(0, player.vars[Inferno.VARP_ZUK_KILLS], "no kill")
                assertTrue(client.messages().none { "ket_keh.impressive" in it.text })
            }
        }
    }

    @Test
    fun `the entrance turns away a player in a test setup`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ivy").use { client ->
                client.await("the login rebuild") { client.position }
                val player = server.world.playerNamed("ivy")!!
                server.world.inferno.sacrifice(player)
                player.scenario = "raid-tester"
                server.world.inferno.enter(player)
                assertNull(server.run("ivy"), "no Inferno")
                client.await("the line") { client.messages().firstOrNull { "inferno.in_scenario" in it.text } }
                player.scenario = null
                server.world.inferno.enter(player)
                assertNotNull(server.run("ivy"))
            }
        }
    }

    @Test
    fun `the wave table and the Inferno scenarios`() {
        assertEquals(InfernoWaves.WAVES, (1..InfernoWaves.WAVES).count { InfernoWaves.monsters(it).isNotEmpty() })
        for (wave in listOf(3, 8, 17, 34)) assertEquals(List(6) { InfernoMonster.NIBBLER }, InfernoWaves.monsters(wave))
        assertEquals(List(3) { InfernoMonster.NIBBLER } + listOf(InfernoMonster.RANGER, InfernoMonster.MELEE, InfernoMonster.BLOB, InfernoMonster.BAT), InfernoWaves.monsters(29))
        assertEquals(List(3) { InfernoMonster.NIBBLER } + listOf(InfernoMonster.MAGE, InfernoMonster.RANGER, InfernoMonster.MELEE, InfernoMonster.BLOB, InfernoMonster.BAT, InfernoMonster.BAT), InfernoWaves.monsters(62))
        assertEquals(List(3) { InfernoMonster.JAD }, InfernoWaves.monsters(68))
        assertEquals(listOf(InfernoMonster.ZUK), InfernoWaves.monsters(69))
        assertTrue((1..66).all { w -> InfernoWaves.monsters(w).count { it == InfernoMonster.NIBBLER } in setOf(3, 6) })
        assertTrue((67..69).all { w -> InfernoWaves.monsters(w).none { it == InfernoMonster.NIBBLER } })
        assertTrue((1..68).all { w -> InfernoWaves.monsters(w).count { it != InfernoMonster.NIBBLER } <= osrsps.game.tzhaar.InfernoSpot.entries.size })

        val scenarios = Scenarios.load()
        assertEquals(Scenario.ScenarioMinigame.INFERNO, scenarios["inferno"]!!.minigame!!.name)
        assertEquals(1, scenarios["inferno"]!!.minigame!!.wave)
        assertEquals(66, scenarios["inferno-wave66"]!!.minigame!!.wave)
    }

    private companion object {
        const val CENTREPIECE = 10
        const val INV = 93
    }
}
