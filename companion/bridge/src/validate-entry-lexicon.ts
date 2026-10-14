// validate-entry.ts with the checks that need the village's dictionary (the bundle's larger build, validators/build.ts):
// the words a tutor's dialog weaves in, each the tested element of a turn (dialog-words.ts checkWoven, with the
// dictionary's and the forms book's lemmas), as publish_scene checks them after validateScene (features/scenes.ts).
// The dictionary's files (companion/lexicon/<language>.json, .extra.json, .forms.json) are mounted under refs.lexicon.dir.
import { checkWoven, lemmaFinder, testedAt, type Card } from './dialog-words'
import { FormsBook } from './forms'
import { Lexicon } from './lexicon'
import { resolveScene, validateScene, type Dialog, type Scene, type SceneRefs } from './scenes'
import { kinds, type Refs } from './validate-entry'

/** The dictionary and forms book of a directory and language, made once (the bridge keeps one per village). */
const dictionaries = new Map<string, { dict: Lexicon; book: FormsBook }>()
function dictionary(dir: string, language: string) {
  const key = `${dir}|${language}`
  let d = dictionaries.get(key)
  if (!d) {
    // no tutor's glosses: a file that isn't there
    d = { dict: new Lexicon({ dir, language, tutorFile: `${dir}/.no-tutor-glosses.json` }), book: new FormsBook({ dir, language }) }
    dictionaries.set(key, d)
  }
  return d
}

/** features/scenes.ts's woven(): the errors of dialog [d]'s declared words in [scene], and what publishing says back. */
function woven(scene: Scene, d: Dialog, r: Refs, at = ''): { errors: string[]; said: string } {
  if (!d.words?.length) return { errors: [], said: '' }
  const { dict, book } = dictionary(r.lexicon.dir, r.lexicon.language ?? scene.language)
  const objects = resolveScene({ ...scene, source: 'tutor' }, r.packs ?? []).objects
  const res = checkWoven(d, { language: scene.language, objects }, d.words, { items: (r.cards ?? {}) as Record<string, Card>, lemmaOf: lemmaFinder(dict, book, r.lexicon.language ?? scene.language) })
  const where = at ? `${at}: ` : ''
  const tests = res.words.map(w => `"${w.declared}"${w.item_id && w.item_id !== w.declared ? ` (${w.item_id})` : ''}: ${w.tests.map(testedAt).join('; ')}`)
  return {
    errors: res.errors.map(e => `${where}${e}`),
    said: `\nWords woven${at ? ` in ${at}` : ''}: ${tests.join(' | ')}` + res.notes.map(n => `\nNote: ${where}${n}`).join(''),
  }
}

/** A scene as publish_scene checks it: validateScene, then the words its dialogs weave (refs.cards: the learner's cards). */
kinds.woven = (i, r) => {
  const v = validateScene(i, r.packs ? (r as SceneRefs) : undefined)
  if (!v.ok) return { verdict: { ok: false, errors: v.errors } }
  const checks = [
    ...v.scene.dialogs.map((d, n) => woven(v.scene, d, r, `dialogs[${n}] (${d.id})`)),
    ...v.scene.variants.map((d, n) => woven(v.scene, d, r, `variants[${n}] (${d.id})`)),
  ]
  const wrong = checks.flatMap(c => c.errors)
  const notes = checks.map(c => c.said).filter(Boolean)
  if (wrong.length) return { verdict: { ok: false, errors: wrong.map(e => `✖ ${e}`).join('\n') } }
  return { verdict: notes.length ? { ok: true, notes } : { ok: true }, value: v.scene }
}

export * from './validate-entry'
