package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Pickpocketing
import osrsps.map.CollisionMap

/**
 * Thieving 2 step T1c through the headless client: a TzHaar-Hur (level 90, 103.4 XP, tokkul or an uncut gem, and its
 * heat's 4 damage on a success without ice gloves, none with them) and a wealthy citizen (Pickpocket its first op, 96
 * XP, a pouch or house keys). The rl-client script test-runner/examples/thieving-t1c.json pickpockets the real ones.
 */
class HeadlessPickpocketFarTest {
    private val hur = NpcType(HUR, "TzHaar-Hur", ops = listOf(null, "Attack", "Pickpocket", null, null), combatLevel = 74, internalName = "tzhaar_hur_city1")
    private val citizen = NpcType(
        CITIZEN, "Wealthy citizen", ops = listOf("Pickpocket", null, "Talk-to", null, null), combatLevel = 0, internalName = "varlamore_wealthy_citizen_a",
    )

    private val goblin = NpcType(
        Pickpocketing.CAVE_GOBLINS.first(), "Cave goblin", ops = listOf(null, "Attack", "Pickpocket", null, null), combatLevel = 3,
        internalName = "dorgesh_citizen",
    )

    private fun server(dir: Path, seed: Long = 5) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(KEYS + 100)
        objs[TOKKUL] = ObjType(TOKKUL, "Tokkul", stackable = true)
        for (gem in listOf(1623, 1621, 1619, 1617)) objs[gem] = ObjType(gem, "Uncut gem")
        objs[ICE_GLOVES] = ObjType(ICE_GLOVES, "Ice gloves")
        objs[Pickpocketing.WEALTHY_CITIZEN_POUCH] = ObjType(Pickpocketing.WEALTHY_CITIZEN_POUCH, "Coin pouch", stackable = true, iops = listOf("Open-all", "Open", null, null, "Destroy"))
        objs[KEYS] = ObjType(KEYS, "House keys", stackable = true)
        for (loot in Pickpocketing.CAVE_GOBLIN_LOOT) objs[loot.obj] = ObjType(loot.obj, "Loot ${loot.obj}", stackable = loot.obj == Pickpocketing.CAVE_GOBLIN_POUCH)
        objs[POT] = ObjType(POT, "Pot")
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(seed),
            npcTypes = NpcTypes.of(hur, citizen, goblin),
            npcSpawns = listOf(
                NpcSpawn(hur, 3223, 3218, wander = 0, source = "test"), NpcSpawn(citizen, 3221, 3218, wander = 0, source = "test"),
                NpcSpawn(goblin, 3222, 3220, wander = 0, source = "test"),
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

    private fun HeadlessClient.tryOnce(id: Int, who: String, op: Int = Pickpocketing.PICKPOCKET): Boolean {
        val wins = seen("[pickpocket.success who=$who]")
        val fails = seen("[pickpocket.fail who=$who]")
        npcOp(npc(id).index, op)
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

    @Test
    fun `a TzHaar-Hur burns for 4 on a success without ice gloves, not with them, and gives tokkul or an uncut gem`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.level("thieving", THIEVING, 89)
                client.npcOp(client.npc(HUR).index, Pickpocketing.PICKPOCKET)
                client.await("the level message") { if (client.seen("[pickpocket.level level=90 who=TzHaar-Hur]") == 1) Unit else null }
                client.level("thieving", THIEVING, 99)
                client.level("hitpoints", HITPOINTS, 99)
                val player = server.world.playerNamed("alice")!!
                for (gloves in listOf(false, true)) {
                    if (gloves) player.equipment[WearPos.HANDS] = InvObj(ICE_GLOVES, 1)
                    var tries = 0
                    while (true) {
                        assertTrue(++tries <= 40, "a success within 40 tries")
                        client.ready(server, "alice")
                        val hp = player.skills.currentLevel(Skill.HITPOINTS)
                        val tenths = player.skills.tenths(Skill.THIEVING)
                        val loot = client.inventory(BACKPACK).filterNotNull().sumOf { it.count }
                        if (!client.tryOnce(HUR, "TzHaar-Hur")) continue
                        client.await("the loot") { if (client.inventory(BACKPACK).filterNotNull().sumOf { it.count } > loot) Unit else null }
                        assertEquals(tenths + 1034, player.skills.tenths(Skill.THIEVING))
                        if (gloves) {
                            server.world.tick.let { now -> client.await("two ticks") { if (server.world.tick >= now + 2) Unit else null } }
                            assertEquals(hp, player.skills.currentLevel(Skill.HITPOINTS), "no heat with ice gloves")
                        } else {
                            client.await("the heat") { if (player.skills.currentLevel(Skill.HITPOINTS) == hp - Pickpocketing.TZHAAR_HEAT) Unit else null }
                        }
                        break
                    }
                }
            }
        }
    }

    @Test
    fun `a wealthy citizen is pickpocketed by its first op for a pouch or house keys and 96 XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.level("thieving", THIEVING, 99)
                client.level("hitpoints", HITPOINTS, 99)
                val player = server.world.playerNamed("bob")!!
                var tries = 0
                var wins = 0
                while (wins < 3) {
                    assertTrue(++tries <= 40, "three successes within 40 tries")
                    client.ready(server, "bob")
                    val tenths = player.skills.tenths(Skill.THIEVING)
                    val loot = client.count(Pickpocketing.WEALTHY_CITIZEN_POUCH) + client.count(KEYS)
                    if (!client.tryOnce(CITIZEN, "wealthy citizen", Pickpocketing.WEALTHY_PICKPOCKET)) continue
                    wins++
                    assertEquals(tenths + 960, player.skills.tenths(Skill.THIEVING))
                    // The table's 1/85 gives nothing.
                    client.await("a pouch or keys, or nothing") {
                        if (client.count(Pickpocketing.WEALTHY_CITIZEN_POUCH) + client.count(KEYS) in loot..loot + 1) Unit else null
                    }
                }
                assertTrue(client.count(Pickpocketing.WEALTHY_CITIZEN_POUCH) > 0, "the pouch is the usual loot")
            }
        }
    }

    @Test
    fun `a cave goblin isn't pickpocketed without room for its most, 4 iron ores`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.level("thieving", THIEVING, 99)
                client.command("item $POT 25")
                client.await("25 pots") { if (client.count(POT) == 25) Unit else null }
                client.npcOp(client.npc(goblin.id).index, Pickpocketing.PICKPOCKET)
                client.await("no room") { if (client.seen("[pickpocket.inventory_full]") == 1) Unit else null }
                assertEquals(0, client.seen("[pickpocket.attempt who=cave goblin]"))
                client.command("empty")
                client.await("an empty backpack") { if (client.count(POT) == 0) Unit else null }
                client.command("item $POT 24")
                client.await("24 pots") { if (client.count(POT) == 24) Unit else null }
                client.ready(server, "dave")
                client.npcOp(client.npc(goblin.id).index, Pickpocketing.PICKPOCKET)
                client.await("the attempt with 4 free") { if (client.seen("[pickpocket.attempt who=cave goblin]") == 1) Unit else null }
            }
        }
    }

    private companion object {
        const val POT = 1931
        const val HUR = 7682
        const val CITIZEN = 13302
        const val TOKKUL = 6529
        const val ICE_GLOVES = 1580
        const val KEYS = 29325
        const val BACKPACK = 93
        const val THIEVING = 17
        const val HITPOINTS = 3
    }
}
