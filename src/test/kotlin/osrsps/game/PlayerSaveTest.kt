package osrsps.game

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.combat.SpecialAttacks
import osrsps.map.CollisionMap
import osrsps.map.Tile

class PlayerSaveTest {
    private val saves = MemorySaveStore()
    private val world = World(IntArray(0), CollisionMap(), saves = saves)

    @Test
    fun `a saved player loads back where they were with the same state`() {
        val player = world.loadPlayer(1, "Test")
        player.x = 3210
        player.z = 3270
        player.level = 1
        player.runEnergy = 4_321
        player.runEnabled = true
        player.vars[1_000] = -5
        player.skills.setLevel(Skill.ATTACK, 99)
        player.skills.addTenths(Skill.COOKING, 175)
        player.skills.setCurrentLevel(Skill.STRENGTH, 5)
        player.inventory[0] = InvObj(1351, 1)
        player.inventory[27] = InvObj(995, 1_000_000)
        player.equipment[WearPos.WEAPON] = InvObj(1351, 1)
        player.equipment[WearPos.AMMO] = InvObj(882, 500)
        assertTrue(world.save(player))

        val loaded = world.loadPlayer(2, "test")
        assertEquals(listOf(3210, 3270, 1), listOf(loaded.x, loaded.z, loaded.level))
        assertEquals(listOf(3210 shr 3, 3270 shr 3), listOf(loaded.buildAreaZoneX, loaded.buildAreaZoneZ))
        assertEquals(4_321, loaded.runEnergy)
        assertTrue(loaded.runEnabled)
        assertEquals(-5, loaded.vars[1_000])
        assertEquals(99, loaded.skills.baseLevel(Skill.ATTACK))
        assertEquals(175, loaded.skills.tenths(Skill.COOKING))
        assertEquals(5, loaded.skills.currentLevel(Skill.STRENGTH))
        assertEquals(10, loaded.skills.currentLevel(Skill.HITPOINTS))
        assertEquals(player.skills.combatLevel, loaded.skills.combatLevel)
        assertEquals(listOf(0 to InvObj(1351, 1), 27 to InvObj(995, 1_000_000)), loaded.inventory.contents())
        assertEquals(listOf(WearPos.WEAPON to InvObj(1351, 1), WearPos.AMMO to InvObj(882, 500)), loaded.equipment.contents())
    }

