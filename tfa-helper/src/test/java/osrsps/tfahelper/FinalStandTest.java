package osrsps.tfahelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FinalStandTest
{
	@Test
	public void theLoopCountsFromEachRise()
	{
		FinalStand stand = new FinalStand();
		assertFalse(stand.active(100));
		stand.rise(100);
		assertTrue(stand.active(100));
		assertEquals(0, stand.loopTick(100));
		assertEquals(24, stand.loopTick(124));
		assertEquals(59, stand.loopTick(159));
		// The next rise re-anchors the loop, even a tick late.
		stand.rise(161);
		assertEquals(0, stand.loopTick(161));
		assertEquals(10, stand.loopTick(171));
	}

	@Test
	public void itLetsGoWhenNoRiseFollowsTheLoop()
	{
		FinalStand stand = new FinalStand();
		stand.rise(0);
		assertTrue(stand.active(FinalStand.LOOP + FinalStand.LOST_AFTER - 1));
		assertFalse(stand.active(FinalStand.LOOP + FinalStand.LOST_AFTER));
		assertFalse(stand.active(0));
	}

	@Test
	public void theNextEventAndItsCountdown()
	{
		assertEquals("Lifeforce", FinalStand.next(0).label);
		assertEquals(3, FinalStand.ticksUntil(0, FinalStand.next(0)));
		assertEquals("Blast", FinalStand.next(22).label);
		assertEquals(2, FinalStand.ticksUntil(22, FinalStand.next(22)));
		assertEquals("Blast", FinalStand.next(50).label);
		assertEquals(52, FinalStand.next(50).at);
		// After the rifts, the next loop's rise.
		assertEquals("Rise (deflect melee)", FinalStand.next(59).label);
		assertEquals(1, FinalStand.ticksUntil(59, FinalStand.next(59)));
	}

	@Test
	public void blastsAreCountedThroughTheLoop()
	{
		assertEquals(0, FinalStand.blastsSoFar(23));
		assertEquals(1, FinalStand.blastsSoFar(24));
		assertEquals(1, FinalStand.blastsSoFar(51));
		assertEquals(2, FinalStand.blastsSoFar(52));
	}
}
