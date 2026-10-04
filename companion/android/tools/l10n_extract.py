#!/usr/bin/env python3
"""Moves the app's "Slovene · English" literals into the string tables (docs/plans/02-worlds-languages-multiplayer.md, §1).

  python3 companion/android/tools/l10n_extract.py [--dry-run] [--show] [--skipped] [--summary] FILE.kt|DIR...
  python3 companion/android/tools/l10n_extract.py hand HAND.json      # keys converted by hand, with their old text
  python3 companion/android/tools/l10n_extract.py set KEY SL EN       # a new key (no old literal), sl.json and en.json
  python3 companion/android/tools/l10n_extract.py rename OLD NEW ...  # in every table, the fixture and the code
  python3 companion/android/tools/l10n_extract.py drop KEY ...        # out of every table and the fixture
  python3 companion/android/tools/l10n_extract.py forget FILE ...     # after `git checkout FILE`, to extract it again
  python3 companion/android/tools/l10n_extract.py imports             # the l10n imports every file needs
  python3 companion/android/tools/l10n_extract.py sort                # rewrites every table sorted

For each literal with one " · " between a Slovene and an English half, in the FILEs given (paths relative to
app/src/main/java/si/lanisce/lani; a directory means its .kt files; the l10n package itself is left out), it
- writes the halves into l10n/sl.json and l10n/en.json (app/src/main/resources) under a key: "<file>.<english>", or
  "common.<english>" when the literal is in several files, or the key that already holds the same two texts;
- replaces the literal with bi("key", args…), keeping what isn't language (a leading emoji or arrow, a trailing " ›",
  ": $value" after both halves) in the code: "✨ Izzivi · Challenges" → "✨ ${bi("common.challenges")}";
- records the old literal in the parity fixture (app/src/test/resources/l10n/parity.json) with sample arguments and
  what the old literal rendered for them; L10nParityTest renders every entry for sl · en and wants the same text.

Template expressions become ICU arguments: $n → {n}; slCount(n, "a", "b", "c", "d") → {n, plural, one {a} two {b} few
{c} other {d}} (CLDR's Slovene rules are slCount's); if (n == 1) "x" else "y" → {n, plural, one {x} other {y}} in
English, =1 in Slovene (as the old code had it). A literal the script can't read for sure (two " · ", a raw string,
an expression with strings of its own, English that carries on in a concatenation) stays, and --skipped lists it
for converting by hand.
"""
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
APP = HERE.parent / "app"
SRC = APP / "src/main/java/si/lanisce/lani"
TABLES = APP / "src/main/resources/l10n"
FIXTURE = APP / "src/test/resources/l10n/parity.json"
IMPORT = "import si.lanisce.lani.l10n.bi"
SEP = " · "

NOTES = {
    "sl": "Slovene. The sl and en tables come straight from the app's \"Slovene · English\" literals "
          "(companion/android/tools/l10n_extract.py): Jan's screens (sl · en) must read exactly as before, "
          "which L10nParityTest checks.",
    "en": "English: the base language of the default learner, and the first fallback for a key another table lacks.",
}

# --- Kotlin lexing ------------------------------------------------------------------------------------------


class Lit:
    """A string literal: [start, end) in the file, its segments ('t', decoded text, source) or ('e', code, source)."""

    def __init__(self, start, end, segs, raw):
        self.start, self.end, self.segs, self.raw = start, end, segs, raw


ESC = {"n": "\n", "t": "\t", "r": "\r", "b": "\b", '"': '"', "'": "'", "\\": "\\", "$": "$"}


