package osrsps.game.content

import osrsps.game.World

/**
 * Rune packs: Open swaps one pack for 100 of its runes, as the Wiki's pack pages have it (the Air rune pack and Tzhaar
 * air rune pack pages, read 2026-11-22; the others follow them). The packs don't stack, so the runes take the pack's slot
 * when the player has none already. No message on opening (none on the Wiki). Sold by the Magic shops and TzHaar-Mej-Roh.
 */
object RunePacks {
    const val RUNES_EACH = 100

    /** Each pack and the rune it holds. */
    val PACKS = mapOf(
        12728 to 556, // air_rune_pack
        12730 to 555, // water_rune_pack
        12732 to 557, // earth_rune_pack
        12734 to 554, // fire_rune_pack
        12736 to 558, // mind_rune_pack
        12738 to 562, // chaos_rune_pack
        21698 to 556, // tzhaar_air_rune_pack
        21701 to 555, // tzhaar_water_rune_pack
        21704 to 557, // tzhaar_earth_rune_pack
        21707 to 554, // tzhaar_fire_rune_pack
    )

    fun register(world: World) {
        for ((pack, rune) in PACKS) {
            val type = world.objTypes[pack] ?: continue
            val runes = world.objTypes[rune] ?: continue
            val op = type.iops.indexOfFirst { it.equals("Open", ignoreCase = true) }.takeIf { it >= 0 }?.plus(1) ?: continue
            world.handlers.onObj(pack, op) { use ->
                if (player.inventory[use.sub]?.id != pack) return@onObj
                if (player.inventory.count(rune) + RUNES_EACH > Int.MAX_VALUE) return@onObj
                player.inventory.remove(use.sub, 1)
                player.inventory.add(runes, RUNES_EACH)
            }
        }
    }
}
