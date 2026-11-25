package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InvObj
import osrsps.game.PlayerOps
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.EmoteLines
import osrsps.game.content.Emotes
import osrsps.game.content.StrongholdOfSecurity
import osrsps.game.dialogue.DialogueText
import osrsps.map.CollisionMap

/**
 * The emote tab through the headless client: a default emote plays its animation, a Stronghold of Security emote is
 * refused until its varbit is set, then plays with its spotanim; and an emote stops a follow. The rl-client script test-runner/examples/emotes.json
 * does the same in the real client.
 */
class HeadlessEmotesTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(3090 shr 6, 3253 shr 6) },
            varTypes = VarTypes(
                VARP_COUNT,
                mapOf(
                    StrongholdOfSecurity.VARBIT_FLAP to VarbitType(VARP_SOS_EMOTE, 0, 0),
                    StrongholdOfSecurity.VARBIT_SLAP_HEAD to VarbitType(VARP_SOS_EMOTE, 1, 1),
                    StrongholdOfSecurity.VARBIT_IDEA to VarbitType(VARP_SOS_EMOTE, 2, 2),
                    StrongholdOfSecurity.VARBIT_STAMP to VarbitType(VARP_SOS_EMOTE, 3, 3),
                ),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.played(from: Int, anim: Int) =
        await("animation $anim") { actorEvents(from).firstOrNull { !it.npc && it.index == index && it.kind == ActorEventKind.ANIMATION && it.id == anim } }

    @Test
    fun `Yes plays at once, Idea only once the Box of Health has unlocked it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                var mark = client.actorEvents().size
                client.ifButton(Emotes.INTERFACE, Emotes.CONTENTS, 1, sub = 0)
                client.played(mark, ANIM_YES)

                // Idea is locked until its varbit is set.
                mark = client.actorEvents().size
                client.ifButton(Emotes.INTERFACE, Emotes.CONTENTS, 1, sub = IDEA)
                client.await("refused") { client.messages().firstOrNull { it.text == DialogueText.NONE[EmoteLines.LOCKED] } }
                client.awaitTicks(2)
                assertTrue(client.actorEvents(mark).none { it.kind == ActorEventKind.ANIMATION && it.id == ANIM_IDEA }, "no Idea while locked")

                server.world.playerNamed("alice")!!.vars.setVarbit(StrongholdOfSecurity.VARBIT_IDEA, 1)
                mark = client.actorEvents().size
                client.ifButton(Emotes.INTERFACE, Emotes.CONTENTS, 2, sub = IDEA)
                client.played(mark, ANIM_IDEA)
                client.await("the lightbulb") { client.actorEvents(mark).firstOrNull { !it.npc && it.kind == ActorEventKind.SPOTANIM && it.id == SPOT_IDEA } }
            }
        }
    }

    @Test
    fun `Skill Cape plays only in a quest point cape, and in the Wise Old Man's house counts for the diary`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                var mark = client.actorEvents().size
                client.ifButton(Emotes.INTERFACE, Emotes.CONTENTS, 1, sub = Emotes.SKILL_CAPE)
                client.awaitTicks(2)
                assertTrue(client.actorEvents(mark).none { it.kind == ActorEventKind.ANIMATION && it.id == Emotes.QUEST_CAPE.anim }, "no cape worn")

                server.world.playerNamed("alice")!!.equipment[WearPos.CAPE] = InvObj(QUEST_POINT_CAPE, 1)
                client.command("tele 3090 3253 0")
                client.await("the house") { client.position?.takeIf { it == Coord(3090, 3253, 0) } }
                mark = client.actorEvents().size
                client.ifButton(Emotes.INTERFACE, Emotes.CONTENTS, 1, sub = Emotes.SKILL_CAPE)
                client.played(mark, Emotes.QUEST_CAPE.anim)
                client.await("the diary task") { Unit.takeIf { client.messages().any { it.text.startsWith("[diary.task_done") } } }
            }
        }
    }

    @Test
    fun `an emote stops a follow where the player stands`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.await("alice's rebuild") { alice.position }
                    bob.await("bob's rebuild") { bob.position }
                    bob.await("alice in view") { bob.otherPlayers()[alice.index] }
                    val bobStart = bob.position!!
                    bob.playerOp(alice.index, PlayerOps.FOLLOW)
                    alice.walk(alice.position!!.x + 4, alice.position!!.z)
                    bob.await("following", timeoutMillis = 10_000) { bob.position?.takeIf { it != bobStart } }
                    val mark = bob.actorEvents().size
                    bob.ifButton(Emotes.INTERFACE, Emotes.CONTENTS, 1, sub = 0)
                    bob.played(mark, ANIM_YES)
                    val stopped = bob.position!!
                    alice.walk(alice.position!!.x + 6, alice.position!!.z)
                    alice.awaitTicks(8)
                    assertEquals(stopped, bob.position, "no longer following")
                }
            }
        }
    }

    private companion object {
        const val VARP_COUNT = 5000
        const val VARP_SOS_EMOTE = 802
        const val QUEST_POINT_CAPE = 9813
        const val IDEA = 28
        const val ANIM_YES = 855
        const val ANIM_IDEA = 4276
        const val SPOT_IDEA = 712
    }
}
