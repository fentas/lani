#!/usr/bin/env python3
# Sloleks 3.0 (CC BY-SA 4.0, http://hdl.handle.net/11356/1745), read from the release itself, for the Slovene word forms
# (docs/plans/05-built/cleanup-code.md, "Sloleks"). viri.cjvt.si/sloleks, which sl_sources.py reads, is Sloleks 2.0, under
# CC BY-NC-SA 4.0: nothing new should come from it. This reads Sloleks.3.0.zip (240 MB, 13 GB unpacked: never unpacked
# here, each file streamed), which isn't in the repository: download it from the handle above, MD5
# 650d90e780c454f523490fac0ae84487, into .cache/sloleks-3.0/.
#
#   python3 -I companion/lexicon/tools/sloleks3.py index <Sloleks.3.0.zip> <index.json> [--repo <checkout>]
#   python3 -I companion/lexicon/tools/sloleks3.py check <index.json> [--repo <checkout>] [--list]
#   python3 -I companion/lexicon/tools/sloleks3.py cite <index.json> [--repo <checkout>] [--write]
#
# index: the entries of the lemmas Lani has (sl.json, sl.extra.json, sl.forms.json), each with its forms in the release's
# order: {lemma: [{key, status, category, gender, forms: [[form, msd, frequency], ...]}]}. sl-forms-order.ts reads it.
# check: every form of sl.extra.json and the forms book against the index: which entries 3.0 confirms whole, which only in
# part, which it lacks.
# cite: the forms book's notes: a table every form of which 3.0 has (its standard, also accepted and variant forms) cites 3.0
# and its key instead of the 2.0 viewer's page; the others stay as they are, and are listed. It changes the citation, never
# a form.
import collections
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
    idx = json.load(open(index_path, encoding='utf8'))['lemmas']
    lex = os.path.join(repo, 'companion/lexicon')
    extra = json.load(open(os.path.join(lex, 'sl.extra.json'), encoding='utf8'))['entries']
    tally = collections.Counter()
    part, lacking = [], []
    forms_all = forms_ok = 0
    for e in extra:
        if 'loleks' not in (e.get('source') or ''):
            continue
        tally['entries citing Sloleks'] += 1
        own = {f.lower() for f in e.get('forms', {})}
        got = forms_of(idx, e['lemma'], e.get('pos'))
        if got is None:
            tally['no such entry in 3.0'] += 1
            lacking.append(f"{e['lemma']} ({e.get('pos')})")
            continue
        missing = sorted(own - got)
        forms_all += len(own)
        forms_ok += len(own) - len(missing)
        if missing:
            tally['entry in 3.0, some forms not'] += 1
            part.append(f"{e['lemma']} ({e.get('pos')}): {', '.join(missing[:8])}")
        else:
            tally['every form in 3.0'] += 1
    for k, v in tally.most_common():
        print(f'{v:6} {k}')
    print(f'{forms_ok} of {forms_all} forms of the entries 3.0 has')
    if listing:
        print('\n## no such entry\n' + '\n'.join(lacking))
        print('\n## some forms not\n' + '\n'.join(part))


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
        new = VIEWER.sub(f"http://hdl.handle.net/11356/1745 ({key_of(idx, t['lemma'], t.get('pos'))})", note)
        new = re.sub(r'\bSloleks(?: 2\.0)?(?! 3\.0)', 'Sloleks 3.0', new)
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


def main():
    args = sys.argv[1:]
    if len(args) >= 3 and args[0] == 'index':
        index(args[1], args[2], repo_of(args))
    elif len(args) >= 2 and args[0] == 'check':
        check(args[1], repo_of(args), '--list' in args)
    elif len(args) >= 2 and args[0] == 'cite':
        cite(args[1], repo_of(args), '--write' in args)
    else:
        print('sloleks3.py index|check|cite: see the head of this file')
        sys.exit(2)


if __name__ == '__main__':
    main()
