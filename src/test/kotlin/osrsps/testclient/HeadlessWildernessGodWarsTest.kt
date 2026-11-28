package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.MultiCombat
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.combat.NpcHitDrain
import osrsps.game.combat.Prayers
import osrsps.game.content.WildernessGodWars
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.NpcSpawnFile
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Wilderness step W6.3 through the headless client (test-runner/examples/wilderness-god-wars-dungeon.json does the way
 * in, both paths and a follower on the real map): the Wilderness God Wars Dungeon's spawn file loads every follower in
 * its multi-way main cavern and the boulder in its single-way empty cavern; the followers fight and drop as the Wiki
 * has them; the cave leads down and its crevice back up; the boulder moves at Strength 60 and the jutting wall passes at
 * Agility 60, each giving XP going in; the crevices behind them lead down to the main cavern and back; a Bandos follower
 * leaves a player in a Bandos item alone and goes for one without; and an ecumenical key past the limit is deleted.
 */
class HeadlessWildernessGodWarsTest {
    private val names = mapOf(
        3169 to "godwars_armadyl_male_armor01_blue", 3177 to "godwars_armadyl_female_armor01_blue", 3170 to "godwars_armadyl_male_armor01_green",
        3171 to "godwars_armadyl_male_armor01_red", 3181 to "godwars_armadyl_female_armor02_green", 3174 to "godwars_armadyl_male_armor02_red",
        3183 to "godwars_armadyl_female_armor03_blue", 3175 to "godwars_armadyl_male_armor03_green", 3166 to "godwars_spiritual_armadyl_warrior",
        3167 to "godwars_spiritual_armadyl_ranger", 3168 to "godwars_spiritual_armadyl_mage", 2249 to "godwars_goblin5", 2248 to "godwars_goblin4",
        2237 to "godwars_ancient_ork1", 2238 to "godwars_ancient_ork2", 2239 to "godwars_ancient_ork3", 2240 to "godwars_ancient_ork4",
        2233 to "godwars_ancient_ogre", 2242 to "godwars_spiritual_bandos_ranger", 2244 to "godwars_spiritual_bandos_mage",
        2243 to "godwars_spiritual_bandos_warrior", 2214 to "godwars_saradomin_knight_2", 2213 to "godwars_saradomin_knight_1",
        2212 to "godwars_spiritual_saradomin_mage", 2211 to "godwars_spiritual_saradomin_ranger", 2210 to "godwars_spiritual_saradomin_warrior",
        3134 to "godwars_ancient_imp", 3140 to "godwars_icefiend_1", 3139 to "godwars_pyrefiend_1", 3137 to "godwars_ancient_vampire",
        3138 to "godwars_bloodveld", 3135 to "godwars_ancient_werewolf1", 3136 to "godwars_ancient_werewolf2", 3159 to "godwars_spiritual_zamorak_warrior",
        3160 to "godwars_spiritual_zamorak_ranger", 3161 to "godwars_spiritual_zamorak_mage", 3141 to "godwars_gorak",
        WildernessGodWars.BOULDER to "wilderness_gwd_boulder",
    )
    private val npcTypes = NpcTypes.of(*names.map { (id, name) -> NpcType(id, name, internalName = name) }.toTypedArray())

    @Test
    fun `the spawn file has the Wiki's followers in the multi-way cavern and the boulder in the single-way one`() {
        val text = NpcSpawnFile::class.java.getResource("/npc-spawns/wilderness-god-wars-dungeon.json")!!.readText()
        val parsed = NpcSpawnFile.parse(text, npcTypes, "wilderness-god-wars-dungeon.json")
        assertEquals(emptyList(), parsed.unknown)
        val multi = MultiCombat.load()
        val (boulders, followers) = parsed.spawns.partition { it.type.id == WildernessGodWars.BOULDER }
        assertEquals(1, boulders.size)
        assertEquals(3, boulders[0].level)
        assertTrue(!multi.at(3, boulders[0].x, boulders[0].z), "the empty cavern is single-way")
        assertEquals(76, followers.size)
        for (spawn in followers) {
            val at = "${spawn.type.name} at ${spawn.x},${spawn.z}"
            assertTrue(spawn.type.id in WildernessGodWars.ALL, at)
            assertTrue(WildernessGodWars.inDungeon(spawn.x, spawn.z, spawn.level) && spawn.level == 0, at)
            assertTrue(multi.at(0, spawn.x, spawn.z), "$at is multi-way")
        }
        // Every god's followers are there.
        for ((gods, _) in WildernessGodWars.FOLLOWERS) assertTrue(followers.any { it.type.id in gods })
    }

