// The family page without a node (companion/README.md, "Family"): the page itself is static and served here at
// /family/<town id>/; its API (/v1/family/<town id>/…) goes to the town's bridge through its mailbox, for a family
// token the town registered (its SHA-256 only). What the token may do is what it may do on a node: the settings, the
// words to record, the challenges, the recordings. A challenge or a recording sent while the town is away waits in
// its mailbox (14 days) and reaches the town when it is back.
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

export type FamilyRoute = { method: string; path: RegExp; max: 'json' | 'audio' | 'none'; queue?: true }

/** What the family page calls on the town, relative to /v1/family/<town id>/. */
export const FAMILY_ROUTES: FamilyRoute[] = [
  { method: 'GET', path: /^family\/api\/settings$/, max: 'none' },
  { method: 'PUT', path: /^family\/api\/settings$/, max: 'json' },
  { method: 'GET', path: /^family\/api\/words$/, max: 'none' },
  { method: 'GET', path: /^family\/api\/challenges$/, max: 'none' },
  { method: 'POST', path: /^family\/api\/challenges$/, max: 'json', queue: true },
  { method: 'POST', path: /^audio$/, max: 'audio', queue: true },
  { method: 'GET', path: /^audio\/index$/, max: 'none' },
  { method: 'GET', path: /^audio\/file\/[a-f0-9]{24}\.(m4a|ogg|webm)$/, max: 'none' },
  { method: 'DELETE', path: /^audio\/file\/[a-f0-9]{24}\.(m4a|ogg|webm)$/, max: 'none' },
]

export const familyRoute = (method: string, rest: string) => FAMILY_ROUTES.find(r => r.method === method && r.path.test(rest))

/** As the bridge sends the page (features/family.ts). */
const CSP = "default-src 'self'; media-src 'self' blob:; img-src 'self' data:; style-src 'self'; script-src 'self'; frame-ancestors 'none'"

const FILES: Record<string, [string, string]> = {
  '': ['index.html', 'text/html; charset=utf-8'],
  '/': ['index.html', 'text/html; charset=utf-8'],
  '/app.js': ['app.js', 'text/javascript; charset=utf-8'],
  '/style.css': ['style.css', 'text/css; charset=utf-8'],
}

/** The page's files from [webDir], read at the first request: the page served for town [id] loads its script and style from under its own path. */
export function familyPage(webDir: string) {
  const cache = new Map<string, string>()
  const file = (name: string) => {
    let text = cache.get(name)
    if (text === undefined) cache.set(name, (text = readFileSync(join(webDir, name), 'utf8')))
    return text
  }
  return (id: string, which: string): Response | undefined => {
    const f = FILES[which]
    if (!f) return undefined
    let body = file(f[0])
    if (f[0] === 'index.html') body = body.replaceAll('"/family/style.css"', `"/family/${id}/style.css"`).replaceAll('"/family/app.js"', `"/family/${id}/app.js"`)
    return new Response(body, {
      headers: {
        'content-type': f[1],
        'content-security-policy': CSP,
        'referrer-policy': 'no-referrer',
        'cache-control': 'no-cache',
        'x-content-type-options': 'nosniff',
        'x-lani-relay': 'ok',
      },
    })
  }
}
