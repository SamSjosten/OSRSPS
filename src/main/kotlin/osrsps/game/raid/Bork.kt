package osrsps.game.raid

import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.combat.AttackType
import osrsps.game.combat.CombatMath
import osrsps.game.combat.DinhsBulwark
import osrsps.game.combat.NpcRangedAttack
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.npc.Npc

/**
 * Bork and the Smorks (raid step 18a), the second row's other pair of bosses, in their own room: Bork, a 4 by 4 ork
 * who keeps to his tank, the first player in, and the Smorks, three goblins in a 3 by 3 cannon cart that stands in the
 * middle of the room. Both have to die for the room to be done; the fight's own NPC is only its anchor
 * (`tfa_bork_smork_hp_ui_handler`, [ANCHOR]), which carries the party size and is taken away once both are gone.
 *
 * - Bork walks after his tank and every [ATTACK_SPEED] ticks beats his gong ([beat]): with his tank beside him, a beat
 *   on everyone round him; with his tank out of his reach, a wide beat on everyone within [WIDE_REACH] tiles of him, and
 *   after it a close one again. The Smorks' cart in the way of a beat ([shields]) keeps it off whoever is behind it.
 * - Every [ATTACK_SPEED] ticks, [SMORKS_OFFSET] after Bork's, the Smorks fire at everyone Bork isn't after ([volley]):
 *   bones, ranged, or a cluster of potions, magic, each player's own shot, all of a volley the same.
 * - Bork's flat armour ([armourFor]): [ARMOUR_BASE] off each melee and ranged hit on him as it's rolled, and [ARMOUR_STEP] more of it, and
 *   of his magic resistance, for every [ARMOUR_EVERY_PERCENT]% the Smorks have lost, up to [ARMOUR_MAX] and
 *   [MAGIC_ARMOUR_MAX].
 *
 * From the OSRS Wiki (Bork, The Fractured Archive's; Smorks; read 2026-11-09): their stats and bonuses (npc-combat
 * .json), Bork 4 by 4 and the Smorks 3 by 3, both 7-tick attackers, Bork crush and the Smorks ranged and magic, their
 * hitpoints by party size (party-scaling.json), and the flat armour: "starts at 8 flat armour and 0 magic resistance at
 * 100% Smorks health, and gains 2 of each for every 25% of damage the Smorks take", up to 14 and 6. From guides
 * (bald.gg, runescapeguides, osrsguide, osrsbestinslot, osrstoolkit; read 2026-11-09): Bork after the first player in;
 * "if that target is not in melee range, his first attack is a stronger beat that covers a larger area. His second
 * attack only hits the tiles immediately around him"; Protect from Melee reducing his damage; the Smorks static in the
 * middle, blocking his beats; their bones (ranged) and potions (magic) "fired at all players not targeted by Bork".
 * From the cache: `tfa_bork` (16640), `tfa_smork` (16641), the anchor (16642), the `npc_bork_*` and `npc_smork_*`
 * animations and the `vfx_bork_*` and `vfx_smork_*` spotanims.
 *
 * Guessed, until a recording shows the fight (each also in the README): where the cart and Bork stand as it begins
 * ([ArchiveRaids.BORK_CART_X], [BORK_WEST]); his first beat [ATTACK_SPEED] ticks in and the Smorks'
 * [SMORKS_OFFSET] after; a beat's reach (round him, his own tiles too) and the wide one's ([WIDE_REACH]) and max hit
 * ([WIDE_MAX]); a beat's roll being his melee's against each player's crush defence; Protect from Melee halving it;
 * the cart shielding whoever the line from his middle to them crosses it for; the Smorks' max hits (the formula's from
 * their Ranged, the magic's the same) and a volley's style picked at random; their shots being rolled as an NPC's
 * shots are; the bones' and potions' spotanims picked by name; the cannon's attack animation for both; flat armour
 * not taking a hit below 0; the death spotanims; the fight's anchor being the hp handler NPC, and both having to be
 * gone, not only dead, before the room is done (so their death animations play out).
 * Still to come: their cannonballs, Bork's stun and stomps, and his tank calls (18b); the enrages (18c).
 */
