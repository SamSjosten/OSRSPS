package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext

/** Larxus's words, the duels' rules and refusals (Transcript:Larxus). */
object LarxusLines : Lines("larxus", "Transcript:Larxus, read 2026-11-19") {
    val GREETING = line("greeting", "Talk-to")
    val CHALLENGE = line("challenge", "option and player: a champion's scroll held")
    val HAND_IT = line("hand_it", "his answer")
    val PLACE = line("place", "option and player: asking about the guild's basement")
    val PLACE_ANSWER = line("place_answer", "his answer")
    val REWARDS = line("rewards", "option and player: asking for rewards")
    val NO_REWARDS = line("no_rewards", "no champion defeated")
    val CHEST = line("chest", "a lamp waiting in the Chest of Champions")
    val HOLDING_LAMP = line("holding_lamp", "a champion's lamp held")
    val NOTHING_MORE = line("nothing_more", "every lamp claimed")
    val NOTHING = line("nothing", "option and player: leaving the talk")
    val LEON_OFFER = line("leon_offer", "Talk-to once Leon d'Cour has challenged the player")
    val YES = line("yes", "option: accepting a challenge")
    val READY = line("ready", "a challenge accepted")
    val NO = line("no", "option and player: turning a challenge down")
    val CAPE_OFFER = line("cape_offer", "Talk-to after Leon d'Cour is defeated")
    val YES_PLEASE = line("yes_please", "option and player: the cape")
    val NO_SPACE = line("no_space", "the cape with a full inventory")
    val CAPE_BOX = line("cape_box", "the item box for the cape")
    val ANOTHER = line("another", "option and player: another cape")
    val GOODBYE = line("goodbye", "option and player: no more capes")
    val LADDER = line("ladder", "any teleport out of the basement")
    val WAITING = line("waiting", "a scroll used on him with a challenge already arranged")
    val ALREADY_DEFEATED = line("already_defeated", "a scroll of a champion already defeated, which he takes")
    val IRRELEVANT = line("irrelevant", "any other item used on him")
    val ARRANGE_FIRST = line("arrange_first", "the Champion Statue with no challenge arranged")
    val WEAPONS_ONLY = line("weapons_only", "the Ghoul Champion's duel entered with anything but a weapon")
    val NO_WEAPONS_ARMOUR = line("no_weapons_armour", "the Lesser Demon Champion's duel entered with a weapon or armour worn")
    val WEARING_ONLY = line("wearing_only", "Leon d'Cour's duel entered with anything in the inventory")
    val ONLY_RANGED = line("only_ranged", "melee or magic on the Skeleton Champion")
    val ONLY_MAGIC = line("only_magic", "melee or ranged on the Goblin Champion; words unsourced")
    val ONLY_MELEE = line("only_melee", "ranged or magic on the Giant Champion; words unsourced")
    val NO_MAGIC = line("no_magic", "magic on the Zombies Champion; words unsourced")
    val NO_MELEE = line("no_melee", "melee on the Hobgoblin Champion; words unsourced")
    val NO_RANGED = line("no_ranged", "ranged on the Jogre Champion; words unsourced")
    val NO_PRAYER = line("no_prayer", "a prayer in the Earth Warrior Champion's duel")
    val NO_SPECIAL = line("no_special", "a special attack in the Imp Champion's duel; words unsourced")
    val NOT_YOURS = line("not_yours", "another player's champion attacked; words unsourced")

    /** The rules he reads out as a scroll is used on him, by champion. */
    val RULES: Map<ChampionsChallenge.Champion, Line> = ChampionsChallenge.Champion.entries.filter { it.scroll != ChampionsChallenge.LEON_SCROLL }
        .associateWith { line("rules_${it.key}", "the ${it.key} champion's scroll used on him") }
}

/** Leon d'Cour's two lines (Transcript:Leon d'Cour). */
object LeonDCourLines : Lines("leon_dcour", "Transcript:Leon d'Cour, read 2026-11-19") {
    val CHALLENGE = line("challenge", "on the tenth champion's defeat")
    val DEFEATED = line("defeated", "on his defeat")
}

