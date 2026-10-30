package osrsps.testrunner;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

	/**
	 * Clicks an option on another player, once they are in view (and, with a tile, standing on it); does not wait for the
	 * result.
	 */
	static final class PlayerOption extends Step
	{
		final String player;
		final String option;
		/** Where the player must stand first, or null for anywhere: a two-VM script's sign the other side is ready. */
		final Tile at;

		PlayerOption(long timeoutMs, String player, String option, Tile at)
		{
			super("playerOption", timeoutMs, false);
			this.player = player;
			this.option = option;
			this.at = at;
		}

		@Override
		Poll poll(Run run)
		{
			run.markAction();
			String error = run.game.playerOption(player, option, at);
			// The other player may not be in view yet (still logging in on the other VM): keep trying until the timeout.
			return error == null ? Poll.pass("clicked") : Poll.waiting(error);
		}

		@Override
		String describe()
		{
			return option + " on player " + player;
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

	/** (0.10.0) Clicks an op on an item on the ground, as {@link TakeItem} clicks Take. Does not wait for the result. */
	static final class GroundItemOption extends Step
	{
		final String item;
		final int itemId;
		final int op;
		final String option;
		final Tile at;

		GroundItemOption(long timeoutMs, String item, int itemId, int op, String option, Tile at)
		{
			super("groundItemOption", timeoutMs, false);
			this.item = item;
			this.itemId = itemId;
			this.op = op;
			this.option = option;
			this.at = at;
		}

		@Override
		Poll poll(Run run)
		{
			run.markAction();
			String error = run.game.groundItemOption(item, itemId, op, option, at);
			return error == null ? Poll.pass("clicked") : Poll.waiting(error);
		}

		@Override
		String describe()
		{
			return option + " (op " + op + ") on " + (item != null ? item : "item " + itemId) + (at == null ? "" : " at " + at.x + ", " + at.y);
		}
	}

	/** (0.10.0) Uses one inventory item on another. Waits (to the timeout) for both; does not wait for the result. */
	static final class UseItemOnItem extends Step
	{
		final String item;
		final int itemId;
		final String onItem;
		final int onItemId;

		UseItemOnItem(long timeoutMs, String item, int itemId, String onItem, int onItemId)
		{
			super("useItemOnItem", timeoutMs, false);
			this.item = item;
			this.itemId = itemId;
			this.onItem = onItem;
			this.onItemId = onItemId;
		}

		@Override
		Poll poll(Run run)
		{
			run.markAction();
			String error = run.game.useItemOnItem(item, itemId, onItem, onItemId);
			// Items the last command gave may not be in the client's inventory yet: keep trying.
			return error == null ? Poll.pass("clicked") : Poll.waiting(error);
		}

		@Override
		String describe()
		{
			return "use " + (item != null ? item : "item " + itemId) + " on " + (onItem != null ? onItem : "item " + onItemId);
		}
	}

	/** (0.12.0) Uses an inventory item on a loc. Waits (to the timeout) for both; does not wait for the result. */
	static final class UseItemOnLoc extends Step
	{
		final String item;
		final int itemId;
		final String loc;
		final int locId;
		final Tile at;

		UseItemOnLoc(long timeoutMs, String item, int itemId, String loc, int locId, Tile at)
		{
			super("useItemOnLoc", timeoutMs, false);
			this.item = item;
			this.itemId = itemId;
			this.loc = loc;
			this.locId = locId;
			this.at = at;
		}

		@Override
		Poll poll(Run run)
		{
			run.markAction();
			String error = run.game.useItemOnLoc(item, itemId, loc, locId, at);
			return error == null ? Poll.pass("clicked") : Poll.waiting(error);
		}

		@Override
		String describe()
		{
			return "use " + (item != null ? item : "item " + itemId) + " on " + (loc != null ? loc : "loc " + locId)
				+ (at == null ? "" : " at " + at.x + ", " + at.y);
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

	/** Passes once an interface is open, or, with {@code absent}, once it is not. */
	static final class AssertInterface extends Step
	{
		final int interfaceId;
		final boolean absent;

		AssertInterface(long timeoutMs, int interfaceId, boolean absent)
		{
			super("assertInterface", timeoutMs, false);
			this.interfaceId = interfaceId;
			this.absent = absent;
		}

		@Override
		Poll poll(Run run)
		{
			boolean open = run.game.interfaceOpen(interfaceId);
			String seen = "interface " + interfaceId + (open ? " is open" : " is not open");
			return open != absent ? Poll.pass(seen) : Poll.waiting(seen);
		}

		@Override
		String describe()
		{
			return "assert interface " + interfaceId + (absent ? " closed" : " open");
		}
	}

	/** Clicks the mouse on a component, for what reacts to the mouse rather than to a menu option. */
	static final class MouseClick extends Step
	{
		final int interfaceId;
		final int component;
		final int sub;

		MouseClick(long timeoutMs, int interfaceId, int component, int sub)
		{
			super("mouseClick", timeoutMs, false);
			this.interfaceId = interfaceId;
			this.component = component;
			this.sub = sub;
		}

		@Override
		Poll poll(Run run)
		{
			run.markAction();
			String error = run.game.mouseClickWidget(interfaceId, component, sub);
			return error == null ? Poll.pass("clicked") : Poll.waiting(error);
		}

		@Override
		String describe()
		{
			return "mouse click on " + interfaceId + ":" + component + (sub < 0 ? "" : " slot " + sub);
		}
	}

	/** Runs a client script from the cache, as an interface of the client's would. */
	static final class RunClientScript extends Step
	{
		final int id;
		final Object[] args;

		RunClientScript(long timeoutMs, int id, Object[] args)
		{
			super("runClientScript", timeoutMs, false);
			this.id = id;
			this.args = args;
		}

		@Override
		Poll poll(Run run)
		{
			run.markAction();
			String error = run.game.runClientScript(id, args);
			return error == null ? Poll.pass("ran") : Poll.waiting(error);
		}

		@Override
		String describe()
		{
			return "run client script " + id + " " + java.util.Arrays.toString(args);
		}
	}

	/**
	 * Passes when a varp or varbit holds the value in both the server's copy and the client's. A setting the client
	 * changed for itself and the server ignored shows here as the client's copy alone holding the new value.
	 */
	static final class AssertVar extends Step
	{
		final boolean varbit;
		final int id;
		final int value;

		AssertVar(long timeoutMs, boolean varbit, int id, int value)
		{
			super(varbit ? "assertVarbit" : "assertVarp", timeoutMs, false);
			this.varbit = varbit;
			this.id = id;
			this.value = value;
		}

		@Override
		Poll poll(Run run)
		{
			int[] values = varbit ? run.game.varbit(id) : run.game.varp(id);
			String what = (varbit ? "varbit " : "varp ") + id;
			if (values == null)
			{
				return Poll.waiting("the client has no " + what);
			}
			String seen = what + ": server " + values[0] + ", client " + values[1];
			return values[0] == value && values[1] == value ? Poll.pass(seen) : Poll.waiting(seen);
		}

		@Override
		String describe()
		{
			return "assert " + (varbit ? "varbit " : "varp ") + id + " = " + value;
		}
	}

	static final class AssertWeight extends Step
	{
		final int kg;

		AssertWeight(long timeoutMs, int kg)
		{
			super("assertWeight", timeoutMs, false);
			this.kg = kg;
		}

		@Override
		Poll poll(Run run)
		{
			int weight = run.game.weight();
			if (weight == Integer.MIN_VALUE)
			{
				return Poll.waiting("logged out");
			}
			String seen = "weight " + weight + " kg";
			return weight == kg ? Poll.pass(seen) : Poll.waiting(seen);
		}

		@Override
		String describe()
		{
			return "assert weight " + kg + " kg";
		}
	}

	/** (0.11.0) Types an amount into the chatbox's number prompt, as Withdraw-X or a settings number asks for one. */
	static final class EnterAmount extends Step
	{
		/** meslayer_mode7's mode: the chatbox asks for a number. */
		static final int NUMBER_PROMPT = 7;

		final int amount;
		private boolean sent;

		EnterAmount(long timeoutMs, int amount)
		{
			super("enterAmount", timeoutMs, false);
			this.amount = amount;
		}

		@Override
		Poll poll(Run run)
		{
			int mode = run.game.promptMode();
			if (!sent)
			{
				if (mode != NUMBER_PROMPT)
				{
					return Poll.waiting("no number prompt open (meslayermode " + mode + ")");
				}
				run.markAction();
				String error = run.game.enterAmount(amount);
				if (error != null)
				{
					return Poll.fail(error);
				}
				sent = true;
				return Poll.waiting("entered, waiting for the prompt to close");
			}
			return mode == NUMBER_PROMPT ? Poll.waiting("the prompt is still open") : Poll.pass("entered " + amount);
		}

		@Override
		String describe()
		{
			return "enter amount " + amount;
		}
	}

	/**
	 * (0.12.0) Types text as key presses, for what reads the keyboard itself, such as the All Settings search. Passes a
	 * game tick after the keys reach the canvas, so the client has run its key scripts before the next step acts.
	 */
	static final class TypeText extends Step
	{
		final String text;
		private boolean sent;
		private int deliveredTick = -1;

		TypeText(long timeoutMs, String text)
		{
			super("typeText", timeoutMs, false);
			this.text = text;
		}

		@Override
		Poll poll(Run run)
		{
			if (!sent)
			{
				run.markAction();
				String error = run.game.typeText(text);
				if (error != null)
				{
					return Poll.waiting(error);
				}
				sent = true;
			}
			if (deliveredTick < 0)
			{
				if (run.game.typingPending())
				{
					return Poll.waiting("the keys aren't sent yet");
				}
				deliveredTick = run.gameTicks();
			}
			return run.gameTicks() > deliveredTick ? Poll.pass("typed") : Poll.waiting("typed, waiting a tick for the client to take the keys");
		}

		@Override
		String describe()
		{
			return "type \"" + text + "\"";
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

	/**
	 * Passes when the player (or the NPC with this name) was given the spotanim (or one of them) since the last step that acted. A spotanim
	 * the actor already had is reported again when it gets another, so one still playing from before can count too.
	 */
	static final class AssertSpotanim extends Step
	{
		/** The NPC's name; null for the player. */
		final String npc;
		/** Any one of these passes. */
		final int[] ids;

		AssertSpotanim(long timeoutMs, String npc, int[] ids)
		{
			super("assertSpotanim", timeoutMs, false);
			this.npc = npc;
			this.ids = ids;
		}

		@Override
		Poll poll(Run run)
		{
			int index = npc == null ? Run.SELF : run.game.npcIndex(npc, -1);
			if (index < 0 && npc != null)
			{
				return Poll.waiting("no NPC \"" + npc + "\" in view");
			}
			for (int id : ids)
			{
				Run.Spotanim seen = run.spotanimSinceAction(index, id);
				if (seen != null)
				{
					return Poll.pass(seen.toString());
				}
			}
			return Poll.waiting("no spotanim " + idList() + " on " + who() + " since the last action");
		}

		private String idList()
		{
			StringBuilder b = new StringBuilder();
			for (int i = 0; i < ids.length; i++)
			{
				b.append(i == 0 ? "" : i == ids.length - 1 ? " or " : ", ").append(ids[i]);
			}
			return b.toString();
		}

		private String who()
		{
			return npc == null ? "you" : npc;
		}

		@Override
		String describe()
		{
			return "assert spotanim " + idList() + " on " + who();
		}
	}

	/**
	 * Picks a spell in an interface (the spellbook) and casts it on an NPC. Waits (to the timeout) for the interface and
	 * the NPC; does not wait for the result.
	 */
	static final class CastOnNpc extends Step
	{
		final int interfaceId;
		final int component;
		final String npc;
		final int npcId;

		CastOnNpc(long timeoutMs, int interfaceId, int component, String npc, int npcId)
		{
			super("castOnNpc", timeoutMs, false);
			this.interfaceId = interfaceId;
			this.component = component;
			this.npc = npc;
			this.npcId = npcId;
		}

		@Override
		Poll poll(Run run)
		{
			run.markAction();
			String error = run.game.castOnNpc(interfaceId, component, npc, npcId);
			return error == null ? Poll.pass("cast") : Poll.waiting(error);
		}

		@Override
		String describe()
		{
			return "cast " + interfaceId + ":" + component + " on " + (npc != null ? npc : "NPC " + npcId);
		}
	}

	/** Clicks an option on the first such item in the inventory, retrying until there is one. */
	static final class ItemOption extends Step
	{
		final String item;
		final int itemId;
		final String option;

		ItemOption(long timeoutMs, String item, int itemId, String option)
		{
			super("itemOption", timeoutMs, false);
			this.item = item;
			this.itemId = itemId;
			this.option = option;
		}

		@Override
		Poll poll(Run run)
		{
			run.markAction();
			String error = run.game.itemOption(item, itemId, option);
			// An item the last command gave may not be in the client's inventory yet: keep trying.
			return error == null ? Poll.pass("clicked") : Poll.waiting(error);
		}

		@Override
		String describe()
		{
			return option + " " + (item != null ? item : "item " + itemId) + " in the inventory";
		}
	}

	/** The equipment slots a script may name, by wearpos: the worn inventory's slot numbers. */
	static final Map<String, Integer> WORN_SLOTS = wornSlots();

	private static Map<String, Integer> wornSlots()
	{
		Map<String, Integer> m = new LinkedHashMap<>();
		m.put("head", 0);
		m.put("cape", 1);
		m.put("neck", 2);
		m.put("weapon", 3);
		m.put("body", 4);
		m.put("shield", 5);
		m.put("legs", 7);
		m.put("hands", 9);
		m.put("feet", 10);
		m.put("ring", 12);
		m.put("ammo", 13);
		return Collections.unmodifiableMap(m);
	}

	static String slotName(int wearpos)
	{
		for (Map.Entry<String, Integer> e : WORN_SLOTS.entrySet())
		{
			if (e.getValue() == wearpos)
			{
				return e.getKey();
			}
		}
		return "slot " + wearpos;
	}

	/** Clicks an option (Remove, Examine, or one of the item's own) on a slot of the equipment tab. */
	static final class WornOption extends Step
	{
		final int wearpos;
		final String option;

		WornOption(long timeoutMs, int wearpos, String option)
		{
			super("wornOption", timeoutMs, false);
			this.wearpos = wearpos;
			this.option = option;
		}

		@Override
		Poll poll(Run run)
		{
			run.markAction();
			String error = run.game.wornOption(wearpos, option);
			return error == null ? Poll.pass("clicked") : Poll.waiting(error);
		}

		@Override
		String describe()
		{
			return option + " on the " + slotName(wearpos) + " slot";
		}
	}

	/** Clicks an op on an interface component (or one slot of it), as its menu entry would. */
	static final class ClickWidget extends Step
	{
		final int interfaceId;
		final int component;
		/** -1 for the component itself. */
		final int sub;
		final int op;

		ClickWidget(long timeoutMs, int interfaceId, int component, int sub, int op)
		{
			super("clickWidget", timeoutMs, false);
			this.interfaceId = interfaceId;
			this.component = component;
			this.sub = sub;
			this.op = op;
		}

		@Override
		Poll poll(Run run)
		{
			run.markAction();
			String error = run.game.clickWidget(interfaceId, component, sub, op);
			// An interface the server has just been asked for may not be open yet: keep trying.
			return error == null ? Poll.pass("clicked") : Poll.waiting(error);
		}

		@Override
		String describe()
		{
			return "click op " + op + " on " + interfaceId + ":" + component + (sub < 0 ? "" : " slot " + sub);
		}
	}

	/**
	 * Passes when a slot of the worn inventory holds the item (or is empty), and the player's model shows the same, for
	 * the slots the model shows.
	 */
	static final class AssertEquipment extends Step
	{
		final int wearpos;
		/** Both unset (null, -1) for an empty slot. */
		final String item;
		final int itemId;

		AssertEquipment(long timeoutMs, int wearpos, String item, int itemId)
		{
			super("assertEquipment", timeoutMs, false);
			this.wearpos = wearpos;
			this.item = item;
			this.itemId = itemId;
		}

		private boolean empty()
		{
			return item == null && itemId < 0;
		}

		private boolean matches(Game g, int id)
		{
			if (id < 0)
			{
				return false;
			}
			return item == null ? id == itemId : item.equalsIgnoreCase(g.itemName(id));
		}

		@Override
		Poll poll(Run run)
		{
			Game g = run.game;
			int worn = g.wornId(wearpos);
			int model = g.modelWornId(wearpos);
			String seen = "worn " + name(g, worn) + (model == Game.NOT_ON_MODEL ? "" : ", model shows " + name(g, model));
			boolean wornOk = empty() ? worn < 0 : matches(g, worn);
			boolean modelOk = model == Game.NOT_ON_MODEL || (empty() ? model < 0 : matches(g, model));
			return wornOk && modelOk ? Poll.pass(seen) : Poll.waiting(seen);
		}

		private static String name(Game g, int id)
		{
			if (id < 0)
			{
				return "nothing";
			}
			String n = g.itemName(id);
			return (n == null ? "item" : n) + " (" + id + ")";
		}

		@Override
		String describe()
		{
			return "assert " + slotName(wearpos) + " slot " + (empty() ? "is empty" : "holds " + (item != null ? item : "item " + itemId));
		}
	}

	/** Passes when the text of an interface component (or the whole interface) contains the given text. */
	static final class AssertWidgetText extends Step
	{
		/** Results quote at most this much of a component's text. */
		static final int QUOTE_LIMIT = 300;

		final int interfaceId;
		/** -1 for the whole interface. */
		final int component;
		final String contains;

		AssertWidgetText(long timeoutMs, int interfaceId, int component, String contains)
		{
			super("assertWidgetText", timeoutMs, false);
			this.interfaceId = interfaceId;
			this.component = component;
			this.contains = contains;
		}

		@Override
		Poll poll(Run run)
		{
			String text = run.game.widgetText(interfaceId, component);
			if (text == null)
			{
				return Poll.waiting("interface " + where() + " is not open");
			}
			String quoted = "\"" + (text.length() > QUOTE_LIMIT ? text.substring(0, QUOTE_LIMIT) + "..." : text) + "\"";
			return text.contains(contains) ? Poll.pass(quoted) : Poll.waiting("text is " + quoted);
		}

		private String where()
		{
			return interfaceId + (component < 0 ? "" : ":" + component);
		}

		@Override
		String describe()
		{
			return "assert " + where() + " says \"" + contains + "\"";
		}
	}

	/** Passes when the player stands in the given ready animation: the stance their weapon gives. */
	static final class AssertPose extends Step
	{
		final int idle;

		AssertPose(long timeoutMs, int idle)
		{
			super("assertPose", timeoutMs, false);
			this.idle = idle;
		}

		@Override
		Poll poll(Run run)
		{
			int pose = run.game.idlePose();
			return pose == idle ? Poll.pass("stands in " + pose) : Poll.waiting("stands in " + pose);
		}

		@Override
		String describe()
		{
			return "assert you stand in animation " + idle;
		}
	}

	/** Clicks the logout button, then waits until the client is logged out. Auto login then logs back in. */
	static final class Logout extends Step
	{
		private boolean clicked;

		Logout(long timeoutMs)
		{
			super("logout", timeoutMs, true);
		}

		@Override
		Poll poll(Run run)
		{
			if (!clicked)
			{
				if (!run.game.loggedIn())
				{
					return Poll.waiting("not logged in yet");
				}
				run.markAction();
				String error = run.game.logout();
				if (error != null)
				{
					return Poll.waiting(error);
				}
				clicked = true;
			}
			return run.game.loggedIn() ? Poll.waiting("clicked logout, still logged in") : Poll.pass("logged out");
		}

		@Override
		String describe()
		{
			return "log out";
		}
	}

	/**
	 * Types a name or a message into one of the chatbox's prompts the way a player does: opens the prompt (a click on a
	 * button, or the client script a menu option runs), waits for it to show in {@code mode}, enters the text and waits
	 * for it to close. The client's own scripts then check the text and tell the server, as for a typed one. Doesn't wait
	 * for the server's answer; an assert after it does.
	 */
	static final class PromptEntry extends Step
	{
		/** The Friends tab (429) and ignore tab (432) buttons and the prompt mode each opens (meslayer_enter). */
		static final int FRIENDS = 429;
		static final int ADD_FRIEND = 14;
		static final int DELETE_FRIEND = 16;
		static final int IGNORES = 432;
		static final int ADD_IGNORE = 12;
		static final int DELETE_IGNORE = 14;
		/** meslayer_mode6, the proc a friend's Message option runs, opening the private message prompt (mode 6). */
		static final int MESSAGE_SCRIPT = 107;
		/** meslayer_mode10, the proc Join Chat runs (joinchat_onop), opening the channel name prompt (mode 10). */
		static final int JOIN_CHAT_SCRIPT = 111;
		/** chatchannel_current's Setup button, and chatchannel_setup's channel name option (Set prefix). */
		static final int FRIENDS_CHAT = 7;
		static final int FRIENDS_CHAT_SETUP = 20;
		static final int FRIENDS_CHAT_SETUP_SCREEN = 94;
		static final int FRIENDS_CHAT_PREFIX = 10;

		final String what;
		final int mode;
		final String text;
		/** Opens the prompt: null when done, else why not yet. */
		private final java.util.function.Function<Game, String> open;
		private boolean listsArrived;
		private boolean opened;
		private boolean sent;

		private PromptEntry(String type, long timeoutMs, String what, int mode, String text, java.util.function.Function<Game, String> open)
		{
			super(type, timeoutMs, false);
			this.what = what;
			this.mode = mode;
			this.text = text;
			this.open = open;
		}

		static PromptEntry addFriend(long timeoutMs, String name)
		{
			return new PromptEntry("addFriend", timeoutMs, "add friend " + name, 2, name, g -> g.clickWidget(FRIENDS, ADD_FRIEND, -1, 1));
		}

		static PromptEntry deleteFriend(long timeoutMs, String name)
		{
			return new PromptEntry("deleteFriend", timeoutMs, "delete friend " + name, 3, name, g -> g.clickWidget(FRIENDS, DELETE_FRIEND, -1, 1));
		}

		static PromptEntry addIgnore(long timeoutMs, String name)
		{
			return new PromptEntry("addIgnore", timeoutMs, "add ignore " + name, 4, name, g -> ignoreTab(g, ADD_IGNORE));
		}

		static PromptEntry deleteIgnore(long timeoutMs, String name)
		{
			return new PromptEntry("deleteIgnore", timeoutMs, "delete ignore " + name, 5, name, g -> ignoreTab(g, DELETE_IGNORE));
		}

		/** A friend's Message option: the private message prompt for {@code name}, then the message. */
		static PromptEntry privateMessage(long timeoutMs, String name, String text)
		{
			return new PromptEntry("privateMessage", timeoutMs, "message " + name + ": " + text, 6, text, g -> g.runClientScript(MESSAGE_SCRIPT, new Object[] {name}));
		}

		/** Join Chat: the channel name prompt, then the owner's name. */
		static PromptEntry joinFriendsChat(long timeoutMs, String owner)
		{
			return new PromptEntry("joinFriendsChat", timeoutMs, "join " + owner + "'s friends chat", 10, owner, g -> g.runClientScript(JOIN_CHAT_SCRIPT, new Object[0]));
		}

		/** The friends chat Setup screen's Set prefix: the server's text prompt (mode 9), then the channel's name. */
		static PromptEntry setFriendsChatName(long timeoutMs, String name)
		{
			return new PromptEntry("setFriendsChatName", timeoutMs, "name the friends chat " + name, 9, name, g ->
			{
				if (!g.interfaceOpen(FRIENDS_CHAT_SETUP_SCREEN))
				{
					String error = g.clickWidget(FRIENDS_CHAT, FRIENDS_CHAT_SETUP, -1, 1);
					return error != null ? error : "opening the setup screen";
				}
				return g.clickWidget(FRIENDS_CHAT_SETUP_SCREEN, FRIENDS_CHAT_PREFIX, -1, 1);
			});
		}

		/** Clicks {@code button} on the ignore tab, first opening the tab from the Friends tab when it isn't. */
		private static String ignoreTab(Game game, int button)
		{
			if (game.interfaceOpen(IGNORES))
			{
				return game.clickWidget(IGNORES, button, -1, 1);
			}
			String error = game.clickWidget(FRIENDS, 1, -1, 1);
			return error != null ? error : "opening the ignore tab";
		}

		@Override
		Poll poll(Run run)
		{
			int now = run.game.promptMode();
			if (!opened)
			{
				// The ignore tab replaces the Friends tab, whose widgets friends() reads, so an open ignore tab counts
				// as the lists having arrived, and so does a list seen before this step switched tabs.
				listsArrived = listsArrived || run.game.friends() != null || run.game.interfaceOpen(IGNORES);
				if (!listsArrived)
				{
					return Poll.waiting("the friends list hasn't arrived");
				}
				run.markAction();
				String error = open.apply(run.game);
				if (error != null)
				{
					return Poll.waiting(error);
				}
				opened = true;
				return Poll.waiting("opened the prompt");
			}
			if (!sent)
			{
				if (now != mode)
				{
					return Poll.waiting("no prompt in mode " + mode + " (meslayermode " + now + ")");
				}
				String error = run.game.enterText(text);
				if (error != null)
				{
					return Poll.fail(error);
				}
				sent = true;
				return Poll.waiting("entered, waiting for the prompt to close");
			}
			return now == mode ? Poll.waiting("the prompt is still open") : Poll.pass(what);
		}

		@Override
		String describe()
		{
			return what;
		}
	}

	/**
	 * Passes when the client is in {@code owner}'s friends chat channel, with {@code member} listed in it when given; or,
	 * with {@code absent}, when it is in no channel ({@code owner} and {@code member} unset) or {@code member} isn't
	 * listed. Names match without regard to case, spaces as underscores.
	 */
	static final class AssertFriendsChat extends Step
	{
		final String owner;
		final String member;
		final boolean absent;

		AssertFriendsChat(long timeoutMs, String owner, String member, boolean absent)
		{
			super("assertFriendsChat", timeoutMs, false);
			this.owner = owner;
			this.member = member;
			this.absent = absent;
		}

		@Override
		Poll poll(Run run)
		{
			String now = run.game.friendsChatOwner();
			if (owner == null)
			{
				return now == null ? Poll.pass("in no friends chat") : Poll.waiting("in " + now + "'s friends chat");
			}
			if (now == null || !AssertFriend.key(now).equals(AssertFriend.key(owner)))
			{
				return Poll.waiting(now == null ? "in no friends chat" : "in " + now + "'s friends chat");
			}
			if (member == null)
			{
				return Poll.pass("in " + owner + "'s friends chat");
			}
			java.util.List<String> members = run.game.friendsChatMembers();
			boolean listed = members.stream().anyMatch(m -> AssertFriend.key(m).equals(AssertFriend.key(member)));
			if (listed != absent)
			{
				return Poll.pass(member + (absent ? " not" : "") + " in " + owner + "'s friends chat");
			}
			return Poll.waiting("members " + members);
		}

		@Override
		String describe()
		{
			if (owner == null)
			{
				return "in no friends chat";
			}
			return member == null ? "in " + owner + "'s friends chat" : member + (absent ? " not" : "") + " in " + owner + "'s friends chat";
		}
	}

	/**
	 * Passes when the friends list shows {@code name} (matched without regard to case, spaces as underscores) on
	 * {@code world}, 0 for offline; or, with {@code absent}, when the name isn't on it.
	 */
	static final class AssertFriend extends Step
	{
		final String name;
		final int world;
		final boolean absent;

		AssertFriend(long timeoutMs, String name, int world, boolean absent)
		{
			super("assertFriend", timeoutMs, false);
			this.name = name;
			this.world = world;
			this.absent = absent;
		}

		@Override
		Poll poll(Run run)
		{
			java.util.Map<String, Integer> friends = run.game.friends();
			if (friends == null)
			{
				return Poll.waiting("the friends list hasn't arrived");
			}
			Integer seen = null;
			for (java.util.Map.Entry<String, Integer> e : friends.entrySet())
			{
				if (key(e.getKey()).equals(key(name)))
				{
					seen = e.getValue();
				}
			}
			String list = "friends " + friends;
			if (absent)
			{
				return seen == null ? Poll.pass(list) : Poll.waiting(list);
			}
			return seen != null && seen == world ? Poll.pass(list) : Poll.waiting(list);
		}

		static String key(String name)
		{
			return name.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_').replace('\u00a0', '_');
		}

		@Override
		String describe()
		{
			return absent ? "assert " + name + " is not a friend" : "assert friend " + name + (world == 0 ? " offline" : " on world " + world);
		}
	}

	/**
	 * Passes when the ignore list holds {@code name} (matched as {@link AssertFriend} matches), or, with {@code absent},
	 * when it doesn't.
	 */
	static final class AssertIgnore extends Step
	{
		final String name;
		final boolean absent;

		AssertIgnore(long timeoutMs, String name, boolean absent)
		{
			super("assertIgnore", timeoutMs, false);
			this.name = name;
			this.absent = absent;
		}

		@Override
		Poll poll(Run run)
		{
			java.util.List<String> ignores = run.game.ignores();
			if (ignores == null)
			{
				return Poll.waiting("the ignore list hasn't arrived");
			}
			boolean seen = ignores.stream().anyMatch(n -> AssertFriend.key(n).equals(AssertFriend.key(name)));
			String list = "ignored " + ignores;
			return seen != absent ? Poll.pass(list) : Poll.waiting(list);
		}

		@Override
		String describe()
		{
			return absent ? "assert " + name + " is not ignored" : "assert " + name + " is ignored";
		}
	}
}