def lex(src):
    """Every top-level string literal of a Kotlin file (strings inside templates belong to their literal)."""
    n = len(src)
    out = []

    def string(i):
        raw = src.startswith('"""', i)
        j = i + (3 if raw else 1)
        segs = []
        text, tsrc = [], []

        def flush():
            if tsrc:
                segs.append(("t", "".join(text), "".join(tsrc)))
                text.clear(), tsrc.clear()

        while j < n:
            if raw and src.startswith('"""', j):
                k = j + 3
                while k < n and src[k] == '"':
                    k += 1
                text.append('"' * (k - j - 3)), tsrc.append('"' * (k - j - 3))
                flush()
                return k, segs, True
            c = src[j]
            if not raw and c == '"':
                flush()
                return j + 1, segs, False
            if not raw and c == "\\":
                e = src[j + 1]
                if e == "u":
                    text.append(chr(int(src[j + 2:j + 6], 16))), tsrc.append(src[j:j + 6])
                    j += 6
                else:
                    text.append(ESC.get(e, e)), tsrc.append(src[j:j + 2])
                    j += 2
                continue
            if c == "$" and j + 1 < n and src[j + 1] == "{":
                flush()
                end = code(j + 2, "}")
                segs.append(("e", src[j + 2:end - 1].strip(), src[j:end]))
                j = end
                continue
            if c == "$" and j + 1 < n and (src[j + 1].isalpha() or src[j + 1] == "_"):
                flush()
                m = re.match(r"[A-Za-z_][A-Za-z0-9_]*", src[j + 1:])
                segs.append(("e", m.group(0), src[j:j + 1 + len(m.group(0))]))
                j += 1 + len(m.group(0))
                continue
            text.append(c), tsrc.append(c)
            j += 1
        raise SystemExit("unterminated string")

    def code(i, close, top=False):
        depth = 0
        while i < n:
            if src.startswith("//", i):
                i = src.find("\n", i)
                i = n if i < 0 else i
                continue
            if src.startswith("/*", i):
                d, i = 1, i + 2
                while i < n and d:
                    if src.startswith("/*", i):
                        d, i = d + 1, i + 2
                    elif src.startswith("*/", i):
                        d, i = d - 1, i + 2
                    else:
                        i += 1
                continue
            c = src[i]
            if c == "'":
                m = re.match(r"'(\\u[0-9a-fA-F]{4}|\\.|[^'\\])'", src[i:])
                if m:
                    i += len(m.group(0))
                    continue
            if c == '"':
                e, segs, raw = string(i)
                if top:
                    out.append(Lit(i, e, segs, raw))
                i = e
                continue
            if c == "{":
                depth += 1
            elif c == "}":
                if depth == 0 and close == "}":
                    return i + 1
                depth -= 1
            i += 1
        return i

    code(0, None, top=True)
    return out


def in_body(src, pos, lits):
    """Whether [pos] is at the top level or right in a class or object body, where a property is read once."""
    stack, i, spans = [], 0, iter(sorted((l.start, l.end) for l in lits))
    nxt = next(spans, None)
    while i < pos:
        if nxt and i >= nxt[0]:
            i, nxt = nxt[1], next(spans, None)
            continue
        if src.startswith("//", i):
            i = src.find("\n", i)
            continue
        if src.startswith("/*", i):
            i = src.find("*/", i) + 2
            continue
        c = src[i]
        if c in "{(":
            stack.append(c + src[src.rfind("\n", 0, i) + 1:i])
        elif c in "})" and stack:
            stack.pop()
        i += 1
    if not stack:
        return True
    head = stack[-1]
    # a constructor's or a function's parameters are no body
    return head[0] == "{" and bool(re.search(r"\b(object|class|interface)\b", head)) and not re.search(r"\bfun\b|->|=", head)


def merged(src, lits):
    """Literals joined by + (nothing but whitespace between) as one: (start, end, segs, raw, parts)."""
    groups = []
    for l in lits:
        if groups and re.fullmatch(r"\s*\+\s*", src[groups[-1][-1].end:l.start]):
            groups[-1].append(l)
        else:
            groups.append([l])
    for g in groups:
        segs = [s for l in g for s in l.segs]
        # adjacent text segments of different parts: one text
        joined = []
        for s in segs:
            if joined and s[0] == "t" and joined[-1][0] == "t":
                joined[-1] = ("t", joined[-1][1] + s[1], joined[-1][2] + s[2])
            else:
                joined.append(s)
        yield g[0].start, g[-1].end, joined, any(l.raw for l in g), len(g)


# --- template expressions ------------------------------------------------------------------------------------

