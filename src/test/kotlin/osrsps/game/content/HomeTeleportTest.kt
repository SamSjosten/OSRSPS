package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.Spellbook
import osrsps.game.combat.Spells
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.QueueType
import osrsps.game.script.ScriptRunner
import osrsps.map.CollisionMap
import osrsps.map.Tile

/** Lumbridge Home Teleport: its checks, its 24 ticks of stages, what stops it and its cooldown. */
class HomeTeleportTest {
    private var tick = 0L
    private var minutes = 29_000_000L
    private var attackedRecently = false
    private var wilderness = 0
    private val handlers = Handlers()
    private val scripts = ScriptRunner(handlers, {}) { tick }
    private val landed = mutableListOf<Pair<Long, Destination>>()
    private val said = mutableListOf<String>()
    private val home = HomeTeleport(
        currentTick = { tick },
        nowMinutes = { minutes },
        inCombat = { attackedRecently },
        teleport = { player, to ->
            player.x = to.x
            player.z = to.z
            player.level = to.level
            landed += tick to to
        },
        message = { _, line, values -> said += (listOf(line.key) + values.map { "${it.first}=${it.second}" }).joinToString(" ") },
        wildernessLevel = { wilderness },
    ).also { it.register(handlers) }
    private val player = Player(1, "Zezima", 3100, 3500, 0)

    /** The anims and spotanims each tick set, by tick. */
    private val anims = mutableMapOf<Long, Int>()
    private val spots = mutableMapOf<Long, Int>()

    /** A click on Cast in this tick's input phase, then the rest of the tick's script phase, as the world runs them. */
    private fun click() {
        assertTrue(scripts.trigger(player, Op.button(Spells.SPELLBOOK, HomeTeleport.COMPONENT, HomeTeleport.CAST)))
        scripts.process(player)
        record()
    }

    private fun record() {
        player.effects.flush(
            anim = { anims[tick] = it.id },
            spotanim = { _, spot -> spots[tick] = spot.id },
            sound = {},
            jingle = {},
        )
    }

    /** One tick: the script phase, then what the player was given goes out. */
    private fun run(ticks: Int) = repeat(ticks) {
        tick++
        scripts.process(player)
        record()
    }

    @Test
    fun `a cast plays the five stages and lands in Lumbridge 24 ticks after the click`() {
        tick = 100
        player.waypoints += Tile(3101, 3500)
        click()
        assertTrue(player.waypoints.isEmpty(), "casting stops the walk")
        run(23)
        assertTrue(landed.isEmpty())
        assertEquals(mapOf(100L to 4847, 107L to 4850, 113L to 4853, 116L to 4855, 119L to 4857), anims)
        assertEquals(mapOf(100L to 800, 107L to 801, 113L to 802, 116L to 803, 119L to 804), spots)
        run(1)
        assertEquals(listOf(124L to HomeTeleport.LUMBRIDGE), landed)
        assertEquals(Triple(3222, 3218, 0), Triple(player.x, player.z, player.level))
        assertEquals(-1, anims[124L])
        assertEquals(minutes.toInt(), player.vars[HomeTeleport.VARP_LAST_CAST])
        assertFalse(home.casting(player))
        run(30)
        assertEquals(1, landed.size)
    }

    @Test
    fun `the cooldown is 30 minutes from landing, and says the minutes left`() {
        click()
        run(24)
        assertEquals(30, home.minutesLeft(player))
        minutes += 12
        click()
        assertEquals(listOf("home_teleport.cooldown minutes=18"), said)
        assertFalse(home.casting(player))
        minutes += 17
        assertEquals(1, home.minutesLeft(player))
        minutes += 1
        assertEquals(0, home.minutesLeft(player))
        click()
        assertTrue(home.casting(player))
        run(24)
        assertEquals(2, landed.size)
    }

    @Test
    fun `walking away or another click that interrupts stops the cast, and costs no cooldown`() {
        click()
        run(10)
        scripts.interrupt(player)
        run(30)
        assertTrue(landed.isEmpty())
        assertEquals(0, player.vars[HomeTeleport.VARP_LAST_CAST])
        assertEquals(0, home.minutesLeft(player))
        assertEquals(-1, anims[11L], "the interruption stops the stage that was playing")
        assertEquals(-1, spots[11L], "and clears its graphic, which a stage of ours can hold for good")
    }

    @Test
    fun `being attacked stops the cast at the next tick`() {
        tick = 50
        click()
        run(5)
        player.attackedTick = tick
        run(1)
        assertFalse(home.casting(player))
        assertEquals(-1, anims[56L], "the hit stops the stage that was playing")
        assertEquals(-1, spots[56L])
        run(30)
        assertTrue(landed.isEmpty())
        assertEquals(0, player.vars[HomeTeleport.VARP_LAST_CAST])
    }

