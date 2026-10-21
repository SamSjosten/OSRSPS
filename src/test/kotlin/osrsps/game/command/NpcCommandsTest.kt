package osrsps.game.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.World
import osrsps.map.CollisionMap

class NpcCommandsTest {
    private val types = NpcTypes.of(
        NpcType(3105, "Hans", internalName = "hans"),
        NpcType(2792, "Cow calf", size = 2, internalName = "cow2_calf"),
    )
    private val world = World(IntArray(0), CollisionMap(), npcTypes = types)
    private val player = Player(1, "test", 3222, 3218, 0)
    private val replies = mutableListOf<String>()

    private fun run(input: String) = world.commands.execute(player, input, replies::add)

    @Test
    fun `npc spawns at the player's tile by id, gameval name or menu name`() {
        run("npc 3105")
        run("npc cow2_calf 3")
        run("npc cow calf")
        assertEquals(
            listOf(
                "Spawned Hans (3105) as #1 at 3222, 3218",
                "Spawned Cow calf (2792) as #2 at 3222, 3218, wander 3",
                "Spawned Cow calf (2792) as #3 at 3222, 3218",
            ),
            replies,
        )
        assertEquals("::npc by test", world.npcs[1]!!.spawn.source)
    }

    @Test
    fun `npc refuses unknown NPCs and bad ranges`() {
        run("npc")
        run("npc goblin")
        run("npc hans 999")
        assertEquals(
            listOf(
                "Usage: ::npc id|name [wander]",
                "No NPC 'goblin'. Try its id, its name, or its gameval name like cow2_calf",
                "The wander range must be 0 to 64",
            ),
            replies,
        )
        assertEquals(0, world.npcs.count)
    }

    @Test
    fun `npcs lists the NPCs nearby and delnpc removes one`() {
        run("npcs")
        run("npc hans 2")
        replies.clear()
        run("npcs")
        assertEquals(listOf("1 NPC within 15 tiles:", "#1 Hans (3105) at 3222, 3218; spawn 3222, 3218, wander 2"), replies)
        replies.clear()
        run("delnpc 1")
        run("delnpc 1")
        run("npcs 200")
        assertEquals(listOf("Removed Hans #1.", "No NPC #1", "The radius must be 0 to 104"), replies)
        assertNull(world.npcs[1])
    }
}
