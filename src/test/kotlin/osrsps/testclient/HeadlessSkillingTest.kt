package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.prot.GameServerProt
import org.junit.jupiter.api.io.TempDir
import osrsps.game.AllSettings
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.ClockStep
import osrsps.game.skill.LevelUps
import osrsps.game.skill.SkillClock
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Gathering step 1 through the headless client: an action that re-runs itself every tick on the skill clock, and the
 * level-up. The rl-client script test-runner/examples/skills-levelup.json shows the level-up in the real client.
 */
class HeadlessSkillingTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val tree = LocType(TREE, "Test tree", ops = listOf("Chop", null, null, null, null), collision = LocCollisionType(width = 1, length = 1))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            locTypes = LocTypes(mapOf(TREE to tree)),
            staticLocs = StaticLocs().apply { add(ZoneLoc(TREE, LocShape.CENTREPIECE_STRAIGHT, 0, 3225, 3218, 0)) },
            cycleMillis = millis,
            varTypes = VarTypes(6_000, AllSettings.VARBITS),
        ).apply {
            val world = this
            // A stand-in gathering action: say where the clock is each tick, and keep going.
            handlers.onLoc(TREE, 1) { op ->
                when (SkillClock.step(player, tick, 4)) {
                    ClockStep.DUE -> player.message("roll $tick${if (op.repeat) "" else " click"}")
                    ClockStep.STARTED -> if (!op.repeat) player.message("start")
                    ClockStep.WAITING -> {}
                }
                world.repeatOp(player, op)
            }
        }
    }

    private fun HeadlessClient.rolls() = messages().map { it.text }.filter { it.startsWith("roll ") }.map { it.removePrefix("roll ").toLong() }

    @Test
    fun `a re-running action rolls every 4 ticks until the player walks away`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(TREE, 3225, 3218, 1)
                client.await("start") { client.messages().firstOrNull { it.text == "start" } }
                val rolls = client.await("three rolls") { client.rolls().takeIf { it.size >= 3 } }
                assertEquals(listOf(4L, 4L), rolls.take(3).zipWithNext { a, b -> b - a })

                client.walk(3220, 3218)
                client.awaitTicks(2)
                val stopped = client.rolls().size
                client.awaitTicks(8)
                assertEquals(stopped, client.rolls().size, "rolls carried on after walking away")
            }
        }
    }

    @Test
    fun `XP past a level shows the level-up box, plays the skill's jingle and fireworks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("addxp woodcutting 83")
                // No dialogue file in tests, so each line shows its key and values.
                client.await("the level-up message") { client.messages().firstOrNull { it.text == "[levelup.message skill=Woodcutting level=2]" } }
                client.await("levelup_display open") { if (LevelUps.INTERFACE in client.openInterfaces()) Unit else null }
                client.await("its title") { client.text(LevelUps.INTERFACE, LevelUps.TEXT1)?.takeIf { it == "[levelup.title article=a skill=Woodcutting]" } }
                assertEquals("[levelup.body skill=Woodcutting level=2]", client.text(LevelUps.INTERFACE, LevelUps.TEXT2))
                assertTrue(client.packets().any { it.prot == GameServerProt.MIDI_JINGLE }, "no jingle")
                assertTrue(client.packets().any { it.prot == GameServerProt.IF_SETHIDE }, "the woodcutting icon wasn't shown")

                client.resumePause(LevelUps.INTERFACE, LevelUps.CONTINUE)
                client.await("the box closed") { if (LevelUps.INTERFACE !in client.openInterfaces()) Unit else null }
            }
        }
    }

    @Test
    fun `a combat skill that raises the combat level shows a second box for it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                // 1,000 XP is Attack 9, and combat level 3 -> 6.
                client.command("addxp attack 1000")
                client.await("the Attack box") { client.text(LevelUps.INTERFACE, LevelUps.TEXT2)?.takeIf { it == "[levelup.body skill=Attack level=9]" } }
                assertEquals("[levelup.title article=an skill=Attack]", client.text(LevelUps.INTERFACE, LevelUps.TEXT1))
                client.resumePause(LevelUps.INTERFACE, LevelUps.CONTINUE)
                client.await("the combat box") { client.text(LevelUps.INTERFACE, LevelUps.TEXT2)?.takeIf { it == "[levelup.combat_body level=6]" } }
                client.await("the combat message") { client.messages().firstOrNull { it.text == "[levelup.combat_message level=6]" } }
                client.resumePause(LevelUps.INTERFACE, LevelUps.CONTINUE)
                client.await("the box closed") { if (LevelUps.INTERFACE !in client.openInterfaces()) Unit else null }
            }
        }
    }

    @Test
    fun `with level-up pop-ups disabled there is no box, but the messages and jingle still come`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                // What the All Settings dropdown's Disabled sets (script1105).
                client.command("varbit ${AllSettings.VARBIT_LEVEL_UP_MESSAGE_DISABLED} 1")
                client.awaitTicks(1)
                // Attack 9 and combat level 6: the skill's and the combat level's level-ups both leave their box out.
                client.command("addxp attack 1000")
                client.await("the level-up message") { client.messages().firstOrNull { it.text == "[levelup.message skill=Attack level=9]" } }
                client.await("the combat message") { client.messages().firstOrNull { it.text == "[levelup.combat_message level=6]" } }
                client.awaitTicks(3)
                // With no box to wait on, both run in one cycle, and the client plays one jingle at a time (PlayerEffects).
                assertTrue(client.packets().any { it.prot == GameServerProt.MIDI_JINGLE }, "no jingle")
                assertTrue(LevelUps.INTERFACE !in client.openInterfaces(), "the level-up box opened")

                // Back to Enabled: the next level-up shows its box again.
                client.command("varbit ${AllSettings.VARBIT_LEVEL_UP_MESSAGE_DISABLED} 0")
                client.awaitTicks(1)
                client.command("addxp woodcutting 83")
                client.await("levelup_display open") { if (LevelUps.INTERFACE in client.openInterfaces()) Unit else null }
            }
        }
    }

    private companion object {
        /** Not a real tree, which woodcutting would already handle. */
        const val TREE = 9010
    }
}
