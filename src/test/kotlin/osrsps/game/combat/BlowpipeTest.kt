package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.EquipmentTab
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.SeqNames
import osrsps.game.SpotanimNames
import osrsps.game.WearPos
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptRunner

/** Loading the blowpipe with darts and scales, and its Check, Unload, Uncharge and Dismantle, through the handlers. */
class BlowpipeTest {
    private var tick = 0L
    private val loaded = ObjType(
        Blowpipe.LOADED, "Toxic blowpipe", iops = listOf(null, "Wield", "Check", "Unload", "Uncharge"), wearpos1 = WearPos.WEAPON,
        params = mapOf(EquipmentTab.PARAM_WORN_OP1 to "Check", EquipmentTab.PARAM_WORN_OP1 + 1 to "Unload", EquipmentTab.PARAM_WORN_OP1 + 2 to "Uncharge"),
        internalName = "toxic_blowpipe_loaded",
    )
    private val empty = ObjType(Blowpipe.EMPTY, "Toxic blowpipe (empty)", iops = listOf(null, null, "Dismantle", null, "Drop"), internalName = "toxic_blowpipe")
    private val dragonDart = ObjType(11230, "Dragon dart", stackable = true, internalName = "dragon_dart")
    private val runeDart = ObjType(811, "Rune dart", stackable = true, internalName = "rune_dart")
    private val scales = ObjType(Blowpipe.SCALE, "Zulrah's scales", stackable = true, internalName = "snakeboss_scale")
    private val objTypes = ObjTypes.of(loaded, empty, dragonDart, runeDart, scales)
    private val weapons = RangedWeapons.parse(
        """{ "weapons": [ { "items": ["toxic_blowpipe_loaded", "toxic_blowpipe"], "ammo": "darts", "tier": 7, "range": 5, "anim": "blow",
              "loaded": true, "delay": "thrown", "source": "test" } ],
          "ammo": [ { "items": ["rune_dart"], "ammo": "darts", "tier": 5, "travel": "rune" },
                    { "items": ["dragon_dart"], "ammo": "darts", "tier": 7, "travel": "dragon" } ] }""",
        objTypes, SeqNames(mapOf("blow" to 5061)), SpotanimNames(mapOf("rune" to 231, "dragon" to 1122)),
    )
    private val blowpipe = Blowpipe(objTypes, weapons)
    private val handlers = Handlers()
    private val scripts = ScriptRunner(handlers, { it.modals.clear() }) { tick }
    private val messages = mutableListOf<String>()
    private val alice = Player(1, "alice", 3222, 3218, 0)

    init {
        blowpipe.register(handlers) { _, text -> messages += text }
    }

    private fun run(op: Op) {
        assertTrue(scripts.trigger(alice, op), "no handler for $op")
        scripts.process(alice)
    }

    private fun slotOf(id: Int) = alice.inventory.contents().first { it.second.id == id }.first

    private fun use(obj: Int, on: Int) = run(Op.objOnObj(obj, slotOf(obj), on, slotOf(on)))

    private fun option(id: Int, option: Int) = run(Op(OpTarget.OBJ, id, option, slotOf(id)))

    @Test
    fun `scales charge the empty blowpipe, darts go in either way round, and Check says what it holds`() {
        alice.inventory.add(empty, 1)
        alice.inventory.add(scales, 20_000)
        alice.inventory.add(dragonDart, 500)
        use(Blowpipe.SCALE, Blowpipe.EMPTY)
        // No more than it holds; the rest stay in the inventory.
        assertEquals(Blowpipe.MAX_SCALES, alice.charges[Blowpipe.SCALES])
        assertEquals(20_000L - Blowpipe.MAX_SCALES, alice.inventory.count(Blowpipe.SCALE))
        assertEquals(1L, alice.inventory.count(Blowpipe.LOADED))
        assertEquals(0L, alice.inventory.count(Blowpipe.EMPTY))
        run(Op.objOnObj(Blowpipe.LOADED, slotOf(Blowpipe.LOADED), dragonDart.id, slotOf(dragonDart.id)))
        assertEquals(500, alice.charges[Blowpipe.DARTS])
        assertEquals(dragonDart.id, alice.charges[Blowpipe.DART_TYPE])
        assertEquals(0L, alice.inventory.count(dragonDart.id))
        assertEquals("[combat.blowpipe_check dart=Dragon dart darts=500 scales=16,383 percent=100.0]", messages.last())
        // Another kind of dart waits for an Unload.
        alice.inventory.add(runeDart, 10)
        use(runeDart.id, Blowpipe.LOADED)
        assertEquals("[combat.blowpipe_other_darts]", messages.last())
        assertEquals(10L, alice.inventory.count(runeDart.id))
        messages.clear()
        option(Blowpipe.LOADED, 3)
        assertEquals(listOf("[combat.blowpipe_check dart=Dragon dart darts=500 scales=16,383 percent=100.0]"), messages)
    }

