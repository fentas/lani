# ONE-OFF (supplement-it, 2026-11-02): makes the Italian supplement's new entries from the tables it_cov_*.txt, as
# it_extra_build.py did for the course's words (docs/plans/05-built/supplement-it.md). companion/lexicon/it.extra.json is the
# source afterwards: edit it by hand. Kept so the way the forms were made can be read and run again.
#
# A table's line:  lemma | pos | gender | paradigm | en ;; en2 | de ;; de2 | sl ;; sl2
#   the meanings written by hand, never copied from Wiktionary; prompts unique in each base (--prompts below)
#   "=pack" instead of the three meanings: the word is a pack's, the entry is there for the forms the pack lacks, with
#   the pack's own meanings; "~" instead of them: the entry is already in the file, only its forms are added
# paradigm: v (regular by its ending; -rsi reflexive), vi (-ire with -isc-), v:<irregular> or v:<prefix>+<irregular>
#   (paradigm.py), +bare after either on a reflexive verb that has no plain verb (its finite forms bare too: si
#   arrabbia and arrabbia), n (a noun, its plural by the ending), n:<plural>[/<plural2>], n= (no other form), a (adj in
#   -o), ac (magico, magici), a2 (adj in -e), a2:<before a noun>, a:bello, p:<verb>:<rest> (a phrase of a verb's forms
#   and the rest), - (only the word itself). After any: ;<form>@<grammar> more forms, kept when the content has them.
#
# Usage: python3 -I companion/lexicon/tools/it_cov_build.py <repo> <base it.extra.json> [--write] [--prompts] [--report <dir>]
#   <base>: the file before this round (git show main:companion/lexicon/it.extra.json), so a run starts over
import glob, json, os, re, sys, unicodedata
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paradigm import IRREGULAR, adj_forms, irregular, noun_forms, regular, verb_forms

args = sys.argv[1:]
root, base_path = args[0], args[1]
WRITE = '--write' in args
PROMPTS = '--prompts' in args
REPORT = args[args.index('--report') + 1] if '--report' in args else None
REMAINING = args[args.index('--remaining') + 1] if '--remaining' in args else None  # a JSON {word: count}: those no entry has yet
TOOLS = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(root, 'companion/lexicon/it.extra.json')


def key(s: str) -> str:
    """As the bridge compares Italian forms (lexicon.ts normalizeForm)."""
    lower = s.strip().lower().replace('’', "'").replace('ʼ', "'")
    nfd = unicodedata.normalize('NFD', lower)
    out = re.sub(r"[̀-ͯ](?=[̀-ͯ']*[^\W\d_])", '', nfd)
    return unicodedata.normalize('NFC', out)


# --- the content's Italian: the course, the grammar book, Friuli, the packs, scenes, scenarios, modules ----------------
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
tokens = set(re.findall(r"[^\W\d_]+(?:['-][^\W\d_]+)*'?", blob))
tokens |= set(re.findall(r'[^\W\d_]+', blob))
spaced = ' ' + re.sub(r"[^\w']+", ' ', blob) + ' '


def met(form: str) -> bool:
    k = key(form)
    if ' ' in k:
        return f' {k} ' in spaced
    return k in tokens


# --- the packs' words (their meanings, for "=pack" and for the prompts) ------------------------------------------------
ARTICLE = re.compile(r"^(?:(?:il|lo|la|i|gli|le|un|uno|una) +|(?:l|un)['’])", re.I)


def pack_key(w: str) -> str:
    w = re.sub(r'\{[mf]:([^{}|]*)\|[mf]:([^{}|]*)\}', r'\1', w)
    w = ARTICLE.sub('', w.strip())
    return key(re.sub(r"[^\w' -]", '', w)).strip()


packs = {}
pack_words = []
for f in sorted(glob.glob(os.path.join(root, 'companion/packs/it/*.json'))) + sorted(glob.glob(os.path.join(root, 'companion/cultures/friuli/packs/*.json'))):
    p = json.load(open(f))
    for w in p.get('words', []):
        if not isinstance(w.get('it'), str):
            continue
        pack_words.append(w)
        packs.setdefault(pack_key(w['it']), w)


