package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.VarTypes
import osrsps.game.VarbitType

/** Draynor step 6: the Wise Old Man's, Miss Schism's and the bank guard's talks, with every line showing its key. */
class WiseOldManTest {
    // wom_bankjob: varp 279, bits 17 to 20, as in the 241 cache.
    private val h = DialogueHarness(varTypes = VarTypes(6_000, mapOf(WiseOldMan.BANKJOB_VARBIT to VarbitType(279, 17, 20))))

    private val give: (Player, Int) -> Unit = { player, obj -> player.inventory.add(type(obj), 1) }

    private fun type(id: Int) = ObjType(id, "Item $id", stackable = id == WiseOldMan.COINS)

    private fun count(id: Int) = h.player.inventory.count(id)

    private fun keys(talk: List<Said>) = talk.map { it.line.key }.toTypedArray()

    private val mainMenu = arrayOf("wise_old_man.hood", "wise_old_man.cape", "wise_old_man.more")
    private val categories = arrayOf("wise_old_man.distant", "wise_old_man.beasts", "wise_old_man.days", "wise_old_man.gods", "wise_old_man.you")

    private fun startWom() {
        h.talk { with(WiseOldMan) { wom(give) } }
        h.expect("wise_old_man.greetings name=Zezima")
        h.menu(*mainMenu)
    }

    private fun startTopics() {
        startWom()
        h.pick(3)
        h.menu("wise_old_man.anything", "wise_old_man.ask")
        h.pick(2)
        h.expect("wise_old_man.ask", "wise_old_man.please_do")
        h.menu(*categories)
    }

    @Test
    fun `the hood is free with a slot for it, and the menu comes back`() {
        startWom()
        h.pick(1)
        h.expect("wise_old_man.hood", "wise_old_man.hood_given")
        h.menu(*mainMenu)
        assertEquals(1, count(WiseOldMan.QUEST_HOOD))
        assertEquals(listOf(WiseOldMan.QUEST_HOOD), h.boxObjs)

        repeat(h.player.inventory.freeSlots) { h.player.inventory.add(type(1), 1) }
        h.pick(1)
        h.expect("wise_old_man.hood", "wise_old_man.hood_no_room")
        h.menu(*mainMenu)
        assertEquals(1, count(WiseOldMan.QUEST_HOOD))
    }

    @Test
    fun `the cape costs 99,000 coins and two free slots`() {
        h.player.inventory.add(type(WiseOldMan.COINS), 98_999)
        startWom()
        h.pick(2)
        h.expect("wise_old_man.cape", "wise_old_man.cape_price")
        h.menu("wise_old_man.cape_okay", "wise_old_man.cape_no_thanks")
        h.pick(1)
        h.expect("wise_old_man.cape_okay", "wise_old_man.cape_no_coins")
        h.menu(*mainMenu)

        h.player.inventory.add(type(WiseOldMan.COINS), 1)
        repeat(h.player.inventory.freeSlots - 1) { h.player.inventory.add(type(1), 1) }
        h.pick(2)
        h.expect("wise_old_man.cape", "wise_old_man.cape_price")
        h.menu("wise_old_man.cape_okay", "wise_old_man.cape_no_thanks")
        h.pick(1)
        h.expect("wise_old_man.cape_okay", "wise_old_man.cape_no_room")
        h.menu(*mainMenu)

        h.player.inventory.remove(h.player.inventory.contents().last().first)
        h.pick(2)
        h.expect("wise_old_man.cape", "wise_old_man.cape_price")
        h.menu("wise_old_man.cape_okay", "wise_old_man.cape_no_thanks")
        h.pick(1)
        h.expect("wise_old_man.cape_okay", "wise_old_man.cape_given")
        h.menu(*mainMenu)
        assertEquals(0, count(WiseOldMan.COINS))
        assertEquals(1, count(WiseOldMan.QUEST_CAPE))
        assertEquals(1, count(WiseOldMan.QUEST_HOOD))

        h.pick(2)
        h.expect("wise_old_man.cape", "wise_old_man.cape_price")
        h.menu("wise_old_man.cape_okay", "wise_old_man.cape_no_thanks")
        h.pick(2)
        h.expect("wise_old_man.cape_no_thanks")
        h.menu(*mainMenu)
    }

