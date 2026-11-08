package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.Skill
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.content.Harlan.claim
import osrsps.game.content.Harlan.talk

/** Captain Harlan, the combat tutor since 242.1 (Lumbridge 242 step 1). Keys show for words, as with no dialogue file. */
class HarlanTest {
    private val types = VarTypes.NONE.with(
        mapOf(Harlan.VARBIT_INTRO to VarbitType(6240, 9, 9)),
        mapOf(CustomVars.HARLAN_CLAIMED to 6_000),
    )
    private val harness = DialogueHarness("Captain Harlan", types)
    private val player = harness.player
    private val given = mutableListOf<Pair<Int, Int>>()
    private var now = 1_000_000L
    private val tutor = Harlan.Tutor(give = { _, obj, count -> given += obj to count }, nowMinutes = { now })
    private val root = arrayOf(
        "captain_harlan.guards", "captain_harlan.advice", "captain_harlan.weapons", "captain_harlan.cape", "captain_harlan.leave",
    )
    private val kinds = arrayOf(
        "captain_harlan.ask_melee", "captain_harlan.ask_ranged", "captain_harlan.ask_magic", "captain_harlan.something_else",
    )

    init {
        // Every account has met him (Mudfoot's Big Adventure is done); the first test clears it.
        player.vars.setVarbit(Harlan.VARBIT_INTRO, 1)
    }

    private fun start(greeting: String = "captain_harlan.greeting") {
        harness.talk { talk(tutor) }
        harness.expect(greeting)
        harness.menu(*root)
    }

    private fun fill(free: Int) {
        val bones = ObjType(526, "Bones")
        player.inventory.add(bones, player.inventory.freeSlots - free)
    }

    @Test
    fun `the first talk greets as new and sets the intro varbit, later ones greet again, and leaving ends it`() {
        player.vars.setVarbit(Harlan.VARBIT_INTRO, 0)
        start("captain_harlan.greeting_first")
        assertEquals(1, player.vars.getVarbit(Harlan.VARBIT_INTRO))
        harness.pick(5)
        harness.expect("captain_harlan.leave", "captain_harlan.leave_reply")
        harness.ends()
        start()
    }

    @Test
    fun `the guards and the combat advice come back to the first menu`() {
        start()
        harness.pick(1)
        harness.expect("captain_harlan.guards", "captain_harlan.guards_1", "captain_harlan.guards_ask", "captain_harlan.guards_2")
        harness.menu(*root)
        harness.pick(2)
        harness.expect(
            "captain_harlan.advice", "captain_harlan.advice_1", "captain_harlan.advice_2", "captain_harlan.triangle",
            "captain_harlan.advice_3", "captain_harlan.advice_4", "captain_harlan.weapon_kinds", "captain_harlan.advice_5",
            "captain_harlan.advice_6", "captain_harlan.advice_7", "captain_harlan.hint_equipment", "captain_harlan.advice_8",
            "captain_harlan.hint_styles", "captain_harlan.hint_xp", "captain_harlan.advice_9", "captain_harlan.advice_10",
            "captain_harlan.advice_11", "captain_harlan.advice_12", "captain_harlan.hint_guides", "captain_harlan.advice_thanks",
        )
        harness.menu(*root)
    }