/** The Chest of Champions and the champion's lamp (Transcript:Chest of Champions, Transcript:Champion's lamp). */
object ChampionsChestLines : Lines("champions_chest", "Transcript:Chest of Champions and Transcript:Champion's lamp, read 2026-11-19") {
    val HOLDING_LAMP = line("holding_lamp", "Search with a lamp held")
    val NO_SPACE = line("no_space", "a lamp waiting and no room")
    val NOTHING = line("nothing", "no lamp waiting")
    val LAMP_TITLE = line("lamp_title", "the lamp's menu", "xp")
    val PROCEED = line("proceed", "the lamp's menu: yes")
    val CANCEL = line("cancel", "the lamp's menu: no")
    val GAINED = line("gained", "the lamp rubbed", "xp")
    val SCROLL_DROPPED = line("scroll_dropped", "a champion's scroll dropped by a monster (Champion scroll page)")
    val FUNNY_FEELING = line("funny_feeling", "a champion's scroll rolled for a champion already defeated (Champion scroll page)")

    /** The box as the chest gives each champion's lamp. */
    val REWARD: Map<ChampionsChallenge.Champion, Line> =
        ChampionsChallenge.Champion.entries.associateWith { line("reward_${it.key}", "the chest's lamp for the ${it.key} champion") }

    /** The box as each champion's lamp is rubbed (the Wiki has the imp's and the zombie's; the rest unsourced). */
    val LAMP: Map<ChampionsChallenge.Champion, Line> =
        ChampionsChallenge.Champion.entries.associateWith { line("lamp_${it.key}", "Rub on the ${it.key} champion's lamp", "xp") }
}

/**
 * The Champions' Challenge in the Champions' Guild's basement (post-quest step 9; every account has the 32 quest points
 * it asks for):
 *
 * - **Larxus** ([LARXUS], npc-spawns/champions-challenge.json) talks as the Wiki's transcript has him: what the place
 *   is, rewards (the chest), Leon d'Cour's challenge once ten champions are beaten, and the champion's cape, as many as
 *   the player wants, once Leon is. A champion's scroll used on him reads out its duel's rules and, on yes, arranges the
 *   duel, keeping the scroll ([CustomVars.championsVarp] holds the champion arranged); a scroll of a champion already beaten is void and he
 *   takes it; any other item gets his line sending the player off to find a champion.
 * - **The Champion Statue** ([STATUE], by Larxus) opens only with a duel arranged and its Climb-down checks the duel's
 *   items (the ghoul's: a weapon worn and nothing else; the lesser demon's: nothing worn; Leon's: nothing carried) and
 *   puts the player in the arena with their champion, who fights them alone ([fights]); the duel's items are held to at
 *   every attack too ([ruled]).
 * - **The duel's rules** ([Rule]), refused as they are broken: one kind of attack only or one kind barred
 *   ([osrsps.game.combat.Combat.ruled], by the attack's own type, or the style set), no prayer (any on go off as the
 *   duel starts, and [osrsps.game.combat.Prayers.barred])
 *   and no special attacks ([osrsps.game.combat.SpecialAttacks.barred]). No teleport leaves the basement
 *   ([osrsps.game.combat.Teleblock.refusal]; Larxus points to the ladder).
 * - **The portcullis** ([PORTCULLIS]) lets the player out of the arena without ending the duel; **the ladder** behind it
 *   ([ARENA_LADDER]) takes them up beside Larxus and ends it, the champion gone (the Wiki). Logging out or dying ends it
 *   too; the arrangement stays. A death there leaves the grave beside the guild's trapdoor upstairs ([deathPlace]).
 * - **A champion beaten** gives the Wiki's Hitpoints and Slayer XP, sets its bit of varp 617
 *   (`champions_defeated`: its banner on the north wall shows, varbits 1452 to 1462, and the Chest of Champions opens,
 *   varbit 6278), and the tenth brings Leon d'Cour's challenge. Leon beaten, his line and the cape from Larxus.
 * - **The Chest of Champions** ([CHEST], the map's multiloc on varbit 6278) gives a champion's lamp ([LAMP]) for each
 *   champion beaten whose lamp isn't claimed (varbit 6279, `champions_lamps`, the high half of varp 617); the lamp gives
 *   ten times the champion's XP in Hitpoints and Slayer after its menu, and is claimed once rubbed.
 * - **The scrolls** drop at 1 in 5,000 from each champion's monsters (npc-drops.json), with the Wiki's red message; a
 *   champion already beaten drops none, the funny-feeling message in its place ([filterDrops]).
 *
 * Sources: the OSRS Wiki's Champions' Challenge, Larxus, Champion Statue, Chest of Champions, Champion's lamp, Champion
 * scroll, Leon d'Cour and each champion's page and their transcripts, read 2026-11-19; ids, varp 617's varbits and the
 * map from the 242 cache. Inferred: the bits of varp 617 in the order of their varbits (1452 earth warrior to 1462
 * human), the chest's lamps in varbit 6279 by the same bits. Guessed: the arena tile ([ARENA]) and the champion's
 * ([CHAMPION_SPAWN]), the tile past the portcullis ([GATE]) and the one the ladder puts the player on ([GALLERY]); the
 * arena not being an instance, each champion fighting its own player alone; the rules judged by the attack style set,
 * the refusals as messages (the Wiki has them as Larxus's lines); the statue swapped for everyone, as a door is; each
 * chathead expression; no animation on Climb-down. Left out: the scrolls' Read, the lesser demon's check for the
 * materials to make them, the music unlock, Leon's champion scroll (unobtainable on live), the drop's members-world and
 * quest point checks (every account has the points).
 */
