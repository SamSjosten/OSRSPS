package osrsps.testrunner;

import java.util.ArrayList;
import java.util.List;

/** A scripted stand-in for the client: tests set what it shows and read what was clicked. */
class FakeGame implements Game
{
	boolean loggedIn = true;
	String name = "test";
	Tile location = new Tile(3200, 3200, 0);
	Dialogue dialogue;
	final List<String> actions = new ArrayList<>();
	/** When set, npcOption fails with this until cleared. */
	String npcError;
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
