package si.lanisce.lani.ui.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import si.lanisce.lani.game.HintPiece
import si.lanisce.lani.game.HintSpeech
import si.lanisce.lani.game.Tip
import si.lanisce.lani.game.TipAction
import si.lanisce.lani.game.scene.TownMarker
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Localized
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget

// A tip's words (companion/GAME.md "Guidance"): its line for an adult and a child ([text]), what its 🔊 says ([speech]), its
// bubble on the map ([marker]). Pure, but for the string tables; the card is TipCard.kt's.

/** The tip's arguments with its [Tip.thing] as [lang] has it, for a line in one language. */
private fun Tip.argsIn(thingAs: Localized?, spoken: Boolean): Map<String, Any?> =
    args + (if (spoken) said() else emptyMap()) + ("thing" to thingAs)

/** What the line says aloud instead of an emoji: the resource it stands for. */
private fun Tip.said(): Map<String, Any?> {
    val a = action as? TipAction.Trade ?: return emptyMap()
    val get = (args["getEmoji"] as? String)?.let { e -> si.lanisce.lani.game.Res.entries.firstOrNull { it.emoji == e } } ?: return emptyMap()
    return mapOf("getEmoji" to get.names).takeIf { a.give != get }.orEmpty()
}

/**
 * The tip's line: an adult's "target · base" (bi(), the culture's names in each language), a child's ([child]) in the base
 * with the course language's word for the thing marked «…» (Teo: "Neu im Dorf: «scuola»! …").
 */
fun Tip.text(child: Boolean): String =
    if (!child) L10n.label(L10n.pair, key, argsIn(thing, spoken = false))
    else inBase(childKey, *argsIn(thing?.let { t -> Localized { _ -> t.of(L10n.pair.target) } }, spoken = false).toList().toTypedArray())

/** What the 🔊 says: an adult's line in the language learned (the narrator's voice) then the base; a child's by its marks. */
fun Tip.speech(child: Boolean): List<HintPiece> =
    if (child) HintSpeech.pieces(quiet(inBase(childKey, *argsIn(thing?.let { t -> Localized { _ -> t.of(L10n.pair.target) } }, spoken = true).toList().toTypedArray())))
    else {
        val a = argsIn(thing, spoken = true).toList().toTypedArray()
        val target = quiet(inTarget(key, *a))
        val base = quiet(inBase(key, *a))
        listOfNotNull(HintPiece(target, true), HintPiece(base, false).takeIf { base != target })
    }

/** [s] without emoji and the symbols a voice would spell out. */
private fun quiet(s: String): String = buildString {
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        val t = Character.getType(cp)
        val skip = Character.isSupplementaryCodePoint(cp) || t == Character.OTHER_SYMBOL.toInt() || cp == 0xFE0F || cp == 0x200D || cp in 0x2190..0x21FF
        if (!skip) appendCodePoint(cp)
        i += Character.charCount(cp)
    }
}.replace(Regex("""\s{2,}"""), " ").replace(Regex("""\s+([.,!?:;])"""), "$1").trim()

/** [line] with its «marked» words in bold and the marks gone, as the card shows it. */
internal fun marked(line: String): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (m in Regex("«([^»]*)»").findAll(line)) {
        append(line.substring(at, m.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Black)) { append(m.groupValues[1]) }
        at = m.range.last + 1
    }
    append(line.substring(at))
}

/** The tip's moment as a bubble on the map at its thing ([Tip.at]): "tip:<moment>", its picture, its line. */
fun Tip.marker(child: Boolean): TownMarker? = at?.let { TownMarker("$TIP_MARKER$moment", TownMarker.Kind.TIP, emoji, HintSpeech.plain(text(child)), it) }

/**
 * The map's bubble this tip stands in for: the same thing at the same place (a request's quiet "🔁" for its drill, its "!"
 * for the first request, someone to meet, a visitor, a festival, the surprise, a project's step), so the map never shows two
 * bubbles for one thing; the tip's goes once it is dealt with and the other comes back. A giver's "!" for several requests
 * stays (the tip names only one).
 */
fun Tip.standsFor(m: TownMarker): Boolean = when (val a = action) {
    is TipAction.Retry -> m.id == "${si.lanisce.lani.game.scene.TownMarkers.BACK}${a.id}"
    is TipAction.Quest -> m.id == "quest:${a.id}" && m.count <= 1
    is TipAction.Meet -> m.id == "arrival:${a.id}"
    is TipAction.Villager -> m.id == "visitor:${a.id}"
    is TipAction.Festival -> m.id == "festival:${a.id}"
    is TipAction.Surprise -> m.id.startsWith("surprise:")
    is TipAction.Projects -> a.id != null && m.id == "project:${a.id}"
    else -> false
}

/** A tip's bubble's id on the map. */
const val TIP_MARKER = "tip:"

/** The tip bubble's look on the map: paper with a gold rim and a 💡 in its corner (TownBubbles). */
internal val TipRim = Color(0xFFE0A526)
