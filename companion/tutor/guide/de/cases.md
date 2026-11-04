Case trainer ("🧩 Fälle · Cases", German): short sessions (10 items, 8 for a child) on the four cases, the kinds
interleaved, one at a time with feedback at once. "Welcher Fall? · Which case?": a real sentence, its noun phrase framed
(the article, the adjectives, the noun: German shows the case in the article), the learner picks the case by its question
(wer? was? Nominativ · wen? was? Akkusativ · wem? Dativ · wessen? Genitiv), then "Warum? · Why?": taps the word that
decides it (mit, für, wegen, in + a verb of motion or of rest, helfen, geben, the noun a genitive belongs to …) or says
nothing decides it (the subject, the object); then the rule's line and its grammar page. "Wo oder wohin?": the two-way
prepositions (in der Hütte, in die Hütte; with aus or von: woher?), zu, bei or nach, over the village's places.
"⚡ Fall-Sturm · Case storm": short phrases sorted into the four cases against the clock (60 s). "Dekliniere! · Decline
it": the learner's own nouns with an article, a possessive or kein, and an adjective, typed into a frame with a known
trigger ("Heute helfe ich ___ (mein Opa)" → meinem Opa), graded as any typed answer. Items are picked towards the weak
cases and rules (the learner's mistakes count from the start); a missed item comes back (the same day, then 1, 3, 7 and
16 days). The rule ids are the German grammar pages: faelle (the subject, what something is), akkusativ, dativ, genitiv,
praepositionen-akkusativ, praepositionen-dativ, praepositionen-genitiv, wechselpraepositionen, pronomen-dativ,
reflexive-verben, verben-praepositionen, adjektive-praepositionen, relativsatz, uhrzeit (am Montag, im Mai). A form people
say is right and never a mistake: wegen dem Regen, dem Stefan sein Hund, den Mensch (Austria's talk).
cases_done: a session the app played, ALREADY persisted by the bridge as grammar practice (and writing for the typed
ones), its wrong answers in the mistakes database under the learner's own pattern ids where one fits (else
cases_<case>_<rule>): don't persist it again. get_cases shows the mastery per case and rule, what comes back, the bank and
the assignment. assign_cases {focus: a case id (nominativ, akkusativ, dativ, genitiv) or a rule id (a page above), note}
focuses the next session. publish_case_items adds items in the bank's German format (de: the sentence; sl, en, it its
meaning; word and phrase: the noun's token and the framed noun phrase [from, to]; lemma; gender: the noun's; case; number
sg|pl; why: a rule key; trigger or role), checked as the bank is (the article, the adjectives and the noun against the
case, the trigger against its rule, the pages). Machine-written: never call a spoken form wrong.
