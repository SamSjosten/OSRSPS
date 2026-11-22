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
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.SureHit
import osrsps.game.content.Scurrius
import osrsps.game.content.ScurriusFight
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Scurrius step 2 through the headless client: his fight in a private lair on open ground, from his swipes to the
 * food pile at 80% (the walk, five heals, less taken while eating), the centre at 30%, his bricks and his giant rats,
 * which any hit kills. The seed is fixed, so his picks replay.
 */
class HeadlessScurriusFightTest {
    private val types = LocTypes(
        listOf(LocType(Scurrius.BARS, "Broken bars", ops = listOf("Climb-through (normal)", "Climb-through (private)", "Peek (normal)", null, null), collision = LocCollisionType(width = 1, length = 1)))
            .associateBy { it.id },
    )

    private val scurrius = NpcType(
        Scurrius.BOSS_PRIVATE, "Scurrius", size = 3, ops = listOf(null, "Attack", null, null, null), combatLevel = 200, internalName = "rat_boss_instance",
        stats = NpcStats(attack = 300, defence = 60, strength = 100, hitpoints = 500, ranged = 50, magic = 50),
    )
    private val rat = NpcType(
        ScurriusFight.GIANT_RAT, "Giant rat", size = 2, ops = listOf(null, "Attack", null, null, null), combatLevel = 46, internalName = "rat_boss_giant_rat",
        stats = NpcStats(attack = 100, defence = 10, strength = 25, hitpoints = 15, ranged = 1, magic = 1),
    )

