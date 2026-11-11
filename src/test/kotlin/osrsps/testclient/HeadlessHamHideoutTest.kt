package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.HamHideout
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Pickpocketing
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Misthalin piece 7 step 4a through the headless client: the trapdoor locked, picked open (a fail numbing Thieving) and
 * climbed down, the ladder back up, a H.A.M. member pickpocketed for 22.2 XP until the third concussion bundles the
 * thief out, and Jimmy's cell door picked from inside. The rl-client script test-runner/examples/ham-hideout.json does
 * the same in the real hideout.
 */
class HeadlessHamHideoutTest {
    private fun type(id: Int, name: String, ops: List<String?>, multiloc: Boolean = false) =
        LocType(id, name, ops = ops, collision = LocCollisionType(width = 1, length = 1, interactType = 0), multiloc = multiloc)

    private val trapdoor = ZoneLoc(HamHideout.TRAPDOOR, LocShape.GROUND_DECOR, 1, 3166, 3252, 0)
    private val ladder = ZoneLoc(HamHideout.LADDER, LocShape.CENTREPIECE_STRAIGHT, 0, 3149, 9653, 0)
    private val cellDoor = ZoneLoc(HamHideout.CELL_DOOR, LocShape.WALL_STRAIGHT, 0, 3183, 9611, 0)

    private val member = NpcType(MEMBER, "H.A.M. Member", ops = listOf("Pickpocket", null, null, null, null), internalName = "favour_male_ham_civilian")

