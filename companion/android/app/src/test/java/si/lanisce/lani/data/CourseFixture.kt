package si.lanisce.lani.data

/**
 * A small course as GET /course serves it (companion/COURSE.md): A1 with two written units (one of each of the school's
 * exercise types) and a planned one, A2 with one unit, B1 and B2 empty; the first unit with an intro and the tutor's
 * homework. The tests of the course's parsing, the sheets and the school's gates share it.
 */
internal object CourseFixture {
    private fun unit(id: String, level: String, pass: Int = 80, intro: Boolean = false, homework: Boolean = false) = """
        {
          "schema": "lani.unit/v0", "id": "$id", "language": "sl", "level": "$level", "emoji": "🟰",
          "title": { "sl": "Enota $id", "en": "Unit $id", "de": "Einheit $id", "it": "Unità $id" },
          "goal": { "en": "You can say who is where." },
          "grammar": ["biti", "zanikanje"],
          "words": ["doma", "kuhinja"],
          ${if (intro) """
          "intro": {
            "table": { "head": [{ "sl": "oseba", "en": "who" }, { "sl": "biti", "en": "to be" }] },
            "steps": [
              { "say": { "en": "One person: **sem, si, je**." }, "examples": [{ "sl": "Jaz sem doma.", "en": "I'm at home." }],
                "rows": [["jaz", "sem"], ["ti", "si"], ["on, ona, ono", "je"]],
                "ask": { "type": "choice", "prompt": "Babica ___ v kuhinji.", "options": ["sem", "si", "je"], "answer": 2, "explain": "ona: je" } },
              { "say": { "en": "Three or more: **smo, ste, so**." }, "rows": [["mi", "smo"], ["vi", "ste"], ["oni", "so"]],
                "ask": { "type": "cloze", "text": "Otroci ___ na vrtu.", "accept": ["so"] } },
              { "say": { "en": "No question here." }, "rows": [["too", "long", "row"]] }
            ]
          },""" else ""}
          "worksheets": [
            { "id": "$id-1", "title": { "sl": "List 1", "en": "Sheet 1" }, "exercises": [
              { "type": "sort", "grammar": "spol-samostalnikov", "instruction": "Razvrsti", "columns": [
                { "label": "moški", "words": ["kruh", "stol"] }, { "label": "ženski", "words": ["miza", "hiša"] } ] },
              { "type": "table", "grammar": "biti", "lemma": "biti", "columns": ["ednina", "množina"], "rows": [
                { "label": "1. oseba", "cells": ["sem", { "accept": ["smo"], "form": "pres.1pl" }] },
                { "label": "3. oseba", "cells": [{ "accept": ["je"], "form": "pres.3sg", "hint": "on, ona" }, { "accept": ["so"], "form": "pres.3pl" }] } ] }
            ] },
            { "id": "$id-2", "title": { "sl": "List 2", "en": "Sheet 2" }, "exercises": [
              { "type": "gaptext", "grammar": "biti", "title": "Pri babici", "text": "Danes ___ pri babici. Babica ___ v kuhinji. Otroci ___ na vrtu.",
                "gaps": [{ "accept": ["sem"] }, { "accept": ["je"], "hint": "ona" }, { "accept": ["so"], "grammar": "mnozina" }],
                "bank": true, "distractors": ["si"], "translation": "Today I'm at granny's." },
              { "type": "transform", "grammar": "zanikanje", "instruction": "Zanikaj", "cue": "ne", "items": [
                { "from": "Sem doma.", "accept": ["Nisem doma."] }, { "from": "Babica je v kuhinji.", "accept": ["Babica ni v kuhinji."], "lemma": "biti", "form": "neg.3sg" } ] },
              { "type": "hologram", "prompt": "a type of a newer app" }
            ] }
          ],
          "test": { "pass": $pass, "exercises": [
            { "type": "cloze", "grammar": "biti", "text": "Jaz ___ doma.", "accept": ["sem"] },
            { "type": "transform", "instruction": "Zanikaj", "items": [{ "from": "Si doma.", "accept": ["Nisi doma."] }, { "from": "So tu.", "accept": ["Niso tu."] }] }
          ] },
          ${if (homework) """"homework": [{ "id": "$id-domaca", "title": { "sl": "Domača", "en": "Homework" }, "source": "tutor", "published_at": 1760000000000.5,
            "exercises": [{ "type": "choice", "prompt": "Mi ___ doma.", "options": ["smo", "so"], "answer": 0 }] }],""" else ""}
          "machine_written": true, "review": "machine-written, not reviewed by a native speaker"
        }
    """.trimIndent()

    val served: String = """
        {
          "schema": "lani.course/v0", "language": "sl",
          "levels": [
            { "level": "A1", "units": [
              { "id": "biti", "title": { "sl": "Biti", "en": "To be" } },
              { "id": "spol", "title": { "sl": "Spol", "en": "Gender" } },
              { "id": "osebni-zaimki", "title": { "sl": "Jaz, ti", "en": "I, you" }, "planned": true, "grammar": ["ti-vi"] }
            ] },
            { "level": "A2", "units": [{ "id": "dajalnik", "title": { "sl": "Dajalnik", "en": "The dative" } }] },
            { "level": "B1", "units": [] },
            { "level": "B2", "units": [] }
          ],
          "units": [${unit("biti", "A1", intro = true, homework = true)}, ${unit("spol", "A1", pass = 50)}, ${unit("dajalnik", "A2")}, ${unit("extra", "A1")}],
          "machine_written": true
        }
    """.trimIndent()

    val course: Course get() = Courses.parse(served)!!
}
