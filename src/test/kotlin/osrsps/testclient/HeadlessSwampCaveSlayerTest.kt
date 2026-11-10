package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.SeqNames
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.Rockslugs
import osrsps.game.content.WallBeasts
import osrsps.game.npc.MoveRestrict
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Misthalin piece 7 step 3b through the headless client, with the real combat and drop data: a rockslug held at 1
 * hitpoint and finished by a bag of salt (one wasted on it at full health), a brine sabre killing one outright, and a
 * wall beast grabbing a player with no helmet each time they step in front of its hole and coming out to fight one in a
 * spiny helmet. The rl-client script test-runner/examples/swamp-caves-slayer.json does the same in the real caves.
 */
class HeadlessSwampCaveSlayerTest {
    private val slug = NpcType(
        Rockslugs.SLUGS[0], "Rockslug", ops = listOf(null, "Attack", null, null, null), combatLevel = 29, internalName = "slayer_rockslug",
        stats = NpcStats(hitpoints = 27, attack = 22, strength = 27, defence = 27),
    )
    private val hole = NpcType(WallBeasts.HOLE, "Hole in the wall", ops = listOf(null, null, null, null, null), internalName = "swamp_wallbeast",
        stats = NpcStats(hitpoints = 105, attack = 30, strength = 30, defence = 16))
    private val beast = NpcType(
        WallBeasts.BEAST, "Wall beast", ops = listOf(null, "Attack", null, null, null), combatLevel = 49, internalName = "swamp_wallbeast_combat",
        stats = NpcStats(hitpoints = 105, attack = 30, strength = 30, defence = 16),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(slug, hole, beast)
        val objTypes = DropTestItems.objTypes(
            listOf(
                ObjType(Rockslugs.BAG_OF_SALT, "Bag of salt", tradeable = true, stackable = true),
                ObjType(Rockslugs.BRINE_SABRE, "Brine sabre", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON),
                ObjType(WallBeasts.SPINY_HELMET, "Spiny helmet", iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = WearPos.HEAD),
            ),
        )
        World(
            crcs, CollisionMap().apply { markLoaded(3220 shr 6, 9570 shr 6); markLoaded(3164 shr 6, 9556 shr 6) },
            random = GameRandom(11), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(
                NpcSpawn(slug, 3222, 9571, source = "test"),
                NpcSpawn(hole, 3164, 9556, restrict = MoveRestrict.NOMOVE, source = "test"),
            ),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.seen(text: String, times: Int = 1) =
        await("$text x$times") { messages().filter { it.text == text }.takeIf { it.size >= times } }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slot(id: Int) = await("item $id in the backpack") { inventory(Inventory.INV).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.wear(id: Int) {
        command("item $id 1")
        ifButton(INVENTORY, INVENTORY_ITEMS, WEAR_BUTTON, slot(id), id)
        await("item $id worn") { inventory(WORN).firstOrNull { it?.id == id } }
    }

    private fun HeadlessClient.strong() {
        commandReply("god") { it.startsWith("You can") }
        command("setlevel slayer 35")
        command("setlevel attack 99")
        command("setlevel strength 99")
        await("Strength 99") { stat(STRENGTH)?.takeIf { it.level == 99 } }
    }

    @Test
    fun `a rockslug stops at 1 hitpoint and a bag of salt finishes it, while one used at full health is wasted`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3222, 9573)
                val view = client.await("the rockslug in view") { client.npcs().firstOrNull { it.id == slug.id } }
                val npc = server.world.npcs[view.index]!!
                client.strong()
                client.command("item ${Rockslugs.BAG_OF_SALT} 2")
                client.await("the salt") { if (client.held(Rockslugs.BAG_OF_SALT) == 2) Unit else null }

                client.npcTarget(view.index, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slot(Rockslugs.BAG_OF_SALT), Rockslugs.BAG_OF_SALT)
                client.seen("[swamp_slayer.salt_wasted]")
                assertEquals(1, client.held(Rockslugs.BAG_OF_SALT))
                assertEquals(27, npc.hitpoints)

                client.npcOp(view.index, 2)
                client.await("the rockslug at 1", timeoutMillis = 60_000) { if (npc.hitpoints == 1) Unit else null }
                client.awaitTicks(2)
                val xp = client.stat(ATTACK)!!.xp
                client.awaitTicks(8)
                assertEquals(1, npc.hitpoints, "no hit takes it below 1")
                assertEquals(xp, client.stat(ATTACK)!!.xp, "and no XP for hits it can't take")
                assertFalse(npc.dead)
                assertNull(npc.target, "it stops fighting")

                client.npcTarget(view.index, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slot(Rockslugs.BAG_OF_SALT), Rockslugs.BAG_OF_SALT)
                client.seen("[swamp_slayer.salt_kills]")
                client.await("the rockslug's death") { if (npc.dead) Unit else null }
                assertEquals(0, client.held(Rockslugs.BAG_OF_SALT))
            }
        }
    }

    @Test
    fun `a brine sabre kills a rockslug outright`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3222, 9573)
                val view = client.await("the rockslug in view") { client.npcs().firstOrNull { it.id == slug.id } }
                val npc = server.world.npcs[view.index]!!
                client.strong()
                client.wear(Rockslugs.BRINE_SABRE)
                client.npcOp(view.index, 2)
                client.await("the rockslug's death", timeoutMillis = 60_000) { if (npc.dead) Unit else null }
            }
        }
    }

    @Test
    fun `a wall beast grabs a player with no helmet each time they step in front, and comes out to fight one in a spiny helmet`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hitpoints 99")
                client.await("Hitpoints 99") { client.stat(HITPOINTS)?.takeIf { it.level == 99 } }
                client.tele(3164, 9553)
                val view = client.await("the hole in view") { client.npcs().firstOrNull { it.id == WallBeasts.HOLE } }
                val npc = server.world.npcs[view.index]!!
                val player = server.world.playerNamed("carol")!!

                client.tele(3164, 9555)
                client.seen("[swamp_slayer.grabbed]")
                assertTrue(player.stunnedUntil > 0, "held")
                // Held: a step away goes nowhere.
                client.walk(3164, 9551)
                client.awaitTicks(1)
                assertEquals(Coord(3164, 9555, 0), client.position)
                assertEquals(npc, player.attackedBy, "in combat with the hole, so no logging out")

                // A second player in front of it is grabbed once too, and neither is grabbed again while they stay.
                server.login("dave").use { dave ->
                    dave.await("dave's login rebuild") { dave.position }
                    dave.tele(3164, 9555)
                    dave.seen("[swamp_slayer.grabbed]")
                    dave.awaitTicks(WallBeasts.GRAB_TICKS + 4)
                    assertEquals(1, dave.messages().count { it.text == "[swamp_slayer.grabbed]" }, "dave, once while staying")
                    assertEquals(1, client.messages().count { it.text == "[swamp_slayer.grabbed]" }, "carol, once while staying")
                }
                assertEquals(WallBeasts.HOLE, npc.type.id)

                client.tele(3164, 9553)
                client.tele(3164, 9555)
                client.seen("[swamp_slayer.grabbed]", times = 2)

                client.tele(3164, 9553)
                client.wear(WallBeasts.SPINY_HELMET)
                client.tele(3164, 9555)
                client.seen("[swamp_slayer.helmet_repels]")
                client.await("the beast out") { if (npc.type.id == WallBeasts.BEAST) Unit else null }
                assertEquals(player, npc.target)
                assertEquals(2, client.messages().count { it.text == "[swamp_slayer.grabbed]" })
                assertEquals(3164 to 9556, npc.x to npc.z, "it never leaves its hole")

                // Away from it and out of a fight, it goes back in.
                client.tele(3164, 9545)
                client.await("back in its hole", timeoutMillis = 30_000) { if (npc.type.id == WallBeasts.HOLE) Unit else null }
            }
        }
    }

    private companion object {
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val WORN = 94
        const val WEAR_BUTTON = 3
        const val ATTACK = 0
        const val STRENGTH = 2
        const val HITPOINTS = 3
    }
}
