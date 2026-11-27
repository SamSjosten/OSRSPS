package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InvObj
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.BottomlessLines
import osrsps.game.content.BottomlessMilkBucket
import osrsps.game.content.CowbellAmulet
import osrsps.game.content.CowSlippers
import osrsps.game.content.CowSlippersLines
import osrsps.game.content.GroatsFarm
import osrsps.game.dialogue.Chatbox
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Churn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Brutus step 5 through the headless client: the bottomless milk bucket (Fill with its first-time warning and amount,
 * Check, chocolate dust, Empty, milked into at a dairy cow every 7 ticks, churned) and the cow slippers' Swap.
 */
class HeadlessBrutusUniquesTest {
    private val box = LocCollisionType(width = 1, length = 1)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = ObjTypes.of(
            ObjType(BottomlessMilkBucket.EMPTY, "Bottomless milk bucket (empty)", iops = listOf(null, null, "Fill", "Check", "Drop")),
            ObjType(BottomlessMilkBucket.FILLED, "Bottomless milk bucket", iops = listOf(null, null, "Fill", "Check", "Empty")),
            ObjType(BottomlessMilkBucket.BUCKET, "Bucket"),
            ObjType(BottomlessMilkBucket.BUCKET_OF_MILK, "Bucket of milk"),
            ObjType(BottomlessMilkBucket.CHOCOLATEY_MILK, "Chocolatey milk"),
            ObjType(BottomlessMilkBucket.MILKY_MIXTURE, "Milky mixture"),
            ObjType(BottomlessMilkBucket.HANGOVER_CURE, "Hangover cure"),
            ObjType(BottomlessMilkBucket.CHOCOLATE_DUST, "Chocolate dust"),
            ObjType(Churn.CREAM, "Pot of cream"),
            ObjType(CowbellAmulet.EMPTY, "Cowbell amulet", iops = listOf(null, "Wear", "Ring", "Charge", "Drop"), wearpos1 = 2),
            ObjType(Churn.BUTTER, "Pat of butter"),
            ObjType(Churn.CHEESE, "Cheese"),
            *CowSlippers.ALL.map { ObjType(it, "Cow slippers", iops = listOf(null, "Wear", "Swap", null, "Drop"), wearpos1 = 10) }.toTypedArray(),
        )
        World(
            crcs, CollisionMap().apply { markLoaded(COW_X shr 6, COW_Z shr 6) }, objTypes = objs, random = GameRandom(5),
            locTypes = LocTypes(
                mapOf(
                    COW to LocType(COW, "Dairy cow", listOf("Milk", null, null, null, null), box),
                    CHURN to LocType(CHURN, "Dairy churn", listOf("Churn", null, null, null, null), box),
                ),
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(COW, LocShape.CENTREPIECE_STRAIGHT, 0, COW_X, COW_Z, 0))
                add(ZoneLoc(CHURN, LocShape.CENTREPIECE_STRAIGHT, 0, CHURN_X, CHURN_Z, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(93).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(93).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.op(id: Int, op: Int) =
        ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, InventoryTab.IOP_BY_BUTTON_OP.entries.first { it.value == op }.key, slotOf(id), id)

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().lastOrNull { it.text == text } }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport") { position?.takeIf { it.x == x && it.z == z } }
    }

    private fun use(id: Int, on: Int, client: HeadlessClient) = client.ifButtonT(
        InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(id), id, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(on), on,
    )

