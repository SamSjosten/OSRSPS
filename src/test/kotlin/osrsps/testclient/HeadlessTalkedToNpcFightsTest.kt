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
import osrsps.game.content.EdgevilleMonastery
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * An NPC one player is talking to still fights a player who attacks it: it walks to them and hits back, while the
 * talker's dialogue stays open. Before, the talk held it on its tile for as long as the dialogue stayed open, so an
 * archer could shoot it in safety (exploit review round 10, NPC-1).
 */
class HeadlessTalkedToNpcFightsTest {
    private val monk = NpcType(
        EdgevilleMonastery.MONK, "Monk", ops = listOf("Talk-to", "Attack", null, null, null), combatLevel = 5, internalName = "monk",
        stats = NpcStats(hitpoints = 15),
    )
    private val shortbow = ObjType(SHORTBOW, "Shortbow", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, params = mapOf(4 to 8, 14 to 4), internalName = "shortbow", category = 64)
    private val arrows = ObjType(BRONZE_ARROW, "Bronze arrow", stackable = true, tradeable = true, iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.AMMO, params = mapOf(12 to 7), internalName = "bronze_arrow")
    private val styles = AttackStyles.fromRows(
        listOf(
            listOf<Any>(0) to listOf<Any>(
                0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)", 0,
                1, "Kick", "(Aggressive)<br>(Crush)<br>(Strength XP)", 0,
                3, "Block", "(Defensive)<br>(Crush)<br>(Defence XP)", 0,
            ),
            listOf<Any>(3) to listOf<Any>(0, "Accurate", "(Accurate)<br>(Standard)<br>(Ranged XP)", 0),
        ),
    )

    /** Lumbridge's square, open ground, a monk standing four tiles east of where players log in. */
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(monk)
        val objs = arrayOfNulls<ObjType>(BRONZE_ARROW + 1)
        for (type in listOf(shortbow, arrows)) objs[type.id] = type
        val objTypes = ObjTypes(objs)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(monk, MONK_X, MONK_Z, wander = 0, source = "test")),
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
    fun `a monk held in a friend's open dialogue still walks to the archer shooting it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { friend ->
                friend.await("bob's login rebuild") { friend.position }
                val seen = friend.await("the monk in view") { friend.npcs().firstOrNull { it.id == monk.id } }
                friend.npcOp(seen.index, EdgevilleMonastery.TALK_TO)
                // Bob never clicks to continue, so the monk stays busy with him.
                friend.awaitDialogue("the monk's greeting") { it is DialogueView.Chat && it.text == "[edgeville_monk.greeting]" }

                server.login("alice").use { archer ->
                    archer.await("alice's login rebuild") { archer.position }
                    val npc = archer.await("the monk in view") { archer.npcs().firstOrNull { it.id == monk.id } }
                    archer.command("item $SHORTBOW")
                    archer.itemOp(SHORTBOW, WIELD)
                    archer.command("item $BRONZE_ARROW $ARROWS")
                    archer.itemOp(BRONZE_ARROW, WIELD)
                    archer.await("the arrows worn") { archer.inventory(WORN).getOrNull(WearPos.AMMO)?.takeIf { it.id == BRONZE_ARROW } }
                    archer.npcOp(npc.index, ATTACK)
                    archer.await("the monk walking to alice") {
                        archer.npcs().firstOrNull { it.index == npc.index }?.takeIf { it.coord != Coord(MONK_X, MONK_Z, 0) }
                    }
                    archer.await("the monk beside alice") {
                        val me = archer.position ?: return@await null
                        archer.npcs().firstOrNull { it.index == npc.index }
                            ?.takeIf { maxOf(Math.abs(it.coord.x - me.x), Math.abs(it.coord.z - me.z)) <= 1 }
                    }
                    assertTrue(friend.dialogueView is DialogueView.Chat, "bob's dialogue is still open")
                }
            }
        }
    }

    private companion object {
        const val MONK_X = 3226
        const val MONK_Z = 3218
        const val ATTACK = 2
        const val SHORTBOW = 841
        const val BRONZE_ARROW = 882
        const val ARROWS = 100
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
