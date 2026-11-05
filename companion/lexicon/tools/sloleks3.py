#!/usr/bin/env python3
# Sloleks 3.0 (CC BY-SA 4.0, http://hdl.handle.net/11356/1745), read from the release itself, for the Slovene word forms
# (docs/plans/05-built/cleanup-code.md, "Sloleks"). viri.cjvt.si/sloleks, which sl_sources.py reads, is Sloleks 2.0, under
# CC BY-NC-SA 4.0: nothing new should come from it. This reads Sloleks.3.0.zip (240 MB, 13 GB unpacked: never unpacked
# here, each file streamed), which isn't in the repository: download it from the handle above, MD5
# 650d90e780c454f523490fac0ae84487, into .cache/sloleks-3.0/.
#
#   python3 -I companion/lexicon/tools/sloleks3.py index <Sloleks.3.0.zip> <index.json> [--repo <checkout>]
#   python3 -I companion/lexicon/tools/sloleks3.py check <index.json> [--repo <checkout>] [--list] [--labels]
#   python3 -I companion/lexicon/tools/sloleks3.py cite <index.json> [--repo <checkout>] [--write]
#   python3 -I companion/lexicon/tools/sloleks3.py rebuild <index.json> [--repo <checkout>] [--write | --out <file>]
#
# index: the entries of the lemmas Lani has (sl.json, sl.extra.json, sl.forms.json), each with its forms in the release's
# order: {lemma: [{key, status, category, gender, forms: [[form, msd, frequency], ...]}]}. sl-forms-order.ts reads it.
# check: every form of sl.extra.json against the 3.0 entries its source cites; it fails on a form they lack and on a link to the 2.0 site.
# cite: the forms book's notes: a table every form of which 3.0 has (its standard, also accepted and variant forms) cites 3.0
# and its key instead of the 2.0 viewer's page; the others stay as they are, and are listed. It changes the citation, never
# a form.
# rebuild: the supplement's entries that were read from the 2.0 site, from 3.0 instead: every form looked up in 3.0's entry, the source
# citing it and its key; those that said Sloleks has no such word, where 3.0 has it after all, too. Lani's choice of which forms an
# entry holds stays (a form that is another word's is left out, a name has no plural), so no form comes in. sloleks3-settled.json holds
# what was settled by hand: a word 3.0 lists under another class or lemma, a form it lacks that stays. A form's grammar stays as written,
# since the tables read it (kmečki's own lemma is what 3.0 calls definite): --labels lists those 3.0's tags read otherwise.
import collections
import itertools
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
import zipfile


def repo_of(args):
    if '--repo' in args:
        return args[args.index('--repo') + 1]
    return os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(sys.argv[0])), '../../..'))


def wanted(repo):
    """The lemmas Lani has, lowercased: the dictionary's, the supplement's, the forms book's."""
    lex = os.path.join(repo, 'companion/lexicon')
    out = {l['lemma'].lower() for l in json.load(open(os.path.join(lex, 'sl.json'), encoding='utf8'))['lemmas']}
    out |= {e['lemma'].lower() for e in json.load(open(os.path.join(lex, 'sl.extra.json'), encoding='utf8'))['entries']}
    tables = json.load(open(os.path.join(lex, 'sl.forms.json'), encoding='utf8'))['tables']
    out |= {t['lemma'].lower() for t in tables}
    out |= set(json.load(open(os.path.join(lex, 'tools/sloleks3-settled.json'), encoding='utf8'))['lemmas'])
    return out


def entries(stream):
    """Each <entry> of an XML [stream], cleared as it goes (a file is 50-250 MB)."""
    context = ET.iterparse(stream, events=('start', 'end'))
    _, root = next(context)
    for event, el in context:
        if event == 'end' and el.tag == 'entry':
            yield el
            root.clear()


def read_entry(el):
    head = el.find('head')
    lemma = head.findtext('headword/lemma') or ''
    unit = head.find('lexicalUnit')
    grammar = head.find('grammar')
    gender = None
    for g in grammar.findall('grammarFeature'):
        if g.get('name') == 'gender':
            gender = g.text
    forms = []
    for wf in el.findall('body/wordFormList/wordForm'):
        msd = wf.findtext('msd') or ''
        for o in wf.findall('formRepresentations/orthographyList/orthography'):
            f = o.findtext('form') or ''
            m = o.find("measureList/measure[@type='frequency']")
            forms.append([f, msd, int(m.text) if m is not None and m.text else 0])
    return {'key': unit.get('sloleksKey') if unit is not None else '', 'status': head.findtext('status'),
            'category': grammar.findtext('category'), 'gender': gender, 'lemma': lemma, 'forms': forms}


