package osrsps.game.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.map.CollisionMap

class NpcScriptsTest {
    private val duck = NpcType(1838, "Duck")
    private val man = NpcType(3106, "Man")
    private var tick = 0L
    private val npcs = Npcs(CollisionMap().apply { markLoaded(3200 shr 6, 3200 shr 6) }, GameRandom(1))
    private val scripts = NpcScripts(npcs) { tick }

    private fun add(type: NpcType = duck, x: Int = 3230) = npcs.add(NpcSpawn(type, x, 3230, source = "test"))!!

    /** Runs [ticks] ticks of the scripts. */
    private fun run(ticks: Int) = repeat(ticks) {
        tick++
        scripts.process()
    }

    @Test
    fun `a timer fires its first ticks after the NPC appears, then as often as it says, only for its types`() {
        val fired = ArrayList<Pair<Int, Long>>()
        val first = add()
        add(man, x = 3232)
        scripts.onTimer(listOf(duck.id), 3) { npc ->
            fired += npc.index to tick
            5
        }
        run(14)
        assertEquals(listOf(first.index to 3L, first.index to 8L, first.index to 13L), fired)
    }

    @Test
    fun `a queued action runs its delay ticks after the tick it was queued on, from whichever NPC queued it`() {
        val ran = ArrayList<Pair<Int, Long>>()
        val a = add()
        val b = add(x = 3232)
        // a queues for b, which is processed after a in the same tick; b's action still waits its 2 ticks.
        scripts.onTimer(listOf(duck.id), 1) { npc ->
            if (npc === a) scripts.queue(b) { ran += it.index to tick }
            100
        }
        run(4)
        assertEquals(listOf(b.index to 3L), ran)
    }

    @Test
    fun `nothing runs for an NPC that is dead or gone`() {
        val ran = ArrayList<Long>()
        val a = add()
        val b = add(x = 3232)
        scripts.onTick(listOf(duck.id)) { if (it === a) ran += tick }
        scripts.queue(b, 1) { ran += -tick }
        a.dead = true
        npcs.remove(b)
        run(3)
        assertEquals(emptyList(), ran)
    }

    @Test
    fun `death actions run for the NPC's type`() {
        val died = ArrayList<Npc>()
        scripts.onDeath(listOf(duck.id)) { died += it }
        val a = add()
        val b = add(man, x = 3232)
        scripts.died(b)
        scripts.died(a)
        assertEquals(listOf(a), died)
    }

    @Test
    fun `a type gets one timer, and timers and queues are at least a tick`() {
        scripts.onTimer(listOf(duck.id), 1) { 1 }
        assertFailsWith<IllegalArgumentException> { scripts.onTimer(listOf(duck.id), 1) { 1 } }
        assertFailsWith<IllegalArgumentException> { scripts.onTimer(listOf(man.id), 0) { 1 } }
        assertFailsWith<IllegalArgumentException> { scripts.queue(add(), 0) {} }
    }

    private fun NpcScripts.queue(npc: Npc, action: (Npc) -> Unit) = queue(npc, 2, action)
}
