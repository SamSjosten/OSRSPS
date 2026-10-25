package osrsps.testrunner;

/**
 * When to log the test account in from the login screen: a few seconds after the screen shows (so the client has
 * finished loading), then again every {@link #RETRY_MS} while it stays there, up to {@link #MAX_TRIES} tries in a row.
 * A login resets the count. Someone typing another name over the one it typed, or logging in under another name, takes
 * the client over until the plugin restarts, so a second client on the same machine can log in another account. Called from the client thread and the
 * plugin's executor, so every method is synchronized.
 */
final class AutoLogin
{
	static final long SETTLE_MS = 3_000;
	static final long RETRY_MS = 15_000;
	static final int MAX_TRIES = 5;

	private boolean onLoginScreen;
	private long nextTryAt;
	private int tries;
	private boolean manual;

	/** Forgets every try, as a plugin restart should: the plugin object, and so this one, outlives a restart. */
	synchronized void reset()
	{
		onLoginScreen = false;
		tries = 0;
		nextTryAt = 0;
		manual = false;
	}

	synchronized void loginScreen(long nowMs)
	{
		if (!onLoginScreen)
		{
			onLoginScreen = true;
			nextTryAt = Math.max(nextTryAt, nowMs + SETTLE_MS);
		}
	}

	/** Any other game state: logging in, loading or logged in. */
	synchronized void leftLoginScreen()
	{
		onLoginScreen = false;
	}

	/**
	 * A login, under {@code name}. The count starts again. Auto login only ever logs the account in, so a login under
	 * another name was a person's, who keeps the client as if they had typed over the account.
	 */
	synchronized void loggedIn(String name, String account)
	{
		if (differs(name, account))
		{
			manual = true;
		}
		onLoginScreen = false;
		tries = 0;
		nextTryAt = 0;
	}

	synchronized boolean due(long nowMs)
	{
		return !manual && onLoginScreen && tries < MAX_TRIES && nowMs >= nextTryAt;
	}

	/**
	 * True, and from then on never due again until {@link #reset}, when the login box holds a name other than the
	 * account after a try has put the account there: someone has typed over it. Before the first try the box can hold
	 * any name the client remembered, so it doesn't count.
	 */
	synchronized boolean takenOver(String typed, String account)
	{
		if (tries > 0 && differs(typed, account))
		{
			manual = true;
		}
		return manual;
	}

	/** A name, and an auto login account to compare it with, that aren't the same account. */
	private static boolean differs(String name, String account)
	{
		return name != null && !name.trim().isEmpty() && account != null && !account.trim().isEmpty()
			&& !Runner.normalise(name).equals(Runner.normalise(account));
	}

	synchronized void tried(long nowMs)
	{
		tries++;
		nextTryAt = nowMs + RETRY_MS;
	}

	synchronized boolean gaveUp()
	{
		return tries >= MAX_TRIES;
	}
}
