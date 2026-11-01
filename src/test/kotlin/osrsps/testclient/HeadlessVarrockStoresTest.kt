package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.VarrockStores
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopStock
import osrsps.game.shop.ShopType
import osrsps.game.shop.ShopTypes
import osrsps.map.CollisionMap

/**
 * Varrock step 5 through the headless client: every way through the General Store keepers', Aubury's, Thessalia's,
 * Asyff's and the Tea Seller's talk, each ending in their shop or with the talk over. Aubury and Thessalia stand as the
 * types every account sees (aubury_3op, thessalia_normal). The rl-client script test-runner/examples/varrock-stores.json
 * trades at every shop in Varrock itself.
 */
class HeadlessVarrockStoresTest {
    private val pot = ObjType(POT, "Pot", cost = 1, tradeable = true)
    private val objs = ObjTypes.of(pot)

    private val trade = listOf("Talk-to", null, "Trade", null, null)
    private val keeper = NpcType(VarrockStores.SHOP_KEEPER, "Shop keeper", ops = trade, internalName = "generalshopkeeper2")
    private val assistant = NpcType(VarrockStores.SHOP_ASSISTANT, "Shop assistant", ops = trade, internalName = "generalassistant2")
    private val aubury = NpcType(AUBURY, "Aubury", ops = listOf("Talk-to", null, "Trade", "Teleport", null), internalName = "aubury_3op")
    private val thessalia = NpcType(VarrockStores.THESSALIA, "Thessalia", ops = listOf("Talk-to", null, "Trade", "Makeover", null), internalName = "thessalia_normal")
    private val asyff = NpcType(VarrockStores.ASYFF, "Asyff", ops = listOf("Talk-to", null, "Trade", "Fur Clothing", null), internalName = "tailorp")
    private val teaSeller = NpcType(VarrockStores.TEA_SELLER, "Tea Seller", ops = trade, internalName = "tea_seller")
    private val all = listOf(keeper, assistant, aubury, thessalia, asyff, teaSeller)

    private fun shop(key: String, vararg npcs: NpcType) = ShopType(
        key, key, invId = 4, size = 1, general = false, sellAt = 1000, buyAt = 600, changePer = 20,
        stock = listOf(ShopStock(pot, 1, 100)), npcs = npcs.map { it.id },
    )

    private val shops = ShopTypes(
        listOf(
            shop("varrock_general_store", keeper, assistant),
            shop("auburys_rune_shop", aubury),
            shop("thessalias_fine_clothes", thessalia),
            shop("fancy_clothes_store", asyff),
            shop("ye_olde_tea_shoppe", teaSeller),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs, npcTypes = NpcTypes.of(*all.toTypedArray()),
            npcSpawns = all.mapIndexed { i, type -> NpcSpawn(type, 3216 + i * 2, 3221, source = "test") },
            shopTypes = shops, cycleMillis = millis,
        )
    }

    /** One box of a talk: a line to read on from, or a menu to pick from. */
    private sealed interface Box
    private data class Line(val key: String) : Box
    private data class Pick(val keys: List<String>, val option: Int) : Box

    private fun line(key: String) = Line(key)
    private fun pick(option: Int, vararg keys: String) = Pick(keys.toList(), option)

