package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.content.ChampionsChallenge
import osrsps.game.content.ChampionsChallenge.Champion
import osrsps.game.content.ChampionsChestLines
import osrsps.game.content.LarxusLines
import osrsps.game.dialogue.Chatbox
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Post-quest content step 9 through the headless client: the giant champion's scroll used on Larxus arranges the duel,
 * the statue opens and goes down to the arena, the champion comes in for its player only, beating it sets its bit,
 * takes the scroll and gives the XP, the ladder goes back up, and the Chest of Champions gives the lamp that is rubbed
 * for ten times the XP. The rl-client script test-runner/examples/champions-challenge.json does the same on the real
 * map.
 */
class HeadlessChampionsChallengeTest {
    private val larxus = NpcType(ChampionsChallenge.LARXUS, "Larxus", ops = listOf("Talk-to", null, null, null, null), internalName = "champions_doorman")
    private val giant = NpcType(
        Champion.GIANT.npc, "Giant Champion", size = 2, ops = listOf(null, "Attack", null, null, null), combatLevel = 56,
        internalName = "champions_giant", stats = NpcStats(attack = 40, defence = 40, strength = 40, hitpoints = 70),
    )

    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val statue = ZoneLoc(ChampionsChallenge.STATUE, LocShape.CENTREPIECE_STRAIGHT, 0, 3189, 9760, 0)
    private val ladder = ZoneLoc(ChampionsChallenge.ARENA_LADDER, LocShape.CENTREPIECE_STRAIGHT, 0, 3183, 9760, 0)
    private val chest = ZoneLoc(ChampionsChallenge.CHEST, LocShape.CENTREPIECE_STRAIGHT, 0, 3186, 9762, 0)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val npcTypes = NpcTypes.of(larxus, giant)
        World(
            crcs, CollisionMap().apply { markLoaded(3150 shr 6, 9740 shr 6) },
            random = GameRandom(5),
            varTypes = VarTypes(6_000, mapOf(CHEST_VARBIT to VarbitType(ChampionsChallenge.VARP_DEFEATED, 0, 15)), 21_000),
            objTypes = ObjTypes.of(
                ObjType(Champion.GIANT.scroll, "Giant champion scroll"),
                ObjType(ChampionsChallenge.LAMP, "Champion's lamp", iops = listOf("Rub", null, null, null, "Drop")),
            ),
            npcTypes = npcTypes,
            npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE),
            npcSpawns = listOf(NpcSpawn(larxus, 3187, 9758, source = "test")),
            locTypes = LocTypes(
                listOf(
                    type(ChampionsChallenge.STATUE, "Champion statue", "Open"),
                    type(ChampionsChallenge.STATUE_OPEN, "Champion statue", "Climb-down"),
                    type(ChampionsChallenge.ARENA_LADDER, "Ladder", "Climb-up"),
                    LocType(
                        ChampionsChallenge.CHEST, "null", List(5) { null }, LocCollisionType(width = 1, length = 1), multiloc = true,
                        multiVarbit = CHEST_VARBIT, multiLocs = listOf(CHEST_CLOSED, CHEST_OPEN),
                    ),
                    type(CHEST_CLOSED, "Chest", "Open"),
                    type(CHEST_OPEN, "Chest", "Search"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { listOf(statue, ladder, chest).forEach(::add) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int) = await("at $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.npcIndex(id: Int) = await("NPC $id in view") { npcs().firstOrNull { it.id == id } }.index

    private fun HeadlessClient.chat(line: Line) {
        awaitDialogue(line.key) { it is DialogueView.Chat && it.text == DialogueText.NONE[line] }
        continueDialogue()
    }

    private fun HeadlessClient.objbox(closes: Boolean = true) {
        awaitDialogue("the object box") { it == DialogueView.Other(Chatbox.OBJECTBOX) }
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
        if (closes) await("the object box closed") { if (dialogueView == null) Unit else null }
    }

    @Test
    fun `a scroll arranges the duel, the statue leads down to it, the win counts, and the chest's lamp gives ten times the XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val alice = server.world.playerNamed("alice")!!
                val challenge = server.world.championsChallenge
                client.command("tele 3188 9758 0")
                client.awaitAt(3188, 9758)

                // The statue won't open before a duel is arranged.
                client.locOp(statue.id, statue.x, statue.z, ChampionsChallenge.OPEN)
                client.chat(LarxusLines.ARRANGE_FIRST)

                client.commandReply("item ${Champion.GIANT.scroll} 1") { it.contains("Giant champion scroll") }
                client.npcTarget(client.npcIndex(larxus.id), InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(Champion.GIANT.scroll), Champion.GIANT.scroll)
                client.chat(LarxusLines.RULES.getValue(Champion.GIANT))
                client.awaitDialogue("yes or no") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat(LarxusLines.READY)
                assertEquals(Champion.GIANT, challenge.arranged(alice))
                assertEquals(1, client.count(Champion.GIANT.scroll), "the scroll is kept until the win")

                client.locOp(statue.id, statue.x, statue.z, ChampionsChallenge.OPEN)
                client.await("the statue open") { server.world.locs.find(ChampionsChallenge.STATUE_OPEN, statue.x, statue.z, 0) }
                client.locOp(ChampionsChallenge.STATUE_OPEN, statue.x, statue.z, ChampionsChallenge.CLIMB_DOWN)
                client.awaitAt(ChampionsChallenge.ARENA.x, ChampionsChallenge.ARENA.z)
                val champion = client.await("the champion in the arena") { challenge.fightOf(alice) }
                assertEquals(Champion.GIANT.npc, champion.type.id)

                val slayer = alice.skills.xp(Skill.SLAYER)
                client.commandReply("kill ${champion.index}") { it.startsWith("Killed") }
                client.await("the win") { if (challenge.defeated(alice, Champion.GIANT)) Unit else null }
                assertEquals(slayer + Champion.GIANT.xp, alice.skills.xp(Skill.SLAYER))
                client.await("the scroll taken") { if (client.count(Champion.GIANT.scroll) == 0) Unit else null }
                assertNull(challenge.arranged(alice))
                assertNull(challenge.fightOf(alice))
                assertEquals(listOf(Champion.GIANT), challenge.lampsWaiting(alice))

                client.command("tele ${ladder.x + 1} ${ladder.z} 0")
                client.awaitAt(ladder.x + 1, ladder.z)
                client.locOp(ladder.id, ladder.x, ladder.z, ChampionsChallenge.CLIMB_UP)
                client.awaitAt(ChampionsChallenge.GALLERY.x, ChampionsChallenge.GALLERY.z)

                // The chest gives one lamp, and none while one is held.
                client.locOp(chest.id, chest.x, chest.z, ChampionsChallenge.CHEST_OP)
                client.objbox()
                client.await("the lamp") { if (client.count(ChampionsChallenge.LAMP) == 1) Unit else null }
                client.locOp(chest.id, chest.x, chest.z, ChampionsChallenge.CHEST_OP)
                client.objbox()
                assertEquals(1, client.count(ChampionsChallenge.LAMP))

                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, RUB_BUTTON_OP, client.slotOf(ChampionsChallenge.LAMP), ChampionsChallenge.LAMP)
                client.objbox(closes = false)
                client.awaitDialogue("the lamp's menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                // The level-up box follows the XP box, so it doesn't close.
                client.objbox(closes = false)
                client.await("the lamp used") { if (client.count(ChampionsChallenge.LAMP) == 0) Unit else null }
                assertEquals(slayer + Champion.GIANT.xp * 11, alice.skills.xp(Skill.SLAYER))
                assertTrue(challenge.lampClaimed(alice, Champion.GIANT))
                assertEquals(emptyList(), challenge.lampsWaiting(alice))

                client.locOp(chest.id, chest.x, chest.z, ChampionsChallenge.CHEST_OP)
                client.await("nothing left") { client.messages().firstOrNull { it.text == DialogueText.NONE[ChampionsChestLines.NOTHING] } }
                assertFalse(challenge.leonWaiting(alice))
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val CHEST_VARBIT = 6278
        const val CHEST_CLOSED = 22456
        const val CHEST_OPEN = 31944
        const val RUB_BUTTON_OP = 2
    }
}
