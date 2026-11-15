package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.PlayerOps
import osrsps.game.SeqNames
import osrsps.game.SpotanimNames
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.AttackStance
import osrsps.game.combat.AttackStyles
import osrsps.game.combat.CombatMath
import osrsps.game.combat.MagicCombat
import osrsps.game.combat.PvpCombat
import osrsps.game.combat.PvpLines
import osrsps.game.combat.RangedWeapons
import osrsps.game.combat.Spells
import osrsps.game.dialogue.DialogueText
import osrsps.map.CollisionMap

/**
 * Wilderness step W1.4 through two headless clients, with the real ranged weapons and spells files: a shortbow shooting
 * a player from where the shooter stands, the arrow used and its damage and XP; Fire Strike cast once from the spellbook
 * with its runes; and Ice Barrage freezing the target, who can't walk off until the caster is more than 10 tiles away.
 */
class HeadlessPvpRangedMagicTest {
    // As HeadlessRangedTest and HeadlessMagicTest have them, shaped like the 241 cache's.
    private val shortbow = ObjType(SHORTBOW, "Shortbow", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, params = mapOf(4 to 8, 14 to 4), internalName = "shortbow", category = 64)
    private val arrows = ObjType(BRONZE_ARROW, "Bronze arrow", stackable = true, tradeable = true, iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.AMMO, params = mapOf(12 to 7), internalName = "bronze_arrow")
    private val fireStrike = ObjType(
        3279, "Fire Strike", internalName = "13_fire_strike",
        params = mapOf(601 to "Fire Strike", 604 to 13, 596 to (218 shl 16 or FIRE_STRIKE), 365 to MIND, 366 to 1, 367 to FIRE, 368 to 3, 369 to AIR, 370 to 2, 1935 to 8),
    )
    private val barrage = ObjType(
        4651, "Ice Barrage", internalName = "94_ice_barrage",
        params = mapOf(601 to "Ice Barrage", 604 to 94, 596 to (218 shl 16 or ICE_BARRAGE), 336 to 1, 365 to WATER, 366 to 6, 367 to BLOOD, 368 to 2, 369 to DEATH, 370 to 4),
    )
    private val runes = listOf(
        Triple(AIR, "Air rune", "airrune"), Triple(FIRE, "Fire rune", "firerune"), Triple(MIND, "Mind rune", "mindrune"),
        Triple(WATER, "Water rune", "waterrune"), Triple(BLOOD, "Blood rune", "bloodrune"), Triple(DEATH, "Death rune", "deathrune"),
    ).map { (id, name, internal) -> ObjType(id, name, stackable = true, iops = listOf(null, null, null, null, "Drop"), internalName = internal) }

