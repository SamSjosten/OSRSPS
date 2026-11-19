package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.EvilChicken
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Random events step 19 through the headless client, with the real NPC combat and drop files: hurt, the Evil Chicken
 * says one of its six lines; killed, it drops bones, raw chicken and the killer's combat level plus 0 to 86 feathers.
 * The rl-client script test-runner/examples/evil-chicken.json does it in the real game.
 */
class HeadlessEvilChickenTest {
    private val chicken = NpcType(
        EvilChicken.NPC, "Evil Chicken", ops = listOf(null, "Attack", null, null, null), combatLevel = 159, internalName = "evil_chicken",
        stats = NpcStats(defence = 126, hitpoints = 120, magic = 200),
    )
    private val objs = ObjTypes.of(ObjType(526, "Bones"), ObjType(2138, "Raw chicken"), ObjType(EvilChicken.FEATHER, "Feather", stackable = true))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(chicken)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(chicken, 3224, 3218, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objs), cycleMillis = millis,
        )
    }

    @Test
    fun `hurt it speaks, and killed it drops bones, raw chicken and the killer's feathers`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val view = client.await("the chicken in view") { client.npcs().firstOrNull { it.id == EvilChicken.NPC } }
                client.commandReply("kill ${view.index}") { it.startsWith("Killed") }
                val said = client.await("its line") { client.npcSaid().firstOrNull { it.index == view.index } }
                assertTrue(said.text.matches(Regex("\\[evil_chicken\\.(bwaaaaaauk|bwuk|bwuk_bwuk_bwuk|flee name=alice|begone name=alice|muahaha)]")), said.text)
                val drops = client.await("the drops", timeoutMillis = 20_000) {
                    client.groundObjs().takeIf { objs -> objs.any { it.id == EvilChicken.FEATHER } }
                }
                assertTrue(drops.any { it.id == 526 } && drops.any { it.id == 2138 }, "bones and raw chicken: $drops")
                val level = server.world.onlinePlayers.single().skills.combatLevel
                val feathers = drops.single { it.id == EvilChicken.FEATHER }.count
                assertTrue(feathers in level..level + EvilChicken.FEATHER_RANDOM, "feathers $feathers for level $level")
            }
        }
    }
}
