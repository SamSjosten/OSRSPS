package osrsps.game.command

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.World
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
        world.run("raid guardian")
        world.run("raid stop")
        world.run("raid")
        assertEquals(
            listOf(
                "Started guardian #1 on Archive Guardian #1 at 3222, 3218, phase 2 (grounded), 1950 hp for a party of 8, seed 7.",
                "guardian #1: phase 2 (grounded) for 0 of 0 ticks; Archive Guardian 1950/1950 hp; 0 players, 0 adds, 0 hazards",
                "0 grounded: phase starts",
                "guardian to tick 0: 1 lines, digest %08x".format("0 grounded: phase starts".hashCode()),
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
                "Usage: ::raid [boss [phase=name|n] [party=n] [seed=n]] | ::raid log [lines] | ::raid digest [ticks] | ::raid stop",
                "You're not in a fight.",
                "You're not in a fight.",
            ),
            replies,
        )
        assertEquals(0, world.npcs.count)
    }
}
