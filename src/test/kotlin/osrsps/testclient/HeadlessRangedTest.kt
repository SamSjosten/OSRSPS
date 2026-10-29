package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.Interactions
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
import osrsps.game.combat.RangedCombat
import osrsps.game.combat.RangedWeapons
import osrsps.game.content.Ducks
import osrsps.game.npc.MoveRestrict
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap

/**
 * Bows through the headless client, with the real ranged weapons and NPC combat files: wielding a shortbow and arrows,
 * shooting a man from where you stand until he dies, the arrows used up and some on the floor, and the quiver running
 * dry. The rl-client script test-runner/examples/combat-ranged-shortbow.json makes the same clicks in the real client.
 */
class HeadlessRangedTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    // Shaped like the 241 cache's: param 4 ranged attack, 12 ranged strength, 14 attack speed; obj category 64 is bows.
    private val shortbow = ObjType(SHORTBOW, "Shortbow", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, params = mapOf(4 to 8, 14 to 4), internalName = "shortbow", category = 64)
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

    private val duck = NpcType(Ducks.DRAKE, "Duck", ops = listOf(null, "Attack", null, null, null), combatLevel = 1, internalName = "duck", stats = NpcStats(hitpoints = 3))

    /** With [pond], a pond east of the player over x 3228 to 3235, z 3214 to 3222, and a duck on it. */
    private fun server(dir: Path, pond: Boolean = false) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man, duck)
        val map = CollisionMap().apply {
            markLoaded(3222 shr 6, 3218 shr 6)
            if (pond) for (x in 3228..3235) for (z in 3214..3222) add(x, z, 0, CollisionFlag.FLOOR)
        }
        val ducks = if (pond) listOf(NpcSpawn(duck, 3230, 3218, restrict = MoveRestrict.BLOCKED, source = "test")) else emptyList()
        val objs = arrayOfNulls<ObjType>(BRONZE_ARROW + 1)
        for (type in listOf(shortbow, arrows)) objs[type.id] = type
        val objTypes = ObjTypes(objs)
        World(
            crcs, map, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3226, 3218, source = "test")) + ducks,
            varTypes = VarTypes(6_000, mapOf(VARBIT_WEAPON_CATEGORY to VarbitType(VARP_WEAPON_CATEGORY, 0, 5))),
            attackStyles = styles, npcCombat = NpcCombat.load(types, SeqNames.NONE),
            rangedWeapons = RangedWeapons.load(objTypes, SeqNames.NONE, SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.itemOp(id: Int, iop: Int) {
        val slot = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }
        ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[iop - 1], slot, id)
    }

    private fun HeadlessClient.worn(wearpos: Int) = inventory(WORN).getOrNull(wearpos)

    @Test
    fun `a shortbow shoots a man dead from where you stand, using arrows and leaving some on the floor`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                val start = client.await("the login rebuild") { client.position }
                client.command("item $SHORTBOW")
                client.itemOp(SHORTBOW, WIELD)
                client.command("item $BRONZE_ARROW $ARROWS")
                client.itemOp(BRONZE_ARROW, WIELD)
                client.await("the arrows worn") { client.worn(WearPos.AMMO)?.takeIf { it.id == BRONZE_ARROW && it.count == ARROWS } }
                assertEquals(
                    "Accurate (accurate, ranged standard), speed 4: attack roll 864, max hit 1",
                    client.commandReply("combat") { it.startsWith("Accurate") },
                )
                // He comes to her once hit, and the dice depend on when the clicks land: ::god keeps the fight hers.
                client.commandReply("god") { it.startsWith("You can") }
                val movesBefore = client.moves().size
                client.npcOp(npc.index, 2)
                client.await("the first arrow gone") { client.worn(WearPos.AMMO)?.takeIf { it.count < ARROWS } }
                val fighting = client.commandReply("fight") { it.startsWith("You are") }
                assertTrue(fighting.startsWith("You are fighting Man #${npc.index} ("), fighting)
                // Four tiles off is in a shortbow's range of 7: she shoots from where she stands.
                assertEquals(start, client.position)
                assertEquals(movesBefore, client.moves().size)
                // Every hit is 0 or 1, so seven of them however the dice fall: about 20 shots.
                client.await("the man's death", timeoutMillis = 60_000) { if (client.npcs().none { it.id == man.id }) Unit else null }
                assertEquals(7 * 4, client.stat(RANGED)!!.xp)
                assertEquals(1_154 + 9, client.stat(HITPOINTS)!!.xp)
                val used = ARROWS - (client.worn(WearPos.AMMO)?.count ?: 0)
                assertTrue(used >= 7, "$used used")
                // Four in five fall to the floor; with seven or more shots, some always do here.
                val onFloor = client.await("arrows on the floor") {
                    client.groundObjs().filter { it.id == BRONZE_ARROW }.sumOf { it.count }.takeIf { it > 0 }
                }
                assertTrue(onFloor <= used, "$onFloor of $used")
            }
        }
    }

    @Test
    fun `with no arrows worn the quiver is empty`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                client.await("the login rebuild") { client.position }
                client.command("item $SHORTBOW")
                client.itemOp(SHORTBOW, WIELD)
                client.await("the bow worn") { client.worn(WearPos.WEAPON)?.takeIf { it.id == SHORTBOW } }
                client.npcOp(npc.index, 2)
                client.awaitMessage("the empty quiver") { it == "[combat.no_ammo]" }
                assertEquals("You are not fighting anything.", client.commandReply("fight") { it.startsWith("You are") })
            }
        }
    }

    @Test
    fun `a duck on the water can't be reached to punch, and arrows shot at it are lost in the water`(@TempDir dir: Path) {
        server(dir, pond = true).use { server ->
            server.login("carol").use { client ->
                val target = client.await("the duck in view") { client.npcs().firstOrNull { it.id == duck.id } }
                client.await("the login rebuild") { client.position }
                client.npcOp(target.index, 2)
                client.awaitMessage("no way to the duck") { it == Interactions.CANT_REACH }
                client.command("item $SHORTBOW")
                client.itemOp(SHORTBOW, WIELD)
                client.command("item $BRONZE_ARROW $ARROWS")
                client.itemOp(BRONZE_ARROW, WIELD)
                client.await("the arrows worn") { client.worn(WearPos.AMMO)?.takeIf { it.id == BRONZE_ARROW && it.count == ARROWS } }
                client.commandReply("god") { it.startsWith("You can") }
                client.npcOp(target.index, 2)
                client.await("the duck's death", timeoutMillis = 60_000) { if (client.npcs().none { it.id == duck.id }) Unit else null }
                val used = ARROWS - (client.worn(WearPos.AMMO)?.count ?: 0)
                assertTrue(used >= 3, "$used used")
                // The last arrow lands as the duck dies; give the floor a few ticks to show any.
                client.awaitTicks(3)
                assertEquals(emptyList(), client.groundObjs().filter { it.id == BRONZE_ARROW })
                // It swam nowhere: a duck keeps to the water, and the player stands on the bank.
                assertTrue(client.position!!.x < 3228, "${client.position}")
            }
        }
    }

    companion object {
        const val SHORTBOW = 841
        const val BRONZE_ARROW = 882
        const val ARROWS = 100
        private const val HITPOINTS = 3
        private const val RANGED = 4

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
