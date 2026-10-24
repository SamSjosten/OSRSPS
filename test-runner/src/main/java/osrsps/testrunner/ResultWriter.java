package osrsps.testrunner;

import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;

/**
 * Writes results to the results folder: {@code <script>.json}, replaced by every run, and a copy in
 * {@code history/<UTC time>_<script>.json}, one per run. A failure screenshot is {@code <script>.png}; a later run without one
 * deletes it, so a png next to a result always belongs to that result.
 */
final class ResultWriter
{
	private static final DateTimeFormatter FILE_TIME =
		DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss.SSS'Z'").withZone(ZoneOffset.UTC);

	private final Path dir;
	private final Gson gson;
	private final String version;

	ResultWriter(Path dir, Gson gson, String version)
	{
		this.dir = dir;
		this.gson = gson;
		this.version = version;
	}

	/** Writes the screenshot (when there is one) and the result; returns the result file. */
	Path write(Result r, BufferedImage screenshot) throws IOException
	{
		Files.createDirectories(dir.resolve("history"));
		Path png = dir.resolve(r.id + ".png");
		if (screenshot != null)
		{
			Path tmp = dir.resolve(r.id + ".png.tmp");
			ImageIO.write(screenshot, "png", tmp.toFile());
			Files.move(tmp, png, StandardCopyOption.REPLACE_EXISTING);
			r.screenshot = png.getFileName().toString();
		}
		else
		{
			Files.deleteIfExists(png);
		}
		String text = gson.toJson(r.toJson(version)) + "\n";
		// Two runs can still start in the same millisecond (a login script also dropped as a file): never overwrite one.
		String stem = FILE_TIME.format(Instant.ofEpochMilli(r.startedAtMs)) + "_" + r.id;
		Path history = dir.resolve("history").resolve(stem + ".json");
		for (int n = 2; Files.exists(history); n++)
		{
			history = dir.resolve("history").resolve(stem + "_" + n + ".json");
		}
		Files.write(history, text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
		// Written whole and then moved, so a reader polling for the file never sees half of it.
		Path out = dir.resolve(r.id + ".json");
		Path tmp = dir.resolve(r.id + ".json.tmp");
		Files.write(tmp, text.getBytes(StandardCharsets.UTF_8));
		Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
		return out;
	}
}
