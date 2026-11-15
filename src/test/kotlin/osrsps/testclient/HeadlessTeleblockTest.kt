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
import osrsps.game.World
import osrsps.game.combat.Prayers
import osrsps.game.combat.Spells
import osrsps.game.combat.Teleblock
import osrsps.game.content.CustomLocs
import osrsps.game.content.CustomVars
import osrsps.game.content.HomeTeleport
import osrsps.game.content.Lodestones
import osrsps.game.loc.LocTypes
import osrsps.map.CollisionMap

/**
 * Wilderness step W1.8 through two headless clients: Tele Block cast on a player stops Home Teleport and the lodestones
 * until it runs out, lasting half as long through Protect from Magic, can't be cast again while it lasts, and ends when
 * the blocked player kills the caster.
 */
class HeadlessTeleblockTest {
    // Shaped like the 242 cache's objs, as HeadlessMagicTest and PrayersTest have theirs.
    private val teleBlock = ObjType(
        4643, "Tele Block", internalName = "85_teleport_block",
        params = mapOf(601 to "Tele Block", 604 to 85, 596 to (218 shl 16 or TELE_BLOCK), 336 to 0, 365 to LAW, 366 to 1, 367 to DEATH, 368 to 1, 369 to CHAOS, 370 to 1),
    )
    private val protectFromMagic = ObjType(
        20815, "Null", internalName = "placeholder_xmas16_marionette_unpainted",
        params = mapOf(630 to 12, 1751 to (Prayers.PRAYERBOOK shl 16 or PROTECT_FROM_MAGIC), 1752 to "Protect from Magic", 1753 to 37, 1755 to 2675),
    )
    private val runes = listOf(Triple(LAW, "Law rune", "lawrune"), Triple(DEATH, "Death rune", "deathrune"), Triple(CHAOS, "Chaos rune", "chaosrune")).map { (id, name, internal) ->
        ObjType(id, name, stackable = true, iops = listOf(null, null, null, null, "Drop"), internalName = internal)
    }

    /** The 242 cache's `teleblock_cycles`, bits 0 to 11 of `deadman_teleblock_transmit`. */
    private val varTypes = VarTypes(6_000, mapOf(Teleblock.VARBIT to VarbitType(TELEBLOCK_VARP, 0, 11)))
    private val vars = CustomVars.after(varTypes)

