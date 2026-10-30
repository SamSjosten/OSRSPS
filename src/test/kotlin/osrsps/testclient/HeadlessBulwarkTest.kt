package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.EquipmentTab
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.AttackStyles
import osrsps.game.combat.DinhsBulwark
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.SpecialAttacks
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Dinh's bulwark through the headless client: Block refuses an attack; in multi-way combat Shield Bash hits the target
 * and the NPCs within 5 tiles, draining 5% of the highest offensive stat of each it hurts (the target's once, though it
 * is hit twice), and not one 6 tiles off or one standing in single-way combat; in single-way combat it hits the target
 * alone. Off Block the next attack waits 8 ticks, and Block cuts hits 8 ticks after the tick the bulwark is wielded on. The rl-client script
 * test-runner/examples/spec-bulwark.json does the multi-way bash in the real client.
 */
class HeadlessBulwarkTest {
    private val ogre = NpcType(
        2095, "Ogre", ops = listOf(null, "Attack", null, null, null), combatLevel = 53, internalName = "ogre",
        stats = NpcStats(attack = 100, defence = 1, strength = 40, hitpoints = 5000),
    )

    // Shaped like the 241 cache's: param 14 its attack speed, obj category 1014, which is the bulwark's weapon category 28.
    private val bulwark = ObjType(
        BULWARK, "Dinh's bulwark", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, wearpos2 = WearPos.SHIELD,
        params = mapOf(14 to 5), internalName = "dinhs_bulwark", category = 1014,
    )

    // The unarmed and bulwark rows of dbtable 78; the Block tooltip names no attack type.
    private val styles = AttackStyles.fromRows(
        listOf(
            listOf<Any>(0) to listOf<Any>(0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)", 0),
            listOf<Any>(28) to listOf<Any>(
                0, "Pummel", "(Accurate)<br>(Crush)<br>(Attack XP)", 0,
                3, "Block", "(Block)<br>(No XP)", 0,
            ),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(ogre)
        val objs = arrayOfNulls<ObjType>(BULWARK + 1)
        objs[BULWARK] = bulwark
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = ObjTypes(objs), npcTypes = types,
            // The target east of the login tile, two more within 5 tiles of where the player stands to hit it, one 6 off,
            // and one within 5 but in the zone south, which stays single-way.
            npcSpawns = listOf(3224 to 3218, 3221 to 3221, 3226 to 3222, 3229 to 3218, 3221 to 3214).map { (x, z) -> NpcSpawn(ogre, x, z, source = "test") },
            varTypes = VarTypes(6_000, mapOf(VARBIT_WEAPON_CATEGORY to VarbitType(VARP_WEAPON_CATEGORY, 0, 5))),
            attackStyles = styles,
            npcCombat = NpcCombat.parse(
                """{ "npcs": [ { "npcs": ["ogre"], "attackSpeed": 5, "maxHit": 1, "attackType": "crush", "attackBonus": 0,
                    "strengthBonus": 0,
                    "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 0, "light": 0, "standard": 0, "heavy": 0 },
                    "anims": { "attack": null, "defend": null, "death": null }, "respawnTicks": 25, "source": "test" } ] }""",
                types, SeqNames.NONE,
            ),
            specialAttackCosts = mapOf(BULWARK to 500), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.slot(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.wieldBulwark() {
        command("item $BULWARK 1")
        ifButton(INVENTORY, INVENTORY_ITEMS, WIELD_BUTTON, slot(BULWARK), BULWARK)
        await("the bulwark wielded") { inventory(WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == BULWARK } }
        commandReply("god") { it.startsWith("You can") }
        commandReply("sure") { it.startsWith("Your attacks can't miss") }
        commandReply("setlevel strength 99") { it.contains("99") }
    }

    /** Each ogre's line in `::npcs`, by index. */
    private fun HeadlessClient.ogres(indexes: List<Int>): Map<Int, String> {
        val from = messages().size
        command("npcs 15")
        return indexes.associateWith { index ->
            await("ogre #$index's line") { messages().drop(from).firstOrNull { it.text.startsWith("#$index Ogre") }?.text }
        }
    }

    private fun hurt(line: String): Boolean = line.contains("/5000 hp")

    private fun HeadlessClient.bash(target: Int) {
        ifButton(EquipmentTab.COMBAT, EquipmentTab.COMBAT_SPECIAL, 1)
        await("the bar lit") { Unit.takeIf { varp(SpecialAttacks.VARP_ACTIVE) == 1 } }
        npcOp(target, 2)
        await("the bash swung", timeoutMillis = 15_000) { Unit.takeIf { varp(SpecialAttacks.VARP_ENERGY) == 500 } }
        awaitTicks(2)
    }

    @Test
    fun `Block can't attack`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("an ogre in view") { client.npcs().firstOrNull { it.id == ogre.id } }
                client.await("the login rebuild") { client.position }
                client.wieldBulwark()
                client.ifButton(EquipmentTab.COMBAT, EquipmentTab.STYLE_BUTTONS[3], 1)
                client.await("Block picked") { Unit.takeIf { client.varp(EquipmentTab.VARP_COM_MODE) == 3 } }
                client.npcOp(npc.index, 2)
                client.awaitMessage("the refusal") { it == "[combat.bulwark_block]" }
            }
        }
    }

