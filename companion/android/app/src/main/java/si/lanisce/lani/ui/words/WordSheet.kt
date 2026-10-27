package si.lanisce.lani.ui.words

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.Adding
import si.lanisce.lani.app.Requesting
import si.lanisce.lani.app.WordCard
import si.lanisce.lani.app.WordQuery
import si.lanisce.lani.app.WordState
import si.lanisce.lani.data.WordEntry
import si.lanisce.lani.data.WordLookup
import si.lanisce.lani.data.WordSuggestion
import si.lanisce.lani.data.Words
import si.lanisce.lani.game.scene.StickerSpot
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.ui.LaniSheet
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.theme.AlpineGreen

/** What a tap on a word of [line] (translated [en]) does: opens its card. [from]: where, e.g. [si.lanisce.lani.data.Words.SCENE]. */
fun lookUpIn(vm: AppViewModel, line: String, en: String?, from: String): (WordToken) -> Unit =
    { t -> vm.words.open(WordQuery(t.text, line, en?.takeIf { it.isNotBlank() }, from)) }

/**
 * A tapped word's card: the word big with 🔊 and the line it's in, then each meaning (emoji, dictionary form and
 * gender, part of speech and what this form is, the English, an example) with "➕ Add to my words", and where the
 * meanings come from. Not in the dictionary: the words it may be instead, "➕ Add to my words" all the same (the tutor
 * writes its meaning: [MissingWord]) and the tutor to ask. The node too old for lookups: the tutor is asked instead.
 */
@Composable
fun WordSheet(vm: AppViewModel, card: WordCard) {
    val q = card.query
    LaniSheet(onDismissRequest = vm.words::close) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(q.word, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false).semantics { heading() })
                // in the word's own language (a town's word on its card at home: that language's voices, not the home one's)
                si.lanisce.lani.ui.WordSpeakButton(vm.speaker, q.word, q.lang, Modifier.padding(start = 8.dp))
            }
            if (q.line.isNotBlank()) {
                Text("«${q.line.trim()}»", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (val s = card.state) {
                WordState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(bi("words.lookingUp"), style = MaterialTheme.typography.bodyMedium)
                }
                WordState.Offline -> {
                    Text("📶 ${bi("words.cantReachTutor")}", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = vm.words::retry) { Text("↻ ${bi("common.retry")}") }
                }
                WordState.Unavailable -> {
                    Text("🧭 ${bi("words.lookupNeedsNewerTutor")}", style = MaterialTheme.typography.bodyMedium)
                    AskButton(vm, prominent = true)
                }
                is WordState.NotFound -> {
                    MissingWord(vm, card, s.lookup)
                    Attribution(s.lookup.attribution)
                }
                is WordState.Found -> {
                    // on a visit the words are another town's language: they join the learner's words in that language
                    // (its own profile: WordsController.add)
                    // an aspect partner goes under the entry the line means (sesti, and sedeti under it: "sedite"),
                    // not as an entry of its own; a word the learner has shows its forms. Theirs: what the node says,
                    // and what the phone knows before it does (a run's words just learned; a card's word under the
                    // dictionary's lemma, "la casa" for casa)
                    val mine = vm.words.mine(q.lang, vm.content.deck(q.lang)?.pool)
                    val entries = s.lookup.entries.map { if (!it.known && Words.yours(it, mine)) it.copy(known = true) else it }
                    for ((e, partner) in Words.withPartners(entries)) {
                        Entry(
                            e, card.added[e.key], e.itemId?.let { vm.scenes.stickers.ofItem(it, e.emoji) }, onAdd = { vm.words.add(e) },
                            partner = partner, partnerAdding = partner?.let { card.added[it.key] }, onAddPartner = { partner?.let(vm.words::add) },
                        ) {
                            // home words only: a visit's words have no forms here; nor a word of an exercise still open
                            // (one of its forms may be the answer)
                            if (q.forms && q.lang == null && (e.known || card.added[e.key] != null) && e.pos != "name") {
                                WordFormsTable(vm, e.lemma, e.pos, e.glossEn.ifEmpty { e.gloss })
                            }
                            // its whole table as a card of its own, made at once: "📋 Vse oblike · All forms" (data/FormsCard.kt)
                            if (q.forms && q.lang == null) si.lanisce.lani.ui.AllFormsButton(vm, e)
                        }
                    }
                    AskButton(vm, prominent = false)
                    Attribution(s.lookup.attribution)
                }
            }
        }
    }
}

/**
 * One meaning of the tapped word, and under it its aspect [partner] when the lookup has that too (sesti, then sedeti:
 * "↔ par · partner", what each is, a line on how they differ, its own ➕); [more] below (the forms of a word the
 * learner has).
 */
