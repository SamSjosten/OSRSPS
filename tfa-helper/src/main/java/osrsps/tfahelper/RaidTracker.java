package osrsps.tfahelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Times one run of the raid from the areas you stand in, one game tick at a time. It knows nothing of the client, so
 * tests can feed it ticks directly.
 *
 * <p>A run starts on the first tick in any {@link TfaArea} and ends once you have been outside all of them for
 * {@link #OUTSIDE_TICKS_TO_END} ticks in a row: the lobby after a win or a wipe, a teleport out, or logging out. The
 * run's length stops at the first of those ticks, so the wait to be sure isn't counted.
 */
final class RaidTracker
{
	/**
	 * Ticks outside the raid before a run counts as over. A few, so that a tick where the player's tile isn't known
	 * (a scene still loading) doesn't split one run in two. Guessed: long enough, as no move inside the raid leaves
	 * the player outside every area for more than a tick or two.
	 */
	static final int OUTSIDE_TICKS_TO_END = 5;

	/** A finished run: its length and the ticks spent in each area, in the order you first went into them. */
	static final class Run
	{
		final int totalTicks;
		final Map<TfaArea, Integer> ticksByArea;
		final List<TfaArea> order;

		Run(int totalTicks, Map<TfaArea, Integer> ticksByArea, List<TfaArea> order)
		{
			this.totalTicks = totalTicks;
			this.ticksByArea = Collections.unmodifiableMap(new EnumMap<>(ticksByArea));
			this.order = Collections.unmodifiableList(new ArrayList<>(order));
		}
	}

	private final EnumMap<TfaArea, Integer> ticksByArea = new EnumMap<>(TfaArea.class);
	private final List<TfaArea> order = new ArrayList<>();
	private TfaArea current;
	private int runStart = -1;
	private int areaStart;
	private int outsideSince = -1;

	/**
	 * Takes the area you stood in on {@code tick} (null outside the raid). Ticks must go up by one each call, apart
	 * from gaps the client didn't tick through, which count towards the area you were in. Returns the run that ended
	 * on this tick, or null.
	 */
	Run tick(int tick, TfaArea area)
	{
		if (area == null)
		{
			return outside(tick);
		}
		outsideSince = -1;
		if (runStart < 0)
		{
			runStart = tick;
		}
		else if (current != null && area != current)
		{
			add(current, tick - areaStart);
		}
		if (area != current)
		{
			current = area;
			areaStart = tick;
			if (!order.contains(area))
			{
				order.add(area);
			}
		}
		return null;
	}

	private Run outside(int tick)
	{
		if (runStart < 0)
		{
			return null;
		}
		if (outsideSince < 0)
		{
			outsideSince = tick;
			if (current != null)
			{
				add(current, tick - areaStart);
				current = null;
			}
		}
		if (tick - outsideSince + 1 < OUTSIDE_TICKS_TO_END)
		{
			return null;
		}
		Run run = new Run(outsideSince - runStart, ticksByArea, order);
		reset();
		return run;
	}

	private void add(TfaArea area, int ticks)
	{
		ticksByArea.merge(area, ticks, Integer::sum);
	}

	/** Forgets the run in progress, if any, without reporting it. */
	void reset()
	{
		ticksByArea.clear();
		order.clear();
		current = null;
		runStart = -1;
		outsideSince = -1;
	}

	boolean inRun()
	{
		return runStart >= 0;
	}

	/** The area you are in now; null between areas or outside a run. */
	TfaArea current()
	{
		return current;
	}

	/** Ticks since you went into the area you are in, counting the tick you went in on as 0. */
	int areaTicks(int tick)
	{
		return current == null ? 0 : tick - areaStart;
	}

	/** Ticks since the run started; 0 outside a run. */
	int runTicks(int tick)
	{
		if (runStart < 0)
		{
			return 0;
		}
		return (outsideSince >= 0 ? outsideSince : tick) - runStart;
	}

	/** Ticks spent in each area you have left so far this run, plus the time in the one you are in now. */
	int ticksIn(TfaArea area, int tick)
	{
		int done = ticksByArea.getOrDefault(area, 0);
		return area == current ? done + areaTicks(tick) : done;
	}

	/** The areas of this run in the order you first went into them. */
	List<TfaArea> order()
	{
		return Collections.unmodifiableList(order);
	}
}
