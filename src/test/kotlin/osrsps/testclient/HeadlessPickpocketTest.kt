package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.combat.Foods
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Pickpocketing
import osrsps.map.CollisionMap

/**
 * Draynor piece step 8 through the headless client: pickpocketing a man (coin pouches, the stun when caught, the pouch
 * limit and opening them) and the Master Farmer (the level, and a seed from his table). The rl-client script
 * test-runner/examples/draynor-pickpocket.json pickpockets in the real game.
 */
class HeadlessPickpocketTest {
    private val pick = listOf("Talk-to", "Attack", "Pickpocket", null, null)
    private val man = NpcType(MAN, "Man", ops = pick, internalName = "man")
    private val farmer = NpcType(FARMER, "Master Farmer", ops = listOf(null, null, "Pickpocket", null, null), internalName = "master_farmer_1")

    private fun server(dir: Path, seed: Long = 7) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Pickpocketing.CITIZEN_POUCH + 1_000)
        for (loot in Pickpocketing.MASTER_FARMER_LOOT) objs[loot.obj] = ObjType(loot.obj, "Seed ${loot.obj}", stackable = true)
        objs[COINS] = ObjType(COINS, "Coins", stackable = true)
        objs[POUCH] = ObjType(POUCH, "Coin pouch", stackable = true, iops = listOf("Open-all", "Open", null, null, "Destroy"))
        objs[POT] = ObjType(POT, "Pot")
        objs[SHARK] = ObjType(SHARK, "Shark", iops = listOf("Eat", null, null, null, "Drop"), internalName = "shark")
        val objTypes = ObjTypes(objs)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objTypes, foodList = Foods.load(objTypes), random = GameRandom(seed),
            npcTypes = NpcTypes.of(man, farmer),
            npcSpawns = listOf(NpcSpawn(man, 3223, 3218, source = "test"), NpcSpawn(farmer, 3221, 3218, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.npc(id: Int) = await("$id in view") { npcs().firstOrNull { it.id == id } }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

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
    fun `a man gives a coin pouch and 8 XP, or catches the thief, hits for 1 and stuns`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                var won = false
                var lost = false
                repeat(40) {
                    if (won && lost) return@repeat
                    val before = client.await("Thieving") { client.stat(THIEVING) }.xp
                    val pouches = client.count(POUCH)
                    val hp = client.await("hitpoints") { client.stat(HITPOINTS) }.level
                    if (client.tryOnce(MAN, "man")) {
                        won = true
                        client.await("8 XP") { client.stat(THIEVING)?.takeIf { it.xp == before + 8 } }
                        client.await("a pouch") { if (client.count(POUCH) == pouches + 1) Unit else null }
                        // A next try waits 2 ticks.
                        client.awaitTicks(Pickpocketing.ATTEMPT_TICKS)
                    } else {
                        lost = true
                        client.await("the stun's message") { if (client.seen("[pickpocket.stunned]") > 0) Unit else null }
                        client.await("the hit") { client.stat(HITPOINTS)?.takeIf { it.level == hp - 1 } }
                        assertTrue(server.world.statusEffects.isStunned(server.world.playerNamed("alice")!!))
                        // Stunned, a try does nothing; then it ends.
                        val attempts = client.seen("[pickpocket.attempt who=man]")
                        client.npcOp(client.npc(MAN).index, Pickpocketing.PICKPOCKET)
                        client.awaitTicks(2)
                        assertEquals(attempts, client.seen("[pickpocket.attempt who=man]"))
                        client.await("the stun to end") { if (!server.world.statusEffects.isStunned(server.world.playerNamed("alice")!!)) Unit else null }
                        // With the stun over, a next pickpocket still waits 8 ticks.
                        client.npcOp(client.npc(MAN).index, Pickpocketing.PICKPOCKET)
                        client.awaitTicks(2)
                        assertEquals(attempts, client.seen("[pickpocket.attempt who=man]"))
                        client.awaitTicks(Pickpocketing.AFTER_STUN_TICKS)
                        client.command("heal")
                    }
                }
                assertTrue(won && lost, "40 tries at level 1 (70.7%) both work and fail")
            }
        }
    }

    @Test
    fun `eating pushes the next pickpocket back past the food's delay`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $SHARK 1")
                val shark = client.slotOf(SHARK)
                val attempts = client.seen("[pickpocket.attempt who=man]")
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, EAT_BUTTON_OP, shark, SHARK)
                client.npcOp(client.npc(MAN).index, Pickpocketing.PICKPOCKET)
                client.await("the shark eaten") { if (client.count(SHARK) == 0) Unit else null }
                client.awaitTicks(1)
                assertEquals(attempts, client.seen("[pickpocket.attempt who=man]"), "within the shark's 3 ticks")
                client.awaitTicks(Foods.DEFAULT_DELAY)
                client.tryOnce(MAN, "man")
            }
        }
    }

    @Test
    fun `28 pouches must be opened before the next pickpocket, and Open-all gives 3 coins each`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $POUCH 28")
                client.await("28 pouches") { if (client.count(POUCH) == 28) Unit else null }
                client.npcOp(client.npc(MAN).index, Pickpocketing.PICKPOCKET)
                client.await("pouches full") { if (client.seen("[pickpocket.pouches_full]") == 1) Unit else null }
                assertEquals(0, client.seen("[pickpocket.attempt who=man]"))
                // Open (iop 2) takes one; Open-all (iop 1) the rest.
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 3, client.slotOf(POUCH), POUCH)
                client.await("one opened") { if (client.count(POUCH) == 27 && client.count(COINS) == 3) Unit else null }
                assertEquals(1, client.seen("[pickpocket.pouch_open coins=3]"))
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 2, client.slotOf(POUCH), POUCH)
                client.await("all opened") { if (client.count(POUCH) == 0 && client.count(COINS) == 84) Unit else null }
                assertEquals(1, client.seen("[pickpocket.pouch_open_all coins=81]"))
            }
        }
    }

    @Test
    fun `the Master Farmer needs level 38, then gives a seed from his table and 43 XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel thieving 37")
                client.await("level 37") { client.stat(THIEVING)?.takeIf { it.level == 37 } }
                client.npcOp(client.npc(FARMER).index, Pickpocketing.PICKPOCKET)
                client.await("the level message") { if (client.seen("[pickpocket.level level=38 who=Master Farmer]") == 1) Unit else null }
                client.command("setlevel thieving 99")
                val xp = client.await("level 99") { client.stat(THIEVING)?.takeIf { it.level == 99 } }.xp
                var tries = 0
                while (!client.tryOnce(FARMER, "Master Farmer")) {
                    assertTrue(++tries < 10, "94% at 99 lands within 10 tries")
                    client.await("the stun to end") { if (!server.world.statusEffects.isStunned(server.world.playerNamed("carol")!!)) Unit else null }
                    client.awaitTicks(Pickpocketing.AFTER_STUN_TICKS)
                    client.command("heal")
                }
                val seed = client.await("a seed") { client.inventory(BACKPACK).filterNotNull().firstOrNull { it.id != POT && it.id != COINS } }
                assertTrue(Pickpocketing.MASTER_FARMER_LOOT.any { it.obj == seed.id && seed.count in it.min..it.max })
                client.await("43 XP") { client.stat(THIEVING)?.takeIf { it.xp == xp + 43 } }
            }
        }
    }

    @Test
    fun `the Master Farmer's ranarr seeds come twice as often at Farming 85 as at 1`(@TempDir dir: Path) {
        server(dir).use { server ->
            val farmerPocket = Pickpocketing.POCKETS.single { FARMER in it.npcs }
            val ranarr = farmerPocket.loot.single { it.obj == RANARR }
            assertEquals(Math.round(10_000_000 / 555.83).toInt(), ranarr.weight(1))
            assertEquals(Math.round(10_000_000 / 268.75).toInt(), ranarr.weight(85))
            assertEquals(ranarr.weight(85), ranarr.weight(99))
            // The table's rates add up to about one: every pickpocket gives a seed.
            val total = farmerPocket.loot.sumOf { it.weight(1) }
            assertTrue(total in 9_800_000..10_200_000, "total $total")
            // Potato seeds are the most common, about 1 in 5.65.
            val potatoes = (1..2_000).count { server.world.pickpocketing.pick(farmerPocket, 1).first == POTATO }
            assertTrue(potatoes in 280..430, "potatoes $potatoes")
        }
    }

    private companion object {
        const val MAN = 3106
        const val FARMER = 5730
        const val POUCH = Pickpocketing.CITIZEN_POUCH
        const val COINS = 995
        const val POT = 1931
        const val RANARR = 5295
        const val POTATO = 5318
        const val BACKPACK = 93
        const val THIEVING = 17
        const val HITPOINTS = 3
        const val SHARK = 385
        const val EAT_BUTTON_OP = 2
    }
}
