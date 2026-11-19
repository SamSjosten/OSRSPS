package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.skill.Mining
import osrsps.game.skill.ShootingStars
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Shooting stars through the headless client: `::star land here` puts a star two tiles north of the player in
 * Lumbridge, then Prospect, the discovery bonus, the level and pickaxe checks, stardust and its XP, and the layers. The
 * rl-client script test-runner/examples/shooting-stars.json does the same at a Misthalin site in the real client.
 */
class HeadlessShootingStarsTest {
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(ShootingStars.STARDUST + 1)
        objs[RUNE_PICKAXE] = ObjType(RUNE_PICKAXE, "Rune pickaxe")
        objs[ShootingStars.STARDUST] = ObjType(ShootingStars.STARDUST, "Stardust", stackable = true)
        for (gem in Mining.GEMS.map { it.obj }.filter { it >= 0 }) objs[gem] = ObjType(gem, "Gem $gem")
        val star = LocCollisionType(width = 2, length = 2)
        val ops = listOf("Mine", "Prospect", null, null, null)
        val types = ShootingStars.STAR_LOCS.map { LocType(it, "Crashed Star", ops, star) } + LocType(ShootingStars.LANDING, "null", collision = star)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            varTypes = VarTypes(6_000, VARBITS),
            random = GameRandom(5),
            locTypes = LocTypes(types.associateBy { it.id }),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.dust() = inventory(BACKPACK).filterNotNull().filter { it.id == ShootingStars.STARDUST }.sumOf { it.count }

    private fun HeadlessClient.seen(text: String) = await(text, timeoutMillis = 30_000) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    /** Lands a size-[size] star two tiles north and waits for it to settle; its south-west tile. */
    private fun HeadlessClient.landHere(size: Int): Pair<Int, Int> {
        val at = await("the login rebuild") { position }
        val x = at.x
        val z = at.z + 2
        commandReply("star land here $size") { it.startsWith("A size-$size star lands") }
        await("the landing") { if (locAt(x, z) == ShootingStars.LANDING) Unit else null }
        await("the star") { if (locAt(x, z) == ShootingStars.idFor(size)) Unit else null }
        return x to z
    }

    private fun HeadlessClient.miner(level: Int) {
        await("the login rebuild") { position }
        command("item $RUNE_PICKAXE")
        command("setlevel mining $level")
        await("level $level") { stat(MINING)?.takeIf { it.level == level } }
    }

    @Test
    fun `Prospect discovers the star, gives the 300 bonus and says its size and level`(@TempDir dir: Path) {
        server(dir).use { server ->
            var site = 0 to 0
            server.login("alice").use { client ->
                val (x, z) = client.landHere(4)
                site = x to z
                client.locOp(ShootingStars.idFor(4), x, z, ShootingStars.PROSPECT)
                client.seen("[shooting_stars.discovered]")
                client.seen("[shooting_stars.prospect size=4 level=40 percent=0]")
                // star_mined (bit 0) and star_bonus_dust 300 (bits 2 to 10) of star_transmit.
                assertEquals(1 or (300 shl 2), client.await("star_transmit") { client.varp(STAR_TRANSMIT).takeIf { it != 0 } })
            }
            server.login("bob").use { client ->
                val (x, z) = site
                client.await("the login rebuild") { client.position }
                client.locOp(ShootingStars.idFor(4), x, z, ShootingStars.PROSPECT)
                client.seen("[shooting_stars.prospect size=4 level=40 percent=0]")
                // Not the first: star_mined only, no bonus and no discovery message.
                assertEquals(1, client.await("star_transmit") { client.varp(STAR_TRANSMIT).takeIf { it != 0 } })
                assertTrue(client.messages().none { it.text == "[shooting_stars.discovered]" })
            }
        }
    }

