package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.SpotanimNames
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.RangedWeapons
import osrsps.game.content.LumbridgeSwamp
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Misthalin piece 7 step 1 through the headless client: the swamp shed's door lets a player in and out but refuses
 * with a dramen staff wielded (Zanaris isn't built), its tools give a spade or a rake, the Water Altar's ruins refuse a
 * water talisman, and the broken raft's Grapple checks its levels and gear and carries the player across the River Lum.
 * The raft's Jump-to and the stepping stone to the desert are HeadlessShortcutsTest's. The rl-client script
 * test-runner/examples/lumbridge-swamp-rest.json does the same in the real swamp.
 */
class HeadlessLumbridgeSwampRestTest {
    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = (ops.toList() + List(5 - ops.size) { null }), collision = LocCollisionType(width = 1, length = 1, interactType = 0))

    private val types = LocTypes(
        listOf(
            type(LumbridgeSwamp.SHED_DOOR, "Door", "Open"),
            LocType(LumbridgeSwamp.SHED_DOOR_OPEN, "null"),
            type(LumbridgeSwamp.TOOLS, "Tools", "Take"),
            LocType(LumbridgeSwamp.WATER_RUINS, "null", collision = LocCollisionType(width = 3, length = 3)),
            type(LumbridgeSwamp.RAFT, "Broken Raft", "Grapple"),
        ).associateBy { it.id },
    )

    /** As the 242 map has them. */
    private val door = ZoneLoc(LumbridgeSwamp.SHED_DOOR, LocShape.WALL_STRAIGHT, 0, 3202, 3169, 0)
    private val tools = ZoneLoc(LumbridgeSwamp.TOOLS, LocShape.CENTREPIECE_STRAIGHT, 0, 3203, 3170, 0)
    private val ruins = ZoneLoc(LumbridgeSwamp.WATER_RUINS, LocShape.CENTREPIECE_STRAIGHT, 0, 3184, 3164, 0)
    private val raft = ZoneLoc(LumbridgeSwamp.RAFT, LocShape.GROUND_DECOR, 0, 3252, 3179, 0)

    private val objs = listOf(
        ObjType(LumbridgeSwamp.DRAMEN_STAFF, "Dramen staff", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, internalName = "dramen_staff"),
        ObjType(LumbridgeSwamp.RAKE, "Rake", tradeable = true, internalName = "rake"),
        ObjType(LumbridgeSwamp.SPADE, "Spade", tradeable = true, internalName = "spade"),
        ObjType(LumbridgeSwamp.WATER_TALISMAN, "Water talisman", tradeable = true, internalName = "water_talisman"),
        // A bronze crossbow, as ranged-weapons.json names the plain crossbow: it fires bolts.
        ObjType(CROSSBOW, "Crossbow", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, internalName = "crossbow"),
        ObjType(LumbridgeSwamp.MITH_GRAPPLE, "Mith grapple", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.AMMO, internalName = "xbows_grapple_tip_bolt_mithril_rope"),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objTypes = ObjTypes.of(*objs.toTypedArray())
        World(
            crcs,
            CollisionMap().apply {
                for (loc in listOf(door, ruins, raft)) markLoaded(loc.x shr 6, loc.z shr 6)
            },
            random = GameRandom(3), objTypes = objTypes, locTypes = types,
            staticLocs = StaticLocs().apply { listOf(door, tools, ruins, raft).forEach(::add) },
            rangedWeapons = RangedWeapons.load(objTypes, SeqNames.NONE, SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int) =
        assertEquals(Coord(x, z, 0), await("$x, $z") { position?.takeIf { it == Coord(x, z, 0) } })

    private fun HeadlessClient.awaitMessage(text: String) = await("the message $text") { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.wield(id: Int) {
        command("item $id")
        ifButton(INVENTORY, INVENTORY_ITEMS, WIELD_BUTTON, slotOf(id), id)
        await("$id worn") { inventory(WORN).firstOrNull { it?.id == id } }
    }

    private fun HeadlessClient.held(id: Int) = inventory(BACKPACK).count { it?.id == id }

    @Test
    fun `the shed's door lets a player in and out, but not with a dramen staff wielded`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3200, 3169)
                client.locOp(door.id, door.x, door.z, LumbridgeSwamp.OPEN)
                client.awaitAt(3202, 3169)
                val world = server.world
                client.await("the door shut again") { world.locs[3202, 3169, 0, 0]?.takeIf { it.id == LumbridgeSwamp.SHED_DOOR } }
                client.locOp(door.id, door.x, door.z, LumbridgeSwamp.OPEN)
                client.awaitAt(3201, 3169)
                client.await("the door shut again") { world.locs[3202, 3169, 0, 0]?.takeIf { it.id == LumbridgeSwamp.SHED_DOOR } }

                client.wield(LumbridgeSwamp.DRAMEN_STAFF)
                client.locOp(door.id, door.x, door.z, LumbridgeSwamp.OPEN)
                client.awaitMessage("[lumbridge_swamp.zanaris_closed]")
                client.awaitTicks(3)
                assertEquals(Coord(3201, 3169, 0), client.position)
                assertEquals(LumbridgeSwamp.SHED_DOOR, world.locs[3202, 3169, 0, 0]?.id)
            }
        }
    }

    @Test
    fun `the shed's tools give a choice with neither held, then the other one, then nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3203, 3169)
                client.locOp(tools.id, tools.x, tools.z, LumbridgeSwamp.TAKE)
                val menu = client.awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[lumbridge_swamp.tools_rake]", "[lumbridge_swamp.tools_spade]"), menu.options)
                client.chooseOption(2)
                client.await("the spade") { if (client.held(LumbridgeSwamp.SPADE) == 1) Unit else null }
                client.await("the menu closed") { if (client.dialogueView == null) Unit else null }

                client.locOp(tools.id, tools.x, tools.z, LumbridgeSwamp.TAKE)
                client.await("the rake") { if (client.held(LumbridgeSwamp.RAKE) == 1) Unit else null }
                assertEquals(null, client.dialogueView)

                client.locOp(tools.id, tools.x, tools.z, LumbridgeSwamp.TAKE)
                client.awaitMessage("[lumbridge_swamp.tools_have_both]")
                assertEquals(1, client.held(LumbridgeSwamp.RAKE))
                assertEquals(1, client.held(LumbridgeSwamp.SPADE))
            }
        }
    }

    @Test
    fun `the Water Altar's ruins refuse a water talisman, as Runecraft isn't built`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3184, 3161)
                client.command("item ${LumbridgeSwamp.WATER_TALISMAN}")
                val slot = client.slotOf(LumbridgeSwamp.WATER_TALISMAN)
                client.locTarget(ruins.id, ruins.x, ruins.z, INVENTORY, INVENTORY_ITEMS, slot, LumbridgeSwamp.WATER_TALISMAN)
                client.awaitMessage("[lumbridge_swamp.runecraft_closed]")
                assertEquals(1, client.held(LumbridgeSwamp.WATER_TALISMAN))
            }
        }
    }

    @Test
    fun `the raft's Grapple wants its levels and a crossbow with a mith grapple, then crosses the river both ways`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                // West of the west bank's tile: the click walks to it first.
                client.tele(3243, 3179)
                client.locOp(raft.id, raft.x, raft.z, LumbridgeSwamp.GRAPPLE)
                client.awaitMessage("[lumbridge_swamp.grapple_levels agility=8 strength=19 ranged=37]")
                assertEquals(Coord(3246, 3179, 0), client.position)

                for ((skill, level) in listOf("agility" to 8, "strength" to 19, "ranged" to 37)) {
                    client.command("setlevel $skill $level")
                }
                client.await("ranged 37") { client.stat(RANGED)?.takeIf { it.level == 37 } }
                client.locOp(raft.id, raft.x, raft.z, LumbridgeSwamp.GRAPPLE)
                client.awaitMessage("[lumbridge_swamp.grapple_gear]")

                client.wield(CROSSBOW)
                client.wield(LumbridgeSwamp.MITH_GRAPPLE)
                client.locOp(raft.id, raft.x, raft.z, LumbridgeSwamp.GRAPPLE)
                client.awaitAt(3259, 3179)
                client.awaitTicks(2)
                if (client.inventory(WORN).getOrNull(WearPos.AMMO)?.id != LumbridgeSwamp.MITH_GRAPPLE) {
                    // One crossing in 25 breaks it; it said so.
                    client.awaitMessage("[lumbridge_swamp.grapple_broken]")
                    client.wield(LumbridgeSwamp.MITH_GRAPPLE)
                }
                client.locOp(raft.id, raft.x, raft.z, LumbridgeSwamp.GRAPPLE)
                client.awaitAt(3246, 3179)
                assertTrue(client.messages().none { it.text == "[game.cant_reach]" }, "${client.messages()}")
            }
        }
    }

    private companion object {
        const val CROSSBOW = 837
        const val RANGED = 4

        // As HeadlessEquipmentTest has them.
        const val BACKPACK = 93
        const val WORN = 94
        const val WIELD_BUTTON = 3
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
    }
}
