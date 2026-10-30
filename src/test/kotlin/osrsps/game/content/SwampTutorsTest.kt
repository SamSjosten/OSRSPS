package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.Skill

/** Rest of Lumbridge step 9: the swamp's mining and fishing tutors, with no dialogue file, so every line shows its key. */
class SwampTutorsTest {
    private val h = DialogueHarness()

    /** The items handed over, in order. */
    private val given = mutableListOf<Int>()
    private val give: (Player, Int) -> Unit = { player, obj ->
        given += obj
        player.inventory[player.inventory.contents().size] = InvObj(obj, 1)
    }

    private fun mining(first: String) {
        h.talk { with(SwampTutors) { miningTutor(give) } }
        h.menu(first, "mining_tutor.quests", "mining_tutor.rocks_and_picks", "mining_tutor.goodbye")
    }

    private fun fishing(first: String) {
        h.talk { with(SwampTutors) { fishingTutor(give) } }
        h.menu(first, "fishing_tutor.fish", "fishing_tutor.where", "fishing_tutor.goodbye")
    }

    @Test
    fun `the Mining tutor teaches the basics and hands over a pickaxe only when there is none`() {
        val top = arrayOf("mining_tutor.basics", "mining_tutor.quests", "mining_tutor.rocks_and_picks", "mining_tutor.goodbye")
        mining("mining_tutor.basics")
        h.pick(1)
        h.expect(
            "mining_tutor.basics", "mining_tutor.site_hint", "mining_tutor.have_a_pick", "mining_tutor.tell_ore",
            "mining_tutor.click_rock", "mining_tutor.full_inventory", "mining_tutor.bank_hint",
        )
        h.menu(*top)
        assertEquals(listOf(SwampTutors.BRONZE_PICKAXE), given)
        assertEquals(listOf(SwampTutors.MINING_SITE_ICON, SwampTutors.BANK_ICON), h.boxObjs)
        // Now carrying the pickaxe.
        h.pick(1)
        basicsAfterPick("mining_tutor.already_pick")

        // A banked pickaxe counts too, so the free one can't be sold for coins from nothing.
        h.player.inventory.clear()
        h.player.bank[0] = InvObj(SwampTutors.RUNE_PICKAXE, 1)
        mining("mining_tutor.basics")
        h.pick(1)
        basicsAfterPick("mining_tutor.already_pick")
        h.player.bank.clear()

        // None anywhere and a full inventory.
        for (slot in 0 until 28) h.player.inventory[slot] = InvObj(1931, 1)
        mining("mining_tutor.basics")
        h.pick(1)
        basicsAfterPick("mining_tutor.no_room")
        assertEquals(1, given.size)
    }

    /** The basics from the pickaxe line on, then Goodbye. */
    private fun basicsAfterPick(pickLine: String) {
        h.expect(
            "mining_tutor.basics", "mining_tutor.site_hint", pickLine, "mining_tutor.tell_ore", "mining_tutor.click_rock",
            "mining_tutor.full_inventory", "mining_tutor.bank_hint",
        )
        h.menu("mining_tutor.basics", "mining_tutor.quests", "mining_tutor.rocks_and_picks", "mining_tutor.goodbye")
        h.pick(4)
        h.expect("mining_tutor.goodbye")
        h.ends()
    }