    private val styles = AttackStyles.fromRows(
        listOf(
            listOf<Any>(0) to listOf<Any>(
                0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)", 0,
                1, "Kick", "(Aggressive)<br>(Crush)<br>(Strength XP)", 0,
                3, "Block", "(Defensive)<br>(Crush)<br>(Defence XP)", 0,
            ),
            listOf<Any>(3) to listOf<Any>(
                0, "Accurate", "(Accurate)<br>(Standard)<br>(Ranged XP)", 0,
                1, "Rapid", "(Rapid)<br>(Standard)<br>(Ranged XP)", 0,
                3, "Longrange", "(Longrange)<br>(Standard)<br>(Ranged XP)<br>(Defence XP)", 0,
            ),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            for (sz in (3456 shr 6)..(3967 shr 6)) markLoaded(3090 shr 6, sz)
        }
        val objs = arrayOfNulls<ObjType>(barrage.id + 1)
        for (type in runes + listOf(shortbow, arrows, fireStrike, barrage)) objs[type.id] = type
        val objTypes = ObjTypes(objs)
        World(
            crcs, map, random = GameRandom(9), objTypes = objTypes,
            varTypes = VarTypes(6_000, mapOf(VARBIT_WEAPON_CATEGORY to VarbitType(VARP_WEAPON_CATEGORY, 0, 5))),
            attackStyles = styles,
            rangedWeapons = RangedWeapons.load(objTypes, SeqNames.NONE, SpotanimNames.NONE),
            spells = Spells.load(objTypes, SeqNames.NONE, SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.hitpoints() = stat(HITPOINTS)?.level

    private fun HeadlessClient.carried(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.give(id: Int, count: Int) {
        val before = carried(id)
        command("item $id $count")
        await("$count of item $id") { carried(id).takeIf { it == before + count } }
    }

    private fun HeadlessClient.itemOp(id: Int, iop: Int) {
        val slot = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }
        ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[iop - 1], slot, id)
    }

    /** [a] and [b] five tiles apart at level 36, each in view of the other, with 99 Hitpoints for [b]. */
    private fun pair(a: HeadlessClient, b: HeadlessClient) {
        a.await("the login rebuild") { a.position }
        b.await("the login rebuild") { b.position }
        b.commandReply("setlevel hitpoints 99") { true }
        b.await("hitpoints at 99") { Unit.takeIf { b.hitpoints() == 99 } }
        // Auto retaliate off: the target stands still, so the distances stay as set.
        b.command("varp 172 1")
        a.tele(3090, Z)
        b.tele(3095, Z)
        a.await("Attack given") { Unit.takeIf { a.playerOps()[PlayerOps.ATTACK] == PlayerOps.ATTACK_TEXT } }
        a.await("the target in view") { a.otherPlayers().takeIf { it.isNotEmpty() } }
    }

    @Test
    fun `a shortbow shoots a player from where the shooter stands, using an arrow`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("quin").use { a ->
                server.login("rosa").use { b ->
                    a.await("the login rebuild") { a.position }
                    a.commandReply("setlevel ranged 99") { true }
                    a.command("item $SHORTBOW")
                    a.itemOp(SHORTBOW, WIELD)
                    a.command("item $BRONZE_ARROW $ARROWS")
                    a.itemOp(BRONZE_ARROW, WIELD)
                    a.await("the arrows worn") { a.inventory(WORN).getOrNull(WearPos.AMMO)?.takeIf { it.count == ARROWS } }
                    a.commandReply("sure max") { it.startsWith("Your attacks") }
                    pair(a, b)
                    val max = CombatMath.playerRangedMaxHit(99, AttackStance.ACCURATE, 7)
                    assertEquals(12, max)
                    val rangedXp = a.stat(RANGED)!!.xp

                    a.playerOp(b.index, PlayerOps.ATTACK)
                    b.await("the first arrow") { Unit.takeIf { b.hitpoints() == 99 - max } }
                    assertEquals(Coord(3090, Z, 0), a.position, "five tiles off is in range")
                    a.await("the Ranged XP") { Unit.takeIf { a.stat(RANGED)!!.xp == rangedXp + 4 * max } }
                    assertTrue(a.inventory(WORN)[WearPos.AMMO]!!.count < ARROWS)
                }
            }
        }
    }

    @Test
    fun `Fire Strike from the spellbook is cast once, with its runes`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("saul").use { a ->
                server.login("tess").use { b ->
                    a.await("the login rebuild") { a.position }
                    a.commandReply("setlevel magic 13") { true }
                    a.give(MIND, 10)
                    a.give(FIRE, 30)
                    a.give(AIR, 20)
                    a.commandReply("sure max") { it.startsWith("Your attacks") }
                    pair(a, b)

                    a.playerTarget(b.index, Spells.SPELLBOOK, FIRE_STRIKE)
                    b.await("the strike") { Unit.takeIf { b.hitpoints() == 99 - 8 } }
                    assertEquals(Coord(3090, Z, 0), a.position)
                    a.awaitTicks(MagicCombat.SPEED * 2)
                    assertEquals(listOf(9, 27, 18), listOf(a.carried(MIND), a.carried(FIRE), a.carried(AIR)), "one cast's runes")
                    assertEquals(99 - 8, b.hitpoints(), "cast once")
                }
            }
        }
    }

    @Test
    fun `Ice Barrage freezes the target until the caster is more than 10 tiles off`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ugo").use { a ->
                server.login("vera").use { b ->
                    a.await("the login rebuild") { a.position }
                    a.commandReply("setlevel magic 94") { true }
                    a.commandReply("spellbook ancient") { it == "Your spellbook is ancient now." }
                    a.give(WATER, 6)
                    a.give(BLOOD, 2)
                    a.give(DEATH, 4)
                    a.commandReply("sure") { it.startsWith("Your attacks") }
                    pair(a, b)
                    val vera = server.world.playerNamed("vera")!!

                    a.playerTarget(b.index, Spells.SPELLBOOK, ICE_BARRAGE)
                    b.await("the freeze message") { b.messages().firstOrNull { it.text == DialogueText.NONE[PvpLines.FROZEN] } }
                    assertTrue(vera.freezeTicks > 0)
                    b.walk(3095, Z + 5)
                    b.awaitTicks(3)
                    assertEquals(Coord(3095, Z, 0), b.position, "frozen in place")

                    a.tele(3090 - 11, Z)
                    b.await("the freeze broken") { Unit.takeIf { vera.freezeTicks == 0 } }
                    assertTrue(vera.freezeImmunityTicks in 1..PvpCombat.FREEZE_IMMUNITY_TICKS, "immune for 5 ticks after")
                    b.walk(3095, Z + 5)
                    b.await("walking again") { b.position?.takeIf { it.z > Z } }
                }
            }
        }
    }

    private companion object {
        const val Z = 3800
        const val SHORTBOW = 841
        const val BRONZE_ARROW = 882
        const val ARROWS = 100
        const val FIRE_STRIKE = 19
        const val ICE_BARRAGE = 87
        const val AIR = 556
        const val FIRE = 554
        const val MIND = 558
        const val WATER = 555
        const val BLOOD = 565
        const val DEATH = 560
        const val HITPOINTS = 3
        const val RANGED = 4

        // As HeadlessEquipmentTest has them.
        const val BACKPACK = 93
        const val WORN = 94
        const val WIELD = 2
        val IOP_BUTTON_OPS = intArrayOf(2, 3, 4, 6, 7)
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val VARBIT_WEAPON_CATEGORY = 357
        const val VARP_WEAPON_CATEGORY = 843
    }
}
