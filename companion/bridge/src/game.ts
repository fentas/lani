// The village game state ("Moja vas"): written by the app, read by the tutor.
// Stored as {rev, state}; the app writes with the rev it last read, so two devices can't clobber each other.
import { existsSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { knownIds } from './mentions'

export type GameDoc = { rev: number; state: Record<string, unknown> }

/**
 * Whether [helps] (a drill's quest.helps: a module id, or a request's id) names request [q]: its module, its id, or its module
 * by the tutor's request id of another version ("tutor-<module>-v<n>"), as the app's QuestQueue.names.
 */
function names(helps: string, q: any): boolean {
  const module = typeof q?.moduleId === 'string' ? q.moduleId : undefined
  return module === helps || q?.id === helps || (module !== undefined && /^tutor-(.+)-v\d+$/.exec(helps)?.[1] === module)
}

/**
 * Whether the task [helps] names (a drill's quest.helps) is passed among the village's requests [all]: one it names is done
 * and none is open (the app's QuestQueue.spentFor); [self]: the drill's own request id, never its own task. The app then
 * shows the drill as practice ("📜 Kasneje · Later", no "!", not one of the two up).
 */
export function taskPassed(helps: string, all: any[], self?: string): boolean {
  const named = (Array.isArray(all) ? all : []).filter(q => q?.id !== self && names(helps, q))
  return named.some(q => q.done === true) && !named.some(q => q.done !== true)
}

/**
 * An open request as the tutor sees it: its module, how often Jan played it and their best try against its pass mark (60 % of
 * that try's questions, at least 1: the app's Quests.passMark), the grammar pages its wrong answers named (most missed
 * first: what a drill for it would practise), the task it is a drill for (quest.helps), and whether that task is passed
 * already (task_passed: the drill is optional practice now, under "Later" without a "!"). [all]: the village's requests.
 */
function openQuest(q: any, all: any[] = []) {
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
    task_passed: (typeof q.helps === 'string' && q.source === 'TUTOR' && taskPassed(q.helps, all, q.id)) || undefined,
  }
}

/**
 * Where the learner is in the school's course (the village state's course: companion/COURSE.md, "The learner's
 * progress"): the day the class began (the village a hamlet), the units done and those started, by id (get_course has the rest).
 */
function courseOf(c: any) {
  if (!c || typeof c !== 'object') return undefined
  const units = Object.entries(c.units && typeof c.units === 'object' ? c.units : {}) as [string, any][]
  const id = (key: string) => key.slice(key.indexOf('/') + 1)
  const done = units.filter(([, u]) => typeof u?.done === 'string').map(([k]) => id(k))
  const begun = (u: any) => !!u && (u.started || u.introDone || (Number.isInteger(u.intro) && u.intro > 0) || (u.sheets && Object.keys(u.sheets).length) || u.test)
  return {
    classroom: typeof c.classroom === 'string' ? c.classroom : null,
    done,
    in_progress: units.filter(([, u]) => typeof u?.done !== 'string' && begun(u)).map(([k]) => id(k)),
  }
}

/** The numbers module's ladder (companion/README.md "Numbers"; the app's NumberRange): each range's id and label, in order. */
const NUMBER_RANGES: [string, string][] = [
  ['0-10', '0–10'], ['11-20', '11–20'], ['tens', '30–90'], ['21-99', '21–99'], ['100-999', '100–999'], ['1000-9999', '1000–9999'],
]

/**
 * Where the learner is in "🔢 Števila · Numbers" (the village state's numbers; companion/README.md "Numbers"): open once the
 * unit stevila's intro is done ([introDone]), the ranges open (0–10 and 11–20 with the intro, the others each opened by a
 * run), the newest one's last answers right of how many (8 of the last 10 open the next), the numbers missed coming back
 * (as asked: the number, the way of asking, the day due), the runs and the last run's day. Undefined before the module.
 */
