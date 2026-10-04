package si.lanisce.lani.ui.scene

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.scene.SceneHint
import si.lanisce.lani.game.scene.SceneObject
import si.lanisce.lani.game.scene.SceneSights
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.theme.AlpineGreen

/**
 * What the words panel's chips say of when a word is in the picture (companion/SCENES.md, "The words panel"): a hint's
 * tag on a chip ("🌙"), in words in the learner's pair ("🌙 ponoči · at night"), and which chips open their word.
 */
object WordChips {
    /** A hint's tag on a chip: "🌙", "🏗️🪜" (the kozolec to build), "⬆️3" (the place's level), "🏗️⚙️" (a project). */
    fun tag(h: SceneHint): String = when (h.kind) {
        SceneHint.Kind.DAWN -> "🌅"
        SceneHint.Kind.DAY -> "☀️"
        SceneHint.Kind.DUSK -> "🌇"
        SceneHint.Kind.NIGHT -> "🌙"
        SceneHint.Kind.MOON -> "🌕"
        SceneHint.Kind.SPRING -> "🌸"
        SceneHint.Kind.SUMMER -> "🌻"
        SceneHint.Kind.AUTUMN -> "🍂"
        SceneHint.Kind.WINTER -> "❄️"
        SceneHint.Kind.NOT_WINTER -> "🌿"
        SceneHint.Kind.SOMETIMES -> "🎲"
        SceneHint.Kind.LATER -> "🔄"
        SceneHint.Kind.BUILD -> "🏗️" + (h.building?.emoji ?: "")
        SceneHint.Kind.UPGRADE -> "⬆️" + (h.level ?: "")
        SceneHint.Kind.PROJECT -> "🏗️" + (Catalog.projectFrames.firstOrNull { it.id == h.project }?.emoji ?: "")
        SceneHint.Kind.AGE -> "⏳" + (h.age?.emoji ?: "")
        SceneHint.Kind.GONE -> "🏚️"
    }

    /** The tags of [hints] side by side: "🌇🌙🌿🎲". */
    fun tags(hints: List<SceneHint>): String = hints.joinToString("") { tag(it) }

    /** [h] in words, in [lang] (a word of the language learned is in [target]): "ponoči", "zgradi: Kozolec". */
    fun text(h: SceneHint, lang: Lang, target: Lang = L10n.pair.target): String {
        fun t(key: String, vararg args: Pair<String, Any?>) = L10n.text(lang, key, args.toMap(), target)
        return when (h.kind) {
            SceneHint.Kind.DAWN -> t("wordGroups.dawn")
            SceneHint.Kind.DAY -> t("wordGroups.day")
            SceneHint.Kind.DUSK -> t("wordGroups.dusk")
            SceneHint.Kind.NIGHT -> t("wordGroups.night")
            SceneHint.Kind.MOON -> t("wordGroups.moon")
            SceneHint.Kind.SPRING -> t("wordGroups.spring")
            SceneHint.Kind.SUMMER -> t("wordGroups.summer")
            SceneHint.Kind.AUTUMN -> t("wordGroups.autumn")
            SceneHint.Kind.WINTER -> t("wordGroups.winter")
            SceneHint.Kind.NOT_WINTER -> t("wordGroups.notWinter")
            SceneHint.Kind.SOMETIMES -> t("wordGroups.sometimes")
            SceneHint.Kind.LATER -> t("wordGroups.later")
            SceneHint.Kind.BUILD -> {
                val name: Any = h.building?.names ?: ""
                val what = if ((h.level ?: 1) > 1) t("wordGroups.buildLevel", "building" to name, "level" to h.level) else name
                t("wordGroups.build", "building" to what)
            }
            SceneHint.Kind.UPGRADE -> t("wordGroups.upgrade", "level" to (h.level ?: 1))
            SceneHint.Kind.PROJECT -> t("wordGroups.project", "project" to (Catalog.projects.firstOrNull { it.id == h.project }?.nameText ?: h.project.orEmpty()))
            SceneHint.Kind.AGE -> t("wordGroups.age", "age" to (h.age?.names ?: ""))
            SceneHint.Kind.GONE -> t("wordGroups.gone")
        }
    }

    /**
     * [hints] in words, in [pair], the tags first: "🌇🌙 ob mraku, ponoči · at dusk, at night"; empty for none. Without
     * [tags] (what TalkBack reads): "ob mraku, ponoči · at dusk, at night".
     */
    fun note(hints: List<SceneHint>, pair: LangPair = L10n.pair, tags: Boolean = true): String {
        if (hints.isEmpty()) return ""
        val target = hints.joinToString(", ") { text(it, pair.target, pair.target) }
        val base = hints.joinToString(", ") { text(it, pair.base, pair.target) }
        return (if (tags) "${tags(hints)} " else "") + "$target · $base"
    }

