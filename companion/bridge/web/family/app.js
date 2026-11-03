// Lani family page: send the learner challenges, see their answers, record words, in the learner's languages. No build step.
'use strict'

const $ = id => document.getElementById(id)
// Served by the relay (companion/relay) at /family/<town id>/ for a town with no node: its API goes through the relay
// to the town, at /v1/family/<town id>/…, and what the browser keeps is that town's (every town's page there has one
// origin); served by the bridge at /family, it is the bridge's own.
const RELAYED = /^\/family\/([0-9a-f]{32})(?:\/|$)/.exec(location.pathname)
const API = RELAYED ? `/v1/family/${RELAYED[1]}` : ''
const mine = key => (RELAYED ? `${key}:${RELAYED[1]}` : key)
const KEY = mine('lani-family-token')
const REC_AS = mine('lani-family-rec-as')
let settings = null
// what this browser saved before the project was renamed from Fluent: still read
const LEGACY = { 'lani-family-token': 'fluent-family-token', 'lani-family-rec-as': 'fluent-family-rec-as' }
const saved = key => localStorage.getItem(key) ?? (LEGACY[key] ? localStorage.getItem(LEGACY[key]) : null)
const EMOJIS = ['💌', '👩', '👨', '👵', '👴', '🧑', '👧', '👦', '❤️', '🌻']

