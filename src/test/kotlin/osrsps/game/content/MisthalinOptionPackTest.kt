package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.Locs
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.game.zone.Zones
import osrsps.map.CollisionMap
import osrsps.map.LocShape

class MisthalinOptionPackTest {
    private var tick = 0L
    private val bush = MisthalinOptionPack.BUSHES.first { it.stages == MisthalinOptionPack.CADAVA }
    private val full = ZoneLoc(bush.stages[0], LocShape.CENTREPIECE_STRAIGHT, 0, 3220, 3220, 0)
    private val static = StaticLocs().apply { add(full) }
    private val locs = Locs(
        LocTypes(bush.stages.associateWith { LocType(it, "Cadava bush") }), static,
        CollisionMap().apply { markLoaded(3220 shr 6, 3220 shr 6) }, Zones(),
    ) { tick }

    private fun now() = locs.at(3220, 3220, 0).single()

    /** Runs the world's timers on each tick up to [until], as the world does after its scripts. */
    private fun runTo(until: Long) {
        while (tick < until) {
            tick++
            locs.process()
        }
    }

    @Test
    fun `the berry picked first keeps its deadline when the last one is picked`() {
        tick = 1000
        MisthalinOptionPack.takeBerry(locs, now(), bush, tick)
        tick = 1199
        MisthalinOptionPack.takeBerry(locs, now(), bush, tick)
        assertEquals(bush.stages[2], now().id)
        runTo(1200)
        assertEquals(bush.stages[1], now().id, "the first berry is back on time")
        runTo(1400)
        assertEquals(bush.stages[0], now().id, "the second berry is back 200 ticks later")
    }

    @Test
    fun `a pick on the tick the first berry is due leaves one, and the full bush is back 200 ticks later`() {
        tick = 1000
        MisthalinOptionPack.takeBerry(locs, now(), bush, tick)
        tick = 1200
        // The pick's script runs before the world's timers on the tick the first berry is due.
        MisthalinOptionPack.takeBerry(locs, now(), bush, tick)
        locs.process()
        assertEquals(bush.stages[1], now().id)
        runTo(1399)
        assertEquals(bush.stages[1], now().id)
        runTo(1400)
        assertEquals(bush.stages[0], now().id, "the bush grows back to full")
    }

    @Test
    fun `a bush that starts with one berry gives no extra on the regrowth tick`() {
        val one = full.copy(id = bush.stages[1], x = 3222)
        static.add(one)
        fun at() = locs.at(3222, 3220, 0).single()
        tick = 1000
        MisthalinOptionPack.takeBerry(locs, at(), bush, tick)
        assertEquals(bush.stages[2], at().id)
        runTo(1200)
        assertEquals(bush.stages[1], at().id)
        // Picked on the tick its timer would put it back as the cache has it.
        val due = locs.revertDue(at())
        if (due != null) tick = due
        MisthalinOptionPack.takeBerry(locs, at(), bush, tick)
        locs.process()
        assertEquals(bush.stages[2], at().id, "picked bare, not left with a berry")
        runTo(tick + MisthalinOptionPack.BERRY_TICKS)
        assertEquals(bush.stages[1], at().id, "back to its one berry")
    }

    @Test
    fun `the ticks until the first berry is back`() {
        assertEquals(1, MisthalinOptionPack.ticksUntilFirstBack(due = 1200, now = 1199))
        assertNull(MisthalinOptionPack.ticksUntilFirstBack(due = 1200, now = 1200))
        assertNull(MisthalinOptionPack.ticksUntilFirstBack(due = 1200, now = 1201))
        assertEquals(MisthalinOptionPack.BERRY_TICKS, MisthalinOptionPack.ticksUntilFirstBack(due = null, now = 1200))
    }
}
