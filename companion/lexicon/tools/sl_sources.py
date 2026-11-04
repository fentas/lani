#!/usr/bin/env python3
# Settles the Slovene of the B1/B2 word packs against the two sources the content's Slovene is checked with
# (packs-sl, 2026-11-03; the kit's native-check.md): fran.si's Slovenski pravopis 2001 (SP2001: the standard spelling,
# the gender, the genitive, a qualifier such as pog. or nar.) and Sloleks (viri.cjvt.si/sloleks: the whole paradigm,
# each form with its MULTEXT-East/JOS tag). Polite: one request at a time, at least 3.5 s apart, every answer cached
# (--cache, default .cache/sl-sources in the repository, not committed), so a run again asks nothing it asked before.
# viri.cjvt.si/sloleks is Sloleks 2.0, CC BY-NC-SA 4.0 (non-commercial), as its pages say: use `supplement` for no new
# forms; sloleks3.py reads Sloleks 3.0, CC BY-SA 4.0 (docs/plans/05-built/cleanup-code.md, "Sloleks").
#
#   python3 companion/lexicon/tools/sl_sources.py check [--level B1,B2] [--offline]   fetch what's missing, report
#   python3 companion/lexicon/tools/sl_sources.py supplement [--level B1,B2] [--write] the entries the supplement lacks
#
# check: for each pack word (its content words: "strinjati se" asks strinjati), SP2001's head and Sloleks's entry; it
# reports a word neither knows, a gender or a plural the sources don't give, and a qualifier (pog., nar., star., …).
# supplement: an entry for each pack word whose lemma neither the dictionary (sl.json) nor the supplement has, in the
# supplement's own format (companion/lexicon/sl.extra.json: the forms with their grammar, the pack's meanings, the source
# with both URLs). A form that is another lemma's in the dictionary or the supplement is left out (a tap on it keeps
# its reading: the supplement's readings come first), and listed.
import json, os, re, sys, time, glob, html, hashlib, urllib.parse, urllib.request, collections

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../..'))
C = os.path.join(ROOT, 'companion')
CACHE = os.path.join(ROOT, '.cache/sl-sources')
UA = 'Mozilla/5.0 (Lani content check, one request at a time; jan.guth@lanisce.si)'
GAP = 2.0
_last = [0.0]
OFFLINE = '--offline' in sys.argv


def fetch(url):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, hashlib.sha1(url.encode()).hexdigest())
    if os.path.exists(path):
        return open(path, encoding='utf8').read()
    if OFFLINE:
        return None
    wait = _last[0] + GAP - time.time()
    if wait > 0:
        time.sleep(wait)
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': UA})
            with urllib.request.urlopen(req, timeout=40) as r:
                body = r.read().decode('utf8', 'replace')
            _last[0] = time.time()
            break
        except urllib.error.HTTPError as e:
            _last[0] = time.time()
            if e.code == 404:
                body = ''
                break
            time.sleep(10 * (attempt + 1))
        except Exception:
            _last[0] = time.time()
            time.sleep(10 * (attempt + 1))
    else:
        return None
    with open(path + '.tmp', 'w', encoding='utf8') as f:
        f.write(body)
    os.replace(path + '.tmp', path)
    return body


def fran_url(q, dic=134):
    return f'https://fran.si/iskanje?View=1&Query={urllib.parse.quote(q)}&FilteredDictionaryIds={dic}'


def strip_accents(s):
    import unicodedata
    out = []
    for ch in unicodedata.normalize('NFD', s):
        if unicodedata.category(ch) == 'Mn' and ch not in '̌':  # keep the háček (č š ž)
            continue
        out.append(ch)
    return unicodedata.normalize('NFC', ''.join(out)).replace('ə', 'e')


def sp2001(q):
    """SP2001's entries for [q]: [{head, word, qualifiers}] (the head as text, its headword without accents)."""
    body = fetch(fran_url(q))
    if body is None:
        return None
    out = []
    for part in body.split('<div class="entry-content">')[1:]:
        part = part.split('<p class="entry-citation"')[0]
        word = re.search(r'class="font_xlarge"><a[^>]*>(.*?)</a>', part, re.S)
        text = html.unescape(re.sub(r'<[^>]+>', ' ', part))
        text = re.sub(r'\s+', ' ', text).strip()
        w = html.unescape(re.sub(r'<[^>]+>', '', word.group(1))).strip() if word else ''
        quals = sorted(set(re.findall(r'\b(pog|nar|star|žarg|ekspr|slabš|neknj|knj\. pog|zastar|redk|vulg|evfem|otr)\.', text)))
        out.append({'head': text[:240], 'word': strip_accents(w), 'qualifiers': quals})
    return out