@Composable
private fun Entry(
    e: WordEntry, adding: Adding?, sticker: StickerSpot?, onAdd: () -> Unit,
    partner: WordEntry? = null, partnerAdding: Adding? = null, onAddPartner: () -> Unit = {},
    more: @Composable () -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val emoji = e.emoji?.takeIf { it.isNotBlank() }
                if (sticker != null || emoji != null) {
                    StickerOr(sticker, 36.dp) { Text(emoji ?: "•", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clearAndSetSemantics { }) }
                    Spacer(Modifier.width(10.dp))
                }
                Text(lemmaLine(e), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                // the meaning the line means, where its partner is a choice too
                if (e.here) {
                    Spacer(Modifier.width(8.dp))
                    Text("📍 ${bi("words.here")}", style = MaterialTheme.typography.labelMedium, color = AlpineGreen, fontWeight = FontWeight.Bold)
                }
            }
            partsLine(e)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            val meaning = e.gloss.map { it.trim() }.filter { it.isNotEmpty() }
            // the meaning in the learner's base; when the node has only the English, it says so ("po angleško: house")
            val foreign = Words.foreignGloss(e, L10n.pair.base)?.let { "${inBase("words.glossIn", "lang" to it.code)}: " }.orEmpty()
            if (meaning.isNotEmpty()) Text(foreign + meaning.joinToString("; "), style = MaterialTheme.typography.bodyLarge)
            if (e.glossVia == "claude") {
                Text("🤖 ${bi("words.machineWritten")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // the tutor was asked for a meaning in the base: it replaces the English when it arrives (lexicon_updated)
            if (Words.tutorWritesMeaning(e, L10n.pair.base)) {
                Text("✍️ ${inBase("words.tutorWritesMeaning")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            e.example?.takeIf { it.sl.isNotBlank() }?.let { ex ->
                Text("„${ex.sl}“", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 2.dp))
                if (ex.en.isNotBlank()) Text(ex.en, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(1.dp))
            when {
                adding != null -> {
                    Text("✓ ${bi("words.added")}", style = MaterialTheme.typography.titleSmall, color = AlpineGreen, fontWeight = FontWeight.Bold)
                    Text(
                        when (adding) {
                            Adding.SENDING -> "…"
                            Adding.ADDED -> bi("words.inYourReviews")
                            Adding.QUEUED -> "📶 ${bi("words.reachesTutorOnline")}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                e.known -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.heightIn(min = 48.dp)) { Text("✓ ${bi("words.inYourWords")}") }
                e.itemId == null -> Unit // a name (a villager, Jan): nothing to learn as a word
                else ->FilledTonalButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) { Text("➕ ${bi("words.addToMyWords")}") }
            }
            e.partner?.let { p -> Partner(e, p, partner, partnerAdding, onAddPartner) }
            more()
        }
    }
}

/**
 * Under a verb, its aspect partner [p] ([entry]: the lookup's entry of it, when it has one): "↔ par · partner: sedeti —
 * to sit", and how the two differ, in the learner's base ("sesti: a movement, once · sedeti: a state that lasts").
 */
@Composable
private fun Partner(e: WordEntry, p: si.lanisce.lani.data.FormPartner, entry: WordEntry?, adding: Adding?, onAdd: () -> Unit) {
    val pf = if (e.aspect == "pf") e.lemma else if (p.aspect == "pf") p.lemma else null
    val impf = if (e.aspect == "impf") e.lemma else if (p.aspect == "impf") p.lemma else null
    val gloss = (entry?.gloss ?: p.gloss).map { it.trim() }.filter { it.isNotEmpty() }.take(2)
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "↔ ${bi("words.partner")}: ${p.lemma}" + if (gloss.isNotEmpty()) " — ${gloss.joinToString("; ")}" else "",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        entry?.grammar?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        if (pf != null && impf != null) {
            // a movement and the state after it (sesti, sedeti), or the same act once or going on (kupiti, kupovati)
            val how = if (p.kind == "position") inBase("words.partnerPosition", "pf" to pf, "impf" to impf)
            else inBase("words.partnerAspect", "pf" to pf, "impf" to impf)
            Text(how, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            adding != null -> Text("✓ ${bi("words.added")}", style = MaterialTheme.typography.labelLarge, color = AlpineGreen, fontWeight = FontWeight.Bold)
            entry == null || entry.itemId == null -> Unit
            entry.known -> Text("✓ ${bi("words.inYourWords")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) { Text("➕ ${bi("words.addToMyWords")}") }
        }
    }
}

/**
 * A word the dictionary doesn't know: first the words it may be instead ("🔎 Morda iščeš · Did you mean", each opening
 * its card in the same line), then "➕ Add to my words": the node asks the tutor for its meaning and adds the word when it
 * comes, the card waiting for it here ("✍️ Tvoj učitelj piše pomen …", or offline: it reaches the tutor once online);
 * the tutor to ask in the chat stays, quieter ("what does it mean here?").
 */
@Composable
private fun MissingWord(vm: AppViewModel, card: WordCard, lookup: WordLookup) {
    if (lookup.suggestions.isNotEmpty()) {
        Text("🔎 ${bi("words.didYouMean")}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        for (sug in lookup.suggestions) Suggestion(sug) { vm.words.openSuggestion(sug) }
    }
    Text("🤷 ${bi("words.notInDictionary")}", style = MaterialTheme.typography.bodyMedium)
    // asked for already (another tap, before a restart): the node says so
    when (card.request ?: Requesting.WAITING.takeIf { lookup.requested }) {
        null -> if (Words.requestable(card.query.word) != null) {
            FilledTonalButton(onClick = vm.words::request, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("➕ ${bi("words.addToMyWords")}") }
            Text("✍️ ${bi("words.tutorWritesIt")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Requesting.SENDING, Requesting.WAITING -> Waiting("✍️ ${bi("words.tutorWriting")}", bi("words.joinsYourWords"), busy = true)
        Requesting.QUEUED -> Waiting("📶 ${bi("words.reachesTutorOnline")}", bi("words.joinsYourWords"), busy = false)
        // its meaning came: the card looks again, and shows the tutor's entry added
        Requesting.DONE -> Waiting("✓ ${bi("words.added")}", bi("words.inYourReviews"), busy = false)
    }
    AskButton(vm, prominent = false)
}

/** The word asked for, waiting: [title] ("✍️ Tvoj učitelj piše pomen …"), what happens then; a spinner while [busy]. */
@Composable
private fun Waiting(title: String, then: String, busy: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(then, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A word the dictionary has that the tapped one may be: the form (its lemma when another), what it means; ›. */
@Composable
private fun Suggestion(s: WordSuggestion, onClick: () -> Unit) {
    val meaning = s.gloss.map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString("; ")
    val head = s.word + if (!s.lemma.equals(s.word, ignoreCase = true)) " (${s.lemma})" else ""
    Surface(
        onClick = onClick, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "${bi("words.didYouMean")}: $head, $meaning" },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(head, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val what = listOfNotNull(s.pos?.takeIf { it.isNotBlank() }?.let(::posLabel), meaning.takeIf { it.isNotEmpty() })
                if (what.isNotEmpty()) Text(what.joinToString(" — "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics { })
        }
    }
}

@Composable
private fun AskButton(vm: AppViewModel, prominent: Boolean) {
    val label = "📖 ${bi("common.askTutor")}"
    if (prominent) FilledTonalButton(onClick = vm.words::ask, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
    else TextButton(onClick = vm.words::ask) { Text(label) }
}

@Composable
private fun Attribution(text: String?) {
    if (text.isNullOrBlank()) return
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** "gozd · m.": the dictionary form, with a noun's gender. */
private fun lemmaLine(e: WordEntry): String = e.lemma + (genderLabel(e.gender)?.let { " · $it" } ?: "")

/** "samostalnik · noun — locative singular": the part of speech (a verb's aspect, when known), and what the tapped form is. */
private fun partsLine(e: WordEntry): String? =
    listOfNotNull(e.pos?.takeIf { it.isNotBlank() }?.let(::posLabel), aspectLabel(e.aspect), e.grammar?.takeIf { it.isNotBlank() }).joinToString(" — ").ifEmpty { null }

/** "dovršni · perfective", "nedovršni · imperfective". */
private fun aspectLabel(a: String?): String? = when (a) {
    "pf" -> bi("wordForms.aspectPf")
    "impf" -> bi("wordForms.aspectImpf")
    else -> null
}

private fun genderLabel(g: String?): String? = when (g?.lowercase()) {
    "m" -> inBase("words.genderM")
    "f" -> inBase("words.genderF")
    "n" -> inBase("words.genderN")
    else -> null
}

/** Wiktionary's parts of speech (kaikki.org's `pos`) in the learner's languages; one it doesn't know as it comes. */
internal fun posLabel(pos: String): String = when (pos.lowercase()) {
    "noun" -> bi("words.posNoun")
    "name" -> bi("words.posName")
    "verb" -> bi("words.posVerb")
    "adj", "adjective" -> bi("words.posAdjective")
    "adv", "adverb" -> bi("words.posAdverb")
    "pron", "pronoun" -> bi("words.posPronoun")
    "prep", "preposition" -> bi("words.posPreposition")
    "conj", "conjunction" -> bi("words.posConjunction")
    "num", "numeral" -> bi("words.posNumeral")
    "particle" -> bi("words.posParticle")
    "intj", "interjection" -> bi("words.posInterjection")
    "det", "determiner" -> bi("words.posDeterminer")
    "article" -> bi("words.posArticle")
    "phrase" -> bi("words.posPhrase")
    else -> pos
}
