package osrsps.tfahelper;

import java.util.Locale;
import java.util.Set;
import net.runelite.api.Client;

/**
 * Whether the client is connected to our own server rather than one of Jagex's worlds. Every feature that reads a boss
 * (its timers, counts, prayers or tiles) draws only while this is true: those features are against Jagex's third-party
 * client guidelines on the live game, and this plugin is built for our server alone. The plain run timers don't ask.
 *
 * <p>It goes by the host RuneLite reports the client connected to ({@link Client#getWorldHost()}), and anything it
 * doesn't recognise counts as not ours. Our hosts: the launcher's (Odin's Gate) connects to the Proxmox host's
 * forwarded game port, 192.168.0.210; the VM itself is 10.99.0.10; the RSProx test client connects to RSProx on the same
 * machine (loopback). Guessed: the exact string each client reports, until checked on a test VM.
 */
final class OwnServer
{
	static final Set<String> HOSTS = Set.of("192.168.0.210", "10.99.0.10", "127.0.0.1", "localhost", "::1");

	private OwnServer()
	{
	}

	static boolean connected(Client client)
	{
		return isOurs(client.getWorldHost());
	}

	static boolean isOurs(String host)
	{
		if (host == null)
		{
			return false;
		}
		String bare = host.trim().toLowerCase(Locale.ROOT);
		// A host may come with its port ("192.168.0.210:43594"); an IPv6 one in brackets ("[::1]:43594").
		if (bare.startsWith("["))
		{
			int end = bare.indexOf(']');
			bare = end < 0 ? "" : bare.substring(1, end);
		}
		else if (bare.indexOf(':') == bare.lastIndexOf(':') && bare.indexOf(':') >= 0)
		{
			bare = bare.substring(0, bare.indexOf(':'));
		}
		return HOSTS.contains(bare);
	}
}
