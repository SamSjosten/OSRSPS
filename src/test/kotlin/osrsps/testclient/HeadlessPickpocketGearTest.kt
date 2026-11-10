package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.PickpocketGear
import osrsps.game.skill.Pickpocketing
import osrsps.map.CollisionMap

/**
 * Thieving 2 step T1a through the headless client: a farmer (level 10, 14.5 XP, a farmer's pouch of 9 coins or a potato
 * seed), an Al Kharid warrior (a warrior's pouch, 26 XP), and the gloves of silence and dodgy necklace (Check, wear on
 * each failure, the necklace's saved stun, both breaking). The rl-client script test-runner/examples/thieving-t1a.json
 * pickpockets the real farmers, warriors and guards and checks both items there.
 */
class HeadlessPickpocketGearTest {
    private val pick = listOf(null, "Attack", "Pickpocket", null, null)
    private val farmer = NpcType(FARMER, "Farmer", ops = pick, combatLevel = 7, internalName = "farmer1")
    private val warrior = NpcType(Pickpocketing.AL_KHARID_WARRIOR, "Al Kharid warrior", ops = pick, combatLevel = 9, internalName = "al_kharid_warrior")

    private fun server(dir: Path, seed: Long = 11) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Pickpocketing.FARMER_POUCH + 100)
        objs[COINS] = ObjType(COINS, "Coins", stackable = true)
        for (pouch in listOf(Pickpocketing.FARMER_POUCH, Pickpocketing.WARRIOR_POUCH)) {
            objs[pouch] = ObjType(pouch, "Coin pouch", stackable = true, iops = listOf("Open-all", "Open", null, null, "Destroy"))
        }
        objs[Pickpocketing.POTATO_SEED] = ObjType(Pickpocketing.POTATO_SEED, "Potato seed", stackable = true)
        objs[PickpocketGear.GLOVES] = ObjType(PickpocketGear.GLOVES, "Gloves of silence", iops = listOf(null, "Wear", "Check", null, "Drop"))
        objs[PickpocketGear.NECKLACE] = ObjType(PickpocketGear.NECKLACE, "Dodgy necklace", iops = listOf(null, "Wear", "Check", "Break", "Drop"))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(seed),
            npcTypes = NpcTypes.of(farmer, warrior),
            npcSpawns = listOf(NpcSpawn(farmer, 3223, 3218, wander = 0, source = "test"), NpcSpawn(warrior, 3221, 3218, wander = 0, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.npc(id: Int) = await("$id in view") { npcs().firstOrNull { it.id == id } }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.ready(server: TestServer, name: String) {
        val player = server.world.playerNamed(name)!!
        await("the wait to end") { if (server.world.tick >= player.nextPickpocketTick && !server.world.statusEffects.isStunned(player)) Unit else null }
    }

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

    private fun HeadlessClient.level(skill: String, id: Int, level: Int) {
        command("setlevel $skill $level")
        await("$skill $level") { stat(id)?.takeIf { it.level == level } }
    }

    @Test
    fun `a farmer needs level 10, gives a pouch of 9 coins or a potato seed and 14,5 XP, or hits for 1 and stuns`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.level("thieving", THIEVING, 9)
                client.npcOp(client.npc(FARMER).index, Pickpocketing.PICKPOCKET)
                client.await("the level message") { if (client.seen("[pickpocket.level level=10 who=farmer]") == 1) Unit else null }
                client.level("thieving", THIEVING, 10)
                client.level("hitpoints", HITPOINTS, 99)
                val player = server.world.playerNamed("alice")!!
                var won = false
                var lost = false
                var tries = 0
                while (!(won && lost)) {
                    assertTrue(++tries <= 60, "60 tries both work and fail")
                    client.ready(server, "alice")
                    val tenths = player.skills.tenths(Skill.THIEVING)
                    val loot = client.count(Pickpocketing.FARMER_POUCH) + client.count(Pickpocketing.POTATO_SEED)
                    val hp = player.skills.currentLevel(Skill.HITPOINTS)
                    if (client.tryOnce(FARMER, "farmer")) {
                        won = true
                        client.await("a pouch or a seed") {
                            if (client.count(Pickpocketing.FARMER_POUCH) + client.count(Pickpocketing.POTATO_SEED) == loot + 1) Unit else null
                        }
                        assertEquals(tenths + 145, player.skills.tenths(Skill.THIEVING))
                    } else {
                        lost = true
                        client.await("the hit") { if (player.skills.currentLevel(Skill.HITPOINTS) == hp - 1) Unit else null }
                        assertTrue(server.world.statusEffects.isStunned(player))
                    }
                }
                val pouches = client.count(Pickpocketing.FARMER_POUCH)
                assertTrue(pouches > 0, "the pouch is the farmer's usual loot")
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 2, client.slotOf(Pickpocketing.FARMER_POUCH), Pickpocketing.FARMER_POUCH)
                client.await("all opened") { if (client.count(Pickpocketing.FARMER_POUCH) == 0) Unit else null }
                assertEquals(pouches * 9, client.count(COINS))
            }
            // The table: a potato seed 5 times in 128.
            val farmerPocket = Pickpocketing.POCKETS.single { FARMER in it.npcs }
            val seeds = (1..4_000).count { server.world.pickpocketing.pick(farmerPocket, 1).first == Pickpocketing.POTATO_SEED }
            assertTrue(seeds in 100..215, "seeds $seeds of 4000, expected about 156")
        }
    }

    @Test
    fun `an Al Kharid warrior gives a warrior's pouch and 26 XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.level("thieving", THIEVING, 99)
                client.level("hitpoints", HITPOINTS, 99)
                val player = server.world.playerNamed("bob")!!
                var tries = 0
                while (true) {
                    assertTrue(++tries <= 20, "a warrior's pocket within 20 tries at 99")
                    client.ready(server, "bob")
                    val tenths = player.skills.tenths(Skill.THIEVING)
                    if (client.tryOnce(Pickpocketing.AL_KHARID_WARRIOR, "Al Kharid warrior")) {
                        client.await("a warrior's pouch") { if (client.count(Pickpocketing.WARRIOR_POUCH) == 1) Unit else null }
                        assertEquals(tenths + 260, player.skills.tenths(Skill.THIEVING))
                        break
                    }
                }
            }
        }
    }

    @Test
    fun `the gloves of silence wear on each failure, warn one failure before the end, then fall apart`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.level("thieving", THIEVING, 10)
                client.level("hitpoints", HITPOINTS, 99)
                val player = server.world.playerNamed("carol")!!
                // Check, carried: new.
                client.command("item ${PickpocketGear.GLOVES}")
                val slot = client.slotOf(PickpocketGear.GLOVES)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 4, slot, PickpocketGear.GLOVES)
                client.await("new gloves") { if (client.seen("[pickpocket_gear.gloves_new]") == 1) Unit else null }
                player.charges[PickpocketGear.GLOVES_USED] = 40
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 4, slot, PickpocketGear.GLOVES)
                client.await("shabby gloves") { if (client.seen("[pickpocket_gear.gloves_shabby]") == 1) Unit else null }
                // Worn, two failures from the end.
                player.equipment[WearPos.HANDS] = InvObj(PickpocketGear.GLOVES, 1)
                player.charges[PickpocketGear.GLOVES_USED] = PickpocketGear.GLOVES_FAILURES - 2
                failUntil(server, client, "carol") { client.seen("[pickpocket_gear.gloves_warning]") == 1 }
                assertEquals(PickpocketGear.GLOVES_FAILURES - 1, player.charges[PickpocketGear.GLOVES_USED])
                assertEquals(PickpocketGear.GLOVES, player.equipment[WearPos.HANDS]?.id)
                failUntil(server, client, "carol") { client.seen("[pickpocket_gear.gloves_fell_apart]") == 1 }
                client.await("the gloves gone") { if (player.equipment[WearPos.HANDS] == null) Unit else null }
                assertEquals(0, player.charges[PickpocketGear.GLOVES_USED])
            }
        }
    }

    @Test
    fun `a dodgy necklace saves one failure in four from the stun and the blow, and crumbles after ten`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.level("thieving", THIEVING, 10)
                client.level("hitpoints", HITPOINTS, 99)
                val player = server.world.playerNamed("dave")!!
                player.equipment[WearPos.NECK] = InvObj(PickpocketGear.NECKLACE, 1)
                var failures = 0
                var saved = false
                var tries = 0
                while (!saved) {
                    assertTrue(++tries <= 200, "a saved failure within 200 tries")
                    client.ready(server, "dave")
                    val hp = player.skills.currentLevel(Skill.HITPOINTS)
                    if (client.tryOnce(FARMER, "farmer")) continue
                    failures++
                    if (client.await("the stun or the necklace") {
                            when {
                                client.seen("[pickpocket_gear.necklace_protects charges=9]") == 1 -> true
                                server.world.statusEffects.isStunned(player) -> false
                                else -> null
                            }
                        }
                    ) {
                        saved = true
                        assertEquals(hp, player.skills.currentLevel(Skill.HITPOINTS), "no blow")
                        assertTrue(!server.world.statusEffects.isStunned(player), "no stun")
                        assertEquals(1, player.charges[PickpocketGear.NECKLACE_USED])
                        // The next try comes after the usual two ticks, not the failure's eight more.
                        assertTrue(player.nextPickpocketTick <= server.world.tick + Pickpocketing.ATTEMPT_TICKS)
                    }
                }
                assertTrue(failures <= 30, "$failures failures for one save")
                // Check says what is left; the tenth save crumbles it.
                client.command("item ${PickpocketGear.NECKLACE}")
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 4, client.slotOf(PickpocketGear.NECKLACE), PickpocketGear.NECKLACE)
                client.await("the check") { if (client.seen("[pickpocket_gear.necklace_check charges=9]") == 1) Unit else null }
                player.charges[PickpocketGear.NECKLACE_USED] = PickpocketGear.NECKLACE_CHARGES - 1
                failUntil(server, client, "dave", limit = 200) { client.seen("[pickpocket_gear.necklace_crumbles]") == 1 }
                client.await("the necklace gone") { if (player.equipment[WearPos.NECK] == null) Unit else null }
                assertEquals(0, player.charges[PickpocketGear.NECKLACE_USED])
                assertNull(player.equipment[WearPos.NECK])
            }
        }
    }

    /** Pickpockets the farmer until [done]. */
    private fun failUntil(server: TestServer, client: HeadlessClient, name: String, limit: Int = 60, done: () -> Boolean) {
        var tries = 0
        while (!done()) {
            assertTrue(++tries <= limit, "done within $limit tries")
            client.ready(server, name)
            if (!client.tryOnce(FARMER, "farmer")) client.awaitTicks(1)
        }
    }

    private companion object {
        const val FARMER = 3114
        const val COINS = 995
        const val BACKPACK = 93
        const val THIEVING = 17
        const val HITPOINTS = 3
    }
}
