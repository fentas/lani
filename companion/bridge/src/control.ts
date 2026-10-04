// The control connection between the bridge service (src/service.ts) and the tutor session's shim (src/shim.ts), see
// docs/plans/04-bridge-service.md: MCP itself, one JSON-RPC message per line, over a unix socket. The shim is an MCP
// client of the service; the service runs an MCP server per connection.
//
// The shim loads this file once for the whole tutor session, so it changes only with a tutor restart: keep it small,
// and keep a service accepting PROTOCOL 1 until every tutor session has restarted.
import { createHash } from 'node:crypto'
import { chmodSync, existsSync, mkdirSync, readFileSync, renameSync, unlinkSync, writeFileSync } from 'node:fs'
import { connect, createServer, type Socket } from 'node:net'
import { homedir } from 'node:os'
import { dirname, join } from 'node:path'
import { ReadBuffer, serializeMessage } from '@modelcontextprotocol/sdk/shared/stdio.js'
import type { Transport } from '@modelcontextprotocol/sdk/shared/transport.js'
import type { JSONRPCMessage } from '@modelcontextprotocol/sdk/types.js'
import { laniDir } from './env'

/** The shim's version of this contract, in its hello. */
export const PROTOCOL = 1
/** The experimental capability the shim's hello travels in (initialize, client → service). */
export const SHIM_CAPABILITY = 'lani/shim'
/** The experimental capability the service's hello travels in (the initialize result, service → shim). */
export const SERVICE_CAPABILITY = 'lani/service'
/** service → shim: another tutor session took over this learner's channel; don't reconnect. */
export const REPLACED = 'notifications/lani/replaced'
/** The JSON-RPC error of a tool call the service refused because it is stopping: the shim retries it on the next one. */
export const RESTARTING = -32090
/** Notifications of these methods pass the shim both ways unread (the channel's events, the permission relay). */
export const FORWARDED = /^notifications\/claude\//

/** What the shim tells the service when it connects. [heard]: the session has had a conversation event already. */
export type ShimHello = { protocol: number; session: string; heard: boolean; pid: number }
/** What the service tells the shim. */
export type ServiceHello = { protocol: number; pid: number; started_at: string; profile: string }

/**
 * What a shim needs to start while the service is down: the channel's instructions, its experimental capabilities and
 * its tools, as the service last gave them (written at each start of the service).
 */
export type ChannelSnapshot = { instructions: string; experimental: Record<string, object>; tools: unknown[]; at: string }

/** Linux allows 107 bytes for a unix socket's path. */
const MAX_SOCKET_PATH = 107

/** ~/.local/state, or $XDG_STATE_HOME. */
export const stateDir = (env = process.env) => env.XDG_STATE_HOME || join(env.HOME || homedir(), '.local', 'state')

/**
 * The service's socket for the learner whose app directory is [appDir]: $LANI_BRIDGE_SOCKET, else
 * ~/.local/state/lani/run/bridge-<the first 12 hex digits of the SHA-256 of appDir>.sock (~/.local/state/fluent/… while
 * only that one exists: env.ts). Not inside the data:
 * copying a directory with a socket in it fails (QA copies data/).
 */
export function controlSocket(appDir: string, env = process.env): string {
  const set = env.LANI_BRIDGE_SOCKET?.trim()
  if (set) return set
  const hash = createHash('sha256').update(appDir).digest('hex').slice(0, 12)
  return join(laniDir(stateDir(env)), 'run', `bridge-${hash}.sock`)
}

const sibling = (socket: string, suffix: string) => socket.replace(/\.sock$/, '') + suffix
/** The service's status beside its socket (lani-bridge status). */
export const statusFile = (socket: string) => sibling(socket, '-status.json')
/** The service's last channel snapshot beside its socket. */
export const channelFile = (socket: string) => sibling(socket, '-channel.json')