    @Test
    fun `Mine needs the layer's level and a pickaxe`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                val (x, z) = client.landHere(6)
                client.locOp(ShootingStars.idFor(6), x, z, ShootingStars.MINE)
                client.seen("[shooting_stars.no_pickaxe]")
                client.miner(59)
                client.locOp(ShootingStars.idFor(6), x, z, ShootingStars.MINE)
                client.seen("[shooting_stars.level level=60]")
            }
        }
    }

    @Test
    fun `a stardust stack one short of the cap can't take a bonus roll, so Mine stops`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.miner(99)
                client.command("item ${ShootingStars.STARDUST} ${Int.MAX_VALUE - 1}")
                client.await("the stack") { if (client.dust() == Int.MAX_VALUE - 1) Unit else null }
                client.commandReply("star bonus 300") { it.contains("300 dust") }
                val (x, z) = client.landHere(1)
                client.locOp(ShootingStars.idFor(1), x, z, ShootingStars.MINE)
                client.seen("[shooting_stars.inventory_full]")
                client.commandReply("star bonus") { it.contains("bonus: 300 dust") }
                assertEquals(Int.MAX_VALUE - 1, client.dust())
            }
        }
    }

    @Test
    fun `stardust comes two at a time with the bonus, 32 XP each roll, and the bonus counts down`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.miner(99)
                val (x, z) = client.landHere(9)
                client.command("star bonus 251")
                client.commandReply("star bonus") { it.contains("251 dust") }
                val start = client.stat(MINING)!!.xp
                client.locOp(ShootingStars.idFor(9), x, z, ShootingStars.MINE)
                client.seen("[shooting_stars.swing]")
                // The first player to Mine discovers it, which puts the bonus back to 300.
                client.seen("[shooting_stars.discovered]")
                client.await("stardust") { client.dust().takeIf { it > 0 } }
                assertEquals(2, client.dust())
                assertEquals(start + 32, client.await("XP") { client.stat(MINING)?.xp?.takeIf { it > start } })
                client.commandReply("star bonus") { it.contains("299 dust") }
                client.command("star bonus 51")
                client.seen("[shooting_stars.bonus_left left=50]")
                client.command("star bonus 1")
                client.seen("[shooting_stars.bonus_used_up]")
                client.commandReply("star bonus") { it.contains("bonus: 0 dust") }
            }
        }
    }

    @Test
    fun `a finished layer drops a size, and the last one disintegrates under the miner`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.miner(99)
                val (x, z) = client.landHere(2)
                client.locOp(ShootingStars.idFor(2), x, z, ShootingStars.MINE)
                client.seen("[shooting_stars.swing]")
                client.commandReply("star layer") { it == "The star is size 1 now." }
                client.await("the size-1 star") { if (client.locAt(x, z) == ShootingStars.idFor(1)) Unit else null }
                // The new layer's loc ends the swing, as live asks for a click every 7 minutes: click again.
                val swings = client.messages().count { it.text == "[shooting_stars.swing]" }
                client.locOp(ShootingStars.idFor(1), x, z, ShootingStars.MINE)
                client.await("a second swing") { client.messages().count { it.text == "[shooting_stars.swing]" }.takeIf { it > swings } }
                client.commandReply("star layer") { it == "The star has disintegrated." }
                client.seen("[shooting_stars.disintegrates]")
                client.commandReply("star") { it.startsWith("No star has landed.") }
            }
        }
    }

    @Test
    fun `a star landing on a player moves them off it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fred").use { client ->
                val at = client.await("the login rebuild") { client.position }
                // The star's south-west tile is two north of the player; a second player stands on it.
                server.login("gina").use { other ->
                    other.await("the login rebuild") { other.position }
                    other.command("tele ${at.x + 1} ${at.z + 3}")
                    other.await("on the star's tiles") { other.position?.takeIf { it.x == at.x + 1 && it.z == at.z + 3 } }
                    client.commandReply("star land here 6") { it.startsWith("A size-6 star lands") }
                    val off = other.await("pushed off") { other.position?.takeIf { it.x !in at.x..at.x + 1 || it.z !in at.z + 2..at.z + 3 } }
                    assertTrue(off.x in at.x - 1..at.x + 2 && off.z in at.z + 1..at.z + 4, "beside the star: $off")
                }
            }
        }
    }

    @Test
    fun `star land here refuses a tile with a loc on it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("frank").use { client ->
                client.landHere(6)
                // The star itself is a loc there now.
                client.commandReply("star land here 6") { it.startsWith("A star needs open ground") }
                client.commandReply("star clear") { it == "The star is gone." }
                client.commandReply("star land here 6") { it.startsWith("A size-6 star lands") }
            }
        }
    }

    @Test
    fun `a telescope shows the next star's region and window in the message box, or nothing with none planned`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("telescope").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("star scout teak")
                client.awaitDialogue("nothing") { it == DialogueView.Message("[shooting_stars.telescope_nothing]") }
                client.continueDialogue()
                client.commandReply("star next 7500") { it.startsWith("The next star lands in 7500 ticks") }
                client.command("star scout mahogany")
                val box = client.awaitDialogue("the sighting") { it is DialogueView.Message && it.text.startsWith("[shooting_stars.telescope") }
                val text = (box as DialogueView.Message).text.replace("<br>", " ")
                assertTrue(text.startsWith("[shooting_stars.telescope region=Misthalin "), text)
                val times = Regex("""hours=(\d+) minutes=(\d+)\]""").findAll(text).map { it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }.toList()
                assertEquals(2, times.size, text)
                assertEquals(2, times[1] - times[0])
                assertTrue(74 in times[0]..times[1], text)
            }
        }
    }

    private companion object {
        const val RUNE_PICKAXE = 1275
        const val BACKPACK = 93
        const val MINING = 14
        const val STAR_TRANSMIT = 2924
        val VARBITS = mapOf(
            ShootingStars.VARBIT_MINED to VarbitType(STAR_TRANSMIT, 0, 0),
            ShootingStars.VARBIT_BONUS to VarbitType(STAR_TRANSMIT, 2, 10),
        )
    }
}
