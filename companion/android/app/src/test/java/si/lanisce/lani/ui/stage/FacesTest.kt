package si.lanisce.lani.ui.stage

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.RoleplayReply
import si.lanisce.lani.data.Scenario
import si.lanisce.lani.data.Talk
import si.lanisce.lani.data.json
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.DialogReply
import si.lanisce.lani.game.scene.Face
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.face
import si.lanisce.lani.game.scene.named
import si.lanisce.lani.ui.scene.DialogMood
import si.lanisce.lani.ui.scene.DialogRun
import si.lanisce.lani.ui.scene.Mood
import si.lanisce.lani.ui.talk.TalkStageRules

/**
 * The faces' logic (companion/SCENES.md, "Faces"): the names and their lenient reading, each pose's face, the content's
 * `face` on a line, a reply and a puzzled reaction carried into what is said, the reactions' faces, how a line's face holds
 * and eases back, the tutor's face in a role-play, and the talk's stage: how the character looks and when it folds.
 */
class FacesTest {
    @Test fun `the twelve names, the resting one never written`() {
        assertEquals(
            listOf("neutral", "happy", "laugh", "surprised", "curious", "confused", "thinking", "worried", "sad", "cross", "shy", "proud"),
            Face.entries.map { it.key },
        )
        assertEquals(Face.entries.drop(1), Face.written)
        assertEquals(Face.LAUGH, Face.of(" Laugh "))
        assertEquals(Face.NEUTRAL, Face.of("grin"))
        assertEquals(Face.NEUTRAL, Face.of(null))
        assertNull(Face.orNull("neutral"))
        assertNull(Face.orNull("smirk"))
        assertEquals(Face.SHY, Face.orNull("shy"))
        assertNull(Face.NEUTRAL.named)
        assertEquals(Face.SAD, Face.SAD.named)
    }

    @Test fun `each pose brings its face`() {
        assertEquals(Face.HAPPY, Pose.HAPPY.face)
        assertEquals(Face.LAUGH, Pose.CHEER.face)
        assertEquals(Face.SAD, Pose.SAD.face)
        assertEquals(Face.THINKING, Pose.THINK.face)
        assertEquals(Face.HAPPY, Pose.WAVE.face)
        for (p in listOf(Pose.IDLE, Pose.TALK, Pose.LISTEN, Pose.SLEEP)) assertEquals(Face.NEUTRAL, p.face)
    }

    @Test fun `every face but the resting ones has its emote, and the strong ones ease through a softer one`() {
        for (f in Face.entries) assertEquals(f.key, f.emote == null, f in setOf(Face.NEUTRAL, Face.HAPPY, Face.SAD, Face.SHY))
        assertEquals(Face.HAPPY, Face.LAUGH.softer)
        assertEquals(Face.CURIOUS, Face.SURPRISED.softer)
        assertNull(Face.HAPPY.softer)
    }

    @Test fun `the content's face is read leniently on a line, a reply and a puzzled reaction`() {
        val d = json.decodeFromString(
            Dialog.serializer(),
            """{"id": "d", "lines": [
                {"who": "micka", "sl": "Oj!", "en": "Hi!", "face": "laugh"},
                {"choices": [{"sl": "Živjo!", "ok": true, "reply": {"sl": "Lepo!", "face": "proud"}},
                             {"sl": "Adijo!", "why": "bye", "reply": {"sl": "Že greš?", "face": "sad"}}],
                 "puzzled": {"sl": "Hm?", "face": "confused"}},
                {"who": "micka", "sl": "No.", "en": "Well.", "face": "grin"},
                {"who": "micka", "sl": "Ja.", "en": "Yes.", "face": 3},
                {"who": "micka", "sl": "Ne.", "en": "No."}
            ]}""",
        )
        assertEquals(Face.LAUGH, d.lines[0].face)
        assertEquals(Face.PROUD, d.lines[1].choices[0].reply?.face)
        assertEquals(Face.SAD, d.lines[1].choices[1].reply?.face)
        assertEquals(Face.CONFUSED, d.lines[1].puzzled?.face)
        // a name the app doesn't know, a number: no face of their own
        assertNull(d.lines[2].face.named)
        assertNull(d.lines[3].face.named)
        assertNull(d.lines[4].face)
    }