def sloleks_search(lemma):
    body = fetch('https://viri.cjvt.si/sloleks/ajax_api/v1/slv/search_exact/' + urllib.parse.quote(lemma))
    if not body:
        return [] if body == '' else None
    try:
        return json.loads(body)
    except Exception:
        return None


def sloleks_url(i):
    return f'https://viri.cjvt.si/sloleks/slv/headword/{i}'


def sloleks_forms(i):
    """[(form, msd)] of a Sloleks headword, in the page's order."""
    body = fetch(sloleks_url(i))
    if not body:
        return None
    out = []
    for m in re.finditer(r'MainWord\.Form=([^&"]+)&[^"]*?MainWord\.Lemma=([^&"]+)&[^"]*?Msds%5B0%5D=([A-Za-z\-]+)', body):
        p = (html.unescape(urllib.parse.unquote(m.group(1))), m.group(3))
        # the page's labels, not forms (six went into the supplement so)
        if p[0].lower() in ('variantno', 'nestandardno'):
            continue
        if p not in out:
            out.append(p)
    return out


CASES = {'i': 'nominative', 'r': 'genitive', 'd': 'dative', 't': 'accusative', 'm': 'locative', 'o': 'instrumental'}
CASE_ORDER = list(CASES.values())
NUM = {'e': 'singular', 'd': 'dual', 'm': 'plural'}
NUM_ORDER = list(NUM.values())
GEN = {'m': 'masculine', 'z': 'feminine', 's': 'neuter'}
GEN_ORDER = list(GEN.values())
PERS = {'p': 'first', 'd': 'second', 't': 'third'}


def join_cases(cases):
    return '/'.join(c for c in CASE_ORDER if c in cases)


def noun_label(tags):
    # tags: [(number, case)] → "genitive singular; nominative/accusative plural"
    by = collections.defaultdict(set)
    for n, c in tags:
        by[n].add(c)
    return '; '.join(f'{join_cases(by[n])} {n}' for n in NUM_ORDER if n in by)


def adj_label(tags):
    # tags: [(degree, definite, number, case, gender)]
    groups = collections.defaultdict(lambda: collections.defaultdict(set))  # (deg, def, num) → case → genders
    for deg, de, n, c, g in tags:
        groups[(deg, de, n)][c].add(g)
    parts = []
    for key in sorted(groups, key=lambda k: (['', 'comparative', 'superlative'].index(k[0]), k[1], NUM_ORDER.index(k[2]))):
        deg, de, n = key
        by_g = collections.defaultdict(set)
        for c, gs in groups[key].items():
            by_g['/'.join(g for g in GEN_ORDER if g in gs)].add(c)
        for gs in sorted(by_g, key=lambda s: GEN_ORDER.index(s.split('/')[0])):
            label = f'{join_cases(by_g[gs])} {gs} {n}' + (f' {deg}' if deg else '') + (' definite' if de else '')
            parts.append(label)
    return '; '.join(parts)


def verb_label(tags):
    out = []
    pres = collections.defaultdict(set)
    imp = collections.defaultdict(set)
    part = collections.defaultdict(set)
    for t in tags:
        if t[0] in ('infinitive', 'supine', 'past participle'):
            out.append(t[0])
        elif t[0] in ('present', 'future', 'imperative'):
            (pres if t[0] != 'imperative' else imp)[(t[0], t[2])].add(t[1])
        elif t[0] == 'l-participle':
            part[t[1]].add(t[2])  # number → genders
    for (tense, n), ps in sorted(pres.items(), key=lambda x: (x[0][0], NUM_ORDER.index(x[0][1]))):
        out.append(f'{"/".join(p for p in ["first", "second", "third"] if p in ps)}-person {n} {tense}')
    for (tense, n), ps in sorted(imp.items(), key=lambda x: NUM_ORDER.index(x[0][1])):
        out.append(f'{"/".join(p for p in ["first", "second", "third"] if p in ps)}-person {n} imperative')
    for n in NUM_ORDER:
        if n in part:
            out.append(f'{"/".join(g for g in GEN_ORDER if g in part[n])} {n} l-participle')
    seen = []
    for x in out:
        if x not in seen:
            seen.append(x)
    return '; '.join(seen)


