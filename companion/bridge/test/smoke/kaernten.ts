// Smoke checks: a village in Carinthia (the kaernten culture pack, German). A bridge plays a profile whose culture is
// kaernten and whose target is German (from Slovene): it serves the kaernten cast and the German word packs
// (companion/packs/de and the pack's own: its festivals', its places', its stories'), builds the villager talks in German
// with Slovene translations, du and Sie and the pack's setting, records a pack's words as German with their Slovene
// meaning, finds a pack word by its plural without its article, tells the tutor about the village, and speaks with the
// pack's voice cast, in German (the phone's German TTS while the cast has no voices).
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { initLearnerData } from '../../src/learners'
import { validatePack } from '../../src/packs'
import { allLines } from '../../src/villagers'
import { bridgeEleven, bridgeWhisper, check, checkCultureVoices, culturesDir, guideOf, languageBook, port as smokePort, tempDir } from './harness'

const repo = resolve(import.meta.dir, '../../../..')
// not 8797 (the QA's dev town) nor a learner's port: next to the smoke's own, as the towns' is
const port = Number(process.env.LANI_SMOKE_KAERNTEN_PORT ?? smokePort + 5)
const base = `http://127.0.0.1:${port}`
const token = 'kaernten-smoke-token'
const auth = { authorization: `Bearer ${token}`, 'content-type': 'application/json' }

