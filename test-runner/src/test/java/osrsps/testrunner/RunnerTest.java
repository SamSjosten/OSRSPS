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

		// Each command is sent at once and waits for the server's answer.
		assertNull(cycle());
		assertEquals(List.of("chat ::keys on"), game.actions);
		runner.message("Dialogue shows keys");
		assertNull(cycle());
		assertEquals("chat ::tele 3222 3218", game.actions.get(1));
		runner.message("Teleported to 3222, 3218, 0");
		game.location = new Tile(3222, 3218, 0);
		assertNull(cycle());
		assertEquals("walk 3222 3220", game.actions.get(2));
		game.location = new Tile(3222, 3220, 0);
		assertNull(cycle());
		assertEquals("Talk-to Hans", game.actions.get(3));
		game.dialogue = npc("[hans.greeting]");
		assertNull(cycle());
		assertEquals("continue", game.actions.get(4));
		// The old dialogue is still up until the server answers: the runner waits rather than clicking again.
		assertNull(cycle());
		assertEquals(5, game.actions.size());
		game.dialogue = new Dialogue(Dialogue.Kind.OPTIONS, "Select an option", "...", List.of(
			new Dialogue.Option(1, "[hans.in_charge]"),
			new Dialogue.Option(5, "[hans.nothing]")));
		assertNull(cycle());
		assertEquals("option 5", game.actions.get(5));
		game.dialogue = new Dialogue(Dialogue.Kind.PLAYER, "test", "[hans.nothing]", null);
		assertNull(cycle());
		assertEquals("continue", game.actions.get(6));
		game.dialogue = null;
		Result r = cycle();
		assertNotNull(r);
		assertEquals(Result.PASS, r.result);
		assertEquals(12, r.steps.size());
		assertEquals("file", r.trigger);
		assertTrue(r.summary(), r.summary().startsWith("Teleport, walk and talk to Hans: pass, 12 steps"));
	}

	@Test
	public void walkRetriesUntilTheTileIsOnScreen() throws Exception
	{
		// Right after a teleport the camera may not show the tile yet.
		game.walkRefusals = 2;
		runner.start(script("{\"step\":\"walk\",\"x\":3200,\"y\":3202}"), "file");
		assertNull(cycle());
		assertNull(cycle());
		assertNull(cycle());
		assertEquals(List.of("walk 3200 3202", "walk 3200 3202", "walk 3200 3202"), game.actions);
		// Sent: no more clicks while the player walks.
		assertNull(cycle());
		assertEquals(3, game.actions.size());
		game.location = new Tile(3200, 3202, 0);
		assertEquals(Result.PASS, cycle().result);
	}

	@Test
	public void walkClicksAgainWhenThePlayerStopsShortOfTheTile() throws Exception
	{
		runner.start(script("{\"step\":\"walk\",\"x\":3200,\"y\":3202}"), "file");
		assertNull(cycle());
		assertEquals(1, game.actions.size());
		// The click landed on another tile: the player walks there and stops.
		game.location = new Tile(3200, 3190, 0);
		runner.gameTick();
		assertNull(cycle());
		for (int i = 1; i < Steps.Walk.STILL_TICKS; i++)
		{
			runner.gameTick();
			assertNull(cycle());
			assertEquals(1, game.actions.size());
		}
		runner.gameTick();
		assertNull(cycle());
		assertEquals(List.of("walk 3200 3202", "walk 3200 3202"), game.actions);
		game.location = new Tile(3200, 3202, 0);
		assertEquals(Result.PASS, cycle().result);
	}

	@Test
	public void assertLocationWaitsUntilTheClientDrawsThePlayerThere() throws Exception
	{
		runner.start(script("{\"step\":\"assertLocation\",\"x\":3222,\"y\":3218,\"timeoutMs\":100}"), "file");
		// The server has them on the tile, but the client still draws them where they were.
		game.location = new Tile(3222, 3218, 0);
		game.drawn = new Tile(3205, 3228, 0);
		Result r = null;
		for (int i = 0; i < 10 && r == null; i++)
		{
			r = cycle();
		}
		assertNotNull(r);
		assertEquals("step 1 (assert location 3222, 3218): timed out after 100 ms: at 3222, 3218, 0, but the client draws the player at 3205, 3228, 0", r.failure);

		runner.start(script("{\"step\":\"assertLocation\",\"x\":3222,\"y\":3218}"), "file");
		assertNull(cycle());
		game.drawn = null;
		assertEquals(Result.PASS, cycle().result);
	}

	@Test
	public void walkWaitsForTheClientToDrawThePlayerOnTheirTile() throws Exception
	{
		game.drawn = new Tile(3205, 3228, 0);
		runner.start(script("{\"step\":\"walk\",\"x\":3200,\"y\":3202}"), "file");
		assertNull(cycle());
		// The camera follows the drawn player, so a click now would land somewhere else: no click yet.
		assertTrue(game.actions.isEmpty());
		game.drawn = null;
		assertNull(cycle());
		assertEquals(List.of("walk 3200 3202"), game.actions);
		// The server has them there before the client has walked them over.
		game.location = new Tile(3200, 3202, 0);
		game.drawn = new Tile(3200, 3201, 0);
		assertNull(cycle());
		game.drawn = null;
		assertEquals(Result.PASS, cycle().result);
	}

	@Test
	public void walkSaysWhereTheClickSentThePlayer() throws Exception
	{
		runner.start(script("{\"step\":\"walk\",\"x\":3200,\"y\":3202,\"timeoutMs\":100}"), "file");
		assertNull(cycle());
		assertEquals(List.of("walk 3200 3202"), game.actions);
		// The client's flag went to another tile than the one clicked.
		game.destination = new Tile(3192, 3210, 0);
		Result r = null;
		for (int i = 0; i < 10 && r == null; i++)
		{
			r = cycle();
		}
		assertNotNull(r);
		assertEquals("step 1 (walk to 3200, 3202): timed out after 100 ms: at 3200, 3200, 0, heading for 3192, 3210, 0", r.failure);
	}

	@Test
	public void assertLocWaitsForTheLocWithItsOption() throws Exception
	{
		runner.start(script("{\"step\":\"assertLoc\",\"loc\":\"Door\",\"option\":\"Open\",\"x\":3207,\"y\":3217}"), "file");
		assertNull(cycle());
		game.locs.add("Door at 3207 3217");
		// A door there, but not a shut one.
		assertNull(cycle());
		game.locs.add("Door with Open at 3207 3217");
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals("there", r.steps.get(0).detail);
		assertTrue(game.actions.isEmpty());
	}

	@Test
	public void walkTimesOutWithWhyItCouldNotClick() throws Exception
	{
		game.walkRefusals = Integer.MAX_VALUE;
		runner.start(script("{\"step\":\"walk\",\"x\":3200,\"y\":3202,\"timeoutMs\":100}"), "file");
		Result r = null;
		for (int i = 0; i < 10 && r == null; i++)
		{
			r = cycle();
		}
		assertNotNull(r);
		assertEquals("step 1 (walk to 3200, 3202): timed out after 100 ms: tile 3200, 3202 is not on screen", r.failure);
	}

	@Test
	public void locOptionRetriesUntilTheLocIsInTheScene() throws Exception
	{
		game.locError = "no loc \"Staircase\" in the scene";
		runner.start(script("{\"step\":\"locOption\",\"loc\":\"Staircase\",\"option\":\"Climb-up\",\"x\":3205,\"y\":3208}"), "file");
		assertNull(cycle());
		assertTrue(game.actions.isEmpty());
		game.locError = null;
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals(List.of("Climb-up Staircase at 3205 3208"), game.actions);
		assertEquals("Climb-up on Staircase at 3205, 3208", r.steps.get(0).description);
	}

	@Test
	public void takeItemRetriesUntilTheItemIsThereThenTheInventoryShowsIt() throws Exception
	{
		game.takeError = "no item \"Knife\" at 3205, 3212 on the ground in the scene";
		runner.start(script("{\"step\":\"takeItem\",\"item\":\"Knife\",\"x\":3205,\"y\":3212},"
			+ "{\"step\":\"assertInventory\",\"item\":\"Knife\",\"count\":1}"), "file");
		assertNull(cycle());
		assertTrue(game.actions.isEmpty());
		game.takeError = null;
		assertNull(cycle());
		assertEquals(List.of("Take Knife at 3205 3212"), game.actions);
		// Not yet: the server hasn't answered.
		assertNull(cycle());
		game.inventory.put("Knife", 1);
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals("take Knife at 3205, 3212", r.steps.get(0).description);
		assertEquals("1 in the inventory", r.steps.get(1).detail);
	}

	@Test
	public void assertInventoryWantsTheExactCountOrAnyWithoutOne() throws Exception
	{
		game.inventory = null;
		runner.start(script("{\"step\":\"assertInventory\",\"itemId\":1511,\"timeoutMs\":40}"), "file");
		Result r = null;
		for (int i = 0; i < 10 && r == null; i++)
		{
			r = cycle();
		}
		assertEquals("step 1 (assert inventory has some item 1511): timed out after 40 ms: no inventory yet", r.failure);

		game.inventory = new java.util.HashMap<>();
		game.inventory.put("item 1511", 3);
		runner.start(script("{\"step\":\"assertInventory\",\"itemId\":1511}"), "file");
		assertEquals(Result.PASS, cycle().result);
		runner.start(script("{\"step\":\"assertInventory\",\"itemId\":1511,\"count\":2,\"timeoutMs\":40}"), "file");
		r = null;
		for (int i = 0; i < 10 && r == null; i++)
		{
			r = cycle();
		}
		assertEquals("step 1 (assert inventory has 2 x item 1511): timed out after 40 ms: 3 in the inventory", r.failure);
		runner.start(script("{\"step\":\"assertInventory\",\"item\":\"Knife\",\"count\":0}"), "file");
		assertEquals(Result.PASS, cycle().result);
	}

	@Test
	public void finishDialogueClicksThroughWhateverIsOpen() throws Exception
	{
		runner.start(script("{\"step\":\"finishDialogue\"}"), "file");
		// Nothing open: done at once.
		assertEquals("no dialogue open", cycle().steps.get(0).detail);

		game.dialogue = npc("[man.help]");
		runner.start(script("{\"step\":\"finishDialogue\"}"), "file");
		assertNull(cycle());
		assertEquals(List.of("continue"), game.actions);
		// Waits for the server rather than clicking the same page again.
		assertNull(cycle());
		assertEquals(1, game.actions.size());
		game.dialogue = new Dialogue(Dialogue.Kind.OPTIONS, "Select an option", "...", List.of(
			new Dialogue.Option(1, "[man.trade]"),
			new Dialogue.Option(2, "[man.quest]")));
		assertNull(cycle());
		assertEquals("option 1", game.actions.get(1));
		game.dialogue = new Dialogue(Dialogue.Kind.PLAYER, "test", "[man.trade]", null);
		assertNull(cycle());
		assertEquals("continue", game.actions.get(2));
		game.dialogue = null;
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals("closed after 3 clicks", r.steps.get(0).detail);
	}

	@Test
	public void finishDialogueClicksAgainWhenTheNextPageLooksTheSame() throws Exception
	{
		game.dialogue = npc("[man.help]");
		runner.start(script("{\"step\":\"finishDialogue\"}"), "file");
		assertNull(cycle());
		assertEquals(1, game.actions.size());
		// The server's next page has the same text; until a few ticks pass it could still be the clicked one.
		for (int i = 1; i < Steps.FinishDialogue.SAME_PAGE_TICKS; i++)
		{
			runner.gameTick();
			assertNull(cycle());
			assertEquals(1, game.actions.size());
		}
		runner.gameTick();
		assertNull(cycle());
		assertEquals(List.of("continue", "continue"), game.actions);
		game.dialogue = null;
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals("closed after 2 clicks", r.steps.get(0).detail);
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
	public void chatPressesEnterAgainWhenTheLineStays() throws Exception
	{
		game.chatPending = true;
		runner.start(script("{\"step\":\"chat\",\"text\":\"one\",\"timeoutMs\":100000}"), "file");
		assertNull(cycle());
		runner.gameTick();
		assertNull(cycle());
		// One tick is too soon; the second tick brings another Enter.
		assertEquals(List.of("chat one"), game.actions);
		runner.gameTick();
		assertNull(cycle());
		assertEquals(List.of("chat one", "enter again"), game.actions);
		for (int i = 0; i < 20; i++)
		{
			runner.gameTick();
			assertNull(cycle());
		}
		// Five presses after the first, then the step only waits for its timeout.
		assertEquals(6, game.actions.size());
		game.chatPending = false;
		assertEquals(Result.PASS, cycle().result);

		game.chatPending = true;
		runner.start(script("{\"step\":\"chat\",\"text\":\"two\",\"timeoutMs\":40}"), "file");
		assertNull(cycle());
		runner.gameTick();
		runner.gameTick();
		assertNull(cycle());
		now.addAndGet(100);
		Result r = cycle();
		assertEquals(Result.FAIL, r.result);
		assertEquals("timed out after 40 ms: the chat input still holds the text: the client has not taken the Enter key after 2 presses (fake focus)", r.steps.get(0).detail);
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
	public void assertInterfaceWaitsForTheInterfaceToOpenAndThenToClose() throws Exception
	{
		runner.start(script(
			"{\"step\":\"assertInterface\",\"interfaceId\":672,\"timeoutMs\":1000},"
				+ "{\"step\":\"clickWidget\",\"interfaceId\":672,\"component\":8,\"timeoutMs\":1000},"
				+ "{\"step\":\"assertInterface\",\"interfaceId\":672,\"absent\":true,\"timeoutMs\":1000}"), "file");
		assertNull(cycle());
		assertNull(cycle());
		game.openInterfaces.add(672);
		assertNull(cycle());
		assertNull(cycle());
		assertNull(cycle());
		assertEquals(List.of("click 672:8 op 1"), game.actions);
		game.openInterfaces.clear();
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals("assert interface 672 closed", r.steps.get(2).description);
	}

	@Test
	public void assertInterfaceTimesOutSayingTheInterfaceIsShut() throws Exception
	{
		runner.start(script("{\"step\":\"assertInterface\",\"interfaceId\":672,\"timeoutMs\":40}"), "file");
		assertNull(cycle());
		assertNull(cycle());
		Result r = cycle();
		assertEquals(Result.FAIL, r.result);
		assertTrue(r.steps.get(0).detail, r.steps.get(0).detail.contains("interface 672 is not open"));
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

	@Test
	public void hitsplatsCountOnlyAfterTheLastActionAndOnTheRightActor() throws Exception
	{
		runner.start(script("{\"step\":\"npcOption\",\"npc\":\"Man\",\"option\":\"Attack\"},"
			+ "{\"step\":\"assertHitsplat\",\"npc\":\"Man\",\"amount\":1},"
			+ "{\"step\":\"assertHitsplat\",\"self\":true}"), "file");
		// A splat from before the click doesn't count.
		runner.hitsplat(7, "Man", 16, 1);
		assertNull(cycle());
		assertEquals(List.of("Attack Man"), game.actions);
		// Nor one on another man (another player's fight), nor a miss when 1 is wanted; and the man has to be in view.
		runner.hitsplat(9, "Man", 16, 1);
		runner.hitsplat(7, "Man", 12, 0);
		assertNull(cycle());
		game.npcIndexes.put("Man", 7);
		assertNull(cycle());
		runner.hitsplat(7, "Man", 16, 1);
		assertNull(cycle());
		runner.hitsplat(Run.SELF, null, 16, 2);
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals("assert hitsplat of 1 on Man", r.steps.get(1).description);
		assertEquals("hitsplat 1 on Man #7 (type 16)", r.steps.get(1).detail);
		assertEquals("assert hitsplat on you", r.steps.get(2).description);
		assertEquals("hitsplat 2 on you (type 16)", r.steps.get(2).detail);
	}

	@Test
	public void npcHealthWaitsForTheBarThenChecksTheBounds() throws Exception
	{
		runner.start(script("{\"step\":\"assertNpcHealth\",\"npc\":\"Man\",\"max\":99,\"timeoutMs\":100}"), "file");
		game.npcHealth = new Game.Health(-1, -1);
		assertNull(cycle());
		game.npcIndexes.put("Man", 7);
		assertNull(cycle());
		game.npcHealth = new Game.Health(30, 30);
		assertNull(cycle());
		game.npcHealth = new Game.Health(21, 30);
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals("assert Man health at most 99%", r.steps.get(0).description);
		assertEquals("Man at 70% (21/30)", r.steps.get(0).detail);

		game.npcIndexes.put("npc 3106", 8);
		runner.start(script("{\"step\":\"assertNpcHealth\",\"npcId\":3106,\"min\":80,\"max\":100,\"timeoutMs\":40}"), "file");
		r = null;
		for (int i = 0; i < 10 && r == null; i++)
		{
			r = cycle();
		}
		assertEquals("step 1 (assert NPC 3106 health 80-100%): timed out after 40 ms: NPC 3106 at 70% (21/30)", r.failure);
	}

	@Test
	public void statWantsAtLeastTheLevelAndXp() throws Exception
	{
		runner.start(script("{\"step\":\"assertStat\",\"skill\":\"attack\",\"xp\":28},"
			+ "{\"step\":\"assertStat\",\"skill\":\"Hitpoints\",\"level\":10,\"xp\":1154}"), "file");
		assertNull(cycle());
		game.levels.put("Attack", 1);
		game.xps.put("Attack", 24);
		assertNull(cycle());
		game.xps.put("Attack", 28);
		game.levels.put("Hitpoints", 10);
		game.xps.put("Hitpoints", 1163);
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals("assert Attack xp 28+", r.steps.get(0).description);
		assertEquals("Attack level 1, 28 xp", r.steps.get(0).detail);
		assertEquals("assert Hitpoints level 10+ xp 1154+", r.steps.get(1).description);
	}

	@Test
	public void mouseClickWaitsForTheComponent() throws Exception
	{
		runner.start(script("{\"step\":\"mouseClick\",\"interfaceId\":116,\"component\":6,\"slot\":0}"), "file");
		game.clickError = "116:6 slot 0 is not showing";
		assertNull(cycle());
		game.clickError = null;
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals("mouse click on 116:6 slot 0", r.steps.get(0).description);
		assertEquals(List.of("116:6 slot 0"), game.mouseClicks);
	}

	@Test
	public void clientScriptRunsOnceWithItsArguments() throws Exception
	{
		runner.start(script("{\"step\":\"runClientScript\",\"id\":9231,\"args\":[7602271,50,116,\"x\"]}"), "file");
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals("run client script 9231 [7602271, 50, 116, x]", r.steps.get(0).description);
		assertEquals(List.of("9231 [7602271, 50, 116, x]"), game.scriptsRun);
	}

	@Test
	public void varsWantTheServerAndTheClientToAgree() throws Exception
	{
		runner.start(script("{\"step\":\"assertVarbit\",\"id\":13131,\"value\":1},"
			+ "{\"step\":\"assertVarp\",\"id\":1107,\"value\":2,\"timeoutMs\":40}"), "file");
		assertNull(cycle());
		// The client flipped its own copy; the server hasn't yet.
		game.varbits.put(13131, new int[] {0, 1});
		assertNull(cycle());
		game.varbits.put(13131, new int[] {1, 1});
		game.varps.put(1107, new int[] {0, 2});
		Result r = null;
		for (int i = 0; i < 10 && r == null; i++)
		{
			r = cycle();
		}
		assertEquals("assert varbit 13131 = 1", r.steps.get(0).description);
		assertEquals("varbit 13131: server 1, client 1", r.steps.get(0).detail);
		assertEquals("step 2 (assert varp 1107 = 2): timed out after 40 ms: varp 1107: server 0, client 2", r.failure);
	}

	@Test
	public void weightWaitsForTheServersNumber() throws Exception
	{
		runner.start(script("{\"step\":\"assertWeight\",\"kg\":-7},{\"step\":\"assertWeight\",\"kg\":3,\"timeoutMs\":40}"), "file");
		game.weight = 19;
		assertNull(cycle());
		game.weight = -7;
		Result r = null;
		for (int i = 0; i < 10 && r == null; i++)
		{
			r = cycle();
		}
		assertEquals("assert weight -7 kg", r.steps.get(0).description);
		assertEquals("weight -7 kg", r.steps.get(0).detail);
		assertEquals("step 2 (assert weight 3 kg): timed out after 40 ms: weight -7 kg", r.failure);
	}

	@Test
	public void groundItemWaitsForTheItemOrForItToGo() throws Exception
	{
		runner.start(script("{\"step\":\"assertGroundItem\",\"item\":\"Bones\",\"x\":3213,\"y\":3210},"
			+ "{\"step\":\"assertGroundItem\",\"itemId\":526,\"absent\":true}"), "file");
		assertNull(cycle());
		game.groundItems.add("Bones at 3213 3210");
		game.groundItems.add("item 526");
		assertNull(cycle());
		game.groundItems.remove("item 526");
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals("assert Bones at 3213, 3210 on the ground", r.steps.get(0).description);
		assertEquals("Bones at 3213, 3210 is on the ground", r.steps.get(0).detail);
		assertEquals("assert no item 526 on the ground", r.steps.get(1).description);
		assertEquals("no item 526 on the ground", r.steps.get(1).detail);
	}

	@Test
	public void groundItemAbsenceNeedsTheTileInTheScene() throws Exception
	{
		game.tileLoaded = false;
		runner.start(script("{\"step\":\"assertGroundItem\",\"item\":\"Bones\",\"x\":3213,\"y\":3210,\"absent\":true,\"timeoutMs\":40}"), "file");
		Result r = null;
		for (int i = 0; i < 10 && r == null; i++)
		{
			r = cycle();
		}
		assertEquals("step 1 (assert no Bones at 3213, 3210 on the ground): timed out after 40 ms: tile 3213, 3210 is not in the loaded scene", r.failure);
		game.tileLoaded = true;
		runner.start(script("{\"step\":\"assertGroundItem\",\"item\":\"Bones\",\"x\":3213,\"y\":3210,\"absent\":true}"), "file");
		assertEquals(Result.PASS, cycle().result);
	}

	@Test
	public void animationCountsOnlyAfterTheLastActionOnTheRightActor() throws Exception
	{
		runner.start(script("{\"step\":\"npcOption\",\"npc\":\"Man\",\"option\":\"Attack\"},"
			+ "{\"step\":\"assertAnimation\",\"id\":422},"
			+ "{\"step\":\"assertAnimation\",\"id\":424,\"npc\":\"Man\"}"), "file");
		game.npcIndexes.put("Man", 7);
		runner.animation(Run.SELF, null, 422);
		assertNull(cycle());
		runner.animation(7, "Man", 422);
		assertNull(cycle());
		runner.animation(Run.SELF, null, 422);
		assertNull(cycle());
		// Another man's block doesn't count; the one the player fights does.
		runner.animation(9, "Man", 424);
		assertNull(cycle());
		runner.animation(7, "Man", 424);
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals("assert animation 422 on you", r.steps.get(1).description);
		assertEquals("animation 422 on you", r.steps.get(1).detail);
		assertEquals("animation 424 on Man #7", r.steps.get(2).detail);
	}

	@Test
	public void castOnNpcRetriesUntilItCanThenSpotanimsCountOnlyAfterIt() throws Exception
	{
		game.castError = "no NPC \"Man\" in view";
		runner.start(script("{\"step\":\"castOnNpc\",\"interfaceId\":218,\"component\":19,\"npc\":\"Man\"},"
			+ "{\"step\":\"assertSpotanim\",\"id\":99},"
			+ "{\"step\":\"assertSpotanim\",\"ids\":[101,85],\"npc\":\"Man\"}"), "file");
		game.npcIndexes.put("Man", 7);
		// One from before the cast doesn't count.
		runner.spotanim(Run.SELF, null, 99);
		assertNull(cycle());
		assertEquals("cast 218:19 on Man", game.actions.get(game.actions.size() - 1));
		game.castError = null;
		assertNull(cycle());
		runner.spotanim(Run.SELF, null, 99);
		assertNull(cycle());
		runner.spotanim(9, "Man", 101);
		assertNull(cycle());
		runner.spotanim(7, "Man", 85);
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals("cast 218:19 on Man", r.steps.get(0).description);
		assertEquals("assert spotanim 101 or 85 on Man", r.steps.get(2).description);
		assertEquals("spotanim 99 on you", r.steps.get(1).detail);
		assertEquals("spotanim 85 on Man #7", r.steps.get(2).detail);
	}

	@Test
	public void itemOptionRetriesUntilTheItemIsThereThenTheSlotShowsIt() throws Exception
	{
		game.clickError = "no \"Bronze axe\" in the inventory";
		runner.start(script("{\"step\":\"itemOption\",\"item\":\"Bronze axe\",\"option\":\"Wield\"},"
			+ "{\"step\":\"assertEquipment\",\"slot\":\"weapon\",\"item\":\"Bronze axe\"},"
			+ "{\"step\":\"assertEquipment\",\"slot\":\"shield\",\"empty\":true}"), "file");
		assertNull(cycle());
		assertTrue(game.actions.isEmpty());
		game.clickError = null;
		assertNull(cycle());
		assertEquals(List.of("Wield Bronze axe in the inventory"), game.actions);
		game.itemNames.put(1351, "Bronze axe");
		game.worn.put(3, 1351);
		// Worn, but the model doesn't show it yet.
		game.model.put(3, -1);
		assertNull(cycle());
		game.model.remove(3);
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals("Wield Bronze axe in the inventory", r.steps.get(0).description);
		assertEquals("assert weapon slot holds Bronze axe", r.steps.get(1).description);
		assertEquals("worn Bronze axe (1351), model shows Bronze axe (1351)", r.steps.get(1).detail);
		assertEquals("assert shield slot is empty", r.steps.get(2).description);
		assertEquals("worn nothing, model shows nothing", r.steps.get(2).detail);
	}

	@Test
	public void equipmentSaysWhatIsWorn() throws Exception
	{
		game.worn.put(13, 884);
		game.itemNames.put(884, "Iron arrow");
		runner.start(script("{\"step\":\"assertEquipment\",\"slot\":\"ammo\",\"itemId\":882,\"timeoutMs\":40}"), "file");
		Result r = null;
		for (int i = 0; i < 10 && r == null; i++)
		{
			r = cycle();
		}
		// Ammo isn't on the model, so only the worn inventory is said.
		assertEquals("step 1 (assert ammo slot holds item 882): timed out after 40 ms: worn Iron arrow (884)", r.failure);
	}

	@Test
	public void wornOptionAndClickWidgetRetryUntilTheyCanClick() throws Exception
	{
		game.clickError = "the equipment tab is not open";
		runner.start(script("{\"step\":\"wornOption\",\"slot\":\"Weapon\",\"option\":\"Remove\"},"
			+ "{\"step\":\"clickWidget\",\"interfaceId\":387,\"component\":1},"
			+ "{\"step\":\"clickWidget\",\"interfaceId\":85,\"component\":0,\"slot\":4,\"op\":1}"), "file");
		assertNull(cycle());
		game.clickError = null;
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals(List.of("Remove slot 3", "click 387:1 op 1", "click 85:0 slot 4 op 1"), game.actions);
		assertEquals("Remove on the weapon slot", r.steps.get(0).description);
		assertEquals("click op 1 on 387:1", r.steps.get(1).description);
		assertEquals("click op 1 on 85:0 slot 4", r.steps.get(2).description);
	}

	@Test
	public void widgetTextWaitsForTheInterfaceAndTheText() throws Exception
	{
		runner.start(script("{\"step\":\"assertWidgetText\",\"interfaceId\":84,\"component\":25,\"contains\":\"Slash: +4\"},"
			+ "{\"step\":\"assertWidgetText\",\"interfaceId\":593,\"contains\":\"Axe\",\"timeoutMs\":40}"), "file");
		assertNull(cycle());
		game.widgetTexts.put("84:25", "Slash: +0");
		assertNull(cycle());
		game.widgetTexts.put("84:25", "Slash: +4");
		game.widgetTexts.put("593", "Bronze axe / Combat Lvl: 3 / Category: Unarmed");
		Result r = null;
		for (int i = 0; i < 10 && r == null; i++)
		{
			r = cycle();
		}
		assertEquals("\"Slash: +4\"", r.steps.get(0).detail);
		assertEquals("assert 84:25 says \"Slash: +4\"", r.steps.get(0).description);
		assertEquals("step 2 (assert 593 says \"Axe\"): timed out after 40 ms: text is \"Bronze axe / Combat Lvl: 3 / Category: Unarmed\"", r.failure);
	}

	@Test
	public void poseAndLogout() throws Exception
	{
		runner.start(script("{\"step\":\"assertPose\",\"idle\":2561},"
			+ "{\"step\":\"logout\"},"
			+ "{\"step\":\"waitForLogin\"},"
			+ "{\"step\":\"assertPose\",\"idle\":2561}"), "file");
		assertNull(cycle());
		game.idlePose = 2561;
		assertNull(cycle());
		assertEquals(List.of("logout"), game.actions);
		game.loggedIn = true;
		Result r = cycle();
		assertEquals(Result.PASS, r.result);
		assertEquals("stands in 2561", r.steps.get(0).detail);
		assertEquals("logged out", r.steps.get(1).detail);
	}
}
