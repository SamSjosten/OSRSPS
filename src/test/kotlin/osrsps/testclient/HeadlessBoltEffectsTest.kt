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
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.SpotanimNames
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.AttackStyles
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.RangedWeapons
import osrsps.game.npc.MoveRestrict
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap

/**
 * Enchanted bolts in a fight, through the headless client and the real ranged weapons and NPC combat files: ruby bolts
 * (e) from a rune crossbow at a man who can't come to her (he stands in water), until Blood Forfeit fires. The client
 * sees her own hitpoints drop by exactly a tenth (99 to 90), with nothing else able to hit her. The man's side (the
 * effect's graphic and its hitsplat, sent in one block) waits for the client's NPC spotanim reader: today's reader
 * stops at that block, so it can't see the hit that comes with it.
 * The rl-client script test-runner/examples/combat-bolt-effects.json makes the same clicks in the real client.
 */
class HeadlessBoltEffectsTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 5_000))

    // Shaped like the cache's: param 4 ranged attack, 12 ranged strength, 14 attack speed; obj category 37 is crossbows.
    private val crossbow = ObjType(RUNE_CROSSBOW, "Rune crossbow", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, params = mapOf(4 to 90, 14 to 6), internalName = "xbows_crossbow_runite", category = 37)
    private val rubyBolts = ObjType(RUBY_BOLTS_E, "Ruby bolts (e)", stackable = true, tradeable = true, iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.AMMO, params = mapOf(12 to 103), internalName = "xbows_crossbow_bolts_adamantite_tipped_ruby_enchanted")

    // The unarmed and crossbow rows of dbtable 78.
    private val styles = AttackStyles.fromRows(
        listOf(
            listOf<Any>(0) to listOf<Any>(
                0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)", 0,
                1, "Kick", "(Aggressive)<br>(Crush)<br>(Strength XP)", 0,
                3, "Block", "(Defensive)<br>(Crush)<br>(Defence XP)", 0,
            ),
            listOf<Any>(5) to listOf<Any>(
                0, "Accurate", "(Accurate)<br>(Heavy)<br>(Ranged XP)", 0,
                1, "Rapid", "(Rapid)<br>(Heavy)<br>(Ranged XP)", 0,
                3, "Longrange", "(Longrange)<br>(Heavy)<br>(Ranged XP)<br>(Defence XP)", 0,
            ),
        ),
    )

    /** The man on a pond east of the player (x 3225 to 3228), kept there, so he never reaches her to hit back. */
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man)
        val map = CollisionMap().apply {
            markLoaded(3222 shr 6, 3218 shr 6)
            for (x in 3225..3228) for (z in 3215..3221) add(x, z, 0, CollisionFlag.FLOOR)
        }
        val objs = arrayOfNulls<ObjType>(RUBY_BOLTS_E + 1)
        for (type in listOf(crossbow, rubyBolts)) objs[type.id] = type
        val objTypes = ObjTypes(objs)
        World(
            crcs, map, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3226, 3218, restrict = MoveRestrict.BLOCKED, source = "test")),
            varTypes = VarTypes(6_000, mapOf(VARBIT_WEAPON_CATEGORY to VarbitType(VARP_WEAPON_CATEGORY, 0, 5))),
            attackStyles = styles, npcCombat = NpcCombat.load(types, SeqNames.NONE),
            rangedWeapons = RangedWeapons.load(objTypes, SeqNames.NONE, SpotanimNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.itemOp(id: Int, iop: Int) {
        val slot = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }
        ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[iop - 1], slot, id)
    }

    @Test
    fun `ruby bolts (e) take exactly a tenth of the shooter's hitpoints when Blood Forfeit fires`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                client.await("the login rebuild") { client.position }
                client.command("setlevel ranged 99")
                client.command("setlevel hitpoints 99")
                client.await("99 hitpoints") { client.stat(HITPOINTS)?.takeIf { it.level == 99 } }
                client.command("item $RUNE_CROSSBOW")
                client.itemOp(RUNE_CROSSBOW, WIELD)
                client.command("item $RUBY_BOLTS_E $BOLTS")
                client.itemOp(RUBY_BOLTS_E, WIELD)
                client.await("the bolts worn") { client.inventory(WORN).getOrNull(WearPos.AMMO)?.takeIf { it.id == RUBY_BOLTS_E && it.count == BOLTS } }
                client.npcOp(npc.index, 2)
                // The man stands in water and can't come to her, so the only thing that can take her hitpoints is Blood
                // Forfeit's cost: 99 / 10 = 9, seen by the client as her hitpoints stat.
                val hitpoints = client.await("Blood Forfeit's cost", timeoutMillis = 12_000) { client.stat(HITPOINTS)?.level?.takeIf { it < 99 } }
                assertEquals(99 - 9, hitpoints)
                assertTrue(client.npcHits().any { it.index == npc.index }, "her bolts hit the man")
                assertEquals(npc.index, client.npcs().single().index)
            }
        }
    }

    companion object {
        const val RUNE_CROSSBOW = 9185
        const val RUBY_BOLTS_E = 9242
        const val BOLTS = 1000
        private const val HITPOINTS = 3

        // As HeadlessEquipmentTest has them.
        private const val BACKPACK = 93
        private const val WORN = 94
        private const val WIELD = 2
        private val IOP_BUTTON_OPS = intArrayOf(2, 3, 4, 6, 7)
        private const val INVENTORY = 149
        private const val INVENTORY_ITEMS = 0
        private const val VARBIT_WEAPON_CATEGORY = 357
        private const val VARP_WEAPON_CATEGORY = 843
    }
}
