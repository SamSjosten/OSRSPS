package osrsps.net

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelayAwareValidatorTest {
    private val validator = RelayAwareValidator(setOf("192.168.0.216"))

    @Test
    fun `an ordinary address keeps RSProt's limit of 10`() {
        assertTrue(validator.acceptGameConnection("192.168.0.50", 9))
        assertFalse(validator.acceptGameConnection("192.168.0.50", 10))
        assertFalse(validator.acceptJs5Connection("192.168.0.50", 10, IntArray(4)))
    }

    @Test
    fun `a relay holds a full world`() {
        assertTrue(validator.acceptGameConnection("192.168.0.216", 10))
        assertTrue(validator.acceptJs5Connection("192.168.0.216", 2000, IntArray(4)))
        assertFalse(validator.acceptGameConnection("192.168.0.216", RelayAwareValidator.RELAY_LIMIT))
    }
}
