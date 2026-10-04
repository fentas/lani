// Lani family page: send Jan challenges, see Jan's answers, record words. No build step.
'use strict'

const $ = id => document.getElementById(id)
const KEY = 'lani-family-token'
// what this browser saved before the project was renamed from Fluent: still read
const LEGACY = { 'lani-family-token': 'fluent-family-token', 'lani-family-rec-as': 'fluent-family-rec-as' }
const saved = key => localStorage.getItem(key) ?? localStorage.getItem(LEGACY[key])
const EMOJIS = ['💌', '👩', '👨', '👵', '👴', '🧑', '👧', '👦', '❤️', '🌻']
const TYPES = {
  question: '❓ Vprašanje · Question',
  translate: '🔁 Prevedi · Translate',
  say: '🗣️ Povej · Say this',
}

// The link carries the key as #t=… (a fragment never reaches the server or its logs); older links
// used ?t=…, which still works.
let token = new URLSearchParams(location.hash.slice(1)).get('t') || new URLSearchParams(location.search).get('t')
try {
  if (token) localStorage.setItem(KEY, token)
  else token = saved(KEY)
} catch {}
if (location.search || location.hash) history.replaceState(null, '', location.pathname) // keep the key out of screenshots and history

let settings = null
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
  const r = await fetch(path, { ...opts, headers: { ...(opts.headers || {}), authorization: `Bearer ${token}` } })
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
  for (const id of ['settings', 'compose', 'thread-wrap', 'record', 'open-settings']) $(id).hidden = true
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
  return today ? d.toLocaleTimeString('sl-SI', { hour: '2-digit', minute: '2-digit' }) : d.toLocaleDateString('sl-SI', { day: 'numeric', month: 'numeric' })
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
function preview() {
  const name = $('set-name').value.trim() || 'Maja'
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
    toast('✅ Shranjeno · Saved')
    loadThread().catch(() => {})
    loadWords().catch(() => {})
  } catch (err) {
    toast(`⚠️ ${err.message}`)
  }
})

function applySettings() {
  const jan = settings.learner_name || 'Jan'
  $('title').textContent = settings.partner_name ? `${settings.partner_emoji} ${settings.partner_name} → ${jan}` : '💌 Lani · Družina'
  $('subtitle').textContent = `Pomagaj pri slovenščini · Help ${jan} learn Slovene`
  $('c-send').textContent = `Pošlji · Send to ${jan}`
  if (!$('rec-as').value) $('rec-as').value = recAs()
  for (const id of ['compose', 'thread-wrap', 'record', 'open-settings']) $(id).hidden = false
}

function recAs() {
  try {
    const as = saved('lani-family-rec-as')
    if (as) return as
  } catch {}
  return settings.partner_name ? `${settings.partner_emoji} ${settings.partner_name}` : ''
}
$('rec-as').addEventListener('change', () => {
  try { localStorage.setItem('lani-family-rec-as', $('rec-as').value.trim()) } catch {}
})

