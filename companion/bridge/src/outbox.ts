// Idempotent writes. The app's offline outbox retries a write when it can't tell whether it arrived.
// It sends a `client_id`; a repeat gets the first answer back instead of being applied twice.
import { readFileSync, renameSync, writeFileSync } from 'node:fs'
import type { Log } from './config'
import { json, MAX_JSON, readText } from './http'

type WriteResult = { status: number; body: unknown }

export class Outbox {
  private readonly done = new Map<string, WriteResult & { at: number }>()
  private readonly running = new Map<string, Promise<WriteResult>>()

  constructor(
    private readonly path: string,
    private readonly log: Log,
  ) {
    try {
      const weekAgo = Date.now() - 7 * 86_400_000
      for (const [k, v] of JSON.parse(readFileSync(path, 'utf8'))) if (v.at > weekAgo) this.done.set(k, v)
    } catch {}
  }

  private remember(key: string, r: WriteResult) {
    this.done.set(key, { ...r, at: Date.now() })
    while (this.done.size > 500) this.done.delete(this.done.keys().next().value!)
    try {
      writeFileSync(`${this.path}.tmp`, JSON.stringify([...this.done]))
      renameSync(`${this.path}.tmp`, this.path)
    } catch (e) {
      this.log(`client ids not saved: ${(e as Error).message}`)
    }
  }

  /** Runs [handle] once per (route, client_id); a request without a valid client_id always runs. */
  async once(req: Request, route: string, handle: (req: Request) => Promise<Response>, max = MAX_JSON): Promise<Response> {
    const raw = await readText(req, max)
    const fresh = () => new Request(req.url, { method: req.method, headers: req.headers, body: raw })
    let id: unknown
    try {
      id = JSON.parse(raw)?.client_id
    } catch {}
    if (typeof id !== 'string' || !/^[\w-]{8,64}$/.test(id)) return handle(fresh())
    const key = `${route} ${id}`
    const done = this.done.get(key)
    if (done) return Response.json(done.body, { status: done.status, headers: { 'x-lani-replay': '1' } })
    let running = this.running.get(key)
    if (!running) {
      running = (async () => {
        const res = await handle(fresh())
        const r = { status: res.status, body: await res.json().catch(() => null) }
        if (res.ok) this.remember(key, r)
        return r
      })().finally(() => this.running.delete(key))
      this.running.set(key, running)
    }
    const r = await running
    return json(r.body, r.status)
  }
}
