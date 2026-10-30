package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.AltarLines
import osrsps.game.content.VarrockChurches
import osrsps.game.content.VarrockInns
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Varrock step 7 through the headless client: the church's and the chapel's altars recharge prayer; Father Lawrence,
 * Elsie (and her story for a cup of tea) and the Mage of Zamorak talk every way; each inn's bartender sells a beer for 2
 * coins and the Blue Moon's cook a cabbage for 1, not to a player short of coins; and the Jolly Boar's cook talks every
 * way. The rl-client script test-runner/examples/varrock-churches-inns.json does the same in the real city.
 */
class HeadlessVarrockChurchesInnsTest {
    private val coins = ObjType(COINS, "Coins", stackable = true)
    private val beer = ObjType(VarrockInns.BEER, "Beer", cost = 2, tradeable = true)
    private val cabbage = ObjType(VarrockInns.CABBAGE, "Cabbage", cost = 1, tradeable = true)
    private val tea = ObjType(VarrockChurches.CUP_OF_TEA, "Cup of tea", cost = 10, tradeable = true)
    private val pot = ObjType(POT, "Pot", cost = 1, tradeable = true)

    private val talk = listOf("Talk-to", null, null, null, null)
    private fun npc(id: Int, name: String, internal: String) = NpcType(id, name, ops = talk, internalName = internal)

    private val lawrence = npc(VarrockChurches.FATHER_LAWRENCE, "Father Lawrence", "father_lawrence")
    private val elsie = npc(VarrockChurches.ELSIE, "Elsie", "varrock_granny_2")
    private val mage = npc(VarrockChurches.MAGE_OF_ZAMORAK, "Mage of Zamorak", "rcu_zammy_mage1_edgeb")
    private val blueMoon = npc(VarrockInns.BLUE_MOON_BARTENDER, "Bartender", "bluemoon_bartender")
    private val donkey = npc(VarrockInns.DANCING_DONKEY_BARTENDER, "Bartender", "donkey_bartender")
    private val jollyBoar = npc(VarrockInns.JOLLY_BOAR_BARTENDER, "Bartender", "jollyboar_bartender")
    private val blueMoonCook = npc(VarrockInns.BLUE_MOON_COOK, "Cook", "fai_varrock_bluemoon_chef")
    private val jollyBoarCook = npc(VarrockInns.JOLLY_BOAR_COOK, "Cook", "fai_varrock_jollyboar_chef")
    private val all = listOf(lawrence, elsie, mage, blueMoon, donkey, jollyBoar, blueMoonCook, jollyBoarCook)

