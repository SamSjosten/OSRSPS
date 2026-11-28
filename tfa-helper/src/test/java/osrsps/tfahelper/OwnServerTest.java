package osrsps.tfahelper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OwnServerTest
{
	@Test
	public void ourHostsAreOurs()
	{
		assertTrue(OwnServer.isOurs("192.168.0.210"));
		assertTrue(OwnServer.isOurs("192.168.0.210:43594"));
		assertTrue(OwnServer.isOurs("10.99.0.10"));
		assertTrue(OwnServer.isOurs("127.0.0.1"));
		// RSProx's own loopback address for our target on VM 215.
		assertTrue(OwnServer.isOurs("127.1.45.3"));
		assertTrue(OwnServer.isOurs("127.1.45.3:43594"));
		assertTrue(OwnServer.isOurs("LOCALHOST"));
		assertTrue(OwnServer.isOurs("::1"));
		assertTrue(OwnServer.isOurs("[::1]:43594"));
	}

	@Test
	public void jagexWorldsAndAnythingUnknownAreNot()
	{
		assertFalse(OwnServer.isOurs("oldschool1.runescape.com"));
		assertFalse(OwnServer.isOurs("oldschool101.runescape.com:43594"));
		assertFalse(OwnServer.isOurs("192.168.0.21"));
		assertFalse(OwnServer.isOurs("192.168.0.2100"));
		assertFalse(OwnServer.isOurs("127.0.0.1.example.com"));
		assertFalse(OwnServer.isOurs(""));
		assertFalse(OwnServer.isOurs(null));
		assertFalse(OwnServer.isOurs("[::1"));
		assertFalse(OwnServer.isOurs("127.1.45.256"));
		assertFalse(OwnServer.isOurs("128.0.0.1"));
		assertFalse(OwnServer.isOurs("127.example.com"));
	}
}
