package osrsps.game.social

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private val logger = KotlinLogging.logger {}

/**
 * Every clan, kept in [file] (data/clans.json on the server, outside git) and written whole after each change, to a
 * temporary file moved over it, so a crash mid-write loses nothing. Also which day each player last founded a clan
 * (live: one a day). Without a file (tests) clans last until the world stops. A file that can't be read stops the
 * server starting rather than starting with no clans and overwriting them at the first change.
 */
class ClanStore(private val file: Path? = null) {
    class Member(val key: String, var name: String, var rank: Int, val joinedDay: Int)

    /** A player who applied to join (Apply on the clan's settings page as a guest), by save key and name. */
    class Applicant(val key: String, var name: String)

    class Clan(val id: Long, var name: String, val createdMillis: Long, val members: MutableList<Member>) {
        /** Applicants, oldest first. */
        val applicants = ArrayList<Applicant>()

        fun member(key: String): Member? = members.firstOrNull { it.key == key }

        fun applicant(key: String): Applicant? = applicants.firstOrNull { it.key == key }
    }

    private val clans = LinkedHashMap<Long, Clan>()
    private val founded = HashMap<String, Long>()
    private var nextId = 1L

    /** Set while the clans in the game differ from the file (a write failed, or applicants changed); [flush] writes them. */
    var unwritten = false
        private set

    init {
        if (file != null && Files.exists(file)) load(file)
    }

    /** The clan [key] (a save key) is a member of, or null. */
    fun clanOf(key: String): Clan? = clans.values.firstOrNull { it.member(key) != null }

    /** Whether a clan already has [name], compared as player names are (case, and spaces against underscores). */
    fun nameTaken(name: String): Boolean = clans.values.any { sameName(it.name, name) }

    /** The clan named [name], compared as [nameTaken] does, or null. */
    fun byName(name: String): Clan? = clans.values.firstOrNull { sameName(it.name, name) }

    /** The clan with [id], or null once it is deleted. */
    fun byId(id: Long): Clan? = clans[id]

    /** The day (days since 1970) [key] last founded a clan, or null. */
    fun foundedDay(key: String): Long? = founded[key]

    /** Makes a clan of [members], the first its owner, and saves. */
    fun create(name: String, createdMillis: Long, day: Long, members: List<Member>): Clan {
        val clan = Clan(nextId++, name, createdMillis, members.toMutableList())
        clans[clan.id] = clan
        founded[members.first().key] = day
        save()
        logger.info { "Clan '$name' founded by ${members.joinToString { it.name }}" }
        return clan
    }

    /** Deletes [clan] and saves; its members may found a clan again today (`::clandisband` is for test scripts). */
    fun delete(clan: Clan) {
        clans.remove(clan.id)
        // For the scripts that found a clan again: its members may found one today.
        for (member in clan.members) founded.remove(member.key)
        save()
        logger.info { "Clan '${clan.name}' deleted" }
    }

    /**
     * Adds [key] to [clan]'s applicants, unless already there. Written at the next [flush] (the autosave), not now: a
     * player can apply and withdraw as fast as they click, and each write is the whole file.
     */
    fun addApplicant(clan: Clan, key: String, name: String) {
        if (clan.applicant(key) != null) return
        clan.applicants += Applicant(key, name)
        unwritten = true
    }

    /** Takes [key] off [clan]'s applicants, written at the next [flush] as [addApplicant]'s; false if they weren't one. */
    fun removeApplicant(clan: Clan, key: String): Boolean {
        if (!clan.applicants.removeIf { it.key == key }) return false
        unwritten = true
        return true
    }

    /** Adds [member] to [clan] and saves (`::clanjoin`, for test scripts). */
    fun addMember(clan: Clan, member: Member) {
        clan.members += member
        save()
        logger.info { "${member.key} joined clan '${clan.name}'" }
    }

    /** Takes [key] out of [clan]'s members and saves; false if they weren't one. */
    fun removeMember(clan: Clan, key: String): Boolean {
        if (!clan.members.removeIf { it.key == key }) return false
        save()
        logger.info { "$key left clan '${clan.name}'" }
        return true
    }

    /** Writes again after a failed write: the world's autosave and shutdown call it. */
    fun flush() {
        if (unwritten) save()
    }

    /**
     * Writes every clan; a failure is logged and the clans stay as they are in the game, written at the next change or
     * the next [flush].
     */
    fun save() {
        val file = file ?: return
        unwritten = true
        try {
            file.parent?.let(Files::createDirectories)
            val temp = file.resolveSibling("${file.fileName}.tmp")
            Files.writeString(temp, format.encodeToString(JsonObject.serializer(), toJson()))
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            unwritten = false
        } catch (e: Exception) {
            logger.error(e) { "Couldn't write ${file.fileName}; trying again at the next autosave" }
        }
    }

    private fun toJson(): JsonObject = buildJsonObject {
        put("nextId", nextId)
        putJsonArray("clans") {
            for (clan in clans.values) {
                addJsonObject {
                    put("id", clan.id)
                    put("name", clan.name)
                    put("created", clan.createdMillis)
                    putJsonArray("members") {
                        for (member in clan.members) {
                            addJsonObject {
                                put("key", member.key)
                                put("name", member.name)
                                put("rank", member.rank)
                                put("joined", member.joinedDay)
                            }
                        }
                    }
                    putJsonArray("applicants") {
                        for (applicant in clan.applicants) {
                            addJsonObject {
                                put("key", applicant.key)
                                put("name", applicant.name)
                            }
                        }
                    }
                }
            }
        }
        putJsonObject("founded") { for ((key, day) in founded) put(key, day) }
    }

    private fun load(file: Path) {
        val root = Json.parseToJsonElement(Files.readString(file)).jsonObject
        nextId = root["nextId"]?.jsonPrimitive?.long ?: 1L
        for (element in root["clans"]?.jsonArray.orEmpty()) {
            val obj = element.jsonObject
            val members = obj["members"]!!.jsonArray.map {
                val m = it.jsonObject
                Member(m["key"]!!.jsonPrimitive.content, m["name"]!!.jsonPrimitive.content, m["rank"]!!.jsonPrimitive.int, m["joined"]!!.jsonPrimitive.int)
            }
            val clan = Clan(obj["id"]!!.jsonPrimitive.long, obj["name"]!!.jsonPrimitive.content, obj["created"]!!.jsonPrimitive.long, members.toMutableList())
            // Files from before applications have none.
            for (a in obj["applicants"]?.jsonArray.orEmpty()) {
                val o = a.jsonObject
                clan.applicants += Applicant(o["key"]!!.jsonPrimitive.content, o["name"]!!.jsonPrimitive.content)
            }
            clans[clan.id] = clan
            if (clan.id >= nextId) nextId = clan.id + 1
        }
        for ((key, day) in root["founded"]?.jsonObject.orEmpty()) founded[key] = day.jsonPrimitive.long
        logger.info { "Loaded ${clans.size} clans from ${file.fileName}" }
    }

    companion object {
        const val FILE_NAME = "clans.json"
        private val format = Json { prettyPrint = true }

        fun sameName(a: String, b: String): Boolean = normal(a) == normal(b)

        private fun normal(name: String) = name.trim().lowercase().replace('_', ' ').replace(SPACES, " ")

        private val SPACES = Regex("\\s+")
    }
}
