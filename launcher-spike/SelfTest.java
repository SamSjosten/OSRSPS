/*
 * Checks SpikeLoader on stand-in jars, with no RuneLite and no network: javac builds a fake client jar (RuneLite,
 * ClientLoader with a .jagex.com check and an updateVanilla that throws, ...) and a fake injected client (a modulus, the
 * exponent, a "127.0.0.1" host check and a "127.0.0.1" that isn't one). SpikeLoader is compiled and run as on the test VM,
 * in a JVM of its own, and the fake RuneLite prints what it sees.
 *
 *   java SelfTest.java <folder with SpikeLoader.java> <ASM class path>
 *
 * Exits 0 when every check passes.
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class SelfTest
{
	static int failures;

	public static void main(String[] args) throws Exception
	{
		Path work = Files.createTempDirectory("spike-selftest");
		Path spike = work.resolve("spike-classes");
		Files.createDirectories(spike);
		if (ToolProvider.getSystemJavaCompiler().run(null, null, null, "-cp", args[1], "-d", spike.toString(), "-Xlint:all", "-Werror",
			Path.of(args[0]).toAbsolutePath().resolve("SpikeLoader.java").toString()) != 0)
		{
			throw new IllegalStateException("javac failed for SpikeLoader.java");
		}
		String asm = args[1];
		Random random = new Random(241);
		String theirs = new BigInteger(1024, random).setBit(1023).toString(16);
		String ours = new BigInteger(1024, random).setBit(1023).toString(16);
		String tooLong = new BigInteger(1040, random).setBit(1039).toString(16);

		Path client = jar(work, "client-0.0-selftest.jar", Map.of(
			"net/runelite/client/RuneLite.java", """
				package net.runelite.client;

				import java.io.File;
				import java.io.InputStream;
				import java.net.Socket;
				import java.net.URI;
				import java.util.Arrays;
				import java.util.function.Supplier;
				import javax.swing.SwingUtilities;

				public class RuneLite
				{
					public static final File RUNELITE_DIR = new File(System.getProperty("user.home"), ".runelite");

					public static void main(String[] args) throws Exception
					{
						System.out.println("FAKE dir=" + RUNELITE_DIR.getName());
						System.out.println("FAKE args=" + String.join(" ", args));
						System.out.println("FAKE thread=" + Thread.currentThread().getName());
						System.out.println("FAKE tccl=" + (Thread.currentThread().getContextClassLoader() == RuneLite.class.getClassLoader()));
						SwingUtilities.invokeAndWait(() -> System.out.println("FAKE edtTccl="
							+ (Thread.currentThread().getContextClassLoader() == RuneLite.class.getClassLoader())));
						System.out.println("FAKE loader=" + RuneLite.class.getClassLoader().getName());
						System.out.println("FAKE spikeVisible=" + visible("SpikeLoader"));
						System.out.println("FAKE developerMode=" + "developer-mode".length());
						net.runelite.client.rs.ClientLoader.check();
						URI api = URI.create(System.getProperty("runelite.http-service.url"));
						try (InputStream in = api.resolve("worlds.js").toURL().openStream())
						{
							System.out.println("FAKE worlds=" + new String(in.readAllBytes(), "UTF-8"));
						}
						// The client's game connection is a plain socket.
						try (Socket socket = new Socket(api.getHost(), api.getPort()))
						{
							System.out.println("FAKE socket=" + socket.isConnected());
						}
						Object client = RuneLite.class.getClassLoader().loadClass("client").getDeclaredConstructor().newInstance();
						System.out.println("FAKE client=" + ((Supplier<?>) client).get());
						// RuneLite ends with System.exit; with --return main just returns and the JVM must end by itself.
						if (!Arrays.asList(args).contains("--return"))
						{
							System.exit(0);
						}
					}

					static boolean visible(String name)
					{
						try
						{
							Class.forName(name, false, RuneLite.class.getClassLoader());
							return true;
						}
						catch (ClassNotFoundException e)
						{
							return false;
						}
					}
				}
				""",
			"net/runelite/client/RuneLiteModule.java", """
				package net.runelite.client;

				class RuneLiteModule
				{
					static String[] properties()
					{
						return new String[]{"runelite.http-service.url", "runelite.session.url", "runelite.static.url", "runelite.ws.url", "runelite.pluginhub.url"};
					}
				}
				""",
			"net/runelite/client/RuntimeConfigLoader.java", """
				package net.runelite.client;

				class RuntimeConfigLoader
				{
					static String file()
					{
						return System.getProperty("runelite.rtconf");
					}
				}
				""",
			"net/runelite/client/game/WorldClient.java", """
				package net.runelite.client.game;

				class WorldClient
				{
					static String path()
					{
						return "worlds.js";
					}
				}
				""",
			"net/runelite/client/rs/RSConfig.java", """
				package net.runelite.client.rs;

				public class RSConfig
				{
				}
				""",
			"net/runelite/client/rs/ClientLoader.java", """
				package net.runelite.client.rs;

				public class ClientLoader
				{
					public static void check()
					{
						ClientLoader loader = new ClientLoader();
						RSConfig config = loader.downloadConfig();
						System.out.println("FAKE downloadConfig=ok");
						loader.updateVanilla(config);
						System.out.println("FAKE updateVanilla=returned");
						System.out.println("FAKE otherJagex=" + other());
					}

					private RSConfig downloadConfig()
					{
						String host = "192.168.0.210";
						if (!host.endsWith(".jagex.com"))
						{
							throw new IllegalStateException("downloadConfig refused " + host);
						}
						return new RSConfig();
					}

					private void updateVanilla(RSConfig config)
					{
						throw new IllegalStateException("updateVanilla ran");
					}

					// The same constant outside downloadConfig, which the patch must leave alone.
					static String other()
					{
						return ".jagex.com";
					}
				}
				"""),
			Map.of("net/runelite/client/runelite.properties", "runelite.version=0.0-selftest\n"));

		Path injected = jar(work, "injected-client-0.0-selftest.jar", Map.of(
			"client.java", """
				import java.math.BigInteger;
				import java.util.function.Supplier;

				public class client implements Supplier<String>
				{
					static final BigInteger EXPONENT = new BigInteger("10001", 16);
					static final BigInteger MODULUS = new BigInteger("THEIRS", 16);

					public String get()
					{
						String host = "192.168.0.210";
						boolean hostOk = host.endsWith("127.0.0.1") || host.endsWith(".runescape.com");
						// Not a host check: the patch must leave it alone.
						String loopback = "127.0.0.1";
						return "modulus=" + MODULUS.toString(16) + " hostOk=" + hostOk + " loopback=" + loopback;
					}
				}
				""".replace("THEIRS", theirs),
			"cd.java", """
				public class cd
				{
					static final String EXPONENT = "10001";
				}
				"""), Map.of());

		Path classpath = work.resolve("classpath.txt");
		Files.writeString(classpath, client + "\n" + injected + "\n");
		Path noInjected = work.resolve("classpath-no-injected.txt");
		Files.writeString(noInjected, client + "\n");
		Path stubLog = work.resolve("stub.log");
		Path out = work.resolve("out.txt");

		String scan = spike(spike, asm, work, List.of("scan", classpath.toString(), ours, "--rlcustom"), 0);
		check("scan finds the modulus in the one class with a hex constant", scan.contains("[scan] modulus: class client, the client's key is 1024 bits"));
		check("scan counts both exponent classes", scan.contains("exponent \"10001\" in 2 class(es), 1 of them"));
		check("scan finds one endsWith host check of two uses", scan.contains("\"127.0.0.1\": 2 use(s) in 1 class(es), 1 taken by String.endsWith"));
		check("scan finds updateVanilla", scan.contains("updateVanilla: PRESENT, called from [check]"));
		check("scan finds .jagex.com in downloadConfig", scan.contains("downloadConfig: \".jagex.com\" 1 time(s)"));
		check("scan reports the client jar", scan.contains("RuneLite 0.0-selftest, signature files none"));
		check("scan passes", scan.contains("every required patch point is there"));

		String console = spike(spike, asm, work, List.of("run", classpath.toString(), ours, "--rlcustom", "--world", "192.168.0.210",
			"--stub-log", stubLog.toString(), "--out", out.toString(), "--", "--jav_config", "http://192.168.0.210:43595/jav_config.ws", "--developer-mode"), 0);
		String run = Files.exists(out) ? Files.readString(out) : "";
		// The JVM's own notes (such as "Picked up JAVA_TOOL_OPTIONS") still go to the console.
		check("with --out the spike's and RuneLite's lines go to the file", console.lines().noneMatch(line -> line.startsWith("[") || line.startsWith("FAKE"))
			&& run.contains("[scan] every required patch point is there") && run.contains("FAKE dir="));
		run.lines().filter(line -> line.startsWith("[") || line.startsWith("FAKE")).forEach(line -> System.out.println("  out: " + line));
		check("RuneLite's folder moved to .rlcustom", run.contains("FAKE dir=.rlcustom"));
		check("RuneLite gets its arguments", run.contains("FAKE args=--jav_config http://192.168.0.210:43595/jav_config.ws --developer-mode"));
		check("main runs on a thread named RuneLite", run.contains("FAKE thread=RuneLite"));
		check("the context class loader is RuneLite's", run.contains("FAKE tccl=true"));
		check("the event dispatch thread's context class loader is RuneLite's", run.contains("FAKE edtTccl=true"));
		check("RuneLite runs in the patching class loader", run.contains("FAKE loader=runelite"));
		check("RuneLite can't see the spike's own classes", run.contains("FAKE spikeVisible=false"));
		check("downloadConfig takes our host", run.contains("FAKE downloadConfig=ok"));
		check("updateVanilla returns at once", run.contains("FAKE updateVanilla=returned"));
		check(".jagex.com outside downloadConfig is kept", run.contains("FAKE otherJagex=.jagex.com"));
		check("worlds.js answers with our world", run.contains("FAKE worlds={\"worlds\":[{\"id\":301,\"types\":[\"MEMBERS\"],\"address\":\"192.168.0.210\""));
		check("the client uses our modulus", run.contains("FAKE client=modulus=" + ours + " "));
		check("the client's host check takes our host", run.contains("hostOk=true"));
		check("a 127.0.0.1 that isn't a host check is kept", run.contains("loopback=127.0.0.1"));
		check("the patched classes are logged", run.contains("[spike] patched client: modulus x1, host check x1, unsigned")
			&& run.contains("[spike] patched net.runelite.client.rs.ClientLoader: jagex host check x1, updateVanilla(Lnet/runelite/client/rs/RSConfig;)V returns at once"));
		check("the planned patches are logged before RuneLite starts", run.indexOf("[spike] will patch client: modulus x1, host check x1")
			< run.indexOf("[spike] starting ") && run.contains("[spike] will patch net.runelite.client.RuneLite: folder .rlcustom x1"));
		String stub = Files.exists(stubLog) ? Files.readString(stubLog) : "";
		check("the stub logs the worlds.js call", stub.contains("GET /api/worlds.js -> 200"));
		check("a URL connection is logged before it connects", Pattern.compile("(?m)^\\[spike\\] connect http://127\\.0\\.0\\.1:\\d+/api/worlds\\.js$").matcher(run).find()
			&& run.indexOf("[spike] connect http://") < run.indexOf("FAKE worlds="));
		check("a plain socket is logged", run.contains("FAKE socket=true")
			&& Pattern.compile("(?m)^\\[spike\\] connect socket://127\\.0\\.0\\.1:\\d+$").matcher(run).find());

		String plain = spike(spike, asm, work, List.of("run", classpath.toString(), ours, "--world", "192.168.0.210", "--"), 0);
		check("without --rlcustom the folder stays .runelite", plain.contains("FAKE dir=.runelite"));

		String returned = spike(spike, asm, work, List.of("run", classpath.toString(), ours, "--", "--return"), 0);
		check("the JVM ends by itself once RuneLite's threads have", returned.contains("FAKE client=modulus=" + ours + " "));

		String missing = spike(spike, asm, work, List.of("scan", noInjected.toString(), ours), 2);
		check("scan without the injected client fails", missing.contains("MISSING no injected-client jar in the class path"));

		String longer = spike(spike, asm, work, List.of("run", classpath.toString(), tooLong, "--"), 2);
		check("a longer modulus than the client's is refused", longer.contains("MISSING modulus: ours has 260 digits, more than the client's 256")
			&& longer.contains("[spike] refusing to start RuneLite") && !longer.contains("FAKE"));

		System.out.println(failures == 0 ? "SelfTest: all checks passed" : "SelfTest: " + failures + " check(s) FAILED");
		System.exit(failures == 0 ? 0 : 1);
	}

	static void check(String what, boolean ok)
	{
		System.out.println((ok ? "pass  " : "FAIL  ") + what);
		if (!ok)
		{
			failures++;
		}
	}

	/** Runs the compiled SpikeLoader in a JVM of its own, as the VM does; returns its console output. */
	static String spike(Path spike, String asm, Path work, List<String> args, int expectedExit) throws Exception
	{
		List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
			"-Duser.home=" + work, "-Djava.awt.headless=true", "-cp", spike + File.pathSeparator + asm, "SpikeLoader"));
		command.addAll(args);
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Thread reader = new Thread(() ->
		{
			try (InputStream in = process.getInputStream())
			{
				in.transferTo(out);
			}
			catch (IOException ignored)
			{
			}
		});
		reader.start();
		if (!process.waitFor(60, TimeUnit.SECONDS))
		{
			System.out.println("  (still running after 60 s, killed)");
			process.destroyForcibly().waitFor();
		}
		reader.join();
		String output = out.toString(StandardCharsets.UTF_8);
		System.out.println("---- SpikeLoader " + String.join(" ", args.subList(0, 1)) + " (exit " + process.exitValue() + ")");
		output.lines().filter(line -> line.startsWith("[") || line.startsWith("FAKE") || line.contains("Exception")).forEach(line -> System.out.println("  " + line));
		check("SpikeLoader " + args.get(0) + " exits " + expectedExit, process.exitValue() == expectedExit);
		return output;
	}

	/** Compiles [sources] (path to source) with javac and jars the classes with [resources] (path to text). */
	static Path jar(Path work, String name, Map<String, String> sources, Map<String, String> resources) throws IOException
	{
		Path src = work.resolve(name + "-src");
		Path classes = work.resolve(name + "-classes");
		List<String> files = new ArrayList<>();
		for (Map.Entry<String, String> source : sources.entrySet())
		{
			Path file = src.resolve(source.getKey());
			Files.createDirectories(file.getParent());
			Files.writeString(file, source.getValue());
			files.add(file.toString());
		}
		Files.createDirectories(classes);
		JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
		List<String> javacArgs = new ArrayList<>(List.of("-d", classes.toString(), "--release", "11"));
		javacArgs.addAll(files);
		if (javac.run(null, null, null, javacArgs.toArray(new String[0])) != 0)
		{
			throw new IllegalStateException("javac failed for " + name);
		}
		Path jar = work.resolve(name);
		try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar)); Stream<Path> walk = Files.walk(classes))
		{
			for (Path file : walk.filter(Files::isRegularFile).toList())
			{
				out.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
				Files.copy(file, out);
				out.closeEntry();
			}
			for (Map.Entry<String, String> resource : resources.entrySet())
			{
				out.putNextEntry(new JarEntry(resource.getKey()));
				out.write(resource.getValue().getBytes(StandardCharsets.UTF_8));
				out.closeEntry();
			}
		}
		return jar;
	}
}
