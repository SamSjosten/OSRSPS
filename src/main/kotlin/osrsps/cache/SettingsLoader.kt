package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.Unpooled
import osrsps.game.SettingDependency
import osrsps.game.SettingRow
import osrsps.game.SettingVar
import osrsps.game.SettingsCatalog
import osrsps.game.SettingsCategory

private val logger = KotlinLogging.logger {}

/**
 * Reads the All Settings window's settings the way its client scripts do (rev 241, Joshua-F/osrs-dumps 2026-10-28):
 * - settings_populate_settings walks enum 422 (or enum 423 at settings_category) for the categories, each category
 *   struct's param 745 for its rows enum, and each row struct's param 1077 (id) and 1078 (kind);
 * - settings_check_dependency follows a row's params 1080/1081 and 1082/1083 to other rows;
 * - settings_get_toggle (script 6716), settings_get_dropdown (3962) and settings_get_keybind (3963) are switches on
 *   the setting id whose cases read the setting's var, which is how the server learns where each value lives;
 * - setting_show_dropdown lists as many options as the row's enum (param 1091) has outputs (script422).
 */
object SettingsLoader {
    private const val CONFIG_INDEX = 2
    private const val CLIENTSCRIPT_INDEX = 12
    private const val ENUM_GROUP = 8
    private const val STRUCT_GROUP = 34

    const val CATEGORIES_ENUM = 422
    const val CATEGORIES_BY_ID_ENUM = 423
    const val TOGGLE_GETTER = 6716
    const val DROPDOWN_GETTER = 3962
    const val KEYBIND_GETTER = 3963

    const val PARAM_CATEGORY_ID = 743
    const val PARAM_CATEGORY_ROWS = 745
    const val PARAM_REQUIRES = 1080
    const val PARAM_REQUIRED_VALUES = 1081
    const val PARAM_EXCLUDES = 1082
    const val PARAM_EXCLUDED_VALUES = 1083

    private const val PUSH_INT = 0
    private const val JUMP = 6
    private const val BRANCH_EQUALS = 8
    private const val ENUM = 3408
    private const val SUB = 4001
    private const val TESTBIT = 4010

    fun load(library: CacheLibrary): SettingsCatalog {
        val catalog = build(
            enum = { library.data(CONFIG_INDEX, ENUM_GROUP, it) },
            struct = { library.data(CONFIG_INDEX, STRUCT_GROUP, it) },
            script = { library.data(CLIENTSCRIPT_INDEX, it, 0) },
        )
        val toggleIds = catalog.rows.values.filter { it.kind == SettingRow.KIND_TOGGLE }.map { it.id }.toSet()
        val unreadable = catalog.toggles.filterValues { it == SettingVar.Client }.keys.sorted()
        logger.info {
            "Loaded ${catalog.categories.count { it != null }} settings categories, ${catalog.slotCount} rows, " +
                "${catalog.toggles.size - unreadable.size} of ${catalog.toggles.size} toggles' vars and " +
                "${catalog.dropdowns.count { it.value != SettingVar.Client }} of ${catalog.dropdowns.size} dropdowns' and " +
                "${catalog.keybinds.count { it.value != SettingVar.Client }} of ${catalog.keybinds.size} keybinds'; " +
                "toggles the client keeps to itself: $unreadable"
        }
        val noGetter = toggleIds - catalog.toggles.keys - 0
        if (noGetter.isNotEmpty()) logger.warn { "Settings: toggles with no case in settings_get_toggle: ${noGetter.sorted()}" }
        return catalog
    }