# --- the tables -------------------------------------------------------------------------------------------------------
def forms_of(lemma: str, pos: str, spec: str) -> dict:
    spec, *more = spec.split(';')
    spec = spec.strip()
    extra = {}
    for m in more:
        f, _, g = m.strip().partition('@')
        extra[f.strip()] = g.strip() or None
    forms: dict = {}
    if pos == 'verb' and spec.startswith('v'):
        bare = spec.endswith('+bare')
        if bare:
            spec = spec[:-5]
        refl = lemma.endswith(('rsi', 'rmi'))
        inf = lemma[:-3] + 're' if refl else lemma
        p = irregular(spec[2:]) if spec.startswith('v:') else regular(inf, spec == 'vi')
        if refl and spec.startswith('v:'):
            p = {**p, 'inf': inf}
        allf = verb_forms(p, refl)
        if refl:
            allf[lemma] = 'infinitive'
        if refl and bare:
            for f, g in verb_forms({**p}, False).items():
                if ', with ' not in g:
                    allf.setdefault(f, g)
        forms = {f: g for f, g in allf.items() if met(f)}
        forms.setdefault(lemma, 'infinitive')
    elif spec.startswith('p:'):
        _, verb, rest = spec.split(':', 2)
        p = irregular(verb) if verb in IRREGULAR or '+' in verb else regular(verb)
        allf = {f: g for f, g in verb_forms(p, False).items() if ', with ' not in g}
        if rest.startswith('^'):
            allf = {f: g for f, g in allf.items() if g.startswith('third-person') or 'participle' in g or g == 'infinitive'}
        for f, g in allf.items():
            ph = f"{rest[1:]} {f}" if rest.startswith('^') else f"{f} {rest}"
            if met(ph):
                forms[ph] = g
        forms.setdefault(lemma, 'infinitive' if pos == 'verb' or lemma.split()[0] == verb else None)
        if pos == 'phrase':
            forms[lemma] = None
    elif pos == 'noun' and spec != '-':
        forms = {f: g for f, g in noun_forms(lemma, spec).items() if met(f) or f == lemma}
    elif spec.startswith('a'):
        forms = {f: g for f, g in adj_forms(lemma, spec).items() if met(f) or f == lemma}
    else:
        forms = {lemma: None}
    for f, g in extra.items():
        if met(f) or f == lemma:
            forms[f] = g
    # a form of words with an apostrophe as a line's words are joined ("un po'" → un po): the lookup finds it there too
    for f, g in list(forms.items()):
        if ' ' in f and "'" in f:
            forms.setdefault(re.sub(r'\s+', ' ', f.replace("'", ' ')).strip(), g)
    return forms


def senses(s: str) -> list:
    return [x.strip() for x in s.split(';;') if x.strip()]


ORDER = ['phrase', 'intj', 'adv', 'prep', 'conj', 'num', 'pron', 'det', 'noun', 'adj', 'verb', 'name']
tables = sorted(glob.glob(os.path.join(TOOLS, 'it_cov_*.txt')))
new_entries = []       # the new entries, in the order they go into the file
extend = {}            # (lemma, pos) of an entry in the file → the forms to add
problems = []
no_form = []
seen = set()
for path in tables:
    # it_cov_0*.txt keep their order (the articles before the pronouns they share a form with); the others go by part of speech
    ordered = os.path.basename(path).startswith('it_cov_0')
    block = []
    for n, line in enumerate(open(path), 1):
        line = line.rstrip('\n')
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        cols = [x.strip() for x in line.split('|')]
        where = f'{os.path.basename(path)}:{n}'
        if len(cols) == 5 and cols[4] in ('=pack', '~'):
            cols = cols[:4] + [cols[4]] * 3
        if len(cols) != 7:
            problems.append(f'{where}: {len(cols)} columns: {line[:80]}')
            continue
        lemma, pos, gender, spec, en, de, sl = cols
        if (lemma, pos) in seen:
            problems.append(f'{where}: {lemma} ({pos}) twice')
            continue
        seen.add((lemma, pos))
        try:
            forms = forms_of(lemma, pos, spec)
        except Exception as e:
            problems.append(f'{where}: {lemma}: {e!r}')
            continue
        if en == '~':
            extend[(lemma, pos)] = forms
            continue
        if en == '=pack':
            w = packs.get(pack_key(lemma))
            if not w:
                problems.append(f'{where}: {lemma}: no pack has it')
                continue
            en, de, sl = w['en'], w.get('de', w['en']), w.get('sl', w['en'])
            e = {'lemma': lemma, 'pos': pos, '_pack': True}
        else:
            e = {'lemma': lemma, 'pos': pos}
        if gender:
            e['gender'] = gender
        e['gloss'] = senses(en)
        e['forms'] = forms
        e['meanings'] = {'de': senses(de), 'sl': senses(sl)}
        if not [f for f in forms if met(f)]:
            no_form.append(f'{where}: {lemma}: no form of it is in the content')
        for x in e['gloss'] + e['meanings']['de'] + e['meanings']['sl']:
            if len(x) > 120:
                problems.append(f'{where}: {lemma}: a meaning over 120 characters: {x}')
        if not (1 <= len(e['gloss']) <= 3 and 1 <= len(e['meanings']['de']) <= 3 and 1 <= len(e['meanings']['sl']) <= 3):
            problems.append(f'{where}: {lemma}: 1 to 3 meanings in each base')
        for k, v in forms.items():
            if len(k) > 40 or (v is not None and len(v) > 160):
                problems.append(f'{where}: {lemma}: form too long: {k}')
        if pos not in ORDER:
            problems.append(f'{where}: {lemma}: pos {pos}')
        block.append(e)
    if not ordered:
        block.sort(key=lambda e: ORDER.index(e['pos']) if e['pos'] in ORDER else 99)
    new_entries.append((ordered, block))
