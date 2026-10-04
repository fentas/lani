package si.lanisce.lani.data

private val markup = Regex("[*`>#_]")

/**
 * The first non-blank line of a tutor message without its Markdown marks, at most [max] characters:
 * the text for a notification or the tutor bar. Empty when there is no text at all.
 */
fun previewLine(markdown: String, max: Int = Int.MAX_VALUE): String =
    markdown.replace(markup, "").lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(max)
