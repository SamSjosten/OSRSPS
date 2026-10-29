package osrsps.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.crypto.rsa.RsaKeyPair
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec

private val logger = KotlinLogging.logger {}

/**
 * The server's login RSA key. Generated once into the data folder (never committed), with the modulus
 * written next to it in the hex form RSProx's `proxy-targets.yaml` expects. The live server's public modulus is
 * committed as well ([COMMITTED_MODULUS]): our launcher's releases carry it, so a live server with another key would
 * turn every friend away.
 */
object RsaKeys {
    const val BITS = 1024

    /** The resource holding the live server's public modulus in hex, as data/rsa-modulus.txt has it. */
    const val COMMITTED_MODULUS = "/rsa-modulus.txt"

    private val HEX = Regex("[0-9a-f]+")

    /** The live server's public modulus, from [COMMITTED_MODULUS]. */
    fun committedModulus(): BigInteger {
        val text = RsaKeys::class.java.getResource(COMMITTED_MODULUS)?.readText()?.trim()
            ?: error("No $COMMITTED_MODULUS in the resources")
        require(HEX.matches(text)) { "$COMMITTED_MODULUS isn't one line of lowercase hex" }
        return BigInteger(text, 16).also {
            require(it.bitLength() == BITS) { "$COMMITTED_MODULUS is a ${it.bitLength()}-bit modulus, not $BITS" }
        }
    }

    /**
     * The key in [dataDir]/rsa.key, its modulus written next to it. With [expected] (the committed modulus, on the live
     * server) the key must be there with that modulus, or this throws: a lost or replaced key stops the start instead
     * of quietly shutting every friend's launcher out with a new one. Changing the key is a reviewed commit of its
     * modulus. Without (a local server, tests), a missing key is made.
     */
    fun load(dataDir: Path, expected: BigInteger? = null): RsaKeyPair {
        val keyFile = dataDir.resolve("rsa.key")
        val key = if (Files.exists(keyFile)) {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Files.readAllBytes(keyFile))) as RSAPrivateCrtKey
        } else {
            check(expected == null) {
                "No $keyFile. Put the live server's key back there; a new key would lock out every friend's launcher. " +
                    "Set OSRSPS_LOCAL_KEY=1 to run a local server with a key of its own."
            }
            generate().also {
                Files.createDirectories(dataDir)
                Files.write(keyFile, it.encoded)
                logger.info { "Generated a new $BITS-bit RSA key in $keyFile" }
            }
        }
        if (expected != null) {
            check(key.modulus == expected) {
                "$keyFile isn't the key whose modulus is committed in src/main/resources$COMMITTED_MODULUS, which " +
                    "friends' launchers carry. Put the live key back, or change that file in a reviewed commit."
            }
            logger.info { "The RSA key matches the committed modulus" }
        }
        Files.writeString(dataDir.resolve("rsa-modulus.txt"), modulusHex(key) + "\n")
        return RsaKeyPair(key.privateExponent, key.modulus)
    }

    fun generate(): RSAPrivateCrtKey {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(BITS)
        return generator.generateKeyPair().private as RSAPrivateCrtKey
    }

    fun modulusHex(key: RSAPrivateCrtKey): String = key.modulus.toString(16)
}
