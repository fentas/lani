# Built: B2 of the Slovene school, part one (six units)

*Built 2026-11-01, branch `sl-b2-a`. Jan, 2026-11-01: "can you make app complete - all what is missing?" Jan learns
Slovene from English and aims at B2; the outline (companion/course/sl/course.json) had B2's 18 units planned. Three
branches write them at once: this one units 1–6, `sl-b2-b` units 7–12, `sl-b2-c` units 13–18. The format and how a unit
is written: companion/COURSE.md. Everything here is machine-written (Claude) in Slovene, English, German and Italian,
and waits for a native speaker's check (below).*

## What

Six units of B2, each a guided intro (Mojca's steps with their quick questions, the growing table), three worksheets
and a test, at B1's size. B2 is "the B1 grammar used accurately, plus the grammar of argument and writing"
(docs/grammar-syllabus.md, "B2"): these six are the verb forms of writing and stories (gerunds, participles, the past
conditional, the pluperfect) and the joining of clauses (kateri, čigar, kdor; kljub temu da, medtem ko, ne da bi). The
sentences are the village's adult life: Stari Janez's stories by the fire, the grape harvest, the storm and the
hayrack, the village meeting about the road, the new bridge, a 1910 letter home from Cleveland.

| # | unit | title (sl) | its pages | intro | worksheets (items) | test |
|---|---|---|---|---|---|---|
| 1 | `delezja` | Hodeč, molče: deležja | delezja, prislovi | 6 steps | sort + table (16) · gap text with a bank + a meaning (9) · 2 transform lists (9) | 12 items |
| 2 | `delezniki-pridevniki` | Odprta vrata, zrel sad: deležniki | delezniki, trpnik, pridevniki-ujemanje | 5 | sort + table (20) · gap text with a bank + a meaning (9) · transform (8) | 12 |
| 3 | `pogojnik-pretekli` | Bil bi prišel: pretekli pogojnik | pogojnik-pretekli, pogojnik-ce-bi | 6 | table + 3 choices (9) · gap text with a bank + a meaning (9) · transform (8) | 12 |
| 4 | `predpreteklik` | Bil sem že odšel: predpreteklik | predpreteklik, pretekli-cas | 6 | table + sort (17) · gap text with a bank + a choice (8) · 2 transform lists (8) | 12 |
| 5 | `oziralni-kateri` | Kdor išče, najde: kateri, čigar, kdor | oziralni-kateri, oziralni-ki | 6 | table + sort (19) · gap text with a bank (9) · transform (8) | 12 |
| 6 | `odvisniki-napredno` | Kljub temu da, medtem ko, ne da bi | odvisniki-napredno, odvisniki | 6 | sort + 3 choices (15) · gap text with a bank + a meaning (9) · transform (8) | 12 |

- **delezja**: the gerund as an adverb. -e, -aje say how (molče, stoje, sede, kleče, jokaje, igraje); -eč, -oč what
  goes on at the same time, from the *they* form (hodijo → hodeč, vedo → vedoč, pojejo → pojoč, rečejo → rekoč), bookish:
  speech says ko je hodil or hodil je … in; the everyday ones (mimogrede, nehote, nevede, tako rekoč); -ši, -vši
  (prišedši) only to recognise, as SP2001 labels it old. The gap text: Stari Janez's story of the shepherd and the lamb.
- **delezniki-pridevniki**: participles as adjectives, and how a thing came to be so: -n, -t done to it (obrano grozdje,
  B1's passive), -l what it became by itself (dozorelo grozdje, ovenele rože, odpadlo listje, zarjavel žebelj, odrasel;
  usahniti gives usahel studenec, though its past is usahnil), -eč, -oč what is going on (tekoča voda, goreča sveča,
  boleča noga), bodoči and bivši, and participles for people (odrasli, mimoidoči). The gap text: autumn in the village.
- **pogojnik-pretekli**: bil bi + the l-form, both agreeing, bi second (Marko bi bil obral, Bil bi prišel), the no
  before bi, če bi bil vedel, bi bil …; near misses and regrets (skoraj bi bil padel, raje bi bila ostala doma, moral
  bi bil priti); the plain conditional with a word of the past says the same and is right too. The gap text: what the
  village would have done before last year's storm.
- **predpreteklik**: sem bil + the l-form, both agreeing, its short words second (Bil sem že odšel; Ko je France prišel,
  je bil pomočnik že zmlel žito), the past with že as speech says it, the aspect (smo jedli, smo bili že pojedli), the
  stories' tense. The gap text: a letter home from Cleveland, 1910.
- **oziralni-kateri** (retitled: the outline's "Hiša, v kateri živim" is B1's `oziralni-ki` already): kateri in every
  case for ki + a pronoun (fant, katerega sem videl; both accepted), whose (čigar for a man, as SP2001 has it; katere,
  katerih, katerega for a woman, many, a thing), kdor and kar in their cases (kogar, komur, s komer; česar, čemur, o
  čemer), kar for a whole sentence (…, kar je Vido razjezilo), kamor and od koder; two proverbs (Kdor išče, najde; Kdor
  drugemu jamo koplje, sam vanjo pade). The gap text: the village meeting about the new road.
- **odvisniki-napredno** (retitled: the outline's "Čeprav, da bi, tako da" are B1's `odvisniki`): kljub temu da and
  četudi, medtem ko (while and whereas), potem ko, odkar, dokler ne with the perfective, takoj ko, razen če, ne da bi
  and namesto da bi with the l-form, kot da bi, tako … da, preveč … da bi. Its transform lists say `whole` (the word in
  brackets is a joining word, not the person). The gap text: building the new bridge.

Each unit's first gap text has a word bank (the units' first worksheets are a sort or a table), the second gap text
and the test none. No table cell or line names a form (`lemma` + `form`): a gerund, a participle, a conditional or a
pluperfect isn't a slot of the forms book.

