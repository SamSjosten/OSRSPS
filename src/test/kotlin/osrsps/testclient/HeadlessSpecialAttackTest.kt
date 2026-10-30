package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.EquipmentTab
import osrsps.game.GameFrame
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.SpecialAttacks
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * The dragon warhammer's Smash through the headless client: the combat tab's bar lights, the next swing takes half the
 * energy and drains 30% of a boss's Defence as it is now (75 to 53 to 38), and the 60% cap stops it at 30. The orb lights
 * the bar too, and without the energy neither does. The rl-client script test-runner/examples/spec-warhammer.json does
 * the same in the real client.
 */
class HeadlessSpecialAttackTest {
    private val boss = NpcType(
        16659, "Archive Guardian", ops = listOf(null, "Attack", null, null, null), combatLevel = 400, internalName = "archive_guardian",
        stats = NpcStats(attack = 1, defence = 75, hitpoints = 5000),
    )

    // Shaped like the 241 cache's: param 14 its attack speed.
    private val warhammer = ObjType(
        WARHAMMER, "Dragon warhammer", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON,
        params = mapOf(14 to 6), internalName = "dragon_warhammer",
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(boss)
        val objs = arrayOfNulls<ObjType>(WARHAMMER + 1)
        objs[WARHAMMER] = warhammer
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = ObjTypes(objs), npcTypes = types,
            npcSpawns = listOf(NpcSpawn(boss, 3224, 3218, source = "test")),
            npcCombat = NpcCombat.parse(
                """{ "npcs": [ { "npcs": ["archive_guardian"], "attackSpeed": 5, "maxHit": 1, "attackType": "crush", "attackBonus": 0,
                    "strengthBonus": 0, "drainCap": { "defence": 60 },
                    "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 0, "light": 0, "standard": 0, "heavy": 0 },
                    "anims": { "attack": null, "defend": null, "death": null }, "respawnTicks": 25, "source": "test" } ] }""",
                types, SeqNames.NONE,
            ),
            specialAttackCosts = mapOf(WARHAMMER to 500), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slot(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.energy() = varp(SpecialAttacks.VARP_ENERGY)

    private fun HeadlessClient.lit() = varp(SpecialAttacks.VARP_ACTIVE) == 1

    private fun HeadlessClient.spec(energyAfter: Int, defence: String, index: Int) {
        ifButton(EquipmentTab.COMBAT, EquipmentTab.COMBAT_SPECIAL, 1)
        // Lit in the input phase and swung in the same tick's movement phase, so the client may never see it lit.
        await("the special swung", timeoutMillis = 15_000) { Unit.takeIf { !lit() && energy() == energyAfter } }
        // It lands in the next tick's NPC phase.
        awaitTicks(2)
        commandReply("npcs 5") { it.startsWith("#$index Archive Guardian") }.let {
            assertEquals(true, it.contains("drained defence $defence/75"), it)
        }
    }

    @Test
    fun `Smash takes half the energy and drains 30 percent of the Defence a time, down to the cap`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("the boss in view") { client.npcs().firstOrNull { it.id == boss.id } }
                client.await("the login rebuild") { client.position }
                client.await("a new account's full energy") { Unit.takeIf { client.energy() == SpecialAttacks.FULL } }
                client.command("item $WARHAMMER 1")
                client.ifButton(INVENTORY, INVENTORY_ITEMS, WIELD_BUTTON, client.slot(WARHAMMER), WARHAMMER)
                client.await("the warhammer wielded") { client.inventory(WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == WARHAMMER } }
                client.commandReply("god") { it.startsWith("You can") }
                // Max hits, as a hit of 0 drains nothing.
                client.commandReply("sure max") { it.startsWith("Your attacks can't miss and your melee hits deal their max") }
                client.commandReply("setlevel strength 99") { it.contains("99") }

                client.npcOp(npc.index, 2)
                client.spec(500, "53", npc.index)
                client.spec(0, "38", npc.index)
                // Empty: the bar won't light.
                client.ifButton(EquipmentTab.COMBAT, EquipmentTab.COMBAT_SPECIAL, 1)
                client.awaitMessage("the refusal") { it == "[combat.spec_not_enough]" }
                assertEquals(false, client.lit())
                // 30% of 38 is 11, but the cap keeps 30 of the 75; a fourth drains nothing.
                client.commandReply("spec 100") { it.startsWith("Special attack energy 100%") }
                client.ifButton(GameFrame.ORBS, GameFrame.ORBS_SPEC_BUTTON, 1)
                client.await("the special swung from the orb", timeoutMillis = 15_000) { Unit.takeIf { !client.lit() && client.energy() == 500 } }
                client.spec(0, "30", npc.index)
            }
        }
    }

    companion object {
        const val WARHAMMER = 13576

        // As HeadlessEquipmentTest has them.
        private const val BACKPACK = 93
        private const val WORN = 94
        private const val INVENTORY = 149
        private const val INVENTORY_ITEMS = 0
        private const val WIELD_BUTTON = 3
    }
}