class ChampionsChallenge(private val world: World) {
    /** The duel each champion fights under. */
    enum class Rule {
        NO_SPECIAL, ONLY_MAGIC, ONLY_RANGED, NO_MAGIC, ONLY_MELEE, NO_MELEE, WEAPON_ONLY, NO_PRAYER, NO_RANGED, NOTHING_WORN, WORN_ONLY;

        /**
         * Larxus's line for an attack the rule bars, null for the rules that bar none. A getter, not a field: [LarxusLines]
         * builds its rules from [Champion], which is built from these, so reading his lines here at start-up would loop.
         */
        val refusal: Line?
            get() = when (this) {
                ONLY_MAGIC -> LarxusLines.ONLY_MAGIC
                ONLY_RANGED -> LarxusLines.ONLY_RANGED
                NO_MAGIC -> LarxusLines.NO_MAGIC
                ONLY_MELEE -> LarxusLines.ONLY_MELEE
                NO_MELEE -> LarxusLines.NO_MELEE
                NO_RANGED -> LarxusLines.NO_RANGED
                else -> null
            }

        /** Whether an attack of [type] is allowed. */
        fun allows(type: AttackType): Boolean {
            val melee = type != AttackType.MAGIC && type != AttackType.RANGED
            return when (this) {
                ONLY_MAGIC -> type == AttackType.MAGIC
                ONLY_RANGED -> type == AttackType.RANGED
                ONLY_MELEE -> melee
                NO_MAGIC -> type != AttackType.MAGIC
                NO_MELEE -> !melee
                NO_RANGED -> type != AttackType.RANGED
                else -> true
            }
        }
    }

    /**
     * One champion: its [key] for lines, NPC, scroll, its bit of varp 617 (and of the lamps' half), the Hitpoints and
     * Slayer XP a win gives (the lamp gives ten times it) and its duel's [rule]. The Wiki's Champions' Challenge table.
     */
    enum class Champion(val key: String, val npc: Int, val scroll: Int, val bit: Int, val xp: Int, val rule: Rule) {
        EARTH_WARRIOR("earthwarrior", 3328, 6798, 0, 432, Rule.NO_PRAYER),
        GHOUL("ghoul", 3330, 6799, 1, 400, Rule.WEAPON_ONLY),
        GIANT("giant", 3329, 6800, 2, 280, Rule.ONLY_MELEE),
        GOBLIN("goblin", 3353, 6801, 3, 128, Rule.ONLY_MAGIC),
        HOBGOBLIN("hobgoblin", 3354, 6802, 4, 232, Rule.NO_MELEE),
        IMP("imp", 3355, 6803, 5, 160, Rule.NO_SPECIAL),
        JOGRE("jogre", 3356, 6804, 6, 480, Rule.NO_RANGED),
        LESSER_DEMON("lesserdemon", 3357, 6805, 7, 592, Rule.NOTHING_WORN),
        SKELETON("skeleton", 3358, 6806, 8, 232, Rule.ONLY_RANGED),
        ZOMBIE("zombie", 3359, 6807, 9, 240, Rule.NO_MAGIC),
        LEON("human", 3360, LEON_SCROLL, 10, 492, Rule.WORN_ONLY),
        ;

