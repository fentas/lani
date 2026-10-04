package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.GameState
import java.time.LocalDate

/**
 * Stage directions (companion/SCENES.md, "Stage directions"): where a dialog's cues put people as it goes, what a day keeps,
 * how they get there on screen, and which place of a tap turn a tap in the picture is.
 */
class StageTest {
    private val hide = Dialog(
        id = "skrivalnice",
        lines = listOf(
            DialogLine(who = "zala", sl = "Ti mižiš, jaz se skrijem!", en = "You close your eyes, I'll hide!"),
            DialogLine(choices = listOf(
                DialogChoice("Deset! Grem iskat!", "Ten! Here I come!", ok = true,
                    reply = DialogReply("Skrila sem se!", "I've hidden!", act = mapOf("zala" to ActCue("behind-door", "hide")))),
                DialogChoice("Deset! Greš iskat!", "Ten! You're coming!", why = "You seek: grem.",
                    reply = DialogReply("Jaz? Ne, ti iščeš!", "Me? No, you seek!")),
            )),
            DialogLine(choices = listOf(
                DialogChoice("Za vrati si!", "You're behind the door!", ok = true, tap = "behind-door",
                    reply = DialogReply("Našel si me!", "You found me!", act = mapOf("zala" to ActCue(to = "door")))),
                DialogChoice("Pod mizo si!", "You're under the table!", why = "Look for a clue.", tap = "under-table",
                    reply = DialogReply("Tu me ni!", "I'm not here!", act = mapOf("zala" to ActCue(pose = "peek")))),
                DialogChoice("Za pečjo si!", "You're behind the stove!", why = "Look for a clue.", tap = "stove",
                    reply = DialogReply("Mrzlo, mrzlo!", "Cold, cold!")),
            )),
            DialogLine(who = "zala", sl = "Še enkrat!", en = "Once more!"),
        ),
    )
    private val base = mapOf("zala" to Placement.at("stove"), "janez" to Placement.at("table"))

    @Test fun `the cues move people as the dialog goes, the reply to the right pick, not the reaction`() {
        assertEquals(emptyMap<String, Placement>(), hide.actAt(1, emptyList(), base)) // the turn is up, nobody moved yet
        assertEquals(mapOf("zala" to Placement("behind-door", "hide")), hide.actAt(2, listOf(0), base))
        // found: she comes out and stands at the door; Janez, never named, isn't among the moved
        assertEquals(mapOf("zala" to Placement("door", "stand")), hide.actAt(3, listOf(0, 0), base))
        // a pose alone is taken where they are; someone the scene lacks is let go
        val peek = Dialog("x", listOf(DialogLine(who = "zala", sl = "A", en = "a", act = mapOf("zala" to ActCue(pose = "peek"), "tine" to ActCue("door")))))
        assertEquals(mapOf("zala" to Placement("stove", "peek")), peek.actAt(1, emptyList(), base))
        assertTrue(hide.hasAct)
        assertFalse(hide.actKept)
        assertTrue(hide.copy(actStays = true).actKept)
    }

    @Test fun `a placement after a cue, a spot alone stands there, a pose alone is taken where one is`() {
        val hidden = Placement("behind-door", "hide")
        assertEquals(Placement("door", "stand"), hidden + ActCue(to = "door"))
        assertEquals(Placement("behind-door", "peek"), hidden + ActCue(pose = "peek"))
        assertEquals(Placement("bench", "sit"), hidden + ActCue("bench", "sit"))
        assertEquals(Stance.STAND, Placement("door", "dance").stance) // a pose a later app knows: standing here
    }

    @Test fun `the format reads act, act_stays and tap, and a file without them as before`() {
        val d = json.decodeFromString(Dialog.serializer(), """
            {"id": "d", "act_stays": true, "lines": [
              {"who": "luka", "sl": "Hvala!", "en": "Thanks!", "act": {"luka": {"to": "lantern", "pose": "sit"}}},
              {"choices": [{"sl": "Za vrati si!", "en": "Behind the door!", "ok": true, "tap": "behind-door",
                            "reply": {"sl": "Ja!", "en": "Yes!", "act": {"zala": {"to": "door"}}}},
                           {"sl": "Pod mizo si!", "en": "Under the table!", "tap": "under-table", "why": "No."}]}
            ]}""".trimIndent())
        assertEquals(true, d.actStays)
        assertEquals(ActCue("lantern", "sit"), d.lines[0].act["luka"])
        assertEquals("behind-door", d.lines[1].choices[0].tap)
        assertEquals(ActCue(to = "door"), d.lines[1].choices[0].reply?.act?.get("zala"))
        assertTrue(d.lines[1].tapTurn)
        val old = json.decodeFromString(Dialog.serializer(), """{"id": "d", "lines": [{"choices": [{"sl": "A", "ok": true}, {"sl": "B"}]}]}""")
        assertFalse(old.lines[0].tapTurn)
        assertNull(old.actStays)
    }

