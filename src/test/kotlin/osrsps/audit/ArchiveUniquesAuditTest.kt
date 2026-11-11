package osrsps.audit

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import osrsps.cache.GameFrameCheck
import osrsps.cache.ObjTypeLoader
import osrsps.game.EquipmentBonuses
import osrsps.game.ObjType
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.SpotanimNames
import osrsps.game.WearPos
import osrsps.game.WearRequirement
import osrsps.game.WearRequirements
import osrsps.game.Weapons
import osrsps.game.combat.AmmoKind
import osrsps.game.combat.RangedWeapons
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Raid step 30: the Fractured Archive's uniques as the 241 cache has them, against the OSRS Wiki's item pages (Ascension
 * crossbows, Obligator, Rondache, Zorya's tome, Horn of Bran; read 2026-11-10). The server reads a worn item's bonuses,
 * slots and speed from the cache, so this is what a player gets when they wear one: the numbers below are the Wiki's
 * infoboxes. Each unique also has its requirement (item-requirements.tsv), its combat tab and, for the crossbows, the
 * bolts they fire. Runs only where the cache is (OSRSPS_FLATCACHE, a checkout of abextm/osrs-cache at a rev 241 tag), so
 * not in CI.
 */
class ArchiveUniquesAuditTest {
    /** The Wiki's bonuses, in [EquipmentBonuses.PARAMS]' order: attack, defence, then strength, ranged strength, magic damage in tenths, prayer. */
    private data class Wiki(val name: String, val slot: Int, val twoHanded: Boolean, val speed: Int?, val requirement: WearRequirement, val bonuses: List<Int>)

    private val wiki = mapOf(
        "ascension_crossbows" to Wiki("Ascension crossbows", WearPos.WEAPON, true, 3, WearRequirement(Skill.RANGED, 80), listOf(0, 0, 0, 3, 34, -7, -5, 0, 10, 0, 0, 0, 0, 0)),
        "obligator" to Wiki("Obligator", WearPos.WEAPON, true, 5, WearRequirement(Skill.ATTACK, 85), listOf(22, -8, 120, 0, 0, 0, 16, 0, 3, 8, 40, 0, 0, 3)),
        "obligator_uncharged" to Wiki("Obligator (uncharged)", WearPos.WEAPON, true, 5, WearRequirement(Skill.ATTACK, 85), listOf(22, -8, 120, 0, 0, 0, 16, 0, 3, 8, 40, 0, 0, 3)),
        "rondache" to Wiki("Rondache", WearPos.SHIELD, false, null, WearRequirement(Skill.DEFENCE, 80), listOf(0, 0, 0, -5, -8, 35, 41, 38, -6, 55, 8, 0, 0, 0)),
        "zoryas_tome" to Wiki("Zorya's tome", WearPos.WEAPON, true, 3, WearRequirement(Skill.MAGIC, 85), listOf(0, 0, 0, 45, 0, 1, 5, 5, 10, 0, 0, 0, 150, 2)),
        // The Wiki gives the uncharged tome the charged one's +2 prayer; the cache gives it none, and the cache is what
        // the game reads. The one difference between the two.
        "zoryas_tome_uncharged" to Wiki("Zorya's tome (uncharged)", WearPos.WEAPON, true, 3, WearRequirement(Skill.MAGIC, 85), listOf(0, 0, 0, 45, 0, 1, 5, 5, 10, 0, 0, 0, 150, 0)),
    )

    @Test
    @EnabledIfEnvironmentVariable(named = "OSRSPS_FLATCACHE", matches = ".+")
    fun `the uniques' stats in the cache are the Wiki's, and each has its requirement, combat tab and ammunition`() {
        val cache = FlatCache(Path(System.getenv("OSRSPS_FLATCACHE")))
        val names = cache.group(GAMEVAL_INDEX, GAMEVAL_OBJS)
        val objs = ObjTypeLoader.link(
            cache.group(CONFIG_INDEX, OBJ_GROUP).mapValues { (id, data) -> ObjTypeLoader.decode(id, data, names[id]?.let { String(it, Charsets.ISO_8859_1) }) },
        )
        val requirements = WearRequirements.load(objs)
        for ((name, expected) in wiki) {
            val type = assertNotNull(objs.find(name), name)
            assertEquals(expected.name, type.name)
            assertEquals(expected.slot, type.wearpos1, "$name's slot")
            assertEquals(expected.twoHanded, type.twoHanded, "$name two-handed")
            assertEquals(expected.bonuses, bonuses(type), "$name's bonuses")
            expected.speed?.let { assertEquals(it, type.intParam(PARAM_ATTACK_SPEED), "$name's speed") }
            assertEquals(listOf(expected.requirement), requirements[type], "$name's requirement")
        }
        assertEquals(Weapons.CROSSBOW, Weapons.category(objs.find("ascension_crossbows")))
        for (name in listOf("obligator", "obligator_uncharged")) assertEquals(Weapons.EXTENDING_CHAINMACE, Weapons.category(objs.find(name)), name)
        for (name in listOf("zoryas_tome", "zoryas_tome_uncharged")) assertEquals(Weapons.POWERED_BOOK, Weapons.category(objs.find(name)), name)

        // The Horn of Bran isn't worn: Inspect only (OSRS Wiki, Horn of Bran).
        val horn = assertNotNull(objs.find("tfa_zeal_reward"))
        assertEquals("Horn of Bran", horn.name)
        assertFalse(horn.wearable)

        // Ascension bolts: ranged strength 53, the gem-tipped 58 (241 cache), all needing 80 Ranged; the crossbows fire
        // them and nothing else, and no other crossbow fires them.
        val weapons = RangedWeapons.load(objs, SeqNames(gamevals(cache, GAMEVAL_SEQS)), SpotanimNames(gamevals(cache, GAMEVAL_SPOTANIMS)))
        val crossbows = assertNotNull(weapons.weapon(objs.find("ascension_crossbows")))
        assertEquals(AmmoKind.ASCENSION_BOLTS, crossbows.kind)
        assertEquals(6, crossbows.range)
        val bolts = listOf("ascension_bolts", "ascension_bolts_diamond_unenchanted", "ascension_bolts_diamond_enchanted", "ascension_bolts_onyx_unenchanted", "ascension_bolts_onyx_enchanted")
        for (name in bolts) {
            val type = assertNotNull(objs.find(name), name)
            assertTrue(crossbows.fires(assertNotNull(weapons.ammo(type), name)), name)
            assertEquals(WearPos.AMMO, type.wearpos1, name)
            assertEquals(listOf(WearRequirement(Skill.RANGED, 80)), requirements[type], name)
        }
        assertEquals(53, bonuses(objs.find("ascension_bolts")!!)[EquipmentBonuses.RANGED_STRENGTH])
        assertEquals(58, bonuses(objs.find("ascension_bolts_onyx_enchanted")!!)[EquipmentBonuses.RANGED_STRENGTH])
        assertNull(weapons.ammo(objs.find("xbows_crossbow_bolts_runite"))?.takeIf { crossbows.fires(it) })
        assertFalse(weapons.weapon(objs.find("acb"))!!.fires(weapons.ammo(objs.find("ascension_bolts"))!!))
    }

    private fun bonuses(type: ObjType): List<Int> = EquipmentBonuses.PARAMS.withIndex().map { (index, param) ->
        type.intParam(param) + if (index == EquipmentBonuses.RANGED_STRENGTH) type.intParam(EquipmentBonuses.PARAM_GEAR_RANGED_STRENGTH) else 0
    }

    private fun gamevals(cache: FlatCache, group: Int): Map<String, Int> =
        cache.group(GAMEVAL_INDEX, group).entries.associate { (id, data) -> GameFrameCheck.decodeName(data) to id }

    private companion object {
        const val CONFIG_INDEX = 2
        const val OBJ_GROUP = 10
        const val GAMEVAL_INDEX = 24
        const val GAMEVAL_OBJS = 0
        const val GAMEVAL_SEQS = 7
        const val GAMEVAL_SPOTANIMS = 8
        const val PARAM_ATTACK_SPEED = 14
    }
}