    @Test
    fun `with no tasks built he has nothing for the player to do`() {
        startWom()
        h.pick(3)
        h.menu("wise_old_man.anything", "wise_old_man.ask")
        h.pick(1)
        h.expect("wise_old_man.anything", "wise_old_man.nothing_now")
        h.ends()
    }

    @Test
    fun `every topic says its lines and closes, and Yes please goes back to the topics`() {
        val topics = with(WomLines) {
            listOf(
                Triple(1, listOf(WILDERNESS, JUNGLES, UNDERGROUND, REALMS), listOf(WILDERNESS_TALK, JUNGLES_TALK, UNDERGROUND_TALK, REALMS_TALK)),
                Triple(2, listOf(BIGGEST, POISON, WEALTH, RANDOM), listOf(BIGGEST_TALK, POISON_TALK, WEALTH_TALK, RANDOM_TALK)),
                Triple(3, listOf(HEROES, MAGIC, SETTLEMENTS, HISTORY), listOf(HEROES_TALK, MAGIC_TALK, SETTLEMENTS_TALK, HISTORY_TALK)),
                Triple(4, listOf(THREE_GODS, WARS, MAHJARRAT, WIELDING), listOf(THREE_GODS_TALK, WARS_TALK, MAHJARRAT_TALK, WIELDING_TALK)),
            )
        }
        for ((category, options, talks) in topics) {
            for ((i, talk) in talks.withIndex()) {
                startTopics()
                h.pick(category)
                h.menu(*options.map { it.key }.toTypedArray())
                h.pick(i + 1)
                when (talk) {
                    // Desert Treasure I's lines end these two.
                    WomLines.THREE_GODS_TALK, WomLines.MAHJARRAT_TALK -> {
                        h.expect(*keys(talk))
                        h.ends()
                        continue
                    }
                    WomLines.HISTORY_TALK -> h.expect(*keys(talk))
                    else -> h.expect(*keys(talk), "wise_old_man.close")
                }
                h.menu("wise_old_man.yes", "wise_old_man.thanks")
                h.pick(1)
                h.expect("wise_old_man.yes")
                h.menu(*categories)
                h.pick(category)
                h.menu(*options.map { it.key }.toTypedArray())
                h.pick(i + 1)
                if (talk == WomLines.HISTORY_TALK) h.expect(*keys(talk)) else h.expect(*keys(talk), "wise_old_man.close")
                h.menu("wise_old_man.yes", "wise_old_man.thanks")
                h.pick(2)
                h.expect("wise_old_man.thanks", "wise_old_man.farewell name=Zezima")
                h.ends()
            }
        }
    }

    @Test
    fun `the Swan Song topics, and the retirement talk ends with no farewell`() {
        startTopics()
        h.pick(5)
        h.menu("wise_old_man.hat", "wise_old_man.quest", "wise_old_man.retirement")
        h.pick(2)
        h.expect(*keys(WomLines.QUEST_TALK), "wise_old_man.close")
        h.menu("wise_old_man.yes", "wise_old_man.thanks")
        h.pick(1)
        h.expect("wise_old_man.yes")
        h.menu(*categories)
        h.pick(5)
        h.menu("wise_old_man.hat", "wise_old_man.quest", "wise_old_man.retirement")
        h.pick(3)
        h.expect(*keys(WomLines.RETIREMENT_TALK))
        h.menu("wise_old_man.yes", "wise_old_man.thanks")
        h.pick(2)
        h.expect("wise_old_man.thanks")
        h.ends()
    }

