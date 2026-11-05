// A family on a computer, from a parent's device (../family-members.ts; docs/plans/05-built/leftovers-app.md):
// - GET /family/members: the members this device's learner looks after, their devices and each device's daily budget;
// - POST /family/members {name, target, base, child}: a new member, and the code that pairs their device;
// - POST /family/members/:profile/pair: another code, for a code that ran out or a second device;
// - DELETE /family/members/:profile/devices/:id: a device unpaired, at its next request;
// - PUT /family/members/:profile/devices/:id/budget {daily_cap_usd}: a device's daily budget, null for none;
// - GET /family/rules: what a parent set for the device that asks (any learner's: a child's too).
//
// All with the app's token or a paired device's only: never the family page's, and nothing of it goes through the relay
// (relay.ts FAMILY_PATH). A child's bridge refuses all but the last: a child's device can't add a member or change a budget,
// whatever it sends. On the phone's own bridge there's no computer, so there are no members.
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { addMember, addRefusal, bring, looksAfter, memberCode, membersOf, readRules, setRule } from '../family-members'
import type { FeatureFactory } from '../feature'
import { json, readJson, type Route, type RouteCtx } from '../http'
import { APP_LANGUAGES } from '../cultures'
import { DEFAULT_ID, entryDataDir, learnerFacts, profilesDir, readRegistry, type ProfileEntry } from '../learners'
import { Devices } from '../pairing'

const memberIn = z.object({
  name: z.string().trim().min(1).max(40).refine(n => !/[\u0000-\u001f<>]/.test(n)),
  target: z.enum(APP_LANGUAGES),
  base: z.enum(APP_LANGUAGES),
  child: z.boolean().default(false),
})
/** The caps the app offers and a little either side: a number a device could be held to. */
const budgetIn = z.object({ daily_cap_usd: z.number().min(0).max(100).nullable() })