    @Test
    fun `boost cycles keep their segments, and saves from before them start the cycles at the beginning`() {
        val player = world.loadPlayer(1, "Test")
        player.statTimers.combatBoost.ticks = 59
        player.statTimers.skillBoost.ticks = 85
        player.statTimers.hitpoints.ticks = 70
        assertTrue(world.save(player))
        // Loaded between ticks, as on a paused clock: no tick is to count them on.
        val loaded = world.loadPlayer(2, "test")
        assertEquals(40, loaded.statTimers.combatBoost.ticks)
        assertEquals(80, loaded.statTimers.skillBoost.ticks)
        assertEquals(0, loaded.statTimers.hitpoints.ticks, "logging in starts Hitpoints' cycle again")
        // In a tick's input, one short, as the login tick counts them on; see BoostCycle.restart.
        val ticking = Player(3, "ticking", 3222, 3218, 0)
        PlayerSave.of(player).applyTo(ticking, inTick = true)
        assertEquals(listOf(39, 79, -1), listOf(ticking.statTimers.combatBoost.ticks, ticking.statTimers.skillBoost.ticks, ticking.statTimers.hitpoints.ticks))
        val text = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap(), boostSegments = PlayerSave.BoostSegments(3, 2)).toJson("old")
        val stripped = text.replace(Regex("""\s*"boostSegments": \{[^}]*},"""), "")
        assertTrue("boostSegments" !in stripped)
        assertEquals(listOf(0, 0), PlayerSave.fromJson(stripped).boostSegments.let { listOf(it.combat, it.other) })
        assertEquals(listOf(3, 2), PlayerSave.fromJson(text).boostSegments.let { listOf(it.combat, it.other) })
        assertFailsWith<IllegalArgumentException> {
            PlayerSave.fromJson(PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap(), boostSegments = PlayerSave.BoostSegments(5, 0)).toJson("bad"))
        }
    }

    @Test
    fun `account age and time played are kept, and saves from before them still load`() {
        val player = world.loadPlayer(1, "Test")
        player.createdAt = 1_700_000_000_000
        player.playedTicks = 123_456
        assertTrue(world.save(player))
        val loaded = world.loadPlayer(2, "test")
        assertEquals(1_700_000_000_000, loaded.createdAt)
        assertEquals(123_456, loaded.playedTicks)

        val old = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap()).toJson("old")
        saves.write("old", old.replace(Regex("\\s*\"playedTicks\": 0,"), ""))
        assertTrue("playedTicks" !in saves.saves.getValue("old"))
        val before = System.currentTimeMillis()
        val fromOld = world.loadPlayer(3, "old")
        assertEquals(0, fromOld.playedTicks)
        assertTrue(fromOld.createdAt >= before)
    }

    @Test
    fun `charges are saved, and a save without them loads with none`() {
        val text = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap(), charges = mapOf("blowpipe_scales" to 500)).toJson("test")
        assertEquals(mapOf("blowpipe_scales" to 500), PlayerSave.fromJson(text).charges)
        saves.write("test", text)
        assertEquals(500, world.loadPlayer(1, "test").charges["blowpipe_scales"])
        val none = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap()).toJson("test")
        assertTrue("charges" !in none)
        assertTrue(PlayerSave.fromJson(none).charges.isEmpty())
        assertFailsWith<IllegalArgumentException> { PlayerSave.fromJson(text.replace("500", "-1")) }
    }

    @Test
    fun `chat filters are saved, and a save without them loads with every filter on`() {
        val text = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap(), chatFilters = ChatFilters(2, 1, 3)).toJson("test")
        assertEquals(ChatFilters(2, 1, 3), PlayerSave.fromJson(text).chatFilters)
        saves.write("test", text)
        assertEquals(ChatFilters(2, 1, 3), world.loadPlayer(1, "test").chatFilters)
        val none = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap()).toJson("test")
        assertTrue("chatFilters" !in none)
        assertEquals(ChatFilters.DEFAULT, PlayerSave.fromJson(none).chatFilters)
        assertFailsWith<IllegalArgumentException> { PlayerSave.fromJson(text.replace("\"trade\": 3", "\"trade\": 256")) }
    }

    @Test
    fun `the friends list is saved in order, kept over a reset, and a save without one loads empty`() {
        val text = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap(), friends = listOf("Zezima", "bob")).toJson("test")
        assertEquals(listOf("Zezima", "bob"), PlayerSave.fromJson(text).friends)
        saves.write("test", text)
        val player = world.loadPlayer(1, "test")
        assertEquals(listOf("Zezima", "bob"), player.friends)
        // ::reset puts a save's state back but not its header, the friends list included.
        PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap()).restoreTo(player)
        assertEquals(listOf("Zezima", "bob"), player.friends)
        val none = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap()).toJson("test")
        assertTrue("friends" !in none)
        assertTrue(PlayerSave.fromJson(none).friends.isEmpty())
        val tooMany = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap(), friends = List(401) { "p$it" }).toJson("test")
        assertFailsWith<IllegalArgumentException> { PlayerSave.fromJson(tooMany) }
    }

    @Test
    fun `the ignore list is saved in order, and a save without one loads empty`() {
        val text = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap(), ignores = listOf("Zezima", "bob")).toJson("test")
        assertEquals(listOf("Zezima", "bob"), PlayerSave.fromJson(text).ignores)
        saves.write("test", text)
        assertEquals(listOf("Zezima", "bob"), world.loadPlayer(1, "test").ignores)
        val none = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap()).toJson("test")
        assertTrue("ignores" !in none)
        val tooMany = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap(), ignores = List(401) { "p$it" }).toJson("test")
        assertFailsWith<IllegalArgumentException> { PlayerSave.fromJson(tooMany) }
    }

    @Test
    fun `aggressive monsters' tolerance areas and count are saved, and a save without them starts at the login`() {
        val tolerance = PlayerSave.ToleranceSave(Tile(3200, 3200), Tile(3222, 3218), 640)
        val text = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap(), tolerance = tolerance).toJson("test")
        saves.write("test", text)
        val player = world.loadPlayer(1, "test")
        assertEquals(Tile(3200, 3200), player.tolerance.older)
        assertEquals(Tile(3222, 3218), player.tolerance.newer)
        assertEquals(640, player.tolerance.ticks)
        val again = PlayerSave.fromJson(PlayerSave.of(player).toJson("test")).tolerance
        assertEquals(listOf(Tile(3200, 3200), Tile(3222, 3218), 640), listOf(again.older, again.newer, again.ticks))
        val none = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap()).toJson("test")
        assertTrue("tolerance" !in none)
        assertNull(PlayerSave.fromJson(none).tolerance.newer)
        val bad = text.replace("\"ticks\": 640", "\"ticks\": -1")
        assertFailsWith<IllegalArgumentException> { PlayerSave.fromJson(bad) }
    }

    @Test
    fun `the bank is saved, and a save without one loads with an empty bank`() {
        val player = world.loadPlayer(1, "Test")
        player.bank[0] = InvObj(995, 2_000_000_000)
        player.bank[Inventory.BANK_CAPACITY - 1] = InvObj(1511, 400)
        assertTrue(world.save(player))
        assertEquals(listOf(0 to InvObj(995, 2_000_000_000), 1409 to InvObj(1511, 400)), world.loadPlayer(2, "test").bank.contents())

        val none = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap()).toJson("none")
        assertTrue("bank" !in none)
        saves.write("none", none)
        assertTrue(world.loadPlayer(3, "none").bank.isEmpty())
        val past = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap(), bank = mapOf(1410 to InvObj(995, 1))).toJson("test")
        assertFailsWith<IllegalArgumentException> { PlayerSave.fromJson(past) }
    }

    @Test
    fun `a save from before equipment loads with nothing worn`() {
        val text = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap()).toJson("test")
        saves.write("test", text.replace(Regex(",\\s*\"equipment\": \\[\\s*]"), ""))
        assertTrue("equipment" !in saves.saves.getValue("test"))
        assertTrue(world.loadPlayer(1, "test").equipment.isEmpty())
    }

    @Test
    fun `an equipment slot past the last is refused`() {
        val text = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap(), equipment = mapOf(3 to InvObj(1351, 1))).toJson("test")
        assertFailsWith<IllegalArgumentException> { PlayerSave.fromJson(text.replace("\"slot\": 3", "\"slot\": 14")) }
    }

    @Test
    fun `a save from before the inventory loads with an empty one`() {
        val text = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap()).toJson("test")
        saves.write("test", text.replace(Regex(",\\s*\"inventory\": \\[\\s*]"), ""))
        assertTrue("inventory" !in saves.saves.getValue("test"))
        assertTrue(world.loadPlayer(1, "test").inventory.isEmpty())
    }

    @Test
    fun `a player with no save starts new in Lumbridge`() {
        val player = world.loadPlayer(1, "nobody")
        assertEquals(listOf(World.SPAWN_X, World.SPAWN_Z, World.SPAWN_LEVEL), listOf(player.x, player.z, player.level))
        assertEquals(Movement.MAX_ENERGY, player.runEnergy)
        assertEquals(3, player.skills.combatLevel)
    }

    @Test
    fun `the save is readable JSON with only the varps that aren't 0, and the special attack energy`() {
        val player = world.loadPlayer(1, "Test")
        player.vars[173] = 1
        world.save(player)
        val text = saves.saves.getValue("test")
        assertTrue("\"version\": 1" in text)
        assertTrue("\"username\": \"Test\"" in text)
        assertTrue("\"173\": 1" in text)
        assertEquals(mapOf(173 to 1, SpecialAttacks.VARP_ENERGY to SpecialAttacks.FULL), PlayerSave.fromJson(text).varps)
    }

    @Test
    fun `special attack energy is saved at 0 too, and a save from before it was kept starts full`() {
        val player = world.loadPlayer(1, "Test")
        assertEquals(SpecialAttacks.FULL, player.vars[SpecialAttacks.VARP_ENERGY], "a new account's")
        player.vars[SpecialAttacks.VARP_ENERGY] = 0
        world.save(player)
        val text = saves.saves.getValue("test")
        assertTrue("\"${SpecialAttacks.VARP_ENERGY}\": 0" in text)
        assertEquals(0, world.loadPlayer(2, "Test").vars[SpecialAttacks.VARP_ENERGY])
        val old = text.replace("\"${SpecialAttacks.VARP_ENERGY}\": 0", "\"1\": 0")
        saves.saves["test"] = old
        assertEquals(SpecialAttacks.FULL, world.loadPlayer(3, "Test").vars[SpecialAttacks.VARP_ENERGY])
    }

    @Test
    fun `loaded varps are sent on the first flush`() {
        val player = world.loadPlayer(1, "Test")
        player.vars[173] = 1
        world.save(player)
        val sent = mutableListOf<Pair<Int, Int>>()
        world.loadPlayer(2, "Test").vars.flush { varp, value -> sent += varp to value }
        assertEquals(listOf(173 to 1, SpecialAttacks.VARP_ENERGY to SpecialAttacks.FULL), sent)
    }

    @Test
    fun `a varp the cache no longer has is dropped instead of failing the login`() {
        saves.write("test", PlayerSave(3222, 3218, 0, 10_000, mapOf(173 to 1, 9_999 to 7), emptyMap()).toJson("test"))
        val player = world.loadPlayer(1, "test")
        assertEquals(1, player.vars[173])
    }

    @Test
    fun `saves that can't be used are refused`() {
        val good = PlayerSave(3222, 3218, 0, 10_000, emptyMap(), emptyMap()).toJson("test")
        val bad = listOf(
            "not json",
            "[]",
            good.replace("\"version\": 1", "\"version\": 2"),
            good.replace("\"level\": 0", "\"level\": 4"),
            good.replace("\"runEnergy\": 10000", "\"runEnergy\": 10001"),
            good.replace("\"runEnergy\": 10000", "\"runEnergy\": \"full\""),
            good.replace("\"skills\": {}", "\"skills\": {\"attack\": {\"xpTenths\": -1, \"currentLevel\": 1}}"),
            good.replace("\"skills\": {}", "\"skills\": {\"dancing\": {\"xpTenths\": 0, \"currentLevel\": 1}}"),
            good.replace("\"varps\": {}", "\"varps\": {\"abc\": 1}"),
            good.replace("\"position\"", "\"place\""),
            good.replace("\"inventory\": []", "\"inventory\": [{\"slot\": 28, \"id\": 1351, \"count\": 1}]"),
            good.replace("\"inventory\": []", "\"inventory\": [{\"slot\": 0, \"id\": 1351, \"count\": 0}]"),
            good.replace("\"inventory\": []", "\"inventory\": [{\"slot\": 0, \"id\": 1, \"count\": 1}, {\"slot\": 0, \"id\": 2, \"count\": 1}]"),
        )
        for (text in bad) {
            saves.write("test", text)
            assertFailsWith<IllegalArgumentException>(text) { world.loadPlayer(1, "test") }
        }
    }

    @Test
    fun `save keys ignore case and spaces and can't leave the folder`() {
        assertEquals("zezima", SaveStore.saveKey(" Zezima "))
        assertEquals("iron_man", SaveStore.saveKey("Iron Man"))
        assertEquals(SaveStore.saveKey("iron man"), SaveStore.saveKey("IRON_MAN"))
        assertEquals("%2e%2e%2fetc", SaveStore.saveKey("../etc"))
        assertEquals("a%40b%2ecom", SaveStore.saveKey("a@b.com"))
        assertEquals("%c3%a9", SaveStore.saveKey("é"))
        assertNull(SaveStore.saveKey("   "))
        assertNull(SaveStore.saveKey("x".repeat(SaveStore.MAX_KEY_LENGTH + 1)))
    }

    @Test
    fun `file saves are read back, replaced whole and leave no temporary file`(@TempDir dir: Path) {
        val store = FileSaveStore(dir.resolve("saves"))
        assertNull(store.read("test"))
        store.write("test", "one")
        store.write("test", "two")
        assertEquals("two", store.read("test"))
        assertEquals(listOf("test.json"), dir.resolve("saves").listDirectoryEntries().map { it.name })
        assertEquals("two", Files.readString(dir.resolve("saves/test.json")))
    }
}
