package osrsps.testrunner;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.SwingUtilities;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EntityOps;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.Widget;

/**
 * {@link Game} over RuneLite's client API. Every action is the menu entry a click would have made, sent with
 * {@link Client#menuAction}, so the client sends the same packet a player's click sends. Chat and walking are the places
 * input events are used: for chat the text goes in the chat input and an Enter key event goes to the canvas; for a walk
 * the mouse is moved over the tile first.
 */
final class ClientGame implements Game
{
	private static final MenuAction[] NPC_OPS = {
		MenuAction.NPC_FIRST_OPTION,
		MenuAction.NPC_SECOND_OPTION,
		MenuAction.NPC_THIRD_OPTION,
		MenuAction.NPC_FOURTH_OPTION,
		MenuAction.NPC_FIFTH_OPTION,
	};

	private static final MenuAction[] LOC_OPS = {
		MenuAction.GAME_OBJECT_FIRST_OPTION,
		MenuAction.GAME_OBJECT_SECOND_OPTION,
		MenuAction.GAME_OBJECT_THIRD_OPTION,
		MenuAction.GAME_OBJECT_FOURTH_OPTION,
		MenuAction.GAME_OBJECT_FIFTH_OPTION,
	};

	/**
	 * The steepest camera pitch the client allows without its pitch relaxer: looking down at the player. Camera angles are
	 * JAU14 (2π/16384 a unit); this is the old 11-bit 383 scaled up.
	 */
	private static final int CAMERA_PITCH_DOWN = 383 << 3;
	private static final String EXAMINE = "Examine";
	private static final String TAKE = "Take";
	private static final String REMOVE = "Remove";
	private static final int EXAMINE_OP = 10;

	/**
	 * The button op our server reads as an item's iop1 to iop5 in the inventory (InventoryTab.IOP_BY_BUTTON_OP, after
	 * RSMod), for when the slot's component has no ops of its own to read.
	 */
	private static final int[] INVENTORY_IOP_BUTTON_OPS = {2, 3, 4, 6, 7};

	/** The equipment tab's slot components by wearpos (0 head to 13 ammo); 0 where no slot is shown. */
	private static final int[] WORN_SLOT_COMPONENTS = {
		InterfaceID.Wornitems.SLOT0, InterfaceID.Wornitems.SLOT1, InterfaceID.Wornitems.SLOT2, InterfaceID.Wornitems.SLOT3,
		InterfaceID.Wornitems.SLOT4, InterfaceID.Wornitems.SLOT5, 0, InterfaceID.Wornitems.SLOT7, 0,
		InterfaceID.Wornitems.SLOT9, InterfaceID.Wornitems.SLOT10, 0, InterfaceID.Wornitems.SLOT12, InterfaceID.Wornitems.SLOT13,
	};
	/** How far, in pixels, the mouse may be from a walk's spot: the spot drifts while the camera eases. */
	private static final int MOUSE_SLACK = 2;

	private final Client client;
	/** The line {@link #sendChat} last put in the chat input, until the client has sent it. */
	private String chatLine;
	/** Set on the Swing thread once the Enter key events for {@link #chatLine} have gone to the canvas. */
	private volatile boolean enterDelivered;
	/** Walk calls in a row that found the mouse on the walk's spot. */
	private int mouseFrames;

	ClientGame(Client client)
	{
		this.client = client;
	}

	@Override
	public boolean loggedIn()
	{
		return client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null;
	}

	@Override
	public String localName()
	{
		Player p = client.getLocalPlayer();
		return p == null ? null : p.getName();
	}

	@Override
	public Tile location()
	{
		Player p = client.getLocalPlayer();
		if (p == null)
		{
			return null;
		}
		WorldPoint w = p.getWorldLocation();
		return new Tile(w.getX(), w.getY(), w.getPlane());
	}

	@Override
	public Tile drawnLocation()
	{
		Player p = client.getLocalPlayer();
		if (p == null)
		{
			return null;
		}
		// getWorldLocation is the tile the server last sent; the local location is where the client draws the player.
		WorldView wv = client.getTopLevelWorldView();
		LocalPoint lp = p.getLocalLocation();
		WorldPoint w = WorldPoint.fromLocal(wv, lp.getX(), lp.getY(), wv.getPlane());
		return new Tile(w.getX(), w.getY(), w.getPlane());
	}

	@Override
	public String sendChat(String text)
	{
		Canvas canvas = client.getCanvas();
		if (canvas == null)
		{
			return "the client has no canvas";
		}
		// The chatbox keeps what has been typed in this varc; Enter then sends it, "::" lines as a command.
		client.setVarcStrValue(VarClientID.CHATINPUT, text);
		chatLine = text;
		pressEnter(canvas);
		return null;
	}