object Bork {
    /** What `::raid` and the raid flow know the fight by. */
    const val NAME = "bork"

    /** `tfa_bork`. */
    const val BORK = 16640

    /** `tfa_smork`: the Smorks' cart. */
    const val SMORKS = 16641

    /** `tfa_bork_smork_hp_ui_handler`: the fight's anchor, never fought. */
    const val ANCHOR = 16642

    /** npc_bork_gong_melee01, his beat, and vfx_bork_gong_melee_spotanim01 with it. */
    const val ANIM_BEAT = 14542
    const val SPOT_BEAT = 4037

    /** vfx_bork_death_spotanim01, with his death animation (npc-combat.json's npc_bork_death01). */
    const val SPOT_BORK_DEATH = 4036

    /** npc_smork_cannon_attack01: the Smorks' shot, bones or potions. */
    const val ANIM_SMORKS_ATTACK = 14554

    /** vfx_smork_range_attack_projanim01, the bones' flight, and vfx_smork_range_player_spotanim01 as they land. */
    const val SPOT_BONES = 4056
    const val SPOT_BONES_HIT = 4055

    /** vfx_smork_mage_attack_spotanim01, _projanim01 and vfx_smork_magic_hit_spotanim01: the potions. */
    const val SPOT_POTIONS_CAST = 4058
    const val SPOT_POTIONS = 4057
    const val SPOT_POTIONS_HIT = 4059

    /** vfx_smork_death_explosion_spotanim01, the cart going up. */
    const val SPOT_SMORKS_DEATH = 4062

    /**
     * Bork's south-west tile as the fight begins, this many tiles west of the cart's: between it and the way in, so he
     * walks straight at the first player in rather than into his own cart. Guessed.
     */
    const val BORK_WEST = 6

    /** The Wiki's attack speed, both of them. */
    const val ATTACK_SPEED = 7

    /** The Smorks fire this many ticks after Bork beats. Guessed. */
    const val SMORKS_OFFSET = 3

    /** A wide beat's reach, in tiles out from his sides, and its max hit. Guessed ("a stronger beat that covers a larger area"). */
    const val WIDE_REACH = 3
    const val WIDE_MAX = 50

    /** The potions' max hit. Guessed: the bones' (npc-combat.json's 28, the formula's from their Ranged). */
    const val POTIONS_MAX = 28

    /** Bork's flat armour: the Wiki's 8, 2 more for each 25% the Smorks have lost, up to 14; his magic resistance up to 6. */
    const val ARMOUR_BASE = 8
    const val ARMOUR_STEP = 2
    const val ARMOUR_EVERY_PERCENT = 25
    const val ARMOUR_MAX = 14
    const val MAGIC_ARMOUR_MAX = 6

    /** The Smorks' bones. */
    val BONES = NpcRangedAttack(anim = ANIM_SMORKS_ATTACK, projectile = SPOT_BONES, impact = SPOT_BONES_HIT)

    /** The Smorks' potions. */
    val POTIONS = NpcRangedAttack(anim = ANIM_SMORKS_ATTACK, cast = SPOT_POTIONS_CAST, projectile = SPOT_POTIONS, impact = SPOT_POTIONS_HIT, maxHit = POTIONS_MAX)

