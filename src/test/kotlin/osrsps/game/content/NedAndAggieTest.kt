package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.Quests
import osrsps.game.PlayerVars
import osrsps.game.VarTypes
import osrsps.game.VarbitType

/** Draynor step 4: Ned's and Aggie's talks, with no dialogue file, so every line shows its key. */
class NedAndAggieTest {
    private val h = DialogueHarness()

    private val give: (Player, Int) -> Unit = { player, obj -> player.inventory.add(type(obj), 1) }

    private fun type(id: Int) = ObjType(id, "Item $id", stackable = id == Ned.COINS)

    private fun hold(id: Int, count: Int) {
        if (id == Ned.COINS) h.player.inventory.add(type(id), count) else repeat(count) { h.player.inventory.add(type(id), 1) }
    }

    private fun count(id: Int) = h.player.inventory.count(id)

    private val ropeTalk = arrayOf("ned.rope", "ned.price", "ned.rope_2", "ned.rope_3", "ned.rope_4", "ned.rope_5")

    private val yakTalk = arrayOf(
        "ned.yak_smell", "ned.yak_what", "ned.yak_isles", "ned.yak_well", "ned.yak_trader", "ned.yak_so", "ned.yak_accuse",
        "ned.yak_confess", "ned.yak_brand", "ned.yak_accept",
    )

    private fun startRope() {
        h.talk { with(Ned) { talk(give) } }
        h.expect("ned.greeting")
        h.menu("ned.crandor", "ned.rope", "ned.decline")
        h.pick(2)
        h.expect(*ropeTalk)
    }

    @Test
    fun `Ned sells a rope for 15 coins, and the yak hair talk gives a second one free`() {
        hold(Ned.COINS, 20)
        startRope()
        h.menu("ned.sell", "ned.expensive", "ned.get_wool")
        h.pick(1)
        h.expect("ned.sell", "ned.here", *yakTalk, "ned.yak_sample", "ned.yak_secret", "ned.yak_rope", "ned.yak_jofridr", "ned.yak_bye")
        h.ends()
        assertEquals(5, count(Ned.COINS))
        assertEquals(2, count(Ned.ROPE_ITEM))
        assertEquals(listOf(Ned.ROPE_ITEM), h.boxObjs)
    }

    @Test
    fun `under 15 coins Ned sells nothing but still tells the yak hair story`() {
        hold(Ned.COINS, 14)
        startRope()
        h.menu("ned.sell", "ned.expensive", "ned.get_wool")
        h.pick(1)
        h.expect("ned.sell", "ned.cant_afford", *yakTalk, "ned.yak_sample", "ned.yak_secret", "ned.yak_rope", "ned.yak_jofridr", "ned.yak_bye")
        h.ends()
        assertEquals(14, count(Ned.COINS))
        assertEquals(1, count(Ned.ROPE_ITEM))
    }

    @Test
    fun `Ned makes a rope from 4 balls of wool, and asks for 4 when there are fewer`() {
        hold(Ned.BALL_OF_WOOL, 3)
        startRope()
        h.menu("ned.sell", "ned.expensive", "ned.get_wool")
        h.pick(3)
        h.expect("ned.get_wool", "ned.four_balls")
        h.ends()

        hold(Ned.BALL_OF_WOOL, 2)
        startRope()
        h.menu("ned.sell", "ned.expensive", "ned.wool")
        h.pick(3)
        h.expect("ned.wool", "ned.wool_thanks")
        h.ends()
        assertEquals(1, count(Ned.BALL_OF_WOOL))
        assertEquals(1, count(Ned.ROPE_ITEM))
    }

    @Test
    fun `Ned's Crandor talk, his price and goodbye`() {
        h.talk { with(Ned) { talk(give) } }
        h.expect("ned.greeting")
        h.menu("ned.crandor", "ned.rope", "ned.decline")
        h.pick(1)
        h.expect("ned.crandor", "ned.crandor_how", "ned.crandor_rope")
        h.menu("ned.again", "ned.rope", "ned.decline")
        h.pick(1)
        h.expect("ned.again_say", "ned.again_docks")
        h.ends()

        startRope()
        h.menu("ned.sell", "ned.expensive", "ned.get_wool")
        h.pick(2)
        h.expect("ned.expensive", "ned.rum")
        h.ends()

        h.talk { with(Ned) { talk(give) } }
        h.expect("ned.greeting")
        h.menu("ned.crandor", "ned.rope", "ned.decline")
        h.pick(3)
        h.expect("ned.decline", "ned.come_back")
        h.ends()
    }

