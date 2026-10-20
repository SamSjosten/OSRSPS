package osrsps.tickrecorder;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class RedactorTest
{
	@Test
	public void replacesOtherPlayersAndSelf()
	{
		assertEquals("{player} has defeated {self}!",
			Redactor.scrub("Zezima has defeated Sam!", "Sam", Collections.singletonList("Zezima")));
	}

	@Test
	public void matchesNamesWithEitherKindOfSpace()
	{
		assertEquals("Trade with {player} and {player}",
			Redactor.scrub("Trade with Iron Man and Iron Man", null, Arrays.asList("Iron Man")));
	}

	@Test
	public void replacesLongerNamesFirst()
	{
		assertEquals("{player} met {player} and {self}",
			Redactor.scrub("Bob Smith met Bob and Bobby", "Bobby", Arrays.asList("Bob", "Bob Smith")));
	}

	@Test
	public void leavesTextWithoutNamesAlone()
	{
		assertEquals("You eat the shark.", Redactor.scrub("You eat the shark.", "Sam", Collections.emptyList()));
		assertEquals(null, Redactor.scrub(null, "Sam", Collections.emptyList()));
	}

	@Test
	public void removesColourTags()
	{
		assertEquals("Goblin  (level-2)", Redactor.removeTags("<col=ffff00>Goblin<col=ff00>  (level-2)"));
	}
}
