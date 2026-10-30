package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.content.AlKharidTraders.karim
import osrsps.game.content.AlKharidTraders.silkTrader
import osrsps.game.shop.ShopScreen

/** Al Kharid step 4: Karim's and the Silk trader's talk and what they sell, and the kebab's outcomes. */
class AlKharidTradersTest {
    private val h = DialogueHarness()
    private val given = mutableListOf<Int>()
    private val coins = ObjType(ShopScreen.COINS, "Coins", stackable = true)

    private fun coinsHeld() = h.player.inventory.count(ShopScreen.COINS).toInt()

    @Test
    fun `Karim sells a kebab for one coin, or sends the player off with none`() {
        val menu = arrayOf("karim.miss", "karim.yes_please", "karim.ali")
        var bought = true
        h.talk { bought = karim { given += it } }
        h.expect("karim.offer")
        h.menu(*menu)
        h.pick(2)
        h.expect("karim.yes_please", "karim.oops", "karim.come_back")
        h.ends()
        assertFalse(bought)

        h.player.inventory.add(coins, 5)
        h.talk { bought = karim { given += it } }
        h.expect("karim.offer")
        h.menu(*menu)
        h.pick(3)
        h.expect(
            "karim.ali", "karim.ali_1", "karim.ali_2", "karim.ali_3", "karim.rumours", "karim.apparently", "karim.himself",
            "karim.really", "karim.kebab",
        )
        h.menu(*menu)
        h.pick(2)
        h.expect("karim.yes_please")
        h.next()
        assertTrue(bought)
        assertEquals(listOf(AlKharidTraders.KEBAB), given)
        assertEquals(4, coinsHeld())

        h.talk { bought = karim { given += it } }
        h.expect("karim.offer")
        h.menu(*menu)
        h.pick(1)
        h.expect("karim.miss")
        h.ends()
        assertFalse(bought)
    }

    @Test
    fun `the Silk trader sells silk for 3 gp, or 2 after haggling, and not without the coins`() {
        h.player.inventory.add(coins, 4)
        var paid = -1
        h.talk { paid = silkTrader { given += it } }
        h.expect("silk_trader.offer")
        h.menu("silk_trader.how_much", "silk_trader.not_for_me")
        h.pick(1)
        h.expect("silk_trader.how_much", "silk_trader.three_gp")
        h.menu("silk_trader.too_much", "silk_trader.sounds_good")
        h.pick(2)
        h.expect("silk_trader.sounds_good", "silk_trader.bought price=3")
        h.ends()
        assertEquals(3, paid)
        assertEquals(1, coinsHeld())
        assertEquals(listOf(AlKharidTraders.SILK), given)

        // One coin left: haggled down to 2 gp, still too dear.
        h.talk { paid = silkTrader { given += it } }
        h.expect("silk_trader.offer")
        h.menu("silk_trader.how_much", "silk_trader.not_for_me")
        h.pick(1)
        h.expect("silk_trader.how_much", "silk_trader.three_gp")
        h.menu("silk_trader.too_much", "silk_trader.sounds_good")
        h.pick(1)
        h.expect("silk_trader.too_much", "silk_trader.two_gp", "silk_trader.varrock")
        h.menu("silk_trader.two_gp_good", "silk_trader.dont_want")
        h.pick(1)
        h.expect("silk_trader.two_gp_good", "silk_trader.no_money", "silk_trader.come_back")
        h.ends()
        assertEquals(0, paid)
        assertEquals(1, coinsHeld())

        h.talk { paid = silkTrader { given += it } }
        h.expect("silk_trader.offer")
        h.menu("silk_trader.how_much", "silk_trader.not_for_me")
        h.pick(2)
        h.expect("silk_trader.not_for_me")
        h.ends()
        assertEquals(listOf(AlKharidTraders.SILK), given)
    }

    /** A level 50 Hitpoints player at 10 hitpoints, every other skill at 40. */
    private fun eater() = Player(2, "Eater", 3222, 3219, 0).apply {
        for (skill in Skill.entries) skills.setLevel(skill, if (skill == Skill.HITPOINTS) 50 else 40)
        hitpoints = 10
    }

    private fun eat(player: Player, roll: Int, skill: Skill = Skill.FISHING): List<String> {
        val said = mutableListOf<String>()
        Kebab.outcome(player, roll, { skill }) { line, _ -> said += line.key }
        return said
    }

    @Test
    fun `a kebab heals or harms by its roll out of 32, as the Wiki's table has it`() {
        // 1/32: 7 + 24% of 50 = 19, and melee +2.
        eater().let { p ->
            assertEquals(listOf("kebab.amazing"), eat(p, 0))
            assertEquals(29, p.hitpoints)
            for (skill in Kebab.MELEE) assertEquals(42, p.skills.currentLevel(skill))
        }
        // 8/32: 6 + 14% of 50 = 13.
        for (roll in 1..8) eater().let { p ->
            assertEquals(listOf("kebab.good"), eat(p, roll))
            assertEquals(23, p.hitpoints)
        }
        // 20/32: 3 + 7% of 50 = 6.
        for (roll in 9..28) eater().let { p ->
            assertEquals(listOf("food.heals"), eat(p, roll))
            assertEquals(16, p.hitpoints)
        }
        eater().let { p ->
            assertEquals(listOf("kebab.no_effect"), eat(p, 29))
            assertEquals(10, p.hitpoints)
        }
        eater().let { p ->
            assertEquals(listOf("kebab.dodgy", "kebab.damaged"), eat(p, 30))
            assertEquals(37, p.skills.currentLevel(Skill.FISHING))
            assertEquals(10, p.hitpoints)
        }
        eater().let { p ->
            assertEquals(listOf("kebab.very_dodgy", "kebab.damaged_some"), eat(p, 31))
            for (skill in Kebab.MELEE) assertEquals(37, p.skills.currentLevel(skill))
            assertEquals(36, p.skills.currentLevel(Skill.FISHING))
        }
    }

    @Test
    fun `no heal message at full hitpoints, and a drain stops at level 2`() {
        eater().let { p ->
            p.hitpoints = 50
            assertEquals(emptyList(), eat(p, 9))
        }
        eater().let { p ->
            p.skills.setLevel(Skill.FISHING, 2)
            assertEquals(listOf("kebab.dodgy"), eat(p, 30))
            assertEquals(2, p.skills.currentLevel(Skill.FISHING))
        }
        assertFalse(Skill.HITPOINTS in Kebab.DRAINABLE)
    }

    @Test
    fun `Karim and the Silk trader have examines`() {
        assertEquals("examine.npc_2877", Examines.npc(AlKharidTraders.KARIM)?.key)
        assertEquals("examine.npc_2873", Examines.npc(AlKharidTraders.SILK_TRADER)?.key)
    }
}
