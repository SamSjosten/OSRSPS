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
import osrsps.game.combat.Autocast
import osrsps.game.combat.MagicCombat
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.Spells
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Spells through the headless client, with the real spells and NPC combat files: Fire Strike picked in the spellbook
 * and cast on a man until he dies, each cast taking its runes and giving its XP, and the refusals for too low a level
 * and too few runes, with a staff of fire then standing in for the fire runes; and Fire Strike picked for autocast on
 * the combat tab, which then fights the man to his death from Attack alone. The rl-client scripts
 * test-runner/examples/combat-magic-strike.json and combat-magic-autocast.json make the same clicks in the real client.
 */
class HeadlessMagicTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    // Fire Strike's obj as the 241 cache has it: 601 name, 604 level, 596 its spellbook component, 365-370 its runes,
    // 1935 its base max hit.
    private val fireStrike = ObjType(
        3279, "Fire Strike", internalName = "13_fire_strike",
        params = mapOf(601 to "Fire Strike", 604 to 13, 596 to (218 shl 16 or FIRE_STRIKE), 365 to MIND, 366 to 1, 367 to FIRE, 368 to 3, 369 to AIR, 370 to 2, 1935 to 8),
    )
    private val runes = listOf(Triple(AIR, "Air rune", "airrune"), Triple(FIRE, "Fire rune", "firerune"), Triple(MIND, "Mind rune", "mindrune")).map { (id, name, internal) ->
        ObjType(id, name, stackable = true, iops = listOf(null, null, null, null, "Drop"), internalName = internal)
    }

    // Obj category 1 is the staves; param 3 magic attack, 14 attack speed.
    private val staff = ObjType(STAFF_OF_FIRE, "Staff of fire", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, params = mapOf(3 to 10, 14 to 5), internalName = "staff_of_fire", category = 1)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man)
        val objs = arrayOfNulls<ObjType>(fireStrike.id + 1)
        for (type in runes + listOf(fireStrike, staff)) objs[type.id] = type
        val objTypes = ObjTypes(objs)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3226, 3218, source = "test")),
            varTypes = VarTypes(6_000, mapOf(VARBIT_WEAPON_CATEGORY to VarbitType(VARP_WEAPON_CATEGORY, 0, 5))),
            npcCombat = NpcCombat.load(types, SeqNames.NONE),
            spells = Spells.load(objTypes, SeqNames.NONE, SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.carried(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.give(id: Int, count: Int) {
        val before = carried(id)
        command("item $id $count")
        await("$count of item $id") { carried(id).takeIf { it == before + count } }
    }

    private fun HeadlessClient.manInView() = npcs().any { it.id == man.id }

    @Test
    fun `Fire Strike cast from the spellbook kills a man, a cast at a time, with its runes and XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                val start = client.await("the login rebuild") { client.position }
                client.commandReply("setlevel magic 13") { it.contains("Magic") }
                client.give(MIND, 100)
                client.give(FIRE, 300)
                client.give(AIR, 200)
                // He comes to her once hit, and the dice depend on when the clicks land: ::god keeps the fight hers, and
                // with auto retaliate off she doesn't punch him between casts (a punch would take XP from the spells).
                client.commandReply("god") { it.startsWith("You can") }
                client.ifButton(EquipmentTab.COMBAT, EquipmentTab.COMBAT_RETALIATE, 1)
                client.await("auto retaliate off") { if (client.varp(EquipmentTab.VARP_AUTO_RETALIATE_OFF) == 1) Unit else null }
                val magicBefore = client.await("the level 13 XP") { client.stat(MAGIC)?.takeIf { it.level == 13 } }.xp
                var casts = 0
                while (client.manInView() && casts < 40) {
                    val before = client.carried(MIND)
                    client.npcTarget(npc.index, Spells.SPELLBOOK, FIRE_STRIKE)
                    client.await("cast ${casts + 1}") { if (client.carried(MIND) < before || !client.manInView()) Unit else null }
                    if (client.carried(MIND) < before) casts++
                    // A cast at a time, as clicking without autocast does: the next once this one's delay is over.
                    client.awaitTicks(MagicCombat.SPEED)
                }
                client.await("the man's death") { if (client.manInView()) null else Unit }
                // Four tiles off is in range: she casts from where she stands.
                assertEquals(start, client.position)
                assertTrue(casts in 1..39, "$casts casts")
                assertEquals(listOf(100 - casts, 300 - 3 * casts, 200 - 2 * casts), listOf(client.carried(MIND), client.carried(FIRE), client.carried(AIR)))
                // 11.5 a cast and 2 a point of the man's 7 hitpoints.
                assertEquals((casts * 115 + 7 * 20) / 10, client.stat(MAGIC)!!.xp - magicBefore)
                assertEquals(1_154 + 9, client.stat(HITPOINTS)!!.xp)
            }
        }
    }

    @Test
    fun `too low a level and too few runes are refused, and a staff of fire stands in for the fire runes`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                client.await("the login rebuild") { client.position }
                client.give(MIND, 1)
                client.give(AIR, 2)
                client.npcTarget(npc.index, Spells.SPELLBOOK, FIRE_STRIKE)
                client.awaitMessage("the level refusal") { it == MagicCombat.LEVEL_TOO_LOW }
                client.commandReply("setlevel magic 13") { it.contains("Magic") }
                client.npcTarget(npc.index, Spells.SPELLBOOK, FIRE_STRIKE)
                client.awaitMessage("the rune refusal") { it == "You do not have enough Fire Runes to cast this spell." }
                assertEquals(1 to 2, client.carried(MIND) to client.carried(AIR))
                client.give(STAFF_OF_FIRE, 1)
                val slot = client.inventory(BACKPACK).indexOfFirst { it?.id == STAFF_OF_FIRE }
                client.ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[WIELD - 1], slot, STAFF_OF_FIRE)
                client.await("the staff worn") { client.inventory(WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == STAFF_OF_FIRE } }
                client.npcTarget(npc.index, Spells.SPELLBOOK, FIRE_STRIKE)
                client.await("the runes used") { if (client.carried(MIND) == 0 && client.carried(AIR) == 0) Unit else null }
            }
        }
    }

    @Test
    fun `Fire Strike picked for autocast on the combat tab kills a man from Attack alone, a cast every 5 ticks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                val start = client.await("the login rebuild") { client.position }
                client.commandReply("setlevel magic 13") { it.contains("Magic") }
                client.give(MIND, 100)
                client.give(AIR, 200)
                client.give(STAFF_OF_FIRE, 1)
                val slot = client.inventory(BACKPACK).indexOfFirst { it?.id == STAFF_OF_FIRE }
                client.ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[WIELD - 1], slot, STAFF_OF_FIRE)
                client.await("the staff worn") { client.inventory(WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == STAFF_OF_FIRE } }
                client.commandReply("god") { it.startsWith("You can") }

                client.ifButton(EquipmentTab.COMBAT, EquipmentTab.COMBAT_AUTOCAST_NORMAL, 1)
                client.await("the autocast menu") { if (EquipmentTab.AUTOCAST in client.openInterfaces()) Unit else null }
                // Slot 4 is Fire Strike's key in enum 1986.
                client.ifButton(EquipmentTab.AUTOCAST, EquipmentTab.AUTOCAST_SPELLS, 1, sub = 4)
                client.await("the combat tab back with Fire Strike picked") {
                    val open = client.openInterfaces()
                    if (client.varp(Autocast.VARP_LASTCASTSPELL) == (4 shl 1 or 1) && EquipmentTab.COMBAT in open && EquipmentTab.AUTOCAST !in open) Unit else null
                }
                assertEquals(Autocast.COM_MODE, client.varp(EquipmentTab.VARP_COM_MODE))

                val magicBefore = client.await("the level 13 XP") { client.stat(MAGIC)?.takeIf { it.level == 13 } }.xp
                client.npcOp(npc.index, ATTACK)
                // At most 40 casts of 5 ticks; the man has 7 hitpoints and Fire Strike hits up to 8.
                client.await("the man's death", timeoutMillis = 40L * MagicCombat.SPEED * 100 + 10_000) { if (client.manInView()) null else Unit }
                val casts = 100 - client.carried(MIND)
                assertTrue(casts in 1..40, "$casts casts")
                assertEquals(start, client.position)
                // The staff stands in for the fire runes.
                assertEquals(200 - 2 * casts, client.carried(AIR))
                assertEquals((casts * 115 + 7 * 20) / 10, client.stat(MAGIC)!!.xp - magicBefore)
            }
        }
    }

    companion object {
        const val FIRE_STRIKE = 19
        private const val ATTACK = 2
        const val AIR = 556
        const val FIRE = 554
        const val MIND = 558
        const val STAFF_OF_FIRE = 1387
        private const val HITPOINTS = 3
        private const val MAGIC = 6

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
