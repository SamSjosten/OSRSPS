package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.Equipment
import osrsps.game.GameRandom
import osrsps.game.Interactions
import osrsps.game.InvObj
import osrsps.game.Movement
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.SpotanimNames
import osrsps.game.WearPos
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcInteractions
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.game.script.Handlers
import osrsps.game.script.ScriptRunner
import osrsps.game.zone.Projectile
import osrsps.map.CollisionMap

/** Casting runs the way World runs it: spells land and NPCs fight in the NPC phase, then each player's interaction. */
class MagicCombatTest {
    private var tick = 0L
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", null, null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    /** No magic defence to speak of: every cast hits. */
    private val dummy = NpcType(2668, "Dummy", ops = listOf(null, "Attack", null, null, null), internalName = "dummy", stats = NpcStats(hitpoints = 10_000))

    /** Magic far past any player's: every cast splashes. */
    private val warded = NpcType(2669, "Warded", ops = listOf(null, "Attack", null, null, null), internalName = "warded", stats = NpcStats(hitpoints = 10_000, magic = 5_000))

    /** The dummy weak to fire, and to water, by 50%. */
    private val fireWeak = NpcType(2670, "Fire-weak dummy", ops = listOf(null, "Attack", null, null, null), internalName = "fire_weak", stats = NpcStats(hitpoints = 10_000))
    private val waterWeak = NpcType(2671, "Water-weak dummy", ops = listOf(null, "Attack", null, null, null), internalName = "water_weak", stats = NpcStats(hitpoints = 10_000))
    private val npcTypes = NpcTypes.of(man, dummy, warded, fireWeak, waterWeak)

    private val fireStrike = spellObj(3279, "13_fire_strike", "Fire Strike", 13, 19, 8, MIND to 1, FIRE to 3, AIR to 2)
    private val runes = listOf(AIR to "airrune", FIRE to "firerune", MIND to "mindrune").map { (id, name) ->
        ObjType(id, name.removeSuffix("rune").replaceFirstChar { it.uppercase() } + " rune", stackable = true, internalName = name)
    }

    // Obj category 1 is the staves (weapon category 18), as Weapons maps it; param 3 magic attack, 14 attack speed.
    private val staffOfFire = ObjType(1387, "Staff of fire", wearpos1 = WearPos.WEAPON, params = mapOf(3 to 10, 14 to 5), internalName = "staff_of_fire", category = 1)
    private val occult = ObjType(12002, "Occult necklace", wearpos1 = WearPos.NECK, params = mapOf(3 to 12, 299 to 50), internalName = "occult_necklace")
    private val objTypes = ObjTypes.of(fireStrike, staffOfFire, occult, *runes.toTypedArray())

