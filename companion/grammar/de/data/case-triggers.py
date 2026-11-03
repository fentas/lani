#!/usr/bin/env python3
"""German's trigger table (docs/plans/05-built/case-de.md): writes case-triggers.json beside it.

What decides a German word's case, as data the case lens (companion/bridge/src/case-lens-de.ts), the case trainer's
checks (bridge/src/case-bank.ts) and, later, the hint chip read: the four cases (their question words and colours, the
same colours as Slovene's), the articles and the other determiners with every form, the adjective endings after each kind
of determiner, the prepositions by case (the two-way ones by motion and rest), the prepositions fused with the article
(im, zum, ins), the verbs that take a dative (helfen) or a dative and an accusative (geben) or a genitive (gedenken), the
verbs of motion and of rest, the copulas, the personal pronouns, the nouns that take -n (n-Deklination), the times without
a preposition (jeden Tag, eines Tages), the fixed phrases (zu Hause, Guten Morgen). Every entry names its rule: the German
grammar book's page that explains it.

Austrian standard (the course's): a form people say is never wrong, and the lens reads it as what it is: wegen dem Regen
is a dative after wegen ("spoken": right in talk, writing wants the genitive), dem Stefan sein Hund a dative, dem Bub
without its -n a dative too. The lens reads, it doesn't grade.

Written here in a compact form with a few helpers (ex: an example the smoke analyses; v: a verb, its forms made from its
infinitive and the forms the rules would get wrong), and made into the JSON with a fixed layout. Change this file, never
the JSON, then run it:

    python3 companion/grammar/de/data/case-triggers.py           # writes case-triggers.json
    python3 companion/grammar/de/data/case-triggers.py --check   # exit 1 when the JSON is not what this makes

companion/tests/test_case_triggers_de.py runs the check. The schema is companion/bridge/src/case-triggers-de.ts; the smoke
(test/smoke/case-de.ts) validates the table and analyses every example. Machine-written (Claude, 2026-11-03), for a
native speaker's check: the lists, the senses and the examples.
"""
import argparse
import json
import sys
from pathlib import Path

ex = lambda de, en, word, case, number, trigger=None, nth=None: {k: v for k, v in dict(de=de, en=en, word=word, nth=nth, case=case, number=number, trigger=trigger).items() if v is not None}

SLOTS = [f"{c}.{g}" for c in ("nom", "acc", "dat", "gen") for g in ("m", "f", "n", "pl")]


def det(lemma, kind, stem=None, plural=True, forms=None):
    """A determiner and its sixteen forms: the article der by its own table, a der-word (dieser) or an ein-word (mein) by its endings."""
    if forms is None:
        s = stem if stem is not None else lemma
        if kind == "der":
            end = dict(zip(SLOTS, "er e es e en e es e em er em en es er es er".split()))
            forms = {k: s + e for k, e in end.items()}
        else:
            end = dict(zip(SLOTS, ". e . e en e . e em er em en es er es er".split()))
            forms = {k: (lemma if e == "." else s + e) for k, e in end.items()}
    if not plural:
        forms = {k: v for k, v in forms.items() if not k.endswith(".pl")}
    return {"lemma": lemma, "kind": kind, "forms": {k: forms[k] for k in SLOTS if k in forms}}


DER = dict(zip(SLOTS, "der die das die den die das die dem der dem den des der des der".split()))

DETERMINERS = [
    det("der", "definite", forms=DER),
    det("dieser", "der", stem="dies"),
    det("jener", "der", stem="jen"),
    det("jeder", "der", stem="jed", plural=False),
    det("welcher", "der", stem="welch"),
    det("mancher", "der", stem="manch"),
    det("solcher", "der", stem="solch"),
    # alle, beide: the plural only, the endings of a der-word
    {"lemma": "alle", "kind": "der", "forms": {"nom.pl": "alle", "acc.pl": "alle", "dat.pl": "allen", "gen.pl": "aller"}},
    {"lemma": "beide", "kind": "der", "forms": {"nom.pl": "beide", "acc.pl": "beide", "dat.pl": "beiden", "gen.pl": "beider"}},
    det("ein", "ein", stem="ein", plural=False),
    det("kein", "ein", stem="kein"),
    det("mein", "possessive", stem="mein"),
    det("dein", "possessive", stem="dein"),
    det("sein", "possessive", stem="sein"),
    det("ihr", "possessive", stem="ihr"),
    det("unser", "possessive", stem="unser"),
    # euer drops its e before an ending: eure, euren (euere is old)
    {**det("euer", "possessive", stem="eur")},
]
# euer's endingless forms are euer itself
for d in DETERMINERS:
    if d["lemma"] == "euer":
        for k in ("nom.m", "nom.n", "acc.n"):
            d["forms"][k] = "euer"

ADJECTIVE_ENDINGS = {
    # after der, dieser, jeder … (weak): -e in the nominative singular and the accusative of die and das, else -en
    "weak": dict(zip(SLOTS, "e e e en en e e en en en en en en en en en".split())),
    # after ein, kein, mein … (mixed): the ending the determiner hasn't (ein großer Hund, ein kleines Haus)
    "mixed": dict(zip(SLOTS, "er e es en en e es en en en en en en en en en".split())),
    # with no determiner (strong): the ending der would have, but -en in the genitive of der and das (frischen Brotes)
    "strong": dict(zip(SLOTS, "er e es e en e es e em er em en en er en er".split())),
}

# --- verbs ---------------------------------------------------------------------------------------------------------


def conj(lemma, p2=None, p3=None, past=None, pp=None, imp=None, extra=""):
    """A verb's forms: the present (its du and er forms where the stem changes), the past, the participle, the imperative."""
    stem = lemma[:-2] if lemma.endswith("en") else lemma[:-1]
    e = stem.endswith(("t", "d")) or stem.endswith(("chn", "ffn", "tm", "gn"))
    sib = stem.endswith(("s", "ß", "z", "x"))
    forms = [lemma, stem + "e", ("%s" % p2) if p2 else stem + ("est" if e else "t" if sib else "st"), p3 or stem + ("et" if e else "t"), stem + ("et" if e else "t")]
    if past:
        forms += [past, past + ("est" if past.endswith(("t", "d")) else "st"), past + ("n" if past.endswith("e") else "en"), past + ("et" if past.endswith(("t", "d")) else "t")]
    else:
        pt = stem + ("ete" if e else "te")
        forms += [pt, pt + "st", pt + "n", pt + "t"]
    forms.append(pp or "ge" + stem + ("et" if e else "t"))
    forms += (imp.split() if imp else [stem])
    forms += extra.split()
    seen = []
    for f in forms:
        if f and f not in seen:
            seen.append(f)
    return seen