export default async function kaernten() {
  // --- a learner of German from Slovene, in the kaernten village -----------------------------------------
  const dataDir = tempDir('lani-smoke-kaernten-')
  initLearnerData(repo, dataDir, { name: 'Ana', target: 'de', base: 'sl', culture: 'kaernten' })
  const ttsBefore = bridgeEleven.st.tts.length
  const client = new Client({ name: 'smoke-kaernten', version: '0' })
  await client.connect(
    new StdioClientTransport({
      command: 'bun',
      args: [resolve(import.meta.dir, '../../src/index.ts')],
      env: {
        ...process.env, LANI_PROFILE: 'ana', LANI_CHILD: '', LANI_CULTURE: 'kaernten', LANI_DATA_DIR: dataDir, LANI_BRIDGE_PORT: String(port),
        LANI_BRIDGE_TOKEN: token, LANI_PACKS_DIR: join(repo, 'companion/packs'), LANI_CULTURES_DIR: culturesDir, LANI_RHYTHM: 'off',
        LANI_SCENES_DIR: join(repo, 'companion/scenes'), LANI_MODULES_DIR: join(repo, 'companion/modules'), LANI_SCENARIOS_DIR: join(repo, 'companion/scenarios'),
        LANI_VOICE_DESIGN: 'off', ELEVENLABS_API_KEY: 'test-key', ELEVENLABS_BASE_URL: bridgeEleven.url, LANI_GEPARD_URL: 'http://127.0.0.1:9',
        LANI_VOICE_QUEUE_DELAY_MS: '0', LANI_STT_URL: bridgeWhisper.url, LANI_LEXICON_URL: '',
      } as Record<string, string>,
      stderr: 'inherit',
    }),
  )
  await Bun.sleep(300)
  try {
    const culture = await (await fetch(`${base}/culture`, { headers: auth })).json()
    check('kaernten: GET /culture plays the kaernten pack, in German', culture.id === 'kaernten' && culture.language === 'de' && culture.status === 'complete' && !culture.wanted && culture.places.some((p: any) => p.name.de === 'Villach' && p.name.sl === 'Beljak'), culture)

    // --- the cast --------------------------------------------------------------------------------------
    const cast = await (await fetch(`${base}/villagers`, { headers: auth })).json()
    check('kaernten: GET /villagers serves the kaernten cast of 13, Oma Resi first', cast.length === 13 && cast[0].id === 'resi' && cast[0].name === 'Oma Resi' && cast.every((v: any) => v.source === 'curated'), cast.map((v: any) => v.id))
    check('kaernten: their lines are German with Slovene, English and Italian, their gift lines too, their roles too', cast.every((v: any) => allLines(v.lines).every((l: any) => l.de && l.sl && l.en && l.it) && v.role.de && v.role.sl && v.role.it), cast[0].lines.greet[0])
    check('kaernten: everyone thanks for a gift in German: 3 lines for a good they like, 2 for an ordinary one, 1 for a rare one', cast.every((v: any) => v.lines.gift?.liked.length === 3 && v.lines.gift.ordinary.length === 2 && v.lines.gift.rare.length === 1), cast.filter((v: any) => !v.lines.gift).map((v: any) => v.id))
    check('kaernten: the learner says Sie to four of them (Müller Franz, Schmied Toni, Wirtin Kathi, Lehrerin Eva), du to the rest', JSON.stringify(cast.filter((v: any) => v.register === 'vi').map((v: any) => v.id).sort()) === '["eva","franz","kathi","toni"]', cast.map((v: any) => [v.id, v.register]))
    const resi = cast[0]
    const talk = await (await fetch(`${base}/villagers/resi/scenario`, { headers: auth })).json()
    const hello = resi.lines.greet.find((l: any) => (l.level ?? 0) === 0)
    check(
      "kaernten: a talk opens with Resi's German greeting, translated into Slovene, in the Gailtal",
      talk.opener_sl === hello.de && talk.opener_en === hello.sl && talk.setting.startsWith("Mein Dorf, Ana's village in the lower Gailtal") && talk.role.includes('of Mein Dorf') && talk.role.includes('Everyone says du here'),
      talk,
    )
    check("kaernten: the hints are German, meant in Slovene", talk.vocabulary_hints[0].sl === "Wie geht's dir?" && talk.vocabulary_hints[0].en === 'Kako si?' && talk.vocabulary_hints.at(-1).sl === 'Servus!' && talk.goals[0].includes('(du)'), talk.vocabulary_hints)
    const eva = await (await fetch(`${base}/villagers/eva/scenario`, { headers: auth })).json()
    check('kaernten: a Sie villager: the polite goal and hints', eva.goals[0].includes('politely (Sie)') && eva.vocabulary_hints[0].sl === 'Wie geht es Ihnen?' && eva.vocabulary_hints[0].en === 'Kako ste?' && eva.vocabulary_hints.at(-1).sl === 'Auf Wiedersehen!' && eva.role.includes('Ana says Sie to them'), eva)

    // --- the word packs: German, the village's festivals', places' and stories', not Slovene or Italian ones ---
    const packs = await (await fetch(`${base}/packs`, { headers: auth })).json()
    const ids = packs.map((p: any) => p.id)
    check(
      "kaernten: GET /packs serves the German starter packs and the pack's own, not the Slovene or Italian ones",
      ['gruesse', 'familie', 'haus-und-kueche', 'essen', 'zahlen-und-uhrzeit', 'im-dorf', 'fest-weihnachten', 'fest-kirchtag', 'am-see', 'am-feuer', 'geschichten'].every(i => ids.includes(i)) &&
        !ids.includes('druzina') && !ids.includes('praznik-bozic') && !ids.includes('famiglia') && !ids.includes('festa-natale') && packs.every((p: any) => p.language === 'de'),
      ids,
    )
    const fest = packs.filter((p: any) => p.festival)
    check('kaernten: 14 festival packs, each named for a festival of the pack', fest.length === 14 && fest.every((p: any) => p.id.startsWith('fest-')), fest.map((p: any) => [p.id, p.festival]))
    const familie = await (await fetch(`${base}/packs/familie`, { headers: auth })).json()
    check('kaernten: a pack comes with its words in German, Slovene, English and Italian, its title per language', familie.language === 'de' && familie.title.de === 'Die Familie' && familie.words.every((w: any) => w.de && w.sl && w.en && w.it && w.example_de), familie.title)
    const w = familie.words[0]
    const learn = await fetch(`${base}/packs/familie/learn`, { method: 'POST', headers: auth, body: JSON.stringify({ results: [{ word_id: w.id, quality: 4 }], duration_minutes: 3 }) })
    const learned = await learn.json()
    const sr = JSON.parse(readFileSync(join(dataDir, 'spaced-repetition.json'), 'utf8'))
    const item = sr.items?.[`vocab_familie_${w.id}`]
    check('kaernten: a learned word is a German card with its Slovene meaning', learn.ok && learned.added?.length === 1 && item?.content === w.de && item?.answer === w.sl, { learned, item })

    // word lookup without a German dictionary: the pack words (meant in Slovene, their article left out) and the villagers
    const lookup = async (q: string) => (await (await fetch(`${base}/lookup?${q}`, { headers: auth })).json()) as any
    const haeuser = await lookup(`w=${encodeURIComponent('Häuser')}&line=${encodeURIComponent('Die Häuser haben Balkone.')}`)
    check('kaernten: a lookup finds a German pack word by its plural, without its article, meant in Slovene', haeuser.entries[0]?.lemma === 'das Haus' && haeuser.entries[0].gloss[0]?.startsWith('hiša') && haeuser.entries[0].gloss_lang === 'sl' && haeuser.entries[0].grammar === 'plural' && haeuser.entries[0].source === 'pack', haeuser)
    const resiName = await lookup('w=Resi')
    check('kaernten: a villager is found by name', resiName.entries[0]?.lemma === 'Oma Resi' && resiName.entries[0].source === 'village', resiName)

    // Jan's curated Slovene modules, role-plays and scenes aren't this village's, nor friuli's places
    const modules = await (await fetch(`${base}/modules`, { headers: auth })).json()
    const scenarios = await (await fetch(`${base}/scenarios`, { headers: auth })).json()
    const scenes = await (await fetch(`${base}/scenes`, { headers: auth })).json()
    check('kaernten: no Slovene modules, role-plays or scenes, no Italian places, for the German village', Array.isArray(modules) && !modules.some((m: any) => m.id === 'dvojina-osnove') && Array.isArray(scenarios) && !scenarios.some((s: any) => s.id === 'v-pekarni') && !scenes.some((s: any) => ['ob-ognju', 'na-gricih', 'il-mare', 'al-fuoco'].includes(s.id)), { modules: modules.length, scenarios: scenarios.length, scenes: scenes.map((s: any) => s.id) })
    // the pack's own place: the Faaker See under the Mittagskogel, in German with Slovene, English and Italian
    const see = scenes.find((s: any) => s.id === 'am-see')
    check("kaernten: the pack's own scene is served (Am See, from the horizon), in German", see?.culture === 'kaernten' && see.language === 'de' && see.art === 'alps' && see.title.de === 'Am See' && see.title.sl === 'Ob jezeru' && see.dialogs.every((d: any) => d.lines.every((l: any) => !l.who || (l.de && l.sl && l.en && l.it))), see && { title: see.title, culture: see.culture })
    const lake = see?.objects.find((o: any) => o.slot === 'lake')
    check('kaernten: its objects are German words with their Slovene, English and Italian', lake?.de === 'der See' && lake.sl === 'jezero' && lake.en === 'lake' && lake.it && lake.example_de && lake.example_sl && lake.pack === 'am-see', lake)
    // (the guest's dialog isn't served to the village's own app: companion/SCENES.md, "Dialogs for guests")
    const alm = see?.dialogs.find((d: any) => d.id === 'alm')
    check('kaernten: a wrong choice explains itself in Slovene too; the guest\'s dialog stays for guests', !!alm?.lines[1].choices.find((c: any) => !c.ok)?.why?.sl && !see.dialogs.some((d: any) => d.id === 'erstes-mal'), alm?.lines[1])
    // the campfire: Opa Sepp tells his stories there in the evening, in German
    const feuer = scenes.find((s: any) => s.id === 'am-feuer')
    check(
      "kaernten: Opa Sepp tells his stories by the fire (am-feuer comes with them, in German, none of Janez's or Bepi's)",
      feuer?.culture === 'kaernten' && feuer.art === 'campfire' && feuer.stories?.length === 10 && feuer.stories.every((s: any) => s.teller === 'sepp' && s.language === 'de' && s.culture === 'kaernten') &&
        feuer.stories[0].id === 'der-lindwurm' && feuer.stories[0].words.every((w: any) => w.de && w.sl && w.en) && scenes.filter((s: any) => s.id !== 'am-feuer').every((s: any) => !s.stories),
      feuer && { stories: feuer.stories?.map((s: any) => [s.id, s.teller]), words: feuer.stories?.[0]?.words },
    )

    // --- the tutor is told about the village ------------------------------------------------------------
    const instructions = await guideOf(client)
    const rules = client.getInstructions() ?? ''
    check("kaernten: the instructions Claude Code shows fit, without a child's rules", rules.length <= 2048 && !rules.includes('Child: the learner is a child') && rules.includes('channel_guide'), rules.length)
    check(
      "kaernten: the instructions name the village's cast and language, du and Sie, the German remember line, and the style",
      instructions.includes('Oma Resi') && instructions.includes('ti = du, vi = Sie') && instructions.includes('Ich denke noch oft an') && instructions.includes('Carinthian style') && instructions.includes('companion/cultures/kaernten') && !instructions.includes('Primorska style'),
      instructions.slice(0, 400),
    )
    check('kaernten: packs are written in German for this village', instructions.includes('"language": "de"'))
    await languageBook('kaernten', base, auth, 'de', 'German', 'Grammatik', 'sl', instructions)

    // --- the voices: the pack's cast speaks German (without voices yet, the phone does) ------------------------
    await checkCultureVoices({ culture: 'kaernten', base, headers: auth, line: 'Servus! Hast du Hunger?', word: 'Holz', role: /Oma Resi/, since: ttsBefore })

    // --- speech recognition: in the village's language when the app doesn't say --------------------------
    // (the worker has German through its general model, stt-local's LANI_STT_MODEL_GENERAL; the fake has it for now)
    const models = bridgeWhisper.st.languages
    bridgeWhisper.st.languages = [...models, 'de']
    const take = new Uint8Array([0, 0, 0, 0x18, ...new TextEncoder().encode('ftypM4A '), 0, 0, 0, 0, 7, 7, 7, 7])
    const heard = await fetch(`${base}/stt?prompt=Servus`, { method: 'POST', headers: { authorization: auth.authorization, 'content-type': 'audio/mp4' }, body: take })
    bridgeWhisper.st.languages = models
    check("kaernten: POST /stt without a language recognizes German, the village's", heard.ok && bridgeWhisper.st.calls.at(-1)?.query.language === 'de', bridgeWhisper.st.calls.at(-1)?.query)
  } finally {
    await client.close()
  }

  // --- the repository's German packs validate ----------------------------------------------------------
  for (const dir of [join(repo, 'companion/packs/de'), join(culturesDir, 'kaernten/packs')]) {
    for (const f of new Bun.Glob('*.json').scanSync(dir)) {
      const v = validatePack(JSON.parse(readFileSync(join(dir, f), 'utf8')))
      check(`kaernten: pack validates: ${f}`, v.ok && v.pack.language === 'de' && v.pack.words.every(x => !!x.de && !!x.sl && !!x.en && !!x.it), !v.ok && v.errors)
    }
  }
}
