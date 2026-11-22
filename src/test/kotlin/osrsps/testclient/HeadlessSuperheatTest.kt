package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.World
import osrsps.game.combat.Spells
import osrsps.game.skill.magic.Superheat
import osrsps.game.skill.magic.TeleportObjs
import osrsps.map.CollisionMap

/**
 * Superheat Item (Magic utility step U2) through the headless client, with the spell obj shaped as the 242 cache has
 * it: cast on an ore it smelts the bar with the furnace's ores and coal, for 53 Magic XP and the bar's Smithing XP;
 * iron ore makes steel with two coal held and a sure iron bar without; anything not ore, elemental ore, too low a
 * Smithing level or too little coal casts nothing. test-runner/examples/magic-superheat.json does the same in RuneLite.
 */
class HeadlessSuperheatTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val params = HashMap<Int, Any>()
        params[Spells.PARAM_NAME] = "Superheat Item"
        params[Spells.PARAM_LEVEL] = 43
        params[Spells.PARAM_COMPONENT] = (Spells.SPELLBOOK shl 16) or COMPONENT
        params[Spells.PARAM_RUNES[0].first] = TeleportObjs.FIRE
        params[Spells.PARAM_RUNES[0].second] = 4
        params[Spells.PARAM_RUNES[1].first] = NATURE
        params[Spells.PARAM_RUNES[1].second] = 1
        val objTypes = TeleportObjs.objTypes(
            ObjType(NATURE, "Nature rune", stackable = true, internalName = "naturerune"),
            ObjType(SPELL, "null", params = params, internalName = "43_superheat_item"),
            ObjType(IRON_ORE, "Iron ore", internalName = "iron_ore"),
            ObjType(COAL, "Coal", internalName = "coal"),
            ObjType(MITHRIL_ORE, "Mithril ore", internalName = "mithril_ore"),
            ObjType(COPPER, "Copper ore", internalName = "copper_ore"),
            ObjType(TIN, "Tin ore", internalName = "tin_ore"),
            ObjType(ELEMENTAL, "Elemental ore", internalName = "elemental_workshop_ore"),
            ObjType(IRON_BAR, "Iron bar", internalName = "iron_bar"),
            ObjType(STEEL_BAR, "Steel bar", internalName = "steel_bar"),
            ObjType(MITHRIL_BAR, "Mithril bar", internalName = "mithril_bar"),
            ObjType(BRONZE_BAR, "Bronze bar", internalName = "bronze_bar"),
        )
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = osrsps.game.GameRandom(7), objTypes = objTypes,
            spells = Spells.load(objTypes, osrsps.game.SeqNames.NONE, osrsps.game.SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int): Int = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of item $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.cast(item: Int) =
        ifButtonT(Spells.SPELLBOOK, COMPONENT, -1, -1, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(item), item)

    private fun HeadlessClient.xp(skill: Int): Int = stat(skill)?.xp ?: 0

    private fun HeadlessClient.ready(magic: Int, smithing: Int) {
        await("the login rebuild") { position }
        commandReply("keys on") { it == "Dialogue shows keys" }
        commandReply("setlevel magic $magic") { it.contains("set to level $magic") }
        commandReply("setlevel smithing $smithing") { it.contains("set to level $smithing") }
        await("the levels") { stat(SMITHING)?.takeIf { it.level == smithing } }
    }

    @Test
    fun `iron ore makes steel with two coal and a sure iron bar without, for both skills' XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready(43, 30)
                val magic = client.xp(MAGIC)
                val smithing = client.xp(SMITHING)
                client.give(IRON_ORE, 2)
                client.give(COAL, 2)
                client.give(NATURE, 2)
                client.give(TeleportObjs.FIRE, 8)
                client.cast(IRON_ORE)
                client.await("a steel bar") { if (client.count(STEEL_BAR) == 1) Unit else null }
                assertEquals(1, client.count(IRON_ORE))
                assertEquals(0, client.count(COAL))
                assertEquals(53, client.await("53 Magic XP") { (client.xp(MAGIC) - magic).takeIf { it >= 53 } })
                assertEquals(17, client.await("17 Smithing XP") { (client.xp(SMITHING) - smithing).takeIf { it >= 17 } })
                client.awaitTicks(3)
                client.cast(IRON_ORE)
                // Never impure, unlike at a furnace.
                client.await("an iron bar") { if (client.count(IRON_BAR) == 1) Unit else null }
                assertEquals(0, client.count(NATURE))
                assertEquals(0, client.count(TeleportObjs.FIRE))
            }
        }
    }

    @Test
    fun `a second cast clicked during the first's 3 ticks is kept and cast once they are up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready(43, 30)
                client.give(IRON_ORE, 2)
                client.give(COAL, 4)
                client.give(NATURE, 2)
                client.give(TeleportObjs.FIRE, 8)
                // The clock stopped, so the second click lands inside the first cast's 3 ticks however slow the machine.
                client.commandReply("pause") { it.startsWith("Paused") }
                client.cast(IRON_ORE)
                client.stepTicks(1)
                client.await("the first bar") { if (client.count(STEEL_BAR) == 1) Unit else null }
                client.cast(IRON_ORE)
                client.stepTicks(1)
                assertEquals(1, client.count(STEEL_BAR), "kept, not cast yet")
                assertEquals(1, client.count(IRON_ORE))
                client.stepTicks(SUPERHEAT_TICKS - 1)
                client.await("the second bar, cast when the clock allowed") { if (client.count(STEEL_BAR) == 2) Unit else null }
                assertEquals(0, client.count(NATURE))
                client.commandReply("resume") { it.isNotEmpty() }
            }
        }
    }

    @Test
    fun `bronze from tin, and refusals for coal, elemental ore, the Smithing level and too little coal`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready(43, 49)
                client.give(COPPER)
                client.give(TIN)
                client.give(NATURE, 5)
                client.give(TeleportObjs.FIRE, 20)
                client.cast(TIN)
                client.await("a bronze bar") { if (client.count(BRONZE_BAR) == 1) Unit else null }
                assertEquals(0, client.count(COPPER))
                client.awaitTicks(3)

                client.give(COAL, 3)
                client.cast(COAL)
                client.awaitMessage("coal isn't ore") { it == "[superheat.not_ore]" }
                client.give(ELEMENTAL)
                client.cast(ELEMENTAL)
                client.awaitMessage("elemental ore") { it == "[superheat.elemental]" }
                client.give(MITHRIL_ORE)
                client.cast(MITHRIL_ORE)
                client.awaitMessage("the Smithing level") { it.startsWith("[smithing.smelt_level") }
                client.commandReply("setlevel smithing 50") { it.contains("set to level 50") }
                client.await("Smithing 50") { client.stat(SMITHING)?.takeIf { it.level == 50 } }
                client.cast(MITHRIL_ORE)
                client.awaitMessage("too little coal") { it.startsWith("[smithing.smelt_no_ore") }
                assertEquals(1, client.count(MITHRIL_ORE))
                assertEquals(3, client.count(COAL))
                assertEquals(4, client.count(NATURE))

                client.give(COAL)
                client.cast(MITHRIL_ORE)
                client.await("a mithril bar") { if (client.count(MITHRIL_BAR) == 1) Unit else null }
                assertEquals(0, client.count(COAL))
            }
        }
    }

    companion object {
        /** Superheat Item's ticks between casts. */
        const val SUPERHEAT_TICKS = 3

        const val BACKPACK = 93
        const val MAGIC = 6
        const val SMITHING = 13

        const val NATURE = 561
        const val SPELL = 3295
        const val COMPONENT = 36
        const val IRON_ORE = Superheat.IRON_ORE
        const val COAL = Superheat.COAL
        const val MITHRIL_ORE = 447
        const val COPPER = 436
        const val TIN = 438
        const val ELEMENTAL = Superheat.ELEMENTAL_ORE
        const val IRON_BAR = 2351
        const val STEEL_BAR = 2353
        const val MITHRIL_BAR = 2359
        const val BRONZE_BAR = 2349
    }
}
