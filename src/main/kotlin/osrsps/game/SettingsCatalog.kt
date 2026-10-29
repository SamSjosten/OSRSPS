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
) {
    /**
     * The category drawn while not searching: enum 423 at settings_category, which in 241 is enum 422 again (checked
     * at load), so the category whose id is [id].
     */
    fun category(id: Int): SettingsCategory? = categories.firstOrNull { it?.id == id }

    /** How many clickzone slots a search can fill at most: every row of every category. */
    val slotCount: Int get() = categories.sumOf { it?.rows?.size ?: 0 }

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
) {
    val id: Int get() = params[PARAM_ID] ?: 0
    val kind: Int get() = params[PARAM_KIND] ?: KIND_TOGGLE

    fun flag(param: Int): Boolean = (params[param] ?: 0) != 0

    companion object {
        const val PARAM_ID = 1077
        const val PARAM_KIND = 1078

        const val KIND_TOGGLE = 0
        const val KIND_SLIDER = 1
        const val KIND_DROPDOWN = 2
        const val KIND_KEYBIND = 3
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

    /** Anything else: a device option, the window mode or a client script; the server can't read it. */
    data object Client : SettingVar

    /** The value the client reads, or null when the server can't know it. */
    fun read(vars: PlayerVars): Int? = when (this) {
        is Varp -> vars[id]
        is Varbit -> if (vars.types.varbit(id) != null) vars.getVarbit(id) else null
        is Inverted -> of.read(vars)?.let { 1 - it }
        is Bit -> if (vars.types.varbit(varbit) != null) (vars.getVarbit(varbit) ushr bit) and 1 else null
        is Conditional -> if (vars.types.varbit(varbit) == null) null else if (vars.getVarbit(varbit) == equals) then.read(vars) else 0
        Client -> null
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
            Client -> return false
        }
        return true
    }
}
