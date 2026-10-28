package osrsps.testrunner;

import java.util.List;
import java.util.Locale;

/** The step types a script can use. README.md lists them with their fields. */
final class Steps
{
	private Steps()
	{
	}

	/** Passes once a player is logged in. The runner checks the name against the script's account on every step. */
	static final class WaitForLogin extends Step
	{
		WaitForLogin(long timeoutMs)
		{
			super("waitForLogin", timeoutMs, true);
		}

		@Override
		Poll poll(Run run)
		{
			Game g = run.game;
			if (!g.loggedIn() || g.localName() == null)
			{
				return Poll.waiting("not logged in");
			}
			return Poll.pass("logged in as " + g.localName());
		}

		@Override
		String describe()
		{
			return "wait for login";
		}
	}

	/** Sends a chat line, normally a ::command, and optionally waits for a chat message the server answers with. */
	static final class Chat extends Step
	{
		/** Game ticks between Enter presses while the client leaves the line in its input, and how many to press. */
		static final int ENTER_RETRY_TICKS = 2;
		static final int ENTER_RETRIES = 5;

		final String text;
		final String expectMessage;
		private boolean sent;
		/** The game tick of the last Enter press, and how many presses came after the first. */
		private int enterTick;
		private int retries;
		private String focusNote;

		Chat(String type, long timeoutMs, String text, String expectMessage)
		{
			super(type, timeoutMs, false);
			this.text = text;
			this.expectMessage = expectMessage;
		}

		@Override
		Poll poll(Run run)
		{
			if (!sent)
			{
				run.markAction();
				String error = run.game.sendChat(text);
				if (error != null)
				{
					return Poll.fail(error);
				}
				sent = true;
				enterTick = run.gameTicks();
			}
			if (run.game.chatPending())
			{
				// The client sometimes leaves the line in its input (seen once after a 30-tick wait): press Enter again
				// a few times, a tick or two apart, before giving the step's timeout up.
				if (retries < ENTER_RETRIES && run.gameTicks() - enterTick >= ENTER_RETRY_TICKS)
				{
					retries++;
					enterTick = run.gameTicks();
					focusNote = run.game.pressEnterAgain();
				}
				return Poll.waiting("the chat input still holds the text: the client has not taken the Enter key"
					+ (retries == 0 ? "" : " after " + (retries + 1) + " presses (" + focusNote + ")"));
			}
			if (expectMessage == null)
			{
				return Poll.pass("sent");
			}
			String seen = run.messageSinceAction(expectMessage);
			return seen != null ? Poll.pass("message \"" + seen + "\"") : Poll.waiting("no message containing \"" + expectMessage + "\" yet");
		}

		@Override
		String describe()
		{
			return "send \"" + text + "\"" + (expectMessage == null ? "" : ", expect message \"" + expectMessage + "\"");
		}
	}

	static final class AssertLocation extends Step
	{
		final int x;
		final int y;
		/** -1 to accept any plane. */
		final int plane;

		AssertLocation(long timeoutMs, int x, int y, int plane)
		{
			super("assertLocation", timeoutMs, false);
			this.x = x;
			this.y = y;
			this.plane = plane;
		}

		@Override
		Poll poll(Run run)
		{
			Tile at = run.game.location();
			if (at == null)
			{
				return Poll.waiting("no local player");
			}
			if (!(at.x == x && at.y == y && (plane < 0 || at.plane == plane)))
			{
				return Poll.waiting("at " + at);
			}
			// The client has to show the player there too: after a teleport it should draw them there at once.
			Tile drawn = run.game.drawnLocation();
			if (!at.equals(drawn))
			{
				return Poll.waiting(drawnElsewhere(at, drawn));
			}
			return Poll.pass("at " + at);
		}

		@Override
		String describe()
		{
			return "assert location " + x + ", " + y + (plane < 0 ? "" : ", " + plane);
		}
	}

	/** Why a step waits when the client draws the player on another tile than the server has them on. */
	private static String drawnElsewhere(Tile at, Tile drawn)
	{
		return "at " + at + ", but the client draws the player at " + drawn;
	}