    private val spells = Spells.parse(
        """{ "splash": "failedspell_impact", "spells": [ { "spell": "13_fire_strike", "autocast": 4, "element": "fire", "xp": 11.5, "anim": "human_caststrike",
            "staffAnim": "human_caststrike_staff", "casting": "firestrike_casting", "travel": "firestrike_travel", "impact": "firestrike_impact" } ],
          "staves": [ { "items": ["staff_of_fire"], "runes": ["firerune"] } ] }""",
        objTypes, SeqNames(mapOf("human_caststrike" to 711, "human_caststrike_staff" to 1162)),
        SpotanimNames(mapOf("firestrike_casting" to 99, "firestrike_travel" to 100, "firestrike_impact" to 101, "failedspell_impact" to 85)),
    )
    private val npcCombat = NpcCombat.parse(
        """{ "npcs": [ { "npcs": ["man", "dummy"], "attackSpeed": 4, "maxHit": 1, "attackType": "crush", "attackBonus": 0,
            "strengthBonus": 0,
            "defence": { "stab": -21, "slash": -21, "crush": -21, "magic": -64, "light": 0, "standard": 0, "heavy": 0 },
            "anims": { "attack": "punch", "defend": "block", "death": "death" }, "respawnTicks": 25, "source": "test" },
          { "npcs": ["warded"], "attackSpeed": 4, "maxHit": 1, "attackType": "crush", "attackBonus": 0, "strengthBonus": 0,
            "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 500, "light": 0, "standard": 0, "heavy": 0 },
            "anims": { "attack": "punch", "defend": "block", "death": "death" }, "respawnTicks": 25, "source": "test" },
          { "npcs": ["fire_weak"], "attackSpeed": 4, "maxHit": 1, "attackType": "crush", "attackBonus": 0, "strengthBonus": 0,
            "defence": { "stab": -21, "slash": -21, "crush": -21, "magic": -64, "light": 0, "standard": 0, "heavy": 0 },
            "anims": { "attack": "punch", "defend": "block", "death": "death" }, "respawnTicks": 25,
            "weakness": { "element": "fire", "percent": 50 }, "source": "test" },
          { "npcs": ["water_weak"], "attackSpeed": 4, "maxHit": 1, "attackType": "crush", "attackBonus": 0, "strengthBonus": 0,
            "defence": { "stab": -21, "slash": -21, "crush": -21, "magic": -64, "light": 0, "standard": 0, "heavy": 0 },
            "anims": { "attack": "punch", "defend": "block", "death": "death" }, "respawnTicks": 25,
            "weakness": { "element": "water", "percent": 50 }, "source": "test" } ] }""",
        npcTypes, SeqNames(mapOf("punch" to 422, "block" to 424, "death" to 836)),
    )
    private val map = CollisionMap().apply { markLoaded(50, 50) }
    private val random = GameRandom(SEED)
    private val npcs = Npcs(map, GameRandom(1))
    private val handlers = Handlers()
    private val scripts = ScriptRunner(handlers, { it.modals.clear() }) { tick }
    private val movement = Movement(map)
    private val messages = mutableListOf<Pair<Player, String>>()
    private val projectiles = mutableListOf<Pair<Long, Projectile>>()
    private val stopped = mutableListOf<Player>()
    private val combat = Combat(Equipment(objTypes), AttackStyles.DEFAULT, npcCombat)
    private lateinit var melee: MeleeCombat
    private val interactions = NpcInteractions(
        npcs, npcTypes, map, movement, handlers, scripts, { tick },
        message = { player, text -> messages += player to text },
        faceNpc = { _, _ -> },
        clearFlag = {},
        attack = { player, npc -> melee.attack(player, npc) },
    )
    private val shared = Interactions { player, text -> messages += player to text }
    private val alice = Player(1, "alice", 3210, 3210, 0)
    private val bob = Player(2, "bob", 3210, 3212, 0)

    init {
        melee = MeleeCombat(
            npcs, map, movement, combat, random, { tick }, removeNpc = { npcs.remove(it) }, spawnNpc = {},
            message = { player, text -> messages += player to text },
            players = { sequenceOf(alice, bob) },
            ranged = { player, npc -> magic.fight(player, npc) },
        )
    }

    private val magic = MagicCombat(
        npcs, map, movement, combat, objTypes, spells, melee, random, { tick },
        projectile = { projectiles += tick to it },
        message = { player, text -> messages += player to text },
        stopFacing = { stopped += it },
    )

    init {
        for (player in listOf(alice, bob)) {
            player.skills.setLevel(Skill.MAGIC, 13)
            give(player, MIND, 100)
            give(player, FIRE, 300)
            give(player, AIR, 200)
        }
    }

    private fun give(player: Player, rune: Int, count: Int) {
        player.inventory.add(objTypes[rune]!!, count)
    }

    private fun spawn(type: NpcType = man, x: Int = 3215, z: Int = 3210) = npcs.add(NpcSpawn(type, x, z, wander = 0, source = "test"))!!

    private fun cast(player: Player, npc: Npc, component: Int = FIRE_STRIKE) =
        interactions.castOn(player, npc.index, invertRun = false) { magic.cast(player, it, component) }

    private fun tick(vararg players: Player = arrayOf(alice)) {
        tick++
        interactions.releaseNpcs()
        magic.process()
        melee.process()
        npcs.process()
        for (player in players) {
            scripts.process(player)
            shared.process(player) { movement.process(player) }
        }
    }

    private fun count(player: Player, rune: Int) = player.inventory.count(rune).toInt()

