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
            crcs, CollisionMap().apply { markLoaded(3110 shr 6, 3362 shr 6); markLoaded(2660 shr 6, 5214 shr 6) },
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

    private companion object {
        const val BACKPACK = 93
        const val WORN = 94
        const val WEAR_BUTTON_OP = 3
        const val ATTACK = 2
        const val HITPOINTS = 3
        const val ORB_X = 2660
        const val ORB_Z = 5214
        const val RANGED_SPEED = 2L
    }
}
