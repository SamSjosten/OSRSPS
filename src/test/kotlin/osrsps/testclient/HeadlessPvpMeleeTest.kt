package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.PlayerOps
import osrsps.game.World
import osrsps.game.combat.AttackStance
import osrsps.game.combat.CombatMath
import osrsps.game.combat.Prayers
import osrsps.game.combat.PvpInteraction
import osrsps.game.combat.PvpLines
import osrsps.game.combat.PvpCombat
import osrsps.game.dialogue.DialogueText
import osrsps.map.CollisionMap

/**
 * Wilderness step W1.3 through two headless clients: a melee fight between players at level 20, the hit and its XP,
 * Protect from Melee taking 40% off, the target fighting back, and the fight ending when the target leaves the
 * Wilderness.
 */
class HeadlessPvpMeleeTest {
    // Shaped like the cache's prayer obj for Protect from Melee, as HeadlessPrayerTest has it.
    private val protectFromMelee = ObjType(
        20817, "Null", internalName = "placeholder_xmas16_marionette_blue",
        params = mapOf(630 to 14, 1751 to (Prayers.PRAYERBOOK shl 16 or PROTECT_FROM_MELEE), 1752 to "Protect from Melee", 1753 to 43, 1755 to 2676),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            for (sz in (3456 shr 6)..(3967 shr 6)) markLoaded(3090 shr 6, sz)
        }
        val objs = arrayOfNulls<ObjType>(20830)
        objs[protectFromMelee.id] = protectFromMelee
        val objTypes = ObjTypes(objs)
        World(crcs, map, random = GameRandom(7), objTypes = objTypes, prayerList = Prayers.load(objTypes), cycleMillis = millis)
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.hitpoints() = stat(HITPOINTS)?.level

    private fun HeadlessClient.setUp() {
        await("the login rebuild") { position }
        for (skill in listOf("strength", "hitpoints")) commandReply("setlevel $skill 99") { true }
        commandReply("setlevel prayer 43") { true }
        await("hitpoints at 99") { Unit.takeIf { hitpoints() == 99 } }
    }

    @Test
    fun `a sure max punch lands its max, Protect from Melee takes 40% off it, and the target fights back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("mara").use { a ->
                server.login("nils").use { b ->
                    a.setUp()
                    b.setUp()
                    val world = server.world
                    val mara = world.playerNamed("mara")!!
                    val nils = world.playerNamed("nils")!!
                    a.commandReply("sure max") { it.startsWith("Your attacks") }
                    // Level 20, beside each other on the same row.
                    a.tele(3090, 3672)
                    b.tele(3091, 3672)
                    a.await("Attack given") { Unit.takeIf { a.playerOps()[PlayerOps.ATTACK] == PlayerOps.ATTACK_TEXT } }
                    a.await("nils in view") { a.otherPlayers().takeIf { it.isNotEmpty() } }
                    val max = CombatMath.playerMaxHit(99, AttackStance.ACCURATE, 0)
                    assertEquals(11, max)
                    val attackXp = a.stat(ATTACK)!!.xp

                    a.playerOp(b.index, PlayerOps.ATTACK)
                    b.await("the first punch") { Unit.takeIf { b.hitpoints() == 99 - max } }
                    a.await("the Attack XP") { Unit.takeIf { a.stat(ATTACK)!!.xp == attackXp + 4 * max } }
                    // Auto retaliate is on: nils turns on mara.
                    b.await("nils fighting back") { (nils.interaction as? PvpInteraction)?.target }
                    assertSame(mara, (nils.interaction as PvpInteraction).target)

                    // nils prays: the next punch is 60% of the max.
                    b.ifButton(Prayers.PRAYERBOOK, PROTECT_FROM_MELEE, 1)
                    b.await("Protect from Melee on") { Unit.takeIf { b.varp(Prayers.VARP_PRAYERS) == 1 shl 14 } }
                    // A punch swung before the prayer counted may still be landing.
                    b.awaitTicks(2)
                    val before = b.hitpoints()!!
                    b.await("a protected punch") { b.hitpoints()?.takeIf { it < before } }
                    assertEquals(before - PvpCombat.prayed(max), b.hitpoints(), "11 through the prayer is 6")
                }
            }
        }
    }

    @Test
    fun `the fight ends when the target steps out of the Wilderness`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("olga").use { a ->
                server.login("piet").use { b ->
                    a.setUp()
                    b.setUp()
                    b.command("varp 172 1")
                    a.tele(3090, 3530)
                    b.tele(3091, 3530)
                    a.await("piet in view") { a.otherPlayers().takeIf { it.isNotEmpty() } }
                    a.playerOp(b.index, PlayerOps.ATTACK)
                    val olga = server.world.playerNamed("olga")!!
                    a.await("the fight on") { olga.interaction as? PvpInteraction }
                    val refusal = DialogueText.NONE[PvpLines.TARGET_NOT_IN_WILDERNESS]
                    b.tele(3091, 3510)
                    a.await("the fight let go") { a.messages().firstOrNull { it.text == refusal } }
                    a.awaitTicks(2)
                    assertTrue(olga.interaction !is PvpInteraction)
                }
            }
        }
    }

    @Test
    fun `40% of a hit is resisted, rounded down`() {
        assertEquals(6, PvpCombat.prayed(11))
        assertEquals(0, PvpCombat.prayed(1))
        assertEquals(30, PvpCombat.prayed(50))
        assertEquals(0, PvpCombat.prayed(0))
    }

    private companion object {
        const val PROTECT_FROM_MELEE = 23
        const val ATTACK = 0
        const val HITPOINTS = 3
    }
}
