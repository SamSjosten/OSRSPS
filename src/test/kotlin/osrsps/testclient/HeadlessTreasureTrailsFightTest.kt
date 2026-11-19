package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InvObj
import osrsps.game.InventoryTab
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.content.Emotes
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.trail.ClueStep
import osrsps.game.trail.StepType
import osrsps.game.trail.TrailData
import osrsps.game.trail.TrailTarget
import osrsps.game.trail.TrailTier
import osrsps.game.trail.TrailTile
import osrsps.game.trail.TreasureTrails
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Treasure Trails step TT7 through the headless client: Dig on a coordinate with a wizard brings him out and gives
 * nothing until he is dead, then the next clue; an emote step's double agent comes instead of Uri, who comes once it is dead; a key step's
 * monster drops the key for the player holding its clue, and the chest wants the key. The fighters are the real npc
 * types' names (npc-combat.json has them); the steps are made up.
 */
class HeadlessTreasureTrailsFightTest {
    private val wizard = NpcType(WIZARD, "Saradomin wizard", ops = listOf(null, "Attack", null, null, null), combatLevel = 108, internalName = "trail_hard2", stats = NpcStats(attack = 1, defence = 1, strength = 1, hitpoints = 120))
    private val agent = NpcType(AGENT, "Double agent", ops = listOf(null, "Attack", null, null, null), combatLevel = 108, internalName = "double_agent_2", stats = NpcStats(attack = 1, defence = 1, strength = 1, hitpoints = 120))
    private val man = NpcType(MAN, "Man", ops = listOf(null, "Attack", null, null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))
    private val uri = NpcType(TreasureTrails.URI.getValue(TrailTier.HARD), "Uri", ops = listOf("Talk-to", null, null, null, null))

