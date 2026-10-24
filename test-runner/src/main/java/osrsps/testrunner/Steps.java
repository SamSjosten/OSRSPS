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
		final String text;
		final String expectMessage;
		private boolean sent;

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
			}
			if (run.game.chatPending())
			{
				return Poll.waiting("the chat input still holds the text: the client has not taken the Enter key");
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
			if (at.x == x && at.y == y && (plane < 0 || at.plane == plane))
			{
				return Poll.pass("at " + at);
			}
			return Poll.waiting("at " + at);
		}

		@Override
		String describe()
		{
			return "assert location " + x + ", " + y + (plane < 0 ? "" : ", " + plane);
		}
	}

	/** Clicks Walk here on a tile and waits until the player stands on it. */
	static final class Walk extends Step
	{
		final int x;
		final int y;
		private boolean sent;

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
			if (at.x == x && at.y == y)
			{
				return Poll.pass("at " + at);
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
}
