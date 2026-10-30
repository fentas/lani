// A Slovene noun's forms checked against the regular declension its gender implies (companion/README.md, "Word forms":
// "Checked forms"). Wiktionary's tables (companion/lexicon/sl.json) list old and dialect forms beside the standard ones
// (brati beside bratu, potoki beside potoku, bratam beside bratom, dve sestre beside dve sestri) and get a few words
// wrong outright (potok's accusative as potoka, sin's as sin, dekle as feminine, avto's plural as a neuter's). The forms
// book's hand-checked tables correct the words found wrong; this check confirms the rest, slot by slot:
// - the stem comes from the genitive singular (brat-a, ps-a, očet-a, sladkorj-a; žen-e, cerkv-e; stvar-i), and it has to
//   go with the nominative as the declension has it (a fleeting e: pes, ogenj, misel; a stem that goes on: oče, ime,
//   dekle; a masculine's -j-: sladkor, sladkorja);
// - a slot's forms that fit the regular endings are its standard forms (the soft stems' -em/-ev, a masculine's -ov- in
//   the plural and the -je plural, a fleeting e or i in the genitive plural, the i-declension's -em/-im, -eh/-ih);
// - the dictionary's other forms in that slot have to be a known old or dialect ending (a masculine's or neuter's -i in
//   the dative and locative singular, -am/-ah/-ama in the plural and dual, a soft stem's -om/-ov, a feminine dual in -e,
//   a feminine genitive plural in -a): variants, accepted, never shown as the answer;
// - a slot with no form that fits, or with a form that is neither (oka beside očesa, kola beside kolesa, mira beside
//   miru: two words or two declensions in one entry), isn't confirmed: it stays as the dictionary has it and is never
//   asked as a form to produce (the case trainer's "Sklanjaj!").
// A masculine noun's accusative singular is its genitive when it names a person or an animal; the forms book says which
// ([animate]): Wiktionary can't be trusted there. Pure.

/** One confirmed slot: its standard forms (they fit), the dictionary's others (old or dialect variants). */
export type CheckedSlot = { standard: string[]; variants: string[] }

/** What the check made of a noun: its stem, each slot it confirmed, the slots it couldn't and why none, when none. */
export type NounCheck = { stem: string | null; slots: Map<string, CheckedSlot>; unconfirmed: string[]; why?: string }

type Gender = 'm' | 'f' | 'n'
/** The feminine declensions: -a (žena, žene), -ev (cerkev, cerkve), the i-declension (stvar, stvari). */
type Kind = 'a' | 'ev' | 'i' | null

const SOFT = /[cčžšj]$/

/** The stem with a fleeting e before its last consonant or nj/lj (ps → pes, sestr → sester, ognj → ogenj), or i before a final j (ladj → ladij). */
function fleeting(stem: string): string[] {
  if (stem.length < 2) return []
  const out = [`${stem.slice(0, -1)}e${stem.slice(-1)}`]
  if (/[nl]j$/.test(stem) && stem.length > 2) out.push(`${stem.slice(0, -2)}e${stem.slice(-2)}`)
  if (stem.endsWith('j')) out.push(`${stem.slice(0, -1)}i${stem.slice(-1)}`)
  return out
}

/** Whether the nominative [lemma] goes with [stem] in a noun of [gender] and [kind]. */
function goesWith(lemma: string, gender: Gender, stem: string, kind: Kind): boolean {
  const S = stem
  if (gender === 'm') {
    // pes ← ps, ogenj ← ognj, avto ← avt, oče ← očet, sladkor ← sladkorj
    return lemma === S || fleeting(S).includes(lemma) || S === `${lemma}j` || (/[oe]$/.test(lemma) && S.startsWith(lemma.slice(0, -1)) && S.length <= lemma.length + 1)
  }
  // mesto ← mest, dekle ← deklet, ime ← imen, kolo ← koles
  if (gender === 'n') return /[oe]$/.test(lemma) && S.startsWith(lemma.slice(0, -1))
  if (kind === 'a') return lemma === `${S}a`
  if (kind === 'ev') return lemma.endsWith('ev') && fleeting(S).includes(lemma)
  return lemma === S || fleeting(S).includes(lemma)
}

