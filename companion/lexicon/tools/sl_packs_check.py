#!/usr/bin/env python3
# The Slovene B1/B2 word packs' own check (packs-sl, 2026-11-03), quicker than the smoke and stricter about the words:
# run it while writing a pack, then the real checks (bun test/prompt-clashes.ts, the smoke, lexicon-build --coverage).
#
#   python3 companion/lexicon/tools/sl_packs_check.py [--level B1,B2] [--unknown] [--quiet] [pack ids…]
#   python3 companion/lexicon/tools/sl_packs_check.py --existing     every word the Slovene packs teach, one a line
#
# For every pack of the levels given (default B1 and B2) it reports:
#   - errors: a word another Slovene pack already teaches (the same spelling: a pack never re-teaches a known word, so it
#     wouldn't count), a word another new pack has, a prompt part (a meaning in en, de or it, split as card.ts
#     promptParts splits it: brackets dropped, at " / ", "," and ";", lower case, an English a/an/the/to dropped) that
#     another word of the Slovene packs has (a new clash: the smoke fails on it), a field too long, a missing example or
#     meaning, a noun without its plural, a review over 200 characters, a giver not of the cast;
#   - warnings: a word whose lemma (by the dictionary's forms) another pack teaches in another form (grem and iti: not a
#     new word, though the app would count it), and with --unknown the example's words no dictionary entry, supplement
#     entry, pack word or name knows (each needs a supplement entry: lexicon-build --coverage is the real check);
#   - the numbers: distinct words (as the app counts them: one card per spelling, a homonym its own) before and after.
import json, re, sys, glob, os, collections

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../..'))
C = os.path.join(ROOT, 'companion')
BASES = ['en', 'de', 'it']
CAST = None


def cast():
    global CAST
    if CAST is None:
        CAST = {}
        for f in glob.glob(os.path.join(C, 'cultures/primorska/villagers/*.json')):
            d = json.load(open(f))
            CAST[d['name']] = d.get('emoji')
    return CAST


def render(s, g):
    # {m:a|f:b} → the gender's branch; {learner…} → a name
    s = re.sub(r'\{([mf]):([^{}|]*)\|([mf]):([^{}|]*)\}', lambda m: m.group(2) if m.group(1) == g else m.group(4), s)
    return re.sub(r'\{learner(?::\w+)?\}', 'Jan', s)


def norm(s):
    s = s.lower().replace('’', "'")
    s = re.sub(r'[.!?¿¡,;:"„“”«»]', '', s)
    return re.sub(r'\s+', ' ', s).strip()


def key_of(w):
    return norm(re.sub(r'\s*\([^)]*\)', '', render(w['sl'], 'm')))


def prompt_parts(back):
    m = re.sub(r'\s*\([^)]*\)', '', back).strip() or back
    out = []
    for p in re.split(r' / |[,;]', m):
        q = re.sub(r'^(a|an|the|to) ', '', norm(p.replace('·', ' ')))
        if q and q not in out:
            out.append(q)
    return out


def load_packs():
    out = []
    for f in sorted(glob.glob(os.path.join(C, 'packs/*.json'))) + sorted(glob.glob(os.path.join(C, 'cultures/primorska/packs/*.json'))):
        try:
            out.append((f, json.load(open(f))))
        except Exception as e:
            print(f'✖ {os.path.relpath(f, ROOT)}: not JSON: {e}')
    return out


def all_pack_ids():
    ids = collections.Counter()
    for f in glob.glob(os.path.join(C, 'packs/**/*.json'), recursive=True) + glob.glob(os.path.join(C, 'cultures/*/packs/*.json')):
        try:
            ids[json.load(open(f))['id']] += 1
        except Exception:
            pass
    return ids


def lexicon():
    forms = collections.defaultdict(set)  # form → lemmas
    d = json.load(open(os.path.join(C, 'lexicon/sl.json')))
    lem = d['lemmas']
    for l in lem:
        forms[l['lemma'].lower()].add(l['lemma'])
    for form, refs in d['forms'].items():
        for r in refs:
            forms[form.lower()].add(lem[r[0]]['lemma'])
    x = json.load(open(os.path.join(C, 'lexicon/sl.extra.json')))
    for e in x['entries']:
        forms[e['lemma'].lower()].add(e['lemma'])
        for f in e.get('forms', {}):
            forms[f.lower()].add(e['lemma'])
    return forms


TOKEN = re.compile(r"[a-zA-ZčšžćđČŠŽĆĐäöüéèàòùìáíóúâêôûß]+")


