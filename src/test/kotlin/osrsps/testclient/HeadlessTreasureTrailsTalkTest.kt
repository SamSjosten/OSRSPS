package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InvObj
import osrsps.game.InventoryTab
import osrsps.game.content.Emotes
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.game.trail.ClueStep
import osrsps.game.trail.StepType
import osrsps.game.trail.TrailChallenge
import osrsps.game.trail.TrailData
import osrsps.game.trail.TrailOutfit
import osrsps.game.trail.TrailTarget
import osrsps.game.trail.TrailTier
import osrsps.game.trail.TrailTile
import osrsps.game.trail.TreasureTrails
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap

/**
 * Treasure Trails step TT3 through the headless client: Talk-to on a talk step's NPC gives the next clue instead of the
 * NPC's own talk, Search on a search step's loc does the same, and an NPC with a challenge gives its challenge scroll
 * first, takes a wrong answer with no penalty, and the right one for the next clue. Made-up steps: the real ones come
 * from the cache.
 */
class HeadlessTreasureTrailsTalkTest {
    private val talk = ClueStep(StepType.CRYPTIC, 1, TrailTier.EASY, "Talk to a made-up man.", listOf(TrailTarget.Npc(TALKER, -1, TrailTile(3224, 3220, 0))), items = listOf(TALK_ITEM))
    private val search = ClueStep(StepType.CRYPTIC, 2, TrailTier.EASY, "Search a made-up crate.", listOf(TrailTarget.Loc(CRATE, -1, TrailTile(CRATE_X, CRATE_Z, 0))), items = listOf(SEARCH_ITEM))
    private val anagram = ClueStep(
        StepType.ANAGRAM, 3, TrailTier.MEDIUM, "A MADE UP ANAGRAM", listOf(TrailTarget.Npc(ASKER, -1, TrailTile(3220, 3220, 0))),
        items = listOf(ANAGRAM_ITEM), challenge = TrailChallenge.Question("How many made-up things?", setOf(19, 20)), challengeItem = CHALLENGE,
    )
    private val far = ClueStep(StepType.CRYPTIC, 4, TrailTier.MEDIUM, "Far away.", listOf(TrailTarget.Npc(ASKER, -1, TrailTile(2600, 3000, 0))), items = listOf(FAR_ITEM))

    // A beginner emote in a hat, and an elite one of two emotes with no outfit: each its tier's only step.
    private val bow = ClueStep(
        StepType.EMOTE, 5, TrailTier.BEGINNER, "Bow in a made-up hat.", listOf(TrailTarget.Dig(TrailTile(3222, 3222, 0))),
        emotes = listOf(BOW), outfits = listOf(TrailOutfit("a made-up hat", items = mapOf(HEAD_SLOT to setOf(HAT)))),
    )
    private val cheerThenDance = ClueStep(StepType.EMOTE, 6, TrailTier.ELITE, "Cheer, then dance.", listOf(TrailTarget.Dig(TrailTile(3222, 3222, 0))), items = listOf(ELITE_ITEM), emotes = listOf(CHEER, DANCE))

