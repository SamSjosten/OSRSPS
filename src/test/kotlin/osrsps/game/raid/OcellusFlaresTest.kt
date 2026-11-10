package osrsps.game.raid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import osrsps.game.combat.AttackType
import osrsps.game.combat.SureHit

class OcellusFlaresTest {
    @Test
    fun `each void flare is maxed by its style, the Siphon by ranged, the Augmenter by magic, the Rift by melee always and the rest when they land`() {
        val melee = listOf(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH)
        for (type in melee) {
            assertEquals(SureHit.MAX, Ocellus.flareHit(Ocellus.Flare.RIFT.id, type))
            assertEquals(SureHit.ROLLED, Ocellus.flareHit(Ocellus.Flare.SIPHON.id, type))
            assertEquals(SureHit.ROLLED, Ocellus.flareHit(Ocellus.Flare.AUGMENTER.id, type))
        }
        assertEquals(SureHit.MAX_IF_HIT, Ocellus.flareHit(Ocellus.Flare.RIFT.id, AttackType.RANGED))
        assertEquals(SureHit.MAX_IF_HIT, Ocellus.flareHit(Ocellus.Flare.RIFT.id, AttackType.MAGIC))
        assertEquals(SureHit.MAX_IF_HIT, Ocellus.flareHit(Ocellus.Flare.SIPHON.id, AttackType.RANGED))
        assertEquals(SureHit.ROLLED, Ocellus.flareHit(Ocellus.Flare.SIPHON.id, AttackType.MAGIC))
        assertEquals(SureHit.MAX_IF_HIT, Ocellus.flareHit(Ocellus.Flare.AUGMENTER.id, AttackType.MAGIC))
        assertEquals(SureHit.ROLLED, Ocellus.flareHit(Ocellus.Flare.AUGMENTER.id, AttackType.RANGED))
        // Nothing else is touched.
        assertNull(Ocellus.flareHit(Ocellus.P3, AttackType.SLASH))
        assertNull(Ocellus.flareHit(Ocellus.VINE, AttackType.RANGED))
    }
}