    private val soup = Dialog(
        id = "juha",
        lines = listOf(
            DialogLine(who = "babica", sl = "Si lačen?", en = "Hungry?", face = Face.CURIOUS),
            DialogLine(
                choices = listOf(
                    DialogChoice("Ja, zelo.", ok = true, reply = DialogReply("Sedi k ognju.", face = Face.LAUGH)),
                    DialogChoice("Žejen sem.", why = "thirsty", reply = DialogReply("Žejen? Tu imaš vodo.", face = Face.SURPRISED)),
                    DialogChoice("Dobro jutro!", why = "evening"),
                ),
                puzzled = DialogReply("Kako, prosim?", face = Face.CONFUSED),
            ),
            DialogLine(who = "babica", sl = "Izvoli.", en = "Here you are."),
            DialogLine(choices = listOf(DialogChoice("Hvala!", ok = true, reply = DialogReply("Prosim.")), DialogChoice("Prosim!", why = "thanks"))),
        ),
    )

    @Test fun `what is said carries its face`() {
        val open = DialogRun.start(soup, "babica")
        assertEquals(Face.CURIOUS, open.said.last().face)
        // a wrong choice's reaction, with its face
        val reacted = open.choose(1)
        assertEquals("Žejen? Tu imaš vodo.", reacted.said.last().sl)
        assertEquals(Face.SURPRISED, reacted.said.last().face)
        // a right one's reply
        val replied = reacted.choose(0)
        assertEquals(Face.LAUGH, replied.said.last().face)
        // a line without one
        assertNull(replied.next().said.last().face)
        // a form typed that none of the choices has: the turn's puzzled reaction, with its face
        val eggs = Dialog(
            id = "jajca",
            lines = listOf(
                DialogLine(who = "micka", sl = "Koliko jajc?", en = "How many eggs?"),
                DialogLine(
                    choices = listOf(DialogChoice("Deset jajc, prosim.", ok = true), DialogChoice("Deset jajce, prosim.", why = "From five on, the genitive plural.")),
                    puzzled = DialogReply("Kako, prosim?", face = Face.CONFUSED),
                ),
            ),
        )
        val typed = DialogRun.start(eggs, "micka", modes = mapOf(1 to si.lanisce.lani.game.TurnMode.TYPE)).type("jajca")
        assertEquals("Kako, prosim?", typed.said.last().sl)
        assertEquals(Face.CONFUSED, typed.said.last().face)
        assertEquals(Face.CONFUSED, DialogMood.react(DialogRun.start(eggs, "micka", modes = mapOf(1 to si.lanisce.lani.game.TurnMode.TYPE)), typed, Mood.NONE, 0).face)
    }

    @Test fun `a line's face, or what the moment brings`() {
        val open = DialogRun.start(soup.copy(lines = soup.lines.mapIndexed { i, l -> if (i == 0) l.copy(face = null) else l }), "babica")
        // the first line greets with a smile
        assertEquals(Face.HAPPY, DialogMood.lineFace(open.said, 0))
        // a wrong choice without a face on its reaction: confused; a right one's reply without one: a smile
        val d = soup.copy(lines = soup.lines.map { l -> l.copy(choices = l.choices.map { c -> c.copy(reply = c.reply?.copy(face = null)) }) })
        val run = DialogRun.start(d, "babica").choose(1)
        assertEquals(Face.CONFUSED, DialogMood.lineFace(run.said, run.said.lastIndex))
        val right = run.choose(0)
        assertEquals(Face.HAPPY, DialogMood.lineFace(right.said, right.said.lastIndex))
        // a line after a line of theirs goes on with its softer face (after a smile, at rest); the learner's own has none
        val on = right.next()
        assertEquals(Face.NEUTRAL, DialogMood.lineFace(on.said, on.said.lastIndex))
        assertEquals(Face.NEUTRAL, DialogMood.lineFace(on.said, 1))
        val laughs = listOf(
            si.lanisce.lani.ui.scene.Said("micka", "Ha ha!", "", face = Face.LAUGH),
            si.lanisce.lani.ui.scene.Said("micka", "Zdaj pa jej.", ""),
            si.lanisce.lani.ui.scene.Said("micka", "Dober tek.", ""),
        )
        assertEquals(listOf(Face.LAUGH, Face.HAPPY, Face.NEUTRAL), laughs.indices.map { DialogMood.lineFace(laughs, it) })
        // the content's own wins
        assertEquals(Face.CURIOUS, DialogMood.lineFace(DialogRun.start(soup, "babica").said, 0))
    }

