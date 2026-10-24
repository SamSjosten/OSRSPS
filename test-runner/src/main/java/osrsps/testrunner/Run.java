package osrsps.testrunner;

import java.util.ArrayList;
import java.util.List;

/** What the steps of one running script share: the game, the chat messages seen and the game tick count. */
final class Run
{
	final Game game;
	private final List<String> messages = new ArrayList<>();
	/** Index into {@link #messages} of the first message after the last step that acted. */
	private int actionMark;
	private int gameTicks;

	Run(Game game)
	{
		this.game = game;
	}

	/** Called by a step just before it acts, so message asserts only look at what came after. */
	void markAction()
	{
		actionMark = messages.size();
	}

	/** The first message since the last action containing the text, or null. */
	String messageSinceAction(String contains)
	{
		for (int i = actionMark; i < messages.size(); i++)
		{
			if (messages.get(i).contains(contains))
			{
				return messages.get(i);
			}
		}
		return null;
	}

	void message(String text)
	{
		messages.add(Dialogue.clean(text));
	}

	void gameTick()
	{
		gameTicks++;
	}

	int gameTicks()
	{
		return gameTicks;
	}
}
