package osrsps.game

/** An identity kit: one style of a body part (a hairstyle, a torso). [bodyPart] is 0 to 6 for body type A, 7 to 13 for B. */
class IdkType(val id: Int, val bodyPart: Int, val selectable: Boolean)

/** The cache's identity kits, by id. */
class IdkTypes(private val types: Array<IdkType?>) {
    operator fun get(id: Int): IdkType? = types.getOrNull(id)

    val size: Int get() = types.count { it != null }

    /** Whether [kit] can be [part] of a body type [bodyType] look: -1 (none) only for the jaw, as body type B has none. */
    fun fits(kit: Int, part: Int, bodyType: Int): Boolean {
        if (kit == -1) return part == PlayerLook.BodyPart.JAW
        return get(kit)?.bodyPart == PlayerLook.BodyPart.kitBodyPart(part, bodyType)
    }

    /**
     * The first selectable kit of each body part for [bodyType], null when the cache lacks one; body type B gets no jaw.
     * For body type A that is [PlayerLook.DEFAULT]'s kits. Guessed as the look a body type change starts from.
     */
    fun firstKits(bodyType: Int): List<Int>? = (0 until PlayerLook.BodyPart.COUNT).map { part ->
        if (bodyType == PlayerLook.BODY_TYPE_B && part == PlayerLook.BodyPart.JAW) return@map -1
        val bodyPart = PlayerLook.BodyPart.kitBodyPart(part, bodyType)
        types.firstOrNull { it != null && it.selectable && it.bodyPart == bodyPart }?.id ?: return null
    }

    companion object {
        val NONE = IdkTypes(emptyArray())
    }
}
