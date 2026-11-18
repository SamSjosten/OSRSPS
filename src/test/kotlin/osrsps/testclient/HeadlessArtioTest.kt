package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.combat.Prayers
import osrsps.game.EquipmentTab
import osrsps.game.content.Artio
import osrsps.game.content.WildernessLairFee
import osrsps.game.content.WildernessLairFeeLines
import osrsps.game.content.WildernessLairLines
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.Tile

/**
 * Wilderness step W4.8 through the headless client: Hunter's End's way in taking the lairs' fee, Check-Fee and Peek, and
 * the way out; Artio's knockback throwing an unprotected player 3 tiles; her roar at 66% laying traps that bind whoever
 * steps on one; and a kill counting in her kill count, leaving her big bones, and taking 10,000 off the fee for the top
 * damage dealer. The rl-client script test-runner/examples/artio.json does the way in and the fight on the real map.
 */
class HeadlessArtioTest {
    private val boss = NpcType(
        Artio.BOSS, "Artio", size = 3, ops = listOf(null, "Attack", null, null, null), combatLevel = 320,
        internalName = "callisto_singles", stats = NpcStats(attack = 250, strength = 270, defence = 150, hitpoints = 450, ranged = 120, magic = 90),
    )
    private val types = NpcTypes.of(boss)

    // Shaped like the 242 cache's obj, as HeadlessTeleblockTest has it.
    private val protectFromMagic = ObjType(
        20815, "Null", internalName = "placeholder_xmas16_marionette_unpainted",
        params = mapOf(630 to 12, 1751 to (Prayers.PRAYERBOOK shl 16 or PROTECT_FROM_MAGIC), 1752 to "Protect from Magic", 1753 to 37, 1755 to 2675),
    )

    /** The cache's `wbr_entrance_fee_paid` and `wbr_entrance_discount`, bits of `wbr_perm_int_1`. */
    private val varTypes = VarTypes(
        6_000,
        mapOf(WildernessLairFee.VARBIT_PAID to VarbitType(WBR_PERM_INT_1, 0, 0), WildernessLairFee.VARBIT_DISCOUNT to VarbitType(WBR_PERM_INT_1, 1, 3)),
        21_000,
    )

