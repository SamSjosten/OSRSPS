package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.content.Cats
import osrsps.game.content.RatPits
import osrsps.game.content.RatPitsLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.shop.ShopScreen
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc

/**
 * Post-quest content step 14 through the headless client: two players climb down the Varrock manhole with their grown
 * cats, challenge each other, set and match their bets, confirm, tell their cats a strategy and watch the fight to its
 * end, the winner taking both bets; and the refusals for a player with no cat or a kitten, and the Challenge option
 * going on the way back up the ladder. The rl-client script test-runner/examples/rat-pits.json covers the way in and
 * out and Hooknosed Jack's pit talk on the real map.
 */
class HeadlessRatPitsTest {
    private val catOps = listOf("Pick-up", null, "Talk-to", "Chase", "Interact")
    private val npcs = NpcTypes.of(
        NpcType(CAT_NPC, "Cat", ops = catOps, internalName = "growncat"),
        NpcType(KITTEN_NPC, "Kitten", ops = catOps, internalName = "kittenpet1"),
        *RatPits.RAT_TYPES.map { NpcType(it, "Rat", ops = List(5) { null }) }.toTypedArray(),
    )

    private fun loc(id: Int, name: String, op: String) = LocType(id, name, ops = listOf(op) + List(4) { null }, collision = LocCollisionType(width = 1, length = 1))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(MANHOLE_X shr 6, MANHOLE_Z shr 6); markLoaded(2896 shr 6, 5097 shr 6) },
            random = GameRandom(5),
            varTypes = VarTypes(6_000, emptyMap(), 21_000),
            npcTypes = npcs,
            objTypes = ObjTypes.of(ObjType(CAT_OBJ, "Pet cat"), ObjType(KITTEN_OBJ, "Pet kitten"), ObjType(ShopScreen.COINS, "Coins", stackable = true)),
            locTypes = LocTypes(listOf(loc(RatPits.MANHOLE, "Manhole", "Climb-down"), loc(RatPits.LADDER, "Ladder", "Climb-up")).associateBy { it.id }),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(RatPits.MANHOLE, LocShape.CENTREPIECE_STRAIGHT, 0, MANHOLE_X, MANHOLE_Z, 0))
                add(ZoneLoc(RatPits.LADDER, LocShape.CENTREPIECE_STRAIGHT, 0, LADDER_X, LADDER_Z, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.coins() = inventory(BACKPACK).filterNotNull().filter { it.id == ShopScreen.COINS }.sumOf { it.count }

    private fun HeadlessClient.message(line: Line, vararg values: Pair<String, Any>) =
        DialogueText.NONE.get(line, *values).let { text -> await(line.key) { messages().firstOrNull { it.text == text } } }

    /** Logs in at the manhole with [cat] (or none) following and [coins], and climbs down. */
    private fun HeadlessClient.ready(cat: Int?, coins: Int) {
        await("the login rebuild") { position }
        command("tele $MANHOLE_X ${MANHOLE_Z + 2} 0")
        await("by the manhole") { position?.takeIf { it == Coord(MANHOLE_X, MANHOLE_Z + 2, 0) } }
        if (coins > 0) commandReply("item ${ShopScreen.COINS} $coins") { it.contains("Coins") }
        if (cat != null) {
            commandReply("item $cat 1") { it.contains("Pet") }
            ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, DROP_BUTTON_OP, slotOf(cat), cat)
            await("the cat out") { npcs().firstOrNull { it.id == (if (cat == CAT_OBJ) CAT_NPC else KITTEN_NPC) } }
        }
        locOp(RatPits.MANHOLE, MANHOLE_X, MANHOLE_Z, RatPits.CLIMB_DOWN)
        await("down in the pits") { position?.takeIf { it == Coord(RatPits.LANDING.x, RatPits.LANDING.z, 0) } }
        await("the Challenge option") { playerOps()[RatPits.CHALLENGE_OP]?.takeIf { it == DialogueText.NONE[RatPitsLines.CHALLENGE] } }
    }

    /** Talks their cat through the strategy dialogue, picking [option]. */
    private fun HeadlessClient.strategy(option: Int) {
        awaitDialogue("up for it") { it is DialogueView.Chat && it.text == DialogueText.NONE[RatPitsLines.UP_FOR_IT] }
        continueDialogue()
        awaitDialogue("the cat's advice") { it is DialogueView.Chat && it.text == DialogueText.NONE[RatPitsLines.ADVICE] }
        continueDialogue()
        awaitDialogue("the strategies") { it is DialogueView.Menu && it.options.size == 4 }
        chooseOption(option)
        awaitDialogue("the strategy said") { it is DialogueView.Chat && it.text == DialogueText.NONE[RatPitsLines.STRATEGY[option - 1]] }
        continueDialogue()
    }

    @Test
    fun `two grown cats are bet on, fight the pits' rats, and the winner's player takes both bets`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.ready(CAT_OBJ, 1_000)
                server.login("bob").use { bob ->
                    bob.ready(CAT_OBJ, 1_000)
                    val a = server.world.playerNamed("alice")!!
                    val b = server.world.playerNamed("bob")!!

                    // A challenge, then one back, opens the betting screen for both.
                    alice.playerOp(b.index, RatPits.CHALLENGE_OP)
                    bob.message(RatPitsLines.CHALLENGED, "name" to "alice")
                    bob.playerOp(a.index, RatPits.CHALLENGE_OP)
                    for (c in listOf(alice, bob)) c.await("the betting screen") { if (RatPits.BETTING in c.openInterfaces()) Unit else null }
                    // The bet's text can come a packet after the screen opens.
                    alice.await("alice's opening bet") { alice.text(RatPits.BETTING, RatPits.P1_BET)?.takeIf { it == "100" } }

                    // Bob raises his bet; alice can't accept until hers matches.
                    bob.ifButton(RatPits.BETTING, RatPits.BET_UP, 1)
                    alice.await("bob's bet on alice's screen") { alice.text(RatPits.BETTING, RatPits.P2_BET)?.takeIf { it == "200" } }
                    alice.ifButton(RatPits.BETTING, RatPits.ACCEPT, 1)
                    alice.message(RatPitsLines.SAME_BET)
                    alice.ifButton(RatPits.BETTING, RatPits.BET_UP, 1)
                    bob.await("alice's bet on bob's screen") { bob.text(RatPits.BETTING, RatPits.P2_BET)?.takeIf { it == "200" } }
                    alice.ifButton(RatPits.BETTING, RatPits.ACCEPT, 1)
                    bob.ifButton(RatPits.BETTING, RatPits.ACCEPT, 1)

                    // The confirm screen, then the coins go in and the strategies are asked.
                    for (c in listOf(alice, bob)) c.await("the confirm screen") { c.text(RatPits.CONFIRM, RatPits.CONFIRM_AMOUNT)?.takeIf { it == "200" } }
                    assertTrue(RatPits.BETTING !in alice.openInterfaces(), "the confirm screen replaces the betting one")
                    alice.ifButton(RatPits.CONFIRM, RatPits.CONFIRM_YES, 1)
                    bob.ifButton(RatPits.CONFIRM, RatPits.CONFIRM_YES, 1)
                    for (c in listOf(alice, bob)) c.await("the bet paid") { if (c.coins() == 800) Unit else null }
                    alice.strategy(4)
                    bob.strategy(1)

                    // The fight: the scores show until one cat wins, flees or dies.
                    for (c in listOf(alice, bob)) c.await("the scores") { if (RatPits.SCORES in c.openInterfaces()) Unit else null }
                    assertTrue(server.world.npcs.all.count { it.type.id in RatPits.RAT_TYPES } == RatPits.RATS, "the pits' rats out")
                    for (c in listOf(alice, bob)) c.await("the fight over", timeoutMillis = 60_000) { if (RatPits.SCORES !in c.openInterfaces()) Unit else null }
                    assertTrue(server.world.npcs.all.none { it.type.id in RatPits.RAT_TYPES }, "the rats go with the fight")
                    assertTrue(!server.world.ratPits.inMatch(a) && !server.world.ratPits.inMatch(b))
                    assertTrue(server.onTick { a.charges[RatPits.STAKE] == 0 && b.charges[RatPits.STAKE] == 0 }, "no stake kept once the match settles")

                    val coins = alice.await("alice's coins settled") { alice.coins().takeIf { it != 800 || bob.coins() != 800 } }
                    val bobs = bob.coins()
                    assertEquals(2_000, coins + bobs, "nothing made or lost")
                    val winner = if (coins > bobs) alice else bob
                    if (coins != bobs) {
                        assertEquals(1_200, winner.coins())
                        winner.awaitDialogue("the win") { it is DialogueView.Chat && it.text == DialogueText.NONE[RatPitsLines.WON, "coins" to "200"] }
                    } else {
                        alice.message(RatPitsLines.DRAW)
                    }
                    // A cat that lived is back following, grown a little; one that died is gone.
                    for (p in listOf(a, b)) {
                        val follower = server.world.cats.follower(p)
                        if (follower != null) assertTrue(server.world.npcs.all.any { it === follower })
                        else assertNull(server.world.cats.followerKind(p))
                    }
                }
            }
        }
    }

    @Test
    fun `a player with no cat or a kitten can't challenge, and the option goes on the way up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { carol ->
                carol.ready(CAT_OBJ, 0)
                server.login("dave").use { dave ->
                    dave.ready(null, 0)
                    server.login("erin").use { erin ->
                        erin.ready(KITTEN_OBJ, 0)
                        val c = server.world.playerNamed("carol")!!
                        val d = server.world.playerNamed("dave")!!
                        carol.playerOp(d.index, RatPits.CHALLENGE_OP)
                        carol.message(RatPitsLines.THEY_NEED_CAT)
                        dave.playerOp(c.index, RatPits.CHALLENGE_OP)
                        dave.message(RatPitsLines.NEED_CAT)
                        erin.playerOp(c.index, RatPits.CHALLENGE_OP)
                        erin.message(RatPitsLines.CATS_ONLY)
                        assertTrue(Cats.Stage.KITTEN == server.world.cats.followerKind(server.world.playerNamed("erin")!!)?.stage)

                        carol.locOp(RatPits.LADDER, LADDER_X, LADDER_Z, RatPits.CLIMB_UP)
                        carol.await("back up") { carol.position?.takeIf { it == Coord(RatPits.SURFACE.x, RatPits.SURFACE.z, 0) } }
                        carol.await("the Challenge option gone") { if (carol.playerOps()[RatPits.CHALLENGE_OP] == null) Unit else null }
                        assertTrue(!server.world.ratPits.inArea(c.x, c.z, c.level))
                    }
                }
            }
        }
    }

    @Test
    fun `a stake saved from a match the server stopped comes back at the next login`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("frank").use { frank ->
                frank.await("the login rebuild") { frank.position }
                val f = server.world.playerNamed("frank")!!
                server.onTick { f.charges[RatPits.STAKE] = 300 }
            }
            waitFor("frank saved and gone") { if (server.world.playerNamed("frank") == null) Unit else null }
            server.login("frank").use { frank ->
                frank.message(RatPitsLines.STAKE_BACK, "coins" to "300")
                frank.await("the stake back") { if (frank.coins() == 300) Unit else null }
                val f = server.world.playerNamed("frank")!!
                assertEquals(0, server.onTick { f.charges[RatPits.STAKE] })
            }
        }
    }

    private fun <T : Any> waitFor(what: String, timeoutMillis: Long = 10_000, value: () -> T?): T {
        val end = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < end) {
            value()?.let { return it }
            Thread.sleep(20)
        }
        throw AssertionError("Timed out waiting for $what")
    }

    private fun <T> TestServer.onTick(block: () -> T): T {
        val result = CompletableFuture<T>()
        world.npcScripts.later(1) { result.complete(block()) }
        return result.get(10, TimeUnit.SECONDS)
    }

    private companion object {
        const val BACKPACK = 93
        const val DROP_BUTTON_OP = 7
        const val CAT_OBJ = 1561
        const val KITTEN_OBJ = 1555
        const val CAT_NPC = 1619
        const val KITTEN_NPC = 5591
        const val MANHOLE_X = 3267
        const val MANHOLE_Z = 3400
        const val LADDER_X = 2895
        const val LADDER_Z = 5097
    }
}
