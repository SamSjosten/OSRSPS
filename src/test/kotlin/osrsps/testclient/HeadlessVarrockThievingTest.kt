package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.PickpocketLines
import osrsps.game.skill.Pickpocketing
import osrsps.game.skill.SuccessRoll
import osrsps.game.skill.Thieving
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Varrock step 13 through the headless client: the tea stall (level 5, a cup of tea and 16 XP, empty for 4 ticks; the
 * Tea Seller who sees it stops the theft, and after one out of his sight won't talk), and pickpocketing a guard and a
 * warrior woman (their levels, XP, pouches and the stun when caught). The rl-client script
 * test-runner/examples/varrock-thieving.json does the same in the real city.
 */
class HeadlessVarrockThievingTest {
    private val pick = listOf(null, "Attack", "Pickpocket", null, null)
    private val guard = NpcType(GUARD, "Guard", ops = pick, combatLevel = 21, internalName = "fai_varrock_guard02")
    private val warrior = NpcType(Pickpocketing.WARRIOR_WOMAN, "Warrior", ops = pick, combatLevel = 24, internalName = "warrior_woman")
    private val seller = NpcType(Thieving.TEA_SELLER, "Tea Seller", ops = listOf("Talk-to", null, "Trade", null, null), internalName = "tea_seller")

    private fun server(dir: Path, vararg spawns: NpcSpawn) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Pickpocketing.GUARD_POUCH + 1_000)
        objs[CUP_OF_TEA] = ObjType(CUP_OF_TEA, "Cup of tea")
        objs[COINS] = ObjType(COINS, "Coins", stackable = true)
        for (pouch in listOf(Pickpocketing.GUARD_POUCH, Pickpocketing.WARRIOR_POUCH)) {
            objs[pouch] = ObjType(pouch, "Coin pouch", stackable = true, iops = listOf("Open-all", "Open", null, null, "Destroy"))
        }
        objs[POT] = ObjType(POT, "Pot")
        val stall = Thieving.STALLS.getValue(TEA_STALL)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(7),
            npcTypes = NpcTypes.of(guard, warrior, seller), npcSpawns = spawns.toList(),
            locTypes = LocTypes(
                listOf(
                    LocType(TEA_STALL, "Tea stall", listOf(null, "Steal-from", null, null, null), LocCollisionType(width = 1, length = 1)),
                    LocType(stall.empty, "Market stall", List(5) { null }, LocCollisionType(width = 1, length = 1)),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { add(ZoneLoc(TEA_STALL, LocShape.CENTREPIECE_STRAIGHT, 0, 3224, 3218, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.npc(id: Int) = await("$id in view") { npcs().firstOrNull { it.id == id } }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    /** Pickpockets [id] once and waits for how it went: true when it worked. */
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

    @Test
    fun `the tea stall needs level 5, then gives a cup of tea and 16 XP and is empty for 4 ticks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel thieving 4")
                client.await("level 4") { client.stat(THIEVING)?.takeIf { it.level == 4 } }
                client.locOp(TEA_STALL, 3224, 3218, 2)
                client.awaitDialogue("the level message") {
                    it is DialogueView.Message && it.text == "[thieving.level level=5 stall=tea stall]"
                }
                client.continueDialogue()
                assertEquals(0, client.count(CUP_OF_TEA))
                client.command("setlevel thieving 5")
                val alice = server.world.playerNamed("alice")!!
                client.await("level 5") { client.stat(THIEVING)?.takeIf { it.level == 5 } }
                val before = alice.skills.tenths(Skill.THIEVING)
                client.locOp(TEA_STALL, 3224, 3218, 2)
                client.await("a cup of tea") { if (client.count(CUP_OF_TEA) == 1) Unit else null }
                client.await("the theft's message") { if (client.seen("[thieving.stolen item=Cup of tea]") == 1) Unit else null }
                assertEquals(before + 160, alice.skills.tenths(Skill.THIEVING))
                val emptied = client.await("the empty stall") {
                    if (client.locAt(3224, 3218) == Thieving.STALLS.getValue(TEA_STALL).empty) server.world.tick else null
                }
                client.await("the stall back") { if (client.locAt(3224, 3218) == TEA_STALL) Unit else null }
                assertTrue(server.world.tick - emptied <= 6, "back ${server.world.tick - emptied} ticks after")
            }
        }
    }

    @Test
    fun `the Tea Seller stops a theft he sees`(@TempDir dir: Path) {
        // Beside the stall: he sees it.
        server(dir, NpcSpawn(seller, 3225, 3220, wander = 0, source = "test")).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel thieving 5")
                client.await("level 5") { client.stat(THIEVING)?.takeIf { it.level == 5 } }
                client.locOp(TEA_STALL, 3224, 3218, 2)
                client.await("his shout") { client.npcSaid().firstOrNull { it.text == "[thieving.caught]" } }
                client.awaitTicks(2)
                assertEquals(0, client.count(CUP_OF_TEA))
                assertEquals(TEA_STALL, client.locAt(3224, 3218) ?: TEA_STALL)
            }
        }
    }

    @Test
    fun `after a theft he doesn't see, the Tea Seller won't talk`(@TempDir dir: Path) {
        // Eight tiles off, past the five he sees.
        server(dir, NpcSpawn(seller, 3232, 3218, wander = 0, source = "test")).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel thieving 5")
                client.await("level 5") { client.stat(THIEVING)?.takeIf { it.level == 5 } }
                client.locOp(TEA_STALL, 3224, 3218, 2)
                client.await("a cup of tea") { if (client.count(CUP_OF_TEA) == 1) Unit else null }
                client.npcOp(client.npc(Thieving.TEA_SELLER).index, 1)
                client.await("his anger") { client.npcSaid().firstOrNull { it.text == "[thieving.owner_angry]" } }
                client.awaitTicks(3)
                assertNull(client.dialogueView, "no talk")
            }
        }
    }

    @Test
    fun `a guard needs level 40, gives a pouch of 30 coins and 46,8 XP, or hits for 2 and stuns`(@TempDir dir: Path) {
        server(dir, NpcSpawn(guard, 3223, 3218, wander = 0, source = "test")).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel thieving 39")
                client.await("level 39") { client.stat(THIEVING)?.takeIf { it.level == 39 } }
                client.npcOp(client.npc(GUARD).index, Pickpocketing.PICKPOCKET)
                client.await("the level message") { if (client.seen("[pickpocket.level level=40 who=guard]") == 1) Unit else null }
                client.command("setlevel thieving 40")
                client.command("setlevel hitpoints 99")
                client.await("level 40") { client.stat(THIEVING)?.takeIf { it.level == 40 } }
                pickpocketBothWays(server, client, "carol", GUARD, "guard", 468, Pickpocketing.GUARD_POUCH)
                // Open-all (iop 1): 30 coins a pouch.
                val pouches = client.count(Pickpocketing.GUARD_POUCH)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 2, slotOf(client, Pickpocketing.GUARD_POUCH), Pickpocketing.GUARD_POUCH)
                client.await("all opened") { if (client.count(Pickpocketing.GUARD_POUCH) == 0) Unit else null }
                assertEquals(pouches * 30, client.count(COINS))
                assertEquals(1, client.seen("[pickpocket.pouch_open_all coins=${pouches * 30}]"))
            }
        }
    }

    @Test
    fun `a warrior woman needs level 25, gives a pouch of 18 coins and 26 XP, or hits for 2 and stuns`(@TempDir dir: Path) {
        server(dir, NpcSpawn(warrior, 3223, 3218, wander = 0, source = "test")).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel thieving 24")
                client.await("level 24") { client.stat(THIEVING)?.takeIf { it.level == 24 } }
                client.npcOp(client.npc(Pickpocketing.WARRIOR_WOMAN).index, Pickpocketing.PICKPOCKET)
                client.await("the level message") { if (client.seen("[pickpocket.level level=25 who=warrior]") == 1) Unit else null }
                client.command("setlevel thieving 25")
                client.command("setlevel hitpoints 99")
                client.await("level 25") { client.stat(THIEVING)?.takeIf { it.level == 25 } }
                pickpocketBothWays(server, client, "dave", Pickpocketing.WARRIOR_WOMAN, "warrior", 260, Pickpocketing.WARRIOR_POUCH)
                // Open (iop 2) one pouch: 18 coins.
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 3, slotOf(client, Pickpocketing.WARRIOR_POUCH), Pickpocketing.WARRIOR_POUCH)
                client.await("18 coins") { if (client.count(COINS) == 18) Unit else null }
                assertEquals(1, client.seen("[pickpocket.pouch_open coins=18]"))
            }
        }
    }

    @Test
    fun `the guard's and warrior's chances follow their pairs`() {
        val guard = Pickpocketing.POCKETS.single { GUARD in it.npcs }
        val warrior = Pickpocketing.POCKETS.single { Pickpocketing.WARRIOR_WOMAN in it.npcs }
        // The Wiki's warrior chart: 135/256 at 25, 241/256 at 99.
        assertEquals(135, SuccessRoll.chance(warrior.low, warrior.high, 25))
        assertEquals(241, SuccessRoll.chance(warrior.low, warrior.high, 99))
        // The guard reaches 256 with the Ardougne diary's 10% at 95, the Pickpocketing page's 100% level, not at 94.
        assertTrue(SuccessRoll.chance(guard.low, guard.high, 95) * 1.1 >= 256)
        assertTrue(SuccessRoll.chance(guard.low, guard.high, 94) * 1.1 < 256)
        assertEquals((Pickpocketing.VARROCK_GUARDS + Pickpocketing.EDGEVILLE_GUARD).toSet(), guard.npcs.toSet())
        // Both say the townsfolk's line when they catch a thief.
        assertEquals(PickpocketLines.CAUGHT_CITIZEN, guard.caught)
        assertEquals(PickpocketLines.CAUGHT_CITIZEN, warrior.caught)
        assertEquals(30..30, Pickpocketing.POUCH_COINS[Pickpocketing.GUARD_POUCH])
        assertEquals(18..18, Pickpocketing.POUCH_COINS[Pickpocketing.WARRIOR_POUCH])
    }

    private fun slotOf(client: HeadlessClient, id: Int) =
        client.await("item $id in the backpack") { client.inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    /** Pickpockets [id] until it has both worked (a pouch, [xpTenths]) and failed (2 damage, a stun). */
    private fun pickpocketBothWays(server: TestServer, client: HeadlessClient, name: String, id: Int, who: String, xpTenths: Int, pouch: Int) {
        val player = server.world.playerNamed(name)!!
        var won = false
        var lost = false
        var tries = 0
        while (!(won && lost)) {
            assertTrue(++tries <= 60, "60 tries both work and fail")
            client.await("the wait to end") { if (server.world.tick >= player.nextPickpocketTick && !server.world.statusEffects.isStunned(player)) Unit else null }
            val tenths = player.skills.tenths(Skill.THIEVING)
            val pouches = client.count(pouch)
            val hp = player.skills.currentLevel(Skill.HITPOINTS)
            if (client.tryOnce(id, who)) {
                won = true
                client.await("a pouch") { if (client.count(pouch) == pouches + 1) Unit else null }
                assertEquals(tenths + xpTenths, player.skills.tenths(Skill.THIEVING))
            } else {
                lost = true
                client.await("the hit") { if (player.skills.currentLevel(Skill.HITPOINTS) == hp - 2) Unit else null }
                assertTrue(server.world.statusEffects.isStunned(player))
                client.await("the stun's message") { if (client.seen("[pickpocket.stunned]") > 0) Unit else null }
            }
        }
    }

    private companion object {
        const val TEA_STALL = 635
        const val GUARD = 11911
        const val CUP_OF_TEA = 1978
        const val COINS = 995
        const val POT = 1931
        const val BACKPACK = 93
        const val THIEVING = 17
    }
}