        companion object {
            /** The ten whose scrolls drop; Leon is challenged by beating them. */
            val TEN: List<Champion> = entries.filter { it != LEON }

            fun ofScroll(obj: Int): Champion? = TEN.firstOrNull { it.scroll == obj }

            fun ofNpc(npc: Int): Champion? = entries.firstOrNull { it.npc == npc }
        }
    }

    /** The champion fighting each player now, by save key. */
    private val fights = HashMap<String, Npc>()

    fun defeated(player: Player, champion: Champion): Boolean = player.vars[VARP_DEFEATED] and (1 shl champion.bit) != 0

    fun lampClaimed(player: Player, champion: Champion): Boolean = player.vars[VARP_DEFEATED] and (1 shl (LAMP_SHIFT + champion.bit)) != 0

    private fun setBit(player: Player, bit: Int) {
        player.vars[VARP_DEFEATED] = player.vars[VARP_DEFEATED] or (1 shl bit)
    }

    /** The champion [player] has arranged to fight, if any. */
    fun arranged(player: Player): Champion? {
        val varp = player.vars.types.namedVarps[CustomVars.CHAMPIONS] ?: return null
        return Champion.entries.getOrNull(player.vars[varp] - 1)
    }

    private fun arrange(player: Player, champion: Champion?) {
        val varp = player.vars.types.namedVarps[CustomVars.CHAMPIONS] ?: return
        player.vars[varp] = champion?.let { it.ordinal + 1 } ?: 0
    }

    /** The champion fighting [player] now, alive, if any. */
    fun fightOf(player: Player): Npc? = fights[player.saveKey]?.takeIf { !it.dead && world.npcs[it.index] === it }

    /** The player [npc] is a champion of, if it is one in a duel. */
    private fun ownerOf(npc: Npc): String? = fights.entries.firstOrNull { it.value === npc }?.key

    /** The lamps waiting in the chest for [player]: beaten champions whose lamps aren't claimed. */
    fun lampsWaiting(player: Player): List<Champion> = Champion.entries.filter { defeated(player, it) && !lampClaimed(player, it) }

    /** Whether [player] holds a champion's lamp. */
    private fun holdsLamp(player: Player): Boolean = player.inventory.count(LAMP) > 0

    /** Whether ten champions are beaten and Leon not yet. */
    fun leonWaiting(player: Player): Boolean = Champion.TEN.all { defeated(player, it) } && !defeated(player, Champion.LEON)

    // ---- Larxus ----------------------------------------------------------------------------------------------------

    suspend fun ScriptContext.talk(npc: Npc) {
        val larxus = this@ChampionsChallenge
        world.dialogues.start(this, npc) {
            if (larxus.defeated(player, Champion.LEON)) {
                chatNpc(ChatAnim.HAPPY, LarxusLines.CAPE_OFFER)
                when (choice(LarxusLines.YES_PLEASE, LarxusLines.PLACE, LarxusLines.REWARDS, LarxusLines.NOTHING)) {
                    1 -> {
                        chatPlayer(ChatAnim.NEUTRAL, LarxusLines.YES_PLEASE)
                        while (true) {
                            val cape = world.objTypes[CAPE] ?: return@start
                            if (player.inventory.add(cape, 1) == 0) return@start chatNpc(ChatAnim.NEUTRAL, LarxusLines.NO_SPACE)
                            objbox(CAPE, LarxusLines.CAPE_BOX)
                            if (choice(LarxusLines.ANOTHER, LarxusLines.GOODBYE) != 1) return@start chatPlayer(ChatAnim.HAPPY, LarxusLines.GOODBYE)
                            chatPlayer(ChatAnim.NEUTRAL, LarxusLines.ANOTHER)
                        }
                    }
                    2 -> place()
                    3 -> larxus.rewardsTalk(this)
                    else -> chatPlayer(ChatAnim.NEUTRAL, LarxusLines.NOTHING)
                }
                return@start
            }
            if (larxus.leonWaiting(player) && larxus.arranged(player) != Champion.LEON) {
                chatNpc(ChatAnim.NEUTRAL, LarxusLines.LEON_OFFER)
                when (choice(LarxusLines.YES, LarxusLines.NO, LarxusLines.REWARDS)) {
                    1 -> {
                        larxus.arrange(player, Champion.LEON)
                        chatNpc(ChatAnim.HAPPY, LarxusLines.READY)
                    }
                    2 -> chatPlayer(ChatAnim.NEUTRAL, LarxusLines.NO)
                    else -> larxus.rewardsTalk(this)
                }
                return@start
            }
            chatNpc(ChatAnim.NEUTRAL, LarxusLines.GREETING)
            val scroll = Champion.TEN.any { player.inventory.count(it.scroll) > 0 }
            val options = listOfNotNull(LarxusLines.CHALLENGE.takeIf { scroll }, LarxusLines.PLACE, LarxusLines.REWARDS, LarxusLines.NOTHING)
            when (options[choice(*options.toTypedArray()) - 1]) {
                LarxusLines.CHALLENGE -> {
                    chatPlayer(ChatAnim.NEUTRAL, LarxusLines.CHALLENGE)
                    chatNpc(ChatAnim.NEUTRAL, LarxusLines.HAND_IT)
                }
                LarxusLines.PLACE -> place()
                LarxusLines.REWARDS -> larxus.rewardsTalk(this)
                else -> chatPlayer(ChatAnim.NEUTRAL, LarxusLines.NOTHING)
            }
        }
    }

