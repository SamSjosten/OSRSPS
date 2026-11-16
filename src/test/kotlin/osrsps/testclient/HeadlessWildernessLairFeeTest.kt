package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.PlayerOps
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.Deaths
import osrsps.game.content.WildernessLairFee
import osrsps.game.content.arriveDelay
import osrsps.game.content.WildernessLairFee.payToEnter
import osrsps.game.content.WildernessLairFeeLines
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Wilderness step W4.7 through headless clients: the lairs' shared fee taken once, from the bank, after a menu; 10,000 off
 * for each top-damage kill down to nothing; and lost to a PvP death before leaving the Wilderness, as coins for the
 * killer, but not after. The lairs themselves come later, so a stand-in cave at level 25 calls [payToEnter].
 */
class HeadlessWildernessLairFeeTest {
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))
    private val coins = ObjType(WildernessLairFee.COINS, "Coins", cost = 1, stackable = true, tradeable = true)
    private val cave = LocType(CAVE, "Cave", listOf("Enter", null, null, null, null), LocCollisionType(width = 1, length = 1))

    /** The cache's `wbr_entrance_fee_paid` and `wbr_entrance_discount`, bits of `wbr_perm_int_1`. */
    private val varTypes = VarTypes(
        6_000,
        mapOf(WildernessLairFee.VARBIT_PAID to VarbitType(WBR_PERM_INT_1, 0, 0), WildernessLairFee.VARBIT_DISCOUNT to VarbitType(WBR_PERM_INT_1, 1, 3)),
        21_000,
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            markLoaded(3222 shr 6, 3218 shr 6)
            for (sz in (3456 shr 6)..(3967 shr 6)) markLoaded(3090 shr 6, sz)
        }
        World(
            crcs, map, objTypes = ObjTypes.of(coins), npcTypes = NpcTypes.of(grave), varTypes = varTypes,
            locTypes = LocTypes(mapOf(CAVE to cave)), staticLocs = StaticLocs().apply { add(ZoneLoc(CAVE, 10, 0, CAVE_X, LEVEL_25, 0)) },
            cycleMillis = millis,
        ).apply {
            handlers.onLoc(CAVE, 1) {
                arriveDelay()
                if (payToEnter(this@apply)) player.message("in")
            }
        }
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.enter(menu: Boolean) {
        val before = messages().count { it.text == "in" }
        locOp(CAVE, CAVE_X, LEVEL_25, 1)
        if (menu) {
            awaitDialogue("the fee's menu") { it is DialogueView.Menu && WildernessLairFeeLines.TITLE.key in it.title }
            chooseOption(1)
        }
        await("in") { Unit.takeIf { messages().count { it.text == "in" } > before } }
    }

    @Test
    fun `the fee is taken once from the bank, comes off by 10,000 a top-damage kill, and goes no lower than nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("amy").use { client ->
                client.await("the login rebuild") { client.position }
                val amy = server.world.playerNamed("amy")!!
                client.tele(CAVE_X, LEVEL_25 - 1)
                client.locOp(CAVE, CAVE_X, LEVEL_25, 1)
                client.await("refused without the fee") { client.messages().firstOrNull { it.text == "[${WildernessLairFeeLines.NO_COINS.key} fee=50000]" } }
                assertFalse(WildernessLairFee.paid(amy))

                amy.bank.add(coins, 60_000)
                client.enter(menu = true)
                assertTrue(WildernessLairFee.paid(amy))
                assertTrue(WildernessLairFee.visited(amy))
                assertEquals(10_000L, amy.bank.count(coins.id), "the fee from the bank")
                client.enter(menu = false)
                assertEquals(10_000L, amy.bank.count(coins.id), "paid once")

                WildernessLairFee.discount(amy)
                WildernessLairFee.discount(amy)
                assertEquals(30_000, WildernessLairFee.fee(amy))
                repeat(5) { WildernessLairFee.discount(amy) }
                assertEquals(0, WildernessLairFee.fee(amy), "no lower than nothing")
                assertEquals(WildernessLairFee.MAX_DISCOUNTS, WildernessLairFee.discounts(amy))
            }
        }
    }

    @Test
    fun `a PvP death before leaving the Wilderness drops the fee for the killer, and after leaving it doesn't`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("kai").use { killer ->
                server.login("lou").use { victim ->
                    killer.await("the login rebuild") { killer.position }
                    victim.await("the login rebuild") { victim.position }
                    val lou = server.world.playerNamed("lou")!!
                    lou.bank.add(coins, 100_000)
                    victim.tele(CAVE_X, LEVEL_25 - 1)
                    victim.enter(menu = true)
                    assertEquals(50_000L, lou.bank.count(coins.id))

                    server.kill(killer, victim)
                    killer.await("the fee on the floor for the killer") {
                        server.world.groundObjs.visibleAt(server.world.playerNamed("kai")!!, 3091, LEVEL_25, 0).firstOrNull { it.id == coins.id && it.count == WildernessLairFee.FEE }
                    }
                    assertFalse(WildernessLairFee.paid(lou), "to be paid again")

                    victim.tele(CAVE_X, LEVEL_25 - 1)
                    victim.enter(menu = true)
                    assertEquals(0L, lou.bank.count(coins.id))
                    // Out of the Wilderness, and back: the last visit no longer puts the fee at risk.
                    victim.tele(3222, 3218)
                    victim.await("the visit over") { Unit.takeIf { !WildernessLairFee.visited(lou) } }
                    server.kill(killer, victim)
                    assertTrue(WildernessLairFee.paid(lou), "kept")
                }
            }
        }
    }

    /** [victim] on 1 hitpoint at level 25, killed by one sure punch from [killer], and back at the spawn. */
    private fun TestServer.kill(killer: HeadlessClient, victim: HeadlessClient) {
        killer.commandReply("sure max") { it.startsWith("Your attacks") }
        victim.commandReply("hit 9 me") { it.startsWith("Hit yourself") }
        victim.command("varp 172 1")
        val deaths = victim.messages().count { it.text == "[combat.dead]" }
        killer.tele(3090, LEVEL_25)
        victim.tele(3091, LEVEL_25)
        killer.await("Attack given") { Unit.takeIf { killer.playerOps()[PlayerOps.ATTACK] == PlayerOps.ATTACK_TEXT } }
        killer.await("the victim in view") { killer.otherPlayers().takeIf { it.isNotEmpty() } }
        killer.playerOp(victim.index, PlayerOps.ATTACK)
        victim.await("[combat.dead]") { Unit.takeIf { victim.messages().count { it.text == "[combat.dead]" } > deaths } }
        victim.await("the respawn", timeoutMillis = 10_000) { victim.position?.takeIf { it == Coord(3222, 3218) } }
    }

    private companion object {
        const val CAVE = 40_000
        const val CAVE_X = 3094
        const val LEVEL_25 = 3520 + 24 * 8
        const val WBR_PERM_INT_1 = 3755
    }
}
