package si.lanisce.lani.ui

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.l10n.Lang

/**
 * What an answer field asks of the keyboard ([AnswerEditor], set by [NoSuggestions]): plain text with no suggestions and
 * no auto-correct (not a visible password's type: Gboard locked the language there), Gboard's "no decoding", and the
 * answer's language as the keyboard's hint.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnswerEditorTest {
    /** What Compose asks for an answer field ([AnswerKeyboard]: no auto-correct), multi-line or single-line. */
    private val multiLine = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    private val singleLine = InputType.TYPE_CLASS_TEXT

    @Test fun anAnswerStaysPlainTextWithoutSuggestions() {
        val a = AnswerEditor.of(multiLine, null, Lang.SL)
        assertEquals(InputType.TYPE_CLASS_TEXT, a.inputType and InputType.TYPE_MASK_CLASS)
        assertEquals("plain text, not a password's", InputType.TYPE_TEXT_VARIATION_NORMAL, a.inputType and InputType.TYPE_MASK_VARIATION)
        assertEquals(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, a.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        assertEquals("the field's own flags stay", InputType.TYPE_TEXT_FLAG_MULTI_LINE, a.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        assertEquals(singleLine or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, AnswerEditor.of(singleLine, null, Lang.SL).inputType)
    }

    @Test fun autoCorrectAndCompletionsGoEvenWhenTheFieldAskedForThem() {
        val asked = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        val a = AnswerEditor.of(asked, null, Lang.SL)
        assertEquals(0, a.inputType and (InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE))
        assertEquals(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, a.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
    }

    @Test fun gboardIsAskedNotToDecodeKeepingTheFieldsOwnOptions() {
        assertEquals("com.google.android.inputmethod.latin.noDecoding", AnswerEditor.of(singleLine, null, Lang.SL).privateImeOptions)
        assertEquals("nm,$GBOARD_NO_DECODING", AnswerEditor.of(singleLine, "nm", Lang.SL).privateImeOptions)
        assertEquals("once", GBOARD_NO_DECODING, AnswerEditor.of(singleLine, " $GBOARD_NO_DECODING ,", Lang.SL).privateImeOptions)
    }

    @Test fun theKeyboardOpensInTheAnswersLanguage() {
        assertEquals("sl", AnswerEditor.of(singleLine, null, Lang.SL).hintLocales)
        assertEquals("a learner of Italian, or a visit to an Italian town", "it", AnswerEditor.of(singleLine, null, Lang.IT).hintLocales)
        assertNull("no language: the keyboard's own", AnswerEditor.of(singleLine, null, null).hintLocales)
    }

    @Test fun appliedToTheEditorInfo() {
        val attrs = EditorInfo().apply { inputType = multiLine; imeOptions = EditorInfo.IME_ACTION_DONE }
        AnswerEditor.of(attrs.inputType, attrs.privateImeOptions, Lang.SL).applyTo(attrs)
        assertEquals(multiLine or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, attrs.inputType)
        assertEquals(GBOARD_NO_DECODING, attrs.privateImeOptions)
        assertEquals("sl", attrs.hintLocales?.toLanguageTags())
        assertEquals("the IME action stays", EditorInfo.IME_ACTION_DONE, attrs.imeOptions)

        val keyboards = EditorInfo().apply { inputType = singleLine }
        AnswerEditor.of(keyboards.inputType, null, null).applyTo(keyboards)
        assertNull("no language: no hint", keyboards.hintLocales)
    }
}