    private suspend fun osrsps.game.dialogue.Dialogue.place() {
        chatPlayer(ChatAnim.QUIZ, LarxusLines.PLACE)
        chatNpc(ChatAnim.NEUTRAL, LarxusLines.PLACE_ANSWER)
    }

    private suspend fun rewardsTalk(scope: osrsps.game.dialogue.Dialogue) = with(scope) {
        chatPlayer(ChatAnim.QUIZ, LarxusLines.REWARDS)
        chatNpc(
            ChatAnim.NEUTRAL,
            when {
                Champion.entries.none { defeated(player, it) } -> LarxusLines.NO_REWARDS
                holdsLamp(player) -> LarxusLines.HOLDING_LAMP
                lampsWaiting(player).isNotEmpty() -> LarxusLines.CHEST
                else -> LarxusLines.NOTHING_MORE
            },
        )
    }

    /** A champion's scroll (or anything else) used on Larxus. */
    suspend fun ScriptContext.useOn(npc: Npc, obj: Int, slot: Int) {
        val larxus = this@ChampionsChallenge
        val champion = Champion.ofScroll(obj)
        world.dialogues.start(this, npc) {
            when {
                champion == null -> chatNpc(ChatAnim.NEUTRAL, LarxusLines.IRRELEVANT)
                larxus.defeated(player, champion) -> {
                    chatNpc(ChatAnim.NEUTRAL, LarxusLines.ALREADY_DEFEATED)
                    if (player.inventory[slot]?.id == obj) player.inventory.remove(slot, 1)
                }
                larxus.arranged(player) != null -> chatNpc(ChatAnim.NEUTRAL, LarxusLines.WAITING)
                else -> {
                    chatNpc(ChatAnim.NEUTRAL, LarxusLines.RULES.getValue(champion))
                    if (choice(LarxusLines.YES, LarxusLines.NO) == 1) {
                        if (!larxus.takeScroll(player, champion, slot)) return@start
                        larxus.arrange(player, champion)
                        chatNpc(ChatAnim.HAPPY, LarxusLines.READY)
                    } else {
                        chatPlayer(ChatAnim.NEUTRAL, LarxusLines.NO)
                    }
                }
            }
        }
    }

    /**
     * Larxus keeps [champion]'s scroll as the duel is arranged, from [slot] or wherever in the inventory it went during
     * the talk; false when it is gone, and nothing is arranged. Kept at arranging, not at the win, so a scroll banked
     * or dropped after can't be arranged again, and the ghoul's duel (nothing carried but a weapon) still uses it up
     * (Codex review on #948; when live takes it is guessed).
     */
    fun takeScroll(player: Player, champion: Champion, slot: Int): Boolean {
        if (player.inventory[slot]?.id == champion.scroll) return player.inventory.remove(slot, 1) > 0
        val (other, _) = player.inventory.contents().firstOrNull { (_, obj) -> obj.id == champion.scroll } ?: return false
        return player.inventory.remove(other, 1) > 0
    }

    // ---- The statue, the arena and the way out ---------------------------------------------------------------------

    private suspend fun ScriptContext.larxusSays(line: Line) {
        val type = world.npcTypes[LARXUS] ?: return world.message(player, line)
        world.dialogues.start(this) { chatNpc(type, type.name, ChatAnim.NEUTRAL, line) }
    }

