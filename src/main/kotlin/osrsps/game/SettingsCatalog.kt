package osrsps.game

/**
 * The All Settings window's settings, as the 241 cache lists them for the client (read by
 * [osrsps.cache.SettingsLoader]): its categories in the order the client draws and searches them, each category's rows,
 * and where each toggle and dropdown keeps its value. Only numbers are kept; the names and descriptions the cache
 * also holds are Jagex text the client shows itself.
 */
class SettingsCatalog(
    /** Enum 422 in order, the tabs' and the search's order. A null is an entry the enum doesn't have. */
    val categories: List<SettingsCategory?>,
    /** Every row a category or a dependency names, by struct id. */
    val rows: Map<Int, SettingRow>,
    /** settings_get_toggle's switch: where each toggle's value is. */
    val toggles: Map<Int, SettingVar>,
    /** settings_get_dropdown's switch: where each dropdown's value is. */
    val dropdowns: Map<Int, SettingVar>,
    /** settings_get_keybind's switch: where each keybind's key is. */
    val keybinds: Map<Int, SettingVar> = emptyMap(),
    /** settings_get_number_input's switch: where each number input's value is. */
    val numbers: Map<Int, SettingVar> = emptyMap(),
    /** settings_get_colour's switch: where each colour input's colour is, -1 for the row's default. */
    val colours: Map<Int, SettingVar> = emptyMap(),
) {
    /**
     * The category drawn while not searching: enum 423 at settings_category, which in 241 is enum 422 again (checked
     * at load), so the category whose id is [id].
     */
    fun category(id: Int): SettingsCategory? = categories.firstOrNull { it?.id == id }

    /** How many clickzone slots a search can fill at most: every row of every category. */
    val slotCount: Int get() = categories.sumOf { it?.rows?.size ?: 0 }

    /** The most options any dropdown or keybind lists. */
    val maxOptions: Int get() = rows.values.maxOfOrNull { it.options } ?: 0

    companion object {
        val NONE = SettingsCatalog(emptyList(), emptyMap(), emptyMap(), emptyMap())
    }
}

/** A settings category (a struct in enum 422) and its rows, in the order the client draws them. */
class SettingsCategory(val struct: Int, val id: Int, val params: Map<Int, Int>, val rows: List<SettingRow>)

/**
 * One row of the window (a struct in a category's rows enum): a setting, or a header, text or "see more" line.
 * [params] holds the struct's int params; a param it doesn't have reads as 0, as the client's booleans do.
 */
class SettingRow(
    val struct: Int,
    val params: Map<Int, Int>,
    /** Param 1080/1081: rows that must have these values for this one to work. */
    val requires: List<SettingDependency> = emptyList(),
    /** Param 1082/1083: rows that must not. */
    val excludes: List<SettingDependency> = emptyList(),
    /**
     * A dropdown's or keybind's option count: the outputs of the enum at param 1091 (script422's enum_getoutputcount).
     * 0 for other rows.
     */
    val options: Int = 0,
) {
    val id: Int get() = params[PARAM_ID] ?: 0
    val kind: Int get() = params[PARAM_KIND] ?: KIND_TOGGLE

    companion object {
        const val PARAM_ID = 1077
        const val PARAM_KIND = 1078
        const val PARAM_OPTIONS_ENUM = 1091

        const val KIND_TOGGLE = 0
        const val KIND_SLIDER = 1
        const val KIND_DROPDOWN = 2
        const val KIND_KEYBIND = 3
        const val KIND_NUMBER = 4
        const val KIND_COLOUR = 9

        /** A colour row's default colour, shown while its var reads -1. */
        const val PARAM_DEFAULT_COLOUR = 1230
    }
}

/** One entry of a dependency list: the row (struct, null when the enum has none there) and the value compared. */
data class SettingDependency(val struct: Int?, val value: Int)

/** Where a setting's value is, as a settings_get_* script's case reads it. */
sealed interface SettingVar {
    /** `get_varp n; return`. */
    data class Varp(val id: Int) : SettingVar

