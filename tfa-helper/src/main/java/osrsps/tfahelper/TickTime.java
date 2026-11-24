package osrsps.tfahelper;

/** Game ticks as the time they take: 600 ms each, shown as m:ss.s (h:mm:ss.s past an hour). */
final class TickTime
{
	static final int MS_PER_TICK = 600;

	private TickTime()
	{
	}

	static String format(int ticks)
	{
		long tenths = (long) ticks * MS_PER_TICK / 100;
		long seconds = tenths / 10;
		long minutes = seconds / 60;
		long hours = minutes / 60;
		if (hours > 0)
		{
			return String.format("%d:%02d:%02d.%d", hours, minutes % 60, seconds % 60, tenths % 10);
		}
		return String.format("%d:%02d.%d", minutes, seconds % 60, tenths % 10);
	}
}