def v(lemma, takes=None, rule=None, particle=None, reflexive=None, only=None, **kw):
    """A verb by its infinitive: its forms ([only]: exactly these); [particle] for a separable one (zuhören: höre … zu, and
    joined in a clause that ends with it: weil ich ihm zuhöre), [reflexive] with sich."""
    base = lemma[len(particle):] if particle else lemma
    forms = only.split() if only else conj(base, **kw)
    d = {"lemma": lemma}
    if particle:
        d["particle"] = particle
        d["joined"] = sorted(set([particle + f for f in forms] + [particle + "zu" + base]))
    d["forms"] = forms
    if reflexive:
        d["reflexive"] = True
    if takes:
        d["takes"] = takes
    if rule:
        d["rule"] = rule
    return d


SEIN = "sein bin bist ist sind seid war warst waren wart gewesen sei seien wäre wären wärst"

# The verbs that take a dative: who gets, is helped, is thanked, likes (gefallen, schmecken: what is liked is the subject).
DATIVE_VERBS = [
    v("helfen", ["dat"], "dativ", p2="hilfst", p3="hilft", past="half", pp="geholfen", imp="hilf"),
    v("danken", ["dat"], "dativ"),
    v("gehören", ["dat"], "pronomen-dativ", pp="gehört"),
    v("gefallen", ["dat"], "pronomen-dativ", p2="gefällst", p3="gefällt", past="gefiel", pp="gefallen"),
    v("schmecken", ["dat"], "pronomen-dativ"),
    v("passen", ["dat"], "dativ", p2="passt", p3="passt", pp="gepasst"),
    v("antworten", ["dat"], "dativ"),
    v("folgen", ["dat"], "dativ"),
    v("gratulieren", ["dat"], "dativ", pp="gratuliert"),
    v("glauben", ["dat"], "dativ"),
    v("vertrauen", ["dat"], "dativ", pp="vertraut"),
    v("fehlen", ["dat"], "dativ"),
    v("begegnen", ["dat"], "dativ", pp="begegnet"),
    v("gelingen", ["dat"], "dativ", past="gelang", pp="gelungen"),
    v("schaden", ["dat"], "dativ"),
    v("widersprechen", ["dat"], "dativ", p2="widersprichst", p3="widerspricht", past="widersprach", pp="widersprochen", imp="widersprich"),
    v("verzeihen", ["dat"], "dativ", past="verzieh", pp="verziehen"),
    v("zuhören", ["dat"], "dativ", particle="zu", pp="gehört"),
    v("zuschauen", ["dat"], "dativ", particle="zu", pp="geschaut"),
    v("zusehen", ["dat"], "dativ", particle="zu", p2="siehst", p3="sieht", past="sah", pp="gesehen", imp="sieh"),
    v("wehtun", ["dat"], "pronomen-dativ", particle="weh", p2="tust", p3="tut", past="tat", pp="getan", extra="tu tue"),
    v("leidtun", ["dat"], "pronomen-dativ", particle="leid", p2="tust", p3="tut", past="tat", pp="getan", extra="tu tue"),
]

# The verbs of giving, showing, telling: whom in the dative, what in the accusative.
GIVING_VERBS = [
    v("geben", ["dat", "acc"], "dativ", p2="gibst", p3="gibt", past="gab", pp="gegeben", imp="gib"),
    v("schenken", ["dat", "acc"], "dativ"),
    v("zeigen", ["dat", "acc"], "dativ"),
    v("bringen", ["dat", "acc"], "dativ", past="brachte", pp="gebracht"),
    v("mitbringen", ["dat", "acc"], "dativ", particle="mit", past="brachte", pp="gebracht"),
    v("schicken", ["dat", "acc"], "dativ"),
    v("erklären", ["dat", "acc"], "dativ", pp="erklärt"),
    v("erzählen", ["dat", "acc"], "dativ", pp="erzählt"),
    v("sagen", ["dat", "acc"], "dativ"),
    v("kaufen", ["dat", "acc"], "dativ"),
    v("leihen", ["dat", "acc"], "dativ", past="lieh", pp="geliehen"),
    v("borgen", ["dat", "acc"], "dativ"),
    v("schreiben", ["dat", "acc"], "dativ", past="schrieb", pp="geschrieben"),
    v("empfehlen", ["dat", "acc"], "dativ", p2="empfiehlst", p3="empfiehlt", past="empfahl", pp="empfohlen", imp="empfiehl"),
    v("wünschen", ["dat", "acc"], "dativ"),
    v("reichen", ["dat", "acc"], "dativ"),
    v("anbieten", ["dat", "acc"], "dativ", particle="an", past="bot", pp="geboten"),
    v("verkaufen", ["dat", "acc"], "dativ", pp="verkauft"),
    v("versprechen", ["dat", "acc"], "dativ", p2="versprichst", p3="verspricht", past="versprach", pp="versprochen", imp="versprich"),
    v("beibringen", ["dat", "acc"], "dativ", particle="bei", past="brachte", pp="gebracht"),
    v("vorlesen", ["dat", "acc"], "dativ", particle="vor", p2="liest", p3="liest", past="las", pp="gelesen", imp="lies"),
    v("erlauben", ["dat", "acc"], "dativ", pp="erlaubt"),
    v("verbieten", ["dat", "acc"], "dativ", past="verbot", pp="verboten"),
]

# The few verbs with a genitive object (written, B2): gedenken, sich bedienen.
GENITIVE_VERBS = [
    v("gedenken", ["gen"], "genitiv", past="gedachte", pp="gedacht"),
    v("bedürfen", ["gen"], "genitiv", p2="bedarfst", p3="bedarf", past="bedurfte", pp="bedurft", extra="bedarf"),
    v("bedienen", ["gen"], "genitiv", reflexive=True, pp="bedient"),
    v("vergewissern", ["gen"], "genitiv", reflexive=True, pp="vergewissert"),
]