    /** Builds the catalog from config enums, config structs and client scripts, each by id (null when missing). */
    fun build(enum: (Int) -> ByteArray?, struct: (Int) -> ByteArray?, script: (Int) -> ByteArray?): SettingsCatalog {
        val enums = HashMap<Int, EnumType>()
        fun enumType(id: Int): EnumType? = enums[id] ?: enum(id)?.let(::decodeEnum)?.also { enums[id] = it }

        val rows = HashMap<Int, SettingRow>()
        val visiting = HashSet<Int>()
        fun dependencies(params: Map<Int, Int>, structsParam: Int, valuesParam: Int, row: (Int) -> SettingRow?): List<SettingDependency> {
            val structs = params[structsParam]?.let(::enumType) ?: return emptyList()
            val values = params[valuesParam]?.let(::enumType)
            // settings_check_dependency walks the struct enum by index, reading the value enum at the same index.
            return (0 until structs.count).map { index ->
                val target = structs.outputs[index]
                target?.let(row)
                SettingDependency(target, values?.get(index) ?: 0)
            }
        }
        fun row(id: Int): SettingRow? {
            rows[id]?.let { return it }
            if (!visiting.add(id)) return null
            val params = decodeStructInts(struct(id) ?: return null)
            // script422 also drops trailing disabled options for a row with param 2637; no row the desktop client
            // shows has it. script7106 swaps the enum for row 318 (anti-aliasing), the enhanced client's.
            val kind = params[SettingRow.PARAM_KIND] ?: SettingRow.KIND_TOGGLE
            val options = if (kind == SettingRow.KIND_DROPDOWN || kind == SettingRow.KIND_KEYBIND) {
                params[SettingRow.PARAM_OPTIONS_ENUM]?.let(::enumType)?.count ?: 0
            } else {
                0
            }
            val loaded = SettingRow(
                id, params,
                dependencies(params, PARAM_REQUIRES, PARAM_REQUIRED_VALUES, ::row),
                dependencies(params, PARAM_EXCLUDES, PARAM_EXCLUDED_VALUES, ::row),
                options,
            )
            rows[id] = loaded
            return loaded
        }

        val categoryEnum = enumType(CATEGORIES_ENUM)
        if (categoryEnum == null) {
            logger.warn { "No enum $CATEGORIES_ENUM (settings categories) in the cache; the All Settings window's clicks will do nothing" }
            return SettingsCatalog.NONE
        }
        val categories = (0 until categoryEnum.count).map { index ->
            val structId = categoryEnum.outputs[index] ?: return@map null
            val params = decodeStructInts(struct(structId) ?: return@map null)
            val rowsEnum = params[PARAM_CATEGORY_ROWS]?.let(::enumType)
            // settings_populate_settings skips an index its rows enum has nothing at: no clickzone slot for it.
            val categoryRows = if (rowsEnum == null) emptyList() else (0 until rowsEnum.count).mapNotNull { rowsEnum.outputs[it]?.let(::row) }
            SettingsCategory(structId, params[PARAM_CATEGORY_ID] ?: index, params, categoryRows)
        }
        val byId = enumType(CATEGORIES_BY_ID_ENUM)
        categories.forEachIndexed { index, category ->
            if (category != null && byId != null && byId.outputs[category.id] != category.struct) {
                logger.warn { "Settings: enum $CATEGORIES_BY_ID_ENUM has ${byId.outputs[category.id]} for category ${category.id}, not its struct ${category.struct} (enum $CATEGORIES_ENUM index $index)" }
            }
        }
        val toggles = script(TOGGLE_GETTER)?.let { decodeGetter(it) } ?: emptyMap<Int, SettingVar>().also {
            logger.warn { "No client script $TOGGLE_GETTER (settings_get_toggle) in the cache; toggles won't be mirrored" }
        }
        val dropdowns = script(DROPDOWN_GETTER)?.let { decodeGetter(it, ::enumType) } ?: emptyMap()
        val keybinds = script(KEYBIND_GETTER)?.let { decodeGetter(it, ::enumType) } ?: emptyMap()
        return SettingsCatalog(categories, rows, toggles, dropdowns, keybinds)
    }

