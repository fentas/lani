# The tutor for every learner

Teo's tutor ran on instructions written for Slovene: `core-prompt.md`'s "Correct Slovene", a header that said "the paragraphs below
were written for a Slovene learner: apply them", skills that said Slovene where they meant the village's language. Now what is
general says so, each language has its own part, and Jan's tutor is what it was (the comparison below).

## What a tutor reads now

**`companion/tutor/languages/`** is hand-written and is the one source for both kinds of tutor:

| File | What it says |
|---|---|
| `general.md` | What holds in every language: grade by the exercise's accepted answers; a spoken or regional form is never a mistake; speech-to-text spellings; **what you can't check, you don't know** |
| `sl.md`, `it.md`, `de.md`, `en.md` | `## Correct X` (register, spoken forms, what a mistake is); `## A child learning X`; `## Writing X` (for whoever writes content) |
| `child.md` | A child's rules for the in-app tutor: the bridge's `CHILD_INSTRUCTIONS` without its sentence on the terminal (the smoke holds the two together) |

**The tutor on a node (Claude Code):** `learnerInstructions` puts `general.md`, what to check a form with in this session, the language's
`Correct` and `Writing` sections and a child's case before the features' paragraphs. Only the default learner of a Slovene village
goes without. The paragraphs are the learner's own: `src/tutor-languages.ts` (`forVillage`, `without`) renders them for the village's
language. The ones that hold only in Slovene say so instead of describing what isn't there: counting cards, forms cards, the case trainer,
the case lens. Nina and Luka go; "Moja vas", Stari Janez and the fant/dekle rule become what that village has. A default learner of another language
(`lani-setup`, or a culture in `lani.env`) was told nothing of their language beyond the base and now reads as a profile does.

**The tutor in the app:** the prefix is the core prompt, then `general.md`, then the language's `Correct` section; for a child `child.md` and
the child's case. The studio's prompt is followed by `Correct` and `Writing`. `LiveTutor` keeps a prefix per `PromptLearner` (language, child).
With no view model up, the background check reads them from `LangSetting` and `Prefs`.
The `guide` tool reads `guide/<language>/<id>.md` when the village has its own copy of a topic (`tutor-export` writes them: the same
bridge built for a learner of each language, 17 topics each), with `{base}` and `{base_code}` filled in with the learner's.

**Before this, a child in the app had no child's rules at all:** `tutor/` had no mention of one. `languages/child.md` and `AppViewModel.child` close that.

**Checking a form.** `.claude/settings.json` allows web lookups on fran.si only, and that wasn't changed. So the texts say: Slovene has an outside
dictionary; for the others you have the word packs, the grammar book and the lexicon's supplement, and what those don't settle you don't know. Don't
call it wrong, don't count it, say you aren't sure, give the form you are sure of, and leave it to a native speaker. `general.md`, `CLAUDE.md`,
`lani-studio`, `lani-fix` and `dictionary_lookup`'s description all say this.

## Publishing in each language

Every `publish_*` tool was tried on real bridges with a synthetic learner: an Italian child from German, a German learner from English, an English learner
from Slovene (`test/smoke/publish-languages.ts`, in the smoke): module, pack, grammar page, card, homework, scenario, scene, dialog variant, story,
reading, villager, arrival, gloss, sentence note. All took their language's content, and the app's routes served the pack and the gloss back.