# Where to (Wohin?): after in, an, auf … the accusative.
MOTION = [
    v("gehen", past="ging", pp="gegangen", imp="geh gehe"),
    v("fahren", p2="fährst", p3="fährt", past="fuhr", pp="gefahren", imp="fahr fahre"),
    v("laufen", p2="läufst", p3="läuft", past="lief", pp="gelaufen", imp="lauf laufe"),
    v("kommen", past="kam", pp="gekommen", imp="komm komme"),
    v("fliegen", past="flog", pp="geflogen", imp="flieg"),
    v("rennen", past="rannte", pp="gerannt", imp="renn"),
    v("springen", past="sprang", pp="gesprungen", imp="spring"),
    v("steigen", past="stieg", pp="gestiegen", imp="steig"),
    v("klettern", extra="klettere kletterst klettert kletterte geklettert"),
    v("wandern", extra="wandere wanderst wandert wanderte gewandert"),
    v("reisen", p2="reist", pp="gereist"),
    v("ziehen", past="zog", pp="gezogen", imp="zieh"),
    v("legen"),
    v("stellen"),
    v("setzen", p2="setzt", pp="gesetzt"),
    v("tragen", p2="trägst", p3="trägt", past="trug", pp="getragen", imp="trag"),
    v("werfen", p2="wirfst", p3="wirft", past="warf", pp="geworfen", imp="wirf"),
    v("schieben", past="schob", pp="geschoben"),
    v("gießen", p2="gießt", past="goss", pp="gegossen", imp="gieß"),
    v("fallen", p2="fällst", p3="fällt", past="fiel", pp="gefallen"),
    v("schauen"),
    v("schwimmen", past="schwamm", pp="geschwommen", imp="schwimm"),
    v("bringen", past="brachte", pp="gebracht"),
    v("schicken"),
    v("hängen", past="hängte", pp="gehängt"),
    v("packen"),
    v("eilen"),
    v("kriechen", past="kroch", pp="gekrochen"),
    v("rollen"),
    v("treiben", past="trieb", pp="getrieben"),
]

# Where (Wo?): after in, an, auf … the dative.
REST = [
    v("sein", only=SEIN),
    v("stehen", past="stand", pp="gestanden", imp="steh"),
    v("liegen", past="lag", pp="gelegen", imp="lieg"),
    v("sitzen", p2="sitzt", past="saß", pp="gesessen", imp="sitz"),
    v("hängen", past="hing", pp="gehangen"),
    v("wohnen"),
    v("leben"),
    v("bleiben", past="blieb", pp="geblieben", imp="bleib"),
    v("arbeiten"),
    v("warten"),
    v("schlafen", p2="schläfst", p3="schläft", past="schlief", pp="geschlafen", imp="schlaf"),
    v("spielen"),
    v("essen", p2="isst", p3="isst", past="aß", pp="gegessen", imp="iss"),
    v("wachsen", p2="wächst", p3="wächst", past="wuchs", pp="gewachsen"),
    v("parken"),
    v("halten", p2="hältst", p3="hält", past="hielt", pp="gehalten", imp="halt"),
    v("lernen"),
    v("singen", past="sang", pp="gesungen", imp="sing"),
    v("tanzen", p2="tanzt", pp="getanzt"),
    v("feiern", extra="feiere feierst feiert feierte gefeiert"),
    v("kochen"),
    v("baden"),
    v("treffen", p2="triffst", p3="trifft", past="traf", pp="getroffen", imp="triff"),
    v("übernachten", pp="übernachtet"),
    v("frühstücken", pp="gefrühstückt"),
    v("befinden", reflexive=True, past="befand", pp="befunden"),
    v("sterben", p2="stirbst", p3="stirbt", past="starb", pp="gestorben"),
    v("lesen", p2="liest", p3="liest", past="las", pp="gelesen", imp="lies"),
    v("helfen", p2="hilfst", p3="hilft", past="half", pp="geholfen", imp="hilf"),
]

# What something is (Wer? Was?): the nominative after them.
COPULAS = [
    v("sein", only=SEIN),
    v("werden", p2="wirst", p3="wird", past="wurde", pp="geworden", extra="werde wurden würde würden"),
    v("bleiben", past="blieb", pp="geblieben", imp="bleib"),
    v("heißen", p2="heißt", p3="heißt", past="hieß", pp="geheißen"),
]

# A verb, an adjective or a noun with a fixed preposition, and the case the chunk takes (verben-praepositionen,
# adjektive-praepositionen): with a two-way preposition the chunk decides, not wo or wohin (Ich warte auf den Bus).
def chunk(verb, prep, case, rule="verben-praepositionen", **kw):
    d = v(verb, **kw)
    return {"lemma": d["lemma"], "forms": d["forms"], **({"particle": d["particle"], "joined": d["joined"]} if "particle" in d else {}), "preposition": prep, "case": case, "rule": rule}


