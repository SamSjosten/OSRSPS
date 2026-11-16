package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.ArceuusRunecraft
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Runecraft step RC5 through the headless client: a dense block chipped off the runestone, venerated at the Dark Altar,
 * chiselled into four fragments and bound into blood runes; the 108-fragment cap and a depleted runestone.
 */
class HeadlessArceuusRunecraftTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(ArceuusRunecraft.DARK_BLOCK + 1)
        objs[ArceuusRunecraft.DENSE_BLOCK] = ObjType(ArceuusRunecraft.DENSE_BLOCK, "Dense essence block")
        objs[ArceuusRunecraft.DARK_BLOCK] = ObjType(ArceuusRunecraft.DARK_BLOCK, "Dark essence block")
        objs[ArceuusRunecraft.FRAGMENTS] = ObjType(ArceuusRunecraft.FRAGMENTS, "Dark essence fragments", iops = listOf("Count", null, null, null, "Drop"))
        objs[ArceuusRunecraft.CHISEL] = ObjType(ArceuusRunecraft.CHISEL, "Chisel")
        objs[BRONZE_PICKAXE] = ObjType(BRONZE_PICKAXE, "Bronze pickaxe")
        objs[BLOOD_RUNE] = ObjType(BLOOD_RUNE, "Blood rune", stackable = true)
        val map = CollisionMap().apply {
            markLoaded(RUNESTONE_X shr 6, RUNESTONE_Z shr 6)
            markLoaded(DARK_X shr 6, DARK_Z shr 6)
            markLoaded(BLOOD_X shr 6, BLOOD_Z shr 6)
        }
        World(
            crcs, map, objTypes = ObjTypes(objs), random = GameRandom(5),
            locTypes = LocTypes(
                mapOf(
                    RUNESTONE to LocType(RUNESTONE, "Dense runestone", listOf("Chip", null, null, null, null), LocCollisionType(width = 5, length = 5), multiloc = true),
                    ArceuusRunecraft.DARK_ALTAR to LocType(ArceuusRunecraft.DARK_ALTAR, "Dark Altar", listOf("Venerate", null, null, null, null), LocCollisionType(width = 3, length = 3)),
                    ArceuusRunecraft.BLOOD_ALTAR to LocType(ArceuusRunecraft.BLOOD_ALTAR, "Blood Altar", listOf("Bind", null, null, null, null), LocCollisionType(width = 4, length = 4)),
                ),
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(RUNESTONE, LocShape.CENTREPIECE_STRAIGHT, 0, RUNESTONE_X, RUNESTONE_Z, 0))
                add(ZoneLoc(ArceuusRunecraft.DARK_ALTAR, LocShape.CENTREPIECE_STRAIGHT, 0, DARK_X, DARK_Z, 0))
                add(ZoneLoc(ArceuusRunecraft.BLOOD_ALTAR, LocShape.CENTREPIECE_STRAIGHT, 0, BLOOD_X, BLOOD_Z, 0))
            },
            // arceuus_runestone_1 and _2: bits 10 and 11 of zeah_temp_1 (1319), as the 242 cache has them.
            varTypes = VarTypes(2_000, mapOf(4927 to VarbitType(1319, 10, 10), 4928 to VarbitType(1319, 11, 11))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.give(id: Int, count: Int) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) >= before + count) Unit else null }
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.setLevel(skill: String, id: Int, level: Int) {
        command("setlevel $skill $level")
        await("$skill $level") { stat(id)?.takeIf { it.level == level } }
    }

    private fun HeadlessClient.seen(text: String, n: Int = 1) = await("$n of $text") { messages().filter { it.text == text }.takeIf { it.size >= n } }

    private fun HeadlessClient.chiselBlock() {
        val chisel = inventory(BACKPACK).indexOfFirst { it?.id == ArceuusRunecraft.CHISEL }
        val block = inventory(BACKPACK).indexOfFirst { it?.id == ArceuusRunecraft.DARK_BLOCK }
        ifButtonT(InventoryTab.INTERFACE, InventoryTab.ITEMS, chisel, ArceuusRunecraft.CHISEL, InventoryTab.INTERFACE, InventoryTab.ITEMS, block, ArceuusRunecraft.DARK_BLOCK)
    }

    @Test
    fun `a dense block becomes four fragments and four blood runes each`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                await(client)
                client.setLevel("mining", MINING, 38)
                client.setLevel("crafting", CRAFTING, 38)
                client.setLevel("runecraft", RUNECRAFT, 77)
                client.setLevel("prayer", PRAYER, 10)
                client.give(BRONZE_PICKAXE, 1)
                client.give(ArceuusRunecraft.CHISEL, 1)
                client.tele(RUNESTONE_X + 2, RUNESTONE_Z - 1)
                client.locOp(RUNESTONE, RUNESTONE_X, RUNESTONE_Z, ArceuusRunecraft.CHIP)
                client.await("a dense block") { if (client.count(ArceuusRunecraft.DENSE_BLOCK) >= 1) Unit else null }
                val player = server.world.playerNamed("alice")!!
                val blocks = client.count(ArceuusRunecraft.DENSE_BLOCK)
                client.tele(DARK_X + 3, DARK_Z + 1)
                client.locOp(ArceuusRunecraft.DARK_ALTAR, DARK_X, DARK_Z, ArceuusRunecraft.VENERATE)
                client.seen("[runecraft_arceuus.venerate]")
                assertEquals(0, client.count(ArceuusRunecraft.DENSE_BLOCK))
                assertEquals(blocks, client.count(ArceuusRunecraft.DARK_BLOCK))
                assertEquals(10 - blocks, player.skills.currentLevel(Skill.PRAYER))
                client.chiselBlock()
                client.await("the fragments") { if (client.count(ArceuusRunecraft.FRAGMENTS) == 1) Unit else null }
                assertEquals(4 * blocks, server.world.arceuusRunecraft.fragments(player))
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 2, client.inventory(BACKPACK).indexOfFirst { it?.id == ArceuusRunecraft.FRAGMENTS }, ArceuusRunecraft.FRAGMENTS)
                client.seen("[runecraft_arceuus.fragments_count count=${4 * blocks}]")
                client.tele(BLOOD_X + 4, BLOOD_Z + 1)
                val start = client.stat(RUNECRAFT)!!.xp
                client.locOp(ArceuusRunecraft.BLOOD_ALTAR, BLOOD_X, BLOOD_Z, ArceuusRunecraft.BIND)
                client.seen("[runecraft_arceuus.bind rune=Blood rune]")
                client.await("the blood runes") { if (client.count(BLOOD_RUNE) == 4 * blocks) Unit else null }
                assertEquals(0, client.count(ArceuusRunecraft.FRAGMENTS))
                assertEquals(start + 4 * blocks * 238 / 10, client.await("the XP") { client.stat(RUNECRAFT)?.takeIf { it.xp > start } }.xp)
            }
        }
    }

    @Test
    fun `fragments stop at 108 and a depleted runestone only checks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                await(client)
                client.setLevel("crafting", CRAFTING, 38)
                val player = server.world.playerNamed("bob")!!
                client.give(ArceuusRunecraft.CHISEL, 1)
                client.give(ArceuusRunecraft.DARK_BLOCK, 27)
                client.chiselBlock()
                client.await("the fragments") { if (client.count(ArceuusRunecraft.FRAGMENTS) == 1) Unit else null }
                assertEquals(108, server.world.arceuusRunecraft.fragments(player))
                assertEquals(0, client.count(ArceuusRunecraft.DARK_BLOCK), "27 blocks are exactly 108")
                client.give(ArceuusRunecraft.DARK_BLOCK, 1)
                client.chiselBlock()
                client.seen("[runecraft_arceuus.chisel_full]")
                player.vars.setVarbit(4927, 1)
                client.tele(RUNESTONE_X + 2, RUNESTONE_Z - 1)
                client.locOp(RUNESTONE, RUNESTONE_X, RUNESTONE_Z, ArceuusRunecraft.CHIP)
                client.seen("[runecraft_arceuus.check_depleted]")
            }
        }
    }

    @Test
    fun `dropped fragments are lost and chiselling again starts from none`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                await(client)
                client.setLevel("crafting", CRAFTING, 38)
                val player = server.world.playerNamed("carol")!!
                client.give(ArceuusRunecraft.CHISEL, 1)
                client.give(ArceuusRunecraft.DARK_BLOCK, 2)
                client.chiselBlock()
                client.await("the fragments") { if (client.count(ArceuusRunecraft.FRAGMENTS) == 1) Unit else null }
                assertEquals(8, server.world.arceuusRunecraft.fragments(player))
                // Drop is iop5, button op 7 (InventoryTab.click).
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 7, client.inventory(BACKPACK).indexOfFirst { it?.id == ArceuusRunecraft.FRAGMENTS }, ArceuusRunecraft.FRAGMENTS)
                client.await("the fragments dropped") { if (client.count(ArceuusRunecraft.FRAGMENTS) == 0) Unit else null }
                assertEquals(0, player.charges[ArceuusRunecraft.FRAGMENT_CHARGES])
                client.give(ArceuusRunecraft.DARK_BLOCK, 1)
                client.chiselBlock()
                client.await("new fragments") { if (client.count(ArceuusRunecraft.FRAGMENTS) == 1) Unit else null }
                assertEquals(4, server.world.arceuusRunecraft.fragments(player))
            }
        }
    }

    private fun await(client: HeadlessClient) {
        client.await("the login rebuild") { client.position }
    }

    private companion object {
        const val BACKPACK = 93
        const val MINING = 14
        const val CRAFTING = 12
        const val PRAYER = 5
        const val RUNECRAFT = 20
        const val BRONZE_PICKAXE = 1265
        const val BLOOD_RUNE = 565
        const val RUNESTONE = 8981
        const val RUNESTONE_X = 1762
        const val RUNESTONE_Z = 3856
        const val DARK_X = 1715
        const val DARK_Z = 3882
        const val BLOOD_X = 1715
        const val BLOOD_Z = 3828
    }
}
