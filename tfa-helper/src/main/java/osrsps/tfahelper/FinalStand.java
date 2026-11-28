package osrsps.tfahelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Ocellus's final stand: a loop of {@link #LOOP} ticks that starts each time he rises ({@link #ANIM_RISE}). It tracks
 * where in the loop he is and what comes next, from the tick he rose. It knows nothing of the client, so tests feed it
 * ticks directly.
 *
 * <p>The loop's events are our server's ({@code osrsps.game.raid.Ocellus}, raid step 27: FINAL_LOOP, FINAL_LAND,
 * FINAL_BLASTS, FINAL_DEFLECT_RANGE, FINAL_ARENA_END, FINAL_RIFTS, SOUL_AT), which come from the tsps capture notes and
 * Sam's recordings. Sam's recordings of 9 and 10 November 2026 count three spike pulses, two ticks apart, at each blast
 * (24, 26, 28 and 52, 54, 56); the server has one blast at 24 and 52 for now, so this follows the server.
 */
final class FinalStand
{
	/** {@code Ocellus.ANIM_DEFLECT_IN}: he rises, tick 0 of each loop. */
	static final int ANIM_RISE = 14863;
	static final int LOOP = 60;
	/** Ticks past the loop's end with no new rise before the tracker lets go: he died, or you left. */
	static final int LOST_AFTER = 5;

	/** One event in the loop, on loop tick {@link #at}. */
	static final class Event
	{
		final int at;
		final String label;

		Event(int at, String label)
		{
			this.at = at;
			this.label = label;
		}
	}

	static final List<Event> EVENTS = Collections.unmodifiableList(Arrays.asList(
		new Event(0, "Rise (deflect melee)"),
		new Event(3, "Lifeforce"),
		new Event(21, "Land"),
		new Event(24, "Blast"),
		new Event(31, "Deflect ranged"),
		new Event(34, "Lifeforce"),
		new Event(50, "Arena ends"),
		new Event(52, "Blast"),
		new Event(59, "Rifts")
	));

	private int rose = -1;

	/** He rose on {@code tick}: a new loop starts. */
	void rise(int tick)
	{
		rose = tick;
	}

	void reset()
	{
		rose = -1;
	}

	/** Whether a loop is running on {@code tick}; lets go of one that ran out without a new rise. */
	boolean active(int tick)
	{
		if (rose >= 0 && tick - rose >= LOOP + LOST_AFTER)
		{
			rose = -1;
		}
		return rose >= 0;
	}

	/** Where in the loop {@code tick} is, 0 to {@link #LOOP} - 1. Only meaningful while {@link #active}. */
	int loopTick(int tick)
	{
		return Math.floorMod(tick - rose, LOOP);
	}

	/** The next event after loop tick {@code at}, wrapping round to the next loop's rise. */
	static Event next(int at)
	{
		for (Event event : EVENTS)
		{
			if (event.at > at)
			{
				return event;
			}
		}
		return EVENTS.get(0);
	}

	/** Ticks from loop tick {@code at} to {@code event}, wrapping round. */
	static int ticksUntil(int at, Event event)
	{
		return Math.floorMod(event.at - at, LOOP) == 0 ? LOOP : Math.floorMod(event.at - at, LOOP);
	}

	/** Blasts this loop up to and including loop tick {@code at}. */
	static int blastsSoFar(int at)
	{
		int count = 0;
		for (Event event : EVENTS)
		{
			if (event.label.equals("Blast") && event.at <= at)
			{
				count++;
			}
		}
		return count;
	}
}