    @Test
    fun `the followers fight and drop as the Wiki has them`() {
        val combat = NpcCombat.load(npcTypes, SeqNames.NONE)
        for (id in WildernessGodWars.ALL) assertTrue(combat[id] != null, "NPC $id fights")
        assertEquals(83, combat[2212]!!.slayerLevel, "spiritual mages need Slayer 83")
        assertTrue(combat[3175]!!.flying, "aviansies fly out of melee's reach")
        assertTrue(combat[3141]!!.typeless, "the gorak's kick pierces prayer")
        val drops = NpcDrops.load(npcTypes, DropTestItems.objTypes())
        for (id in WildernessGodWars.ALL) assertTrue(drops[id] != null, "NPC $id drops")
        assertTrue(drops[3175]!!.table!!.lines.any { line -> line.item?.type?.id == ADAMANTITE_BAR_NOTED }, "the aviansies' bars noted")
        assertEquals(listOf(532), drops[3141]!!.always.map { it.type.id }, "the gorak's big bones")
        // Codex review on #1042: magical and ranged melee are Protect from Melee's; the gorak's hit drains a stat; every
        // follower that attacks unprovoked does so however long the player stays (no timed tolerance), the bloodveld too.
        for (id in listOf(3140, 3139, 3138, 3166)) assertEquals(AttackType.CRUSH, combat[id]!!.prayedAs, "NPC $id prayed as melee")
        assertEquals(NpcHitDrain(1, 4), combat[3141]!!.hitDrain)
        assertTrue(combat[3138]!!.aggression != null, "the bloodveld attacks unprovoked")
        for (id in WildernessGodWars.ALL) combat[id]!!.aggression?.let { assertTrue(!it.tolerant, "NPC $id never grows tolerant") }
    }

    private val ork = NpcType(
        2237, "Ork", ops = listOf(null, "Attack", null, null, null), combatLevel = 107, internalName = "godwars_ancient_ork1",
        stats = NpcStats(attack = 100, strength = 100, defence = 60, hitpoints = 110),
    )
    private val pyrefiend = NpcType(
        3139, "Pyrefiend", ops = listOf(null, "Attack", null, null, null), combatLevel = 48, internalName = "godwars_pyrefiend_1",
        stats = NpcStats(attack = 60, strength = 60, defence = 30, hitpoints = 45, magic = 60),
    )
    private val gorak = NpcType(
        3141, "Gorak", ops = listOf(null, "Attack", null, null, null), combatLevel = 149, internalName = "godwars_gorak",
        stats = NpcStats(attack = 130, strength = 130, defence = 130, hitpoints = 135),
    )
    private val boulder = NpcType(WildernessGodWars.BOULDER, "Boulder", size = 2, ops = listOf("Move", null, null, null, null), internalName = "wilderness_gwd_boulder")

