package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.WearPos
import osrsps.game.combat.NpcCombat
import osrsps.game.content.DraynorManorPeople
import osrsps.game.content.KillerwattPlane
import osrsps.game.content.KillerwattPlaneLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Post-quest content step 15 through the headless client: Professor Oddenstein's warning at the rift for a player
 * without insulated boots, staying or going through, the Portal Home back, no warning once the boots are on, and a
 * killerwatt growing limbs as it fights back, hitting, and turning back into an orb once the player has gone. The
 * rl-client script test-runner/examples/killerwatt-plane.json goes through the rift and back on the real map.
 */
class HeadlessKillerwattPlaneTest {
    private val stats = NpcStats(attack = 50, defence = 40, strength = 50, hitpoints = 51, ranged = 67, magic = 67)
    private val orb = NpcType(KillerwattPlane.ORB, "Killerwatt", ops = listOf(null, "Attack", null, null, null), combatLevel = 55, internalName = "slayer_killerwatt_ball", stats = stats)
    private val biped = NpcType(KillerwattPlane.BIPED, "Killerwatt", ops = listOf(null, "Attack", null, null, null), combatLevel = 55, internalName = "slayer_killerwatt", stats = stats)
    private val oddenstein = NpcType(DraynorManorPeople.ODDENSTEIN, "Professor Oddenstein", ops = listOf("Talk-to", null, null, null, null), internalName = "professor_oddenstein")

    private fun loc(id: Int, name: String) = LocType(id, name, ops = listOf("Enter") + List(4) { null }, collision = LocCollisionType(width = 1, length = 1))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val types = NpcTypes.of(orb, biped, oddenstein)
        World(
            crcs, CollisionMap().apply { markLoaded(3110 shr 6, 3362 shr 6); markLoaded(2660 shr 6, 5214 shr 6); markLoaded(FAR_X shr 6, 5214 shr 6) },
            random = GameRandom(9),
            npcTypes = types,
            npcCombat = NpcCombat.load(types, SeqNames.NONE),
            objTypes = ObjTypes.of(ObjType(KillerwattPlane.INSULATED_BOOTS, "Insulated boots", iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = WearPos.FEET)),
            npcSpawns = listOf(
                NpcSpawn(oddenstein, 3110, 3367, 2, wander = 0, source = "test"),
                NpcSpawn(orb, ORB_X, ORB_Z, 2, wander = 0, source = "test"),
            ),
            locTypes = LocTypes(listOf(loc(KillerwattPlane.RIFT, "Interdimensional rift"), loc(KillerwattPlane.PORTAL_HOME, "Portal Home")).associateBy { it.id }),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(KillerwattPlane.RIFT, LocShape.CENTREPIECE_STRAIGHT, 0, 3110, 3362, 2))
                add(ZoneLoc(KillerwattPlane.PORTAL_HOME, LocShape.CENTREPIECE_STRAIGHT, 0, 2678, 5214, 2))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.chat(line: Line) {
        val text = DialogueText.NONE[line]
        awaitDialogue(line.key) { it is DialogueView.Chat && it.text == text }
        continueDialogue()
    }