    /** Each hint once, in words, for the legend under the chips: "🌙 ponoči · at night". */
    fun legend(hints: List<SceneHint>, pair: LangPair = L10n.pair): List<String> = hints.distinct().map { note(listOf(it), pair) }

    /**
     * Whether a chip of [groups] opens [o]'s word when tapped: what the picture shows now (a find, as a tap on it there
     * is) and what was seen before, even though it isn't there now; never a word still hidden, which stays unrevealed.
     */
    fun opens(o: SceneObject, groups: SceneSights.Groups): Boolean =
        groups.now.any { it.slot == o.slot } || groups.seen.any { it.first.slot == o.slot }

    /** The note of when [slot] shows, for its word's bubble when it was seen before and isn't in the picture now; null otherwise. */
    fun noteOf(slot: String, groups: SceneSights.Groups, pair: LangPair = L10n.pair): String? =
        groups.seen.firstOrNull { it.first.slot == slot }?.second?.takeIf { it.isNotEmpty() }?.let { note(it, pair) }
}

/**
 * The scene's words in three groups (companion/SCENES.md, "The words panel"): "👀 Zdaj tukaj · Here now", what the picture
 * shows at this moment (the ones not found yet a "❔" to find by tapping the picture, as before); "🗂️ Že videno · Seen
 * before", words found before that aren't in the picture now, each opening its word as a found chip does, with a tag of
 * when it shows; "🔒 Še skrito · Still hidden", not found and not there now, the thing's silhouette and when or how to find
 * it. Under them a legend of the tags. With nothing seen before or hidden, the chips show as one group, as they did.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SceneWordGroups(
    groups: SceneSights.Groups,
    found: Set<String>,
    selected: String?,
    learned: (SceneObject) -> Boolean,
    onWord: (SceneObject) -> Unit,
) {
    val split = groups.seen.isNotEmpty() || groups.hidden.isNotEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (split) GroupTitle("👀 ${bi("wordGroups.hereNow", "n" to groups.now.size)}")
        if (groups.now.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (o in groups.now) {
                val known = learned(o)
                val isFound = o.slot in found
                FilterChip(
                    selected = o.slot == selected,
                    onClick = { onWord(o) },
                    label = {
                        Text(
                            if (isFound) "${o.emoji ?: "•"} ${o.sl}${if (known) "  ✓" else ""}" else "❔",
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = if (isFound) "${o.sl} · ${o.en}${if (known) ", ${bi("sceneScreen.known")}" else ""}" else bi("sceneScreen.notFoundYet")
                            },
                        )
                    },
                    colors = if (known) FilterChipDefaults.filterChipColors(labelColor = AlpineGreen) else FilterChipDefaults.filterChipColors(),
                )
            }
        }
        if (groups.seen.isNotEmpty()) {
            GroupTitle("🗂️ ${bi("wordGroups.seenBefore")}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for ((o, hints) in groups.seen) {
                    val known = learned(o)
                    FilterChip(
                        selected = o.slot == selected,
                        onClick = { onWord(o) },
                        label = {
                            Text(
                                "${o.emoji ?: "•"} ${o.sl}${if (known) "  ✓" else ""}" + if (hints.isEmpty()) "" else "  ${WordChips.tags(hints)}",
                                modifier = Modifier.clearAndSetSemantics {
                                    contentDescription = "${o.sl} · ${o.en}${if (known) ", ${bi("sceneScreen.known")}" else ""}" +
                                        if (hints.isEmpty()) "" else ", ${WordChips.note(hints, tags = false)}"
                                },
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = if (known) AlpineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
        if (groups.hidden.isNotEmpty()) {
            GroupTitle("🔒 ${bi("wordGroups.stillHidden")}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((o, hints) in groups.hidden) HiddenChip(o, hints)
            }
        }
        if (split && groups.hints.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 2.dp)) {
                for (line in WordChips.legend(groups.hints)) {
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
}

/** A word still hidden: not a button (it isn't there to find), the thing's silhouette and when or how it comes. */
@Composable
private fun HiddenChip(o: SceneObject, hints: List<SceneHint>) {
    val shade = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.heightIn(min = 32.dp).semantics(mergeDescendants = true) {
            contentDescription = "${bi("wordGroups.stillHidden")}: " + (WordChips.note(hints, tags = false).ifEmpty { bi("sceneScreen.notFoundYet") })
        },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val emoji = o.emoji
            if (emoji == null) {
                Text("❔", modifier = Modifier.clearAndSetSemantics { })
            } else {
                // its shape, filled in one colour: what to look for, not what it is called
                Text(
                    emoji,
                    modifier = Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = 0.6f }
                        .drawWithContent { drawContent(); drawRect(shade, blendMode = BlendMode.SrcIn) }
                        .clearAndSetSemantics { },
                )
            }
            if (hints.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(WordChips.tags(hints), style = MaterialTheme.typography.labelLarge, modifier = Modifier.clearAndSetSemantics { })
            }
        }
    }
}
