package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.VarrockChampionsGuild
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopStock
import osrsps.game.shop.ShopType
import osrsps.game.shop.ShopTypes
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Varrock step 10 through the headless client: the Champions' Guild's door (the Guildmaster greets whoever comes in, and
 * lets them out again without a word), its trapdoor down to the basement and the ladder back up, the Guildmaster's talk,
 * and Scavvo's and Valaine's talk every way, ending in their shops or with the talk over. The rl-client script
 * test-runner/examples/varrock-champions-guild.json does the same in the real guild.
 */
class HeadlessChampionsGuildTest {
    private val coif = ObjType(COIF, "Coif", cost = 200, tradeable = true)

    private val trade = listOf("Talk-to", null, "Trade", null, null)
    private val guildmaster = NpcType(VarrockChampionsGuild.GUILDMASTER, "Guildmaster", ops = listOf("Talk-to", null, null, null, null), internalName = "guildmaster")
    private val scavvo = NpcType(VarrockChampionsGuild.SCAVVO, "Scavvo", ops = trade, internalName = "scavvo")
    private val valaine = NpcType(VarrockChampionsGuild.VALAINE, "Valaine", ops = trade, internalName = "valaine")

    private fun shop(key: String, npc: NpcType) = ShopType(
        key, key, invId = 33, size = 1, general = false, sellAt = 1000, buyAt = 600, changePer = 33,
        stock = listOf(ShopStock(coif, 2, 1000)), npcs = listOf(npc.id),
    )

    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = (ops.toList() + List(5 - ops.size) { null }), collision = LocCollisionType(width = 1, length = 1))

    private val types = LocTypes(
        listOf(
            type(VarrockChampionsGuild.DOOR, "Door", "Open"),
            type(VarrockChampionsGuild.DOOR_OPEN, "Door", "Close"),
            type(VarrockChampionsGuild.TRAPDOOR, "Trapdoor", "Open"),
            type(VarrockChampionsGuild.TRAPDOOR_OPEN, "Trapdoor", "Climb-down", "Close"),
            type(VarrockChampionsGuild.LADDER, "Ladder", "Climb-up"),
        ).associateBy { it.id },
    )

    /** As the 241 map has them; the ladder is on a bridged level 1 there, so it acts at level 0. */
    private val door = ZoneLoc(VarrockChampionsGuild.DOOR, LocShape.WALL_STRAIGHT, 3, 3191, 3363, 0)
    private val trapdoor = ZoneLoc(VarrockChampionsGuild.TRAPDOOR, LocShape.GROUND_DECOR, 0, 3190, 3355, 0)
    private val ladder = ZoneLoc(VarrockChampionsGuild.LADDER, LocShape.CENTREPIECE_STRAIGHT, 1, 3190, 9758, 0)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3191 shr 6, 3363 shr 6); markLoaded(3190 shr 6, 9758 shr 6) },
            objTypes = ObjTypes.of(coif), locTypes = types,
            staticLocs = StaticLocs().apply { listOf(door, trapdoor, ladder).forEach(::add) },
            npcTypes = NpcTypes.of(guildmaster, scavvo, valaine),
            npcSpawns = listOf(
                NpcSpawn(guildmaster, 3193, 3362, wander = 0, source = "test"),
                NpcSpawn(scavvo, 3191, 3351, level = 1, wander = 0, source = "test"),
                NpcSpawn(valaine, 3193, 3361, level = 1, wander = 0, source = "test"),
            ),
            shopTypes = ShopTypes(listOf(shop("scavvos_rune_store", scavvo), shop("valaines_shop_of_champions", valaine))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int = 0) =
        assertEquals(Coord(x, z, level), await("$x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } })

    private fun HeadlessClient.chat(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    private fun HeadlessClient.pick(option: Int, vararg keys: String) {
        val texts = keys.map { "[$it]" }
        awaitDialogue("the menu $texts") { it is DialogueView.Menu && it.options == texts }
        chooseOption(option)
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null && ShopScreen.MAIN !in openInterfaces()) Unit else null }

    private fun HeadlessClient.shopOpens() {
        await("the shop") { if (ShopScreen.MAIN in openInterfaces() && opEnabled(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 27, 2)) Unit else null }
        closeModal()
        await("the shop closed") { if (ShopScreen.MAIN !in openInterfaces()) Unit else null }
    }

    /** Talks to [id] through [steps] (keys under [prefix], or "n:key1,key2" to pick option n). */
    private fun HeadlessClient.talk(id: Int, prefix: String, vararg steps: String) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, VarrockChampionsGuild.TALK_TO)
        for (step in steps) {
            val menu = step.substringBefore(':', "")
            if (menu.isEmpty()) chat("$prefix.$step") else pick(menu.toInt(), *step.substringAfter(':').split(',').map { "$prefix.$it" }.toTypedArray())
        }
    }

    @Test
    fun `the Guildmaster greets players coming in, and the door lets them out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3191, 3364)
                client.locOp(door.id, door.x, door.z, VarrockChampionsGuild.OPEN)
                client.chat("guildmaster.welcome")
                client.awaitAt(3191, 3362)
                val world = server.world
                client.await("the door shut again") { world.locs[3191, 3363, 0, 0]?.takeIf { it.id == VarrockChampionsGuild.DOOR } }
                client.locOp(door.id, door.x, door.z, VarrockChampionsGuild.OPEN)
                client.awaitAt(3191, 3363)
                assertEquals(null, client.dialogueView)
                // From beside the doorway, outside: into the doorway first, then through past the open leaf.
                client.await("the door shut") { world.locs[3191, 3363, 0, 0]?.takeIf { it.id == VarrockChampionsGuild.DOOR } }
                client.tele(3190, 3363)
                client.locOp(door.id, door.x, door.z, VarrockChampionsGuild.OPEN)
                client.chat("guildmaster.welcome")
                client.awaitAt(3191, 3362)
            }
        }
    }

    @Test
    fun `the trapdoor opens and leads down to the basement, and its ladder back up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val world = server.world
                client.tele(3190, 3356)
                client.locOp(trapdoor.id, trapdoor.x, trapdoor.z, VarrockChampionsGuild.OPEN)
                client.await("the trapdoor open") { world.locs[3190, 3355, 0, LAYER_GROUND_DECOR]?.takeIf { it.id == VarrockChampionsGuild.TRAPDOOR_OPEN } }
                client.locOp(VarrockChampionsGuild.TRAPDOOR_OPEN, trapdoor.x, trapdoor.z, VarrockChampionsGuild.CLIMB_DOWN)
                client.awaitAt(VarrockChampionsGuild.BASEMENT.x, VarrockChampionsGuild.BASEMENT.z)
                client.locOp(ladder.id, ladder.x, ladder.z, VarrockChampionsGuild.CLIMB_UP)
                client.awaitAt(VarrockChampionsGuild.GROUND.x, VarrockChampionsGuild.GROUND.z)
                client.locOp(VarrockChampionsGuild.TRAPDOOR_OPEN, trapdoor.x, trapdoor.z, VarrockChampionsGuild.CLOSE)
                client.await("the trapdoor shut") { world.locs[3190, 3355, 0, LAYER_GROUND_DECOR]?.takeIf { it.id == VarrockChampionsGuild.TRAPDOOR } }
            }
        }
    }

    @Test
    fun `the Guildmaster, Scavvo and Valaine talk every way`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3192, 3361)
                client.talk(guildmaster.id, "guildmaster", "greeting", "1:what_place", "what_place_answer")
                client.over()
                client.tele(3191, 3353, 1)
                val scavvoMenu = "no_toys,lets_see,goody,why"
                client.talk(scavvo.id, "scavvo", "greeting", "2:$scavvoMenu")
                client.shopOpens()
                client.talk(scavvo.id, "scavvo", "greeting", "3:$scavvoMenu", "goody_said")
                client.shopOpens()
                client.talk(scavvo.id, "scavvo", "greeting", "1:$scavvoMenu", "no_toys")
                client.over()
                client.talk(scavvo.id, "scavvo", "greeting", "4:$scavvoMenu", "why", "why_answer")
                client.over()
                client.tele(3193, 3359, 1)
                client.talk(valaine.id, "valaine", "greeting", "1:yes,no")
                client.shopOpens()
                client.talk(valaine.id, "valaine", "greeting", "2:yes,no", "no")
                client.over()
            }
        }
    }

    private companion object {
        const val COIF = 1169

        /** The loc layer of ground decoration (a trapdoor): walls 0, wall decoration 1, centrepieces 2, ground decor 3. */
        const val LAYER_GROUND_DECOR = 3
    }
}
