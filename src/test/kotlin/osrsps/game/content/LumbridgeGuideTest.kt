package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.LumbridgeGuide.talk
import osrsps.map.CollisionMap

/** The Lumbridge Guide with no dialogue file: every line shows its key. */
class LumbridgeGuideTest {
    private val sword = ObjType(BRONZE_SWORD, "Bronze sword", wearpos1 = WearPos.WEAPON)
    private val book = ObjType(LumbridgeGuide.SECURITY_BOOK_OBJ, "Security book")
    private val world = World(
        IntArray(0), CollisionMap(),
        objTypes = ObjTypes(arrayOfNulls<ObjType>(LumbridgeGuide.SECURITY_BOOK_OBJ + 1).also { it[sword.id] = sword; it[book.id] = book }),
    )
    private val h = DialogueHarness("Lumbridge Guide")

    private fun talk() = h.talk { with(LumbridgeGuide) { talk(world) } }

    private fun expect(vararg keys: String) = h.expect(*keys.map { "lumbridge_guide.$it" }.toTypedArray())

    private fun menu(vararg keys: String) = h.menu(*keys.map { "lumbridge_guide.$it" }.toTypedArray())

    private fun firstMenu() = menu("quest", "monsters", "money", "info", "more_options")

    private fun start() {
        talk()
        expect("greeting")
        firstMenu()
    }

    @Test
    fun `with every quest done he has none to give, then the first menu comes back`() {
        start()
        h.pick(1)
        expect("quest", "no_more_quests", "quest_icon", "anything_else")
        menu("yes_please", "no_thanks")
        h.pick(1)
        expect("yes_please")
        firstMenu()
        h.pick(5)
        menu("security", "bank", "no_help", "previous")
        h.pick(4)
        firstMenu()
        h.pick(5)
        menu("security", "bank", "no_help", "previous")
        h.pick(3)
        expect("no_help_said")
        h.ends()
        assertEquals(listOf(LumbridgeGuide.QUEST_ICON_OBJ), h.boxObjs)
    }

    @Test
    fun `with no weapon he says how to get one, and more on weapons goes back to that menu`() {
        start()
        h.pick(2)
        expect("monsters", "need_weapon")
        menu("get_weapon", "staff", "once_armed")
        h.pick(1)
        expect("get_weapon", "make_find_buy")
        menu("find_weapon", "make_weapon", "buy_weapon")
        h.pick(1)
        expect("find_weapon_said", "find_1", "find_2", "find_3", "anything_else")
        menu("no_thanks", "more_weapons", "something_else")
        h.pick(2)
        menu("find_weapon", "make_weapon", "buy_weapon")
        h.pick(3)
        expect("buy_weapon", "buy_1", "buy_2", "buy_3", "anything_else")
        menu("no_thanks", "more_weapons", "something_else")
        h.pick(1)
        expect("no_thanks")
        h.ends()
    }

    @Test
    fun `making a weapon goes by Smithing level`() {
        start()
        h.pick(2)
        expect("monsters", "need_weapon")
        menu("get_weapon", "staff", "once_armed")
        h.pick(1)
        expect("get_weapon", "make_find_buy")
        menu("find_weapon", "make_weapon", "buy_weapon")
        h.pick(2)
        expect("make_weapon", "smithing", "where_smith", "smithing_tutor", "smithing_tutor_icon", "mining_icon", "mine_ore", "anything_else")
        menu("yes_please", "no_thanks")
        h.pick(2)
        expect("no_thanks")
        h.ends()
        assertEquals(listOf(LumbridgeGuide.SMITHING_TUTOR_ICON_OBJ, LumbridgeGuide.MINING_ICON_OBJ), h.boxObjs)

        h.player.skills.setLevel(Skill.SMITHING, LumbridgeGuide.SMITHING_KNOWN_LEVEL)
        start()
        h.pick(2)
        expect("monsters", "need_weapon")
        menu("get_weapon", "staff", "once_armed")
        h.pick(1)
        expect("get_weapon", "make_find_buy")
        menu("find_weapon", "make_weapon", "buy_weapon")
        h.pick(2)
        expect("make_weapon", "smithing_known")
        h.ends()
    }

