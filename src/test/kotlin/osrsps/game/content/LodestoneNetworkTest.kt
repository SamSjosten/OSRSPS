package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.interfaces.IfCloseSub
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfSetHide
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.Interfaces
import osrsps.game.Player
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.combat.Spells
import osrsps.game.dialogue.DialogueText
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptRunner
import osrsps.map.CollisionMap

/** The lodestone network's rules: activating, the map, the 28-tick cast with no cooldown, Previous destination, and what refuses it. */
class LodestoneNetworkTest {
    private var tick = 0L
    private var attackedRecently = false
    private var wilderness = 0
    private val handlers = Handlers()
    private val scripts = ScriptRunner(handlers, {}) { tick }
    private val landed = mutableListOf<Pair<Long, Destination>>()
    private val said = mutableListOf<String>()
    private val sent = mutableListOf<OutgoingGameMessage>()
    private val keys = DialogueText.NONE
    private val vars = CustomVars.after(VarTypes.NONE)
    private val interfaces = Interfaces { _, message -> sent += message }
    private val home = HomeTeleport(
        currentTick = { tick },
        nowMinutes = { 29_000_000L },
        inCombat = { attackedRecently },
        teleport = { player, to ->
            player.x = to.x
            player.z = to.z
            player.level = to.level
            landed += tick to to
        },
        message = { _, line, values -> said += keys.get(line, *values) },
        wildernessLevel = { wilderness },
    ).also { it.register(handlers) }
    private val network = LodestoneNetwork(
        vars,
        home,
        interfaces,
        message = { _, line, values -> said += keys.get(line, *values) },
        words = { _, line, values -> keys.get(line, *values) },
    ).also { it.register(handlers) }
    private val player = Player(1, "Zezima", 3100, 3500, 0, VarTypes.NONE.with(vars.varbits))
    private val anims = mutableMapOf<Long, Int>()

    private val map = LodestoneMap.INTERFACE

    /** The interfaces opened as modals so far. */
    private fun opened(): List<Int> = sent.filterIsInstance<IfOpenSub>().map { it.interfaceId }

    /** The map's components last hidden (true) or shown (false). */
    private fun hidden(): Map<Int, Boolean> = sent.filterIsInstance<IfSetHide>().filter { it.interfaceId == map }.associate { it.componentId to it.hidden }

    private fun texts(): Map<Int, String> = sent.filterIsInstance<IfSetText>().filter { it.interfaceId == map }.associate { it.componentId to it.text }

    /** The map's components whose option the client may send. */
    private fun enabled(): Set<Int> = sent.filterIsInstance<IfSetEventsV2>().filter { it.interfaceId == map }
        // Slots -1 to -1 (the component itself, not a slot of it); RSProt keeps them as the 2-byte 65535 it sends.
        .onEach { assertEquals(Triple(0xFFFF, 0xFFFF, Interfaces.IF_EVENT_OP1), Triple(it.start, it.end, it.events2), "op 1 on the component itself") }
        .map { it.componentId }.toSet()

    /** A click on a stone's one option in this tick's input phase, then the rest of the tick's script phase. */
    private fun click(site: Lodestones.Site) {
        assertTrue(scripts.trigger(player, Op(OpTarget.LOC, site.id, Lodestones.ACTIVATE)))
        scripts.process(player)
        record()
    }

    /** A click on one of the map's buttons. */
    private fun clickMap(component: Int) {
        assertTrue(scripts.trigger(player, Op.button(map, component, LodestoneMap.TELEPORT)))
        scripts.process(player)
        record()
    }

    /** Cast on the spellbook's Home Teleport in this tick's input phase, then the rest of the tick's script phase. */
    private fun castHomeTeleport() {
        assertTrue(scripts.trigger(player, Op.button(Spells.SPELLBOOK, HomeTeleport.COMPONENT, HomeTeleport.CAST)))
        scripts.process(player)
        record()
    }

    /** `::lodestone [site]`. */
    private fun command(site: Lodestones.Site? = null) {
        if (site == null) network.open(player) else scripts.start(player) { with(network) { teleportTo(site) } }
        scripts.process(player)
        record()
    }

    private fun record() = player.effects.flush(anim = { anims[tick] = it.id }, spotanim = { _, _ -> }, sound = {}, jingle = {})

    private fun run(ticks: Int) = repeat(ticks) {
        tick++
        scripts.process(player)
        record()
    }

