package osrsps.testrunner;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs test scripts from {@code <RuneLite folder>\tests} against our own server and writes a result per script to
 * {@code <RuneLite folder>\test-results}. A script runs when it is dropped into (or changed in) the tests folder, or
 * after each login when it says "runOnLogin". See README.md.
 *
 * It clicks for you, so it is never for the Plugin Hub or Jagex's worlds: it is sideloaded into our test client only,
 * and a script only runs on an account in the Test accounts setting.
 */
@PluginDescriptor(
	name = "OSRSPS Test Runner",
	description = "Runs scripted tests against the OSRSPS test server and writes pass/fail results",
	tags = {"test", "osrsps", "automation"},
	enabledByDefault = true
)
public class TestRunnerPlugin extends Plugin
{
	/** Written into every result, bumped with every change to how scripts run. */
	static final String RUNNER_VERSION = "0.7.1";
	private static final Logger log = LoggerFactory.getLogger(TestRunnerPlugin.class);
	/** How long a failure waits for a frame to screenshot before writing its result without one. */
	private static final long SCREENSHOT_WAIT_MS = 3_000;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private TestRunnerConfig config;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private DrawManager drawManager;

	@Inject
	private Gson gson;

	@Inject
	private KeyManager keyManager;

	private final AutoLogin autoLogin = new AutoLogin();
	/** A key on the login screen means a person is at this client, so auto login leaves it to them. */
	private final KeyListener personAtLogin = new KeyListener()
	{
		@Override
		public boolean isEnabledOnLoginScreen()
		{
			return true;
		}

		@Override
		public void keyPressed(KeyEvent e)
		{
			if (client.getGameState() == GameState.LOGIN_SCREEN && autoLogin.keyPressed())
			{
				log.info("Test runner: a key was pressed on the login screen; auto login is off until the plugin restarts");
			}
		}

		@Override
		public void keyTyped(KeyEvent e)
		{
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
		}
	};
	private ScriptFolder folder;
	private ResultWriter writer;
	private Runner runner;
	private ScheduledFuture<?> scanTask;
	/** Scripts read and checked on the executor, waiting for the client thread to run them in order. */
	private final Queue<Pending> pending = new ConcurrentLinkedQueue<>();
	/**
	 * True while a failed run waits for its screenshot. The next script waits too, or a late frame (a minimised
	 * client restored) could show the next script's state as this one's failure. Holds that run's own token, and only
	 * the holder of the current token clears it, so a stale capture (an older run, or one from before a plugin
	 * restart) can never release a newer one.
	 */
	private final AtomicReference<AtomicBoolean> capturing = new AtomicReference<>();
	/** True between a login and the next login screen: LOGGED_IN also follows every region load. */
	private boolean loggedIn;

	private static final class Pending
	{
		final String id;
		final String trigger;
		final Script script;
		/** Why the file could not be run, when {@link #script} is null. */
		final String error;

		Pending(String id, String trigger, Script script, String error)
		{
			this.id = id;
			this.trigger = trigger;
			this.script = script;
			this.error = error;
		}
	}