    suspend fun ScriptContext.openStatue(loc: osrsps.game.zone.ZoneLoc) {
        arriveDelay()
        if (arranged(player) == null) return larxusSays(LarxusLines.ARRANGE_FIRST)
        swapFurniture(world, loc, STATUE_OPEN, Searching.ANIM_OPEN)
    }

    /** The items [champion]'s duel refuses [player] for, as Larxus's line; null when they may go in. */
    fun itemRefusal(player: Player, champion: Champion): Line? = when (champion.rule) {
        Rule.WEAPON_ONLY -> LarxusLines.WEAPONS_ONLY.takeIf {
            !player.inventory.isEmpty() || player.equipment.contents().any { (slot, _) -> slot != WearPos.WEAPON }
        }
        Rule.NOTHING_WORN -> LarxusLines.NO_WEAPONS_ARMOUR.takeIf { !player.equipment.isEmpty() }
        Rule.WORN_ONLY -> LarxusLines.WEARING_ONLY.takeIf { !player.inventory.isEmpty() }
        else -> null
    }

    suspend fun ScriptContext.climbDown() {
        arriveDelay()
        val champion = arranged(player) ?: return larxusSays(LarxusLines.ARRANGE_FIRST)
        itemRefusal(player, champion)?.let { return larxusSays(it) }
        world.teleport(player, ARENA.x, ARENA.z, 0)
        start(player, champion)
    }

    /** [champion] comes into the arena for [player] and goes for them, any champion of theirs before it gone. */
    fun start(player: Player, champion: Champion): Npc? {
        end(player)
        val type = world.npcTypes[champion.npc] ?: return null
        val npc = world.spawnNpc(NpcSpawn(type, CHAMPION_SPAWN.x, CHAMPION_SPAWN.z, 0, source = "champions' challenge", respawns = false)) ?: return null
        fights[player.saveKey] = npc
        // The earth warrior's duel bars prayer: any already on go off as it starts, this tick's effect too (Codex
        // review on #948), or they'd last the whole duel past Prayers.barred.
        if (champion.rule == Rule.NO_PRAYER) world.prayers.deactivateAll(player)
        world.meleeCombat.provoke(npc, player, world.tick)
        watch(player.saveKey, npc)
        return npc
    }

    /** The duel ends once its player has logged out, died or left the basement, the champion going with it. */
    private fun watch(key: String, npc: Npc) {
        world.npcScripts.later(WATCH_TICKS) {
            if (fights[key] !== npc) return@later
            val player = world.onlinePlayers.firstOrNull { it.saveKey == key }
            if (npc.dead || player == null || player.dead || !inBasement(player.x, player.z, player.level)) return@later endFight(key)
            watch(key, npc)
        }
    }

    /** [player]'s duel is over: the champion goes, if still there. The arrangement stays. */
    fun end(player: Player) = endFight(player.saveKey)

    private fun endFight(key: String) {
        val npc = fights.remove(key) ?: return
        if (!npc.dead && world.npcs[npc.index] === npc) world.removeNpc(npc)
    }

    suspend fun ScriptContext.throughPortcullis() {
        arriveDelay()
        world.teleport(player, GATE.x, GATE.z, 0)
    }

    suspend fun ScriptContext.climbUp() {
        arriveDelay()
        player.anim(Climbing.ANIM_REACH_FOR_LADDER)
        delay(1)
        end(player)
        world.teleport(player, GALLERY.x, GALLERY.z, 0)
    }

    /**
     * Where a player dying in the basement comes back (the spawn) and their grave goes: on the guild's ground floor
     * beside the trapdoor ([GRAVE]), as the Wiki's rules have it, not in the arena; null elsewhere.
     */
    fun deathPlace(player: Player): osrsps.game.combat.DeathPlace? {
        if (!inBasement(player.x, player.z, player.level)) return null
        return osrsps.game.combat.DeathPlace(keepItems = false, GRAVE.x, GRAVE.z, 0, World.SPAWN_X, World.SPAWN_Z, World.SPAWN_LEVEL)
    }

    // ---- Rules -----------------------------------------------------------------------------------------------------

    /** The duel's rule [player] is under now: their champion fighting them, in the basement. */
    private fun ruleOf(player: Player): Rule? = fightOf(player)?.let { Champion.ofNpc(it.type.id)?.rule }

