package osrsps.testrunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A scripted stand-in for the client: tests set what it shows and read what was clicked. */
class FakeGame implements Game
{
	boolean loggedIn = true;
	String name = "test";
	Tile location = new Tile(3200, 3200, 0);
	/** Where the client draws the player; null for the tile in {@link #location}. */
	Tile drawn;
	Dialogue dialogue;
	final List<String> actions = new ArrayList<>();
	/** When set, npcOption fails with this until cleared. */
	String npcError;
	String locError;
	String takeError;
	/** The locs locAt finds, as "name at x y" or "name with option at x y". */
	final List<String> locs = new ArrayList<>();
	/** What inventoryCount answers, by item name; null for no inventory yet. */
	Map<String, Integer> inventory = new HashMap<>();
	/** How many walk clicks to refuse first, as when the tile is not on screen yet. */
	int walkRefusals;
	/** What chatPending answers. */
	boolean chatPending;

	@Override
	public boolean loggedIn()
	{
		return loggedIn;
	}

	@Override
	public String localName()
	{
		return loggedIn ? name : null;
	}

	@Override
	public Tile location()
	{
		return loggedIn ? location : null;
	}

	@Override
	public Tile drawnLocation()
	{
		return loggedIn ? (drawn != null ? drawn : location) : null;
	}

	@Override
	public String sendChat(String text)
	{
		actions.add("chat " + text);
		return null;
	}

	@Override
	public boolean chatPending()
	{
		return chatPending;
	}

	@Override
	public String walk(int x, int y)
	{
		actions.add("walk " + x + " " + y);
		if (walkRefusals > 0)
		{
			walkRefusals--;
			return "tile " + x + ", " + y + " is not on screen";
		}
		return null;
	}

	@Override
	public String locOption(String name, int id, String option, Tile at)
	{
		if (locError != null)
		{
			return locError;
		}
		actions.add(option + " " + (name != null ? name : "loc " + id) + (at == null ? "" : " at " + at.x + " " + at.y));
		return null;
	}

	@Override
	public String npcOption(String name, int id, String option)
	{
		if (npcError != null)
		{
			return npcError;
		}
		actions.add(option + " " + name);
		return null;
	}

	@Override
	public String locAt(String name, int id, String option, Tile at)
	{
		String what = (name != null ? name : "loc " + id) + (option == null ? "" : " with " + option) + " at " + at.x + " " + at.y;
		return locs.contains(what) ? null : "no " + what;
	}

	@Override
	public String takeItem(String name, int id, Tile at)
	{
		if (takeError != null)
		{
			return takeError;
		}
		actions.add("Take " + (name != null ? name : "item " + id) + (at == null ? "" : " at " + at.x + " " + at.y));
		return null;
	}

	@Override
	public int inventoryCount(String name, int id)
	{
		return inventory == null ? -1 : inventory.getOrDefault(name != null ? name : "item " + id, 0);
	}

	@Override
	public Dialogue dialogue()
	{
		return dialogue;
	}

	@Override
	public String continueDialogue()
	{
		actions.add("continue");
		return null;
	}

	@Override
	public String chooseOption(int slot)
	{
		actions.add("option " + slot);
		return null;
	}
}