def index(zip_path, out_path, repo):
    want = wanted(repo)
    found = collections.defaultdict(list)
    seen = 0
    with zipfile.ZipFile(zip_path) as z:
        names = sorted(n for n in z.namelist() if re.search(r'sloleks_3\.0_\d+\.xml$', n))
        for i, name in enumerate(names):
            with z.open(name) as f:
                for el in entries(f):
                    seen += 1
                    lemma = (el.findtext('head/headword/lemma') or '').lower()
                    if lemma in want:
                        found[lemma].append(read_entry(el))
            print(f'{i + 1}/{len(names)} {name}: {seen} entries read, {len(found)} of {len(want)} lemmas', flush=True)
    tmp = out_path + '.tmp'
    with open(tmp, 'w', encoding='utf8') as f:
        json.dump({'source': 'Sloleks 3.0, CC BY-SA 4.0, http://hdl.handle.net/11356/1745', 'entries_read': seen, 'lemmas': found}, f, ensure_ascii=False)
    os.replace(tmp, out_path)
    print(f'{len(found)} of {len(want)} lemmas, {seen} entries read: {out_path}')


CATEGORY = {'noun': 'noun', 'name': 'noun', 'verb': 'verb', 'adj': 'adjective', 'adv': 'adverb', 'num': 'numeral', 'pron': 'pronoun',
            'prep': 'preposition', 'conj': 'conjunction', 'particle': 'particle', 'intj': 'interjection'}


def forms_of(idx, lemma, pos):
    """The forms 3.0 gives [lemma] of [pos], lowercased; None when it has no such entry."""
    hits = [e for e in idx.get(lemma.lower(), []) if e['category'] == CATEGORY.get(pos, pos)]
    if not hits:
        return None
    return {f[0].lower() for e in hits for f in e['forms']}


def check(index_path, repo, listing):
    """Every form of the supplement against the 3.0 entries its source cites: nothing may cite the 2.0 site, or lack a form it cites."""
    idx = json.load(open(index_path, encoding='utf8'))['lemmas']
    rows = collections.defaultdict(list)  # (a key isn't unique: a name of either gender can have one, S_Lambergar)
    for hs in idx.values():
        for h in hs:
            rows[h['key']].append(h)
    lex = os.path.join(repo, 'companion/lexicon')
    extra = json.load(open(os.path.join(lex, 'sl.extra.json'), encoding='utf8'))['entries']
    settled = json.load(open(os.path.join(lex, 'tools/sloleks3-settled.json'), encoding='utf8'))['entries']
    tally = collections.Counter()
    bad = []
    for e in extra:
        source = e.get('source') or ''
        if VIEWER.search(source):
            tally['citing the 2.0 site'] += 1
            bad.append(f"{e['lemma']} ({e.get('pos')}): cites the 2.0 site")
        cited_keys = re.search(r'11356/1745 \(([^)]*)\)', source)
        if not cited_keys:
            continue
        tally['entries citing 3.0'] += 1
        keys = cited_keys.group(1).split(', ')
        unknown = [k for k in keys if k not in rows]
        if unknown:
            tally['keys not in the index'] += len(unknown)
            bad.append(f"{e['lemma']} ({e.get('pos')}): {', '.join(unknown)} not in the index")
            continue
        got = {f[0].lower() for k in keys for h in rows[k] for f in h['forms']}
        kept = settled.get(f"{e['lemma']}|{e.get('pos')}", {}).get('kept', {})
        missing = sorted(f for f in e['forms'] if f.lower() not in got and f not in kept)
        tally['forms'] += len(e['forms'])
        tally['forms kept by another source'] += sum(1 for f in e['forms'] if f in kept)
        tables = [{f.lower(): g for f, g in grammar(h['forms'], h['category']).items()} for k in keys for h in rows[k]]
        for f, label in e['forms'].items():
            said = next((t[f.lower()] for t in tables if f.lower() in t), None)
            if label and said and not NOTED.search(label) and not readings(label) <= readings(said):
                tally['labels 3.0 reads otherwise'] += 1
                if '--labels' in sys.argv:
                    print(f"labels: {e['lemma']} ({e.get('pos')}): {f}: {label}")
        if missing:
            tally['forms 3.0 lacks'] += len(missing)
            bad.append(f"{e['lemma']} ({e.get('pos')}): {', '.join(missing[:8])}")
    for k, v in tally.most_common():
        print(f'{v:6} {k}')
    if listing or bad:
        print('\n'.join(bad))
    if bad:
        sys.exit(1)