    private fun altar(id: Int) = LocType(id, "Altar", ops = listOf("Pray-at", null, null, null, null), collision = LocCollisionType(width = 2, length = 1))
    private val altars = listOf(VarrockChurches.CHURCH_ALTAR, VarrockChurches.CHAOS_ALTAR)
    private val locs = altars.mapIndexed { i, id -> ZoneLoc(id, LocShape.CENTREPIECE_STRAIGHT, 0, 3214 + i * 4, 3226, 0) }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(coins, beer, cabbage, tea, pot),
            locTypes = LocTypes(altars.associateWith(::altar)), staticLocs = StaticLocs().apply { locs.forEach(::add) },
            npcTypes = NpcTypes.of(*all.toTypedArray()),
            npcSpawns = all.mapIndexed { i, type -> NpcSpawn(type, 3212 + i * 2, 3218, wander = 0, source = "test") },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.talkTo(id: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, VarrockChurches.TALK_TO)
    }

    private fun HeadlessClient.chat(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    private fun HeadlessClient.pick(option: Int, vararg keys: String) {
        val texts = keys.map { "[$it]" }
        awaitDialogue("the menu $texts") { it is DialogueView.Menu && it.options == texts }
        chooseOption(option)
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    /** Reads on from each of [steps], a key under [prefix], or "n:key1,key2" to pick option n. */
    private fun HeadlessClient.steps(prefix: String, vararg steps: String) {
        for (step in steps) {
            val menu = step.substringBefore(':', "")
            if (menu.isEmpty()) chat("$prefix.$step") else pick(menu.toInt(), *step.substringAfter(':').split(',').map { "$prefix.$it" }.toTypedArray())
        }
    }

    private fun HeadlessClient.walk(id: Int, prefix: String, vararg steps: String) {
        talkTo(id)
        steps(prefix, *steps)
        over()
    }

    private fun HeadlessClient.useOn(id: Int, obj: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        val slot = inventory(Inventory.INV).indexOfFirst { it?.id == obj }
        npcTarget(npc.index, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, obj)
    }

    @Test
    fun `the church's and the chapel's altars recharge prayer`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel prayer 43") { it.contains("43") }
                for (loc in locs) {
                    client.commandReply("pray 3") { it.startsWith("Prayer points set") }
                    client.await("3 points") { client.stat(PRAYER)?.takeIf { it.level == 3 } }
                    val text = DialogueText.NONE[AltarLines.RECHARGE]
                    val seen = client.messages().count { it.text == text }
                    client.locOp(loc.id, loc.x, loc.z, VarrockChurches.PRAY_AT)
                    client.await("${loc.id} recharging") { if (client.messages().count { it.text == text } > seen) Unit else null }
                    assertEquals(43, client.await("43 points") { client.stat(PRAYER)?.takeIf { it.level == 43 } }.level)
                }
            }
        }
    }

    @Test
    fun `Father Lawrence, Elsie and the Mage of Zamorak talk every way`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                // Father Lawrence picks one of two endings at random; talk until both have come up.
                val endings = mutableSetOf<String>()
                while (endings.size < 2) {
                    client.talkTo(lawrence.id)
                    client.steps("father_lawrence", "whiskey", "gutters")
                    val next = client.awaitDialogue("an ending") { it is DialogueView.Chat } as DialogueView.Chat
                    endings += next.text
                    client.continueDialogue()
                    if (next.text == "[father_lawrence.forgive]") client.chat("father_lawrence.drink")
                    assertEquals(true, next.text in listOf("[father_lawrence.morning]", "[father_lawrence.forgive]"))
                    client.over()
                }

                val menu = "making,story,rich"
                client.walk(elsie.id, "elsie", "greeting", "1:$menu", "making", "stole", "3:$menu", "rich", "trade", "ore_food", "thanks", "2:$menu", "story", "for_tea", "1:think")
                client.command("item ${VarrockChurches.CUP_OF_TEA} 1")
                client.await("a cup of tea") { if (client.held(VarrockChurches.CUP_OF_TEA) == 1) Unit else null }
                client.useOn(elsie.id, VarrockChurches.CUP_OF_TEA)
                client.steps("elsie", "tea", "tea_story", "2:have_it,keep_it", "keep_it", "another_time", "2:$menu", "story", "for_tea", "1:think")
                client.over()
                assertEquals(1, client.held(VarrockChurches.CUP_OF_TEA))
                client.useOn(elsie.id, VarrockChurches.CUP_OF_TEA)
                client.steps("elsie", "tea", "tea_story", "1:have_it,keep_it", "have_it", *(1..9).map { "tale_$it" }.toTypedArray(), "leave")
                client.over()
                assertEquals(0, client.held(VarrockChurches.CUP_OF_TEA))
                client.command("item $POT 1")
                client.await("a pot") { if (client.held(POT) == 1) Unit else null }
                client.useOn(elsie.id, POT)
                client.chat("elsie.not_that")
                client.over()
                assertEquals(1, client.held(POT))

                val asks = "incantation,abyss,group,looking"
                client.walk(
                    mage.id, "mage_of_zamorak", "greeting", "1:$asks", "incantation", "rift", "flesh", "right",
                    "2:$asks", "abyss", *(1..13).map { "abyss_$it" }.toTypedArray(),
                    "3:$asks", "group", *(1..12).map { "group_$it" }.toTypedArray(),
                    "4:$asks", "looking", "very_well",
                )
            }
        }
    }

    @Test
    fun `each bartender sells a beer for 2 coins, and the Blue Moon's cook a cabbage for 1`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                // Short of coins: nothing changes hands.
                client.command("item $COINS 1")
                client.await("1 coin") { if (client.held(COINS) == 1) Unit else null }
                client.walk(blueMoon.id, "bluemoon_bartender", "greeting", "1:ale,fortune,equipment", "ale", "two_coins", "no_money")
                client.walk(jollyBoar.id, "jollyboar_bartender", "greeting", "1:beer,hints,gossip", "beer", "two_coins", "no_money")
                client.walk(donkey.id, "donkey_bartender", "hello", "beer", "1:yes,no,how_much", "yes", "two_coins")
                assertEquals(0, client.held(VarrockInns.BEER))
                client.command("item $COINS 6")
                client.await("7 coins") { if (client.held(COINS) == 7) Unit else null }
                client.walk(blueMoon.id, "bluemoon_bartender", "greeting", "1:ale,fortune,equipment", "ale", "two_coins")
                client.walk(jollyBoar.id, "jollyboar_bartender", "greeting", "1:beer,hints,gossip", "beer", "two_coins")
                client.walk(donkey.id, "donkey_bartender", "hello", "beer", "3:yes,no,how_much", "how_much", "price", "1:yes,no,how_much", "yes", "two_coins", "cheers", "cheers_back")
                assertEquals(3, client.await("three beers") { client.held(VarrockInns.BEER).takeIf { it == 3 } })
                assertEquals(1, client.held(COINS))

                val cook = arrayOf("greeting", "1:sell,free,horrible", "sell", "cabbage")
                client.walk(blueMoonCook.id, "bluemoon_cook", *cook, "2:buy,dont_like", "dont_like", "junk")
                client.walk(blueMoonCook.id, "bluemoon_cook", *cook, "1:buy,dont_like", "buy", "eat_it")
                assertEquals(1, client.await("a cabbage") { client.held(VarrockInns.CABBAGE).takeIf { it == 1 } })
                assertEquals(0, client.held(COINS))
                client.walk(blueMoonCook.id, "bluemoon_cook", *cook, "no_money", "waste")
                client.command("item $COINS 1")
                client.command("item $POT 23")
                client.await("a full backpack") { if (client.held(POT) == 23 && client.held(COINS) == 1) Unit else null }
                client.walk(blueMoonCook.id, "bluemoon_cook", *cook, "no_space", "waste")
                assertEquals(1, client.held(COINS))
            }
        }
    }

    @Test
    fun `the bartenders' and cooks' other talk`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                val bar = "ale,fortune,equipment"
                val fortune = arrayOf("greeting", "2:$bar", "fortune", "too_easy")
                client.walk(blueMoon.id, "bluemoon_bartender", *fortune, "1:ah_well,game,clue", "ah_well")
                client.walk(blueMoon.id, "bluemoon_bartender", *fortune, "2:ah_well,game,clue", "game", "online", "what_online", "magic_boxes", "mad")
                client.walk(blueMoon.id, "bluemoon_bartender", *fortune, "3:ah_well,game,clue", "clue", "ask_jolly_boar")
                client.walk(blueMoon.id, "bluemoon_bartender", "greeting", "3:$bar", "equipment", "sword_shop")
                client.walk(donkey.id, "donkey_bartender", "hello", "beer", "2:yes,no,how_much", "no", "change_mind")
                client.walk(jollyBoar.id, "jollyboar_bartender", "greeting", "2:beer,hints,gossip", "hints", "think", "sewers", "where", "east_of_palace")
                client.walk(jollyBoar.id, "jollyboar_bartender", "greeting", "3:beer,hints,gossip", "gossip", "mad", "old_news")
                client.walk(blueMoonCook.id, "bluemoon_cook", "greeting", "2:sell,free,horrible", "free", "free_money", "why_pay", "why_free", "forget_it")
                client.walk(blueMoonCook.id, "bluemoon_cook", "greeting", "3:sell,free,horrible", "horrible", "cleaning", "disgust", "leave_me")
                val ask = "cook,why,joke"
                client.walk(jollyBoarCook.id, "jollyboar_cook", "greeting", "1:$ask", "cook", *(1..7).map { "cook_$it" }.toTypedArray())
                client.walk(jollyBoarCook.id, "jollyboar_cook", "greeting", "2:$ask", "why", *(1..8).map { "why_$it" }.toTypedArray())
                client.walk(jollyBoarCook.id, "jollyboar_cook", "greeting", "3:$ask", "joke", *(1..5).map { "joke_$it" }.toTypedArray())
            }
        }
    }

    private companion object {
        const val COINS = 995
        const val POT = 1931
        const val PRAYER = 5
    }
}