    private val coordinate = ClueStep(
        StepType.COORDINATE, 1, TrailTier.HARD, "00.01N 00.01E", listOf(TrailTarget.Dig(TrailTile(3222, 3222, 0))),
        items = listOf(COORD_CLUE), combat = true, combatNpcs = listOf(WIZARD),
    )
    private val emote = ClueStep(
        StepType.EMOTE, 2, TrailTier.HARD, "Bow, then fight.", listOf(TrailTarget.Dig(TrailTile(3222, 3222, 0))),
        items = listOf(EMOTE_CLUE), emotes = listOf(BOW), combat = true, combatNpcs = listOf(AGENT),
    )
    private val key = ClueStep(
        StepType.CRYPTIC, 3, TrailTier.MEDIUM, "A made-up chest.", listOf(TrailTarget.Key(CHEST, TrailTile(CHEST_X, CHEST_Z, 0), listOf(MAN), KEY)),
        items = listOf(KEY_CLUE),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(25_000)
        val read = listOf("Read", "Check steps", null, null, "Drop")
        for ((id, param) in listOf(COORD_CLUE to TrailTier.HARD.param, EMOTE_CLUE to TrailTier.HARD.param, KEY_CLUE to TrailTier.MEDIUM.param)) objs[id] = ObjType(id, "Clue scroll", iops = read, params = mapOf(param to 1))
        objs[TreasureTrails.SPADE] = ObjType(TreasureTrails.SPADE, "Spade", iops = listOf("Dig", null, null, null, "Drop"))
        objs[KEY] = ObjType(KEY, "Key (medium)")
        for (tier in TrailTier.entries) objs[tier.rewardCasket] = ObjType(tier.rewardCasket, "Reward casket (${tier.key})", stackable = true, iops = listOf("Open", null, null, null, "Drop"))
        val types = NpcTypes.of(wizard, agent, man, uri)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3228, 3218, source = "test")), npcCombat = NpcCombat.load(types, SeqNames.NONE),
            locTypes = LocTypes(mapOf(CHEST to LocType(CHEST, "Closed chest", ops = listOf("Open", null, null, null, null), collision = LocCollisionType(width = 1, length = 1)))),
            staticLocs = StaticLocs().apply { add(ZoneLoc(CHEST, LocShape.CENTREPIECE_STRAIGHT, 0, CHEST_X, CHEST_Z, 0)) },
            trailData = TrailData(listOf(coordinate, emote, key)), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.op(id: Int, op: Int) = ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, op + 1, slotOf(id), id)

    private fun HeadlessClient.objbox(key: String) {
        await(key) { if (dialogueView is DialogueView.Other && text(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_TEXT) == "[$key]") Unit else null }
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
    }

    private fun npc(server: TestServer, type: Int) = server.world.npcs.all.firstOrNull { it.type.id == type && !it.dead }

    @Test
    fun `a coordinate's wizard comes out at the first dig, and the dig gives the next clue only once he is dead`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("max").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god on") { true }
                client.commandReply("clue hard coordinate:1") { it.startsWith("Clue") }
                client.command("item ${TreasureTrails.SPADE}")
                client.commandReply("tele 3222 3222") { it.startsWith("Teleported") }
                client.awaitTicks(2)
                client.op(TreasureTrails.SPADE, 1)
                val wizard = client.await("the wizard") { npc(server, WIZARD) }
                client.op(TreasureTrails.SPADE, 1)
                client.awaitTicks(2)
                assertEquals(1, server.world.npcs.all.count { it.type.id == WIZARD && !it.dead }, "one wizard")
                assertTrue(client.inventory(BACKPACK).any { it?.id == COORD_CLUE }, "no casket while he lives")

                client.commandReply("kill ${wizard.index}") { it.startsWith("Killed") }
                client.await("the fight won") { if (server.world.playerNamed("max")!!.trails[TrailTier.HARD]?.fought == true) Unit else null }
                client.op(TreasureTrails.SPADE, 1)
                // The hard pool is this step and the emote, so the trail goes on to the emote.
                client.objbox("trail.another_clue")
                client.slotOf(EMOTE_CLUE)
                assertEquals(false, server.world.playerNamed("max")!!.trails[TrailTier.HARD]?.fought, "the emote's fight is still to come")
            }
        }
    }

    @Test
    fun `an emote's double agent comes before Uri, who comes to the emote once it is dead`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ned").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god on") { true }
                client.commandReply("clue hard emote:2") { it.startsWith("Clue") }
                client.commandReply("tele 3223 3223") { it.startsWith("Teleported") }
                client.awaitTicks(2)
                client.ifButton(Emotes.INTERFACE, Emotes.CONTENTS, 1, sub = BOW)
                val agent = client.await("the double agent") { npc(server, AGENT) }
                assertNull(npc(server, uri.id), "no Uri yet")
                client.commandReply("kill ${agent.index}") { it.startsWith("Killed") }
                client.await("the fight won") { if (server.world.playerNamed("ned")!!.trails[TrailTier.HARD]?.fought == true) Unit else null }
                client.awaitTicks(3)
                client.ifButton(Emotes.INTERFACE, Emotes.CONTENTS, 1, sub = BOW)
                val uri = client.await("Uri") { npc(server, uri.id) }
                client.npcOp(uri.index, 1)
                client.await("Uri's clue") { (client.dialogueView as? DialogueView.Chat)?.takeIf { it.text == "[trail.uri_clue]" } }
            }
        }
    }

    @Test
    fun `a key step's monster drops the key for the clue's holder, and the chest opens only with it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("oz").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("clue medium cryptic:3") { it.startsWith("Clue") }
                val from = client.messages().size
                client.locOp(CHEST, CHEST_X, CHEST_Z, 1)
                client.await("locked") { client.messages().drop(from).firstOrNull { it.text == "[trail.locked]" } }

                val man = npc(server, MAN)!!
                val (x, z) = man.x to man.z
                client.commandReply("kill ${man.index}") { it.startsWith("Killed") }
                val player = server.world.playerNamed("oz")!!
                client.await("the key") { server.world.groundObjs.visibleAt(player, x, z, 0).firstOrNull { it.id == KEY } }

                player.inventory[20] = InvObj(KEY, 1)
                client.locOp(CHEST, CHEST_X, CHEST_Z, 1)
                client.objbox("trail.casket")
                assertEquals(0L, player.inventory.count(KEY))
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val WIZARD = 2955
        const val AGENT = 1778
        const val MAN = 3106
        const val CHEST = 2_000
        const val CHEST_X = 3_224
        const val CHEST_Z = 3_224
        const val KEY = 2_832
        const val COORD_CLUE = 2_723
        const val EMOTE_CLUE = 2_725
        const val KEY_CLUE = 2_831
        const val BOW = 2
    }
}
