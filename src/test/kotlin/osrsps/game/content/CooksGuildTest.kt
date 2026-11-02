package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.InvObj
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.content.CooksGuild.headChef

/** Edgeville step 9: the Cooks' Guild's door check and the Head chef, with no dialogue file, so every line shows its key. */
class CooksGuildTest {
    private val h = DialogueHarness()
    private val given = mutableListOf<Int>()
    private var coinsTaken = 0

    private fun talk() = h.talk {
        headChef({ _, obj -> given += obj }, { _, count -> coinsTaken += count })
    }

    @Test
    fun `the door wants 32 Cooking and a chef's hat or the like`() {
        val p = h.player
        p.skills.setLevel(Skill.COOKING, 31)
        assertEquals(HeadChefLines.DOOR_LEVEL, CooksGuild.refusal(p))
        p.equipment[WearPos.HEAD] = InvObj(1949, 1)
        assertEquals(HeadChefLines.DOOR_LEVEL_HAT, CooksGuild.refusal(p))
        assertFalse(CooksGuild.mayEnter(p))

        p.skills.setLevel(Skill.COOKING, 32)
        assertNull(CooksGuild.refusal(p))
        assertTrue(CooksGuild.mayEnter(p))

        p.equipment[WearPos.HEAD] = null
        assertEquals(HeadChefLines.DOOR_HAT, CooksGuild.refusal(p))
        for (worn in listOf(20205 to WearPos.HEAD, CooksGuild.COOKING_CAPE to WearPos.CAPE, 13107 to WearPos.BODY, 13280 to WearPos.CAPE)) {
            p.equipment[worn.second] = InvObj(worn.first, 1)
            assertTrue(CooksGuild.mayEnter(p), "${worn.first} lets a player in")
            p.equipment[worn.second] = null
        }
    }

    @Test
    fun `below 99 the Head chef talks about his cape`() {
        h.player.skills.setLevel(Skill.COOKING, 50)
        talk()
        h.expect("head_chef.welcome")
        h.menu("head_chef.nice_cape", "head_chef.thanks_bye")
        h.pick(1)
        h.expect("head_chef.nice_cape_said", "head_chef.cape_info", "head_chef.cape_info_2")
        h.ends()

        talk()
        h.expect("head_chef.welcome")
        h.menu("head_chef.nice_cape", "head_chef.thanks_bye")
        h.pick(2)
        h.expect("head_chef.thanks_bye")
        h.ends()
        assertEquals(emptyList(), given)
    }

    private fun toPrice() {
        talk()
        h.expect("head_chef.master")
        h.menu("head_chef.no_thanks", "head_chef.yes_please")
        h.pick(2)
        h.expect("head_chef.buy", "head_chef.price")
        h.menu("head_chef.too_expensive", "head_chef.sure")
    }

    @Test
    fun `at 99 he sells the cape and hood for 99,000 coins`() {
        val p = h.player
        p.skills.setLevel(Skill.COOKING, 99)

        talk()
        h.expect("head_chef.master")
        h.menu("head_chef.no_thanks", "head_chef.yes_please")
        h.pick(1)
        h.expect("head_chef.no_thanks", "head_chef.change_mind")
        h.ends()

        toPrice()
        h.pick(1)
        h.expect("head_chef.too_expensive", "head_chef.shame")
        h.ends()

        p.inventory[0] = InvObj(CooksGuild.COINS, 98_999)
        toPrice()
        h.pick(2)
        h.expect("head_chef.sure", "head_chef.no_money", "head_chef.come_back")
        h.ends()
        assertEquals(0, coinsTaken)

        p.inventory[0] = InvObj(CooksGuild.COINS, 99_000)
        toPrice()
        h.pick(2)
        h.expect("head_chef.sure", "head_chef.sold")
        h.ends()
        assertEquals(99_000, coinsTaken)
        assertEquals(listOf(CooksGuild.COOKING_CAPE, CooksGuild.COOKING_HOOD), given)
    }

    @Test
    fun `with no room for the cape and hood he holds on to them`() {
        val p = h.player
        p.skills.setLevel(Skill.COOKING, 99)
        p.inventory[0] = InvObj(CooksGuild.COINS, 100_000)
        for (slot in 1 until 28) p.inventory[slot] = InvObj(1511, 1)
        toPrice()
        h.pick(2)
        h.expect("head_chef.sure", "head_chef.no_space")
        h.ends()
        assertEquals(0, coinsTaken)
        assertEquals(emptyList(), given)
    }

    @Test
    fun `another 99 makes the cape trimmed`() {
        val p = h.player
        p.skills.setLevel(Skill.COOKING, 99)
        p.skills.setLevel(Skill.FISHING, 99)
        p.inventory[0] = InvObj(CooksGuild.COINS, 99_000)
        toPrice()
        h.pick(2)
        h.expect("head_chef.sure", "head_chef.sold")
        h.ends()
        assertEquals(listOf(CooksGuild.COOKING_CAPE_TRIMMED, CooksGuild.COOKING_HOOD), given)
    }

    @Test
    fun `a cape in the bank counts as owned`() {
        h.player.bank[0] = InvObj(CooksGuild.COOKING_CAPE_TRIMMED, 1)
        assertTrue(CooksGuild.ownsCape(h.player))
    }

    @Test
    fun `an owner of the cape can have another hood for free`() {
        val p = h.player
        p.skills.setLevel(Skill.COOKING, 99)
        p.inventory[0] = InvObj(CooksGuild.COOKING_CAPE, 1)
        talk()
        h.expect("head_chef.master")
        h.menu("head_chef.no_thanks", "head_chef.yes_please")
        h.pick(2)
        h.menu("head_chef.skillcape", "head_chef.hood")
        h.pick(2)
        h.expect("head_chef.hood_ask", "head_chef.hood_given", "head_chef.hood_handed")
        h.ends()
        assertEquals(listOf(CooksGuild.COOKING_HOOD), given)
        assertEquals(0, coinsTaken)
    }
}
