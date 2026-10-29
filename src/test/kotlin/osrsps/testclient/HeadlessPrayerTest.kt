package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameFrame
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Scenario
import osrsps.game.Scenarios
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.Prayers
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * The prayer book through the headless client, with the real prayers and NPC combat files: Protect from Melee clicked
 * on, draining a point every 5 ticks, a man fought with it on doing no damage, a second prayer alongside, the level
 * refusal, and every prayer going off at no points. The rl-client script test-runner/examples/prayer-protect.json makes
 * the same clicks in the real client.
 */
class HeadlessPrayerTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    // Shaped like the 241 cache's prayer objs: 630 the bit, 1751 the prayer book component, 1752 the name, 1753 the
    // level, 1755 the sound.
    private fun prayerObj(id: Int, internal: String, name: String, bit: Int, component: Int, level: Int, sound: Int) = ObjType(
        id, "Null", internalName = internal,
        params = mapOf(630 to bit, 1751 to (Prayers.PRAYERBOOK shl 16 or component), 1752 to name, 1753 to level, 1755 to sound),
    )

    private val prayerObjs = listOf(
        prayerObj(20803, "placeholder_xmas16_ball_inflated_red", "Thick Skin", 0, THICK_SKIN, 1, 2690),
        prayerObj(20817, "placeholder_xmas16_marionette_blue", "Protect from Melee", 14, PROTECT_FROM_MELEE, 43, 2676),
        prayerObj(20819, "placeholder_xmas16_marionette_green", "Redemption", 16, REDEMPTION, 49, 2680),
        prayerObj(20829, "placeholder_xmas16_blue_partyhat", "Piety", 26, PIETY, 70, 3825),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man)
        val objs = arrayOfNulls<ObjType>(20830)
        for (type in prayerObjs) objs[type.id] = type
        val objTypes = ObjTypes(objs)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3224, 3218, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), prayerList = Prayers.load(objTypes), cycleMillis = millis,
            scenarios = Scenarios(listOf(Scenario.parse("praying", """{ "position": { "x": 3222, "z": 3218 }, "skills": { "prayer": 43 } }"""))),
        )
    }

    private fun HeadlessClient.prayers() = varp(Prayers.VARP_PRAYERS)

    private fun HeadlessClient.points() = stat(PRAYER)?.level

    private fun HeadlessClient.click(component: Int) = ifButton(Prayers.PRAYERBOOK, component, 1)

    @Test
    fun `Protect from Melee drains a point every 5 ticks and blocks a man's punches, and no points turn it off`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel prayer 43") { it.contains("Prayer") }
                client.await("43 points") { client.points()?.takeIf { it == 43 } }

                client.click(PIETY)
                client.awaitMessage("the level refusal") { it == "You need a Prayer level of 70 to use Piety." }
                assertEquals(0, client.prayers())

                client.click(PROTECT_FROM_MELEE)
                // Counted by the ticks the varp and the stat were sent in, not when the waits see them, which a slow machine
                // makes late.
                val on = client.await("Protect from Melee on") { if (client.prayers() == 1 shl 14) client.varpTick(Prayers.VARP_PRAYERS) else null }
                val drained = client.await("the first point", timeoutMillis = 20_000) { if (client.points() == 42) client.statTick(PRAYER) else null }
                // A point goes once the counter passes 60 at 12 a tick: on the 6th tick of draining.
                assertEquals(6, drained - on, "ticks from on to the first point")
                client.click(THICK_SKIN)
                client.await("both on") { if (client.prayers() == (1 shl 14 or 1)) Unit else null }

                // He fights back once punched; for 20 ticks (five of his punches) she takes nothing.
                client.npcOp(npc.index, ATTACK)
                client.awaitTicks(20)
                assertEquals(10, client.stat(HITPOINTS)!!.level)

                client.commandReply("pray 0") { it.startsWith("Prayer points set") }
                // The drain later in the same tick finds no points, so the message can come with the reply.
                client.await("out of points") { if (client.messages().any { it.text == Prayers.OUT_OF_POINTS }) Unit else null }
                client.await("every prayer off") { if (client.prayers() == 0) Unit else null }
                client.click(PROTECT_FROM_MELEE)
                client.awaitTicks(2)
                assertEquals(0, client.prayers())
            }
        }
    }

    @Test
    fun `Piety raises the combat command's numbers from the tick after it is clicked on`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                client.await("the login rebuild") { client.position }
                for (skill in listOf("attack", "strength", "defence", "prayer")) client.commandReply("setlevel $skill 99") { it.contains("set to level 99") }
                client.commandReply("combat man") { it.contains("attack roll 7040, max hit 11") }
                client.click(PIETY)
                client.await("Piety on") { if (client.prayers() == 1 shl 26) Unit else null }
                client.awaitTicks(1)
                // 99 * 1.2 = 118 and 99 * 1.23 = 121: (118 + 3 + 8) * 64 and ((121 + 8) * 64 + 320) / 640.
                client.commandReply("combat man") { it.contains("attack roll 8256, max hit 13") }
                // The reply's lines come together, so the third may be in before the wait starts.
                client.await("Piety's defence") { if (client.messages().any { it.text.contains("vs your 8384") }) Unit else null }
                client.click(PIETY)
                client.await("Piety off") { if (client.prayers() == 0) Unit else null }
                client.awaitTicks(1)
                client.commandReply("combat man") { it.contains("attack roll 7040, max hit 11") }
            }
        }
    }

    @Test
    fun `a scenario and its reset turn every prayer off, the overhead icon with them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel prayer 43") { it.contains("Prayer") }
                client.click(PROTECT_FROM_MELEE)
                client.await("Protect from Melee on") { if (client.prayers() == 1 shl 14) Unit else null }
                client.commandReply("scenario praying") { it.startsWith("Started scenario") }
                client.await("every prayer off") { if (client.prayers() == 0) Unit else null }
                client.commandReply("prayers") { it == "Prayer 43/43, drain resistance 60: none" }
                client.click(PROTECT_FROM_MELEE)
                client.await("Protect from Melee on again") { if (client.prayers() == 1 shl 14) Unit else null }
                client.commandReply("reset") { it.startsWith("Back to how you were") }
                // The varp before the scenario had Protect from Melee on; the reset doesn't bring it back.
                client.await("off after the reset") { if (client.prayers() == 0) Unit else null }
                client.commandReply("prayers") { it.endsWith("drain resistance 60: none") }
            }
        }
    }

    @Test
    fun `Redemption heals a quarter of the Prayer level when a hit leaves under 10% hitpoints, and every prayer goes off`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                for (skill in listOf("hitpoints", "prayer")) client.commandReply("setlevel $skill 99") { it.contains("set to level 99") }
                client.click(REDEMPTION)
                client.await("Redemption on") { if (client.prayers() == 1 shl 16) Unit else null }
                client.awaitTicks(1)
                // 9 left is under 9.9: 99 / 4 = 24 back.
                client.commandReply("hit 90 me") { it == "Hit yourself for 90: 33/99 hp" }
                client.await("every prayer off") { if (client.prayers() == 0) Unit else null }
                client.await("no points") { client.points()?.takeIf { it == 0 } }
            }
        }
    }

    @Test
    fun `quick prayers picked in the orb's setup, turned on and off from the orb`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                for (skill in listOf("defence", "prayer")) client.commandReply("setlevel $skill 99") { it.contains("set to level 99") }
                client.ifButton(GameFrame.ORBS, GameFrame.ORBS_PRAYER_BUTTON, 1)
                client.awaitMessage("nothing picked") { it == Prayers.NO_QUICK_PRAYERS }

                // Setup: the quick prayer setup in the prayer book's place, its prayers clickable by bit.
                client.ifButton(GameFrame.ORBS, GameFrame.ORBS_PRAYER_BUTTON, 2)
                client.await("the setup open") { if (Prayers.QUICKPRAYER in client.openInterfaces()) Unit else null }
                assertFalse(Prayers.PRAYERBOOK in client.openInterfaces())
                assertTrue(client.opEnabled(Prayers.QUICKPRAYER, Prayers.QUICKPRAYER_BUTTONS, 26, 1))
                client.ifButton(Prayers.QUICKPRAYER, Prayers.QUICKPRAYER_BUTTONS, 1, sub = 26)
                client.ifButton(Prayers.QUICKPRAYER, Prayers.QUICKPRAYER_BUTTONS, 1, sub = 14)
                val picked = 1 shl 26 or (1 shl 14)
                client.await("Piety and Protect from Melee picked") { if (client.varp(Prayers.VARP_QUICK_PRAYERS) == picked) Unit else null }
                assertEquals(0, client.prayers())
                client.ifButton(Prayers.QUICKPRAYER, Prayers.QUICKPRAYER_CLOSE, 1)
                client.await("the prayer book back") { if (Prayers.PRAYERBOOK in client.openInterfaces()) Unit else null }
                assertFalse(Prayers.QUICKPRAYER in client.openInterfaces())

                client.ifButton(GameFrame.ORBS, GameFrame.ORBS_PRAYER_BUTTON, 1)
                client.await("both on") { if (client.prayers() == picked) Unit else null }
                assertEquals(1, client.varp(Prayers.VARP_QUICK_ACTIVE))
                client.ifButton(GameFrame.ORBS, GameFrame.ORBS_PRAYER_BUTTON, 1)
                client.await("every prayer off") { if (client.prayers() == 0) Unit else null }
                assertEquals(0, client.varp(Prayers.VARP_QUICK_ACTIVE))
            }
        }
    }

    companion object {
        // prayerbook:prayer1 is 541:9, so prayer N is 9 + N - 1.
        private const val THICK_SKIN = 9
        private const val PROTECT_FROM_MELEE = 23
        private const val PIETY = 35
        private const val REDEMPTION = 25
        private const val ATTACK = 2
        private const val HITPOINTS = 3
        private const val PRAYER = 5
    }
}
