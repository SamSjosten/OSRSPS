package osrsps.game

/**
 * Equipment slots as the cache numbers them in an item's wearpos, which are also the slots of the worn inventory
 * (inv 94, gameval `worn`). Arms (6) and hair (8) never hold an item: they are only covered, by bodies and helms.
 * Jaw (11) holds the Barbarian Assault icons and isn't shown in the equipment tab.
 */
object WearPos {
    const val HEAD = 0
    const val CAPE = 1
    const val NECK = 2
    const val WEAPON = 3
    const val BODY = 4
    const val SHIELD = 5
    const val ARMS = 6
    const val LEGS = 7
    const val HAIR = 8
    const val HANDS = 9
    const val FEET = 10
    const val JAW = 11
    const val RING = 12
    const val AMMO = 13
    const val COUNT = 14
}

/** A skill level needed to wear an item. */
data class WearRequirement(val skill: Skill, val level: Int)

/** What [Equipment.wear] or [Equipment.remove] did. */
sealed interface EquipResult {
    data object Done : EquipResult

    /** Nothing happened: the slot didn't hold what the click said, or the item can't be worn. */
    data object Ignored : EquipResult

    data class LevelTooLow(val requirement: WearRequirement) : EquipResult

    data object NoSpace : EquipResult
}

/**
 * Wearing and removing items: moving them between the inventory and the worn inventory with the slot, level and
 * two-handed rules. Telling the player and updating their model is left to the caller ([EquipmentTab]).
 */
class Equipment(val objTypes: ObjTypes) {
    /**
     * Wears the item in inventory [slot]. What it replaces goes into the inventory slot it leaves; a two-handed weapon
     * worn over both a weapon and a shield needs one more free slot for the shield. Levels are checked against base
     * levels, so boosts don't count. Both rules and the slots things land in are from memory of live, not checked
     * against a recording.
     */
    fun wear(player: Player, slot: Int): EquipResult {
        val inv = player.inventory
        val held = inv[slot] ?: return EquipResult.Ignored
        val type = objTypes[held.id] ?: return EquipResult.Ignored
        if (!type.wearable) return EquipResult.Ignored
        val missing = requirements(type).firstOrNull { player.skills.baseLevel(it.skill) < it.level }
        if (missing != null) return EquipResult.LevelTooLow(missing)

        val worn = player.equipment
        val pos = type.wearpos1
        val current = worn[pos]
        // Inferred: a stackable item worn on a stack of the same item (arrows on arrows) joins the stack.
        if (current != null && current.id == held.id && type.stackable) {
            val room = Inventory.MAX_STACK - current.count
            if (room <= 0) return EquipResult.NoSpace
            val moved = minOf(room, held.count)
            inv.remove(slot, moved)
            worn[pos] = current.copy(count = current.count + moved)
            return EquipResult.Done
        }
        val displaced = displacedBy(type, worn).mapNotNull { wearpos -> worn[wearpos]?.let { wearpos to it } }
        // The item's own slot takes the first displaced item; the rest need free slots.
        if (displaced.size - 1 > inv.freeSlots) return EquipResult.NoSpace
        inv[slot] = null
        worn[pos] = held
        for ((index, entry) in displaced.withIndex()) {
            val (wearpos, obj) = entry
            if (wearpos != pos) worn[wearpos] = null
            if (index == 0) inv[slot] = obj else inv[inv.firstFree()] = obj
        }
        return EquipResult.Done
    }

    /** Takes off what is worn in [wearpos] and puts it in the inventory, onto its stack if it stacks. */
    fun remove(player: Player, wearpos: Int): EquipResult {
        if (wearpos !in 0 until WearPos.COUNT) return EquipResult.Ignored
        val worn = player.equipment
        val obj = worn[wearpos] ?: return EquipResult.Ignored
        val type = objTypes[obj.id]
        val inv = player.inventory
        if (type != null && type.stackable) {
            val stackSlot = inv.contents().firstOrNull { it.second.id == obj.id }
            if (stackSlot != null) {
                val (invSlot, stack) = stackSlot
                // Inferred: a stack that would overflow stays worn, as a full inventory does.
                if (stack.count.toLong() + obj.count > Inventory.MAX_STACK) return EquipResult.NoSpace
                inv[invSlot] = stack.copy(count = stack.count + obj.count)
                worn[wearpos] = null
                return EquipResult.Done
            }
        }
        if (inv.freeSlots == 0) return EquipResult.NoSpace
        inv[inv.firstFree()] = obj
        worn[wearpos] = null
        return EquipResult.Done
    }

