// The voice profiles' table on the phone (profiles.ts keeps one voice per person in the voice store's SQLite on a node):
// the same rows in a JSON file (<data>/app/voice-profiles.json), answering exactly the statements profiles.ts makes, so
// the app gets everyone's archetype, pitch and pace from the phone's bridge as from a node's. Anything else is refused.
import { existsSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import type { Profile } from '../profiles'

type Row = Profile

/** What profiles.ts needs of bun:sqlite's Database: exec and query(…).get/all/run for its own statements. */
export class ProfilesTable {
  private rows = new Map<string, Row>()

  constructor(private readonly path: string) {
    try {
      if (existsSync(path)) for (const r of JSON.parse(readFileSync(path, 'utf8')) as Row[]) this.rows.set(r.person, r)
    } catch {} // unreadable: a fresh table, as a new voice store would be
  }

  private save() {
    writeFileSync(`${this.path}.tmp`, JSON.stringify([...this.rows.values()]))
    renameSync(`${this.path}.tmp`, this.path)
  }

  exec(sql: string) {
    const s = sql.replace(/\s+/g, ' ').trim()
    if (/^CREATE TABLE IF NOT EXISTS profiles /.test(s)) return
    if (s === "UPDATE profiles SET status = CASE WHEN elevenlabs IS NOT NULL THEN 'own' ELSE 'shared' END WHERE status = 'designing'") {
      let changed = false
      for (const r of this.rows.values()) if (r.status === 'designing') {
        r.status = r.elevenlabs !== null && r.elevenlabs !== undefined ? 'own' : 'shared'
        changed = true
      }
      if (changed) this.save()
      return
    }
    throw new Error(`the phone's voice profiles don't run: ${s}`)
  }

  query(sql: string) {
    const s = sql.replace(/\s+/g, ' ').trim()
    const all = () => [...this.rows.values()]
    if (s === 'SELECT * FROM profiles WHERE person = ?') return { get: (person: string) => (this.rows.has(person) ? { ...this.rows.get(person)! } : null) }
    if (s === 'SELECT * FROM profiles ORDER BY "cast" DESC, "order", person')
      return { all: () => all().sort((a, b) => b.cast - a.cast || a.order - b.order || (a.person < b.person ? -1 : a.person > b.person ? 1 : 0)).map(r => ({ ...r })) }
    if (s === "SELECT person, archetype, elevenlabs FROM profiles WHERE status = 'own' AND elevenlabs IS NOT NULL")
      return { all: () => all().filter(r => r.status === 'own' && r.elevenlabs != null).map(r => ({ person: r.person, archetype: r.archetype, elevenlabs: r.elevenlabs })) }
    if (s.startsWith('INSERT OR REPLACE INTO profiles (')) {
      const cols = /\(([^)]*)\) VALUES/.exec(s)![1].split(',').map(c => c.trim().replace(/"/g, ''))
      return {
        run: (...values: unknown[]) => {
          const row = Object.fromEntries(cols.map((c, i) => [c, values[i] ?? null])) as unknown as Row
          this.rows.set(row.person, row)
          this.save()
          return { changes: 1 }
        },
      }
    }
    throw new Error(`the phone's voice profiles don't run: ${s}`)
  }

  close() {}
}