    @Test
    fun `the hat talk changes once the recording is seen, and asking about it then counts as confronting him`() {
        startTopics()
        h.pick(5)
        h.menu("wise_old_man.hat", "wise_old_man.quest", "wise_old_man.retirement")
        h.pick(1)
        h.expect(*keys(WomLines.HAT_TALK), *keys(WomLines.HAT_UNSEEN_TALK))
        h.menu("wise_old_man.how_hat", "wise_old_man.yes")
        h.pick(1)
        h.expect("wise_old_man.how_hat", *keys(WomLines.HOW_HAT_TALK))
        h.menu("wise_old_man.give_back", "wise_old_man.yes", "wise_old_man.thanks")
        h.pick(1)
        h.expect("wise_old_man.give_back", *keys(WomLines.GIVE_BACK_TALK))
        h.menu("wise_old_man.how_hat", "wise_old_man.yes", "wise_old_man.thanks")
        h.pick(3)
        h.expect("wise_old_man.thanks", "wise_old_man.farewell name=Zezima")
        h.ends()
        assertEquals(0, WiseOldMan.bankjob(h.player))

        h.player.vars.setVarbit(WiseOldMan.BANKJOB_VARBIT, WiseOldMan.SEEN)
        startTopics()
        h.pick(5)
        h.menu("wise_old_man.hat", "wise_old_man.quest", "wise_old_man.retirement")
        h.pick(1)
        h.expect(*keys(WomLines.HAT_TALK), *keys(WomLines.HAT_SEEN_TALK))
        h.menu("wise_old_man.how_hat", "wise_old_man.yes", "wise_old_man.thanks")
        h.pick(2)
        h.expect("wise_old_man.yes")
        h.menu(*categories)
        assertEquals(WiseOldMan.CONFRONTED, WiseOldMan.bankjob(h.player))
    }

    @Test
    fun `the telescope talk changes once the recording is seen`() {
        val womSays: suspend osrsps.game.dialogue.Dialogue.(osrsps.game.dialogue.Line) -> Unit = { chatNpc(null, it) }
        h.talk { with(WiseOldMan) { telescope(womSays) } }
        h.expect(*keys(WomLines.TELESCOPE), *keys(WomLines.TELESCOPE_UNSEEN))
        h.ends()
        h.player.vars.setVarbit(WiseOldMan.BANKJOB_VARBIT, WiseOldMan.SEEN)
        h.talk { with(WiseOldMan) { telescope(womSays) } }
        h.expect(*keys(WomLines.TELESCOPE), *keys(WomLines.TELESCOPE_SEEN))
        h.ends()
    }

    @Test
    fun `Miss Schism's news waits for the recording, and who she is goes back to her menu`() {
        val menu = arrayOf("miss_schism.news", "miss_schism.who", "miss_schism.horrible")
        h.talk { with(WiseOldMan) { missSchism() } }
        h.expect("miss_schism.news_opener")
        h.menu(*menu)
        h.pick(2)
        h.expect("miss_schism.who", *keys(MissSchismLines.WHO_TALK))
        h.menu(*menu)
        h.pick(1)
        h.expect(*keys(MissSchismLines.UNSEEN_TALK))
        h.ends()

        h.player.vars.setVarbit(WiseOldMan.BANKJOB_VARBIT, WiseOldMan.SEEN)
        for ((option, keys) in listOf(1 to arrayOf("miss_schism.new", "miss_schism.new_answer"), 2 to arrayOf("miss_schism.ages", "miss_schism.ages_answer"))) {
            h.talk { with(WiseOldMan) { missSchism() } }
            h.expect("miss_schism.news_opener")
            h.menu(*menu)
            h.pick(1)
            h.expect(*keys(MissSchismLines.SEEN_TALK))
            h.menu("miss_schism.new", "miss_schism.ages", "miss_schism.enough")
            h.pick(option)
            h.expect(*keys, *keys(MissSchismLines.STORY_TALK))
            h.ends()
        }
        h.talk { with(WiseOldMan) { missSchism() } }
        h.expect("miss_schism.news_opener")
        h.menu(*menu)
        h.pick(1)
        h.expect(*keys(MissSchismLines.SEEN_TALK))
        h.menu("miss_schism.new", "miss_schism.ages", "miss_schism.enough")
        h.pick(3)
        h.expect("miss_schism.enough", "miss_schism.enough_answer")
        h.ends()

        h.talk { with(WiseOldMan) { missSchism() } }
        h.expect("miss_schism.news_opener")
        h.menu(*menu)
        h.pick(3)
        h.expect("miss_schism.horrible", "miss_schism.oooh")
        h.ends()
    }

    private val payFromBank: (osrsps.game.Player, Int) -> Unit = { player, coins -> Ned.take(player.bank, WiseOldMan.COINS, coins) }

    private val recording = arrayOf("wom_bankguard.eyes", "wom_bankguard.plays", "wom_bankguard.end")

