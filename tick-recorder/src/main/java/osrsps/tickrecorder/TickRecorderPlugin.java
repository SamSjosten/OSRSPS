package osrsps.tickrecorder;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.io.File;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.AreaSoundEffectPlayed;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
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
import net.runelite.client.RuneLite;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.HotkeyListener;

/**
 * Records what the client already sees to a JSON lines file: one "tick" line per game tick with the
 * state of everything nearby, plus one line per event (animation, hitsplat, projectile, ...) as it
 * happens. Events carry the number of the tick line that follows them, which is the tick whose
 * packets produced them. See README.md for the format.
 */
@PluginDescriptor(
	name = "Tick Recorder",
	description = "Records what the client sees each game tick to a local file",
	tags = {"record", "log", "tick", "animation", "projectile", "hitsplat", "npc"}
)
public class TickRecorderPlugin extends Plugin
{
	static final int FORMAT_VERSION = 1;
	static final File RECORDINGS_DIR = new File(RuneLite.RUNELITE_DIR, "tick-recorder");

	private static final DateTimeFormatter FILE_TIME =
		DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);

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

	private RecordingWriter writer;
	private boolean recording;
	/** Ticks since this recording started; the next tick line gets tick + 1. */
	private int tick;
	private final Set<Projectile> seenProjectiles = Collections.newSetFromMap(new WeakHashMap<>());

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
		recording = false;
		writer.shutdown();
		writer = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!TickRecorderConfig.GROUP.equals(event.getGroup()) || !TickRecorderConfig.RECORDING.equals(event.getKey()))
		{
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
		String name = FILE_TIME.format(Instant.now()) + ".jsonl";
		writer.open(new File(RECORDINGS_DIR, name).toPath());
		recording = true;
		tick = 0;
		seenProjectiles.clear();

		JsonObject header = line("start");
		header.addProperty("format", FORMAT_VERSION);
		header.addProperty("runelite", RuneLiteProperties.getVersion());
		header.addProperty("world", client.getWorld());
		header.addProperty("time", Instant.now().toString());
		header.addProperty("radius", config.radius());
		emit(header);
		chat("Tick Recorder: recording to " + name);
	}

	private void stopRecording()
	{
		JsonObject stop = line("stop");
		stop.addProperty("time", Instant.now().toString());
		emit(stop);
		recording = false;
		writer.close();
		chat("Tick Recorder: stopped after " + tick + " ticks");
	}

	// ---- per-tick snapshot ----

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!recording)
		{
			return;
		}
		Player self = client.getLocalPlayer();
		if (self == null)
		{
			return;
		}

		recordNewProjectiles();

		JsonObject snapshot = line("tick");
		snapshot.addProperty("clientTick", client.getTickCount());
		snapshot.add("self", actorState(self));

		JsonArray npcs = new JsonArray();
		for (NPC npc : worldView().npcs())
		{
			if (nearby(npc))
			{
				npcs.add(actorState(npc));
			}
		}
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
		writer.flush();
	}

	private void recordNewProjectiles()
	{
		for (Projectile projectile : client.getProjectiles())
		{
			if (!seenProjectiles.add(projectile))
			{
				continue;
			}
			LocalPoint source = local(projectile.getSourcePoint());
			if (!nearby(source))
			{
				continue;
			}
			JsonObject json = line("projectile");
			json.addProperty("id", projectile.getId());
			json.add("from", location(source));
			Actor sourceActor = projectile.getSourceActor();
			if (sourceActor != null)
			{
				json.add("source", actorRef(sourceActor));
			}
			json.add("to", location(local(projectile.getTargetPoint())));
			Actor target = projectile.getTargetActor();
			if (target != null)
			{
				json.add("target", actorRef(target));
			}
			json.addProperty("startCycle", projectile.getStartCycle());
			json.addProperty("endCycle", projectile.getEndCycle());
			json.addProperty("startHeight", projectile.getStartHeight());
			json.addProperty("endHeight", projectile.getEndHeight());
			json.addProperty("slope", projectile.getSlope());
			json.addProperty("startPos", projectile.getStartPos());
			emit(json);
		}
	}

	// ---- events ----

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (wanted(actor))
		{
			JsonObject json = actorLine("anim", actor);
			json.addProperty("anim", actor.getAnimation());
			emit(json);
		}
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		Actor actor = event.getActor();
		if (wanted(actor))
		{
			JsonObject json = actorLine("graphic", actor);
			json.addProperty("graphic", actor.getGraphic());
			json.addProperty("height", actor.getGraphicHeight());
			emit(json);
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor actor = event.getActor();
		if (wanted(actor))
		{
			Hitsplat hitsplat = event.getHitsplat();
			JsonObject json = actorLine("hitsplat", actor);
			json.addProperty("type", hitsplat.getHitsplatType());
			json.addProperty("amount", hitsplat.getAmount());
			json.addProperty("mine", hitsplat.isMine());
			json.addProperty("healthRatio", actor.getHealthRatio());
			json.addProperty("healthScale", actor.getHealthScale());
			emit(json);
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		Actor source = event.getSource();
		if (wanted(source))
		{
			JsonObject json = actorLine("interact", source);
			Actor target = event.getTarget();
			if (target != null)
			{
				json.add("target", actorRef(target));
			}
			emit(json);
		}
	}

	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged event)
	{
		// NPC shouts only; other players' chat is never recorded.
		if (event.getActor() instanceof NPC && wanted(event.getActor()))
		{
			JsonObject json = actorLine("say", event.getActor());
			json.addProperty("text", event.getOverheadText());
			emit(json);
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
		if (wanted(event.getNpc()))
		{
			emit(npcLine("npcDespawn", event.getNpc()));
		}
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		if (wanted(event.getNpc()))
		{
			JsonObject json = npcLine("npcChange", event.getNpc());
			json.addProperty("oldId", event.getOld().getId());
			emit(json);
		}
	}

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated event)
	{
		GraphicsObject object = event.getGraphicsObject();
		if (recording && nearby(object.getLocation()))
		{
			JsonObject json = line("spotanim");
			json.addProperty("id", object.getId());
			json.add("at", location(object.getLocation()));
			json.addProperty("startCycle", object.getStartCycle());
			emit(json);
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
		object("wallSpawn", event.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		object("wallDespawn", event.getWallObject());
	}

	private void object(String type, TileObject object)
	{
		if (!recording || !config.objects() || !nearby(object.getLocalLocation()))
		{
			return;
		}
		JsonObject json = line(type);
		json.addProperty("id", object.getId());
		json.add("at", location(object.getLocalLocation()));
		if (object instanceof GameObject)
		{
			json.addProperty("orientation", ((GameObject) object).getOrientation());
		}
		emit(json);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		// Messages from the game itself only, never chat typed by players.
		if (recording && (type == ChatMessageType.GAMEMESSAGE || type == ChatMessageType.SPAM
			|| type == ChatMessageType.ENGINE || type == ChatMessageType.MESBOX))
		{
			JsonObject json = line("message");
			json.addProperty("type", type.name());
			json.addProperty("text", event.getMessage());
			emit(json);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (recording)
		{
			JsonObject json = line("stat");
			json.addProperty("skill", event.getSkill().getName());
			json.addProperty("xp", event.getXp());
			json.addProperty("level", event.getLevel());
			json.addProperty("boosted", event.getBoostedLevel());
			emit(json);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (!recording)
		{
			return;
		}
		JsonObject json = line("items");
		json.addProperty("container", event.getContainerId());
		JsonArray items = new JsonArray();
		for (Item item : event.getItemContainer().getItems())
		{
			JsonArray pair = new JsonArray();
			pair.add(item.getId());
			pair.add(item.getQuantity());
			items.add(pair);
		}
		json.add("items", items);
		emit(json);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (recording && config.varbits())
		{
			JsonObject json = line("var");
			json.addProperty("varp", event.getVarpId());
			json.addProperty("varbit", event.getVarbitId());
			json.addProperty("value", event.getValue());
			emit(json);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (recording && config.menuClicks())
		{
			JsonObject json = line("click");
			json.addProperty("option", event.getMenuOption());
			json.addProperty("target", event.getMenuTarget());
			json.addProperty("action", event.getMenuAction().name());
			json.addProperty("id", event.getId());
			json.addProperty("param0", event.getParam0());
			json.addProperty("param1", event.getParam1());
			emit(json);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (recording)
		{
			JsonObject json = line("widgetOpen");
			json.addProperty("group", event.getGroupId());
			emit(json);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (recording)
		{
			JsonObject json = line("widgetClose");
			json.addProperty("group", event.getGroupId());
			emit(json);
		}
	}

	@Subscribe
	public void onSoundEffectPlayed(SoundEffectPlayed event)
	{
		if (recording && config.sounds())
		{
			JsonObject json = line("sound");
			json.addProperty("id", event.getSoundId());
			json.addProperty("delay", event.getDelay());
			if (event.getSource() != null)
			{
				json.add("source", actorRef(event.getSource()));
			}
			emit(json);
		}
	}

	@Subscribe
	public void onAreaSoundEffectPlayed(AreaSoundEffectPlayed event)
	{
		if (recording && config.sounds())
		{
			JsonObject json = line("areaSound");
			json.addProperty("id", event.getSoundId());
			json.addProperty("delay", event.getDelay());
			json.addProperty("range", event.getRange());
			json.add("at", location(LocalPoint.fromScene(event.getSceneX(), event.getSceneY(), worldView())));
			emit(json);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (recording)
		{
			JsonObject json = line("gameState");
			json.addProperty("state", event.getGameState().name());
			emit(json);
		}
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			seenProjectiles.clear();
		}
	}

	// ---- building lines ----

	private JsonObject line(String type)
	{
		JsonObject json = new JsonObject();
		json.addProperty("t", type);
		json.addProperty("tick", tick + 1);
		json.addProperty("cycle", client.getGameCycle());
		return json;
	}

	private JsonObject actorLine(String type, Actor actor)
	{
		JsonObject json = line(type);
		json.add("actor", actorRef(actor));
		return json;
	}

	private JsonObject npcLine(String type, NPC npc)
	{
		JsonObject json = actorLine(type, npc);
		json.addProperty("id", npc.getId());
		json.addProperty("name", npc.getName());
		NPCComposition composition = npc.getTransformedComposition();
		if (composition != null)
		{
			json.addProperty("size", composition.getSize());
		}
		json.addProperty("combatLevel", npc.getCombatLevel());
		json.add("at", location(npc.getLocalLocation()));
		return json;
	}

	/** Who an event is about: yourself, an NPC by index and id, or another player by index only. */
	private JsonObject actorRef(Actor actor)
	{
		JsonObject json = new JsonObject();
		if (actor == client.getLocalPlayer())
		{
			json.addProperty("self", true);
		}
		else if (actor instanceof NPC)
		{
			NPC npc = (NPC) actor;
			json.addProperty("npc", npc.getIndex());
			json.addProperty("id", npc.getId());
		}
		else if (actor instanceof Player)
		{
			json.addProperty("player", ((Player) actor).getId());
		}
		return json;
	}

	private JsonObject actorState(Actor actor)
	{
		JsonObject json = actorRef(actor);
		json.add("at", location(actor.getLocalLocation()));
		json.addProperty("orientation", actor.getOrientation());
		json.addProperty("anim", actor.getAnimation());
		json.addProperty("pose", actor.getPoseAnimation());
		json.addProperty("graphic", actor.getGraphic());
		json.addProperty("healthRatio", actor.getHealthRatio());
		json.addProperty("healthScale", actor.getHealthScale());
		Actor target = actor.getInteracting();
		if (target != null)
		{
			json.add("target", actorRef(target));
		}
		return json;
	}

	/**
	 * A tile as x, y, plane. Inside an instance, tx/ty/tplane also give the template tile the
	 * instance was copied from, which is the same on every visit.
	 */
	private JsonObject location(LocalPoint point)
	{
		JsonObject json = new JsonObject();
		if (point == null)
		{
			return json;
		}
		WorldPoint world = WorldPoint.fromLocal(client, point);
		json.addProperty("x", world.getX());
		json.addProperty("y", world.getY());
		json.addProperty("plane", world.getPlane());
		if (worldView().isInstance())
		{
			WorldPoint template = WorldPoint.fromLocalInstance(client, point);
			json.addProperty("tx", template.getX());
			json.addProperty("ty", template.getY());
			json.addProperty("tplane", template.getPlane());
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
		if (actor instanceof Player && !config.otherPlayers())
		{
			return false;
		}
		return nearby(actor);
	}

	private boolean nearby(Actor actor)
	{
		return nearby(actor.getLocalLocation());
	}

	private boolean nearby(LocalPoint point)
	{
		Player self = client.getLocalPlayer();
		if (point == null || self == null || self.getLocalLocation() == null)
		{
			return false;
		}
		LocalPoint here = self.getLocalLocation();
		int dx = Math.abs(point.getSceneX() - here.getSceneX());
		int dy = Math.abs(point.getSceneY() - here.getSceneY());
		return Math.max(dx, dy) <= config.radius();
	}

	private LocalPoint local(WorldPoint point)
	{
		return point == null ? null : LocalPoint.fromWorld(worldView(), point);
	}

	private WorldView worldView()
	{
		return client.getTopLevelWorldView();
	}

	private void emit(JsonObject json)
	{
		writer.write(gson.toJson(json));
	}

	private void chat(String message)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
		}
	}
}
