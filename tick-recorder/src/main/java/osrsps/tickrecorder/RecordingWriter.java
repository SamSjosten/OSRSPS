package osrsps.tickrecorder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Appends JSON lines to one recording file. Lines are queued from the client thread and written
 * on a single background thread, so disk I/O never stalls the game. The queue is bounded: when the
 * disk falls too far behind, the recording stops with a reason instead of leaving a silent hole.
 * Failures are reported through {@link #takeFailure()} so the plugin can stop recording and say why.
 */
class RecordingWriter
{
	/** Creates a file by name, throwing {@link FileAlreadyExistsException} when the name is taken. */
	interface Opener
	{
		BufferedWriter open(String fileName) throws IOException;
	}

	/** Why writing stopped: a short code for the stop line and a sentence for the chatbox. */
	static final class Failure
	{
		final String code;
		final String detail;

		Failure(String code, String detail)
		{
			this.code = code;
			this.detail = detail;
		}
	}

	/** Lines that may wait for the disk; past this the recording stops rather than pile up without end. */
	static final int QUEUE_LIMIT = 20000;
	/** Room past the limit for the stop line and the close that end a recording. */
	private static final int RESERVED = 2;

	private static final Logger log = LoggerFactory.getLogger(RecordingWriter.class);

	private final String where;
	private final Opener opener;
	private final ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(QUEUE_LIMIT + RESERVED);
	private final ExecutorService executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue, r ->
	{
		Thread thread = new Thread(r, "tick-recorder-writer");
		thread.setDaemon(true);
		return thread;
	});

	private BufferedWriter out;
	private volatile String file;
	private volatile Failure failure;
	private volatile long lines;
	private volatile long chars;
	private volatile int queueMax;

	/** Writes into {@code dir}, the plugin's own folder from RuneLite, creating it when needed. */
	RecordingWriter(Filepath dir)
	{
		this(dir.toString(), fileName ->
		{
			dir.createDirectories();
			return dir.join(fileName).openBufferedWriter(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		});
	}

	/** {@code where} names the place files go, for messages; {@code opener} creates them there. */
	RecordingWriter(String where, Opener opener)
	{
		this.where = where;
		this.opener = opener;
	}

	/**
	 * Starts a new file {@code <name>.jsonl}. An existing file is never overwritten:
	 * {@code <name>-1.jsonl}, {@code <name>-2.jsonl} and so on are tried instead.
	 */
	void open(String name)
	{
		failure = null;
		submit(() ->
		{
			closeQuietly();
			// Cleared again here, behind any failure the previous file's last lines may still raise.
			failure = null;
			file = null;
			lines = 0;
			chars = 0;
			try
			{
				for (int attempt = 0; out == null; attempt++)
				{
					String candidate = attempt == 0 ? name + ".jsonl" : name + "-" + attempt + ".jsonl";
					try
					{
						out = opener.open(candidate);
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
				fail("cannot create a file in " + where, e);
			}
		});
	}

	void write(String line)
	{
		if (accept())
		{
			submit(() -> append(line));
		}
	}

	/** Queues a line even past the limit: the stop line that says why a recording ended is always written. */
	void writeLast(String line)
	{
		submit(() -> append(line));
	}

	/** Pushes buffered lines to disk; called once per tick so a crash loses at most one tick. */
	void flush()
	{
		if (!accept())
		{
			return;
		}
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
				fail("cannot write " + file + " in " + where, e);
			}
		});
	}

	/** Closes the current file once every queued line is written. */
	Future<?> close()
	{
		return submit(this::closeQuietly);
	}

	/** Returns and clears the reason writing stopped, or null when all is well. */
	Failure takeFailure()
	{
		Failure reason = failure;
		failure = null;
		return reason;
	}

	/** Lines written to the file so far. */
	long lines()
	{
		return lines;
	}

	/** Characters written so far, newlines included. */
	long chars()
	{
		return chars;
	}

	/** The deepest the queue has been since this was last called. */
	int takeQueueMax()
	{
		int max = queueMax;
		queueMax = 0;
		return max;
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
			// The caller is being torn down; the daemon writer thread finishes the close on its own.
		}
	}

	/** Whether an ordinary line may be queued now. Notices the disk falling behind and records the backlog. */
	private boolean accept()
	{
		if (failure != null)
		{
			return false;
		}
		int depth = queue.size();
		if (depth > queueMax)
		{
			queueMax = depth;
		}
		if (depth >= QUEUE_LIMIT)
		{
			failure = new Failure("writerBehind", "the disk fell " + QUEUE_LIMIT + " lines behind");
			return false;
		}
		return true;
	}

	private void append(String line)
	{
		if (out == null)
		{
			return;
		}
		try
		{
			out.write(line);
			out.newLine();
			lines++;
			chars += line.length() + 1;
		}
		catch (IOException e)
		{
			fail("cannot write " + file + " in " + where, e);
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
			// Shut down while the client thread was still emitting, or the reserved room is already taken.
			return CompletableFuture.completedFuture(null);
		}
	}

	private void fail(String reason, IOException e)
	{
		log.warn("Tick Recorder: {}", reason, e);
		failure = new Failure("writeFailed", reason);
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
