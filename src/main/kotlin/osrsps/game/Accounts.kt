package osrsps.game

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Who owns which name. The first login as a name claims it with the password it gave; later logins as that name must
 * give the same password. Names match the way saves do ([SaveStore.saveKey]), so "Zezima" and "zezima " are one
 * account. Our own rule, not live's: live accounts are made on the website and logged in to through Jagex's login
 * servers, which a test server has no part in.
 *
 * Only the salted PBKDF2 hash of a password is kept, one file per name in [store]. Used from the one login-auth
 * thread, never the game thread: a hash takes tens of milliseconds, which would stall a tick.
 */
class Accounts(
    private val store: SaveStore,
    /** PBKDF2 rounds for a new or changed password; an account keeps the count it was hashed with. */
    private val iterations: Int = ITERATIONS,
) {
    enum class Check {
        /** The name had no account; it is now claimed with this password. */
        CLAIMED,

        /** The password is the one the name was claimed with. */
        MATCHED,

        /** The name is claimed with another password. */
        WRONG_PASSWORD,

        /** Not a name a save can be kept under; the login refuses it on its own. */
        NO_SUCH_NAME,
    }

    private val salts = SecureRandom()

    /** Checks [password] for [username], claiming the name if nobody has. Throws if the account file can't be read or written. */
    fun check(username: String, password: CharArray): Check {
        val key = SaveStore.saveKey(username) ?: return Check.NO_SUCH_NAME
        val text = store.read(key)
        if (text == null) {
            val salt = ByteArray(SALT_BYTES).also(salts::nextBytes)
            store.write(key, toJson(username, iterations, salt, hash(password, salt, iterations)))
            return Check.CLAIMED
        }
        val account = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("Account file for '$key' is not a JSON object: ${e.message}", e)
        }
        val rounds = account["iterations"]!!.jsonPrimitive.int
        val salt = Base64.getDecoder().decode(account["salt"]!!.jsonPrimitive.content)
        val expected = Base64.getDecoder().decode(account["hash"]!!.jsonPrimitive.content)
        // Constant time, so how long a refusal takes says nothing about how much of the hash matched.
        return if (MessageDigest.isEqual(hash(password, salt, rounds), expected)) Check.MATCHED else Check.WRONG_PASSWORD
    }

    private fun toJson(username: String, rounds: Int, salt: ByteArray, hash: ByteArray): String = buildJsonObject {
        put("version", VERSION)
        // For whoever opens the file; the file name is what finds it.
        put("username", username)
        put("algorithm", ALGORITHM)
        put("iterations", rounds)
        put("salt", Base64.getEncoder().encodeToString(salt))
        put("hash", Base64.getEncoder().encodeToString(hash))
    }.toString()

    private fun hash(password: CharArray, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, rounds, HASH_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val VERSION = 1
        const val ALGORITHM = "PBKDF2WithHmacSHA256"

        /** OWASP's 2023 figure for PBKDF2-HMAC-SHA256. */
        const val ITERATIONS = 600_000
        const val SALT_BYTES = 16
        const val HASH_BITS = 256
    }
}
