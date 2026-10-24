package osrsps.testrunner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(TestRunnerConfig.GROUP)
public interface TestRunnerConfig extends Config
{
	String GROUP = "osrspstestrunner";

	@ConfigItem(
		keyName = "watchFolder",
		name = "Run dropped scripts",
		description = "Run a script as soon as it is added to or changed in the tests folder",
		position = 0
	)
	default boolean watchFolder()
	{
		return true;
	}

	@ConfigItem(
		keyName = "runOnLogin",
		name = "Run login scripts",
		description = "After each login, run every script in the tests folder that has \"runOnLogin\": true",
		position = 1
	)
	default boolean runOnLogin()
	{
		return true;
	}

	@ConfigItem(
		keyName = "accounts",
		name = "Test accounts",
		description = "Comma separated. A script only runs while one of these is logged in, and only on the account it names. Never add a real Jagex account.",
		position = 2
	)
	default String accounts()
	{
		return "test, test2";
	}

	@ConfigItem(
		keyName = "screenshotOnFailure",
		name = "Screenshot on failure",
		description = "Save the client's picture next to the result when a script fails",
		position = 3
	)
	default boolean screenshotOnFailure()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoLoginAccount",
		name = "Auto login account",
		description = "Logs this account in from the login screen without anyone typing. Must be one of the Test accounts; empty turns auto login off.",
		position = 4
	)
	default String autoLoginAccount()
	{
		return "test";
	}

	@ConfigItem(
		keyName = "autoLoginPassword",
		name = "Auto login password",
		description = "Our server takes any password for a test account; this is a throwaway, never a real one",
		secret = true,
		position = 5
	)
	default String autoLoginPassword()
	{
		return "test";
	}
}
