package osrsps.testrunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FilesTest
{
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static void touch(Path p, long ms) throws Exception
	{
		if (!Files.exists(p))
		{
			Files.write(p, "{}".getBytes(StandardCharsets.UTF_8));
		}
		Files.setLastModifiedTime(p, FileTime.fromMillis(ms));
	}

	@Test
	public void folderRunsNewAndChangedFilesOnly() throws Exception
	{
		Path dir = tmp.getRoot().toPath();
		long now = 10_000_000;
		touch(dir.resolve("old.json"), now - 60_000);
		touch(dir.resolve("notes.txt"), now - 60_000);
		ScriptFolder f = new ScriptFolder(dir);
		assertTrue("files there at start don't run", f.scan(now).isEmpty());

		touch(dir.resolve("new.json"), now - 500);
		assertTrue("too fresh: may still be being written", f.scan(now).isEmpty());
		assertEquals(List.of(dir.resolve("new.json")), f.scan(now + 1_000));
		assertTrue(f.scan(now + 2_000).isEmpty());

		touch(dir.resolve("old.json"), now + 1_000);
		assertEquals(List.of(dir.resolve("old.json")), f.scan(now + 5_000));
		assertTrue(f.scan(now + 6_000).isEmpty());
	}

	@Test
	public void writerKeepsAScreenshotOnlyWithItsResult() throws Exception
	{
		Path dir = tmp.getRoot().toPath().resolve("test-results");
		ResultWriter w = new ResultWriter(dir, new Gson(), "0.1.0");
		Result fail = new Result("demo", "Demo", "file", Result.FAIL, "step 1 (x): no", 0, 5, 1, List.of(
			new Result.StepResult(1, "assertNoDialogue", "assert no dialogue", "fail", "no", 5, 1)));
		w.write(fail, new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB));
		assertTrue(Files.exists(dir.resolve("demo.png")));
		JsonObject o = new JsonParser().parse(new String(Files.readAllBytes(dir.resolve("demo.json")), StandardCharsets.UTF_8)).getAsJsonObject();
		assertEquals("fail", o.get("result").getAsString());
		assertEquals("demo.png", o.get("screenshot").getAsString());
		assertEquals("1970-01-01T00:00:00Z", o.get("startedAt").getAsString());
		assertEquals(1, o.getAsJsonArray("steps").size());

		Result pass = new Result("demo", "Demo", "file", Result.PASS, null, 1_000, 5, 1, List.of());
		w.write(pass, null);
		assertFalse("a stale screenshot would look like this run's", Files.exists(dir.resolve("demo.png")));
		o = new JsonParser().parse(new String(Files.readAllBytes(dir.resolve("demo.json")), StandardCharsets.UTF_8)).getAsJsonObject();
		assertEquals("pass", o.get("result").getAsString());
		assertFalse(o.has("screenshot"));
		try (var history = Files.list(dir.resolve("history")))
		{
			assertEquals(2, history.count());
		}
	}

	@Test
	public void cleanReadsWidgetTextAsShown()
	{
		assertEquals("Hello. What are you doing here?", Dialogue.clean("Hello. What are<br>you doing here?"));
		assertEquals("Hans", Dialogue.clean("<col=ffff00>Hans</col>"));
		assertEquals("a b", Dialogue.clean(" a  b "));
	}
}
