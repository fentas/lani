// In memory: the rate limits (a token bucket per key, address or family page) and who waits for what (a town's
// long poll for its mail, a sender for its answer). One relay process holds them; a second replica would need its
// own (docs/plans/05-built/relay.md, "Operations").

/** Token buckets: [perMin] a minute, up to [burst] at once, per name. */
export class RateLimiter {
  private buckets = new Map<string, { tokens: number; at: number }>()

  constructor(
    private readonly perMin: number,
    private readonly burst: number,
    private readonly now: () => number,
  ) {}

  /** Milliseconds until [name] may go again; 0 when it may now (and its token is spent). */
  take(name: string): number {
    if (this.perMin <= 0) return 0
    const now = this.now()
    if (this.buckets.size > 50_000) {
      for (const [k, b] of this.buckets) if (now - b.at > 10 * 60_000) this.buckets.delete(k)
    }
    const rate = this.perMin / 60_000
    const b = this.buckets.get(name) ?? { tokens: this.burst, at: now }
    b.tokens = Math.min(this.burst, b.tokens + (now - b.at) * rate)
    b.at = now
    this.buckets.set(name, b)
    if (b.tokens >= 1) {
      b.tokens -= 1
      return 0
    }
    return Math.ceil((1 - b.tokens) / rate)
  }
}

/** Who waits for [key]: notified, or let go after their time (or when their request goes away). */
export class Waiters {
  private waiting = new Map<string, Set<() => void>>()

  /** Waits until [key] is notified, [ms] pass, or [signal] aborts; true when notified. */
  wait(key: string, ms: number, signal?: AbortSignal): Promise<boolean> {
    return new Promise(resolve => {
      let done = false
      const finish = (notified: boolean) => {
        if (done) return
        done = true
        clearTimeout(timer)
        signal?.removeEventListener('abort', aborted)
        const set = this.waiting.get(key)
        set?.delete(wake)
        if (set && !set.size) this.waiting.delete(key)
        resolve(notified)
      }
      const wake = () => finish(true)
      const aborted = () => finish(false)
      const timer = setTimeout(() => finish(false), Math.max(0, ms))
      if (signal?.aborted) return finish(false)
      signal?.addEventListener('abort', aborted)
      let set = this.waiting.get(key)
      if (!set) this.waiting.set(key, (set = new Set()))
      set.add(wake)
    })
  }

  notify(key: string) {
    for (const wake of [...(this.waiting.get(key) ?? [])]) wake()
  }

  /** How many wait for [key]. */
  count(key: string): number {
    return this.waiting.get(key)?.size ?? 0
  }
}
