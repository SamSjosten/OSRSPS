package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.ThievingLocks
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Wilderness step W3.5b through the headless client: a Rogues' Castle chest finds nothing below Thieving 84; searched at
 * 84 it gives a roll of the `rogues_castle` table and 701.7 XP, stands open, sets the rogue beside it on the looter and
 * restocks 34 ticks later. The chest stands on open ground at its 242 tile. The rl-client script
 * test-runner/examples/rogues-castle-chest.json does the same on the real map.
 */
class HeadlessRoguesCastleChestTest {
    private val rogue = NpcType(
        ThievingLocks.ROGUE, "Rogue", ops = listOf(null, "Attack", null, null, null), combatLevel = 135,
        internalName = "wilderness_rogue", stats = NpcStats(hitpoints = 125),
    )

    private val chest = ZoneLoc(ThievingLocks.CHEST_ROGUES, LocShape.CENTREPIECE_STRAIGHT, 0, CHEST_X, CHEST_Z, 0)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(TABLE_ITEMS.max() + 1)
        for (id in TABLE_ITEMS) objs[id] = ObjType(id, "Loot $id", stackable = id != PRAYER_POTION && id != HARD_CLUE)
        val objTypes = ObjTypes(objs)
        // The level 15 rogue too, or npc-combat.json's "rogue" falls back to this one's menu name.
        val types = NpcTypes.of(rogue, NpcType(526, "Rogue", internalName = "rogue"))
        val ops = listOf("Open", "Search for traps", null, null, null)
        World(
            crcs, CollisionMap().apply { markLoaded(CHEST_X shr 6, CHEST_Z shr 6) }, random = GameRandom(5), objTypes = objTypes,
            locTypes = LocTypes(
                mapOf(
                    ThievingLocks.CHEST_ROGUES to LocType(ThievingLocks.CHEST_ROGUES, "Chest", ops = ops, collision = LocCollisionType(width = 1, length = 1)),
                    ThievingLocks.CHEST_ROGUES_OPEN to LocType(ThievingLocks.CHEST_ROGUES_OPEN, "null", collision = LocCollisionType(width = 1, length = 1)),
                ),
            ),
            staticLocs = StaticLocs().apply { add(chest) },
            npcTypes = types, npcSpawns = listOf(NpcSpawn(rogue, CHEST_X + 3, CHEST_Z - 3, wander = 0, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(NpcTypes.of(), objTypes),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    private fun HeadlessClient.level(skill: String, id: Int, level: Int) {
        command("setlevel $skill $level")
        await("$skill $level") { stat(id)?.takeIf { it.level == level } }
    }

    @Test
    fun `a Rogues' chest searched at 84 gives its table, 701,7 XP and the rogues, and restocks after 34 ticks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ruth").use { client ->
                client.await("the login rebuild") { client.position }
                assertEquals(54, Wilderness.level(CHEST_X, CHEST_Z))
                val ruth = server.world.playerNamed("ruth")!!
                client.commandReply("god") { it.startsWith("You can") }
                client.level("thieving", THIEVING, 83)
                client.tele(CHEST_X, CHEST_Z - 1)
                client.locOp(chest.id, CHEST_X, CHEST_Z, ThievingLocks.OP_SECOND)
                client.await("nothing below 84") { Unit.takeIf { client.seen("[thieving_lock.nothing]") == 1 } }

                client.level("thieving", THIEVING, 84)
                val before = ruth.skills.tenths(Skill.THIEVING)
                client.locOp(chest.id, CHEST_X, CHEST_Z, ThievingLocks.OP_SECOND)
                client.await("the treasure") { Unit.takeIf { client.seen("[thieving_lock.treasure]") == 1 } }
                assertEquals(7017, ruth.skills.tenths(Skill.THIEVING) - before)
                // The backpack's update can reach the client after the message, so wait for it rather than read it now.
                val loot = client.await("the loot in the backpack") { client.inventory(BACKPACK).filterNotNull().takeIf { it.isNotEmpty() } }
                assertTrue(loot.isNotEmpty() && loot.all { it.id in TABLE_ITEMS }, "a roll of the table: $loot")
                client.await("the chest open") { Unit.takeIf { client.locAt(CHEST_X, CHEST_Z) == ThievingLocks.CHEST_ROGUES_OPEN } }
                val npc = server.world.npcs.all.first { it.type.id == ThievingLocks.ROGUE }
                client.await("the rogue on the looter") { Unit.takeIf { npc.target === ruth } }
                assertSame(ruth, npc.target)

                client.await("the restock", timeoutMillis = 30_000) { Unit.takeIf { client.locAt(CHEST_X, CHEST_Z) == ThievingLocks.CHEST_ROGUES } }
            }
        }
    }

    @Test
    fun `the rogues do no damage while their chest is searched, and do again once it is looted`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ivo").use { client ->
                client.await("the login rebuild") { client.position }
                val ivo = server.world.playerNamed("ivo")!!
                client.level("thieving", THIEVING, 84)
                client.level("hitpoints", HITPOINTS, 99)
                client.tele(CHEST_X, CHEST_Z - 1)
                val rogue = server.world.npcs.all.first { it.type.id == ThievingLocks.ROGUE }
                client.locOp(chest.id, CHEST_X, CHEST_Z, ThievingLocks.OP_SECOND)
                client.await("the trap found") { Unit.takeIf { client.seen("[thieving_lock.trap_found]") == 1 } }
                // Mid-search: a rogue's hit does nothing, though any other harm still lands.
                client.commandReply("pause") { true }
                val before = ivo.hitpoints
                assertEquals(0, server.world.meleeCombat.hitPlayer(ivo, rogue, 10, server.world.tick))
                assertEquals(before, ivo.hitpoints)
                assertEquals(5, server.world.meleeCombat.hitPlayer(ivo, null, 5, server.world.tick))
                client.commandReply("resume") { true }
                client.await("the treasure") { Unit.takeIf { client.seen("[thieving_lock.treasure]") == 1 } }
                assertEquals(-1, ivo.shieldedFrom, "the shield goes with the loot")
                client.commandReply("pause") { true }
                assertEquals(10, server.world.meleeCombat.hitPlayer(ivo, rogue, 10, server.world.tick))
                client.commandReply("resume") { true }
            }
        }
    }

    @Test
    fun `attacking any NPC mid-search ends the rogues' shield`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ivo").use { client ->
                client.await("the login rebuild") { client.position }
                val ivo = server.world.playerNamed("ivo")!!
                client.level("thieving", THIEVING, 84)
                client.level("hitpoints", HITPOINTS, 99)
                client.tele(CHEST_X, CHEST_Z - 1)
                val rogue = server.world.npcs.all.first { it.type.id == ThievingLocks.ROGUE }
                client.locOp(chest.id, CHEST_X, CHEST_Z, ThievingLocks.OP_SECOND)
                client.await("the trap found") { Unit.takeIf { client.seen("[thieving_lock.trap_found]") == 1 } }
                client.commandReply("pause") { true }
                assertEquals(0, server.world.meleeCombat.hitPlayer(ivo, rogue, 10, server.world.tick), "shielded")
                // A swing or a cast at an NPC, as one fired from the chest's own tile.
                ivo.attackedNpcTick = server.world.tick
                assertEquals(10, server.world.meleeCombat.hitPlayer(ivo, rogue, 10, server.world.tick), "no shield once they attack")
                client.commandReply("resume") { true }
                client.await("the treasure") { Unit.takeIf { client.seen("[thieving_lock.treasure]") == 1 } }

                assertTrue(ivo.shieldedFrom == -1)
            }
        }
    }

    @Test
    fun `the Rogues' chest is open loot, wanting two free slots`() {
        val chest = ThievingLocks.CHESTS.getValue(ThievingLocks.CHEST_ROGUES)
        assertEquals(84, chest.level)
        assertEquals(34, chest.respawnTicks)
        assertEquals(ThievingLocks.ROGUES_TABLE, chest.table)
        assertTrue(chest.stayOpen)
    }

    private companion object {
        const val CHEST_X = 3283
        const val CHEST_Z = 3946
        const val THIEVING = 17
        const val HITPOINTS = 3
        const val BACKPACK = 93
        const val PRAYER_POTION = 141
        const val HARD_CLUE = 2722
        val TABLE_ITEMS = setOf(561, 224, 563, 454, 995, 25770, 1618, 1622, 24607, PRAYER_POTION, 441, 562, 560, 24590, 24593, 1624, 1616, HARD_CLUE)
    }
}