# the ordered tables first, then the rest by part of speech
ordered_block = [e for o, b in new_entries if o for e in b]
rest = sorted([e for o, b in new_entries if not o for e in b], key=lambda e: ORDER.index(e['pos']) if e['pos'] in ORDER else 99)
new_entries = ordered_block + rest


# --- the file: its entries' text kept, the extended ones written anew, the new ones after them --------------------------
text = open(base_path).read()
old = json.loads(text)
start = text.index('"entries"')
start = text.index('[', start) + 1
chunks = []
i, depth, in_str, esc, begin = start, 0, False, False, None
while i < len(text):
    c = text[i]
    if in_str:
        if esc:
            esc = False
        elif c == '\\':
            esc = True
        elif c == '"':
            in_str = False
    elif c == '"':
        in_str = True
    elif c == '{':
        if depth == 0:
            begin = i
        depth += 1
    elif c == '}':
        depth -= 1
        if depth == 0:
            chunks.append(text[begin:i + 1])
    elif c == ']' and depth == 0:
        break
    i += 1
assert len(chunks) == len(old['entries']), (len(chunks), len(old['entries']))
d = lambda x: json.dumps(x, ensure_ascii=False)


def forms_lines(forms: dict) -> str:
    items = [f'{d(k.lower())}: {d(v)}' for k, v in forms.items()]
    lines, cur = [], ''
    for it in items:
        if cur and len(cur) + len(it) > 150:
            lines.append(cur)
            cur = ''
        cur = f'{cur}, {it}' if cur else it
    if cur:
        lines.append(cur)
    if len(lines) == 1:
        return '      "forms": { ' + lines[0] + ' },'
    return '      "forms": {\n' + ',\n'.join('        ' + l for l in lines) + '\n      },'


def render(e: dict) -> str:
    head = [f'"lemma": {d(e["lemma"])}', f'"pos": {d(e["pos"])}']
    if e.get('gender'):
        head.append(f'"gender": {d(e["gender"])}')
    if e.get('override'):
        head.append('"override": true')
    head.append(f'"gloss": {d(e["gloss"])}')
    m = e['meanings']
    return '    { ' + ', '.join(head) + ',\n' + forms_lines(e['forms']) + '\n      "meanings": { "de": ' + d(m['de']) + ', "sl": ' + d(m['sl']) + ' } }'


have = {(e['lemma'], e['pos']): i for i, e in enumerate(old['entries'])}
out_chunks = list(chunks)
added_forms = 0
for k, forms in extend.items():
    if k not in have:
        problems.append(f'{k}: "~" but no such entry in the file')
        continue
    e = old['entries'][have[k]]
    ks = {key(f) for f in e['forms']}
    add = {f: g for f, g in forms.items() if key(f) not in ks}
    if add:
        e['forms'] = {**e['forms'], **add}
        added_forms += len(add)
        out_chunks[have[k]] = render(e)
for e in new_entries:
    if (e['lemma'], e['pos']) in have:
        problems.append(f"{e['lemma']} ({e['pos']}): already in the file: use ~")
body = text[:start].rstrip() + '\n' + ',\n'.join([('    ' + c if not c.startswith(' ') else c) for c in out_chunks] + [render(e) for e in new_entries]) + '\n  ]\n}\n'
ABOUT = (
    " Since 2026-11-02 (supplement-it) also every other word of the Italian content a tap met nothing for, so that a tap means the word on a "
    "phone without Wiktionary: the function words (il, la, un, che, non, per, si), the articles and contractions, the clitics, the verbs and "
    "the forms the content uses, the nouns' plurals, the adjectives' endings, the sounds with a meaning (hm, shh, bzz), the places and "
    "stars, the Friulian words said to be Friulian and the Slovene words said to be Slovene in every base; the packs' words again where the "
    "pack lacks a form, with the pack's meanings; the meanings written, not copied, the forms from paradigms written by hand "
    "(docs/plans/05-built/supplement-it.md)."
)
old_about = old['meta']['about']
assert d(old_about) in body
if 'supplement-it' not in old_about:
    body = body.replace(d(old_about), d(old_about + ABOUT), 1)
parsed = json.loads(body)
for e in parsed['entries']:
    e.pop('_pack', None)

