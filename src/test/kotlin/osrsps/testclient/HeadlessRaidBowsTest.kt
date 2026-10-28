package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
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
import osrsps.game.combat.CombatMath
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.RangedWeapons
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * The raid's bows through the headless client, with the real ranged weapons and NPC combat files: the twisted bow's
 * numbers on `::combat`, and the Venator bow's shot going on to the man next to the one shot once `::multi` makes the
 * zone multi-way, and not before. The rl-client script test-runner/examples/combat-raid-bows.json makes the same clicks
 * in the real client.
 */
class HeadlessRaidBowsTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    // Shaped like the 241 cache's: param 4 ranged attack, 12 ranged strength, 14 attack speed; obj category 64 is bows.
    private val twistedBow = ObjType(TWISTED_BOW, "Twisted bow", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, params = mapOf(4 to 70, 12 to 20, 14 to 6), internalName = "twisted_bow", category = 64)
    private val venator = ObjType(VENATOR_BOW, "Venator bow", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, params = mapOf(4 to 90, 12 to 25, 14 to 5), internalName = "venator_bow", category = 64)
    private val arrows = ObjType(BRONZE_ARROW, "Bronze arrow", stackable = true, tradeable = true, iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.AMMO, params = mapOf(12 to 7), internalName = "bronze_arrow")

    // The unarmed and bow rows of dbtable 78 as the 241 cache has them.
    private val styles = AttackStyles.fromRows(
        listOf(
            listOf<Any>(0) to listOf<Any>(
                0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)", 0,
                1, "Kick", "(Aggressive)<br>(Crush)<br>(Strength XP)", 0,
                3, "Block", "(Defensive)<br>(Crush)<br>(Defence XP)", 0,
            ),
            listOf<Any>(3) to listOf<Any>(
                0, "Accurate", "(Accurate)<br>(Standard)<br>(Ranged XP)", 0,
                1, "Rapid", "(Rapid)<br>(Standard)<br>(Ranged XP)", 0,
                3, "Longrange", "(Longrange)<br>(Standard)<br>(Ranged XP)<br>(Defence XP)", 0,
            ),
        ),
    )

    // Three men west of the spawn, all in its zone (3216-3223 both ways): the second two tiles from the first.
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man)
        val objs = arrayOfNulls<ObjType>(VENATOR_BOW + 1)
        for (type in listOf(twistedBow, venator, arrows)) objs[type.id] = type
        val objTypes = ObjTypes(objs)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3218, 3218, source = "test"), NpcSpawn(man, 3218, 3220, source = "test")),
            varTypes = VarTypes(6_000, mapOf(VARBIT_WEAPON_CATEGORY to VarbitType(VARP_WEAPON_CATEGORY, 0, 5))),
            attackStyles = styles, npcCombat = NpcCombat.load(types, SeqNames.NONE),
            rangedWeapons = RangedWeapons.load(objTypes, SeqNames.NONE, SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.itemOp(id: Int, iop: Int) {
        val slot = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }
        ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[iop - 1], slot, id)
    }

    private fun HeadlessClient.wield(id: Int, count: Int = 1) {
        command("item $id $count")
        itemOp(id, WIELD)
        await("item $id worn") { inventory(WORN).firstOrNull { it?.id == id } }
    }

    private fun HeadlessClient.men() = await("both men in view") { npcs().filter { it.id == man.id }.sortedBy { it.coord.z }.takeIf { it.size == 2 } }

    @Test
    fun `the twisted bow's roll and max hit are scaled by the man's Magic of 1`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.wield(TWISTED_BOW)
                client.wield(BRONZE_ARROW, 10)
                // Level 1 on accurate: (1 + 3 + 8) * (70 + 64), and (12 * (20 + 7 + 64) + 320) / 640 = 2, then 40% and 54%.
                val roll = 12 * 134 * CombatMath.twistedBowAccuracy(1) / 100
                val max = 2 * CombatMath.twistedBowDamage(1) / 100
                assertEquals(
                    "Accurate (accurate, ranged standard), speed 6: attack roll $roll, max hit $max",
                    client.commandReply("combat man") { it.startsWith("Accurate") },
                )
            }
        }
    }

    @Test
    fun `in multi-way combat the Venator bow's shot goes on to the man beside the one shot`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val (first, second) = client.men()
                assertTrue(client.commandReply("multi") { it.startsWith("Combat is") }.startsWith("Combat is multi-way"))
                client.commandReply("god") { it.startsWith("You can") }
                client.wield(VENATOR_BOW)
                client.wield(BRONZE_ARROW, ARROWS)
                client.npcOp(first.index, 2)
                // Only a bounce hits the second man, and a man that is hit comes for whoever hit him.
                client.await("the second man coming for him", timeoutMillis = 30_000) {
                    client.npcs().firstOrNull { it.index == second.index }?.takeIf { it.coord != second.coord }
                }
                // The arrows go one a shot, however many men each one hits.
                val used = ARROWS - (client.inventory(WORN).getOrNull(WearPos.AMMO)?.count ?: 0)
                assertTrue(used in 1..4, "$used used")
            }
        }
    }

    @Test
    fun `in single-way combat the Venator bow's shot hits only the man shot`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                val (first, second) = client.men()
                client.commandReply("god") { it.startsWith("You can") }
                client.wield(VENATOR_BOW)
                client.wield(BRONZE_ARROW, ARROWS)
                client.npcOp(first.index, 2)
                client.await("three arrows shot", timeoutMillis = 30_000) {
                    client.inventory(WORN).getOrNull(WearPos.AMMO)?.takeIf { it.count <= ARROWS - 3 }
                }
                client.awaitTicks(3)
                assertEquals(second.coord, client.npcs().first { it.index == second.index }.coord)
            }
        }
    }

    companion object {
        const val TWISTED_BOW = 20997
        const val VENATOR_BOW = 27610
        const val BRONZE_ARROW = 882
        const val ARROWS = 100

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
