package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.PlayerOps
import osrsps.game.Scenario
import osrsps.game.Scenarios
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.combat.PvpInteraction
import osrsps.game.combat.Skulls
import osrsps.game.combat.Teleblock
import osrsps.map.CollisionMap

/**
 * Wilderness step W1.9 through two headless clients: `::scenario pvp` gives both players the fight's loadout, and with
 * `::seed` set before the first attack the same fight plays out hit for hit a second time; `::scenario` clears the skull
 * and PJ timer the first fight left, `::skull` gives and takes a skull, and `::pj` clears the timer.
 */
class HeadlessPvpScenarioTest {
    /** The real scenario file's items, by their 242 cache ids and names; worn ones with their slot. */
    private val items = listOf(
        ObjType(1163, "Rune full helm", internalName = "rune_full_helm", wearpos1 = WearPos.HEAD),
        ObjType(1127, "Rune platebody", internalName = "rune_platebody", wearpos1 = WearPos.BODY),
        ObjType(1079, "Rune platelegs", internalName = "rune_platelegs", wearpos1 = WearPos.LEGS),
        ObjType(1201, "Rune kiteshield", internalName = "rune_kiteshield", wearpos1 = WearPos.SHIELD),
        ObjType(1333, "Rune scimitar", internalName = "rune_scimitar", wearpos1 = WearPos.WEAPON),
        ObjType(1731, "Amulet of power", internalName = "amulet_of_power", wearpos1 = WearPos.NECK),
        ObjType(1061, "Leather boots", internalName = "leather_boots", wearpos1 = WearPos.FEET),
        ObjType(1059, "Leather gloves", internalName = "leather_gloves", wearpos1 = WearPos.HANDS),
        ObjType(385, "Shark", internalName = "shark"),
        ObjType(563, "Law rune", stackable = true, internalName = "lawrune"),
        ObjType(560, "Death rune", stackable = true, internalName = "deathrune"),
        ObjType(562, "Chaos rune", stackable = true, internalName = "chaosrune"),
    )

    private val pvp = Scenario.parse("pvp", Scenarios::class.java.getResource("/scenarios/pvp.json")!!.readText())

