package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.npc.NpcSpawn
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.EdgevilleClosedLines
import osrsps.game.content.EdgevillePeople
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Edgeville piece step 3 through the headless client: the Basement of Doom's trapdoor (each player's own, on varbit
 * 1888) and cellar stairs, the refusals on the lever, fairy ring, Soul Wars portal, and Death's Domain,
 * Doris's talk, and Lucien showing (on varbit 10753, set at login as after the quest) with his new pendant.
 */
class HeadlessEdgevillePeopleTest {
    private fun type(id: Int, name: String, vararg ops: String?, multiloc: Boolean = false) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1), multiloc = multiloc)

    private val locTypes = LocTypes(
        listOf(
            type(EdgevillePeople.TRAPDOOR, "null", multiloc = true),
            type(EdgevillePeople.CELLAR_STAIRS, "Cellar stairs", "Climb"),
            type(EdgevillePeople.LEVER, "Lever", "Pull"),
            type(EdgevillePeople.FAIRY_RING, "Fairy ring", "Zanaris", "Configure", "Last-destination", "Favourites"),
            type(EdgevillePeople.SOUL_WARS_PORTAL, "Soul Wars Portal", "Enter"),
            type(EdgevillePeople.DEATHS_DOMAIN, "Death's Domain", "Enter"),
        ).associateBy { it.id },
    )

    private val talk = listOf("Talk-to", null, null, null, null)
    private val doris = NpcType(EdgevillePeople.DORIS, "Doris", ops = talk, internalName = "hundred_dave_mum")
    private val evilDave = NpcType(EdgevillePeople.EVIL_DAVE, "Evil Dave", ops = talk, internalName = "hundred_evil_dave")
    private val hari = NpcType(EdgevillePeople.HARI, "Hari", ops = listOf("Talk-to", null, "Store-axe", null, null), internalName = "canoeing_hari")
    private val lucienShown = NpcType(EdgevillePeople.LUCIEN_SHOWN, "Lucien", ops = talk, internalName = "ikov_lucien2_vis_noattack")
    private val lucien = NpcType(
        EdgevillePeople.LUCIEN, multiVarbit = EdgevillePeople.VARBIT_LUCIEN, multiNpcs = intArrayOf(-1, EdgevillePeople.LUCIEN_SHOWN, 13607),
        internalName = "ikov_lucien2",
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(EdgevillePeople.PENDANT_OF_LUCIEN + 1)
        objs[EdgevillePeople.PENDANT_OF_LUCIEN] = ObjType(EdgevillePeople.PENDANT_OF_LUCIEN, "Pendant of Lucien")
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3077 shr 6, 3493 shr 6)
                markLoaded(3077 shr 6, 9893 shr 6)
                markLoaded(3130 shr 6, 3508 shr 6)
            },
            objTypes = ObjTypes(objs),
            locTypes = locTypes,
            npcTypes = NpcTypes.of(doris, lucien, lucienShown, evilDave, hari),
            npcSpawns = listOf(
                NpcSpawn(doris, 3080, 3491, source = "test"), NpcSpawn(lucien, 3082, 3491, source = "test"),
                NpcSpawn(evilDave, 3080, 9890, source = "test"), NpcSpawn(hari, 3133, 3509, source = "test"),
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(EdgevillePeople.TRAPDOOR, LocShape.GROUND_DECOR, 3, 3077, 3493, 0))
                add(ZoneLoc(EdgevillePeople.CELLAR_STAIRS, LocShape.CENTREPIECE_STRAIGHT, 0, 3076, 9893, 0))
                add(ZoneLoc(EdgevillePeople.LEVER, LocShape.CENTREPIECE_STRAIGHT, 0, 3090, 3475, 0))
                add(ZoneLoc(EdgevillePeople.FAIRY_RING, LocShape.CENTREPIECE_STRAIGHT, 0, 3129, 3496, 0))
                add(ZoneLoc(EdgevillePeople.SOUL_WARS_PORTAL, LocShape.CENTREPIECE_DIAGONAL, 3, 3082, 3473, 0))
                add(ZoneLoc(EdgevillePeople.DEATHS_DOMAIN, LocShape.CENTREPIECE_STRAIGHT, 0, 3096, 3476, 0))
            },
            varTypes = VarTypes(
                VARP_COUNT,
                mapOf(
                    EdgevillePeople.VARBIT_TRAPDOOR to VarbitType(VARP_TRAPDOOR, 22, 22),
                    EdgevillePeople.VARBIT_LUCIEN to VarbitType(VARP_LUCIEN, 29, 30),
                ),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    /** Does [action] and waits for [text], counting the messages from before the action so a quick reply isn't missed. */
    private fun HeadlessClient.expectMessage(text: String, action: () -> Unit) {
        val before = messages().count { it.text == text }
        action()
        await("the message '$text'") { if (messages().count { it.text == text } > before) Unit else null }
    }

    private fun HeadlessClient.chat(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    private fun HeadlessClient.trapdoor() = (varp(VARP_TRAPDOOR) ushr 22) and 1

    @Test
    fun `the trapdoor opens and closes for the player, goes down when open, and the stairs come back up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3078, 3493, 0)
                client.locOp(EdgevillePeople.TRAPDOOR, 3077, 3493, 1)
                client.await("the trapdoor open") { if (client.trapdoor() == 1) Unit else null }
                client.locOp(EdgevillePeople.TRAPDOOR, 3077, 3493, 2)
                client.await("the trapdoor shut") { if (client.trapdoor() == 0) Unit else null }
                client.locOp(EdgevillePeople.TRAPDOOR, 3077, 3493, 1)
                client.await("the trapdoor open again") { if (client.trapdoor() == 1) Unit else null }

                client.locOp(EdgevillePeople.TRAPDOOR, 3077, 3493, 1)
                client.await("the basement") { client.position?.takeIf { it == Coord(EdgevillePeople.BASEMENT_X, EdgevillePeople.BASEMENT_Z, 0) } }
                client.locOp(EdgevillePeople.CELLAR_STAIRS, 3076, 9893, 1)
                client.await("Doris's house") { client.position?.takeIf { it == Coord(EdgevillePeople.HOUSE_X, EdgevillePeople.HOUSE_Z, 0) } }
            }
        }
    }

    @Test
    fun `the lever, fairy ring, Soul Wars portal and Death's Domain refuse`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3091, 3475, 0)
                client.expectMessage(DialogueText.NONE[EdgevilleClosedLines.LEVER]) { client.locOp(EdgevillePeople.LEVER, 3090, 3475, 1) }
                client.tele(3083, 3474, 0)
                client.expectMessage(DialogueText.NONE[EdgevilleClosedLines.SOUL_WARS]) { client.locOp(EdgevillePeople.SOUL_WARS_PORTAL, 3082, 3473, 1) }
                client.tele(3097, 3476, 0)
                client.expectMessage(DialogueText.NONE[EdgevilleClosedLines.DEATHS_DOMAIN]) { client.locOp(EdgevillePeople.DEATHS_DOMAIN, 3096, 3476, 1) }
                client.tele(3130, 3496, 0)
                for (option in 1..4) {
                    client.expectMessage(DialogueText.NONE[EdgevilleClosedLines.FAIRY_RING]) { client.locOp(EdgevillePeople.FAIRY_RING, 3129, 3496, option) }
                }
            }
        }
    }

    @Test
    fun `Doris talks, and Lucien shows after the quest and gives a new pendant`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3081, 3492, 0)
                client.npcOp(client.await("Doris in view") { client.npcs().firstOrNull { it.id == doris.id } }.index, EdgevillePeople.TALK_TO)
                client.chat("doris.greeting")
                client.awaitDialogue("her menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat("doris.pretty_good")
                client.chat("doris.pretty_good_reply")

                assertEquals(1, (client.varp(VARP_LUCIEN) ushr 29) and 3, "Lucien shown, as after the quest")
                client.npcOp(client.await("Lucien in view") { client.npcs().firstOrNull { it.id == lucien.id } }.index, EdgevillePeople.TALK_TO)
                client.chat("lucien.what")
                client.chat("lucien.lost_pendant")
                client.chat("lucien.new_pendant")
                client.await("the pendant") { if (client.inventory(BACKPACK).any { it?.id == EdgevillePeople.PENDANT_OF_LUCIEN }) Unit else null }
            }
        }
    }

    @Test
    fun `Evil Dave and Hari talk through the client`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3079, 9890, 0)
                client.npcOp(client.await("Evil Dave in view") { client.npcs().firstOrNull { it.id == evilDave.id } }.index, EdgevillePeople.TALK_TO)
                client.chat("evil_dave.greeting")
                client.awaitDialogue("his menu") { it is DialogueView.Menu }
                client.chooseOption(3)
                client.chat("evil_dave.time_travel")
                client.awaitDialogue("the time travel menu") { it is DialogueView.Menu }
                client.chooseOption(5)
                client.chat("evil_dave.never_mind")

                client.tele(3132, 3509, 0)
                client.npcOp(client.await("Hari in view") { client.npcs().firstOrNull { it.id == hari.id } }.index, EdgevillePeople.TALK_TO)
                client.chat("hari.hello")
                client.chat("hari.hello_reply")
                client.awaitDialogue("his menu") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.chat("hari.teach_me")
                // A new account's Woodcutting is 1: below the log canoe's level.
                client.chat("hari.no_skill")
            }
        }
    }

    private companion object {
        const val VARP_COUNT = 5_000

        /** hundred_dave_trapdoor is bit 22 of varp 680; ikov_lucien_vis is bits 29 and 30 of varp 4093 (241 cache). */
        const val VARP_TRAPDOOR = 680
        const val VARP_LUCIEN = 4093

        const val BACKPACK = 93
    }
}