// --- the page's languages ------------------------------------------------------------------------
// Every label as the app has it: the language the learner learns first, the learner's base second ("Vrsta · Type", "Tipo ·
// Art"). The family of anyone reads it: the bridge says which two (settings.language, settings.base), a bridge from before
// that said nothing has Slovene and English. Machine-written, not reviewed by a native speaker.
// A language's name as each language says it: {in}: "in Slovene", {name}: on its own, {help}: what "help with" takes.
const NAMES = {
  sl: {
    in: { sl: 'v slovenščini', en: 'v angleščini', de: 'v nemščini', it: 'v italijanščini' },
    name: { sl: 'slovensko', en: 'angleško', de: 'nemško', it: 'italijansko' },
    help: { sl: 'slovenščini', en: 'angleščini', de: 'nemščini', it: 'italijanščini' },
  },
  en: {
    in: { sl: 'in Slovene', en: 'in English', de: 'in German', it: 'in Italian' },
    name: { sl: 'Slovene', en: 'English', de: 'German', it: 'Italian' },
    help: { sl: 'Slovene', en: 'English', de: 'German', it: 'Italian' },
  },
  de: {
    in: { sl: 'auf Slowenisch', en: 'auf Englisch', de: 'auf Deutsch', it: 'auf Italienisch' },
    name: { sl: 'Slowenisch', en: 'Englisch', de: 'Deutsch', it: 'Italienisch' },
    help: { sl: 'Slowenisch', en: 'Englisch', de: 'Deutsch', it: 'Italienisch' },
  },
  it: {
    in: { sl: 'in sloveno', en: 'in inglese', de: 'in tedesco', it: 'in italiano' },
    name: { sl: 'sloveno', en: 'inglese', de: 'tedesco', it: 'italiano' },
    help: { sl: 'lo sloveno', en: "l'inglese", de: 'il tedesco', it: "l'italiano" },
  },
}
const LOCALES = { sl: 'sl-SI', en: 'en-GB', de: 'de-AT', it: 'it-IT' }
// {target}, {base}: the languages of the pair ({in…}, {name…}, {help…}: NAMES), {who}: the learner, each as this language says it
const STRINGS = {
  family: { sl: 'Družina', en: 'Family', de: 'Familie', it: 'Famiglia' },
  help: { sl: 'Pomagaj pri {helpTarget}', en: 'Help {who} learn {helpTarget}', de: 'Hilf {who}, {helpTarget} zu lernen', it: 'Aiuta {who} a imparare {helpTarget}' },
  settings: { sl: 'Nastavitve', en: 'Settings', de: 'Einstellungen', it: 'Impostazioni' },
  lockedTitle: { sl: 'Manjka ključ', en: 'Key missing', de: 'Schlüssel fehlt', it: 'Manca la chiave' },
  lockedText: { sl: 'Odpri povezavo, ki ti je bila poslana (na koncu ima #t=…).', en: 'Open the link you were sent (it ends with #t=…).', de: 'Öffne den Link, den du bekommen hast (er endet mit #t=…).', it: 'Apri il link che hai ricevuto (finisce con #t=…).' },
  whoTitle: { sl: 'Kdo si?', en: 'Who are you?', de: 'Wer bist du?', it: 'Chi sei?' },
  yourName: { sl: 'Ime', en: 'Your name', de: 'Dein Name', it: 'Il tuo nome' },
  yourEmoji: { sl: 'Tvoj znak', en: 'Your emoji', de: 'Dein Emoji', it: 'La tua emoji' },
  fromForm: { sl: '„Od …“ (neobvezno)', en: '"From …" in Slovene (optional)', de: '„Von …“ auf Slowenisch (optional)', it: '«Da …» in sloveno (facoltativo)' },
  whoSees: { sl: '{who} vidi:', en: '{who} sees:', de: '{who} sieht:', it: '{who} vede:' },
  from: { sl: 'Od {from}', en: 'From {from}', de: 'Von {from}', it: 'Da {from}' },
  save: { sl: 'Shrani', en: 'Save', de: 'Speichern', it: 'Salva' },
  saved: { sl: 'Shranjeno', en: 'Saved', de: 'Gespeichert', it: 'Salvato' },
  friends: { sl: 'Prijatelji', en: 'Friends', de: 'Freunde', it: 'Amici' },
  linkedTowns: { sl: 'Povezane vasi', en: 'Linked towns', de: 'Verbundene Dörfer', it: 'Paesi collegati' },
  newMessage: { sl: 'Novo sporočilo', en: 'New message', de: 'Neue Nachricht', it: 'Nuovo messaggio' },
  type: { sl: 'Vrsta', en: 'Type', de: 'Art', it: 'Tipo' },
  question: { sl: 'Vprašanje', en: 'Question', de: 'Frage', it: 'Domanda' },
  questionLong: { sl: 'Vprašanje za odgovor', en: 'Question to answer', de: 'Frage zum Beantworten', it: 'Domanda a cui rispondere' },
  translate: { sl: 'Prevedi', en: 'Translate', de: 'Übersetzen', it: 'Traduci' },
  translateLong: { sl: 'Stavek za prevod', en: 'Sentence to translate', de: 'Satz zum Übersetzen', it: 'Frase da tradurre' },
  say: { sl: 'Povej', en: 'Say this', de: 'Sag das', it: 'Dillo' },
  sayLong: { sl: 'Povej to', en: 'Say this', de: 'Sag das', it: 'Da dire' },
  textQuestion: { sl: 'Vprašanje {inTarget}', en: 'Your question {inTarget}', de: 'Deine Frage {inTarget}', it: 'La tua domanda {inTarget}' },
  textTranslate: { sl: 'Stavek za prevod ({nameTarget} ali {nameBase})', en: 'Sentence to translate ({nameTarget} or {nameBase})', de: 'Satz zum Übersetzen ({nameTarget} oder {nameBase})', it: 'Frase da tradurre ({nameTarget} o {nameBase})' },
  textSay: { sl: 'Kaj naj {who} reče?', en: 'What should {who} say?', de: 'Was soll {who} sagen?', it: 'Che cosa deve dire {who}?' },
  hintLabel: { sl: 'Namig {inBase} (neobvezno)', en: 'Hint {inBase} (optional)', de: 'Hinweis {inBase} (optional)', it: 'Suggerimento {inBase} (facoltativo)' },
  hintNote: { sl: 'Namig vidi {who} šele, ko ga odpre.', en: '{who} sees the hint only after tapping it.', de: '{who} sieht den Hinweis erst nach einem Tipp darauf.', it: '{who} vede il suggerimento solo dopo averlo toccato.' },
  sendTo: { sl: 'Pošlji', en: 'Send to {who}', de: 'An {who} schicken', it: 'Invia a {who}' },
  sent: { sl: 'Poslano!', en: 'Sent!', de: 'Geschickt!', it: 'Inviato!' },
  queued: { sl: 'Shranjeno: {who} dobi, ko bo vas spet na spletu', en: 'Saved: {who} gets it when their town is back online', de: 'Gespeichert: {who} bekommt es, sobald das Dorf wieder online ist', it: 'Salvato: {who} lo riceve quando il paese torna in linea' },
  conversations: { sl: 'Pogovori', en: 'Conversations', de: 'Gespräche', it: 'Conversazioni' },
  threadEmpty: { sl: 'Še nič. Pošlji prvo sporočilo!', en: 'Nothing yet. Send {who} a first message!', de: 'Noch nichts. Schick {who} eine erste Nachricht!', it: 'Ancora niente. Manda a {who} un primo messaggio!' },
  waiting: { sl: 'Čaka na odgovor', en: 'Waiting for {who}', de: 'Wartet auf {who}', it: 'In attesa di {who}' },
  tutorLooking: { sl: 'Učitelj pregleduje', en: 'The tutor is looking at it…', de: 'Der Tutor schaut es sich an …', it: "L'insegnante ci sta guardando …" },
  tutor: { sl: 'Učitelj', en: 'Tutor', de: 'Tutor', it: 'Insegnante' },
  whatWasTold: { sl: 'Kaj je rekel učitelj', en: 'What {who} was told', de: 'Was {who} gesagt wurde', it: 'Che cosa è stato detto a {who}' },
  recordThis: { sl: 'Posnemi to', en: 'Record this', de: 'Das aufnehmen', it: 'Registra questo' },
  recordWords: { sl: 'Posnemi besede', en: 'Record words', de: 'Wörter aufnehmen', it: 'Registra parole' },
  recordAbout: { sl: '{who} sliši tvoj glas namesto robota. Govori naravno, po domače.', en: '{who} will hear your voice instead of the robot voice. Speak naturally, the way you talk at home.', de: '{who} hört deine Stimme statt der Roboterstimme. Sprich ganz natürlich, wie zu Hause.', it: '{who} sente la tua voce invece di quella del robot. Parla in modo naturale, come a casa.' },
  recordingAs: { sl: 'Snema', en: 'Recording as', de: 'Aufnahme von', it: 'Registrazione di' },
  anyPhrase: { sl: 'Druga beseda ali stavek', en: 'Any word or phrase', de: 'Ein anderes Wort oder ein Satz', it: "Un'altra parola o frase" },
  record: { sl: 'Posnemi', en: 'Record', de: 'Aufnehmen', it: 'Registra' },
  toRecord: { sl: 'Manjka', en: 'To record', de: 'Noch aufzunehmen', it: 'Da registrare' },
  recorded: { sl: 'Posneto', en: 'Recorded', de: 'Aufgenommen', it: 'Registrate' },
  everythingRecorded: { sl: 'Vse je posneto', en: 'Everything is recorded!', de: 'Alles ist aufgenommen!', it: 'È tutto registrato!' },
  nothingRecorded: { sl: 'Še nič posnetega', en: 'Nothing recorded yet.', de: 'Noch nichts aufgenommen.', it: 'Ancora niente di registrato.' },
  remove: { sl: 'Izbriši', en: 'Delete', de: 'Löschen', it: 'Elimina' },
  removeAsk: { sl: 'Izbrišem ta posnetek?', en: 'Delete this recording?', de: 'Diese Aufnahme löschen?', it: 'Eliminare questa registrazione?' },
  couldNotPlay: { sl: 'Predvajanje ni uspelo', en: 'Could not play', de: 'Abspielen hat nicht geklappt', it: 'Riproduzione non riuscita' },
  cannotRecord: { sl: 'Ta brskalnik ne more snemati', en: 'This browser cannot record', de: 'Dieser Browser kann nicht aufnehmen', it: 'Questo browser non può registrare' },
  readAloud: { sl: 'Preberi na glas', en: 'Read aloud', de: 'Lies laut vor', it: 'Leggi ad alta voce' },
  tapAndSpeak: { sl: 'Tapni ⏺ in govori', en: 'Tap ⏺ and speak', de: 'Tippe auf ⏺ und sprich', it: 'Tocca ⏺ e parla' },
  allowMic: { sl: 'Dovoli mikrofon za to stran', en: 'Allow the microphone for this page', de: 'Erlaube das Mikrofon für diese Seite', it: 'Consenti il microfono per questa pagina' },
  listenThenSave: { sl: 'Poslušaj in shrani', en: 'Listen, then save', de: 'Anhören, dann speichern', it: 'Ascolta, poi salva' },
  recordingNow: { sl: 'Snemam … tapni ⏹', en: 'Recording … tap ⏹ (max {seconds} s)', de: 'Aufnahme … tippe auf ⏹ (max. {seconds} s)', it: 'Registro … tocca ⏹ (max {seconds} s)' },
  listen: { sl: 'Poslušaj', en: 'Listen', de: 'Anhören', it: 'Ascolta' },
  again: { sl: 'Znova', en: 'Again', de: 'Nochmal', it: 'Di nuovo' },
  close: { sl: 'Zapri', en: 'Close', de: 'Schließen', it: 'Chiudi' },
  tooLong: { sl: 'Predolgo', en: 'Too long, try a shorter take', de: 'Zu lang, versuch es kürzer', it: 'Troppo lungo, prova più breve' },
  recordingQueued: { sl: 'Shranjeno: v vas pride, ko bo spet na spletu', en: 'Saved: it reaches the town when it is back online', de: 'Gespeichert: Es kommt ins Dorf, sobald es wieder online ist', it: 'Salvato: arriva al paese quando torna in linea' },
  recordingSaved: { sl: 'Shranjeno. {who} zdaj sliši tvoj glas!', en: 'Saved. {who} will hear your voice!', de: 'Gespeichert. {who} hört jetzt deine Stimme!', it: 'Salvato. {who} ora sente la tua voce!' },
  friendsWait: { sl: '{who} si želi prijateljev: vasi se povežejo šele, ko starš reče da.', en: '{who} would like to be friends: the towns link only once a parent says yes.', de: '{who} möchte Freunde finden: Die Dörfer verbinden sich erst, wenn ein Elternteil Ja sagt.', it: '{who} vorrebbe fare amicizia: i paesi si collegano solo quando un genitore dice di sì.' },
  friendsNone: { sl: 'Nič ne čaka nate. {who} lahko povabi prijatelje v aplikaciji; tukaj rečeš da ali ne.', en: 'Nothing waits for you. {who} can invite friends in the app; you say yes or no here.', de: 'Nichts wartet auf dich. {who} kann in der App Freunde einladen; hier sagst du Ja oder Nein.', it: "Niente ti aspetta. {who} può invitare amici nell'app; qui dici sì o no." },
  theyAccepted: { sl: 'Sprejeli so povabilo', en: "They accepted {who}'s invitation", de: 'Sie haben die Einladung von {who} angenommen', it: "Hanno accettato l'invito di {who}" },
  learnerAccepted: { sl: 'Njihovo povabilo je sprejeto', en: '{who} accepted their invitation', de: '{who} hat ihre Einladung angenommen', it: '{who} ha accettato il loro invito' },
  allow: { sl: 'Dovoli', en: 'Allow', de: 'Erlauben', it: 'Consenti' },
  decline: { sl: 'Ne', en: 'Decline', de: 'Ablehnen', it: 'Rifiuta' },
  linked: { sl: 'Povezano', en: 'Linked', de: 'Verbunden', it: 'Collegato' },
  notThisTime: { sl: 'Ne tokrat', en: 'Not this time', de: 'Diesmal nicht', it: 'Non questa volta' },
  noAnswer: { sl: 'Vas se ta hip ne oglasi', en: "The town doesn't answer right now", de: 'Das Dorf antwortet gerade nicht', it: 'Il paese ora non risponde' },
  parentsTown: { sl: 'To je vas starša (vidi napredek)', en: "This is a parent's town (sees the progress)", de: 'Das ist das Dorf eines Elternteils (sieht den Fortschritt)', it: 'È il paese di un genitore (vede i progressi)' },
}
// What a text field shows until something is typed: in the language it is written in
const SAMPLES = {
  question: { sl: 'Kaj si danes jedel za kosilo?', en: 'What did you have for lunch today?', de: 'Was hast du heute zu Mittag gegessen?', it: 'Che cosa hai mangiato oggi a pranzo?' },
  translate: { sl: 'Jutri gremo k nonotu na kosilo.', en: "Tomorrow we're going to Grandad's for lunch.", de: 'Morgen gehen wir zum Opa zum Mittagessen.', it: 'Domani andiamo a pranzo dal nonno.' },
  say: { sl: 'Dober tek!', en: 'Enjoy your meal!', de: 'Guten Appetit!', it: 'Buon appetito!' },
  // who records: the grandmother of each language's village
  speaker: { sl: '👵 Micka', en: '👵 Maggie', de: '👵 Resi', it: '👵 Rosa' },
  // who stands in when they gave no name
  name: { sl: 'Maja', en: 'Mary', de: 'Maria', it: 'Maria' },
}
// The two languages of the page. Before the bridge has said (a link without its key): the browser's language, else English.
let target = (navigator.language || 'en').slice(0, 2).toLowerCase() in STRINGS.family ? navigator.language.slice(0, 2).toLowerCase() : 'en'
let base = target