    @Test
    fun `Unload gives the darts back, Uncharge the darts and scales, and the empty one dismantles for scales`() {
        alice.inventory.add(loaded, 1)
        alice.charges[Blowpipe.DART_TYPE] = dragonDart.id
        alice.charges[Blowpipe.DARTS] = 30
        alice.charges[Blowpipe.SCALES] = 1_000
        option(Blowpipe.LOADED, 4)
        assertEquals(30L, alice.inventory.count(dragonDart.id))
        assertEquals(0, alice.charges[Blowpipe.DARTS])
        assertNull(blowpipe.dart(alice))
        assertEquals("[combat.blowpipe_check_empty scales=1,000 percent=6.1]", blowpipe.check(alice))
        use(dragonDart.id, Blowpipe.LOADED)
        option(Blowpipe.LOADED, 5)
        assertEquals(30L, alice.inventory.count(dragonDart.id))
        assertEquals(1_000L, alice.inventory.count(Blowpipe.SCALE))
        assertEquals(1L, alice.inventory.count(Blowpipe.EMPTY))
        assertTrue(alice.charges.contents().isEmpty(), alice.charges.contents().toString())
        option(Blowpipe.EMPTY, 3)
        assertEquals(0L, alice.inventory.count(Blowpipe.EMPTY))
        assertEquals(1_000L + Blowpipe.DISMANTLE_SCALES, alice.inventory.count(Blowpipe.SCALE))
    }

    @Test
    fun `worn, its own options check it and uncharge it in place`() {
        alice.equipment[WearPos.WEAPON] = InvObj(Blowpipe.LOADED, 1)
        alice.charges[Blowpipe.DART_TYPE] = runeDart.id
        alice.charges[Blowpipe.DARTS] = 5
        alice.charges[Blowpipe.SCALES] = 50
        run(Op(OpTarget.WORN, Blowpipe.LOADED, 1, WearPos.WEAPON))
        assertEquals("[combat.blowpipe_check dart=Rune dart darts=5 scales=50 percent=0.3]", messages.last())
        run(Op(OpTarget.WORN, Blowpipe.LOADED, 3, WearPos.WEAPON))
        assertEquals(5L, alice.inventory.count(runeDart.id))
        assertEquals(50L, alice.inventory.count(Blowpipe.SCALE))
        assertEquals(InvObj(Blowpipe.EMPTY, 1), alice.equipment[WearPos.WEAPON])
    }

    @Test
    fun `Uncharge changes nothing when a stack has no room for all the scales`() {
        alice.inventory.add(loaded, 1)
        alice.inventory.add(scales, Inventory.MAX_STACK - 10)
        alice.charges[Blowpipe.DART_TYPE] = dragonDart.id
        alice.charges[Blowpipe.DARTS] = 30
        alice.charges[Blowpipe.SCALES] = 1_000
        option(Blowpipe.LOADED, 5)
        assertEquals("[combat.no_room]", messages.last())
        assertEquals(0L, alice.inventory.count(dragonDart.id))
        assertEquals((Inventory.MAX_STACK - 10).toLong(), alice.inventory.count(Blowpipe.SCALE))
        assertEquals(1L, alice.inventory.count(Blowpipe.LOADED))
        assertEquals(30 to 1_000, alice.charges[Blowpipe.DARTS] to alice.charges[Blowpipe.SCALES])
    }

    @Test
    fun `darts loaded into the empty blowpipe come back when it is dismantled, and a full inventory stops it`() {
        alice.inventory.add(empty, 1)
        alice.inventory.add(dragonDart, 40)
        use(dragonDart.id, Blowpipe.EMPTY)
        assertEquals(40, alice.charges[Blowpipe.DARTS])
        // The blowpipe's slot takes the scales, but the darts would need another.
        val filler = ObjType(4151, "Abyssal whip", internalName = "abyssal_whip")
        while (alice.inventory.freeSlots > 0) alice.inventory.add(filler, 1)
        option(Blowpipe.EMPTY, 3)
        assertEquals("[combat.no_room]", messages.last())
        assertEquals(1L, alice.inventory.count(Blowpipe.EMPTY))
        alice.inventory.remove(slotOf(filler.id))
        option(Blowpipe.EMPTY, 3)
        assertEquals(0L, alice.inventory.count(Blowpipe.EMPTY))
        assertEquals(Blowpipe.DISMANTLE_SCALES.toLong(), alice.inventory.count(Blowpipe.SCALE))
        assertEquals(40L, alice.inventory.count(dragonDart.id))
        assertTrue(alice.charges.contents().isEmpty(), alice.charges.contents().toString())
    }

    @Test
    fun `a shot takes a dart and two scales in three, and the last scale leaves the worn blowpipe empty`() {
        alice.equipment[WearPos.WEAPON] = InvObj(Blowpipe.LOADED, 1)
        alice.charges[Blowpipe.DART_TYPE] = dragonDart.id
        alice.charges[Blowpipe.DARTS] = 10
        alice.charges[Blowpipe.SCALES] = 2
        val said = mutableListOf<String>()
        assertTrue(blowpipe.use(alice, { said += it }) { true })
        assertEquals(9 to 2, alice.charges[Blowpipe.DARTS] to alice.charges[Blowpipe.SCALES])
        assertTrue(blowpipe.use(alice, { said += it }) { false })
        assertEquals(8 to 1, alice.charges[Blowpipe.DARTS] to alice.charges[Blowpipe.SCALES])
        assertTrue(blowpipe.use(alice, { said += it }) { false })
        assertEquals(InvObj(Blowpipe.EMPTY, 1), alice.equipment[WearPos.WEAPON])
        assertEquals(listOf("[combat.blowpipe_ran_out]"), said)
        assertTrue(!blowpipe.use(alice, { said += it }) { false })
        assertEquals("[combat.blowpipe_no_scales]", said.last())
        alice.charges[Blowpipe.SCALES] = 5
        alice.charges[Blowpipe.DARTS] = 0
        assertTrue(!blowpipe.use(alice, { said += it }) { false })
        assertEquals("[combat.blowpipe_no_darts]", said.last())
    }
}
