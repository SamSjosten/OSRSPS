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
import osrsps.game.Weapons
import osrsps.game.World
import osrsps.game.combat.AttackStyles
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.PoweredStaves
import osrsps.game.combat.RangedWeapons
import osrsps.game.combat.Spells
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Raid step 30, the Fractured Archive's uniques worn and fought with through the headless client, with the real ranged
 * weapons, spells and NPC combat files: the Obligator takes the Rondache off as a two-handed weapon, shows its own combat
 * tab and attacks every 5 ticks on Thrust and every 3 on Smash; the ascension crossbows refuse rune bolts and shoot
 * ascension bolts, every 3 ticks and 2 on Rapid; Zorya's tome is charged with astral and nature runes and kills a man
 * with its own spell, a charge a cast. The items are shaped like the 241 cache's (ArchiveUniquesAuditTest checks the
 * cache's against the Wiki's). The rl-client script test-runner/examples/archive-uniques.json makes the same clicks in
 * the real client.
 */
class HeadlessArchiveUniquesTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    private val wield = listOf(null, "Wield", null, null, "Drop")

    // The 241 cache's params: 0 to 10 the attack and defence bonuses and strength, 11 prayer, 12 ranged strength, 299
    // magic damage, 14 attack speed; the obj categories 2543, 37 and 2544.
    private val obligator = ObjType(
        OBLIGATOR, "Obligator", iops = wield, wearpos1 = WearPos.WEAPON, wearpos2 = WearPos.SHIELD, internalName = "obligator", category = 2543,
        params = mapOf(0 to 22, 1 to -8, 2 to 120, 6 to 16, 8 to 3, 9 to 8, 10 to 40, 11 to 3, 14 to 5),
    )
    private val rondache = ObjType(
        RONDACHE, "Rondache", iops = wield, wearpos1 = WearPos.SHIELD, internalName = "rondache",
        params = mapOf(3 to -5, 4 to -8, 5 to 35, 6 to 41, 7 to 38, 8 to -6, 9 to 55, 10 to 8),
    )
    private val crossbows = ObjType(
        CROSSBOWS, "Ascension crossbows", iops = wield, wearpos1 = WearPos.WEAPON, wearpos2 = WearPos.SHIELD, internalName = "ascension_crossbows", category = 37,
        params = mapOf(3 to 3, 4 to 34, 5 to -7, 6 to -5, 8 to 10, 14 to 3),
    )
    private val ascensionBolts = ObjType(
        ASCENSION_BOLTS, "Ascension bolts", stackable = true, iops = wield, wearpos1 = WearPos.AMMO, internalName = "ascension_bolts", category = 2542, params = mapOf(12 to 53),
    )
    private val runeBolts = ObjType(
        RUNE_BOLTS, "Runite bolts", stackable = true, iops = wield, wearpos1 = WearPos.AMMO, internalName = "xbows_crossbow_bolts_runite", params = mapOf(12 to 115),
    )
    private val tome = ObjType(
        TOME, "Zorya's tome", iops = listOf(null, "Wield", "Check", "Charge", "Uncharge"), wearpos1 = WearPos.WEAPON, wearpos2 = WearPos.SHIELD,
        internalName = "zoryas_tome", category = 2544, params = mapOf(3 to 45, 5 to 1, 6 to 5, 7 to 5, 8 to 10, 299 to 150, 11 to 2, 14 to 3),
    )
    private val uncharged = ObjType(
        TOME_UNCHARGED, "Zorya's tome (uncharged)", iops = listOf(null, "Wield", "Charge", null, "Drop"), wearpos1 = WearPos.WEAPON, wearpos2 = WearPos.SHIELD,
        internalName = "zoryas_tome_uncharged", category = 2544, params = mapOf(3 to 45, 5 to 1, 6 to 5, 7 to 5, 8 to 10, 299 to 150, 14 to 3),
    )
    private val runes = listOf(ASTRAL to "Astral rune", NATURE to "Nature rune").map { (id, name) ->
        ObjType(id, name, stackable = true, iops = listOf(null, null, null, null, "Drop"))
    }

    // The unarmed, crossbow, extending chainmace and self-powering book rows of dbtable 78 as the 241 cache has them.
    private val styles = AttackStyles.fromRows(
        listOf(
            listOf<Any>(0) to listOf<Any>(
                0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)", 0,
                1, "Kick", "(Aggressive)<br>(Crush)<br>(Strength XP)", 0,
                3, "Block", "(Defensive)<br>(Crush)<br>(Defence XP)", 0,
            ),
            listOf<Any>(Weapons.CROSSBOW) to listOf<Any>(
                0, "Accurate", "(Accurate)<br>(Heavy)<br>(Ranged XP)", 0,
                1, "Rapid", "(Rapid)<br>(Heavy)<br>(Ranged XP)", 0,
                3, "Longrange", "(Longrange)<br>(Heavy)<br>(Ranged XP)<br>(Defence XP)", 0,
            ),
            listOf<Any>(Weapons.EXTENDING_CHAINMACE) to listOf<Any>(
                0, "Thrust", "(Accurate)<br>(Crush)<br>(Piercing)<br>(Attack XP)", 0,
                1, "Thrust", "(Aggressive)<br>(Crush)<br>(Piercing)<br>(Strength XP)", 0,
                2, "Smash", "(Aggressive)<br>(Crush)<br>(Strength XP)", 0,
            ),
            listOf<Any>(Weapons.POWERED_BOOK) to listOf<Any>(
                0, "Accurate", "(Accurate)<br>(Magic XP)", 0,
                1, "Accurate", "(Accurate)<br>(Magic XP)", 0,
                3, "Longrange", "(Longrange)<br>(Magic XP)<br>(Defence XP)", 0,
            ),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man)
        val objs = arrayOfNulls<ObjType>(TOME_UNCHARGED + 1)
        for (type in runes + listOf(obligator, rondache, crossbows, ascensionBolts, runeBolts, tome, uncharged)) objs[type.id] = type
        val objTypes = ObjTypes(objs)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3226, 3218, source = "test")),
            varTypes = VarTypes(6_000, mapOf(VARBIT_WEAPON_CATEGORY to VarbitType(VARP_WEAPON_CATEGORY, 0, 5))),
            attackStyles = styles, npcCombat = NpcCombat.load(types, SeqNames.NONE),
            rangedWeapons = RangedWeapons.load(objTypes, SeqNames.NONE, SpotanimNames.NONE),
            spells = Spells.load(objTypes, SeqNames.NONE, SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slot(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.itemOp(id: Int, iop: Int) = ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[iop - 1], slot(id), id)

    private fun HeadlessClient.carried(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.worn(slot: Int) = inventory(WORN).getOrNull(slot)

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = carried(id)
        command("item $id $count")
        await("$count of item $id") { carried(id).takeIf { it == before + count } }
    }

    private fun HeadlessClient.wield(id: Int, count: Int = 1, slot: Int = WearPos.WEAPON) {
        give(id, count)
        itemOp(id, WIELD)
        await("item $id worn") { worn(slot)?.takeIf { it.id == id } }
    }

    private fun HeadlessClient.style(button: Int) {
        ifButton(EquipmentTab.COMBAT, EquipmentTab.STYLE_BUTTONS[button], 1)
        await("style $button picked") { if (varp(VARP_COM_MODE) == button) Unit else null }
    }

    private fun HeadlessClient.category() = (varp(VARP_WEAPON_CATEGORY) ?: 0) and 0x3f

    private fun HeadlessClient.combat(start: String) = commandReply("combat man") { it.startsWith(start) }

    @Test
    fun `the Obligator takes the Rondache off, has its own combat tab and attacks every 5 ticks on Thrust and 3 on Smash`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel attack 85") { it.contains("Attack") }
                client.commandReply("setlevel defence 80") { it.contains("Defence") }
                client.wield(RONDACHE, slot = WearPos.SHIELD)
                client.wield(OBLIGATOR)
                client.await("the Rondache back in the pack") { client.carried(RONDACHE).takeIf { it == 1 } }
                assertEquals(null, client.worn(WearPos.SHIELD))
                client.await("the Obligator's combat tab") { if (client.category() == Weapons.EXTENDING_CHAINMACE) Unit else null }
                assertTrue(client.combat("Thrust").startsWith("Thrust (accurate, crush), speed 5: "))
                client.style(2)
                assertTrue(client.combat("Smash").startsWith("Smash (aggressive, crush), speed 3: "))
            }
        }
    }

    @Test
    fun `the ascension crossbows refuse rune bolts, and shoot ascension bolts every 3 ticks, 2 on Rapid`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel ranged 80") { it.contains("Ranged") }
                client.commandReply("god") { it.startsWith("You can") }
                client.wield(CROSSBOWS)
                client.await("the crossbow combat tab") { if (client.category() == Weapons.CROSSBOW) Unit else null }
                client.wield(RUNE_BOLTS, 10, WearPos.AMMO)
                client.npcOp(npc.index, ATTACK)
                client.awaitMessage("the refusal") { it == "[combat.wrong_bolts]" }
                assertEquals(10, client.worn(WearPos.AMMO)?.count)

                client.wield(ASCENSION_BOLTS, 20, WearPos.AMMO)
                assertTrue(client.combat("Accurate").startsWith("Accurate (accurate, ranged heavy), speed 3: "))
                client.style(1)
                assertTrue(client.combat("Rapid").startsWith("Rapid (rapid, ranged heavy), speed 2: "))
                client.npcOp(npc.index, ATTACK)
                client.await("bolts shot", timeoutMillis = 30_000) { client.worn(WearPos.AMMO)?.takeIf { it.count <= 18 } }
            }
        }
    }

    @Test
    fun `Zorya's tome is charged with astral and nature runes and kills a man with its own spell`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                client.await("the login rebuild") { client.position }
                client.give(TOME_UNCHARGED)
                // Enough for 10 charges and a nature rune over: 3 astral and 2 nature runes a charge.
                client.give(ASTRAL, 30)
                client.give(NATURE, 21)
                client.itemOp(TOME_UNCHARGED, CHARGE_UNCHARGED)
                client.await("the count prompt") { client.clientScripts().lastOrNull { it.id == osrsps.game.script.ScriptContext.MESLAYER_MODE7 } }
                client.resumeCount(20)
                client.awaitMessage("the charges added") { it == "[combat.staff_check staff=Zorya's tome charges=10]" }
                client.await("the tome charged") { client.carried(TOME).takeIf { it == 1 } }
                assertEquals(0 to 1, client.carried(ASTRAL) to client.carried(NATURE))

                client.commandReply("setlevel magic 99") { it.contains("Magic") }
                client.wield(TOME)
                client.await("the tome's combat tab") { if (client.category() == Weapons.POWERED_BOOK) Unit else null }
                // A third of 99, less 9, is 24; the tome's own +15% magic damage makes it 27.
                val line = client.combat("Accurate")
                assertTrue(line.startsWith("Accurate (Zorya's tome), speed 3: ") && line.endsWith("max hit 27"), line)
                client.commandReply("god") { it.startsWith("You can") }
                client.ifButton(EquipmentTab.COMBAT, EquipmentTab.COMBAT_RETALIATE, 1)
                client.await("auto retaliate off") { if (client.varp(EquipmentTab.VARP_AUTO_RETALIATE_OFF) == 1) Unit else null }
                client.npcOp(npc.index, ATTACK)
                client.await("the man's death", timeoutMillis = 60_000) { if (client.npcs().none { it.id == man.id }) Unit else null }
                val left = Regex("""charges=(\d+)]""").find(client.commandReply("tome") { it.startsWith("[combat.staff_check ") })!!.groupValues[1].toInt()
                assertTrue(left in 0..9, "$left charges left")
                assertEquals(0 to 1, client.carried(ASTRAL) to client.carried(NATURE))
            }
        }
    }

    companion object {
        const val OBLIGATOR = 34715
        const val RONDACHE = 34719
        const val CROSSBOWS = 34681
        const val ASCENSION_BOLTS = 34684
        const val RUNE_BOLTS = 9144
        const val TOME = 34725
        const val TOME_UNCHARGED = 34727
        const val ASTRAL = PoweredStaves.ASTRAL_RUNE
        const val NATURE = PoweredStaves.NATURE_RUNE
        private const val ATTACK = 2
        private const val WIELD = 2
        private const val CHARGE_UNCHARGED = 3

        // As HeadlessEquipmentTest has them.
        private const val BACKPACK = 93
        private const val WORN = 94
        private val IOP_BUTTON_OPS = intArrayOf(2, 3, 4, 6, 7)
        private const val INVENTORY = 149
        private const val INVENTORY_ITEMS = 0
        private const val VARBIT_WEAPON_CATEGORY = 357
        private const val VARP_WEAPON_CATEGORY = 843
        private const val VARP_COM_MODE = 43
    }
}
