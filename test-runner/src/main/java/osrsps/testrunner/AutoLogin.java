package osrsps.testrunner;

/**
 * When to log the test account in from the login screen: a few seconds after the screen shows (so the client has
 * finished loading), then again every {@link #RETRY_MS} while it stays there, up to {@link #MAX_TRIES} tries in a row.
 * A login resets the count. Called from the client thread and the plugin's executor, so every method is synchronized.
 */
final class AutoLogin
{
	static final long SETTLE_MS = 3_000;
	static final long RETRY_MS = 15_000;
	static final int MAX_TRIES = 5;

	private boolean onLoginScreen;
	private long nextTryAt;
	private int tries;

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

	synchronized void loggedIn()
	{
		onLoginScreen = false;
		tries = 0;
		nextTryAt = 0;
	}

	synchronized boolean due(long nowMs)
	{
		return onLoginScreen && tries < MAX_TRIES && nowMs >= nextTryAt;
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
