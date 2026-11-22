package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GroundObjs
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjSpawn
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.PhoenixGang
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Post-quest content step 2 through the headless client: Shield of Arrav as a Phoenix Gang member leaves it. Straven
 * hands a member with no weapon store key a spare, then talks of the treasures and thieving; the hideout's door lets a
 * member through both ways and its ladder climbs to the alley; the chest is empty; the weapon store's door wants the
 * key from the alley; the Weaponsmaster talks and stops a member taking Straven's crossbows; Katrine sends a member away
 * and her house's inner door is locked to them. The rl-client script test-runner/examples/phoenix-gang.json does the
 * same in the real client.
 */
class HeadlessPhoenixGangTest {
    private val key = ObjType(PhoenixGang.WEAPON_STORE_KEY, "Weapon store key")
    private val crossbow = ObjType(PhoenixGang.PHOENIX_CROSSBOW, "Phoenix crossbow")

    private val talk = listOf("Talk-to", null, null, null, null)
    private val straven = NpcType(PhoenixGang.STRAVEN, "Straven", ops = talk, internalName = "straven")
    private val katrine = NpcType(PhoenixGang.KATRINE, "Katrine", ops = talk, internalName = "katrine")
    private val weaponsmaster = NpcType(PhoenixGang.WEAPONSMASTER, "Weaponsmaster", ops = talk, internalName = "weaponsmaster_vis")
    /** The store's watcher as the map spawns it (multi-NPC 5211; a plain type here). */
    private val watcher = NpcType(PhoenixGang.WEAPONSMASTER_SPAWN, "Weaponsmaster", ops = talk, internalName = "weaponsmaster")

    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = (ops.toList() + List(5 - ops.size) { null }), collision = LocCollisionType(width = 1, length = 1))

    private val types = LocTypes(
        listOf(
            type(PhoenixGang.HIDEOUT_LADDER, "Ladder", "Climb-up"),
            type(PhoenixGang.HIDEOUT_DOOR, "Door", "Open"),
            type(PhoenixGang.STORE_DOOR, "Door", "Open"),
            type(PhoenixGang.BLACK_ARM_DOOR, "Door", "Open"),
            type(PhoenixGang.DOOR_OPEN, "Door", "Close"),
            type(PhoenixGang.CHEST, "Chest", "Open"),
            type(PhoenixGang.CHEST_OPEN, "Chest", null),
        ).associateBy { it.id },
    )

    private val hideoutDoor = ZoneLoc(PhoenixGang.HIDEOUT_DOOR, LocShape.WALL_STRAIGHT, 1, 3247, 9779, 0)
    private val storeDoor = ZoneLoc(PhoenixGang.STORE_DOOR, LocShape.WALL_STRAIGHT, 3, 3251, 3386, 0)
    private val blackArmDoor = ZoneLoc(PhoenixGang.BLACK_ARM_DOOR, LocShape.WALL_STRAIGHT, 3, 3185, 3388, 0)
    private val ladder = ZoneLoc(PhoenixGang.HIDEOUT_LADDER, LocShape.CENTREPIECE_STRAIGHT, 0, 3244, 9783, 0)
    private val chest = ZoneLoc(PhoenixGang.CHEST, LocShape.CENTREPIECE_STRAIGHT, 0, 3238, 9761, 0)
    /** A copy of the hideout's ladder type elsewhere, which mustn't climb anywhere. */
    private val strayLadder = ZoneLoc(PhoenixGang.HIDEOUT_LADDER, LocShape.CENTREPIECE_STRAIGHT, 0, 3240, 3390, 0)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3247 shr 6, 9779 shr 6); markLoaded(3238 shr 6, 9761 shr 6)
                markLoaded(3251 shr 6, 3386 shr 6); markLoaded(3185 shr 6, 3388 shr 6); markLoaded(3240 shr 6, 3390 shr 6)
            },
            objTypes = ObjTypes.of(key, crossbow), locTypes = types,
            staticLocs = StaticLocs().apply { listOf(hideoutDoor, storeDoor, blackArmDoor, ladder, chest, strayLadder).forEach(::add) },
            npcTypes = NpcTypes.of(straven, katrine, weaponsmaster, watcher),
            npcSpawns = listOf(
                NpcSpawn(straven, 3246, 9780, wander = 0, source = "test"),
                NpcSpawn(katrine, 3186, 3385, wander = 0, source = "test"),
                NpcSpawn(watcher, 3248, 3384, level = 1, wander = 0, source = "test"),
                NpcSpawn(weaponsmaster, 3250, 3390, wander = 0, source = "test"),
            ),
            cycleMillis = millis,
        ).also {
            it.groundObjs.addSpawn(ObjSpawn(PhoenixGang.PHOENIX_CROSSBOW, 1, 3243, 3383, 1, 100))
            it.groundObjs.addSpawn(ObjSpawn(PhoenixGang.PHOENIX_CROSSBOW, 1, 3256, 3390, 0, 100))
        }
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int = 0) =
        assertEquals(Coord(x, z, level), await("$x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } })

    private fun HeadlessClient.chat(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    private fun HeadlessClient.pick(option: Int, vararg keys: String) {
        val texts = keys.map { "[$it]" }
        awaitDialogue("the menu $texts") { it is DialogueView.Menu && it.options == texts }
        chooseOption(option)
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.message(key: String) = await(key) { messages().firstOrNull { it.text == "[$key]" } }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    /** Talks to [id] through [steps] (keys under [prefix], or "n:key1,key2" to pick option n). */
    private fun HeadlessClient.talk(id: Int, prefix: String, vararg steps: String) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, PhoenixGang.TALK_TO)
        for (step in steps) {
            val menu = step.substringBefore(':', "")
            if (menu.isEmpty()) chat("$prefix.$step") else pick(menu.toInt(), *step.substringAfter(':').split(',').map { "$prefix.$it" }.toTypedArray())
        }
    }

    @Test
    fun `Straven hands a member a spare key, then talks, and the hideout door, chest and ladder work`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3245, 9780)
                client.talk(straven.id, "straven", "greeting", "lost_key", "lost_key_1")
                client.await("the key handed over") {
                    if (client.dialogueView is DialogueView.Other && client.text(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_TEXT) == "[straven.hands_key]") Unit else null
                }
                client.resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
                client.over()
                assertEquals(1, client.count(PhoenixGang.WEAPON_STORE_KEY))
                val menu = "treasures,thieving,go"
                client.talk(
                    straven.id, "straven", "greeting",
                    "1:$menu", "treasures", "treasures_1", "treasures_2", "treasures_3", "treasures_4", "treasures_5", "treasures_6",
                    "2:$menu", "thieving", "thieving_1",
                    "3:$menu", "go",
                )
                client.over()
                assertEquals(1, client.count(PhoenixGang.WEAPON_STORE_KEY))

                // Through the hideout's door south, and back north.
                client.tele(3247, 9780)
                client.locOp(hideoutDoor.id, hideoutDoor.x, hideoutDoor.z, PhoenixGang.OP1)
                client.awaitAt(3247, 9779)
                val world = server.world
                client.await("the door shut") { world.locs[3247, 9779, 0, 0]?.takeIf { it.id == PhoenixGang.HIDEOUT_DOOR } }
                client.locOp(hideoutDoor.id, hideoutDoor.x, hideoutDoor.z, PhoenixGang.OP1)
                client.awaitAt(3247, 9780)

                // The chest opens on nothing, and shuts again.
                client.tele(3238, 9762)
                client.locOp(chest.id, chest.x, chest.z, PhoenixGang.OP1)
                client.message("gang_house.chest_search")
                client.message("gang_house.chest_nothing")
                client.await("the chest open") { world.locs[3238, 9761, 0, LAYER_CENTREPIECE]?.takeIf { it.id == PhoenixGang.CHEST_OPEN } }
                client.await("the chest shut") { world.locs[3238, 9761, 0, LAYER_CENTREPIECE]?.takeIf { it.id == PhoenixGang.CHEST } }

                // Up the ladder to the alley.
                client.tele(3244, 9782)
                client.locOp(ladder.id, ladder.x, ladder.z, PhoenixGang.OP1)
                // Beside the street's ladder, on the side the player stood.
                client.awaitAt(3244, 3382)
            }
        }
    }

    @Test
    fun `a key-holder crosses the store door without it opening, so no one follows, and a stray ladder copy goes nowhere`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { a ->
                server.login("dave").use { b ->
                    for (client in listOf(a, b)) {
                        client.await("the login rebuild") { client.position }
                        client.tele(3251, 3387)
                    }
                    a.command("item ${PhoenixGang.WEAPON_STORE_KEY} 1")
                    a.await("the key") { if (a.count(PhoenixGang.WEAPON_STORE_KEY) == 1) Unit else null }
                    val world = server.world
                    a.locOp(storeDoor.id, storeDoor.x, storeDoor.z, PhoenixGang.OP1)
                    b.locOp(storeDoor.id, storeDoor.x, storeDoor.z, PhoenixGang.OP1)
                    // The door stays shut while the key-holder crosses.
                    repeat(4) {
                        assertEquals(PhoenixGang.STORE_DOOR, world.locs[3251, 3386, 0, 0]?.id, "the store door shut")
                        a.awaitTicks(1)
                    }
                    a.awaitAt(3251, 3385)
                    b.message("gang_house.securely_locked")
                    b.awaitTicks(3)
                    // Outside is the door's own tile and north of it; the store is south of its edge.
                    assertEquals(true, b.position!!.z >= 3386, "the keyless player still outside: ${b.position}")

                    b.tele(3240, 3391)
                    b.locOp(strayLadder.id, strayLadder.x, strayLadder.z, PhoenixGang.OP1)
                    b.message("game.nothing_interesting")
                    b.awaitTicks(3)
                    assertEquals(0, b.position?.level)
                    assertEquals(true, b.position!!.z in 3380..3400, "not sent south")
                }
            }
        }
    }

    @Test
    fun `the weapon store wants the key from the alley, and the Weaponsmaster guards Straven's crossbows`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3251, 3387)
                client.locOp(storeDoor.id, storeDoor.x, storeDoor.z, PhoenixGang.OP1)
                client.message("gang_house.securely_locked")
                client.awaitTicks(2)
                assertEquals(Coord(3251, 3386, 0), client.position)

                // A banked key doesn't open it.
                server.world.playerNamed("bob")!!.bank.add(key, 1)
                client.locOp(storeDoor.id, storeDoor.x, storeDoor.z, PhoenixGang.OP1)
                client.message("gang_house.securely_locked")
                client.awaitTicks(2)
                assertEquals(Coord(3251, 3386, 0), client.position)

                client.command("item ${PhoenixGang.WEAPON_STORE_KEY} 1")
                client.await("the key") { if (client.count(PhoenixGang.WEAPON_STORE_KEY) == 1) Unit else null }
                client.locOp(storeDoor.id, storeDoor.x, storeDoor.z, PhoenixGang.OP1)
                client.awaitAt(3251, 3385)
                // Out again from inside, key or not.
                client.command("empty")
                client.await("no key") { if (client.count(PhoenixGang.WEAPON_STORE_KEY) == 0) Unit else null }
                val world = server.world
                client.await("the door shut") { world.locs[3251, 3386, 0, 0]?.takeIf { it.id == PhoenixGang.STORE_DOOR } }
                client.locOp(storeDoor.id, storeDoor.x, storeDoor.z, PhoenixGang.OP1)
                client.awaitAt(3251, 3386)

                client.tele(3250, 3389)
                client.talk(weaponsmaster.id, "weaponsmaster", "hello", "greeting", "1:weapons,treasure", "weapons", "weapons_1")
                client.over()
                client.talk(weaponsmaster.id, "weaponsmaster", "hello", "greeting", "2:weapons,treasure", "treasure", "treasure_1")
                client.over()

                // Upstairs, under the Weaponsmaster's eye, the crossbow stays put.
                client.tele(3243, 3384, 1)
                client.objOp(PhoenixGang.PHOENIX_CROSSBOW, 3243, 3383, GroundObjs.TAKE_OPTION)
                client.chat("weaponsmaster.stravens")
                client.over()
                assertEquals(0, client.count(PhoenixGang.PHOENIX_CROSSBOW))
                // One lying anywhere else takes as any item does.
                client.tele(3256, 3391)
                client.objOp(PhoenixGang.PHOENIX_CROSSBOW, 3256, 3390, GroundObjs.TAKE_OPTION)
                client.await("the crossbow taken") { if (client.count(PhoenixGang.PHOENIX_CROSSBOW) == 1) Unit else null }
            }
        }
    }

    @Test
    fun `Katrine sends a member away, and her inner door is locked to them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3185, 3386)
                client.talk(katrine.id, "katrine", "phoenix_scum")
                client.over()
                client.tele(3185, 3387)
                client.locOp(blackArmDoor.id, blackArmDoor.x, blackArmDoor.z, PhoenixGang.OP1)
                client.message("gang_house.securely_locked")
                client.awaitTicks(2)
                assertEquals(Coord(3185, 3387, 0), client.position)
                // From the back of the house, out.
                client.tele(3185, 3388)
                client.locOp(blackArmDoor.id, blackArmDoor.x, blackArmDoor.z, PhoenixGang.OP1)
                client.awaitAt(3185, 3387)
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        /** The loc layer of centrepieces: walls 0, wall decoration 1, centrepieces 2, ground decor 3. */
        const val LAYER_CENTREPIECE = 2
    }
}
