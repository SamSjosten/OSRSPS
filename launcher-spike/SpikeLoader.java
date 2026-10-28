/*
 * Spike for the custom launcher (PR 2 of the plan; a draft that is never merged). It answers one question before any
 * big work: can the pinned RuneLite be pointed at our server by patching classes in memory as they load, with no proxy
 * and no jar rewritten on disk?
 *
 *   javac -cp asm-9.11.jar;asm-tree-9.11.jar -d classes SpikeLoader.java
 *   java -cp classes;asm-9.11.jar;asm-tree-9.11.jar SpikeLoader scan <classpath.txt> <modulus> [--rlcustom]
 *   javaw <RuneLite's JVM flags> -cp classes;asm-9.11.jar;asm-tree-9.11.jar SpikeLoader run <classpath.txt> <modulus>
 *       [--rlcustom] [--world <host>] [--stub-log <file>] [--out <file>] [--main <class>] -- <RuneLite's arguments>
 *
 * classpath.txt lists RuneLite's jars, one path per line, in the bootstrap's order. <modulus> is our server's RSA
 * modulus in hex. --rlcustom moves RuneLite's folder from .runelite to .rlcustom, as RSProx does, so a client started
 * with -Duser.home=C:\RSPS on the test VM finds the test runner and its profile there. --out appends everything the JVM
 * prints (the spike's lines and RuneLite's console log) to a file, for javaw, which has no console.
 *
 * "scan" reports every patch point it finds and exits 0 only when each required one is there. "run" scans the same way
 * and refuses to start when one is missing. Then it starts RuneLite through PatchingClassLoader, the way RuneLite's own
 * launcher runs it on a Mac, with a stub on 127.0.0.1 for RuneLite's web API: worlds.js answers with our world, every
 * other call is logged and answered 404. Each place Java is asked to connect to is logged as "[spike] connect ...".
 *
 * What it patches (RSProx's recipe, rsprox/runelite-launcher RuneLitePatcher.kt and ClientLoaderPatcher.kt, redone with
 * ASM on classes as they load):
 * - injected-client: the RSA modulus, in the one class that also holds the exponent "10001". Ours may not be longer.
 * - injected-client: "127.0.0.1" where String.endsWith takes it (the client's host check), made "" so any host passes.
 * - ClientLoader: updateVanilla, when the jar has it, made to return at once. Left on, a forged jav_config could make it
 *   download a gamepack, so it goes before the host check below is touched.
 * - ClientLoader.downloadConfig: ".jagex.com", when the jar has it, made "".
 * - RuneLite: ".runelite" made ".rlcustom" (only with --rlcustom).
 * And it checks, without patching, that RuneLiteModule reads -Drunelite.http-service.url (our world list) and
 * RuntimeConfigLoader reads -Drunelite.rtconf (a local runtime config, so RuneLite's remote "outdated" list never applies).
 *
 * Output names jars, classes, methods and counts or bit lengths, never class bytes or the client's own constants.
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class SpikeLoader
{
	static final String CLIENT_LOADER = "net/runelite/client/rs/ClientLoader";
	static final String DOWNLOAD_CONFIG = "downloadConfig()Lnet/runelite/client/rs/RSConfig;";
	static final String UPDATE_VANILLA = "updateVanilla(Lnet/runelite/client/rs/RSConfig;)V";
	static final String RUNELITE = "net/runelite/client/RuneLite";
	static final String MODULE = "net/runelite/client/RuneLiteModule";
	static final String RTCONF_LOADER = "net/runelite/client/RuntimeConfigLoader";
	static final String WORLD_CLIENT = "net/runelite/client/game/WorldClient";
	static final List<String> URL_PROPERTIES = List.of("runelite.http-service.url", "runelite.session.url",
		"runelite.static.url", "runelite.ws.url", "runelite.pluginhub.url");
	static final String EXPONENT = "10001";
	static final String LOCALHOST = "127.0.0.1";
	static final String JAGEX_HOST = ".jagex.com";
	static final Pattern HEX = Pattern.compile("[0-9a-fA-F]+");

	public static void main(String[] args) throws Exception
	{
		Options options = Options.parse(args);
		if (options.out != null)
		{
			PrintStream out = new PrintStream(Files.newOutputStream(options.out, StandardOpenOption.CREATE, StandardOpenOption.APPEND), true, StandardCharsets.UTF_8);
			System.setOut(out);
			System.setErr(out);
		}
		List<Path> jars = new ArrayList<>();
		for (String line : Files.readAllLines(options.classpath, StandardCharsets.UTF_8))
		{
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			Path jar = Path.of(line);
			if (!Files.isRegularFile(jar))
			{
				fail("no jar at " + jar);
			}
			jars.add(jar);
		}

		Scan scan = Scan.of(jars, options.modulus, options.rlcustom);
		scan.report.forEach(line -> System.out.println("[scan] " + line));
		if (!scan.missing.isEmpty())
		{
			scan.missing.forEach(line -> System.out.println("[scan] MISSING " + line));
		}
		System.out.println("[scan] " + (scan.missing.isEmpty() ? "every required patch point is there" : scan.missing.size() + " required patch point(s) missing"));
		if (options.mode.equals("scan"))
		{
			System.exit(scan.missing.isEmpty() ? 0 : 2);
		}
		if (!scan.missing.isEmpty())
		{
			System.out.println("[spike] refusing to start RuneLite");
			System.exit(2);
		}
		run(jars, scan, options);
	}

	static void run(List<Path> jars, Scan scan, Options options) throws Exception
	{
		PrintStream stubLog = options.stubLog == null ? System.out
			: new PrintStream(Files.newOutputStream(options.stubLog, StandardOpenOption.CREATE, StandardOpenOption.APPEND), true, StandardCharsets.UTF_8);
		ApiStub stub = new ApiStub(options.world, stubLog);
		// RuneLiteModule reads these when RuneLite's injector starts, long after this.
		setIfAbsent("runelite.http-service.url", stub.base + "api/");
		setIfAbsent("runelite.session.url", stub.base + "session/");
		setIfAbsent("runelite.static.url", stub.base + "static/");
		setIfAbsent("runelite.ws.url", stub.base + "ws/");
		setIfAbsent("runelite.pluginhub.url", stub.base + "plugins/");
		System.out.println("[spike] RuneLite API stub at " + stub.base + ", world " + options.world);
		for (String property : URL_PROPERTIES)
		{
			System.out.println("[spike] " + property + "=" + System.getProperty(property));
		}

		// Plain sockets, URL connections and OkHttp all ask the default ProxySelector before they connect, so this logs
		// every place RuneLite and the client connect to, however short the connection; the test VM's connection watch
		// only sees one that is still open at its next sample. NIO channels don't ask it, and the watch covers those.
		ProxySelector.setDefault(new ConnectLog(ProxySelector.getDefault(), System.out));

		// Each is patched as it loads, so a class RuneLite never loads is never patched.
		scan.patches.values().forEach(patch -> System.out.println("[spike] will patch " + patch.className.replace('/', '.') + ": " + patch.summary()));
		PatchingClassLoader loader = new PatchingClassLoader(jars, scan.patches, System.out);
		Thread thread = new Thread(() ->
		{
			try
			{
				// As RuneLite's ReflectionLauncher does: Swing loads the look and feel through this when it isn't on the
				// boot path. On this thread, so that the event queue it sets up takes RuneLite's loader as the context
				// class loader of the event dispatch thread.
				javax.swing.UIManager.put("ClassLoader", loader);
				Method main = loader.loadClass(options.mainClass).getMethod("main", String[].class);
				System.out.println("[spike] starting " + options.mainClass + " " + String.join(" ", options.runeliteArgs));
				main.invoke(null, (Object) options.runeliteArgs);
			}
			catch (Throwable e)
			{
				System.out.println("[spike] RuneLite did not start");
				e.printStackTrace(System.out);
				System.exit(3);
			}
		}, "RuneLite");
		// Unlike ReflectionLauncher, which leaves it at the class path's loader: anything that looks classes up through
		// the context class loader then finds RuneLite's, as it would with RuneLite on the class path.
		thread.setContextClassLoader(loader);
		thread.start();
	}

	static void setIfAbsent(String key, String value)
	{
		if (System.getProperty(key) == null)
		{
			System.setProperty(key, value);
		}
	}

	static void fail(String message)
	{
		System.out.println("[spike] " + message);
		System.exit(1);
	}

	record Options(String mode, Path classpath, String modulus, boolean rlcustom, String world, Path stubLog, Path out,
		String mainClass, String[] runeliteArgs)
	{
		static Options parse(String[] args)
		{
			if (args.length < 3 || !(args[0].equals("scan") || args[0].equals("run")))
			{
				System.out.println("usage: SpikeLoader scan|run <classpath.txt> <modulus> [--rlcustom] [--world <host>] "
					+ "[--stub-log <file>] [--out <file>] [--main <class>] [-- <RuneLite's arguments>]");
				System.exit(1);
			}
			String modulus = args[2];
			if (!HEX.matcher(modulus).matches())
			{
				fail("the modulus must be hex");
			}
			boolean rlcustom = false;
			String world = "127.0.0.1";
			Path stubLog = null;
			Path out = null;
			String mainClass = "net.runelite.client.RuneLite";
			String[] rest = new String[0];
			for (int i = 3; i < args.length; i++)
			{
				switch (args[i])
				{
					case "--rlcustom" -> rlcustom = true;
					case "--world" -> world = args[++i];
					case "--stub-log" -> stubLog = Path.of(args[++i]);
					case "--out" -> out = Path.of(args[++i]);
					case "--main" -> mainClass = args[++i];
					case "--" ->
					{
						rest = Arrays.copyOfRange(args, i + 1, args.length);
						i = args.length;
					}
					default -> fail("unknown option " + args[i]);
				}
			}
			return new Options(args[0], Path.of(args[1]), modulus, rlcustom, world, stubLog, out, mainClass, rest);
		}
	}

	/** One class's patch: constant replacements (each counted against what the scan found) and methods made to return. */
	static final class ClassPatch
	{
		final String className;
		final List<Rule> rules = new ArrayList<>();
		final Set<String> returnAtOnce = new TreeSet<>();

		ClassPatch(String className)
		{
			this.className = className;
		}

		/** A string constant to replace: in one method only when [method] is set, only where String.endsWith takes it. */
		record Rule(String what, String from, String to, String method, boolean endsWithOnly, boolean fields, int expected)
		{
		}

		String summary()
		{
			List<String> parts = new ArrayList<>();
			rules.forEach(rule -> parts.add(rule.what + " x" + rule.expected));
			returnAtOnce.forEach(method -> parts.add(method + " returns at once"));
			return String.join(", ", parts);
		}

		byte[] apply(byte[] bytes)
		{
			ClassNode node = new ClassNode();
			new ClassReader(bytes).accept(node, 0);
			int[] done = new int[rules.size()];
			for (FieldNode field : node.fields)
			{
				for (int i = 0; i < rules.size(); i++)
				{
					Rule rule = rules.get(i);
					if (rule.fields && rule.from.equals(field.value))
					{
						field.value = rule.to;
						done[i]++;
					}
				}
			}
			Set<String> returned = new HashSet<>();
			for (MethodNode method : node.methods)
			{
				String key = method.name + method.desc;
				if (returnAtOnce.contains(key))
				{
					returnAtOnce(method);
					returned.add(key);
					continue;
				}
				for (AbstractInsnNode insn : method.instructions)
				{
					if (!(insn instanceof LdcInsnNode ldc) || !(ldc.cst instanceof String value))
					{
						continue;
					}
					for (int i = 0; i < rules.size(); i++)
					{
						Rule rule = rules.get(i);
						if (rule.from.equals(value) && (rule.method == null || rule.method.equals(key))
							&& (!rule.endsWithOnly || takenByEndsWith(ldc)))
						{
							ldc.cst = rule.to;
							done[i]++;
							break;
						}
					}
				}
			}
			for (int i = 0; i < rules.size(); i++)
			{
				if (done[i] != rules.get(i).expected)
				{
					throw new IllegalStateException(className + ": " + rules.get(i).what + " replaced " + done[i]
						+ " times, the scan found " + rules.get(i).expected);
				}
			}
			if (!returned.equals(returnAtOnce))
			{
				throw new IllegalStateException(className + ": methods to stop not all found: " + returnAtOnce);
			}
			// Maxima recomputed, frames kept as they are: no patch changes a branch, and a stopped method has none.
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			node.accept(writer);
			return writer.toByteArray();
		}

		static void returnAtOnce(MethodNode method)
		{
			if (!method.desc.endsWith(")V"))
			{
				throw new IllegalStateException(method.name + method.desc + " does not return void");
			}
			method.instructions.clear();
			method.tryCatchBlocks.clear();
			method.localVariables = null;
			method.visibleLocalVariableAnnotations = null;
			method.invisibleLocalVariableAnnotations = null;
			method.instructions.add(new InsnNode(Opcodes.RETURN));
		}
	}

	static boolean takenByEndsWith(AbstractInsnNode insn)
	{
		AbstractInsnNode next = insn.getNext();
		while (next instanceof LabelNode || next instanceof LineNumberNode || next instanceof FrameNode)
		{
			next = next.getNext();
		}
		return next instanceof MethodInsnNode call && call.owner.equals("java/lang/String") && call.name.equals("endsWith")
			&& call.desc.equals("(Ljava/lang/String;)Z");
	}

	/** The string and int constants a class's constant pool holds (what its ldc instructions and constant fields use). */
	record Constants(Set<String> strings, Set<Integer> ints)
	{
		static Constants of(ClassReader reader)
		{
			char[] buffer = new char[reader.getMaxStringLength()];
			Set<String> strings = new HashSet<>();
			Set<Integer> ints = new HashSet<>();
			for (int i = 1; i < reader.getItemCount(); i++)
			{
				int offset = reader.getItem(i);
				if (offset == 0)
				{
					continue; // the second slot of a long or double
				}
				switch (reader.readByte(offset - 1))
				{
					case 8 -> strings.add(reader.readUTF8(offset, buffer)); // CONSTANT_String
					case 3 -> ints.add(reader.readInt(offset)); // CONSTANT_Integer
					default ->
					{
					}
				}
			}
			return new Constants(strings, ints);
		}
	}

	static final class Scan
	{
		final List<String> report = new ArrayList<>();
		final List<String> missing = new ArrayList<>();
		/** By internal class name. */
		final Map<String, ClassPatch> patches = new LinkedHashMap<>();

		ClassPatch patch(String className)
		{
			return patches.computeIfAbsent(className, ClassPatch::new);
		}

		static Scan of(List<Path> jars, String modulus, boolean rlcustom) throws IOException
		{
			Scan scan = new Scan();
			Set<String> seen = new HashSet<>();
			Path injected = null;
			int injectedClasses = 0;
			int portClasses = 0;
			Map<String, List<String>> exponentClasses = new LinkedHashMap<>();
			Map<String, byte[]> exponentBytes = new LinkedHashMap<>();
			Map<String, int[]> localhost = new LinkedHashMap<>();
			Map<String, byte[]> named = new LinkedHashMap<>();
			Map<String, String> jarOf = new LinkedHashMap<>();
			for (Path jar : jars)
			{
				String jarName = jar.getFileName().toString();
				boolean isInjected = jarName.startsWith("injected-client");
				if (isInjected)
				{
					injected = jar;
				}
				try (JarFile file = new JarFile(jar.toFile(), false))
				{
					if (jarName.startsWith("client-"))
					{
						scan.reportJar(file, jarName);
					}
					for (Enumeration<JarEntry> entries = file.entries(); entries.hasMoreElements(); )
					{
						JarEntry entry = entries.nextElement();
						String name = entry.getName();
						if (!name.endsWith(".class") || name.startsWith("META-INF/"))
						{
							continue;
						}
						String className = name.substring(0, name.length() - ".class".length());
						// The class loader takes the first jar that has a class, so the scan does too.
						if (!seen.add(className))
						{
							continue;
						}
						byte[] bytes;
						try (InputStream in = file.getInputStream(entry))
						{
							bytes = in.readAllBytes();
						}
						if (List.of(CLIENT_LOADER, RUNELITE, MODULE, RTCONF_LOADER, WORLD_CLIENT).contains(className))
						{
							named.put(className, bytes);
							jarOf.put(className, jarName);
						}
						if (!isInjected)
						{
							continue;
						}
						injectedClasses++;
						Constants constants = Constants.of(new ClassReader(bytes));
						if (constants.strings.contains(EXPONENT))
						{
							exponentClasses.put(className, constants.strings.stream().filter(s -> s.length() >= 256 && HEX.matcher(s).matches()).toList());
							exponentBytes.put(className, bytes);
						}
						if (constants.strings.contains(LOCALHOST))
						{
							localhost.put(className, countLocalhost(bytes));
						}
						if (constants.ints.contains(43594))
						{
							portClasses++;
						}
					}
				}
			}

			if (injected == null)
			{
				scan.missing.add("no injected-client jar in the class path");
			}
			else
			{
				scan.report.add(injected.getFileName() + ": " + injectedClasses + " classes; " + portClasses
					+ " hold the game port 43594 (ours too, so not patched)");
				scan.scanModulus(exponentClasses, exponentBytes, modulus);
				scan.scanLocalhost(localhost);
			}
			scan.scanClientLoader(named.get(CLIENT_LOADER), jarOf.get(CLIENT_LOADER));
			scan.scanRuneLite(named.get(RUNELITE), jarOf.get(RUNELITE), rlcustom);
			scan.scanProperty(named.get(MODULE), MODULE, "runelite.http-service.url", true, "the world list comes from our stub");
			for (String property : URL_PROPERTIES.subList(1, URL_PROPERTIES.size()))
			{
				scan.scanProperty(named.get(MODULE), MODULE, property, false, "that API stays on the stub");
			}
			scan.scanProperty(named.get(RTCONF_LOADER), RTCONF_LOADER, "runelite.rtconf", true, "the runtime config is a local file");
			scan.scanProperty(named.get(WORLD_CLIENT), WORLD_CLIENT, "worlds.js", false, "RuneLite asks the API base for worlds.js");
			return scan;
		}

		void reportJar(JarFile file, String jarName) throws IOException
		{
			JarEntry properties = file.getJarEntry("net/runelite/client/runelite.properties");
			String version = "?";
			if (properties != null)
			{
				Properties loaded = new Properties();
				try (InputStream in = file.getInputStream(properties))
				{
					loaded.load(in);
				}
				version = loaded.getProperty("runelite.version", "?");
			}
			List<String> signatures = file.stream().map(JarEntry::getName)
				.filter(n -> n.startsWith("META-INF/") && (n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC") || n.endsWith(".SF")))
				.toList();
			report.add(jarName + ": RuneLite " + version + ", signature files " + (signatures.isEmpty() ? "none" : signatures));
		}

		void scanModulus(Map<String, List<String>> exponentClasses, Map<String, byte[]> exponentBytes, String modulus)
		{
			List<String> candidates = exponentClasses.entrySet().stream().filter(e -> !e.getValue().isEmpty()).map(Map.Entry::getKey).toList();
			report.add("exponent \"10001\" in " + exponentClasses.size() + " class(es), " + candidates.size() + " of them with a 256+ digit hex constant");
			if (candidates.size() != 1)
			{
				missing.add("modulus: need exactly one class with the exponent and a 256+ digit hex constant, found " + candidates.size());
				return;
			}
			String className = candidates.get(0);
			List<String> hex = exponentClasses.get(className);
			if (hex.size() != 1)
			{
				missing.add("modulus: class " + className + " holds " + hex.size() + " 256+ digit hex constants, need exactly one");
				return;
			}
			String old = hex.get(0);
			int theirs = new BigInteger(old, 16).bitLength();
			int ours = new BigInteger(modulus, 16).bitLength();
			report.add("modulus: class " + className + ", the client's key is " + theirs + " bits (" + old.length()
				+ " digits), ours " + ours + " bits (" + modulus.length() + " digits)");
			if (modulus.length() > old.length())
			{
				missing.add("modulus: ours has " + modulus.length() + " digits, more than the client's " + old.length());
				return;
			}
			int uses = usesOf(exponentBytes.get(className), old);
			if (uses == 0)
			{
				missing.add("modulus: class " + className + " holds the constant but no ldc or constant field uses it");
				return;
			}
			patch(className).rules.add(new ClassPatch.Rule("modulus", old, modulus, null, false, true, uses));
		}

		void scanLocalhost(Map<String, int[]> localhost)
		{
			int total = localhost.values().stream().mapToInt(counts -> counts[0]).sum();
			int endsWith = localhost.values().stream().mapToInt(counts -> counts[1]).sum();
			report.add("\"127.0.0.1\": " + total + " use(s) in " + localhost.size() + " class(es), " + endsWith
				+ " taken by String.endsWith" + (localhost.isEmpty() ? "" : " " + localhost.keySet()));
			if (endsWith == 0)
			{
				missing.add("host check: no \"127.0.0.1\" taken by String.endsWith in the injected client");
				return;
			}
			localhost.forEach((className, counts) ->
			{
				if (counts[1] > 0)
				{
					patch(className).rules.add(new ClassPatch.Rule("host check", LOCALHOST, "", null, true, false, counts[1]));
				}
			});
		}

		void scanClientLoader(byte[] bytes, String jarName)
		{
			if (bytes == null)
			{
				missing.add("no " + CLIENT_LOADER);
				return;
			}
			ClassNode node = new ClassNode();
			new ClassReader(bytes).accept(node, 0);
			List<String> methods = node.methods.stream().map(m -> m.name + m.desc).toList();
			report.add("ClientLoader (" + jarName + "): " + methods.size() + " methods "
				+ node.methods.stream().map(m -> m.name).distinct().toList());
			int inDownloadConfig = 0;
			List<String> hostStrings = new ArrayList<>();
			List<String> callers = new ArrayList<>();
			for (MethodNode method : node.methods)
			{
				String key = method.name + method.desc;
				for (AbstractInsnNode insn : method.instructions)
				{
					if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String value)
					{
						if (value.equals(JAGEX_HOST) && key.equals(DOWNLOAD_CONFIG))
						{
							inDownloadConfig++;
						}
						if (value.contains("jagex") || value.contains("runescape") || value.contains("gamepack") || value.contains("vanilla"))
						{
							hostStrings.add(method.name + ": \"" + value + "\"");
						}
					}
					if (insn instanceof MethodInsnNode call && call.owner.equals(CLIENT_LOADER) && (call.name + call.desc).equals(UPDATE_VANILLA))
					{
						callers.add(method.name);
					}
				}
			}
			report.add("ClientLoader strings naming Jagex, a gamepack or vanilla: " + (hostStrings.isEmpty() ? "none" : hostStrings));
			MethodNode updateVanilla = node.methods.stream().filter(m -> (m.name + m.desc).equals(UPDATE_VANILLA)).findFirst().orElse(null);
			if (updateVanilla == null)
			{
				report.add("updateVanilla: absent (as in the public source)");
			}
			else
			{
				Set<String> calls = new TreeSet<>();
				for (AbstractInsnNode insn : updateVanilla.instructions)
				{
					if (insn instanceof MethodInsnNode call)
					{
						calls.add(call.owner.substring(call.owner.lastIndexOf('/') + 1) + "." + call.name);
					}
					else if (insn instanceof FieldInsnNode field)
					{
						calls.add("field " + field.owner.substring(field.owner.lastIndexOf('/') + 1) + "." + field.name);
					}
				}
				report.add("updateVanilla: PRESENT, called from " + callers + ", uses " + calls);
				patch(CLIENT_LOADER).returnAtOnce.add(UPDATE_VANILLA);
			}
			if (!methods.contains(DOWNLOAD_CONFIG))
			{
				missing.add("ClientLoader has no " + DOWNLOAD_CONFIG);
				return;
			}
			report.add("downloadConfig: \"" + JAGEX_HOST + "\" " + (inDownloadConfig == 0 ? "absent (as in the public source)" : inDownloadConfig + " time(s), made \"\""));
			if (inDownloadConfig > 0)
			{
				patch(CLIENT_LOADER).rules.add(new ClassPatch.Rule("jagex host check", JAGEX_HOST, "", DOWNLOAD_CONFIG, false, false, inDownloadConfig));
			}
		}

		void scanRuneLite(byte[] bytes, String jarName, boolean rlcustom)
		{
			if (bytes == null)
			{
				missing.add("no " + RUNELITE);
				return;
			}
			ClassNode node = new ClassNode();
			new ClassReader(bytes).accept(node, 0);
			int folder = 0;
			int fields = 0;
			boolean developerMode = false;
			for (FieldNode field : node.fields)
			{
				if (".runelite".equals(field.value))
				{
					fields++;
				}
			}
			for (MethodNode method : node.methods)
			{
				for (AbstractInsnNode insn : method.instructions)
				{
					if (insn instanceof LdcInsnNode ldc && ".runelite".equals(ldc.cst))
					{
						folder++;
					}
					if (insn instanceof LdcInsnNode ldc && "developer-mode".equals(ldc.cst))
					{
						developerMode = true;
					}
				}
			}
			report.add("RuneLite (" + jarName + "): \".runelite\" " + folder + " time(s)" + (fields > 0 ? " and in " + fields + " field(s)" : "")
				+ ", --developer-mode option " + (developerMode ? "there" : "NOT FOUND"));
			if (rlcustom)
			{
				if (folder + fields == 0)
				{
					missing.add("RuneLite: no \".runelite\" to move to \".rlcustom\"");
					return;
				}
				patch(RUNELITE).rules.add(new ClassPatch.Rule("folder .rlcustom", ".runelite", ".rlcustom", null, false, true, folder + fields));
			}
		}

		void scanProperty(byte[] bytes, String className, String value, boolean required, String why)
		{
			boolean found = bytes != null && Constants.of(new ClassReader(bytes)).strings.contains(value);
			report.add(className.substring(className.lastIndexOf('/') + 1) + ": \"" + value + "\" " + (found ? "there, so " + why : "NOT FOUND"));
			if (!found && required)
			{
				missing.add(className + " does not read \"" + value + "\"");
			}
		}
	}

	/** How often a class uses a string constant, in ldc instructions and constant fields. */
	static int usesOf(byte[] bytes, String value)
	{
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		int uses = 0;
		for (FieldNode field : node.fields)
		{
			if (value.equals(field.value))
			{
				uses++;
			}
		}
		for (MethodNode method : node.methods)
		{
			for (AbstractInsnNode insn : method.instructions)
			{
				if (insn instanceof LdcInsnNode ldc && value.equals(ldc.cst))
				{
					uses++;
				}
			}
		}
		return uses;
	}

	/** Uses of "127.0.0.1" in a class: all of them, and those String.endsWith takes. */
	static int[] countLocalhost(byte[] bytes)
	{
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		int[] counts = new int[2];
		for (MethodNode method : node.methods)
		{
			for (AbstractInsnNode insn : method.instructions)
			{
				if (insn instanceof LdcInsnNode ldc && LOCALHOST.equals(ldc.cst))
				{
					counts[0]++;
					if (takenByEndsWith(ldc))
					{
						counts[1]++;
					}
				}
			}
		}
		return counts;
	}

	/** Logs each scheme, host, port and path Java is asked to connect to, once, and never a query; then asks [next]. */
	static final class ConnectLog extends ProxySelector
	{
		private final ProxySelector next;
		private final PrintStream log;
		private final Set<String> seen = ConcurrentHashMap.newKeySet();

		ConnectLog(ProxySelector next, PrintStream log)
		{
			this.next = next;
			this.log = log;
		}

		@Override
		public List<Proxy> select(URI uri)
		{
			if (uri == null)
			{
				throw new IllegalArgumentException("URI can't be null");
			}
			String scheme = String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT);
			int port = uri.getPort() != -1 ? uri.getPort() : switch (scheme)
			{
				case "http", "ws" -> 80;
				case "https", "wss" -> 443;
				default -> -1;
			};
			String where = uri.getHost() != null ? uri.getHost() + ":" + port : uri.getRawAuthority();
			String target = scheme + "://" + where + (uri.getRawPath() == null ? "" : uri.getRawPath());
			if (seen.add(target))
			{
				log.println("[spike] connect " + target);
			}
			return next != null ? next.select(uri) : List.of(Proxy.NO_PROXY);
		}

		@Override
		public void connectFailed(URI uri, SocketAddress address, IOException e)
		{
			if (next != null)
			{
				next.connectFailed(uri, address, e);
			}
		}
	}

	/**
	 * RuneLite's jars, with the classes [patches] names patched as they load. A patched class keeps the code signers of
	 * its jar entry, so a package's classes all carry the same signers, patched or not, and the jar's signature is
	 * still checked as the class is read.
	 */
	static final class PatchingClassLoader extends URLClassLoader
	{
		static
		{
			registerAsParallelCapable();
		}

		private final List<JarFile> files = new ArrayList<>();
		private final List<URL> urls = new ArrayList<>();
		private final Map<String, ClassPatch> patches;
		private final PrintStream log;

		PatchingClassLoader(List<Path> jars, Map<String, ClassPatch> patches, PrintStream log) throws IOException
		{
			super("runelite", toUrls(jars), ClassLoader.getPlatformClassLoader());
			for (Path jar : jars)
			{
				files.add(new JarFile(jar.toFile(), true));
				urls.add(jar.toUri().toURL());
			}
			this.patches = patches;
			this.log = log;
		}

		static URL[] toUrls(List<Path> jars) throws IOException
		{
			URL[] urls = new URL[jars.size()];
			for (int i = 0; i < jars.size(); i++)
			{
				urls[i] = jars.get(i).toUri().toURL();
			}
			return urls;
		}

		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException
		{
			String className = name.replace('.', '/');
			ClassPatch patch = patches.get(className);
			if (patch == null)
			{
				return super.findClass(name);
			}
			for (int i = 0; i < files.size(); i++)
			{
				JarFile file = files.get(i);
				JarEntry entry = file.getJarEntry(className + ".class");
				if (entry == null)
				{
					continue;
				}
				try
				{
					byte[] bytes;
					try (InputStream in = file.getInputStream(entry))
					{
						bytes = in.readAllBytes();
					}
					// Known once the entry has been read to its end, which also checked it against the jar's signature.
					CodeSigner[] signers = entry.getCodeSigners();
					byte[] patched = patch.apply(bytes);
					definePackageOf(name, file, urls.get(i));
					Class<?> loaded = defineClass(name, patched, 0, patched.length, new CodeSource(urls.get(i), signers));
					log.println("[spike] patched " + name + ": " + patch.summary()
						+ (signers == null ? ", unsigned" : ", keeps the jar's " + signers.length + " signer(s)"));
					return loaded;
				}
				catch (IOException | RuntimeException e)
				{
					log.println("[spike] could not patch " + name + ": " + e);
					throw new ClassNotFoundException(name, e);
				}
			}
			throw new ClassNotFoundException(name);
		}

		private void definePackageOf(String name, JarFile file, URL url) throws IOException
		{
			int dot = name.lastIndexOf('.');
			if (dot < 0)
			{
				return;
			}
			String pkg = name.substring(0, dot);
			if (getDefinedPackage(pkg) != null)
			{
				return;
			}
			Manifest manifest = file.getManifest();
			try
			{
				if (manifest != null)
				{
					definePackage(pkg, manifest, url);
				}
				else
				{
					definePackage(pkg, null, null, null, null, null, null, null);
				}
			}
			catch (IllegalArgumentException definedMeanwhile)
			{
				// Another thread defined it first.
			}
		}
	}

	/** RuneLite's web API on 127.0.0.1: worlds.js answers with our world; every call is logged, the rest get 404. */
	static final class ApiStub
	{
		final String base;
		private final PrintStream log;
		private final byte[] worlds;

		ApiStub(String worldHost, PrintStream log) throws IOException, InterruptedException
		{
			this.log = log;
			// RuneLite's World: id, types, address, activity, location, players.
			worlds = ("{\"worlds\":[{\"id\":301,\"types\":[\"MEMBERS\"],\"address\":\"" + worldHost
				+ "\",\"activity\":\"OSRSPS spike\",\"location\":0,\"players\":0}]}").getBytes(StandardCharsets.UTF_8);
			HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			server.createContext("/", this::handle);
			server.setExecutor(Executors.newCachedThreadPool(task ->
			{
				Thread thread = new Thread(task, "spike-api-stub");
				thread.setDaemon(true);
				return thread;
			}));
			// Started from a daemon thread, so that its dispatcher thread is one too and doesn't keep the JVM running after
			// RuneLite's last thread has ended.
			Thread starter = new Thread(server::start, "spike-api-stub-start");
			starter.setDaemon(true);
			starter.start();
			starter.join();
			base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
		}

		private void handle(HttpExchange exchange) throws IOException
		{
			try (exchange)
			{
				exchange.getRequestBody().readAllBytes();
				boolean isWorlds = exchange.getRequestURI().getPath().endsWith("/worlds.js");
				synchronized (log)
				{
					log.println(Instant.now() + " " + exchange.getRequestMethod() + " " + exchange.getRequestURI() + " -> " + (isWorlds ? 200 : 404));
				}
				if (!isWorlds)
				{
					exchange.sendResponseHeaders(404, -1);
					return;
				}
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, worlds.length);
				try (OutputStream out = exchange.getResponseBody())
				{
					out.write(worlds);
				}
			}
		}
	}
}