    private fun startDyes(colour: Int, ask: String, cost: String) {
        h.talk { with(Aggie) { talk(give) } }
        h.expect("aggie.help")
        h.menu("aggie.make", "aggie.frogs", "aggie.witch", "aggie.dyes")
        h.pick(4)
        h.expect("aggie.dyes", "aggie.which")
        h.menu("aggie.red", "aggie.yellow", "aggie.blue", "aggie.happy")
        h.pick(colour)
        h.expect(ask, cost)
    }

    @Test
    fun `Aggie makes each dye for its ingredients and 5 coins`() {
        hold(Aggie.COINS, 15)
        for (dye in Aggie.Dye.entries) hold(dye.ingredient, dye.needed)
        for ((i, dye) in Aggie.Dye.entries.withIndex()) {
            val name = dye.name.lowercase()
            startDyes(i + 1, "aggie.$name", "aggie.${name}_cost")
            h.menu("aggie.make_$name", "aggie.not_yet", "aggie.price", dye.where.key, "aggie.other")
            h.pick(1)
            h.expect("aggie.make_$name", dye.got.key)
            h.ends()
            assertEquals(0, count(dye.ingredient))
            assertEquals(1, count(dye.dye))
        }
        assertEquals(0, count(Aggie.COINS))
        assertEquals(Aggie.Dye.entries.map { it.dye }, h.boxObjs)
    }

    @Test
    fun `Aggie wants the ingredients first, then the coins`() {
        startDyes(2, "aggie.yellow", "aggie.yellow_cost")
        h.menu("aggie.make_yellow", "aggie.not_yet", "aggie.price", "aggie.where_onions", "aggie.other")
        h.pick(1)
        h.expect("aggie.make_yellow", "aggie.no_onions")
        h.ends()

        hold(Aggie.Dye.YELLOW.ingredient, 2)
        hold(Aggie.COINS, 4)
        startDyes(2, "aggie.yellow", "aggie.yellow_cost")
        h.menu("aggie.make_yellow", "aggie.not_yet", "aggie.price", "aggie.where_onions", "aggie.other")
        h.pick(1)
        h.expect("aggie.make_yellow", "aggie.no_coins")
        h.ends()
        assertEquals(2, count(Aggie.Dye.YELLOW.ingredient))
        assertEquals(4, count(Aggie.COINS))
    }

    @Test
    fun `Aggie says where woad grows with no question from the player, then names her colours`() {
        startDyes(3, "aggie.blue", "aggie.blue_cost")
        h.menu("aggie.make_blue", "aggie.not_yet", "aggie.price", "aggie.where_woad", "aggie.other")
        h.pick(4)
        h.expect("aggie.woad_answer")
        h.menu("aggie.other", "aggie.thanks")
        h.pick(1)
        h.expect("aggie.other", "aggie.other_answer")
        h.menu("aggie.red", "aggie.yellow", "aggie.blue", "aggie.happy")
        h.pick(4)
        h.expect("aggie.happy", "aggie.happy_answer")
        h.ends()
    }

    @Test
    fun `insulting Aggie costs 5 coins, 20 coins or a pot of flour, in that order`() {
        fun insult(vararg keys: String) {
            h.talk { with(Aggie) { talk(give) } }
            h.expect("aggie.help")
            h.menu("aggie.make", "aggie.frogs", "aggie.witch", "aggie.dyes")
            h.pick(3)
            h.expect("aggie.witch", *keys)
            h.ends()
        }
        hold(Aggie.POT_OF_FLOUR, 1)
        hold(Aggie.COINS, 101)
        insult("aggie.oh", "aggie.twenty_coins", "aggie.respect")
        assertEquals(81, count(Aggie.COINS))
        insult("aggie.oh", "aggie.five_coins", "aggie.respect")
        assertEquals(76, count(Aggie.COINS))
        Ned.take(h.player, Aggie.COINS, 60)
        insult("aggie.oh", "aggie.flour", "aggie.flour_thanks")
        assertEquals(0, count(Aggie.POT_OF_FLOUR))
        insult("aggie.nothing")
        assertEquals(16, count(Aggie.COINS))
    }

    @Test
    fun `every account has met Aggie, so she shows Dyes`() {
        val vars = PlayerVars(VarTypes(6_000, mapOf(13597 to VarbitType(4000, 0, 0))))
        Quests.NONE.completeAll(vars)
        assertEquals(1, vars.getVarbit(13597))
        // A cache without the varbit is left alone.
        Quests.NONE.completeAll(PlayerVars(VarTypes.NONE))
    }
}
