package osrsps.testrunner

import osrsps.cache.MapElementCodec
import osrsps.game.EquipmentTab
import osrsps.game.Experience
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.ObjTypes
import osrsps.game.NpcTypes
import osrsps.game.Skill
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.content.CustomLocs
import osrsps.game.bank.DepositBox
import osrsps.game.ge.GrandExchangeScreen
import osrsps.game.ge.PriceChecker
import osrsps.game.shop.ShopScreen
import osrsps.game.trade.Trades
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.script.ScriptContext
import osrsps.game.skill.LevelUps
import osrsps.game.zone.ZoneLoc
import osrsps.testclient.ClientScript
import osrsps.testclient.Coord
import osrsps.testclient.CopiedZone
import osrsps.testclient.DialogueView
import osrsps.testclient.HeadlessClient
import kotlin.math.abs
import kotlin.math.max

/**
 * The runner's [Game] on a [HeadlessClient] instead of RuneLite, so the runner's JSON scripts play against our server
 * with no client and no VM. What RuneLite reads from its cache (names, options, the map's locs, varbit layouts) comes
 * from [types], the same 242 cache the server loaded; what it is told by the server comes from the headless client.
 *
 * Every click sends the packet the menu entry ClientGame names would make the client send. What the real client works
 * out by running its own client scripts (a dropdown opened by the mouse, a search box's typing, text an interface has
 * in the cache) is not here: those methods say so, and the script fails with that reason rather than passing wrongly.
 */
