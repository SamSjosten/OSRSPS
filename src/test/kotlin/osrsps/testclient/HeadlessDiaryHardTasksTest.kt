package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.zone.ZoneLoc
import osrsps.game.combat.Prayers
import osrsps.game.content.Church
import osrsps.game.skill.AnvilItems
import osrsps.game.skill.Mining
import osrsps.game.skill.RuneCrafting
import osrsps.game.skill.Smithing
import osrsps.map.CollisionMap

/**
 * Three Lumbridge & Draynor diary tasks done through their real actions, each next to a near miss that does nothing:
 * Pray-at with Smite on at the Emir's Arena altar (not at Lumbridge's, and not with Smite off), an adamant platebody
 * smithed in Draynor Sewers (not at an anvil outside them), and 140 water runes or more crafted at once (not fewer;
 * any water runes are the easy task).
 */
class HeadlessDiaryHardTasksTest {
    private val water = RuneCrafting.RECIPES.first { it.key == "water" }

    // Shaped like the cache's Smite obj (prayers.json names it xmas16_marionette_red): 630 the bit, 1751 the prayer
    // book component, 1752 the name, 1753 the level, 1755 the sound.
    private fun prayerObj(id: Int, name: String, bit: Int, component: Int, level: Int) = ObjType(
        id, "Null", internalName = "xmas16_marionette_red",
        params = mapOf(630 to bit, 1751 to (Prayers.PRAYERBOOK shl 16 or component), 1752 to name, 1753 to level, 1755 to 2690),
    )

