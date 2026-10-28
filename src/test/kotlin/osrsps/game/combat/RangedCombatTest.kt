package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.Equipment
import osrsps.game.EquipmentTab
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
import osrsps.map.LineOfSight
import osrsps.map.LocCollision
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/** Shooting runs the way World runs it: shots land and NPCs fight in the NPC phase, then each player's interaction. */
class RangedCombatTest {
    private var tick = 0L
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", null, null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    /** Stands for anything with a lot of hitpoints, for counting shots. */
    private val dummy = NpcType(2668, "Dummy", ops = listOf(null, "Attack", null, null, null), internalName = "dummy", stats = NpcStats(hitpoints = 10_000))
    /** A mage with 200 Magic, for the twisted bow. */
    private val mage = NpcType(3257, "Mage", ops = listOf(null, "Attack", null, null, null), internalName = "mage", stats = NpcStats(hitpoints = 10_000, magic = 200))
    private val npcTypes = NpcTypes.of(man, dummy, mage)

    // Bonuses as the 241 cache has them: param 4 ranged attack, 12 ranged strength, 14 attack speed.
    private val shortbow = ObjType(841, "Shortbow", wearpos1 = WearPos.WEAPON, params = mapOf(4 to 8, 14 to 4), internalName = "shortbow", category = 64)
    private val darkbow = ObjType(11235, "Dark bow", wearpos1 = WearPos.WEAPON, params = mapOf(4 to 95, 14 to 9), internalName = "darkbow", category = 64)
    private val runeCrossbow = ObjType(9185, "Rune crossbow", wearpos1 = WearPos.WEAPON, params = mapOf(4 to 90, 14 to 6), internalName = "xbows_crossbow_runite", category = 37)
    private val twistedBow = ObjType(20997, "Twisted bow", wearpos1 = WearPos.WEAPON, params = mapOf(4 to 70, 12 to 20, 14 to 6), internalName = "twisted_bow", category = 64)
    private val venator = ObjType(27610, "Venator bow", wearpos1 = WearPos.WEAPON, params = mapOf(4 to 90, 12 to 25, 14 to 5), internalName = "venator_bow", category = 64)
    private val blowpipe = ObjType(Blowpipe.LOADED, "Toxic blowpipe", wearpos1 = WearPos.WEAPON, params = mapOf(4 to 30, 12 to 20, 14 to 3), internalName = "toxic_blowpipe_loaded", category = 24)
    private val emptyBlowpipe = ObjType(Blowpipe.EMPTY, "Toxic blowpipe (empty)", wearpos1 = WearPos.WEAPON, params = mapOf(4 to 30, 14 to 3), internalName = "toxic_blowpipe", category = 24)
    private val dragonDart = ObjType(11230, "Dragon dart", stackable = true, params = mapOf(12 to 35), internalName = "dragon_dart")
    private val sword = ObjType(1277, "Bronze sword", wearpos1 = WearPos.WEAPON, params = mapOf(0 to 4, 14 to 4), internalName = "bronze_sword", category = 25)
    private val bronzeArrow = ObjType(882, "Bronze arrow", stackable = true, wearpos1 = WearPos.AMMO, params = mapOf(12 to 7), internalName = "bronze_arrow")
    private val steelArrow = ObjType(886, "Steel arrow", stackable = true, wearpos1 = WearPos.AMMO, params = mapOf(12 to 16), internalName = "steel_arrow")
    private val runiteBolts = ObjType(9144, "Runite bolts", stackable = true, wearpos1 = WearPos.AMMO, params = mapOf(12 to 115), internalName = "xbows_crossbow_bolts_runite")
    private val dragonBolts = ObjType(21905, "Dragon bolts", stackable = true, wearpos1 = WearPos.AMMO, params = mapOf(12 to 122), internalName = "dragon_bolts")
    private val objTypes = ObjTypes.of(shortbow, darkbow, runeCrossbow, twistedBow, venator, blowpipe, emptyBlowpipe, dragonDart, sword, bronzeArrow, steelArrow, runiteBolts, dragonBolts)

