package osrsps.testrunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutoLoginTest
{
	@Test
	public void waitsForTheScreenToSettleThenRetriesAndGivesUp()
	{
		AutoLogin a = new AutoLogin();
		assertFalse("not on the login screen", a.due(0));
		a.loginScreen(1_000);
		assertFalse(a.due(1_000 + AutoLogin.SETTLE_MS - 1));
		long now = 1_000 + AutoLogin.SETTLE_MS;
		for (int i = 0; i < AutoLogin.MAX_TRIES; i++)
		{
			assertTrue("try " + (i + 1), a.due(now));
			a.tried(now);
			// A failed login goes back to the login screen; the retry waits its turn.
			a.leftLoginScreen();
			a.loginScreen(now + 1_000);
			assertFalse(a.due(now + AutoLogin.RETRY_MS - 1));
			now += AutoLogin.RETRY_MS;
		}
		assertTrue(a.gaveUp());
		assertFalse(a.due(now + 60_000));
	}

	@Test
	public void aLoginStartsTheCountAgain()
	{
		AutoLogin a = new AutoLogin();
		a.loginScreen(0);
		for (int i = 0; i < AutoLogin.MAX_TRIES; i++)
		{
			a.tried(i * AutoLogin.RETRY_MS);
		}
		assertTrue(a.gaveUp());
		a.loggedIn("test", "test");
		assertFalse(a.gaveUp());
		a.loginScreen(1_000_000);
		assertTrue(a.due(1_000_000 + AutoLogin.SETTLE_MS));
	}

	@Test
	public void anotherNameTypedOverTheAccountStopsItUntilAReset()
	{
		AutoLogin a = new AutoLogin();
		a.loginScreen(0);
		assertFalse("a remembered name before the first try doesn't count", a.takenOver("test2"));
		a.tried(AutoLogin.SETTLE_MS);
		a.typed("test");
		assertFalse("the account it typed", a.takenOver("Test"));
		assertFalse("a box cleared to type in", a.takenOver(""));
		assertTrue(a.takenOver("test2"));
		assertFalse(a.due(1_000_000));
		a.loggedIn("test", "test");
		a.loginScreen(2_000_000);
		assertFalse("still theirs after their login and logout", a.due(3_000_000));
		a.reset();
		a.loginScreen(4_000_000);
		assertTrue(a.due(4_000_000 + AutoLogin.SETTLE_MS));
	}

	@Test
	public void anotherNameTypedAfterAnAutoLoginAndLogoutCounts()
	{
		AutoLogin a = new AutoLogin();
		a.loginScreen(0);
		a.tried(AutoLogin.SETTLE_MS);
		a.typed("test");
		a.loggedIn("test", "test");
		a.loginScreen(1_000_000);
		assertTrue(a.takenOver("test2"));
		assertFalse(a.due(1_000_000 + AutoLogin.SETTLE_MS));
	}

	@Test
	public void aChangeOfAccountIsNotATakeOver()
	{
		AutoLogin a = new AutoLogin();
		a.loginScreen(0);
		a.tried(AutoLogin.SETTLE_MS);
		a.typed("test");
		// The account setting is now test2; the box still holds the test the plugin typed.
		assertFalse(a.takenOver("test"));
		assertTrue(a.due(AutoLogin.SETTLE_MS + AutoLogin.RETRY_MS));
		a.tried(AutoLogin.SETTLE_MS + AutoLogin.RETRY_MS);
		a.typed("test2");
		a.loggedIn("test2", "test2");
		a.loginScreen(1_000_000);
		assertFalse(a.takenOver("test2"));
		assertTrue(a.due(1_000_000 + AutoLogin.SETTLE_MS));
	}

	@Test
	public void aLoginUnderAnotherNameKeepsTheClientOffAutoLogin()
	{
		AutoLogin a = new AutoLogin();
		a.loginScreen(0);
		a.tried(AutoLogin.SETTLE_MS);
		// Typed and logged in before the next try came round, so no try saw the name.
		a.loggedIn("test2", "test");
		a.loginScreen(1_000_000);
		assertFalse(a.due(2_000_000));
		a.reset();
		a.loginScreen(3_000_000);
		assertTrue(a.due(3_000_000 + AutoLogin.SETTLE_MS));
	}

	@Test
	public void aResetStartsTheCountAgain()
	{
		AutoLogin a = new AutoLogin();
		a.loginScreen(0);
		for (int i = 0; i < AutoLogin.MAX_TRIES; i++)
		{
			a.tried(i * AutoLogin.RETRY_MS);
		}
		assertTrue(a.gaveUp());
		a.reset();
		assertFalse(a.gaveUp());
		assertFalse("off the login screen until told otherwise", a.due(1_000_000));
		a.loginScreen(1_000_000);
		assertTrue(a.due(1_000_000 + AutoLogin.SETTLE_MS));
	}
}
