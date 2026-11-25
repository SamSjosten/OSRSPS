package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.prot.GameServerProt
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.BrimhavenAgilityArena
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Karamja step K3.4 through the headless client: Cap'n Izzy No-Beard's talk refusing the arena without 200 coins and
 * taking them with it, his Pay option, the ladder refusing an unpaid player and using up the fee, the hint arrow over the
 * arrowed dispenser, a tag giving the XP, ticket and voucher once until the arrow moves, the ladder back up clearing the
 * arrow, a save in the arena putting the player in the hut, Izzy's Agility cape and hood sale (trimmed with another 99)
 * and free hood, and Pirate Jackie the Fruit's talk. test-runner/examples/agility-arena.json and
 * agility-arena-izzy-jackie.json do the same on the real map.
 */
class HeadlessAgilityArenaTest {
    private val izzy = NpcType(BrimhavenAgilityArena.IZZY, "Cap'n Izzy No-Beard", ops = listOf("Talk-to", null, "Pay", null, null), internalName = "agilityarena_clerk")
    private val parrot = NpcType(BrimhavenAgilityArena.PARROT, "Parrot", internalName = "eadgar_parrotts")
    private val jackie = NpcType(BrimhavenAgilityArena.JACKIE, "Pirate Jackie the Fruit", ops = listOf("Talk-to", null, null, null, null), internalName = "agilityarena_shopkeeper")
    private val objs = listOf(
        ObjType(ShopScreen.COINS, "Coins", stackable = true),
        ObjType(BrimhavenAgilityArena.TICKET, "Agility arena ticket", stackable = true),
        ObjType(BrimhavenAgilityArena.VOUCHER, "Brimhaven voucher", stackable = true),
        ObjType(BrimhavenAgilityArena.AGILITY_CAPE, "Agility cape"),
        ObjType(BrimhavenAgilityArena.AGILITY_CAPE_TRIMMED, "Agility cape(t)"),
        ObjType(BrimhavenAgilityArena.AGILITY_HOOD, "Agility hood"),
    )