    private fun server(dir: Path, seed: Long = 7) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply { markLoaded(3282 shr 6, 9867 shr 6) }
        val npcTypes = NpcTypes.of(scurrius, rat)
        World(
            crcs, map, random = GameRandom(seed), locTypes = types,
            staticLocs = StaticLocs().apply { add(ZoneLoc(Scurrius.BARS, LocShape.CENTREPIECE_STRAIGHT, 1, 3282, 9867, 0)) },
            npcTypes = npcTypes, npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.goIn(server: TestServer) {
        await("the login rebuild") { position }
        command("tele ${Scurrius.OUTSIDE.x} ${Scurrius.OUTSIDE.z} 0")
        await("outside the bars") { position?.takeIf { it == Coord(Scurrius.OUTSIDE.x, Scurrius.OUTSIDE.z, 0) } }
        commandReply("god") { it.startsWith("You can") }
        locOp(Scurrius.BARS, 3282, 9867, Scurrius.CLIMB_PRIVATE)
    }

    @Test
    fun `he swipes, eats at a food pile from 80 percent, then goes to the centre at 30`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.goIn(server)
                val alice = server.world.playerNamed("alice")!!
                val run = client.await("in a lair of her own") { server.world.scurriusLairs.runOf(alice) }
                val boss = client.await("Scurrius") { server.world.npcs.all.firstOrNull { it.type.id == Scurrius.BOSS_PRIVATE && run.instance.contains(it.x, it.z) } }
                val fight = client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === boss } }
                client.await("his first swipe", timeoutMillis = 30_000) { fight.lines.firstOrNull { it.contains("swipes at alice") } }
                assertEquals(ScurriusFight.Stage.MELEE, ScurriusFight.fight(boss).stage)
                assertTrue(fight.lines.none { it.contains("bolts") || it.contains("fur at") || it.contains("summons") }, "phase one: swipes and bricks only")

                // At 80% he goes to a pile and eats: five heals of 5 for one player.
                boss.hitpoints = 390
                client.await("to a pile") { fight.lines.firstOrNull { it.contains("goes to the food pile") } }
                // A freeze on the way doesn't hold him off the pile (R49B-1): he breaks free every tick he walks.
                server.world.npcs.freeze(boss, 1_000)
                client.await("eating", timeoutMillis = 30_000) { if (ScurriusFight.eating(boss)) Unit else null }
                assertEquals(80, server.world.combat.rolledOn(alice, boss, AttackType.SLASH, 100), "a slight damage reduction while eating")
                val before = boss.hitpoints
                client.await("five heals", timeoutMillis = 40_000) { fight.lines.firstOrNull { it.contains("heals") && it.contains("(5)") } }
                assertTrue(boss.hitpoints >= before, "he healed")
                client.await("fighting again") { if (ScurriusFight.fight(boss).stage == ScurriusFight.Stage.FIGHT) Unit else null }
                assertEquals(100, server.world.combat.rolledOn(alice, boss, AttackType.SLASH, 100), "no reduction once he stops eating")

                // At 30% to the centre, then only shots, bricks and rats.
                boss.hitpoints = 140
                client.await("to the centre") { fight.lines.firstOrNull { it.contains("goes to the centre") } }
                client.await("at the centre", timeoutMillis = 30_000) { if (ScurriusFight.fight(boss).stage == ScurriusFight.Stage.CENTRE) Unit else null }
                val centre = run.inside(ScurriusFight.CENTRE.x, ScurriusFight.CENTRE.z)
                assertEquals(centre.x to centre.z, boss.x to boss.z)
                val mark = fight.lines.size
                client.await("an attack from the centre", timeoutMillis = 30_000) {
                    fight.lines.drop(mark).firstOrNull { it.contains("bolts") || it.contains("fur at") || it.contains("bricks") || it.contains("summons") }
                }
                client.awaitTicks(12)
                assertTrue(fight.lines.drop(mark).none { it.contains("swipes") }, "no swipes in phase three")
            }
        }
    }

    @Test
    fun `his bricks fall where the player stood, and his giant rats die to any hit`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.goIn(server)
                val bob = server.world.playerNamed("bob")!!
                val run = client.await("in a lair of his own") { server.world.scurriusLairs.runOf(bob) }
                val boss = client.await("Scurrius") { server.world.npcs.all.firstOrNull { it.type.id == Scurrius.BOSS_PRIVATE && run.instance.contains(it.x, it.z) } }
                val fight = client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === boss } }
                // In phase three every attack is a shot, bricks or rats, so both come soon.
                boss.hitpoints = 100
                client.await("bricks", timeoutMillis = 90_000) { fight.lines.firstOrNull { it.contains("bricks on") } }
                // The shadow logged just before the first bricks holds Bob's own tile (he stands still all the while).
                val lines = fight.lines.toList()
                val shadow = lines.subList(0, lines.indexOfFirst { it.contains("bricks on") }).last { it.contains("hazard on") }
                val tiles = shadow.substringAfter("hazard on ").substringBefore(" from tick").split(" ")
                assertTrue("${bob.x},${bob.z}" in tiles, "a shadow on Bob's tile: $shadow")
                client.await("giant rats", timeoutMillis = 120_000) { ScurriusFight.rats(boss).takeIf { it.isNotEmpty() } }
                val rat = ScurriusFight.rats(boss).first()
                assertEquals(SureHit.MAX, server.world.combat.sureHit(bob, rat, AttackType.MAGIC), "any style hits one")
                assertEquals(rat.hitpoints, server.world.combat.rolledOn(bob, rat, AttackType.RANGED, 1), "and kills it")
                assertEquals(0, server.world.combat.rolledOn(bob, rat, AttackType.MAGIC, 0), "but a spell with no damage (Bind) does none")
                assertTrue(server.world.multiCombat.at(0, rat.x, rat.z))
                // They stay when he dies, while Bob is still in his lair...
                val rats = ScurriusFight.rats(boss).filter { server.world.npcs[it.index] === it }
                client.commandReply("kill ${boss.index}") { it.startsWith("Killed") }
                client.await("his death") { if (boss.dead) Unit else null }
                client.awaitTicks(2)
                assertEquals(rats.size, ScurriusFight.leftoverRats(server.world).size)
                // ...and go once no one is left in it (R49B-2).
                client.command("tele ${Scurrius.OUTSIDE.x} ${Scurrius.OUTSIDE.z} 0")
                client.await("the rats gone") { if (ScurriusFight.leftoverRats(server.world).isEmpty()) Unit else null }
                assertTrue(rats.none { server.world.npcs[it.index] === it }, "none of them left in the world")
            }
        }
    }
}