    private val spotanims = SpotanimNames(
        mapOf(
            "bronze_arrow_launch" to 19, "double_bronze_arrow_launch" to 1104, "bronze_arrow_travel" to 10,
            "steel_arrow_launch" to 20, "double_steel_arrow_launch" to 1106, "steel_arrow_travel" to 11,
            "crossbowbolt_travel" to 27, "dragon_crossbowbolt_travel" to 1468,
            "arrow_venator01_launch01" to 2289, "arrow_venator01_travel01" to 2291, "ii_dragon_dart_projanim" to 1122,
        ),
    )
    private val weapons = RangedWeapons.parse(
        """{ "weapons": [
            { "items": ["shortbow"], "ammo": "arrows", "tier": 1, "range": 7, "anim": "human_bow", "source": "test" },
            { "items": ["darkbow"], "ammo": "arrows", "tier": 7, "range": 10, "shots": 2, "anim": "human_bow", "source": "test" },
            { "items": ["twisted_bow"], "ammo": "arrows", "tier": 7, "range": 10, "anim": "human_bow", "scaling": "twisted_bow", "source": "test" },
            { "items": ["venator_bow"], "ammo": "arrows", "tier": 7, "range": 6, "anim": "human_weapon_bow_venator01_shoot", "bounces": 2,
              "launch": "arrow_venator01_launch01", "travel": "arrow_venator01_travel01", "source": "test" },
            { "items": ["toxic_blowpipe_loaded", "toxic_blowpipe"], "ammo": "darts", "tier": 7, "range": 5, "anim": "snakeboss_blowpipe_attack",
              "loaded": true, "delay": "thrown", "source": "test" },
            { "items": ["xbows_crossbow_runite"], "ammo": "bolts", "tier": 7, "range": 7, "anim": "xbows_human_fire_and_reload_pvn", "source": "test" } ],
          "ammo": [
            { "items": ["bronze_arrow"], "ammo": "arrows", "tier": 1, "launch": "bronze_arrow_launch", "doubleLaunch": "double_bronze_arrow_launch", "travel": "bronze_arrow_travel" },
            { "items": ["steel_arrow"], "ammo": "arrows", "tier": 2, "launch": "steel_arrow_launch", "doubleLaunch": "double_steel_arrow_launch", "travel": "steel_arrow_travel" },
            { "items": ["xbows_crossbow_bolts_runite"], "ammo": "bolts", "tier": 7, "travel": "crossbowbolt_travel" },
            { "items": ["dragon_bolts"], "ammo": "bolts", "tier": 8, "travel": "dragon_crossbowbolt_travel" },
            { "items": ["dragon_dart"], "ammo": "darts", "tier": 7, "travel": "ii_dragon_dart_projanim" } ] }""",
        objTypes, SeqNames(mapOf("human_bow" to 426, "xbows_human_fire_and_reload_pvn" to 7552, "human_weapon_bow_venator01_shoot" to 9858, "snakeboss_blowpipe_attack" to 5061)), spotanims,
    )