	/** Clicks Walk here on a tile and waits until the player stands on it. */
	static final class Walk extends Step
	{
		/**
		 * Game ticks the player may stand still away from the tile after a click before it is clicked again. The click
		 * names a spot on screen, not a tile, so one sent while the camera still moves (right after a teleport) can
		 * land on another tile.
		 */
		static final int STILL_TICKS = 3;

		final int x;
		final int y;
		private boolean sent;
		private Tile lastAt;
		private int stillSince;

		Walk(long timeoutMs, int x, int y)
		{
			super("walk", timeoutMs, false);
			this.x = x;
			this.y = y;
		}

		@Override
		Poll poll(Run run)
		{
			Tile at = run.game.location();
			if (at == null)
			{
				return Poll.waiting("no local player");
			}
			Tile drawn = run.game.drawnLocation();
			if (at.x == x && at.y == y)
			{
				// The client draws the player a step behind while they walk, so this waits for them to arrive on screen.
				return at.equals(drawn) ? Poll.pass("at " + at) : Poll.waiting(drawnElsewhere(at, drawn));
			}
			if (!at.equals(lastAt))
			{
				lastAt = at;
				stillSince = run.gameTicks();
			}
			if (sent && run.gameTicks() - stillSince >= STILL_TICKS)
			{
				sent = false;
			}
			if (!sent && !at.equals(drawn))
			{
				// Standing still, the client should draw the player on their tile. A click picks the tile under a spot
				// on screen, and the camera follows the drawn player, so a click now would go somewhere else.
				return Poll.waiting(drawnElsewhere(at, drawn));
			}
			if (!sent)
			{
				// Right after a teleport the tile can be off the viewport until the camera catches up, so a refused click
				// is retried to the timeout rather than failing the step.
				run.markAction();
				String error = run.game.walk(x, y);
				if (error != null)
				{
					return Poll.waiting(error);
				}
				sent = true;
				stillSince = run.gameTicks();
			}
			// Where the client's flag went shows which tile the click picked.
			Tile flag = run.game.walkDestination();
			if (flag != null && !(flag.x == x && flag.y == y))
			{
				return Poll.waiting("at " + at + ", heading for " + flag);
			}
			return Poll.waiting("at " + at);
		}

		@Override
		String describe()
		{
			return "walk to " + x + ", " + y;
		}
	}

	/** Clicks an NPC option. Waits (to the timeout) for an NPC by that name to be in view; does not wait for the result. */
	static final class NpcOption extends Step
	{
		final String npc;
		final int npcId;
		final String option;

		NpcOption(long timeoutMs, String npc, int npcId, String option)
		{
			super("npcOption", timeoutMs, false);
			this.npc = npc;
			this.npcId = npcId;
			this.option = option;
		}

		@Override
		Poll poll(Run run)
		{
			run.markAction();
			String error = run.game.npcOption(npc, npcId, option);
			// The NPC may not be in view yet (a teleport's scene still loading): keep trying until the timeout.
			return error == null ? Poll.pass("clicked") : Poll.waiting(error);
		}

		@Override
		String describe()
		{
			return option + " on " + (npc != null ? npc : "NPC " + npcId);
		}
	}

	/** Clicks a loc option. Waits (to the timeout) for such a loc to be in the scene; does not wait for the result. */
	static final class LocOption extends Step
	{
		final String loc;
		final int locId;
		final String option;
		/** Only a loc on this tile counts, when set. Its plane is ignored: locs are looked for on the player's plane. */
		final Tile at;

		LocOption(long timeoutMs, String loc, int locId, String option, Tile at)
		{
			super("locOption", timeoutMs, false);
			this.loc = loc;
			this.locId = locId;
			this.option = option;
			this.at = at;
		}

		@Override
		Poll poll(Run run)
		{
			run.markAction();
			String error = run.game.locOption(loc, locId, option, at);
			// The scene may still be loading after a teleport: keep trying until the timeout.
			return error == null ? Poll.pass("clicked") : Poll.waiting(error);
		}

		@Override
		String describe()
		{
			return option + " on " + (loc != null ? loc : "loc " + locId) + (at == null ? "" : " at " + at.x + ", " + at.y);
		}
	}

	/**
	 * Waits for a loc to be on a tile, with an option when one is given: a door shut again (its Open option back) before
	 * the script teleports away, since a teleport cancels a click the server hasn't carried out yet.
	 */
	static final class AssertLoc extends Step
	{
		final String loc;
		final int locId;
		/** Null for any. */
		final String option;
		/** Its plane is ignored: locs are looked for on the player's plane. */
		final Tile at;

