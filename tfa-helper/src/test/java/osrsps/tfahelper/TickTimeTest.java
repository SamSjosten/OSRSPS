package osrsps.tfahelper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TickTimeTest
{
	@Test
	public void ticksAreSixHundredMillisecondsEach()
	{
		assertEquals("0:00.0", TickTime.format(0));
		assertEquals("0:00.6", TickTime.format(1));
		assertEquals("0:01.2", TickTime.format(2));
		assertEquals("1:00.0", TickTime.format(100));
		assertEquals("22:14.4", TickTime.format(2224));
		assertEquals("1:00:00.0", TickTime.format(6000));
	}
}
