// The relay's settings, from the environment (docs/plans/05-built/relay.md, "Operations"). Every limit has a default
// that fits a few hundred towns on one small machine.
import { resolve } from 'node:path'

export type Config = {
  host: string
  port: number
  /** Where the database (relay.sqlite) and the backups live. */
  dataDir: string
  /** The family page's static files (companion/bridge/web/family). */
  webDir: string
  /** Use X-Forwarded-For for a client's address (behind a proxy that sets it). */
  trustProxy: boolean

  /** Signed requests per key and minute, and the burst above it. */
  ratePerMin: number
  rateBurst: number
  /** Requests per address and minute (unsigned ones and the family page included). */
  ipRatePerMin: number
  /** The family page's requests per town and minute. */
  familyRatePerMin: number

  /** How long mail is kept (days). */
  mailDays: number
  /** At most this many messages and bytes wait in one town's mailbox. */
  mailboxMax: number
  mailboxBytes: number
  /** At most this many bytes of one sender wait in the mailboxes (the family page counts as its town's). */
  senderBytes: number
  /** A request's body (a town's tunnelled request; a family upload has its own limit). */
  mailMaxBytes: number
  /** A body up to this size is handed to the town in the mailbox's listing; a larger one is fetched on its own (a blob). */
  inlineMaxBytes: number
  /** A town's answer. */
  replyMaxBytes: number
  /** All of one town's answers waiting to be picked up. */
  replyBytes: number
  /** How long an answer is kept for its sender (hours). */
  replyHours: number
  /** A family recording's upload. */
  familyAudioMaxBytes: number
  /** A family page's JSON body. */
  familyJsonMaxBytes: number
  /** A town's public state. */
  publicMaxBytes: number

  /** The longest a tunnelled request waits for its answer, and the default. */
  tunnelWaitMaxMs: number
  tunnelWaitDefaultMs: number
  /** The longest a mailbox poll waits. */
  pollWaitMaxMs: number
  /** A town that polled this recently counts as there. */
  onlineMs: number
  /** How long a fetched message is the town's to answer before it is handed out again. */
  leaseMs: number

  /** A backup (the shared contract: at most 50 MB), and all of one key's backups. */
  backupMaxBytes: number
  backupQuotaBytes: number
  backupMaxCount: number

  /** All the relay keeps on disk at most (backups, mail and answers together): beyond it new ones are refused (507). */
  storeMaxBytes: number
  /** Backup uploads at once. */
  backupUploads: number

  /** Origins whose pages may call the family API (CORS); the relay's own family page needs none. */
  familyOrigins: string[]

  /** The clock (tests move it). */
  now: () => number
}

const MB = 1024 * 1024

export function configFromEnv(env: Record<string, string | undefined> = process.env): Config {
  const n = (k: string, d: number) => {
    const v = env[k]
    if (v === undefined || v === '') return d
    const x = Number(v)
    if (!Number.isFinite(x) || x < 0) throw new Error(`${k}: not a number: ${v}`)
    return x
  }
  return {
    host: env.RELAY_HOST || '0.0.0.0',
    port: n('RELAY_PORT', 8080),
    dataDir: resolve(env.RELAY_DATA_DIR || './data'),
    webDir: resolve(env.RELAY_WEB_DIR || resolve(import.meta.dir, '../../bridge/web/family')),
    trustProxy: env.RELAY_TRUST_PROXY === '1',
    ratePerMin: n('RELAY_RATE_PER_MIN', 600),
    rateBurst: n('RELAY_RATE_BURST', 120),
    ipRatePerMin: n('RELAY_IP_RATE_PER_MIN', 1200),
    familyRatePerMin: n('RELAY_FAMILY_RATE_PER_MIN', 60),
    mailDays: n('RELAY_MAIL_DAYS', 14),
    mailboxMax: n('RELAY_MAILBOX_MAX', 500),
    mailboxBytes: n('RELAY_MAILBOX_BYTES', 64 * MB),
    senderBytes: n('RELAY_SENDER_BYTES', 32 * MB),
    mailMaxBytes: n('RELAY_MAIL_MAX_BYTES', 64 * 1024),
    inlineMaxBytes: n('RELAY_INLINE_MAX_BYTES', 64 * 1024),
    replyMaxBytes: n('RELAY_REPLY_MAX_BYTES', 8 * MB),
    replyBytes: n('RELAY_REPLY_BYTES', 64 * MB),
    replyHours: n('RELAY_REPLY_HOURS', 24),
    familyAudioMaxBytes: n('RELAY_FAMILY_AUDIO_MAX_BYTES', 2 * MB + 64 * 1024),
    familyJsonMaxBytes: n('RELAY_FAMILY_JSON_MAX_BYTES', 16 * 1024),
    publicMaxBytes: n('RELAY_PUBLIC_MAX_BYTES', 256 * 1024),
    tunnelWaitMaxMs: n('RELAY_TUNNEL_WAIT_MAX_MS', 25_000),
    tunnelWaitDefaultMs: n('RELAY_TUNNEL_WAIT_MS', 8_000),
    pollWaitMaxMs: n('RELAY_POLL_WAIT_MAX_MS', 30_000),
    onlineMs: n('RELAY_ONLINE_MS', 45_000),
    leaseMs: n('RELAY_LEASE_MS', 60_000),
    backupMaxBytes: n('RELAY_BACKUP_MAX_BYTES', 50 * MB),
    backupQuotaBytes: n('RELAY_BACKUP_QUOTA_BYTES', 512 * MB),
    backupMaxCount: n('RELAY_BACKUP_MAX_COUNT', 100),
    storeMaxBytes: n('RELAY_STORE_MAX_BYTES', 4 * 1024 * MB),
    backupUploads: n('RELAY_BACKUP_UPLOADS', 4),
    familyOrigins: (env.RELAY_FAMILY_ORIGINS ?? '').split(',').map(s => s.trim().replace(/\/+$/, '')).filter(Boolean),
    now: Date.now,
  }
}