# --- the prompts: a card's prompt is its first meanings in the base without their notes (Words.meaning, card.py) --------
PAREN = re.compile(r'\s*\(([^)]*)\)')
PUNCT = re.compile(r'[.,!?;:¡¿"„“”«»]')
ARTS = {'en': r'(a|an|the|to) ', 'de': r'(der|die|das|ein|eine|einen|sich) ', 'sl': r'^$'}


def prompt(gloss: str, base: str) -> str:
    g = PAREN.sub('', gloss).strip() or gloss
    parts = []
    for p in re.split(r' / |[,;]', g):
        p = re.sub(r'\s+', ' ', PUNCT.sub('', p.lower())).strip()
        p = re.sub('^' + ARTS[base], '', p).strip() if base != 'sl' else p
        if p:
            parts.append(p)
    return ' | '.join(sorted(set(parts)))


def answer(gl: list) -> str:
    out = ''
    for g in gl[:3]:
        nxt = f'{out}, {g}' if out else g
        if len(nxt) > 200:
            break
        out = nxt
    return out or gl[0][:200]


clashes = []
if PROMPTS:
    new_ids = {(e['lemma'], e['pos']) for e in new_entries if not e.get('_pack')}
    for base in ('de', 'sl', 'en'):
        by = {}
        for w in pack_words:
            m = w.get(base) or w['en']
            m = re.sub(r'\{[mf]:([^{}|]*)\|[mf]:([^{}|]*)\}', r'\1', m)
            by.setdefault(prompt(m, base), []).append(('pack', pack_key(w['it'])))
        for e in parsed['entries']:
            gl = e['gloss'] if base == 'en' else e['meanings'].get(base, e['gloss'])
            by.setdefault(prompt(answer(gl), base), []).append(('new' if (e['lemma'], e['pos']) in new_ids else 'old', e['lemma']))
        for pr, who in by.items():
            lemmas = {key(l) for _, l in who}
            if len(lemmas) > 1 and any(k == 'new' for k, _ in who):
                clashes.append(f"{base}: {pr!r}: " + ', '.join(sorted({f'{l} [{k}]' for k, l in who})))

n_forms = sum(len(e['forms']) for e in new_entries)
print(f"{len(tables)} tables: {len(new_entries)} new entries ({sum(1 for e in new_entries if e.get('_pack'))} of a pack's word), {n_forms} forms; {len(extend)} old entries extended with {added_forms} forms; {len(parsed['entries'])} in all")
for p in problems:
    print('PROBLEM', p)
for c in clashes:
    print('CLASH', c)
if PROMPTS:
    print(f'{len(clashes)} prompt clashes')
if REMAINING:
    known = {key(f) for e in parsed['entries'] for f in e['forms']}
    words = json.load(open(REMAINING))
    words = words.get('miss', words)
    left = {w: n for w, n in words.items() if w not in known}
    print(f"{len(left)} of {len(words)} words left, {sum(left.values())} tokens: " + ', '.join(f'{w} {n}' for w, n in left.items()))
if REPORT:
    os.makedirs(REPORT, exist_ok=True)
    open(os.path.join(REPORT, 'no-form.txt'), 'w').write('\n'.join(no_form) + '\n')
    # the forms with more than one reading, the first what a tap shows when the line doesn't tell
    by_form = {}
    for e in parsed['entries']:
        for f in e['forms']:
            by_form.setdefault(key(f), []).append(f"{e['lemma']}/{e['pos']}")
    with open(os.path.join(REPORT, 'homographs.txt'), 'w') as o:
        for f, ls in sorted(by_form.items()):
            if len(set(ls)) > 1:
                o.write(f"{f}: {', '.join(ls)}\n")
    # the check against Wiktionary's build (forms only, never its meanings): a form it has under another lemma only
    wp = '/mnt/scratch/github/fentas/lani-itlex-work/out/it.json'
    if os.path.exists(wp):
        w = json.load(open(wp))
        WF, WL = w['forms'], w['lemmas']
        with open(os.path.join(REPORT, 'wiktionary.txt'), 'w') as o:
            for e in new_entries:
                if ' ' in e['lemma']:
                    continue
                plain = e['lemma'][:-3] + 're' if e['pos'] == 'verb' and e['lemma'].endswith('rsi') else e['lemma']
                for f, g in e['forms'].items():
                    if ' ' in f or (g and ', with ' in g):
                        continue
                    rs = {WL[r[0]]['lemma'] for r in WF.get(key(f), []) if r[0] < len(WL)}
                    if key(plain) not in {key(x) for x in rs}:
                        o.write(f"{e['lemma']} ({e['pos']}): {f}: Wiktionary has it under {sorted(rs)[:5] or 'nothing'}\n")
if WRITE and not problems:
    open(OUT, 'w').write(body)
    print('written', OUT)
elif WRITE:
    print('NOT written: problems above')
    sys.exit(1)
