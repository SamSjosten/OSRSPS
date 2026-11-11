package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.MisthalinSweep
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Misthalin piece 7 step 12a through the headless client: a H.A.M. guard's and a seated member's menus, Johanhus
 * Ulsbrecht and Jimmy the Chisel, nettles stinging bare hands and picked with gloves on, a coop's egg and an empty
 * crate. The rl-client script test-runner/examples/misthalin-sweep.json does the same on the real map.
 */
class HeadlessMisthalinSweepTest {
    private fun npc(id: Int, name: String) = NpcType(id, name, ops = listOf("Talk-to", null, null, null, null), internalName = "test_$id")

    private val guard = npc(MisthalinSweep.HAM_GUARDS.first(), "H.A.M. Guard")
    private val member = npc(MisthalinSweep.HAM_SEATED.first(), "H.A.M. Member")
    private val johanhus = npc(MisthalinSweep.JOHANHUS, "Johanhus Ulsbrecht")
    private val jimmy = npc(MisthalinSweep.JIMMY, "Jimmy the Chisel")

    private val nettles = ZoneLoc(MisthalinSweep.NETTLES.first(), LocShape.CENTREPIECE_STRAIGHT, 0, 3131, 3242, 0)
    private val coop = ZoneLoc(MisthalinSweep.COOPS.first(), LocShape.CENTREPIECE_STRAIGHT, 0, 3178, 3307, 0)
    private val crate = ZoneLoc(MisthalinSweep.EMPTY_CRATES.first(), LocShape.CENTREPIECE_STRAIGHT, 0, 3347, 3446, 0)

    private fun type(id: Int, name: String, op: String) =
        LocType(id, name, ops = listOf(op, null, null, null, null), collision = LocCollisionType(width = 1, length = 1, interactType = 1))

