package osrsps.game.combat

import osrsps.game.Player
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc

/** The words of being poisoned, in the dialogue file like every other line of Jagex text. */
object PoisonLines : Lines("poison", "Poison") {
    val POISONED = line("poisoned", "becoming poisoned")
    val ENVENOMED = line("envenomed", "becoming envenomed")
}

/**
 * Poison and venom on a player (OSRS Wiki, Poison and Venom, read 2026-10-30), all in the client's poison varp
 * ([VARP]), which the hitpoints orb and RuneLite's Poison and Timers & Buffs plugins read: 0 is neither; 1 to 999,999
 * is poison, hitting for a fifth of it rounded up and going down by one a hit, so a severity of 20 hits 4 five times,
 * then 3 five times, and so on; [VENOM] and up is venom, hitting 6 and 2 more each time up to 20 and going up by one
 * a hit; below 0 is immunity, going up by one each [TICKS], and below [VENOM_IMMUNE] it keeps venom off too. Each
 * [TICKS] (18 seconds) a poisoned or envenomed player takes a hit, a green or dark green hitsplat, which can kill.
 * Poison and venom never stack: venom isn't turned into poison by poisoning, and poison is replaced by venom.
 *
 * The cures ([cure], [cureVenom]) leave the immunity a potion gives in the varp: an antipoison while envenomed turns
 * the venom into poison at the same damage instead, so a second dose cures it (Venom). Dying cures everything. The
 * varp is saved with the others, so poison and immunity go on after a logout, the timer starting again at login.
 *
 * From RuneLite's plugins: the varp's meaning, the venom damage formula and the immunity cut-off. Guessed: the timer
 * starting on the tick the varp leaves 0 and the first hit coming a full [TICKS] after; the immunity as many [TICKS]
 * as come nearest the Wiki's minutes; a stronger poison replacing a weaker one and a weaker one doing nothing; the
 * message only on going from neither to poisoned or envenomed; an antipoison's immunity never shortening a longer one;
 * the venom turned into poison with no immunity; that the timer, immunity included, stops while an interface is open
 * ("Poison doesn't damage or wear off while an interface is open"), and the hit comes as soon as it closes; that
 * poison and venom hits count for Redemption; and that dying clears the immunity too. A monster whose attacks poison
 * ([NpcCombatDef.poison]) poisons players from [MeleeCombat]. Not here yet: venom from monsters, the Serpentine helm,
 * curing from the hitpoints orb, and the poison timer resetting after 54 seconds out of combat.
 *
 * NPCs are poisoned and envenomed too ([poison] and [envenom] on an [Npc]), by poisoned weapons and the toxic blowpipe
 * ([PoisonedWeapons]), and keep the same count on [Npc.poison]. Not here yet for them: an NPC losing its poison, or
 * becoming immune to venom, when it leaves combat.
 */
