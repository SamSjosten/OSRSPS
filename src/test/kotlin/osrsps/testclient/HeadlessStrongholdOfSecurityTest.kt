package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.Church
import osrsps.game.content.StrongholdLines
import osrsps.game.content.StrongholdOfSecurity
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Edgeville piece step 12 through the headless client and the registered handlers: the entrance down and the ladder
 * back up, the Dead Explorer's notes, a pair of Gates of War with the second one's question (wrong answers keep the
 * player back, the right one lets them through), the Gift of Peace once, and the portal refused and then taken. The
 * locs are placed as the 241 cache has them (types, shapes, angles and options), on open ground. The rl-client script
 * test-runner/examples/stronghold-of-security.json does the same on the real map.
 */
class HeadlessStrongholdOfSecurityTest {
    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val types = LocTypes(
        listOf(
            type(StrongholdOfSecurity.ENTRANCE, "Entrance", "Climb-down"),
            type(StrongholdOfSecurity.LADDER_UP, "Ladder", "Climb-up"),
            type(StrongholdOfSecurity.GATE_OF_WAR_LEFT, "Gate of War", "Open"),
            type(StrongholdOfSecurity.GATE_OF_WAR_RIGHT, "Gate of War", "Open"),
            type(StrongholdOfSecurity.DEAD_EXPLORER, "Dead Explorer", "Search"),
            type(StrongholdOfSecurity.GIFT_OF_PEACE, "Gift of Peace", "Open"),
            type(StrongholdOfSecurity.PORTAL, "Portal", "Use"),
            type(StrongholdOfSecurity.WAR_LADDER_DOWN, "Ladder", "Climb-down"),
            type(StrongholdOfSecurity.FAMINE_LADDER_UP, "Ladder", "Climb-up"),
            type(StrongholdOfSecurity.FAMINE_ROPE, "Rope", "Climb-up"),
            type(StrongholdOfSecurity.RICKETY_DOOR_LEFT, "Rickety door", "Open"),
            type(StrongholdOfSecurity.RICKETY_DOOR_RIGHT, "Rickety door", "Open"),
            type(StrongholdOfSecurity.GRAIN_OF_PLENTY, "Grain of Plenty", "Search"),
            type(StrongholdOfSecurity.FAMINE_PORTAL, "Portal", "Use"),
        ).associateBy { it.id },
    )

    private val locs = listOf(
        ZoneLoc(StrongholdOfSecurity.ENTRANCE, LocShape.CENTREPIECE_STRAIGHT, 0, 3081, 3420, 0),
        ZoneLoc(StrongholdOfSecurity.LADDER_UP, LocShape.CENTREPIECE_STRAIGHT, 2, 1859, 5244, 0),
        ZoneLoc(StrongholdOfSecurity.LADDER_UP, LocShape.CENTREPIECE_STRAIGHT, 3, 1913, 5226, 0),
        ZoneLoc(StrongholdOfSecurity.DEAD_EXPLORER, LocShape.CENTREPIECE_STRAIGHT, 0, 1860, 5240, 0),
        ZoneLoc(StrongholdOfSecurity.GIFT_OF_PEACE, LocShape.CENTREPIECE_STRAIGHT, 0, 1907, 5222, 0),
        ZoneLoc(StrongholdOfSecurity.PORTAL, LocShape.CENTREPIECE_STRAIGHT, 0, 1863, 5238, 0),
        // The pair east of the reward room's south: west edges of x 1904 and 1907, rows 5203 and 5204.
        ZoneLoc(StrongholdOfSecurity.GATE_OF_WAR_LEFT, LocShape.WALL_STRAIGHT, 0, 1904, 5203, 0),
        ZoneLoc(StrongholdOfSecurity.GATE_OF_WAR_RIGHT, LocShape.WALL_STRAIGHT, 0, 1904, 5204, 0),
        ZoneLoc(StrongholdOfSecurity.GATE_OF_WAR_LEFT, LocShape.WALL_STRAIGHT, 0, 1907, 5203, 0),
        ZoneLoc(StrongholdOfSecurity.GATE_OF_WAR_RIGHT, LocShape.WALL_STRAIGHT, 0, 1907, 5204, 0),
        // The second floor, the Catacomb of Famine.
        ZoneLoc(StrongholdOfSecurity.WAR_LADDER_DOWN, LocShape.CENTREPIECE_STRAIGHT, 1, 1902, 5222, 0),
        ZoneLoc(StrongholdOfSecurity.FAMINE_LADDER_UP, LocShape.CENTREPIECE_STRAIGHT, 1, 2042, 5246, 0),
        ZoneLoc(StrongholdOfSecurity.FAMINE_ROPE, LocShape.CENTREPIECE_STRAIGHT, 0, 2017, 5210, 0),
        ZoneLoc(StrongholdOfSecurity.GRAIN_OF_PLENTY, LocShape.CENTREPIECE_STRAIGHT, 0, 2021, 5215, 0),
        ZoneLoc(StrongholdOfSecurity.FAMINE_PORTAL, LocShape.CENTREPIECE_STRAIGHT, 1, 2039, 5240, 0),
        // A pair on the west edge of x 1997 and the east edge of x 1999, rows 5215 and 5216.
        ZoneLoc(StrongholdOfSecurity.RICKETY_DOOR_LEFT, LocShape.WALL_STRAIGHT, 0, 1997, 5215, 0),
        ZoneLoc(StrongholdOfSecurity.RICKETY_DOOR_RIGHT, LocShape.WALL_STRAIGHT, 0, 1997, 5216, 0),
        ZoneLoc(StrongholdOfSecurity.RICKETY_DOOR_RIGHT, LocShape.WALL_STRAIGHT, 2, 1999, 5215, 0),
        ZoneLoc(StrongholdOfSecurity.RICKETY_DOOR_LEFT, LocShape.WALL_STRAIGHT, 2, 1999, 5216, 0),
    )

