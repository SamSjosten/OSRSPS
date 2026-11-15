package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.PlayerOps
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.PvpInteraction
import osrsps.game.combat.PvpLines
import osrsps.game.combat.Skulls
import osrsps.game.dialogue.DialogueText
import osrsps.map.CollisionMap

/**
 * Wilderness step W1.6 through two headless clients: an unprovoked attack skulls the attacker and not the player who
 * hits back; the PK Skull Prevention setting refuses an attack that would skull, but not hitting back; and the skull
 * goes when its time runs out.
 */
class HeadlessPvpSkullTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            for (sz in (3456 shr 6)..(3967 shr 6)) markLoaded(3090 shr 6, sz)
        }
        // skull_prevent_enabled on settings_varp_3, as the 242 cache has it.
        World(crcs, map, varTypes = VarTypes(6_000, mapOf(Skulls.VARBIT_PREVENTION to VarbitType(3075, 16, 16))), cycleMillis = millis)
    }

    private fun HeadlessClient.ready(x: Int) {
        await("the login rebuild") { position }
        commandReply("setlevel hitpoints 99") { true }
        command("tele $x 3600 0")
        await("the teleport") { position?.takeIf { it == Coord(x, 3600, 0) } }
        await("Attack given") { Unit.takeIf { playerOps()[PlayerOps.ATTACK] == PlayerOps.ATTACK_TEXT } }
    }

    @Test
    fun `an unprovoked attack skulls the attacker, and hitting back doesn't`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gus").use { a ->
                server.login("hal").use { b ->
                    a.ready(3090)
                    b.ready(3091)
                    a.await("hal in view") { a.otherPlayers().takeIf { it.isNotEmpty() } }
                    b.await("gus in view") { b.otherPlayers().takeIf { it.isNotEmpty() } }
                    val gus = server.world.playerNamed("gus")!!
                    val hal = server.world.playerNamed("hal")!!
                    a.playerOp(b.index, PlayerOps.ATTACK)
                    a.await("gus skulled") { Unit.takeIf { gus.skullTicks > 0 } }
                    assertTrue(gus.skullTicks > Skulls.TICKS - 10)
                    // hal's auto retaliate is on: he hits back, and stays unskulled.
                    b.await("hal fighting back") { (hal.interaction as? PvpInteraction)?.takeIf { hal.pjOpponent === gus && hal.nextAttackTick > 0 } }
                    b.awaitTicks(8)
                    assertEquals(0, hal.skullTicks, "hitting back doesn't skull")
                    assertEquals(Skulls.ICON, gus.sentSkullIcon)
                    assertEquals(-1, hal.sentSkullIcon)

                    // The skull goes when its time is up.
                    a.command("tele 3090 3500 0")
                    a.awaitTicks(2)
                    gus.skullTicks = 3
                    a.await("the skull gone") { Unit.takeIf { gus.sentSkullIcon == -1 && gus.skullTicks == 0 } }
                }
            }
        }
    }

    @Test
    fun `skull prevention refuses an attack that would skull, but not hitting back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ida").use { a ->
                server.login("jon").use { b ->
                    a.ready(3090)
                    b.ready(3091)
                    a.await("hal in view") { a.otherPlayers().takeIf { it.isNotEmpty() } }
                    b.await("gus in view") { b.otherPlayers().takeIf { it.isNotEmpty() } }
                    val ida = server.world.playerNamed("ida")!!
                    a.command("varbit ${Skulls.VARBIT_PREVENTION} 1")
                    a.command("varp 172 1")
                    val prevented = DialogueText.NONE[PvpLines.SKULL_PREVENTED]
                    a.playerOp(b.index, PlayerOps.ATTACK)
                    a.await("the refusal") { a.messages().firstOrNull { it.text == prevented } }
                    assertEquals(0, ida.skullTicks)

                    b.playerOp(a.index, PlayerOps.ATTACK)
                    b.await("jon's first hit") { Unit.takeIf { ida.attackedFirstBy.isNotEmpty() } }
                    a.playerOp(b.index, PlayerOps.ATTACK)
                    a.await("ida hitting back") { (ida.interaction as? PvpInteraction)?.takeIf { ida.pjOpponent != null && ida.nextAttackTick > 0 } }
                    a.awaitTicks(6)
                    assertEquals(0, ida.skullTicks, "hitting back with prevention on is allowed, and doesn't skull")
                }
            }
        }
    }
}
