package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Scenario
import osrsps.game.Scenarios
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.content.CatLines
import osrsps.game.content.Cats
import osrsps.game.content.CustomVars
import osrsps.game.dialogue.DialogueText
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Post-quest content step 13 through the headless client: a kitten dropped to follow and picked up again, its hunger
 * warned of and fed, its loneliness stroked away, growing into a cat, coming back after a logout, a cat chasing a rat
 * down, and shooing it away. The rl-client script test-runner/examples/cats.json does the same in RuneLite.
 */
class HeadlessCatsTest {
    private val ops = listOf("Pick-up", null, "Talk-to", "Chase", "Interact")
    private val kitten = NpcType(KITTEN_NPC, "Kitten", ops = ops, internalName = "kittenpet1")
    private val cat = NpcType(CAT_NPC, "Cat", ops = ops, internalName = "growncat")
    private val rat = NpcType(RAT_NPC, "Rat", ops = listOf(null, "Attack", null, null, null), combatLevel = 1, internalName = "rat", stats = osrsps.game.NpcStats(hitpoints = 2))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            random = GameRandom(3),
            varTypes = VarTypes(6_000, emptyMap(), 21_000),
            npcTypes = NpcTypes.of(kitten, cat, rat),
            objTypes = ObjTypes.of(
                ObjType(KITTEN_OBJ, "Pet kitten"), ObjType(CAT_OBJ, "Pet cat"),
                ObjType(SARDINE, "Raw sardine", certlink = NOTED_SARDINE), ObjType(NOTED_SARDINE, "Raw sardine", stackable = true, certlink = SARDINE, certtemplate = 799),
                ObjType(Cats.BALL_OF_WOOL, "Ball of wool"),
            ),
            npcSpawns = listOf(NpcSpawn(rat, 3230, 3218, source = "test")),
            // The rat's respawn as npc-combat.json has it, shorter than its death ticks.
            npcCombat = osrsps.game.combat.NpcCombat(mapOf(RAT_NPC to osrsps.game.combat.NpcCombatDef(
                4, 1, osrsps.game.combat.AttackType.STAB, 0, 0, osrsps.game.combat.NpcDefence(0, 0, 0, 0, 0, 0, 0),
                osrsps.game.combat.NpcCombatAnims.NONE, respawnTicks = 2, source = "test",
            ))),
            scenarios = Scenarios(listOf(Scenario.parse("bare", """{ "position": { "x": 3225, "z": 3218 } }"""))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.npcIndex(id: Int) = await("NPC $id in view") { npcs().firstOrNull { it.id == id } }.index

    private fun HeadlessClient.drop(id: Int) = ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, DROP_BUTTON_OP, slotOf(id), id)

    private fun HeadlessClient.message(line: osrsps.game.dialogue.Line, red: Boolean = false) {
        val text = DialogueText.NONE[line].let { if (red) "<col=${Cats.RED}>$it</col>" else it }
        await(line.key) { messages().firstOrNull { it.text == text } }
    }

