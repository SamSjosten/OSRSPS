package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.Bryophyta
import osrsps.game.content.BryophytaLines
import osrsps.game.content.GiantLairs
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Bryophyta (Misthalin bosses step 2) through the headless client and the registered handlers: the gate in with its
 * warning and menu, Check-count, a lair of the player's own, her fight with its growthlings (her shield while they live,
 * the logs' bronze axe, an axe used on each to finish it), her giant bones, the chest taking the mossy key, and the rock
 * pile out. The locs are placed as the 242 cache has them, on open ground. The rl-client script
 * test-runner/examples/bryophyta.json does the same on the real map.
 */
class HeadlessBryophytaTest {
    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val types = LocTypes(
        listOf(
            type(Bryophyta.GATE, "Gate", "Open", "Check-Count"),
            type(Bryophyta.EXIT, "Rock Pile", "Clamber", null, "Quick-exit"),
            type(Bryophyta.LOGS, "Logs", "Take-axe"),
            type(Bryophyta.LOGS_EMPTY, "Logs"),
            type(Bryophyta.CHEST, "Chest", "Open"),
        ).associateBy { it.id },
    )

    private val locs = listOf(
        ZoneLoc(Bryophyta.GATE, LocShape.WALL_STRAIGHT, 3, 3174, 9901, 0),
        ZoneLoc(Bryophyta.EXIT, LocShape.CENTREPIECE_STRAIGHT, 1, 3212, 9937, 0),
        ZoneLoc(Bryophyta.LOGS, LocShape.CENTREPIECE_STRAIGHT, 0, 3228, 9932, 0),
        ZoneLoc(Bryophyta.CHEST, LocShape.CENTREPIECE_STRAIGHT, 1, 3229, 9934, 0),
    )

    private val bryophyta = NpcType(
        Bryophyta.BOSS, "Bryophyta", size = 3, ops = listOf(null, "Attack", null, null, null), combatLevel = 128, internalName = "gb_mossgiant",
        stats = NpcStats(attack = 130, defence = 100, strength = 100, hitpoints = 115, ranged = 1, magic = 90),
    )
    private val growthling = NpcType(
        Bryophyta.GROWTHLING, "Growthling", size = 1, ops = listOf(null, "Attack", null, null, null), combatLevel = 37, internalName = "gb_growthling",
        stats = NpcStats(attack = 40, defence = 40, strength = 38, hitpoints = 10, ranged = 1, magic = 1),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            markLoaded(3174 shr 6, 9901 shr 6)
            markLoaded(3208 shr 6, 9920 shr 6)
        }
        val npcTypes = NpcTypes.of(bryophyta, growthling)
        val drops = DropTestItems.objTypes()
        val extra = listOf(
            ObjType(Bryophyta.MOSSY_KEY, "Mossy key", stackable = true), ObjType(GiantLairs.GIANT_BONES, "Giant bones", ops = listOf(null, null, "Bury", "Take", null)),
            ObjType(Bryophyta.BRONZE_AXE, "Bronze axe", category = 35), ObjType(Bryophyta.SECATEURS, "Secateurs"), ObjType(1373, "Rune battleaxe", category = 35),
            ObjType(22372, "Bryophyta's essence"), ObjType(6800, "Giant champion scroll"), ObjType(1620, "Uncut ruby", stackable = true),
            ObjType(1618, "Uncut diamond", stackable = true), ObjType(1124, "Adamant platebody", stackable = true), ObjType(1392, "Battlestaff", stackable = true),
            ObjType(2354, "Steel bar", stackable = true), ObjType(1179, "Black sq shield"), ObjType(1389, "Magic staff"), ObjType(1285, "Mithril shortsword"),
            ObjType(1243, "Mithril spear"), ObjType(1193, "Steel kiteshield"), ObjType(1141, "Steel med helm"), ObjType(1185, "Rune sq shield"),
            ObjType(1289, "Rune shortsword"), ObjType(1199, "Adamant kiteshield"), ObjType(890, "Adamant arrow", stackable = true), ObjType(2363, "Runite bar"),
            ObjType(2353, "Steel bar"), ObjType(453, "Coal"), ObjType(1969, "Spinach roll"), ObjType(556, "Air rune", stackable = true),
            ObjType(557, "Earth rune", stackable = true), ObjType(565, "Blood rune", stackable = true),
        )
        val objTypes = ObjTypes.of(*((0 until drops.size).mapNotNull { drops[it] }.filter { d -> extra.none { it.id == d.id } } + extra).toTypedArray())
        World(
            crcs, map, random = GameRandom(7), objTypes = objTypes, locTypes = types, staticLocs = StaticLocs().apply { locs.forEach(::add) },
            npcTypes = npcTypes, npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), npcDrops = NpcDrops.load(npcTypes, objTypes), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.count(id: Int): Int = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.pick(title: Line, option: Int) {
        awaitDialogue("the menu ${title.key}") { it is DialogueView.Menu && it.title == DialogueText.NONE[title] }
        chooseOption(option)
    }

