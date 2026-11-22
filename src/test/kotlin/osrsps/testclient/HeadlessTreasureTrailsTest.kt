package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.combat.DropItem
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.combat.NpcDropsDef
import osrsps.game.npc.NpcSpawn
import osrsps.game.trail.ClueStep
import osrsps.game.trail.StepType
import osrsps.game.trail.TrailData
import osrsps.game.trail.TrailRewards
import osrsps.game.trail.TrailTarget
import osrsps.game.trail.TrailTier
import osrsps.game.trail.TrailTile
import osrsps.game.trail.TreasureTrails
import osrsps.map.CollisionMap

/**
 * Treasure Trails step TT1 through the headless client: a scroll box opening into a clue on a new trail (and refusing
 * while one is held), Read showing a map's interface, a cryptic's words and a coordinate written out, Dig solving a step
 * within a tile and doing nothing elsewhere, the last step giving the reward casket, and a monster's clue scroll
 * dropping as its scroll box until the cap. The steps are made up here: the real ones come from the cache.
 */
class HeadlessTreasureTrailsTest {
    private val mapStep = ClueStep(StepType.MAP, 107, TrailTier.EASY, null, listOf(TrailTarget.Dig(TrailTile(3226, 3218, 0))), items = listOf(MAP_ITEM))
    private val digStep = ClueStep(StepType.CRYPTIC, 52, TrailTier.EASY, "Words of a made-up step.", listOf(TrailTarget.Dig(TrailTile(3230, 3222, 0))), items = listOf(CRYPTIC_ITEM))
    private val coordinate = ClueStep(StepType.COORDINATE, 400, TrailTier.MEDIUM, "01.02N 03.04W", listOf(TrailTarget.Dig(TrailTile(3240, 3240, 0))), items = listOf(COORD_ITEM))
    // A step outside Misthalin, which never comes up.
    private val faraway = ClueStep(StepType.CRYPTIC, 60, TrailTier.EASY, "Far away.", listOf(TrailTarget.Dig(TrailTile(2600, 3000, 0))), items = listOf(FAR_ITEM))

    private val man = NpcType(MAN, "Man", ops = listOf(null, "Attack", null, null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(31_000)
        val read = listOf("Read", "Check steps", null, null, "Drop")
        for ((id, tier) in listOf(MAP_ITEM to 347, CRYPTIC_ITEM to 347, FAR_ITEM to 347, COORD_ITEM to 348, EASY_DROP to 347)) {
            objs[id] = ObjType(id, "Clue scroll", iops = read, params = mapOf(tier to 1))
        }
        for (tier in TrailTier.entries) {
            objs[tier.scrollBox] = ObjType(tier.scrollBox, "Scroll box (${tier.key})", stackable = true, iops = listOf("Open", null, null, null, "Drop"))
            objs[tier.rewardCasket] = ObjType(tier.rewardCasket, "Reward casket (${tier.key})", stackable = true, iops = listOf("Open", null, null, null, "Drop"))
        }
        objs[TreasureTrails.SPADE] = ObjType(TreasureTrails.SPADE, "Spade", iops = listOf("Dig", null, null, null, "Drop"))
        objs[COINS] = ObjType(COINS, "Coins", stackable = true)
        objs[DAGGER] = ObjType(DAGGER, "Bronze dagger")
        objs[MINOR_CASE] = ObjType(MINOR_CASE, "Minor easy scroll case", iops = listOf("Open", null, null, null, "Drop"))
        val objTypes = ObjTypes(objs)
        val types = NpcTypes.of(man)
        val drops = NpcDrops(mapOf(MAN to NpcDropsDef(listOf(DropItem(objTypes[EASY_DROP]!!, 1, 1)), null, emptyList(), "test")))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objTypes,
            npcTypes = types, npcSpawns = listOf(NpcSpawn(man, 3224, 3216, source = "test"), NpcSpawn(man, 3219, 3216, source = "test")), npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = drops,
            trailData = TrailData(listOf(mapStep, digStep, coordinate, faraway)), trailRewards = TrailRewards.parse(REWARDS, objTypes), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.backpack() = inventory(BACKPACK)

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { backpack().indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.op(id: Int, op: Int) = ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, op + 1, slotOf(id), id)

    private fun HeadlessClient.objbox(key: String) {
        await(key) { if (dialogueView is DialogueView.Other && text(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_TEXT) == "[$key]") Unit else null }
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `a scroll box opens into a clue on a new trail and won't open while a clue is held`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${TrailTier.EASY.scrollBox} 2")
                client.op(TrailTier.EASY.scrollBox, 1)
                val clue = client.await("a clue scroll") { client.backpack().firstOrNull { it?.id == MAP_ITEM || it?.id == CRYPTIC_ITEM } }
                assertEquals(1, client.backpack().single { it?.id == TrailTier.EASY.scrollBox }!!.count)
                // Two steps are built for easy, and an easy trail is at least two long: the trail is both.
                val trail = client.commandReply("clue") { it.startsWith("easy") }
                assertTrue(trail.endsWith("0 of 2 done."), trail)
                assertTrue(clue!!.id != FAR_ITEM)

                client.op(TrailTier.EASY.scrollBox, 1)
                client.seen("[trail.box_refuses]")
                assertEquals(1, client.backpack().single { it?.id == TrailTier.EASY.scrollBox }!!.count)
            }
        }
    }

