package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.ObjType
import osrsps.game.World
import osrsps.game.combat.Spells
import osrsps.game.skill.magic.TeleportObjs
import osrsps.map.CollisionMap

/**
 * Telekinetic Grab (Magic utility step U7b) through the headless client, with the spell obj shaped as the 242 cache has
 * it: cast on coins 8 tiles off it takes the whole stack where the player stands, for 43 XP, a law and an air rune;
 * from 15 tiles off the player walks into range first; a full backpack or too low a level casts nothing.
 * test-runner/examples/magic-telekinetic-grab.json does the same in RuneLite.
 */
class HeadlessTelekineticGrabTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val params = HashMap<Int, Any>()
        params[Spells.PARAM_NAME] = "Telekinetic Grab"
        params[Spells.PARAM_LEVEL] = 33
        params[Spells.PARAM_COMPONENT] = (Spells.SPELLBOOK shl 16) or COMPONENT
        params[Spells.PARAM_RUNES[0].first] = TeleportObjs.LAW
        params[Spells.PARAM_RUNES[0].second] = 1
        params[Spells.PARAM_RUNES[1].first] = TeleportObjs.AIR
        params[Spells.PARAM_RUNES[1].second] = 1
        val objTypes = TeleportObjs.objTypes(
            ObjType(SPELL, "null", params = params, internalName = "33_tele_grab"),
            ObjType(COINS, "Coins", stackable = true, internalName = "coins"),
            ObjType(LOGS, "Logs", internalName = "logs"),
            ObjType(NO_TAKE, "Statue piece", ops = listOf(null, null, null, null, null)),
        )
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = osrsps.game.GameRandom(7), objTypes = objTypes,
            spells = Spells.load(objTypes, osrsps.game.SeqNames.NONE, osrsps.game.SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int): Int = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of item $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.ready(magic: Int) {
        await("the login rebuild") { position }
        commandReply("keys on") { it == "Dialogue shows keys" }
        commandReply("setlevel magic $magic") { it.contains("set to level $magic") }
        await("the level") { stat(MAGIC)?.takeIf { it.level == magic } }
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        commandReply("tele $x $z") { it.startsWith("Teleported") }
        await("at $x, $z") { position?.takeIf { it.x == x && it.z == z } }
    }

    /** Puts [count] of [id] on the ground at [x], [z], then goes back to [fromX], [fromZ]. */
    private fun HeadlessClient.drop(id: Int, count: Int, x: Int, z: Int, fromX: Int, fromZ: Int) {
        tele(x, z)
        commandReply("groundobj $id $count") { it.startsWith("Put ") }
        await("the obj on the ground") { groundObjs().takeIf { objs -> objs.any { it.id == id } } }
        tele(fromX, fromZ)
    }

    private fun HeadlessClient.grab(id: Int, x: Int, z: Int) = objTarget(id, x, z, Spells.SPELLBOOK, COMPONENT)

    private fun HeadlessClient.saw(description: String, text: String) =
        await(description) { if (messages().any { it.text == text }) Unit else null }

    @Test
    fun `a stack of coins 8 tiles off comes to the player for 43 XP and a law and an air rune`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready(33)
                client.give(TeleportObjs.LAW, 2)
                client.give(TeleportObjs.AIR, 2)
                client.drop(COINS, 500, 3230, 3218, 3222, 3218)
                val xp = client.stat(MAGIC)?.xp ?: 0
                client.grab(COINS, 3230, 3218)
                client.await("the coins") { if (client.count(COINS) == 500) Unit else null }
                assertEquals(3222, client.position?.x, "cast from where they stood")
                assertEquals(1, client.count(TeleportObjs.LAW))
                assertEquals(1, client.count(TeleportObjs.AIR))
                client.await("43 XP") { if (client.stat(MAGIC)?.xp == xp + 43) Unit else null }
                client.await("nothing left on the ground") { if (client.groundObjs().none { it.id == COINS }) Unit else null }
            }
        }
    }

    @Test
    fun `from 15 tiles off the player walks into range first`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready(33)
                client.give(TeleportObjs.LAW)
                client.give(TeleportObjs.AIR)
                client.drop(LOGS, 1, 3237, 3218, 3222, 3218)
                client.grab(LOGS, 3237, 3218)
                client.await("the logs") { if (client.count(LOGS) == 1) Unit else null }
                val x = client.position!!.x
                assertTrue(x in 3227..3229, "stopped 10 tiles off, at $x")
            }
        }
    }

    @Test
    fun `too low a level or a full backpack casts nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready(32)
                client.give(TeleportObjs.LAW)
                client.give(TeleportObjs.AIR)
                client.drop(LOGS, 1, 3226, 3218, 3222, 3218)
                client.grab(LOGS, 3226, 3218)
                client.saw("the level", "[combat.magic_level]")
                client.commandReply("setlevel magic 33") { it.contains("set to level 33") }
                client.await("level 33") { client.stat(MAGIC)?.takeIf { it.level == 33 } }
                client.give(COINS, 1)
                client.command("item $LOGS 25")
                client.await("a full backpack") { if (client.inventory(BACKPACK).count { it != null } == 28) Unit else null }
                client.grab(LOGS, 3226, 3218)
                client.saw("no space", "[game.no_space]")
                assertEquals(1, client.count(TeleportObjs.LAW), "nothing spent")
            }
        }
    }

    @Test
    fun `an item under the player is grabbed, and a second cast inside the 3 ticks waits for them (Codex review on #982)`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready(33)
                client.give(TeleportObjs.LAW, 2)
                client.give(TeleportObjs.AIR, 2)
                client.drop(LOGS, 1, 3226, 3218, 3222, 3218)
                client.drop(COINS, 50, 3222, 3218, 3222, 3218)
                client.grab(COINS, 3222, 3218)
                client.await("the coins underfoot") { if (client.count(COINS) == 50) Unit else null }
                client.grab(LOGS, 3226, 3218)
                client.await("the logs, cast once the 3 ticks were up") { if (client.count(LOGS) == 1) Unit else null }
                assertEquals(0, client.count(TeleportObjs.LAW), "two casts")
                assertTrue(client.messages().none { it.text == "[game.cant_reach]" }, "no I can't reach that")
            }
        }
    }

    @Test
    fun `an item with no Take casts nothing (exploit review R61A-1)`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.ready(33)
                client.give(TeleportObjs.LAW)
                client.give(TeleportObjs.AIR)
                client.drop(NO_TAKE, 1, 3226, 3218, 3222, 3218)
                client.grab(NO_TAKE, 3226, 3218)
                client.saw("refused", "[game.nothing_interesting]")
                assertEquals(1, client.count(TeleportObjs.LAW), "nothing spent")
                assertEquals(0, client.count(NO_TAKE))
            }
        }
    }

    companion object {
        const val BACKPACK = 93
        const val MAGIC = 6
        const val SPELL = 3290
        const val COINS = 995
        const val LOGS = 1511
        /** Made up: an obj whose ops have no Take. */
        const val NO_TAKE = 30000

        /** magic_spellbook:telegrab, 218:30 in the 242 cache. */
        const val COMPONENT = 30
    }
}