    private fun server(dir: Path, seed: Long, spawn: Boolean = true) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val text = NpcDrops::class.java.getResource(NpcDrops.RESOURCE)!!.readText()
        val names = Regex("\"item\": (\\d+), \"name\": \"([^\"]+)\"").findAll(text).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val objTypes = ObjTypes.of(*(names.map { (id, name) -> ObjType(id, name, stackable = true) } + protectFromMagic).toTypedArray())
        val entrance = LocType(Artio.ENTRANCE, "Cave", listOf("Enter", "Peek", "Check-Fee", null, null), LocCollisionType(width = 3, length = 3))
        val exit = LocType(Artio.EXIT, "Cave", listOf("Exit", null, null, null, null), LocCollisionType(width = 2, length = 2))
        World(
            crcs, CollisionMap().apply { markLoaded(27, 180); markLoaded(48, 57) }, objTypes = objTypes, npcTypes = types,
            random = GameRandom(seed), varTypes = varTypes,
            npcSpawns = if (spawn) listOf(NpcSpawn(boss, Artio.SPAWN.x, Artio.SPAWN.z, wander = 0, source = "test")) else emptyList(),
            prayerList = Prayers.load(objTypes),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes),
            locTypes = LocTypes(mapOf(Artio.ENTRANCE to entrance, Artio.EXIT to exit)),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Artio.ENTRANCE, 10, 0, 3115, 3676, 0))
                add(ZoneLoc(Artio.EXIT, 10, 1, 1758, 11531, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `the way in takes the fee, Check-Fee and Peek answer, and the way out leads back`(@TempDir dir: Path) {
        server(dir, seed = 1, spawn = false).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                val abe = server.world.playerNamed("abe")!!
                client.tele(3116, 3675)
                client.locOp(Artio.ENTRANCE, 3115, 3676, Artio.CHECK_FEE)
                client.seen("[${WildernessLairLines.FEE_DUE.key} fee=50000]")
                client.locOp(Artio.ENTRANCE, 3115, 3676, Artio.PEEK)
                client.seen("[${WildernessLairLines.PEEK_KILLS.key}]")

                client.command("item ${WildernessLairFee.COINS} 50000")
                client.await("the coins") { Unit.takeIf { abe.inventory.count(WildernessLairFee.COINS) == 50_000L } }
                client.locOp(Artio.ENTRANCE, 3115, 3676, Artio.ENTER)
                client.awaitDialogue("the fee's menu") { it is DialogueView.Menu && WildernessLairFeeLines.TITLE.key in it.title }
                client.chooseOption(1)
                client.await("in the den", timeoutMillis = 15_000) { client.position?.takeIf { it == Coord(Artio.ARRIVAL.x, Artio.ARRIVAL.z, 0) } }
                assertEquals(0L, abe.inventory.count(WildernessLairFee.COINS), "the fee taken")
                assertTrue(WildernessLairFee.paid(abe))

                client.locOp(Artio.EXIT, 1758, 11531, 1)
                client.await("outside", timeoutMillis = 15_000) { client.position?.takeIf { it == Coord(Artio.OUTSIDE.x, Artio.OUTSIDE.z, 0) } }
                client.locOp(Artio.ENTRANCE, 3115, 3676, Artio.CHECK_FEE)
                client.seen("[${WildernessLairLines.FEE_PAID.key}]")
                client.locOp(Artio.ENTRANCE, 3115, 3676, Artio.ENTER)
                client.await("in again, nothing asked", timeoutMillis = 15_000) { client.position?.takeIf { it == Coord(Artio.ARRIVAL.x, Artio.ARRIVAL.z, 0) } }
            }
        }
    }

    @Test
    fun `her knockback throws an unprotected player 3 tiles, and her roar lays traps that bind`(@TempDir dir: Path) {
        server(dir, seed = 4).use { server ->
            server.login("bo").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.command("varp 172 1")
                client.tele(Artio.SPAWN.x + 1, Artio.SPAWN.z + 6)
                val npc = server.world.npcs.all.first { it.type.id == Artio.BOSS }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Artio") }
                val fight = client.await("her fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                client.await("a knockback", timeoutMillis = 60_000) { fight.lines.firstOrNull { "threw bo " in it } }
                assertTrue(fight.lines.any { "threw bo 3 to" in it && it.endsWith("for 5") }, fight.lines.joinToString("\n"))

                // Down to 66%: she roars and lays her traps; one stepped on binds.
                npc.hitpoints = 290
                client.await("her roar") { fight.lines.firstOrNull { it.endsWith("roars (1)") } }
                val trap = client.await("her traps") { Artio.traps(npc).firstOrNull() }
                client.tele(trap.x, trap.z)
                client.await("the trap sprung") { fight.lines.firstOrNull { "bo springs a trap" in it } }
                assertTrue(Tile(trap.x, trap.z) !in Artio.traps(npc), "a sprung trap is gone")
            }
        }
    }

    @Test
    fun `a kill counts in her kill count, leaves her big bones, and takes 10,000 off the fee for the top damage`(@TempDir dir: Path) {
        server(dir, seed = 3).use { server ->
            server.login("cy").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(Artio.SPAWN.x + 1, Artio.SPAWN.z - 1)
                val npc = server.world.npcs.all.first { it.type.id == Artio.BOSS }
                client.commandReply("hit 450 ${npc.index}") { it.startsWith("Hit Artio") }
                val cy = server.world.playerNamed("cy")!!
                client.await("the kill count") { Unit.takeIf { cy.vars[Artio.KILL_COUNT_VARP] == 1 } }
                client.await("her bones") { server.world.groundObjs.visibleAt(cy, npc.x, npc.z, 0).firstOrNull { it.id == BIG_BONES } }
                assertEquals(40_000, WildernessLairFee.fee(cy), "10,000 off for the most damage")
            }
        }
    }

    @Test
    fun `Check-Fee says paid when her discounts leave nothing to pay`(@TempDir dir: Path) {
        server(dir, seed = 1, spawn = false).use { server ->
            server.login("eve").use { client ->
                client.await("the login rebuild") { client.position }
                val eve = server.world.playerNamed("eve")!!
                client.command("varbit ${WildernessLairFee.VARBIT_DISCOUNT} 5")
                client.await("five discounts") { Unit.takeIf { WildernessLairFee.fee(eve) == 0 } }
                client.tele(3116, 3675)
                client.locOp(Artio.ENTRANCE, 3115, 3676, Artio.CHECK_FEE)
                client.seen("[${WildernessLairLines.FEE_PAID.key}]")
            }
        }
    }

    @Test
    fun `her orb blocked by Protect from Magic still counts as an attack`(@TempDir dir: Path) {
        server(dir, seed = 4).use { server ->
            server.login("fay").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.command("setlevel prayer 99")
                client.ifButton(Prayers.PRAYERBOOK, PROTECT_FROM_MAGIC, 1)
                client.await("Protect from Magic on") { Unit.takeIf { client.varp(Prayers.VARP_PRAYERS) == 1 shl 12 } }
                client.tele(Artio.SPAWN.x + 1, Artio.SPAWN.z + 6)
                val fay = server.world.playerNamed("fay")!!
                val npc = server.world.npcs.all.first { it.type.id == Artio.BOSS }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Artio") }
                val fight = client.await("her fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                val blocked = client.await("a blocked orb", timeoutMillis = 60_000) { fight.lines.firstOrNull { it.endsWith("fay blocks the orb") } }
                val landed = server.world.tick - fight.ticks + blocked.substringBefore(" ").toLong()
                assertTrue(fay.attackedTick >= landed, "attacked at ${fay.attackedTick}, the orb landed at $landed")
            }
        }
    }

    @Test
    fun `her swipe splashes a player beside her target, a diagonal too`(@TempDir dir: Path) {
        server(dir, seed = 5).use { server ->
            server.login("gil").use { a ->
                server.login("hal").use { b ->
                    a.await("the login rebuild") { a.position }
                    b.await("the login rebuild") { b.position }
                    a.commandReply("god") { it.startsWith("You can") }
                    b.commandReply("god") { it.startsWith("You can") }
                    b.tele(Artio.SPAWN.x - 1, Artio.SPAWN.z - 1)
                    a.tele(Artio.SPAWN.x, Artio.SPAWN.z - 1)
                    val npc = server.world.npcs.all.first { it.type.id == Artio.BOSS }
                    a.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Artio") }
                    val fight = a.await("her fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                    // One swipe, both struck: the two lines on the same tick.
                    a.await("a swipe at both", timeoutMillis = 60_000) {
                        val swipes = fight.lines.filter { "swipes at" in it }.groupBy { it.substringBefore(" ") }.values
                        swipes.firstOrNull { tick -> tick.any { "at gil" in it } && tick.any { "at hal" in it } }
                    }
                }
            }
        }
    }

    @Test
    fun `a swing made before her fight began holds her next attack`(@TempDir dir: Path) {
        server(dir, seed = 2).use { server ->
            server.login("ida").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(Artio.SPAWN.x + 1, Artio.SPAWN.z + 6)
                val npc = server.world.npcs.all.first { it.type.id == Artio.BOSS }
                // Her cooldown is the NPC's own: held from before her fight starts, then 12 ticks from its start.
                npc.nextAttackTick = server.world.tick + 1_000
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Artio") }
                val fight = client.await("her fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                val heldUntil = fight.ticks + 12
                npc.nextAttackTick = server.world.tick + 12
                val attack = Regex("swipes at|throws crescents|knockback at")
                val first = client.await("an attack", timeoutMillis = 30_000) {
                    fight.lines.firstOrNull { attack.containsMatchIn(it) && it.substringBefore(" ").toLong() > heldUntil - 12 }
                }
                assertTrue(first.substringBefore(" ").toLong() >= heldUntil, "attacked before $heldUntil: $first")
            }
        }
    }

    @Test
    fun `she follows a player at the den's way in, past her spawn's leash`(@TempDir dir: Path) {
        server(dir, seed = 7).use { server ->
            server.login("jon").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                // Auto retaliate off, so jon stays where he is and she has to come to him.
                client.command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
                client.await("auto retaliate off") { Unit.takeIf { client.varp(EquipmentTab.VARP_AUTO_RETALIATE_OFF) == 1 } }
                client.tele(Artio.ARRIVAL.x, Artio.ARRIVAL.z)
                val npc = server.world.npcs.all.first { it.type.id == Artio.BOSS }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Artio") }
                val fight = client.await("her fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                client.await("a swipe at jon", timeoutMillis = 60_000) { fight.lines.firstOrNull { "swipes at jon" in it } }
            }
        }
    }

    @Test
    fun `her roar's tick has no attack, so the roar is what shows`(@TempDir dir: Path) {
        server(dir, seed = 4).use { server ->
            server.login("kim").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(Artio.SPAWN.x + 1, Artio.SPAWN.z - 1)
                val npc = server.world.npcs.all.first { it.type.id == Artio.BOSS }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Artio") }
                val fight = client.await("her fight") { server.world.encounters.all.firstOrNull { it.boss === npc } }
                // Her attack due on the tick she roars.
                npc.nextAttackTick = 0
                npc.hitpoints = 290
                val roar = client.await("her roar") { fight.lines.firstOrNull { it.endsWith("roars (1)") } }
                val tick = roar.substringBefore(" ")
                val attack = Regex("swipes at|throws crescents|knockback at")
                assertTrue(fight.lines.none { it.substringBefore(" ") == tick && attack.containsMatchIn(it) }, fight.lines.joinToString("\n"))
            }
        }
    }

    private companion object {
        const val PROTECT_FROM_MAGIC = 21
        const val WBR_PERM_INT_1 = 3755
        const val BIG_BONES = 532
    }
}
