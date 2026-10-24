package osrsps.testrunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class RunnerTest
{
	private final FakeGame game = new FakeGame();
	private final AtomicLong now = new AtomicLong(1_000_000);
	private final Runner runner = new Runner(game, now::get, () -> Arrays.asList("test", "test2"));

	private static Script script(String steps) throws Script.Invalid
	{
		return Script.parse("t.json", "{\"account\":\"test\",\"steps\":[" + steps + "]}");
	}

	/** Polls once and moves the clock on a client cycle. */
	private Result cycle()
	{
		Result r = runner.poll();
		now.addAndGet(20);
		return r;
	}

	private static Dialogue npc(String text)
	{
		return new Dialogue(Dialogue.Kind.NPC, "Hans", text, null);
	}

	@Test
	public void runsTheHansDemo() throws Exception
	{
		String json = new String(Files.readAllBytes(Paths.get("examples/hans-demo.json")), StandardCharsets.UTF_8);
		runner.start(Script.parse("hans-demo.json", json), "file");

		// The command is sent at once and waits for the server's answer.
		assertNull(cycle());
		assertEquals(List.of("chat ::tele 3222 3218"), game.actions);
		runner.message("Teleported to 3222, 3218, 0");
		game.location = new Tile(3222, 3218, 0);
		assertNull(cycle());
		assertEquals("walk 3222 3220", game.actions.get(1));
		game.location = new Tile(3222, 3220, 0);
		assertNull(cycle());
		assertEquals("Talk-to Hans", game.actions.get(2));
		game.dialogue = npc("Hello. What are you doing here?");
		assertNull(cycle());
		assertEquals("continue", game.actions.get(3));
		// The old dialogue is still up until the server answers: the runner waits rather than clicking again.
		assertNull(cycle());
		assertEquals(4, game.actions.size());
		game.dialogue = new Dialogue(Dialogue.Kind.OPTIONS, "Select an option", "...", List.of(
			new Dialogue.Option(1, "I'm looking for whoever is in charge of this place."),
			new Dialogue.Option(5, "Nothing.")));
		assertNull(cycle());
		assertEquals("option 5", game.actions.get(4));
		game.dialogue = new Dialogue(Dialogue.Kind.PLAYER, "test", "Nothing.", null);
		assertNull(cycle());
		assertEquals("continue", game.actions.get(5));
		game.dialogue = null;
		Result r = cycle();
		assertNotNull(r);
		assertEquals(Result.PASS, r.result);
		assertEquals(11, r.steps.size());
		assertEquals("file", r.trigger);
		assertTrue(r.summary(), r.summary().startsWith("Teleport, walk and talk to Hans: pass, 11 steps"));
	}

	@Test
	public void timesOutWithWhatItSaw() throws Exception
	{
		runner.start(script("{\"step\":\"assertLocation\",\"x\":3222,\"y\":3218,\"timeoutMs\":100},{\"step\":\"assertNoDialogue\"}"), "file");
		Result r = null;
		for (int i = 0; i < 10 && r == null; i++)
		{
			r = cycle();
		}
		assertNotNull(r);
		assertEquals(Result.FAIL, r.result);
		assertEquals("step 1 (assert location 3222, 3218): timed out after 100 ms: at 3200, 3200, 0", r.failure);
		assertEquals("fail", r.steps.get(0).outcome);
		assertEquals("skipped", r.steps.get(1).outcome);
	}

	@Test
	public void messagesCountOnlyAfterTheLastAction() throws Exception
	{
		runner.start(script("{\"step\":\"chat\",\"text\":\"hi\"},{\"step\":\"assertMessage\",\"contains\":\"message\",\"timeoutMs\":100}"), "file");
		runner.message("old message");
		assertNull(cycle());
		runner.message("new message");
		Result r = cycle();
		// Only what came after the chat step acted counts.
		assertEquals(Result.PASS, r.result);
		assertEquals("message \"new message\"", r.steps.get(1).detail);

		runner.start(script("{\"step\":\"assertMessage\",\"contains\":\"x\",\"timeoutMs\":40}"), "file");
		assertNull(cycle());
		runner.message("<col=ff0000>x</col> marks the spot");
		r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals("message \"x marks the spot\"", r.steps.get(0).detail);
	}

	@Test
	public void chatWaitsUntilTheLineHasGone() throws Exception
	{
		game.chatPending = true;
		runner.start(script("{\"step\":\"chat\",\"text\":\"one\"},{\"step\":\"chat\",\"text\":\"two\"}"), "file");
		assertNull(cycle());
		assertNull(cycle());
		// The second line must not overwrite the first while it is still in the chat input.
		assertEquals(List.of("chat one"), game.actions);
		game.chatPending = false;
		assertEquals(Result.PASS, cycle().result);
		assertEquals(List.of("chat one", "chat two"), game.actions);
	}

	@Test
	public void refusesAnotherAccount() throws Exception
	{
		game.name = "Zezima";
		runner.start(script("{\"step\":\"waitForLogin\"}"), "login");
		Result r = cycle();
		assertEquals(Result.FAIL, r.result);
		assertTrue(r.failure, r.failure.contains("logged in as \"Zezima\""));
		assertTrue(game.actions.isEmpty());
	}

	@Test
	public void refusesAnAccountNotAllowed() throws Exception
	{
		game.name = "main";
		runner.start(Script.parse("t.json", "{\"account\":\"main\",\"steps\":[{\"step\":\"chat\",\"text\":\"hi\"}]}"), "file");
		Result r = cycle();
		assertEquals(Result.FAIL, r.result);
		assertTrue(r.failure, r.failure.contains("not in the plugin's test accounts"));
		assertTrue(game.actions.isEmpty());
	}

	@Test
	public void accountNamesCompareAsTheGameDoes()
	{
		assertEquals(Runner.normalise("Test two"), Runner.normalise("test_two"));
	}

	@Test
	public void waitsForALogin() throws Exception
	{
		game.loggedIn = false;
		runner.start(script("{\"step\":\"waitForLogin\"},{\"step\":\"chat\",\"text\":\"hi\"}"), "file");
		assertNull(cycle());
		assertNull(cycle());
		assertTrue(game.actions.isEmpty());
		game.loggedIn = true;
		assertEquals(Result.PASS, cycle().result);
		assertEquals(List.of("chat hi"), game.actions);
	}

	@Test
	public void npcOptionRetriesUntilTheNpcIsInView() throws Exception
	{
		game.npcError = "no NPC \"Hans\" in view";
		runner.start(script("{\"step\":\"npcOption\",\"npc\":\"Hans\",\"option\":\"Talk-to\",\"timeoutMs\":1000}"), "file");
		assertNull(cycle());
		assertNull(cycle());
		game.npcError = null;
		assertEquals(Result.PASS, cycle().result);
		assertEquals(List.of("Talk-to Hans"), game.actions);
	}

	@Test
	public void chooseOptionFailsWhenTheOptionIsMissing() throws Exception
	{
		game.dialogue = new Dialogue(Dialogue.Kind.OPTIONS, "Select an option", "Select an option / Yes", List.of(new Dialogue.Option(1, "Yes")));
		runner.start(script("{\"step\":\"chooseOption\",\"text\":\"No\"}"), "file");
		Result r = cycle();
		assertEquals(Result.FAIL, r.result);
		assertTrue(r.failure, r.failure.contains("no option containing \"No\""));
	}

	@Test
	public void waitTicksCountsGameTicks() throws Exception
	{
		runner.start(script("{\"step\":\"waitTicks\",\"ticks\":2}"), "file");
		assertNull(cycle());
		runner.gameTick();
		assertNull(cycle());
		runner.gameTick();
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals(2, r.ticks);
	}

	@Test
	public void abortFailsTheCurrentStep() throws Exception
	{
		runner.start(script("{\"step\":\"assertNoDialogue\"},{\"step\":\"assertNoDialogue\"}"), "file");
		game.dialogue = npc("hi");
		assertNull(cycle());
		Result r = runner.abort("the plugin was turned off");
		assertEquals(Result.FAIL, r.result);
		assertEquals("step 1 (assert no dialogue): the plugin was turned off", r.failure);
		assertNull(runner.abort("again"));
	}
}
