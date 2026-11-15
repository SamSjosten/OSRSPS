package osrsps.game.content

import kotlin.math.max
import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.Dragonfire
import osrsps.game.combat.MeleeCombat
import osrsps.game.combat.PvpLines
import osrsps.game.dialogue.Lines
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterContext
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.encounter.heldTarget
import osrsps.game.instance.Instance
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.Projectile
import osrsps.map.LineOfSight

/** The King Black Dragon's lair's words (keys only; the words are in data/dialogue.txt). */
object KingBlackDragonLines : Lines("kbd", "King Black Dragon Lair") {
    val COMMUNE = line("commune", "Commune on the lever into the lair: how many players are inside (no source yet)", "count")
    val ICY = line("icy", "An icy breath hits a player with no dragonfire protection (the Wiki's King Black Dragon)")
    val TOXIC = line("toxic", "A toxic breath hits a player with no dragonfire protection (the Wiki's King Black Dragon)")
    val SHOCKING = line("shocking", "A shocking breath hits a player with no dragonfire protection (the Wiki's King Black Dragon)")
    val FIERY = line("fiery", "His fiery breath hits a player with no dragonfire protection (the Wiki's King Black Dragon)")
    val PRIVATE_TITLE = line("private_title", "Private on the lever into the lair: the menu's title, asking to pay 50,000 coins for a private lair (no source yet)")
    val PRIVATE_YES = line("private_yes", "Private on the lever: the menu's option that pays and goes in (no source yet)")
    val PRIVATE_NO = line("private_no", "Private on the lever: the menu's option that doesn't (no source yet)")
    val PRIVATE_NO_COINS = line("private_no_coins", "Private on the lever with fewer than 50,000 coins in the backpack and in the bank (no source yet)")
}

/**
 * The King Black Dragon and his lair (Wilderness step W4.6; the Wiki's King Black Dragon, its Strategies page, its
 * Dragonfire table, King Black Dragon Lair and the two levers' pages, read 2026-11-15).
 *
 * The lair (map square 35_73, multi-way, not in the Wilderness) is reached by Pull on the Lava Maze Dungeon's lever
 * (`dragonkinginlever` 1816 at 3067,10253), which a Tele Blocked player can't, and left by Pull on its own
 * (`dragonkingoutlever` 1817 at 2271,4680), both through [WildernessLevers]. Commune on the way in says how many players
 * are in the lair. Private on the way in (step W4.6b) takes 50,000 coins, from the backpack or else the bank ("Players do
 * not need to carry coins on them, as the fee may be deducted from the bank", the Wiki's King Black Dragon), for a lair
 * of the player's own: a copy of his map square ([osrsps.game.instance.Instance]) with him in it, which he comes back to
 * after each kill as in the open lair, gone with everything in it once nobody is inside. Its lever out works there too,
 * and logging out or dying inside leaves by the dungeon's lever, as every instance does.
 *
 * `king_dragon` 239, size 5, combat 276, 240 hitpoints, aggressive, every 4 ticks. Beside the player he swings (stab,
 * max 25) one attack in three; otherwise he breathes, half the time dragonfire (max 65) and half one of three special
 * breaths (max 50): toxic, which may poison from 8; shocking, which may drain 2 from every stat; icy, which may freeze
 * for 10 ticks; each special's effect half the time it hits, one in eight behind a shield or Protect from Magic
 * (the Wiki's Strategies page, from Mod Ash). An anti-dragon shield (or its upgrades) caps dragonfire at
 * 15 and the specials at 10, Protect from Magic alone at 20 and 15, both at 15 and 10. Antifire potions aren't in the
 * game yet. His drops are in npc-drops.json and his kill count in `total_kbd_kills`.
 *
 * GUESSED until recorded: each breath's damage from 0 to its cap with no accuracy roll; the effects only on a hit of 1
 * or more; the shock draining every stat but Hitpoints; his animations (dragon_attack for the swing,
 * dragon_ranged_attacks for every breath) and the breaths' flight; the chromatic dragons' dragonfire messages for a
 * shield or Protect from Magic; where the levers put the player (the far lever's own tile, as the other levers); the
 * Commune line's count
 * of everyone in the lair's map square; Private taking the fee from the backpack when it holds it all and else from the
 * bank, after a menu, and refused under a Tele Block; the private lair's pull as the open lever's. Left out: the
 * instance's drops lasting 30 minutes (ours last as anywhere else). No pet, no draconic visage, no KBD heads yet.
 */
