package osrsps

import kotlin.test.Test
import kotlin.test.assertEquals

class RevisionTest {
    @Test
    fun `targets live revision 241`() {
        assertEquals(241, Revision.MAJOR)
    }
}