def labelled(forms, category):
    """{form: grammar} from Sloleks's (form, msd) pairs, as the supplement writes them."""
    by = collections.defaultdict(list)
    kind = None
    for form, msd in forms:
        f = form.lower() if category != 'proper' else form
        if msd.startswith('S'):  # noun: S type gender number case [animate]
            kind = 'noun'
            if len(msd) >= 5:
                by[f].append((NUM.get(msd[3], '?'), CASES.get(msd[4], '?')))
        elif msd.startswith('P'):  # adjective: P type degree gender number case definite
            if category == 'verb':
                if msd[1] == 'd' and msd[3:6] == 'mei':
                    by[f].append(('past participle',))
                continue
            kind = 'adj'
            if len(msd) >= 6:
                deg = {'n': '', 'p': 'comparative', 's': 'superlative'}.get(msd[2], '')
                de = len(msd) > 6 and msd[6] == 'd'
                by[f].append((deg, de, NUM.get(msd[4], '?'), CASES.get(msd[5], '?'), GEN.get(msd[3], '?')))
        elif msd.startswith('G'):  # verb: G type aspect vform person number gender negative
            kind = 'verb'
            v = msd[3] if len(msd) > 3 else ''
            if v == 'n':
                by[f].append(('infinitive',))
            elif v == 'm':
                by[f].append(('supine',))
            elif v in ('s', 'p') and len(msd) >= 6:
                if len(msd) > 6 and msd[6] == 'd':
                    continue  # the negative present (nisem, nočem): its own words
                by[f].append(({'s': 'present', 'p': 'future'}[v], PERS.get(msd[4], '?'), NUM.get(msd[5], '?')))
            elif v == 'v' and len(msd) >= 6:
                by[f].append(('imperative', PERS.get(msd[4], '?'), NUM.get(msd[5], '?')))
            elif v == 'd' and len(msd) >= 7:
                by[f].append(('l-participle', NUM.get(msd[5], '?'), GEN.get(msd[6], '?')))
        elif msd.startswith('R'):  # adverb: R type degree
            kind = 'adv'
            by[f].append(({'n': 'positive', 'r': 'comparative', 's': 'superlative'}.get(msd[2] if len(msd) > 2 else 'n', 'positive'),))
        else:
            by[f].append(('',))
    out = {}
    for f, tags in by.items():
        if kind == 'noun' or (tags and len(tags[0]) == 2 and category == 'noun'):
            out[f] = noun_label(tags)
        elif kind == 'adj' and category != 'verb':
            out[f] = adj_label(tags)
        elif category == 'verb':
            out[f] = verb_label(tags)
        elif kind == 'adv':
            degs = [t[0] for t in tags]
            out[f] = '; '.join(d for d in ['comparative', 'superlative'] if d in degs) or 'adverb'
        else:
            out[f] = ''
    return {f: g for f, g in out.items() if g != ''} if category in ('noun', 'adjective', 'verb') else out


POS = {'noun': 'noun', 'verb': 'verb', 'adjective': 'adj', 'adverb': 'adv', 'conjunction': 'conj', 'preposition': 'prep',
       'particle': 'particle', 'pronoun': 'pron', 'numeral': 'num', 'interjection': 'intj'}
GENDER = {'masculine': 'm', 'feminine': 'f', 'neuter': 'n'}


def render(s, g='m'):
    s = re.sub(r'\{([mf]):([^{}|]*)\|([mf]):([^{}|]*)\}', lambda m: m.group(2) if m.group(1) == g else m.group(4), s)
    return re.sub(r'\{learner(?::\w+)?\}', 'Jan', s)


SKIP = {'se', 'si', 'je', 'biti', 'na', 'v', 'za', 'po', 'z', 's', 'do', 'od', 'pri', 'o', 'iz', 'k', 'h', 'ne', 'in', 'pa', 'kaj', 'ki', 'da', 'to'}


def lemma_queries(w):
    """The words of a pack word to look up: itself, or (a phrase) its content words."""
    s = re.sub(r'\s*\([^)]*\)', '', render(w['sl'])).strip().rstrip('!?.').strip()
    words = s.split()
    if len(words) == 1:
        return [words[0]]
    return [x for x in words if x.lower() not in SKIP]


EXTRA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'sl_extra_words')


def extra_records():
    """The other words the examples use, written by hand (sl_extra_words/*.json: [{lemma, pos, en, de, it}])."""
    out = []
    for f in sorted(glob.glob(os.path.join(EXTRA_DIR, '*.json'))):
        try:
            out += json.load(open(f, encoding='utf8'))
        except Exception as e:
            print(f'✖ {f}: {e}')
    return out


SL_CAT = {'noun': 'noun', 'verb': 'verb', 'adj': 'adjective', 'adv': 'adverb', 'conj': 'conjunction', 'prep': 'preposition',
          'particle': 'particle', 'pron': 'pronoun', 'num': 'numeral', 'intj': 'interjection'}