    @Test
    fun `the Mining tutor's tips and advice offer the map, and the quests name Doric and the Dig Site`() {
        h.player.inventory[0] = InvObj(SwampTutors.BRONZE_PICKAXE, 1)
        h.player.skills.setLevel(Skill.MINING, 30)
        val tips = arrayOf("mining_tutor.tips", "mining_tutor.quests", "mining_tutor.rocks_and_picks", "mining_tutor.goodbye")
        mining("mining_tutor.tips")
        h.pick(1)
        h.expect(
            "mining_tutor.tips", "mining_tutor.already_pick", "mining_tutor.different_places", "mining_tutor.al_kharid",
            "mining_tutor.scorpions", "mining_tutor.show_al_kharid",
        )
        h.menu("mining_tutor.yes_please", "mining_tutor.no_thanks")
        h.pick(2)
        h.expect("mining_tutor.no_thanks")
        h.menu(*tips)
        h.pick(2)
        h.expect("mining_tutor.doric", "mining_tutor.dig_site")
        h.menu(*tips)
        h.pick(4)
        h.expect("mining_tutor.goodbye")
        h.ends()

        h.player.skills.setLevel(Skill.MINING, 60)
        mining("mining_tutor.advanced")
        h.pick(1)
        h.expect(
            "mining_tutor.advanced", "mining_tutor.already_pick", "mining_tutor.guild", "mining_tutor.coal_trucks",
            "mining_tutor.thank_you", "mining_tutor.show_either",
        )
        h.menu("mining_tutor.show_guild", "mining_tutor.show_coal", "mining_tutor.no_thanks")
        h.pick(1)
        h.expect("mining_tutor.show_guild")
        h.menu("mining_tutor.advanced", "mining_tutor.quests", "mining_tutor.rocks_and_picks", "mining_tutor.goodbye")
        assertEquals(emptyList(), given)
    }

    @Test
    fun `the Mining tutor's rocks menu has two pages, and each topic leads back to the rocks or the first menu`() {
        val top = arrayOf("mining_tutor.basics", "mining_tutor.quests", "mining_tutor.rocks_and_picks", "mining_tutor.goodbye")
        val rocksAndPicks = arrayOf("mining_tutor.rocks_and_ores", "mining_tutor.pickaxes", "mining_tutor.teach_mining")
        val page1 = arrayOf(
            "mining_tutor.clay_essence", "mining_tutor.copper_tin", "mining_tutor.iron_coal", "mining_tutor.teach_mining",
            "mining_tutor.more",
        )
        val page2 = arrayOf("mining_tutor.silver_gold", "mining_tutor.mithril_runite", "mining_tutor.teach_mining", "mining_tutor.back")
        val after = arrayOf("mining_tutor.teach_mining", "mining_tutor.teach_rocks")
        mining("mining_tutor.basics")
        h.pick(3)
        h.expect("mining_tutor.rocks_and_picks", "mining_tutor.here_goes")
        h.menu(*rocksAndPicks)
        h.pick(1)
        h.menu(*page1)
        h.pick(3)
        h.expect("mining_tutor.iron_hint", "mining_tutor.furnace_hint", "mining_tutor.coal_hint")
        h.menu(*after)
        h.pick(2)
        h.menu(*page1)
        h.pick(5)
        h.menu(*page2)
        h.pick(2)
        h.expect("mining_tutor.mithril_hint", "mining_tutor.runite_hint")
        h.menu(*after)
        // Back to the rocks is the first page.
        h.pick(2)
        h.menu(*page1)
        h.pick(5)
        h.menu(*page2)
        h.pick(4)
        h.menu(*page1)
        h.pick(1)
        h.expect("mining_tutor.clay_hint", "mining_tutor.essence_hint", "mining_tutor.clay_uses")
        h.menu(*after)
        h.pick(1)
        h.menu(*top)
        assertEquals(
            listOf(
                SwampTutors.IRON_ORE, SwampTutors.FURNACE_ICON, SwampTutors.COAL, SwampTutors.MITHRIL_ORE,
                SwampTutors.ADAMANTITE_ORE, SwampTutors.RUNITE_ORE, SwampTutors.CLAY, SwampTutors.RUNE_ESSENCE,
            ),
            h.boxObjs,
        )

        h.pick(3)
        h.expect("mining_tutor.rocks_and_picks", "mining_tutor.here_goes")
        h.menu(*rocksAndPicks)
        h.pick(2)
        val picks = arrayOf("mining_tutor.bronze_steel", "mining_tutor.mithril_runite", "mining_tutor.teach_mining")
        h.menu(*picks)
        h.pick(1)
        h.expect("mining_tutor.bronze_pick_hint", "mining_tutor.iron_steel_pick_hint")
        h.menu(*picks)
        h.pick(2)
        h.expect("mining_tutor.mithril_picks_hint")
        h.menu(*picks)
        h.pick(3)
        h.menu(*top)
    }

