// .claude/hooks/card.py: find a review card, show how the app asks and grades it, and fix it with its history and
// scheduling kept (find, show, overlaps, edit, regrade, merge, split, forgive). Prints JSON. Exit 0, 1 (invalid input
// or refused: nothing written), 2 (I/O error).
import { nodeRefusal } from './lease'
import { dateOf, FILE, homeLanguage, languageCode, languageDir, learned, normDate, rusty, saveJson } from './languages'
import { dataDir } from './paths'
import { Parser, SUPPRESS, type Namespace } from './pyargparse'
import { dumpIndented, dumpsCompact, loads } from './pyjson'
import {
  add, append, ceil, contains, count, cpLen, deepcopy, delItem, eq, F, fmtDate, fmtStamp, ge, getItem, int, isCombining,
  isDict, isExc, isInt, isList, isStr, iter, le, len, lower, max, min, mul, num, or, plusDays, PyDict, PyError, repr,
  round, S, setdefault, setItem, slice, sortedStrs, str as pyStr, strCmp, strip, strptime, sub, SysExit, truthy, typeName,
  unique, WS, type Py,
} from './py'
import { copy2, exists, expanduser, isUnder, mkdirp, parent, pjoin, pnorm, readText, resolve, type Ctx } from './sys'

const SR_FILE = FILE.sr
const MISTAKES_FILE = FILE.mistakes
/** ID.fullmatch and DATE.fullmatch (JS's $ without the m flag is the end only, as \Z). */
const ID = /^[A-Za-z0-9_][A-Za-z0-9_-]{0,127}$/
const DATE = /^\p{Nd}{4}-\p{Nd}{2}-\p{Nd}{2}$/u
const MAX_TEXT = 200

/** Invalid input or a change the card can't take: exit 1, nothing written. */
class Refused {
  constructor(readonly message: string) {}
}

// --- How the app reads a card (companion/android/.../data/ReviewPlanner.kt) ---------------------------------------

