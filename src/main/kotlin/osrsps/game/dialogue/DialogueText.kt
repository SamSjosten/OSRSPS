package osrsps.game.dialogue

import java.nio.file.Files
import java.nio.file.Path

/**
 * The words of each [Line], by key. They aren't in git: the OSRS Wiki's transcripts are Jagex's text, so they are typed
 * into the server's dialogue file (`data/dialogue.txt` on 214) and read from there. A line with no words shows its key
 * in brackets instead, so every conversation still runs, and is tested, without them.
 */
class DialogueText(private val words: Map<String, String>) {
    /** [line]'s words, with each `{arg}` replaced by its value in [values]. */
    operator fun get(line: Line, vararg values: Pair<String, Any>): String {
        val text = words[line.key]
            ?: return "[" + (listOf(line.key) + values.map { (name, value) -> "$name=$value" }).joinToString(" ") + "]"
        return values.fold(text) { result, (name, value) -> result.replace("{$name}", value.toString()) }
    }

    /** Whether [line] has words. */
    fun has(line: Line): Boolean = line.key in words

    companion object {
        val NONE = DialogueText(emptyMap())
    }
}

/**
 * The dialogue file: one line per key, `key = words`, in UTF-8. Blank lines and lines starting with `#` are notes. A key
 * with nothing after `=` has no words yet, and doesn't count when the key comes again with words.
 *
 * ```
 * # Talk-to, his first line
 * hans.greeting = ...
 * ```
 *
 * `osrsps dialogue-keys` writes it with every key the server has, each under the Wiki section its words come from,
 * keeping the words already typed in.
 */
object DialogueFile {
    const val NAME = "dialogue.txt"

    /** What [parse] read: the words by key, in file order, and what was wrong with the lines it couldn't use. */
    class Read(val words: Map<String, String>, val problems: List<String>)

    fun parse(text: String): Read {
        val words = LinkedHashMap<String, String>()
        val problems = mutableListOf<String>()
        text.removePrefix(BOM).lines().forEachIndexed { i, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith('#')) return@forEachIndexed
            val equals = line.indexOf('=')
            val key = if (equals < 0) "" else line.substring(0, equals).trim()
            if (!KEY.matches(key)) {
                problems += "line ${i + 1}: not 'key = words': $line"
                return@forEachIndexed
            }
            val text = line.substring(equals + 1).trim()
            // A key with no words yet doesn't count, so words can be added further down as well as after the '='.
            val before = words[key]
            if (!before.isNullOrEmpty() && text.isNotEmpty()) {
                problems += "line ${i + 1}: $key has words further up too; the first ones count"
            } else if (before.isNullOrEmpty()) {
                words[key] = text
            }
        }
        return Read(words, problems)
    }

    /** Keys in [words] that no line in [sets] has, and placeholders a line's words use that it doesn't fill. */
    fun check(words: Map<String, String>, sets: List<Lines>): List<String> {
        val lines = sets.flatMap { it.lines }.associateBy { it.key }
        val problems = mutableListOf<String>()
        for ((key, text) in words) {
            val line = lines[key]
            if (line == null) {
                problems += "$key isn't a line the server has"
                continue
            }
            for (match in PLACEHOLDER.findAll(text)) {
                if (match.groupValues[1] !in line.args) {
                    problems += "$key: ${match.value} isn't filled in; it can use ${line.args.joinToString(" ") { "{$it}" }.ifEmpty { "no placeholders" }}"
                }
            }
        }
        return problems
    }

    /** The words in [file] for the lines in [sets], with a summary and the problems found. A missing file has none. */
    fun load(file: Path, sets: List<Lines>): Loaded {
        val all = sets.flatMap { it.lines }
        if (!Files.exists(file)) {
            return Loaded(DialogueText.NONE, "No ${file.fileName}: all ${all.size} dialogue lines show their keys", emptyList())
        }
        val read = parse(Files.readString(file))
        val words = read.words.filterValues { it.isNotEmpty() }
        val text = DialogueText(words)
        val missing = all.filterNot(text::has)
        val summary = "${all.size - missing.size} of ${all.size} dialogue lines have words in ${file.fileName}" +
            if (missing.isEmpty()) "" else "; ${missing.size} show their keys (${missing.take(SHOWN).joinToString()}${if (missing.size > SHOWN) ", ..." else ""})"
        return Loaded(text, summary, read.problems + check(words, sets))
    }

    class Loaded(val text: DialogueText, val summary: String, val problems: List<String>)

    /**
     * The whole file for [sets], each key under where on the Wiki its words are, with the [words] already typed in.
     * Words for keys the server no longer has are kept at the end, so nothing typed in is lost.
     */
    fun render(sets: List<Lines>, words: Map<String, String>): String = buildString {
        appendLine("# OSRSPS dialogue: the words NPCs and players say. Not in git; fill each line from the Wiki page above it.")
        appendLine("# key = words. A line with no words shows its key in brackets. {name} is filled in by the server.")
        appendLine("# Rewritten by 'osrsps dialogue-keys', which keeps the words and adds new keys; notes added here are not kept.")
        val known = HashSet<String>()
        for (set in sets) {
            appendLine()
            appendLine("# ===== ${set.prefix}: ${set.url}")
            for (line in set.lines) {
                known += line.key
                appendLine()
                val placeholders = if (line.args.isEmpty()) "" else " (placeholders: ${line.args.joinToString(" ") { "{$it}" }})"
                appendLine("# ${line.source}$placeholders")
                appendLine("${line.key} = ${words[line.key].orEmpty()}".trimEnd())
            }
        }
        val unused = words.filterKeys { it !in known }
        if (unused.isNotEmpty()) {
            appendLine()
            appendLine("# ===== Not used by the server any more (renamed or removed); kept so nothing is lost")
            for ((key, text) in unused) appendLine("$key = $text".trimEnd())
        }
    }

    private const val BOM = "﻿"
    private const val SHOWN = 5
    private val KEY = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")
    private val PLACEHOLDER = Regex("\\{([a-z][a-z0-9_]*)}")
}
