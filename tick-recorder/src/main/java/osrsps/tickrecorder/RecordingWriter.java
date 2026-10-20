package osrsps.tickrecorder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Appends JSON lines to one recording file. Lines are queued from the client thread and written
 * on a single background thread, so disk I/O never stalls the game. Failures are reported through
 * {@link #takeFailure()} so the plugin can stop recording and say why.
 */
class RecordingWriter
{
	private static final Logger log = LoggerFactory.getLogger(RecordingWriter.class);

	private final ExecutorService executor = Executors.newSingleThreadExecutor(r ->
	{
		Thread thread = new Thread(r, "tick-recorder-writer");
		thread.setDaemon(true);
		return thread;
	});

	private BufferedWriter out;
	private volatile Path file;
	private volatile String failure;

	/**
	 * Starts a new file {@code <name>.jsonl} in {@code dir}. An existing file is never overwritten:
	 * {@code <name>-1.jsonl}, {@code <name>-2.jsonl} and so on are tried instead.
	 */
	void open(Path dir, String name)
	{
		submit(() ->
		{
			closeQuietly();
			file = null;
			try
			{
				Files.createDirectories(dir);
				for (int attempt = 0; out == null; attempt++)
				{
					Path candidate = dir.resolve(attempt == 0 ? name + ".jsonl" : name + "-" + attempt + ".jsonl");
					try
					{
						out = Files.newBufferedWriter(candidate, StandardCharsets.UTF_8,
							StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
						file = candidate;
					}
					catch (FileAlreadyExistsException e)
					{
						if (attempt >= 99)
						{
							throw e;
						}
					}
				}
			}
			catch (IOException e)
			{
				fail("cannot create a file in " + dir, e);
			}
		});
	}

	void write(String line)
	{
		submit(() ->
		{
			if (out == null)
			{
				return;
			}
			try
			{
				out.write(line);
				out.newLine();
			}
			catch (IOException e)
			{
				fail("cannot write " + file, e);
			}
		});
	}

	/** Pushes buffered lines to disk; called once per tick so a crash loses at most one tick. */
	void flush()
	{
		submit(() ->
		{
			if (out == null)
			{
				return;
			}
			try
			{
				out.flush();
			}
			catch (IOException e)
			{
				fail("cannot write " + file, e);
			}
		});
	}

	/** Closes the current file once every queued line is written. */
	Future<?> close()
	{
		return submit(this::closeQuietly);
	}

	/** The file being written, once {@link #open} has succeeded. */
	Path file()
	{
		return file;
	}

	/** Returns and clears the reason writing stopped, or null when all is well. */
	String takeFailure()
	{
		String reason = failure;
		failure = null;
		return reason;
	}

	/** Closes the file and stops the writer thread, waiting briefly for queued lines. */
	void shutdown()
	{
		close();
		executor.shutdown();
		try
		{
			executor.awaitTermination(2, TimeUnit.SECONDS);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	private Future<?> submit(Runnable task)
	{
		try
		{
			return executor.submit(task);
		}
		catch (RejectedExecutionException e)
		{
			// Shut down while the client thread was still emitting; the recording is already closed.
			return CompletableFuture.completedFuture(null);
		}
	}

	private void fail(String reason, IOException e)
	{
		log.warn("Tick Recorder: {}", reason, e);
		failure = reason;
		closeQuietly();
	}

	private void closeQuietly()
	{
		if (out == null)
		{
			return;
		}
		try
		{
			out.close();
		}
		catch (IOException e)
		{
			log.warn("Unable to close recording", e);
		}
		out = null;
	}
}
