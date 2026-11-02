package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.content.ArchiveLobby
import osrsps.game.ge.GrandExchangeScreen
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Raid step 14 through the headless client: the Chasm of Tears' cavity, open from login as after While Guthix Sleeps,
 * crawls into The Fractured Archive's lobby and the lobby's Opening goes back; the bank chest and the banker bank; the
 * Wizard apprentice's first talk is remembered. The rl-client script test-runner/examples/archive-lobby.json does the
 * same in the real client.
 */
class HeadlessArchiveLobbyTest {
    private fun type(id: Int, name: String, size: Int, vararg ops: String?, multiloc: Boolean = false) = LocType(
        id, name, ops = ops.toList() + List(5 - ops.size) { null },
        collision = LocCollisionType(width = size, length = size), multiloc = multiloc,
    )

    private val locTypes = LocTypes(
        listOf(
            type(ArchiveLobby.CAVITY, "null", 3, multiloc = true),
            type(ArchiveLobby.CAVITY_UPPER, "Opening", 3, "Crawl-through"),
            type(ArchiveLobby.LOBBY_EXIT, "Opening", 3, "Enter"),
            type(BANK_CHEST, "Bank Chest", 1, "Use", null, "Collect"),
        ).associateBy { it.id },
    )

    private val talk = listOf("Talk-to", null, null, null, null)
    private val banker = NpcType(ArchiveLobby.BANKER, "Banker", ops = listOf("Talk-to", null, "Bank", "Collect", null), internalName = "tfa_lobby_banker")
    private val apprentice = NpcType(ArchiveLobby.WIZARD_APPRENTICE, "Wizard apprentice", ops = talk, internalName = "tfa_wizard_apprentice")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(ArchiveLobby.CHASM_X shr 6, ArchiveLobby.CHASM_Z shr 6)
                markLoaded(ArchiveLobby.LOBBY_X shr 6, ArchiveLobby.LOBBY_Z shr 6)
            },
            locTypes = locTypes,
            npcTypes = NpcTypes.of(banker, apprentice),
            npcSpawns = listOf(NpcSpawn(banker, 4122, 5553, source = "test"), NpcSpawn(apprentice, 4113, 5536, source = "test")),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(ArchiveLobby.CAVITY, LocShape.CENTREPIECE_STRAIGHT, 3, 4050, 4567, 0))
                add(ZoneLoc(ArchiveLobby.CAVITY_UPPER, LocShape.CENTREPIECE_STRAIGHT, 3, 4050, 4567, 1))
                add(ZoneLoc(ArchiveLobby.LOBBY_EXIT, LocShape.CENTREPIECE_STRAIGHT, 2, 4126, 5505, 0))
                add(ZoneLoc(BANK_CHEST, LocShape.CENTREPIECE_DIAGONAL, 3, 4121, 5553, 0))
            },
            varTypes = VarTypes(
                VARP_COUNT,
                mapOf(
                    ArchiveLobby.VARBIT_CAVITY to VarbitType(VARP_CAVITY, 3, 3),
                    ArchiveLobby.VARBIT_APPRENTICE_MET to VarbitType(VARP_APPRENTICE, 7, 7),
                ),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int = 0) =
        assertEquals(Coord(x, z, level), await("$x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } })

    private fun HeadlessClient.awaitOpen(id: Int, what: String) = await(what) { openInterfaces().takeIf { id in it } }

    @Test
    fun `the cavity is open from login, crawls into the lobby, and the lobby's Opening goes back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.await("the cavity's varbit") { if ((client.varp(VARP_CAVITY) ushr 3) and 1 == 1) Unit else null }
                client.tele(ArchiveLobby.CHASM_X, ArchiveLobby.CHASM_Z)
                client.locOp(ArchiveLobby.CAVITY, 4050, 4567, ArchiveLobby.CRAWL)
                client.awaitAt(ArchiveLobby.LOBBY_X, ArchiveLobby.LOBBY_Z)
                client.locOp(ArchiveLobby.LOBBY_EXIT, 4126, 5505, ArchiveLobby.ENTER)
                client.awaitAt(ArchiveLobby.CHASM_X, ArchiveLobby.CHASM_Z)
                client.tele(ArchiveLobby.CHASM_X, ArchiveLobby.CHASM_Z, 1)
                client.locOp(ArchiveLobby.CAVITY_UPPER, 4050, 4567, ArchiveLobby.CRAWL)
                client.awaitAt(ArchiveLobby.LOBBY_X, ArchiveLobby.LOBBY_Z)
            }
        }
    }

    @Test
    fun `the bank chest's Use opens the bank and Collect the collection box`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(4120, 5553)
                client.locOp(BANK_CHEST, 4121, 5553, 1)
                client.awaitOpen(BankScreen.MAIN, "the bank")
            }
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(4120, 5553)
                client.locOp(BANK_CHEST, 4121, 5553, 3)
                client.awaitOpen(GrandExchangeScreen.COLLECTION_BOX, "the collection box")
            }
        }
    }

    @Test
    fun `the banker's Bank, Collect, and asking her for the bank`(@TempDir dir: Path) {
        server(dir).use { server ->
            for ((name, op) in listOf("dave" to ArchiveLobby.BANK, "erin" to ArchiveLobby.COLLECT, "frank" to ArchiveLobby.TALK_TO)) {
                server.login(name).use { client ->
                    client.await("the login rebuild") { client.position }
                    client.tele(4124, 5553)
                    val npc = client.await("the banker in view") { client.npcs().firstOrNull { it.id == ArchiveLobby.BANKER } }
                    client.npcOp(npc.index, op)
                    if (op == ArchiveLobby.TALK_TO) {
                        client.awaitDialogue("tfa_banker.greeting") { it is DialogueView.Chat && it.text == "[tfa_banker.greeting]" }
                        client.continueDialogue()
                        client.awaitDialogue("her menu") { it is DialogueView.Menu }
                        client.chooseOption(1)
                    }
                    val screen = if (op == ArchiveLobby.COLLECT) GrandExchangeScreen.COLLECTION_BOX else BankScreen.MAIN
                    client.awaitOpen(screen, "screen $screen")
                }
            }
        }
    }

    @Test
    fun `the Wizard apprentice's first talk sets his varbit, and the next is his later talk`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("grace").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(4113, 5534)
                val npc = client.await("the apprentice in view") { client.npcs().firstOrNull { it.id == ArchiveLobby.WIZARD_APPRENTICE } }
                client.npcOp(npc.index, ArchiveLobby.TALK_TO)
                for (i in 1..26) {
                    val key = "tfa_wizard_apprentice.first_$i"
                    client.awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
                    client.continueDialogue()
                }
                client.await("the met varbit") { if ((client.varp(VARP_APPRENTICE) ushr 7) and 1 == 1) Unit else null }
                client.npcOp(npc.index, ArchiveLobby.TALK_TO)
                client.awaitDialogue("the later talk") { it is DialogueView.Chat && it.text == "[tfa_wizard_apprentice.later_1]" }
            }
        }
    }

    private companion object {
        /** tfa_lobby_bankchest_open: the lobby's bank chest, Use op1 and Collect op3 in the 241.4 cache. */
        const val BANK_CHEST = 63275
        const val VARP_COUNT = 5_000
        const val VARP_CAVITY = 700
        const val VARP_APPRENTICE = 701
    }
}
