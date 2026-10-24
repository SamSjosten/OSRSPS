package osrsps.testrunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class ScriptTest
{
	@Test
	public void readsTheHansDemo() throws Exception
	{
		String json = new String(Files.readAllBytes(Paths.get("examples/hans-demo.json")), StandardCharsets.UTF_8);
		Script s = Script.parse("hans-demo.json", json);
		assertEquals("hans-demo", s.id);
		assertEquals("test", s.account);
		assertFalse(s.runOnLogin);
		assertEquals(11, s.steps.size());
		assertEquals("waitForLogin", s.steps.get(0).type);
		assertEquals(Script.LOGIN_TIMEOUT_MS, s.steps.get(0).timeoutMs);
		assertEquals(20_000, s.steps.get(5).timeoutMs);
		assertEquals(Script.DEFAULT_TIMEOUT_MS, s.steps.get(6).timeoutMs);
	}

	@Test
	public void commandAddsTheColons() throws Exception
	{
		Script s = Script.parse("a.json", "{\"account\":\"test\",\"steps\":[{\"step\":\"command\",\"text\":\"pos\"}]}");
		assertEquals("::pos", ((Steps.Chat) s.steps.get(0)).text);
	}

	@Test
	public void scriptTimeoutIsTheDefaultForItsSteps() throws Exception
	{
		Script s = Script.parse("a.json", "{\"account\":\"test\",\"timeoutMs\":500,\"steps\":[{\"step\":\"assertNoDialogue\"}]}");
		assertEquals(500, s.steps.get(0).timeoutMs);
	}

	@Test
	public void idIsASafeFileName()
	{
		assertEquals("my%20test", Script.idFor("my test.json"));
		assertEquals("my_test", Script.idFor("my_test.json"));
		assertEquals("100%25", Script.idFor("100%.json"));
		assertEquals("h%C3%A4ns", Script.idFor("h\u00e4ns.json"));
		assertEquals("a.b-c", Script.idFor("a.b-c.JSON"));
	}

	@Test
	public void rejectsWithAReason()
	{
		invalid("not json", "not valid JSON");
		invalid("[]", "one JSON object");
		invalid("{\"steps\":[{\"step\":\"waitForLogin\"}]}", "needs \"account\"");
		invalid("{\"account\":\"test\"}", "needs \"steps\"");
		invalid("{\"account\":\"test\",\"steps\":[]}", "\"steps\" is empty");
		invalid("{\"account\":\"test\",\"steps\":[{\"step\":\"fly\"}]}", "step 1: unknown step \"fly\"");
		invalid("{\"account\":\"test\",\"steps\":[{\"step\":\"walk\",\"x\":1}]}", "step 1 needs \"y\"");
		invalid("{\"account\":\"test\",\"steps\":[{\"step\":\"walk\",\"x\":1,\"y\":2,\"plan\":0}]}", "unknown field \"plan\"");
		invalid("{\"account\":\"test\",\"steps\":[{\"step\":\"walk\",\"x\":1.5,\"y\":2}]}", "whole number");
		invalid("{\"account\":\"test\",\"steps\":[{\"step\":\"npcOption\",\"option\":\"Talk-to\"}]}", "needs \"npc\"");
		invalid("{\"account\":\"test\",\"steps\":[{\"step\":\"waitForLogin\",\"timeoutMs\":0}]}", "more than 0");
		invalid("{\"account\":\"test\",\"stepz\":[]}", "needs \"steps\"");
		invalid("{\"account\":\"test\",\"steps\":[{\"step\":\"walk\",\"x\":4294970518,\"y\":3218}]}", "\"x\" is out of range");
	}

	private static void invalid(String json, String reason)
	{
		try
		{
			Script.parse("a.json", json);
			fail("accepted " + json);
		}
		catch (Script.Invalid e)
		{
			assertTrue(e.getMessage() + " should say " + reason, e.getMessage().contains(reason));
		}
	}
}