IDENT = r"[A-Za-z_][A-Za-z0-9_]*"
CHAIN = rf"{IDENT}(?:(?:\?\.|\.|!!\.){IDENT})*(?:!!)?"
SLCOUNT = re.compile(rf'^slCount\(\s*({CHAIN}(?:\s*[+-]\s*\d+)?)\s*,\s*"([^"$\\]*)"\s*,\s*"([^"$\\]*)"\s*,\s*"([^"$\\]*)"\s*,\s*"([^"$\\]*)"\s*\)$')
IF_ONE = re.compile(rf'^if\s*\(\s*({CHAIN})\s*(==|!=)\s*1\s*\)\s*"([^"$\\]*)"\s*else\s*"([^"$\\]*)"$')
SIMPLE = re.compile(rf"^(?:{CHAIN})(?:\s*\?:\s*(?:{CHAIN}|\d+|\"[^\"$\\]*\"))?$|^\(?\s*{CHAIN}\s*[+-]\s*\d+\s*\)?$|^{IDENT}\(\s*(?:{CHAIN}(?:\s*,\s*{CHAIN})*)?\s*\)$|^{CHAIN}\.{IDENT}\(\)$")
GENERIC = {"size", "count", "value", "name", "length", "it", "text", "label", "title", "id", "sl", "en", "emoji"}


class Expr:
    """A template expression as a message argument: its name, the Kotlin value, and how it renders."""

    def __init__(self, kind, value, words=None, en=None):
        self.kind, self.value, self.words, self.en = kind, value, words, en  # kind: arg | count | if1


def expr(code):
    """(kind, value expression, words) for a template expression, or None when it isn't one the script can read."""
    code = code.strip()
    m = SLCOUNT.match(code)
    if m:
        return Expr("count", m.group(1), [m.group(i) for i in range(2, 6)])
    m = IF_ONE.match(code)
    if m:
        a, b = (m.group(3), m.group(4)) if m.group(2) == "==" else (m.group(4), m.group(3))
        return Expr("if1", m.group(1), [a, b])
    if '"' in code and not re.search(r'\?:\s*"[^"$\\]*"$', code):
        return None
    if SIMPLE.match(code):
        return Expr("arg", code)
    return None


def arg_name(value):
    """A readable argument name for a Kotlin expression: a.cards → cards, d?.xp ?: 0 → xp, new.size → newSize."""
    v = value.split("?:")[0].strip().strip("()").strip()
    v = re.sub(r"\s*[+-]\s*\d+$", "", v)
    v = re.sub(r"\(.*\)$", "", v)
    ids = re.findall(IDENT, v)
    if not ids:
        return "arg"
    last = ids[-1]
    if last in GENERIC and len(ids) > 1 and ids[-2] not in ("it", "this"):
        return ids[-2] + last[0].upper() + last[1:]
    return last


# --- messages -----------------------------------------------------------------------------------------------

def icu_escape(s):
    if any(c in s for c in "{}"):
        raise ValueError("braces")
    return s


def message(segs, lang, names):
    """The ICU message for one half: text as is, expressions as {name} or plurals."""
    out = []
    for kind, a, b in segs:
        if kind == "t":
            out.append(icu_escape(a))
            continue
        e = expr(a)
        name = names[e.value]
        if e.kind == "arg":
            out.append("{%s}" % name)
        elif e.kind == "count":
            one, two, few, many = e.words
            out.append("{%s, plural, one {%s} two {%s} few {%s} other {%s}}" % (name, one, two, few, many))
        else:
            one, other = e.words
            sel = "one" if lang == "en" else "=1"
            out.append("{%s, plural, %s {%s} other {%s}}" % (name, sel, one, other))
    return "".join(out)


def render_old(segs, sample):
    """What the old literal's segments rendered with [sample] (expression value → Python value)."""
    out = []
    for kind, a, b in segs:
        if kind == "t":
            out.append(a)
            continue
        e = expr(a)
        v = sample[e.value]
        if e.kind == "arg":
            out.append(str(v))
        elif e.kind == "count":
            one, two, few, many = e.words
            r = v % 100 if v >= 0 else -((-v) % 100)
            out.append(one if r == 1 else two if r == 2 else few if r in (3, 4) else many)
        else:
            out.append(e.words[0] if v == 1 else e.words[1])
    return "".join(out)


def has_letters(segs):
    """Whether a half has words: in its text, or in a plural's forms."""
    return any(re.search(r"[^\W\d_]", a) if k == "t" else (expr(a) is not None and expr(a).kind != "arg") for k, a, _ in segs)


