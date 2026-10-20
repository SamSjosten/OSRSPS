package osrsps.tickrecorder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Appends JSON lines to one recording file. Lines are queued from the client thread and written
 * on a single background thread, so disk I/O never stalls the game.
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

	void open(Path file)
	{
		executor.execute(() ->
		{
			closeQuietly();
			try
			{
				Files.createDirectories(file.getParent());
				out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
			}
			catch (IOException e)
			{
				log.warn("Unable to open recording {}", file, e);
			}
		});
	}

	void write(String line)
	{
		executor.execute(() ->
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
				log.warn("Unable to write recording, stopping", e);
				closeQuietly();
			}
		});
	}

	/** Pushes buffered lines to disk; called once per tick so a crash loses at most one tick. */
	void flush()
	{
		executor.execute(() ->
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
				log.warn("Unable to flush recording", e);
			}
		});
	}

	void close()
	{
		executor.execute(this::closeQuietly);
	}

	/** Closes the file and stops the writer thread, waiting briefly for queued lines. */
	void shutdown()
	{
		close();
		executor.shutdown();
		try
		{
			executor.awaitTermination(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
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
