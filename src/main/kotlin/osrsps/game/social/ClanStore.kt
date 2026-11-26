package osrsps.game.social

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

private val logger = KotlinLogging.logger {}

/**
 * Every clan, kept in [file] (data/clans.json on the server, outside git) and written whole after each change, to a
 * temporary file moved over it, so a crash mid-write loses nothing. Also which day each player last founded a clan
 * (live: one a day). Without a file (tests) clans last until the world stops. A file that can't be read stops the
 * server starting rather than starting with no clans and overwriting them at the first change.
 */
class ClanStore(private val file: Path? = null) {
    class Member(val key: String, var name: String, var rank: Int, val joinedDay: Int)

    /**
     * A player who applied to join (Apply on the clan's settings page as a guest), by save key and name. [stats] is what
     * the applicants page shows of them, as client script 4375 reads it after the name: combat level, total level, then
     * the 24 skill levels in its order, `|` between; empty if never taken (files from before it).
     */
    class Applicant(val key: String, var name: String, var stats: String = "")

    /** A player banned from the clan's channel (Ban guest), by save key and the name they had. */
    class Banned(val key: String, val name: String)

    class Clan(val id: Long, var name: String, val createdMillis: Long, val members: MutableList<Member>) {
        /** Applicants, oldest first. */
        val applicants = ArrayList<Applicant>()

        /** Clan setting vars set by its members (permissions), by var id; unset ones take their defaults. */
        val settings = HashMap<Int, Int>()

        /** Whether it is on the clan recruitment boards (List your clan). */
        var listed = false

        /** The coins in its coffer, in the clan hall. */
        var coffer = 0

        /** Its interests, a bit each in enum_3792's order; nothing sets them yet. */
        var interests = 0

        /** Players banned from its channel, oldest first. */
        val banned = ArrayList<Banned>()

        fun banned(key: String): Banned? = banned.firstOrNull { it.key == key }

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

    /** Every clan, oldest first. */
    fun all(): Collection<Clan> = clans.values

    /**
     * Sets the coins in [clan]'s coffer and saves at once: coins came out of or went into someone's inventory. When the
     * file can't be written the coffer stays as it was and this is false, so the coins don't move (exploit review R58C-2).
     */
    fun setCoffer(clan: Clan, coins: Int): Boolean {
        val before = clan.coffer
        clan.coffer = coins
        return when (write()) {
            Written.YES -> true
            Written.NO -> {
                clan.coffer = before
                false
            }
            // The file holds the new sum but its move may not have reached the disk (Codex on PR 966). Whichever way
            // that goes may only lose coins, never make them: a deposit keeps the new sum (the player's side is already
            // saved without the coins), and a withdrawal goes back to the old one and gives nothing. Either is written
            // again at the next flush.
            Written.UNSYNCED -> if (coins > before) {
                true
            } else {
                clan.coffer = before
                write()
                false
            }
        }
    }
    /** Puts [clan] on the recruitment boards or takes it off, written at the next [flush]. */
    fun setListed(clan: Clan, listed: Boolean) {
        clan.listed = listed
        unwritten = true
    }

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
    fun addApplicant(clan: Clan, key: String, name: String, stats: String = "") {
        if (clan.applicant(key) != null) return
        clan.applicants += Applicant(key, name, stats)
        unwritten = true
    }

    /** [applicant]'s levels as they are now, written at the next [flush] when they changed. */
    fun setApplicantStats(applicant: Applicant, stats: String) {
        if (applicant.stats == stats) return
        applicant.stats = stats
        unwritten = true
    }

    /** Takes [key] off every clan's applicants (they joined a clan), written at the next [flush]. */
    fun removeApplications(key: String) {
        for (clan in clans.values) if (clan.applicants.removeIf { it.key == key }) unwritten = true
    }

    /** Takes [key] off [clan]'s applicants, written at the next [flush] as [addApplicant]'s; false if they weren't one. */
    fun removeApplicant(clan: Clan, key: String): Boolean {
        if (!clan.applicants.removeIf { it.key == key }) return false
        unwritten = true
        return true
    }

    /**
     * Adds [member] to [clan], written at the next [flush] (the autosave), not now: joins and leaves can be repeated as
     * fast as Recruit and Leave are answered, and each write is the whole file (exploit review R53A-4).
     */
    fun addMember(clan: Clan, member: Member) {
        clan.members += member
        unwritten = true
        logger.info { "${member.key} joined clan '${clan.name}'" }
    }

    /**
     * Bans [key] from [clan]'s channel, unless they are banned already, written at the next [flush] (the autosave), not
     * now: a ban and unban can be repeated as fast as they click, and each write is the whole file.
     */
    fun addBanned(clan: Clan, key: String, name: String) {
        if (clan.banned(key) != null) return
        clan.banned += Banned(key, name)
        unwritten = true
        logger.info { "$key banned from the channel of clan '${clan.name}'" }
    }