    @Test
    fun `a login has Lumbridge's stone active and the rest dormant`() {
        assertTrue(Lodestones.SITES.none { network.unlocked(player, it) })
        network.login(player)
        assertEquals(listOf(Lodestones.LUMBRIDGE), network.unlockedSites(player))
        assertEquals(1, player.vars[vars.lodestonesVarp], "bit 0 of the lodestones' varp")
        network.login(player)
        assertEquals(1, player.vars[vars.lodestonesVarp], "a second login changes nothing")
        assertEquals(null, network.previous(player))
    }

    @Test
    fun `clicking a dormant stone activates it for the player, and says so`() {
        network.login(player)
        click(Lodestones.DRAYNOR)
        assertTrue(network.unlocked(player, Lodestones.DRAYNOR))
        assertEquals(listOf(Lodestones.LUMBRIDGE, Lodestones.DRAYNOR), network.unlockedSites(player))
        assertEquals(listOf("[lodestone.activated name=[lodestone.draynor]]"), said)
        assertFalse(network.isOpen(player), "activating opens no map")
        assertTrue(sent.isEmpty())
        assertFalse(home.casting(player))
        assertEquals(1 or 2, player.vars[vars.lodestonesVarp])
        assertEquals(1, player.vars.getVarbit(vars.lodestoneVarbit(Lodestones.DRAYNOR)))
    }

    @Test
    fun `clicking an active stone opens the map, which shows each stone's state, and Teleport on a row casts there in 28 ticks`() {
        network.login(player)
        network.unlock(player, Lodestones.VARROCK)
        tick = 100
        click(Lodestones.LUMBRIDGE)
        assertTrue(network.isOpen(player))
        assertEquals(listOf(map), opened())
        assertEquals(LodestoneMap.INTERFACE, player.modals.values.single())
        assertEquals("[lodestone.title]", texts()[LodestoneMap.TITLE])
        assertEquals("[lodestone.lumbridge]", texts()[LodestoneMap.rowText(Lodestones.LUMBRIDGE)])
        assertEquals("[lodestone.varrock]", texts()[LodestoneMap.rowText(Lodestones.VARROCK)])
        assertEquals("<col=9f9f9f>[lodestone.draynor]</col>", texts()[LodestoneMap.rowText(Lodestones.DRAYNOR)], "a locked stone's name is grey")
        assertEquals(false, hidden()[LodestoneMap.activeIcon(Lodestones.LUMBRIDGE)])
        assertEquals(true, hidden()[LodestoneMap.dormantIcon(Lodestones.LUMBRIDGE)])
        assertEquals(true, hidden()[LodestoneMap.activeIcon(Lodestones.DRAYNOR)])
        assertEquals(false, hidden()[LodestoneMap.dormantIcon(Lodestones.DRAYNOR)])
        assertEquals(true, hidden()[LodestoneMap.PREVIOUS], "no previous destination yet")
        assertEquals(
            setOf(
                LodestoneMap.iconButton(Lodestones.LUMBRIDGE), LodestoneMap.row(Lodestones.LUMBRIDGE),
                LodestoneMap.iconButton(Lodestones.VARROCK), LodestoneMap.row(Lodestones.VARROCK), LodestoneMap.CLOSE,
            ),
            enabled(),
            "only the activated stones and the close button are clickable",
        )
        assertFalse(home.casting(player))

        clickMap(LodestoneMap.row(Lodestones.VARROCK))
        assertFalse(network.isOpen(player), "the map closes on the pick")
        assertEquals(1, sent.filterIsInstance<IfCloseSub>().size)
        assertTrue(home.casting(player))
        run(27)
        assertTrue(landed.isEmpty())
        assertEquals(mapOf(100L to 4847, 108L to 4850, 115L to 4853, 119L to 4855, 122L to 4857), anims)
        run(1)
        assertEquals(listOf(128L to Lodestones.VARROCK.arrival), landed)
        assertEquals(Triple(3214, 3376, 0), Triple(player.x, player.z, player.level), "the stone's centre tile")
        assertEquals(-1, anims[128L])
        assertEquals(0, player.vars[HomeTeleport.VARP_LAST_CAST], "no cooldown")
        assertFalse(home.casting(player))
        assertTrue(said.isEmpty())
        assertEquals(Lodestones.VARROCK, network.previous(player))
        assertEquals(3, player.vars.getVarbit(vars.lodestonePreviousVarbit), "Varrock's index plus one")
    }