    // The bow (3) and crossbow (5) rows as the 241 cache has them.
    private val styles = AttackStyles.fromRows(
        listOf(
            listOf<Any>(0) to listOf<Any>(
                0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)", 0,
                1, "Kick", "(Aggressive)<br>(Crush)<br>(Strength XP)", 0,
                3, "Block", "(Defensive)<br>(Crush)<br>(Defence XP)", 0,
            ),
            listOf<Any>(3) to listOf<Any>(
                0, "Accurate", "(Accurate)<br>(Standard)<br>(Ranged XP)", 0,
                1, "Rapid", "(Rapid)<br>(Standard)<br>(Ranged XP)", 0,
                3, "Longrange", "(Longrange)<br>(Standard)<br>(Ranged XP)<br>(Defence XP)", 0,
            ),
            listOf<Any>(19) to listOf<Any>(
                0, "Accurate", "(Accurate)<br>(Standard)<br>(Ranged XP)", 0,
                1, "Rapid", "(Rapid)<br>(Standard)<br>(Ranged XP)", 0,
                3, "Longrange", "(Longrange)<br>(Standard)<br>(Ranged XP)<br>(Defence XP)", 0,
            ),
            listOf<Any>(5) to listOf<Any>(
                0, "Accurate", "(Accurate)<br>(Heavy)<br>(Ranged XP)", 0,
                1, "Rapid", "(Rapid)<br>(Heavy)<br>(Ranged XP)", 0,
                3, "Longrange", "(Longrange)<br>(Heavy)<br>(Ranged XP)<br>(Defence XP)", 0,
            ),
            listOf<Any>(17) to listOf<Any>(
                0, "Stab", "(Accurate)<br>(Stab)<br>(Attack XP)", 0,
                1, "Lunge", "(Aggressive)<br>(Stab)<br>(Strength XP)", 0,
                2, "Slash", "(Aggressive)<br>(Slash)<br>(Strength XP)", 0,
                3, "Block", "(Defensive)<br>(Stab)<br>(Defence XP)", 0,
            ),
        ),
    )
    private val npcCombat = NpcCombat.parse(
        """{ "npcs": [ { "npcs": ["man", "dummy", "mage"], "attackSpeed": 4, "maxHit": 1, "attackType": "crush", "attackBonus": 0,
            "strengthBonus": 0,
            "defence": { "stab": -21, "slash": -21, "crush": -21, "magic": -21, "light": 10, "standard": 0, "heavy": -10 },
            "anims": { "attack": "punch", "defend": "block", "death": "death" }, "respawnTicks": 25, "source": "test" } ] }""",
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
    private val dropped = mutableListOf<Triple<ObjType, Pair<Int, Int>, Player>>()
    private val combat = Combat(Equipment(objTypes), styles, npcCombat, weapons)
    private val multi = MultiCombat()
    private lateinit var melee: MeleeCombat
    private lateinit var ranged: RangedCombat
    private val interactions = NpcInteractions(
        npcs, npcTypes, map, movement, handlers, scripts, { tick },
        message = { player, text -> messages += player to text },
        faceNpc = { _, _ -> },
        clearFlag = {},
        attack = { player, npc -> melee.attack(player, npc) },
        attackFromAfar = { player, npc -> melee.attackFromAfar(player, npc) },
    )
    private val shared = Interactions { player, text -> messages += player to text }
    private val alice = Player(1, "alice", 3210, 3210, 0)
    private val bob = Player(2, "bob", 3210, 3212, 0)

    init {
        melee = MeleeCombat(
            npcs, map, movement, combat, random, { tick }, removeNpc = { npcs.remove(it) }, spawnNpc = {},
            message = { player, text -> messages += player to text },
            players = { sequenceOf(alice, bob) },
            ranged = { player, npc -> ranged.fight(player, npc) },
            multi = multi,
        )
        ranged = RangedCombat(
            npcs, map, movement, combat, objTypes, weapons, melee, random, { tick },
            projectile = { projectiles += tick to it },
            dropAmmo = { type, x, z, _, owner -> dropped += Triple(type, x to z, owner) },
            message = { player, text -> messages += player to text },
            multi = multi,
        )
        wield(alice, shortbow, bronzeArrow, 100)
    }

    private fun wield(player: Player, weapon: ObjType, ammo: ObjType? = null, count: Int = 0) {
        player.equipment[WearPos.WEAPON] = InvObj(weapon.id, 1)
        player.equipment[WearPos.AMMO] = ammo?.let { InvObj(it.id, count) }
    }

    private fun spawn(type: NpcType = man, x: Int = 3215, z: Int = 3210) = npcs.add(NpcSpawn(type, x, z, wander = 0, source = "test"))!!

    private fun tick(vararg players: Player = arrayOf(alice)) {
        tick++
        interactions.releaseNpcs()
        ranged.process()
        melee.process()
        npcs.process()
        for (player in players) {
            scripts.process(player)
            shared.process(player) { movement.process(player) }
        }
    }

    private fun attack(player: Player, npc: Npc) = interactions.click(player, npc.index, 2, -1, invertRun = false)

    private fun ammoLeft(player: Player) = player.equipment[WearPos.AMMO]?.count ?: 0

    @Test
    fun `a shot from range is fired without walking up, and lands a tick after the Wiki's bow delay`() {
        val npc = spawn()
        attack(alice, npc)
        tick()
        assertIs<RangedInteraction>(alice.interaction)
        assertEquals(3210 to 3210, alice.x to alice.z)
        assertEquals(426, alice.effects.anim?.id)
        assertEquals(19, alice.effects.pendingSpotanims[0]?.id)
        assertEquals(99, ammoLeft(alice))
        assertEquals(5L, alice.nextAttackTick)
        val (firedOn, flight) = projectiles.single()
        assertEquals(1L, firedOn)
        assertEquals(10, flight.id)
        assertEquals(-(alice.index + 1) to npc.index + 1, flight.sourceIndex to flight.targetIndex)
        // Distance 5: 1 + (3 + 5) / 6 = 2 ticks, and one more on an NPC, so it lands on tick 4.
        val landsOn = 1 + HitDelay.BOW.ticks(5) + HitDelay.ON_NPC
        assertEquals(4, landsOn)
        assertEquals((landsOn - firedOn.toInt()) * 30, flight.endTime)
        while (tick < landsOn - 1) {
            tick()
            assertTrue(npc.effects.pendingHits.isEmpty(), "landed early on tick $tick")
        }
        tick()
        assertEquals(1, npc.effects.pendingHits.size)
        assertEquals(alice, npc.attacker)
        // The arrow either broke or fell under the man as the hit landed.
        assertTrue(dropped.isEmpty() || dropped.single() == Triple(bronzeArrow, 3215 to 3210, alice), dropped.toString())
        assertTrue(messages.isEmpty(), messages.toString())
    }

    @Test
    fun `out of range the player walks only until the NPC is in range`() {
        val npc = spawn(x = 3220)
        attack(alice, npc)
        var firedAt: Int? = null
        repeat(5) {
            tick()
            if (firedAt == null && projectiles.isNotEmpty()) firedAt = alice.x
        }
        // Range 7 from 3220 is 3213; the player stopped there.
        assertEquals(3213, firedAt)
        assertEquals(3213 to 3210, alice.x to alice.z)
        assertEquals(3L, projectiles.first().first)
    }

    @Test
    fun `ctrl on the click runs the walk into range, and a plain click after it walks again`() {
        val npc = spawn(x = 3220)
        alice.runEnabled = false
        interactions.click(alice, npc.index, 2, -1, invertRun = true)
        tick()
        assertEquals(3212, alice.x)
        alice.interaction = null
        alice.x = 3210
        alice.waypoints.clear()
        attack(alice, npc)
        tick()
        assertEquals(3211, alice.x)
    }

    @Test
    fun `a loc that blocks projectiles makes the player find a tile with a clear line`() {
        LocCollision(map).addLoc(LocCollisionType(blocksProjectile = true), 3212, 3210, 0, LocShape.CENTREPIECE_STRAIGHT, 0)
        val npc = spawn(x = 3214)
        assertTrue(!LineOfSight.clear(map, 0, alice.x, alice.z, 1, npc.x, npc.z))
        attack(alice, npc)
        while (projectiles.isEmpty() && tick < 6) tick()
        // A shot is fired after the step, and the player stops where they fired from.
        assertTrue(projectiles.isNotEmpty())
        assertTrue(alice.x to alice.z != (3210 to 3210))
        assertTrue(LineOfSight.clear(map, 0, alice.x, alice.z, 1, npc.x, npc.z), "fired from ${alice.x}, ${alice.z}")
    }

    @Test
    fun `no ammo, or ammo the weapon can't fire, ends the fight with the message`() {
        val npc = spawn()
        wield(alice, shortbow)
        attack(alice, npc)
        tick()
        assertNull(alice.interaction)
        assertEquals(listOf(alice to RangedCombat.NO_AMMO), messages)
        assertNull(alice.effects.anim)

        for ((weapon, ammo, text) in listOf(
            Triple(shortbow, steelArrow, RangedCombat.WRONG_ARROWS),
            Triple(shortbow, runiteBolts, RangedCombat.WRONG_ARROWS),
            Triple(runeCrossbow, dragonBolts, RangedCombat.WRONG_BOLTS),
            Triple(runeCrossbow, bronzeArrow, RangedCombat.WRONG_BOLTS),
        )) {
            messages.clear()
            wield(alice, weapon, ammo, 10)
            attack(alice, npc)
            tick()
            assertNull(alice.interaction, "${weapon.name} with ${ammo.name}")
            assertEquals(listOf(alice to text), messages, "${weapon.name} with ${ammo.name}")
            assertEquals(10, ammoLeft(alice))
        }
        assertTrue(projectiles.isEmpty())
    }

    @Test
    fun `rapid fires a tick sooner, longrange reaches two further and shares the XP with Defence`() {
        val npc = spawn(type = dummy, x = 3219)
        // Distance 9: out of a shortbow's 7 on rapid, so the player walks two tiles first.
        alice.vars[EquipmentTab.VARP_COM_MODE] = 1
        attack(alice, npc)
        repeat(3) { tick() }
        assertEquals(3212, alice.x)
        val firstShot = projectiles.first().first
        assertEquals(firstShot + 3, alice.nextAttackTick)

        alice.interaction = null
        alice.vars[EquipmentTab.VARP_COM_MODE] = 3
        alice.x = 3210
        alice.waypoints.clear()
        alice.nextAttackTick = 0
        projectiles.clear()
        val rangedBefore = alice.skills.tenths(Skill.RANGED)
        val defenceBefore = alice.skills.tenths(Skill.DEFENCE)
        attack(alice, npc)
        tick()
        assertEquals(3210, alice.x)
        assertEquals(tick, projectiles.single().first)
        assertEquals(tick + 4, alice.nextAttackTick)
        // 2 XP a point each; with a max hit of 1 the shot is worth 0 or 2.
        val ranged = alice.skills.tenths(Skill.RANGED) - rangedBefore
        assertEquals(ranged, alice.skills.tenths(Skill.DEFENCE) - defenceBefore)
        assertTrue(ranged == 0 || ranged == 20, "$ranged")
    }

    @Test
    fun `the NPC's ranged defence is the one for the style's weight`() {
        val npc = spawn()
        val bow = combat.rangedMatchup(alice, npc)!!
        // (1 + 3 + 8) * (8 + 64) against (1 + 9) * (0 + 64), standard; max hit (12 * 71 + 320) / 640.
        assertEquals(864, bow.attackRoll)
        assertEquals(640, bow.defenceRoll)
        assertEquals(1, bow.maxHit)
        wield(alice, runeCrossbow, runiteBolts, 10)
        val crossbow = combat.rangedMatchup(alice, npc)!!
        assertEquals((1 + 9) * (-10 + 64), crossbow.defenceRoll)
        assertEquals((12 * (115 + 64) + 320) / 640, crossbow.maxHit)
        wield(alice, sword)
        assertNull(combat.rangedMatchup(alice, npc))
    }

    @Test
    fun `four shots in five leave their arrow under the NPC for the shooter`() {
        val npc = spawn(type = dummy, x = 3214)
        attack(alice, npc)
        alice.god = true
        repeat(200) { tick() }
        val fired = 100 - ammoLeft(alice)
        val landed = fired - ranged.shotsInFlight
        assertTrue(landed >= 45, "$landed landed")
        assertTrue(dropped.all { (type, _, owner) -> type == bronzeArrow && owner === alice })
        val share = dropped.size.toDouble() / landed
        assertTrue(share in 0.65..0.95, "${dropped.size} of $landed dropped")
    }

    @Test
    fun `the dark bow fires two arrows at once, or one when one is left`() {
        val npc = spawn(type = dummy)
        wield(alice, darkbow, bronzeArrow, 3)
        attack(alice, npc)
        tick()
        assertEquals(1, ammoLeft(alice))
        assertEquals(1104, alice.effects.pendingSpotanims[0]?.id)
        assertEquals(2, projectiles.size)
        assertTrue(projectiles[1].second.startTime > projectiles[0].second.startTime)
        assertEquals(2, ranged.shotsInFlight)
        repeat(9) { tick() }
        assertEquals(0, ammoLeft(alice))
        assertEquals(3, projectiles.size)
        // Out of arrows, she stops; once the last lands, every shot's damage reserved as it was fired is given back.
        repeat(3) { tick() }
        assertEquals(0, ranged.shotsInFlight)
        assertEquals(0, npc.incoming)
        assertEquals(19, alice.effects.pendingSpotanims[0]?.id)
    }

    @Test
    fun `auto retaliate with a bow shoots back, and a sword picked up mid-fight carries on in melee`() {
        val npc = spawn(x = 3214)
        // Auto retaliate is on by default (varp 172 at 0).
        assertTrue(alice.autoRetaliate)
        melee.hitPlayer(alice, npc, 0, tick)
        assertIs<RangedInteraction>(alice.interaction)
        wield(alice, sword)
        tick()
        assertIs<MeleeInteraction>(alice.interaction)
    }

    @Test
    fun `an NPC someone else is shooting can't be shot`() {
        val npc = spawn()
        wield(bob, shortbow, bronzeArrow, 10)
        attack(bob, npc)
        repeat(4) { tick(bob, alice) }
        assertEquals(bob, npc.attacker)
        attack(alice, npc)
        tick(bob, alice)
        assertNull(alice.interaction)
        assertTrue(alice to MeleeCombat.SOMEONE_ELSE in messages, messages.toString())
    }

    @Test
    fun `the twisted bow's roll and max hit grow with the NPC's Magic`() {
        alice.skills.setLevel(Skill.RANGED, 99)
        wield(alice, twistedBow, steelArrow, 100)
        val low = combat.rangedMatchup(alice, spawn(type = dummy))!!
        val high = combat.rangedMatchup(alice, spawn(type = mage, x = 3212))!!
        // On accurate, (99 + 3 + 8) * (70 + 64) and ((99 + 3 + 8) * (20 + 16 + 64) + 320) / 640 = 17, then the bow's percentages.
        val roll = 110 * 134
        assertEquals(roll * CombatMath.twistedBowAccuracy(1) / 100, low.attackRoll)
        assertEquals(17 * CombatMath.twistedBowDamage(1) / 100, low.maxHit)
        assertEquals(roll * CombatMath.twistedBowAccuracy(200) / 100, high.attackRoll)
        assertEquals(17 * CombatMath.twistedBowDamage(200) / 100, high.maxHit)
        assertTrue(high.maxHit > 17 && low.maxHit < 17, "${low.maxHit} and ${high.maxHit}")
    }

    @Test
    fun `the Venator bow's shot bounces to two more NPCs nearby in multi-way combat`() {
        alice.god = true
        wield(alice, venator, steelArrow, 100)
        val first = spawn(type = dummy, x = 3214)
        val second = spawn(type = dummy, x = 3214, z = 3212)
        // Further than two tiles from either: never bounced to.
        val far = spawn(type = dummy, x = 3214, z = 3215)
        multi.set(0, 3214, 3210, true)
        assertTrue(multi.at(0, far.x, far.z) && multi.at(0, alice.x, alice.z), "the same zone")
        attack(alice, first)
        tick()
        assertEquals(2291, projectiles.single().second.id)
        assertEquals(2289, alice.effects.pendingSpotanims[0]?.id)
        // The shot lands on tick 4 (distance 4: 2 ticks, and one on an NPC), then a bounce on each of the next two ticks.
        repeat(4) { tick() }
        val bounces = projectiles.filter { (_, flight) -> flight.sourceIndex > 0 }
        assertEquals(listOf(4L, 5L), bounces.map { it.first }, projectiles.toString())
        assertEquals(first.index + 1 to second.index + 1, bounces[0].second.sourceIndex to bounces[0].second.targetIndex)
        // The only NPC within two tiles of the second is the first again.
        assertEquals(second.index + 1 to first.index + 1, bounces[1].second.sourceIndex to bounces[1].second.targetIndex)
        assertEquals(2291, bounces[0].second.id)
        assertEquals(10_000, far.hitpoints)
        assertTrue(second.attacker === alice)
        // Bounces fire no ammunition: one arrow for the one shot.
        assertEquals(99, ammoLeft(alice))
        alice.interaction = null
        repeat(2) { tick() }
        assertEquals(0, ranged.shotsInFlight)
        assertEquals(0, first.incoming + second.incoming)
    }

    @Test
    fun `a Venator shot in the air bounces with what it was fired with`() {
        alice.god = true
        wield(alice, venator, steelArrow, 100)
        val first = spawn(type = dummy, x = 3214)
        val second = spawn(type = dummy, x = 3214, z = 3212)
        multi.set(0, 3214, 3210, true)
        attack(alice, first)
        tick()
        // A sword now, and no fight: the arrow on its way still bounces.
        wield(alice, sword)
        alice.interaction = null
        repeat(4) { tick() }
        assertEquals(2, projectiles.count { (_, flight) -> flight.sourceIndex > 0 }, projectiles.toString())
    }

    @Test
    fun `a Venator shot doesn't bounce off an NPC that died before it landed`() {
        wield(alice, venator, steelArrow, 100)
        val first = spawn(type = dummy, x = 3214)
        val second = spawn(type = dummy, x = 3214, z = 3212)
        multi.set(0, 3214, 3210, true)
        val left = second.hitpoints
        attack(alice, first)
        tick()
        assertEquals(1, ranged.shotsInFlight)
        // Gone before the arrow gets there: nothing to bounce off.
        melee.hit(first, null, 10_000, tick)
        repeat(4) { tick() }
        assertTrue(projectiles.none { (_, flight) -> flight.sourceIndex > 0 }, projectiles.toString())
        assertEquals(left, second.hitpoints)
    }

    @Test
    fun `in single-way combat the Venator bow's shot doesn't bounce`() {
        alice.god = true
        wield(alice, venator, steelArrow, 100)
        val first = spawn(type = dummy, x = 3214)
        val second = spawn(type = dummy, x = 3214, z = 3212)
        attack(alice, first)
        repeat(8) { tick() }
        assertEquals(2, projectiles.size)
        assertTrue(projectiles.all { (_, flight) -> flight.sourceIndex == -(alice.index + 1) })
        assertEquals(10_000, second.hitpoints)
        assertNull(second.attacker)
    }

    private fun loadBlowpipe(darts: Int, scales: Int) {
        wield(alice, blowpipe, bronzeArrow, 50)
        alice.charges[Blowpipe.DART_TYPE] = dragonDart.id
        alice.charges[Blowpipe.DARTS] = darts
        alice.charges[Blowpipe.SCALES] = scales
    }

    @Test
    fun `the blowpipe fires its darts every 3 ticks at the thrown delay, uses two scales in three, and leaves the quiver alone`() {
        alice.god = true
        loadBlowpipe(darts = 200, scales = 300)
        val npc = spawn(type = dummy, x = 3214)
        attack(alice, npc)
        tick()
        assertEquals(5061, alice.effects.anim?.id)
        assertEquals(1122, projectiles.single().second.id)
        assertEquals(4L, alice.nextAttackTick)
        // Distance 4 thrown: 1 + 4 / 6 = 1 tick, and one on an NPC: tick 3.
        assertEquals(1 + HitDelay.THROWN.ticks(4) + HitDelay.ON_NPC, 3)
        tick()
        assertTrue(npc.effects.pendingHits.isEmpty())
        tick()
        assertEquals(1, npc.effects.pendingHits.size)
        repeat(297) { tick() }
        val shots = projectiles.size
        assertEquals(100, shots)
        assertEquals(200 - shots, alice.charges[Blowpipe.DARTS])
        val scalesUsed = 300 - alice.charges[Blowpipe.SCALES]
        assertTrue(scalesUsed in 50..85, "$scalesUsed scales for $shots shots")
        assertEquals(50, ammoLeft(alice))
        assertTrue(dropped.isEmpty(), dropped.toString())
    }

    @Test
    fun `the blowpipe's strength is its dart's, not the ammo slot's`() {
        alice.skills.setLevel(Skill.RANGED, 99)
        loadBlowpipe(darts = 10, scales = 10)
        val npc = spawn(type = dummy)
        // On accurate: ((99 + 3 + 8) * (20 + 35 + 64) + 320) / 640 = 20; with the bronze arrows' 7 as well it would be 22.
        assertEquals(20, combat.rangedMatchup(alice, npc)!!.maxHit)
        alice.charges[Blowpipe.DARTS] = 0
        assertEquals((110 * 84 + 320) / 640, combat.rangedMatchup(alice, npc)!!.maxHit)
    }

    @Test
    fun `out of scales the worn blowpipe goes empty and won't fire, and with no darts it says so`() {
        alice.god = true
        // Off, so the dummy hitting back doesn't start the fight again.
        alice.vars[EquipmentTab.VARP_AUTO_RETALIATE_OFF] = 1
        loadBlowpipe(darts = 50, scales = 1)
        val npc = spawn(type = dummy, x = 3214)
        attack(alice, npc)
        repeat(30) { tick() }
        assertEquals(blowpipe.id - 2, alice.equipment[WearPos.WEAPON]?.id)
        assertTrue(alice to Blowpipe.RAN_OUT in messages, messages.toString())
        assertTrue(alice to Blowpipe.NO_SCALES in messages, messages.toString())
        assertNull(alice.interaction)

        messages.clear()
        loadBlowpipe(darts = 0, scales = 10)
        attack(alice, npc)
        tick()
        assertEquals(listOf(alice to Blowpipe.NO_DARTS), messages)
        assertNull(alice.interaction)
    }

    companion object {
        private const val SEED = 5L
    }
}
