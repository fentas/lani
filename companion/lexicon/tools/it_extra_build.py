# ONE-OFF (it-print-meanings, 2026-11-01): these scripts made the first version of the Italian course's entries in
# companion/lexicon/it.extra.json (271 entries after its first 38). That file is the source now: edit it by hand, as the
# rest of the supplement. Kept so the way the forms were made can be read and run again for new words; a run adds only
# entries the file doesn't have (it_extra_merge.py), never changes one. See docs/plans/05-built/it-print-meanings.md.
#
# Builds the entries of it_words.py for companion/lexicon/it.extra.json: each word's forms the Italian content uses
# (the course, the grammar book, the Friuli culture, the packs, the scenarios), checked against Wiktionary's build
# (kaikki.org: the it-lexicon round's it.json, a download, not in the repository); the report lists the forms Wiktionary
# doesn't have under the same lemma and the forms another word shares (messa, girino).
# Usage: python3 -I companion/lexicon/tools/it_extra_build.py <repository> <it.json> <out.json> <report.txt>
#        python3 -I companion/lexicon/tools/it_extra_merge.py companion/lexicon/it.extra.json <out.json>
import json, os, re, sys, unicodedata
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from it_words import ENTRIES
from paradigm import adj_forms, irregular, noun_forms, regular, verb_forms

root, wik_path, out_path, report_path = sys.argv[1:5]


def key(s: str) -> str:
    """As the bridge compares Italian forms (lexicon.ts normalizeForm)."""
    lower = s.strip().lower().replace('’', "'").replace('ʼ', "'")
    nfd = unicodedata.normalize('NFD', lower)
    out = re.sub(r"[̀-ͯ](?=[̀-ͯ']*[^\W\d_])", '', nfd)
    return unicodedata.normalize('NFC', out)


# --- the content's Italian -------------------------------------------------------------------------------------------
SKIP = {'de', 'sl', 'en', 'review', 'id', 'schema', 'emoji', 'machine_written', 'source', 'sources', 'voice', 'image'}


def texts(x, k=''):
    if isinstance(x, dict):
        for kk, v in x.items():
            if kk in SKIP or re.search(r'_(de|sl|en)$', kk):
                continue
            yield from texts(v, kk)
    elif isinstance(x, list):
        for v in x:
            yield from texts(v, k)
    elif isinstance(x, str):
        yield x


def files():
    c = os.path.join(root, 'companion')
    dirs = [('course/it', True), ('grammar/it', False), ('cultures/friuli', True), ('packs/it', False), ('scenes/it', False), ('scenarios/it', True), ('modules/it', False)]
    for d, deep in dirs:
        p = os.path.join(c, d)
        if not os.path.isdir(p):
            continue
        for dp, _, fs in os.walk(p):
            for f in fs:
                if f.endswith('.json'):
                    yield os.path.join(dp, f)
            if not deep:
                break


corpus_text = []
for f in files():
    try:
        j = json.load(open(f))
    except Exception:
        continue
    for t in texts(j):
        t = re.sub(r'\{[mf]:([^{}|]*)\|[mf]:([^{}|]*)\}', r' \1 \2 ', t)
        corpus_text.append(key(re.sub(r'[*_⟦⟧]', ' ', t)))
blob = '\n'.join(corpus_text)
tokens = set(re.findall(r"[^\W\d_]+(?:'[^\W\d_]+)*'?", blob))
tokens |= set(re.findall(r'[^\W\d_]+', blob))
spaced = ' ' + re.sub(r"[^\w']+", ' ', blob) + ' '


def met(form: str) -> bool:
    k = key(form)
    if ' ' in k:
        return f' {k} ' in spaced
    return k in tokens


# --- the entries -----------------------------------------------------------------------------------------------------
wik = json.load(open(wik_path))
WF, WL = wik['forms'], wik['lemmas']


def wik_lemmas(form):
    return {(WL[r[0]]['lemma'], WL[r[0]]['pos']) for r in WF.get(key(form), []) if r[0] < len(WL)}


