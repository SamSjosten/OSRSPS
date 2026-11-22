package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.PoweredStaves
import osrsps.game.content.HistorianAldoLines
import osrsps.game.content.RatBoneLines
import osrsps.game.content.RatBoneWeapons
import osrsps.game.content.Scurrius
import osrsps.game.content.ScurriusFight
import osrsps.map.CollisionMap

/**
 * Scurrius step 4 through the headless client: the bone shortbow and bone staff made by hand, a bone mace from Historian
 * Aldo for his fee, a combat lamp for a spine and its XP, and the weapons' rules: rats only, +10 against them, no
 * cooldown on Scurrius's giant rats.
 */
class HeadlessRatBoneWeaponsTest {
    private val aldo = NpcType(Scurrius.ALDO_TRADE, "Historian Aldo", ops = listOf("Talk-to", null, "Trade", null, null), internalName = "rat_boss_biologist_op2")
    private val rat = NpcType(
        ScurriusFight.GIANT_RAT, "Giant rat", size = 2, ops = listOf(null, "Attack", null, null, null), combatLevel = 46, internalName = "rat_boss_giant_rat",
        stats = NpcStats(attack = 100, defence = 10, strength = 25, hitpoints = 15, ranged = 1, magic = 1),
    )
    private val angry = NpcType(7860, "Angry giant rat", ops = listOf(null, "Attack", null, null, null), combatLevel = 45, internalName = "soulbane_rat", stats = NpcStats(hitpoints = 40))
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", null, null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    private val objs = listOf(
        ObjType(RatBoneWeapons.SPINE, "Scurrius' spine", internalName = "rat_boss_spine"),
        ObjType(RatBoneWeapons.KNIFE, "Knife", internalName = "knife"),
        ObjType(RatBoneWeapons.CHISEL, "Chisel", internalName = "chisel"),
        ObjType(RatBoneWeapons.YEW_SHORTBOW, "Yew shortbow", wearpos1 = WearPos.WEAPON, internalName = "yew_shortbow"),
        ObjType(RatBoneWeapons.RUNE_MACE, "Rune mace", wearpos1 = WearPos.WEAPON, internalName = "rune_mace"),
        ObjType(RatBoneWeapons.BATTLESTAFF, "Battlestaff", wearpos1 = WearPos.WEAPON, internalName = "battlestaff"),
        ObjType(RatBoneWeapons.CHAOS_RUNE, "Chaos rune", stackable = true, internalName = "chaosrune"),
        ObjType(995, "Coins", stackable = true, internalName = "coins"),
        ObjType(RatBoneWeapons.LAMP, "Antique lamp", iops = listOf("Rub", null, null, null, "Destroy"), internalName = "rat_boss_lamp"),
        ObjType(RatBoneWeapons.BONE_MACE, "Bone mace", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, internalName = "rat_bone_mace"),
        ObjType(RatBoneWeapons.BONE_SHORTBOW, "Bone shortbow", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, internalName = "rat_bone_bow"),
        ObjType(RatBoneWeapons.BONE_STAFF, "Bone staff", iops = listOf(null, "Wield", "Check", "Uncharge", null), wearpos1 = WearPos.WEAPON, internalName = "rat_bone_staff"),
    )

    // The powered staff row of dbtable 78, as the 241 cache has it (HeadlessShadowTest's).
    private val styles = osrsps.game.combat.AttackStyles.fromRows(
        listOf(
            listOf<Any>(24) to listOf<Any>(
                0, "Accurate", "(Accurate)<br>(Magic XP)", 0,
                1, "Accurate", "(Accurate)<br>(Magic XP)", 0,
                3, "Longrange", "(Longrange)<br>(Magic XP)<br>(Defence XP)", 0,
            ),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val array = arrayOfNulls<ObjType>(objs.maxOf { it.id } + 1)
        for (type in objs) array[type.id] = type
        val npcTypes = NpcTypes.of(aldo, rat, man, angry)
        World(
            crcs, CollisionMap().apply { markLoaded(3276 shr 6, 9870 shr 6) }, random = GameRandom(3), objTypes = ObjTypes(array),
            npcTypes = npcTypes, npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), attackStyles = styles, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slot(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.useOn(id: Int, target: Int) =
        ifButtonT(INVENTORY, INVENTORY_ITEMS, slot(id), id, INVENTORY, INVENTORY_ITEMS, slot(target), target)

    private fun HeadlessClient.itemOp(id: Int, iop: Int) = ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[iop - 1], slot(id), id)

    private fun HeadlessClient.carried(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = carried(id)
        command("item $id $count")
        await("$count of $id") { if (carried(id) >= before + count) Unit else null }
    }

    private fun HeadlessClient.says(description: String, line: osrsps.game.dialogue.Line) =
        awaitDialogue(description) { it is DialogueView.Chat && it.text.contains(line.key) }

    @Test
    fun `the shortbow and staff made by hand, a mace from Aldo for 50,000, and a combat lamp for a spine`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dana").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 3276 9870 0")
                client.await("by Aldo's spot") { client.position?.takeIf { it == Coord(3276, 9870, 0) } }
                val dana = server.world.playerNamed("dana")!!

                client.give(RatBoneWeapons.SPINE, 3)
                client.give(RatBoneWeapons.KNIFE)
                client.give(RatBoneWeapons.YEW_SHORTBOW)
                client.command("setlevel fletching 35")
                client.await("35 Fletching") { if (dana.skills.currentLevel(Skill.FLETCHING) == 35) Unit else null }
                val fletching = dana.skills.xp(Skill.FLETCHING)
                client.useOn(RatBoneWeapons.KNIFE, RatBoneWeapons.SPINE)
                client.await("the bone shortbow") { if (client.carried(RatBoneWeapons.BONE_SHORTBOW) == 1) Unit else null }
                assertEquals(0, client.carried(RatBoneWeapons.YEW_SHORTBOW))
                assertEquals(300, dana.skills.xp(Skill.FLETCHING) - fletching)

                // The staff wants its 1,000 chaos runes, then takes them as its charges.
                client.give(RatBoneWeapons.CHISEL)
                client.give(RatBoneWeapons.BATTLESTAFF)
                client.command("setlevel crafting 35")
                client.await("35 Crafting") { if (dana.skills.currentLevel(Skill.CRAFTING) == 35) Unit else null }
                client.give(RatBoneWeapons.CHAOS_RUNE, 999)
                client.useOn(RatBoneWeapons.CHISEL, RatBoneWeapons.SPINE)
                client.awaitMessage("short of runes") { it.contains(RatBoneLines.STAFF_RUNES.key) }
                client.give(RatBoneWeapons.CHAOS_RUNE, 1)
                client.useOn(RatBoneWeapons.CHISEL, RatBoneWeapons.SPINE)
                client.await("the bone staff") { if (client.carried(RatBoneWeapons.BONE_STAFF) == 1) Unit else null }
                assertEquals(0, client.carried(RatBoneWeapons.CHAOS_RUNE))
                assertEquals(1_000, dana.charges[PoweredStaves.BONE_STAFF.charge])

                // Aldo makes the mace for his fee, with no Smithing level.
                client.give(RatBoneWeapons.RUNE_MACE)
                client.give(995, 50_000)
                client.commandReply("npc ${Scurrius.ALDO_TRADE}") { it.isNotEmpty() }
                val npc = client.await("Aldo") { server.world.npcs.all.firstOrNull { it.type.id == Scurrius.ALDO_TRADE } }
                client.npcOp(npc.index, Scurrius.TALK_TO)
                client.says("his greeting", HistorianAldoLines.HELLO)
                client.continueDialogue()
                client.awaitDialogue("the menu") { it is DialogueView.Menu && it.options.size == 4 }
                client.chooseOption(3)
                for ((what, line) in listOf("asked" to HistorianAldoLines.ASK_WEAPON, "needs" to HistorianAldoLines.WEAPON_NEEDS, "fee" to HistorianAldoLines.WEAPON_FEE)) {
                    client.says(what, line)
                    client.continueDialogue()
                }
                client.awaitDialogue("which weapon") { it is DialogueView.Menu && it.title.contains(HistorianAldoLines.WEAPON_WHICH.key) && it.options.size == 1 }
                client.chooseOption(1)
                client.says("he'll make it", HistorianAldoLines.WEAPON_OKAY)
                client.continueDialogue()
                client.await("the bone mace") { if (client.carried(RatBoneWeapons.BONE_MACE) == 1) Unit else null }
                assertEquals(0, client.carried(995))
                assertEquals(0, client.carried(RatBoneWeapons.RUNE_MACE))

                // Trade: a spine for a combat lamp, then rubbed for 5,000 Attack XP.
                client.give(RatBoneWeapons.SPINE)
                val attackBefore = dana.skills.xp(Skill.ATTACK)
                client.npcOp(npc.index, Scurrius.TRADE)
                client.awaitDialogue("the lamp asked") { it is DialogueView.Menu && it.options.size == 2 }
                client.chooseOption(1)
                client.await("the lamp") { if (client.carried(RatBoneWeapons.LAMP) == 1) Unit else null }
                assertEquals(0, client.carried(RatBoneWeapons.SPINE))
                client.itemOp(RatBoneWeapons.LAMP, 1)
                client.awaitDialogue("the skills") { it is DialogueView.Menu && it.options.size == 5 }
                client.chooseOption(1)
                client.await("the lamp rubbed") { if (client.carried(RatBoneWeapons.LAMP) == 0) Unit else null }
                assertEquals(5_000, dana.skills.xp(Skill.ATTACK) - attackBefore)

                // With no spine left, Trade asks for one.
                client.npcOp(npc.index, Scurrius.TRADE)
                client.says("a spine wanted", HistorianAldoLines.TRADE_NO_SPINE)
            }
        }
    }

    @Test
    fun `the weapons are for rats alone, hit them 10 harder, and have no cooldown on his giant rats`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                val world = server.world
                val erin = world.playerNamed("erin")!!
                val giant = world.spawnNpc(osrsps.game.npc.NpcSpawn(rat, 3224, 3218, 0, source = "test"))!!
                val human = world.spawnNpc(osrsps.game.npc.NpcSpawn(man, 3226, 3218, 0, source = "test"))!!
                val before = world.combat.matchup(erin, giant)!!.maxHit
                erin.equipment[WearPos.WEAPON] = InvObj(RatBoneWeapons.BONE_MACE, 1)
                assertEquals(RatBoneLines.ONLY_RATS, world.combat.barred(erin, human), "rats only")
                assertNull(world.combat.barred(erin, giant))
                assertEquals(RatBoneLines.ONLY_RATS, world.combat.weaponBarredOnPlayer(erin), "no players either")
                assertEquals(before + RatBoneWeapons.MAX_HIT_BONUS, world.combat.matchup(erin, giant)!!.maxHit, "+10 on a rat")
                assertTrue(world.combat.noCooldown(erin, giant))
                assertTrue(!world.combat.noCooldown(erin, human))
                // Rat-like by name, but not a rat to these weapons (the Wiki's excluded monsters).
                val angryRat = world.spawnNpc(osrsps.game.npc.NpcSpawn(angry, 3228, 3218, 0, source = "test"))!!
                assertEquals(RatBoneLines.ONLY_RATS, world.combat.barred(erin, angryRat), "not an Angry giant rat")
                // The bone staff: its built-in spell, its max hit on a rat the Wiki's floor(Magic / 3) + 5 with the +10
                // counted once, and no XP for a splash.
                erin.equipment[WearPos.WEAPON] = InvObj(RatBoneWeapons.BONE_STAFF, 1)
                assertEquals(osrsps.game.Weapons.POWERED_STAFF, osrsps.game.Weapons.category(world.objTypes[RatBoneWeapons.BONE_STAFF]))
                val spell = world.magicCombat.poweredSpell(erin)
                assertEquals(0, spell?.splashXpTenths, "no splash XP")
                assertTrue(spell!!.maxHit > 0, "a damaging spell even at low Magic, not one that only splashes")
                // A spell with no damage (as Bind) gets no +10: it stays harmless, earning no damage XP.
                assertEquals(0, world.combat.magicMatchup(erin, giant, spell.copy(maxHit = 0, staff = null))!!.maxHit)
                erin.skills.setLevel(Skill.MAGIC, 99)
                assertEquals(38, world.combat.magicMatchup(erin, giant, world.magicCombat.poweredSpell(erin)!!)!!.maxHit, "the Wiki's 38 at 99 Magic")
                erin.equipment[WearPos.WEAPON] = null
                assertNull(world.combat.barred(erin, human), "anything goes without one")
                assertNull(world.combat.weaponBarredOnPlayer(erin))
                // Another staff while the charges can't take its 1,000: nothing taken (Codex, PR 878).
                for ((id, count) in listOf(RatBoneWeapons.SPINE to 1, RatBoneWeapons.BATTLESTAFF to 1, RatBoneWeapons.CHAOS_RUNE to 1_000)) {
                    erin.inventory.add(world.objTypes[id]!!, count)
                }
                erin.charges[osrsps.game.combat.PoweredStaves.BONE_STAFF.charge] = 19_500
                assertTrue(!RatBoneWeapons.make(world, erin, RatBoneWeapons.BONE_STAFF))
                assertEquals(1_000, erin.inventory.count(RatBoneWeapons.CHAOS_RUNE))
                assertEquals(19_500, erin.charges[osrsps.game.combat.PoweredStaves.BONE_STAFF.charge])
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        val IOP_BUTTON_OPS = intArrayOf(2, 3, 4, 6, 7)
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
    }
}
