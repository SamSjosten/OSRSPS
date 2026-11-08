package osrsps.game.content

import osrsps.cache.SeqCodec
import osrsps.cache.SpotanimCodec
import osrsps.game.EffectIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomAnimsTest {
    @Test
    fun `our ids are above the cache's, and the overlay serves each definition in its group, read back the same`() {
        assertEquals(listOf(65000, 65001, 65002, 65003, 65004), CustomAnims.seqs.keys.sorted())
        assertEquals(listOf(65000, 65001, 65002), CustomAnims.spotanims.keys.sorted())
        val files = CustomAnims.overlayFiles
        assertEquals(8, files.size)
        assertTrue(files.all { it.index == 2 && (it.group == SeqCodec.GROUP || it.group == SpotanimCodec.GROUP) })
        for ((id, seq) in CustomAnims.seqs) assertEquals(seq, SeqCodec.decode(files.single { it.group == SeqCodec.GROUP && it.file == id }.data))
        for ((id, spotanim) in CustomAnims.spotanims) assertEquals(spotanim, SpotanimCodec.decode(files.single { it.group == SpotanimCodec.GROUP && it.file == id }.data))
    }

    @Test
    fun `the cast plays the cache's teleport frames with the hands empty, and holds its last frame past any cast`() {
        assertEquals((1..5).map { SeqCodec.frame(11003, it) }, CustomAnims.CHARGE.frames, "the standard teleport's arms rising")
        assertEquals((1..17).map { SeqCodec.frame(11679, it) }, CustomAnims.RISE.frames, "Tele Other's recipient")
        for (seq in listOf(CustomAnims.CHARGE, CustomAnims.RISE, CustomAnims.LAND)) {
            assertEquals(0, seq.leftHand)
            assertEquals(0, seq.rightHand)
            assertEquals(8, seq.verticalOffset)
            assertEquals(6, seq.priority)
        }
        for (seq in listOf(CustomAnims.CHARGE, CustomAnims.RISE)) assertTrue(seq.delays.last() * 20 > Lodestones.CAST_TICKS * 600, "the last frame outlasts the cast")
        val rise = CustomAnims.RISE.delays.dropLast(1).sum()
        assertEquals(64, rise, "Tele Other's 16 frames at 4 cycles")
        assertTrue(rise * 20 <= Lodestones.RISE_TICKS * 600, "the rise fits before the landing")
    }

    @Test
    fun `the landing is the rise backwards and the swirl backwards, once through at the cache's pace`() {
        assertEquals(CustomAnims.RISE.frames.reversed(), CustomAnims.LAND.frames)
        assertEquals(List(17) { 4 }, CustomAnims.LAND.delays)
        assertEquals(68, CustomAnims.LAND.cycles, "1.4 s, then the player stands")
        assertEquals((17 downTo 1).map { SeqCodec.frame(7588, it) }, CustomAnims.LAND_FLASH_SEQ.frames)
        assertEquals(68, CustomAnims.LAND_FLASH_SEQ.cycles)
        assertEquals(CustomAnims.LODESTONE_LAND_FLASH_SEQ, CustomAnims.LAND_FLASH.seq)
        assertEquals(HomeTeleport.Stage(Lodestones.CAST_TICKS, CustomAnims.LODESTONE_LAND, CustomAnims.LODESTONE_LAND_FLASH), Lodestones.LANDING)
        assertTrue(Lodestones.LANDING.anim in CustomAnims.seqs && Lodestones.LANDING.spotanim in CustomAnims.spotanims)
    }

    @Test
    fun `the graphics are Tele Other's model drawn white-cyan, the pulse on its own held sequence and the flash on the cache's`() {
        for (graphic in listOf(CustomAnims.PULSE, CustomAnims.FLASH, CustomAnims.LAND_FLASH)) {
            assertEquals(5802, graphic.model)
            assertEquals(listOf(49969 to CustomAnims.LODESTONE_WHITE), graphic.recolours)
            assertEquals(100, graphic.ambient)
            assertEquals(100, graphic.contrast)
        }
        assertEquals(CustomAnims.LODESTONE_PULSE_SEQ, CustomAnims.PULSE.seq)
        assertEquals(1813, CustomAnims.FLASH.seq)
        assertEquals((1..9).map { SeqCodec.frame(7588, it) }, CustomAnims.PULSE_SEQ.frames)
        assertTrue(CustomAnims.PULSE_SEQ.delays.last() * 20 > Lodestones.CAST_TICKS * 600, "the glow stays")
        assertEquals(SpotanimCodec.hsl(29, 2, 100), CustomAnims.LODESTONE_WHITE)
    }

    @Test
    fun `the lodestone cast is the charge from the click and the rise before the landing, both ours`() {
        assertEquals(
            listOf(
                HomeTeleport.Stage(0, CustomAnims.LODESTONE_CHARGE, CustomAnims.LODESTONE_PULSE),
                HomeTeleport.Stage(Lodestones.CAST_TICKS - Lodestones.RISE_TICKS, CustomAnims.LODESTONE_RISE, CustomAnims.LODESTONE_FLASH),
            ),
            Lodestones.STAGES,
        )
        assertTrue(Lodestones.STAGES.all { it.anim in CustomAnims.seqs && it.spotanim in CustomAnims.spotanims })
        assertTrue(Lodestones.RISE_TICKS in 1 until Lodestones.CAST_TICKS)
    }

    @Test
    fun `the dev commands' ids take ours beside the cache's`() {
        val ids = EffectIds(setOf(4847), setOf(800), null, null).withCustom(CustomAnims.seqs.keys, CustomAnims.spotanims.keys)
        assertTrue(ids.seqExists(4847))
        assertTrue(ids.seqExists(CustomAnims.LODESTONE_RISE))
        assertFalse(ids.seqExists(65005))
        assertTrue(ids.spotanimExists(CustomAnims.LODESTONE_FLASH))
        assertFalse(ids.spotanimExists(65003))
        assertTrue(EffectIds.UNKNOWN.withCustom(CustomAnims.seqs.keys, CustomAnims.spotanims.keys).seqExists(123), "unknown stays unknown")
    }
}
