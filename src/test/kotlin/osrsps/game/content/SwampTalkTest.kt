package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.WearPos

/** Rest of Lumbridge step 9: the swamp's talks, with no dialogue file, so every line shows its key. */
class SwampTalkTest {
    private val h = DialogueHarness()

    /** The items handed over, in order. */
    private val given = mutableListOf<Int>()
    private val give: (Player, Int) -> Unit = { player, obj ->
        given += obj
        player.inventory[player.inventory.contents().size] = InvObj(obj, 1)
    }

    private fun urhney() {
        h.talk { with(FatherUrhney) { talk(give) } }
        h.expect("father_urhney.go_away")
        h.menu("father_urhney.friendly", "father_urhney.lost_amulet", "father_urhney.repossess")
    }

    @Test
    fun `Father Urhney sends the player off, and so does each grounds for taking his house`() {
        urhney()
        h.pick(1)
        h.expect("father_urhney.friendly", "father_urhney.still_go", "father_urhney.grouch")
        h.ends()

        urhney()
        h.pick(3)
        h.expect("father_urhney.repossess", "father_urhney.grounds")
        h.menu("father_urhney.mortgage", "father_urhney.wanted_it")
        h.pick(1)
        h.expect(
            "father_urhney.mortgage", "father_urhney.what", "father_urhney.built_it", "father_urhney.wrong_house",
            "father_urhney.what_houses", "father_urhney.never_mind",
        )
        h.ends()

        urhney()
        h.pick(3)
        h.expect("father_urhney.repossess", "father_urhney.grounds")
        h.menu("father_urhney.mortgage", "father_urhney.wanted_it")
        h.pick(2)
        h.expect("father_urhney.wanted_it", "father_urhney.get_out")
        h.ends()
    }

    @Test
    fun `a lost ghostspeak amulet is replaced only when it is nowhere else and there is room`() {
        fun lost(vararg keys: String) {
            urhney()
            h.pick(2)
            h.expect("father_urhney.lost_amulet", *keys)
            h.ends()
        }
        // A free slot and no amulet: a spare, handed over in an item box.
        lost("father_urhney.spare", "father_urhney.hands_amulet", "father_urhney.careful", "father_urhney.okay")
        assertEquals(listOf(FatherUrhney.GHOSTSPEAK_AMULET), given)
        assertEquals(listOf(FatherUrhney.GHOSTSPEAK_AMULET), h.boxObjs)
        // Carried, then worn.
        lost("father_urhney.have_it")
        h.player.inventory.clear()
        h.player.equipment[WearPos.NECK] = InvObj(FatherUrhney.GHOSTSPEAK_AMULET, 1)
        lost("father_urhney.have_it")
        h.player.equipment.clear()
        // In the bank.
        h.player.bank[0] = InvObj(FatherUrhney.GHOSTSPEAK_AMULET, 1)
        lost("father_urhney.in_bank")
        h.player.bank.clear()
        // Nowhere, and a full inventory.
        for (slot in 0 until 28) h.player.inventory[slot] = InvObj(1931, 1)
        lost("father_urhney.no_space")
        assertEquals(1, given.size)
    }

    @Test
    fun `the four adventurers each say their piece after Lost City`() {
        h.talk { with(AdventurersCamp) { warrior() } }
        h.expect("lost_city_warrior.thanks", "lost_city_warrior.party", "lost_city_warrior.asks", "lost_city_warrior.secret")
        h.ends()
        h.talk { with(AdventurersCamp) { archer() } }
        h.expect("lost_city_archer.found_it", "lost_city_archer.dont", "lost_city_archer.yep", "lost_city_archer.not", "lost_city_archer.believe")
        h.ends()
        h.talk { with(AdventurersCamp) { monk() } }
        h.expect("lost_city_monk.anymore")
        h.ends()
        h.talk { with(AdventurersCamp) { wizard() } }
        h.expect("lost_city_wizard.laugh", "lost_city_wizard.go", "lost_city_wizard.right")
        h.ends()
    }

    private val lesson = arrayOf(
        "candle_seller.lanterns", "candle_seller.lesson_out", "candle_seller.lesson_firstly", "candle_seller.lesson_then",
        "candle_seller.lesson_finally", "candle_seller.lesson_once", "candle_seller.lesson_for", "candle_seller.lesson_gold",
    )

    private fun candle(vararg options: String) {
        h.talk { with(CandleSeller) { talk(give) } }
        h.expect("candle_seller.offer")
        h.menu(*options)
    }

    private val first = arrayOf("candle_seller.yes", "candle_seller.thousand", "candle_seller.curse")

    @Test
    fun `the candle seller wants 1000 coins for a lit candle, and takes them`() {
        candle(*first)
        h.pick(1)
        h.expect("candle_seller.yes", "candle_seller.cant_afford", "candle_seller.no_candle")
        h.ends()
        assertEquals(emptyList(), given)

        h.player.inventory[0] = InvObj(CandleSeller.COINS, 1500)
        candle(*first)
        h.pick(1)
        h.expect("candle_seller.yes", "candle_seller.here", "candle_seller.careful")
        h.menu("candle_seller.naked_flame", "candle_seller.lanterns", "candle_seller.thanks_bye")
        h.pick(1)
        h.expect("candle_seller.naked_flame", "candle_seller.heh")
        h.ends()
        assertEquals(listOf(CandleSeller.LIT_CANDLE), given)
        assertEquals(500, h.player.inventory.count(CandleSeller.COINS))

        // The price explained, then no way; and the darkness cursed.
        candle(*first)
        h.pick(2)
        h.expect("candle_seller.thousand", "candle_seller.look", "candle_seller.so", "candle_seller.privilege")
        h.menu("candle_seller.you_win", "candle_seller.no_way", "candle_seller.lanterns")
        h.pick(2)
        h.expect("candle_seller.no_way")
        h.ends()
        candle(*first)
        h.pick(3)
        h.expect("candle_seller.curse")
        h.ends()
    }

    @Test
    fun `once the player has heard how lanterns are made, the first menu offers it again`() {
        candle(*first)
        h.pick(2)
        h.expect("candle_seller.thousand", "candle_seller.look", "candle_seller.so", "candle_seller.privilege")
        h.menu("candle_seller.you_win", "candle_seller.no_way", "candle_seller.lanterns")
        h.pick(3)
        h.expect(*lesson)
        h.menu("candle_seller.you_win", "candle_seller.curse")
        h.pick(2)
        h.expect("candle_seller.curse")
        h.ends()
        assertTrue(CandleSeller.heardLanterns(h.player))

        // Now four options; buying after the lesson still checks the coins.
        candle(*first, "candle_seller.lanterns")
        h.pick(4)
        h.expect(*lesson)
        h.menu("candle_seller.you_win", "candle_seller.curse")
        h.pick(1)
        h.expect("candle_seller.you_win", "candle_seller.cant_afford", "candle_seller.no_candle")
        h.ends()
    }

    @Test
    fun `the swamp's talking NPCs have examines`() {
        for (id in listOf(FatherUrhney.ID, AdventurersCamp.ARCHER, AdventurersCamp.WARRIOR, AdventurersCamp.MONK, AdventurersCamp.WIZARD, CandleSeller.ID)) {
            assertEquals("examine.npc_$id", Examines.npc(id)?.key, "NPC $id")
        }
    }
}