NOTE_MAX = 300
VIEWER = re.compile(r'https?://viri\.cjvt\.si/sloleks/slv/headword/\d+')


def table_forms(t):
    """Every form a forms book table holds, lowercased: its standard ones and those also accepted, old and spoken ones too."""
    out = set()
    for part in ('forms', 'also', 'variants'):
        for v in (t.get(part) or {}).values():
            out |= {f.lower() for f in ([v] if isinstance(v, str) else v)}
    return out


def key_of(idx, lemma, pos):
    hits = [e for e in idx.get(lemma.lower(), []) if e['category'] == CATEGORY.get(pos, pos)]
    return hits[0]['key'] if hits else None


def cite(index_path, repo, write):
    idx = json.load(open(index_path, encoding='utf8'))['lemmas']
    path = os.path.join(repo, 'companion/lexicon/sl.forms.json')
    text = open(path, encoding='utf8').read()
    tables = json.loads(text)['tables']
    done = 0
    left = []
    for t in tables:
        note = t.get('note') or ''
        if not VIEWER.search(note):
            continue
        got = forms_of(idx, t['lemma'], t.get('pos'))
        missing = sorted(table_forms(t) - got) if got is not None else None
        if got is None or missing:
            left.append(f"{t['lemma']} ({t.get('pos')}): {'no such entry' if got is None else ', '.join(missing[:6])}")
            continue
        new = VIEWER.sub(f"hdl.handle.net/11356/1745 ({key_of(idx, t['lemma'], t.get('pos'))})", note)
        new = re.sub(r'\bSloleks(?: 2\.0)?(?! 3\.0)', 'Sloleks 3.0', new)
        # forms.ts holds a note to this: a longer one leaves its whole table out of the book
        if len(new) > NOTE_MAX:
            left.append(f"{t['lemma']} ({t.get('pos')}): its note would be {len(new)} characters")
            continue
        old_json, new_json = json.dumps(note, ensure_ascii=False), json.dumps(new, ensure_ascii=False)
        if text.count(old_json) != 1:
            left.append(f"{t['lemma']} ({t.get('pos')}): its note isn't in the file once")
            continue
        text = text.replace(old_json, new_json)
        done += 1
    print(f'{done} tables cite 3.0; {len(left)} left as they are')
    print('\n'.join(left))
    if write:
        with open(path, 'w', encoding='utf8') as f:
            f.write(text)


NOTED = re.compile(r'\(|passive participle|possessive|gerund')
GENDERS = {'m': 'masculine', 'f': 'feminine', 'n': 'neuter'}


def readings(label):
    """What a grammar label claims, each reading on its own: "a/b masculine plural" as two. "definite" says nothing of the slot."""
    out = set()
    for part in (label or '').split('; '):
        sides = []
        for token in part.split():
            if token != 'definite' and token != '…':
                sides.append([w.replace('-person', '') for w in token.split('/')])
        for reading in itertools.product(*sides):
            if reading:
                out.add(frozenset(reading))
    return out


