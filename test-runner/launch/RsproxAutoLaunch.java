import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

/**
 * Starts RSProx and presses its Launch Session button, so the test client comes up with nobody at the screen.
 *
 * <p>RSProx has no flag that launches a client by itself. This runs RSProx's own GUI (net.rsprox.gui.ProxyToolGuiKt,
 * the class its launcher starts) in this JVM, waits for its window, picks the proxy target by name and the RuneLite
 * client type, and clicks Launch Session through Swing, the same call a mouse click makes. Nothing depends on where the
 * window is or whether it has focus.
 *
 * <p>The outcome is also written to .rsprox\logs\autolaunch.txt under user.home (C:\RSPS with RSProx.cmd), one line
 * starting "launched" or "failed", so a script can tell whether the client is on its way.
 *
 * <p>Run as a single-file source program on RSProx's classpath (see RSProx.cmd next to it):
 *
 * <pre>
 * java -cp "C:\RSPS\.rsprox\launcher\repository\*" RsproxAutoLaunch.java [--target OSRSPS] [--timeout 180]
 * </pre>
 *
 * <p>Written against RSProx 1.0.5 (LaunchBar: target dropdown of ProxyTargetConfig, client type dropdown of SessionType,
 * button text "Launch Session"). When a later RSProx changes those, this says what it couldn't find and leaves the
 * RSProx window open, so a person can still click.
 */
public class RsproxAutoLaunch
{
	private static final String GUI_MAIN = "net.rsprox.gui.ProxyToolGuiKt";
	private static final String LAUNCH_TEXT = "Launch Session";
	private static final String CLIENT_TYPE = "RuneLite";

	public static void main(String[] args) throws Exception
	{
		String target = "OSRSPS";
		int timeoutSeconds = 180;
		for (int i = 0; i < args.length; i++)
		{
			switch (args[i])
			{
				case "--target":
					target = args[++i];
					break;
				case "--timeout":
					timeoutSeconds = Integer.parseInt(args[++i]);
					break;
				default:
					throw new IllegalArgumentException("Unknown argument " + args[i]
						+ " (use --target <proxy target name> and --timeout <seconds>)");
			}
		}

		// RSProx's main sets up its proxy service (which can take a while on first start), then builds the window on
		// the Swing thread and returns. Arguments given to it are passed on to RuneLite, so ours are not.
		try
		{
			Class.forName(GUI_MAIN).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
		}
		catch (ReflectiveOperationException e)
		{
			Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
			log("failed", "RSProx did not start: " + cause);
			throw e;
		}

		long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
		String last = "the RSProx window did not open";
		while (System.currentTimeMillis() < deadline)
		{
			Thread.sleep(1000);
			String result = onSwingThread(target);
			if (result == null)
			{
				log("launched", "Launched a RuneLite session on proxy target " + target + ".");
				return;
			}
			last = result;
		}
		log("failed", "Gave up after " + timeoutSeconds + " s: " + last + ". RSProx stays open; click Launch Session by hand.");
	}

	/** Looks for the launch bar and launches; null once launched, else what is still missing. */
	private static String onSwingThread(String target) throws InterruptedException
	{
		String[] result = new String[1];
		try
		{
			SwingUtilities.invokeAndWait(() -> result[0] = tryLaunch(target));
		}
		catch (InvocationTargetException e)
		{
			return "error " + e.getCause();
		}
		return result[0];
	}

	private static String tryLaunch(String target)
	{
		List<Component> all = new ArrayList<>();
		for (Window window : Window.getWindows())
		{
			if (window.isShowing())
			{
				collect(window, all);
			}
		}

		AbstractButton launch = null;
		JComboBox<?> targets = null;
		JComboBox<?> clientTypes = null;
		for (Component c : all)
		{
			if (c instanceof AbstractButton && LAUNCH_TEXT.equals(((AbstractButton) c).getText()))
			{
				launch = (AbstractButton) c;
			}
			else if (c instanceof JComboBox)
			{
				JComboBox<?> box = (JComboBox<?>) c;
				if (indexOf(box, item -> item.getClass().getSimpleName().equals("ProxyTargetConfig")) >= 0)
				{
					targets = box;
				}
				else if (indexOf(box, item -> item instanceof Enum && ((Enum<?>) item).name().equals(CLIENT_TYPE)) >= 0)
				{
					clientTypes = box;
				}
			}
		}
		if (launch == null || targets == null || clientTypes == null)
		{
			return "no " + (launch == null ? "Launch Session button" : targets == null ? "proxy target list" : "client type list")
				+ " in the RSProx window";
		}

		// Selecting through the dropdowns fires their listeners, which also save the choice in RSProx's settings.
		int clientIndex = indexOf(clientTypes, item -> item instanceof Enum && ((Enum<?>) item).name().equals(CLIENT_TYPE));
		if (clientTypes.getSelectedIndex() != clientIndex)
		{
			clientTypes.setSelectedIndex(clientIndex);
		}
		int targetIndex = indexOf(targets, item -> target.equals(nameOf(item)));
		if (targetIndex < 0)
		{
			return "no proxy target named " + target + " (check proxy-targets.yaml)";
		}
		if (targets.getSelectedIndex() != targetIndex)
		{
			targets.setSelectedIndex(targetIndex);
		}
		if (!launch.isEnabled())
		{
			return "the Launch Session button is disabled";
		}
		launch.doClick();
		return null;
	}

	private static void collect(Component c, List<Component> into)
	{
		into.add(c);
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				collect(child, into);
			}
		}
	}

	private interface ItemTest
	{
		boolean matches(Object item);
	}

	private static int indexOf(JComboBox<?> box, ItemTest test)
	{
		for (int i = 0; i < box.getModel().getSize(); i++)
		{
			Object item = box.getModel().getElementAt(i);
			if (item != null && test.matches(item))
			{
				return i;
			}
		}
		return -1;
	}

	/** ProxyTargetConfig.name, read by reflection so this compiles without RSProx's classes. */
	private static String nameOf(Object item)
	{
		try
		{
			return String.valueOf(item.getClass().getMethod("getName").invoke(item));
		}
		catch (ReflectiveOperationException e)
		{
			return null;
		}
	}

	private static void log(String outcome, String message)
	{
		System.out.println("[RsproxAutoLaunch] " + message);
		Path status = Paths.get(System.getProperty("user.home"), ".rsprox", "logs", "autolaunch.txt");
		try
		{
			Files.createDirectories(status.getParent());
			Files.write(status, (outcome + " " + Instant.now() + " " + message + System.lineSeparator())
				.getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			System.out.println("[RsproxAutoLaunch] Could not write " + status + ": " + e);
		}
	}
}
