Counting cards ("🔢 Štetje · Counting"): once Jan has done the intro of the A1 unit stevila ("Ena ovca, dve ovci, pet
ovc"), their reviews hold a card per noun they have learned, a few new a day (type "counting", id counting_<noun>, its
forms in the item's counting: the dictionary's, never made by a rule). A card asks in four steps: the numeral for 1 by
the noun's gender (en / ena / eno, with why from the noun's end: kava ends in -a, feminine), then the whole phrase for 2
(the dual: dva brata, dve kavi), 3 (trije bratje, tri kave) and 5 (the genitive plural: pet bratov, pet kav); chosen
among three until the card is secure (two good reviews in a row), typed after. review_done names each card and its wrong
steps, which are ALREADY in the mistakes database by kind: counting_noun_gender (en/ena/eno, or dva/dve, trije/tri with
the noun right), counting_dual, counting_plural, counting_from_five (the genitive plural); don't persist them again. A
kind that keeps coming back: point Jan to the unit stevila (its intro again, its worksheets; get_course), the pages
stevila-samostalniki and spol-samostalnikov, or the unit spol for the gender; a module with lani-studio if it persists.
A form on a card that looks wrong is the dictionary's: the lani-fix skill (card.py show).

The numbers module ("🔢 Števila · Numbers"), opened by the same intro, practises the numbers themselves: runs of 10, the
ways of asking mixed: a number heard and typed on a number pad (hear), its word read and typed in figures (read), its
figures and the word written (write: sedemindvajset, the ones first with in), said (say, from a range the learner has),
a date's ordinal (date: "Danes je ___ maj. (15.)" → petnajsti; page datumi) and, once the unit kdaj-cas's intro is
done, a clock time heard or written (time_hear, time_write: "ob pol šestih" is 5:30; page kdaj-cas). Its ranges grow
one at a time: 0–10 and 11–20 with the intro, then 30–90, 21–99, 100–999, 1000–9999 with the years, the next opening
when 8 of the last 10 answers in the newest are right. It schedules its own repeats (a number missed comes back in its
next runs: the same day, a day, three days, a week later): make no review cards of them. A run reaches you as a
session_end whose data.numbers has the ranges open (opened_now: one opened by this run), each task (mode, shown, answer,
expected, verdict, kind) and errors: update-db.py's errors as they stand, to persist as the report's errors with the
run's results (lani-db-updater; skills: listening for hear and time_hear, vocabulary for read and write, speaking for say,
grammar for date and time_write). Their pattern ids: numbers_listening, numbers_reading, numbers_spelling,
numbers_order (the tens first: dvajset sedem for sedemindvajset, 72 typed for 27), numbers_speaking,
numbers_ordinal_date (on datumi), numbers_clock_time, numbers_clock_time_half (half past counted from the hour before;
on kdaj-cas). numbers_order that keeps coming back: explain the order once more (sedem-in-dvajset, as German's
sieben-und-zwanzig). get_village's numbers shows where the learner is. Its words are the app's rules, machine-written:
one that looks wrong (about_exercise.source kind numbers) is a note for the developer, not a content fix.