object KingBlackDragon {
    const val BOSS = 239
    const val KILL_COUNT_VARP = 1514

    const val LEVER_IN = 1816
    const val LEVER_OUT = 1817
    const val COMMUNE = 2
    const val PRIVATE = 5
    const val PRIVATE_FEE = 50_000
    const val COINS = 995

    /** Where he spawns, and how far he wanders, in the open lair and (moved) in a private one. */
    val SPAWN = osrsps.map.Tile(2269, 4697)
    const val WANDER = 10

    /** The private lair copies his whole map square. */
    const val LAIR_X = 35 shl 6
    const val LAIR_Z = 73 shl 6
    const val LAIR_ZONES = 8

    /** His locs whose handlers work in a private lair: the lever out. */
    val INSTANCE_LOCS: Set<Int> = setOf(LEVER_OUT)

    const val ATTACK_SPEED = 4
    const val MELEE_MAX = 25

    const val FIRE_MAX = 65
    const val FIRE_SHIELD = 15
    const val FIRE_PRAYER = 20
    const val SPECIAL_MAX = 50
    const val SPECIAL_SHIELD = 10
    const val SPECIAL_PRAYER = 15

    const val POISON = 8
    const val EFFECT_UNPROTECTED = 2
    const val EFFECT_PROTECTED = 8
    const val SHOCK_DRAIN = 2
    const val FREEZE_TICKS = 10

    const val ANIM_MELEE = 80
    const val ANIM_BREATH = 1990
    const val SPOT_FIRE = 393
    const val SPOT_TOXIC = 394
    const val SPOT_ICE = 395
    const val SPOT_SHOCK = 396

    /** The lair: its map square. */
    fun inLair(x: Int, z: Int): Boolean = x shr 6 == 35 && z shr 6 == 73

    enum class Breath(val spot: Int, val special: Boolean) {
        FIRE(SPOT_FIRE, false), TOXIC(SPOT_TOXIC, true), SHOCK(SPOT_SHOCK, true), ICE(SPOT_ICE, true);

        /** What a player with no protection is told as it hits: each breath its own line (the Wiki's King Black Dragon). */
        val line get() = when (this) {
            FIRE -> KingBlackDragonLines.FIERY
            TOXIC -> KingBlackDragonLines.TOXIC
            SHOCK -> KingBlackDragonLines.SHOCKING
            ICE -> KingBlackDragonLines.ICY
        }
    }

    /** The most [breath] can hit for, with or without an anti-dragon [shield] and Protect from Magic ([prayer]). */
    fun cap(breath: Breath, shield: Boolean, prayer: Boolean): Int = when {
        shield -> if (breath.special) SPECIAL_SHIELD else FIRE_SHIELD
        prayer -> if (breath.special) SPECIAL_PRAYER else FIRE_PRAYER
        else -> if (breath.special) SPECIAL_MAX else FIRE_MAX
    }

    fun def(world: World): EncounterDef? {
        val type = world.npcTypes[BOSS] ?: return null
        return EncounterDef(
            "kbd", type,
            // Unleashed: he fights anywhere in his lair, not only near his spawn.
            listOf(PhaseDef("fight", cycle = listOf(CycleStep(1) { tick() }), onStart = { boss.unleashed = true })),
            scriptedAttacks = true,
        )
    }

    /** One in this many hits of a special breath sets off its effect: 8 behind an anti-dragon shield or Protect from Magic, else 2. */
    fun effectChance(shield: Boolean, prayer: Boolean): Int = if (shield || prayer) EFFECT_PROTECTED else EFFECT_UNPROTECTED

