package osrsps.tickrecorder;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.io.File;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.IterableHashTable;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldType;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.AreaSoundEffectPlayed;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.SoundEffectPlayed;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.WorldChanged;
import net.runelite.client.RuneLite;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.HotkeyListener;

/**
 * Records what the client already sees to a JSON lines file: one "tick" line per game tick with the
 * state of everything nearby, plus one line per event (animation, hitsplat, projectile, ...) as it
 * happens. Events carry the number of the tick line that follows them. See README.md for the format.
 */
@PluginDescriptor(
	name = "Tick Recorder",
	description = "Records what the client sees each game tick to a local file",
	tags = {"record", "log", "tick", "animation", "projectile", "hitsplat", "npc"}
)
public class TickRecorderPlugin extends Plugin
{
	static final int FORMAT_VERSION = 2;
	static final File RECORDINGS_DIR = new File(RuneLite.RUNELITE_DIR, "tick-recorder");

	private static final DateTimeFormatter FILE_TIME =
		DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss.SSS'Z'").withZone(ZoneOffset.UTC);

	/** Inventory and worn equipment, written at the start of every recording. */
	private static final int[] BASELINE_CONTAINERS = {93, 94};
	/** Containers with this bit belong to the other side of a trade or similar, never to you. */
	private static final int OTHER_PARTY_CONTAINER = 0x8000;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private TickRecorderConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private Gson gson;

	private Gson json;
	private volatile RecordingWriter writer;
	private volatile boolean recording;
	/** Tick lines written so far; events carry tick + 1, the number of the tick line that follows them. */
	private int tick;
	/** True from LOADING until the first tick after it: spawns in that window are the scene being built. */
	private boolean sceneLoading;
	private final Set<Projectile> seenProjectiles = Collections.newSetFromMap(new WeakHashMap<>());
	private Set<Integer> npcsInRange = new HashSet<>();

