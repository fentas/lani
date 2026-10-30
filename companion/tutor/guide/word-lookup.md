Word lookup: in the app's dialogs Jan taps a word and sees its base form, meaning and grammar (the bridge looks it up
in the word packs, your glosses, the villagers, Wiktionary and a supplement), and can add it to their words. Meanings
are in Jan's base language when the dictionary has one there (from Wiktionary: its own, a translation table, or
through English); else the card shows the English and says so. words_added lists the words Jan added that way:
ALREADY persisted as vocabulary review items, don't persist them again; reply only if you have something useful to
say. A chat whose JSON block has lookup {word, line, en, language?} is a word the dictionary didn't know: explain it
briefly with reply (base form, meaning, what this form is in the line) AND call publish_gloss with the form as it
appears, its lemma, pos, 1-3 short glosses (in the language you explain in) and the grammar, so the next lookup finds
it. gloss_gaps lists words whose lookup had no meaning in Jan's base, with the English: for each, call publish_gloss
{form, lemma, pos, gloss: 1-3 short meanings in that base, gloss_lang, language when the note names one}; the app
shows them flagged machine-written, and the card open on the word updates. No reply needed for gloss_gaps.
gloss_request lists words Jan tapped "Add to my words" on that the dictionary doesn't know, with the line each was met
in (JSON block gloss_requests): for each, call publish_gloss {form exactly as listed, lemma, pos, a noun's gender,
gloss: 1-3 short meanings in the note's gloss_lang (the one the line means first), gloss_lang, grammar of this form,
language when the note names one}; the line becomes the word's example. The bridge then adds the word to Jan's words itself (don't persist it) and the open card fills in: no
reply needed. A typo of another word: gloss that word (its lemma) with a note; a name: pos "name" (not added). Ask Jan
in a reply only if the word can't be explained at all.
publish_gloss's gloss_lang is the language your glosses are in (default: Jan's base); language is the word's (default:
the village's; on a visit, the town's, as the note or data.lookup.language says).
The case lens (Slovene): a word card of a Slovene word in its line shows its case and number ("rodilnik, ed. · koga?
česa?"), the word that decided it (a preposition, a negation, a number or quantity, a verb or word of feeling, the
subject's verb), the rule's grammar page, and every reading when the line can't tell; readings, stories and dialogs can
underline each noun in its case's colour ("Skloni · Cases"). The lens is a rule table (companion/grammar/sl/data/
case-triggers.json), never a guess: point Jan to it when a case trips them ("tap the word: the card says what decides
it"). A word whose case its line decides, looked up that way, counts as seen for its rule (the grammar page's id): met,
not practised; the app counts it in the case trainer's progress (get_village's cases.seen_in_lens), weighed lightly.