const learner = () => settings?.learner_name || ''
function inLang(lang, key, vars = {}) {
  const entry = STRINGS[key]
  const names = NAMES[lang]
  const all = {
    who: learner(),
    inTarget: names.in[target], inBase: names.in[base],
    nameTarget: names.name[target], nameBase: names.name[base],
    helpTarget: names.help[target],
    ...vars,
  }
  return (entry[lang] ?? entry.en).replace(/\{(\w+)\}/g, (_, k) => String(all[k] ?? ''))
}
/** "Vrsta · Type": the label in the language learned and in the base. */
const t = (key, vars) => {
  const a = inLang(target, key, vars)
  const b = inLang(base, key, vars)
  return a === b ? a : `${a} · ${b}`
}
/** Everything the page says that stays, in its two languages (again when they or the learner's name are known). */
function applyLanguage() {
  document.documentElement.lang = target
  document.title = `Lani · ${inLang(target, 'family')}`
  for (const e of document.querySelectorAll('[data-t]')) e.textContent = (e.dataset.prefix || '') + t(e.dataset.t)
  for (const e of document.querySelectorAll('[data-t-target]')) e.textContent = inLang(target, e.dataset.tTarget)
  for (const e of document.querySelectorAll('[data-t-base]')) e.textContent = inLang(base, e.dataset.tBase)
  for (const e of document.querySelectorAll('[data-t-aria]')) e.setAttribute('aria-label', t(e.dataset.tAria))
  for (const e of document.querySelectorAll('[data-t-placeholder]')) e.placeholder = t(e.dataset.tPlaceholder)
  $('title').textContent = `💌 Lani · ${inLang(target, 'family')}`
  $('subtitle').textContent = settings ? t('help') : ''
  $('from-field').hidden = target !== 'sl' // the Slovene "from" form: what the app says only in Slovene
  $('set-name').placeholder = SAMPLES.name[target]
  $('rec-as').placeholder = SAMPLES.speaker[target]
  $('c-hint').placeholder = SAMPLES.question[base]
  showType()
}

