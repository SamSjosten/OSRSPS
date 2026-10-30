package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.GameFrame
import osrsps.game.Player
import osrsps.game.Skill

/** Rest of Lumbridge step 10: the church's talk and its altar, with no dialogue file, so every line shows its key. */
class ChurchTest {
    private val h = DialogueHarness()

    /** The side tabs shown, in order. */
    private val tabs = mutableListOf<Int>()
    private val sideTab: (Player, Int) -> Unit = { _, tab -> tabs += tab }

    @Test
    fun `Father Aereck thanks the player for the ghost and has no more quests`() {
        h.talk { with(Church) { aereck() } }
        h.expect("father_aereck.thanks", "father_aereck.new_quest", "father_aereck.one_quest")
        h.ends()
    }

    @Test
    fun `the Prayer tutor answers both questions, each offering the other, and opens the prayer tab`() {
        h.talk { with(Church) { prayerTutor(sideTab) } }
        h.expect("prayer_tutor.good_day", "prayer_tutor.greeting name=Zezima")
        h.menu("prayer_tutor.train", "prayer_tutor.useful", "prayer_tutor.no_thanks")
        h.pick(1)
        h.expect(
            "prayer_tutor.train", "prayer_tutor.bury_or_offer", "prayer_tutor.altars", "prayer_tutor.bones",
            "prayer_tutor.anything_else",
        )
        h.menu("prayer_tutor.useful", "prayer_tutor.no_thanks")
        h.pick(1)
        h.expect(
            "prayer_tutor.useful", "prayer_tutor.kindly", "prayer_tutor.what_benefits", "prayer_tutor.benefits",
            "prayer_tutor.prayer_book", "prayer_tutor.wow", "prayer_tutor.careful", "prayer_tutor.anything_else",
        )
        assertEquals(listOf(GameFrame.SIDE_TAB_PRAYER), tabs)
        h.menu("prayer_tutor.train", "prayer_tutor.no_thanks")
        h.pick(2)
        h.expect("prayer_tutor.no_thanks", "prayer_tutor.farewell")
        h.ends()
    }

    @Test
    fun `at 99 Prayer the tutor says the player should teach her, then gives her advice anyway`() {
        h.player.skills.setLevel(Skill.PRAYER, 99)
        h.talk { with(Church) { prayerTutor(sideTab) } }
        h.expect("prayer_tutor.good_day", "prayer_tutor.greeting name=Zezima")
        h.menu("prayer_tutor.train", "prayer_tutor.useful", "prayer_tutor.no_thanks")
        h.pick(1)
        h.expect(
            "prayer_tutor.train", "prayer_tutor.teach_me", "prayer_tutor.advice", "prayer_tutor.bury_or_offer",
            "prayer_tutor.altars", "prayer_tutor.bones", "prayer_tutor.anything_else",
        )
        h.menu("prayer_tutor.useful", "prayer_tutor.no_thanks")
        h.pick(2)
        h.expect("prayer_tutor.no_thanks", "prayer_tutor.farewell")
        h.ends()
    }

    @Test
    fun `Nigel's Deadman questions go back to their page, More and Previous turn it, and the store is shut`() {
        val page1 = arrayOf("nigel.what_is", "nigel.how_play", "nigel.die", "nigel.kill", "nigel.more")
        val page2 = arrayOf("nigel.protect_items", "nigel.protect_stats", "nigel.safe_zones", "nigel.previous")
        h.talk { with(Church) { nigel() } }
        h.expect("nigel.who", "nigel.intro")
        h.menu("nigel.question", "nigel.rewards", "nigel.goodbye")
        h.pick(1)
        h.expect("nigel.question", "nigel.what_like")
        h.menu(*page1)
        h.pick(3)
        h.expect("nigel.die", "nigel.die_1", "nigel.die_2", "nigel.die_3")
        h.menu(*page1)
        h.pick(5)
        h.menu(*page2)
        h.pick(2)
        h.expect("nigel.protect_stats", "nigel.protect_stats_1", "nigel.protect_stats_2", "nigel.protect_stats_3")
        h.menu(*page2)
        h.pick(4)
        h.menu(*page1)
        h.pick(1)
        h.expect(
            "nigel.what_is", "nigel.what_is_1", "nigel.lucrative", "nigel.what_is_2", "nigel.brutal", "nigel.what_is_3",
            "nigel.what_is_4",
        )
        // The questions have no goodbye: only walking off or closing the chatbox ends them.
        h.menu(*page1)

        val rewards = DialogueHarness()
        rewards.talk { with(Church) { nigel() } }
        rewards.expect("nigel.who", "nigel.intro")
        rewards.menu("nigel.question", "nigel.rewards", "nigel.goodbye")
        rewards.pick(2)
        rewards.expect("nigel.store_closed")
        rewards.ends()
        rewards.talk { with(Church) { nigel() } }
        rewards.expect("nigel.who", "nigel.intro")
        rewards.menu("nigel.question", "nigel.rewards", "nigel.goodbye")
        rewards.pick(3)
        rewards.expect("nigel.goodbye")
        rewards.ends()
        rewards.talk { with(Church) { nigelTrade() } }
        rewards.expect("nigel.store_closed")
        rewards.ends()
    }

