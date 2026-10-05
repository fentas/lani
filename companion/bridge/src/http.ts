// HTTP plumbing shared by every feature: route table, roles and tokens, body limits, JSON helpers,
// the server.
import { createHash, randomBytes, timingSafeEqual } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { mentionsLearner, renderDeep, type Addressee } from './addressee'
import type { Log } from './config'

/** Who is asking: the app (Jan's token), the family page (the family token), or nobody. */
export type Role = 'app' | 'family' | null

/** Who may call a route: anyone, either token, or only the app token (the default). */
export type Access = 'public' | 'any' | 'app'

export type RouteCtx = { req: Request; url: URL; role: Role; params: string[] }

export type Route = {
  /** 'GET', 'POST', … or '*' for any method. */
  method: string
  /** An exact path, or a RegExp whose groups become `params`. */
  path: string | RegExp
  access?: Access
  /**
   * The answer is content that speaks to the learner (scenes, villagers, packs …): its JSON is rendered for them
   * ([addressed], addressee.ts) before it goes out.
   */
  addressed?: boolean
  handle: (c: RouteCtx) => Response | Promise<Response>
}

/**
 * [routes] with the answers of those [Route.addressed] rendered for the learner [who] says ({learner}, {m:…|f:…}:
 * addressee.ts): a JSON answer that went well, anything else as it is.
 */
export function addressed(routes: Route[], who: () => Addressee): Route[] {
  return routes.map(r =>
    r.addressed
      ? {
          ...r,
          handle: async c => {
            const res = await r.handle(c)
            if (res.status !== 200 || !(res.headers.get('content-type') ?? '').includes('json')) return res
            const text = await res.text()
            const headers = new Headers(res.headers)
            headers.delete('content-length')
            const body = mentionsLearner(text) ? JSON.stringify(renderDeep(JSON.parse(text), who())) : text
            return new Response(body, { status: res.status, headers })
          },
        }
      : r,
  )
}

/** The largest body the server reads at all. A route that needs more must raise this. */
export const MAX_BODY = 10 * 1024 * 1024
/** The default limit for a JSON body. */
export const MAX_JSON = 256 * 1024

export const json = (body: unknown, status = 200) => Response.json(body, { status })

/** Thrown by handlers for a plain error answer (status + message); the router turns it into JSON. */
export class HttpError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message)
  }
}

/**
 * The request body, at most [max] bytes (413 beyond). Reads the stream itself, so a chunked body
 * without a content-length can't grow past the limit either.
 */
export async function readBody(req: Request, max: number, tooLarge = `body too large (max ${max} bytes)`): Promise<Uint8Array<ArrayBuffer>> {
  if (Number(req.headers.get('content-length') ?? 0) > max) throw new HttpError(413, tooLarge)
  if (!req.body) return new Uint8Array()
  const chunks: Uint8Array[] = []
  let size = 0
  const reader = req.body.getReader()
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    size += value.byteLength
    if (size > max) {
      await reader.cancel().catch(() => {})
      throw new HttpError(413, tooLarge)
    }
    chunks.push(value)
  }
  const out = new Uint8Array(size)
  let at = 0
  for (const c of chunks) {
    out.set(c, at)
    at += c.byteLength
  }
  return out
}

export async function readText(req: Request, max = MAX_JSON, tooLarge?: string): Promise<string> {
  return new TextDecoder().decode(await readBody(req, max, tooLarge))
}

/** The request's JSON body (at most [max] bytes, else 413), or null when it isn't JSON. */
export async function readJson(req: Request, max = MAX_JSON): Promise<unknown> {
  const text = await readText(req, max)
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

/**
 * A secret from the environment, else from [path]. Generated there (mode 600) on first use, and
 * again if the file is empty: an empty secret would match a request without a token.
 */
export function loadToken(path: string, fromEnv: string | undefined, log: Log, label: string): Buffer {
  if (fromEnv?.trim()) return Buffer.from(fromEnv.trim())
  const saved = existsSync(path) ? readFileSync(path, 'utf8').trim() : ''
  if (saved) return Buffer.from(saved)
  writeFileSync(path, randomBytes(24).toString('base64url') + '\n', { mode: 0o600 })
  log(`generated ${label} token at ${path}`)
  return Buffer.from(readFileSync(path, 'utf8').trim())
}

const digest = (b: Buffer | string) => createHash('sha256').update(b).digest()

/**
 * Whether a request carries `Authorization: Bearer <secret>`. Compares SHA-256 digests in constant
 * time, so neither the content nor the length of the secret leaks; an empty token never matches.
 */
export function bearer(secret: Buffer): (req: Request) => boolean {
  const want = digest(secret)
  return req => {
    const got = (req.headers.get('authorization') ?? '').replace(/^Bearer /, '')
    return secret.length > 0 && got.length > 0 && timingSafeEqual(digest(got), want)
  }
}

/** The family's own area: an unknown path there is a 404 for the family token, elsewhere a 403. */
const familyArea = (p: string) => p.startsWith('/family') || p.startsWith('/audio')

function secured(res: Response): Response {
  try {
    res.headers.set('x-content-type-options', 'nosniff')
  } catch {} // immutable headers: leave them
  return res
}

/** The server's fetch handler: finds the route, checks who may call it, runs it. */
export function router(routes: Route[], roleOf: (req: Request) => Role, log: Log) {
  async function dispatch(req: Request): Promise<Response> {
    const url = new URL(req.url)
    let params: string[] = []
    const route = routes.find(r => {
      if (r.method !== '*' && r.method !== req.method) return false
      if (typeof r.path === 'string') return r.path === url.pathname
      const m = r.path.exec(url.pathname)
      if (m) params = m.slice(1)
      return !!m
    })
    const access = route?.access ?? 'app'
    if (route && access === 'public') return route.handle({ req, url, role: null, params })
    const role = roleOf(req)
    if (!role) return json({ error: 'unauthorized' }, 401)
    if (!route) return role === 'family' && !familyArea(url.pathname) ? json({ error: 'forbidden' }, 403) : json({ error: 'not found' }, 404)
    if (access === 'app' && role !== 'app') return json({ error: 'forbidden' }, 403)
    return route.handle({ req, url, role, params })
  }
  return async (req: Request): Promise<Response> => {
    try {
      return secured(await dispatch(req))
    } catch (e) {
      if (e instanceof HttpError) return secured(json({ error: e.message }, e.status))
      return secured(internalError(e, log))
    }
  }
}

/** A 500 that tells the client nothing about the code; the details go to the log. */
function internalError(e: unknown, log: Log): Response {
  log(`internal error: ${(e as Error)?.stack ?? e}`)
  return json({ error: 'internal error' }, 500)
}

/** Starts the HTTP server; undefined (logged) when the port is taken. */
export function serve(o: { host: string; port: number; fetch: (req: Request) => Promise<Response>; log: Log }): Bun.Server<undefined> | undefined {
  try {
    return Bun.serve({
      hostname: o.host,
      port: o.port,
      // Never share the port: a second bridge (every Claude Code session in this repo starts one from .mcp.json)
      // must fail to bind and keep only its MCP tools, not take half of the app's requests.
      reusePort: false,
      idleTimeout: 0, // SSE streams stay open; they carry a ping every 25 s
      development: false, // never Bun's error page (it shows stack and source)
      maxRequestBodySize: MAX_BODY,
      fetch: o.fetch,
      error: e => internalError(e, o.log),
    })
  } catch (e) {
    o.log(`HTTP disabled: ${(e as Error).message}`)
    return undefined
  }
}
