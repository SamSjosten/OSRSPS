package osrsps.tickrecorder;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Starts a development RuneLite with this plugin loaded. Only point it at our own server: an
 * unreviewed plugin must not be used on Jagex's game worlds.
 */
public class TickRecorderPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TickRecorderPlugin.class);
		RuneLite.main(args);
	}
}