    private fun countCheck(sent: MutableList<Player>) {
        h.talk { with(Church) { countCheck({ it in sent }, { sent += it }) } }
        h.expect("count_check.intro")
    }

    private val top = arrayOf("count_check.check", "count_check.advice", "count_check.learn", "count_check.who", "count_check.bye")

    @Test
    fun `Count Check's check fails, as there is no Jagex Account or Bank PIN`() {
        countCheck(mutableListOf())
        h.menu(*top)
        h.pick(1)
        h.expect("count_check.check", "count_check.fail")
        h.ends()
    }

    @Test
    fun `Count Check counts his advice, and three can be questioned`() {
        countCheck(mutableListOf())
        h.menu(*top)
        h.pick(2)
        h.expect(
            "count_check.advice", "count_check.count_steps", "count_check.one", "count_check.one_password", "count_check.two",
            "count_check.two_email", "count_check.three", "count_check.three_website",
        )
        h.menu("count_check.carry_on", "count_check.what_three")
        h.pick(2)
        h.expect("count_check.what_three", "count_check.flow", "count_check.silence", "count_check.four", "count_check.four_pin", "count_check.fun")
        h.menu(*top)
        h.pick(5)
        h.expect("count_check.bye")
        h.ends()
    }

    @Test
    fun `Count Check sends the player to the Stronghold only once, and after Who the Salve is asked about`() {
        val sent = mutableListOf<Player>()
        countCheck(sent)
        h.menu(*top)
        h.pick(3)
        h.expect("count_check.learn", "count_check.stronghold")
        h.menu("count_check.yes", "count_check.no")
        h.pick(2)
        h.expect("count_check.may_check")
        h.menu(*top)
        h.pick(3)
        h.expect("count_check.learn", "count_check.stronghold")
        h.menu("count_check.yes", "count_check.no")
        h.pick(1)
        assertEquals(listOf(h.player), sent)
        assertEquals(-1, h.player.chatModal)

        countCheck(sent)
        h.menu(*top)
        h.pick(3)
        h.expect("count_check.learn", "count_check.stronghold_visited")
        h.menu(*top)
        h.pick(4)
        h.expect(
            "count_check.who", "count_check.vampyre", "count_check.perils", "count_check.be_warned", "count_check.left_ways",
            "count_check.blessed",
        )
        h.menu("count_check.salve", "count_check.check", "count_check.advice", "count_check.learn", "count_check.bye")
        h.pick(1)
        h.expect("count_check.salve", "count_check.of_course", "count_check.not_role_play", "count_check.right", "count_check.may_check")
        h.menu(*top)
        h.pick(5)
        h.expect("count_check.bye")
        h.ends()
    }

    @Test
    fun `praying at the altar fills the points to the level, keeps a boost and resets the drain counter`() {
        val player = h.player
        player.skills.setLevel(Skill.PRAYER, 43)
        player.skills.setCurrentLevel(Skill.PRAYER, 2)
        player.prayerDrainCounter = 50
        Church.pray(player)
        assertEquals(43, player.skills.currentLevel(Skill.PRAYER))
        assertEquals(0, player.prayerDrainCounter)
        player.skills.setCurrentLevel(Skill.PRAYER, 45)
        Church.pray(player)
        assertEquals(45, player.skills.currentLevel(Skill.PRAYER))
    }
}
