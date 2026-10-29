package osrsps.game

import net.rsprot.protocol.api.Session
import net.rsprot.protocol.game.outgoing.info.Infos
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import osrsps.game.npc.Npc
import osrsps.game.script.PlayerScripts
import osrsps.game.shop.OpenShop
import osrsps.game.zone.ZoneView
import osrsps.map.Tile

/** A logged-in player. Only touched on the game thread, apart from [droppedSession]. */
class Player(
    val index: Int,
    val username: String,
    var x: Int,
    var z: Int,
    var level: Int,
    varTypes: VarTypes = VarTypes.NONE,
) {
    lateinit var session: Session<Player>

    /**
     * Where the player connects from: the address a relay's PROXY header named, or the connection's own (see
     * osrsps.net.ProxyProtocol). "none" before a connection is set.
     */
    val address: String get() = if (::session.isInitialized) session.inetAddress else "none"
    lateinit var infos: Infos

    /** 1 = fixed, 2 = resizable, from the client's WindowStatus. */
    var windowMode: Int = 0

    /** The top-level interface the client shows. */
    var frame: GameFrame = GameFrame.RESIZABLE_CLASSIC

    /** Varbit resizable_stone_arrangement: which resizable layout the player last chose. */
    var stoneArrangement: Int
        // Read from and written to the varp itself, so ::varp and ::varbit changes are seen here too.
        get() = (vars[GameFrame.STONE_ARRANGEMENT_VARP] ushr GameFrame.STONE_ARRANGEMENT_BIT) and 1
        set(value) {
            val bit = 1 shl GameFrame.STONE_ARRANGEMENT_BIT
            val varp = vars[GameFrame.STONE_ARRANGEMENT_VARP]
            vars[GameFrame.STONE_ARRANGEMENT_VARP] = if (value == 0) varp and bit.inv() else varp or bit
        }

    /** A layout change waiting for its tick. */
    var pendingFrameMove: FrameMove? = null

    /** Open modal interfaces by where they are open. */
    val modals = LinkedHashMap<FrameSlot, Int>()

    /** The dialogue (or other modal) open in the chatbox's chatmodal, -1 for none. See [Interfaces.openChatModal]. */
    var chatModal: Int = -1
        internal set

    /** Counts chatbox modals opened, so a continued dialogue can tell whether its script opened the next one. */
    var chatModalOpens: Int = 0
        internal set

    /** Whether any modal is open, in the game frame or the chatbox. */
    val modalOpen: Boolean get() = modals.isNotEmpty() || chatModal >= 0

    /** Set by the logout button; the player is logged out at the end of the tick. */
    var logoutRequested: Boolean = false

    /** Logged out by the button: removed at the start of the next cycle, with no wait for a reconnect. */
    var loggedOut: Boolean = false

    /** What the save file is named after; see [SaveStore.saveKey]. */
    val saveKey: String = SaveStore.saveKey(username) ?: username

    /** The ISAAC seed the client picked for this connection. A reconnect has to send it back. */
    var loginSeed: IntArray = IntArray(0)

    /**
     * Set from a Netty thread to the session whose connection dropped. Kept as the session rather than a flag so a
     * late drop of a session a reconnect has already replaced is ignored.
     */
    @Volatile
    var droppedSession: Session<Player>? = null

    /** Whether the connection is gone and the player waits in the world for the client to reconnect. */
    val connectionLost: Boolean
        get() {
            val dropped = droppedSession
            return dropped != null && dropped === session
        }

    /** The tick the lost connection was noticed on, or -1 while connected. */
    var connectionLostTick: Long = -1

    /** Turning points of the current route, next one first. */
    val waypoints = ArrayDeque<Tile>()

    /**
     * The tile the player was on before their last step, null since login or a teleport. Someone following them heads
     * here; see [Follows].
     */
    var lastStepFrom: Tile? = null

    /**
     * The last tick the player took a step on, -1 before their first. A ladder walked to waits a tick when the player
     * arrived on this one; see [osrsps.game.content.arriveDelay].
     */
    var lastMoveTick: Long = -1

    /** Where someone following this player heads this tick, noted before anyone moves. Null until the first tick. */
    var followTile: Tile? = null

    /** Where the player stood when this tick's movement began, for followers to measure from. Null until the first tick. */
    var movementStart: Tile? = null
    var movementStartLevel: Int = level

    /** The player this one was last told to face, so a change is sent once. See [World]'s face updates. */
    var sentFacePlayer: Player? = null

    /** What the player is walking to use, a loc, a ground obj or an NPC, if anything. Walking elsewhere or teleporting drops it. */
    var interaction: Interaction? = null

    /** The NPC the client was told this player turns to, or null. */
    var facingNpc: Npc? = null

    val vars = PlayerVars(varTypes)

    /** The run orb, which is varp 173 (option_run), so `::varp 173 1` turns it on too. */
    var runEnabled: Boolean
        get() = vars[World.VARP_RUN] != 0
        set(value) {
            vars[World.VARP_RUN] = if (value) 1 else 0
        }

    val skills = PlayerSkills()

    /** The 28-slot inventory, inv 93. Changes are sent at the end of the cycle. */
    val inventory = Inventory(Inventory.INV, Inventory.INV_CAPACITY)

    /** What the player wears, inv 94, one slot per [WearPos]. Changes are sent at the end of the cycle. */
    val equipment = Inventory(Inventory.WORN, WearPos.COUNT)

    /**
     * The bank, inv 95. Sent only while the bank is open: changes made with it shut wait for the next opening, which
     * sends it all again. See [osrsps.game.bank.Bank].
     */
    val bank = Inventory(Inventory.BANK, Inventory.BANK_CAPACITY)

    /** The shop the player has open, whose stock they are sent; see [osrsps.game.shop.ShopScreen]. Not saved. */
    var openShop: OpenShop? = null

    /** What the player's charged items hold, such as a blowpipe's darts and scales; see [Charges]. */
    val charges = Charges()

    /** The `::scenario` the player last started, null when they aren't in one. Saved. */
    var scenario: String? = null

    /** What `::reset` puts back: the player before their first `::scenario` since the last reset. Saved. */
    var beforeScenario: PlayerSave? = null

    /** When the account was made, in milliseconds since 1970; Hans counts its age from this. Saved. */
    var createdAt: Long = System.currentTimeMillis()

    /** Game ticks spent logged in, which Hans reports as time played. Paused cycles don't count. Saved. */
    var playedTicks: Long = 0

    /**
     * Dialogue and examine texts show their keys, not the dialogue file's words (`::keys`), so a test script asserts
     * the same thing whether or not the file has words yet. Not saved.
     */
    var showDialogueKeys: Boolean = false

    /** Queues, timers and the current delay; run by [World.scripts]. */
    val scripts = PlayerScripts()

    /** Current hitpoints, which are the Hitpoints skill's current level. */
    var hitpoints: Int
        get() = skills.currentLevel(Skill.HITPOINTS)
        set(value) = skills.setCurrentLevel(Skill.HITPOINTS, value)

    /** Ctrl-click flips walk and run for the route it starts. */
    var invertRun: Boolean = false

    /** The first tick they may swing again; see [osrsps.game.combat.MeleeCombat]. */
    var nextAttackTick: Long = 0

    /** The NPC that hit them last, and the tick it did; see [osrsps.game.combat.MeleeCombat]. Not saved. */
    var attackedBy: Npc? = null
    var attackedTick: Long = -1

    /** `::buttons`: every interface click the server gets from them is echoed to their chat. Not saved. */
    var echoButtons: Boolean = false

    /** `::god`: NPC hits do them no damage. Not saved. */
    var god: Boolean = false

    /** `::sure`: their attacks on NPCs always pass the accuracy roll, which is still drawn. Not saved. */
    var sureHits: Boolean = false

    /** The tick they died on, -1 while alive: they lie dead, taking no input, until [osrsps.game.combat.Deaths] respawns them. Not saved. */
    var deathTick: Long = -1
    val dead: Boolean get() = deathTick >= 0

    /** Auto retaliate from the combat tab: on unless varp 172 (option_nodef) is 1. */
    val autoRetaliate: Boolean get() = vars[EquipmentTab.VARP_AUTO_RETALIATE_OFF] == 0

    /** 0 to 10,000; 100 units show as 1% on the orb. */
    var runEnergy: Int = Movement.MAX_ENERGY

    /** What the client was last told, so changes are sent once. Varps are tracked by [vars]. */
    var sentRunEnabled: Boolean? = null
    var sentRunEnergy: Int = -1
    var sentCombatLevel: Int = -1

    /** The stance last put in the player's appearance; null until the first is sent. */
    var sentStance: Stance? = null

    /**
     * Whether the combat tab shows the autocast menu in its place, picked from the defensive (true) or normal (false)
     * spell button; null while it shows the combat tab. See [EquipmentTab.clickCombatTab].
     */
    var autocastMenu: Boolean? = null

    /** Moved by a teleport this tick; the client is told when the tick's movement is sent. */
    var teleported: Boolean = false

    /** Clicks and buttons that arrived while the clock was paused, run at the start of the next tick. */
    val heldActions = ArrayDeque<() -> Unit>()

    /** The zone the client's build area was last centred on. */
    var buildAreaZoneX: Int = x shr 3
    var buildAreaZoneZ: Int = z shr 3

    /** The zones this client gets updates for. */
    val zoneView = ZoneView()

    /** The animation, spotanims, sounds and jingle waiting to be sent at the end of the cycle. */
    val effects = PlayerEffects()

    /** Plays animation (seq) [id] on the player for everyone who can see them; -1 stops the one playing. */
    fun anim(id: Int, delay: Int = 0) = effects.anim(id, delay)

    /** Plays spotanim [id] on the player for everyone who can see them; -1 clears [slot]. [height] is above the ground. */
    fun spotanim(id: Int, height: Int = 0, delay: Int = 0, slot: Int = 0) = effects.spotanim(slot, id, height, delay)

    /** Plays sound effect (synth) [id] to this player alone. Sounds others hear go through [World.zones]. */
    fun sound(id: Int, loops: Int = 1, delay: Int = 0) = effects.sound(id, loops, delay)

    /** Plays jingle [id] to this player alone; the client pauses its music for it. */
    fun jingle(id: Int) = effects.jingle(id)

    /** Shows [text] in the chatbox as a game message. */
    fun message(text: String) {
        session.queue(MessageGame(CHATTYPE_GAMEMESSAGE, text))
    }

    private companion object {
        const val CHATTYPE_GAMEMESSAGE = 0
    }
}