def split_text(segs):
    """The segments before and after the one " · " in the text; None when there isn't exactly one."""
    hits = [(i, a.find(SEP)) for i, (k, a, _) in enumerate(segs) if k == "t" and SEP in a]
    if len(hits) != 1 or segs[hits[0][0]][1].count(SEP) != 1:
        return None
    i, at = hits[0]
    k, a, s = segs[i]
    at_src = s.find(SEP)
    left = segs[:i] + ([("t", a[:at], s[:at_src])] if at else [])
    right = ([("t", a[at + len(SEP):], s[at_src + len(SEP):])] if a[at + len(SEP):] else []) + segs[i + 1:]
    return left, right


def cut(seg, at):
    """A text segment split before decoded index [at]: (head, tail), either None when empty."""
    k, a, s = seg
    sa = len(src_prefix(s, at))
    head = ("t", a[:at], s[:sa]) if at > 0 else None
    tail = ("t", a[at:], s[sa:]) if at < len(a) else None
    return head, tail


def take_parens(left, right):
    """A bilingual part in parentheses, "$x (A · B)": up to the "(" and from the ")" on stays in the code."""
    depth, at = 0, None
    for i in range(len(left) - 1, -1, -1):
        k, a, _ = left[i]
        if k != "t":
            continue
        for j in range(len(a) - 1, -1, -1):
            if a[j] == ")":
                depth += 1
            elif a[j] == "(":
                if depth == 0:
                    at = (i, j)
                    break
                depth -= 1
        if at:
            break
    if not at:
        return [], left, right, []
    depth, end = 0, None
    for i, (k, a, _) in enumerate(right):
        if k != "t":
            continue
        for j, c in enumerate(a):
            if c == "(":
                depth += 1
            elif c == ")":
                if depth == 0:
                    end = (i, j)
                    break
                depth -= 1
        if end:
            break
    if not end:
        return [], left, right, []
    i, j = at
    head, tail = cut(left[i], j + 1)
    prefix, left = left[:i] + [head], ([tail] if tail else []) + left[i + 1:]
    i, j = end
    head, tail = cut(right[i], j)
    right, suffix = right[:i] + ([head] if head else []), [tail] + right[i + 1:]
    return prefix, left, right, suffix


def take_prefix(left):
    """Leading non-language text of the Slovene half (an emoji and its space, an arrow), kept in the code."""
    if not left or left[0][0] != "t":
        return [], left
    a = left[0][1]
    m = re.match(r"^([^\w(\"„“‘'«\[]*?)(?=[\w(\"„“‘'«\[]|$)", a, re.U)
    p = m.group(1) if m else ""
    if not p or re.search(r"\w", p):
        return [], left
    head, tail = cut(left[0], len(p))
    return [head], ([tail] if tail else []) + left[1:]


def src_prefix(s, n):
    """The source text of the first [n] decoded characters of text-segment source [s]."""
    i = 0
    for _ in range(n):
        if s[i] == "\\":
            i += 6 if s[i + 1] == "u" else 2
        else:
            i += 1
    return s[:i]


SUFFIX_TEXT = re.compile(r" [›→✓]$")


def take_suffix(left, right):
    """What follows both halves: " ›", or ": $value" when the Slovene half has no value and no colon."""
    if not right:
        return [], right
    # ": <expressions and punctuation>" at the end of the English half
    if not any(k == "e" for k, _, _ in left) and not any(k == "t" and ":" in a for k, a, _ in left):
        for i in range(len(right) - 1, -1, -1):
            k, a, s = right[i]
            if k != "t":
                continue
            at = a.rfind(":")
            if at < 0:
                if re.search(r"[^\W\d_]", a):
                    break
                continue
            tail_text = a[at:] + "".join(x[1] for x in right[i + 1:] if x[0] == "t")
            if re.search(r"[^\W\d_]", tail_text) or not (a[at:].startswith(": ") or a[at:] == ":"):
                break
            s_at = s.rfind(":")
            head = [("t", a[:at], s[:s_at])] if a[:at] else []
            suffix = [("t", a[at:], s[s_at:])] + right[i + 1:]
            if not has_letters(right[:i] + head):
                break
            return suffix, right[:i] + head
    k, a, s = right[-1]
    if k == "t":
        m = SUFFIX_TEXT.search(a)
        if m and not any(x[0] == "t" and x[1].rstrip().endswith(m.group(0).strip()) for x in left[-1:]):
            cut = len(m.group(0))
            return [("t", a[-cut:], s[-cut:])], right[:-1] + ([("t", a[:-cut], s[:-cut])] if a[:-cut] else [])
        # layout: a trailing space
        m = re.search(r"[ ]+$", a)
        if m and not (left and left[-1][0] == "t" and left[-1][1].endswith(m.group(0))):
            cut = len(m.group(0))
            return [("t", a[-cut:], s[-cut:])], right[:-1] + ([("t", a[:-cut], s[:-cut])] if a[:-cut] else [])
    return [], right