**One real fault found:** `publish_pack`, `publish_scene`, `publish_story` and `publish_arrival` took a left-out `language` as Slovene (the formats' default), so in another
village they were refused or filed wrong. It's the village's now, as `publish_scenario`, `publish_grammar` and `publish_worksheet` had it. A learner of Slovene sees no change.

`publish_case_items`, `assign_cases` and `get_cases` stay Slovene's and refuse elsewhere; the guide says so. `card.py` and `read-db.py` were run on a synthetic learner of Italian and work.

## Jan's tutor, before and after

Compared with `test/tutor-text.ts` (a synthetic Jan as `tutor-export` builds them, run on 45924165 and on this branch) and the prompts' files.

| What Jan's tutor reads | Difference |
|---|---|
| The channel's rules | **None** (byte-identical) |
| `channel_guide`, the whole guide | **None** (byte-identical) |
| Tool descriptions | Three: `publish_card` ("the target language's words in bold" for "the Slovene"), `voice_profiles` ("a sample of their lines"), `voice_redesign` ("the clearest in the village's language"). The same thing for a Slovene village |
| `lani-studio`, its `module-spec.md` | Wording: "the village's language" where it said Slovene and meant that; a paragraph saying which language and what's Slovene's only. Every Slovene rule stays, labelled |
| `lani-fix` | Its last rule goes on: in another language, what can't be settled isn't counted either. Nothing changes for Slovene |
| `lani-feedback-formatter`, its template | The category labels say ti/vi, tu/Lei, du/Sie for Dutch's u/je, and one bullet: a spoken form gets no ❌. That was the rule already |
| `CLAUDE.md` | A new section on checking another language; fran.si's stays as it was |
| The in-app core prompt | `Correct Slovene` now comes from `sl.md`, after `general.md`. Every statement stays. **Moved to general, examples kept in Slovene's part:** accepted answers, speech-to-text, spoken forms from home. **New in general:** "what you can't check, you don't know", which goes a step beyond rules 5 and 13. Rule 5 points to the sections; rules 13 and 15 label their examples Slovene's |
| The in-app studio prompt | "Correct Slovene first" says the general rule; its Slovene is in `## Writing Slovene`. **New for it:** `## Correct Slovene`, the rules the chat already had (the cases by number, the locative, the dual). None contradicts it |

The in-app tutor's prefix changes, so each day's thread restarts once as text after the update, as with any prompt change. Older apps have theirs bundled.
No tutor was restarted: that's the gate's.

## What's left

- **The native check** below. Nothing in the three new parts has been seen by a native speaker.
- **In-app tool descriptions are one text for all:** they're exported from Jan's bridge, and some keep a Slovene example ("e.g. prinesel"). Those examples are labelled as examples.
- **The generic practice skills** (`lani-learn`, `lani-writing`, `lani-speaking`, `lani-review`, `lani-progress`, `lani-db-updater`, `lani-session-analyzer`) still have the Dutch examples of the kit they came from. They name no rule of a language and are no worse for an Italian learner than for Jan, so they stayed.
- **Jan's channel guide has no `Correct Slovene`**, by the brief: it stays as it was. Jan's rules come from `CLAUDE.md`, the skills and the session's memory. It takes one condition in `build.ts` to give it to Jan.
- **The numbers module:** the guide's text for another language says "where this village has it", so it holds once `practice-it` turns it on. Merging `practice-it` needs no change here.
- A label quoted in a paragraph stays the app's Slovene one; the header says so. Rendering them from each string table would be a larger job.

## Native check

Machine-written, by language. Each line is a claim the tutor will now act on.

**Italian** (`it.md`). Sources: Crusca on [a me mi](https://accademiadellacrusca.it/it/consulenza/a-me-mi-e-una-forma-corretta/61) and on [gli](https://accademiadellacrusca.it/it/consulenza/uso-di-gli-per-a-lui-a-loro-e-a-lei/102); Treccani on [gli o loro](https://www.treccani.it/enciclopedia/gli-o-loro_(La-grammatica-italiana)/), [gli o le](https://www.treccani.it/enciclopedia/gli-o-le_(La-grammatica-italiana)/), [redundant pronouns](https://www.treccani.it/enciclopedia/pronomi-personali-ridondanti-prontuario_(Enciclopedia-dell'Italiano)/), [colloquial forms](https://www.treccani.it/enciclopedia/forme-prontuario-colloquiali_(Enciclopedia-dell'Italiano)/) and [Lingua d'oggi](https://www.treccani.it/enciclopedia/lingua-d-oggi_(Enciclopedia-dell'Italiano)/); [mandi](https://it.wikipedia.org/wiki/Mandi_(linguistica)).
- Sourced: *lui*, *lei*, *loro* as subjects; *gli* for *a loro* accepted; *gli* for *le* spoken only; *a me mi*; *c'ho*; the indicative after *penso che*; the imperfect in hypotheticals; the passato remoto absent in the north; *mandi*.
- **Not sourced, from what I know:** *voi* to one person as old-fashioned or southern; *mia mamma* and *la mia mamma* alike; *se avrei* as a real mistake; the article and auxiliary examples; a child's *tu* to a grown-up never counted (the Friuli scenarios already say "may say tu or Lei").

**German** (`de.md`). Sources: Duden on [sitzen](https://www.duden.de/rechtschreibung/sitzen) and [wegen](https://www.duden.de/rechtschreibung/wegen_infolge_bezueglich); grammis on [wegen](https://grammis.ids-mannheim.de/fragen/67); the Variantengrammatik on [Präteritum and Perfekt](https://mediawiki.ids-mannheim.de/VarGra/index.php/Pr%C3%A4teritum_vs._Perfekt); Wikipedia on [Austrian German](https://de.wikipedia.org/wiki/%C3%96sterreichisches_Deutsch).
- Sourced: Austrian German as a standard variety; *ich bin gesessen*; the Präteritum rare in Austrian speech; *wegen dem* colloquial.
- **Not sourced:** the word pairs (*Jänner*, *heuer*, *Erdäpfel*, *Semmel*: the Kärnten content has them, my search didn't confirm them); *weil* with the verb second; *Griaß di*, *Pfiat di*, *ned*; what's 🟢 minor.

**English** (`en.md`). Sources: Cambridge on [less and fewer](https://dictionaryblog.cambridge.org/2014/05/28/less-or-fewer/); the British Council on [contractions](https://learnenglishteens.britishcouncil.org/exams/listening-exams/contractions); [alt.usage.english](https://alt-usage-english.org/intro_d.html) on *gotten*; Wikipedia on [singular they](https://en.wikipedia.org/wiki/Singular_they).
- **Not sourced:** *who* for *whom*; *me and Tom went*; the split infinitive; *realise* and *realize*; the Cumbrian words (the Lake District content has them); *Miss* for a teacher.

**Slovene** (`sl.md`): the sections were there and were checked before, moved as they stood. **New and unchecked:** `## A child learning Slovene`.

**How far the sources go:** most were read as a search tool's summary of the page. Duden's *sitzen* and Treccani's two encyclopedia pages were opened. Cambridge's grammar pages refused the fetch.

## Voice

No voiced text changed: **0 characters** to build. The language parts and the paragraphs are instructions, never spoken.

## How to compare again

`cd companion/bridge && bun test/tutor-text.ts <jan|teo|kaernten|lakeland|friuli-default> <dir>` writes the rules, the guide and the tools for that synthetic learner; `diff -r` two runs.
