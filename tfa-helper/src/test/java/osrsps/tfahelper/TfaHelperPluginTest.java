package osrsps.tfahelper;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Starts a development RuneLite with this plugin loaded: the developer-mode route RuneLite supports for a plugin of your own. */
public class TfaHelperPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TfaHelperPlugin.class);
		RuneLite.main(args);
	}
}
