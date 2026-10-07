package si.lanisce.lani.ui

import android.os.LocaleList
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang

/** An answer field's own options: no auto-correct (the suggestions go with [NoSuggestions]). */
val AnswerKeyboard = KeyboardOptions(autoCorrectEnabled = false)

/**
 * Gboard's private option for a field it should not decode ([EditorInfo.privateImeOptions]: its package and the key):
 * no suggestion strip (its toolbar shows instead), no auto-correct, no glide typing; the language's layout stays (č on a
 * long press of c), and so does the language switch (the globe key, a long press on the space bar). Gboard keeps its
 * strip for [InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS] outside a list of apps of its own.
 */
const val GBOARD_NO_DECODING = "com.google.android.inputmethod.latin.noDecoding"

/**
 * What an answer field asks of the keyboard: [inputType] (the field's text type, with no suggestions and no
 * auto-correct), [privateImeOptions] (the field's own and [GBOARD_NO_DECODING]) and [hintLocales] (the language of the
 * answer as a language tag, or null: the keyboard's own).
 */
data class AnswerEditor(val inputType: Int, val privateImeOptions: String, val hintLocales: String?) {
    /** Sets this on what the field asks of the keyboard ([NoSuggestions]). */
    fun applyTo(attrs: EditorInfo) {
        attrs.inputType = inputType
        attrs.privateImeOptions = privateImeOptions
        hintLocales?.let { attrs.hintLocales = LocaleList.forLanguageTags(it) }
    }

    companion object {
        /**
         * An answer in [lang], in a field that asked for [inputType] and [privateImeOptions]. The type stays the field's
         * (plain text, not a visible password's as before: in a password's field Gboard drops the globe key where the
         * emoji key shows, and on Jan's phone kept to English), with no suggestions and no auto-correct; Gboard gets
         * [GBOARD_NO_DECODING] too. [lang] goes to the keyboard as its code ("sl"): Gboard opens in that language when
         * the learner has it (in their layout for it), else in it for this field alone; null leaves the keyboard's.
         */
        fun of(inputType: Int, privateImeOptions: String?, lang: Lang?): AnswerEditor {
            val off = InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE
            val type = (inputType and off.inv()) or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            val own = privateImeOptions?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            return AnswerEditor(type, (own + GBOARD_NO_DECODING).distinct().joinToString(","), lang?.code)
        }
    }
}

/**
 * The text fields in [content] are answers in [lang] (the learner's target; during a visit, the town's language): what
 * the learner types as an answer (an exercise, a review card, a dialog's turn, what they say in a talk), where a
 * suggestion strip or an auto-correct (Gboard's Slovene makes "hisa" "hiša") would give the words away. The keyboard is
 * asked for neither and to open in [lang] ([AnswerEditor]); the learner can switch its language as in any field. The
 * text stays as it is, and the letter chips below the field (č, š, ž …) are there as before. The tutor chat keeps the
 * keyboard's suggestions and language ([on] false).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NoSuggestions(on: Boolean = true, lang: Lang? = L10n.pair.target, content: @Composable () -> Unit) {
    if (!on) return content()
    InterceptPlatformTextInput(
        interceptor = { request, next ->
            next.startInputMethod(
                PlatformTextInputMethodRequest { attrs: EditorInfo ->
                    request.createInputConnection(attrs).also {
                        AnswerEditor.of(attrs.inputType, attrs.privateImeOptions, lang).applyTo(attrs)
                    }
                },
            )
        },
        content = content,
    )
}
