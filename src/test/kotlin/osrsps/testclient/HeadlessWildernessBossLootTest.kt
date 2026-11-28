package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.AllSettings
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.SettingsSide
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.map.CollisionMap

/**
 * Wilderness step W4.1 through the headless client: a test boss with group loot, spawned with `::wboss`, hit for 30 by
 * one player and finished for 70 by another; each gets their own pile under it (the bones whole, the coins at their
 * share: 30 and 70), theirs alone at first, and each is told their kill count, read off its varp.
 */
class HeadlessWildernessBossLootTest {
    private val boss = NpcType(BOSS, "Ice giant", ops = listOf(null, "Attack", null, null, null), combatLevel = 53, internalName = "icegiant", stats = NpcStats(hitpoints = 100))
    private val types = NpcTypes.of(boss)

    private val drops = """
        {"npcs": [{"npcs": ["icegiant"], "always": [{"item": 532, "name": "Big bones"}],
          "table": {"slots": 1, "lines": [{"item": 995, "name": "Coins", "count": 100, "weight": 1}]}, "source": "test"}]}
    """.trimIndent()

    /** The drops with an elite clue in the always list too: a boss's tertiary, made sure. */
    private val clueDrops = drops.replace(
        "\"always\": [{\"item\": 532, \"name\": \"Big bones\"}]",
        "\"always\": [{\"item\": 532, \"name\": \"Big bones\"}, {\"item\": $ELITE_CLUE, \"name\": \"Clue scroll (elite)\"}]",
    )

    /** A collection log page holding the big bones, its ids made up. */
    private val catalog = osrsps.game.collection.CollectionLogCatalog(
        listOf(osrsps.game.collection.CollectionLogCatalog.Tab(0, 471, listOf(osrsps.game.collection.CollectionLogCatalog.Page(1, 0, 0, listOf(532), completedVarbit = null)))),
    )

    private fun server(dir: Path, deathTicks: Int? = null, group: Boolean = true, topDamage: Boolean = false, clue: Boolean = topDamage) = TestServer(dir) { crcs, millis ->
        val objs = ObjTypes.of(
            ObjType(532, "Big bones"), ObjType(995, "Coins", stackable = true),
            ObjType(ELITE_CLUE, "Clue scroll (elite)", iops = listOf("Read", null, null, null, "Drop"), params = mapOf(osrsps.game.trail.TrailTier.ELITE.param to 1)),
            ObjType(osrsps.game.trail.TrailTier.ELITE.scrollBox, "Scroll box (elite)", stackable = true),
        )
        World(
            crcs, CollisionMap().apply { markLoaded(3200 shr 6, 3900 shr 6) }, objTypes = objs, npcTypes = types, random = GameRandom(4),
            varTypes = VarTypes(6_000, SettingsSide.VARBITS + AllSettings.VARBITS),
            npcCombat = if (deathTicks == null) NpcCombat.load(types, SeqNames.NONE) else NpcCombat.parse(
                """{ "npcs": [ { "npcs": ["icegiant"], "attackSpeed": 5, "maxHit": 0, "attackType": "crush", "attackBonus": 0, "strengthBonus": 0,
                    "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 0, "light": 0, "standard": 0, "heavy": 0 },
                    "anims": { "attack": null, "defend": null, "death": null }, "deathTicks": $deathTicks, "respawnTicks": 100, "source": "test" } ] }""",
                types, SeqNames.NONE,
            ), npcDrops = NpcDrops.parse(if (clue) clueDrops else drops, types, objs), cycleMillis = millis,
            collectionLogCatalog = catalog,
        ).also { it.wildernessBosses.register(osrsps.game.content.WildernessBoss("test boss", setOf(BOSS), KILL_COUNT_VARP, group = group && !topDamage, topDamage = topDamage)) }
    }