	@Override
	public String pressEnterAgain()
	{
		Canvas canvas = client.getCanvas();
		if (canvas == null)
		{
			return "the client has no canvas";
		}
		// A 0.5.0 run left "::combat man" in the input after a 30-tick wait, the Enter never taken, while the same
		// run's earlier commands went fine. Whether the canvas had lost keyboard focus is unknown; the note says.
		boolean focused = canvas.isFocusOwner();
		pressEnter(canvas);
		return focused ? "the canvas has keyboard focus" : "the canvas had no keyboard focus, asked for it";
	}

	/** Sends the Enter key to the canvas on the Swing thread, as a key press does, taking keyboard focus first. */
	private void pressEnter(Canvas canvas)
	{
		enterDelivered = false;
		SwingUtilities.invokeLater(() ->
		{
			if (!canvas.isFocusOwner())
			{
				canvas.requestFocusInWindow();
			}
			long now = System.currentTimeMillis();
			canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_PRESSED, now, 0, KeyEvent.VK_ENTER, '\n'));
			canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_TYPED, now, 0, KeyEvent.VK_UNDEFINED, '\n'));
			canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_RELEASED, now, 0, KeyEvent.VK_ENTER, '\n'));
			enterDelivered = true;
		});
	}

	@Override
	public boolean chatPending()
	{
		if (chatLine == null)
		{
			return false;
		}
		// Sending a line empties the chat input, so the line is gone once the input no longer holds it.
		if (!enterDelivered || chatLine.equals(client.getVarcStrValue(VarClientID.CHATINPUT)))
		{
			return true;
		}
		chatLine = null;
		return false;
	}

	@Override
	public String walk(int x, int y)
	{
		if (client.isMenuOpen())
		{
			return "a right-click menu is open";
		}
		WorldView wv = client.getTopLevelWorldView();
		LocalPoint lp = LocalPoint.fromWorld(wv, x, y);
		if (lp == null)
		{
			return "tile " + x + ", " + y + " is not in the loaded scene";
		}
		Point p = Perspective.localToCanvas(client, lp, wv.getPlane());
		int vx = client.getViewportXOffset();
		int vy = client.getViewportYOffset();
		if (p == null || p.getX() < vx || p.getY() < vy || p.getX() >= vx + client.getViewportWidth() || p.getY() >= vy + client.getViewportHeight())
		{
			faceCamera(lp);
			return "tile " + x + ", " + y + " is not on screen; walk somewhere closer first (" + cameraState(lp, p) + ")";
		}
		// Walk here goes to the tile under the mouse, as a click does; the client picks it while drawing the next frame.
		// Sending Walk here with the spot as its params (0.4.4 and before) walked the player 8 to 16 tiles off on the
		// VM, so the mouse may be what counts: it goes over the tile first, and once the client has drawn a frame with it
		// there, the Walk here entry the client built for that spot is used, as a right-click and Walk here would.
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null || Math.abs(mouse.getX() - p.getX()) > MOUSE_SLACK || Math.abs(mouse.getY() - p.getY()) > MOUSE_SLACK)
		{
			mouseFrames = 0;
			moveMouse(p);
			return "pointing the mouse at tile " + x + ", " + y;
		}
		if (++mouseFrames < 2)
		{
			return "pointing the mouse at tile " + x + ", " + y;
		}
		MenuEntry walkHere = null;
		for (MenuEntry e : client.getMenu().getMenuEntries())
		{
			if (e.getType() == MenuAction.WALK)
			{
				walkHere = e;
			}
		}
		if (walkHere == null)
		{
			return "no Walk here in the menu at " + p.getX() + ", " + p.getY();
		}
		mouseFrames = 0;
		client.menuAction(walkHere.getParam0(), walkHere.getParam1(), MenuAction.WALK, walkHere.getIdentifier(),
			walkHere.getItemId(), walkHere.getOption(), walkHere.getTarget());
		return null;
	}

	/**
	 * Moves the mouse over a canvas spot, as a person would before clicking. The event goes to the canvas on the AWT
	 * thread, like chat's Enter key. Assumes stretched mode is off, so canvas and game coordinates are the same.
	 */
	private void moveMouse(Point p)
	{
		Canvas canvas = client.getCanvas();
		if (canvas == null)
		{
			return;
		}
		SwingUtilities.invokeLater(() -> canvas.dispatchEvent(
			new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, p.getX(), p.getY(), 0, false)));
	}

	@Override
	public Tile walkDestination()
	{
		LocalPoint d = client.getLocalDestinationLocation();
		if (d == null)
		{
			return null;
		}
		WorldView wv = client.getTopLevelWorldView();
		WorldPoint w = WorldPoint.fromLocal(wv, d.getX(), d.getY(), wv.getPlane());
		return new Tile(w.getX(), w.getY(), w.getPlane());
	}

	/**
	 * Turns the camera toward the target and tilts it down to the usual steepest angle, so a tile near the player comes
	 * into view however the camera was left (a fresh client can face a wall). The camera eases there over the next
	 * frames; the caller tries again. The angle is from Perspective's projection: yaw 0 looks north and a quarter turn
	 * (4096 JAU14) looks west. Not yet tried in the client.
	 */
	private void faceCamera(LocalPoint target)
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return;
		}
		LocalPoint here = me.getLocalLocation();
		int dx = target.getX() - here.getX();
		int dy = target.getY() - here.getY();
		if (dx != 0 || dy != 0)
		{
			client.setCameraYawTarget((int) Math.round(Math.atan2(-dx, dy) / Perspective.UNIT14) & 0x3fff);
		}
		client.setCameraPitchTarget(CAMERA_PITCH_DOWN);
	}

	/** Where the target landed and what the camera was doing, for a walk that can't find its tile on screen. */
	private String cameraState(LocalPoint target, Point p)
	{
		Player me = client.getLocalPlayer();
		LocalPoint here = me != null ? me.getLocalLocation() : null;
		return String.format("projected to %s, viewport %d,%d %dx%d; camera yaw %d target %d, pitch %d target %d, mode %d, scale %d,"
				+ " at %.0f,%.0f,%.0f, focus %.0f,%.0f; player %s, tile %d,%d",
			p == null ? "nothing" : p.getX() + "," + p.getY(),
			client.getViewportXOffset(), client.getViewportYOffset(), client.getViewportWidth(), client.getViewportHeight(),
			client.getCameraYaw(), client.getCameraYawTarget(), client.getCameraPitch(), client.getCameraPitchTarget(),
			client.getCameraMode(), client.getScale(),
			client.getCameraFpX(), client.getCameraFpY(), client.getCameraFpZ(),
			client.getCameraFocalPointX(), client.getCameraFocalPointY(),
			here == null ? "none" : here.getX() + "," + here.getY(), target.getX(), target.getY());
	}

	@Override
	public String npcOption(String name, int id, String option)
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return "no local player";
		}
		WorldPoint here = me.getWorldLocation();
		boolean examine = isExamine(option);
		NPC best = null;
		int bestDistance = Integer.MAX_VALUE;
		boolean nameSeen = false;
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (npc == null)
			{
				continue;
			}
			boolean match = name != null ? name.equalsIgnoreCase(Dialogue.clean(npc.getName())) : npc.getId() == id;
			if (!match || npc.getWorldLocation().getPlane() != here.getPlane())
			{
				continue;
			}
			nameSeen = true;
			int d = npc.getWorldLocation().distanceTo(here);
			if (d < bestDistance && (examine || opIndex(npc, option) >= 0))
			{
				best = npc;
				bestDistance = d;
			}
		}
		String who = name != null ? "\"" + name + "\"" : "id " + id;
		if (best == null)
		{
			return nameSeen ? "NPC " + who + " has no option \"" + option + "\"" : "no NPC " + who + " in view";
		}
		if (examine)
		{
			// The client adds Examine to every NPC's menu itself and sends the NPC's type with it.
			client.menuAction(0, 0, MenuAction.EXAMINE_NPC, best.getIndex(), -1, EXAMINE, best.getName());
			return null;
		}
		int op = opIndex(best, option);
		client.menuAction(0, 0, NPC_OPS[op], best.getIndex(), -1, composition(best).getOps().getOp(op), best.getName());
		return null;
	}

	/** Examine isn't one of an NPC's or loc's own options: the client adds it to every menu. */
	private static boolean isExamine(String option)
	{
		return EXAMINE.equalsIgnoreCase(option);
	}

	private static NPCComposition composition(NPC npc)
	{
		NPCComposition c = npc.getTransformedComposition();
		return c != null ? c : npc.getComposition();
	}

	private static int opIndex(NPC npc, String option)
	{
		NPCComposition c = composition(npc);
		return c == null ? -1 : opSlot(c.getOps(), option);
	}

	/**
	 * The menu op slot (0 for the first option to 4 for the fifth) whose text is the option, or -1. Read from the ops,
	 * not getActions(): since RuneLite 1.13 that array is sparse (the second option at index 2, and so on).
	 */
	private static int opSlot(EntityOps ops, String option)
	{
		if (ops == null)
		{
			return -1;
		}
		for (int i = 0; i < EntityOps.MAX_OPS; i++)
		{
			String op = ops.getOp(i);
			if (op != null && Dialogue.clean(op).equalsIgnoreCase(option))
			{
				return i;
			}
		}
		return -1;
	}

	@Override
	public String locOption(String name, int id, String option, osrsps.testrunner.Tile at)
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return "no local player";
		}
		WorldView wv = client.getTopLevelWorldView();
		Scene scene = wv.getScene();
		int plane = wv.getPlane();
		LocalPoint target = at == null ? null : LocalPoint.fromWorld(wv, at.x, at.y);
		if (at != null && target == null)
		{
			return "tile " + at.x + ", " + at.y + " is not in the loaded scene";
		}
		WorldPoint here = me.getWorldLocation();
		boolean examine = isExamine(option);
		TileObject best = null;
		int bestOp = -1;
		int bestDistance = Integer.MAX_VALUE;
		boolean nameSeen = false;
		for (net.runelite.api.Tile[] column : scene.getTiles()[plane])
		{
			for (net.runelite.api.Tile tile : column)
			{
				if (tile == null)
				{
					continue;
				}
				List<TileObject> objects = new ArrayList<>();
				// A big loc is listed on every tile it covers; seeing it more than once does no harm.
				objects.addAll(Arrays.asList(tile.getGameObjects()));
				objects.add(tile.getWallObject());
				objects.add(tile.getDecorativeObject());
				objects.add(tile.getGroundObject());
				for (TileObject o : objects)
				{
					if (o == null || !locMatches(o, name, id) || (target != null && !covers(o, tile, target)))
					{
						continue;
					}
					nameSeen = true;
					// A hidden multiloc (no composition) has no menu, Examine included.
					int op = examine ? (locComposition(o.getId()) != null ? 0 : -1) : locOpIndex(o.getId(), option);
					int d = o.getWorldLocation().distanceTo(here);
					if (op >= 0 && d < bestDistance)
					{
						best = o;
						bestOp = op;
						bestDistance = d;
					}
				}
			}
		}
		String what = (name != null ? "\"" + name + "\"" : "id " + id) + (at == null ? "" : " at " + at.x + ", " + at.y);
		if (best == null)
		{
			return nameSeen ? "loc " + what + " has no option \"" + option + "\"" : "no loc " + what + " in the scene";
		}
		// The menu entry a click makes: the loc's scene tile (a big loc's south-west tile) and its base id, which the
		// server turns into whatever the loc currently is. Guessed from how RuneLite's interact highlight reads a loc
		// click back; not yet tried in the client.
		Point p = best instanceof GameObject ? ((GameObject) best).getSceneMinLocation() : sceneTile(best);
		ObjectComposition c = locComposition(best.getId());
		if (examine)
		{
			// Examine is of what the loc is now, so its current form's id. Guessed from the client's menu, as above.
			client.menuAction(p.getX(), p.getY(), MenuAction.EXAMINE_OBJECT, c.getId(), -1, EXAMINE, c.getName());
			return null;
		}
		client.menuAction(p.getX(), p.getY(), LOC_OPS[bestOp], best.getId(), -1, c.getOps().getOp(bestOp), c.getName());
		return null;
	}

	private static Point sceneTile(TileObject o)
	{
		LocalPoint lp = o.getLocalLocation();
		return new Point(lp.getSceneX(), lp.getSceneY());
	}

	@Override
	public String locAt(String name, int id, String option, osrsps.testrunner.Tile at)
	{
		WorldView wv = client.getTopLevelWorldView();
		LocalPoint target = LocalPoint.fromWorld(wv, at.x, at.y);
		if (target == null)
		{
			return "tile " + at.x + ", " + at.y + " is not in the loaded scene";
		}
		String what = (name != null ? "\"" + name + "\"" : "id " + id) + " at " + at.x + ", " + at.y;
		net.runelite.api.Tile tile = wv.getScene().getTiles()[wv.getPlane()][target.getSceneX()][target.getSceneY()];
		if (tile == null)
		{
			return "no loc " + what;
		}
		// A big loc is listed on every tile it covers, so the tile's own lists are enough.
		List<TileObject> objects = new ArrayList<>(Arrays.asList(tile.getGameObjects()));
		objects.add(tile.getWallObject());
		objects.add(tile.getDecorativeObject());
		objects.add(tile.getGroundObject());
		boolean seen = false;
		for (TileObject o : objects)
		{
			if (o != null && locMatches(o, name, id))
			{
				// A hidden loc (no current form) matches by its id but isn't there to see. Examine is the client's own
				// entry, on every loc that is there, not one of its ops.
				boolean anyForm = option == null || isExamine(option);
				if (anyForm ? locComposition(o.getId()) != null : locOpIndex(o.getId(), option) >= 0)
				{
					return null;
				}
				seen = true;
			}
		}
		if (!seen)
		{
			return "no loc " + what;
		}
		return option == null ? "loc " + what + " is hidden" : "loc " + what + " has no option \"" + option + "\"";
	}

	/** True when the loc stands on the target tile; a big loc counts on every tile it covers. */
	private static boolean covers(TileObject o, net.runelite.api.Tile tile, LocalPoint target)
	{
		if (o instanceof GameObject)
		{
			Point min = ((GameObject) o).getSceneMinLocation();
			Point max = ((GameObject) o).getSceneMaxLocation();
			return target.getSceneX() >= min.getX() && target.getSceneX() <= max.getX()
				&& target.getSceneY() >= min.getY() && target.getSceneY() <= max.getY();
		}
		Point t = tile.getSceneLocation();
		return t.getX() == target.getSceneX() && t.getY() == target.getSceneY();
	}

	/**
	 * What the loc is now: a loc that changes with a varbit (a door some quests open) is its impostor. Null when it has
	 * no current form (hidden), so it is never clicked.
	 */
	private ObjectComposition locComposition(int id)
	{
		ObjectComposition c = client.getObjectDefinition(id);
		return c != null && c.getImpostorIds() != null ? c.getImpostor() : c;
	}

	private boolean locMatches(TileObject o, String name, int id)
	{
		ObjectComposition c = locComposition(o.getId());
		if (name != null)
		{
			return c != null && name.equalsIgnoreCase(Dialogue.clean(c.getName()));
		}
		return o.getId() == id || (c != null && c.getId() == id);
	}

	private int locOpIndex(int id, String option)
	{
		ObjectComposition c = locComposition(id);
		return c == null ? -1 : opSlot(c.getOps(), option);
	}

	@Override
	public String takeItem(String name, int id, osrsps.testrunner.Tile at)
	{
		if (client.getLocalPlayer() == null)
		{
			return "no local player";
		}
		GroundItem best = nearestGroundItem(name, id, at);
		if (best == null)
		{
			return groundItemMissing(name, id, at);
		}
		// The menu entry a click on Take makes: the item's scene tile and its id. Take is the third ground option, as
		// the cache's default ground options have it and the server reads it.
		Point p = best.tile.getSceneLocation();
		client.menuAction(p.getX(), p.getY(), MenuAction.GROUND_ITEM_THIRD_OPTION, best.item.getId(), -1, TAKE,
			client.getItemDefinition(best.item.getId()).getName());
		return null;
	}

	@Override
	public String groundItem(String name, int id, osrsps.testrunner.Tile at)
	{
		if (client.getLocalPlayer() == null)
		{
			return "no local player";
		}
		return nearestGroundItem(name, id, at) == null ? groundItemMissing(name, id, at) : null;
	}

	private static String groundItemMissing(String name, int id, osrsps.testrunner.Tile at)
	{
		String what = (name != null ? "\"" + name + "\"" : "id " + id) + (at == null ? "" : " at " + at.x + ", " + at.y);
		return "no item " + what + " on the ground in the scene";
	}

	private static final class GroundItem
	{
		final net.runelite.api.Tile tile;
		final TileItem item;

		GroundItem(net.runelite.api.Tile tile, TileItem item)
		{
			this.tile = tile;
			this.item = item;
		}
	}

	/** The nearest item on the ground on the player's plane with the name (or id), on the tile when given; else null. */
	private GroundItem nearestGroundItem(String name, int id, osrsps.testrunner.Tile at)
	{
		WorldView wv = client.getTopLevelWorldView();
		WorldPoint here = client.getLocalPlayer().getWorldLocation();
		GroundItem best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (net.runelite.api.Tile[] column : wv.getScene().getTiles()[wv.getPlane()])
		{
			for (net.runelite.api.Tile tile : column)
			{
				List<TileItem> items = tile == null ? null : tile.getGroundItems();
				if (items == null)
				{
					continue;
				}
				WorldPoint where = tile.getWorldLocation();
				if (at != null && (where.getX() != at.x || where.getY() != at.y))
				{
					continue;
				}
				for (TileItem item : items)
				{
					int d = where.distanceTo(here);
					if (item != null && itemMatches(item.getId(), name, id) && d < bestDistance)
					{
						best = new GroundItem(tile, item);
						bestDistance = d;
					}
				}
			}
		}
		return best;
	}

	@Override
	public int inventoryCount(String name, int id)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return -1;
		}
		int held = 0;
		for (Item item : inventory.getItems())
		{
			if (item.getId() >= 0 && item.getQuantity() > 0 && itemMatches(item.getId(), name, id))
			{
				held += item.getQuantity();
			}
		}
		return held;
	}

	private boolean itemMatches(int itemId, String name, int id)
	{
		if (name == null)
		{
			return itemId == id;
		}
		ItemComposition c = client.getItemDefinition(itemId);
		return c != null && name.equalsIgnoreCase(Dialogue.clean(c.getName()));
	}

	@Override
	public int npcIndex(String name, int id)
	{
		NPC npc = theNpc(name, id);
		return npc == null ? -1 : npc.getIndex();
	}

	@Override
	public Health npcHealth(String name, int id)
	{
		NPC npc = theNpc(name, id);
		return npc == null ? null : new Health(npc.getHealthRatio(), npc.getHealthScale());
	}

	/**
	 * The NPC with the name (or id when the name is null) that the player is interacting with, else the nearest such
	 * NPC on the player's plane, else null. Several castle NPCs share a name (Man), so the one the player is fighting
	 * comes first.
	 */
	private NPC theNpc(String name, int id)
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return null;
		}
		Actor target = me.getInteracting();
		if (target instanceof NPC && npcMatches((NPC) target, name, id))
		{
			return (NPC) target;
		}
		WorldPoint here = me.getWorldLocation();
		NPC best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (npc == null || npc.getWorldLocation().getPlane() != here.getPlane())
			{
				continue;
			}
			int d = npc.getWorldLocation().distanceTo(here);
			if (npcMatches(npc, name, id) && d < bestDistance)
			{
				best = npc;
				bestDistance = d;
			}
		}
		return best;
	}

	private static boolean npcMatches(NPC npc, String name, int id)
	{
		return name != null ? name.equalsIgnoreCase(Dialogue.clean(npc.getName())) : npc.getId() == id;
	}

	@Override
	public boolean tileLoaded(osrsps.testrunner.Tile at)
	{
		return LocalPoint.fromWorld(client.getTopLevelWorldView(), at.x, at.y) != null;
	}

	@Override
	public boolean interfaceOpen(int interfaceId)
	{
		Widget root = client.getWidget(interfaceId, 0);
		return root != null && !root.isHidden();
	}


	@Override
	public int skillLevel(String skill)
	{
		Skill s = skill(skill);
		return s == null || !loggedIn() ? -1 : client.getRealSkillLevel(s);
	}

	@Override
	public int skillXp(String skill)
	{
		Skill s = skill(skill);
		return s == null || !loggedIn() ? -1 : client.getSkillExperience(s);
	}

	/** The client's skill of that name (as the stats tab shows it), or null. */
	private static Skill skill(String name)
	{
		for (Skill s : Skill.values())
		{
			if (s.getName().equalsIgnoreCase(name))
			{
				return s;
			}
		}
		return null;
	}

	@Override
	public Dialogue dialogue()
	{
		Widget text = visible(InterfaceID.ChatLeft.TEXT);
		if (text != null)
		{
			return new Dialogue(Dialogue.Kind.NPC, widgetText(InterfaceID.ChatLeft.NAME), Dialogue.clean(text.getText()), null);
		}
		text = visible(InterfaceID.ChatRight.TEXT);
		if (text != null)
		{
			return new Dialogue(Dialogue.Kind.PLAYER, widgetText(InterfaceID.ChatRight.NAME), Dialogue.clean(text.getText()), null);
		}
		Widget menu = visible(InterfaceID.Chatmenu.OPTIONS);
		if (menu != null)
		{
			// Child 0 is the "Select an option" title, the options are children 1 up: the slot a click sends back.
			Widget[] children = menu.getDynamicChildren();
			List<Dialogue.Option> options = new ArrayList<>();
			String title = "";
			StringBuilder all = new StringBuilder();
			for (int i = 0; children != null && i < children.length; i++)
			{
				String t = children[i] == null ? "" : Dialogue.clean(children[i].getText());
				if (t.isEmpty())
				{
					continue;
				}
				if (i == 0)
				{
					title = t;
				}
				else
				{
					options.add(new Dialogue.Option(i, t));
				}
				all.append(all.length() == 0 ? "" : " / ").append(t);
			}
			if (children != null && children.length > 0)
			{
				return new Dialogue(Dialogue.Kind.OPTIONS, title, all.toString(), options);
			}
		}
		text = visible(InterfaceID.Messagebox.TEXT);
		if (text != null)
		{
			return new Dialogue(Dialogue.Kind.MESSAGE, "", Dialogue.clean(text.getText()), null);
		}
		text = visible(InterfaceID.Objectbox.TEXT);
		if (text != null)
		{
			return new Dialogue(Dialogue.Kind.OBJECT, "", Dialogue.clean(text.getText()), null);
		}
		return null;
	}

	@Override
	public String continueDialogue()
	{
		int component;
		int sub = -1;
		if (visible(InterfaceID.ChatLeft.TEXT) != null)
		{
			component = InterfaceID.ChatLeft.CONTINUE;
		}
		else if (visible(InterfaceID.ChatRight.TEXT) != null)
		{
			component = InterfaceID.ChatRight.CONTINUE;
		}
		else if (visible(InterfaceID.Messagebox.TEXT) != null)
		{
			component = InterfaceID.Messagebox.CONTINUE;
		}
		else if (visible(InterfaceID.Objectbox.TEXT) != null)
		{
			// Guessed: the objectbox's continue is slot 0 of its universe component (our server's Chatbox.OBJECTBOX notes).
			component = InterfaceID.Objectbox.UNIVERSE;
			sub = 0;
		}
		else
		{
			return "no dialogue with a continue button is open";
		}
		client.menuAction(sub, component, MenuAction.WIDGET_CONTINUE, 0, -1, "Continue", "");
		return null;
	}

	@Override
	public String chooseOption(int slot)
	{
		if (visible(InterfaceID.Chatmenu.OPTIONS) == null)
		{
			return "no option menu is open";
		}
		client.menuAction(slot, InterfaceID.Chatmenu.OPTIONS, MenuAction.WIDGET_CONTINUE, 0, -1, "Continue", "");
		return null;
	}

	private Widget visible(int component)
	{
		Widget w = client.getWidget(component);
		return w != null && !w.isHidden() ? w : null;
	}

	private String widgetText(int component)
	{
		Widget w = visible(component);
		return w == null ? "" : Dialogue.clean(w.getText());
	}

	@Override
	public String itemOption(String name, int id, String option)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return "no inventory yet";
		}
		Item[] items = inventory.getItems();
		int slot = -1;
		for (int i = 0; i < items.length; i++)
		{
			if (items[i].getId() >= 0 && items[i].getQuantity() > 0 && itemMatches(items[i].getId(), name, id))
			{
				slot = i;
				break;
			}
		}
		String what = name != null ? "\"" + name + "\"" : "item " + id;
		if (slot < 0)
		{
			return "no " + what + " in the inventory";
		}
		Widget panel = client.getWidget(InterfaceID.Inventory.ITEMS);
		if (panel == null)
		{
			return "the inventory panel is not open";
		}
		int itemId = items[slot].getId();
		ItemComposition c = client.getItemDefinition(itemId);
		int op = -1;
		if (isExamine(option))
		{
			op = EXAMINE_OP;
		}
		else
		{
			// The slot's own ops, as the client builds its menu from them: action i is op i + 1. Not yet seen in the
			// client: whether the inventory's slots carry the item's ops; if not, the item's iops as our server maps them.
			Widget child = panel.getChild(slot);
			op = actionOp(child == null ? null : child.getActions(), option);
			String[] iops = c.getInventoryActions();
			for (int i = 0; op < 0 && iops != null && i < iops.length && i < INVENTORY_IOP_BUTTON_OPS.length; i++)
			{
				if (iops[i] != null && Dialogue.clean(iops[i]).equalsIgnoreCase(option))
				{
					op = INVENTORY_IOP_BUTTON_OPS[i];
				}
			}
		}
		if (op < 0)
		{
			return what + " has no option \"" + option + "\"";
		}
		client.menuAction(slot, InterfaceID.Inventory.ITEMS, ccOp(op), op, itemId, option, c.getName());
		return null;
	}

	/** Op i + 1 for the action i whose text is the option, or -1. */
	private static int actionOp(String[] actions, String option)
	{
		for (int i = 0; actions != null && i < actions.length; i++)
		{
			if (actions[i] != null && Dialogue.clean(actions[i]).equalsIgnoreCase(option))
			{
				return i + 1;
			}
		}
		return -1;
	}

	/** Ops 6 and up sort under the others in a component's menu, as RuneLite's menu entry swapper has them. */
	private static MenuAction ccOp(int op)
	{
		return op >= 6 ? MenuAction.CC_OP_LOW_PRIORITY : MenuAction.CC_OP;
	}

	@Override
	public int wornId(int wearpos)
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn == null)
		{
			// Seen on the VM (runner 0.6.1): after a login with nothing worn the client has no worn inventory, though
			// the server sends it, empty, at login. The client only makes an inventory once an item is put in it.
			return -1;
		}
		Item item = worn.getItem(wearpos);
		return item == null || item.getId() < 0 || item.getQuantity() <= 0 ? -1 : item.getId();
	}

	@Override
	public int modelWornId(int wearpos)
	{
		if (wearpos < 0 || wearpos >= KitType.values().length)
		{
			return NOT_ON_MODEL;
		}
		Player me = client.getLocalPlayer();
		PlayerComposition c = me == null ? null : me.getPlayerComposition();
		return c == null ? -1 : c.getEquipmentId(KitType.values()[wearpos]);
	}

	@Override
	public String itemName(int id)
	{
		ItemComposition c = id < 0 ? null : client.getItemDefinition(id);
		return c == null ? null : Dialogue.clean(c.getName());
	}

	@Override
	public String wornOption(int wearpos, String option)
	{
		int component = wearpos >= 0 && wearpos < WORN_SLOT_COMPONENTS.length ? WORN_SLOT_COMPONENTS[wearpos] : 0;
		if (component == 0)
		{
			return "the equipment tab shows no slot " + wearpos;
		}
		Widget slot = client.getWidget(component);
		if (slot == null)
		{
			return "the equipment tab is not open";
		}
		int itemId = wornId(wearpos);
		if (itemId < 0)
		{
			return "nothing worn in slot " + wearpos;
		}
		// Op 1 is Remove and 10 Examine; the item's own worn options are ops 2 to 9 (its params 451 to 458), as
		// RuneLite's menu entry swapper and our server have them, and the slot's actions say which is which.
		int op = REMOVE.equalsIgnoreCase(option) ? 1 : isExamine(option) ? EXAMINE_OP : actionOp(slot.getActions(), option);
		if (op < 0)
		{
			return "the item in slot " + wearpos + " has no option \"" + option + "\"";
		}
		client.menuAction(-1, component, ccOp(op), op, itemId, option, client.getItemDefinition(itemId).getName());
		return null;
	}

	@Override
	public String clickWidget(int interfaceId, int component, int sub, int op)
	{
		int packed = interfaceId << 16 | component;
		Widget w = client.getWidget(packed);
		if (w == null)
		{
			return "interface " + interfaceId + ":" + component + " is not open";
		}
		int itemId = w.getItemId();
		if (sub >= 0)
		{
			Widget child = w.getChild(sub);
			if (child == null)
			{
				return "interface " + interfaceId + ":" + component + " has no slot " + sub;
			}
			itemId = child.getItemId();
		}
		String[] actions = (sub >= 0 ? w.getChild(sub) : w).getActions();
		String option = actions != null && op - 1 < actions.length && actions[op - 1] != null ? actions[op - 1] : "";
		client.menuAction(sub, packed, ccOp(op), op, itemId, option, "");
		return null;
	}

	@Override
	public String widgetText(int interfaceId, int component)
	{
		List<String> texts = new ArrayList<>();
		if (component >= 0)
		{
			Widget w = client.getWidget(interfaceId << 16 | component);
			if (w == null)
			{
				return null;
			}
			collectText(w, texts, true);
		}
		else
		{
			// Every component of the interface in turn; their static children are components too, so only the
			// dynamic and nested ones are walked into. Component ids are numbered from 0 without gaps.
			if (client.getWidget(interfaceId << 16) == null)
			{
				return null;
			}
			for (int c = 0; c < 0x10000; c++)
			{
				Widget w = client.getWidget(interfaceId << 16 | c);
				if (w == null)
				{
					break;
				}
				collectText(w, texts, false);
			}
		}
		return String.join(" / ", texts);
	}

	private static void collectText(Widget w, List<String> texts, boolean staticChildren)
	{
		String t = w.getText() == null ? "" : Dialogue.clean(w.getText());
		if (!t.isEmpty())
		{
			texts.add(t);
		}
		List<Widget[]> groups = new ArrayList<>();
		if (staticChildren)
		{
			groups.add(w.getStaticChildren());
		}
		groups.add(w.getDynamicChildren());
		groups.add(w.getNestedChildren());
		for (Widget[] group : groups)
		{
			for (int i = 0; group != null && i < group.length; i++)
			{
				if (group[i] != null)
				{
					collectText(group[i], texts, true);
				}
			}
		}
	}

	@Override
	public int idlePose()
	{
		Player me = client.getLocalPlayer();
		return me == null ? -1 : me.getIdlePoseAnimation();
	}

	@Override
	public String logout()
	{
		if (client.getWidget(InterfaceID.Logout.LOGOUT) == null)
		{
			return "the logout tab is not open";
		}
		client.menuAction(-1, InterfaceID.Logout.LOGOUT, MenuAction.CC_OP, 1, -1, "Logout", "");
		return null;
	}
}