def grammar(forms, category):
    """{form: grammar} of a 3.0 entry's [form, msd, frequency] rows: as the supplement writes them, a pronoun's and a numeral's too."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import sl_sources as s
    if category not in ('pronoun', 'numeral'):
        return s.labelled([(f[0], f[1]) for f in forms], category)
    tags = collections.defaultdict(list)
    for form, msd, _ in forms:
        number, case, gender = s.NUM.get(msd[4:5]), s.CASES.get(msd[5:6]), s.GEN.get(msd[3:4])
        if number and case:
            tags[form.lower()].append((number, case, gender))
    return {f: s.adj_label([('', False, n, c, g) for n, c, g in t]) if all(g for _, _, g in t) else s.noun_label([(n, c) for n, c, _ in t]) for f, t in tags.items()}


def cited(source, keys):
    """[source] citing 3.0's entries [keys]: the 2.0 site's link replaced, or "not in Sloleks", where 3.0 has them after all."""
    link = f"hdl.handle.net/11356/1745 ({', '.join(keys)})"
    if VIEWER.search(source):
        return re.sub(r'\bSloleks(?: 2\.0)?(?! 3\.0)', 'Sloleks 3.0', VIEWER.sub(link, source))
    source, changed = re.subn(r'(?:Not in Sloleks: .*|no Sloleks paradigm)$', f'Sloleks 3.0: {link}', source)
    if changed:
        return source
    source, changed = re.subn(r'no SP2001 or Sloleks entry$', f'no SP2001 entry. Sloleks 3.0: {link}', source)
    return source if changed else f'{source} Sloleks 3.0: {link}'


def rebuilt(entry, idx, settled):
    """[entry] as 3.0 has it, what was settled by hand applied, with the forms 3.0 lacks. None: no such entry."""
    done = settled.get(f"{entry['lemma']}|{entry.get('pos')}", {})
    keys = done.get('entries')
    if keys == []:
        return {**entry, 'source': done['source']}, []
    rows = [h for hs in idx.values() for h in hs if h['key'] in keys] if keys else [h for h in idx.get(entry['lemma'].lower(), []) if h['category'] == CATEGORY.get(entry.get('pos'), entry.get('pos'))]
    if not rows:
        return None, None
    have = {f.lower() for f in entry['forms']}
    # the entry that holds the forms, a word of its own spelling and gender before a name that looks like it
    rows.sort(key=lambda h: (-len({f[0].lower() for f in h['forms']} & have), h['lemma'] != entry['lemma'], h['gender'] != GENDERS.get(entry.get('gender')), -sum(f[2] for f in h['forms'])))
    lacking = []
    used = []
    for f in entry['forms']:
        row = next((h for h in rows if any(f.lower() == x[0].lower() for x in h['forms'])), None)
        if row is None:
            if f not in done.get('kept', {}):
                lacking.append(f)
            continue
        if row['key'] not in used:
            used.append(row['key'])
    used.sort(key=[h['key'] for h in rows].index)
    source = done.get('source') or cited(entry['source'], used)
    for f, why in done.get('kept', {}).items():
        source += f' {f}: {why}.'
    return {**entry, 'source': source}, lacking


def rebuild(index_path, repo, write):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import sl_sources as s
    idx = json.load(open(index_path, encoding='utf8'))['lemmas']
    tools = os.path.dirname(os.path.abspath(__file__))
    settled = json.load(open(os.path.join(tools, 'sloleks3-settled.json'), encoding='utf8'))['entries']
    path = os.path.join(repo, 'companion/lexicon/sl.extra.json')
    lines = open(path, encoding='utf8').read().split('\n')
    tally = collections.Counter()
    out = []
    for line in lines:
        body = line.strip().rstrip(',')
        if not body.startswith('{ "lemma"'):
            out.append(line)
            continue
        e = json.loads(body)
        src = e.get('source') or ''
        key = f"{e['lemma']}|{e.get('pos')}"
        if 'loleks' not in src or '11356/1745' in src:
            out.append(line)
            continue
        new, lacking = rebuilt(e, idx, settled)
        # (a common noun's forms in a name that looks like it, bulin in S_Bulin, are no such entry)
        named = '(S_' + e['lemma'].capitalize() in new['source'] if new else False
        if new is None or (not VIEWER.search(src) and (lacking or (named and e.get('pos') != 'name' and key not in settled))):
            # never read from the 2.0 site: what it says of itself stands
            tally['left as they are'] += 1
            print(f'left: {key}: {src[:90]}')
            out.append(line)
            continue
        tally['entries'] += 1
        tally['read from the 2.0 site'] += bool(VIEWER.search(src))
        if lacking:
            print(f"not in 3.0: {key}: {', '.join(lacking)}")
            tally['forms not in 3.0'] += len(lacking)
        tally['forms'] += len(e['forms'])
        comma = ',' if line.rstrip().endswith(',') else ''
        out.append('    ' + s.styled(new) + comma)
    print(dict(tally))
    if write:
        text = '\n'.join(out)
        json.loads(text)
        with open(write if isinstance(write, str) else path, 'w', encoding='utf8') as f:
            f.write(text)


def main():
    args = sys.argv[1:]
    if len(args) >= 3 and args[0] == 'index':
        index(args[1], args[2], repo_of(args))
    elif len(args) >= 2 and args[0] == 'check':
        check(args[1], repo_of(args), '--list' in args)
    elif len(args) >= 2 and args[0] == 'cite':
        cite(args[1], repo_of(args), '--write' in args)
    elif len(args) >= 2 and args[0] == 'rebuild':
        rebuild(args[1], repo_of(args), args[args.index('--out') + 1] if '--out' in args else '--write' in args)
    else:
        print('sloleks3.py index|check|cite|rebuild: see the head of this file')
        sys.exit(2)


if __name__ == '__main__':
    main()