    private fun HeadlessClient.goIn() {
        locOp(Bryophyta.GATE, 3174, 9901, Bryophyta.OPEN)
        awaitDialogue("the warning") { it is DialogueView.Message && it.text == DialogueText.NONE[BryophytaLines.GATE_WARNING] }
        continueDialogue()
        pick(BryophytaLines.GATE_TITLE, 1)
    }

    @Test
    fun `the gate warns and asks, unlocks for good with a mossy key, and counts the chests`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(Bryophyta.EXIT_TILE.x, Bryophyta.EXIT_TILE.z, 0)
                val alice = server.world.playerNamed("alice")!!
                client.locOp(Bryophyta.GATE, 3174, 9901, Bryophyta.OPEN)
                client.seen(DialogueText.NONE[BryophytaLines.GATE_LOCKED])

                client.commandReply("item ${Bryophyta.MOSSY_KEY} 1") { it.contains("Mossy key") }
                // The warning, then the menu's no: she says she decided not to, and nothing is unlocked.
                client.locOp(Bryophyta.GATE, 3174, 9901, Bryophyta.OPEN)
                client.awaitDialogue("the warning") { it is DialogueView.Message && it.text == DialogueText.NONE[BryophytaLines.GATE_WARNING] }
                client.continueDialogue()
                client.pick(BryophytaLines.GATE_TITLE, 2)
                client.seen(DialogueText.NONE[BryophytaLines.GATE_DECLINED])
                assertFalse(server.world.giantLairs.unlocked(alice, Bryophyta.LAIR))
                assertNull(server.world.giantLairs.runOf(alice))

                client.goIn()
                client.seen(DialogueText.NONE[BryophytaLines.GATE_OPENED])
                val run = client.await("inside a lair") { server.world.giantLairs.runOf(alice) }
                val entry = run.inside(Bryophyta.ENTRY.x, Bryophyta.ENTRY.z)
                client.await("at the lair's entry") { client.position?.takeIf { it == Coord(entry.x, entry.z, 0) } }
                assertEquals(1, client.count(Bryophyta.MOSSY_KEY), "the key is kept")

