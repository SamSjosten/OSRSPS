package osrsps.tickrecorder;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
		Path file = folder.getRoot().toPath().resolve("nested/rec.jsonl");
		RecordingWriter writer = new RecordingWriter();
		writer.open(file);
		writer.write("{\"t\":\"start\"}");
		writer.write("{\"t\":\"tick\",\"tick\":1}");
		writer.shutdown();

		assertEquals(Arrays.asList("{\"t\":\"start\"}", "{\"t\":\"tick\",\"tick\":1}"),
			Files.readAllLines(file, StandardCharsets.UTF_8));
	}

	@Test
	public void startsAFreshFileOnReopen() throws Exception
	{
		Path first = folder.getRoot().toPath().resolve("a.jsonl");
		Path second = folder.getRoot().toPath().resolve("b.jsonl");
		RecordingWriter writer = new RecordingWriter();
		writer.open(first);
		writer.write("one");
		writer.close();
		writer.write("dropped while closed");
		writer.open(second);
		writer.write("two");
		writer.shutdown();

		assertEquals(Arrays.asList("one"), Files.readAllLines(first, StandardCharsets.UTF_8));
		assertEquals(Arrays.asList("two"), Files.readAllLines(second, StandardCharsets.UTF_8));
	}
}
