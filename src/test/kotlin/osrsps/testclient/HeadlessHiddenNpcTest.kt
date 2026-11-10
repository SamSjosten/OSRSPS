package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * An NPC hidden while it stays in the world ([World.hide], by hand with ::hidenpc): what a boss fight needs for a boss
 * that is gone for a while and comes back under the same index, with its fight still going (Dreadnoth, split into
 * jellies). ::pause, ::tele and ::god only make the timing exact.
 */
class HeadlessHiddenNpcTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 99))
    private val guard = NpcType(3010, "Guard", ops = listOf(null, "Attack", null, null, null), combatLevel = 21, internalName = "guard", stats = NpcStats(hitpoints = 22))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man, guard)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(MAN_X shr 6, MAN_Z shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(
                NpcSpawn(man, MAN_X, MAN_Z, source = "test"),
                // Just east of the man, to ask whether his tile is free to step onto.
                NpcSpawn(guard, MAN_X + 1, MAN_Z, source = "test"),
            ),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    private fun World.npc(type: NpcType): Npc = npcs.all.first { it.type.id == type.id }

    private fun HeadlessClient.sees(npc: Npc): Boolean = npcs().any { it.index == npc.index }

    /** Attacks [npc] and steps till it has turned on the player, 30 ticks at most. */
    private fun HeadlessClient.fight(npc: Npc, ticks: Int = 30): Boolean {
        npcOp(npc.index, 2)
        repeat(ticks) {
            stepTicks(1)
            if (npc.target != null) return true
        }
        return false
    }

    @Test
    fun `a hidden NPC leaves the client's view, its tile and its fight, can't be clicked or hurt, and comes back as it was`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("god") { it.startsWith("You can") }
                alice.commandReply("pause") { it.startsWith("Paused after tick") }
                val world = server.world
                val npc = world.npc(man)
                val other = world.npc(guard)
                val player = assertNotNull(world.playerNamed("alice"))
                alice.commandReply("tele ${MAN_X - 1} $MAN_Z") { it.startsWith("Teleported") }
                alice.stepTicks(1)
                alice.await("the man in view") { alice.sees(npc).takeIf { it } }
                assertFalse(world.npcs.canStep(other, -1, 0), "the man's tile is his")
                assertTrue(alice.fight(npc), "the man turns on the player attacking him")
                assertSame(player, npc.target)
                alice.commandReply("hit 10 ${npc.index}") { it.startsWith("Hit Man") }
                val hitpoints = npc.hitpoints
                assertTrue(world.meleeCombat.fightingAnother(player, other), "fighting the man, the player is his in single-way combat")

                assertEquals("Hid Man #${npc.index}.", alice.commandReply("hidenpc ${npc.index}") { it.startsWith("Hid") })
                alice.stepTicks(2)
                assertTrue(npc.hidden)
                assertSame(npc, world.npcs[npc.index], "he keeps his place in the world")
                assertFalse(alice.sees(npc), "the client is no longer shown him: ${alice.npcs()}")
                assertNull(npc.target, "he fights no one")
                assertNull(player.interaction, "the player's fight with him is over")
                assertTrue(world.npcs.canStep(other, -1, 0), "his tile is free")
                assertFalse(world.meleeCombat.fightingAnother(player, other), "the player is free for another monster to fight")
                // A boss fight's own swing for him does nothing either.
                val nextAttack = npc.nextAttackTick
                world.meleeCombat.swingAt(npc, player, world.tick)
                assertEquals(nextAttack, npc.nextAttackTick, "hidden, he swings at no one")
                assertTrue(alice.commandReply("npcs") { it.startsWith("#${npc.index} ") }.contains("; hidden"), "::npcs says so")

                // A click on him now is one the client couldn't have sent: nothing comes of it, nor of anything else.
                assertFalse(alice.fight(npc, ticks = 8), "a forged Attack on him does nothing")
                assertNull(player.interaction)
                assertEquals("Man #${npc.index} is hidden", alice.commandReply("hit 10 ${npc.index}") { it.startsWith("Man") })
                assertEquals("Man #${npc.index} is hidden", alice.commandReply("kill ${npc.index}") { it.startsWith("Man") })
                assertEquals(0, world.meleeCombat.statusHit(npc, 5, 0), "poison and the like don't hurt him")
                assertTrue(hitpoints <= npc.hitpoints && !npc.dead, "he wasn't hurt meanwhile: ${npc.hitpoints}, was $hitpoints")
                assertEquals(MAN_X to MAN_Z, npc.x to npc.z, "he stands where he was hidden")

                val back = alice.commandReply("hidenpc ${npc.index}") { it.startsWith("Man") }
                assertEquals("Man #${npc.index} is back at $MAN_X, $MAN_Z.", back)
                alice.stepTicks(2)
                assertFalse(npc.hidden)
                val seen = alice.await("the man in view again") { alice.npcs().firstOrNull { it.index == npc.index } }
                assertEquals(Coord(MAN_X, MAN_Z), seen.coord)
                assertEquals(man.id, seen.id)
                assertFalse(world.npcs.canStep(other, -1, 0), "his tile is his again")
                assertTrue(alice.fight(npc), "he can be fought again")
                assertSame(player, npc.target)
            }
        }
    }

    /** The world hands a removed NPC's index, and RSProt its avatar, to the next one put in: neither may stay hidden. */
    @Test
    fun `an NPC put in after a hidden one was removed is shown`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("pause") { it.startsWith("Paused after tick") }
                val world = server.world
                val npc = world.npc(man)
                alice.commandReply("tele ${MAN_X - 1} $MAN_Z") { it.startsWith("Teleported") }
                alice.stepTicks(1)
                alice.await("the man in view") { alice.sees(npc).takeIf { it } }
                alice.commandReply("hidenpc ${npc.index}") { it.startsWith("Hid") }
                alice.stepTicks(2)
                assertFalse(alice.sees(npc))
                alice.commandReply("delnpc ${npc.index}") { it.startsWith("Removed") }
                alice.stepTicks(2)
                val spawned = alice.commandReply("npc man") { it.startsWith("Spawned") }
                val index = spawned.substringAfter("#").substringBefore(" ").toInt()
                assertEquals(npc.index, index, "the new man takes the freed index: $spawned")
                alice.stepTicks(2)
                val seen = alice.await("the new man in view") { alice.npcs().firstOrNull { it.index == index } }
                assertEquals(Coord(MAN_X - 1, MAN_Z), seen.coord)
                assertFalse(assertNotNull(world.npcs[index]).hidden)
            }
        }
    }

    private companion object {
        const val MAN_X = 3231
        const val MAN_Z = 3218
    }
}
