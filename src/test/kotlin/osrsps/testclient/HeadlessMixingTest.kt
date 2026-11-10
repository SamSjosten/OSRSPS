package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.skill.HerbloreData
import osrsps.map.CollisionMap

/**
 * Artisan 2 step A2a through the headless client: super combat potions from four ingredients, stamina and extended
 * antifire by the dose, and sanfew serum's three steps.
 */
class HeadlessMixingTest {
    private val data = HerbloreData.load()

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val ids = data.mixing.values.flatten().flatMap { r -> r.inputs.map { it.obj } + r.product }
        val objs = arrayOfNulls<ObjType>(ids.max() + 1)
        for (id in ids) objs[id] = ObjType(id, "Item $id", stackable = id in STACKS, tradeable = true)
        objs[TORSTOL] = ObjType(TORSTOL, "Torstol", tradeable = true)
        objs[SUPER_COMBAT] = ObjType(SUPER_COMBAT, "Super combat potion(4)", tradeable = true)
        objs[AMYLASE] = ObjType(AMYLASE, "Amylase crystal", stackable = true, tradeable = true)
        objs[VIAL] = ObjType(VIAL, "Vial", tradeable = true)
        World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), cycleMillis = millis)
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.use(id: Int, target: Int) =
        ifButtonT(InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(id), id, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(target), target)

    private fun HeadlessClient.boxes() = clientScripts().count { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }

    /** Uses [id] on [target] and picks the first item of the new Make-X box, [quantity] of it. */
    private fun HeadlessClient.mix(id: Int, target: Int, quantity: Int = 1) {
        val before = boxes()
        use(id, target)
        await("a new Make-X box") { boxes().takeIf { it > before } }
        resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, quantity)
    }

    private fun HeadlessClient.ready(level: Int = 99) {
        await("the login rebuild") { position }
        command("setlevel herblore $level")
        await("level $level") { stat(HERBLORE)?.takeIf { it.level == level } }
    }

    private fun HeadlessClient.xpOver99() = (stat(HERBLORE)?.xp ?: 0) - 13_034_431

    @Test
    fun `a torstol on a super attack mixes the three super potions into a super combat potion(4) for 150 XP, at 90`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready(level = 89)
                for (id in listOf(TORSTOL, SUPER_ATTACK, SUPER_STRENGTH, SUPER_DEFENCE)) client.give(id)
                client.mix(TORSTOL, SUPER_ATTACK)
                client.seen("[herblore.level level=90 item=super combat potion(4)]")
                client.ready()
                client.mix(SUPER_DEFENCE, TORSTOL)
                client.await("the super combat") { if (client.count(SUPER_COMBAT) == 1) Unit else null }
                for (id in listOf(TORSTOL, SUPER_ATTACK, SUPER_STRENGTH, SUPER_DEFENCE)) assertEquals(0, client.count(id), "item $id used")
                assertEquals(2, client.count(VIAL), "two of the three potions' vials back")
                assertEquals(150, client.await("150 XP") { client.xpOver99().takeIf { it >= 150 } })
            }
        }
    }

    @Test
    fun `amylase on a super energy(3) takes 3 crystals a potion, 76,5 XP each, 2 ticks apart`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.give(SUPER_ENERGY_3, 3)
                client.give(AMYLASE, 7)
                client.mix(AMYLASE, SUPER_ENERGY_3, quantity = 2)
                val made = client.await("two staminas") { client.messages().filter { it.text.startsWith("[herblore.mix") }.takeIf { it.size >= 2 } }
                assertEquals(2, made[1].tick - made[0].tick)
                client.await("two stamina(3)") { if (client.count(STAMINA_3) == 2) Unit else null }
                assertEquals(1, client.count(SUPER_ENERGY_3))
                assertEquals(1, client.count(AMYLASE))
                assertEquals(153, client.await("153 XP") { client.xpOver99().takeIf { it >= 153 } })
            }
        }
    }

    @Test
    fun `sanfew serum's three steps go one potion a use with no box, 192 XP for a serum(4)`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                client.give(SUPER_RESTORE_4, 2)
                client.give(UNICORN_HORN_DUST, 2)
                for (id in listOf(SNAKE_WEED, NAIL_BEAST_NAILS)) client.give(id)
                client.use(UNICORN_HORN_DUST, SUPER_RESTORE_4)
                client.await("step 1") { if (client.count(SANFEW_STEP_1_4) == 1) Unit else null }
                client.awaitTicks(4)
                assertEquals(1, client.count(SANFEW_STEP_1_4), "one potion a use, though two could be mixed")
                assertEquals(1, client.count(SUPER_RESTORE_4))
                client.use(SNAKE_WEED, SANFEW_STEP_1_4)
                client.await("step 2") { if (client.count(SANFEW_STEP_2_4) == 1) Unit else null }
                client.use(SANFEW_STEP_2_4, NAIL_BEAST_NAILS)
                client.await("the serum") { if (client.count(SANFEW_4) == 1) Unit else null }
                assertEquals(192, client.await("192 XP") { client.xpOver99().takeIf { it >= 192 } })
                assertEquals(0, client.boxes(), "no Make-X box for any step")
            }
        }
    }

    @Test
    fun `lava scale shards on an antifire potion(2) make an extended antifire(2) for 55 XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready()
                client.give(ANTIFIRE_2)
                client.give(LAVA_SHARD, 1)
                // One shard isn't enough for two doses: no box.
                client.use(LAVA_SHARD, ANTIFIRE_2)
                client.awaitTicks(3)
                assertTrue(client.boxes() == 0, "no box with one shard")
                client.give(LAVA_SHARD, 1)
                client.mix(LAVA_SHARD, ANTIFIRE_2)
                client.await("the extended antifire") { if (client.count(EXTENDED_ANTIFIRE_2) == 1) Unit else null }
                assertEquals(0, client.count(LAVA_SHARD))
                assertEquals(55, client.await("55 XP") { client.xpOver99().takeIf { it >= 55 } })
            }
        }
    }

    private companion object {
        const val VIAL = 229
        const val TORSTOL = 269
        const val SUPER_ATTACK = 2436
        const val SUPER_STRENGTH = 2440
        const val SUPER_DEFENCE = 2442
        const val SUPER_COMBAT = 12695
        const val SUPER_ENERGY_3 = 3018
        const val STAMINA_3 = 12627
        const val AMYLASE = 12640
        const val SUPER_RESTORE_4 = 3024
        const val UNICORN_HORN_DUST = 235
        const val SNAKE_WEED = 1526
        const val NAIL_BEAST_NAILS = 10937
        const val SANFEW_STEP_1_4 = 10909
        const val SANFEW_STEP_2_4 = 10917
        const val SANFEW_4 = 10925
        const val ANTIFIRE_2 = 2456
        const val LAVA_SHARD = 11994
        const val EXTENDED_ANTIFIRE_2 = 11955
        val STACKS = setOf(12640, 11994, 10937)
        const val BACKPACK = 93
        const val HERBLORE = 15
    }
}
