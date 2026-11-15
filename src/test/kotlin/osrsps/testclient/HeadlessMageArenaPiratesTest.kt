package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.bank.BankScreen
import osrsps.game.GameRandom
import osrsps.game.ge.GrandExchangeScreen
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.MageArenaBank
import osrsps.game.content.MageArenaBankLines
import osrsps.game.content.SearchLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopTypes
import osrsps.game.skill.ThievingLocks
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Wilderness step W3.7 through the headless client: Gundai's talk, Bank and Collect; Lundail's talk ending in his rune
 * shop (read from the real shops.json), and Claim-runes refused; the sack between the webs giving a knife once; and the
 * Pirates' Hideout's door locked from outside, wanting level 39 and a lockpick, picked through for 35 XP and opened
 * freely from inside. The rl-client script test-runner/examples/mage-bank-pirates.json does the same on the real map.
 */
class HeadlessMageArenaPiratesTest {
    private val gundai = NpcType(MageArenaBank.GUNDAI, "Gundai", ops = listOf("Talk-to", null, "Bank", "Collect", null), internalName = "magearena_banker")
    private val lundail = NpcType(MageArenaBank.LUNDAIL, "Lundail", ops = listOf("Talk-to", null, "Trade", "Claim-runes", null), internalName = "magearena_runeshop")
    private val npcTypes = NpcTypes.of(gundai, lundail)

