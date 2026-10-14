package si.lanisce.lani.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import si.lanisce.lani.app.ChatMessage
import si.lanisce.lani.app.QaHooks

/**
 * A tutor message as plain text, as its bubble reads ([markdownPlain]): what "📄 Kopiraj · Copy" puts on the clipboard
 * and "↗️ Deli · Share" hands on (README "Tutor chat"); the learner's own message as typed ([plainOf]).
 */
class MarkdownPlainTest {
    @Test fun `the tutor's imeti, its list a line an item, the bold and the italics out`() {
        assertEquals(
            """
            Imeti – the whole verb:
            • jaz imam · ti imaš · on/ona ima
            • midva imava · vidva/onadva imata
            • mi imamo · vi imate · oni imajo

            Vida ima gostilno. (one) · Vida in Nejc imata konja. (two) · Vsi imajo čas. (three+)

            The two-person endings are the same everywhere: -va (me + one more), -ta (two others), like sva / sta with biti.
            """.trimIndent(),
            markdownPlain(QaHooks.TUTOR_TABLE),
        )
    }

    @Test fun `a table a line a row, its cells between dots, its rule and its empty corner left out`() {
        val zaimki = """
            **mi / vi / oni** at a glance:

            | | 1 | 2 | 3+ |
            |---|---|---|---|
            | I/we | jaz | **midva** | **mi** |
            | you | ti | **vidva** | **vi** |
            | he/she/they | on, ona | **onadva** | **oni** |

            **vi** is also the polite "you" to one person. Usually you drop them; say the pronoun for contrast.
        """.trimIndent()
        assertEquals(
            """
            mi / vi / oni at a glance:

            1 · 2 · 3+
            I/we · jaz · midva · mi
            you · ti · vidva · vi
            he/she/they · on, ona · onadva · oni

            vi is also the polite "you" to one person. Usually you drop them; say the pronoun for contrast.
            """.trimIndent(),
            markdownPlain(zaimki),
        )
    }

    @Test fun `headings, quotes, numbers, rules and code as they read, paragraphs a blank line apart`() {
        val md = "# Glagol *biti*\n\n\n> „Sem doma.“ — I am at home.\n\n1. `sem`\n2) **si**\n\n---\n\n* je\n• smo"
        assertEquals("Glagol biti\n\n„Sem doma.“ — I am at home.\n\n1. sem\n2. si\n\n• je\n• smo", markdownPlain(md))
        assertEquals("A table with no rule\nx · y", markdownPlain("A table with no rule\n| x | y |"))
    }

    @Test fun `the learner's own message is copied as typed, the tutor's as it reads`() {
        assertEquals("**kako** se reče 2*3?", plainOf(ChatMessage(fromMe = true, text = "**kako** se reče 2*3?")))
        assertEquals("kako se reče", plainOf(ChatMessage(fromMe = false, text = "**kako** se reče")))
    }
}