    @Test
    fun `the bucket fills after its warning, checks, makes chocolatey milk, empties, and won't mix milks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val alice = server.world.playerNamed("alice")!!
                client.give(BottomlessMilkBucket.EMPTY)
                client.op(BottomlessMilkBucket.EMPTY, BottomlessMilkBucket.FILL)
                client.seen(DialogueText.NONE[BottomlessLines.NO_MILK])
                client.op(BottomlessMilkBucket.EMPTY, BottomlessMilkBucket.CHECK)
                client.seen(DialogueText.NONE[BottomlessLines.CHECK_EMPTY])

                // Three buckets of milk; the warning, Yes, two of them.
                client.give(BottomlessMilkBucket.BUCKET_OF_MILK, 3)
                client.op(BottomlessMilkBucket.EMPTY, BottomlessMilkBucket.FILL)
                client.awaitDialogue("the warning") { it is DialogueView.Message && it.text == DialogueText.NONE[BottomlessLines.FIRST_WARNING] }
                client.continueDialogue()
                client.awaitDialogue("the question") { it is DialogueView.Menu && it.title == DialogueText.NONE[BottomlessLines.FIRST_PROCEED] }
                client.chooseOption(1)
                client.awaitTicks(2)
                client.resumeCount(2)
                client.seen(DialogueText.NONE[BottomlessLines.FILLED, "amount" to 2, "type" to DialogueText.NONE[BottomlessLines.TYPE_MILK], "total" to 2])
                assertEquals(1, client.count(BottomlessMilkBucket.FILLED))
                assertEquals(listOf(1L, 2L), listOf(client.count(BottomlessMilkBucket.BUCKET_OF_MILK), client.count(BottomlessMilkBucket.BUCKET)))

                // The last bucket used on it: no warning now, no amount for one.
                use(BottomlessMilkBucket.BUCKET_OF_MILK, BottomlessMilkBucket.FILLED, client)
                client.seen(DialogueText.NONE[BottomlessLines.FILLED_ONE, "type" to DialogueText.NONE[BottomlessLines.TYPE_MILK], "total" to 3])
                client.op(BottomlessMilkBucket.FILLED, BottomlessMilkBucket.CHECK)
                client.seen(DialogueText.NONE[BottomlessLines.CHECK, "amount" to 3, "s" to "s", "type" to DialogueText.NONE[BottomlessLines.TYPE_MILK]])

                // Another milk won't go in.
                client.give(BottomlessMilkBucket.HANGOVER_CURE)
                use(BottomlessMilkBucket.HANGOVER_CURE, BottomlessMilkBucket.FILLED, client)
                client.seen(DialogueText.NONE[BottomlessLines.WRONG_MILK])

                // Chocolate dust: a chocolatey milk for a use.
                client.give(BottomlessMilkBucket.CHOCOLATE_DUST)
                use(BottomlessMilkBucket.CHOCOLATE_DUST, BottomlessMilkBucket.FILLED, client)
                client.await("chocolatey milk") { if (client.count(BottomlessMilkBucket.CHOCOLATEY_MILK) == 1L) Unit else null }
                assertEquals(0, client.count(BottomlessMilkBucket.CHOCOLATE_DUST))
                assertEquals(2, BottomlessMilkBucket.uses(alice))

                // Empty, after its warning: No keeps it, Yes discards it.
                client.op(BottomlessMilkBucket.FILLED, BottomlessMilkBucket.EMPTY_OP)
                client.awaitDialogue("Empty's warning") { it is DialogueView.Message && it.text == DialogueText.NONE[BottomlessLines.EMPTY_WARNING] }
                client.continueDialogue()
                client.awaitDialogue("Empty's question") { it is DialogueView.Menu && it.title == DialogueText.NONE[BottomlessLines.EMPTY_PROCEED] }
                client.chooseOption(2)
                client.awaitTicks(2)
                assertEquals(2, BottomlessMilkBucket.uses(alice))
                client.op(BottomlessMilkBucket.FILLED, BottomlessMilkBucket.EMPTY_OP)
                client.awaitDialogue("Empty's warning") { it is DialogueView.Message }
                client.continueDialogue()
                client.awaitDialogue("Empty's question") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.seen(DialogueText.NONE[BottomlessLines.EMPTIED])
                client.await("empty") { if (client.count(BottomlessMilkBucket.EMPTY) == 1L) Unit else null }
                assertEquals(0, BottomlessMilkBucket.uses(alice))

                // Empty, the hangover cure goes in.
                use(BottomlessMilkBucket.HANGOVER_CURE, BottomlessMilkBucket.EMPTY, client)
                client.awaitDialogue("the warning") { it is DialogueView.Message }
                client.continueDialogue()
                client.awaitDialogue("the question") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.seen(DialogueText.NONE[BottomlessLines.FILLED_ONE, "type" to DialogueText.NONE[BottomlessLines.TYPE_HANGOVER], "total" to 1])
                assertEquals(BottomlessMilkBucket.TYPES[3], BottomlessMilkBucket.type(alice))
            }
        }
    }

    @Test
    fun `a new empty bucket banked beside a filled one joins its slot when the milk next changes, so one id has one slot`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cass").use { client ->
                client.await("the login rebuild") { client.position }
                val cass = server.world.playerNamed("cass")!!
                cass.bank[0] = InvObj(BottomlessMilkBucket.FILLED, 1)
                cass.bank[1] = InvObj(BottomlessMilkBucket.EMPTY, 1)
                BottomlessMilkBucket.set(cass, BottomlessMilkBucket.REGULAR, 4, server.world)
                assertEquals(listOf(InvObj(BottomlessMilkBucket.FILLED, 2)), cass.bank.contents().map { it.second })
            }
        }
    }

    @Test
    fun `a dairy cow milks into it every 7 ticks, ahead of empty buckets, and the churn takes its milk`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val bob = server.world.playerNamed("bob")!!
                client.give(BottomlessMilkBucket.EMPTY)
                client.give(GroatsFarm.BUCKET)
                client.tele(COW_X - 1, COW_Z)
                client.locOp(COW, COW_X, COW_Z, GroatsFarm.MILK)
                val first = client.await("the first use") { if (BottomlessMilkBucket.uses(bob) == 1) server.world.tick else null }
                val second = client.await("the second use") { if (BottomlessMilkBucket.uses(bob) == 2) server.world.tick else null }
                assertEquals(BottomlessMilkBucket.MILK_TICKS.toLong(), second - first)
                assertEquals(1, client.count(GroatsFarm.BUCKET), "the empty bucket left alone")
                client.await("a third") { if (BottomlessMilkBucket.uses(bob) >= 3) Unit else null }

                // With a cowbell amulet worn, a use every 6 ticks. Wearing it stops the milking, so milk again.
                client.give(CowbellAmulet.EMPTY)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 3, client.inventory(93).indexOfFirst { it?.id == CowbellAmulet.EMPTY }, CowbellAmulet.EMPTY)
                client.await("the amulet on") { client.inventory(94).firstOrNull { it?.id == CowbellAmulet.EMPTY } }
                client.awaitTicks(1)
                val worn = BottomlessMilkBucket.uses(bob)
                client.locOp(COW, COW_X, COW_Z, GroatsFarm.MILK)
                val third = client.await("a use worn") { if (BottomlessMilkBucket.uses(bob) == worn + 1) server.world.tick else null }
                val fourth = client.await("another worn") { if (BottomlessMilkBucket.uses(bob) == worn + 2) server.world.tick else null }
                assertEquals(CowbellAmulet.MILK_TICKS.toLong(), fourth - third)

                // Off to the churn, which takes the milk for cream.
                client.tele(CHURN_X - 1, CHURN_Z)
                client.command("setlevel cooking 99")
                client.awaitTicks(2)
                val have = BottomlessMilkBucket.uses(bob)
                val before = client.clientScripts().count { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }
                client.locOp(CHURN, CHURN_X, CHURN_Z, 1)
                client.await("the Make-X box") { client.clientScripts().filter { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }.drop(before).lastOrNull() }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                client.await("two creams", 30_000) { if (client.count(Churn.CREAM) == 2L) Unit else null }
                assertEquals(have - 2, BottomlessMilkBucket.uses(bob))
                assertEquals(1, client.count(GroatsFarm.BUCKET), "no bucket back from the bottomless one")
            }
        }
    }

    @Test
    fun `Swap goes round the three styles, sometimes to the rare one, which asks before swapping away`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(CowSlippers.PLAIN)
                val seen = mutableListOf(CowSlippers.PLAIN)
                var rare = false
                repeat(400) {
                    if (rare) return@repeat
                    val now = seen.last()
                    client.op(now, CowSlippers.SWAP)
                    val next = client.await("swapped") { client.inventory(93).firstNotNullOfOrNull { it?.id?.takeIf { id -> id in CowSlippers.ALL && id != now } } }
                    if (next == CowSlippers.RARE) rare = true else assertEquals(CowSlippers.STYLES[(CowSlippers.STYLES.indexOf(now) + 1) % 3], next)
                    seen += next
                }
                assertTrue(rare, "the rare style within 400 swaps")
                client.op(CowSlippers.RARE, CowSlippers.SWAP)
                client.awaitDialogue("the question") { it is DialogueView.Menu && it.title == DialogueText.NONE[CowSlippersLines.SWAP_RARE] }
                client.chooseOption(2)
                client.awaitTicks(2)
                assertEquals(1, client.count(CowSlippers.RARE))
                client.op(CowSlippers.RARE, CowSlippers.SWAP)
                client.awaitDialogue("the question") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.await("the first style") { if (client.count(CowSlippers.PLAIN) == 1L) Unit else null }
            }
        }
    }

    private companion object {
        const val COW = 8689
        const val COW_X = 3254
        const val COW_Z = 3272
        const val CHURN = 11695
        const val CHURN_X = 3250
        const val CHURN_Z = 3266
    }
}