    @Test
    fun `Home Teleport opens the map, and Teleport on an icon casts there in 28 ticks with no cooldown, whatever the timer says`() {
        network.login(player)
        network.unlock(player, Lodestones.EDGEVILLE)
        // OSRS's cooldown would refuse a cast for 30 minutes from here; the lodestones' doesn't count it.
        player.vars[HomeTeleport.VARP_LAST_CAST] = 29_000_000
        tick = 100
        castHomeTeleport()
        assertTrue(network.isOpen(player))
        assertEquals(false, hidden()[LodestoneMap.activeIcon(Lodestones.EDGEVILLE)])
        assertFalse(home.casting(player))
        clickMap(LodestoneMap.iconButton(Lodestones.EDGEVILLE))
        assertFalse(network.isOpen(player))
        assertTrue(home.casting(player))
        run(27)
        assertTrue(landed.isEmpty())
        assertEquals(mapOf(100L to 4847, 108L to 4850, 115L to 4853, 119L to 4855, 122L to 4857), anims)
        run(1)
        assertEquals(listOf(128L to Lodestones.EDGEVILLE.arrival), landed)
        assertEquals(Triple(3073, 3505, 0), Triple(player.x, player.z, player.level), "the stone's centre tile")
        assertEquals(29_000_000, player.vars[HomeTeleport.VARP_LAST_CAST], "the timer is left as it was")
        assertFalse(home.casting(player))
        assertTrue(said.isEmpty())
    }

    @Test
    fun `Previous destination names the stone last teleported to, casts there again, and goes when the stone is locked`() {
        network.login(player)
        network.unlock(player, Lodestones.VARROCK)
        command(Lodestones.VARROCK)
        run(28)
        assertEquals(listOf(Lodestones.VARROCK.arrival), landed.map { it.second })
        sent.clear()
        command()
        assertEquals(false, hidden()[LodestoneMap.PREVIOUS])
        assertEquals("[lodestone.previous name=[lodestone.varrock]]", texts()[LodestoneMap.PREVIOUS_TEXT])
        assertTrue(LodestoneMap.PREVIOUS in enabled())
        clickMap(LodestoneMap.PREVIOUS)
        assertFalse(network.isOpen(player))
        assertTrue(home.casting(player))
        run(28)
        assertEquals(listOf(Lodestones.VARROCK.arrival, Lodestones.VARROCK.arrival), landed.map { it.second })

        // A stone locked again (an admin's ::varp) is no destination, previous or not.
        player.vars.setVarbit(vars.lodestoneVarbit(Lodestones.VARROCK), 0)
        sent.clear()
        command()
        assertEquals(true, hidden()[LodestoneMap.PREVIOUS])
        assertFalse(LodestoneMap.PREVIOUS in enabled())
        clickMap(LodestoneMap.PREVIOUS)
        assertEquals(listOf("[lodestone.locked name=[lodestone.varrock]]"), said, "a click the client wouldn't send")
        assertFalse(home.casting(player))
    }

    @Test
    fun `the close button closes the map with no cast, a click with the map shut does nothing, and the Wilderness refuses before the map`() {
        network.login(player)
        click(Lodestones.LUMBRIDGE)
        assertTrue(network.isOpen(player))
        clickMap(LodestoneMap.CLOSE)
        assertFalse(network.isOpen(player))
        assertEquals(1, sent.filterIsInstance<IfCloseSub>().size)
        assertFalse(home.casting(player))
        run(30)
        assertTrue(landed.isEmpty())

        clickMap(LodestoneMap.row(Lodestones.LUMBRIDGE))
        assertFalse(home.casting(player), "a stale click")
        assertTrue(said.isEmpty())
        assertEquals(1, sent.filterIsInstance<IfCloseSub>().size)

        wilderness = 21
        castHomeTeleport()
        assertEquals(1, opened().size, "no map in the deep Wilderness")
        assertEquals(listOf("[home_teleport.wilderness]", "[home_teleport.wilderness_2]"), said)
    }

    @Test
    fun `with no stone activated the map opens with nothing but its close button to click`() {
        network.login(player)
        player.vars[vars.lodestonesVarp] = 0
        command()
        assertTrue(network.isOpen(player))
        assertEquals(setOf(LodestoneMap.CLOSE), enabled())
        for (site in Lodestones.SITES) assertEquals(true, hidden()[LodestoneMap.activeIcon(site)], site.slug)
        assertTrue(said.isEmpty())
    }

