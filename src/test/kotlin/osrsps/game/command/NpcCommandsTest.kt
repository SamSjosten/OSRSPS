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
    fun `hidenpc hides an NPC and shows it again, by index or every one the player put in`() {
        run("npc hans")
        run("npc cow calf")
        replies.clear()
        run("hidenpc 1")
        run("npcs")
        run("hidenpc 1")
        run("hidenpc mine")
        run("hidenpc 2")
        run("hidenpc mine")
        run("hidenpc mine")
        run("hidenpc 9")
        run("hidenpc")
        assertEquals(
            listOf(
                "Hid Hans #1.",
                "2 NPCs within 15 tiles:",
                "#1 Hans (3105) at 3222, 3218; spawn 3222, 3218, wander 0; hidden",
                "#2 Cow calf (2792) at 3222, 3218; spawn 3222, 3218, wander 0",
                "Hans #1 is back at 3222, 3218.",
                "Hid 2 NPCs you put in.",
                "Cow calf #2 is back at 3222, 3218.",
                // One of the two shows, so both are hidden: only the calf changes.
                "Hid 1 NPC you put in.",
                "2 NPCs you put in are back.",
                "No NPC #9",
                "Usage: ::hidenpc index|mine (from ::npcs); again to show it",
            ),
            replies,
        )
        assertEquals(listOf(false, false), listOf(world.npcs[1]!!.hidden, world.npcs[2]!!.hidden))
    }

    @Test
    fun `npcs leaves hidden NPCs out for a player who isn't an admin`() {
        run("npc hans")
        run("npc cow calf")
        run("hidenpc 1")
        replies.clear()
        val players = CommandRegistry { false }.apply { registerNpcCommands(world) }
        players.execute(player, "npcs", replies::add)
        assertEquals(listOf("1 NPC within 15 tiles:", "#2 Cow calf (2792) at 3222, 3218; spawn 3222, 3218, wander 0"), replies)
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

    @Test
    fun `npcs mine lists only the NPCs you put in, however many others stand nearer`() {
        repeat(25) { world.spawnNpc(osrsps.game.npc.NpcSpawn(world.npcTypes.lookup("hans")!!, 3222, 3218, source = "map"))!! }
        player.z = 3228
        run("npc hans")
        player.z = 3218
        replies.clear()
        run("npcs 15")
        assertEquals(1 + 20 + 1, replies.size, "the plain list stops at 20 and says how many more")
        assertEquals(false, replies.any { it.startsWith("#26 ") })
        replies.clear()
        run("npcs 15 mine")
        run("npcs mine")
        run("npcs 5 mine")
        assertEquals(
            listOf(
                "1 NPC you put in within 15 tiles:",
                "#26 Hans (3105) at 3222, 3228; spawn 3222, 3228, wander 0",
                "1 NPC you put in within 15 tiles:",
                "#26 Hans (3105) at 3222, 3228; spawn 3222, 3228, wander 0",
                "No NPCs you put in within 5 tiles.",
            ),
            replies,
        )
    }

    @Test
    fun `delnpc mine removes every NPC you put in, and no others`() {
        run("npc hans")
        run("npc hans")
        val spawned = world.spawnNpc(osrsps.game.npc.NpcSpawn(world.npcTypes.lookup("hans")!!, 3220, 3218, source = "test"))!!
        replies.clear()
        run("delnpc mine")
        run("delnpc mine")
        assertEquals(listOf("Removed 2 NPCs you put in.", "Removed 0 NPCs you put in."), replies)
        assertEquals(listOf(spawned), world.npcs.all.toList())
    }

    @Test
    fun `npchp sets the hitpoints of one NPC or every one you put in`() {
        run("npc hans")
        run("npc hans")
        replies.clear()
        run("npchp 500 mine")
        run("npchp 9 1")
        run("npchp 0 1")
        run("npchp 9 99")
        assertEquals(listOf("2 NPCs now on 500 hitpoints until they respawn.", "1 NPC now on 9 hitpoints until they respawn.",
            "The hitpoints must be 1 to 100000", "No NPC #99"), replies)
        assertEquals(listOf(9, 500), listOf(world.npcs[1]!!.hitpoints, world.npcs[2]!!.hitpoints))
    }

    @Test
    fun `follow makes an NPC follow the player, and again stops it`() {
        run("npc hans")
        run("follow 1")
        val npc = world.npcs[1]!!
        assertEquals(player, npc.following)
        run("follow 1")
        assertNull(npc.following)
        run("follow 9")
        assertEquals(
            listOf(
                "Spawned Hans (3105) as #1 at 3222, 3218",
                "Hans #1 follows you. ::follow 1 again to stop.",
                "Hans #1 stopped following you.",
                "No NPC #9",
            ),
            replies,
        )
    }
}