// The link carries the key as #t=… (a fragment never reaches the server or its logs); older links
// used ?t=…, which still works.
let token = new URLSearchParams(location.hash.slice(1)).get('t') || new URLSearchParams(location.search).get('t')
try {
  if (token) localStorage.setItem(KEY, token)
  else token = saved(KEY)
} catch {}
if (location.search || location.hash) history.replaceState(null, '', location.pathname) // keep the key out of screenshots and history

let words = []
let index = {}
let tab = 'missing'

// --- helpers -----------------------------------------------------------------------------
function el(tag, props = {}, ...children) {
  const e = document.createElement(tag)
  for (const [k, v] of Object.entries(props)) {
    if (k === 'class') e.className = v
    else if (k.startsWith('on')) e.addEventListener(k.slice(2), v)
    else e.setAttribute(k, v)
  }
  for (const c of children.flat()) if (c != null && c !== false) e.append(c)
  return e
}

function toast(msg) {
  const t = $('toast')
  t.textContent = msg
  t.hidden = false
  clearTimeout(toast.timer)
  toast.timer = setTimeout(() => (t.hidden = true), 3500)
}

async function api(path, opts = {}) {
  const r = await fetch(API + path, { ...opts, headers: { ...(opts.headers || {}), authorization: `Bearer ${token}` } })
  if (r.status === 401) {
    lock()
    throw new Error('unauthorized')
  }
  if (!r.ok) {
    const body = await r.json().catch(() => ({}))
    throw new Error(body.error || `HTTP ${r.status}`)
  }
  return r
}
const getJson = async path => (await api(path)).json()
const sendJson = (path, method, body) =>
  api(path, { method, headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) }).then(r => r.json())

