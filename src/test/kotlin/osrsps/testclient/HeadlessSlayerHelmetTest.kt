package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.EquipmentTab
import osrsps.game.GameRandom
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.skill.Slayer
import osrsps.game.skill.SlayerHelmets
import osrsps.map.CollisionMap

/**
 * Slayer step 7 through the headless client: the Slayer helmet's assembly refused without Malevolent Masquerade, below
 * 55 Crafting and without every part, then made from a charged black mask; its Check, held and worn; Disassemble
 * refused without room, then giving the parts and an uncharged mask back; and a black mask (i) making the helmet (i).
 */
class HeadlessSlayerHelmetTest {
    private val helmetOps = listOf(null, "Wear", "Check", "Disassemble", "Drop")
    private val wornCheck = mapOf<Int, Any>(EquipmentTab.PARAM_WORN_OP1 to "Check")

    // The 242 cache's names and options for the parts, a charged and an uncharged black mask, the black mask (i) and the helmets.
    private val types = listOf(
        ObjType(SlayerHelmets.EARMUFFS, "Earmuffs", iops = WEAR, wearpos1 = WearPos.HEAD),
        ObjType(SlayerHelmets.FACEMASK, "Facemask", iops = WEAR, wearpos1 = WearPos.HEAD),
        ObjType(SlayerHelmets.NOSE_PEG, "Nose peg", iops = WEAR, wearpos1 = WearPos.HEAD),
        ObjType(SlayerHelmets.SPINY_HELMET, "Spiny helmet", iops = WEAR, wearpos1 = WearPos.HEAD),
        ObjType(SlayerHelmets.REINFORCED_GOGGLES, "Reinforced goggles", iops = listOf(null, "Wear", null, null, "Destroy"), wearpos1 = WearPos.HEAD),
        ObjType(Slayer.ENCHANTED_GEM, "Enchanted gem", iops = listOf("Activate", "Check", "Partner", "Log", "Drop")),
        ObjType(CHARGED_MASK, "Black mask (10)", iops = listOf(null, "Wear", null, "Uncharge", "Drop"), wearpos1 = WearPos.HEAD),
        ObjType(BLACK_MASK, "Black mask", iops = WEAR, wearpos1 = WearPos.HEAD),
        ObjType(BLACK_MASK_I, "Black mask (i)", iops = listOf(null, "Wear", null, "Uncharge", "Drop"), wearpos1 = WearPos.HEAD),
        ObjType(SlayerHelmets.SLAYER_HELMET, "Slayer helmet", iops = helmetOps, wearpos1 = WearPos.HEAD, params = wornCheck),
        ObjType(HELMET_I, "Slayer helmet (i)", iops = helmetOps, wearpos1 = WearPos.HEAD, params = wornCheck),
        ObjType(FILLER, "Pot", iops = listOf(null, null, null, null, "Drop")),
    )

