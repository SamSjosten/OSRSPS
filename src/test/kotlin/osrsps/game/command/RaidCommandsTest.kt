package osrsps.game.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.raid.PartyScaling
import osrsps.map.CollisionMap

class RaidCommandsTest {
    private val guardian = NpcType(16659, "Archive Guardian", size = 5, internalName = "tfa_guardian", stats = NpcStats(hitpoints = 550))
    private val types = NpcTypes.of(guardian)
    private val def = EncounterDef("guardian", guardian, listOf(PhaseDef("flying"), PhaseDef("grounded")))
    private val player = Player(1, "test", 3222, 3218, 0)
    private val replies = mutableListOf<String>()

    private fun world(vararg defs: EncounterDef) = World(IntArray(0), CollisionMap(), npcTypes = types, partyScaling = PartyScaling.load(types), encounterDefs = defs.toList())

    private fun World.run(input: String) = commands.execute(player, input, replies::add)

    @Test
    fun `raid starts a fight at a phase for a party size on a seed, shows it and stops it`() {
        val world = world(def)
        world.run("raid guardian phase=grounded party=8 seed=7")
        world.run("raid")
        world.run("raid log")
        world.run("raid digest 0")
        world.run("raid digest 1")
        world.run("raid guardian")
        world.run("raid stop")
        world.run("raid")
        assertEquals(
            listOf(
                "Started guardian #1 on Archive Guardian #1 at 3222, 3218, phase 2 (grounded), 1950 hp for a party of 8, seed 7.",
                "guardian #1: phase 2 (grounded) for 0 of 0 ticks; Archive Guardian 1950/1950 hp; 0 players, 0 adds, 0 hazards",
                "0 grounded: phase starts",
                "guardian to tick 0: 1 lines, digest %08x".format("0 grounded: phase starts".hashCode()),
                "The fight has run to tick 0 so far",
                "You're in guardian already: ::raid stop first",
                "Stopped guardian.",
                "You're not in a fight. Bosses: guardian",
            ),
            replies,
        )
        assertEquals(7, world.random.seed)
        assertEquals(0, world.npcs.count, "the boss went with the fight")
    }

    @Test
    fun `raid refuses unknown bosses, phases, sizes and seeds, and fight commands outside one`() {
        world().run("raid")
        val world = world(def)
        world.run("raid ezekiel")
        world.run("raid guardian phase=3")
        world.run("raid guardian party=9")
        world.run("raid guardian seed=x")
        world.run("raid guardian colour=red")
        world.run("raid log")
        world.run("raid stop")
        assertEquals(
            listOf(
                "You're not in a fight. No bosses are in yet.",
                "No boss 'ezekiel'. ::raid lists them",
                "guardian's phases: 1 flying, 2 grounded",
                "The party size must be 1 to 8",
                "The seed must be a whole number",
                "Usage: ::raid [boss [phase=name|n] [party=n] [seed=n]] | ::raid log [lines] | ::raid digest [ticks] | ::raid stop | ::raid run | ::raid tp <nexus|room> | ::raid clear | ::raid hold [ticks]",
                "You're not in a fight.",
                "You're not in a fight.",
            ),
            replies,
        )
        assertEquals(0, world.npcs.count)
    }

    @Test
    fun `a hold keeps the boss standing for the ticks asked, this tick the first, and a fight can name whom it holds`() {
        val world = world(def)
        world.run("raid guardian")
        val boss = world.npcs.all.single()
        replies.clear()
        world.run("raid hold 1")
        world.run("raid hold 1")
        assertEquals(listOf("Held Archive Guardian for 1 ticks.", "Archive Guardian can't be held now: held already, or not long after a hold."), replies)
        // A command runs before the tick counts freezes down (World.tick): it still stands in this tick's NPC phase,
        // and is free in the next.
        world.npcs.countDownFreezes()
        assertEquals(1, boss.freezeTicks)
        world.npcs.countDownFreezes()
        assertEquals(0, boss.freezeTicks)
        world.run("raid stop")
        // A fight whose boss is only its anchor names the one that walks; with none left, there is nothing to hold.
        var walker: osrsps.game.npc.Npc? = null
        val anchored = EncounterDef("anchored", guardian, listOf(PhaseDef("open", onStart = { walker = spawnAdd(guardian, boss.x + 6, boss.z) })), held = { listOfNotNull(walker) })
        val other = world(anchored)
        other.run("raid anchored")
        replies.clear()
        other.run("raid hold 3")
        assertEquals(listOf("Held Archive Guardian for 3 ticks."), replies)
        assertEquals(listOf(4, 0), listOf(assertNotNull(walker).freezeTicks, other.npcs.all.first { it !== walker }.freezeTicks))
        walker = null
        other.run("raid hold 3")
        assertEquals("Nothing of anchored is left to hold.", replies.last())
    }

    @Test
    fun `a first step with no delay runs on the tick the fight starts`() {
        val quick = EncounterDef("quick", guardian, listOf(PhaseDef("open", cycle = listOf(CycleStep(0) { log("first") }, CycleStep(3) { log("second") }))))
        val world = world(quick)
        world.run("raid quick")
        replies.clear()
        world.run("raid log")
        assertEquals(listOf("0 open: phase starts", "0 open: first"), replies)
    }

    @Test
    fun `a reset ends the test fight the player started, so a script that stopped part way leaves none behind`() {
        // Nightly 2026-11-08: archive-guardian-hazards failed with its fight still going on the login tile, and the
        // Guardian killed the account there again and again through every script after it.
        val world = world(def)
        player.beforeScenario = world.snapshot(player)
        world.run("raid guardian")
        world.run("reset")
        assertEquals(0, world.npcs.count, "the boss went with the reset")
        assertEquals(emptyList(), world.encounters.all)
    }

    @Test
    fun `a fight whose boss leaves in its first step ends there`() {
        lateinit var world: World
        val vanish = EncounterDef("vanish", guardian, listOf(PhaseDef("open", cycle = listOf(CycleStep(0) { world.removeNpc(boss) }, CycleStep(3) {}))))
        world = world(vanish)
        world.run("raid vanish")
        replies.clear()
        world.run("raid")
        assertEquals(listOf("You're not in a fight. Bosses: vanish"), replies)
        assertEquals("0 open: ends: boss gone", world.encounters.all.single().lines.last())
    }

    @Test
    fun `a digest is refused once the log has let go of its first lines`() {
        val noisy = EncounterDef("noisy", guardian, listOf(PhaseDef("open", onStart = { repeat(250) { log("line $it") } })))
        val world = world(noisy)
        world.run("raid noisy")
        replies.clear()
        world.run("raid digest 0")
        assertEquals(listOf("The log has let go of its first 51 lines, so it can't be summed up from the start"), replies)
    }
}
