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
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.TaiBwoCleanup
import osrsps.game.content.TaiBwoEvents
import osrsps.game.content.TaiBwoWannai
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Karamja step K4.2b through the headless client: `::tbwevent` making the next roll of a hack each event in turn, the
 * gout tuber plant dug with a spade, the gem rock mined for its three gems, and a jungle spider leaping out, killed for
 * its carcass and the favour. test-runner/examples/tai-bwo-wannai-events.json does the same on the real map.
 */
class HeadlessTaiBwoEventsTest {
    private val spider = NpcType(
        TaiBwoEvents.Creature.SPIDER.npc, "Jungle spider", ops = listOf(null, "Attack", null, null, null), combatLevel = 44,
        internalName = "tbwcu_spider_appear", stats = NpcStats(attack = 35, defence = 35, strength = 37, hitpoints = 50),
    )

    private val locs = listOf(
        ZoneLoc(LIGHT_JUNGLE, 10, 0, 2770, 3061, 0),
        ZoneLoc(LIGHT_JUNGLE, 10, 0, 2768, 3061, 0),
        ZoneLoc(LIGHT_JUNGLE, 10, 0, 2766, 3061, 0),
    )

    private fun type(id: Int, op: Int, option: String?) =
        LocType(id, "Loc", List(5) { if (option != null && it == op - 1) option else null }, LocCollisionType(width = 1, length = 1, interactType = 0))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val npcTypes = NpcTypes.of(spider)
        val objTypes = ObjTypes.of(
            ObjType(MACHETE, "Machete"), ObjType(TaiBwoEvents.SPADE, "Spade"), ObjType(RUNE_PICKAXE, "Rune pickaxe"),
            ObjType(TaiBwoEvents.GOUT_TUBER, "Gout tuber"), ObjType(SPAR_LIGHT, "Thatch spar light"), ObjType(CARCASS, "Spider carcass"),
            *GEMS.map { ObjType(it, "Uncut gem") }.toTypedArray(),
        )
        World(
            crcs, CollisionMap().apply { markLoaded(2770 shr 6, 3061 shr 6) }, random = GameRandom(11),
            objTypes = objTypes, npcTypes = npcTypes, npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), npcDrops = NpcDrops.load(npcTypes, objTypes),
            locTypes = LocTypes(
                (LIGHT_JUNGLE..LIGHT_JUNGLE + 3).associateWith { type(it, TaiBwoCleanup.HACK, "Hack") } +
                    mapOf(LIGHT_JUNGLE + 4 to type(LIGHT_JUNGLE + 4, 1, null), TaiBwoEvents.TUBER_PLANT to type(TaiBwoEvents.TUBER_PLANT, 1, null)) +
                    TaiBwoEvents.GEM_ROCKS.associateWith { type(it, 1, "Mine") },
            ),
            staticLocs = StaticLocs().apply { locs.forEach(::add) },
            varTypes = VARS, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(Inventory.INV).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("at $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.message(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.shown(id: Int, x: Int, z: Int) =
        await("loc $id at $x, $z") { locChanges().lastOrNull { it.coord == Coord(x, z, 0) }?.takeIf { it.id == id } }

    private fun HeadlessClient.favour() = varp(FAVOUR_VARP) shr 2 and 1023

    @Test
    fun `forced events give the tuber, the gem rock's three gems and a spider worth favour`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                // Met Murcaily, hacked, repaired and had his first sticks.
                client.command("varp tbw_cleanup 31")
                client.command("setlevel woodcutting 99")
                client.command("setlevel mining 99")
                client.command("item $MACHETE 1")
                client.command("item ${TaiBwoEvents.SPADE} 1")
                client.command("item $RUNE_PICKAXE 1")
                client.await("the tools") { if (client.count(RUNE_PICKAXE) == 1L) Unit else null }

                client.tele(2770, 3060)
                client.commandReply("tbwevent tuber") { it.endsWith("tuber") }
                client.locOp(LIGHT_JUNGLE, 2770, 3061, TaiBwoCleanup.HACK)
                client.message("[tbw_event.tuber]")
                client.shown(TaiBwoEvents.TUBER_PLANT, 2770, 3061)
                client.locTarget(TaiBwoEvents.TUBER_PLANT, 2770, 3061, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(TaiBwoEvents.SPADE), TaiBwoEvents.SPADE)
                client.await("the gout tuber") { if (client.count(TaiBwoEvents.GOUT_TUBER) == 1L) Unit else null }
                client.shown(LIGHT_JUNGLE + 4, 2770, 3061)

                client.tele(2768, 3060)
                client.commandReply("tbwevent gems") { it.endsWith("gems") }
                client.locOp(LIGHT_JUNGLE, 2768, 3061, TaiBwoCleanup.HACK)
                client.message("[tbw_event.gems]")
                client.shown(TaiBwoEvents.GEM_ROCKS[0], 2768, 3061)
                for (stage in 1..3) {
                    val id = TaiBwoEvents.GEM_ROCKS[stage - 1]
                    client.locOp(id, 2768, 3061, 1)
                    client.await("gem $stage", timeoutMillis = 30_000) { if (GEMS.sumOf { client.count(it) } == stage.toLong()) Unit else null }
                }
                client.shown(LIGHT_JUNGLE + 4, 2768, 3061)

                client.command("god on")
                client.tele(2766, 3060)
                client.commandReply("tbwevent spider") { it.endsWith("spider") }
                client.locOp(LIGHT_JUNGLE, 2766, 3061, TaiBwoCleanup.HACK)
                client.message("[tbw_event.monster]")
                val npc = client.await("the spider") { client.npcs().firstOrNull { it.id == spider.id } }.index
                assertEquals(0, client.favour())
                client.commandReply("kill $npc") { it.startsWith("Killed") }
                client.await("its carcass", timeoutMillis = 20_000) { client.groundObjs().firstOrNull { it.id == CARCASS } }
                val favour = client.await("the spider's favour") { client.favour().takeIf { it > 0 } }
                assertTrue(favour in 10..20, "1 to 2%, not $favour tenths")
            }
        }
    }

    private companion object {
        const val LIGHT_JUNGLE = 9010
        const val MACHETE = 975
        const val RUNE_PICKAXE = 1275
        const val SPAR_LIGHT = 6281
        const val CARCASS = 6291
        val GEMS = listOf(1625, 1627, 1629, 1623, 1621, 1619, 1617)
        const val FAVOUR_VARP = 535

        /** As HeadlessTaiBwoWannaiTest's: the villagers' chat varbits on varp 534 and favour on 535. */
        val VARS = VarTypes(
            6_000,
            TaiBwoWannai.Villager.entries.withIndex().associate { (i, v) -> v.varbit to VarbitType(534, i * 3, i * 3 + 2) } +
                (TaiBwoWannai.FAVOUR_VARBIT to VarbitType(FAVOUR_VARP, 2, 11)),
        )
    }
}
