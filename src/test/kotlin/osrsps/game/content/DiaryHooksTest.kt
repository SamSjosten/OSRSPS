package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.Player
import osrsps.game.VarTypes
import osrsps.game.skill.LightSources

/** Which actions where do which diary task: the place checks, the counts and the names. */
class DiaryHooksTest {
    private fun at(x: Int, z: Int, level: Int = 0) = Player(1, "alice", x, z, level, VarTypes.NONE)

    @Test
    fun `an item counts only where its task says`() {
        val mine = at(3300, 3290)
        assertEquals(listOf(DiaryTask.LUMBRIDGE_EASY_11), DiaryHooks.made(DiaryHooks.MADE, mine, DiaryHooks.IRON_ORE, 1))
        assertEquals(listOf(DiaryTask.VARROCK_EASY_3), DiaryHooks.made(DiaryHooks.MADE, at(3285, 3365), DiaryHooks.IRON_ORE, 1), "Varrock's mine, not Al Kharid's")
        assertEquals(listOf(DiaryTask.LUMBRIDGE_MEDIUM_6), DiaryHooks.made(DiaryHooks.MADE, at(3241, 3248), DiaryHooks.RAW_SALMON, 1))
        assertEquals(listOf(DiaryTask.LUMBRIDGE_EASY_10), DiaryHooks.made(DiaryHooks.MADE, at(3211, 3215), DiaryHooks.BREAD, 1))
        assertEquals(emptyList(), DiaryHooks.made(DiaryHooks.MADE, at(3211, 3215, 1), DiaryHooks.BREAD, 1), "upstairs")
        assertEquals(listOf(DiaryTask.LUMBRIDGE_MEDIUM_7), DiaryHooks.made(DiaryHooks.MADE, at(3257, 3267), DiaryHooks.COIF, 1))
        assertEquals(listOf(DiaryTask.LUMBRIDGE_EASY_4), DiaryHooks.made(DiaryHooks.MADE, at(2716, 4836), DiaryHooks.WATER_RUNE, 3))
        assertEquals(listOf(DiaryTask.LUMBRIDGE_MEDIUM_12), DiaryHooks.made(DiaryHooks.MADE, at(2585, 4838), DiaryHooks.LAVA_RUNE, 1))
        assertEquals(emptyList(), DiaryHooks.made(DiaryHooks.MADE, at(2655, 4839), DiaryHooks.LAVA_RUNE, 1), "the earth altar")
    }

    @Test
    fun `the hard and elite items, a lit helmet in the basement, a platebody in the sewers, 140 water runes at once`() {
        assertEquals(listOf(DiaryTask.LUMBRIDGE_HARD_9), DiaryHooks.made(DiaryHooks.MADE, at(3210, 9620), LightSources.MINING_HELMET_LIT, 1))
        assertEquals(emptyList(), DiaryHooks.made(DiaryHooks.MADE, at(3210, 3220), LightSources.MINING_HELMET_LIT, 1))
        assertEquals(listOf(DiaryTask.LUMBRIDGE_ELITE_4), DiaryHooks.made(DiaryHooks.MADE, at(3112, 9689), DiaryHooks.ADAMANT_PLATEBODY, 1))
        assertEquals(
            listOf(DiaryTask.LUMBRIDGE_EASY_4, DiaryTask.LUMBRIDGE_ELITE_5),
            DiaryHooks.made(DiaryHooks.MADE, at(2716, 4836), DiaryHooks.WATER_RUNE, 140),
        )
        assertEquals(listOf(DiaryTask.LUMBRIDGE_EASY_4), DiaryHooks.made(DiaryHooks.MADE, at(2716, 4836), DiaryHooks.WATER_RUNE, 139))
    }

    @Test
    fun `the Varrock items`() {
        assertEquals(listOf(DiaryTask.VARROCK_EASY_3), DiaryHooks.made(DiaryHooks.MADE, at(3285, 3365), DiaryHooks.IRON_ORE, 1))
        assertEquals(listOf(DiaryTask.VARROCK_EASY_4), DiaryHooks.made(DiaryHooks.MADE, at(3302, 3490), DiaryHooks.PLANK, 3))
        assertEquals(listOf(DiaryTask.VARROCK_EASY_10), DiaryHooks.made(DiaryHooks.MADE, at(3085, 3409), DiaryHooks.BOWL, 1))
        assertEquals(emptyList(), DiaryHooks.made(DiaryHooks.MADE, at(3200, 3200), DiaryHooks.BOWL, 1))
        assertEquals(listOf(DiaryTask.VARROCK_EASY_13), DiaryHooks.made(DiaryHooks.MADE, at(3104, 3430), DiaryHooks.RAW_TROUT, 1))
        assertEquals(listOf(DiaryTask.VARROCK_MEDIUM_10), DiaryHooks.made(DiaryHooks.MADE, at(3302, 3490), DiaryHooks.MAHOGANY_PLANK, 20))
        assertEquals(emptyList(), DiaryHooks.made(DiaryHooks.MADE, at(3302, 3490), DiaryHooks.MAHOGANY_PLANK, 19))
    }