    /** The fight, or null when the cache hasn't the three (a test world's). */
    fun def(types: NpcTypes): EncounterDef? {
        val anchor = types[ANCHOR] ?: return null
        val borkType = types[BORK] ?: return null
        val smorksType = types[SMORKS] ?: return null
        return EncounterDef(
            NAME, anchor,
            listOf(
                PhaseDef(
                    "fight",
                    cycle = listOf(CycleStep(1) { tick() }),
                    onStart = {
                        // Scaled to the party the anchor was (the raid's, or ::raid bork party=n), at least two.
                        val party = boss.scaledFor.takeIf { it > 0 } ?: PartyScaling.MIN_COUNTED
                        val smorks = spawnAdd(smorksType, boss.x, boss.z)
                        val bork = spawnAdd(borkType, boss.x - BORK_WEST, boss.z)
                        for (npc in listOfNotNull(smorks, bork)) {
                            world.partyScaling.scale(npc, party.coerceIn(1, PartyScaling.MAX_PARTY))
                            // They attack only as this fight says, and never give up on whoever they're set on.
                            npc.scriptedAttacks = true
                            npc.unleashed = true
                        }
                        state.smorks = smorks?.let(::WeakReference)
                        state.bork = bork?.let(::WeakReference)
                        // Worked out as each hit on him is rolled, so a hit taking the Smorks past a quarter counts at
                        // once for the next on him, in the same tick too.
                        val held = smorks?.let(::WeakReference)
                        bork?.armour = { magic -> armourAgainst(held?.get(), magic) }
                        bork?.let { armour(it) }
                        log("Bork at ${bork?.x},${bork?.z}, the Smorks at ${smorks?.x},${smorks?.z}")
                    },
                ),
            ),
            scriptedAttacks = true,
            status = {
                val bork = state.bork?.get()
                val smorks = state.smorks?.get()
                "Bork ${hp(bork)}, the Smorks ${hp(smorks)}"
            },
        )
    }

    private fun hp(npc: Npc?): String = if (npc == null) "none" else "${if (npc.dead) 0 else npc.hitpoints}/${npc.maxHitpoints} hp"

    /** What a fight keeps between ticks, by its anchor. Holds its NPCs and its tank only weakly. */
    private class State {
        var bork: WeakReference<Npc>? = null
        var smorks: WeakReference<Npc>? = null

        /** His tank, kept here as his combat target can go (they died, or left). */
        var tank: WeakReference<Player>? = null

        /** Whether his last beat was a wide one: the next is a close one, whatever. */
        var wideLast = false

        /** Whether each one's death has been seen. */
        var borkDown = false
        var smorksDown = false

        /** His flat armour and magic resistance as last set, for the log. */
        var armour = -1
        var magicArmour = -1
    }

    private val states = WeakHashMap<Npc, State>()

    private val EncounterContext.state: State get() = states.getOrPut(boss) { State() }

    private fun EncounterContext.inWorld(npc: Npc): Boolean = world.npcs[npc.index] === npc

    private fun EncounterContext.alive(npc: Npc?): Npc? = npc?.takeIf { !it.dead && inWorld(it) }

    /** One tick of the fight. */
    private fun EncounterContext.tick() {
        // One that couldn't be spawned (no NPC slot): nothing happens, and the raid takes the fight back (ArchiveRaids).
        if (state.bork == null || state.smorks == null) return
        val borkHeld = state.bork?.get()
        val smorksHeld = state.smorks?.get()
        if (borkHeld != null && borkHeld.dead && !state.borkDown) {
            state.borkDown = true
            borkHeld.effects.spotanim(0, SPOT_BORK_DEATH, 0)
            log("Bork down")
        }
        if (smorksHeld != null && smorksHeld.dead && !state.smorksDown) {
            state.smorksDown = true
            smorksHeld.effects.spotanim(0, SPOT_SMORKS_DEATH, 0)
            log("the Smorks down")
        }
        // Done once both are gone, their death animations over: the anchor dies, which does the room.
        if ((borkHeld == null || !inWorld(borkHeld)) && (smorksHeld == null || !inWorld(smorksHeld))) {
            log("both gone")
            boss.dead = true
            world.removeNpc(boss)
            return
        }
        val bork = alive(borkHeld)
        val smorks = alive(smorksHeld)
        if (bork != null) {
            armour(bork)
            val tank = tank(bork)
            if (tank != null) {
                chase(bork, tank)
                if (phaseTick > 0 && phaseTick % ATTACK_SPEED == 0L) beat(bork, smorks, tank)
            }
        }
        // Their first [SMORKS_OFFSET] ticks after his first beat, then every [ATTACK_SPEED].
        if (smorks != null && phaseTick >= ATTACK_SPEED + SMORKS_OFFSET && phaseTick % ATTACK_SPEED == SMORKS_OFFSET.toLong()) volley(smorks, bork)
    }