    /** The hut's ladder, the ladder up, and every dispenser, as the 242 map has them. */
    private val locs = listOf(
        ZoneLoc(BrimhavenAgilityArena.LADDER_DOWN, 10, 1, 2809, 3194, 0),
        ZoneLoc(BrimhavenAgilityArena.LADDER_UP, 10, 0, 2805, 9590, 3),
    ) + BrimhavenAgilityArena.DISPENSERS.map { ZoneLoc(BrimhavenAgilityArena.DISPENSER, 10, 0, it.x, it.z, it.level) }

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(2809 shr 6, 3194 shr 6)
                for (x in listOf(2761, 2805)) for (z in listOf(9546, 9590)) markLoaded(x shr 6, z shr 6)
            },
            objTypes = ObjTypes.of(*objs.toTypedArray()), npcTypes = NpcTypes.of(izzy, parrot, jackie),
            npcSpawns = listOf(NpcSpawn(izzy, 2807, 3190, wander = 0, source = "test"), NpcSpawn(jackie, 2811, 3190, wander = 0, source = "test")),
            locTypes = LocTypes(locs.map { it.id }.distinct().associateWith { LocType(it, "Loc", listOf("Op", null, null, null, null), LocCollisionType(width = 1, length = 1, interactType = 0)) }),
            staticLocs = StaticLocs().apply { locs.forEach(::add) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("at $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.chat(key: String) =
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }.also { continueDialogue() }

    private fun HeadlessClient.menu(option: Int) {
        awaitDialogue("a menu") { it is DialogueView.Menu }
        chooseOption(option)
    }

    private fun HeadlessClient.message(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.arrows() = packets().filter { it.prot == GameServerProt.HINT_ARROW }

    private fun HeadlessClient.ready() {
        await("the login rebuild") { position }
        commandReply("keys on") { it == "Dialogue shows keys" }
    }

    private fun HeadlessClient.izzyIndex() = await("Izzy in view") { npcs().firstOrNull { it.id == BrimhavenAgilityArena.IZZY } }.index

    @Test
    fun `Izzy turns away a player without 200 coins, takes them through his talk, and the ladder uses up the fee`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.ready()
                client.tele(2808, 3191)
                client.npcOp(client.izzyIndex(), BrimhavenAgilityArena.TALK_TO)
                for (key in listOf("ahoy_capn", "ahoy_there", "parrot_avast", "huh", "cracked")) client.chat("izzy.$key")
                client.menu(3)
                for (key in listOf("use_arena", "entrance_200", "parrot_eight", "warning", "no_money", "parrot_squawk", "no_coins")) client.chat("izzy.$key")
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
                client.locOp(BrimhavenAgilityArena.LADDER_DOWN, 2809, 3194, BrimhavenAgilityArena.CLIMB)
                client.message("[izzy.not_paid]")

                client.command("item ${ShopScreen.COINS} 250")
                client.await("the coins") { if (client.held(ShopScreen.COINS) == 250L) Unit else null }
                client.npcOp(client.izzyIndex(), BrimhavenAgilityArena.TALK_TO)
                for (key in listOf("ahoy_capn", "ahoy_there", "parrot_avast", "huh", "cracked")) client.chat("izzy.$key")
                client.menu(3)
                for (key in listOf("use_arena", "entrance_200", "parrot_eight", "warning")) client.chat("izzy.$key")
                client.menu(1)
                client.chat("izzy.heres_200")
                client.awaitDialogue("the box") { it is DialogueView.Message && it.text == "[izzy.hand_200]" }
                client.continueDialogue()
                client.chat("izzy.wind")
                assertEquals(50L, client.held(ShopScreen.COINS), "the 200-coin fee")
                client.locOp(BrimhavenAgilityArena.LADDER_DOWN, 2809, 3194, BrimhavenAgilityArena.CLIMB)
                client.await("in the arena") { client.position?.takeIf { it == Coord(2805, 9589, 3) } }
                client.locOp(BrimhavenAgilityArena.LADDER_UP, 2805, 9590, BrimhavenAgilityArena.CLIMB)
                client.await("back in the hut") { client.position?.takeIf { it == Coord(2810, 3194, 0) } }
                client.locOp(BrimhavenAgilityArena.LADDER_DOWN, 2809, 3194, BrimhavenAgilityArena.CLIMB)
                client.message("[izzy.not_paid]")
                assertEquals(Coord(2810, 3194, 0), client.position, "the fee lasts one way in")
            }
        }
    }

    @Test
    fun `the arrowed dispenser gives XP, a ticket and a voucher once until the arrow moves, and the others none`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.world.agilityArena.rotateTicks = Int.MAX_VALUE
            server.login("ben").use { client ->
                client.ready()
                client.command("item ${ShopScreen.COINS} 200")
                client.await("the coins") { if (client.held(ShopScreen.COINS) == 200L) Unit else null }
                client.tele(2808, 3191)
                client.npcOp(client.izzyIndex(), BrimhavenAgilityArena.PAY)
                client.message("[izzy.fee_paid]")
                assertEquals(0L, client.held(ShopScreen.COINS))
                client.command("setlevel agility 47")
                client.await("level 47") { client.stat(AGILITY)?.takeIf { it.level == 47 } }
                client.locOp(BrimhavenAgilityArena.LADDER_DOWN, 2809, 3194, BrimhavenAgilityArena.CLIMB)
                client.await("in the arena") { client.position?.takeIf { it == Coord(2805, 9589, 3) } }
                client.await("the hint arrow") { client.arrows().firstOrNull() }
                val arena = server.world.agilityArena
                val active = BrimhavenAgilityArena.DISPENSERS[arena.active]
                val other = BrimhavenAgilityArena.DISPENSERS.first { it != active }
                client.tele(other.x, other.z - 1, 3)
                client.locOp(BrimhavenAgilityArena.DISPENSER, other.x, other.z, BrimhavenAgilityArena.TAG)
                client.message("[agility_arena.not_arrowed]")
                val before = client.stat(AGILITY)!!.xp
                client.tele(active.x, active.z - 1, 3)
                client.locOp(BrimhavenAgilityArena.DISPENSER, active.x, active.z, BrimhavenAgilityArena.TAG)
                client.message("[agility_arena.ticket]")
                client.await("the ticket") { if (client.held(BrimhavenAgilityArena.TICKET) == 1L) Unit else null }
                assertEquals(1L, client.held(BrimhavenAgilityArena.VOUCHER))
                assertEquals(before + 120, client.await("120 XP") { client.stat(AGILITY)?.takeIf { it.xp >= before + 120 } }.xp, "30 for each of 4 tens of levels")
                client.locOp(BrimhavenAgilityArena.DISPENSER, active.x, active.z, BrimhavenAgilityArena.TAG)
                client.message("[agility_arena.one_at_a_time]")
                assertEquals(1L, client.held(BrimhavenAgilityArena.TICKET))
                // Once it moves, to another dispenser, the next tag pays again.
                val arrows = client.arrows().size
                arena.rotateTicks = 1
                client.await("the arrow to move") { if (client.arrows().size > arrows) Unit else null }
                arena.rotateTicks = Int.MAX_VALUE
                client.awaitTicks(2)
                val next = BrimhavenAgilityArena.DISPENSERS[arena.active]
                assertTrue(next != active, "the arrow moves to another dispenser")
                client.tele(next.x, next.z - 1, 3)
                client.locOp(BrimhavenAgilityArena.DISPENSER, next.x, next.z, BrimhavenAgilityArena.TAG)
                client.await("a second ticket") { if (client.held(BrimhavenAgilityArena.TICKET) == 2L) Unit else null }
                val shown = client.arrows().size
                client.tele(2810, 3194)
                client.await("the arrow cleared") { if (client.arrows().size > shown) Unit else null }
            }
        }
    }

    private fun HeadlessClient.izzyCape() {
        npcOp(izzyIndex(), BrimhavenAgilityArena.TALK_TO)
        for (key in listOf("ahoy_capn", "ahoy_there", "parrot_avast", "huh", "cracked")) chat("izzy.$key")
        menu(4)
    }

    @Test
    fun `below 99 Agility Izzy only talks of the cape, at 99 he sells it with its hood for 99,000, trimmed with another 99, and gives a free hood to an owner`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dee").use { client ->
                client.ready()
                client.tele(2808, 3191)
                client.npcOp(client.izzyIndex(), BrimhavenAgilityArena.TALK_TO)
                for (key in listOf("ahoy_capn", "ahoy_there", "parrot_avast", "huh", "cracked")) client.chat("izzy.$key")
                client.menu(4)
                for (key in listOf("cape_about", "cape_symbol", "anything_else")) client.chat("izzy.$key")
                client.menu(5)
                client.chat("izzy.see_you")

                client.commandReply("setlevel agility 99") { it.contains("Agility") }
                client.command("item ${ShopScreen.COINS} 98999")
                client.await("the coins") { if (client.held(ShopScreen.COINS) == 98_999L) Unit else null }
                client.izzyCape()
                for (key in listOf("buy_cape", "price")) client.chat("izzy.$key")
                client.menu(2)
                for (key in listOf("here_you_go", "not_enough", "come_back")) client.chat("izzy.$key")
                client.izzyCape()
                for (key in listOf("buy_cape", "price")) client.chat("izzy.$key")
                client.menu(1)
                for (key in listOf("cant_afford", "so_sad")) client.chat("izzy.$key")

                // Exactly the price: the coins' slot frees up for the cape.
                client.command("item ${ShopScreen.COINS} 1")
                client.await("99,000 coins") { if (client.held(ShopScreen.COINS) == 99_000L) Unit else null }
                client.izzyCape()
                for (key in listOf("buy_cape", "price")) client.chat("izzy.$key")
                client.menu(2)
                for (key in listOf("here_you_go", "suits_you")) client.chat("izzy.$key")
                client.await("the cape and hood") { if (client.held(BrimhavenAgilityArena.AGILITY_CAPE) == 1L && client.held(BrimhavenAgilityArena.AGILITY_HOOD) == 1L) Unit else null }
                assertEquals(0L, client.held(ShopScreen.COINS))

                // An owner asks for another hood, free.
                client.izzyCape()
                client.menu(2)
                for (key in listOf("another_hood", "free")) client.chat("izzy.$key")
                client.awaitDialogue("the box") { it is DialogueView.Message && it.text == "[izzy.hood_given]" }
                client.continueDialogue()
                client.await("a second hood") { if (client.held(BrimhavenAgilityArena.AGILITY_HOOD) == 2L) Unit else null }

                // With another 99 the cape he sells is trimmed.
                client.commandReply("setlevel cooking 99") { it.contains("Cooking") }
                client.command("item ${ShopScreen.COINS} 99000")
                client.await("99,000 coins") { if (client.held(ShopScreen.COINS) == 99_000L) Unit else null }
                client.izzyCape()
                client.menu(1)
                for (key in listOf("buy_cape", "price")) client.chat("izzy.$key")
                client.menu(2)
                for (key in listOf("here_you_go", "suits_you")) client.chat("izzy.$key")
                client.await("the trimmed cape") { if (client.held(BrimhavenAgilityArena.AGILITY_CAPE_TRIMMED) == 1L) Unit else null }
                assertEquals(3L, client.held(BrimhavenAgilityArena.AGILITY_HOOD))
            }
        }
    }

    @Test
    fun `Pirate Jackie the Fruit talks of the arena and the tickets`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("eve").use { client ->
                client.ready()
                client.tele(2810, 3191)
                val index = client.await("Jackie in view") { client.npcs().firstOrNull { it.id == BrimhavenAgilityArena.JACKIE } }.index
                client.npcOp(index, BrimhavenAgilityArena.TALK_TO)
                client.chat("pirate_jackie.ahoy")
                client.menu(1)
                for (key in listOf("what_place", "welcome", "ask_izzy")) client.chat("pirate_jackie.$key")
                client.menu(2)
                for (key in listOf("what_do", "jack_o_tickets", "sounds_good")) client.chat("pirate_jackie.$key")
                client.menu(3)
                for (key in listOf("trade_in", "right_tack")) client.chat("pirate_jackie.$key")
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
            }
        }
    }

    @Test
    fun `a save in the arena puts the player back in the hut`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cal").use { client ->
                client.ready()
                client.tele(2783, 9557, 3)
                val player = server.world.playerNamed("cal")!!
                val save = server.world.snapshot(player)
                assertEquals(listOf(2810, 3194, 0), listOf(save.x, save.z, save.level))
                client.tele(2809, 3190)
                assertNull(BrimhavenAgilityArena.savedAt(player))
                assertTrue(server.world.snapshot(player).let { it.x == 2809 && it.z == 3190 })
            }
        }
    }

    private companion object {
        const val AGILITY = 16
    }
}
