package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertSame
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.PlayerOps
import osrsps.game.SeqNames
import osrsps.game.SpotanimNames
import osrsps.game.World
import osrsps.game.combat.CombatLines
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.PvpCombat
import osrsps.game.combat.PvpInteraction
import osrsps.game.combat.PvpLines
import osrsps.game.combat.Spells
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Wilderness step W1.5 through three headless clients: in single-way combat a fight between two players keeps a third
 * off both of them, and the one attacked off anyone else, until the PJ timer runs out; in multi-way combat a third can
 * join; and Ice Barrage freezes a player beside its target in multi-way combat but not in single-way.
 */
class HeadlessPvpSingleWayTest {
    // As HeadlessMagicTest has it, shaped like the 241 cache's.
    private val barrage = ObjType(
        4651, "Ice Barrage", internalName = "94_ice_barrage",
        params = mapOf(601 to "Ice Barrage", 604 to 94, 596 to (218 shl 16 or ICE_BARRAGE), 336 to 1, 365 to WATER, 366 to 6, 367 to BLOOD, 368 to 2, 369 to DEATH, 370 to 4),
    )
    private val runes = listOf(Triple(WATER, "Water rune", "waterrune"), Triple(BLOOD, "Blood rune", "bloodrune"), Triple(DEATH, "Death rune", "deathrune")).map { (id, name, internal) ->
        ObjType(id, name, stackable = true, iops = listOf(null, null, null, null, "Drop"), internalName = internal)
    }