	@Provides
	TestRunnerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TestRunnerConfig.class);
	}

	@Override
	protected void startUp()
	{
		capturing.set(null);
		autoLogin.reset();
		Path base = RuneLite.RUNELITE_DIR.toPath();
		folder = new ScriptFolder(base.resolve("tests"));
		writer = new ResultWriter(base.resolve("test-results"), gson.newBuilder().setPrettyPrinting().disableHtmlEscaping().create(), RUNNER_VERSION);
		runner = new Runner(new ClientGame(client), System::currentTimeMillis, this::accounts);
		loggedIn = client.getGameState() == GameState.LOGGED_IN;
		if (client.getGameState() == GameState.LOGIN_SCREEN)
		{
			autoLogin.loginScreen(System.currentTimeMillis());
		}
		keyManager.registerKeyListener(personAtLogin);
		scanTask = executor.scheduleWithFixedDelay(this::scan, 0, 1, TimeUnit.SECONDS);
		log.info("Test runner: watching {}", folder.dir());
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(personAtLogin);
		if (scanTask != null)
		{
			scanTask.cancel(false);
			scanTask = null;
		}
		pending.clear();
		Result r = runner.abort("the plugin was turned off");
		if (r != null)
		{
			finished(r);
		}
	}

	/**
	 * Client thread: fills in the auto login account and starts the login, as pressing Login would. Only for an account
	 * on the test account list. Starting it by setting the game state to LOGGING_IN is how RuneLite's own developer
	 * tools log in; not yet tried against our server.
	 */
	private void logIn()
	{
		long now = System.currentTimeMillis();
		if (client.getGameState() != GameState.LOGIN_SCREEN || !autoLogin.due(now))
		{
			return;
		}
		String account = config.autoLoginAccount().trim();
		if (account.isEmpty())
		{
			return;
		}
		if (autoLogin.takenOver(client.getUsername()))
		{
			log.info("Test runner: someone typed {} in the login box; auto login is off until the plugin restarts", client.getUsername());
			return;
		}
		boolean allowed = false;
		for (String a : accounts())
		{
			allowed |= Runner.normalise(a).equals(Runner.normalise(account));
		}
		autoLogin.tried(now);
		if (!allowed)
		{
			log.warn("Test runner: auto login account \"{}\" is not in the test accounts {}", account, accounts());
			return;
		}
		log.info("Test runner: logging in as {}", account);
		client.setUsername(account);
		client.setPassword(config.autoLoginPassword());
		autoLogin.typed(account);
		client.setGameState(GameState.LOGGING_IN);
		if (autoLogin.gaveUp())
		{
			log.warn("Test runner: auto login gave up after {} tries; log in by hand or restart the plugin", AutoLogin.MAX_TRIES);
		}
	}

	private List<String> accounts()
	{
		List<String> out = new ArrayList<>();
		for (String a : config.accounts().split(","))
		{
			if (!a.trim().isEmpty())
			{
				out.add(a.trim());
			}
		}
		return out;
	}

	/** On the executor: queues scripts that are new or changed in the tests folder. */
	private void scan()
	{
		try
		{
			Files.createDirectories(folder.dir());
			List<Path> due = folder.scan(System.currentTimeMillis());
			// ClientTick only comes while logged in; this keeps a script that waits for a login moving.
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				clientThread.invokeLater(this::pump);
			}
			if (autoLogin.due(System.currentTimeMillis()))
			{
				clientThread.invokeLater(this::logIn);
			}
			if (config.watchFolder())
			{
				for (Path p : due)
				{
					queue(p, "file", false);
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Test runner: cannot scan {}", folder.dir(), e);
		}
	}

	/** On the executor: queues the scripts marked runOnLogin. */
	private void queueLoginScripts()
	{
		try
		{
			for (Path p : folder.scripts())
			{
				queue(p, "login", true);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Test runner: cannot read {}", folder.dir(), e);
		}
	}

	private void queue(Path file, String trigger, boolean onlyRunOnLogin)
	{
		String name = file.getFileName().toString();
		String id = Script.idFor(name);
		try
		{
			Script s = Script.parse(name, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
			if (!onlyRunOnLogin || s.runOnLogin)
			{
				pending.add(new Pending(id, trigger, s, null));
			}
		}
		catch (Script.Invalid e)
		{
			// A broken file still gets a result, so whoever dropped it learns why nothing ran.
			if (!onlyRunOnLogin)
			{
				pending.add(new Pending(id, trigger, null, e.getMessage()));
			}
		}
		catch (IOException e)
		{
			pending.add(new Pending(id, trigger, null, "cannot read " + file + ": " + e));
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN)
		{
			loggedIn = false;
			autoLogin.loginScreen(System.currentTimeMillis());
		}
		else
		{
			autoLogin.leftLoginScreen();
		}
		if (state == GameState.LOGGED_IN && !loggedIn)
		{
			loggedIn = true;
			autoLogin.loggedIn(client.getUsername(), config.autoLoginAccount());
			if (config.runOnLogin())
			{
				executor.execute(this::queueLoginScripts);
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		runner.gameTick();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		// Console lines are this plugin's own (and other plugins'); the game's messages are what scripts assert on.
		if (event.getType() != ChatMessageType.CONSOLE)
		{
			runner.message(event.getMessage());
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor actor = event.getActor();
		runner.hitsplat(actorIndex(actor), npcName(actor), event.getHitsplat().getHitsplatType(), event.getHitsplat().getAmount());
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		runner.animation(actorIndex(actor), npcName(actor), actor.getAnimation());
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		Actor actor = event.getActor();
		// The event doesn't say which slot changed: every spotanim the actor has now is reported, old ones again too.
		for (ActorSpotAnim spot : actor.getSpotAnims())
		{
			runner.spotanim(actorIndex(actor), npcName(actor), spot.getId());
		}
	}

	/** An NPC's index; {@link Run#SELF} for the local player; -2 for another player, which no step asks about. */
	private int actorIndex(Actor actor)
	{
		if (actor instanceof NPC)
		{
			return ((NPC) actor).getIndex();
		}
		return actor == client.getLocalPlayer() ? Run.SELF : -2;
	}

	/** The actor's name when it is an NPC, as scripts name NPCs; null for a player. */
	private static String npcName(Actor actor)
	{
		return actor instanceof NPC ? Dialogue.clean(actor.getName()) : null;
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		pump();
	}

	/** Starts the next queued script when none is running, and advances the running one. Client thread only. */
	private void pump()
	{
		try
		{
			if (!runner.busy())
			{
				if (capturing.get() != null)
				{
					return;
				}
				Pending next = pending.poll();
				if (next == null)
				{
					return;
				}
				if (next.script == null)
				{
					finished(Result.error(next.id, next.trigger, next.error, System.currentTimeMillis()));
					return;
				}
				say("running " + next.script.name);
				runner.start(next.script, next.trigger);
			}
			Result r = runner.poll();
			if (r != null)
			{
				finished(r);
			}
		}
		catch (RuntimeException e)
		{
			log.warn("Test runner: script {} threw", runner.running(), e);
			Result r = runner.abort("the runner threw " + e);
			if (r != null)
			{
				finished(r);
			}
		}
	}

	private void finished(Result r)
	{
		say(r.summary());
		log.info("Test runner: {}", r.summary());
		if (!r.passed() && !Result.ERROR.equals(r.result) && config.screenshotOnFailure())
		{
			AtomicBoolean written = new AtomicBoolean();
			capturing.set(written);
			drawManager.requestNextFrameListener(image ->
			{
				// Only the side that claims the write releases the queue: a frame arriving after the fallback must not
				// clear the guard a later failed run has set.
				if (written.compareAndSet(false, true))
				{
					BufferedImage shot = ImageUtil.bufferedImageFromImage(image);
					capturing.compareAndSet(written, null);
					executor.execute(() -> write(r, shot));
				}
			});
			// A minimised client draws no frames: write the result without a screenshot rather than not at all.
			executor.schedule(() ->
			{
				if (written.compareAndSet(false, true))
				{
					capturing.compareAndSet(written, null);
					write(r, null);
				}
			}, SCREENSHOT_WAIT_MS, TimeUnit.MILLISECONDS);
			return;
		}
		executor.execute(() -> write(r, null));
	}

	private void write(Result r, BufferedImage screenshot)
	{
		try
		{
			Path out = writer.write(r, screenshot);
			log.info("Test runner: wrote {}", out);
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Test runner: cannot write the result of {}", r.id, e);
		}
	}

	private void say(String message)
	{
		client.addChatMessage(ChatMessageType.CONSOLE, "", "[Test runner] " + message, null);
	}
}