    /** [osrsps.game.combat.Combat.ruled]: another player's champion, or an attack the duel's rule bars. */
    fun ruled(player: Player, npc: Npc, type: AttackType?): Line? {
        val owner = ownerOf(npc) ?: return null
        if (owner != player.saveKey) return LarxusLines.NOT_YOURS
        val champion = Champion.ofNpc(npc.type.id) ?: return null
        // The duel's items are held to at every attack, not only at the statue: gear put on in the arena doesn't fight.
        itemRefusal(player, champion)?.let { return it }
        val rule = champion.rule
        return rule.refusal.takeIf { !rule.allows(type ?: world.combat.style(player).type) }
    }

    fun prayerBarred(player: Player): Line? = LarxusLines.NO_PRAYER.takeIf { ruleOf(player) == Rule.NO_PRAYER }

    fun specialBarred(player: Player): Line? = LarxusLines.NO_SPECIAL.takeIf { ruleOf(player) == Rule.NO_SPECIAL }

    // ---- A champion beaten -----------------------------------------------------------------------------------------

    /** A champion died: if it was [killer]'s duel, the win. */
    fun killed(npc: Npc, killer: Player?) {
        val champion = Champion.ofNpc(npc.type.id) ?: return
        val key = ownerOf(npc) ?: return
        fights.remove(key)
        if (killer == null || killer.saveKey != key) return
        win(killer, champion)
    }

    /** [player] beat [champion]: the XP, the banner and the chest, and Leon's challenge or his cape. */
    fun win(player: Player, champion: Champion) {
        player.skills.addExactTenths(Skill.HITPOINTS, champion.xp * 10)
        player.skills.addExactTenths(Skill.SLAYER, champion.xp * 10)
        setBit(player, champion.bit)
        if (arranged(player) == champion) arrange(player, null)
        val leon = world.npcTypes[Champion.LEON.npc] ?: return
        val line = when {
            champion == Champion.LEON -> LeonDCourLines.DEFEATED
            leonWaiting(player) -> LeonDCourLines.CHALLENGE
            else -> return
        }
        world.scripts.start(player) { world.dialogues.start(this) { chatNpc(leon, leon.name, ChatAnim.NEUTRAL, line) } }
    }

    // ---- The chest and the lamp ------------------------------------------------------------------------------------

    suspend fun ScriptContext.searchChest() {
        arriveDelay()
        if (holdsLamp(player)) return world.dialogues.start(this) { objbox(LAMP, ChampionsChestLines.HOLDING_LAMP) }
        val champion = lampsWaiting(player).firstOrNull() ?: return world.message(player, ChampionsChestLines.NOTHING)
        val lamp = world.objTypes[LAMP] ?: return
        if (player.inventory.add(lamp, 1) == 0) return world.dialogues.start(this) { objbox(LAMP, ChampionsChestLines.NO_SPACE) }
        world.dialogues.start(this) { objbox(LAMP, ChampionsChestLines.REWARD.getValue(champion)) }
    }

    /**
     * Rub on a lamp: it is for the first champion beaten whose lamp isn't used, as the chest gave it (one lamp at a time,
     * so the lamp held is that one), its XP ten times over after the menu, and the lamp counts as claimed only then, so
     * a lamp lost comes again from the chest (the Wiki: "it can be reclaimed from the Chest of Champions").
     */
    suspend fun ScriptContext.rub(slot: Int) {
        val champion = lampsWaiting(player).firstOrNull() ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        val xp = champion.xp * LAMP_TIMES
        var yes = false
        world.dialogues.start(this) {
            objbox(LAMP, text(ChampionsChestLines.LAMP.getValue(champion), "xp" to "%,d".format(xp)))
            yes = choice(text(ChampionsChestLines.PROCEED), text(ChampionsChestLines.CANCEL), title = text(ChampionsChestLines.LAMP_TITLE, "xp" to "%,d".format(xp))) == 1
        }
        if (!yes || player.inventory[slot]?.id != LAMP) return
        if (lampClaimed(player, champion)) return
        player.inventory.remove(slot, 1)
        setBit(player, LAMP_SHIFT + champion.bit)
        player.skills.addExactTenths(Skill.HITPOINTS, xp * 10)
        player.skills.addExactTenths(Skill.SLAYER, xp * 10)
        world.dialogues.start(this) { objbox(LAMP, text(ChampionsChestLines.GAINED, "xp" to "%,d".format(xp))) }
    }

