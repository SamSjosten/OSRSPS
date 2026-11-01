package osrsps.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

private val logger = io.github.oshai.kotlinlogging.KotlinLogging.logger {}

/**
 * [ReportForwarder.IssueTracker] on GitHub's REST API: issues in [repo] (the private server repository), releases
 * looked up in [launcherRepo] (the public launcher repository). [token] is a fine-grained token with Issues read and
 * write on [repo]; it never leaves this class but in the Authorization header, and isn't in any message it throws.
 */
class GitHubIssues(
    private val repo: String,
    private val token: String,
    private val launcherRepo: String = LAUNCHER_REPO,
    private val api: String = API,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) : ReportForwarder.IssueTracker {
    override fun isRelease(version: String): Boolean? {
        val status = runCatching { send("GET", "/repos/$launcherRepo/releases/tags/v$version", null).statusCode() }.getOrNull()
        return when (status) {
            200 -> true
            404 -> false
            else -> null
        }
    }

    override fun isOpen(issue: Int): Boolean {
        val response = send("GET", "/repos/$repo/issues/$issue", null)
        // Deleted (410), gone or never ours (404), or moved to another repository (301): not open here, so a new one.
        if (response.statusCode() in GONE) return false
        ok(response, "reading issue #$issue")
        return Json.parseToJsonElement(response.body()).jsonObject["state"]?.jsonPrimitive?.content == "open"
    }

    override fun create(title: String, body: String, labels: List<String>): Int {
        val json = buildJsonObject {
            put("title", title)
            put("body", body)
            put("labels", JsonArray(labels.map(::JsonPrimitive)))
        }
        val response = ok(send("POST", "/repos/$repo/issues", json.toString()), "opening an issue")
        return Json.parseToJsonElement(response.body()).jsonObject["number"]!!.jsonPrimitive.int
    }

    override fun comment(issue: Int, body: String) {
        val response = send("POST", "/repos/$repo/issues/$issue/comments", buildJsonObject { put("body", body) }.toString())
        // Locked (403), or gone since it was read: the report goes in a new issue instead.
        if (response.statusCode() in CLOSED_TO_COMMENTS) throw ReportForwarder.Gone("issue #$issue takes no comments (${response.statusCode()})")
        ok(response, "commenting on #$issue")
    }

    private fun send(method: String, path: String, json: String?): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create(api + path))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "osrsps-report-forwarder")
            .method(method, json?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
            .apply { if (json != null) header("Content-Type", "application/json") }
            .build()
        return try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            // Never connected, so nothing was sent; any later failure (a timeout, a reset) may have reached GitHub.
            if (e is java.net.ConnectException || e is java.net.UnknownHostException || e is java.net.http.HttpConnectTimeoutException) {
                throw ReportForwarder.NotPosted("couldn't reach GitHub (${e.javaClass.simpleName})", e)
            }
            throw e
        }
    }

    private fun ok(response: HttpResponse<String>, what: String): HttpResponse<String> {
        val status = response.statusCode()
        if (status in 200..299) return response
        // 400 and 422 are GitHub saying this request will never do (a body it won't take): not worth trying again.
        if (status == 400 || status == 422) throw ReportForwarder.Rejected("GitHub refused $what ($status)")
        throw ReportForwarder.NotPosted("GitHub answered $status $what")
    }

    companion object {
        const val API = "https://api.github.com"
        const val LAUNCHER_REPO = "SamSjosten/OdinsGate"
        const val REPO = "SamSjosten/OSRSPS"

        private val GONE = setOf(301, 404, 410)
        private val CLOSED_TO_COMMENTS = setOf(301, 403, 404, 410)

        /** The data folder's token file: one line, the token. Sam puts it there by hand; it is never in git. */
        const val TOKEN_FILE = "github-report-token.txt"

        /** What a GitHub token is made of; anything else (a second line, a space) would break or leak in a header. */
        private val TOKEN = Regex("[A-Za-z0-9_]{20,255}")

        /**
         * A tracker for the token in [file], or null when there is none yet or it isn't a token: one line of
         * letters, digits and underscores, as GitHub's are. What is wrong with a bad one is logged, never the token.
         */
        fun fromTokenFile(file: Path, repo: String = REPO): GitHubIssues? {
            val token = runCatching { Files.readString(file).trim() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
            if (!TOKEN.matches(token)) {
                logger.warn { "${file.fileName} isn't one line holding a GitHub token (letters, digits and _); reports wait" }
                return null
            }
            return GitHubIssues(repo, token)
        }
    }
}
