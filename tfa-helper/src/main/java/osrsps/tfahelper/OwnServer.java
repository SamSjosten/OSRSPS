package osrsps.tfahelper;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;

/**
 * Whether the client is connected to our own server rather than one of Jagex's worlds. Every feature that reads a boss
 * (its timers, counts, prayers or tiles) draws only while this is true: those features are against Jagex's third-party
 * client guidelines on the live game, and this plugin is built for our server alone. The plain run timers don't ask.
 *
 * <p>It goes by the host RuneLite reports the client connected to ({@link Client#getWorldHost()}), and anything it
 * doesn't recognise counts as not ours. Our hosts: the launcher's (Odin's Gate) connects to the Proxmox host's
 * forwarded game port, 192.168.0.210; the VM itself is 10.99.0.10; the RSProx test client connects to RSProx on the same
 * machine, which gives each of its targets a loopback address of its own: 127.1.45.3 for ours on VM 215 (seen 17 November
 * 2026), so any IPv4 loopback address counts, written as an address, never a name to look up.
 */
final class OwnServer
{
	static final Set<String> HOSTS = Set.of("192.168.0.210", "10.99.0.10", "localhost", "::1");
	private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

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
		return HOSTS.contains(bare) || isIpv4Loopback(bare);
	}

	/** Whether {@code host} is an IPv4 address in 127.0.0.0/8, each part 0 to 255. */
	static boolean isIpv4Loopback(String host)
	{
		Matcher m = IPV4.matcher(host);
		if (!m.matches() || !m.group(1).equals("127"))
		{
			return false;
		}
		for (int i = 2; i <= 4; i++)
		{
			if (Integer.parseInt(m.group(i)) > 255)
			{
				return false;
			}
		}
		return true;
	}
}
