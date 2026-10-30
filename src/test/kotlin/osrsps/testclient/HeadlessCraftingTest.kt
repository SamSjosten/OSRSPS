package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Crafting
import osrsps.game.skill.CraftingData
import osrsps.game.skill.Smithing
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Artisan step 3 onwards through the headless client: Crafting's spinning wheel. The rl-client script
 * test-runner/examples/crafting-spinning.json spins on Lumbridge Castle's wheel.
 */
class HeadlessCraftingTest {
    private val data = CraftingData.load()

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val recipes = data.spinning + data.leather.values.flatten() + data.studded.values + data.gems.values +
            data.jewellery.flatMap { it.items.values } + data.amulets.values
        val ids = recipes.flatMap { r -> r.inputs.map { it.obj } + r.product + r.tools + listOfNotNull(r.wears?.obj, r.chance?.failed) } + data.studs
        val objs = arrayOfNulls<ObjType>(ids.max() + 1)
        for (id in ids) objs[id] = ObjType(id, "Item $id", tradeable = true)
        objs[WOOL] = ObjType(WOOL, "Wool", tradeable = true)
        objs[BALL_OF_WOOL] = ObjType(BALL_OF_WOOL, "Ball of wool", tradeable = true)
        objs[BOW_STRING] = ObjType(BOW_STRING, "Bow string", tradeable = true)
        objs[THREAD] = ObjType(THREAD, "Thread", stackable = true, tradeable = true)
        objs[STUDS] = ObjType(STUDS, "Steel studs", tradeable = true)
        objs[GLOVES] = ObjType(GLOVES, "Leather gloves", tradeable = true)
        objs[LEATHER_BODY] = ObjType(LEATHER_BODY, "Leather body", tradeable = true)
        objs[STUDDED_BODY] = ObjType(STUDDED_BODY, "Studded body", tradeable = true)
        objs[OPAL] = ObjType(OPAL, "Opal", tradeable = true)
        objs[SAPPHIRE] = ObjType(SAPPHIRE, "Sapphire", tradeable = true)
        objs[TIARA] = ObjType(TIARA, "Tiara", tradeable = true)
        objs[SAPPHIRE_RING] = ObjType(SAPPHIRE_RING, "Sapphire ring", tradeable = true)
        objs[STRUNG_GOLD] = ObjType(STRUNG_GOLD, "Gold amulet", tradeable = true)
        objs[UNSTRUNG_GOLD] = ObjType(UNSTRUNG_GOLD, "Gold amulet (u)", tradeable = true)
        objs[GREEN_BODY] = ObjType(GREEN_BODY, "Green d'hide body", tradeable = true)
        val spin = listOf(null, Crafting.SPIN, null, null, null)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(5),
            locTypes = LocTypes(
                mapOf(
                    Crafting.LUMBRIDGE_WHEEL to LocType(Crafting.LUMBRIDGE_WHEEL, "Spinning wheel", spin, LocCollisionType(width = 1, length = 1)),
                    Smithing.FURNACE to LocType(Smithing.FURNACE, "Furnace", listOf(null, "Smelt", null, null, null), LocCollisionType(width = 1, length = 1)),
                ),
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Crafting.LUMBRIDGE_WHEEL, LocShape.CENTREPIECE_STRAIGHT, 0, WHEEL_X, WHEEL_Z, 0))
                add(ZoneLoc(Smithing.FURNACE, LocShape.CENTREPIECE_STRAIGHT, 0, FURNACE_X, FURNACE_Z, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.spun() = messages().filter { it.text.startsWith("[crafting.spin") }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.box() = await("the Make-X box") { clientScripts().lastOrNull { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } }

    @Test
    fun `Spin with nothing to spin does nothing, then offers wool and flax and spins three balls 3 ticks apart`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel crafting 99")
                client.locOp(Crafting.LUMBRIDGE_WHEEL, WHEEL_X, WHEEL_Z, 2)
                client.seen("[game.nothing_interesting]")
                client.give(WOOL, 3)
                client.give(FLAX, 1)
                client.locOp(Crafting.LUMBRIDGE_WHEEL, WHEEL_X, WHEEL_Z, 2)
                val setup = client.box()
                assertTrue(BALL_OF_WOOL in setup.args && BOW_STRING in setup.args, "both: ${setup.args}")
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 3)
                val balls = client.await("three balls", timeoutMillis = 20_000) { client.spun().takeIf { it.size >= 3 } }
                assertEquals("[crafting.spin item=ball of wool]", balls[0].text)
                assertEquals(listOf(3, 3), balls.zipWithNext { a, b -> b.tick - a.tick })
                client.await("three balls held") { if (client.count(BALL_OF_WOOL) == 3) Unit else null }
                assertEquals(1, client.count(FLAX))
                assertEquals(7, client.await("Crafting XP") { client.stat(CRAFTING)?.takeIf { it.xp > 13_034_431 } }.xp - 13_034_431)
            }
        }
    }

    @Test
    fun `flax used on the wheel at level 1 needs 10`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(FLAX, 2)
                client.locTarget(Crafting.LUMBRIDGE_WHEEL, WHEEL_X, WHEEL_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(FLAX), FLAX)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                client.seen("[crafting.level level=10 item=bow string]")
                client.awaitTicks(6)
                assertEquals(2, client.count(FLAX))
            }
        }
    }

    private fun HeadlessClient.xpOver99() = (stat(CRAFTING)?.xp ?: 0) - 13_034_431

    private fun HeadlessClient.use(id: Int, target: Int) =
        ifButtonT(InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(id), id, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(target), target)

    private fun HeadlessClient.made() = messages().filter { it.text.startsWith("[crafting.leather") || it.text.startsWith("[crafting.studs") }

    @Test
    fun `a needle on leather offers all seven items, and one thread lasts five pairs of gloves`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel crafting 99")
                client.give(NEEDLE)
                client.give(THREAD)
                client.give(LEATHER, 6)
                client.use(NEEDLE, LEATHER)
                val setup = client.box()
                assertEquals(7, data.leather.getValue(LEATHER).count { it.product in setup.args }, "the box: ${setup.args}")
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 6)
                client.seen("[crafting.thread_used]")
                client.awaitTicks(8)
                val gloves = client.made()
                assertEquals(5, gloves.size, "the thread ran out after five")
                assertEquals("[crafting.leather item=leather gloves]", gloves[0].text)
                assertEquals(listOf(3, 3, 3, 3), gloves.zipWithNext { a, b -> b.tick - a.tick })
                assertEquals(5, client.count(GLOVES))
                assertEquals(1, client.count(LEATHER))
                assertEquals(0, client.count(THREAD))
                assertEquals(69, client.await("69 XP") { client.xpOver99().takeIf { it >= 69 } })
            }
        }
    }

    @Test
    fun `thread uses carry over between jobs, and a green d'hide body takes three leathers`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel crafting 99")
                client.give(NEEDLE)
                client.give(THREAD)
                client.give(GREEN_LEATHER, 9)
                for (round in 1..2) {
                    client.use(NEEDLE, GREEN_LEATHER)
                    client.box()
                    client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + 2, 1)
                    client.await("body $round") { if (client.count(GREEN_BODY) == round) Unit else null }
                }
                assertEquals(3, client.count(GREEN_LEATHER))
                assertEquals(372, client.await("372 XP") { client.xpOver99().takeIf { it >= 372 } })
                // Two uses so far: three more use the thread up.
                assertEquals(1, client.count(THREAD))
                assertTrue(client.messages().none { it.text == "[crafting.thread_used]" })
            }
        }
    }

    @Test
    fun `steel studs on a leather body stud it, and need level 41`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(STUDS)
                client.give(LEATHER_BODY)
                client.use(STUDS, LEATHER_BODY)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.seen("[crafting.level level=41 item=studded body]")
                client.command("setlevel crafting 99")
                client.await("level 99") { client.stat(CRAFTING)?.takeIf { it.level == 99 } }
                client.use(STUDS, LEATHER_BODY)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.seen("[crafting.studs item=studded body]")
                client.await("the studded body") { if (client.count(STUDDED_BODY) == 1) Unit else null }
                assertEquals(0, client.count(STUDS))
                assertEquals(40, client.await("40 XP") { client.xpOver99().takeIf { it >= 40 } })
            }
        }
    }

    @Test
    fun `a chisel on uncut opals at level 1 cuts some and crushes some, 2 ticks apart, and a sapphire needs 20`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fred").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(CHISEL)
                client.give(UNCUT_SAPPHIRE)
                client.use(CHISEL, UNCUT_SAPPHIRE)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.seen("[crafting.level level=20 item=sapphire]")
                // Five: even all cut (75 XP) stays short of level 2, whose box would end the job.
                client.give(UNCUT_OPAL, 5)
                client.use(UNCUT_OPAL, CHISEL)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 5)
                val cuts = client.await("five cut", timeoutMillis = 20_000) {
                    client.messages().filter { it.text == "[crafting.cut item=opal]" || it.text == "[crafting.crushed item=opal]" }.takeIf { it.size >= 5 }
                }
                assertTrue(cuts.zipWithNext { a, b -> b.tick - a.tick }.all { it == 2 }, "2 ticks a gem")
                val opals = client.await("the opals") { client.count(OPAL).takeIf { it + client.count(CRUSHED) == 5 } }
                val crushed = client.count(CRUSHED)
                assertTrue(opals > 0 && crushed > 0, "about half crush at level 1: $opals cut, $crushed crushed")
                assertEquals(0, client.count(UNCUT_OPAL))
                val xpTenths = opals * 150 + crushed * 38
                assertEquals(xpTenths / 10, client.await("the XP") { client.stat(CRAFTING)?.xp?.takeIf { it >= xpTenths / 10 } })
            }
        }
    }

    @Test
    fun `a gold bar on a furnace opens the jewellery interface, and All makes two sapphire rings 3 ticks apart`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gina").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(GOLD_BAR, 2)
                client.give(RING_MOULD)
                client.give(AMULET_MOULD)
                client.give(SAPPHIRE, 2)
                client.locTarget(Smithing.FURNACE, FURNACE_X, FURNACE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(GOLD_BAR), GOLD_BAR)
                client.await("crafting_gold") { if (Crafting.JEWELLERY_INTERFACE in client.openInterfaces()) Unit else null }
                // Level 1: a gold amulet needs 8.
                client.ifButton(Crafting.JEWELLERY_INTERFACE, GOLD_AMULET_BUTTON, 1)
                client.seen("[crafting.level level=8 item=gold amulet (u)]")
                client.command("setlevel crafting 99")
                client.await("level 99") { client.stat(CRAFTING)?.takeIf { it.level == 99 } }
                client.locTarget(Smithing.FURNACE, FURNACE_X, FURNACE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(RING_MOULD), RING_MOULD)
                client.await("crafting_gold again") { if (Crafting.JEWELLERY_INTERFACE in client.openInterfaces()) Unit else null }
                client.ifButton(Crafting.JEWELLERY_INTERFACE, MAKE_ALL, 1)
                client.await("All") { if (client.varp(Smithing.VARP_QUANTITY) == Smithing.MAX_QUANTITY) Unit else null }
                client.ifButton(Crafting.JEWELLERY_INTERFACE, SAPPHIRE_RING_BUTTON, 1)
                val rings = client.await("two rings") { client.messages().filter { it.text == "[crafting.jewellery item=sapphire ring]" }.takeIf { it.size >= 2 } }
                assertEquals(3, rings[1].tick - rings[0].tick)
                assertTrue(Crafting.JEWELLERY_INTERFACE !in client.openInterfaces(), "the interface closes as the making starts")
                client.await("the rings") { if (client.count(SAPPHIRE_RING) == 2) Unit else null }
                assertEquals(0, client.count(GOLD_BAR))
                assertEquals(0, client.count(SAPPHIRE))
                assertEquals(1, client.count(RING_MOULD))
                assertEquals(80, client.await("80 XP") { client.xpOver99().takeIf { it >= 80 } })
            }
        }
    }

    @Test
    fun `a tiara mould on a furnace opens the silver interface, which makes a tiara for 52,5 XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ivy").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel crafting 99")
                client.give(SILVER_BAR)
                client.give(TIARA_MOULD)
                client.locTarget(Smithing.FURNACE, FURNACE_X, FURNACE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(TIARA_MOULD), TIARA_MOULD)
                client.await("silver_crafting") { if (Crafting.SILVER_INTERFACE in client.openInterfaces()) Unit else null }
                client.ifButton(Crafting.SILVER_INTERFACE, TIARA_BUTTON, 1)
                client.seen("[crafting.jewellery item=tiara]")
                client.await("the tiara") { if (client.count(TIARA) == 1) Unit else null }
                assertEquals(0, client.count(SILVER_BAR))
                assertEquals(52, client.await("52 XP") { client.xpOver99().takeIf { it >= 52 } })
            }
        }
    }

    @Test
    fun `a ball of wool strings an amulet for 4 XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("hank").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(UNSTRUNG_GOLD)
                client.give(BALL_OF_WOOL)
                client.use(BALL_OF_WOOL, UNSTRUNG_GOLD)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.seen("[crafting.string_amulet item=gold amulet]")
                client.await("the amulet") { if (client.count(STRUNG_GOLD) == 1) Unit else null }
                assertEquals(4, client.await("4 XP") { client.stat(CRAFTING)?.xp?.takeIf { it >= 4 } })
            }
        }
    }

    private companion object {
        const val SILVER_BAR = 2355
        const val TIARA_MOULD = 5523
        const val TIARA = 5525
        const val TIARA_BUTTON = 28
        const val FURNACE_X = 3220
        const val FURNACE_Z = 3218
        const val GOLD_BAR = 2357
        const val RING_MOULD = 1592
        const val AMULET_MOULD = 1595
        const val SAPPHIRE_RING = 1637
        const val UNSTRUNG_GOLD = 1673
        const val STRUNG_GOLD = 1692
        const val SAPPHIRE_RING_BUTTON = 9
        const val GOLD_AMULET_BUTTON = 37
        const val MAKE_ALL = 64
        const val CHISEL = 1755
        const val UNCUT_OPAL = 1625
        const val OPAL = 1609
        const val CRUSHED = 1633
        const val UNCUT_SAPPHIRE = 1623
        const val SAPPHIRE = 1607
        const val NEEDLE = 1733
        const val THREAD = 1734
        const val LEATHER = 1741
        const val GLOVES = 1059
        const val LEATHER_BODY = 1129
        const val STUDS = 2370
        const val STUDDED_BODY = 1133
        const val GREEN_LEATHER = 1745
        const val GREEN_BODY = 1135
        const val WHEEL_X = 3224
        const val WHEEL_Z = 3218
        const val WOOL = 1737
        const val BALL_OF_WOOL = 1759
        const val FLAX = 1779
        const val BOW_STRING = 1777
        const val BACKPACK = 93
        const val CRAFTING = 12
    }
}
