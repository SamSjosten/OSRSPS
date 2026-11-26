package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.Spells
import osrsps.game.content.EdgevilleRespawn
import osrsps.game.skill.magic.TeleportObjs
import osrsps.map.CollisionMap

/**
 * The jewellery that saves its wearer (Magic utility step U6a) through the headless client, hit with `::hit n me`: a
 * phoenix necklace heals 30% of 10 Hitpoints when a hit leaves 2 and is gone; a necklace of faith restores a quarter
 * of 40 Prayer when a hit leaves 1; a ring of life takes its wearer to Lumbridge when a hit leaves 1, and not before, or to Edgeville for a player
 * who chose Krystilia's respawn.
 * test-runner/examples/magic-jewellery-effects.json does it in RuneLite.
 */
class HeadlessJewelleryEffectsTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objTypes = TeleportObjs.objTypes(
            ObjType(PHOENIX, "Phoenix necklace", wearpos1 = WearPos.NECK, iops = WEARABLE, internalName = "jewl_necklace_of_phoenix"),
            ObjType(FAITH, "Necklace of faith", wearpos1 = WearPos.NECK, iops = WEARABLE, internalName = "necklace_of_faith"),
            ObjType(RING_OF_LIFE, "Ring of life", wearpos1 = WearPos.RING, iops = WEARABLE, internalName = "ring_of_life"),
        )
        World(
            crcs, CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(EdgevilleRespawn.X shr 6, EdgevilleRespawn.Z shr 6)
            }, random = osrsps.game.GameRandom(7), objTypes = objTypes,
            spells = Spells.load(objTypes, osrsps.game.SeqNames.NONE, osrsps.game.SpotanimNames.NONE), cycleMillis = millis,
            // edgeville_spawn and edgeville_spawn_unlocked, on cws_var and cws_var_2, as the 242 cache has them.
            varTypes = VarTypes(6_000, mapOf(EdgevilleRespawn.VARBIT_SPAWN to VarbitType(1045, 31, 31), EdgevilleRespawn.VARBIT_UNLOCKED to VarbitType(1046, 0, 0))),
        )
    }

    private fun HeadlessClient.ready() {
        await("the login rebuild") { position }
        commandReply("keys on") { it == "Dialogue shows keys" }
    }

    private fun HeadlessClient.wear(id: Int, wearpos: Int) {
        command("item $id 1")
        val slot = await("item $id") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }
        ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, WEAR_BUTTON_OP, slot, id)
        await("item $id worn") { inventory(WORN).getOrNull(wearpos)?.takeIf { it.id == id } }
    }

    private fun HeadlessClient.hit(damage: Int, hitpoints: Int) =
        commandReply("hit $damage me") { it.startsWith("Hit yourself") }.also { assertEquals("Hit yourself for $damage: $hitpoints/10 hp", it) }

    private fun HeadlessClient.saw(description: String, text: String) =
        await(description) { if (messages().any { it.text == text }) Unit else null }

    @Test
    fun `a phoenix necklace heals 3 of 10 when a hit leaves 2, and is gone`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.wear(PHOENIX, WearPos.NECK)
                client.hit(7, 3)
                assertEquals(PHOENIX, client.inventory(WORN).getOrNull(WearPos.NECK)?.id, "3 of 10 is above 20%")
                client.hit(1, 5)
                client.saw("the heal", "[jewellery_effect.phoenix]")
                client.await("the necklace gone") { if (client.inventory(WORN).getOrNull(WearPos.NECK) == null) Unit else null }
            }
        }
    }

    @Test
    fun `a necklace of faith restores 10 of 40 Prayer when a hit leaves 1`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.commandReply("setlevel prayer 40") { it.contains("set to level 40") }
                client.commandReply("boost prayer 5") { it.contains("is now 5/40") }
                client.wear(FAITH, WearPos.NECK)
                client.hit(8, 2)
                assertEquals(FAITH, client.inventory(WORN).getOrNull(WearPos.NECK)?.id, "2 of 10 isn't below 20%")
                client.hit(1, 1)
                client.saw("the prayer", "[jewellery_effect.faith]")
                client.await("15 Prayer") { client.stat(PRAYER)?.takeIf { it.level == 15 } }
                assertNull(client.inventory(WORN).getOrNull(WearPos.NECK))
            }
        }
    }

    @Test
    fun `a ring of life takes its wearer to Lumbridge when a hit leaves 1, and not before`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                client.commandReply("tele 3235 3235") { it.startsWith("Teleported") }
                client.await("away from Lumbridge") { client.position?.takeIf { it.x == 3235 && it.z == 3235 } }
                client.wear(RING_OF_LIFE, WearPos.RING)
                client.hit(8, 2)
                client.awaitTicks(2)
                assertEquals(3235, client.position?.x, "2 of 10 is above 10%")
                client.hit(1, 1)
                client.saw("the ring", "[jewellery_effect.ring_of_life]")
                client.await("in Lumbridge") { client.position?.takeIf { it.x in 3219..3223 && it.z in 3217..3221 } }
                assertNull(client.inventory(WORN).getOrNull(WearPos.RING))
            }
        }
    }

    @Test
    fun `a ring of life takes a player who chose Krystilia's respawn to Edgeville`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dina").use { client ->
                client.ready()
                client.commandReply("varbit ${EdgevilleRespawn.VARBIT_UNLOCKED} 1") { it.startsWith("Varbit") }
                client.commandReply("varbit ${EdgevilleRespawn.VARBIT_SPAWN} 1") { it.startsWith("Varbit") }
                client.wear(RING_OF_LIFE, WearPos.RING)
                client.hit(9, 1)
                client.saw("the ring", "[jewellery_effect.ring_of_life]")
                client.await("in Edgeville") { client.position?.takeIf { it.x == EdgevilleRespawn.X && it.z == EdgevilleRespawn.Z } }
            }
        }
    }

    @Test
    fun `a hit of 0 sets off no ring of life (round 59, R59A-3)`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.ready()
                client.commandReply("tele 3235 3235") { it.startsWith("Teleported") }
                client.await("away from Lumbridge") { client.position?.takeIf { it.x == 3235 && it.z == 3235 } }
                client.hit(9, 1)
                client.wear(RING_OF_LIFE, WearPos.RING)
                client.hit(0, 1)
                client.awaitTicks(2)
                assertEquals(3235, client.position?.x, "no escape at will")
                assertEquals(RING_OF_LIFE, client.inventory(WORN).getOrNull(WearPos.RING)?.id)
            }
        }
    }

    @Test
    fun `a ring of life's escape stops the script the player was in, as a death does (round 59, R59A-1)`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fred").use { client ->
                client.ready()
                client.wear(RING_OF_LIFE, WearPos.RING)
                client.commandReply("pause") { it.startsWith("Paused") }
                val world = server.world
                val player = world.playerNamed("fred")!!
                var carriedOn = false
                // An obstacle-like script waiting out a delay when poison hits.
                world.scripts.start(player) {
                    delay(5)
                    carriedOn = true
                }
                // A background timer, as wine fermenting has (Codex review on #964).
                var timerFired = false
                world.scripts.setTimer(player, "test_background", 3, osrsps.game.script.TimerType.NORMAL) { timerFired = true }
                assertEquals(true, player.scripts.delayed)
                world.meleeCombat.statusHit(player, 9, osrsps.game.combat.Poison.POISON_HITSPLAT)
                assertFalse(player.scripts.delayed, "the escape dropped it")
                client.commandReply("resume") { it.startsWith("Resumed") }
                client.saw("the ring", "[jewellery_effect.ring_of_life]")
                client.awaitTicks(8)
                assertFalse(carriedOn, "never carries on at Lumbridge")
                assertEquals(true, timerFired, "the timer carried on")
            }
        }
    }

    companion object {
        const val BACKPACK = 93
        const val WORN = 94
        const val PRAYER = 5

        /** The inventory's Wear, iop 2, as an IF_BUTTON op. */
        const val WEAR_BUTTON_OP = 3

        val WEARABLE = listOf(null, "Wear", null, null, "Drop")

        const val PHOENIX = 11090
        const val FAITH = 21157
        const val RING_OF_LIFE = 2570
    }
}
