package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import osrsps.game.dialogue.DialogueText

class ExaminesTest {
    @Test
    fun `NPCs and locs have a key each, and a multi-NPC's ids share one`() {
        assertEquals("examine.npc_3105", Examines.npc(3105)?.key)
        assertEquals("examine.loc_365", Examines.loc(365)?.key)
        assertSame(Examines.npc(Perdu.ID), Examines.npc(7457))
        assertNull(Examines.npc(1))
        assertNull(Examines.loc(1))
    }

    @Test
    fun `every castle NPC that talks has an examine`() {
        for (id in listOf(Hans.ID, Cook.ID, DukeHoracio.ID, Perdu.ID, CraftingTutor.ID, Bankers.BANKER, Bankers.BANKER_TUTOR, Doomsayer.ID, Hatius.ID, LumbridgeGuide.ID) + Townsfolk.MEN + Townsfolk.WOMEN) {
            assertEquals("examine.npc_$id", Examines.npc(id)?.key, "NPC $id")
        }
    }

    @Test
    fun `the church's NPCs and the scenery outside the castle have examines`() {
        for (id in listOf(Church.FATHER_AERECK, Church.PRAYER_TUTOR, Church.NIGEL_REGULAR, Church.COUNT_CHECK)) {
            assertEquals("examine.npc_$id", Examines.npc(id)?.key, "NPC $id")
        }
        // Nigel's multi-NPC id shares the Nigel a normal world shows.
        assertSame(Examines.npc(Church.NIGEL_REGULAR), Examines.npc(Church.NIGEL))
        // The church's altar, organ and pews, the graveyard's gravestones, a water wheel and a plant west of the castle: the 241
        // cache's ids (dump.loc).
        for (id in listOf(Church.ALTAR, 3500, 5788, 404, 405, 5590, 1391)) assertEquals("examine.loc_$id", Examines.loc(id)?.key, "loc $id")
    }

    @Test
    fun `an examine with no words shows its key`() {
        assertEquals("[examine.npc_3105]", DialogueText.NONE[Examines.npc(3105)!!])
        assertEquals("Servant.", DialogueText(mapOf("examine.npc_3105" to "Servant."))[Examines.npc(3105)!!])
    }
}
