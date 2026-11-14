package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos

/** The museum's varbits as the 242 cache defines them (osrs-dumps 2026-11-11-rev242, config/dump.varbit). */
val MUSEUM_VARBITS: Map<Int, VarbitType> = mapOf(
    Kudos.VARBIT_KUDOS to VarbitType(1010, 0, 10),
    Kudos.VARBIT_NECKLACE to VarbitType(1010, 12, 12),
    Kudos.VARBIT_REWARD_50 to VarbitType(1010, 13, 13),
    Kudos.VARBIT_REWARD_100 to VarbitType(1010, 14, 14),
    Kudos.VARBIT_REWARD_150 to VarbitType(1010, 15, 15),
    Kudos.VARBIT_ANCIENT_SYMBOL to VarbitType(1011, 2, 2),
    Kudos.VARBIT_OLD_SYMBOL to VarbitType(1011, 3, 3),
    Kudos.VARBIT_ANCIENT_COIN to VarbitType(1011, 4, 4),
    Kudos.VARBIT_OLD_COIN to VarbitType(1011, 5, 5),
    Kudos.VARBIT_POTTERY to VarbitType(1011, 6, 6),
    NaturalHistoryQuiz.VARBIT_AGREED to VarbitType(1014, 1, 1),
    NaturalHistoryQuiz.VARBIT_REWARD_GIVEN to VarbitType(1014, 31, 31),
) + (3672..3685).associateWith { VarbitType(1014, 2 + 2 * (it - 3672), 3 + 2 * (it - 3672)) } +
    mapOf(
        MuseumTimeline.VARBIT_STAFF_OF_ARMADYL to VarbitType(1011, 7, 8),
        MuseumTimeline.VARBIT_SALVE_KNIGHTS to VarbitType(1011, 9, 10),
        MuseumTimeline.VARBIT_DEFENDER to VarbitType(1011, 21, 21),
        MuseumTimeline.VARBIT_SHIELD_SHOWN to VarbitType(1011, 22, 22),
    ) + (MuseumTimeline.VARBIT_OBSERVATORY..MuseumTimeline.VARBIT_RUNES).associateWith { VarbitType(1011, 11 + it - 3652, 11 + it - 3652) }

/** Kudos counted from the museum's varbits, the clerk's rewards, and cleaning never turning up a find twice (R29B-2). */
class MuseumKudosTest {
    private fun player() = Player(1, "alice", 3260, 3445, 0, VarTypes(6_000, MUSEUM_VARBITS))

    /** How many of [rolls] cleans turn up [obj], from a fixed seed. */
    private fun found(player: Player, obj: Int, rolls: Int = 5_000): Int {
        val random = GameRandom(42)
        return (1..rolls).count { MuseumCleaning.clean(random, player)?.first == obj }
    }

    @Test
    fun `each display filled is 10 Kudos, written to the client's count`() {
        val alice = player()
        assertEquals(0, Kudos.total(alice))
        alice.vars.setVarbit(Kudos.VARBIT_OLD_SYMBOL, 1)
        alice.vars.setVarbit(Kudos.VARBIT_POTTERY, 1)
        Kudos.refresh(alice)
        assertEquals(20, alice.vars.getVarbit(Kudos.VARBIT_KUDOS))
        assertEquals(2, Kudos.done(alice, Kudos.DIG_SITE))
        // The necklace and the rewards are no Kudos.
        alice.vars.setVarbit(Kudos.VARBIT_NECKLACE, 1)
        alice.vars.setVarbit(Kudos.VARBIT_REWARD_50, 1)
        assertEquals(20, Kudos.total(alice))
    }