    @Test
    fun `a spell is cast once from range with its runes, and lands a tick after the Wiki's magic delay`() {
        val npc = spawn(dummy)
        cast(alice, npc)
        tick()
        assertNull(alice.interaction)
        assertEquals(3210 to 3210, alice.x to alice.z)
        assertEquals(listOf(99, 300 - 3, 200 - 2), listOf(count(alice, MIND), count(alice, FIRE), count(alice, AIR)))
        assertEquals(711, alice.effects.anim?.id)
        assertEquals(99, alice.effects.pendingSpotanims[0]?.id)
        assertEquals(1L + MagicCombat.SPEED, alice.nextAttackTick)
        val (castOn, flight) = projectiles.single()
        assertEquals(1L, castOn)
        assertEquals(100, flight.id)
        assertEquals(-(alice.index + 1) to npc.index + 1, flight.sourceIndex to flight.targetIndex)
        // Distance 5: 1 + (1 + 5) / 3 = 3 ticks, and one more on an NPC, so it lands on tick 5.
        val landsOn = 1 + HitDelay.MAGIC.ticks(5) + HitDelay.ON_NPC
        assertEquals(5, landsOn)
        assertEquals((landsOn - castOn.toInt()) * 30, flight.endTime)
        while (tick < landsOn - 1) {
            tick()
            assertTrue(npc.effects.pendingHits.isEmpty(), "landed early on tick $tick")
        }
        tick()
        val hit = npc.effects.pendingHits.single()
        assertEquals(101, npc.effects.pendingSpotanims.single().id)
        assertEquals(MagicCombat.IMPACT_HEIGHT, npc.effects.pendingSpotanims.single().height)
        assertEquals(alice, npc.attacker)
        // 11.5 Magic XP for the cast and 2 a point of damage; 1.33 Hitpoints a point.
        assertEquals(115 + hit.value * 20, alice.skills.tenths(Skill.MAGIC) - xpAt13)
        assertTrue(messages.isEmpty(), messages.toString())
        // One cast and no more: nothing is cast when the delay is over.
        repeat(6) { tick() }
        assertEquals(1, projectiles.size)
    }

    @Test
    fun `a spell that misses splashes, with the graphic and the base XP, no hitsplat, and the NPC fighting back`() {
        val npc = spawn(warded, x = 3212)
        cast(alice, npc)
        tick()
        val before = alice.skills.tenths(Skill.HITPOINTS)
        repeat(3) { tick() }
        assertEquals(85, npc.effects.pendingSpotanims.single().id)
        assertTrue(npc.effects.pendingHits.isEmpty())
        assertTrue(npc.effects.pendingHeadBars.isEmpty())
        assertEquals(alice, npc.attacker)
        assertEquals(115, alice.skills.tenths(Skill.MAGIC) - xpAt13)
        assertEquals(before, alice.skills.tenths(Skill.HITPOINTS))
        // It turns on the caster as a hit NPC does.
        assertEquals(alice, npc.target)
    }

    @Test
    fun `out of range the player walks only until the NPC is within 10 tiles`() {
        val npc = spawn(dummy, x = 3225)
        cast(alice, npc)
        var castFrom: Int? = null
        repeat(8) {
            tick()
            if (castFrom == null && projectiles.isNotEmpty()) castFrom = alice.x
        }
        assertEquals(3215, castFrom)
        assertEquals(3215 to 3210, alice.x to alice.z)
    }

    @Test
    fun `a cast waits for the attack delay a swing or shot left`() {
        val npc = spawn(dummy)
        alice.nextAttackTick = 3
        cast(alice, npc)
        tick()
        tick()
        assertTrue(projectiles.isEmpty())
        tick()
        assertEquals(3L, projectiles.single().first)
    }

    @Test
    fun `too low a level or too few runes refuses the cast with the message, and takes nothing`() {
        val npc = spawn(dummy)
        alice.skills.setLevel(Skill.MAGIC, 12)
        cast(alice, npc)
        tick()
        assertEquals(listOf(alice to MagicCombat.LEVEL_TOO_LOW), messages)
        assertNull(alice.interaction)
        assertEquals(listOf(alice), stopped)
        assertNull(alice.effects.anim)
        messages.clear()

        alice.skills.setLevel(Skill.MAGIC, 13)
        val slot = alice.inventory.contents().first { it.second.id == FIRE }.first
        alice.inventory.remove(slot, 298)
        cast(alice, npc)
        tick()
        assertEquals(listOf(alice to "You do not have enough Fire Runes to cast this spell."), messages)
        assertEquals(listOf(100, 2, 200), listOf(count(alice, MIND), count(alice, FIRE), count(alice, AIR)))
        assertTrue(projectiles.isEmpty())
    }

    @Test
    fun `a staff of fire stands in for the fire runes, and casts with the staff animation`() {
        val npc = spawn(dummy)
        val slot = alice.inventory.contents().first { it.second.id == FIRE }.first
        alice.inventory.remove(slot)
        alice.equipment[WearPos.WEAPON] = InvObj(staffOfFire.id, 1)
        cast(alice, npc)
        tick()
        assertTrue(messages.isEmpty(), messages.toString())
        assertEquals(listOf(99, 0, 198), listOf(count(alice, MIND), count(alice, FIRE), count(alice, AIR)))
        assertEquals(1162, alice.effects.anim?.id)
    }