    @Test
    fun `Read shows a map's interface, a cryptic's words and a coordinate written out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("clue easy map:107") { it.startsWith("Clue") }
                client.op(MAP_ITEM, 1)
                client.await("the map") { if (346 in client.openInterfaces()) Unit else null }
                client.closeModal()

                client.command("empty")
                client.commandReply("clue easy cryptic:52") { it.startsWith("Clue") }
                client.op(CRYPTIC_ITEM, 1)
                client.await("the words") { client.text(TreasureTrails.CLUE_TEXT, TreasureTrails.CLUE_TEXT_WORDS)?.takeIf { it == "Words of a made-up step." } }

                client.commandReply("clue medium coordinate:400") { it.startsWith("Clue") }
                client.op(COORD_ITEM, 1)
                client.await("the coordinate") {
                    client.text(TreasureTrails.CLUE_TEXT, TreasureTrails.CLUE_TEXT_WORDS)
                        ?.takeIf { it == "[trail.coordinate lat_deg=01 lat_min=02 lat_dir=[trail.north] lon_deg=03 lon_min=04 lon_dir=[trail.west]]" }
                }
                client.op(COORD_ITEM, 2)
                client.seen("[trail.check_steps count=0]")
            }
        }
    }

    @Test
    fun `Dig within a tile solves the step, the last giving the casket, and elsewhere does nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${TreasureTrails.SPADE}")
                client.commandReply("clue easy map:107") { it.startsWith("Clue") }

                client.commandReply("tele 3200 3200") { it.startsWith("Teleported") }
                client.awaitTicks(2)
                client.op(TreasureTrails.SPADE, 1)
                client.seen("[game.nothing_interesting]")
                assertEquals(MAP_ITEM, client.backpack()[client.slotOf(MAP_ITEM)]?.id)

                // A tile off the spot is close enough.
                client.commandReply("tele 3227 3219") { it.startsWith("Teleported") }
                client.awaitTicks(2)
                client.op(TreasureTrails.SPADE, 1)
                client.await("the next clue") { client.backpack().firstOrNull { it?.id == CRYPTIC_ITEM } }
                assertNull(client.backpack().firstOrNull { it?.id == MAP_ITEM })
                client.objbox("trail.another_clue")
                client.op(CRYPTIC_ITEM, 2)
                client.seen("[trail.check_steps count=1]")