    /** How many ticks a breath takes from [boss] to [target]: a magic bolt's flight. */
    fun breathTicks(boss: Npc, target: Player): Int =
        1 + (1 + max(1, LineOfSight.distance(boss.x, boss.z, boss.type.size, target.x, target.z, 1, 1))) / 3

    /** A breath's projectile, arriving as it lands [ticks] later: timed as an NPC's shot ([MeleeCombat.flight]). */
    fun breathProjectile(world: World, boss: Npc, target: Player, breath: Breath, ticks: Int): Projectile =
        world.meleeCombat.flight(boss, target, breath.spot, ticks)

    /** A private lair, him in it; null when the dynamic area has no room. */
    fun openPrivate(world: World): Instance? {
        val exit = WildernessLevers.KBD_DUNGEON_TILE
        val instance = world.instances.create(LAIR_X, LAIR_Z, LAIR_ZONES, LAIR_ZONES, exit.x, exit.z, exit.level) ?: return null
        val type = world.npcTypes[BOSS] ?: return instance
        world.spawnNpc(NpcSpawn(type, instance.baseX + SPAWN.x - LAIR_X, instance.baseZ + SPAWN.z - LAIR_Z, 0, wander = WANDER, source = "kbd private lair"))
        return instance
    }

    /** Takes the fee: from the backpack if it holds it all, else from the bank; false (nothing taken) if neither does. */
    fun pay(world: World, player: Player): Boolean {
        if (player.inventory.count(COINS) >= PRIVATE_FEE) {
            Ned.take(player.inventory, COINS, PRIVATE_FEE)
            return true
        }
        if (player.bank.count(COINS) < PRIVATE_FEE) return false
        world.bank.take(player.bank, world.bankScreen.tabs(player), COINS, PRIVATE_FEE)
        return true
    }

    /** Whether [player] can pay the fee, from the backpack or the bank. */
    fun canPay(player: Player): Boolean = player.inventory.count(COINS) >= PRIVATE_FEE || player.bank.count(COINS) >= PRIVATE_FEE

    private fun EncounterContext.tick() {
        val target = heldTarget() ?: hunt()
        if (target == null) {
            world.npcs.stopFollowing(boss)
            return
        }
        boss.face(target)
        val reaches = world.meleeCombat.reaches(boss, target)
        val sees = world.meleeCombat.sees(boss, target)
        if (sees) world.npcs.stopFollowing(boss) else world.npcs.follow(boss, target)
        // His cooldown is the NPC's own, so a generic strike made before his fight began (as he respawns beside a
        // player) holds his next attack too.
        if (!sees || world.tick < boss.nextAttackTick) return
        boss.nextAttackTick = world.tick + ATTACK_SPEED
        if (reaches && roll(1, 3) == 1) {
            world.meleeCombat.swingAt(boss, target, world.tick, ANIM_MELEE)
            log("swings at ${target.username}")
            return
        }
        val breath = if (roll(1, 2) == 1) Breath.FIRE else Breath.entries[roll(1, 3)]
        breathe(target, breath)
    }

    /**
     * With no one to fight, he takes on the nearest player in his lair he can see, wherever they stand in it (as they
     * come through the lever), tolerance or not. GUESSED: the whole lair as his hunting ground.
     */
    private fun EncounterContext.hunt(): Player? {
        val target = players.filter { !it.dead && it.level == boss.level && world.meleeCombat.sees(boss, it) }
            .minByOrNull { max(kotlin.math.abs(it.x - boss.x), kotlin.math.abs(it.z - boss.z)) } ?: return null
        world.meleeCombat.aggress(boss, target, world.tick)
        // As any NPC taking a target on, his first attack waits for the next tick.
        boss.nextAttackTick = maxOf(boss.nextAttackTick, world.tick + 1)
        log("takes on ${target.username}")
        return heldTarget()
    }

