package osrsps.tickrecorder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.FileAlreadyExistsException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class RecordingWriterTest
{
	/** Stands in for the recordings folder: a file is a StringWriter by name, and a name is taken once used. */
	private static class Folder implements RecordingWriter.Opener
	{
		private final Map<String, StringWriter> files = new HashMap<>();

		@Override
		public BufferedWriter open(String fileName) throws IOException
		{
			if (files.containsKey(fileName))
			{
				throw new FileAlreadyExistsException(fileName);
			}
			StringWriter content = new StringWriter();
			files.put(fileName, content);
			return new BufferedWriter(content);
		}

		List<String> lines(String fileName)
		{
			StringWriter content = files.get(fileName);
			assertNotNull(fileName + " was never created", content);
			return Arrays.asList(content.toString().split(System.lineSeparator()));
		}
	}

	@Test
	public void writesOneLinePerCallInOrder() throws Exception
	{
		Folder folder = new Folder();
		RecordingWriter writer = new RecordingWriter("memory", folder);
		writer.open("rec");
		writer.write("{\"t\":\"start\"}");
		writer.write("{\"t\":\"tick\",\"tick\":1}");
		writer.shutdown();

		assertEquals(Arrays.asList("{\"t\":\"start\"}", "{\"t\":\"tick\",\"tick\":1}"), folder.lines("rec.jsonl"));
		assertNull(writer.takeFailure());
	}

	@Test
	public void neverOverwritesAnEarlierRecording() throws Exception
	{
		Folder folder = new Folder();
		RecordingWriter writer = new RecordingWriter("memory", folder);
		writer.open("same");
		writer.write("one");
		writer.close();
		writer.write("dropped while closed");
		writer.open("same");
		writer.write("two");
		writer.shutdown();

		assertEquals(Collections.singletonList("one"), folder.lines("same.jsonl"));
		assertEquals(Collections.singletonList("two"), folder.lines("same-1.jsonl"));
	}

	@Test
	public void givesUpWhenEveryNameIsTaken() throws Exception
	{
		RecordingWriter writer = new RecordingWriter("memory", fileName ->
		{
			throw new FileAlreadyExistsException(fileName);
		});
		writer.open("rec");
		writer.write("lost");
		writer.close().get();

		assertNotNull(writer.takeFailure());
		writer.shutdown();
	}

	@Test
	public void reportsAFolderItCannotWriteTo() throws Exception
	{
		RecordingWriter writer = new RecordingWriter("memory", fileName ->
		{
			throw new IOException("read only");
		});
		writer.open("rec");
		writer.write("lost");
		writer.close().get();

		assertNotNull(writer.takeFailure());
		assertNull(writer.takeFailure());
		writer.shutdown();
	}

	@Test
	public void ignoresWritesAfterShutdown()
	{
		RecordingWriter writer = new RecordingWriter("memory", new Folder());
		writer.shutdown();
		writer.write("late");
		writer.flush();
	}
}
