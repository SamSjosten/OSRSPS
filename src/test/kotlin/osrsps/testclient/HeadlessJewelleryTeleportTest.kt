package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.EquipmentTab
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.Spells
import osrsps.game.skill.magic.JewelleryTeleportData
import osrsps.game.skill.magic.JewelleryTeleports
import osrsps.game.skill.magic.TeleportObjs
import osrsps.map.CollisionMap

/**
 * The teleport jewellery (Magic utility step U4) through the headless client, with the real jewellery file and the
 * items shaped as the 242 cache has them (their worn options): a ring of dueling rubbed in the backpack opens the menu
 * and goes to Emir's Arena for a charge, and its last charge crumbles it; a worn amulet of glory's own option goes to
 * Edgeville and leaves the uncharged amulet, which then has no charges; the glory works at level 25 Wilderness where
 * the ring doesn't, and neither works above 30; the skills necklace's six places page behind More.
 * test-runner/examples/magic-jewellery-teleports.json does it in RuneLite.
 */
class HeadlessJewelleryTeleportTest {
    private fun objs(): List<ObjType> {
        val glory = listOf("Edgeville", "Karamja", "Draynor Village", "Al Kharid")
        val dueling = listOf("Emir's Arena", "Castle Wars", "Ferox Enclave", "Fortis Colosseum")
        val skills = listOf("Fishing Guild", "Mining Guild", "Crafting Guild", "Cooking Guild", "Woodcutting Guild", "Farming Guild")
        fun worn(names: List<String>) = names.withIndex().associate { (i, name) -> EquipmentTab.PARAM_WORN_OP1 + i to name as Any }
        val rub = listOf(null, "Wear", null, "Rub", "Drop")
        fun chain(ids: List<Int>, name: String, key: String, wearpos: Int, places: List<String>) = ids.mapIndexed { i, id ->
            val charges = ids.size - i
            ObjType(id, "$name($charges)", wearpos1 = wearpos, iops = rub, params = worn(places), internalName = "${key}_$charges")
        }
        return chain(GLORIES, "Amulet of glory", "amulet_of_glory", WearPos.NECK, glory) +
            chain(DUELINGS, "Ring of dueling", "ring_of_dueling", WearPos.RING, dueling) +
            chain(SKILLS_NECKLACES, "Skills necklace", "jewl_necklace_of_skills", WearPos.NECK, skills) +
            listOf(
                ObjType(GLORY, "Amulet of glory", wearpos1 = WearPos.NECK, iops = rub, params = worn(listOf("Rub")), internalName = "amulet_of_glory"),
                ObjType(SKILLS, "Skills necklace", wearpos1 = WearPos.NECK, iops = rub, params = worn(listOf("Rub")), internalName = "jewl_necklace_of_skills"),
            )
    }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objTypes = TeleportObjs.objTypes(*objs().toTypedArray())
        World(
            crcs, CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(3100 shr 6, 3714 shr 6)
                markLoaded(3100 shr 6, 3762 shr 6)
            }, random = osrsps.game.GameRandom(7), objTypes = objTypes,
            spells = Spells.load(objTypes, osrsps.game.SeqNames.NONE, osrsps.game.SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int): Int = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.give(id: Int) {
        val before = count(id)
        command("item $id 1")
        await("item $id") { if (count(id) == before + 1) Unit else null }
    }

