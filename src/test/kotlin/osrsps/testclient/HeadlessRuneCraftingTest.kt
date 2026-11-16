package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Mining
import osrsps.game.skill.RuneCrafting
import osrsps.game.skill.Runecraft
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Runecraft step RC2 through the headless client: Craft-rune at the Air and Cosmic altars below and above a multiple,
 * rune essence refused where only pure essence binds, and an air tiara bound at the Air Altar. The altars stand where
 * the 242 map has them.
 */
class HeadlessRuneCraftingTest {
    private val air = Runecraft.ALTARS.first { it.key == "air" }
    private val cosmic = Runecraft.ALTARS.first { it.key == "cosmic" }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Runecraft.ELEMENTAL_TIARA + 1)
        objs[Mining.RUNE_ESSENCE] = ObjType(Mining.RUNE_ESSENCE, "Rune essence")
        objs[Mining.PURE_ESSENCE] = ObjType(Mining.PURE_ESSENCE, "Pure essence")
        objs[AIR_RUNE] = ObjType(AIR_RUNE, "Air rune", stackable = true)
        objs[COSMIC_RUNE] = ObjType(COSMIC_RUNE, "Cosmic rune", stackable = true)
        objs[air.talisman] = ObjType(air.talisman, "Air talisman")
        objs[air.tiara] = ObjType(air.tiara, "Air tiara")
        objs[RuneCrafting.TIARA] = ObjType(RuneCrafting.TIARA, "Tiara")
        objs[RuneCrafting.GOLD_TIARA] = ObjType(RuneCrafting.GOLD_TIARA, "Gold tiara")
        objs[Runecraft.ELEMENTAL_TALISMAN] = ObjType(Runecraft.ELEMENTAL_TALISMAN, "Elemental talisman")
        objs[Runecraft.ELEMENTAL_TIARA] = ObjType(Runecraft.ELEMENTAL_TIARA, "Elemental tiara")
        val map = CollisionMap().apply {
            markLoaded(2841 shr 6, 4829 shr 6)
            markLoaded(2142 shr 6, 4813 shr 6)
        }
        World(
            crcs, map, objTypes = ObjTypes(objs), random = GameRandom(5),
            locTypes = LocTypes(
                listOf(air.altar, cosmic.altar).associateWith { LocType(it, "Altar", listOf("Craft-rune", null, null, null, null), LocCollisionType(width = 3, length = 3)) },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(air.altar, LocShape.CENTREPIECE_STRAIGHT, 0, AIR_ALTAR_X, AIR_ALTAR_Z, 0))
                add(ZoneLoc(cosmic.altar, LocShape.CENTREPIECE_STRAIGHT, 0, COSMIC_ALTAR_X, COSMIC_ALTAR_Z, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.ready(x: Int, z: Int, level: Int) {
        await("the login rebuild") { position }
        command("tele $x $z 0")
        await("the teleport") { position?.takeIf { it == Coord(x, z, 0) } }
        command("setlevel runecraft $level")
        await("level $level") { stat(RUNECRAFT)?.takeIf { it.level == level } }
    }

    private fun HeadlessClient.give(id: Int, count: Int) {
        command("item $id $count")
        await("$count of $id") { if (count(id) >= count) Unit else null }
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `Craft-rune at the Air Altar makes one air rune an essence at 10 and two at 11, for 5 XP an essence`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready(2841, 4829, 10)
                val start = client.stat(RUNECRAFT)!!.xp
                client.give(Mining.RUNE_ESSENCE, 4)
                client.give(Mining.PURE_ESSENCE, 3)
                client.locOp(air.altar, AIR_ALTAR_X, AIR_ALTAR_Z, RuneCrafting.CRAFT_RUNE)
                client.seen("[runecraft_craft.bind rune=Air rune]")
                client.await("7 air runes") { if (client.count(AIR_RUNE) == 7) Unit else null }
                assertEquals(0, client.count(Mining.RUNE_ESSENCE) + client.count(Mining.PURE_ESSENCE))
                assertEquals(start + 35, client.await("35 XP") { client.stat(RUNECRAFT)?.takeIf { it.xp >= start + 35 } }.xp)

                client.command("setlevel runecraft 11")
                client.await("level 11") { client.stat(RUNECRAFT)?.takeIf { it.level == 11 } }
                client.give(Mining.PURE_ESSENCE, 5)
                client.locOp(air.altar, AIR_ALTAR_X, AIR_ALTAR_Z, RuneCrafting.CRAFT_RUNE)
                client.await("17 air runes") { if (client.count(AIR_RUNE) == 17) Unit else null }
            }
        }
    }

    @Test
    fun `the Cosmic Altar takes pure essence alone and needs level 27`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready(2142, 4813, 26)
                client.give(Mining.RUNE_ESSENCE, 2)
                client.locOp(cosmic.altar, COSMIC_ALTAR_X, COSMIC_ALTAR_Z, RuneCrafting.CRAFT_RUNE)
                client.seen("[runecraft_craft.pure_only]")
                client.give(Mining.PURE_ESSENCE, 2)
                client.locOp(cosmic.altar, COSMIC_ALTAR_X, COSMIC_ALTAR_Z, RuneCrafting.CRAFT_RUNE)
                client.seen("[runecraft_craft.level level=27]")
                client.command("setlevel runecraft 59")
                client.await("level 59") { client.stat(RUNECRAFT)?.takeIf { it.level == 59 } }
                client.locOp(cosmic.altar, COSMIC_ALTAR_X, COSMIC_ALTAR_Z, RuneCrafting.CRAFT_RUNE)
                client.await("4 cosmic runes") { if (client.count(COSMIC_RUNE) == 4) Unit else null }
                assertEquals(2, client.count(Mining.RUNE_ESSENCE), "the rune essence stays")
            }
        }
    }

    @Test
    fun `a tiara used on the Air Altar with an air talisman held becomes an air tiara for 25 XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready(2841, 4829, 1)
                val start = client.stat(RUNECRAFT)!!.xp
                client.give(RuneCrafting.TIARA, 1)
                val tiaraSlot = client.inventory(BACKPACK).indexOfFirst { it?.id == RuneCrafting.TIARA }
                client.locTarget(air.altar, AIR_ALTAR_X, AIR_ALTAR_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, tiaraSlot, RuneCrafting.TIARA)
                client.seen("[runecraft_craft.tiara_needs_talisman]")
                client.give(air.talisman, 1)
                client.locTarget(air.altar, AIR_ALTAR_X, AIR_ALTAR_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, tiaraSlot, RuneCrafting.TIARA)
                client.seen("[runecraft_craft.tiara]")
                client.await("the air tiara") { if (client.count(air.tiara) == 1) Unit else null }
                assertEquals(0, client.count(air.talisman) + client.count(RuneCrafting.TIARA))
                assertEquals(start + 25, client.await("25 XP") { client.stat(RUNECRAFT)?.takeIf { it.xp >= start + 25 } }.xp)
            }
        }
    }

    @Test
    fun `a gold tiara used on the Air Altar makes the elemental tiara even with a tiara and an air talisman held`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready(2841, 4829, 1)
                val start = client.stat(RUNECRAFT)!!.xp
                client.give(RuneCrafting.TIARA, 1)
                client.give(air.talisman, 1)
                client.give(RuneCrafting.GOLD_TIARA, 1)
                client.give(Runecraft.ELEMENTAL_TALISMAN, 1)
                val goldSlot = client.inventory(BACKPACK).indexOfFirst { it?.id == RuneCrafting.GOLD_TIARA }
                client.locTarget(air.altar, AIR_ALTAR_X, AIR_ALTAR_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, goldSlot, RuneCrafting.GOLD_TIARA)
                client.await("the elemental tiara") { if (client.count(Runecraft.ELEMENTAL_TIARA) == 1) Unit else null }
                assertEquals(0, client.count(RuneCrafting.GOLD_TIARA) + client.count(Runecraft.ELEMENTAL_TALISMAN))
                assertEquals(1, client.count(RuneCrafting.TIARA), "the plain tiara stays")
                assertEquals(1, client.count(air.talisman), "the air talisman stays")
                assertEquals(start + 40, client.await("40 XP") { client.stat(RUNECRAFT)?.takeIf { it.xp >= start + 40 } }.xp)
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val RUNECRAFT = 20
        const val AIR_RUNE = 556
        const val COSMIC_RUNE = 564
        const val AIR_ALTAR_X = 2843
        const val AIR_ALTAR_Z = 4833
        const val COSMIC_ALTAR_X = 2141
        const val COSMIC_ALTAR_Z = 4832
    }
}
