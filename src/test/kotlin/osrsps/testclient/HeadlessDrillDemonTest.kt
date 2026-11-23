package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.XpLamp
import osrsps.game.content.randomevents.DrillDemon
import osrsps.game.content.randomevents.RandomEvents
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.LocShape

/**
 * Random events step 5 through the headless client: Sergeant Damien takes his player to a camp of their own and orders
 * exercises; the signs (varbits on varp macro_transmit) say which mat is which and shuffle after every go. A wrong one
 * starts the four again; four right in a row bring the player back with a camouflage piece (the top first), or with the
 * whole outfit a lamp and the drill emotes. Declining and leaving early give nothing. The rl-client script
 * test-runner/examples/random-events-drill-demon.json does it in the real game.
 */
class HeadlessDrillDemonTest {
    private val npcs = listOf(
        NpcType(DrillDemon.npcId, "Sergeant Damien", ops = listOf("Talk-to", null, null, null, "Dismiss")),
        NpcType(DrillDemon.CAMP_SERGEANT, "Sergeant Damien", ops = listOf("Talk-to", null, null, null, null)),
    )

    private val objs = listOf(
        ObjType(DrillDemon.TOP, "Camo top"), ObjType(DrillDemon.BOTTOMS, "Camo bottoms"), ObjType(DrillDemon.HELM, "Camo helmet"),
        ObjType(XpLamp.LAMP, "Lamp"), ObjType(BEER, "Beer"),
    )

    private val mats = DrillDemon.MATS.keys.associateWith { LocType(it, "Exercise mat", ops = listOf("Use", null, null, null, null)) }

    private val varbits = DrillDemon.POST_VARBITS.withIndex().associate { (i, id) -> id to VarbitType(MACRO_TRANSMIT, i * 3, i * 3 + 2) } +
        (DrillDemon.EMOTES_VARBIT to VarbitType(EMOTE_ACCESS, 11, 11))

