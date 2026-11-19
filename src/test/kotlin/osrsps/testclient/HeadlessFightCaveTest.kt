package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Scenario
import osrsps.game.Scenarios
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.Skill
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.Prayers
import osrsps.game.tzhaar.FightCaveMonsters
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.tzhaar.FightCaveMonster
import osrsps.game.tzhaar.FightCaveWaves
import osrsps.game.tzhaar.FightCaves
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Fight Caves step FC1 through the headless client: the cave entrance into a cave of one's own, the waves coming in at
 * the rotation's spots one after another, the exit and a death paying Tokkul for the waves done, a logout clicked in a
 * wave pausing the run at its end and the next login starting the next wave, a second click starting the same wave
 * again, and TzTok-Jad's death handing over the fire cape. The rl-client script test-runner/examples/fight-cave.json
 * goes in on the real map.
 */
class HeadlessFightCaveTest {
    private fun monster(id: Int, name: String, size: Int, level: Int, internal: String, hitpoints: Int) = NpcType(
        id, name, size = size, ops = listOf("Attack", null, null, null, null), combatLevel = level, internalName = internal,
        stats = NpcStats(attack = 20, strength = 30, defence = 15, hitpoints = hitpoints),
    )

    private val types = NpcTypes.of(
        monster(3116, "Tz-Kih", 1, 22, "tzhaar_fightcave_swarm_1a", 10),
        monster(3117, "Tz-Kih", 1, 22, "tzhaar_fightcave_swarm_1b", 10),
        monster(3118, "Tz-Kek", 2, 45, "tzhaar_fightcave_swarm_2a", 20),
        monster(3119, "Tz-Kek", 2, 45, "tzhaar_fightcave_swarm_2b", 20),
        monster(3120, "Tz-Kek", 1, 22, "tzhaar_fightcave_swarm_2spawn", 10),
        monster(3121, "Tok-Xil", 3, 90, "tzhaar_fightcave_swarm_3a", 40),
        monster(3122, "Tok-Xil", 3, 90, "tzhaar_fightcave_swarm_3b", 40),
        monster(3123, "Yt-MejKot", 4, 180, "tzhaar_fightcave_swarm_4a", 80),
        monster(3124, "Yt-MejKot", 4, 180, "tzhaar_fightcave_swarm_4b", 80),
        monster(3125, "Ket-Zek", 5, 360, "tzhaar_fightcave_swarm_5a", 160),
        monster(3126, "Ket-Zek", 5, 360, "tzhaar_fightcave_swarm_5b", 160),
        monster(3127, "TzTok-Jad", 5, 702, "tzhaar_fightcave_swarm_boss", 250),
        NpcType(FightCaves.MEJ_JAL, "TzHaar-Mej-Jal", ops = listOf("Talk-to", null, "Exchange fire cape", null, null), internalName = "tzhaar_fightcave_master"),
    )

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val objTypes = ObjTypes.of(
            ObjType(FightCaves.TOKKUL, "Tokkul", stackable = true),
            ObjType(FightCaves.FIRE_CAPE, "Fire cape"),
            ObjType(995, "Coins", stackable = true),
            // Protect from Melee's prayer obj, shaped like the cache's (HeadlessArchiveGuardianTest).
            ObjType(
                PROTECT_FROM_MELEE_OBJ, "Null", internalName = "placeholder_xmas16_marionette_blue",
                params = mapOf(630 to 14, 1751 to (Prayers.PRAYERBOOK shl 16 or PROTECT_FROM_MELEE), 1752 to "Protect from Melee", 1753 to 43, 1755 to 2676),
            ),
        )
        val cave = { id: Int, name: String, ops: List<String?> -> LocType(id, name, ops, LocCollisionType(width = 3, length = 2)) }
        World(
            crcs, CollisionMap().apply { markLoaded(37, 79); markLoaded(38, 80) }, objTypes = objTypes, npcTypes = types,
            random = GameRandom(1), npcCombat = NpcCombat.load(types, SeqNames.NONE), prayerList = Prayers.load(objTypes),
            locTypes = LocTypes(
                listOf(
                    cave(FightCaves.ENTRANCE, "Cave entrance", listOf("Enter", null, null, null, null)),
                    cave(FightCaves.EXIT, "Cave entrance", listOf("Leave", "Escape", null, null, null)),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(FightCaves.ENTRANCE, CENTREPIECE, 0, 2437, 5166, 0))
                add(ZoneLoc(FightCaves.EXIT, CENTREPIECE, 0, 2412, 5118, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun TestServer.run(name: String): FightCaves.Run? = world.playerNamed(name)?.let { world.fightCaves.runOf(it) }

    /** The run's living monsters once its wave [wave] has come in. */
    private fun HeadlessClient.waveIn(server: TestServer, name: String, wave: Int) = await("wave $wave", timeoutMillis = 20_000) {
        server.run(name)?.takeIf { it.wave == wave && it.nextWaveTick < 0 && it.monsters.isNotEmpty() }?.monsters?.toList()
    }

    private fun HeadlessClient.tokkul(): Int = inventory(INV).filterNotNull().filter { it.id == FightCaves.TOKKUL }.sumOf { it.count }

    @Test
    fun `the entrance makes a cave of one's own and the waves come in at the rotation's spots, one after another`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ash").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(2438, 5168)
                client.locOp(FightCaves.ENTRANCE, 2437, 5166, 1)
                val run = client.await("the run") { server.run("ash") }
                val arrival = run.at(FightCaves.ARRIVAL)
                client.await("in the cave") { client.position?.takeIf { it == Coord(arrival.x, arrival.z, 0) } }
                assertTrue(server.world.instances.at(arrival.x, arrival.z) === run.instance, "a cave of their own")
                assertTrue(run.rotation in 1..FightCaveWaves.ROTATIONS)

                val first = client.waveIn(server, "ash", 1).single()
                assertEquals(FightCaveMonster.TZ_KIH.first, first.type.id)
                val spot = run.at(FightCaveWaves.spots(1, run.rotation).single().tile)
                assertEquals(spot.x to spot.z, first.spawn.x to first.spawn.z)
                client.await("wave 1's line") { client.messages().firstOrNull { "fight_cave.wave" in it.text } }
                assertTrue(server.world.multiCombat.at(0, arrival.x, arrival.z), "the cave is multi-way")

                client.commandReply("kill ${first.index}") { it.startsWith("Killed") }
                val second = client.waveIn(server, "ash", 2)
                assertEquals(listOf(FightCaveMonster.TZ_KIH.first, FightCaveMonster.TZ_KIH.second), second.map { it.type.id })
                val spots = FightCaveWaves.spots(2, run.rotation).map { run.at(it.tile) }
                assertEquals(spots.map { it.x to it.z }, second.map { it.spawn.x to it.spawn.z })
                assertTrue(second.all { it.target === server.world.playerNamed("ash") }, "they go for the player")

                // Out by the exit, one wave done: 2 Tokkul, and the cave goes.
                val exit = run.at(osrsps.map.Tile(2412, 5118))
                client.locOp(FightCaves.EXIT, exit.x, exit.z, 1)
                client.await("outside") { client.position?.takeIf { it == Coord(FightCaves.OUTSIDE.x, FightCaves.OUTSIDE.z, 0) } }
                assertEquals(2, client.await("the Tokkul") { client.tokkul().takeIf { it > 0 } })
                assertNull(server.run("ash"))
                client.await("the cave gone") { server.world.instances.all.isEmpty().takeIf { it } }
                assertTrue(second.none { server.world.npcs[it.index] === it }, "its monsters went with it")
                assertEquals(0, server.world.playerNamed("ash")!!.vars[server.world.customVars.fightCaveVarp])
            }
        }
    }

    @Test
    fun `leaving the cave drops the shots its monsters have on their way`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cyd").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                // Wave 7 is a Tok-Xil alone.
                client.commandReply("fightcave 7 1") { it.startsWith("Fight cave") }
                client.waveIn(server, "cyd", 7)
                client.await("a shot on its way", timeoutMillis = 20_000) { server.world.meleeCombat.shotsInFlight.takeIf { it > 0 } }
                client.commandReply("fightcave leave") { true }
                client.await("the run over") { true.takeIf { server.run("cyd") == null } }
                assertEquals(0, server.world.meleeCombat.shotsInFlight)
            }
        }
    }

    @Test
    fun `a shot from the cave doesn't land on a player who teleported out before it arrived`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dot").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel hitpoints 99") { true }
                client.commandReply("fightcave 7 1") { it.startsWith("Fight cave") }
                client.waveIn(server, "dot", 7)
                val player = server.world.playerNamed("dot")!!
                // Wait for a shot to leave, then teleport out with it on its way (a ::tele, which lands at once).
                client.await("a shot on its way", timeoutMillis = 20_000) { server.world.meleeCombat.shotsInFlight.takeIf { it > 0 } }
                val hp = player.hitpoints
                client.tele(2438, 5170)
                client.await("the shot gone") { true.takeIf { server.world.meleeCombat.shotsInFlight == 0 } }
                assertEquals(hp, player.hitpoints, "no hit outside")
            }
        }
    }

    @Test
    fun `a death settled as the player leaves the world pays the TokKul that won't fit into the bank`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("eve").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("item ${FightCaves.FIRE_CAPE} 28") { true }
                client.await("a full backpack") { client.inventory(INV).takeIf { inv -> inv.count { it != null } == 28 } }
                client.commandReply("fightcave 4 2") { it.startsWith("Fight cave") }
                client.waveIn(server, "eve", 4)
                val player = server.world.playerNamed("eve")!!
                // As a logout or World.stop() finishes a death: the run marked died, then settled as they go.
                server.world.fightCaves.runOf(player)!!.died = true
                server.world.fightCaves.leaving(player)
                // Three waves done: 2 + 4 + 6, all in the bank.
                assertEquals(12L, player.bank.count(FightCaves.TOKKUL))
                assertNull(server.run("eve"))
            }
        }
    }

    @Test
    fun `TokKul a death's settling has no room for comes at the next login, not in a test setup`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ivy").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("item ${FightCaves.FIRE_CAPE} 28") { true }
                client.await("a full backpack") { client.inventory(INV).takeIf { inv -> inv.count { it != null } == 28 } }
                client.commandReply("fightcave 4 2") { it.startsWith("Fight cave") }
                client.waveIn(server, "ivy", 4)
                val player = server.world.playerNamed("ivy")!!
                // A bank with every slot taken, none of them TokKul.
                for (slot in 0 until Inventory.BANK_CAPACITY) player.bank[slot] = InvObj(995, 1)
                server.world.fightCaves.runOf(player)!!.died = true
                server.world.fightCaves.leaving(player)
                assertEquals(0L, player.bank.count(FightCaves.TOKKUL) + player.inventory.count(FightCaves.TOKKUL))
                assertEquals(12, server.world.fightCaves.owed(player), "kept for later")
                assertNull(server.world.fightCaves.saved(player), "no run to come back to")
                // Room again, but a login in a test setup doesn't pay it: putting the setup back would owe it again.
                player.inventory[0] = null
                player.scenario = "fight-cave"
                server.world.fightCaves.login(player)
                assertEquals(12, server.world.fightCaves.owed(player))
                assertEquals(0L, player.inventory.count(FightCaves.TOKKUL))
                // Out of it, the next login pays it.
                player.scenario = null
                server.world.fightCaves.login(player)
                assertEquals(12L, player.inventory.count(FightCaves.TOKKUL))
                assertEquals(0, server.world.fightCaves.owed(player))
            }
        }
    }

    @Test
    fun `the cave entrance turns away a player in a test setup`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("lou").use { client ->
                client.await("the login rebuild") { client.position }
                val player = server.world.playerNamed("lou")!!
                player.scenario = "raid-tester"
                server.world.fightCaves.enter(player)
                assertNull(server.run("lou"), "no cave")
                client.await("the line") { client.messages().firstOrNull { "fight_cave.in_scenario" in it.text } }
                player.scenario = null
                server.world.fightCaves.enter(player)
                assertNotNull(server.run("lou"))
            }
        }
    }

    @Test
    fun `a death in the cave is safe and pays for the waves done`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bea").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("item 995 7") { true }
                client.commandReply("fightcave 4 2") { it.startsWith("Fight cave") }
                client.waveIn(server, "bea", 4)
                client.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                client.await("outside after the death", timeoutMillis = 20_000) {
                    client.position?.takeIf { it == Coord(FightCaves.OUTSIDE.x, FightCaves.OUTSIDE.z, 0) }
                }
                // Three waves done: 2 + 4 + 6.
                assertEquals(12, client.await("the Tokkul") { client.tokkul().takeIf { it > 0 } })
                assertEquals(7, client.inventory(INV).filterNotNull().single { it.id == 995 }.count, "nothing lost")
                assertTrue(server.world.deaths.all.isEmpty(), "no grave")
                assertNull(server.run("bea"))
            }
        }
    }

    @Test
    fun `logout in a wave pauses the run at its end, and the next login starts the next wave in the middle of the cave`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cal").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("fightcave 2 1") { it.startsWith("Fight cave") }
                val wave = client.waveIn(server, "cal", 2)
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the pause and its warning") {
                    client.messages().map { it.text }.takeIf { m -> m.any { "fight_cave.logout_pause" in it } && m.any { "fight_cave.logout_repeat" in it } }
                }
                assertTrue(client.packets().none { it.prot.name == "LOGOUT" }, "not out before the wave ends")
                for (npc in wave) client.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                client.await("the logout at the wave's end") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.login("cal").use { client ->
                client.await("the login rebuild") { client.position }
                val run = client.await("the run back") { server.run("cal") }
                assertEquals(3, run.wave)
                assertEquals(1, run.rotation)
                val middle = run.at(FightCaves.MIDDLE)
                client.await("in the middle") { client.position?.takeIf { it == Coord(middle.x, middle.z, 0) } }
                val next = client.waveIn(server, "cal", 3)
                assertEquals(listOf(FightCaveMonster.TZ_KEK.first), next.map { it.type.id })
            }
        }
    }

    @Test
    fun `logging out again before the wave ends starts that wave over at the next login`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dee").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("fightcave 1 1") { it.startsWith("Fight cave") }
                client.waveIn(server, "dee", 1)
                // The Tz-Kih comes in across the cave, so nothing has hit them yet and the second click logs them out.
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the pause") { client.messages().firstOrNull { "fight_cave.logout_pause" in it.text } }
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.login("dee").use { client ->
                client.await("the login rebuild") { client.position }
                assertEquals(1, client.await("the run back") { server.run("dee") }.wave)
                assertEquals(FightCaveMonster.TZ_KIH.first, client.waveIn(server, "dee", 1).single().type.id)
            }
        }
    }

    @Test
    fun `TzTok-Jad's death ends the run with the fire cape and every wave's Tokkul`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("eve").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("fightcave 63 5") { it.startsWith("Fight cave") }
                val jad = client.waveIn(server, "eve", 63).single()
                assertEquals(FightCaveMonster.TZTOK_JAD.first, jad.type.id)
                val run = server.run("eve")!!
                val spot = run.at(FightCaveWaves.spots(3, 5).single().tile)
                assertEquals(spot.x to spot.z, jad.spawn.x to jad.spawn.z, "where wave 3's monster comes in")
                client.commandReply("kill ${jad.index}") { it.startsWith("Killed") }
                client.await("outside", timeoutMillis = 20_000) { client.position?.takeIf { it == Coord(FightCaves.OUTSIDE.x, FightCaves.OUTSIDE.z, 0) } }
                assertEquals(8_032, client.await("the Tokkul") { client.tokkul().takeIf { it > 0 } })
                assertNotNull(client.inventory(INV).firstOrNull { it?.id == FightCaves.FIRE_CAPE })
                client.awaitDialogue("TzHaar-Mej-Jal") { it is DialogueView.Chat && "fight_cave.victorious" in it.text }
            }
        }
    }

    @Test
    fun `the Fight Cave scenarios put the player in at their wave`() {
        val scenarios = Scenarios.load()
        val jad = scenarios["fight-cave-jad"]!!.minigame!!
        assertEquals(Scenario.ScenarioMinigame.FIGHT_CAVE, jad.name)
        assertEquals(63 to 1, jad.wave to jad.rotation)
        assertEquals(1 to null, scenarios["fight-cave"]!!.minigame!!.let { it.wave to it.rotation })
        assertEquals(31, scenarios["fight-cave-wave31"]!!.minigame!!.wave)
    }

    // Fight Caves step FC2: the monsters' own mechanics.

    @Test
    fun `a Tz-Kih's swings drain a prayer point each, hit or not`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fay").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel prayer 10") { true }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("fightcave 1 1") { it.startsWith("Fight cave") }
                val bat = client.waveIn(server, "fay", 1).single()
                assertTrue(bat.scriptedAttacks)
                val player = server.world.playerNamed("fay")!!
                client.await("two points drained", timeoutMillis = 20_000) { player.skills.currentLevel(Skill.PRAYER).takeIf { it <= 8 } }
                assertEquals(player.skills.baseLevel(Skill.HITPOINTS), player.hitpoints, "god: every hit a 0, and still drained")
            }
        }
    }

    @Test
    fun `a level 45 Tz-Kek recoils 1 on each melee hit, and splits into two level 22s that the wave waits for`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gus").use { client ->
                client.await("the login rebuild") { client.position }
                for (skill in listOf("attack", "strength", "hitpoints", "prayer")) client.commandReply("setlevel $skill 99") { true }
                client.commandReply("fightcave 3 1") { it.startsWith("Fight cave") }
                val kek = client.waveIn(server, "gus", 3).single()
                assertEquals(3118, kek.type.id)
                client.ifButton(Prayers.PRAYERBOOK, PROTECT_FROM_MELEE, 1)
                client.await("Protect from Melee on") { if (client.varp(Prayers.VARP_PRAYERS) == 1 shl 14) Unit else null }
                val player = server.world.playerNamed("gus")!!
                client.tele(kek.x - 1, kek.z)
                client.await("it in view") { client.npcs().firstOrNull { it.index == kek.index } }
                client.npcOp(kek.index, 1)
                // Its own swings are all 0s under the prayer: what they lose is the recoil.
                client.await("the recoil", timeoutMillis = 20_000) { player.hitpoints.takeIf { it < 99 } }
                assertTrue(client.actorEvents().filter { !it.npc && it.kind == ActorEventKind.HITSPLAT && it.amount > 0 }.all { it.amount == 1 })

                client.commandReply("kill ${kek.index}") { it.startsWith("Killed") }
                val run = server.run("gus")!!
                val small = client.await("the split") { run.monsters.filter { it.type.id == FightCaveMonsters.TZ_KEK_22 && !it.dead }.takeIf { it.size == 2 } }
                assertTrue(small.all { it.target === player && !it.scriptedAttacks })
                assertEquals(setOf(kek.x to kek.z, kek.x + 1 to kek.z), small.map { it.x to it.z }.toSet())
                assertEquals(3, run.wave, "the wave waits for them")
                for (npc in small) client.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                client.await("wave 4") { run.takeIf { it.wave == 4 } }
            }
        }
    }

    @Test
    fun `a split owed for want of room comes in later and the wave waits for it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ned").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("fightcave 3 1") { it.startsWith("Fight cave") }
                val kek = client.waveIn(server, "ned", 3).single()
                val run = server.run("ned")!!
                // As if a split's level 22 couldn't come in when it died (the world's NPCs all taken).
                run.splitsOwed += osrsps.map.Tile(kek.x, kek.z)
                client.commandReply("kill ${kek.index}") { it.startsWith("Killed") }
                // The split's two, and the owed one a tick later.
                val small = client.await("the owed one in") {
                    run.monsters.filter { it.type.id == FightCaveMonsters.TZ_KEK_22 && !it.dead }.takeIf { it.size == 3 }
                }
                assertTrue(run.splitsOwed.isEmpty())
                assertEquals(3, run.wave, "the wave waits for it")
                for (npc in small.take(2)) client.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                assertEquals(3, run.wave, "still waiting for the owed one")
                client.commandReply("kill ${small[2].index}") { it.startsWith("Killed") }
                client.await("wave 4") { run.takeIf { it.wave == 4 } }
            }
        }
    }

    @Test
    fun `a Tok-Xil fires from afar and punches in reach, and a Ket-Zek both stabs and casts in reach`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("hal").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("fightcave 7 1") { it.startsWith("Fight cave") }
                val xil = client.waveIn(server, "hal", 7).single()
                val player = server.world.playerNamed("hal")!!
                client.await("its spines", timeoutMillis = 20_000) { player.attackedBy?.takeIf { it === xil } }
                assertTrue(!server.world.meleeCombat.reaches(xil, player), "fired from where it stood")
                client.tele(xil.x - 1, xil.z)
                client.await("its punch", timeoutMillis = 20_000) {
                    client.actorEvents().firstOrNull { it.npc && it.index == xil.index && it.kind == ActorEventKind.ANIMATION && it.id == FightCaveMonsters.ANIM_TOK_XIL_PUNCH }
                }

                client.commandReply("fightcave 31 1") { it.startsWith("Fight cave") }
                val zek = client.waveIn(server, "hal", 31).single()
                assertEquals(3125, zek.type.id)
                client.tele(zek.x - 1, zek.z)
                client.await("its stab", timeoutMillis = 20_000) {
                    client.actorEvents().firstOrNull { it.npc && it.index == zek.index && it.kind == ActorEventKind.ANIMATION && it.id == FightCaveMonsters.ANIM_KET_ZEK_MELEE }
                }
                // Its magic stays in reach too (the Wiki's Strategies: both Magic and Melee in melee range).
                client.await("its magic in reach", timeoutMillis = 20_000) {
                    server.world.meleeCombat.shotsInFlight.takeIf { it > 0 && server.world.meleeCombat.reaches(zek, server.world.playerNamed("hal")!!) }
                }
                // Drained Strength takes off its stab's max hit as it would the file's (a Bandos godsword's specials).
                val def = server.world.combat.npcCombat[zek.type]!!
                assertEquals(FightCaveMonsters.KET_ZEK_MELEE_MAX, server.world.combat.npcMaxHit(zek, def, FightCaveMonsters.KET_ZEK_MELEE_MAX))
                zek.drained[osrsps.game.NpcStat.STRENGTH.ordinal] = 20
                val lost = def.maxHit - server.world.combat.npcMaxHit(zek, def)
                assertTrue(lost > 0)
                assertEquals(FightCaveMonsters.KET_ZEK_MELEE_MAX - lost, server.world.combat.npcMaxHit(zek, def, FightCaveMonsters.KET_ZEK_MELEE_MAX))
            }
        }
    }

    @Test
    fun `a Yt-MejKot beside the player heals itself below half in place of an attack`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ida").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("fightcave 15 1") { it.startsWith("Fight cave") }
                val mej = client.waveIn(server, "ida", 15).single()
                assertEquals(3123, mej.type.id)
                client.commandReply("hit 50 ${mej.index}") { it.startsWith("Hit") }
                val hurt = mej.hitpoints
                assertTrue(hurt * 2 < mej.maxHitpoints)
                client.tele(mej.x - 1, mej.z)
                val heal = client.await("its heal", timeoutMillis = 20_000) {
                    client.actorEvents().firstOrNull { it.npc && it.index == mej.index && it.kind == ActorEventKind.ANIMATION && it.id == FightCaveMonsters.ANIM_MEJKOT_HEAL }
                }
                assertTrue(client.actorEvents().any { it.npc && it.index == mej.index && it.kind == ActorEventKind.SPOTANIM && it.id == FightCaveMonsters.SPOTANIM_HEAL })
                // The heal is its attack for that tick: no swing lands on the player alongside it (a 0 under ::god).
                assertTrue(client.actorEvents().none { !it.npc && it.kind == ActorEventKind.HITSPLAT && it.ticks == heal.ticks })
                // Each heal is a roll of 0 to 10, and it heals again every attack while below half, so it gains in time.
                client.await("hitpoints back", timeoutMillis = 30_000) { mej.hitpoints.takeIf { it > hurt } }
            }
        }
    }

    private companion object {
        const val CENTREPIECE = 10
        const val INV = 93
        const val PROTECT_FROM_MELEE = 23
        const val PROTECT_FROM_MELEE_OBJ = 20817
    }
}
