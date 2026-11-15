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
import osrsps.game.combat.Skulls
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

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            for (sz in (3456 shr 6)..(3967 shr 6)) markLoaded(3090 shr 6, sz)
        }
        val objs = arrayOfNulls<ObjType>(items.maxOf { it.id } + 1)
        for (type in items) objs[type.id] = type
        World(crcs, map, random = GameRandom(3), objTypes = ObjTypes(objs), scenarios = Scenarios(listOf(pvp)), cycleMillis = millis)
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
                    a.commandReply("skull on") { it.startsWith("You're skulled") }
                    assertEquals(Skulls.TICKS, emil.skullTicks)
                    a.commandReply("skull off") { it == "Your skull is gone." }
                    assertEquals(0, emil.skullTicks)
                    a.commandReply("skull") { it == "You have no skull." }
                    a.commandReply("skull on") { true }
                    a.commandReply("skull") { it == "You're skulled: ${Skulls.TICKS} ticks left." || it == "You're skulled: ${Skulls.TICKS - 1} ticks left." }

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

    private companion object {
        const val ATTACK = 0

        /** Long enough for several swings, short of a kill: 70 Hitpoints. */
        const val TICKS = 14
    }
}
