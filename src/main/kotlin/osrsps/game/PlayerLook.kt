package osrsps.game

/**
 * How a player looks under what they wear: body type A (0) or B (1), an identity kit for each of the seven body parts
 * ([BodyPart]: hair, jaw, torso, arms, hands, legs, feet; -1 for none, as body type B's jaw) and the five colours
 * ([ColourPart]: hair, torso, legs, feet, skin). Saved with the account and sent in the appearance block at login.
 * The makeover screens and the design screen at a new account's first login change it.
 */
data class PlayerLook(val bodyType: Int, val kits: List<Int>, val colours: List<Int>, val pronoun: Int = bodyType) {
    init {
        require(bodyType == BODY_TYPE_A || bodyType == BODY_TYPE_B) { "Body type $bodyType is neither A (0) nor B (1)" }
        require(kits.size == BodyPart.COUNT) { "${kits.size} identity kits, not ${BodyPart.COUNT}" }
        require(colours.size == ColourPart.entries.size) { "${colours.size} colours, not ${ColourPart.entries.size}" }
        for (kit in kits) require(kit == -1 || kit in 0 until MAX_KIT) { "Identity kit $kit is out of range" }
        for ((part, colour) in ColourPart.entries.zip(colours)) {
            require(colour in 0 until part.count) { "${part.name.lowercase()} colour $colour is out of range (0 to ${part.count - 1})" }
        }
        require(pronoun in 0 until PRONOUNS) { "Pronoun $pronoun is out of range" }
    }

    fun withKit(part: Int, kit: Int) = copy(kits = kits.toMutableList().also { it[part] = kit })

    fun withColour(part: ColourPart, colour: Int) = copy(colours = colours.toMutableList().also { it[part.ordinal] = colour })

    /** The seven body parts' order in the appearance block's identity kits. */
    object BodyPart {
        const val HAIR = 0

        /** The jaw, the one part a look can leave empty (kit -1). */
        const val JAW = 1
        const val TORSO = 2
        const val ARMS = 3
        const val HANDS = 4
        const val LEGS = 5
        const val FEET = 6
        const val COUNT = 7

        val NAMES = listOf("hair", "jaw", "torso", "arms", "hands", "legs", "feet")

        /**
         * The identity kit type's body part (idk opcode 1) for [part] of [bodyType]: body type B's kits are numbered 7
         * to 13 (the 242 cache's 307 kits: 0 to 6 with body type A's styles, 7 to 13 with B's).
         */
        fun kitBodyPart(part: Int, bodyType: Int) = part + bodyType * COUNT
    }

    /**
     * The five colours, with how many choices each has: hair 30 and torso and legs 29 each (the makeover screen's
     * colour enums 890, 789 and 791 in the 242 cache), feet 6 (enum 5290) and skin 14 (the OSRS Wiki's Makeover page,
     * read 2026-11-19: 14 skin colours, some unlocked by events).
     */
    enum class ColourPart(val count: Int) { HAIR(30), TORSO(29), LEGS(29), FEET(6), SKIN(14) }

    companion object {
        const val BODY_TYPE_A = 0
        const val BODY_TYPE_B = 1

        /** RSProt sends an identity kit as its id + 0x100 in two bytes, and takes ids below 1,792. */
        const val MAX_KIT = 1792

        /** He/him, she/her and they/them (the settings' pronoun choices; enum 5500 names the first two). */
        const val PRONOUNS = 3

        /**
         * The look every account had before looks were kept: body type A in its first kit of each part, every colour
         * the first. Guessed, not checked against live. Accounts saved before then load with it, so nobody changes.
         */
        val DEFAULT = PlayerLook(BODY_TYPE_A, listOf(0, 10, 18, 26, 33, 36, 42), listOf(0, 0, 0, 0, 0))
    }
}
