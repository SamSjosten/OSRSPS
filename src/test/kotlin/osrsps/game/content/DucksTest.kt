package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.Player
import osrsps.game.dialogue.DialogueText
import osrsps.game.npc.MoveRestrict
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcScripts
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.Tile

/** Rest of Lumbridge step 7b: the ducks, with no dialogue file, so what they say shows its key. */
class DucksTest {
    private val drake = NpcType(Ducks.DRAKE, "Duck")
    private val female = NpcType(Ducks.FEMALE_DUCK, "Duck")
    private val ducklings = NpcType(Ducks.DUCKLINGS_GROUP, "Ducklings")
    private val landDuck = NpcType(2003, "Duck")

    /** A pond over x and z 3220 to 3240, with land around it. */
    private val map = CollisionMap().apply {
        markLoaded(3200 shr 6, 3200 shr 6)
        for (x in 3220..3240) for (z in 3220..3240) add(x, z, 0, CollisionFlag.FLOOR)
    }
    private var tick = 0L
    private val npcs = Npcs(map, GameRandom(3))
    private val scripts = NpcScripts(npcs) { tick }
    private val players = ArrayList<Player>()
    private val sounds = ArrayList<Pair<Int, Long>>()

    /** What each NPC said, by index, with the tick. */
    private val said = ArrayList<Triple<Int, String, Long>>()

    init {
        Ducks(npcs, scripts, map, GameRandom(5), { tick }, { DialogueText.NONE }, { players }) { npc, id ->
            assertEquals(Ducks.SYNTH_QUACK, id)
            sounds += npc.index to tick
        }.register()
    }

    private fun swimmer(type: NpcType, x: Int, z: Int, wander: Int = 0) =
        npcs.add(NpcSpawn(type, x, z, wander = wander, restrict = MoveRestrict.BLOCKED, source = "test"))!!

    /** One tick as the world runs it: the scripts, then movement, then what was said goes out. */
    private fun run(ticks: Int) = repeat(ticks) {
        tick++
        scripts.process()
        npcs.process()
        for (npc in npcs.all) {
            npc.effects.flush(anim = {}, hit = {}, headBar = {}, say = { said += Triple(npc.index, it, tick) })
        }
    }

    private fun saidBy(npc: Npc) = said.filter { it.first == npc.index }.map { it.second to it.third }

    @Test
    fun `a duck quacks the tick after its first timer, 25 ticks in, and then every 50 to 100 ticks`() {
        val duck = swimmer(drake, 3230, 3230)
        run(1_000)
        val ticks = saidBy(duck).map { (text, at) -> assertEquals("[duck.quack]", text).let { at } }
        assertEquals(26L, ticks.first())
        assertTrue(ticks.zipWithNext { a, b -> b - a }.all { it in 50..100 }, "gaps ${ticks.zipWithNext { a, b -> b - a }}")
        assertTrue(ticks.size >= 10)
        assertEquals(ticks, sounds.filter { it.first == duck.index }.map { it.second })
    }

    @Test
    fun `ducklings close to a female duck follow her, and answer her call`() {
        val duck = swimmer(female, 3230, 3230)
        val near = swimmer(ducklings, 3232, 3231)
        val far = swimmer(ducklings, 3236, 3236)
        run(11)
        assertSame(duck, near.leader)
        assertNull(far.leader)
        duck.destination = Tile(3226, 3228)
        run(12)
        assertEquals(1, maxOf(kotlin.math.abs(near.x - duck.x), kotlin.math.abs(near.z - duck.z)), "beside her")
        run(40)
        // 50 ticks in, each duckling's timer: she calls if she is within 10 tiles and in sight, and it answers 2 later.
        assertTrue("[duck.call]" to 50L in saidBy(duck), "${saidBy(duck)}")
        assertEquals(listOf("[duck.eep]" to 52L), saidBy(near))
        assertEquals(listOf("[duck.eep]" to 52L), saidBy(far))
    }

    @Test
    fun `when a duck is killed, the ducklings beside her cry and swim off, and a drake beside her quacks`() {
        val duck = swimmer(female, 3230, 3230)
        val duckling = swimmer(ducklings, 3231, 3230)
        val other = swimmer(drake, 3229, 3231)
        val away = swimmer(drake, 3235, 3235)
        run(11)
        assertSame(duck, duckling.leader)
        duck.dead = true
        scripts.died(duck)
        val killed = tick
        run(5)
        assertNull(duckling.leader)
        assertEquals(listOf("[duck.cheep]" to killed + 2, "[duck.eep]" to killed + 4), saidBy(duckling))
        assertEquals(listOf("[duck.quack]" to killed + 2), saidBy(other))
        assertEquals(emptyList(), saidBy(away))
        // It swam off, and only over the water.
        assertTrue(duckling.x != 3231 || duckling.z != 3230, "still where it was")
        assertTrue(duckling.x in 3220..3240 && duckling.z in 3220..3240)
    }

    @Test
    fun `swimming ducks and the ducklings with them keep to the water`() {
        val duck = swimmer(female, 3221, 3221, wander = 35)
        val duckling = swimmer(ducklings, 3222, 3222)
        val drakeNpc = swimmer(drake, 3239, 3239, wander = 35)
        val tiles = HashSet<Tile>()
        repeat(2_000) {
            run(1)
            for (npc in listOf(duck, duckling, drakeNpc)) tiles += Tile(npc.x, npc.z)
        }
        assertTrue(tiles.all { it.x in 3220..3240 && it.z in 3220..3240 }, "off the water: ${tiles.filter { it.x !in 3220..3240 || it.z !in 3220..3240 }}")
        assertTrue(tiles.size > 100, "they hardly moved")
    }

    @Test
    fun `a duck on land runs four tiles straight away from a player who comes within two`() {
        val duck = npcs.add(NpcSpawn(landDuck, 3210, 3210, source = "test"))!!
        players += Player(1, "zezima", 3213, 3210, 0)
        run(1)
        assertNull(duck.destination)
        players[0].x = 3212
        players[0].z = 3211
        run(1)
        // Its first step away is taken the same tick.
        assertEquals(Tile(3209, 3209), Tile(duck.x, duck.z))
        assertEquals(Tile(3206, 3206), assertNotNull(duck.destination))
    }
}