    private fun server(dir: Path, collision: CollisionMap = CollisionMap()) = TestServer(dir, 30) { crcs, millis ->
        World(
            crcs, collision.apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(49, 75) },
            npcTypes = NpcTypes.of(*npcs.toTypedArray()), objTypes = ObjTypes.of(*objs.toTypedArray()),
            locTypes = LocTypes(mats),
            staticLocs = StaticLocs().apply {
                for ((mat, post) in DrillDemon.MATS) {
                    add(ZoneLoc(mat, LocShape.GROUND_DECOR, 0, matX(post), 4819, 0))
                    add(ZoneLoc(mat, LocShape.GROUND_DECOR, 2, matX(post), 4820, 0))
                }
            },
            varTypes = VarTypes(2_000, varbits), cycleMillis = millis,
        )
    }

    private fun matX(post: Int) = 3158 + post * 2

    private fun HeadlessClient.ready() {
        await("the login rebuild") { position }
        command("tele 3222 3218 0")
        await("the teleport") { position?.takeIf { it == Coord(3222, 3218, 0) } }
        commandReply("randomevent off") { it == "Random events off" }
    }

    private fun HeadlessClient.chat(text: String) {
        awaitDialogue(text) { it is DialogueView.Chat && it.text == text }
        continueDialogue()
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.has(id: Int) = inventory(Inventory.INV).count { it?.id == id }

    /** His order (chat, picture, options): returns the exercise's sign value, answering [answer]. */
    private fun HeadlessClient.order(prefix: String = "order", answer: Int = 1): Int {
        val said = awaitDialogue("his order") { it is DialogueView.Chat && it.text.startsWith("[drill_demon.$prefix") } as DialogueView.Chat
        val exercise = said.text.removePrefix("[drill_demon.$prefix").removeSuffix("]").substringAfter("_")
        continueDialogue()
        awaitDialogue("the picture") { it is DialogueView.Other && it.interfaceId == Chatbox.OBJECTBOX }
        assertEquals("[drill_demon.box_$exercise]", text(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_TEXT))
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
        awaitDialogue("the options") { it is DialogueView.Menu }
        chooseOption(answer)
        if (answer == 1) {
            chat("[drill_demon.okay]")
            over()
        }
        return SIGNS.getValue(exercise)
    }

    /** Talk-to on him out in the world and yes: into the camp, and his first order. Returns the camp's offset and the order. */
    private fun HeadlessClient.intoCamp(name: String): Triple<Int, Int, Int> {
        command("randomevent ${DrillDemon.key}")
        val npc = await("the sergeant") { npcs().firstOrNull { it.id == DrillDemon.npcId } }
        npcOp(npc.index, RandomEvents.TALK_TO)
        chat("[drill_demon.recommended name=$name]")
        awaitDialogue("the options") { it is DialogueView.Menu }
        chooseOption(1)
        chat("[drill_demon.yes_sir]")
        chat("[drill_demon.move_yourself]")
        val wanted = order()
        val at = position!!
        return Triple(at.x - DrillDemon.ARRIVE.x, at.z - DrillDemon.ARRIVE.z, wanted)
    }

    /** The post whose sign shows [sign] (or, with [right] false, any other). */
    private fun HeadlessClient.post(sign: Int, right: Boolean = true): Int {
        val signs = await("the signs") { varp(MACRO_TRANSMIT).takeIf { it != 0 } }
        val posts = (1..4).associateWith { (signs shr (3 * (it - 1))) and 7 }
        assertEquals((1..4).toSet(), posts.values.toSet(), "each exercise on one sign: $posts")
        return posts.entries.first { (it.value == sign) == right }.key
    }

    private fun HeadlessClient.useMat(post: Int, dx: Int, dz: Int) {
        val mat = DrillDemon.MATS.entries.first { it.value == post }.key
        locOp(mat, matX(post) + dx, 4820 + dz, 1)
    }

    /** Four right in a row, after one wrong; then his last words, read on or (with [shut]) shut. */
    private fun HeadlessClient.drill(dx: Int, dz: Int, first: Int, shut: Boolean = false, beforeLast: () -> Unit = {}) {
        var wanted = first
        useMat(post(wanted, right = false), dx, dz)
        wanted = order(prefix = "wrong")
        repeat(DrillDemon.ROUNDS) { round ->
            if (round == DrillDemon.ROUNDS - 1) beforeLast()
            useMat(post(wanted), dx, dz)
            if (round < DrillDemon.ROUNDS - 1) wanted = order()
        }
        if (!shut) return chat("[drill_demon.done]")
        awaitDialogue("his last words") { it is DialogueView.Chat && it.text == "[drill_demon.done]" }
        closeModal()
        over()
    }

    @Test
    fun `the sergeant's camp, four exercises right in a row, and back with a camo piece, then a lamp and the emotes`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                val (dx, dz, first) = client.intoCamp("alice")
                assertTrue(dx != 0 || dz != 0, "in an instance")
                client.drill(dx, dz, first)
                client.await("back where they came from") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.await("the top first") { if (client.has(DrillDemon.TOP) == 1) Unit else null }
                client.await("the signs blank") { if (client.varp(MACRO_TRANSMIT) == 0) Unit else null }

                // With all three had, a lamp and the emotes.
                client.command("item ${DrillDemon.BOTTOMS}")
                client.command("item ${DrillDemon.HELM}")
                client.await("all three") { if (DrillDemon.PIECES.all { client.has(it) > 0 }) Unit else null }
                assertEquals(0, client.varp(EMOTE_ACCESS) and EMOTE_BIT)
                client.awaitTicks(2)
                val (dx2, dz2, first2) = client.intoCamp("alice")
                // Shutting his last words loses nothing: the player is back and rewarded before them.
                client.drill(dx2, dz2, first2, shut = true)
                client.await("back again") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.await("the lamp") { if (client.has(XpLamp.LAMP) == 1) Unit else null }
                client.await("the emotes") { if (client.varp(EMOTE_ACCESS) and EMOTE_BIT != 0) Unit else null }
                client.await("the camp ended") { if (server.world.instances.all.isEmpty()) Unit else null }
                client.awaitTicks(2)
                assertEquals(1, client.has(XpLamp.LAMP))
            }
        }
    }

    @Test
    fun `with a full backpack the piece goes on the ground back home, not in the camp`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                val (dx, dz, first) = client.intoCamp("carol")
                client.drill(dx, dz, first) {
                    client.command("item $BEER 28")
                    client.await("a full backpack") { if (client.inventory(Inventory.INV).count { it != null } == 28) Unit else null }
                }
                client.await("back") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                val obj = client.await("the top on the ground") { client.groundObjs().firstOrNull { it.id == DrillDemon.TOP } }
                assertEquals(Coord(3222, 3218, 0), obj.coord)
            }
        }
    }

    @Test
    fun `no thanks sends him off, a mat before the order is refused, and leaving early gives nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.command("randomevent ${DrillDemon.key}")
                val npc = client.await("the sergeant") { client.npcs().firstOrNull { it.id == DrillDemon.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[drill_demon.recommended name=bob]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.chat("[drill_demon.not_interested]")
                client.over()
                client.await("him gone") { if (client.npcs().none { it.id == DrillDemon.npcId }) Unit else null }

                client.awaitTicks(2)
                val (dx, dz, _) = client.intoCamp("bob")
                // Talking to him gives the order again; walking off from it leaves none given.
                val sergeant = client.await("the camp's sergeant") { client.npcs().firstOrNull { it.id == DrillDemon.CAMP_SERGEANT } }
                client.npcOp(sergeant.index, RandomEvents.TALK_TO)
                client.awaitDialogue("his order again") { it is DialogueView.Chat && it.text.startsWith("[drill_demon.order_") }
                client.useMat(1, dx, dz)
                client.chat("[drill_demon.not_yet]")
                client.order(answer = 2)
                client.chat("[drill_demon.leave]")
                client.chat("[drill_demon.pathetic]")
                client.await("back") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.awaitTicks(2)
                assertTrue(client.inventory(Inventory.INV).all { it == null })
                assertTrue(server.world.instances.all.isEmpty(), "the camp ended")
            }
        }
    }

    @Test
    fun `the signs blank after a teleport out, and a blocked tile sends them beside it, by the way back and by a logout`(@TempDir dir: Path) {
        val collision = CollisionMap()
        server(dir, collision).use { server ->
            server.login("dan").use { client ->
                client.ready()
                client.intoCamp("dan")
                client.await("the signs") { if (client.varp(MACRO_TRANSMIT) != 0) Unit else null }
                // Out by a teleport, not his way back: the signs still blank (exploit review R51 A-2).
                client.command("tele 3222 3218 0")
                client.await("out") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.await("the signs blank") { if (client.varp(MACRO_TRANSMIT) == 0) Unit else null }

                // The tile they came from blocked meanwhile: back beside it, not at Lumbridge (R51 C-4).
                client.awaitTicks(2)
                client.command("tele 3230 3218 0")
                client.await("the first tile") { client.position?.takeIf { it == Coord(3230, 3218, 0) } }
                client.intoCamp("dan")
                collision.set(3230, 3218, 0, CollisionFlag.LOC)
                val sergeant = client.await("the camp's sergeant") { client.npcs().firstOrNull { it.id == DrillDemon.CAMP_SERGEANT } }
                client.npcOp(sergeant.index, RandomEvents.TALK_TO)
                client.order(answer = 2)
                client.chat("[drill_demon.leave]")
                client.chat("[drill_demon.pathetic]")
                client.await("beside it") { client.position?.takeIf { it == Coord(3230, 3219, 0) } }

                // The same rule for a logout in the camp.
                client.awaitTicks(2)
                client.command("tele 3234 3218 0")
                client.await("the second tile") { client.position?.takeIf { it == Coord(3234, 3218, 0) } }
                client.intoCamp("dan")
                collision.set(3234, 3218, 0, CollisionFlag.LOC)
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            val until = System.currentTimeMillis() + 10_000
            while (server.world.playerNamed("dan") != null && System.currentTimeMillis() < until) Thread.sleep(20)
            server.login("dan").use { again ->
                again.await("saved beside it") { again.position?.takeIf { it == Coord(3234, 3219, 0) } }
            }
        }
    }

    private companion object {
        const val MACRO_TRANSMIT = 531
        const val EMOTE_ACCESS = 313
        const val EMOTE_BIT = 1 shl 11
        const val BEER = 1917
        val SIGNS = mapOf("jog" to 1, "situps" to 2, "pushups" to 3, "starjumps" to 4)
    }
}