CHUNKS = [
    chunk("warten", "auf", "acc"), chunk("freuen", "auf", "acc"), chunk("freuen", "über", "acc"), chunk("aufpassen", "auf", "acc", particle="auf", p2="passt", p3="passt", pp="gepasst"),
    chunk("denken", "an", "acc", past="dachte", pp="gedacht"), chunk("schreiben", "an", "acc", past="schrieb", pp="geschrieben"),
    chunk("sprechen", "über", "acc", p2="sprichst", p3="spricht", past="sprach", pp="gesprochen", imp="sprich"),
    chunk("reden", "über", "acc"), chunk("lachen", "über", "acc"), chunk("ärgern", "über", "acc", extra="ärgere ärgerst ärgert ärgerte geärgert"),
    chunk("bitten", "um", "acc", past="bat", pp="gebeten"), chunk("kümmern", "um", "acc", extra="kümmere kümmerst kümmert kümmerte gekümmert"),
    chunk("interessieren", "für", "acc", pp="interessiert"), chunk("erinnern", "an", "acc", extra="erinnere erinnerst erinnert erinnerte"),
    chunk("glauben", "an", "acc"), chunk("gewöhnen", "an", "acc", pp="gewöhnt"), chunk("hoffen", "auf", "acc"), chunk("achten", "auf", "acc"),
    chunk("verlieben", "in", "acc", pp="verliebt"), chunk("verlassen", "auf", "acc", p2="verlässt", p3="verlässt", past="verließ", pp="verlassen"),
    chunk("träumen", "von", "dat"), chunk("erzählen", "von", "dat", pp="erzählt"), chunk("fragen", "nach", "dat"),
    chunk("teilnehmen", "an", "dat", particle="teil", p2="nimmst", p3="nimmt", past="nahm", pp="genommen", imp="nimm"),
    chunk("fürchten", "vor", "dat"), chunk("schützen", "vor", "dat", p2="schützt", pp="geschützt"),
    {"lemma": "angst", "forms": ["angst"], "preposition": "vor", "case": "dat", "rule": "verben-praepositionen"},
    {"lemma": "stolz", "forms": ["stolz"], "preposition": "auf", "case": "acc", "rule": "adjektive-praepositionen"},
    {"lemma": "gespannt", "forms": ["gespannt"], "preposition": "auf", "case": "acc", "rule": "adjektive-praepositionen"},
    {"lemma": "neugierig", "forms": ["neugierig"], "preposition": "auf", "case": "acc", "rule": "adjektive-praepositionen"},
    {"lemma": "froh", "forms": ["froh"], "preposition": "über", "case": "acc", "rule": "adjektive-praepositionen"},
    {"lemma": "verliebt", "forms": ["verliebt"], "preposition": "in", "case": "acc", "rule": "adjektive-praepositionen"},
    {"lemma": "lust", "forms": ["lust"], "preposition": "auf", "case": "acc", "rule": "adjektive-praepositionen"},
    {"lemma": "interessiert", "forms": ["interessiert"], "preposition": "an", "case": "dat", "rule": "adjektive-praepositionen"},
    {"lemma": "interesse", "forms": ["interesse"], "preposition": "an", "case": "dat", "rule": "adjektive-praepositionen"},
    {"lemma": "bekannt", "forms": ["bekannt"], "preposition": "für", "case": "acc", "rule": "adjektive-praepositionen"},
    {"lemma": "zufrieden", "forms": ["zufrieden"], "preposition": "mit", "case": "dat", "rule": "adjektive-praepositionen"},
    {"lemma": "fertig", "forms": ["fertig"], "preposition": "mit", "case": "dat", "rule": "adjektive-praepositionen"},
    {"lemma": "verheiratet", "forms": ["verheiratet"], "preposition": "mit", "case": "dat", "rule": "adjektive-praepositionen"},
    {"lemma": "befreundet", "forms": ["befreundet"], "preposition": "mit", "case": "dat", "rule": "adjektive-praepositionen"},
    {"lemma": "begeistert", "forms": ["begeistert"], "preposition": "von", "case": "dat", "rule": "adjektive-praepositionen"},
    {"lemma": "abhängig", "forms": ["abhängig"], "preposition": "von", "case": "dat", "rule": "adjektive-praepositionen"},
]

# --- prepositions ------------------------------------------------------------------------------------------------

def take(case, sense, rule, **kw):
    return {"case": case, "sense": sense, "rule": rule, **kw}