STOP = {"the", "a", "an", "is", "are", "to", "of", "your", "you", "it", "its", "this", "that", "for", "and", "with", "be", "in", "on"}


def slug(en_segs, names):
    """The words a key is made of: the English half's, arguments by name, small words left out when others remain."""
    parts = []
    for k, a, _ in en_segs:
        if k == "t":
            parts += re.findall(r"[A-Za-z0-9]+", a.replace("'", "").replace("’", ""))
        else:
            n = names.get(expr(a).value)
            if n and (not parts or parts[-1].lower() != n.lower()):
                parts.append(n)
    content = [w for w in parts if w.lower() not in STOP]
    return content or parts


def camel(words):
    if not words:
        return ""
    w = [x.lower() for x in words]
    return w[0] + "".join(x[:1].upper() + x[1:] for x in w[1:])


def area_of(path):
    stem = path.stem
    return stem[:1].lower() + stem[1:]


# --- tables and fixture ---------------------------------------------------------------------------------------

def load_table(lang):
    p = TABLES / f"{lang}.json"
    return json.loads(p.read_text()) if p.exists() else {}


def save_table(lang, t):
    notes = {k: v for k, v in t.items() if k.startswith("_")}
    if lang in NOTES and "_note" not in notes:
        notes = {"_note": NOTES[lang], **notes}
    keys = sorted(k for k in t if not k.startswith("_"))
    out = {**notes, **{k: t[k] for k in keys}}
    TABLES.mkdir(parents=True, exist_ok=True)
    (TABLES / f"{lang}.json").write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n")


def load_fixture():
    return json.loads(FIXTURE.read_text()) if FIXTURE.exists() else []


def save_fixture(f):
    FIXTURE.parent.mkdir(parents=True, exist_ok=True)
    seen, out = set(), []
    for e in sorted(f, key=lambda e: (e["file"], e["key"], e["old"])):
        k = json.dumps(e, sort_keys=True, ensure_ascii=False)
        if k not in seen:
            seen.add(k)
            out.append(e)
    # one literal a line; empty args, prefix and suffix left out
    for e in out:
        for s in e["samples"]:
            for k in ("args", "prefix", "suffix"):
                if not s.get(k, True):
                    s.pop(k)
    FIXTURE.write_text("[\n" + ",\n".join(json.dumps(e, ensure_ascii=False) for e in out) + "\n]\n")


def samples(exprs):
    """Sample values per expression: numbers (0 1 2 3 4 5 11 21 101 102 104 111) for counts, a marker for the rest."""
    numeric = {e.value for e in exprs if e.kind in ("count", "if1")}
    plain = [e.value for e in exprs if e.value not in numeric]
    counts = [0, 1, 2, 3, 4, 5, 11, 21, 101, 102, 104, 111] if numeric else [None]
    out = []
    for c in counts:
        s = {v: c for v in numeric}
        for v in plain:
            s.setdefault(v, f"‹{arg_name(v)}›")
        out.append(s)
    return out


# --- conversion ---------------------------------------------------------------------------------------------

class Skip(Exception):
    pass


def count_everywhere():
    """How many files hold each constant literal pair (sl, en): a pair in several files gets a common key."""
    files = {}
    for f in SRC.rglob("*.kt"):
        src = f.read_text()
        for start, end, segs, raw, parts in merged(src, lex(src)):
            core = constant_core(segs) if not raw else None
            if core:
                files.setdefault(core, set()).add(f)
    return {t: len(fs) for t, fs in files.items()}


