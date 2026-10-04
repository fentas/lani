// The village game state ("Moja vas"): written by the app, read by the tutor.
// Stored as {rev, state}; the app writes with the rev it last read, so two devices can't clobber each other.
import { existsSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { knownIds } from './mentions'

export type GameDoc = { rev: number; state: Record<string, unknown> }

/**
 * An open request as the tutor sees it: its module, how often Jan played it and their best try against its pass mark (60 % of
 * that try's questions, at least 1: the app's Quests.passMark), the grammar pages its wrong answers named (most missed
 * first: what a drill for it would practise), and the task it is a drill for (quest.helps).
 */
function openQuest(q: any) {
  const count = (v: unknown) => (Number.isInteger(v) && (v as number) > 0 ? (v as number) : 0)
  const bestOf = count(q.bestOf)
  return {
    giver: q.giver,
    title: q.title,
    skill: q.skill,
    source: q.source,
    module: typeof q.moduleId === 'string' ? q.moduleId : undefined,
    tries: count(q.tries) || undefined,
    best: bestOf ? `${count(q.best)}/${bestOf}` : undefined,
    pass_mark: bestOf ? Math.max(1, Math.ceil((bestOf * 3) / 5)) : undefined,
    missed: Array.isArray(q.missed) && q.missed.some((p: unknown) => typeof p === 'string') ? q.missed.filter((p: unknown) => typeof p === 'string') : undefined,
    helps: typeof q.helps === 'string' ? q.helps : undefined,
  }
}

export class GameStore {
  private listeners: ((doc: GameDoc) => void)[] = []

  constructor(private readonly path: string) {}

  read(): GameDoc | undefined {
    return existsSync(this.path) ? JSON.parse(readFileSync(this.path, 'utf8')) : undefined
  }

  write(doc: GameDoc) {
    writeFileSync(`${this.path}.tmp`, JSON.stringify(doc))
    renameSync(`${this.path}.tmp`, this.path)
    for (const f of this.listeners) {
      try {
        f(doc)
      } catch {
        // a listener's trouble never fails the app's write
      }
    }
  }

  /** Calls [f] with the village every time the app writes it (the stories running low: features/stories.ts). */
  onWrite(f: (doc: GameDoc) => void) {
    this.listeners.push(f)
  }

  /**
   * A compact view of the village for the tutor: enough to talk about it, not the whole state. [nameOf]: a cast member's
   * name ("Čebelar Anton"), for who lives there.
   */
  summary(nameOf: (id: string) => string | undefined = () => undefined): string {
    const g = this.read()
    if (!g) return 'No village yet: Jan has not opened the game.'
    const s = g.state as any
    const count: Record<string, number> = {}
    for (const b of s.buildings ?? []) count[b.type] = (count[b.type] ?? 0) + 1
    // friendship level 2 ("Prijatelj · Friend") starts at 30 points (Bonds.kt); the later ages need friends
    const friends = Object.entries(s.bonds ?? {})
      .filter(([, b]: [string, any]) => (b?.points ?? 0) >= 30)
      .map(([id]) => id)
    // who is here and met: all a scene, a request or a line may name (companion/VILLAGERS.md, "Who a text may name");
    // who lives here and waits to be met first
    const known = knownIds(s)
    const living: string[] = (Array.isArray(s.residents) ? s.residents : []).flatMap((r: any) => (typeof r?.id === 'string' ? [r.id] : []))
    const named = (id: string) => nameOf(id) ?? (s.residents ?? []).find((r: any) => r?.id === id)?.name ?? id
    const people = known ? { known: [...known].map(named), to_meet: living.filter(id => !known.has(id)).map(named) } : undefined
    return JSON.stringify(
      {
        age: s.age,
        // the land it stands on (GAME.md, "The land"): classic for a village from before generated land
        land: typeof s.land?.kind === 'string' ? s.land.kind : 'classic',
        resources: s.resources,
        villagers: s.villagers,
        people,
        fire: s.fire,
        morale: s.morale,
        buildings: count,
        damaged: (s.buildings ?? []).filter((b: any) => b.damaged).map((b: any) => b.type),
        event: s.event,
        open_quests: (s.quests ?? []).filter((q: any) => !q.done).map(openQuest),
        // 🤝 earned by helping people (requests, talks, scenes, family), spent on a moba (companion/GAME.md)
        help: s.help ?? 0,
        friends,
        // the chest: tools the villagers gave (by giver: tier, times forged) and goods (thank-yous, gifts to give or sell)
        tools: s.chest?.tools ?? {},
        goods: s.chest?.goods ?? {},
        last_feast: s.lastFeast || undefined,
        // village projects (id → steps done), festivals celebrated (id → day), the day's surprise (companion/GAME.md)
        projects: s.projects ?? {},
        festivals: s.festivals ?? {},
        surprise: s.surprise ?? undefined,
        stats: s.stats,
        chronicle: (s.log ?? []).slice(-10),
      },
      null,
      2,
    )
  }
}