def cached_forms(lemma, pos=None):
    """The forms Sloleks gives [lemma] (of [pos]), from the cache only: what its supplement entry would know."""
    global OFFLINE
    was, OFFLINE = OFFLINE, True
    try:
        sl = sloleks_search(lemma) or []
        out = set()
        for e in sl:
            if e.get('lemma', '').lower() != lemma.lower() or (pos and e.get('category') != SL_CAT.get(pos, pos)):
                continue
            for form, msd in sloleks_forms(e['id']) or []:
                out.add(form.lower())
        return out
    finally:
        OFFLINE = was


def new_packs(levels):
    out = []
    for f in sorted(glob.glob(os.path.join(C, 'packs/*.json'))) + sorted(glob.glob(os.path.join(C, 'cultures/primorska/packs/*.json'))):
        try:
            p = json.load(open(f))
        except Exception:
            continue
        if p.get('level') in levels:
            out.append((f, p))
    return out


_wiki = None


def wiki_forms(lemma):
    """The forms the dictionary (sl.json, Wiktionary) has of [lemma]: {form: grammar}, or None if it lacks the lemma."""
    global _wiki
    if _wiki is None:
        d = json.load(open(os.path.join(C, 'lexicon/sl.json')))
        lem, gram = d['lemmas'], d['grammar']
        _wiki = collections.defaultdict(dict)
        for l in lem:
            _wiki[l['lemma'].lower()].setdefault(l['lemma'].lower(), '')
        for form, refs in d['forms'].items():
            for r in refs:
                g = gram[r[1]] if len(r) > 1 and r[1] < len(gram) else ''
                _wiki[lem[r[0]]['lemma'].lower()][form.lower()] = g
    return _wiki.get(lemma.lower())


def look(q):
    """
    Both sources for one word: (sp2001 entries, sloleks entries, {id: forms}). A word the dictionary has already
    (sl.json) gets no Sloleks forms page unless it is cached: its forms are the dictionary's.
    """
    sp = sp2001(q)
    sl = sloleks_search(q)
    forms = {}
    have = wiki_forms(q) is not None
    for e in sl or []:
        if e.get('lemma') == q:
            if have:
                global OFFLINE
                was, OFFLINE = OFFLINE, True
                try:
                    f = sloleks_forms(e['id'])
                finally:
                    OFFLINE = was
                if f:
                    forms[e['id']] = f
            else:
                forms[e['id']] = sloleks_forms(e['id'])
    return sp, sl, forms


def sloleks_pass(levels):
    """Sloleks first (the forms the supplement needs), for every word and record; fran.si comes after, in check()."""
    qs = []
    for f, p in new_packs(levels):
        for w in p['words']:
            qs += lemma_queries(w)
    qs += [r['lemma'] for r in extra_records()]
    for q in dict.fromkeys(qs):
        sl = sloleks_search(q)
        have = wiki_forms(q) is not None
        for e in sl or []:
            if e.get('lemma') == q and not have:
                sloleks_forms(e['id'])


def check(levels):
    rows = []
    for f, p in new_packs(levels):
        for w in p['words']:
            for q in lemma_queries(w):
                sp, sl, forms = look(q)
                rows.append((p['id'], w, q, sp, sl, forms))
    for r in extra_records():
        look(r['lemma'])
    report = collections.defaultdict(list)
    for pid, w, q, sp, sl, forms in rows:
        tag = f'{pid}: {w["sl"]} ({q})'
        if sp is None or sl is None:
            report['not fetched'].append(tag)
            continue
        sp_hit = [e for e in sp if e['word'].lower() == q.lower()]
        sl_hit = [e for e in sl if e.get('lemma', '').lower() == q.lower()]
        if not sp_hit and not sl_hit:
            report['in neither source'].append(tag)
            continue
        if not sp_hit:
            report['not in SP2001 (Sloleks only)'].append(tag)
        if not sl_hit:
            report['not in Sloleks (SP2001 only)'].append(tag)
        quals = sorted({x for e in sp_hit for x in e['qualifiers']})
        if quals:
            report['qualified in SP2001'].append(f'{tag}: {", ".join(quals)} — {sp_hit[0]["head"][:120]}')
        single = len(lemma_queries(w)) == 1 and ' ' not in render(w['sl']).strip()
        if single and w.get('gender'):
            nouns = [e for e in sl_hit if e.get('category') == 'noun']
            gs = {GENDER.get(e.get('gender')) for e in nouns}
            if nouns and w['gender'] not in gs:
                report['gender differs from Sloleks'].append(f'{tag}: pack {w["gender"]}, Sloleks {"/".join(sorted(g or "?" for g in gs))}')
            if not nouns:
                report['a gender, Sloleks no noun'].append(f'{tag}: Sloleks {", ".join(e.get("category", "?") for e in sl_hit)}')
            # the plural: the nominative plural Sloleks gives
            pl = set()
            for e in nouns:
                for form, msd in forms.get(e['id']) or []:
                    if msd.startswith('S') and len(msd) >= 5 and msd[3] == 'm' and msd[4] == 'i':
                        pl.add(form.lower())
            if not pl and wiki_forms(q):
                pl = {f for f, g in wiki_forms(q).items() if 'nominative' in g.split(';')[0] and 'plural' in g} | \
                     {f for f, g in wiki_forms(q).items() if any('nominative' in part and 'plural' in part for part in g.split(';'))}
            if pl and w.get('plural') and w['plural'].lower() not in pl:
                report['plural differs from Sloleks'].append(f'{tag}: pack {w["plural"]}, Sloleks {"/".join(sorted(pl))}')
            if pl and not w.get('plural'):
                report['no plural in the pack'].append(f'{tag}: Sloleks {"/".join(sorted(pl))}')
    for k in ['not fetched', 'in neither source', 'not in SP2001 (Sloleks only)', 'not in Sloleks (SP2001 only)', 'qualified in SP2001',
              'gender differs from Sloleks', 'a gender, Sloleks no noun', 'plural differs from Sloleks', 'no plural in the pack']:
        if report[k]:
            print(f'\n## {k} ({len(report[k])})')
            for x in report[k]:
                print('  ' + x)
    print(f'\n{len(rows)} words looked up')