    /** `get_varbit n; return`. */
    data class Varbit(val id: Int) : SettingVar

    /** `calc(1 - %var)`: shown the other way round from how it is stored. */
    data class Inverted(val of: SettingVar) : SettingVar

    /** `testbit(%varbit, bit)`. */
    data class Bit(val varbit: Int, val bit: Int) : SettingVar

    /** `if (%varbit = equals) return(then)`, else 0: toggle 162, re-failure notifications. */
    data class Conditional(val varbit: Int, val equals: Int, val then: SettingVar) : SettingVar

    /**
     * `enum(int, int, enum, %of)`: [of] looked up in an int enum, [outputs] by key, [default] for a key it hasn't. The
     * keybinds use enum 1161, which maps 1 to 13 to themselves.
     */
    data class Mapped(val of: SettingVar, val outputs: Map<Int, Int>, val default: Int) : SettingVar

    /** `%of - by` and the like: [of] read with [by] added. The chat colours keep their colour plus 1, 0 for none. */
    data class Offset(val of: SettingVar, val by: Int) : SettingVar

    /** Anything else: a device option, the window mode or a client script; the server can't read it. */
    data object Client : SettingVar

    /** The value the client reads, or null when the server can't know it. */
    fun read(vars: PlayerVars): Int? = when (this) {
        is Varp -> vars[id]
        is Varbit -> if (vars.types.varbit(id) != null) vars.getVarbit(id) else null
        is Inverted -> of.read(vars)?.let { 1 - it }
        is Bit -> if (vars.types.varbit(varbit) != null) (vars.getVarbit(varbit) ushr bit) and 1 else null
        is Conditional -> if (vars.types.varbit(varbit) == null) null else if (vars.getVarbit(varbit) == equals) then.read(vars) else 0
        is Mapped -> of.read(vars)?.let { outputs[it] ?: default }
        is Offset -> of.read(vars)?.let { it + by }
        Client -> null
    }

    /**
     * Stores [value] so that [read] gives it back. False when the server can't: the var isn't in the cache, or no
     * stored value reads as [value].
     */
    fun set(vars: PlayerVars, value: Int): Boolean {
        when (this) {
            is Varp -> vars[id] = value
            is Varbit -> {
                val type = vars.types.varbit(id) ?: return false
                if (type.mask != -1 && value !in 0..type.mask) return false
                vars.setVarbit(id, value)
            }
            is Inverted -> return of.set(vars, 1 - value)
            is Bit -> {
                if (vars.types.varbit(varbit) == null || value !in 0..1) return false
                vars.setVarbit(varbit, (vars.getVarbit(varbit) and (1 shl bit).inv()) or (value shl bit))
            }
            is Conditional -> {
                if (vars.types.varbit(varbit) == null || vars.getVarbit(varbit) != equals) return false
                return then.set(vars, value)
            }
            is Mapped -> {
                // The lowest key with that output; for the default, the lowest key the enum hasn't (0 for the keybinds).
                val key = outputs.filterValues { it == value }.keys.minOrNull()
                    ?: (if (value == default) generateSequence(0) { it + 1 }.first { it !in outputs } else return false)
                return of.set(vars, key)
            }
            is Offset -> return of.set(vars, value - by)
            Client -> return false
        }
        return true
    }

    /** Flips a toggle's value where it is stored. False when there's nothing the server can flip. */
    fun flip(vars: PlayerVars): Boolean {
        when (this) {
            is Varp -> vars[id] = 1 - vars[id]
            is Varbit -> {
                if (vars.types.varbit(id) == null) return false
                vars.setVarbit(id, 1 - vars.getVarbit(id))
            }
            is Inverted -> return of.flip(vars)
            is Bit -> {
                if (vars.types.varbit(varbit) == null) return false
                vars.setVarbit(varbit, vars.getVarbit(varbit) xor (1 shl bit))
            }
            is Conditional -> {
                if (vars.types.varbit(varbit) == null || vars.getVarbit(varbit) != equals) return false
                return then.flip(vars)
            }
            is Mapped, is Offset, Client -> return false
        }
        return true
    }
}
