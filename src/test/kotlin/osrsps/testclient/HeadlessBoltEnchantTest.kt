package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.World
import osrsps.game.combat.Spells
import osrsps.game.dialogue.Chatbox
import osrsps.game.skill.magic.BoltEnchant
import osrsps.game.skill.magic.TeleportObjs
import osrsps.map.CollisionMap

/**
 * Enchant Crossbow Bolt (Artisan 2 step A4b) through the headless client, with the real bolt-enchant file and the
 * spell objs shaped as the 242 cache has them: the magic tab's button opens the Make-X box over the enchantments whose
 * bolts are held, and each cast takes its runes and up to 10 bolts, [BoltEnchant.INTERVAL] ticks apart, for the
 * spell's XP; a staff of air stands in for the air runes; too low a level, too few runes or no bolts casts nothing.
 * The rl-client script test-runner/examples/magic-bolt-enchant.json does the same in the real client.
 */
class HeadlessBoltEnchantTest {
    private fun spell(id: Int, internal: String, vararg runes: Pair<Int, Int>): ObjType {
        val params = HashMap<Int, Any>()
        runes.forEachIndexed { i, (rune, count) ->
            val (runeParam, countParam) = Spells.PARAM_RUNES[i]
            params[runeParam] = rune
            params[countParam] = count
        }
        return ObjType(id, "null", params = params, internalName = internal)
    }

    private fun bolts(id: Int, internal: String, name: String) =
        ObjType(id, name, stackable = true, iops = listOf(null, "Wield", null, null, "Drop"), internalName = internal)

    private fun server(dir: Path, cycleMillis: Long = 40) = TestServer(dir, cycleMillis = cycleMillis) { crcs, millis ->
        val objTypes = TeleportObjs.objTypes(
            ObjType(COSMIC, "Cosmic rune", stackable = true, internalName = "cosmicrune"),
            ObjType(BLOOD, "Blood rune", stackable = true, internalName = "bloodrune"),
            spell(OPAL_SPELL, "04_enchant_bolt_opal", TeleportObjs.AIR to 2, COSMIC to 1),
            spell(RUBY_SPELL, "49_enchant_bolt_ruby", BLOOD to 1, TeleportObjs.FIRE to 5, COSMIC to 1),
            bolts(OPAL, "opal_bolt", "Opal bolts"),
            bolts(OPAL_E, "xbows_crossbow_bolts_bronze_tipped_opal_enchanted", "Opal bolts (e)"),
            bolts(OPAL_DRAGON, "dragon_bolts_unenchanted_opal", "Opal dragon bolts"),
            bolts(OPAL_DRAGON_E, "dragon_bolts_enchanted_opal", "Opal dragon bolts (e)"),
            bolts(RUBY, "xbows_crossbow_bolts_adamantite_tipped_ruby", "Ruby bolts"),
            bolts(RUBY_E, "xbows_crossbow_bolts_adamantite_tipped_ruby_enchanted", "Ruby bolts (e)"),
        )
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(7), objTypes = objTypes,
            spells = Spells.load(objTypes, osrsps.game.SeqNames.NONE, osrsps.game.SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.cast() = ifButton(Spells.SPELLBOOK, BoltEnchant.COMPONENT, BoltEnchant.CAST)

    private fun HeadlessClient.count(id: Int): Int = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.give(id: Int, count: Int) {
        val before = count(id)
        command("item $id $count")
        await("$count of item $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.magicXp(): Int = stat(MAGIC)?.xp ?: 0

    private fun HeadlessClient.ready(level: Int) {
        await("the login rebuild") { position }
        commandReply("keys on") { it == "Dialogue shows keys" }
        commandReply("setlevel magic $level") { it.contains("set to level $level") }
        await("Magic $level") { stat(MAGIC)?.takeIf { it.level == level } }
    }

    /** The Make-X box, once more than [after] have opened. */
    private fun HeadlessClient.box(after: Int = 0) = await("the Make-X box") {
        clientScripts().filter { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }.takeIf { it.size > after }?.last()
    }

    private fun HeadlessClient.boxes() = clientScripts().count { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }

    @Test
    fun `the button offers the held bolts and enchants opal bolts 10 a cast, 3 ticks apart, for 9 XP a cast`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready(4)
                val start = client.magicXp()
                client.give(OPAL, 25)
                client.give(OPAL_DRAGON, 5)
                client.give(COSMIC, 4)
                client.give(TeleportObjs.AIR, 8)
                client.cast()
                val setup = client.box()
                assertTrue(OPAL_E in setup.args, "the box shows the bolt it makes: ${setup.args}")
                assertTrue(RUBY_E !in setup.args, "no ruby bolts held")
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 3)
                client.await("25 enchanted") { if (client.count(OPAL_E) == 25) Unit else null }
                assertEquals(0, client.count(OPAL))
                assertEquals(5, client.count(OPAL_DRAGON), "the ordinary bolts first")
                assertEquals(1, client.count(COSMIC))
                assertEquals(2, client.count(TeleportObjs.AIR))
                assertEquals(27, client.await("27 XP") { (client.magicXp() - start).takeIf { it >= 27 } })
                val casts = client.animationTicks().takeLast(3)
                assertEquals(listOf(BoltEnchant.INTERVAL, BoltEnchant.INTERVAL), casts.zipWithNext { a, b -> b - a })
            }
        }
    }