    @Test
    fun `the Fishing tutor teaches the basics and hands over a net only when there is none and there's room`() {
        val top = arrayOf("fishing_tutor.basics", "fishing_tutor.fish", "fishing_tutor.where", "fishing_tutor.goodbye")
        fishing("fishing_tutor.basics")
        h.pick(1)
        h.expect(
            "fishing_tutor.basics", "fishing_tutor.lost_net", "fishing_tutor.spot_hint", "fishing_tutor.how_to",
            "fishing_tutor.is_that_it", "fishing_tutor.progress", "fishing_tutor.full_inventory",
        )
        h.menu(*top)
        assertEquals(listOf(SwampTutors.SMALL_FISHING_NET), given)
        h.pick(1)
        basicsAfterNet("fishing_tutor.have_net")

        // Banked counts as having one.
        h.player.inventory.clear()
        h.player.bank[0] = InvObj(SwampTutors.SMALL_FISHING_NET, 1)
        fishing("fishing_tutor.basics")
        h.pick(1)
        basicsAfterNet("fishing_tutor.have_net")
        h.player.bank.clear()

        // None and no room: nothing about the net.
        for (slot in 0 until 28) h.player.inventory[slot] = InvObj(1931, 1)
        fishing("fishing_tutor.basics")
        h.pick(1)
        basicsAfterNet()
        assertEquals(1, given.size)
    }

    /** The basics from the net line (if any) on, then Goodbye. */
    private fun basicsAfterNet(vararg netLine: String) {
        h.expect(
            "fishing_tutor.basics", *netLine, "fishing_tutor.spot_hint", "fishing_tutor.how_to", "fishing_tutor.is_that_it",
            "fishing_tutor.progress", "fishing_tutor.full_inventory",
        )
        h.menu("fishing_tutor.basics", "fishing_tutor.fish", "fishing_tutor.where", "fishing_tutor.goodbye")
        h.pick(4)
        h.expect("fishing_tutor.goodbye")
        h.ends()
    }

    @Test
    fun `the Fishing tutor's tips offer the map and his advice names no quest, as every one is done`() {
        h.player.inventory[0] = InvObj(SwampTutors.SMALL_FISHING_NET, 1)
        h.player.skills.setLevel(Skill.FISHING, 30)
        fishing("fishing_tutor.tips")
        h.pick(1)
        h.expect(
            "fishing_tutor.tips", "fishing_tutor.have_net", "fishing_tutor.choose", "fishing_tutor.hang_on",
            "fishing_tutor.trawler", "fishing_tutor.murphy", "fishing_tutor.show_map",
        )
        h.menu("fishing_tutor.yes_please", "fishing_tutor.no_thanks")
        h.pick(1)
        h.menu("fishing_tutor.tips", "fishing_tutor.fish", "fishing_tutor.where", "fishing_tutor.goodbye")
        h.pick(4)
        h.expect("fishing_tutor.goodbye")
        h.ends()

        h.player.skills.setLevel(Skill.FISHING, 70)
        fishing("fishing_tutor.advanced")
        h.pick(1)
        h.expect(
            "fishing_tutor.advanced", "fishing_tutor.have_net", "fishing_tutor.tuna_swordfish", "fishing_tutor.shark",
            "fishing_tutor.potion", "fishing_tutor.now_quests", "fishing_tutor.no_quests",
        )
        h.menu("fishing_tutor.advanced", "fishing_tutor.fish", "fishing_tutor.where", "fishing_tutor.goodbye")
    }