		AssertLoc(long timeoutMs, String loc, int locId, String option, Tile at)
		{
			super("assertLoc", timeoutMs, false);
			this.loc = loc;
			this.locId = locId;
			this.option = option;
			this.at = at;
		}

		@Override
		Poll poll(Run run)
		{
			String error = run.game.locAt(loc, locId, option, at);
			return error == null ? Poll.pass("there") : Poll.waiting(error);
		}

		@Override
		String describe()
		{
			return "assert " + (loc != null ? loc : "loc " + locId) + (option == null ? "" : " with " + option) + " at " + at.x + ", " + at.y;
		}
	}

	/** Clicks Take on an item on the ground. Waits (to the timeout) for one to be in the scene; does not wait for the result. */
	static final class TakeItem extends Step
	{
		final String item;
		final int itemId;
		/** Only an item on this tile counts, when set. Its plane is ignored: items are looked for on the player's plane. */
		final Tile at;

		TakeItem(long timeoutMs, String item, int itemId, Tile at)
		{
			super("takeItem", timeoutMs, false);
			this.item = item;
			this.itemId = itemId;
			this.at = at;
		}

		@Override
		Poll poll(Run run)
		{
			run.markAction();
			String error = run.game.takeItem(item, itemId, at);
			// The scene may still be loading after a teleport, or an item taken a moment ago not be back yet: keep trying.
			return error == null ? Poll.pass("clicked") : Poll.waiting(error);
		}

		@Override
		String describe()
		{
			return "take " + (item != null ? item : "item " + itemId) + (at == null ? "" : " at " + at.x + ", " + at.y);
		}
	}

	/** Passes when the inventory holds exactly that many of an item, or any at all when no count is given. */
	static final class AssertInventory extends Step
	{
		final String item;
		final int itemId;
		/** -1 for any number but none. */
		final int count;

		AssertInventory(long timeoutMs, String item, int itemId, int count)
		{
			super("assertInventory", timeoutMs, false);
			this.item = item;
			this.itemId = itemId;
			this.count = count;
		}

		@Override
		Poll poll(Run run)
		{
			int held = run.game.inventoryCount(item, itemId);
			if (held < 0)
			{
				return Poll.waiting("no inventory yet");
			}
			boolean ok = count < 0 ? held > 0 : held == count;
			return ok ? Poll.pass(held + " in the inventory") : Poll.waiting(held + " in the inventory");
		}

		@Override
		String describe()
		{
			return "assert inventory has " + (count < 0 ? "some " : count + " x ") + (item != null ? item : "item " + itemId);
		}
	}

	static final class AssertDialogue extends Step
	{
		final String contains;
		final String speaker;

		AssertDialogue(long timeoutMs, String contains, String speaker)
		{
			super("assertDialogue", timeoutMs, false);
			this.contains = contains;
			this.speaker = speaker;
		}

		@Override
		Poll poll(Run run)
		{
			Dialogue d = run.game.dialogue();
			if (d == null)
			{
				return Poll.waiting("no dialogue open");
			}
			boolean textOk = contains == null || d.text.contains(contains);
			boolean speakerOk = speaker == null || d.speaker.equalsIgnoreCase(speaker);
			return textOk && speakerOk ? Poll.pass(d.toString()) : Poll.waiting("dialogue is " + d);
		}

		@Override
		String describe()
		{
			return "assert dialogue" + (speaker == null ? "" : " from " + speaker) + (contains == null ? "" : " contains \"" + contains + "\"");
		}
	}

	static final class AssertNoDialogue extends Step
	{
		AssertNoDialogue(long timeoutMs)
		{
			super("assertNoDialogue", timeoutMs, false);
		}

		@Override
		Poll poll(Run run)
		{
			Dialogue d = run.game.dialogue();
			return d == null ? Poll.pass("none open") : Poll.waiting("dialogue is " + d);
		}

		@Override
		String describe()
		{
			return "assert no dialogue";
		}
	}

	/** Waits for a dialogue with a continue button, clicks it, then waits until that dialogue has gone. */
	static final class ContinueDialogue extends Step
	{
		private String clicked;

