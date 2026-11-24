package osrsps.game.content

import osrsps.game.ObjType
import osrsps.game.WearPos

/**
 * What Entrana's search turns back, from the Wiki's Entrana page (Rules, read 24 Nov 2026): anything worn as a weapon
 * or armour with an attack or defence bonus, except the clothing, gloves, boots, capes, books and jewellery the page
 * lets through; and the items it bars that have no bonuses (dismantled cannons and godswords, banners, the swift blade,
 * the ham joint, insect nets, impling jars and the rest of its list). Used by the balloon assistants' search; the
 * Port Sarim monks' boat isn't built yet.
 *
 * Inferred: matching the page's items by their cache names (a whole family, such as every god cape or wizard robe, by
 * a shared part of the name); noted items passing, as nothing can wear them; only the vestment and third-age cloaks passing of the cloaks (the
 * page names no others, so an Ardougne cloak with bonuses is barred); imbued god capes barred, as the page names the
 * god capes only. Not built yet: the looting bag and tackle
 * box, which the page bars only when they hold a barred item.
 */
object EntranaSearch {
    /** Whether [type] may not be taken to Entrana. */
    fun prohibited(type: ObjType): Boolean {
        val name = type.name.lowercase()
        if (BARRED.any { it in name }) return true
        if (type.wearpos1 !in SLOTS) return false
        if (COMBAT_BONUS_PARAMS.none { (type.params[it] as? Int ?: 0) != 0 }) return false
        return !allowed(name)
    }

    private fun allowed(name: String): Boolean =
        !name.startsWith("imbued") && (ALLOWED.any { it in name } || COLOURED_ROBE.containsMatchIn(name) || FREMENNIK_CLOTHING.containsMatchIn(name))

    /** The slots weapons and armour are worn in; jewellery (neck and ring) and ammunition other than darts pass. */
    private val SLOTS = setOf(WearPos.HEAD, WearPos.CAPE, WearPos.WEAPON, WearPos.BODY, WearPos.SHIELD, WearPos.LEGS, WearPos.HANDS, WearPos.FEET)

    /** Params 0 to 9: the attack and defence bonuses (see Equipment). */
    private val COMBAT_BONUS_PARAMS = 0..9

    /** The page's items without bonuses, or that can't be worn, that are still barred. */
    private val BARRED = listOf(
        "cannon base", "cannon stand", "cannon barrels", "cannon furnace",
        " banner", "godsword blade", "godsword shard",
        "swift blade", "ham joint", "insect net", "butterfly net", "impling jar", "noose wand", "hallowed hammer",
        "prospector", "extra supply crate", "spoils of war", "forgotten lockbox", "teasing stick", "zealot's helm",
        "noxious point", "noxious blade", "noxious pommel", "sage's greaves",
    )

    /** The page's items with bonuses that may still go. */
    private val ALLOWED = listOf(
        "magic secateurs", "wizard", "zamorak monk", "ham shirt", "ham robe", "ham hood", "ham cloak", "ham logo",
        "ham gloves", "ham boots", "gloves of silence", "ice gloves", "penance gloves", "twitcher's gloves",
        "leather gloves", "ghostly gloves", "ghostly boots", "leather boots", "boots of lightness", "climbing boots",
        "rock-shell boots", "boots of stone", "spiked boots", "saradomin cloak", "guthix cloak", "zamorak cloak",
        "armadyl cloak", "bandos cloak", "ancient cloak", "3rd age cloak", "black cape", "red cape", "blue cape",
        "yellow cape", "green cape", "purple cape", "orange cape", "team-", "team cape", "spotted cape", "spottier cape",
        "saradomin cape", "zamorak cape", "guthix cape", "holy book", "unholy book", "book of balance", "book of law",
        "book of war", "book of darkness", "book of the dead", "tome of water", "tome of earth", "chronicle",
        "explorer's ring",
    )

    /** Yrsa's Fremennik clothing; the Fremennik blade, shield and helm are weapons and armour. */
    private val FREMENNIK_CLOTHING = Regex("^fremennik .*(shirt|robe|skirt|hat|cloak|boots|gloves)$")

    /** Canifis and gnome robes: a colour, then robe top or robe bottoms. */
    private val COLOURED_ROBE = Regex("^(yellow|green|red|blue|purple|grey|white|black|pink|cream|turquoise|teal) robe (top|bottoms)")
}
