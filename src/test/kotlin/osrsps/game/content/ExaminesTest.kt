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
    fun `the text review's types have examines too, keyed one per id`() {
        // A Farmer of the farms and roads, and the Lumbridge Swamp Caves' gas hole and stalagmites (2026-11-15).
        for (id in listOf(3114, 3243, 11921)) assertEquals("examine.npc_$id", Examines.npc(id)?.key, "NPC $id")
        for (id in listOf(5918, 22542, 22543)) assertEquals("examine.loc_$id", Examines.loc(id)?.key, "loc $id")
    }

    @Test
    fun `the Wilderness's NPCs and Ferox Enclave's locs up to level 20 have examines, keyed one per id`() {
        // Wilderness step W2.5: the types the Edgeville Dungeon's Wilderness part, levels 1 to 20 and Ferox Enclave
        // spawn (the 242 cache's ids) that had none, and the Enclave's pool, barriers and bank chest and the Corporeal
        // Beast's cave.
        val npcs = listOf(
            45, 46, 47, 48, 60, 61, 62, 63, 82, 86, 87, 90, 91, 260, 263, 264, 510, 520, 525, 531, 1027, 2049, 2050, 2052,
            2840, 2849, 3023, 6590, 6594, 6604, 6605, 6606, 6607, 8721, 10370, 10373, 10377, 10378, 10379, 10380, 10381,
            10382, 10383, 10385, 10386, 10387, 10388, FeroxEnclave.BANKER, 10392,
        )
        for (id in npcs) assertEquals("examine.npc_$id", Examines.npc(id)?.key, "NPC $id")
        val locs = listOf(678, 26711, FeroxEnclave.POOL, FeroxEnclave.BARRIER, FeroxEnclave.BARRIER_MIRROR)
        for (id in locs) assertEquals("examine.loc_$id", Examines.loc(id)?.key, "loc $id")
    }

    @Test
    fun `the Wilderness's NPCs and handled locs from level 21 up have examines, keyed one per id`() {
        // Wilderness step W3.8: the types piece 3 spawns (the 242 cache's ids) and the locs its content handles that
        // had none and that the Wiki gives one text.
        val npcs = listOf(
            13, 59, 104, 106, 108, 255, 256, 261, 262, 291, 301, 302, 303, 517, 523, 1026, 1536, MageArenaBank.GUNDAI,
            MageArenaBank.LUNDAIL, 2006, 2007, 2008, 2018, 2085, 2086, 2087, 2834, 2841, 2844, 3022, 3027, 6593, 6596, 6597,
            6598, ResourceArea.MANDRITH, ResourceArea.GOLEM, ResourceArea.GOLEM_ROCK, 6603, 6608,
        )
        for (id in npcs) assertEquals("examine.npc_$id", Examines.npc(id)?.key, "NPC $id")
        val locs = listOf(170, 171, 5960, 9472, 11727, 14825, 14826, 14827, 14828, 14829, 14830, ResourceArea.GATE)
        for (id in locs) assertEquals("examine.loc_$id", Examines.loc(id)?.key, "loc $id")
    }

    @Test
    fun `the Wilderness's bosses and their lairs have examines, keyed one per id`() {
        // Wilderness step W4.16: piece 4's bosses (the 242 cache's ids), their lairs' monsters, the Escape Caves' bear
        // and spider, and the lairs' locs that had none and that the Wiki gives one text.
        val npcs = listOf(
            239, CorporealBeast.BOSS, CorporealBeast.CORE, 2054, Callisto.BOSS, Venenatis.BOSS, 6611, 6612,
            6613, 6614, 6615, 6616, 6617, 6618, 6619, 11989, 11990, 11992, 11993, 11994, 11998, Venenatis.SPIDERLING, 12001,
            12107, 12108,
        )
        for (id in npcs) assertEquals("examine.npc_$id", Examines.npc(id)?.key, "NPC $id")
        val locs = listOf(CorporealBeast.EXIT, VolcanicForge.FORGE, Vetion.ENTRANCE)
        for (id in locs) assertEquals("examine.loc_$id", Examines.loc(id)?.key, "loc $id")
    }

    @Test
    fun `the Revenant Caves' ways in and out have examines, keyed one per id`() {
        // Wilderness steps W5.1 and W5.1b: the caverns, the crevice, the stairs, the trapdoors they come out by and the pillars.
        for (id in listOf(RevenantCaves.CAVERN_LOW, RevenantCaves.CAVERN_HIGH, 31557, RevenantCaves.STAIRS_LOW, RevenantCaves.CREVICE, RevenantCaves.STAIRS_HIGH, 31561)) {
            assertEquals("examine.loc_$id", Examines.loc(id)?.key, "loc $id")
        }
    }

    @Test
    fun `the Revenant Caves' monsters and the Emblem Trader have examines, keyed one per id`() {
        // Wilderness step W5.5: piece 5's NPCs, the eleven revenants (W5.2), the Emblem Trader (W5.3) and the Revenant
        // maledictus (W5.4).
        for (id in Revenants.IDS + EmblemTrader.ID + Maledictus.BOSS) assertEquals("examine.npc_$id", Examines.npc(id)?.key, "NPC $id")
    }

    @Test
    fun `every castle NPC that talks has an examine`() {
        for (id in listOf(Hans.ID, Cook.ID, DukeHoracio.ID, Perdu.ID, Cadmus.ID, Bankers.BANKER, Doomsayer.ID, Mikasi.ID, LumbridgeGuide.ID) + Townsfolk.MEN + Townsfolk.WOMEN) {
            assertEquals("examine.npc_$id", Examines.npc(id)?.key, "NPC $id")
        }
    }

    @Test
    fun `the church's NPCs and the scenery outside the castle have examines`() {
        for (id in listOf(Church.FATHER_AERECK, Church.NIGEL_REGULAR, Church.COUNT_CHECK)) {
            assertEquals("examine.npc_$id", Examines.npc(id)?.key, "NPC $id")
        }
        // Nigel's multi-NPC id shares the Nigel a normal world shows.
        assertSame(Examines.npc(Church.NIGEL_REGULAR), Examines.npc(Church.NIGEL))
        assertSame(Examines.npc(Church.NIGEL_REGULAR), Examines.npc(Church.NIGEL_VARROCK))
        // The church's altar, organ and pews, the graveyard's gravestones, a water wheel and a plant west of the castle: the 241
        // cache's ids (dump.loc).
        for (id in listOf(Church.ALTAR, 3500, 5788, 404, 405, 5590, 1391)) assertEquals("examine.loc_$id", Examines.loc(id)?.key, "loc $id")
    }

    @Test
    fun `the reworked Lumbridge NPCs of 242 have examines, and the Ironman tutor's shown type shares his`() {
        val ids = listOf(Harlan.ID, Mikasi.ID, Feoras.ID, Cadmus.ID, Wilfred.ID, SwampTutors.MONLUM, SwampTutors.FINLAY, ShearedRam.ARTHUR)
        for (id in ids) assertEquals("examine.npc_$id", Examines.npc(id)?.key, "NPC $id")
        assertSame(Examines.npc(LumbridgeTownsfolk.IRONMAN_TUTOR), Examines.npc(LumbridgeTownsfolk.IRONMAN_TUTOR_VISIBLE))
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
