package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.FrameSlot
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.ItemPrices
import osrsps.game.Movement
import osrsps.game.NpcType
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.npc.Npc
import osrsps.game.npc.Npcs
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptRunner
import osrsps.map.CollisionMap

class DeathsTest {
    private var tick = 0L
    private val map = CollisionMap().apply { markLoaded(50, 50) }
    private val npcs = Npcs(map, GameRandom(1))
    private val handlers = Handlers()
    private val scripts = ScriptRunner(handlers, { it.modals.clear() }) { tick }
    private val graveType = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))
    private val coins = ObjType(995, "Coins", cost = 1, stackable = true)
    private val sword = ObjType(1277, "Bronze sword", cost = 26, wearpos1 = WearPos.WEAPON)
    private val shield = ObjType(1171, "Wooden shield", cost = 20, wearpos1 = WearPos.SHIELD)
    private val bones = ObjType(526, "Bones", cost = 1)
    private val lobster = ObjType(379, "Lobster", cost = 150)
    private val pipe = ObjType(Blowpipe.LOADED, "Toxic blowpipe", cost = 1, wearpos1 = WearPos.WEAPON)
    private val dart = ObjType(810, "Adamant dart", cost = 1, stackable = true)
    private val scale = ObjType(Blowpipe.SCALE, "Zulrah's scales", cost = 1, stackable = true)
    private val objTypes = ObjTypes.of(coins, sword, shield, bones, lobster, pipe, dart, scale)
    private val alice = Player(1, "alice", 3212, 3210, 0)
    private val bob = Player(2, "bob", 3214, 3210, 0)
    private val online = mutableListOf(alice, bob)
    private val messages = mutableListOf<Pair<Player, String>>()
    private val sent = mutableListOf<Pair<Player, OutgoingGameMessage>>()
    private val teleported = mutableListOf<Player>()
    private val varbits = mutableListOf<Triple<Player, Int, Int>>()
    private val removed = mutableListOf<Npc>()
    /** Stands in for Protect Item; see [Prayers.has]. */
    private var protectItem = false

    private val deaths = Deaths(
        objTypes, graveType, { tick }, { online.asSequence() }, { key -> online.firstOrNull { it.saveKey == key } },
        spawnNpc = { npcs.add(it) }, removeNpc = { npcs.remove(it); removed += it },
        teleportToSpawn = { player -> teleported += player; player.x = 3222; player.z = 3218 },
        openModal = { player, id -> player.modals[FrameSlot.MAINMODAL] = id },
        closeModals = { it.modals.clear() },
        setVarbitNow = { player, id, value -> varbits += Triple(player, id, value) },
        cancelScripts = { scripts.clearAll(it) },
        prayersOff = { protectItem = false },
        protectItem = { protectItem },
        send = { player, message -> sent += player to message },
        message = { player, text -> messages += player to text },
    )

    init {
        deaths.register(handlers)
    }

    private fun tick(count: Int = 1) = repeat(count) {
        tick++
        deaths.process()
    }

    /** Alice dies with a sword on, and a lobster, ten coins, bones and a shield in her bag. */
    private fun aliceDies(): Grave {
        alice.equipment[WearPos.WEAPON] = InvObj(sword.id, 1)
        alice.inventory.add(lobster, 1)
        alice.inventory.add(coins, 10)
        alice.inventory.add(bones, 1)
        alice.inventory.add(shield, 1)
        alice.hitpoints = 0
        alice.runEnergy = 0
        alice.skills.setCurrentLevel(Skill.ATTACK, 5)
        deaths.die(alice)
        tick(Deaths.DEATH_TICKS)
        return deaths.graveOf(alice) ?: error("Alice has no grave")
    }

    /** An op on the grave, from the tile east of it, where Loot's walk would have taken them. */
    private fun npcOp(player: Player, npc: Npc, option: Int): Boolean {
        player.x = npc.x + 1
        player.z = npc.z
        return scripts.trigger(player, Op(OpTarget.NPC, Deaths.GRAVE, option, npc = npc))
    }

    private fun button(player: Player, componentId: Int, sub: Int = -1, op: Int = 1) = scripts.trigger(player, Op.button(Deaths.GRAVESTONE, componentId, op, sub))

    private fun grave(): Npc = npcs.all.single { it.type.id == Deaths.GRAVE }

    @Test
    fun `a player lies dead for the death ticks, then keeps the three most valuable items and the rest go in a grave where they fell`() {
        alice.equipment[WearPos.WEAPON] = InvObj(sword.id, 1)
        alice.inventory.add(lobster, 1)
        alice.inventory.add(coins, 10)
        alice.inventory.add(bones, 1)
        alice.inventory.add(shield, 1)
        alice.hitpoints = 0
        alice.runEnergy = 0
        alice.modals[FrameSlot.MAINMODAL] = 1
        deaths.die(alice)
        assertTrue(alice.dead)
        assertEquals(Deaths.DEATH_ANIM, alice.effects.anim?.id)
        assertTrue(alice.modals.isEmpty())
        tick(Deaths.DEATH_TICKS - 1)
        assertTrue(alice.dead)
        assertTrue(teleported.isEmpty())
        tick()
        assertFalse(alice.dead)
        assertEquals(listOf(alice), teleported)
        assertEquals(10, alice.hitpoints)
        assertEquals(Movement.MAX_ENERGY, alice.runEnergy)
        // The death pose is stopped, or the client would keep her lying there and hold back her steps.
        assertEquals(-1, alice.effects.anim?.id)
        assertEquals(InvObj(sword.id, 1), alice.equipment[WearPos.WEAPON])
        assertEquals(listOf(0 to InvObj(lobster.id, 1), 3 to InvObj(shield.id, 1)), alice.inventory.contents())
        val npc = grave()
        assertEquals(Triple(3212, 3210, 0), Triple(npc.x, npc.z, npc.level))
        val grave = deaths.graveOf(alice)!!
        assertEquals(npc, grave.npc)
        assertEquals(listOf(0 to InvObj(coins.id, 10), 1 to InvObj(bones.id, 1)), grave.items.contents())
        assertEquals(Deaths.GRAVE_TICKS, grave.ticksLeft)
    }

    @Test
    fun `dying drops a delayed script, so a climb half done never finishes`() {
        handlers.onLoc(1, 1) {
            delay(1)
            player.x = 3300
        }
        scripts.trigger(alice, Op(OpTarget.LOC, 1, 1))
        assertTrue(alice.scripts.delayed)
        deaths.die(alice)
        assertFalse(alice.scripts.delayed)
        tick(Deaths.DEATH_TICKS)
        scripts.process(alice)
        assertEquals(3222, alice.x)
    }

    @Test
    fun `a stack counts a unit per item, so three of ten coins are kept`() {
        alice.inventory.add(coins, 10)
        alice.hitpoints = 0
        deaths.die(alice)
        tick(Deaths.DEATH_TICKS)
        assertEquals(listOf(0 to InvObj(coins.id, 3)), alice.inventory.contents())
        assertEquals(listOf(0 to InvObj(coins.id, 7)), deaths.graveOf(alice)!!.items.contents())
    }

    @Test
    fun `Protect Item on as she falls keeps a fourth, though the prayers go off with the fall`() {
        alice.inventory.add(coins, 10)
        protectItem = true
        alice.hitpoints = 0
        deaths.die(alice)
        assertFalse(protectItem)
        tick(Deaths.DEATH_TICKS)
        assertEquals(listOf(0 to InvObj(coins.id, 4)), alice.inventory.contents())
        assertEquals(listOf(0 to InvObj(coins.id, 6)), deaths.graveOf(alice)!!.items.contents())
        // The next death without it keeps three again.
        alice.inventory.add(coins, 6)
        alice.hitpoints = 0
        deaths.die(alice)
        tick(Deaths.DEATH_TICKS)
        assertEquals(listOf(0 to InvObj(coins.id, 3)), alice.inventory.contents())
    }

    @Test
    fun `stats come back to their levels on the respawn`() {
        aliceDies()
        assertEquals(1, alice.skills.currentLevel(Skill.ATTACK))
    }

    @Test
    fun `nothing lost raises no grave`() {
        alice.inventory.add(lobster, 1)
        alice.inventory.add(bones, 1)
        alice.hitpoints = 0
        deaths.die(alice)
        tick(Deaths.DEATH_TICKS)
        assertNull(deaths.graveOf(alice))
        assertTrue(npcs.all.none())
        assertEquals(listOf(0 to InvObj(lobster.id, 1), 1 to InvObj(bones.id, 1)), alice.inventory.contents())
    }

    @Test
    fun `the grave counts down only while its owner is in and hasn't got it open, and collapses with its contents`() {
        val grave = aliceDies()
        tick(10)
        assertEquals(Deaths.GRAVE_TICKS - 10, grave.ticksLeft)
        online.remove(alice)
        tick(10)
        assertEquals(Deaths.GRAVE_TICKS - 10, grave.ticksLeft)
        online += alice
        npcOp(alice, grave.npc, Deaths.LOOT)
        assertEquals(Deaths.GRAVESTONE, alice.modals[FrameSlot.MAINMODAL])
        tick(10)
        assertEquals(Deaths.GRAVE_TICKS - 10, grave.ticksLeft)
        alice.modals.clear()
        tick(10)
        assertEquals(Deaths.GRAVE_TICKS - 20, grave.ticksLeft)
        grave.ticksLeft = 1
        tick()
        assertEquals(listOf(grave.npc), removed)
        assertTrue(deaths.all.isEmpty())
        assertNull(deaths.graveOf(alice))
        assertEquals(alice to "[combat.grave_collapsed]", messages.last())
    }

    @Test
    fun `only the owner can check or loot it`() {
        val grave = aliceDies()
        npcOp(bob, grave.npc, Deaths.CHECK)
        npcOp(bob, grave.npc, Deaths.LOOT)
        assertEquals(listOf(bob to "[combat.grave_not_yours]", bob to "[combat.grave_not_yours]"), messages)
        assertTrue(bob.modals.isEmpty())
        tick(101)
        npcOp(alice, grave.npc, Deaths.CHECK)
        assertEquals(alice to "[combat.grave_minutes minutes=14]", messages.last())
    }

    @Test
    fun `Loot opens the gravestone with its items, Take moves one slot and Take-All the rest, and an empty grave goes`() {
        val grave = aliceDies()
        npcOp(alice, grave.npc, Deaths.LOOT)
        assertEquals(Deaths.GRAVESTONE, alice.modals[FrameSlot.MAINMODAL])
        // Nothing worth 100,000 a unit: every band starts at slot 0, so the whole grave is the free list.
        assertEquals(
            listOf(Triple(alice, Deaths.VARBIT_UNDER_10M, 1), Triple(alice, Deaths.VARBIT_UNDER_1M, 1), Triple(alice, Deaths.VARBIT_FREE_FROM, 1), Triple(alice, Deaths.VARBIT_MERGED, 0)),
            varbits,
        )
        // gravestone_transmit_data: no fee, an empty coffer.
        val transmit = sent.map { it.second }.filterIsInstance<RunClientScript>().single()
        assertEquals(Deaths.TRANSMIT_DATA to listOf<Any>(0, 0), transmit.id to transmit.values.toList())
        val inv = sent.map { it.second }.filterIsInstance<UpdateInvFull>().single()
        assertEquals(Deaths.GRAVE_INV, inv.inventoryId)
        assertEquals(2, inv.capacity)
        val events = sent.map { it.second }.filterIsInstance<IfSetEventsV2>()
        assertEquals(listOf(Deaths.FREEITEMS, Deaths.PAYITEMS, Deaths.FREEBUTTON, Deaths.PAYBUTTON), events.map { it.componentId })
        assertEquals(0 to Deaths.GRAVE_CAPACITY - 1, events[0].start to events[0].end)

        button(alice, Deaths.FREEITEMS, sub = 1)
        assertEquals(listOf(0 to InvObj(lobster.id, 1), 1 to InvObj(bones.id, 1), 3 to InvObj(shield.id, 1)), alice.inventory.contents())
        assertEquals(listOf(0 to InvObj(coins.id, 10)), grave.items.contents())
        assertEquals(2, sent.map { it.second }.filterIsInstance<UpdateInvFull>().size)
        assertEquals(Deaths.GRAVESTONE, alice.modals[FrameSlot.MAINMODAL])

        button(alice, Deaths.FREEBUTTON)
        assertEquals(listOf(0 to InvObj(lobster.id, 1), 1 to InvObj(bones.id, 1), 2 to InvObj(coins.id, 10), 3 to InvObj(shield.id, 1)), alice.inventory.contents())
        assertTrue(alice.modals.isEmpty())
        assertEquals(listOf(grave.npc), removed)
        assertTrue(deaths.all.isEmpty())
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `items worth 100,000 a unit or more wait in the paid list, most valuable first, until Unlock takes the fee from the bank`() {
        // Three crowns kept, the most valuable; a godsword (100,000 to take back), a dragon dagger (1,000) and bones lost.
        val crown = ObjType(20_000, "Crown", cost = 50_000_000)
        val godsword = ObjType(20_001, "Godsword", cost = 20_000_000)
        val dagger = ObjType(20_002, "Dragon dagger", cost = 150_000)
        val types = ObjTypes.of(coins, bones, crown, godsword, dagger)
        // The bank's first tab, as its varbits keep it: the coins the fee is paid from are its only item.
        val tabs = object : osrsps.game.bank.BankTabs {
            val sizes = IntArray(osrsps.game.bank.Bank.TABS + 1)
            override fun get(tab: Int) = sizes[tab]
            override fun set(tab: Int, size: Int) {
                sizes[tab] = size
            }
            override var current = 1
        }
        val bank = osrsps.game.bank.Bank(types)
        val fees = Deaths(
            types, graveType, { tick }, { online.asSequence() }, { key -> online.firstOrNull { it.saveKey == key } },
            spawnNpc = { npcs.add(it) }, removeNpc = { npcs.remove(it); removed += it },
            teleportToSpawn = { player -> player.x = 3222; player.z = 3218 },
            openModal = { player, id -> player.modals[FrameSlot.MAINMODAL] = id }, closeModals = { it.modals.clear() },
            setVarbitNow = { player, id, value -> varbits += Triple(player, id, value) },
            send = { player, message -> sent += player to message }, message = { player, text -> messages += player to text },
            takeFromBank = { player, id, count -> bank.take(player.bank, tabs, id, count) },
        )
        val feeHandlers = Handlers()
        fees.register(feeHandlers)
        val feeScripts = ScriptRunner(feeHandlers, { it.modals.clear() }) { tick }
        repeat(3) { alice.inventory.add(crown, 1) }
        alice.inventory.add(bones, 1)
        alice.inventory.add(dagger, 1)
        alice.inventory.add(godsword, 1)
        alice.hitpoints = 0
        fees.die(alice)
        repeat(Deaths.DEATH_TICKS) {
            tick++
            fees.process()
        }
        val grave = fees.graveOf(alice)!!
        assertEquals(listOf(godsword.id, dagger.id, bones.id), grave.items.contents().map { it.second.id })
        assertEquals(101_000L, fees.fee(alice, grave))

        alice.x = grave.npc.x + 1
        alice.z = grave.npc.z
        feeScripts.trigger(alice, Op(OpTarget.NPC, Deaths.GRAVE, Deaths.LOOT, npc = grave.npc))
        // The client's bands: under 10m from slot 1, under 1m from 1, free from 2, each plus one.
        assertEquals(listOf(Deaths.VARBIT_UNDER_10M to 2, Deaths.VARBIT_UNDER_1M to 2, Deaths.VARBIT_FREE_FROM to 3), varbits.take(3).map { it.second to it.third })
        val transmit = sent.map { it.second }.filterIsInstance<RunClientScript>().last()
        assertEquals(listOf<Any>(101_000, 0), transmit.values.toList())
        fun press(component: Int, sub: Int = -1) = feeScripts.trigger(alice, Op.button(Deaths.GRAVESTONE, component, 1, sub))

        // Locked: neither a paid item's Take nor the free list's Take-All reaches it.
        press(Deaths.PAYITEMS, sub = 0)
        assertEquals(alice to "[combat.grave_locked]", messages.last())
        press(Deaths.FREEBUTTON)
        assertEquals(listOf(godsword.id, dagger.id), grave.items.contents().map { it.second.id })
        assertEquals(1L, alice.inventory.count(bones.id))

        alice.bank.add(coins, 50_000)
        tabs[1] = 1
        press(Deaths.PAYBUTTON)
        assertEquals(alice to "[combat.grave_cant_pay fee=101,000]", messages.last())
        assertEquals(50_000L, alice.bank.count(coins.id))
        alice.bank.add(coins, 51_000)
        press(Deaths.PAYBUTTON)
        assertEquals(alice to "[combat.grave_paid fee=101,000]", messages.last())
        // Every coin taken, through the bank's own take: the tab they were the last of is gone too (Codex, PR 614).
        assertTrue(alice.bank.isEmpty())
        assertEquals(0, tabs[1])
        assertEquals(0L, fees.fee(alice, grave))
        assertEquals(listOf<Any>(0, 0), sent.map { it.second }.filterIsInstance<RunClientScript>().last().values.toList())

        press(Deaths.PAYITEMS, sub = 0)
        assertEquals(1L, alice.inventory.count(godsword.id))
        press(Deaths.PAYBUTTON)
        assertEquals(1L, alice.inventory.count(dagger.id))
        assertTrue(fees.all.isEmpty())
    }

    @Test
    fun `a grave sorted by older prices is put in order again before a click, so a cheaper first item frees nothing worth more`() {
        // Exploit review round 25, R25A-3: which items are free goes by where the first under 100,000 stands.
        val crown = ObjType(20_000, "Crown", cost = 50_000_000)
        val godsword = ObjType(20_001, "Godsword", cost = 20_000_000)
        val whip = ObjType(20_003, "Whip", cost = 3_000_000)
        val types = ObjTypes.of(coins, bones, crown, godsword, whip)
        val prices = mutableMapOf<Int, Long>()
        val graves = Deaths(
            types, graveType, { tick }, { online.asSequence() }, { key -> online.firstOrNull { it.saveKey == key } },
            spawnNpc = { npcs.add(it) }, removeNpc = { npcs.remove(it); removed += it },
            teleportToSpawn = { player -> player.x = 3222; player.z = 3218 },
            openModal = { player, id -> player.modals[FrameSlot.MAINMODAL] = id }, closeModals = { it.modals.clear() },
            setVarbitNow = { player, id, value -> varbits += Triple(player, id, value) },
            send = { player, message -> sent += player to message }, message = { player, text -> messages += player to text },
            value = { prices[it.id] ?: it.cost.toLong() },
        )
        val graveHandlers = Handlers()
        graves.register(graveHandlers)
        val graveScripts = ScriptRunner(graveHandlers, { it.modals.clear() }) { tick }
        repeat(3) { alice.inventory.add(crown, 1) }
        alice.inventory.add(bones, 1)
        alice.inventory.add(whip, 1)
        alice.inventory.add(godsword, 1)
        alice.hitpoints = 0
        graves.die(alice)
        repeat(Deaths.DEATH_TICKS) {
            tick++
            graves.process()
        }
        val grave = graves.graveOf(alice)!!
        assertEquals(listOf(godsword.id, whip.id, bones.id), grave.items.contents().map { it.second.id })
        alice.x = grave.npc.x + 1
        alice.z = grave.npc.z
        fun loot() = graveScripts.trigger(alice, Op(OpTarget.NPC, Deaths.GRAVE, Deaths.LOOT, npc = grave.npc))
        fun press(component: Int, sub: Int = -1) = graveScripts.trigger(alice, Op.button(Deaths.GRAVESTONE, component, 1, sub))

        // The godsword falls under 100,000 with the grave shut: opening it puts the whip first, behind the fee.
        prices[godsword.id] = 50_000
        loot()
        assertEquals(listOf(whip.id, godsword.id, bones.id), grave.items.contents().map { it.second.id })
        assertEquals(1, varbits.last { it.second == Deaths.VARBIT_FREE_FROM }.third - 1, "free from the godsword")
        assertTrue(graves.fee(alice, grave) > 0)
        press(Deaths.FREEITEMS, sub = 0)
        assertEquals(0L, alice.inventory.count(whip.id), "the whip stays behind the fee")

        // Prices change back with the gravestone open: a Take on the godsword where it showed as free does nothing,
        // and the grave is put in order and sent again.
        prices[godsword.id] = 20_000_000
        prices[whip.id] = 10_000
        val shown = sent.size
        press(Deaths.FREEITEMS, sub = 1)
        assertEquals(0L, alice.inventory.count(godsword.id))
        assertEquals(listOf(godsword.id, whip.id, bones.id), grave.items.contents().map { it.second.id })
        assertTrue(sent.drop(shown).any { it.first == alice && it.second is UpdateInvFull }, "the grave shown again")
        // Take-All on the free list now takes the whip and the bones, and the godsword waits for its fee.
        press(Deaths.FREEBUTTON)
        assertEquals(1L, alice.inventory.count(whip.id))
        assertEquals(1L, alice.inventory.count(bones.id))
        assertEquals(0L, alice.inventory.count(godsword.id))
        assertEquals(listOf(godsword.id), grave.items.contents().map { it.second.id })
    }

    @Test
    fun `a Hardcore Ironman's death makes them an Ironman`() {
        val types = osrsps.game.VarTypes(600, mapOf(osrsps.game.SettingsSide.VARBIT_IRONMAN to osrsps.game.VarbitType(499, 3, 5)))
        val carol = Player(3, "carol", 3212, 3210, 0, types)
        online += carol
        carol.vars.setVarbit(osrsps.game.SettingsSide.VARBIT_IRONMAN, Deaths.HARDCORE_IRONMAN)
        carol.hitpoints = 0
        deaths.die(carol)
        tick(Deaths.DEATH_TICKS)
        assertEquals(Triple(carol, osrsps.game.SettingsSide.VARBIT_IRONMAN, Deaths.IRONMAN), varbits.last())
        assertEquals(carol to "[combat.hardcore_lost]", messages.last())
    }

    @Test
    fun `a click on the items with the gravestone closed does nothing`() {
        val grave = aliceDies()
        button(alice, Deaths.FREEITEMS, sub = 0)
        button(alice, Deaths.FREEBUTTON)
        assertEquals(2, grave.items.contents().size)
        npcOp(alice, grave.npc, Deaths.LOOT)
        alice.modals.clear()
        button(alice, Deaths.FREEBUTTON)
        assertEquals(2, grave.items.contents().size)
    }

    @Test
    fun `no room in the inventory says so and leaves the item in the grave`() {
        val grave = aliceDies()
        alice.inventory.add(coins, 1)
        while (alice.inventory.freeSlots > 0) alice.inventory.add(bones, 1)
        npcOp(alice, grave.npc, Deaths.LOOT)
        button(alice, Deaths.FREEITEMS, sub = 1)
        assertEquals(alice to "[combat.grave_no_space]", messages.last())
        assertEquals(2, grave.items.contents().size)
        // Take-All still takes what stacks.
        button(alice, Deaths.FREEBUTTON)
        assertEquals(listOf(1 to InvObj(bones.id, 1)), grave.items.contents())
        assertEquals(11L, alice.inventory.count(coins.id))
    }

    @Test
    fun `dying again adds to the grave standing and starts its timer over`() {
        val grave = aliceDies()
        tick(100)
        alice.inventory.add(bones, 5)
        alice.x = 3200
        alice.z = 3200
        alice.hitpoints = 0
        deaths.die(alice)
        tick(Deaths.DEATH_TICKS)
        assertEquals(1, npcs.all.count())
        assertEquals(grave, deaths.graveOf(alice))
        assertEquals(Triple(3212, 3210, 0), Triple(grave.npc.x, grave.npc.z, grave.npc.level))
        assertEquals(Deaths.GRAVE_TICKS, grave.ticksLeft)
        // The lobster, sword and shield are kept again; the five bones join the coins and bones there.
        assertEquals(7, grave.items.contents().size)
        assertEquals(6L, grave.items.count(bones.id))
    }

    @Test
    fun `a grave moved off a place that is going away keeps its owner, items and time, and still takes her deaths`() {
        val grave = aliceDies()
        tick(100)
        val left = grave.ticksLeft
        deaths.moveGraves({ it.x == 3212 }, 3222, 3218, 0)
        assertEquals(listOf(grave.npc), removed)
        val moved = deaths.graveOf(alice)!!
        assertEquals(Triple(3222, 3218, 0), Triple(moved.npc.x, moved.npc.z, moved.npc.level))
        assertEquals(grave.items.contents(), moved.items.contents())
        assertEquals(left, moved.ticksLeft)
        assertEquals(listOf(moved.npc), npcs.all.toList())
        // Her next death adds to it there.
        alice.inventory.add(bones, 1)
        alice.hitpoints = 0
        deaths.die(alice)
        tick(Deaths.DEATH_TICKS)
        assertEquals(moved, deaths.graveOf(alice))
        assertEquals(2L, moved.items.count(bones.id))
        assertTrue(npcOp(alice, moved.npc, Deaths.LOOT))
        assertEquals(Deaths.GRAVESTONE, alice.modals[FrameSlot.MAINMODAL])
    }

    @Test
    fun `the gravestone does nothing from more than two tiles away, and the grave counts down meanwhile`() {
        val grave = aliceDies()
        npcOp(alice, grave.npc, Deaths.LOOT)
        alice.x = grave.npc.x + Deaths.GRAVE_REACH + 1
        button(alice, Deaths.FREEBUTTON)
        button(alice, Deaths.FREEITEMS, sub = 0)
        assertEquals(2, grave.items.contents().size)
        tick(10)
        assertEquals(Deaths.GRAVE_TICKS - 10, grave.ticksLeft)
        alice.x = grave.npc.x + Deaths.GRAVE_REACH
        button(alice, Deaths.FREEITEMS, sub = 0)
        assertEquals(1, grave.items.contents().size)
    }

    @Test
    fun `examine on a slot past the grave's end does nothing`() {
        val grave = aliceDies()
        npcOp(alice, grave.npc, Deaths.LOOT)
        button(alice, Deaths.FREEITEMS, sub = Deaths.GRAVE_CAPACITY + 5, op = 10)
        assertEquals(2, grave.items.contents().size)
    }

    @Test
    fun `a lost blowpipe takes its darts and scales to the grave, and a kept one keeps them`() {
        alice.equipment[WearPos.WEAPON] = InvObj(pipe.id, 1)
        alice.charges[Blowpipe.DART_TYPE] = dart.id
        alice.charges[Blowpipe.DARTS] = 500
        alice.charges[Blowpipe.SCALES] = 1000
        alice.inventory.add(lobster, 3)
        deaths.die(alice)
        tick(Deaths.DEATH_TICKS)
        // Three lobsters are worth more, so the blowpipe went, and its charges with it.
        val grave = deaths.graveOf(alice) ?: error("Alice has no grave")
        assertEquals(1L, grave.items.count(pipe.id))
        assertEquals(500L, grave.items.count(dart.id))
        assertEquals(1000L, grave.items.count(scale.id))
        assertEquals(emptyMap(), alice.charges.contents())

        bob.equipment[WearPos.WEAPON] = InvObj(pipe.id, 1)
        bob.charges[Blowpipe.DART_TYPE] = dart.id
        bob.charges[Blowpipe.DARTS] = 500
        bob.inventory.add(bones, 5)
        deaths.die(bob)
        tick(Deaths.DEATH_TICKS)
        assertEquals(InvObj(pipe.id, 1), bob.equipment[WearPos.WEAPON])
        assertEquals(500, bob.charges[Blowpipe.DARTS])
    }

    @Test
    fun `losing the carried blowpipe keeps the charges while another is in the bank`() {
        alice.equipment[WearPos.WEAPON] = InvObj(pipe.id, 1)
        alice.bank.add(pipe, 1)
        alice.charges[Blowpipe.DART_TYPE] = dart.id
        alice.charges[Blowpipe.DARTS] = 500
        alice.inventory.add(lobster, 3)
        deaths.die(alice)
        tick(Deaths.DEATH_TICKS)
        val grave = deaths.graveOf(alice) ?: error("Alice has no grave")
        assertEquals(1L, grave.items.count(pipe.id))
        assertEquals(0L, grave.items.count(dart.id))
        assertEquals(500, alice.charges[Blowpipe.DARTS])
    }

    @Test
    fun `the kept items are the ones worth most by the value given, a Grand Exchange price say`() {
        // Made-up prices: the shield and bones are worth more on the Exchange than their values say.
        val prices = ItemPrices(mapOf(shield.id to ItemPrices.Entry("Wooden shield", 500, null), bones.id to ItemPrices.Entry("Bones", 300, null)))
        val byPrice = Deaths(
            objTypes, null, { tick }, { online.asSequence() }, { null }, { npcs.add(it) }, {}, {}, { _, _ -> }, {}, { _, _, _ -> },
            send = { _, _ -> }, message = { _, _ -> }, value = prices::deathValue,
        )
        alice.inventory.add(lobster, 1)
        alice.inventory.add(bones, 1)
        alice.inventory.add(shield, 1)
        alice.inventory.add(sword, 1)
        byPrice.die(alice)
        byPrice.respawn(alice)
        // Shield 500, bones 300, lobster 90 (high alchemy of 150), sword 15: the sword goes.
        assertEquals(listOf(lobster.id, bones.id, shield.id), alice.inventory.contents().map { it.second.id })
    }

    @Test
    fun `a death with no grave type loses the items`() {
        val noGraves = Deaths(
            objTypes, null, { tick }, { online.asSequence() }, { null }, { npcs.add(it) }, {}, {}, { _, _ -> }, {}, { _, _, _ -> },
            send = { _, _ -> }, message = { _, _ -> },
        )
        alice.inventory.add(coins, 10)
        noGraves.die(alice)
        noGraves.respawn(alice)
        assertEquals(listOf(0 to InvObj(coins.id, 3)), alice.inventory.contents())
        assertTrue(npcs.all.none())
    }

    /** A [Deaths] whose deaths go as [place] says, landing players where it puts them. */
    /** What [placed]'s deaths dropped under a player: who, the item, the count and the tile they stood on. */
    private val dropped = ArrayList<Pair<Player, Triple<Int, Int, Triple<Int, Int, Int>>>>()

    private fun placed(closeModals: (Player) -> Unit = { it.modals.clear() }, place: (Player) -> DeathPlace?) = Deaths(
        objTypes, graveType, { tick }, { online.asSequence() }, { key -> online.firstOrNull { it.saveKey == key } },
        spawnNpc = { npcs.add(it) }, removeNpc = { npcs.remove(it) },
        teleportToSpawn = { player -> teleported += player; player.x = 3222; player.z = 3218 },
        openModal = { _, _ -> }, closeModals = closeModals, setVarbitNow = { _, _, _ -> },
        placeOf = place,
        teleport = { player, x, z, level -> player.x = x; player.z = z; player.level = level },
        drop = { player, type, count -> dropped += player to Triple(type.id, count, Triple(player.x, player.z, player.level)) },
    )

    @Test
    fun `a death with a place for it comes back there, and its grave goes where the place says`() {
        val placed = placed { DeathPlace(keepItems = false, 3200, 3201, 0, 3205, 3206, 0) }
        alice.inventory.add(lobster, 1)
        alice.inventory.add(coins, 10)
        alice.inventory.add(bones, 1)
        alice.inventory.add(shield, 1)
        alice.hitpoints = 0
        placed.die(alice)
        repeat(Deaths.DEATH_TICKS) { tick++; placed.process() }
        assertFalse(alice.dead)
        assertEquals(Triple(3205, 3206, 0), Triple(alice.x, alice.z, alice.level))
        assertTrue(teleported.isEmpty(), "not to the spawn")
        val npc = grave()
        assertEquals(Triple(3200, 3201, 0), Triple(npc.x, npc.z, npc.level))
        assertEquals(listOf(0 to InvObj(coins.id, 9), 1 to InvObj(bones.id, 1)), placed.graveOf(alice)!!.items.contents())
    }

    @Test
    fun `a death that keeps everything loses nothing and raises no grave, but still restores the player`() {
        val placed = placed { DeathPlace(keepItems = true, 3200, 3201, 0, 3205, 3206, 0) }
        alice.inventory.add(lobster, 1)
        alice.inventory.add(coins, 10)
        alice.inventory.add(bones, 1)
        alice.inventory.add(shield, 1)
        alice.skills.setCurrentLevel(Skill.ATTACK, 5)
        alice.hitpoints = 0
        placed.die(alice)
        repeat(Deaths.DEATH_TICKS) { tick++; placed.process() }
        assertEquals(4, alice.inventory.contents().size)
        assertTrue(npcs.all.none())
        assertEquals(10, alice.hitpoints)
        assertEquals(1, alice.skills.currentLevel(Skill.ATTACK))
        assertEquals(Triple(3205, 3206, 0), Triple(alice.x, alice.z, alice.level))
    }

    @Test
    fun `a player respawned on their feet, as a raid's wipe does, has their trade closed first, so its offers count towards the loss`() {
        // Stands in for the trade's close hook, which hands the offer back: four shields, worth more than the coins.
        val placed = placed(closeModals = { player ->
            if (player.modals.isNotEmpty()) player.inventory.add(shield, 4)
            player.modals.clear()
        }) { DeathPlace(keepItems = false, 3200, 3201, 0, 3205, 3206, 0) }
        alice.inventory.add(coins, 10)
        alice.modals[FrameSlot.MAINMODAL] = 335
        assertFalse(alice.dead)
        placed.respawn(alice)
        assertTrue(alice.modals.isEmpty())
        // Three shields kept, the fourth and the coins in the grave.
        assertEquals(listOf(shield.id, shield.id, shield.id), alice.inventory.contents().map { it.second.id })
        assertEquals(1L, placed.graveOf(alice)!!.items.count(shield.id))
        assertEquals(10L, placed.graveOf(alice)!!.items.count(coins.id))
    }

    @Test
    fun `a player respawned on their feet keeps the price checker's items for the sorting, not given back to the floor`() {
        val floor = mutableListOf<InvObj>()
        // Stands in for the price checker's close hook: given back, overflow to the floor, unless the player is dead.
        val placed = placed(closeModals = { player ->
            if (!player.dead) for ((slot, obj) in player.priceChecker.contents()) { player.priceChecker[slot] = null; floor += obj }
            player.modals.clear()
        }) { DeathPlace(keepItems = false, 3200, 3201, 0, 3205, 3206, 0) }
        alice.priceChecker[0] = InvObj(lobster.id, 1)
        alice.inventory.add(coins, 10)
        alice.modals[FrameSlot.MAINMODAL] = 464
        placed.respawn(alice)
        assertTrue(floor.isEmpty(), "nothing given back to the floor")
        // The lobster, worth most, is kept with two coins; eight coins go in the grave.
        assertEquals(1L, alice.inventory.count(lobster.id))
        assertEquals(8L, placed.graveOf(alice)!!.items.count(coins.id))
    }

    @Test
    fun `a death that loses items sorts the price checker's in with the rest, so a valuable one being checked is kept`() {
        val placed = placed { DeathPlace(keepItems = false, 3200, 3201, 0, 3205, 3206, 0) }
        // A full backpack of bones, and the shield, worth most, in the price checker.
        alice.inventory.add(bones, 28)
        alice.priceChecker[0] = InvObj(shield.id, 1)
        alice.hitpoints = 0
        placed.die(alice)
        repeat(Deaths.DEATH_TICKS) { tick++; placed.process() }
        assertEquals(1L, alice.inventory.count(shield.id))
        assertEquals(2L, alice.inventory.count(bones.id))
        assertTrue(alice.priceChecker.contents().isEmpty())
        val grave = placed.graveOf(alice)!!
        assertEquals(26L, grave.items.count(bones.id))
        assertEquals(0L, grave.items.count(shield.id))
        assertTrue(dropped.isEmpty())
    }

    @Test
    fun `a death that keeps everything drops what the price checker held and doesn't fit where they come back, with no grave`() {
        val placed = placed { DeathPlace(keepItems = true, 3200, 3201, 0, 3205, 3206, 0) }
        alice.inventory.add(bones, 28)
        alice.priceChecker[0] = InvObj(lobster.id, 1)
        alice.hitpoints = 0
        placed.die(alice)
        repeat(Deaths.DEATH_TICKS) { tick++; placed.process() }
        assertFalse(alice.dead)
        assertTrue(npcs.all.none(), "no grave")
        // Not left in the closed checker, where the next save would bank it (exploit review round 12, RAID-1).
        assertTrue(alice.priceChecker.contents().isEmpty())
        assertEquals(listOf(alice to Triple(lobster.id, 1, Triple(3205, 3206, 0))), dropped)
        assertEquals(28L, alice.inventory.count(bones.id))
    }

    @Test
    fun `a player respawned on their feet falls first, so Protect Item counts and the prayers go off`() {
        alice.inventory.add(lobster, 4)
        alice.inventory.add(coins, 10)
        protectItem = true
        assertFalse(alice.dead)
        deaths.respawn(alice, DeathPlace(keepItems = false, 3200, 3201, 0, 3205, 3206, 0))
        assertFalse(protectItem, "prayers off")
        assertEquals(4L, alice.inventory.count(lobster.id))
        assertEquals(10L, deaths.graveOf(alice)!!.items.count(coins.id))
        assertEquals(0L, deaths.graveOf(alice)!!.items.count(lobster.id))
    }
}
