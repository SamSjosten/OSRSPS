package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.prot.GameServerProt
import org.junit.jupiter.api.io.TempDir
import osrsps.cache.DbRow
import osrsps.cache.OmnishopLoader
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.BrimhavenAgilityArena
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.OmnishopScreen
import osrsps.game.shop.Omnishops
import osrsps.game.shop.ShopScreen
import osrsps.game.skill.Agility
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
    // Shaped like the 242 cache's: 5516 is a multi-NPC by varbit 4567 showing 7650 (the default) or 7651.
    private val jackie = NpcType(
        BrimhavenAgilityArena.JACKIE, "null", ops = listOf(null, null, null, null, null), multiVarbit = 4567,
        multiNpcs = intArrayOf(7650, 7651, 7650), internalName = "agilityarena_tickettrader",
    )
    private val jackieShown = BrimhavenAgilityArena.JACKIE_SHOWN.map {
        NpcType(it, "Pirate Jackie the Fruit", ops = listOf("Talk-to", null, "Trade", null, null), internalName = "agilityarena_tickettrader_${it}")
    }
    private val objs = listOf(
        ObjType(ShopScreen.COINS, "Coins", stackable = true),
        ObjType(BrimhavenAgilityArena.TICKET, "Agility arena ticket", stackable = true),
        ObjType(BrimhavenAgilityArena.VOUCHER, "Brimhaven voucher", stackable = true),
        ObjType(BrimhavenAgilityArena.AGILITY_CAPE, "Agility cape"),
        ObjType(BrimhavenAgilityArena.AGILITY_CAPE_TRIMMED, "Agility cape(t)"),
        ObjType(BrimhavenAgilityArena.AGILITY_HOOD, "Agility hood"),
        ObjType(TOADFLAX, "Toadflax", certlink = TOADFLAX + 1),
        ObjType(TOADFLAX + 1, "Toadflax", stackable = true, certlink = TOADFLAX),
        ObjType(PIRATES_HOOK, "Pirate's hook"),
        ObjType(BrimhavenAgilityArena.STORE_XP, "Agility"),
        ObjType(BrimhavenAgilityArena.STORE_GRACEFUL_RECOLOUR, "Graceful outfit"),
    )

    /** The Agility Arena Store's db rows as the 242 cache has them (rows 583, 3538-3543, currencies 620 and 3531), the snapdragon and amylase left out. */
    private val omnishops = arenaStore(xpTickets = 1, toadflaxVouchers = 3, hookVouchers = 800)

    private fun arenaStore(xpTickets: Int, toadflaxVouchers: Int, hookVouchers: Int) = OmnishopLoader.build(
        shops = mapOf(BrimhavenAgilityArena.STORE to DbRow(OmnishopLoader.SHOP_TABLE, mapOf(0 to listOf("Agility Arena Store"), 5 to listOf(3538, 3539, 3541, 3542)))),
        stock = mapOf(
            3538 to DbRow(OmnishopLoader.STOCK_TABLE, mapOf(0 to listOf(BrimhavenAgilityArena.STORE_XP), 7 to listOf(TICKETS, xpTickets), 11 to listOf(0), 19 to listOf(0))),
            3539 to DbRow(OmnishopLoader.STOCK_TABLE, mapOf(0 to listOf(TOADFLAX), 7 to listOf(VOUCHERS, toadflaxVouchers), 19 to listOf(0), 20 to listOf(1))),
            3541 to DbRow(OmnishopLoader.STOCK_TABLE, mapOf(0 to listOf(BrimhavenAgilityArena.STORE_GRACEFUL_RECOLOUR), 7 to listOf(VOUCHERS, 250, 3532, 1), 19 to listOf(0))),
            3542 to DbRow(OmnishopLoader.STOCK_TABLE, mapOf(0 to listOf(PIRATES_HOOK), 7 to listOf(VOUCHERS, hookVouchers), 19 to listOf(0))),
        ),
        currencies = mapOf(
            TICKETS to DbRow(OmnishopLoader.CURRENCY_TABLE, mapOf(0 to listOf(BrimhavenAgilityArena.TICKET), 1 to listOf("Agility Arena Ticket"), 2 to listOf("Agility Arena Tickets"))),
            VOUCHERS to DbRow(OmnishopLoader.CURRENCY_TABLE, mapOf(0 to listOf(BrimhavenAgilityArena.VOUCHER), 1 to listOf("Brimhaven Voucher"), 2 to listOf("Brimhaven Vouchers"))),
        ),
    )

    /** The hut's ladder, the ladder up, and every dispenser, as the 242 map has them. */
    private val locs = listOf(
        ZoneLoc(BrimhavenAgilityArena.LADDER_DOWN, 10, 1, 2809, 3194, 0),
        ZoneLoc(BrimhavenAgilityArena.LADDER_UP, 10, 0, 2805, 9590, 3),
    ) + BrimhavenAgilityArena.DISPENSERS.map { ZoneLoc(BrimhavenAgilityArena.DISPENSER, 10, 0, it.x, it.z, it.level) } +
        // The western planks, ends and middle pieces, and a climbing rope in the pit below their east end.
        PLANKS.flatMap { (z, ids) -> ids.mapIndexed { i, (id, angle) -> ZoneLoc(id, 22, angle, 2764 + i, z, 3) } } +
        ZoneLoc(BrimhavenAgilityArena.CLIMBING_ROPE, 10, 0, 2771, 9557, 0) +
        ZoneLoc(MONKEY_BARS, 22, 0, 2771, 9570, 3) +
        ZoneLoc(HAND_HOLDS, 22, 0, 2759, 9559, 3)

    private fun server(dir: Path, omnishops: Omnishops = this.omnishops) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(2809 shr 6, 3194 shr 6)
                for (x in listOf(2761, 2805)) for (z in listOf(9546, 9590)) markLoaded(x shr 6, z shr 6)
            },
            objTypes = ObjTypes.of(*objs.toTypedArray()), npcTypes = NpcTypes.of(izzy, parrot, jackie, *jackieShown.toTypedArray()),
            npcSpawns = listOf(NpcSpawn(izzy, 2807, 3190, wander = 0, source = "test"), NpcSpawn(jackie, 2811, 3190, wander = 0, source = "test")),
            omnishops = omnishops,
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
                // A new refusal, not the first one still in the history.
                val before = client.messages().size
                client.locOp(BrimhavenAgilityArena.LADDER_DOWN, 2809, 3194, BrimhavenAgilityArena.CLIMB)
                client.await("a second refusal") { client.messages().drop(before).firstOrNull { it.text == "[izzy.not_paid]" } }
                client.awaitTicks(2)
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
                // The message can come before the backpack's update.
                client.await("the fee taken") { if (client.held(ShopScreen.COINS) == 0L) Unit else null }
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
                // Exactly one move: the timer stays held, so a slow test thread can't see a second one.
                arena.moveArrow()
                client.await("the arrow to move") { if (client.arrows().size > arrows) Unit else null }
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
    fun `a player who reconnects in the arena gets the arrow again, over the dispenser it moved to while they were gone`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.world.agilityArena.rotateTicks = Int.MAX_VALUE
            val client = server.login("fay")
            client.ready()
            client.tele(2805, 9589, 3)
            client.await("the hint arrow") { client.arrows().firstOrNull() }
            val arena = server.world.agilityArena
            // The arrow moves; the old connection has it, as a dropped one would, then the reconnect comes in.
            val arrows = client.arrows().size
            arena.moveArrow()
            client.await("the arrow to move") { if (client.arrows().size > arrows) Unit else null }
            server.reconnect("fay", client).use { back ->
                back.await("the arrow on the new connection") { back.arrows().firstOrNull() }
            }
            client.close()
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

    @Test
    fun `a broken plank drops the player into the pit, the climbing rope takes them back, and the smooth one holds`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.world.agilityArena.rotateTicks = Int.MAX_VALUE
            server.login("dee").use { client ->
                client.ready()
                client.commandReply("setlevel hitpoints 60") { true }
                client.await("60 HP") { client.stat(HITPOINTS)?.takeIf { it.level == 60 } }
                client.tele(2770, 9557, 3)
                client.awaitTicks(2)
                val arena = server.world.agilityArena
                val rows = BrimhavenAgilityArena.PLANK_ROWS[0]
                val smooth = rows[arena.smoothRow(0)]
                val broken = rows.first { it != smooth }
                // The smooth plank's middle is the map's whole one, the others' broken.
                for (z in rows) {
                    val middles = (2765..2768).map { x -> server.world.locs.at(x, z, 3).single().id }
                    assertEquals(z != smooth, BrimhavenAgilityArena.PLANK_BROKEN in middles, "row $z: $middles")
                }

                client.tele(2770, broken, 3)
                client.locOp(endAt(broken), 2769, broken, 1)
                client.await("in the pit") { client.position?.takeIf { it == Coord(2770, broken, 0) } }
                // 60 * 5 / 100 + 2.
                client.await("a 5 hit") { client.stat(HITPOINTS)?.takeIf { it.level == 55 } }
                client.locOp(BrimhavenAgilityArena.CLIMBING_ROPE, 2771, 9557, BrimhavenAgilityArena.CLIMB)
                client.await("back up") { client.position?.takeIf { it == Coord(2771, 9557, 3) } }

                val before = client.stat(AGILITY)!!.xp
                client.tele(2770, smooth, 3)
                client.locOp(endAt(smooth), 2769, smooth, 1)
                client.await("across") { client.position?.takeIf { it == Coord(2763, smooth, 3) } }
                assertEquals(before + 6, client.await("6 XP") { client.stat(AGILITY)?.takeIf { it.xp >= before + 6 } }.xp)

                // At 99 a broken plank holds too.
                client.command("setlevel agility 99")
                client.await("level 99") { client.stat(AGILITY)?.takeIf { it.level == 99 } }
                client.tele(2770, broken, 3)
                client.locOp(endAt(broken), 2769, broken, 1)
                client.await("across a broken plank") { client.position?.takeIf { it == Coord(2763, broken, 3) } }
                assertEquals(55, client.stat(HITPOINTS)!!.level)

                // The smooth plank moves with the arrow, and the middle pieces are laid again to match.
                val arrows = client.arrows().size
                arena.moveArrow()
                client.await("the arrow to move") { if (client.arrows().size > arrows) Unit else null }
                client.awaitTicks(1)
                val moved = rows[arena.smoothRow(0)]
                for (z in rows) {
                    val middles = (2765..2768).map { x -> server.world.locs.at(x, z, 3).single().id }
                    assertEquals(z != moved, BrimhavenAgilityArena.PLANK_BROKEN in middles, "after the move, row $z: $middles")
                }
            }
        }
    }

    @Test
    fun `the monkey bars take 13 ticks across, not a tile a tick`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.world.agilityArena.rotateTicks = Int.MAX_VALUE
            server.login("mo").use { client ->
                client.ready()
                client.command("setlevel agility 99")
                client.await("level 99") { client.stat(AGILITY)?.takeIf { it.level == 99 } }
                client.tele(2771, 9570, 3)
                client.awaitTicks(2)
                client.locOp(MONKEY_BARS, 2771, 9570, 1)
                client.await("on the bars") { client.position?.takeIf { it.z > 9570 } }
                val started = server.world.tick
                client.await("across") { client.position?.takeIf { it == Coord(2771, 9577, 3) } }
                val took = server.world.tick - started
                // The first tile is taken as the walk starts, so 6 more tiles across the rest of the 13 ticks.
                assertTrue(took in 10..13, "the bars took $took ticks after the first tile")
            }
        }
    }

    @Test
    fun `the hand holds' grab is stopped before the steps across, or the client holds the player still`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.world.agilityArena.rotateTicks = Int.MAX_VALUE
            server.login("holds").use { client ->
                client.ready()
                client.command("setlevel agility 99")
                client.await("level 99") { client.stat(AGILITY)?.takeIf { it.level == 99 } }
                client.tele(2759, 9559, 3)
                client.awaitTicks(2)
                val from = client.actorEvents().size
                client.locOp(HAND_HOLDS, 2759, 9559, 1)
                client.await("across") { client.position?.takeIf { it == Coord(2759, 9566, 3) } }
                val anims = client.actorEvents(from).filter { !it.npc && it.index == client.index && it.kind == ActorEventKind.ANIMATION }.map { it.id }
                val grab = anims.indexOf(HAND_HOLDS_GRAB)
                assertTrue(grab >= 0, "the grab played: $anims")
                assertEquals(-1, anims.getOrNull(grab + 1), "the grab stopped before the walk: $anims")
            }
        }
    }

    @Test
    fun `the hand holds' climb-off waits for the last step to be drawn, or the client holds the player a tile short`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.world.agilityArena.rotateTicks = Int.MAX_VALUE
            server.login("holds2").use { client ->
                client.ready()
                client.command("setlevel agility 99")
                client.await("level 99") { client.stat(AGILITY)?.takeIf { it.level == 99 } }
                client.tele(2759, 9559, 3)
                client.awaitTicks(2)
                val from = client.actorEvents().size
                client.locOp(HAND_HOLDS, 2759, 9559, 1)
                client.await("across") { client.position?.takeIf { it == Coord(2759, 9566, 3) } }
                val off = client.await("the climb-off") {
                    client.actorEvents(from).firstOrNull { !it.npc && it.index == client.index && it.kind == ActorEventKind.ANIMATION && it.id == HAND_HOLDS_OFF }
                }
                assertEquals(Agility.WALK_END_DELAY, off.amount, "the climb-off's delay")
                val grab = client.actorEvents(from).first { !it.npc && it.index == client.index && it.kind == ActorEventKind.ANIMATION && it.id == HAND_HOLDS_GRAB }
                assertEquals(0, grab.amount, "the grab, before any walk, plays at once")
            }
        }
    }

    @Test
    fun `a fall from the hand holds plays at once, so the drop that follows doesn't overtake it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.world.agilityArena.rotateTicks = Int.MAX_VALUE
            server.login("holds3").use { client ->
                client.ready()
                client.command("setlevel agility 20")
                client.await("level 20") { client.stat(AGILITY)?.takeIf { it.level == 20 } }
                val from = client.actorEvents().size
                // About a third of crossings fail at level 20; 30 tries all crossing is under one in a million.
                for (attempt in 1..30) {
                    client.tele(2759, 9559, 3)
                    client.awaitTicks(2)
                    client.locOp(HAND_HOLDS, 2759, 9559, 1)
                    val end = client.await("off the hand holds") { client.position?.takeIf { it == Coord(2759, 9566, 3) || it.level == 0 } }
                    client.awaitTicks(2)
                    if (end.level == 0) break
                }
                val fall = client.actorEvents(from).firstOrNull { !it.npc && it.index == client.index && it.kind == ActorEventKind.ANIMATION && it.id == HAND_HOLDS_FALL }
                assertTrue(fall != null, "a fall in 30 tries")
                assertEquals(0, fall.amount, "the fall's delay")
            }
        }
    }

    @Test
    fun `a teleport off the monkey bars ends the walk there, with no drag back toward them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.world.agilityArena.rotateTicks = Int.MAX_VALUE
            server.login("mo").use { client ->
                client.ready()
                client.command("setlevel agility 99")
                client.await("level 99") { client.stat(AGILITY)?.takeIf { it.level == 99 } }
                client.tele(2771, 9570, 3)
                client.awaitTicks(2)
                client.locOp(MONKEY_BARS, 2771, 9570, 1)
                client.await("on the bars") { client.position?.takeIf { it.z > 9570 } }
                // Away mid-walk: none of the walk's steps left pulls them a tile a tick back toward the bars (exploit
                // review R65A-4).
                client.command("tele 2771 9560 3")
                client.await("away") { client.position?.takeIf { it == Coord(2771, 9560, 3) } }
                client.awaitTicks(15)
                assertEquals(Coord(2771, 9560, 3), client.position)
            }
        }
    }

    @Test
    fun `walking onto the floor spikes jumps them, and a third pressure pad in a row gives no XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.world.agilityArena.rotateTicks = Int.MAX_VALUE
            server.login("fay").use { client ->
                client.ready()
                client.command("setlevel agility 99")
                client.await("level 99") { client.stat(AGILITY)?.takeIf { it.level == 99 } }
                client.tele(2772, 9553, 3)
                var xp = client.stat(AGILITY)!!.xp
                client.walk(2772, 9548)
                client.await("over the spikes") { client.position?.takeIf { it == Coord(2772, 9550, 3) } }
                assertEquals(xp + 24, client.await("24 XP") { client.stat(AGILITY)?.takeIf { it.xp >= xp + 24 } }.xp)

                client.tele(2772, 9586, 3)
                xp = client.stat(AGILITY)!!.xp
                client.walk(2772, 9580)
                client.await("over the pad south") { client.position?.takeIf { it == Coord(2772, 9583, 3) } }
                client.await("26 XP") { client.stat(AGILITY)?.takeIf { it.xp >= xp + 26 } }
                client.walk(2772, 9590)
                client.await("over the pad north") { client.position?.takeIf { it == Coord(2772, 9586, 3) } }
                client.await("52 XP") { client.stat(AGILITY)?.takeIf { it.xp >= xp + 52 } }
                client.walk(2772, 9580)
                client.await("over the pad again") { client.position?.takeIf { it == Coord(2772, 9583, 3) } }
                client.awaitTicks(2)
                assertEquals(xp + 52, client.stat(AGILITY)!!.xp, "the third pad in a row within 8 ticks")
            }
        }
    }

    @Test
    fun `a failed jump over the spinning blades hurts, says so and puts the player back, and the spikes never fail from 50`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.world.agilityArena.rotateTicks = Int.MAX_VALUE
            server.login("gus").use { client ->
                client.ready()
                client.commandReply("setlevel hitpoints 99") { true }
                client.await("99 HP") { client.stat(HITPOINTS)?.takeIf { it.level == 99 } }
                // Below 40 the blades always fail.
                client.tele(2783, 9575, 3)
                client.walk(2783, 9570)
                client.await("hit below 40") { client.stat(HITPOINTS)?.takeIf { it.level < 99 } }
                client.await("put back below 40") { client.position?.takeIf { it == Coord(2783, 9575, 3) } }
                client.awaitTicks(2)
                // Level 40: the Wiki's 153/256, so about two times in five a failure.
                client.command("setlevel agility 40")
                client.await("level 40") { client.stat(AGILITY)?.takeIf { it.level == 40 } }
                var failed = false
                for (attempt in 1..30) {
                    client.commandReply("setlevel hitpoints 99") { true }
                    client.await("healed") { client.stat(HITPOINTS)?.takeIf { it.level == 99 } }
                    client.tele(2783, 9575, 3)
                    client.walk(2783, 9570)
                    // Across, or hit: the put-back tile is where the walk starts, so the hit tells a failure apart.
                    val hit = client.await("over or hit") {
                        when {
                            (client.stat(HITPOINTS)?.level ?: 99) < 99 -> true
                            client.position == Coord(2783, 9572, 3) -> false
                            else -> null
                        }
                    }
                    if (hit) {
                        // 99 * 5 / 100 + 2.
                        client.await("a 6 hit") { client.stat(HITPOINTS)?.takeIf { it.level == 93 } }
                        client.message("[agility_arena.spinning_blades]")
                        client.await("put back") { client.position?.takeIf { it == Coord(2783, 9575, 3) } }
                        client.awaitTicks(2)
                        assertEquals(Coord(2783, 9575, 3), client.position, "back on the tile before the blades")
                        failed = true
                        break
                    }
                    client.awaitTicks(1)
                }
                assertTrue(failed, "no failed jump in 30 at level 40")

                client.command("setlevel agility 50")
                client.await("level 50") { client.stat(AGILITY)?.takeIf { it.level == 50 } }
                client.commandReply("setlevel hitpoints 99") { true }
                client.await("healed") { client.stat(HITPOINTS)?.takeIf { it.level == 99 } }
                repeat(6) {
                    client.tele(2772, 9553, 3)
                    client.walk(2772, 9548)
                    val over = client.await("over the spikes") { client.position?.takeIf { it == Coord(2772, 9550, 3) } }
                    assertEquals(Coord(2772, 9550, 3), over)
                }
                assertEquals(99, client.stat(HITPOINTS)!!.level, "the spikes at 50")
            }
        }
    }

    @Test
    fun `a crossing takes the Wiki's ticks, 4 over the spikes and 5 over the blades, and a run stops on the trap`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.world.agilityArena.rotateTicks = Int.MAX_VALUE
            server.login("tia").use { client ->
                client.ready()
                client.command("setlevel agility 99")
                client.await("level 99") { client.stat(AGILITY)?.takeIf { it.level == 99 } }
                val player = server.world.playerNamed("tia")!!
                // The blades from the tile beside them, running: the run's first step is onto the trap.
                val crossings = listOf(
                    Crossing(Coord(2772, 9553, 3), Coord(2772, 9552, 3), Coord(2772, 9550, 3), 4, run = false),
                    Crossing(Coord(2783, 9575, 3), Coord(2783, 9574, 3), Coord(2783, 9572, 3), 5, run = true),
                )
                for (crossing in crossings) {
                    // The blades still fail 1 time in 256 at 99: a failure (the hit) is tried again.
                    var took = -1
                    for (attempt in 1..5) {
                        client.commandReply("setlevel hitpoints 99") { true }
                        client.await("healed") { client.stat(HITPOINTS)?.takeIf { it.level == 99 } }
                        client.tele(crossing.from.x, crossing.from.z, 3)
                        client.awaitTicks(2)
                        player.runEnabled = crossing.run
                        // Paused and stepped a tick at a time, so the count is game ticks, not test-thread time.
                        client.commandReply("pause") { it.contains("aused after tick") }
                        client.walk(crossing.landing.x, crossing.landing.z)
                        var steppedOn = -1
                        var landed = -1
                        for (tick in 1..12) {
                            client.stepTicks(1)
                            if (player.skills.currentLevel(Skill.HITPOINTS) < 99) break
                            val at = Coord(player.x, player.z, player.level)
                            if (steppedOn < 0 && at != crossing.from) {
                                assertEquals(crossing.trap, at, "the first step ends on the trap, a run too")
                                steppedOn = tick
                            }
                            if (at == crossing.landing) {
                                landed = tick
                                break
                            }
                        }
                        client.commandReply("resume") { it.startsWith("Resumed") }
                        if (landed < 0) continue
                        took = landed - steppedOn
                        break
                    }
                    assertEquals(crossing.ticks - 1, took, "ticks from the step onto the trap to the landing at ${crossing.landing}")
                }
            }
        }
    }

    @Test
    fun `a diagonal step onto the spikes jumps along the corridor they lie in, and a step across it puts the player back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.world.agilityArena.rotateTicks = Int.MAX_VALUE
            server.login("dia").use { client ->
                client.ready()
                client.command("setlevel agility 99")
                client.await("level 99") { client.stat(AGILITY)?.takeIf { it.level == 99 } }
                // The spikes at 2761,9573 and 2761,9574 lie along a north-south corridor (the 242 map), so the jump goes
                // north: from 2760,9572 the first step is north-east onto 2761,9573, and the landing is 2761,9575 past
                // the pair, not off the corridor to the east (round 61, R61C-1).
                client.tele(2760, 9572, 3)
                client.awaitTicks(2)
                client.walk(2763, 9576)
                assertEquals(Coord(2761, 9575, 3), client.await("over the spikes") { client.position?.takeIf { it.z >= 9575 } })
                client.awaitTicks(2)
                assertEquals(Coord(2761, 9575, 3), client.position, "the jump clears the walk, so the player stays where it lands")
                // Straight across the corridor from the side: nothing to jump along, so back where the step started.
                client.tele(2760, 9573, 3)
                client.awaitTicks(2)
                client.walk(2763, 9573)
                client.await("put back") { client.position?.takeIf { it == Coord(2760, 9573, 3) } }
                client.awaitTicks(3)
                assertEquals(Coord(2760, 9573, 3), client.position)
            }
        }
    }

    private data class Crossing(val from: Coord, val trap: Coord, val landing: Coord, val ticks: Int, val run: Boolean)

    @Test
    fun `Agility XP from something else between pads starts the pad streak again`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.world.agilityArena.rotateTicks = Int.MAX_VALUE
            server.login("pip").use { client ->
                client.ready()
                client.command("setlevel agility 99")
                client.await("level 99") { client.stat(AGILITY)?.takeIf { it.level == 99 } }
                client.tele(2772, 9586, 3)
                val xp = client.stat(AGILITY)!!.xp
                client.walk(2772, 9583)
                client.await("over the pad south") { client.position?.takeIf { it == Coord(2772, 9583, 3) } }
                client.await("26 XP") { client.stat(AGILITY)?.takeIf { it.xp >= xp + 26 } }
                // Another obstacle's XP in between.
                client.command("addxp agility 10")
                client.await("10 XP") { client.stat(AGILITY)?.takeIf { it.xp >= xp + 36 } }
                client.walk(2772, 9586)
                client.await("over the pad north") { client.position?.takeIf { it == Coord(2772, 9586, 3) } }
                client.await("62 XP") { client.stat(AGILITY)?.takeIf { it.xp >= xp + 62 } }
                client.walk(2772, 9583)
                client.await("over the pad again") { client.position?.takeIf { it == Coord(2772, 9583, 3) } }
                client.await("88 XP: only the second pad of the new streak") { client.stat(AGILITY)?.takeIf { it.xp >= xp + 88 } }
            }
        }
    }

    @Test
    fun `Pirate Jackie's store trades tickets for 345 XP each and vouchers for items, noted herbs among them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("eve").use { client ->
                client.ready()
                client.command("item ${BrimhavenAgilityArena.TICKET} 3")
                client.command("item ${BrimhavenAgilityArena.VOUCHER} 10")
                client.await("the tickets and vouchers") { if (client.held(BrimhavenAgilityArena.VOUCHER) == 10L && client.held(BrimhavenAgilityArena.TICKET) == 3L) Unit else null }
                client.tele(2810, 3192)
                val jackieIndex = client.await("Jackie in view") { client.npcs().firstOrNull { it.id == BrimhavenAgilityArena.JACKIE } }.index
                client.npcOp(jackieIndex, BrimhavenAgilityArena.TRADE)
                val player = server.world.playerNamed("eve")!!
                client.await("the store") { server.world.omnishopScreen.shopOf(player)?.takeIf { it.row == BrimhavenAgilityArena.STORE } }

                val before = client.stat(AGILITY)!!.xp
                client.ifButton(OmnishopScreen.MAIN, OmnishopScreen.LIST, 2, 0)
                assertEquals(before + 345, client.await("345 XP") { client.stat(AGILITY)?.takeIf { it.xp >= before + 345 } }.xp)
                client.await("a ticket gone") { if (client.held(BrimhavenAgilityArena.TICKET) == 2L) Unit else null }

                client.ifButton(OmnishopScreen.MAIN, OmnishopScreen.LIST, 2, OmnishopScreen.ENTRY_COMPONENTS)
                client.await("a noted toadflax") { if (client.held(TOADFLAX + 1) == 1L) Unit else null }
                assertEquals(7L, client.held(BrimhavenAgilityArena.VOUCHER))
                assertEquals(0L, client.held(TOADFLAX))

                // The recolour isn't built: nothing goes. The hook wants 800 vouchers.
                client.ifButton(OmnishopScreen.MAIN, OmnishopScreen.LIST, 2, 2 * OmnishopScreen.ENTRY_COMPONENTS)
                client.ifButton(OmnishopScreen.MAIN, OmnishopScreen.LIST, 2, 3 * OmnishopScreen.ENTRY_COMPONENTS)
                client.message("[shop.not_enough_currency currency=Brimhaven Vouchers]")
                assertEquals(7L, client.held(BrimhavenAgilityArena.VOUCHER))
                assertEquals(0L, client.held(PIRATES_HOOK))

                // A full stack of noted toadflax: no room, and the vouchers stay.
                client.command("item ${TOADFLAX + 1} ${Int.MAX_VALUE - 1}")
                client.await("a full stack") { if (client.held(TOADFLAX + 1) == Int.MAX_VALUE.toLong()) Unit else null }
                client.ifButton(OmnishopScreen.MAIN, OmnishopScreen.LIST, 2, OmnishopScreen.ENTRY_COMPONENTS)
                client.message("[shop.not_enough_space]")
                assertEquals(7L, client.held(BrimhavenAgilityArena.VOUCHER))
                assertEquals(Int.MAX_VALUE.toLong(), client.held(TOADFLAX + 1))
            }
        }
    }

    @Test
    fun `an entry of Jackie's store priced at nothing gives nothing, XP or item`(@TempDir dir: Path) {
        // Round 60, R60B-4: a cache whose XP or herb entry costs 0 must not hand them out for a click.
        server(dir, arenaStore(xpTickets = 0, toadflaxVouchers = 0, hookVouchers = 1)).use { server ->
            server.login("eve").use { client ->
                client.ready()
                client.command("item ${BrimhavenAgilityArena.VOUCHER} 1")
                client.await("the voucher") { if (client.held(BrimhavenAgilityArena.VOUCHER) == 1L) Unit else null }
                client.tele(2810, 3192)
                val jackieIndex = client.await("Jackie in view") { client.npcs().firstOrNull { it.id == BrimhavenAgilityArena.JACKIE } }.index
                client.npcOp(jackieIndex, BrimhavenAgilityArena.TRADE)
                val player = server.world.playerNamed("eve")!!
                client.await("the store") { server.world.omnishopScreen.shopOf(player)?.takeIf { it.row == BrimhavenAgilityArena.STORE } }

                val before = client.stat(AGILITY)!!.xp
                client.ifButton(OmnishopScreen.MAIN, OmnishopScreen.LIST, 2, 0)
                client.ifButton(OmnishopScreen.MAIN, OmnishopScreen.LIST, 2, OmnishopScreen.ENTRY_COMPONENTS)
                // The hook, priced at a voucher, comes after both clicks: once it is in, those two were handled.
                client.ifButton(OmnishopScreen.MAIN, OmnishopScreen.LIST, 2, 3 * OmnishopScreen.ENTRY_COMPONENTS)
                client.await("the hook") { if (client.held(PIRATES_HOOK) == 1L) Unit else null }
                assertEquals(before, client.stat(AGILITY)!!.xp)
                assertEquals(before, player.skills.xp(Skill.AGILITY))
                assertEquals(0L, client.held(TOADFLAX) + client.held(TOADFLAX + 1))
                assertEquals(0L, client.held(BrimhavenAgilityArena.VOUCHER))
            }
        }
    }

    private fun endAt(z: Int) = PLANKS.getValue(z).last().first

    private companion object {
        const val AGILITY = 16
        const val HITPOINTS = 3
        const val MONKEY_BARS = 3564
        const val HAND_HOLDS = 3583
        const val HAND_HOLDS_GRAB = 1117
        const val HAND_HOLDS_OFF = 1121
        const val HAND_HOLDS_FALL = 1119
        const val TOADFLAX = 2998
        const val PIRATES_HOOK = 2997
        const val TICKETS = 620
        const val VOUCHERS = 3531

        /** The western planks by row, west to east: loc id and angle, as the 242 map has them. */
        val PLANKS = mapOf(
            9556 to listOf(3572 to 3, 3576 to 1, 3576 to 1, 3576 to 3, 3576 to 1, 3571 to 1),
            9557 to listOf(3572 to 3, 3573 to 3, 3574 to 1, 3573 to 3, 3574 to 3, 3570 to 3),
            9558 to listOf(3572 to 3, 3576 to 3, 3574 to 1, 3576 to 1, 3576 to 1, 3572 to 3),
        )
    }
}