TWO_WAY = "wechselpraepositionen"
PREPOSITIONS = [
    # the accusative always
    {"words": ["durch"], "takes": [take("acc", "other", "praepositionen-akkusativ")], "examples": [ex("Wir gehen durch den Wald.", "We walk through the forest.", "Wald", "acc", "sg", "durch")]},
    {"words": ["für"], "takes": [take("acc", "for", "praepositionen-akkusativ")], "examples": [ex("Das Geschenk ist für den Opa.", "The present is for Grandpa.", "Opa", "acc", "sg", "für")]},
    {"words": ["gegen"], "takes": [take("acc", "other", "praepositionen-akkusativ")], "examples": [ex("Wir laufen gegen den Wind.", "We run against the wind.", "Wind", "acc", "sg", "gegen")]},
    {"words": ["ohne"], "takes": [take("acc", "other", "praepositionen-akkusativ")], "examples": [ex("Kathi geht ohne ihren Hut.", "Kathi goes without her hat.", "Hut", "acc", "sg", "ohne")]},
    {"words": ["um"], "takes": [take("acc", "other", "praepositionen-akkusativ", time="uhrzeit")], "examples": [ex("Wir gehen um den See.", "We walk round the lake.", "See", "acc", "sg", "um")]},
    {"words": ["bis"], "takes": [take("acc", "time", "praepositionen-akkusativ")], "examples": [ex("Das Fest dauert bis nächsten Sonntag.", "The fair lasts until next Sunday.", "Sonntag", "acc", "sg", "bis")]},
    # the dative always
    {"words": ["aus"], "takes": [take("dat", "from", "praepositionen-dativ")], "examples": [ex("Franz kommt aus dem Stall.", "Franz comes out of the barn.", "Stall", "dat", "sg", "aus")]},
    {"words": ["bei"], "takes": [take("dat", "rest", "praepositionen-dativ")], "examples": [ex("Ich bin bei der Oma.", "I'm at Granny's.", "Oma", "dat", "sg", "bei")]},
    {"words": ["mit"], "takes": [take("dat", "with", "praepositionen-dativ")], "examples": [ex("Stefan fährt mit dem Bus.", "Stefan goes by bus.", "Bus", "dat", "sg", "mit"), ex("Ich gehe mit meinen Freunden.", "I'm going with my friends.", "Freunden", "dat", "pl", "mit")]},
    {"words": ["nach"], "takes": [take("dat", "motion", "praepositionen-dativ")], "examples": [ex("Nach dem Essen gehen wir.", "After the meal we go.", "Essen", "dat", "sg", "Nach")]},
    {"words": ["seit"], "takes": [take("dat", "time", "praepositionen-dativ")], "examples": [ex("Ich wohne seit einem Jahr hier.", "I've lived here for a year.", "Jahr", "dat", "sg", "seit")]},
    {"words": ["von"], "takes": [take("dat", "from", "praepositionen-dativ")], "examples": [ex("Der Käse ist von der Alm.", "The cheese is from the mountain pasture.", "Alm", "dat", "sg", "von")]},
    {"words": ["zu"], "takes": [take("dat", "towards", "praepositionen-dativ")], "examples": [ex("Ich gehe zu meiner Tante.", "I'm going to my aunt's.", "Tante", "dat", "sg", "zu")]},
    {"words": ["gegenüber"], "takes": [take("dat", "rest", "praepositionen-dativ")], "examples": [ex("Die Bank steht gegenüber der Kirche.", "The bench stands opposite the church.", "Kirche", "dat", "sg", "gegenüber")]},
    {"words": ["außer"], "takes": [take("dat", "other", "praepositionen-dativ")], "examples": [ex("Alle außer dem Hund schlafen.", "Everyone but the dog is asleep.", "Hund", "dat", "sg", "außer")]},
    {"words": ["ab"], "takes": [take("dat", "time", "praepositionen-dativ")], "examples": [ex("Ab dem ersten Mai ist die Hütte offen.", "From the first of May the hut is open.", "Mai", "dat", "sg", "Ab")]},
    {"words": ["samt", "nebst"], "takes": [take("dat", "with", "praepositionen-dativ")], "examples": [ex("Er kam samt seinem Hund.", "He came along with his dog.", "Hund", "dat", "sg", "samt")]},
    # the two-way prepositions: where to (motion) the accusative, where (rest) the dative
    {"words": ["in"], "takes": [take("acc", "motion", TWO_WAY), take("dat", "rest", TWO_WAY, time="uhrzeit")],
     "examples": [ex("Wir gehen in die Hütte.", "We go into the hut.", "Hütte", "acc", "sg", "in"), ex("Wir sind in der Hütte.", "We're in the hut.", "Hütte", "dat", "sg", "in")]},
    {"words": ["an"], "takes": [take("acc", "motion", TWO_WAY), take("dat", "rest", TWO_WAY, time="uhrzeit")],
     "examples": [ex("Wir fahren an den See.", "We're driving to the lake.", "See", "acc", "sg", "an"), ex("Das Bild hängt an der Wand.", "The picture hangs on the wall.", "Wand", "dat", "sg", "an")]},
    {"words": ["auf"], "takes": [take("acc", "motion", TWO_WAY), take("dat", "rest", TWO_WAY)],
     "examples": [ex("Die Kühe gehen auf die Alm.", "The cows go up to the pasture.", "Alm", "acc", "sg", "auf"), ex("Die Kühe sind auf der Alm.", "The cows are on the pasture.", "Alm", "dat", "sg", "auf")]},
    {"words": ["über"], "takes": [take("acc", "motion", TWO_WAY), take("dat", "rest", TWO_WAY)],
     "examples": [ex("Wir gehen über die Brücke.", "We walk over the bridge.", "Brücke", "acc", "sg", "über"), ex("Die Lampe hängt über dem Tisch.", "The lamp hangs above the table.", "Tisch", "dat", "sg", "über")]},
    {"words": ["unter"], "takes": [take("acc", "motion", TWO_WAY), take("dat", "rest", TWO_WAY)],
     "examples": [ex("Die Katze liegt unter dem Tisch.", "The cat lies under the table.", "Tisch", "dat", "sg", "unter"), ex("Die Katze kriecht unter den Tisch.", "The cat crawls under the table.", "Tisch", "acc", "sg", "unter")]},
    {"words": ["vor"], "takes": [take("acc", "motion", TWO_WAY), take("dat", "rest", TWO_WAY, time="uhrzeit")],
     "examples": [ex("Das Auto steht vor dem Haus.", "The car is in front of the house.", "Haus", "dat", "sg", "vor"), ex("Stell das Auto vor das Haus!", "Put the car in front of the house!", "Haus", "acc", "sg", "vor")]},
    {"words": ["hinter"], "takes": [take("acc", "motion", TWO_WAY), take("dat", "rest", TWO_WAY)],
     "examples": [ex("Der Hund schläft hinter dem Ofen.", "The dog sleeps behind the stove.", "Ofen", "dat", "sg", "hinter")]},
    {"words": ["neben"], "takes": [take("acc", "motion", TWO_WAY), take("dat", "rest", TWO_WAY)],
     "examples": [ex("Das Zelt steht neben dem Teich.", "The tent stands next to the pond.", "Teich", "dat", "sg", "neben"), ex("Setz dich neben mich!", "Sit down next to me!", "mich", "acc", "sg", "neben")]},
    {"words": ["zwischen"], "takes": [take("acc", "motion", TWO_WAY), take("dat", "rest", TWO_WAY)],
     "examples": [ex("Das Dorf liegt zwischen den Bergen.", "The village lies between the mountains.", "Bergen", "dat", "pl", "zwischen")]},
    # the genitive, and the dative people say (wegen dem Regen: right in talk; in Austria trotz dem too)
    {"words": ["wegen"], "takes": [take("gen", "other", "praepositionen-genitiv"), take("dat", "other", "praepositionen-genitiv", spoken=True)],
     "examples": [ex("Wegen des Regens bleiben wir zu Hause.", "Because of the rain we stay at home.", "Regens", "gen", "sg", "Wegen"), ex("Wegen dem Regen bleiben wir daheim.", "Because of the rain we stay at home.", "Regen", "dat", "sg", "Wegen")]},
    {"words": ["trotz"], "takes": [take("gen", "other", "praepositionen-genitiv"), take("dat", "other", "praepositionen-genitiv", spoken=True)],
     "examples": [ex("Trotz der Kälte arbeitet Toni draußen.", "Despite the cold Toni works outside.", "Kälte", "gen", "sg", "Trotz")]},
    {"words": ["während"], "takes": [take("gen", "time", "praepositionen-genitiv"), take("dat", "time", "praepositionen-genitiv", spoken=True)],
     "examples": [ex("Während des Essens redet niemand.", "Nobody talks during the meal.", "Essens", "gen", "sg", "Während")]},
    {"words": ["statt", "anstatt"], "takes": [take("gen", "other", "praepositionen-genitiv"), take("dat", "other", "praepositionen-genitiv", spoken=True)],
     "examples": [ex("Statt eines Briefes schreibt sie eine Karte.", "Instead of a letter she writes a card.", "Briefes", "gen", "sg", "Statt")]},
    {"words": ["innerhalb", "außerhalb", "oberhalb", "unterhalb", "diesseits", "jenseits"], "takes": [take("gen", "rest", "praepositionen-genitiv")],
     "examples": [ex("Die Mühle steht außerhalb des Dorfes.", "The mill stands outside the village.", "Dorfes", "gen", "sg", "außerhalb")]},
    {"words": ["aufgrund", "angesichts", "infolge", "anhand", "mithilfe", "bezüglich"], "takes": [take("gen", "other", "praepositionen-genitiv")],
     "examples": [ex("Aufgrund des Sturms fällt der Markt aus.", "Because of the storm the market is called off.", "Sturms", "gen", "sg", "Aufgrund")]},
    # either, both written: laut dem Bericht, laut des Berichts; dank dem Wetter, dank des Wetters
    {"words": ["laut", "dank"], "takes": [take("dat", "other", "praepositionen-genitiv"), take("gen", "other", "praepositionen-genitiv")],
     "examples": [ex("Dank dem schönen Wetter ernten wir heute.", "Thanks to the fine weather we harvest today.", "Wetter", "dat", "sg", "Dank")]},
]

