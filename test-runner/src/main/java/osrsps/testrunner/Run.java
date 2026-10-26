package osrsps.testrunner;

import java.util.ArrayList;
import java.util.List;

/**
 * What the steps of one running script share: the game, the chat messages, hitsplats and animations seen, and the
 * game tick count.
 */
final class Run
{
	/** A hitsplat the client showed: on the player ({@link #self}) or on an NPC by name. */
	static final class Hitsplat
	{
		final boolean self;
		/** The NPC's name, null for the player. */
		final String npc;
		final int type;
		final int amount;

		Hitsplat(boolean self, String npc, int type, int amount)
		{
			this.self = self;
			this.npc = npc;
			this.type = type;
			this.amount = amount;
		}

		@Override
		public String toString()
		{
			return amount + " on " + (self ? "you" : npc) + " (type " + type + ")";
		}
	}

	/** An animation an actor started: the player ({@link #self}) or an NPC by name. */
	static final class Animation
	{
		final boolean self;
		final String npc;
		final int id;

		Animation(boolean self, String npc, int id)
		{
			this.self = self;
			this.npc = npc;
			this.id = id;
		}

		@Override
		public String toString()
		{
			return "animation " + id + " on " + (self ? "you" : npc);
		}
	}

	final Game game;
	private final List<String> messages = new ArrayList<>();
	private final List<Hitsplat> hitsplats = new ArrayList<>();
	private final List<Animation> animations = new ArrayList<>();
	/** Index into {@link #messages} of the first message after the last step that acted. */
	private int actionMark;
	private int hitsplatMark;
	private int animationMark;
	private int gameTicks;

	Run(Game game)
	{
		this.game = game;
	}

	/** Called by a step just before it acts, so message asserts only look at what came after. */
	void markAction()
	{
		actionMark = messages.size();
		hitsplatMark = hitsplats.size();
		animationMark = animations.size();
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

	void hitsplat(boolean self, String npc, int type, int amount)
	{
		hitsplats.add(new Hitsplat(self, npc, type, amount));
	}

	/**
	 * The first hitsplat since the last action on the player (npc null) or on an NPC of that name, showing
	 * {@code amount} when that is 0 or more; else null.
	 */
	Hitsplat hitsplatSinceAction(String npc, int amount)
	{
		for (int i = hitsplatMark; i < hitsplats.size(); i++)
		{
			Hitsplat h = hitsplats.get(i);
			boolean who = npc == null ? h.self : !h.self && npc.equalsIgnoreCase(h.npc);
			if (who && (amount < 0 || h.amount == amount))
			{
				return h;
			}
		}
		return null;
	}

	void animation(boolean self, String npc, int id)
	{
		animations.add(new Animation(self, npc, id));
	}

	/** The first animation since the last action with that id on the player (npc null) or on an NPC of that name. */
	Animation animationSinceAction(String npc, int id)
	{
		for (int i = animationMark; i < animations.size(); i++)
		{
			Animation a = animations.get(i);
			boolean who = npc == null ? a.self : !a.self && npc.equalsIgnoreCase(a.npc);
			if (who && a.id == id)
			{
				return a;
			}
		}
		return null;
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
