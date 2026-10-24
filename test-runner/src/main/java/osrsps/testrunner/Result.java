package osrsps.testrunner;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.List;

/** How one run of a script went. {@link #toJson} is the result file's content. */
final class Result
{
	static final String PASS = "pass";
	static final String FAIL = "fail";
	/** The script could not be read, so no step ran. */
	static final String ERROR = "error";

	static final class StepResult
	{
		final int n;
		final String type;
		final String description;
		/** pass, fail or skipped. */
		final String outcome;
		final String detail;
		final long ms;
		final int ticks;

		StepResult(int n, String type, String description, String outcome, String detail, long ms, int ticks)
		{
			this.n = n;
			this.type = type;
			this.description = description;
			this.outcome = outcome;
			this.detail = detail;
			this.ms = ms;
			this.ticks = ticks;
		}
	}

	final String id;
	final String name;
	/** What started the run: "file" (dropped or changed in the tests folder) or "login". */
	final String trigger;
	final String result;
	/** For fail and error, one line saying what went wrong. */
	final String failure;
	final long startedAtMs;
	final long ms;
	final int ticks;
	final List<StepResult> steps;
	/** File name of the failure screenshot next to the result, or null. */
	String screenshot;

	Result(String id, String name, String trigger, String result, String failure, long startedAtMs, long ms, int ticks, List<StepResult> steps)
	{
		this.id = id;
		this.name = name;
		this.trigger = trigger;
		this.result = result;
		this.failure = failure;
		this.startedAtMs = startedAtMs;
		this.ms = ms;
		this.ticks = ticks;
		this.steps = steps;
	}

	static Result error(String id, String trigger, String why, long nowMs)
	{
		return new Result(id, id, trigger, ERROR, why, nowMs, 0, 0, List.of());
	}

	boolean passed()
	{
		return PASS.equals(result);
	}

	/** "hans-demo: pass, 9 steps in 8.4 s" or the failing step. */
	String summary()
	{
		if (passed())
		{
			return name + ": pass, " + steps.size() + " steps in " + String.format("%.1f", ms / 1000.0) + " s";
		}
		return name + ": " + result + ", " + failure;
	}

	JsonObject toJson(String recorderVersion)
	{
		JsonObject o = new JsonObject();
		o.addProperty("script", id);
		o.addProperty("name", name);
		o.addProperty("result", result);
		if (failure != null)
		{
			o.addProperty("failure", failure);
		}
		o.addProperty("startedAt", Instant.ofEpochMilli(startedAtMs).toString());
		o.addProperty("ms", ms);
		o.addProperty("ticks", ticks);
		o.addProperty("trigger", trigger);
		o.addProperty("runner", recorderVersion);
		if (screenshot != null)
		{
			o.addProperty("screenshot", screenshot);
		}
		JsonArray a = new JsonArray();
		for (StepResult s : steps)
		{
			JsonObject j = new JsonObject();
			j.addProperty("n", s.n);
			j.addProperty("step", s.type);
			j.addProperty("does", s.description);
			j.addProperty("result", s.outcome);
			if (s.detail != null)
			{
				j.addProperty("detail", s.detail);
			}
			if (!"skipped".equals(s.outcome))
			{
				j.addProperty("ms", s.ms);
				j.addProperty("ticks", s.ticks);
			}
			a.add(j);
		}
		o.add("steps", a);
		return o;
	}
}
