package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.AdventurersCamp
import osrsps.game.content.CandleSeller
import osrsps.game.content.FatherUrhney
import osrsps.game.content.SwampTutors
import osrsps.game.dialogue.Chatbox
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Rest of Lumbridge step 9 through the headless client, with the real combat and drop files: a giant rat killed leaves
 * its bones and raw rat meat, Father Urhney talks, the camp's monk talks, the candle seller sells a lit candle, and the
 * Mining and Fishing tutors hand over a pickaxe and a net. The rl-client script test-runner/examples/lumbridge-swamp.json does the same in the real
 * client, in the swamp, and kills a giant frog for its big bones.
 */
class HeadlessLumbridgeSwampTest {
    private val giantRat = NpcType(
        GIANT_RAT, "Giant rat", ops = listOf(null, "Attack", null, null, null), combatLevel = 3, size = 2,
        internalName = "giantrat", stats = NpcStats(attack = 2, defence = 2, strength = 3, hitpoints = 5),
    )

    private val urhney = NpcType(FatherUrhney.ID, "Father Urhney", ops = listOf("Talk-to", null, null, null, null), internalName = "father_urhney")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(giantRat, urhney)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(9), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(giantRat, 3224, 3218, source = "test"), NpcSpawn(urhney, 3220, 3218, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    @Test
    fun `a giant rat killed drops its bones and raw rat meat`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("the giant rat in view") { client.npcs().firstOrNull { it.id == GIANT_RAT } }
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.npcOp(npc.index, 2)
                // Every hit is 0 or 1, so five of them, however the dice fall. It attacks first, being aggressive.
                client.await("the giant rat's death", timeoutMillis = 60_000) { if (client.npcs().none { it.id == GIANT_RAT }) Unit else null }
                val drops = client.await("the bones and the meat") {
                    client.groundObjs().map { it.id }.takeIf { BONES in it && RAW_RAT_MEAT in it }
                }
                assertEquals(1, drops.count { it == BONES })
                // 4 XP a point of its 5, and of any it got back: it heals a point a minute, and a fight of misses can last that long.
                val xp = client.stat(ATTACK)!!.xp
                assertTrue(xp >= 5 * 4 && xp % 4 == 0, "$xp XP")
            }
        }
    }

    @Test
    fun `Father Urhney tells the player to go, and the friendly remark gets the same`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val npc = client.await("Father Urhney in view") { client.npcs().firstOrNull { it.id == FatherUrhney.ID } }
                client.npcOp(npc.index, FatherUrhney.TALK_TO)
                val first = client.awaitDialogue("his first line") { it is DialogueView.Chat && it.text == "[father_urhney.go_away]" } as DialogueView.Chat
                assertEquals("Father Urhney", first.name)
                client.continueDialogue()
                val menu = client.awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[father_urhney.friendly]", "[father_urhney.lost_amulet]", "[father_urhney.repossess]"), menu.options)
                client.chooseOption(1)
                for (key in listOf("father_urhney.friendly", "father_urhney.still_go", "father_urhney.grouch")) {
                    client.awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
                    client.continueDialogue()
                }
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
            }
        }
    }

    @Test
    fun `the candle seller takes 1000 coins for a lit candle, and the camp's monk talks`(@TempDir dir: Path) {
        val talkTo = listOf("Talk-to", null, null, null, null)
        val seller = NpcType(CandleSeller.ID, "Candle seller", ops = talkTo, internalName = "swamp_candle_guy")
        val monk = NpcType(AdventurersCamp.MONK, "Monk", ops = talkTo, internalName = "monkadventurerpg")
        val coins = ObjType(CandleSeller.COINS, "Coins", cost = 1, stackable = true, tradeable = true)
        val candle = ObjType(CandleSeller.LIT_CANDLE, "Lit candle", tradeable = true)
        TestServer(dir) { crcs, millis ->
            World(
                crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(coins, candle),
                npcTypes = NpcTypes.of(seller, monk),
                npcSpawns = listOf(NpcSpawn(seller, 3220, 3218, wander = 0, source = "test"), NpcSpawn(monk, 3224, 3218, wander = 0, source = "test")),
                cycleMillis = millis,
            )
        }.use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("item ${CandleSeller.COINS} 1500") { it.startsWith("Added") }
                val npc = client.await("the candle seller in view") { client.npcs().firstOrNull { it.id == CandleSeller.ID } }
                client.npcOp(npc.index, CandleSeller.TALK_TO)
                client.awaitDialogue("the offer") { it is DialogueView.Chat && it.text == "[candle_seller.offer]" }
                client.continueDialogue()
                val menu = client.awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[candle_seller.yes]", "[candle_seller.thousand]", "[candle_seller.curse]"), menu.options)
                client.chooseOption(1)
                client.awaitDialogue("the player's yes") { it is DialogueView.Chat && it.text == "[candle_seller.yes]" }
                client.continueDialogue()
                client.awaitDialogue("the sale") { it is DialogueView.Chat && it.text == "[candle_seller.here]" }
                val bag = client.await("the candle in the bag and the coins taken") {
                    client.inventory(INVENTORY).filterNotNull().takeIf { objs -> objs.any { it.id == CandleSeller.LIT_CANDLE } }
                }
                assertEquals(500, bag.single { it.id == CandleSeller.COINS }.count)
                client.continueDialogue()
                client.awaitDialogue("the warning") { it is DialogueView.Chat && it.text == "[candle_seller.careful]" }
                client.continueDialogue()
                client.awaitDialogue("the last menu") { it is DialogueView.Menu }
                client.chooseOption(3)
                client.awaitDialogue("goodbye") { it is DialogueView.Chat && it.text == "[candle_seller.thanks_bye]" }
                client.continueDialogue()
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }

                val camp = client.await("the monk in view") { client.npcs().firstOrNull { it.id == AdventurersCamp.MONK } }
                client.npcOp(camp.index, AdventurersCamp.TALK_TO)
                val line = client.awaitDialogue("the monk's line") { it is DialogueView.Chat } as DialogueView.Chat
                assertEquals("Monk" to "[lost_city_monk.anymore]", line.name to line.text)
                client.continueDialogue()
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
            }
        }
    }

    @Test
    fun `the Mining tutor teaches the basics and hands over a bronze pickaxe`(@TempDir dir: Path) {
        val tutor = NpcType(SwampTutors.MINING_TUTOR, "Mining tutor", ops = listOf("Talk-to", null, null, null, null), internalName = "aide_tutor_mining")
        val pickaxe = ObjType(SwampTutors.BRONZE_PICKAXE, "Bronze pickaxe", tradeable = true)
        TestServer(dir) { crcs, millis ->
            World(
                crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(pickaxe),
                npcTypes = NpcTypes.of(tutor), npcSpawns = listOf(NpcSpawn(tutor, 3220, 3218, source = "test")), cycleMillis = millis,
            )
        }.use { server ->
            server.login("carol").use { client ->
                val npc = client.await("the Mining tutor in view") { client.npcs().firstOrNull { it.id == SwampTutors.MINING_TUTOR } }
                client.npcOp(npc.index, SwampTutors.TALK_TO)
                val menu = client.awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(
                    listOf("[mining_tutor.basics]", "[mining_tutor.quests]", "[mining_tutor.rocks_and_picks]", "[mining_tutor.goodbye]"),
                    menu.options,
                )
                client.chooseOption(1)
                client.awaitDialogue("the player's question") { it is DialogueView.Chat && it.text == "[mining_tutor.basics]" }
                client.continueDialogue()
                assertEquals(DialogueView.Other(Chatbox.OBJECTBOX), client.awaitDialogue("the mining site box") { it is DialogueView.Other })
                client.resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
                client.awaitDialogue("the pickaxe line") { it is DialogueView.Chat && it.text == "[mining_tutor.have_a_pick]" }
                client.await("the pickaxe in the bag") {
                    client.inventory(INVENTORY).firstOrNull { it?.id == SwampTutors.BRONZE_PICKAXE }
                }
            }
        }
    }

    @Test
    fun `the Fishing tutor teaches the basics and hands over a small fishing net`(@TempDir dir: Path) {
        val tutor = NpcType(SwampTutors.FISHING_TUTOR, "Fishing tutor", ops = listOf("Talk-to", null, null, null, null), internalName = "aide_tutor_fishing")
        val net = ObjType(SwampTutors.SMALL_FISHING_NET, "Small fishing net", tradeable = true)
        TestServer(dir) { crcs, millis ->
            World(
                crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(net),
                npcTypes = NpcTypes.of(tutor), npcSpawns = listOf(NpcSpawn(tutor, 3220, 3218, source = "test")), cycleMillis = millis,
            )
        }.use { server ->
            server.login("erin").use { client ->
                val npc = client.await("the Fishing tutor in view") { client.npcs().firstOrNull { it.id == SwampTutors.FISHING_TUTOR } }
                client.npcOp(npc.index, SwampTutors.TALK_TO)
                val menu = client.awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(
                    listOf("[fishing_tutor.basics]", "[fishing_tutor.fish]", "[fishing_tutor.where]", "[fishing_tutor.goodbye]"),
                    menu.options,
                )
                client.chooseOption(1)
                client.awaitDialogue("the player's question") { it is DialogueView.Chat && it.text == "[fishing_tutor.basics]" }
                client.continueDialogue()
                client.awaitDialogue("the net line") { it is DialogueView.Chat && it.text == "[fishing_tutor.lost_net]" }
                client.await("the net in the bag") {
                    client.inventory(INVENTORY).firstOrNull { it?.id == SwampTutors.SMALL_FISHING_NET }
                }
            }
        }
    }

    private companion object {
        const val INVENTORY = 93
        const val GIANT_RAT = 2856
        const val BONES = 526
        const val RAW_RAT_MEAT = 2134
        const val ATTACK = 0
    }
}