function lock() {
  for (const id of ['settings', 'compose', 'thread-wrap', 'record', 'open-settings', 'towns']) $(id).hidden = true
  $('locked').hidden = false
}

/** "Maja" → "Maje", as the app does, for the preview only. */
function genitive(n) {
  n = n.trim()
  if (!n) return n
  if (/a$/i.test(n)) return n.slice(0, -1) + 'e'
  if (/o$/i.test(n)) return n.slice(0, -1) + 'a'
  if (/[eiu]$/i.test(n)) return n
  return n + 'a'
}

const when = iso => {
  const d = new Date(iso)
  const today = new Date().toDateString() === d.toDateString()
  return today ? d.toLocaleTimeString(LOCALES[target], { hour: '2-digit', minute: '2-digit' }) : d.toLocaleDateString(LOCALES[target], { day: 'numeric', month: 'numeric' })
}

/** The tutor writes a little Markdown: paragraphs and **bold**. Built as DOM, never as HTML. */
function markdown(text) {
  const box = el('div', { class: 'feedback' })
  for (const para of text.split(/\n{2,}/)) {
    const p = el('p')
    para.split('\n').forEach((line, i) => {
      if (i) p.append(el('br'))
      line = line.replace(/^\s*[-*]\s+/, '• ').replace(/^#+\s*/, '').replace(/`/g, '')
      line.split(/(\*\*[^*]+\*\*)/).forEach(part => {
        if (/^\*\*[^*]+\*\*$/.test(part)) p.append(el('b', {}, part.slice(2, -2)))
        else if (part) p.append(part.replace(/(^|\s)[*_]([^*_]+)[*_]/g, '$1$2'))
      })
    })
    box.append(p)
  }
  return box
}

// --- settings --------------------------------------------------------------------------------
let chosenEmoji = '💌'
function showSettings() {
  $('settings').hidden = false
  $('set-name').value = settings.partner_name || ''
  $('set-from').value = settings.from_sl || ''
  chosenEmoji = settings.partner_emoji || '💌'
  renderEmoji()
  preview()
}
function renderEmoji() {
  const row = $('set-emoji')
  row.replaceChildren(
    ...EMOJIS.map(e =>
      el('button', { type: 'button', role: 'radio', 'aria-checked': String(e === chosenEmoji), class: e === chosenEmoji ? 'on' : '', onclick: () => { chosenEmoji = e; renderEmoji(); preview() } }, e),
    ),
  )
}
/** What the learner's app will say: "💌 Od Maje · From Maja" (the Slovene "from" form only for a learner of Slovene). */
function preview() {
  const name = $('set-name').value.trim() || SAMPLES.name[target]
  if (target !== 'sl') return ($('set-preview').textContent = `${chosenEmoji} ${t('from', { from: name })}`)
  $('set-from').placeholder = genitive(name)
  $('set-preview').textContent = `${chosenEmoji} Od ${$('set-from').value.trim() || genitive(name)}`
}
$('set-name').addEventListener('input', preview)
$('set-from').addEventListener('input', preview)
$('open-settings').addEventListener('click', () => ($('settings').hidden ? showSettings() : ($('settings').hidden = true)))
$('settings-form').addEventListener('submit', async e => {
  e.preventDefault()
  try {
    const from = $('set-from').value.trim()
    settings = await sendJson('/family/api/settings', 'PUT', {
      partner_name: $('set-name').value.trim(),
      partner_emoji: chosenEmoji,
      ...(from ? { from_sl: from } : {}),
    })
    $('settings').hidden = true
    applySettings()
    toast(`✅ ${t('saved')}`)
    loadThread().catch(() => {})
    loadWords().catch(() => {})
  } catch (err) {
    toast(`⚠️ ${err.message}`)
  }
})

function applySettings() {
  $('title').textContent = settings.partner_name ? `${settings.partner_emoji} ${settings.partner_name} → ${learner()}` : `💌 Lani · ${inLang(target, 'family')}`
  if (!$('rec-as').value) $('rec-as').value = recAs()
  for (const id of ['compose', 'thread-wrap', 'record', 'open-settings']) $(id).hidden = false
}

function recAs() {
  try {
    const as = saved(REC_AS)
    if (as) return as
  } catch {}
  return settings.partner_name ? `${settings.partner_emoji} ${settings.partner_name}` : ''
}
$('rec-as').addEventListener('change', () => {
  try { localStorage.setItem(REC_AS, $('rec-as').value.trim()) } catch {}
})

// --- compose -----------------------------------------------------------------------------------
const TEXT_LABELS = { question: 'textQuestion', translate: 'textTranslate', say: 'textSay' }
/** The text field as the message's type asks it: its label, and a sample in the language it is written in. */
function showType() {
  const type = document.querySelector('input[name=type]:checked').value
  $('text-label').textContent = t(TEXT_LABELS[type])
  $('c-text').placeholder = SAMPLES[type][target]
}
document.querySelectorAll('input[name=type]').forEach(r => r.addEventListener('change', showType))
$('compose-form').addEventListener('submit', async e => {
  e.preventDefault()
  const btn = $('c-send')
  btn.disabled = true
  try {
    const hint = $('c-hint').value.trim()
    const sent = await sendJson('/family/api/challenges', 'POST', {
      type: document.querySelector('input[name=type]:checked').value,
      text: $('c-text').value.trim(),
      ...(hint ? { hint } : {}),
    })
    $('c-text').value = ''
    $('c-hint').value = ''
    // through the relay while the town is away: it waits there and reaches the town when it is back
    toast(sent.queued ? `📮 ${t('queued')}` : `💌 ${t('sent')}`)
    await loadThread()
  } catch (err) {
    toast(`⚠️ ${err.message}`)
  } finally {
    btn.disabled = false
  }
})

// --- thread ----------------------------------------------------------------------------------------
async function loadThread() {
  const list = await getJson('/family/api/challenges')
  const box = $('thread')
  const TYPES = { question: `❓ ${t('question')}`, translate: `🔁 ${t('translate')}`, say: `🗣️ ${t('say')}` }
  if (!list.length) {
    box.replaceChildren(el('p', { class: 'empty' }, t('threadEmpty')))
    return
  }
  box.replaceChildren(
    ...list.map(c =>
      el(
        'article',
        { class: 'card msg' },
        el('div', { class: 'meta' }, el('span', { class: 'badge' }, TYPES[c.type] || c.type), el('span', {}, when(c.created_at))),
        el('div', { class: 'text' }, c.text),
        c.hint && el('div', { class: 'hint' }, `💡 ${c.hint}`),
        c.answer
          ? el('div', { class: 'bubble jan' }, el('span', { class: 'who' }, `${learner()}${c.answer.spoken ? ' 🎤' : ''} · ${when(c.answer.at)}`), c.answer.text)
          : el('div', { class: 'waiting' }, `⏳ ${t('waiting')}`),
        c.answer && !c.feedback && el('div', { class: 'waiting' }, `🧑‍🏫 ${t('tutorLooking')}`),
        c.feedback &&
          el(
            'div',
            { class: 'bubble tutor' },
            typeof c.feedback.score === 'number' && el('span', { class: 'score' }, `${c.feedback.score}/10`),
            el('span', { class: 'who' }, `🧑‍🏫 ${t('tutor')}`),
            c.feedback.partner_note || '',
            el('details', {}, el('summary', {}, t('whatWasTold')), markdown(c.feedback.text)),
          ),
        el('div', { class: 'actions' }, el('button', { type: 'button', onclick: () => openRecorder(c.text) }, `⏺ ${t('recordThis')}`)),
      ),
    ),
  )
}

// --- words to record ----------------------------------------------------------------------------------
async function loadWords() {
  ;[words, index] = await Promise.all([getJson('/family/api/words'), getJson('/audio/index')])
  renderWords()
}

const norm = s => s.normalize('NFC').toLocaleLowerCase('sl').replace(/[^\p{L}\p{N}\s]/gu, '').replace(/\s+/g, ' ').trim()

function renderWords() {
  const recorded = Object.entries(index).map(([key, recs]) => ({ key, recs, text: recs[recs.length - 1].text }))
  const missing = words.filter(w => !index[norm(w.text)])
  $('tab-missing').textContent = `${t('toRecord')} (${missing.length})`
  $('tab-done').textContent = `${t('recorded')} (${recorded.length})`
  for (const b of document.querySelectorAll('.tabs button')) b.classList.toggle('on', b.dataset.tab === tab)
  const ul = $('words')
  if (tab === 'missing') {
    ul.replaceChildren(
      ...(missing.length
        ? missing.map(w =>
            el(
              'li',
              {},
              el('div', { class: 'w' }, el('div', { class: 'sl' }, w.text), el('div', { class: 'en' }, [w.en, w.source].filter(Boolean).join(' · '))),
              el('button', { class: 'round', type: 'button', 'aria-label': `${t('record')}: ${w.text}`, onclick: () => openRecorder(w.text) }, '⏺'),
            ),
          )
        : [el('li', { class: 'empty' }, `🎉 ${t('everythingRecorded')}`)]),
    )
  } else {
    ul.replaceChildren(
      ...(recorded.length
        ? recorded.map(r =>
            el(
              'li',
              {},
              el(
                'div',
                { class: 'w' },
                el('div', { class: 'sl' }, r.text),
                el(
                  'div',
                  { class: 'chips' },
                  r.recs.map(rec =>
                    el(
                      'span',
                      {},
                      el('button', { type: 'button', onclick: () => playFile(rec.file) }, `▶ ${rec.speaker}`),
                      el('button', { type: 'button', 'aria-label': t('remove'), onclick: () => removeFile(rec) }, '🗑'),
                    ),
                  ),
                ),
              ),
              el('button', { class: 'round', type: 'button', 'aria-label': `${t('record')}: ${r.text}`, onclick: () => openRecorder(r.text) }, '⏺'),
            ),
          )
        : [el('li', { class: 'empty' }, t('nothingRecorded'))]),
    )
  }
}
for (const b of document.querySelectorAll('.tabs button'))
  b.addEventListener('click', () => {
    tab = b.dataset.tab
    renderWords()
  })

let player = null
async function playFile(file) {
  try {
    const blob = await (await api(`/audio/file/${file}`)).blob()
    play(blob)
  } catch (err) {
    toast(`⚠️ ${err.message}`)
  }
}
function play(blob) {
  if (player) {
    player.pause()
    URL.revokeObjectURL(player.src)
  }
  player = new Audio(URL.createObjectURL(blob))
  player.play().catch(() => toast(`⚠️ ${t('couldNotPlay')}`))
}
async function removeFile(rec) {
  if (!confirm(`${t('removeAsk')}\n„${rec.text}“ (${rec.speaker})`)) return
  try {
    await api(`/audio/file/${rec.file}`, { method: 'DELETE' })
    await loadWords()
  } catch (err) {
    toast(`⚠️ ${err.message}`)
  }
}

$('custom-rec').addEventListener('click', () => {
  const t = $('custom-text').value.trim()
  if (t) openRecorder(t)
  else $('custom-text').focus()
})

// --- recorder ----------------------------------------------------------------------------------------
const MAX_SECONDS = 15
let recText = ''
let recorder = null
let take = null
let stopTimer = null

function pickType() {
  if (!window.MediaRecorder) return null
  for (const t of ['audio/webm;codecs=opus', 'audio/mp4', 'audio/ogg;codecs=opus', 'audio/webm']) if (MediaRecorder.isTypeSupported(t)) return t
  return ''
}

function openRecorder(text) {
  if (pickType() === null || !navigator.mediaDevices?.getUserMedia) {
    toast(`⚠️ ${t('cannotRecord')}`)
    return
  }
  recText = text
  take = null
  $('sheet-text').textContent = text
  $('sheet-status').textContent = t('tapAndSpeak')
  $('after-rec').hidden = true
  $('rec-btn').textContent = '⏺'
  $('rec-btn').classList.remove('live')
  $('sheet').hidden = false
}

function closeRecorder() {
  if (recorder && recorder.state === 'recording') recorder.stop()
  $('sheet').hidden = true
}
$('sheet-close').addEventListener('click', closeRecorder)
$('sheet').addEventListener('click', e => { if (e.target === $('sheet')) closeRecorder() })

$('rec-btn').addEventListener('click', async () => {
  if (recorder && recorder.state === 'recording') {
    recorder.stop()
    return
  }
  let stream
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true } })
  } catch {
    toast(`🎙️ ${t('allowMic')}`)
    return
  }
  const type = pickType()
  recorder = new MediaRecorder(stream, type ? { mimeType: type, audioBitsPerSecond: 64000 } : undefined)
  const chunks = []
  recorder.ondataavailable = e => e.data.size && chunks.push(e.data)
  recorder.onstop = () => {
    clearTimeout(stopTimer)
    stream.getTracks().forEach(t => t.stop())
    take = new Blob(chunks, { type: recorder.mimeType || type || 'audio/webm' })
    $('rec-btn').textContent = '⏺'
    $('rec-btn').classList.remove('live')
    $('sheet-status').textContent = t('listenThenSave')
    $('after-rec').hidden = false
  }
  recorder.start()
  stopTimer = setTimeout(() => recorder.state === 'recording' && recorder.stop(), MAX_SECONDS * 1000)
  $('rec-btn').textContent = '⏹'
  $('rec-btn').classList.add('live')
  $('after-rec').hidden = true
  $('sheet-status').textContent = t('recordingNow', { seconds: MAX_SECONDS })
})
$('rec-play').addEventListener('click', () => take && play(take))
$('rec-again').addEventListener('click', () => openRecorder(recText))
$('rec-save').addEventListener('click', async () => {
  if (!take) return
  if (take.size > 2 * 1024 * 1024) return toast(`⚠️ ${t('tooLong')}`)
  const btn = $('rec-save')
  btn.disabled = true
  try {
    const q = new URLSearchParams({ text: recText, speaker: $('rec-as').value.trim() || recAs() || inLang(target, 'family') })
    const r = await api(`/audio?${q}`, { method: 'POST', headers: { 'content-type': take.type || 'application/octet-stream' }, body: take })
    $('sheet').hidden = true
    toast(r.status === 202 ? `📮 ${t('recordingQueued')}` : `✅ ${t('recordingSaved')}`)
    await loadWords()
  } catch (err) {
    toast(`⚠️ ${err.message}`)
  } finally {
    btn.disabled = false
  }
})

