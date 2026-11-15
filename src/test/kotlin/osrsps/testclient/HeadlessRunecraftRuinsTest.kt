package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Runecraft
import osrsps.game.skill.RunicAltar
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Runecraft step RC1 through the headless client: the air and water ruins entered with a talisman, the elemental
 * talisman and an air tiara's Enter, the varbit the tiara sets, the air altar's exit portal, and Locate. The ruins are
 * placed as the 242 map has them, as multilocs over the shown types, and the client sends the multiloc's own id.
 */
class HeadlessRunecraftRuinsTest {
    private val air = Runecraft.ALTARS.first { it.key == "air" }
    private val water = Runecraft.ALTARS.first { it.key == "water" }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Runecraft.CATALYTIC_TALISMAN + 1)
        val talismanOps = listOf(null, null, null, "Locate", "Drop")
        for (altar in Runecraft.ALTARS) {
            objs[altar.talisman] = ObjType(altar.talisman, "${altar.key} talisman", iops = talismanOps)
            objs[altar.tiara] = ObjType(altar.tiara, "${altar.key} tiara", iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = WearPos.HEAD)
        }
        objs[Runecraft.ELEMENTAL_TALISMAN] = ObjType(Runecraft.ELEMENTAL_TALISMAN, "Elemental talisman", iops = talismanOps)
        objs[Runecraft.CATALYTIC_TALISMAN] = ObjType(Runecraft.CATALYTIC_TALISMAN, "Catalytic talisman", iops = talismanOps)
        val map = CollisionMap().apply {
            for ((x, z) in listOf(3222 to 3218, 3222 to 9618, air.ruinsTile.x to air.ruinsTile.z, water.ruinsTile.x to water.ruinsTile.z, 2841 to 4829, 2726 to 4832)) {
                markLoaded(x shr 6, z shr 6)
            }
        }
        val types = buildList {
            for (altar in listOf(air, water)) {
                add(LocType(altar.ruins, collision = LocCollisionType(width = 3, length = 3), multiloc = true))
                add(LocType(altar.portal, "Portal", listOf("Use", null, null, null, null), LocCollisionType()))
            }
        }
        World(
            crcs, map, objTypes = ObjTypes(objs), random = GameRandom(5),
            locTypes = LocTypes(types.associateBy { it.id }),
            staticLocs = StaticLocs().apply {
                for (altar in listOf(air, water)) add(ZoneLoc(altar.ruins, LocShape.CENTREPIECE_STRAIGHT, 0, altar.ruinsTile.x, altar.ruinsTile.z, 0))
                add(ZoneLoc(air.portal, LocShape.CENTREPIECE_STRAIGHT, 0, AIR_PORTAL_X, AIR_PORTAL_Z, 0))
            },
            varTypes = VarTypes(6_000, Runecraft.ALTARS.associate { it.varbit to varbit(it) }),
            cycleMillis = millis,
        )
    }

    /** The altars' varbits as the 242 cache has them: bits of abyssal_warp (491), blood's of abyssal_warp_2 (3413). */
    private fun varbit(altar: RunicAltar): VarbitType = when (altar.key) {
        "blood" -> VarbitType(3413, 0, 0)
        "wrath" -> VarbitType(491, 31, 31)
        else -> VarbitType(491, Runecraft.ALTARS.indexOf(altar), Runecraft.ALTARS.indexOf(altar))
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.give(id: Int) {
        command("item $id 1")
        await("item $id") { slotOf(id) }
    }

    private fun HeadlessClient.slotOf(id: Int) = inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 }

    private fun HeadlessClient.seen(text: String, after: Int = 0) =
        await(text) { messages().drop(after).firstOrNull { it.text == text } }

    private fun HeadlessClient.useOn(altar: RunicAltar, obj: Int) =
        locTarget(altar.ruins, altar.ruinsTile.x, altar.ruinsTile.z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(obj)!!, obj)

    private fun HeadlessClient.at(x: Int, z: Int, level: Int = 0) = await("at $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }

    @Test
    fun `an air talisman on the air ruins sends the player in, and the portal sends them back beside the ruins`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(2985, 3289)
                client.give(air.talisman)
                client.useOn(air, air.talisman)
                client.seen("[runecraft.hold talisman=air talisman]")
                client.seen("[runecraft.enter]")
                client.at(2841, 4829)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 6, client.slotOf(air.talisman)!!, air.talisman)
                client.seen("[runecraft.locate_in_temple altar=Air]")

                client.locOp(air.portal, AIR_PORTAL_X, AIR_PORTAL_Z, 1)
                client.seen("[runecraft.portal]")
                client.at(air.outside.x, air.outside.z)
            }
        }
    }

    @Test
    fun `the elemental talisman opens the water ruins, and the air talisman does not`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3185, 3162)
                client.give(air.talisman)
                client.useOn(water, air.talisman)
                client.seen("[game.nothing_interesting]")
                client.give(Runecraft.ELEMENTAL_TALISMAN)
                client.useOn(water, Runecraft.ELEMENTAL_TALISMAN)
                client.seen("[runecraft.enter]")
                client.at(2726, 4832)
            }
        }
    }

    @Test
    fun `wearing the air tiara sets its varbit and lets Enter send the player in, and without it Enter does nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                val player = server.world.playerNamed("carol")!!
                client.tele(2985, 3289)
                client.locOp(air.ruins, air.ruinsTile.x, air.ruinsTile.z, 1)
                client.seen("[game.nothing_interesting]")
                assertEquals(0, client.varp(ABYSSAL_WARP) and 1)

                client.give(air.tiara)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 3, client.slotOf(air.tiara)!!, air.tiara)
                client.await("the air varbit") { if (client.varp(ABYSSAL_WARP) and 1 == 1) Unit else null }
                assertEquals(0, client.varp(ABYSSAL_WARP) and 0b100, "the water ruins stay shut")
                assertEquals(setOf("air"), Runecraft.opens(player))
                client.locOp(air.ruins, air.ruinsTile.x, air.ruinsTile.z, 1)
                client.seen("[runecraft.enter]")
                client.at(2841, 4829)
            }
        }
    }

    @Test
    fun `Locate points each talisman at its ruins, and the catalytic talisman's says nothing interesting happens`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3222, 3218)
                client.give(air.talisman)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 6, client.slotOf(air.talisman)!!, air.talisman)
                // The air ruins are 237 west and 74 north of Lumbridge: west.
                client.seen("[runecraft.locate_west]")
                client.give(water.talisman)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 6, client.slotOf(water.talisman)!!, water.talisman)
                // The water ruins' middle (3185, 3165) is 37 west and 53 south: south-west.
                client.seen("[runecraft.locate_south_west]")
                client.give(Runecraft.CATALYTIC_TALISMAN)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 6, client.slotOf(Runecraft.CATALYTIC_TALISMAN)!!, Runecraft.CATALYTIC_TALISMAN)
                client.seen("[game.nothing_interesting]")
                // Underground (the Lumbridge Swamp Caves' map square) the talismans don't work.
                client.tele(3222, 9618)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 6, client.slotOf(air.talisman)!!, air.talisman)
                client.seen("[runecraft.locate_underground]")
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val ABYSSAL_WARP = 491
        const val AIR_PORTAL_X = 2841
        const val AIR_PORTAL_Z = 4828
    }
}
