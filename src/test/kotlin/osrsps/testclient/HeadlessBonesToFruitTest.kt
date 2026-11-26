package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.ObjType
import osrsps.game.World
import osrsps.game.combat.Spells
import osrsps.game.skill.magic.BonesToFruit
import osrsps.game.skill.magic.TeleportObjs
import osrsps.map.CollisionMap

/**
 * Bones to Bananas and Bones to Peaches (Magic utility step U7a) through the headless client, with the spell objs
 * shaped as the 242 cache has them: every listed bone held turns into the fruit, slot for slot, for the spell's XP once;
 * noted bones, a stack and dragon bones stay; holding no bones, or only bones the spell doesn't change, casts nothing.
 * test-runner/examples/magic-bones-to-fruit.json does the same in RuneLite.
 */
class HeadlessBonesToFruitTest {
    private fun spell(id: Int, internal: String, name: String, level: Int, component: Int, nature: Int, elements: Int): ObjType {
        val params = HashMap<Int, Any>()
        params[Spells.PARAM_NAME] = name
        params[Spells.PARAM_LEVEL] = level
        params[Spells.PARAM_COMPONENT] = (Spells.SPELLBOOK shl 16) or component
        params[Spells.PARAM_RUNES[0].first] = NATURE
        params[Spells.PARAM_RUNES[0].second] = nature
        params[Spells.PARAM_RUNES[1].first] = TeleportObjs.WATER
        params[Spells.PARAM_RUNES[1].second] = elements
        params[Spells.PARAM_RUNES[2].first] = TeleportObjs.EARTH
        params[Spells.PARAM_RUNES[2].second] = elements
        return ObjType(id, "null", params = params, internalName = internal)
    }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val bury = listOf("Bury", null, null, null, "Drop")
        val objTypes = TeleportObjs.objTypes(
            ObjType(NATURE, "Nature rune", stackable = true, internalName = "naturerune"),
            spell(BANANAS_SPELL, "15_bones_bananas", "Bones to Bananas", 15, BANANAS_COMPONENT, 1, 2),
            spell(PEACHES_SPELL, "60_bones_peaches", "Bones to Peaches", 60, PEACHES_COMPONENT, 2, 4),
            ObjType(BONES, "Bones", iops = bury, internalName = "bones", certlink = BONES_NOTE),
            ObjType(BONES_NOTE, "Bones", stackable = true, certlink = BONES, certtemplate = 799),
            ObjType(BIG_BONES, "Big bones", iops = bury, internalName = "big_bones"),
            ObjType(DRAGON_BONES, "Dragon bones", iops = bury, internalName = "dragon_bones"),
            ObjType(STACKED_BONES, "Bones", iops = bury, stackable = true),
            ObjType(PEACH, "Peach", iops = listOf("Eat", null, null, null, "Drop"), internalName = "peach"),
        )
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = osrsps.game.GameRandom(7), objTypes = objTypes,
            spells = Spells.load(objTypes, osrsps.game.SeqNames.NONE, osrsps.game.SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int): Int = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of item $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.ready(magic: Int) {
        await("the login rebuild") { position }
        commandReply("keys on") { it == "Dialogue shows keys" }
        commandReply("setlevel magic $magic") { it.contains("set to level $magic") }
        await("the level") { stat(MAGIC)?.takeIf { it.level == magic } }
    }

    private fun HeadlessClient.xp(): Int = stat(MAGIC)?.xp ?: 0

    private fun HeadlessClient.saw(description: String, text: String) =
        await(description) { if (messages().any { it.text == text }) Unit else null }

    @Test
    fun `Bones to Bananas turns every listed bone into a banana for 25 XP, leaving noted and dragon bones`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready(15)
                client.give(BONES, 3)
                client.give(BIG_BONES, 2)
                client.give(DRAGON_BONES)
                client.give(BONES_NOTE, 5)
                client.give(STACKED_BONES, 4)
                client.give(NATURE, 2)
                client.give(TeleportObjs.WATER, 4)
                client.give(TeleportObjs.EARTH, 4)
                val before = client.xp()
                client.ifButton(Spells.SPELLBOOK, BANANAS_COMPONENT, BonesToFruit.CAST)
                client.await("the bananas") { if (client.count(TeleportObjs.BANANA) == 5) Unit else null }
                assertEquals(0, client.count(BONES))
                assertEquals(0, client.count(BIG_BONES))
                assertEquals(1, client.count(DRAGON_BONES), "not on the list")
                assertEquals(5, client.count(BONES_NOTE), "noted")
                assertEquals(4, client.count(STACKED_BONES), "a stack isn't changed whole for one fruit")
                assertEquals(1, client.count(NATURE))
                assertEquals(2, client.count(TeleportObjs.WATER))
                client.await("25 XP") { if (client.xp() == before + 25) Unit else null }
            }
        }
    }

    @Test
    fun `nothing to change casts nothing, and Bones to Peaches needs level 60`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready(59)
                client.give(NATURE, 4)
                client.give(TeleportObjs.WATER, 8)
                client.give(TeleportObjs.EARTH, 8)
                client.ifButton(Spells.SPELLBOOK, BANANAS_COMPONENT, BonesToFruit.CAST)
                client.saw("no bones", "[bones_to_fruit.no_bones]")
                client.give(DRAGON_BONES)
                client.ifButton(Spells.SPELLBOOK, BANANAS_COMPONENT, BonesToFruit.CAST)
                client.saw("unsuitable", "[bones_to_fruit.unsuitable]")
                client.give(BONES, 2)
                client.ifButton(Spells.SPELLBOOK, PEACHES_COMPONENT, BonesToFruit.CAST)
                client.saw("too low", "[combat.magic_level]")
                assertEquals(2, client.count(BONES))
                client.commandReply("setlevel magic 60") { it.contains("set to level 60") }
                client.await("level 60") { client.stat(MAGIC)?.takeIf { it.level == 60 } }
                val before = client.xp()
                client.ifButton(Spells.SPELLBOOK, PEACHES_COMPONENT, BonesToFruit.CAST)
                client.await("the peaches") { if (client.count(PEACH) == 2) Unit else null }
                assertEquals(2, client.count(NATURE))
                assertEquals(1, client.count(DRAGON_BONES))
                client.await("35.5 XP") { if (client.xp() == before + 35) Unit else null }
            }
        }
    }

    companion object {
        const val BACKPACK = 93
        const val MAGIC = 6

        const val NATURE = 561
        const val BONES = 526
        const val BONES_NOTE = 527
        const val BIG_BONES = 532
        /** One of the listed bones, made stackable here to check that a stack is left alone. */
        const val STACKED_BONES = 30973
        const val DRAGON_BONES = 536
        const val PEACH = 6883

        const val BANANAS_SPELL = 3280
        const val PEACHES_SPELL = 6884

        /** The magic tab components, 218:20 and 218:53 in the 242 cache. */
        const val BANANAS_COMPONENT = 20
        const val PEACHES_COMPONENT = 53
    }
}