    @Test fun `the reactions' faces`() {
        val open = DialogRun.start(soup, "babica")
        assertEquals(Face.HAPPY, DialogMood.react(null, open, Mood.NONE, 0).face)
        // a wrong pick they react to: the reaction's own face, else confused
        assertEquals(Face.SURPRISED, DialogMood.react(open, open.choose(1), Mood.NONE, 0).face)
        val plain = soup.copy(lines = soup.lines.map { l -> l.copy(choices = l.choices.map { c -> c.copy(reply = c.reply?.copy(face = null)) }) })
        val o2 = DialogRun.start(plain, "babica")
        assertEquals(Face.CONFUSED, DialogMood.react(o2, o2.choose(1), Mood.NONE, 0).face)
        assertEquals(Pose.THINK, DialogMood.react(o2, o2.choose(1), Mood.NONE, 0).pose)
        // a wrong one without: worried, shrugging
        val worried = DialogMood.react(open, open.choose(2), Mood.NONE, 0)
        assertEquals(Pose.SAD to Face.WORRIED, worried.pose to worried.face)
        // a right one: a smile
        assertEquals(Face.HAPPY, DialogMood.react(open, open.choose(0), Mood.NONE, 0).face)
        // the end without a mistake: proud, a child laughs; with one, a smile
        val last = open.choose(0).next().choose(0)
        val end = last.next()
        assertEquals(DialogRun.Step.END, end.step)
        assertEquals(Pose.CHEER to Face.PROUD, DialogMood.react(last, end, Mood.NONE, 0).let { it.pose to it.face })
        assertEquals(Face.LAUGH, DialogMood.react(last, end, Mood.NONE, 0, child = true).face)
        val sloppy = open.choose(0).next().choose(1).choose(0)
        assertEquals(Face.HAPPY, DialogMood.react(sloppy, sloppy.next(), Mood.NONE, 0).face)
    }

    @Test fun `a face holds while its line is said and a while after, then eases back`() {
        val line = LineFace(Face.LAUGH, shownAt = 1_000, quietAt = 4_000)
        assertEquals(Face.LAUGH, Expression.held(line, 9_000, talking = true))
        assertEquals(Face.LAUGH, Expression.held(line, 4_000 + Expression.HOLD_MS - 1, talking = false))
        // through its softer face
        assertEquals(Face.HAPPY, Expression.held(line, 4_000 + Expression.HOLD_MS, talking = false))
        assertNull(Expression.held(line, 4_000 + Expression.HOLD_MS + Expression.SOFT_MS, talking = false))
        // a face with no softer one goes straight back
        assertNull(Expression.held(line.copy(face = Face.SAD), 4_000 + Expression.HOLD_MS, talking = false))
        assertNull(Expression.held(line.copy(face = null), 4_100, talking = true))
        // when it changes next by itself
        assertEquals(4_000 + Expression.HOLD_MS, Expression.next(line, 0, 5_000, talking = false))
        assertEquals(4_000 + Expression.HOLD_MS + Expression.SOFT_MS, Expression.next(line, 0, 4_000 + Expression.HOLD_MS, talking = false))
        assertNull(Expression.next(line, 0, 9_000, talking = false))
        assertEquals(6_000L, Expression.next(null, 6_000, 5_000, talking = false))
    }

