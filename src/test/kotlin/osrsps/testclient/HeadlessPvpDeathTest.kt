package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.MemorySaveStore
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.PlayerOps
import osrsps.game.PlayerSave
import osrsps.game.SaveStore
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.Deaths
import osrsps.game.combat.PvpLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.skill.ArceuusRunecraft
import osrsps.map.CollisionMap

/**
 * Wilderness step W1.7 through two headless clients: a player killed by another keeps their three most valuable items
 * (none skulled), the tradeable rest drops where they fell for the killer alone, and the untradeable rest goes to the
 * grave at level 20 or below, while above it one that can't be worn is kept and one that can is gone.
 */
class HeadlessPvpDeathTest {
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))
    // Not tradeable on the Grand Exchange, as the cache has it (Codex review on #712): it still drops for the killer.
    private val coins = ObjType(995, "Coins", cost = 1, stackable = true)
    private val sword = ObjType(1277, "Bronze sword", cost = 26, tradeable = true)
    private val bones = ObjType(526, "Bones", cost = 1, tradeable = true)
    private val lobster = ObjType(379, "Lobster", cost = 150, tradeable = true)
    private val cape = ObjType(6570, "Fire cape", wearpos1 = WearPos.CAPE)
    private val book = ObjType(757, "Book")
    private val fragments = ObjType(ArceuusRunecraft.FRAGMENTS, "Dark essence fragments")

    private fun server(dir: Path, saves: SaveStore = MemorySaveStore()) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            markLoaded(3222 shr 6, 3218 shr 6)
            for (sz in (3456 shr 6)..(3967 shr 6)) markLoaded(3090 shr 6, sz)
        }
        World(crcs, map, objTypes = ObjTypes.of(coins, sword, bones, lobster, cape, book, fragments), npcTypes = NpcTypes.of(grave), cycleMillis = millis, saves = saves)
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    /** [victim] carrying the test's items at level [z]'s Wilderness, on 1 hitpoint, killed by one sure punch from [killer]. */
    private fun TestServer.kill(killer: HeadlessClient, victim: HeadlessClient, victimName: String, z: Int, skulled: Boolean = false, extra: List<String> = emptyList()) {
        killer.await("the login rebuild") { killer.position }
        victim.await("the login rebuild") { victim.position }
        // Both at combat level 3, so even level 5 has them in range: one point of hitpoints left, and a sure punch.
        killer.commandReply("sure max") { it.startsWith("Your attacks") }
        victim.commandReply("hit 9 me") { it.startsWith("Hit yourself") }
        for (command in listOf("item 379", "item 1277", "item 995 10", "item 526 2", "item 6570", "item 757") + extra) victim.command(command)
        victim.await("the bag filled") { victim.inventory(BACKPACK).takeIf { it.count { obj -> obj != null } == 7 + extra.size } }
        victim.command("varp 172 1")
        if (skulled) world.playerNamed(victimName)!!.skullTicks = 500
        killer.tele(3090, z)
        victim.tele(3091, z)
        killer.await("Attack given") { Unit.takeIf { killer.playerOps()[PlayerOps.ATTACK] == PlayerOps.ATTACK_TEXT } }
        killer.await("the victim in view") { killer.otherPlayers().takeIf { it.isNotEmpty() } }
        killer.playerOp(victim.index, PlayerOps.ATTACK)
        victim.await("[combat.dead]") { victim.messages().firstOrNull { it.text == "[combat.dead]" } }
        victim.await("the respawn", timeoutMillis = 10_000) { victim.position?.takeIf { it == Coord(3222, 3218) } }
    }

    private fun TestServer.groundFor(name: String, z: Int) =
        world.groundObjs.visibleAt(world.playerNamed(name)!!, 3091, z, 0).map { InvObj(it.id, it.count) }.sortedBy { it.id }

    @Test
    fun `above level 20 the killer gets the tradeables, an untradeable that can be worn is gone, one that can't is kept`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("kai").use { killer ->
                server.login("lou").use { victim ->
                    server.kill(killer, victim, "lou", LEVEL_25)
                    victim.await("the kept items") { victim.inventory(BACKPACK).filterNotNull().takeIf { it.size == 4 } }
                    assertEquals(
                        listOf(InvObj(lobster.id, 1), InvObj(sword.id, 1), InvObj(coins.id, 1), InvObj(book.id, 1)),
                        victim.inventory(BACKPACK).filterNotNull(),
                        "the three most valuable, and the book, which can't be worn",
                    )
                    assertEquals(listOf(InvObj(bones.id, 1), InvObj(bones.id, 1), InvObj(coins.id, 9)), server.groundFor("kai", LEVEL_25))
                    assertEquals(emptyList(), server.groundFor("lou", LEVEL_25), "the killer's alone for now")
                    assertNull(server.world.deaths.graveOf(server.world.playerNamed("lou")!!), "nothing for a grave above 20")
                }
            }
        }
    }

    @Test
    fun `above level 20 dark essence fragments are kept, as an untradeable that can't be worn, with their count`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("kai").use { killer ->
                server.login("lou").use { victim ->
                    victim.await("the login rebuild") { victim.position }
                    server.world.playerNamed("lou")!!.charges[ArceuusRunecraft.FRAGMENT_CHARGES] = 40
                    server.kill(killer, victim, "lou", LEVEL_25, extra = listOf("item ${ArceuusRunecraft.FRAGMENTS}"))
                    victim.await("the fragments kept") { victim.inventory(BACKPACK).firstOrNull { it?.id == fragments.id } }
                    // Exploit review round 42, R42A-1: the obj came back to the backpack, so its count stays with it.
                    assertEquals(40, server.world.arceuusRunecraft.fragments(server.world.playerNamed("lou")!!))
                }
            }
        }
    }

    @Test
    fun `skulled, nothing is kept, and the skull goes`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("max").use { killer ->
                server.login("ned").use { victim ->
                    server.world.playerNamed("ned")!!.attackedFirst["zed"] = 500
                    server.kill(killer, victim, "ned", LEVEL_25, skulled = true)
                    victim.awaitTicks(2)
                    assertEquals(emptyMap(), server.world.playerNamed("ned")!!.attackedFirst, "hit-back rights go with the skull")
                    assertEquals(listOf(InvObj(book.id, 1)), victim.inventory(BACKPACK).filterNotNull())
                    assertTrue(InvObj(lobster.id, 1) in server.groundFor("max", LEVEL_25))
                    assertEquals(0, server.world.playerNamed("ned")!!.skullTicks)
                }
            }
        }
    }

    @Test
    fun `at level 20 or below the untradeables go to the grave`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("oli").use { killer ->
                server.login("pam").use { victim ->
                    server.kill(killer, victim, "pam", LEVEL_5)
                    val graveItems = server.world.deaths.graveOf(server.world.playerNamed("pam")!!)!!.items.contents().map { InvObj(it.second.id, it.second.count) }
                    assertEquals(setOf(InvObj(cape.id, 1), InvObj(book.id, 1)), graveItems.toSet())
                    assertEquals(listOf(InvObj(bones.id, 1), InvObj(bones.id, 1), InvObj(coins.id, 9)), server.groundFor("oli", LEVEL_5))
                }
            }
        }
    }

    @Test
    fun `the loot is the victim's, keeps its own timers beside the killer's stack, and the victim is saved without it`(@TempDir dir: Path) {
        val saves = MemorySaveStore()
        server(dir, saves).use { server ->
            server.login("quin").use { killer ->
                server.login("rex").use { victim ->
                    killer.await("the login rebuild") { killer.position }
                    // The killer's own coins already on the tile, nearly public and nearly gone.
                    server.world.groundObjs.addPrivate(coins, 5, 3091, LEVEL_25, 0, "quin", 3, 30)
                    server.kill(killer, victim, "rex", LEVEL_25)
                    val stacks = server.world.groundObjs.visibleAt(server.world.playerNamed("quin")!!, 3091, LEVEL_25, 0).filter { it.id == coins.id }
                    assertEquals(listOf(5, 9), stacks.map { it.count }.sorted(), "not merged into the killer's stack")
                    val loot = stacks.single { it.count == 9 }
                    assertEquals("rex", loot.droppedBy, "the victim's, so a pickup saves them")
                    assertEquals("quin", loot.owner)
                    assertTrue(loot.ticksUntilPublic > 90 && loot.ticksUntilDespawn > 490, "its own timers: ${loot.ticksUntilPublic}, ${loot.ticksUntilDespawn}")
                    val saved = PlayerSave.fromJson(saves.read("rex")!!).inventory.values.map { InvObj(it.id, it.count) }.toSet()
                    assertEquals(setOf(InvObj(lobster.id, 1), InvObj(sword.id, 1), InvObj(coins.id, 1), InvObj(book.id, 1)), saved, "saved without the loot")
                }
            }
        }
    }

    @Test
    fun `if the victim's save can't be written, the loot goes to their grave, not the killer`(@TempDir dir: Path) {
        val memory = MemorySaveStore()
        var failing: String? = null
        val saves = object : SaveStore {
            override fun read(key: String) = memory.read(key)
            override fun write(key: String, text: String) {
                if (key == failing) throw java.io.IOException("disk full (test)")
                memory.write(key, text)
            }
        }
        server(dir, saves).use { server ->
            server.login("yan").use { killer ->
                server.login("zoe").use { victim ->
                    killer.await("the login rebuild") { killer.position }
                    failing = "zoe"
                    server.kill(killer, victim, "zoe", LEVEL_25)
                    assertEquals(emptyList(), server.groundFor("yan", LEVEL_25), "nothing handed over")
                    val graveItems = server.world.deaths.graveOf(server.world.playerNamed("zoe")!!)!!.items.contents().map { InvObj(it.second.id, it.second.count) }
                    assertEquals(setOf(InvObj(bones.id, 2), InvObj(coins.id, 9)), graveItems.groupBy { it.id }.map { (id, l) -> InvObj(id, l.sumOf { it.count }) }.toSet())
                    failing = null
                }
            }
        }
    }

    @Test
    fun `on a tie in damage the latest of the leaders to hit is the killer`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("sid").use { a ->
                server.login("tom").use { b ->
                    server.login("uma").use { c ->
                        a.await("the login rebuild") { a.position }
                        b.await("the login rebuild") { b.position }
                        c.await("the login rebuild") { c.position }
                        val world = server.world
                        val (sid, tom, uma) = listOf("sid", "tom", "uma").map { world.playerNamed(it)!! }
                        val tick = world.tick
                        world.pvpCombat.dealtBy(uma, sid, 5, tick)
                        world.pvpCombat.dealtBy(uma, tom, 5, tick)
                        assertEquals("tom", world.pvpCombat.killerOf(uma))
                        world.pvpCombat.dealtBy(uma, sid, 0, tick)
                        assertEquals("sid", world.pvpCombat.killerOf(uma), "a splash of 0 still makes them the latest")
                        world.pvpCombat.dealtBy(uma, tom, 1, tick)
                        assertEquals("tom", world.pvpCombat.killerOf(uma), "the most damage wins outright")
                    }
                }
            }
        }
    }

    @Test
    fun `a death the killer didn't finish still credits them, and starts their PJ timer`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("vic").use { killer ->
                server.login("wes").use { victim ->
                    killer.await("the login rebuild") { killer.position }
                    victim.await("the login rebuild") { victim.position }
                    victim.command("varp 172 1")
                    killer.commandReply("sure") { it.startsWith("Your attacks") }
                    killer.tele(3090, LEVEL_25)
                    victim.tele(3091, LEVEL_25)
                    killer.await("Attack given") { Unit.takeIf { killer.playerOps()[PlayerOps.ATTACK] == PlayerOps.ATTACK_TEXT } }
                    killer.await("the victim in view") { killer.otherPlayers().takeIf { it.isNotEmpty() } }
                    val world = server.world
                    val vic = world.playerNamed("vic")!!
                    val wes = world.playerNamed("wes")!!
                    killer.playerOp(victim.index, PlayerOps.ATTACK)
                    killer.await("a hit on wes") { wes.pvpDamage["vic"] }
                    killer.tele(3090, 3500)
                    killer.await("vic's PJ timer over") { Unit.takeIf { world.tick >= vic.pjUntil } }
                    victim.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                    victim.await("[combat.dead]") { victim.messages().firstOrNull { it.text == "[combat.dead]" } }
                    assertEquals("vic", wes.deathKiller)
                    assertTrue(vic.pjOpponent === wes && vic.pjUntil > world.tick, "credited, vic's timer runs again")
                }
            }
        }
    }

    @Test
    fun `hit in the Wilderness but dying outside it, or in a scenario, is no PvP death`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("kai").use { killer ->
                server.login("lia").use { victim ->
                    killer.await("the login rebuild") { killer.position }
                    victim.await("the login rebuild") { victim.position }
                    victim.command("varp 172 1")
                    killer.commandReply("sure") { it.startsWith("Your attacks") }
                    killer.tele(3090, LEVEL_25)
                    victim.tele(3091, LEVEL_25)
                    killer.await("Attack given") { Unit.takeIf { killer.playerOps()[PlayerOps.ATTACK] == PlayerOps.ATTACK_TEXT } }
                    killer.await("the victim in view") { killer.otherPlayers().takeIf { it.isNotEmpty() } }
                    val world = server.world
                    val lia = world.playerNamed("lia")!!
                    killer.playerOp(victim.index, PlayerOps.ATTACK)
                    killer.await("a hit on lia") { lia.pvpDamage["kai"] }
                    killer.command("tele 3090 3500 0")
                    // Out of the Wilderness, within the 100 ticks a hit counts for: her death is a death like any other.
                    victim.command("item 379")
                    victim.tele(3222, 3230)
                    victim.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                    victim.await("[combat.dead]") { victim.messages().firstOrNull { it.text == "[combat.dead]" } }
                    assertNull(lia.deathKiller, "no killer outside the Wilderness")
                    victim.await("the respawn", timeoutMillis = 10_000) { victim.position?.takeIf { it == Coord(3222, 3218) } }
                    assertTrue(world.groundObjs.visibleAt(world.playerNamed("kai")!!, 3222, 3230, 0).isEmpty(), "nothing on the ground for kai")

                    // In a ::scenario, killed in the Wilderness: the scenario's items go with its grave, not to the killer.
                    killer.tele(3090, LEVEL_25)
                    victim.tele(3091, LEVEL_25)
                    lia.scenario = "pvp"
                    killer.await("the victim in view again") { killer.otherPlayers().takeIf { it.isNotEmpty() } }
                    killer.playerOp(victim.index, PlayerOps.ATTACK)
                    killer.await("another hit on lia") { lia.pvpDamage["kai"] }
                    victim.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                    victim.await("[combat.dead] again") { victim.messages().count { it.text == "[combat.dead]" }.takeIf { it >= 2 } }
                    assertNull(lia.deathKiller, "no killer for a scenario death")
                    lia.scenario = null
                }
            }
        }
    }

    @Test
    fun `a death that keeps everything keeps the skull and the hit-back rights`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ray").use { client ->
                client.await("the login rebuild") { client.position }
                val world = server.world
                val ray = world.playerNamed("ray")!!
                client.commandReply("pause") { it.startsWith("Paused after tick") }
                ray.skullTicks = 500
                ray.attackedFirst["sue"] = 500
                world.deaths.respawn(ray, osrsps.game.combat.DeathPlace(true, 3222, 3218, 0, 3222, 3218, 0))
                assertTrue(ray.skullTicks > 0, "the skull stays")
                assertEquals(500, ray.attackedFirst["sue"])
                client.commandReply("resume") { it.startsWith("Resumed") }
            }
        }
    }

    @Test
    fun `the killer's PJ timer holds though the victim logs out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { killer ->
                val victim = server.login("bo")
                server.login("cy").use { third ->
                    third.await("the login rebuild") { third.position }
                    third.tele(3093, LEVEL_25)
                    server.kill(killer, victim, "bo", LEVEL_25)
                    val world = server.world
                    val ada = world.playerNamed("ada")!!
                    // Out of combat at once, so the victim can log out while the killer's timer still runs.
                    world.playerNamed("bo")!!.attackedByPlayerTick = -1
                    victim.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                    third.await("bo gone") { Unit.takeIf { world.playerNamed("bo") == null } }
                    assertTrue(ada.pjAfterKill && ada.pjUntil > world.tick, "ada's timer after the kill still runs")
                    third.await("ada in view") { third.otherPlayers().takeIf { it.isNotEmpty() } }
                    val text = DialogueText.NONE[PvpLines.TARGET_IN_COMBAT]
                    third.playerOp(killer.index, PlayerOps.ATTACK)
                    third.await("the refusal") { third.messages().firstOrNull { it.text == text } }
                }
            }
        }
    }

    private companion object {
        const val LEVEL_25 = 3520 + 24 * 8
        const val LEVEL_5 = 3520 + 4 * 8
        const val BACKPACK = 93
    }
}
