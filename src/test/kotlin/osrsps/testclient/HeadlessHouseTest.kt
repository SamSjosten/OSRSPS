package osrsps.testclient

import osrsps.game.AllSettings
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.PlayerSave
import osrsps.game.Skill
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.construction.EstateAgent
import osrsps.game.skill.construction.Furniture
import osrsps.game.skill.construction.FurnitureData
import osrsps.game.skill.construction.FurnitureOption
import osrsps.game.skill.construction.House
import osrsps.game.skill.construction.Kitchen
import osrsps.game.skill.construction.HouseRoom
import osrsps.game.skill.construction.RoomType
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Construction steps CON1 and CON2 through the headless client: the estate agent sells the starter house, the
 * Rimmington portal takes its owner in with building mode off (walls, doors, the exit portal, no hotspots) or on (the
 * hotspots, the exit portal on its own), the exit portal and a logout put them back outside, and the house is in the
 * save; in building mode a door hotspot adds a room, turned to fit, and removes it. The house's room templates here are
 * the cache's own zones (garden 232,881, parlour 232,887, kitchen 234,887, yard 233,880), with the door hotspots the 242
 * cache places in them. The rl-client scripts test-runner/examples/construction-house.json and construction-rooms.json
 * do the same on the real map.
 */
class HeadlessHouseTest {
    private val agent = NpcType(EstateAgent.ID, "Estate agent", ops = listOf("Talk-to", null, "Relocate", "Redecorate", null), internalName = "poh_estate_agent")

    private fun hotspot(id: Int, name: String, width: Int = 1) =
        LocType(id, name, ops = listOf(null, null, null, null, House.BUILD_OP), collision = LocCollisionType(width = width, length = width, interactType = 0))

    private fun built(id: Int, name: String) =
        LocType(id, name, ops = listOf(null, null, null, null, "Remove"), collision = LocCollisionType(width = 1, length = 1, interactType = 0))

    private fun door(id: Int, op: String) = LocType(id, "Door", ops = listOf(op, null, null, null, null), collision = LocCollisionType(width = 1, length = 1))

    private val types = listOf(
        LocType(House.RIMMINGTON_PORTAL, "Portal", ops = listOf("Enter", "Home", "Build mode", "Friend's house", null), collision = LocCollisionType(width = 5, length = 2)),
        LocType(House.EXIT_PORTAL, "Portal", ops = listOf("Enter", "Lock", "Remove board advert", null, "Remove"), collision = LocCollisionType(width = 2, length = 2)),
        hotspot(House.CENTREPIECE_SPACE, "Centrepiece space", 2),
        hotspot(House.DOOR_HOTSPOT_LEFT, "Door hotspot"),
        hotspot(House.DOOR_HOTSPOT_RIGHT, "Door hotspot"),
        hotspot(CHAIR_SPACE, "Chair space"),
        hotspot(RUG_MIDDLE_SPACE, "Rug space"), hotspot(RUG_SIDE_SPACE, "Rug space"), hotspot(RUG_CORNER_SPACE, "Rug space"),
        LocType(WOODEN_LARDER, "Larder", ops = listOf("Search", null, null, null, "Remove"), collision = LocCollisionType(width = 2, length = 1)),
        LocType(BROWN_RUG_CORNER, "Rug", ops = listOf(null, null, null, null, "Remove"), collision = LocCollisionType(width = 1, length = 1, interactType = 0)),
        LocType(BROWN_RUG_SIDE, "Rug", ops = listOf(null, null, null, null, "Remove"), collision = LocCollisionType(width = 1, length = 1, interactType = 0)),
        LocType(BROWN_RUG_MIDDLE, "Rug", ops = listOf(null, null, null, null, "Remove"), collision = LocCollisionType(width = 1, length = 1, interactType = 0)),
        hotspot(BOOKCASE_SPACE, "Bookcase space"), hotspot(SCROLL_SPACE, "Bookcase space"),
        hotspot(FIREPLACE_SPACE, "Fireplace space"), hotspot(CURTAIN_SPACE, "Curtain space"),
        built(WOODEN_BOOKCASE, "Wooden bookcase"), built(WOODEN_SCROLLS, "Wooden bookcase"),
        built(CLAY_FIREPLACE, "Clay fireplace"), built(TORN_CURTAINS, "Torn curtains"),
        LocType(LARDER_SPACE, "Larder space", ops = listOf(null, null, null, null, House.BUILD_OP), collision = LocCollisionType(width = 2, length = 1, interactType = 0)),
        LocType(House.WALL, "null", collision = LocCollisionType(width = 1, length = 1)),
        LocType(CRUDE_CHAIR, "Chair", ops = listOf("Sit-on", null, null, null, "Remove"), collision = LocCollisionType(width = 1, length = 1)),
        door(House.DOOR_LEFT, "Open"), door(House.DOOR_RIGHT, "Open"), door(House.DOOR_LEFT_OPEN, "Close"), door(House.DOOR_RIGHT_OPEN, "Close"),
    ).associateBy { it.id }

