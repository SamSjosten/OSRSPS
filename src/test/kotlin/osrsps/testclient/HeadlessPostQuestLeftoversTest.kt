package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.PostQuestLeftovers
import osrsps.game.content.PostQuestLeftoversLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Post-quest content step 16 through the headless client: Rat Burgiss's broken cart searched, swamp bubbles sucked
 * into ogre bellows (all of them, then one used on the bubbles), and Lucien's door letting the player through. The
 * rl-client script test-runner/examples/post-quest-leftovers.json does the same on the real map.
 */
class HeadlessPostQuestLeftoversTest {
    private val open = LocCollisionType(width = 1, length = 1, interactType = 0, blocksProjectile = false)

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            random = GameRandom(4),
            objTypes = ObjTypes.of(
                ObjType(PostQuestLeftovers.BELLOWS_EMPTY, "Ogre bellows"), ObjType(PostQuestLeftovers.BELLOWS_3, "Ogre bellows (3)"),
                ObjType(PostQuestLeftovers.BELLOWS_2, "Ogre bellows (2)"), ObjType(PostQuestLeftovers.BELLOWS_1, "Ogre bellows (1)"),
            ),
            locTypes = LocTypes(
                listOf(
                    LocType(PostQuestLeftovers.CART, "Broken cart", listOf("Search", null, null, null, null), LocCollisionType(width = 1, length = 1)),
                    LocType(PostQuestLeftovers.SWAMP_BUBBLES, "Swamp bubbles", listOf("Suck", null, null, null, null), open),
                    LocType(PostQuestLeftovers.LUCIEN_DOOR, "Door", listOf("Open", null, null, null, null), LocCollisionType(width = 1, length = 1)),
                    LocType(PostQuestLeftovers.LUCIEN_DOOR_OPEN, "null", List(5) { null }, LocCollisionType(width = 1, length = 1)),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(PostQuestLeftovers.CART, LocShape.CENTREPIECE_STRAIGHT, 0, CART_X, CART_Z, 0))
                add(ZoneLoc(PostQuestLeftovers.SWAMP_BUBBLES, LocShape.GROUND_DECOR, 0, BUBBLES_X, BUBBLES_Z, 0))
                add(ZoneLoc(PostQuestLeftovers.LUCIEN_DOOR, LocShape.WALL_STRAIGHT, 0, DOOR_X, DOOR_Z, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().count { it.id == id }

    private fun HeadlessClient.message(line: Line) = await(line.key) { messages().lastOrNull()?.takeIf { it.text == DialogueText.NONE[line] } }

    @Test
    fun `the cart is searched, the bubbles fill ogre bellows, and Lucien's door lets the player through`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 3222 3218 0")
                client.await("placed") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }

                client.locOp(PostQuestLeftovers.CART, CART_X, CART_Z, PostQuestLeftovers.SEARCH)
                client.awaitDialogue("the cart's box") { it == DialogueView.Message(DialogueText.NONE[PostQuestLeftoversLines.CART]) }
                client.continueDialogue()

                // No bellows: nothing to fill.
                client.locOp(PostQuestLeftovers.SWAMP_BUBBLES, BUBBLES_X, BUBBLES_Z, PostQuestLeftovers.SUCK)
                client.message(PostQuestLeftoversLines.NO_BELLOWS)

                // Suck fills every unfilled bellows.
                client.commandReply("item ${PostQuestLeftovers.BELLOWS_EMPTY} 1") { it.isNotEmpty() }
                client.commandReply("item ${PostQuestLeftovers.BELLOWS_1} 1") { it.isNotEmpty() }
                client.locOp(PostQuestLeftovers.SWAMP_BUBBLES, BUBBLES_X, BUBBLES_Z, PostQuestLeftovers.SUCK)
                client.await("both filled") { if (client.count(PostQuestLeftovers.BELLOWS_3) == 2) Unit else null }
                client.message(PostQuestLeftoversLines.BELLOWS_FILLED)
                client.awaitTicks(2)
                client.locOp(PostQuestLeftovers.SWAMP_BUBBLES, BUBBLES_X, BUBBLES_Z, PostQuestLeftovers.SUCK)
                client.message(PostQuestLeftoversLines.BELLOWS_FULL)

                // One bellows used on the bubbles fills that one alone.
                client.commandReply("item ${PostQuestLeftovers.BELLOWS_2} 1") { it.isNotEmpty() }
                client.commandReply("item ${PostQuestLeftovers.BELLOWS_EMPTY} 1") { it.isNotEmpty() }
                val slot = client.await("the (2)") { client.inventory(BACKPACK).indexOfFirst { it?.id == PostQuestLeftovers.BELLOWS_2 }.takeIf { it >= 0 } }
                client.locTarget(PostQuestLeftovers.SWAMP_BUBBLES, BUBBLES_X, BUBBLES_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, PostQuestLeftovers.BELLOWS_2)
                client.await("the (2) filled") { if (client.count(PostQuestLeftovers.BELLOWS_3) == 3) Unit else null }
                assertEquals(1, client.count(PostQuestLeftovers.BELLOWS_EMPTY), "the other left empty")

                // Lucien's door: through to the other side.
                client.command("tele ${DOOR_X + 2} $DOOR_Z 0")
                client.await("by the door") { client.position?.takeIf { it == Coord(DOOR_X + 2, DOOR_Z, 0) } }
                client.locOp(PostQuestLeftovers.LUCIEN_DOOR, DOOR_X, DOOR_Z, PostQuestLeftovers.OPEN)
                client.await("through the door") { client.position?.takeIf { it.x == DOOR_X - 1 && it.z == DOOR_Z } }
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val CART_X = 3226
        const val CART_Z = 3220
        const val BUBBLES_X = 3220
        const val BUBBLES_Z = 3214
        const val DOOR_X = 3230
        const val DOOR_Z = 3214
    }
}
