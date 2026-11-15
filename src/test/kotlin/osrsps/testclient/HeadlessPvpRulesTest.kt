package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.PlayerOps
import osrsps.game.World
import osrsps.game.combat.PvpLines
import osrsps.game.dialogue.DialogueText
import osrsps.map.CollisionMap

/**
 * Wilderness step W1.2 through two headless clients: Attack on players comes and goes with the Wilderness, and an attack
 * is refused outside its fighting ground or out of combat-level range, and otherwise goes after the target.
 */
class HeadlessPvpRulesTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            for (sz in (3456 shr 6)..(3967 shr 6)) markLoaded(3090 shr 6, sz)
        }
        World(crcs, map, cycleMillis = millis)
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.attackShown() = playerOps()[PlayerOps.ATTACK] == PlayerOps.ATTACK_TEXT

    private fun HeadlessClient.refusedWith(vararg lines: osrsps.game.dialogue.Line, action: () -> Unit) {
        val texts = lines.map { DialogueText.NONE[it] }
        val before = texts.map { text -> messages().count { it.text == text } }
        action()
        texts.forEachIndexed { i, text -> await("the message '$text'") { Unit.takeIf { messages().count { it.text == text } > before[i] } } }
    }

    @Test
    fun `Attack shows only inside the Wilderness`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("hana").use { client ->
                client.await("the login rebuild") { client.position }
                client.awaitTicks(2)
                assertTrue(!client.attackShown(), "not in Misthalin")
                client.tele(3090, 3530)
                client.await("Attack given") { Unit.takeIf { client.attackShown() } }
                client.tele(3090, 3510)
                client.await("Attack taken away") { Unit.takeIf { PlayerOps.ATTACK in client.playerOps() && client.playerOps()[PlayerOps.ATTACK] == null } }
            }
        }
    }

    @Test
    fun `an attack is refused on the safe rows, outside, and out of range, and goes after the target in range`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ivan").use { a ->
                server.login("jade").use { b ->
                    a.await("the login rebuild") { a.position }
                    b.await("the login rebuild") { b.position }
                    // ivan at combat 3, jade raised to about 50.
                    for (skill in listOf("attack", "strength", "defence", "hitpoints")) b.commandReply("setlevel $skill 50") { true }
                    b.awaitTicks(2)
                    val world = server.world
                    val ivan = world.playerNamed("ivan")!!
                    val jade = world.playerNamed("jade")!!
                    val gap = jade.skills.combatLevel - ivan.skills.combatLevel
                    assertTrue(gap in 30..55, "a gap of $gap")

                    // Both on the safe rows: Attack is on the menu, but nobody can fight there.
                    a.tele(3090, 3523)
                    b.tele(3091, 3524)
                    a.await("Attack given") { Unit.takeIf { a.attackShown() } }
                    a.await("jade in view") { a.otherPlayers().takeIf { it.isNotEmpty() } }
                    a.refusedWith(PvpLines.NOT_IN_WILDERNESS) { a.playerOp(b.index, PlayerOps.ATTACK) }

                    // ivan past the safe rows, jade still on them.
                    a.tele(3090, 3526)
                    a.refusedWith(PvpLines.TARGET_NOT_IN_WILDERNESS) { a.playerOp(b.index, PlayerOps.ATTACK) }

                    // Both at level 20: the gap is too great.
                    a.command("wildy 20")
                    a.await("ivan at level 20") { a.position?.takeIf { it.z >= 3672 } }
                    val at = a.position!!
                    b.tele(at.x + 1, at.z)
                    a.refusedWith(PvpLines.LEVEL_DIFFERENCE, PvpLines.MOVE_DEEPER) { a.playerOp(b.index, PlayerOps.ATTACK) }

                    // Deep enough for the gap: ivan goes after jade, facing them, and nothing is said.
                    val deep = 3520 + (gap - 1) * 8 + 4
                    a.tele(3090, deep)
                    b.tele(3093, deep)
                    a.awaitTicks(2)
                    val said = a.messages().size
                    a.playerOp(b.index, PlayerOps.ATTACK)
                    a.await("ivan beside jade") { a.position?.takeIf { maxOf(Math.abs(it.x - 3093), Math.abs(it.z - deep)) == 1 } }
                    assertEquals(said, a.messages().size, "no refusal")
                }
            }
        }
    }

    @Test
    fun `Attack can't be sent outside the Wilderness`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("kira").use { a ->
                server.login("liam").use { b ->
                    a.await("the login rebuild") { a.position }
                    a.tele(3090, 3500)
                    b.tele(3091, 3500)
                    a.await("liam in view") { a.otherPlayers().takeIf { it.isNotEmpty() } }
                    a.playerOp(b.index, PlayerOps.ATTACK)
                    a.awaitTicks(3)
                    assertEquals(Coord(3090, 3500, 0), a.position, "a forged Attack does nothing")
                    assertNull(a.messages().firstOrNull { it.text.startsWith("[pvp.") })
                }
            }
        }
    }
}
