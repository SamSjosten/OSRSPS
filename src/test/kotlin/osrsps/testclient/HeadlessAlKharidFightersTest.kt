package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.AlKharidCamels
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Al Kharid step 6 through the headless client, with the real combat and drop files: an Al Kharid man killed leaves his
 * bones, and a camel's talk. The rl-client script test-runner/examples/al-kharid-fighters.json does the same in the real
 * client, and kills a scorpion and a warrior.
 */
class HeadlessAlKharidFightersTest {
    private val man = NpcType(
        AL_KHARID_MAN, "Man", ops = listOf(null, "Attack", "Pickpocket", null, null), combatLevel = 2,
        internalName = "al_kharid_man", stats = NpcStats(hitpoints = 7),
    )

    private val camel = NpcType(AlKharidCamels.AL, "Al the Camel", ops = listOf("Talk-to", null, null, null, null), size = 2, internalName = "al_the_camel")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        // Lumbridge's man too, so the files' "man" finds him and not Al Kharid's by its display name.
        val types = NpcTypes.of(man, camel, NpcType(3106, "Man", internalName = "man"))
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(9), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3224, 3218, source = "test"), NpcSpawn(camel, 3218, 3218, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    @Test
    fun `an Al Kharid man killed drops his bones`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == AL_KHARID_MAN } }
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.npcOp(npc.index, 2)
                // Every hit is 0 or 1, so seven of them, however the dice fall.
                client.await("the man's death", timeoutMillis = 90_000) { if (client.npcs().none { it.id == AL_KHARID_MAN }) Unit else null }
                val drops = client.await("the bones") { client.groundObjs().map { it.id }.takeIf { BONES in it } }
                assertEquals(1, drops.count { it == BONES })
                assertEquals(7 * 4, client.stat(ATTACK)!!.xp)
            }
        }
    }

    @Test
    fun `a camel's talk, something unpleasant and its reaction`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val npc = client.await("Al in view") { client.npcs().firstOrNull { it.id == AlKharidCamels.AL } }
                client.npcOp(npc.index, AlKharidCamels.TALK_TO)
                val menu = client.awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[camel.dung]", "[camel.unpleasant]", "[camel.neither]"), menu.options)
                client.chooseOption(2)
                val rude = client.awaitDialogue("a rude line") { it is DialogueView.Chat && it.text.startsWith("[camel.rude_") } as DialogueView.Chat
                assertEquals("bob", rude.name)
                client.continueDialogue()
                client.awaitDialogue("its reaction") { it is DialogueView.Message && it.text.startsWith("[camel.reacts_") }
            }
        }
    }

    private companion object {
        const val AL_KHARID_MAN = 3261
        const val BONES = 526
        const val ATTACK = 0
    }
}
