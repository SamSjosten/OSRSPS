package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.Potions
import osrsps.map.CollisionMap

/**
 * Drinking through the headless client, with the real potion file: a super combat dose boosts the three stats by 19
 * and leaves three doses, and a Saradomin brew after it heals past the level. The rl-client script
 * test-runner/examples/potions.json drinks more of them in the real client.
 */
class HeadlessPotionTest {
    private fun dose(id: Int, internal: String, name: String) = ObjType(id, name, iops = listOf("Drink", null, null, null, "Drop"), internalName = internal)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(SUPER_COMBAT_3 + 1)
        objs[SUPER_COMBAT_4] = dose(SUPER_COMBAT_4, "4dose2combat", "Super combat potion(4)")
        objs[SUPER_COMBAT_3] = dose(SUPER_COMBAT_3, "3dose2combat", "Super combat potion(3)")
        objs[BREW_4] = dose(BREW_4, "4dosepotionofsaradomin", "Saradomin brew(4)")
        objs[BREW_3] = dose(BREW_3, "3dosepotionofsaradomin", "Saradomin brew(3)")
        val objTypes = ObjTypes(objs)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objTypes, potionData = Potions.load(objTypes),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    @Test
    fun `a super combat dose boosts by 19 and leaves 3 doses, and a brew after the delay heals past the level`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                for (skill in listOf("attack", "strength", "defence", "hitpoints")) client.commandReply("setlevel $skill 99") { it.contains("set to level 99") }
                client.commandReply("statcycles") { it.startsWith("Boost and drain cycles started again") }
                client.command("item $SUPER_COMBAT_4")
                client.command("item $BREW_4")
                val combat = client.slotOf(SUPER_COMBAT_4)
                val brew = client.slotOf(BREW_4)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, DRINK_BUTTON_OP, combat, SUPER_COMBAT_4)
                client.await("Attack 118") { client.stat(Skill.ATTACK.id)?.takeIf { it.level == 118 } }
                client.await("Defence 118") { client.stat(Skill.DEFENCE.id)?.takeIf { it.level == 118 } }
                client.await("3 doses left") { if (client.inventory(BACKPACK)[combat]?.id == SUPER_COMBAT_3) Unit else null }
                client.awaitTicks(Potions.DRINK_DELAY)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, DRINK_BUTTON_OP, brew, BREW_4)
                // 99 * 15% + 2 past the level, and 118 - (11 + 2).
                client.await("115 hitpoints") { client.stat(Skill.HITPOINTS.id)?.takeIf { it.level == 115 } }
                client.await("Attack 105") { client.stat(Skill.ATTACK.id)?.takeIf { it.level == 105 } }
            }
        }
    }

    private companion object {
        const val BREW_4 = 6685
        const val BREW_3 = 6687
        const val SUPER_COMBAT_4 = 12695
        const val SUPER_COMBAT_3 = 12697
        const val BACKPACK = 93

        /** Drink is iop1, which the inventory sends as button op 2. */
        const val DRINK_BUTTON_OP = 2
    }
}
