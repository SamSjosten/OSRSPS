package osrsps.testrunner;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Watches the tests folder for *.json scripts. A file that is new or changed since the last scan is due to run; files
 * already there when the plugin starts are not. A file changed in the last second is left for the next scan, so a
 * copy still being written isn't read half way.
 */
final class ScriptFolder
{
	static final long SETTLE_MS = 1_000;

	private final Path dir;
	private final Map<Path, Long> seen = new HashMap<>();
	private boolean first = true;

	ScriptFolder(Path dir)
	{
		this.dir = dir;
	}

	Path dir()
	{
		return dir;
	}

	/** Script files new or changed since the last scan, sorted by name. The first scan only takes note. */
	List<Path> scan(long nowMs) throws IOException
	{
		List<Path> due = new ArrayList<>();
		Map<Path, Long> now = new HashMap<>();
		for (Path p : scripts())
		{
			long modified = Files.getLastModifiedTime(p).toMillis();
			Long before = seen.get(p);
			if (before != null && before == modified)
			{
				now.put(p, modified);
				continue;
			}
			if (!first && nowMs - modified < SETTLE_MS)
			{
				// Still settling: keep the old time (or none), so the next scan sees it as changed.
				if (before != null)
				{
					now.put(p, before);
				}
				continue;
			}
			now.put(p, modified);
			if (!first)
			{
				due.add(p);
			}
		}
		seen.clear();
		seen.putAll(now);
		first = false;
		Collections.sort(due);
		return due;
	}

	/** Every script file in the folder, sorted by name. */
	List<Path> scripts() throws IOException
	{
		List<Path> out = new ArrayList<>();
		if (!Files.isDirectory(dir))
		{
			return out;
		}
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json"))
		{
			for (Path p : files)
			{
				if (Files.isRegularFile(p))
				{
					out.add(p);
				}
			}
		}
		Collections.sort(out);
		return out;
	}
}