    /** On the west edge of its tile, which is the hideout's side; outside is x 3037. */
    private val door = ZoneLoc(ThievingLocks.DOOR_PIRATES, LocShape.WALL_STRAIGHT, 0, 3038, 3956, 0)
    private val sack = ZoneLoc(MageArenaBank.SACK, LocShape.CENTREPIECE_STRAIGHT, 0, MageArenaBank.SACK_X, MageArenaBank.SACK_Z, 0)

    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val shopText = ShopTypes::class.java.getResource(ShopTypes.RESOURCE)!!.readText()
        val stock = Regex("\"item\": (\\d+),\\s*\"name\": \"([^\"]+)\"").findAll(shopText).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val objs = arrayOfNulls<ObjType>(stock.keys.max() + 1)
        for ((id, name) in stock) objs[id] = ObjType(id, name, stackable = true)
        objs[MageArenaBank.KNIFE] = ObjType(MageArenaBank.KNIFE, "Knife")
        objs[ThievingLocks.LOCKPICK] = ObjType(ThievingLocks.LOCKPICK, "Lockpick")
        val objTypes = ObjTypes(objs)
        World(
            crcs,
            CollisionMap().apply { markLoaded(2534 shr 6, 4714 shr 6); markLoaded(3093 shr 6, 3956 shr 6); markLoaded(3038 shr 6, 3956 shr 6) },
            random = GameRandom(7), objTypes = objTypes, npcTypes = npcTypes,
            npcSpawns = listOf(NpcSpawn(gundai, 2534, 4714, wander = 0, source = "test"), NpcSpawn(lundail, 2534, 4719, wander = 0, source = "test")),
            shopTypes = ShopTypes.parse(shopText, npcTypes, objTypes),
            locTypes = LocTypes(listOf(type(ThievingLocks.DOOR_PIRATES, "Door", "Open", "Pick-lock"), type(MageArenaBank.SACK, "Sack", "Search")).associateBy { it.id }),
            staticLocs = StaticLocs().apply { add(door); add(sack) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.count(id: Int): Long = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.sees(vararg lines: Line) {
        for (line in lines) {
            awaitDialogue(line.key) { it is DialogueView.Chat && it.text == DialogueText.NONE[line] }
            continueDialogue()
        }
    }

    private fun HeadlessClient.awaitOpen(id: Int, what: String) = await(what) { if (id in openInterfaces()) Unit else null }

    private fun HeadlessClient.seen(key: String) = messages().count { it.text == "[thieving_lock.$key]" }

    private fun HeadlessClient.npcIndex(server: TestServer, id: Int) = server.world.npcs.all.first { it.type.id == id }.index

    @Test
    fun `Gundai banks from his talk and his Bank and Collect options`(@TempDir dir: Path) {
        server(dir).use { server ->
            for ((name, op) in listOf("ada" to MageArenaBank.TALK_TO, "ben" to 3, "cat" to 4)) {
                server.login(name).use { client ->
                    client.await("the login rebuild") { client.position }
                    client.commandReply("keys on") { it == "Dialogue shows keys" }
                    // Across his counter: two tiles west of him.
                    client.tele(2532, 4714)
                    client.npcOp(client.npcIndex(server, MageArenaBank.GUNDAI), op)
                    if (op == MageArenaBank.TALK_TO) {
                        client.sees(MageArenaBankLines.GUNDAI_HELLO, MageArenaBankLines.GUNDAI_BANKER)
                        client.awaitDialogue("his menu") { it is DialogueView.Menu }
                        client.chooseOption(1)
                    }
                    val screen = if (op == 4) GrandExchangeScreen.COLLECTION_BOX else BankScreen.MAIN
                    client.awaitOpen(screen, "screen $screen from op $op")
                }
            }
        }
    }

    @Test
    fun `Lundail's talk ends in his rune shop, and Claim-runes wants the diary`(@TempDir dir: Path) {
        server(dir).use { server ->
            assertNotNull(server.world.shopTypes.ofNpc(MageArenaBank.LUNDAIL), "shops.json's rune shop is Lundail's")
            server.login("dee").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(2533, 4719)
                val index = client.npcIndex(server, MageArenaBank.LUNDAIL)
                with(MageArenaBankLines) {
                    client.npcOp(index, MageArenaBank.CLAIM_RUNES)
                    client.sees(LUNDAIL_NEED_DIARY)
                    client.await("the talk over") { if (client.dialogueView == null) Unit else null }

                    client.npcOp(index, MageArenaBank.TALK_TO)
                    client.sees(LUNDAIL_HELLO)
                    client.awaitDialogue("his menu") { it is DialogueView.Menu }
                    client.chooseOption(2)
                    client.sees(LUNDAIL_BUILDING, LUNDAIL_ARENA, LUNDAIL_WOW, LUNDAIL_FRIED, LUNDAIL_NOTICED)
                    client.await("the talk over") { if (client.dialogueView == null) Unit else null }
                    assertTrue(ShopScreen.MAIN !in client.openInterfaces(), "no shop after the arena talk")

                    client.npcOp(index, MageArenaBank.TALK_TO)
                    client.sees(LUNDAIL_HELLO)
                    client.awaitDialogue("his menu") { it is DialogueView.Menu }
                    client.chooseOption(1)
                    client.sees(LUNDAIL_SELLING, LUNDAIL_RUNES)
                    client.awaitOpen(ShopScreen.MAIN, "his shop")
                }
            }
        }
    }

    @Test
    fun `the sack between the webs gives a knife to a player without one`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("eve").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(MageArenaBank.SACK_X - 1, MageArenaBank.SACK_Z)
                client.locOp(sack.id, sack.x, sack.z, MageArenaBank.SEARCH)
                client.awaitMessage("the knife") { it == DialogueText.NONE[MageArenaBankLines.SACK_KNIFE] }
                client.await("a knife") { Unit.takeIf { client.count(MageArenaBank.KNIFE) == 1L } }
                client.locOp(sack.id, sack.x, sack.z, MageArenaBank.SEARCH)
                client.awaitMessage("the empty sack") { it == DialogueText.NONE[SearchLines.SACKS_EMPTY] }
                client.awaitTicks(2)
                assertEquals(1L, client.count(MageArenaBank.KNIFE), "one knife at a time")
            }
        }
    }

    @Test
    fun `the hideout's door wants 39 and a lockpick from outside, and just opens from inside`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fay").use { client ->
                client.await("the login rebuild") { client.position }
                val fay = server.world.playerNamed("fay")!!
                client.tele(door.x - 1, door.z)
                client.locOp(door.id, door.x, door.z, ThievingLocks.OP_OPEN)
                client.await("locked") { if (client.seen("door_locked") == 1) Unit else null }

                client.command("setlevel thieving 38")
                client.await("Thieving 38") { client.stat(THIEVING)?.takeIf { it.level == 38 } }
                client.locOp(door.id, door.x, door.z, ThievingLocks.OP_SECOND)
                client.awaitDialogue("the level") { it is DialogueView.Message && it.text == "[thieving_lock.door_level level=39]" }
                client.continueDialogue()
                client.await("the box shut") { if (client.dialogueView == null) Unit else null }

                client.command("setlevel thieving 39")
                client.await("Thieving 39") { client.stat(THIEVING)?.takeIf { it.level == 39 } }
                client.locOp(door.id, door.x, door.z, ThievingLocks.OP_SECOND)
                client.await("no lockpick") { if (client.seen("need_lockpick") == 1) Unit else null }

                client.command("item ${ThievingLocks.LOCKPICK} 1")
                client.await("the lockpick") { Unit.takeIf { client.count(ThievingLocks.LOCKPICK) == 1L } }
                val tenths = fay.skills.tenths(Skill.THIEVING)
                var tries = 0
                while (client.seen("pick_done") == 0) {
                    assertTrue(++tries <= 40, "a pick works in 40 tries")
                    val fails = client.seen("pick_fail")
                    client.locOp(door.id, door.x, door.z, ThievingLocks.OP_SECOND)
                    client.await("the pick's result") { if (client.seen("pick_fail") > fails || client.seen("pick_done") > 0) Unit else null }
                }
                client.await("inside, on the door's own tile") { client.position?.takeIf { it == Coord(door.x, door.z, 0) } }
                assertEquals(tenths + 350, fay.skills.tenths(Skill.THIEVING))
                assertEquals(1L, client.count(ThievingLocks.LOCKPICK), "the lockpick kept")

                client.locOp(door.id, door.x, door.z, ThievingLocks.OP_OPEN)
                client.await("through") { if (client.seen("through") >= 1) Unit else null }
                client.await("outside") { client.position?.takeIf { it == Coord(door.x - 1, door.z, 0) } }
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val THIEVING = 17
    }
}
