package osrsps.game.combat

import osrsps.game.Equipment
import osrsps.game.EquipmentBonuses
import osrsps.game.EquipmentTab
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.Weapons
import osrsps.game.WearPos
import osrsps.game.npc.Npc
import osrsps.map.LineOfSight

/** A player's melee numbers against one NPC, and the NPC's against them. */
data class MeleeMatchup(
    val style: AttackStyle,
    val attackRoll: Int,
    val defenceRoll: Int,
    val maxHit: Int,
    val hitChance: Double,
    val npcAttackRoll: Int,
    val playerDefenceRoll: Int,
    val npcMaxHit: Int,
    val npcHitChance: Double,
)

/** A player's ranged numbers against one NPC with the ammunition they have; see [Combat.rangedMatchup]. */
data class RangedMatchup(val style: AttackStyle, val attackRoll: Int, val defenceRoll: Int, val maxHit: Int, val hitChance: Double)

/** A player's numbers against one NPC with a spell; see [Combat.magicMatchup]. */
data class MagicMatchup(val attackRoll: Int, val defenceRoll: Int, val maxHit: Int, val hitChance: Double)

/** What an NPC rolls against a player: its attack roll, the player's defence roll against its attack type, its max hit. */
data class NpcAttack(val attackRoll: Int, val defenceRoll: Int, val maxHit: Int)

/**
 * Combat between players and NPCs: the numbers ([matchup], [rangedMatchup], [magicMatchup], [npcAttack]); the fighting
 * is [MeleeCombat], [RangedCombat] and [MagicCombat].
 *
 * The prayers that count this tick ([prayerBoosts]) multiply the levels before the style bonus and the +8, rounding
 * down (OSRS Wiki, Damage per second/Melee, /Ranged, /Magic). A player's defence against a magic attack is 70% of
 * their Magic and 30% of their Defence, each boosted by its prayer and rounded down on its own, before the style
 * bonus (osrs-dps-calc's NPCVsPlayerCalc); against anything else it is their Defence. The magic prayers' damage bonus
 * adds to the worn magic damage bonus, after a powered staff's multiplier and the cap (guessed: osrs-dps-calc adds it
 * to the worn bonus, which the shadow has already multiplied).
 */
