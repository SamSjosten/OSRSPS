package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.CombinationRunes
import osrsps.game.skill.Mining
import osrsps.game.skill.RuneCrafting
import osrsps.game.skill.Runecraft
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Runecraft step RC3 through the headless client: mist runes at the Water Altar from air runes and an air talisman,
 * at 50% without a binding necklace and every time with one, and the necklace's uses.
 */
class HeadlessCombinationRunesTest {
    private val water = Runecraft.ALTARS.first { it.key == "water" }
    private val air = Runecraft.ALTARS.first { it.key == "air" }

    private fun server(dir: Path, seed: Long = 5) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Mining.PURE_ESSENCE + 1)
        objs[Mining.PURE_ESSENCE] = ObjType(Mining.PURE_ESSENCE, "Pure essence")
        objs[AIR_RUNE] = ObjType(AIR_RUNE, "Air rune", stackable = true)
        objs[MIST_RUNE] = ObjType(MIST_RUNE, "Mist rune", stackable = true)
        objs[air.talisman] = ObjType(air.talisman, "Air talisman")
        objs[CombinationRunes.BINDING_NECKLACE] = ObjType(
            CombinationRunes.BINDING_NECKLACE, "Binding necklace", iops = listOf(null, "Wear", "Check", null, "Destroy"), wearpos1 = WearPos.NECK,
        )
        World(
            crcs, CollisionMap().apply { markLoaded(2726 shr 6, 4832 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(seed),
            locTypes = LocTypes(mapOf(water.altar to LocType(water.altar, "Altar", listOf("Craft-rune", null, null, null, null), LocCollisionType(width = 3, length = 3)))),
            staticLocs = StaticLocs().apply { add(ZoneLoc(water.altar, LocShape.CENTREPIECE_STRAIGHT, 0, ALTAR_X, ALTAR_Z, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.ready(level: Int) {
        await("the login rebuild") { position }
        command("tele 2718 4834 0")
        await("the teleport") { position?.takeIf { it == Coord(2718, 4834, 0) } }
        command("setlevel runecraft $level")
        await("level $level") { stat(RUNECRAFT)?.takeIf { it.level == level } }
    }

    private fun HeadlessClient.give(id: Int, count: Int) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) >= before + count) Unit else null }
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.useOnAltar(obj: Int) =
        locTarget(water.altar, ALTAR_X, ALTAR_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, inventory(BACKPACK).indexOfFirst { it?.id == obj }, obj)

    private fun HeadlessClient.seenCount(text: String, n: Int) = await("$n of $text") { messages().filter { it.text == text }.takeIf { it.size >= n } }

    @Test
    fun `air runes and an air talisman on the Water Altar make mist runes at about half, for 8,5 XP each made`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready(5)
                client.give(Mining.PURE_ESSENCE, 20)
                client.give(AIR_RUNE, 20)
                client.useOnAltar(AIR_RUNE)
                client.seenCount("[runecraft_combo.level level=6]", 1)
                client.command("setlevel runecraft 6")
                client.await("level 6") { client.stat(RUNECRAFT)?.takeIf { it.level == 6 } }
                client.useOnAltar(AIR_RUNE)
                client.seenCount("[runecraft_combo.no_talisman]", 1)
                val start = client.stat(RUNECRAFT)!!.xp
                client.give(air.talisman, 1)
                client.useOnAltar(AIR_RUNE)
                client.seenCount("[runecraft_combo.bind rune=Mist rune]", 1)
                client.await("the essence used") { if (client.count(Mining.PURE_ESSENCE) == 0) Unit else null }
                val made = client.count(MIST_RUNE)
                assertTrue(made in 3..17, "about half of 20: $made")
                assertEquals(0, client.count(AIR_RUNE))
                assertEquals(0, client.count(air.talisman), "the talisman is used up")
                val xp = client.await("the XP") { client.stat(RUNECRAFT)?.takeIf { it.xp > start || made == 0 } }.xp
                assertEquals(start + made * 85 / 10, xp)
            }
        }
    }

    @Test
    fun `a binding necklace makes every mist rune, spends one use a click and crumbles after 16`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready(6)
                val player = server.world.playerNamed("bob")!!
                client.give(CombinationRunes.BINDING_NECKLACE, 1)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 3, client.inventory(BACKPACK).indexOfFirst { it?.id == CombinationRunes.BINDING_NECKLACE }, CombinationRunes.BINDING_NECKLACE)
                client.await("the necklace worn") { if (client.count(CombinationRunes.BINDING_NECKLACE) == 0) Unit else null }
                player.charges[CombinationRunes.CHARGES] = 14
                for (click in 1..2) {
                    client.give(Mining.PURE_ESSENCE, 5)
                    client.give(AIR_RUNE, 5)
                    client.give(air.talisman, 1)
                    client.useOnAltar(air.talisman)
                    client.await("mist runes after click $click") { if (client.count(MIST_RUNE) == 5 * click) Unit else null }
                }
                client.seenCount("[runecraft_combo.necklace_crumbles]", 1)
                assertEquals(null, player.equipment[WearPos.NECK])
                assertEquals(0, player.charges[CombinationRunes.CHARGES])
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val RUNECRAFT = 20
        const val AIR_RUNE = 556
        const val MIST_RUNE = 4695
        const val ALTAR_X = 2715
        const val ALTAR_Z = 4835
    }
}
