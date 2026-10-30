import java.util.Locale
import java.util.zip.CRC32
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

group = "osrsps"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

dependencies {
    // RSProt for revision 241; wired into login in the next step.
    implementation(libs.rsprot.api)
    implementation(libs.netty.codec.http)
    implementation(libs.netty.codec.haproxy)
    implementation(libs.displee.cache)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.logback.classic)

    // The opcode and size tables, for the headless test client. Already on the runtime classpath through rsprot-api.
    testImplementation(libs.rsprot.desktop)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test-junit5"))
    // To see what a test's server logs.
    testImplementation(libs.logback.classic)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("osrsps.MainKt")
}

tasks.test {
    useJUnitPlatform()
    // The item audit (osrsps.audit.ItemAuditTest) reads a 50 MB Wiki file when asked to; everything else fits the default.
    maxHeapSize = providers.environmentVariable("OSRSPS_TEST_HEAP").getOrElse("512m")
    // GameRandomTest reads the main sources themselves.
    inputs.dir("src/main/kotlin").withPathSensitivity(PathSensitivity.RELATIVE)
    // For CI (build.yml's server-tests jobs). -PtestShard=i/n runs only shard i of n: the classes in
    // src/test/test-times.txt are dealt out longest first to whichever shard has the least so far, so the shards take
    // about as long as each other, and a class the file doesn't list yet goes by a hash of its name.
    // -PtestForks=n runs test classes in n JVMs at once.
    maxParallelForks = providers.gradleProperty("testForks").map(String::toInt).getOrElse(1)
    val shard = providers.gradleProperty("testShard").orNull?.split("/")?.map(String::toInt)
    if (shard != null) {
        val (index, count) = shard
        val owner = HashMap<String, Int>()
        val loads = DoubleArray(count)
        file("src/test/test-times.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line -> line.trim().split(Regex("\\s+")).let { it[1] to it[0].toDouble() } }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
            .forEach { (name, seconds) ->
                val least = loads.indices.minBy { loads[it] }
                loads[least] += seconds
                owner[name] = least
            }
        exclude { element ->
            if (element.isDirectory || !element.name.endsWith(".class")) return@exclude false
            val name = element.relativePath.pathString.removeSuffix(".class").substringBefore('$').replace('/', '.')
            val home = owner[name] ?: (CRC32().apply { update(name.toByteArray()) }.value % count).toInt()
            home != index - 1
        }
    }
}

// Writes src/test/test-times.txt, the seconds each test class took, from the last full test run, for the shards above:
// ./gradlew test --rerun testTimes
tasks.register("testTimes") {
    val results = layout.buildDirectory.dir("test-results/test")
    val out = file("src/test/test-times.txt")
    doLast {
        val parser = DocumentBuilderFactory.newInstance()
        val lines = results.get().asFile.listFiles { f -> f.name.endsWith(".xml") }.orEmpty().map { f ->
            val suite = parser.newDocumentBuilder().parse(f).documentElement
            String.format(Locale.ROOT, "%.1f %s", suite.getAttribute("time").toDouble(), suite.getAttribute("name"))
        }.sortedBy { it.substringAfter(' ') }
        out.writeText("# Seconds per test class, for build.gradle.kts's test shards: ./gradlew test --rerun testTimes\n" + lines.joinToString("\n") + "\n")
    }
}
