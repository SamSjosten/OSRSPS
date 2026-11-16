package osrsps.tfahelper;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** The run's timers: the area you are in, your time there, the run's time and, if wanted, each area's split. */
class TfaHelperOverlay extends OverlayPanel
{
	private final TfaHelperPlugin plugin;
	private final TfaHelperConfig config;

	@Inject
	TfaHelperOverlay(TfaHelperPlugin plugin, TfaHelperConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		RaidTracker tracker = plugin.tracker();
		if (!config.showTimers() || !tracker.inRun())
		{
			return null;
		}
		int tick = plugin.tick();
		TfaArea current = tracker.current();
		panelComponent.getChildren().add(TitleComponent.builder().text("Fractured Archive").build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left(current == null ? "-" : current.label())
			.right(TickTime.format(tracker.areaTicks(tick)))
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Run")
			.right(TickTime.format(tracker.runTicks(tick)))
			.build());
		if (config.showSplits())
		{
			for (TfaArea area : tracker.order())
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left("  " + area.label())
					.right(TickTime.format(tracker.ticksIn(area, tick)))
					.build());
			}
		}
		return super.render(graphics);
	}
}