// --- compose -----------------------------------------------------------------------------------
const TEXT_LABELS = {
  question: ['Vprašanje v slovenščini · Your question in Slovene', 'Kaj si danes jedel za kosilo?'],
  translate: ['Stavek za prevod (slovensko ali angleško) · Sentence to translate (Slovene or English)', 'Jutri gremo k nonotu na kosilo.'],
  say: ['Kaj naj Jan reče? · What should Jan say?', 'Dober tek!'],
}
document.querySelectorAll('input[name=type]').forEach(r =>
  r.addEventListener('change', () => {
    const [label, ph] = TEXT_LABELS[r.value]
    $('text-label').textContent = label
    $('c-text').placeholder = ph
  }),
)
$('compose-form').addEventListener('submit', async e => {
  e.preventDefault()
  const btn = $('c-send')
  btn.disabled = true
  try {
    const hint = $('c-hint').value.trim()
    await sendJson('/family/api/challenges', 'POST', {
      type: document.querySelector('input[name=type]:checked').value,
      text: $('c-text').value.trim(),
      ...(hint ? { hint } : {}),
    })
    $('c-text').value = ''
    $('c-hint').value = ''
    toast('💌 Poslano · Sent!')
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
  const jan = settings?.learner_name || 'Jan'
  const box = $('thread')
  if (!list.length) {
    box.replaceChildren(el('p', { class: 'empty' }, `Še nič. Pošlji prvo sporočilo! · Nothing yet. Send ${jan} a first message!`))
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
          ? el('div', { class: 'bubble jan' }, el('span', { class: 'who' }, `${jan}${c.answer.spoken ? ' 🎤' : ''} · ${when(c.answer.at)}`), c.answer.text)
          : el('div', { class: 'waiting' }, `⏳ Čaka na odgovor · Waiting for ${jan}`),
        c.answer && !c.feedback && el('div', { class: 'waiting' }, '🧑‍🏫 Učitelj pregleduje · The tutor is looking at it…'),
        c.feedback &&
          el(
            'div',
            { class: 'bubble tutor' },
            typeof c.feedback.score === 'number' && el('span', { class: 'score' }, `${c.feedback.score}/10`),
            el('span', { class: 'who' }, '🧑‍🏫 Učitelj · Tutor'),
            c.feedback.partner_note || '',
            el('details', {}, el('summary', {}, `Kaj je rekel ${jan}u · What ${jan} was told`), markdown(c.feedback.text)),
          ),
        el('div', { class: 'actions' }, el('button', { type: 'button', onclick: () => openRecorder(c.text) }, '⏺ Posnemi to · Record this')),
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
  $('tab-missing').textContent = `Manjka · To record (${missing.length})`
  $('tab-done').textContent = `Posneto · Recorded (${recorded.length})`
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
              el('button', { class: 'round', type: 'button', 'aria-label': `Posnemi · Record ${w.text}`, onclick: () => openRecorder(w.text) }, '⏺'),
            ),
          )
        : [el('li', { class: 'empty' }, '🎉 Vse je posneto · Everything is recorded!')]),
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
                      el('button', { type: 'button', 'aria-label': 'Izbriši · Delete', onclick: () => removeFile(rec) }, '🗑'),
                    ),
                  ),
                ),
              ),
              el('button', { class: 'round', type: 'button', 'aria-label': `Posnemi · Record ${r.text}`, onclick: () => openRecorder(r.text) }, '⏺'),
            ),
          )
        : [el('li', { class: 'empty' }, 'Še nič posnetega · Nothing recorded yet.')]),
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
  player.play().catch(() => toast('⚠️ Predvajanje ni uspelo · Could not play'))
}
async function removeFile(rec) {
  if (!confirm(`Izbrišem posnetek „${rec.text}“ (${rec.speaker})? · Delete this recording?`)) return
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
    toast('⚠️ Ta brskalnik ne more snemati · This browser cannot record')
    return
  }
  recText = text
  take = null
  $('sheet-text').textContent = text
  $('sheet-status').textContent = 'Tapni ⏺ in govori · Tap ⏺ and speak'
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
    toast('🎙️ Dovoli mikrofon za to stran · Allow the microphone for this page')
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
    $('sheet-status').textContent = 'Poslušaj in shrani · Listen, then save'
    $('after-rec').hidden = false
  }
  recorder.start()
  stopTimer = setTimeout(() => recorder.state === 'recording' && recorder.stop(), MAX_SECONDS * 1000)
  $('rec-btn').textContent = '⏹'
  $('rec-btn').classList.add('live')
  $('after-rec').hidden = true
  $('sheet-status').textContent = `Snemam… tapni ⏹ · Recording… tap ⏹ (max ${MAX_SECONDS} s)`
})
$('rec-play').addEventListener('click', () => take && play(take))
$('rec-again').addEventListener('click', () => openRecorder(recText))
$('rec-save').addEventListener('click', async () => {
  if (!take) return
  if (take.size > 2 * 1024 * 1024) return toast('⚠️ Predolgo · Too long, try a shorter take')
  const btn = $('rec-save')
  btn.disabled = true
  try {
    const q = new URLSearchParams({ text: recText, speaker: $('rec-as').value.trim() || recAs() || 'Družina' })
    await api(`/audio?${q}`, { method: 'POST', headers: { 'content-type': take.type || 'application/octet-stream' }, body: take })
    $('sheet').hidden = true
    toast('✅ Shranjeno · Saved. Jan bo slišal tvoj glas · Jan will hear your voice!')
    await loadWords()
  } catch (err) {
    toast(`⚠️ ${err.message}`)
  } finally {
    btn.disabled = false
  }
})

// --- start ---------------------------------------------------------------------------------------------------
async function start() {
  if (!token) return lock()
  try {
    settings = await getJson('/family/api/settings')
  } catch (err) {
    if (err.message !== 'unauthorized') toast(`⚠️ ${err.message}`)
    return
  }
  if (!settings.partner_name) showSettings()
  else applySettings()
  if (settings.partner_name) {
    loadThread().catch(err => toast(`⚠️ ${err.message}`))
    loadWords().catch(err => toast(`⚠️ ${err.message}`))
  }
  setInterval(() => {
    if (document.visibilityState === 'visible' && settings?.partner_name) loadThread().catch(() => {})
  }, 20000)
}
start()
