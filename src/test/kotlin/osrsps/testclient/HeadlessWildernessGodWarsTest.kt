package osrsps.testclient

import java.nio.file.Path
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
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.MultiCombat
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
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
    }

    private val ork = NpcType(
        2237, "Ork", ops = listOf(null, "Attack", null, null, null), combatLevel = 107, internalName = "godwars_ancient_ork1",
        stats = NpcStats(attack = 100, strength = 100, defence = 60, hitpoints = 110),
    )
    private val boulder = NpcType(WildernessGodWars.BOULDER, "Boulder", size = 2, ops = listOf("Move", null, null, null, null), internalName = "wilderness_gwd_boulder")

    private fun server(dir: Path, spawns: List<NpcSpawn> = emptyList()) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val types = NpcTypes.of(ork, boulder)
        val loc = { id: Int, name: String, op: String, w: Int, l: Int -> LocType(id, name, listOf(op, null, null, null, null), LocCollisionType(width = w, length = l, interactType = 0)) }
        val objs = ObjTypes.of(
            ObjType(KEY, "Ecumenical key", iops = listOf(null, null, null, null, "Drop")),
            ObjType(CHESTPLATE, "Bandos chestplate", iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = WearPos.BODY),
        )
        World(
            crcs, CollisionMap().apply { markLoaded(47, 58); markLoaded(47, 158) },
            npcTypes = types, random = GameRandom(3), npcSpawns = spawns, npcCombat = NpcCombat.load(types, SeqNames.NONE), objTypes = objs,
            locTypes = LocTypes(
                mapOf(
                    WildernessGodWars.ENTRANCE to loc(WildernessGodWars.ENTRANCE, "Cave", "Enter", 3, 2),
                    WildernessGodWars.CREVICE to loc(WildernessGodWars.CREVICE, "Crevice", "Use", 1, 1),
                    WildernessGodWars.CREVICE_BACK to loc(WildernessGodWars.CREVICE_BACK, "Crevice", "Use", 1, 1),
                    WildernessGodWars.JUTTING_WALL to loc(WildernessGodWars.JUTTING_WALL, "Jutting wall", "Pass", 2, 1),
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
    }
}
