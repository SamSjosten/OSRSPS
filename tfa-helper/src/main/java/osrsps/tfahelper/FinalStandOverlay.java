package osrsps.tfahelper;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** Ocellus's final stand: the loop tick, the next event and its countdown, and blasts so far. Our server only. */
class FinalStandOverlay extends OverlayPanel
{
	private final TfaHelperPlugin plugin;
	private final TfaHelperConfig config;
	private final Client client;

	@Inject
	FinalStandOverlay(TfaHelperPlugin plugin, TfaHelperConfig config, Client client)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		setPosition(OverlayPosition.TOP_RIGHT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		int tick = plugin.tick();
		FinalStand stand = plugin.finalStand();
		if (!config.ocellusFinalStand() || !OwnServer.connected(client) || !stand.active(tick))
		{
			return null;
		}
		int at = stand.loopTick(tick);
		FinalStand.Event next = FinalStand.next(at);
		panelComponent.getChildren().add(TitleComponent.builder().text("Ocellus final stand").build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Loop tick")
			.right(at + " / " + FinalStand.LOOP)
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left(next.label)
			.right("in " + FinalStand.ticksUntil(at, next))
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Blasts this loop")
			.right(Integer.toString(FinalStand.blastsSoFar(at)))
			.build());
		return super.render(graphics);
	}
}