    @Test
    fun `a unique turns up until it's held anywhere or on display, and the necklace until its secret is learnt`() {
        val alice = player()
        val symbol = ObjType(MuseumCleaning.OLD_SYMBOL, "Old symbol")
        val necklace = ObjType(MuseumCleaning.CLEAN_NECKLACE, "Clean necklace")
        assertTrue(found(alice, MuseumCleaning.OLD_SYMBOL) > 0)
        assertTrue(found(alice, MuseumCleaning.CLEAN_NECKLACE) > 0)

        alice.bank.add(symbol, 1)
        assertEquals(0, found(alice, MuseumCleaning.OLD_SYMBOL))
        alice.bank.clear()
        alice.inventory.add(symbol, 1)
        assertEquals(0, found(alice, MuseumCleaning.OLD_SYMBOL))
        alice.inventory.clear()
        alice.equipment[WearPos.NECK] = InvObj(MuseumCleaning.CLEAN_NECKLACE, 1)
        assertEquals(0, found(alice, MuseumCleaning.CLEAN_NECKLACE))
        alice.equipment.clear()

        // Dropped before it was handed in, it turns up again (guessed); on display, never.
        assertTrue(found(alice, MuseumCleaning.OLD_SYMBOL) > 0)
        alice.vars.setVarbit(Kudos.VARBIT_OLD_SYMBOL, 1)
        assertEquals(0, found(alice, MuseumCleaning.OLD_SYMBOL))
        assertTrue(found(alice, MuseumCleaning.ANCIENT_SYMBOL) > 0)
        alice.vars.setVarbit(Kudos.VARBIT_NECKLACE, 1)
        assertEquals(0, found(alice, MuseumCleaning.CLEAN_NECKLACE))
        // Pottery's case doesn't stop pottery.
        alice.vars.setVarbit(Kudos.VARBIT_POTTERY, 1)
        assertTrue(found(alice, MuseumCleaning.POTTERY) > 0)
        alice.inventory.add(necklace, 1)
    }

    @Test
    fun `the clerk's rewards wait for their Kudos and are given once`() {
        val alice = player()
        assertEquals(emptyList(), Kudos.pendingRewards(alice))
        for (case in MuseumFinds.CASES) alice.vars.setVarbit(case.varbit, 1)
        // 50 Kudos is one short of the first reward.
        assertEquals(50, Kudos.total(alice))
        assertEquals(emptyList(), Kudos.pendingRewards(alice))
        // A natural history display done is 2 more, enough for the first.
        alice.vars.setVarbit(NaturalHistoryQuiz.DISPLAYS.first().varbit, NaturalHistoryQuiz.DONE)
        assertEquals(listOf(Kudos.Reward.AT_51), Kudos.pendingRewards(alice))
        Kudos.claim(alice, Kudos.pendingRewards(alice))
        assertEquals(emptyList(), Kudos.pendingRewards(alice))
    }

    @Test
    fun `a natural history display is 2 Kudos at its third right answer, and all fourteen are 28`() {
        val alice = player()
        val snail = NaturalHistoryQuiz.DISPLAYS.single { it.animal == "snail" }
        alice.vars.setVarbit(snail.varbit, 2)
        assertEquals(0, Kudos.total(alice))
        alice.vars.setVarbit(snail.varbit, 3)
        assertEquals(2, Kudos.total(alice))
        for (display in NaturalHistoryQuiz.DISPLAYS) alice.vars.setVarbit(display.varbit, 3)
        assertEquals(28, Kudos.total(alice))
        assertTrue(NaturalHistoryQuiz.allDone(alice))
        // The client's vm_nathis_display_alldone is the same bits, every display at once (osrs-dumps: bits 2 to 29).
        assertEquals((1 shl 28) - 1, alice.vars[1014] shr 2 and ((1 shl 28) - 1))
    }

    @Test
    fun `the displays are the cache's fourteen plaques and varbits, each question with one right answer of three`() {
        val displays = NaturalHistoryQuiz.DISPLAYS
        assertEquals((24605..24618).toSet(), displays.map { it.plaque }.toSet())
        assertEquals((3672..3685).toSet(), displays.map { it.varbit }.toSet())
        assertEquals((1..14).toSet(), displays.map { it.number }.toSet())
        assertEquals(NaturalHistoryQuiz.ANIMALS, displays.map { it.animal })
        for (display in displays) {
            assertEquals(NaturalHistoryQuizLines.QUESTIONS, display.right.size)
            assertTrue(display.right.all { it in 0..2 })
            assertEquals(NaturalHistoryQuizLines.QUESTIONS, display.lines.questions.size)
        }
    }

