// The page of a town's invitation (site/town/index.html): reads the invitation from the fragment (#v=1&u=…&c=…&f=…[&r=…],
// as the bridge's inviteWebUri writes it), and offers it to the app: on Android an intent:// link (it opens Lani, or the
// install page when Lani isn't there), elsewhere the lani://town?… text to paste. Nothing is sent anywhere.
'use strict'

const $ = id => document.getElementById(id)
const raw = location.hash.length > 1 ? location.hash.slice(1) : location.search.slice(1)
const q = new URLSearchParams(raw)
const FP = /^[0-9a-f]{32}$/
const CODE = /^[A-Z0-9]{6,32}$/
const v = Number(q.get('v'))
const code = (q.get('c') || '').toUpperCase().replace(/[\s-]/g, '')
const fp = (q.get('f') || '').toLowerCase()
let url = ''
try {
  const u = new URL(q.get('u') || '')
  if ((u.protocol === 'http:' || u.protocol === 'https:') && !u.username && !u.password && !u.search && !u.hash) url = u.toString().replace(/\/+$/, '')
} catch {}

// The second half of each label in the browser's language (the first is Slovene, as everywhere in Lani)
const T = {
  de: {
    title: 'Eine Einladung in ein Dorf', intro: 'Ein Freund lädt dich ein, eure Dörfer in Lani zu verbinden.', open: 'In Lani öffnen',
    once: 'Sie gilt einmal, 30 Minuten lang.', none: 'Noch kein Lani?', install: 'Lani installieren (Android)',
    back: 'Komm dann hierher zurück und tippe auf „In Lani öffnen“.', inapp: 'Oder in der App', paste: '🤝 Freunde → 🔗 Annehmen, dann einfügen:',
    copy: 'Kopieren', bad: 'Diese Einladung ist unvollständig: bitte um eine neue.',
  },
  it: {
    title: 'Un invito in un villaggio', intro: 'Un amico ti invita a collegare i vostri villaggi in Lani.', open: 'Apri in Lani',
    once: 'Vale una volta, per 30 minuti.', none: 'Non hai ancora Lani?', install: 'Installa Lani (Android)',
    back: 'Poi torna qui e tocca «Apri in Lani».', inapp: "Oppure nell'app", paste: '🤝 Amici → 🔗 Accetta, poi incolla:',
    copy: 'Copia', bad: 'Questo invito è incompleto: chiedine uno nuovo.',
  },
}
const lang = (navigator.language || 'en').slice(0, 2)
for (const [k, text] of Object.entries(T[lang] || {})) {
  const e = $(`t-${k}`)
  if (e) e.textContent = text
}
if (lang === 'sl') for (const e of document.querySelectorAll('i[id^="t-"], span[id^="t-"]')) e.remove()

if (v >= 1 && url && CODE.test(code) && FP.test(fp)) {
  // the invitation for the app, as the bridge writes it (lani://town?…); r= only when it is this town's relay address
  const relay = q.get('r') || ''
  const params = `v=${v}&u=${encodeURIComponent(url)}&c=${code}&f=${fp}${new RegExp(`/v1/towns/${fp}$`).test(relay) ? `&r=${encodeURIComponent(relay)}` : ''}`
  const app = `lani://town?${params}`
  const android = /Android/i.test(navigator.userAgent)
  const install = 'https://github.com/fentas/lani/releases/latest'
  $('open').href = android ? `intent://town?${params}#Intent;scheme=lani;package=si.lanisce.lani;S.browser_fallback_url=${encodeURIComponent(install)};end` : app
  $('code').textContent = app
  $('copy').addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(app)
      $('copied').hidden = false
    } catch {
      getSelection().selectAllChildren($('code'))
    }
  })
  $('ok').hidden = false
} else {
  $('bad').hidden = false
}