export function writeJson(path: string, value: unknown) {
  mkdirSync(dirname(path), { recursive: true, mode: 0o700 })
  writeFileSync(`${path}.tmp`, JSON.stringify(value, null, 2) + '\n', { mode: 0o600 })
  renameSync(`${path}.tmp`, path)
}

export function readJson<T>(path: string): T | undefined {
  try {
    return JSON.parse(readFileSync(path, 'utf8')) as T
  } catch {
    return undefined
  }
}

/** One MCP connection over a socket: JSON-RPC messages, one per line (the stdio framing). */
export class SocketTransport implements Transport {
  onclose?: () => void
  onerror?: (error: Error) => void
  onmessage?: (message: JSONRPCMessage) => void
  private readonly buffer = new ReadBuffer()
  private closed = false

  constructor(private readonly socket: Socket) {
    socket.on('error', e => this.onerror?.(e))
    socket.on('close', () => this.finish())
  }

  async start() {
    if (this.closed) throw new Error('the control connection closed before it started')
    this.socket.on('data', (chunk: Buffer) => {
      try {
        this.buffer.append(chunk)
      } catch (e) {
        this.onerror?.(e as Error) // a message beyond the buffer's limit: the connection is dropped
        this.socket.destroy()
        return
      }
      for (;;) {
        let message: JSONRPCMessage | null
        try {
          message = this.buffer.readMessage()
        } catch (e) {
          this.onerror?.(e as Error) // a line that isn't JSON-RPC is skipped
          continue
        }
        if (!message) break
        this.onmessage?.(message)
      }
    })
  }

  send(message: JSONRPCMessage): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.closed || this.socket.destroyed) return reject(new Error('the control connection is closed'))
      this.socket.write(serializeMessage(message), e => (e ? reject(e) : resolve()))
    })
  }

  async close() {
    this.socket.end()
    this.socket.destroy()
    this.finish()
  }

  private finish() {
    if (this.closed) return
    this.closed = true
    this.buffer.clear()
    this.onclose?.()
  }
}

/** A connection to the service at [path]; rejects when nothing listens there within [timeoutMs]. */
export function connectSocket(path: string, timeoutMs = 2_000): Promise<SocketTransport> {
  return new Promise((resolve, reject) => {
    const socket = connect(path)
    const timer = setTimeout(() => {
      socket.destroy()
      reject(new Error(`no answer on ${path} within ${timeoutMs} ms`))
    }, timeoutMs)
    socket.once('error', e => {
      clearTimeout(timer)
      reject(e)
    })
    socket.once('connect', () => {
      clearTimeout(timer)
      socket.removeAllListeners('error')
      resolve(new SocketTransport(socket))
    })
  })
}

/** Whether a service answers on [path] now. */
export async function answers(path: string): Promise<boolean> {
  try {
    const t = await connectSocket(path, 1_000)
    await t.close()
    return true
  } catch {
    return false
  }
}

/**
 * Listens on [path] (mode 600; its directory made with mode 700). A socket file nobody answers on is left from a
 * service that died: it is removed. When a service answers there, this throws: one service per learner.
 */
export async function listenControl(path: string, onConnection: (t: SocketTransport) => void): Promise<{ close: () => void }> {
  if (Buffer.byteLength(path) > MAX_SOCKET_PATH) throw new Error(`the control socket's path is too long (${Buffer.byteLength(path)} bytes, at most ${MAX_SOCKET_PATH}): set LANI_BRIDGE_SOCKET to a shorter one`)
  mkdirSync(dirname(path), { recursive: true, mode: 0o700 })
  if (existsSync(path)) {
    if (await answers(path)) throw new Error(`another bridge service already listens on ${path}`)
    unlinkSync(path)
  }
  const server = createServer(socket => onConnection(new SocketTransport(socket)))
  await new Promise<void>((resolve, reject) => {
    server.once('error', reject)
    server.listen(path, () => {
      server.off('error', reject)
      resolve()
    })
  })
  chmodSync(path, 0o600)
  return {
    close: () => {
      server.close()
      try {
        unlinkSync(path)
      } catch {} // gone already
    },
  }
}
