// Smoke checks: the learner's words in the dialogs (companion/SCENES.md, "Your words in the dialogs"; companion/GAME.md):
// GET /state's features, POST /reviews with a dialog's words (a review only of a card due within its window and not
// reviewed today, a slip about the word lowered gently once a day, a form slip left alone), the words a tutor's variant
// weaves (publish_dialog_variant: each the tested element of a turn), and the words offered to weave (dialog-words.ts).
import { existsSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { addDays, checkWoven, countsInDialog, dialogWindow, fittingWords, testedWords, weavePlan, wordsToWeave } from '../../src/dialog-words'
import { auth, base, channelEvents, check, client, dataDir, scenesDir } from './harness'

/** A tool result's text. */
const text = (r: any) => (r.content ?? []).map((c: any) => c.text ?? '').join('\n')
const DBS = ['learner-profile', 'progress-db', 'mistakes-db', 'mastery-db', 'spaced-repetition', 'session-log'].map(f => `${f}.json`)

/** Babica Micka in the kitchen: a turn that tests žlica (another word in its place), one that tests a form, a line. */
function kuhinja() {
  const l = (sl: string, en: string) => ({ sl, en })
  return {
    schema: 'lani.scene/v0', id: 'smoke-kuhinja', title: 'V kuhinji · In the kitchen', emoji: '🍳', level: 'A1', art: 'kitchen', from: ['hut'],
    pack: 'smoke-kitchen',
    objects: [{ slot: 'spoon', word: 'zlica' }, { slot: 'plate', word: 'kroznik' }],
    people: [{ id: 'babica', name: 'Babica Micka', emoji: '👵', art: 'grandma', slot: 'stove', villager: 'micka' }],
    happenings: [{ id: 'juha', title: 'Babica kuha juho · Grandma is cooking soup', who: 'babica', when: ['evening'], chance: 1, dialog: 'juha', reward: { food: 15 }, marker: '🍲' }],
    dialogs: [{
      id: 'juha',
      lines: [
        { who: 'babica', ...l('Juha je na mizi.', 'The soup is on the table.') },
        { choices: [
          { ...l('Ja, prosim.', 'Yes, please.'), ok: true, reply: l('Izvoli.', 'Here you are.') },
          { ...l('Ne, hvala.', 'No, thanks.'), why: 'She offers soup: say yes.', reply: l('Nisi lačen?', "Aren't you hungry?") },
        ] },
        { who: 'babica', ...l('Dober tek!', 'Enjoy your meal!') },
      ],
    }],
  }
}

/** A fresh variant weaving [words]: žlica tested by a distractor (vilice), krožnik only said, miza in no choice. */
function woven(id: string, words: string[]) {
  const l = (sl: string, en: string) => ({ sl, en })
  return {
    id, words,
    lines: [
      { who: 'babica', ...l('Tu je krožnik. Kaj še rabiš?', "Here's a plate. What else do you need?") },
      { choices: [
        { ...l('Daj mi žlico, prosim.', 'A spoon, please.'), ok: true, reply: l('Izvoli žlico.', "Here's a spoon.") },
        { ...l('Daj mi vilice, prosim.', 'A fork, please.'), why: 'Soup is eaten with a spoon: žlica.', reply: l('Vilice? Za juho?', 'A fork? For soup?') },
      ] },
      { who: 'babica', ...l('Dober tek!', 'Enjoy your meal!') },
    ],
  }
}

export default async function dialogWords() {
  // the learner databases as they are: put back at the end, so the later sections read them as before
  const kept = Object.fromEntries(DBS.map(f => [f, readFileSync(join(dataDir, f), 'utf8')]))
  try {
    await words(kept)
  } finally {
    for (const [f, raw] of Object.entries(kept)) writeFileSync(join(dataDir, f), raw)
    if (existsSync(join(scenesDir, 'smoke-kuhinja.json'))) unlinkSync(join(scenesDir, 'smoke-kuhinja.json'))
  }
}

async function words(kept: Record<string, string>) {
  // --- GET /state says what this bridge takes ------------------------------------------------------------------------
  const st = await (await fetch(`${base}/state`, { headers: auth })).json()
  check('GET /state lists "dialog-words" in its features', Array.isArray(st.features) && st.features.includes('dialog-words'), st.features)
  const today: string = st.computed.today
  const ago = (n: number) => addDays(today, -n)

  // the learner's cards: žlica due today, vilice due in ten days (interval 30), miza and jajce to get wrong
  const sr = JSON.parse(kept['spaced-repetition.json'])
  const card = (content: string, answer: string, due: string, interval: number, last: string) =>
    ({ id: '', type: 'vocabulary', content, answer, category: 'smoke-kitchen', due_date: due, interval_days: interval, repetitions: 2, easiness_factor: 2.5, last_reviewed: last, last_quality: 4, total_reviews: 2, review_history: [{ date: last, quality: 4 }] })
  sr.items = {
    ...sr.items,
    'vocab_smoke-kitchen_zlica': { ...card('žlica', 'spoon', today, 6, ago(6)), id: 'vocab_smoke-kitchen_zlica' },
    'vocab_smoke-kitchen_vilice': { ...card('vilice', 'fork', addDays(today, 10), 30, ago(20)), id: 'vocab_smoke-kitchen_vilice' },
    'vocab_smoke-kitchen_miza': { ...card('miza', 'table', addDays(today, 8), 12, ago(4)), id: 'vocab_smoke-kitchen_miza' },
    'vocab_smoke-kitchen_jajce': { ...card('jajce', 'egg', today, 6, ago(6)), id: 'vocab_smoke-kitchen_jajce' },
  }
  writeFileSync(join(dataDir, 'spaced-repetition.json'), JSON.stringify(sr, null, 2))
  const items = () => JSON.parse(readFileSync(join(dataDir, 'spaced-repetition.json'), 'utf8')).items

  // --- the window and once a day (the app's DialogReviews, the bridge's countsInDialog) ---------------------------
  check('the window is a tenth of the interval, 1 to 3 days', [1, 6, 14, 15, 24, 25, 90].map(dialogWindow).join() === '1,1,1,2,2,3,3')
  check('a card counts when due within its window and not reviewed today',
    countsInDialog({ due_date: today, interval_days: 6, last_reviewed: ago(6) }, today) && countsInDialog({ due_date: addDays(today, 3), interval_days: 30, last_reviewed: ago(27) }, today) &&
      !countsInDialog({ due_date: addDays(today, 2), interval_days: 6, last_reviewed: ago(4) }, today) && !countsInDialog({ due_date: today, interval_days: 6, last_reviewed: today }, today) &&
      !countsInDialog(undefined, today))

  // --- POST /reviews with a dialog ---------------------------------------------------------------------------------
  const post = (body: object) => fetch(`${base}/reviews`, { method: 'POST', headers: auth, body: JSON.stringify(body) })
  const where = { scene: 'smoke-kuhinja', happening: 'juha', dialog: 'juha-zlica', person: 'Babica Micka', title: 'Babica kuha juho' }
  const w = (item_id: string, word: string, right: boolean, how: 'meaning' | 'form', extra: object = {}) => ({ item_id, word, lemma: word, right, how, ...extra })
  const before = items()
  const r1 = await post({
    results: [{ item_id: 'vocab_smoke-kitchen_zlica', quality: 4 }, { item_id: 'vocab_smoke-kitchen_vilice', quality: 4 }],
    duration_minutes: 2,
    dialog: { ...where, words: [
      w('vocab_smoke-kitchen_zlica', 'žlico', true, 'meaning', { review: true, expected: 'Daj mi žlico, prosim.' }),
      w('vocab_smoke-kitchen_vilice', 'vilice', true, 'meaning', { review: true }),
      w('vocab_smoke-kitchen_miza', 'mizo', false, 'meaning', { said: 'Daj mi stol.', expected: 'Daj mi mizo.' }),
      w('vocab_smoke-kitchen_jajce', 'jajc', false, 'form', { said: 'Deset jajce.', expected: 'Deset jajc.' }),
    ] },
  })
  const b1 = await r1.json()
  const after = items()
  check('a dialog\'s words: 200, a review of the card due, the one not due skipped, the slip lowered',
    r1.ok && b1.skipped?.includes('vocab_smoke-kitchen_vilice') && b1.lowered?.includes('vocab_smoke-kitchen_miza') && !b1.lowered.includes('vocab_smoke-kitchen_jajce') && !!b1.session_id, b1)
  const z = after['vocab_smoke-kitchen_zlica']
  check('… the card due is reviewed: its due date moves on, reviewed today, quality 4, no easiness lost',
    z.last_reviewed === today && z.due_date > today && z.repetitions === 3 && z.last_quality === 4 && z.easiness_factor === 2.5 && z.review_history.length === 2, z)
  check('… the one not due is as it was', JSON.stringify(after['vocab_smoke-kitchen_vilice']) === JSON.stringify(before['vocab_smoke-kitchen_vilice']), after['vocab_smoke-kitchen_vilice'])
  const m = after['vocab_smoke-kitchen_miza']
  check('… the slip about the word lowers its card gently: due tomorrow, half its interval, a little easiness, no review',
    m.due_date === addDays(today, 1) && m.interval_days === 6 && m.easiness_factor === 2.36 && m.repetitions === 2 && m.last_quality === 4 && m.last_reviewed === ago(4) &&
      m.review_history.length === 1 && m.lapses === 1 && m.last_lapse === today, m)
  check('… a form slip leaves its card alone (the rule counts it)', JSON.stringify(after['vocab_smoke-kitchen_jajce']) === JSON.stringify(before['vocab_smoke-kitchen_jajce']), after['vocab_smoke-kitchen_jajce'])
  await Bun.sleep(100)
  const note = channelEvents.filter(n => n.params?.meta?.kind === 'dialog_words').at(-1)?.params?.content ?? ''
  check('the tutor hears it in one line: each word, what came of it', note.includes('Babica Micka') && note.includes('counted as a review') && note.includes('lowered gently') && note.includes('form slip') && note.includes('not due'), note)

  // the same again the same day: nothing more
  const r2 = await (await post({ results: [{ item_id: 'vocab_smoke-kitchen_zlica', quality: 5 }], duration_minutes: 1, dialog: { ...where, words: [w('vocab_smoke-kitchen_zlica', 'žlico', true, 'meaning', { review: true }), w('vocab_smoke-kitchen_miza', 'mizo', false, 'meaning')] } })).json()
  const again = items()
  check('once a day: a card reviewed today and one lowered today change nothing more',
    r2.skipped?.includes('vocab_smoke-kitchen_zlica') && r2.skipped?.includes('vocab_smoke-kitchen_miza') && !r2.lowered?.length &&
      JSON.stringify(again['vocab_smoke-kitchen_zlica']) === JSON.stringify(after['vocab_smoke-kitchen_zlica']) && JSON.stringify(again['vocab_smoke-kitchen_miza']) === JSON.stringify(m), r2)
  // only a slip, no review: taken
  const r3 = await post({ results: [], duration_minutes: 1, dialog: { ...where, words: [w('vocab_smoke-kitchen_jajce', 'jajc', false, 'meaning')] } })
  const b3 = await r3.json()
  check('a dialog with only a slip (results empty) is taken, and lowers its card', r3.ok && b3.lowered?.includes('vocab_smoke-kitchen_jajce') && items()['vocab_smoke-kitchen_jajce'].last_lapse === today, b3)
  check('a review without a dialog still needs a result', (await post({ results: [], duration_minutes: 1 })).status === 400)
  // an older app's review is as it was
  const plain = await post({ results: [{ item_id: 'vocab_smoke-kitchen_vilice', quality: 4 }], duration_minutes: 1 })
  check('a plain review (no dialog) reviews as before, due or not', plain.ok && items()['vocab_smoke-kitchen_vilice'].last_reviewed === today)

  // --- the words a tutor's variant weaves --------------------------------------------------------------------------
  writeFileSync(join(scenesDir, 'smoke-kuhinja.json'), JSON.stringify(kuhinja()))
  const pub = (dialog: object) => client.callTool({ name: 'publish_dialog_variant', arguments: { scene: 'smoke-kuhinja', happening: 'juha', dialog } })
  const ok1 = await pub(woven('juha-zlica', ['žlica']))
  check('publish_dialog_variant takes a declared word a turn tests, and says which turn', !ok1.isError && text(ok1).includes('Words woven') && text(ok1).includes('"žlica"') && text(ok1).includes('lines[1]') && text(ok1).includes('žlico'), text(ok1))
  const byId = await pub(woven('juha-zlica-2', ['vocab_smoke-kitchen_zlica']))
  check('… a card id too', !byId.isError && text(byId).includes('vocab_smoke-kitchen_zlica'), text(byId))
  const said = await pub(woven('juha-x', ['žlica', 'krožnik']))
  check('… refuses a word only said (krožnik in Micka\'s line), with how to fix it, and publishes nothing',
    said.isError && text(said).includes('"krožnik" is in no turn\'s test') && text(said).includes('only said') && text(said).includes('wrong choice differs') && text(said).includes('nothing published'), text(said))
  const present = await pub({ ...woven('juha-y', ['prosim']) })
  check('… refuses a word present in the choices where they don\'t differ in it', present.isError && text(present).includes('the choices don\'t differ in it'), text(present))
  const noCard = await pub(woven('juha-z', ['vocab_smoke-kitchen_nic']))
  check('… refuses a card id the learner hasn\'t', noCard.isError && text(noCard).includes('no card "vocab_smoke-kitchen_nic"'), text(noCard))
  const notMine = await pub({ ...woven('juha-vilice', ['vilice']), lines: woven('juha-vilice', []).lines.map((l: any, i: number) => i !== 1 ? l : { choices: [{ ...l.choices[1], ok: true, why: undefined, reply: { sl: 'Izvoli.', en: 'Here you are.' } }, { ...l.choices[0], ok: false, why: 'Babica wants the fork.', reply: { sl: 'Žlico? Za kruh?', en: 'A spoon? For bread?' } }] }) })
  check('… publishes a lemma that is a card of the learner\'s', !notMine.isError, text(notMine))
  const caj = await pub({
    id: 'juha-caj', words: ['čaj'],
    lines: [
      { who: 'babica', sl: 'Kaj boš pil?', en: 'What will you drink?' },
      { choices: [{ sl: 'Čaj, prosim.', en: 'Tea, please.', ok: true }, { sl: 'Vino, prosim.', en: 'Wine, please.', why: 'There is only tea.' }] },
      { who: 'babica', sl: 'Izvoli.', en: 'Here you are.' },
    ],
  })
  check('… and a lemma that is none of the learner\'s cards, said so, and the day\'s limit of woven variants',
    !caj.isError && text(caj).includes('not one of the learner\'s cards') && text(caj).includes('limit of 2'), text(caj))
  const served = await (await fetch(`${base}/scenes/smoke-kuhinja`, { headers: auth })).json()
  check('GET /scenes serves a woven variant with its words', JSON.stringify(served.dialogs?.find((d: any) => d.id === 'juha-zlica')?.words) === '["žlica"]', served.dialogs?.map((d: any) => d.id))

  // the pure check: tested words of a turn, a tap turn's thing
  const scene = { language: 'sl', objects: [{ slot: 'spoon', word: 'zlica', pack: 'smoke-kitchen', sl: 'žlica' }] }
  const lemmaOf = (x: string) => [x.toLowerCase(), ...(x.toLowerCase() === 'žlico' ? ['žlica'] : [])]
  const t = testedWords(woven('a', []), scene, lemmaOf)
  check('testedWords: the word the choices differ in, its meaning', t.length === 1 && t[0].word === 'žlico' && t[0].line === 1 && t[0].how.join() === 'meaning', t)
  const tap = testedWords({ lines: [{ sl: 'Kje je žlica?' }, { choices: [{ sl: 'Tu!', ok: true, tap: 'spoon' }, { sl: 'Tam!', tap: 'stove' }] }] } as any, scene, lemmaOf)
  check('testedWords: a tap turn tests the thing its right place is', tap.length === 1 && tap[0].tap === 'spoon' && tap[0].item_id === 'vocab_smoke-kitchen_zlica', tap)
  const r = checkWoven(woven('a', []), scene, ['žlica', 'krožnik'], { items: { 'vocab_smoke-kitchen_zlica': { type: 'vocabulary', content: 'žlica' } }, lemmaOf })
  check('checkWoven: a word tested passes, one only said is an error', r.words.length === 1 && r.errors.length === 1 && r.errors[0].includes('krožnik'), r)

  // --- the words offered to weave ------------------------------------------------------------------------------------
  const offer = wordsToWeave({
    items: {
      'vocab_smoke-kitchen_zlica': { type: 'vocabulary', content: 'žlica', answer: 'spoon', category: 'smoke-kitchen', due_date: today, repetitions: 2, total_reviews: 2, last_quality: 4 },
      'vocab_smoke-kitchen_miza': { type: 'vocabulary', content: 'miza', answer: 'table', category: 'smoke-kitchen', due_date: addDays(today, 9), repetitions: 2, total_reviews: 2, last_quality: 2 },
      'vocab_smoke-kitchen_noz': { type: 'vocabulary', content: 'nož', answer: 'knife', category: 'smoke-kitchen', due_date: addDays(today, 30), repetitions: 0, total_reviews: 0, last_quality: 3 },
      'vocab_smoke-kitchen_sir': { type: 'vocabulary', content: 'sir', answer: 'cheese', category: 'smoke-kitchen', due_date: addDays(today, 30), repetitions: 4, total_reviews: 4, last_quality: 5 },
      'vocab_word_hvala': { type: 'vocabulary', content: 'hvala lepa', answer: 'thanks' },
    },
    today,
    scenes: [{ ...kuhinja(), people: kuhinja().people, happenings: kuhinja().happenings, dialogs: kuhinja().dialogs, objects: kuhinja().objects }] as any,
    packs: [{ id: 'smoke-kitchen', giver: { name: 'Babica Micka' } }],
    villagers: [{ id: 'micka', name: 'Babica Micka' }],
  })
  check('wordsToWeave: the weak first, then the due, then the new; a known word not due and a phrase left out',
    offer.map(o => `${o.lemma}:${o.why}`).join() === 'miza:weak,žlica:due,nož:new' && offer[0].fits.some(f => f.scene === 'smoke-kuhinja' && f.happening === 'juha' && f.person === 'Babica Micka'), offer)
  check('fittingWords: the words of a happening, at most four', fittingWords(offer, 'smoke-kuhinja', 'juha').length === 3 && fittingWords(offer, 'smoke-kuhinja', 'nope').length === 0)
  check('weavePlan: a happening with two words or more', weavePlan(offer).length === 1 && weavePlan(offer)[0].words.length === 3 && weavePlan(offer.slice(0, 1)).length === 0)

  // dialog_variants_heard names the words that fit the happening
  const put = async (dialogsHeard: object) => {
    const cur = await (await fetch(`${base}/game`, { headers: auth })).json()
    return fetch(`${base}/game`, { method: 'PUT', headers: auth, body: JSON.stringify({ rev: cur.rev ?? 0, state: { ...(cur.state ?? {}), dialogsHeard } }) })
  }
  const was = (await (await fetch(`${base}/game`, { headers: auth })).json()).state?.dialogsHeard ?? {}
  const heard = await (await fetch(`${base}/scenes/smoke-kuhinja`, { headers: auth })).json()
  const ids: string[] = heard.happenings[0].dialogs ?? [heard.happenings[0].dialog]
  // the cards again, žlica due once more (the review above moved it on)
  const sr2 = JSON.parse(readFileSync(join(dataDir, 'spaced-repetition.json'), 'utf8'))
  sr2.items['vocab_smoke-kitchen_zlica'] = { ...sr2.items['vocab_smoke-kitchen_zlica'], due_date: today, last_quality: 2 }
  writeFileSync(join(dataDir, 'spaced-repetition.json'), JSON.stringify(sr2, null, 2))
  await put({ 'smoke-kuhinja/juha': { heard: Object.fromEntries(ids.map(id => [id, '2026-09-20'])), last: ids.at(-1) } })
  const asked = () => channelEvents.filter(n => n.params?.meta?.kind === 'dialog_variants_heard' && String(n.params?.content).includes('smoke-kuhinja'))
  for (let i = 0; i < 40 && !asked().length; i++) await Bun.sleep(100)
  const ask = String(asked()[0]?.params?.content ?? '')
  check('dialog_variants_heard offers the learner\'s words that fit the happening, to weave as a turn\'s test',
    ask.includes('"words_to_weave"') && ask.includes('"lemma": "žlica"') && ask.includes('tested element of a turn') && ask.includes('"words"'), ask.slice(0, 400))
  check('… and asks for the learner\'s forms as placeholders, not in the masculine', ask.includes('{learner}') && ask.includes('{m:') && !ask.includes('in the masculine'), ask.slice(0, 1200))
  for (const id of ['juha-zlica', 'juha-zlica-2', 'juha-vilice', 'juha-caj']) await client.callTool({ name: 'remove_dialog_variant', arguments: { scene: 'smoke-kuhinja', happening: 'juha', id } })
  await put(was)
}
