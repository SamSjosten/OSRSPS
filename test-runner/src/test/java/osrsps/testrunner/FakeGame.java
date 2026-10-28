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
	/** What walkDestination answers. */
	Tile destination;
	/** What chatPending answers. */
	boolean chatPending;
	/** The index npcIndex answers by NPC name (or "npc <id>"); a missing name is not in view. */
	final Map<String, Integer> npcIndexes = new HashMap<>();
	/** What npcHealth answers for an NPC in view; null for no bar yet. */
	Health npcHealth;
	/** What tileLoaded answers. */
	boolean tileLoaded = true;
	/** What skillLevel and skillXp answer, by skill name; a missing skill is -1. */
	final Map<String, Integer> levels = new HashMap<>();
	final Map<String, Integer> xps = new HashMap<>();
	/** The items groundItem finds, as "name" or "name at x y". */
	final List<String> groundItems = new ArrayList<>();
	/** The interfaces interfaceOpen says are open. */
	final List<Integer> openInterfaces = new ArrayList<>();
	/** When set, itemOption, wornOption and clickWidget fail with this until cleared. */
	String clickError;
	/** What wornId answers by wearpos; a missing slot is empty. */
	final Map<Integer, Integer> worn = new HashMap<>();
	/** What modelWornId answers by wearpos; a missing slot shows the same as {@link #worn}. */
	final Map<Integer, Integer> model = new HashMap<>();
	/** Item names by id, for itemName. */
	final Map<Integer, String> itemNames = new HashMap<>();
	/** What widgetText answers, by "interface:component" or "interface"; a missing key is an interface not open. */
	final Map<String, String> widgetTexts = new HashMap<>();
	/** What idlePose answers. */
	int idlePose = 808;
	/** Whether logout logs out at once. */
	boolean logoutWorks = true;

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
	public String pressEnterAgain()
	{
		actions.add("enter again");
		return "fake focus";
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
	public Tile walkDestination()
	{
		return destination;
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

	@Override
	public int npcIndex(String name, int id)
	{
		return npcIndexes.getOrDefault(name != null ? name : "npc " + id, -1);
	}

	@Override
	public Health npcHealth(String name, int id)
	{
		return npcIndex(name, id) < 0 ? null : npcHealth;
	}

	@Override
	public boolean tileLoaded(Tile at)
	{
		return tileLoaded;
	}

	@Override
	public int skillLevel(String skill)
	{
		return levels.getOrDefault(skill, -1);
	}

	@Override
	public int skillXp(String skill)
	{
		return xps.getOrDefault(skill, -1);
	}

	@Override
	public boolean interfaceOpen(int interfaceId)
	{
		return openInterfaces.contains(interfaceId);
	}


	@Override
	public String groundItem(String name, int id, Tile at)
	{
		String what = (name != null ? name : "item " + id) + (at == null ? "" : " at " + at.x + " " + at.y);
		return groundItems.contains(what) ? null : "no " + what + " on the ground";
	}

	@Override
	public String itemOption(String name, int id, String option)
	{
		if (clickError != null)
		{
			return clickError;
		}
		actions.add(option + " " + (name != null ? name : "item " + id) + " in the inventory");
		return null;
	}

	@Override
	public int wornId(int wearpos)
	{
		return worn.getOrDefault(wearpos, -1);
	}

	@Override
	public int modelWornId(int wearpos)
	{
		if (wearpos >= 12)
		{
			return NOT_ON_MODEL;
		}
		return model.containsKey(wearpos) ? model.get(wearpos) : Math.max(wornId(wearpos), -1);
	}

	@Override
	public String itemName(int id)
	{
		return itemNames.get(id);
	}

	@Override
	public String wornOption(int wearpos, String option)
	{
		if (clickError != null)
		{
			return clickError;
		}
		actions.add(option + " slot " + wearpos);
		return null;
	}

	@Override
	public String clickWidget(int interfaceId, int component, int sub, int op)
	{
		if (clickError != null)
		{
			return clickError;
		}
		actions.add("click " + interfaceId + ":" + component + (sub < 0 ? "" : " slot " + sub) + " op " + op);
		return null;
	}

	@Override
	public String widgetText(int interfaceId, int component)
	{
		return widgetTexts.get(component < 0 ? String.valueOf(interfaceId) : interfaceId + ":" + component);
	}

	@Override
	public int idlePose()
	{
		return loggedIn ? idlePose : -1;
	}

	@Override
	public String logout()
	{
		actions.add("logout");
		if (logoutWorks)
		{
			loggedIn = false;
		}
		return null;
	}
}
