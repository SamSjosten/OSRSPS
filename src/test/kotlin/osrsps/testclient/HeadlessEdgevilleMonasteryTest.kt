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
import osrsps.game.npc.NpcSpawn
import osrsps.game.content.EdgevilleMonastery
import osrsps.game.content.LumbridgeCastle
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Edgeville piece step 4 through the headless client: the monastery's ground-floor ladders stop a player who hasn't
 * joined (a monk's line at the south-western one, with no monk about; the Abbot and his join menu at the south-eastern one), joining at 31 Prayer
 * lets them up, and the upstairs altar recharges prayer to 2 above the level.
 */
class HeadlessEdgevilleMonasteryTest {
    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val locTypes = LocTypes(
        listOf(
            type(EdgevilleMonastery.LADDER, "Ladder", "Climb-up"),
            type(LumbridgeCastle.LADDER_TOP, "Ladder", "Climb-down"),
            type(EdgevilleMonastery.ALTAR, "Altar", "Pray-at"),
        ).associateBy { it.id },
    )

    private val monk = NpcType(EdgevilleMonastery.MONK, "Monk", ops = listOf("Talk-to", "Attack", null, null, null), internalName = "monk")
    private val abbot = NpcType(EdgevilleMonastery.ABBOT_LANGLEY, "Abbot Langley", ops = listOf("Talk-to", null, null, null, null), internalName = "abbot_langley")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply { markLoaded(3057 shr 6, 3483 shr 6) },
            locTypes = locTypes,
            npcTypes = NpcTypes.of(monk, abbot),
            npcSpawns = listOf(NpcSpawn(abbot, 3058, 3486, wander = 0, source = "test")),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(EdgevilleMonastery.LADDER, LocShape.CENTREPIECE_STRAIGHT, 1, 3046, 3483, 0))
                add(ZoneLoc(EdgevilleMonastery.LADDER, LocShape.CENTREPIECE_STRAIGHT, 1, 3057, 3483, 0))
                add(ZoneLoc(LumbridgeCastle.LADDER_TOP, LocShape.CENTREPIECE_STRAIGHT, 1, 3057, 3483, 1))
                add(ZoneLoc(EdgevilleMonastery.ALTAR, LocShape.CENTREPIECE_STRAIGHT, 0, 3051, 3498, 1))
            },
            varTypes = VarTypes(VARP_COUNT, mapOf(EdgevilleMonastery.VARBIT_JOINED to VarbitType(VARP_JOINED, 0, 0))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.chat(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    @Test
    fun `the ladders stop a player outside the order, the Abbot lets them join at 31 Prayer, and then they climb`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3046, 3484, 0)
                client.locOp(EdgevilleMonastery.LADDER, 3046, 3483, 1)
                client.chat("edgeville_monk.ladder")
                client.awaitTicks(2)
                assertEquals(Coord(3046, 3484, 0), client.position)

                // Under 31 Prayer the Abbot refuses.
                client.tele(3057, 3484, 0)
                client.locOp(EdgevilleMonastery.LADDER, 3057, 3483, 1)
                client.chat("abbot_langley.members_only")
                client.awaitDialogue("the join menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat("abbot_langley.join")
                client.chat("abbot_langley.too_low")
                client.awaitDialogue("the requirement") { it is DialogueView.Message && it.text == "[abbot_langley.requirement]" }
                client.continueDialogue()
                assertEquals(0, client.varp(VARP_JOINED) and 1)

                client.commandReply("setlevel prayer 31") { it.contains("31") }
                client.locOp(EdgevilleMonastery.LADDER, 3057, 3483, 1)
                client.chat("abbot_langley.members_only")
                client.awaitDialogue("the join menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat("abbot_langley.join")
                client.chat("abbot_langley.accepted")
                client.await("joined") { if (client.varp(VARP_JOINED) and 1 == 1) Unit else null }

                client.locOp(EdgevilleMonastery.LADDER, 3057, 3483, 1)
                client.await("upstairs") { client.position?.takeIf { it.level == 1 } }
            }
        }
    }

    @Test
    fun `the upstairs altar recharges prayer to 2 above the level`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel prayer 31") { it.contains("31") }
                client.tele(3051, 3497, 1)
                client.locOp(EdgevilleMonastery.ALTAR, 3051, 3498, 1)
                client.await("the boosted prayer") { client.stat(PRAYER)?.takeIf { it.level == 33 } }
            }
        }
    }

    private companion object {
        const val VARP_COUNT = 5_000
        const val VARP_JOINED = 190
        const val PRAYER = 5
    }
}