def known_lemmas():
    d = json.load(open(os.path.join(C, 'lexicon/sl.json')))
    lem = d['lemmas']
    lemmas = {(l['lemma'].lower(), l['pos']) for l in lem}
    forms = collections.defaultdict(set)
    for form, refs in d['forms'].items():
        for r in refs:
            forms[form.lower()].add(lem[r[0]]['lemma'].lower())
    for l in lem:
        forms[l['lemma'].lower()].add(l['lemma'].lower())
    x = json.load(open(os.path.join(C, 'lexicon/sl.extra.json')))
    for e in x['entries']:
        lemmas.add((e['lemma'].lower(), e['pos']))
        forms[e['lemma'].lower()].add(e['lemma'].lower())
        for f in e.get('forms', {}):
            forms[f.lower()].add(e['lemma'].lower())
    return lemmas, forms


def supplement(levels, write):
    lemmas, forms = known_lemmas()
    names = {l for l, _ in lemmas}
    new = []
    dropped = []
    seen = set()
    for f, p in new_packs(levels):
        for w in p['words']:
            qs = lemma_queries(w)
            if len(qs) != 1 or ' ' in render(w['sl']).strip().rstrip('!?.'):
                continue  # phrases: their words are the dictionary's, or listed by --coverage
            q = qs[0]
            if q.lower() in names or q.lower() in seen:
                continue
            sp, sl, fs = look(q)
            if not sl:
                continue
            hit = [e for e in sl if e.get('lemma', '').lower() == q.lower()]
            if w.get('gender'):
                hit = [e for e in hit if e.get('category') == 'noun'] or hit
            if not hit:
                continue
            e = max(hit, key=lambda e: e.get('frequency') or 0)
            cat = e.get('category')
            pos = POS.get(cat, cat)
            table = labelled(fs.get(e['id']) or [], cat)
            keep = {}
            for form, g in table.items():
                others = forms.get(form, set()) - {q.lower()}
                if others and form != q.lower():
                    dropped.append(f'{q}: {form} ({g}) is also {", ".join(sorted(others))}')
                    continue
                keep[form] = g
            if q.lower() not in keep:
                keep = {q.lower(): {'noun': 'nominative singular', 'verb': 'infinitive', 'adj': 'nominative masculine singular'}.get(pos, pos), **keep}
            sp_hit = [x for x in sp or [] if x['word'].lower() == q.lower()]
            src = (f'SP2001 {sp_hit[0]["head"][:90].strip()}: {fran_url(q)} ' if sp_hit else 'Not in SP2001. ') + f'Sloleks: {sloleks_url(e["id"])}'
            entry = {'lemma': q, 'pos': pos}
            if pos == 'noun' and e.get('gender') in GENDER:
                entry['gender'] = GENDER[e['gender']]
            entry['gloss'] = [w['en']]
            entry['forms'] = keep
            entry['meanings'] = {'de': [w['de']], 'it': [w['it']]}
            entry['source'] = src
            new.append(entry)
            seen.add(q.lower())
    additions = []  # (record, forms): a lemma the dictionary or the supplement has, without forms the examples use
    for r in extra_records():
        q = r['lemma']
        if q.lower() in seen:
            continue
        if (q.lower(), r['pos']) in lemmas:
            if r.get('forms'):
                additions.append((r, r['forms']))
            continue
        sp, sl, fs = look(q)
        hit = [e for e in sl or [] if e.get('lemma', '').lower() == q.lower() and e.get('category') == SL_CAT.get(r['pos'], r['pos'])]
        e = max(hit, key=lambda e: e.get('frequency') or 0) if hit else None
        table = labelled(fs.get(e['id']) or [], e['category']) if e else {}
        keep = {}
        for form, g in table.items():
            others = forms.get(form, set()) - {q.lower()}
            if others and form != q.lower():
                dropped.append(f'{q}: {form} ({g}) is also {", ".join(sorted(others))}')
                continue
            keep[form] = g
        if not keep:
            keep = {q.lower(): {'noun': 'nominative singular', 'verb': 'infinitive', 'adj': 'nominative masculine singular'}.get(r['pos'], r['pos'])}
        for f2, g2 in (r.get('forms') or {}).items():
            keep.setdefault(f2, g2)
        sp_hit = [x for x in sp or [] if x['word'].lower() == q.lower()]
        src = (f'SP2001 {sp_hit[0]["head"][:90].strip()}: {fran_url(q)}' if sp_hit else 'Not in SP2001.') + (f' Sloleks: {sloleks_url(e["id"])}' if e else ' Not in Sloleks: the forms by hand.')
        entry = {'lemma': q, 'pos': r['pos']}
        if r['pos'] == 'noun' and e and e.get('gender') in GENDER:
            entry['gender'] = GENDER[e['gender']]
        entry['gloss'] = [r['en']]
        entry['forms'] = keep
        entry['meanings'] = {'de': [r['de']], 'it': [r['it']]}
        entry['source'] = src
        new.append(entry)
        seen.add(q.lower())
    print(f'{len(new)} new supplement entries; {len(dropped)} forms left out as another word\'s')
    for d in dropped:
        print('  left out: ' + d)
    print(f'{len(additions)} known lemmas get forms the examples use')
    if write:
        path = os.path.join(C, 'lexicon/sl.extra.json')
        text = open(path, encoding='utf8').read()
        x = json.loads(text)
        have = {(e['lemma'], e['pos']) for e in x['entries']}
        add = [e for e in new if (e['lemma'], e['pos']) not in have]
        lines = text.split('\n')
        merged = 0
        for r, fs in additions:
            key = (r['lemma'], r['pos'])
            if key in have:
                # merge the forms into the supplement's own entry, on its line
                for i, line in enumerate(lines):
                    if line.lstrip().startswith('{') and f'"lemma": {json.dumps(r["lemma"], ensure_ascii=False)}, "pos": "{r["pos"]}"' in line:
                        e = json.loads(line.strip().rstrip(','))
                        new_forms = {f: g for f, g in fs.items() if f not in e['forms']}
                        if new_forms:
                            e['forms'].update(new_forms)
                            lines[i] = '    ' + styled(e) + (',' if line.rstrip().endswith(',') else '')
                            merged += 1
                        break
            else:
                add.append({'lemma': r['lemma'], 'pos': r['pos'], 'gloss': [r['en']], 'forms': fs,
                            'meanings': {'de': [r['de']], 'it': [r['it']]},
                            'source': 'Forms the dictionary lacks, by hand (machine-written); the lemma is the dictionary\'s.'})
        text = '\n'.join(lines)
        # one entry a line, as the file is written
        head = text.rstrip().rstrip('}').rstrip().rstrip(']').rstrip()
        body = ''.join(',\n    ' + styled(e) for e in add)
        with open(path, 'w', encoding='utf8') as f:
            f.write(head + body + '\n  ]\n}\n')
        json.load(open(path, encoding='utf8'))
        print(f'wrote {len(add)} entries and merged forms into {merged} to {os.path.relpath(path, ROOT)}')


