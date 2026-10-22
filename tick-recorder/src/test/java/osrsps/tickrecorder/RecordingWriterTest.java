package osrsps.tickrecorder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.FileAlreadyExistsException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

		assertEquals("writeFailed", writer.takeFailure().code);
		assertNull(writer.takeFailure());
		writer.shutdown();
	}

	/** A file whose first write blocks until {@link #open()}, so the queue in front of it fills up. */
	private static class Gate extends Writer
	{
		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch opened = new CountDownLatch(1);
		private final StringWriter content = new StringWriter();

		@Override
		public void write(char[] buffer, int offset, int length) throws IOException
		{
			entered.countDown();
			try
			{
				if (!opened.await(10, TimeUnit.SECONDS))
				{
					throw new IOException("the gate never opened");
				}
			}
			catch (InterruptedException e)
			{
				throw new IOException(e);
			}
			content.write(buffer, offset, length);
		}

		@Override
		public void flush()
		{
		}

		@Override
		public void close()
		{
		}

		void awaitFirstWrite() throws InterruptedException
		{
			assertTrue("the writer thread never reached the file", entered.await(10, TimeUnit.SECONDS));
		}

		void open()
		{
			opened.countDown();
		}

		List<String> lines()
		{
			return Arrays.asList(content.toString().split(System.lineSeparator()));
		}
	}

	@Test
	public void stopsWithAReasonWhenTheDiskFallsBehind() throws Exception
	{
		Gate gate = new Gate();
		// A one-character buffer, so the first line reaches the gate at once and blocks the writer thread there.
		RecordingWriter writer = new RecordingWriter("memory", fileName -> new BufferedWriter(gate, 1));
		writer.open("rec");
		writer.write("first");
		gate.awaitFirstWrite();
		for (int i = 1; i <= RecordingWriter.QUEUE_LIMIT + 1; i++)
		{
			writer.write("line " + i);
		}

		RecordingWriter.Failure failure = writer.takeFailure();
		assertNotNull(failure);
		assertEquals("writerBehind", failure.code);
		assertEquals(RecordingWriter.QUEUE_LIMIT, writer.takeQueueMax());
		writer.writeLast("stop");
		Future<?> closed = writer.close();
		gate.open();
		closed.get();
		writer.shutdown();

		List<String> lines = gate.lines();
		assertEquals(RecordingWriter.QUEUE_LIMIT + 2, lines.size());
		assertEquals("first", lines.get(0));
		assertEquals("line " + RecordingWriter.QUEUE_LIMIT, lines.get(lines.size() - 2));
		assertEquals("stop", lines.get(lines.size() - 1));
		assertEquals(RecordingWriter.QUEUE_LIMIT + 2, writer.lines());
	}

	/** A file whose disk refuses one write: the first chunk holding '!' fails, everything after goes through. */
	private static class Flaky extends Writer
	{
		private final StringWriter content = new StringWriter();
		private final CountDownLatch refused = new CountDownLatch(1);
		private boolean failed;

		@Override
		public void write(char[] buffer, int offset, int length) throws IOException
		{
			if (!failed && new String(buffer, offset, length).indexOf('!') >= 0)
			{
				failed = true;
				refused.countDown();
				throw new IOException("disk full");
			}
			content.write(buffer, offset, length);
		}

		@Override
		public void flush()
		{
		}

		@Override
		public void close()
		{
		}

		List<String> lines()
		{
			String text = content.toString();
			return text.isEmpty() ? Collections.emptyList() : Arrays.asList(text.split("\n"));
		}
	}

	@Test
	public void writesTheStopLineAfterAWriteFailure() throws Exception
	{
		Flaky flaky = new Flaky();
		RecordingWriter writer = new RecordingWriter("the test folder", name -> new BufferedWriter(flaky));
		writer.open("r");
		writer.write("first");
		writer.write("boom!");
		writer.flush();
		assertTrue("the disk never refused the write", flaky.refused.await(10, TimeUnit.SECONDS));
		RecordingWriter.Failure failure = awaitFailure(writer);
		assertEquals("writeFailed", failure.code);
		writer.writeLast("stop");
		writer.close().get();
		writer.shutdown();
		assertEquals(Arrays.asList("first", "boom!", "stop"), flaky.lines());
	}

	/** The failure is recorded on the writer thread just after the disk refuses the write; wait briefly for it. */
	private static RecordingWriter.Failure awaitFailure(RecordingWriter writer)
	{
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		RecordingWriter.Failure failure = writer.takeFailure();
		while (failure == null && System.nanoTime() < deadline)
		{
			Thread.yield();
			failure = writer.takeFailure();
		}
		assertNotNull("the writer never reported the refused write", failure);
		return failure;
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
