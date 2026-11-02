package osrsps.game.content

import osrsps.cache.InterfaceCodec
import osrsps.cache.InterfaceCodec.Component
import osrsps.cache.Sprite
import osrsps.cache.SpriteCodec
import osrsps.cache.overlay.CacheOverlay

/**
 * The lodestone map, after RS3's: an interface of our own (the first; [INTERFACE], served through the cache overlay as
 * our locs are) that Home Teleport, `::lodestone` and an active stone's Teleport open in place of the chat menu steps 1
 * to 5 used. A map of the stones' part of Misthalin ([MAP_SPRITE]) with each stone's icon where it stands, lit once the
 * player has activated it and grey until then ([activeIcon], [dormantIcon]; one shown, one hidden), the stones' names
 * in a list beside the map ([row]), both clickable (Teleport) for the activated ones, "Previous destination" under the
 * list ([PREVIOUS]; RS3's spell option, the stone last teleported to), the title and a close button.
 *
 * Built in code, the components ([components]; if3, as [InterfaceCodec] writes them) and the sprites ([sprites]; drawn
 * here: a parchment map with the map squares' grid and the Wilderness's edge, the two icons and the close button). A
 * file `data/sprites/<id>.dat` on the server takes a sprite's place ([CacheOverlay]), which is how a real map image
 * goes in without touching git: `osrsps import-sprite map.png data/sprites/65003.dat`. What the server sets at every
 * open ([LodestoneNetwork.open]): the title, the names, which icon shows, Previous destination's words, and the
 * events that let the client send the clicks. Nothing of the look is in the cache's definition but the layout.
 *
 * Guessed: the look (RS3's isn't in the OSRS cache), the fonts' ids (RuneLite's FontID: p12 495, b12 496), and that a
 * modal of 480 by 300 fits the main slot (the fixed frame's viewport is 512 by 334). Checked by a VM screenshot.
 */
object LodestoneMap {
    /** Above the 241 cache's own interfaces (they end below 1,000) and below 32,768: a packed component id is a signed int. */
    const val INTERFACE = 30000
    const val WIDTH = 480
    const val HEIGHT = 300

    /** The fixed components; a site's follow, five each ([activeIcon] and on). */
    const val ROOT = 0
    const val BACKGROUND = 1
    const val BORDER = 2
    const val TITLE = 3
    const val CLOSE = 4
    const val MAP = 5
    const val PREVIOUS = 6
    const val PREVIOUS_TEXT = 7
    private const val FIRST_SITE_COMPONENT = 8
    private const val COMPONENTS_A_SITE = 5

    /** The one option of a stone's icon, its row and Previous destination, and of the close button. */
    const val TELEPORT = 1
    const val CLOSE_OP = 1

    /** The site's lit icon, shown once the player has activated the stone. */
    fun activeIcon(site: Lodestones.Site): Int = FIRST_SITE_COMPONENT + site.index * COMPONENTS_A_SITE

    /** The site's grey icon, shown until then. */
    fun dormantIcon(site: Lodestones.Site): Int = activeIcon(site) + 1

    /** The clickable layer over the site's icon. */
    fun iconButton(site: Lodestones.Site): Int = activeIcon(site) + 2

    /** The site's clickable row in the list beside the map. */
    fun row(site: Lodestones.Site): Int = activeIcon(site) + 3

    /** The row's text: the site's name. */
    fun rowText(site: Lodestones.Site): Int = activeIcon(site) + 4

    /** Our sprites, in the sprite index above the cache's own (they end below 10,000). */
    const val ACTIVE_SPRITE = 65000
    const val DORMANT_SPRITE = 65001
    const val CLOSE_SPRITE = 65002
    const val MAP_SPRITE = 65003

    /** The map's place in the window and its size; icons are [ICON] square. */
    const val MAP_X = 12
    const val MAP_Y = 30
    const val MAP_WIDTH = 300
    const val MAP_HEIGHT = 262
    const val ICON = 20

    /** The map's projection: the tile at its left edge, the tile at its top, and 7/10 of a pixel a tile. */
    const val MAP_WEST = 3000
    const val MAP_NORTH = 3540
    private const val SCALE_NUMERATOR = 7
    private const val SCALE_DENOMINATOR = 10