    /**
     * A settings_get_* switch: each case's instructions up to its `return`, matched against the shapes the 241 scripts
     * use. Anything else (a device option, a client script, the window mode) is [SettingVar.Client]. [enum] gives the
     * enums an `enum(...)` case looks in.
     */
    fun decodeGetter(data: ByteArray, enum: (Int) -> EnumType? = { null }): Map<Int, SettingVar> {
        val script = ClientScript.decode(data)
        val switch = script.switches.singleOrNull() ?: error("Getter script has ${script.switches.size} switches, expected 1")
        val switchAt = script.opcodes.indexOf(ClientScript.SWITCH)
        val ops = script.opcodes
        val args = script.intOperands
        fun shape(at: Int, vararg expected: Int): Boolean =
            at + expected.size <= ops.size && expected.indices.all { ops[at + it] == expected[it] }
        return switch.mapValues { (_, offset) ->
            val at = switchAt + 1 + offset
            when {
                shape(at, ClientScript.GET_VARBIT, ClientScript.RETURN) -> SettingVar.Varbit(args[at])
                shape(at, ClientScript.GET_VARP, ClientScript.RETURN) -> SettingVar.Varp(args[at])
                shape(at, PUSH_INT, ClientScript.GET_VARBIT, SUB, ClientScript.RETURN) && args[at] == 1 ->
                    SettingVar.Inverted(SettingVar.Varbit(args[at + 1]))
                shape(at, ClientScript.GET_VARBIT, PUSH_INT, TESTBIT, ClientScript.RETURN) -> SettingVar.Bit(args[at], args[at + 1])
                // get_varbit a; push v; branch_equals +1; jump (on); get_varbit b; return
                shape(at, ClientScript.GET_VARBIT, PUSH_INT, BRANCH_EQUALS, JUMP, ClientScript.GET_VARBIT, ClientScript.RETURN) && args[at + 2] == 1 ->
                    SettingVar.Conditional(args[at], args[at + 1], SettingVar.Varbit(args[at + 4]))
                // push input type; push output type; push enum; get_varbit v; enum; return
                shape(at, PUSH_INT, PUSH_INT, PUSH_INT, ClientScript.GET_VARBIT, ENUM, ClientScript.RETURN) ->
                    enum(args[at + 2])?.let { SettingVar.Mapped(SettingVar.Varbit(args[at + 3]), it.outputs, it.defaultInt) } ?: SettingVar.Client
                else -> SettingVar.Client
            }
        }
    }

    /** A config enum's int outputs by key (string outputs are dropped), and how many outputs it has. */
    class EnumType(val outputs: Map<Int, Int>, val count: Int, val defaultInt: Int) {
        /** `enum(int, int, ...)`: the output at [key], or the default. */
        fun get(key: Int): Int = outputs[key] ?: defaultInt
    }

    /** As RuneLite's cache EnumLoader reads it. */
    fun decodeEnum(data: ByteArray): EnumType {
        val buf = Unpooled.wrappedBuffer(data)
        val outputs = HashMap<Int, Int>()
        var count = 0
        var defaultInt = 0
        while (true) {
            when (val opcode = buf.readUnsignedByte().toInt()) {
                0 -> break
                1, 2 -> buf.skipBytes(1)
                3 -> skipString(buf)
                4 -> defaultInt = buf.readInt()
                5 -> {
                    count = buf.readUnsignedShort()
                    repeat(count) {
                        buf.readInt()
                        skipString(buf)
                    }
                }
                6 -> {
                    count = buf.readUnsignedShort()
                    repeat(count) { outputs[buf.readInt()] = buf.readInt() }
                }
                else -> error("Unknown enum opcode $opcode")
            }
        }
        // A struct enum's default is -1 (null): left out, so a missing key reads as no struct.
        return EnumType(outputs, count, defaultInt)
    }

    /** A config struct's int params (opcode 249); string params are skipped. */
    fun decodeStructInts(data: ByteArray): Map<Int, Int> {
        val buf = Unpooled.wrappedBuffer(data)
        val params = HashMap<Int, Int>()
        while (true) {
            when (val opcode = buf.readUnsignedByte().toInt()) {
                0 -> break
                249 -> repeat(buf.readUnsignedByte().toInt()) {
                    val isString = buf.readUnsignedByte().toInt() == 1
                    val key = buf.readUnsignedMedium()
                    if (isString) skipString(buf) else params[key] = buf.readInt()
                }
                else -> error("Unknown struct opcode $opcode")
            }
        }
        return params
    }

    private fun skipString(buf: io.netty.buffer.ByteBuf) {
        buf.readerIndex(buf.indexOf(buf.readerIndex(), buf.writerIndex(), 0) + 1)
    }
}