    // ---- The scrolls' drops ----------------------------------------------------------------------------------------

    /**
     * A monster's drops for [killer]: a champion's scroll of a champion they've beaten goes, the funny-feeling message in
     * its place; one kept comes with the scroll's red message.
     */
    fun filterDrops(killer: Player?, drops: List<osrsps.game.combat.Drop>): List<osrsps.game.combat.Drop> {
        if (killer == null || drops.none { Champion.ofScroll(it.type.id) != null }) return drops
        return drops.filter { drop ->
            val champion = Champion.ofScroll(drop.type.id) ?: return@filter true
            val beaten = defeated(killer, champion)
            world.message(killer, if (beaten) ChampionsChestLines.FUNNY_FEELING else ChampionsChestLines.SCROLL_DROPPED)
            !beaten
        }
    }

    companion object {
        const val LARXUS = 3321
        const val TALK_TO = 1

        /** champions_statue_trap and its open form. */
        const val STATUE = 10556
        const val STATUE_OPEN = 10557
        const val OPEN = 1
        const val CLIMB_DOWN = 1
        const val PORTCULLIS = 10553
        const val ARENA_LADDER = 10554
        const val CLIMB_UP = 1

        /** champions_rewardchest: the map's multiloc, closed (22456, Open) at 0, open (31944, Search) once any is beaten. */
        const val CHEST = 31945
        const val CHEST_OP = 1

        const val LAMP = 22320
        const val RUB = 1
        const val LAMP_TIMES = 10
        const val CAPE = 21439
        const val LEON_SCROLL = 6808

        /** champions_defeated: a bit per champion, the lamps claimed in [LAMP_SHIFT] up. */
        const val VARP_DEFEATED = 617
        const val LAMP_SHIFT = 16

        /** Ticks between the checks that a duel's player is still there ([watch]). */
        const val WATCH_TICKS = 2

        /** Where Climb-down puts the player, in the arena west of the portcullis (guessed). */
        val ARENA = DorgeshuunTunnels.Spot(3178, 9758)
        /** Where the champion comes in, in the arena's middle (guessed). */
        val CHAMPION_SPAWN = DorgeshuunTunnels.Spot(3168, 9758)
        /** Past the portcullis, before the ladder (guessed). */
        val GATE = DorgeshuunTunnels.Spot(3182, 9758)
        /** Where the arena's ladder puts the player, beside the statue in Larxus's gallery (guessed). */
        val GALLERY = DorgeshuunTunnels.Spot(3185, 9758)

        /** Where a grave from the basement goes: the rug beside the guild's trapdoor (3190,3355), ground floor (guessed tile). */
        val GRAVE = DorgeshuunTunnels.Spot(3190, 3356)

        /** Whether a tile is in the Champions' Guild's basement, where no teleport works. */
        fun inBasement(x: Int, z: Int, level: Int): Boolean = level == 0 && x in 3150..3191 && z in 9740..9780
    }
}

fun registerChampionsChallenge(world: World, challenge: ChampionsChallenge) = with(challenge) {
    world.combat.ruled = challenge::ruled
    world.prayers.barred = challenge::prayerBarred
    world.specialAttacks.barred = challenge::specialBarred
    world.handlers.onNpc(ChampionsChallenge.LARXUS, ChampionsChallenge.TALK_TO) { op -> talk(op.npc!!) }
    world.handlers.onObjOnNpc(ChampionsChallenge.LARXUS) { op -> useOn(op.npc!!, op.obj, op.sub) }
    world.handlers.onLoc(ChampionsChallenge.STATUE, ChampionsChallenge.OPEN) { op -> openStatue(op.loc!!) }
    world.handlers.onLoc(ChampionsChallenge.STATUE_OPEN, ChampionsChallenge.CLIMB_DOWN) { climbDown() }
    world.handlers.onLoc(ChampionsChallenge.PORTCULLIS, ChampionsChallenge.OPEN) { throughPortcullis() }
    world.handlers.onLoc(ChampionsChallenge.ARENA_LADDER, ChampionsChallenge.CLIMB_UP) { climbUp() }
    world.handlers.onLoc(ChampionsChallenge.CHEST, ChampionsChallenge.CHEST_OP) { searchChest() }
    world.handlers.onObj(ChampionsChallenge.LAMP, ChampionsChallenge.RUB) { op -> rub(op.sub) }
}