    private fun HeadlessClient.itemOp(id: Int, iop: Int) = ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, IOP_BUTTON_OPS[iop - 1], slotOf(id), id)

    /** Waits until [text] has come [times] times since login (the messages of one tick come together). */
    private fun HeadlessClient.saw(description: String, text: String, times: Int = 1) =
        await(description) { if (messages().count { it.text == text } >= times) Unit else null }

    private fun HeadlessClient.ready() {
        await("the login rebuild") { position }
        commandReply("keys on") { it == "Dialogue shows keys" }
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        commandReply("tele $x $z") { it.startsWith("Teleported") }
        await("at $x, $z", 20_000) { position?.takeIf { it.x == x && it.z == z } }
    }

    private fun HeadlessClient.menu(description: String, options: List<String>) {
        val view = awaitDialogue(description) { it is DialogueView.Menu } as DialogueView.Menu
        assertEquals("[jewellery_teleport.menu_title]", view.title)
        assertEquals(options, view.options)
    }

    private fun HeadlessClient.landsWithin(x: Int, z: Int, r: Int) =
        await("landed within $r of $x, $z", 10_000) { position?.takeIf { kotlin.math.abs(it.x - x) <= r && kotlin.math.abs(it.z - z) <= r && it.level == 0 } }

    @Test
    fun `the real file loads, and a worn option that isn't the place's name fails it`() {
        val text = JewelleryTeleportData::class.java.getResource(JewelleryTeleportData.RESOURCE)!!.readText()
        val data = JewelleryTeleportData.parse(text, ObjTypes.of(*objs().toTypedArray()))
        val keys = data.jewellery.map { it.key }
        assertEquals(listOf("glory", "dueling", "skills"), keys, "the others' items aren't in this cache")
        val glory = data.jewellery.first()
        assertEquals(GLORY_1, glory.after(GLORY_2))
        assertEquals(GLORY, glory.after(GLORY_1))
        assertNull(data.jewellery[1].after(DUELING_1), "a ring of dueling crumbles")
        val wrong = objs().map { if (it.id == DUELING_2) ObjType(it.id, it.name, params = mapOf(EquipmentTab.PARAM_WORN_OP1 to "Duel Arena"), internalName = it.internalName) else it }
        val failure = assertFailsWith<IllegalArgumentException> { JewelleryTeleportData.parse(text, ObjTypes.of(*wrong.toTypedArray())) }
        assertTrue("Duel Arena" in failure.message!!, failure.message)
    }

    @Test
    fun `a rubbed ring of dueling goes to Emir's Arena for a charge, and crumbles on its last`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.give(DUELING_2)
                client.itemOp(DUELING_2, RUB)
                client.menu("the ring's places", listOf("Emir's Arena", "Castle Wars", "Ferox Enclave", "Fortis Colosseum", "[jewellery_teleport.nowhere]"))
                client.chooseOption(1)
                client.saw("the rub", "[jewellery_teleport.rub_ring]")
                client.saw("the uses left", "[jewellery_teleport.one_use_left item=ring of dueling]")
                client.landsWithin(3315, 3235, 2)
                assertEquals(1, client.count(DUELING_1))
                client.itemOp(DUELING_1, RUB)
                client.menu("the ring's places again", listOf("Emir's Arena", "Castle Wars", "Ferox Enclave", "Fortis Colosseum", "[jewellery_teleport.nowhere]"))
                client.chooseOption(5)
                client.awaitTicks(4)
                assertEquals(1, client.count(DUELING_1), "Nowhere keeps the charge")
                client.itemOp(DUELING_1, RUB)
                client.menu("the ring's places a third time", listOf("Emir's Arena", "Castle Wars", "Ferox Enclave", "Fortis Colosseum", "[jewellery_teleport.nowhere]"))
                client.chooseOption(2)
                client.saw("the crumbling", "[jewellery_teleport.crumbles item=ring of dueling]")
                client.landsWithin(2440, 3090, 0)
                assertEquals(0, client.count(DUELING_1))
            }
        }
    }

    @Test
    fun `a worn glory's own option goes to Edgeville, its last charge leaving the uncharged amulet`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.give(GLORY_1)
                client.itemOp(GLORY_1, WEAR)
                client.await("the glory worn") { client.inventory(WORN).getOrNull(WearPos.NECK)?.takeIf { it.id == GLORY_1 } }
                // Worn op 1 is Remove; op 2 is the first place.
                client.ifButton(EquipmentTab.WORNITEMS, NECK_SLOT, 2)
                client.saw("the rub", "[jewellery_teleport.rub_amulet]")
                client.saw("the last charge", "[jewellery_teleport.last_charge item=amulet of glory]")
                client.landsWithin(3087, 3496, 0)
                client.await("the uncharged amulet worn") { client.inventory(WORN).getOrNull(WearPos.NECK)?.takeIf { it.id == GLORY } }
                client.ifButton(EquipmentTab.WORNITEMS, NECK_SLOT, 2)
                client.saw("no charges", "[jewellery_teleport.no_charges item=amulet of glory]")
            }
        }
    }

    @Test
    fun `the glory works at level 25 Wilderness where the ring of dueling doesn't, and nothing above 30`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                client.give(DUELING_2)
                client.give(GLORY_2)
                // Level 25 is z 3712 to 3719.
                client.tele(3100, 3714)
                client.itemOp(DUELING_2, RUB)
                client.menu("the ring's places", listOf("Emir's Arena", "Castle Wars", "Ferox Enclave", "Fortis Colosseum", "[jewellery_teleport.nowhere]"))
                client.chooseOption(1)
                client.saw("the ring refused", "[home_teleport.wilderness_2]")
                assertEquals(1, client.count(DUELING_2))
                client.tele(3100, 3762)
                client.itemOp(GLORY_2, RUB)
                client.menu("the glory's places at 31", listOf("Edgeville", "Karamja", "Draynor Village", "Al Kharid", "[jewellery_teleport.nowhere]"))
                client.chooseOption(1)
                client.saw("the glory refused at 31", "[jewellery_teleport.wilderness_30]")
                assertEquals(1, client.count(GLORY_2))
                client.tele(3100, 3714)
                client.itemOp(GLORY_2, RUB)
                client.menu("the glory's places at 25", listOf("Edgeville", "Karamja", "Draynor Village", "Al Kharid", "[jewellery_teleport.nowhere]"))
                client.chooseOption(3)
                client.landsWithin(3105, 3251, 0)
                assertEquals(1, client.count(GLORY_1))
            }
        }
    }

    @Test
    fun `the skills necklace's six places page behind More`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready()
                client.give(SKILLS_1)
                client.itemOp(SKILLS_1, RUB)
                client.menu("the first page", listOf("Fishing Guild", "Mining Guild", "Crafting Guild", "Cooking Guild", "[jewellery_teleport.more]"))
                client.chooseOption(5)
                client.menu("the second page", listOf("Woodcutting Guild", "Farming Guild", "[jewellery_teleport.more]"))
                client.chooseOption(3)
                client.menu("back to the first", listOf("Fishing Guild", "Mining Guild", "Crafting Guild", "Cooking Guild", "[jewellery_teleport.more]"))
                client.chooseOption(1)
                client.landsWithin(2611, 3390, 0)
                assertEquals(1, client.count(SKILLS))
                assertEquals(JewelleryTeleports.RUB, RUB)
            }
        }
    }

    companion object {
        const val BACKPACK = 93
        const val WORN = 94
        const val NECK_SLOT = 17

        const val WEAR = 2
        const val RUB = 4

        /** The inventory's iop 1 to 5 as IF_BUTTON ops. */
        val IOP_BUTTON_OPS = intArrayOf(2, 3, 4, 6, 7)

        const val GLORY = 1704
        const val GLORY_1 = 1706
        const val GLORY_2 = 1708
        val GLORIES = listOf(11978, 11976, 1712, 1710, GLORY_2, GLORY_1)
        const val DUELING_1 = 2566
        const val DUELING_2 = 2564
        val DUELINGS = listOf(2552, 2554, 2556, 2558, 2560, 2562, DUELING_2, DUELING_1)
        const val SKILLS = 11113
        const val SKILLS_1 = 11111
        val SKILLS_NECKLACES = listOf(11968, 11970, 11105, 11107, 11109, SKILLS_1)
    }
}
