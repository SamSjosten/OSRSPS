package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.Player
import osrsps.game.Skill

/** Rest of Lumbridge step 10: the church's talk and its altar, with no dialogue file, so every line shows its key. */
class ChurchTest {
    private val h = DialogueHarness()

    private val root = arrayOf("father_aereck.more_prayers", "father_aereck.saradomin", "father_aereck.leave_first")
    private val rootAfter = arrayOf("father_aereck.more_prayers", "father_aereck.saradomin", "father_aereck.leave")

    @Test
    fun `Father Aereck welcomes the player, and leaving first is the longer goodbye`() {
        h.talk { with(Church) { aereck() } }
        h.expect("father_aereck.greeting")
        h.menu(*root)
        h.pick(3)
        h.expect("father_aereck.leave_first", "father_aereck.guide_you")
        h.ends()
    }

    @Test
    fun `his prayer tips since 242, with the altar and bones boxes, come back to the menu with the shorter goodbye`() {
        h.talk { with(Church) { aereck() } }
        h.expect("father_aereck.greeting")
        h.menu(*root)
        h.pick(1)
        h.expect(
            "father_aereck.prayers", "father_aereck.prayers_1", "father_aereck.saradomin_thing", "father_aereck.other_gods",
            "father_aereck.curious", "father_aereck.battle", "father_aereck.protection", "father_aereck.hint_prayers",
            "father_aereck.not_free", "father_aereck.hint_points", "father_aereck.faith", "father_aereck.altars",
            "father_aereck.hint_altar", "father_aereck.anything_else", "father_aereck.dedication", "father_aereck.how",
            "father_aereck.bury", "father_aereck.hint_bones", "father_aereck.covers", "father_aereck.hint_guide",
            "father_aereck.thanks",
        )
        assertEquals(listOf(Church.ALTAR_ICON, Church.BONES), h.boxObjs)
        h.menu(*rootAfter)
        h.pick(3)
        h.expect("father_aereck.leave", "father_aereck.guide_you")
        h.ends()
    }

    @Test
    fun `asked about Saradomin, the player can claim another world, as a game or a joke`() {
        h.talk { with(Church) { aereck() } }
        h.expect("father_aereck.greeting")
        h.menu(*root)
        h.pick(2)
        h.expect("father_aereck.saradomin", "father_aereck.saradomin_1", "father_aereck.saradomin_2")
        h.menu("father_aereck.info_thanks", "father_aereck.not_from_world")
        h.pick(2)
        h.expect("father_aereck.not_from_world", "father_aereck.tentacles")
        h.menu("father_aereck.online_game", "father_aereck.joking")
        h.pick(1)
        h.expect("father_aereck.online_game", "father_aereck.pardon", "father_aereck.never_mind")
        h.menu(*rootAfter)
        h.pick(2)
        h.expect("father_aereck.saradomin", "father_aereck.saradomin_1", "father_aereck.saradomin_2")
        h.menu("father_aereck.info_thanks", "father_aereck.not_from_world")
        h.pick(2)
        h.expect("father_aereck.not_from_world", "father_aereck.tentacles")
        h.menu("father_aereck.online_game", "father_aereck.joking")
        h.pick(2)
        h.expect("father_aereck.joking", "father_aereck.i_see")
        h.menu(*rootAfter)
        h.pick(1)
        h.expect(
            "father_aereck.prayers", "father_aereck.prayers_1", "father_aereck.saradomin_thing", "father_aereck.other_gods",
            "father_aereck.curious", "father_aereck.battle", "father_aereck.protection", "father_aereck.hint_prayers",
            "father_aereck.not_free", "father_aereck.hint_points", "father_aereck.faith", "father_aereck.altars",
            "father_aereck.hint_altar", "father_aereck.anything_else", "father_aereck.dedication", "father_aereck.how",
            "father_aereck.bury", "father_aereck.hint_bones", "father_aereck.covers", "father_aereck.hint_guide",
            "father_aereck.thanks",
        )
        h.menu(*rootAfter)
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