    @Test
    fun `the staff answer goes back to the no-weapon menu, and once armed leads to the monsters`() {
        start()
        h.pick(2)
        expect("monsters", "need_weapon")
        menu("get_weapon", "staff", "once_armed")
        h.pick(2)
        expect("staff", "one_staff", "anything_else")
        menu("more_weapons", "something_else", "no_thanks")
        h.pick(1)
        menu("get_weapon", "staff", "once_armed")
        h.pick(3)
        expect("once_armed", "overview_1", "overview_2", "overview_3", "overview_4")
        menu("food", "better_gear", "go_kill", "kill_players")
        h.pick(4)
        expect("kill_players", "wilderness_1", "wilderness_2", "anything_else")
        menu("no_thanks", "more_combat", "something_else")
        h.pick(2)
        menu("food", "better_gear", "go_kill", "kill_players")
        h.pick(2)
        expect("better_gear", "gear_ways")
        menu("make_weapon", "buy_weapon", "staff")
        h.pick(3)
        expect("staff", "one_staff", "anything_else")
        menu("more_weapons", "something_else", "no_thanks")
        h.pick(1)
        menu("make_weapon", "buy_weapon", "staff")
        h.pick(2)
        expect("buy_weapon", "buy_1", "buy_2", "buy_3", "anything_else")
        menu("no_thanks", "more_weapons", "something_else")
        h.pick(3)
        expect("something_else", "what_else")
        firstMenu()
        h.pick(2)
        expect("monsters", "need_weapon")
        menu("get_weapon", "staff", "once_armed")
        h.pick(3)
        expect("once_armed", "overview_1", "overview_2", "overview_3", "overview_4")
        menu("food", "better_gear", "go_kill", "kill_players")
        h.pick(3)
        expect("go_kill")
        h.ends()
    }

    @Test
    fun `with a weapon carried he talks about monsters and food`() {
        h.player.inventory.add(sword, 1)
        start()
        h.pick(2)
        expect("monsters", "overview_1", "overview_2", "overview_3", "overview_4")
        menu("food", "better_gear", "go_kill", "kill_players")
        h.pick(1)
        expect("food", "foods")
        menu("cabbages", "fish", "meat")
        h.pick(2)
        expect("fish", "fishing_1", "fishing_2", "fishing_icon", "net_box", "where_net", "net_1", "net_2", "anything_else")
        menu("other_food", "something_else", "no_thanks")
        h.pick(1)
        menu("cabbages", "fish", "meat")
        h.pick(3)
        expect("meat", "meat_1", "meat_2", "range_icon", "anything_else")
        menu("other_food", "something_else", "no_thanks")
        h.pick(1)
        menu("cabbages", "fish", "meat")
        h.pick(1)
        expect("cabbages", "cabbage_field", "farming_patch", "anything_else")
        menu("other_food", "something_else", "no_thanks")
        h.pick(2)
        expect("something_else", "what_else")
        firstMenu()
        h.pick(4)
        expect("info", "wiki_1", "wiki_2", "anything_else")
        menu("yes_please", "no_thanks")
        h.pick(2)
        expect("no_thanks")
        h.ends()
        assertEquals(listOf(LumbridgeGuide.FISHING_ICON_OBJ, LumbridgeGuide.SMALL_FISHING_NET, LumbridgeGuide.RANGE_ICON_OBJ), h.boxObjs)
    }

    @Test
    fun `a worn weapon counts too`() {
        h.player.equipment[WearPos.WEAPON] = InvObj(BRONZE_SWORD, 1)
        start()
        h.pick(2)
        expect("monsters", "overview_1", "overview_2", "overview_3", "overview_4")
        menu("food", "better_gear", "go_kill", "kill_players")
        h.pick(3)
        expect("go_kill")
        h.ends()
    }