    @Test
    fun `a kitten follows when dropped, eats when hungry, is stroked, grows into a cat and comes back after a logout`(@TempDir dir: Path) {
        server(dir).use { server ->
            val cats = server.world.cats
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val alice = server.world.playerNamed("alice")!!
                client.commandReply("item $KITTEN_OBJ 1") { it.contains("Pet kitten") }
                client.commandReply("item $SARDINE 1") { it.contains("Raw sardine") }

                client.drop(KITTEN_OBJ)
                val index = client.npcIndex(KITTEN_NPC)
                client.await("the kitten out") { if (client.count(KITTEN_OBJ) == 0) Unit else null }
                assertEquals(KITTEN_OBJ, alice.vars[Cats.FOLLOWER_OBJ])
                assertEquals(index, alice.vars[Cats.FOLLOWER_NPC])
                assertTrue(cats.hungerLeft(alice) > Cats.HUNGER_TICKS - 20, "fresh care")

                // A second cat can't come out with the first.
                client.commandReply("item $KITTEN_OBJ 1") { it.contains("Pet kitten") }
                client.drop(KITTEN_OBJ)
                client.message(CatLines.ALREADY_FOLLOWER)

                // It follows: walk off and it comes along.
                client.command("tele 3226 3222 0")
                client.await("the kitten beside alice") {
                    client.npcs().firstOrNull { it.id == KITTEN_NPC }?.coord?.takeIf { maxOf(kotlin.math.abs(it.x - 3226), kotlin.math.abs(it.z - 3222)) <= 1 }
                }

                // Hungry two ticks on; the sardine feeds it.
                client.command("varp ${CustomVars.CAT_CARE} ${(1 shl 30) or (Cats.HUNGRY_AT + 2) or (Cats.ATTENTION_TICKS shl 12)}")
                client.message(CatLines.HUNGRY, red = true)
                client.npcTarget(index, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(SARDINE), SARDINE)
                client.message(CatLines.GOBBLES)
                client.await("the sardine eaten") { if (client.count(SARDINE) == 0) Unit else null }
                assertTrue(cats.hungerLeft(alice) > Cats.HUNGER_TICKS - 10)

                // A banknote of a fish is no food: it stays, and the kitten's hunger runs on.
                client.commandReply("item $NOTED_SARDINE 1") { it.contains("Raw sardine") }
                val before = cats.hungerLeft(alice)
                client.npcTarget(index, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(NOTED_SARDINE), NOTED_SARDINE)
                client.awaitTicks(3)
                assertEquals(1L, client.count(NOTED_SARDINE).toLong(), "the note kept")
                assertTrue(cats.hungerLeft(alice) < before, "not fed")

                // Lonely two ticks on; a stroke from Interact holds it off 18 minutes.
                client.command("varp ${CustomVars.CAT_CARE} ${(1 shl 30) or Cats.HUNGER_TICKS or ((Cats.ATTENTION_AT + 2) shl 12)}")
                client.message(CatLines.ATTENTION, red = true)
                client.npcOp(index, Cats.INTERACT)
                client.awaitDialogue("the interact menu") { it is DialogueView.Menu && it.options.size == 4 }
                client.chooseOption(1)
                client.message(CatLines.STROKED)
                assertTrue(cats.attentionLeft(alice) > Cats.STROKE_TICKS - 10)
                assertTrue(cats.attentionLeft(alice) <= Cats.STROKE_TICKS, "a first stroke is the 18 minutes")
                repeat(3) {
                    client.awaitDialogue("the stroke's talk") { it is DialogueView.Chat }
                    client.continueDialogue()
                }

                // Stroked again while the first holds, after its talk ran the timer down: the 25 minutes.
                client.npcOp(index, Cats.INTERACT)
                client.awaitDialogue("the interact menu again") { it is DialogueView.Menu && it.options.size == 4 }
                client.chooseOption(1)
                client.await("the second stroke") { Unit.takeIf { cats.attentionLeft(alice) > Cats.STROKE_TICKS } }
                assertTrue(cats.attentionLeft(alice) > Cats.ATTENTION_TICKS - 10)
                repeat(3) {
                    client.awaitDialogue("the second stroke's talk") { it is DialogueView.Chat }
                    client.continueDialogue()
                }

                // Pick-up puts it back; dropped again, it is the same kitten.
                client.command("empty")
                client.await("the backpack empty") { if (client.count(KITTEN_OBJ) == 0 && client.count(SARDINE) == 0) Unit else null }
                // ::empty may close a page as it is read here, so a page gone in between is no failure.
                client.dialogueView?.let { runCatching { client.continueDialogue() } }
                client.npcOp(index, Cats.PICK_UP)
                client.await("the kitten picked up") { if (client.count(KITTEN_OBJ) == 1) Unit else null }
                assertEquals(-1, alice.vars[Cats.FOLLOWER_OBJ])
                assertNull(cats.follower(alice))

                // A cycle from grown: out for 150 ticks, it grows into a cat.
                client.command("varp ${CustomVars.CAT} ${Cats.GROWN_CYCLES - 1}")
                client.drop(KITTEN_OBJ)
                client.awaitDialogue("the grown box", timeoutMillis = 30_000) { it == DialogueView.Message(DialogueText.NONE[CatLines.GROWN]) }
                client.continueDialogue()
                assertEquals(Cats.Stage.CAT, cats.followerKind(alice)?.stage)
                assertEquals(CAT_OBJ, alice.vars[Cats.FOLLOWER_OBJ])
                client.npcIndex(CAT_NPC)
            }
            val deadline = System.currentTimeMillis() + 10_000
            while (server.world.playerNamed("alice") != null && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertTrue(server.world.npcs.all.none { it.type.id == CAT_NPC }, "the cat goes with its player")

            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.npcIndex(CAT_NPC)
                val alice = server.world.playerNamed("alice")!!
                assertEquals(Cats.Stage.CAT, cats.followerKind(alice)?.stage)
            }
        }
    }

    @Test
    fun `a cat chases a rat down and is shooed away`(@TempDir dir: Path) {
        server(dir).use { server ->
            val cats = server.world.cats
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val bob = server.world.playerNamed("bob")!!
                client.command("tele 3228 3218 0")
                client.await("at the rat") { client.position?.takeIf { it == Coord(3228, 3218, 0) } }
                client.commandReply("item $CAT_OBJ 1") { it.contains("Pet cat") }
                client.drop(CAT_OBJ)
                val index = client.npcIndex(CAT_NPC)

                // Half the time a cat catches the rat; it is back after its respawn to try again. Each rat is hurt
                // first, so the one back after a catch shows it is a fresh rat at full hitpoints.
                var tries = 0
                while (cats.ratsCaught(bob) == 0 && tries++ < 20) {
                    server.world.npcs.all.filter { it.type.id == RAT_NPC }.forEach { it.hitpoints = 1 }
                    client.npcOp(index, Cats.CHASE)
                    client.await("the chase over", timeoutMillis = 10_000) {
                        client.messages().lastOrNull()?.takeIf { cats.ratsCaught(bob) > 0 || it.text == DialogueText.NONE[CatLines.GOT_AWAY] }
                    }
                    client.awaitTicks(Cats.RAT_RESPAWN_TICKS + 2)
                }
                assertEquals(1, cats.ratsCaught(bob))
                client.await("a fresh rat back") {
                    server.world.npcs.all.firstOrNull { it.type.id == RAT_NPC && !it.dead && it.hitpoints == rat.stats.hitpoints }
                }

                client.npcOp(index, Cats.INTERACT)
                client.awaitDialogue("the interact menu") { it is DialogueView.Menu && it.options.size == 3 }
                client.chooseOption(3)
                client.awaitDialogue("are you sure") { it is DialogueView.Menu && it.title == DialogueText.NONE[CatLines.SURE_SHOO] }
                client.chooseOption(1)
                repeat(2) {
                    client.awaitDialogue("the shooing") { it is DialogueView.Chat }
                    client.continueDialogue()
                }
                client.message(CatLines.RUN_AWAY)
                assertNull(cats.follower(bob))
                assertEquals(-1, bob.vars[Cats.FOLLOWER_OBJ])
                client.await("the cat gone") { if (server.world.npcs.all.none { it.type.id == CAT_NPC }) Unit else null }
                assertEquals(1, cats.ratsCaught(bob), "the count stays")
            }
        }
    }

    @Test
    fun `a follower with no room in the world when it catches up goes back in the backpack, or under a player whose backpack is full`(@TempDir dir: Path) {
        server(dir).use { server ->
            val cats = server.world.cats
            server.login("eve").use { client ->
                client.await("the login rebuild") { client.position }
                val eve = server.world.playerNamed("eve")!!
                client.commandReply("item $CAT_OBJ 1") { it.contains("Pet cat") }
                client.drop(CAT_OBJ)
                client.await("the cat following") { cats.follower(eve) }

                // A tile in the instances' area with no instance there: no NPC can be put beside them (World.spawnNpc).
                server.world.npcScripts.later(1) { server.world.teleport(eve, NO_ROOM_X, NO_ROOM_Z, 0) }
                client.await("the cat back in the backpack") { if (client.count(CAT_OBJ) == 1) Unit else null }
                assertNull(cats.follower(eve))
                assertEquals(-1, eve.vars[Cats.FOLLOWER_OBJ])
                assertEquals(-1, eve.vars[Cats.FOLLOWER_NPC])
                assertTrue(server.world.npcs.all.none { it.type.id == CAT_NPC }, "no cat left behind")

                // Again with a full backpack: the cat lands under them instead (exploit review 64, R64A-5).
                server.world.npcScripts.later(1) { server.world.teleport(eve, 3222, 3218, 0) }
                client.await("back home") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.drop(CAT_OBJ)
                client.await("the cat following again") { cats.follower(eve) }
                client.commandReply("item $SARDINE 28") { it.contains("Raw sardine") }
                client.await("a full backpack") { if (client.count(SARDINE) == 28) Unit else null }
                server.world.npcScripts.later(1) { server.world.teleport(eve, NO_ROOM_X, NO_ROOM_Z, 0) }
                client.await("the cat under eve") {
                    server.world.groundObjs.find(eve, CAT_OBJ, NO_ROOM_X, NO_ROOM_Z, 0)
                }
                assertEquals(0L, client.count(CAT_OBJ).toLong())
                assertNull(cats.follower(eve))
                assertEquals(-1, eve.vars[Cats.FOLLOWER_OBJ])
            }
        }
    }

    @Test
    fun `Chase vermin leaves the Rat Pits' rats alone`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dan").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 3222 3230 0")
                client.await("away from the test rat") { client.position?.takeIf { it == Coord(3222, 3230, 0) } }
                server.world.npcScripts.later(1) {
                    server.world.spawnNpc(NpcSpawn(rat, 3224, 3230, source = osrsps.game.content.RatPits.RAT_SOURCE, respawns = false))
                }
                client.await("the pit rat out") { server.world.npcs.all.firstOrNull { it.spawn.source == osrsps.game.content.RatPits.RAT_SOURCE } }
                client.commandReply("item $CAT_OBJ 1") { it.contains("Pet cat") }
                client.drop(CAT_OBJ)
                client.npcOp(client.npcIndex(CAT_NPC), Cats.CHASE)
                client.message(CatLines.NO_VERMIN)
            }
        }
    }

    @Test
    fun `a reset puts back the cat that followed before the scenario, and a kitten from the scenario goes`(@TempDir dir: Path) {
        server(dir).use { server ->
            val cats = server.world.cats
            server.login("cal").use { client ->
                client.await("the login rebuild") { client.position }
                val cal = server.world.playerNamed("cal")!!
                client.commandReply("item $CAT_OBJ 1") { it.contains("Pet cat") }
                client.drop(CAT_OBJ)
                client.await("the cat following") { cats.follower(cal) }

                client.command("scenario bare")
                client.await("at the scenario's tile") { client.position?.takeIf { it == Coord(3225, 3218, 0) } }
                // The cat from before stays out of the scenario, so it can't be picked up there and come back twice
                // (exploit review 64, R64A-1).
                client.await("the cat gone") { if (server.world.npcs.all.none { it.type.id == CAT_NPC }) Unit else null }
                assertNull(cats.follower(cal))
                assertEquals(-1, cal.vars[Cats.FOLLOWER_OBJ])
                client.commandReply("item $KITTEN_OBJ 1") { it.contains("Pet kitten") }
                client.drop(KITTEN_OBJ)
                client.await("the kitten following") { cats.follower(cal)?.takeIf { cats.followerKind(cal)?.stage == Cats.Stage.KITTEN } }

                client.command("reset")
                client.await("the cat back") { cats.follower(cal)?.takeIf { it.type.id == CAT_NPC } }
                assertEquals(CAT_OBJ, cal.vars[Cats.FOLLOWER_OBJ])
                client.await("the kitten gone") { if (server.world.npcs.all.none { it.type.id == KITTEN_NPC }) Unit else null }

                // A saved cat not out yet (as one waiting at login for room in the world) is still their follower:
                // another dropped is refused, and the saved one is kept.
                server.world.npcScripts.later(1) { cats.logout(cal) }
                client.await("the cat out of the world") { if (cats.follower(cal) == null) Unit else null }
                client.commandReply("item $KITTEN_OBJ 1") { it.contains("Pet kitten") }
                client.drop(KITTEN_OBJ)
                client.message(CatLines.ALREADY_FOLLOWER)
                assertNull(cats.follower(cal))
                assertEquals(CAT_OBJ, cal.vars[Cats.FOLLOWER_OBJ])
                assertTrue(cats.followingCat(cal), "Gertrude counts it as owned")
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val DROP_BUTTON_OP = 7
        const val KITTEN_OBJ = 1555
        const val CAT_OBJ = 1561
        const val KITTEN_NPC = 5591
        const val CAT_NPC = 1619
        const val RAT_NPC = 2854
        const val SARDINE = 327
        const val NOTED_SARDINE = 328
        const val NO_ROOM_X = 6_500
        const val NO_ROOM_Z = 100
    }
}