    /**
     * Bork's flat armour and magic resistance for the Smorks at [smorksHitpoints] of [smorksMax] (0 hitpoints for
     * Smorks dead): [ARMOUR_STEP] each for every whole [ARMOUR_EVERY_PERCENT]% they've lost.
     */
    fun armourFor(smorksHitpoints: Int, smorksMax: Int): Pair<Int, Int> {
        val lostPercent = if (smorksMax <= 0) 100 else (smorksMax - smorksHitpoints.coerceIn(0, smorksMax)) * 100 / smorksMax
        val steps = lostPercent / ARMOUR_EVERY_PERCENT
        return minOf(ARMOUR_MAX, ARMOUR_BASE + ARMOUR_STEP * steps) to minOf(MAGIC_ARMOUR_MAX, ARMOUR_STEP * steps)
    }

    /** Bork's flat armour, or magic resistance for a spell, with [smorks] as they are now (null or dead: at its most). */
    private fun armourAgainst(smorks: Npc?, magic: Boolean): Int {
        val (flat, resistance) = if (smorks == null || smorks.dead) armourFor(0, 1) else armourFor(smorks.hitpoints, smorks.maxHitpoints)
        return if (magic) resistance else flat
    }

    /** Logs Bork's armour when it changes; what hits on him lose comes from [Npc.armour] as each is rolled. */
    private fun EncounterContext.armour(bork: Npc) {
        val smorks = state.smorks?.get()
        val flat = armourAgainst(smorks, magic = false)
        val magic = armourAgainst(smorks, magic = true)
        if (flat != state.armour || magic != state.magicArmour) {
            state.armour = flat
            state.magicArmour = magic
            log("Bork's armour $flat, magic $magic")
        }
    }

    /**
     * His tank: the player he fights while they're in the fight, else the one he was set on (the first player in), else
     * the first in the fight.
     */
    private fun EncounterContext.tank(bork: Npc): Player? {
        val fighting = players
        state.tank?.get()?.let { held -> if (fighting.any { it === held }) return held }
        val pick = bork.target?.takeIf { held -> fighting.any { it === held } } ?: fighting.firstOrNull() ?: return null
        state.tank = WeakReference(pick)
        if (bork.target !== pick) {
            bork.target = null
            world.meleeCombat.aggress(bork, pick, world.tick)
        }
        log("Bork takes on ${pick.username}")
        return pick
    }

    /** He walks after his tank till he's beside them, and stands while he is. */
    private fun EncounterContext.chase(bork: Npc, tank: Player) {
        if (beside(bork.x, bork.z, bork.type.size, tank.x, tank.z)) {
            if (bork.following === tank) world.npcs.stopFollowing(bork)
            bork.face(tank)
        } else if (bork.following !== tank) {
            world.npcs.follow(bork, tank)
        }
    }

    /** Whether ([x], [z]) is beside a [size] by [size] NPC at ([npcX], [npcZ]): along one of its sides, not off a corner. */
    fun beside(npcX: Int, npcZ: Int, size: Int, x: Int, z: Int): Boolean {
        val alongX = x in npcX until npcX + size && (z == npcZ - 1 || z == npcZ + size)
        val alongZ = z in npcZ until npcZ + size && (x == npcX - 1 || x == npcX + size)
        return alongX || alongZ
    }

    /** Whether ([x], [z]) is within [reach] tiles of a [size] by [size] NPC at ([npcX], [npcZ]), counting its own tiles. */
    fun near(npcX: Int, npcZ: Int, size: Int, x: Int, z: Int, reach: Int): Boolean {
        val dx = when {
            x < npcX -> npcX - x
            x >= npcX + size -> x - (npcX + size - 1)
            else -> 0
        }
        val dz = when {
            z < npcZ -> npcZ - z
            z >= npcZ + size -> z - (npcZ + size - 1)
            else -> 0
        }
        return max(dx, dz) <= reach
    }