		ContinueDialogue(long timeoutMs)
		{
			super("continueDialogue", timeoutMs, false);
		}

		@Override
		Poll poll(Run run)
		{
			Dialogue d = run.game.dialogue();
			if (clicked == null)
			{
				if (d == null || d.kind == Dialogue.Kind.OPTIONS)
				{
					return Poll.waiting(d == null ? "no dialogue open" : "dialogue is " + d);
				}
				run.markAction();
				String error = run.game.continueDialogue();
				if (error != null)
				{
					return Poll.fail(error);
				}
				clicked = d.toString();
				return Poll.waiting("clicked continue, " + clicked + " still open");
			}
			return d == null || !d.toString().equals(clicked) ? Poll.pass("continued " + clicked) : Poll.waiting("clicked continue, " + clicked + " still open");
		}

		@Override
		String describe()
		{
			return "continue dialogue";
		}
	}

	/**
	 * Clicks through whatever dialogue is open until none is: Click here to continue on each page, the first option on a
	 * menu. Passes at once when none is open. For talks that go a random way, so a script can end them and the next
	 * script starts with the chatbox free.
	 */
	static final class FinishDialogue extends Step
	{
		/**
		 * Game ticks to wait for the page to change after a click. The server answers a click on the next tick, so a page
		 * still shown after this many is a new page with the same text, and gets clicked again.
		 */
		static final int SAME_PAGE_TICKS = 3;

		private String clicked;
		private int clickedTick;
		private int clicks;

		FinishDialogue(long timeoutMs)
		{
			super("finishDialogue", timeoutMs, false);
		}

		@Override
		Poll poll(Run run)
		{
			Dialogue d = run.game.dialogue();
			if (clicked != null && d != null && d.toString().equals(clicked) && run.gameTicks() - clickedTick < SAME_PAGE_TICKS)
			{
				return Poll.waiting("clicked, " + clicked + " still open");
			}
			if (d == null)
			{
				return Poll.pass(clicks == 0 ? "no dialogue open" : "closed after " + clicks + " clicks");
			}
			run.markAction();
			String error;
			if (d.kind == Dialogue.Kind.OPTIONS)
			{
				error = d.options.isEmpty() ? "the menu has no options: " + d : run.game.chooseOption(d.options.get(0).slot);
			}
			else
			{
				error = run.game.continueDialogue();
			}
			if (error != null)
			{
				return Poll.fail(error);
			}
			clicked = d.toString();
			clickedTick = run.gameTicks();
			clicks++;
			return Poll.waiting("clicked, " + clicked + " still open");
		}

		@Override
		String describe()
		{
			return "finish dialogue";
		}
	}

	/** Waits for an option menu, clicks the option whose text contains the given text, then waits for the menu to go. */
	static final class ChooseOption extends Step
	{
		final String text;
		private String clicked;

		ChooseOption(long timeoutMs, String text)
		{
			super("chooseOption", timeoutMs, false);
			this.text = text;
		}

		@Override
		Poll poll(Run run)
		{
			Dialogue d = run.game.dialogue();
			if (clicked == null)
			{
				if (d == null || d.kind != Dialogue.Kind.OPTIONS)
				{
					return Poll.waiting(d == null ? "no dialogue open" : "dialogue is " + d);
				}
				Dialogue.Option match = find(d.options, text);
				if (match == null)
				{
					return Poll.fail("no option containing \"" + text + "\" in " + d);
				}
				run.markAction();
				String error = run.game.chooseOption(match.slot);
				if (error != null)
				{
					return Poll.fail(error);
				}
				clicked = d.toString();
				return Poll.waiting("chose \"" + match.text + "\", menu still open");
			}
			return d == null || !d.toString().equals(clicked) ? Poll.pass("chose \"" + text + "\"") : Poll.waiting("chose \"" + text + "\", menu still open");
		}

		private static Dialogue.Option find(List<Dialogue.Option> options, String text)
		{
			String want = text.toLowerCase(Locale.ROOT);
			for (Dialogue.Option o : options)
			{
				if (o.text.equalsIgnoreCase(text))
				{
					return o;
				}
			}
			for (Dialogue.Option o : options)
			{
				if (o.text.toLowerCase(Locale.ROOT).contains(want))
				{
					return o;
				}
			}
			return null;
		}

