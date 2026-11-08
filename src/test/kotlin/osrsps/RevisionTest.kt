package osrsps

import kotlin.test.Test
import kotlin.test.assertEquals

class RevisionTest {
    @Test
    fun `targets live revision 242`() {
        assertEquals(242, Revision.MAJOR)
    }
}