    @Test
    fun `the command casts to a named stone, and refuses one the player hasn't activated`() {
        network.login(player)
        command(Lodestones.VARROCK)
        assertEquals(listOf("[lodestone.locked name=[lodestone.varrock]]"), said)
        assertFalse(home.casting(player))
        assertEquals(null, network.previous(player))
        command(Lodestones.LUMBRIDGE)
        assertTrue(home.casting(player))
        run(28)
        assertEquals(listOf(Lodestones.LUMBRIDGE.arrival), landed.map { it.second })
        assertEquals(Triple(3233, 3221, 0), Triple(player.x, player.z, player.level))
        assertEquals(Lodestones.LUMBRIDGE, network.previous(player))
    }

    @Test
    fun `the command without a site opens the map`() {
        network.login(player)
        command()
        assertTrue(network.isOpen(player))
        assertEquals(listOf(map), opened())
        assertFalse(home.casting(player))
    }

    @Test
    fun `no cast in combat or above level 20 Wilderness, and being attacked stops one`() {
        network.login(player)
        attackedRecently = true
        command(Lodestones.LUMBRIDGE)
        assertEquals(listOf("[home_teleport.in_combat]"), said)
        assertFalse(home.casting(player))
        attackedRecently = false
        said.clear()

        wilderness = 21
        command(Lodestones.LUMBRIDGE)
        assertEquals(listOf("[home_teleport.wilderness]", "[home_teleport.wilderness_2]"), said)
        assertFalse(home.casting(player))
        said.clear()
        assertEquals(null, network.previous(player), "a refused cast is no destination")

        wilderness = 20
        tick = 50
        command(Lodestones.LUMBRIDGE)
        assertTrue(home.casting(player), "level 20 is allowed")
        run(5)
        player.attackedTick = tick
        run(1)
        assertFalse(home.casting(player))
        run(30)
        assertTrue(landed.isEmpty())
        assertTrue(said.isEmpty())
    }

    @Test
    fun `a second click while casting does nothing`() {
        network.login(player)
        command(Lodestones.LUMBRIDGE)
        run(3)
        command(Lodestones.LUMBRIDGE)
        run(25)
        assertEquals(1, landed.size)
        assertEquals(1, anims.values.count { it == 4847 })
    }

    @Test
    fun `the world answers every stone's option and the map's buttons, and knows the command`() {
        val world = World(IntArray(0), CollisionMap())
        for (site in Lodestones.SITES) {
            assertNotNull(world.handlers.find(Op(OpTarget.LOC, site.id, Lodestones.ACTIVATE)), site.slug)
            assertNotNull(world.handlers.find(Op.button(map, LodestoneMap.row(site), LodestoneMap.TELEPORT)), site.slug)
            assertNotNull(world.handlers.find(Op.button(map, LodestoneMap.iconButton(site), LodestoneMap.TELEPORT)), site.slug)
        }
        assertNotNull(world.handlers.find(Op.button(map, LodestoneMap.PREVIOUS, LodestoneMap.TELEPORT)))
        assertNotNull(world.handlers.find(Op.button(map, LodestoneMap.CLOSE, LodestoneMap.CLOSE_OP)))
        val command = world.commands.all.first { it.name == "lodestone" }
        assertFalse(command.admin, "every player may use it: it follows the game's rules")
        assertEquals("::lodestone [lumbridge|draynor|varrock|al_kharid|edgeville]", command.usage)
        assertEquals(Lodestones.AL_KHARID, Lodestones.site("Al_Kharid"))
        assertEquals(null, Lodestones.site("falador"))

        val replies = mutableListOf<String>()
        val dead = Player(2, "Ghost", 3222, 3218, 0, world.varTypes).apply { deathTick = 0 }
        world.commands.execute(dead, "::lodestone", replies::add)
        world.commands.execute(dead, "::lodestone lumbridge extra", replies::add)
        world.commands.execute(dead, "::lodestone falador", replies::add)
        assertEquals(
            listOf(
                "You can't use ::lodestone while you are dead.",
                "You can't use ::lodestone while you are dead.",
                "You can't use ::lodestone while you are dead.",
            ),
            replies,
        )
        dead.deathTick = -1
        dead.chatModal = 1
        world.commands.execute(dead, "::lodestone", replies::add)
        dead.chatModal = -1
        world.commands.execute(dead, "::lodestone lumbridge extra", replies::add)
        world.commands.execute(dead, "::lodestone falador", replies::add)
        assertEquals(
            listOf(
                "You can't use ::lodestone with an interface open or in the middle of something.",
                "Usage: ::lodestone [lumbridge|draynor|varrock|al_kharid|edgeville]",
                "No lodestone called 'falador': ::lodestone [lumbridge|draynor|varrock|al_kharid|edgeville]",
            ),
            replies.drop(3),
        )
    }
}