    /**
     * The worn slots that have to be emptied to wear [type], its own first: a two-handed weapon also takes the shield
     * off, and a shield takes off a two-handed weapon. Other covered slots (hair, jaw, arms) only change the model.
     */
    private fun displacedBy(type: ObjType, worn: Inventory): List<Int> {
        val slots = mutableListOf(type.wearpos1)
        if (type.twoHanded) slots += WearPos.SHIELD
        if (type.wearpos1 == WearPos.SHIELD) {
            val weapon = worn[WearPos.WEAPON]?.let { objTypes[it.id] }
            if (weapon != null && weapon.twoHanded) slots += WearPos.WEAPON
        }
        return slots
    }

    /** The bonuses of everything worn, added up. */
    fun bonuses(player: Player): EquipmentBonuses {
        val totals = IntArray(EquipmentBonuses.PARAMS.size)
        for ((_, obj) in player.equipment.contents()) {
            val type = objTypes[obj.id] ?: continue
            for ((index, param) in EquipmentBonuses.PARAMS.withIndex()) totals[index] += type.intParam(param)
            totals[EquipmentBonuses.RANGED_STRENGTH] += type.intParam(EquipmentBonuses.PARAM_GEAR_RANGED_STRENGTH)
        }
        val weapon = player.equipment[WearPos.WEAPON]?.let { objTypes[it.id] }
        val speed = weapon?.intParam(PARAM_ATTACK_SPEED, UNARMED_ATTACK_SPEED) ?: UNARMED_ATTACK_SPEED
        return EquipmentBonuses(totals, speed)
    }

    /** The worn weapon's type, or null when unarmed. */
    fun weapon(player: Player): ObjType? = player.equipment[WearPos.WEAPON]?.let { objTypes[it.id] }

    companion object {
        /** Inventory options that wear the item when no content handler takes them. */
        val WEAR_OPTIONS = setOf("Wear", "Wield", "Equip")

        /**
         * Requirement params as RSMod names them (statreq1_skill, statreq2_skill, statreq1_level, statreq2_level):
         * the skill's id and the level. Checked in the 241 cache: the abyssal whip has 0 (Attack) and 70, the
         * granite maul 0/50 and 2 (Strength)/50.
         */
        const val PARAM_REQ1_SKILL = 434
        const val PARAM_REQ2_SKILL = 435
        const val PARAM_REQ1_LEVEL = 436
        const val PARAM_REQ2_LEVEL = 437

        /** Ticks between attacks; a bronze sword has 4, a bronze 2h sword 7 in the cache. */
        const val PARAM_ATTACK_SPEED = 14

        /** Unarmed attacks every 4 ticks (OSRS Wiki, from memory). */
        const val UNARMED_ATTACK_SPEED = 4

        /**
         * Metal tiers by gameval prefix and the level their weapons (Attack) and armour (Defence) need. The 241 cache
         * only carries requirement params on some items (rune scimitar, not rune dagger; no mithril item at all), so
         * the rest of the standard metal sets come from this rule. From memory of the OSRS Wiki, not checked.
         * Halberds are left out: they also need Strength.
         */
        private val METAL_TIERS = mapOf("steel" to 5, "black" to 10, "mithril" to 20, "adamant" to 30, "rune" to 40, "dragon" to 60)
        private val METAL_WEAPONS = setOf(
            "dagger", "sword", "longsword", "scimitar", "2h_sword", "mace", "warhammer", "battleaxe", "axe", "pickaxe",
            "spear", "claws",
        )
        private val METAL_ARMOUR = setOf(
            "full_helm", "med_helm", "chainbody", "platebody", "platelegs", "plateskirt", "sq_shield", "kiteshield",
        )

        /** The levels needed to wear [type]: from its params when it has them, else from the metal tier rule. */
        fun requirements(type: ObjType): List<WearRequirement> {
            val fromParams = listOfNotNull(
                requirement(type, PARAM_REQ1_SKILL, PARAM_REQ1_LEVEL),
                requirement(type, PARAM_REQ2_SKILL, PARAM_REQ2_LEVEL),
            )
            if (fromParams.isNotEmpty()) return fromParams
            val name = type.internalName ?: return emptyList()
            val metal = name.substringBefore('_')
            val level = METAL_TIERS[metal] ?: return emptyList()
            return when (name.substringAfter('_')) {
                in METAL_WEAPONS -> listOf(WearRequirement(Skill.ATTACK, level))
                in METAL_ARMOUR -> listOf(WearRequirement(Skill.DEFENCE, level))
                else -> emptyList()
            }
        }

        private fun requirement(type: ObjType, skillParam: Int, levelParam: Int): WearRequirement? {
            val skillId = type.params[skillParam] as? Int ?: return null
            val level = type.intParam(levelParam)
            val skill = Skill.entries.getOrNull(skillId) ?: return null
            return if (level > 1) WearRequirement(skill, level) else null
        }
    }
}