// --- a child's friends: the links that wait for the parent ----------------------------------------------------------
// A child's app can invite and accept, but its town links nothing until a parent says yes: here (the family's key), in
// the parent's own app, or on the node. "👪 This is a parent's town": that town becomes the child's guardian (it sees
// the child's progress in detail).
const FLAGS = { sl: '🇸🇮', it: '🇮🇹', de: '🇩🇪', en: '🇬🇧', hr: '🇭🇷', fr: '🇫🇷', es: '🇪🇸' }
const AGES = { OGENJ: '🔥', TABOR: '⛺', ZASELEK: '🛖', VAS: '🏘️', TRG: '🏛️', MESTO: '🏰' }
const BUILDINGS = { TENT: '⛺', FIELD: '🌾', WELL: '🪣', HUT: '🛖', KOZOLEC: '🪜', BEEHIVE: '🐝', PALISADE: '🪵', WATCHTOWER: '🗼', SMITHY: '⚒️', LIPA: '🌳', HOUSE: '🏠', CHURCH: '⛪', SCHOOL: '🏫', MARKET: '🏪' }
const flag = l => FLAGS[l] || (l || '').toUpperCase()

/** "🏘️ · 👥 3 · 🛖×2 🌾": how a town looks now, from its preview; '' when it didn't answer. */
function previewLine(p) {
  if (!p) return ''
  const parts = Object.entries(p.buildings || {}).slice(0, 6).map(([t, n]) => (BUILDINGS[t] || '🏠') + (n > 1 ? `×${n}` : ''))
  return [p.age ? AGES[p.age] || '' : '', p.villagers ? `👥 ${p.villagers}` : '', parts.join(' ')].filter(Boolean).join(' · ')
}