    @Test
    fun `it can't be cast in combat`() {
        attackedRecently = true
        click()
        assertEquals(listOf("home_teleport.in_combat"), said)
        assertFalse(home.casting(player))
        assertTrue(anims.isEmpty())
    }

    @Test
    fun `it can't be cast above level 20 Wilderness`() {
        wilderness = 21
        click()
        assertEquals(listOf("home_teleport.wilderness", "home_teleport.wilderness_2"), said, "live sends two lines")
        assertFalse(home.casting(player))
        wilderness = 20
        click()
        assertTrue(home.casting(player))
    }

    @Test
    fun `by default the Wilderness level is read from where the player stands`() {
        val handlers = Handlers()
        val scripts = ScriptRunner(handlers, {}) { tick }
        val home = HomeTeleport(
            currentTick = { tick }, nowMinutes = { minutes }, inCombat = { false }, teleport = { _, _ -> },
            message = { _, line, _ -> said += line.key },
        ).also { it.register(handlers) }
        player.x = 3100
        player.z = 3700
        assertTrue(scripts.trigger(player, Op.button(Spells.SPELLBOOK, HomeTeleport.COMPONENT, HomeTeleport.CAST)))
        scripts.process(player)
        assertEquals(listOf("home_teleport.wilderness", "home_teleport.wilderness_2"), said)
        assertFalse(home.casting(player))
        player.z = 3679
        assertTrue(scripts.trigger(player, Op.button(Spells.SPELLBOOK, HomeTeleport.COMPONENT, HomeTeleport.CAST)))
        scripts.process(player)
        assertTrue(home.casting(player), "level 20")
    }

    @Test
    fun `casting stops a queued activity`() {
        var made = 0
        scripts.queue(player, "making", QueueType.WEAK, 1) { made++ }
        click()
        run(24)
        assertEquals(0, made)
        assertEquals(1, landed.size)
    }

    @Test
    fun `only the standard book casts it`() {
        Spellbook.set(player, Spellbook.ANCIENT)
        click()
        assertFalse(home.casting(player))
        run(30)
        assertTrue(landed.isEmpty())
    }

    @Test
    fun `a second click while casting doesn't start it again`() {
        click()
        run(3)
        click()
        run(21)
        assertEquals(1, landed.size)
        assertEquals(1, anims.values.count { it == 4847 })
    }

    @Test
    fun `the target seam can choose another tile, length and stages, skip the cooldown, or cancel the cast`() {
        val elsewhere = Destination(3093, 3493, 0)
        home.target = { HomeTeleport.Target(elsewhere, cooldown = false) }
        click()
        run(24)
        assertEquals(listOf(0L + 24 to elsewhere), landed)
        assertEquals(0, player.vars[HomeTeleport.VARP_LAST_CAST])
        // No cooldown recorded, so the next cast starts at once; a stale timer doesn't stop a target without one.
        player.vars[HomeTeleport.VARP_LAST_CAST] = minutes.toInt()
        click()
        assertTrue(home.casting(player))
        assertTrue(said.isEmpty())
        scripts.interrupt(player)
        home.target = { HomeTeleport.Target(elsewhere, Lodestones.CAST_TICKS, Lodestones.STAGES, cooldown = false, landing = Lodestones.LANDING) }
        tick = 100
        click()
        run(Lodestones.CAST_TICKS - 1)
        assertEquals(1, landed.size)
        run(1)
        assertEquals(100L + Lodestones.CAST_TICKS to elsewhere, landed.last(), "the target's own length")
        assertEquals(CustomAnims.LODESTONE_RISE, anims[100L + Lodestones.CAST_TICKS - Lodestones.RISE_TICKS], "the target's own stages")
        assertEquals(CustomAnims.LODESTONE_LAND, anims[100L + Lodestones.CAST_TICKS], "the target's landing plays on the arrival tile in place of stopping the cast's animation")
        assertEquals(CustomAnims.LODESTONE_LAND_FLASH, spots[100L + Lodestones.CAST_TICKS])
        home.target = { null }
        click()
        assertFalse(home.casting(player))
    }

    @Test
    fun `combat and the Wilderness are refused before the target is asked`() {
        var asked = 0
        home.target = { asked++; HomeTeleport.Target(HomeTeleport.LUMBRIDGE) }
        attackedRecently = true
        click()
        assertEquals(0, asked)
        assertEquals(listOf("home_teleport.in_combat"), said)
        attackedRecently = false
        wilderness = 21
        click()
        assertEquals(0, asked)
        wilderness = 0
        click()
        assertEquals(1, asked)
        assertTrue(home.casting(player))
    }

    @Test
    fun `the world registers the Cast button`() {
        val world = World(IntArray(0), CollisionMap())
        assertTrue(world.handlers.find(Op.button(Spells.SPELLBOOK, HomeTeleport.COMPONENT, HomeTeleport.CAST)) != null)
    }
}