    // As HeadlessMaxHitTest has him: 99 hitpoints, so a fight with him lasts.
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 99))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            for (sz in (3456 shr 6)..(3967 shr 6)) markLoaded(3090 shr 6, sz)
        }
        val objs = arrayOfNulls<ObjType>(barrage.id + 1)
        for (type in runes + barrage) objs[type.id] = type
        val objTypes = ObjTypes(objs)
        val npcTypes = NpcTypes.of(man)
        World(
            crcs, map, random = GameRandom(11), objTypes = objTypes, spells = Spells.load(objTypes, SeqNames.NONE, SpotanimNames.NONE),
            npcTypes = npcTypes, npcSpawns = listOf(NpcSpawn(man, 3090, Z - 6, source = "test")), npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    /** Logged in, at 99 Hitpoints with auto retaliate off, standing at [x], [Z]. */
    private fun HeadlessClient.ready(x: Int) {
        await("the login rebuild") { position }
        commandReply("setlevel hitpoints 99") { true }
        command("varp 172 1")
        tele(x, Z)
        await("Attack given") { Unit.takeIf { playerOps()[PlayerOps.ATTACK] == PlayerOps.ATTACK_TEXT } }
    }

    private fun HeadlessClient.refusedWith(line: Line, action: () -> Unit) {
        val text = DialogueText.NONE[line]
        val before = messages().count { it.text == text }
        action()
        await("the message '$text'") { Unit.takeIf { messages().count { it.text == text } > before } }
    }

    @Test
    fun `in single-way a fight keeps a third player off both, until the PJ timer runs out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("abe").use { a ->
                server.login("bea").use { b ->
                    server.login("cal").use { c ->
                        a.ready(3090)
                        b.ready(3091)
                        c.ready(3093)
                        c.await("both in view") { c.otherPlayers().takeIf { it.size == 2 } }
                        b.await("cal in view") { b.otherPlayers().takeIf { it.size == 2 } }
                        val world = server.world
                        val bea = world.playerNamed("bea")!!
                        val cal = world.playerNamed("cal")!!

                        a.playerOp(b.index, PlayerOps.ATTACK)
                        a.await("abe's first swing") { bea.pjOpponent }
                        c.refusedWith(PvpLines.TARGET_IN_COMBAT) { c.playerOp(b.index, PlayerOps.ATTACK) }
                        c.refusedWith(PvpLines.TARGET_IN_COMBAT) { c.playerOp(a.index, PlayerOps.ATTACK) }
                        b.refusedWith(CombatLines.ALREADY_UNDER_ATTACK) { b.playerOp(c.index, PlayerOps.ATTACK) }

                        // abe leaves the Wilderness, which ends his fight; the timer runs out 20 ticks after his last swing.
                        a.tele(3090, 3500)
                        val until = bea.pjUntil
                        c.await("the PJ timer over") { Unit.takeIf { world.tick >= until } }
                        c.playerOp(b.index, PlayerOps.ATTACK)
                        c.await("cal on bea") { (cal.interaction as? PvpInteraction)?.target?.takeIf { it === bea } }
                    }
                }
            }
        }
    }

    @Test
    fun `in multi-way a third player can join, and a barrage freezes the player beside its target`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dee").use { a ->
                server.login("eli").use { b ->
                    server.login("fay").use { c ->
                        a.ready(3090)
                        b.ready(3093)
                        c.ready(3094)
                        a.await("both in view") { a.otherPlayers().takeIf { it.size == 2 } }
                        val world = server.world
                        val eli = world.playerNamed("eli")!!
                        val fay = world.playerNamed("fay")!!
                        a.commandReply("setlevel magic 94") { true }
                        a.commandReply("spellbook ancient") { it == "Your spellbook is ancient now." }
                        for ((id, count) in listOf(WATER to 12, BLOOD to 4, DEATH to 8)) a.command("item $id $count")
                        a.commandReply("sure") { it.startsWith("Your attacks") }

                        // Single-way: the barrage freezes eli alone.
                        a.playerTarget(b.index, Spells.SPELLBOOK, ICE_BARRAGE)
                        a.await("eli frozen") { Unit.takeIf { eli.freezeTicks > 0 } }
                        a.awaitTicks(2)
                        assertEquals(0, fay.freezeTicks, "fay is beside eli, but combat is single-way")
                        c.refusedWith(PvpLines.TARGET_IN_COMBAT) { c.playerOp(b.index, PlayerOps.ATTACK) }

                        // Multi-way: fay can join on eli, and a barrage on eli freezes fay too.
                        a.commandReply("multi on") { it.startsWith("Combat is multi-way") }
                        c.playerOp(b.index, PlayerOps.ATTACK)
                        c.await("fay on eli") { (fay.interaction as? PvpInteraction)?.target?.takeIf { it === eli } }
                        a.awaitTicks(PvpCombat.FREEZE_IMMUNITY_TICKS)
                        a.playerTarget(b.index, Spells.SPELLBOOK, ICE_BARRAGE)
                        a.await("fay frozen") { Unit.takeIf { fay.freezeTicks > 0 } }
                    }
                }
            }
        }
    }

    @Test
    fun `a player who has just attacked a monster can't be attacked by a player in single-way, before it hits back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gus").use { a ->
                server.login("hal").use { b ->
                    a.ready(3090)
                    b.ready(3092)
                    b.await("gus in view") { b.otherPlayers().takeIf { it.isNotEmpty() } }
                    val gus = server.world.playerNamed("gus")!!
                    val npc = a.await("the man in view") { a.npcs().firstOrNull { it.id == man.id } }.index

                    // Codex review on #710: gus's fight with the man keeps hal off him from gus's first swing, not only
                    // once the man has hit back.
                    a.npcOp(npc, 2)
                    a.await("gus's swing at the man") { gus.attackedNpc }
                    b.refusedWith(PvpLines.TARGET_IN_COMBAT) { b.playerOp(a.index, PlayerOps.ATTACK) }
                }
            }
        }
    }

    @Test
    fun `a caster in single-way can't barrage past their target, and a player a barrage hits gets a PJ timer`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ida").use { a ->
                server.login("jon").use { b ->
                    server.login("kim").use { c ->
                        a.ready(3086)
                        b.ready(3093)
                        c.ready(3094)
                        a.await("both in view") { a.otherPlayers().takeIf { it.size == 2 } }
                        val world = server.world
                        val ida = world.playerNamed("ida")!!
                        val jon = world.playerNamed("jon")!!
                        val kim = world.playerNamed("kim")!!
                        // The zone from 3088 is multi-way; ida stands single-way, west of it.
                        world.multiCombat.set(0, 3093, Z, true)
                        a.commandReply("setlevel magic 94") { true }
                        a.commandReply("spellbook ancient") { it == "Your spellbook is ancient now." }
                        for ((id, count) in listOf(WATER to 12, BLOOD to 4, DEATH to 8)) a.command("item $id $count")
                        a.commandReply("sure") { it.startsWith("Your attacks") }

                        // Codex review on #710: ida's PJ timer holds her to jon, so the barrage leaves kim alone.
                        a.playerTarget(b.index, Spells.SPELLBOOK, ICE_BARRAGE)
                        a.await("jon frozen") { Unit.takeIf { jon.freezeTicks > 0 } }
                        a.awaitTicks(2)
                        assertEquals(0, kim.freezeTicks, "ida stands single-way")

                        // From inside the multi-way zone the barrage takes kim too, and starts her PJ timer naming ida.
                        a.tele(3089, Z)
                        a.await("jon free and no longer immune") { Unit.takeIf { jon.freezeTicks == 0 && jon.freezeImmunityTicks == 0 } }
                        a.playerTarget(b.index, Spells.SPELLBOOK, ICE_BARRAGE)
                        a.await("kim frozen") { Unit.takeIf { kim.freezeTicks > 0 } }
                        assertSame(ida, kim.pjOpponent)
                        assertTrue(kim.pjUntil > world.tick)
                    }
                }
            }
        }
    }

    private companion object {
        // Level 51: the caster's 94 Magic puts them some 45 combat levels over the others.
        const val Z = 3920
        const val ICE_BARRAGE = 87
        const val WATER = 555
        const val BLOOD = 565
        const val DEATH = 560
    }
}
