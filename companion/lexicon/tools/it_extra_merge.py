# ONE-OFF (it-print-meanings, 2026-11-01): these scripts made the first version of the Italian course's entries in
# companion/lexicon/it.extra.json (271 entries after its first 38). That file is the source now: edit it by hand, as the
# rest of the supplement. Kept so the way the forms were made can be read and run again for new words; a run adds only
# entries the file doesn't have (it_extra_merge.py), never changes one. See docs/plans/05-built/it-print-meanings.md.
#
# Appends the new entries to companion/lexicon/it.extra.json in its own layout (an entry a few lines), the file's
# other entries and its text kept as they are; the meta's "about" says what the new ones are.
# Usage: python3 -I companion/lexicon/tools/it_extra_merge.py companion/lexicon/it.extra.json <new-entries.json>
import json, sys

path, new_path = sys.argv[1:3]
text = open(path).read()
old = json.loads(text)
new = json.load(open(new_path))
have = {(e['lemma'], e['pos']) for e in old['entries']}
new = [e for e in new if (e['lemma'], e['pos']) not in have]

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


out = []
for e in new:
    head = [f'"lemma": {d(e["lemma"])}', f'"pos": {d(e["pos"])}']
    if e.get('gender'):
        head.append(f'"gender": {d(e["gender"])}')
    head.append(f'"gloss": {d(e["gloss"])}')
    m = e['meanings']
    out.append('    { ' + ', '.join(head) + ',\n' + forms_lines(e['forms']) + '\n      "meanings": { "de": ' + d(m['de']) + ', "sl": ' + d(m['sl']) + ' } }')

about_add = (
    " Since 2026-11-01 also the words the course's printed sheets left off for want of a meaning (the everyday verbs, "
    "adverbs, nouns and expressions of A1, A2 and B1: guardare, mai, il treno, c'era una volta), written for a child "
    "(German first, Slovene, English), each with the forms the Italian content uses (the course, the grammar book, the "
    "Friuli culture, the packs) and their grammar; a reflexive verb's forms with their pronoun (si lava, mi sono "
    "lavata), so the plain verb's (gira il frico) aren't taken for it; the forms written by hand and checked against "
    "Wiktionary's (kaikki.org, English and German Wiktionary), the meanings written, not copied "
    "(docs/plans/05-built/it-print-meanings.md)."
)
end = text.rstrip().rstrip('}').rstrip().rstrip(']').rstrip()
assert end.endswith('}'), end[-40:]
body = end + ',\n' + ',\n'.join(out) + '\n  ]\n}\n'
old_about = old['meta']['about']
assert d(old_about) in body
body = body.replace(d(old_about), d(old_about + about_add), 1)
json.loads(body)
open(path, 'w').write(body)
print(len(new), 'entries added;', len(old['entries']) + len(new), 'in all')
