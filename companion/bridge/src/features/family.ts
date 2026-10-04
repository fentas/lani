// Family: recorded voices (/audio) and partner challenges (/family), see ../family.ts. The page itself
// is public (it holds no data); its API and the audio routes take either token, answers only the app's.
import { resolve } from 'node:path'
import { exampleOf, meaningOf, titleFor, wordOf } from '../packs'
import type { Ctx, FeatureFactory } from '../feature'
import { familyInstructions } from '../family'
import { json, type Route } from '../http'

const STATIC: Record<string, [string, string]> = {
  '/family': ['index.html', 'text/html; charset=utf-8'],
  '/family/': ['index.html', 'text/html; charset=utf-8'],
  '/family/app.js': ['app.js', 'text/javascript; charset=utf-8'],
  '/family/style.css': ['style.css', 'text/css; charset=utf-8'],
}
const CSP = "default-src 'self'; media-src 'self' blob:; img-src 'self' data:; style-src 'self'; script-src 'self'; frame-ancestors 'none'"

export const family: FeatureFactory = ({ cfg, family }) => ({
  instructions: familyInstructions,
  routes: [
    ...Object.entries(STATIC).map(
      ([path, [file, type]]): Route => ({
        method: 'GET',
        path,
        access: 'public',
        handle: () =>
          new Response(Bun.file(resolve(cfg.webDir, file)), {
            headers: { 'content-type': type, 'content-security-policy': CSP, 'referrer-policy': 'no-referrer', 'cache-control': 'no-cache' },
          }),
      }),
    ),
    { method: 'POST', path: '/audio', access: 'any', handle: ({ req, url }) => family.upload(req, url) },
    { method: 'GET', path: '/audio/index', access: 'any', handle: () => json(family.index()) },
    { method: 'GET', path: /^\/audio\/file\/([^/]+)$/, access: 'any', handle: ({ params: [file] }) => family.serveFile(file) },
    { method: 'DELETE', path: /^\/audio\/file\/([^/]+)$/, access: 'any', handle: ({ params: [file] }) => family.remove(file) },
    { method: 'GET', path: '/family/api/settings', access: 'any', handle: () => json(family.settings()) },
    { method: 'PUT', path: '/family/api/settings', access: 'any', handle: ({ req }) => family.saveSettings(req) },
    { method: 'GET', path: '/family/api/words', access: 'any', handle: () => json(family.wordList()) },
    { method: 'GET', path: '/family/api/challenges', access: 'any', handle: () => json(family.challenges().slice().reverse()) },
    { method: 'POST', path: '/family/api/challenges', access: 'any', handle: ({ req }) => family.createChallenge(req) },
    // Jan's answer: the app token only.
    { method: 'POST', path: /^\/family\/api\/challenges\/(\w+)\/answer$/, handle: ({ req, params: [id] }) => family.answer(req, id) },
  ],
})

/** What there is to record: pack words and examples, and the vocabulary review cards. */
export function familyWords({ packs, learner }: Ctx) {
  return [
    // in each pack's language (Slovene for Jan's; a pack in another language says so), glossed in English (an English
    // pack's words in another of its languages: their "en" is the word itself)
    ...packs.all().flatMap(p => [
      ...p.words.map(w => ({ text: wordOf(p, w), en: meaningOf(p, w), source: `${p.emoji} ${titleFor(p, p.language, 'en')}` })),
      ...p.words.flatMap(w => {
        const ex = exampleOf(p, w)
        return ex ? [{ text: ex, en: w.example_en, source: `${p.emoji} ${titleFor(p, p.language, 'en')}` }] : []
      }),
    ]),
    ...Object.values(learner.srItems())
      .filter(i => i.type === 'vocabulary' && i.content)
      .map(i => ({ text: i.content!, en: i.answer, source: '🔄 Kartice · Cards' })),
  ]
}