    @Test
    fun `each timeline story fills its display for 5 Kudos, Curse of the Empty Lord 10, 80 in all`() {
        val alice = player()
        assertEquals(MuseumTimeline.Story.entries, MuseumTimeline.untold(alice))
        MuseumTimeline.fill(alice, MuseumTimeline.Story.TEMPLE_OF_IKOV)
        assertEquals(5, alice.vars.getVarbit(Kudos.VARBIT_KUDOS))
        MuseumTimeline.fill(alice, MuseumTimeline.Story.CURSE_OF_THE_EMPTY_LORD)
        assertEquals(2, alice.vars.getVarbit(MuseumTimeline.VARBIT_STAFF_OF_ARMADYL))
        assertEquals(15, Kudos.total(alice))
        MuseumTimeline.fill(alice, MuseumTimeline.Story.PRIEST_IN_PERIL)
        MuseumTimeline.fill(alice, MuseumTimeline.Story.MYREQUE)
        assertEquals(25, Kudos.total(alice))
        // Told already, a story isn't told or counted again.
        assertEquals(MuseumTimeline.Story.entries - setOf(MuseumTimeline.Story.TEMPLE_OF_IKOV, MuseumTimeline.Story.CURSE_OF_THE_EMPTY_LORD, MuseumTimeline.Story.PRIEST_IN_PERIL, MuseumTimeline.Story.MYREQUE), MuseumTimeline.untold(alice))
        MuseumTimeline.fill(alice, MuseumTimeline.Story.TEMPLE_OF_IKOV)
        assertEquals(2, alice.vars.getVarbit(MuseumTimeline.VARBIT_STAFF_OF_ARMADYL))
        assertEquals(0, alice.vars.getVarbit(MuseumTimeline.VARBIT_SHIELD_SHOWN))
        for (story in MuseumTimeline.Story.entries) MuseumTimeline.fill(alice, story)
        assertEquals(1, alice.vars.getVarbit(MuseumTimeline.VARBIT_SHIELD_SHOWN))
        assertEquals(80, Kudos.total(alice))
        assertEquals(Kudos.TIMELINE.size, Kudos.done(alice, Kudos.TIMELINE))
        assertTrue(MuseumTimeline.untold(alice).isEmpty())
    }

    @Test
    fun `the timeline is the client's fifteen rows, and everything built is 158 Kudos, past the Digsite gate's 153`() {
        assertEquals(15, Kudos.TIMELINE.size)
        assertEquals(80, Kudos.TIMELINE.sumOf { it.kudos })
        assertEquals(158, Kudos.SOURCES.sumOf { it.kudos })
        assertTrue(Kudos.SOURCES.sumOf { it.kudos } >= Kudos.DIGSITE_GATE)
        // Five stories give a lamp, each its own of the cache's vm_reward_* lamps.
        val lamps = MuseumTimeline.Story.entries.mapNotNull { it.lamp }
        assertEquals(5, lamps.toSet().size)
        assertEquals(MuseumTimeline.Story.entries.map { it.varbit }.toSet(), Kudos.TIMELINE.map { it.varbit }.toSet())
    }

    @Test
    fun `claiming gives each reward's XP exactly and marks it claimed`() {
        val alice = player()
        Kudos.claim(alice, listOf(Kudos.Reward.AT_51, Kudos.Reward.AT_151))
        assertEquals(1_000, alice.skills.xp(Skill.MINING))
        for (skill in listOf(Skill.CRAFTING, Skill.HUNTER, Skill.PRAYER, Skill.SLAYER, Skill.SMITHING)) assertEquals(4_000, alice.skills.xp(skill))
        assertEquals(1, alice.vars.getVarbit(Kudos.VARBIT_REWARD_50))
        assertEquals(0, alice.vars.getVarbit(Kudos.VARBIT_REWARD_100))
        assertEquals(1, alice.vars.getVarbit(Kudos.VARBIT_REWARD_150))
        // Claimed rewards aren't given again.
        Kudos.claim(alice, listOf(Kudos.Reward.AT_51))
        assertEquals(1_000, alice.skills.xp(Skill.MINING))
    }
}