    private val gate = NpcType(StrongholdOfSecurity.GATE_OF_WAR_NPC, "Gate of War", ops = listOf("talk", null, null, null, null))
    private val famineDoor = NpcType(StrongholdOfSecurity.FAMINE_DOOR_NPC, "Rickety door", ops = listOf("talk", null, null, null, null))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            markLoaded(3081 shr 6, 3420 shr 6)
            markLoaded(1859 shr 6, 5244 shr 6)
            markLoaded(2021 shr 6, 5215 shr 6)
        }
        World(
            crcs, map, random = GameRandom(12), locTypes = types, staticLocs = StaticLocs().apply { locs.forEach(::add) },
            objTypes = ObjTypes.of(ObjType(StrongholdOfSecurity.COINS, "Coins", stackable = true), ObjType(StrongholdOfSecurity.NOTES, "Stronghold notes")),
            npcTypes = NpcTypes.of(gate, famineDoor),
            varTypes = VarTypes(
                VARP_COUNT,
                mapOf(
                    StrongholdOfSecurity.VARBIT_FLAP to VarbitType(VARP_SOS_EMOTE, 0, 0),
                    StrongholdOfSecurity.VARBIT_SLAP_HEAD to VarbitType(VARP_SOS_EMOTE, 1, 1),
                ),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int) =
        assertEquals(Coord(x, z, level), await("$x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } })

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.message(line: String) =
        awaitDialogue("the box $line") { it is DialogueView.Message && it.text == "[$line]" }

    @Test
    fun `the entrance climbs down to the first floor and its ladders climb back up beside it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3081, 3421, 0)
                client.locOp(StrongholdOfSecurity.ENTRANCE, 3081, 3420, StrongholdOfSecurity.CLIMB_DOWN)
                client.awaitAt(StrongholdOfSecurity.LANDING.x, StrongholdOfSecurity.LANDING.z, 0)
                client.locOp(StrongholdOfSecurity.LADDER_UP, 1859, 5244, StrongholdOfSecurity.CLIMB_UP)
                client.awaitAt(Church.STRONGHOLD.x, Church.STRONGHOLD.z, 0)
                client.tele(1913, 5225, 0)
                client.locOp(StrongholdOfSecurity.LADDER_UP, 1913, 5226, StrongholdOfSecurity.CLIMB_UP)
                client.awaitAt(Church.STRONGHOLD.x, Church.STRONGHOLD.z, 0)
            }
        }
    }

    @Test
    fun `the Dead Explorer gives the notes once while they are carried`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(1860, 5241, 0)
                client.locOp(StrongholdOfSecurity.DEAD_EXPLORER, 1860, 5240, StrongholdOfSecurity.SEARCH)
                client.seen("[${StrongholdLines.EXPLORER_FIND.key}]")
                client.await("the notes") { client.inventory(BACKPACK).firstOrNull { it?.id == StrongholdOfSecurity.NOTES } }
                client.locOp(StrongholdOfSecurity.DEAD_EXPLORER, 1860, 5240, StrongholdOfSecurity.SEARCH)
                client.seen("[${StrongholdLines.EXPLORER_NOTHING.key}]")
                assertEquals(1, client.inventory(BACKPACK).count { it?.id == StrongholdOfSecurity.NOTES })
            }
        }
    }

    @Test
    fun `the second gate of a pair asks, a wrong answer keeps the player back, the right one lets them through`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                // The first gate of the pair, from the room into the passage: never asks.
                client.tele(1903, 5203, 0)
                client.locOp(StrongholdOfSecurity.GATE_OF_WAR_LEFT, 1904, 5203, StrongholdOfSecurity.OPEN)
                client.awaitAt(1904, 5203, 0)
                client.tele(1903, 5204, 0)
                client.locOp(StrongholdOfSecurity.GATE_OF_WAR_RIGHT, 1904, 5204, StrongholdOfSecurity.OPEN)
                client.awaitAt(1904, 5204, 0)

                // The second gate, from the passage: it may let the player straight through, or ask.
                var wrong = false
                var right = false
                var straight = false
                repeat(40) {
                    if (wrong && right && straight) return@repeat
                    client.tele(1906, 5203, 0)
                    client.locOp(StrongholdOfSecurity.GATE_OF_WAR_LEFT, 1907, 5203, StrongholdOfSecurity.OPEN)
                    val asked = client.await("through or asked") {
                        if (client.position == Coord(1907, 5203, 0)) false
                        else (client.dialogueView as? DialogueView.Chat)?.takeIf { it.name == "Gate of War" }?.let { true }
                    }
                    if (!asked) {
                        straight = true
                        return@repeat
                    }
                    val chat = client.dialogueView as DialogueView.Chat
                    val number = QUESTION.matchEntire(chat.text)!!.groupValues[1].toInt()
                    val question = StrongholdLines.QUESTIONS[number - 1]
                    client.continueDialogue()
                    val menu = client.awaitDialogue("the answers") { it is DialogueView.Menu } as DialogueView.Menu
                    assertEquals(question.options.map { "[${it.key}]" }, menu.options)
                    val pick = if (!wrong) (1..question.options.size).first { it != question.right } else question.right
                    client.chooseOption(pick)
                    client.awaitDialogue("the reply") { it is DialogueView.Chat && it.text == "[${question.replies[pick - 1].key}]" }
                    client.continueDialogue()
                    if (pick == question.right) {
                        client.awaitAt(1907, 5203, 0)
                        right = true
                    } else {
                        client.await("the box closed") { Unit.takeIf { client.dialogueView == null } }
                        assertEquals(Coord(1906, 5203, 0), client.position)
                        wrong = true
                    }
                }
                assertTrue(wrong && right && straight, "wrong $wrong, right $right, straight $straight")
            }
        }
    }

    @Test
    fun `the Gift of Peace pays once, then the portal takes the player to it and the gates stop asking`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                // Combat level 3 and nothing claimed: the portal refuses.
                client.tele(1863, 5239, 0)
                client.locOp(StrongholdOfSecurity.PORTAL, 1863, 5238, StrongholdOfSecurity.USE)
                client.message(StrongholdLines.PORTAL_REFUSE.key)
                client.continueDialogue()

                client.tele(1907, 5223, 0)
                client.locOp(StrongholdOfSecurity.GIFT_OF_PEACE, 1907, 5222, StrongholdOfSecurity.OPEN)
                client.message(StrongholdLines.GIFT_OPEN.key)
                client.continueDialogue()
                client.message(StrongholdLines.GIFT_REWARD.key)
                client.continueDialogue()
                assertEquals(StrongholdOfSecurity.GIFT_COINS, client.await("the coins") {
                    client.inventory(BACKPACK).firstOrNull { it?.id == StrongholdOfSecurity.COINS }?.count
                })
                assertEquals(1, client.await("the Flap emote") { client.varp(VARP_SOS_EMOTE).takeIf { it != 0 } })
                client.locOp(StrongholdOfSecurity.GIFT_OF_PEACE, 1907, 5222, StrongholdOfSecurity.OPEN)
                client.message(StrongholdLines.GIFT_CLAIMED.key)
                client.continueDialogue()
                assertEquals(StrongholdOfSecurity.GIFT_COINS, client.inventory(BACKPACK).firstOrNull { it?.id == StrongholdOfSecurity.COINS }?.count)

                client.tele(1863, 5239, 0)
                client.locOp(StrongholdOfSecurity.PORTAL, 1863, 5238, StrongholdOfSecurity.USE)
                client.awaitAt(StrongholdOfSecurity.PORTAL_LANDING.x, StrongholdOfSecurity.PORTAL_LANDING.z, 0)

                repeat(6) {
                    client.tele(1906, 5203, 0)
                    client.locOp(StrongholdOfSecurity.GATE_OF_WAR_LEFT, 1907, 5203, StrongholdOfSecurity.OPEN)
                    client.awaitAt(1907, 5203, 0)
                }
            }
        }
    }

    @Test
    fun `the ladder down reaches the Catacomb of Famine, whose ladder, rope, doors, Grain of Plenty and portal work`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(1903, 5222, 0)
                client.locOp(StrongholdOfSecurity.WAR_LADDER_DOWN, 1902, 5222, StrongholdOfSecurity.CLIMB_DOWN)
                client.awaitAt(StrongholdOfSecurity.FAMINE_LANDING.x, StrongholdOfSecurity.FAMINE_LANDING.z, 0)
                client.locOp(StrongholdOfSecurity.FAMINE_LADDER_UP, 2042, 5246, StrongholdOfSecurity.CLIMB_UP)
                client.awaitAt(StrongholdOfSecurity.WAR_LADDER_DOWN_LANDING.x, StrongholdOfSecurity.WAR_LADDER_DOWN_LANDING.z, 0)
                client.tele(2017, 5211, 0)
                client.locOp(StrongholdOfSecurity.FAMINE_ROPE, 2017, 5210, StrongholdOfSecurity.CLIMB_UP)
                client.awaitAt(Church.STRONGHOLD.x, Church.STRONGHOLD.z, 0)

                // Combat level 3 and the grain not searched: the portal refuses.
                client.tele(2040, 5240, 0)
                client.locOp(StrongholdOfSecurity.FAMINE_PORTAL, 2039, 5240, StrongholdOfSecurity.USE)
                client.message(StrongholdLines.PORTAL_REFUSE.key)
                client.continueDialogue()

                // The first door of the pair never asks; the second (from the passage, an east edge) may.
                client.tele(1996, 5215, 0)
                client.locOp(StrongholdOfSecurity.RICKETY_DOOR_LEFT, 1997, 5215, StrongholdOfSecurity.OPEN)
                client.awaitAt(1997, 5215, 0)
                var asked = false
                repeat(20) {
                    if (asked) return@repeat
                    client.tele(1999, 5215, 0)
                    client.locOp(StrongholdOfSecurity.RICKETY_DOOR_RIGHT, 1999, 5215, StrongholdOfSecurity.OPEN)
                    asked = client.await("through or asked") {
                        if (client.position == Coord(2000, 5215, 0)) false
                        else (client.dialogueView as? DialogueView.Chat)?.takeIf { it.name == "Rickety door" }?.let { true }
                    }
                }
                assertTrue(asked, "the rickety door asked")
                client.tele(2021, 5216, 0)
                client.locOp(StrongholdOfSecurity.GRAIN_OF_PLENTY, 2021, 5215, StrongholdOfSecurity.SEARCH)
                client.message(StrongholdLines.GRAIN_OPEN.key)
                client.continueDialogue()
                client.message(StrongholdLines.GRAIN_REWARD.key)
                client.continueDialogue()
                assertEquals(StrongholdOfSecurity.GRAIN_COINS, client.await("the coins") {
                    client.inventory(BACKPACK).firstOrNull { it?.id == StrongholdOfSecurity.COINS }?.count
                })
                assertEquals(2, client.await("the Slap Head emote") { client.varp(VARP_SOS_EMOTE).takeIf { it != 0 } })
                client.locOp(StrongholdOfSecurity.GRAIN_OF_PLENTY, 2021, 5215, StrongholdOfSecurity.SEARCH)
                client.message(StrongholdLines.GRAIN_CLAIMED.key)
                client.continueDialogue()

                client.tele(2040, 5240, 0)
                client.locOp(StrongholdOfSecurity.FAMINE_PORTAL, 2039, 5240, StrongholdOfSecurity.USE)
                client.seen("[${StrongholdLines.PORTAL_ENTER.key}]")
                client.awaitAt(StrongholdOfSecurity.FAMINE_PORTAL_LANDING.x, StrongholdOfSecurity.FAMINE_PORTAL_LANDING.z, 0)
                // The grain searched, the floor's doors stop asking.
                repeat(6) {
                    client.tele(1999, 5216, 0)
                    client.locOp(StrongholdOfSecurity.RICKETY_DOOR_LEFT, 1999, 5216, StrongholdOfSecurity.OPEN)
                    client.awaitAt(2000, 5216, 0)
                }
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val VARP_COUNT = 6_000
        const val VARP_SOS_EMOTE = 802
        val QUESTION = Regex("\\[stronghold_of_security\\.q(\\d+)]")
    }
}
