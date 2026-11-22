package osrsps.game.combat

import osrsps.game.Equipment
import osrsps.game.EquipmentBonuses
import osrsps.game.EquipmentTab
import osrsps.game.GameLines
import osrsps.game.NpcStat
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.Weapons
import osrsps.game.WearPos
import osrsps.game.dialogue.Line
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

/**
 * A player's melee numbers against another player: their [attackRoll] and [maxHit] with [style], and the target's
 * [defenceRoll] against it; see [Combat.pvpMatchup].
 */
data class PvpMeleeMatchup(val style: AttackStyle, val attackRoll: Int, val defenceRoll: Int, val maxHit: Int)

/** A player's ranged numbers against one NPC with the ammunition they have; see [Combat.rangedMatchup]. */
data class RangedMatchup(val style: AttackStyle, val attackRoll: Int, val defenceRoll: Int, val maxHit: Int, val hitChance: Double)

/**
 * How a player's attack on an NPC is rolled ([Combat.sureHit]): as usual; at its max hit when its accuracy roll lands;
 * or landing at its max hit whatever the roll. The two rolls are drawn either way.
 */
enum class SureHit { ROLLED, MAX_IF_HIT, MAX }

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
    /** What the player's black mask or Slayer helmet does against this NPC; see [SlayerHelm]. */
    private val slayerHelm: (Player, Npc) -> SlayerHelm = { _, _ -> SlayerHelm.NONE },
    /** Why [Player] may not attack [Npc] that belongs to another player (a Slayer superior), null when they may. */
    private val notYours: (Player, Npc) -> Line? = { _, _ -> null },
    /** Whether [Npc] is [Player]'s own Slayer superior, which single-way combat lets them switch to. */
    private val ownSuperior: (Player, Npc) -> Boolean = { _, _ -> false },
) {
    /**
     * Whether [player] may attack [npc] in single-way combat though another NPC is fighting them: their own Slayer
     * superior (OSRS Wiki, Superior slayer monster: the standard monster then stops attacking them).
     */
    fun switchesSingleWay(player: Player, npc: Npc): Boolean = ownSuperior(player, npc)

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

    /** What [player] wields, null for nothing. */
    fun weapon(player: Player): ObjType? = equipment.weapon(player)

    /** The category of the weapon the player wields ([Weapons]), [Weapons.UNARMED] for none. */
    fun weaponCategory(player: Player): Int = Weapons.category(equipment.weapon(player))

    /**
     * Whether [player]'s Slayer level, boosts counted, is below what [npc] needs to be hurt ([NpcCombatDef.slayerLevel]):
     * then no attack of theirs reaches it, a click, a retaliation or a hit that spreads to it (OSRS Wiki, Slayer: "a
     * player cannot attack a monster that requires a higher Slayer level"). Boosts count (OSRS Wiki, Slayer, Boosts).
     */
    fun slayerGated(player: Player, npc: Npc): Boolean =
        (npcCombat[npc.type]?.slayerLevel ?: 0) > player.skills.currentLevel(Skill.SLAYER)

    /**
     * Set by content: whether a player is shut out of attacking an NPC by where the two stand, as a raid room's arena
     * shuts out whoever isn't on it ([osrsps.game.raid.ArchiveRaids.shutOut]).
     */
    var shutOut: (Player, Npc) -> Boolean = { _, _ -> false }

    /**
     * Set by [osrsps.game.content.WildernessBosses]: whether [Player] is outside the fight area of the boss [Npc], and so
     * can't attack it. Apart from [shutOut], which the raids set outright.
     */
    var outsideFight: (Player, Npc) -> Boolean = { _, _ -> false }

    /**
     * Set by content: how [Player]'s attack of [AttackType] on [Npc] is rolled ([SureHit]), asked once as each attack on
     * it is rolled (a hit that bounces or splashes on to another is rolled as usual): Ocellus's void flares, each maxed
     * by a style ([osrsps.game.raid.Ocellus]; OSRS Wiki, Void Siphon, Void Augmenter, Void Rift).
     */
    var sureHit: (Player, Npc, AttackType) -> SureHit = { _, _, _ -> SureHit.ROLLED }

    /**
     * Set by content: what of [Player]'s rolled damage of [AttackType] on [Npc] it takes, before its armour, asked once
     * as each attack on it is rolled (a hit that bounces or splashes on to another is rolled as usual): Ocellus's
     * Deflect Melee and Deflect Range in his final stand ([osrsps.game.raid.Ocellus]; tsps capture notes; OSRS Wiki,
     * The Fractured Archive/Strategies).
     */
    var rolledOn: (Player, Npc, AttackType, Int) -> Int = { _, _, _, rolled -> rolled }

    /**
     * Set by content: whether [Npc] takes nothing from a hit now, asked as each hit lands, so a shot or spell already on
     * its way, recoil and retribution are stopped too; poison and venom ([MeleeCombat.statusHit]) and a finishing hit
     * (::kill, a bag of salt) still land. Bryophyta while her growthlings live ([osrsps.game.content.Bryophyta]).
     */
    var immune: (Npc) -> Boolean = { false }

    /**
     * Set by content: why [Player]'s weapon can't be used on [Npc], as the line that tells them, or null when it can: the
     * rat bone weapons, which work on rats alone ([osrsps.game.content.RatBoneWeapons]). Asked with [barred].
     */
    var weaponBarred: (Player, Npc) -> Line? = { _, _ -> null }

    /**
     * Set by content: why [Player]'s weapon can't be used on another player, as [weaponBarred] for an NPC: the rat bone
     * weapons, rats only. Asked by [PvpCombat] at every attempt, so a weapon swapped mid-fight counts.
     */
    var weaponBarredOnPlayer: (Player) -> Line? = { null }

    /**
     * Set by content: what is added to [Player]'s max hit on [Npc], with any style, after every other bonus: the rat
     * bone weapons' +10 against rats ([osrsps.game.content.RatBoneWeapons]).
     */
    var maxHitAdded: (Player, Npc) -> Int = { _, _ -> 0 }

    /**
     * Set by content: whether [Player]'s attack on [Npc] leaves them free to attack again on the next tick, whatever
     * their weapon's speed: the rat bone weapons on Scurrius's giant rats ([osrsps.game.content.RatBoneWeapons]).
     */
    var noCooldown: (Player, Npc) -> Boolean = { _, _ -> false }

    /**
     * Why no attack of [player]'s reaches [npc], as the line that tells them, null when one can: its Slayer level
     * ([slayerGated]), being shut out of where it is fought ([shutOut]), or its being another player's ([notYours]). Asked wherever an attack begins, goes on,
     * or spreads to another NPC.
     */
    fun barred(player: Player, npc: Npc): Line? = when {
        slayerGated(player, npc) -> CombatLines.SLAYER_LEVEL
        shutOut(player, npc) || outsideFight(player, npc) -> GameLines.CANT_REACH
        else -> weaponBarred(player, npc) ?: notYours(player, npc)
    }

    /**
     * By NPC type id, what lets a player's hits take one below its [NpcCombatDef.minHitpoints]: the brine sabre for
     * rockslugs ([osrsps.game.content.Rockslugs]). Content fills it in. Asked with the attack's type when it is rolled,
     * null when a hit lands (its damage was already held to the floor when rolled).
     */
    val finishers = HashMap<Int, (Player, AttackType?) -> Boolean>()

    /** Whether [source]'s attack of [type] may take [npc] below its [NpcCombatDef.minHitpoints] ([finishers]). */
    fun finishes(npc: Npc, source: Player?, type: AttackType?): Boolean =
        source != null && finishers[npc.type.id]?.invoke(source, type) == true

    /**
     * The hitpoints a hit from [source] (null for none) of [type] (null when not known) leaves [npc] at the least: its
     * own, or its fight's [Npc.holdsAt]. [finisher], when given, is [finishes] as it stood when the hit was rolled, so a
     * weapon changed while the hit is on its way doesn't change it.
     */
    fun floor(npc: Npc, source: Player?, type: AttackType? = null, finisher: Boolean? = null): Int {
        val min = npcCombat[npc.type]?.minHitpoints ?: 0
        val own = if (min == 0 || (finisher ?: finishes(npc, source, type))) 0 else min
        return maxOf(own, npc.holdsAt)
    }

    /** The animations the player swings and blocks with: their weapon's category's. */
    fun anims(player: Player): WeaponAnims = WeaponAnims.of(Weapons.category(equipment.weapon(player)))

    /**
     * Ticks between the player's attacks: the weapon's speed, 4 unarmed, and a combat spell's [MagicCombat.SPEED] when
     * autocasting; a tick more while weary ([Player.wearyTicks]; guessed, the guides say only that it slows attacks).
     */
    fun attackSpeed(player: Player): Int = baseAttackSpeed(player) + wearyDelay(player)

    /** The tick weariness adds to every attack, a manual cast's included. */
    fun wearyDelay(player: Player): Int = if (player.wearyTicks > 0) 1 else 0

    private fun baseAttackSpeed(player: Player): Int {
        val style = style(player)
        val category = Weapons.category(equipment.weapon(player))
        if (style.type != AttackType.MAGIC) return Weapons.styleSpeed(category, style.button, bonuses(player).attackSpeed)
        // A powered staff's own speed is its weapon's (5 for Tumeken's shadow, 3 for Zorya's tome).
        if (Weapons.selfPowered(category)) return bonuses(player).attackSpeed
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
     * gun's "Aim and Fire"; Dinh's bulwark's Block can't attack at all, see [bulwarkBlock]), and null for a ranged or magic style, which this piece doesn't fight
     * with.
     */
    fun meleeType(style: AttackStyle): AttackType? = when (style.type) {
        AttackType.STAB, AttackType.SLASH, AttackType.CRUSH -> style.type
        AttackType.NONE -> AttackType.CRUSH
        AttackType.RANGED, AttackType.MAGIC -> null
    }

    /**
     * The player's melee numbers against [npc], or null when it has no combat definition (it can't be attacked) or the
     * player's style isn't melee (see [meleeType]). [against] rolls against the NPC's defence for that attack type
     * instead of the style's, as the Bandos godsword's Warstrike always rolls against slash ([SpecialAttack.against]);
     * the attack roll keeps the style's own bonus (OSRS Wiki, Bandos godsword: "always rolls against the opponent's
     * slash defence roll"; the Wiki's DPS calculator does the same).
     */
    fun matchup(player: Player, npc: Npc, against: AttackType? = null): MeleeMatchup? {
        val def = npcCombat[npc.type] ?: return null
        val style = style(player)
        val type = meleeType(style) ?: return null
        val bonuses = bonuses(player)
        val skills = player.skills
        val boosts = prayerBoosts(player)
        val attack = PrayerBoosts.boost(skills.currentLevel(Skill.ATTACK), boosts.attack)
        val strength = PrayerBoosts.boost(skills.currentLevel(Skill.STRENGTH), boosts.strength)
        val helm = slayerHelm(player, npc)
        val attackRoll = helm.melee(CombatMath.playerAttackRoll(attack, style.stance, attackBonus(bonuses, type)))
        val defenceRoll = CombatMath.npcDefenceRoll(npc.level(NpcStat.DEFENCE), def.defence.against(against ?: type))
        val npcAttackRoll = CombatMath.npcAttackRoll(npcAttackLevel(npc, def.attackType), def.attackBonus)
        val playerDefenceRoll = playerDefenceRoll(player, style, bonuses, def.attackType, boosts)
        return MeleeMatchup(
            style = style,
            attackRoll = attackRoll,
            defenceRoll = defenceRoll,
            maxHit = helm.melee(CombatMath.playerMaxHit(strength, style.stance, bonuses.meleeStrength + bulwarkStrength(player, bonuses))) + maxHitAdded(player, npc),
            hitChance = CombatMath.hitChance(attackRoll, defenceRoll),
            npcAttackRoll = npcAttackRoll,
            playerDefenceRoll = playerDefenceRoll,
            npcMaxHit = npcMaxHit(npc, def),
            npcHitChance = CombatMath.hitChance(npcAttackRoll, playerDefenceRoll),
        )
    }

    /**
     * [attacker]'s melee numbers against [target], or null when [attacker]'s style isn't melee ([meleeType]). The
     * attack roll and max hit are those against an NPC; the defence roll is the target's own, as an NPC's swing at them
     * meets it: their Defence with their prayers' boosts, their style's stance bonus, and their worn defence bonus for
     * the attack type (OSRS Wiki, Combat: a player defends with the same formula whoever attacks).
     */
    fun pvpMatchup(attacker: Player, target: Player): PvpMeleeMatchup? {
        val style = style(attacker)
        val type = meleeType(style) ?: return null
        val bonuses = bonuses(attacker)
        val boosts = prayerBoosts(attacker)
        val attack = PrayerBoosts.boost(attacker.skills.currentLevel(Skill.ATTACK), boosts.attack)
        val strength = PrayerBoosts.boost(attacker.skills.currentLevel(Skill.STRENGTH), boosts.strength)
        return PvpMeleeMatchup(
            style = style,
            attackRoll = CombatMath.playerAttackRoll(attack, style.stance, attackBonus(bonuses, type)),
            defenceRoll = playerDefenceRoll(target, style(target), bonuses(target), type, prayerBoosts(target)),
            maxHit = CombatMath.playerMaxHit(strength, style.stance, bonuses.meleeStrength + bulwarkStrength(attacker, bonuses)),
        )
    }

    /** What Dinh's bulwark adds to the melee strength bonus ([DinhsBulwark.strengthBonus]); 0 with any other weapon. */
    private fun bulwarkStrength(player: Player, bonuses: EquipmentBonuses): Int =
        if (DinhsBulwark.isBulwark(equipment.weapon(player))) DinhsBulwark.strengthBonus(bonuses) else 0

    /** Whether [player] holds Dinh's bulwark on its Block style, which can't attack ([DinhsBulwark]). */
    fun bulwarkBlock(player: Player): Boolean = DinhsBulwark.isBulwark(equipment.weapon(player)) && style(player).type == AttackType.NONE

    /**
     * Whether Block cuts the hits [player] takes on [tick]: on it, with the bulwark wielded [DinhsBulwark.BLOCK_DELAY]
     * ticks. [Player.weaponId] is brought up to date only at the end of the tick, so a bulwark wielded this tick, which
     * it doesn't name yet, doesn't count.
     */
    fun bulwarkBlocking(player: Player, tick: Long): Boolean =
        bulwarkBlock(player) && player.weaponId == equipment.weapon(player)?.id && tick - player.weaponSince >= DinhsBulwark.BLOCK_DELAY

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
        val size = npc.type.size
        val (attackRoll, maxHit) = rangedNumbers(
            player, style,
            distance = { LineOfSight.distance(player.x, player.z, 1, npc.x, npc.z, size, size) },
            magic = { maxOf(npc.level(NpcStat.MAGIC), def.magicAttack) },
            mask = slayerHelm(player, npc)::rangedOrMagic,
        )
        val defenceRoll = rangedDefenceRoll(npc, style)!!
        return RangedMatchup(
            style = style,
            attackRoll = attackRoll,
            defenceRoll = defenceRoll,
            maxHit = maxHit + maxHitAdded(player, npc),
            hitChance = CombatMath.hitChance(attackRoll, defenceRoll),
        )
    }

    /**
     * [attacker]'s ranged numbers against [target], another player, or null when [attacker]'s style isn't ranged: as
     * against an NPC ([rangedMatchup]), the target defending with their own defence roll for ranged ([pvpMatchup]).
     * A twisted bow scales by the higher of the target's Magic level and magic attack bonus, as for an NPC (OSRS Wiki,
     * Twisted bow).
     */
    fun pvpRangedMatchup(attacker: Player, target: Player): RangedMatchup? {
        val style = style(attacker)
        if (style.type != AttackType.RANGED) return null
        val (attackRoll, maxHit) = rangedNumbers(
            attacker, style,
            distance = { LineOfSight.distance(attacker.x, attacker.z, 1, target.x, target.z, 1, 1) },
            magic = { maxOf(target.skills.currentLevel(Skill.MAGIC), bonuses(target).magicAttack) },
        )
        val defenceRoll = playerDefenceRoll(target, style(target), bonuses(target), AttackType.RANGED, prayerBoosts(target))
        return RangedMatchup(style, attackRoll, defenceRoll, maxHit, CombatMath.hitChance(attackRoll, defenceRoll))
    }

    /**
     * [player]'s ranged attack roll and max hit with [style]: chinchompas' accuracy by the target's [distance], a twisted
     * bow's scaling by the target's [magic]. See [rangedMatchup].
     */
    private fun rangedNumbers(
        player: Player,
        style: AttackStyle,
        distance: () -> Int,
        magic: () -> Int,
        mask: (Int) -> Int = { it },
    ): Pair<Int, Int> {
        val bonuses = bonuses(player)
        val boosts = prayerBoosts(player)
        val level = player.skills.currentLevel(Skill.RANGED)
        val ranged = PrayerBoosts.boost(level, boosts.ranged)
        val rangedStrengthLevel = maxOf(PrayerBoosts.boost(level, boosts.rangedStrength), level + boosts.minRangedStrengthGain)
        val weapon = rangedWeapons.weapon(equipment.weapon(player))
        var attackBonus = bonuses.rangedAttack
        if (weapon?.kind == AmmoKind.CHINCHOMPAS) {
            attackBonus = attackBonus * CombatMath.chinchompaAccuracy(style.stance, distance()) / 100
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
        // Inferred: the imbued mask's 15% comes before the twisted bow's scaling, as the Wiki's DPS calculator has it.
        attackRoll = mask(attackRoll)
        maxHit = mask(maxHit)
        if (weapon?.scaling == RangedScaling.TWISTED_BOW) {
            val scaling = magic()
            attackRoll = attackRoll * CombatMath.twistedBowAccuracy(scaling) / 100
            maxHit = maxHit * CombatMath.twistedBowDamage(scaling) / 100
        }
        return attackRoll to maxHit
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
        var (attackRoll, maxHit) = magicNumbers(player, spell)
        // The imbued mask's 15% multiplies the roll and max hit before an elemental weakness, which then adds its own
        // share of the roll and of the spell's base max hit without the mask (the Wiki's DPS calculator,
        // PlayerVsNPCCalc: blackMaskBonus, then the spellement bonus).
        val helm = slayerHelm(player, npc)
        val unmaskedRoll = attackRoll
        attackRoll = helm.rangedOrMagic(attackRoll)
        maxHit = helm.rangedOrMagic(maxHit)
        val weakness = def.weakness?.takeIf { spell.element != null && it.element == spell.element }
        if (weakness != null) {
            attackRoll += CombatMath.weaknessAttackRoll(unmaskedRoll, weakness.percent) - unmaskedRoll
            maxHit += CombatMath.weaknessMaxHit(spell.maxHit, weakness.percent)
        }
        val defenceRoll = CombatMath.npcDefenceRoll(npc.level(NpcStat.MAGIC), def.defence.magic)
        return MagicMatchup(
            attackRoll = attackRoll,
            defenceRoll = defenceRoll,
            maxHit = maxHit + maxHitAdded(player, npc),
            hitChance = CombatMath.hitChance(attackRoll, defenceRoll),
        )
    }

    /**
     * [attacker]'s numbers against [target], another player, with [spell]: as against an NPC ([magicMatchup]), the
     * target defending with 70% of their Magic and 30% of their Defence ([defenceLevel]), their stance and their worn
     * magic defence bonus (OSRS Wiki, Combat: a player's magic defence whoever attacks).
     */
    fun pvpMagicMatchup(attacker: Player, target: Player, spell: CombatSpell): MagicMatchup {
        val (attackRoll, maxHit) = magicNumbers(attacker, spell)
        val defenceRoll = playerDefenceRoll(target, style(target), bonuses(target), AttackType.MAGIC, prayerBoosts(target))
        return MagicMatchup(attackRoll, defenceRoll, maxHit, CombatMath.hitChance(attackRoll, defenceRoll))
    }

    /** [player]'s magic attack roll and max hit with [spell]; see [magicMatchup]. */
    private fun magicNumbers(player: Player, spell: CombatSpell): Pair<Int, Int> {
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
        return attackRoll to maxHit
    }

    /** [npc]'s defence roll against a ranged [style], or null when it has no combat definition. */
    fun rangedDefenceRoll(npc: Npc, style: AttackStyle): Int? {
        val def = npcCombat[npc.type] ?: return null
        return CombatMath.npcDefenceRoll(npc.level(NpcStat.DEFENCE), def.defence.against(AttackType.RANGED, style.weight))
    }

    /**
     * [npc]'s numbers against [player], or null when it has no combat definition: it rolls its Attack level for melee,
     * its Magic or Ranged level for those (OSRS Wiki, Combat: an NPC's attack roll is its level for the style plus 9,
     * times its bonus plus 64), against the player's defence for its attack type.
     */
    fun npcAttack(npc: Npc, player: Player): NpcAttack? = npcAttack(npc, player, npcCombat[npc.type]?.attackType ?: return null)

    /**
     * [npc]'s roll at [player] with an attack of [type]: its own attack type, magic for a melee NPC's spell
     * ([NpcMeleeSpell]), which rolls with its Magic level and its magic attack bonus, or ranged for a melee NPC's
     * throw, with its Ranged level and its ranged attack bonus ([NpcCombatDef.rangedAttack]).
     */
    fun npcAttack(npc: Npc, player: Player, type: AttackType): NpcAttack? {
        val def = npcCombat[npc.type] ?: return null
        val style = style(player)
        val bonus = when {
            type == def.attackType -> def.attackBonus
            type == AttackType.MAGIC -> def.magicAttack
            type == AttackType.RANGED -> def.rangedAttack
            else -> def.meleeAttack
        }
        return NpcAttack(
            attackRoll = CombatMath.npcAttackRoll(npcAttackLevel(npc, type), bonus),
            defenceRoll = playerDefenceRoll(player, style, bonuses(player), type, prayerBoosts(player)),
            maxHit = npcMaxHit(npc, def),
        )
    }

    /** The level [npc] attacks with: its Magic or Ranged for a magic or ranged NPC, else its Attack. */
    private fun npcAttackLevel(npc: Npc, type: AttackType): Int = when (type) {
        AttackType.MAGIC -> npc.level(NpcStat.MAGIC)
        AttackType.RANGED -> npc.level(NpcStat.RANGED)
        else -> npc.level(NpcStat.ATTACK)
    }

    /**
     * [npc]'s melee max hit: [base] (its file's unless a swing names its own), less what its drained Strength takes off
     * by [CombatMath.npcMaxHit], so the file still decides the max hit of an undrained monster. Guessed: that a monster whose file max hit isn't the formula's
     * loses the same as the formula would.
     */
    internal fun npcMaxHit(npc: Npc, def: NpcCombatDef, base: Int = def.maxHit): Int {
        if (npc.drained[NpcStat.STRENGTH.ordinal] == 0) return base
        val lost = CombatMath.npcMaxHit(npc.type.stats.strength, def.strengthBonus) - CombatMath.npcMaxHit(npc.level(NpcStat.STRENGTH), def.strengthBonus)
        return (base - lost).coerceAtLeast(0)
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