		@Override
		String describe()
		{
			return "choose option \"" + text + "\"";
		}
	}

	/** Passes when a chat message containing the text has arrived since the last step that acted. */
	static final class AssertMessage extends Step
	{
		final String contains;

		AssertMessage(long timeoutMs, String contains)
		{
			super("assertMessage", timeoutMs, false);
			this.contains = contains;
		}

		@Override
		Poll poll(Run run)
		{
			String seen = run.messageSinceAction(contains);
			return seen != null ? Poll.pass("message \"" + seen + "\"") : Poll.waiting("no message containing \"" + contains + "\" since the last action");
		}

		@Override
		String describe()
		{
			return "assert message contains \"" + contains + "\"";
		}
	}

	/** Waits a number of game ticks (GameTick events, which only come while logged in). */
	static final class WaitTicks extends Step
	{
		final int ticks;
		private int startTick = -1;

		WaitTicks(long timeoutMs, int ticks)
		{
			super("waitTicks", timeoutMs, false);
			this.ticks = ticks;
		}

		@Override
		Poll poll(Run run)
		{
			if (startTick < 0)
			{
				startTick = run.gameTicks();
			}
			int waited = run.gameTicks() - startTick;
			return waited >= ticks ? Poll.pass(waited + " ticks") : Poll.waiting(waited + " of " + ticks + " ticks");
		}

		@Override
		String describe()
		{
			return "wait " + ticks + " ticks";
		}
	}

	/**
	 * Passes when a hitsplat has shown on the player, or on the NPC of the given name ({@link Game#npcIndex}: the one
	 * the player is fighting, else the nearest), since the last step that acted; with an amount, one showing exactly
	 * that (0 is a miss's blue splat).
	 */
	static final class AssertHitsplat extends Step
	{
		/** The NPC's name; null for the player. */
		final String npc;
		/** -1 for any. */
		final int amount;

		AssertHitsplat(long timeoutMs, String npc, int amount)
		{
			super("assertHitsplat", timeoutMs, false);
			this.npc = npc;
			this.amount = amount;
		}

		@Override
		Poll poll(Run run)
		{
			int index = npc == null ? Run.SELF : run.game.npcIndex(npc, -1);
			if (index < 0 && npc != null)
			{
				return Poll.waiting("no NPC \"" + npc + "\" in view");
			}
			Run.Hitsplat seen = run.hitsplatSinceAction(index, amount);
			return seen != null ? Poll.pass("hitsplat " + seen) : Poll.waiting("no hitsplat " + what() + " since the last action");
		}

		private String what()
		{
			return (amount < 0 ? "" : "of " + amount + " ") + "on " + (npc == null ? "you" : npc);
		}

		@Override
		String describe()
		{
			return "assert hitsplat " + what();
		}
	}

	/**
	 * Passes when the NPC of the name ({@link Game#npcIndex}: the one the player is fighting, else the nearest) shows a
	 * health bar within the given bounds, as a percentage. The client is only told an NPC's health once it is hit, so
	 * this also waits for the first hit to show.
	 */
	static final class AssertNpcHealth extends Step
	{
		final String npc;
		final int npcId;
		/** Percentages; -1 for no bound. */
		final int min;
		final int max;

		AssertNpcHealth(long timeoutMs, String npc, int npcId, int min, int max)
		{
			super("assertNpcHealth", timeoutMs, false);
			this.npc = npc;
			this.npcId = npcId;
			this.min = min;
			this.max = max;
		}

		@Override
		Poll poll(Run run)
		{
			Game.Health h = run.game.npcHealth(npc, npcId);
			if (h == null)
			{
				return Poll.waiting("no NPC " + who() + " in view");
			}
			int percent = h.percent();
			if (percent < 0)
			{
				return Poll.waiting(who() + " shows no health bar");
			}
			boolean ok = (min < 0 || percent >= min) && (max < 0 || percent <= max);
			return ok ? Poll.pass(who() + " at " + h) : Poll.waiting(who() + " at " + h);
		}

		private String who()
		{
			return npc != null ? npc : "NPC " + npcId;
		}

		@Override
		String describe()
		{
			String bounds = min < 0 ? "at most " + max + "%" : max < 0 ? "at least " + min + "%" : min + "-" + max + "%";
			return "assert " + who() + " health " + bounds;
		}
	}

