package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
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

/** The lodestone network's rules: activating, the menu, the 28-tick cast with no cooldown, and what refuses it. */
class LodestoneNetworkTest {
    private var tick = 0L
    private var attackedRecently = false
    private var wilderness = 0
    private val handlers = Handlers()
    private val scripts = ScriptRunner(handlers, {}) { tick }
    private val landed = mutableListOf<Pair<Long, Destination>>()
    private val said = mutableListOf<String>()
    private val menus = mutableListOf<Pair<String, List<String>>>()
    private var pick = 0
    private val keys = DialogueText.NONE
    private val vars = CustomVars.after(VarTypes.NONE)
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
        message = { _, line, values -> said += keys.get(line, *values) },
        words = { _, line -> keys[line] },
        menu = { title, options ->
            menus += keys[title] to options.map { keys[it] }
            pick
        },
    ).also { it.register(handlers) }
    private val player = Player(1, "Zezima", 3100, 3500, 0, VarTypes.NONE.with(vars.varbits))
    private val anims = mutableMapOf<Long, Int>()

    /** A click on a stone's one option in this tick's input phase, then the rest of the tick's script phase. */
    private fun click(site: Lodestones.Site) {
        assertTrue(scripts.trigger(player, Op(OpTarget.LOC, site.id, Lodestones.ACTIVATE)))
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
        scripts.start(player) { with(network) { if (site == null) open() else teleportTo(site) } }
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
    }

    @Test
    fun `clicking a dormant stone activates it for the player, and says so`() {
        network.login(player)
        click(Lodestones.DRAYNOR)
        assertTrue(network.unlocked(player, Lodestones.DRAYNOR))
        assertEquals(listOf(Lodestones.LUMBRIDGE, Lodestones.DRAYNOR), network.unlockedSites(player))
        assertEquals(listOf("[lodestone.activated name=[lodestone.draynor]]"), said)
        assertTrue(menus.isEmpty(), "activating opens no menu")
        assertFalse(home.casting(player))
        assertEquals(1 or 2, player.vars[vars.lodestonesVarp])
        assertEquals(1, player.vars.getVarbit(vars.lodestoneVarbit(Lodestones.DRAYNOR)))
    }

    @Test
    fun `clicking an active stone opens the menu of activated stones, and a pick casts there in 28 ticks`() {
        network.login(player)
        network.unlock(player, Lodestones.VARROCK)
        tick = 100
        pick = 2
        click(Lodestones.LUMBRIDGE)
        assertEquals(listOf("[lodestone.title]" to listOf("[lodestone.lumbridge]", "[lodestone.varrock]")), menus)
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
    }

    @Test
    fun `Home Teleport opens the same menu, and a pick casts there in 28 ticks with no cooldown, whatever the timer says`() {
        network.login(player)
        network.unlock(player, Lodestones.EDGEVILLE)
        // OSRS's cooldown would refuse a cast for 30 minutes from here; the lodestones' doesn't count it.
        player.vars[HomeTeleport.VARP_LAST_CAST] = 29_000_000
        tick = 100
        pick = 2
        castHomeTeleport()
        assertEquals(listOf("[lodestone.title]" to listOf("[lodestone.lumbridge]", "[lodestone.edgeville]")), menus)
        assertTrue(home.casting(player))
        run(27)
        assertTrue(landed.isEmpty())
        assertEquals(mapOf(100L to 4847, 108L to 4850, 115L to 4853, 119L to 4855, 122L to 4857), anims)
        run(1)
        assertEquals(listOf(128L to Lodestones.EDGEVILLE.arrival), landed)
        assertEquals(Triple(3067, 3505, 0), Triple(player.x, player.z, player.level), "the stone's centre tile")
        assertEquals(29_000_000, player.vars[HomeTeleport.VARP_LAST_CAST], "the timer is left as it was")
        assertFalse(home.casting(player))
        assertTrue(said.isEmpty())
    }

    @Test
    fun `Home Teleport's menu closed without a pick casts nothing, and the Wilderness refuses it before the menu`() {
        network.login(player)
        pick = 0
        castHomeTeleport()
        assertEquals(1, menus.size)
        assertFalse(home.casting(player))
        run(30)
        assertTrue(landed.isEmpty())
        wilderness = 21
        castHomeTeleport()
        assertEquals(1, menus.size, "no menu in the deep Wilderness")
        assertEquals(listOf("[home_teleport.wilderness]", "[home_teleport.wilderness_2]"), said)
    }

    @Test
    fun `closing the menu without a pick casts nothing, and no activated stone shows no menu`() {
        network.login(player)
        pick = 0
        click(Lodestones.LUMBRIDGE)
        assertEquals(1, menus.size)
        assertFalse(home.casting(player))
        run(30)
        assertTrue(landed.isEmpty())
        player.vars[vars.lodestonesVarp] = 0
        command()
        assertEquals(1, menus.size, "a chat menu needs an option")
        assertTrue(said.isEmpty())
    }

    @Test
    fun `the command casts to a named stone, and refuses one the player hasn't activated`() {
        network.login(player)
        command(Lodestones.VARROCK)
        assertEquals(listOf("[lodestone.locked name=[lodestone.varrock]]"), said)
        assertFalse(home.casting(player))
        command(Lodestones.LUMBRIDGE)
        assertTrue(home.casting(player))
        run(28)
        assertEquals(listOf(Lodestones.LUMBRIDGE.arrival), landed.map { it.second })
        assertEquals(Triple(3233, 3221, 0), Triple(player.x, player.z, player.level))
    }

    @Test
    fun `the command without a site opens the menu`() {
        network.login(player)
        pick = 1
        command()
        assertEquals(listOf("[lodestone.title]" to listOf("[lodestone.lumbridge]")), menus)
        assertTrue(home.casting(player))
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
    fun `the world answers every stone's option and knows the command`() {
        val world = World(IntArray(0), CollisionMap())
        for (site in Lodestones.SITES) assertNotNull(world.handlers.find(Op(OpTarget.LOC, site.id, Lodestones.ACTIVATE)), site.slug)
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