    /** Presses Logout until it takes, [other] counting ticks: the button refuses it for a few seconds after a hit. */
    private fun HeadlessClient.logOutOfCombat(other: HeadlessClient) {
        repeat(LOGOUT_TRIES) {
            if (packets().any { it.prot.name == "LOGOUT" }) return
            // The socket may close between the check and the click once the logout has gone through.
            runCatching { ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1) }
            other.awaitTicks(2)
        }
        await("the logout") { packets().firstOrNull { it.prot.name == "LOGOUT" } }
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    @Test
    fun `a boss without group loot drops for whoever dealt the most damage, not the last hit`(@TempDir dir: Path) {
        server(dir, group = false).use { server ->
            server.login("ada").use { a ->
                server.login("bea").use { b ->
                    a.await("the login rebuild") { a.position }
                    b.await("the login rebuild") { b.position }
                    a.tele(3200, 3900)
                    b.tele(3200, 3901)
                    a.commandReply("wboss test boss") { it.startsWith("Spawned Ice giant") }
                    val npc = a.await("the boss") { server.world.npcs.all.firstOrNull { it.type.id == BOSS } }
                    a.commandReply("god") { it.startsWith("You can") }
                    b.commandReply("god") { it.startsWith("You can") }
                    a.commandReply("hit 60 ${npc.index}") { it.startsWith("Hit Ice giant") }
                    // Bea's last 40 kill it, but Ada's 60 are the most (exploit review round 39).
                    b.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                    a.await("ada's kill count") { a.messages().firstOrNull { it.text == "[wilderness_boss.kill_count boss=Ice giant count=1]" } }
                    val world = server.world
                    val ada = world.playerNamed("ada")!!
                    val bea = world.playerNamed("bea")!!
                    a.await("ada's drop") { world.groundObjs.visibleAt(ada, npc.x, npc.z, npc.level).firstOrNull { it.id == 995 } }
                    assertEquals(1, ada.vars[KILL_COUNT_VARP])
                    assertEquals(0, bea.vars[KILL_COUNT_VARP], "not the last hit's")
                    assertTrue(world.groundObjs.visibleAt(bea, npc.x, npc.z, npc.level).isEmpty(), "ada's alone at first")
                }
            }
        }
    }

    @Test
    fun `one who walked away from the boss before the kill gets neither its drop nor the kill count`(@TempDir dir: Path) {
        server(dir, group = false).use { server ->
            server.login("ada").use { a ->
                server.login("bea").use { b ->
                    a.await("the login rebuild") { a.position }
                    b.await("the login rebuild") { b.position }
                    a.tele(3200, 3900)
                    b.tele(3200, 3901)
                    a.commandReply("wboss test boss") { it.startsWith("Spawned Ice giant") }
                    val npc = a.await("the boss") { server.world.npcs.all.firstOrNull { it.type.id == BOSS } }
                    a.commandReply("god") { it.startsWith("You can") }
                    b.commandReply("god") { it.startsWith("You can") }
                    a.commandReply("hit 60 ${npc.index}") { it.startsWith("Hit Ice giant") }
                    // Ada's 60 are the most, but she has walked off, still logged in (exploit review round 40, R40B-3).
                    a.tele(3240, 3900)
                    b.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                    b.await("bea's kill count") { b.messages().firstOrNull { it.text == "[wilderness_boss.kill_count boss=Ice giant count=1]" } }
                    val world = server.world
                    val bea = world.playerNamed("bea")!!
                    b.await("bea's drop") { world.groundObjs.visibleAt(bea, npc.x, npc.z, npc.level).firstOrNull { it.id == 995 } }
                    assertEquals(0, world.playerNamed("ada")!!.vars[KILL_COUNT_VARP])
                }
            }
        }
    }

    @Test
    fun `16 tiles from a boss without an encounter is gone from its fight`(@TempDir dir: Path) {
        server(dir, group = false).use { server ->
            server.login("ada").use { a ->
                server.login("bea").use { b ->
                    a.await("the login rebuild") { a.position }
                    b.await("the login rebuild") { b.position }
                    a.tele(3200, 3900)
                    b.tele(3200, 3901)
                    a.commandReply("wboss test boss") { it.startsWith("Spawned Ice giant") }
                    val npc = a.await("the boss") { server.world.npcs.all.firstOrNull { it.type.id == BOSS } }
                    a.commandReply("god") { it.startsWith("You can") }
                    b.commandReply("god") { it.startsWith("You can") }
                    a.commandReply("hit 60 ${npc.index}") { it.startsWith("Hit Ice giant") }
                    // One tile past the 15, measured from the tile it stands on (Codex review on #817); auto retaliate
                    // off, so she stays there.
                    a.commandReply("varp ${osrsps.game.EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1") { it.startsWith("Varp") }
                    a.tele(npc.x + 16, npc.z)
                    b.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                    b.await("bea's kill count") { b.messages().firstOrNull { it.text == "[wilderness_boss.kill_count boss=Ice giant count=1]" } }
                    assertEquals(0, server.world.playerNamed("ada")!!.vars[KILL_COUNT_VARP])
                }
            }
        }
    }

