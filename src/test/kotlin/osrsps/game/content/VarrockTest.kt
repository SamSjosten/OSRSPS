package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import osrsps.game.GameRandom
import osrsps.game.Interfaces
import osrsps.game.NpcType
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.DialogueText
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptRunner
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocShape

/** Varrock's straight stairs, each way round, on staircases as the 241 cache places them, and its stray dogs. */
class VarrockTest {
    private fun stairs(id: Int, angle: Int, x: Int, z: Int, level: Int) = ZoneLoc(id, LocShape.CENTREPIECE_STRAIGHT, angle, x, z, level)

    private val top = Varrock.STRAIGHT_STAIRS_TOP

    @Test
    fun `stairs climbing north (angle 0) go up past the north end and down before the south end`() {
        // 3188, 3389 with its top a tile north, as in the house south-west of the square.
        assertEquals(Destination(3188, 3392, 1), Varrock.straightStairs(stairs(11796, 0, 3188, 3389, 0), up = true, 3188, 3388))
        assertEquals(Destination(3189, 3388, 0), Varrock.straightStairs(stairs(top, 0, 3188, 3390, 1), up = false, 3189, 3392))
    }

    @Test
    fun `stairs climbing east (angle 1) go up past the east end and down before the west end`() {
        // 3227, 3393 with its top a tile east; Draynor's Morgan's stairs work the same way.
        assertEquals(Destination(3230, 3394, 1), Varrock.straightStairs(stairs(11796, 1, 3227, 3393, 0), up = true, 3226, 3394))
        assertEquals(Destination(3226, 3393, 0), Varrock.straightStairs(stairs(top, 1, 3228, 3393, 1), up = false, 3230, 3393))
        assertEquals(Destination(3102, 3266, 1), Varrock.straightStairs(stairs(11796, 1, 3099, 3266, 0), up = true, 3098, 3266))
    }

    @Test
    fun `stairs climbing south (angle 2) go up past the south end and down before the north end`() {
        // 3230, 3383 by the south gate, its top on the same corner.
        assertEquals(Destination(3230, 3382, 1), Varrock.straightStairs(stairs(11797, 2, 3230, 3383, 0), up = true, 3230, 3386))
        assertEquals(Destination(3231, 3386, 0), Varrock.straightStairs(stairs(top, 2, 3230, 3383, 1), up = false, 3231, 3382))
    }

    @Test
    fun `stairs climbing west (angle 3) go up past the west end and down before the east end`() {
        // 3156, 3435, its top on the same corner.
        assertEquals(Destination(3155, 3435, 1), Varrock.straightStairs(stairs(11797, 3, 3156, 3435, 0), up = true, 3159, 3435))
        assertEquals(Destination(3159, 3436, 0), Varrock.straightStairs(stairs(top, 3, 3156, 3435, 1), up = false, 3155, 3436))
    }

    @Test
    fun `a player beside the stairs keeps to the stairs' two columns`() {
        assertEquals(Destination(3189, 3392, 1), Varrock.straightStairs(stairs(11796, 0, 3188, 3389, 0), up = true, 3195, 3388))
        assertEquals(Destination(3188, 3392, 1), Varrock.straightStairs(stairs(11796, 0, 3188, 3389, 0), up = true, 3180, 3388))
    }

    @Test
    fun `a stray dog whines when shooed and barks when petted, each with its sound`() {
        val world = World(IntArray(0), CollisionMap())
        val scripts = ScriptRunner(Handlers(), Interfaces { _, _ -> }::closeModals) { 0 }
        val player = Player(1, "Zezima", 3200, 3399, 0)
        for ((type, internal) in Varrock.STRAY_DOGS.zip(listOf("dog_stray", "dog_stray2"))) {
            val dog = Npcs(CollisionMap(), GameRandom(1)).add(NpcSpawn(NpcType(type, "Stray dog", internalName = internal), 3201, 3399, source = "test"))!!
            for ((option, line, sound) in listOf(
                Triple(Varrock.SHOO_AWAY, StrayDogLines.WHINE, Varrock.SHOO_SOUND),
                Triple(Varrock.PET, StrayDogLines.WOOF, Varrock.PET_SOUND),
            )) {
                assertNull(dog.effects.say)
                val op = Op(OpTarget.NPC, type, option, npc = dog)
                val handler = world.handlers.find(op)!!
                scripts.start(player) { handler(op) }
                assertEquals(DialogueText.NONE[line], dog.effects.say)
                assertEquals(listOf(sound), player.effects.pendingSounds.map { it.id })
                dog.effects.flush({}, {}, {})
                player.effects.flush({}, { _, _ -> }, {}, {})
            }
        }
    }
}