    @Test
    fun `a staff of air stands in for the air runes, and the dragon bolts follow once the ordinary ones are gone`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready(4)
                client.give(TeleportObjs.STAFF_OF_AIR, 1)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, WIELD_BUTTON_OP, client.slotOf(TeleportObjs.STAFF_OF_AIR), TeleportObjs.STAFF_OF_AIR)
                client.await("the staff wielded") { if (client.count(TeleportObjs.STAFF_OF_AIR) == 0) Unit else null }
                client.give(OPAL, 10)
                client.give(OPAL_DRAGON, 4)
                client.give(COSMIC, 2)
                client.cast()
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                client.await("the dragon bolts enchanted") { if (client.count(OPAL_DRAGON_E) == 4) Unit else null }
                assertEquals(10, client.count(OPAL_E))
                assertEquals(0, client.count(COSMIC))
            }
        }
    }

    @Test
    fun `two casts sent in one tick enchant once`(@TempDir dir: Path) {
        // Long ticks, so the four packets all land in one.
        server(dir, cycleMillis = 1500).use { server ->
            server.login("dave").use { client ->
                client.ready(4)
                val start = client.magicXp()
                client.give(OPAL, 30)
                client.give(COSMIC, 5)
                client.give(TeleportObjs.AIR, 10)
                client.cast()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.cast()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.await("10 enchanted") { if (client.count(OPAL_E) >= 10) Unit else null }
                client.await("two more ticks") { client.position }
                Thread.sleep(3_500)
                assertEquals(10, client.count(OPAL_E), "one cast, not two")
                assertEquals(4, client.count(COSMIC))
                assertEquals(9, client.magicXp() - start)
            }
        }
    }

    @Test
    fun `too low a level, too few runes or no bolts enchants nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready(48)
                client.cast()
                client.awaitMessage("no bolts") { it == "[bolt_enchant.no_bolts]" }
                assertEquals(0, client.boxes())

                client.give(RUBY, 10)
                client.give(COSMIC, 1)
                client.give(BLOOD, 1)
                client.give(TeleportObjs.FIRE, 5)
                client.cast()
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.awaitMessage("the level refusal") { it == "[combat.magic_level]" }
                assertEquals(10, client.count(RUBY))
                assertEquals(1, client.count(BLOOD))

                client.commandReply("setlevel magic 49") { it.contains("set to level 49") }
                client.await("Magic 49") { client.stat(MAGIC)?.takeIf { it.level == 49 } }
                client.command("empty")
                client.await("an empty backpack") { if (client.inventory(BACKPACK).all { it == null }) Unit else null }
                client.give(RUBY, 10)
                client.give(COSMIC, 1)
                client.give(TeleportObjs.FIRE, 5)
                client.cast()
                client.box(after = 1)
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.awaitMessage("the runes refusal") { it.startsWith("[combat.not_enough_runes") }
                assertEquals(10, client.count(RUBY))
                assertEquals(0, client.count(RUBY_E))
            }
        }
    }

    companion object {
        const val BACKPACK = 93
        const val MAGIC = 6
        const val WIELD_BUTTON_OP = 3

        const val COSMIC = 564
        const val BLOOD = 565
        const val OPAL_SPELL = 24350
        const val RUBY_SPELL = 24356
        const val OPAL = 879
        const val OPAL_E = 9236
        const val OPAL_DRAGON = 21955
        const val OPAL_DRAGON_E = 21932
        const val RUBY = 9339
        const val RUBY_E = 9242
    }
}
