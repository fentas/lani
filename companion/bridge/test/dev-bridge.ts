// Dev bridge for emulator QA: the real bridge on a COPY of the learner data, with a canned tutor.
// Run: bun test/dev-bridge.ts   → http://127.0.0.1:8791 (emulator: http://10.0.2.2:8791), token "dev-token".
// Env: LANI_DEV_PORT its port; LANI_DEV_DATA the data to copy (default: the default learner's, LANI_DATA_DIR in lani.env,
// else the repo's data/), with its voice clips (the voice cache, paths.ts voiceDir); LANI_DEV_KEEP_TOWNS=1
// keeps the copy's town links (by default QA's town knows only the towns QA links, e.g. test/dev-town.ts, and never
// asks a real learner's town); LANI_DEV_FAKE_ELEVENLABS=1 a fake ElevenLabs (below).
// The outside dictionaries are always a fake here (test/fakes/dictionary.ts): QA never reaches a real site.
import '../src/env' // first: LANI_* from the FLUENT_* names of a node set up before the rename
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { cpSync, existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { setting } from '../src/env'
import { learnerLangs } from '../src/learners'
import { voiceDir } from '../src/paths'
import { fakeDictionary } from './fakes/dictionary'
import { fakeEleven } from './fakes/eleven'

const port = process.env.LANI_DEV_PORT ?? '8791'
const dataDir = mkdtempSync(join(tmpdir(), 'lani-dev-'))
// The copy leaves out release APKs, backups and the data repository's history (large, and QA doesn't need them), and is
// deleted on exit.
const skip = [/\/app\/release(\/|$)/, /\/\.backups(\/|$)/, /\.json\.backup-/, /\/\.git(\/|$)/]
const source = resolve(process.env.LANI_DEV_DATA || setting('LANI_DATA_DIR') || resolve(import.meta.dir, '../../../data'))
cpSync(source, dataDir, { recursive: true, filter: src => !skip.some(r => r.test(src)) })
// The clips: from the voice cache when the data's are there (lani-setup moved them), into the copy's own app/voice, so
// QA never writes into the learner's cache.
const clips = voiceDir(join(source, 'app'), { ...process.env, LANI_DATA_DIR: source })
if (clips !== join(source, 'app', 'voice') && existsSync(clips)) cpSync(clips, join(dataDir, 'app', 'voice'), { recursive: true })
const townsFile = join(dataDir, 'app/towns.json')
if (process.env.LANI_DEV_KEEP_TOWNS !== '1' && existsSync(townsFile)) {
  // the village's name stays; its links go (in the copy)
  const t = JSON.parse(readFileSync(townsFile, 'utf8'))
  writeFileSync(townsFile, JSON.stringify({ ...t, links: [] }, null, 2) + '\n')
}
console.log(`data copy: ${dataDir}`)
const cleanup = () => rmSync(dataDir, { recursive: true, force: true })
process.on('exit', cleanup)
for (const sig of ['SIGINT', 'SIGTERM', 'SIGHUP'] as const) process.on(sig, () => process.exit(0))

const client = new Client({ name: 'dev-tutor', version: '0' })
const reply = (conversation_id: string, text: string, data?: object) =>
  client.callTool({ name: 'reply', arguments: { conversation_id, text, ...(data ? { data } : {}) } })

/**
 * The canned role-play in each course's language: the lines are `sl` and `en` in a reply's data whatever the language
 * (scenarios.ts: "the key is sl in every language"), `en` in the learner's base (a Slovene base reads `sl_base`). One set
 * per language the village may speak (Slovene, Italian, German, English), each fitting any of its role-plays.
 * Machine-written, for QA only (no learner sees them).
 */
type Said = Record<string, string>
const ROLEPLAY: Record<string, { hint: Said; puzzled: Said; turns: Said[]; correction: Said; practise: string }> = {
  sl: {
    hint: { sl: 'Hvala, zelo dobro je.', en: 'Thanks, it is very good.', de: 'Danke, es ist sehr gut.', it: 'Grazie, è molto buono.' },
    puzzled: { sl: 'Kako, prosim? Še enkrat?', en: 'Pardon? Once more?', de: 'Wie bitte? Noch einmal?', it: 'Come, scusa? Ancora una volta?' },
    turns: [
      { sl: 'Me veseli! Še malo juhe?', en: "I'm glad! A bit more soup?", de: 'Das freut mich! Noch ein bisschen Suppe?', it: 'Che piacere! Ancora un po\' di minestra?' },
      { sl: 'Res? Ti je juha všeč?', en: 'Really? Do you like the soup?', de: 'Wirklich? Schmeckt dir die Suppe?', it: 'Davvero? Ti piace la minestra?' },
      { sl: 'Ha ha, prav imaš!', en: "Ha ha, you're right!", de: 'Haha, du hast recht!', it: 'Ah ah, hai ragione!' },
      { sl: 'Hvala! To je moj recept.', en: "Thank you! It's my recipe.", de: 'Danke! Das ist mein Rezept.', it: 'Grazie! È la mia ricetta.' },
    ],
    correction: {
      en: 'Rekli bi: **Zelo je dobro.** · "dobro" agrees with the neuter "it".',
      de: 'Man sagt: **Zelo je dobro.** · „dobro" richtet sich nach dem sächlichen „es".',
      it: 'Si dice: **Zelo je dobro.** · «dobro» concorda con il neutro «esso».',
    },
    practise: '*sit sem*, *še malo*',
  },
  it: {
    // any of the Italian role-plays (companion/scenarios/it: the market, the baker, the ice-cream shop …), so no soup
    hint: { sl: 'Grazie, è buonissimo!', en: "Thanks, it's delicious!", de: 'Danke, das ist super lecker!', sl_base: 'Hvala, odlično je!' },
    puzzled: { sl: 'Come, scusa? Puoi ripetere?', en: 'Pardon? Once more?', de: 'Wie bitte? Noch einmal?', sl_base: 'Kako, prosim? Še enkrat?' },
    turns: [
      { sl: 'Che bello! E poi?', en: 'How lovely! And then?', de: 'Wie schön! Und dann?', sl_base: 'Kako lepo! Pa potem?' },
      { sl: 'Davvero? Ti piace?', en: 'Really? Do you like it?', de: 'Wirklich? Gefällt es dir?', sl_base: 'Res? Ti je všeč?' },
      { sl: 'Ah ah, che simpatico!', en: 'Ha ha, how funny!', de: 'Haha, wie lustig!', sl_base: 'Ha ha, kako smešno!' },
      { sl: 'Bravo! Allora ciao, a presto!', en: 'Well done! Bye then, see you soon!', de: 'Bravo! Also tschüss, bis bald!', sl_base: 'Bravo! Pa adijo, se vidiva!' },
    ],
    correction: { en: 'Si dice: **Mi piace molto.** · "piace" agrees with the thing liked.', de: 'Man sagt: **Mi piace molto.** · „piace“ richtet sich nach dem, was gefällt.', sl: 'Rečemo: **Mi piace molto.** · »piace« se ujema s tem, kar je všeč.' },
    practise: '*vorrei*, *quanto costa?*',
  },
  de: {
    // any of the German role-plays (companion/scenarios/de: the baker, the market, the station, the neighbour …)
    hint: { sl: 'Danke, das ist sehr nett!', en: "Thanks, that's very kind!", it: 'Grazie, molto gentile!', sl_base: 'Hvala, zelo prijazno!' },
    puzzled: { sl: 'Wie bitte? Noch einmal, bitte?', en: 'Pardon? Once more, please?', it: 'Come, scusi? Può ripetere?', sl_base: 'Kako, prosim? Še enkrat, prosim?' },
    turns: [
      { sl: 'Wie schön! Und sonst?', en: 'How lovely! Anything else?', it: 'Che bello! E poi?', sl_base: 'Kako lepo! Pa še kaj?' },
      { sl: 'Wirklich? Das freut mich!', en: "Really? I'm glad!", it: 'Davvero? Mi fa piacere!', sl_base: 'Res? To me veseli!' },
      { sl: 'Haha, sehr gut!', en: 'Ha ha, very good!', it: 'Ah ah, benissimo!', sl_base: 'Ha ha, zelo dobro!' },
      { sl: 'Danke! Also dann, auf Wiedersehen!', en: 'Thank you! Goodbye, then!', it: 'Grazie! Allora arrivederci!', sl_base: 'Hvala! Pa na svidenje!' },
    ],
    correction: {
      en: 'Better: **Ich hätte gern ein Brot, bitte.** · "Ich hätte gern" is the polite "I\'d like".',
      sl: 'Bolje: **Ich hätte gern ein Brot, bitte.** · »Ich hätte gern« je vljudni »rad bi«.',
      it: 'Meglio: **Ich hätte gern ein Brot, bitte.** · «Ich hätte gern» è il cortese «vorrei».',
    },
    practise: '*ich hätte gern*, *was kostet das?*',
  },
  en: {
    // any of the English role-plays (companion/scenarios/en: the farm shop, the pub, the bus stop, the fell …)
    hint: { sl: "Thanks, that's very kind!", en: "Thanks, that's very kind!", de: 'Danke, das ist sehr nett!', it: 'Grazie, molto gentile!', sl_base: 'Hvala, zelo prijazno!' },
    puzzled: { sl: 'Sorry? Could you say that again?', en: 'Sorry? Could you say that again?', de: 'Wie bitte? Kannst du das noch einmal sagen?', it: 'Come, scusa? Puoi ripetere?', sl_base: 'Prosim? Lahko ponoviš?' },
    turns: [
      { sl: 'Lovely! Anything else?', en: 'Lovely! Anything else?', de: 'Schön! Sonst noch etwas?', it: "Bene! Qualcos'altro?", sl_base: 'Lepo! Še kaj?' },
      { sl: "Really? That's nice!", en: "Really? That's nice!", de: 'Wirklich? Das ist schön!', it: 'Davvero? Che bello!', sl_base: 'Res? To je lepo!' },
      { sl: 'Ha ha, very good!', en: 'Ha ha, very good!', de: 'Haha, sehr gut!', it: 'Ah ah, benissimo!', sl_base: 'Ha ha, zelo dobro!' },
      { sl: 'Thanks! Bye for now, then!', en: 'Thanks! Bye for now, then!', de: 'Danke! Also dann, tschüss!', it: 'Grazie! Allora ciao, a presto!', sl_base: 'Hvala! Pa adijo!' },
    ],
    correction: {
      en: 'Better: **I\'d like a loaf, please.** · "I\'d like" is the polite way to ask.',
      de: 'Besser: **I\'d like a loaf, please.** · „I\'d like“ ist das höfliche „ich hätte gern“.',
      sl: 'Bolje: **I\'d like a loaf, please.** · »I\'d like« je vljudni »rad bi«.',
      it: 'Meglio: **I\'d like a loaf, please.** · «I\'d like» è il cortese «vorrei».',
    },
    practise: "*I'd like*, *how much is it?*",
  },
}
/**
 * One set of canned lines for every B1 and B2 talk of a language (a complaint, the mayor, a job interview: no soup there):
 * Slovene's, German's and English's, which ROLEPLAY_LEVELS takes for both levels. Machine-written, for QA only.
 */
const ABOVE_A2: Record<string, (typeof ROLEPLAY)[string]> = {
  sl: {
    // any of Jan's B1 and B2 role-plays (companion/scenarios: the shop, the winemaker, the surgery, the bora, the
    // neighbour, the tourist farm, the mayor, the interview, the doctor, the stone house, the insurance, Marko)
    hint: { sl: 'Razumem. Bi lahko to še enkrat razložili?', en: 'I see. Could you explain that once more?', de: 'Verstehe. Könnten Sie das noch einmal erklären?', it: 'Capisco. Potrebbe spiegarlo ancora una volta?' },
    puzzled: { sl: 'Oprostite, kako prosim? Lahko to ponovite?', en: 'Sorry, pardon? Could you say that again?', de: 'Entschuldigung, wie bitte? Können Sie das wiederholen?', it: 'Scusi, come? Può ripetere?' },
    turns: [
      { sl: 'Razumem. Kaj pa potem?', en: 'I see. And then?', de: 'Verstehe. Und dann?', it: 'Capisco. E poi?' },
      { sl: 'Zanimivo. Zakaj pa?', en: 'Interesting. Why, though?', de: 'Interessant. Und warum?', it: 'Interessante. E perché?' },
      { sl: 'Prav, to se mi zdi smiselno.', en: 'All right, that makes sense to me.', de: 'Gut, das finde ich sinnvoll.', it: 'Va bene, mi sembra sensato.' },
      { sl: 'Dobro, hvala. Tako bomo naredili.', en: "Good, thank you. That's what we'll do.", de: 'Gut, danke. So machen wir es.', it: 'Bene, grazie. Faremo così.' },
    ],
    correction: {
      en: 'Rekli bi: **Bi lahko to ponovili?** · "bi" with the -l form asks politely (the conditional).',
      de: 'Man sagt: **Bi lahko to ponovili?** · „bi" mit der l-Form fragt höflich (der Konditional).',
      it: 'Si dice: **Bi lahko to ponovili?** · «bi» con la forma in -l chiede con cortesia (il condizionale).',
    },
    practise: '*bi lahko*, *na vašem mestu bi*',
  },
  en: {
    // any of the Lake District's B1 and B2 role-plays (companion/scenarios/en: the jacket, Mr Dixon's gate, the garage,
    // the flood with Auntie Edna, Maggie's trip, the inn's summer job; the councillor, the interview, the new patient
    // check, the cottage, the police statement, Jack at a crossroads); the correction is a learner's mistake (would
    // after if), never what people here say (we was, aye: tutor/languages/en.md)
    hint: { sl: 'I see. Could you explain that a bit more?', en: 'I see. Could you explain that a bit more?', de: 'Verstehe. Könnten Sie das etwas genauer erklären?', it: 'Capisco. Potrebbe spiegarlo meglio?', sl_base: 'Razumem. Bi lahko to malo bolj razložili?' },
    puzzled: { sl: "Sorry, I didn't quite catch that. Could you say it again?", en: "Sorry, I didn't quite catch that. Could you say it again?", de: 'Entschuldigung, das habe ich nicht ganz verstanden. Können Sie das wiederholen?', it: 'Scusi, non ho capito bene. Può ripetere?', sl_base: 'Oprostite, tega nisem čisto razumel. Lahko ponovite?' },
    turns: [
      { sl: 'I see. And what happened then?', en: 'I see. And what happened then?', de: 'Verstehe. Und was ist dann passiert?', it: 'Capisco. E poi che cosa è successo?', sl_base: 'Razumem. Kaj pa se je zgodilo potem?' },
      { sl: "Right. And why's that, then?", en: 'All right. And why is that?', de: 'Gut. Und warum das?', it: 'Va bene. E come mai?', sl_base: 'Prav. Zakaj pa?' },
      { sl: 'Aye, that makes sense.', en: 'Yes, that makes sense.', de: 'Ja, das klingt vernünftig.', it: 'Sì, ha senso.', sl_base: 'Ja, to je smiselno.' },
      { sl: "Grand. Thanks, that's what we'll do.", en: "Great. Thanks, that's what we'll do.", de: 'Prima. Danke, so machen wir es.', it: 'Perfetto. Grazie, faremo così.', sl_base: 'Odlično. Hvala, tako bomo naredili.' },
    ],
    correction: {
      en: 'Better: **If I were you, I\'d book early.** · after "if", no "would": "if I were", and "I\'d" in the other half.',
      de: 'Besser: **If I were you, I\'d book early.** · nach „if“ kein „would“: „if I were“, und „I\'d“ im anderen Satzteil.',
      sl: 'Bolje: **If I were you, I\'d book early.** · za »if« ni »would«: »if I were«, »I\'d« pa v drugem delu.',
      it: 'Meglio: **If I were you, I\'d book early.** · dopo «if» niente «would»: «if I were», e «I\'d» nell\'altra parte.',
    },
    practise: "*if I were you, I'd …*, *could you …?*",
  },
  de: {
    // any of the German B1 and B2 role-plays (companion/scenarios/de: the jacket, Stefan's Most, the surgery, the storm,
    // Tante Mitzi, Kathi's Gasthaus, the mayor, the interview, the new doctor, the car, the slopes, Florian)
    hint: { sl: 'Verstehe. Könnten Sie das noch einmal erklären?', en: 'I see. Could you explain that once more?', it: 'Capisco. Potrebbe spiegarlo ancora una volta?', sl_base: 'Razumem. Bi lahko to še enkrat razložili?' },
    puzzled: { sl: 'Entschuldigung, wie bitte? Können Sie das wiederholen?', en: 'Sorry, pardon? Could you say that again?', it: 'Scusi, come? Può ripetere?', sl_base: 'Oprostite, kako prosim? Lahko to ponovite?' },
    turns: [
      { sl: 'Verstehe. Und wie ist es dann weitergegangen?', en: 'I see. And what happened then?', it: 'Capisco. E poi com\'è andata avanti?', sl_base: 'Razumem. Kaj pa se je zgodilo potem?' },
      { sl: 'Interessant. Und warum eigentlich?', en: 'Interesting. And why, actually?', it: 'Interessante. E perché, di preciso?', sl_base: 'Zanimivo. Zakaj pa pravzaprav?' },
      { sl: 'Gut, das klingt vernünftig.', en: 'All right, that sounds reasonable.', it: 'Bene, mi sembra ragionevole.', sl_base: 'Dobro, to se sliši smiselno.' },
      { sl: 'Passt, danke. So machen wir es.', en: "Fine, thank you. That's what we'll do.", it: 'Va bene, grazie. Facciamo così.', sl_base: 'Velja, hvala. Tako bomo naredili.' },
    ],
    correction: {
      en: 'Better: **Könnten Sie das bitte wiederholen?** · "könnten" (the Konjunktiv II) asks politely.',
      sl: 'Bolje: **Könnten Sie das bitte wiederholen?** · »könnten« (Konjunktiv II) vljudno prosi.',
      it: 'Meglio: **Könnten Sie das bitte wiederholen?** · «könnten» (il Konjunktiv II) chiede con cortesia.',
    },
    practise: '*könnten Sie*, *an deiner Stelle würde ich*',
  },
}
/**
 * A B1 or B2 role-play's canned lines (its brief's level), over its language's A1 ones: a story asked for, a problem
 * weighed, a deal settled, with the level's forms (the imperfetto beside the passato prossimo at B1, the congiuntivo and
 * the unreal past at B2). B1's say tu, as every B1 talk does; B2's neither tu nor Lei, so they fit Davide and the counter.
 * Machine-written, for QA only.
 */
const ROLEPLAY_LEVELS: Record<string, Partial<Record<string, Partial<(typeof ROLEPLAY)[string]>>>> = {
  // Jan's B1 and B2 talks (companion/scenarios), the Carinthian village's and the Lake District's (scenarios/de, en): one set each
  sl: { B1: ABOVE_A2.sl, B2: ABOVE_A2.sl },
  de: { B1: ABOVE_A2.de, B2: ABOVE_A2.de },
  en: { B1: ABOVE_A2.en, B2: ABOVE_A2.en },
  it: {
    // the B1 talks (companion/scenarios/it: the broken kite, the stickers, the swing, the ankle, Lorenzo, the cat)
    B1: {
      hint: { sl: 'Mentre giocavo, è successa una cosa strana.', en: 'While I was playing, something strange happened.', de: 'Während ich gespielt habe, ist etwas Komisches passiert.', sl_base: 'Med igro se je zgodilo nekaj čudnega.' },
      puzzled: { sl: 'Scusa, non ho capito bene. Me lo ripeti?', en: "Sorry, I didn't quite get that. Will you say it again?", de: 'Entschuldige, das habe ich nicht ganz verstanden. Sagst du es noch mal?', sl_base: 'Oprosti, ne razumem čisto. Mi ponoviš?' },
      turns: [
        { sl: 'Ah, capisco. E poi che cosa è successo?', en: 'Ah, I see. And then what happened?', de: 'Ah, verstehe. Und was ist dann passiert?', sl_base: 'Aha, razumem. Pa kaj se je zgodilo potem?' },
        { sl: 'Davvero? Hai fatto bene a dirmelo.', en: 'Really? You were right to tell me.', de: 'Wirklich? Gut, dass du es mir sagst.', sl_base: 'Res? Prav je, da mi to poveš.' },
        { sl: 'Va bene, allora facciamo così.', en: "All right, let's do it like that, then.", de: 'Gut, dann machen wir es so.', sl_base: 'Prav, potem naredimo tako.' },
        { sl: 'Grazie mille! Ci vediamo presto!', en: 'Thanks a lot! See you soon!', de: 'Vielen Dank! Bis bald!', sl_base: 'Najlepša hvala! Se kmalu vidimo!' },
      ],
      correction: {
        en: 'Si dice: **Mentre giocavo, è arrivato il temporale.** · the imperfetto for what was going on, the passato prossimo for what happened.',
        de: 'Man sagt: **Mentre giocavo, è arrivato il temporale.** · das Imperfetto für das, was gerade lief, das Passato prossimo für das, was passiert ist.',
        sl: 'Rečemo: **Mentre giocavo, è arrivato il temporale.** · imperfetto za to, kar je potekalo, passato prossimo za to, kar se je zgodilo.',
      },
      practise: '*mentre + imperfetto*, *vorrei*',
    },
    // the B2 talks (the cancelled train, the landslide, the smith's gate, the interview, the family doctor, Davide)
    B2: {
      hint: { sl: "Capisco, però credo che ci sia un'altra soluzione.", en: "I understand, but I think there's another solution.", de: 'Ich verstehe, aber ich glaube, es gibt eine andere Lösung.', sl_base: 'Razumem, a mislim, da obstaja druga rešitev.' },
      puzzled: { sl: 'Temo di non aver capito bene. Ancora una volta?', en: "I'm afraid I didn't quite understand. Once more?", de: 'Ich fürchte, ich habe das nicht ganz verstanden. Noch einmal?', sl_base: 'Bojim se, da tega ne razumem čisto. Še enkrat?' },
      turns: [
        { sl: 'Capisco perfettamente. Mi dispiace che sia andata così.', en: "I understand completely. I'm sorry it turned out like that.", de: 'Das verstehe ich gut. Es tut mir leid, dass es so gelaufen ist.', sl_base: 'Popolnoma razumem. Žal mi je, da se je tako izteklo.' },
        { sl: "Se l'avessimo saputo prima, avremmo trovato una soluzione.", en: "If we'd known earlier, we'd have found a solution.", de: 'Hätten wir das früher gewusst, hätten wir eine Lösung gefunden.', sl_base: 'Če bi to vedeli prej, bi našli rešitev.' },
        { sl: 'Benché non sia facile, vediamo che cosa si può fare.', en: "Although it isn't easy, let's see what can be done.", de: 'Auch wenn es nicht leicht ist: Schauen wir, was sich machen lässt.', sl_base: 'Čeprav ni lahko, poglejmo, kaj se da narediti.' },
        { sl: "D'accordo, allora restiamo così. Grazie e buona giornata!", en: "Agreed, that's settled, then. Thank you and have a good day!", de: 'Einverstanden, dann bleibt es dabei. Danke und einen schönen Tag!', sl_base: 'Dogovorjeno, potem ostane tako. Hvala in lep dan!' },
      ],
      correction: {
        en: "Si dice: **Se l'avessi saputo, avrei preso il treno prima.** · an unreal past: se + the congiuntivo trapassato, then the condizionale passato.",
        de: "Man sagt: **Se l'avessi saputo, avrei preso il treno prima.** · Für eine Vergangenheit, die nicht so war: se + Congiuntivo trapassato, dann Condizionale passato.",
        sl: "Rečemo: **Se l'avessi saputo, avrei preso il treno prima.** · za preteklost, ki ni bila taka: se + congiuntivo trapassato, nato condizionale passato.",
      },
      practise: '*se avessi …, avrei …*, *penso che + congiuntivo*',
    },
  },
}
const DEBRIEF: Said = {
  en: '✅ You greeted politely.\n\n📌 Practise',
  de: '✅ Du hast höflich gegrüßt.\n\n📌 Übe',
  sl: '✅ Tvoj pozdrav je bil vljuden.\n\n📌 Za vajo',
  it: '✅ Hai salutato con gentilezza.\n\n📌 Da esercitare',
}

/** The hints given so far in each conversation: the next hint is the scenario's next useful phrase. */
const hinted = new Map<string, number>()

/** The next of scenario [id]'s useful phrases for [conversation], as the app gets them (in the learner's base); none for a villager's or a visit's. */
async function scenarioHint(id: string, conversation: string): Promise<{ sl: string; en: string } | undefined> {
  if (!id || id.includes(':')) return undefined
  try {
    const r = await fetch(`http://127.0.0.1:${port}/scenarios/${id}`, { headers: { authorization: 'Bearer dev-token' } })
    const hints = ((await r.json()) as { vocabulary_hints?: { sl: string; en: string }[] }).vocabulary_hints ?? []
    if (!hints.length) return undefined
    const n = hinted.get(conversation) ?? 0
    hinted.set(conversation, n + 1)
    return hints[n % hints.length]
  } catch {
    return undefined
  }
}

/** The course's language (the village's: its culture pack's, GET /culture) and the learner's base, asked once. */
let course: { target: string; base: string } | undefined
async function courseLangs(): Promise<{ target: string; base: string }> {
  if (course) return course
  try {
    const r = await fetch(`http://127.0.0.1:${port}/culture`, { headers: { authorization: 'Bearer dev-token' } })
    const language = ((await r.json()) as { language?: string }).language ?? 'sl'
    course = learnerLangs(dataDir, language)
  } catch {
    return learnerLangs(dataDir, 'sl')
  }
  return course
}

/**
 * A role-play's language: the scenario's own (its brief's language: an Italian role-play in Italian, whatever the course),
 * a visit's talk the visited town's (data.visit.language), any other the course's. Its lines are the language's, a B1 or
 * B2 one's at its level where the language has them (ROLEPLAY_LEVELS).
 */
async function talkLangs(d: { scenario?: { language?: string; level?: string }; visit?: { language?: string } }): Promise<{ lines: (typeof ROLEPLAY)[string]; target: string; base: string }> {
  const c = await courseLangs()
  const target = d.scenario?.language ?? d.visit?.language ?? c.target
  const base = c.base === target ? 'en' : c.base
  const lines = ROLEPLAY[target] ?? ROLEPLAY.sl
  return { lines: { ...lines, ...ROLEPLAY_LEVELS[target]?.[d.scenario?.level ?? ''] }, target, base }
}

/**
 * [s] for a reply: its line (`sl`, in the talk's language) and its translation in [base] (`en`), English without one. A
 * Slovene base reads `sl_base` (the key `sl` is the line itself).
 */
const said = (s: Said, base: string) => ({ sl: s.sl, en: (base === 'sl' ? s.sl_base : s[base]) ?? s.en })

client.fallbackNotificationHandler = async n => {
  const p = n.params as any
  if (n.method === 'notifications/claude/channel/permission_request') return
  if (n.method !== 'notifications/claude/channel') return
  const { kind, conversation_id } = p.meta
  console.log(`← ${kind} [${conversation_id}] ${String(p.content).slice(0, 160).replace(/\n/g, ' ')}`)
  if (kind === 'answer') await reply(conversation_id, '**Dobro!** 🟢 (dev tutor) Nice sentence.', { score: 8 })
  else if (kind === 'chat' && /tabela/i.test(String(p.content)))
    // Markdown QA: a table, a heading, a numbered list and tappable Slovene.
    await reply(conversation_id, [
      '## Glagol *biti* · to be',
      '',
      '| oseba | ednina | dvojina | množina |',
      '|---|---|---|---|',
      '| 1. | *sem* | *sva* | *smo* |',
      '| 2. | *si* | *sta* | *ste* |',
      '| 3. | *je* | *sta* | *so* |',
      '',
      '1. Say „Jaz sem Jan.“ out loud.',
      '2. Then try *Midva sva iz Gorice.*',
    ].join('\n'))
  else if (kind === 'chat' && /"image":\s*\{/.test(String(p.content))) {
    // a photo (README "Tutor chat", "Photos"): its event's data.image, as QA's chat-photo step reads it from the log
    const image = JSON.parse(/```json\n([\s\S]*)\n```/.exec(String(p.content))?.[1] ?? '{}').image ?? {}
    console.log(`← chat image ${JSON.stringify(image)}`)
    await reply(conversation_id, `📷 (dev tutor) **Vidim fotografijo** · I see the photo: ${image.width}×${image.height}, ${Math.round((image.bytes ?? 0) / 1024)} KB.`)
  } else if (kind === 'chat') await reply(conversation_id, `(dev tutor) Prejel sem: *${String(p.content).split('\n')[0].slice(0, 60)}*`)
  else if (kind === 'session_end') await reply(conversation_id, '(dev tutor) 🎉 Session saved.')
  else if (kind === 'roleplay') {
    const d = JSON.parse(/```json\n([\s\S]*)\n```/.exec(String(p.content))?.[1] ?? '{}')
    await Bun.sleep(2500) // the real tutor takes 10-30 s
    // in the talk's language: the course's (an Italian learner's talk in Italian), a visit's in the town's
    const { lines, base, target } = await talkLangs(d)
    if (d.hint) {
      const own = await scenarioHint(String(d.scenario_id ?? ''), conversation_id)
      const h = own && (!d.scenario?.language || d.scenario.language === target) ? own : said(lines.hint, base)
      await reply(conversation_id, h.sl, { hint: true, ...h })
    }
    else {
      const turn = Number(d.turn ?? 1)
      const goals = Array.from({ length: Math.min(turn, d.scenario?.goals?.length ?? 0) }, (_, i) => i)
      const correction = turn === 2 ? lines.correction[base] ?? lines.correction.en : undefined
      // a line and a face a turn (companion/SCENES.md, "Faces"), so QA's screenshots of each differ; nothing heard: puzzled
      const faces = ['happy', 'curious', 'laugh', 'proud']
      const line = !String(d.heard ?? '').trim()
        ? { ...said(lines.puzzled, base), face: 'confused' }
        : { ...said(lines.turns[Math.min(turn, 4) - 1] ?? lines.turns[3], base), face: faces[Math.min(turn, 4) - 1] ?? 'proud' }
      await reply(conversation_id, line.sl, { ...line, goals_done: goals, correction, end: turn >= 4 || undefined })
    }
  } else if (kind === 'roleplay_end') {
    await Bun.sleep(2500)
    const d = JSON.parse(/```json\n([\s\S]*)\n```/.exec(String(p.content))?.[1] ?? '{}')
    const { lines, base } = await talkLangs(d)
    await reply(conversation_id, `(dev tutor) **Bravo!** 🎉\n\n${DEBRIEF[base] ?? DEBRIEF.en}: ${lines.practise}.`, { debrief: true })
  }
  else if (kind === 'partner_answer') await reply(conversation_id, '**Lepo!** 🟢 (dev tutor) Nice answer.', { score: 8, partner_note: '(dev tutor) Jan answered well.' })
  else if (kind === 'gloss_request') {
    // words asked for from a card ("➕ Add to my words" on a word the dictionary lacks): a meaning for each after
    // LANI_DEV_GLOSS_MS (default 4 s), so QA sees the card wait, then fill in; the words QA asks for have their own
    await Bun.sleep(Number(process.env.LANI_DEV_GLOSS_MS ?? 4000))
    const d = JSON.parse(/```json\n([\s\S]*)\n```/.exec(String(p.content))?.[1] ?? '{}').gloss_requests ?? {}
    for (const w of d.words ?? []) {
      const known = DEV_GLOSSES[String(w.form).toLowerCase()]
      const gloss = known?.gloss[d.gloss_lang] ?? [`(dev tutor) ${w.form}`]
      const r = await client.callTool({ name: 'publish_gloss', arguments: { form: w.form, lemma: known?.lemma ?? w.form, pos: known?.pos ?? 'noun', gloss, gloss_lang: d.gloss_lang, language: d.language, ...(known?.grammar ? { grammar: known.grammar } : {}), ...(known?.gender ? { gender: known.gender } : {}) } })
      console.log(`→ publish_gloss ${w.form}: ${JSON.stringify((r as any).content?.[0]?.text ?? r).slice(0, 200)}`)
    }
  }
}

/** The dev tutor's meanings of the words QA asks for (a card's "➕ Add to my words"), by their gloss language. */
const DEV_GLOSSES: Record<string, { lemma: string; pos: string; gender?: string; grammar?: string; gloss: Record<string, string[]> }> = {
  vreteno: { lemma: 'vreteno', pos: 'noun', gender: 'n', grammar: 'nominative/accusative singular', gloss: { en: ['spindle'], de: ['Spindel'], it: ['fuso'] } },
  vretenom: { lemma: 'vreteno', pos: 'noun', gender: 'n', grammar: 'instrumental singular', gloss: { en: ['spindle'], de: ['Spindel'], it: ['fuso'] } },
  motovilo: { lemma: 'motovilo', pos: 'noun', gender: 'n', grammar: 'nominative/accusative singular', gloss: { en: ['reel', 'yarn reel'], de: ['Haspel'], it: ['aspo'] } },
  arcolaio: { lemma: 'arcolaio', pos: 'noun', gender: 'm', grammar: 'masculine singular', gloss: { de: ['Garnwinde', 'Haspel'], en: ['yarn swift'], sl: ['motovilo'] } },
  aspo: { lemma: 'aspo', pos: 'noun', gender: 'm', grammar: 'masculine singular', gloss: { de: ['Haspel', 'Weife'], en: ['reel'], sl: ['motovilo'] } },
}

// LANI_DEV_FAKE_ELEVENLABS=1: the bridge talks to a fake ElevenLabs here (test/fakes/eleven.ts: tones, longer for a longer
// text, a quota of its own), so the whole path of a clip made on demand runs without the real API; as the real service it
// takes LANI_DEV_FAKE_ELEVENLABS_MS a take plus LANI_DEV_FAKE_ELEVENLABS_MS_PER_CHAR a character (default 600 and 15) and
// refuses what goes over LANI_DEV_FAKE_ELEVENLABS_CONCURRENCY at once (default 5, a Creator plan's; 0: no limit).
// LANI_DEV_FAKE_ELEVENLABS_DOWN=<file>: while that file is there, the fake answers every take with a 503, as the service
// down (companion/bin/qa's visit-voices: a line the node can't voice); the bridge then pauses ElevenLabs for a while, as it
// does for the real one.
const fake = process.env.LANI_DEV_FAKE_ELEVENLABS === '1' ? fakeEleven({ tones: true }) : undefined
if (fake) {
  const num = (name: string, d: number) => (process.env[name] ? Number(process.env[name]) : d)
  Object.assign(fake.st, {
    delayMs: num('LANI_DEV_FAKE_ELEVENLABS_MS', 600), msPerChar: num('LANI_DEV_FAKE_ELEVENLABS_MS_PER_CHAR', 15),
    concurrency: num('LANI_DEV_FAKE_ELEVENLABS_CONCURRENCY', 5), limit: 180_000,
    // no voice slot free: nobody gets a voice of their own designed on the fake, everyone keeps the voice the copy gives them
    voiceLimit: 0,
  })
  console.log(`fake ElevenLabs on ${fake.url} (concurrency ${fake.st.concurrency}, ${fake.st.delayMs} ms + ${fake.st.msPerChar} ms a character)`)
  const down = process.env.LANI_DEV_FAKE_ELEVENLABS_DOWN
  if (down) {
    setInterval(() => {
      const off = existsSync(down)
      if (off === (fake.st.fail === 503)) return
      fake.st.fail = off ? 503 : 0
      console.log(`fake ElevenLabs: ${off ? 'down (every take a 503)' : 'up again'}`)
    }, 300)
  }
  // what it was asked, as it goes: QA's log shows the takes, what went over the concurrency and the most at once
  let told = 0
  let refused = 0
  setInterval(() => {
    if (fake.st.tts.length === told && fake.st.refused === refused) return
    for (const t of fake.st.tts.slice(told)) console.log(`fake ElevenLabs: ${t.voice} ${t.body.language_code ?? '(no language_code)'} ${String(t.body.text).length} chars "${String(t.body.text).slice(0, 60)}"`)
    told = fake.st.tts.length
    refused = fake.st.refused
    console.log(`fake ElevenLabs: ${told} takes asked, ${refused} refused (over the concurrency), at most ${fake.st.maxInFlight} at once`)
  }, 1_000)
}
const eleven = fake
  ? { ELEVENLABS_API_KEY: 'test-key', ELEVENLABS_BASE_URL: fake.url, LANI_VOICE_DESIGN: 'off' }
  : // QA must not spend the ElevenLabs quota; LANI_DEV_ELEVENLABS=1 allows it. Cached clips still play.
    { ELEVENLABS_API_KEY: process.env.LANI_DEV_ELEVENLABS === '1' ? (process.env.ELEVENLABS_API_KEY ?? '') : '' }

// what QA's tutor turn looks up (bin/qa): words the lexicon has nothing on, one in each language's section
const dictionary = fakeDictionary({
  zlatoperka: { section: 'Slovene', text: 'a word of the fake dictionary (QA)' },
  zibaldone: { section: 'Italian', text: 'a word of the fake dictionary (QA)' },
  Schnickschnack: { section: 'German', text: 'a word of the fake dictionary (QA)', wortart: 'Substantiv' },
  thingamajig: { section: 'English', text: 'a word of the fake dictionary (QA)' },
})

await client.connect(
  new StdioClientTransport({
    command: 'bun',
    args: [resolve(import.meta.dir, '../src/index.ts')],
    env: { ...process.env, LANI_DICTIONARY_SOURCE: dictionary.url, LANI_DICTIONARY_CACHE: join(dataDir, 'app', 'dictionary-cache'), LANI_DATA_DIR: dataDir, LANI_VOICE_CACHE: join(dataDir, 'app', 'voice'), LANI_BRIDGE_PORT: port, LANI_BRIDGE_TOKEN: 'dev-token', LANI_FAMILY_TOKEN: 'dev-family', LANI_RHYTHM: 'off', ...eleven } as Record<string, string>,
    stderr: 'inherit',
  }),
)
console.log(`dev bridge up on :${port} (token dev-token; family page http://127.0.0.1:${port}/family#t=dev-family)`)
// Never outlive QA: exit when the bridge closes, or when whoever started us is gone (we'd be re-parented to init).
// Orphaned dev bridges once piled up for days, each spinning a core.
client.onclose = () => process.exit(1)
const parent = process.ppid
setInterval(() => { if (process.ppid !== parent) process.exit(0) }, 2000)
await new Promise(() => {})
