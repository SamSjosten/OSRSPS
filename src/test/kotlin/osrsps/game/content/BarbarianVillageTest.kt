package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.ObjType
import osrsps.game.Skill
import osrsps.game.content.BarbarianVillage.atlas
import osrsps.game.content.BarbarianVillage.barbarian
import osrsps.game.content.BarbarianVillage.checkal
import osrsps.game.content.BarbarianVillage.gunthor
import osrsps.game.content.BarbarianVillage.hunding
import osrsps.game.content.BarbarianVillage.litara
import osrsps.game.content.BarbarianVillage.peksa
import osrsps.game.content.BarbarianVillage.sigurd
import osrsps.game.content.BarbarianVillage.tassie

/** Edgeville step 5: Barbarian Village's people, with no dialogue file, so every line shows its key. */
class BarbarianVillageTest {
    private val h = DialogueHarness()

    @Test
    fun `Peksa trades on a yes and says goodbye on a no`() {
        var trade = false
        h.talk { trade = peksa() }
        h.expect("peksa.greeting")
        h.menu("peksa.yes", "peksa.no")
        h.pick(1)
        assertEquals(-1, h.player.chatModal)
        assertEquals(true, trade)

        h.talk { trade = peksa() }
        h.expect("peksa.greeting")
        h.menu("peksa.yes", "peksa.no")
        h.pick(2)
        h.expect("peksa.no", "peksa.no_reply")
        h.ends()
        assertEquals(false, trade)
    }

    @Test
    fun `Tassie Slipcast has two boxes`() {
        h.talk { tassie() }
        h.expect("tassie_slipcast.use_wheel", "tassie_slipcast.tidy")
        h.ends()
    }

    @Test
    fun `Hunding answers every option of both menus`() {
        fun open() {
            h.talk { hunding() }
            h.expect("hunding.hello", "hunding.what_doing")
            h.menu("hunding.nothing_much", "hunding.exploring", "hunding.kill")
        }
        open()
        h.pick(1)
        h.expect("hunding.nothing_much", "hunding.longhall", "hunding.bear_in_mind")
        h.ends()
        open()
        h.pick(3)
        h.expect("hunding.kill", "hunding.kill_reply")
        h.ends()
        val second = listOf(
            listOf("hunding.tribe_said") + (1..9).map { "hunding.tribe_$it" } + "hunding.thank_you",
            listOf("hunding.savages", "hunding.savages_reply"),
            listOf("hunding.bored", "hunding.bored_reply", "hunding.bored_bear"),
        )
        for ((i, pages) in second.withIndex()) {
            open()
            h.pick(2)
            h.expect("hunding.exploring", "hunding.cannot_hope")
            h.menu("hunding.tribe", "hunding.savages", "hunding.bored")
            h.pick(i + 1)
            h.expect(*pages.toTypedArray())
            h.ends()
        }
    }

    @Test
    fun `Atlas offers a workout for 25,000 coins, which isn't built, and takes no coins`() {
        fun open() {
            h.talk { atlas() }
            h.expect("atlas.proven", "atlas.again", "atlas.why", "atlas.fun", "atlas.fee name=Zezima")
            h.menu("atlas.yes", "atlas.no")
        }
        open()
        h.pick(2)
        assertEquals(-1, h.player.chatModal)
        open()
        h.pick(1)
        h.expect("atlas.cant_afford", "atlas.too_excited", "atlas.come_back")
        h.ends()
        h.player.inventory.add(ObjType(BarbarianVillage.COINS, "Coins", stackable = true), 25_000)
        open()
        h.pick(1)
        h.expect("atlas.lets_do", "atlas.not_easier", "atlas.workout_closed")
        h.ends()
        assertEquals(25_000, h.player.inventory.count(BarbarianVillage.COINS))
    }