    @Test
    fun `a top damager who logs out as the body falls keeps the drop theirs`(@TempDir dir: Path) {
        server(dir, deathTicks = LONG_DEATH_TICKS, group = false).use { server ->
            server.login("bea").use { b ->
                b.await("the login rebuild") { b.position }
                b.tele(3200, 3901)
                b.commandReply("wboss test boss") { it.startsWith("Spawned Ice giant") }
                val npc = b.await("the boss") { server.world.npcs.all.firstOrNull { it.type.id == BOSS } }
                b.commandReply("god") { it.startsWith("You can") }
                server.login("ada").use { a ->
                    a.await("the login rebuild") { a.position }
                    a.tele(3200, 3900)
                    a.commandReply("god") { it.startsWith("You can") }
                    a.commandReply("hit 60 ${npc.index}") { it.startsWith("Hit Ice giant") }
                    b.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                    // Gone while the body is still falling (exploit review round 40, R40B-6).
                    a.logOutOfCombat(b)
                }
                val bea = server.world.playerNamed("bea")!!
                b.await("the body gone", timeoutMillis = 20_000) { Unit.takeIf { server.world.npcs.all.none { it === npc } } }
                b.awaitTicks(2)
                assertEquals(emptyList(), server.world.groundObjs.visibleAt(bea, npc.x, npc.z, npc.level).map { it.id }, "ada's alone, not everyone's")
                server.login("ada").use { a ->
                    a.await("the login rebuild") { a.position }
                    val ada = server.world.playerNamed("ada")!!
                    a.await("ada's drop") { server.world.groundObjs.visibleAt(ada, npc.x, npc.z, npc.level).firstOrNull { it.id == 995 } }
                }
            }
        }
    }

