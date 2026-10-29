// Smoke checks: the learner's own voice, "🗣️ Moj glas · My voice" (src/my-voice.ts, src/cast.ts `players`; companion/
// SCENES.md "The learner's voice"; docs/plans/05-built/my-voice.md): the voices each culture's cast offers by gender and
// age, never a voice someone in the cast speaks with (the crossCheck), the resolution by gender, the child flag, the choice
// and off, a visit's place on the town's list, voice-build's corpus, and the routes on the smoke's bridge (Jan, a man).
import { readFileSync, utimesSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { castClash, CAST_FILE, cast, checkPlayers, isLearnerVoice, optionsOf, playerOption, playerVoice, speakerFor, useCastFile, type Cast } from '../../src/cast'
import { myVoice, sampleLine } from '../../src/my-voice'
import { corpus, ElevenLabs, Gepard, PRIORITY_LABELS, sloveneNarrators, VoiceStore } from '../../src/voice'
import { visitPlayer, visitVoices } from '../../src/visit-voices'
import { addressee } from '../../src/addressee'
import { auth, base, bridgeEleven, check, client, culturesDir, dataDir, fakeEleven, fakeGepard, tempDir } from './harness'

/** Each culture's cast file: Primorska's is the default cast (companion/voice-cast.json). */
const CASTS: Record<string, string> = {
  primorska: CAST_FILE,
  friuli: join(culturesDir, 'friuli/voice-cast.json'),
  kaernten: join(culturesDir, 'kaernten/voice-cast.json'),
  lakeland: join(culturesDir, 'lakeland/voice-cast.json'),
}

/** Every ElevenLabs voice the cast's people speak with: everyone but the narrators and the learner's voices. */
function castIds(speakers: Cast, options: Set<string>): Map<string, string> {
  const out = new Map<string, string>()
  for (const [id, s] of Object.entries(speakers)) {
    if (id === 'female' || id === 'male' || options.has(id)) continue
    if (s.elevenlabs) out.set(s.elevenlabs, id)
    for (const [l, v] of Object.entries(s.languages ?? {})) out.set(v.elevenlabs, `${id}.${l}`)
  }
  return out
}

const idOf = (v: string, speakers: Cast) => {
  const m = /^([a-z][a-z0-9-]*)\.([a-z]{2})$/.exec(v)
  return m ? speakers[m[1]]?.languages?.[m[2]]?.elevenlabs : speakers[v]?.elevenlabs
}

export default async function myVoiceSection() {
  // --- the casts shipped: 2–3 voices a gender and age, none a voice the cast speaks with ---------------------------------
  for (const [culture, file] of Object.entries(CASTS)) {
    const raw = JSON.parse(readFileSync(file, 'utf8'))
    const speakers = cast(file)
    const { players, problems } = checkPlayers(raw.players, speakers)
    const named = new Set(Object.values(players).flatMap(a => Object.values(a).flat()).map(v => v.replace(/\.[a-z]{2}$/, '')))
    const ids = castIds(speakers, named)
    const all = Object.entries(players).flatMap(([g, ages]) => Object.entries(ages).flatMap(([a, list]) => list.map(v => ({ g, a, v, id: idOf(v, speakers) }))))
    const counts = Object.fromEntries(Object.entries(players).flatMap(([g, ages]) => Object.entries(ages).map(([a, l]) => [`${g} ${a}`, l.length])))
    check(`my voice, ${culture}: its cast offers 2 or 3 voices for each gender and age (adult, child), every entry valid`, problems.length === 0 && Object.values(counts).length === 4 && Object.values(counts).every(n => n >= 2 && n <= 3), { problems, counts })
    // the crossCheck: never a voice anyone the learner talks to speaks with (the bug: Teo's line and Pastore Davide's reply)
    const clashes = all.filter(o => !o.id || ids.has(o.id)).map(o => ({ ...o, with: o.id && ids.get(o.id) }))
    check(`my voice, ${culture}: no voice offered is one someone in the cast speaks with, nor the same as another offered (the crossCheck)`, clashes.length === 0 && new Set(all.map(o => o.id)).size === all.length, { clashes, ids: all.map(o => o.id) })
    // each offered speaker: its gender and age as its place, a name, the local voice for a Slovene village
    const wrong = all.filter(o => {
      const s = speakers[o.v.replace(/\.[a-z]{2}$/, '')]
      return !s || s.gender !== o.g || (!o.v.includes('.') && (s.age !== o.a || !s.name)) || (culture === 'primorska' && !s.gepard)
    })
    check(`my voice, ${culture}: each voice offered has its gender, its age and a name (a Slovene one its local Gepard voice too)`, wrong.length === 0, wrong)
    // the cast's `player` stays the grown-ups' default per gender
    check(`my voice, ${culture}: the cast's player is the grown-ups' default (the first offered) for each gender`, ['female', 'male'].every(g => !raw.player?.[g] || raw.player[g] === players[g as 'female' | 'male'].adult[0]), { player: raw.player, players })
  }

  // the bug of October 2026 caught: Friuli's male narrator (Chris Basetta) is Pastore Davide's voice (young-man)
  {
    const friuli = cast(CASTS.friuli)
    const bad = checkPlayers({ male: { adult: ['male'] } }, friuli)
    check("my voice: the crossCheck refuses the friuli male narrator for a learner (Chris Basetta, the young man Pastore Davide's voice too)", bad.problems.length === 1 && bad.problems[0].includes('young-man') && !bad.players.male.adult.length && castClash('male', friuli, new Set()) === 'young-man', bad)
    const odd = checkPlayers({ male: { adult: ['grandma', 'nobody', 'player-boy-a'], elder: ['male'] }, other: {} }, friuli)
    check('my voice: … and a voice of the other gender, one not in the cast, a child on the grown-ups\' list, a place that is no age, no gender: each logged and left out', odd.problems.length === 5 && !odd.players.male.adult.length, odd.problems)
  }

  // --- the resolution: the profile's gender, the child flag, the choice, off --------------------------------------------
  try {
    useCastFile(CASTS.friuli)
    const boys = optionsOf('male', 'child')
    const teo = myVoice({ gender: 'male', child: true })
    const friuli = cast()
    check("my voice: Teo (friuli, a boy, a child) hears a boy's voice by default: the first of the friuli boys', not Chris Basetta (the narrator and Pastore Davide), not Kavya (the village's boys)",
      boys.length >= 2 && teo.option?.id === boys[0].id && teo.option.age === 'child' && teo.option.gender === 'male' && teo.options.every(o => o.age === 'child' && o.gender === 'male') && !teo.off && !teo.chosen &&
        idOf(teo.option.id, friuli) !== friuli.male.elevenlabs && idOf(teo.option.id, friuli) !== friuli['young-man'].elevenlabs && idOf(teo.option.id, friuli) !== friuli.boy.elevenlabs,
      teo)
    const second = myVoice({ gender: 'male', child: true, choice: boys[1].id })
    const off = myVoice({ gender: 'male', child: true, choice: 'off' })
    const notHis = myVoice({ gender: 'male', child: true, choice: optionsOf('female', 'child')[0].id })
    check("my voice: … his choice of the second boy's voice is his; off: no lines (the default for an app that asks anyway); a voice not offered him (a girl's): the default",
      second.option?.id === boys[1].id && second.chosen && off.off && off.option?.id === boys[0].id && off.chosen && notHis.option?.id === boys[0].id, { second: second.option, off, notHis: notHis.option })
    const unsaid = myVoice({ child: true })
    check("my voice: a child whose profile says no gender is offered the girls' and the boys' voices, a girl's by default (the female one's, as before)", unsaid.options.some(o => o.gender === 'male') && unsaid.options.some(o => o.gender === 'female') && unsaid.option?.gender === 'female' && unsaid.option.age === 'child', unsaid.options)
    check("my voice: the friuli young man's voice and the narrator stay theirs (the visit's view: the town's voices for a learner never theirs)", !isLearnerVoice('young-man') && !isLearnerVoice('male') && isLearnerVoice(boys[0].voice) && speakerFor({ voice: 'male', speaker: boys[0].voice }) === 'male', boys[0])
  } finally {
    useCastFile(undefined)
  }
  {
    const jan = myVoice({ gender: 'male', child: false })
    const nina = myVoice({ gender: 'female', child: false })
    check('my voice: Jan (Primorska, a man) keeps Luka by default (the male narrator\'s Slovene voice: as the store speaks it, male); a woman Nina', jan.option?.id === 'male.sl' && jan.option.voice === 'male' && jan.option.name === 'Luka' && playerVoice('male') === 'male' && nina.option?.id === 'female.sl' && nina.option.voice === 'female' && playerOption('female')?.name === 'Nina', [jan.option, nina.option])
  }
  // a cast without voices to choose: as before, the cast's player; a narrator who is a villager's voice too: none
  {
    const dir = tempDir('lani-smoke-my-voice-')
    const friuli = JSON.parse(readFileSync(CASTS.friuli, 'utf8'))
    const old = join(dir, 'old-friuli.json')
    writeFileSync(old, JSON.stringify({ ...friuli, players: undefined, player: undefined }))
    const noChild = join(dir, 'no-child.json')
    writeFileSync(noChild, JSON.stringify({ ...friuli, players: { ...friuli.players, male: { adult: friuli.players.male.adult } } }))
    try {
      useCastFile(old)
      const man = playerOption('male')
      const woman = playerOption('female')
      useCastFile(noChild)
      const boy = myVoice({ gender: 'male', child: true })
      check("my voice: a cast without voices to choose falls back to its player (the narrator), never to a voice the cast speaks with: friuli's male narrator is the young man's too, so none (the phone speaks); a child without children's voices gets a grown-up's",
        man === undefined && woman?.voice === 'female' && boy.option?.age === 'adult' && boy.option.id === friuli.players.male.adult[0], { man, woman, boy: boy.option })
    } finally {
      useCastFile(undefined)
    }
  }

  // --- a visit: the town's voice of the visitor's kind, at their place on its list ---------------------------------------
  {
    const town = {
      speakers: { female: { gender: 'female' as const, elevenlabs: 'aaaaaaaaaaaaaaaaaaaa' }, male: { gender: 'male' as const, elevenlabs: 'bbbbbbbbbbbbbbbbbbbb' }, 'young-man': { gender: 'male' as const, elevenlabs: 'bbbbbbbbbbbbbbbbbbbb' }, 'player-boy-a': { gender: 'male' as const, elevenlabs: 'cccccccccccccccccccc' }, 'player-boy-b': { gender: 'male' as const, elevenlabs: 'dddddddddddddddddddd' } },
      player: { female: 'female', male: 'male' },
      players: { female: { adult: ['female'], child: [] }, male: { adult: [], child: ['player-boy-a', 'player-boy-b'] } },
      people: {},
    }
    const second = visitVoices('player', town, { gender: 'male', age: 'child', pick: 1 })
    const third = visitPlayer(town, { gender: 'male', age: 'child', pick: 2 })
    const older = visitPlayer({ ...town, players: undefined }, { gender: 'male' })
    check("my voice on a visit: the town's voice of the visitor's kind at their place on its list (a boy's second: the town's second boy's); a place it hasn't, its first; a town from before the voices, its player unless a villager speaks with it too (none: the phone speaks)",
      second.wanted.join() === 'player-boy-b' && !second.fallback.length && third === 'player-boy-a' && older === undefined && visitPlayer({ ...town, players: undefined }, { gender: 'female' }) === 'female', { second, third, older })
  }

  // --- voice-build's corpus: the learner's lines in their voice, after the scenes ----------------------------------------
  {
    const scene = { id: 'fire', language: 'sl', objects: [], people: [{ id: 'micka', villager: 'micka', art: 'grandma' }], dialogs: [{ lines: [{ who: 'micka', sl: 'Si lačen?', choices: [{ sl: 'Ja, zelo.', reply: { sl: 'Na, juha.' } }] }] }] }
    const items = corpus({ packs: [], modules: [], scenarios: [], scenes: [scene as any], villagers: [{ id: 'micka', voice: 'female', speaker: 'grandma', lines: {} } as any], mine: 'male' })
    const mine = items.filter(i => i.text === 'Ja, zelo.')
    const none = corpus({ packs: [], modules: [], scenarios: [], scenes: [scene as any] }).filter(i => i.text === 'Ja, zelo.')
    check("my voice: voice-build voices the learner's choices in their voice too ('my lines', after the scenes and I spy), the default voice's clip kept for the choice's 🔊; without a voice (off), only that",
      mine.length === 2 && mine.some(i => i.voice === 'female' && PRIORITY_LABELS[i.priority] === 'scenes') && mine.some(i => i.voice === 'male' && PRIORITY_LABELS[i.priority] === 'my lines') && PRIORITY_LABELS.indexOf('my lines') > PRIORITY_LABELS.indexOf('ispy') && none.length === 1, mine)
  }
  check('my voice: the sample line in the village\'s language with the learner\'s name ("Ciao! Sono Teo."), a greeting without one',
    sampleLine('it', addressee({ name: 'Teo', gender: 'male' }, 'it')) === 'Ciao! Sono Teo.' && sampleLine('sl', addressee({ name: 'Jan', gender: 'male' })) === 'Živjo! Jaz sem Jan.' && sampleLine('de', addressee({}, 'de')) === 'Hallo! Wie geht es dir?' && sampleLine('en', addressee({ name: 'Ana' }, 'en')) === "Hello! I'm Ana.")

  // --- the routes, on the smoke's bridge: Jan, a man, in the Slovene village ---------------------------------------------
  const profile = join(dataDir, 'learner-profile.json')
  const was = readFileSync(profile, 'utf8')
  const touch = (t: number) => utimesSync(profile, t, t)
  try {
    const get = async () => (await fetch(`${base}/learner/voice`, { headers: auth })).json()
    const choose = (voice: string) => fetch(`${base}/learner/voice`, { method: 'POST', headers: auth, body: JSON.stringify({ voice }) })
    const say = (text: string, voice = 'player') => fetch(`${base}/voice/say`, { method: 'POST', headers: auth, body: JSON.stringify({ text, voice }) })
    const s0 = await get()
    const p0 = await (await fetch(`${base}/voice/profiles`, { headers: auth })).json()
    const ids = s0.options?.map((o: any) => o.id) ?? []
    check("GET /learner/voice: Jan is offered the men's voices of the Slovene village (Luka first, the default), not chosen yet, the sample with his name; GET /voice/profiles says it (mine: male, Luka)",
      s0.language === 'sl' && s0.sample === 'Živjo! Jaz sem Jan.' && s0.voice === 'male.sl' && !s0.chosen && !s0.off && s0.age === 'adult' && ids.length >= 2 && ids[0] === 'male.sl' && s0.options.every((o: any) => o.gender === 'male' && o.age === 'adult') && s0.options[0].default &&
        p0.player === 'male' && p0.mine?.voice === 'male' && p0.mine.id === 'male.sl' && p0.mine.name === 'Luka' && p0.mine.chosen === false && p0.mine.off === false && p0.mine.gender === 'male',
      { s0, mine: p0.mine })

    // a voice heard: its sample in it alone, made the first time, kept after
    const other = s0.options[1]
    const otherId = cast()[other.voice]?.elevenlabs
    const t0 = bridgeEleven.st.tts.length
    const sample = await (await fetch(`${base}/voice/sample`, { method: 'POST', headers: auth, body: JSON.stringify({ voice: other.id }) })).json()
    const sampleAgain = await (await fetch(`${base}/voice/sample`, { method: 'POST', headers: auth, body: JSON.stringify({ voice: other.id }) })).json()
    check("POST /voice/sample: a voice offered says the sample in it (ElevenLabs, the village's language), on the normal path: made once, kept",
      sample.text === 'Živjo! Jaz sem Jan.' && /^\/voice\/file\//.test(sample.url) && sample.voice === other.voice && bridgeEleven.st.tts.length === t0 + 1 && bridgeEleven.st.tts.at(-1)?.voice === otherId && bridgeEleven.st.tts.at(-1)?.body.language_code === 'sl' && sampleAgain.url === sample.url && bridgeEleven.st.tts.length === t0 + 1,
      { sample, sampleAgain, tts: bridgeEleven.st.tts.at(-1) })
    const notOffered = await fetch(`${base}/voice/sample`, { method: 'POST', headers: auth, body: JSON.stringify({ voice: 'grandma' }) })
    check('POST /voice/sample: a voice not offered (a villager\'s) is a 400', notOffered.status === 400)

    // the choice: kept in the learner profile, the rest of it as it was; the next line in it
    const r1 = await choose(other.id)
    const s1 = await r1.json()
    const kept = JSON.parse(readFileSync(profile, 'utf8'))
    const before = JSON.parse(was)
    const t1 = bridgeEleven.st.tts.length
    const said = await say('Ja, rad bi juho.')
    const line = said.status === 200 ? await said.json() : { status: said.status }
    const p1 = await (await fetch(`${base}/voice/profiles`, { headers: auth })).json()
    check("POST /learner/voice: Jan's choice is kept in his learner profile (preferences.my_voice), the rest of it as it was; his next line is in it (asked as player), GET /voice/profiles says it",
      r1.ok && s1.voice === other.id && s1.chosen && !s1.off && kept.preferences?.my_voice?.voice === other.id && /^\d{4}-\d\d-\d\d$/.test(kept.preferences.my_voice.since) && kept.preferences.gamification_enabled === before.preferences.gamification_enabled && kept.learner.name === 'Jan' &&
        line.voice === other.voice && bridgeEleven.st.tts.length === t1 + 1 && bridgeEleven.st.tts.at(-1)?.voice === otherId && p1.player === other.voice && p1.mine.chosen === true,
      { s1, my_voice: kept.preferences?.my_voice, line, mine: p1.mine })

    // ElevenLabs can't (down; a store of its own, so the bridge's ElevenLabs isn't paused): a Slovene voice's local Gepard
    // voice says it, though the male narrator has a clip of the line (no one else's clip stands in for the learner's)
    {
      const fe = fakeEleven()
      const fg = fakeGepard()
      const vs = new VoiceStore({ appDir: tempDir('lani-smoke-my-voice-store-'), engines: [new ElevenLabs({ key: 'test-key', base: fe.url }), new Gepard({ url: fg.url })], narrators: sloveneNarrators('sl', {}) })
      await vs.voice('Hvala, ne bi več.', 'male', 'test')
      fe.st.fail = 500
      const local = await vs.say('Hvala, ne bi več.', other.voice, undefined, { alone: true })
      const gepardVoice = cast()[other.voice]?.gepard
      check("my voice: ElevenLabs can't (down): the voice's local Gepard voice says the line (a Slovene village) in its key, though the male narrator has a clip of it",
        local?.voice === other.voice && local.engine === 'gepard' && fg.st.calls.at(-1)?.speaker === gepardVoice && fg.st.calls.at(-1)?.text === 'Hvala, ne bi več.', { local, call: fg.st.calls.at(-1) })
      vs.close()
    }

    const off = await (await choose('off')).json()
    const pOff = await (await fetch(`${base}/voice/profiles`, { headers: auth })).json()
    const wrong = await choose(optionsOf('female', 'adult')[0].id)
    const junk = await choose('../x')
    const back = await (await choose('default')).json()
    check('POST /learner/voice: off (the app says no line of his; /voice/profiles mine.off), a woman\'s voice for a man (not offered) and a malformed one 400, "default" back to Luka, chosen',
      off.off && off.chosen && pOff.mine.off === true && wrong.status === 400 && junk.status === 400 && back.voice === 'male.sl' && back.chosen && !back.off && JSON.parse(readFileSync(profile, 'utf8')).preferences.my_voice.voice === 'default', { off, wrong: wrong.status, junk: junk.status, back })

    const vt = await client.callTool({ name: 'voice_status', arguments: {} })
    const status = JSON.parse((vt as any).content?.[0]?.text ?? '{}')
    check('voice_status: the learner\'s voice (my_voice: the choice, the voice in effect, what is offered) and the cast\'s players', status.my_voice?.choice === 'default' && /male\.sl/.test(status.my_voice.voice) && status.my_voice.offered.length >= 2 && Array.isArray(status.players?.male?.child) && status.players.male.child.length >= 2, { my_voice: status.my_voice, players: status.players })
  } finally {
    writeFileSync(profile, was)
    touch(Date.now() / 1000 + 20)
  }
}
