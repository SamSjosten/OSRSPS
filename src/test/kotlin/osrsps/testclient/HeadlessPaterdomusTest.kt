package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.Paterdomus
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Misthalin piece 7 step 8 through the headless client: Drezel handing back a lost wolfbane dagger (not into a full
 * backpack) and a lost library key before his talk after The Blood Moon Rises, Ivan Strom's talk and Drakan's
 * medallion, the front doors and the mausoleum's gates taking the player through, the spiral stairs, the library's
 * trapdoor and ladder, and the ways into Morytania refused. The rl-client script test-runner/examples/paterdomus.json
 * does the same on the real map.
 */
class HeadlessPaterdomusTest {
    private val drezel = NpcType(Paterdomus.DREZEL, "Drezel", ops = listOf("Talk-to", null, null, null, null), internalName = "priestperiltrappedmonk_vis")
    private val ivan = NpcType(Paterdomus.IVAN, "Ivan Strom", ops = listOf("Talk-to", null, null, null, null), internalName = "myq6_ivan_postquest_vis")

    private fun type(id: Int, name: String, op: String?, width: Int = 1, length: Int = 1, multiloc: Boolean = false) =
        LocType(id, name, ops = listOf(op, null, null, null, null), collision = LocCollisionType(width = width, length = length, interactType = 0), multiloc = multiloc)

