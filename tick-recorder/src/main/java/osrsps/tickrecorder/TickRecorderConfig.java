package osrsps.tickrecorder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(TickRecorderConfig.GROUP)
public interface TickRecorderConfig extends Config
{
	String GROUP = "tickrecorder";
	String RECORDING = "recording";

	@ConfigItem(
		keyName = RECORDING,
		name = "Recording",
		description = "Write what the client sees each tick to the tick-recorder folder in RuneLite's settings folder. A new file starts each time this is turned on.",
		position = 0
	)
	default boolean recording()
	{
		return false;
	}

	@ConfigItem(
		keyName = "toggleHotkey",
		name = "Toggle hotkey",
		description = "Starts or stops recording",
		position = 1
	)
	default Keybind toggleHotkey()
	{
		return Keybind.NOT_SET;
	}

	@Range(min = 1, max = 32)
	@ConfigItem(
		keyName = "radius",
		name = "Radius (tiles)",
		description = "Only NPCs, objects and projectiles this close to you are recorded",
		position = 2
	)
	default int radius()
	{
		return 16;
	}

	@ConfigItem(
		keyName = "otherPlayers",
		name = "Other players",
		description = "Also record other players' positions, animations and clicks on them, by index. Names in text are replaced with {player}.",
		position = 3
	)
	default boolean otherPlayers()
	{
		return false;
	}

	@ConfigItem(
		keyName = "objects",
		name = "Objects and ground items",
		description = "Record objects and ground items near you",
		position = 4
	)
	default boolean objects()
	{
		return true;
	}

	@ConfigItem(
		keyName = "varbits",
		name = "Varps and varbits",
		description = "Record your varp and varbit changes",
		position = 5
	)
	default boolean varbits()
	{
		return true;
	}

	@ConfigItem(
		keyName = "menuClicks",
		name = "Your clicks",
		description = "Record the menu options you click",
		position = 6
	)
	default boolean menuClicks()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sounds",
		name = "Sounds",
		description = "Record sound effects",
		position = 7
	)
	default boolean sounds()
	{
		return true;
	}
}
