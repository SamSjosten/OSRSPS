package osrsps.game.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.World
import osrsps.map.CollisionMap

class ItemCommandsTest {
    private val types = ObjTypes.of(
        ObjType(1351, "Bronze axe", internalName = "bronze_axe"),
        ObjType(1352, "Bronze axe", stackable = true, certlink = 1351, certtemplate = 799, internalName = "cert_bronze_axe"),
        ObjType(995, "Coins", stackable = true, internalName = "coins"),
    )
    private val world = World(IntArray(0), CollisionMap(), objTypes = types)
    private val player = Player(1, "test", 3222, 3218, 0)
    private val replies = mutableListOf<String>()

    private fun run(input: String) = world.commands.execute(player, input, replies::add)

    @Test
    fun `item by id puts one in the inventory`() {
        run("item 1351")
        assertEquals(listOf("Added 1 x Bronze axe (1351)."), replies)
        assertEquals(InvObj(1351, 1), player.inventory[0])
    }

    @Test
    fun `item by gameval name, menu name and with a count`() {
        run("item coins 10k")
        run("item bronze axe 2")
        run("item cert_bronze_axe 5")
        assertEquals(
            listOf("Added 10,000 x Coins (995).", "Added 2 x Bronze axe (1351).", "Added 5 x Bronze axe (1352, noted)."),
            replies,
        )
        assertEquals(listOf(InvObj(995, 10_000), InvObj(1351, 1), InvObj(1351, 1), InvObj(1352, 5)), player.inventory.contents().map { it.second })
    }

    @Test
    fun `item fills what room there is`() {
        run("item 1351 30")
        run("item 1351")
        assertEquals(listOf("Added 28 x Bronze axe (1351); no room for the other 2.", "No room for Bronze axe."), replies)
    }

    @Test
    fun `item refuses an unknown item`() {
        run("item 4151")
        run("item dragon claws")
        run("item")
        assertEquals(
            listOf(
                "No item '4151'. Try its id, its name, or its gameval name like bronze_axe",
                "No item 'dragon claws'. Try its id, its name, or its gameval name like bronze_axe",
                "Usage: ::item id|name [count]",
            ),
            replies,
        )
    }

    @Test
    fun `empty clears the inventory`() {
        run("item 995 5")
        run("empty")
        assertTrue(player.inventory.isEmpty())
        assertEquals("Inventory emptied.", replies.last())
    }
}