    private val smite = prayerObj(SMITE_OBJ, "Smite", 23, SMITE_COMPONENT, 52)

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val ids = listOf(Mining.RUNE_ESSENCE, water.rune, Smithing.HAMMER, Smithing.ADAMANTITE.obj, SMITE_OBJ) +
            AnvilItems.BY_BAR.getValue(Smithing.ADAMANTITE.obj).map { it.obj }
        val objs = arrayOfNulls<ObjType>(ids.max() + 1)
        for (id in ids) objs[id] = ObjType(id, "Obj $id", stackable = id == water.rune)
        objs[Mining.RUNE_ESSENCE] = ObjType(Mining.RUNE_ESSENCE, "Rune essence")
        objs[SMITE_OBJ] = smite
        val objTypes = ObjTypes(objs)
        fun loc(id: Int, op: String, size: Int = 1) = LocType(id, "Loc $id", listOf(op, null, null, null, null), LocCollisionType(width = size, length = size))
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(SEWER_ANVIL_X shr 6, SEWER_ANVIL_Z shr 6)
                markLoaded(EMIR_ALTAR_X shr 6, EMIR_ALTAR_Z shr 6)
            },
            objTypes = objTypes, random = GameRandom(3), prayerList = Prayers.load(objTypes),
            locTypes = LocTypes(
                listOf(loc(Smithing.ANVIL, "Smith"), loc(Church.ALTAR, "Pray-at", 2), loc(water.altar, "Craft-rune", 3)).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Smithing.ANVIL, LocShape.CENTREPIECE_STRAIGHT, 0, LUMBRIDGE_ANVIL_X, LUMBRIDGE_ANVIL_Z, 0))
                add(ZoneLoc(Smithing.ANVIL, LocShape.CENTREPIECE_STRAIGHT, 0, SEWER_ANVIL_X, SEWER_ANVIL_Z, 0))
                add(ZoneLoc(Church.ALTAR, LocShape.CENTREPIECE_STRAIGHT, 0, LUMBRIDGE_ALTAR_X, LUMBRIDGE_ALTAR_Z, 0))
                add(ZoneLoc(Church.ALTAR, LocShape.CENTREPIECE_STRAIGHT, 0, EMIR_ALTAR_X, EMIR_ALTAR_Z, 0))
                add(ZoneLoc(water.altar, LocShape.CENTREPIECE_STRAIGHT, 0, WATER_ALTAR_X, WATER_ALTAR_Z, 0))
            },
            varTypes = VARS,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tasksDone() = messages().count { it.text.startsWith("[diary.task_done") }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.level(skill: String, id: Int, level: Int) {
        command("setlevel $skill $level")
        await("$skill $level") { stat(id)?.takeIf { it.level == level } }
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("at $x, $z") { position?.takeIf { it.x == x && it.z == z } }
    }

    @Test
    fun `Pray-at with Smite on does the hard task at the Emir's Arena altar only`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.level("prayer", PRAYER, 99)

                // Smite off, at the arena's altar: nothing.
                client.tele(EMIR_ALTAR_X - 1, EMIR_ALTAR_Z)
                client.locOp(Church.ALTAR, EMIR_ALTAR_X, EMIR_ALTAR_Z, 1)
                client.awaitTicks(3)
                assertEquals(0, client.tasksDone(), "Smite off")

                // Smite on, at Lumbridge's altar: nothing.
                client.ifButton(Prayers.PRAYERBOOK, SMITE_COMPONENT, 1)
                client.await("Smite on") { Unit.takeIf { client.varp(Prayers.VARP_PRAYERS) and (1 shl 23) != 0 } }
                client.tele(LUMBRIDGE_ALTAR_X - 1, LUMBRIDGE_ALTAR_Z)
                client.locOp(Church.ALTAR, LUMBRIDGE_ALTAR_X, LUMBRIDGE_ALTAR_Z, 1)
                client.awaitTicks(3)
                assertEquals(0, client.tasksDone(), "the wrong altar")

                client.tele(EMIR_ALTAR_X - 1, EMIR_ALTAR_Z)
                client.locOp(Church.ALTAR, EMIR_ALTAR_X, EMIR_ALTAR_Z, 1)
                client.await("the diary task") { Unit.takeIf { client.tasksDone() == 1 } }
            }
        }
    }

    @Test
    fun `an adamant platebody smithed in Draynor Sewers does the elite task, and one smithed outside doesn't`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.level("smithing", SMITHING, 99)
                client.give(Smithing.HAMMER)
                client.give(Smithing.ADAMANTITE.obj, 10)

                client.tele(LUMBRIDGE_ANVIL_X - 1, LUMBRIDGE_ANVIL_Z)
                client.smithPlatebody(LUMBRIDGE_ANVIL_X, LUMBRIDGE_ANVIL_Z, 1)
                assertEquals(0, client.tasksDone(), "outside the sewers")

                client.tele(SEWER_ANVIL_X - 1, SEWER_ANVIL_Z)
                client.smithPlatebody(SEWER_ANVIL_X, SEWER_ANVIL_Z, 2)
                client.await("the diary task") { Unit.takeIf { client.tasksDone() == 1 } }
            }
        }
    }

    private fun HeadlessClient.smithPlatebody(x: Int, z: Int, made: Int) {
        locOp(Smithing.ANVIL, x, z, 1)
        await("the anvil") { if (Smithing.INTERFACE in openInterfaces()) Unit else null }
        ifButton(Smithing.INTERFACE, PLATEBODY, 1)
        await("platebody $made", timeoutMillis = 20_000) { if (count(ADAMANT_PLATEBODY) == made) Unit else null }
    }

    @Test
    fun `140 water runes at once do the elite task, and fewer don't`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                // Level 99: six water runes an essence.
                client.level("runecraft", RUNECRAFT, 99)
                client.tele(WATER_ALTAR_X - 2, WATER_ALTAR_Z)

                client.give(Mining.RUNE_ESSENCE, 23)
                client.locOp(water.altar, WATER_ALTAR_X, WATER_ALTAR_Z, 1)
                client.await("138 runes") { if (client.count(water.rune) == 138) Unit else null }
                // Any water runes are the easy task; 138 aren't the elite one.
                client.await("the easy task") { Unit.takeIf { client.tasksDone() == 1 } }
                client.awaitTicks(2)
                assertEquals(1, client.tasksDone(), "138 isn't 140")

                client.give(Mining.RUNE_ESSENCE, 24)
                client.locOp(water.altar, WATER_ALTAR_X, WATER_ALTAR_Z, 1)
                client.await("144 more runes") { if (client.count(water.rune) == 138 + 144) Unit else null }
                client.await("the elite task") { Unit.takeIf { client.tasksDone() == 2 } }
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val PRAYER = 5
        const val SMITHING = 13
        const val RUNECRAFT = 20
        const val SMITE_OBJ = 20820
        const val SMITE_COMPONENT = 32
        const val PLATEBODY = 22
        const val ADAMANT_PLATEBODY = 1123

        const val LUMBRIDGE_ANVIL_X = 3220
        const val LUMBRIDGE_ANVIL_Z = 3218
        /** Inside the diary's Draynor Sewers box. */
        const val SEWER_ANVIL_X = 3100
        const val SEWER_ANVIL_Z = 9660
        const val LUMBRIDGE_ALTAR_X = 3225
        const val LUMBRIDGE_ALTAR_Z = 3218
        /** Quest Helper's Emir's Arena altar. */
        const val EMIR_ALTAR_X = 3377
        const val EMIR_ALTAR_Z = 3285
        const val WATER_ALTAR_X = 3224
        const val WATER_ALTAR_Z = 3222

        val VARS = VarTypes(
            6_000,
            mapOf(
                Smithing.VARBIT_BAR_TYPE to VarbitType(210, 0, 2),
                Smithing.VARBIT_LAST_BAR to VarbitType(549, 24, 26),
                Smithing.VARBIT_LAST_ITEM to VarbitType(549, 27, 31),
            ),
        )
    }
}
