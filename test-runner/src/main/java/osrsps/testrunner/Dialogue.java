package osrsps.testrunner;

import java.util.Collections;
import java.util.List;

/** A dialogue open in the chatbox, its text already stripped of colour tags and line breaks. */
final class Dialogue
{
	enum Kind
	{
		/** chat_left: an NPC talking. */
		NPC,
		/** chat_right: the player talking. */
		PLAYER,
		/** chatmenu: "Select an option". */
		OPTIONS,
		/** messagebox: text with no head. */
		MESSAGE,
		/** objectbox: an item and text. */
		OBJECT,
		/** levelup: the level-up box, its title as the name; only from {@link Game#levelUp}. */
		LEVEL_UP
	}

	static final class Option
	{
		/** What the client sends back when this option is clicked: its child index in the menu. */
		final int slot;
		final String text;

		Option(int slot, String text)
		{
			this.slot = slot;
			this.text = text;
		}
	}

	final Kind kind;
	/** Who is talking, empty when the dialogue has no name. */
	final String speaker;
	/** The text, or for an option menu its title and options joined with " / ". */
	final String text;
	final List<Option> options;

	Dialogue(Kind kind, String speaker, String text, List<Option> options)
	{
		this.kind = kind;
		this.speaker = speaker == null ? "" : speaker;
		this.text = text == null ? "" : text;
		this.options = options == null ? Collections.emptyList() : options;
	}

	@Override
	public String toString()
	{
		return kind.name().toLowerCase() + (speaker.isEmpty() ? "" : " " + speaker) + ": \"" + text + "\"";
	}

	/**
	 * Widget text as a player reads it: tags such as {@code <col=ff0000>} removed, {@code <br>} and runs of spaces
	 * (non-breaking ones too) made one space.
	 */
	static String clean(String widgetText)
	{
		if (widgetText == null)
		{
			return "";
		}
		String s = widgetText.replaceAll("(?i)<br>", " ").replaceAll("<[^>]*>", "");
		return s.replace(' ', ' ').replaceAll("\\s+", " ").trim();
	}
}