    @Test
    fun `a shortsword and shield once, then 30 minutes before anything else, counted down in whole minutes`() {
        start()
        harness.pick(3)
        harness.expect("captain_harlan.weapons", "captain_harlan.offer_all")
        harness.menu(*kinds)
        harness.pick(1)
        harness.expect("captain_harlan.ask_melee", "captain_harlan.gives_melee")
        assertEquals(listOf(Harlan.BRONZE_SHORTSWORD to 1, Harlan.WOODEN_SHIELD to 1), given)
        assertEquals(listOf(Harlan.BRONZE_SHORTSWORD, Harlan.WOODEN_SHIELD), harness.boxObjs)
        harness.next()
        harness.expect("captain_harlan.anything_else")
        harness.menu(*kinds)
        // A second kind in the same talk is refused: the handout started the clock.
        harness.pick(2)
        harness.expect("captain_harlan.ask_ranged", "captain_harlan.too_soon_all minutes=30")
        harness.ends()
        assertEquals(30, Harlan.minutesLeft(player, now))

        now += 29
        start()
        harness.pick(3)
        harness.expect("captain_harlan.weapons", "captain_harlan.too_soon_all minutes=1")
        harness.ends()
        assertEquals(1, Harlan.minutesLeft(player, now))

        now += 1
        start()
        harness.pick(3)
        harness.expect("captain_harlan.weapons", "captain_harlan.offer_all")
        harness.menu(*kinds)
        harness.pick(3)
        harness.expect("captain_harlan.ask_magic", "captain_harlan.gives_magic")
        assertEquals(
            listOf(Harlan.STAFF to 1, Harlan.MIND_RUNE to Harlan.AMMO, Harlan.AIR_RUNE to Harlan.AMMO),
            given.drop(2),
        )
    }

    @Test
    fun `owning both a shortsword and shield gets nothing and the clock doesn't start`() {
        player.inventory.add(ObjType(Harlan.BRONZE_SHORTSWORD, "Bronze shortsword"), 1)
        player.equipment[WearPos.SHIELD] = InvObj(Harlan.WOODEN_SHIELD, 1)
        start()
        harness.pick(3)
        harness.expect("captain_harlan.weapons", "captain_harlan.offer_all")
        harness.menu(*kinds)
        harness.pick(1)
        harness.expect("captain_harlan.ask_melee", "captain_harlan.has_melee", "captain_harlan.anything_else")
        harness.menu(*root)
        assertTrue(given.isEmpty())
        assertEquals(0, Harlan.minutesLeft(player, now))
    }

    @Test
    fun `a worn wooden shield means only the shortsword, which needs one slot`() {
        player.equipment[WearPos.SHIELD] = InvObj(Harlan.WOODEN_SHIELD, 1)
        fill(1)
        start()
        harness.pick(3)
        harness.expect("captain_harlan.weapons", "captain_harlan.offer_all")
        harness.menu(*kinds)
        harness.pick(1)
        harness.expect("captain_harlan.ask_melee", "captain_harlan.gives_shortsword", "captain_harlan.anything_else")
        assertEquals(listOf(Harlan.BRONZE_SHORTSWORD to 1), given)
        harness.menu(*kinds)
    }

    @Test
    fun `with one slot the shortsword and shield are refused but the shortbow and arrows are given`() {
        fill(1)
        start()
        harness.pick(3)
        harness.expect("captain_harlan.weapons", "captain_harlan.offer_all")
        harness.menu(*kinds)
        harness.pick(1)
        harness.expect("captain_harlan.ask_melee", "captain_harlan.no_space_melee", "captain_harlan.anything_else")
        harness.menu(*root)
        assertTrue(given.isEmpty())
        harness.pick(3)
        harness.expect("captain_harlan.weapons", "captain_harlan.offer_all")
        harness.menu(*kinds)
        harness.pick(2)
        harness.expect("captain_harlan.ask_ranged", "captain_harlan.gives_ranged", "captain_harlan.anything_else")
        assertEquals(listOf(Harlan.SHORTBOW to 1, Harlan.BRONZE_ARROW to Harlan.AMMO), given)
    }