export const familyMembers: FeatureFactory = ctx => {
  const { cfg, profile, devices, culture, log } = ctx
  const onNode = existsSync(join(cfg.projectDir, 'companion/bin/lani-bridge'))
  const name = () => learnerFacts(cfg.dataDir).name
  const dir = () => profilesDir(cfg.projectDir)

  /** The member [id], when this learner looks after them; an answer to give instead when not. */
  function member(id: string): { entry: ProfileEntry } | { self: true } | { refused: Response } {
    if (id === profile.id) return { self: true }
    const e = readRegistry(dir()).profiles.find(p => p.id === id)
    if (!e || !looksAfter(profile.id, e)) return { refused: json({ error: 'no such member of your family', code: 'unknown_member' }, 404) }
    return { entry: e }
  }

  const appDirOf = (m: { entry: ProfileEntry } | { self: true }) => ('self' in m ? cfg.appDir : join(entryDataDir(dir(), m.entry), 'app'))

  /** Refuses what only a parent's device on a computer may do. */
  const parents = (handle: (c: RouteCtx) => Response | Promise<Response>) => (c: RouteCtx) => {
    if (!onNode) return json({ error: 'there is no computer here', code: 'no_computer' }, 404)
    if (profile.child) return json({ error: "a child's device doesn't do this", code: 'child' }, 403)
    return handle(c)
  }

  const routes: Route[] = [
    {
      method: 'GET',
      path: '/family/members',
      handle: parents(async () => {
        const refusal = addRefusal(cfg.projectDir)
        const members = await membersOf({ projectDir: cfg.projectDir, self: profile.id, selfName: name(), selfAppDir: cfg.appDir, child: profile.child })
        return json({ members, can_add: !refusal, ...(refusal ? { why_not: refusal } : {}) })
      }),
    },
    {
      method: 'POST',
      path: '/family/members',
      handle: parents(async ({ req }) => {
        const refusal = addRefusal(cfg.projectDir)
        if (refusal) return json({ error: refusal === 'off' ? "this computer's owner turned this off" : 'this computer has as many learners as it takes', code: refusal }, 409)
        const r = memberIn.safeParse(await readJson(req, 4 * 1024))
        if (!r.success || r.data.target === r.data.base) return json({ error: 'expected {name, target, base, child}, two different languages', code: 'bad_request' }, 400)
        const done = await addMember({ projectDir: cfg.projectDir, culturesDir: culture.dir, addedBy: profile.id }, r.data)
        if ('problem' in done) return json({ error: `no village speaks ${r.data.target} yet`, code: done.problem }, 409)
        log(`family: added ${done.entry.id}${done.entry.child ? ' (a child)' : ''}, from a parent's device`)
        // a child's town is the parent's to look after: linked as soon as its bridge answers
        const linked = done.up && done.entry.child ? await ctx.linkLocalChild?.(done.entry.id) : undefined
        return json({ profile: done.entry.id, name: done.entry.name, child: done.entry.child, up: done.up, pair: done.code ?? null, left: done.left, ...(linked ? { town_linked: true } : {}) })
      }),
    },
    {
      method: 'POST',
      path: /^\/family\/members\/([a-z][a-z0-9-]{0,23})\/pair$/,
      handle: parents(async ({ params: [id] }) => {
        const m = member(id)
        if ('refused' in m) return m.refused
        // this learner's own second device: lani-pair's way, on this computer
        if ('self' in m) {
          const e = readRegistry(dir()).profiles.find(p => p.id === id)
          if (!e) return json({ error: 'the default learner pairs with lani-pair on the computer', code: 'not_here' }, 409)
          return json({ profile: id, pair: memberCode(dir(), e) ?? null, left: [] })
        }
        const { code, left, up } = await bring(cfg.projectDir, dir(), m.entry)
        return json({ profile: id, up, pair: code ?? null, left })
      }),
    },
    {
      method: 'DELETE',
      path: /^\/family\/members\/([a-z][a-z0-9-]{0,23})\/devices\/(d_[0-9a-f]{8})$/,
      handle: parents(({ req, params: [id, device] }) => {
        const m = member(id)
        if ('refused' in m) return m.refused
        // the device that asks would lock itself out with no way back from the app
        if ('self' in m && devices.authorize(req)?.id === device) return json({ error: 'this is the device you are on', code: 'this_device' }, 409)
        const gone = new Devices(appDirOf(m)).revoke(device)
        if (!gone) return json({ error: 'no such device', code: 'unknown_device' }, 404)
        setRule(appDirOf(m), device, null, profile.id)
        log(`family: unpaired ${gone.name} of ${id}, from a parent's device`)
        return json({ ok: true, device: { id: gone.id, name: gone.name } })
      }),
    },
    {
      method: 'PUT',
      path: /^\/family\/members\/([a-z][a-z0-9-]{0,23})\/devices\/(d_[0-9a-f]{8})\/budget$/,
      handle: parents(async ({ req, params: [id, device] }) => {
        const m = member(id)
        if ('refused' in m) return m.refused
        const r = budgetIn.safeParse(await readJson(req, 1024))
        if (!r.success) return json({ error: 'expected {daily_cap_usd: a number, or null}', code: 'bad_request' }, 400)
        if (!new Devices(appDirOf(m)).list().some(d => d.id === device)) return json({ error: 'no such device', code: 'unknown_device' }, 404)
        setRule(appDirOf(m), device, r.data.daily_cap_usd, name())
        return json({ ok: true, daily_cap_usd: r.data.daily_cap_usd })
      }),
    },
    {
      method: 'GET',
      path: '/family/rules',
      handle: ({ req }) => {
        const d = devices.authorize(req)
        const rule = d ? readRules(cfg.appDir).devices[d.id] : undefined
        return json({ daily_cap_usd: rule?.daily_cap_usd ?? null, by: rule?.by ?? null })
      },
    },
  ]
  return { routes }
}
