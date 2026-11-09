package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.content.LumbridgeGuide.talk

/** The Lumbridge Guide as since 242.1 (Lumbridge 242 step 4b), with no dialogue file: every line shows its key. */
class LumbridgeGuideTest {
    private val h = DialogueHarness(
        "Lumbridge Guide", VarTypes.NONE.with(mapOf(LumbridgeGuide.VARBIT_MET to VarbitType(6241, 17, 17)), emptyMap()),
    )

    init {
        // Most tests talk to a Guide the player has met; the first test clears it.
        h.player.vars.setVarbit(LumbridgeGuide.VARBIT_MET, 1)
    }

    private fun expect(vararg keys: String) = h.expect(*keys.map { "lumbridge_guide.$it" }.toTypedArray())

    private fun menu(vararg keys: String) = h.menu(*keys.map { "lumbridge_guide.$it" }.toTypedArray())

    private fun firstMenu() = menu("goals", "quest_to_go_on", "combat", "skills", "more_options")

    private fun moreMenu() = menu("make_money", "find_bank", "be_off", "previous_options")

    private fun start(greeting: String = "hello") {
        h.talk { with(LumbridgeGuide) { talk() } }
        expect(greeting)
        firstMenu()
    }

    @Test
    fun `the first talk is his introduction and sets the met varbit, later ones are hello`() {
        h.player.vars.setVarbit(LumbridgeGuide.VARBIT_MET, 0)
        start("greeting_first")
        assertEquals(1, h.player.vars.getVarbit(LumbridgeGuide.VARBIT_MET))
        h.pick(5)
        moreMenu()
        h.pick(3)
        expect("be_off", "until_next")
        h.ends()
        start()
    }

    @Test
    fun `goals, the quest and combat each come back to the first menu, combat sending the player to Captain Harlan`() {
        start()
        h.pick(1)
        expect("goals", "goals_answer")
        firstMenu()
        h.pick(2)
        expect("quest_to_go_on", "no_more_quests")
        firstMenu()
        h.pick(3)
        expect("combat_harlan")
        firstMenu()
    }

    @Test
    fun `the skills tips show their icon boxes and come back to the first menu`() {
        start()
        h.pick(4)
        expect(
            "skills", "skills_1", "skills_2", "skills_mining", "hint_mining", "skills_smithing", "hint_furnace", "hint_anvil",
            "skills_fishing", "hint_fishing", "hint_range", "skills_woodcutting", "hint_trees", "skills_3", "skills_4", "skills_5",
            "hint_guide",
        )
        assertEquals(
            listOf(
                LumbridgeGuide.MINING_ICON_OBJ, LumbridgeGuide.FURNACE_ICON_OBJ, LumbridgeGuide.FISHING_ICON_OBJ,
                LumbridgeGuide.RANGE_ICON_OBJ, LumbridgeGuide.TREE_ICON_OBJ,
            ),
            h.boxObjs,
        )
        firstMenu()
    }

    @Test
    fun `money and the bank come back to the more options menu, and previous goes to the first`() {
        start()
        h.pick(5)
        moreMenu()
        h.pick(1)
        expect("make_money", "money_1", "money_2", "money_3", "money_4", "money_5", "money_6", "money_7")
        moreMenu()
        h.pick(2)
        expect("find_bank", "bank_roof", "hint_bank")
        assertEquals(listOf(LumbridgeGuide.BANK_ICON_OBJ), h.boxObjs)
        moreMenu()
        h.pick(4)
        firstMenu()
    }
}
