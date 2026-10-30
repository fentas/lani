#!/usr/bin/env python3
"""The case lens's trigger table (docs/plans/05-built/case-lens.md): writes case-triggers.json beside it.

The table is data the lens (companion/bridge/src/case-lens.ts) and the case trainer read: what decides a Slovene word's
case. It is written here, in a compact form with a few helpers (ex: an example the smoke analyses; v: a verb by its
infinitive and its first person singular), and made into the JSON with a fixed layout. Change this file, never the JSON,
then run it:

    python3 companion/grammar/sl/data/case-triggers.py           # writes case-triggers.json
    python3 companion/grammar/sl/data/case-triggers.py --check   # exit 1 when the JSON is not what this makes

companion/tests/test_case_triggers.py runs the check, so a JSON edited by hand fails the Python tests. The schema is
companion/bridge/src/case-triggers.ts; the smoke (test/smoke/case-lens.ts) validates the table and analyses every example.
Machine-written (Claude, 2026-10-29), checked against Fran 2026-10-30 (docs/plans/05-built/native-check-sl.md); the
lists and the examples want a native speaker's check.
"""
import argparse
import json
import sys
from pathlib import Path

ex = lambda sl, en, word, case, number, trigger=None, nth=None: {k: v for k, v in dict(sl=sl, en=en, word=word, nth=nth, case=case, number=number, trigger=trigger).items() if v is not None}
def v(lemma, present, forms=None, se=None, **kw):
    d = {"lemma": lemma, "present": present}
    if forms: d["forms"] = forms.split()
    if se: d["se"] = True
    d.update(kw)
    return d

