package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.ObjType
import osrsps.game.Skill
import osrsps.game.content.EdgevillePeople.doris
import osrsps.game.content.EdgevillePeople.evilDave
import osrsps.game.content.EdgevillePeople.hari
import osrsps.game.content.EdgevillePeople.lucien

/** Edgeville step 3: Doris, Evil Dave, Hari and Lucien, with no dialogue file, so every line shows its key. */
class EdgevillePeopleTest {
    private val h = DialogueHarness()

    @Test
    fun `Doris answers each of her four options and ends`() {
        val menu = arrayOf("doris.pretty_good", "doris.not_good", "doris.wilderness", "doris.dave")
        val answers = listOf(
            listOf("doris.pretty_good", "doris.pretty_good_reply"),
            listOf("doris.not_good", "doris.not_good_reply"),
            listOf("doris.wilderness") + (1..5).map { "doris.wilderness_$it" },
            listOf("doris.dave") + (1..4).map { "doris.dave_$it" },
        )
        for ((i, pages) in answers.withIndex()) {
            h.talk { doris() }
            h.expect("doris.greeting")
            h.menu(*menu)
            h.pick(i + 1)
            h.expect(*pages.toTypedArray())
            h.ends()
        }
    }

    @Test
    fun `Evil Dave's basement talk loops on the summoning, then says a random farewell`() {
        val basementMenu = arrayOf("evil_dave.summoning", "evil_dave.give_up", "evil_dave.see_you")
        h.talk { evilDave { 2 } }
        h.expect("evil_dave.greeting")
        h.menu("evil_dave.nice_basement", "evil_dave.give_up", "evil_dave.time_travel")
        h.pick(1)
        h.expect("evil_dave.nice_basement", "evil_dave.is_it_nice", "evil_dave.evil", "evil_dave.plans")
        h.menu(*basementMenu)
        h.pick(1)
        h.expect("evil_dave.summoning", *(1..4).map { "evil_dave.summoning_$it" }.toTypedArray())
        h.menu(*basementMenu)
        h.pick(2)
        h.expect("evil_dave.give_up", *(1..6).map { "evil_dave.give_up_$it" }.toTypedArray())
        h.menu("evil_dave.nice_basement", "evil_dave.see_you")
        h.pick(2)
        // The farewell is the one the pick gave: the third of five.
        h.expect("evil_dave.see_you", "evil_dave.farewell_3")
        h.ends()
    }

    @Test
    fun `Evil Dave's time travel menu loops until the magic or never mind`() {
        val menu = arrayOf("evil_dave.frozen", "evil_dave.saved", "evil_dave.food", "evil_dave.magic", "evil_dave.never_mind")
        h.talk { evilDave { 0 } }
        h.expect("evil_dave.greeting")
        h.menu("evil_dave.nice_basement", "evil_dave.give_up", "evil_dave.time_travel")
        h.pick(3)
        h.expect("evil_dave.time_travel")
        h.menu(*menu)
        h.pick(1)
        h.expect("evil_dave.frozen", *(1..9).map { "evil_dave.frozen_$it" }.toTypedArray())
        h.menu(*menu)
        h.pick(2)
        h.expect("evil_dave.saved", *(1..5).map { "evil_dave.saved_$it" }.toTypedArray())
        h.menu(*menu)
        h.pick(3)
        h.expect("evil_dave.food", *(1..5).map { "evil_dave.food_$it" }.toTypedArray())
        h.menu("evil_dave.already", "evil_dave.paradox", "evil_dave.dont_know")
        h.pick(3)
        h.expect("evil_dave.dont_know", "evil_dave.dont_know_reply")
        h.menu(*menu)
        h.pick(4)
        h.expect("evil_dave.magic", "evil_dave.magic_reply")
        h.ends()

        h.talk { evilDave { 0 } }
        h.expect("evil_dave.greeting")
        h.menu("evil_dave.nice_basement", "evil_dave.give_up", "evil_dave.time_travel")
        h.pick(3)
        h.expect("evil_dave.time_travel")
        h.menu(*menu)
        h.pick(5)
        h.expect("evil_dave.never_mind")
        h.ends()
    }

    @Test
    fun `Hari explains canoes by the player's Woodcutting level`() {
        val bands = listOf(
            1 to listOf("hari.no_skill", "hari.level_12", "hari.quicker"),
            12 to listOf("hari.chop", "hari.shape", "hari.log", "hari.good", "hari.one_stop"),
            27 to listOf("hari.chop", "hari.shape", "hari.dugout", "hari.reach_wilderness", "hari.not_survive", "hari.wont_try"),
            42 to listOf("hari.chop", "hari.shape", "hari.stable", "hari.anywhere", "hari.only_waka", "hari.what_waka", "hari.come_back"),
            57 to listOf("hari.chop", "hari.shape", "hari.can_waka", "hari.what_waka", "hari.waka", "hari.any_destination", "hari.north", "hari.lost"),
        )
        for ((level, pages) in bands) {
            h.player.skills.setLevel(Skill.WOODCUTTING, level)
            h.talk { hari() }
            h.expect("hari.hello", "hari.hello_reply")
            h.menu("hari.who", "hari.teach")
            h.pick(2)
            h.expect("hari.teach_me", *pages.toTypedArray())
            h.ends()
        }
    }

    @Test
    fun `Hari says who he is, then explains canoes or is turned down`() {
        h.talk { hari() }
        h.expect("hari.hello", "hari.hello_reply")
        h.menu("hari.who", "hari.teach")
        h.pick(1)
        h.expect("hari.who", "hari.name", "hari.doing", "hari.adventure", "hari.river", "hari.comes_out", "hari.pond", "hari.offer")
        h.menu("hari.yes", "hari.no")
        h.pick(2)
        h.expect("hari.no_thanks")
        h.ends()
    }

    @Test
    fun `Lucien gives a new pendant only to a player who owns none`() {
        val given = mutableListOf<Int>()
        h.talk { lucien { _, obj -> given += obj } }
        h.expect("lucien.what", "lucien.lost_pendant", "lucien.new_pendant", "lucien.nothing", "lucien.leave")
        h.ends()
        assertEquals(listOf(EdgevillePeople.PENDANT_OF_LUCIEN), given)

        h.player.bank.add(ObjType(EdgevillePeople.PENDANT_OF_LUCIEN, "Pendant of Lucien"), 1)
        h.talk { lucien { _, obj -> given += obj } }
        h.expect("lucien.what", "lucien.nothing", "lucien.leave")
        h.ends()
        assertEquals(1, given.size)
    }
}
