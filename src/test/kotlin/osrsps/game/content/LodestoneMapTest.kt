package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.cache.InterfaceCodec
import osrsps.cache.InterfaceCodec.Component
import osrsps.cache.SpriteCodec
import osrsps.cache.overlay.CacheOverlay

/** The lodestone map's layout, sprites and what the overlay serves of them. */
class LodestoneMapTest {
    private val components = LodestoneMap.components

    private fun Component.right() = x + width
    private fun Component.bottom() = y + height
    private fun Component.overlaps(other: Component) = x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom()

    @Test
    fun `the components are numbered in order, every site has its five, and the ids fit the packets`() {
        assertEquals(components.indices.toList(), components.map { it.id })
        assertEquals(8 + 5 * Lodestones.SITES.size, components.size)
        assertTrue(LodestoneMap.INTERFACE < 0x8000, "a packed component id is a signed int")
        for (site in Lodestones.SITES) {
            assertEquals(InterfaceCodec.GRAPHIC, components[LodestoneMap.activeIcon(site)].type, site.slug)
            assertEquals(InterfaceCodec.GRAPHIC, components[LodestoneMap.dormantIcon(site)].type, site.slug)
            assertEquals(InterfaceCodec.LAYER, components[LodestoneMap.iconButton(site)].type, site.slug)
            assertEquals(InterfaceCodec.LAYER, components[LodestoneMap.row(site)].type, site.slug)
            assertEquals(InterfaceCodec.TEXT, components[LodestoneMap.rowText(site)].type, site.slug)
            assertEquals(LodestoneMap.row(site), components[LodestoneMap.rowText(site)].parent, "the name sits in its row")
            assertTrue(components[LodestoneMap.activeIcon(site)].hidden, "lit only once the server says so")
            assertEquals(listOf("Teleport"), components[LodestoneMap.iconButton(site)].ops)
            assertEquals(listOf("Teleport"), components[LodestoneMap.row(site)].ops)
            assertEquals(listOf(LodestoneMap.ACTIVE_SPRITE, LodestoneMap.DORMANT_SPRITE), listOf(components[LodestoneMap.activeIcon(site)].sprite, components[LodestoneMap.dormantIcon(site)].sprite))
        }
        assertEquals(listOf("Close"), components[LodestoneMap.CLOSE].ops)
        assertTrue(components[LodestoneMap.PREVIOUS].hidden, "shown only with a previous destination")
        assertTrue(components.all { it.text.isEmpty() && it.name.isEmpty() }, "the words come from the server at every open")
        assertTrue(components.map { it.sprite }.filter { it != InterfaceCodec.NO_SPRITE }.all { it in LodestoneMap.sprites }, "every sprite drawn is one of ours")
        assertTrue(components.map { it.font }.filter { it != InterfaceCodec.NO_FONT }.all { it in LodestoneMap.FONTS })
    }

    @Test
    fun `the window fits the fixed viewport, every part the window, the icons the map, and nothing clickable overlaps`() {
        assertTrue(LodestoneMap.WIDTH <= 512 && LodestoneMap.HEIGHT <= 334, "the fixed frame's viewport")
        val root = components[LodestoneMap.ROOT]
        assertEquals(InterfaceCodec.CENTRE, root.xMode)
        assertEquals(InterfaceCodec.CENTRE, root.yMode)
        assertTrue(root.noClickThrough, "clicks on the window don't fall through to the game")
        for (component in components.filter { it.parent == LodestoneMap.ROOT }) {
            assertTrue(component.x >= 0 && component.y >= 0 && component.right() <= LodestoneMap.WIDTH && component.bottom() <= LodestoneMap.HEIGHT, "component ${component.id} inside the window")
        }
        for (site in Lodestones.SITES) {
            val icon = components[LodestoneMap.activeIcon(site)]
            assertTrue(icon.x >= LodestoneMap.MAP_X && icon.y >= LodestoneMap.MAP_Y, "${site.slug}'s icon on the map")
            assertTrue(icon.right() <= LodestoneMap.MAP_X + LodestoneMap.MAP_WIDTH && icon.bottom() <= LodestoneMap.MAP_Y + LodestoneMap.MAP_HEIGHT, "${site.slug}'s icon on the map")
            assertEquals(icon.x to icon.y, components[LodestoneMap.dormantIcon(site)].let { it.x to it.y })
            assertEquals(icon.x to icon.y, components[LodestoneMap.iconButton(site)].let { it.x to it.y })
            assertTrue(components[LodestoneMap.row(site)].x >= LodestoneMap.MAP_X + LodestoneMap.MAP_WIDTH, "${site.slug}'s row beside the map")
        }
        val clickable = components.filter { it.ops.isNotEmpty() }
        for (a in clickable) for (b in clickable) if (a.id < b.id) assertTrue(!a.overlaps(b), "components ${a.id} and ${b.id} overlap")
        assertEquals(1 + 1 + 2 * Lodestones.SITES.size, clickable.size)
    }