report = []
entries = []
for line in ENTRIES.strip().splitlines():
    if not line.strip():
        continue
    lemma, pos, gender, spec, en, de, sl = [x.strip() for x in line.split('|')]
    senses = lambda s: [x.strip() for x in s.split(';;')]
    forms: dict = {}
    if spec.startswith('x:'):
        from it_words import OWN_FORMS
        forms = {f: g for f, g in OWN_FORMS[spec[2:]].items() if met(f) or f == lemma}
    elif pos == 'verb' and spec.startswith('v'):
        refl = lemma.endswith('rsi')
        inf = lemma[:-3] + 're' if refl else lemma
        p = irregular(spec[2:]) if spec.startswith('v:') else regular(inf, spec == 'vi')
        if refl and spec.startswith('v:'):
            p = {**p, 'inf': inf}
        allf = verb_forms(p, refl)
        if refl:
            allf[lemma] = 'infinitive'
        forms = {f: g for f, g in allf.items() if met(f)}
        forms.setdefault(lemma, 'infinitive')
    elif spec.startswith('p:'):
        _, verb, rest = spec.split(':', 2)
        p = irregular(verb) if verb in __import__('paradigm').IRREGULAR else regular(verb)
        allf = {f: g for f, g in verb_forms(p, False).items() if ', with ' not in g}
        if rest.startswith('^'):
            # ci vuole, ci vogliono: volerci is said in the third person only (ci voglio andare is volere)
            allf = {f: g for f, g in allf.items() if g.startswith('third-person') or 'participle' in g or g == 'infinitive'}
        for f, g in allf.items():
            ph = f"{rest[1:]} {f}" if rest.startswith('^') else f"{f} {rest}"
            if met(ph):
                forms[ph] = g
        forms.setdefault(lemma, 'infinitive' if pos == 'verb' or lemma.split()[0] == verb else None)
        if pos == 'phrase':
            forms[lemma] = None
    elif pos == 'noun':
        allf = noun_forms(lemma, spec)
        forms = {f: g for f, g in allf.items() if met(f) or f == lemma}
    elif spec.startswith('a'):
        allf = adj_forms(lemma, spec)
        forms = {f: g for f, g in allf.items() if met(f) or f == lemma}
    else:
        forms = {lemma: None}
        # a phrase with an apostrophe as the line's words are joined ("un po'" → un po): the lookup finds it there too
        bare = re.sub(r"'", ' ', lemma).strip()
        bare = re.sub(r'\s+', ' ', bare)
        if ' ' in lemma and bare != lemma:
            forms[bare] = None
    # a form of words with an apostrophe as a line's words are joined ("ce l'ho fatta" → ce l ho fatta, "un po'" → un
    # po): the lookup finds the expression around a tapped word that way too
    for f, g in list(forms.items()):
        if ' ' in f and "'" in f:
            forms.setdefault(re.sub(r'\s+', ' ', f.replace("'", ' ')).strip(), g)
    e = {'lemma': lemma, 'pos': pos}
    if gender:
        e['gender'] = gender
    e['gloss'] = senses(en)
    e['forms'] = forms
    e['meanings'] = {'de': senses(de), 'sl': senses(sl)}
    entries.append(e)
    # the check against Wiktionary: each form a reading of the lemma there
    if ' ' not in lemma:
        plain = lemma[:-3] + 're' if pos == 'verb' and lemma.endswith('rsi') else lemma
        ok = {lemma, lemma.lower(), plain}
        if pos == 'verb':
            pp = next((f for f, g in forms.items() if g == 'past participle'), None)
            if pp:
                ok.add(pp)
        def known(f):
            last = f.split()[-1]
            return any(l in ok for l, _ in wik_lemmas(f)) or (' ' in f and any(l in ok or l.endswith(('ato', 'uto', 'ito')) for l, _ in wik_lemmas(last)))
        missing = [f for f, g in forms.items() if ', with ' not in (g or '') and not known(f)]
        # a form that is another word's too (messa: the church's mass; girino: a tadpole): the lookup shows both
        others = sorted({f'{f}={l}/{p}' for f in forms if ' ' not in f for l, p in wik_lemmas(f) if l not in ok and p in ('noun', 'adj', 'adv', 'num', 'intj', 'prep', 'conj')})
        if others:
            report.append(f"{lemma} ({pos}): also another word in Wiktionary: {', '.join(others)}")
        if missing:
            report.append(f"{lemma} ({pos}): not a form of {lemma} in Wiktionary: {', '.join(missing)}")
        if not wik_lemmas(lemma):
            report.append(f"{lemma} ({pos}): Wiktionary has no {lemma}")
    else:
        have = wik_lemmas(lemma)
        report.append(f"{lemma} ({pos}): phrase, {'in Wiktionary as ' + ', '.join(sorted(p for _, p in have)) if have else 'not in Wiktionary'}")
    met_forms = [f for f in forms if met(f)]
    if not met_forms:
        report.append(f"{lemma}: no form met in the content")

for e in entries:
    for k, v in e['forms'].items():
        assert len(k) <= 40, k
        assert v is None or len(v) <= 160, (k, v)
    for g in e['gloss'] + e['meanings']['de'] + e['meanings']['sl']:
        assert len(g) <= 120, g
    assert len(e['gloss']) <= 3

# the order the lookup shows a form's readings in when nothing else tells (no translation): an expression, an
# interjection, an adverb, … before a verb whose form it shares (prima the adverb before primo; le fate before fare's
# fate; voglia the noun before volere's), but "fa" the verb's (fa freddo) before the adverb (due anni fa)
ORDER = ['phrase', 'intj', 'adv', 'prep', 'conj', 'num', 'pron', 'det', 'noun', 'adj', 'verb']
entries.sort(key=lambda e: (e['lemma'] == 'fa' and e['pos'] == 'adv', ORDER.index(e['pos'])))
json.dump(entries, open(out_path, 'w'), ensure_ascii=False, indent=1)
open(report_path, 'w').write('\n'.join(report) + '\n')
print(len(entries), 'entries,', sum(len(e['forms']) for e in entries), 'forms;', len(report), 'report lines')