    // A shorter tick than the usual test's, so the 250 ticks of a block run out in a few seconds.
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val map = CollisionMap().apply {
            for (sz in (3456 shr 6)..(3967 shr 6)) markLoaded(3090 shr 6, sz)
            markLoaded(Lodestones.LUMBRIDGE.x shr 6, Lodestones.LUMBRIDGE.z shr 6)
        }
        val objs = arrayOfNulls<ObjType>(protectFromMagic.id + 1)
        for (type in runes + listOf(teleBlock, protectFromMagic)) objs[type.id] = type
        val objTypes = ObjTypes(objs)
        World(
            crcs, map, random = GameRandom(13), objTypes = objTypes, varTypes = varTypes, customVars = vars,
            locTypes = LocTypes.NONE.with(CustomLocs(vars).types), prayerList = Prayers.load(objTypes),
            spells = Spells.load(objTypes, SeqNames.NONE, SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.carried(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    /**
     * [caster] with 85 Magic, runes for two casts and sure hits, and [target] with 99 Hitpoints, Prayer for Protect from
     * Magic and auto retaliate off, five tiles apart at level 20, in view of each other, both showing keys for words.
     */
    private fun pair(caster: HeadlessClient, target: HeadlessClient) {
        for (client in listOf(caster, target)) {
            client.await("the login rebuild") { client.position }
            client.commandReply("keys on") { it == "Dialogue shows keys" }
        }
        caster.commandReply("setlevel magic 85") { true }
        for (rune in listOf(LAW, DEATH, CHAOS)) {
            caster.command("item $rune 2")
            caster.await("two of rune $rune") { Unit.takeIf { caster.carried(rune) == 2 } }
        }
        caster.commandReply("sure") { it.startsWith("Your attacks") }
        target.commandReply("setlevel hitpoints 99") { true }
        target.commandReply("setlevel prayer 43") { true }
        target.command("varp 172 1")
        caster.tele(3090, Z)
        target.tele(3095, Z)
        caster.await("Attack given") { Unit.takeIf { caster.playerOps()[PlayerOps.ATTACK] == PlayerOps.ATTACK_TEXT } }
        caster.await("the target in view") { caster.otherPlayers().takeIf { it.isNotEmpty() } }
    }

    /** Every message [client] got after the first [from], by text. */
    private fun HeadlessClient.messagesAfter(from: Int) = messages().drop(from).map { it.text }

    @Test
    fun `Tele Block stops Home Teleport and the lodestones until it runs out, half as long through Protect from Magic`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("wren").use { a ->
                server.login("xavi").use { b ->
                    pair(a, b)
                    val world = server.world
                    val xavi = world.playerNamed("xavi")!!
                    b.ifButton(Prayers.PRAYERBOOK, PROTECT_FROM_MAGIC, 1)
                    b.await("Protect from Magic on") { Unit.takeIf { b.varp(Prayers.VARP_PRAYERS) == 1 shl 12 } }

                    a.playerTarget(b.index, Spells.SPELLBOOK, TELE_BLOCK)
                    b.await("the Tele Block message") { b.messages().firstOrNull { it.text == "[pvp.teleblocked name=wren]" } }
                    assertTrue(xavi.teleblockTicks in (Teleblock.PROTECTED_TICKS - 2)..Teleblock.PROTECTED_TICKS, "halved by Protect from Magic: ${xavi.teleblockTicks}")
                    b.await("the varbit RuneLite reads") { b.varp(TELEBLOCK_VARP).takeIf { it == Teleblock.PROTECTED_TICKS + Teleblock.VARBIT_OFFSET } }
                    assertEquals(1, a.carried(LAW), "one cast's runes")

                    // Cast again while it lasts: refused, with no runes spent.
                    a.awaitTicks(6)
                    a.playerTarget(b.index, Spells.SPELLBOOK, TELE_BLOCK)
                    a.awaitMessage("the recast refused") { it == "[pvp.teleblock_already]" }
                    assertEquals(1, a.carried(LAW))

                    // Out of combat, so the refusals are the block's and not the fight's.
                    b.awaitTicks(World.LOGOUT_COMBAT_TICKS.toInt() + 1)
                    val before = b.messages().size
                    b.ifButton(Spells.SPELLBOOK, HomeTeleport.COMPONENT, HomeTeleport.CAST)
                    b.await("Home Teleport refused") { Unit.takeIf { "[pvp.teleport_blocked]" in b.messagesAfter(before) } }
                    val beforeLodestone = b.messages().size
                    b.command("lodestone lumbridge")
                    b.await("the lodestone refused") { Unit.takeIf { "[pvp.teleport_blocked]" in b.messagesAfter(beforeLodestone) } }
                    assertEquals(Coord(3095, Z, 0), b.position)

                    // When it runs out, the lodestone takes them home.
                    b.await("the block over", timeoutMillis = 30_000) { Unit.takeIf { xavi.teleblockTicks == 0 } }
                    b.await("the varbit cleared") { b.varp(TELEBLOCK_VARP).takeIf { it == 0 } }
                    b.command("lodestone lumbridge")
                    b.await("the landing in Lumbridge", timeoutMillis = 30_000) { b.position?.takeIf { it.z < 3500 } }
                }
            }
        }
    }

    @Test
    fun `killing the caster ends the Tele Block, and leaving the Wilderness does`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("yuri").use { a ->
                server.login("zara").use { b ->
                    pair(a, b)
                    val zara = server.world.playerNamed("zara")!!
                    a.playerTarget(b.index, Spells.SPELLBOOK, TELE_BLOCK)
                    b.await("the Tele Block message") { b.messages().firstOrNull { it.text == "[pvp.teleblocked name=yuri]" } }
                    assertTrue(zara.teleblockTicks > Teleblock.PROTECTED_TICKS, "the full 5 minutes without Protect from Magic")

                    // The caster on one hitpoint, and one sure punch.
                    a.commandReply("hit 9 me") { it.startsWith("Hit yourself") }
                    b.commandReply("sure max") { it.startsWith("Your attacks") }
                    b.playerOp(a.index, PlayerOps.ATTACK)
                    b.await("the block removed") { b.messages().firstOrNull { it.text == "[pvp.teleblock_removed name=yuri]" } }
                    assertEquals(0, zara.teleblockTicks)
                    b.await("the varbit cleared") { b.varp(TELEBLOCK_VARP).takeIf { it == 0 } }
                }
            }
            server.login("abel").use { a ->
                server.login("bree").use { b ->
                    pair(a, b)
                    val bree = server.world.playerNamed("bree")!!
                    a.playerTarget(b.index, Spells.SPELLBOOK, TELE_BLOCK)
                    b.await("the Tele Block") { Unit.takeIf { bree.teleblockTicks > 0 } }
                    b.tele(3095, 3500)
                    b.await("the block over outside") { Unit.takeIf { bree.teleblockTicks == 0 } }
                }
            }
        }
    }

    @Test
    fun `the block ends for whoever is credited with the caster's death, not the last hitter`(@TempDir dir: Path) {
        // Codex review on #720: the kill that ends a block is the credited one, the most damage (killerOf).
        server(dir).use { server ->
            server.login("cara").use { a ->
                server.login("dov").use { b ->
                    server.login("eli").use { c ->
                        c.await("the login rebuild") { c.position }
                        pair(a, b)
                        val world = server.world
                        val (cara, dov, eli) = listOf("cara", "dov", "eli").map { world.playerNamed(it)!! }
                        a.playerTarget(b.index, Spells.SPELLBOOK, TELE_BLOCK)
                        b.await("the Tele Block") { Unit.takeIf { dov.teleblockTicks > 0 } }

                        // eli dealt the caster the most; dov only lands the last punch, and the block stays.
                        a.commandReply("hit 9 me") { it.startsWith("Hit yourself") }
                        world.pvpCombat.dealtBy(cara, eli, 50, world.tick)
                        b.commandReply("sure max") { it.startsWith("Your attacks") }
                        val before = b.messages().size
                        b.playerOp(a.index, PlayerOps.ATTACK)
                        a.await("the caster's respawn", timeoutMillis = 30_000) { a.position?.takeIf { it.z < 3500 } }
                        assertTrue(dov.teleblockTicks > 0, "the last hitter isn't credited")
                        assertTrue(b.messagesAfter(before).none { it.startsWith("[pvp.teleblock_removed") })
                    }
                }
            }
            server.login("fay").use { a ->
                server.login("gus").use { b ->
                    pair(a, b)
                    val world = server.world
                    val fay = world.playerNamed("fay")!!
                    val gus = world.playerNamed("gus")!!
                    a.playerTarget(b.index, Spells.SPELLBOOK, TELE_BLOCK)
                    b.await("the Tele Block") { Unit.takeIf { gus.teleblockTicks > 0 } }
                    // The caster dies to something else soon after gus's hit: gus is credited, and the block is over.
                    world.pvpCombat.dealtBy(fay, gus, 5, world.tick)
                    a.command("hit 99 me")
                    b.await("the block removed") { b.messages().firstOrNull { it.text == "[pvp.teleblock_removed name=fay]" } }
                    assertEquals(0, gus.teleblockTicks)
                }
            }
        }
    }

    @Test
    fun `a logout ends the block, and the varbit comes back cleared`(@TempDir dir: Path) {
        // Codex review on #720: the save keeps the varbit's backing varp.
        server(dir).use { server ->
            server.login("hal").use { a ->
                server.login("ivy").use { b ->
                    pair(a, b)
                    val ivy = server.world.playerNamed("ivy")!!
                    a.playerTarget(b.index, Spells.SPELLBOOK, TELE_BLOCK)
                    b.await("the varbit set") { b.varp(TELEBLOCK_VARP).takeIf { it == Teleblock.TICKS + Teleblock.VARBIT_OFFSET } }
                    assertTrue(ivy.teleblockTicks > 0)
                    b.awaitTicks(World.LOGOUT_COMBAT_TICKS.toInt() + 1)
                    b.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                    b.await("the logout") { b.packets().firstOrNull { it.prot.name == "LOGOUT" } }
                }
                b@ while (true) {
                    if (server.world.playerNamed("ivy") == null) break@b
                    a.awaitTicks(1)
                }
                server.login("ivy").use { b ->
                    b.await("the login rebuild") { b.position }
                    b.awaitTicks(2)
                    assertEquals(0, b.varp(TELEBLOCK_VARP), "no timer for a block that's over")
                    assertEquals(0, server.world.playerNamed("ivy")!!.teleblockTicks)
                }
            }
        }
    }

    private companion object {
        // Level 20, the deepest teleports work from.
        const val Z = 3672

        /** magic_spellbook:teleport_block in the 242 cache. */
        const val TELE_BLOCK = 75
        const val PROTECT_FROM_MAGIC = 21
        const val LAW = 563
        const val DEATH = 560
        const val CHAOS = 562
        const val TELEBLOCK_VARP = 2741
        const val BACKPACK = 93
    }
}