                // Quick-exit on the rock pile, then in again with no key: it stays unlocked.
                val pile = run.inside(3212, 9937)
                client.locOp(Bryophyta.EXIT, pile.x, pile.z, Bryophyta.QUICK_EXIT)
                client.await("back in the sewer") { client.position?.takeIf { it == Coord(Bryophyta.EXIT_TILE.x, Bryophyta.EXIT_TILE.z, 0) } }
                client.command("empty")
                client.await("an empty backpack") { if (client.count(Bryophyta.MOSSY_KEY) == 0) Unit else null }
                client.locOp(Bryophyta.GATE, 3174, 9901, Bryophyta.CHECK_COUNT)
                client.awaitDialogue("the count") { it is DialogueView.Message && it.text.contains("bryophyta.check_count") }
                client.continueDialogue()
                client.goIn()
                client.await("inside a lair again") { server.world.giantLairs.runOf(alice) }
                assertTrue(server.world.giantLairs.unlocked(alice, Bryophyta.LAIR))
            }
        }
    }

    @Test
    fun `growthlings shield her until an axe cuts them, then the chest takes the key`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(Bryophyta.EXIT_TILE.x, Bryophyta.EXIT_TILE.z, 0)
                client.commandReply("item ${Bryophyta.MOSSY_KEY} 2") { it.contains("Mossy key") }
                client.commandReply("god") { it.startsWith("You can") }
                client.command("setlevel attack 99")
                client.command("setlevel strength 99")
                client.goIn()
                val bob = server.world.playerNamed("bob")!!
                val run = client.await("inside a lair") { server.world.giantLairs.runOf(bob) }
                val boss = assertNotNull(run.boss, "Bryophyta is in her lair")
                val fight = server.world.encounters.all.single { it.boss === boss }
                client.await("her first attack", timeoutMillis = 20_000) { fight.lines.firstOrNull { it.contains("swings at bob") || it.contains("casts at bob") } }

                // The logs give a bronze axe once, and refuse while one is held.
                val logs = run.inside(3228, 9932)
                client.locOp(Bryophyta.LOGS, logs.x, logs.z, Bryophyta.TAKE_AXE)
                client.await("a bronze axe") { if (client.count(Bryophyta.BRONZE_AXE) == 1) Unit else null }

                // Down past a quarter of her hitpoints, she brings growthlings while bob attacks her.
                boss.hitpoints = 80
                client.npcOp(boss.index, 2)
                client.await("growthlings", timeoutMillis = 90_000) { Bryophyta.state(boss).growthlings.takeIf { it.size == Bryophyta.GROWTHLINGS } }
                assertTrue(Bryophyta.shielded(server.world, boss))
                val shieldedAt = boss.hitpoints
                client.awaitTicks(8)
                assertEquals(shieldedAt, boss.hitpoints, "no damage while they live")
                assertTrue(server.world.combat.immune(boss), "a shot or spell landing now does nothing either")
                assertEquals(0, server.world.combat.rolledOn(bob, boss, AttackType.CRUSH, 20), "nothing rolled against her shield, so no XP")
                assertFalse(server.world.combat.finishers[Bryophyta.GROWTHLING]!!(bob, AttackType.MAGIC), "a spell never finishes one")
                assertFalse(server.world.combat.finishers[Bryophyta.GROWTHLING]!!(bob, null), "nor damage of no attack (Retribution)")
                // Her lair is multi-way, so they fight bob while she does.
                assertTrue(server.world.multiCombat.at(bob.level, bob.x, bob.z), "multi-way in her lair")
                assertFalse(server.world.multiCombat.at(0, Bryophyta.EXIT_TILE.x, Bryophyta.EXIT_TILE.z), "single-way outside it")
                client.await("a growthling on bob", timeoutMillis = 20_000) { Bryophyta.state(boss).growthlings.firstOrNull { it.target === bob } }

                // An axe used on each finishes it; a battleaxe doesn't.
                val first = Bryophyta.state(boss).growthlings.first()
                client.commandReply("item 1373 1") { it.contains("battleaxe") }
                client.npcTarget(first.index, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(1373), 1373)
                client.awaitTicks(3)
                assertFalse(first.dead, "a battleaxe doesn't cut a growthling")
                for (growthling in Bryophyta.state(boss).growthlings.toList()) {
                    client.npcTarget(growthling.index, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(Bryophyta.BRONZE_AXE), Bryophyta.BRONZE_AXE)
                    client.await("growthling ${growthling.index} cut", timeoutMillis = 20_000) { if (growthling.dead) Unit else null }
                }
                assertFalse(Bryophyta.shielded(server.world, boss))
                // The cooldown runs from the tick the last one died, not the tick she notices.
                val lastDied = Bryophyta.state(boss).lastGone
                client.await("the cooldown") { Bryophyta.state(boss).growthAfter.takeIf { it != Long.MIN_VALUE } }
                assertEquals(lastDied + Bryophyta.GROWTH_COOLDOWN_TICKS, Bryophyta.state(boss).growthAfter)

                client.commandReply("kill ${boss.index}") { it.startsWith("Killed") }
                client.await("her death") { if (boss.dead) Unit else null }
                client.await("her giant bones", timeoutMillis = 20_000) { client.groundObjs().firstOrNull { it.id == GiantLairs.GIANT_BONES } }

                val chest = run.inside(3229, 9934)
                client.locOp(Bryophyta.CHEST, chest.x, chest.z, Bryophyta.OPEN)
                client.seen(DialogueText.NONE[BryophytaLines.CHEST_STIR])
                client.seen(DialogueText.NONE[BryophytaLines.CHEST_LOOT])
                client.await("the count") { client.messages().firstOrNull { it.text.contains("bryophyta.kill_count") } }
                assertEquals(1, bob.vars[Bryophyta.KILL_COUNT_VARP])
                assertEquals(1, client.count(Bryophyta.MOSSY_KEY), "one key used")
                client.await("the chest's clue on the floor") { client.groundObjs().firstOrNull { it.id == CLUE_BEGINNER } }
                client.await("her back after 15 ticks", timeoutMillis = 30_000) { run.boss?.takeIf { !it.dead } }

                // Clamber on the rock pile leaves at once (no menu).
                val pile = run.inside(3212, 9937)
                client.locOp(Bryophyta.EXIT, pile.x, pile.z, Bryophyta.CLAMBER)
                client.await("back in the sewer") { client.position?.takeIf { it == Coord(Bryophyta.EXIT_TILE.x, Bryophyta.EXIT_TILE.z, 0) } }
            }
        }
    }

    private fun HeadlessClient.slotOf(id: Int) = inventory(BACKPACK).indexOfFirst { it?.id == id }

    private companion object {
        const val BACKPACK = 93
        const val CLUE_BEGINNER = 23182
    }
}