    private fun server(dir: Path, spawns: List<Triple<NpcType, Int, Int>>) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(5000)
        objs[MisthalinSweep.NETTLES_ITEM] = ObjType(MisthalinSweep.NETTLES_ITEM, "Nettles")
        objs[MisthalinSweep.EGG] = ObjType(MisthalinSweep.EGG, "Egg")
        objs[GLOVES] = ObjType(GLOVES, "Leather gloves", iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = WearPos.HANDS)
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(48, 50)
                markLoaded(49, 51)
                markLoaded(52, 53)
                markLoaded(49, 150)
            },
            objTypes = ObjTypes(objs),
            npcTypes = NpcTypes.of(guard, member, johanhus, jimmy),
            npcSpawns = spawns.map { (type, x, z) -> NpcSpawn(type, x, z, source = "test") },
            locTypes = LocTypes(
                listOf(
                    type(nettles.id, "Nettles", "Pick"),
                    type(coop.id, "Coop", "Search"),
                    type(crate.id, "Crate", "Search"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { listOf(nettles, coop, crate).forEach(::add) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.ready() = await("the login rebuild") { position }

    private fun HeadlessClient.chats(vararg keys: String) {
        for (key in keys) {
            awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
            continueDialogue()
        }
    }

    private fun HeadlessClient.pick(option: Int, vararg keys: String) {
        val menu = awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
        assertEquals(keys.map { "[$it]" }, menu.options)
        chooseOption(option)
    }

    private fun HeadlessClient.awaitClosed() = await("the dialogue to close") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.awaitCount(id: Int, count: Int) = await("$count of item $id") { if (count(id) == count) Unit else null }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(Inventory.INV).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    private fun HeadlessClient.awaitSeen(text: String, times: Int = 1) = await("$text x$times") { if (seen(text) >= times) Unit else null }

    private fun HeadlessClient.talk(id: Int) = npcOp(await("NPC $id in view") { npcs().firstOrNull { it.id == id } }.index, 1)

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("at $x, $z") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private val menu = arrayOf("ham_talk.what_doing", "ham_talk.who", "ham_talk.achieve", "ham_talk.where", "ham_talk.ok_thanks")

    @Test
    fun `a guard and a seated member answer the H-A-M menu, Johanhus explains, Jimmy says hello`(@TempDir dir: Path) {
        val spawns = listOf(Triple(guard, 3159, 9613), Triple(member, 3162, 9615), Triple(johanhus, 3165, 9613), Triple(jimmy, 3168, 9613))
        server(dir, spawns).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.tele(3163, 9612)
                client.talk(guard.id)
                client.pick(3, *menu)
                client.chats("ham_talk.achieve", "ham_talk.achieve_answer", "ham_talk.beasts", "ham_talk.totally")
                client.pick(1, *menu)
                client.chats("ham_talk.what_doing")
                val answer = client.awaitDialogue("a random answer") { it is DialogueView.Chat } as DialogueView.Chat
                assertTrue(answer.text in listOf("[ham_talk.doing_1]", "[ham_talk.doing_2]", "[ham_talk.doing_3]"), answer.text)
                client.continueDialogue()
                client.pick(5, *menu)
                client.chats("ham_talk.ok_thanks")
                client.awaitClosed()

                client.talk(member.id)
                client.pick(4, *menu)
                client.chats("ham_talk.where", "ham_talk.where_answer", "ham_talk.where_answer_2")
                client.awaitClosed()

                client.talk(johanhus.id)
                client.chats("johanhus.welcome")
                client.pick(1, "johanhus.what_org", "johanhus.who", "johanhus.ok_option")
                client.chats("johanhus.what_org", "johanhus.org_answer", "johanhus.how", "johanhus.mobilising", "johanhus.strange")
                client.pick(3, "johanhus.what_org", "johanhus.who", "johanhus.ok_option")
                client.chats("johanhus.ok_said")
                client.awaitClosed()

                client.talk(jimmy.id)
                client.chats("jimmy_the_chisel.hello")
                client.awaitClosed()
            }
        }
    }

    @Test
    fun `nettles sting bare hands and are picked with gloves on, a coop gives an egg, a crate is empty`(@TempDir dir: Path) {
        server(dir, emptyList()).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.tele(nettles.x, nettles.z - 1)
                val before = client.await("hitpoints") { client.stat(HITPOINTS) }.level
                client.locOp(nettles.id, nettles.x, nettles.z, MisthalinSweep.PICK)
                client.awaitSeen("[misthalin_sweep.stung]")
                client.await("stung for 1 or 2") { client.stat(HITPOINTS)?.level?.takeIf { it in before - 2 until before } }
                assertEquals(0, client.count(MisthalinSweep.NETTLES_ITEM))

                client.command("item $GLOVES 1")
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 3, client.slotOf(GLOVES), GLOVES)
                client.awaitCount(GLOVES, 0)
                client.locOp(nettles.id, nettles.x, nettles.z, MisthalinSweep.PICK)
                client.awaitSeen("[misthalin_sweep.nettles_picked]")
                client.awaitCount(MisthalinSweep.NETTLES_ITEM, 1)
                assertTrue(server.world.locs.at(nettles.x, nettles.z, 0).none { it.id == nettles.id })
                client.await("the nettles back 13 ticks later") { if (server.world.locs.at(nettles.x, nettles.z, 0).any { it.id == nettles.id }) Unit else null }

                client.tele(coop.x, coop.z - 1)
                client.locOp(coop.id, coop.x, coop.z, MisthalinSweep.SEARCH)
                client.awaitSeen("[misthalin_sweep.egg_found]")
                client.awaitCount(MisthalinSweep.EGG, 1)

                client.tele(crate.x, crate.z - 1)
                client.locOp(crate.id, crate.x, crate.z, MisthalinSweep.SEARCH)
                client.awaitSeen("[search.crate_empty]")
            }
        }
    }

    private companion object {
        const val GLOVES = 1059
        const val HITPOINTS = 3
    }
}
