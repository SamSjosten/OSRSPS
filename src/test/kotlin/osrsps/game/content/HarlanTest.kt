package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.GameFrame
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.content.Harlan.giveBack
import osrsps.game.content.Harlan.talk

/** Harlan, the melee combat tutor: combat step 7. Keys show for words, as with no dialogue file. */
class HarlanTest {
    private val harness = DialogueHarness("Melee combat tutor")
    private val player = harness.player
    private val given = mutableListOf<Int>()
    private val tabs = mutableListOf<Int>()
    private val tutor = Harlan.Tutor(give = { _, obj -> given += obj }, sideTab = { _, tab -> tabs += tab })
    private val root = arrayOf("harlan.melee", "harlan.weapon_types", "harlan.skillcapes", "harlan.training_kit", "harlan.goodbye")

    private fun start() {
        harness.talk { talk(tutor) }
    }

    @Test
    fun `melee combat opens the worn and combat tabs, then the human option takes melee's place`() {
        start()
        harness.expect("harlan.greeting")
        harness.menu(*root)
        harness.pick(1)
        harness.expect("harlan.melee", "harlan.melee_1", "harlan.melee_2")
        assertEquals(listOf(GameFrame.SIDE_TAB_WORN), tabs)
        harness.next()
        harness.expect(
            "harlan.melee_3", "harlan.melee_4", "harlan.styles", "harlan.accurate", "harlan.aggressive", "harlan.defensive",
            "harlan.controlled", "harlan.attack_types_1", "harlan.attack_types_2", "harlan.hover", "harlan.melee_anything_else",
        )
        assertEquals(listOf(GameFrame.SIDE_TAB_WORN, GameFrame.SIDE_TAB_COMBAT), tabs)
        harness.menu("harlan.human", *root.drop(1).toTypedArray())
        harness.pick(1)
        harness.expect(
            "harlan.human", "harlan.human_1", "harlan.human_2", "harlan.capture_the_flag", "harlan.human_3",
            "harlan.human_4", "harlan.human_5", "harlan.human_6", "harlan.human_7", "harlan.human_8", "harlan.human_9",
            "harlan.human_anything_else",
        )
        harness.menu(*root)
        harness.pick(5)
        harness.expect("harlan.goodbye")
        harness.ends()
    }

    @Test
    fun `weapon types and skillcapes come back to the first menu`() {
        start()
        harness.expect("harlan.greeting")
        harness.menu(*root)
        harness.pick(2)
        harness.expect("harlan.weapon_types", "harlan.weapons_1", "harlan.weapons_2", "harlan.weapons_3", "harlan.weapons_4", "harlan.weapons_anything_else")
        harness.menu(*root)
        harness.pick(3)
        harness.expect("harlan.skillcapes", "harlan.capes_1", "harlan.capes_2", "harlan.capes_anything_else")
        harness.menu(*root)
    }

    @Test
    fun `a player with neither gets the training sword and shield, and a second ask is refused`() {
        start()
        harness.expect("harlan.greeting")
        harness.menu(*root)
        harness.pick(4)
        harness.expect("harlan.training_kit", "harlan.gives_both")
        assertEquals(listOf(Harlan.TRAINING_SWORD, Harlan.TRAINING_SHIELD), given)
        assertEquals(listOf(Harlan.TRAINING_SWORD, Harlan.TRAINING_SHIELD), harness.boxObjs)
        // The fake hands nothing over, so put them where the real one would.
        player.inventory.add(ObjType(Harlan.TRAINING_SWORD, "Training sword"), 1)
        player.inventory.add(ObjType(Harlan.TRAINING_SHIELD, "Training shield"), 1)
        harness.next()
        harness.expect("harlan.there_you_go", "harlan.kit_anything_else")
        harness.menu(*root)
        harness.pick(4)
        harness.expect("harlan.training_kit", "harlan.has_both", "harlan.kit_anything_else")
        assertEquals(2, given.size)
    }

    @Test
    fun `a worn training shield counts, so only the sword is given`() {
        player.equipment[WearPos.SHIELD] = InvObj(Harlan.TRAINING_SHIELD, 1)
        start()
        harness.expect("harlan.greeting")
        harness.menu(*root)
        harness.pick(4)
        harness.expect("harlan.training_kit", "harlan.gives_sword", "harlan.there_you_go", "harlan.kit_anything_else")
        assertEquals(listOf(Harlan.TRAINING_SWORD), given)
        assertEquals(listOf(Harlan.TRAINING_SWORD), harness.boxObjs)
    }

    @Test
    fun `at 99 Defence he sells the cape for 99,000 coins, trimmed with another 99, the hood free`() {
        player.skills.setLevel(Skill.DEFENCE, 99)
        player.skills.setLevel(Skill.ATTACK, 99)
        player.inventory.add(ObjType(Harlan.COINS, "Coins", stackable = true), 100_000)
        start()
        harness.expect("harlan.master_1", "harlan.master_2")
        harness.menu("harlan.buy_cape", "harlan.master_weapon_types")
        harness.pick(1)
        harness.expect("harlan.buy_cape_ask", "harlan.price")
        harness.menu("harlan.too_expensive", "harlan.have_money")
        harness.pick(2)
        harness.expect("harlan.have_money", "harlan.sold")
        assertEquals(listOf(Harlan.DEFENCE_CAPE_TRIMMED, Harlan.DEFENCE_HOOD), given)
        assertEquals(1_000, player.inventory.count(Harlan.COINS))
        harness.ends()
    }

    @Test
    fun `at 99 Defence with too few coins or no room nothing is sold`() {
        player.skills.setLevel(Skill.DEFENCE, 99)
        player.inventory.add(ObjType(Harlan.COINS, "Coins", stackable = true), 98_999)
        start()
        harness.expect("harlan.master_1", "harlan.master_2")
        harness.menu("harlan.buy_cape", "harlan.master_weapon_types")
        harness.pick(1)
        harness.expect("harlan.buy_cape_ask", "harlan.price")
        harness.menu("harlan.too_expensive", "harlan.have_money")
        harness.pick(2)
        harness.expect("harlan.have_money", "harlan.no_money", "harlan.no_money_reply")
        harness.ends()

        player.inventory.add(ObjType(Harlan.COINS, "Coins", stackable = true), 1)
        val bones = ObjType(526, "Bones")
        player.inventory.add(bones, player.inventory.freeSlots - 1)
        start()
        harness.expect("harlan.master_1", "harlan.master_2")
        harness.menu("harlan.buy_cape", "harlan.master_weapon_types")
        harness.pick(1)
        harness.expect("harlan.buy_cape_ask", "harlan.price")
        harness.menu("harlan.too_expensive", "harlan.have_money")
        harness.pick(2)
        // Exactly 99,000 coins free their slot, which with the one free slot is room for both.
        harness.expect("harlan.have_money", "harlan.sold")
        assertEquals(listOf(Harlan.DEFENCE_CAPE, Harlan.DEFENCE_HOOD), given)
        assertEquals(0, player.inventory.count(Harlan.COINS))
    }

    @Test
    fun `a training sword used on him is taken back`() {
        player.inventory.add(ObjType(Harlan.TRAINING_SWORD, "Training sword"), 1)
        harness.talk { giveBack(Harlan.TRAINING_SWORD, 0) }
        harness.expect("harlan.return_sword", "harlan.return_sword_reply", "harlan.returned_sword")
        assertTrue(player.inventory.isEmpty())
        assertEquals(listOf(Harlan.TRAINING_SWORD), harness.boxObjs)
        harness.ends()
    }
}
