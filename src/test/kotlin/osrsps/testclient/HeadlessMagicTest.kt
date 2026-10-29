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
 * the combat tab, which then fights the man to his death from Attack alone; Bind holding the man in place; and Ice
 * Barrage from the Ancient book freezing two men in a multi-way zone. The rl-client scripts
 * test-runner/examples/combat-magic-strike.json, combat-magic-autocast.json, combat-magic-bind.json and
 * combat-magic-ice.json make the same clicks in the real client.
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
                client.awaitMessage("the level refusal") { it == "[combat.magic_level]" }
                client.commandReply("setlevel magic 13") { it.contains("Magic") }
                client.npcTarget(npc.index, Spells.SPELLBOOK, FIRE_STRIKE)
                client.awaitMessage("the rune refusal") { it == "[combat.not_enough_runes runes=Fire Runes]" }
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
                client.commandReply("buttons on") { it == "Interface clicks are echoed" }

                client.ifButton(EquipmentTab.COMBAT, EquipmentTab.COMBAT_AUTOCAST_NORMAL, 1)
                client.await("the autocast menu") { if (EquipmentTab.AUTOCAST in client.openInterfaces()) Unit else null }
                // The menu's spells are made by its script, so the real client only sends a click on one the server enabled.
                client.await("op 1 on the menu's spells") { if (client.opEnabled(EquipmentTab.AUTOCAST, EquipmentTab.AUTOCAST_SPELLS, 4, 1)) Unit else null }
                // Slot 4 is Fire Strike's key in enum 1986.
                client.ifButton(EquipmentTab.AUTOCAST, EquipmentTab.AUTOCAST_SPELLS, 1, sub = 4)
                client.await("the combat tab back with Fire Strike picked") {
                    val open = client.openInterfaces()
                    if (client.varp(Autocast.VARP_LASTCASTSPELL) == (4 shl 1 or 1) && EquipmentTab.COMBAT in open && EquipmentTab.AUTOCAST !in open) Unit else null
                }
                assertEquals(Autocast.COM_MODE, client.varp(EquipmentTab.VARP_COM_MODE))
                // ::buttons echoed both clicks as the server got them.
                val echoed = client.messages().map { it.text }.filter { it.startsWith("Button ") }
                assertEquals(listOf("Button 593:${EquipmentTab.COMBAT_AUTOCAST_NORMAL} slot -1 op 1", "Button 201:1 slot 4 op 1"), echoed)

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

    @Test
    fun `the imp's water weakness gives Water Bolt a tenth more accuracy and max hit, and nothing to Fire Strike`(@TempDir dir: Path) {
        // Water Bolt as the 241 cache has it: level 23, a chaos rune, two water and two air, max hit 10, key 6.
        val waterBolt = ObjType(
            3285, "Water Bolt", internalName = "23_water_bolt",
            params = mapOf(601 to "Water Bolt", 604 to 23, 596 to (218 shl 16 or WATER_BOLT), 365 to CHAOS, 366 to 1, 367 to WATER, 368 to 2, 369 to AIR, 370 to 2, 1935 to 10),
        )
        val imp = NpcType(5007, "Imp", ops = listOf(null, "Attack", null, null, null), combatLevel = 2, internalName = "imp", stats = NpcStats(hitpoints = 8))
        val extraRunes = listOf(Triple(WATER, "Water rune", "waterrune"), Triple(CHAOS, "Chaos rune", "chaosrune")).map { (id, name, internal) ->
            ObjType(id, name, stackable = true, iops = listOf(null, null, null, null, "Drop"), internalName = internal)
        }
        val server = TestServer(dir) { crcs, millis ->
            val types = NpcTypes.of(man, imp)
            val objs = arrayOfNulls<ObjType>(waterBolt.id + 1)
            for (type in runes + extraRunes + listOf(fireStrike, waterBolt, staff)) objs[type.id] = type
            val objTypes = ObjTypes(objs)
            World(
                crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
                npcSpawns = listOf(NpcSpawn(man, 3226, 3218, source = "test"), NpcSpawn(imp, 3222, 3222, wander = 0, source = "test")),
                varTypes = VarTypes(6_000, mapOf(VARBIT_WEAPON_CATEGORY to VarbitType(VARP_WEAPON_CATEGORY, 0, 5))),
                npcCombat = NpcCombat.load(types, SeqNames.NONE),
                spells = Spells.load(objTypes, SeqNames.NONE, SpotanimNames.NONE), cycleMillis = millis,
            )
        }
        server.use {
            server.login("dave").use { client ->
                client.await("the imp in view") { client.npcs().firstOrNull { it.id == imp.id } }
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel magic 23") { it.contains("Magic") }
                client.give(STAFF_OF_FIRE, 1)
                val slot = client.inventory(BACKPACK).indexOfFirst { it?.id == STAFF_OF_FIRE }
                client.ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[WIELD - 1], slot, STAFF_OF_FIRE)
                client.await("the staff worn") { client.inventory(WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == STAFF_OF_FIRE } }
                fun pick(key: Int) {
                    client.ifButton(EquipmentTab.COMBAT, EquipmentTab.COMBAT_AUTOCAST_NORMAL, 1)
                    client.await("the autocast menu") { if (EquipmentTab.AUTOCAST in client.openInterfaces()) Unit else null }
                    client.ifButton(EquipmentTab.AUTOCAST, EquipmentTab.AUTOCAST_SPELLS, 1, sub = key)
                    client.await("spell $key picked") { if (client.varp(Autocast.VARP_LASTCASTSPELL) == (key shl 1 or 1)) Unit else null }
                }
                fun combat(name: String) = client.commandReply("combat $name") { it.startsWith("Spell (") }
                pick(6)
                val onMan = combat("man")
                val onImp = combat("imp")
                assertTrue(onMan.startsWith("Spell (Water Bolt), speed 5: attack roll ${(23 + 8) * (10 + 64)}, max hit 10"), onMan)
                // 10 + 10 x 10%, and the attack roll times 1.1.
                assertTrue(onImp.startsWith("Spell (Water Bolt), speed 5: attack roll ${(23 + 8) * (10 + 64) * 110 / 100}, max hit 11"), onImp)
                // Fire Strike gets nothing from a water weakness.
                pick(4)
                assertTrue(combat("imp").endsWith("max hit 8"), "Fire Strike on the imp")
            }
        }
    }

    @Test
    fun `Bind holds the man where he stands for 8 ticks, and a second Bind meanwhile is refused`(@TempDir dir: Path) {
        // Bind as the 241 cache has it: level 20, two nature, three water and three earth runes, and no max hit param.
        val bind = ObjType(
            3283, "Bind", internalName = "20_bind",
            params = mapOf(601 to "Bind", 604 to 20, 596 to (218 shl 16 or BIND), 365 to NATURE, 366 to 2, 367 to WATER, 368 to 3, 369 to EARTH, 370 to 3),
        )
        val bindRunes = listOf(Triple(WATER, "Water rune", "waterrune"), Triple(EARTH, "Earth rune", "earthrune"), Triple(NATURE, "Nature rune", "naturerune")).map { (id, name, internal) ->
            ObjType(id, name, stackable = true, iops = listOf(null, null, null, null, "Drop"), internalName = internal)
        }
        val server = TestServer(dir) { crcs, millis ->
            val types = NpcTypes.of(man)
            val objs = arrayOfNulls<ObjType>(bind.id + 1)
            for (type in runes + bindRunes + listOf(fireStrike, bind, staff)) objs[type.id] = type
            val objTypes = ObjTypes(objs)
            World(
                crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
                npcSpawns = listOf(NpcSpawn(man, 3226, 3218, source = "test")),
                varTypes = VarTypes(6_000, mapOf(VARBIT_WEAPON_CATEGORY to VarbitType(VARP_WEAPON_CATEGORY, 0, 5))),
                npcCombat = NpcCombat.load(types, SeqNames.NONE),
                spells = Spells.load(objTypes, SeqNames.NONE, SpotanimNames.NONE), cycleMillis = millis,
            )
        }
        server.use {
            server.login("dave").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel magic 20") { it.contains("Magic") }
                client.give(NATURE, 20)
                client.give(WATER, 30)
                client.give(EARTH, 30)
                client.commandReply("god") { it.startsWith("You can") }
                // A splash holds nothing, so cast until one hits; the man's magic defence lets most through.
                var held: String? = null
                var casts = 0
                while (held == null && casts < 10) {
                    client.npcTarget(npc.index, Spells.SPELLBOOK, BIND)
                    casts++
                    client.awaitTicks(MagicCombat.SPEED + 1)
                    held = client.commandReply("npcs 10") { it.startsWith("#${npc.index} ") }.takeIf { "; frozen " in it }
                }
                assertTrue(held != null, "no Bind hit in $casts casts")
                val at = client.npcs().first { it.index == npc.index }.coord
                client.npcTarget(npc.index, Spells.SPELLBOOK, BIND)
                client.awaitMessage("the refusal") { it == "[combat.already_held]" }
                client.awaitTicks(2)
                // He fights back, but stands where the spell caught him.
                assertEquals(at, client.npcs().first { it.index == npc.index }.coord)
                assertEquals(20 - 2 * casts, client.carried(NATURE))
            }
        }
    }

    @Test
    fun `Ice Barrage from the Ancient book freezes both men standing together in a multi-way zone`(@TempDir dir: Path) {
        // Ice Barrage as the 241 cache has it: Ancient Magicks (param 336), level 94, six water, two blood and four death
        // runes, and no max hit param.
        val barrage = ObjType(
            4651, "Ice Barrage", internalName = "94_ice_barrage",
            params = mapOf(601 to "Ice Barrage", 604 to 94, 596 to (218 shl 16 or ICE_BARRAGE), 336 to 1, 365 to WATER, 366 to 6, 367 to BLOOD, 368 to 2, 369 to DEATH, 370 to 4),
        )
        val barrageRunes = listOf(Triple(WATER, "Water rune", "waterrune"), Triple(BLOOD, "Blood rune", "bloodrune"), Triple(DEATH, "Death rune", "deathrune")).map { (id, name, internal) ->
            ObjType(id, name, stackable = true, iops = listOf(null, null, null, null, "Drop"), internalName = internal)
        }
        // Men with the hitpoints to take a barrage each, which would kill a 7-hitpoint man before his freeze shows.
        val bigMan = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 1_000))
        val server = TestServer(dir) { crcs, millis ->
            val types = NpcTypes.of(bigMan)
            val objs = arrayOfNulls<ObjType>(barrage.id + 1)
            for (type in runes + barrageRunes + listOf(fireStrike, barrage, staff)) objs[type.id] = type
            val objTypes = ObjTypes(objs)
            World(
                crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
                // Both in the player's 8x8 zone, which ::multi makes multi-way, and beside each other.
                npcSpawns = listOf(NpcSpawn(bigMan, 3219, 3218, wander = 0, source = "test"), NpcSpawn(bigMan, 3219, 3219, wander = 0, source = "test")),
                varTypes = VarTypes(6_000, mapOf(VARBIT_WEAPON_CATEGORY to VarbitType(VARP_WEAPON_CATEGORY, 0, 5))),
                npcCombat = NpcCombat.load(types, SeqNames.NONE),
                spells = Spells.load(objTypes, SeqNames.NONE, SpotanimNames.NONE), cycleMillis = millis,
            )
        }
        server.use {
            server.login("dave").use { client ->
                val men = client.await("both men in view") { client.npcs().filter { it.id == bigMan.id }.takeIf { it.size == 2 } }
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel magic 94") { it.contains("Magic") }
                client.give(WATER, 60)
                client.give(BLOOD, 20)
                client.give(DEATH, 40)
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("sure") { it.startsWith("Your attacks") }
                client.commandReply("multi on") { it.startsWith("Combat is multi-way") }
                // On the standard book the spell isn't there to cast: nothing happens.
                client.npcTarget(men[0].index, Spells.SPELLBOOK, ICE_BARRAGE)
                client.awaitTicks(MagicCombat.SPEED + 1)
                assertEquals(40, client.carried(DEATH))
                client.commandReply("spellbook ancient") { it == "Your spellbook is ancient now." }
                client.npcTarget(men[0].index, Spells.SPELLBOOK, ICE_BARRAGE)
                client.awaitTicks(MagicCombat.SPEED + 1)
                assertEquals(36, client.carried(DEATH))
                for (npc in men) {
                    val line = client.commandReply("npcs 10") { it.startsWith("#${npc.index} ") }
                    assertTrue("; frozen " in line, line)
                }
            }
        }
    }

    companion object {
        const val FIRE_STRIKE = 19
        const val BIND = 23
        const val ICE_BARRAGE = 87
        const val BLOOD = 565
        const val DEATH = 560
        const val EARTH = 557
        const val NATURE = 561
        const val WATER_BOLT = 25
        const val WATER = 555
        const val CHAOS = 562
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
