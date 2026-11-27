package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.EvilChicken
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Random events step 19 through the headless client, with the real NPC combat and drop files: hurt, the Evil Chicken
 * says one of its six lines; killed, it drops bones, raw chicken and the killer's combat level plus 0 to 86 feathers.
 * Attacked, it fights back with Magic from where it stands, a hit up to 21 every 4 ticks, for XP at 2.5% of the norm;
 * killed, it is back 50 ticks on. The rl-client script test-runner/examples/evil-chicken.json does part of it in the
 * real game.
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

    @Test
    fun `attacked it casts back from where it stands, gives a fortieth of the XP, and comes back 50 ticks after death`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                for (skill in listOf("attack", "strength", "hitpoints")) client.commandReply("setlevel $skill 99") { it.isNotEmpty() }
                val view = client.await("the chicken in view") { client.npcs().firstOrNull { it.id == EvilChicken.NPC } }
                val player = server.world.onlinePlayers.single()
                val chicken = server.world.npcs.all.single { it.type.id == EvilChicken.NPC }
                val home = chicken.x to chicken.z
                val attackBefore = player.skills.tenths(Skill.ATTACK)
                val events = client.actorEvents().size
                client.npcOp(view.index, ATTACK_OP)

                // Its hits on the player: one cast every 4 ticks (a splash shows no hitsplat), none over 21.
                val hits = client.await("four of its hits", timeoutMillis = 20_000) {
                    client.actorEvents(events).filter { !it.npc && it.index == client.index && it.kind == ActorEventKind.HITSPLAT }.takeIf { it.size >= 4 }
                }
                assertTrue(hits.all { it.amount in 0..21 }, "hits $hits")
                val gaps = hits.zipWithNext { a, b -> b.ticks - a.ticks }
                assertTrue(gaps.all { it > 0 && it % 4 == 0 } && 4 in gaps, "hits $hits")
                assertEquals(home, chicken.x to chicken.z, "it stands where it was")

                // Attack XP for damage on it at 2.5%: 4 a point becomes a tenth a point.
                val dealt = client.await("a hit on it", timeoutMillis = 20_000) {
                    client.npcHits().filter { it.index == view.index }.sumOf { it.value }.takeIf { it > 0 }
                }
                client.walk(player.x - 3, player.z)
                client.awaitTicks(3)
                val onIt = client.npcHits().filter { it.index == view.index }.sumOf { it.value }
                assertTrue(onIt >= dealt)
                assertEquals(onIt, player.skills.tenths(Skill.ATTACK) - attackBefore, "attack XP in tenths for $onIt damage")
                assertEquals(home, chicken.x to chicken.z, "still where it was with its target walked off")

                // Killed, it is back 50 ticks on (paused, so the count is read before a tick passes).
                client.commandReply("pause") { it.startsWith("Paused after tick") }
                client.commandReply("kill ${view.index}") { it.startsWith("Killed") }
                assertEquals("Evil Chicken (6739) back at ${home.first}, ${home.second} in 50 ticks", client.commandReply("respawns") { it.startsWith("Evil Chicken (") })
                client.commandReply("resume") { it.startsWith("Resumed") }
            }
        }
    }

    private companion object {
        const val ATTACK_OP = 2
    }
}