private fun Inventory.firstFree(): Int = (0 until capacity).first { this[it] == null }

/**
 * Equipment bonuses, in the order of [PARAMS]. The param ids were matched to bonuses against items whose stats are
 * well known (bronze sword: stab 4, slash 3, crush -2; wooden shield; occult necklace 5% magic damage as 50; rune
 * arrow ranged strength 49), so the mapping is checked against the 241 cache, the stats themselves from memory. Ranged
 * strength has a second param for gear, [PARAM_GEAR_RANGED_STRENGTH] (found by the item audit against the Wiki).
 */
class EquipmentBonuses(private val values: IntArray, val attackSpeed: Int) {
    operator fun get(index: Int): Int = values[index]

    val stabAttack get() = values[STAB_ATTACK]
    val slashAttack get() = values[SLASH_ATTACK]
    val crushAttack get() = values[CRUSH_ATTACK]
    val magicAttack get() = values[MAGIC_ATTACK]
    val rangedAttack get() = values[RANGED_ATTACK]
    val stabDefence get() = values[STAB_DEFENCE]
    val slashDefence get() = values[SLASH_DEFENCE]
    val crushDefence get() = values[CRUSH_DEFENCE]
    val magicDefence get() = values[MAGIC_DEFENCE]
    val rangedDefence get() = values[RANGED_DEFENCE]
    val meleeStrength get() = values[MELEE_STRENGTH]
    val rangedStrength get() = values[RANGED_STRENGTH]

    /** In tenths of a percent: 50 is +5%. */
    val magicDamage get() = values[MAGIC_DAMAGE]
    val prayer get() = values[PRAYER]

    companion object {
        const val STAB_ATTACK = 0
        const val SLASH_ATTACK = 1
        const val CRUSH_ATTACK = 2
        const val MAGIC_ATTACK = 3
        const val RANGED_ATTACK = 4
        const val STAB_DEFENCE = 5
        const val SLASH_DEFENCE = 6
        const val CRUSH_DEFENCE = 7
        const val MAGIC_DEFENCE = 8
        const val RANGED_DEFENCE = 9
        const val MELEE_STRENGTH = 10
        const val RANGED_STRENGTH = 11
        const val MAGIC_DAMAGE = 12
        const val PRAYER = 13

        /** Item param id of each bonus, by the indexes above. */
        val PARAMS = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 299, 11)

        /**
         * Ranged strength on what isn't ammunition or a thrown weapon (the twisted bow 20, Masori body 4, pegasian boots 1
         * in the 241 cache): it adds to [RANGED_STRENGTH]'s param 12, which no item has alongside it.
         */
        const val PARAM_GEAR_RANGED_STRENGTH = 189

        /** [type]'s own ranged strength, from either param. */
        fun rangedStrength(type: ObjType?): Int =
            type?.let { it.intParam(PARAMS[RANGED_STRENGTH]) + it.intParam(PARAM_GEAR_RANGED_STRENGTH) } ?: 0
    }
}
