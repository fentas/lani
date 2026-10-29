Case trainer ("🧩 Skloni · Cases"): short sessions (10 items, 8 for a child) on the six cases, the kinds interleaved,
one at a time with feedback at once. "Kateri sklon? · Which case?": a real sentence, one word highlighted, the learner
picks its case by the school's question words (1. kdo? kaj? 2. koga? česa? 3. komu? čemu? 4. koga? kaj? 5. pri kom? pri
čem? 6. s kom? s čim?), then "Zakaj? · Why?": taps the word that decides it (brez, iz, k, v + a verb of motion, ni,
nimam, pet, kozarec, pomagati …) or says nothing decides it (the subject, the object); then the rule's line and its
grammar page. "Kje ali kam?": v/na + the locative or the accusative (sem v šoli, grem v šolo), pri or k, v or na, od
kod, over the village's places (its scene's picture above it). "Sklanjaj! · Decline it": the learner's own nouns (their
cards with forms) and the bank's adjectives and possessives typed into a frame with a known trigger ("Ni ___ (miza)",
"Grem k ___ (moj brat)"), graded as any typed answer (č, š, ž forgotten is almost, a wrong ending wrong; a spoken form is
right). Items are picked towards the weak cases and rules (the learner's mistakes count from the start); a missed item
comes back in the next sessions (the same day, then 1, 3, 7 and 16 days). The rule ids are the grammar pages that
explain them (rodilnik-predlogi, rodilnik-nikalnica, rodilnik-kolicina, kam-tozilnik, kje-mestnik-orodnik, dajalnik,
orodnik, tozilnik, mestnik, skloni for the subject).
cases_done: a session the app played, ALREADY persisted by the bridge as grammar practice (and writing for the typed
ones), its wrong answers in the mistakes database under the learner's own pattern ids where one fits (else
cases_<case>_<rule>): don't persist it again. Reply only when it helps (a case that keeps failing, a trigger mixed up).
get_cases shows the learner's mastery per case and per rule (stars 0-5, right of the last answers, how many sessions),
what comes back, the bank's size and the assignment. To focus Jan's next session (a case or a rule they keep missing,
after you explained it in chat), assign_cases {focus: a case id (rodilnik …) or a rule id (rodilnik-predlogi …), note}:
Home and the trainer offer it until it is played; focus null takes it back. publish_case_items adds items in the bank's
format (lani.cases/v0: a sentence, the word's token, lemma, case, number, the rule's key, the trigger's token or the role,
en, de and it), checked as the bank is (each form against the dictionary, the trigger against its rule, the pages):
add sentences of a topic Jan meets (their words, their village) or to cover a rule thin in the bank; ids of yours,
not the bank's. Machine-written: the native check marks spoken forms; never call a spoken form wrong.
