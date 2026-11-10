package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Mining
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Gathering 2, step G1 through the headless client: the Mining Guild's runite and amethyst and Shilo Village's gem rocks,
 * each reached by ::tele as live has them, on open ground (no cache collision in tests). The rl-client script
 * test-runner/examples/mining-g1.json does the same in the real client from ::scenario gathering.
 */
class HeadlessRuniteAmethystGemTest {
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(AMETHYST_ORE + 1)
        objs[RUNITE_ORE] = ObjType(RUNITE_ORE, "Runite ore")
        objs[AMETHYST_ORE] = ObjType(AMETHYST_ORE, "Amethyst")
        objs[RUNE_PICKAXE] = ObjType(RUNE_PICKAXE, "Rune pickaxe")
        objs[MITHRIL_PICKAXE] = ObjType(MITHRIL_PICKAXE, "Mithril pickaxe")
        objs[POT] = ObjType(POT, "Pot")
        objs[GLORY] = ObjType(GLORY, "Amulet of glory(4)", iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = WearPos.NECK)
        for (gem in (Mining.GEMS + Mining.GEM_ROCK_TABLE).map { it.obj }.filter { it >= 0 }) objs[gem] = ObjType(gem, "Gem $gem")
        val mine = listOf("Mine", null, null, null, null)
        fun rock(id: Int, name: String) = LocType(id, name, mine, LocCollisionType(width = 1, length = 1))
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(RUNITE_X shr 6, RUNITE_Z shr 6)
                markLoaded(GEM_X shr 6, GEM_Z shr 6)
            },
            objTypes = ObjTypes(objs),
            random = GameRandom(7),
            locTypes = LocTypes(
                listOf(
                    rock(RUNITE, "Runite rocks"), rock(AMETHYST, "Amethyst crystals"), rock(GEM_ROCK, "Gem rocks"),
                    rock(Mining.ROCKS1, "Rocks"), rock(Mining.AMETHYST_EMPTY, "Empty wall"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(RUNITE, LocShape.CENTREPIECE_STRAIGHT, 0, RUNITE_X, RUNITE_Z, 0))
                add(ZoneLoc(AMETHYST, LocShape.CENTREPIECE_STRAIGHT, 0, AMETHYST_X, AMETHYST_Z, 0))
                add(ZoneLoc(GEM_ROCK, LocShape.CENTREPIECE_STRAIGHT, 0, GEM_X, GEM_Z, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).count { it?.id == id }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z")
        await("at $x, $z") { position?.takeIf { it.x == x && it.z == z } }
    }

    private fun HeadlessClient.miner(level: Int) {
        await("the login rebuild") { position }
        command("item $RUNE_PICKAXE")
        command("setlevel mining $level")
        await("level $level") { stat(MINING)?.takeIf { it.level == level } }
    }

    @Test
    fun `the guild's runite gives one ore and 125 XP, then the rock empties`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.miner(85)
                val start = client.stat(MINING)!!.xp
                client.tele(RUNITE_X - 1, RUNITE_Z)
                client.locOp(RUNITE, RUNITE_X, RUNITE_Z, 1)
                client.seen("[mining.swing]")
                client.await("runite ore", timeoutMillis = 60_000) { if (client.count(RUNITE_ORE) == 1) Unit else null }
                client.seen("[mining.get_ore ore=runite]")
                assertEquals(start + 125, client.await("runite XP") { client.stat(MINING)?.takeIf { it.xp > start } }.xp)
                client.await("the empty rock") { if (client.locAt(RUNITE_X, RUNITE_Z) == Mining.ROCKS1) Unit else null }
            }
        }
    }

    @Test
    fun `runite needs 85 and amethyst 92`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.miner(84)
                client.tele(RUNITE_X - 1, RUNITE_Z)
                client.locOp(RUNITE, RUNITE_X, RUNITE_Z, 1)
                client.seen("[mining.level level=85]")
                client.tele(AMETHYST_X - 1, AMETHYST_Z)
                client.locOp(AMETHYST, AMETHYST_X, AMETHYST_Z, 1)
                client.seen("[mining.level level=92]")
            }
        }
    }

    @Test
    fun `amethyst crystals give two or three amethysts from one click before the wall empties`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.miner(99)
                val start = client.stat(MINING)!!.xp
                client.tele(AMETHYST_X - 1, AMETHYST_Z)
                client.locOp(AMETHYST, AMETHYST_X, AMETHYST_Z, 1)
                client.await("the empty wall", timeoutMillis = 90_000) {
                    if (client.locAt(AMETHYST_X, AMETHYST_Z) == Mining.AMETHYST_EMPTY) Unit else null
                }
                val mined = client.await("the last amethyst") { client.count(AMETHYST_ORE).takeIf { it >= 2 } }
                assertTrue(mined in 2..3, "$mined amethysts")
                assertEquals(mined, client.messages().count { it.text == "[mining.get_ore ore=amethyst]" })
                // One click: the swing message only once.
                assertEquals(1, client.messages().count { it.text == "[mining.swing]" })
                assertEquals(start + 240 * mined, client.await("amethyst XP") { client.stat(MINING)?.takeIf { it.xp >= start + 240 * mined } }.xp)
            }
        }
    }

    @Test
    fun `a gem rock gives a gem from its own table and 65 XP, and empties`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.miner(39)
                client.tele(GEM_X - 1, GEM_Z)
                client.locOp(GEM_ROCK, GEM_X, GEM_Z, 1)
                client.seen("[mining.level level=40]")
                // A rune pickaxe needs 41: a mithril one (21) at 40.
                client.command("item $MITHRIL_PICKAXE")
                client.command("setlevel mining 40")
                val start = client.await("level 40") { client.stat(MINING)?.takeIf { it.level == 40 } }.xp
                client.locOp(GEM_ROCK, GEM_X, GEM_Z, 1)
                val gems = Mining.GEM_ROCK_TABLE.map { it.obj }
                val gem = client.await("a gem", timeoutMillis = 60_000) { gems.firstOrNull { client.count(it) == 1 } }
                val line = "[mining.gem_rock_" + GEM_KEYS.getValue(gem) + "]"
                client.seen(line)
                assertEquals(start + 65, client.await("gem rock XP") { client.stat(MINING)?.takeIf { it.xp > start } }.xp)
                client.await("the empty rock") { if (client.locAt(GEM_X, GEM_Z) == Mining.ROCKS1) Unit else null }
            }
        }
    }

    @Test
    fun `a full backpack stops a gem rock, and a worn charged glory still mines it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.miner(99)
                client.command("item $GLORY 1")
                val slot = client.await("the glory") { client.inventory(Inventory.INV).indexOfFirst { it?.id == GLORY }.takeIf { it >= 0 } }
                client.ifButton(INVENTORY, INVENTORY_ITEMS, WEAR_BUTTON, slot, GLORY)
                client.await("the glory worn") { client.inventory(WORN).firstOrNull { it?.id == GLORY } }
                client.command("item $POT 27")
                client.await("a full backpack") { if (client.inventory(BACKPACK).count { it != null } == 28) Unit else null }
                client.tele(GEM_X - 1, GEM_Z)
                client.locOp(GEM_ROCK, GEM_X, GEM_Z, 1)
                client.seen("[mining.gem_rock_full]")
                client.command("empty")
                client.await("an empty backpack") { if (client.inventory(BACKPACK).all { it == null }) Unit else null }
                client.command("item $RUNE_PICKAXE")
                client.locOp(GEM_ROCK, GEM_X, GEM_Z, 1)
                val gems = Mining.GEM_ROCK_TABLE.map { it.obj }
                client.await("a gem", timeoutMillis = 60_000) { gems.firstOrNull { client.count(it) == 1 } }
            }
        }
    }

    private companion object {
        /** runiterock1 in the Mining Guild's members' area (OSRS Wiki, Runite rocks). */
        const val RUNITE = 11376
        const val RUNITE_X = 3056
        const val RUNITE_Z = 9721
        /** amethystrock1, one of the guild's south-east crystals. */
        const val AMETHYST = 11388
        const val AMETHYST_X = 3017
        const val AMETHYST_Z = 9701
        /** gemrock1 on Shilo Village's surface. */
        const val GEM_ROCK = 11380
        const val GEM_X = 2825
        const val GEM_Z = 3001
        const val RUNITE_ORE = 451
        const val AMETHYST_ORE = 21347
        const val RUNE_PICKAXE = 1275
        const val MITHRIL_PICKAXE = 1273
        const val GLORY = 1712
        const val POT = 1931
        const val BACKPACK = 93
        const val WORN = 94
        const val MINING = 14
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val WEAR_BUTTON = 3
        val GEM_KEYS = mapOf(1625 to "opal", 1627 to "jade", 1629 to "red_topaz", 1623 to "sapphire", 1621 to "emerald", 1619 to "ruby", 1617 to "diamond")
    }
}