const paren = () => new RegExp(`[${WS}]*\\(([^)]*)\\)`, 'gu')
const SPACES = new RegExp(`[${WS}]+`, 'gu')
const PUNCTUATION = /[.,!?;:¡¿"„“”«»]/gu
const APOSTROPHE = /[’ʼ`´]/gu
const MAX_ANSWER = 40

/** PAREN.sub(repl, s) */
const parenSub = (s: Py, repl: string | ((g: string) => string)): string =>
  reStr(s).replace(paren(), (_m, g: string) => (typeof repl === 'string' ? repl : repl(g)))
/** A str for a regex call: Python's TypeError for anything else. */
function reStr(s: Py): string {
  if (typeof s !== 'string') throw new PyError('TypeError', `expected string or bytes-like object, got '${typeName(s)}'`)
  return s
}

/** Rules and placeholders ("dober/dobra/dobro → …", "Imenujem se ...") aren't phrases you can type. */
const isRule = (front: Py): boolean => [...'→♂♀…'].some(c => contains(front, c)) || contains(front, '...')

/** ReviewPlanner.accepted: the answers the app takes for a front. */
function accepted(front: Py): string[] {
  if (isRule(front)) return []
  const out: string[] = []
  for (const alt of S(front, 'split').split(' / ')) {
    const bare = strip(parenSub(alt, ''))
    const full = strip(parenSub(alt, g => ' ' + g).replace(SPACES, ' '))
    out.push(bare, full)
  }
  return unique(out.filter(a => strip(a) && cpLen(a) <= MAX_ANSWER))
}

/** ReviewPlanner.meaning: the back without its notes. */
const meaning = (back: Py): string => strip(parenSub(back, '')) || (back as string)

/** What the back has in parentheses. */
function notes(back: Py): string {
  return [...reStr(back).matchAll(paren())].map(m => strip(m[1])).filter(Boolean).join('; ')
}

/** The back's meaning with [note] as its only note ("" removes the notes). */
function withNote(back: Py, note: string): string {
  const base = notes(back) || paren().test(reStr(back)) ? meaning(back) : strip(back)
  return strip(note) ? `${base} (${strip(note)})` : base
}

const normalize = (s: Py): string => strip(lower(s).replace(APOSTROPHE, "'").replace(PUNCTUATION, '').replace(SPACES, ' '))

/** normalize()d, without accents: for searching, not grading. */
const fold = (s: Py): string => [...normalize(s).normalize('NFD')].filter(c => !isCombining(c)).join('')

/** A card's front or back as text to search: a str as it is; anything else (none, a number, a list) has none. */
const text = (v: Py): string => (isStr(v) ? v : '')

/** Dashboard.parse: an error pattern's content is the learner's wrong answer, never shown as a card. */
const shown = (item: PyDict): boolean => item.get('type') !== 'error_pattern' && isStr(item.get('content')) && isStr(item.get('answer'))

function variants(item: PyDict): string[] {
  if (!shown(item)) return []
  if (item.get('type') === COUNTING) return countingOf(item) ? ['counting', 'counting_type'] : []
  if (item.get('type') === FORMS) return formsOf(item) ? ['forms_card', 'forms_card_type'] : []
  const acc = accepted(item.get('content'))
  if (item.get('type', 'vocabulary') !== 'vocabulary' || !acc.length) return ['flip']
  const out = ['flip', 'recognize', 'listen', 'pick_slovene', 'type', 'dictation', 'speak']
  if (acc[0].split(' ').length >= 2) out.splice(4, 0, 'tiles')
  return out
}

const COUNTING = 'counting'
const COUNT_SLOTS: [string, string][] = [['1', 'nom.sg'], ['2', 'nom.du'], ['3', 'nom.pl'], ['5+', 'gen.pl']]

function countingOf(item: PyDict): PyDict | null {
  const c = item.get('counting')
  if (!isDict(c) || !['m', 'f', 'n'].includes(c.get('gender') as string) || !(isStr(c.get('lemma')) && strip(c.get('lemma')))) return null
  const lines = c.get('lines')
  const forms = c.get('forms')
  if (!(isList(lines) && lines.length === 4 && lines.every(x => isStr(x) && strip(x).includes(' ')))) return null
  if (!(isDict(forms) && COUNT_SLOTS.every(([, k]) => {
    const f = forms.get(k)
    return isList(f) && iter(getItem(forms, k)).some(x => isStr(x) && strip(x))
  }))) return null
  return c
}

const D = PyDict.of

function countingView(item: PyDict): PyDict {
  const c = countingOf(item)
  if (c === null) return D({ shown: false, why: 'a counting card without its forms (counting: gender, lines, forms): the app skips it' })
  return D({
    shown: true,
    front: getItem(item, 'content'),
    back: getItem(item, 'answer'),
    how: 'four steps: the numeral for 1 (en / ena / eno, by the gender), then the whole phrase for 2, 3 and 5; chosen '
      + 'among three (the other numeral, another number\'s ending) until the card is secure (two good reviews in a '
      + 'row, the last without a slip), typed after',
    gender: getItem(c, 'gender'),
    lines: getItem(c, 'lines'),
    forms: new PyDict(COUNT_SLOTS.map(([n, k]) => [n, getItem(getItem(c, 'forms'), k)] as const)),
    table: c.get('table'),
    variants: variants(item),
    fix: 'the forms are the dictionary\'s (companion/lexicon/sl.json, the forms book sl.forms.json): fix them there, '
      + 'then the card again (delete it: the app asks the bridge for a new one); never edit the forms by hand here',
  })
}

const FORMS = 'forms'
const OWN_SLOTS = ['nom.sg', 'adj.m.sg']

function formsOf(item: PyDict): PyDict | null {
  const f = item.get('forms')
  if (!isDict(f) || !['noun', 'verb', 'adj'].includes(f.get('pos') as string) || !(isStr(f.get('lemma')) && strip(f.get('lemma')))) return null
  const slots = f.get('slots')
  if (!isList(slots) || !slots.length) return null
  for (const sl of slots) {
    if (!(isDict(sl) && isStr(sl.get('key')) && isStr(sl.get('page')) && isList(sl.get('forms'))
      && (sl.get('forms') as Py[]).some(x => isStr(x) && strip(x)))) return null
  }
  return f
}

function formsView(item: PyDict): PyDict {
  const f = formsOf(item)
  if (f === null) return D({ shown: false, why: 'a forms card without its table (forms: lemma, pos, slots with key, forms, page): the app skips it' })
  const tested = iter(f.get('tested', [])).filter(isStr)
  const slots = new PyDict()
  for (const sl of getItem(f, 'slots') as PyDict[]) {
    const v = new PyDict()
    v.set('forms', getItem(sl, 'forms'))
    if (truthy(sl.get('also'))) v.set('also', getItem(sl, 'also'))
    v.set('page', getItem(sl, 'page'))
    if (truthy(sl.get('needs'))) v.set('needs', getItem(sl, 'needs'))
    if (truthy(sl.get('composed'))) v.set('composed', "biti's future + the l-form, both the dictionary's")
    v.set('tested', contains(tested, getItem(sl, 'key')))
    if (OWN_SLOTS.includes(getItem(sl, 'key') as string)) v.set('own', 'the word itself: given')
    slots.set(getItem(sl, 'key') as string, v)
  }
  return D({
    shown: true,
    front: getItem(item, 'content'),
    back: getItem(item, 'answer'),
    how: 'the slots whose grammar page (and the pages it needs) the learner was introduced to are gaps, the word '
      + 'itself given; a part (a row or two, at most 6 gaps) per review, the parts in turn, a resurfaced card\'s '
      + 'new part first; the card shows only the sections of the table (the present, the negative, the future, '
      + 'the past, the imperative; a noun\'s cases) with a gap asked now, the others hidden, and in them a slot '
      + 'not introduced yet greyed, to read ahead; each gap chosen among three of the word\'s forms until the '
      + 'card is secure (two good reviews in a row, the last without a slip), typed after (a new part chosen); '
      + 'any form of the slot is right, its `also` too. The whole table is the app\'s overview ("📋 Vse oblike" '
      + 'on the word\'s card and after the card), to read, never graded. The app works out what is tested from '
      + 'the learner\'s grammar book and course; `tested` is what the bridge last saw',
    pos: f.get('pos'),
    gender: f.get('gender'),
    slots,
    tested,
    fresh: or(f.get('fresh'), []),
    unlocked: f.get('unlocked'),
    table: f.get('table'),
    asked: truthy(f.get('asked')),
    variants: variants(item),
    fix: 'the forms are the dictionary\'s (companion/lexicon/sl.json, the forms book sl.forms.json; a composed future '
      + 'is biti\'s future there and the verb\'s l-form): fix them there, then delete the card (the app asks the '
      + 'bridge for a new one, made from the fixed forms); never edit the forms by hand here',
  })
}

function appView(item: PyDict): PyDict {
  if (item.get('type') === 'error_pattern') {
    return D({ shown: false, why: 'an error pattern: its content is the learner\'s wrong answer; the app never shows it as a card (modules drill it)' })
  }
  if (!shown(item)) return D({ shown: false, why: 'its front or back (content, answer) is missing or not a text: the app skips the card' })
  if (item.get('type') === COUNTING) return countingView(item)
  if (item.get('type') === FORMS) return formsView(item)
  const front = getItem(item, 'content')
  const back = getItem(item, 'answer')
  const acc = accepted(front)
  return D({
    shown: true,
    front,
    back,
    prompt: meaning(back),
    note: notes(back) || null,
    accepted: acc,
    first: acc.length ? acc[0] : null,
    variants: variants(item),
    rusty: rusty(item),
    learned: learned(item),
  })
}

// --- Problems a card can have -------------------------------------------------------------------------------------

/** The meanings a back asks for, compared loosely ("the house" and "house" alike): a set, as the Python's. */
function promptParts(back: Py): string[] {
  const out: string[] = []
  for (const p of meaning(back).split(/ \/ |[,;]/u)) {
    const q = normalize(p).replace(/^(a|an|the|to) /u, '')
    if (q && !out.includes(q)) out.push(q)
  }
  return out
}

const quizzed = (item: PyDict) => variants(item).length > 1

function overlap(a: PyDict, b: PyDict): PyDict | null {
  if (!(shown(a) && shown(b))) return null
  if ([COUNTING, FORMS].includes(a.get('type') as string) || [COUNTING, FORMS].includes(b.get('type') as string)) return null
  let samePrompt: string[] = []
  if (quizzed(a) || quizzed(b)) {
    const pb = promptParts(getItem(b, 'answer'))
    samePrompt = sortedStrs(promptParts(getItem(a, 'answer')).filter(x => pb.includes(x)))
  }
  const ab = new Set(accepted(getItem(b, 'content')).map(normalize))
  const sameAnswer = sortedStrs(new Set(accepted(getItem(a, 'content')).map(normalize).filter(x => ab.has(x))))
  if (!samePrompt.length && !sameAnswer.length) return null
  const out = new PyDict()
  if (samePrompt.length) out.set('same_prompt', samePrompt)
  if (sameAnswer.length) out.set('same_answer', sameAnswer)
  return out
}

function overlapsOf(cid: string, its: PyDict): PyDict[] {
  const item = getItem(its, cid) as PyDict
  const out: PyDict[] = []
  for (const [oid, other] of its.items()) {
    if (oid === cid || !isDict(other)) continue
    const o = overlap(item, other)
    if (o && o.size) {
      const e = D({ id: oid, front: other.get('content'), back: other.get('answer') })
      e.update(o)
      out.push(e)
    }
  }
  return out
}

function problems(item: PyDict): string[] {
  if (item.get('type') === 'error_pattern') {
    return ['an error pattern: the app never shows it as a card (its content is the learner\'s wrong answer); fix the practice that drills it instead']
  }
  const odd = ([['front', 'content'], ['back', 'answer']] as const)
    .filter(([, key]) => item.get(key) !== null && !isStr(item.get(key)))
    .map(([side, key]) => `the ${side} is not a text (${key}: ${repr(item.get(key))}): the app skips the card`)
  if (odd.length) return odd
  if (item.get('type') === COUNTING) return countingOf(item) ? [] : ['a counting card without its forms (counting: gender, lines, forms): the app skips it']
  if (item.get('type') === FORMS) return formsOf(item) ? [] : ['a forms card without its table (forms: lemma, pos, slots with key, forms, page): the app skips it']
  const front = or(item.get('content'), '')
  const back = or(item.get('answer'), '')
  const out: string[] = []
  if (!strip(front) || !strip(back)) {
    out.push('an empty front or back: the app skips the card')
    return out
  }
  const f = front as string
  for (const [side, text] of [['front', f], ['back', back as string]]) {
    if (count(text, '(') !== count(text, ')')) out.push(`the ${side} has unbalanced parentheses`)
  }
  if (new RegExp(`[^${WS}]/|/[^${WS}]`, 'u').test(f) && !isRule(f)) {
    out.push("the front has '/' without spaces: it is not split into alternatives (write ' / ')")
  }
  const alts = f.split(' / ')
  if (isRule(f)) out.push('a rule or placeholder (→ ♂ ♀ … or ...): the app shows it as a flashcard only, nothing to type')
  else if (!accepted(f).length) out.push(`no alternative of the front is ${MAX_ANSWER} letters or shorter: nothing to type, flashcard only`)
  else {
    const long = alts.filter(a => cpLen(strip(parenSub(a, ''))) > MAX_ANSWER)
    if (long.length) out.push(`alternatives over ${MAX_ANSWER} letters are not accepted: ${repr(long)}`)
  }
  if (item.get('type', 'vocabulary') !== 'vocabulary') {
    out.push(`type ${repr(item.get('type'))}: only vocabulary cards get quiz variants; this one is a flashcard only`)
  }
  if (meaning(back).includes(' / ') && alts.length >= 2) {
    out.push(`the prompt asks for several meanings at once (${repr(meaning(back))}) but every answer is one alternative of the front: `
      + 'split it into one card per meaning')
  }
  if (strip(parenSub(back, '')) === '') out.push('the back is only a note: the app asks with the note itself')
  return out
}

// --- SM-2 (update-db.py's, for replaying a card's history) ---------------------------------------------------------

const SM2_FIELDS = ['easiness_factor', 'interval_days', 'repetitions', 'due_date', 'last_reviewed', 'last_quality',
  'total_reviews', 'consecutive_correct', 'consecutive_incorrect', 'mastery_level']

function sm2(ef: Py, interval: Py, reps: Py, quality: Py): [Py, Py, Py] {
  if (ge(quality, 3)) {
    if (eq(reps, 0)) interval = 1
    else if (eq(reps, 1)) interval = 6
    else interval = int(ceil(mul(interval, ef)))
    reps = add(reps, 1)
  } else {
    reps = 0
    interval = 1
  }
  ef = max(F(1.3), add(ef, sub(F(0.1), mul(sub(5, quality), add(F(0.08), mul(sub(5, quality), F(0.02)))))))
  return [round(ef, 2), interval, reps]
}

/** The scheduling a card has after [history], from the state update-db.py gives a new item. */
function replay(item: PyDict, history: Py[]): PyDict {
  const st = D({
    easiness_factor: F(2.5), interval_days: 1, repetitions: 0, total_reviews: 0, consecutive_correct: 0,
    consecutive_incorrect: item.get('type') === 'error_pattern' ? 1 : 0, mastery_level: 0,
  })
  for (const e of history) {
    const q = int(getItem(e, 'quality'))
    const [ef, interval, reps] = sm2(st.get('easiness_factor'), st.get('interval_days'), st.get('repetitions'), q)
    st.set('easiness_factor', ef)
    st.set('interval_days', interval)
    st.set('repetitions', reps)
    st.set('due_date', plusDaysStr(getItem(e, 'date'), st.get('interval_days')))
    st.set('last_reviewed', getItem(e, 'date'))
    st.set('last_quality', q)
    st.set('total_reviews', add(st.get('total_reviews'), 1))
    if (ge(q, 3)) {
      st.set('consecutive_correct', add(st.get('consecutive_correct'), 1))
      st.set('consecutive_incorrect', 0)
    } else {
      st.set('consecutive_incorrect', add(st.get('consecutive_incorrect'), 1))
      st.set('consecutive_correct', 0)
    }
    const current = st.get('mastery_level')
    if (ge(st.get('repetitions'), 5) && ge(st.get('consecutive_correct'), 3)) st.set('mastery_level', min(5, max(current, 3)))
    else if (ge(st.get('repetitions'), 2) && ge(st.get('consecutive_correct'), 1) && ge(q, 4)) st.set('mastery_level', min(5, add(current, 1)))
  }
  return st
}

const schedule = (item: PyDict): PyDict => new PyDict(SM2_FIELDS.map(k => [k, item.get(k)] as const))

// --- Files --------------------------------------------------------------------------------------------------------

const plusDaysStr = (day: Py, n: Py): string => fmtDate(plusDays(strptime(day), n))

function load(path: string): PyDict {
  let data: Py
  try {
    data = loads(readText(path))
  } catch (e) {
    if (isExc(e, 'FileNotFoundError')) throw new PyError('OSError', `${path} does not exist`)
    if (isExc(e, 'JSONDecodeError')) throw new PyError('OSError', `${path} is not valid JSON: ${(e as PyError).msg}`)
    throw e
  }
  if (!isDict(data)) throw new PyError('OSError', `${path} is not a JSON object`)
  return data
}

class Store {
  readonly dir: string
  readonly language: string | null = null

  constructor(
    readonly ctx: Ctx,
    readonly root: string,
    language: Py,
  ) {
    this.dir = root
    if (truthy(language)) {
      const code = languageCode(language)
      if (code === null) throw new Refused(`--language: a language code (it) or name (Italian), not ${repr(language)}`)
      let home: string | null = null
      const profile = pjoin(root, FILE.profile)
      if (exists(profile)) home = homeLanguage(load(profile))
      if (code !== home) {
        this.language = code
        this.dir = languageDir(root, code)
      }
    }
  }

  path(name: string): string {
    return pjoin(this.dir, name)
  }

  sr(): PyDict {
    const data = load(this.path(SR_FILE))
    if (!isDict(data.get('items'))) throw new PyError('OSError', `${this.path(SR_FILE)} has no items`)
    return data
  }

  /** Copies of [names] in .backups/pre-card-<command>-<time>/, under their path in the data directory. */
  backup(command: string, names: string[]): string {
    const tag = `pre-card-${command}-${fmtStamp(this.ctx.now())}`
    let dest = pjoin(this.root, '.backups', tag)
    let n = 1
    while (exists(dest)) {
      n += 1
      dest = pjoin(this.root, '.backups', `${tag}-${n}`)
    }
    for (const name of names) {
      const src = this.path(name)
      if (exists(src)) {
        const rel = relativeTo(src, this.root)
        const target = pjoin(dest, rel)
        mkdirp(parent(target))
        copy2(src, target)
      }
    }
    return dest
  }
}

/** PurePath.relative_to: [p] under [base] (a ValueError otherwise). */
function relativeTo(p: string, base: string): string {
  if (!isUnder(p, base)) throw new PyError('ValueError', `${repr(pnorm(p))} is not in the subpath of ${repr(pnorm(base))}`)
  const a = pnorm(p).split('/')
  const b = pnorm(base) === '/' ? [''] : pnorm(base).split('/')
  return a.slice(b.length).join('/') || '.'
}

/** update-db.py's review_queue, from the due dates. */
function rebuildQueue(sr: PyDict, today: string) {
  const queue = D({ today: [], tomorrow: [], this_week: [], later: [] })
  const tom = plusDaysStr(today, 1)
  const weekEnd = plusDaysStr(today, 7)
  for (const [iid, item] of (getItem(sr, 'items') as PyDict).items()) {
    const due = isDict(item) ? normDate(item.get('due_date', today)) : today
    const key = le(due, today) ? 'today' : eq(due, tom) ? 'tomorrow' : le(due, weekEnd) ? 'this_week' : 'later'
    ;(queue.get(key) as Py[]).push(iid)
  }
  sr.set('review_queue', queue)
  const meta = setdefault(sr, 'metadata', new PyDict())
  setItem(meta, 'last_updated', today)
  setItem(meta, 'total_items_tracked', len(getItem(sr, 'items')))
}

// --- Commands -----------------------------------------------------------------------------------------------------

type Args = Namespace
const arg = (a: Args, k: string): Py => (a.has(k) ? (a.get(k) as Py) : null)
const sarg = (a: Args, k: string): string => arg(a, k) as string

function cardOf(its: PyDict, cid: string): PyDict {
  const item = its.get(cid)
  if (!isDict(item)) {
    const close = its.keys().filter(i => i.toLowerCase().includes(cid.toLowerCase())).slice(0, 5)
    throw new Refused(`no card ${repr(cid)}` + (close.length ? ` (did you mean ${close.join(', ')}?)` : ' (card.py find <text>)'))
  }
  return item
}

function summary(cid: string, item: PyDict): PyDict {
  const out = D({ id: cid, type: item.get('type'), front: item.get('content'), back: item.get('answer') })
  if (shown(item)) {
    out.set('accepted', accepted(item.get('content')))
    out.set('prompt', meaning(item.get('answer')))
  }
  for (const k of ['due_date', 'interval_days', 'repetitions', 'last_quality', 'total_reviews']) out.set(k, item.get(k))
  out.set('rusty', rusty(item))
  return out
}

function cmdFind(store: Store, args: Args): PyDict {
  const query = fold(arg(args, 'text'))
  if (!query) throw new Refused('find: give some text (a word of the front or the back, or part of an id)')
  const its = getItem(store.sr(), 'items') as PyDict
  const hits: [number, string, string, PyDict][] = []
  for (const [cid, item] of its.items()) {
    if (!isDict(item)) continue
    const front = text(item.get('content'))
    const back = text(item.get('answer'))
    const answers = shown(item) ? [...accepted(front).map(fold), ...promptParts(back).map(fold)] : []
    const words = new Set(`${fold(front)} ${fold(back)}`.match(/[\p{L}\p{N}_'-]+/gu) ?? [])
    let score: number
    let how: string
    if (answers.includes(query)) [score, how] = [3, 'answer']
    else if (words.has(query) || query === fold(cid)) [score, how] = [2, 'word']
    else if (cpLen(query) >= 3 && (fold(cid).includes(query) || fold(front).includes(query) || fold(back).includes(query))) [score, how] = [1, 'text']
    else continue
    hits.push([score, cid, how, item])
  }
  hits.sort((a, b) => b[0] - a[0] || Number(!shown(a[3])) - Number(!shown(b[3])) || strCmp(a[1], b[1]))
  const limit = arg(args, 'limit')
  const cards = slice(hits, 0, num(limit)).map(([, cid, how, item]) => {
    const c = summary(cid, item)
    c.set('match', how)
    return c
  })
  return D({ query: arg(args, 'text'), language: store.language, count: hits.length, cards })
}

function cmdShow(store: Store, args: Args): PyDict {
  const its = getItem(store.sr(), 'items') as PyDict
  const id = sarg(args, 'id')
  const item = cardOf(its, id)
  return D({
    id,
    language: store.language,
    file: store.path(SR_FILE),
    card: item,
    app: appView(item),
    problems: problems(item),
    overlaps: shown(item) ? overlapsOf(id, its) : [],
  })
}

function cmdOverlaps(store: Store, args: Args): PyDict {
  const its = new PyDict((getItem(store.sr(), 'items') as PyDict).items().filter(([, v]) => isDict(v)))
  const id = arg(args, 'id')
  if (truthy(id)) {
    cardOf(its, id as string)
    return D({ id, overlaps: overlapsOf(id as string, its) })
  }
  const ids = sortedStrs(its.keys())
  const pairs: PyDict[] = []
  ids.forEach((a, i) => {
    for (const b of ids.slice(i + 1)) {
      const A = its.get(a) as PyDict
      const B = its.get(b) as PyDict
      const o = overlap(A, B)
      if (o && o.size) {
        const p = D({ ids: [a, b], fronts: [getItem(A, 'content'), getItem(B, 'content')], backs: [getItem(A, 'answer'), getItem(B, 'answer')] })
        p.update(o)
        pairs.push(p)
      }
    }
  })
  return D({ count: pairs.length, pairs })
}

function checkedText(value: Py, what: string): string | null {
  if (value === null) return null
  const v = value as string
  const text = v.includes('\n') ? null : strip(v.replace(SPACES, ' '))
  if (text === null) throw new Refused(`${what}: one line, no line breaks`)
  if (!text) throw new Refused(`${what}: empty`)
  if (cpLen(text) > MAX_TEXT) throw new Refused(`${what}: ${cpLen(text)} letters; at most ${MAX_TEXT}`)
  if (count(text, '(') !== count(text, ')')) throw new Refused(`${what}: unbalanced parentheses in ${repr(text)}`)
  return text
}

/** Applies --front/--back/--note/--category (or --new-… with [prefix]); the old texts, or null when nothing changes. */
function retext(item: PyDict, args: Args, today: string, action: string, prefix = ''): PyDict | null {
  const front = checkedText(arg(args, `${prefix}front`), `--${prefix}front`)
  let back = checkedText(arg(args, `${prefix}back`), `--${prefix}back`)
  const note = arg(args, `${prefix}note`) as string | null
  const category = !prefix ? (arg(args, 'category') as string | null) : null
  if (note !== null) {
    if (note.includes('\n') || note.includes('(') || note.includes(')')) {
      throw new Refused(`--${prefix}note: one line, without parentheses (they are added)`)
    }
    back = withNote(back !== null ? back : or(item.get('answer'), ''), note)
  }
  const before = D({ content: item.get('content'), answer: item.get('answer'), category: item.get('category') })
  const after = before.copy()
  if (front !== null) after.set('content', front)
  if (back !== null) after.set('answer', back)
  if (category !== null) after.set('category', strip(category))
  if (eq(after, before)) return null
  for (const [k, v] of after.items()) if (v !== null) item.set(k, v)
  const entry = D({ date: today, action })
  for (const [k, v] of before.items()) if (v !== null && !eq(v, after.get(k))) entry.set(k, v)
  if (truthy(arg(args, 'reason'))) entry.set('reason', strip(arg(args, 'reason')))
  append(setdefault(item, 'edits', []), entry)
  return before
}

function textWarnings(cid: string, item: PyDict, its: PyDict): string[] {
  const out = problems(item)
  for (const o of shown(item) ? overlapsOf(cid, its) : []) {
    const what = o.items().filter(([k]) => k.startsWith('same_')).map(([k, v]) => `${k.replaceAll('_', ' ')} ${pyStr(v)}`).join(' and ')
    out.push(`overlaps ${pyStr(o.get('id'))} (${repr(o.get('front'))} = ${repr(o.get('back'))}): ${what}`)
  }
  return out
}

/** Backs up and writes [data] (file name → content), unless --dry-run. */
function finish(store: Store, args: Args, command: string, names: string[], data: Map<string, PyDict>, result: PyDict): PyDict {
  result.set('dry_run', truthy(arg(args, 'dry_run')))
  if (truthy(arg(args, 'dry_run'))) return result
  // The lease (lani_lease.py, lease.ts): while another side holds the data, this side only reads it.
  const refusal = nodeRefusal(store.ctx, store.root)
  if (refusal) throw new Refused(`not written: ${refusal}`)
  result.set('backup', store.backup(command, names))
  for (const name of names) saveJson(store.path(name), data.get(name) as PyDict)
  result.set('written', names.map(n => store.path(n)))
  return result
}

const isVocabulary = (item: PyDict) => item.get('type', 'vocabulary') === 'vocabulary'

function cmdEdit(store: Store, args: Args): PyDict {
  const sr = store.sr()
  const its = getItem(sr, 'items') as PyDict
  const id = sarg(args, 'id')
  const item = cardOf(its, id)
  const before = retext(item, args, sarg(args, 'today'), 'edit')
  if (before === null) throw new Refused('edit: nothing to change (give --front, --back, --note or --category with a new text)')
  if (shown(item) && !accepted(item.get('content')).length && isVocabulary(item) && !truthy(arg(args, 'force'))) {
    throw new Refused(`edit: the new front ${repr(item.get('content'))} has no answer the app can take (a rule or placeholder, or too long); `
      + 'add --force if it should be a flashcard only')
  }
  const result = D({
    id,
    before,
    after: new PyDict(before.keys().map(k => [k, item.get(k)] as const)),
    app: appView(item),
    warnings: textWarnings(id, item, its),
    kept: schedule(item),
  })
  return finish(store, args, 'edit', [SR_FILE], new Map([[SR_FILE, sr]]), result)
}

function regradeFirst(store: Store, args: Args, sr: PyDict, item: PyDict): PyDict {
  const id = sarg(args, 'id')
  const quality = arg(args, 'quality')
  if (truthy(arg(args, 'date'))) throw new Refused(`regrade: ${id} has no reviews; --date is for a review`)
  const old = item.get('last_quality')
  if (eq(old, quality)) throw new Refused(`regrade: ${id}'s first grade is ${pyStr(old)} already`)
  const before = D({ last_quality: old, learned: learned(item) })
  item.set('last_quality', quality)
  const entry = D({ date: arg(args, 'today'), action: 'regrade', last_quality: old })
  if (truthy(arg(args, 'reason'))) entry.set('reason', strip(arg(args, 'reason')))
  append(setdefault(item, 'edits', []), entry)
  const result = D({
    id,
    first_grade: D({ from: old, to: quality }),
    before,
    after: D({ last_quality: quality, learned: learned(item) }),
  })
  return finish(store, args, 'regrade', [SR_FILE], new Map([[SR_FILE, sr]]), result)
}

function cmdRegrade(store: Store, args: Args): PyDict {
  const sr = store.sr()
  const id = sarg(args, 'id')
  const quality = arg(args, 'quality')
  const today = sarg(args, 'today')
  const item = cardOf(getItem(sr, 'items') as PyDict, id)
  const history = item.get('review_history')
  if ((!isList(history) || !history.length) && !truthy(item.get('total_reviews'))) return regradeFirst(store, args, sr, item)
  if (!isList(history) || !history.length) {
    throw new Refused(`regrade: ${id} was reviewed (${pyStr(item.get('total_reviews'))}) but has no review history to replay`)
  }
  if (!history.every(e => isDict(e) && dateOf(e.get('date')) && isInt(e.get('quality')))) {
    throw new Refused(`regrade: ${id} has a review history entry without a date or quality`)
  }
  const hist = history as PyDict[]
  let index: number
  const date = arg(args, 'date')
  if (truthy(date)) {
    const at = hist.map((e, i) => [e, i] as const).filter(([e]) => eq(normDate(getItem(e, 'date')), date)).map(([, i]) => i)
    if (!at.length) throw new Refused(`regrade: ${id} has no review on ${pyStr(date)} (reviews: ${hist.map(e => S(getItem(e, 'date'), 'join')).join(', ')})`)
    index = at[at.length - 1]
  } else index = hist.length - 1
  const old = getItem(hist[index], 'quality')
  if (eq(old, quality)) throw new Refused(`regrade: the review on ${pyStr(getItem(hist[index], 'date'))} already has quality ${pyStr(old)}`)
  const replayed = replay(item, hist)
  const stored = new PyDict(replayed.keys().map(k => [k, item.get(k)] as const))
  if (!eq(replayed, stored) && !truthy(arg(args, 'force'))) {
    const differ = new PyDict()
    for (const k of replayed.keys()) {
      if (!eq(stored.get(k), replayed.get(k))) differ.set(k, D({ stored: stored.get(k), from_history: replayed.get(k) }))
    }
    throw new Refused("regrade: the card's scheduling doesn't follow from its review history (changed by hand?), so a replay "
      + `would lose that: ${dumpsCompact(differ)}. Add --force to replay anyway.`)
  }
  const before = schedule(item)
  const wasRusty = rusty(item)
  const oldIncorrect = item.get('consecutive_incorrect', 0)
  const entry = hist[index]
  entry.set('quality', quality)
  const regraded = D({ from: old, date: today })
  if (truthy(arg(args, 'reason'))) regraded.set('reason', strip(arg(args, 'reason')))
  entry.set('regraded', regraded)
  item.update(replay(item, hist))
  if (ge(getItem(item, 'consecutive_incorrect'), 2)) item.set('priority', 'high')
  else if (ge(getItem(item, 'mastery_level'), 3)) item.set('priority', 'low')
  else if (ge(oldIncorrect, 2) && item.get('priority') === 'high') item.set('priority', 'medium')
  rebuildQueue(sr, today)
  const result = D({
    id,
    review: D({ date: getItem(entry, 'date'), quality: D({ from: old, to: quality }) }),
    before,
    after: schedule(item),
    rusty: D({ before: wasRusty, after: rusty(item) }),
    note: 'the review\'s session keeps its score in progress-db and the session log; only the card changes',
  })
  return finish(store, args, 'regrade', [SR_FILE], new Map([[SR_FILE, sr]]), result)
}

function cmdMerge(store: Store, args: Args): PyDict {
  const sr = store.sr()
  const its = getItem(sr, 'items') as PyDict
  const keepId = sarg(args, 'keep')
  const dropId = sarg(args, 'drop')
  const today = sarg(args, 'today')
  const force = truthy(arg(args, 'force'))
  if (keepId === dropId) throw new Refused('merge: two different cards')
  const keep = cardOf(its, keepId)
  const drop = cardOf(its, dropId)
  for (const [cid, it] of [[keepId, keep], [dropId, drop]] as const) {
    if (!shown(it) && !force) throw new Refused(`merge: ${cid} is an error pattern, not a card (add --force to merge it anyway)`)
  }
  const before = retext(keep, args, today, 'merge')
    ?? D({ content: keep.get('content'), answer: keep.get('answer'), category: keep.get('category') })
  if (shown(keep) && shown(drop) && !accepted(keep.get('content')).length && isVocabulary(keep) && !force) {
    throw new Refused(`merge: the kept front ${repr(keep.get('content'))} has no answer the app can take; add --force for a flashcard only`)
  }
  const record = D({ date: today, id: dropId, item: deepcopy(drop) })
  if (truthy(arg(args, 'reason'))) record.set('reason', strip(arg(args, 'reason')))
  append(setdefault(keep, 'merged', []), record)
  delItem(its, dropId)
  rebuildQueue(sr, today)
  const warnings = textWarnings(keepId, keep, its)
  const kept = new Set(accepted(or(keep.get('content'), '')).map(normalize))
  const lost = accepted(or(drop.get('content'), '')).filter(a => !kept.has(normalize(a)))
  if (lost.length) warnings.push(`no card takes ${repr(lost)} any more: a word pack with it will teach it again`)
  warnings.push(`a review of ${dropId} still in the app (not yet sent) is ignored when it arrives`)
  const result = D({
    kept: keepId,
    dropped: dropId,
    before,
    after: new PyDict(['content', 'answer', 'category'].map(k => [k, keep.get(k)] as const)),
    schedule: schedule(keep),
    app: appView(keep),
    warnings,
  })
  return finish(store, args, 'merge', [SR_FILE], new Map([[SR_FILE, sr]]), result)
}

function cmdSplit(store: Store, args: Args): PyDict {
  const sr = store.sr()
  const its = getItem(sr, 'items') as PyDict
  const id = sarg(args, 'id')
  const newId = sarg(args, 'new_id')
  const today = sarg(args, 'today')
  const item = cardOf(its, id)
  if (!shown(item)) throw new Refused(`split: ${id} is an error pattern, not a card`)
  if (!ID.test(newId)) throw new Refused(`split: --new-id ${repr(newId)}: letters, digits, _ and - (e.g. vocab_ne)`)
  if (its.has(newId)) throw new Refused(`split: ${newId} exists already (merge into it, or pick another id)`)
  const before = retext(item, args, today, 'split')
    ?? D({ content: item.get('content'), answer: item.get('answer'), category: item.get('category') })
  const fresh = D({ content: '', answer: '' })
  retext(fresh, args, today, 'split', 'new_')
  if (!truthy(fresh.get('content')) || !truthy(fresh.get('answer'))) throw new Refused('split: the new card needs --new-front and --new-back')
  if (fresh.has('edits')) fresh.delete('edits')
  its.set(newId, D({
    id: newId,
    type: item.get('type', 'vocabulary'),
    content: fresh.get('content'),
    answer: fresh.get('answer'),
    category: item.get('category', ''),
    difficulty: item.get('difficulty', ''),
    created_date: today,
    due_date: plusDaysStr(today, 1),
    interval_days: 1,
    repetitions: 0,
    easiness_factor: F(2.5),
    consecutive_correct: 0,
    consecutive_incorrect: 0,
    last_reviewed: today,
    last_quality: 3,
    mastery_level: 0,
    total_reviews: 0,
    priority: 'medium',
    split_from: id,
  }))
  const added = its.get(newId) as PyDict
  for (const [cid, it] of [[id, item], [newId, added]] as const) {
    if (isVocabulary(it) && !accepted(getItem(it, 'content')).length && !truthy(arg(args, 'force'))) {
      throw new Refused(`split: the front ${repr(it.get('content'))} of ${cid} has no answer the app can take; add --force for a flashcard only`)
    }
  }
  rebuildQueue(sr, today)
  const result = D({
    id,
    before,
    after: new PyDict(['content', 'answer', 'category'].map(k => [k, item.get(k)] as const)),
    kept: schedule(item),
    new: added,
    warnings: [...textWarnings(id, item, its), ...textWarnings(newId, added, its)],
  })
  return finish(store, args, 'split', [SR_FILE], new Map([[SR_FILE, sr]]), result)
}

function cmdForgive(store: Store, args: Args): PyDict {
  const mistakes = load(store.path(MISTAKES_FILE))
  const pattern = sarg(args, 'pattern')
  const today = sarg(args, 'today')
  const answer = arg(args, 'answer')
  const patterns = mistakes.get('error_patterns')
  if (!isDict(patterns) || !isDict(patterns.get(pattern))) {
    const known = isDict(patterns) ? sortedStrs(patterns.keys()) : []
    throw new Refused(`forgive: no error pattern ${repr(pattern)} in ${MISTAKES_FILE}` + (known.length ? ` (patterns: ${known.join(', ')})` : ''))
  }
  const pat = patterns.get(pattern) as PyDict
  const examples = iter(pat.get('examples', [])).filter(isDict) as PyDict[]
  const date = arg(args, 'date')
  // dates compared as dates: one written before they were zero-padded ("2026-9-1") too
  const day = truthy(date) ? date : examples.length ? max(...examples.map(e => normDate(e.get('date', '')))) : ''
  const matches = iter(pat.get('examples', []))
    .map((e, i) => [e, i] as const)
    .filter(([e]) => isDict(e) && eq(normDate(e.get('date')), day) && (answer === null || normalize(e.get('incorrect', '')) === normalize(answer)))
    .map(([, i]) => i)
  if (!matches.length) {
    const shownEx = examples.map(e => D({ date: e.get('date'), incorrect: e.get('incorrect') }))
    throw new Refused(`forgive: ${pattern} has no example on ${pyStr(or(day, '?'))}${truthy(answer) ? ' with that answer' : ''}: ${dumpsCompact(shownEx)}`)
  }
  const before = new PyDict(['frequency', 'consecutive_incorrect', 'consecutive_correct', 'last_seen'].map(k => [k, pat.get(k)] as const))
  const list = getItem(pat, 'examples') as Py[]
  const removed = list.splice(matches[matches.length - 1], 1)[0]
  pat.set('frequency', max(0, sub(int(pat.get('frequency', 1)), 1)))
  pat.set('consecutive_incorrect', max(0, sub(int(pat.get('consecutive_incorrect', 1)), 1)))
  const left = list.filter(e => isDict(e) && truthy(e.get('date'))).map(e => (e as PyDict).get('date'))
  if (left.length) {
    const latest = max(...left.map(normDate))
    pat.set('last_seen', latest)
    pat.set('last_occurred', latest)
  }
  const names = [MISTAKES_FILE]
  const data = new Map<string, PyDict>([[MISTAKES_FILE, mistakes]])
  const result = D({ pattern, removed, before })
  if (eq(pat.get('frequency'), 0)) {
    patterns.delete(pattern)
    result.set('deleted', true)
    if (exists(store.path(SR_FILE))) {
      const sr = store.sr()
      const its = getItem(sr, 'items') as PyDict
      const it = its.get(pattern)
      if (isDict(it) && it.get('type') === 'error_pattern' && !truthy(it.get('total_reviews'))) {
        its.delete(pattern)
        rebuildQueue(sr, today)
        names.push(SR_FILE)
        data.set(SR_FILE, sr)
        result.set('deleted_review_item', pattern)
      }
    }
  } else {
    const entry = D({ date: today, example: removed })
    if (truthy(arg(args, 'reason'))) entry.set('reason', strip(arg(args, 'reason')))
    append(setdefault(pat, 'forgiven', []), entry)
    result.set('after', new PyDict(before.keys().map(k => [k, pat.get(k)] as const)))
  }
  const meta = setdefault(mistakes, 'metadata', new PyDict())
  setItem(meta, 'last_updated', today)
  setItem(meta, 'total_patterns_tracked', len(patterns))
  return finish(store, args, 'forgive', names, data, result)
}

const COMMANDS: Record<string, (store: Store, args: Args) => PyDict> = {
  find: cmdFind, show: cmdShow, overlaps: cmdOverlaps, edit: cmdEdit, regrade: cmdRegrade,
  merge: cmdMerge, split: cmdSplit, forgive: cmdForgive,
}

/** card.py's parser: the same actions in the same order (option prefixes, errors and texts depend on it). */
function parser(ctx: Ctx): Parser {
  const common = (p: Parser) => {
    p.arg('--data', { default: SUPPRESS })
    p.arg('--language', { default: SUPPRESS })
    p.arg('--today', { default: SUPPRESS })
  }
  const writes = (p: Parser) => {
    p.arg('--reason')
    p.add({ dest: 'dry_run', optionStrings: ['--dry-run'], nargs: 0, kind: 'store_true' })
    p.add({ dest: 'force', optionStrings: ['--force'], nargs: 0, kind: 'store_true' })
  }
  const texts = (p: Parser) => {
    p.arg('--front')
    p.arg('--back')
    p.arg('--note')
  }
  const ap = new Parser(ctx, 'card.py', 'card')
  common(ap)
  const subs = new Map<string, Parser>()
  ap.add({ dest: 'command', optionStrings: [], nargs: 'PARSER', kind: 'parsers', required: true, parsers: subs })
  const sub = (name: string, ...parents: ((p: Parser) => void)[]) => {
    const p = new Parser(ctx, `card.py ${name}`, `card ${name}`)
    for (const f of parents) f(p)
    subs.set(name, p)
    return p
  }
  let p = sub('find', common)
  p.arg('text')
  p.arg('--limit', { type: 'int', default: 20 })
  p = sub('show', common)
  p.arg('id')
  p = sub('overlaps', common)
  p.arg('id', { nargs: '?' })
  p = sub('edit', common, writes, texts)
  p.arg('id')
  p.arg('--category')
  p = sub('regrade', common, writes)
  p.arg('id')
  p.arg('--quality', { type: 'int', required: true, choices: [0, 1, 2, 3, 4, 5], metavar: '0-5' })
  p.arg('--date')
  p = sub('merge', common, writes, texts)
  p.arg('keep')
  p.arg('drop')
  p = sub('split', common, writes, texts)
  p.arg('id')
  p.arg('--new-id', { required: true })
  p.arg('--new-front', { required: true })
  p.arg('--new-back', { required: true })
  p.arg('--new-note')
  p = sub('forgive', common, writes)
  p.arg('pattern')
  p.arg('--date')
  p.arg('--answer')
  return ap
}

export function main(ctx: Ctx, argv: string[]): void {
  const args = parser(ctx).parseArgs(argv)
  for (const k of ['data', 'language', 'today']) if (!args.has(k)) args.set(k, null)
  let result: PyDict
  try {
    const today = args.get('today') as Py
    if (today === null) args.set('today', fmtDate(ctx.now()))
    else if (!DATE.test(today as string) || !dateOf(today)) throw new Refused('--today: YYYY-MM-DD')
    const date = args.get('date') ?? null
    if (truthy(date) && (!DATE.test(date as string) || !dateOf(date))) throw new Refused('--date: YYYY-MM-DD')
    // in the form the databases keep (ASCII digits): what is written and compared
    args.set('today', normDate(args.get('today') as Py))
    if (truthy(date)) args.set('date', normDate(date as Py))
    const data = args.get('data') as Py
    const root = truthy(data) ? resolve(expanduser(pnorm(data as string), ctx.env), ctx.cwd) : dataDir(ctx)
    ctx.dataRoot = root
    const store = new Store(ctx, root, args.get('language') as Py)
    result = COMMANDS[args.get('command') as string](store, args)
  } catch (e) {
    if (e instanceof Refused) {
      ctx.eprint(`[Lani] Error: ${e.message}`)
      throw new SysExit(1)
    }
    if (isExc(e, 'OSError')) {
      ctx.eprint(`[Lani] Error: ${(e as PyError).msg}`)
      throw new SysExit(2)
    }
    throw e
  }
  ctx.out.push(dumpIndented(result) + '\n')
  throw new SysExit(0)
}
