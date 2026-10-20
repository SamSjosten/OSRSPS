package osrsps.tickrecorder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Removes player names from free text before it is written. Names in OSRS text can be separated
 * by a normal or a non-breaking space, so both forms are matched.
 */
final class Redactor
{
	static final String PLAYER = "{player}";
	static final String SELF = "{self}";

	private static final Pattern TAGS = Pattern.compile("<[^>]*>");

	private Redactor()
	{
	}

	/** Replaces every occurrence of a name in {@code others} with {player} and {@code self} with {self}. */
	static String scrub(String text, String self, Collection<String> others)
	{
		if (text == null || text.isEmpty())
		{
			return text;
		}
		// Longest names first, so "Bob" never eats the front of "Bob Smith".
		List<String[]> names = new ArrayList<>();
		for (String name : others)
		{
			names.add(new String[]{name, PLAYER});
		}
		names.add(new String[]{self, SELF});
		names.sort(Comparator.comparingInt((String[] n) -> n[0] == null ? 0 : n[0].length()).reversed());
		String result = text;
		for (String[] name : names)
		{
			result = replaceName(result, name[0], name[1]);
		}
		return result;
	}

	/** Drops colour and image tags such as {@code <col=ffffff>}. */
	static String removeTags(String text)
	{
		return text == null ? null : TAGS.matcher(text).replaceAll("");
	}

	private static String replaceName(String text, String name, String replacement)
	{
		if (name == null || name.isEmpty())
		{
			return text;
		}
		String spaced = name.replace(' ', ' ');
		String hard = spaced.replace(' ', ' ');
		return text.replace(spaced, replacement).replace(hard, replacement);
	}
}
