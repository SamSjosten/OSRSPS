package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals

class ItemWeightsTest {
    // Made-up items shaped like the 241 cache's: a weight-reducing hood has a second obj named ..._worn.
    private val platebody = ObjType(1127, "Rune platebody", wearpos1 = WearPos.BODY, weight = 9979, internalName = "rune_platebody")
    private val hood = ObjType(11850, "Graceful hood", wearpos1 = WearPos.HEAD, weight = 600, internalName = "graceful_hood")
    private val hoodWorn = ObjType(11851, "Graceful hood", wearpos1 = WearPos.HEAD, weight = -3000, internalName = "graceful_hood_worn")
    private val boots = ObjType(88, "Boots of lightness", wearpos1 = WearPos.FEET, weight = 340, internalName = "ikov_bootsoflightness")
    private val bootsWorn = ObjType(89, "Boots of lightness", wearpos1 = WearPos.FEET, weight = -4535, internalName = "ikov_bootsoflightnessworn")
    private val arrows = ObjType(892, "Rune arrow", stackable = true, wearpos1 = WearPos.AMMO, weight = 16, internalName = "rune_arrow")
    private val types = ObjTypes.of(platebody, hood, hoodWorn, boots, bootsWorn, arrows)
    private val weights = ItemWeights(types)
    private val player = Player(1, "test", 3222, 3218, 0)

    @Test
    fun `inventory and worn items add up`() {
        player.inventory.add(platebody, 1)
        player.equipment[WearPos.BODY] = InvObj(platebody.id, 1)
        assertEquals(19_958, weights.total(player))
        assertEquals(19, ItemWeights.kilograms(weights.total(player)))
    }

    @Test
    fun `weight-reducing gear lightens only while worn, by its worn obj's weight`() {
        player.inventory.add(hood, 1)
        player.inventory.add(boots, 1)
        assertEquals(940, weights.total(player))
        player.inventory.clear()
        player.equipment[WearPos.HEAD] = InvObj(hood.id, 1)
        player.equipment[WearPos.FEET] = InvObj(boots.id, 1)
        assertEquals(-7535, weights.total(player))
        assertEquals(-7, ItemWeights.kilograms(weights.total(player)), "toward zero, as Equipment Stats shows -4.535 kg as -4")
    }

    @Test
    fun `stackable items weigh nothing`() {
        player.inventory.add(arrows, 1000)
        player.equipment[WearPos.AMMO] = InvObj(arrows.id, 500)
        assertEquals(0, weights.total(player))
    }
}
