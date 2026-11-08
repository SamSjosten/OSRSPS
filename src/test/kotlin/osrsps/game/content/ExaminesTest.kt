package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import osrsps.game.dialogue.DialogueText
import osrsps.game.skill.Mining

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
    fun `the Wizards' Tower's people and the essence mine have examines, and multi-NPCs share theirs`() {
        val ids = listOf(EssenceMine.SEDRIDOR, WizardsTowerPeople.TRAIBORN, WizardsTowerPeople.MIZGOG, WizardsTowerPeople.GRAYZAG, WizardsTowerPeople.ONGLEWIP, Jalarast.JALARAST, Rick.RICK)
        for (id in ids) assertEquals("examine.npc_$id", Examines.npc(id)?.key, "NPC $id")
        // Sedridor's, Mizgog's and Rick's other types show the same text.
        assertSame(Examines.npc(EssenceMine.SEDRIDOR), Examines.npc(5034))
        assertSame(Examines.npc(WizardsTowerPeople.MIZGOG), Examines.npc(5005))
        assertSame(Examines.npc(Rick.RICK), Examines.npc(11409))
        // The Rune Essence rock and a portal (the 241 cache's ids).
        for (id in listOf(Mining.ESSENCE_ROCK, EssenceMine.PORTAL)) assertEquals("examine.loc_$id", Examines.loc(id)?.key, "loc $id")
        // The map's portal, a multiloc, shows the text of the portal it looks like.
        assertSame(Examines.loc(EssenceMine.PORTAL), Examines.loc(EssenceMine.MAP_PORTAL))
    }

    @Test
    fun `Draynor Manor's basement has examines, and its doors and levers share their visible types' ones`() {
        for (id in listOf(DraynorManorBasement.LADDER_UP, DraynorManorBasement.COFFIN)) assertEquals("examine.loc_$id", Examines.loc(id)?.key, "loc $id")
        // A door's multiloc id and both its states (shut 11449, ajar 11450) show one text.
        for (door in DraynorManorBasement.Door.entries) assertSame(Examines.loc(11449), Examines.loc(door.id), door.name)
        assertSame(Examines.loc(11449), Examines.loc(11450))
        // Lever A's multiloc id (146) and its up and down types (11451, 11452).
        assertSame(Examines.loc(11451), Examines.loc(DraynorManorBasement.LEVERS.first()))
        assertSame(Examines.loc(11451), Examines.loc(11452))
    }

    @Test
    fun `an examine with no words shows its key`() {
        assertEquals("[examine.npc_3105]", DialogueText.NONE[Examines.npc(3105)!!])
        assertEquals("Servant.", DialogueText(mapOf("examine.npc_3105" to "Servant."))[Examines.npc(3105)!!])
    }
}