def constant_core(segs):
    """(Slovene, English) of a literal without expressions, what's around them left out; None for anything else."""
    if any(k == "e" for k, _, _ in segs):
        return None
    halves = split_text(segs)
    if not halves:
        return None
    _, left, right, _ = take_parens(*halves)
    _, left = take_prefix(left)
    _, right = take_suffix(left, right)
    return "".join(a for _, a, _ in left), "".join(a for _, a, _ in right)


def convert(path, src, sl, en, fixture, counts, skipped, show=False):
    raw_lits = lex(src)
    lits = list(merged(src, raw_lits))
    edits = []
    for start, end, segs, raw, parts in lits:
        text = "".join(a for k, a, _ in segs if k == "t")
        where = f"{path.relative_to(SRC) if SRC in path.parents else path}:{src.count(chr(10), 0, start) + 1}"
        if SEP not in text:
            if any(k == "e" and re.search(r'"[^"]* · [^"]*"', a) for k, a, _ in segs):
                skipped.append(f"{where}: labels inside a template expression: {src[start:end][:140]}")
            continue
        try:
            if raw:
                raise Skip("raw string")
            line_start = src.rfind("\n", 0, start) + 1
            line = src[line_start:src.find("\n", end) if src.find("\n", end) >= 0 else len(src)]
            if re.search(r"\bLog\.[a-z]\(|\blog\(|println\(", line):
                raise Skip("a log line")
            before = src[:start].rstrip()
            after = src[end:].lstrip()
            if before.endswith("+") or after.startswith("+"):
                # English carrying on in a concatenation reads wrong in another pair: by hand, unless the halves end
                # in ": " (a label and its value) or the other side is a whole label.
                if not (text.endswith(": ") or text.endswith(":")) or before.endswith("+"):
                    raise Skip("part of a concatenation")
            halves = split_text(segs)
            if not halves:
                raise Skip("not one ' · '")
            paren_prefix, left, right, paren_suffix = take_parens(*halves)
            prefix, left = take_prefix(left)
            suffix, right = take_suffix(left, right)
            prefix, suffix = paren_prefix + prefix, suffix + paren_suffix
            if not has_letters(left) or not has_letters(right):
                raise Skip("a half without words")
            if has_letters(prefix) or has_letters(suffix):
                raise Skip("words outside the halves")
            exprs = []
            for k, a, _ in prefix + left + right + suffix:
                if k == "e":
                    e = expr(a)
                    if e is None:
                        raise Skip(f"expression {a!r}")
                    exprs.append(e)
            for k, a, _ in prefix + suffix:
                if k == "e" and expr(a).kind != "arg":
                    raise Skip("a plural outside the halves")
            # argument names, one per value expression
            msg_exprs = [expr(a) for k, a, _ in left + right if k == "e"]
            names, used = {}, set()
            for e in msg_exprs:
                if e.value in names:
                    continue
                n = arg_name(e.value)
                base, i = n, 2
                while n in used:
                    n, i = f"{base}{i}", i + 1
                names[e.value] = n
                used.add(n)
            try:
                m_sl = message(left, "sl", names)
                m_en = message(right, "en", names)
            except ValueError:
                raise Skip("braces in the text")
            if m_sl != m_sl.strip() or m_en != m_en.strip():
                raise Skip("spaces around a half")
            # the key: an existing one with the same texts, else <file or common>.<english words>
            key = next((k for k in sl if not k.startswith("_") and sl[k] == m_sl and en.get(k) == m_en), None)
            if key is None:
                area = "common" if not msg_exprs and counts.get((m_sl, m_en), 0) > 1 else area_of(path)
                words = slug(right, names)
                if not words:
                    raise Skip("no English words for a key")
                for n in range(4, 10):
                    key = f"{area}.{camel(words[:n])}"
                    if key not in sl and key not in en:
                        break
                else:
                    i = 2
                    while f"{key}{i}" in sl:
                        i += 1
                    key = f"{key}{i}"
            # the call and what surrounds it
            arg_values = []
            for e in msg_exprs:
                if (names[e.value], e.value) not in arg_values:
                    arg_values.append((names[e.value], e.value))
            call_args = "".join(
                f', "{n}" to {v if re.fullmatch(CHAIN, v) else "(" + v + ")"}' for n, v in arg_values
            )
            call = f'bi("{key}"{call_args})'
            prefix_src = "".join(s for _, _, s in prefix)
            suffix_src = "".join(s for _, _, s in suffix)
            new = f'"{prefix_src}${{{call}}}{suffix_src}"' if prefix_src or suffix_src else call
            # a constant or a property initializer is read once: a getter reads the pair every time
            m = re.search(r"(\b(?:private |internal )?)const val (\w+)(\s*:\s*String)?\s*=\s*$", src[line_start:start])
            decl = None
            if m:
                decl = (line_start + m.start(), start, f"{m.group(1)}val {m.group(2)}: String get() = ")
            else:
                m = re.search(r"^(\s*)((?:private |internal |override )*)val (\w+)(\s*:\s*String)?\s*=\s*$", src[line_start:start])
                if m and in_body(src, start, raw_lits):
                    decl = (line_start + m.start(), start, f"{m.group(1)}{m.group(2)}val {m.group(3)}: String get() = ")
            sl[key], en[key] = m_sl, m_en
            smp = []
            for s in samples(exprs):
                smp.append({
                    "args": {names[v]: val for v, val in s.items() if v in names and any(x.value == v for x in msg_exprs)},
                    "prefix": render_old(prefix, s),
                    "suffix": render_old(suffix, s),
                    "expect": render_old(segs, s),
                })
            fixture.append({"file": str(path.relative_to(SRC)) if SRC in path.parents else str(path), "key": key,
                            "old": src[start:end], "samples": smp})
            edits.append((start, end, new, decl))
            if show:
                print(f"{where}\n  {src[start:end]}\n  → {new}\n    sl: {m_sl}\n    en: {m_en}")
        except Skip as e:
            skipped.append(f"{where}: {e}: {src[start:end][:140]}")
    if not edits:
        return src
    out = src
    for start, end, new, decl in sorted(edits, key=lambda x: -x[0]):
        out = out[:start] + new + out[end:]
        if decl:
            out = out[:decl[0]] + decl[2] + out[decl[1]:]
    return add_import(out, IMPORT) if IMPORT not in out else out


