package osrsps.testclient

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
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.construction.EstateAgent
import osrsps.game.skill.construction.House
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

    private fun door(id: Int, op: String) = LocType(id, "Door", ops = listOf(op, null, null, null, null), collision = LocCollisionType(width = 1, length = 1))

    private val types = listOf(
        LocType(House.RIMMINGTON_PORTAL, "Portal", ops = listOf("Enter", "Home", "Build mode", "Friend's house", null), collision = LocCollisionType(width = 5, length = 2)),
        LocType(House.EXIT_PORTAL, "Portal", ops = listOf("Enter", "Lock", "Remove board advert", null, "Remove"), collision = LocCollisionType(width = 2, length = 2)),
        hotspot(House.CENTREPIECE_SPACE, "Centrepiece space", 2),
        hotspot(House.DOOR_HOTSPOT_LEFT, "Door hotspot"),
        hotspot(House.DOOR_HOTSPOT_RIGHT, "Door hotspot"),
        hotspot(CHAIR_SPACE, "Chair space"),
        LocType(LARDER_SPACE, "Larder space", ops = listOf(null, null, null, null, House.BUILD_OP), collision = LocCollisionType(width = 2, length = 1, interactType = 0)),
        LocType(House.WALL, "null", collision = LocCollisionType(width = 1, length = 1)),
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
        val objs = arrayOfNulls<ObjType>(EstateAgent.GUIDE_BOOK + 1)
        objs[EstateAgent.COINS] = ObjType(EstateAgent.COINS, "Coins", stackable = true)
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
                doors(parlour.templateX, parlour.templateZ, listOf(0, 2, 3)).forEach(::add)
                // The kitchen's doors are west and south; a 2 by 1 space in its north-west shows how a turned room moves its locs.
                doors(kitchen.templateX, kitchen.templateZ, listOf(0, 3)).forEach(::add)
                add(ZoneLoc(LARDER_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, kitchen.templateX + 1, kitchen.templateZ + 6, 0))
            },
            cycleMillis = millis,
        )
    }

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
                client.await("the house's own locs") { if (client.shown(House.EXIT_PORTAL, gardenX + 3, gardenZ + 3)) Unit else null }
                assertTrue(client.shown(-1, gardenX + 3, gardenZ + 7), "the garden's north door hotspot goes")
                assertTrue(client.shown(-1, gardenX + 2, parlourZ + 4), "the chair space goes")
                // Its right leaf is on the left hotspot's tile: the double door's leaves go clockwise along the wall.
                assertTrue(client.shown(House.DOOR_RIGHT, gardenX + 3, parlourZ), "the parlour's south door")
                assertTrue(client.shown(House.DOOR_LEFT, gardenX + 4, parlourZ), "the parlour's south door")
                assertTrue(client.shown(House.WALL, gardenX, parlourZ + 4), "the parlour's west wall")
                assertTrue(client.shown(House.WALL, gardenX + 7, parlourZ + 3), "the parlour's east wall")

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
                client.locOp(House.EXIT_PORTAL, px + 3, pz - 8 + 3, House.EXIT_ENTER)
                client.await("outside") { client.position?.takeIf { it == Coord(OUTSIDE.x, OUTSIDE.z, 0) } }
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

    private companion object {
        const val CHAIR_SPACE = 4515

        /** The kitchen's `poh_larder` space; its size here is the test's own. */
        const val LARDER_SPACE = 15403
        const val PORTAL_X = 2951
        const val PORTAL_Z = 3222
        val OUTSIDE = House.RIMMINGTON_OUTSIDE
        const val AGENT_X = 3240
        const val AGENT_Z = 3476
    }
}