def unknown_tokens(levels=('B1', 'B2')):
    """The example words of the packs of [levels] no entry knows (with the cache's forms and the records): token → pack ids."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import sl_sources
    packs = load_packs()
    forms = lexicon()
    known = set(forms)
    for n in cast():
        for t in TOKEN.findall(n):
            known.add(t.lower())
    new = [p for f, p in packs if p.get('level') in levels]
    for f, p in packs:
        for w in p['words']:
            known.add(key_of(w))
            known.add(norm(render(w['sl'], 'f')))
    for p in new:
        for w in p['words']:
            for q in sl_sources.lemma_queries(w):
                known |= sl_sources.cached_forms(q)
    for r in sl_sources.extra_records():
        known.add(r['lemma'].lower())
        known |= sl_sources.cached_forms(r['lemma'], r['pos']) | {x.lower() for x in (r.get('forms') or {})}
    out = collections.defaultdict(set)
    for p in new:
        for w in p['words']:
            for g in 'mf':
                for t in TOKEN.findall(render(w.get('example_sl', ''), g)):
                    if t.lower() not in known:
                        out[t.lower()].add(p['id'])
    return out


def main():
    args = sys.argv[1:]
    levels = {'B1', 'B2'}
    if '--level' in args:
        levels = set(args[args.index('--level') + 1].split(','))
        del args[args.index('--level'):args.index('--level') + 2]
    if '--existing' in args:
        # every word the Slovene packs teach, one a line: what a new pack mustn't teach again
        rows = {}
        for f, p in load_packs():
            for w in p['words']:
                rows.setdefault(key_of(w), f'{w["sl"]} — {w["en"]} ({p["id"]}, {p["level"]})')
        print('\n'.join(rows[k] for k in sorted(rows)))
        return
    show_unknown = '--unknown' in args
    quiet = '--quiet' in args
    only = [a for a in args if not a.startswith('--')]
    packs = load_packs()
    forms = lexicon()
    names = set()
    for n in cast():
        for t in TOKEN.findall(n):
            names.add(t.lower())
    old = [(f, p) for f, p in packs if p.get('level') not in levels]
    new = [(f, p) for f, p in packs if p.get('level') in levels]
    # existing spellings and lemmas
    old_keys = {}
    old_lemmas = {}
    for f, p in old:
        for w in p['words']:
            k = key_of(w)
            old_keys.setdefault(k, (p['id'], w))
            for lm in forms.get(k, set()) if ' ' not in k else set():
                old_lemmas.setdefault(lm.lower(), (p['id'], w['sl']))
            old_lemmas.setdefault(k, (p['id'], w['sl']))
    # prompt parts of every word: base → part → [(key, pack id)]
    parts = {b: collections.defaultdict(list) for b in BASES}
    for f, p in packs:
        for w in p['words']:
            for b in BASES:
                if w.get(b):
                    for g in 'mf':
                        for q in prompt_parts(render(w[b], g)):
                            parts[b][q].append((key_of(w), p['id'], w['id']))
    ids = all_pack_ids()
    pack_words = set()
    for f, p in packs:
        for w in p['words']:
            pack_words.add(key_of(w))
            pack_words.add(norm(render(w['sl'], 'f')))
    errors = warnings = 0
    new_keys = collections.defaultdict(list)
    for f, p in new:
        for w in p['words']:
            new_keys[key_of(w)].append(p['id'])
    # what the supplement will know once sl_sources.py has fetched it (its cache): the new words' forms, the records'
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import sl_sources
    for f, p in new:
        for w in p['words']:
            for q in sl_sources.lemma_queries(w):
                for x in sl_sources.cached_forms(q):
                    forms[x].add(q)
    records = sl_sources.extra_records()
    for r in records:
        forms[r['lemma'].lower()].add(r['lemma'])
        for x in sl_sources.cached_forms(r['lemma'], r['pos']) | set((r.get('forms') or {}).keys()):
            forms[x.lower()].add(r['lemma'])
    unknown_all = collections.Counter()
    for f, p in new:
        if only and p['id'] not in only:
            continue
        rel = os.path.relpath(f, ROOT)
        msgs = []
        def err(m):
            nonlocal errors
            errors += 1
            msgs.append('  ✖ ' + m)
        def warn(m):
            nonlocal warnings
            warnings += 1
            msgs.append('  ⚠ ' + m)
        if ids[p['id']] > 1:
            err(f'pack id {p["id"]} is used {ids[p["id"]]} times across the cultures')
        if os.path.basename(f) != p['id'] + '.json':
            err(f'file name is not {p["id"]}.json')
        if len(p.get('review', '')) > 200:
            err(f'review is {len(p["review"])} characters (max 200)')
        g = p.get('giver', {}).get('name')
        if g and g not in cast():
            err(f'giver {g} is not of the cast ({", ".join(sorted(cast()))})')
        for l in ['sl', 'en', 'de', 'it']:
            if not isinstance(p.get('title'), dict) or not p['title'].get(l):
                err(f'title has no {l}')
        for l in BASES:
            if not isinstance(p.get('description'), dict) or not p['description'].get(l):
                err(f'description has no {l}')
        if not 8 <= len(p['words']) <= 24:
            err(f'{len(p["words"])} words (8-24)')
        wid = set()
        for w in p['words']:
            k = key_of(w)
            tag = f'{w["id"]} ({w["sl"]})'
            if w['id'] in wid:
                err(f'{tag}: duplicate word id')
            wid.add(w['id'])
            if not re.match(r'^[a-z0-9][a-z0-9-]{0,62}$', w['id']):
                err(f'{tag}: id not kebab-case')
            for l, mx in [('sl', 60), ('en', 80), ('de', 60), ('it', 60)]:
                if not w.get(l):
                    err(f'{tag}: no {l}')
                elif len(w[l]) > mx:
                    err(f'{tag}: {l} longer than {mx}')
                if not w.get('example_' + l):
                    err(f'{tag}: no example_{l}')
            if w.get('note') and (not w.get('note_de') or not w.get('note_it')):
                err(f'{tag}: a note without note_de and note_it')
            if w.get('gender') and not w.get('plural') and not w.get('no_plural_ok'):
                warn(f'{tag}: a noun without its plural')
            if k in old_keys and not w.get('homonym'):
                op, ow = old_keys[k]
                err(f'{tag}: already taught by {op} ("{ow["sl"]}": {ow["en"]})')
            if len(new_keys[k]) > 1 and not w.get('homonym'):
                err(f'{tag}: also in {", ".join(x for x in new_keys[k] if x != p["id"]) or "this pack twice"}')
            if ' ' not in k:
                for lm in forms.get(k, set()):
                    if lm.lower() != k and lm.lower() in old_lemmas:
                        warn(f'{tag}: its lemma {lm} is taught by {old_lemmas[lm.lower()][0]} as "{old_lemmas[lm.lower()][1]}"')
            for b in BASES:
                if not w.get(b):
                    continue
                for g2 in 'mf':
                    for q in prompt_parts(render(w[b], g2)):
                        others = {(ok, op) for ok, op, ow in parts[b][q] if ok != k}
                        if others:
                            err(f'{tag}: {b} prompt "{q}" also asks ' + ', '.join(f'{op}\'s {ok}' for ok, op in sorted(others)))
            ex = ' '.join(render(w.get('example_sl', ''), g2) for g2 in 'mf')
            for t in TOKEN.findall(ex):
                tl = t.lower()
                if tl not in forms and tl not in pack_words and tl not in names:
                    unknown_all[tl] += 1
                    if show_unknown:
                        stems = [x for x in p['words'] if len(tl) >= 4 and key_of(x)[:max(3, min(len(key_of(x)), len(tl)) - 3)] in tl]
                        hint = f' (a form of {stems[0]["sl"]}? fetched later)' if stems else ' (a record in sl_extra_words/ needed)'
                        warn(f'{tag}: example word "{t}" is in no dictionary entry{hint}')
        # de-duplicate the prompt messages (m and f)
        seen = []
        for m in msgs:
            if m not in seen:
                seen.append(m)
        if seen or not quiet:
            print(f'{rel}: {p["level"]}, {len(p["words"])} words, giver {g}')
            for m in seen:
                print(m)
    # numbers
    def distinct(ps):
        cards = set()
        for f, p in ps:
            for w in p['words']:
                k = key_of(w)
                cards.add((k, w['en']) if w.get('homonym') else (k, ''))
        # a homonym's two meanings: two cards; plain words of one spelling: one
        return len({c for c in cards})
    n_old = distinct(old)
    n_all = distinct(packs)
    print(f'\ndistinct words: {n_old} before, {n_all} with B1/B2 ({n_all - n_old} new); B1/B2 packs: {len(new)}, words {sum(len(p["words"]) for _, p in new)}')
    if unknown_all:
        print(f'example words in no dictionary entry: {len(unknown_all)} ({sum(unknown_all.values())} times)' + ('' if show_unknown else ' (--unknown lists them)'))
        if show_unknown:
            print('  ' + ', '.join(f'{w} {n}' for w, n in unknown_all.most_common()))
    print(f'{errors} errors, {warnings} warnings')
    sys.exit(1 if errors else 0)


if __name__ == '__main__':
    main()