    /**
     * Whether the Smorks' cart, [cartSize] by [cartSize] at ([cartX], [cartZ]), stands between Bork, [size] by [size]
     * at ([fromX], [fromZ]), and ([x], [z]): the line from his middle to that tile crosses it before reaching it.
     */
    fun shields(cartX: Int, cartZ: Int, cartSize: Int, fromX: Int, fromZ: Int, size: Int, x: Int, z: Int): Boolean {
        fun cart(tx: Int, tz: Int) = tx in cartX until cartX + cartSize && tz in cartZ until cartZ + cartSize
        if (cart(x, z)) return false
        val cx = fromX + (size - 1) / 2.0
        val cz = fromZ + (size - 1) / 2.0
        val steps = 4 * max(abs(x - cx), abs(z - cz)).roundToInt().coerceAtLeast(1)
        for (i in 0 until steps) {
            val t = i.toDouble() / steps
            if (cart((cx + (x - cx) * t).roundToInt(), (cz + (z - cz) * t).roundToInt())) return true
        }
        return false
    }

    /** His beat, as it's due: close round him with his tank beside him or after a wide one, else wide. */
    private fun EncounterContext.beat(bork: Npc, smorks: Npc?, tank: Player) {
        val size = bork.type.size
        val wide = !beside(bork.x, bork.z, size, tank.x, tank.z) && !state.wideLast
        state.wideLast = wide
        val reach = if (wide) WIDE_REACH else 1
        bork.face(tank)
        bork.effects.anim(ANIM_BEAT)
        bork.effects.spotanim(0, SPOT_BEAT, 0)
        val inReach = players.filter { near(bork.x, bork.z, size, it.x, it.z, reach) }
        val struck = inReach.filter { smorks == null || !shields(smorks.x, smorks.z, smorks.type.size, bork.x, bork.z, size, it.x, it.z) }
        val behind = inReach - struck.toSet()
        val what = if (wide) "wide beat" else "beat"
        log("$what on ${struck.joinToString { it.username }.ifEmpty { "no one" }}" + if (behind.isEmpty()) "" else ", the cart shielding ${behind.joinToString { it.username }}")
        for (player in struck) {
            if (gone(bork)) return
            val dealt = world.meleeCombat.hitPlayer(player, bork, beatDamage(bork, player, wide), world.tick)
            log("$what hits ${player.username} for $dealt")
        }
    }

    /**
     * A beat's damage on [player]: his melee's roll at them, up to his max hit or [WIDE_MAX], cut by Dinh's bulwark on
     * Block as any NPC's melee is, then halved by Protect from Melee.
     */
    private fun EncounterContext.beatDamage(bork: Npc, player: Player, wide: Boolean): Int {
        val roll = world.combat.npcAttack(bork, player) ?: return 0
        val hits = CombatMath.rollHits(world.random, roll.attackRoll, roll.defenceRoll)
        val damage = if (hits) CombatMath.rollDamage(world.random, if (wide) WIDE_MAX else roll.maxHit) else 0
        val blocked = if (world.combat.bulwarkBlocking(player, world.tick)) DinhsBulwark.blocked(damage) else damage
        return if (world.prayers.protects(player, AttackType.CRUSH)) blocked / 2 else blocked
    }

    /** The Smorks' shots, all bones or all potions, at everyone Bork isn't after. */
    private fun EncounterContext.volley(smorks: Npc, bork: Npc?) {
        val tank = if (bork != null) state.tank?.get() else null
        val targets = players.filter { it !== tank }
        if (targets.isEmpty()) return
        val magic = roll(0, 1) == 1
        log("the Smorks fire ${if (magic) "potions" else "bones"} at ${targets.joinToString { it.username }}")
        for (player in targets) {
            if (gone(smorks)) return
            world.meleeCombat.fireAt(smorks, player, world.tick, if (magic) POTIONS else BONES, if (magic) AttackType.MAGIC else AttackType.RANGED)
        }
    }

    private fun EncounterContext.gone(npc: Npc): Boolean = boss.dead || npc.dead || !inWorld(npc)
}
