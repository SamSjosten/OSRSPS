package osrsps.tfahelper;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Room and run timers for The Fractured Archive. It reads only where you are standing, never the bosses or anything
 * about them, and sends nothing: see the rules doc this thread keeps (Jagex's third-party client guidelines) before
 * adding a feature.
 */
@PluginDescriptor(
	name = "TFA Helper",
	description = "Room and run timers for The Fractured Archive",
	tags = {"tfa", "fractured", "archive", "raid", "timer", "splits"}
)
public class TfaHelperPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private TfaHelperConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TfaHelperOverlay overlay;

	@Inject
	private ChatMessageManager chatMessageManager;

	private final RaidTracker tracker = new RaidTracker();
	/** Game ticks seen since the plugin started; the tracker's clock. */
	private int tick;

	@Provides
	TfaHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TfaHelperConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		tracker.reset();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tick++;
		RaidTracker.Run ended = tracker.tick(tick, areaNow());
		if (ended != null && config.chatSummary())
		{
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(summary(ended))
				.build());
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Back at the login screen the run is over and its end isn't worth a line in a chatbox you can't see.
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			tracker.reset();
		}
	}

	/** The raid area you are standing in, by your tile in the instance's template; null outside the raid. */
	private TfaArea areaNow()
	{
		Player player = client.getLocalPlayer();
		if (player == null || !client.getTopLevelWorldView().isInstance())
		{
			return null;
		}
		LocalPoint local = player.getLocalLocation();
		if (local == null)
		{
			return null;
		}
		WorldPoint template = WorldPoint.fromLocalInstance(client, local);
		return template == null ? null : TfaArea.at(template.getX(), template.getY());
	}

	static String summary(RaidTracker.Run run)
	{
		StringBuilder line = new StringBuilder("Fractured Archive run ").append(TickTime.format(run.totalTicks));
		String separator = ": ";
		for (TfaArea area : run.order)
		{
			line.append(separator).append(area.label()).append(' ')
				.append(TickTime.format(run.ticksByArea.getOrDefault(area, 0)));
			separator = ", ";
		}
		return line.toString();
	}

	RaidTracker tracker()
	{
		return tracker;
	}

	int tick()
	{
		return tick;
	}
}