    /** The four door hotspot pairs of a template zone at [x], [z] on the sides [sides] (0 west, 1 north, 2 east, 3 south), as the cache places them. */
    private fun doors(x: Int, z: Int, sides: List<Int>): List<ZoneLoc> = sides.flatMap { side ->
        when (side) {
            0 -> listOf(ZoneLoc(House.DOOR_HOTSPOT_LEFT, LocShape.WALL_STRAIGHT, 0, x, z + 4, 0), ZoneLoc(House.DOOR_HOTSPOT_RIGHT, LocShape.WALL_STRAIGHT, 0, x, z + 3, 0))
            1 -> listOf(ZoneLoc(House.DOOR_HOTSPOT_LEFT, LocShape.WALL_STRAIGHT, 1, x + 4, z + 7, 0), ZoneLoc(House.DOOR_HOTSPOT_RIGHT, LocShape.WALL_STRAIGHT, 1, x + 3, z + 7, 0))
            2 -> listOf(ZoneLoc(House.DOOR_HOTSPOT_LEFT, LocShape.WALL_STRAIGHT, 2, x + 7, z + 3, 0), ZoneLoc(House.DOOR_HOTSPOT_RIGHT, LocShape.WALL_STRAIGHT, 2, x + 7, z + 4, 0))
            else -> listOf(ZoneLoc(House.DOOR_HOTSPOT_LEFT, LocShape.WALL_STRAIGHT, 3, x + 3, z, 0), ZoneLoc(House.DOOR_HOTSPOT_RIGHT, LocShape.WALL_STRAIGHT, 3, x + 4, z, 0))
        }
    }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Furniture.ANY_NAILS + 1)
        objs[EstateAgent.COINS] = ObjType(EstateAgent.COINS, "Coins", stackable = true)
        objs[PLANK] = ObjType(PLANK, "Plank")
        objs[BOLT_OF_CLOTH] = ObjType(BOLT_OF_CLOTH, "Bolt of cloth")
        objs[SOFT_CLAY] = ObjType(SOFT_CLAY, "Soft clay")
        objs[TEA_LEAVES] = ObjType(TEA_LEAVES, "Tea leaves")
        objs[MILK] = ObjType(MILK, "Bucket of milk")
        objs[STEEL_NAILS] = ObjType(STEEL_NAILS, "Steel nails", stackable = true)
        objs[BRONZE_NAILS] = ObjType(BRONZE_NAILS, "Bronze nails", stackable = true)
        objs[Furniture.ANY_NAILS] = ObjType(Furniture.ANY_NAILS, "Nails")
        objs[HAMMER] = ObjType(HAMMER, "Hammer")
        objs[SAW] = ObjType(SAW, "Saw")
        objs[EstateAgent.GUIDE_BOOK] = ObjType(EstateAgent.GUIDE_BOOK, "Construction guide")
        val garden = RoomType.GARDEN
        val parlour = RoomType.PARLOUR
        val kitchen = RoomType.KITCHEN
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(garden.templateX shr 6, garden.templateZ shr 6)
                markLoaded(PORTAL_X shr 6, PORTAL_Z shr 6)
                markLoaded(AGENT_X shr 6, AGENT_Z shr 6)
            },
            objTypes = ObjTypes(objs),
            npcTypes = NpcTypes.of(agent),
            npcSpawns = listOf(NpcSpawn(agent, AGENT_X, AGENT_Z, source = "test")),
            locTypes = LocTypes(types),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(House.RIMMINGTON_PORTAL, LocShape.CENTREPIECE_STRAIGHT, 1, PORTAL_X, PORTAL_Z, 0))
                add(ZoneLoc(House.CENTREPIECE_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, garden.templateX + 3, garden.templateZ + 3, 0))
                doors(garden.templateX, garden.templateZ, listOf(0, 1, 2, 3)).forEach(::add)
                add(ZoneLoc(CHAIR_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, parlour.templateX + 2, parlour.templateZ + 4, 0))
                // A small rug: a corner, a side and the middle, one tile each (the cache's rug is bigger).
                add(ZoneLoc(RUG_CORNER_SPACE, LocShape.GROUND_DECOR, 0, parlour.templateX + 1, parlour.templateZ + 1, 0))
                add(ZoneLoc(RUG_SIDE_SPACE, LocShape.GROUND_DECOR, 0, parlour.templateX + 2, parlour.templateZ + 1, 0))
                add(ZoneLoc(RUG_MIDDLE_SPACE, LocShape.GROUND_DECOR, 0, parlour.templateX + 2, parlour.templateZ + 2, 0))
                // A bookcase with its scroll rack beside it, a fireplace, and curtains on two windows of the north wall.
                add(ZoneLoc(BOOKCASE_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, parlour.templateX + 6, parlour.templateZ + 2, 0))
                add(ZoneLoc(SCROLL_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, parlour.templateX + 6, parlour.templateZ + 3, 0))
                add(ZoneLoc(FIREPLACE_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, parlour.templateX + 4, parlour.templateZ + 6, 0))
                add(ZoneLoc(CURTAIN_SPACE, LocShape.WALL_STRAIGHT, 1, parlour.templateX + 2, parlour.templateZ + 7, 0))
                add(ZoneLoc(CURTAIN_SPACE, LocShape.WALL_STRAIGHT, 1, parlour.templateX + 5, parlour.templateZ + 7, 0))
                doors(parlour.templateX, parlour.templateZ, listOf(0, 2, 3)).forEach(::add)
                // The kitchen's doors are west and south; a 2 by 1 space in its north-west shows how a turned room moves its locs.
                doors(kitchen.templateX, kitchen.templateZ, listOf(0, 3)).forEach(::add)
                add(ZoneLoc(LARDER_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, kitchen.templateX + 1, kitchen.templateZ + 6, 0))
            },
            // Building mode's varbit, on a varp of its own here (the cache's layout doesn't matter to the server).
            varTypes = VarTypes(3_000, mapOf(House.BUILDING_MODE_VARBIT to VarbitType(2_000, 0, 0))),
            cycleMillis = millis,
            furnitureData = FurnitureData(
                mapOf(
                    RoomType.PARLOUR.id to List(3) { chairs } + listOf(rugs, bookcases, fireplaces, curtains),
                    RoomType.KITCHEN.id to List(4) { emptyList<FurnitureOption>() } + listOf(larders),
                ),
            ),
        )
    }

    /** The chair spaces' first two choices, as the cache's furniture table has them (rows 5517 and 5518). */
    private val chairs = listOf(
        FurnitureOption(CRUDE_CHAIR_ROW, "Crude wooden chair", 8309, listOf(PLANK to 2, Furniture.ANY_NAILS to 2), level = 1),
        FurnitureOption(WOODEN_CHAIR_ROW, "Wooden chair", 8310, listOf(PLANK to 3, Furniture.ANY_NAILS to 3), level = 8),
    )

    /** The larder space's first choice (row 5562). */
    private val larders = listOf(FurnitureOption(WOODEN_LARDER_ROW, "Wooden larder", 8233, listOf(PLANK to 8, Furniture.ANY_NAILS to 8), level = 9))

    /** The rug space's first choice (row 5524). */
    private val rugs = listOf(FurnitureOption(BROWN_RUG_ROW, "Brown rug", 8316, listOf(BOLT_OF_CLOTH to 2), level = 2))

    /** The other parlour places' first choices (rows 5529, 5532 and 5535), with the Wiki's materials and levels. */
    private val bookcases = listOf(FurnitureOption(WOODEN_BOOKCASE_ROW, "Wooden bookcase", 8319, listOf(PLANK to 4, Furniture.ANY_NAILS to 4), level = 4))
    private val fireplaces = listOf(FurnitureOption(CLAY_FIREPLACE_ROW, "Clay fireplace", 8325, listOf(SOFT_CLAY to 3), level = 3))
    private val curtains = listOf(FurnitureOption(TORN_CURTAINS_ROW, "Torn curtains", 8322, listOf(PLANK to 3, BOLT_OF_CLOTH to 3, Furniture.ANY_NAILS to 3), level = 2))

    private fun HeadlessClient.ready() = await("the login rebuild") { position }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.chats(vararg keys: String) {
        for (key in keys) {
            awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
            continueDialogue()
        }
    }

    private fun HeadlessClient.menu(vararg keys: String) {
        val menu = awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
        assertEquals(keys.map { "[$it]" }, menu.options)
    }

    private fun HeadlessClient.awaitClosed() = await("the dialogue to close") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.buyHouse() {
        tele(AGENT_X + 1, AGENT_Z)
        command("item ${EstateAgent.COINS} ${EstateAgent.HOUSE_PRICE}")
        await("the coins") { if (count(EstateAgent.COINS) == EstateAgent.HOUSE_PRICE) Unit else null }
        val npc = await("the estate agent") { npcs().firstOrNull { it.id == EstateAgent.ID } }
        npcOp(npc.index, EstateAgent.TALK_TO)
        chats("estate_agent.greeting")
        menu("estate_agent.how_get", "estate_agent.about")
        chooseOption(1)
        chats("estate_agent.how_get", "estate_agent.how_get_reply", "estate_agent.want_buy")
        menu("estate_agent.yes_please", "estate_agent.no_thanks")
        chooseOption(1)
        chats("estate_agent.yes_please")
        // The guide comes with the sale, before the closing lines that could be walked away from.
        await("the guide book") { if (count(EstateAgent.GUIDE_BOOK) == 1) Unit else null }
        chats("estate_agent.sold", "estate_agent.sold_book")
        awaitClosed()
    }

    /** Uses the portal's [op] from outside it, and waits to be in the house; returns where the player landed. */
    private fun HeadlessClient.goIn(op: Int): Coord {
        tele(OUTSIDE.x, OUTSIDE.z)
        locOp(House.RIMMINGTON_PORTAL, PORTAL_X, PORTAL_Z, op)
        return await("the house") { position?.takeIf { it.x >= 6400 && it.level == House.GROUND } }
    }

    private fun HeadlessClient.shown(id: Int, x: Int, z: Int) = locChanges().any { it.id == id && it.coord == Coord(x, z, House.GROUND) }

    @Test
    fun `the estate agent sells the starter house, and the portal takes its owner in and out of it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                // No house yet: the portal says so.
                client.tele(OUTSIDE.x, OUTSIDE.z)
                client.locOp(House.RIMMINGTON_PORTAL, PORTAL_X, PORTAL_Z, House.HOME)
                client.await("the refusal") { client.messages().firstOrNull { it.text == "[construction.no_house]" } }

                client.buyHouse()
                assertEquals(0, client.count(EstateAgent.COINS))
                val house = assertNotNull(server.world.playerNamed("alice")!!.house)
                assertEquals(listOf(RoomType.GARDEN, RoomType.PARLOUR), house.rooms.map { it.type })

                val at = client.goIn(House.HOME)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                val gardenX = instance.baseX + (House.STARTER_X shl 3)
                val gardenZ = instance.baseZ + (House.STARTER_Z shl 3)
                assertEquals(Coord(gardenX + House.ARRIVE_DX, gardenZ + House.ARRIVE_DZ, House.GROUND), at)
                // The client draws each cell from its template, the ground floor on level 1.
                val zones = client.await("the region rebuild") { client.regionZones() }
                assertEquals(CopiedZone(232, 881, 0, 0), zones[Triple(gardenX shr 3, gardenZ shr 3, House.GROUND)])
                assertEquals(CopiedZone(232, 887, 0, 0), zones[Triple(gardenX shr 3, (gardenZ shr 3) + 1, House.GROUND)])
                assertEquals(CopiedZone(233, 880, 0, 0), zones[Triple((gardenX shr 3) - 2, (gardenZ shr 3) - 2, House.GROUND)])
                // A level 1 builder's yard is 5 by 5 cells: none past it.
                assertNull(zones[Triple((gardenX shr 3) + 3, gardenZ shr 3, House.GROUND)])

                // Building mode off: the exit portal on the centrepiece, the garden's door hotspots and the chair space
                // gone, the parlour's south door onto the garden a door, its west and east sides walls.
                val parlourZ = gardenZ + 8
                // Each cell's locs come in their own zone update, so wait for all of them, not just the garden's (a CI
                // run once saw the parlour's arrive after the portal's).
                // Its right leaf is on the left hotspot's tile: the double door's leaves go clockwise along the wall.
                val expected = listOf(
                    Triple(House.EXIT_PORTAL, gardenX + 3, gardenZ + 3) to "the exit portal",
                    Triple(-1, gardenX + 3, gardenZ + 7) to "the garden's north door hotspot goes",
                    Triple(-1, gardenX + 2, parlourZ + 4) to "the chair space goes",
                    Triple(House.DOOR_RIGHT, gardenX + 3, parlourZ) to "the parlour's south door",
                    Triple(House.DOOR_LEFT, gardenX + 4, parlourZ) to "the parlour's south door",
                    Triple(House.WALL, gardenX, parlourZ + 4) to "the parlour's west wall",
                    Triple(House.WALL, gardenX + 7, parlourZ + 3) to "the parlour's east wall",
                )
                client.await("the house's own locs: ${expected.map { it.second }}") {
                    if (expected.all { (loc, _) -> client.shown(loc.first, loc.second, loc.third) }) Unit else null
                }

                // The parlour's door opens as a double door, the house's own.
                client.locOp(House.DOOR_LEFT, gardenX + 4, parlourZ, 1)
                client.await("both leaves open") {
                    if (client.locChanges().any { it.id == House.DOOR_LEFT_OPEN } && client.locChanges().any { it.id == House.DOOR_RIGHT_OPEN }) Unit else null
                }

                // A death here is a safe one, outside the portal she came in by.
                val alice = server.world.playerNamed("alice")!!
                val death = assertNotNull(server.world.houses.deathPlace(alice))
                assertEquals(listOf(true, true, OUTSIDE.x, OUTSIDE.z, 0), listOf(death.safe, death.keepItems, death.toX, death.toZ, death.toLevel))

                // Poisoned in the house, the exit portal puts her outside the Rimmington portal cured, and the house
                // ends once she has gone.
                client.command("poison 6")
                client.await("poisoned") { if (server.world.poison.value(alice) > 0) Unit else null }
                client.locOp(House.EXIT_PORTAL, gardenX + 3, gardenZ + 3, House.EXIT_ENTER)
                client.await("outside") { client.position?.takeIf { it == Coord(OUTSIDE.x, OUTSIDE.z, 0) } }
                client.await("cured") { if (server.world.poison.value(alice) == 0) Unit else null }
                assertNull(server.world.houses.deathPlace(alice), "outside, an ordinary death")
                client.await("the house ended") { if (server.world.instances.all.isEmpty()) Unit else null }

                // Building mode: the hotspots stay, what is built shows on them.
                val built = client.goIn(House.BUILD_MODE)
                val buildBase = assertNotNull(server.world.instances.at(built.x, built.z))
                val bx = buildBase.baseX + (House.STARTER_X shl 3)
                val bz = buildBase.baseZ + (House.STARTER_Z shl 3)
                client.await("the exit portal") { if (client.shown(House.EXIT_PORTAL, bx + 3, bz + 3)) Unit else null }
                assertTrue(client.locChanges().none { it.coord.level == House.GROUND && it.id != House.EXIT_PORTAL }, "only the exit portal changes: ${client.locChanges()}")

                // A save from inside is outside the portal, house and all, and reads back the same.
                val save = server.world.snapshot(server.world.playerNamed("alice")!!)
                assertEquals(Coord(OUTSIDE.x, OUTSIDE.z, 0), Coord(save.x, save.z, save.level))
                val read = assertNotNull(PlayerSave.fromJson(save.toJson("alice")).house)
                assertEquals(listOf("garden 2,2 {${House.CENTREPIECE_SPACE}=${House.EXIT_PORTAL}}", "parlour 2,3 {}"), read.rooms.map { "${it.type.key} ${it.x},${it.z} ${it.built}" })
            }
        }
    }

    @Test
    fun `Enter offers the menu, and a second house is never sold`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.buyHouse()
                client.tele(OUTSIDE.x, OUTSIDE.z)
                client.locOp(House.RIMMINGTON_PORTAL, PORTAL_X, PORTAL_Z, House.ENTER)
                client.menu("construction.enter_house", "construction.enter_building", "construction.enter_friend", "construction.enter_nothing")
                client.chooseOption(2)
                client.await("the house") { client.position?.takeIf { it.x >= 6400 && it.level == House.GROUND } }
                assertEquals(1, server.world.instances.all.size)

                // Owning one, the agent offers the guide book, houses and his cape, and gives no second book.
                client.tele(AGENT_X + 1, AGENT_Z)
                val npc = client.await("the estate agent") { client.npcs().firstOrNull { it.id == EstateAgent.ID } }
                client.npcOp(npc.index, EstateAgent.TALK_TO)
                client.chats("estate_agent.greeting")
                client.menu("estate_agent.guide", "estate_agent.about", "estate_agent.cape")
                client.chooseOption(1)
                client.chats("estate_agent.guide", "estate_agent.guide_has")
                client.awaitClosed()
                assertEquals(1, client.count(EstateAgent.GUIDE_BOOK))
            }
        }
    }

    @Test
    fun `building mode adds a room through a door hotspot, turned to fit, and removes it again`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                client.buyHouse()
                val at = client.goIn(House.BUILD_MODE)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                // The parlour, north of the garden, and its east door hotspot; the kitchen goes in the cell past it.
                val px = instance.baseX + (House.STARTER_X shl 3)
                val pz = instance.baseZ + ((House.STARTER_Z + 1) shl 3)
                val kx = px + 8
                val kitchenZone = Triple(kx shr 3, pz shr 3, House.GROUND)
                val player = server.world.playerNamed("carol")!!

                // Level 1: the menu opens, but the kitchen wants level 5.
                client.locOp(House.DOOR_HOTSPOT_LEFT, px + 7, pz + 3, House.BUILD)
                client.await("the add-room menu") { if (House.ADD_ROOM in client.openInterfaces()) Unit else null }
                client.resumePause(House.ADD_ROOM, House.ADD_ROOM_LIST, RoomType.KITCHEN.id)
                client.await("the level refusal") { client.messages().firstOrNull { it.text == "[construction.room_level level=5]" } }
                assertEquals(2, player.house!!.rooms.size)

                // Level 5 with 5,000 coins: the kitchen's ghost, first turned as it is, its west door on the parlour.
                client.command("setlevel construction 5")
                client.command("item ${EstateAgent.COINS} ${RoomType.KITCHEN.cost}")
                client.await("the coins") { if (client.count(EstateAgent.COINS) == RoomType.KITCHEN.cost) Unit else null }
                client.locOp(House.DOOR_HOTSPOT_LEFT, px + 7, pz + 3, House.BUILD)
                client.await("the add-room menu") { if (House.ADD_ROOM in client.openInterfaces()) Unit else null }
                client.resumePause(House.ADD_ROOM, House.ADD_ROOM_LIST, RoomType.KITCHEN.id)
                client.menu("construction.rotate_clockwise", "construction.rotate_anticlockwise", "construction.build_room", "construction.cancel_room")
                client.await("the ghost") { client.regionZones()?.get(kitchenZone)?.takeIf { it == CopiedZone(234, 887, 0, 0) } }

                // Turned clockwise: its doors west and north still have one on the parlour. The larder space turns too,
                // from 1,6 to 6,5, a quarter round.
                client.chooseOption(1)
                client.await("the turned ghost") { client.regionZones()?.get(kitchenZone)?.takeIf { it == CopiedZone(234, 887, 0, 1) } }
                client.menu("construction.rotate_clockwise", "construction.rotate_anticlockwise", "construction.build_room", "construction.cancel_room")
                val larder = server.world.locs.at(kx + 6, pz + 5, House.GROUND).firstOrNull { it.id == LARDER_SPACE }
                assertEquals(1, assertNotNull(larder, "the larder space at 6,5").angle)
                // A third turn would put its doors east and north: none on the parlour, so clockwise again comes back.
                client.chooseOption(1)
                client.await("back as it is") { client.regionZones()?.get(kitchenZone)?.takeIf { it == CopiedZone(234, 887, 0, 0) } }
                client.menu("construction.rotate_clockwise", "construction.rotate_anticlockwise", "construction.build_room", "construction.cancel_room")
                client.chooseOption(2)
                client.await("turned again") { client.regionZones()?.get(kitchenZone)?.takeIf { it == CopiedZone(234, 887, 0, 1) } }
                client.menu("construction.rotate_clockwise", "construction.rotate_anticlockwise", "construction.build_room", "construction.cancel_room")

                // Built: the coins go, and the house has a kitchen turned once.
                client.chooseOption(3)
                client.awaitClosed()
                client.await("the coins gone") { if (client.count(EstateAgent.COINS) == 0) Unit else null }
                val kitchen = assertNotNull(player.house!!.room(3, 3))
                assertEquals(RoomType.KITCHEN to 1, kitchen.type to kitchen.rotation)

                // Out of building mode it is drawn turned, and the parlour's east doorway is a door onto it.
                assertEquals(1, player.vars.getVarbit(House.BUILDING_MODE_VARBIT))
                client.locOp(House.EXIT_PORTAL, px + 3, pz - 8 + 3, House.EXIT_ENTER)
                client.await("outside") { client.position?.takeIf { it == Coord(OUTSIDE.x, OUTSIDE.z, 0) } }
                // Leaving turns building mode's varbit off again (exploit review R43B-2).
                client.await("building mode off") { if (player.vars.getVarbit(House.BUILDING_MODE_VARBIT) == 0) Unit else null }
                val home = client.goIn(House.HOME)
                val again = assertNotNull(server.world.instances.at(home.x, home.z))
                val hx = again.baseX + (House.STARTER_X shl 3)
                val hz = again.baseZ + ((House.STARTER_Z + 1) shl 3)
                client.await("the kitchen drawn") { client.regionZones()?.get(Triple((hx shr 3) + 1, hz shr 3, House.GROUND))?.takeIf { it == CopiedZone(234, 887, 0, 1) } }
                client.await("the parlour's east door") { if (client.shown(House.DOOR_RIGHT, hx + 7, hz + 3)) Unit else null }

                // In building mode again, Build on that doorway offers to remove the kitchen; yes puts the yard back.
                client.locOp(House.EXIT_PORTAL, hx + 3, hz - 8 + 3, House.EXIT_ENTER)
                client.await("outside") { client.position?.takeIf { it == Coord(OUTSIDE.x, OUTSIDE.z, 0) } }
                val back = client.goIn(House.BUILD_MODE)
                val third = assertNotNull(server.world.instances.at(back.x, back.z))
                val rx = third.baseX + (House.STARTER_X shl 3)
                val rz = third.baseZ + ((House.STARTER_Z + 1) shl 3)
                client.locOp(House.DOOR_HOTSPOT_LEFT, rx + 7, rz + 3, House.BUILD)
                client.menu("construction.remove_yes", "construction.remove_no")
                client.chooseOption(1)
                client.await("the yard back") { client.regionZones()?.get(Triple((rx shr 3) + 1, rz shr 3, House.GROUND))?.takeIf { it == CopiedZone(233, 880, 0, 0) } }
                assertNull(player.house!!.room(3, 3))
                // The garden with the exit portal can't go.
                client.locOp(House.DOOR_HOTSPOT_LEFT, rx + 3, rz, House.BUILD)
                client.await("the refusal") { client.messages().firstOrNull { it.text == "[construction.remove_portal]" } }
                assertEquals(2, player.house!!.rooms.size)
            }
        }
    }

    @Test
    fun `building mode builds a chair on its space from the creation menu, and removes it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready()
                client.buyHouse()
                val at = client.goIn(House.BUILD_MODE)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                val cx = instance.baseX + (House.STARTER_X shl 3) + 2
                val cz = instance.baseZ + ((House.STARTER_Z + 1) shl 3) + 4
                val player = server.world.playerNamed("dave")!!
                fun menuOpen() = client.await("the creation menu") { if (Furniture.MENU in client.openInterfaces()) Unit else null }

                // No hammer or saw: the menu opens, both choices crossed out, and building is refused.
                client.locOp(CHAIR_SPACE, cx, cz, House.BUILD)
                menuOpen()
                client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                client.await("the tools refusal") { client.messages().firstOrNull { it.text == "[construction.furniture_tools]" } }
                // The menu's layout script took the chat's keys (script1406's chatdefault_stopinput); its closing gives them back.
                client.await("chatdefault_restoreinput") {
                    client.clientScripts().count { it.id == AllSettings.CHATDEFAULT_RESTOREINPUT_SCRIPT }.takeIf { it == 1 }
                }

                // With the tools and materials: each slot is drawn by the client's own script, the wooden chair crossed out
                // (level 8), and the list laid out for two.
                for (obj in listOf(HAMMER, SAW)) client.command("item $obj 1")
                client.command("item $PLANK 2")
                client.command("item $STEEL_NAILS 2")
                client.command("item $BRONZE_NAILS 3")
                client.await("the materials") { if (client.count(BRONZE_NAILS) == 3) Unit else null }
                client.locOp(CHAIR_SPACE, cx, cz, House.BUILD)
                menuOpen()
                val scripts = client.await("the menu's slots") { client.clientScripts().filter { it.id == Furniture.ENTRY_SCRIPT }.takeIf { it.size >= 4 } }
                assertEquals(listOf<Any>(1, CRUDE_CHAIR_ROW, 1, "2 x Plank|2 x Nails", 1), scripts[2].args)
                assertEquals(listOf<Any>(2, WOODEN_CHAIR_ROW, 8, "3 x Plank|3 x Nails", 0), scripts[3].args)
                assertEquals(listOf<Any>(2, 0), client.clientScripts().last { it.id == Furniture.LAYOUT_SCRIPT }.args)
                client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 2)
                client.await("the level refusal") { client.messages().firstOrNull { it.text == "[construction.furniture_level level=8]" } }

                // The crude wooden chair: the planks go, and the steel nails before the bronze; 58 XP; built and saved.
                client.locOp(CHAIR_SPACE, cx, cz, House.BUILD)
                menuOpen()
                client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                client.await("the chair") { if (client.shown(CRUDE_CHAIR, cx, cz)) Unit else null }
                assertEquals(listOf(0, 0, 3), listOf(client.count(PLANK), client.count(STEEL_NAILS), client.count(BRONZE_NAILS)))
                client.await("the XP") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.xp == 58 } }
                assertEquals(CRUDE_CHAIR, player.house!!.room(House.STARTER_X, House.STARTER_Z + 1)!!.built[CHAIR_SPACE])

                // Out of building mode the chair is there; in it again, Remove asks, and yes puts the space back.
                client.locOp(House.EXIT_PORTAL, at.x, at.z + 1, House.EXIT_ENTER)
                client.await("outside") { client.position?.takeIf { it == Coord(OUTSIDE.x, OUTSIDE.z, 0) } }
                val home = client.goIn(House.HOME)
                val homeInstance = assertNotNull(server.world.instances.at(home.x, home.z))
                val hx = homeInstance.baseX + (House.STARTER_X shl 3) + 2
                val hz = homeInstance.baseZ + ((House.STARTER_Z + 1) shl 3) + 4
                client.await("the chair at home") { if (client.shown(CRUDE_CHAIR, hx, hz)) Unit else null }
                client.locOp(House.EXIT_PORTAL, home.x, home.z + 1, House.EXIT_ENTER)
                client.await("outside again") { client.position?.takeIf { it == Coord(OUTSIDE.x, OUTSIDE.z, 0) } }
                val back = client.goIn(House.BUILD_MODE)
                val third = assertNotNull(server.world.instances.at(back.x, back.z))
                val rx = third.baseX + (House.STARTER_X shl 3) + 2
                val rz = third.baseZ + ((House.STARTER_Z + 1) shl 3) + 4
                client.locOp(CRUDE_CHAIR, rx, rz, Furniture.REMOVE)
                client.menu("construction.remove_furniture_yes", "construction.remove_furniture_no")
                client.chooseOption(1)
                client.await("the space back") { if (client.shown(CHAIR_SPACE, rx, rz)) Unit else null }
                assertNull(player.house!!.room(House.STARTER_X, House.STARTER_Z + 1)!!.built[CHAIR_SPACE])

                // The three bronze nails left over and one steel make the next chair's two: the steel first, then one bronze,
                // as the menu counts every kind together (Codex review on #869).
                client.command("item $PLANK 2")
                client.command("item $STEEL_NAILS 1")
                client.await("more materials") { if (client.count(STEEL_NAILS) == 1 && client.count(PLANK) == 2) Unit else null }
                client.locOp(CHAIR_SPACE, rx, rz, House.BUILD)
                client.await("the creation menu again") { if (Furniture.MENU in client.openInterfaces()) Unit else null }
                client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                client.await("the second chair") { if (client.shown(CRUDE_CHAIR, rx, rz)) Unit else null }
                assertEquals(listOf(0, 0, 2), listOf(client.count(PLANK), client.count(STEEL_NAILS), client.count(BRONZE_NAILS)))
            }
        }
    }

    @Test
    fun `a rug is built on every rug space at once, each tile its own part, and mixed nails make up a chair`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.ready()
                client.buyHouse()
                val at = client.goIn(House.BUILD_MODE)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                val px = instance.baseX + (House.STARTER_X shl 3)
                val pz = instance.baseZ + ((House.STARTER_Z + 1) shl 3)
                val player = server.world.playerNamed("erin")!!
                client.command("setlevel construction 2")
                for (obj in listOf(HAMMER, SAW)) client.command("item $obj 1")
                client.command("item $BOLT_OF_CLOTH 2")
                client.await("the cloth") { if (client.count(BOLT_OF_CLOTH) == 2) Unit else null }
                val xp = client.await("level 2") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.level == 2 } }.xp

                // Build on the side tile: the corner, the side and the middle each become their part of the brown rug.
                client.locOp(RUG_SIDE_SPACE, px + 2, pz + 1, House.BUILD)
                client.await("the creation menu") { if (Furniture.MENU in client.openInterfaces()) Unit else null }
                client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                val parts = listOf(Triple(BROWN_RUG_CORNER, px + 1, pz + 1), Triple(BROWN_RUG_SIDE, px + 2, pz + 1), Triple(BROWN_RUG_MIDDLE, px + 2, pz + 2))
                client.await("the rug") { if (parts.all { (id, x, z) -> client.shown(id, x, z) }) Unit else null }
                client.await("the XP") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.xp == xp + 30 } }
                val room = player.house!!.room(House.STARTER_X, House.STARTER_Z + 1)!!
                assertEquals(mapOf(RUG_CORNER_SPACE to BROWN_RUG_CORNER, RUG_SIDE_SPACE to BROWN_RUG_SIDE, RUG_MIDDLE_SPACE to BROWN_RUG_MIDDLE), room.built)
                assertEquals(0, client.count(BOLT_OF_CLOTH))

                // Remove on the middle takes the whole rug up.
                client.locOp(BROWN_RUG_MIDDLE, px + 2, pz + 2, Furniture.REMOVE)
                client.menu("construction.remove_furniture_yes", "construction.remove_furniture_no")
                client.chooseOption(1)
                val spaces = listOf(Triple(RUG_CORNER_SPACE, px + 1, pz + 1), Triple(RUG_SIDE_SPACE, px + 2, pz + 1), Triple(RUG_MIDDLE_SPACE, px + 2, pz + 2))
                client.await("the spaces back") { if (spaces.all { (id, x, z) -> client.shown(id, x, z) }) Unit else null }
                assertEquals(emptyMap(), room.built)

                // One steel nail and one bronze make the crude chair's two, the steel taken first.
                client.command("item $PLANK 2")
                client.command("item $STEEL_NAILS 1")
                client.command("item $BRONZE_NAILS 1")
                client.await("the materials") { if (client.count(BRONZE_NAILS) == 1 && client.count(PLANK) == 2) Unit else null }
                client.locOp(CHAIR_SPACE, px + 2, pz + 4, House.BUILD)
                client.await("the creation menu again") { if (Furniture.MENU in client.openInterfaces()) Unit else null }
                client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                client.await("the chair") { if (client.shown(CRUDE_CHAIR, px + 2, pz + 4)) Unit else null }
                assertEquals(listOf(0, 0, 0), listOf(client.count(PLANK), client.count(STEEL_NAILS), client.count(BRONZE_NAILS)))
            }
        }
    }

    @Test
    fun `a bookcase builds with its scroll rack, a fireplace on its own, and curtains on every window at once`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("finn").use { client ->
                client.ready()
                client.buyHouse()
                val at = client.goIn(House.BUILD_MODE)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                val px = instance.baseX + (House.STARTER_X shl 3)
                val pz = instance.baseZ + ((House.STARTER_Z + 1) shl 3)
                val room = server.world.playerNamed("finn")!!.house!!.room(House.STARTER_X, House.STARTER_Z + 1)!!
                client.command("setlevel construction 4")
                for (obj in listOf(HAMMER, SAW)) client.command("item $obj 1")
                client.command("item $PLANK 7")
                client.command("item $STEEL_NAILS 7")
                client.command("item $BOLT_OF_CLOTH 3")
                client.command("item $SOFT_CLAY 3")
                client.await("the materials") { if (client.count(SOFT_CLAY) == 3 && client.count(BOLT_OF_CLOTH) == 3) Unit else null }
                var xp = client.await("level 4") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.level == 4 } }.xp

                fun build(space: Int, x: Int, z: Int, gain: Int, vararg shown: Triple<Int, Int, Int>) {
                    client.locOp(space, x, z, House.BUILD)
                    client.await("the creation menu") { if (Furniture.MENU in client.openInterfaces()) Unit else null }
                    client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                    client.await("$space built") { if (shown.all { (id, sx, sz) -> client.shown(id, sx, sz) }) Unit else null }
                    xp += gain
                    client.await("the XP for $space") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.xp == xp } }
                }

                // The bookcase and, on the space beside it, its scroll rack.
                build(BOOKCASE_SPACE, px + 6, pz + 2, 115, Triple(WOODEN_BOOKCASE, px + 6, pz + 2), Triple(WOODEN_SCROLLS, px + 6, pz + 3))
                assertEquals(mapOf(BOOKCASE_SPACE to WOODEN_BOOKCASE, SCROLL_SPACE to WOODEN_SCROLLS), room.built)
                build(FIREPLACE_SPACE, px + 4, pz + 6, 30, Triple(CLAY_FIREPLACE, px + 4, pz + 6))
                // One build hangs curtains on both windows.
                build(CURTAIN_SPACE, px + 2, pz + 7, 132, Triple(TORN_CURTAINS, px + 2, pz + 7), Triple(TORN_CURTAINS, px + 5, pz + 7))
                assertEquals(TORN_CURTAINS, room.built[CURTAIN_SPACE])
                assertEquals(listOf(0, 0, 0, 0), listOf(client.count(PLANK), client.count(STEEL_NAILS), client.count(BOLT_OF_CLOTH), client.count(SOFT_CLAY)))

                // Remove on the scroll rack takes the bookcase down too; Remove on the far window takes both curtains.
                client.locOp(WOODEN_SCROLLS, px + 6, pz + 3, Furniture.REMOVE)
                client.menu("construction.remove_furniture_yes", "construction.remove_furniture_no")
                client.chooseOption(1)
                client.await("the bookcase spaces back") { if (client.shown(BOOKCASE_SPACE, px + 6, pz + 2) && client.shown(SCROLL_SPACE, px + 6, pz + 3)) Unit else null }
                client.locOp(TORN_CURTAINS, px + 5, pz + 7, Furniture.REMOVE)
                client.menu("construction.remove_furniture_yes", "construction.remove_furniture_no")
                client.chooseOption(1)
                client.await("the curtain spaces back") { if (client.shown(CURTAIN_SPACE, px + 2, pz + 7) && client.shown(CURTAIN_SPACE, px + 5, pz + 7)) Unit else null }
                assertEquals(mapOf(FIREPLACE_SPACE to CLAY_FIREPLACE), room.built)
            }
        }
    }

    @Test
    fun `a wooden larder is built in the kitchen, and Search gives its tea leaves and milk without end`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("finn").use { client ->
                client.ready()
                client.buyHouse()
                val player = server.world.playerNamed("finn")!!
                // A kitchen east of the parlour, as building mode would add it (the rooms test builds one that way).
                player.house!!.rooms += HouseRoom(RoomType.KITCHEN, House.STARTER_X + 1, House.STARTER_Z + 1)
                client.command("setlevel construction 9")
                for (obj in listOf(HAMMER, SAW)) client.command("item $obj 1")
                client.command("item $PLANK 8")
                client.command("item $STEEL_NAILS 8")
                client.await("the materials") { if (client.count(STEEL_NAILS) == 8 && client.count(PLANK) == 8) Unit else null }
                val xp = client.await("level 9") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.level == 9 } }.xp
                val at = client.goIn(House.BUILD_MODE)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                val lx = instance.baseX + ((House.STARTER_X + 1) shl 3) + 1
                val lz = instance.baseZ + ((House.STARTER_Z + 1) shl 3) + 6

                client.locOp(LARDER_SPACE, lx, lz, House.BUILD)
                client.await("the creation menu") { if (Furniture.MENU in client.openInterfaces()) Unit else null }
                client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                client.await("the larder") { if (client.shown(WOODEN_LARDER, lx, lz)) Unit else null }
                client.await("the XP") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.xp == xp + 228 } }

                // Search: the Make-X box of its supplies; two tea leaves, then a bucket of milk.
                client.locOp(WOODEN_LARDER, lx, lz, Kitchen.SEARCH)
                client.await("the supplies box") { if (Chatbox.SKILLMULTI in client.openInterfaces()) Unit else null }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                client.await("the tea leaves") { if (client.count(TEA_LEAVES) == 2) Unit else null }
                client.locOp(WOODEN_LARDER, lx, lz, Kitchen.SEARCH)
                client.await("the supplies box again") { if (Chatbox.SKILLMULTI in client.openInterfaces()) Unit else null }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + 1, 1)
                client.await("the milk") { if (client.count(MILK) == 1) Unit else null }
                assertEquals(2, client.count(TEA_LEAVES))
            }
        }
    }

    private companion object {
        const val CHAIR_SPACE = 4515
        const val CRUDE_CHAIR = 6752
        const val CRUDE_CHAIR_ROW = 5517
        const val WOODEN_CHAIR_ROW = 5518
        const val PLANK = 960
        const val BOLT_OF_CLOTH = 8790
        const val WOODEN_LARDER_ROW = 5562
        const val WOODEN_LARDER = 13565
        const val TEA_LEAVES = 7738
        const val MILK = 1927
        const val RUG_MIDDLE_SPACE = 4518
        const val RUG_SIDE_SPACE = 4519
        const val RUG_CORNER_SPACE = 4520
        const val BROWN_RUG_ROW = 5524
        const val BROWN_RUG_CORNER = 6759
        const val BROWN_RUG_SIDE = 6760
        const val BROWN_RUG_MIDDLE = 6761
        const val BOOKCASE_SPACE = 4521
        const val SCROLL_SPACE = 4522
        const val FIREPLACE_SPACE = 4523
        const val CURTAIN_SPACE = 4524
        const val WOODEN_BOOKCASE_ROW = 5529
        const val CLAY_FIREPLACE_ROW = 5532
        const val TORN_CURTAINS_ROW = 5535
        const val WOODEN_BOOKCASE = 6768
        const val WOODEN_SCROLLS = 6771
        const val CLAY_FIREPLACE = 6780
        const val TORN_CURTAINS = 6774
        const val SOFT_CLAY = 1761
        const val STEEL_NAILS = 1539
        const val BRONZE_NAILS = 4819
        const val HAMMER = 2347
        const val SAW = 8794

        /** The kitchen's `poh_larder` space; its size here is the test's own. */
        const val LARDER_SPACE = 15403
        const val PORTAL_X = 2951
        const val PORTAL_Z = 3222
        val OUTSIDE = House.RIMMINGTON_OUTSIDE
        const val AGENT_X = 3240
        const val AGENT_Z = 3476
    }
}
