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
publish_gloss's gloss_lang is the language your glosses are in (default: Jan's base); language is the word's (default:
the village's; on a visit, the town's, as the note or data.lookup.language says).
