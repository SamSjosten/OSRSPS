package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.ObjType
import osrsps.game.Skill
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.content.EdgevilleMonastery.abbot
import osrsps.game.content.EdgevilleMonastery.althric
import osrsps.game.content.EdgevilleMonastery.jered
import osrsps.game.content.EdgevilleMonastery.joinMenu
import osrsps.game.content.EdgevilleMonastery.monk

/** Edgeville step 4: the monastery's people, with no dialogue file, so every line shows its key. */
class EdgevilleMonasteryTest {
    // monastery_enter is varp 190's bit 0 in the 241 cache.
    private val h = DialogueHarness(varTypes = VarTypes(5_000, mapOf(EdgevilleMonastery.VARBIT_JOINED to VarbitType(190, 0, 0))))
    private val join: (osrsps.game.Player) -> Unit = { it.vars.setVarbit(EdgevilleMonastery.VARBIT_JOINED, 1) }

    @Test
    fun `a monk heals 2 and a fifth of the Hitpoints level, and answers his other two options`() {
        h.player.skills.setLevel(Skill.HITPOINTS, 52)
        h.player.hitpoints = 10
        h.talk { monk() }
        h.expect("edgeville_monk.greeting")
        h.menu("edgeville_monk.heal", "edgeville_monk.out_of_way", "edgeville_monk.get_in")
        h.pick(1)
        h.expect("edgeville_monk.heal", "edgeville_monk.heal_yes", "edgeville_monk.healed")
        h.ends()
        assertEquals(10 + 2 + 52 / 5, h.player.hitpoints)
        // Never above the level.
        h.player.hitpoints = 50
        h.talk { monk() }
        h.expect("edgeville_monk.greeting")
        h.menu("edgeville_monk.heal", "edgeville_monk.out_of_way", "edgeville_monk.get_in")
        h.pick(1)
        h.expect("edgeville_monk.heal", "edgeville_monk.heal_yes", "edgeville_monk.healed")
        h.ends()
        assertEquals(52, h.player.hitpoints)
        for ((option, pages) in listOf(2 to listOf("edgeville_monk.out_of_way", "edgeville_monk.out_of_way_reply"), 3 to listOf("edgeville_monk.get_in", "edgeville_monk.get_in_reply"))) {
            h.talk { monk() }
            h.expect("edgeville_monk.greeting")
            h.menu("edgeville_monk.heal", "edgeville_monk.out_of_way", "edgeville_monk.get_in")
            h.pick(option)
            h.expect(*pages.toTypedArray())
            h.ends()
        }
    }

    @Test
    fun `the Abbot lets a player join at 31 Prayer and not before, and drops the option once joined`() {
        h.player.skills.setLevel(Skill.PRAYER, 30)
        h.talk { abbot(join) {} }
        h.expect("abbot_langley.greeting")
        h.menu("abbot_langley.heal", "abbot_langley.where", "abbot_langley.access", "abbot_langley.shields")
        h.pick(3)
        h.expect("abbot_langley.access", "abbot_langley.access_reply")
        h.menu("abbot_langley.join", "abbot_langley.sorry")
        h.pick(1)
        h.expect("abbot_langley.join", "abbot_langley.too_low", "abbot_langley.requirement")
        h.ends()
        assertEquals(false, EdgevilleMonastery.joined(h.player))

        h.talk { joinMenu(join) }
        h.pick(2)
        h.expect("abbot_langley.sorry")
        h.ends()

        h.player.skills.setLevel(Skill.PRAYER, 31)
        h.talk { joinMenu(join) }
        h.pick(1)
        h.expect("abbot_langley.join", "abbot_langley.accepted")
        h.ends()
        assertTrue(EdgevilleMonastery.joined(h.player))

        h.talk { abbot(join) {} }
        h.expect("abbot_langley.greeting")
        h.menu("abbot_langley.heal", "abbot_langley.where", "abbot_langley.shields")
        h.pick(2)
        h.expect("abbot_langley.where", "abbot_langley.where_reply")
        h.ends()
    }

    @Test
    fun `the Abbot breaks the spirit shields only on a yes`() {
        var broken = 0
        for ((pick, pages) in listOf(1 to listOf("abbot_langley.broken"), 2 to listOf("abbot_langley.no"))) {
            h.talk { abbot(join) { broken++ } }
            h.expect("abbot_langley.greeting")
            h.menu("abbot_langley.heal", "abbot_langley.where", "abbot_langley.access", "abbot_langley.shields")
            h.pick(4)
            h.expect("abbot_langley.shields", "abbot_langley.shields_reply", "abbot_langley.sigils", "abbot_langley.shields_warning", "abbot_langley.shields_warning_2")
            h.menu("abbot_langley.yes", "abbot_langley.no")
            h.pick(pick)
            h.expect(*pages.toTypedArray())
            h.ends()
        }
        assertEquals(1, broken)
    }