function numbersOf(n: any, introDone: boolean) {
  if (!introDone) return undefined
  const p = n && typeof n === 'object' ? n : {}
  const opened = p.opened && typeof p.opened === 'object' ? p.opened : {}
  const open = NUMBER_RANGES.filter(([id], i) => i < 2 || typeof opened[id] === 'string')
  const newest = open[open.length - 1]
  const last = typeof p.last?.[newest[0]] === 'string' ? (p.last[newest[0]] as string) : ''
  const again = (Array.isArray(p.again) ? p.again : []).filter((a: any) => Number.isInteger(a?.n) && typeof a?.mode === 'string' && typeof a?.due === 'string')
  const shown = (a: any) => (a.mode === 'date' && Number.isInteger(a.month) ? `${a.n}.${a.month}.` : a.mode.startsWith('time') ? `${Math.floor(a.n / 100)}:${String(a.n % 100).padStart(2, '0')}` : `${a.n}`)
  return {
    ranges_open: open.map(([, label]) => label),
    newest: { range: newest[1], right: [...last].filter(c => c === '1').length, of: last.length, opens_next_at: '8 of the last 10' },
    missed: again.length ? again.slice(0, 12).map((a: any) => ({ number: shown(a), mode: a.mode, due: a.due })) : undefined,
    runs: Number.isInteger(p.runs) ? p.runs : 0,
    last_run: typeof p.day === 'string' && p.day ? p.day : undefined,
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
   * name ("Čebelar Anton"), for who lives there; [calendar]: the culture's calendar now, given the festivals kept (id →
   * day): the days on and the next (towns.ts calendarOn), a quiet one said to be quiet; [lookedUp]: the topics looked up
   * on the cheat sheet during exercises of late, given the state's sheet (cards.ts topicsLookedUp; none: left out).
   */
  summary(
    nameOf: (id: string) => string | undefined = () => undefined,
    calendar?: (kept: Record<string, unknown>) => unknown,
    lookedUp?: (sheet: unknown) => unknown[],
    spots?: (cycles: Record<string, unknown>) => unknown,
  ): string {
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
    const looked = lookedUp?.(s.sheet) ?? []
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
        // while wolves, a bear or a storm are about (event) the app folds the requests up away until it's over (GAME.md "People react")
        open_quests: (s.quests ?? []).filter((q: any) => !q.done).map((q: any) => openQuest(q, s.quests ?? [])),
        // 🤝 earned by helping people (requests, talks, scenes, family), spent on a moba (companion/GAME.md)
        help: s.help ?? 0,
        friends,
        // the chest: tools the villagers gave (by giver: tier, times forged) and goods (thank-yous, gifts to give or sell)
        tools: s.chest?.tools ?? {},
        goods: s.chest?.goods ?? {},
        last_feast: s.lastFeast || undefined,
        // village projects (id → steps done), festivals celebrated (id → day), the day's surprise (companion/GAME.md)
        projects: s.projects ?? {},
        // the finished projects whose landmark is down for the season now (the maypole after May, until the village raises
        // it again on the eve of May Day): it isn't in the village or on its square; the project stays finished
        landmarks_down: Array.isArray(s.landmarksDown) && s.landmarksDown.length ? s.landmarksDown : undefined,
        festivals: s.festivals ?? {},
        // the days the village reached its ages, and the ages' parties it had (age → day; companion/GAME.md "Ages and their
        // celebrations"): its decorations stay up the day it grew and the next, its party is that evening
        grown: s.grown && typeof s.grown === 'object' && Object.keys(s.grown).length ? s.grown : undefined,
        parties: s.parties && typeof s.parties === 'object' && Object.keys(s.parties).length ? s.parties : undefined,
        calendar: calendar?.(s.festivals && typeof s.festivals === 'object' ? s.festivals : {}),
        surprise: s.surprise ?? undefined,
        // the school's course (companion/COURSE.md): the day the class began, the units done and started
        course: courseOf(s.course),
        // the numbers module (companion/README.md "Numbers"), open with the unit stevila's intro: its ranges, the numbers missed
        numbers: numbersOf(s.numbers, s.course?.units?.['sl/stevila']?.introDone === true),
        // the landscape's spots with a cycle today (the charcoal pile: its stage, the day of it, a breakout tonight, the
        // last delivery to the smith; spot-cycles.ts cyclesNow), given what the village keeps of them
        spots: spots?.(s.cycles && typeof s.cycles === 'object' ? s.cycles : {}),
        // the grammar pages looked up on the cheat sheet during exercises, the last two weeks (most first): work potential
        looked_up: looked.length ? looked : undefined,
        stats: s.stats,
        chronicle: (s.log ?? []).slice(-10),
      },
      null,
      2,
    )
  }
}
