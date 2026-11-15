package osrsps.game.skill.hunter

/**
 * Bird snaring (Hunter step 1): a bird snare laid near the five birds it catches.
 *
 * Sources: the OSRS Wiki's Bird snare page (read 2026-11-15) for the birds and the snare's ops (Reset and the failed
 * state since 7 October 2026); each bird's page for its level, XP (its Hunter info; the Bird snare page's table
 * rounds the cerulean twitch to 64.6 and the tropical wagtail to 95, the birds' own pages give 64.5 and 95.2, used
 * here), drops (bones, raw bird meat and 5 to 10 feathers of its colour) and catch pair (the "Skilling success chart",
 * Mod Ash's figures: crimson swift 39% at 1 to 164% at 99). Ids from the rev 242 cache by gameval name: the snare is
 * hunting_ojibway_bird_snare, its locs hunting_ojibway_trap (set), _failing, _broken, and one _trapping_ and _full_
 * pair per bird colour. Left out: the honey buzzards by the Digsite (Varlamore's 2026 birds), smoking a snare, and
 * the tailfeathers a wagtail drops only on a Hunters' Rumour.
 */
object BirdSnares {
    const val SNARE = 10006 // hunting_ojibway_bird_snare
    const val SET = 9345 // hunting_ojibway_trap
    const val FAILING = 9346 // hunting_ojibway_trap_failing
    const val BROKEN = 9344 // hunting_ojibway_trap_broken

    /** human_laytrap. */
    const val LAY_ANIM = 5208

    const val BONES = 526
    const val RAW_BIRD_MEAT = 9978 // spit_raw_bird_meat

    const val CRIMSON_SWIFT = 5549 // hunting_bird_jungle
    const val GOLDEN_WARBLER = 5551 // hunting_bird_desert
    const val COPPER_LONGTAIL = 5552 // hunting_bird_woodland
    const val CERULEAN_TWITCH = 5550 // hunting_bird_polar
    const val TROPICAL_WAGTAIL = 5548 // multicoloured_bird

    private fun bird(npc: Int, level: Int, xpTenths: Int, low: Int, high: Int, catching: Int, full: Int, feather: Int) =
        Creature(
            npc, level, xpTenths, low, high, catching, full,
            listOf(CreatureLoot(BONES, 1), CreatureLoot(RAW_BIRD_MEAT, 1), CreatureLoot(feather, 5, 10)),
        )

    val BIRDS = listOf(
        // hunting_ojibway_trap_trapping_jungle / _full_jungle; hunting_jungle_feather (red).
        bird(CRIMSON_SWIFT, 1, 340, 100, 420, 9349, 9373, 10088),
        // _desert; hunting_desert_feather (yellow).
        bird(GOLDEN_WARBLER, 5, 470, 92, 400, 9376, 9377, 10090),
        // _woodland; hunting_woodland_feather (orange).
        bird(COPPER_LONGTAIL, 9, 612, 85, 390, 9378, 9379, 10091),
        // _polar; hunting_polar_feather (blue).
        bird(CERULEAN_TWITCH, 11, 645, 82, 380, 9374, 9375, 10089),
        // _coloured; hunting_stripy_bird_feather.
        bird(TROPICAL_WAGTAIL, 19, 952, 75, 370, 9347, 9348, 10087),
    )

    val KIND = TrapKind("bird snare", SNARE, SET, FAILING, BROKEN, LAY_ANIM, BIRDS)
}
