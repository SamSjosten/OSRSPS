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

	Runner(Game game, LongSupplier clock, Supplier<List<String>> allowedAccounts)
	{
		this.game = game;
		this.clock = clock;
		this.allowedAccounts = allowedAccounts;
	}

	boolean busy()
	{
		return script != null;
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
		run = new Run(game);
		stepIndex = 0;
		done = new ArrayList<>();
		scriptStartMs = clock.getAsLong();
		startStep();
	}

	void gameTick()
	{
		if (run != null)
		{
			run.gameTick();
		}
	}

	void message(String text)
	{
		if (run != null)
		{
			run.message(text);
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
			failText = "step " + (stepIndex + 1) + " (" + step.describe() + "): " + failure.detail;
			for (int i = stepIndex + 1; i < script.steps.size(); i++)
			{
				Step skipped = script.steps.get(i);
				done.add(new Result.StepResult(i + 1, skipped.type, skipped.describe(), "skipped", null, 0, 0));
			}
		}
		Result r = new Result(script.id, script.name, trigger, failure == null ? Result.PASS : Result.FAIL, failText,
			scriptStartMs, now - scriptStartMs, run.gameTicks(), done);
		script = null;
		run = null;
		done = null;
		return r;
	}
}
