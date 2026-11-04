#!/usr/bin/env python3
# Writes a word pack back in the curated packs' layout (a word a block: its head on one line, the examples on the next,
# each note on its own): python3 companion/lexicon/tools/sl_pack_fmt.py <pack.json>… (in place). Also a module:
# dump(pack) → the text.
import json, sys

HEAD = ['id', 'sl', 'en', 'de', 'it', 'emoji', 'gender', 'plural', 'homonym']
EX = ['example_sl', 'example_en', 'example_de', 'example_it']
NOTES = ['note', 'note_de', 'note_it', 'note_sl']


def j(v):
    return json.dumps(v, ensure_ascii=False)


def word(w):
    head = ', '.join(f'{j(k)}: {j(w[k])}' for k in HEAD if k in w)
    rest = [k for k in w if k not in HEAD + EX + NOTES]
    if rest:
        head += ', ' + ', '.join(f'{j(k)}: {j(w[k])}' for k in rest)
    lines = ['    { ' + head]
    ex = ', '.join(f'{j(k)}: {j(w[k])}' for k in EX if k in w)
    if ex:
        lines.append('      ' + ex)
    for k in NOTES:
        if k in w:
            lines.append(f'      {j(k)}: {j(w[k])}')
    return ',\n'.join(lines) + ' }'


def dump(p):
    out = ['{']
    keys = [k for k in p if k != 'words']
    for k in keys:
        v = p[k]
        if isinstance(v, dict) and k == 'description':
            out.append(f'  {j(k)}: {{\n' + ',\n'.join(f'    {j(a)}: {j(b)}' for a, b in v.items()) + '\n  },')
        elif isinstance(v, dict):
            out.append(f'  {j(k)}: {{ ' + ', '.join(f'{j(a)}: {j(b)}' for a, b in v.items()) + ' },')
        else:
            out.append(f'  {j(k)}: {j(v)},')
    out.append('  "words": [')
    out.append(',\n'.join(word(w) for w in p['words']))
    out.append('  ]')
    out.append('}')
    return '\n'.join(out) + '\n'


if __name__ == '__main__':
    for f in sys.argv[1:]:
        p = json.load(open(f, encoding='utf8'))
        text = dump(p)
        assert json.loads(text) == p, f
        open(f, 'w', encoding='utf8').write(text)