def add_import(src, imp):
    """[imp] after the file's last import of ours (or its last import, or its package line)."""
    imports = [m for m in re.finditer(r"^import .*$", src, re.M)]
    ours = [m for m in imports if m.group(0).startswith("import si.lanisce.lani.")]
    at = (ours or imports)[-1].end() if imports else re.search(r"^package .*$", src, re.M).end()
    return src[:at] + "\n" + imp + ("" if imports else "\n") + src[at:]


def main(argv):
    if argv[:1] == ["sort"]:
        for p in TABLES.glob("*.json"):
            save_table(p.stem, json.loads(p.read_text()))
        return
    if argv[:1] == ["forget"]:
        # after `git checkout FILE`, to extract it again: its fixture entries go, and keys no code names any more
        rel = {str((SRC / f).resolve().relative_to(SRC)) if (SRC / f).exists() else f for f in argv[1:]}
        fixture = [e for e in load_fixture() if e["file"] not in rel]
        code = "\n".join(p.read_text() for p in SRC.rglob("*.kt"))
        tables = {p.stem: load_table(p.stem) for p in TABLES.glob("*.json")}
        for lang, t in tables.items():
            for k in [k for k in t if not k.startswith("_") and f'"{k}"' not in code]:
                del t[k]
            save_table(lang, t)
        save_fixture(fixture)
        return
    if argv[:1] == ["drop"]:
        # keys the code no longer uses (a literal converted again by hand): out of every table and the fixture
        keys = set(argv[1:])
        for p in TABLES.glob("*.json"):
            t = load_table(p.stem)
            save_table(p.stem, {k: v for k, v in t.items() if k not in keys})
        save_fixture([e for e in load_fixture() if e["key"] not in keys])
        return
    if argv[:1] == ["rename"]:
        # a better key: rename OLD NEW [OLD NEW ...], in every table, the fixture and the code
        pairs = list(zip(argv[1::2], argv[2::2]))
        tables = {p.stem: load_table(p.stem) for p in TABLES.glob("*.json")}
        for old, new in pairs:
            if any(new in t for t in tables.values()):
                raise SystemExit(f"{new} exists already")
        fixture = load_fixture()
        for old, new in pairs:
            for t in tables.values():
                if old in t:
                    t[new] = t.pop(old)
            for e in fixture:
                if e["key"] == old:
                    e["key"] = new
        for p in SRC.rglob("*.kt"):
            src = p.read_text()
            out = src
            for old, new in pairs:
                out = out.replace(f'"{old}"', f'"{new}"')
            if out != src:
                p.write_text(out)
        for lang, t in tables.items():
            save_table(lang, t)
        save_fixture(fixture)
        return
    if argv[:1] == ["imports"]:
        # the l10n imports every source file needs for bi, inTarget and inBase (after converting by hand)
        for p in SRC.rglob("*.kt"):
            if SRC / "l10n" in p.parents:
                continue
            src = p.read_text()
            out = src
            for fn in ("bi", "inTarget", "inBase"):
                imp = f"import si.lanisce.lani.l10n.{fn}"
                if re.search(rf"(?<![\w.]){fn}\(", out) and imp not in out:
                    out = add_import(out, imp)
            if out != src:
                p.write_text(out)
        return
    if argv[:1] == ["set"]:
        _, key, s, e = argv
        sl, en = load_table("sl"), load_table("en")
        sl[key], en[key] = s, e
        save_table("sl", sl), save_table("en", en)
        return
    if argv[:1] == ["hand"]:
        # literals converted by hand, from a JSON file: [{"file", "key", "sl", "en", "old"?, "samples"?}], samples being
        # [[args, what the old literal's part rendered for them]]; without samples the old text was "sl · en"
        sl, en, fixture = load_table("sl"), load_table("en"), load_fixture()
        for h in json.loads(Path(argv[1]).read_text()):
            key, s, e = h["key"], h["sl"], h["en"]
            if key in sl and (sl[key], en.get(key)) != (s, e):
                raise SystemExit(f"{key} holds other texts already: {sl[key]!r} · {en.get(key)!r}")
            sl[key], en[key] = s, e
            # kind: "bi" (a "sl · en" label), "target" (shown in Slovene alone) or "base" (English alone)
            kind = h.get("kind", "bi")
            samples = h.get("samples") or [[{}, {"bi": f"{s} · {e}", "target": s, "base": e}[kind]]]
            entry = {"file": h["file"], "key": key, "old": h.get("old", "(by hand)"),
                     "samples": [{"args": a, "expect": x} for a, x in samples]}
            fixture.append({**entry, "kind": kind} if kind != "bi" else entry)
        save_table("sl", sl), save_table("en", en), save_fixture(fixture)
        return
    dry = "--dry-run" in argv
    show = "--skipped" in argv
    files = []
    for a in (a for a in argv if not a.startswith("--")):
        p = (SRC / a if (SRC / a).exists() else Path(a)).resolve()
        if SRC not in p.parents:
            raise SystemExit(f"{a}: not in the app's sources ({SRC})")
        files += sorted(p.rglob("*.kt")) if p.is_dir() else [p]
    files = [f for f in files if SRC / "l10n" not in f.parents]  # the runtime joins its halves with " · " itself
    sl, en, fixture = load_table("sl"), load_table("en"), load_fixture()
    counts = count_everywhere()
    skipped = []
    before = len(fixture)
    for p in files:
        p = p.resolve()
        src = p.read_text()
        n, s = len(fixture), len(skipped)
        out = convert(p, src, sl, en, fixture, counts, skipped, show="--show" in argv)
        if "--summary" in argv and (len(fixture) > n or len(skipped) > s):
            print(f"{p.relative_to(SRC)}: {len(fixture) - n} converted, {len(skipped) - s} left")
        if out != src and not dry:
            p.write_text(out)
    if not dry:
        save_table("sl", sl), save_table("en", en), save_fixture(fixture)
    print(f"converted {len(fixture) - before} literals; {len(skipped)} left for converting by hand", file=sys.stderr)
    if show:
        print("\n".join(skipped))


if __name__ == "__main__":
    main(sys.argv[1:])
