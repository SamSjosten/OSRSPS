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
import osrsps.game.combat.Foods
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Cooking
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Brutus step 3's t-bone steak through the headless client, with the real food file: a raw t-bone steak (his
 * always-drop) used on a range cooks at 99 for 90 XP, and the cooked steak eaten heals 9 and boosts Strength by 2.
 * The rl-client script test-runner/examples/brutus-tbone.json cooks and eats one in the Lumbridge Castle kitchen.
 */
class HeadlessTboneSteakTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Cooking.TBONE.burnt + 1)
        objs[Cooking.TBONE.raw] = ObjType(Cooking.TBONE.raw, "Raw t-bone steak", internalName = "raw_tbone_steak")
        objs[Cooking.TBONE.cooked] = ObjType(Cooking.TBONE.cooked, "Cooked t-bone steak", iops = listOf("Eat", null, null, null, "Drop"), internalName = "tbone_steak")
        objs[Cooking.TBONE.burnt] = ObjType(Cooking.TBONE.burnt, "Burnt t-bone steak", internalName = "burnt_tbone_steak")
        val objTypes = ObjTypes(objs)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objTypes, foodList = Foods.load(objTypes), random = GameRandom(7),
            locTypes = LocTypes(mapOf(Cooking.RANGE to LocType(Cooking.RANGE, "Range", listOf("Cook", null, null, null, null), LocCollisionType(width = 1, length = 1)))),
            staticLocs = StaticLocs().apply { add(ZoneLoc(Cooking.RANGE, LocShape.CENTREPIECE_STRAIGHT, 0, RANGE_X, RANGE_Z, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    @Test
    fun `a raw t-bone steak cooks for 90 XP, and the cooked one heals 9 and boosts Strength by 2`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel cooking 99") { it.contains("set to level 99") }
                client.commandReply("setlevel hitpoints 99") { it.contains("set to level 99") }
                client.commandReply("setlevel strength 99") { it.contains("set to level 99") }
                val xp = client.await("cooking 99") { client.stat(COOKING)?.takeIf { it.level == 99 } }.xp
                client.command("item ${Cooking.TBONE.raw}")
                val raw = client.slotOf(Cooking.TBONE.raw)
                client.locTarget(Cooking.RANGE, RANGE_X, RANGE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, raw, Cooking.TBONE.raw)
                client.await("the cooking message") { client.messages().firstOrNull { it.text == "[cooking.cooked_tbone]" } }
                val cooked = client.slotOf(Cooking.TBONE.cooked)
                assertEquals(90, client.await("Cooking XP") { client.stat(COOKING)?.takeIf { it.xp > xp } }.xp - xp)

                client.commandReply("hit 20 me") { it == "Hit yourself for 20: 79/99 hp" }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, EAT_BUTTON_OP, cooked, Cooking.TBONE.cooked)
                client.await("88 hitpoints") { client.stat(HITPOINTS)?.takeIf { it.level == 88 } }
                client.await("Strength 101") { client.stat(STRENGTH)?.takeIf { it.level == 101 } }
                client.await("eaten") { if (client.inventory(BACKPACK).none { it?.id == Cooking.TBONE.cooked }) Unit else null }
            }
        }
    }

    @Test
    fun `a cooked t-bone steak lifts a drained Strength by 2, not back to its level`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel strength 99") { it.contains("set to level 99") }
                client.commandReply("boost strength 50") { it == "Strength is now 50/99" }
                client.command("item ${Cooking.TBONE.cooked} 2")
                val first = client.slotOf(Cooking.TBONE.cooked)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, EAT_BUTTON_OP, first, Cooking.TBONE.cooked)
                client.await("Strength 52") { client.stat(STRENGTH)?.takeIf { it.level == 52 } }

                client.commandReply("boost strength 100") { it == "Strength is now 100/99" }
                client.await("Strength 100") { client.stat(STRENGTH)?.takeIf { it.level == 100 } }
                client.awaitTicks(4) // past the 3-tick eating delay
                val second = client.slotOf(Cooking.TBONE.cooked)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, EAT_BUTTON_OP, second, Cooking.TBONE.cooked)
                client.await("eaten") { if (client.inventory(BACKPACK).none { it?.id == Cooking.TBONE.cooked }) Unit else null }
                // Capped at 99 + 2.
                client.await("Strength 101") { client.stat(STRENGTH)?.takeIf { it.level == 101 } }
            }
        }
    }

    private companion object {
        const val RANGE_X = 3224
        const val RANGE_Z = 3218
        const val BACKPACK = 93
        const val STRENGTH = 2
        const val HITPOINTS = 3
        const val COOKING = 7

        /** Eat is iop1, which the inventory sends as button op 2. */
        const val EAT_BUTTON_OP = 2
    }
}
