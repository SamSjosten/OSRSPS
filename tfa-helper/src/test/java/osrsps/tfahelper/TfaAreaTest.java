package osrsps.tfahelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TfaAreaTest
{
	@Test
	public void sideRoomsAreTheirWholeMapSquare()
	{
		// Arrival tiles from our server's ArchiveRoom (the tsps capture notes' for these four).
		assertEquals(TfaArea.EZEKIEL, TfaArea.at(4079, 5536));
		assertEquals(TfaArea.BORK, TfaArea.at(4173, 5599));
		assertEquals(TfaArea.DREADNOTH, TfaArea.at(4174, 5664));
		assertEquals(TfaArea.XARTH, TfaArea.at(4175, 5535));
		assertEquals(TfaArea.MOIA, TfaArea.at(4084, 5663));
		assertEquals(TfaArea.DAGONHAI, TfaArea.at(4086, 5599));
		// A square's corners, and one past them.
		assertEquals(TfaArea.BORK, TfaArea.at(65 << 6, 87 << 6));
		assertEquals(TfaArea.BORK, TfaArea.at((66 << 6) - 1, (88 << 6) - 1));
		assertEquals(TfaArea.DREADNOTH, TfaArea.at(65 << 6, 88 << 6));
		assertNull(TfaArea.at(66 << 6, 87 << 6));
	}

	@Test
	public void theNexusEndsAtTheArchiveDoorNorthAndOcellusBeginsThere()
	{
		assertEquals(TfaArea.NEXUS, TfaArea.at(4127, 5573));
		assertEquals(TfaArea.NEXUS, TfaArea.at(4127, 5622));
		assertEquals(TfaArea.OCELLUS, TfaArea.at(4127, 5623));
		// Sam's recordings of 9 November 2026: the arrival and the fight in Ocellus's arena.
		assertEquals(TfaArea.OCELLUS, TfaArea.at(4128, 5637));
		assertEquals(TfaArea.OCELLUS, TfaArea.at(4120, 5670));
		assertEquals(TfaArea.OCELLUS, TfaArea.at(4136, 5685));
	}

	@Test
	public void theVaultIsSquare64By90AndTheLobbyIsNothing()
	{
		assertEquals(TfaArea.VAULT, TfaArea.at(4127, 5779));
		// The lobby's Opening, square 64,86: not in the raid.
		assertNull(TfaArea.at(4126, 5505));
		assertNull(TfaArea.at(3222, 3218));
	}
}