    @Test
    fun `the Varrock hard and elite items`() {
        assertEquals(listOf(DiaryTask.VARROCK_HARD_8), DiaryHooks.made(DiaryHooks.MADE, at(3229, 3459), DiaryHooks.YEW_ROOTS, 2))
        assertEquals(emptyList(), DiaryHooks.made(DiaryHooks.MADE, at(3229, 3459), DiaryHooks.YEW_ROOTS, 1), "one root")
        assertEquals(emptyList(), DiaryHooks.made(DiaryHooks.MADE, at(3183, 3358), DiaryHooks.YEW_ROOTS, 3), "another patch")
        assertEquals(listOf(DiaryTask.VARROCK_ELITE_1), DiaryHooks.made(DiaryHooks.MADE, at(3185, 3440), DiaryHooks.SUPER_COMBAT, 1))
        assertEquals(emptyList(), DiaryHooks.made(DiaryHooks.MADE, at(3253, 3420), DiaryHooks.SUPER_COMBAT, 1), "the East Bank")
        assertEquals(listOf(DiaryTask.VARROCK_ELITE_3), DiaryHooks.made(DiaryHooks.MADE, at(3146, 3450, 1), DiaryHooks.SUMMER_PIE, 1))
        assertEquals(emptyList(), DiaryHooks.made(DiaryHooks.MADE, at(3211, 3215), DiaryHooks.SUMMER_PIE, 1), "Lumbridge's kitchen")
        assertEquals(listOf(DiaryTask.VARROCK_ELITE_4), DiaryHooks.made(DiaryHooks.MADE, at(3230, 3430), DiaryHooks.RUNE_DART, 10))
        assertEquals(emptyList(), DiaryHooks.made(DiaryHooks.MADE, at(3230, 3430), DiaryHooks.RUNE_DART, 9))
        assertEquals(emptyList(), DiaryHooks.made(DiaryHooks.MADE, at(3222, 3218), DiaryHooks.RUNE_DART, 10), "Lumbridge")
        assertEquals(
            listOf(DiaryTask.VARROCK_EASY_12, DiaryTask.VARROCK_ELITE_5),
            DiaryHooks.made(DiaryHooks.MADE, at(2655, 4839), DiaryHooks.EARTH_RUNE, 100),
        )
        assertEquals(listOf(DiaryTask.VARROCK_EASY_12), DiaryHooks.made(DiaryHooks.MADE, at(2655, 4839), DiaryHooks.EARTH_RUNE, 99))
        assertEquals(listOf(DiaryTask.VARROCK_HARD_6), DiaryHooks.made(DiaryHooks.BURNED, at(3256, 3485, 3), DiaryHooks.YEW_LOGS, 1))
        assertEquals(emptyList(), DiaryHooks.made(DiaryHooks.BURNED, at(3256, 3485, 1), DiaryHooks.YEW_LOGS, 1), "the church's first floor, not the top")
        assertEquals(emptyList(), DiaryHooks.made(DiaryHooks.BURNED, at(3222, 3430), DiaryHooks.YEW_LOGS, 1), "the square")
        assertEquals(listOf(DiaryTask.VARROCK_HARD_9), DiaryHooks.SMITE_ALTARS.filter { it.first.has(at(3208, 3495, 1)) }.map { it.second })
    }

    @Test
    fun `oak burned in Lumbridge, not Draynor`() {
        assertEquals(listOf(DiaryTask.LUMBRIDGE_EASY_7), DiaryHooks.made(DiaryHooks.BURNED, at(3219, 3206), DiaryHooks.OAK_LOGS, 1))
        assertEquals(emptyList(), DiaryHooks.made(DiaryHooks.BURNED, at(3100, 3250), DiaryHooks.OAK_LOGS, 1))
    }

    @Test
    fun `kills and pickpockets go by the NPC's name and place`() {
        assertEquals(listOf(DiaryTask.LUMBRIDGE_EASY_8), DiaryHooks.named(DiaryHooks.KILLED, at(3123, 9648), "Zombie"))
        assertEquals(emptyList(), DiaryHooks.named(DiaryHooks.KILLED, at(3240, 3600), "Zombie"))
        assertEquals(listOf(DiaryTask.LUMBRIDGE_EASY_2), DiaryHooks.named(DiaryHooks.KILLED, at(3151, 9574), "Cave bug"))
        assertEquals(listOf(DiaryTask.LUMBRIDGE_EASY_6), DiaryHooks.named(DiaryHooks.PICKPOCKETED, at(3215, 3219), "Woman"))
        assertEquals(emptyList(), DiaryHooks.named(DiaryHooks.PICKPOCKETED, at(3215, 3419), "Man"), "Varrock")
    }
}
