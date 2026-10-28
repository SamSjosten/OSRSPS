package osrsps.testrunner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

	/**
	 * The id a script file's results are written under: its name without .json. Any other character than letters,
	 * digits, '.', '_' and '-' becomes %XX of its UTF-8 bytes, so two file names never share an id.
	 */
	static String idFor(String fileName)
	{
		String base = fileName.toLowerCase().endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
		StringBuilder id = new StringBuilder();
		for (byte b : base.getBytes(StandardCharsets.UTF_8))
		{
			char c = (char) (b & 0xff);
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-')
			{
				id.append(c);
			}
			else
			{
				id.append(String.format("%%%02X", b & 0xff));
			}
		}
		return id.toString();
	}

	static Script parse(String fileName, String json) throws Invalid
	{
		JsonObject root;
		try
		{
			JsonElement e = new JsonParser().parse(json);
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
			case "locOption":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				String loc = f.string("loc", null);
				int id = f.integer("locId", -1);
				if (loc == null && id < 0)
				{
					throw new Invalid(f.where + " needs \"loc\" (a name) or \"locId\"");
				}
				String option = f.requiredString("option");
				int x = f.integer("x", -1);
				int y = f.integer("y", -1);
				if ((x < 0) != (y < 0))
				{
					throw new Invalid(f.where + " needs both \"x\" and \"y\", or neither");
				}
				f.rejectUnknown("step", "timeoutMs", "loc", "locId", "option", "x", "y", "comment");
				return new Steps.LocOption(timeout, loc, id, option, x < 0 ? null : new Tile(x, y, -1));
			}
			case "assertLoc":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				String loc = f.string("loc", null);
				int id = f.integer("locId", -1);
				if (loc == null && id < 0)
				{
					throw new Invalid(f.where + " needs \"loc\" (a name) or \"locId\"");
				}
				String option = f.string("option", null);
				int x = f.integer("x", -1);
				int y = f.integer("y", -1);
				if (x < 0 || y < 0)
				{
					throw new Invalid(f.where + " needs \"x\" and \"y\"");
				}
				f.rejectUnknown("step", "timeoutMs", "loc", "locId", "option", "x", "y", "comment");
				return new Steps.AssertLoc(timeout, loc, id, option, new Tile(x, y, -1));
			}
			case "takeItem":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				String item = f.string("item", null);
				int id = f.integer("itemId", -1);
				if (item == null && id < 0)
				{
					throw new Invalid(f.where + " needs \"item\" (a name) or \"itemId\"");
				}
				int x = f.integer("x", -1);
				int y = f.integer("y", -1);
				if ((x < 0) != (y < 0))
				{
					throw new Invalid(f.where + " needs both \"x\" and \"y\", or neither");
				}
				f.rejectUnknown("step", "timeoutMs", "item", "itemId", "x", "y", "comment");
				return new Steps.TakeItem(timeout, item, id, x < 0 ? null : new Tile(x, y, -1));
			}
			case "assertInventory":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				String item = f.string("item", null);
				int id = f.integer("itemId", -1);
				if (item == null && id < 0)
				{
					throw new Invalid(f.where + " needs \"item\" (a name) or \"itemId\"");
				}
				int count = f.integer("count", -1);
				if (f.o.has("count") && count < 0)
				{
					throw new Invalid(f.where + ": \"count\" must be 0 or more");
				}
				f.rejectUnknown("step", "timeoutMs", "item", "itemId", "count", "comment");
				return new Steps.AssertInventory(timeout, item, id, count);
			}
			case "finishDialogue":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				f.rejectUnknown("step", "timeoutMs", "comment");
				return new Steps.FinishDialogue(timeout);
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
			case "assertHitsplat":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				String npc = f.string("npc", null);
				boolean self = f.bool("self", false);
				if ((npc == null) == !self)
				{
					throw new Invalid(f.where + " needs \"npc\" (a name) or \"self\": true, not both");
				}
				int amount = f.integer("amount", -1);
				if (f.o.has("amount") && amount < 0)
				{
					throw new Invalid(f.where + ": \"amount\" must be 0 or more");
				}
				f.rejectUnknown("step", "timeoutMs", "npc", "self", "amount", "comment");
				return new Steps.AssertHitsplat(timeout, npc, amount);
			}
			case "assertNpcHealth":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				String npc = f.string("npc", null);
				int id = f.integer("npcId", -1);
				if (npc == null && id < 0)
				{
					throw new Invalid(f.where + " needs \"npc\" (a name) or \"npcId\"");
				}
				int min = f.integer("min", -1);
				int max = f.integer("max", -1);
				if (min < 0 && max < 0)
				{
					throw new Invalid(f.where + " needs \"min\" or \"max\", a percentage");
				}
				if (min > 100 || max > 100 || (f.o.has("min") && min < 0) || (f.o.has("max") && max < 0))
				{
					throw new Invalid(f.where + ": \"min\" and \"max\" are percentages, 0 to 100");
				}
				f.rejectUnknown("step", "timeoutMs", "npc", "npcId", "min", "max", "comment");
				return new Steps.AssertNpcHealth(timeout, npc, id, min, max);
			}
			case "assertStat":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				String skill = f.requiredString("skill");
				String known = null;
				for (String name : Steps.AssertStat.SKILLS)
				{
					if (name.equalsIgnoreCase(skill))
					{
						known = name;
					}
				}
				if (known == null)
				{
					throw new Invalid(f.where + ": no skill \"" + skill + "\"; one of " + Steps.AssertStat.SKILLS);
				}
				int level = f.integer("level", -1);
				int xp = f.integer("xp", -1);
				if (level < 0 && xp < 0)
				{
					throw new Invalid(f.where + " needs \"level\" or \"xp\", the least to expect");
				}
				if ((f.o.has("level") && level < 0) || (f.o.has("xp") && xp < 0))
				{
					throw new Invalid(f.where + ": \"level\" and \"xp\" must be 0 or more");
				}
				f.rejectUnknown("step", "timeoutMs", "skill", "level", "xp", "comment");
				return new Steps.AssertStat(timeout, known, level, xp);
			}
			case "assertGroundItem":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				String item = f.string("item", null);
				int id = f.integer("itemId", -1);
				if (item == null && id < 0)
				{
					throw new Invalid(f.where + " needs \"item\" (a name) or \"itemId\"");
				}
				int x = f.integer("x", -1);
				int y = f.integer("y", -1);
				if ((x < 0) != (y < 0))
				{
					throw new Invalid(f.where + " needs both \"x\" and \"y\", or neither");
				}
				boolean absent = f.bool("absent", false);
				f.rejectUnknown("step", "timeoutMs", "item", "itemId", "x", "y", "absent", "comment");
				return new Steps.AssertGroundItem(timeout, item, id, x < 0 ? null : new Tile(x, y, -1), absent);
			}
			case "assertAnimation":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				int id = f.requiredInt("id");
				if (id < 0)
				{
					throw new Invalid(f.where + ": \"id\" must be 0 or more");
				}
				String npc = f.string("npc", null);
				f.rejectUnknown("step", "timeoutMs", "id", "npc", "comment");
				return new Steps.AssertAnimation(timeout, npc, id);
			}
			case "itemOption":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				String item = f.string("item", null);
				int id = f.integer("itemId", -1);
				if (item == null && id < 0)
				{
					throw new Invalid(f.where + " needs \"item\" (a name) or \"itemId\"");
				}
				String option = f.requiredString("option");
				f.rejectUnknown("step", "timeoutMs", "item", "itemId", "option", "comment");
				return new Steps.ItemOption(timeout, item, id, option);
			}
			case "wornOption":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				int wearpos = wornSlot(f);
				String option = f.requiredString("option");
				f.rejectUnknown("step", "timeoutMs", "slot", "option", "comment");
				return new Steps.WornOption(timeout, wearpos, option);
			}
			case "assertEquipment":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				int wearpos = wornSlot(f);
				String item = f.string("item", null);
				int id = f.integer("itemId", -1);
				boolean empty = f.bool("empty", false);
				if (empty == (item != null || id >= 0))
				{
					throw new Invalid(f.where + " needs \"item\" (a name) or \"itemId\", or \"empty\": true");
				}
				f.rejectUnknown("step", "timeoutMs", "slot", "item", "itemId", "empty", "comment");
				return new Steps.AssertEquipment(timeout, wearpos, item, id);
			}
			case "clickWidget":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				int interfaceId = f.requiredInt("interfaceId");
				int component = f.requiredInt("component");
				int sub = f.integer("slot", -1);
				int op = f.integer("op", 1);
				if (interfaceId < 0 || interfaceId > 0xffff || component < 0 || component > 0xffff)
				{
					throw new Invalid(f.where + ": \"interfaceId\" and \"component\" must be 0 to 65535");
				}
				if (f.o.has("slot") && sub < 0)
				{
					throw new Invalid(f.where + ": \"slot\" must be 0 or more");
				}
				if (op < 1 || op > 10)
				{
					throw new Invalid(f.where + ": \"op\" must be 1 to 10");
				}
				f.rejectUnknown("step", "timeoutMs", "interfaceId", "component", "slot", "op", "comment");
				return new Steps.ClickWidget(timeout, interfaceId, component, sub, op);
			}
			case "assertWidgetText":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				int interfaceId = f.requiredInt("interfaceId");
				int component = f.integer("component", -1);
				if (interfaceId < 0 || interfaceId > 0xffff || (f.o.has("component") && (component < 0 || component > 0xffff)))
				{
					throw new Invalid(f.where + ": \"interfaceId\" and \"component\" must be 0 to 65535");
				}
				String contains = f.requiredString("contains");
				f.rejectUnknown("step", "timeoutMs", "interfaceId", "component", "contains", "comment");
				return new Steps.AssertWidgetText(timeout, interfaceId, component, contains);
			}
			case "assertPose":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				int idle = f.requiredInt("idle");
				if (idle < 0)
				{
					throw new Invalid(f.where + ": \"idle\" must be 0 or more");
				}
				f.rejectUnknown("step", "timeoutMs", "idle", "comment");
				return new Steps.AssertPose(timeout, idle);
			}
			case "logout":
			{
				long timeout = f.positiveLong("timeoutMs", defaultTimeout);
				f.rejectUnknown("step", "timeoutMs", "comment");
				return new Steps.Logout(timeout);
			}
			default:
				throw new Invalid(f.where + ": unknown step \"" + type + "\"");
		}
	}

	/** The step's "slot", an equipment slot name such as "weapon", as its wearpos. */
	private static int wornSlot(Fields f) throws Invalid
	{
		String slot = f.requiredString("slot");
		Integer wearpos = Steps.WORN_SLOTS.get(slot.toLowerCase(Locale.ROOT));
		if (wearpos == null)
		{
			throw new Invalid(f.where + ": \"slot\" must be one of " + String.join(", ", Steps.WORN_SLOTS.keySet()));
		}
		return wearpos;
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
			return n == null ? fallback : toInt(key, n);
		}

		int requiredInt(String key) throws Invalid
		{
			Long n = number(key);
			if (n == null)
			{
				throw new Invalid(where + " needs \"" + key + "\"");
			}
			return toInt(key, n);
		}

		/** Narrowing would wrap a number too big for an int round to another, valid looking one. */
		private int toInt(String key, long n) throws Invalid
		{
			if (n < Integer.MIN_VALUE || n > Integer.MAX_VALUE)
			{
				throw new Invalid(where + ": \"" + key + "\" is out of range");
			}
			return (int) n;
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
