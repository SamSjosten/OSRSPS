package osrsps.testrunner;

import java.util.ArrayList;
import java.util.List;

/**
 * What the steps of one running script share: the game, the chat messages, hitsplats, animations and spotanims seen,
 * and the game tick count.
 */
final class Run
{
	/** The actor index that stands for the player in {@link #hitsplat} and {@link #animation}. */
	static final int SELF = -1;

	/** A hitsplat the client showed: on the player ({@link #index} {@link #SELF}) or on an NPC by index. */
	static final class Hitsplat
	{
		final int index;
		/** The NPC's name, null for the player. */
		final String npc;
		final int type;
		final int amount;

		Hitsplat(int index, String npc, int type, int amount)
		{
			this.index = index;
			this.npc = npc;
			this.type = type;
			this.amount = amount;
		}

		@Override
		public String toString()
		{
			return amount + " on " + (index == SELF ? "you" : npc + " #" + index) + " (type " + type + ")";
		}
	}

	/** An animation an actor started: the player ({@link #index} {@link #SELF}) or an NPC by index. */
	static final class Animation
	{
		final int index;
		final String npc;
		final int id;

		Animation(int index, String npc, int id)
		{
			this.index = index;
			this.npc = npc;
			this.id = id;
		}

		@Override
		public String toString()
		{
			return "animation " + id + " on " + (index == SELF ? "you" : npc + " #" + index);
		}
	}

	/** A spotanim (graphic) an actor was given: the player ({@link #index} {@link #SELF}) or an NPC by index. */
	static final class Spotanim
	{
		final int index;
		final String npc;
		final int id;

		Spotanim(int index, String npc, int id)
		{
			this.index = index;
			this.npc = npc;
			this.id = id;
		}

		@Override
		public String toString()
		{
			return "spotanim " + id + " on " + (index == SELF ? "you" : npc + " #" + index);
		}
	}

	final Game game;
	private final List<String> messages = new ArrayList<>();
	private final List<Hitsplat> hitsplats = new ArrayList<>();
	private final List<Animation> animations = new ArrayList<>();
	private final List<Spotanim> spotanims = new ArrayList<>();
	/** Index into {@link #messages} of the first message after the last step that acted. */
	private int actionMark;
	private int hitsplatMark;
	private int animationMark;
	private int spotanimMark;
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
		spotanimMark = spotanims.size();
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

	/** A hitsplat on the player ({@code index} {@link #SELF}) or on the NPC with that index and name. */
	void hitsplat(int index, String npc, int type, int amount)
	{
		hitsplats.add(new Hitsplat(index, npc, type, amount));
	}

	/**
	 * The first hitsplat since the last action on the actor with that index ({@link #SELF} for the player), showing
	 * {@code amount} when that is 0 or more; else null.
	 */
	Hitsplat hitsplatSinceAction(int index, int amount)
	{
		for (int i = hitsplatMark; i < hitsplats.size(); i++)
		{
			Hitsplat h = hitsplats.get(i);
			if (h.index == index && (amount < 0 || h.amount == amount))
			{
				return h;
			}
		}
		return null;
	}

	/** An animation the player ({@code index} {@link #SELF}) or the NPC with that index and name started. */
	void animation(int index, String npc, int id)
	{
		animations.add(new Animation(index, npc, id));
	}

	/** The first animation since the last action with that id on the actor with that index ({@link #SELF} for the player). */
	Animation animationSinceAction(int index, int id)
	{
		for (int i = animationMark; i < animations.size(); i++)
		{
			Animation a = animations.get(i);
			if (a.index == index && a.id == id)
			{
				return a;
			}
		}
		return null;
	}

	/** A spotanim the player ({@code index} {@link #SELF}) or the NPC with that index and name was given. */
	void spotanim(int index, String npc, int id)
	{
		spotanims.add(new Spotanim(index, npc, id));
	}

	/** The first spotanim since the last action with that id on the actor with that index ({@link #SELF} for the player). */
	Spotanim spotanimSinceAction(int index, int id)
	{
		for (int i = spotanimMark; i < spotanims.size(); i++)
		{
			Spotanim a = spotanims.get(i);
			if (a.index == index && a.id == id)
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