                client.commandReply("tele 3230 3222") { it.startsWith("Teleported") }
                client.awaitTicks(2)
                client.op(TreasureTrails.SPADE, 1)
                client.await("the casket") { client.backpack().firstOrNull { it?.id == TrailTier.EASY.rewardCasket } }
                assertNull(client.backpack().firstOrNull { it?.id == CRYPTIC_ITEM })
                client.objbox("trail.casket")
                assertEquals("No trail.", client.commandReply("clue") { it.startsWith("No") })
            }
        }
    }

    @Test
    fun `a monster's clue scroll drops as its scroll box, until the player holds two`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("kill man") { it.startsWith("Killed") }
                val box = client.await("the box on the ground") { client.groundObjs().firstOrNull { it.id == TrailTier.EASY.scrollBox } }
                assertTrue(client.groundObjs().none { it.id == EASY_DROP })

                // A clue and a box held: the next is kept back, and the player told.
                client.command("item ${TrailTier.EASY.scrollBox}")
                client.commandReply("clue easy") { it.startsWith("Clue") }
                val before = client.groundObjs().count { it.id == TrailTier.EASY.scrollBox }
                client.commandReply("kill man") { it.startsWith("Killed") }
                client.seen("[trail.suspicion_easy]")
                assertEquals(before, client.groundObjs().count { it.id == TrailTier.EASY.scrollBox })
                assertEquals(TrailTier.EASY.scrollBox, box.id)
            }
        }
    }

    @Test
    fun `a casket gives its rolls on the reward screen, counts the trail, and gives the scroll case from its count until opened`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${TrailTier.EASY.rewardCasket} 3")
                client.op(TrailTier.EASY.rewardCasket, 1)
                client.seen("[trail.well_done]")
                client.seen("[trail.completed count=1 tier=easy]")
                client.await("the worth") { client.messages().firstOrNull { it.text.startsWith("[trail.worth value=") } }
                client.await("the reward screen") { if (TreasureTrails.REWARD_SCREEN in client.openInterfaces()) Unit else null }
                // Two rolls, each ten coins or a dagger.
                val coins = client.backpack().filterNotNull().filter { it.id == COINS }.sumOf { it.count }
                val daggers = client.backpack().count { it?.id == DAGGER }
                assertEquals(20, coins + daggers * 10)
                assertTrue(client.backpack().none { it?.id == MINOR_CASE })
                client.closeModal()

                // The case comes at 3 caskets, and again until it is opened.
                client.commandReply("clue easy done 2") { it.startsWith("easy") }
                client.op(TrailTier.EASY.rewardCasket, 1)
                client.seen("[trail.completed count=3 tier=easy]")
                client.await("the scroll case") { client.backpack().firstOrNull { it?.id == MINOR_CASE } }
                client.closeModal()
                client.op(MINOR_CASE, 1)
                client.seen("[trail.case_opened]")
                assertEquals("easy: 3 caskets opened, cap 3.", client.commandReply("clue easy done 3") { it.startsWith("easy") })
                client.op(TrailTier.EASY.rewardCasket, 1)
                client.seen("[trail.completed count=4 tier=easy]")
                assertTrue(client.backpack().none { it?.id == MINOR_CASE })
                client.closeModal()
                assertEquals("No trail, no caskets opened, no scroll cases.", client.commandReply("clue reset") { it.startsWith("No") })
            }
        }
    }

    private companion object {
        const val COINS = 995
        const val DAGGER = 1205
        const val MINOR_CASE = 30906
        /** Two rolls on a table of ten coins or a bronze dagger; the minor scroll case at three caskets. */
        val REWARDS = """
            {"tiers": {"easy": {"rolls": {"min": 2, "max": 2}, "slots": 2,
              "lines": [{"item": 995, "name": "Coins", "count": 10, "weight": 1}, {"item": 1205, "name": "Bronze dagger", "weight": 1}],
              "extra": [], "milestones": [{"count": 3, "item": 30906, "name": "Minor easy scroll case"}]}}}
        """.trimIndent()
        const val BACKPACK = 93
        const val MAN = 3106
        const val MAP_ITEM = 2713
        const val CRYPTIC_ITEM = 19814
        const val FAR_ITEM = 2690
        const val COORD_ITEM = 2801
        /** A clue scroll a monster drops, as npc-drops.json has them (an easy one). */
        const val EASY_DROP = 2677
    }
}