## The grammar pages

Six new B2 pages in companion/grammar/sl/, each with its cheat-sheet card (a rule line, a compact table, two examples,
a tip), so each unit's summary card is its own rule: `delezja`, `delezniki`, `pogojnik-pretekli`, `predpreteklik`,
`oziralni-kateri`, `odvisniki-napredno` (the book: 65 Slovene pages with this branch). Each unit also lists the B1 or
A2 page it builds on (prislovi, trpnik and pridevniki-ujemanje, pogojnik-ce-bi, pretekli-cas, oziralni-ki,
odvisniki), whose card follows on the cheat sheet. New pages rather than the B1 pages extended: each B2 unit teaches
something its B1 page names at most in passing (pogojnik-ce-bi's "bi bil prišel: only to recognise", oziralni-ki's
kateri after a preposition), the B1 pages stay at their size, and each unit gets a card of its own. Each new page sees
its B1 page.

**What a unit turns on** (COURSE.md's table, a B2 part): `delezniki-pridevniki` an adjective's agreement (it lists
pridevniki-ujemanje), `predpreteklik` the past's l-forms (pretekli-cas); the other four no form of a table.

**Spoken forms and the clitics.** None is graded wrong. A sentence may begin with its short word (memory: Slovene
initial clitics): bi bil prišel, Bi bil prišel., Sem že odšel. are accepted beside the written orders; no choice offers a
clitic-first order as the wrong one. Čigar for a woman, heard in speech and the papers and marked ° by SP2001, is an
`almost` choice; for a thing it is accepted (SSKJ² allows it, SP2001 marks it °). Kot da and kakor da, takoj ko, brž ko
and čim, kljub temu da and čeprav are each accepted where they fit. The past conditional and the pluperfect are taught as
written forms, and the units say every time that speech says the same with the plain conditional or the past and že,
and that both are right.

## Settling the forms

Against Fran (SP2001, SSKJ²) and Sloleks 2.0, one request at a time:

| what | settled | source |
|---|---|---|
| deležje's kinds | SP2001: "~ na -aje, -oč, -eč, -(v)ši"; SSKJ²: "glagolska oblika v prislovni funkciji" | [Fran](https://fran.si/iskanje?View=1&Query=deležje) |
| molče, stoje, jokaje | SP2001 nač. prisl. (molčé ~ poslušati; stojé ~ jesti; jokáje ~ govoriti); SSKJ² under the verbs | [molče](https://fran.si/iskanje?View=1&Query=molče) [stoje](https://fran.si/iskanje?View=1&Query=stoje) [jokaje](https://fran.si/iskanje?View=1&Query=jokaje) |
| hodeč, stoječ | SSKJ²: hodèč -éča -e (poslušal ga je, hodeč iz kota v kot); stojèč (stoječ pred hišo, je glasno zavpil: the comma) | [hodeč](https://fran.si/iskanje?View=1&Query=hodeč) |
| nehote, nevede | SSKJ² nèhoté prisl. (nevede in nehote se je izdal) | [Fran](https://fran.si/iskanje?View=1&Query=nehote) |
| prišedši | SSKJ² and SP2001 star.: only to recognise | [Fran](https://fran.si/iskanje?View=1&Query=prišedši) |
| the gerunds in Sloleks | adverbs: molče (4,023), stoje (346), sede (125), leže (16: rare, so not taught), kleče (438), jokaje (151), igraje (508), nehote (12,223), nevede (2,998), mimogrede (36,407), rekoč (61,486, mostly tako rekoč), vedoč, smehljaje; sedeč, hodeč, pojoč as adjective and adverb | [molče](https://viri.cjvt.si/sloleks/slv/headword/42099) [nehote](https://viri.cjvt.si/sloleks/slv/headword/42486) [rekoč](https://viri.cjvt.si/sloleks/slv/headword/44642) |
| ovenel, usahel | SP2001 ovenèl -éla -o; SSKJ² usáhel -hla -o prid. (usahli studenci), prim. usahniti, whose l-form Sloleks gives as usahnil | [ovenel](https://fran.si/iskanje?View=1&Query=ovenel) [usahel](https://fran.si/iskanje?View=1&Query=usahel) [usahniti](https://viri.cjvt.si/sloleks/slv/headword/8976) |
| the participles as adjectives | Sloleks adjectives: usahel, ovenel, odrasel, zarjavel, dozorel, odpadel, tekoč, boleč, goreč, cvetoč, bodoč, stoječ, mimoidoč, bivši, vrel, pograbljen, obran | [usahel](https://viri.cjvt.si/sloleks/slv/headword/36933) [odpadel](https://viri.cjvt.si/sloleks/slv/headword/26910) [mimoidoč](https://viri.cjvt.si/sloleks/slv/headword/23979) |
| grabiti | the raking verb (3,394) beside the rare grabljati (15): "France grabi listje" | [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/1234) |
| pogojnik, predpreteklik | SSKJ² and SP2001: sedanji, pretekli pogojnik; predpreteklik "glagolska oblika za izražanje preteklega dejanja, izvršenega pred kakim drugim dejanjem v preteklosti" | [pogojnik](https://fran.si/iskanje?View=1&Query=pogojnik) [predpreteklik](https://fran.si/iskanje?View=1&Query=predpreteklik) |
| kdor | SP2001: kógar, kómur, kógar, kómer, kómer; Kdor drugemu jamo koplje, sam vanjo pade; SSKJ² kdor išče, najde; kdor laže, ta krade | [Fran](https://fran.si/iskanje?View=1&Query=kdor) |
| čigar | SP2001: ozir. svoj. zaim. ed. moške osebe (stol, °čigar noga je nalomljena); SSKJ²: also of a masculine or neuter thing (kostanj, čigar senca …) | [Fran](https://fran.si/iskanje?View=1&Query=čigar) |
| kljub temu da | SP2001: dop. podr. vez. zv. = čeprav; SSKJ²: "piše se tudi z vejico" | [Fran](https://fran.si/iskanje?View=1&Query=kljub+temu+da) |

The forms of the past conditional (bil bi delal, bi bil delal) and of the pluperfect (sem bil delal) are Toporišič's
(Slovenska slovnica), not in the dictionaries' entries: the native check below.

## Tests

(filled in at the end of the branch)

## For the native check

Every unit and page is marked machine-written (`review`). What most needs Jan's wife's eye, unit by unit:

- **delezja**: whether the -eč, -oč gerunds read as bookish and right in a story (Hodeč po vasi, je Nejc žvižgal; Sedeč
  na klopi, je bral časopis), and their comma; sede against sedeč after zaspal (both accepted); igraje for "with ease"
  (Tone je igraje dvignil vrečo, nakovalo); Gospodinja ga je jokaje objela; Pojoč so šli skozi vas.
- **delezniki-pridevniki**: usahel studenec, k usahlemu studencu; zarjavel or zarjaveli žebelj (both accepted); Luka
  ima bolečo nogo; Za odrasle je trgatev delo; bodoči ženin; ovenjene and odpadena as the wrong forms.
- **pogojnik-pretekli**: how much the past conditional is still said (Če bi bil vedel, bi bil prišel), the orders
  accepted (bil bi prišel, prišel bi bil, bi bil prišel), bi ga bila voda odnesla, Brez Toneta bi se bil most podrl,
  Tone bi bil moral popraviti, Vida in Ančka bi bili spekli (the dual).
- **predpreteklik**: where bil goes in a main clause after ko (je bil avtobus že odpeljal, je avtobus že bil odpeljal:
  all accepted); the letter (mi je bil obljubil stric, denar mi je pošel, ki mi je bila spekla potico); Ko je Luka
  prišel na planino, je bila megla že padla.
- **oziralni-kateri**: kateri for ki + a pronoun in speech (accepted); čigar for a woman (almost) and a thing
  (accepted); the meeting (Na sestanku, ki ga je vodila teta Ančka, …); To je orodje, katerega Tone nima; Mesto, kamor
  bo vodila cesta.
- **odvisniki-napredno**: Nejc je bledel, kot da bi videl duha; Voda je bila premrzla / preveč mrzla, da bi se kopali
  (both accepted); Ko bo most gotov accepted beside takoj ko; the bridge's text.
- The six pages' rules and tables, in all three bases.

## What's left

(filled in at the end of the branch)
