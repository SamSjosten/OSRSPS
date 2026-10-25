package osrsps.tickrecorder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Appends JSON lines to one recording file. Lines are queued from the client thread and written
 * on a single background thread, so disk I/O never stalls the game. The lines waiting for the disk
 * are counted: when the disk falls too far behind, the recording stops with a reason instead of
 * piling lines up without end or leaving a silent hole. Each file keeps its own failure and counts,
 * so what one recording ran into is never reported for the next, however close together they are.
 * Failures are reported through {@link #failure()} so the plugin can stop recording and say why.
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

	/** One file's bookkeeping. The writer thread's tasks keep the one they were queued for. */
	private static final class Recording
	{
		/** The first failure this file ran into, set from either thread. */
		final AtomicReference<Failure> failure = new AtomicReference<>();
		volatile long lines;
		volatile long chars;
		/** Most lines waiting at once since last asked; client thread only. */
		int queueMax;
	}

	/** Lines that may wait for the disk; past this the recording stops rather than pile up without end. */
	static final int QUEUE_LIMIT = 20000;

	private static final Logger log = LoggerFactory.getLogger(RecordingWriter.class);

	private final String where;
	private final Opener opener;
	/**
	 * Unbounded, because only lines are limited, by {@link #queued}: the flushes, stops, opens and closes
	 * that share it come at most a few per tick and stop with the recording.
	 */
	private final ExecutorService executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), r ->
	{
		Thread thread = new Thread(r, "tick-recorder-writer");
		thread.setDaemon(true);
		return thread;
	});
	/** Lines queued and not yet started, whichever file they belong to. */
	private final AtomicInteger queued = new AtomicInteger();

	private BufferedWriter out;
	private volatile String file;
	/** The file lines go to now; set by {@link #open(String)} on the client thread. */
	private volatile Recording current = new Recording();
	/** What newLine() writes: two characters on Windows. */
	private static final int NEWLINE_CHARS = System.lineSeparator().length();

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
	 * The file starts with no failure and no counts of its own, whatever the last file left behind.
	 */
	void open(String name)
	{
		Recording recording = new Recording();
		current = recording;
		submit(() ->
		{
			closeQuietly();
			file = null;
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
				fail(recording, "cannot create a file in " + where, e);
			}
		});
	}

	void write(String line)
	{
		Recording recording = current;
		if (!accept(recording))
		{
			return;
		}
		queued.incrementAndGet();
		try
		{
			executor.submit(() ->
			{
				queued.decrementAndGet();
				append(recording, line);
			});
		}
		catch (RejectedExecutionException e)
		{
			// Shut down while the client thread was still emitting.
			queued.decrementAndGet();
		}
	}

	/**
	 * Queues the stop line behind every pending write, outside the line limit so a file always ends with
	 * its reason. The reason is decided on the writer thread once those writes are done, so a failure one
	 * of them raises wins over the reason given. The future settles with this file's failure once the stop
	 * line is written, null for a clean stop, and belongs to this file alone: it is unaffected by whatever
	 * the next file runs into.
	 */
	CompletableFuture<Failure> writeStop(String reason, Function<String, String> lineFor)
	{
		Recording recording = current;
		try
		{
			return CompletableFuture.supplyAsync(() ->
			{
				Failure found = recording.failure.get();
				append(recording, lineFor.apply(found == null ? reason : found.code));
				return recording.failure.get();
			}, executor);
		}
		catch (RejectedExecutionException e)
		{
			return CompletableFuture.completedFuture(recording.failure.get());
		}
	}

	/** Pushes buffered lines to disk; called once per tick so a crash loses at most one tick. Not a line: never counted against the limit. */
	void flush()
	{
		Recording recording = current;
		if (recording.failure.get() != null)
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
				fail(recording, "cannot write " + file + " in " + where, e);
			}
		});
	}

	/** Closes the current file once every queued line is written. */
	Future<?> close()
	{
		return submit(this::closeQuietly);
	}

	/** Why writing to the current file stopped, or null when all is well. */
	Failure failure()
	{
		return current.failure.get();
	}

	/** Lines written to the current file so far. */
	long lines()
	{
		return current.lines;
	}

	/** Characters written to the current file so far, newlines included. */
	long chars()
	{
		return current.chars;
	}

	/** The most lines that were waiting for the disk at once since this was last called. */
	int takeQueueMax()
	{
		Recording recording = current;
		int max = recording.queueMax;
		recording.queueMax = 0;
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
	private boolean accept(Recording recording)
	{
		if (recording.failure.get() != null)
		{
			return false;
		}
		int depth = queued.get();
		if (depth > recording.queueMax)
		{
			recording.queueMax = depth;
		}
		if (depth >= QUEUE_LIMIT)
		{
			recording.failure.compareAndSet(null, new Failure("writerBehind", "the disk fell " + QUEUE_LIMIT + " lines behind"));
			return false;
		}
		return true;
	}

	private void append(Recording recording, String line)
	{
		if (out == null)
		{
			return;
		}
		try
		{
			out.write(line);
			out.newLine();
			recording.lines++;
			recording.chars += line.length() + NEWLINE_CHARS;
		}
		catch (IOException e)
		{
			fail(recording, "cannot write " + file + " in " + where, e);
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
			// Shut down while the client thread was still emitting.
			return CompletableFuture.completedFuture(null);
		}
	}

	private void fail(Recording recording, String reason, IOException e)
	{
		if (!recording.failure.compareAndSet(null, new Failure("writeFailed", reason)))
		{
			return;
		}
		log.warn("Tick Recorder: {}", reason, e);
		// The file stays open: the plugin still queues its stop line, a best effort, and then closes.
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
