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
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.PoweredStaves
import osrsps.game.combat.Spells
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.map.CollisionMap

/**
 * Tumeken's shadow through the headless client, with the real spells and NPC combat files: charged from the uncharged
 * staff with Charge and the count prompt, its runes taken; Check in the pack and worn; wielded, it kills a man with its
 * own spell from Attack alone, a charge a cast and no runes; Uncharge gives the runes for what is left back; and a
 * soul rune used on the uncharged staff charges it again. The rl-client script
 * test-runner/examples/combat-magic-shadow.json does the fight in the real client.
 */
class HeadlessShadowTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    // Shaped like the 241 cache's: param 3 magic attack, 14 attack speed, 451 the worn Check; obj category 1, the staves'.
    private val shadow = ObjType(
        SHADOW, "Tumeken's shadow", iops = listOf(null, "Wield", "Check", "Charge", "Uncharge"), wearpos1 = WearPos.WEAPON,
        params = mapOf(3 to 35, 14 to 5, EquipmentTab.PARAM_WORN_OP1 to "Check"), internalName = "tumekens_shadow", category = 1,
    )
    private val uncharged = ObjType(
        SHADOW_UNCHARGED, "Tumeken's shadow (uncharged)", iops = listOf(null, "Wield", "Charge", null, "Drop"), wearpos1 = WearPos.WEAPON,
        params = mapOf(14 to 5), internalName = "tumekens_shadow_uncharged", category = 1,
    )
    private val runes = listOf(Triple(SOUL, "Soul rune", "soulrune"), Triple(CHAOS, "Chaos rune", "chaosrune")).map { (id, name, internal) ->
        ObjType(id, name, stackable = true, iops = listOf(null, null, null, null, "Drop"), internalName = internal)
    }

    // The unarmed and powered staff rows of dbtable 78 as the 241 cache has them.
    private val styles = AttackStyles.fromRows(
        listOf(
            listOf<Any>(0) to listOf<Any>(
                0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)", 0,
                1, "Kick", "(Aggressive)<br>(Crush)<br>(Strength XP)", 0,
                3, "Block", "(Defensive)<br>(Crush)<br>(Defence XP)", 0,
            ),
            listOf<Any>(24) to listOf<Any>(
                0, "Accurate", "(Accurate)<br>(Magic XP)", 0,
                1, "Accurate", "(Accurate)<br>(Magic XP)", 0,
                3, "Longrange", "(Longrange)<br>(Magic XP)<br>(Defence XP)", 0,
            ),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man)
        val objs = arrayOfNulls<ObjType>(SHADOW_UNCHARGED + 1)
        for (type in runes + listOf(shadow, uncharged)) objs[type.id] = type
        val objTypes = ObjTypes(objs)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3226, 3218, source = "test")),
            varTypes = VarTypes(6_000, mapOf(VARBIT_WEAPON_CATEGORY to VarbitType(VARP_WEAPON_CATEGORY, 0, 5))),
            attackStyles = styles, npcCombat = NpcCombat.load(types, SeqNames.NONE),
            spells = Spells.load(objTypes, SeqNames.NONE, SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slot(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.useOn(id: Int, target: Int) =
        ifButtonT(INVENTORY, INVENTORY_ITEMS, slot(id), id, INVENTORY, INVENTORY_ITEMS, slot(target), target)

    private fun HeadlessClient.itemOp(id: Int, iop: Int) = ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[iop - 1], slot(id), id)

    private fun HeadlessClient.carried(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.give(id: Int, count: Int) {
        val before = carried(id)
        command("item $id $count")
        await("$count of item $id") { carried(id).takeIf { it == before + count } }
    }

    @Test
    fun `charges that change while the count prompt is up are counted when it is answered`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(SHADOW_UNCHARGED, 1)
                client.give(SOUL, 20)
                client.give(CHAOS, 50)
                client.itemOp(SHADOW_UNCHARGED, CHARGE_UNCHARGED)
                client.await("the count prompt") { client.clientScripts().lastOrNull { it.id == ScriptContext.MESLAYER_MODE7 } }
                // As a second shadow, worn and casting, would change them.
                client.commandReply("shadow 100") { it.contains("charges=100]") }
                client.resumeCount(10)
                client.awaitMessage("the charges added") { it == "[combat.staff_check staff=Tumeken's shadow charges=110]" }
            }
        }
    }

    @Test
    fun `the shadow is charged, checked, kills a man with its charges alone and is uncharged`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                client.await("the login rebuild") { client.position }
                client.give(SHADOW_UNCHARGED, 1)
                // Enough for 15 charges: 30 soul runes and 75 chaos runes, at 2 and 5 a charge.
                client.give(SOUL, 31)
                client.give(CHAOS, 75)
                client.itemOp(SHADOW_UNCHARGED, CHARGE_UNCHARGED)
                client.await("the count prompt") { client.clientScripts().lastOrNull { it.id == ScriptContext.MESLAYER_MODE7 } }
                client.resumeCount(10)
                client.awaitMessage("the charges added") { it == "[combat.staff_check staff=Tumeken's shadow charges=10]" }
                client.await("the staff charged") { client.carried(SHADOW).takeIf { it == 1 } }
                assertEquals(listOf(11, 25, 0), listOf(client.carried(SOUL), client.carried(CHAOS), client.carried(SHADOW_UNCHARGED)))

                client.itemOp(SHADOW, CHECK)
                client.await("the Check reply") { client.messages().count { it.text == "[combat.staff_check staff=Tumeken's shadow charges=10]" }.takeIf { it == 2 } }

                client.itemOp(SHADOW, WIELD)
                client.await("the shadow worn") { client.inventory(WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == SHADOW } }
                client.ifButton(EquipmentTab.WORNITEMS, WORNITEMS_WEAPON, WORN_CHECK)
                client.await("the worn Check reply") { client.messages().count { it.text == "[combat.staff_check staff=Tumeken's shadow charges=10]" }.takeIf { it == 3 } }

                client.commandReply("setlevel magic 99") { it.contains("Magic") }
                val magicBefore = client.await("the level 99 XP") { client.stat(MAGIC)?.takeIf { it.level == 99 } }.xp
                // ::god keeps the fight hers; with auto retaliate off she only casts when she clicks.
                client.commandReply("god") { it.startsWith("You can") }
                client.ifButton(EquipmentTab.COMBAT, EquipmentTab.COMBAT_RETALIATE, 1)
                client.await("auto retaliate off") { if (client.varp(EquipmentTab.VARP_AUTO_RETALIATE_OFF) == 1) Unit else null }
                client.npcOp(npc.index, ATTACK)
                client.await("the man's death", timeoutMillis = 60_000) { if (client.npcs().none { it.id == man.id }) Unit else null }
                val left = Regex("""charges=(\d+)]""").find(client.commandReply("shadow") { it.startsWith("[combat.staff_check ") })!!
                    .groupValues[1].toInt()
                assertTrue(left in 0..9, "$left charges left")
                // No runes for the casts, and 2 Magic XP a point of the man's 7 hitpoints with no base XP.
                assertEquals(11 to 25, client.carried(SOUL) to client.carried(CHAOS))
                assertEquals(14, client.stat(MAGIC)!!.xp - magicBefore)

                client.ifButton(EquipmentTab.WORNITEMS, WORNITEMS_WEAPON, REMOVE)
                client.await("the shadow back in the pack") { client.carried(SHADOW).takeIf { it == 1 } }
                client.itemOp(SHADOW, UNCHARGE)
                client.await("the staff uncharged") { client.carried(SHADOW_UNCHARGED).takeIf { it == 1 } }
                client.await("the runes back") { if (client.carried(SOUL) == 11 + 2 * left && client.carried(CHAOS) == 25 + 5 * left) Unit else null }
                client.commandReply("shadow") { it == "[combat.staff_check staff=Tumeken's shadow charges=0]" }

                // A soul rune used on the uncharged staff asks the same: one charge this time.
                val prompts = client.clientScripts().count { it.id == ScriptContext.MESLAYER_MODE7 }
                client.useOn(SOUL, SHADOW_UNCHARGED)
                client.await("the second count prompt") { if (client.clientScripts().count { it.id == ScriptContext.MESLAYER_MODE7 } > prompts) Unit else null }
                client.resumeCount(1)
                client.await("the staff charged again") { client.carried(SHADOW).takeIf { it == 1 } }
                assertEquals(9 + 2 * left to 20 + 5 * left, client.carried(SOUL) to client.carried(CHAOS))
                client.commandReply("shadow") { it == "[combat.staff_check staff=Tumeken's shadow charges=1]" }
            }
        }
    }

    companion object {
        const val SHADOW = 27275
        const val SHADOW_UNCHARGED = 27277
        const val SOUL = PoweredStaves.SOUL_RUNE
        const val CHAOS = PoweredStaves.CHAOS_RUNE
        private const val ATTACK = 2
        private const val MAGIC = 6
        private const val WIELD = 2
        private const val CHECK = 3
        private const val CHARGE_UNCHARGED = 3
        private const val UNCHARGE = 5

        // Worn op 1 is Remove and op 2 the obj's param 451, here Check (EquipmentTab.clickWorn).
        private const val WORNITEMS_WEAPON = 18
        private const val REMOVE = 1
        private const val WORN_CHECK = 2

        // As HeadlessEquipmentTest has them.
        private const val BACKPACK = 93
        private const val WORN = 94
        private val IOP_BUTTON_OPS = intArrayOf(2, 3, 4, 6, 7)
        private const val INVENTORY = 149
        private const val INVENTORY_ITEMS = 0
        private const val VARBIT_WEAPON_CATEGORY = 357
        private const val VARP_WEAPON_CATEGORY = 843
    }
}