    @Test
    fun `two players' damage splits the boss's loot into two piles, and both count the kill`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { a ->
                server.login("bea").use { b ->
                    a.await("the login rebuild") { a.position }
                    b.await("the login rebuild") { b.position }
                    a.tele(3200, 3900)
                    b.tele(3200, 3901)
                    a.commandReply("wboss") { it == "Wilderness bosses: test boss" }
                    a.commandReply("wboss test boss") { it.startsWith("Spawned Ice giant") }
                    val npc = a.await("the boss") { server.world.npcs.all.firstOrNull { it.type.id == BOSS } }
                    a.commandReply("god") { it.startsWith("You can") }
                    b.commandReply("god") { it.startsWith("You can") }
                    a.commandReply("hit 30 ${npc.index}") { it.startsWith("Hit Ice giant") }
                    b.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                    for (client in listOf(a, b)) {
                        client.await("the kill count") { client.messages().firstOrNull { it.text == "[wilderness_boss.kill_count boss=Ice giant count=1]" } }
                    }
                    val world = server.world
                    val ada = world.playerNamed("ada")!!
                    val bea = world.playerNamed("bea")!!
                    assertEquals(1, ada.vars[KILL_COUNT_VARP])
                    assertEquals(1, bea.vars[KILL_COUNT_VARP])
                    fun pile(player: osrsps.game.Player) = world.groundObjs.visibleAt(player, npc.x, npc.z, npc.level).map { it.id to it.count }.sortedBy { it.first }
                    a.await("the piles") { Unit.takeIf { pile(ada).isNotEmpty() && pile(bea).isNotEmpty() } }
                    assertEquals(listOf(532 to 1, 995 to 30), pile(ada), "ada's 30%")
                    assertEquals(listOf(532 to 1, 995 to 70), pile(bea), "bea's 70%")
                }
            }
        }
    }

    @Test
    fun `an Ironman under 7% gets 5%, and one who logged out before the kill gets neither loot nor kill count`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bea").use { b ->
                b.await("the login rebuild") { b.position }
                b.tele(3200, 3901)
                b.commandReply("wboss test boss") { it.startsWith("Spawned Ice giant") }
                val npc = b.await("the boss") { server.world.npcs.all.firstOrNull { it.type.id == BOSS } }
                server.login("ada").use { a ->
                    a.await("the login rebuild") { a.position }
                    a.tele(3200, 3900)
                    a.commandReply("hit 30 ${npc.index}") { it.startsWith("Hit Ice giant") }
                    // Out of the boss's reach and out of combat first: the button refuses a logout for a few seconds after a hit.
                    a.tele(3260, 3845)
                    a.logOutOfCombat(b)
                }
                server.login("iron").use { i ->
                    i.await("the login rebuild") { i.position }
                    i.tele(3200, 3900)
                    i.commandReply("varbit ${SettingsSide.VARBIT_IRONMAN} 1") { it.startsWith("Varbit") }
                    i.commandReply("god") { it.startsWith("You can") }
                    i.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Ice giant") }
                    // Loot notifications on, for anything worth a coin or more.
                    b.commandReply("varbit ${AllSettings.VARBIT_LOOT_NOTIFICATION} 1") { it.startsWith("Varbit") }
                    b.commandReply("varbit ${AllSettings.VARBIT_LOOT_NOTIFICATION_VALUE} 1") { it.startsWith("Varbit") }
                    b.commandReply("god") { it.startsWith("You can") }
                    b.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                    // bea's 69 of the 100 damage: ada's 30 still counts in the total.
                    b.await("bea's loot notification") { b.messages().firstOrNull { it.text.startsWith("[loot.valuable item=69 x Coins") } }
                    val iron = server.world.playerNamed("iron")!!
                    fun pile(player: osrsps.game.Player) = server.world.groundObjs.visibleAt(player, npc.x, npc.z, npc.level).map { it.id to it.count }.sortedBy { it.first }
                    i.await("iron's pile") { Unit.takeIf { pile(iron).isNotEmpty() } }
                    // 1% of the damage: an Ironman's 5%, not the 25% everyone else gets.
                    assertEquals(listOf(532 to 1, 995 to 5), pile(iron))
                    assertEquals(1, iron.vars[KILL_COUNT_VARP])
                }
                server.login("ada").use { a ->
                    a.await("the login rebuild") { a.position }
                    val ada = server.world.playerNamed("ada")!!
                    a.awaitTicks(2)
                    assertEquals(emptyList(), server.world.groundObjs.visibleAt(ada, npc.x, npc.z, npc.level).filter { it.id == 995 }.map { it.count }, "no pile for ada")
                    assertEquals(0, ada.vars[KILL_COUNT_VARP])
                }
            }
        }
    }

    @Test
    fun `one who logs back in while the boss's body is still there gets no loot, as who is logged in at the kill decides`(@TempDir dir: Path) {
        server(dir, deathTicks = LONG_DEATH_TICKS).use { server ->
            server.login("bea").use { b ->
                b.await("the login rebuild") { b.position }
                b.tele(3200, 3901)
                b.commandReply("wboss test boss") { it.startsWith("Spawned Ice giant") }
                val npc = b.await("the boss") { server.world.npcs.all.firstOrNull { it.type.id == BOSS } }
                server.login("ada").use { a ->
                    a.await("the login rebuild") { a.position }
                    a.tele(3200, 3900)
                    a.commandReply("hit 30 ${npc.index}") { it.startsWith("Hit Ice giant") }
                    a.tele(3260, 3845)
                    a.logOutOfCombat(b)
                }
                b.commandReply("god") { it.startsWith("You can") }
                b.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                server.login("ada").use { a ->
                    a.await("the login rebuild") { a.position }
                    val ada = server.world.playerNamed("ada")!!
                    val bea = server.world.playerNamed("bea")!!
                    assertTrue(server.world.npcs.all.any { it === npc }, "back before the body goes")
                    a.await("the loot") { Unit.takeIf { server.world.groundObjs.visibleAt(bea, npc.x, npc.z, npc.level).isNotEmpty() } }
                    a.awaitTicks(2)
                    assertEquals(emptyList(), server.world.groundObjs.visibleAt(ada, npc.x, npc.z, npc.level).filter { it.id == 995 }.map { it.count }, "no pile for ada")
                    assertEquals(0, ada.vars[KILL_COUNT_VARP])
                }
            }
        }
    }

    @Test
    fun `a boss taken out alive leaves no damage tally behind`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { a ->
                a.await("the login rebuild") { a.position }
                a.tele(3200, 3900)
                a.commandReply("wboss test boss") { it.startsWith("Spawned Ice giant") }
                val npc = a.await("the boss") { server.world.npcs.all.firstOrNull { it.type.id == BOSS } }
                a.commandReply("god") { it.startsWith("You can") }
                a.commandReply("hit 30 ${npc.index}") { it.startsWith("Hit Ice giant") }
                assertEquals(1, server.world.wildernessBosses.tallies)
                a.commandReply("delnpc ${npc.index}") { true }
                a.await("the tally gone") { Unit.takeIf { server.world.wildernessBosses.tallies == 0 } }
                assertTrue(server.world.npcs.all.none { it === npc })
            }
        }
    }

    @Test
    fun `a boss deleted mid-death leaves no damage tally behind either`(@TempDir dir: Path) {
        server(dir, deathTicks = LONG_DEATH_TICKS).use { server ->
            server.login("ada").use { a ->
                a.await("the login rebuild") { a.position }
                a.tele(3200, 3900)
                a.commandReply("wboss test boss") { it.startsWith("Spawned Ice giant") }
                val npc = a.await("the boss") { server.world.npcs.all.firstOrNull { it.type.id == BOSS } }
                a.commandReply("god") { it.startsWith("You can") }
                a.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                assertEquals(1, server.world.wildernessBosses.tallies)
                a.commandReply("delnpc ${npc.index}") { true }
                a.await("the tally gone") { Unit.takeIf { server.world.wildernessBosses.tallies == 0 } }
            }
        }
    }

    @Test
    fun `a top-damage boss's loot is its top damager's, its clue as a scroll box and its log entry kept`(@TempDir dir: Path) {
        server(dir, topDamage = true).use { server ->
            server.login("ada").use { a ->
                server.login("bea").use { b ->
                    a.await("the login rebuild") { a.position }
                    b.await("the login rebuild") { b.position }
                    a.tele(3200, 3900)
                    b.tele(3200, 3901)
                    a.commandReply("wboss test boss") { it.startsWith("Spawned Ice giant") }
                    val npc = a.await("the boss") { server.world.npcs.all.firstOrNull { it.type.id == BOSS } }
                    a.commandReply("god") { it.startsWith("You can") }
                    b.commandReply("god") { it.startsWith("You can") }
                    a.commandReply("hit 70 ${npc.index}") { it.startsWith("Hit Ice giant") }
                    b.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                    val world = server.world
                    val ada = world.playerNamed("ada")!!
                    fun pile(player: osrsps.game.Player) = world.groundObjs.visibleAt(player, npc.x, npc.z, npc.level).map { it.id }.sorted()
                    a.await("ada's pile") { Unit.takeIf { pile(ada).isNotEmpty() } }
                    assertEquals(listOf(532, 995, osrsps.game.trail.TrailTier.ELITE.scrollBox), pile(ada), "the box, not the raw clue")
                    assertEquals(1, world.collectionLog.count(ada, 532), "the bones in ada's collection log")
                }
            }
        }
    }

    @Test
    fun `a group kill's clue scroll drops as a scroll box, and as nothing for a looter at the clue cap`(@TempDir dir: Path) {
        server(dir, clue = true).use { server ->
            server.login("ada").use { a ->
                server.login("bea").use { b ->
                    a.await("the login rebuild") { a.position }
                    b.await("the login rebuild") { b.position }
                    val box = osrsps.game.trail.TrailTier.ELITE.scrollBox
                    a.commandReply("item $box ${osrsps.game.trail.TreasureTrails.BOX_CAP}") { it.startsWith("Added") }
                    a.tele(3200, 3900)
                    b.tele(3200, 3901)
                    a.commandReply("wboss test boss") { it.startsWith("Spawned Ice giant") }
                    val npc = a.await("the boss") { server.world.npcs.all.firstOrNull { it.type.id == BOSS } }
                    a.commandReply("god") { it.startsWith("You can") }
                    b.commandReply("god") { it.startsWith("You can") }
                    a.commandReply("hit 30 ${npc.index}") { it.startsWith("Hit Ice giant") }
                    b.commandReply("kill ${npc.index}") { it.startsWith("Killed") }
                    val world = server.world
                    val ada = world.playerNamed("ada")!!
                    val bea = world.playerNamed("bea")!!
                    fun pile(player: osrsps.game.Player) = world.groundObjs.visibleAt(player, npc.x, npc.z, npc.level).map { it.id }.sorted()
                    a.await("the piles") { Unit.takeIf { pile(ada).isNotEmpty() && pile(bea).isNotEmpty() } }
                    assertEquals(listOf(532, 995, box), pile(bea), "bea's box, not the raw clue")
                    assertEquals(listOf(532, 995), pile(ada), "ada, at the cap, gets none")
                }
            }
        }
    }

    private companion object {
        const val ELITE_CLUE = 12073
        const val BOSS = 2085
        const val KILL_COUNT_VARP = 1510
        const val LOGOUT_TRIES = 30
        const val LONG_DEATH_TICKS = 40
    }
}
