package com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete

/*
 Mention Autocomplete — people in the channel and the text model of the composer (a port of
 mentionData.ts). The composer's text is plain text plus "chips": ranges of it that are a mention
 of a person. Everything here is plain Kotlin shared by the loop's script and by a person's typing.
*/

data class MentionAutocompletePerson(
    val id: String,
    val first: String,
    val last: String,
    val role: String,
    /** Three-stop gradient of the avatar disc. */
    val colors: List<Long>,
    val online: Boolean,
) {
    val fullName get() = "$first $last"
    val initials get() = "${first[0]}${last[0]}"
}

data class MentionAutocompleteChip(val start: Int, val len: Int, val person: Int)

data class MentionAutocompleteDoc(
    val text: String,
    val chips: List<MentionAutocompleteChip>,
    /** Index into chips of the chip a first Backspace selected, or -1. */
    val selected: Int,
)

internal object MentionAutocompleteData {
    /** Ordered by who spoke last in the channel: an empty query lists them in this order. */
    val PEOPLE = listOf(
        MentionAutocompletePerson("theo", "Theo", "Lindqvist", "Design", listOf(0xFFC3CB86, 0xFF97A356, 0xFF727E3A), true),
        MentionAutocompletePerson("priya", "Priya", "Raman", "Product", listOf(0xFFD1A6C9, 0xFFA9739F, 0xFF83507D), true),
        MentionAutocompletePerson("maya", "Maya", "Okafor", "Copy lead", listOf(0xFFF3A882, 0xFFE07A5C, 0xFFC4573F), true),
        MentionAutocompletePerson("jonah", "Jonah", "Weiss", "Marketing", listOf(0xFFF2C978, 0xFFDBA545, 0xFFB88228), false),
        MentionAutocompletePerson("marcus", "Marcus", "Bell", "Engineering", listOf(0xFF8CC0D1, 0xFF5797AD, 0xFF3A768C), true),
        MentionAutocompletePerson("mayra", "Mayra", "Diaz", "Support", listOf(0xFFA6CDAF, 0xFF76AA84, 0xFF548A63), false),
    )

    const val MAX_ROWS = 4

    fun mention(p: MentionAutocompletePerson) = "@${p.fullName}"

    /** First names that start with the query, then last names, then names containing it; at most MAX_ROWS. */
    fun rank(query: String, people: List<MentionAutocompletePerson> = PEOPLE): List<Int> {
        val q = query.lowercase()
        if (q.isEmpty()) return people.indices.take(MAX_ROWS)
        val first = mutableListOf<Int>()
        val last = mutableListOf<Int>()
        val rest = mutableListOf<Int>()
        people.forEachIndexed { i, p ->
            if (p.first.lowercase().startsWith(q)) first.add(i)
            else if (p.last.lowercase().startsWith(q)) last.add(i)
            else if (p.fullName.lowercase().contains(q)) rest.add(i)
        }
        return (first + last + rest).take(MAX_ROWS)
    }

    /** The stretch of the person's full name that the query matched, for the bold letters. */
    fun matchRange(p: MentionAutocompletePerson, query: String): IntRange? {
        val q = query.lowercase()
        if (q.isEmpty()) return null
        val name = p.fullName.lowercase()
        if (name.startsWith(q)) return 0 until q.length
        val lastAt = p.first.length + 1
        if (name.substring(lastAt).startsWith(q)) return lastAt until lastAt + q.length
        val at = name.indexOf(q)
        return if (at < 0) null else at until at + q.length
    }

    // ---- Composer text ------------------------------------------------------------------------

    val EMPTY_DOC = MentionAutocompleteDoc("", emptyList(), -1)

    /** Plain and chip runs of a doc, in order, for drawing it. chip = -1 for plain text. */
    class Run(val text: String, val chip: Int)

    fun runs(doc: MentionAutocompleteDoc): List<Run> {
        val out = mutableListOf<Run>()
        var at = 0
        doc.chips.forEachIndexed { i, c ->
            if (c.start > at) out.add(Run(doc.text.substring(at, c.start), -1))
            out.add(Run(doc.text.substring(c.start, c.start + c.len), i))
            at = c.start + c.len
        }
        if (at < doc.text.length) out.add(Run(doc.text.substring(at), -1))
        return out
    }

    class Query(val at: Int, val query: String)

    private val ACTIVE = Regex("(^|\\s)@([A-Za-z]*)$")

    /** The mention being typed before the caret: where its "@" is and what follows it, or null. */
    fun activeQuery(doc: MentionAutocompleteDoc, caret: Int): Query? {
        val before = doc.text.substring(0, caret.coerceIn(0, doc.text.length))
        val m = ACTIVE.find(before) ?: return null
        val typed = m.groupValues[2]
        val at = before.length - typed.length - 1
        if (doc.chips.any { at >= it.start && at < it.start + it.len }) return null
        return Query(at, typed)
    }

    sealed class Edit {
        /** A normal edit: the new doc and where the caret ended up. */
        class Text(val doc: MentionAutocompleteDoc, val caret: Int) : Edit()
        /** Backspace reached a chip: select it (first press). */
        class Select(val chip: Int) : Edit()
        /** ... or break it into plain text (second press). */
        class Break(val chip: Int, val doc: MentionAutocompleteDoc) : Edit()
    }

    /** Interprets a change of the input's plain text against the doc. */
    fun applyText(doc: MentionAutocompleteDoc, next: String): Edit {
        val prev = doc.text
        var p = 0
        while (p < prev.length && p < next.length && prev[p] == next[p]) p += 1
        var ep = prev.length
        var en = next.length
        while (ep > p && en > p && prev[ep - 1] == next[en - 1]) {
            ep -= 1
            en -= 1
        }
        val delta = en - ep
        val pureDelete = en == p && ep > p
        val hit = doc.chips.indexOfFirst { it.start < ep && it.start + it.len > p }
        if (hit >= 0 && pureDelete) {
            if (doc.selected != hit) return Edit.Select(hit)
            return Edit.Break(hit, MentionAutocompleteDoc(prev, doc.chips.filterIndexed { i, _ -> i != hit }, -1))
        }
        val chips = mutableListOf<MentionAutocompleteChip>()
        for (c in doc.chips) {
            if (c.start + c.len <= p) chips.add(c)
            else if (c.start >= ep) chips.add(c.copy(start = c.start + delta))
            // A chip the edit overlapped is no longer a mention.
        }
        return Edit.Text(MentionAutocompleteDoc(next, chips, -1), en)
    }

    class Inserted(val doc: MentionAutocompleteDoc, val chip: Int, val caret: Int)

    /** Replaces the typed "@query" (from `at` to the caret) with a chip for the person, and a space. */
    fun insertMention(doc: MentionAutocompleteDoc, at: Int, caret: Int, person: Int): Inserted {
        val label = mention(PEOPLE[person])
        val text = doc.text.substring(0, at) + label + " " + doc.text.substring(caret.coerceAtMost(doc.text.length))
        val delta = label.length + 1 - (caret - at)
        val chips = mutableListOf<MentionAutocompleteChip>()
        var index = 0
        for (c in doc.chips) {
            if (c.start < at) {
                chips.add(c)
                index += 1
            } else chips.add(c.copy(start = c.start + delta))
        }
        chips.add(index, MentionAutocompleteChip(at, label.length, person))
        return Inserted(MentionAutocompleteDoc(text, chips, -1), index, at + label.length + 1)
    }
}
