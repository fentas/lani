// Pairing a phone by QR code: POST /pair trades a one-time code (made by companion/bin/lani-pair) for
// a device token, and answers with the learner it paired with, signed with the bridge's key (see
// ../pairing.ts). The route is public: the code is the credential.
import { z } from 'zod'
import type { FeatureFactory } from '../feature'
import { json, readJson } from '../http'
import { learnerFacts } from '../learners'
import { Attempts, PAIR_VERSION, cleanDeviceName, normalizeCode, pairMessage, redeemCode, signPairing } from '../pairing'

const pairIn = z.object({ code: z.string().min(1).max(64), device: z.string().max(200).optional() })

export const pairing: FeatureFactory = ({ cfg, devices, bridgeKey, profile, log }) => {
  const attempts = new Attempts(Number(process.env.LANI_PAIR_MAX_FAILS ?? 10), Number(process.env.LANI_PAIR_WINDOW_MS ?? 10 * 60_000))

  return {
    routes: [
      {
        method: 'POST',
        path: '/pair',
        access: 'public',
        handle: async ({ req }) => {
          const wait = attempts.wait()
          if (wait > 0) {
            const res = json({ error: 'too many attempts', code: 'rate_limited' }, 429)
            res.headers.set('retry-after', String(Math.ceil(wait / 1000)))
            return res
          }
          const r = pairIn.safeParse(await readJson(req, 4 * 1024))
          if (!r.success) return json({ error: 'expected {code, device}', code: 'bad_request' }, 400)
          const code = normalizeCode(r.data.code)
          const spent = redeemCode(cfg.appDir, code)
          if (spent !== 'ok') {
            attempts.fail()
            return spent === 'expired' ? json({ error: 'code expired', code: 'expired_code' }, 410) : json({ error: 'invalid code', code: 'invalid_code' }, 403)
          }
          const { device, token } = devices.add(cleanDeviceName(r.data.device))
          const facts = learnerFacts(cfg.dataDir)
          log(`paired a device: ${device.name} (${device.id})`)
          return json({
            v: PAIR_VERSION,
            token,
            device: { id: device.id, name: device.name },
            profile: profile.id,
            learner: { name: facts.name, target: facts.target, base: facts.base, child: profile.child },
            fingerprint: bridgeKey.fingerprint,
            public_key: bridgeKey.spki.toString('base64'),
            signature: signPairing(bridgeKey, pairMessage({ profile: profile.id, code, token })),
          })
        },
      },
    ],
  }
}
