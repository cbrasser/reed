package app.reed.data

/**
 * Undoes print leftovers in books converted from a scanned or typeset edition, as each chapter
 * is read: the printed page numbers left as lines of their own, and words the print edition
 * hyphenated at a line end, which show up mid-line once the text reflows ("gewe-  sen").
 * The patterns are narrow on purpose; well-made books pass through untouched.
 */
object ScanCleanup {

    // A page number where a printed page ended: alone between line breaks, or run onto the last
    // line of the page after a flattened line break (two spaces) and followed by a line break.
    private val pageNumber = Regex("""(?:(?:<br\s*/?>\s*){1,2}| {2,})(\d{1,4})\s*(?:<br\s*/?>\s*){1,2}""")

    // A print line break inside a word: letter, hyphen, then the line break flattened to 2+ spaces.
    // Not before "und"/"oder"/…, where the hyphen stands for a shared word part ("Hin- und Rückweg").
    private val brokenWord = Regex("""(\p{L})- {2,}(?!(?:und|oder|bis|sowie|and|or|to)\b)(\p{L})""")

    fun clean(html: String): String {
        if (!html.contains("-  ") && !pageNumber.containsMatchIn(html)) return html
        val withoutPages = pageNumber.replace(html) { match ->
            var i = match.range.first - 1
            while (i >= 0 && html[i].isWhitespace()) i--
            val before = html.getOrNull(i)
            when {
                // The page broke mid-word: let the broken-word pass join it.
                before == '-' -> "  "
                // Mid-sentence: the paragraph carries on across the page.
                before != null && (before.isLetterOrDigit() || before in ",;:") -> " "
                else -> "<br/>\n<br/>\n"
            }
        }
        return brokenWord.replace(withoutPages) { match ->
            val (end, start) = match.destructured
            // A capital after the break means a real hyphenated compound ("Quidditch-Weltmeisterschaft").
            if (start.first().isUpperCase()) "$end-$start" else "$end$start"
        }
    }

    fun clean(bytes: ByteArray): ByteArray {
        val html = bytes.toString(Charsets.UTF_8)
        val cleaned = clean(html)
        return if (cleaned === html) bytes else cleaned.toByteArray(Charsets.UTF_8)
    }
}
