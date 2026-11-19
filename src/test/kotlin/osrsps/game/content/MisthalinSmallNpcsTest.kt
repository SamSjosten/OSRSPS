package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.skill.Pickpocketing
import osrsps.map.CollisionMap

/**
 * Misthalin fixes 2: Martin the Master Gardener's talk (the Farming cape and the quest), Fluffs' catspeak talk in the
 * Lumber Yard, and every new option registered: Draynor's Town Crier, Martin's Pickpocket, Fluffs' three. No dialogue
 * file, so every line shows its key.
 */
class MisthalinSmallNpcsTest {
    private val h = DialogueHarness()

    private val given = mutableListOf<Int>()
    private val give: (Player, Int) -> Unit = { _, obj -> given += obj }

    private val coins = ObjType(MartinTheMasterGardener.COINS, "Coins", stackable = true)

    @Test
    fun `Martin talks about his cape below 99 Farming, and about the quest`() {
        h.talk { with(MartinTheMasterGardener) { talk(give) } }
        h.menu("martin.ask_cape", "martin.ask_quest")
        h.pick(1)
        h.expect("martin.what_cape", "martin.cape_answer")
        h.ends()

        h.talk { with(MartinTheMasterGardener) { talk(give) } }
        h.menu("martin.ask_cape", "martin.ask_quest")
        h.pick(2)
        h.expect("martin.solved", "martin.reserve", "martin.godfather", "martin.crops")
        h.ends()
        assertEquals(emptyList(), given)
    }

    @Test
    fun `at 99 Farming Martin sells the cape and hood for 99,000 coins, and turns away a player short of them`() {
        h.player.skills.setLevel(Skill.FARMING, 99)
        h.player.inventory.add(coins, 98_999)
        h.talk { with(MartinTheMasterGardener) { talk(give) } }
        h.menu("martin.ask_cape", "martin.ask_quest")
        h.pick(1)
        h.menu("martin.skillcape", "martin.hood")
        h.pick(1)
        h.expect("martin.can_i_buy", "martin.price")
        h.menu("martin.not_paying", "martin.sure")
        h.pick(2)
        h.expect("martin.sure", "martin.no_money", "martin.come_back")
        h.ends()
        assertEquals(emptyList(), given)

        h.player.inventory.add(coins, 1)
        h.talk { with(MartinTheMasterGardener) { talk(give) } }
        h.menu("martin.ask_cape", "martin.ask_quest")
        h.pick(1)
        h.menu("martin.skillcape", "martin.hood")
        h.pick(1)
        h.expect("martin.can_i_buy", "martin.price")
        h.menu("martin.not_paying", "martin.sure")
        h.pick(2)
        h.expect("martin.sure", "martin.sold")
        h.ends()
        assertEquals(listOf(MartinTheMasterGardener.FARMING_CAPE, MartinTheMasterGardener.FARMING_HOOD), given)
        assertEquals(0, h.player.inventory.count(MartinTheMasterGardener.COINS))
    }

    @Test
    fun `Fluffs talks to a player with a catspeak amulet as the transcript has it`() {
        h.talk { with(GertrudesCat) { talk() } }
        h.expect("fluffs.hello", "fluffs.can_talk", "fluffs.sphinx", "fluffs.thank_you", "fluffs.cute", "fluffs.purr", "fluffs.proud", "fluffs.father", "fluffs.look_after", "fluffs.you_too name=Zezima")
        h.ends()
    }

    @Test
    fun `the new options are registered on the world`() {
        val world = World(IntArray(0), CollisionMap())
        for (op in listOf(
            Op(OpTarget.NPC, VarrockTownsfolk.TOWN_CRIER_DRAYNOR, VarrockTownsfolk.TALK_TO),
            Op(OpTarget.NPC, MartinTheMasterGardener.ID, MartinTheMasterGardener.TALK_TO),
            Op(OpTarget.NPC, Pickpocketing.MARTIN, Pickpocketing.PICKPOCKET),
            Op(OpTarget.NPC, GertrudesCat.ID, GertrudesCat.PICK_UP),
            Op(OpTarget.NPC, GertrudesCat.ID, GertrudesCat.STROKE),
            Op(OpTarget.NPC, GertrudesCat.ID, GertrudesCat.TALK_TO),
        )) {
            assertNotNull(world.handlers.find(op), "$op")
        }
        assertTrue(Pickpocketing.POCKETS.any { Pickpocketing.MARTIN in it.npcs && it.level == 38 })
    }
}
