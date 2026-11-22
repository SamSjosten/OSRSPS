package osrsps.game.content

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.WearPos
import osrsps.game.combat.PoweredStaves
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.script.ScriptContext
import osrsps.game.skill.MakeRecipe
import osrsps.game.skill.ObjCount
import osrsps.game.skill.Smithing

/** The rat bone weapons' words (keys only; the words are in data/dialogue.txt). */
object RatBoneLines : Lines("rat_bone", "Rat bone weapons") {
    val ONLY_RATS = line("only_rats", "a rat bone weapon used on anything but a rat (OSRS Wiki, Bone mace, Bone shortbow, Bone staff)")
    val MADE = line("made", "a rat bone weapon made (no transcript)", "item")
    val TOO_LOW = line("too_low", "making one below the level (no transcript)", "level", "item")
    val STAFF_RUNES = line("staff_runes", "a bone staff without 1,000 chaos runes (no transcript)")
    val STAFF_FULL = line("staff_full", "another bone staff while the charges held can't take its 1,000 more (no transcript)")
    val LAMP_MORE = line("lamp_more", "the combat lamp's skill menu: its option to the rest of the skills (guessed menu)")
    val LAMP_RUBBED = line("lamp_rubbed", "the combat lamp rubbed (no transcript)", "skill", "xp")
}

/**
 * The rat bone weapons made from Scurrius's spine (Scurrius step 4; OSRS Wiki, Bone mace, Bone shortbow, Bone staff and
 * Historian Aldo, read 2026-11-19; ids from the 242 cache):
 *
 * - **Making them**: the bone mace (28792) from a rune mace and the spine on an anvil with a hammer at 35 Smithing, 300
 *   XP, 5 ticks; the bone shortbow (28794) from a yew shortbow and the spine with a knife at 35 Fletching, 300 XP; the
 *   bone staff (28796) from a battlestaff, 1,000 chaos runes and the spine with a chisel at 35 Crafting, 300 XP, the
 *   runes becoming its first 1,000 charges ([PoweredStaves.BONE_STAFF]). Historian Aldo makes any of them for 50,000
 *   coins and no level ([Scurrius]'s talk). GUESSED: the animations (the anvil's 898; none for the knife and chisel)
 *   and the made and level messages.
 * - **Rats only**: "You can only use this weapon against rats", manual spells too, while one is wielded. **+10** to the
 *   max hit against rats with any of them ("a static +10 in the code", Mod Ash), and **no attack cooldown** against
 *   Scurrius's giant rats: the next attack may come on the next tick. INFERRED: which NPCs are rats: any whose name has
 *   the word rat or rats in it, and Scurrius.
 * - **The combat lamp** (`rat_boss_lamp` 28800), one for each spine traded to Aldo: Rub gives 5,000 XP in a combat skill
 *   of the player's choice, 3,500 in Prayer. GUESSED: the choice as two menus of skill names (live has its own skill
 *   window).
 */
object RatBoneWeapons {
    const val BONE_MACE = 28792
    const val BONE_SHORTBOW = 28794
    const val BONE_STAFF = PoweredStaves.BONE_STAFF_ID
    const val SPINE = 28798
    const val LAMP = 28800
    const val RUNE_MACE = 1432
    const val YEW_SHORTBOW = 857
    const val BATTLESTAFF = 1391
    const val CHAOS_RUNE = 562
    const val STAFF_RUNES = 1_000
    const val KNIFE = 946
    const val CHISEL = 1755
    const val HAMMER = 2347
    const val LEVEL = 35
    const val XP_TENTHS = 3_000
    const val MAX_HIT_BONUS = 10
    const val LAMP_XP = 5_000
    const val LAMP_PRAYER_XP = 3_500
    const val GIANT_RAT = ScurriusFight.GIANT_RAT

    /** The spot animation on making the staff: the cache's battlestaff_rat_bone_crafting_spotanim. */
    const val SPOT_STAFF = 2637