internal class HeadlessGame(
    /** The client logged in now, or null between a logout and the next login. */
    private val client: () -> HeadlessClient?,
    private val account: String,
    private val types: Types,
) : Game {
    /** What the client's cache knows: names, options, varbit layouts and the map's own locs. */
    class Types(
        val npcs: NpcTypes,
        val objs: ObjTypes,
        val locs: LocTypes,
        val vars: VarTypes,
        /** The loc the cache's maps place on a tile and layer, before the server changes any. */
        val staticLoc: (x: Int, z: Int, level: Int, layer: Int) -> ZoneLoc?,
        /** The map elements (minimap icons) our cache overlay adds, by id: the cache's own aren't decoded here. */
        val mapElements: Map<Int, MapElementCodec.Definition> = emptyMap(),
    ) {
        companion object {
            /** What [world] loaded from the cache, and the overlay's map elements. */
            fun of(world: World) =
                Types(world.npcTypes, world.objTypes, world.locs.types, world.varTypes, world.locs::cached, CustomLocs(world.customVars).mapElements)
        }
    }

    /** Components that list an inventory's items, so a click on a slot sends the item it shows, by packed id. */
    private val inventoryComponents = HashMap(STATIC_INVENTORY_COMPONENTS)

    /** A component listing [inv]'s items, slot 0 at sub [firstSub] (the shop's sub 0 is its highlight). */
    data class InvBinding(val inv: Int, val firstSub: Int = 0)
    private var scriptsSeen = 0

    /** The first client script [promptMode] reads: the one after the last prompt answered from here. */
    private var promptsFrom = 0

    private fun c(): HeadlessClient? = client()?.takeUnless { it.isClosed }

    override fun loggedIn(): Boolean = c()?.position != null

    override fun localName(): String? = if (loggedIn()) account else null

    override fun location(): Tile? = c()?.position?.let { Tile(it.x, it.z, it.level) }

    /** The headless client has no drawing: it shows the player where the server has them. */
    override fun drawnLocation(): Tile? = location()

    override fun sendChat(text: String): String? {
        val client = c() ?: return "not logged in"
        if (text.startsWith("::")) client.command(text.substring(2)) else client.say(text)
        return null
    }

    /** Lines go straight out as a packet, so none is ever left in a chat input. */
    override fun chatPending(): Boolean = false

    override fun pressEnterAgain(): String = "the headless client has no chat input"

    override fun closePrompt() {
        c()?.closeModal()
    }

    override fun walk(x: Int, y: Int): String? {
        val client = c() ?: return "not logged in"
        val here = client.position ?: return "no local player"
        if (!inScene(client, x, y)) return "tile $x, $y is not in the loaded scene"
        client.walk(x, y)
        return null
    }

    /** There is no walk flag to read; the step then only waits for the player to arrive. */
    override fun walkDestination(): Tile? = null

    override fun playerOption(name: String, option: String, at: Tile?): String =
        "playerOption needs other players' names, which the headless client doesn't read yet"

    override fun npcOption(name: String?, id: Int, option: String): String? {
        val client = c() ?: return "not logged in"
        val here = client.position ?: return "no local player"
        val examine = option.equals(EXAMINE, ignoreCase = true)
        var best: Pair<Int, NpcType>? = null
        var bestDistance = Int.MAX_VALUE
        var nameSeen = false
        for (npc in client.npcs()) {
            val type = npcType(client, npc.id) ?: continue
            if (!npcMatches(type, name, id) || npc.coord.level != here.level) continue
            nameSeen = true
            val d = distance(npc.coord, here)
            if (d < bestDistance && (examine || opSlot(type.ops, option) >= 0)) {
                best = npc.index to type
                bestDistance = d
            }
        }
        val who = if (name != null) "\"$name\"" else "id $id"
        val (index, type) = best ?: return if (nameSeen) "NPC $who has no option \"$option\"" else "no NPC $who in view"
        // Examine names the NPC's type as the client shows it; an op names the NPC by its index.
        if (examine) client.examineNpc(type.id) else client.npcOp(index, opSlot(type.ops, option) + 1)
        return null
    }

    override fun locOption(name: String?, id: Int, option: String, at: Tile?): String? {
        val client = c() ?: return "not logged in"
        val here = client.position ?: return "no local player"
        if (at != null && !inScene(client, at.x, at.y)) return "tile ${at.x}, ${at.y} is not in the loaded scene"
        val examine = option.equals(EXAMINE, ignoreCase = true)
        var best: SceneLoc? = null
        var bestOp = -1
        var bestDistance = Int.MAX_VALUE
        var nameSeen = false
        for (loc in sceneLocs(client, here)) {
            if (!locMatches(loc, name, id) || (at != null && !loc.covers(at.x, at.y))) continue
            nameSeen = true
            // A hidden multiloc (no current form) has no menu, Examine included.
            val shown = loc.shown
            val op = if (examine) (if (shown != null) 0 else -1) else shown?.let { opSlot(it.ops, option) } ?: -1
            val d = distance(Coord(loc.loc.x, loc.loc.z, loc.loc.level), here)
            if (op >= 0 && d < bestDistance) {
                best = loc
                bestOp = op
                bestDistance = d
            }
        }
        val what = (if (name != null) "\"$name\"" else "id $id") + (if (at == null) "" else " at ${at.x}, ${at.y}")
        val loc = best ?: return if (nameSeen) "loc $what has no option \"$option\"" else "no loc $what in the scene"
        // Examine is of what the loc is now; an op sends the base id and the server works out what it is.
        if (examine) client.examineLoc(loc.shown!!.id, longId = loc.shown!!.id > 0xFFFF) else client.locOp(loc.loc.id, loc.loc.x, loc.loc.z, bestOp + 1, longId = loc.loc.id > 0xFFFF)
        return null
    }

    override fun locAt(name: String?, id: Int, option: String?, at: Tile): String? {
        val client = c() ?: return "not logged in"
        val here = client.position ?: return "no local player"
        if (!inScene(client, at.x, at.y)) return "tile ${at.x}, ${at.y} is not in the loaded scene"
        val what = (if (name != null) "\"$name\"" else "id $id") + " at ${at.x}, ${at.y}"
        var seen = false
        val locs = sceneLocs(client, here)
        for (loc in locs) {
            if (!loc.covers(at.x, at.y) || !locMatches(loc, name, id)) continue
            // A hidden loc (no current form) matches by its id but isn't there to see.
            val anyForm = option == null || option.equals(EXAMINE, ignoreCase = true)
            if (if (anyForm) loc.shown != null else loc.shown?.let { opSlot(it.ops, option!!) >= 0 } == true) return null
            seen = true
        }
        if (!seen) {
            val there = locs.filter { it.covers(at.x, at.y) }.map { "${it.shown?.name ?: "hidden"} (${it.loc.id})" }
            return "no loc $what" + if (there.isEmpty()) "" else "; there: ${there.joinToString()}"
        }
        return if (option == null) "loc $what is hidden" else "loc $what has no option \"$option\""
    }

    /**
     * The headless client decodes no sprites, so the check is of the cache the server serves: the loc's current form
     * names the element (opcode 82, in the loc types the server decoded, the overlay's included) and, for an element of
     * ours, the overlay defines it with its sprite. A cache element's sprite is taken as there.
     */
    override fun mapIconAt(name: String?, id: Int, icon: Int, at: Tile): String? {
        val client = c() ?: return "not logged in"
        val here = client.position ?: return "no local player"
        if (!inScene(client, at.x, at.y)) return "tile ${at.x}, ${at.y} is not in the loaded scene"
        val what = (if (name != null) "\"$name\"" else "id $id") + " at ${at.x}, ${at.y}"
        var wrong: String? = null
        for (loc in sceneLocs(client, here)) {
            if (!loc.covers(at.x, at.y) || !locMatches(loc, name, id)) continue
            val shown = loc.shown
            if (shown == null) {
                wrong = "loc $what is hidden"
                continue
            }
            if (shown.mapElement != icon) {
                wrong = "loc $what " + (if (shown.mapElement < 0) "has no map icon" else "has map icon ${shown.mapElement}") + ", not $icon"
                continue
            }
            if (icon >= CustomLocs.FIRST_ID && icon !in types.mapElements) return "the overlay has no map element $icon"
            return null
        }
        return wrong ?: "no loc $what"
    }

    override fun takeItem(name: String?, id: Int, at: Tile?): String? = groundItemOption(name, id, TAKE_OP, TAKE, at)

    override fun groundItemOption(name: String?, id: Int, op: Int, option: String, at: Tile?): String? {
        val client = c() ?: return "not logged in"
        val obj = nearestGroundItem(client, name, id, at) ?: return groundItemMissing(name, id, at)
        client.objOp(obj.id, obj.coord.x, obj.coord.z, op)
        return null
    }

    override fun groundItem(name: String?, id: Int, at: Tile?): String? {
        val client = c() ?: return "not logged in"
        return if (nearestGroundItem(client, name, id, at) == null) groundItemMissing(name, id, at) else null
    }

    override fun useItemOnItem(name: String?, id: Int, onName: String?, onId: Int): String? {
        val client = c() ?: return "not logged in"
        val items = client.inventory(Inventory.INV)
        val slot = inventorySlot(items, name, id, -1)
        val onSlot = inventorySlot(items, onName, onId, slot)
        if (slot < 0 || onSlot < 0) {
            val what = if (slot < 0) describeItem(name, id) else describeItem(onName, onId)
            return "no $what in the inventory"
        }
        client.ifButtonT(
            InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, items[slot]!!.id,
            InventoryTab.INTERFACE, InventoryTab.ITEMS, onSlot, items[onSlot]!!.id,
        )
        return null
    }

    override fun useItemOnLoc(name: String?, id: Int, locName: String?, locId: Int, at: Tile?): String? {
        val client = c() ?: return "not logged in"
        val here = client.position ?: return "no local player"
        val items = client.inventory(Inventory.INV)
        val slot = inventorySlot(items, name, id, -1)
        if (slot < 0) return "no ${describeItem(name, id)} in the inventory"
        if (at != null && !inScene(client, at.x, at.y)) return "tile ${at.x}, ${at.y} is not in the loaded scene"
        // As locOption finds a loc, without an option to match: the nearest one.
        val loc = sceneLocs(client, here)
            .filter { locMatches(it, locName, locId) && (at == null || it.covers(at.x, at.y)) }
            .minByOrNull { distance(Coord(it.loc.x, it.loc.z, it.loc.level), here) }
            ?: return "no loc " + (if (locName != null) "\"$locName\"" else "id $locId") + (if (at == null) "" else " at ${at.x}, ${at.y}") + " in the scene"
        client.locTarget(loc.loc.id, loc.loc.x, loc.loc.z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, items[slot]!!.id, longId = loc.loc.id > 0xFFFF)
        return null
    }

    override fun inventoryCount(name: String?, id: Int): Int {
        val client = c() ?: return -1
        return client.inventory(Inventory.INV).filterNotNull().filter { it.count > 0 && itemMatches(it.id, name, id) }.sumOf { it.count }
    }

    override fun dialogue(): Dialogue? {
        val client = c() ?: return null
        return when (val view = client.dialogueView) {
            is DialogueView.Chat -> Dialogue(
                if (view.interfaceId == Chatbox.CHAT_LEFT) Dialogue.Kind.NPC else Dialogue.Kind.PLAYER,
                Dialogue.clean(view.name), Dialogue.clean(view.text), null,
            )
            is DialogueView.Menu -> {
                // As the client lists them: the title is child 0, the options children 1 up, the slot a click sends back.
                val options = view.options.mapIndexed { i, text -> Dialogue.Option(i + 1, Dialogue.clean(text)) }
                val all = (listOf(Dialogue.clean(view.title)) + options.map { it.text }).filter { it.isNotEmpty() }.joinToString(" / ")
                Dialogue(Dialogue.Kind.OPTIONS, Dialogue.clean(view.title), all, options)
            }
            is DialogueView.Message -> Dialogue(Dialogue.Kind.MESSAGE, "", Dialogue.clean(view.text), null)
            is DialogueView.Other -> when (view.interfaceId) {
                Chatbox.OBJECTBOX -> Dialogue(Dialogue.Kind.OBJECT, "", Dialogue.clean(client.text(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_TEXT)), null)
                Chatbox.OBJECTBOX_DOUBLE -> Dialogue(Dialogue.Kind.OBJECT, "", Dialogue.clean(client.text(Chatbox.OBJECTBOX_DOUBLE, Chatbox.OBJECTBOX_DOUBLE_TEXT)), null)
                else -> null
            }
            null -> null
        }
    }

    override fun levelUp(): Dialogue? {
        val client = c() ?: return null
        if (client.subAt(Chatbox.CHATBOX, Chatbox.CHATMODAL) != LEVEL_UP) return null
        return Dialogue(Dialogue.Kind.LEVEL_UP, Dialogue.clean(client.text(LEVEL_UP, LEVEL_UP_TITLE)), Dialogue.clean(client.text(LEVEL_UP, LEVEL_UP_TEXT)), null)
    }

    override fun continueDialogue(): String? {
        val client = c() ?: return "not logged in"
        when (val view = client.dialogueView) {
            is DialogueView.Chat, is DialogueView.Message -> client.continueDialogue()
            // The objectbox's continue is slot 0 of its universe component; the double one has a pausebutton of its own.
            is DialogueView.Other -> when (view.interfaceId) {
                Chatbox.OBJECTBOX -> client.resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
                Chatbox.OBJECTBOX_DOUBLE -> client.resumePause(Chatbox.OBJECTBOX_DOUBLE, Chatbox.OBJECTBOX_DOUBLE_PAUSEBUTTON)
                LEVEL_UP -> client.resumePause(LEVEL_UP, LEVEL_UP_CONTINUE)
                else -> return "no dialogue with a continue button is open"
            }
            else -> return "no dialogue with a continue button is open"
        }
        return null
    }

    override fun chooseOption(slot: Int): String? {
        val client = c() ?: return "not logged in"
        if (client.dialogueView !is DialogueView.Menu) return "no option menu is open"
        client.resumePause(Chatbox.CHATMENU, Chatbox.CHATMENU_OPTIONS, slot)
        return null
    }

    override fun npcIndex(name: String?, id: Int): Int {
        val client = c() ?: return -1
        val here = client.position ?: return -1
        return client.npcs()
            .filter { it.coord.level == here.level && npcType(client, it.id)?.let { type -> npcMatches(type, name, id) } == true }
            .minByOrNull { distance(it.coord, here) }?.index ?: -1
    }

    /** Health bars aren't read yet. */
    override fun npcHealth(name: String?, id: Int): Game.Health? = if (npcIndex(name, id) < 0) null else Game.Health(-1, 0)

    /** The server's copy only: the headless client runs no client scripts that could change its own. */
    override fun varp(id: Int): IntArray? {
        val client = c() ?: return null
        if (!loggedIn() || id < 0 || id >= types.vars.varpCount) return null
        val value = client.varp(id)
        return intArrayOf(value, value)
    }

    override fun varbit(id: Int): IntArray? {
        val type = types.vars.varbit(id) ?: return null
        val values = varp(type.baseVar) ?: return null
        return IntArray(2) { (values[it] ushr type.startBit) and type.mask }
    }

    override fun playerOptions(): Array<String?> {
        val ops = c()?.playerOps().orEmpty()
        return Array(PLAYER_OPTION_SLOTS) { slot -> ops[slot + 1]?.let { Dialogue.clean(it) } }
    }

    override fun weight(): Int = c()?.weightKg ?: Int.MIN_VALUE

    /** The prompt's mode is set by the client's own scripts, which don't run here. */
    /**
     * Stand-in for varc meslayermode, which the prompts' own client scripts set: the mode of the last prompt script the
     * server ran (meslayer_mode7, mode9 or mode14), until a meslayer_close for it or any mode, or an answer sent from
     * here (the client closes its prompt on Enter).
     */
    override fun promptMode(): Int {
        val scripts = c()?.clientScripts() ?: return 0
        var mode = 0
        for (i in promptsFrom.coerceAtMost(scripts.size) until scripts.size) {
            val script = scripts[i]
            when (script.id) {
                ScriptContext.MESLAYER_CLOSE -> {
                    val closes = script.args.firstOrNull() as? Int ?: 0
                    if (closes == 0 || closes == mode) mode = 0
                }
                else -> PROMPT_SCRIPTS[script.id]?.let { mode = it }
            }
        }
        return mode
    }

    override fun enterAmount(amount: Int): String? {
        val client = c() ?: return "not logged in"
        client.resumeCount(amount)
        promptsFrom = client.clientScripts().size
        return null
    }

    override fun typeText(text: String): String = "typing into an interface runs the client's own scripts, which the headless client doesn't have"

    override fun typingPending(): Boolean = false

    override fun skillLevel(skill: String): Int {
        val stat = statOf(skill) ?: return -1
        return Experience.levelForTenths(stat.xp * 10)
    }

    override fun skillXp(skill: String): Int = statOf(skill)?.xp ?: -1

    private fun statOf(name: String): osrsps.testclient.Stat? {
        val skill = Skill.entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: return null
        return c()?.stat(skill.ordinal)
    }

    override fun tileLoaded(at: Tile): Boolean = c()?.let { inScene(it, at.x, at.y) } == true

    override fun interfaceOpen(interfaceId: Int): Boolean = c()?.openInterfaces()?.contains(interfaceId) == true

    override fun itemOption(name: String?, id: Int, option: String): String? {
        val client = c() ?: return "not logged in"
        val items = client.inventory(Inventory.INV)
        val slot = inventorySlot(items, name, id, -1)
        val what = if (name != null) "\"$name\"" else "item $id"
        if (slot < 0) return "no $what in the inventory"
        val itemId = items[slot]!!.id
        val op = if (option.equals(EXAMINE, ignoreCase = true)) {
            InventoryTab.EXAMINE_OP
        } else {
            // The item's iops as the client builds the inventory's menu from them, and our server reads them.
            val iop = types.objs[itemId]?.iops?.indexOfFirst { it != null && Dialogue.clean(it).equals(option, ignoreCase = true) } ?: -1
            InventoryTab.IOP_BY_BUTTON_OP.entries.firstOrNull { it.value == iop + 1 }?.key ?: -1
        }
        if (op < 0) return "$what has no option \"$option\""
        client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, op, slot, itemId)
        return null
    }

    override fun wornId(wearpos: Int): Int {
        val worn = c()?.inventory(Inventory.WORN) ?: return -1
        val item = worn.getOrNull(wearpos)
        return if (item == null || item.count <= 0) -1 else item.id
    }

    /**
     * The player's appearance isn't read yet, so this is an id no item has: an equipment check then fails, naming why,
     * rather than passing as [Game.NOT_ON_MODEL] would for a slot the model doesn't show.
     */
    override fun modelWornId(wearpos: Int): Int = MODEL_NOT_READ

    override fun itemName(id: Int): String? = when {
        id == MODEL_NOT_READ -> "an appearance the headless client doesn't read yet"
        id < 0 -> null
        else -> types.objs[id]?.let { Dialogue.clean(it.name) }
    }

    override fun wornOption(wearpos: Int, option: String): String? {
        val client = c() ?: return "not logged in"
        val component = wornSlotComponent(wearpos) ?: return "the equipment tab shows no slot $wearpos"
        val itemId = wornId(wearpos)
        if (itemId < 0) return "nothing worn in slot $wearpos"
        // Op 1 is Remove and 10 Examine; the item's own worn options are ops 2 to 9, its params 451 to 458.
        val op = when {
            option.equals(REMOVE, ignoreCase = true) -> 1
            option.equals(EXAMINE, ignoreCase = true) -> InventoryTab.EXAMINE_OP
            else -> (0 until 8).firstOrNull { types.objs[itemId]?.stringParam(WORN_OP_FIRST_PARAM + it)?.let(Dialogue::clean).equals(option, ignoreCase = true) }?.plus(2) ?: -1
        }
        if (op < 0) return "the item in slot $wearpos ($itemId) has no option \"$option\""
        client.ifButton(EquipmentTab.WORNITEMS, component, op, -1, itemId)
        return null
    }

    override fun clickWidget(interfaceId: Int, component: Int, sub: Int, op: Int): String? {
        val client = c() ?: return "not logged in"
        if (!interfaceOpen(interfaceId)) return "interface $interfaceId:$component is not open (open: ${client.openInterfaces().sorted()})"
        if (interfaceId == Chatbox.SKILLMULTI && component - Chatbox.SKILLMULTI_FIRST_ITEM in 0 until Chatbox.SKILLMULTI_MAX_ITEMS) {
            // Stand-in for the Make-X box's own click script: an item button resumes with the quantity the box shows,
            // which is the one its setup script started on (its last argument), as no quantity button is clicked here.
            val setup = client.clientScripts().lastOrNull { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }
            val quantity = setup?.args?.lastOrNull() as? Int ?: return "the Make-X box's setup script hasn't come"
            client.resumePause(interfaceId, component, quantity)
            return null
        }
        if (sub == FRAME_CLOSE_CHILD && (interfaceId shl 16 or component) in FRAME_CLOSE_BUTTONS) {
            // Inferred: steelborder's close button closes the screen in the client (if_close), which tells the server
            // with CLOSE_MODAL; the button's own op isn't the server's to handle.
            client.closeModal()
            return null
        }
        if (interfaceId == BankScreen.POPUPOVERLAY && component == POPUP_CONFIRM &&
            client.clientScripts().lastOrNull()?.id == BankScreen.CONFIRM_OVERLAY_SCRIPT
        ) {
            // Stand-in for the confirm overlay's own click script: its buttons answer resume_p_countdialog, 1 to confirm.
            client.resumeCount(1)
            return null
        }
        client.ifButton(interfaceId, component, op, sub, objAt(client, interfaceId, component, sub))
        return null
    }

    override fun clickWidgetNamed(interfaceId: Int, component: Int, name: String, op: Int): String =
        "slots built by the client's own scripts aren't known to the headless client"

    /**
     * Only the scripts whose whole effect stays in the client are stood in for, as doing nothing: the side tab switch
     * (915, toplevel_sidebutton_switch) shows another tab and tells the server nothing (inferred from its name and
     * from the server never seeing it). Any other script fails, saying it needs the client.
     */
    override fun runClientScript(id: Int, args: Array<Any>): String? =
        if (id in CLIENT_ONLY_SCRIPTS) null else "client script $id runs in the client, which the headless client doesn't have"

    override fun mouseClickWidget(interfaceId: Int, component: Int, sub: Int): String =
        "a mouse click on $interfaceId:$component runs the client's own scripts, which the headless client doesn't have"

    override fun castOnNpc(interfaceId: Int, component: Int, npc: String?, npcId: Int): String? {
        val client = c() ?: return "not logged in"
        if (!interfaceOpen(interfaceId)) return "interface $interfaceId:$component is not open"
        val index = npcIndex(npc, npcId)
        if (index < 0) return "no NPC " + (if (npc != null) "\"$npc\"" else "id $npcId") + " in view"
        client.npcTarget(index, interfaceId, component)
        return null
    }

    /**
     * The text the server set on the component (IF_SETTEXT), or on every component of the interface for -1. Text an
     * interface has in the cache, or that the client's scripts build, isn't known here.
     */
    override fun widgetText(interfaceId: Int, component: Int): String? {
        val client = c() ?: return null
        if (!interfaceOpen(interfaceId)) return null
        val texts = if (component >= 0) listOfNotNull(client.text(interfaceId, component)) else client.texts(interfaceId).values.toList()
        return texts.map(Dialogue::clean).filter { it.isNotEmpty() }.joinToString(" / ")
    }

    /** The player's appearance isn't read yet. */
    override fun idlePose(): Int = -1

    override fun logout(): String? {
        val client = c() ?: return "not logged in"
        if (!interfaceOpen(World.LOGOUT_TAB)) return "the logout tab is not open"
        client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
        return null
    }

    override fun friends(): Map<String, Int>? = c()?.takeIf { it.friendsLoaded }?.friends()

    override fun ignores(): List<String>? = c()?.ignores()

    override fun friendsChatOwner(): String? = c()?.friendsChat()?.owner

    override fun friendsChatMembers(): List<String> = c()?.friendsChat()?.members?.keys?.toList().orEmpty()

    override fun friendsChatRank(name: String): Int? =
        c()?.friendsChat()?.members?.entries?.firstOrNull { Runner.normalise(it.key) == Runner.normalise(name) }?.value

    override fun enterText(text: String): String? {
        val client = c() ?: return "not logged in"
        client.resumeString(text)
        promptsFrom = client.clientScripts().size
        return null
    }

    /**
     * Reads the client scripts the server ran since the last call for the ones that tie a component to an inventory,
     * as the real client's interfaces do, so a click on one of its slots names the item there.
     */
    fun noteInventoryBindings() {
        val client = c() ?: return
        val scripts = client.clientScripts()
        if (scripts.size < scriptsSeen) scriptsSeen = 0
        for (script in scripts.subList(scriptsSeen, scripts.size)) {
            when (script.id) {
                INTERFACE_INV_INIT -> {
                    val component = script.args.getOrNull(0) as? Int ?: continue
                    val inv = script.args.getOrNull(1) as? Int ?: continue
                    inventoryComponents[component] = InvBinding(inv)
                }
                SHOP_MAIN_INIT -> (script.args.getOrNull(0) as? Int)?.let { inventoryComponents[SHOP_MAIN_ITEMS] = InvBinding(it, firstSub = 1) }
            }
        }
        scriptsSeen = scripts.size
    }

    /**
     * Stand-in for a chat line a client script prints: the item examine script (6003) puts the item's description in
     * the chatbox. Inferred: only the description is given, not the price lines the script adds after it.
     */
    fun scriptMessage(script: ClientScript): String? =
        if (script.id == InventoryTab.EXAMINE_SCRIPT) script.args.getOrNull(EXAMINE_DESC_ARG) as? String else null

    /** A new login starts from a new client, whose scripts are counted from 0. */
    fun newLogin() {
        scriptsSeen = 0
        promptsFrom = 0
    }

    private fun objAt(client: HeadlessClient, interfaceId: Int, component: Int, sub: Int): Int {
        noteInventoryBindings()
        val packed = (interfaceId shl 16) or component
        if (interfaceId == EquipmentTab.WORNITEMS) {
            val i = EquipmentTab.WORNITEMS_SLOTS.indexOf(component)
            if (i >= 0) return wornId(EquipmentTab.SLOT_WEARPOS[i])
        }
        val binding = inventoryComponents[packed] ?: return -1
        if (sub < binding.firstSub) return -1
        // A sold-out shop slot keeps its item at 0, and stays clickable; an empty slot is null.
        return client.inventory(binding.inv).getOrNull(sub - binding.firstSub)?.id ?: -1
    }

    // --- Finding things -------------------------------------------------------------------------------------------

    /** A loc in the scene, as the client has it: the cache's, or what the server put there since. */
    private inner class SceneLoc(val loc: ZoneLoc, val client: HeadlessClient) {
        val type: LocType? get() = types.locs[loc.id]

        /** What the loc shows now, a multiloc's current form; null when it has none (hidden). */
        val shown: LocType? by lazy { type?.let { visibleLoc(client, it) } }

        /** Whether the loc stands on the tile; a big ground loc covers every tile of its footprint. */
        fun covers(x: Int, z: Int): Boolean {
            if (loc.layer != GROUND_LOC_LAYER) return loc.x == x && loc.z == z
            val t = type ?: return loc.x == x && loc.z == z
            val turned = loc.angle and 1 == 1
            val width = if (turned) t.length else t.width
            val length = if (turned) t.width else t.length
            return x in loc.x until loc.x + width && z in loc.z until loc.z + length
        }
    }

    private fun sceneLocs(client: HeadlessClient, here: Coord): List<SceneLoc> {
        // The server's changes since the client loaded the area, by tile and layer; id -1 is a cache loc removed.
        val changed = HashMap<Long, ZoneLoc?>()
        for (change in client.locChanges()) {
            if (change.coord.level != here.level) continue
            changed[slot(change.coord.x, change.coord.z, change.layer)] =
                if (change.id < 0) null else ZoneLoc(change.id, change.shape, change.angle, change.coord.x, change.coord.z, change.coord.level)
        }
        val out = ArrayList<SceneLoc>()
        val origin = client.sceneOrigin() ?: return out
        val copied = client.regionZones()
        for (x in origin.x until origin.x + HeadlessClient.SCENE_SIZE) {
            for (z in origin.z until origin.z + HeadlessClient.SCENE_SIZE) {
                for (layer in 0 until LAYERS) {
                    val key = slot(x, z, layer)
                    val loc = if (changed.containsKey(key)) changed[key] else mapLoc(copied, x, z, here.level, layer)
                    if (loc != null) out += SceneLoc(loc, client)
                }
            }
        }
        return out
    }

    /**
     * The loc the client's map data puts on a tile. In a region rebuild (an instance) the client draws each zone from
     * the one the rebuild names, so the loc is read from that zone of the cache's maps and moved here; a zone the
     * rebuild left out is empty. Only unrotated copies are read, which is all the server sends.
     */
    private fun mapLoc(copied: Map<Triple<Int, Int, Int>, CopiedZone>?, x: Int, z: Int, level: Int, layer: Int): ZoneLoc? {
        if (copied == null) return types.staticLoc(x, z, level, layer)
        val zone = copied[Triple(x shr 3, z shr 3, level)] ?: return null
        if (zone.rotation != 0) return null
        val fromX = (zone.zoneX shl 3) + (x and 7)
        val fromZ = (zone.zoneZ shl 3) + (z and 7)
        return types.staticLoc(fromX, fromZ, zone.level, layer)?.copy(x = x, z = z, level = level)
    }

    /** Whether a tile is in the area the client last built. */
    private fun inScene(client: HeadlessClient, x: Int, z: Int): Boolean {
        val origin = client.sceneOrigin() ?: return false
        return x - origin.x in 0 until HeadlessClient.SCENE_SIZE && z - origin.z in 0 until HeadlessClient.SCENE_SIZE
    }

    private fun locMatches(loc: SceneLoc, name: String?, id: Int): Boolean {
        val shown = loc.shown
        if (name != null) return shown != null && name.equals(Dialogue.clean(shown.name), ignoreCase = true)
        return loc.loc.id == id || shown?.id == id
    }

    /** A multiloc's current form by the player's varps, as the client picks it; the loc itself when it isn't one. */
    private fun visibleLoc(client: HeadlessClient, type: LocType): LocType? {
        if (type.multiLocs.isEmpty()) return type
        val value = multiValue(client, type.multiVarbit, type.multiVarp)
        val id = if (value in 0 until type.multiLocs.size - 1) type.multiLocs[value] else type.multiLocs.last()
        return if (id < 0) null else types.locs[id]
    }

    /** An NPC's type as the client shows it: a multi-NPC's current form. Null for one that shows nothing. */
    private fun npcType(client: HeadlessClient, id: Int): NpcType? {
        val type = types.npcs[id] ?: return null
        if (type.multiNpcs.isEmpty()) return type
        val value = multiValue(client, type.multiVarbit, type.multiVarp)
        val shown = if (value in 0 until type.multiNpcs.size - 1) type.multiNpcs[value] else type.multiNpcs.last()
        return if (shown < 0) null else types.npcs[shown]
    }

    private fun multiValue(client: HeadlessClient, varbit: Int, varp: Int): Int {
        if (varbit >= 0) {
            val type = types.vars.varbit(varbit) ?: return -1
            return (client.varp(type.baseVar) ushr type.startBit) and type.mask
        }
        return if (varp >= 0) client.varp(varp) else -1
    }

    private fun npcMatches(type: NpcType, name: String?, id: Int): Boolean =
        if (name != null) name.equals(Dialogue.clean(type.name), ignoreCase = true) else type.id == id

    private fun itemMatches(itemId: Int, name: String?, id: Int): Boolean {
        if (name == null) return itemId == id
        val type = types.objs[itemId] ?: return false
        return name.equals(Dialogue.clean(type.name), ignoreCase = true)
    }

    private fun inventorySlot(items: List<osrsps.testclient.InvObj?>, name: String?, id: Int, skip: Int): Int =
        items.indices.firstOrNull { i -> i != skip && items[i]?.let { it.count > 0 && itemMatches(it.id, name, id) } == true } ?: -1

    private fun nearestGroundItem(client: HeadlessClient, name: String?, id: Int, at: Tile?): osrsps.testclient.GroundObjView? {
        val here = client.position ?: return null
        return client.groundObjs()
            .filter { it.coord.level == here.level && itemMatches(it.id, name, id) && (at == null || (it.coord.x == at.x && it.coord.z == at.y)) }
            .minByOrNull { distance(it.coord, here) }
    }

    private fun groundItemMissing(name: String?, id: Int, at: Tile?): String =
        "no item " + (if (name != null) "\"$name\"" else "id $id") + (if (at == null) "" else " at ${at.x}, ${at.y}") + " on the ground in the scene"

    private fun describeItem(name: String?, id: Int) = if (name != null) "\"$name\"" else "item $id"

    private companion object {
        const val EXAMINE = "Examine"
        const val REMOVE = "Remove"
        const val TAKE = "Take"
        const val TAKE_OP = 3

        const val LAYERS = 4

        /** What [modelWornId] gives until the player's appearance is read: no item has this id. */
        const val MODEL_NOT_READ = Int.MAX_VALUE

        /** Client scripts that change only what the client shows: toplevel_sidebutton_switch. */
        val CLIENT_ONLY_SCRIPTS = setOf(915)

        /** The examine script's argument holding the item's description. */
        const val EXAMINE_DESC_ARG = 2

        /** The prompts' client scripts and the meslayer mode each sets: a number, a line of text, the item search. */
        val PROMPT_SCRIPTS = mapOf(
            ScriptContext.MESLAYER_MODE7 to ScriptContext.MESLAYER_COUNT,
            ScriptContext.MESLAYER_MODE9 to ScriptContext.MESLAYER_STRING,
            GrandExchangeScreen.MESLAYER_MODE14 to ITEM_SEARCH_MODE,
        )
        const val ITEM_SEARCH_MODE = 14

        /** Screens whose frame (steelborder) draws a close button as child [FRAME_CLOSE_CHILD]: shopmain, ge_pricechecker, ge_offers. */
        val FRAME_CLOSE_BUTTONS = setOf((ShopScreen.MAIN shl 16) or 1, (PriceChecker.MAIN shl 16) or 1, (GrandExchangeScreen.MAIN shl 16) or 2)
        const val FRAME_CLOSE_CHILD = 11

        /** popupoverlay's confirm button, as the scripts click it. */
        const val POPUP_CONFIRM = 8
        const val GROUND_LOC_LAYER = 2
        const val PLAYER_OPTION_SLOTS = 8

        /** levelup_display: its title, text and continue. */
        const val LEVEL_UP = LevelUps.INTERFACE
        const val LEVEL_UP_TITLE = LevelUps.TEXT1
        const val LEVEL_UP_TEXT = LevelUps.TEXT2
        const val LEVEL_UP_CONTINUE = LevelUps.CONTINUE

        /** The params naming a worn item's options 2 to 9 in the equipment tab. */
        const val WORN_OP_FIRST_PARAM = 451

        /** The equipment tab's slot component for a wearpos (0 head to 13 ammo), or null where it shows none. */
        fun wornSlotComponent(wearpos: Int): Int? =
            EquipmentTab.SLOT_WEARPOS.indexOf(wearpos).takeIf { it >= 0 }?.let { EquipmentTab.WORNITEMS_SLOTS[it] }

        /** interface_inv_init (149) ties args[0], a component, to args[1], an inventory; shop_main_init (1074) the shop's. */
        const val INTERFACE_INV_INIT = 149
        const val SHOP_MAIN_INIT = 1074
        const val SHOP_MAIN_ITEMS = (300 shl 16) or 16

        /** Components that always list one inventory: the inventory tab, the bank's and price checker's items and the screens' inventory sides. */
        val STATIC_INVENTORY_COMPONENTS = mapOf(
            (InventoryTab.INTERFACE shl 16) or InventoryTab.ITEMS to InvBinding(Inventory.INV),
            (BankScreen.MAIN shl 16) or BankScreen.MAIN_ITEMS to InvBinding(Inventory.BANK),
            (BankScreen.SIDE shl 16) or BankScreen.SIDE_ITEMS to InvBinding(Inventory.INV),
            (BankScreen.SIDE shl 16) or BankScreen.SIDE_WORNOPS to InvBinding(Inventory.INV),
            (DepositBox.INTERFACE shl 16) or DepositBox.ITEMS to InvBinding(Inventory.INV),
            (ShopScreen.SIDE shl 16) or ShopScreen.SIDE_ITEMS to InvBinding(Inventory.INV),
            (Trades.SIDE shl 16) or Trades.SIDE_ITEMS to InvBinding(Inventory.INV),
            (GrandExchangeScreen.SIDE shl 16) or GrandExchangeScreen.SIDE_ITEMS to InvBinding(Inventory.INV),
            (PriceChecker.MAIN shl 16) or PriceChecker.ITEMS to InvBinding(PriceChecker.INV),
            (PriceChecker.SIDE shl 16) or PriceChecker.SIDE_ITEMS to InvBinding(Inventory.INV),
        )

        /** RuneLite's WorldPoint.distanceTo on one plane: the larger of the two axes. */
        fun distance(a: Coord, b: Coord) = max(abs(a.x - b.x), abs(a.z - b.z))

        fun slot(x: Int, z: Int, layer: Int): Long = (x.toLong() shl 32) or (z.toLong() shl 2) or layer.toLong()

        /** The menu op slot (0 to 4) whose text is the option, or -1. */
        fun opSlot(ops: List<String?>, option: String): Int = ops.indexOfFirst { it != null && Dialogue.clean(it).equals(option, ignoreCase = true) }
    }
}
