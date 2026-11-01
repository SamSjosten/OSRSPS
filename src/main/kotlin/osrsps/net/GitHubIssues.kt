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
        val response = ok(send("GET", "/repos/$repo/issues/$issue", null), "reading issue #$issue")
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
        ok(send("POST", "/repos/$repo/issues/$issue/comments", buildJsonObject { put("body", body) }.toString()), "commenting on #$issue")
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
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun ok(response: HttpResponse<String>, what: String): HttpResponse<String> {
        if (response.statusCode() !in 200..299) throw IOException("GitHub answered ${response.statusCode()} $what")
        return response
    }

    companion object {
        const val API = "https://api.github.com"
        const val LAUNCHER_REPO = "SamSjosten/OdinsGate"
        const val REPO = "SamSjosten/OSRSPS"

        /** The data folder's token file: one line, the token. Sam puts it there by hand; it is never in git. */
        const val TOKEN_FILE = "github-report-token.txt"

        /** A tracker for the token in [file], or null when there is none yet. */
        fun fromTokenFile(file: Path, repo: String = REPO): GitHubIssues? {
            val token = runCatching { Files.readString(file).trim() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
            return GitHubIssues(repo, token)
        }
    }
}