# A preposition and the article as one word.
CONTRACTIONS = [
    {"form": "im", "preposition": "in", "article": "dem", "case": "dat", "genders": ["m", "n"], "usual": True},
    {"form": "am", "preposition": "an", "article": "dem", "case": "dat", "genders": ["m", "n"], "usual": True},
    {"form": "beim", "preposition": "bei", "article": "dem", "case": "dat", "genders": ["m", "n"], "usual": True},
    {"form": "vom", "preposition": "von", "article": "dem", "case": "dat", "genders": ["m", "n"], "usual": True},
    {"form": "zum", "preposition": "zu", "article": "dem", "case": "dat", "genders": ["m", "n"], "usual": True},
    {"form": "zur", "preposition": "zu", "article": "der", "case": "dat", "genders": ["f"], "usual": True},
    {"form": "ins", "preposition": "in", "article": "das", "case": "acc", "genders": ["n"], "usual": True},
    {"form": "ans", "preposition": "an", "article": "das", "case": "acc", "genders": ["n"], "usual": True},
    {"form": "aufs", "preposition": "auf", "article": "das", "case": "acc", "genders": ["n"]},
    {"form": "ums", "preposition": "um", "article": "das", "case": "acc", "genders": ["n"]},
    {"form": "durchs", "preposition": "durch", "article": "das", "case": "acc", "genders": ["n"]},
    {"form": "fürs", "preposition": "für", "article": "das", "case": "acc", "genders": ["n"]},
    {"form": "übers", "preposition": "über", "article": "das", "case": "acc", "genders": ["n"]},
    {"form": "unters", "preposition": "unter", "article": "das", "case": "acc", "genders": ["n"]},
    {"form": "vors", "preposition": "vor", "article": "das", "case": "acc", "genders": ["n"]},
    {"form": "hinters", "preposition": "hinter", "article": "das", "case": "acc", "genders": ["n"]},
    {"form": "überm", "preposition": "über", "article": "dem", "case": "dat", "genders": ["m", "n"]},
    {"form": "unterm", "preposition": "unter", "article": "dem", "case": "dat", "genders": ["m", "n"]},
    {"form": "vorm", "preposition": "vor", "article": "dem", "case": "dat", "genders": ["m", "n"]},
    {"form": "hinterm", "preposition": "hinter", "article": "dem", "case": "dat", "genders": ["m", "n"]},
    # spoken, never wrong: aufm (auf dem)
    {"form": "aufm", "preposition": "auf", "article": "dem", "case": "dat", "genders": ["m", "n"], "spoken": True},
]

PRONOUNS = [
    {"lemma": "ich", "person": 1, "number": "sg", "genders": ["m", "f", "n"], "forms": {"nom": ["ich"], "acc": ["mich"], "dat": ["mir"], "gen": ["meiner"]}},
    {"lemma": "du", "person": 2, "number": "sg", "genders": ["m", "f", "n"], "forms": {"nom": ["du"], "acc": ["dich"], "dat": ["dir"], "gen": ["deiner"]}},
    {"lemma": "er", "person": 3, "number": "sg", "genders": ["m"], "forms": {"nom": ["er"], "acc": ["ihn"], "dat": ["ihm"], "gen": ["seiner"]}},
    {"lemma": "sie", "person": 3, "number": "sg", "genders": ["f"], "forms": {"nom": ["sie"], "acc": ["sie"], "dat": ["ihr"], "gen": ["ihrer"]}},
    {"lemma": "es", "person": 3, "number": "sg", "genders": ["n"], "forms": {"nom": ["es"], "acc": ["es"], "dat": ["ihm"], "gen": ["seiner"]}},
    {"lemma": "wir", "person": 1, "number": "pl", "genders": ["m", "f", "n"], "forms": {"nom": ["wir"], "acc": ["uns"], "dat": ["uns"], "gen": ["unser"]}},
    {"lemma": "ihr", "person": 2, "number": "pl", "genders": ["m", "f", "n"], "forms": {"nom": ["ihr"], "acc": ["euch"], "dat": ["euch"], "gen": ["euer"]}},
    {"lemma": "sie", "person": 3, "number": "pl", "genders": ["m", "f", "n"], "forms": {"nom": ["sie"], "acc": ["sie"], "dat": ["ihnen"], "gen": ["ihrer"]}},
    # the reflexive (sich, never the subject), who (wer, wen, wem, wessen), man, jemand, niemand
    {"lemma": "sich", "person": 3, "number": "sg", "genders": ["m", "f", "n"], "forms": {"acc": ["sich"], "dat": ["sich"]}},
    {"lemma": "wer", "person": None, "number": "sg", "genders": ["m"], "forms": {"nom": ["wer"], "acc": ["wen"], "dat": ["wem"], "gen": ["wessen"]}},
    {"lemma": "man", "person": 3, "number": "sg", "genders": ["m"], "forms": {"nom": ["man"], "acc": ["einen"], "dat": ["einem"]}},
    {"lemma": "jemand", "person": 3, "number": "sg", "genders": ["m"], "forms": {"nom": ["jemand"], "acc": ["jemanden", "jemand"], "dat": ["jemandem", "jemand"], "gen": ["jemandes"]}},
    {"lemma": "niemand", "person": 3, "number": "sg", "genders": ["m"], "forms": {"nom": ["niemand"], "acc": ["niemanden", "niemand"], "dat": ["niemandem", "niemand"], "gen": ["niemandes"]}},
]