	/** Passes when the player's level and experience in a skill are at least the given ones. */
	static final class AssertStat extends Step
	{
		/** The skills a script may name, as the stats tab does. */
		static final List<String> SKILLS = List.of("Attack", "Defence", "Strength", "Hitpoints", "Ranged", "Prayer", "Magic",
			"Cooking", "Woodcutting", "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing", "Mining", "Herblore",
			"Agility", "Thieving", "Slayer", "Farming", "Runecraft", "Hunter", "Construction", "Sailing");

		final String skill;
		/** -1 for no bound. */
		final int level;
		final int xp;

		AssertStat(long timeoutMs, String skill, int level, int xp)
		{
			super("assertStat", timeoutMs, false);
			this.skill = skill;
			this.level = level;
			this.xp = xp;
		}

		@Override
		Poll poll(Run run)
		{
			int haveLevel = run.game.skillLevel(skill);
			int haveXp = run.game.skillXp(skill);
			if (haveLevel < 0 || haveXp < 0)
			{
				return Poll.waiting("the client has no " + skill + " stat");
			}
			String have = skill + " level " + haveLevel + ", " + haveXp + " xp";
			boolean ok = (level < 0 || haveLevel >= level) && (xp < 0 || haveXp >= xp);
			return ok ? Poll.pass(have) : Poll.waiting(have);
		}

		@Override
		String describe()
		{
			return "assert " + skill + (level < 0 ? "" : " level " + level + "+") + (xp < 0 ? "" : " xp " + xp + "+");
		}
	}

	/** Passes when an item lies on the ground in the scene, or, with absent, when none does. */
	static final class AssertGroundItem extends Step
	{
		final String item;
		final int itemId;
		/** Only an item on this tile counts, when set. Its plane is ignored: items are looked for on the player's plane. */
		final Tile at;
		final boolean absent;

		AssertGroundItem(long timeoutMs, String item, int itemId, Tile at, boolean absent)
		{
			super("assertGroundItem", timeoutMs, false);
			this.item = item;
			this.itemId = itemId;
			this.at = at;
			this.absent = absent;
		}

		@Override
		Poll poll(Run run)
		{
			if (absent)
			{
				// Absence can only be seen on a tile the client has loaded, with a player to see it.
				if (run.game.location() == null)
				{
					return Poll.waiting("no local player");
				}
				if (at != null && !run.game.tileLoaded(at))
				{
					return Poll.waiting("tile " + at.x + ", " + at.y + " is not in the loaded scene");
				}
			}
			String missing = run.game.groundItem(item, itemId, at);
			if (absent)
			{
				return missing != null ? Poll.pass(missing) : Poll.waiting(what() + " is on the ground");
			}
			return missing == null ? Poll.pass(what() + " is on the ground") : Poll.waiting(missing);
		}

		private String what()
		{
			return (item != null ? item : "item " + itemId) + (at == null ? "" : " at " + at.x + ", " + at.y);
		}

		@Override
		String describe()
		{
			return "assert " + (absent ? "no " : "") + what() + " on the ground";
		}
	}

	/**
	 * Passes when the player, or the NPC of the given name ({@link Game#npcIndex}), has started the animation since the
	 * last step that acted.
	 */
	static final class AssertAnimation extends Step
	{
		/** The NPC's name; null for the player. */
		final String npc;
		final int id;

		AssertAnimation(long timeoutMs, String npc, int id)
		{
			super("assertAnimation", timeoutMs, false);
			this.npc = npc;
			this.id = id;
		}

		@Override
		Poll poll(Run run)
		{
			int index = npc == null ? Run.SELF : run.game.npcIndex(npc, -1);
			if (index < 0 && npc != null)
			{
				return Poll.waiting("no NPC \"" + npc + "\" in view");
			}
			Run.Animation seen = run.animationSinceAction(index, id);
			return seen != null ? Poll.pass(seen.toString()) : Poll.waiting("no animation " + id + " on " + who() + " since the last action");
		}

		private String who()
		{
			return npc == null ? "you" : npc;
		}

		@Override
		String describe()
		{
			return "assert animation " + id + " on " + who();
		}
	}
}