    @Test fun `the line, the reaction and listening, in their order`() {
        val line = LineFace(Face.CURIOUS, 1_000, 2_000)
        // listening: attentive
        assertEquals(Face.NEUTRAL, Expression.face(line, Face.HAPPY, 2_500, moodOn = true, now = 2_600, talking = false, listening = true))
        // talking: the line's own, else the mood's
        assertEquals(Face.CURIOUS, Expression.face(line, Face.HAPPY, 2_500, true, 2_600, talking = true, listening = false))
        assertEquals(Face.HAPPY, Expression.face(line.copy(face = null), Face.HAPPY, 2_500, true, 2_600, talking = true, listening = false))
        // a reaction newer than the line heard: the reaction; a line heard after it: the line's
        assertEquals(Face.HAPPY, Expression.face(line, Face.HAPPY, 2_500, true, 2_600, talking = false, listening = false))
        assertEquals(Face.CURIOUS, Expression.face(line.copy(quietAt = 3_000), Face.HAPPY, 2_500, true, 3_100, talking = false, listening = false))
        // both over: at rest
        assertEquals(Face.NEUTRAL, Expression.face(line, Face.HAPPY, 2_500, moodOn = false, now = 9_000, talking = false, listening = false))
    }

    @Test fun `the tutor's face in a role-play, leniently`() {
        fun parse(data: String) = RoleplayReply.parse("Me veseli!", json.parseToJsonElement(data).jsonObject)
        assertEquals(Face.LAUGH, parse("""{"sl": "Me veseli!", "face": "laugh"}""").face)
        assertNull(parse("""{"sl": "Me veseli!", "face": "giggle"}""").face)
        assertNull(parse("""{"sl": "Me veseli!", "face": "neutral"}""").face)
        assertNull(parse("""{"sl": "Me veseli!"}""").face)
        // an older tutor sends none: the line has none
        val s = Scenario(id = "pekarna", title = "V pekarni", setting = "", role = "Prodajalka Ana, …", goals = listOf("pozdravi"), openerSl = "Dobro jutro!", openerEn = "Good morning!")
        var st = Talk.start(s, 0L)
        st = Talk.said(st, "Dober dan.", typed = true, now = 1L)
        st = Talk.replied(st, parse("""{"sl": "Izvolite?", "face": "curious"}"""))
        assertEquals(Face.CURIOUS, st.lines.last().face)
        // the opener smiles, a line without one rests, the last one once the scene is over smiles
        assertEquals(Face.HAPPY, Talk.faceOf(st, st.lines[0]))
        st = Talk.said(st, "Kruh, prosim.", typed = true, now = 2L)
        st = Talk.replied(st, parse("""{"sl": "Izvolite.", "end": true}"""))
        assertEquals(Face.HAPPY, Talk.faceOf(st, st.lines.last()))
        assertEquals(Face.CURIOUS, Talk.faceOf(st, st.lines[2]))
        assertEquals(Face.NEUTRAL, Talk.faceOf(st, st.lines[1]))
    }

