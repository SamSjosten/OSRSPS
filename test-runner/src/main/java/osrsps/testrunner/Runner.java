package osrsps.testrunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Runs one script at a time, a step per poll. The plugin calls {@link #poll} every client cycle, {@link #gameTick}
 * on each game tick, {@link #message} for each chat message, {@link #hitsplat} for each hitsplat and
 * {@link #animation} for each animation an actor starts, {@link #spotanim} for each spotanim one is given, all on the
 * client thread.
 */
final class Runner
{
	private final Game game;
	private final LongSupplier clock;
	private final Supplier<List<String>> allowedAccounts;

	private Script script;
	private String trigger;
	private Run run;
	private int stepIndex;
	private long scriptStartMs;
	private long stepStartMs;
	private int stepStartTick;
	private List<Result.StepResult> done;
	/**
	 * (0.25.0) Game ticks since the client last logged in. It starts settled, for a client already logged in when the
	 * plugin starts.
	 */
	private int ticksSinceLogin = Steps.WaitForLogin.SETTLE_TICKS;
	/** (0.27.0) Whether the running script's last {@code ::keys} command left the server showing keys. */
	private boolean keysOn;
	/**
	 * (0.27.0) A {@code ::keys off} sent after a script that left keys on, so a client someone is watching between
	 * scripts shows the dialogue file's words, as a player sees them, not {@code [key]}. Not part of any result.
	 */
	private Step tidy;
	private Run tidyRun;
	private long tidyStartMs;
	/** The account the tidy-up is for: it is sent only while that account, still a test account, is logged in. */
	private String tidyAccount;

	Runner(Game game, LongSupplier clock, Supplier<List<String>> allowedAccounts)
	{
		this.game = game;
		this.clock = clock;
		this.allowedAccounts = allowedAccounts;
	}

	boolean busy()
	{
		return script != null || tidy != null;
	}

	/** Name of the running script, or null. */
	String running()
	{
		return script == null ? null : script.id;
	}

	void start(Script s, String trigger)
	{
		script = s;
		this.trigger = trigger;
		run = new Run(game, () -> ticksSinceLogin);
		stepIndex = 0;
		keysOn = false;
		done = new ArrayList<>();
		scriptStartMs = clock.getAsLong();
		startStep();
	}

	/** The client has just logged in (the plugin calls this once per login, not after each region load). */
	void loggedIn()
	{
		ticksSinceLogin = 0;
		// A login starts with keys off, so a tidy-up still owed from before it has nothing left to do, and a script that
		// spans the login starts counting its ::keys commands again from off.
		dropTidy();
		keysOn = false;
	}

	void gameTick()
	{
		if (ticksSinceLogin < Integer.MAX_VALUE)
		{
			ticksSinceLogin++;
		}
		if (run != null)
		{
			run.gameTick();
		}
		if (tidyRun != null)
		{
			tidyRun.gameTick();
		}
	}

	void message(String text)
	{
		if (run != null)
		{
			run.message(text);
		}
		if (tidyRun != null)
		{
			tidyRun.message(text);
		}
	}

	/** A hitsplat shown on the player ({@code index} {@link Run#SELF}) or on an NPC by index and name. */
	void hitsplat(int index, String npc, int type, int amount)
	{
		if (run != null)
		{
			run.hitsplat(index, npc, type, amount);
		}
	}

	/** An animation the player ({@code index} {@link Run#SELF}) or an NPC by index and name started. */
	void animation(int index, String npc, int id)
	{
		if (run != null)
		{
			run.animation(index, npc, id);
		}
	}

	/** A spotanim the player ({@code index} {@link Run#SELF}) or an NPC by index and name was given. */
	void spotanim(int index, String npc, int id)
	{
		if (run != null)
		{
			run.spotanim(index, npc, id);
		}
	}

	/** Stops the running script, as a failure of its current step. */
	Result abort(String why)
	{
		return script == null ? null : finish(Step.Poll.fail(why));
	}

	/** Advances the running script; returns its result once it has finished, else null. */
	Result poll()
	{
		if (script == null)
		{
			pollTidy();
			return null;
		}
		while (script != null)
		{
			Step step = script.steps.get(stepIndex);
			Step.Poll p;
			String guard = accountProblem();
			if (guard != null)
			{
				p = Step.Poll.fail(guard);
			}
			else if (!step.runsLoggedOut && !game.loggedIn())
			{
				p = Step.Poll.waiting("not logged in");
			}
			else
			{
				p = step.poll(run);
			}

			long now = clock.getAsLong();
			if (p.state == Step.State.WAIT)
			{
				if (now - stepStartMs < step.timeoutMs)
				{
					return null;
				}
				p = Step.Poll.fail("timed out after " + step.timeoutMs + " ms: " + p.detail);
			}
			if (p.state == Step.State.FAIL)
			{
				return finish(p);
			}
			done.add(stepResult(step, "pass", p.detail, now));
			noteKeys(step);
			stepIndex++;
			if (stepIndex == script.steps.size())
			{
				return finish(null);
			}
			// The next step starts in the same poll: an assert that already holds passes without waiting a cycle.
			startStep();
		}
		return null;
	}

	/**
	 * The guard that keeps scripts off any account but our test ones: whoever is logged in must be the script's
	 * account, and that account must be on the plugin's allow list.
	 */
	private String accountProblem()
	{
		if (!isAllowed(script.account))
		{
			return "account \"" + script.account + "\" is not in the plugin's test accounts " + allowedAccounts.get();
		}
		if (!game.loggedIn())
		{
			return null;
		}
		String name = game.localName();
		if (name != null && !normalise(name).equals(normalise(script.account)))
		{
			return "logged in as \"" + name + "\", but this script is for \"" + script.account + "\"";
		}
		return null;
	}

	private boolean isAllowed(String account)
	{
		for (String a : allowedAccounts.get())
		{
			if (normalise(a).equals(normalise(account)))
			{
				return true;
			}
		}
		return false;
	}

	/** Names as OSRS compares them: case and space versus underscore or non-breaking space don't matter. */
	static String normalise(String name)
	{
		return name.replace(' ', ' ').replace('_', ' ').trim().toLowerCase(Locale.ROOT);
	}

	private void startStep()
	{
		stepStartMs = clock.getAsLong();
		stepStartTick = run.gameTicks();
	}

	private Result.StepResult stepResult(Step step, String outcome, String detail, long now)
	{
		return new Result.StepResult(stepIndex + 1, step.type, step.describe(), outcome, detail, now - stepStartMs, run.gameTicks() - stepStartTick);
	}

	private Result finish(Step.Poll failure)
	{
		long now = clock.getAsLong();
		String failText = null;
		if (failure != null)
		{
			Step step = script.steps.get(stepIndex);
			done.add(stepResult(step, "fail", failure.detail, now));
			// The command may have reached the server though its reply never matched: ::keys off is harmless either way.
			if (isKeysCommand(step))
			{
				keysOn = true;
			}
			failText = "step " + (stepIndex + 1) + " (" + step.describe() + "): " + failure.detail;
			for (int i = stepIndex + 1; i < script.steps.size(); i++)
			{
				Step skipped = script.steps.get(i);
				done.add(new Result.StepResult(i + 1, skipped.type, skipped.describe(), "skipped", null, 0, 0));
			}
		}
		String account = script.account;
		Result r = new Result(script.id, script.name, trigger, failure == null ? Result.PASS : Result.FAIL, failText,
			scriptStartMs, now - scriptStartMs, run.gameTicks(), done);
		script = null;
		run = null;
		done = null;
		if (keysOn)
		{
			tidy = new Steps.Chat("command", TIDY_TIMEOUT_MS, KEYS_OFF, KEYS_OFF_REPLY);
			tidyRun = new Run(game, () -> ticksSinceLogin);
			tidyStartMs = now;
			tidyAccount = account;
		}
		keysOn = false;
		return r;
	}

	/** A chat step's words split on whitespace as the server splits a command, or null for any other step. */
	private static String[] keysCommand(Step step)
	{
		if (!(step instanceof Steps.Chat))
		{
			return null;
		}
		String[] words = ((Steps.Chat) step).text.trim().toLowerCase(Locale.ROOT).split("\\s+");
		return words[0].equals("::keys") ? words : null;
	}

	private static boolean isKeysCommand(Step step)
	{
		return keysCommand(step) != null;
	}

	/** Keeps track of what a passed {@code ::keys} command left on: {@code ::keys} alone flips it. */
	private void noteKeys(Step step)
	{
		String[] words = keysCommand(step);
		if (words == null)
		{
			return;
		}
		if (words.length == 1)
		{
			keysOn = !keysOn;
		}
		else if (words.length == 2 && (words[1].equals("on") || words[1].equals("off")))
		{
			keysOn = words[1].equals("on");
		}
	}

	/**
	 * Sends the {@code ::keys off} a finished script left owing, on the script's own account only while it is logged in
	 * and still a test account. It waits through region loads, gives up quietly when it times out or a fresh login
	 * comes (a login starts with keys off), and the next script waits for it, so the two never type at once.
	 */
	private void pollTidy()
	{
		if (tidy == null)
		{
			return;
		}
		boolean timedOut = clock.getAsLong() - tidyStartMs >= tidy.timeoutMs;
		if (timedOut || !isAllowed(tidyAccount))
		{
			dropTidy();
			return;
		}
		if (!game.loggedIn())
		{
			return;
		}
		String name = game.localName();
		if (name == null || !normalise(name).equals(normalise(tidyAccount)))
		{
			dropTidy();
			return;
		}
		if (tidy.poll(tidyRun).state != Step.State.WAIT)
		{
			dropTidy();
		}
	}

	private void dropTidy()
	{
		tidy = null;
		tidyRun = null;
		tidyAccount = null;
	}

	static final String KEYS_OFF = "::keys off";
	static final String KEYS_OFF_REPLY = "Dialogue shows words";
	/** How long the tidy-up may take before it is dropped. */
	static final long TIDY_TIMEOUT_MS = 10_000;
}