    @Test
    fun `Brother Jered blesses an unblessed symbol, explains, and sells the Prayer cape at 99`() {
        val swapped = mutableListOf<Pair<Int, Int>>()
        val given = mutableListOf<Int>()
        val taken = mutableListOf<Int>()
        fun talk() = h.talk { jered({ _, from, to -> swapped += from to to }, { _, obj -> given += obj }, { _, n -> taken += n }) }

        talk()
        h.menu("brother_jered.help", "brother_jered.praise")
        h.pick(2)
        h.expect("brother_jered.praise", "brother_jered.praise_reply")
        h.ends()

        // No symbol: the two topics; the cape's talk offers the topics again.
        talk()
        h.pick(1)
        h.expect("brother_jered.help", "brother_jered.topics")
        h.menu("brother_jered.symbols", "brother_jered.cape")
        h.pick(2)
        h.expect("brother_jered.cape", "brother_jered.cape_1", "brother_jered.cape_2")
        h.menu("brother_jered.help", "brother_jered.nothing_else")
        h.pick(1)
        h.expect("brother_jered.help", "brother_jered.topics")
        h.menu("brother_jered.symbols", "brother_jered.cape")
        h.pick(1)
        h.expect("brother_jered.symbols", "brother_jered.symbols_reply")
        h.ends()

        h.player.inventory.add(ObjType(EdgevilleMonastery.UNBLESSED_SYMBOL, "Unblessed symbol"), 1)
        talk()
        h.pick(1)
        h.expect("brother_jered.help", "brother_jered.has_symbol")
        h.menu("brother_jered.bless", "brother_jered.cape", "brother_jered.no_thanks")
        h.pick(1)
        h.expect("brother_jered.bless", "brother_jered.blessed")
        h.ends()
        assertEquals(listOf(EdgevilleMonastery.UNBLESSED_SYMBOL to EdgevilleMonastery.HOLY_SYMBOL), swapped)
        assertEquals(listOf(EdgevilleMonastery.HOLY_SYMBOL), h.boxObjs)

        // 99 Prayer with a symbol: still blessed, and the cape option leads to the sale.
        h.player.skills.setLevel(Skill.PRAYER, 99)
        h.player.inventory.add(ObjType(EdgevilleMonastery.UNBLESSED_SYMBOL, "Unblessed symbol"), 2)
        talk()
        h.pick(1)
        h.expect("brother_jered.help", "brother_jered.has_symbol")
        h.menu("brother_jered.bless", "brother_jered.cape", "brother_jered.no_thanks")
        h.pick(1)
        h.expect("brother_jered.bless", "brother_jered.blessed")
        h.ends()
        assertEquals(2, swapped.size)
        talk()
        h.pick(1)
        h.expect("brother_jered.help", "brother_jered.has_symbol")
        h.menu("brother_jered.bless", "brother_jered.cape", "brother_jered.no_thanks")
        h.pick(2)
        h.expect("brother_jered.master")
        h.menu("brother_jered.too_dear", "brother_jered.want_cape")
        h.pick(1)
        h.expect("brother_jered.too_dear", "brother_jered.too_dear_reply")
        h.ends()
        h.player.inventory.clear()

        // 99 Prayer: without the coins, then with them (untrimmed: no other 99).
        talk()
        h.pick(1)
        h.expect("brother_jered.help", "brother_jered.master")
        h.menu("brother_jered.too_dear", "brother_jered.want_cape")
        h.pick(2)
        h.expect("brother_jered.want_cape", "brother_jered.price")
        h.menu("brother_jered.too_dear", "brother_jered.donate")
        h.pick(2)
        h.expect("brother_jered.donate", "brother_jered.no_money", "brother_jered.no_money_reply")
        h.ends()
        h.player.inventory.add(ObjType(EdgevilleMonastery.COINS, "Coins", stackable = true), 100_000)
        talk()
        h.pick(1)
        h.expect("brother_jered.help", "brother_jered.master")
        h.menu("brother_jered.too_dear", "brother_jered.want_cape")
        h.pick(2)
        h.expect("brother_jered.want_cape", "brother_jered.price")
        h.menu("brother_jered.too_dear", "brother_jered.donate")
        h.pick(2)
        h.expect("brother_jered.donate", "brother_jered.sold")
        h.ends()
        assertEquals(listOf(EdgevilleMonastery.CAPE_PRICE), taken)
        assertEquals(listOf(EdgevilleMonastery.PRAYER_CAPE, EdgevilleMonastery.PRAYER_HOOD), given)
    }

    @Test
    fun `Brother Althric talks about his roses`() {
        h.talk { althric() }
        h.expect("brother_althric.roses", "brother_althric.roses_reply")
        h.ends()
    }
}