    @Test fun `the talk's character listens, thinks, talks with their line's face and smiles at the end`() {
        val s = Scenario(id = "pekarna", title = "V pekarni", setting = "", role = "Prodajalka Ana, …", goals = listOf("pozdravi"), openerSl = "Dobro jutro!", openerEn = "Good morning!")
        val st = Talk.start(s, 0L)
        val line = LineFace(Face.SURPRISED, 0, 1_000)
        assertEquals(Pose.LISTEN to Face.NEUTRAL, TalkStageRules.look(st, line, 1_200, talking = false, listening = true))
        val waiting = Talk.said(st, "Kruh.", typed = true, now = 2L)
        assertEquals(Pose.THINK to Face.THINKING, TalkStageRules.look(waiting, line, 1_200, talking = false, listening = false))
        assertEquals(Pose.IDLE to Face.SURPRISED, TalkStageRules.look(st, line, 1_200, talking = true, listening = false))
        assertEquals(Pose.IDLE to Face.NEUTRAL, TalkStageRules.look(st, line, 60_000, talking = false, listening = false))
        assertEquals(Face.HAPPY, TalkStageRules.look(st.copy(sceneOver = true), line, 60_000, talking = false, listening = false).second)
        // the portrait's pose talks while they talk
        assertEquals(Pose.TALK, Look(Pose.IDLE, Face.HAPPY, talking = true).portraitPose)
        assertEquals(Pose.LISTEN, Look(Pose.LISTEN, Face.NEUTRAL, talking = true).portraitPose)
    }

    @Test fun `the talk's stage folds with the keyboard, on a short screen, with large fonts, and as chosen`() {
        assertTrue(TalkStageRules.shown(900, 1f, keyboard = false, chosen = null))
        assertFalse(TalkStageRules.shown(900, 1f, keyboard = true, chosen = null))
        assertFalse(TalkStageRules.shown(900, 1f, keyboard = true, chosen = true))
        // a phone turned (411 dp high), large fonts: folded, unless the learner opens it
        assertFalse(TalkStageRules.shown(411, 1f, keyboard = false, chosen = null))
        assertTrue(TalkStageRules.shown(411, 1f, keyboard = false, chosen = true))
        assertFalse(TalkStageRules.shown(900, 1.3f, keyboard = false, chosen = null))
        assertFalse(TalkStageRules.shown(900, 1f, keyboard = false, chosen = false))
        // Teo's tablet (2560 × 1600 at 2.0: 800 dp high in landscape, 1280 dp upright): shown both ways, bigger upright
        assertTrue(TalkStageRules.shown(800, 1f, keyboard = false, chosen = null))
        assertTrue(TalkStageRules.shown(1280, 1f, keyboard = false, chosen = null))
        assertEquals(TalkStageRules.TALL_DP to TalkStageRules.PORTRAIT_DP, TalkStageRules.size(800))
        assertEquals(TalkStageRules.BIG_TALL_DP to TalkStageRules.BIG_PORTRAIT_DP, TalkStageRules.size(1280))
        assertEquals(TalkStageRules.TALL_DP to TalkStageRules.PORTRAIT_DP, TalkStageRules.size(914)) // a phone
    }

    @Test fun `TalkBack hears who and how`() {
        val before = si.lanisce.lani.l10n.L10n.pair
        try {
            si.lanisce.lani.l10n.L10n.pair = si.lanisce.lani.l10n.LangPair(si.lanisce.lani.l10n.Lang.SL, si.lanisce.lani.l10n.Lang.EN)
            assertEquals("Mojca, se smeji · laughs", described("Mojca", Pose.CHEER, Face.LAUGH))
            assertEquals("Mojca", described("Mojca", Pose.IDLE, Face.NEUTRAL))
            // the pose's word when the face says nothing
            assertEquals("Mojca, ${poseLabel(Pose.LISTEN)}", described("Mojca", Pose.LISTEN, Face.NEUTRAL))
            for (f in Face.written) assertTrue(f.key, faceLabel(f).contains(" · "))
            // every face in every table
            for (base in listOf(si.lanisce.lani.l10n.Lang.DE, si.lanisce.lani.l10n.Lang.IT)) {
                si.lanisce.lani.l10n.L10n.pair = si.lanisce.lani.l10n.LangPair(si.lanisce.lani.l10n.Lang.SL, base)
                for (f in Face.written) assertFalse("${f.key} in $base", faceLabel(f).substringAfter(" · ") == si.lanisce.lani.l10n.L10n.text(si.lanisce.lani.l10n.Lang.EN, "face.${f.key}"))
            }
        } finally {
            si.lanisce.lani.l10n.L10n.pair = before
        }
    }
}