    /** Where tile ([x], [z]) is drawn, in the map's pixels from its top left. */
    fun mapX(x: Int): Int = (x - MAP_WEST) * SCALE_NUMERATOR / SCALE_DENOMINATOR
    fun mapY(z: Int): Int = (MAP_NORTH - z) * SCALE_NUMERATOR / SCALE_DENOMINATOR

    /** The site's icon's top-left corner in the window: centred on the stone's centre tile. */
    fun iconX(site: Lodestones.Site): Int = MAP_X + mapX(site.x + 1) - ICON / 2
    fun iconY(site: Lodestones.Site): Int = MAP_Y + mapY(site.z + 1) - ICON / 2

    /** The list beside the map: a row a site, then Previous destination. */
    const val ROW_X = 324
    const val ROW_Y = 36
    const val ROW_WIDTH = 144
    const val ROW_HEIGHT = 20
    const val ROW_GAP = 24
    const val PREVIOUS_Y = 170
    const val PREVIOUS_HEIGHT = 40
    fun rowY(site: Lodestones.Site): Int = ROW_Y + site.index * ROW_GAP

    /** Fonts: the cache's p12_full and b12_full (RuneLite's FontID; guessed to be the 241 cache's ids). */
    const val FONT_P12 = 495
    const val FONT_B12 = 496
    val FONTS = listOf(FONT_P12, FONT_B12)

    /** Colours: the window's browns, OSRS's orange for text, and the grey a locked stone's name is wrapped in. */
    const val BROWN = 0x3E3529
    const val TAN = 0x8F7A4F
    const val ORANGE = 0xFF981F
    const val LOCKED_COLOUR = "9f9f9f"

    val components: List<Component> = buildList {
        add(Component(ROOT, InterfaceCodec.LAYER, width = WIDTH, height = HEIGHT, xMode = InterfaceCodec.CENTRE, yMode = InterfaceCodec.CENTRE, noClickThrough = true))
        add(Component(BACKGROUND, InterfaceCodec.RECT, parent = ROOT, width = WIDTH, height = HEIGHT, colour = BROWN, filled = true))
        add(Component(BORDER, InterfaceCodec.RECT, parent = ROOT, width = WIDTH, height = HEIGHT, colour = TAN))
        add(
            Component(
                TITLE, InterfaceCodec.TEXT, parent = ROOT, y = 8, width = WIDTH, height = 18, font = FONT_B12,
                xAlign = InterfaceCodec.CENTRE, yAlign = InterfaceCodec.CENTRE, shadowed = true, colour = ORANGE,
            ),
        )
        add(Component(CLOSE, InterfaceCodec.GRAPHIC, parent = ROOT, x = WIDTH - 28, y = 8, width = ICON, height = ICON, sprite = CLOSE_SPRITE, ops = listOf("Close")))
        add(Component(MAP, InterfaceCodec.GRAPHIC, parent = ROOT, x = MAP_X, y = MAP_Y, width = MAP_WIDTH, height = MAP_HEIGHT, sprite = MAP_SPRITE))
        add(Component(PREVIOUS, InterfaceCodec.LAYER, parent = ROOT, x = ROW_X, y = PREVIOUS_Y, width = ROW_WIDTH, height = PREVIOUS_HEIGHT, hidden = true, ops = listOf("Teleport")))
        add(
            Component(
                PREVIOUS_TEXT, InterfaceCodec.TEXT, parent = PREVIOUS, width = ROW_WIDTH, height = PREVIOUS_HEIGHT, font = FONT_P12,
                yAlign = InterfaceCodec.CENTRE, shadowed = true, colour = ORANGE,
            ),
        )
        for (site in Lodestones.SITES) {
            val x = iconX(site)
            val y = iconY(site)
            add(Component(activeIcon(site), InterfaceCodec.GRAPHIC, parent = ROOT, x = x, y = y, width = ICON, height = ICON, sprite = ACTIVE_SPRITE, hidden = true))
            add(Component(dormantIcon(site), InterfaceCodec.GRAPHIC, parent = ROOT, x = x, y = y, width = ICON, height = ICON, sprite = DORMANT_SPRITE))
            add(Component(iconButton(site), InterfaceCodec.LAYER, parent = ROOT, x = x, y = y, width = ICON, height = ICON, ops = listOf("Teleport")))
            add(Component(row(site), InterfaceCodec.LAYER, parent = ROOT, x = ROW_X, y = rowY(site), width = ROW_WIDTH, height = ROW_HEIGHT, ops = listOf("Teleport")))
            add(
                Component(
                    rowText(site), InterfaceCodec.TEXT, parent = row(site), width = ROW_WIDTH, height = ROW_HEIGHT, font = FONT_P12,
                    yAlign = InterfaceCodec.CENTRE, shadowed = true, colour = ORANGE,
                ),
            )
        }
    }