    private fun server(dir: Path, seed: Long = 3) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Pickpocketing.HAM_POUCH + 1)
        for (loot in Pickpocketing.HAM_LOOT) if (loot.obj >= 0) objs[loot.obj] = ObjType(loot.obj, "Loot ${loot.obj}", stackable = true)
        for ((i, id) in HamHideout.CLOTHING.withIndex()) objs[id] = ObjType(id, "Ham $i", iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = WearPos.BODY)
        objs[HamHideout.LOCKPICK] = ObjType(HamHideout.LOCKPICK, "Lockpick")
        objs[Pickpocketing.HAM_POUCH] = ObjType(Pickpocketing.HAM_POUCH, "Coin pouch", stackable = true, iops = listOf("Open-all", "Open", null, null, "Destroy"))
        objs[COINS] = ObjType(COINS, "Coins", stackable = true)
        World(
            crcs,
            CollisionMap().apply { markLoaded(3166 shr 6, 3252 shr 6); markLoaded(3149 shr 6, 9653 shr 6); markLoaded(3183 shr 6, 9611 shr 6) },
            // ham_thief, bits 14 and 15 of marketthief (174), as the 242 cache has it.
            varTypes = VarTypes(6_000, mapOf(HamHideout.TRAPDOOR_VARBIT to VarbitType(174, 14, 15))),
            random = GameRandom(seed), objTypes = ObjTypes(objs),
            locTypes = LocTypes(
                listOf(
                    // A multiloc, as the cache's: its options are whichever its shut or open form shows.
                    type(HamHideout.TRAPDOOR, "Trapdoor", listOf(null, null, null, null, null), multiloc = true),
                    type(HamHideout.LADDER, "Ladder", listOf("Climb-up", null, null, null, null)),
                    type(HamHideout.CELL_DOOR, "Door", listOf("Open", null, null, null, "Pick-lock")),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { listOf(trapdoor, ladder, cellDoor).forEach(::add) },
            npcTypes = NpcTypes.of(member), npcSpawns = listOf(NpcSpawn(member, 3150, 9650, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int) = await("$x, $z") { position?.takeIf { it == Coord(x, z, 0) } }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    private fun HeadlessClient.awaitSeen(text: String, times: Int = 1) = await("$text x$times") { if (seen(text) >= times) Unit else null }

    private fun HeadlessClient.setLevel(skill: String, id: Int, level: Int) {
        command("setlevel $skill $level")
        await("$skill $level") { stat(id)?.takeIf { it.level == level } }
    }

    /** Picks [loc]'s lock until it works or [tries] run out: how many failed first. */
    private fun HeadlessClient.pickUntilDone(loc: ZoneLoc, tries: Int = 20): Int {
        repeat(tries) { failed ->
            val done = seen(DONE)
            val fails = seen(FAIL)
            locOp(loc.id, loc.x, loc.z, HamHideout.OP_PICK)
            val worked = await("the pick's result") {
                when {
                    seen(DONE) > done -> true
                    seen(FAIL) > fails -> false
                    else -> null
                }
            }
            if (worked) return failed
        }
        error("no pick worked in $tries tries")
    }

    @Test
    fun `the trapdoor is locked until picked, a fail numbs Thieving, and it leads down to the ladder and back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val alice = server.world.playerNamed("alice")!!
                client.tele(HamHideout.SURFACE.first, HamHideout.SURFACE.second)
                client.locOp(trapdoor.id, trapdoor.x, trapdoor.z, HamHideout.OP_OPEN_OR_CLIMB)
                client.awaitSeen("[ham_hideout.trapdoor_locked]")

                // Thieving 2 with no lockpick: 4%, so a fail first, and it takes Thieving down 1; at 1 a fail leaves it at 1.
                client.setLevel("thieving", THIEVING, 2)
                client.locOp(trapdoor.id, trapdoor.x, trapdoor.z, HamHideout.OP_PICK)
                client.awaitSeen("[ham_hideout.pick]")
                client.awaitSeen(FAIL)
                client.await("Thieving numbed to 1") { client.stat(THIEVING)?.takeIf { it.level == 1 } }
                client.locOp(trapdoor.id, trapdoor.x, trapdoor.z, HamHideout.OP_PICK)
                client.awaitSeen("[ham_hideout.pick]", 2)
                client.awaitSeen(FAIL, 2)
                assertEquals(1, alice.skills.currentLevel(osrsps.game.Skill.THIEVING), "never below 1")

                // Thieving 99 and a lockpick: 99%.
                client.setLevel("thieving", THIEVING, 99)
                client.command("item ${HamHideout.LOCKPICK} 1")
                val xp = client.stat(THIEVING)!!.xp
                client.pickUntilDone(trapdoor)
                assertTrue(HamHideout.trapdoorOpen(alice))
                assertEquals(1, alice.vars.getVarbit(HamHideout.TRAPDOOR_VARBIT), "shown open")
                client.await("3.8 XP") { client.stat(THIEVING)?.takeIf { it.xp >= xp + 3 } }

                client.locOp(trapdoor.id, trapdoor.x, trapdoor.z, HamHideout.OP_OPEN_OR_CLIMB)
                client.awaitAt(HamHideout.INSIDE.first, HamHideout.INSIDE.second)
                client.locOp(ladder.id, ladder.x, ladder.z, 1)
                client.awaitAt(HamHideout.SURFACE.first, HamHideout.SURFACE.second)

                client.locOp(trapdoor.id, trapdoor.x, trapdoor.z, HamHideout.OP_CLOSE)
                client.await("closed") { if (!HamHideout.trapdoorOpen(alice)) Unit else null }
                assertEquals(0, alice.vars.getVarbit(HamHideout.TRAPDOOR_VARBIT))
            }
        }
    }

    @Test
    fun `a member gives 22 XP and loot, and the third concussion bundles the thief out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val bob = server.world.playerNamed("bob")!!
                client.commandReply("god") { it.startsWith("You can") }
                client.setLevel("thieving", THIEVING, 15)
                client.setLevel("agility", AGILITY, 1)
                client.tele(3150, 9651)
                val view = client.await("the member in view") { client.npcs().firstOrNull { it.id == MEMBER } }
                var won = false
                repeat(80) {
                    if (client.seen(BUNDLED) > 0) return@repeat
                    val wins = client.seen("[pickpocket.success who=H.A.M. member]")
                    val fails = client.seen("[pickpocket.fail who=H.A.M. member]")
                    val xp = client.stat(THIEVING)!!.xp
                    client.await("the wait to end") { if (server.world.tick >= bob.nextPickpocketTick && !server.world.statusEffects.isStunned(bob)) Unit else null }
                    client.npcOp(view.index, Pickpocketing.HAM_PICKPOCKET)
                    val worked = client.await("the pickpocket's result") {
                        when {
                            client.seen("[pickpocket.success who=H.A.M. member]") > wins -> true
                            client.seen("[pickpocket.fail who=H.A.M. member]") > fails -> false
                            else -> null
                        }
                    }
                    if (worked) {
                        won = true
                        client.await("22.2 XP") { client.stat(THIEVING)?.takeIf { it.xp in xp + 22..xp + 23 } }
                        Unit
                    } else {
                        // The stunned message gives way to the concussion's (or Agility's).
                        client.await("the blow") { if (client.seen(CONCUSSED) + client.seen(AVOIDED) > 0) Unit else null }
                        assertEquals(0, client.seen("[pickpocket.stunned]"))
                    }
                }
                assertTrue(won, "a pickpocket at 53% works in 80 tries")
                client.awaitSeen(BUNDLED)
                assertTrue(client.seen(CONCUSSED) >= HamHideout.CONCUSSIONS_OUT)
                val out = client.await("out") { client.position?.takeIf { it != Coord(3150, 9651, 0) && it.z != 9650 } }
                assertTrue(
                    out == Coord(HamHideout.SURFACE.first, HamHideout.SURFACE.second, 0) || out == Coord(HamHideout.CELL.first, HamHideout.CELL.second, 0),
                    "on the surface or in the cell: $out",
                )
                assertEquals(0, bob.charges[HamHideout.CONCUSSIONS], "the count starts again")
            }
        }
    }

    @Test
    fun `Open-all on an admin's huge stack of H,A,M, pouches opens at most 28, each 1 to 21 coins`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${Pickpocketing.HAM_POUCH} 1000000")
                val slot = client.await("the pouches") { client.inventory(BACKPACK).indexOfFirst { it?.id == Pickpocketing.HAM_POUCH }.takeIf { it >= 0 } }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 2, slot, Pickpocketing.HAM_POUCH)
                client.await("opened") { if (client.count(Pickpocketing.HAM_POUCH) < 1_000_000) Unit else null }
                assertEquals(1_000_000 - Pickpocketing.MAX_POUCHES, client.count(Pickpocketing.HAM_POUCH))
                val coins = client.await("the coins") { client.count(COINS).takeIf { it > 0 } }
                assertTrue(coins in Pickpocketing.MAX_POUCHES..Pickpocketing.MAX_POUCHES * 21, "$coins")
            }
        }
    }

    @Test
    fun `a catch that kills counts no concussion and moves no one`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                val dave = server.world.playerNamed("dave")!!
                client.setLevel("thieving", THIEVING, 15)
                client.setLevel("agility", AGILITY, 1)
                client.setLevel("hitpoints", HITPOINTS, 1)
                dave.charges[HamHideout.CONCUSSIONS] = HamHideout.CONCUSSIONS_OUT - 1
                client.tele(3150, 9651)
                val view = client.await("the member in view") { client.npcs().firstOrNull { it.id == MEMBER } }
                repeat(80) {
                    if (dave.dead) return@repeat
                    client.await("the wait to end") { if (server.world.tick >= dave.nextPickpocketTick) Unit else null }
                    val fails = client.seen("[pickpocket.fail who=H.A.M. member]")
                    val wins = client.seen("[pickpocket.success who=H.A.M. member]")
                    client.npcOp(view.index, Pickpocketing.HAM_PICKPOCKET)
                    client.await("the pickpocket's result") {
                        if (client.seen("[pickpocket.success who=H.A.M. member]") > wins || client.seen("[pickpocket.fail who=H.A.M. member]") > fails) Unit else null
                    }
                }
                assertTrue(dave.dead, "a catch in 80 tries kills at 1 hitpoint")
                assertEquals(0, client.seen(CONCUSSED) + client.seen(AVOIDED) + client.seen(BUNDLED))
                assertEquals(HamHideout.CONCUSSIONS_OUT - 1, dave.charges[HamHideout.CONCUSSIONS])
                assertEquals(3150 to 9651, dave.x to dave.z, "died where caught")
            }
        }
    }

    @Test
    fun `Jimmy's cell door is locked, and one Pick-lock from inside keeps trying until the player is out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                val carol = server.world.playerNamed("carol")!!
                client.tele(HamHideout.OUTSIDE_CELL.first, HamHideout.OUTSIDE_CELL.second)
                client.locOp(cellDoor.id, cellDoor.x, cellDoor.z, 1)
                client.awaitSeen("[ham_hideout.cell_locked]")
                // From outside, Pick-lock does nothing but say so.
                client.locOp(cellDoor.id, cellDoor.x, cellDoor.z, HamHideout.OP_PICK)
                client.awaitSeen("[ham_hideout.cell_locked]", 2)
                assertEquals(0, client.seen("[ham_hideout.pick]"))

                // A walk stops a pick in progress, and the player can log out meanwhile (no delay holds them).
                client.tele(HamHideout.CELL.first, HamHideout.CELL.second)
                client.setLevel("thieving", THIEVING, 1)
                client.locOp(cellDoor.id, cellDoor.x, cellDoor.z, HamHideout.OP_PICK)
                client.awaitSeen("[ham_hideout.pick]")
                client.awaitTicks(2)
                assertTrue(!carol.scripts.delayed, "not held while picking")
                // The try's timer and fail count are kept for the session only, never in the save.
                assertTrue(carol.sessionCharges[HamHideout.PICK_DUE] > 0)
                assertTrue(carol.charges.contents().keys.none { it == HamHideout.PICK_DUE || it == HamHideout.CELL_FAILS })
                client.walk(3185, 9609)
                client.awaitAt(3185, 9609)
                val stopped = client.seen(FAIL) + client.seen(DONE)
                client.awaitTicks(HamHideout.PICK_TICKS + 2)
                assertEquals(stopped, client.seen(FAIL) + client.seen(DONE), "no more tries after the walk")

                // Thieving 1, no lockpick: one Pick-lock keeps trying, each fail worth 0.5 XP and raising the chance, until out.
                client.tele(HamHideout.CELL.first, HamHideout.CELL.second)
                val xp = client.stat(THIEVING)!!.xp
                val picks = client.seen("[ham_hideout.pick]")
                val failsBefore = client.seen(FAIL)
                client.locOp(cellDoor.id, cellDoor.x, cellDoor.z, HamHideout.OP_PICK)
                client.await("out of the cell", 60_000) { client.position?.takeIf { it == Coord(HamHideout.OUTSIDE_CELL.first, HamHideout.OUTSIDE_CELL.second, 0) } }
                assertEquals(picks + 1, client.seen("[ham_hideout.pick]"), "one Pick-lock")
                assertEquals(1, client.seen(DONE))
                val fails = client.seen(FAIL) - failsBefore
                client.await("3.8 XP and 0.5 a fail") { client.stat(THIEVING)?.takeIf { it.xp >= xp + 3 + fails / 2 } }
                assertEquals(1, client.stat(THIEVING)!!.level, "no numbing on the cell door")
            }
        }
    }

    private companion object {
        const val MEMBER = 2540
        const val THIEVING = 17
        const val AGILITY = 16
        const val HITPOINTS = 3
        const val COINS = 995
        const val BACKPACK = 93
        const val DONE = "[ham_hideout.pick_done]"
        const val FAIL = "[ham_hideout.pick_fail]"
        const val CONCUSSED = "[ham_hideout.concussed]"
        const val AVOIDED = "[ham_hideout.avoided]"
        const val BUNDLED = "[ham_hideout.bundled_out]"
    }
}