    /** Lifts [key]'s ban from [clan]'s channel, written at the next [flush] as [addBanned]'s; false if they weren't banned. */
    fun removeBanned(clan: Clan, key: String): Boolean {
        if (!clan.banned.removeIf { it.key == key }) return false
        unwritten = true
        logger.info { "$key unbanned from the channel of clan '${clan.name}'" }
        return true
    }

    /** Sets [clan]'s setting var [id] to [value], written at the next [flush]. */
    fun setSetting(clan: Clan, id: Int, value: Int) {
        clan.settings[id] = value
        unwritten = true
    }

    /**
     * Gives [member] of [clan] [rank], written at the next [flush] (the autosave), not now: an editor can pick ranks as
     * fast as they click, and each write is the whole file.
     */
    fun setRank(clan: Clan, member: Member, rank: Int) {
        member.rank = rank
        unwritten = true
        logger.info { "${member.key} is now rank $rank in clan '${clan.name}'" }
    }

    /** Takes [key] out of [clan]'s members, written at the next [flush] as [addMember]'s; false if they weren't one. */
    fun removeMember(clan: Clan, key: String): Boolean {
        if (!clan.members.removeIf { it.key == key }) return false
        unwritten = true
        logger.info { "$key left clan '${clan.name}'" }
        return true
    }

    /** Writes again after a failed write: the world's autosave and shutdown call it. */
    fun flush() {
        if (unwritten) save()
    }

    /**
     * Writes every clan, forced to the disk as player saves are (exploit review R58C-2), and whether it was written; a
     * failure is logged and the clans stay as they are in the game, written at the next change or the next [flush].
     */
    fun save(): Boolean = write() == Written.YES

    /** How far a write got: [Written.UNSYNCED] is a file moved into place whose move couldn't be forced to the disk. */
    private enum class Written { YES, NO, UNSYNCED }

    private fun write(): Written {
        val file = file ?: return Written.YES
        unwritten = true
        val dir = file.toAbsolutePath().parent
        try {
            Files.createDirectories(dir)
            val temp = file.resolveSibling("${file.fileName}.tmp")
            FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val bytes = ByteBuffer.wrap(format.encodeToString(JsonObject.serializer(), toJson()).toByteArray(Charsets.UTF_8))
                while (bytes.hasRemaining()) channel.write(bytes)
                channel.force(true)
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            logger.error(e) { "Couldn't write ${file.fileName}; trying again at the next autosave" }
            return Written.NO
        }
        // The move itself: Linux opens a folder for this, Windows doesn't, so it's skipped only when the folder can't
        // be opened. A failed force leaves the write unsure (Codex on PR 966).
        try {
            val folder = try {
                FileChannel.open(dir, StandardOpenOption.READ)
            } catch (_: IOException) {
                null
            }
            folder?.use { it.force(true) }
        } catch (e: Exception) {
            logger.error(e) { "Couldn't force ${file.fileName} to the disk; writing it again at the next autosave" }
            return Written.UNSYNCED
        }
        unwritten = false
        return Written.YES
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
                    putJsonObject("settings") { for ((id, value) in clan.settings) put(id.toString(), value) }
                    if (clan.listed) put("listed", true)
                    if (clan.coffer != 0) put("coffer", clan.coffer)
                    if (clan.interests != 0) put("interests", clan.interests)
                    putJsonArray("applicants") {
                        for (applicant in clan.applicants) {
                            addJsonObject {
                                put("key", applicant.key)
                                put("name", applicant.name)
                                if (applicant.stats.isNotEmpty()) put("stats", applicant.stats)
                            }
                        }
                    }
                    putJsonArray("banned") {
                        for (banned in clan.banned) {
                            addJsonObject {
                                put("key", banned.key)
                                put("name", banned.name)
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
            // Files from before settings, applications or bans have none.
            for ((id, value) in obj["settings"]?.jsonObject.orEmpty()) clan.settings[id.toInt()] = value.jsonPrimitive.int
            clan.listed = obj["listed"]?.jsonPrimitive?.boolean ?: false
            clan.coffer = obj["coffer"]?.jsonPrimitive?.int ?: 0
            clan.interests = obj["interests"]?.jsonPrimitive?.int ?: 0
            for (a in obj["applicants"]?.jsonArray.orEmpty()) {
                val o = a.jsonObject
                clan.applicants += Applicant(o["key"]!!.jsonPrimitive.content, o["name"]!!.jsonPrimitive.content, o["stats"]?.jsonPrimitive?.content.orEmpty())
            }
            for (b in obj["banned"]?.jsonArray.orEmpty()) {
                val o = b.jsonObject
                clan.banned += Banned(o["key"]!!.jsonPrimitive.content, o["name"]!!.jsonPrimitive.content)
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