T = {
 "schema": "lani.case-triggers/v0",
 "language": "sl",
 "machine_written": True,
 "review": "Machine-written (Claude, 2026-10-29), not reviewed by a native speaker yet: the lists, the senses, the rules and the examples want a native check (docs/plans/05-built/case-lens.md, \"Native check\"). Standard Slovene; a spoken form is never wrong, only not listed. Checked against Fran 2026-10-30 (docs/plans/05-built/native-check-sl.md): every preposition's cases and the governing verbs by SP2001, the fused forms by SSKJ² (po, the plural and the dual added); the lists and examples otherwise as written.",
 "about": "What decides a Slovene word's case, for the case lens (bridge/src/case-lens.ts, docs/plans/05-built/case-lens.md): the six cases (their question words and colours, shared with the case trainer), the prepositions and the cases they take by sense (motion: kam? rest: kje? time: kdaj?), the negations, the words of quantity and the numbers from five, the verbs and the words of feeling that govern a case, the verbs of motion and of rest, the copulas, the verbs with an accusative object and those without one, the nouns of time, fixed phrases, the personal pronouns' forms (the osebni-zaimki page's, with the genitive and the locative it leaves out) and the prepositions fused with a pronoun (zame, nanj). Every entry names its rule: the grammar book's page that explains it. A verb is given by its infinitive and its first person singular; the lens makes its other forms. The examples are analysed by the smoke: each must come out as it says. Made by case-triggers.py beside it: change that and run it, never this file.",
 "cases": [
  {"key": "nom", "id": "imenovalnik", "n": 1, "questions": ["kdo?", "kaj?"], "colour": "#4A7FC1", "page": "imenovalnik"},
  {"key": "gen", "id": "rodilnik", "n": 2, "questions": ["koga?", "česa?"], "colour": "#C2503C", "page": "rodilnik"},
  {"key": "dat", "id": "dajalnik", "n": 3, "questions": ["komu?", "čemu?"], "colour": "#D69A2D", "page": "dajalnik"},
  {"key": "acc", "id": "tozilnik", "n": 4, "questions": ["koga?", "kaj?"], "colour": "#3E9B5F", "page": "tozilnik"},
  {"key": "loc", "id": "mestnik", "n": 5, "questions": ["pri kom?", "pri čem?"], "also": ["o kom?", "o čem?"], "colour": "#8A5CB8", "page": "mestnik"},
  {"key": "ins", "id": "orodnik", "n": 6, "questions": ["s kom?", "s čim?"], "colour": "#2C9AA0", "page": "orodnik"},
 ],
 "prepositions": [
  {"words": ["brez"], "takes": [{"case": "gen", "sense": "other", "rule": "rodilnik-predlogi"}],
   "examples": [ex("Kavo pijem brez mleka.", "I drink coffee without milk.", "mleka", "gen", "sg", "brez"), ex("Kavo pijem brez sladkorja.", "I drink coffee without sugar.", "sladkorja", "gen", "sg", "brez")]},
  {"words": ["iz"], "takes": [{"case": "gen", "sense": "from", "rule": "rodilnik-predlogi"}],
   "examples": [ex("Sem iz Berlina.", "I'm from Berlin.", "Berlina", "gen", "sg", "iz"), ex("Moja partnerka je iz Gorice.", "My partner is from Gorizia.", "Gorice", "gen", "sg", "iz")]},
  {"words": ["od"], "takes": [{"case": "gen", "sense": "from", "rule": "rodilnik-predlogi"}],
   "examples": [ex("Vlak gre od Nove Gorice do Ljubljane.", "The train goes from Nova Gorica to Ljubljana.", "Gorice", "gen", "sg", "od")]},
  {"words": ["do"], "takes": [{"case": "gen", "sense": "towards", "rule": "rodilnik-predlogi"}],
   "examples": [ex("Vlak gre od Nove Gorice do Ljubljane.", "The train goes from Nova Gorica to Ljubljana.", "Ljubljane", "gen", "sg", "do")]},
  {"words": ["blizu", "zraven", "poleg"], "takes": [{"case": "gen", "sense": "rest", "rule": "rodilnik-predlogi"}],
   "examples": [ex("Šotor bo stal blizu ribnika.", "The tent will stand near the pond.", "ribnika", "gen", "sg", "blizu"), ex("Zraven hiše je vrt.", "Next to the house is a garden.", "hiše", "gen", "sg", "Zraven")]},
  {"words": ["mimo"], "takes": [{"case": "gen", "sense": "motion", "rule": "rodilnik-predlogi"}],
   "examples": [ex("Pot gre mimo cerkve.", "The path goes past the church.", "cerkve", "gen", "sg", "mimo")]},
  {"words": ["zaradi", "namesto", "razen"], "takes": [{"case": "gen", "sense": "other", "rule": "rodilnik-predlogi"}],
   "examples": [ex("Zaradi dežja ostanemo doma.", "Because of the rain we stay at home.", "dežja", "gen", "sg", "Zaradi"), ex("Namesto kave pijem čaj.", "Instead of coffee I drink tea.", "kave", "gen", "sg", "Namesto")]},
  {"words": ["okoli", "okrog", "sredi"], "takes": [{"case": "gen", "sense": "rest", "rule": "rodilnik-predlogi"}],
   "examples": [ex("Okoli ognja sedijo otroci.", "The children sit around the fire.", "ognja", "gen", "sg", "Okoli"), ex("Sredi vasi stoji lipa.", "In the middle of the village stands a lime tree.", "vasi", "gen", "sg", "Sredi")]},
  {"words": ["izpod", "izza"], "takes": [{"case": "gen", "sense": "from", "rule": "rodilnik-predlogi"}],
   "examples": [ex("Mačka pride izpod mize.", "The cat comes out from under the table.", "mize", "gen", "sg", "izpod")]},
  {"words": ["k", "h"], "takes": [{"case": "dat", "sense": "towards", "rule": "dajalnik"}],
   "examples": [ex("Grem k babici.", "I'm going to grandma's.", "babici", "dat", "sg", "k"), ex("K ognju, prosim.", "To the fire, please.", "ognju", "dat", "sg", "K")]},
  {"words": ["proti", "kljub"], "takes": [{"case": "dat", "sense": "towards", "rule": "dajalnik"}],
   "examples": [ex("Grem proti vasi.", "I'm walking towards the village.", "vasi", "dat", "sg", "proti"), ex("Kljub dežju gremo ven.", "Despite the rain we go out.", "dežju", "dat", "sg", "Kljub")]},
  {"words": ["v"], "takes": [{"case": "acc", "sense": "motion", "rule": "kam-tozilnik", "time": "kdaj-cas"}, {"case": "loc", "sense": "rest", "rule": "kje-mestnik-orodnik"}],
   "examples": [ex("Jutri grem v Gorico.", "Tomorrow I'm going to Gorizia.", "Gorico", "acc", "sg", "v"), ex("Babica je v kuhinji.", "Grandma is in the kitchen.", "kuhinji", "loc", "sg", "v"), ex("V sredo grem k Tonetu.", "On Wednesday I'm going to Tone's.", "sredo", "acc", "sg", "V")]},
  {"words": ["na"], "takes": [{"case": "acc", "sense": "motion", "rule": "kam-tozilnik"}, {"case": "loc", "sense": "rest", "rule": "kje-mestnik-orodnik"}],
   "examples": [ex("Grem na pošto.", "I'm going to the post office.", "pošto", "acc", "sg", "na"), ex("Pisma so na mizi.", "The letters are on the table.", "mizi", "loc", "sg", "na"), ex("Postavi lonec na mizo.", "Put the pot on the table.", "mizo", "acc", "sg", "na")]},
  {"words": ["ob"], "takes": [{"case": "loc", "sense": "rest", "rule": "kje-mestnik-orodnik", "time": "kdaj-cas"}, {"case": "acc", "sense": "motion", "rule": "kam-tozilnik"}],
   "examples": [ex("Šotor bo stal ob ribniku.", "The tent will stand by the pond.", "ribniku", "loc", "sg", "ob"), ex("Ana ima tečaj ob torkih.", "Ana has a course on Tuesdays.", "torkih", "loc", "pl", "ob")]},
  {"words": ["pri"], "takes": [{"case": "loc", "sense": "rest", "rule": "kje-mestnik-orodnik"}],
   "examples": [ex("Mačka spi pri peči.", "The cat sleeps by the stove.", "peči", "loc", "sg", "pri"), ex("Seveda, počakaj pri meni!", "Of course, wait at my place!", "meni", "loc", "sg", "pri")]},
  {"words": ["o"], "takes": [{"case": "loc", "sense": "about", "rule": "mestnik"}],
   "examples": [ex("Zvečer govorimo o knjigi.", "In the evening we talk about the book.", "knjigi", "loc", "sg", "o")]},
  {"words": ["po"], "takes": [{"case": "loc", "sense": "after", "rule": "mestnik", "default": True}, {"case": "acc", "sense": "fetch", "rule": "tozilnik"}],
   "examples": [ex("Po kosilu gremo na njivo.", "After lunch we go to the field.", "kosilu", "loc", "sg", "Po"), ex("Tine teka po vasi.", "Tine runs around the village.", "vasi", "loc", "sg", "po"), ex("Grem po kruh.", "I'm going to get bread.", "kruh", "acc", "sg", "po")]},
  {"words": ["z", "s"], "takes": [{"case": "ins", "sense": "with", "rule": "orodnik", "default": True}, {"case": "gen", "sense": "from", "rule": "rodilnik-predlogi"}],
   "examples": [ex("Tone kuje s kladivom.", "Tone forges with a hammer.", "kladivom", "ins", "sg", "s"), ex("Ravno prihajam s tržnice.", "I'm just coming from the market.", "tržnice", "gen", "sg", "s"), ex("Kava z mlekom, prosim.", "Coffee with milk, please.", "mlekom", "ins", "sg", "z")]},
  {"words": ["pod", "nad", "pred", "med"], "takes": [{"case": "ins", "sense": "rest", "rule": "kje-mestnik-orodnik", "time": "kdaj-cas", "default": True}, {"case": "acc", "sense": "motion", "rule": "kam-tozilnik"}],
   "examples": [ex("Tam je, pod klopjo!", "There it is, under the bench!", "klopjo", "ins", "sg", "pod"), ex("Pred šotorom bo ognjišče.", "In front of the tent there will be a fireplace.", "šotorom", "ins", "sg", "Pred"), ex("Ovce se pasejo med drevesi.", "The sheep graze among the trees.", "drevesi", "ins", "pl", "med"), ex("Postavi lonec pod mizo.", "Put the pot under the table.", "mizo", "acc", "sg", "pod"), ex("Anton je bil tukaj pred eno uro.", "Anton was here an hour ago.", "uro", "ins", "sg", "pred")]},
  {"words": ["za"], "takes": [{"case": "acc", "sense": "for", "rule": "tozilnik", "default": True}, {"case": "ins", "sense": "rest", "rule": "kje-mestnik-orodnik"}, {"case": "acc", "sense": "motion", "rule": "kam-tozilnik"}],
   "examples": [ex("Hvala za kavo.", "Thanks for the coffee.", "kavo", "acc", "sg", "za"), ex("Drva so za šotorom.", "The firewood is behind the tent.", "šotorom", "ins", "sg", "za"), ex("Luka nese drva za šotor.", "Luka carries the firewood behind the tent.", "šotor", "acc", "sg", "za")]},
  {"words": ["skozi", "čez", "zoper"], "takes": [{"case": "acc", "sense": "motion", "rule": "tozilnik", "time": "kdaj-cas"}],
   "examples": [ex("Mačka skoči skozi okno.", "The cat jumps through the window.", "okno", "acc", "sg", "skozi"), ex("Krošnjar bo čez eno uro pri vratih.", "The pedlar will be at the door in an hour.", "uro", "acc", "sg", "čez")]},
 ],
 "negation": {
  "words": "ni nisem nisi nisva nista nismo niste niso nimam nimaš nima nimava nimata nimamo nimate nimajo nočem nočeš noče nočeva nočeta nočemo nočete nočejo".split(),
  "before_verb": ["ne"],
  "contrast": ["ampak", "temveč"],
  "rule": "rodilnik-nikalnica",
  "examples": [ex("Danes nimam časa.", "I have no time today.", "časa", "gen", "sg", "nimam"), ex("Luka ne pije kave.", "Luka doesn't drink coffee.", "kave", "gen", "sg", "ne"), ex("V trgovini ni kruha.", "There's no bread in the shop.", "kruha", "gen", "sg", "ni"), ex("Ne vidim ovce.", "I can't see the sheep.", "ovce", "gen", "sg", "Ne")],
 },
 "quantity": {
  "words": "malo veliko dosti preveč premalo več manj nekaj koliko toliko polno ogromno malce dovolj precej nič".split(),
  "measures": "kos hlebec liter deciliter kilogram dekagram gram kozarec kozarček skodelica steklenica žlica žlička ščepec vreča vrečka škatla lonec krožnik košara vedro par kup ducat pest šopek čreda kanta rezina".split(),
  "numbers": "pet šest sedem osem devet deset enajst dvanajst trinajst štirinajst petnajst šestnajst sedemnajst osemnajst devetnajst dvajset trideset štirideset petdeset šestdeset sedemdeset osemdeset devetdeset sto dvesto tristo štiristo petsto tisoč".split(),
  "rule": "rodilnik-kolicina",
  "examples": [ex("Še hlebec kruha, prosim.", "Another loaf of bread, please.", "kruha", "gen", "sg", "hlebec"), ex("V gozdu je veliko borovnic.", "There are lots of blueberries in the forest.", "borovnic", "gen", "pl", "veliko"), ex("Luka ima dvanajst ovc.", "Luka has twelve sheep.", "ovc", "gen", "pl", "dvanajst"), ex("Koliko jajc potrebuješ za potico?", "How many eggs do you need for the potica?", "jajc", "gen", "pl", "Koliko"), ex("V hlevu je pet ovc.", "There are five sheep in the barn.", "ovc", "gen", "pl", "pet")],
 },
 "agreeing_numbers": "en ena eno enega eni enemu enim eden dva dve dveh dvema trije tri treh trem tremi štirje štiri štirih štirim štirimi oba obe obeh obema".split(),
 "dual_words": "dva dve dveh dvema oba obe obeh obema midva medve vidva vedve onadva onidve sva sta bova bosta nisva nista naju vaju nama vama njiju njima ju jima".split(),
 "verbs": [
  # the genitive (rodilnik): bati se, veseliti se, spomniti se …
  v("bati", "bojim", "boj bojte bojmo bal bala balo bali bale", se=True, takes=["gen"], rule="rodilnik", examples=[ex("Nejc se boji nevihte.", "Nejc is afraid of the storm.", "nevihte", "gen", "sg", "boji"), ex("Muri se boji medveda.", "Muri is afraid of the bear.", "medveda", "gen", "sg", "boji")]),
  v("veseliti", "veselim", se=True, takes=["gen"], rule="rodilnik", examples=[ex("Veselim se praznika.", "I'm looking forward to the festival.", "praznika", "gen", "sg", "Veselim")]),
  v("spomniti", "spomnim", se=True, takes=["gen"], rule="rodilnik", examples=[ex("Spomnim se babice.", "I remember grandma.", "babice", "gen", "sg", "Spomnim")]),
  v("sramovati", "sramujem", se=True, takes=["gen"], rule="rodilnik"),
  v("naveličati", "naveličam", se=True, takes=["gen"], rule="rodilnik"),
  v("udeležiti", "udeležim", se=True, takes=["gen"], rule="rodilnik"),
  v("lotiti", "lotim", se=True, takes=["gen"], rule="rodilnik"),
  v("izogibati", "izogibam", se=True, takes=["gen"], rule="rodilnik"),
  v("spominjati", "spominjam", se=True, takes=["gen"], rule="rodilnik"),
  # the dative (dajalnik): pomagati, verjeti …
  v("pomagati", "pomagam", takes=["dat"], rule="dajalnik", examples=[ex("Seveda vam pomagam!", "Of course I'll help you!", "vam", "dat", "pl", "pomagam"), ex("Babici pomagam.", "I help grandma.", "Babici", "dat", "sg", "pomagam")]),
  v("verjeti", "verjamem", "verjel verjela verjelo verjeli verjele verjemi verjemite", takes=["dat"], rule="dajalnik", examples=[ex("Verjamem ti.", "I believe you.", "ti", "dat", "sg", "Verjamem")]),
  v("zaupati", "zaupam", takes=["dat"], rule="dajalnik"),
  v("slediti", "sledim", takes=["dat"], rule="dajalnik"),
  v("odgovoriti", "odgovorim", takes=["dat"], rule="dajalnik"),
  v("odgovarjati", "odgovarjam", takes=["dat"], rule="dajalnik"),
  v("telefonirati", "telefoniram", takes=["dat"], rule="dajalnik"),
  v("čestitati", "čestitam", takes=["dat"], rule="dajalnik"),
  v("zahvaliti", "zahvalim", se=True, takes=["dat"], rule="dajalnik", examples=[ex("Zahvalim se babici.", "I thank grandma.", "babici", "dat", "sg", "Zahvalim")]),
  v("ugajati", "ugajam", takes=["dat"], rule="dajalnik"),
  v("škoditi", "škodim", takes=["dat"], rule="dajalnik"),
  v("groziti", "grozim", takes=["dat"], rule="dajalnik"),
  v("ploskati", "ploskam", takes=["dat"], rule="dajalnik"),
  v("približati", "približam", se=True, takes=["dat"], rule="dajalnik"),
  v("smejati", "smejem", "smejijo smej smejte", se=True, takes=["dat"], rule="dajalnik"),
  v("zdeti", "zdim", "zdelo zdel zdela zdeli", se=True, takes=["dat"], rule="dajalnik"),
  v("manjkati", "manjkam", takes=["dat"], rule="dajalnik", examples=[ex("Knjigi manjka zadnja stran.", "The book is missing its last page.", "Knjigi", "dat", "sg", "manjka")]),
  v("pripadati", "pripadam", takes=["dat"], rule="dajalnik"),
  v("nasprotovati", "nasprotujem", takes=["dat"], rule="dajalnik"),
  # whom something is given, said or shown to (komu?) and what (kaj?)
  v("dati", "dam", "daš da dava dasta damo daste dajo dal dala dalo dali dale daj dajte dajmo", takes=["dat", "acc"], rule="dajalnik", examples=[ex("Dam mu kruh.", "I give him bread.", "mu", "dat", "sg", "Dam")]),
  v("dajati", "dajem", takes=["dat", "acc"], rule="dajalnik"),
  v("prinesti", "prinesem", "prinesel prinesla prineslo prinesli prinesle prinesi prinesite", takes=["dat", "acc"], rule="dajalnik"),
  v("nesti", "nesem", "nesel nesla neslo nesli nesle nesi nesite", takes=["dat", "acc"], rule="dajalnik", examples=[ex("Naj ga nesem gostom?", "Shall I take it to the guests?", "gostom", "dat", "pl", "nesem")]),
  v("poslati", "pošljem", "poslal poslala poslalo poslali poslale pošlji pošljite", takes=["dat", "acc"], rule="dajalnik"),
  v("pisati", "pišem", "pisal pisala pisalo pisali pisale piši pišite", takes=["dat", "acc"], rule="dajalnik"),
  v("napisati", "napišem", "napisal napisala napisali napiši napišite", takes=["dat", "acc"], rule="dajalnik"),
  v("reči", "rečem", "rekel rekla reklo rekli rekle reci recite", takes=["dat", "acc"], rule="dajalnik"),
  v("povedati", "povem", "poveš pove poveva povesta povemo poveste povejo povedal povedala povedalo povedali povedale povej povejte", takes=["dat", "acc"], rule="dajalnik"),
  v("pokazati", "pokažem", "pokazal pokazala pokazali pokaži pokažite", takes=["dat", "acc"], rule="dajalnik"),
  v("kazati", "kažem", "kazal kazala kaži kažite", takes=["dat", "acc"], rule="dajalnik"),
  v("posoditi", "posodim", takes=["dat", "acc"], rule="dajalnik"),
  v("podariti", "podarim", takes=["dat", "acc"], rule="dajalnik"),
  v("kupiti", "kupim", takes=["dat", "acc"], rule="dajalnik"),
  v("skuhati", "skuham", takes=["dat", "acc"], rule="dajalnik"),
  v("obljubiti", "obljubim", takes=["dat", "acc"], rule="dajalnik"),
  v("razložiti", "razložim", takes=["dat", "acc"], rule="dajalnik"),
  v("ponuditi", "ponudim", takes=["dat", "acc"], rule="dajalnik"),
  v("vrniti", "vrnem", "vrnil vrnila vrnili vrni vrnite", takes=["dat", "acc"], rule="dajalnik"),
  v("prodati", "prodam", "prodaš proda prodamo prodate prodajo prodal prodala prodaj", takes=["dat", "acc"], rule="dajalnik"),
  v("plačati", "plačam", takes=["dat", "acc"], rule="dajalnik"),
  v("natočiti", "natočim", takes=["dat", "acc"], rule="dajalnik"),
  v("svetovati", "svetujem", takes=["dat", "acc"], rule="dajalnik"),
  v("govoriti", "govorim", takes=["dat", "acc"], rule="dajalnik"),
  v("brati", "berem", "bral brala bralo brali brale beri berite", takes=["dat", "acc"], rule="dajalnik"),
  v("dovoliti", "dovolim", takes=["dat", "acc"], rule="dajalnik"),
  v("predstaviti", "predstavim", takes=["dat", "acc"], rule="dajalnik"),
  v("praviti", "pravim", takes=["dat", "acc"], rule="dajalnik"),
  v("prinašati", "prinašam", takes=["dat", "acc"], rule="dajalnik"),
  v("posojati", "posojam", takes=["dat", "acc"], rule="dajalnik"),
  # who feels it, in the accusative: zebe me, boli ga
  v("zebsti", "zebem", "zebe zeblo", takes=["acc"], rule="custva", examples=[ex("Zebe me.", "I'm cold.", "me", "acc", "sg", "Zebe")]),
  v("boleti", "bolim", "boli bolijo bolelo bolela", takes=["acc"], rule="custva"),
  v("skrbeti", "skrbim", "skrbi skrbelo", takes=["acc"], rule="custva"),
  v("srbeti", "srbim", "srbi srbelo", takes=["acc"], rule="custva"),
  v("jeziti", "jezim", "jezi jezilo", takes=["acc"], rule="custva"),
  v("veseliti", "veselim", "veseli veselilo", takes=["acc"], rule="custva"),
  v("muditi", "mudim", "mudi mudilo", se=True, takes=["dat"], rule="custva"),
  v("zanimati", "zanimam", "zanimalo", takes=["acc"], rule="custva", examples=[ex("Toneta zanima vse o konjih.", "Tone is interested in everything about horses.", "Toneta", "acc", "sg", "zanima")]),
 ],
 "predicates": [
  {"words": "všeč mrzlo toplo vroče žal dolgčas hudo slabo težko treba ime jasno škoda vseeno".split(), "case": "dat", "rule": "custva",
   "examples": [ex("Rebula mi je zelo všeč.", "I like Rebula a lot.", "mi", "dat", "sg", "všeč"), ex("Tudi meni je mrzlo.", "I'm cold too.", "meni", "dat", "sg", "mrzlo"), ex("Koliko ti je let, Nejc?", "How old are you, Nejc?", "ti", "dat", "sg")]},
  {"words": ["strah", "sram"], "case": "acc", "rule": "custva", "examples": [ex("Strah me je.", "I'm afraid.", "me", "acc", "sg", "Strah")]},
  # adjectives that take the genitive of what follows them (SP2001 pôln: "poln česa: vrč, ~ vina"; vréden: "vreden
  # koga/česa: pomilovanja ~ človek") (case-lens-2)
  {"words": "poln polna polno polni polne vreden vredna vredno vredni vredne".split(), "case": "gen", "rule": "rodilnik", "after": True,
   "examples": [ex("Hrib je poln grmovja.", "The hill is full of bushes.", "grmovja", "gen", "sg", "poln"), ex("Pa poglej ta razgled. Vreden je vsake stopnice.", "And look at this view. It's worth every step.", "stopnice", "gen", "sg", "Vreden")]},
 ],
 "motion": [
  v("iti", "grem"), v("priti", "pridem"), v("oditi", "odidem", "odšel odšla odšlo odšli odšle odidi odidite"), v("hoditi", "hodim"),
  v("teči", "tečem", "tekel tekla teklo tekli tekle teci tecite"), v("tekati", "tekam"), v("peljati", "peljem", "peljal peljala peljali pelji peljite"),
  v("pripeljati", "pripeljem", "pripeljal pripeljala pripelji"), v("odpeljati", "odpeljem", "odpeljal odpeljala odpelji"), v("voziti", "vozim"),
  v("leteti", "letim"), v("pluti", "plujem"), v("skočiti", "skočim"), v("stopiti", "stopim"), v("vstopiti", "vstopim"),
  v("sesti", "sedem", "sedel sedla sedlo sedli sedle sedi sedite"), v("usesti", "usedem", "usedel usedla usedli usedi usedite", se=True),
  v("leči", "ležem", "legel legla leglo legli lezi lezite"), v("uleči", "uležem", "ulegel ulegla ulegli ulezi", se=True),
  v("postaviti", "postavim"), v("položiti", "položim"), v("dati", "dam", "daš da damo daste dajo dal dala dali daj dajte"), v("devati", "devam"),
  v("obesiti", "obesim"), v("nesti", "nesem", "nesel nesla neslo nesli nesle nesi nesite"),
  v("prinesti", "prinesem", "prinesel prinesla prineslo prinesli prinesle prinesi prinesite"), v("odnesti", "odnesem", "odnesel odnesla odnesli odnesi"),
  v("nositi", "nosim"), v("poslati", "pošljem", "poslal poslala poslali pošlji"), v("vreči", "vržem", "vrgel vrgla vrglo vrgli vrzi vrzite"),
  v("metati", "mečem", "metal metala meči mečite"), v("natočiti", "natočim"), v("spraviti", "spravim"), v("pospraviti", "pospravim"),
  v("skriti", "skrijem", "skril skrila skrij skrijte"), v("potovati", "potujem"), v("vrniti", "vrnem", "vrnil vrnila vrnili vrni vrnite"),
  v("preseliti", "preselim"), v("pogledati", "pogledam"), v("gledati", "gledam"), v("splezati", "splezam"), v("zlesti", "zlezem", "zlezel zlezla zlezi"),
  v("pasti", "padem", "padel padla padlo padli padle"), v("povabiti", "povabim"), v("vpisati", "vpišem", "vpisal vpisala"), v("spustiti", "spustim"),
  v("odpraviti", "odpravim", se=True), v("zaviti", "zavijem", "zavil zavila zavij"), v("hiteti", "hitim"),
 ],
 "rest": [
  v("biti", "sem"), v("stati", "stojim", "stal stala stalo stali stale stoj stojte"), v("sedeti", "sedim", "sedel sedela sedelo sedeli sedele"),
  v("ležati", "ležim", "ležal ležala ležalo ležali"), v("živeti", "živim"), v("stanovati", "stanujem"), v("delati", "delam"), v("spati", "spim"),
  v("ostati", "ostanem", "ostal ostala ostalo ostali ostale ostani ostanite"), v("čakati", "čakam"), v("viseti", "visim"),
  v("rasti", "rastem", "rasel rasla raslo rasli"), v("igrati", "igram"), v("počivati", "počivam"), v("pasti", "pasem", "pasel pasla pasli", se=True),
  v("sijati", "sijem"), v("skrivati", "skrivam", se=True), v("kuhati", "kuham"), v("peči", "pečem", "pekel pekla peklo pekli peci"), v("plavati", "plavam"),
 ],
 "copulas": [v("biti", "sem"), v("postati", "postanem", "postal postala postalo postali postale postani"), v("ostati", "ostanem", "ostal ostala ostalo ostali ostani"), v("imenovati", "imenujem", se=True)],
 "transitive": [
  v("imeti", "imam"), v("videti", "vidim", "videl videla videlo videli videle"), v("vzeti", "vzamem"), v("piti", "pijem"), v("jesti", "jem"),
  v("kupiti", "kupim"), v("potrebovati", "potrebujem"), v("iskati", "iščem", "iskal iskala iskali išči iščite"), v("poznati", "poznam"), v("rabiti", "rabim"),
  v("ljubiti", "ljubim"), v("dobiti", "dobim"), v("slišati", "slišim"), v("gledati", "gledam"), v("brati", "berem", "bral brala brali beri berite"),
  v("narediti", "naredim"), v("delati", "delam"), v("prodati", "prodam", "prodajo prodal prodala prodaj"), v("vprašati", "vprašam"), v("čakati", "čakam"),
  v("obiskati", "obiščem", "obiskal obiskala obiskali obišči obiščite"), v("poklicati", "pokličem", "poklical poklicala pokliči pokličite"),
  v("peči", "pečem", "pekel pekla peklo pekli peci"), v("kuhati", "kuham"), v("skuhati", "skuham"), v("oprati", "operem", "opral oprala operi"), v("umiti", "umijem", "umil umila umij"),
  v("postaviti", "postavim"), v("položiti", "položim"), v("odpreti", "odprem", "odprl odprla odprlo odprli odpri odprite"), v("zapreti", "zaprem", "zaprl zaprla zaprlo zaprli zapri zaprite"),
  v("prižgati", "prižgem", "prižgal prižgala prižgi"), v("najti", "najdem", "našel našla našlo našli našle najdi najdite"), v("izgubiti", "izgubim"), v("pozabiti", "pozabim"),
  v("spoznati", "spoznam"), v("voziti", "vozim"), v("peljati", "peljem", "peljal peljala pelji"), v("nositi", "nosim"), v("pospraviti", "pospravim"),
  v("zgraditi", "zgradim"), v("popraviti", "popravim"), v("napisati", "napišem", "napisal napisala napiši"), v("pisati", "pišem", "pisal pisala piši"),
  v("risati", "rišem", "risal risala riši"), v("nabrati", "naberem", "nabral nabrala naberi"), v("nabirati", "nabiram"), v("pasti", "pasem", "pasel pasla pasli pasi"),
  v("krmiti", "krmim"), v("sekati", "sekam"), v("kovati", "kujem", "koval kovala kuj"), v("prositi", "prosim"), v("plačati", "plačam"),
  v("pojesti", "pojem", "pojedel pojedla pojedli pojej"), v("popiti", "popijem", "popil popila popij"), v("pripraviti", "pripravim"), v("poskusiti", "poskusim"),
  v("ujeti", "ujamem", "ujel ujela ujeli ujemi"), v("loviti", "lovim"), v("poslušati", "poslušam"), v("srečati", "srečam"), v("pozdraviti", "pozdravim"),
  v("poljubiti", "poljubim"), v("objeti", "objamem", "objel objela objemi"), v("učiti", "učim"), v("prijeti", "primem", "prijel prijela primi"),
  v("držati", "držim"), v("pobrati", "poberem", "pobral pobrala poberi"), v("zbuditi", "zbudim"), v("nesti", "nesem", "nesel nesla nesli nesi"),
  v("prinesti", "prinesem", "prinesel prinesla prinesli prinesi"), v("dati", "dam"), v("poslati", "pošljem", "poslal poslala pošlji"),
  v("vreči", "vržem", "vrgel vrgla vrzi"), v("obleči", "oblečem", "oblekel oblekla obleci"), v("pomesti", "pometem", "pometel pometla pometi"),
  v("mesiti", "mesim"), v("zamesiti", "zamesim"), v("zaliti", "zalijem", "zalil zalila zalij"), v("rezati", "režem", "rezal rezala reži"),
  v("pobarvati", "pobarvam"), v("šivati", "šivam"), v("molsti", "molzem", "molzel molzla molzi"), v("napolniti", "napolnim"), v("pokriti", "pokrijem", "pokril pokrila pokrij"),
  v("kuriti", "kurim"), v("zakuriti", "zakurim"), v("čistiti", "čistim"), v("počistiti", "počistim"), v("vabiti", "vabim"), v("povabiti", "povabim"),
  v("pomeriti", "pomerim"), v("ponesti", "ponesem", "ponesel ponesla"), v("zaščititi", "zaščitim"), v("pričakati", "pričakam"), v("pričakovati", "pričakujem"),
  v("želeti", "želim"), v("hoteti", "hočem"), v("marati", "maram"), v("začeti", "začnem", "začel začela začelo začeli začni"), v("končati", "končam"),
  v("obrniti", "obrnem", "obrnil obrnila obrni"), v("poiskati", "poiščem", "poiskal poiskala poišči"), v("dvigniti", "dvignem", "dvignil dvignila dvigni"),
  v("pičiti", "pičim"), v("peti", "pojem", "pel pela pelo peli poj pojte"), v("zapeti", "zapojem", "zapel zapela zapoj"), v("igrati", "igram"),
  v("praviti", "pravim"), v("prinašati", "prinašam"), v("posojati", "posojam"), v("gnesti", "gnetem", "gnetel gnetla gneti"), v("pokazati", "pokažem", "pokazal pokazala pokaži"), v("povedati", "povem", "povejo povedal povedala povej povejte"),
  v("reči", "rečem", "rekel rekla rekli reci"), v("kupovati", "kupujem"), v("prebrati", "preberem", "prebral prebrala preberi"), v("razumeti", "razumem"),
  v("vedeti", "vem"), v("znati", "znam"), v("ljubiti", "ljubim"), v("pogledati", "pogledam"), v("zgubiti", "zgubim"), v("nositi", "nosim"),
 ],
 "intransitive": [
  v("iti", "grem"), v("priti", "pridem"), v("oditi", "odidem", "odšel odšla odšlo odšli odšle odidi"), v("hoditi", "hodim"), v("teči", "tečem", "tekel tekla teklo tekli teci"),
  v("tekati", "tekam"), v("leteti", "letim"), v("letati", "letam"), v("pluti", "plujem"), v("skočiti", "skočim"), v("stopiti", "stopim"), v("vstopiti", "vstopim"),
  v("potovati", "potujem"), v("stati", "stojim", "stal stala stalo stali stale"), v("sedeti", "sedim", "sedel sedela sedelo sedeli"), v("ležati", "ležim", "ležal ležala ležalo ležali"),
  v("živeti", "živim"), v("stanovati", "stanujem"), v("spati", "spim"), v("viseti", "visim"), v("rasti", "rastem", "rasel rasla raslo rasli"),
  v("počivati", "počivam"), v("sijati", "sijem"), v("pihati", "piham"), v("deževati", "dežujem", "dežuje deževalo"), v("snežiti", "snežim", "sneži snežilo"),
  v("cveteti", "cvetim"), v("umreti", "umrem", "umrl umrla umrlo umrli"), v("jokati", "jokam"), v("plesati", "plešem", "plesal plesala pleši"),
  v("kričati", "kričim"), v("lajati", "lajam"), v("plavati", "plavam"), v("vstati", "vstanem", "vstal vstala vstali vstani"), v("hiteti", "hitim"),
  v("zgoditi", "zgodim", "zgodi zgodilo", se=True), v("zbuditi", "zbudim", se=True), v("pasti", "padem", "padel padla padlo padli"), v("pasti", "pasem", "pasel pasla pasli", se=True),
  v("smejati", "smejem", "smejijo smej", se=True), v("igrati", "igram", se=True), v("vrniti", "vrnem", "vrnil vrnila vrnili vrni", se=True), v("usesti", "usedem", "usedel usedla usedi", se=True),
  v("uleči", "uležem", "ulegel ulegla ulezi", se=True), v("odpraviti", "odpravim", se=True), v("imenovati", "imenujem", se=True), v("veseliti", "veselim", se=True),
  v("stati", "stanem", "stal stala stalo stali"), v("vstajati", "vstajam"), v("skakati", "skačem", "skakal skakala skači"), v("zvoniti", "zvonim"),
  v("grmeti", "grmim", "grmi grmelo"), v("sanjati", "sanjam"), v("zbežati", "zbežim", "zbežal zbežala zbeži"), v("dišati", "dišim", "diši dišalo"),
  v("bliskati", "bliskam", "bliska bliskalo", se=True), v("mračiti", "mračim", "mrači mračilo", se=True), v("začeti", "začnem", "začel začela začelo začeli", se=True),
 ],
 "time": {
  "nouns": "dan teden mesec leto ura minuta sekunda trenutek jutro dopoldne popoldne večer noč zima pomlad poletje jesen ponedeljek torek sreda četrtek petek sobota nedelja čas vikend".split()
  # (SP2001 pôldne: "zvoniti ~", case-lens-2)
  + ["poldne", "polnoč"],
  "rule": "kdaj-cas",
  "examples": [ex("Ves dan je deževalo.", "It rained all day.", "dan", "acc", "sg"), ex("Vsak dan grem v šolo.", "Every day I go to school.", "dan", "acc", "sg")],
 },
 # the genitive of time (when?): leto before a year in figures (leta 1844), a masculine or neuter noun of time after
 # nekega, tistega … (nekega dne, tistega večera): the cas-izrazi page's (datumi has leta 2026 too). SP2001 léto:
 # "1945. ~a in ~a 1945", "Še istega ~a … je umrl"; néki: "~ega lepega dne" (case-lens-2)
 "time_genitive": {
  "years": ["leto"],
  "determiners": ["nekega", "tistega", "istega", "onega", "naslednjega", "prejšnjega", "drugega"],
  "rule": "cas-izrazi",
  "examples": [
   ex("France Prešeren je Zdravljico napisal leta 1844.", "France Prešeren wrote the Zdravljica in 1844.", "leta", "gen", "sg", "1844"),
   ex("Mlin so zgradili leta 1890.", "The mill was built in 1890.", "leta", "gen", "sg", "1890"),
   ex("Leta 1991 je Slovenija postala samostojna.", "In 1991 Slovenia became independent.", "Leta", "gen", "sg", "1991"),
   ex("Tukaj živim od leta 2024.", "I've lived here since 2024.", "leta", "gen", "sg", "od"),
   ex("Nekega dne pride na Dunaj Pegam.", "One day Pegam comes to Vienna.", "dne", "gen", "sg", "Nekega"),
   ex("Tistega večera je prišel tujec.", "That evening a stranger came.", "večera", "gen", "sg", "Tistega"),
  ],
 },
 "phrases": [
  {"words": "dober dan", "case": "nom", "number": "sg", "rule": "imenovalnik"},
  {"words": "dobro jutro", "case": "nom", "number": "sg", "rule": "imenovalnik"},
  {"words": "dober večer", "case": "nom", "number": "sg", "rule": "imenovalnik"},
  {"words": "lahko noč", "case": "nom", "number": "sg", "rule": "imenovalnik"},
  {"words": "dober tek", "case": "nom", "number": "sg", "rule": "imenovalnik"},
  {"words": "lepa hvala", "case": "nom", "number": "sg", "rule": "imenovalnik"},
  {"words": "hvala lepa", "case": "nom", "number": "sg", "rule": "imenovalnik"},
  {"words": "na svidenje", "case": "acc", "number": "sg", "trigger": "na", "rule": "tozilnik"},
  {"words": "na zdravje", "case": "acc", "number": "sg", "trigger": "na", "rule": "tozilnik"},
  {"words": "na primer", "case": "acc", "number": "sg", "trigger": "na", "rule": "tozilnik"},
  {"words": "na srečo", "case": "acc", "number": "sg", "trigger": "na", "rule": "tozilnik"},
  {"words": "na žalost", "case": "acc", "number": "sg", "trigger": "na", "rule": "tozilnik"},
  {"words": "na pomoč", "case": "acc", "number": "sg", "trigger": "na", "rule": "tozilnik"},
  {"words": "na voljo", "case": "acc", "number": "sg", "trigger": "na", "rule": "tozilnik"},
  {"words": "v redu", "case": "loc", "number": "sg", "trigger": "v", "rule": "mestnik"},
  {"words": "na koncu", "case": "loc", "number": "sg", "trigger": "na", "rule": "mestnik"},
  {"words": "na začetku", "case": "loc", "number": "sg", "trigger": "na", "rule": "mestnik"},
  {"words": "na vrsti", "case": "loc", "number": "sg", "trigger": "na", "rule": "mestnik"},
 ],
 "predicatives": [
  {"lemma": "rad", "rule": "imenovalnik", "forms": [
   {"form": "rad", "number": "sg", "genders": ["m"]}, {"form": "rada", "number": "sg", "genders": ["f"]}, {"form": "rado", "number": "sg", "genders": ["n"]},
   {"form": "radi", "number": "pl", "genders": ["m"]}, {"form": "rade", "number": "pl", "genders": ["f", "n"]},
   {"form": "rada", "number": "du", "genders": ["m"]}, {"form": "radi", "number": "du", "genders": ["f", "n"]}]},
  # sam (alone, by oneself), said of the subject as rad is: Zdaj kuham sam, jota se ne skuha sama, moramo sami; not samo
  # (the adverb, only) (case-lens-2)
  {"lemma": "sam", "rule": "imenovalnik", "forms": [
   {"form": "sam", "number": "sg", "genders": ["m"]}, {"form": "sama", "number": "sg", "genders": ["f"]},
   {"form": "sami", "number": "pl", "genders": ["m"]}, {"form": "same", "number": "pl", "genders": ["f"]}, {"form": "sama", "number": "pl", "genders": ["n"]},
   {"form": "sama", "number": "du", "genders": ["m"]}, {"form": "sami", "number": "du", "genders": ["f", "n"]}]},
 ],
 "pronouns": [
  {"lemma": "jaz", "person": 1, "number": "sg", "genders": ["m", "f"], "forms": {"nom": ["jaz"], "gen": ["mene", "me"], "dat": ["meni", "mi"], "acc": ["mene", "me"], "loc": ["meni"], "ins": ["mano", "menoj"]}},
  {"lemma": "ti", "person": 2, "number": "sg", "genders": ["m", "f"], "forms": {"nom": ["ti"], "gen": ["tebe", "te"], "dat": ["tebi", "ti"], "acc": ["tebe", "te"], "loc": ["tebi"], "ins": ["tabo", "teboj"]}},
  {"lemma": "on", "person": 3, "number": "sg", "genders": ["m"], "forms": {"nom": ["on"], "gen": ["njega", "ga"], "dat": ["njemu", "mu"], "acc": ["njega", "ga"], "loc": ["njem"], "ins": ["njim"]}},
  {"lemma": "ono", "person": 3, "number": "sg", "genders": ["n"], "forms": {"nom": ["ono"], "gen": ["njega", "ga"], "dat": ["njemu", "mu"], "acc": ["njega", "ga"], "loc": ["njem"], "ins": ["njim"]}},
  {"lemma": "ona", "person": 3, "number": "sg", "genders": ["f"], "forms": {"nom": ["ona"], "gen": ["nje"], "dat": ["njej", "ji"], "acc": ["njo", "jo"], "loc": ["njej"], "ins": ["njo"]}},
  {"lemma": "midva", "person": 1, "number": "du", "genders": ["m"], "forms": {"nom": ["midva"], "gen": ["naju"], "dat": ["nama"], "acc": ["naju"], "loc": ["naju"], "ins": ["nama"]}},
  {"lemma": "medve", "person": 1, "number": "du", "genders": ["f", "n"], "forms": {"nom": ["medve"], "gen": ["naju"], "dat": ["nama"], "acc": ["naju"], "loc": ["naju"], "ins": ["nama"]}},
  {"lemma": "vidva", "person": 2, "number": "du", "genders": ["m"], "forms": {"nom": ["vidva"], "gen": ["vaju"], "dat": ["vama"], "acc": ["vaju"], "loc": ["vaju"], "ins": ["vama"]}},
  {"lemma": "vedve", "person": 2, "number": "du", "genders": ["f", "n"], "forms": {"nom": ["vedve"], "gen": ["vaju"], "dat": ["vama"], "acc": ["vaju"], "loc": ["vaju"], "ins": ["vama"]}},
  {"lemma": "onadva", "person": 3, "number": "du", "genders": ["m"], "forms": {"nom": ["onadva"], "gen": ["njiju", "ju"], "dat": ["njima", "jima"], "acc": ["njiju", "ju"], "loc": ["njiju"], "ins": ["njima"]}},
  {"lemma": "onidve", "person": 3, "number": "du", "genders": ["f", "n"], "forms": {"nom": ["onidve", "onedve"], "gen": ["njiju", "ju"], "dat": ["njima", "jima"], "acc": ["njiju", "ju"], "loc": ["njiju"], "ins": ["njima"]}},
  {"lemma": "mi", "person": 1, "number": "pl", "genders": ["m"], "forms": {"nom": ["mi"], "gen": ["nas"], "dat": ["nam"], "acc": ["nas"], "loc": ["nas"], "ins": ["nami"]}},
  {"lemma": "me", "person": 1, "number": "pl", "genders": ["f"], "forms": {"nom": ["me"], "gen": ["nas"], "dat": ["nam"], "acc": ["nas"], "loc": ["nas"], "ins": ["nami"]}},
  {"lemma": "vi", "person": 2, "number": "pl", "genders": ["m"], "forms": {"nom": ["vi"], "gen": ["vas"], "dat": ["vam"], "acc": ["vas"], "loc": ["vas"], "ins": ["vami"]}},
  {"lemma": "ve", "person": 2, "number": "pl", "genders": ["f"], "forms": {"nom": ["ve"], "gen": ["vas"], "dat": ["vam"], "acc": ["vas"], "loc": ["vas"], "ins": ["vami"]}},
  {"lemma": "oni", "person": 3, "number": "pl", "genders": ["m"], "forms": {"nom": ["oni"], "gen": ["njih", "jih"], "dat": ["njim", "jim"], "acc": ["njih", "jih"], "loc": ["njih"], "ins": ["njimi"]}},
  {"lemma": "one", "person": 3, "number": "pl", "genders": ["f", "n"], "forms": {"nom": ["one", "ona"], "gen": ["njih", "jih"], "dat": ["njim", "jim"], "acc": ["njih", "jih"], "loc": ["njih"], "ins": ["njimi"]}},
  {"lemma": "se", "person": None, "number": "sg", "genders": ["m", "f", "n"], "forms": {"gen": ["sebe"], "dat": ["sebi"], "acc": ["sebe"], "loc": ["sebi"], "ins": ["sabo", "seboj"]}},
 ],
 "fused": [],
 "roles": {
  "subject": {"rule": "imenovalnik", "examples": [ex("Luka pase ovce.", "Luka grazes the sheep.", "Luka", "nom", "sg"), ex("Babica peče kruh.", "Grandma bakes bread.", "Babica", "nom", "sg")]},
  "predicate": {"rule": "imenovalnik", "examples": [ex("France je mlinar.", "France is a miller.", "mlinar", "nom", "sg", "je")]},
  "object": {"rule": "tozilnik", "examples": [ex("Janez bere knjigo.", "Janez reads a book.", "knjigo", "acc", "sg", "bere"), ex("Imam brata in sestro.", "I have a brother and a sister.", "brata", "acc", "sg", "Imam"), ex("Luka išče jagnje.", "Luka is looking for the lamb.", "jagnje", "acc", "sg", "išče")]},
  "address": {"rule": "imenovalnik", "examples": [ex("Anton, poglej!", "Anton, look!", "Anton", "nom", "sg"), ex("Dober dan, učiteljica!", "Good afternoon, teacher!", "učiteljica", "nom", "sg")]},
  "attribute": {"rule": "rodilnik", "examples": [ex("To je hiša mojega brata.", "This is my brother's house.", "brata", "gen", "sg", "hiša")]},
  "dative": {"rule": "dajalnik", "examples": [ex("Mami zapojem pesem.", "I sing mum a song.", "Mami", "dat", "sg")]},
  "partitive": {"rule": "rodilnik", "examples": [ex("Daj mi kruha.", "Give me some bread.", "kruha", "gen", "sg")]},
 },
}
for p, pron in [("za", "tozilnik"), ("na", "kam-tozilnik"), ("v", "kam-tozilnik"), ("pred", "kam-tozilnik"), ("pod", "kam-tozilnik"), ("nad", "kam-tozilnik"), ("čez", "tozilnik"), ("skoz", "tozilnik")]:
    stem = {"v": "va"}.get(p, p)
    for suffix, lemma, number in [("me", "jaz", "sg"), ("te", "ti", "sg"), ("se", "se", "sg"), ("nj", "on", "sg"), ("njo", "ona", "sg")]:
        if p in ("pred", "pod", "nad", "čez", "skoz") and suffix == "nj":
            form = f"{p}enj"
        else:
            form = stem + suffix
        if p in ("skoz", "čez") and suffix in ("te", "se", "njo"):
            continue
        T["fused"].append({"form": form, "preposition": "skozi" if p == "skoz" else p, "pronoun": lemma, "case": "acc", "number": number, "rule": pron})
