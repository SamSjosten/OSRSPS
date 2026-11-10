package osrsps.game.skill.magic

import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.combat.Spells

/**
 * The teleport spell objs, tablets, runes, banana and staff of air shaped as the rev 242 cache has them (osrs-dumps
 * 2026-11-03-rev242, config/dump.obj and interface/magic_spellbook.if3), for the teleport tests.
 */
object TeleportObjs {
    const val LAW = 563
    const val AIR = 556
    const val FIRE = 554
    const val EARTH = 557
    const val WATER = 555
    const val BANANA = 1963
    const val STAFF_OF_AIR = 1381

    const val VARROCK = 3286
    const val LUMBRIDGE = 3289
    const val FALADOR = 3292
    const val CAMELOT = 3296
    const val KOUREND = 21836
    const val ARDOUGNE = 3301
    const val FORTIS = 24612
    const val WATCHTOWER = 3306
    const val TROLLHEIM = 3312
    const val APE_ATOLL = 7619

    const val VARROCK_TABLET = 8007
    const val LUMBRIDGE_TABLET = 8008
    const val FALADOR_TABLET = 8009
    const val CAMELOT_TABLET = 8010
    const val ARDOUGNE_TABLET = 8011
    const val WATCHTOWER_TABLET = 8012
    const val KOUREND_TABLET = 28790
    const val FORTIS_TABLET = 28824

    /** The magic tab components (218:n). */
    const val VARROCK_COMPONENT = 26
    const val LUMBRIDGE_COMPONENT = 29
    const val FALADOR_COMPONENT = 32
    const val CAMELOT_COMPONENT = 37
    const val KOUREND_COMPONENT = 39
    const val ARDOUGNE_COMPONENT = 44
    const val FORTIS_COMPONENT = 46
    const val WATCHTOWER_COMPONENT = 50
    const val TROLLHEIM_COMPONENT = 57
    const val APE_ATOLL_COMPONENT = 60

    private fun spell(id: Int, internal: String, name: String, level: Int, component: Int, vararg runes: Pair<Int, Int>, extra: Pair<Int, Int>? = null): ObjType {
        val params = HashMap<Int, Any>()
        params[Spells.PARAM_NAME] = name
        params[Spells.PARAM_SPELLBOOK] = 0
        params[Spells.PARAM_LEVEL] = level
        params[Spells.PARAM_COMPONENT] = (Spells.SPELLBOOK shl 16) or component
        runes.forEachIndexed { i, (rune, count) ->
            val (runeParam, countParam) = Spells.PARAM_RUNES[i]
            params[runeParam] = rune
            params[countParam] = count
        }
        if (extra != null) {
            params[TeleportData.PARAM_EXTRA_ITEM] = extra.first
            params[TeleportData.PARAM_EXTRA_COUNT] = extra.second
        }
        return ObjType(id, "null", params = params, internalName = internal)
    }

    private fun tablet(id: Int, internal: String, name: String, vararg ops: String?) =
        ObjType(id, name, stackable = true, members = true, iops = listOf("Break", *ops).plus(List(4 - ops.size) { null }), internalName = internal)

    private fun item(id: Int, internal: String, name: String, stackable: Boolean = true) =
        ObjType(id, name, stackable = stackable, iops = listOf(null, null, null, null, "Drop"), internalName = internal)

    val all: List<ObjType> = listOf(
        item(LAW, "lawrune", "Law rune"),
        item(AIR, "airrune", "Air rune"),
        item(FIRE, "firerune", "Fire rune"),
        item(EARTH, "earthrune", "Earth rune"),
        item(WATER, "waterrune", "Water rune"),
        item(BANANA, "banana", "Banana", stackable = false),
        ObjType(STAFF_OF_AIR, "Staff of air", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, internalName = "staff_of_air", category = 1),
        spell(VARROCK, "25_varrock_teleport", "Varrock Teleport", 25, VARROCK_COMPONENT, LAW to 1, AIR to 3, FIRE to 1),
        spell(LUMBRIDGE, "31_lumbridge_teleport", "Lumbridge Teleport", 31, LUMBRIDGE_COMPONENT, LAW to 1, AIR to 3, EARTH to 1),
        spell(FALADOR, "37_falador_teleport", "Falador Teleport", 37, FALADOR_COMPONENT, LAW to 1, AIR to 3, WATER to 1),
        spell(CAMELOT, "45_camelot_teleport", "Camelot Teleport", 45, CAMELOT_COMPONENT, LAW to 1, AIR to 5),
        spell(KOUREND, "cert_deadman_level99_lamp", "Kourend Castle Teleport", 48, KOUREND_COMPONENT, LAW to 2, WATER to 1, FIRE to 1),
        spell(ARDOUGNE, "51_ardougne_teleport", "Ardougne Teleport", 51, ARDOUGNE_COMPONENT, LAW to 2, WATER to 2),
        spell(FORTIS, "placeholder_blighted_sack_snare", "Civitas illa Fortis Teleport", 54, FORTIS_COMPONENT, LAW to 2, EARTH to 1, FIRE to 1),
        spell(WATCHTOWER, "58_watchtower_teleport", "Watchtower Teleport", 58, WATCHTOWER_COMPONENT, LAW to 2, EARTH to 2),
        spell(TROLLHEIM, "61_trollheim_teleport", "Trollheim Teleport", 61, TROLLHEIM_COMPONENT, LAW to 2, FIRE to 2),
        spell(APE_ATOLL, "64_ape_atoll_teleport", "Ape Atoll Teleport", 64, APE_ATOLL_COMPONENT, LAW to 2, WATER to 2, FIRE to 2, extra = BANANA to 1),
        tablet(VARROCK_TABLET, "poh_tablet_varrockteleport", "Varrock teleport", "Varrock", "Grand Exchange", "Toggle"),
        tablet(LUMBRIDGE_TABLET, "poh_tablet_lumbridgeteleport", "Lumbridge teleport"),
        tablet(FALADOR_TABLET, "poh_tablet_faladorteleport", "Falador teleport"),
        tablet(CAMELOT_TABLET, "poh_tablet_camelotteleport", "Camelot teleport", "Camelot", "Seers' Village", "Toggle"),
        tablet(ARDOUGNE_TABLET, "poh_tablet_ardougneteleport", "Ardougne teleport"),
        tablet(WATCHTOWER_TABLET, "poh_tablet_watchtowerteleport", "Watchtower teleport", "Watchtower", "Yanille", "Toggle"),
        tablet(KOUREND_TABLET, "poh_tablet_kourendteleport", "Kourend castle teleport"),
        tablet(FORTIS_TABLET, "poh_tablet_fortisteleport", "Civitas illa fortis teleport"),
    )

    fun objTypes(vararg more: ObjType): ObjTypes {
        val types = all + more
        val objs = arrayOfNulls<ObjType>(types.maxOf { it.id } + 1)
        for (type in types) objs[type.id] = type
        return ObjTypes(objs)
    }
}
