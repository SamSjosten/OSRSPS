package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill

/** Eating: heals, the eat delay, combo food, the attack delay, bites, anglerfish, and the food file. */
class FoodsTest {
    private fun food(id: Int, internal: String, name: String) = ObjType(id, name, iops = listOf("Eat", null, null, null, "Drop"), internalName = internal)

    private val shark = food(385, "shark", "Shark")
    private val karambwan = food(3144, "tbwt_cooked_karambwan", "Cooked karambwan")
    private val meatPie = food(2327, "meat_pie", "Meat pie")
    private val halfMeatPie = food(2331, "half_a_meat_pie", "Half a meat pie")
    private val pieDish = ObjType(2313, "Pie dish", internalName = "piedish")
    private val anglerfish = food(13441, "anglerfish", "Anglerfish")
    private val objTypes = ObjTypes.of(shark, karambwan, meatPie, halfMeatPie, pieDish, anglerfish)
    private val messages = mutableListOf<String>()
    private val foods = Foods(Foods.load(objTypes), message = { _, text -> messages += text })

    private val alice = Player(1, "alice", 3222, 3218, 0).apply {
        skills.setLevel(Skill.HITPOINTS, 99)
        hitpoints = 50
    }

    private fun give(vararg items: ObjType) {
        for ((slot, item) in items.withIndex()) alice.inventory[slot] = InvObj(item.id, 1)
    }

    private fun eat(slot: Int, tick: Long) = assertTrue(foods.eat(alice, slot, alice.inventory[slot]!!.id, tick))

    @Test
    fun `the file is read against the cache, skipping foods it doesn't have`() {
        // The test cache has 6 of the file's objs, so 5 foods.
        assertEquals(setOf(shark.id, karambwan.id, meatPie.id, halfMeatPie.id, anglerfish.id), foods.all.keys)
        assertEquals(Food(shark.id, "shark", 20), foods.all[shark.id])
        val bite = foods.all.getValue(halfMeatPie.id)
        assertEquals(pieDish.id, bite.next)
        // The second bite shows the whole pie's name.
        assertEquals("meat pie", bite.name)
        assertEquals(FoodLines.EAT_REST, bite.message)
        assertEquals(2, bite.eatDelay)
        assertTrue(foods.all.getValue(karambwan.id).combo)
    }

    @Test
    fun `a bite heals up to the Hitpoints level, takes the food and starts the eat delay`() {
        give(shark, shark)
        eat(0, 10)
        assertEquals(70, alice.hitpoints)
        assertEquals(listOf("[food.eat food=shark]", "[food.heals]"), messages)
        assertNull(alice.inventory[0])
        assertEquals(Foods.EAT_ANIM, alice.effects.anim?.id)
        // The second shark waits out the 3 ticks.
        eat(1, 12)
        assertEquals(70, alice.hitpoints)
        assertEquals(shark.id, alice.inventory[1]?.id)
        eat(1, 13)
        assertEquals(90, alice.hitpoints)
        give(shark)
        eat(0, 16)
        assertEquals(99, alice.hitpoints)
    }

    @Test
    fun `karambwan after other food in the same tick heals too, but not before it`() {
        give(shark, karambwan, shark, karambwan)
        eat(0, 10)
        eat(1, 10)
        assertEquals(88, alice.hitpoints)
        // Neither kind again for 3 ticks.
        eat(2, 12)
        eat(3, 12)
        assertEquals(88, alice.hitpoints)
        alice.hitpoints = 50
        eat(3, 13)
        eat(2, 13)
        assertEquals(68, alice.hitpoints, "the karambwan holds the shark back")
    }

    @Test
    fun `eating adds to an attack still waiting, 3 ticks for a shark and 2 for a karambwan, and nothing to a ready one`() {
        give(shark, karambwan, shark)
        alice.nextAttackTick = 12
        eat(0, 10)
        eat(1, 10)
        assertEquals(17, alice.nextAttackTick)
        alice.nextAttackTick = 13
        eat(2, 13)
        assertEquals(13, alice.nextAttackTick)
    }

    @Test
    fun `a pie is eaten in two bites a tick or two apart, leaving the dish`() {
        give(meatPie)
        eat(0, 10)
        assertEquals(56, alice.hitpoints)
        assertEquals(halfMeatPie.id, alice.inventory[0]?.id)
        eat(0, 11)
        assertEquals(62, alice.hitpoints)
        assertEquals(pieDish.id, alice.inventory[0]?.id)
    }

    @Test
    fun `anglerfish heals by the Hitpoints level and past it`() {
        assertEquals(listOf(3, 4, 6, 11, 15, 17, 22), listOf(10, 24, 25, 50, 75, 92, 99).map(Foods::anglerfishHeals))
        alice.hitpoints = 99
        give(anglerfish, anglerfish)
        eat(0, 10)
        assertEquals(121, alice.hitpoints)
        eat(1, 13)
        assertEquals(121, alice.hitpoints, "no further than 22 past the level")
    }

    @Test
    fun `the messages are lines from the dialogue file`() {
        val text = osrsps.game.dialogue.DialogueText(mapOf("food.eat" to "You eat the {food}.", "food.heals" to "It heals some health."))
        val worded = Foods(foods.all, words = { text }, message = { _, line -> messages += line })
        give(shark, meatPie)
        assertTrue(worded.eat(alice, 0, shark.id, 10))
        assertEquals(listOf("You eat the shark.", "It heals some health."), messages)
        // A line without words shows its key; at full hitpoints there is no heal line.
        alice.hitpoints = 99
        messages.clear()
        assertTrue(worded.eat(alice, 1, meatPie.id, 13))
        assertEquals(listOf("[food.eat_half food=meat pie]"), messages)
    }

    @Test
    fun `a food the file names wrongly is refused`() {
        fun parse(entry: String) = Foods.parse("""{ "foods": [ $entry ] }""", objTypes)
        assertFailsWith<IllegalArgumentException> { parse("""{"food": "shark", "heals": 20, "heal": 20}""") }
        assertFailsWith<IllegalArgumentException> { parse("""{"food": "piedish", "heals": 2}""") }
        assertFailsWith<IllegalArgumentException> { parse("""{"food": "shark", "heals": 20, "message": "gulp"}""") }
        assertEquals(emptyMap(), parse("""{"food": "manta_ray", "heals": 22}"""))
    }
}