    @Test
    fun `magic damage bonus raises the max hit, and magic attack bonus the attack roll`() {
        val npc = spawn(dummy)
        val plain = combat.magicMatchup(alice, npc, spells.spell(FIRE_STRIKE)!!)!!
        assertEquals(8, plain.maxHit)
        assertEquals((13 + 8) * 64, plain.attackRoll)
        assertEquals(9 * 0, plain.defenceRoll)
        alice.equipment[WearPos.NECK] = InvObj(occult.id, 1)
        alice.skills.setLevel(Skill.MAGIC, 99)
        val geared = combat.magicMatchup(alice, npc, spells.spell(FIRE_STRIKE)!!)!!
        // 8 * 1.05 = 8.4, rounded down.
        assertEquals(8, geared.maxHit)
        assertEquals((99 + 8) * (12 + 64), geared.attackRoll)
    }

    @Test
    fun `a monster weak to the spell's element takes its percentage more accuracy and max hit`() {
        val strike = spells.spell(FIRE_STRIKE)!!
        val plain = combat.magicMatchup(alice, spawn(dummy), strike)!!
        val weak = combat.magicMatchup(alice, spawn(fireWeak, z = 3211), strike)!!
        // 8 + 8 x 50%, and the attack roll times 1.5.
        assertEquals(12, weak.maxHit)
        assertEquals(plain.attackRoll * 3 / 2, weak.attackRoll)
        assertEquals(plain.defenceRoll, weak.defenceRoll)
        // A weakness to another element does nothing for Fire Strike.
        assertEquals(plain, combat.magicMatchup(alice, spawn(waterWeak, z = 3212), strike))
    }

    @Test
    fun `Fire Strike on a fire-weak monster hits past its own max hit, up to the Wiki's`() {
        val npc = spawn(fireWeak, x = 3212)
        autocast(alice)
        // Its punches would kill her before the 40th cast.
        alice.god = true
        melee.attackFromAfar(alice, npc)
        repeat(40 * MagicCombat.SPEED) { tick() }
        val hits = npc.effects.pendingHits.map { it.value }
        assertEquals(40, hits.size)
        assertTrue(hits.max() in 9..12, "hits $hits")
    }

    @Test
    fun `single-way rules refuse a cast on an NPC someone else is fighting`() {
        val npc = spawn(dummy)
        cast(bob, npc)
        // Bob is 5 tiles away: his spell lands on tick 5.
        repeat(5) { tick(alice, bob) }
        assertEquals(bob, npc.attacker)
        cast(alice, npc)
        tick(alice, bob)
        assertEquals(listOf(alice to MeleeCombat.SOMEONE_ELSE), messages)
        assertNull(alice.interaction)
    }

    @Test
    fun `a spell that isn't a combat spell here does nothing`() {
        val npc = spawn(dummy)
        cast(alice, npc, component = 11)
        tick()
        assertNull(alice.interaction)
        assertTrue(projectiles.isEmpty())
        assertEquals(100, count(alice, MIND))
    }

    private fun autocast(player: Player, defensive: Boolean = false) {
        player.equipment[WearPos.WEAPON] = InvObj(staffOfFire.id, 1)
        Autocast.set(player, spells.autocast(4)!!, defensive)
    }

    /** The ticks the player cast on, from the projectiles. */
    private fun castTicks() = projectiles.map { it.first }

    @Test
    fun `autocasting, Attack casts the picked spell every 5 ticks until the NPC is dead`() {
        val npc = spawn(man)
        autocast(alice)
        assertEquals(AttackType.MAGIC, combat.style(alice).type)
        assertTrue(melee.attackFromAfar(alice, npc))
        while (!npc.dead && tick < 200) tick()
        assertTrue(npc.dead)
        val casts = castTicks()
        assertTrue(casts.isNotEmpty())
        assertEquals(casts.indices.map { 1L + it * MagicCombat.SPEED }, casts)
        // The staff stands in for the fire runes; the rest go a cast at a time.
        assertEquals(listOf(100 - casts.size, 300, 200 - 2 * casts.size), listOf(count(alice, MIND), count(alice, FIRE), count(alice, AIR)))
        // 11.5 a cast and 2 a point of the man's 7 hitpoints, all to Magic.
        assertEquals(casts.size * 115 + 7 * 20, alice.skills.tenths(Skill.MAGIC) - xpAt13)
        assertEquals(0, alice.skills.tenths(Skill.DEFENCE))
        repeat(10) { tick() }
        assertEquals(casts.size, projectiles.size)
    }

