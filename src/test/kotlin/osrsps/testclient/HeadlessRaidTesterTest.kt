package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Scenario
import osrsps.game.Scenarios
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.command.Admins
import osrsps.game.content.RaidTester
import osrsps.game.npc.NpcSpawn
import osrsps.game.raid.ArchiveRaids
import osrsps.map.CollisionMap

/**
 * The raid tester (a testing NPC, not live content) through the headless client, for a player who isn't an admin: his
 * Talk-to's menu starts each loadout's scenario, which lands in the raid's lobby in front of the Archive Door with its
 * stats and gear; his fourth option puts the player back as they were; his last does nothing. The scenarios here are
 * small stand-ins with the real ones' names; the real files are checked by RaidTesterTest and the cache audit.
 */
class HeadlessRaidTesterTest {
    private val tester = NpcType(RaidTester.NPC, "Leagues Tutor", ops = listOf("Talk-to", null, "Trade", "Claim-trophies", null), internalName = "league_tutor_normal")
    private val sword = ObjType(1277, "Bronze sword", wearpos1 = WearPos.WEAPON, internalName = "bronze_sword")
    private val bow = ObjType(841, "Shortbow", wearpos1 = WearPos.WEAPON, internalName = "shortbow")
    private val staff = ObjType(1381, "Staff of air", wearpos1 = WearPos.WEAPON, internalName = "staff_of_air")
    private val weapons = listOf(sword, bow, staff)

    private val scenarios = Scenarios(
        RaidTester.LOADOUTS.zip(weapons).map { (name, weapon) ->
            Scenario.parse(
                name,
                """{ "position": { "x": ${ArchiveRaids.LOBBY_X}, "z": ${ArchiveRaids.LOBBY_Z} }, "skills": { "attack": 99 },
                    "equipment": [ { "item": "${weapon.internalName}" } ] }""",
            )
        },
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(LUMBRIDGE_X shr 6, LUMBRIDGE_Z shr 6)
                markLoaded(ArchiveRaids.LOBBY_X shr 6, ArchiveRaids.LOBBY_Z shr 6)
            },
            objTypes = ObjTypes.of(sword, bow, staff),
            npcTypes = NpcTypes.of(tester),
            npcSpawns = listOf(
                NpcSpawn(tester, LUMBRIDGE_X, LUMBRIDGE_Z, source = "test"),
                NpcSpawn(tester, LOBBY_TESTER_X, LOBBY_TESTER_Z, source = "test"),
            ),
            scenarios = scenarios,
            // Nobody who logs in here is an admin: the tester is for every playtester.
            admins = Admins.parse("somebody_else"),
            cycleMillis = millis,
        )
    }

    /** Talks to the tester standing on [x], [z] and picks [option] of his menu. */
    private fun HeadlessClient.ask(x: Int, z: Int, option: Int) {
        val npc = await("the tester at $x, $z") { npcs().firstOrNull { it.id == RaidTester.NPC && it.coord == Coord(x, z) } }
        npcOp(npc.index, RaidTester.TALK_TO)
        awaitDialogue("his greeting") { it is DialogueView.Chat && it.text == "[raid_tester.greeting]" }
        continueDialogue()
        val menu = awaitDialogue("his menu") { it is DialogueView.Menu } as DialogueView.Menu
        assertEquals(listOf("melee", "ranged", "magic", "put_back", "nothing").map { "[raid_tester.$it]" }, menu.options)
        chooseOption(option)
    }

    @Test
    fun `each loadout lands in the lobby with its gear and stats, and put back undoes it`(@TempDir dir: Path) {
        server(dir).use { server ->
            for ((i, name) in RaidTester.LOADOUTS.withIndex()) {
                server.login("player$i").use { client ->
                    val start = client.await("the login rebuild") { client.position }
                    client.ask(LUMBRIDGE_X, LUMBRIDGE_Z, i + 1)
                    client.await("the scenario's reply") { client.messages().firstOrNull { it.text.startsWith("Started scenario $name.") } }
                    client.await("the lobby") { client.position?.takeIf { it == Coord(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z) } }
                    client.await("the ${weapons[i].name} worn") { client.inventory(WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == weapons[i].id } }
                    client.await("Attack 99") { client.stat(ATTACK)?.takeIf { it.level == 99 } }

                    // The lobby's tester puts the player back where they stood when the scenario began.
                    client.ask(LOBBY_TESTER_X, LOBBY_TESTER_Z, 4)
                    client.await("the reset's reply") { client.messages().firstOrNull { it.text.startsWith("Back to how you were before ::scenario $name") } }
                    val back = client.await("back in Lumbridge") { client.position?.takeIf { it.z < ArchiveRaids.LOBBY_Z - 1000 } }
                    assertTrue(back.x in LUMBRIDGE_X - 3..LUMBRIDGE_X + 3 && back.z in LUMBRIDGE_Z - 3..LUMBRIDGE_Z + 3, "$back, from $start")
                    client.await("the weapon gone") { if (client.inventory(WORN).all { it == null }) Unit else null }
                    client.await("Attack 1 again") { client.stat(ATTACK)?.takeIf { it.level == 1 } }
                }
            }
        }
    }

    @Test
    fun `nothing does nothing, and put back with no loadout says so`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.ask(LUMBRIDGE_X, LUMBRIDGE_Z, 5)
                client.ask(LUMBRIDGE_X, LUMBRIDGE_Z, 4)
                client.await("nothing to reset") { client.messages().firstOrNull { it.text.startsWith("Nothing to reset") } }
                assertTrue(client.messages().none { it.text.startsWith("Started scenario") })
                assertTrue(client.position!!.z < ArchiveRaids.LOBBY_Z - 1000)
            }
        }
    }

    private companion object {
        const val LUMBRIDGE_X = 3225
        const val LUMBRIDGE_Z = 3218
        const val LOBBY_TESTER_X = 4130
        const val LOBBY_TESTER_Z = 5558
        const val WORN = 94
        const val ATTACK = 0
    }
}
