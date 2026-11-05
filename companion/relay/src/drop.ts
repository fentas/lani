// A family's drop boxes (docs/plans/05-built/leftovers-app.md, "The relay"): two of a family's devices meet here when they
// aren't on one Wi-Fi. A box is 32 random-looking bytes only the two devices can work out (from a one-time code while a new
// device is added, from the key the two share after that: data/FamilyLink.kt, data/FamilyPost.kt); what's in it is sealed
// on one device and opened on the other, so the relay holds opaque bytes under opaque names: no key, no name, no family.
//
// There are no accounts and no signatures here, on purpose: a new device has no town and so no key the relay could know yet,
// and a signature would only tie a family's boxes together. Knowing the box is the whole right to it. A stranger who guessed one
// (2^256) would find bytes they can't open and could write nothing the devices would take, so the limits below are about
// room and abuse, never secrecy.
//
//   PUT /v1/drop/{box}           the body as it is, at most RELAY_DROP_MAX_BYTES; `x-lani-drop-ttl` seconds (default a day, at
//                                most RELAY_DROP_DAYS) → 202 {expires}. 507 box_full / full; 429 past an address's writes or bytes.
//   GET /v1/drop/{box}?wait=<s>  every note that waits, oldest first, and gone: {items: [base64]}, at once or as soon as one
//                                comes (a long poll of up to RELAY_POLL_WAIT_MAX_MS).
import type { Config } from './config'
import { HttpError, json, readBody } from './http'
import { RateLimiter, type Waiters } from './limits'
import type { Store } from './store'

const DAY_S = 86_400

export function drops(cfg: Config, store: Store, waiters: Waiters, limited: (wait: number) => HttpError) {
  const now = cfg.now
  const writes = new RateLimiter(cfg.dropRatePerMin, Math.max(10, cfg.dropRatePerMin / 4), now)
  // boxes are free and a note can stay two weeks, so what an address may leave is counted in bytes too; a tiny note still costs a row
  const bytes = new RateLimiter(cfg.dropBytesPerMin, cfg.dropBytesPerMin * 4, now)
  const CHARGED_AT_LEAST = 1024

  async function put(req: Request, box: string, ip: string): Promise<Response> {
    const wait = writes.take(`drop:${ip}`)
    if (wait) throw limited(wait)
    const body = await readBody(req, cfg.dropMaxBytes)
    if (!body.length) throw new HttpError(400, 'bad_request', 'an empty note')
    const over = bytes.take(`drop:${ip}`, Math.max(CHARGED_AT_LEAST, body.length))
    if (over) throw limited(over)
    const t = now()
    if (store.dropCount(box, t) >= cfg.dropBoxMax) throw new HttpError(507, 'box_full', 'too many notes wait in this box')
    if (store.dropBytes() + body.length > cfg.dropTotalBytes || store.totalBytes() + body.length > cfg.storeMaxBytes) throw new HttpError(507, 'full', 'the relay is full: try again later')
    const asked = Number(req.headers.get('x-lani-drop-ttl'))
    const ttl = Math.min(cfg.dropDays * DAY_S, Number.isFinite(asked) && asked > 0 ? asked : DAY_S)
    const expires = t + Math.max(60, ttl) * 1000
    store.putDrop(box, body, t, expires)
    waiters.notify(`drop:${box}`)
    return json({ ok: true, expires: new Date(expires).toISOString() }, 202)
  }

  async function take(req: Request, url: URL, box: string): Promise<Response> {
    const wait = Math.max(0, Math.min(cfg.pollWaitMaxMs, (Number(url.searchParams.get('wait')) || 0) * 1000))
    let got = store.takeDrops(box, now())
    if (!got.length && wait > 0) {
      await waiters.wait(`drop:${box}`, wait, req.signal)
      // a poll whose connection closed takes nothing: what came stays for the next one
      if (req.signal.aborted) return json({ items: [] })
      got = store.takeDrops(box, now())
    }
    return json({ items: got.map(b => Buffer.from(b).toString('base64')) })
  }

  return (req: Request, url: URL, box: string, ip: string): Promise<Response> => {
    if (req.method === 'PUT') return put(req, box, ip)
    if (req.method === 'GET') return take(req, url, box)
    throw new HttpError(405, 'method', 'GET or PUT')
  }
}
