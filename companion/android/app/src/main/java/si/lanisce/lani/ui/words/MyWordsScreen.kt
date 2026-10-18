package si.lanisce.lani.ui.words

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.Screen
import si.lanisce.lani.app.DictionaryLook
import si.lanisce.lani.app.MyWordsSearch
import si.lanisce.lani.app.WordQuery
import si.lanisce.lani.data.MyWord
import si.lanisce.lani.data.MyWords
import si.lanisce.lani.data.WordEntry
import si.lanisce.lani.data.Words
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.GradientHeader
import si.lanisce.lani.ui.theme.AlpineGreen
import java.time.LocalDate

// "📚 Moje besede · My words" (data/MyWords.kt; companion/README.md "My words"): the learner's words, the newest few on
// top, then all of them by letter; a search that filters them as it is typed and looks the word up in the dictionary
// (GET /lookup) when it isn't one of theirs. A word opens its card (WordSheet: its meanings, its forms, "📋 Vse oblike").
// The app draws edge to edge and the window doesn't shrink for the keyboard: the screen keeps above it (imePadding after
// navigationBarsPadding), the search field at its top, the list taking the room left.

@Composable
fun MyWordsScreen(vm: AppViewModel, at: Screen.MyWords) {
    BackHandler { vm.leaveMyWords(at) }
    val d = vm.content.dashboard
    val words = remember(d?.pool, d?.learned) { MyWords.of(d?.pool.orEmpty(), d?.learned.orEmpty()) }
    val scope = rememberCoroutineScope()
    val search = remember { MyWordsSearch(scope, vm.words::lookUp) }
    val today = remember { LocalDate.now() }
    val groups = remember(words, today) { MyWords.groups(words, today) }
    val found = remember(words, search.query) { MyWords.filter(words, search.query) }
    val known = words.count { it.learned }
    val list = rememberLazyListState()
    // a new search starts at the top of what it found
    LaunchedEffect(search.query) { if (list.firstVisibleItemIndex > 0) list.scrollToItem(0) }
    // QA reads what the list shows (a debug or qa build)
    if (si.lanisce.lani.BuildConfig.QA) LaunchedEffect(search.query, search.look, words.size) {
        android.util.Log.i("MyWords", "«${search.query}» ${if (search.query.isBlank()) words.size else found.size} of ${words.size}; dictionary ${search.look::class.simpleName}")
    }
    val open = { w: String -> vm.words.open(WordQuery(w, "", null, Words.SEARCH)) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
        val learning = words.size - known
        GradientHeader(
            "📚 ${bi("myWords.title")}",
            listOfNotNull(
                bi("myWords.known", "n" to known),
                bi("myWords.learning", "n" to learning).takeIf { learning > 0 },
            ).joinToString(" · "),
            if (at.fromPacks) "← ${bi("homeScreen.newWords")}" else "← ${bi("common.progress")}",
            onBack = { vm.leaveMyWords(at) },
            bottom = 16.dp,
        )
        SearchField(search.query, onChange = { search.type(it, words) }, onClear = search::clear)
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
            if (search.query.isBlank()) {
                if (words.isEmpty()) item("empty") {
                    Text(
                        "🌱 ${bi("myWords.empty")}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp),
                    )
                }
                for (g in groups) {
                    item("h-${g.key}") { GroupHead(if (g.recent) "🆕 ${bi("myWords.recent")}" else g.key) }
                    items(g.words, key = { "${g.key}-${it.id}" }) { w -> WordRow(w) { open(w.word) } }
                }
            } else {
                if (found.isNotEmpty()) item("h-mine") { GroupHead("📚 ${bi("myWords.yours", "n" to found.size)}") }
                items(found, key = { "f-${it.id}" }) { w -> WordRow(w) { open(w.word) } }
                item("dictionary") { Dictionary(search.look, mine = found.isNotEmpty(), open = open) }
            }
        }
    }
}

/** The search: 🔍, what is typed, ✕ to empty it; the keyboard's search key puts the keyboard away (the list is there already). */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onClear: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        placeholder = { Text(bi("myWords.search"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Text("🔍", modifier = Modifier.clearAndSetSemantics { }) },
        trailingIcon = if (value.isEmpty()) null else {
            { TextButton(onClick = onClear) { Text("✕", modifier = Modifier.semantics { contentDescription = bi("common.close") }) } }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
    )
}

@Composable
private fun GroupHead(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** One of the learner's words: the word, what it means; one still being learned with 🌱. A tap opens its card. */
@Composable
private fun WordRow(w: MyWord, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (w.meaning.isNotBlank()) Text(
                    w.meaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (!w.learned) Text("🌱", modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = bi("myWords.learningOne") })
        }
    }
}

/**
 * The dictionary under the learner's matches ([mine]: there are some): its entries of the word searched (one of the
 * learner's says so), each opening the word's card, where it can be added; nothing found, offline or an older node said
 * quietly, and only when nothing of theirs matched.
 */
@Composable
private fun Dictionary(look: DictionaryLook, mine: Boolean, open: (String) -> Unit) {
    when (look) {
        DictionaryLook.Idle -> Unit
        is DictionaryLook.Looking -> Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(bi("myWords.lookingUp", "word" to look.word), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is DictionaryLook.Found -> Column {
            GroupHead("📖 ${bi("myWords.dictionary")}")
            for (e in look.entries) EntryRow(e) { open(look.word) }
        }
        is DictionaryLook.NotFound -> if (!mine) Quiet("🤷 ${bi("words.notInDictionary")}")
        is DictionaryLook.Offline -> if (!mine) Quiet("📶 ${bi("words.cantReachTutor")}")
        DictionaryLook.Unavailable -> if (!mine) Quiet("🧭 ${bi("words.lookupNeedsNewerTutor")}")
    }
}

@Composable
private fun Quiet(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))

/** A dictionary entry: its emoji, its lemma (a noun's gender), what it is and means; ✓ when it is one of the learner's. */
@Composable
private fun EntryRow(e: WordEntry, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).heightIn(min = 52.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            e.emoji?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                val gender = when (e.gender?.lowercase()) {
                    "m" -> bi("words.genderM")
                    "f" -> bi("words.genderF")
                    "n" -> bi("words.genderN")
                    else -> null
                }
                Text(e.lemma + (gender?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val what = listOfNotNull(e.pos?.takeIf { it.isNotBlank() }?.let(::posLabel), Words.meaning(e).takeIf { it.isNotBlank() })
                if (what.isNotEmpty()) Text(what.joinToString(" — "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (e.known) Text("✓", color = AlpineGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = bi("words.inYourWords") })
        }
    }
}