    @Test
    fun `defensive autocast gives Defence 1 and Magic 1·33 a point of damage, the base XP to Magic`() {
        val npc = spawn(dummy)
        autocast(alice, defensive = true)
        assertEquals(listOf(Skill.MAGIC, Skill.DEFENCE), combat.style(alice).xp)
        melee.attackFromAfar(alice, npc)
        repeat(16) { tick() }
        assertEquals(4, projectiles.size)
        // The XP comes with each cast, for what it rolled: what has landed and what is on its way.
        val rolled = dummy.stats.hitpoints - npc.hitpoints + npc.incoming
        assertTrue(rolled > 0)
        assertEquals(rolled * 10, alice.skills.tenths(Skill.DEFENCE))
        // 1.33 a point, rounded down a cast at a time.
        val magic = alice.skills.tenths(Skill.MAGIC) - xpAt13 - 4 * 115
        assertTrue(magic in rolled * 40 / 3 - 3..rolled * 40 / 3, "$magic tenths for $rolled damage")
    }

    @Test
    fun `out of runes the autocast fight ends with the message`() {
        val npc = spawn(dummy)
        alice.inventory.remove(alice.inventory.contents().first { it.second.id == MIND }.first)
        give(alice, MIND, 2)
        autocast(alice)
        melee.attackFromAfar(alice, npc)
        repeat(12) { tick() }
        assertEquals(2, projectiles.size)
        assertEquals("You do not have enough Mind Runes to cast this spell.", messages.single().second)
        assertNull(alice.interaction)
    }

    @Test
    fun `autocast going off mid-fight hands the fight to melee`() {
        val npc = spawn(dummy, x = 3211)
        autocast(alice)
        melee.attackFromAfar(alice, npc)
        tick()
        assertEquals(1, projectiles.size)
        Autocast.clear(alice)
        tick()
        assertTrue(alice.interaction is MeleeInteraction, "${alice.interaction}")
    }

    @Test
    fun `a spell picked without a staff to cast it is kept, and the player punches`() {
        Autocast.set(alice, spells.autocast(4)!!, defensive = false)
        assertEquals(4, Autocast.key(alice))
        assertEquals(AttackType.CRUSH, combat.style(alice).type)
        assertNull(magic.fight(alice, spawn(dummy)))
    }

    @Test
    fun `auto retaliate autocasts, with half the spell's delay`() {
        val npc = spawn(dummy, x = 3212)
        autocast(alice)
        melee.hitPlayer(alice, npc, 1, tick)
        assertTrue(alice.interaction is MagicInteraction)
        assertEquals(tick + 3, alice.nextAttackTick)
    }

    @Test
    fun `a spell cast from the book while autocasting is followed by the autocast spell`() {
        val npc = spawn(dummy)
        autocast(alice)
        cast(alice, npc)
        tick()
        val interaction = alice.interaction as MagicInteraction
        assertNull(interaction.spell)
        repeat(5) { tick() }
        assertEquals(listOf(1L, 6L), castTicks())
    }

    /** Magic XP in tenths at level 13, where every test starts. */
    private val xpAt13 = alice.skills.tenths(Skill.MAGIC)

    private fun spellObj(id: Int, internalName: String, name: String, level: Int, component: Int, maxHit: Int, vararg runes: Pair<Int, Int>): ObjType {
        val params = HashMap<Int, Any>()
        params[Spells.PARAM_NAME] = name
        params[Spells.PARAM_LEVEL] = level
        params[Spells.PARAM_COMPONENT] = (Spells.SPELLBOOK shl 16) or component
        params[Spells.PARAM_MAX_HIT] = maxHit
        runes.forEachIndexed { i, (rune, count) ->
            val (runeParam, countParam) = Spells.PARAM_RUNES[i]
            params[runeParam] = rune
            params[countParam] = count
        }
        return ObjType(id, name, params = params, internalName = internalName)
    }

    companion object {
        const val SEED = 7L
        const val FIRE_STRIKE = 19
        const val AIR = 556
        const val FIRE = 554
        const val MIND = 558
    }
}
