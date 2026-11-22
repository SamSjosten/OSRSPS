package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
import osrsps.game.combat.AttackType
import osrsps.game.combat.NpcCombat
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.tzhaar.FightCaves
import osrsps.game.tzhaar.Inferno
import osrsps.game.tzhaar.InfernoMonster
import osrsps.game.tzhaar.InfernoMonsters
import osrsps.game.tzhaar.InfernoPillars
import osrsps.game.tzhaar.InfernoWaves
import osrsps.game.tzhaar.InfernoZuk
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
        monster(7694, "Jal-AkRek-Mej", 1, "inferno_creature_splitter_mage", 15),
        monster(7695, "Jal-AkRek-Xil", 1, "inferno_creature_splitter_range", 15),
        monster(7696, "Jal-AkRek-Ket", 1, "inferno_creature_splitter_melee", 15),
        monster(7697, "Jal-ImKot", 4, "inferno_creature_melee", 75),
        monster(7698, "Jal-Xil", 3, "inferno_creature_ranger", 125),
        monster(7699, "Jal-Zek", 4, "inferno_creature_mager", 220),
        monster(7700, "JalTok-Jad", 5, "inferno_jad", 350),
        monster(7701, "Yt-HurKot", 1, "inferno_jad_healer", 90),
        monster(7706, "TzKal-Zuk", 7, "inferno_tzkalzuk_placeholder", 1200),
        monster(7702, "Jal-Xil", 3, "inferno_ranger_finalwave", 125),
        monster(7703, "Jal-Zek", 4, "inferno_mager_finalwave", 220),
        monster(7704, "JalTok-Jad", 5, "inferno_jad_finalwave", 350),
        monster(7705, "Yt-HurKot", 1, "inferno_jad_healer_finalwave", 90),
        monster(InfernoZuk.GLYPH, "Ancestral Glyph", 3, "inferno_moving_safespot", 600, ops = listOf(null, null, null, null, null)),
        monster(InfernoZuk.MEJJAK, "Jal-MejJak", 1, "inferno_zuk_healer", 75),
        NpcType(KetKeh.AFTER_ZUK, "TzHaar-Ket-Keh", size = 2, ops = listOf("Talk-to", null, "Exchange", null, null), internalName = "inferno_master_2op"),
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
            ObjType(InfernoZuk.PET, "Jal-nib-rek"),
            ObjType(995, "Coins", stackable = true),
        )
        val loc = { id: Int, name: String, ops: List<String?>, w: Int, l: Int -> LocType(id, name, ops, LocCollisionType(width = w, length = l)) }
        World(
            crcs, CollisionMap().apply { markLoaded(35, 83); markLoaded(38, 80); markLoaded(39, 79); markLoaded(39, 80) }, varTypes,
            objTypes = objTypes, npcTypes = types,
            random = GameRandom(1), npcCombat = NpcCombat.load(types, SeqNames.NONE),
            npcSpawns = listOf(
                NpcSpawn(types[KetKeh.TZHAAR_KET_KEH]!!, 2496, 5115, source = "test"),
                // His form after a Zuk kill, beside him, for Exchange (the test world has no multinpcs).
                NpcSpawn(types[KetKeh.AFTER_ZUK]!!, 2500, 5115, source = "test"),
            ),
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
        assertEquals(69, scenarios["inferno-zuk"]!!.minigame!!.wave)
    }

    // Inferno step IN2: the monsters' own mechanics.

    @Test
    fun `a Jal-MejRah's attacks drain 3 run energy each without Protect from Missiles`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gus").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("inferno 1") { it.startsWith("Inferno") }
                val bat = client.waveIn(server, "gus", 1).single { it.type.id == InfernoMonster.BAT.type }
                assertTrue(bat.scriptedAttacks)
                val player = server.world.playerNamed("gus")!!
                // 3% a hit, less what the player gets back standing between hits.
                client.await("run energy drained", timeoutMillis = 20_000) {
                    player.runEnergy.takeIf { it <= osrsps.game.Movement.MAX_ENERGY - InfernoMonsters.BAT_RUN_DRAIN + 50 }
                }
            }
        }
    }

    @Test
    fun `a Jal-MejRah's drain takes run energy and now and then a combat level, and none through Protect from Missiles`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ned").use { client ->
                client.await("the login rebuild") { client.position }
                val player = server.world.playerNamed("ned")!!
                val stats = listOf(osrsps.game.Skill.ATTACK, osrsps.game.Skill.STRENGTH, osrsps.game.Skill.DEFENCE, osrsps.game.Skill.RANGED, osrsps.game.Skill.MAGIC)
                for (skill in stats) client.commandReply("setlevel ${skill.name.lowercase()} 99") { true }
                client.await("the levels") { stats.all { player.skills.currentLevel(it) == 99 }.takeIf { it } }
                val monsters = server.world.inferno.monsterMechanics
                player.runEnergy = osrsps.game.Movement.MAX_ENERGY
                repeat(40) { monsters.batDrain(player, protected = true) }
                assertEquals(osrsps.game.Movement.MAX_ENERGY, player.runEnergy, "no run energy through the prayer")
                assertTrue(stats.all { player.skills.currentLevel(it) == 99 }, "no levels through the prayer")
                monsters.batDrain(player, protected = false)
                assertEquals(osrsps.game.Movement.MAX_ENERGY - InfernoMonsters.BAT_RUN_DRAIN, player.runEnergy, "3% a hit")
                repeat(40) { monsters.batDrain(player, protected = false) }
                assertTrue(stats.all { player.skills.currentLevel(it) < 99 }, "a level off each combat stat now and then")
                assertTrue(stats.all { player.skills.currentLevel(it) > 99 - 41 }, "not every hit")
            }
        }
    }

    @Test
    fun `a Jal-Ak splits into its three smaller selves as it dies, and the wave waits for them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("hal").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("inferno 4") { it.startsWith("Inferno") }
                val blob = client.waveIn(server, "hal", 4).single { it.type.id == InfernoMonster.BLOB.type }
                val run = server.run("hal")!!
                val player = server.world.playerNamed("hal")!!
                client.commandReply("kill ${blob.index}") { it.startsWith("Killed") }
                val small = client.await("the split") { run.monsters.filter { it.type.id in InfernoMonsters.SPLITS && !it.dead }.takeIf { it.size == 3 } }
                assertEquals(InfernoMonsters.SPLITS.toSet(), small.map { it.type.id }.toSet())
                assertTrue(small.all { it.target === player })
                for (npc in run.monsters.filter { !it.dead && it.type.id == InfernoMonster.NIBBLER.type }) client.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                assertEquals(4, run.wave, "the wave waits for them")
                for (npc in small) client.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                client.await("wave 5") { run.takeIf { it.wave == 5 } }
            }
        }
    }

    @Test
    fun `a Jal-Ak split owed for want of room comes in later and the wave waits for it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("hew").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("inferno 4") { it.startsWith("Inferno") }
                val blob = client.waveIn(server, "hew", 4).single { it.type.id == InfernoMonster.BLOB.type }
                val run = server.run("hew")!!
                for (npc in run.monsters.filter { !it.dead && it.type.id == InfernoMonster.NIBBLER.type }) client.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                // As if one of an earlier split couldn't come in: owed, it comes in with the next free tick.
                run.splitsOwed += InfernoMonsters.SPLITS.first() to osrsps.map.Tile(blob.x, blob.z)
                client.commandReply("kill ${blob.index}") { it.startsWith("Killed") }
                val small = client.await("the split and the owed one") { run.monsters.filter { it.type.id in InfernoMonsters.SPLITS && !it.dead }.takeIf { it.size == 4 } }
                assertTrue(run.splitsOwed.isEmpty())
                assertEquals(4, run.wave, "the wave waits for them")
                for (npc in small) client.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                client.await("wave 5") { run.takeIf { it.wave == 5 } }
            }
        }
    }

    @Test
    fun `a Jal-ImKot that can't reach the player digs and comes up beside them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ida").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("inferno 9") { it.startsWith("Inferno") }
                val imKot = client.waveIn(server, "ida", 9).single { it.type.id == InfernoMonster.MELEE.type }
                val run = server.run("ida")!!
                val player = server.world.playerNamed("ida")!!
                val monsters = server.world.inferno.monsterMechanics
                // Out of its reach in a far corner, standing still, with its dig made due.
                val corner = listOf(run.at(Tile(2258, 5330)), run.at(Tile(2284, 5357))).maxBy { maxOf(Math.abs(it.x - imKot.x), Math.abs(it.z - imKot.z)) }
                client.command("tele ${corner.x} ${corner.z} 0")
                client.await("in the corner") { client.position?.takeIf { it == Coord(corner.x, corner.z, 0) } }
                // Paused and stepped, so a slow machine can't miss the 4-tick dig-down or the 2 ticks under (Codex review on #835).
                client.commandReply("pause") { it.startsWith("Paused after tick") }
                monsters.digNow(imKot)
                client.stepTicks(1)
                assertTrue((monsters.digs[imKot] ?: 0) >= 1, "it digs")
                assertFalse(imKot.hidden, "its dig-down shows before it goes from sight")
                var down = 1
                while (!imKot.hidden && down <= InfernoMonsters.DIG_TICKS + 1) { client.stepTicks(1); down++ }
                assertTrue(imKot.hidden, "under after its dig-down")
                assertTrue(down >= InfernoMonsters.DIG_TICKS, "the dig-down lasted its ticks")
                var under = 0
                while (imKot.hidden && under <= InfernoMonsters.UNDER_TICKS + 1) { client.stepTicks(1); under++ }
                assertFalse(imKot.hidden, "it comes up")
                client.commandReply("resume") { it.startsWith("Resumed") }
                assertTrue(server.world.meleeCombat.reaches(imKot, player), "beside them")
                assertTrue(server.world.npcs.canStand(imKot, imKot.x, imKot.z), "on ground it can stand on")
                assertTrue(imKot.target === player)
            }
        }
    }

    @Test
    fun `a Jal-Ak reads the player's prayer 3 ticks before it attacks, its first attack too`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("kit").use { client ->
                client.await("the login rebuild") { client.position }
                val monsters = server.world.inferno.monsterMechanics
                repeat(50) {
                    assertEquals(AttackType.RANGED, monsters.blobStyle(magic = true, missiles = false, melee = false, inReach = false))
                    assertEquals(AttackType.MAGIC, monsters.blobStyle(magic = false, missiles = true, melee = false, inReach = false))
                    assertTrue(monsters.blobStyle(magic = false, missiles = false, melee = true, inReach = true) in setOf(AttackType.MAGIC, AttackType.RANGED), "no melee through Protect from Melee")
                }
                assertEquals(setOf(AttackType.MAGIC, AttackType.RANGED), (1..200).map { monsters.blobStyle(false, false, false, inReach = false) }.toSet())
                assertTrue((1..200).any { monsters.blobStyle(false, false, false, inReach = true) == AttackType.CRUSH }, "sometimes melee in reach")

                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("inferno 4") { it.startsWith("Inferno") }
                val blob = client.waveIn(server, "kit", 4).single { it.type.id == InfernoMonster.BLOB.type }
                val read = client.await("its first read", timeoutMillis = 30_000) { monsters.readStyle(blob)?.let { it to blob.nextAttackTick } }
                assertTrue(read.second - server.world.tick in 0..InfernoMonsters.BLOB_READS_AHEAD.toLong(), "it attacks within 3 ticks of reading")
                val fired = client.await("its first attack") { monsters.lastStyle[blob] }
                assertEquals(read.first.takeIf { it != AttackType.CRUSH } ?: fired, fired, "it fires the style it read")
            }
        }
    }

    @Test
    fun `a Jal-Xil beside the player uses its melee, even on a diagonal`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("lou").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("inferno 18") { it.startsWith("Inferno") }
                val wave = client.waveIn(server, "lou", 18)
                val monsters = server.world.inferno.monsterMechanics
                val xil = wave.single { it.type.id == InfernoMonster.RANGER.type }
                client.await("the Jal-Xil shoots from afar", timeoutMillis = 20_000) { monsters.lastStyle[xil]?.takeIf { it == AttackType.RANGED } }
                for (npc in wave.filter { it !== xil }) client.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                // On the tile diagonal to its south-west corner.
                client.command("tele ${xil.x - 1} ${xil.z - 1} 0")
                client.await("its melee", timeoutMillis = 20_000) { monsters.lastStyle[xil]?.takeIf { it == AttackType.CRUSH } }
            }
        }
    }

    @Test
    fun `a Jal-Zek beside the player uses its melee`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("max").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("inferno 36") { it.startsWith("Inferno") }
                val wave = client.waveIn(server, "max", 36)
                val monsters = server.world.inferno.monsterMechanics
                val zek = wave.single { it.type.id == InfernoMonster.MAGE.type }
                for (npc in wave.filter { it !== zek }) client.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                client.command("tele ${zek.x + zek.type.size} ${zek.z} 0")
                client.await("its melee", timeoutMillis = 30_000) { monsters.lastStyle[zek]?.takeIf { it == AttackType.STAB } }
            }
        }
    }

    @Test
    fun `a Jal-Zek brings a dead monster of its wave back at half its hitpoints, once`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("jon").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("inferno 36") { it.startsWith("Inferno") }
                val wave = client.waveIn(server, "jon", 36)
                val bat = wave.single { it.type.id == InfernoMonster.BAT.type }
                val run = server.run("jon")!!
                client.commandReply("kill ${bat.index}") { it.startsWith("Killed") }
                val back = client.await("the bat back", timeoutMillis = 30_000) {
                    run.monsters.firstOrNull { it.type.id == InfernoMonster.BAT.type && it !== bat && !it.dead }
                }
                val at = run.at(InfernoMonsters.REVIVE_TILE)
                assertEquals(at.x to at.z, back.spawn.x to back.spawn.z)
                assertTrue(back.hitpoints <= back.maxHitpoints / 2)
                assertTrue(run.slain.isEmpty(), "nothing left to bring back")
                client.commandReply("kill ${back.index}") { it.startsWith("Killed") }
                client.await("its death not counted again") { run.takeIf { back.dead } }
                assertTrue(run.slain.isEmpty(), "a revived monster isn't revived twice")
            }
        }
    }

    // Inferno step IN3: the JalTok-Jads and their healers.

    @Test
    fun `wave 68's three JalTok-Jads attack three ticks apart, each every 9 ticks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("kim").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("inferno 68") { it.startsWith("Inferno") }
                val jads = client.waveIn(server, "kim", 68)
                assertEquals(List(3) { InfernoMonster.JAD.type }, jads.map { it.type.id })
                val firsts = jads.map { server.world.inferno.jads.firstAttack(it)!! }
                assertEquals(listOf(0L, 3L, 6L), firsts.map { it - firsts[0] })
                assertTrue(jads.all { server.world.inferno.jads.attackSpeed(it) == osrsps.game.tzhaar.InfernoJads.TRIPLE_SPEED && it.scriptedAttacks })
                // Whether or not each could make its attacks, every one stays on its beat, 3 ticks from the others' (Codex
                // review on #849).
                val speed = osrsps.game.tzhaar.InfernoJads.TRIPLE_SPEED
                client.await("two rounds", timeoutMillis = 20_000) { jads.takeIf { j -> j.all { it.nextAttackTick > firsts[0] + 2 * speed } } }
                for ((i, jad) in jads.withIndex()) assertEquals(0L, (jad.nextAttackTick - firsts[i]) % speed, "Jad ${i + 1} on its beat")
            }
        }
    }

    @Test
    fun `wave 67's JalTok-Jad summons five healers below half health, who heal him, and they go when he dies`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("lee").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("inferno 67") { it.startsWith("Inferno") }
                val jad = client.waveIn(server, "lee", 67).single()
                assertEquals(osrsps.game.tzhaar.InfernoJads.ATTACK_SPEED, server.world.inferno.jads.attackSpeed(jad))
                client.commandReply("hit 200 ${jad.index}") { it.startsWith("Hit") }
                val healers = client.await("five healers") { server.world.inferno.jads.healersOf(jad).takeIf { it.size == 5 } }
                assertTrue(healers.all { it.type.id == osrsps.game.tzhaar.InfernoJads.HEALER && it.target == null })
                // No regeneration, so only a healer's 15-24 can lift him (Codex review on #849).
                jad.hitpointsRegenTicks = Int.MAX_VALUE
                client.await("a heal", timeoutMillis = 20_000) { jad.hitpoints.takeIf { it >= 150 + osrsps.game.tzhaar.InfernoJads.HEAL_MIN } }
                client.commandReply("kill ${jad.index}") { it.startsWith("Killed") }
                client.await("the healers gone") { healers.takeIf { h -> h.none { server.world.npcs[it.index] === it } } }
                client.await("wave 68 coming") { server.run("lee")?.takeIf { it.wave == 68 } }
            }
        }
    }

    // Inferno step IN4: TzKal-Zuk's fight and TzHaar-Ket-Keh's Exchange.

    @Test
    fun `wave 69 shakes the cavern, the Ancestral Glyph walks its track, and Zuk's shots hit a player out of its cover`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("max").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel hitpoints 99") { true }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("inferno 69") { it.startsWith("Inferno") }
                client.waveIn(server, "max", 69)
                client.awaitDialogue("the cavern shaking") { it is DialogueView.Message && "inferno_zuk.shaking" in it.text }
                client.next("TzHaar-Ket-Keh") { it is DialogueView.Chat && "inferno_zuk.breaking" in it.text }
                val run = server.run("max")!!
                val fight = server.world.inferno.zuk.fightOf(run)!!
                val glyph = fight.glyph!!
                assertEquals(InfernoZuk.GLYPH, glyph.type.id)
                assertTrue(glyph !in run.monsters, "the glyph is no monster of the wave")
                val start = glyph.x
                client.await("the glyph moving") { glyph.takeIf { it.x != start } }
                val player = server.world.playerNamed("max")!!
                assertFalse(server.world.inferno.zuk.covered(run, player), "the arena's south end has no cover")
                assertTrue(server.world.combat.shutOut(player, glyph), "no player attack reaches the glyph, an area one neither")
                client.commandReply("god") { it.startsWith("You can be hurt") }
                client.await("a hit from Zuk", timeoutMillis = 20_000) { player.takeIf { it.hitpoints < 99 } }
            }
        }
    }

    @Test
    fun `the sets go for the glyph until attacked, 600 pauses their timer, 480 brings the Jad and 240 the MejJaks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ned").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("inferno 69") { it.startsWith("Inferno") }
                val zuk = client.waveIn(server, "ned", 69).single()
                val run = server.run("ned")!!
                val fight = server.world.inferno.zuk.fightOf(run)!!
                val zukFight = server.world.inferno.zuk
                // The first set, as the glyph gets back to where it set off.
                val set = client.await("the first set", timeoutMillis = 20_000) {
                    run.monsters.filter { it.type.id == InfernoZuk.FINAL_RANGER || it.type.id == InfernoZuk.FINAL_MAGER }.takeIf { it.size == 2 }
                }
                assertTrue(set.all { zukFight.onGlyph(run, it) })
                client.await("the glyph hit") { fight.glyph!!.takeIf { it.hitpoints < 600 } }
                val ranger = set.first { it.type.id == InfernoZuk.FINAL_RANGER }
                client.commandReply("hit 1 ${ranger.index}") { it.startsWith("Hit") }
                client.await("the ranger off the glyph") { ranger.takeIf { !zukFight.onGlyph(run, it) } }
                assertTrue(fight.nextSet > server.world.tick)

                client.commandReply("hit 601 ${zuk.index}") { it.startsWith("Hit") }
                client.await("the timer paused") { fight.takeIf { it.setPausedLeft > InfernoZuk.INTERLUDE_TICKS } }
                client.commandReply("hit 120 ${zuk.index}") { it.startsWith("Hit") }
                val jad = client.await("the Jad") { run.monsters.firstOrNull { it.type.id == InfernoZuk.FINAL_JAD } }
                assertTrue(zukFight.onGlyph(run, jad))
                assertEquals(-1L, fight.setPausedLeft, "the timer runs again")

                client.commandReply("hit 240 ${zuk.index}") { it.startsWith("Hit") }
                client.await("four MejJaks") { fight.takeIf { it.enraged && it.mejJaks.size == 4 } }
                client.await("a heal") { zuk.takeIf { it.hitpoints > 239 } }
                client.commandReply("kill ${zuk.index}") { it.startsWith("Killed") }
                client.await("everything gone with him", timeoutMillis = 20_000) {
                    (fight.mejJaks + jad + set).takeIf { all -> all.none { server.world.npcs[it.index] === it } }
                }
            }
        }
    }

    @Test
    fun `600 reached before the first set holds that set back until 480, with the 1-45 added`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ora").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("inferno 69") { it.startsWith("Inferno") }
                val zuk = client.waveIn(server, "ora", 69).single()
                val fight = server.world.inferno.zuk.fightOf(server.run("ora")!!)!!
                client.commandReply("hit 601 ${zuk.index}") { it.startsWith("Hit") }
                client.await("the pause") { fight.takeIf { it.setPausedLeft == InfernoZuk.INTERLUDE_TICKS } }
                client.await("the glyph round", timeoutMillis = 20_000) { fight.takeIf { it.glyphTurns >= 2 } }
                client.awaitTicks(2)
                assertEquals(-1L, fight.nextSet, "no set while paused")
                client.commandReply("hit 120 ${zuk.index}") { it.startsWith("Hit") }
                client.await("the timer running") { fight.takeIf { it.setPausedLeft < 0 && it.nextSet >= 0 } }
                assertTrue(fight.nextSet > server.world.tick + InfernoZuk.INTERLUDE_TICKS - 10, "the 1:45 added")
            }
        }
    }

    @Test
    fun `TzHaar-Ket-Keh's Exchange takes an infernal cape for a chance at Jal-Nib-Rek, never with the pet owned`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("oz").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(2500, 5112)
                val keh = client.await("TzHaar-Ket-Keh in view") { client.npcs().firstOrNull { it.id == KetKeh.AFTER_ZUK } }
                client.npcOp(keh.index, KetKeh.EXCHANGE)
                client.awaitDialogue("no cape") { it is DialogueView.Chat && "ket_keh.no_infernal" in it.text }
                client.continueDialogue()

                client.commandReply("item ${Inferno.INFERNAL_CAPE} 1") { true }
                client.await("the cape") { client.inventory(INV).firstOrNull { it?.id == Inferno.INFERNAL_CAPE } }
                client.npcOp(keh.index, KetKeh.EXCHANGE)
                client.awaitDialogue("the offer") { it is DialogueView.Chat && "ket_keh.exchange_offer" in it.text }
                client.next("give it up?") { it is DialogueView.Menu }
                client.chooseOption(1)
                val outcome = client.awaitDialogue("the toss") { it is DialogueView.Chat && ("ket_keh.lucky" in it.text || "ket_keh.unlucky" in it.text) }
                assertTrue(client.inventory(INV).none { it?.id == Inferno.INFERNAL_CAPE }, "the cape is gone")
                val won = "ket_keh.lucky" in (outcome as DialogueView.Chat).text
                assertEquals(won, client.inventory(INV).any { it?.id == InfernoZuk.PET })
                client.continueDialogue()

                if (!won) client.commandReply("item ${InfernoZuk.PET} 1") { true }
                client.commandReply("item ${Inferno.INFERNAL_CAPE} 1") { true }
                client.await("the pet") { client.inventory(INV).firstOrNull { it?.id == InfernoZuk.PET } }
                client.npcOp(keh.index, KetKeh.EXCHANGE)
                client.awaitDialogue("the pet owned") { it is DialogueView.Chat && "ket_keh.have_pet" in it.text }
                assertNotNull(client.inventory(INV).firstOrNull { it?.id == Inferno.INFERNAL_CAPE }, "the cape kept")
                client.continueDialogue()

                // In a test setup (whose kit can hold an infernal cape) he takes none, pet or not.
                client.command("empty")
                client.commandReply("item ${Inferno.INFERNAL_CAPE} 1") { true }
                client.await("only the cape") { client.inventory(INV).takeIf { inv -> inv.none { it?.id == InfernoZuk.PET } && inv.any { it?.id == Inferno.INFERNAL_CAPE } } }
                server.world.playerNamed("oz")!!.scenario = "raid-tester"
                client.npcOp(keh.index, KetKeh.EXCHANGE)
                client.await("the line") { client.messages().firstOrNull { "ket_keh.in_scenario" in it.text } }
                assertNotNull(client.inventory(INV).firstOrNull { it?.id == Inferno.INFERNAL_CAPE }, "the cape kept in a setup")
            }
        }
    }

    private companion object {
        const val CENTREPIECE = 10
        const val INV = 93
    }
}
