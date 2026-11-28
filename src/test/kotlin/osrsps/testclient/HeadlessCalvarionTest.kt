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
import osrsps.game.content.SkeletalLord
import osrsps.game.content.WildernessLairFee
import osrsps.game.content.WildernessLairFeeLines
import osrsps.game.content.WildernessLairLines
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.Tile

/**
 * Wilderness step W4.9 through the headless client: the Skeletal Tomb's way in taking the lairs' fee and dropping the
 * player on level 1, and its way out; Calvar'ion's lightning hurting, his hellhounds at half his hitpoints keeping him
 * from harm while they live, and his enraged form at 0 with full hitpoints; and his enraged form's death counting in his
 * kill count, leaving his big bones, and taking 10,000 off the fee. The rl-client script
 * test-runner/examples/calvarion.json does the way in and the fight on the real map.
 */
class HeadlessCalvarionTest {
    private val stats = NpcStats(attack = 250, strength = 250, defence = 225, hitpoints = 150, magic = 178)
    private val normal = NpcType(Calvarion.BOSS, "Calvar'ion", size = 3, ops = listOf(null, "Attack", null, null, null), combatLevel = 264, internalName = "vetion_single", stats = stats)
    private val enraged = NpcType(Calvarion.ENRAGED, "Calvar'ion", size = 3, ops = listOf(null, "Attack", null, null, null), combatLevel = 264, internalName = "vetion_2_single", stats = stats)
    private val hound = NpcType(
        Calvarion.HOUND, "Skeleton Hellhound", size = 2, ops = listOf(null, "Attack", null, null, null), combatLevel = 115,
        internalName = "vetion_hellhound_jnr_singles", stats = NpcStats(attack = 110, strength = 150, defence = 95, hitpoints = 30),
    )
    private val greater = NpcType(
        Calvarion.GREATER_HOUND, "Greater Skeleton Hellhound", size = 2, ops = listOf(null, "Attack", null, null, null), combatLevel = 139,
        internalName = "vetion_hellhound_snr_singles", stats = NpcStats(attack = 150, strength = 170, defence = 110, hitpoints = 30),
    )
    private val types = NpcTypes.of(normal, enraged, hound, greater)

    private val varTypes = VarTypes(
        6_000,
        mapOf(WildernessLairFee.VARBIT_PAID to VarbitType(WBR_PERM_INT_1, 0, 0), WildernessLairFee.VARBIT_DISCOUNT to VarbitType(WBR_PERM_INT_1, 1, 3)),
        21_000,
    )