SLOT_CASE = {'i': 'nom', 'r': 'gen', 'd': 'dat', 't': 'acc', 'm': 'loc', 'o': 'ins'}
SLOT_NUM = {'e': 'sg', 'd': 'du', 'm': 'pl'}


def noun_table(lemma, gloss=None, force=True):
    """
    A forms-book table of a noun (companion/lexicon/sl.forms.json "tables") from Sloleks's paradigm, its note citing
    SP2001's head and both URLs; None when Sloleks has no noun of that lemma. [force]: fetch the headword page even
    when the dictionary has the lemma (the table replaces the dictionary's forms).
    """
    sl = sloleks_search(lemma) or []
    hit = [e for e in sl if e.get('lemma') == lemma and e.get('category') == 'noun']
    if not hit:
        return None
    e = max(hit, key=lambda e: e.get('frequency') or 0)
    fs = sloleks_forms(e['id']) or []
    forms = collections.defaultdict(list)
    for form, msd in fs:
        if msd.startswith('S') and len(msd) >= 5 and msd[3] in SLOT_NUM and msd[4] in SLOT_CASE:
            k = f'{SLOT_CASE[msd[4]]}.{SLOT_NUM[msd[3]]}'
            if form.lower() not in forms[k]:
                forms[k].append(form.lower())
    if not forms:
        return None
    order = [f'{c}.{n}' for n in ['sg', 'du', 'pl'] for c in ['nom', 'gen', 'dat', 'acc', 'loc', 'ins']]
    sp = [x for x in sp2001(lemma) or [] if x['word'].lower() == lemma.lower()]
    head = sp[0]['head'][:90].strip() if sp else None
    note = (f'SP2001 {head}: {fran_url(lemma)} ' if head else 'Not in SP2001. ') + f'Sloleks: {sloleks_url(e["id"])}'
    t = {'lemma': lemma, 'pos': 'noun', 'gender': GENDER.get(e.get('gender'))}
    if gloss:
        t['gloss'] = gloss
    t['forms'] = {k: (forms[k][0] if len(forms[k]) == 1 else forms[k][:4]) for k in order if k in forms}
    t['note'] = note[:300]
    return t