    val WEAPONS = setOf(BONE_MACE, BONE_SHORTBOW, BONE_STAFF)
    val LAMP_SKILLS = listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS, Skill.RANGED, Skill.MAGIC, Skill.PRAYER)

    private val RAT_WORD = Regex("""\brats?\b""", RegexOption.IGNORE_CASE)

    /** Rat-like by name but not rats to these weapons (OSRS Wiki, Rat (attribute), excluded monsters). */
    private val NOT_RATS = setOf("angry giant rat", "experiment no.2", "mouse")

    /** Whether [npc] is a rat: one named so, bar [NOT_RATS], or Scurrius. */
    fun isRat(npc: Npc): Boolean =
        npc.type.id in ScurriusLoot.TYPES || (RAT_WORD.containsMatchIn(npc.type.name) && npc.type.name.lowercase() !in NOT_RATS)

    fun wields(player: Player): Boolean = player.equipment[WearPos.WEAPON]?.id in WEAPONS

    val MACE = MakeRecipe(
        "rat_bone:mace", Skill.SMITHING, BONE_MACE, 1, LEVEL, XP_TENTHS, listOf(ObjCount(SPINE, 1), ObjCount(RUNE_MACE, 1)), listOf(HAMMER),
        Smithing.ANVIL_ANIM, 5, 5, RatBoneLines.MADE, RatBoneLines.TOO_LOW,
    )

    val SHORTBOW = MakeRecipe(
        "rat_bone:shortbow", Skill.FLETCHING, BONE_SHORTBOW, 1, LEVEL, XP_TENTHS, listOf(ObjCount(SPINE, 1), ObjCount(YEW_SHORTBOW, 1)), listOf(KNIFE),
        -1, 0, 1, RatBoneLines.MADE, RatBoneLines.TOO_LOW,
    )

    /** What [player] holds the materials for, as Aldo asks (no tools, no level, his fee apart). */
    fun materials(player: Player): List<Int> {
        val inv = player.inventory
        if (inv.count(SPINE) < 1) return emptyList()
        return buildList {
            if (inv.count(RUNE_MACE) >= 1) add(BONE_MACE)
            if (inv.count(YEW_SHORTBOW) >= 1) add(BONE_SHORTBOW)
            if (inv.count(BATTLESTAFF) >= 1 && inv.count(CHAOS_RUNE) >= STAFF_RUNES && staffRoom(player)) add(BONE_STAFF)
        }
    }

    /** Whether [player]'s bone staff charges can take a new staff's 1,000 without going over the cap (Codex, PR 878). */
    fun staffRoom(player: Player): Boolean {
        val staff = PoweredStaves.BONE_STAFF
        return player.charges[staff.charge] + STAFF_RUNES <= staff.maxCharges
    }

    /**
     * Takes [weapon]'s materials from [player] and gives the weapon, the staff with its 1,000 charges. False, taking
     * nothing, when they lack any.
     */
    fun make(world: World, player: Player, weapon: Int): Boolean {
        if (weapon !in materials(player)) return false
        val inv = player.inventory
        take(player, SPINE, 1)
        when (weapon) {
            BONE_MACE -> take(player, RUNE_MACE, 1)
            BONE_SHORTBOW -> take(player, YEW_SHORTBOW, 1)
            else -> {
                take(player, BATTLESTAFF, 1)
                take(player, CHAOS_RUNE, STAFF_RUNES)
                val staff = PoweredStaves.BONE_STAFF
                player.charges[staff.charge] += STAFF_RUNES
            }
        }
        world.objTypes[weapon]?.let { inv.add(it, 1) }
        return true
    }

    /** Takes [count] of [id] from [player]'s backpack, from the first stacks on. */
    internal fun take(player: Player, id: Int, count: Int) {
        var left = count
        for ((slot, held) in player.inventory.contents()) {
            if (left == 0) break
            if (held.id == id) left -= player.inventory.remove(slot, left)
        }
    }

    /** The chisel on the spine with a battlestaff and 1,000 chaos runes held: the bone staff. */
    internal fun ScriptContext.craftStaff(world: World) {
        val name = world.objTypes[BONE_STAFF]?.name ?: "Bone staff"
        if (player.inventory.count(CHISEL) < 1 || player.inventory.count(BATTLESTAFF) < 1 || player.inventory.count(SPINE) < 1) return
        if (player.skills.currentLevel(Skill.CRAFTING) < LEVEL) {
            world.message(player, RatBoneLines.TOO_LOW, "level" to LEVEL, "item" to name.lowercase())
            return
        }
        if (player.inventory.count(CHAOS_RUNE) < STAFF_RUNES) {
            world.message(player, RatBoneLines.STAFF_RUNES)
            return
        }
        if (!staffRoom(player)) {
            world.message(player, RatBoneLines.STAFF_FULL)
            return
        }
        if (!make(world, player, BONE_STAFF)) return
        player.effects.spotanim(0, SPOT_STAFF, 0, 0)
        player.skills.addTenths(Skill.CRAFTING, XP_TENTHS)
        world.message(player, RatBoneLines.MADE, "item" to name.lowercase())
    }

    /** Rub on the combat lamp: a combat skill picked, and its XP. */
    internal suspend fun ScriptContext.rub(world: World, slot: Int) {
        var skill: Skill? = null
        world.dialogues.start(this) {
            val first = LAMP_SKILLS.take(4)
            val pick = choice(*first.map { it.displayName }.toTypedArray(), text(RatBoneLines.LAMP_MORE))
            skill = if (pick <= first.size) first[pick - 1] else {
                val rest = LAMP_SKILLS.drop(4)
                rest[choice(*rest.map { it.displayName }.toTypedArray()) - 1]
            }
        }
        val picked = skill ?: return
        // The lamp may have gone while the menu was up.
        if (player.inventory[slot]?.id != LAMP) return
        player.inventory[slot] = null
        val xp = if (picked == Skill.PRAYER) LAMP_PRAYER_XP else LAMP_XP
        player.skills.addTenths(picked, xp * 10)
        world.message(player, RatBoneLines.LAMP_RUBBED, "skill" to picked.displayName, "xp" to xp)
    }
}