	private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.toggleHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			configManager.setConfiguration(TickRecorderConfig.GROUP, TickRecorderConfig.RECORDING, !config.recording());
		}
	};

	@Provides
	TickRecorderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TickRecorderConfig.class);
	}

	@Override
	protected void startUp()
	{
		json = gson.newBuilder().disableHtmlEscaping().create();
		writer = new RecordingWriter();
		keyManager.registerKeyListener(hotkeyListener);
		if (config.recording())
		{
			clientThread.invokeLater(this::startRecording);
		}
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(hotkeyListener);
		RecordingWriter w = writer;
		if (recording)
		{
			emit(stopLine("pluginStopped"));
			recording = false;
		}
		writer = null;
		if (w != null)
		{
			w.shutdown();
		}
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		RecordingWriter w = writer;
		if (recording && w != null)
		{
			emit(stopLine("clientExit"));
			recording = false;
			event.waitFor(w.close());
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!TickRecorderConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if (!TickRecorderConfig.RECORDING.equals(event.getKey()))
		{
			if (recording)
			{
				JsonObject line = line("config");
				line.addProperty("key", event.getKey());
				line.addProperty("value", event.getNewValue());
				emit(line);
			}
			return;
		}
		clientThread.invokeLater(() ->
		{
			if (config.recording() && !recording)
			{
				startRecording();
			}
			else if (!config.recording() && recording)
			{
				stopRecording();
			}
		});
	}

	private void startRecording()
	{
		RecordingWriter w = writer;
		if (w == null)
		{
			return;
		}
		String name = FILE_TIME.format(Instant.now());
		// Say so before recording starts, so our own chat line is not recorded as a game message.
		chat("Tick Recorder: recording to tick-recorder/" + name + ".jsonl");
		w.open(RECORDINGS_DIR.toPath(), name);
		tick = 0;
		seenProjectiles.clear();
		npcsInRange = new HashSet<>();
		recording = true;

		JsonObject header = line("start");
		header.addProperty("format", FORMAT_VERSION);
		header.addProperty("runelite", RuneLiteProperties.getVersion());
		header.addProperty("revision", client.getRevision());
		header.addProperty("time", Instant.now().toString());
		header.add("settings", settings());
		addWorld(header);
		emit(header);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			emitBaseline();
		}
	}

	private void stopRecording()
	{
		emit(stopLine("stopped"));
		recording = false;
		writer.close();
		chat("Tick Recorder: stopped after " + tick + " ticks");
	}

	private JsonObject stopLine(String reason)
	{
		JsonObject stop = line("stop");
		stop.addProperty("reason", reason);
		stop.addProperty("time", Instant.now().toString());
		return stop;
	}

	private JsonObject settings()
	{
		JsonObject settings = new JsonObject();
		settings.addProperty("radius", config.radius());
		settings.addProperty("otherPlayers", config.otherPlayers());
		settings.addProperty("objects", config.objects());
		settings.addProperty("varbits", config.varbits());
		settings.addProperty("menuClicks", config.menuClicks());
		settings.addProperty("sounds", config.sounds());
		return settings;
	}

	private void addWorld(JsonObject line)
	{
		line.addProperty("world", client.getWorld());
		JsonArray types = new JsonArray();
		for (WorldType type : client.getWorldType())
		{
			types.add(type.name());
		}
		line.add("worldTypes", types);
	}

	/**
	 * What the server already sent before recording started: your items, skills and varps, and the
	 * objects and ground items in range. NPCs follow as npcSeen lines on the first tick.
	 */
	private void emitBaseline()
	{
		for (int id : BASELINE_CONTAINERS)
		{
			ItemContainer container = client.getItemContainer(id);
			if (container != null)
			{
				JsonObject line = itemsLine(id, container);
				line.addProperty("baseline", true);
				emit(line);
			}
		}

		for (Skill skill : Skill.values())
		{
			JsonObject line = line("stat");
			line.addProperty("skill", skill.getName());
			line.addProperty("xp", client.getSkillExperience(skill));
			line.addProperty("level", client.getRealSkillLevel(skill));
			line.addProperty("boosted", client.getBoostedSkillLevel(skill));
			line.addProperty("baseline", true);
			emit(line);
		}

		if (config.varbits())
		{
			JsonObject varps = new JsonObject();
			long[] values = client.getServerVarpsLong();
			for (int i = 0; i < values.length; i++)
			{
				if (values[i] != 0)
				{
					varps.addProperty(Integer.toString(i), values[i]);
				}
			}
			JsonObject line = line("varps");
			line.add("values", varps);
			line.addProperty("baseline", true);
			emit(line);
		}

		if (config.objects())
		{
			Tile[][][] tiles = worldView().getScene().getTiles();
			for (Tile[][] plane : tiles)
			{
				for (Tile[] column : plane)
				{
					for (Tile tile : column)
					{
						if (tile != null && inRadius(tile.getWorldLocation()))
						{
							baselineTile(tile);
						}
					}
				}
			}
		}
	}

	private void baselineTile(Tile tile)
	{
		List<TileObject> objects = new ArrayList<>();
		for (GameObject object : tile.getGameObjects())
		{
			// Multi-tile objects sit on every tile they cover; write them once, from their SW tile.
			if (object != null && object.getSceneMinLocation().equals(tile.getSceneLocation()))
			{
				objects.add(object);
			}
		}
		objects.add(tile.getWallObject());
		objects.add(tile.getGroundObject());
		objects.add(tile.getDecorativeObject());
		for (TileObject object : objects)
		{
			if (object != null)
			{
				JsonObject line = objectLine("object", object);
				line.addProperty("baseline", true);
				emit(line);
			}
		}
		List<TileItem> items = tile.getGroundItems();
		if (items != null)
		{
			for (TileItem item : items)
			{
				JsonObject line = groundItemLine("groundItem", tile, item);
				line.addProperty("baseline", true);
				emit(line);
			}
		}
	}

	// ---- per-tick snapshot ----

	@Subscribe
	public void onGameTick(GameTick event)
	{
		sceneLoading = false;
		if (!recording)
		{
			return;
		}
		RecordingWriter w = writer;
		String failure = w == null ? null : w.takeFailure();
		if (failure != null)
		{
			recording = false;
			configManager.setConfiguration(TickRecorderConfig.GROUP, TickRecorderConfig.RECORDING, false);
			chat("Tick Recorder stopped: " + failure);
			return;
		}
		Player self = client.getLocalPlayer();
		if (self == null)
		{
			return;
		}

		Set<Integer> inRange = new HashSet<>();
		JsonArray npcs = new JsonArray();
		for (NPC npc : worldView().npcs())
		{
			if (!nearby(npc))
			{
				continue;
			}
			inRange.add(npc.getIndex());
			if (!npcsInRange.contains(npc.getIndex()))
			{
				emit(npcLine("npcSeen", npc));
			}
			JsonObject state = actorState(npc);
			state.addProperty("size", size(npc));
			npcs.add(state);
		}
		npcsInRange = inRange;

		JsonObject snapshot = line("tick");
		snapshot.addProperty("clientTick", client.getTickCount());
		snapshot.addProperty("ms", System.currentTimeMillis());
		JsonObject selfState = actorState(self);
		selfState.addProperty("energy", client.getEnergy());
		selfState.addProperty("weight", client.getWeight());
		LocalPoint destination = client.getLocalDestinationLocation();
		if (destination != null)
		{
			selfState.add("destination", location(WorldPoint.fromLocal(client, destination)));
		}
		snapshot.add("self", selfState);
		snapshot.add("npcs", npcs);

		if (config.otherPlayers())
		{
			JsonArray players = new JsonArray();
			for (Player player : worldView().players())
			{
				if (player != self && nearby(player))
				{
					players.add(actorState(player));
				}
			}
			snapshot.add("players", players);
		}

		tick++;
		emit(snapshot);
		if (w != null)
		{
			w.flush();
		}
	}

	/** Projectiles are picked up on the client cycle they appear, so startCycle - cycle is their delay. */
	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (!recording)
		{
			return;
		}
		for (Projectile projectile : client.getProjectiles())
		{
			if (!seenProjectiles.add(projectile))
			{
				continue;
			}
			Actor source = projectile.getSourceActor();
			Actor target = projectile.getTargetActor();
			if (isOtherPlayer(source) && !config.otherPlayers())
			{
				continue;
			}
			WorldPoint from = projectile.getSourcePoint();
			WorldPoint to = projectile.getTargetPoint();
			boolean relevant = (from != null && inRadius(from)) || (to != null && inRadius(to))
				|| target == client.getLocalPlayer();
			if (!relevant)
			{
				continue;
			}
			JsonObject line = line("projectile");
			line.addProperty("id", projectile.getId());
			line.add("from", location(from));
			line.addProperty("sourceLevel", projectile.getSourceLevel());
			addActor(line, "source", source);
			line.add("to", location(to));
			line.addProperty("targetLevel", projectile.getTargetLevel());
			addActor(line, "target", target);
			line.addProperty("startCycle", projectile.getStartCycle());
			line.addProperty("endCycle", projectile.getEndCycle());
			line.addProperty("startHeight", projectile.getStartHeight());
			line.addProperty("endHeight", projectile.getEndHeight());
			line.addProperty("slope", projectile.getSlope());
			line.addProperty("startPos", projectile.getStartPos());
			emit(line);
		}
	}

	// ---- actor events ----

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (wanted(actor))
		{
			JsonObject line = actorLine("anim", actor);
			line.addProperty("anim", actor.getAnimation());
			line.addProperty("frame", actor.getAnimationFrame());
			emit(line);
		}
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		Actor actor = event.getActor();
		if (wanted(actor))
		{
			JsonObject line = actorLine("spotanims", actor);
			line.add("spotanims", spotAnims(actor));
			emit(line);
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor actor = event.getActor();
		if (wanted(actor))
		{
			Hitsplat hitsplat = event.getHitsplat();
			JsonObject line = actorLine("hitsplat", actor);
			line.addProperty("type", hitsplat.getHitsplatType());
			line.addProperty("amount", hitsplat.getAmount());
			line.addProperty("mine", hitsplat.isMine());
			line.addProperty("disappearsCycle", hitsplat.getDisappearsOnGameCycle());
			emit(line);
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		Actor source = event.getSource();
		if (wanted(source))
		{
			JsonObject line = actorLine("interact", source);
			addActor(line, "target", event.getTarget());
			emit(line);
		}
	}

	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged event)
	{
		// NPC shouts only; other players' chat is never recorded.
		if (event.getActor() instanceof NPC && wanted(event.getActor()))
		{
			JsonObject line = actorLine("say", event.getActor());
			line.addProperty("text", scrub(event.getOverheadText()));
			emit(line);
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (wanted(event.getNpc()))
		{
			emit(npcLine("npcSpawn", event.getNpc()));
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		if (recording && npcsInRange.contains(npc.getIndex()))
		{
			emit(npcLine("npcDespawn", npc));
		}
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		if (wanted(event.getNpc()))
		{
			JsonObject line = npcLine("npcChange", event.getNpc());
			line.addProperty("oldId", event.getOld().getId());
			emit(line);
		}
	}

	// ---- tile events ----

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated event)
	{
		GraphicsObject object = event.getGraphicsObject();
		LocalPoint point = object.getLocation();
		if (!recording || point == null)
		{
			return;
		}
		WorldPoint at = WorldPoint.fromLocal(worldView(), point.getX(), point.getY(), object.getLevel());
		if (inRadius(at))
		{
			JsonObject line = line("spotanim");
			line.addProperty("id", object.getId());
			line.add("at", location(at));
			line.addProperty("startCycle", object.getStartCycle());
			emit(line);
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		object("objectSpawn", event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		object("objectDespawn", event.getGameObject());
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		object("objectSpawn", event.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		object("objectDespawn", event.getWallObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		object("objectSpawn", event.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		object("objectDespawn", event.getGroundObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		object("objectSpawn", event.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		object("objectDespawn", event.getDecorativeObject());
	}

	private void object(String type, TileObject object)
	{
		if (recording && config.objects() && inRadius(objectOrigin(object)))
		{
			emit(objectLine(type, object));
		}
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		groundItem("groundItem", event.getTile(), event.getItem());
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		groundItem("groundItemGone", event.getTile(), event.getItem());
	}

	private void groundItem(String type, Tile tile, TileItem item)
	{
		if (recording && config.objects() && inRadius(tile.getWorldLocation()))
		{
			emit(groundItemLine(type, tile, item));
		}
	}

	// ---- your own state ----

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		// Text from the game itself only, never chat typed by players.
		if (recording && (type == ChatMessageType.GAMEMESSAGE || type == ChatMessageType.SPAM
			|| type == ChatMessageType.ENGINE || type == ChatMessageType.MESBOX || type == ChatMessageType.DIALOG))
		{
			JsonObject line = line("message");
			line.addProperty("type", type.name());
			line.addProperty("text", scrub(event.getMessage()));
			emit(line);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (recording)
		{
			JsonObject line = line("stat");
			line.addProperty("skill", event.getSkill().getName());
			line.addProperty("xp", event.getXp());
			line.addProperty("level", event.getLevel());
			line.addProperty("boosted", event.getBoostedLevel());
			emit(line);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (recording && (event.getContainerId() & OTHER_PARTY_CONTAINER) == 0)
		{
			emit(itemsLine(event.getContainerId(), event.getItemContainer()));
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (recording && config.varbits())
		{
			JsonObject line = line("var");
			line.addProperty("varp", event.getVarpId());
			line.addProperty("varbit", event.getVarbitId());
			line.addProperty("value", event.getVarbitId() == -1 ? event.getLongValue() : event.getValue());
			emit(line);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!recording || !config.menuClicks())
		{
			return;
		}
		MenuEntry entry = event.getMenuEntry();
		boolean onPlayer = entry.getPlayer() != null || event.getMenuAction().name().contains("PLAYER");
		JsonObject line = line("click");
		line.addProperty("option", event.getMenuOption());
		line.addProperty("action", event.getMenuAction().name());
		line.addProperty("param0", event.getParam0());
		line.addProperty("param1", event.getParam1());
		line.addProperty("consumed", event.isConsumed());
		if (onPlayer)
		{
			// The target and identifier name or index another player.
			if (config.otherPlayers() && entry.getPlayer() != null)
			{
				line.add("player", actorRef(entry.getPlayer()));
			}
		}
		else
		{
			line.addProperty("target", scrub(Redactor.removeTags(event.getMenuTarget())));
			line.addProperty("id", event.getId());
			if (entry.getNpc() != null)
			{
				line.add("npc", actorRef(entry.getNpc()));
			}
		}
		emit(line);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (recording)
		{
			JsonObject line = line("widgetOpen");
			line.addProperty("group", event.getGroupId());
			emit(line);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (recording)
		{
			JsonObject line = line("widgetClose");
			line.addProperty("group", event.getGroupId());
			emit(line);
		}
	}

	@Subscribe
	public void onSoundEffectPlayed(SoundEffectPlayed event)
	{
		if (recording && config.sounds() && !(isOtherPlayer(event.getSource()) && !config.otherPlayers()))
		{
			JsonObject line = line("sound");
			line.addProperty("id", event.getSoundId());
			line.addProperty("delay", event.getDelay());
			addActor(line, "source", event.getSource());
			emit(line);
		}
	}

	@Subscribe
	public void onAreaSoundEffectPlayed(AreaSoundEffectPlayed event)
	{
		if (!recording || !config.sounds() || (isOtherPlayer(event.getSource()) && !config.otherPlayers()))
		{
			return;
		}
		WorldPoint at = WorldPoint.fromScene(worldView(), event.getSceneX(), event.getSceneY(), worldView().getPlane());
		if (inRadius(at))
		{
			JsonObject line = line("areaSound");
			line.addProperty("id", event.getSoundId());
			line.addProperty("delay", event.getDelay());
			line.addProperty("range", event.getRange());
			line.add("at", location(at));
			addActor(line, "source", event.getSource());
			emit(line);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOADING)
		{
			sceneLoading = true;
		}
		if (recording)
		{
			JsonObject line = line("gameState");
			line.addProperty("state", state.name());
			line.addProperty("ms", System.currentTimeMillis());
			emit(line);
		}
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			seenProjectiles.clear();
			npcsInRange = new HashSet<>();
		}
	}

	@Subscribe
	public void onWorldChanged(WorldChanged event)
	{
		if (recording)
		{
			JsonObject line = line("world");
			addWorld(line);
			emit(line);
		}
	}

	// ---- building lines ----

	private JsonObject line(String type)
	{
		JsonObject line = new JsonObject();
		line.addProperty("t", type);
		line.addProperty("tick", tick + 1);
		line.addProperty("cycle", client.getGameCycle());
		return line;
	}

	private JsonObject actorLine(String type, Actor actor)
	{
		JsonObject line = line(type);
		line.add("actor", actorRef(actor));
		return line;
	}

	private JsonObject npcLine(String type, NPC npc)
	{
		JsonObject line = actorLine(type, npc);
		line.addProperty("id", npc.getId());
		line.addProperty("name", npc.getName());
		line.addProperty("size", size(npc));
		line.addProperty("combatLevel", npc.getCombatLevel());
		line.add("at", location(npc.getWorldLocation()));
		return line;
	}

	private JsonObject objectLine(String type, TileObject object)
	{
		JsonObject line = line(type);
		line.addProperty("id", object.getId());
		line.add("at", location(objectOrigin(object)));
		int config = -1;
		if (object instanceof GameObject)
		{
			GameObject game = (GameObject) object;
			config = game.getConfig();
			line.addProperty("kind", "game");
			line.addProperty("sizeX", game.sizeX());
			line.addProperty("sizeY", game.sizeY());
		}
		else if (object instanceof WallObject)
		{
			WallObject wall = (WallObject) object;
			config = wall.getConfig();
			line.addProperty("kind", "wall");
			line.addProperty("orientationA", wall.getOrientationA());
			line.addProperty("orientationB", wall.getOrientationB());
		}
		else if (object instanceof GroundObject)
		{
			config = ((GroundObject) object).getConfig();
			line.addProperty("kind", "ground");
		}
		else if (object instanceof DecorativeObject)
		{
			config = ((DecorativeObject) object).getConfig();
			line.addProperty("kind", "decor");
		}
		if (config != -1)
		{
			line.addProperty("type", config & 31);
			line.addProperty("rotation", (config >>> 6) & 3);
		}
		if (sceneLoading)
		{
			line.addProperty("sceneLoad", true);
		}
		return line;
	}

	private JsonObject groundItemLine(String type, Tile tile, TileItem item)
	{
		JsonObject line = line(type);
		line.addProperty("id", item.getId());
		line.addProperty("quantity", item.getQuantity());
		line.add("at", location(tile.getWorldLocation()));
		line.addProperty("ownership", item.getOwnership());
		line.addProperty("private", item.isPrivate());
		line.addProperty("visibleTime", item.getVisibleTime());
		line.addProperty("despawnTime", item.getDespawnTime());
		if (sceneLoading)
		{
			line.addProperty("sceneLoad", true);
		}
		return line;
	}

	private JsonObject itemsLine(int containerId, ItemContainer container)
	{
		JsonObject line = line("items");
		line.addProperty("container", containerId);
		JsonArray items = new JsonArray();
		for (Item item : container.getItems())
		{
			JsonArray pair = new JsonArray();
			pair.add(item.getId());
			pair.add(item.getQuantity());
			items.add(pair);
		}
		line.add("items", items);
		return line;
	}

	/**
	 * Who an event is about: yourself, an NPC by index and id, or another player by index only. Null
	 * for another player while "Other players" is off.
	 */
	private JsonObject actorRef(Actor actor)
	{
		JsonObject ref = new JsonObject();
		if (actor == client.getLocalPlayer())
		{
			ref.addProperty("self", true);
		}
		else if (actor instanceof NPC)
		{
			NPC npc = (NPC) actor;
			ref.addProperty("npc", npc.getIndex());
			ref.addProperty("id", npc.getId());
		}
		else if (actor instanceof Player)
		{
			if (!config.otherPlayers())
			{
				return null;
			}
			ref.addProperty("player", ((Player) actor).getId());
		}
		return ref;
	}

	private void addActor(JsonObject line, String key, Actor actor)
	{
		if (actor != null)
		{
			JsonObject ref = actorRef(actor);
			if (ref != null)
			{
				line.add(key, ref);
			}
		}
	}

	private JsonObject actorState(Actor actor)
	{
		JsonObject state = actorRef(actor);
		state.add("at", location(actor.getWorldLocation()));
		state.addProperty("orientation", actor.getOrientation());
		state.addProperty("anim", actor.getAnimation());
		state.addProperty("frame", actor.getAnimationFrame());
		state.addProperty("pose", actor.getPoseAnimation());
		state.add("spotanims", spotAnims(actor));
		state.addProperty("healthRatio", actor.getHealthRatio());
		state.addProperty("healthScale", actor.getHealthScale());
		addActor(state, "target", actor.getInteracting());
		return state;
	}

	private static JsonArray spotAnims(Actor actor)
	{
		JsonArray list = new JsonArray();
		IterableHashTable<ActorSpotAnim> table = actor.getSpotAnims();
		if (table == null)
		{
			return list;
		}
		for (ActorSpotAnim spotAnim : table)
		{
			JsonObject entry = new JsonObject();
			entry.addProperty("slot", spotAnim.getHash());
			entry.addProperty("id", spotAnim.getId());
			entry.addProperty("height", spotAnim.getHeight());
			entry.addProperty("startCycle", spotAnim.getStartCycle());
			entry.addProperty("frame", spotAnim.getFrame());
			list.add(entry);
		}
		return list;
	}

	/** The SW tile of an object, which is what the server places it by. */
	private WorldPoint objectOrigin(TileObject object)
	{
		if (object instanceof GameObject)
		{
			Point min = ((GameObject) object).getSceneMinLocation();
			return WorldPoint.fromScene(worldView(), min.getX(), min.getY(), object.getPlane());
		}
		return object.getWorldLocation();
	}

	private static int size(NPC npc)
	{
		NPCComposition composition = npc.getTransformedComposition();
		return composition == null ? 1 : composition.getSize();
	}

	/**
	 * A tile as x, y, plane. Inside an instance it also carries tx, ty, tplane: the template tile the
	 * instance was copied from, which is the same on every visit.
	 */
	private JsonObject location(WorldPoint point)
	{
		JsonObject json = new JsonObject();
		if (point == null)
		{
			return json;
		}
		json.addProperty("x", point.getX());
		json.addProperty("y", point.getY());
		json.addProperty("plane", point.getPlane());
		WorldView wv = worldView();
		if (wv.isInstance())
		{
			// fromWorld(wv, WorldPoint) refuses other planes; the x/y overload does not, and the plane is passed below.
			LocalPoint local = LocalPoint.fromWorld(wv, point.getX(), point.getY());
			int[][][] chunks = wv.getInstanceTemplateChunks();
			int plane = point.getPlane();
			if (local != null && chunks != null && plane >= 0 && plane < chunks.length)
			{
				int cx = local.getSceneX() >> 3;
				int cy = local.getSceneY() >> 3;
				if (cx >= 0 && cx < chunks[plane].length && cy >= 0 && cy < chunks[plane][cx].length
					&& chunks[plane][cx][cy] != -1)
				{
					WorldPoint template = WorldPoint.fromLocalInstance(client, local, plane);
					json.addProperty("tx", template.getX());
					json.addProperty("ty", template.getY());
					json.addProperty("tplane", template.getPlane());
				}
			}
		}
		return json;
	}

	// ---- filters ----

	private boolean wanted(Actor actor)
	{
		if (!recording || actor == null)
		{
			return false;
		}
		if (actor == client.getLocalPlayer())
		{
			return true;
		}
		if (isOtherPlayer(actor) && !config.otherPlayers())
		{
			return false;
		}
		return nearby(actor);
	}

	private boolean isOtherPlayer(Actor actor)
	{
		return actor instanceof Player && actor != client.getLocalPlayer();
	}

	/** On your plane and within the radius, by server tile. */
	private boolean nearby(Actor actor)
	{
		WorldPoint point = actor.getWorldLocation();
		Player self = client.getLocalPlayer();
		return point != null && self != null && point.getPlane() == self.getWorldLocation().getPlane()
			&& inRadius(point);
	}

	/** Within the radius horizontally, on any plane: objects upstairs still matter for a rebuild. */
	private boolean inRadius(WorldPoint point)
	{
		Player self = client.getLocalPlayer();
		if (point == null || self == null)
		{
			return false;
		}
		WorldPoint here = self.getWorldLocation();
		return Math.max(Math.abs(point.getX() - here.getX()), Math.abs(point.getY() - here.getY())) <= config.radius();
	}

	private String scrub(String text)
	{
		List<String> names = new ArrayList<>();
		Player self = client.getLocalPlayer();
		for (Player player : worldView().players())
		{
			if (player != self && player.getName() != null)
			{
				names.add(player.getName());
			}
		}
		return Redactor.scrub(text, self == null ? null : self.getName(), names);
	}

	private WorldView worldView()
	{
		return client.getTopLevelWorldView();
	}

	private void emit(JsonObject line)
	{
		RecordingWriter w = writer;
		if (w != null)
		{
			w.write(json.toJson(line));
		}
	}

	private void chat(String message)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
		}
	}
}
