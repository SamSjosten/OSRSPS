package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.SpotanimNames
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.AttackStyles
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.RangedWeapons
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Chinchompas through the headless client, with the real ranged weapons and NPC combat files: the fuse styles on
 * `::combat`, each throw taking one from the stack in the weapon slot, and once `::multi` makes the zone multi-way, the
 * man beside the one aimed at caught in the blast. The rl-client script test-runner/examples/combat-chinchompa.json
 * makes the same clicks in the real client.
 */
class HeadlessChinchompaTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    // Shaped like the 241 cache's: param 4 ranged attack, 12 ranged strength, 14 attack speed; obj category 572 is chinchompas.
    private val chinchompa = ObjType(
        BLACK_CHINCHOMPA, "Black chinchompa", stackable = true, iops = listOf(null, "Wield", null, null, "Release"), wearpos1 = WearPos.WEAPON,
        params = mapOf(4 to 80, 12 to 30, 14 to 4), internalName = "chinchompa_black", category = 572,
    )

    // The unarmed and chinchompa rows of dbtable 78 as the 241 cache has them.
    private val styles = AttackStyles.fromRows(
        listOf(
            listOf<Any>(0) to listOf<Any>(
                0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)", 0,
                1, "Kick", "(Aggressive)<br>(Crush)<br>(Strength XP)", 0,
                3, "Block", "(Defensive)<br>(Crush)<br>(Defence XP)", 0,
            ),
            listOf<Any>(7) to listOf<Any>(
                0, "Short fuse", "(Short fuse)<br>(Heavy)<br>(Ranged XP)", 0,
                1, "Medium fuse", "(Medium fuse)<br>(Heavy)<br>(Ranged XP)", 0,
                3, "Long fuse", "(Long fuse)<br>(Heavy)<br>(Ranged XP)<br>(Defence XP)", 0,
            ),
        ),
    )

    // Two men west of the spawn, a tile apart diagonally, both in its zone (3216-3223 both ways).
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man)
        val objs = arrayOfNulls<ObjType>(BLACK_CHINCHOMPA + 1)
        objs[chinchompa.id] = chinchompa
        val objTypes = ObjTypes(objs)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3218, 3218, source = "test"), NpcSpawn(man, 3217, 3219, source = "test")),
            varTypes = VarTypes(6_000, mapOf(VARBIT_WEAPON_CATEGORY to VarbitType(VARP_WEAPON_CATEGORY, 0, 5))),
            attackStyles = styles, npcCombat = NpcCombat.load(types, SeqNames.NONE),
            rangedWeapons = RangedWeapons.load(objTypes, SeqNames.NONE, SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.wieldChinchompas(count: Int) {
        command("item $BLACK_CHINCHOMPA $count")
        val slot = await("the chinchompas in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == BLACK_CHINCHOMPA }.takeIf { it >= 0 } }
        ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[WIELD - 1], slot, BLACK_CHINCHOMPA)
        await("the chinchompas worn") { inventory(WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == BLACK_CHINCHOMPA } }
    }

    private fun HeadlessClient.men() = await("both men in view") { npcs().filter { it.id == man.id }.sortedBy { it.coord.x }.takeIf { it.size == 2 } }

    @Test
    fun `a throw takes one from the stack, and in multi-way combat the blast catches the man beside the one aimed at`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                // West first: the man at 3217 is beside the one at 3218.
                val (beside, aimed) = client.men()
                client.wieldChinchompas(CHINCHOMPAS)
                assertTrue(client.commandReply("combat man") { it.startsWith("Short fuse") }.startsWith("Short fuse (accurate, ranged heavy), speed 4"))
                assertTrue(client.commandReply("multi") { it.startsWith("Combat is") }.startsWith("Combat is multi-way"))
                client.commandReply("god") { it.startsWith("You can") }
                client.npcOp(aimed.index, 2)
                // Only a blast hits the other man, and a man that is hit comes for whoever hit him.
                client.await("the man beside coming for her", timeoutMillis = 30_000) {
                    client.npcs().firstOrNull { it.index == beside.index }?.takeIf { it.coord != beside.coord }
                }
                val left = client.inventory(WORN).getOrNull(WearPos.WEAPON)?.count ?: 0
                assertTrue(CHINCHOMPAS - left in 1..4, "${CHINCHOMPAS - left} thrown")
                // None to pick up again.
                assertTrue(client.groundObjs().none { it.id == BLACK_CHINCHOMPA })
            }
        }
    }

    @Test
    fun `the last one thrown leaves the weapon slot empty`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val (_, aimed) = client.men()
                client.commandReply("god") { it.startsWith("You can") }
                client.wieldChinchompas(1)
                client.npcOp(aimed.index, 2)
                client.await("the weapon slot empty", timeoutMillis = 20_000) { if (client.inventory(WORN).getOrNull(WearPos.WEAPON) == null) Unit else null }
                assertNull(client.inventory(WORN).getOrNull(WearPos.WEAPON))
                assertEquals(0, client.inventory(BACKPACK).count { it?.id == BLACK_CHINCHOMPA })
            }
        }
    }

    companion object {
        const val BLACK_CHINCHOMPA = 11959
        const val CHINCHOMPAS = 50

        // As HeadlessEquipmentTest has them.
        private const val BACKPACK = 93
        private const val WORN = 94
        private const val WIELD = 2
        private val IOP_BUTTON_OPS = intArrayOf(2, 3, 4, 6, 7)
        private const val INVENTORY = 149
        private const val INVENTORY_ITEMS = 0
        private const val VARBIT_WEAPON_CATEGORY = 357
        private const val VARP_WEAPON_CATEGORY = 843
    }
}
