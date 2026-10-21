package osrsps.game.command

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZonePartialEnclosed
import net.rsprot.protocol.game.outgoing.zone.payload.SoundArea
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.EffectIds
import osrsps.game.Player
import osrsps.game.World
import osrsps.map.CollisionMap

class EffectCommandsTest {
    private val world = World(
        IntArray(0),
        CollisionMap(),
        effectIds = EffectIds(seqs = setOf(866), spotanims = setOf(85), synths = setOf(3813, 200), jingles = setOf(2)),
    )
    private val player = Player(1, "test", 3222, 3218, 0)
    private val other = Player(2, "other", 3225, 3220, 0)
    private val replies = mutableListOf<String>()
    private val sent = mutableListOf<OutgoingGameMessage>()

    private fun run(input: String) = world.commands.execute(player, input, replies::add)

    private fun flush(): List<String> {
        val out = mutableListOf<String>()
        player.effects.flush(
            anim = { out += "anim ${it.id} ${it.delay}" },
            spotanim = { slot, spot -> out += "spotanim $slot ${spot.id} ${spot.height} ${spot.delay}" },
            sound = { out += "sound ${it.id} ${it.loops} ${it.delay}" },
            jingle = { out += "jingle $it" },
        )
        return out
    }

    @AfterTest
    fun release() {
        sent.filterIsInstance<UpdateZonePartialEnclosed>().forEach { it.release() }
    }

    @Test
    fun `anim 866 plays the dance`() {
        run("anim 866")
        run("anim -1 5")
        assertEquals(listOf("Animation 866", "Animation stopped"), replies)
        assertEquals(listOf("anim -1 5"), flush())
    }

    @Test
    fun `sound with no id plays the bell`() {
        run("sound")
        run("sound 200 3 10")
        assertEquals(listOf("Sound 3813", "Sound 200"), replies)
        assertEquals(listOf("sound 3813 1 0", "sound 200 3 10"), flush())
    }

    @Test
    fun `spotanim and jingle`() {
        run("spotanim 85 92 0 1")
        run("jingle 2")
        assertEquals(listOf("Spotanim 85 in slot 1", "Jingle 2"), replies)
        assertEquals(listOf("spotanim 1 85 92 0", "jingle 2"), flush())
    }

    @Test
    fun `ids the cache doesn't have are refused`() {
        run("anim 867")
        run("spotanim 86")
        run("sound 1")
        run("areasound 1")
        run("jingle 3")
        assertEquals(
            listOf(
                "No animation 867 in the cache",
                "No spotanim 86 in the cache",
                "No sound 1 in the cache",
                "No sound 1 in the cache",
                "No jingle 3 in the cache",
            ),
            replies,
        )
        assertEquals(emptyList(), flush())
    }

    @Test
    fun `arguments are checked`() {
        run("anim")
        run("anim x")
        run("anim 866 256")
        run("spotanim 85 0 70000")
        run("spotanim 85 0 0 256")
        run("sound 3813 0")
        run("areasound 3813 32")
        run("jingle")
        assertEquals(
            listOf(
                "Usage: ::anim id [delay]",
                "Usage: ::anim id [delay] ('x' is not a number)",
                "Delay must be 0 to 255 client cycles",
                "Delay must be 0 to 65535 client cycles",
                "Slot must be 0 to 255",
                "Loops must be 1 to 255",
                "Radius must be 0 to 31 tiles",
                "Usage: ::jingle id",
            ),
            replies,
        )
        assertEquals(emptyList(), flush())
    }

    @Test
    fun `areasound is heard by another player nearby`() {
        world.zones.update(other) { sent += it }
        world.zones.endCycle()
        run("areasound 3813 10")
        assertEquals(listOf("Sound 3813 from 3222, 3218, heard 10 tiles away"), replies)
        val sound = world.zones.collect(other).single().shared.single() as SoundArea
        assertEquals(listOf(3813, 10, 1), listOf(sound.id, sound.range, sound.loops))
        world.zones.endCycle()
    }
}