    /** The sprites by id; see [draw]. */
    val sprites: Map<Int, Sprite> = mapOf(
        ACTIVE_SPRITE to icon(fill = 0x4FC3F7, rim = 0x1565C0, centre = 0xE1F5FE),
        DORMANT_SPRITE to icon(fill = 0x8A8A8A, rim = 0x4A4A4A, centre = 0xB0B0B0),
        CLOSE_SPRITE to closeButton(),
        MAP_SPRITE to map(),
    )

    /** What the overlay serves: the interface as one group of the interface index, and each sprite as a group of the sprite index. */
    val overlayGroups: List<CacheOverlay.Group> =
        listOf(CacheOverlay.Group(InterfaceCodec.INDEX, INTERFACE, InterfaceCodec.encodeGroup(components), components.indices.toList())) +
            sprites.entries.sortedBy { it.key }.map { (id, sprite) -> CacheOverlay.Group(SpriteCodec.INDEX, id, SpriteCodec.encode(sprite)) }

    private const val OPAQUE = 0xFF000000.toInt()

    private fun draw(width: Int, height: Int, paint: (x: Int, y: Int) -> Int): Sprite =
        Sprite(width, height, IntArray(width * height) { paint(it % width, it / width) })

    /** A round stone icon: a [rim] round a [fill] with a lighter [centre]; the corners clear. */
    private fun icon(fill: Int, rim: Int, centre: Int): Sprite = draw(ICON, ICON) { x, y ->
        val dx = 2 * x - (ICON - 1)
        val dy = 2 * y - (ICON - 1)
        val d = dx * dx + dy * dy
        when {
            d > 19 * 19 -> 0
            d > 15 * 15 -> OPAQUE or rim
            d <= 5 * 5 -> OPAQUE or centre
            else -> OPAQUE or fill
        }
    }

    /** A brown square with a light cross. */
    private fun closeButton(): Sprite = draw(ICON, ICON) { x, y ->
        val edge = x == 0 || y == 0 || x == ICON - 1 || y == ICON - 1
        val onCross = x in 4 until ICON - 4 && y in 4 until ICON - 4 && (kotlin.math.abs(x - y) <= 1 || kotlin.math.abs(x + y - (ICON - 1)) <= 1)
        OPAQUE or when {
            edge -> TAN
            onCross -> 0xE0D4B8
            else -> 0x5A2A2A
        }
    }

    /**
     * The map: parchment with the map squares' lines (every 64 tiles), the Wilderness's edge (north of tile 3,522)
     * shaded, and a dark border. A placeholder until a map image is put in `data/sprites`.
     */
    private fun map(): Sprite {
        val columns = (MAP_WEST..MAP_WEST + MAP_WIDTH * SCALE_DENOMINATOR / SCALE_NUMERATOR).filter { it % 64 == 0 }.map { mapX(it) }.toSet()
        val rows = (MAP_NORTH - MAP_HEIGHT * SCALE_DENOMINATOR / SCALE_NUMERATOR..MAP_NORTH).filter { it % 64 == 0 }.map { mapY(it) }.toSet()
        val wildernessBottom = mapY(WILDERNESS_EDGE)
        return draw(MAP_WIDTH, MAP_HEIGHT) { x, y ->
            val edge = x == 0 || y == 0 || x == MAP_WIDTH - 1 || y == MAP_HEIGHT - 1
            OPAQUE or when {
                edge -> 0x6E5B3A
                x in columns || y in rows -> 0xBFAE84
                y < wildernessBottom -> 0xC9A98E
                (x * 31 + y * 17) % 7 == 0 -> 0xD2C094
                else -> 0xD9C89E
            }
        }
    }

    /** The first Wilderness tile's z: the ditch is at 3,520 to 3,522, level 1 from 3,523. */
    private const val WILDERNESS_EDGE = 3523
}
