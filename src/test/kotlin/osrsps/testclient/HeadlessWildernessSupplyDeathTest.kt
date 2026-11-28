package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.WearPos
import osrsps.game.combat.Deaths
import osrsps.game.combat.Foods
import osrsps.game.combat.Potions
import osrsps.map.CollisionMap

/**
 * A Wilderness death with no player killer through the headless client, with the real food and potion files and the
 * server's own wiring: the shark, the potion and the phoenix necklace it loses are deleted, and the bones go to the grave
 * (OSRS Wiki, Grave: "while in the Wilderness or on a PvP world, food, potions, and phoenix necklaces cannot be sent to a
 * grave or dropped, and are instead deleted"; exploit review R65X-1). The same death at Lumbridge graves all four. The
 * rl-client script test-runner/examples/wilderness-supply-death.json does it in the real client.
 */
class HeadlessWildernessSupplyDeathTest {
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(SUPER_COMBAT_4 + 1)
        objs[BONES] = ObjType(BONES, "Bones", cost = 1, internalName = "bones")
        objs[SHARK] = ObjType(SHARK, "Shark", cost = 1, iops = listOf("Eat", null, null, null, "Drop"), internalName = "shark")
        objs[PHOENIX] = ObjType(PHOENIX, "Phoenix necklace", cost = 1, wearpos1 = WearPos.NECK, internalName = "jewl_necklace_of_phoenix")
        objs[SUPER_COMBAT_4] = ObjType(SUPER_COMBAT_4, "Super combat potion(4)", cost = 1, iops = listOf("Drink", null, null, null, "Drop"), internalName = "4dose2combat")
        val objTypes = ObjTypes(objs)
        val map = CollisionMap().apply {
            markLoaded(3222 shr 6, 3218 shr 6)
            markLoaded(WILDY_X shr 6, WILDY_Z shr 6)
        }
        World(
            crcs, map, objTypes = objTypes, npcTypes = NpcTypes.of(grave), foodList = Foods.load(objTypes),
            potionData = Potions.load(objTypes), cycleMillis = millis,
        )
    }

    /** [name] logs in, carries the four items, and dies skulled (so keeping nothing) at [x], [z]: the ids in their grave. */
    private fun diedWithSupplies(server: TestServer, name: String, x: Int, z: Int): List<Int> = server.login(name).use { client ->
        client.await("the login rebuild") { client.position }
        val player = assertNotNull(server.world.playerNamed(name))
        for (id in listOf(SHARK, SUPER_COMBAT_4, PHOENIX, BONES)) client.command("item $id")
        client.await("the bag filled") { client.inventory(BACKPACK).takeIf { it.count { obj -> obj != null } == 4 } }
        client.commandReply("skull on") { it.startsWith("You're skulled") }
        client.commandReply("tele $x $z 0") { it.startsWith("Teleported") }
        client.await("the teleport to $x, $z") { client.position?.takeIf { it == Coord(x, z, 0) } }
        client.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
        client.await("the respawn", timeoutMillis = 10_000) { client.position?.takeIf { it == Coord(3222, 3218) && !player.dead } }
        client.await("the bag emptied") { client.inventory(BACKPACK).takeIf { it.all { obj -> obj == null } } }
        assertNotNull(server.world.deaths.graveOf(player)).items.contents().map { it.second.id }.sorted()
    }

    @Test
    fun `a Wilderness death to no player deletes the lost food, potion and phoenix necklace, and one at Lumbridge graves them`(@TempDir dir: Path) {
        server(dir).use { server ->
            assertEquals(listOf(BONES), diedWithSupplies(server, "alice", WILDY_X, WILDY_Z))
            assertEquals(listOf(SHARK, BONES, PHOENIX, SUPER_COMBAT_4), diedWithSupplies(server, "bob", 3220, 3220))
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val BONES = 526
        const val SHARK = 385
        const val PHOENIX = 11090
        const val SUPER_COMBAT_4 = 12695
        /** Level 13 Wilderness, north of Edgeville. */
        const val WILDY_X = 3100
        const val WILDY_Z = 3600
    }
}