    @Test
    fun `the bank guard shows the recording free the first time, and the player has seen it`() {
        h.talk { with(WiseOldMan) { bankGuard(payFromBank) } }
        h.expect("wom_bankguard.yes_q", *keys(BankGuardLines.FIRST_TALK))
        h.menu("wom_bankguard.see", "wom_bankguard.robber")
        h.pick(2)
        h.expect("wom_bankguard.robber", "wom_bankguard.robber_answer")
        h.menu("wom_bankguard.see", "wom_bankguard.stop")
        h.pick(2)
        h.expect("wom_bankguard.stop", "wom_bankguard.goodbye")
        h.ends()

        h.talk { with(WiseOldMan) { bankGuard(payFromBank) } }
        h.expect("wom_bankguard.yes_q", *keys(BankGuardLines.FIRST_TALK))
        h.menu("wom_bankguard.see", "wom_bankguard.robber")
        h.pick(1)
        h.expect("wom_bankguard.see", "wom_bankguard.see_long")
        h.menu("wom_bankguard.show", "wom_bankguard.another_day")
        h.pick(2)
        h.expect("wom_bankguard.another_day", "wom_bankguard.ok")
        h.ends()
        assertEquals(0, WiseOldMan.bankjob(h.player))

        h.talk { with(WiseOldMan) { bankGuard(payFromBank) } }
        h.expect("wom_bankguard.yes_q", *keys(BankGuardLines.FIRST_TALK))
        h.menu("wom_bankguard.see", "wom_bankguard.robber")
        h.pick(1)
        h.expect("wom_bankguard.see", "wom_bankguard.see_long")
        h.menu("wom_bankguard.show", "wom_bankguard.another_day")
        h.pick(1)
        h.expect("wom_bankguard.show", "wom_bankguard.show_how", *recording)
        h.ends()
        assertEquals(WiseOldMan.SEEN, WiseOldMan.bankjob(h.player))
    }

    @Test
    fun `after the recording the guard charges 50 coins, from the bank if need be`() {
        h.player.vars.setVarbit(WiseOldMan.BANKJOB_VARBIT, WiseOldMan.SEEN)
        val menu = arrayOf("wom_bankguard.familiar", "wom_bankguard.replay", "wom_bankguard.sorry")
        fun replay(vararg then: String) {
            h.talk { with(WiseOldMan) { bankGuard(payFromBank) } }
            h.expect("wom_bankguard.yes_q")
            h.menu(*menu)
            h.pick(2)
            h.expect("wom_bankguard.replay", "wom_bankguard.fifty", *then)
        }

        replay("wom_bankguard.not_enough", "wom_bankguard.no_bank")
        h.ends()

        h.player.bank.add(type(WiseOldMan.COINS), 60)
        replay("wom_bankguard.not_enough", "wom_bankguard.from_bank")
        h.menu("wom_bankguard.pay_bank", "wom_bankguard.another_day")
        h.pick(1)
        h.expect(*recording)
        h.ends()
        assertEquals(10, h.player.bank.count(WiseOldMan.COINS))

        h.player.inventory.add(type(WiseOldMan.COINS), 55)
        replay()
        h.menu("wom_bankguard.pay", "wom_bankguard.another_day")
        h.pick(1)
        h.expect(*recording)
        h.ends()
        assertEquals(5, count(WiseOldMan.COINS))

        h.talk { with(WiseOldMan) { bankGuard(payFromBank) } }
        h.expect("wom_bankguard.yes_q")
        h.menu(*menu)
        h.pick(3)
        h.expect("wom_bankguard.sorry", "wom_bankguard.ok")
        h.ends()
    }

    @Test
    fun `the robber looked familiar, and the guard knows once the player has confronted him`() {
        for ((value, talk) in listOf(WiseOldMan.SEEN to BankGuardLines.NOT_CONFRONTED_TALK, WiseOldMan.CONFRONTED to BankGuardLines.CONFRONTED_TALK)) {
            h.player.vars.setVarbit(WiseOldMan.BANKJOB_VARBIT, value)
            h.talk { with(WiseOldMan) { bankGuard(payFromBank) } }
            h.expect("wom_bankguard.yes_q")
            h.menu("wom_bankguard.familiar", "wom_bankguard.replay", "wom_bankguard.sorry")
            h.pick(1)
            h.expect("wom_bankguard.familiar", "wom_bankguard.familiar_answer", *keys(talk), *keys(BankGuardLines.ARREST_TALK))
            h.ends()
        }
    }
}
