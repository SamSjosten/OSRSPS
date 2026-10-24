package osrsps.testrunner;

/**
 * One step of a test script. The runner polls it every client cycle (20 ms) until it passes, fails or runs out of
 * time. A step that acts does so once, when it first can, and then waits for the client to show the result: so an
 * assert right after an action waits for the server's answer instead of failing on the old state.
 */
abstract class Step
{
	enum State
	{
		PASS,
		WAIT,
		FAIL
	}

	static final class Poll
	{
		final State state;
		/** For PASS, what was seen; for WAIT, what is still missing (said if the step times out); for FAIL, why. */
		final String detail;

		private Poll(State state, String detail)
		{
			this.state = state;
			this.detail = detail;
		}

		static Poll pass(String detail)
		{
			return new Poll(State.PASS, detail);
		}

		static Poll waiting(String detail)
		{
			return new Poll(State.WAIT, detail);
		}

		static Poll fail(String detail)
		{
			return new Poll(State.FAIL, detail);
		}
	}

	/** The step's name in the script, such as "walk". */
	final String type;
	final long timeoutMs;
	/** True for steps that may run while logged out; every other step waits for a login. */
	final boolean runsLoggedOut;

	Step(String type, long timeoutMs, boolean runsLoggedOut)
	{
		this.type = type;
		this.timeoutMs = timeoutMs;
		this.runsLoggedOut = runsLoggedOut;
	}

	abstract Poll poll(Run run);

	/** One line saying what the step does, for the result file. */
	abstract String describe();
}
