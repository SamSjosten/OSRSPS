package osrsps.game.look

import osrsps.game.PlayerLook

/**
 * The makeover screen's pages, from the cache ([osrsps.cache.MakeoverLoader]): for each page the styles it lists, in the
 * order the client draws them, and how many colours it offers.
 */
class MakeoverCatalog(val pages: Map<MakeoverPage, Page>) {
    /** One style: the identity kit it gives body type A and body type B. */
    data class Style(val kitA: Int, val kitB: Int) {
        fun kit(bodyType: Int) = if (bodyType == PlayerLook.BODY_TYPE_A) kitA else kitB
    }

    /** A page's styles in the client's order, and its colour count (0 when it has no colours). */
    data class Page(val styles: List<Style>, val colours: Int)

    operator fun get(page: MakeoverPage): Page? = pages[page]

    companion object {
        val NONE = MakeoverCatalog(emptyMap())
    }
}

/**
 * The makeover screen's pages, as `makeover_interface_type` (varbit 4130) numbers them in `makeover_init` (rev 242
 * osrs-dumps): each page's style enum (of db rows whose columns 1 and 2 are the body type A and B kits), its colour
 * enum (null for none), the body part its styles fill and the colour its swatches set. Type 2, the burger menu's
 * "Body types", is another interface ([MakeoverScreen.BODY_TYPES]), not a page of this one.
 */
enum class MakeoverPage(
    val type: Int,
    val stylesEnum: Int,
    val coloursEnum: Int?,
    val part: Int,
    val colour: PlayerLook.ColourPart?,
) {
    HAIR(0, 496, 890, PlayerLook.BodyPart.HAIR, PlayerLook.ColourPart.HAIR),
    FACIAL_HAIR(1, 2630, 890, PlayerLook.BodyPart.JAW, PlayerLook.ColourPart.HAIR),
    TORSOS(3, 5495, 789, PlayerLook.BodyPart.TORSO, PlayerLook.ColourPart.TORSO),
    SLEEVES(4, 5496, 789, PlayerLook.BodyPart.ARMS, PlayerLook.ColourPart.TORSO),
    BOTTOMS(5, 5497, 791, PlayerLook.BodyPart.LEGS, PlayerLook.ColourPart.LEGS),
    SHOES(6, 5498, 5290, PlayerLook.BodyPart.FEET, PlayerLook.ColourPart.FEET),
    WRISTWEAR(7, 5499, null, PlayerLook.BodyPart.HANDS, null),
    ;

    companion object {
        fun ofType(type: Int): MakeoverPage? = entries.firstOrNull { it.type == type }
    }
}