    private val crate = ZoneLoc(CRATE, LocShape.CENTREPIECE_STRAIGHT, 0, CRATE_X, CRATE_Z, 0)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(25_000)
        val read = listOf("Read", "Check steps", null, null, "Drop")
        objs[HAT] = ObjType(HAT, "Made-up hat")
        objs[TrailTier.BEGINNER.sharedClue] = ObjType(TrailTier.BEGINNER.sharedClue, "Clue scroll (beginner)", iops = read, params = mapOf(TrailTier.BEGINNER.param to 1))
        for ((id, tier) in listOf(TALK_ITEM to 347, SEARCH_ITEM to 347, ANAGRAM_ITEM to 348, FAR_ITEM to 348, ELITE_ITEM to 350)) objs[id] = ObjType(id, "Clue scroll", iops = read, params = mapOf(tier to 1))
        objs[CHALLENGE] = ObjType(CHALLENGE, "Challenge scroll (medium)", iops = listOf("Read", null, null, null, "Drop"))
        for (tier in TrailTier.entries) objs[tier.rewardCasket] = ObjType(tier.rewardCasket, "Reward casket (${tier.key})", stackable = true, iops = listOf("Open", null, null, null, "Drop"))
        val talkTo = listOf("Talk-to", null, null, null, null)
        val uris = listOf(TrailTier.BEGINNER, TrailTier.ELITE).map { NpcType(TreasureTrails.URI.getValue(it), "Uri", ops = talkTo) }
        val types = NpcTypes.of(NpcType(TALKER, "Made-up man", ops = talkTo), NpcType(ASKER, "Made-up asker", ops = talkTo), *uris.toTypedArray())
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            locTypes = LocTypes(mapOf(CRATE to LocType(CRATE, "Crates", ops = listOf("Search", null, null, null, null), collision = LocCollisionType(width = 1, length = 1)))),
            staticLocs = StaticLocs().apply { add(crate) },
            npcTypes = types, npcSpawns = listOf(NpcSpawn(types[TALKER]!!, 3224, 3220, wander = 0, source = "test"), NpcSpawn(types[ASKER]!!, 3220, 3220, wander = 0, source = "test")),
            trailData = TrailData(listOf(talk, search, anagram, far, bow, cheerThenDance)), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.backpack() = inventory(BACKPACK)

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { backpack().indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.talkTo(server: TestServer, type: Int) = npcOp(server.world.npcs.all.first { it.type.id == type }.index, 1)

    private fun HeadlessClient.chat(key: String) {
        await(key) { (dialogueView as? DialogueView.Chat)?.takeIf { it.text == "[$key]" } }
        continueDialogue()
    }

    private fun HeadlessClient.objbox(key: String) {
        await(key) { if (dialogueView is DialogueView.Other && text(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_TEXT) == "[$key]") Unit else null }
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
    }

    @Test
    fun `Talk-to on the step's NPC gives the next clue, and Search on the step's crate the casket`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fay").use { client ->
                client.await("the login rebuild") { client.position }
                // The easy pool is the talk and the search step, so the trail is both.
                client.commandReply("clue easy cryptic:1") { it.startsWith("Clue") }
                client.talkTo(server, TALKER)
                client.chat("trail.npc_clue")
                client.objbox("trail.another_clue")
                client.slotOf(SEARCH_ITEM)
                assertNull(client.backpack().firstOrNull { it?.id == TALK_ITEM })

                client.locOp(CRATE, CRATE_X, CRATE_Z, 1)
                client.objbox("trail.casket")
                client.slotOf(TrailTier.EASY.rewardCasket)
                assertEquals("No trail.", client.commandReply("clue") { it.startsWith("No") })
            }
        }
    }

    @Test
    fun `an NPC with a challenge gives its scroll, takes a wrong answer, and the right one for the next step`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gil").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("clue medium anagram:3") { it.startsWith("Clue") }
                client.talkTo(server, ASKER)
                client.chat("trail.npc_challenge")
                val scroll = client.slotOf(CHALLENGE)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 2, scroll, CHALLENGE)
                client.await("the question") { client.text(TreasureTrails.CLUE_TEXT, TreasureTrails.CLUE_TEXT_WORDS)?.takeIf { it == "How many made-up things?" } }
                client.closeModal()

                client.talkTo(server, ASKER)
                client.await("the answer prompt") { client.clientScripts().lastOrNull { it.id == ScriptContext.MESLAYER_MODE7 } }
                client.resumeCount(7)
                client.chat("trail.npc_wrong")
                client.slotOf(CHALLENGE)

                client.talkTo(server, ASKER)
                client.await("the answer prompt again") { client.clientScripts().count { it.id == ScriptContext.MESLAYER_MODE7 }.takeIf { it >= 2 } }
                client.resumeCount(20)
                client.await("trail.npc_right") { (client.dialogueView as? DialogueView.Chat)?.takeIf { it.text == "[trail.npc_right]" } }
                // Walking off before the step goes on keeps the challenge scroll and the step (Codex review on #863).
                val at = client.position!!
                client.walk(at.x + 2, at.z)
                client.awaitTicks(3)
                client.slotOf(CHALLENGE)
                client.slotOf(ANAGRAM_ITEM)

                client.talkTo(server, ASKER)
                client.await("the answer prompt a third time") { client.clientScripts().count { it.id == ScriptContext.MESLAYER_MODE7 }.takeIf { it >= 3 } }
                client.resumeCount(20)
                client.chat("trail.npc_right")
                // The medium pool is this one step (the other is outside Misthalin), so the trail ends here.
                client.objbox("trail.casket")
                assertTrue(client.backpack().none { it?.id == CHALLENGE || it?.id == ANAGRAM_ITEM })
                client.slotOf(TrailTier.MEDIUM.rewardCasket)
            }
        }
    }

    private fun HeadlessClient.emote(index: Int) = ifButton(Emotes.INTERFACE, Emotes.CONTENTS, 1, sub = index)

    private fun uri(server: TestServer, tier: TrailTier) = server.world.npcs.all.firstOrNull { it.type.id == TreasureTrails.URI.getValue(tier) }

    @Test
    fun `an emote at the spot in the outfit calls Uri, who gives the next clue, and in the wrong outfit he stays away`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("hal").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("clue beginner emote:5") { it.startsWith("Clue") }
                client.commandReply("tele 3223 3223") { it.startsWith("Teleported") }
                client.awaitTicks(2)
                client.emote(BOW)
                client.awaitTicks(3)
                assertNull(uri(server, TrailTier.BEGINNER), "no hat, no Uri")

                server.world.playerNamed("hal")!!.equipment[HEAD_SLOT] = InvObj(HAT, 1)
                client.emote(BOW)
                val uri = client.await("Uri") { uri(server, TrailTier.BEGINNER) }
                client.npcOp(uri.index, 1)
                client.chat("trail.uri_clue")
                client.objbox("trail.casket")
                client.slotOf(TrailTier.BEGINNER.rewardCasket)
                client.await("Uri gone") { if (uri(server, TrailTier.BEGINNER) == null) Unit else null }
            }
        }
    }

    @Test
    fun `a step of two emotes has Uri wait for the second before he gives the clue`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ida").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("clue elite emote:6") { it.startsWith("Clue") }
                client.commandReply("tele 3222 3221") { it.startsWith("Teleported") }
                client.awaitTicks(2)
                client.emote(CHEER)
                val uri = client.await("Uri") { uri(server, TrailTier.ELITE) }
                client.npcOp(uri.index, 1)
                client.chat("trail.uri_not_yet")
                client.emote(DANCE)
                client.awaitTicks(2)
                client.npcOp(uri.index, 1)
                client.chat("trail.uri_clue")
                client.objbox("trail.casket")
                client.slotOf(TrailTier.ELITE.rewardCasket)
            }
        }
    }

    private companion object {
        const val HEAD_SLOT = 0
        const val HAT = 1137
        const val ELITE_ITEM = 12073
        const val BOW = 2
        const val CHEER = 7
        const val DANCE = 12
        const val BACKPACK = 93
        const val TALKER = 14_990
        const val ASKER = 14_991
        const val CRATE = 49_990
        const val CRATE_X = 3226
        const val CRATE_Z = 3216
        const val TALK_ITEM = 2690
        const val SEARCH_ITEM = 2691
        const val ANAGRAM_ITEM = 2843
        const val CHALLENGE = 2842
        const val FAR_ITEM = 2844
    }
}