    private fun EncounterContext.breathe(target: Player, breath: Breath) {
        boss.effects.anim(ANIM_BREATH)
        val ticks = breathTicks(boss, target)
        world.zones.projectile(breathProjectile(world, boss, target, breath, ticks))
        val shield = Dragonfire.shielded(target)
        val prayer = world.prayers.protects(target, AttackType.MAGIC)
        val damage = roll(0, cap(breath, shield, prayer))
        log("breathes ${breath.name.lowercase()} at ${target.username} for $damage")
        after(ticks) {
            if (target.dead || players.none { it === target }) return@after
            val dealt = hit(target, damage)
            world.message(target, if (shield || prayer) Dragonfire.line(shield, prayer, resisted = false) else breath.line)
            if (!breath.special || dealt <= 0 || roll(1, effectChance(shield, prayer)) != 1) return@after
            when (breath) {
                Breath.TOXIC -> world.poison.poison(target, POISON * 5)
                Breath.SHOCK -> for (skill in Skill.entries) {
                    if (skill == Skill.HITPOINTS) continue
                    target.skills.setCurrentLevel(skill, max(0, target.skills.currentLevel(skill) - SHOCK_DRAIN))
                }
                Breath.ICE -> if (target.freezeTicks <= 0 && target.freezeImmunityTicks <= 0) {
                    target.freezeTicks = FREEZE_TICKS
                    target.frozenBy = null
                    target.waypoints.clear()
                }
                Breath.FIRE -> Unit
            }
            log("${breath.name.lowercase()} takes hold of ${target.username}")
        }
    }
}

fun registerKingBlackDragon(world: World) {
    with(KingBlackDragon) {
        world.handlers.onLoc(LEVER_IN, COMMUNE) {
            val inside = world.onlinePlayers.count { inLair(it.x, it.z) && it.level == 0 }
            world.message(player, KingBlackDragonLines.COMMUNE, "count" to inside)
        }
        world.handlers.onLoc(LEVER_IN, PRIVATE) {
            arriveDelay()
            if (player.teleblockTicks > 0) return@onLoc world.message(player, PvpLines.TELEPORT_BLOCKED)
            if (!canPay(player)) return@onLoc world.message(player, KingBlackDragonLines.PRIVATE_NO_COINS)
            var yes = false
            world.dialogues.start(this) {
                yes = choice(KingBlackDragonLines.PRIVATE_YES, KingBlackDragonLines.PRIVATE_NO, title = text(KingBlackDragonLines.PRIVATE_TITLE)) == 1
            }
            if (!yes) return@onLoc
            if (!canPay(player)) return@onLoc world.message(player, KingBlackDragonLines.PRIVATE_NO_COINS)
            player.anim(WildernessLevers.PULL_ANIM)
            delay(WildernessLevers.PULL_TICKS)
            player.anim(WildernessLevers.TELEPORT_ANIM)
            player.spotanim(WildernessLevers.TELEPORT_SPOTANIM, WildernessLevers.TELEPORT_SPOTANIM_HEIGHT)
            delay(WildernessLevers.TELEPORT_TICKS)
            player.anim(-1)
            // Made, paid for and gone into in one tick: an instance with nobody in it ends at the tick's end.
            if (player.teleblockTicks > 0) return@onLoc world.message(player, PvpLines.TELEPORT_BLOCKED)
            if (!canPay(player)) return@onLoc world.message(player, KingBlackDragonLines.PRIVATE_NO_COINS)
            val instance = openPrivate(world) ?: return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            pay(world, player)
            val to = WildernessLevers.KBD_LAIR_TILE
            world.teleport(player, instance.baseX + to.x - LAIR_X, instance.baseZ + to.z - LAIR_Z, 0)
        }
        // Not in a cache without him (a test's).
        val def = def(world) ?: return
        world.wildernessBosses.register(WildernessBoss("king black dragon", setOf(BOSS), KILL_COUNT_VARP))
        world.npcScripts.onTick(listOf(BOSS)) { npc ->
            if (npc.dead || world.encounters.all.any { it.boss === npc }) return@onTick
            val instance = world.instances.at(npc.x, npc.z)
            world.encounters.start(def, npc, if (instance != null) instance::contains else { x, z -> inLair(x, z) })
        }
    }
}
