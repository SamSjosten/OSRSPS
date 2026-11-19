package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.Foods
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.combat.Potions
import osrsps.game.content.TaiBwoBroodoo
import osrsps.game.content.TaiBwoCleanup
import osrsps.game.content.TaiBwoWannai
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Karamja step K4.2c through the headless client: a green Broodoo victim forced out of the jungle with `::tbwevent`,
 * a banana doing nothing to it, antipoison doses taken one at a time until one hits it, for Herblore XP, and a player's
 * own hit on it held to 3. test-runner/examples/tai-bwo-wannai-broodoo.json brings one out on the real map.
 */
class HeadlessTaiBwoBroodooTest {
    private val victim = NpcType(
        TaiBwoBroodoo.POISON, "Broodoo victim", ops = listOf(null, "Attack", null, null, null), combatLevel = 60,
        internalName = "tbwcu_broodoo_poison", stats = NpcStats(defence = 26, hitpoints = 100, magic = 60),
    )

    private fun type(id: Int, option: String?) =
        LocType(id, "Loc", List(5) { if (option != null && it == TaiBwoCleanup.HACK - 1) option else null }, LocCollisionType(width = 1, length = 1, interactType = 0))

    private fun obj(id: Int, internal: String, name: String, op: String = "Drink", category: Int = -1) =
        ObjType(id, name, iops = listOf(op, null, null, null, "Drop"), internalName = internal, category = category)

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val npcTypes = NpcTypes.of(victim)
        val objs = arrayOfNulls<ObjType>(MASK + 1)
        objs[MACHETE] = ObjType(MACHETE, "Machete")
        objs[SPAR_LIGHT] = ObjType(SPAR_LIGHT, "Thatch spar light")
        objs[MASK] = ObjType(MASK, "Tribal mask")
        objs[VIAL] = ObjType(VIAL, "Vial")
        objs[ANTIPOISON_4] = obj(ANTIPOISON_4, "4doseantipoison", "Antipoison(4)")
        objs[ANTIPOISON_3] = obj(ANTIPOISON_3, "3doseantipoison", "Antipoison(3)")
        objs[ANTIPOISON_2] = obj(ANTIPOISON_2, "2doseantipoison", "Antipoison(2)")
        objs[ANTIPOISON_1] = obj(ANTIPOISON_1, "1doseantipoison", "Antipoison(1)")
        objs[BANANA] = obj(BANANA, "banana", "Banana", "Eat", category = TaiBwoBroodoo.FRUIT)
        val objTypes = ObjTypes(objs)
        World(
            crcs, CollisionMap().apply { markLoaded(2770 shr 6, 3061 shr 6) }, random = GameRandom(5),
            objTypes = objTypes, npcTypes = npcTypes, npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), npcDrops = NpcDrops.load(npcTypes, objTypes),
            foodList = Foods.load(objTypes), potionData = Potions.load(objTypes),
            locTypes = LocTypes((LIGHT_JUNGLE..LIGHT_JUNGLE + 3).associateWith { type(it, "Hack") } + mapOf(LIGHT_JUNGLE + 4 to type(LIGHT_JUNGLE + 4, null))),
            staticLocs = StaticLocs().apply { add(ZoneLoc(LIGHT_JUNGLE, 10, 0, 2770, 3061, 0)) },
            varTypes = VARS, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(Inventory.INV).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.use(npc: Int, obj: Int) = npcTarget(npc, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(obj), obj)

    @Test
    fun `antipoison hurts the green victim a dose at a time, a banana does nothing, and a player's hit is held to 3`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.command("varp tbw_cleanup 31")
                client.command("setlevel woodcutting 99")
                client.command("god on")
                // Auto retaliate off, so only the potions hit the victim.
                client.command("varp 172 1")
                client.command("item $MACHETE 1")
                client.command("item $BANANA 1")
                client.command("item $ANTIPOISON_4 1")
                client.command("item $ANTIPOISON_4 1")
                client.await("the potions") { if (client.count(ANTIPOISON_4) == 2L) Unit else null }
                client.command("tele 2770 3060 0")
                client.await("by the jungle") { client.position?.takeIf { it == Coord(2770, 3060, 0) } }
                client.commandReply("tbwevent broodoo_poison") { it.endsWith("broodoo_poison") }
                client.locOp(LIGHT_JUNGLE, 2770, 3061, TaiBwoCleanup.HACK)
                val npc = client.await("the victim") { client.npcs().firstOrNull { it.id == victim.id } }.index

                client.use(npc, BANANA)
                client.await("nothing interesting") { client.messages().firstOrNull { it.text == "[game.nothing_interesting]" } }
                assertEquals(1L, client.count(BANANA), "the banana kept")

                val xp = client.stat(Skill.HERBLORE.id)?.xp ?: 0
                var doses = 0
                while (client.npcHits().none { it.index == npc && it.value > 0 } && doses < 8) {
                    val held = client.inventory(Inventory.INV).filterNotNull().filter { it.id in ANTIPOISONS }.sumOf { 4 - ANTIPOISONS.indexOf(it.id) }
                    client.use(npc, client.inventory(Inventory.INV).filterNotNull().first { it.id in ANTIPOISONS }.id)
                    client.await("a dose taken") {
                        val now = client.inventory(Inventory.INV).filterNotNull().filter { it.id in ANTIPOISONS }.sumOf { 4 - ANTIPOISONS.indexOf(it.id) }
                        if (now == held - 1) Unit else null
                    }
                    doses++
                    client.await("the hit") { client.npcHits().count { it.index == npc }.takeIf { it >= doses } }
                }
                val hit = client.npcHits().first { it.index == npc && it.value > 0 }.value
                assertTrue(hit in 1..20, "antipoison's 20 at most, not $hit")
                client.await("Herblore XP, twice the damage") { client.stat(Skill.HERBLORE.id)?.xp?.takeIf { it >= xp + 2 * hit } }

                val world = server.world
                val player = world.onlinePlayers.single()
                val target = world.npcs.inPlay.single { it.type.id == victim.id }
                repeat(50) { assertTrue(world.combat.rolledOn(player, target, AttackType.SLASH, 60) in 0..3) }
                assertEquals(0, world.combat.rolledOn(player, target, AttackType.SLASH, 0))
            }
        }
    }

    private companion object {
        const val LIGHT_JUNGLE = 9010
        const val MACHETE = 975
        const val SPAR_LIGHT = 6281
        const val MASK = 6335
        const val VIAL = 229
        const val ANTIPOISON_4 = 2446
        const val ANTIPOISON_3 = 175
        const val ANTIPOISON_2 = 177
        const val ANTIPOISON_1 = 179
        val ANTIPOISONS = listOf(ANTIPOISON_4, ANTIPOISON_3, ANTIPOISON_2, ANTIPOISON_1)
        const val BANANA = 1963

        val VARS = VarTypes(
            6_000,
            TaiBwoWannai.Villager.entries.withIndex().associate { (i, v) -> v.varbit to VarbitType(534, i * 3, i * 3 + 2) } +
                (TaiBwoWannai.FAVOUR_VARBIT to VarbitType(535, 2, 11)),
        )
    }
}
