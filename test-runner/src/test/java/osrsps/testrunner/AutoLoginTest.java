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
		a.loggedIn();
		assertFalse(a.gaveUp());
		a.loginScreen(1_000_000);
		assertTrue(a.due(1_000_000 + AutoLogin.SETTLE_MS));
	}

	@Test
	public void anotherNameTypedOverTheAccountStopsItUntilAReset()
	{
		AutoLogin a = new AutoLogin();
		a.loginScreen(0);
		assertFalse("a remembered name before the first try doesn't count", a.takenOver("test2", "test"));
		a.tried(AutoLogin.SETTLE_MS);
		assertFalse("the account it typed", a.takenOver("Test", "test"));
		assertFalse("a box cleared to type in", a.takenOver("", "test"));
		assertTrue(a.takenOver("test2", "test"));
		assertFalse(a.due(1_000_000));
		a.loggedIn();
		a.loginScreen(2_000_000);
		assertFalse("still theirs after their login and logout", a.due(3_000_000));
		a.reset();
		a.loginScreen(4_000_000);
		assertTrue(a.due(4_000_000 + AutoLogin.SETTLE_MS));
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