def animate_label(lemma):
    """SP2001's label of a masculine noun: 'člov.' (a person), 'živ.' (an animal), or None."""
    sp = [x for x in sp2001(lemma) or [] if x['word'].lower() == lemma.lower()]
    for x in sp:
        m = re.search(r'\b(člov|živ)\.', x['head'][:80])
        if m:
            return m.group(1) + '.'
    return None


def wiki_gaps(levels):
    """
    The example words no entry knows that are forms of a word the dictionary (sl.json) has without them: each found by
    its stem among the pack words and the records, confirmed by Sloleks's paradigm, and written as a record with the
    forms the examples use to sl_extra_words/auto-wiki.json (made by this command), which `supplement` adds.
    """
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import sl_packs_check as chk
    unknown = chk.unknown_tokens(tuple(levels))
    words = {}
    for f, p in chk.load_packs():
        for w in p['words']:
            k = chk.key_of(w)
            if ' ' not in k and w.get('en'):
                words.setdefault(k, (w['en'], w.get('de', w['en']), w.get('it', w['en']), 'noun' if w.get('gender') else None))
    for r in extra_records():
        words.setdefault(r['lemma'].lower(), (r['en'], r['de'], r['it'], r['pos']))
    path = os.path.join(EXTRA_DIR, 'auto-wiki.json')
    recs = {}
    for t in sorted(unknown):
        for k, (en, de, it, pos) in words.items():
            if wiki_forms(k) is None:
                continue
            n = max(3, len(k) - 2)
            stem = k[:-5] if k.endswith(('ovati', 'evati')) else k[:n]
            if not t.startswith(stem):
                continue
            sl = sloleks_search(k) or []
            hit = [e for e in sl if e.get('lemma', '').lower() == k and (not pos or SL_CAT.get(pos, pos) == e.get('category'))]
            if not hit:
                continue
            e = max(hit, key=lambda e: e.get('frequency') or 0)
            table = labelled(sloleks_forms(e['id']) or [], e['category'])
            if t in table:
                rp = {v: kk for kk, v in SL_CAT.items()}.get(e['category'], e['category'])
                r = recs.setdefault(k, {'lemma': k, 'pos': rp, 'en': en, 'de': de, 'it': it, 'forms': {}, 'auto': True})
                if t not in (wiki_forms(k) or {}):
                    r['forms'][t] = table[t]
                break
    recs = {k: r for k, r in recs.items() if r['forms']}
    with open(path, 'w', encoding='utf8') as f:
        f.write('[\n' + ',\n'.join('  ' + json.dumps(r, ensure_ascii=False) for r in sorted(recs.values(), key=lambda r: r['lemma'])) + '\n]\n')
    print(f'{len(recs)} dictionary lemmas get {sum(len(r["forms"]) for r in recs.values())} forms')


def styled(v):
    """JSON in the supplement's style: { "k": v, … } with spaces inside an object's braces, lists tight."""
    if isinstance(v, dict):
        return '{ ' + ', '.join(f'{json.dumps(k, ensure_ascii=False)}: {styled(x)}' for k, x in v.items()) + ' }'
    if isinstance(v, list):
        return '[' + ', '.join(styled(x) for x in v) + ']'
    return json.dumps(v, ensure_ascii=False)