    /** The 242 cache's `teleblock_cycles`, bits 0 to 11 of varp 2741. */
    private val varTypes = VarTypes(6_000, mapOf(Teleblock.VARBIT to VarbitType(TELEBLOCK_VARP, 0, 11)))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            for (sz in (3456 shr 6)..(3967 shr 6)) markLoaded(3090 shr 6, sz)
        }
        val objs = arrayOfNulls<ObjType>(items.maxOf { it.id } + 1)
        for (type in items) objs[type.id] = type
        World(crcs, map, random = GameRandom(3), objTypes = ObjTypes(objs), scenarios = Scenarios(listOf(pvp)), varTypes = varTypes, cycleMillis = millis)
    }

    /**
     * Both start the scenario, [b] steps beside [a] with auto retaliate off, and [a] attacks with the seed set to 42 in
     * the same tick; returns [a]'s Attack XP after each of the [ticks] ticks that follow, counted from the scenario's.
     */
    private fun fight(a: HeadlessClient, b: HeadlessClient, ticks: Int): List<Int> {
        for (client in listOf(a, b)) client.commandReply("scenario pvp") { it.startsWith("Started scenario pvp") }
        b.command("varp 172 1")
        b.command("tele 3091 3592 0")
        b.await("beside a") { b.position?.takeIf { it == Coord(3091, 3592, 0) } }
        a.await("Attack given") { Unit.takeIf { a.playerOps()[PlayerOps.ATTACK] == PlayerOps.ATTACK_TEXT } }
        a.await("b in view") { a.otherPlayers().takeIf { it.isNotEmpty() } }
        val start = a.stat(ATTACK)!!.xp
        a.commandReply("pause") { it.startsWith("Paused after tick") }
        a.command("seed 42")
        a.playerOp(b.index, PlayerOps.ATTACK)
        val xp = List(ticks) {
            a.stepTicks(1)
            a.stat(ATTACK)!!.xp - start
        }
        a.commandReply("resume") { it.startsWith("Resumed after tick") }
        return xp
    }

    @Test
    fun `the scenario twice with the same seed is the same fight`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cleo").use { a ->
                server.login("dirk").use { b ->
                    a.await("the login rebuild") { a.position }
                    b.await("the login rebuild") { b.position }
                    val cleo = server.world.playerNamed("cleo")!!
                    val first = fight(a, b, TICKS)
                    assertTrue(first.last() > 0, "some damage dealt: $first")
                    assertTrue(cleo.skullTicks > 0, "the attack skulled her")

                    val second = fight(a, b, TICKS)
                    assertEquals(first, second, "the same rolls on the same ticks")
                    assertTrue(cleo.skullTicks in (Skulls.TICKS - TICKS)..Skulls.TICKS, "the scenario cleared the first skull, and the new fight gave one")
                }
            }
        }
    }

    @Test
    fun `skull and pj set the state a test needs`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("emil").use { a ->
                server.login("faye").use { b ->
                    a.await("the login rebuild") { a.position }
                    b.await("the login rebuild") { b.position }
                    val emil = server.world.playerNamed("emil")!!
                    // Ranges, not exact counts: the clock keeps running between a reply and the next read (Codex review on #723).
                    a.commandReply("skull on") { it.startsWith("You're skulled") }
                    assertTrue(emil.skullTicks in (Skulls.TICKS - SLACK)..Skulls.TICKS, "a fresh skull: ${emil.skullTicks}")
                    a.commandReply("skull off") { it == "Your skull is gone." }
                    assertEquals(0, emil.skullTicks)
                    a.commandReply("skull") { it == "You have no skull." }
                    a.commandReply("skull on") { true }
                    a.commandReply("skull") { reply ->
                        Regex("You're skulled: (\\d+) ticks left\\.").matchEntire(reply)?.groupValues?.get(1)?.toInt()?.let { it in (Skulls.TICKS - SLACK)..Skulls.TICKS } == true
                    }

                    // A fight starts both PJ timers; ::pj clears the one who asks.
                    for (client in listOf(a, b)) client.commandReply("scenario pvp") { it.startsWith("Started scenario pvp") }
                    assertEquals(0, emil.skullTicks, "the scenario cleared it")
                    b.command("tele 3091 3592 0")
                    a.await("b in view") { a.otherPlayers().takeIf { it.isNotEmpty() } }
                    a.await("Attack given") { Unit.takeIf { a.playerOps()[PlayerOps.ATTACK] == PlayerOps.ATTACK_TEXT } }
                    a.playerOp(b.index, PlayerOps.ATTACK)
                    a.await("the PJ timer") { Unit.takeIf { emil.pjUntil > server.world.tick } }
                    // Out of the Wilderness the fight ends, and no swing starts the timer again; it still runs.
                    a.command("tele 3090 3500 0")
                    a.await("out of the Wilderness") { a.position?.takeIf { it == Coord(3090, 3500, 0) } }
                    assertTrue(emil.pjUntil > server.world.tick)
                    a.commandReply("pj") { it.startsWith("Your PJ timer is cleared") }
                    assertEquals(0L, emil.pjUntil)
                    assertNotEquals(0, emil.skullTicks, "::pj leaves the skull")
                    a.commandReply("pj") { it == "No PJ timer was running." }
                }
            }
        }
    }

    @Test
    fun `reset after a scenario leaves no skull, Tele Block timer or opponent's attack from before`(@TempDir dir: Path) {
        // Codex review on #723: the snapshot taken as the scenario starts holds the block's varbit.
        server(dir).use { server ->
            server.login("gwen").use { a ->
                server.login("hugo").use { b ->
                    a.await("the login rebuild") { a.position }
                    b.await("the login rebuild") { b.position }
                    val world = server.world
                    val gwen = world.playerNamed("gwen")!!
                    a.command("wildy 10")
                    a.await("in the Wilderness") { a.position?.takeIf { it.z > 3520 } }
                    val hugo = world.playerNamed("hugo")!!
                    Teleblock.block(gwen, hugo, Teleblock.TICKS)
                    a.await("the varbit set") { a.varp(TELEBLOCK_VARP).takeIf { it > 0 } }
                    a.commandReply("skull on") { it.startsWith("You're skulled") }
                    a.commandReply("scenario pvp") { it.startsWith("Started scenario pvp") }
                    assertEquals(0, gwen.teleblockTicks, "the scenario cleared the block")
                    // An opponent beside them still attacking as they start the scenario again: it stops the attack.
                    b.commandReply("scenario pvp") { it.startsWith("Started scenario pvp") }
                    a.command("varp 172 1")
                    b.command("tele 3091 3592 0")
                    b.await("beside gwen") { b.position?.takeIf { it == Coord(3091, 3592, 0) } }
                    b.await("Attack given") { Unit.takeIf { b.playerOps()[PlayerOps.ATTACK] == PlayerOps.ATTACK_TEXT } }
                    b.await("gwen in view") { b.otherPlayers().takeIf { it.isNotEmpty() } }
                    b.playerOp(a.index, PlayerOps.ATTACK)
                    b.await("hugo attacking") { Unit.takeIf { (hugo.interaction as? PvpInteraction)?.target === gwen } }
                    a.commandReply("scenario pvp") { it.startsWith("Started scenario pvp") }
                    assertTrue((hugo.interaction as? PvpInteraction)?.target !== gwen, "the opponent's attack is stopped")
                    a.command("reset")
                    a.await("the reset") { a.messages().firstOrNull { it.text.startsWith("Back to how you were") } }
                    a.awaitTicks(2)
                    assertEquals(0, a.varp(TELEBLOCK_VARP), "no timer for a block that's gone")
                    assertEquals(0, gwen.teleblockTicks)
                    assertEquals(0, gwen.skullTicks, "the skull from before the scenario is cleared too")
                }
            }
        }
    }

    private companion object {
        const val ATTACK = 0
        const val TELEBLOCK_VARP = 2741

        /** Ticks the clock may run between a command's reply and the test's next look. */
        const val SLACK = 20

        /** Long enough for several swings, short of a kill: 70 Hitpoints. */
        const val TICKS = 14
    }
}
