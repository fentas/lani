#!/usr/bin/env python3
"""The words a language's content uses most that no word pack teaches (followups: `common-words`, 2026-11-03).

A word counts as learned only as a pack's card (GAME.md, "Words learned"): a word the course and the village use all the
time but no pack has (Italian essere, avere, piccolo) is never counted, however often the learner meets it. This lists
them, the most frequent first, so a pack can take them.

What is counted, for language L:
- the course (course/L/units): the sentences and words in L of its items (a gap text, a transform's line, a choice's
  options, a table's cells, a bank's words; an `accept` or `solutions` list only by its first, the standard answer; no
  `spoken`, no hint, label or explanation);
- the rest, as `lexicon-build --coverage` reads it: the scenes, packs, scenarios and modules of L, its grammar pages, its
  culture's directory (people, stories, readings, arrivals, steps, parties), its drills, I spy lines and cards: the texts
  under the key L, example_L, opener_L, say, text, options, tokens, distractors, and an accept or solutions list's first.
Each word as the app taps it (WordTokens; an Italian elided article dropped, as the lookup does), normalized as
bridge/src/lexicon.ts normalizeForm. Its lemma: the supplement's first entry with that form (<L>.extra.json, read first by
the lookup too), else for Slovene the dictionary's first reading (sl.json). A word is a pack's when the form itself or its
lemma is a pack word (packKey: no article, no punctuation), or it stands in a pack's expression of 2-4 words where
that is written (dobro jutro, come stai), in companion/packs/L (Slovene: companion/packs), the
culture's packs/, and any --packs directory (a branch's packs not on main yet).

Usage: python3 -I companion/lexicon/tools/common_words.py --lang it [--packs DIR …] [--top N] [--pos verb,adj,…] [--tsv]
  --top  how many to list (default 80)
  --pos  only these parts of speech (the supplement's or the dictionary's names: verb, noun, adj, adjective, adv, …)
  --tsv  count, lemma, pos, gloss, a tab between, as packs-it's frequent-not-in-packs.tsv
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
import unicodedata
from collections import Counter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../..'))  # companion/

TAPPED = re.compile(r"[^\W\d_]+(?:['’‐-][^\W\d_]+)*")
ARTICLES = {
    'it': re.compile(r"^(?:(?:il|lo|la|i|gli|le|un|uno|una) +|(?:l|un)['’])"),
    'de': re.compile(r'^(?:der|die|das|ein|eine) +'),
    'en': re.compile(r'^(?:the|an?|to) +'),
}
COURSE_KEYS = {'words', 'options', 'rows', 'from', 'text', 'cells', 'distractors', 'tokens', 'head', 'columns', 'cue', 'title'}
FIRST_ONLY = {'accept', 'solutions'}
PHRASE_WORDS = 4  # the longest expression the lookup looks for (lexicon.ts)


def norm(s: str, lang: str) -> str:
    """bridge/src/lexicon.ts normalizeForm."""
    low = s.strip().lower()
    if lang == 'sl':
        nfd = unicodedata.normalize('NFD', low)
        out = ''.join(ch for ch in nfd if not unicodedata.combining(ch) or ch == '̌')
        return unicodedata.normalize('NFC', out).replace('ł', 'l').replace('ə', 'e')
    low = low.replace('’', "'").replace('ʼ', "'")
    if lang == 'it':
        nfd = unicodedata.normalize('NFD', low)
        out = re.sub(r"[̀-ͯ](?=[̀-ͯ']*[^\W\d_])", '', nfd)
        return unicodedata.normalize('NFC', out)
    return unicodedata.normalize('NFC', low)


def pack_key(w: str, lang: str) -> str:
    """bridge/src/lexicon.ts packKey (a gender pair as its first form)."""
    w = re.sub(r'\{[mf]:([^{}|]*)\|[mf]:([^{}|]*)\}', r'\1', w)
    k = re.sub(r"[^\w'’ ]+|[\d_]+", ' ', norm(w, lang))
    k = re.sub(r'\s+', ' ', k).strip()
    return ARTICLES[lang].sub('', k) if lang in ARTICLES else k


def counted(t: str) -> str:
    """lexicon-build countedText: both forms of a gender pair, no other {placeholder}, no (bracket)."""
    s = re.sub(r'\{[mf]:([^{}|]*)\|[mf]:([^{}|]*)\}', r' \1 \2 ', t)
    before = None
    while before != s:
        before, s = s, re.sub(r'\{[^{}]*\}', ' ', s)
    return re.sub(r'\([^)]*\)', ' ', s.replace('{', ' ').replace('}', ' '))


def strings(v, keys: set[str], key: str = ''):
    if isinstance(v, dict):
        for k, x in v.items():
            yield from strings(x, keys, k)
    elif isinstance(v, list):
        if key in FIRST_ONLY:
            v = v[:1]
        for x in v:
            yield from strings(x, keys, key)
    elif isinstance(v, str) and (key in keys or key in FIRST_ONLY):
        yield v


def culture_of(lang: str) -> str | None:
    for m in glob.glob(os.path.join(ROOT, 'cultures/*/culture.json')):
        if json.load(open(m, encoding='utf8')).get('language', 'sl') == lang:
            return os.path.basename(os.path.dirname(m))
    return None


def content(lang: str) -> list[tuple[str, set[str]]]:
    """(file, keys): the content files of [lang] and the keys their texts in [lang] are under."""
    own = [] if lang == 'sl' else [lang]
    keys = {lang, f'example_{lang}', f'opener_{lang}', 'say', 'text', 'options', 'tokens', 'distractors'}
    out = []
    for d in ('scenes', 'packs', 'scenarios', 'modules'):
        out += [(f, keys) for f in sorted(glob.glob(os.path.join(ROOT, d, *own, '*.json')))]
    for d in (f'grammar/{lang}', f'drills/{lang}', f'cards/{lang}', f'cases/{lang}'):
        out += [(f, keys) for f in sorted(glob.glob(os.path.join(ROOT, d, '*.json')))]
    out += [(f, keys) for f in glob.glob(os.path.join(ROOT, f'ispy/{lang}.json'))]
    c = culture_of(lang)
    if c:
        out += [(f, keys) for f in sorted(glob.glob(os.path.join(ROOT, 'cultures', c, '**/*.json'), recursive=True))]
    course = keys | COURSE_KEYS | {'prompt'}
    out += [(f, course) for f in sorted(glob.glob(os.path.join(ROOT, f'course/{lang}/units/*.json')))]
    return out


def pack_files(lang: str, extra: list[str]) -> list[str]:
    own = [] if lang == 'sl' else [lang]
    fs = glob.glob(os.path.join(ROOT, 'packs', *own, '*.json'))
    c = culture_of(lang)
    if c:
        fs += glob.glob(os.path.join(ROOT, 'cultures', c, 'packs/*.json'))
    for d in extra:
        fs += glob.glob(os.path.join(d, '*.json'))
    return fs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--lang', required=True)
    ap.add_argument('--packs', action='append', default=[])
    ap.add_argument('--top', type=int, default=80)
    ap.add_argument('--pos', default='')
    ap.add_argument('--tsv', action='store_true')
    ap.add_argument('--where', default='', help='a lemma: the files its count comes from')
    a = ap.parse_args()
    lang = a.lang

    # readings: the supplement's entries first, then (Slovene) the dictionary's
    first: dict[str, tuple[str, str, str]] = {}
    for e in json.load(open(os.path.join(ROOT, f'lexicon/{lang}.extra.json'), encoding='utf8'))['entries']:
        for f in [e['lemma'], *e['forms'].keys()]:
            first.setdefault(norm(f, lang), (norm(e['lemma'], lang), e['pos'], '; '.join(e.get('gloss', []))[:60]))
    dict_path = os.path.join(ROOT, f'lexicon/{lang}.json')
    if os.path.exists(dict_path):
        d = json.load(open(dict_path, encoding='utf8'))
        for f, rows in d['forms'].items():
            if rows:
                lem = d['lemmas'][rows[0][0]]
                first.setdefault(norm(f, lang), (norm(lem['lemma'], lang), lem['pos'], '; '.join(lem.get('gloss', []))[:60]))

    packs: set[str] = set()
    pack_count = 0
    for f in pack_files(lang, a.packs):
        j = json.load(open(f, encoding='utf8'))
        if j.get('schema') != 'lani.pack/v0' or j.get('language', 'sl') != lang:
            continue
        pack_count += 1
        for w in j['words']:
            for s in (w.get(lang), w.get('plural')):
                if s:
                    packs.add(pack_key(s, lang))

    phrases = {p for p in packs if ' ' in p}
    count: Counter[tuple[str, str]] = Counter()
    where: Counter[str] = Counter()
    gloss: dict[tuple[str, str], str] = {}
    tokens = covered = unknown = 0
    files = content(lang)
    for f, keys in files:
        for t in strings(json.load(open(f, encoding='utf8')), keys):
            text = counted(t)
            if lang == 'it':
                text = re.sub(r"[^\W\d_]+['’](?=[^\W\d_])", '', text)
            ws = [norm(m.group(0), lang) for m in TAPPED.finditer(text)]
            # the words of a pack's expression where it stands (dobro jutro, come stai): learned with it, as the lookup finds it
            inside = set()
            for n in range(PHRASE_WORDS, 1, -1):
                for i in range(len(ws) - n + 1):
                    if ' '.join(ws[i:i + n]) in phrases:
                        inside.update(range(i, i + n))
            for i, w in enumerate(ws):
                tokens += 1
                r = first.get(w)
                if i in inside or w in packs or (r and r[0] in packs):
                    covered += 1
                    continue
                if not r:
                    unknown += 1
                    continue
                k = (r[0], r[1])
                count[k] += 1
                if r[0] == a.where:
                    where[os.path.relpath(f, ROOT)] += 1
                gloss.setdefault(k, r[2])

    if a.where:
        for f, n in where.most_common(30):
            print(f'{n:6} {f}')
        return
    pos = {p for p in a.pos.split(',') if p}
    rows = [(n, lem, p) for (lem, p), n in count.most_common() if not pos or p in pos]
    if a.tsv:
        print(f'# {lang}: lemmas the content uses most that no pack has (common_words.py): count, lemma, pos, gloss')
        for n, lem, p in rows[: a.top]:
            print(f'{n}\t{lem}\t{p}\t{gloss[(lem, p)]}')
        return
    print(f'{lang}: {len(files)} files, {tokens} tokens; {pack_count} packs, {len(packs)} pack words')
    print(f'  a pack word {covered} ({covered / tokens:.1%}), no reading {unknown} ({unknown / tokens:.1%}), '
          f'another {tokens - covered - unknown} ({(tokens - covered - unknown) / tokens:.1%}) in {len(count)} lemmas')
    for n, lem, p in rows[: a.top]:
        print(f'{n:6} {lem} ({p}) {gloss[(lem, p)]}')


if __name__ == '__main__':
    main()
