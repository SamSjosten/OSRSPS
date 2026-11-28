package osrsps.testclient

import net.rsprot.protocol.game.outgoing.prot.GameServerProt
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.ClanHall
import osrsps.game.content.ClanHallFurniture
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The clan hall's piano and dummy through the headless client: Play-quietly opens the piano with its script and a key
 * clicked plays for nobody else; Entertain plays it for everyone in the hall, the player too; Background-music plays Rhapsody; the
 * dummy gives no experience. The rl-client script test-runner/examples/clan-piano.json does the same on the real map.
 */
class HeadlessClanHallFurnitureTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(ClanHall.TEMPLATE_X shr 6, ClanHall.TEMPLATE_Z shr 6)
                markLoaded(PORTAL_X shr 6, PORTAL_Z shr 6)
            },
            locTypes = LocTypes(
                listOf(
                    LocType(ClanHall.PORTAL, "Clan hall portal", ops = listOf("Enter", "Enter-member", "Enter-guest", null, null), collision = LocCollisionType(width = 1, length = 1)),
                    LocType(ClanHallFurniture.PIANO, "Piano", ops = listOf("Entertain", "Play-quietly", "Background-music", null, null), collision = LocCollisionType(width = 1, length = 1)),
                    LocType(ClanHallFurniture.DUMMY, "Dummy", ops = listOf("Attack", null, null, null, null), collision = LocCollisionType(width = 1, length = 1)),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(ClanHall.PORTAL, LocShape.CENTREPIECE_STRAIGHT, 0, PORTAL_X, PORTAL_Z, 0))
                add(ZoneLoc(ClanHallFurniture.PIANO, LocShape.CENTREPIECE_STRAIGHT, 0, PIANO_X, PIANO_Z, 0))
                add(ZoneLoc(ClanHallFurniture.DUMMY, LocShape.CENTREPIECE_STRAIGHT, 0, DUMMY_X, DUMMY_Z, 0))
            },
            cycleMillis = millis,
            clansDir = dir.resolve("data"),
        )
    }

    private fun HeadlessClient.enterHall(): Coord {
        command("tele ${PORTAL_X - 1} $PORTAL_Z 0")
        await("beside the portal") { position?.takeIf { it == Coord(PORTAL_X - 1, PORTAL_Z, 0) } }
        locOp(ClanHall.PORTAL, PORTAL_X, PORTAL_Z, ClanHall.ENTER)
        return await("the hall") { position?.takeIf { it.x >= 6400 } }
    }

    private fun HeadlessClient.synths(): Int = packets().count { it.prot == GameServerProt.SYNTH_SOUND }

    @Test
    fun `the piano plays quietly or for the hall, and the dummy teaches nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.await("the login rebuild") { alice.position }
                    bob.await("the login rebuild") { bob.position }
                    alice.commandReply("clanmake Odins Gate") { it.startsWith("Clan") }
                    alice.commandReply("clanjoin Odins Gate") { it.startsWith("Joined") }
                    bob.commandReply("clanjoin Odins Gate") { it.startsWith("Joined") }
                    val at = alice.enterHall()
                    bob.enterHall()
                    val hall = server.world.instances.at(at.x, at.z)!!
                    fun x(template: Int) = hall.baseX + template - ClanHall.TEMPLATE_X
                    fun z(template: Int) = hall.baseZ + template - ClanHall.TEMPLATE_Z

                    alice.command("tele ${x(PIANO_X)} ${z(PIANO_Z) - 1} 0")
                    alice.await("beside the piano") { alice.position?.takeIf { it == Coord(x(PIANO_X), z(PIANO_Z) - 1, 0) } }

                    // Play-quietly: the window and its script; a key plays for nobody else.
                    alice.locOp(ClanHallFurniture.PIANO, x(PIANO_X), z(PIANO_Z), ClanHallFurniture.PLAY_QUIETLY)
                    alice.await("the piano") { if (ClanHallFurniture.WINDOW in alice.openInterfaces()) Unit else null }
                    alice.await("its script") { alice.clientScripts().firstOrNull { it.id == ClanHallFurniture.SCRIPT_PIANO } }
                    val quiet = bob.synths()
                    alice.ifButton(ClanHallFurniture.WINDOW, 31, 1)
                    alice.awaitTicks(2)
                    assertEquals(quiet, bob.synths())
                    alice.ifButton(ClanHallFurniture.WINDOW, ClanHallFurniture.CLOSE, 1)
                    alice.await("closed") { if (ClanHallFurniture.WINDOW in alice.openInterfaces()) null else Unit }
                    // Closing clears the script's keyboard hold (%smithing_listening) with smithing_close.
                    alice.await("smithing_close") { alice.clientScripts().firstOrNull { it.id == ClanHallFurniture.SCRIPT_SMITHING_CLOSE } }

                    // Entertain: no script (the client mustn't play the key itself); the key plays for bob and alice.
                    val scripts = alice.clientScripts().count { it.id == ClanHallFurniture.SCRIPT_PIANO }
                    alice.locOp(ClanHallFurniture.PIANO, x(PIANO_X), z(PIANO_Z), ClanHallFurniture.ENTERTAIN)
                    alice.await("the piano again") { if (ClanHallFurniture.WINDOW in alice.openInterfaces()) Unit else null }
                    val before = alice.synths()
                    alice.ifButton(ClanHallFurniture.WINDOW, 31, 1)
                    bob.await("bob hears it") { Unit.takeIf { bob.synths() > quiet } }
                    alice.await("alice hears it") { Unit.takeIf { alice.synths() > before } }
                    assertEquals(scripts, alice.clientScripts().count { it.id == ClanHallFurniture.SCRIPT_PIANO })

                    // Three keys in one tick play together as a chord, but a key pressed again that tick isn't sent
                    // again (exploit review R65C-4, round 67 R67C-3).
                    alice.awaitTicks(1)
                    val heard = bob.synths()
                    alice.commandReply("pause") { it.startsWith("Paused after tick") }
                    repeat(3) { alice.ifButton(ClanHallFurniture.WINDOW, 32 + it, 1) }
                    repeat(4) { alice.ifButton(ClanHallFurniture.WINDOW, 32, 1) }
                    alice.commandReply("resume") { it.startsWith("Resumed") }
                    bob.await("bob hears the tick's chord") { Unit.takeIf { bob.synths() >= heard + 3 } }
                    alice.awaitTicks(2)
                    assertEquals(heard + 3, bob.synths(), "each key once a tick")
                    alice.closeModal()

                    // Background-music: Rhapsody.
                    alice.awaitTicks(1)
                    alice.locOp(ClanHallFurniture.PIANO, x(PIANO_X), z(PIANO_Z), ClanHallFurniture.BACKGROUND_MUSIC)
                    alice.await("the song") { alice.packets().firstOrNull { it.prot == GameServerProt.MIDI_SONG_V2 } }

                    // The dummy: no experience.
                    alice.command("tele ${x(DUMMY_X)} ${z(DUMMY_Z) - 1} 0")
                    alice.await("beside the dummy") { alice.position?.takeIf { it == Coord(x(DUMMY_X), z(DUMMY_Z) - 1, 0) } }
                    alice.locOp(ClanHallFurniture.DUMMY, x(DUMMY_X), z(DUMMY_Z), ClanHallFurniture.ATTACK)
                    alice.await("a swing") { alice.messages().firstOrNull { it.text == "[dummy.hit]" } }
                    assertFalse(server.world.playerNamed("alice")!!.skills.xp(Skill.ATTACK) > 0)
                }
            }
        }
    }

    private companion object {
        const val PORTAL_X = 3186
        const val PORTAL_Z = 3471
        const val PIANO_X = 1756
        const val PIANO_Z = 5463
        const val DUMMY_X = 1744
        const val DUMMY_Z = 5456
    }
}