    /** A front door on the east edge of 3224,3218, as the temple's are on the west wall's east edge (angle 2). */
    private val door = ZoneLoc(Paterdomus.DOOR_LEFT, LocShape.WALL_STRAIGHT, 2, 3224, 3218, 0)
    /** The temple's stairs on their own tiles, and a plain top of a spiral staircase (16673) somewhere else. */
    private val stairs = ZoneLoc(Paterdomus.STAIRS_UP, LocShape.CENTREPIECE_STRAIGHT, 2, 3417, 3492, 0)
    private val stairsTop = ZoneLoc(Paterdomus.STAIRS_DOWN, LocShape.CENTREPIECE_STRAIGHT, 2, 3417, 3493, 1)
    private val otherTop = ZoneLoc(Paterdomus.STAIRS_DOWN, LocShape.CENTREPIECE_STRAIGHT, 2, 3218, 3223, 1)
    private val trapdoor = ZoneLoc(Paterdomus.LIBRARY_TRAPDOOR, LocShape.GROUND_DECOR, 2, 3226, 3214, 0)
    private val barrier = ZoneLoc(Paterdomus.HOLY_BARRIER, LocShape.CENTREPIECE_STRAIGHT, 1, 3216, 3216, 0)
    private val railing = ZoneLoc(Paterdomus.RAILINGS.first(), LocShape.WALL_STRAIGHT, 0, 3228, 3222, 0)

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Paterdomus.MEDALLION + 1)
        objs[Paterdomus.WOLFBANE] = ObjType(Paterdomus.WOLFBANE, "Wolfbane")
        objs[Paterdomus.LIBRARY_KEY] = ObjType(Paterdomus.LIBRARY_KEY, "Temple library key")
        objs[Paterdomus.MEDALLION] = ObjType(Paterdomus.MEDALLION, "Drakan's medallion")
        objs[POT] = ObjType(POT, "Pot")
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(3417 shr 6, 3492 shr 6)
                markLoaded(Paterdomus.LIBRARY_LANDING.x shr 6, Paterdomus.LIBRARY_LANDING.z shr 6)
                markLoaded(Paterdomus.MAUSOLEUM_LANDING.x shr 6, Paterdomus.MAUSOLEUM_LANDING.z shr 6)
            },
            objTypes = ObjTypes(objs),
            npcTypes = NpcTypes.of(drezel, ivan),
            npcSpawns = listOf(NpcSpawn(drezel, 3220, 3214, source = "test"), NpcSpawn(ivan, 3222, 3214, source = "test")),
            locTypes = LocTypes(
                listOf(
                    type(Paterdomus.DOOR_LEFT, "Large door", "Open"),
                    type(Paterdomus.STAIRS_UP, "Staircase", "Climb-up", 2, 2),
                    type(Paterdomus.STAIRS_DOWN, "Staircase", "Climb-down"),
                    type(Paterdomus.LIBRARY_TRAPDOOR, "null", null, multiloc = true),
                    type(Paterdomus.HOLY_BARRIER, "Holy barrier", "Pass-through"),
                    type(Paterdomus.RAILINGS.first(), "Ornate railing", "Squeeze-through"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { listOf(door, stairs, stairsTop, otherTop, trapdoor, barrier, railing).forEach(::add) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.ready() = await("the login rebuild") { position }

    private fun HeadlessClient.chats(vararg keys: String) {
        for (key in keys) {
            awaitDialogue(key) { it is DialogueView.Chat && it.text.startsWith("[$key") }
            continueDialogue()
        }
    }

    private fun HeadlessClient.menu(vararg keys: String) {
        val menu = awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
        assertEquals(keys.map { "[$it]" }, menu.options)
    }

    private fun HeadlessClient.awaitClosed() = await("the dialogue to close") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    private fun HeadlessClient.awaitSeen(text: String, times: Int = 1) = await("$text x$times") { if (seen(text) >= times) Unit else null }

    private fun HeadlessClient.talk(id: Int) {
        val npc = await("NPC $id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, Paterdomus.TALK_TO)
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int) =
        await("at $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        awaitAt(x, z, level)
    }

    @Test
    fun `Drezel won't hand back the dagger into a full backpack, then hands it back, then the key, then talks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.command("item $POT 28")
                client.await("a full backpack") { if (client.count(POT) == 28) Unit else null }
                client.talk(Paterdomus.DREZEL)
                client.chats("drezel.lost_dagger", "drezel.no_space")
                client.awaitClosed()
                assertEquals(0, client.count(Paterdomus.WOLFBANE))
                client.command("empty")
                client.await("an empty backpack") { if (client.count(POT) == 0) Unit else null }

                client.talk(Paterdomus.DREZEL)
                client.chats("drezel.lost_dagger", "drezel.washed_up", "drezel.thanks")
                client.awaitClosed()
                assertEquals(1, client.count(Paterdomus.WOLFBANE))

                client.talk(Paterdomus.DREZEL)
                client.chats("drezel.lost_key", "drezel.another_key", "drezel.thanks")
                client.awaitClosed()
                assertEquals(1, client.count(Paterdomus.LIBRARY_KEY))

                client.talk(Paterdomus.DREZEL)
                client.chats(
                    "drezel.how_are_things name=alice", "drezel.not_bad", "drezel.friends", "drezel.that_or", "drezel.good_point",
                    "drezel.happier_news", "drezel.picky", "drezel.successor", "drezel.until_next",
                )
                client.awaitClosed()
            }
        }
    }

    @Test
    fun `Ivan talks about the columbarium, hands over one medallion and no second, and says goodbye`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                val options = arrayOf("ivan_strom.how_are_things", "ivan_strom.another_medallion", "ivan_strom.until_next_time")
                client.talk(Paterdomus.IVAN)
                client.chats("ivan_strom.hey name=bob", "ivan_strom.good_to_see")
                client.menu(*options)
                client.chooseOption(1)
                client.chats(
                    "ivan_strom.how_are_things", "ivan_strom.twice_as_hard", "ivan_strom.not_that_old", "ivan_strom.columbarium",
                    "ivan_strom.columbarium_q", "ivan_strom.burial_chamber", "ivan_strom.interesting", "ivan_strom.burgh",
                    "ivan_strom.safalaan", "ivan_strom.efaritay", "ivan_strom.homeland", "ivan_strom.hallowvale", "ivan_strom.swamps",
                    "ivan_strom.ellipsis", "ivan_strom.ghasts", "ivan_strom.sigh", "ivan_strom.until_next name=bob",
                )
                client.awaitClosed()

                client.talk(Paterdomus.IVAN)
                client.chats("ivan_strom.hey name=bob", "ivan_strom.good_to_see")
                client.menu(*options)
                client.chooseOption(2)
                client.chats("ivan_strom.another_medallion", "ivan_strom.sure")
                // The item box, whose text the headless client doesn't read (the runner script checks it).
                client.awaitDialogue("the medallion box") { it is DialogueView.Other && it.interfaceId == Chatbox.OBJECTBOX }
                client.resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
                client.awaitClosed()
                assertEquals(1, client.count(Paterdomus.MEDALLION))

                client.talk(Paterdomus.IVAN)
                client.chats("ivan_strom.hey name=bob", "ivan_strom.good_to_see")
                client.menu(*options)
                client.chooseOption(2)
                client.chats("ivan_strom.another_medallion", "ivan_strom.sure", "paterdomus.medallion_owned")
                client.awaitClosed()
                assertEquals(1, client.count(Paterdomus.MEDALLION))

                client.talk(Paterdomus.IVAN)
                client.chats("ivan_strom.hey name=bob", "ivan_strom.good_to_see")
                client.menu(*options)
                client.chooseOption(3)
                client.chats("ivan_strom.until_next_time", "ivan_strom.see_you name=bob")
                client.awaitClosed()
            }
        }
    }

    @Test
    fun `the doors take the player through both ways, the stairs up and down, and the library's trapdoor and ladder`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                client.tele(3224, 3218)
                client.locOp(door.id, door.x, door.z, Paterdomus.OPEN)
                client.awaitAt(3225, 3218, 0)
                client.locOp(door.id, door.x, door.z, Paterdomus.OPEN)
                client.awaitAt(3224, 3218, 0)

                client.tele(3418, 3491)
                client.locOp(stairs.id, stairs.x, stairs.z, 1)
                client.awaitAt(3416, 3493, 1)
                client.locOp(stairsTop.id, stairsTop.x, stairsTop.z, 1)
                client.awaitAt(3417, 3494, 0)
                // The same type elsewhere is another building's stairs, not the temple's.
                client.tele(3218, 3224, 1)
                client.locOp(otherTop.id, otherTop.x, otherTop.z, 1)
                client.awaitSeen("[game.nothing_interesting]")
                assertEquals(Coord(3218, 3224, 1), client.position)

                client.tele(3224, 3218)

                client.locOp(trapdoor.id, trapdoor.x, trapdoor.z, 1)
                client.awaitAt(Paterdomus.LIBRARY_LANDING.x, Paterdomus.LIBRARY_LANDING.z, 0)
            }
        }
    }

    @Test
    fun `the Holy barrier and the ornate railing wait for Morytania`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready()
                client.locOp(barrier.id, barrier.x, barrier.z, 1)
                client.awaitSeen("[paterdomus.morytania_later]")
                client.locOp(railing.id, railing.x, railing.z, 1)
                client.awaitSeen("[paterdomus.morytania_later]", 2)
            }
        }
    }

    private companion object {
        const val POT = 1931
    }
}