# The relative pronoun's own forms (where it isn't the article's): denen, dessen, deren (relativsatz).
RELATIVE = {
    "forms": {"nom.m": "der", "nom.f": "die", "nom.n": "das", "nom.pl": "die", "acc.m": "den", "acc.f": "die", "acc.n": "das", "acc.pl": "die",
              "dat.m": "dem", "dat.f": "der", "dat.n": "dem", "dat.pl": "denen", "gen.m": "dessen", "gen.f": "deren", "gen.n": "dessen", "gen.pl": "deren"},
    "rule": "relativsatz",
    "examples": [ex("Das ist der Mann, dem ich helfe.", "That's the man I'm helping.", "dem", "dat", "sg", "helfe"),
                 ex("Das sind die Kinder, denen der Hof gehört.", "Those are the children the farm belongs to.", "denen", "dat", "pl", "gehört")],
}

# The masculine nouns that take -n or -en in every case but the nominative singular (n-deklination); [genitive] where
# it adds -s too (des Namens); the neuter Herz (dem Herzen, des Herzens). In speech: dem Bub, den Mensch: never wrong.
N_NOUNS = [
    ("Herr", "Herrn", None, "Herren"), ("Mensch", "Menschen", None), ("Nachbar", "Nachbarn", None), ("Bauer", "Bauern", None),
    ("Bär", "Bären", None), ("Bub", "Buben", None), ("Held", "Helden", None), ("Prinz", "Prinzen", None), ("Graf", "Grafen", None),
    ("Fürst", "Fürsten", None), ("Narr", "Narren", None), ("Christ", "Christen", None),
    ("Junge", "Jungen", None), ("Kollege", "Kollegen", None), ("Kunde", "Kunden", None), ("Neffe", "Neffen", None),
    ("Hirte", "Hirten", None), ("Slowene", "Slowenen", None), ("Hase", "Hasen", None), ("Löwe", "Löwen", None),
    ("Affe", "Affen", None), ("Rabe", "Raben", None), ("Bote", "Boten", None), ("Zeuge", "Zeugen", None),
    ("Experte", "Experten", None), ("Riese", "Riesen", None), ("Ochse", "Ochsen", None), ("Franzose", "Franzosen", None),
    ("Russe", "Russen", None), ("Pole", "Polen", None), ("Grieche", "Griechen", None), ("Kroate", "Kroaten", None),
    ("Däne", "Dänen", None), ("Schwede", "Schweden", None), ("Biologe", "Biologen", None), ("Psychologe", "Psychologen", None),
    ("Student", "Studenten", None), ("Präsident", "Präsidenten", None), ("Patient", "Patienten", None), ("Assistent", "Assistenten", None),
    ("Tourist", "Touristen", None), ("Polizist", "Polizisten", None), ("Journalist", "Journalisten", None), ("Komponist", "Komponisten", None),
    ("Soldat", "Soldaten", None), ("Kandidat", "Kandidaten", None), ("Pilot", "Piloten", None), ("Fotograf", "Fotografen", None),
    ("Architekt", "Architekten", None), ("Elefant", "Elefanten", None), ("Planet", "Planeten", None), ("Automat", "Automaten", None),
    ("Kamerad", "Kameraden", None),
    ("Name", "Namen", "Namens"), ("Gedanke", "Gedanken", "Gedankens"), ("Buchstabe", "Buchstaben", "Buchstabens"),
    ("Glaube", "Glauben", "Glaubens"), ("Friede", "Frieden", "Friedens"), ("Funke", "Funken", "Funkens"), ("Wille", "Willen", "Willens"),
]

