package osrsps.testrunner;

/**
 * When to log the test account in from the login screen: a few seconds after the screen shows (so the client has
 * finished loading), then again every {@link #RETRY_MS} while it stays there, up to {@link #MAX_TRIES} tries in a row.
 * A login resets the count. A person at the client (a key pressed on the login screen, another name typed over the one
 * it typed, or a login under another name) takes the client over until the plugin restarts, so a second client on the
 * same machine can log in another account. Called from the client thread, the AWT thread and the plugin's executor, so
 * every method is synchronized.
 */
final class AutoLogin
{
	static final long SETTLE_MS = 3_000;
	static final long RETRY_MS = 15_000;
	static final int MAX_TRIES = 5;

	private boolean onLoginScreen;
	private long nextTryAt;
	private int tries;
	private String typedName;
	private boolean manual;

	/** Forgets every try, as a plugin restart should: the plugin object, and so this one, outlives a restart. */
	synchronized void reset()
	{
		onLoginScreen = false;
		tries = 0;
		nextTryAt = 0;
		typedName = null;
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
	 * A login, under {@code name}. The count starts again. Auto login only ever logs in the name it typed (or, before
	 * it has typed one, would type: {@code account}), so a login under another name was a person's, who keeps the client
	 * as if they had typed over it.
	 */
	synchronized void loggedIn(String name, String account)
	{
		if (differs(name, typedName != null ? typedName : account))
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
	 * A key was pressed on the login screen. Auto login never presses keys there, so a person is at this client: they
	 * keep it, even before the first try. True the first time.
	 */
	synchronized boolean keyPressed()
	{
		boolean first = !manual;
		manual = true;
		return first;
	}

	/** Auto login put {@code name} in the login box. From then on another name there was typed by someone. */
	synchronized void typed(String name)
	{
		typedName = name;
	}

	/**
	 * True, and from then on never due again until {@link #reset}, when the login box holds a name other than the one
	 * auto login last put there (on any login screen since, a logout included): someone has typed over it. Before auto
	 * login has typed a name the box can hold any name the client remembered, so it doesn't count. Comparing with the
	 * name typed, not the configured account, lets a change of account take effect.
	 */
	synchronized boolean takenOver(String box)
	{
		if (typedName != null && differs(box, typedName))
		{
			manual = true;
		}
		return manual;
	}

	/** Two names, neither empty, that aren't the same account. */
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
