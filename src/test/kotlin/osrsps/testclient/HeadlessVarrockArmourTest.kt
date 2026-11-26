package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.VarrockArmour
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Mining
import osrsps.game.skill.Smithing
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Varrock armour worn, through the headless client: a second copper ore now and then with its XP, and never with one
 * free backpack slot; a second bronze bar now and then at the Edgeville furnace, from the ores for it, and never when
 * the ores are only enough for one. [VarrockArmourTest] covers the tiers' levels and the furnace's place.
 */
class HeadlessVarrockArmourTest {
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val ids = Smithing.BARS.flatMap { bar -> bar.ores.map { it.first } + bar.obj } + listOf(RUNE_PICKAXE, POT, RING_OF_FORGING) + VarrockArmour.PIECES
        val objs = arrayOfNulls<ObjType>(ids.max() + 1)
        for (id in ids) objs[id] = ObjType(id, "Obj $id", internalName = if (id == RING_OF_FORGING) "ring_of_forging" else null)
        World(
            crcs,
            CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(FURNACE_X shr 6, FURNACE_Z shr 6) },
            objTypes = ObjTypes(objs), random = GameRandom(11),
            locTypes = LocTypes(
                listOf(
                    LocType(COPPER_ROCK, "Copper rocks", listOf("Mine", null, null, null, null), LocCollisionType(width = 1, length = 1)),
                    LocType(Smithing.FURNACE, "Furnace", listOf(null, "Smelt", null, null, null), LocCollisionType(width = 1, length = 1)),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(COPPER_ROCK, LocShape.CENTREPIECE_STRAIGHT, 0, ROCK_X, ROCK_Z, 0))
                add(ZoneLoc(Smithing.FURNACE, LocShape.CENTREPIECE_STRAIGHT, 0, FURNACE_X, FURNACE_Z, 0))
            },
            varTypes = VARS,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).count { it?.id == id }

    private fun HeadlessClient.used() = inventory(BACKPACK).count { it != null }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    private fun HeadlessClient.give(id: Int, count: Int) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.emptyPack() {
        command("empty")
        await("an empty backpack") { if (used() == 0) Unit else null }
    }

    /** Mines the copper rock once, after it's back, and gives how many ores that swing gave. */
    private fun HeadlessClient.mineOnce(): Int {
        await("the copper rock", timeoutMillis = 10_000) { if (locAt(ROCK_X, ROCK_Z).let { it == null || it == COPPER_ROCK }) Unit else null }
        val before = count(COPPER_ORE)
        val swings = messages().count { it.text == "[mining.get_ore ore=copper]" }
        locOp(COPPER_ROCK, ROCK_X, ROCK_Z, 1)
        await("the ore", timeoutMillis = 10_000) { if (messages().count { it.text == "[mining.get_ore ore=copper]" } > swings) Unit else null }
        // The empty rock is sent with the ore: once it shows, the backpack's packet has come too.
        await("the empty rock") { if (locAt(ROCK_X, ROCK_Z) == Mining.ROCKS1) Unit else null }
        return count(COPPER_ORE) - before
    }

    private fun wear(server: TestServer, name: String, armour: Int) {
        val player = server.world.playerNamed(name)!!
        player.equipment[WearPos.BODY] = InvObj(armour, 1)
        player.equipment[WearPos.WEAPON] = InvObj(RUNE_PICKAXE, 1)
    }

    @Test
    fun `Varrock armour 1 mines a second copper ore with its XP now and then, never with one slot free`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel mining 99")
                val base = client.await("level 99") { client.stat(MINING)?.takeIf { it.level == 99 } }.xp
                wear(server, "alice", VarrockArmour.PIECES[0])
                var ores = 0
                var doubles = 0
                repeat(MINES) {
                    if (client.used() > 25) client.emptyPack()
                    val got = client.mineOnce()
                    assertTrue(got in 1..2, "$got ores from one swing")
                    ores += got
                    if (got == 2) doubles++
                }
                assertTrue(doubles in 1 until MINES, "$doubles doubles in $MINES swings")
                // 17.5 XP an ore, the second's too: the stat packet carries whole XP.
                val xp = base + ores * 175 / 10
                assertEquals(xp, client.await("Mining XP") { client.stat(MINING)?.takeIf { it.xp >= xp } }.xp)

                // The near miss: one slot free, so only ever the one ore, however many swings.
                repeat(MINES) {
                    client.emptyPack()
                    client.give(POT, 27)
                    assertEquals(1, client.mineOnce())
                    assertEquals(28, client.used())
                }
            }
        }
    }

    @Test
    fun `Varrock armour 4 smelts a second bronze bar from its ores at Edgeville, never without them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele ${FURNACE_X - 1} $FURNACE_Z 0")
                client.await("by the furnace") { client.position?.takeIf { it.x == FURNACE_X - 1 && it.z == FURNACE_Z } }
                wear(server, "bob", VarrockArmour.PIECES[3])
                client.give(COPPER, 14)
                client.give(TIN, 14)
                client.locOp(Smithing.FURNACE, FURNACE_X, FURNACE_Z, 2)
                client.await("the Make-X box") { client.clientScripts().firstOrNull { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } }
                client.resumePause(Chatbox.SKILLMULTI, barButton(Smithing.BRONZE), 14)
                client.await("every ore smelted", timeoutMillis = 60_000) { if (client.count(COPPER) + client.count(TIN) == 0) Unit else null }
                client.awaitTicks(2)
                val bars = client.count(Smithing.BRONZE.obj)
                val smelts = client.messages().count { it.text == "[smithing.smelted bar=bronze]" }
                assertEquals(14, bars, "a bar for each copper and tin")
                assertTrue(smelts < bars, "$smelts smelts gave $bars bars: no second bar")
                // 6.2 XP a bar, the second's too.
                assertEquals(bars * 62 / 10, client.await("Smithing XP") { client.stat(SMITHING)?.takeIf { it.xp >= bars * 62 / 10 } }.xp)

                // The near miss: ores for one bar only, so only ever the one.
                repeat(SMELTS) {
                    client.emptyPack()
                    client.give(COPPER, 1)
                    client.give(TIN, 1)
                    val boxes = client.clientScripts().count { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }
                    client.locOp(Smithing.FURNACE, FURNACE_X, FURNACE_Z, 2)
                    client.await("the Make-X box") { Unit.takeIf { client.clientScripts().count { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } > boxes } }
                    client.resumePause(Chatbox.SKILLMULTI, barButton(Smithing.BRONZE), 1)
                    client.await("the bar", timeoutMillis = 10_000) { if (client.count(Smithing.BRONZE.obj) == 1) Unit else null }
                    client.awaitTicks(2)
                    assertEquals(1, client.count(Smithing.BRONZE.obj))
                    assertEquals(0, client.count(COPPER) + client.count(TIN))
                }
            }
        }
    }

    @Test
    fun `a second iron bar spends a ring of forging use as the first does`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel smithing 15")
                client.await("level 15") { client.stat(SMITHING)?.takeIf { it.level == 15 } }
                client.command("tele ${FURNACE_X - 1} $FURNACE_Z 0")
                client.await("by the furnace") { client.position?.takeIf { it.x == FURNACE_X - 1 && it.z == FURNACE_Z } }
                wear(server, "carol", VarrockArmour.PIECES[3])
                val carol = server.world.playerNamed("carol")!!
                carol.equipment[WearPos.RING] = InvObj(RING_OF_FORGING, 1)
                client.give(IRON_ORE, 20)
                client.locOp(Smithing.FURNACE, FURNACE_X, FURNACE_Z, 2)
                client.await("the Make-X box") { client.clientScripts().firstOrNull { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } }
                client.resumePause(Chatbox.SKILLMULTI, barButton(Smithing.IRON), 20)
                client.await("every ore smelted", timeoutMillis = 60_000) { if (client.count(IRON_ORE) == 0) Unit else null }
                client.awaitTicks(2)
                val smelts = client.messages().count { it.text == "[smithing.smelted bar=iron]" }
                // With the ring every ore is a bar, the armour's second ones too, and every bar a use.
                assertEquals(20, client.count(Smithing.IRON.obj))
                assertTrue(smelts < 20, "$smelts smelts gave 20 bars: no second bar")
                assertEquals(0, client.messages().count { it.text == "[smithing.impure]" })
                assertEquals(20, carol.charges["jewel_forging"])
            }
        }
    }

    /** The Make-X box's button for [bar]. */
    private fun barButton(bar: osrsps.game.skill.Bar) = Chatbox.SKILLMULTI_FIRST_ITEM + Smithing.BARS.indexOf(bar)

    private companion object {
        const val MINES = 30
        const val SMELTS = 20
        const val ROCK_X = 3223
        const val ROCK_Z = 3218
        /** Inside the Edgeville furnace's building ([VarrockArmour]). */
        const val FURNACE_X = 3108
        const val FURNACE_Z = 3499
        const val COPPER_ROCK = 10943
        const val COPPER_ORE = 436
        const val COPPER = 436
        const val TIN = 438
        const val IRON_ORE = 440
        const val RING_OF_FORGING = 2568
        const val RUNE_PICKAXE = 1275
        const val POT = 1931
        const val BACKPACK = 93
        const val MINING = 14
        const val SMITHING = 13
        val VARS = VarTypes(
            6_000,
            mapOf(
                Smithing.VARBIT_BAR_TYPE to VarbitType(210, 0, 2),
                Smithing.VARBIT_LAST_BAR to VarbitType(549, 24, 26),
                Smithing.VARBIT_LAST_ITEM to VarbitType(549, 27, 31),
            ),
        )
    }
}