    @Test fun `what a dialog keeps stays in its scene until midnight`() {
        val today = LocalDate.of(2026, 9, 28)
        val stays = hide.copy(actStays = true)
        val s = DayAct.keep(GameState(), "v-hisi", stays, mapOf("zala" to Placement("bench", "sit")), today)
        assertEquals(mapOf("zala" to Placement("bench", "sit")), DayAct.of(s, today, "v-hisi"))
        assertEquals(emptyMap<String, Placement>(), DayAct.of(s, today.plusDays(1), "v-hisi"))
        assertEquals(emptyMap<String, Placement>(), DayAct.of(s, today, "kuhinja"))
        // a dialog that doesn't say so keeps nothing
        assertSame(s, DayAct.keep(s, "v-hisi", hide, mapOf("zala" to Placement("door")), today))
        // the state round-trips (it syncs with the village)
        val back = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), s))
        assertEquals(s.sceneAct, back.sceneAct)
    }

    @Test fun `the base is everyone's spot, today's kept placements over it, as far as the art's stage has them`() {
        val scene = SceneSpec(
            id = "v-hisi", title = "V hiši", art = "livingroom", from = listOf("house"),
            people = listOf(ScenePerson("janez", "Janez", art = "grandpa", slot = "table"), ScenePerson("zala", "Zala", art = "child2", slot = "stove")),
        )
        assertEquals(base, Stage.base(scene, emptyMap()))
        assertEquals(base + ("zala" to Placement("bench", "sit")), Stage.base(scene, mapOf("zala" to Placement("bench", "sit"), "tine" to Placement("door"))))
        // a spot the stage lacks, a pose its spot hasn't: not shown
        assertEquals(mapOf("zala" to Placement("door")), Stage.shown("livingroom", mapOf("zala" to Placement("door"), "janez" to Placement("attic"), "x" to Placement("bench", "hide"))))
        assertEquals(emptyMap<String, Placement>(), Stage.base(scene.copy(art = "forest"), emptyMap())) // no stage there
    }

    @Test fun `on screen a new placement starts a move from where they were headed, over within MOVE_S`() {
        val people = listOf(PersonInScene("zala", "child2", "stove"), PersonInScene("janez", "grandpa", "table"))
        val e0 = StageEase.still(base)
        assertFalse(e0.moving(10.0))
        assertEquals(people, e0.apply(people, 10.0))
        val e1 = e0.toward(base + ("zala" to Placement("behind-door", "hide")), 10.0)
        assertTrue(e1.moving(10.5))
        val mid = e1.apply(people, 10.5).first()
        assertEquals("behind-door", mid.slot)
        assertEquals(Stance.HIDE, mid.stance)
        assertEquals("stove", mid.from)
        assertEquals(0.5f, mid.moveAge, 1e-4f)
        assertFalse(e1.moving(10.01 + Stage.MOVE_S))
        assertNull(e1.apply(people, 10.01 + Stage.MOVE_S).first().from)
        // the same target again keeps the move going; back home (the dialog closed): a move from the hiding place
        assertSame(e1, e1.toward(base + ("zala" to Placement("behind-door", "hide")), 11.0))
        val back = e1.toward(base, 12.0).apply(people, 12.2).first()
        assertEquals(listOf("stove", "behind-door"), listOf(back.slot, back.from))
        assertEquals(Stance.HIDE, back.fromStance)
        // someone asleep stays in their bed
        val asleep = listOf(PersonInScene("zala", "child2", "bed", pose = Pose.SLEEP))
        assertEquals(asleep, e1.apply(asleep, 10.5))
    }

    @Test fun `a tap in the picture is the place a choice stands for, the person there, the thing that hides it, the spot's area`() {
        val taps = listOf("behind-door", "under-table", "stove")
        val spotOf = { id: String -> if (id == "zala") "behind-door" else "table" }
        assertEquals("behind-door", Stage.tapped("livingroom", SceneTarget.Thing("door"), null, taps, spotOf)) // the door hides the spot
        assertEquals("under-table", Stage.tapped("livingroom", SceneTarget.Thing("table"), null, taps, spotOf))
        assertEquals("stove", Stage.tapped("livingroom", SceneTarget.Thing("stove"), null, taps, spotOf)) // a thing itself
        assertEquals("behind-door", Stage.tapped("livingroom", SceneTarget.Person("zala"), null, taps, spotOf)) // peeking out there
        assertEquals("under-table", Stage.tapped("livingroom", SceneTarget.Thing("dog"), "under-table", taps, spotOf)) // the spot's area
        assertNull(Stage.tapped("livingroom", SceneTarget.Thing("clock"), null, taps, spotOf))
        assertNull(Stage.tapped("livingroom", SceneTarget.Person("janez"), null, taps, spotOf))
        assertEquals("zala", Stage.tapped("livingroom", SceneTarget.Person("zala"), null, listOf("zala", "janez"), spotOf)) // a person
        // the things a tap turn needs to be tappable: the ones among its places, and what hides its spots
        assertEquals(setOf("stove", "door", "table"), Stage.tapSlots("livingroom", taps))
    }

    @Test fun `every art's stage has its person spots, standing, and the poses the app knows`() {
        for ((art, spots) in SceneArt.stage) {
            val names = spots.map { it.name }
            assertEquals("$art: a spot once", names.toSet().size, names.size)
            for (slot in SceneArt.personSlots.getValue(art)) assertTrue("$art: its person spot $slot is a spot, standing", spots.any { it.name == slot && "stand" in it.poses })
            for (s in spots) {
                assertTrue("$art/${s.name}: poses", s.poses.isNotEmpty() && s.poses.all { Stance.of(it) != null })
                s.cover?.let { assertTrue("$art/${s.name}: its cover $it is a slot", it in SceneArt.objects.getValue(art)) }
            }
        }
    }
}
