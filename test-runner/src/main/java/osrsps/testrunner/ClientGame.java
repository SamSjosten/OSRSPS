package osrsps.testrunner;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;

/**
 * {@link Game} over RuneLite's client API. Every action is the menu entry a click would have made, sent with
 * {@link Client#menuAction}, so the client sends the same packet a player's click sends. Chat is the one place keys are
 * used: the text goes in the chat input and an Enter key event goes to the canvas.
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

	private final Client client;
	/** The line {@link #sendChat} last put in the chat input, until the client has sent it. */
	private String chatLine;
	/** Set on the Swing thread once the Enter key events for {@link #chatLine} have gone to the canvas. */
	private volatile boolean enterDelivered;

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
		enterDelivered = false;
		SwingUtilities.invokeLater(() ->
		{
			long now = System.currentTimeMillis();
			canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_PRESSED, now, 0, KeyEvent.VK_ENTER, '\n'));
			canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_TYPED, now, 0, KeyEvent.VK_UNDEFINED, '\n'));
			canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_RELEASED, now, 0, KeyEvent.VK_ENTER, '\n'));
			enterDelivered = true;
		});
		return null;
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
			return "tile " + x + ", " + y + " is not on screen; walk somewhere closer first";
		}
		// Walk here points at the tile under a spot in the 3D view, as a click does; the client then picks the tile there
		// while drawing the next frame. Guessed from the client's own Walk here entries: the spot is relative to the
		// viewport, not the canvas (a few pixels' difference in fixed mode, none in resizable).
		client.menuAction(p.getX() - vx, p.getY() - vy, MenuAction.WALK, 0, -1, "Walk here", "");
		return null;
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
			if (d < bestDistance && opIndex(npc, option) >= 0)
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
		int op = opIndex(best, option);
		client.menuAction(0, 0, NPC_OPS[op], best.getIndex(), -1, composition(best).getActions()[op], best.getName());
		return null;
	}

	private static NPCComposition composition(NPC npc)
	{
		NPCComposition c = npc.getTransformedComposition();
		return c != null ? c : npc.getComposition();
	}

	private static int opIndex(NPC npc, String option)
	{
		NPCComposition c = composition(npc);
		String[] actions = c == null ? null : c.getActions();
		if (actions == null)
		{
			return -1;
		}
		for (int i = 0; i < actions.length && i < NPC_OPS.length; i++)
		{
			if (actions[i] != null && Dialogue.clean(actions[i]).equalsIgnoreCase(option))
			{
				return i;
			}
		}
		return -1;
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
}
