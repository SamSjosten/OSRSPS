package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.AllSettings
import osrsps.game.GameFrame
import osrsps.game.InventoryTab
import osrsps.game.ItemPrices
import osrsps.game.ObjType
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.Spells
import osrsps.game.dialogue.DialogueText
import osrsps.game.skill.magic.Alchemy
import osrsps.game.skill.magic.AlchemyLines
import osrsps.game.skill.magic.TeleportObjs
import osrsps.map.CollisionMap

/**
 * Low and High Level Alchemy (Magic utility step U1) through the headless client, with the spell objs shaped as the
 * 242 cache has them: a cast on an inventory item turns one of it into 40% or 60% of its value in coins for the runes
 * and XP, at most once every 3 or 5 ticks; a note is alchemised as its item; coins, items the cache marks and too low a
 * level or too few runes cast nothing; a valuable or untradeable item asks first; the Fountain of Rune pays the runes
 * and gives no XP. The rl-client script test-runner/examples/magic-alchemy.json does the same in the real client.
 */
class HeadlessAlchemyTest {
    private fun spell(id: Int, internal: String, name: String, level: Int, component: Int, fire: Int): ObjType {
        val params = HashMap<Int, Any>()
        params[Spells.PARAM_NAME] = name
        params[Spells.PARAM_LEVEL] = level
        params[Spells.PARAM_COMPONENT] = (Spells.SPELLBOOK shl 16) or component
        val (rune1, count1) = Spells.PARAM_RUNES[0]
        val (rune2, count2) = Spells.PARAM_RUNES[1]
        params[rune1] = TeleportObjs.FIRE
        params[count1] = fire
        params[rune2] = NATURE
        params[count2] = 1
        return ObjType(id, "null", params = params, internalName = internal)
    }