    @Test
    fun `the map's projection puts each stone where it stands`() {
        // 7/10 of a pixel a tile from tile 3,000 at the left and 3,540 at the top: Lumbridge's centre tile 3233, 3221.
        assertEquals(163, LodestoneMap.mapX(3233))
        assertEquals(223, LodestoneMap.mapY(3221))
        assertEquals(LodestoneMap.MAP_X + 163 - 10, LodestoneMap.iconX(Lodestones.LUMBRIDGE))
        assertEquals(LodestoneMap.MAP_Y + 223 - 10, LodestoneMap.iconY(Lodestones.LUMBRIDGE))
        assertTrue(LodestoneMap.iconY(Lodestones.EDGEVILLE) < LodestoneMap.iconY(Lodestones.VARROCK), "Edgeville north of Varrock")
        assertTrue(LodestoneMap.iconX(Lodestones.DRAYNOR) < LodestoneMap.iconX(Lodestones.LUMBRIDGE), "Draynor west of Lumbridge")
        assertTrue(LodestoneMap.iconX(Lodestones.AL_KHARID) > LodestoneMap.iconX(Lodestones.LUMBRIDGE), "Al Kharid east of Lumbridge")
    }

    @Test
    fun `the sprites are the icons' size and the map's, the icons' corners clear and the map opaque`() {
        for (id in listOf(LodestoneMap.ACTIVE_SPRITE, LodestoneMap.DORMANT_SPRITE, LodestoneMap.CLOSE_SPRITE)) {
            val sprite = LodestoneMap.sprites.getValue(id)
            assertEquals(LodestoneMap.ICON to LodestoneMap.ICON, sprite.width to sprite.height, "sprite $id")
        }
        for (id in listOf(LodestoneMap.ACTIVE_SPRITE, LodestoneMap.DORMANT_SPRITE)) {
            val icon = LodestoneMap.sprites.getValue(id)
            assertEquals(0, icon[0, 0], "sprite $id's corner is clear")
            assertEquals(0xFF, icon[LodestoneMap.ICON / 2, LodestoneMap.ICON / 2] ushr 24, "sprite $id's centre is opaque")
        }
        val map = LodestoneMap.sprites.getValue(LodestoneMap.MAP_SPRITE)
        assertEquals(LodestoneMap.MAP_WIDTH to LodestoneMap.MAP_HEIGHT, map.width to map.height)
        assertTrue(map.pixels.all { it ushr 24 == 0xFF }, "the map has no clear pixel")
        assertTrue(map.pixels.map { it and 0xFFFFFF }.toSet().size <= SpriteCodec.MAX_COLOURS)
        // The minimap's icon has the cache's icons' size and frame: clear corners, a dark outline, an opaque middle.
        val minimap = LodestoneMap.sprites.getValue(LodestoneMap.MINIMAP_SPRITE)
        assertEquals(LodestoneMap.MINIMAP_ICON to LodestoneMap.MINIMAP_ICON, minimap.width to minimap.height)
        assertEquals(0, minimap[0, 0], "corner clear")
        assertEquals(0xFF010101.toInt(), minimap[LodestoneMap.MINIMAP_ICON / 2, 0], "the outline at the top")
        assertEquals(0xFF, minimap[LodestoneMap.MINIMAP_ICON / 2, LodestoneMap.MINIMAP_ICON / 2] ushr 24, "centre opaque")
    }

    @Test
    fun `the overlay serves the interface as one group of its components and each sprite as a group`() {
        val groups = LodestoneMap.overlayGroups
        assertEquals(listOf("3/30000", "8/65000", "8/65001", "8/65002", "8/65003", "8/65004"), groups.map { "${it.index}/${it.group}" })
        val interfaceGroup = groups[0]
        assertEquals(components.indices.toList(), interfaceGroup.files)
        assertEquals(components, InterfaceCodec.decodeGroup(interfaceGroup.data, components.size))
        for (group in groups.drop(1)) {
            val sprite = SpriteCodec.decode(group.data).single()
            val drawn = LodestoneMap.sprites.getValue(group.group)
            assertEquals(drawn.width to drawn.height, sprite.width to sprite.height, "sprite ${group.group}")
            assertContentEquals(drawn.pixels, sprite.pixels, "sprite ${group.group}")
            assertEquals(listOf(0), group.files)
        }
        assertTrue(groups.all { it.group <= CacheOverlay.MAX_GROUP })
    }
}