class Poison(
    /** [player] takes a hit of this much with this hitsplat type ([MeleeCombat.statusHit]). */
    private val hit: (Player, Int, Int) -> Unit = { _, _, _ -> },
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
    private val message: (Player, String) -> Unit = Player::message,
    /** [Npc] takes a hit of this much with this hitsplat type ([MeleeCombat.statusHit]). */
    private val hitNpc: (Npc, Int, Int) -> Unit = { _, _, _ -> },
    /** What an NPC fights with, for its immunities; null for one that can't be fought. */
    private val npcCombat: (Npc) -> NpcCombatDef? = { null },
) {
    /** [player]'s poison varp. */
    fun value(player: Player): Int = player.vars[VARP]

    /** Poisons [player] at [severity] (1 to 999,999), unless they are immune, envenomed or poisoned as strongly already. */
    fun poison(player: Player, severity: Int): Boolean {
        require(severity in 1 until VENOM) { "A poison severity is 1 to ${VENOM - 1}, not $severity" }
        val value = value(player)
        if (value < 0 || value >= severity) return false
        player.vars[VARP] = severity
        if (value == 0) message(player, words(player)[PoisonLines.POISONED])
        return true
    }

    /** Envenoms [player], unless they are immune to venom or envenomed already. Poison immunity alone doesn't stop it. */
    fun envenom(player: Player): Boolean {
        val value = value(player)
        if (value < VENOM_IMMUNE || value >= VENOM) return false
        player.vars[VARP] = VENOM
        if (value <= 0) message(player, words(player)[PoisonLines.ENVENOMED])
        return true
    }

    /**
     * An antipoison's dose: turns venom into poison at the same damage; otherwise cures poison and leaves [player]
     * immune for [periods] of [TICKS], unless they are immune for longer already.
     */
    fun cure(player: Player, periods: Int) {
        val value = value(player)
        player.vars[VARP] = if (value >= VENOM) damage(value) * 5 else minOf(value, 0, -periods)
    }

    /** An anti-venom's dose: cures venom and poison and leaves [player] immune for [periods], as [cure]. */
    fun cureVenom(player: Player, periods: Int) {
        player.vars[VARP] = minOf(value(player), 0, -periods)
    }

    /** Ends poison, venom and immunity on [player]: death, or a scenario starting. */
    fun clear(player: Player) {
        player.vars[VARP] = 0
        player.poisonTicks = -1
    }

    /** Counts [player]'s timer down by a tick, hitting them or wearing the immunity off when it runs out. */
    fun process(player: Player) {
        if (player.dead) return
        val value = value(player)
        if (value == 0) {
            player.poisonTicks = -1
            return
        }
        if (player.poisonTicks < 0) {
            player.poisonTicks = TICKS
            return
        }
        if (player.poisonTicks > 0) player.poisonTicks--
        if (player.poisonTicks > 0 || player.modalOpen) return
        player.poisonTicks = TICKS
        when {
            value < 0 -> player.vars[VARP] = value + 1
            value >= VENOM -> {
                // The varp goes on counting past the 20 cap, as RuneLite's Poison plugin says; it never gets near overflowing.
                player.vars[VARP] = value + 1
                hit(player, damage(value), VENOM_HITSPLAT)
            }
            else -> {
                player.vars[VARP] = value - 1
                hit(player, damage(value), POISON_HITSPLAT)
            }
        }
    }

    /**
     * Poisons [npc] at [severity], as a poisoned weapon's hit does: false while it is immune, envenomed or poisoned more
     * strongly. A poison as strong as its own starts it again (OSRS Wiki, Poison: "Poison duration can be reset on an
     * already-poisoned target"). Its timer starts again too, as for a player newly poisoned (guessed).
     */
    fun poison(npc: Npc, severity: Int): Boolean {
        require(severity in 1 until VENOM) { "A poison severity is 1 to ${VENOM - 1}, not $severity" }
        val def = npcCombat(npc) ?: return false
        if (def.poisonImmune || npc.dead || npc.poison > severity) return false
        npc.poison = severity
        if (npc.poisonTicks < 0) npc.poisonTicks = TICKS
        return true
    }

    /**
     * Envenoms [npc], as the toxic blowpipe's hit does: false while it is immune or envenomed already. Venom replaces
     * its poison, and poison never turns its venom back into poison (the Wiki's 2024 change).
     */
    fun envenom(npc: Npc): Boolean {
        val def = npcCombat(npc) ?: return false
        if (def.venomImmune || npc.dead || npc.poison >= VENOM) return false
        npc.poison = VENOM
        if (npc.poisonTicks < 0) npc.poisonTicks = TICKS
        return true
    }

    /**
     * Counts [npc]'s timer down by a tick and hits it when it runs out, every [TICKS] as for a player; poison runs out
     * at 0, venom never does. Poison and venom kills give no XP (the Wiki), and the NPC doesn't turn on anyone for them.
     */
    fun process(npc: Npc) {
        if (npc.dead || npc.poison == 0) {
            npc.poisonTicks = -1
            return
        }
        if (npc.poisonTicks < 0) npc.poisonTicks = TICKS
        if (--npc.poisonTicks > 0) return
        npc.poisonTicks = TICKS
        val value = npc.poison
        if (value >= VENOM) {
            npc.poison = value + 1
            hitNpc(npc, damage(value), VENOM_HITSPLAT)
        } else {
            npc.poison = value - 1
            hitNpc(npc, damage(value), POISON_HITSPLAT)
        }
    }

    /** What [npc]'s poison is, for `::npcs`; null for none. */
    fun describe(npc: Npc): String? = when {
        npc.poison >= VENOM -> "envenomed, hitting ${damage(npc.poison)} in ${npc.poisonTicks} ticks"
        npc.poison > 0 -> "poisoned, hitting ${damage(npc.poison)} in ${npc.poisonTicks} ticks"
        else -> null
    }

    /** What [player]'s varp means, for `::poison`. */
    fun describe(player: Player): String {
        val value = value(player)
        val next = if (player.poisonTicks >= 0) ", the next in ${player.poisonTicks} ticks" else ""
        return when {
            value == 0 -> "Not poisoned or envenomed (poison varp 0)."
            value >= VENOM -> "Envenomed, hitting ${damage(value)}$next (poison varp $value)."
            value > 0 -> "Poisoned, hitting ${damage(value)}$next (poison varp $value)."
            value < VENOM_IMMUNE -> "Immune to poison and venom for ${-value} more periods$next (poison varp $value)."
            else -> "Immune to poison for ${-value} more periods$next (poison varp $value)."
        }
    }

    companion object {
        /** The poison varp. */
        const val VARP = 102

        /** Between hits, 18 seconds. */
        const val TICKS = 30

        /** The varp's first venom value, which hits 6. */
        const val VENOM = 1_000_000

        /** Immunity below this keeps venom off too (RuneLite's Timers & Buffs: "Antivenom < -38 <= Antipoison < 0"). */
        const val VENOM_IMMUNE = -38

        /** Venom's most a hit. */
        const val VENOM_MAX = 20

        /** The green poison hitsplat and the dark green venom one (RuneLite's HitsplatID). */
        const val POISON_HITSPLAT = 65
        const val VENOM_HITSPLAT = 5

        /** The next hit of a poisoned or envenomed varp [value]. */
        fun damage(value: Int): Int =
            if (value >= VENOM) minOf(VENOM_MAX, (value - VENOM + 3) * 2) else (value + 4) / 5
    }
}