    private val parts = SlayerHelmets.PARTS

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = ObjTypes.of(*types.toTypedArray()), cycleMillis = millis)
    }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).count { it?.id == id }

    /** Waits for [n] of [id] in the pack: the message can arrive a packet before the inventory's update. */
    private fun HeadlessClient.awaitCount(id: Int, n: Int) = await("$n of item $id") { if (count(id) == n) Unit else null }

    private fun HeadlessClient.slot(id: Int) = await("item $id in the backpack") { inventory(Inventory.INV).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.use(id: Int, target: Int) = ifButtonT(INVENTORY, INVENTORY_ITEMS, slot(id), id, INVENTORY, INVENTORY_ITEMS, slot(target), target)

    private fun HeadlessClient.op(id: Int, iop: Int) = ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[iop - 1], slot(id), id)

    /** Waits for a message [text] after the [before] messages already seen. */
    private fun HeadlessClient.seen(text: String, before: Int) = await(text) { messages().drop(before).firstOrNull { it.text == text } }

    private fun HeadlessClient.ready(level: Int) {
        commandReply("setlevel crafting $level") { it.contains("set to level $level") }
    }

    private fun HeadlessClient.unlock() {
        commandReply("varp ${Slayer.UNLOCK_VARPS[0]} ${1 shl SlayerHelmets.MALEVOLENT_MASQUERADE}") { it.startsWith("Varp ") }
    }

    @Test
    fun `the helmet is made only with the unlock, the level and every part, checks the task, and comes apart again`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ivy").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(CHARGED_MASK)
                for (part in parts - SlayerHelmets.REINFORCED_GOGGLES) client.give(part)

                var before = client.messages().size
                client.use(Slayer.ENCHANTED_GEM, SlayerHelmets.EARMUFFS)
                client.seen("[slayer_helmet.need_unlock]", before)

                client.unlock()
                client.ready(54)
                before = client.messages().size
                client.use(SlayerHelmets.FACEMASK, CHARGED_MASK)
                client.seen("[slayer_helmet.need_crafting]", before)

                client.ready(55)
                before = client.messages().size
                client.use(SlayerHelmets.FACEMASK, CHARGED_MASK)
                client.seen("[slayer_helmet.missing_parts]", before)

                client.give(SlayerHelmets.REINFORCED_GOGGLES)
                val maskSlot = client.slot(CHARGED_MASK)
                before = client.messages().size
                client.use(SlayerHelmets.NOSE_PEG, SlayerHelmets.SPINY_HELMET)
                client.seen("[slayer_helmet.assembled]", before)
                assertEquals(maskSlot, client.slot(SlayerHelmets.SLAYER_HELMET), "the helmet in the mask's slot")
                for (id in parts + CHARGED_MASK) client.awaitCount(id, 0)

                // Its Check is the gem's, held and worn.
                before = client.messages().size
                client.op(SlayerHelmets.SLAYER_HELMET, SlayerHelmets.CHECK)
                client.seen("[slayer.gem_no_task]", before)
                client.op(SlayerHelmets.SLAYER_HELMET, 2)
                client.await("the helmet worn") { client.inventory(WORN).getOrNull(WearPos.HEAD)?.takeIf { it.id == SlayerHelmets.SLAYER_HELMET } }
                before = client.messages().size
                client.ifButton(EquipmentTab.WORNITEMS, WORNITEMS_HEAD, 2)
                client.seen("[slayer.gem_no_task]", before)
                client.ifButton(EquipmentTab.WORNITEMS, WORNITEMS_HEAD, 1)
                client.slot(SlayerHelmets.SLAYER_HELMET)

                // 22 pots leave five free slots: not room for the six parts beyond the helmet's own.
                client.give(FILLER, 22)
                before = client.messages().size
                client.op(SlayerHelmets.SLAYER_HELMET, SlayerHelmets.DISASSEMBLE)
                client.seen("[slayer_helmet.no_room]", before)
                client.awaitCount(SlayerHelmets.SLAYER_HELMET, 1)

                client.op(FILLER, 5)
                client.await("a pot dropped") { if (client.count(FILLER) == 21) Unit else null }
                val helmetSlot = client.slot(SlayerHelmets.SLAYER_HELMET)
                before = client.messages().size
                client.op(SlayerHelmets.SLAYER_HELMET, SlayerHelmets.DISASSEMBLE)
                client.seen("[slayer_helmet.disassembled]", before)
                assertEquals(helmetSlot, client.slot(BLACK_MASK), "the uncharged mask in the helmet's slot")
                for (id in parts) client.awaitCount(id, 1)
                client.awaitCount(SlayerHelmets.SLAYER_HELMET, 0)
            }
        }
    }

    @Test
    fun `a black mask (i) makes the helmet (i), which comes apart into the black mask (i)`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("jade").use { client ->
                client.await("the login rebuild") { client.position }
                client.unlock()
                client.ready(55)
                client.give(BLACK_MASK_I)
                for (part in parts) client.give(part)

                var before = client.messages().size
                client.use(BLACK_MASK_I, Slayer.ENCHANTED_GEM)
                client.seen("[slayer_helmet.assembled]", before)
                client.awaitCount(HELMET_I, 1)
                client.awaitCount(BLACK_MASK_I, 0)

                before = client.messages().size
                client.op(HELMET_I, SlayerHelmets.DISASSEMBLE)
                client.seen("[slayer_helmet.disassembled]", before)
                client.awaitCount(BLACK_MASK_I, 1)
                client.awaitCount(HELMET_I, 0)
            }
        }
    }

    private companion object {
        val WEAR = listOf(null, "Wear", null, null, "Drop")
        const val CHARGED_MASK = 8901
        const val BLACK_MASK = 8921
        const val BLACK_MASK_I = 11784
        const val HELMET_I = 11865
        const val FILLER = 1931
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val WORN = 94
        const val WORNITEMS_HEAD = 15
        val IOP_BUTTON_OPS = intArrayOf(2, 3, 4, 6, 7)
    }
}