    private fun server(dir: Path, spawns: List<NpcSpawn> = emptyList()) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val types = NpcTypes.of(ork, pyrefiend, gorak, boulder)
        val loc = { id: Int, name: String, op: String, w: Int, l: Int -> LocType(id, name, listOf(op, null, null, null, null), LocCollisionType(width = w, length = l, interactType = 0)) }
        val objs = ObjTypes.of(
            ObjType(KEY, "Ecumenical key", iops = listOf(null, null, null, null, "Drop")),
            ObjType(CHESTPLATE, "Bandos chestplate", iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = WearPos.BODY),
            // Protect from Melee's prayer obj, shaped like the cache's (HeadlessArchiveGuardianTest).
            ObjType(
                PROTECT_FROM_MELEE_OBJ, "Null", internalName = "placeholder_xmas16_marionette_blue",
                params = mapOf(630 to PROTECT_FROM_MELEE_BIT, 1751 to (Prayers.PRAYERBOOK shl 16 or 23), 1752 to "Protect from Melee", 1753 to 43, 1755 to 2676),
            ),
        )
        World(
            crcs, CollisionMap().apply { markLoaded(47, 58); markLoaded(47, 158) },
            npcTypes = types, random = GameRandom(3), npcSpawns = spawns, npcCombat = NpcCombat.load(types, SeqNames.NONE), objTypes = objs, prayerList = Prayers.load(objs),
            locTypes = LocTypes(
                mapOf(
                    WildernessGodWars.ENTRANCE to loc(WildernessGodWars.ENTRANCE, "Cave", "Enter", 3, 2),
                    WildernessGodWars.CREVICE to loc(WildernessGodWars.CREVICE, "Crevice", "Use", 1, 1),
                    WildernessGodWars.CREVICE_BACK to loc(WildernessGodWars.CREVICE_BACK, "Crevice", "Use", 1, 1),
                    WildernessGodWars.JUTTING_WALL to loc(WildernessGodWars.JUTTING_WALL, "Jutting wall", "Pass", 2, 1),
                    WildernessGodWars.BLOCKER to LocType(WildernessGodWars.BLOCKER, "null", listOf(null, null, null, null, null), LocCollisionType(width = 1, length = 1)),
                ),
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(WildernessGodWars.ENTRANCE, 10, 2, 3016, 3738, 0))
                add(ZoneLoc(WildernessGodWars.CREVICE, 0, 3, 3065, 10160, 3))
                add(ZoneLoc(WildernessGodWars.CREVICE, 0, 2, 3049, 10165, 3))
                add(ZoneLoc(WildernessGodWars.CREVICE, 0, 1, 3066, 10142, 3))
                add(ZoneLoc(WildernessGodWars.CREVICE_BACK, 0, 0, 3035, 10158, 0))
                add(ZoneLoc(WildernessGodWars.CREVICE_BACK, 0, 3, 3062, 10131, 0))
                add(ZoneLoc(WildernessGodWars.JUTTING_WALL, 10, 0, 3066, 10148, 3))
            },
            cycleMillis = millis,
        )
    }

    private fun <T> TestServer.onTick(block: () -> T): T {
        val result = CompletableFuture<T>()
        world.npcScripts.later(1) { result.complete(block()) }
        return result.get(10, TimeUnit.SECONDS)
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.arrive(x: Int, z: Int, level: Int, what: String) =
        await(what, timeoutMillis = 15_000) { position?.takeIf { it == Coord(x, z, level) } }

    private fun HeadlessClient.level(skill: Int, level: Int, name: String) {
        command("setlevel $name $level")
        await("$name $level") { stat(skill)?.takeIf { it.level == level } }
    }

    @Test
    fun `the way in and out, the boulder and the jutting wall at 60, and the crevices down and back`(@TempDir dir: Path) {
        server(dir, listOf(NpcSpawn(boulder, 3053, 10165, level = 3, wander = 0, source = "test"))).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(3017, 3740)
                client.locOp(WildernessGodWars.ENTRANCE, 3016, 3738, 1)
                client.arrive(3065, 10159, 3, "in the empty cavern")
                client.awaitTicks(2)
                client.locOp(WildernessGodWars.CREVICE, 3065, 10160, 1)
                client.arrive(3017, 3740, 0, "back out by the cave")

                client.level(STRENGTH, 59, "strength")
                client.tele(3055, 10165, 3)
                val rock = client.await("the boulder in view") { client.npcs().firstOrNull { it.id == WildernessGodWars.BOULDER } }
                client.npcOp(rock.index, 1)
                client.await("the Strength message") { client.messages().firstOrNull { it.text == "[wilderness_gwd.boulder_strength level=60]" } }
                client.level(STRENGTH, 60, "strength")
                val strength = client.stat(STRENGTH)!!.xp
                client.npcOp(rock.index, 1)
                client.arrive(3052, 10165, 3, "past the boulder")
                client.await("5.5 Strength XP") { client.stat(STRENGTH)?.takeIf { it.xp == strength + 5 } }
                client.awaitTicks(2)
                client.locOp(WildernessGodWars.CREVICE, 3049, 10165, 1)
                client.arrive(3034, 10158, 0, "by Armadyl's camp")
                client.awaitTicks(2)
                client.locOp(WildernessGodWars.CREVICE_BACK, 3035, 10158, 1)
                client.arrive(3050, 10165, 3, "back behind the boulder")
                client.awaitTicks(2)
                client.npcOp(rock.index, 1)
                client.arrive(3055, 10165, 3, "back past the boulder")

                client.level(AGILITY, 59, "agility")
                client.tele(3066, 10149, 3)
                client.locOp(WildernessGodWars.JUTTING_WALL, 3066, 10148, 1)
                client.await("the Agility message") { client.messages().firstOrNull { it.text == "[agility.level level=60]" } }
                client.level(AGILITY, 60, "agility")
                val agility = client.stat(AGILITY)!!.xp
                client.locOp(WildernessGodWars.JUTTING_WALL, 3066, 10148, 1)
                client.arrive(3066, 10147, 3, "past the jutting wall")
                client.await("5.5 Agility XP") { client.stat(AGILITY)?.takeIf { it.xp == agility + 5 } }
                client.awaitTicks(2)
                client.locOp(WildernessGodWars.CREVICE, 3066, 10142, 1)
                client.arrive(3062, 10130, 0, "by Bandos's camp")
                client.awaitTicks(2)
                client.locOp(WildernessGodWars.CREVICE_BACK, 3062, 10131, 1)
                client.arrive(3066, 10143, 3, "back behind the jutting wall")
            }
        }
    }

    @Test
    fun `below Strength 60 the boulder can't be walked through, only moved`(@TempDir dir: Path) {
        server(dir, listOf(NpcSpawn(boulder, 3053, 10165, level = 3, wander = 0, source = "test"))).use { server ->
            server.login("fay").use { client ->
                client.await("the login rebuild") { client.position }
                // Exploit review R67A-1: an NPC blocks only NPCs, so blocking locs hold its tiles shut.
                for (dx in 0..1) for (dz in 0..1) {
                    assertTrue(server.world.locs.find(WildernessGodWars.BLOCKER, 3053 + dx, 10165 + dz, 3) != null, "blocked at ${3053 + dx}, ${10165 + dz}")
                }
                client.level(STRENGTH, 1, "strength")
                client.tele(3055, 10165, 3)
                client.walk(3054, 10165)
                client.awaitTicks(4)
                assertEquals(Coord(3055, 10165, 3), client.position, "not onto the boulder's tile")
            }
        }
    }

    @Test
    fun `a Bandos follower leaves a player in a Bandos item alone and goes for one without`(@TempDir dir: Path) {
        server(dir, listOf(NpcSpawn(ork, 3050, 10130, wander = 0, source = "test"))).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                val npc = client.await("the ork") { server.world.npcs.all.firstOrNull { it.type.id == ork.id } }
                client.commandReply("setlevel hitpoints 99") { true }
                client.command("item $CHESTPLATE")
                val slot = client.await("the chestplate") { client.inventory(BACKPACK).indexOfFirst { it?.id == CHESTPLATE }.takeIf { it >= 0 } }
                client.ifButton(INVENTORY, INVENTORY_ITEMS, IOP_WEAR, slot, CHESTPLATE)
                client.await("the chestplate worn") { client.inventory(WORN).getOrNull(WearPos.BODY)?.takeIf { it.id == CHESTPLATE } }
                client.tele(3052, 10130)
                client.awaitTicks(8)
                assertNull(npc.target, "spared in a Bandos item")
                client.command("tele 3016 3740 0")
            }
            val npc = server.world.npcs.all.first { it.type.id == ork.id }
            server.login("bo").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel hitpoints 99") { true }
                client.tele(3052, 10130)
                client.await("the ork's attack", timeoutMillis = 15_000) { npc.target?.takeIf { it.username == "bo" } }
            }
        }
    }

    @Test
    fun `an ecumenical key past the limit is deleted as it is taken`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cy").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(3040, 10140)
                client.command("item $KEY ${WildernessGodWars.KEY_LIMIT}")
                client.await("the keys") { client.inventory(BACKPACK).count { it?.id == KEY }.takeIf { it == WildernessGodWars.KEY_LIMIT } }
                val slot = client.inventory(BACKPACK).indexOfFirst { it?.id == KEY }
                client.ifButton(INVENTORY, INVENTORY_ITEMS, DROP, slot, KEY)
                client.await("the key on the floor") { client.groundObjs().firstOrNull { it.id == KEY } }
                // Four held: the fifth may be taken back.
                client.objOp(KEY, 3040, 10140, 3)
                client.await("taken back") { client.inventory(BACKPACK).count { it?.id == KEY }.takeIf { it == WildernessGodWars.KEY_LIMIT } }
                client.command("item $KEY")
                client.await("six") { client.inventory(BACKPACK).count { it?.id == KEY }.takeIf { it == WildernessGodWars.KEY_LIMIT + 1 } }
                client.ifButton(INVENTORY, INVENTORY_ITEMS, DROP, client.inventory(BACKPACK).indexOfFirst { it?.id == KEY }, KEY)
                client.await("the key on the floor") { client.groundObjs().firstOrNull { it.id == KEY } }
                client.objOp(KEY, 3040, 10140, 3)
                client.await("the limit's message") { client.messages().firstOrNull { it.text == "[wilderness_gwd.enough_keys]" } }
                client.await("the key gone") { if (client.groundObjs().none { it.id == KEY }) Unit else null }
                assertEquals(WildernessGodWars.KEY_LIMIT, client.inventory(BACKPACK).count { it?.id == KEY })
                assertSame(null, client.groundObjs().firstOrNull { it.id == KEY })
            }
        }
    }

    @Test
    fun `a follower killed in the dungeon drops an ecumenical key now and then, and one killed outside it never does`(@TempDir dir: Path) {
        // 300 deaths' loot at 1/60 misses every key about once in 170,000 runs (Codex review on #1042: the loot path itself).
        val inside = (0 until 5).flatMap { row -> (3010 until 3070).map { x -> NpcSpawn(ork, x, 10120 + row * 2, wander = 0, source = "test") } }
        val outside = (3010 until 3040).map { x -> NpcSpawn(ork, x, 3744, wander = 0, source = "test") }
        server(dir, inside + outside).use { server ->
            server.login("dee").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel hitpoints 99") { true }
                client.tele(3040, 10150)
                val world = server.world
                val dee = world.playerNamed("dee")!!
                val orks = world.npcs.all.filter { it.type.id == ork.id }.toList()
                // Each body's loot as its death animation ends (MeleeCombat.process), the extra-drop hooks with it.
                server.onTick { for (npc in orks) world.dropLoot(npc, dee) }
                fun keys(xs: IntRange, zs: IntRange) = xs.sumOf { x -> zs.sumOf { z -> world.groundObjs.visibleAt(dee, x, z, 0).count { it.id == KEY } } }
                assertTrue(keys(3008..3071, 10112..10175) > 0, "a key among 300 deaths in the dungeon")
                assertEquals(0, keys(3000..3060, 3712..3775), "no key from a follower outside the dungeon")
            }
        }
    }

    @Test
    fun `Protect from Melee blocks a pyrefiend's magical melee, and a gorak's hits drain stats`(@TempDir dir: Path) {
        val spawns = listOf(NpcSpawn(pyrefiend, 3044, 10150, wander = 0, source = "test"), NpcSpawn(gorak, 3044, 10154, wander = 0, source = "test"))
        server(dir, spawns).use { server ->
            server.login("eve").use { client ->
                client.await("the login rebuild") { client.position }
                for (skill in listOf("attack", "strength", "defence", "magic", "ranged", "prayer")) client.commandReply("setlevel $skill 99") { true }
                client.commandReply("setlevel hitpoints 99") { true }
                client.tele(3040, 10150)
                val world = server.world
                val eve = world.playerNamed("eve")!!
                val pyre = world.npcs.all.first { it.type.id == pyrefiend.id }
                val kicker = world.npcs.all.first { it.type.id == gorak.id }
                // Codex review on #1042: magical melee rolls against Magic defence, but Protect from Melee blocks it.
                val prayed = server.onTick {
                    eve.prayersInEffect = 1 shl PROTECT_FROM_MELEE_BIT
                    val before = eve.hitpoints
                    repeat(30) { world.meleeCombat.swingAt(pyre, eve, world.tick) }
                    before - eve.hitpoints
                }
                assertEquals(0, prayed, "Protect from Melee blocks every pyrefiend hit")
                val unprayed = server.onTick {
                    eve.prayersInEffect = 0
                    val before = eve.hitpoints
                    repeat(15) { world.meleeCombat.swingAt(pyre, eve, world.tick) }
                    before - eve.hitpoints
                }
                assertTrue(unprayed > 0, "without the prayer the pyrefiend hits")
                // A gorak's successful hits each drain a random stat other than Hitpoints by 1 to 4.
                client.commandReply("setlevel hitpoints 99") { true }
                val drained = server.onTick {
                    val before = Skill.entries.filter { it != Skill.HITPOINTS }.sumOf { eve.skills.currentLevel(it) }
                    repeat(4) { world.meleeCombat.swingAt(kicker, eve, world.tick) }
                    before - Skill.entries.filter { it != Skill.HITPOINTS }.sumOf { eve.skills.currentLevel(it) }
                }
                assertTrue(drained > 0, "the gorak's hits drained a stat")
            }
        }
    }

    private companion object {
        const val KEY = WildernessGodWars.KEY
        const val CHESTPLATE = 11832
        const val ADAMANTITE_BAR_NOTED = 2362
        const val STRENGTH = 2
        const val AGILITY = 16
        const val BACKPACK = 93
        const val WORN = 94
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val IOP_WEAR = 3
        const val DROP = 7
        const val PROTECT_FROM_MELEE_BIT = 14
        const val PROTECT_FROM_MELEE_OBJ = 20817
    }
}