async function loadTowns() {
  let towns
  try {
    towns = await getJson('/family/api/towns')
  } catch (err) {
    // an older tutor has no such route: no card
    return
  }
  if (!towns.child) return ($('towns').hidden = true)
  const child = { who: towns.learner || learner() }
  $('towns').hidden = false
  $('towns-intro').textContent = t(towns.requests.length ? 'friendsWait' : 'friendsNone', child)
  const list = $('towns-requests')
  list.replaceChildren(...towns.requests.map(r => {
    const how = `📨 ${t(r.via === 'invite' ? 'theyAccepted' : 'learnerAccepted', child)}`
    const guardian = el('input', { type: 'checkbox' })
    const allow = el('button', { class: 'primary', type: 'button' }, `✓ ${t('allow')}`)
    const no = el('button', { type: 'button' }, `✕ ${t('decline')}`)
    const decide = async approve => {
      allow.disabled = no.disabled = true
      try {
        const res = await sendJson(`/family/api/towns/requests/${r.id}`, 'POST', { approve, guardian: approve && guardian.checked, town: r.town.id })
        toast(approve ? `🤝 ${t('linked')}: ${r.town.name}${res.guardian ? ' (👪)' : ''}` : `👋 ${t('notThisTime')}: ${r.town.name}`)
      } catch (err) {
        toast(`⚠️ ${err.message}`)
      }
      await loadTowns()
    }
    allow.addEventListener('click', () => decide(true))
    no.addEventListener('click', () => decide(false))
    return el('li', { class: 'request' },
      el('div', { class: 'req-title' }, `${flag(r.town.language)} ${r.town.name} · ${r.town.learner}`),
      el('div', { class: 'small' }, previewLine(r.preview) || `📡 ${t('noAnswer')}`),
      el('div', { class: 'small' }, `${how} · ${when(r.at)}`),
      el('label', { class: 'check' }, guardian, ` 👪 ${t('parentsTown')}`),
      el('div', { class: 'req-actions' }, no, allow),
    )
  }))
  $('towns-linked-wrap').hidden = !towns.towns.length
  $('towns-linked').replaceChildren(...towns.towns.map(l => el('li', {}, `${flag(l.language)} ${l.name} · ${l.learner}${l.guardian ? ' 👪' : ''}`)))
}

// --- start ---------------------------------------------------------------------------------------------------
async function start() {
  applyLanguage()
  if (!token) return lock()
  try {
    settings = await getJson('/family/api/settings')
  } catch (err) {
    if (err.message !== 'unauthorized') toast(`⚠️ ${err.message}`)
    return
  }
  // the learner's two languages; a bridge from before they were said here: Slovene and English
  target = NAMES[settings.language] ? settings.language : settings.language ? 'en' : 'sl'
  base = NAMES[settings.base] && settings.base !== target ? settings.base : target === 'en' ? 'sl' : 'en'
  applyLanguage()
  if (!settings.partner_name) showSettings()
  else applySettings()
  if (settings.partner_name) {
    loadThread().catch(err => toast(`⚠️ ${err.message}`))
    loadWords().catch(err => toast(`⚠️ ${err.message}`))
  }
  // a child's links that wait for the parent: even before the name is set (that is what the parent came for)
  loadTowns()
  setInterval(() => {
    if (document.visibilityState === 'visible' && settings?.partner_name) loadThread().catch(() => {})
    if (document.visibilityState === 'visible') loadTowns()
  }, 20000)
}
start()
