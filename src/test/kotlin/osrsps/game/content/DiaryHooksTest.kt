package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.Player
import osrsps.game.VarTypes

/** Which actions where do which diary task: the place checks, the counts and the names. */
class DiaryHooksTest {
    private fun at(x: Int, z: Int, level: Int = 0) = Player(1, "alice", x, z, level, VarTypes.NONE)

    @Test
    fun `an item counts only where its task says`() {
        val mine = at(3300, 3290)
        assertEquals(listOf(DiaryTask.LUMBRIDGE_EASY_11), DiaryHooks.made(DiaryHooks.MADE, mine, DiaryHooks.IRON_ORE, 1))
        assertEquals(emptyList(), DiaryHooks.made(DiaryHooks.MADE, at(3285, 3365), DiaryHooks.IRON_ORE, 1), "Varrock's mine")
        assertEquals(listOf(DiaryTask.LUMBRIDGE_MEDIUM_6), DiaryHooks.made(DiaryHooks.MADE, at(3241, 3248), DiaryHooks.RAW_SALMON, 1))
        assertEquals(listOf(DiaryTask.LUMBRIDGE_EASY_10), DiaryHooks.made(DiaryHooks.MADE, at(3211, 3215), DiaryHooks.BREAD, 1))
        assertEquals(emptyList(), DiaryHooks.made(DiaryHooks.MADE, at(3211, 3215, 1), DiaryHooks.BREAD, 1), "upstairs")
        assertEquals(listOf(DiaryTask.LUMBRIDGE_MEDIUM_7), DiaryHooks.made(DiaryHooks.MADE, at(3257, 3267), DiaryHooks.COIF, 1))
        assertEquals(listOf(DiaryTask.LUMBRIDGE_EASY_4), DiaryHooks.made(DiaryHooks.MADE, at(2716, 4836), DiaryHooks.WATER_RUNE, 3))
        assertEquals(listOf(DiaryTask.LUMBRIDGE_MEDIUM_12), DiaryHooks.made(DiaryHooks.MADE, at(2585, 4838), DiaryHooks.LAVA_RUNE, 1))
        assertEquals(emptyList(), DiaryHooks.made(DiaryHooks.MADE, at(2655, 4839), DiaryHooks.LAVA_RUNE, 1), "the earth altar")
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