class Combat(
    private val equipment: Equipment,
    val attackStyles: AttackStyles,
    val npcCombat: NpcCombat,
    /** The bows and crossbows, for a weapon whose numbers scale with its target (the twisted bow). */
    private val rangedWeapons: RangedWeapons = RangedWeapons.NONE,
    /** What the player's prayers add this tick; see [Prayers.boosts]. */
    private val prayerBoosts: (Player) -> PrayerBoosts = { PrayerBoosts.NONE },
) {
    /**
     * The level [player] defends with against an attack of [type], before the style bonus: their Defence, or for magic
     * 70% of their Magic and 30% of their Defence (see the class note), with their prayers' boosts.
     */
    fun defenceLevel(player: Player, type: AttackType, boosts: PrayerBoosts = prayerBoosts(player)): Int {
        val defence = PrayerBoosts.boost(player.skills.currentLevel(Skill.DEFENCE), boosts.defence)
        if (type != AttackType.MAGIC) return defence
        val magic = PrayerBoosts.boost(player.skills.currentLevel(Skill.MAGIC), boosts.magicDefence)
        return magic * 7 / 10 + defence * 3 / 10
    }

    private fun playerDefenceRoll(player: Player, style: AttackStyle, bonuses: EquipmentBonuses, type: AttackType, boosts: PrayerBoosts): Int =
        CombatMath.playerDefenceRoll(defenceLevel(player, type, boosts), style.stance, defenceBonus(bonuses, type))

    /**
     * The attack style the player fights with: their weapon's category and the button picked on the combat tab (varp 43).
     * A button the category lacks falls back to its first style, as the equipment tab resets it; a category the table
     * doesn't know (no cache) to punching. A player autocasting with a staff ([Autocast]) fights with magic: [AUTOCAST]
     * or [DEFENSIVE_AUTOCAST].
     */
    fun style(player: Player): AttackStyle {
        val category = Weapons.category(equipment.weapon(player))
        if (Autocast.active(player, category)) return if (Autocast.defensive(player)) DEFENSIVE_AUTOCAST else AUTOCAST
        val button = player.vars[EquipmentTab.VARP_COM_MODE]
        return attackStyles.style(category, button)
            ?: attackStyles.styles(category).firstOrNull()
            ?: AttackStyles.DEFAULT.styles(Weapons.UNARMED).first()
    }

    fun bonuses(player: Player): EquipmentBonuses = equipment.bonuses(player)

    /** The category of the weapon the player wields ([Weapons]), [Weapons.UNARMED] for none. */
    fun weaponCategory(player: Player): Int = Weapons.category(equipment.weapon(player))

    /** The animations the player swings and blocks with: their weapon's category's. */
    fun anims(player: Player): WeaponAnims = WeaponAnims.of(Weapons.category(equipment.weapon(player)))

    /** Ticks between the player's attacks: the weapon's speed, 4 unarmed, and a combat spell's [MagicCombat.SPEED] when autocasting. */
    fun attackSpeed(player: Player): Int {
        val style = style(player)
        if (style.type != AttackType.MAGIC) return bonuses(player).attackSpeed
        // A powered staff's own speed is its weapon's (5 for Tumeken's shadow).
        if (Weapons.category(equipment.weapon(player)) == Weapons.POWERED_STAFF) return bonuses(player).attackSpeed
        return if (harmonisedAutocast(player, style)) MagicCombat.HARMONISED_SPEED else MagicCombat.SPEED
    }

    /**
     * Whether [player] autocasts a standard spell with the Harmonised nightmare staff, which casts it every 4 ticks
     * (OSRS Wiki, Harmonised nightmare staff: autocast only, standard spells only).
     */
    fun harmonisedAutocast(player: Player, style: AttackStyle = style(player)): Boolean =
        (style === AUTOCAST || style === DEFENSIVE_AUTOCAST) && equipment.weapon(player)?.id == MagicCombat.HARMONISED_STAFF &&
            Spellbook.of(player) == Spellbook.STANDARD

    /**
     * The melee attack type the player's style hits with: its own for a melee style, crush for a style with none (a
     * bulwark's block; those weapons come later), and null for a ranged or magic style, which this piece doesn't fight
     * with.
     */
    fun meleeType(style: AttackStyle): AttackType? = when (style.type) {
        AttackType.STAB, AttackType.SLASH, AttackType.CRUSH -> style.type
        AttackType.NONE -> AttackType.CRUSH
        AttackType.RANGED, AttackType.MAGIC -> null
    }

    /**
     * The player's melee numbers against [npc], or null when it has no combat definition (it can't be attacked) or the
     * player's style isn't melee (see [meleeType]).
     */
    fun matchup(player: Player, npc: Npc): MeleeMatchup? {
        val def = npcCombat[npc.type] ?: return null
        val stats = npc.type.stats
        val style = style(player)
        val type = meleeType(style) ?: return null
        val bonuses = bonuses(player)
        val skills = player.skills
        val boosts = prayerBoosts(player)
        val attack = PrayerBoosts.boost(skills.currentLevel(Skill.ATTACK), boosts.attack)
        val strength = PrayerBoosts.boost(skills.currentLevel(Skill.STRENGTH), boosts.strength)
        val attackRoll = CombatMath.playerAttackRoll(attack, style.stance, attackBonus(bonuses, type))
        val defenceRoll = CombatMath.npcDefenceRoll(stats.defence, def.defence.against(type))
        val npcAttackRoll = CombatMath.npcAttackRoll(stats.attack, def.attackBonus)
        val playerDefenceRoll = playerDefenceRoll(player, style, bonuses, def.attackType, boosts)
        return MeleeMatchup(
            style = style,
            attackRoll = attackRoll,
            defenceRoll = defenceRoll,
            maxHit = CombatMath.playerMaxHit(strength, style.stance, bonuses.meleeStrength),
            hitChance = CombatMath.hitChance(attackRoll, defenceRoll),
            npcAttackRoll = npcAttackRoll,
            playerDefenceRoll = playerDefenceRoll,
            npcMaxHit = def.maxHit,
            npcHitChance = CombatMath.hitChance(npcAttackRoll, playerDefenceRoll),
        )
    }

    /**
     * The player's ranged numbers against [npc], or null when it has no combat definition or the player's style isn't
     * ranged. Everything worn counts, the ammunition's ranged strength included (OSRS Wiki, Ranged): the weapons here
     * all fire what is in the ammo slot, and [RangedCombat] won't fire ammunition the weapon can't use. The NPC defends
     * with its bonus for the style's weight (light, standard or heavy). A twisted bow multiplies the attack roll and the
     * max hit by its percentages for the higher of the NPC's Magic level and magic attack bonus (OSRS Wiki, Twisted bow;
     * that the max hit is rounded down after the multiply is inferred, as for other percentage bonuses). A blowpipe's
     * strength is its own and its dart's (OSRS Wiki, Toxic blowpipe); the ammo slot's doesn't count (inferred: it
     * fires nothing from there).
     */
    fun rangedMatchup(player: Player, npc: Npc): RangedMatchup? {
        val def = npcCombat[npc.type] ?: return null
        val style = style(player)
        if (style.type != AttackType.RANGED) return null
        val bonuses = bonuses(player)
        val boosts = prayerBoosts(player)
        val level = player.skills.currentLevel(Skill.RANGED)
        val ranged = PrayerBoosts.boost(level, boosts.ranged)
        val rangedStrengthLevel = maxOf(PrayerBoosts.boost(level, boosts.rangedStrength), level + boosts.minRangedStrengthGain)
        val weapon = rangedWeapons.weapon(equipment.weapon(player))
        var attackBonus = bonuses.rangedAttack
        if (weapon?.kind == AmmoKind.CHINCHOMPAS) {
            val size = npc.type.size
            val distance = LineOfSight.distance(player.x, player.z, 1, npc.x, npc.z, size, size)
            attackBonus = attackBonus * CombatMath.chinchompaAccuracy(style.stance, distance) / 100
        }
        var attackRoll = CombatMath.playerRangedAttackRoll(ranged, style.stance, attackBonus)
        var strength = bonuses.rangedStrength
        if (weapon?.loaded == true) {
            val slot = player.equipment[WearPos.AMMO]?.let { equipment.objTypes[it.id] }
            val dart = if (player.charges[Blowpipe.DARTS] > 0) equipment.objTypes[player.charges[Blowpipe.DART_TYPE]] else null
            strength += rangedStrength(dart) - rangedStrength(slot)
        } else if (weapon?.thrown == true) {
            // Guessed: a thrown weapon is its own ammunition, so what is in the ammo slot adds nothing.
            strength -= rangedStrength(player.equipment[WearPos.AMMO]?.let { equipment.objTypes[it.id] })
        }
        var maxHit = CombatMath.playerRangedMaxHit(rangedStrengthLevel, style.stance, strength)
        if (weapon?.scaling == RangedScaling.TWISTED_BOW) {
            val magic = maxOf(npc.type.stats.magic, def.magicAttack)
            attackRoll = attackRoll * CombatMath.twistedBowAccuracy(magic) / 100
            maxHit = maxHit * CombatMath.twistedBowDamage(magic) / 100
        }
        val defenceRoll = rangedDefenceRoll(npc, style)!!
        return RangedMatchup(
            style = style,
            attackRoll = attackRoll,
            defenceRoll = defenceRoll,
            maxHit = maxHit,
            hitChance = CombatMath.hitChance(attackRoll, defenceRoll),
        )
    }

    /**
     * The player's numbers against [npc] with [spell] cast from the spellbook, or null when it has no combat definition.
     * Everything worn counts towards the magic attack and damage bonuses. The NPC defends with its Magic level, not its
     * Defence, and its magic defence bonus (OSRS Wiki, Damage per second/Magic). A monster weak to the spell's element
     * ([NpcCombatDef.weakness]) makes it more accurate and harder hitting by its percentage ([CombatMath.weaknessAttackRoll],
     * [CombatMath.weaknessMaxHit]).
     */
    fun magicMatchup(player: Player, npc: Npc, spell: CombatSpell): MagicMatchup? {
        val def = npcCombat[npc.type] ?: return null
        val bonuses = bonuses(player)
        val boosts = prayerBoosts(player)
        val magic = PrayerBoosts.boost(player.skills.currentLevel(Skill.MAGIC), boosts.magic)
        val staff = spell.staff
        var attackRoll: Int
        var maxHit: Int
        if (staff == null) {
            attackRoll = CombatMath.playerMagicAttackRoll(magic, bonuses.magicAttack)
            maxHit = CombatMath.spellMaxHit(spell.maxHit, bonuses.magicDamage + boosts.magicDamage)
        } else {
            // A powered staff's spell: its styles' level bonus, and its multiplier on the worn bonuses (OSRS Wiki,
            // Damage per second/Magic, Tumeken's shadow).
            val level = magic + CombatMath.poweredStaffStanceBonus(style(player).stance)
            attackRoll = CombatMath.playerMagicAttackRoll(level, bonuses.magicAttack * staff.bonusMultiplier)
            val worn = minOf(bonuses.magicDamage * staff.bonusMultiplier, CombatMath.MAGIC_DAMAGE_CAP)
            maxHit = CombatMath.spellMaxHit(spell.maxHit, worn + boosts.magicDamage)
        }
        val weakness = def.weakness?.takeIf { spell.element != null && it.element == spell.element }
        if (weakness != null) {
            attackRoll = CombatMath.weaknessAttackRoll(attackRoll, weakness.percent)
            maxHit += CombatMath.weaknessMaxHit(spell.maxHit, weakness.percent)
        }
        val defenceRoll = CombatMath.npcDefenceRoll(npc.type.stats.magic, def.defence.magic)
        return MagicMatchup(
            attackRoll = attackRoll,
            defenceRoll = defenceRoll,
            maxHit = maxHit,
            hitChance = CombatMath.hitChance(attackRoll, defenceRoll),
        )
    }

    /** [npc]'s defence roll against a ranged [style], or null when it has no combat definition. */
    fun rangedDefenceRoll(npc: Npc, style: AttackStyle): Int? {
        val def = npcCombat[npc.type] ?: return null
        return CombatMath.npcDefenceRoll(npc.type.stats.defence, def.defence.against(AttackType.RANGED, style.weight))
    }

    /** [npc]'s melee numbers against [player], or null when it has no combat definition. */
    fun npcAttack(npc: Npc, player: Player): NpcAttack? {
        val def = npcCombat[npc.type] ?: return null
        val style = style(player)
        return NpcAttack(
            attackRoll = CombatMath.npcAttackRoll(npc.type.stats.attack, def.attackBonus),
            defenceRoll = playerDefenceRoll(player, style, bonuses(player), def.attackType, prayerBoosts(player)),
            maxHit = def.maxHit,
        )
    }

    companion object {
        /**
         * The styles of a player autocasting ([style]), on varp 43's [Autocast.COM_MODE]. The XP is what [MagicCombat]
         * gives, Defence too when defensive (OSRS Wiki, Autocast). Guessed: no stance bonus to the player's Defence
         * either way; the Wiki gives the XP split, not a bonus.
         */
        val AUTOCAST = AttackStyle(Autocast.COM_MODE, "Spell", AttackStance.OTHER, AttackType.MAGIC, listOf(Skill.MAGIC))
        val DEFENSIVE_AUTOCAST = AttackStyle(Autocast.COM_MODE, "Spell (defensive)", AttackStance.OTHER, AttackType.MAGIC, listOf(Skill.MAGIC, Skill.DEFENCE))

        private fun rangedStrength(type: ObjType?): Int = EquipmentBonuses.rangedStrength(type)

        fun attackBonus(bonuses: EquipmentBonuses, type: AttackType): Int = when (type) {
            AttackType.STAB -> bonuses.stabAttack
            AttackType.SLASH -> bonuses.slashAttack
            AttackType.CRUSH -> bonuses.crushAttack
            AttackType.RANGED -> bonuses.rangedAttack
            AttackType.MAGIC -> bonuses.magicAttack
            AttackType.NONE -> 0
        }

        fun defenceBonus(bonuses: EquipmentBonuses, type: AttackType): Int = when (type) {
            AttackType.STAB -> bonuses.stabDefence
            AttackType.SLASH -> bonuses.slashDefence
            AttackType.CRUSH -> bonuses.crushDefence
            AttackType.RANGED -> bonuses.rangedDefence
            AttackType.MAGIC -> bonuses.magicDefence
            AttackType.NONE -> 0
        }
    }
}
