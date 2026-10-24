package osrsps.testrunner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A test script as read from a JSON file in the tests folder. README.md documents the format. */
final class Script
{
	static final long DEFAULT_TIMEOUT_MS = 10_000;
	static final long LOGIN_TIMEOUT_MS = 120_000;
	static final long WALK_TIMEOUT_MS = 30_000;

	/** The file's name without .json; results are written under the same name. */
	final String id;
	final String name;
	/** The only account the script may run on. */
	final String account;
	final boolean runOnLogin;
	final List<Step> steps;

	private Script(String id, String name, String account, boolean runOnLogin, List<Step> steps)
	{
		this.id = id;
		this.name = name;
		this.account = account;
		this.runOnLogin = runOnLogin;
		this.steps = Collections.unmodifiableList(steps);
	}

	static final class Invalid extends Exception
	{
		Invalid(String message)
		{
			super(message);
		}
	}

	/** The id a script file's results are written under: its name without .json, made safe for a file name. */
	static String idFor(String fileName)
	{
		String base = fileName.toLowerCase().endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
		return base.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	static Script parse(String fileName, String json) throws Invalid
	{
		JsonObject root;
		try
		{
			JsonElement e = JsonParser.parseString(json);
			if (!e.isJsonObject())
			{
				throw new Invalid("the file must hold one JSON object");
			}
			root = e.getAsJsonObject();
		}
		catch (JsonParseException e)
		{
			throw new Invalid("not valid JSON: " + e.getMessage());
		}

		String id = idFor(fileName);
		Fields top = new Fields(root, "the script");
		String name = top.string("name", id);
		String account = top.string("account", null);
		if (account == null || account.trim().isEmpty())
		{
			throw new Invalid("the script needs \"account\", the test account it runs on (such as \"test\")");
		}
		boolean runOnLogin = top.bool("runOnLogin", false);
		long timeoutMs = top.positiveLong("timeoutMs", DEFAULT_TIMEOUT_MS);
		JsonArray array = top.array("steps");
		top.rejectUnknown("name", "account", "runOnLogin", "timeoutMs", "steps", "comment");
		if (array.size() == 0)
		{
			throw new Invalid("\"steps\" is empty");
		}

		List<Step> steps = new ArrayList<>();
		for (int i = 0; i < array.size(); i++)
		{
			if (!array.get(i).isJsonObject())
			{
				throw new Invalid("step " + (i + 1) + " is not a JSON object");
			}
			steps.add(step(new Fields(array.get(i).getAsJsonObject(), "step " + (i + 1)), timeoutMs));
		}
		return new Script(id, name, account.trim(), runOnLogin, steps);
	}

	private static Step step(Fields f, long defaultTimeout) throws Invalid
	{
		String type = f.requiredString("step");
		switch (type)
		{
			case "waitForLogin":
			{
				long timeout = f.positiveLong("timeoutMs", LOGIN_TIMEOUT_MS);
				f.rejectUnknown("step", "timeoutMs", "comment");
				return new Steps.WaitForLogin(timeout);
			}
			case "command":
			case "chat":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				String text = f.requiredString("text");
				if (type.equals("command") && !text.startsWith("::"))
				{
					text = "::" + text;
				}
				String expect = f.string("expectMessage", null);
				f.rejectUnknown("step", "timeoutMs", "text", "expectMessage", "comment");
				return new Steps.Chat(type, timeout, text, expect);
			}
			case "assertLocation":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				int x = f.requiredInt("x");
				int y = f.requiredInt("y");
				int plane = f.integer("plane", -1);
				f.rejectUnknown("step", "timeoutMs", "x", "y", "plane", "comment");
				return new Steps.AssertLocation(timeout, x, y, plane);
			}
			case "walk":
			{
				long timeout = f.positiveLong("timeoutMs", WALK_TIMEOUT_MS);
				int x = f.requiredInt("x");
				int y = f.requiredInt("y");
				f.rejectUnknown("step", "timeoutMs", "x", "y", "comment");
				return new Steps.Walk(timeout, x, y);
			}
			case "npcOption":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				String npc = f.string("npc", null);
				int id = f.integer("npcId", -1);
				if (npc == null && id < 0)
				{
					throw new Invalid(f.where + " needs \"npc\" (a name) or \"npcId\"");
				}
				String option = f.requiredString("option");
				f.rejectUnknown("step", "timeoutMs", "npc", "npcId", "option", "comment");
				return new Steps.NpcOption(timeout, npc, id, option);
			}
			case "assertDialogue":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				String contains = f.string("contains", null);
				String speaker = f.string("speaker", null);
				f.rejectUnknown("step", "timeoutMs", "contains", "speaker", "comment");
				return new Steps.AssertDialogue(timeout, contains, speaker);
			}
			case "assertNoDialogue":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				f.rejectUnknown("step", "timeoutMs", "comment");
				return new Steps.AssertNoDialogue(timeout);
			}
			case "continueDialogue":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				f.rejectUnknown("step", "timeoutMs", "comment");
				return new Steps.ContinueDialogue(timeout);
			}
			case "chooseOption":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				String text = f.requiredString("text");
				f.rejectUnknown("step", "timeoutMs", "text", "comment");
				return new Steps.ChooseOption(timeout, text);
			}
			case "assertMessage":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				String contains = f.requiredString("contains");
				f.rejectUnknown("step", "timeoutMs", "contains", "comment");
				return new Steps.AssertMessage(timeout, contains);
			}
			case "waitTicks":
			{
				int ticks = f.requiredInt("ticks");
				if (ticks < 1)
				{
					throw new Invalid(f.where + ": \"ticks\" must be 1 or more");
				}
				// A tick is 600 ms; leave room for a stall.
				long timeout = f.positiveLong("timeoutMs", ticks * 600L + defaultTimeout);
				f.rejectUnknown("step", "timeoutMs", "ticks", "comment");
				return new Steps.WaitTicks(timeout, ticks);
			}
			default:
				throw new Invalid(f.where + ": unknown step \"" + type + "\"");
		}
	}

	/** Reads one JSON object's fields with error messages that say where the problem is. */
	private static final class Fields
	{
		final JsonObject o;
		final String where;

		Fields(JsonObject o, String where)
		{
			this.o = o;
			this.where = where;
		}

		String string(String key, String fallback) throws Invalid
		{
			JsonElement e = o.get(key);
			if (e == null || e.isJsonNull())
			{
				return fallback;
			}
			if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString())
			{
				throw new Invalid(where + ": \"" + key + "\" must be a string");
			}
			return e.getAsString();
		}

		String requiredString(String key) throws Invalid
		{
			String s = string(key, null);
			if (s == null || s.isEmpty())
			{
				throw new Invalid(where + " needs \"" + key + "\"");
			}
			return s;
		}

		private Long number(String key) throws Invalid
		{
			JsonElement e = o.get(key);
			if (e == null || e.isJsonNull())
			{
				return null;
			}
			if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
			{
				throw new Invalid(where + ": \"" + key + "\" must be a number");
			}
			double d = e.getAsDouble();
			if (d != Math.rint(d))
			{
				throw new Invalid(where + ": \"" + key + "\" must be a whole number");
			}
			return (long) d;
		}

		int integer(String key, int fallback) throws Invalid
		{
			Long n = number(key);
			return n == null ? fallback : n.intValue();
		}

		int requiredInt(String key) throws Invalid
		{
			Long n = number(key);
			if (n == null)
			{
				throw new Invalid(where + " needs \"" + key + "\"");
			}
			return n.intValue();
		}

		long positiveLong(String key, long fallback) throws Invalid
		{
			Long n = number(key);
			if (n == null)
			{
				return fallback;
			}
			if (n <= 0)
			{
				throw new Invalid(where + ": \"" + key + "\" must be more than 0");
			}
			return n;
		}

		boolean bool(String key, boolean fallback) throws Invalid
		{
			JsonElement e = o.get(key);
			if (e == null || e.isJsonNull())
			{
				return fallback;
			}
			if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean())
			{
				throw new Invalid(where + ": \"" + key + "\" must be true or false");
			}
			return e.getAsBoolean();
		}

		JsonArray array(String key) throws Invalid
		{
			JsonElement e = o.get(key);
			if (e == null || !e.isJsonArray())
			{
				throw new Invalid(where + " needs \"" + key + "\", a list");
			}
			return e.getAsJsonArray();
		}

		/** A misspelt field would otherwise be silently ignored, so every field must be known. */
		void rejectUnknown(String... known) throws Invalid
		{
			Set<String> ok = new HashSet<>(List.of(known));
			for (String key : o.keySet())
			{
				if (!ok.contains(key))
				{
					throw new Invalid(where + ": unknown field \"" + key + "\"");
				}
			}
		}
	}
}
