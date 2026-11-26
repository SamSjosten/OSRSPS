package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.ObjType
import osrsps.game.World
import osrsps.game.combat.Spells
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.magic.ChargeOrbs
import osrsps.game.skill.magic.TeleportObjs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Charge Air Orb (Magic utility step U8a) through the headless client, with the spell obj shaped as the 242 cache has
 * it: cast on the Obelisk of Air from 4 tiles off it walks there, opens the Make-X box and charges the number picked,
 * 6 ticks apart, for 76 XP each; cast on the Obelisk of Water, or with no unpowered orb, it charges nothing.
 */
class HeadlessChargeOrbsTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val params = HashMap<Int, Any>()
        params[Spells.PARAM_NAME] = "Charge Air Orb"
        params[Spells.PARAM_LEVEL] = 66
        params[Spells.PARAM_COMPONENT] = (Spells.SPELLBOOK shl 16) or COMPONENT
        params[Spells.PARAM_RUNES[0].first] = COSMIC
        params[Spells.PARAM_RUNES[0].second] = 3
        params[Spells.PARAM_RUNES[1].first] = TeleportObjs.AIR
        params[Spells.PARAM_RUNES[1].second] = 30
        params[Spells.PARAM_RUNES[2].first] = ChargeOrbs.UNPOWERED_ORB
        params[Spells.PARAM_RUNES[2].second] = 1
        val objTypes = TeleportObjs.objTypes(
            ObjType(SPELL, "null", params = params, internalName = "66_charge_air_orb"),
            ObjType(COSMIC, "Cosmic rune", stackable = true, internalName = "cosmicrune"),
            ObjType(ChargeOrbs.UNPOWERED_ORB, "Unpowered orb", internalName = "stafforb"),
            ObjType(AIR_ORB, "Air orb", internalName = "air_orb"),
        )
        val obelisk = { id: Int -> LocType(id, "Obelisk", collision = LocCollisionType(width = 1, length = 1)) }
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = osrsps.game.GameRandom(7), objTypes = objTypes,
            locTypes = LocTypes(mapOf(ChargeOrbs.OBELISK_AIR to obelisk(ChargeOrbs.OBELISK_AIR), ChargeOrbs.OBELISK_WATER to obelisk(ChargeOrbs.OBELISK_WATER))),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(ChargeOrbs.OBELISK_AIR, LocShape.CENTREPIECE_STRAIGHT, 0, AIR_X, Z, 0))
                add(ZoneLoc(ChargeOrbs.OBELISK_WATER, LocShape.CENTREPIECE_STRAIGHT, 0, WATER_X, Z, 0))
            },
            spells = Spells.load(objTypes, osrsps.game.SeqNames.NONE, osrsps.game.SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int): Int = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of item $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.ready() {
        await("the login rebuild") { position }
        commandReply("keys on") { it == "Dialogue shows keys" }
        commandReply("setlevel magic 66") { it.contains("set to level 66") }
        await("the level") { stat(MAGIC)?.takeIf { it.level == 66 } }
    }

    private fun HeadlessClient.saw(description: String, text: String) =
        await(description) { if (messages().any { it.text == text }) Unit else null }

    private fun HeadlessClient.box() = await("the Make-X box") { clientScripts().lastOrNull { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } }

    @Test
    fun `cast on the Obelisk of Air it walks there and charges the orbs picked, 6 ticks apart, for 76 XP each`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.give(ChargeOrbs.UNPOWERED_ORB, 3)
                client.give(COSMIC, 9)
                client.give(TeleportObjs.AIR, 90)
                val xp = client.stat(MAGIC)?.xp ?: 0
                client.locTarget(ChargeOrbs.OBELISK_AIR, AIR_X, Z, Spells.SPELLBOOK, COMPONENT)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                client.await("two orbs") { if (client.count(AIR_ORB) == 2) Unit else null }
                assertEquals(1, client.count(ChargeOrbs.UNPOWERED_ORB))
                assertEquals(3, client.count(COSMIC))
                assertEquals(30, client.count(TeleportObjs.AIR))
                client.await("152 XP") { if (client.stat(MAGIC)?.xp == xp + 152) Unit else null }
                val casts = client.animationTicks().takeLast(2)
                assertEquals(ChargeOrbs.INTERVAL, casts[1] - casts[0])
                assertEquals(AIR_X - 1, client.position?.x, "beside the obelisk")
            }
        }
    }

    @Test
    fun `the wrong obelisk or no orb charges nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.give(COSMIC, 3)
                client.give(TeleportObjs.AIR, 30)
                client.locTarget(ChargeOrbs.OBELISK_AIR, AIR_X, Z, Spells.SPELLBOOK, COMPONENT)
                client.saw("no orb", "[charge_orb.no_orb]")
                client.give(ChargeOrbs.UNPOWERED_ORB, 1)
                client.locTarget(ChargeOrbs.OBELISK_WATER, WATER_X, Z, Spells.SPELLBOOK, COMPONENT)
                client.saw("the wrong obelisk", "[charge_orb.wrong_obelisk]")
                assertEquals(1, client.count(ChargeOrbs.UNPOWERED_ORB))
                assertEquals(3, client.count(COSMIC))
            }
        }
    }

    companion object {
        const val BACKPACK = 93
        const val MAGIC = 6
        const val SPELL = 3316
        const val COSMIC = 564
        const val AIR_ORB = 573

        /** magic_spellbook:charge_air_orb, 218:62 in the 242 cache. */
        const val COMPONENT = 62

        const val AIR_X = 3226
        const val WATER_X = 3218
        const val Z = 3218
    }
}