/**
 * The regular forms of each slot ([fit]) and the old or dialect ones ([old]) of a noun of [gender] and [kind] with
 * [stem] (its genitive singular without the ending) and nominative [lemma]; [animate]: a masculine noun naming a
 * person or an animal. Null when [lemma] doesn't go with the stem.
 */
export function regularForms(lemma: string, gender: Gender, stem: string, kind: Kind, animate: boolean): { fit: Map<string, string[]>; old: Map<string, string[]> } | null {
  if (!goesWith(lemma, gender, stem, kind)) return null
  const S = stem
  const soft = SOFT.test(S)
  const o = soft ? 'e' : 'o'
  const fit = new Map<string, string[]>()
  const old = new Map<string, string[]>()
  const put = (into: Map<string, string[]>, keys: string[], forms: string[]) => keys.forEach(k => into.set(k, [...new Set([...(into.get(k) ?? []), ...forms])]))
  const set = (keys: string[], forms: string[]) => put(fit, keys, forms)
  const was = (keys: string[], forms: string[]) => put(old, keys, forms)
  if (gender === 'm' || gender === 'n') {
    // a masculine's plural and dual may go on -ov- (sinovi, gradovi), a soft stem's -ev- (možev, ključev)
    const P = gender === 'm' ? [S, `${S}${o}v`] : [S]
    const each = (end: string) => P.map(p => p + end)
    if (gender === 'm') {
      set(['nom.sg'], [lemma])
      set(['acc.sg'], [animate ? `${S}a` : lemma])
      set(['nom.du', 'acc.du'], each('a'))
      set(['gen.du', 'gen.pl'], [...each(`${o}v`), S])
      set(['nom.pl'], [...each('i'), `${S}je`])
      set(['acc.pl'], each('e'))
    } else {
      set(['nom.sg', 'acc.sg'], [lemma])
      set(['nom.du', 'acc.du'], [`${S}i`])
      set(['gen.du', 'gen.pl'], [S, ...fleeting(S)])
      set(['nom.pl', 'acc.pl'], [`${S}a`])
    }
    set(['gen.sg'], [`${S}a`])
    set(['dat.sg', 'loc.sg'], [`${S}u`])
    set(['ins.sg'], [`${S}${o}m`])
    set(['dat.du', 'ins.du'], each(`${o}ma`))
    set(['loc.du', 'loc.pl'], each('ih'))
    set(['dat.pl'], each(`${o}m`))
    set(['ins.pl'], each('i'))
    // old and dialect endings: brati, potoki; bratam, bratah, bratama; rogeh, rogmi; a soft stem's ključom, ključov
    was(['dat.sg', 'loc.sg'], [`${S}i`])
    was(['dat.pl'], each('am'))
    was(['loc.pl', 'loc.du'], [...each('ah'), ...each('eh')])
    was(['ins.pl'], each('mi'))
    was(['dat.du', 'ins.du'], each('ama'))
    if (soft) {
      was(['ins.sg'], [`${S}om`])
      was(['gen.pl', 'gen.du'], each('ov'))
      was(['dat.pl'], each('om'))
      was(['dat.du', 'ins.du'], each('oma'))
    }
    return { fit, old }
  }
  if (kind === 'a' || kind === 'ev') {
    if (kind === 'a') {
      set(['nom.sg'], [lemma])
      set(['acc.sg', 'ins.sg'], [`${S}o`])
    } else {
      // cerkev, cerkve, cerkvi, cerkev, cerkvi, s cerkvijo
      set(['nom.sg', 'acc.sg'], [lemma])
      set(['ins.sg'], [`${S}ijo`])
    }
    set(['gen.sg'], [`${S}e`])
    set(['dat.sg', 'loc.sg'], [`${S}i`])
    set(['nom.du', 'acc.du'], [`${S}i`])
    set(['gen.du', 'gen.pl'], [S, ...fleeting(S)])
    set(['dat.du', 'ins.du'], [`${S}ama`])
    set(['loc.du', 'loc.pl'], [`${S}ah`])
    set(['nom.pl', 'acc.pl'], [`${S}e`])
    set(['dat.pl'], [`${S}am`])
    set(['ins.pl'], [`${S}ami`])
    // the dual said as the plural (dve sestre), an old genitive plural (sestra, ovac)
    was(['nom.du', 'acc.du'], [`${S}e`])
    was(['gen.du', 'gen.pl'], [`${S}a`, `${S.slice(0, -1)}a${S.slice(-1)}`])
    return { fit, old }
  }
  // the i-declension: stvar, stvari; misel, misli; noč, noči (its accent types: stvarem, stvareh; pesmim, pesmih)
  set(['nom.sg', 'acc.sg'], [lemma])
  set(['gen.sg', 'dat.sg', 'loc.sg'], [`${S}i`])
  set(['ins.sg'], [`${S}jo`, `${S}ijo`])
  set(['nom.du', 'acc.du', 'gen.du'], [`${S}i`])
  set(['dat.du', 'ins.du'], [`${S}ema`, `${S}ima`, `${S}ma`])
  set(['loc.du', 'loc.pl'], [`${S}eh`, `${S}ih`])
  set(['nom.pl', 'acc.pl', 'gen.pl'], [`${S}i`])
  set(['dat.pl'], [`${S}em`, `${S}im`])
  set(['ins.pl'], [`${S}mi`, `${S}imi`])
  return { fit, old }
}

