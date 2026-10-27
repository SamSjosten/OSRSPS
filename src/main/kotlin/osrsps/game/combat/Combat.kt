package osrsps.game.combat

import osrsps.game.Equipment
import osrsps.game.EquipmentBonuses
import osrsps.game.EquipmentTab
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.Weapons
import osrsps.game.npc.Npc

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

/**
 * Melee combat between players and NPCs: the numbers for now ([matchup]); attacking, retaliation and death come in the
 * next steps of the combat piece.
 */
class Combat(
    private val equipment: Equipment,
    val attackStyles: AttackStyles,
    val npcCombat: NpcCombat,
) {
    /**
     * The attack style the player fights with: their weapon's category and the button picked on the combat tab (varp 43).
     * A button the category lacks falls back to its first style, as the equipment tab resets it; a category the table
     * doesn't know (no cache) to punching.
     */
    fun style(player: Player): AttackStyle {
        val category = Weapons.category(equipment.weapon(player))
        val button = player.vars[EquipmentTab.VARP_COM_MODE]
        return attackStyles.style(category, button)
            ?: attackStyles.styles(category).firstOrNull()
            ?: AttackStyles.DEFAULT.styles(Weapons.UNARMED).first()
    }

    fun bonuses(player: Player): EquipmentBonuses = equipment.bonuses(player)

    /** The animations the player swings and blocks with: their weapon's category's. */
    fun anims(player: Player): WeaponAnims = WeaponAnims.of(Weapons.category(equipment.weapon(player)))

    /** Ticks between the player's attacks: the weapon's speed, 4 unarmed. */
    fun attackSpeed(player: Player): Int = bonuses(player).attackSpeed

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
        val attackRoll = CombatMath.playerAttackRoll(skills.currentLevel(Skill.ATTACK), style.stance, attackBonus(bonuses, type))
        val defenceRoll = CombatMath.npcDefenceRoll(stats.defence, def.defence.against(type))
        val npcAttackRoll = CombatMath.npcAttackRoll(stats.attack, def.attackBonus)
        val playerDefenceRoll = CombatMath.playerDefenceRoll(skills.currentLevel(Skill.DEFENCE), style.stance, defenceBonus(bonuses, def.attackType))
        return MeleeMatchup(
            style = style,
            attackRoll = attackRoll,
            defenceRoll = defenceRoll,
            maxHit = CombatMath.playerMaxHit(skills.currentLevel(Skill.STRENGTH), style.stance, bonuses.meleeStrength),
            hitChance = CombatMath.hitChance(attackRoll, defenceRoll),
            npcAttackRoll = npcAttackRoll,
            playerDefenceRoll = playerDefenceRoll,
            npcMaxHit = def.maxHit,
            npcHitChance = CombatMath.hitChance(npcAttackRoll, playerDefenceRoll),
        )
    }

    companion object {
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
