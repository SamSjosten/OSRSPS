package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.StatusEffects
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Pickpocketing
import osrsps.map.CollisionMap

/**
 * Thieving 2 step T1b through the headless client: a paladin (level 70, 131.8 XP, a pouch and 2 chaos runes, 3 damage
 * and a 4.8-second stun when caught), a watchman (a pouch and a bread, and no room for both with one free slot) and a
 * hero (its table, a 6-second stun). The rl-client script test-runner/examples/thieving-t1b.json pickpockets the real
 * Kandarin and Wilderness NPCs.
 */
class HeadlessPickpocketKandarinTest {
    private val pick = listOf(null, "Attack", "Pickpocket", null, null)
    private val paladin = NpcType(PALADIN, "Paladin", ops = pick, combatLevel = 62, internalName = "paladin")
    private val watchman = NpcType(WATCHMAN, "Watchman", ops = pick, combatLevel = 33, internalName = "yanille_watchman")
    private val hero = NpcType(HERO, "Hero", ops = pick, combatLevel = 69, internalName = "hero")

    private fun server(dir: Path, seed: Long = 7) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Pickpocketing.HERO_POUCH + 100)
        for (pouch in listOf(Pickpocketing.PALADIN_POUCH, Pickpocketing.WATCHMAN_POUCH, Pickpocketing.HERO_POUCH)) {
            objs[pouch] = ObjType(pouch, "Coin pouch", stackable = true, iops = listOf("Open-all", "Open", null, null, "Destroy"))
        }
        for (rune in listOf(CHAOS_RUNE, 560, 565)) objs[rune] = ObjType(rune, "Rune", stackable = true)
        objs[BREAD] = ObjType(BREAD, "Bread")
        objs[JUNK] = ObjType(JUNK, "Pot")
        for (other in listOf(1993, 1601, 569, 444)) objs[other] = ObjType(other, "Loot $other")
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(seed),
            npcTypes = NpcTypes.of(paladin, watchman, hero),
            npcSpawns = listOf(
                NpcSpawn(paladin, 3223, 3218, wander = 0, source = "test"),
                NpcSpawn(watchman, 3221, 3218, wander = 0, source = "test"),
                NpcSpawn(hero, 3222, 3220, wander = 0, source = "test"),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.npc(id: Int) = await("$id in view") { npcs().firstOrNull { it.id == id } }

    private fun HeadlessClient.ready(server: TestServer, name: String) {
        val player = server.world.playerNamed(name)!!
        await("the wait to end") { if (server.world.tick >= player.nextPickpocketTick && !server.world.statusEffects.isStunned(player)) Unit else null }
    }

    private fun HeadlessClient.tryOnce(id: Int, who: String): Boolean {
        val wins = seen("[pickpocket.success who=$who]")
        val fails = seen("[pickpocket.fail who=$who]")
        npcOp(npc(id).index, Pickpocketing.PICKPOCKET)
        return await("the pickpocket's result") {
            when {
                seen("[pickpocket.success who=$who]") > wins -> true
                seen("[pickpocket.fail who=$who]") > fails -> false
                else -> null
            }
        }
    }

    private fun HeadlessClient.level(skill: String, id: Int, level: Int) {
        command("setlevel $skill $level")
        await("$skill $level") { stat(id)?.takeIf { it.level == level } }
    }

    /**
     * Pickpockets [id] until it has both worked and failed: [won] after each success with the XP before it, and the
     * stun after each failure is checked to last no more than [stunTicks] with a blow of 1 to [damage].
     */
    private fun HeadlessClient.bothWays(server: TestServer, name: String, id: Int, who: String, xp: Int, stunTicks: Int, damage: Int, won: () -> Unit) {
        val player = server.world.playerNamed(name)!!
        var wins = 0
        var lost = false
        var tries = 0
        while (wins == 0 || !lost) {
            assertTrue(++tries <= 60, "60 tries both work and fail")
            ready(server, name)
            val tenths = player.skills.tenths(Skill.THIEVING)
            val hp = player.skills.currentLevel(Skill.HITPOINTS)
            if (tryOnce(id, who)) {
                wins++
                await("the XP") { if (player.skills.tenths(Skill.THIEVING) == tenths + xp) Unit else null }
                won()
            } else {
                lost = true
                await("the stun") { if (server.world.statusEffects.isStunned(player)) Unit else null }
                assertTrue(player.stunnedUntil <= server.world.tick + stunTicks, "stunned $stunTicks ticks at most")
                await("the blow") { player.skills.currentLevel(Skill.HITPOINTS).takeIf { it < hp } }
                assertTrue(hp - player.skills.currentLevel(Skill.HITPOINTS) in 1..damage)
            }
        }
    }

    @Test
    fun `a paladin needs level 70 and gives a pouch, 2 chaos runes and 131,8 XP, or hits for up to 3 and stuns`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.level("thieving", THIEVING, 69)
                client.npcOp(client.npc(PALADIN).index, Pickpocketing.PICKPOCKET)
                client.await("the level message") { if (client.seen("[pickpocket.level level=70 who=paladin]") == 1) Unit else null }
                client.level("thieving", THIEVING, 99)
                client.level("hitpoints", HITPOINTS, 99)
                client.bothWays(server, "alice", PALADIN, "paladin", 1318, Pickpocketing.STUN_4_8_SECONDS, 3) {
                    val pouches = client.count(Pickpocketing.PALADIN_POUCH)
                    client.await("the chaos runes") { if (client.count(CHAOS_RUNE) == 2 * pouches) Unit else null }
                }
            }
        }
    }

    @Test
    fun `a watchman gives a pouch and a bread, and won't with one slot free and no pouch held`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.level("thieving", THIEVING, 99)
                client.level("hitpoints", HITPOINTS, 99)
                val player = server.world.playerNamed("bob")!!
                client.command("item $JUNK 27")
                client.await("27 pots") { if (client.count(JUNK) == 27) Unit else null }
                assertEquals(1, player.inventory.freeSlots)
                client.ready(server, "bob")
                client.npcOp(client.npc(WATCHMAN).index, Pickpocketing.PICKPOCKET)
                client.await("no room") { if (client.seen("[pickpocket.inventory_full]") == 1) Unit else null }
                assertEquals(0, client.count(Pickpocketing.WATCHMAN_POUCH))
                client.command("empty")
                client.await("an empty backpack") { if (client.count(JUNK) == 0) Unit else null }
                client.bothWays(server, "bob", WATCHMAN, "watchman", 1375, StatusEffects.PICKPOCKET_TICKS, 3) {
                    val pouches = client.count(Pickpocketing.WATCHMAN_POUCH)
                    client.await("the bread") { if (client.count(BREAD) == pouches) Unit else null }
                }
            }
        }
    }

    @Test
    fun `a paladin won't be pickpocketed when the chaos runes would pass the most a stack holds`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.level("thieving", THIEVING, 99)
                val player = server.world.playerNamed("dave")!!
                player.inventory.add(server.world.objTypes[CHAOS_RUNE]!!, Int.MAX_VALUE - 1)
                client.await("the chaos runes") { if (client.count(CHAOS_RUNE) == Int.MAX_VALUE - 1) Unit else null }
                val tenths = player.skills.tenths(Skill.THIEVING)
                client.ready(server, "dave")
                client.npcOp(client.npc(PALADIN).index, Pickpocketing.PICKPOCKET)
                client.await("no room") { if (client.seen("[pickpocket.inventory_full]") == 1) Unit else null }
                assertEquals(0, client.seen("[pickpocket.attempt who=paladin]"))
                assertEquals(tenths, player.skills.tenths(Skill.THIEVING))
                assertEquals(0, client.count(Pickpocketing.PALADIN_POUCH))
                // One rune fewer leaves room for the two.
                player.inventory.remove(player.inventory.contents().single { it.second.id == CHAOS_RUNE }.first, 1)
                client.level("hitpoints", HITPOINTS, 99)
                var tries = 0
                while (!client.run { ready(server, "dave"); tryOnce(PALADIN, "paladin") }) assertTrue(++tries <= 30, "a success within 30 tries")
                client.await("a full stack") { if (client.count(CHAOS_RUNE) == Int.MAX_VALUE) Unit else null }
                assertEquals(1, client.count(Pickpocketing.PALADIN_POUCH))
            }
        }
    }

    @Test
    fun `a hero gives from its table, mostly a pouch, and 163,3 XP, or stuns for 6 seconds`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.level("thieving", THIEVING, 99)
                client.level("hitpoints", HITPOINTS, 99)
                var items = 0
                client.bothWays(server, "carol", HERO, "hero", 1633, Pickpocketing.STUN_6_SECONDS, 3) {
                    items++
                    client.await("one more item") { if (client.inventory(BACKPACK).filterNotNull().sumOf { if (it.id == 560) it.count / 2 else it.count } == items) Unit else null }
                }
            }
        }
    }

    private companion object {
        const val PALADIN = 3293
        const val WATCHMAN = 5420
        const val HERO = 3295
        const val CHAOS_RUNE = 562
        const val BREAD = 2309
        const val JUNK = 1931
        const val BACKPACK = 93
        const val THIEVING = 17
        const val HITPOINTS = 3
    }
}