    private fun server(dir: Path, cycleMillis: Long = 40) = TestServer(dir, cycleMillis = cycleMillis) { crcs, millis ->
        val objTypes = TeleportObjs.objTypes(
            ObjType(NATURE, "Nature rune", stackable = true, tradeable = true, internalName = "naturerune"),
            ObjType(Alchemy.COINS, "Coins", stackable = true, internalName = "coins"),
            spell(LOW_SPELL, "21_low_alchemy", "Low Level Alchemy", 21, LOW, 3),
            spell(HIGH_SPELL, "55_high_alchemy", "High Level Alchemy", 55, HIGH, 5),
            ObjType(SCIMITAR, "Rune scimitar", cost = 25_600, tradeable = true, certlink = SCIMITAR_NOTE, internalName = "rune_scimitar"),
            ObjType(SCIMITAR_NOTE, "Rune scimitar", cost = 25_600, stackable = true, tradeable = true, certlink = SCIMITAR, certtemplate = 799, internalName = "cert_rune_scimitar"),
            ObjType(BUCKET, "Bucket", cost = 2, tradeable = true, internalName = "bucket_empty"),
            ObjType(TROPHY, "Trophy", cost = 5_000, internalName = "trophy"),
            ObjType(CAPE, "Cape", cost = 99_000, tradeable = false, params = mapOf(ObjType.PARAM_NO_ALCHEMY to 1), internalName = "cape"),
        )
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(3375 shr 6, 3897 shr 6) }, random = osrsps.game.GameRandom(7), objTypes = objTypes,
            spells = Spells.load(objTypes, osrsps.game.SeqNames.NONE, osrsps.game.SpotanimNames.NONE), cycleMillis = millis,
            itemPrices = ItemPrices(mapOf(SCIMITAR to ItemPrices.Entry("Rune scimitar", 15_000L, 70), BUCKET to ItemPrices.Entry("Bucket", 5L, 1000))),
            varTypes = VarTypes(6_000, AllSettings.VARBITS + mapOf(Alchemy.VARBIT_WARNING_VALUE to VarbitType(5_100, 0, 29), Alchemy.VARBIT_UNTRADEABLE_WARNING to VarbitType(5_101, 0, 0))),
        )
    }

    private fun HeadlessClient.count(id: Int): Int = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of item $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.cast(component: Int, item: Int) =
        ifButtonT(Spells.SPELLBOOK, component, -1, -1, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(item), item)

    private fun HeadlessClient.magicXp(): Int = stat(MAGIC)?.xp ?: 0

    private fun HeadlessClient.ready(level: Int) {
        await("the login rebuild") { position }
        commandReply("keys on") { it == "Dialogue shows keys" }
        commandReply("setlevel magic $level") { it.contains("set to level $level") }
        await("Magic $level") { stat(MAGIC)?.takeIf { it.level == level } }
    }

    @Test
    fun `High Level Alchemy turns one of a stack of notes into 60% of the value, every 5 ticks, for 65 XP and the runes`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready(55)
                client.commandReply("varbit ${Alchemy.VARBIT_WARNING_VALUE} 0") { true }
                val start = client.magicXp()
                client.give(SCIMITAR_NOTE, 3)
                client.give(NATURE, 3)
                client.give(TeleportObjs.FIRE, 15)
                // The clock stopped, so each cast lands on a tick we step to, however slow the machine.
                client.commandReply("pause") { it.startsWith("Paused") }
                fun step(ticks: Int) = client.stepTicks(ticks)
                client.cast(HIGH, SCIMITAR_NOTE)
                step(1)
                client.await("the coins") { if (client.count(Alchemy.COINS) == 15_360) Unit else null }
                assertEquals(2, client.count(SCIMITAR_NOTE))
                assertEquals(2, client.count(NATURE))
                assertEquals(10, client.count(TeleportObjs.FIRE))
                assertEquals(65, client.await("65 XP") { (client.magicXp() - start).takeIf { it >= 65 } })
                assertTrue(client.clientScripts().any { it.id == GameFrame.SIDEBUTTON_SWITCH_SCRIPT && it.args == listOf<Any>(GameFrame.SIDE_TAB_MAGIC) }, "the magic tab comes back")

                // Cast again on the next tick: refused, and not kept for later either.
                client.cast(HIGH, SCIMITAR_NOTE)
                step(1)
                step(Alchemy.load(server.world.objTypes).first { it.high }.spell.ticks)
                assertEquals(2, client.count(SCIMITAR_NOTE), "too soon, and dropped")
                assertEquals(15_360, client.count(Alchemy.COINS))
                client.cast(HIGH, SCIMITAR_NOTE)
                step(1)
                client.await("the second coins") { if (client.count(Alchemy.COINS) == 30_720) Unit else null }
                assertEquals(1, client.count(SCIMITAR_NOTE))
                client.commandReply("resume") { it.isNotEmpty() }
            }
        }
    }

    @Test
    fun `Low Level Alchemy gives 40%, and an item worth too little goes for no coins`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready(21)
                client.commandReply("varbit ${Alchemy.VARBIT_WARNING_VALUE} 0") { true }
                client.give(SCIMITAR)
                client.give(BUCKET)
                client.give(NATURE, 2)
                client.give(TeleportObjs.FIRE, 6)
                client.cast(LOW, SCIMITAR)
                client.await("the coins") { if (client.count(Alchemy.COINS) == 10_240) Unit else null }
                client.awaitTicks(3)
                client.cast(LOW, BUCKET)
                // A bucket's value is 2: 40% is 0 coins, and it goes all the same.
                client.await("the bucket gone") { if (client.count(BUCKET) == 0) Unit else null }
                assertEquals(10_240, client.count(Alchemy.COINS))
                assertEquals(0, client.count(NATURE))
                assertEquals(0, client.count(TeleportObjs.FIRE))
            }
        }
    }

    @Test
    fun `coins, items that can't be alchemised, too low a level and too few runes cast nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready(54)
                client.give(SCIMITAR)
                client.give(NATURE)
                client.give(TeleportObjs.FIRE, 4)
                client.cast(HIGH, SCIMITAR)
                client.awaitMessage("the level refusal") { it == "[combat.magic_level]" }

                client.commandReply("setlevel magic 55") { it.contains("set to level 55") }
                client.await("Magic 55") { client.stat(MAGIC)?.takeIf { it.level == 55 } }
                client.give(Alchemy.COINS, 10)
                client.cast(HIGH, Alchemy.COINS)
                client.awaitMessage("the coins refusal") { it == "[alchemy.coins]" }
                client.give(CAPE)
                client.cast(HIGH, CAPE)
                client.awaitMessage("the can't refusal") { it == "[alchemy.cant]" }
                client.cast(HIGH, SCIMITAR)
                client.awaitMessage("the runes refusal") { it.startsWith("[combat.not_enough_runes") }
                assertEquals(1, client.count(SCIMITAR))
                assertEquals(1, client.count(CAPE))
                assertEquals(10, client.count(Alchemy.COINS))
                assertEquals(1, client.count(NATURE))
                assertEquals(4, client.count(TeleportObjs.FIRE))

                // The only nature rune can't pay for its own alchemy.
                client.give(TeleportObjs.FIRE, 1)
                client.cast(HIGH, NATURE)
                client.awaitMessage("the nature rune's refusal") { it.startsWith("[combat.not_enough_runes") }
                assertEquals(1, client.count(NATURE))
                assertEquals(5, client.count(TeleportObjs.FIRE))
                assertEquals(10, client.count(Alchemy.COINS))
            }
        }
    }

    @Test
    fun `a valuable or untradeable item asks first, No keeps it and Yes alchemises it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready(55)
                // A new account's warnings: untradeables, and 1,000 coins or more at the Grand Exchange.
                client.give(SCIMITAR)
                client.give(TROPHY)
                client.give(NATURE, 3)
                client.give(TeleportObjs.FIRE, 15)
                client.cast(HIGH, SCIMITAR)
                val menu = client.awaitDialogue("the warning") { it is DialogueView.Menu }
                val words = DialogueText.NONE
                assertEquals(
                    DialogueView.Menu(words[AlchemyLines.WARNING_TITLE, "spell" to "High Level Alchemy", "item" to "Rune scimitar"], listOf(words[AlchemyLines.WARNING_YES], words[AlchemyLines.WARNING_NO])),
                    menu,
                )
                client.chooseOption(2)
                client.awaitTicks(2)
                assertEquals(1, client.count(SCIMITAR), "No kept it")
                assertEquals(3, client.count(NATURE))

                client.cast(HIGH, SCIMITAR)
                client.awaitDialogue("the warning again") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.await("the coins") { if (client.count(Alchemy.COINS) == 15_360) Unit else null }

                client.awaitTicks(5)
                client.cast(HIGH, TROPHY)
                client.awaitDialogue("the untradeable warning") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.await("the trophy's coins") { if (client.count(Alchemy.COINS) == 15_360 + 3_000) Unit else null }
            }
        }
    }

    @Test
    fun `beside the Fountain of Rune alchemy needs no runes and gives no XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fen").use { client ->
                client.ready(55)
                val start = client.magicXp()
                client.give(BUCKET)
                client.command("tele 3375 3897 0")
                client.await("at the fountain") { client.position?.takeIf { it.x == 3375 && it.z == 3897 } }
                client.cast(HIGH, BUCKET)
                client.await("1 coin") { if (client.count(Alchemy.COINS) == 1) Unit else null }
                client.awaitTicks(2)
                assertEquals(start, client.magicXp(), "no XP for a free cast")
            }
        }
    }

    companion object {
        const val BACKPACK = 93
        const val MAGIC = 6

        const val NATURE = 561
        const val LOW_SPELL = 3284
        const val HIGH_SPELL = 3303
        const val LOW = 24
        const val HIGH = 47
        const val SCIMITAR = 1333
        const val SCIMITAR_NOTE = 1334
        const val BUCKET = 1925
        const val TROPHY = 4000
        const val CAPE = 4001
    }
}
