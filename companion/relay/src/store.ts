// The relay's storage: one SQLite database (bun:sqlite, WAL) for the towns, their public state, the mailboxes and the
// answers, and the backups' list; the backups themselves are files beside it (backup.ts).
import { Database } from 'bun:sqlite'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

/** A town the relay knows: it signed as itself (a poll, a publish). Its key answers /town/hello for it. */
export type TownRow = {
  id: string
  public_key: string
  first_seen: number
  last_seen: number
  last_poll: number | null
  family_sha256: string | null
  readers: string
}

/**
 * A message in a town's mailbox: an HTTP request for the town's bridge ([kind] "http"), from [sender] (a town's id,
 * or "family:<town>" for the family page), whose [meta] holds the request's method, path and headers and the sender's
 * own signature of it; its body inline below (a large one is a "blob": fetched on its own).
 */
export type MailRow = {
  id: string
  town: string
  kind: string
  scope: string
  sender: string
  at: number
  expires: number
  leased_until: number
  meta: string
  size: number
}

export type ReplyRow = { mail: string; sender: string; town: string; status: number; headers: string; size: number; at: number; expires: number }

export type BackupRow = { owner: string; name: string; size: number; time: number; sha256: string }

export class Store {
  readonly db: Database

  constructor(dir: string) {
    mkdirSync(dir, { recursive: true })
    this.db = new Database(join(dir, 'relay.sqlite'), { create: true })
    this.db.exec('PRAGMA journal_mode = WAL; PRAGMA busy_timeout = 5000; PRAGMA synchronous = NORMAL;')
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS towns (
        id TEXT PRIMARY KEY, public_key TEXT NOT NULL, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL,
        last_poll INTEGER, family_sha256 TEXT, readers TEXT NOT NULL DEFAULT '[]');
      CREATE TABLE IF NOT EXISTS public_state (
        id TEXT PRIMARY KEY, body BLOB NOT NULL, path TEXT NOT NULL, time TEXT NOT NULL, sig TEXT NOT NULL, at INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS mail (
        id TEXT PRIMARY KEY, town TEXT NOT NULL, kind TEXT NOT NULL, scope TEXT NOT NULL, sender TEXT NOT NULL,
        at INTEGER NOT NULL, expires INTEGER NOT NULL, leased_until INTEGER NOT NULL DEFAULT 0, meta TEXT NOT NULL,
        body BLOB, size INTEGER NOT NULL DEFAULT 0);
      CREATE INDEX IF NOT EXISTS mail_town ON mail (town, at);
      CREATE INDEX IF NOT EXISTS mail_sender ON mail (sender);
      CREATE TABLE IF NOT EXISTS replies (
        mail TEXT PRIMARY KEY, sender TEXT NOT NULL, town TEXT NOT NULL, status INTEGER NOT NULL, headers TEXT NOT NULL,
        body BLOB, size INTEGER NOT NULL, at INTEGER NOT NULL, expires INTEGER NOT NULL);
      CREATE INDEX IF NOT EXISTS replies_town ON replies (town);
      CREATE TABLE IF NOT EXISTS backups (
        owner TEXT NOT NULL, name TEXT NOT NULL, size INTEGER NOT NULL, time INTEGER NOT NULL, sha256 TEXT NOT NULL,
        PRIMARY KEY (owner, name));
      CREATE TABLE IF NOT EXISTS seen (k TEXT PRIMARY KEY, until INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS drops (
        id INTEGER PRIMARY KEY AUTOINCREMENT, box TEXT NOT NULL, body BLOB NOT NULL, size INTEGER NOT NULL, at INTEGER NOT NULL, expires INTEGER NOT NULL);
      CREATE INDEX IF NOT EXISTS drops_box ON drops (box, id);
    `)
  }

  close() {
    this.db.close()
  }

  // --- replays ---------------------------------------------------------------------------------------------------------

  /** Whether [k] is new (and remembers it until [until]); false for a replay. Kept in the database, so a restart remembers. */
  firstTime(k: string, until: number): boolean {
    return this.db.query('INSERT OR IGNORE INTO seen (k, until) VALUES (?, ?)').run(k, until).changes > 0
  }

  // --- towns -----------------------------------------------------------------------------------------------------------

  town(id: string): TownRow | undefined {
    return (this.db.query('SELECT * FROM towns WHERE id = ?').get(id) as TownRow | null) ?? undefined
  }

  /** The town [id] signed as itself: it is known (its key) from now on. */
  seeTown(id: string, publicKey: string, now: number) {
    this.db
      .query('INSERT INTO towns (id, public_key, first_seen, last_seen) VALUES (?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET last_seen = excluded.last_seen, public_key = excluded.public_key')
      .run(id, publicKey, now, now)
  }

  polled(id: string, now: number) {
    this.db.query('UPDATE towns SET last_poll = ? WHERE id = ?').run(now, id)
  }

  setReaders(id: string, readers: string[]) {
    this.db.query('UPDATE towns SET readers = ? WHERE id = ?').run(JSON.stringify(readers), id)
  }

  setFamily(id: string, sha256: string | null) {
    this.db.query('UPDATE towns SET family_sha256 = ? WHERE id = ?').run(sha256, id)
  }

  // --- public state ----------------------------------------------------------------------------------------------------

  putPublic(id: string, body: Uint8Array, signed: { path: string; time: string; sig: string }, now: number) {
    this.db
      .query('INSERT INTO public_state (id, body, path, time, sig, at) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET body = excluded.body, path = excluded.path, time = excluded.time, sig = excluded.sig, at = excluded.at')
      .run(id, body, signed.path, signed.time, signed.sig, now)
  }

  getPublic(id: string): { body: Uint8Array; path: string; time: string; sig: string; at: number } | undefined {
    return (this.db.query('SELECT body, path, time, sig, at FROM public_state WHERE id = ?').get(id) as any) ?? undefined
  }

  // --- mail ------------------------------------------------------------------------------------------------------------

  addMail(m: MailRow, body: Uint8Array | null) {
    this.db
      .query('INSERT INTO mail (id, town, kind, scope, sender, at, expires, leased_until, meta, body, size) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)')
      .run(m.id, m.town, m.kind, m.scope, m.sender, m.at, m.expires, m.meta, body, m.size)
  }

  /** How much waits in [town]'s mailbox. */
  mailbox(town: string, now: number): { count: number; bytes: number } {
    return this.db.query('SELECT COUNT(*) AS count, COALESCE(SUM(size), 0) AS bytes FROM mail WHERE town = ? AND expires > ?').get(town, now) as any
  }

  /** How much of [sender]'s mail waits, in all mailboxes. */
  senderBytes(sender: string, now: number): number {
    return (this.db.query('SELECT COALESCE(SUM(size), 0) AS bytes FROM mail WHERE sender = ? AND expires > ?').get(sender, now) as any).bytes
  }

  /**
   * Up to [limit] of [town]'s messages that aren't being answered, oldest first, now leased to it until [until]; their
   * bodies up to [inline] bytes (a larger one is fetched on its own).
   */
  lease(town: string, limit: number, now: number, until: number, inline: number): (MailRow & { body: Uint8Array | null })[] {
    return this.db.transaction(() => {
      const rows = this.db
        .query('SELECT id, town, kind, scope, sender, at, expires, leased_until, meta, size, CASE WHEN size <= ? THEN body END AS body FROM mail WHERE town = ? AND leased_until <= ? AND expires > ? ORDER BY at LIMIT ?')
        .all(inline, town, now, now, limit) as (MailRow & { body: Uint8Array | null })[]
      const lease = this.db.query('UPDATE mail SET leased_until = ? WHERE id = ?')
      for (const r of rows) lease.run(until, r.id)
      return rows
    })()
  }

  mail(id: string): (MailRow & { body: Uint8Array | null }) | undefined {
    return (this.db.query('SELECT * FROM mail WHERE id = ?').get(id) as any) ?? undefined
  }

  /** Takes message [id] back while its town hasn't fetched it; whether it did. */
  withdraw(id: string): boolean {
    return this.db.query('DELETE FROM mail WHERE id = ? AND leased_until = 0').run(id).changes > 0
  }

  /** Removes [town]'s messages [ids]; how many. */
  ack(town: string, ids: string[]): number {
    const del = this.db.query('DELETE FROM mail WHERE id = ? AND town = ?')
    return this.db.transaction(() => ids.reduce((n, id) => n + del.run(id, town).changes, 0))()
  }

  // --- answers ---------------------------------------------------------------------------------------------------------

  /** The town's answer to message [r.mail]: kept for its sender, the message gone. */
  putReply(r: ReplyRow, body: Uint8Array) {
    this.db.transaction(() => {
      this.db
        .query('INSERT OR REPLACE INTO replies (mail, sender, town, status, headers, body, size, at, expires) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)')
        .run(r.mail, r.sender, r.town, r.status, r.headers, body, r.size, r.at, r.expires)
      this.db.query('DELETE FROM mail WHERE id = ?').run(r.mail)
    })()
  }

  reply(mail: string): (ReplyRow & { body: Uint8Array | null }) | undefined {
    return (this.db.query('SELECT * FROM replies WHERE mail = ?').get(mail) as any) ?? undefined
  }

  dropReply(mail: string) {
    this.db.query('DELETE FROM replies WHERE mail = ?').run(mail)
  }

  replyBytes(town: string, now: number): number {
    return (this.db.query('SELECT COALESCE(SUM(size), 0) AS bytes FROM replies WHERE town = ? AND expires > ?').get(town, now) as any).bytes
  }

  // --- backups ---------------------------------------------------------------------------------------------------------

  backups(owner: string): BackupRow[] {
    return this.db.query('SELECT * FROM backups WHERE owner = ? ORDER BY name').all(owner) as BackupRow[]
  }

  backup(owner: string, name: string): BackupRow | undefined {
    return (this.db.query('SELECT * FROM backups WHERE owner = ? AND name = ?').get(owner, name) as BackupRow | null) ?? undefined
  }

  putBackup(b: BackupRow) {
    this.db.query('INSERT OR REPLACE INTO backups (owner, name, size, time, sha256) VALUES (?, ?, ?, ?, ?)').run(b.owner, b.name, b.size, b.time, b.sha256)
  }

  deleteBackup(owner: string, name: string): boolean {
    return this.db.query('DELETE FROM backups WHERE owner = ? AND name = ?').run(owner, name).changes > 0
  }

  // --- a family's drop boxes (drop.ts) -----------------------------------------------------------------------------------

  /** How many notes wait in [box] now. */
  dropCount(box: string, now: number): number {
    return (this.db.query('SELECT COUNT(*) AS n FROM drops WHERE box = ? AND expires > ?').get(box, now) as any).n
  }

  /** All the notes' bytes, in every box. */
  dropBytes(): number {
    return (this.db.query('SELECT COALESCE(SUM(size), 0) AS bytes FROM drops').get() as any).bytes
  }

  putDrop(box: string, body: Uint8Array, at: number, expires: number) {
    this.db.query('INSERT INTO drops (box, body, size, at, expires) VALUES (?, ?, ?, ?, ?)').run(box, body, body.length, at, expires)
  }

  /** The notes of [box], oldest first, and gone: what's read here is read once. */
  takeDrops(box: string, now: number): Uint8Array[] {
    return this.db.transaction(() => {
      const rows = this.db.query('SELECT body FROM drops WHERE box = ? AND expires > ? ORDER BY id').all(box, now) as { body: Uint8Array }[]
      this.db.query('DELETE FROM drops WHERE box = ?').run(box)
      return rows.map(r => r.body)
    })()
  }

  /** All the bytes the relay keeps: backups, mail, answers, a family's notes. */
  totalBytes(): number {
    const sum = (table: string) => (this.db.query(`SELECT COALESCE(SUM(size), 0) AS bytes FROM ${table}`).get() as any).bytes as number
    return sum('backups') + sum('mail') + sum('replies') + sum('drops')
  }

  // --- retention ---------------------------------------------------------------------------------------------------------

  /** Removes what ran out: mail (14 days, or a request's own deadline), answers, a family's notes, remembered signatures. */
  sweep(now: number): { mail: number; replies: number; seen: number; drops: number } {
    return {
      mail: this.db.query('DELETE FROM mail WHERE expires <= ?').run(now).changes,
      replies: this.db.query('DELETE FROM replies WHERE expires <= ?').run(now).changes,
      seen: this.db.query('DELETE FROM seen WHERE until <= ?').run(now).changes,
      drops: this.db.query('DELETE FROM drops WHERE expires <= ?').run(now).changes,
    }
  }
}