    @Test
    fun `Checkal and Litara talk as the Wiki has them`() {
        h.talk { checkal() }
        h.expect("checkal.glad name=Zezima", "checkal.why_not", "checkal.friends", "checkal.sorry name=Zezima", "checkal.understand", "checkal.dont_let")
        h.ends()

        h.talk { litara() }
        h.expect("litara.greeting")
        h.menu("litara.stronghold", "litara.passing")
        h.pick(1)
        h.expect(
            "litara.stronghold", "litara.stronghold_reply", "litara.points", "litara.deep", "litara.brother", "litara.your_brother",
            "litara.explorer", "litara.not_good", "litara.lots", "litara.eyes_open",
        )
        h.ends()
        h.talk { litara() }
        h.expect("litara.greeting")
        h.menu("litara.stronghold", "litara.passing")
        h.pick(2)
        h.expect("litara.passing")
        h.ends()
    }

    @Test
    fun `Sigurd tells his story and teaches by Woodcutting level`() {
        h.talk { sigurd() }
        h.expect("sigurd.hello", "sigurd.ha_ha")
        h.menu("sigurd.who", "sigurd.teach")
        h.pick(1)
        h.expect(
            "sigurd.who", "sigurd.great", "sigurd.why_called", "sigurd.invented", "sigurd.log_canoe", "sigurd.cousins", "sigurd.smack",
            "sigurd.game", "sigurd.won", "sigurd.woke", "sigurd.so_invented", "sigurd.refined", "sigurd.branches",
        )
        h.menu("sigurd.yes", "sigurd.no")
        h.pick(2)
        h.expect("sigurd.no")
        h.ends()

        val levels = listOf(
            1 to listOf("sigurd.low_1", "sigurd.low_2", "sigurd.low_3"),
            12 to listOf("sigurd.simple", "sigurd.axe", "sigurd.log_1", "sigurd.log_2"),
            27 to listOf("sigurd.simple", "sigurd.axe", "sigurd.dugout"),
            42 to listOf("sigurd.simple", "sigurd.axe", "sigurd.handy", "sigurd.stable", "sigurd.reckons"),
            57 to listOf("sigurd.simple", "sigurd.axe", "sigurd.know", "sigurd.what_waka", "sigurd.hari_1", "sigurd.hari_2", "sigurd.hari_3"),
        )
        for ((level, pages) in levels) {
            h.player.skills.setLevel(Skill.WOODCUTTING, level)
            h.talk { sigurd() }
            h.expect("sigurd.hello", "sigurd.ha_ha")
            h.menu("sigurd.who", "sigurd.teach")
            h.pick(2)
            h.expect("sigurd.teach_said", *pages.toTypedArray())
            h.ends()
        }
        // Yes after his story teaches the same way.
        h.talk { sigurd() }
        h.expect("sigurd.hello", "sigurd.ha_ha")
        h.menu("sigurd.who", "sigurd.teach")
        h.pick(1)
        h.expect(
            "sigurd.who", "sigurd.great", "sigurd.why_called", "sigurd.invented", "sigurd.log_canoe", "sigurd.cousins", "sigurd.smack",
            "sigurd.game", "sigurd.won", "sigurd.woke", "sigurd.so_invented", "sigurd.refined", "sigurd.branches",
        )
        h.menu("sigurd.yes", "sigurd.no")
        h.pick(1)
        h.expect("sigurd.teach_said", *levels.last().second.toTypedArray())
        h.ends()
    }

    @Test
    fun `a barbarian says one of his six lines and Gunthor one of his six talks`() {
        for (i in 0 until 6) {
            h.talk { barbarian { i } }
            h.expect("barbarian.random_${i + 1}")
            h.ends()
        }
        val talks = listOf(
            listOf("gunthor.come_for_fight"),
            listOf("gunthor.what_want", "gunthor.offering", "gunthor.a_fight"),
            listOf("gunthor.look_odd"),
            listOf("gunthor.wanna_fight"),
            listOf("gunthor.growl"),
            listOf("gunthor.go_away"),
        )
        for ((i, pages) in talks.withIndex()) {
            h.talk { gunthor { i } }
            h.expect(*pages.toTypedArray())
            h.ends()
        }
    }

    @Test
    fun `the fighters are the village's seventeen barbarians, less Hunding`() {
        assertEquals(17, BarbarianVillage.BARBARIANS.size)
        assertEquals(false, BarbarianVillage.HUNDING in BarbarianVillage.BARBARIANS)
    }
}
