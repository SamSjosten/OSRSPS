package osrsps.tfahelper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(TfaHelperConfig.GROUP)
public interface TfaHelperConfig extends Config
{
	String GROUP = "tfahelper";

	@ConfigItem(
		keyName = "showTimers",
		name = "Show timers",
		description = "While in The Fractured Archive, show the area you are in, your time in it and the run's time",
		position = 0
	)
	default boolean showTimers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSplits",
		name = "Show splits",
		description = "Also list your time in each area of the run so far",
		position = 1
	)
	default boolean showSplits()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatSummary",
		name = "Splits in chat",
		description = "When a run ends, write its time and splits in your chatbox (only you see them)",
		position = 2
	)
	default boolean chatSummary()
	{
		return true;
	}
}