fun registerRatBoneWeapons(world: World) = with(RatBoneWeapons) {
    for (anvil in Smithing.ANVILS) {
        for (obj in listOf(SPINE, RUNE_MACE)) world.handlers.onObjOnLoc(anvil, obj) { world.making.choose(this, listOf(MACE), alwaysAsk = false, fromMovement = true) }
    }
    world.handlers.onObjOnObj(KNIFE, SPINE) { world.making.choose(this, listOf(SHORTBOW), alwaysAsk = false) }
    world.handlers.onObjOnObj(SPINE, YEW_SHORTBOW) { world.making.choose(this, listOf(SHORTBOW), alwaysAsk = false) }
    world.handlers.onObjOnObj(CHISEL, SPINE) { craftStaff(world) }
    world.handlers.onObjOnObj(SPINE, BATTLESTAFF) { craftStaff(world) }
    world.handlers.onObj(LAMP, 1) { op -> rub(world, op.sub) }

    val barred = world.combat.weaponBarred
    world.combat.weaponBarred = { player, npc -> if (wields(player) && !isRat(npc)) RatBoneLines.ONLY_RATS else barred(player, npc) }
    val barredOnPlayer = world.combat.weaponBarredOnPlayer
    world.combat.weaponBarredOnPlayer = { player -> if (wields(player)) RatBoneLines.ONLY_RATS else barredOnPlayer(player) }
    val added = world.combat.maxHitAdded
    world.combat.maxHitAdded = { player, npc -> added(player, npc) + if (wields(player) && isRat(npc)) MAX_HIT_BONUS else 0 }
    val noCooldown = world.combat.noCooldown
    world.combat.noCooldown = { player, npc -> (wields(player) && npc.type.id == GIANT_RAT) || noCooldown(player, npc) }
}