    @Test
    fun `off Block, the next attack waits 8 ticks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("an ogre in view") { client.npcs().firstOrNull { it.id == ogre.id && it.coord.x == 3224 } }
                client.await("the login rebuild") { client.position }
                client.wieldBulwark()
                client.ifButton(EquipmentTab.COMBAT, EquipmentTab.STYLE_BUTTONS[3], 1)
                client.await("Block picked") { Unit.takeIf { client.varp(EquipmentTab.VARP_COM_MODE) == 3 } }
                // Paused, so the attack goes in on the tick Pummel is picked, and the swing is counted in game ticks.
                client.commandReply("pause") { it.startsWith("Paused after tick") }
                client.ifButton(EquipmentTab.COMBAT, EquipmentTab.STYLE_BUTTONS[0], 1)
                client.npcOp(npc.index, 2)
                client.stepTicks(1)
                val world = server.world
                val picked = world.tick
                assertEquals(picked + DinhsBulwark.BLOCK_DELAY, world.playerNamed("alice")!!.nextAttackTick)
                client.stepTicks(DinhsBulwark.BLOCK_DELAY - 1)
                assertEquals(5000, world.npcs[npc.index]!!.hitpoints, "no swing before the 8 ticks are up")
                client.stepTicks(2)
                client.commandReply("resume") { it.startsWith("Resumed") }
                client.await("the first swing", timeoutMillis = 15_000) { world.npcs[npc.index]!!.takeIf { it.hitpoints < 5000 } }
            }
        }
    }

    @Test
    fun `Block cuts hits only once the tick the bulwark is wielded on has ended, and 8 ticks after it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.awaitTicks(DinhsBulwark.BLOCK_DELAY + 2)
                client.commandReply("pause") { it.startsWith("Paused after tick") }
                val world = server.world
                val player = world.playerNamed("alice")!!
                // Wielded and on Block by the input phase of the next tick, as the NPCs that tick see it.
                player.equipment[WearPos.WEAPON] = InvObj(BULWARK, 1)
                player.vars[EquipmentTab.VARP_COM_MODE] = 3
                assertFalse(world.combat.bulwarkBlocking(player, world.tick + 1), "on the tick it is wielded")
                client.stepTicks(1)
                // The tick's end records it, and Block counts its 8 ticks from that tick.
                assertEquals(BULWARK, player.weaponId)
                assertTrue(player.weaponSince > DinhsBulwark.BLOCK_DELAY, "wielded on tick ${player.weaponSince}")
                assertFalse(world.combat.bulwarkBlocking(player, player.weaponSince + DinhsBulwark.BLOCK_DELAY - 1))
                assertTrue(world.combat.bulwarkBlocking(player, player.weaponSince + DinhsBulwark.BLOCK_DELAY))
                client.commandReply("resume") { it.startsWith("Resumed") }
            }
        }
    }

    @Test
    fun `in multi-way combat Shield Bash hits everything within 5 tiles, and drains each it hurts once`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the ogres in view") { client.npcs().filter { it.id == ogre.id }.takeIf { it.size == 5 } }
                client.await("the login rebuild") { client.position }
                client.wieldBulwark()
                // Multi-way in the login tile's zone and the one east, where the target and one of the near two stand.
                client.commandReply("multi on") { it.startsWith("Combat is multi-way") }
                client.tele(3226, 3222)
                client.commandReply("multi on") { it.startsWith("Combat is multi-way") }
                client.tele(3222, 3218)
                val indexes = client.npcs().filter { it.id == ogre.id }.associate { (it.coord.x to it.coord.z) to it.index }
                val target = indexes.getValue(3224 to 3218)
                val near = listOf(indexes.getValue(3221 to 3221), indexes.getValue(3226 to 3222))
                val far = indexes.getValue(3229 to 3218)
                val single = indexes.getValue(3221 to 3214)
                client.bash(target)
                val lines = client.ogres(listOf(target) + near + far + single)
                assertTrue((listOf(target) + near).any { hurt(lines.getValue(it)) }, "the bash hurt something: $lines")
                for (index in listOf(target) + near) {
                    val line = lines.getValue(index)
                    // 5% of 100 is 5, and the target's second hit doesn't drain it again.
                    if (hurt(line)) assertTrue(line.contains("drained attack 95/100,"), line) else assertTrue("drained" !in line, line)
                }
                assertEquals(false, hurt(lines.getValue(far)) || "drained" in lines.getValue(far), lines.getValue(far))
                assertEquals(false, hurt(lines.getValue(single)) || "drained" in lines.getValue(single), "single-way: ${lines.getValue(single)}")
            }
        }
    }

    @Test
    fun `in single-way combat Shield Bash hits its target alone`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the ogres in view") { client.npcs().filter { it.id == ogre.id }.takeIf { it.size == 5 } }
                client.await("the login rebuild") { client.position }
                client.wieldBulwark()
                val target = client.npcs().first { it.id == ogre.id && it.coord.x == 3224 && it.coord.z == 3218 }.index
                val others = client.npcs().filter { it.id == ogre.id && it.index != target }.map { it.index }
                client.bash(target)
                val lines = client.ogres(others)
                for (index in others) assertEquals(false, hurt(lines.getValue(index)), lines.getValue(index))
            }
        }
    }

    companion object {
        const val BULWARK = 21015

        // As HeadlessShadowTest has them.
        private const val BACKPACK = 93
        private const val WORN = 94
        private const val INVENTORY = 149
        private const val INVENTORY_ITEMS = 0
        private const val WIELD_BUTTON = 3
        private const val VARBIT_WEAPON_CATEGORY = 357
        private const val VARP_WEAPON_CATEGORY = 843
    }
}
