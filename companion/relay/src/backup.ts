// Backups (the shared contract): encrypted blobs, scoped to the key that signs. The relay holds ciphertext only; the
// encryption is the client's (plan 5, "Backup through the SaaS").
//   PUT    /v1/backup/{name}   the body: the blob, at most 50 MB   → {name, size, time}
//   GET    /v1/backup/{name}   the blob                            (x-lani-backup-time, x-lani-backup-sha256)
//   GET    /v1/backup          [{name, size, time}]                (time: unix ms of the PUT)
//   DELETE /v1/backup/{name}                                       → {ok: true}
// A PUT's body is streamed to a temp file and hashed on the way; its signature (over that hash) is checked before the
// file takes the name. Each key has a quota of bytes and a number of backups.
import { createHash, randomBytes } from 'node:crypto'
import { closeSync, existsSync, mkdirSync, openSync, renameSync, unlinkSync, writeSync } from 'node:fs'
import { join } from 'node:path'
import type { Config } from './config'
import { HttpError, json } from './http'
import { MAX_SKEW_MS, checkSigned, parseKey, sha256hex, type Signed } from './sign'
import type { Store } from './store'

export const BACKUP_NAME = /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/

export type Admit = (signed: Signed) => void

export function backups(cfg: Config, store: Store, admit: Admit) {
  const root = join(cfg.dataDir, 'backup')
  let uploads = 0
  const dirOf = (owner: string) => join(root, owner)
  const fileOf = (owner: string, name: string) => join(dirOf(owner), name)

  /** A signed GET or DELETE (no body): its signer, admitted. */
  function signedEmpty(req: Request, url: URL): Signed {
    const check = checkSigned(req.headers, { method: req.method, pathWithQuery: url.pathname + url.search, bodySha256: sha256hex(''), now: cfg.now() })
    if (!check.ok) throw new HttpError(401, check.code, 'the request is not signed by a town key')
    admit(check.signed)
    return check.signed
  }

  /** Streams [req]'s body into a temp file under [dir], at most [max] bytes: its path, size and SHA-256. */
  async function spool(req: Request, dir: string, max: number): Promise<{ tmp: string; size: number; sha256: string }> {
    if (Number(req.headers.get('content-length') ?? 0) > max) throw new HttpError(413, 'too_large', `a backup is at most ${max} bytes`)
    mkdirSync(dir, { recursive: true, mode: 0o700 })
    const tmp = join(dir, `.tmp-${randomBytes(8).toString('hex')}`)
    const fd = openSync(tmp, 'w', 0o600)
    const hash = createHash('sha256')
    let size = 0
    try {
      if (req.body) {
        const reader = req.body.getReader()
        for (;;) {
          const { done, value } = await reader.read()
          if (done) break
          size += value.byteLength
          if (size > max) {
            await reader.cancel().catch(() => {})
            throw new HttpError(413, 'too_large', `a backup is at most ${max} bytes`)
          }
          hash.update(value)
          writeSync(fd, value)
        }
      }
    } catch (e) {
      closeSync(fd)
      unlinkSync(tmp)
      throw e
    }
    closeSync(fd)
    return { tmp, size, sha256: hash.digest('hex') }
  }

  async function put(req: Request, url: URL, name: string): Promise<Response> {
    // what can be checked before the body: the headers' shape, the clock, the key's quota and the relay's room for what
    // it says it sends, and how many uploads run (the signature needs the whole body)
    const timeText = req.headers.get('x-lani-time') ?? ''
    const claimed = parseKey(req.headers.get('x-lani-key'))
    if (!claimed || !req.headers.get('x-lani-sig') || !/^\d{1,16}$/.test(timeText)) throw new HttpError(401, 'unsigned', 'the request is not signed by a town key')
    if (Math.abs(cfg.now() - Number(timeText)) > MAX_SKEW_MS) throw new HttpError(401, 'stale', "the request's time is more than 5 minutes off")
    const announced = Number(req.headers.get('content-length') ?? cfg.backupMaxBytes)
    const mine = store.backups(claimed.fingerprint)
    const kept = mine.reduce((n, b) => n + b.size, 0) - (mine.find(b => b.name === name)?.size ?? 0)
    if (kept + Math.min(announced, cfg.backupMaxBytes) > cfg.backupQuotaBytes) throw new HttpError(507, 'quota', `the backups of a key are at most ${cfg.backupQuotaBytes} bytes together: delete one first`)
    if (store.totalBytes() + Math.min(announced, cfg.backupMaxBytes) > cfg.storeMaxBytes) throw new HttpError(507, 'full', 'the relay is full: try again later')
    if (uploads >= cfg.backupUploads) throw new HttpError(429, 'busy', 'too many backups are being uploaded: try again in a minute', { 'retry-after': '30' })
    uploads++
    const staging = join(root, '.incoming')
    let spooled: { tmp: string; size: number; sha256: string }
    try {
      spooled = await spool(req, staging, cfg.backupMaxBytes)
    } finally {
      uploads--
    }
    const { tmp, size, sha256 } = spooled
    try {
      const check = checkSigned(req.headers, { method: 'PUT', pathWithQuery: url.pathname + url.search, bodySha256: sha256, now: cfg.now() })
      if (!check.ok) throw new HttpError(401, check.code, 'the request is not signed by a town key')
      admit(check.signed)
      const owner = check.signed.id
      const all = store.backups(owner)
      const old = all.find(b => b.name === name)
      const total = all.reduce((n, b) => n + b.size, 0) - (old?.size ?? 0) + size
      if (!old && all.length >= cfg.backupMaxCount) throw new HttpError(507, 'quota', `at most ${cfg.backupMaxCount} backups per key: delete one first`)
      if (total > cfg.backupQuotaBytes) throw new HttpError(507, 'quota', `the backups of a key are at most ${cfg.backupQuotaBytes} bytes together: delete one first`)
      mkdirSync(dirOf(owner), { recursive: true, mode: 0o700 })
      renameSync(tmp, fileOf(owner, name))
      const row = { owner, name, size, time: cfg.now(), sha256 }
      store.putBackup(row)
      return json({ name, size, time: row.time })
    } finally {
      if (existsSync(tmp)) unlinkSync(tmp)
    }
  }

  function get(req: Request, url: URL, name: string): Response {
    const owner = signedEmpty(req, url).id
    const row = store.backup(owner, name)
    const path = fileOf(owner, name)
    if (!row || !existsSync(path)) throw new HttpError(404, 'not_found', 'no such backup')
    return new Response(Bun.file(path), {
      headers: { 'x-lani-relay': 'ok', 'content-type': 'application/octet-stream', 'content-length': String(row.size), 'x-lani-backup-time': String(row.time), 'x-lani-backup-sha256': row.sha256, 'cache-control': 'no-store' },
    })
  }

  function list(req: Request, url: URL): Response {
    const owner = signedEmpty(req, url).id
    return json(store.backups(owner).map(b => ({ name: b.name, size: b.size, time: b.time })))
  }

  function remove(req: Request, url: URL, name: string): Response {
    const owner = signedEmpty(req, url).id
    if (!store.deleteBackup(owner, name)) throw new HttpError(404, 'not_found', 'no such backup')
    try {
      unlinkSync(fileOf(owner, name))
    } catch {}
    return json({ ok: true })
  }

  /** The backup routes; undefined when [url] isn't one. */
  return async (req: Request, url: URL): Promise<Response | undefined> => {
    if (url.pathname === '/v1/backup') {
      if (req.method !== 'GET') throw new HttpError(405, 'method', 'GET only')
      return list(req, url)
    }
    const m = /^\/v1\/backup\/([^/]+)$/.exec(url.pathname)
    if (!m) return undefined
    let name = ''
    try {
      name = decodeURIComponent(m[1])
    } catch {}
    if (!BACKUP_NAME.test(name)) throw new HttpError(400, 'bad_name', 'a backup name is 1-128 letters, digits, ".", "_" or "-", starting with a letter or digit')
    if (req.method === 'PUT') return put(req, url, name)
    if (req.method === 'GET') return get(req, url, name)
    if (req.method === 'DELETE') return remove(req, url, name)
    throw new HttpError(405, 'method', 'PUT, GET or DELETE')
  }
}