    private fun server(dir: Path, seed: Long, spawn: Boolean = true, blocked: List<Tile> = emptyList()) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val text = NpcDrops::class.java.getResource(NpcDrops.RESOURCE)!!.readText()
        val names = Regex("\"item\": (\\d+), \"name\": \"([^\"]+)\"").findAll(text).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val objTypes = ObjTypes.of(*names.map { (id, name) -> ObjType(id, name, stackable = true) }.toTypedArray())
        val entrance = LocType(Calvarion.ENTRANCE, "Memorial", listOf("Enter", "Peek", "Check-Fee", null, null), LocCollisionType())
        val exit = LocType(Calvarion.EXIT, "Cave", listOf("Exit", null, null, null, null), LocCollisionType(width = 2, length = 2))
        World(
            crcs, CollisionMap().apply { markLoaded(29, 180); markLoaded(49, 57); for (t in blocked) add(t.x, t.z, Calvarion.TOMB_LEVEL, CollisionFlag.BLOCK_WALK) }, objTypes = objTypes, npcTypes = types,
            random = GameRandom(seed), varTypes = varTypes,
            npcSpawns = if (spawn) listOf(NpcSpawn(normal, Calvarion.SPAWN.x, Calvarion.SPAWN.z, Calvarion.TOMB_LEVEL, wander = 0, source = "test")) else emptyList(),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes),
            locTypes = LocTypes(mapOf(Calvarion.ENTRANCE to entrance, Calvarion.EXIT to exit)),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Calvarion.ENTRANCE, 10, 0, 3180, 3683, 0))
                add(ZoneLoc(Calvarion.EXIT, 10, 0, 1886, 11534, Calvarion.TOMB_LEVEL))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    /** Awaits [npc]'s hounds, kills them, and awaits [line] after the [skip] earlier ones. */
    private fun HeadlessClient.killHounds(world: World, fight: osrsps.game.encounter.Encounter, npc: osrsps.game.npc.Npc, line: String, skip: Int = 0) {
        await("his hounds") { fight.lines.filter { it.endsWith("calls 2 hounds") }.drop(skip).firstOrNull() }
        for (h in Calvarion.FIGHT.hounds(npc).toList()) commandReply("hit 30 ${h.index}") { it.startsWith("Hit ") }
        await("his hounds dead") { fight.lines.filter { it.endsWith(line) }.drop(skip).firstOrNull() }
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `the way in takes the fee and drops the player into the tomb, and the way out leads back`(@TempDir dir: Path) {
        server(dir, seed = 1, spawn = false).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                val abe = server.world.playerNamed("abe")!!
                client.tele(3180, 3682, 0)
                client.locOp(Calvarion.ENTRANCE, 3180, 3683, Calvarion.CHECK_FEE)
                client.seen("[${WildernessLairLines.FEE_DUE.key} fee=50000]")
                client.locOp(Calvarion.ENTRANCE, 3180, 3683, Calvarion.PEEK)
                client.seen("[${WildernessLairLines.PEEK_KILLS.key}]")

                client.command("item ${WildernessLairFee.COINS} 50000")
                client.await("the coins") { Unit.takeIf { abe.inventory.count(WildernessLairFee.COINS) == 50_000L } }
                client.locOp(Calvarion.ENTRANCE, 3180, 3683, Calvarion.ENTER)
                client.awaitDialogue("the fee's menu") { it is DialogueView.Menu && WildernessLairFeeLines.TITLE.key in it.title }
                client.chooseOption(1)
                val arrival = Coord(Calvarion.ARRIVAL.x, Calvarion.ARRIVAL.z, Calvarion.TOMB_LEVEL)
                client.await("in the tomb", timeoutMillis = 15_000) { client.position?.takeIf { it == arrival } }
                assertEquals(0L, abe.inventory.count(WildernessLairFee.COINS), "the fee taken")

                client.locOp(Calvarion.EXIT, 1886, 11534, 1)
                client.await("outside", timeoutMillis = 15_000) { client.position?.takeIf { it == Coord(Calvarion.OUTSIDE.x, Calvarion.OUTSIDE.z, 0) } }
            }
        }
    }

    @Test
    fun `his lightning hurts, his hounds keep him from harm, and at 0 he comes back enraged`(@TempDir dir: Path) {
        server(dir, seed = 4).use { server ->
            server.login("bo").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(Calvarion.SPAWN.x + 1, Calvarion.SPAWN.z - 4, Calvarion.TOMB_LEVEL)
                val npc = server.world.npcs.all.first { it.type.id == Calvarion.BOSS }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Calvar'ion") }
                val fight = client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                assertTrue(npc.unleashed, "unleashed, so a player far from his spawn can't hold him off (R45C-1)")
                client.await("his lightning", timeoutMillis = 30_000) { fight.lines.firstOrNull { "lightning on" in it } }

                // Half his hitpoints: two hounds, and nothing hurts him while one lives.
                client.commandReply("hit 75 ${npc.index}") { it.startsWith("Hit Calvar'ion") }
                client.await("his hounds") { fight.lines.firstOrNull { it.endsWith("calls 2 hounds") } }
                assertEquals(2, Calvarion.FIGHT.hounds(npc).size)
                client.commandReply("hit 50 ${npc.index}") { it.startsWith("Hit Calvar'ion #${npc.index} for 0") }
                for (h in Calvarion.FIGHT.hounds(npc).toList()) client.commandReply("hit 30 ${h.index}") { it.startsWith("Hit ") }
                client.await("his hounds dead") { fight.lines.firstOrNull { it.endsWith("his hounds are dead") } }

                // At 0 in his normal form: enraged, at full hitpoints.
                client.commandReply("hit 200 ${npc.index}") { it.startsWith("Hit Calvar'ion") }
                client.await("his enraged form") { fight.lines.firstOrNull { it.endsWith("enrages") } }
                assertTrue(!npc.dead, "not dead at 0 in his normal form")
                assertEquals(Calvarion.ENRAGED, npc.type.id)
                assertEquals(npc.maxHitpoints, npc.hitpoints)
                // Codex review on #818: the client is shown the full bar, not the empty one the last hit sent.
                client.await("his full health bar") { client.npcHealth(npc.index)?.takeIf { it.fill == osrsps.game.combat.MeleeCombat.healthBarFill(1, 1) } }
            }
        }
    }

    @Test
    fun `his enraged form's death counts, leaves his big bones, and takes 10,000 off the fee`(@TempDir dir: Path) {
        server(dir, seed = 3).use { server ->
            server.login("cy").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(Calvarion.SPAWN.x + 1, Calvarion.SPAWN.z - 4, Calvarion.TOMB_LEVEL)
                val npc = server.world.npcs.all.first { it.type.id == Calvarion.BOSS }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Calvar'ion") }
                val fight = client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                client.commandReply("hit 149 ${npc.index}") { it.startsWith("Hit Calvar'ion") }
                client.killHounds(server.world, fight, npc, "his hounds are dead")
                client.commandReply("hit 75 ${npc.index}") { it.startsWith("Hit Calvar'ion") }
                client.await("his enraged form") { fight.lines.firstOrNull { it.endsWith("enrages") } }
                client.commandReply("hit 150 ${npc.index}") { it.startsWith("Hit Calvar'ion #${npc.index} for 75") }
                client.killHounds(server.world, fight, npc, "his hounds are dead", skip = 1)
                client.commandReply("hit 75 ${npc.index}") { it.startsWith("Hit Calvar'ion") }
                val cy = server.world.playerNamed("cy")!!
                client.await("the kill count") { Unit.takeIf { cy.vars[Calvarion.KILL_COUNT_VARP] == 1 } }
                client.await("his bones") { server.world.groundObjs.visibleAt(cy, npc.x, npc.z, Calvarion.TOMB_LEVEL).firstOrNull { it.id == BIG_BONES } }
                assertEquals(40_000, WildernessLairFee.fee(cy), "10,000 off for the most damage")
            }
        }
    }

    @Test
    fun `a hit past half his hitpoints stops at half, for his hounds`(@TempDir dir: Path) {
        server(dir, seed = 4).use { server ->
            server.login("di").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(Calvarion.SPAWN.x + 1, Calvarion.SPAWN.z - 4, Calvarion.TOMB_LEVEL)
                val npc = server.world.npcs.all.first { it.type.id == Calvarion.BOSS }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Calvar'ion") }
                val fight = client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                // Codex review on #818: a hit of 149 on 149 would have enraged him without his hounds.
                client.commandReply("hit 149 ${npc.index}") { it.startsWith("Hit Calvar'ion #${npc.index} for 74: 75/150") }
                client.await("his hounds") { fight.lines.firstOrNull { it.endsWith("calls 2 hounds") } }
                assertTrue(fight.lines.none { it.endsWith("enrages") })
            }
        }
    }

    @Test
    fun `a hit landing as his enraged form runs out counts against his normal form`(@TempDir dir: Path) {
        server(dir, seed = 3).use { server ->
            server.login("ed").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(Calvarion.SPAWN.x + 1, Calvarion.SPAWN.z - 4, Calvarion.TOMB_LEVEL)
                val npc = server.world.npcs.all.first { it.type.id == Calvarion.BOSS }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Calvar'ion") }
                val fight = client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                client.commandReply("hit 149 ${npc.index}") { it.startsWith("Hit Calvar'ion") }
                client.killHounds(server.world, fight, npc, "his hounds are dead")
                client.commandReply("hit 75 ${npc.index}") { it.startsWith("Hit Calvar'ion") }
                client.await("his enraged form") { fight.lines.firstOrNull { it.endsWith("enrages") } }
                val ed = server.world.playerNamed("ed")!!
                // Codex review on #818: a shot landing after the transformation ran out, before his fight's tick, as
                // ranged and magic do; his calm-down used to put his hitpoints back to full over it.
                val ended = npc.transformEnded!!
                npc.transformEnded = {
                    ended()
                    server.world.meleeCombat.hit(npc, ed, 10, server.world.tick)
                }
                npc.transformTicks = 1
                client.await("his calm-down") { fight.lines.firstOrNull { it.endsWith("calms down") } }
                assertEquals(Calvarion.BOSS, npc.type.id)
                assertEquals(npc.maxHitpoints - 10, npc.hitpoints)
                // His damage from before his calm-down no longer counts; the shot that landed as it ran out does
                // (exploit review round 63, R63B-1).
                assertEquals(1, server.world.wildernessBosses.tallies)
                assertEquals(mapOf(ed.saveKey to 10), server.world.wildernessBosses.dealt(npc))
                client.commandReply("hit 140 ${npc.index}") { it.startsWith("Hit Calvar'ion #${npc.index} for 65: 75/150") }
            }
        }
    }

    @Test
    fun `his lightning warns for 3 ticks, then strikes its tile and the eight round it, near the player`(@TempDir dir: Path) {
        server(dir, seed = 4).use { server ->
            server.login("ida").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(Calvarion.SPAWN.x + 1, Calvarion.SPAWN.z - 4, Calvarion.TOMB_LEVEL)
                val npc = server.world.npcs.all.first { it.type.id == Calvarion.BOSS }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Calvar'ion") }
                val fight = client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                // Codex review on #818: the bolts' tiles, warning and landing, not only the log line.
                val cast = client.await("his lightning", timeoutMillis = 30_000) { fight.lines.firstOrNull { "lightning on" in it } }
                val tick = cast.substringBefore(' ').toLong()
                val centres = cast.substringAfter("lightning on ").split(' ').map { it.split(',').let { (x, z) -> x.toInt() to z.toInt() } }
                assertEquals(1 + osrsps.game.content.SkeletalLord.SOLO_RANDOM_BOLTS, centres.size, "one near her, the rest at random")
                val (cx, cz) = centres.first()
                val ida = server.world.playerNamed("ida")!!
                assertTrue(kotlin.math.abs(cx - ida.x) <= 1 && kotlin.math.abs(cz - ida.z) <= 1, "the first bolt within a tile of her")
                val hazards = fight.lines.filter { it.startsWith("$tick fight: hazard on ") }
                val warn = osrsps.game.content.SkeletalLord.LIGHTNING_WARN
                assertEquals("$tick fight: hazard on $cx,$cz from tick $warn for 1", hazards[0], "the bolt's own tile")
                val ring = (-1..1).flatMap { dx -> (-1..1).map { dz -> "${cx + dx},${cz + dz}" } }.filter { it != "$cx,$cz" }
                assertEquals("$tick fight: hazard on ${ring.joinToString(" ")} from tick $warn for 1", hazards[1], "the eight round it")
                val hit = client.await("the bolt landing on her") { fight.lines.firstOrNull { it.startsWith("${tick + warn} fight: hazard hit ida") } }
                assertTrue(fight.lines.none { "hazard hit ida" in it && it.substringBefore(' ').toLong() in tick until tick + warn }, "nothing before the warning ran out: $hit")
            }
        }
    }

    @Test
    fun `his shield bash hurts and stops attacks for 8 ticks for whoever is in its shadows, not one standing off`(@TempDir dir: Path) {
        server(dir, seed = 5).use { server ->
            server.login("ed").use { near ->
                server.login("fay").use { far ->
                    near.await("the login rebuild") { near.position }
                    far.await("the login rebuild") { far.position }
                    near.commandReply("god") { it.startsWith("You can") }
                    far.commandReply("god") { it.startsWith("You can") }
                    // Beside him, south of his 3x3, where he can reach; and 6 tiles off, out of every bash's area.
                    near.tele(Calvarion.SPAWN.x + 1, Calvarion.SPAWN.z - 1, Calvarion.TOMB_LEVEL)
                    far.tele(Calvarion.SPAWN.x + 1, Calvarion.SPAWN.z - 7, Calvarion.TOMB_LEVEL)
                    val npc = server.world.npcs.all.first { it.type.id == Calvarion.BOSS }
                    near.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Calvar'ion") }
                    val fight = near.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                    val hit = near.await("a bash hitting ed", timeoutMillis = 60_000) { fight.lines.firstOrNull { "bash hits ed for" in it } }
                    val tick = hit.substringBefore(' ').toLong()
                    // The log counts from the fight's start: its start tick, read with no tick going by in between.
                    val start = generateSequence { Triple(server.world.tick, fight.ticks, server.world.tick) }
                        .first { it.first == it.third }.let { it.first - it.second }
                    assertEquals(start + tick + osrsps.game.content.SkeletalLord.BASH_STUN_TICKS, server.world.playerNamed("ed")!!.nextAttackTick, "no attack till 8 ticks on")
                    assertEquals(start + tick + osrsps.game.content.SkeletalLord.BASH_STUN_TICKS, server.world.playerNamed("ed")!!.attackLockedUntil, "a lock no retaliation shortens")
                    val bash = fight.lines.last { "bash on" in it }
                    assertTrue(bash.substringBefore(' ').toLong() + osrsps.game.content.SkeletalLord.BASH_WARN == tick, "its shadows ${osrsps.game.content.SkeletalLord.BASH_WARN} ticks before")
                    assertTrue(fight.lines.none { "bash hits fay" in it }, "fay stood off")
                }
            }
        }
    }

    @Test
    fun `fought by him, the player can attack his hounds, single-way though the tomb is`(@TempDir dir: Path) {
        server(dir, seed = 4).use { server ->
            server.login("gus").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(Calvarion.SPAWN.x + 1, Calvarion.SPAWN.z - 1, Calvarion.TOMB_LEVEL)
                val npc = server.world.npcs.all.first { it.type.id == Calvarion.BOSS }
                val gus = server.world.playerNamed("gus")!!
                client.commandReply("hit 75 ${npc.index}") { it.startsWith("Hit Calvar'ion") }
                val fight = client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                client.await("his hounds") { fight.lines.firstOrNull { it.endsWith("calls 2 hounds") } }
                client.await("him fighting gus") { Unit.takeIf { gus.attackedBy === npc } }
                // Codex review on #818: single-way, this was "already under attack" while he fought gus.
                val hound = Calvarion.FIGHT.hounds(npc).first()
                client.npcOp(hound.index, 2)
                client.await("gus fighting the hound") { Unit.takeIf { (gus.interaction as? osrsps.game.combat.MeleeInteraction)?.npc === hound } }
                client.awaitTicks(3)
                assertTrue(client.messages().none { "already_under_attack" in it.text }, "no refusal")
            }
        }
    }

    /** Hits him to half, freezes both hounds where they come in, away from the player, and awaits [line] for both. */
    private fun HeadlessClient.idleHounds(server: TestServer, line: String): kotlin.Pair<osrsps.game.npc.Npc, osrsps.game.encounter.Encounter> {
        val npc = server.world.npcs.all.first { it.type.id == Calvarion.BOSS }
        commandReply("hit 75 ${npc.index}") { it.startsWith("Hit Calvar'ion") }
        val fight = await("his fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
        val hounds = await("his hounds") {
            Calvarion.FIGHT.hounds(npc).toList().takeIf { it.size == 2 }?.onEach { server.world.npcs.freeze(it, 500) }
        }
        assertTrue(hounds.none { it.dead })
        commandReply("hit 10 ${npc.index}") { it.startsWith("Hit Calvar'ion #${npc.index} for 0") }
        val called = server.world.tick
        await(line, timeoutMillis = 20_000) { fight.lines.filter { it.endsWith(line) }.takeIf { it.size == 2 } }
        assertTrue(server.world.tick - called >= SkeletalLord.HOUND_IDLE_TICKS - 2, "not before 50 idle ticks")
        return npc to fight
    }

    @Test
    fun `a hound that never swings comes back beside the player, and keeps him from harm`(@TempDir dir: Path) {
        server(dir, seed = 4).use { server ->
            server.login("ida").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.command("varp 172 1")
                client.tele(Calvarion.SPAWN.x + 1, Calvarion.SPAWN.z - 4, Calvarion.TOMB_LEVEL)
                val ida = server.world.playerNamed("ida")!!
                // Kept out of reach, they aren't sent away unkilled (exploit review round 49, R49C-2): stuck ones too (R47B-1).
                val (npc, _) = client.idleHounds(server, "brings back an idle hound")
                for (hound in Calvarion.FIGHT.hounds(npc)) {
                    assertTrue(!hound.dead, "still there")
                    assertTrue(server.world.meleeCombat.reaches(hound, ida), "beside ida at ${hound.x},${hound.z}")
                }
                client.commandReply("hit 10 ${npc.index}") { it.startsWith("Hit Calvar'ion #${npc.index} for 0") }
            }
        }
    }

    @Test
    fun `a hound that never swings, with nowhere beside the player for it, is let go and he can be hurt again`(@TempDir dir: Path) {
        val at = Tile(Calvarion.SPAWN.x + 1, Calvarion.SPAWN.z - 4)
        val walledIn = listOf(Tile(at.x - 1, at.z), Tile(at.x + 1, at.z), Tile(at.x, at.z - 1), Tile(at.x, at.z + 1))
        server(dir, seed = 4, blocked = walledIn).use { server ->
            server.login("ida").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.command("varp 172 1")
                client.tele(at.x, at.z, Calvarion.TOMB_LEVEL)
                val (npc, fight) = client.idleHounds(server, "lets go an idle hound")
                client.await("his hounds gone") { fight.lines.firstOrNull { it.endsWith("his hounds are dead") } }
                client.commandReply("hit 10 ${npc.index}") { it.startsWith("Hit Calvar'ion #${npc.index} for 10") }
            }
        }
    }

    @Test
    fun `with a gap too narrow for a hound on his east side, both hounds still come, elsewhere beside him`(@TempDir dir: Path) {
        // Blocked floor two tiles east of him: the tile beside him is open, but a hound two tiles across doesn't fit.
        val blocked = (-6..5).map { Tile(Calvarion.SPAWN.x + 4, Calvarion.SPAWN.z + it) }
        server(dir, seed = 4, blocked = blocked).use { server ->
            server.login("jo").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.command("varp 172 1")
                client.tele(Calvarion.SPAWN.x + 1, Calvarion.SPAWN.z - 4, Calvarion.TOMB_LEVEL)
                val npc = server.world.npcs.all.first { it.type.id == Calvarion.BOSS }
                client.commandReply("hit 75 ${npc.index}") { it.startsWith("Hit Calvar'ion") }
                val fight = client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                client.await("his hounds") { fight.lines.firstOrNull { it.endsWith("calls 2 hounds") } }
                val adds = fight.lines.filter { " add " in it }
                assertEquals(2, adds.size, adds.joinToString())
                for (line in adds) assertTrue(line.substringAfterLast(" at ").substringBefore(",").toInt() < npc.x + npc.type.size, "not in the gap: $line")
            }
        }
    }

    private companion object {
        const val WBR_PERM_INT_1 = 3755
        const val BIG_BONES = 532
    }
}
