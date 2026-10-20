package osrsps.tickrecorder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RecordingWriterTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void writesOneLinePerCallInOrder() throws Exception
	{
		Path dir = folder.getRoot().toPath().resolve("nested");
		RecordingWriter writer = new RecordingWriter();
		writer.open(dir, "rec");
		writer.write("{\"t\":\"start\"}");
		writer.write("{\"t\":\"tick\",\"tick\":1}");
		writer.shutdown();

		assertEquals(Arrays.asList("{\"t\":\"start\"}", "{\"t\":\"tick\",\"tick\":1}"),
			Files.readAllLines(dir.resolve("rec.jsonl"), StandardCharsets.UTF_8));
		assertNull(writer.takeFailure());
	}

	@Test
	public void neverOverwritesAnEarlierRecording() throws Exception
	{
		Path dir = folder.getRoot().toPath();
		RecordingWriter writer = new RecordingWriter();
		writer.open(dir, "same");
		writer.write("one");
		writer.close();
		writer.write("dropped while closed");
		writer.open(dir, "same");
		writer.write("two");
		writer.shutdown();

		assertEquals(Collections.singletonList("one"), Files.readAllLines(dir.resolve("same.jsonl"), StandardCharsets.UTF_8));
		assertEquals(Collections.singletonList("two"), Files.readAllLines(dir.resolve("same-1.jsonl"), StandardCharsets.UTF_8));
	}

	@Test
	public void reportsAFolderItCannotWriteTo() throws Exception
	{
		Path notADir = folder.newFile("taken").toPath();
		RecordingWriter writer = new RecordingWriter();
		writer.open(notADir, "rec");
		writer.write("lost");
		writer.close().get();

		assertNotNull(writer.takeFailure());
		assertNull(writer.takeFailure());
		writer.shutdown();
	}

	@Test
	public void ignoresWritesAfterShutdown()
	{
		RecordingWriter writer = new RecordingWriter();
		writer.shutdown();
		writer.write("late");
		writer.flush();
	}
}
