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
import osrsps.game.combat.Deaths
import osrsps.game.combat.MeleeCombat
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.PoweredStaves
import osrsps.game.combat.Spells
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.map.CollisionMap

/**
 * Tumeken's shadow and death: its charges are the player's ([osrsps.game.Charges]), so a death that loses the last
 * shadow must take them too, into the grave as runes. Admin commands (::item, ::hit me) only stand in for things a
 * player gets in play (items from the bank, an NPC's killing blow).
 */
class HeadlessShadowDeathTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))

    // As HeadlessShadowTest has them; the shadow is made cheap so three lobsters outrank it on death.
    private val shadow = ObjType(
        SHADOW, "Tumeken's shadow", cost = 1, iops = listOf(null, "Wield", "Check", "Charge", "Uncharge"), wearpos1 = WearPos.WEAPON,
        params = mapOf(3 to 35, 14 to 5, EquipmentTab.PARAM_WORN_OP1 to "Check"), internalName = "tumekens_shadow", category = 1,
    )
    private val uncharged = ObjType(
        SHADOW_UNCHARGED, "Tumeken's shadow (uncharged)", cost = 1, iops = listOf(null, "Wield", "Charge", null, "Drop"), wearpos1 = WearPos.WEAPON,
        params = mapOf(14 to 5), internalName = "tumekens_shadow_uncharged", category = 1,
    )
    private val runes = listOf(Triple(SOUL, "Soul rune", "soulrune"), Triple(CHAOS, "Chaos rune", "chaosrune")).map { (id, name, internal) ->
        ObjType(id, name, stackable = true, iops = listOf(null, null, null, null, "Drop"), internalName = internal)
    }
    private val lobster = ObjType(LOBSTER, "Lobster", cost = 150)

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

    private fun shadowServer(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man, grave)
        val objs = arrayOfNulls<ObjType>(SHADOW_UNCHARGED + 1)
        for (type in runes + listOf(shadow, uncharged, lobster)) objs[type.id] = type
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

    private fun HeadlessClient.itemOp(id: Int, iop: Int) = ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[iop - 1], slot(id), id)

    private fun HeadlessClient.carried(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.give(id: Int, count: Int) {
        val before = carried(id)
        command("item $id $count")
        await("$count of item $id") { carried(id).takeIf { it == before + count } }
    }

    private fun HeadlessClient.charges(): Int =
        Regex("""has ([\d,]+) charges""").find(commandReply("shadow") { it.startsWith("Your Tumeken's shadow has") })!!
            .groupValues[1].replace(",", "").toInt()

    private fun HeadlessClient.chargeUncharged(count: Int) {
        val prompts = clientScripts().count { it.id == ScriptContext.MESLAYER_MODE7 }
        itemOp(SHADOW_UNCHARGED, CHARGE_UNCHARGED)
        await("the count prompt") { if (clientScripts().count { it.id == ScriptContext.MESLAYER_MODE7 } > prompts) Unit else null }
        resumeCount(count)
        await("the staff charged") { carried(SHADOW).takeIf { it >= 1 } }
    }

    /**
     * The second exploit review of 29 Oct 2026 found the charges stayed with the player when the staff went to the grave:
     * after the grave collapsed, charging any other shadow by one and uncharging it paid back the runes for them all.
     */
    @Test
    fun `a death that loses the last shadow puts its charges in the grave as runes`(@TempDir dir: Path) {
        shadowServer(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                // 1,000 charges paid for with real runes, then three lobsters that outrank the staff on death.
                client.give(SHADOW_UNCHARGED, 1)
                client.give(SOUL, 2_000)
                client.give(CHAOS, 5_000)
                client.chargeUncharged(1_000)
                client.await("every rune spent") { if (client.carried(SOUL) == 0 && client.carried(CHAOS) == 0) Unit else null }
                assertEquals(1_000, client.charges())
                repeat(3) { client.give(LOBSTER, 1) }

                client.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                client.await(MeleeCombat.DEAD) { client.messages().firstOrNull { it.text == MeleeCombat.DEAD } }
                client.await("the respawn", timeoutMillis = 10_000) { client.position?.takeIf { it.x == 3222 && it.z == 3218 } }
                client.await("the lobsters kept, the staff lost") {
                    if (client.carried(LOBSTER) == 3 && client.carried(SHADOW) == 0) Unit else null
                }
                // The staff and the runes its charges were made of are in the grave; the player has none left.
                val g = server.world.deaths.all.single()
                assertEquals(setOf(SHADOW to 1, SOUL to 2_000, CHAOS to 5_000), g.items.contents().map { it.second.id to it.second.count }.toSet())
                assertEquals(0, client.charges())

                // With the grave gone, another shadow holds only what it is given.
                g.ticksLeft = 1
                client.await("the grave collapsed", timeoutMillis = 10_000) { if (server.world.deaths.all.isEmpty()) Unit else null }
                client.give(SHADOW_UNCHARGED, 1)
                client.give(SOUL, 2)
                client.give(CHAOS, 5)
                client.chargeUncharged(1)
                assertEquals(1, client.charges())
                client.itemOp(SHADOW, UNCHARGE)
                client.await("one charge's runes back") { if (client.carried(SOUL) == 2 && client.carried(CHAOS) == 5) Unit else null }
                assertTrue(client.carried(SHADOW_UNCHARGED) == 1)
            }
        }
    }

    @Test
    fun `a shadow in the bank keeps the charges through a death that loses the other`(@TempDir dir: Path) {
        shadowServer(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(SHADOW_UNCHARGED, 1)
                client.give(SOUL, 20)
                client.give(CHAOS, 50)
                client.chargeUncharged(10)
                repeat(3) { client.give(LOBSTER, 1) }
                server.world.playerNamed("bob")!!.bank.add(uncharged, 1)

                client.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                client.await("the respawn", timeoutMillis = 10_000) { client.position?.takeIf { it.x == 3222 && it.z == 3218 } }
                client.await("the staff lost") { if (client.carried(LOBSTER) == 3 && client.carried(SHADOW) == 0) Unit else null }
                assertEquals(listOf(SHADOW), server.world.deaths.all.single().items.contents().map { it.second.id })
                assertEquals(10, client.charges())
            }
        }
    }

    companion object {
        const val SHADOW = 27275
        const val SHADOW_UNCHARGED = 27277
        const val SOUL = PoweredStaves.SOUL_RUNE
        const val CHAOS = PoweredStaves.CHAOS_RUNE
        const val LOBSTER = 379
        private const val CHARGE_UNCHARGED = 3
        private const val UNCHARGE = 5

        private const val BACKPACK = 93
        private val IOP_BUTTON_OPS = intArrayOf(2, 3, 4, 6, 7)
        private const val INVENTORY = 149
        private const val INVENTORY_ITEMS = 0
        private const val VARBIT_WEAPON_CATEGORY = 357
        private const val VARP_WEAPON_CATEGORY = 843
    }
}