    private fun HeadlessClient.at(x: Int, z: Int, level: Int) = await("at $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }

    @Test
    fun `Oddenstein warns a player without insulated boots, who stays or goes through, and the Portal Home brings them back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 3110 3364 2")
                client.at(3110, 3364, 2)

                // Staying.
                client.locOp(KillerwattPlane.RIFT, 3110, 3362, KillerwattPlane.ENTER)
                client.chat(KillerwattPlaneLines.ERR)
                client.chat(KillerwattPlaneLines.PROBLEM)
                client.chat(KillerwattPlaneLines.SHOCKING)
                client.awaitDialogue("the menu") { it is DialogueView.Menu && it.options.size == 3 }
                client.chooseOption(2)
                client.chat(KillerwattPlaneLines.STAY)
                client.awaitTicks(3)
                assertEquals(2, client.position?.level, "still in the manor")
                assertTrue(client.position!!.z < 5000)

                // Not scared: through.
                client.locOp(KillerwattPlane.RIFT, 3110, 3362, KillerwattPlane.ENTER)
                client.chat(KillerwattPlaneLines.ERR)
                client.chat(KillerwattPlaneLines.PROBLEM)
                client.chat(KillerwattPlaneLines.SHOCKING)
                client.awaitDialogue("the menu") { it is DialogueView.Menu && it.options.size == 3 }
                client.chooseOption(3)
                client.chat(KillerwattPlaneLines.NOT_SCARED)
                client.chat(KillerwattPlaneLines.OK_WARNED)
                client.at(KillerwattPlane.PLANE_LANDING.x, KillerwattPlane.PLANE_LANDING.z, 2)

                client.locOp(KillerwattPlane.PORTAL_HOME, 2678, 5214, KillerwattPlane.ENTER)
                client.at(KillerwattPlane.MANOR_LANDING.x, KillerwattPlane.MANOR_LANDING.z, 2)

                // With the boots on, straight through.
                client.command("item ${KillerwattPlane.INSULATED_BOOTS} 1")
                val slot = client.await("the boots") { client.inventory(BACKPACK).indexOfFirst { it?.id == KillerwattPlane.INSULATED_BOOTS }.takeIf { it >= 0 } }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, WEAR_BUTTON_OP, slot, KillerwattPlane.INSULATED_BOOTS)
                client.await("the boots worn") { client.inventory(WORN).getOrNull(WearPos.FEET)?.takeIf { it.id == KillerwattPlane.INSULATED_BOOTS } }
                client.locOp(KillerwattPlane.RIFT, 3110, 3362, KillerwattPlane.ENTER)
                client.at(KillerwattPlane.PLANE_LANDING.x, KillerwattPlane.PLANE_LANDING.z, 2)
                assertEquals(null, client.dialogueView, "no warning")
            }
        }
    }

    @Test
    fun `a killerwatt grows limbs as it fights back, hits hard without insulated boots, and is an orb again once the player leaves`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel slayer 37") { it.isNotEmpty() }
                client.command("tele ${ORB_X - 1} $ORB_Z 2")
                client.at(ORB_X - 1, ORB_Z, 2)
                val killerwatt = server.world.npcs.all.first { it.spawn.type.id == KillerwattPlane.ORB }
                val index = client.await("the orb in view") { client.npcs().firstOrNull { it.id == KillerwattPlane.ORB } }.index
                val full = client.await("hitpoints") { client.stat(HITPOINTS) }.level

                client.npcOp(index, ATTACK)
                client.await("limbs", timeoutMillis = 10_000) { if (killerwatt.type.id == KillerwattPlane.BIPED) Unit else null }
                assertTrue(killerwatt.scriptedAttacks)
                // It fights back in melee, every 2 ticks: within 20 ticks it has hit.
                client.await("a hit taken", timeoutMillis = 15_000) { client.stat(HITPOINTS)?.level?.takeIf { it < full } }

                // Far off and out of its reach, it lets go and is an orb again.
                client.command("tele 3110 3364 2")
                client.at(3110, 3364, 2)
                client.await("an orb again", timeoutMillis = 30_000) { if (killerwatt.target == null && killerwatt.type.id == KillerwattPlane.ORB) Unit else null }
            }
        }
    }

    @Test
    fun `a killerwatt's first swing comes the tick after its fight starts`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cy").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele ${ORB_X - 1} $ORB_Z 2")
                client.at(ORB_X - 1, ORB_Z, 2)
                val world = server.world
                val cy = world.playerNamed("cy")!!
                val killerwatt = world.npcs.all.first { it.spawn.type.id == KillerwattPlane.ORB }
                val result = java.util.concurrent.CompletableFuture<Pair<Long, Long>>()
                world.npcScripts.later(1) {
                    // Beside it, its attack ready: the tick its fight starts it only gets ready to swing.
                    killerwatt.nextAttackTick = 0
                    killerwatt.target = cy
                    world.killerwattPlane.tick(killerwatt)
                    result.complete(world.tick to killerwatt.nextAttackTick)
                }
                val (tick, next) = result.get(10, java.util.concurrent.TimeUnit.SECONDS)
                assertEquals(tick + 1, next, "no swing the tick the fight starts")
            }
        }
    }

    @Test
    fun `out of melee reach a killerwatt stays put and shoots every 2 ticks, and insulated boots cap its hits`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dee").use { client ->
                client.await("the login rebuild") { client.position }
                // Enough hitpoints to take its 15s while it shoots.
                client.commandReply("setlevel hitpoints 99") { it.isNotEmpty() }
                client.command("tele ${ORB_X - 5} $ORB_Z 2")
                client.at(ORB_X - 5, ORB_Z, 2)
                val world = server.world
                val plane = world.killerwattPlane
                val dee = world.playerNamed("dee")!!
                val killerwatt = world.npcs.all.first { it.spawn.type.id == KillerwattPlane.ORB }
                fun <T> onTick(block: () -> T): T {
                    val result = java.util.concurrent.CompletableFuture<T>()
                    world.npcScripts.later(1) { result.complete(block()) }
                    return result.get(10, java.util.concurrent.TimeUnit.SECONDS)
                }

                // Without boots, both its attacks hit up to 15; in them, its melee is its own and its ranged 8.
                onTick {
                    assertEquals(KillerwattPlane.UNINSULATED_MAX_HIT, plane.meleeMaxHit(dee))
                    assertEquals(KillerwattPlane.UNINSULATED_MAX_HIT, plane.rangedMaxHit(dee))
                }

                // Five tiles off, its fight starts out of melee reach: it shoots from where it is.
                // As if hit from there: it takes them on (MeleeCombat.aggress), and its fight is its own (Npc.scriptedAttacks).
                // Then its next attack tick on each of the 7 ticks after (inside the fight's IN_COMBAT_TICKS with no hit
                // back), all read on the game thread so no tick is missed: the distinct values are its shots,
                // RANGED_SPEED apart.
                val samples = java.util.concurrent.CompletableFuture<List<Long>>()
                fun sample(into: MutableList<Long>) {
                    world.npcScripts.later(1) {
                        into += killerwatt.nextAttackTick
                        if (into.size == 7) samples.complete(into) else sample(into)
                    }
                }
                world.npcScripts.later(1) {
                    world.meleeCombat.aggress(killerwatt, dee, world.tick)
                    sample(ArrayList())
                }
                val seen = samples.get(10, java.util.concurrent.TimeUnit.SECONDS).filter { it > 0 }.distinct()
                assertTrue(seen.size >= 3, "it kept shooting: $seen")
                assertEquals(List(seen.size - 1) { RANGED_SPEED }, seen.zipWithNext { x, y -> y - x }, "a shot every $RANGED_SPEED ticks: $seen")
                assertEquals(ORB_X to ORB_Z, onTick { killerwatt.x to killerwatt.z }, "it doesn't come to melee")

                client.command("item ${KillerwattPlane.INSULATED_BOOTS} 1")
                val slot = client.await("the boots") { client.inventory(BACKPACK).indexOfFirst { it?.id == KillerwattPlane.INSULATED_BOOTS }.takeIf { it >= 0 } }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, WEAR_BUTTON_OP, slot, KillerwattPlane.INSULATED_BOOTS)
                client.await("the boots worn") { client.inventory(WORN).getOrNull(WearPos.FEET)?.takeIf { it.id == KillerwattPlane.INSULATED_BOOTS } }
                onTick {
                    assertEquals(null, plane.meleeMaxHit(dee))
                    assertEquals(KillerwattPlane.RANGED_MAX_HIT, plane.rangedMaxHit(dee))
                }
            }
        }
    }

    /**
     * Each tick for [ticks] ticks after it takes on [name] where they stand at ([fromX], [ORB_Z]) and they are put on
     * ([toX], [toZ]) the tick after, read on the game thread: its next attack tick, its distance from them on either axis,
     * its animation, and whether it still fights them.
     */
    private data class Seen(val nextAttack: Long, val distance: Int, val anim: Int?, val fighting: Boolean)

    private fun fought(
        server: TestServer, client: HeadlessClient, name: String, fromX: Int, toX: Int, ticks: Int, toZ: Int = ORB_Z,
    ): List<Seen> {
        client.await("the login rebuild") { client.position }
        client.commandReply("setlevel hitpoints 99") { it.isNotEmpty() }
        client.command("tele $fromX $ORB_Z 2")
        client.at(fromX, ORB_Z, 2)
        val world = server.world
        val player = world.playerNamed(name)!!
        val killerwatt = world.npcs.all.first { it.spawn.type.id == KillerwattPlane.ORB }
        val samples = java.util.concurrent.CompletableFuture<List<Seen>>()
        fun sample(into: MutableList<Seen>) {
            world.npcScripts.later(1) {
                val distance = maxOf(kotlin.math.abs(killerwatt.x - player.x), kotlin.math.abs(killerwatt.z - player.z))
                into += Seen(killerwatt.nextAttackTick, distance, killerwatt.effects.anim?.id, killerwatt.target === player)
                if (into.size == ticks) samples.complete(into) else sample(into)
            }
        }
        world.npcScripts.later(1) {
            world.meleeCombat.aggress(killerwatt, player, world.tick)
            world.npcScripts.later(1) {
                if (toX != player.x || toZ != player.z) world.teleport(player, toX, toZ, 2)
                sample(ArrayList())
            }
        }
        return samples.get(15, java.util.concurrent.TimeUnit.SECONDS)
    }

    @Test
    fun `a killerwatt taken on in melee walks after the player past its leash and swings, never shooting`(@TempDir dir: Path) {
        // Beside it, then 11 tiles from its spawn: past the 7 an NPC keeps to while it fights (exploit reviews 66 and 67,
        // R66C-1 and R67B-1). In melee it keeps after them (the Wiki's Killerwatt): it comes beside them and attacks there.
        server(dir).use { server ->
            server.login("fay").use { client ->
                val seen = fought(server, client, "fay", fromX = ORB_X - 1, toX = ORB_X - 11, ticks = 16)
                assertTrue(seen.all { it.fighting }, "it never gives up on them: $seen")
                val beside = seen.indexOfFirst { it.distance == 1 }
                assertTrue(beside >= 0, "it comes beside them: $seen")
                assertTrue(seen.drop(beside).map { it.nextAttack }.distinct().size >= 2, "and attacks there: $seen")
                assertTrue(seen.none { it.anim == KillerwattPlane.CAST_ANIM }, "never with its ranged cast: $seen")
            }
        }
    }

    @Test
    fun `a killerwatt taken on in melee keeps walking after a player more than 32 tiles off`(@TempDir dir: Path) {
        // Past Npcs.FOLLOW_RANGE (Codex review on #1044): it still comes after them, closer each tick, and keeps the fight.
        server(dir).use { server ->
            server.login("ida").use { client ->
                val seen = fought(server, client, "ida", fromX = ORB_X - 1, toX = FAR_X, ticks = 6)
                assertTrue(seen.first().distance > 32, "they start past its follow range: $seen")
                assertTrue(seen.all { it.fighting }, "it never gives up on them: $seen")
                assertTrue(seen.last().distance <= seen.first().distance - 3, "it walks after them: $seen")
            }
        }
    }

    @Test
    fun `a killerwatt left far from home when its fight ends walks back and can be fought on the way`(@TempDir dir: Path) {
        // Lured 40 tiles off, then its player goes home: it stays unleashed so a new fight there isn't cut off by its
        // leash, walks home, and is leashed again once back (Codex review on #1044).
        server(dir).use { server ->
            server.login("jo").use { client ->
                val world = server.world
                val killerwatt = world.npcs.all.first { it.spawn.type.id == KillerwattPlane.ORB }
                fought(server, client, "jo", fromX = ORB_X - 1, toX = FAR_X, ticks = 1)
                val jo = world.playerNamed("jo")!!
                fun <T> onTick(block: () -> T): T {
                    val result = java.util.concurrent.CompletableFuture<T>()
                    world.npcScripts.later(1) { result.complete(block()) }
                    return result.get(10, java.util.concurrent.TimeUnit.SECONDS)
                }
                // Its fight goes on, lured off, until it's 25 tiles from home.
                var away = 0
                while (away < 25) away = onTick { kotlin.math.abs(killerwatt.x - killerwatt.spawn.x) }
                val home = KillerwattPlane.MANOR_LANDING
                onTick { world.teleport(jo, home.x, home.z, home.level) }
                val after = onTick { Triple(killerwatt.target, killerwatt.unleashed, kotlin.math.abs(killerwatt.x - killerwatt.spawn.x)) }
                assertEquals(null, after.first, "its fight is over: $after")
                assertTrue(after.second && after.third > 18, "far off, still unleashed: $after")
                // Taken on again out there, its new fight holds rather than ending on its leash.
                onTick {
                    world.teleport(jo, killerwatt.x - 1, killerwatt.z, 2)
                    world.meleeCombat.aggress(killerwatt, jo, world.tick)
                }
                onTick { }
                assertTrue(onTick { killerwatt.target === jo }, "a new fight out there holds")
                onTick { world.teleport(jo, home.x, home.z, home.level) }
                var back = false
                repeat(80) { if (!back) back = onTick { !killerwatt.unleashed } }
                assertTrue(back, "it walked home and is leashed again")
                assertTrue(onTick { kotlin.math.abs(killerwatt.x - killerwatt.spawn.x) <= 7 }, "home")
            }
        }
    }

    @Test
    fun `a killerwatt left far from home and taken on from range there shoots back, and walks home once they're out of its range`(@TempDir dir: Path) {
        // Lured 25 tiles off in melee, its player goes home and comes back to shoot it from 5 tiles, more than 18 from its
        // spawn: its fight must hold and it must shoot, not end on its leash every tick (exploit review round 68, R68C-1).
        server(dir).use { server ->
            server.login("kim").use { client ->
                val world = server.world
                val killerwatt = world.npcs.all.first { it.spawn.type.id == KillerwattPlane.ORB }
                fought(server, client, "kim", fromX = ORB_X - 1, toX = FAR_X, ticks = 1)
                val kim = world.playerNamed("kim")!!
                fun <T> onTick(block: () -> T): T {
                    val result = java.util.concurrent.CompletableFuture<T>()
                    world.npcScripts.later(1) { result.complete(block()) }
                    return result.get(10, java.util.concurrent.TimeUnit.SECONDS)
                }
                var away = 0
                while (away < 25) away = onTick { killerwatt.spawn.x - killerwatt.x }
                val home = KillerwattPlane.MANOR_LANDING
                onTick { world.teleport(kim, home.x, home.z, home.level) }
                assertEquals(null, onTick { killerwatt.target }, "its melee fight is over")
                onTick {
                    world.teleport(kim, killerwatt.x - 5, killerwatt.z, 2)
                    world.meleeCombat.aggress(killerwatt, kim, world.tick)
                }
                assertTrue(onTick { killerwatt.spawn.x - kim.x } > 18, "they stand past its leash")
                val seen = (1..8).map { onTick { Triple(killerwatt.target === kim, killerwatt.effects.anim?.id, killerwatt.nextAttackTick) } }
                assertTrue(seen.all { it.first }, "its ranged fight holds: $seen")
                assertTrue(seen.any { it.second == KillerwattPlane.CAST_ANIM } || seen.map { it.third }.distinct().size >= 2, "and it shoots: $seen")
                // Out of its range, the fight is over and it walks home.
                onTick { world.teleport(kim, killerwatt.x - 20, killerwatt.z, 2) }
                var over = false
                repeat(3) { if (!over) over = onTick { killerwatt.target == null } }
                assertTrue(over, "out of its range, its fight ends")
                var back = false
                repeat(80) { if (!back) back = onTick { !killerwatt.unleashed } }
                assertTrue(back, "it walked home and is leashed again")
            }
        }
    }

    @Test
    fun `a killerwatt taken on from range keeps shooting when the player comes beside it`(@TempDir dir: Path) {
        // As the Wiki's strategy has it: from range it shoots for the whole fight, Protect from Missiles' to block, and
        // doesn't come to melee (exploit review 67, R67B-2, is live).
        server(dir).use { server ->
            server.login("gus").use { client ->
                val seen = fought(server, client, "gus", fromX = ORB_X - 5, toX = ORB_X - 1, ticks = 6)
                assertTrue(seen.all { it.fighting && it.distance == 1 }, "it fights them beside it: $seen")
                assertTrue(seen.any { it.anim == KillerwattPlane.CAST_ANIM }, "with its ranged cast: $seen")
            }
        }
    }

    @Test
    fun `a killerwatt in melee gives up the fight once the player is home off the plane`(@TempDir dir: Path) {
        // Home through the portal is on the plane's level: unleashed, only leaving the plane ends its fight.
        server(dir).use { server ->
            server.login("hal").use { client ->
                val home = KillerwattPlane.MANOR_LANDING
                val seen = fought(server, client, "hal", fromX = ORB_X - 1, toX = home.x, toZ = home.z, ticks = 3)
                assertTrue(seen.none { it.fighting }, "its fight is over: $seen")
                val killerwatt = server.world.npcs.all.first { it.spawn.type.id == KillerwattPlane.ORB }
                assertTrue(!killerwatt.unleashed, "and it keeps to its spawn again")
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val WORN = 94
        const val WEAR_BUTTON_OP = 3
        const val ATTACK = 2
        const val HITPOINTS = 3
        const val ORB_X = 2660
        const val ORB_Z = 5214
        const val RANGED_SPEED = 2L
        const val FAR_X = ORB_X - 40
    }
}