T = {
    "schema": "lani.case-triggers/v0",
    "language": "de",
    "machine_written": True,
    "review": "Machine-written (Claude, 2026-11-03), not reviewed by a native speaker yet: the lists, the senses and the examples want a native check (docs/plans/05-built/case-de.md, \"Native check\"). Austrian standard, as the course; a form people say is never wrong (wegen dem Regen, dem Bub, aufm): the lens reads it as what it is, the spoken ones marked so. The determiners' forms and the adjective endings follow the grammar book's pages artikel-genus, akkusativ, dativ, genitiv, possessiv, adjektiv-tabelle; the n-Deklination's nouns its page and Duden's lists.",
    "about": "What decides a German word's case, for the case lens (bridge/src/case-lens-de.ts) and the case trainer's checks (docs/plans/05-built/case-de.md): the four cases (their question words, their colours: the same as Slovene's), the article and the other determiners with all sixteen forms, the adjective endings after each (weak, mixed, strong), the prepositions by case (the two-way ones by motion: Wohin? and rest: Wo?; the genitive's, with the dative people say), the preposition fused with the article (im, zum, ins), the verbs with a dative, with a dative and an accusative, with a genitive, the verbs of motion and of rest, the copulas, the personal pronouns, the relative pronoun's own forms, the nouns of the n-Deklination, times without a preposition (jeden Tag, eines Tages) and fixed phrases (zu Hause, Guten Morgen). Every entry names its rule: the grammar book's page that explains it. Made by case-triggers.py beside it: change that and run it, never this file.",
    "cases": [
        {"key": "nom", "id": "nominativ", "n": 1, "questions": ["wer?", "was?"], "colour": "#4A7FC1", "page": "faelle"},
        {"key": "acc", "id": "akkusativ", "n": 4, "questions": ["wen?", "was?"], "colour": "#3E9B5F", "page": "akkusativ"},
        {"key": "dat", "id": "dativ", "n": 3, "questions": ["wem?"], "colour": "#D69A2D", "page": "dativ"},
        {"key": "gen", "id": "genitiv", "n": 2, "questions": ["wessen?"], "colour": "#C2503C", "page": "genitiv"},
    ],
    "determiners": DETERMINERS,
    "adjective_endings": ADJECTIVE_ENDINGS,
    # adjectives that drop an e before an ending (hoch: hohe; dunkel: dunkle; teuer: teure), and those that take none (rosa, lila)
    "adjective_stems": {"hoch": "hoh", "dunkel": "dunkl", "teuer": "teur", "sauer": "saur", "edel": "edl", "eitel": "eitl", "sensibel": "sensibl", "flexibel": "flexibl", "komfortabel": "komfortabl"},
    "adjective_fixed": ["rosa", "lila", "prima", "super", "klasse", "beige"],
    "prepositions": PREPOSITIONS,
    "contractions": CONTRACTIONS,
    "chunks": CHUNKS,
    "verbs": DATIVE_VERBS + GIVING_VERBS + GENITIVE_VERBS,
    "predicates": [
        {"words": ["kalt", "warm", "heiß", "langweilig", "schlecht", "übel", "schwindlig", "recht", "egal", "wohl"], "case": "dat", "rule": "dativ",
         "examples": [ex("Mir ist kalt.", "I'm cold.", "Mir", "dat", "sg", "kalt")]},
    ],
    "motion": MOTION,
    "rest": REST,
    "copulas": COPULAS,
    "pronouns": PRONOUNS,
    "relative": RELATIVE,
    "n_declension": [{"lemma": a, "oblique": b, **({"genitive": g} if g else {}), **({"plural": r[0]} if r else {})} for a, b, g, *r in N_NOUNS] + [{"lemma": "Herz", "gender": "n", "oblique": "Herzen", "accusative": "Herz", "genitive": "Herzens"}],
    "time": {
        # a time with no preposition, said by its determiner: the accusative (jeden Tag, nächste Woche, den ganzen Tag)
        "nouns": ["Tag", "Woche", "Monat", "Jahr", "Abend", "Morgen", "Vormittag", "Nachmittag", "Nacht", "Mittag", "Stunde", "Minute", "Mal", "Sommer", "Winter", "Frühling", "Herbst", "Wochenende",
                  "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag", "Jänner", "Januar", "Feber", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember"],
        "rule": "akkusativ",
        "examples": [ex("Jeden Tag melkt Franz die Kühe.", "Every day Franz milks the cows.", "Tag", "acc", "sg"), ex("Nächste Woche fahren wir nach Villach.", "Next week we go to Villach.", "Woche", "acc", "sg")],
    },
    # one day, one evening (when?): the genitive of time, a phrase (eines Nachts too, though Nacht is a die word)
    "time_genitive": {"phrases": ["eines tages", "eines abends", "eines morgens", "eines nachts", "eines sonntags", "eines winters"], "rule": "genitiv",
                      "examples": [ex("Eines Tages kam ein Fremder ins Dorf.", "One day a stranger came to the village.", "Tages", "gen", "sg")]},
    "phrases": [
        {"words": "zu hause", "case": "dat", "number": "sg", "trigger": "zu", "rule": "praepositionen-dativ"},
        {"words": "nach hause", "case": "dat", "number": "sg", "trigger": "nach", "rule": "praepositionen-dativ"},
        {"words": "zu haus", "case": "dat", "number": "sg", "trigger": "zu", "rule": "praepositionen-dativ"},
        {"words": "nach haus", "case": "dat", "number": "sg", "trigger": "nach", "rule": "praepositionen-dativ"},
        {"words": "guten morgen", "case": "acc", "number": "sg", "rule": "akkusativ"},
        {"words": "guten tag", "case": "acc", "number": "sg", "rule": "akkusativ"},
        {"words": "guten abend", "case": "acc", "number": "sg", "rule": "akkusativ"},
        {"words": "gute nacht", "case": "acc", "number": "sg", "rule": "akkusativ"},
        {"words": "guten appetit", "case": "acc", "number": "sg", "rule": "akkusativ"},
        {"words": "gute reise", "case": "acc", "number": "sg", "rule": "akkusativ"},
        {"words": "vielen dank", "case": "acc", "number": "sg", "rule": "akkusativ"},
        {"words": "herzlichen dank", "case": "acc", "number": "sg", "rule": "akkusativ"},
        {"words": "herzlichen glückwunsch", "case": "acc", "number": "sg", "rule": "akkusativ"},
        {"words": "schönen tag", "case": "acc", "number": "sg", "rule": "akkusativ"},
        {"words": "schönes wochenende", "case": "acc", "number": "sg", "rule": "akkusativ"},
        {"words": "frohe weihnachten", "case": "acc", "number": "pl", "rule": "akkusativ"},
        {"words": "frohe ostern", "case": "acc", "number": "pl", "rule": "akkusativ"},
        {"words": "viel glück", "case": "acc", "number": "sg", "rule": "akkusativ"},
        {"words": "viel spaß", "case": "acc", "number": "sg", "rule": "akkusativ"},
        {"words": "es gibt", "case": "acc", "number": "sg", "trigger": "gibt", "rule": "akkusativ"},
        {"words": "gibt es", "case": "acc", "number": "sg", "trigger": "gibt", "rule": "akkusativ"},
    ],
    "roles": {
        "subject": {"rule": "faelle", "examples": [ex("Der Teich ist heute ganz still.", "The pond is completely calm today.", "Teich", "nom", "sg")]},
        "predicate": {"rule": "faelle", "examples": [ex("Franz ist ein guter Bauer.", "Franz is a good farmer.", "Bauer", "nom", "sg", "ist")]},
        "object": {"rule": "akkusativ", "examples": [ex("Jakob sieht den Teich.", "Jakob sees the pond.", "Teich", "acc", "sg")]},
        "dative": {"rule": "dativ", "examples": [ex("Lena gibt dem Pferd einen Apfel.", "Lena gives the horse an apple.", "Pferd", "dat", "sg", "gibt")]},
        "attribute": {"rule": "genitiv", "examples": [ex("Das ist das Haus des Müllers.", "That's the miller's house.", "Müllers", "gen", "sg", "Haus")]},
        "address": {"rule": "faelle", "examples": [ex("Lieber Franz, danke für den Brief!", "Dear Franz, thanks for the letter!", "Franz", "nom", "sg")]},
    },
}


def fmt(x, ind=0):
    sp = '  ' * ind
    one = json.dumps(x, ensure_ascii=False)
    if not isinstance(x, (dict, list)): return one
    if isinstance(x, list) and all(not isinstance(y, (dict, list)) for y in x):
        if len(one) + len(sp) <= 118: return one
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
    ap = argparse.ArgumentParser(description="Write German's trigger table (case-triggers.json beside this file).")
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
    print(f"{a.out}: {len(T['prepositions'])} preposition groups, {len(T['contractions'])} contractions, {len(T['verbs'])} governing verbs, {len(T['determiners'])} determiners, {len(T['n_declension'])} n-nouns")
    return 0


if __name__ == "__main__":
    sys.exit(main())
