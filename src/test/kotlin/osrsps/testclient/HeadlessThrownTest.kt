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
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Artisan 2 step A4a through the headless client, with the real ranged weapons and NPC combat files: rune knives thrown
 * from the weapon slot at a man, falling under him to be picked up again, and the light ballista firing rune javelins
 * from the ammo slot. The rl-client script test-runner/examples/combat-thrown.json makes the same clicks in the real client.
 */
class HeadlessThrownTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    // Shaped like the 242 cache's: param 4 ranged attack, 12 ranged strength, 14 attack speed; obj category 24 thrown, 567 ballistae.
    private val knife = ObjType(
        RUNE_KNIFE, "Rune knife", stackable = true, iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON,
        params = mapOf(4 to 25, 12 to 24, 14 to 3), internalName = "rune_knife", category = 24,
    )
    private val ballista = ObjType(
        LIGHT_BALLISTA, "Light ballista", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON,
        params = mapOf(4 to 110, 14 to 7), internalName = "light_ballista", category = 567,
    )
    private val javelin = ObjType(
        RUNE_JAVELIN, "Rune javelin", stackable = true, iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.AMMO,
        params = mapOf(12 to 124), internalName = "rune_javelin",
    )

    // The unarmed, thrown and crossbow rows of dbtable 78 as the cache has them.
    private val styles = AttackStyles.fromRows(
        listOf(
            listOf<Any>(0) to listOf<Any>(
                0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)", 0,
                1, "Kick", "(Aggressive)<br>(Crush)<br>(Strength XP)", 0,
                3, "Block", "(Defensive)<br>(Crush)<br>(Defence XP)", 0,
            ),
            listOf<Any>(19) to listOf<Any>(
                0, "Accurate", "(Accurate)<br>(Light)<br>(Ranged XP)", 0,
                1, "Rapid", "(Rapid)<br>(Light)<br>(Ranged XP)", 0,
                3, "Longrange", "(Longrange)<br>(Light)<br>(Ranged XP)<br>(Defence XP)", 0,
            ),
            listOf<Any>(5) to listOf<Any>(
                0, "Accurate", "(Accurate)<br>(Heavy)<br>(Ranged XP)", 0,
                1, "Rapid", "(Rapid)<br>(Heavy)<br>(Ranged XP)", 0,
                3, "Longrange", "(Longrange)<br>(Heavy)<br>(Ranged XP)<br>(Defence XP)", 0,
            ),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man)
        val objs = arrayOfNulls<ObjType>(LIGHT_BALLISTA + 1)
        for (type in listOf(knife, ballista, javelin)) objs[type.id] = type
        val objTypes = ObjTypes(objs)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3218, 3218, wander = 0, source = "test")),
            varTypes = VarTypes(6_000, mapOf(VARBIT_WEAPON_CATEGORY to VarbitType(VARP_WEAPON_CATEGORY, 0, 5))),
            attackStyles = styles, npcCombat = NpcCombat.load(types, SeqNames.NONE),
            rangedWeapons = RangedWeapons.load(objTypes, SeqNames.NONE, SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.wield(id: Int, count: Int, pos: Int) {
        command("item $id $count")
        val slot = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }
        ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[WIELD - 1], slot, id)
        await("item $id worn") { inventory(WORN).getOrNull(pos)?.takeIf { it.id == id } }
    }

    private fun HeadlessClient.theMan() = await("the man in view") { npcs().firstOrNull { it.id == man.id } }

    @Test
    fun `rune knives are thrown from the weapon slot at speed 3 and fall under the man`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.wield(RUNE_KNIFE, KNIVES, WearPos.WEAPON)
                assertTrue(client.commandReply("combat man") { it.startsWith("Accurate") }.startsWith("Accurate (accurate, ranged light), speed 3"))
                client.commandReply("god") { it.startsWith("You can") }
                client.npcOp(client.theMan().index, 2)
                client.await("a knife on the floor", timeoutMillis = 30_000) { client.groundObjs().firstOrNull { it.id == RUNE_KNIFE } }
                val left = client.inventory(WORN).getOrNull(WearPos.WEAPON)?.count ?: 0
                assertTrue(left < KNIVES, "$left left")
            }
        }
    }

    @Test
    fun `the light ballista fires rune javelins from the ammo slot`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.wield(LIGHT_BALLISTA, 1, WearPos.WEAPON)
                client.wield(RUNE_JAVELIN, JAVELINS, WearPos.AMMO)
                client.commandReply("god") { it.startsWith("You can") }
                client.npcOp(client.theMan().index, 2)
                client.await("a javelin fired", timeoutMillis = 20_000) {
                    client.inventory(WORN).getOrNull(WearPos.AMMO)?.takeIf { it.count < JAVELINS }
                }
            }
        }
    }

    companion object {
        const val RUNE_KNIFE = 868
        const val LIGHT_BALLISTA = 19478
        const val RUNE_JAVELIN = 830
        const val KNIVES = 50
        const val JAVELINS = 20

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