# fused forms SSKJ2 gives with po and čez, the plural and the dual (native-check-sl, 2026-10-30: pote, ponjo, zanje, nanju)
T["fused"] += [
    {"form": "pome", "preposition": "po", "pronoun": "jaz", "case": "acc", "number": "sg", "rule": "tozilnik"},
    {"form": "pote", "preposition": "po", "pronoun": "ti", "case": "acc", "number": "sg", "rule": "tozilnik"},
    {"form": "ponj", "preposition": "po", "pronoun": "on", "case": "acc", "number": "sg", "rule": "tozilnik"},
    {"form": "ponjo", "preposition": "po", "pronoun": "ona", "case": "acc", "number": "sg", "rule": "tozilnik"},
    {"form": "čezte", "preposition": "čez", "pronoun": "ti", "case": "acc", "number": "sg", "rule": "tozilnik"},
    {"form": "zanje", "preposition": "za", "pronoun": "oni", "case": "acc", "number": "pl", "rule": "tozilnik"},
    {"form": "nanje", "preposition": "na", "pronoun": "oni", "case": "acc", "number": "pl", "rule": "kam-tozilnik"},
    {"form": "vanje", "preposition": "v", "pronoun": "oni", "case": "acc", "number": "pl", "rule": "kam-tozilnik"},
    {"form": "ponje", "preposition": "po", "pronoun": "oni", "case": "acc", "number": "pl", "rule": "tozilnik"},
    {"form": "podnje", "preposition": "pod", "pronoun": "oni", "case": "acc", "number": "pl", "rule": "kam-tozilnik"},
    {"form": "prednje", "preposition": "pred", "pronoun": "oni", "case": "acc", "number": "pl", "rule": "kam-tozilnik"},
    {"form": "nadnje", "preposition": "nad", "pronoun": "oni", "case": "acc", "number": "pl", "rule": "kam-tozilnik"},
    {"form": "čeznje", "preposition": "čez", "pronoun": "oni", "case": "acc", "number": "pl", "rule": "tozilnik"},
    {"form": "zanju", "preposition": "za", "pronoun": "onadva", "case": "acc", "number": "du", "rule": "tozilnik"},
    {"form": "nanju", "preposition": "na", "pronoun": "onadva", "case": "acc", "number": "du", "rule": "kam-tozilnik"},
    {"form": "vanju", "preposition": "v", "pronoun": "onadva", "case": "acc", "number": "du", "rule": "kam-tozilnik"},
    {"form": "ponju", "preposition": "po", "pronoun": "onadva", "case": "acc", "number": "du", "rule": "tozilnik"},
    {"form": "podnju", "preposition": "pod", "pronoun": "onadva", "case": "acc", "number": "du", "rule": "kam-tozilnik"},
    {"form": "prednju", "preposition": "pred", "pronoun": "onadva", "case": "acc", "number": "du", "rule": "kam-tozilnik"},
    {"form": "nadnju", "preposition": "nad", "pronoun": "onadva", "case": "acc", "number": "du", "rule": "kam-tozilnik"},
    {"form": "čeznju", "preposition": "čez", "pronoun": "onadva", "case": "acc", "number": "du", "rule": "tozilnik"},
]