    @Test
    fun `a carried shortbow gets arrows, and with no room for them nothing`() {
        player.inventory.add(ObjType(Harlan.SHORTBOW, "Shortbow"), 1)
        fill(0)
        start()
        harness.pick(3)
        harness.expect("captain_harlan.weapons", "captain_harlan.offer_all")
        harness.menu(*kinds)
        harness.pick(2)
        harness.expect("captain_harlan.ask_ranged", "captain_harlan.no_space_arrows", "captain_harlan.anything_else")
        harness.menu(*root)
        assertTrue(given.isEmpty())

        // Arrows already carried stack, so they need no slot.
        player.inventory.remove(1, 1)
        player.inventory.add(ObjType(Harlan.BRONZE_ARROW, "Bronze arrow", stackable = true), 5)
        harness.pick(3)
        harness.expect("captain_harlan.weapons", "captain_harlan.offer_all")
        harness.menu(*kinds)
        harness.pick(2)
        harness.expect("captain_harlan.ask_ranged", "captain_harlan.gives_arrows", "captain_harlan.anything_else")
        assertEquals(listOf(Harlan.BRONZE_ARROW to Harlan.AMMO), given)
    }

    @Test
    fun `Claim-weapons goes straight to the handout and ends after it`() {
        harness.talk { claim(tutor) }
        harness.menu(
            "captain_harlan.claim_melee", "captain_harlan.claim_ranged", "captain_harlan.claim_magic", "captain_harlan.cancel",
        )
        harness.pick(2)
        harness.expect("captain_harlan.gives_ranged", "captain_harlan.anything_else")
        harness.ends()
        assertEquals(listOf(Harlan.SHORTBOW to 1, Harlan.BRONZE_ARROW to Harlan.AMMO), given)

        harness.talk { claim(tutor) }
        harness.menu(
            "captain_harlan.claim_melee", "captain_harlan.claim_ranged", "captain_harlan.claim_magic", "captain_harlan.cancel",
        )
        harness.pick(1)
        harness.expect("captain_harlan.too_soon_all minutes=30")
        harness.ends()
        assertEquals(2, given.size)
    }

    @Test
    fun `at 99 Defence he sells the cape for 99,000 coins, trimmed with another 99, the hood free`() {
        player.skills.setLevel(Skill.DEFENCE, 99)
        player.skills.setLevel(Skill.ATTACK, 99)
        player.inventory.add(ObjType(Harlan.COINS, "Coins", stackable = true), 100_000)
        start()
        harness.pick(4)
        harness.expect("captain_harlan.cape", "captain_harlan.cape_what")
        harness.menu("captain_harlan.buy_cape", "captain_harlan.hood")
        harness.pick(1)
        harness.expect("captain_harlan.buy_cape", "captain_harlan.price")
        harness.menu("captain_harlan.accept", "captain_harlan.too_much")
        harness.pick(1)
        harness.expect("captain_harlan.accept", "captain_harlan.sold")
        assertEquals(listOf(Harlan.DEFENCE_CAPE_TRIMMED to 1, Harlan.DEFENCE_HOOD to 1), given)
        assertEquals(1_000, player.inventory.count(Harlan.COINS))
        harness.menu(*root)
    }

    @Test
    fun `under 99 Defence no cape is sold, and a hood needs a Defence cape`() {
        player.inventory.add(ObjType(Harlan.COINS, "Coins", stackable = true), 100_000)
        start()
        harness.pick(4)
        harness.expect("captain_harlan.cape", "captain_harlan.cape_what")
        harness.menu("captain_harlan.buy_cape", "captain_harlan.hood")
        harness.pick(1)
        harness.expect("captain_harlan.buy_cape", "captain_harlan.price")
        harness.menu("captain_harlan.accept", "captain_harlan.too_much")
        harness.pick(1)
        harness.expect("captain_harlan.accept", "captain_harlan.not_master")
        harness.menu(*root)
        harness.pick(4)
        harness.expect("captain_harlan.cape", "captain_harlan.cape_what")
        harness.menu("captain_harlan.buy_cape", "captain_harlan.hood")
        harness.pick(2)
        harness.expect("captain_harlan.hood", "captain_harlan.hood_no_cape")
        harness.menu(*root)
        assertTrue(given.isEmpty())
        assertEquals(100_000, player.inventory.count(Harlan.COINS))
    }
}