    @Test
    fun `above his combat level he leaves the monsters to the player`() {
        for (skill in listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS)) h.player.skills.setLevel(skill, 30)
        assertTrue(h.player.skills.combatLevel >= LumbridgeGuide.HIGH_COMBAT_LEVEL)
        start()
        h.pick(2)
        expect("monsters", "high_level", "combat_tutor_icon")
        firstMenu()
        h.pick(5)
        menu("security", "bank", "no_help", "previous")
        h.pick(2)
        expect("bank", "bank_upstairs", "bank_icon", "anything_else")
        menu("yes_please", "no_thanks")
        h.pick(2)
        expect("no_thanks")
        h.ends()
        assertEquals(listOf(LumbridgeGuide.COMBAT_TUTOR_ICON_OBJ), h.boxObjs)
    }

    @Test
    fun `the money menu leads to smithing, fishing and the monsters`() {
        h.player.skills.setLevel(Skill.FISHING, LumbridgeGuide.FISHING_KNOWN_LEVEL)
        start()
        h.pick(3)
        expect("money", "money_ways", "no_begging")
        menu("money_smith", "money_fish", "money_monsters")
        h.pick(1)
        expect("money_smith", "smithing_tutor", "smithing_tutor_icon", "mining_icon", "mine_ore", "anything_else")
        menu("yes_please", "no_thanks")
        h.pick(1)
        expect("yes_please")
        firstMenu()
        h.pick(3)
        expect("money", "money_ways", "no_begging")
        menu("money_smith", "money_fish", "money_monsters")
        h.pick(3)
        expect("need_weapon")
        menu("get_weapon", "staff", "once_armed")
        h.pick(2)
        expect("staff", "one_staff", "anything_else")
        menu("more_weapons", "something_else", "no_thanks")
        h.pick(2)
        expect("something_else", "what_else")
        firstMenu()
        h.pick(3)
        expect("money", "money_ways", "no_begging")
        menu("money_smith", "money_fish", "money_monsters")
        h.pick(2)
        expect("money_fish", "fishing_known")
        h.ends()
    }

    @Test
    fun `security topics come back to security, and the Stronghold answer hands over one book`() {
        start()
        h.pick(5)
        menu("security", "bank", "no_help", "previous")
        h.pick(1)
        expect("security", "security_1", "security_topics_1", "security_topics_2")
        menu("passwords", "scams", "moderation", "stronghold")
        h.pick(4)
        expect(
            "stronghold", "stronghold_1", "fell_on_head", "stronghold_2", "how_so", "stronghold_3", "where_explorer", "stronghold_4",
            "security_book", "anything_else",
        )
        menu("more_security", "something_else", "no_thanks")
        h.pick(1)
        expect("security", "security_1", "security_topics_1", "security_topics_2")
        menu("passwords", "scams", "moderation", "stronghold")
        h.pick(3)
        expect(
            "moderation", "moderation_1", "moderation_2", "moderation_3", "moderation_4", "become_mod", "become_mod_1", "become_mod_2",
            "anything_else",
        )
        menu("no_thanks", "yes_please_moderation", "more_security")
        h.pick(2)
        expect("yes_please_moderation")
        firstMenu()
        h.pick(5)
        menu("security", "bank", "no_help", "previous")
        h.pick(1)
        expect("security", "security_1", "security_topics_1", "security_topics_2")
        menu("passwords", "scams", "moderation", "stronghold")
        h.pick(4)
        expect("stronghold", "stronghold_1", "fell_on_head", "stronghold_2", "how_so", "stronghold_3", "where_explorer", "stronghold_4", "anything_else")
        menu("more_security", "something_else", "no_thanks")
        h.pick(3)
        expect("no_thanks")
        h.ends()
        assertEquals(1L, h.player.inventory.count(LumbridgeGuide.SECURITY_BOOK_OBJ))
        assertEquals(listOf(LumbridgeGuide.SECURITY_BOOK_OBJ), h.boxObjs)
    }

    @Test
    fun `password security and scams`() {
        start()
        h.pick(5)
        menu("security", "bank", "no_help", "previous")
        h.pick(1)
        expect("security", "security_1", "security_topics_1", "security_topics_2")
        menu("passwords", "scams", "moderation", "stronghold")
        h.pick(1)
        expect(
            "passwords", "password_1", "password_2", "password_3", "more_to_know", "fake_sites_1", "fake_sites_2", "trojans_1",
            "trojans_2", "trojans_3", "not_hidden", "anything_else",
        )
        menu("more_security", "something_else", "no_thanks")
        h.pick(1)
        expect("security", "security_1", "security_topics_1", "security_topics_2")
        menu("passwords", "scams", "moderation", "stronghold")
        h.pick(2)
        expect("scams", "scams_1", "scams_2", "scams_3", "scams_4", "scams_5", "scams_6", "scams_7", "scams_8", "scams_9", "anything_else")
        menu("more_security", "something_else", "no_thanks")
        h.pick(3)
        expect("no_thanks")
        h.ends()
    }

    private companion object {
        /** bronze_sword. */
        const val BRONZE_SWORD = 1277
    }
}
