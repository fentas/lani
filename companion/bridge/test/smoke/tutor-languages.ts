// Smoke checks: the tutor for every learner (docs/plans/05-built/tutor-languages.md). Jan's guide reads as it always did. A
// learner of Italian, German or English reads what's correct in their language (companion/tutor/languages), a child's
// case when they are one, no paragraph of a feature only Slovene has, and every publishing tool takes their language's
// content. Their bridges take the culture smokes' ports, free by now (pairs.ts takes friuli's after them).
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { existsSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { CHILD_INSTRUCTIONS, initLearnerData } from '../../src/learners'
import { routineAsk } from '../../src/rhythm'
import { forVillage } from '../../src/tutor-languages'
import { check, client as janClient, culturesDir, guideOf, port as smokePort, tempDir } from './harness'
import { publishEach } from './publish-languages'

const repo = resolve(import.meta.dir, '../../../..')
const token = 'tutor-languages-smoke-token'
const auth = { authorization: `Bearer ${token}`, 'content-type': 'application/json' }
const parts = join(repo, 'companion/tutor/languages')

const LEARNERS = [
  { id: 'mia', name: 'Mia', culture: 'friuli', lang: 'it', base: 'de', child: true, title: 'Italian', port: Number(process.env.LANI_SMOKE_FRIULI_PORT ?? smokePort + 2) },
  { id: 'eva', name: 'Eva', culture: 'kaernten', lang: 'de', base: 'en', child: false, title: 'German', port: Number(process.env.LANI_SMOKE_KAERNTEN_PORT ?? smokePort + 5) },
  { id: 'ana', name: 'Ana', culture: 'lakeland', lang: 'en', base: 'sl', child: false, title: 'English', port: Number(process.env.LANI_SMOKE_LAKELAND_PORT ?? smokePort + 6) },
]

export default async function tutorLanguages() {
  // --- Jan -------------------------------------------------------------------------------------------------------------
  const jan = await guideOf(janClient)
  check("Jan's guide has no language part, its paragraphs as they were", !jan.includes('## Correct') && !jan.includes('## A child') && jan.includes('simple Slovene at Jan') && jan.includes('Slovene-speaking family') && jan.includes('Counting cards ("🔢 Štetje') && jan.includes('never with a bare singular "fant"'))

  check("the morning's routine greets in the village's language, Jan's as it was", forVillage(routineAsk.morning, 'it').includes('Greet in Italian.') && forVillage(routineAsk.morning, 'sl') === routineAsk.morning && routineAsk.morning.includes('Greet in Slovene.'))

  // --- the files ---------------------------------------------------------------------------------------------------------
  for (const l of ['sl', 'it', 'de', 'en']) {
    const name = { sl: 'Slovene', it: 'Italian', de: 'German', en: 'English' }[l]
    const t = readFileSync(join(parts, `${l}.md`), 'utf8')
    check(`companion/tutor/languages/${l}.md has its three sections`, t.includes(`## Correct ${name}`) && t.includes(`## A child learning ${name}`) && t.includes(`## Writing ${name}`))
  }
  const childApp = readFileSync(join(parts, 'child.md'), 'utf8').replace(/<!--[\s\S]*?-->\s*/g, '').replace('## The learner is a child\n\n', '').trim()
  const childNode = CHILD_INSTRUCTIONS.replace('Child: the learner is a child. ', '').replace(/ Tool approvals are not relayed[\s\S]*$/, '')
  check("the app's child rules are the bridge's, without the terminal", childApp === childNode, { childApp, childNode })

  // --- each learner --------------------------------------------------------------------------------------------------------
  for (const l of LEARNERS) {
    const dataDir = tempDir(`lani-smoke-tl-${l.id}-`)
    initLearnerData(repo, dataDir, { name: l.name, target: l.lang, base: l.base, child: l.child, culture: l.culture })
    const c = new Client({ name: `smoke-${l.id}`, version: '0' })
    c.fallbackNotificationHandler = async () => {}
    await c.connect(
      new StdioClientTransport({
        command: 'bun',
        args: [resolve(import.meta.dir, '../../src/index.ts')],
        env: {
          ...process.env, LANI_PROFILE: l.id, LANI_CHILD: l.child ? '1' : '', LANI_CULTURE: l.culture, LANI_DATA_DIR: dataDir, LANI_BRIDGE_PORT: String(l.port),
          LANI_BRIDGE_TOKEN: token, LANI_PACKS_DIR: join(repo, 'companion/packs'), LANI_CULTURES_DIR: culturesDir, LANI_RHYTHM: 'off',
          LANI_SCENES_DIR: join(repo, 'companion/scenes'), LANI_MODULES_DIR: join(repo, 'companion/modules'), LANI_SCENARIOS_DIR: join(repo, 'companion/scenarios'),
          LANI_VOICE_DESIGN: 'off', ELEVENLABS_API_KEY: '', LANI_GEPARD_URL: 'http://127.0.0.1:9', LANI_LEXICON_URL: '',
        } as Record<string, string>,
        stderr: 'inherit',
      }),
    )
    await Bun.sleep(300)
    try {
      const guide = await guideOf(c)
      check(`${l.title}: the guide says what's correct, in every language and in theirs`, guide.indexOf('## Correct language, in every language') > 0 && guide.indexOf(`## Correct ${l.title}`) > guide.indexOf('## Correct language, in every language') && guide.includes(`## Writing ${l.title}`))
      check(`${l.title}: … before the paragraphs, none of Slovene's rules`, guide.indexOf(`## Correct ${l.title}`) < guide.indexOf('Inbound:') && !guide.includes('## Correct Slovene') && !guide.includes('*grem spat*') && !guide.includes('written for a Slovene learner'))
      check(`${l.title}: … and says what to do when it can't check a dictionary`, guide.includes("**What you can't check, you don't know.**") && guide.includes('WebFetch reads ') && guide.includes('as far as this session\'s settings allow') && guide.includes(`companion/lexicon/${l.lang}.extra.json`) && existsSync(join(repo, `companion/lexicon/${l.lang}.extra.json`)))
      check(`${l.title}: a child's case only for a child`, guide.includes(`## A child learning ${l.title}`) === l.child && guide.includes('Child: the learner is a child') === l.child)
      check(`${l.title}: no paragraph of what only Slovene has, each said so`, !guide.includes('Štetje') && !guide.includes('Oblike · Forms') && !guide.includes('Skloni · Cases') && guide.includes("Counting cards are Slovene's") && guide.includes("Forms cards are Slovene's") && guide.includes("The case trainer and the case lens are Slovene's"))
      check(`${l.title}: its paragraphs say its language: a photo, a card, a scene, the family, the voices`, ['transcribe the Slovene text', 'with the Slovene in bold', 'a short dialog of Slovene choices', 'Slovene-speaking family', 'every Slovene word', 'In the Slovene village', 'bare singular "fant"', 'Moja vas'].every(t => !guide.includes(t)) && guide.includes(`transcribe the ${l.title} text`) && guide.includes(`${l.title}-speaking family`), ['transcribe the Slovene text', 'with the Slovene in bold', 'a short dialog of Slovene choices', 'Slovene-speaking family', 'every Slovene word', 'In the Slovene village', 'bare singular "fant"', 'Moja vas'].filter(t => guide.includes(t)))
      await publishEach(c, `http://127.0.0.1:${l.port}`, auth, l.title, l.lang, l.base)
    } finally {
      await c.close()
    }
  }
}
