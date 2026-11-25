package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
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
import osrsps.game.npc.MoveRestrict
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap

/**
 * Exploit fix R56A-1 through the headless client, with the real combat file: a steel dragon kept to ground the player
 * can't walk onto (as the ranged test's duck is to its pond) breathes dragonfire at a player shooting it from out of its
 * reach, so standing where it can't get to them is no safespot. test-runner/examples/metal-dragonfire-range.json shoots
 * a steel dragon in Brimhaven Dungeon in the real client.
 */
class HeadlessMetalDragonfireTest {
    private val steel = NpcType(
        STEEL_DRAGON, "Steel dragon", ops = listOf(null, "Attack", null, null, null), combatLevel = 246, size = 4,
        internalName = "steel_dragon", stats = NpcStats(hitpoints = 255, magic = 215, defence = 1),
    )

    // As HeadlessRangedTest has them, shaped like the cache's.
    private val shortbow = ObjType(SHORTBOW, "Shortbow", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, params = mapOf(4 to 8, 14 to 4), internalName = "shortbow", category = 64)
    private val arrows = ObjType(BRONZE_ARROW, "Bronze arrow", stackable = true, tradeable = true, iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.AMMO, params = mapOf(12 to 7), internalName = "bronze_arrow")
    private val styles = AttackStyles.fromRows(
        listOf(
            listOf<Any>(3) to listOf<Any>(
                0, "Accurate", "(Accurate)<br>(Standard)<br>(Ranged XP)", 0,
                1, "Rapid", "(Rapid)<br>(Standard)<br>(Ranged XP)", 0,
                3, "Longrange", "(Longrange)<br>(Standard)<br>(Ranged XP)<br>(Defence XP)", 0,
            ),
        ),
    )

    /** Ground the player can't walk onto over x 3228 to 3237, z 3214 to 3223, with the dragon kept to it. */
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(steel)
        val map = CollisionMap().apply {
            markLoaded(3222 shr 6, 3218 shr 6)
            for (x in 3228..3237) for (z in 3214..3223) add(x, z, 0, CollisionFlag.FLOOR)
        }
        val objs = arrayOfNulls<ObjType>(BRONZE_ARROW + 1)
        for (type in listOf(shortbow, arrows)) objs[type.id] = type
        val objTypes = ObjTypes(objs)
        World(
            crcs, map, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(steel, 3230, 3216, restrict = MoveRestrict.BLOCKED, source = "test")),
            varTypes = VarTypes(6_000, mapOf(VARBIT_WEAPON_CATEGORY to VarbitType(VARP_WEAPON_CATEGORY, 0, 5))),
            attackStyles = styles, npcCombat = NpcCombat.load(types, SeqNames.NONE),
            rangedWeapons = RangedWeapons.load(objTypes, SeqNames.NONE, SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.itemOp(id: Int, iop: Int) {
        val slot = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }
        ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[iop - 1], slot, id)
    }

    @Test
    fun `a steel dragon that can't reach a player shooting it breathes dragonfire at them from where it stands`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                val npc = client.await("the dragon in view") { client.npcs().firstOrNull { it.id == STEEL_DRAGON } }
                client.command("item $SHORTBOW")
                client.itemOp(SHORTBOW, WIELD)
                client.command("item $BRONZE_ARROW $ARROWS")
                client.itemOp(BRONZE_ARROW, WIELD)
                client.await("the arrows worn") { client.inventory(WORN).getOrNull(WearPos.AMMO)?.takeIf { it.id == BRONZE_ARROW } }
                client.commandReply("god") { it.startsWith("You can") }
                val start = client.position!!
                client.npcOp(npc.index, 2)
                client.awaitMessage("a breath from afar", timeoutMillis = 60_000) { it == "[dragonfire.burnt]" || it == "[dragonfire.resisted]" }
                assertTrue(client.position!!.x < 3228, "shot from the bank, out of its reach: ${client.position}")
            }
        }
    }

    private companion object {
        const val STEEL_DRAGON = 274
        const val SHORTBOW = 841
        const val BRONZE_ARROW = 882
        const val ARROWS = 1000
        const val BACKPACK = 93
        const val WORN = 94
        const val WIELD = 2
        val IOP_BUTTON_OPS = intArrayOf(2, 3, 4, 6, 7)
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val VARBIT_WEAPON_CATEGORY = 357
        const val VARP_WEAPON_CATEGORY = 843
    }
}
