package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.Poison
import osrsps.game.combat.Potions
import osrsps.map.CollisionMap

/**
 * Poison through the headless client: `::poison 20` hits 4 a full 30 ticks on and the client's poison varp goes to 19,
 * an antipoison dose then leaves 5 periods of immunity, and a poison hit can kill, the death curing it. The rl-client
 * script test-runner/examples/poison.json does the same in the real client.
 */
class HeadlessPoisonTest {
    private fun dose(id: Int, internal: String, name: String) = ObjType(id, name, iops = listOf("Drink", null, null, null, "Drop"), internalName = internal)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(ANTIPOISON_4 + 1)
        objs[ANTIPOISON_4] = dose(ANTIPOISON_4, "4doseantipoison", "Antipoison(4)")
        objs[ANTIPOISON_3] = dose(ANTIPOISON_3, "3doseantipoison", "Antipoison(3)")
        val objTypes = ObjTypes(objs)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objTypes, potionData = Potions.load(objTypes),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.hitpoints() = stat(Skill.HITPOINTS.id)?.level

    @Test
    fun `poison hits 4 after 30 ticks and an antipoison cures it, leaving 5 periods of immunity`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel hitpoints 99") { it.contains("set to level 99") }
                client.command("item $ANTIPOISON_4")
                val slot = client.await("the antipoison") { client.inventory(BACKPACK).indexOfFirst { it?.id == ANTIPOISON_4 }.takeIf { it >= 0 } }
                client.commandReply("poison 20") { it.startsWith("Poisoned, hitting 4") }
                client.await("poison varp 20") { client.varp(Poison.VARP).takeIf { it == 20 } }
                client.awaitTicks(Poison.TICKS - 5)
                check(client.hitpoints() == 99) { "hit before 30 ticks: ${client.hitpoints()}" }
                client.await("95 hitpoints", timeoutMillis = 10_000) { client.hitpoints()?.takeIf { it == 95 } }
                client.await("poison varp 19") { client.varp(Poison.VARP).takeIf { it == 19 } }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, DRINK_BUTTON_OP, slot, ANTIPOISON_4)
                client.await("immune for 5 periods") { client.varp(Poison.VARP).takeIf { it == -5 } }
                client.commandReply("poison 20") { it.startsWith("Not poisoned: you are immune") }
            }
        }
    }

    @Test
    fun `a poison hit can kill, and dying cures the poison`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel hitpoints 99") { it.contains("set to level 99") }
                client.commandReply("hit 96 me") { it.startsWith("Hit yourself for 96") }
                client.commandReply("poison 20") { it.startsWith("Poisoned, hitting 4") }
                client.await("3 hitpoints") { client.hitpoints()?.takeIf { it == 3 } }
                client.await("dead", timeoutMillis = 10_000) { client.hitpoints()?.takeIf { it == 0 } }
                client.await("back at full hitpoints with no poison", timeoutMillis = 10_000) {
                    if (client.hitpoints() == 99 && client.varp(Poison.VARP) == 0) Unit else null
                }
            }
        }
    }

    private companion object {
        const val ANTIPOISON_4 = 2446
        const val ANTIPOISON_3 = 175
        const val BACKPACK = 93

        /** Drink is iop1, which the inventory sends as button op 2. */
        const val DRINK_BUTTON_OP = 2
    }
}
