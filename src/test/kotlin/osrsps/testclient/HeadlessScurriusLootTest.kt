package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.Scurrius
import osrsps.game.content.ScurriusLoot
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.Npc
import osrsps.game.VarTypes
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Scurrius step 3 through the headless client: his loot on the centre of his body, the MVP table for the most damage
 * and the non-MVP table for everyone else, and the kill count for each of them.
 */
class HeadlessScurriusLootTest {
    private val types = LocTypes(
        listOf(LocType(Scurrius.BARS, "Broken bars", ops = listOf("Climb-through (normal)", "Climb-through (private)", "Peek (normal)", null, null), collision = LocCollisionType(width = 1, length = 1)))
            .associateBy { it.id },
    )

    private val scurrius = NpcType(
        Scurrius.BOSS_PRIVATE, "Scurrius", size = 3, ops = listOf(null, "Attack", null, null, null), combatLevel = 200, internalName = "rat_boss_instance",
        stats = NpcStats(attack = 300, defence = 60, strength = 100, hitpoints = 500, ranged = 50, magic = 50),
    )

    /** Every item his two tables name, under its own name so the loader takes them. */
    private val items = mapOf(
        532 to "Big bones", 2134 to "Raw rat meat", 1123 to "Adamant platebody", 1147 to "Rune med helm", 1163 to "Rune full helm",
        1185 to "Rune sq shield", 1113 to "Rune chainbody", 1373 to "Rune battleaxe", 890 to "Adamant arrow", 892 to "Rune arrow",
        562 to "Chaos rune", 560 to "Death rune", 563 to "Law rune", 333 to "Trout", 361 to "Tuna", 385 to "Shark", 379 to "Lobster",
        173 to "Ranging potion(1)", 161 to "Super strength(1)", 1985 to "Cheese", 2434 to "Prayer potion(4)", 995 to "Coins",
        2801 to "Clue scroll (medium)", 28798 to "Scurrius' spine", 10976 to "Long bone", 10977 to "Curved bone", 28801 to "Scurry",
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(items.keys.max() + 1)
        for ((id, name) in items) objs[id] = ObjType(id, name, stackable = id in setOf(890, 892, 562, 560, 563, 995), tradeable = true)
        val objTypes = ObjTypes(objs)
        val npcTypes = NpcTypes.of(scurrius)
        World(
            crcs, CollisionMap().apply { markLoaded(3282 shr 6, 9867 shr 6) }, random = GameRandom(11), locTypes = types, objTypes = objTypes,
            staticLocs = StaticLocs().apply { add(ZoneLoc(Scurrius.BARS, LocShape.CENTREPIECE_STRAIGHT, 1, 3282, 9867, 0)) },
            npcTypes = npcTypes, npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), npcDrops = NpcDrops.load(npcTypes, objTypes),
            varTypes = VarTypes(6_000, emptyMap()), cycleMillis = millis,
        )
    }

    @Test
    fun `the most damage gets the MVP table on his centre tile, everyone else the non-MVP table, and each counts the kill`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    bob.await("the login rebuild") { bob.position }
                    alice.await("the login rebuild") { alice.position }
                    bob.command("tele ${Scurrius.OUTSIDE.x} ${Scurrius.OUTSIDE.z} 0")
                    bob.await("outside the bars") { bob.position?.takeIf { it == Coord(Scurrius.OUTSIDE.x, Scurrius.OUTSIDE.z, 0) } }
                    bob.commandReply("god") { it.startsWith("You can") }
                    bob.locOp(Scurrius.BARS, 3282, 9867, Scurrius.CLIMB_PRIVATE)
                    val world = server.world
                    val b = world.playerNamed("bob")!!
                    val a = world.playerNamed("alice")!!
                    val run = bob.await("in a lair of his own") { world.scurriusLairs.runOf(b) }
                    val boss: Npc = bob.await("Scurrius") { world.npcs.all.firstOrNull { it.type.id == Scurrius.BOSS_PRIVATE && run.instance.contains(it.x, it.z) } }
                    // Alice's share is the smaller: as if both had hit him (the tally is what decides, not where they stand).
                    world.scurriusLoot.damaged(boss, a, 20)
                    world.scurriusLoot.damaged(boss, b, 80)
                    val (x, z) = boss.x to boss.z
                    bob.commandReply("kill ${boss.index}") { it.startsWith("Killed") }
                    bob.await("his kill count") { bob.messages().firstOrNull { it.text.contains("scurrius.kill_count") } }
                    alice.await("hers") { alice.messages().firstOrNull { it.text.contains("scurrius.kill_count") } }
                    assertEquals(1, b.vars[Scurrius.KILL_COUNT_VARP])
                    assertEquals(1, a.vars[Scurrius.KILL_COUNT_VARP])

                    val centre = bob.await("his loot", timeoutMillis = 20_000) { world.groundObjs.visibleAt(b, x + 1, z + 1, 0).takeIf { it.isNotEmpty() } }
                    val bobs = centre.map { it.id }
                    assertTrue(ScurriusLoot.BIG_BONES in bobs && ScurriusLoot.RAW_RAT_MEAT in bobs, "the MVP's two always items: $bobs")
                    val alices = world.groundObjs.visibleAt(a, x + 1, z + 1, 0).filter { it.owner == a.saveKey }.map { it.id }
                    assertTrue(ScurriusLoot.BIG_BONES in alices, "big bones for the rest too: $alices")
                    assertTrue(ScurriusLoot.RAW_RAT_MEAT !in alices, "but no rat meat, which only the MVP table has: $alices")
                    assertTrue(alices.none { it == ScurriusLoot.PET }, "no pet off the non-MVP table")
                    assertTrue(world.groundObjs.visibleAt(b, x, z, 0).none { it.id == ScurriusLoot.BIG_BONES }, "nothing but bones, clues on his corner")
                }
            }
        }
    }
}
