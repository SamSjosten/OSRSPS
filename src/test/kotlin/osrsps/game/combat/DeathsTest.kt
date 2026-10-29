package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.FrameSlot
import osrsps.game.GameRandom
import osrsps.game.InvObj
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
        assertEquals(listOf(Triple(alice, Deaths.VARBIT_FREE_FROM, 1)), varbits)
        val inv = sent.map { it.second }.filterIsInstance<UpdateInvFull>().single()
        assertEquals(Deaths.GRAVE_INV, inv.inventoryId)
        assertEquals(2, inv.capacity)
        val events = sent.map { it.second }.filterIsInstance<IfSetEventsV2>()
        assertEquals(listOf(Deaths.FREEITEMS, Deaths.FREEBUTTON), events.map { it.componentId })
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
}
