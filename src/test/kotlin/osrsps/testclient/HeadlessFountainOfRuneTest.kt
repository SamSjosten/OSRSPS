package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.EquipmentTab
import osrsps.game.FountainOfRune
import osrsps.game.FountainOfRuneLines
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.SpotanimNames
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.Spells
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Wilderness step W3.4d through the headless client: the Fountain of Rune's area; Fire Strike cast with no runes at
 * all beside it, killing a man with no Magic or Hitpoints XP; and jewellery used on it recharging every glory, ring of
 * wealth and noted skills necklace carried, with a noted glory left alone. The fountain stands on open ground at its
 * 242 tile. The rl-client script test-runner/examples/fountain-of-rune.json does the same on the real map.
 */
class HeadlessFountainOfRuneTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    // As HeadlessMagicTest's: Fire Strike's obj as the cache has it.
    private val fireStrike = ObjType(
        3279, "Fire Strike", internalName = "13_fire_strike",
        params = mapOf(601 to "Fire Strike", 604 to 13, 596 to (218 shl 16 or FIRE_STRIKE), 365 to MIND, 366 to 1, 367 to FIRE, 368 to 3, 369 to AIR, 370 to 2, 1935 to 8),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man)
        val objs = arrayOfNulls<ObjType>(FountainOfRune.ETERNAL_GLORY + 1)
        for ((id, name) in listOf(AIR to "Air rune", FIRE to "Fire rune", MIND to "Mind rune")) objs[id] = ObjType(id, name, stackable = true)
        objs[fireStrike.id] = fireStrike
        for (id in listOf(GLORY_1, GLORY_6, RING, RING_5, FountainOfRune.ETERNAL_GLORY)) objs[id] = ObjType(id, "Jewellery $id", certlink = id + 1)
        objs[GLORY_1 + 1] = ObjType(GLORY_1 + 1, "Noted glory", stackable = true, certlink = GLORY_1, certtemplate = 799)
        objs[SKILLS] = ObjType(SKILLS, "Skills necklace", certlink = SKILLS + 1)
        objs[SKILLS + 1] = ObjType(SKILLS + 1, "Skills necklace", stackable = true, certlink = SKILLS, certtemplate = 799)
        objs[SKILLS_6] = ObjType(SKILLS_6, "Skills necklace(6)", certlink = SKILLS_6 + 1)
        objs[SKILLS_6 + 1] = ObjType(SKILLS_6 + 1, "Skills necklace(6)", stackable = true, certlink = SKILLS_6, certtemplate = 799)
        objs[LOGS] = ObjType(LOGS, "Logs")
        val objTypes = ObjTypes(objs)
        val fountain = LocType(FountainOfRune.FOUNTAIN, "Fountain of Rune", collision = LocCollisionType(width = 3, length = 3))
        World(
            crcs, CollisionMap().apply { markLoaded(FOUNTAIN_X shr 6, FOUNTAIN_Z shr 6) }, random = GameRandom(5), objTypes = objTypes,
            locTypes = LocTypes(mapOf(FountainOfRune.FOUNTAIN to fountain)),
            staticLocs = StaticLocs().apply { add(ZoneLoc(FountainOfRune.FOUNTAIN, LocShape.CENTREPIECE_STRAIGHT, 0, FOUNTAIN_X, FOUNTAIN_Z, 0)) },
            npcTypes = types, npcSpawns = listOf(NpcSpawn(man, 3378, 3897, source = "test")),
            varTypes = VarTypes(6_000, mapOf(VARBIT_WEAPON_CATEGORY to VarbitType(VARP_WEAPON_CATEGORY, 0, 5))),
            npcCombat = NpcCombat.load(types, SeqNames.NONE),
            spells = Spells.load(objTypes, SeqNames.NONE, SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.count(id: Int): Long = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    @Test
    fun `the fountain covers the tiles in sight of it, on the surface`() {
        assertTrue(FountainOfRune.covers(3373, 3896, 0))
        assertTrue(FountainOfRune.covers(3385, 3890, 0))
        assertTrue(FountainOfRune.covers(3375, 3897, 0))
        assertFalse(FountainOfRune.covers(3373, 3896, 1))
        assertFalse(FountainOfRune.covers(3360, 3895, 0))
        assertFalse(FountainOfRune.covers(3370, 3888, 0), "south of the wall's line")
        assertFalse(FountainOfRune.covers(3390, 3890, 0))
        assertEquals(47, Wilderness.level(FOUNTAIN_X, FOUNTAIN_Z + 3))
    }

    @Test
    fun `beside the fountain Fire Strike needs no runes and gives no XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("xan").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3375, 3897)
                client.commandReply("setlevel magic 13") { it.contains("Magic") }
                client.commandReply("god") { it.startsWith("You can") }
                client.ifButton(EquipmentTab.COMBAT, EquipmentTab.COMBAT_RETALIATE, 1)
                client.await("auto retaliate off") { if (client.varp(EquipmentTab.VARP_AUTO_RETALIATE_OFF) == 1) Unit else null }
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                val magicBefore = client.await("the level 13 XP") { client.stat(MAGIC)?.takeIf { it.level == 13 } }.xp
                val hitpointsBefore = client.stat(HITPOINTS)!!.xp
                var clicks = 0
                while (client.npcs().any { it.id == man.id } && clicks < 40) {
                    client.npcTarget(npc.index, Spells.SPELLBOOK, FIRE_STRIKE)
                    clicks++
                    client.awaitTicks(6)
                }
                assertFalse(client.npcs().any { it.id == man.id }, "the man killed with free casts")
                assertFalse(client.messages().any { it.text.startsWith("[combat.not_enough_runes") })
                assertEquals(magicBefore, client.stat(MAGIC)!!.xp)
                assertEquals(hitpointsBefore, client.stat(HITPOINTS)!!.xp)
            }
        }
    }

    @Test
    fun `jewellery used on the fountain recharges everything it can`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("yew").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                for (item in listOf("$GLORY_1", "$RING", "${SKILLS + 1} 3", "${GLORY_1 + 1} 2", "$LOGS")) client.command("item $item")
                client.await("the jewellery") { Unit.takeIf { client.count(LOGS) == 1L } }
                client.tele(FOUNTAIN_X + 1, FOUNTAIN_Z - 1)

                val logs = client.inventory(BACKPACK).indexOfFirst { it?.id == LOGS }
                client.locTarget(FountainOfRune.FOUNTAIN, FOUNTAIN_X, FOUNTAIN_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, logs, LOGS)
                client.awaitMessage("nothing interesting") { it == "[game.nothing_interesting]" }

                val ring = client.inventory(BACKPACK).indexOfFirst { it?.id == RING }
                client.locTarget(FountainOfRune.FOUNTAIN, FOUNTAIN_X, FOUNTAIN_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, ring, RING)
                client.awaitMessage("the recharge") { it == "[${FountainOfRuneLines.RECHARGE.key}]" }
                assertEquals(1L, client.count(GLORY_6), "the glory to six")
                assertEquals(1L, client.count(RING_5), "the ring to five")
                assertEquals(3L, client.count(SKILLS_6 + 1), "noted skills necklaces too")
                assertEquals(2L, client.count(GLORY_1 + 1), "noted glories left alone")
            }
        }
    }

    private companion object {
        const val FOUNTAIN_X = 3373
        const val FOUNTAIN_Z = 3892
        const val GLORY_1 = 1706
        const val GLORY_6 = 11978
        const val RING = 2572
        const val RING_5 = 11980
        const val SKILLS = 11113
        const val SKILLS_6 = 11968
        const val LOGS = 1511
        const val FIRE_STRIKE = 19
        const val AIR = 556
        const val FIRE = 554
        const val MIND = 558
        const val HITPOINTS = 3
        const val MAGIC = 6
        const val BACKPACK = 93
        const val VARBIT_WEAPON_CATEGORY = 357
        const val VARP_WEAPON_CATEGORY = 843
    }
}
