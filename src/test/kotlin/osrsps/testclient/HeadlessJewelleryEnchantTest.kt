package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.Spells
import osrsps.game.skill.magic.JewelleryEnchant
import osrsps.game.skill.magic.JewelleryEnchantData
import osrsps.game.skill.magic.TeleportObjs
import osrsps.map.CollisionMap

/**
 * Lvl-1 Enchant (Magic utility step U3) through the headless client, with the real jewellery file and the spell obj
 * shaped as the 242 cache has it: cast on a sapphire ring it becomes a ring of recoil in the same slot for 17.5 XP and
 * the runes, then the next sapphire ring follows 7 ticks later on its own; walking stops that; an item the spell
 * doesn't take, too low a level or too few runes enchants nothing. test-runner/examples/magic-jewellery-enchant.json
 * does it in RuneLite.
 */
class HeadlessJewelleryEnchantTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val params = HashMap<Int, Any>()
        params[Spells.PARAM_NAME] = "Lvl-1 Enchant"
        params[Spells.PARAM_LEVEL] = 7
        params[Spells.PARAM_COMPONENT] = (Spells.SPELLBOOK shl 16) or COMPONENT
        params[Spells.PARAM_RUNES[0].first] = COSMIC
        params[Spells.PARAM_RUNES[0].second] = 1
        params[Spells.PARAM_RUNES[1].first] = TeleportObjs.WATER
        params[Spells.PARAM_RUNES[1].second] = 1
        val objTypes = TeleportObjs.objTypes(
            ObjType(COSMIC, "Cosmic rune", stackable = true, internalName = "cosmicrune"),
            ObjType(SPELL, "null", params = params, internalName = "07_enchant_amulet_lvl1"),
            ObjType(SAPPHIRE_RING, "Sapphire ring", wearpos1 = WearPos.RING, internalName = "sapphire_ring"),
            ObjType(RECOIL, "Ring of recoil", wearpos1 = WearPos.RING, internalName = "ring_of_recoil"),
            ObjType(SAPPHIRE_AMULET, "Sapphire amulet", wearpos1 = WearPos.NECK, internalName = "strung_sapphire_amulet"),
            ObjType(AMULET_OF_MAGIC, "Amulet of magic", wearpos1 = WearPos.NECK, internalName = "amulet_of_magic"),
            ObjType(RUBY_RING, "Ruby ring", wearpos1 = WearPos.RING, internalName = "ruby_ring"),
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

    private fun HeadlessClient.ready(level: Int) {
        await("the login rebuild") { position }
        commandReply("keys on") { it == "Dialogue shows keys" }
        commandReply("setlevel magic $level") { it.contains("set to level $level") }
        await("Magic $level") { stat(MAGIC)?.takeIf { it.level == level } }
    }

    @Test
    fun `the file loads every spell's items from the cache's names`() {
        val data = JewelleryEnchantData.parse(
            JewelleryEnchantData::class.java.getResource(JewelleryEnchantData.RESOURCE)!!.readText(),
            osrsps.game.ObjTypes.of(*listOf(
                ObjType(SPELL, "null", params = mapOf(Spells.PARAM_LEVEL to 7, Spells.PARAM_COMPONENT to ((Spells.SPELLBOOK shl 16) or COMPONENT), Spells.PARAM_RUNES[0].first to COSMIC, Spells.PARAM_RUNES[0].second to 1), internalName = "07_enchant_amulet_lvl1"),
                ObjType(SAPPHIRE_RING, "Sapphire ring", wearpos1 = WearPos.RING, internalName = "sapphire_ring"),
                ObjType(RECOIL, "Ring of recoil", internalName = "ring_of_recoil"),
            ).toTypedArray()),
        )
        val lvl1 = data.enchantments.single()
        assertEquals(1, lvl1.tier)
        assertEquals(175, lvl1.spell.xpTenths)
        assertTrue(lvl1.items.single().ring)
    }

    @Test
    fun `a sapphire ring becomes a ring of recoil in its slot, and the next follows 7 ticks later until walking stops it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready(7)
                val start = client.stat(MAGIC)?.xp ?: 0
                client.give(SAPPHIRE_RING, 3)
                client.give(COSMIC, 5)
                client.give(TeleportObjs.WATER, 5)
                val slot = client.slotOf(SAPPHIRE_RING)
                client.cast(SAPPHIRE_RING)
                client.await("a ring of recoil") { if (client.count(RECOIL) == 1) Unit else null }
                assertEquals(RECOIL, client.inventory(BACKPACK)[slot]?.id, "in the same slot")
                assertEquals(17, client.await("17.5 XP") { ((client.stat(MAGIC)?.xp ?: 0) - start).takeIf { it >= 17 } })
                client.await("the second ring", 20_000) { if (client.count(RECOIL) == 2) Unit else null }
                val casts = client.await("the second cast's animation") { client.animationTicks().takeIf { it.size >= 2 } }.takeLast(2)
                assertEquals(JewelleryEnchant.RECAST_TICKS, casts[1] - casts[0], "7 ticks on")
                client.walk(3222, 3220)
                client.awaitTicks(JewelleryEnchant.RECAST_TICKS + 2)
                assertEquals(2, client.count(RECOIL), "walking stopped the third")
                assertEquals(3, client.count(COSMIC))
            }
        }
    }

    @Test
    fun `the wrong item, too low a level and too few runes enchant nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready(6)
                client.give(SAPPHIRE_AMULET)
                client.give(RUBY_RING)
                client.give(COSMIC, 1)
                client.cast(RUBY_RING)
                client.awaitMessage("the wrong item") { it == "[jewellery_enchant.wrong_item]" }
                client.cast(SAPPHIRE_AMULET)
                client.awaitMessage("the level") { it == "[combat.magic_level]" }
                client.commandReply("setlevel magic 7") { it.contains("set to level 7") }
                client.await("Magic 7") { client.stat(MAGIC)?.takeIf { it.level == 7 } }
                client.cast(SAPPHIRE_AMULET)
                client.awaitMessage("the runes") { it.startsWith("[combat.not_enough_runes") }
                assertEquals(1, client.count(SAPPHIRE_AMULET))
                assertEquals(1, client.count(COSMIC))
                client.give(TeleportObjs.WATER)
                client.cast(SAPPHIRE_AMULET)
                client.await("an amulet of magic") { if (client.count(AMULET_OF_MAGIC) == 1) Unit else null }
            }
        }
    }

    companion object {
        const val BACKPACK = 93
        const val MAGIC = 6

        const val COSMIC = 564
        const val SPELL = 3276
        const val COMPONENT = 16
        const val SAPPHIRE_RING = 1637
        const val RECOIL = 2550
        const val SAPPHIRE_AMULET = 1694
        const val AMULET_OF_MAGIC = 1727
        const val RUBY_RING = 1641
    }
}
