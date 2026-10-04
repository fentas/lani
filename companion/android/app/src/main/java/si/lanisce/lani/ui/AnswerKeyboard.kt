package si.lanisce.lani.ui

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/** An answer field's own options: no auto-correct (the suggestions go with [NoSuggestions]). */
val AnswerKeyboard = KeyboardOptions(autoCorrectEnabled = false)

/**
 * The text fields in [content] ask the keyboard for no suggestion strip and no auto-correct: for what the learner types
 * as an answer (an exercise, a review card, what they say in a talk), where the strip would give the words away.
 * Gboard ignores the plain "no suggestions" flag but not a visible password's type, so both are set on the request;
 * the text stays as it is and visible, and the letter chips below the field (č, š, ž …) are there as before. The
 * tutor chat keeps the keyboard's suggestions.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NoSuggestions(on: Boolean = true, content: @Composable () -> Unit) {
    if (!on) return content()
    InterceptPlatformTextInput(
        interceptor = { request, next ->
            next.startInputMethod(
                PlatformTextInputMethodRequest { attrs: EditorInfo ->
                    request.createInputConnection(attrs).also {
                        attrs.inputType = (attrs.inputType and InputType.TYPE_MASK_VARIATION.inv()) or
                            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    }
                },
            )
        },
        content = content,
    )
}