/**
 * [slots] (the dictionary's forms of a noun of [gender], nominative [lemma]) checked: of the stems its genitive
 * singular gives, the one that confirms the most slots; a slot is confirmed when one of its forms fits and every other
 * is a known old or dialect ending. A noun without a singular, a gender, or a stem that goes with its lemma confirms none.
 */
export function checkNoun(lemma: string, gender: string | null | undefined, slots: Map<string, string[]>, animate: boolean): NounCheck {
  const keys = [...slots.keys()]
  const none = (why: string): NounCheck => ({ stem: null, slots: new Map(), unconfirmed: keys, why })
  if (gender !== 'm' && gender !== 'f' && gender !== 'n') return none('no gender')
  const gen = slots.get('gen.sg') ?? []
  if (!slots.get('nom.sg')?.length || !gen.length) return none('no singular')
  let best: { stem: string; out: Map<string, CheckedSlot> } | null = null
  for (const g of gen) {
    const kinds: Kind[] = gender === 'f' ? (g.endsWith('e') ? ['a', 'ev'] : g.endsWith('i') ? ['i'] : []) : g.endsWith('a') ? [null] : []
    for (const kind of kinds) {
      const r = regularForms(lemma, gender, g.slice(0, -1), kind, animate)
      if (!r) continue
      const out = new Map<string, CheckedSlot>()
      for (const k of keys) {
        const forms = slots.get(k)!
        const fit = r.fit.get(k) ?? []
        const old = r.old.get(k) ?? []
        const standard = forms.filter(f => fit.includes(f))
        const variants = forms.filter(f => !fit.includes(f))
        if (standard.length && variants.every(f => old.includes(f))) out.set(k, { standard, variants })
      }
      if (!best || out.size > best.out.size) best = { stem: g.slice(0, -1), out }
    }
  }
  if (!best) return none(`no stem from the genitive (${gen.join('/')}) goes with ${lemma} as a ${gender === 'm' ? 'masculine' : gender === 'f' ? 'feminine' : 'neuter'} noun`)
  return { stem: best.stem, slots: best.out, unconfirmed: keys.filter(k => !best!.out.has(k)) }
}
