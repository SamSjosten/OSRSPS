package osrsps.tfahelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class RaidTrackerTest
{
	private final RaidTracker tracker = new RaidTracker();
	private int tick;

	/** Stands in [area] for [ticks] ticks; returns the run that ended during them, if any. */
	private RaidTracker.Run stay(TfaArea area, int ticks)
	{
		RaidTracker.Run ended = null;
		for (int i = 0; i < ticks; i++)
		{
			RaidTracker.Run run = tracker.tick(++tick, area);
			if (run != null)
			{
				ended = run;
			}
		}
		return ended;
	}

	@Test
	public void nothingHappensOutsideTheRaid()
	{
		assertNull(stay(null, 50));
		assertFalse(tracker.inRun());
		assertEquals(0, tracker.runTicks(tick));
	}

	@Test
	public void aRunIsTimedAreaByAreaAndEndsAfterFiveTicksOutside()
	{
		stay(TfaArea.NEXUS, 100);
		stay(TfaArea.BORK, 200);
		stay(TfaArea.NEXUS, 10);
		stay(TfaArea.DREADNOTH, 150);
		assertTrue(tracker.inRun());
		assertEquals(TfaArea.DREADNOTH, tracker.current());
		assertEquals(149, tracker.areaTicks(tick));
		assertEquals(459, tracker.runTicks(tick));
		assertEquals(110, tracker.ticksIn(TfaArea.NEXUS, tick));

		assertNull(stay(null, RaidTracker.OUTSIDE_TICKS_TO_END - 1));
		RaidTracker.Run run = stay(null, 1);
		assertNotNull(run);
		assertEquals(460, run.totalTicks);
		assertEquals(Arrays.asList(TfaArea.NEXUS, TfaArea.BORK, TfaArea.DREADNOTH), run.order);
		assertEquals(110, (int) run.ticksByArea.get(TfaArea.NEXUS));
		assertEquals(200, (int) run.ticksByArea.get(TfaArea.BORK));
		assertEquals(150, (int) run.ticksByArea.get(TfaArea.DREADNOTH));
		assertFalse(tracker.inRun());
	}

	@Test
	public void aShortGapOutsideDoesNotSplitTheRun()
	{
		stay(TfaArea.NEXUS, 20);
		assertNull(stay(null, 2));
		stay(TfaArea.XARTH, 30);
		assertTrue(tracker.inRun());
		assertEquals(Arrays.asList(TfaArea.NEXUS, TfaArea.XARTH), tracker.order());
		// The gap counts towards the run, not towards either area.
		assertEquals(51, tracker.runTicks(tick));
		assertEquals(20, tracker.ticksIn(TfaArea.NEXUS, tick));
	}

	@Test
	public void aNewRunStartsFromNothingAfterOneEnds()
	{
		stay(TfaArea.NEXUS, 20);
		assertNotNull(stay(null, RaidTracker.OUTSIDE_TICKS_TO_END));
		stay(TfaArea.NEXUS, 7);
		assertEquals(6, tracker.runTicks(tick));
		assertEquals(Arrays.asList(TfaArea.NEXUS), tracker.order());
	}

	@Test
	public void resetForgetsTheRunWithoutReportingIt()
	{
		stay(TfaArea.OCELLUS, 40);
		tracker.reset();
		assertNull(stay(null, 10));
		assertFalse(tracker.inRun());
	}

	@Test
	public void theSummaryListsAreasInTheOrderVisited()
	{
		stay(TfaArea.NEXUS, 100);
		stay(TfaArea.BORK, 200);
		RaidTracker.Run run = stay(null, RaidTracker.OUTSIDE_TICKS_TO_END);
		assertEquals("Fractured Archive run 3:00.0: Nexus 1:00.0, Bork 2:00.0", TfaHelperPlugin.summary(run));
	}
}