def fmt(x, ind=0):
    sp = '  ' * ind
    one = json.dumps(x, ensure_ascii=False)
    if not isinstance(x, (dict, list)): return one
    if isinstance(x, list) and all(not isinstance(y, (dict, list)) for y in x):
        if len(one) + len(sp) <= 118: return one
        # wrap a long list of words
        lines, cur = [], []
        for y in x:
            s = json.dumps(y, ensure_ascii=False)
            if cur and len(sp) + 2 + len(', '.join(cur + [s])) > 118:
                lines.append(', '.join(cur)); cur = []
            cur.append(s)
        if cur: lines.append(', '.join(cur))
        return '[\n' + ',\n'.join(sp + '  ' + l for l in lines) + '\n' + sp + ']'
    if len(one) + len(sp) <= 118: return one
    if isinstance(x, dict):
        return '{\n' + ',\n'.join(f'{sp}  {json.dumps(k, ensure_ascii=False)}: {fmt(v, ind + 1)}' for k, v in x.items()) + '\n' + sp + '}'
    return '[\n' + ',\n'.join(sp + '  ' + fmt(y, ind + 1) for y in x) + '\n' + sp + ']'


OUT = Path(__file__).with_name("case-triggers.json")


def main() -> int:
    ap = argparse.ArgumentParser(description="Write the case lens's trigger table (case-triggers.json beside this file).")
    ap.add_argument("--check", action="store_true", help="only compare: exit 1 when the JSON is not what this makes")
    ap.add_argument("--out", type=Path, default=OUT, help="where to write it (default: case-triggers.json beside this file)")
    a = ap.parse_args()
    made = fmt(T) + "\n"
    if a.check:
        had = a.out.read_text(encoding="utf-8") if a.out.exists() else ""
        if had != made:
            print(f"{a.out} is not what {Path(__file__).name} makes: run it (python3 {Path(__file__).name}) and commit both", file=sys.stderr)
            return 1
        print(f"{a.out.name}: matches {Path(__file__).name}")
        return 0
    a.out.write_text(made, encoding="utf-8")
    print(f"{a.out}: {len(T['prepositions'])} preposition groups, {len(T['verbs'])} governing verbs, {len(T['phrases'])} phrases, {len(T['fused'])} fused forms")
    return 0


if __name__ == "__main__":
    sys.exit(main())
