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

    private fun server(dir: Path, seed: Long, spawn: Boolean = true) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val text = NpcDrops::class.java.getResource(NpcDrops.RESOURCE)!!.readText()
        val names = Regex("\"item\": (\\d+), \"name\": \"([^\"]+)\"").findAll(text).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val objTypes = ObjTypes.of(*names.map { (id, name) -> ObjType(id, name, stackable = true) }.toTypedArray())
        val entrance = LocType(Calvarion.ENTRANCE, "Memorial", listOf("Enter", "Peek", "Check-Fee", null, null), LocCollisionType())
        val exit = LocType(Calvarion.EXIT, "Cave", listOf("Exit", null, null, null, null), LocCollisionType(width = 2, length = 2))
        World(
            crcs, CollisionMap().apply { markLoaded(29, 180); markLoaded(49, 57) }, objTypes = objTypes, npcTypes = types,
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
                client.commandReply("hit 140 ${npc.index}") { it.startsWith("Hit Calvar'ion #${npc.index} for 65: 75/150") }
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

    private companion object {
        const val WBR_PERM_INT_1 = 3755
        const val BIG_BONES = 532
    }
}