    @Test
    fun `the Fishing tutor's fish menu has the lobster pot from 29 and the harpoon from 39 in one slot`() {
        fun fishMenu(level: Int, first: String, vararg options: String) {
            h.player.skills.setLevel(Skill.FISHING, level)
            fishing(first)
            h.pick(2)
            h.expect("fishing_tutor.fish")
            h.menu(*options)
        }
        fishMenu(1, "fishing_tutor.basics", "fishing_tutor.small_net", "fishing_tutor.big_net", "fishing_tutor.rod_fly", "fishing_tutor.tell_me_about")
        h.pick(2)
        h.expect("fishing_tutor.big_net_hint", "fishing_tutor.big_net_catch", "fishing_tutor.rich_rewards")
        h.menu("fishing_tutor.small_net", "fishing_tutor.big_net", "fishing_tutor.rod_fly", "fishing_tutor.tell_me_about")
        h.pick(4)
        h.menu("fishing_tutor.basics", "fishing_tutor.fish", "fishing_tutor.where", "fishing_tutor.goodbye")
        h.pick(4)
        h.expect("fishing_tutor.goodbye")
        h.ends()

        fishMenu(
            29, "fishing_tutor.tips", "fishing_tutor.small_net", "fishing_tutor.big_net", "fishing_tutor.rod_fly",
            "fishing_tutor.lobster", "fishing_tutor.tell_me_about",
        )
        h.pick(4)
        h.expect("fishing_tutor.lobster_hint", "fishing_tutor.lobster_catch", "fishing_tutor.is_that_all")
        h.menu(
            "fishing_tutor.small_net", "fishing_tutor.big_net", "fishing_tutor.rod_fly", "fishing_tutor.lobster",
            "fishing_tutor.tell_me_about",
        )
        h.pick(5)
        h.menu("fishing_tutor.tips", "fishing_tutor.fish", "fishing_tutor.where", "fishing_tutor.goodbye")
        h.pick(4)
        h.expect("fishing_tutor.goodbye")
        h.ends()

        fishMenu(
            39, "fishing_tutor.advanced", "fishing_tutor.small_net", "fishing_tutor.big_net", "fishing_tutor.rod_fly",
            "fishing_tutor.harpoon", "fishing_tutor.tell_me_about",
        )
        h.pick(4)
        h.expect("fishing_tutor.harpoon_hint", "fishing_tutor.harpoon_catch", "fishing_tutor.and_shark", "fishing_tutor.fishing_guild")
        h.menu(
            "fishing_tutor.small_net", "fishing_tutor.big_net", "fishing_tutor.rod_fly", "fishing_tutor.harpoon",
            "fishing_tutor.tell_me_about",
        )
        assertEquals(
            listOf(
                SwampTutors.BIG_FISHING_NET, SwampTutors.RAW_MACKEREL, SwampTutors.RAW_COD, SwampTutors.LOBSTER_POT,
                SwampTutors.RAW_LOBSTER, SwampTutors.HARPOON_OBJ, SwampTutors.RAW_TUNA, SwampTutors.RAW_SWORDFISH,
                SwampTutors.RAW_SHARK,
            ),
            h.boxObjs,
        )
    }

    @Test
    fun `where to fish goes by level on a members' world`() {
        fun where(level: Int, first: String, vararg keys: String) {
            h.player.skills.setLevel(Skill.FISHING, level)
            fishing(first)
            h.pick(3)
            h.expect("fishing_tutor.where", *keys)
            h.menu(first, "fishing_tutor.fish", "fishing_tutor.where", "fishing_tutor.goodbye")
            h.pick(4)
            h.expect("fishing_tutor.goodbye")
            h.ends()
        }
        where(
            1, "fishing_tutor.basics", "fishing_tutor.young", "fishing_tutor.what_parrot", "fishing_tutor.percy",
            "fishing_tutor.ookay", "fishing_tutor.arrr",
        )
        where(15, "fishing_tutor.basics", "fishing_tutor.herring")
        where(16, "fishing_tutor.basics", "fishing_tutor.mackerel")
        where(27, "fishing_tutor.basics", "fishing_tutor.cod")
        where(28, "fishing_tutor.basics", "fishing_tutor.slimy_eel")
        where(38, "fishing_tutor.tips", "fishing_tutor.cave_eel")
        where(46, "fishing_tutor.advanced", "fishing_tutor.bass")
        where(50, "fishing_tutor.advanced", "fishing_tutor.catherby", "fishing_tutor.manta")
    }
}
