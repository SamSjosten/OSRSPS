package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.EquipmentTab
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
import osrsps.game.combat.Blowpipe
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.RangedWeapons
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * The blowpipe through the headless client, with the real ranged weapons and NPC combat files: scales and darts used on
 * it (IF_BUTTONT), Check, and wielding it and shooting a man dead with its darts. The
 * rl-client script test-runner/examples/combat-blowpipe.json does the shooting part in the real client.
 */
class HeadlessBlowpipeTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    // Shaped like the 241 cache's: param 4 ranged attack, 12 ranged strength, 14 attack speed; obj category 24 is thrown.
    private val loaded = ObjType(
        Blowpipe.LOADED, "Toxic blowpipe", iops = listOf(null, "Wield", "Check", "Unload", "Uncharge"), wearpos1 = WearPos.WEAPON,
        params = mapOf(4 to 30, 12 to 20, 14 to 3, EquipmentTab.PARAM_WORN_OP1 to "Check"), internalName = "toxic_blowpipe_loaded", category = 24,
    )
    private val empty = ObjType(Blowpipe.EMPTY, "Toxic blowpipe", iops = listOf(null, null, "Dismantle", null, "Drop"), wearpos1 = WearPos.WEAPON, params = mapOf(4 to 30, 14 to 3), internalName = "toxic_blowpipe", category = 24)
    private val darts = ObjType(DRAGON_DART, "Dragon dart", stackable = true, params = mapOf(12 to 35), internalName = "dragon_dart")
    private val scales = ObjType(Blowpipe.SCALE, "Zulrah's scales", stackable = true, internalName = "snakeboss_scale")

    // The unarmed and thrown rows of dbtable 78 as the 241 cache has them.
    private val styles = AttackStyles.fromRows(
        listOf(
            listOf<Any>(0) to listOf<Any>(
                0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)", 0,
                1, "Kick", "(Aggressive)<br>(Crush)<br>(Strength XP)", 0,
                3, "Block", "(Defensive)<br>(Crush)<br>(Defence XP)", 0,
            ),
            listOf<Any>(19) to listOf<Any>(
                0, "Accurate", "(Accurate)<br>(Standard)<br>(Ranged XP)", 0,
                1, "Rapid", "(Rapid)<br>(Standard)<br>(Ranged XP)", 0,
                3, "Longrange", "(Longrange)<br>(Standard)<br>(Ranged XP)<br>(Defence XP)", 0,
            ),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man)
        val objs = arrayOfNulls<ObjType>(Blowpipe.SCALE + 1)
        for (type in listOf(loaded, empty, darts, scales)) objs[type.id] = type
        val objTypes = ObjTypes(objs)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3226, 3218, source = "test")),
            varTypes = VarTypes(6_000, mapOf(VARBIT_WEAPON_CATEGORY to VarbitType(VARP_WEAPON_CATEGORY, 0, 5))),
            attackStyles = styles, npcCombat = NpcCombat.load(types, SeqNames.NONE),
            rangedWeapons = RangedWeapons.load(objTypes, SeqNames.NONE, SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slot(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.useOn(id: Int, target: Int) =
        ifButtonT(INVENTORY, INVENTORY_ITEMS, slot(id), id, INVENTORY, INVENTORY_ITEMS, slot(target), target)

    private fun HeadlessClient.itemOp(id: Int, iop: Int) = ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[iop - 1], slot(id), id)

    private fun HeadlessClient.carried(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    @Test
    fun `scales and darts used on the blowpipe load it, and it shoots a man dead with them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                client.await("the login rebuild") { client.position }
                client.command("item ${Blowpipe.EMPTY}")
                client.command("item ${Blowpipe.SCALE} 1000")
                client.command("item $DRAGON_DART $DARTS")
                client.await("the darts") { client.carried(DRAGON_DART).takeIf { it == DARTS } }
                client.useOn(Blowpipe.SCALE, Blowpipe.EMPTY)
                client.await("the blowpipe charged") { client.carried(Blowpipe.LOADED).takeIf { it == 1 } }
                client.useOn(DRAGON_DART, Blowpipe.LOADED)
                client.awaitMessage("the darts in it") { it == "Darts: Dragon dart x 50. Scales: 1,000 (6.1%)." }
                assertEquals(0, client.carried(DRAGON_DART))
                client.itemOp(Blowpipe.LOADED, WIELD)
                client.await("the blowpipe worn") { client.inventory(WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == Blowpipe.LOADED } }
                client.commandReply("god") { it.startsWith("You can") }
                client.npcOp(npc.index, 2)
                client.await("the man's death", timeoutMillis = 60_000) { if (client.npcs().none { it.id == man.id }) Unit else null }
                val left = client.commandReply("blowpipe") { it.startsWith("Darts:") }
                val match = Regex("""Darts: Dragon dart x (\d+)\. Scales: ([\d,]+) """).find(left)!!
                val used = DARTS - match.groupValues[1].toInt()
                val scalesUsed = 1_000 - match.groupValues[2].replace(",", "").toInt()
                assertTrue(used >= 1 && scalesUsed <= used, "$used darts and $scalesUsed scales: $left")
                // Nothing on the floor: darts don't come back without an Ava's device.
                assertTrue(client.groundObjs().none { it.id == DRAGON_DART })
            }
        }
    }

    companion object {
        const val DRAGON_DART = 11230
        const val DARTS = 50

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