    /** Talks to [id] through [boxes] (keys under [prefix]); then the shop opens (closed again) or the talk is over. */
    private fun HeadlessClient.walkThrough(id: Int, prefix: String, shop: Boolean, vararg boxes: Box) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, VarrockStores.TALK_TO)
        for (box in boxes) when (box) {
            is Line -> {
                awaitDialogue("$prefix.${box.key}") { it is DialogueView.Chat && it.text == "[$prefix.${box.key}]" }
                continueDialogue()
            }
            is Pick -> {
                val keys = box.keys.map { "[$prefix.$it]" }
                awaitDialogue("$prefix's menu $keys") { it is DialogueView.Menu && it.options == keys }
                chooseOption(box.option)
            }
        }
        if (shop) {
            await("the shop") { if (ShopScreen.MAIN in openInterfaces() && opEnabled(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 27, 2)) Unit else null }
            closeModal()
            await("the shop closed") { if (ShopScreen.MAIN !in openInterfaces()) Unit else null }
        } else {
            await("the talk over") { if (dialogueView == null && ShopScreen.MAIN !in openInterfaces()) Unit else null }
        }
    }

    @Test
    fun `each keeper's talk opens their shop, every way it can`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                for (id in listOf(keeper.id, assistant.id)) {
                    client.walkThrough(id, "varrock_shop_keeper", true, line("help"), pick(1, "selling", "no_thanks"))
                }
                client.walkThrough(aubury.id, "aubury", true, line("greeting"), pick(2, "cape", "yes", "decline"))
                client.walkThrough(
                    aubury.id, "aubury", true, line("greeting"), pick(1, "cape", "yes", "decline"), line("cape_1"), line("cape_2"),
                    pick(1, "view", "no_thanks"),
                )
                client.walkThrough(thessalia.id, "thessalia", true, line("greeting"), pick(2, "what_got", "buy", "no_thanks"))
                client.walkThrough(
                    thessalia.id, "thessalia", true, line("greeting"), pick(1, "what_got", "buy", "no_thanks"), line("what_got"),
                    line("offer"), pick(2, "more", "buy", "no_thanks"),
                )
                client.walkThrough(
                    thessalia.id, "thessalia", true, line("greeting"), pick(1, "what_got", "buy", "no_thanks"), line("what_got"),
                    line("offer"), pick(1, "more", "buy", "no_thanks"), line("more"), line("more_1"), line("more_2"), line("more_3"),
                    line("more_4"), pick(1, "buy", "no_thanks"),
                )
                val asyffOpening = arrayOf(line("greeting"), line("what"), line("just_saying"), line("materials_too"))
                client.walkThrough(asyff.id, "asyff", true, *asyffOpening, pick(1, "stock", "leave", "materials"), line("stock"))
                client.walkThrough(
                    asyff.id, "asyff", true, *asyffOpening, pick(3, "stock", "leave", "materials"), line("materials"),
                    line("materials_1"), line("materials_2"), line("see"),
                )
                client.walkThrough(teaSeller.id, "tea_seller", true, line("greeting"), pick(1, "yes", "what", "no"), line("yes"))
            }
        }
    }

    @Test
    fun `turning each keeper down ends the talk with no shop`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.walkThrough(keeper.id, "varrock_shop_keeper", false, line("help"), pick(2, "selling", "no_thanks"), line("no_thanks"))
                client.walkThrough(
                    aubury.id, "aubury", false, line("greeting"), pick(3, "cape", "yes", "decline"), line("decline"), line("bye"),
                )
                client.walkThrough(
                    aubury.id, "aubury", false, line("greeting"), pick(1, "cape", "yes", "decline"), line("cape_1"), line("cape_2"),
                    pick(2, "view", "no_thanks"), line("no_thanks"), line("bye"),
                )
                client.walkThrough(
                    thessalia.id, "thessalia", false, line("greeting"), pick(3, "what_got", "buy", "no_thanks"), line("no_thanks"),
                    line("return"),
                )
                client.walkThrough(
                    thessalia.id, "thessalia", false, line("greeting"), pick(1, "what_got", "buy", "no_thanks"), line("what_got"),
                    line("offer"), pick(1, "more", "buy", "no_thanks"), line("more"), line("more_1"), line("more_2"), line("more_3"),
                    line("more_4"), pick(2, "buy", "no_thanks"), line("no_thanks"), line("return"),
                )
                client.walkThrough(
                    asyff.id, "asyff", false, line("greeting"), line("what"), line("just_saying"), line("materials_too"),
                    pick(2, "stock", "leave", "materials"), line("leave"),
                )
                client.walkThrough(teaSeller.id, "tea_seller", false, line("greeting"), pick(2, "yes", "what", "no"), line("what"), line("wares"))
                client.walkThrough(teaSeller.id, "tea_seller", false, line("greeting"), pick(3, "yes", "what", "no"), line("no"), line("come_back"))
            }
        }
    }

    private companion object {
        const val POT = 1931
        const val AUBURY = 11435
    }
}