# Pack words the case lens derives from their endings and the smoke tests it on (test/smoke/case-lens.ts: "Na vrhu mlaja
# visi nagrada."): no supplement entry of theirs, or the lens would read them instead of deriving them.
LEFT_TO_THE_LENS = {'mlaj'}


def auto(levels):
    """
    The example words no entry knows that are forms of a pack word (any Slovene pack's, a single word) whose lemma the
    dictionary lacks: each found by its stem, confirmed by Sloleks's forms of that word, and written as a record with the
    pack's meanings to sl_extra_words/auto.json (made by this command: don't edit it by hand).
    """
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import sl_packs_check as chk
    unknown = chk.unknown_tokens(tuple(levels))
    lemmas, _ = known_lemmas()
    have = {l for l, _ in lemmas} | {r['lemma'].lower() for r in extra_records() if not r.get('auto')}
    words = {}
    for f, p in chk.load_packs():
        for w in p['words']:
            k = chk.key_of(w)
            if ' ' in k or not w.get('en'):
                continue
            words.setdefault(k, w)
    path = os.path.join(EXTRA_DIR, 'auto.json')
    old = json.load(open(path, encoding='utf8')) if os.path.exists(path) else []
    recs = {r['lemma']: r for r in old}
    found = 0
    for t in sorted(unknown):
        cands = []
        for k, w in words.items():
            n = max(3, len(k) - 2)
            stem = k[:n]
            if k.endswith(('ovati', 'evati')):
                stem = k[:-5]
            if t.startswith(stem) and k not in have and k not in LEFT_TO_THE_LENS:
                cands.append((k, w))
        for k, w in cands:
            pos = 'noun' if w.get('gender') else None
            fs = cached_forms(k, pos) if OFFLINE else None
            if fs is None:
                look(k)
                fs = cached_forms(k, pos)
            if t in fs:
                sl = sloleks_search(k) or []
                cat = next((e['category'] for e in sl if e.get('lemma', '').lower() == k and (not pos or e['category'] == 'noun')), None)
                rp = {v: kk for kk, v in SL_CAT.items()}.get(cat, cat)
                if rp and k not in recs:
                    recs[k] = {'lemma': k, 'pos': rp, 'en': w['en'], 'de': w.get('de', w['en']), 'it': w.get('it', w['en']), 'auto': True}
                    found += 1
                break
    with open(path, 'w', encoding='utf8') as f:
        f.write('[\n' + ',\n'.join('  ' + json.dumps(r, ensure_ascii=False) for r in sorted(recs.values(), key=lambda r: r['lemma'])) + '\n]\n')
    left = chk.unknown_tokens(tuple(levels))
    print(f'{found} new auto records ({len(recs)} in all); {len(left)} example words still unknown:')
    print('  ' + ', '.join(f'{t} ({",".join(sorted(ps))})' for t, ps in sorted(left.items())))


if __name__ == '__main__':
    a = sys.argv[1:]
    levels = {'B1', 'B2'}
    if '--level' in a:
        levels = set(a[a.index('--level') + 1].split(','))
    if not a or a[0] == 'check':
        check(levels)
    elif a[0] == 'supplement':
        supplement(levels, '--write' in a)
    elif a[0] == 'wiki-gaps':
        wiki_gaps(levels)
    elif a[0] == 'tables':
        # python3 sl_sources.py tables lemma… : the forms-book tables, as JSON lines
        for q in a[1:]:
            if not q.startswith('--'):
                print(json.dumps(noun_table(q), ensure_ascii=False))
    elif a[0] == 'animate':
        for q in a[1:]:
            if not q.startswith('--'):
                print(q, animate_label(q))
    elif a[0] == 'sloleks':
        sloleks_pass(levels)
    elif a[0] == 'auto':
        auto(levels)
    elif a[0] == 'unknown':
        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
        import sl_packs_check as chk
        for t, ps in sorted(chk.unknown_tokens(tuple(levels)).items()):
            print(t, ','.join(sorted(ps)))
    elif a[0] == 'look':
        for q in a[1:]:
            if q.startswith('--'):
                continue
            sp, sl, fs = look(q)
            print(q, json.dumps(sp, ensure_ascii=False)[:600])
            print('  sloleks', json.dumps(sl, ensure_ascii=False)[:300])
            for i, f in fs.items():
                print('  ', sloleks_url(i), json.dumps(labelled(f or [], next(e['category'] for e in sl if e['id'] == i)), ensure_ascii=False)[:900])
