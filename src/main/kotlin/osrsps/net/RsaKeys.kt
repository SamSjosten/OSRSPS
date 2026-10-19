package osrsps.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.crypto.rsa.RsaKeyPair
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec

private val logger = KotlinLogging.logger {}

/**
 * The server's login RSA key. Generated once into the data folder (never committed), with the modulus
 * written next to it in the hex form RSProx's `proxy-targets.yaml` expects.
 */
object RsaKeys {
    const val BITS = 1024

    fun loadOrCreate(dataDir: Path): RsaKeyPair {
        val keyFile = dataDir.resolve("rsa.key")
        val key = if (Files.exists(keyFile)) {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Files.readAllBytes(keyFile))) as RSAPrivateCrtKey
        } else {
            generate().also {
                Files.createDirectories(dataDir)
                Files.write(keyFile, it.encoded)
                logger.info { "Generated a new $BITS-bit RSA key in $keyFile" }
            }
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
